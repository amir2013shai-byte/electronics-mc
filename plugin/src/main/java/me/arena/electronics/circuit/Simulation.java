package me.arena.electronics.circuit;

import me.arena.electronics.Config;
import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.registry.Def;
import me.arena.electronics.fw.Firmware;
import me.arena.electronics.registry.Kind;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.sim.Mna;
import me.arena.electronics.util.Num;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayDeque;
import java.util.ArrayList;
import me.arena.electronics.kinetics.Kinetics;
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

    /** Общий шаг симуляции для источников со своей фазой (синусоидальный вход). */
    static long acPhaseStep = 0;

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
            if (c.def.kind == Kind.SOURCE || c.def.kind == Kind.BATTERY
                    || c.def.kind == Kind.GENERATOR || c.def.kind == Kind.SOLAR) {
                isl.hasSource = true;
            }
        }
        // Земля в сборке одна: сколько бы шин ни стояло, это тот же проводник.
        // Без склейки «минус источника» и «корпус механизма» на двух шинах
        // оказывались двумя узлами, цепь оставалась разомкнутой, а мотор — при
        // нулевом токе (видно было только утечки в единицы микроампер).
        if (isl.grounds.size() > 1) {
            String g0 = isl.grounds.get(0);
            for (int i = 1; i < isl.grounds.size(); i++) {
                union(isl, g0, isl.grounds.get(i));
            }
            for (int i = 0; i < isl.grounds.size(); i++) {
                isl.grounds.set(i, find(isl, g0));
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
        // Линейный стабилизатор: ограничение тока — это петля обратной связи,
        // поэтому коэффициент foldback пересчитываем по уже измеренному току
        // (2–4 итерации, как с реле: иначе КЗ показывало бы «идеальный» источник).
        for (int extra = 0; extra < 4; extra++) {
            if (!updateLimits(isl)) {
                break;
            }
            v = iterate(plugin, cfg, isl, dt, v, notify, true);
        }
        // один «шаг света» для синусоидальных источников: считаем решение острова,
        // а не число проходов внутри него
        acPhaseStep++;
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
                case PTC -> {
                    if (b != null) {
                        mna.stamp(a, b, 1.0 / ptcResistance(c, d), 0);
                    }
                }
                case VARISTOR -> {
                    if (b != null) {
                        double u = Math.abs(prev.getOrDefault(a, 0.0) - prev.getOrDefault(b, 0.0));
                        mna.stamp(a, b, movConductance(c, d, u), 0);
                    }
                }
                case MCU -> stampMcu(isl, mna, cfg, c, prev);
                case BRIDGE -> stampBridge(isl, mna, cfg, c, prev);
                case REGULATOR -> stampRegulator(isl, mna, cfg, c, prev);
                case TRANSFORMER -> stampTransformer(isl, mna, c, dt);
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
                        // утечка электролита: R_leak параллельно (иначе «банковские»
                        // конденсаторы держат заряд вечно, чего в жизни не бывает)
                        if (d.resistance > 0) {
                            mna.stamp(a, b, 1.0 / d.resistance, 0);
                        }
                    }
                }
                case LED, DIODE -> {
                    if (b != null) {
                        double vf = d.vForward > 0 ? d.vForward : Math.max(0.4, d.vDrop);
                        double ud = prev.getOrDefault(a, 0.0) - prev.getOrDefault(b, 0.0);
                        diodeEdge(mna, cfg, prev, a, b, vf);
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
                case BATTERY -> {
                    if (b != null) {
                        // Тот же Тевенин, что у SOURCE, но ЭДС берётся из заряда
                        // банки, а внутреннее сопротивление — из её состояния:
                        // севшая банка проседает под нагрузкой, и это видно по
                        // лампочке (проверка «нагрузкой», а не вольтметром).
                        Battery.Params bp = batteryParams(cfg, d);
                        double r = Math.max(1e-3, Battery.resistance(bp, c.aux));
                        mna.stamp(b, a, 1.0 / r, Battery.emf(bp, c.aux) / r);
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
                        // speedPrev — доля паспортных оборотов (её пишет KineticEngine,
                        // когда деталь в приводе), поэтому ЭДС вращения = доля × номинал:
                        // на холостом ходу она равна сети и ток холостого хода мал, а под
                        // нагрузкой вал проседает — ток и момент растут.
                        double emf = c.speedPrev * (d.backEmfPerVolt > 0 ? d.ratedVoltage : 0);
                        mna.stamp(a, b, 1.0 / r, -emf / r);
                    }
                }
                case BUZZER -> {
                    if (b != null) {
                        mna.stamp(a, b, 1.0 / 240.0, 0);
                    }
                }
                case VOLTMETER, SCOPE -> {
                    // Приборный вход: 1 МОм у вольтметра, 10 МОм у осциллографа.
                    // Одна строка на оба, потому что осциллограф и есть вольтметр
                    // с памятью: на цепь он не должен влиять заметнее хорошего
                    // тестера, иначе «измерение» меняло бы измеряемое.
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
            Def d = c.def;
            double v0 = nodeVoltage(isl, v, c.pinNet, 0) - gndV;
            double v1 = nodeVoltage(isl, v, c.pinNet, 1) - gndV;
            c.pinVoltage[0] = v0;
            c.pinVoltage[1] = v1;
            c.voltage = v0 - v1;
            double i = branchCurrent(c, mna, isl, v);
            double dtF = Math.max(0.005, cfg.dt());
            if (d.kind == Kind.REGULATOR) {
                // стабилизатор отдаёт в нагрузку ток своего источника: берём его из
                // собственного stamp'а «выход -> земля» (иначе branchCurrent считает
                // только утечку IQ и «ограничение тока» было бы негде показать)
                String outN = rep(isl, c.pinNet[1]);
                String gndN = rep(isl, c.pinNet[2]);
                Mna.Stamp src = outN == null ? null : mna.findBranch(outN, gndN);
                if (src != null) {
                    i = Math.abs(Mna.branchCurrent(src, v, outN));
                }
                c.reading = Math.abs(c.pinVoltage[1]);
            } else if (d.kind == Kind.TRANSFORMER) {
                double[] ii = transformerCurrents(c, isl, v, dtF);
                c.reading = Math.abs(nodeVoltage(isl, v, c.pinNet, 2) - nodeVoltage(isl, v, c.pinNet, 3));
                c.aux[0] = ii[0];
                c.aux[1] = ii[1];
                i = Math.abs(ii[0]);
                if (d.ratedPower > 0 && Math.abs(ii[1]) * c.reading > d.ratedPower * 6) {
                    c.aux[2] = 1;             // перегрузка вторички: считаем в protect()
                }
            } else if (d.kind == Kind.BATTERY) {
                // Аккумулятору знак тока жизненно необходим: по нему решается,
                // сажать банк или заряжать. Модуль branchCurrent() считает честно,
                // знак берём из stamp'а банки и кладём в reading (модуль — в i).
                double ib = batterySignedCurrent(c, mna, isl, v);
                c.reading = ib;
                i = Math.abs(ib);
            }
            c.current = i;
            if (c.def.isInductive() && d.kind != Kind.TRANSFORMER) {
                // история катушки: по ней считается следующий шаг dt
                c.iPrev = inductorCurrent(c, isl, v, dtF);
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
        for (CompInstance c : isl.comps) {
            if (c.def != null && c.def.kind == Kind.MCU) {
                tickMicrocontroller(plugin, cfg, isl, mna, v, c, notify);
            }
        }
        if (burned && notify) {
            plugin.circuit().markDirty();
        }
    }

    /**
     * Микроконтроллер в матрице. Нога 0 = VCC, нога 1 = GND: без них чип молчит
     * (ровно как в жизни — питание и опора обязательны). Ноги 2..5, которые
     * прошивка выставила выходом, работают как источник Тевенина: ЭДС =
     * VCC·скважность, внутреннее сопротивление = def.resistance (25 Ом, как у
     * порта ATmega). Вход — hi-Z, в матрицу не лезет вообще: читаем то, что
     * посчитал решатель. Ограничение по току порта (def.iLimit, 40 мА) честно
     * сажает напряжение: КЗ на ногу — и светодиод горит тускло; догрев корпуса
     * идёт через heat_j/thermal_k, как у всех нагретых деталей.
     */
    private static void stampMcu(Island isl, Mna mna, Config cfg, CompInstance c,
                                 Map<String, Double> prev) {
        Def d = c.def;
        String vccNet = rep(isl, c.pinNet[0]);
        String gndNet = rep(isl, c.pinNet[1]);
        if (vccNet == null || gndNet == null) {
            c.mcuVcc = 0;
            return;
        }
        double vg = prev.getOrDefault(gndNet, 0.0);
        c.mcuVcc = prev.containsKey(vccNet) ? prev.getOrDefault(vccNet, 0.0) - vg : 0.0;
        // Как у стабилизатора: на нулевом проходе prev пуст, и брать из него нечего.
        // Затравка номиналом детали нужна, чтобы первый же проход не объявил
        // коротыш там, где его нет (дальше решает реальное напряжение узла).
        double vccSeed = prev.containsKey(vccNet) ? c.mcuVcc : Math.max(0, d.voltage);
        // потребление самой микросхемы (буферы + квартовый генератор): 470 кОм
        mna.stamp(vccNet, gndNet, 1.0 / 4.7e5, 0);
        double vcc = vccSeed;
        boolean alive = !c.burnt() && vcc >= 2.6;
        for (int pin = 2; pin <= 5; pin++) {
            int mode = c.mcuDir[pin];
            c.mcuPinI[pin] = 0;
            if (!alive || mode == 0) {
                continue;
            }
            String net = rep(isl, c.pinNet[pin]);
            if (net == null) {
                continue;
            }
            double lvl = mode == 2 ? c.mcuPwm[pin] / 255.0 : (c.mcuLvl[pin] != 0 ? 1.0 : 0.0);
            double rz = Math.max(1.0, d.resistance);
            double head = d.iLimit > 0 ? d.iLimit : 1.0;
            double target = vcc * lvl;
            if (net.equals(gndNet)) {
                // Нога замкнута проводом прямо на землю этой же микросхемы: отдельного
                // узла не остаётся, штамповать некуда — зато ток коротыша есть, и он
                // упирается в предел порта. Его считаем явно.
                c.mcuPinI[pin] = head > 0
                        ? Math.copySign(Math.min(Math.abs(target) / rz, head), target) : 0;
                continue;
            }
            // Мягкий лимит, как у настоящего ключа: на перегрузке транзистор выходит
            // из насыщения и его сопротивление растёт. «Идеальный генератор тока»
            // вместо этого на оборванном узле даёт десятки киловольт — решатель
            // честно сходится к 40 кВ, и всё вокруг портится (проверено на деле).
            double iOut = (target - (prev.getOrDefault(net, 0.0) - vg)) / rz;
            if (head > 0 && Math.abs(iOut) > head) {
                rz = rz * Math.abs(iOut) / head;
                iOut = Math.copySign(head, iOut);
            }
            c.mcuPinI[pin] = iOut;
            double g = 1.0 / rz;
            mna.stamp(net, gndNet, g, target * g);
        }
    }

    /** Шаг прошивки: снять показания, исполнить операторы, записать ноги. */
    private static void tickMicrocontroller(ElectronicsPlugin plugin, Config cfg, Island isl,
                                            Mna mna, Map<String, Double> v, CompInstance c,
                                            boolean notify) {
        Def d = c.def;
        double gndV = isl.grounds.isEmpty() ? 0 : v.getOrDefault(isl.grounds.get(0), 0.0);
        double vcc = c.mcuVcc;
        for (int pin = 2; pin <= 5; pin++) {
            c.mcuVolt[pin] = nodeVoltage(isl, v, c.pinNet, pin) - gndV;
            c.mcuAdc[pin] = Firmware.adc(c.mcuVolt[pin], vcc);
            // Ток ноги берём из решателя (ветвь «нога -> земля» — это наш штамп
            // Тевенина): оценка по prev годится только когда узла нет вовсе, т.е.
            // когда нога замкнута на землю этой же микросхемы — тогда ветви нет.
            String gndNet = rep(isl, c.pinNet[1]);
            String net = rep(isl, c.pinNet[pin]);
            if (net != null && gndNet != null && !net.equals(gndNet)) {
                Mna.Stamp src = mna.findBranch(net, gndNet);
                if (src != null) {
                    c.mcuPinI[pin] = Mna.branchCurrent(src, v, net);
                }
            }
        }
        if (c.program == null) {
            c.program = "";
        }
        if (c.firmware == null || !c.program.equals(c.firmwareSource)) {
            c.firmware = Firmware.compile(c.program, d.pins);
            c.firmwareSource = c.program;
        }
        Firmware.Program p = c.firmware;
        double diss = 0;
        int hot = 0;
        double head = d.iLimit > 0 ? d.iLimit : 1.0;
        for (int pin = 2; pin <= 5; pin++) {
            double i = Math.abs(c.mcuPinI[pin]);
            if (head > 0 && i >= head * 0.98) {
                hot++;                        // порт на пределе — это видно в GUI
            }
            diss += Math.abs(vcc - c.mcuVolt[pin]) * i;      // потери на ключе
        }
        c.aux[0] = diss;
        c.aux[1] = hot;
        c.reading = vcc;
        if (c.burnt()) {
            c.mcuHalt = "корпус сгорел";
            c.resetFirmwareKeepProgram();
            c.state = CompStates.BURNT;
        } else if (vcc < 2.6) {
            c.mcuHalt = vcc <= 0.05 ? "питания нет (VCC/GND не подключены)"
                    : "питание просело до " + String.format(java.util.Locale.ROOT, "%.2f", vcc) + " В";
            c.resetFirmwareKeepProgram();
            c.state = CompStates.OFF;
        } else if (!p.ok()) {
            c.mcuHalt = p.errorMessage() == null ? "" : p.errorMessage();
            c.clearMcuPins();
            c.state = CompStates.REVERSE;
        } else if (p.statements() == 0) {
            c.mcuHalt = "прошивка пустая";
            c.clearMcuPins();
            c.state = CompStates.OFF;
        } else {
            c.mcuHalt = "";
            p.tick(new Board(c));
            c.state = p.delayTicks() > 0 ? CompStates.DIM : CompStates.ON;
        }
        c.modelState = ModelState.of(c);
        if (notify) {
            plugin.renderer().requestUpdate(c);
        }
    }

    /** Плата для интерпретатора: ноги Instance, больше ничего. */
    private static final class Board implements Firmware.Board {
        private final CompInstance c;

        Board(CompInstance c) {
            this.c = c;
        }

        public double vcc() {
            return c.mcuVcc;
        }

        public int dir(int pin) {
            return pin >= 0 && pin < c.mcuDir.length ? c.mcuDir[pin] : 0;
        }

        public void dir(int pin, int mode) {
            if (pin >= 0 && pin < c.mcuDir.length) {
                c.mcuDir[pin] = mode;
            }
        }

        public int level(int pin) {
            return pin >= 0 && pin < c.mcuLvl.length ? c.mcuLvl[pin] : 0;
        }

        public void level(int pin, int value) {
            if (pin >= 0 && pin < c.mcuLvl.length) {
                c.mcuLvl[pin] = value;
            }
        }

        public int pwm(int pin) {
            return pin >= 0 && pin < c.mcuPwm.length ? c.mcuPwm[pin] : 0;
        }

        public void pwm(int pin, int value) {
            if (pin >= 0 && pin < c.mcuPwm.length) {
                c.mcuPwm[pin] = value;
            }
        }

        public double voltage(int pin) {
            return pin >= 0 && pin < c.mcuVolt.length ? c.mcuVolt[pin] : 0;
        }
    }

    private static void updateState(ElectronicsPlugin plugin, Config cfg, CompInstance c, double i) {
        Def d = c.def;
        double dt = Math.max(0.005, cfg.dt());
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
            case BATTERY -> {
                Battery.Params bp = batteryParams(cfg, d);
                Battery.step(bp, c.aux, c.voltage, c.reading, dt);
                c.state = Battery.stateOf(bp, c.aux, c.voltage, c.reading);
                c.fault = Battery.faultOf(bp, c.aux, c.voltage, c.reading);
                // подсветка корпуса пропорциональна току: 20 А = полный огонь
                c.brightness = Math.min(1.0, Math.abs(c.reading) / 20.0);
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
            case SCOPE -> {
                // Дифференциальное чтение: «+» без «−», иначе второй щуп прибора
                // был бы декорацией, а «земля» на плате — обязательной.
                c.reading = c.voltage;
                ScopeTrace tr = plugin.circuit().scope(c.key);
                // Развёртку задаёт «ползунок» (шаг 1,2,4,8 отсчёта на столбец).
                // Прореживает сам трекер: пик между отсчётами обязан остаться на
                // экране (peak-detect), а dt частотомера — шаг отсчёта, не симуляции.
                int lvl = Math.min(3, (int) Math.round(c.tuneFraction() * 3.0));
                tr.setRate(Math.max(0.005, cfg.dt()), 1 << lvl);
                tr.sample(c.voltage);
                c.state = tr.size() > 2 ? CompStates.ON : CompStates.OFF;
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
                if (c.kinOwned || c.kinOmega != 0.0) {
                    // Обороты диктует механика: тяжёлая пила реально садит вал, ЭДС падает,
                    // ток растёт — и дальше нагреваются PTC и плавкая вставка. Без этого
                    // нагрузки в мире не существовало бы: мотор крутился «по напряжению».
                    // напряжение и ток здесь не трогаем: их считает решатель по ЭДС вращения,
                    // а доля оборотов берётся из вала — иначе она повисает на значении
                    // «до перехода на механику» и решатель видит мотор почти на холостом токе
                    double w0 = Kinetics.toOmega(Math.max(1.0, d.rpm) / 60.0);
                    c.speedPrev = w0 > 1e-9
                            ? Math.max(0.0, Math.min(1.15, c.kinOmega / w0)) : 0.0;
                    c.brightness = Math.min(1.0, c.speedPrev);
                    // «тускло» = валу тяжело: ровно то, что игрок видит на лампочке,
                    // когда нагрузка съедает момент
                    c.state = c.speedPrev > 0.05
                            ? (c.kinLoad > 0.85 ? CompStates.DIM : CompStates.ON)
                            : CompStates.OFF;
                } else {
                    double target = Math.max(0, Math.min(1, (Math.abs(c.voltage) - 0.6) / Math.max(1.0, d.ratedVoltage)));
                    c.speedPrev = c.speedPrev + (target - c.speedPrev) * 0.25;
                    c.brightness = c.speedPrev;
                    c.state = c.speedPrev > 0.05 ? CompStates.ON : CompStates.OFF;
                }
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
            case FUSE -> {
                // I²t: плавкая вставка греется не мгновенно, а копит «огонь».
                // При 2× номинала стекло 5×20 живёт доли секунды, при 1.2× — минуты.
                double budget = d.blowI2t;
                if (budget > 0) {
                    // Плавкая вставка копит I²t только вверху от номинала: при 0,8·I_ном
                    // она обязана жить (иначе любая долгая работа механизма — пила, насос —
                    // «сжигает» вставку на ровном месте), а при 2·I_ном стекло 5×20 живёт
                    // доли секунды. Считаем превышение мощности дуги: I² − I_ном².
                    double in = Math.abs(c.current);
                    double iNom = Math.max(0.0, d.maxCurrent);
                    double over = Math.max(0.0, in * in - iNom * iNom);
                    c.aux[0] = Math.max(0, c.aux[0] + over * dt * (c.burnt() ? 0 : 1));
                    c.state = c.aux[0] > budget * 0.45 ? CompStates.DIM : CompStates.CLOSED;
                } else {
                    c.state = CompStates.CLOSED;
                }
            }
            case PTC -> {
                double cth = Math.max(0.2, d.heatJ);
                double rth = Math.max(0.2, d.thermalK);
                c.aux[0] = Math.max(0, c.aux[0] + dt * (c.power / cth - c.aux[0] / rth));
                c.aux[1] = Math.max(c.aux[1], c.aux[0]);   // пик: в защите деталь остывает и греется по кругу
                double tsw = d.tripTemp > 0 ? d.tripTemp : 90;
                c.state = c.aux[0] >= tsw ? CompStates.ON : (c.aux[0] >= tsw * 0.35 ? CompStates.DIM : CompStates.OFF);
            }
            case VARISTOR -> {
                if (c.power > 0.5) {
                    c.aux[0] += c.power * dt;                     // Дж, накопленные «ударом»
                    c.state = CompStates.ON;
                } else {
                    c.state = c.aux[1] > 0 ? CompStates.DIM : CompStates.OFF;
                }
            }
            case BRIDGE -> c.state = Math.abs(i) > 0.002 ? CompStates.ON : CompStates.OFF;
            case REGULATOR -> {
                double cth = Math.max(0.5, d.heatJ);
                double rth = Math.max(1.0, d.thermalK);
                // на корпусе рассеивается (Uвх − Uвых)·Iнагр + Uвх·IQ — ровно то, что
                // заставляет ставить радиатор
                double pdiss = Math.abs(c.voltage) * Math.abs(i) + Math.abs(c.pinVoltage[0]) * Math.max(0, d.iQuiescent);
                c.aux[0] = Math.max(0, c.aux[0] + dt * (pdiss / cth - c.aux[0] / rth));
                c.reading = Math.abs(c.pinVoltage[1]);
                c.state = c.aux[0] > 100 ? CompStates.DIM
                        : (c.voltage > Math.max(0.2, d.dropout) * 0.98 && Math.abs(i) < 1e-9 ? CompStates.OFF
                        : (c.pinVoltage[1] > 0.05 ? CompStates.ON : CompStates.OFF));
            }
            case TRANSFORMER -> c.state = Math.abs(i) > 0.005 ? CompStates.ON : CompStates.OFF;
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
        if (c.def.kind == Kind.MOTOR && c.aux[2] > 0.0 && c.power <= c.def.ratedPower * 3) {
            c.aux[2] = Math.max(0.0, c.aux[2] - 0.5);   // остывает, пока не перегружен
        }
        Def d = c.def;
        if ((d.kind == Kind.LED || d.kind == Kind.DIODE) && d.vReverseMax > 0 && c.voltage < -d.vReverseMax * 1.2) {
            c.burn("пробой обратным напряжением (U=" + Num.volt(Math.abs(c.voltage)) + ")");
            noteIsland(isl, "диод/LED пробит обратным напряжением");
            return true;
        }
        if (d.ratedPower > 0 && c.power > d.ratedPower * 3) {
            if (d.kind == Kind.MOTOR) {
                // У машины мгновенная перегрузка бывает на пуске (5-10 с ротор
                // крутит вал вразгон), а обмотка с оборами греется не за тик: копим
                // «секунды тройной перегрузки» и сжигаем после ~4 таких секунд.
                // Заодно это учит игрока: пуск без редуктора и есть та причина,
                // по которой пилу запускают вхолостую, а не прижав к бревну.
                c.aux[2] = Math.max(0.0, c.aux[2]
                        + (c.power / (d.ratedPower * 3.0) - 1.0)
                        * Math.max(0.005, Math.min(0.25, cfg.dt())));
                if (c.aux[2] < 4.0) {
                    return false;
                }
                c.aux[2] = 0.0;
            }
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
        if (d.kind == Kind.FUSE && d.maxCurrent > 0) {
            if (d.blowI2t > 0) {
                if (c.aux[0] > d.blowI2t) {
                    c.burn("плавкая вставка сгорела: I²t=" + Num.eng(c.aux[0], "А²·с")
                            + " при пределе " + Num.eng(d.blowI2t, "А²·с"));
                    noteIsland(isl, "предохранитель перегорел — заменить/перепаять на столе: /elec repair");
                    return true;
                }
            } else if (Math.abs(c.current) > d.maxCurrent * 1.5) {
                c.burn("предохранитель перегорел (I=" + Num.amp(Math.abs(c.current)) + ")");
                noteIsland(isl, "предохранитель перегорел — перепаять на столе: /elec repair");
                return true;
            }
        }
        if (d.kind == Kind.PTC && c.aux[0] > (d.tripTemp > 0 ? d.tripTemp : 90) * 2.6) {
            c.burn("PTC перегрет и не восстанавливается (Δt=" + (int) c.aux[0] + " °C)");
            noteIsland(isl, "самовосстанавливающийся предохранитель сгорел от долгого КЗ");
            return true;
        }
        if (d.kind == Kind.REGULATOR && c.aux[0] > 200) {
            c.burn("стабилизатор сгорел: Δt=" + (int) c.aux[0] + " °C, нужен радиатор");
            noteIsland(isl, "линейный стабилизатор сгорел — (Uвх−Uвых)·I слишком велика");
            return true;
        }
        // Обратную полярность у стабилизатора здесь не проверяем: для 3-выводных
        // деталей c.voltage считается между первыми двумя выводами (IN и GND), а на
        // итерационных проходах решателя знак там гуляет сам по себе — деталь гишла бы
        // ни за что. Защита от обратки — диод навстречу входу (об этом describe), а
        // сама микросхема гибнет от перегрева: он проверен выше и честно считан.
        if (d.kind == Kind.VARISTOR && c.aux[1] >= 3) {
            c.burn("варистор отработал ресурс (3 раза поглотил импульс): утечка выросла");
            noteIsland(isl, "MOV деградирует с каждым импульсом — это расходник");
            return true;
        }
        if (d.kind == Kind.TRANSFORMER && c.aux[2] > 0 && c.power > Math.max(1, d.ratedPower) * 5) {
            c.burn("трансформатор перегружен: " + Num.watt(c.power) + " при " + Num.watt(d.ratedPower));
            noteIsland(isl, "вторичка трансформатора гружен — греется и сгорает");
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

    /**
     * Знаковый ток аккумулятора: сколько ампер банк отдаёт в вывод 0.
     * Положительный — разряжается, отрицательный — его заряжают. Модуль тока
     * решатель считает сам (branchCurrent), знак здесь — из stamp'а банки.
     */
    private static double batterySignedCurrent(CompInstance c, Mna mna, Island isl,
                                               Map<String, Double> v) {
        String a = rep(isl, c.pinNet[0]);
        String b = rep(isl, c.pinNet[1]);
        if (a == null || b == null) {
            return 0;
        }
        Mna.Stamp s = mna.findBranch(a, b);
        return s == null ? 0 : -Mna.branchCurrent(s, v, a);
    }

    /** Параметры банки: реестр (Def) + три настройки из config.yml. */
    static Battery.Params batteryParams(Config cfg, Def d) {
        return new Battery.Params(d.voltage, d.rInt,
                d.capacityAh > 0 ? d.capacityAh : 1.0, d.peukert,
                cfg.batteryChargeEff,
                Math.max(0.0, cfg.batterySelfDischargePerDay) / 100.0 / 24.0,
                Math.max(0.0, cfg.batterySulfationPerHour) / 100.0);
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
     * Сопротивление PTC-предохранителя. До температуры срабатывания оно почти
     * линейное (1 + 0.4 %/K), после — растёт на порядок каждые 2.5 °C, чем и
     * обеспечивается «щелчок» в защиту. Остывает — возвращается само: за это
     * деталь и называется самовосстанавливающейся.
     */
    private static double ptcResistance(CompInstance c, Def d) {
        double r0 = Math.max(1e-3, d.resistance > 0 ? d.resistance : 1.0);
        double t = Math.max(0, c.aux[0]);
        double tsw = d.tripTemp > 0 ? d.tripTemp : 90;
        if (t <= tsw) {
            return r0 * (1 + 0.004 * t);
        }
        return Math.min(1e7, r0 * Math.pow(10, Math.min(7, (t - tsw) / 2.5)));
    }

    /**
     * Проводимость варистора: I = K·U^α, K подбирается так, чтобы на паспортном
     * пороге через деталь шёл 1 мА. Выше порога α=9 даёт «кирпич»: 390 В держатся
     * и при 1 А, и при 10 А. Деградация уменьшает сопротивление утечки на порядок
     * за каждый поглощённый импульс — варистор расходник, а не вечный КЗ-защитник.
     */
    private static double movConductance(CompInstance c, Def d, double u) {
        double rOff = d.resistance > 0 ? d.resistance : 1e9;
        rOff = Math.max(1e3, rOff / Math.pow(10, c.aux[1]));
        double vc = d.vClamp > 0 ? d.vClamp : 1e9;
        double alpha = d.nlAlpha > 1 ? d.nlAlpha : 8;
        double k = 1e-3 / Math.pow(vc, alpha);
        double g = u > 1e-6 ? Math.min(2.0, k * Math.pow(u, alpha) / u) : 0;
        return 1.0 / rOff + g;
    }

    /** Один p-n переход: открыт — 25 Ом с ЭДС vf, закрыт — ток утечки. */
    private static void diodeEdge(Mna mna, Config cfg, Map<String, Double> prev,
                                  String from, String to, double vf) {
        double ud = prev.getOrDefault(from, 0.0) - prev.getOrDefault(to, 0.0);
        if (ud > vf) {
            mna.stamp(from, to, 1.0 / 25.0, vf / 25.0);
        } else {
            mna.stamp(from, to, cfg.gLeak(), 0);
        }
    }

    /** Диодный мост: 4 вывода, 0/1 — «~», 2 — «+», 3 — «−». */
    private static void stampBridge(Island isl, Mna mna, Config cfg, CompInstance c, Map<String, Double> prev) {
        String ac1 = rep(isl, c.pinNet[0]);
        String ac2 = rep(isl, c.pinNet[1]);
        String plus = rep(isl, c.pinNet[2]);
        String minus = rep(isl, c.pinNet[3]);
        if (ac1 == null || ac2 == null || plus == null || minus == null) {
            return;
        }
        double vf = c.def.vForward > 0 ? c.def.vForward : 0.55;
        diodeEdge(mna, cfg, prev, ac1, plus, vf);
        diodeEdge(mna, cfg, prev, minus, ac1, vf);
        diodeEdge(mna, cfg, prev, ac2, plus, vf);
        diodeEdge(mna, cfg, prev, minus, ac2, vf);
    }

    /**
     * Линейный стабилизатор как «идеальный источник + последовательное
     * сопротивление + ограничение по току». Uвых = min(Uцель, Uвх − dropout):
     * не хватает входа — деталь честно выпадает в режим резистора и пропускает
     * вход на выход (именно поэтому у 7805 просят 7.5 В, а не 5.1 В).
     */
    private static void stampRegulator(Island isl, Mna mna, Config cfg, CompInstance c, Map<String, Double> prev) {
        Def d = c.def;
        String in = rep(isl, c.pinNet[0]);
        String out = rep(isl, c.pinNet[1]);
        String gnd = rep(isl, c.pinNet[2]);
        if (in == null || out == null || gnd == null) {
            return;
        }
        double vg = prev.getOrDefault(gnd, 0.0);
        double target = Math.max(0, d.regulatorTarget(c.tuneFraction()));
        // Первый проход prev пуст — узла входа в нём ещё нет. Считать тогда vin = 0
        // значит «стабилизатор никогда не стартует» (он живёт своим же решением),
        // поэтому при неизвестном входе считаем вход «сколько угодно»: target
        // держится, а на следующих проходах prev уже знает реальное Uвх и мы
        // честно просядем на dropout.
        double vin = prev.containsKey(in) ? prev.getOrDefault(in, 0.0) - vg : Double.POSITIVE_INFINITY;
        double vout = Math.max(0, Math.min(target, vin - Math.max(0.2, d.dropout)));
        double rz = Math.max(0.05, d.internalResistance > 0 ? d.internalResistance : 0.6);
        // Ограничение по току. Смотрим на тог, что деталь отдавала на прошлом проходе:
        // Uвых/rz — это и есть ток КЗ (для 7805: 5 В / 0.6 Ом = 8 А). Пока он в
        // пределах паспорта — стабилизатор источник напряжения; как только вышел —
        // он источник тока на head, и Uвых проседает столько, сколько нужно. Так
        // деталь ведёт себя и на холостом ходу, и в коротыше, без «лишнего»
        // резистора в разрыв, который портил бы Uвых на нормальной нагрузке.
        double head = d.iLimit > 0 ? d.iLimit : 1.0;
        if (c.aux[0] > 100) {
            head *= Math.max(0.05, 1 - (c.aux[0] - 100) / 60.0);   // тепловая защита (корпус > 100°)
        }
        // берём именно знаковое «сколько отдали»: на просадке (dropout) выход
        // выше цели и деталь ничего не отдаёт — ограничивать нечего
        double iOut = prev.containsKey(out) ? (vout - (prev.get(out) - vg)) / rz : 0.0;
        boolean limit = iOut > head;
        c.aux[3] = limit ? head / Math.max(head, iOut) : 1.0;
        if (limit) {
            mna.stamp(out, gnd, 1.0 / 1e6, head);                  // генератор тока: держит head
        } else {
            mna.stamp(out, gnd, 1.0 / rz, vout / rz);
        }
        // Линейный стабилизатор берёт из входа ровно столько, сколько отдаёт в
        // нагрузку (плюс свои 5..10 мА) — именно поэтому его греют радиатором.
        // Штампуем это отдельной ветвью, иначе «коротыш на выходе» не было бы
        // видно по току питания, а он как раз и есть главный учебный сюжет.
        double iDeliver = Math.max(0.0, limit ? head : iOut);
        mna.stamp(in, gnd, cfg.gLeak(), -(Math.max(0.0005, d.iQuiescent) + iDeliver));
        if (vin < target + Math.max(0.2, d.dropout)) {
            // dropout: переход не открыт полностью -> выход подтянут входом через
            // сопротивление прохождения; иначе «нестabilизированный» режим выглядел бы
            // как полный обрыв, а не как «5 В не держатся, держи 7 В»
            mna.stamp(in, out, 1.0 / Math.max(1.0, rz * 30), 0);
        }
    }

    /**
     * Трансформатор = две связанные индуктивности с обратным Эйлером:
     *   u1 = R1·i1 + L1·di1/dt + M·di2/dt,  u2 = R2·i2 + M·di1/dt + L2·di2/dt
     * После дискретизации получаем 2-порт с проводимостями y** и «памятью» h**.
     * При M = 0 формула вырождается ровно в stampInductive (проверено в тестах),
     * поэтому вторичка без связи — это просто вторая катушка.
     */
    private static void stampTransformer(Island isl, Mna mna, CompInstance c, double dt) {
        Def d = c.def;
        String p1 = rep(isl, c.pinNet[0]);
        String p2 = rep(isl, c.pinNet[1]);
        String s1 = rep(isl, c.pinNet[2]);
        String s2 = rep(isl, c.pinNet[3]);
        if (p1 == null || p2 == null || s1 == null || s2 == null) {
            return;
        }
        double n = d.ratio > 1.0001 ? d.ratio : 1.0;
        double l1 = Math.max(1e-4, d.inductance);
        double l2 = l1 / (n * n);
        double k = Math.max(0.5, Math.min(0.9999, d.coupling > 0 ? d.coupling : 0.98));
        double m = k * Math.sqrt(l1 * l2);
        double r1 = Math.max(0.01, d.resistance);
        double r2 = Math.max(0.01, r1 / (n * n));
        double a11 = r1 + l1 / dt;
        double a22 = r2 + l2 / dt;
        double a12 = m / dt;
        double det = a11 * a22 - a12 * a12;
        if (!(det > 1e-9)) {
            stampInductive(mna, c, p1, p2, dt, r1, l1);
            return;
        }
        double e1 = (l1 * c.aux[0] + m * c.aux[1]) / dt;
        double e2 = (m * c.aux[0] + l2 * c.aux[1]) / dt;
        mna.coupled(p1, p2, s1, s2, a22 / det, -a12 / det, -a12 / det, a11 / det,
                (a22 * e1 - a12 * e2) / det, (a11 * e2 - a12 * e1) / det);
    }

    /** Токи обмоток «как они получились» — та же формула, что в штампе. */
    private static double[] transformerCurrents(CompInstance c, Island isl, Map<String, Double> v, double dt) {
        Def d = c.def;
        double n = d.ratio > 1.0001 ? d.ratio : 1.0;
        double l1 = Math.max(1e-4, d.inductance);
        double l2 = l1 / (n * n);
        double k = Math.max(0.5, Math.min(0.9999, d.coupling > 0 ? d.coupling : 0.98));
        double m = k * Math.sqrt(l1 * l2);
        double r1 = Math.max(0.01, d.resistance);
        double r2 = Math.max(0.01, r1 / (n * n));
        double u1 = nodeVoltage(isl, v, c.pinNet, 0) - nodeVoltage(isl, v, c.pinNet, 1);
        double u2 = nodeVoltage(isl, v, c.pinNet, 2) - nodeVoltage(isl, v, c.pinNet, 3);
        double a11 = r1 + l1 / dt;
        double a22 = r2 + l2 / dt;
        double a12 = m / dt;
        double det = a11 * a22 - a12 * a12;
        if (!(det > 1e-9)) {
            return new double[]{c.aux[0], c.aux[1]};
        }
        double e1 = (l1 * c.aux[0] + m * c.aux[1]) / dt;
        double e2 = (m * c.aux[0] + l2 * c.aux[1]) / dt;
        return new double[]{((u1 + e1) * a22 - (u2 + e2) * a12) / det,
                ((u2 + e2) * a11 - (u1 + e1) * a12) / det};
    }

    /** Петля ограничения тока стабилизатора: возвращает true, если коэффициент поменялся. */
    private static boolean updateLimits(Island isl) {
        // К.т. стабилизатора теперь считается прямо в stampRegulator (по току отдачи
        // с прошлого прохода) — здесь было бы не на что опереться: провод ветви
        // «IN–OUT» через проходит только в dropout'е, и ограничение никогда не
        // включалось. Функция оставлена как точка роста для других видов лимитов.
        return false;
    }

    /**
     * Восстановление сгоревшей детали на паяльном столе: снимает отметку
     * неисправности и обнуляет переходную память (накопленный I²t, перегрев,
     * деградацию). Возвращает false, если чинить нечего.
     */
    public static boolean repair(CompInstance c) {
        if (c == null || !c.burnt()) {
            return false;
        }
        c.clearFault();
        java.util.Arrays.fill(c.aux, 0);
        c.iPrev = 0;
        c.vcPrev = 0;
        c.brightness = 0;
        return true;
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
        if (d.acHz > 0) {
            // Фаза — общий счётчик шагов симуляции, а не память детали: сетку
            // редактора пересобирают заново на каждый прогон, и у aux[3] не было бы
            // шанса дорасти до полупериода (источник застрял бы на нуле).
            // Честные 50 Гц на шаге dt = 50 мс не размещаются (Никвист 10 Гц),
            // поэтому у источника в реестре 1 Гц: период 20 шагов — видно и
            // переходный процесс моста с конденсатором, и переход через ноль.
            double period = 1.0 / Math.max(0.01, d.acHz);
            double t = (acPhaseStep * Math.max(0.005, cfg.dt())) % period;
            return c.tunedVoltage() * Math.sin(2 * Math.PI * t / period);
        }
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
