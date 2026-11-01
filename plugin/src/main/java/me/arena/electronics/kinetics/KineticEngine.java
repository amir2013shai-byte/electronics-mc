package me.arena.electronics.kinetics;

import me.arena.electronics.Config;
import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.circuit.Dynamo;
import me.arena.electronics.circuit.CompStates;
import me.arena.electronics.kinetics.Kinetics.Action;
import me.arena.electronics.kinetics.Kinetics.Element;
import me.arena.electronics.kinetics.Kinetics.Material0;
import me.arena.electronics.kinetics.Kinetics.Motor;
import me.arena.electronics.kinetics.Kinetics.Part;
import me.arena.electronics.kinetics.Kinetics.Result;
import me.arena.electronics.kinetics.Kinetics.Role;
import me.arena.electronics.kinetics.Kinetics.Spec;
import me.arena.electronics.kinetics.Kinetics.State;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.registry.Orientation;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Механика в мире: собирает детали привода в установки, считает их через
 * {@link Kinetics}, а результат возвращает и в мир (сломать блок, сдвинуть каретку),
 * и в электрику (обороты → back-EMF).
 *
 * <h2>Почему это не «читерские обороты»</h2>
 * Единственное, что движок берёт из мира, — ток обмотки. Момент мотора считается как
 * {@code M = k·I}, где {@code k = U₀/ω₀} (та же константа, что даёт обратную ЭДС в stampAll),
 * а значит:
 * <ul>
 *   <li>на холостом ходу ток мал → момент мал → обороты растут, пока ЭДС не уравновесит U;</li>
 *   <li>пила упёрлась → обороты падают → ЭДС падает → ток растёт → греется обмотка,
 *       срабатывает PTC и плавится вставка. Нагрузка в мире реально видна в цепи;</li>
 *   <li>мощность не берётся из воздуха: больше трёх инструментов — дольше каждый блок,
 *       редуктор мощности не добавляет — он только меняет момент на обороты.</li>
 * </ul>
 *
 * <p>Обратно в {@code Simulation} пишется {@code speedPrev} (оборотовая доля) — и только.
 * Дальше решает решатель.</p>
 */
public final class KineticEngine {

    private static final BlockFace[] SIX = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
            BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
    /** Сколько блоков успевает срубить одна пила за проход ствола (защита от «комбайна»). */
    private static final int MAX_FELL_BLOCKS = 48;

    /** Одна установка: детали, их состояние и то, что к ней подведено. */
    public static final class Rig {
        public final List<CompInstance> parts = new ArrayList<>();
        public final List<CompInstance> motors = new ArrayList<>();
        /** Генераторы на этом же валу: они не крутят, а тормозят. */
        public final List<CompInstance> dynamos = new ArrayList<>();
        public final State state = new State();
        public final Result last = new Result();
        public Spec spec = new Spec();
        /** Куда смотрит «перед» каретки (вдоль инструмента). */
        public Orientation forward = Orientation.NORTH;
        public String handle = "";
        public String blockNote = "";
        private long lastSoundTick;
        private int breaks;
        private long windowStart;

        public double rpm() {
            return state.rpm();
        }
    }

    private final ElectronicsPlugin plugin;
    /** handle → установка (переживает перестановку блоков, иначе каретка теряла бы обороты). */
    private final Map<String, Rig> rigs = new LinkedHashMap<>();
    /** позиция детали → handle: по ней мы снова находим свою установку после хода. */
    private final Map<String, String> ownerByKey = new HashMap<>();
    private final Set<String> ownedMotors = new HashSet<>();
    /** Что тронула учебная сборка (блок и что на нём было) — чтобы умело убрать за собой. */
    private final List<Block> demoTouched = new ArrayList<>();
    private final List<Material> demoPrev = new ArrayList<>();
    private long tickCount;
    private long lastBreaks;
    private String lastNote = "";

    public KineticEngine(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    public int rigCount() {
        return rigs.size();
    }

    public Collection<Rig> rigs() {
        return rigs.values();
    }

    public long blocksBroken() {
        return lastBreaks;
    }

    public String lastNote() {
        return lastNote;
    }

    /** Установка, ближе всего к точке (для {@code /elec rig}). */
    public Rig nearest(org.bukkit.Location loc, double radius) {
        Rig best = null;
        double bd = radius * radius;
        for (Rig r : rigs.values()) {
            for (CompInstance c : r.parts) {
                Block b = c.key.block();
                if (b == null) {
                    continue;
                }
                double d = b.getLocation().distanceSquared(loc);
                if (d < bd) {
                    bd = d;
                    best = r;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ тик

    /** Вызывается из {@code Circuit.engineTick()} сразу после расчёта цепей. */
    public void tick() {
        tickCount++;
        Config cfg = plugin.config();
        Set<String> aliveMotors = new HashSet<>();
        if (!cfg.kineticsEnabled) {
            releaseOwned();
            rigs.clear();
            ownerByKey.clear();
            return;
        }
        double dt = Math.max(0.02, Math.min(0.25, cfg.dt()));
        Map<String, Rig> next = new LinkedHashMap<>();
        Map<String, String> nextOwner = new HashMap<>();

        for (Rig r : group(cfg)) {
            // 1) электрика → момент (единственное, что движок берёт из решателя)
            Motor motor = null;
            double torque = 0.0;
            double rpm = 0.0;
            for (CompInstance m : r.motors) {
                double k = Kinetics.torqueConstant(Math.max(1.0, m.def.ratedVoltage),
                        Math.max(1.0, m.def.rpm));
                // Момент берём не мгновенным, а с фильтром первого порядка на τ = 60 мс
                // — это постоянная тока якоря (индуктивность обмотки и драйвер). Без
                // фильтра пила входила в дерево на полутакте и вылетала на полутакте,
                // а {@code Kinetics.step} при нехватке момента обнуляет недопиленный
                // проход — механизм застревал в предельном цикле и не резал вовсе.
                // Ток — из решателя: ЭДС он получает долей паспортных оборотов,
                // поэтому на разносе ток сам падает к нулю и момент не «залипает».
                double raw = k * Math.abs(m.current);
                // фильтр на 60 мс — постоянная тока якоря и драйвера
                m.kinTorque += (raw - m.kinTorque) * (dt / (0.06 + dt));
                torque += m.kinTorque;
                rpm = Math.max(rpm, m.def.rpm);
                aliveMotors.add(sk(m.key));
            }
            // 1б) движители: рука на рукояти и вода на лопатках. Им не нужен
            // решатель — они отдают момент прямо в вал, и у каждого есть паспорт
            // в ваттах (rated_power) и холостые обороты (rpm), из которых честный
            // момент на стопе = 4P/ω₀.
            double moverTorque = 0.0;
            long nowMs = System.currentTimeMillis();
            for (CompInstance c : r.parts) {
                if (c.def.kineticRole() != Role.MOVER) {
                    continue;
                }
                double idle = Math.max(1.0, c.def.rpm);
                double t0 = Movers.stallTorque(c.def.ratedPower, idle);
                if (t0 <= 0.0) {
                    continue;
                }
                double share;
                if (c.def.isHandCrank()) {
                    share = nowMs < c.crankUntilMs ? 1.0 : 0.0;
                } else {
                    int[] wet = waterContact(c);
                    share = Math.min(1.0, wet[0] / (double) Movers.WATER_MAX_BLOCKS)
                            * (1.0 + Movers.WATER_HEAD_BONUS * Math.max(0, wet[1] - 1));
                }
                if (share > 0.0) {
                    moverTorque += Movers.torqueAt(r.state.omega, t0 * share, idle);
                    rpm = Math.max(rpm, idle);
                    c.brightness = (float) Math.min(1.0, share);
                }
            }
            torque += moverTorque;

            // 1в) тормоз: генератор отбирает момент ровно на k·I, и это единственная
            // «нагрузка», которая появляется из электрики сама, без инструмента.
            double brake = 0.0;
            for (CompInstance g : r.dynamos) {
                brake += Dynamo.torque(Dynamo.of(g.def), g.current);
            }
            // Момент тормоза живёт на быстроходной стороне вала, а Kinetics.step
            // считает баланс на стороне привода — значит на приводе это
            // T_нагрузки × передаточное число. Без пересчёта генератор за тремя
            // редукторами (×27) отдавал бы 273 Вт при руке, способной на 90 Вт:
            // нарушался бы закон сохранения энергии, и это была бы вечная игрушка.
            brake *= Math.max(1.0, r.spec.gearProduct);

            motor = new Motor(rpm > 0 ? rpm : 1800.0, torque, false);

            // 2) механика
            Kinetics.step(motor, r.spec, r.state, Double.MAX_VALUE, dt, r.last, brake);

            // 3) обратно в электрику: обороты задаёт механика, а не «напряжение на клеммах»
            double w0 = Kinetics.toOmega(Math.max(1.0, rpm > 0 ? rpm : 1800.0));
            double share = r.spec.gearProduct > 0 ? r.spec.gearProduct : 1.0;
            for (CompInstance m : r.motors) {
                m.kinOwned = true;
                m.kinOmega = r.state.omega * share;
                m.kinLoad = r.state.loadFraction;
                m.speedPrev = Math.max(0.0, Math.min(1.15, m.kinOmega / Math.max(0.1, w0)));
            }
            // Генератору обороты нужнее, чем «хозяйство»: по ним решатель считает ЭДС.
            for (CompInstance g : r.dynamos) {
                g.kinOmega = r.state.omega * share;
                g.kinLoad = r.state.loadFraction;
                g.speedPrev = Math.max(0.0, Math.min(1.5, g.kinOmega / Math.max(0.1, w0)));
            }

            // 4) вид деталей: 0 — стоит, 2 — крутится, 3 — заклинило
            int vis = r.state.jammed ? CompStates.BURNT
                    : (r.state.omega > 0.4 ? CompStates.ON : CompStates.OFF);
            for (CompInstance c : r.parts) {
                if (c.state != vis) {
                    c.state = vis;
                    plugin.renderer().requestUpdate(c);
                }
                c.kinOmega = r.state.omega * share;
                c.spin = (c.spin + (float) (r.state.omega * 57.29578 * dt / Math.max(1.0, share))) % 360f;
            }

            // 5) мир
            apply(r, cfg);
            if (!r.state.note.isEmpty()) {
                lastNote = r.state.note;
            }
            String id = r.handle;
            next.put(id, r);
            for (CompInstance c : r.parts) {
                nextOwner.put(sk(c.key), id);
            }
        }
        rigs.clear();
        rigs.putAll(next);
        ownerByKey.clear();
        ownerByKey.putAll(nextOwner);
        releaseOwnedExcept(aliveMotors);
    }

    /**
     * Сколько воды касается колеса (боковые грани) и какой над ним напор (столб
     * воды сверху). Проточная или стоячая — считаем мокрым любой сосед-вода: в
     * Minecraft источник рядом с колесом всё равно стекает вниз, а ученику
     * важно другое — четырёх мокрых граней больше, чем есть, и напора «в ноль»
     * не бывает.
     */
    private static int[] waterContact(CompInstance c) {
        Block here = c.key.block();
        if (here == null || here.getWorld() == null) {
            return new int[]{0, 0};
        }
        int wet = 0;
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
            if (isWater(here.getRelative(f))) {
                wet++;
            }
        }
        int head = 0;
        Block up = here.getRelative(BlockFace.UP);
        while (isWater(up) && head < 4) {
            head++;
            up = up.getRelative(BlockFace.UP);
        }
        if (head > 0 && wet == 0) {
            wet = 1;                    // водопад сверху — тоже один рабочий поток
        }
        return new int[]{wet, head};
    }

    private static boolean isWater(Block b) {
        return b != null && b.getType() == Material.WATER;
    }

    /** Моторы, которые больше никто не крутит, возвращаем электрике. */
    private void releaseOwned() {
        for (String k : new ArrayList<>(ownedMotors)) {
            LocationKey lk = fromSk(k);
            if (lk == null) {
                continue;
            }
            CompInstance c = plugin.circuit().get(lk);
            if (c != null) {
                c.kinOwned = false;
                c.kinLoad = 0.0;
                c.kinTorque = 0.0;
            }
        }
        ownedMotors.clear();
    }

    private void releaseOwnedExcept(Set<String> alive) {
        for (String k : new ArrayList<>(ownedMotors)) {
            if (alive.contains(k)) {
                continue;
            }
            LocationKey lk = fromSk(k);
            CompInstance c = lk == null ? null : plugin.circuit().get(lk);
            if (c != null) {
                c.kinOwned = false;
                c.kinLoad = 0.0;
                // обороты не обнуляем одним махом: ротор с инерцией выбегает.
                // иначе малейшая заминка с «хозяином» (каретка сместила деталь на
                // клетку) сбрасывала ЭДС в ноль, решатель видел короткое замыкание,
                // а электрическая модель — шанс затереть обороты «по напряжению».
                c.kinOmega = c.kinOmega > 0.05 ? c.kinOmega * 0.6 : 0.0;
                c.kinTorque = 0.0;
            }
        }
        ownedMotors.clear();
        ownedMotors.addAll(alive);
    }

    // ------------------------------------------------------------------ сборка установки

    /**
     * Группы связных деталей привода (6 сторон). К группе подвешиваются моторы,
     * стоящие рядом с любой её деталью: несколько моторов на одном валу складывают
     * момент — как в реальности (и как два человека, крутящих одну рукоять).
     */
    private List<Rig> group(Config cfg) {
        Map<LocationKey, CompInstance> kin = new LinkedHashMap<>();
        for (CompInstance c : plugin.circuit().all()) {
            if (c.def.kineticRole() != null) {
                kin.put(c.key, c);
            }
        }
        List<Rig> out = new ArrayList<>();
        Set<LocationKey> seen = new HashSet<>();
        long handleSeq = 0;
        for (LocationKey start : kin.keySet()) {
            if (seen.contains(start)) {
                continue;
            }
            Rig r = new Rig();
            List<LocationKey> queue = new ArrayList<>();
            queue.add(start);
            seen.add(start);
            for (int i = 0; i < queue.size(); i++) {
                LocationKey k = queue.get(i);
                CompInstance c = kin.get(k);
                if (c == null) {
                    continue;
                }
                r.parts.add(c);
                for (BlockFace f : SIX) {
                    LocationKey n = k.shift(f.getModX(), f.getModY(), f.getModZ());
                    if (kin.containsKey(n) && seen.add(n)) {
                        queue.add(n);
                    }
                    CompInstance other = plugin.circuit().get(n);
                    if (other != null && other.def.drivesShaft() && !r.motors.contains(other)) {
                        r.motors.add(other);
                    }
                    if (other != null && other.def.isDynamo() && !r.dynamos.contains(other)) {
                        r.dynamos.add(other);
                    }
                }
            }
            // состояние переносим по «хозяину» детали — иначе ход каретки сбрасывал бы обороты
            String carry = null;
            for (CompInstance c : r.parts) {
                String h = ownerByKey.get(sk(c.key));
                if (h != null && rigs.containsKey(h)) {
                    carry = h;
                    break;
                }
            }
            if (carry != null) {
                Rig prev = rigs.get(carry);
                r.state.omega = prev.state.omega;
                r.state.travel = prev.state.travel;
                r.state.blocksDone = prev.state.blocksDone;
                r.state.progress.putAll(prev.state.progress);
                r.state.strikes.putAll(prev.state.strikes);
                r.handle = carry;
                r.breaks = prev.breaks;
                r.windowStart = prev.windowStart;
            } else {
                r.handle = "rig" + (++handleSeq) + "@" + sk(start);
            }
            buildSpec(r, cfg);
            out.add(r);
        }
        return out;
    }

    /** Из деталей и блоков перед ними — параметры установки. */
    private void buildSpec(Rig r, Config cfg) {
        Spec spec = new Spec();
        Orientation fwd = null;
        int movers = 0;
        for (CompInstance c : r.parts) {
            Role role = c.def.kineticRole();
            if (role == null) {
                continue;
            }
            double ratio = role == Role.SHAFT || role == Role.FLYWHEEL || role == Role.SAW
                    || role == Role.DRILL ? 1.0 : Math.max(1.0, c.def.ratio);
            Part p = new Part(role, new Element(ratio, c.def.coupling), c.def.inertia,
                    c.def.mass, c.def.radius, c.key.x, c.key.y, c.key.z);
            Orientation o = c.orientation == null ? Orientation.NORTH : c.orientation;
            Block front = c.key.block(o.axis);
            if (role.isTool() || role == Role.WHEEL) {
                p.tx = front == null ? p.x : front.getX();
                p.ty = front == null ? p.y : front.getY();
                p.tz = front == null ? p.z : front.getZ();
            }
            if (role.isTool() && front != null) {
                p.facing = materialFor(c, front);
            }
            if (role == Role.WHEEL) {
                Block below = c.key.block(BlockFace.DOWN);
                spec.mu = isRailish(below) ? Kinetics.MU_RAIL : Kinetics.MU_GROUND;
            }
            if (fwd == null && (role.isTool() || role == Role.WHEEL)) {
                fwd = o;
            }
            if (role == Role.MOVER) {
                // Трение несёт сам движитель: у рукояти оно маленькое (одна пара
                // втулок), у колеса — такое, чтобы насухо вставать, а не молоть.
                spec.dragNm += c.def.isHandCrank() ? Movers.CRANK_DRAG_NM
                        : Movers.WATER_IDLE_DRAG_NM;
                movers++;
            }
            spec.add(p);
        }
        r.spec = spec;
        r.forward = fwd == null ? Orientation.NORTH : fwd;
        double rpmSum = 0.0;
        for (CompInstance m : r.motors) {
            rpmSum = Math.max(rpmSum, m.def.rpm);
        }
        if (r.motors.isEmpty() && movers == 0) {
            r.state.note = "нет мотора рядом: ставь мотор привода вплотную к валу";
        }
    }

    /** Материал перед инструментом (через наш же классификатор — тот же, что в тесте). */
    private Material0 materialFor(CompInstance tool, Block front) {
        String name = front.getType().name();
        boolean ore = name.contains("_ORE") || name.endsWith("ORE");
        return Kinetics.fromName(name, ore, tool.def.shape);
    }

    /** Рельс, отполированная плита — то, по чему колесу легко катиться. */
    private static boolean isRailish(Block b) {
        if (b == null) {
            return false;
        }
        String n = b.getType().name();
        return n.contains("RAIL") || n.equals("GOLD_BLOCK") || n.equals("IRON_BLOCK")
                || n.equals("SMOOTH_QUARTZ") || n.equals("POLISHED_BLACKSTONE")
                || n.equals("ICE") || n.contains("PACKED_ICE") || n.contains("SEAGRASS")
                || n.equals("SLIME_BLOCK");
    }

    // ------------------------------------------------------------------ действия в мире

    private void apply(Rig r, Config cfg) {
        State st = r.state;
        if (st.actions.isEmpty()) {
            return;
        }
        for (Action a : new ArrayList<>(st.actions)) {
            switch (a.kind) {
                case "break", "fell" -> {
                    if (!cfg.kineticsBreak) {
                        st.note = "режим без разрушений (config: kinetics.break-blocks=false)";
                        continue;
                    }
                    long now = System.currentTimeMillis();
                    if (now - r.windowStart > 60_000L) {
                        r.windowStart = now;
                        r.breaks = 0;
                    }
                    if (r.breaks >= cfg.kineticsBlocksPerMinute) {
                        st.note = "лимит " + cfg.kineticsBlocksPerMinute
                                + " блоков/мин на механизм (config: kinetics.blocks-per-minute)";
                        continue;
                    }
                    Block b = blockAt(r, a.x, a.y, a.z);
                    if (b == null || b.getType().isAir()) {
                        continue;
                    }
                    r.breaks++;
                    lastBreaks++;
                    if ("fell".equals(a.kind)) {
                        fell(b, r);
                    } else {
                        mine(b, r);
                    }
                }
                case "move" -> advance(r, cfg);
                default -> {
                }
            }
        }
        if (st.engaged && tickCount - r.lastSoundTick >= 4 && cfg.soundsEnabled) {
            r.lastSoundTick = tickCount;
            Block src = r.parts.isEmpty() ? null : r.parts.get(0).key.block();
            if (src != null) {
                float pitch = (float) (0.55 + 0.35 * Math.min(1.0, 1.0 - st.loadFraction));
                src.getWorld().playSound(src.getLocation().add(0.5, 0.6, 0.5),
                        Sound.BLOCK_GRINDSTONE_USE, 0.45f, pitch);
                src.getWorld().spawnParticle(Particle.CRIT, src.getLocation().add(0.5, 0.7, 0.5),
                        6, 0.25, 0.15, 0.25, 0.05);
            }
        }
        if (st.jammed && tickCount % 8 == 0) {
            Block src = r.parts.isEmpty() ? null : r.parts.get(0).key.block();
            if (src != null) {
                src.getWorld().spawnParticle(Particle.CLOUD,
                        src.getLocation().add(0.5, 0.9, 0.5), 3, 0.2, 0.1, 0.2, 0.01);
            }
        }
    }

    private Block blockAt(Rig r, int x, int y, int z) {
        if (r.parts.isEmpty()) {
            return null;
        }
        Block anchor = r.parts.get(0).key.block();
        if (anchor == null) {
            return null;
        }
        World w = anchor.getWorld();
        return w.getBlockAt(x, y, z);
    }

    /** Бур: порода через тот же конвейер, что и кирка игрока (жила, сырьё, истощение). */
    private void mine(Block b, Rig r) {
        Material was = b.getType();
        World w = b.getWorld();
        if (plugin.events().harvestVein(b)) {
            // жила уже отдала сырьё OurItem'ом: ванильный дроп не нужен, поэтому
            // блок снимаем сами и проигрываем его звук разрушения вручную
            b.setType(Material.AIR);
            w.playEffect(b.getLocation().add(0.5, 0.5, 0.5), org.bukkit.Effect.STEP_SOUND, was);
        } else {
            b.breakNaturally();
        }
        w.spawnParticle(Particle.CRIT, b.getLocation().add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, 0.1);
        w.playSound(b.getLocation(), was.name().contains("DEEPSLATE")
                ? Sound.BLOCK_DEEPSLATE_BREAK : Sound.BLOCK_STONE_BREAK, 0.55f, 1.25f);
    }

    /**
     * Пила: срезан комель — остальное делает гравитация. Снимаем столб брёвнов вверх и
     * всё, что к нему липнет (листва, ветки), — ровно как это выглядит в Create, но
     * объяснимо физикой: энергия падения дерева уже в нём.
     */
    private void fell(Block base, Rig r) {
        List<Block> cut = new ArrayList<>();
        List<Block> edge = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Block cur = base;
        for (int i = 0; i < 32 && cur != null && isLog(cur); i++) {
            cut.add(cur);
            seen.add(key(cur));
            for (BlockFace f : SIX) {
                edge.add(cur.getRelative(f));
            }
            cur = cur.getRelative(BlockFace.UP);
        }
        // крона: облепляем ствол листвой/саженцами в 2 блока вокруг
        for (Block b : edge) {
            if (b != null && isFoliage(b) && seen.add(key(b))) {
                cut.add(b);
            }
        }
        int n = 0;
        for (Block b : cut) {
            if (n++ >= MAX_FELL_BLOCKS || b.getType().isAir()) {
                continue;
            }
            if (plugin.circuit().get(b) != null) {
                continue;      // чужая электроника деревом не считается
            }
            b.breakNaturally();
        }
        World w = base.getWorld();
        w.playSound(base.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.5f, 0.7f);
        r.state.note = "срублено блоков: " + n + " — дальше работала гравитация";
    }

    private static boolean isLog(Block b) {
        String n = b.getType().name();
        return n.contains("LOG") || n.contains("WOOD") || n.contains("HYPHAE")
                || n.contains("STEM") || n.contains("SHROOM") || n.equals("BAMBOO")
                || n.equals("BAMBOO_BLOCK");
    }

    private static boolean isFoliage(Block b) {
        String n = b.getType().name();
        return n.contains("LEAVES") || n.contains("SAPLING") || n.contains("SHRUB")
                || n.contains("AZALEA") || n.contains("HANGING") || n.contains("ROOTS");
    }

    /**
     * Ход каретки: весь механизм (вместе с мотором, который его питает) сдвигается на блок.
     * Требуем: свободно впереди, опора под колёсами, и чтобы целевая клетка была пуста.
     * Мотор едет с кареткой — а если из-за этого оборвётся провод, решатель просто
     * перестанет давать ток, и механизм встанет. Так честность держится сама.
     */
    private void advance(Rig r, Config cfg) {
        if (!cfg.kineticsMove) {
            r.state.note = "ход кареток выключен (config: kinetics.carriage-move=false)";
            r.state.travel = 0.0;
            return;
        }
        Orientation o = r.forward;
        if (o == Orientation.UP || o == Orientation.DOWN) {
            r.state.note = "каретка ходит по горизонтали: поверни инструмент (Shift+ПКМ)";
            r.state.travel = 0.0;
            return;
        }
        int dx = o.axis.getModX();
        int dz = o.axis.getModZ();
        List<CompInstance> movers = new ArrayList<>(r.parts);
        movers.addAll(r.motors);
        List<LocationKey> from = new ArrayList<>();
        List<Def> defs = new ArrayList<>();
        List<Orientation> orients = new ArrayList<>();
        for (CompInstance c : movers) {
            LocationKey to = c.key.shift(dx, 0, dz);
            Block tb = to.block();
            Block below = to.block(BlockFace.DOWN);
            if (tb == null || !tb.isEmpty()) {
                r.state.note = "упор: впереди " + tb.getType().name().toLowerCase(Locale.ROOT);
                r.state.travel = 0.0;
                return;
            }
            if (plugin.circuit().get(to) != null) {
                r.state.note = "впереди уже стоит деталь — каретке тесно";
                r.state.travel = 0.0;
                return;
            }
            if (below == null || below.isPassable()) {
                r.state.note = "под колёсами пусто: каретке нужен путь (рельс, плита, земля)";
                r.state.travel = 0.0;
                return;
            }
            from.add(c.key);
            defs.add(c.def);
            orients.add(c.orientation == null ? Orientation.NORTH : c.orientation);
        }
        Material base = cfg.baseBlock;
        // 1) снимаем старое
        for (int i = 0; i < from.size(); i++) {
            LocationKey k = from.get(i);
            Block old = k.block();
            plugin.circuit().remove(k);
            plugin.renderer().drop(k);
            plugin.circuit().persistence().clear(old);
            if (old != null && old.getType() == base) {
                old.setType(Material.AIR);
                plugin.circuit().persistence().markHost(old, false);
            }
        }
        // 2) ставим новое
        for (int i = 0; i < from.size(); i++) {
            LocationKey to = from.get(i).shift(dx, 0, dz);
            Block nb = to.block();
            if (nb != null && nb.getType().isAir()) {
                nb.setType(base);
                plugin.circuit().persistence().markHost(nb, true);
            }
            plugin.circuit().put(to, defs.get(i), orients.get(i));
            CompInstance nc = plugin.circuit().get(to);
            if (nc != null) {
                plugin.circuit().persistence().write(nc);
                plugin.renderer().requestUpdate(nc);
            }
        }
        // прогресс по старым координатам больше не нужен — инструменты теперь в других клетках
        r.state.progress.clear();
        r.state.strikes.clear();
        plugin.circuit().markDirty();
    }

    // ------------------------------------------------------------------ учебная сборка

    /**
     * Готовый механизм «пила пилит бревно» — чтобы не собирать 12 блоков руками на уроке.
     * Схема питания простая: источник → провод → предохранитель → мотор, а обратный путь
     * идёт через шину земли (её узел глобальный, как и в настоящем щите).
     *
     * @return что получилось (для {@code /elec rig place})
     */
    public String demo(World w, int x, int y, int z) {
        if (w == null) {
            return "нет такого мира";
        }
        List<String> missed = new ArrayList<>();
        demoTouched.clear();
        demoPrev.clear();
        // опора: каретка и детали должны стоять на чём-то
        // Под колёсами — рельс: на камне та же каретка не едет (μ 0,35 против 0,005),
        // и привод встанает коленом, тратя момент на трение, а не на рез.
        for (int i = -8; i <= 6; i++) {
            for (int dz = -1; dz <= 1; dz++) {
                Block below = w.getBlockAt(x + i, y - 1, z + dz);
                if (below == null || !below.getType().isAir()) {
                    continue;
                }
                note(below);
                below.setType(dz == 1 && (i == -1 || i == 0) ? Material.RAIL : Material.STONE);
            }
        }
        // то, что пила будет резать: одно бревно прямо перед диском.
        // Каретка за следующим бревном не едет нарочно: питание подведено
        // проводом сбоку, и на первом же метре пути мотор остался бы без
        // цепи — на станке для этого нужен кабельный барабан, а не учебная сборка.
        Block log = w.getBlockAt(x + 1, y, z);
        if (log != null && log.getType().isAir()) {
            note(log);
            log.setType(Material.OAK_LOG);
        }
        cell(w, x, y, z, "saw_kinetic", Orientation.EAST, missed);
        cell(w, x - 1, y, z, "flywheel_kinetic", Orientation.EAST, missed);
        cell(w, x - 2, y, z, "gearbox_3x", Orientation.EAST, missed);
        cell(w, x - 3, y, z, "shaft_kinetic", Orientation.EAST, missed);
        // колёса на рельсе: по камню та же каретка не трогается (тяги не хватает),
        // здесь они нужны ещё и как опора — без них пила висела бы в воздухе
        cell(w, x - 1, y, z + 1, "wheel_kinetic", Orientation.EAST, missed);
        cell(w, x, y, z + 1, "wheel_kinetic", Orientation.EAST, missed);
        // привод: мотор стоит сбоку от вала (6-й сосед — и цепочка, и электрика рядом).
        // Мотор с редуктором: без него пила садит мотор в режим короткого замыкания
        // (8 А на обмотке вчетверо сильнее паспортных 2,3 А) и обмотка перегорает —
        // ровно то, что происходит с настоящей пилой, если дать ей упор в бревно.
        cell(w, x - 3, y, z - 1, "motor_220v", Orientation.EAST, missed);
        cell(w, x - 4, y, z - 1, "wire_red", Orientation.EAST, missed);
        cell(w, x - 5, y, z - 1, "fuse_slow_10a", Orientation.EAST, missed);
        cell(w, x - 6, y, z - 1, "supply_220v", Orientation.EAST, missed);
        cell(w, x - 7, y, z - 1, "ground_bus", Orientation.EAST, missed);
        cell(w, x - 2, y, z - 1, "wire_red", Orientation.EAST, missed);
        cell(w, x - 1, y, z - 1, "wire_red", Orientation.EAST, missed);
        cell(w, x, y, z - 1, "ground_bus", Orientation.EAST, missed);
        return "механизм собран у " + x + "," + y + "," + z
                + (missed.isEmpty() ? "" : "; не хватает id: " + String.join(",", missed));
    }

    /**
     * Вторая учебная сборка — «свет от собственных рук»: рукоять, маховик,
     * машинный генератор и лампа на одном валу, а рядом водяное колесо, которое
     * крутится только стоя в воде. Нужна она для того, чтобы ученик (и самотест)
     * видел всю цепочку «усилие → вращение → ЭДС → ток → свет» целиком.
     *
     * @return что получилось (для {@code /elec rig place})
     */
    public String demoMovers(World w, int x, int y, int z) {
        if (w == null) {
            return "нет такого мира";
        }
        List<String> missed = new ArrayList<>();
        for (int i = 0; i <= 11; i++) {
            for (int dz = 0; dz <= 1; dz++) {
                Block below = w.getBlockAt(x + i, y - 1, z - dz);
                if (below != null && below.getType().isAir()) {
                    note(below);
                    below.setType(Material.STONE);
                }
            }
        }
        // вал: рукоять и маховик (без него рывки руки гоняли бы обороты туда-сюда),
        // рядом редуктор — он в том же механизме, но стоит сбоку, чтобы освободить
        // генератору вторую клемму под провод, а не под деталь вала.
        cell(w, x, y, z, "crank_hand", Orientation.EAST, missed);
        cell(w, x + 1, y, z, "flywheel_kinetic", Orientation.EAST, missed);
        // Редукторы обязательны: рука даёт 90 об/мин, а машине нужно 1500. Без ×9
        // ЭДС (0,72 В на одном редукторе — 2,16 В) не переживает падение на щётках
        // (0,6 В), и светодиода не хватает. Это тоже урок: генератор всегда считают
        // на обороты, а не на «силу тока в руках».
        cell(w, x + 2, y, z, "gearbox_3x", Orientation.EAST, missed);
        cell(w, x + 3, y, z, "gearbox_3x", Orientation.EAST, missed);
        // электрика генератора: «−» на землю, «+» через провод на лампу, и лампа
        // вторым выводом снова на землю (шина земли в модели глобальная). Рядом с западным выводом машины стоит именно земля, а не
        // деталь вала: иначе цепь была бы разомкнута, и генератор показывал бы
        // ЭДС без тока (ровно это и ловил самотест на первой версии сборки).
        cell(w, x + 1, y, z - 1, "ground_bus", Orientation.EAST, missed);
        cell(w, x + 2, y, z - 1, "dynamo_12v", Orientation.EAST, missed);
        cell(w, x + 3, y, z - 1, "wire_red", Orientation.EAST, missed);
        cell(w, x + 4, y, z - 1, "lamp", Orientation.EAST, missed);
        cell(w, x + 5, y, z - 1, "ground_bus", Orientation.EAST, missed);
        // водяное колесо: две мокрые грани и напор в одну единицу
        cell(w, x + 9, y, z, "wheel_water", Orientation.EAST, missed);
        for (int dx = 0; dx <= 1; dx++) {
            for (int dy = 0; dy <= 1; dy++) {
                Block wet = w.getBlockAt(x + 9 + (dx == 0 ? 1 : -1), y + dy, z);
                if (wet != null && wet.getType().isAir()) {
                    note(wet);
                    wet.setType(Material.WATER);
                }
            }
        }
        return "рукоять и колесо собраны у " + x + "," + y + "," + z
                + (missed.isEmpty() ? "" : "; не хватает id: " + String.join(",", missed));
    }

    /** Убрать сборку movers — тот же список затронутых блоков, что и у {@link #demo(World, int, int, int)}. */
    public void undoMovers() {
        undoDemo();
    }

    private void note(Block b) {
        demoTouched.add(b);
        demoPrev.add(b.getType());
    }

    /** Убрать учебную сборку: детали из цепей, блоки — как было. */
    public void undoDemo() {
        for (Block b : new ArrayList<>(demoTouched)) {
            LocationKey k = LocationKey.of(b);
            if (plugin.circuit().get(k) != null) {
                plugin.circuit().remove(k);
                plugin.renderer().drop(k);
                plugin.circuit().persistence().clear(b);
            }
        }
        for (int i = demoTouched.size() - 1; i >= 0; i--) {
            Block b = demoTouched.get(i);
            b.setType(demoPrev.get(i));
            plugin.circuit().persistence().markHost(b, !demoPrev.get(i).isAir());
        }
        demoTouched.clear();
        demoPrev.clear();
        rigs.clear();
        ownerByKey.clear();
        releaseOwned();
        plugin.circuit().markDirty();
    }

    /** Один блок-деталь учебной сборки. */
    private CompInstance cell(World w, int x, int y, int z, String defId, Orientation o,
                              List<String> missed) {
        me.arena.electronics.registry.Def def = me.arena.electronics.registry.Components.byId(defId);
        if (def == null) {
            missed.add(defId);
            return null;
        }
        LocationKey k = new LocationKey(w, x, y, z);
        Block b = k.block();
        if (b == null) {
            return null;
        }
        note(b);
        if (!b.getType().isAir()) {
            b.setType(Material.AIR);
        }
        b.setType(plugin.config().baseBlock);
        plugin.circuit().persistence().markHost(b, true);
        plugin.circuit().put(k, def, o);
        CompInstance c = plugin.circuit().get(k);
        if (c != null) {
            plugin.circuit().persistence().write(c);
            plugin.renderer().requestUpdate(c);
        }
        plugin.circuit().markDirty();
        return c;
    }

    // ------------------------------------------------------------------ мелочи

    private static String key(Block b) {
        return b.getWorld().getName() + ";" + b.getX() + ";" + b.getY() + ";" + b.getZ();
    }

    private static String sk(LocationKey k) {
        return k.worldName() + ";" + k.x + ";" + k.y + ";" + k.z;
    }

    private static LocationKey fromSk(String s) {
        String[] p = s.split(";");
        if (p.length != 4) {
            return null;
        }
        try {
            return new LocationKey(org.bukkit.Bukkit.getWorld(p[0]), Integer.parseInt(p[1]),
                    Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (RuntimeException ex) {
            return null;
        }
    }
}
