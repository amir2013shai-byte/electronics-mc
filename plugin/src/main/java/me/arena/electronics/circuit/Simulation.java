package me.arena.electronics.circuit;

import me.arena.electronics.Config;
import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.sim.Mna;
import me.arena.electronics.util.Num;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Расчёт электрических цепей.
 *
 * Порядок работы на каждом тике:
 *  1) контакты всех компонентов превращаются в узлы (net = позиция блока,
 *     в который «смотрит» контакт);
 *  2) блоки-проводники (провод, шина земли, замкнутый ключ, уцелевший
 *     предохранитель) объединяют узлы union-find'ом;
 *  3) мировое пространство разбивается на «островки» — связные группы
 *     компонентов;
 *  4) каждый островок решается методом узловых потенциалов (MNA) с
 *     итерациями для нелинейных элементов (светодиод, диод, лампа,
 *     транзистор, логика) и шаговым интегрированием для конденсаторов;
 *  5) по напряжениям/токам считаются мощность, яркость, показания приборов
 *     и срабатывает защита (сгоревшие компоненты).
 */
public final class Simulation {

    static final class Island {
        final Map<String, String> parent = new HashMap<>();
        final List<CompInstance> comps = new ArrayList<>();
        final List<String> grounds = new ArrayList<>();
        int nodeCount;
        boolean hasSource;
        String hint = "";
    }

    private Simulation() {
    }

    /** Пересчёт всех цепей; возвращает число обработанных островков. */
    public static int run(ElectronicsPlugin plugin, Circuit circuit, int budget) {
        pruneInvalid(plugin, circuit);
        return solve(plugin, c -> circuit.world(c.key.worldName()),
                new ArrayList<>(circuit.all()), budget, true);
    }

    /**
     * Расчёт «виртуальной» цепи — для редактора схемы в GUI. Физика ровно та
     * же (тот же solveIsland/stampAll/finish), но компоненты живут в
     * синтетических координатах и никакие блоки мира не трогаются, а
     * «кто с кем соединён» считает та же Topology по сетке.
     *
     * @param byPos «мировая карта» песочницы: Circuit.pos(x,y,z) -> компонент
     * @return число островков, которые удалось решить
     */
    public static int solveVirtual(ElectronicsPlugin plugin, Map<String, CompInstance> byPos,
                                  List<CompInstance> components) {
        return solve(plugin, c -> byPos, components, Integer.MAX_VALUE, false);
    }

    /** Соседи компонента (для «стопки на проводе»); в мире — чанк, в GUI — сетка. */
    private interface Neighbors {
        Map<String, CompInstance> of(CompInstance c);
    }

    private static int solve(ElectronicsPlugin plugin, Neighbors neighbors,
                             List<CompInstance> components, int budget, boolean notify) {
        Config cfg = plugin.config();
        Map<String, List<CompInstance>> byNet = new HashMap<>();
        for (CompInstance c : components) {
            Circuit.resolvePins(c, neighbors.of(c));
            addNet(byNet, c.anchorNet, c);
            for (int i = 0; i < 6; i++) {
                addNet(byNet, c.pinNet[i], c);
            }
        }

        Map<CompInstance, Island> assigned = new HashMap<>();
        int islands = 0;
        int processed = 0;
        for (CompInstance start : components) {
            if (assigned.containsKey(start)) {
                continue;
            }
            if (processed >= budget) {
                if (notify) {
                    plugin.circuit().markDirty();
                }
                break;            // не успели — дорешаем в следующий тик
            }
            Island isl = collectIsland(start, byNet, cfg, assigned);
            if (isl.comps.isEmpty()) {
                continue;
            }
            processed += isl.comps.size();
            islands++;
            if (cfg.debug) {
                plugin.getLogger().info("[isl] comps=" + isl.comps.size() + " nets=" + isl.parent.size()
                        + " nodeCount=" + isl.nodeCount + " grounds=" + isl.grounds.size()
                        + " src=" + isl.hasSource + " hint=" + isl.hint);
            }
            solveIsland(plugin, cfg, isl, notify, neighbors);
        }

        if (cfg.debug) {
            for (CompInstance c : components) {
                plugin.getLogger().info("[net] " + c.def.id + " @" + c.key.x + "," + c.key.y + "," + c.key.z
                        + " o" + c.orientation.index + " anchor=" + shortNet(c.anchorNet)
                        + " pins=" + shortNets(c.pinNets));
            }
        }

        for (CompInstance c : components) {
            if (assigned.containsKey(c)) {
                continue;
            }
            boolean changed = c.state != CompStates.OFF || c.voltage != 0 || c.current != 0;
            c.state = CompStates.OFF;
            c.voltage = 0;
            c.current = 0;
            c.power = 0;
            c.brightness = 0;
            c.reading = 0;
            c.vcPrev = 0;
            c.modelState = ModelState.of(c);
            if (changed && notify) {
                plugin.renderer().requestUpdate(c);
            }
        }
        return islands;
    }

    /** Если блок под компонентом исчез (режим карты/админ), снимаем компонент. */
    private static void pruneInvalid(ElectronicsPlugin plugin, Circuit circuit) {
        List<CompInstance> dead = new ArrayList<>();
        for (CompInstance c : circuit.all()) {
            Block b = c.key.block();
            if (b == null) {
                if (!c.key.chunkLoaded()) {
                    continue;   // выгруженный чанк — не удаляем
                }
                dead.add(c);
                continue;
            }
            if (b.getType().isAir()) {
                dead.add(c);
            }
        }
        for (CompInstance c : dead) {
            circuit.remove(c.key);
            plugin.circuit().persistence().remove(c.key);
            plugin.renderer().drop(c.key);
        }
    }

    private static String firstNonNull(String[] a) {
        for (String s : a) {
            if (s != null) {
                return s;
            }
        }
        return null;
    }

    private static void addNet(Map<String, List<CompInstance>> byNet, String net, CompInstance c) {
        if (net != null) {
            byNet.computeIfAbsent(net, k -> new ArrayList<>()).add(c);
        }
    }

    private static Island collectIsland(CompInstance start,
                                        Map<String, List<CompInstance>> byNet, Config cfg,
                                        Map<CompInstance, Island> assigned) {
        Island isl = new Island();
        Deque<CompInstance> queue = new ArrayDeque<>();
        queue.add(start);
        assigned.put(start, isl);
        int limit = Math.max(4, cfg.maxIslandComponents);
        while (!queue.isEmpty() && isl.comps.size() < limit) {
            CompInstance c = queue.poll();
            isl.comps.add(c);
            Block base = c.key.block();
            if (base != null && c.anchorNet != null) {
                isl.parent.putIfAbsent(c.anchorNet, c.anchorNet);
            }
            for (int i = 0; i < 6; i++) {
                String net = c.pinNet[i];
                if (net == null) {
                    continue;
                }
                isl.parent.putIfAbsent(net, net);
                isl.nodeCount++;
                for (CompInstance n : byNet.getOrDefault(net, List.of())) {
                    if (assigned.containsKey(n)) {
                        continue;
                    }
                    assigned.put(n, isl);
                    queue.add(n);
                }
            }
        }
        // проводники склеивают свои узлы (вдоль оси + «ноги» на стопку)
        for (CompInstance c : isl.comps) {
            if (!c.isConductor()) {
                continue;
            }
            String own = c.anchorNet;
            if (own == null) {
                continue;
            }
            isl.parent.putIfAbsent(own, own);
            for (int i = 0; i < 6; i++) {
                if (c.pinNet[i] != null) {
                    union(isl, own, c.pinNet[i]);
                }
            }
            // в режиме «скамейки» проводник соединяет вообще всё вокруг
            if (cfg.benchWiring) {
                for (BlockFace f : CompInstance.FACES) {
                    Block nb = base(c);
                    if (nb == null) {
                        continue;
                    }
                    String nn = Circuit.pairNet(nb, nb.getRelative(f));
                    isl.parent.putIfAbsent(nn, nn);
                    union(isl, own, nn);
                }
            }
        }
        for (CompInstance c : isl.comps) {
            for (int i = 0; i < 6; i++) {
                c.pinNets[i] = rep(isl, c.pinNet[i]);
            }
            if ((c.def.kind == Kind.GROUND || c.def.kind == Kind.DISTRIBUTOR) && !c.burnt()) {
                String ref = c.anchorNet != null && find(isl, c.anchorNet) != null
                        ? c.anchorNet : firstNonNull(c.pinNet);
                if (ref != null) {
                    isl.grounds.add(find(isl, ref));
                }
            }
            if (c.def.kind == Kind.SOURCE || c.def.kind == Kind.GENERATOR || c.def.kind == Kind.SOLAR) {
                isl.hasSource = true;
            }
        }
        if (isl.grounds.isEmpty() && !isl.hasSource) {
            isl.hint = "нет источника питания";
        } else if (isl.grounds.isEmpty()) {
            isl.hint = "нет земли: потенциалы показаны относительно минусового вывода источника";
        }
        return isl;
    }

    // ------------------------------------------------------------------ решатель

    private static void solveIsland(ElectronicsPlugin plugin, Config cfg, Island isl, boolean notify,
                                    Neighbors neighbors) {
        double dt = Math.max(0.005, cfg.dt());
        Map<String, Double> v = Map.of();

        for (int pass = 0; pass < Math.max(1, cfg.solvePasses); pass++) {
            boolean commit = pass == Math.max(1, cfg.solvePasses) - 1;
            v = iterate(plugin, cfg, isl, dt, v, notify, commit);
        }
        // Реле и геркон: поле зависит от тока, а ток — от замкнутого контакта.
        // Пока состояния не сошлись, пересобираем матрицу (не больше 3 раз):
        // иначе первый «Пересчитать» в GUI всегда показывал бы разомкнутый геркон.
        for (int extra = 0; extra < 3; extra++) {
            if (!refreshMagnetics(isl, neighbors)) {
                break;
            }
            v = iterate(plugin, cfg, isl, dt, v, notify, true);
        }
    }

    /** Одна сборка и решение матрицы для острова. */
    private static Map<String, Double> iterate(ElectronicsPlugin plugin, Config cfg, Island isl,
                                               double dt, Map<String, Double> prev, boolean notify,
                                               boolean commit) {
        Mna mna = new Mna();
        for (String net : isl.parent.keySet()) {
            mna.node(net);
        }
        for (String g : isl.grounds) {
            mna.ground(g);
        }
        if (isl.grounds.isEmpty() && !isl.parent.isEmpty()) {
            mna.ground(isl.parent.keySet().iterator().next());
        }
        stampAll(plugin, cfg, isl, mna, dt, prev);
        Map<String, Double> v = mna.solve();
        if (commit) {
            finish(plugin, cfg, isl, mna, v, notify);
        }
        return v;
    }

    /**
     * Пересчитывает поле у герконов и состояние якоря реле.
     *
     * @return true, если хоть одно переключение изменилось (тогда надо решить ещё раз)
     */
    private static boolean refreshMagnetics(Island isl, Neighbors neighbors) {
        boolean changed = false;
        for (CompInstance c : isl.comps) {
            Def d = c.def;
            if (d.kind == Kind.REED) {
                c.field = fieldAt(neighbors == null ? null : neighbors.of(c), c);
                double need = d.operateMt > 0 ? d.operateMt : 0.6;
                int want = c.field >= need ? CompStates.CLOSED : CompStates.OFF;
                if (c.state != want) {
                    c.state = want;
                    changed = true;
                }
            } else if (d.kind == Kind.RELAY) {
                double i = Math.abs(c.current) * 1000;          // мА
                double need = Math.max(1, c.state == CompStates.CLOSED ? d.releaseMa : d.operateMa);
                int want = i >= need ? CompStates.CLOSED : CompStates.OFF;
                if (c.state != want) {
                    c.state = want;
                    changed = true;
                }
            }
        }
        return changed;
    }

    /**
     * Магнитное поле в клетке компонента, мТл: постоянные магниты рядом дают
     * своё, катушки — «мТл на ампер» × ток. Соседство — те же шесть сторон, что
     * и у проводов, поэтому в редакторе схемы (где «мир» — сетка листа) правило
     * то же самое.
     */
    private static double fieldAt(Map<String, CompInstance> world, CompInstance c) {
        if (world == null) {
            return 0;
        }
        double sum = 0;
        for (int d = -1; d < 6; d++) {      // -1 — своя клетка (стопка «магнит сверху»)
            int dx = d < 0 ? 0 : Topology.DX[d];
            int dy = d < 0 ? 0 : Topology.DY[d];
            int dz = d < 0 ? 0 : Topology.DZ[d];
            CompInstance n = world.get(Circuit.pos(c.key.x + dx, c.key.y + dy, c.key.z + dz));
            if (n == null || n == c || n.def == null) {
                continue;
            }
            Def nd = n.def;
            if (nd.kind == Kind.MAGNET) {
                sum += nd.fieldMt > 0 ? nd.fieldMt : 1.2;
            } else if ((nd.kind == Kind.ELECTROMAGNET || nd.kind == Kind.RELAY) && nd.fieldPerAmp > 0) {
                sum += nd.fieldPerAmp * Math.abs(nd.inductance > 0 ? n.iPrev : n.current);
            }
        }
        return sum;
    }

    private static void stampAll(ElectronicsPlugin plugin, Config cfg, Island isl, Mna mna,
                                 double dt, Map<String, Double> prev) {
        for (CompInstance c : isl.comps) {
            String a = rep(isl, c.pinNet[0]);
            String b = rep(isl, c.pinNet[1]);
            if (a == null) {
                continue;
            }
            Def d = c.def;
            if (c.burnt()) {
                if (b != null) {
                    mna.stamp(a, b, cfg.gLeak(), 0);
                }
                continue;
            }
            switch (d.kind) {
                case WIRE, GROUND, DISTRIBUTOR -> {
                    if (b != null) {
                        mna.bond(a, b);
                    }
                }
                case FUSE -> {
                    if (b != null) {
                        mna.stamp(a, b, 1.0 / Math.max(1e-3, d.resistance), 0);
                    }
                }
                case SWITCH, BUTTON, TOGGLE, PROBE -> {
                    if (b != null) {
                        mna.stamp(a, b, c.isClosed() ? cfg.gConductor() : cfg.gLeak(), 0);
                    }
                }
                case RESISTOR -> {
                    if (b != null) {
                        mna.stamp(a, b, 1.0 / Math.max(1e-3, d.resistance * resistorScale(c, d)), 0);
                    }
                }
                case REED -> {
                    if (b != null) {
                        mna.stamp(a, b, c.state == CompStates.CLOSED ? cfg.gConductor() : cfg.gLeak(), 0);
                    }
                }
                case RELAY -> {
                    // 0/1 — катушка (R + L), 2/3 — нормально разомкнутый контакт
                    stampInductive(mna, c, a, b, dt, Math.max(1.0, d.resistance), d.inductance);
                    String c2 = rep(isl, c.pinNet[2]);
                    String c3 = rep(isl, c.pinNet[3]);
                    if (c2 != null && c3 != null) {
                        mna.stamp(c2, c3, c.isClosed() ? cfg.gConductor() : cfg.gLeak(), 0);
                    }
                }
                case INDUCTOR -> stampInductive(mna, c, a, b, dt, Math.max(0.01, d.resistance), d.inductance);
                case ELECTROMAGNET -> {
                    stampInductive(mna, c, a, b, dt, Math.max(1.0, d.resistance), d.inductance);
                    c.field = d.fieldPerAmp * Math.abs(c.iPrev);
                }
                case LAMP -> {
                    if (b != null) {
                        mna.stamp(a, b, 1.0 / lampResistance(c, prev, a, b), 0);
                    }
                }
                case CAPACITOR -> {
                    if (b != null) {
                        // CompanionModel: проводимость g = C/dt между «+» (a) и «-» (b) и
                        // источник тока g*U(prev), втекающий в «+». Знак важен: наоборот
                        // получается положительная обратная связь и конденсатор «не заряжается».
                        double g = Math.max(d.capacitance, 1e-12) / dt;
                        mna.stamp(a, b, g, g * c.vcPrev);
                    }
                }
                case LED, DIODE -> {
                    if (b != null) {
                        double vf = d.vForward > 0 ? d.vForward : Math.max(0.4, d.vDrop);
                        double ud = prev.getOrDefault(a, 0.0) - prev.getOrDefault(b, 0.0);
                        if (ud > vf) {
                            mna.stamp(a, b, 1.0 / 25.0, vf / 25.0);
                        } else {
                            mna.stamp(a, b, cfg.gLeak(), 0);
                        }
                        double vrev = d.vReverseMax > 0 ? d.vReverseMax : 30.0;
                        if (ud < -vrev) {
                            mna.stamp(b, a, 1.0 / 20.0, -vrev / 20.0);
                        }
                    }
                }
                case SOURCE, GENERATOR, SOLAR -> {
                    if (b != null) {
                        // Thévenin: ЭДС emf и внутреннее сопротивление r между «-» (b) и «+» (a)
                        double emf = sourceVoltage(plugin, cfg, c);
                        double r = Math.max(1e-3, d.rInt);
                        // Тевенин -> Нортон: проводимость 1/r между выводами и
                        // источник тока emf/r. Без члена «с предыдущего прохода»
                        // система сходится за один проход и честно отдаёт ЭДС
                        // на разомкнутом выводе (иначе холостой источник
                        // расходится до 2*emf и больше).
                        mna.stamp(b, a, 1.0 / r, emf / r);
                    }
                }
                case POT -> {
                    String w = rep(isl, c.pinNet[4]);
                    double total = Math.max(1.0, d.resistance);
                    double frac = Math.max(0.001, Math.min(0.999, c.tuneFraction()));
                    if (w != null) {
                        mna.stamp(a, w, 1.0 / (total * frac), 0);
                        if (b != null) {
                            mna.stamp(w, b, 1.0 / (total * (1 - frac)), 0);
                        }
                    } else if (b != null) {
                        mna.stamp(a, b, 1.0 / total, 0);
                    }
                }
                case TRANSISTOR -> stampTransistor(isl, mna, cfg, c, prev);
                case GATE -> stampGate(isl, mna, cfg, c, prev, isl);
                case MOTOR -> {
                    if (b != null) {
                        double r = Math.max(0.5, d.winding);
                        double emf = c.speedPrev * (d.backEmfPerVolt > 0 ? d.ratedVoltage : 0);
                        mna.stamp(a, b, 1.0 / r, -emf / r);
                    }
                }
                case BUZZER -> {
                    if (b != null) {
                        mna.stamp(a, b, 1.0 / 240.0, 0);
                    }
                }
                case VOLTMETER -> {
                    if (b != null) {
                        mna.stamp(a, b, 1.0 / Math.max(1000, d.internalResistance), 0);
                    }
                }
                case AMMETER -> {
                    if (b != null) {
                        mna.stamp(a, b, 1.0 / Math.max(1e-3, d.internalResistance), 0);
                    }
                }
                default -> {
                    if (b != null) {
                        mna.stamp(a, b, cfg.gLeak(), 0);
                    }
                }
            }
        }
    }

    private static void stampTransistor(Island isl, Mna mna, Config cfg, CompInstance c, Map<String, Double> prev) {
        String base = rep(isl, c.pinNet[0]);
        String col = rep(isl, c.pinNet[4]);
        String emi = rep(isl, c.pinNet[5]);
        if (base == null || col == null || emi == null) {
            return;
        }
        double rBase = 220.0;
        mna.stamp(base, emi, 1.0 / rBase, 0);
        double ve = prev.getOrDefault(emi, 0.0);
        double vbe = prev.getOrDefault(base, 0.0) - ve;
        boolean pnp = c.def.id.contains("pnp");
        double vbeEff = pnp ? -vbe : vbe;
        if (vbeEff > 0.7) {
            double ib = (vbeEff - 0.7) / rBase;
            double vMax = 30.0;
            double ic = Math.min(c.def.beta * ib, (vbeEff - 0.7) / 25.0 * vMax);
            double gce = Math.max(0, ic) / vMax;
            if (gce > 1e-9) {
                if (pnp) {
                    mna.stamp(col, emi, gce, 0);
                } else {
                    mna.stamp(col, emi, -gce, 0);
                }
            }
        }
    }

    private static void stampGate(Island isl, Mna mna, Config cfg, CompInstance c,
                                  Map<String, Double> prev, Island island) {
        String a = rep(isl, c.pinNet[0]);
        String b = rep(isl, c.pinNet[1]);
        String y = rep(isl, c.pinNet[2]);
        String vcc = rep(isl, c.pinNet[4]);
        String gnd = rep(isl, c.pinNet[5]);
        if (y == null) {
            return;
        }
        boolean powered = vcc != null && gnd != null && !vcc.equals(gnd);
        if (powered) {
            mna.stamp(vcc, gnd, cfg.gLeak(), 0);        // потребление микросхемы
        }
        double vg = powered ? prev.getOrDefault(gnd, 0.0) : 0.0;
        double high = powered ? Math.max(0.5, prev.getOrDefault(vcc, 5.0) - vg) : 5.0;
        double thr = high * 0.5;
        double va = (a == null ? 0 : prev.getOrDefault(a, 0.0)) - vg;
        double vb = (b == null ? 0 : prev.getOrDefault(b, 0.0)) - vg;
        String logic = c.def.logic == null ? "" : c.def.logic.toUpperCase(java.util.Locale.ROOT);
        boolean hi = switch (logic) {
            case "AND" -> va > thr && vb > thr;
            case "OR" -> va > thr || vb > thr;
            case "XOR" -> (va > thr) ^ (vb > thr);
            case "CMP" -> va > vb;
            case "NOT" -> !(va > thr);
            default -> false;
        };
        if (powered) {
            double target = hi ? vg + high - 0.3 : vg;
            mna.stamp(y, vcc, 1.0 / 100.0, target / 100.0);   // выходной каскад, R=100 Ом
        } else {
            mna.stamp(y, y, 1.0 / 100.0, hi ? 0.05 : 0);
            if (island.hint == null || island.hint.isEmpty()) {
                island.hint = "логическому элементу нужны VCC (контакт сверху) и GND (контакт снизу)";
            }
        }
    }

    // ------------------------------------------------------------------ измерения

    private static void finish(ElectronicsPlugin plugin, Config cfg, Island isl, Mna mna,
                               Map<String, Double> v, boolean notify) {
        double gndV = isl.grounds.isEmpty() ? 0 : v.getOrDefault(isl.grounds.get(0), 0.0);
        boolean burned = false;
        for (CompInstance c : isl.comps) {
            double v0 = nodeVoltage(isl, v, c.pinNet, 0) - gndV;
            double v1 = nodeVoltage(isl, v, c.pinNet, 1) - gndV;
            c.pinVoltage[0] = v0;
            c.pinVoltage[1] = v1;
            c.voltage = v0 - v1;
            double i = branchCurrent(c, mna, isl, v);
            c.current = i;
            if (c.def.isInductive()) {
                // история катушки: по ней считается следующий шаг dt
                c.iPrev = inductorCurrent(c, isl, v, Math.max(0.005, cfg.dt()));
            }
            c.power = Math.abs(c.voltage * i);
            updateState(plugin, cfg, c, i);
            if (protect(cfg, c, isl)) {
                burned = true;
            }
            c.modelState = ModelState.of(c);
            if (notify) {
                plugin.renderer().requestUpdate(c);
            }
        }
        if (burned && notify) {
            plugin.circuit().markDirty();
        }
    }

    private static void updateState(ElectronicsPlugin plugin, Config cfg, CompInstance c, double i) {
        Def d = c.def;
        switch (d.kind) {
            case LED -> {
                c.brightness = Math.max(0, Math.min(1, Math.abs(i) / 0.02));
                if (i > 0.0004) {
                    c.state = c.brightness < 0.15 ? CompStates.DIM : CompStates.ON;
                } else if (c.voltage < -0.2) {
                    c.state = CompStates.REVERSE;
                } else {
                    c.state = CompStates.OFF;
                }
            }
            case LAMP -> {
                c.brightness = d.ratedPower > 0 ? Math.min(1, c.power / d.ratedPower) : 0;
                c.state = c.brightness < 0.02 ? CompStates.OFF : (c.brightness < 0.35 ? CompStates.DIM : CompStates.ON);
            }
            case DIODE -> c.state = i > 0.0004 ? CompStates.ON : (c.voltage < -0.3 ? CompStates.REVERSE : CompStates.OFF);
            case CAPACITOR -> {
                c.vcPrev = c.voltage;
                c.brightness = Math.min(1, Math.abs(c.voltage) / Math.max(1, d.ratedVoltage > 0 ? d.ratedVoltage : 16));
                c.state = Math.abs(c.voltage) < 0.05 ? CompStates.OFF : (c.brightness > 0.66 ? CompStates.ON : CompStates.DIM);
            }
            case VOLTMETER -> {
                c.reading = c.voltage;
                c.state = Math.abs(c.reading) > 0.02 ? CompStates.ON : CompStates.OFF;
            }
            case AMMETER -> {
                c.reading = Math.abs(i);
                c.state = c.reading > 0.0002 ? CompStates.ON : CompStates.OFF;
            }
            case BUZZER -> {
                boolean sound = Math.abs(i) > (d.minCurrent > 0 ? d.minCurrent : 0.002);
                c.state = sound ? CompStates.ON : CompStates.OFF;
                c.brightness = sound ? 1 : 0;
                if (sound) {
                    plugin.sounds().buzzer(c.key);
                }
            }
            case MOTOR -> {
                double target = Math.max(0, Math.min(1, (Math.abs(c.voltage) - 0.6) / Math.max(1.0, d.ratedVoltage)));
                c.speedPrev = c.speedPrev + (target - c.speedPrev) * 0.25;
                c.brightness = c.speedPrev;
                c.state = c.speedPrev > 0.05 ? CompStates.ON : CompStates.OFF;
            }
            case RESISTOR -> c.state = c.power > Math.max(0.01, d.ratedPower) * 0.6 ? CompStates.ON : CompStates.OFF;
            case INDUCTOR -> c.state = Math.abs(i) > 0.01 ? CompStates.ON : CompStates.OFF;
            case ELECTROMAGNET -> {
                c.brightness = d.fieldPerAmp > 0
                        ? Math.min(1, c.field / (d.fieldPerAmp * 20)) : (Math.abs(i) > 0.02 ? 1 : 0);
                c.state = Math.abs(i) > 0.005 ? CompStates.ON : CompStates.OFF;
            }
            case REED -> c.brightness = c.state == CompStates.CLOSED ? 1 : 0;
            case RELAY -> c.brightness = c.state == CompStates.CLOSED ? 1 : 0;
            case FUSE -> c.state = CompStates.CLOSED;
            case TRANSISTOR -> c.state = Math.abs(i) > 0.0004 ? CompStates.ON : CompStates.OFF;
            case GATE -> c.state = c.voltage > 2.0 ? CompStates.ON : CompStates.OFF;
            case WIRE -> c.state = Math.abs(i) > 2.0 ? CompStates.ON : CompStates.OFF;
            default -> {
            }
        }
    }

    private static boolean protect(Config cfg, CompInstance c, Island isl) {
        if (!cfg.faultsEnabled || c.burnt()) {
            return false;
        }
        Def d = c.def;
        if ((d.kind == Kind.LED || d.kind == Kind.DIODE) && d.vReverseMax > 0 && c.voltage < -d.vReverseMax * 1.2) {
            c.burn("пробой обратным напряжением (U=" + Num.volt(Math.abs(c.voltage)) + ")");
            noteIsland(isl, "диод/LED пробит обратным напряжением");
            return true;
        }
        if (d.ratedPower > 0 && c.power > d.ratedPower * 3) {
            c.burn("перегрев: P=" + Num.watt(c.power) + " при норме " + Num.watt(d.ratedPower));
            if (d.kind == Kind.LED) {
                noteIsland(isl, "светодиору не хватает гасящего резистора");
            }
            return true;
        }
        if (d.kind == Kind.CAPACITOR && d.ratedVoltage > 0 && Math.abs(c.voltage) > d.ratedVoltage * 1.3) {
            c.burn("пробой конденсатора (U=" + Num.volt(Math.abs(c.voltage)) + ")");
            return true;
        }
        if (d.kind == Kind.CAPACITOR && d.polar && c.voltage < -0.5) {
            c.burn("полярный конденсатор включён обратной полярностью");
            return true;
        }
        if (d.kind == Kind.FUSE && d.maxCurrent > 0 && Math.abs(c.current) > d.maxCurrent * 1.5) {
            c.burn("предохранитель перегорел (I=" + Num.amp(Math.abs(c.current)) + ")");
            return true;
        }
        if (d.kind == Kind.MOTOR && d.ratedVoltage > 0 && Math.abs(c.voltage) > d.ratedVoltage * 1.6) {
            c.burn("обмотка мотора сгорела (U=" + Num.volt(Math.abs(c.voltage)) + ")");
            return true;
        }
        return false;
    }

    private static void noteIsland(Island isl, String msg) {
        if (isl.hint == null || isl.hint.isEmpty()) {
            isl.hint = msg;
        }
    }

    private static Block base(CompInstance c) {
        return c.key.block();
    }

    private static double nodeVoltage(Island isl, Map<String, Double> v, String[] pinNet, int pin) {
        String n = rep(isl, pinNet[pin]);
        return n == null ? 0 : v.getOrDefault(n, 0.0);
    }

    private static double branchCurrent(CompInstance c, Mna mna, Island isl, Map<String, Double> v) {
        String a = rep(isl, c.pinNet[0]);
        String b = rep(isl, c.pinNet[1]);
        if (a == null) {
            return 0;
        }
        if (b == null) {
            return 0;
        }
        Mna.Stamp s = mna.findBranch(a, b);
        if (s != null) {
            return Math.abs(Mna.branchCurrent(s, v, b));
        }
        return Math.abs(mna.netInflow(a, v));
    }

    /**
     * Последовательные R и L одним companion-узлом (обратный Эйлер):
     *   I = I_prev + dt/(L + dt*R) * (U - R*I_prev)
     * При L -> 0 это просто R, при dt -> 0 — обрыв, то есть катушка ведёт себя
     * как на уроке: в первый момент ток через неё не течёт, потом разгоняется.
     */
    private static void stampInductive(Mna mna, CompInstance c, String a, String b,
                                       double dt, double r, double l) {
        double rr = Math.max(0.01, r);
        double ll = Math.max(1e-6, l);
        double g = dt / (ll + dt * rr);
        mna.stamp(a, b, g, -(1 - g * rr) * c.iPrev);
    }

    /** Ток катушки «как он получился» — та же формула, что в stemпе. */
    private static double inductorCurrent(CompInstance c, Island isl, Map<String, Double> v, double dt) {
        double u = nodeVoltage(isl, v, c.pinNet, 0) - nodeVoltage(isl, v, c.pinNet, 1);
        double r = Math.max(0.01, c.def.resistance);
        double g = dt / (Math.max(1e-6, c.def.inductance) + dt * r);
        return g * u + (1 - g * r) * c.iPrev;
    }

    /**
     * Модификатор сопротивления для фоторезистора и термистора. В мире смотрим
     * на свет блока сверху и на соседей-источники тепла; в редакторе схемы блока
     * нет — считаем, что деталь лежит на столе под лампой (0.8).
     */
    private static double resistorScale(CompInstance c, Def d) {
        double k = 1;
        org.bukkit.block.Block b = c.key.block();
        if (d.lightDependent) {
            double lux = 0.8;
            if (b != null) {
                lux = Math.max(0, b.getRelative(org.bukkit.block.BlockFace.UP).getLightLevel() / 15.0);
            }
            k *= 1 / (0.02 + 1.6 * lux);
        }
        if (d.bConstant > 0) {
            double t = 298.15 + Math.max(0, c.power) * Math.max(0, d.selfHeat);
            if (b != null) {
                t += ambientHeat(b);
            }
            k *= Math.exp(d.bConstant * (1 / Math.max(120, t) - 1 / 298.15));
        }
        return k;
    }

    /** Степень «меня греют»: лава/огонь/факел рядом дают +60..+400 К. */
    private static double ambientHeat(org.bukkit.block.Block b) {
        double heat = 0;
        for (org.bukkit.block.BlockFace f : CompInstance.FACES) {
            String name = b.getRelative(f).getType().name();
            if (name.contains("LAVA")) {
                heat = Math.max(heat, 400);
            } else if (name.contains("FIRE") || name.contains("MAGMA")) {
                heat = Math.max(heat, 250);
            } else if (name.contains("TORCH") || name.contains("LANTERN")) {
                heat = Math.max(heat, 60);
            }
        }
        return heat;
    }

    private static double lampResistance(CompInstance c, Map<String, Double> prev, String a, String b) {
        double hot = Math.max(1.0, c.def.resistanceHot);
        double cold = hot * Math.max(0.02, Math.min(1, c.def.coldFactor));
        double u = prev.getOrDefault(a, 0.0) - prev.getOrDefault(b, 0.0);
        double p = u * u / hot;
        double k = Math.max(0, Math.min(1, p / Math.max(0.01, c.def.ratedPower)));
        return cold + (hot - cold) * k;
    }

    private static double sourceVoltage(ElectronicsPlugin plugin, Config cfg, CompInstance c) {
        Def d = c.def;
        if (d.kind == Kind.GENERATOR) {
            long period = Math.max(2, cfg.generatorPeriodTicks);
            long phase = plugin.circuit().tick() % (period * 2);
            return phase < period ? d.voltage : 0.0;
        }
        if (d.kind == Kind.SOLAR) {
            Block b = c.key.block();
            World w = b == null ? null : b.getWorld();
            long time = w == null ? 6000 : w.getTime();
            boolean day = time > 0 && time < 12000;
            return day ? cfg.solarDayVoltage : cfg.solarNightVoltage;
        }
        return c.tunedVoltage();
    }

    /** Узел «как он называется» без имени мира — для диагностики. */
    public static String netShort(String net) {
        return shortNet(net);
    }

    private static String shortNet(String net) {
        if (net == null) {
            return "-";
        }
        int i = net.indexOf('|');
        return i < 0 ? net : net.substring(i + 1);
    }

    private static String shortNets(String[] nets) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < nets.length; i++) {
            sb.append(i > 0 ? " " : "").append(nets[i] == null ? "-" : shortNet(nets[i]));
        }
        return sb.toString();
    }

    private static String rep(Island isl, String net) {
        return net == null ? null : find(isl, net);
    }

    private static String find(Island isl, String a) {
        String root = a;
        String p = isl.parent.get(root);
        int guard = 0;
        while (p != null && !p.equals(root) && guard++ < 4096) {
            root = p;
            p = isl.parent.get(root);
        }
        String cur = a;
        guard = 0;
        while (cur != null && !cur.equals(root) && guard++ < 4096) {
            String next = isl.parent.get(cur);
            if (next == null) {
                break;
            }
            isl.parent.put(cur, root);
            cur = next;
        }
        return root;
    }

    private static void union(Island isl, String a, String b) {
        String ra = find(isl, a);
        String rb = find(isl, b);
        if (ra != null && rb != null && !ra.equals(rb)) {
            isl.parent.put(ra, rb);
        }
    }

    /** Карта «net контакта -> объединённый net» для последнего расчёта. */
    public static Map<String, String> mergedNets(Circuit circuit) {
        Map<String, String> out = new HashMap<>();
        for (CompInstance c : circuit.all()) {
            for (int i = 0; i < 6; i++) {
                if (c.pinNet[i] != null && c.pinNets[i] != null) {
                    out.put(c.pinNet[i], c.pinNets[i]);
                }
            }
        }
        return out;
    }

    /** Быстрая сборка «цепи по сетке» для автотестов/презетов. */
    public static Map<String, String> netsOf(CompInstance c) {
        Map<String, String> out = new HashMap<>();
        for (int i = 0; i < 6; i++) {
            if (c.pinNet[i] != null) {
                out.put(c.pinNet[i], c.pinNets[i]);
            }
        }
        return out;
    }

    /** Все компоненты, собранные в одну цепь с указанным блоком. */
    public static List<CompInstance> islandWith(Circuit circuit, CompInstance start) {
        List<CompInstance> out = new ArrayList<>();
        if (start == null) {
            return out;
        }
        Map<String, List<CompInstance>> byNet = new HashMap<>();
        for (CompInstance c : circuit.all()) {
            Circuit.resolvePins(c, circuit.world(c.key.worldName()));
            addNet(byNet, c.anchorNet, c);
            for (int i = 0; i < 6; i++) {
                addNet(byNet, c.pinNet[i], c);
            }
        }
        java.util.Set<CompInstance> seen = new java.util.HashSet<>();
        Deque<CompInstance> q = new ArrayDeque<>();
        q.add(start);
        seen.add(start);
        while (!q.isEmpty() && out.size() < 400) {
            CompInstance c = q.poll();
            out.add(c);
            for (int i = 0; i < 6; i++) {
                if (c.pinNet[i] == null) {
                    continue;
                }
                for (CompInstance n : byNet.getOrDefault(c.pinNet[i], List.of())) {
                    if (seen.add(n)) {
                        q.add(n);
                    }
                }
            }
        }
        out.sort(java.util.Comparator.comparing(x -> x.key.flat()));
        return out;
    }

    /** Строка с основными величинами — для action bar и отладки. */
    public static String describe(CompInstance c) {
        LocationKey k = c.key;
        return String.format(java.util.Locale.US, "%s [%s] U=%.3fВ I=%.4fА P=%.3fВт",
                k, c.def.id, c.voltage, c.current, c.power);
    }
}
