package me.arena.electronics.kinetics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Кинематика привода — чистая механика, без Bukkit (тот же код гоняет офлайн-тест).
 *
 * <h2>Откуда берутся числа</h2>
 * Электричество не превращается в обороты магически: MNA уже посчитал ток мотора, а дальше
 * работает школьная механика.
 * <ul>
 *   <li>{@code P = U·I} — мощность на валу (ток растёт, когда мотору тяжело: нагрузка
 *       попадает в {@link State#loadFraction} и обратно в {@code Simulation} через
 *       {@code speedPrev}, так что затёртая пила реально греет PTC и рвёт предохранитель);</li>
 *   <li>{@code M₀ = 4P/ω₀} — момент заклинивания линейной характеристики ДПТ
 *       {@code M(ω) = M₀(1 − ω/ω₀)}: максимум мощности на половине оборотов;</li>
 *   <li>{@code J·dω/dt = M − Mнагр} — разгон и остановка (поэтому маховик — не украшение:
 *       он держит обороты на ударе);</li>
 *   <li>{@code E = Jω²/2} — запас, который маховик отдаёт за один удар;</li>
 *   <li>{@code Fтяги = M/r} против {@code μ·m·g} — едет каретка или буксует;</li>
 *   <li>{@code t = W/P} — сколько секунд идёт работа.</li>
 * </ul>
 *
 * <h2>Почему энергии так мало</h2>
 * Инструмент не перемалывает блок — он снимает стружку. Пила делает пропил 0,5 × 40 мм
 * поперёк блока (2·10⁻⁵ м³), удельная энергия резания древесины ~1,5 МДж/м³ → 30 Дж.
 * Бур не дробит камень, а пробивает шпур Ø4 × 40 мм (5·10⁻⁷ м³) при ~30 МДж/м³ → 15 Дж,
 * а блок разрушается от четырёх шпуров (как в породе на самом деле). Отсюда и «дерево —
 * 30 Дж, камень — 60 Дж»: это работа снятого материала, а не объём блока.
 *
 * <p>Дерево падает от одного пропила у комля — вот почему пила сносит весь ствол: дальше
 * работает потенциальная энергия дерева, а не мотор.</p>
 */
public final class Kinetics {

    private Kinetics() {
    }

    /** об/мин → рад/с. */
    public static final double RPM_PER_RAD = 2.0 * Math.PI / 60.0;
    /** ускорение свободного падения, м/с². */
    public static final double G = 9.81;
    /** стальное колесо по рельсу (каретка): трение качения маленькое — потому и едет. */
    public static final double MU_RAIL = 0.005;
    /** тащится по земле (стружка, грунт, неровности): на два порядка больше, чем по рельсу. */
    public static final double MU_GROUND = 0.35;
    /** полярный предел: больше этого числа событий за тик мир не получает (защита от комбайна). */
    public static final int MAX_ACTIONS_PER_TICK = 16;
    /** трение в подшипниках и щётках — доля момента, чтобы на холостом ходу не было ∞ об/мин. */
    public static final double FRICTION_FRACTION = 0.06;

    // ------------------------------------------------------------------ материалы

    /** Кусок мира, с которым работает инструмент. */
    public static final class Material0 {
        /** Русское имя — в подсказку и в лог. */
        public final String name;
        /** Работа одного прохода (пропил / шпур), Дж. */
        public final double workJ;
        /** Момент сопротивления на валу инструмента, Н·м. */
        public final double torqueNm;
        /** Сколько проходов нужно, чтобы блок отдался (бур: 4 шпура). */
        public final int strikes;
        /** "wood" | "rock" | "ore" | "none" — для «свой/чужой» у пилы и бура. */
        public final String group;
        /** true: это часть дерева — после пропила комля рубится весь ствол. */
        public final boolean tree;

        public Material0(String name, double workJ, double torqueNm, int strikes,
                         String group, boolean tree) {
            this.name = name;
            this.workJ = Math.max(0.0, workJ);
            this.torqueNm = Math.max(0.0, torqueNm);
            this.strikes = Math.max(1, strikes);
            this.group = group == null ? "none" : group;
            this.tree = tree;
        }

        public boolean workable() {
            return workJ > 0.0;
        }

        /** Полная работа над блоком, Дж. */
        public double totalJ() {
            return workJ * strikes;
        }

        @Override
        public String toString() {
            return name + ": " + (int) workJ + " Дж × " + strikes
                    + ", момент " + String.format("%.3f", torqueNm) + " Н·м";
        }
    }

    /**
     * Пила: пропил 1 мм поперёк блока (1 м² × 1 мм = 10⁻³ м³) при удельной энергии
     * резания древесины ~1,5 МДж/м³ → 1500 Дж. Доски чуть плотнее склейка — то же.
     */
    public static final Material0 WOOD = new Material0("дерево", 1500.0, 0.90, 1, "wood", true);
    public static final Material0 LEAVES = new Material0("листва", 40.0, 0.05, 1, "wood", true);
    public static final Material0 PLANKS = new Material0("доски", 1200.0, 0.75, 1, "wood", false);
    /**
     * Бур: четыре шпура Ø4 × 400 мм (5·10⁻⁷ м³ каждый) по породе с удельной
     * энергией разрушения ~30 МДж/м³ → 1200 Дж на шпур, 4800 Дж на блок.
     */
    public static final Material0 STONE = new Material0("камень", 1200.0, 2.20, 4, "rock", false);
    public static final Material0 DEEPSLATE = new Material0("глубинный сланец", 1600.0, 3.00, 5, "rock", false);
    public static final Material0 ORE = new Material0("руда в породе", 1300.0, 2.60, 5, "ore", false);
    public static final Material0 SOFT = new Material0("рыхлый грунт", 250.0, 0.35, 2, "none", false);
    /** Не берётся никем — по такому блоку видно, что механизму не хватает момента. */
    public static final Material0 REFRACTORY = new Material0("препятствие", 0.0, 40.0, 1, "none", false);
    /** Пустота: работать нечем, механизм на холостом ходу. */
    public static final Material0 NOTHING = new Material0("пустота", 0.0, 0.0, 1, "none", false);

    /**
     * Что инструмент увидит перед собой. {@code role} — форма детали: {@code saw} / {@code drill}.
     * Не по материалу — возвращаем {@link #REFRACTORY}: механизм встанет и честно объяснит почему.
     */
    public static Material0 fromName(String matName, boolean isOre, String role) {
        if (matName == null || matName.isEmpty() || "AIR".equals(matName)
                || "CAVE_AIR".equals(matName) || "VOID_AIR".equals(matName)) {
            return NOTHING;
        }
        boolean obsidianish = matName.contains("OBSIDIAN") || matName.contains("BEDROCK")
                || matName.contains("ANCIENT") || matName.contains("BEACON")
                || matName.contains("ENCHANT") || matName.contains("ANHATE")
                || matName.contains("ANCIENT_DEBRIS");
        if (obsidianish) {
            return REFRACTORY;
        }
        boolean wood = matName.contains("LOG") || matName.contains("WOOD")
                || matName.contains("STRIPPED") || matName.contains("BARK")
                || matName.contains("SHROOM") || matName.contains("STEM")
                || matName.contains("HYPHAE") || matName.contains("CRIMSON")
                || matName.contains("WARPED");
        boolean foliage = matName.contains("LEAVES") || matName.contains("SAPLING")
                || matName.contains("FENCE") || matName.contains("SIGN")
                || matName.contains("LOOM") || matName.contains("BARREL")
                || matName.contains("CHEST") || matName.contains("CRAFTING")
                || matName.contains("BOOKSHELF") || matName.contains("NOTE_BLOCK")
                || matName.contains("LADDER") || matName.contains("WORKBENCH")
                || matName.contains("SPONGE") || matName.contains("BAMBOO");
        boolean planks = matName.contains("PLANK") || matName.contains("SLAB")
                || matName.contains("STAIRS") || matName.contains("TRAPDOOR")
                || matName.contains("SHELF");
        boolean rock = matName.contains("STONE") || matName.contains("DEEPSLATE")
                || matName.contains("GRANITE") || matName.contains("DIORITE")
                || matName.contains("ANDESITE") || matName.contains("NETHERRACK")
                || matName.contains("COBBLE") || matName.contains("BLACKSTONE")
                || matName.contains("BASALT") || matName.contains("END_STONE")
                || matName.contains("BRICK") || matName.contains("CONCRETE")
                || matName.contains("TERRACOTTA") || matName.contains("GLASS")
                || matName.contains("QUARTZ") || matName.contains("GLOWSTONE")
                || matName.contains("ORE") || matName.contains("GOLD_BLOCK")
                || matName.contains("IRON_BLOCK") || matName.contains("PRISMARINE");
        boolean soft = matName.contains("DIRT") || matName.contains("SAND")
                || matName.contains("GRAVEL") || matName.contains("SNOW")
                || matName.contains("CLAY") || matName.contains("MOSS")
                || matName.contains("MUD") || matName.contains("MYCELIUM")
                || matName.contains("GRASS_BLOCK") || matName.contains("PODZOL")
                || matName.contains("COARSE") || matName.contains("ROOTS");
        if ("saw".equals(role)) {
            if (wood) {
                return WOOD;
            }
            if (foliage) {
                return LEAVES;
            }
            return planks ? PLANKS : REFRACTORY;   // камень пиле не по зубам
        }
        if ("drill".equals(role)) {
            if (rock) {
                if (isOre) {
                    return ORE;
                }
                return matName.contains("DEEPSLATE") ? DEEPSLATE : STONE;
            }
            if (soft) {
                return SOFT;
            }
            return REFRACTORY;                      // бур по дереву не работает
        }
        // вал/шестерня/маховик/колесо: блок перед ними — просто препятствие
        if (wood) {
            return PLANKS;
        }
        return rock ? STONE : soft ? SOFT : NOTHING;
    }

    /** Рудное ли это сырьё (бур тогда отдаёт кусок руды, а не булыжник). */
    public static boolean isOreGroup(Material0 m) {
        return m != null && "ore".equals(m.group);
    }

    /** Порода, которую бур берёт (для «жилы»: сырьё идёт через наш же pipeline). */
    public static boolean isRockGroup(Material0 m) {
        return m != null && ("rock".equals(m.group) || "ore".equals(m.group));
    }

    // ------------------------------------------------------------------ передачи

    /** Передаточное отношение пары и её КПД. */
    public static final class Element {
        public final double ratio;
        public final double efficiency;

        public Element(double ratio, double efficiency) {
            this.ratio = Math.max(0.05, ratio);
            this.efficiency = Math.min(1.0, Math.max(0.05, efficiency));
        }

        /** Прямая передача: вал — это редуктор 1:1 с потерями подшипников. */
        public static final Element SHAFT = new Element(1.0, 0.99);
        /** Шестерни 1:3 (наш gearbox): момент ×2,7, обороты ÷3. */
        public static final Element GEAR3 = new Element(3.0, 0.9);
    }

    /** Характеристика мотора: обороты холостого хода и момент на валу. */
    public static final class Motor {
        public final double rpmIdle;
        public final double torqueStall;
        /**
         * true — момент задан «кривой» (M₀ при ω=0, линейное падение до нуля на ω₀): так
         * считается автономная проверка. false — момент уже взят из электрики (M = k·I, где
         * I посчитал MNA), тогда падать он должен сам — через падение тока.
         */
        public final boolean linear;

        public Motor(double rpmIdle, double torqueStall, boolean linear) {
            this.rpmIdle = Math.max(1.0, rpmIdle);
            this.torqueStall = Math.max(0.0, torqueStall);
            this.linear = linear;
        }

        /** Мотор из электрики: k = U₀/ω₀, M = k·I — момент прямо из решателя. */
        public static Motor fromCurrents(double rpmNominal, double ratedVoltage, double amps) {
            double w0 = Math.max(1.0, rpmNominal) * RPM_PER_RAD;
            double k = ratedVoltage / w0;
            return new Motor(rpmNominal, k * Math.max(0.0, amps), false);
        }

        @Override
        public String toString() {
            return (int) rpmIdle + " об/мин, M₀ = " + String.format("%.3f", torqueStall) + " Н·м";
        }
    }

    /**
     * constant мотора: k = U₀/ω₀ (В·с/рад = Н·м/А). Back-EMF = k·ω, момент = k·I —
     * одна и та же константа с двух сторон, это и есть причина, по которой мотор
     * на холостом ходу тока почти не берёт.
     */
    public static double torqueConstant(double ratedVoltage, double rpmNominal) {
        return ratedVoltage / Math.max(1.0, toOmega(rpmNominal));
    }

    /** Ток якоря: I = (U − k·ω)/(R_обмотки + R_источника) — то же самое считает MNA. */
    public static double motorCurrent(double volts, double omega, double ratedVoltage,
                                      double rpmNominal, double windingR, double sourceR) {
        double k = torqueConstant(ratedVoltage, rpmNominal);
        return Math.max(0.0, (volts - k * Math.max(0.0, omega))
                / (Math.max(0.001, windingR) + Math.max(0.0, sourceR)));
    }

    /** Мотор «как его видит решатель»: момент k·I при текущих оборотах. */
    public static Motor motorAtSpeed(double rpmNominal, double volts, double ratedVoltage,
                                     double omega, double windingR, double sourceR) {
        return Motor.fromCurrents(rpmNominal, ratedVoltage,
                motorCurrent(volts, omega, ratedVoltage, rpmNominal, windingR, sourceR));
    }

    /** Мотор из мощности: P = η·U·I, момент заклинивания M₀ = 4P/ω₀ (линейная модель). */
    public static Motor motorFromPower(double rpmNominal, double powerW, double efficiency) {
        double w0 = Math.max(1.0, rpmNominal) * RPM_PER_RAD;
        double p = Math.max(0.0, powerW) * Math.min(1.0, Math.max(0.05, efficiency));
        return new Motor(rpmNominal, 4.0 * p / w0, true);
    }

    /** Что получилось на выходном валу: обороты вниз, момент вверх. */
    public static final class Drive {
        public final double rpmOut;
        public final double torqueOut;

        Drive(double rpmOut, double torqueOut) {
            this.rpmOut = rpmOut;
            this.torqueOut = torqueOut;
        }
    }

    public static Drive solve(Motor m, List<Element> gears) {
        double rpm = m.rpmIdle;
        double torque = m.torqueStall;
        for (Element g : gears) {
            rpm /= g.ratio;
            torque *= g.ratio * g.efficiency;
        }
        return new Drive(rpm, torque);
    }

    // ------------------------------------------------------------------ маховик

    public static double flywheelEnergy(double inertia, double omega) {
        return 0.5 * Math.max(0.0, inertia) * omega * omega;
    }

    /** Обороты после того, как сняли энергию (не отрицательные: больше запаса не отдаст). */
    public static double rpmAfterTake(double inertia, double rpmBefore, double energyJ) {
        double w0 = Math.max(0.0, rpmBefore) * RPM_PER_RAD;
        return toRpm(omegaAfterTake(inertia, w0, energyJ));
    }

    /** То же в рад/с — движку удобнее. */
    public static double omegaAfterTake(double inertia, double omegaBefore, double energyJ) {
        double j = Math.max(1e-6, inertia);
        double w2 = omegaBefore * omegaBefore - 2.0 * Math.max(0.0, energyJ) / j;
        return w2 <= 0.0 ? 0.0 : Math.sqrt(w2);
    }

    public static double toRpm(double omega) {
        return Math.max(0.0, omega) / RPM_PER_RAD;
    }

    public static double toOmega(double rpm) {
        return Math.max(0.0, rpm) * RPM_PER_RAD;
    }

    /** Новое ω после приращения момента (явный Эйлер; инерция J, шаг dt). */
    public static double stepOmega(double inertia, double omega, double torqueNet, double dt) {
        double j = Math.max(1e-4, inertia);
        return Math.max(0.0, omega + torqueNet / j * Math.max(0.0, dt));
    }

    /** Сколько секунд понадобится мощности p, чтобы сделать работу w. */
    public static double secondsFor(double workJ, double powerW) {
        return workJ / Math.max(0.001, powerW);
    }

    /** Время разгона до ω с учётом постоянства момента (приближённо: t = Jω/M). */
    public static double spinUpSeconds(double inertia, double omega, double torque) {
        return inertia * omega / Math.max(0.001, torque);
    }

    // ------------------------------------------------------------------ каретка

    /** Итог проверки «едет или стоит». */
    public static final class Traction {
        public final double forceN;
        public final double dragN;
        public final double speedMps;

        Traction(double forceN, double dragN, double speedMps) {
            this.forceN = forceN;
            this.dragN = dragN;
            this.speedMps = speedMps;
        }

        public boolean moves() {
            return forceN > dragN && speedMps > 0.0;
        }
    }

    /**
     * Колесо: {@code F = M/r}, сопротивление {@code μ·m·g}, скорость {@code v = ω·r/red}.
     * {@code reduce} — редуктор привода колёс (иначе каретка летела бы 8 м/с).
     */
    public static Traction carriage(double omega, double torqueNm, double r, double mass,
                                    double mu, double reduce) {
        double rr = Math.max(0.02, r);
        double m = Math.max(0.5, mass);
        double red = Math.max(1.0, reduce);
        double atWheel = Math.max(0.0, torqueNm) * red * 0.9;
        double force = atWheel / rr;
        double drag = Math.max(0.0, mu) * m * G;
        double v = Math.max(0.0, omega) / red * rr;
        return new Traction(force, drag, force > drag ? v : 0.0);
    }

    /** Строка для GUI/подсказки: почему едет или почему стоит. */
    public static String explainCarriage(double omega, double torqueNm, double r, double mass,
                                         double mu, double reduce) {
        Traction t = carriage(omega, torqueNm, r, mass, mu, reduce);
        String s = String.format("тяга %.1f Н, сопротивление %.1f Н", t.forceN, t.dragN);
        return t.moves()
                ? s + String.format(" — едет %.2f м/с", t.speedMps)
                : s + " — буксует: нужен редуктор побольше, колесо помельче или опора легче";
    }

    // ------------------------------------------------------------------ установка

    /** Роль детали в цепочке — берётся из «shape» компонента. */
    public enum Role {
        SHAFT, GEAR, FLYWHEEL, WHEEL, SAW, DRILL,
        /** Движитель: рука или вода. Момент отдаёт, не берёт. */
        MOVER;

        public static Role of(String shape) {
            if (shape == null) {
                return SHAFT;
            }
            return switch (shape) {
                case "gear" -> GEAR;
                case "flywheel" -> FLYWHEEL;
                case "wheel" -> WHEEL;
                case "saw" -> SAW;
                case "drill" -> DRILL;
                case "crank", "waterwheel" -> MOVER;
                default -> SHAFT;
            };
        }

        public boolean isTool() {
            return this == SAW || this == DRILL;
        }

        public String ru() {
            return switch (this) {
                case GEAR -> "редуктор";
                case FLYWHEEL -> "маховик";
                case WHEEL -> "колесо";
                case SAW -> "пила";
                case DRILL -> "бур";
                case MOVER -> "движитель";
                default -> "вал";
            };
        }
    }

    /** Одна деталь установки. */
    public static final class Part {
        public final Role role;
        public final Element element;
        public final double inertia;
        public final double mass;
        public final double radius;
        /** Что лежит перед деталью (для инструмента — то, что она пытается взять). */
        public Material0 facing;
        /** Координаты детали — по ним движок передвигает каретку. */
        public final int x;
        public final int y;
        public final int z;
        /** Клетка, на которую направлен инструмент (деталь + её ось). */
        public int tx;
        public int ty;
        public int tz;

        public Part(Role role, Element element, double inertia, double mass, double radius,
                    int x, int y, int z) {
            this.role = role;
            this.element = element == null ? Element.SHAFT : element;
            this.inertia = Math.max(0.0, inertia);
            this.mass = Math.max(0.0, mass);
            this.radius = radius <= 0.0 ? 0.15 : radius;
            this.facing = NOTHING;
            this.tx = x;
            this.ty = y;
            this.tz = z;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    /** Параметры установки целиком (считаются при сканировании мира). */
    public static final class Spec {
        public final List<Part> parts = new ArrayList<>();
        public double inertiaTotal = 2e-4;
        public double massTotal = 1.0;
        /** Передаточное отношение до инструмента. */
        public double gearProduct = 1.0;
        /** КПД всей цепочки передач. */
        public double efficiency = 0.99;
        public boolean hasWheels;
        public double wheelRadius = 0.15;
        /** Редуктор хода (колёса крутятся медленнее вала). */
        public double wheelReduce = 8.0;
        /** Трение опоры под кареткой (рельс или земля). */
        public double mu = MU_GROUND;
        /** Идущий привод: 0 — просто каретка без инструментов. */
        public int toolCount;

        public void add(Part p) {
            parts.add(p);
            inertiaTotal += p.inertia;
            massTotal += p.mass;
            if (p.role == Role.GEAR) {
                gearProduct *= p.element.ratio;
                efficiency *= p.element.efficiency;
            } else if (p.role == Role.SHAFT) {
                efficiency *= p.element.efficiency;
            }
            if (p.role == Role.WHEEL) {
                hasWheels = true;
                wheelRadius = p.radius;
                wheelReduce = Math.max(1.0, p.element.ratio);
            }
            if (p.role.isTool()) {
                toolCount++;
            }
        }
    }

    /** Состояние установки: обороты, путь, нагрузка, прогресс инструмента. */
    public static final class State {
        public double omega;
        public double travel;
        public double loadFraction;
        public boolean engaged;
        public boolean jammed;
        public long blocksDone;
        public long strikesDone;
        public String note = "привод остановлен";
        /** Прогресс и счётчик проходов для каждой позиции инструмента. */
        public final Map<Long, Double> progress = new LinkedHashMap<>();
        public final Map<Long, Integer> strikes = new LinkedHashMap<>();
        /** Действия, которые движок обязан исполнить в мире за этот тик. */
        public final List<Action> actions = new ArrayList<>();

        public double rpm() {
            return toRpm(omega);
        }

        public static long key(int x, int y, int z) {
            return ((long) (x + 1_000_000) * 2_000_001L + (long) (y + 1_000_000)) * 2_000_001L
                    + (long) (z + 1_000_000);
        }
    }

    /** Просьба к миру. */
    public static final class Action {
        /** "break" — сломать, "fell" — срубить дерево целиком, "move" — сдвинуть каретку. */
        public final String kind;
        public final int x;
        public final int y;
        public final int z;
        public final String note;

        public Action(String kind, int x, int y, int z, String note) {
            this.kind = kind;
            this.x = x;
            this.y = y;
            this.z = z;
            this.note = note == null ? "" : note;
        }

        @Override
        public String toString() {
            return kind + "@" + x + "," + y + "," + z + (note.isEmpty() ? "" : " (" + note + ")");
        }
    }

    /** Результат одного шага: то, что интересно тестам и GUI. */
    public static final class Result {
        public double powerW;
        public double torqueAvail;
        public double torqueLoad;
        public double speedMps;
        /** Тормозной момент генератора, Н·м, и его мощность, Вт — для подсказки. */
        public double brakeNm;
        public double brakeW;
    }

    /**
     * Один шаг длительностью dt секунд: разгон, работа инструментов, ход каретки.
     *
     * @param motor  характеристика привода на его собственном валу
     * @param spec   установка (роли деталей и что перед ними)
     * @param state  состояние (обновляется на месте)
     * @param powerW доступная механическая мощность, Вт
     * @param out    куда выписать цифры для GUI (может быть null)
     */
    public static Result step(Motor motor, Spec spec, State state, double powerW,
                              double dt, Result out) {
        return step(motor, spec, state, powerW, dt, out, 0.0);
    }

    /**
     * Тот же шаг, но с внешним тормозом: генератор под нагрузкой — это нагрузка
     * на вал (τ = k·I), а не «ещё один мотор наоборот».
     */
    public static Result step(Motor motor, Spec spec, State state, double powerW,
                              double dt, Result out, double brakeNm) {
        Result r = out == null ? new Result() : out;
        state.actions.clear();
        double d = Math.max(0.005, Math.min(0.5, dt));
        Drive drive = solve(motor, List.of(new Element(Math.max(0.05, spec.gearProduct),
                spec.efficiency)));
        double w0 = Math.max(0.05, drive.rpmOut * RPM_PER_RAD);
        double torqueStall = Math.max(0.0, drive.torqueOut);
        double j = Math.max(1e-4, spec.inertiaTotal);

        // ---------- нагрузка
        double torqueLoad = FRICTION_FRACTION * torqueStall;   // подшипники, щётки
        double powerLoad = 0.0;
        List<Part> working = new ArrayList<>();
        boolean blocked = false;
        for (Part p : spec.parts) {
            if (!p.role.isTool() || p.facing == null) {
                continue;
            }
            if (!p.facing.workable()) {
                if (p.facing.torqueNm > 0.0) {
                    blocked = true;                       // уперлись во что-то непробиваемое
                    torqueLoad += p.facing.torqueNm;
                }
                continue;
            }
            working.add(p);
            torqueLoad += p.facing.torqueNm;
            powerLoad += p.facing.torqueNm * state.omega;
        }
        if (spec.hasWheels) {
            torqueLoad += spec.mu * spec.massTotal * G * spec.wheelRadius / Math.max(1.0, spec.wheelReduce);
        }
        double brake = Math.max(0.0, brakeNm);
        if (brake > 0.0) {
            // Генератор — нагрузка без «кусаемого» блока: он тормозит всегда,
            // поэтому «не хватает момента» считаем и по нему тоже.
            torqueLoad += brake;
            powerLoad += brake * Math.max(0.0, state.omega);
        }
        r.brakeNm = brake;
        r.brakeW = brake * Math.max(0.0, state.omega);

        // «встанает» только тот, кому есть что пилить: у пустого лезвия момент
        // нехватки не просит, и честный ответ — холостой ход, а не «не берёт»
        boolean stalled = ((!working.isEmpty() || brake > 0.0) && torqueLoad > torqueStall) || blocked;
        double torqueAvail = motor.linear ? torqueStall * (1.0 - state.omega / w0) : torqueStall;
        if (torqueAvail < 0.0) {
            torqueAvail = 0.0;
        }
        state.loadFraction = torqueStall <= 0.0 ? 1.0 : torqueLoad / torqueStall;
        r.torqueAvail = torqueAvail;
        r.torqueLoad = torqueLoad;

        // ---------- разгон/торможение
        double net = stalled ? -torqueLoad : torqueAvail - torqueLoad;
        state.omega = stepOmega(j, state.omega, net, d);
        double pAvail = torqueAvail * state.omega;
        double pMech = Math.max(0.0, powerW);
        r.powerW = Math.min(pMech, Math.max(0.0, pAvail));
        if (pAvail < 0.01 && state.omega < 0.5) {
            r.powerW = 0.0;
        }

        // ---------- инструменты
        double share = working.isEmpty() ? 0.0 : r.powerW / working.size();
        for (Part p : working) {
            long k = State.key(p.x, p.y, p.z);
            if (stalled) {
                state.progress.put(k, 0.0);
                continue;
            }
            double have = Math.max(0.0, share);
            double prog = state.progress.getOrDefault(k, 0.0)
                    + (have > 0.01 ? have * d : 0.0) / Math.max(1.0, p.facing.workJ);
            while (prog >= 1.0) {
                prog -= 1.0;
                state.strikesDone++;
                int n = state.strikes.merge(k, 1, Integer::sum);
                if (n >= p.facing.strikes) {
                    state.strikes.put(k, 0);
                    state.blocksDone++;
                    state.engaged = true;
                    state.actions.add(new Action(
                            p.role == Role.SAW && p.facing.tree ? "fell" : "break",
                            p.tx, p.ty, p.tz, p.facing.name));
                    // удар: маховик отдаёт часть энергии
                    double take = Math.min(flywheelEnergy(j, state.omega) * 0.25,
                            p.facing.totalJ() * 0.5);
                    state.omega = omegaAfterTake(j, state.omega, take);
                }
            }
            state.progress.put(k, prog);
        }

        // ---------- ход каретки
        Traction t = spec.hasWheels
                ? carriage(state.omega, Math.max(0.0, torqueAvail - torqueLoad * 0.6),
                spec.wheelRadius, spec.massTotal, spec.mu, spec.wheelReduce)
                : new Traction(0.0, 0.0, 0.0);
        r.speedMps = t.speedMps;
        if (spec.hasWheels && !blocked) {
            state.travel += t.speedMps * d;
            while (state.travel >= 1.0) {
                state.travel -= 1.0;
                state.actions.add(new Action("move", 0, 0, 0, "каретка прошла метр"));
            }
        }

        // ---------- объяснение для игрока
        state.jammed = stalled && (!working.isEmpty() || blocked);
        if (blocked) {
            state.note = "упор: впереди то, что механизм не берёт — нужен другой инструмент";
            state.engaged = false;
        } else if (stalled) {
            state.note = String.format("не берёт: нужно %.3f Н·м, привод даёт %.3f Н·м"
                    + " — редуктор или маховик", torqueLoad, torqueStall);
            state.engaged = false;
        } else if (!working.isEmpty()) {
            state.note = String.format("%d инстр., %d об/мин, нагрузка %d%%",
                    working.size(), (int) state.rpm(), (int) (state.loadFraction * 100));
            state.engaged = true;
        } else if (t.moves()) {
            state.note = "едет: " + explainCarriage(state.omega, Math.max(0.0, torqueAvail),
                    spec.wheelRadius, spec.massTotal, spec.mu, spec.wheelReduce);
            state.engaged = true;
        } else if (spec.hasWheels) {
            state.note = explainCarriage(state.omega, Math.max(0.0, torqueAvail),
                    spec.wheelRadius, spec.massTotal, spec.mu, spec.wheelReduce);
            state.engaged = false;
        } else {
            state.note = "холостой ход: " + (int) state.rpm() + " об/мин, работать нечем";
            state.engaged = false;
        }
        if (state.actions.size() > MAX_ACTIONS_PER_TICK) {
            state.actions.subList(MAX_ACTIONS_PER_TICK, state.actions.size()).clear();
        }
        return r;
    }

    /** Тот же шаг без отдельного Result (для движка). */
    public static Result step(Motor motor, Spec spec, State state, double powerW, double dt) {
        return step(motor, spec, state, powerW, dt, null);
    }
}
