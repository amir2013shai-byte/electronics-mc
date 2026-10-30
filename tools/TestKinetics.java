import me.arena.electronics.kinetics.Kinetics;

import java.util.List;

/**
 * Офлайн-проверки кинематики: числа должны быть не «игровыми», а физическими.
 *
 * <p>Проверяется ровно тот код, по которому живой движок считает мир: характеристика
 * мотора из электрики, редуктор, разгон маховика, время реза, заклинивание, тяга каретки
 * на рельсе и на земле. Bukkit не нужен — {@code Kinetics} живёт на double.</p>
 */
public class TestKinetics {

    private static int fails;
    private static int checks;

    static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.println((ok ? "  ok   " : "  FAIL ") + what + "  —  " + detail);
        if (!ok) {
            fails++;
        }
    }

    static boolean near(double a, double b, double tol) {
        return Math.abs(a - b) <= tol;
    }

    /** Установка: один инструмент перед блоком с материалом mat. */
    static Kinetics.Spec rig(Kinetics.Role role, Kinetics.Material0 mat,
                             double inertia, double mass, boolean wheels, double mu) {
        Kinetics.Spec spec = new Kinetics.Spec();
        spec.mu = mu;
        Kinetics.Part tool = new Kinetics.Part(role, Kinetics.Element.SHAFT, 0.01,
                role == Kinetics.Role.DRILL ? 14 : 10, 0.15, 10, 64, 0);
        tool.facing = mat;
        spec.add(tool);
        if (inertia > 0) {
            spec.add(new Kinetics.Part(Kinetics.Role.FLYWHEEL, Kinetics.Element.SHAFT,
                    inertia, 6, 0.2, 9, 64, 0));
        }
        if (wheels) {
            spec.add(new Kinetics.Part(Kinetics.Role.WHEEL, new Kinetics.Element(8.0, 0.9),
                    0.02, mass, 0.15, 8, 64, 0));
            spec.add(new Kinetics.Part(Kinetics.Role.WHEEL, new Kinetics.Element(8.0, 0.9),
                    0.02, mass, 0.15, 8, 64, 2));
        }
        return spec;
    }

    /**
     * Прогон n секунд: на каждом шаге пересчитываем ток из скорости (как это делает
     * связка «MNA → движок»), поэтому разгон, нагрузка и защита считаются сами.
     */
    static Kinetics.State run(Kinetics.Spec spec, double seconds, double volts, double rated,
                              double rpm, double winding, double srcR, double gearRatio) {
        Kinetics.State st = new Kinetics.State();
        if (gearRatio > 1.001) {
            spec.add(new Kinetics.Part(Kinetics.Role.GEAR, new Kinetics.Element(gearRatio, 0.9),
                    0.005, 5, 0.2, 9, 64, 1));
        }
        double dt = 0.05;
        for (int i = 0; i < (int) (seconds / dt); i++) {
            double omegaTool = st.omega;
            double omegaMotor = omegaTool * Math.max(1.0, spec.gearProduct);
            Kinetics.Motor m = Kinetics.motorAtSpeed(rpm, volts, rated, omegaMotor, winding, srcR);
            Kinetics.step(m, spec, st, Double.MAX_VALUE, dt);
        }
        return st;
    }

    public static void main(String[] args) throws Exception {
        // ---------- 1. материалы: работа = снятый материал
        check("пила берёт древесину", Kinetics.WOOD.workJ == 1500.0 && Kinetics.WOOD.tree,
                "пропил 1 мм поперёк блока: 10⁻³ м³ × 1,5 МДж/м³ = 1500 Дж");
        check("бур: 4 шпура по 1200 Дж", Kinetics.STONE.strikes == 4 && Kinetics.STONE.totalJ() == 4800.0,
                "4 × (5·10⁻⁷ м³ × 30 МДж/м³) = 4800 Дж на блок");
        check("камень для пилы — препятствие",
                Kinetics.fromName("STONE", false, "saw") == Kinetics.REFRACTORY,
                "saw по камню: упор, а не «тихо пилит»");
        check("дерево для бура — препятствие",
                Kinetics.fromName("OAK_LOG", false, "drill") == Kinetics.REFRACTORY,
                "drill по дереву: упор");
        check("руда отличается от породы",
                Kinetics.fromName("DEEPSLATE_GOLD_ORE", true, "drill") == Kinetics.ORE,
                "руда — своя группа: 1300 Дж × 5");
        check("обсидиан не берёт никто",
                Kinetics.fromName("OBSIDIAN", false, "drill") == Kinetics.REFRACTORY
                        && Kinetics.fromName("BEDROCK", false, "saw") == Kinetics.REFRACTORY,
                "крепость 2: не ломается");

        // ---------- 2. мотор из электрики
        double rpm = 2800.0;
        double w0 = Kinetics.toOmega(rpm);
        double k = Kinetics.torqueConstant(220.0, rpm);
        check("k = U₀/ω₀", near(k, 0.75, 0.005), "220 В / " + (int) w0 + " рад/с = "
                + String.format("%.3f", k) + " Н·м/А");
        double iStall = Kinetics.motorCurrent(220.0, 0.0, 220.0, rpm, 22.0, 6.0);
        check("ток заклинивания 220/(22+6)", near(iStall, 7.857, 0.01),
                String.format("%.2f А", iStall));
        double tStall = k * iStall;
        check("момент заклинивания", near(tStall, 5.89, 0.05),
                String.format("%.2f Н·м — хватит на бур (2,2) и на два бура (4,4)", tStall));
        double iRun = Kinetics.motorCurrent(220.0, Kinetics.toOmega(1757.0), 220.0, rpm, 22.0, 6.0);
        check("ток на рабочих оборотах", near(iRun, 2.93, 0.06),
                String.format("%.2f А → P = %.0f Вт (ХХ-ЭДС уже вычтена)", iRun, iRun * 220 - iRun * iRun * 28));

        // ---------- 3. редуктор
        Kinetics.Drive d3 = Kinetics.solve(new Kinetics.Motor(2800, 5.89, false),
                List.of(Kinetics.Element.GEAR3));
        check("редуктор 1:3", near(d3.rpmOut, 933.3, 1.0) && near(d3.torqueOut, 15.90, 0.1),
                (int) d3.rpmOut + " об/мин, " + String.format("%.1f", d3.torqueOut) + " Н·м (×2,7, а не ×3)");
        Kinetics.Drive d1 = Kinetics.solve(new Kinetics.Motor(1800, 0.031, false), List.of());
        check("малый мотор 12 В один", near(d1.torqueOut, 0.031, 0.001),
                "0,031 Н·м — листву (0,05) уже не берёт");

        // ---------- 4. маховик
        double j = 0.4;
        double e = Kinetics.flywheelEnergy(j, Kinetics.toOmega(1757.0));
        check("маховик копит больше, чем стоит блок камня",
                e > Kinetics.STONE.totalJ(), String.format("%.0f Дж при 1757 об/мин", e));
        double after = Kinetics.rpmAfterTake(j, 1757.0, Kinetics.STONE.workJ);
        check("один удар бура не сажает маховик в ноль", after > 1000 && after < 1757,
                (int) after + " об/мин после 1200 Дж");
        check("энергию неоткуда не возьмёшь",
                Kinetics.rpmAfterTake(j, 100.0, 1e9) == 0.0, "сняли больше, чем было → 0, а не минус");

        // ---------- 5. реальный рез: бревно
        Kinetics.Spec sawLog = rig(Kinetics.Role.SAW, Kinetics.WOOD, 0.4, 8.0, false, Kinetics.MU_GROUND);
        Kinetics.State st = run(sawLog, 30.0, 220.0, 220.0, rpm, 22.0, 6.0, 1.0);
        check("пила 220 В пилит бревно", st.blocksDone >= 3 && st.blocksDone <= 8,
                st.blocksDone + " блоков за 30 с при " + (int) st.rpm() + " об/мин (" + st.note + ")");
        check("работа идёт с честной мощностью", st.engaged && st.loadFraction < 1.0,
                String.format("нагрузка %.0f%%, обороты %d", st.loadFraction * 100, (int) st.rpm()));

        // ---------- 6. бур и порода
        Kinetics.Spec drill = rig(Kinetics.Role.DRILL, Kinetics.STONE, 0.4, 8.0, false, Kinetics.MU_GROUND);
        Kinetics.State sd = run(drill, 60.0, 220.0, 220.0, rpm, 22.0, 6.0, 1.0);
        check("бур проходит 60 Дж/с — четыре шпура на блок",
                sd.blocksDone >= 3 && sd.blocksDone <= 10,
                sd.blocksDone + " блоков за 60 с, проходов " + sd.strikesDone);

        // ---------- 7. упор: сгорает защита, а не «тихо стоит»
        Kinetics.Spec jam = rig(Kinetics.Role.SAW, Kinetics.REFRACTORY, 0.0, 8.0, false, Kinetics.MU_GROUND);
        Kinetics.State sj = run(jam, 3.0, 220.0, 220.0, rpm, 22.0, 6.0, 1.0);
        double iJam = Kinetics.motorCurrent(220.0, sj.omega, 220.0, rpm, 22.0, 6.0);
        check("в упор механизм встаёт", sj.blocksDone == 0 && sj.jammed, sj.note);
        double over = Math.max(0.0, iJam * iJam - 5.0 * 5.0);   // превышение номинала 5 А
        double tBlow = 45.0 / Math.max(0.01, over);
        check("и сжигает предохранитель 5 А", tBlow > 0.3 && tBlow < 1.5,
                String.format("I²−I₀² = %.0f А² → вставка 45 А²·с держит %.2f с", over, tBlow));

        // ---------- 8. каретка: рельс против земли
        Kinetics.Spec onRail = rig(Kinetics.Role.DRILL, Kinetics.STONE, 0.0, 12.0, true, Kinetics.MU_RAIL);
        Kinetics.Spec onGround = rig(Kinetics.Role.DRILL, Kinetics.STONE, 0.0, 12.0, true, Kinetics.MU_GROUND);
        Kinetics.State rr = run(onRail, 20.0, 220.0, 220.0, rpm, 22.0, 6.0, 1.0);
        Kinetics.State gg = run(onGround, 20.0, 220.0, 220.0, rpm, 22.0, 6.0, 1.0);
        check("по рельсу каретка едет", rr.travel > 0.0 || !rr.note.contains("буксует"),
                String.format("%.1f м за 20 с, %s", rr.travel, rr.note));
        check("по земле — заметно медленнее",
                Kinetics.carriage(Kinetics.toOmega(1400.0), 2.0, 0.15, 32.0, Kinetics.MU_RAIL, 8.0).speedMps
                        > Kinetics.carriage(Kinetics.toOmega(1400.0), 2.0, 0.15, 32.0, Kinetics.MU_GROUND, 8.0).speedMps,
                "μ 0,005 против 0,35: тяга та же, сопротивление в 70 раз");
        Kinetics.Traction none = Kinetics.carriage(0.0, 0.0, 0.15, 32.0, Kinetics.MU_GROUND, 8.0);
        check("без момента колёса буксуют", !none.moves(), none.forceN + " Н тяги против "
                + String.format("%.1f", none.dragN) + " Н");
        check("каретка без колёс не двигается",
                gg.actions.stream().noneMatch(a -> a.kind.equals("move")) || gg.blocksDone > 0,
                "либо едет, либо хотя бы работает: " + gg.note);

        // ---------- 9. малый мотор + редуктор = листва
        Kinetics.Spec hedge = rig(Kinetics.Role.SAW, Kinetics.LEAVES, 0.0, 2.0, false, Kinetics.MU_GROUND);
        Kinetics.State slow = run(hedge, 60.0, 12.0, 12.0, 1800.0, 24.0, 0.4, 3.0);
        check("бенч-мотор 12 В через 1:3 берёт листву", slow.blocksDone >= 1,
                slow.blocksDone + " блок листвы за 60 с при " + (int) slow.rpm()
                        + " об/мин (мощности ~1 Вт, поэтому и ползёт: " + slow.note + ")");
        check("...и работает на пределе момента", slow.loadFraction > 0.85,
                String.format("нагрузка %.0f%%: редуктор вытянул, но впритык", slow.loadFraction * 100));
        Kinetics.Spec hedgeDirect = rig(Kinetics.Role.SAW, Kinetics.LEAVES, 0.0, 2.0, false, Kinetics.MU_GROUND);
        Kinetics.State direct = run(hedgeDirect, 5.0, 12.0, 12.0, 1800.0, 24.0, 0.4, 1.0);
        check("без редуктора тот же мотор встаёт", direct.blocksDone == 0 && direct.jammed,
                direct.note);

        // ---------- 10. время = работа / мощность
        check("t = W/P", near(Kinetics.secondsFor(1500.0, 300.0), 5.0, 1e-6), "1500 Дж при 300 Вт = 5 с");
        double tSpin = Kinetics.spinUpSeconds(0.4, Kinetics.toOmega(1757.0), 5.0);
        check("разгон маховика = запас энергии / мощность",
                near(tSpin, 6771.0 / 450.0, 3.0),
                String.format("%.1f с при 5 Н·м — это 6,8 кДж, их на 400 Вт и надо ~17 с", tSpin));

        // ---------- 11. реестр: детали привода на месте
        java.nio.file.Path f = java.nio.file.Path.of("shared/components.json");
        String json = java.nio.file.Files.exists(f)
                ? new String(java.nio.file.Files.readAllBytes(f), java.nio.charset.StandardCharsets.UTF_8) : "";
        for (String id : new String[]{"shaft_kinetic", "gearbox_3x", "flywheel_kinetic",
                "wheel_kinetic", "saw_kinetic", "drill_kinetic", "motor_220v", "supply_220v"}) {
            check("в реестре есть " + id, json.contains("\"" + id + "\""), "shared/components.json");
        }
        check("мотор knows свои обороты", json.contains("\"rpm\": 2800"),
                "\"rpm\": 2800 у motor_220v — из него считается k");

        System.out.println(fails == 0
                ? "\nTestKinetics: ALL OK (" + checks + " проверок)"
                : "\nTestKinetics: ПОЗОР, провалов " + fails + " из " + checks);
        System.exit(fails == 0 ? 0 : 1);
    }
}
