import me.arena.electronics.circuit.Battery;
import me.arena.electronics.circuit.CompStates;

/**
 * Проверка модели свинцового аккумулятора: кулоны, Пейкерт, сульфатация, тепло.
 *
 * <p>Bukkit не нужен — тот же класс, что видит сервер. Проверяется ровно то, за
 * счёт чего аккумулятор перестаёт быть «батарейкой с украшением». Знаки — как в
 * решателе: «+» банки — вывод 0, положительный ток = банк отдаёт (разряд): ёмкость должна
 * кончаться, высокий ток должен съедать её быстрее, обратно загнанный ток —
 * убивать пластины, а нагретый корпус — остывать, а не стоять.</p>
 */
public final class TestBattery {

    private static int fails = 0;
    private static int checks = 0;

    private static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.println((ok ? "  OK    " : "  ПОЗОР ") + what + " — " + detail);
        if (!ok) {
            fails++;
        }
    }

    private static boolean near(double got, double want, double tol) {
        return Math.abs(got - want) <= tol;
    }

    /** 12.65 В, 20 мОм, 7 А·ч, n = 1.25, КПД заряда 0.95, саморазряд 0.002/ч, сульфат 0.02/ч. */
    private static Battery.Params params() {
        return new Battery.Params(12.65, 0.020, 7.0, 1.25, 0.95, 0.002, 0.02);
    }

    private static double[] full() {
        double[] aux = new double[4];
        Battery.reset(params(), aux);
        return aux;
    }

    public static void main(String[] args) {
        Battery.Params p = params();

        check("пустой aux = новый полный банк",
                Battery.soc(new double[4]) == 1.0 && Battery.degrade(new double[4]) == 0.0,
                "инициализация детали не нужна, реестр не пухнет");

        // --- 1. ЭДС: весь диапазон 0.8 В, и это не «датчик заряда» -------------
        check("ЭДС полной банки", near(p.emfAt(1.0), 12.65, 1e-9), "12.65 В");
        check("разброс ЭДС = 0.8 В", near(p.emfAt(1.0) - p.emfAt(0.0), Battery.OCV_SPAN, 1e-9),
                String.format("%.2f В на 0..100 %% SOC", p.emfAt(1.0) - p.emfAt(0.0)));
        check("по вольтметру SOC не видно",
                p.emfAt(0.2) > 11.9 && p.emfAt(0.8) < 12.6,
                "20 % -> " + String.format("%.2f", p.emfAt(0.2)) + " В, 80 % -> "
                        + String.format("%.2f", p.emfAt(0.8)) + " В (разница 6 %)");

        // --- 2. Пейкерт: высокий ток съедает ёмкость ---------------------------
        check("при 20-часовом токе ёмкость номинальная",
                near(Battery.availableAh(p, full(), 7.0 / 20.0), 7.0, 1e-9), "0.35 А -> 7.00 А·ч");
        double fast = Battery.availableAh(p, full(), 35.0);
        check("при 5 С ёмкость падает втрое", fast > 1.8 && fast < 2.8,
                "35 А -> " + String.format("%.2f", fast) + " А·ч (номинал 7)");
        double tiny = Battery.availableAh(p, full(), 0.05);
        check("очень малый ток = номинал (потолок Пейкерта)", near(tiny, 7.0, 1e-9),
                "0.05 А -> " + String.format("%.2f", tiny) + " А·ч");
        double slow = Battery.availableAh(p, full(), 0.7);
        check("0.1C чуть хуже номинала", slow > 5.5 && slow < 7.0,
                "0.7 А -> " + String.format("%.2f", slow) + " А·ч ("
                        + String.format("%.0f", slow / 7.0 * 100) + " % номинала)");
        double[] old = full();
        old[Battery.DEGRADE] = 0.5;
        check("сульфатированная банка теряет и номинал",
                near(Battery.capacityLeft(p, old), 3.5, 1e-9)
                        && near(Battery.availableAh(p, old, 3.5 / 20.0), 3.5, 1e-9),
                "50 % деградации -> " + String.format("%.2f", Battery.capacityLeft(p, old))
                        + " А·ч из 7, её C20 = 0.175 А");

        // --- 3. Кулоны: 1 А за 1 час = 1/7 заряда ------------------------------
        double[] a = full();
        Battery.step(p, a, p.emfAt(1.0), 7.0 / 20.0, 20 * 3600.0);   // 20-часовой ток, 20 ч
        check("20 часов током C20 сажают банку в ноль", Battery.soc(a) < 0.001,
                String.format("%.1f %% после 20 ч при 0.35 А", Battery.soc(a) * 100));
        double[] a1 = full();
        Battery.step(p, a1, p.emfAt(1.0), 1.0, 3600.0);         // отдаёт 1 А час
        double drained = 1.0 - Battery.soc(a1);
        check("1 А · 1 ч = чуть больше 1/7 заряда (Пейкерт)",
                drained > 1.0 / 7.0 && drained < 0.20,
                String.format("ушло %.1f %% вместо «чистых» 14.3 %%", drained * 100));
        check("счётчик отданных ампер-часов", near(a1[Battery.AMPERE_HOURS], 1.0, 1e-6)
                        && a[Battery.AMPERE_HOURS] > 6.9,
                "за час 1 А: +" + String.format("%.3f", a1[Battery.AMPERE_HOURS])
                        + " А·ч, за 20 ч: +" + String.format("%.1f", a[Battery.AMPERE_HOURS]) + " А·ч");

        // --- 4. КПД заряда: обратно возвращается меньше ------------------------
        double[] b = full();
        Battery.step(p, b, p.emfAt(1.0), 1.0, 3600.0);          // слили 1 А·ч
        double socAfter = Battery.soc(b);
        Battery.step(p, b, 13.8, -1.0, 3600.0);                  // загнали 1 А·ч
        check("час заряда 1 А возвращает не всё",
                Battery.soc(b) - socAfter < 1.0 / 7.0 && Battery.soc(b) - socAfter > 0.9 / 7.0,
                String.format("%.1f %% против 14.3 %%", (Battery.soc(b) - socAfter) * 100));
        Battery.step(p, b, 13.8, -1.0, 3600.0 * 20.0);           // и ещё двадцать часов
        check("заряд не переливается через край", Battery.soc(b) <= 1.0 + 1e-12,
                String.format("%.1f %%", Battery.soc(b) * 100));

        // --- 5. Глубокий разряд при высоком токе кончается быстрее -------------
        double[] c1 = full();
        double[] c2 = full();
        int steps = 15 * 60 * 60;                                 // 20 часов игрового времени
        for (int i = 0; i < steps; i++) {
            Battery.step(p, c1, p.emfAt(Battery.soc(c1)), 0.35, 1.0);   // 0.35 А = 20-часовой
            Battery.step(p, c2, p.emfAt(Battery.soc(c2)), 35.0, 1.0);   // 35 А = 5 С
        }
        check("на 5 С банка села, на C20 ещё отдаёт",
                Battery.soc(c2) < 0.02 && Battery.soc(c1) > 0.20,
                "0.35 А -> " + String.format("%.0f %%", Battery.soc(c1) * 100)
                        + ", 35 А -> " + String.format("%.0f %%", Battery.soc(c2) * 100));

        // --- 6. Севшая банка растит внутреннее сопротивление -------------------
        check("полная банка: r_int как в реестре",
                near(Battery.resistance(p, full()), 0.020, 1e-9), "0.020 Ом");
        double[] dead = full();
        Battery.setSoc(dead, 0.0);
        check("пустая банка: r_int в 40 раз больше",
                near(Battery.resistance(p, dead), 0.020 * Battery.DEAD_RESISTANCE_FACTOR, 1e-9),
                String.format("%.3f Ом", Battery.resistance(p, dead)));
        double[] half = full();
        Battery.setSoc(half, Battery.V_DEAD_SOC);
        check("на границе «села» сопротивления нет",
                near(Battery.resistance(p, half), 0.020, 1e-9), "5 % SOC -> 0.020 Ом");

        // --- 7. Сульфатация и саморазряд --------------------------------------
        double[] s = full();
        Battery.setSoc(s, 0.0);
        double d0 = s[Battery.DEGRADE];
        for (int i = 0; i < 24; i++) {
            Battery.step(p, s, p.emfAt(0.0), 0.0, 3600.0);       // сутки в ноль, разомкнуто
        }
        check("сутки разряженным = потеря ёмкости", s[Battery.DEGRADE] > d0 + 0.1,
                String.format("+%.0f %% за сутки", (s[Battery.DEGRADE] - d0) * 100));
        double[] f = full();
        double socBefore = Battery.soc(f);
        for (int i = 0; i < 24; i++) {
            Battery.step(p, f, p.emfAt(1.0), 0.0, 3600.0);       // сутки полным, разомкнутым
        }
        check("полный саморазряд мал, но есть",
                Battery.soc(f) < socBefore - 0.04 && Battery.soc(f) > socBefore - 0.06,
                String.format("%.1f %% за сутки", (socBefore - Battery.soc(f)) * 100));
        check("полный не сульфатируется", near(f[Battery.DEGRADE], 0.0, 1e-12), "0 %");

        // --- 8. Заряд: «обратно» = ток в банк, «горит» = перезаряд --------------
        double[] low = full();
        Battery.setSoc(low, 0.4);
        check("умеренный заряд = состояние «обратно» (ток в банк)",
                Battery.stateOf(p, low, 13.0, -6.0) == CompStates.REVERSE,
                "13.0 В, 6 А в банк: меньше 1C (7 А) и ниже 14.4 В — банк не кипит");
        check("перезаряд = «горит»", Battery.stateOf(p, low, 15.0, -20.0) == CompStates.ON,
                "15 В и 20 А: выше и по напряжению, и по току");
        check("по току 20 А, но при 13 В — тоже кипение",
                Battery.stateOf(p, low, 13.0, -20.0) == CompStates.ON, "предел 1C = 7 А");
        double[] ok = full();
        double okD0 = ok[Battery.DEGRADE];
        for (int i = 0; i < 60; i++) {
            Battery.step(p, ok, 13.0, -6.0, 60.0);                 // час нормального заряда
        }
        check("нормальный заряд не убивает банк", ok[Battery.DEGRADE] == okD0
                        && Battery.faultOf(p, ok, 13.0, -6.0).isEmpty()
                        && Battery.soc(ok) >= 0.999,
                "емкость цела, отказа нет, полный банк просто стоит на потолке");
        double[] boil = full();
        Battery.setSoc(boil, 0.4);
        double boilD0 = boil[Battery.DEGRADE];
        for (int i = 0; i < 60; i++) {
            Battery.step(p, boil, 15.0, -20.0, 60.0);              // час «кипящего» заряда
        }
        check("кипящий заряд теряет ёмкость", boil[Battery.DEGRADE] > boilD0 + 0.01
                        && Battery.faultOf(p, boil, 15.0, -20.0).contains("выкипание"),
                String.format("потеря %.1f %% за час при 15 В / 20 А",
                        (boil[Battery.DEGRADE] - boilD0) * 100));
        check("заряд током 12 А при 13 В — отказ по току",
                Battery.faultOf(p, boil, 13.0, -12.0).contains("кипит"),
                Battery.faultOf(p, boil, 13.0, -12.0));

        // --- 9. Тепло и выкипание ---------------------------------------------
        double[] t = full();
        for (int i = 0; i < 600; i++) {
            Battery.step(p, t, p.emfAt(Battery.soc(t)), 35.0, 1.0);   // минута коротыша
        }
        double hot = t[Battery.TEMP];
        check("короткое замыкание греет корпус", hot > 3.0,
                String.format("+%.1f °C за минуту на 35 А (36 Вт)", hot));
        for (int i = 0; i < 3000; i++) {
            Battery.step(p, t, p.emfAt(Battery.soc(t)), 35.0, 1.0);   // ещё полчаса
        }
        check("нагрев идёт к равновесию P/k, а не в бесконечность",
                t[Battery.TEMP] > hot && t[Battery.TEMP] < 36.6 / Battery.COOL_W_PER_C,
                String.format("+%.1f °C после 31 мин при потолке %.1f", t[Battery.TEMP],
                        36.6 / Battery.COOL_W_PER_C));
        for (int i = 0; i < 6000; i++) {
            Battery.step(p, t, -p.emfAt(Battery.soc(t)), 0.0, 1.0);     // сто минут покоя
        }
        check("корпус остывает к среде", t[Battery.TEMP] < 1.0,
                String.format("+%.2f °C", t[Battery.TEMP]));
        double[] g = full();
        double gd0 = g[Battery.DEGRADE];
        for (int i = 0; i < 60; i++) {
            Battery.step(p, g, 15.0, -2.0, 60.0);                  // час заряда при 15 В
        }
        check("заряд выше 14.4 В = выкипание", g[Battery.DEGRADE] > gd0 + 0.005
                        && Battery.faultOf(p, g, 15.0, -2.0).contains("выкипание"),
                String.format("потеря %.1f %% ёмкости", (g[Battery.DEGRADE] - gd0) * 100));

        // --- 10. Состояния и подпись ------------------------------------------
        check("покой = выключен", Battery.stateOf(p, full(), 12.65, 0.0) == CompStates.CLOSED,
                "полная в холостую = «банка полна»");
        check("разряд = тускло", Battery.stateOf(p, low, 12.0, 2.0) == CompStates.DIM,
                "2 А наружу");
        check("заряд = «обратно», а не «горит»",
                Battery.stateOf(p, low, 13.0, -2.0) == CompStates.REVERSE, "2 А в банку — штатно");
        double[] wreck = full();
        wreck[Battery.DEGRADE] = 0.6;
        check("сульфатированная = сгорела",
                Battery.stateOf(p, wreck, 12.0, 0.0) == CompStates.BURNT
                        && Battery.faultOf(p, wreck, 12.0, 0.0).contains("сульфатация"),
                Battery.faultOf(p, wreck, 12.0, 0.0));
        String label = Battery.label(low, 12.42, 2.1);          // +2.1 А = банк отдаёт
        check("подпись читается", label.contains("12.42 В") && label.contains("40 %")
                        && label.contains("разряд") && label.contains("2.1 А"),
                label);

        // --- 11. Устойчивость: шаги не зависят от дробления и не врут --------
        double[] x1 = full();
        double[] x2 = full();
        for (int i = 0; i < 100; i++) {
            Battery.step(p, x1, p.emfAt(1.0), 2.0, 0.05);        // 5 с по шагам решателя
        }
        Battery.step(p, x2, p.emfAt(1.0), 2.0, 5.0);             // те же 5 с одним шагом
        check("мелкий шаг и крупный дают одно и то же",
                Math.abs(Battery.soc(x1) - Battery.soc(x2)) < 5e-4,
                String.format("%.5f против %.5f", Battery.soc(x1), Battery.soc(x2)));
        double[] wild = full();
        for (int i = 0; i < 500; i++) {
            Battery.step(p, wild, -999.0, +1e6, 0.05);            // мусор из разошедшегося решателя
        }
        check("soc не выпрыгивает за 0..1", Battery.soc(wild) >= 0.0 && Battery.soc(wild) <= 1.0,
                String.format("%.1f %%", Battery.soc(wild) * 100));
        check("dt <= 0 ничего не делает",
                near(Battery.soc(x2), Battery.soc(x2), 0.0) && Battery.step(p, x2, 12.0, 1.0, 0.0) == 0.0,
                "return 0, aux целые");

        System.out.println(fails == 0
                ? "TestBattery: ALL OK (" + checks + " проверок)"
                : "TestBattery: FAIL (" + fails + " из " + checks + ")");
        if (fails != 0) {
            System.exit(1);
        }
    }
}
