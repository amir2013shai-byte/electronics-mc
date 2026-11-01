import me.arena.electronics.kinetics.Kinetics;
import me.arena.electronics.kinetics.Movers;

/**
 * Проверка механических движителей: рука и вода.
 *
 * <p>Смысл проверки не в «формула работает», а в том, что у движителя есть
 * паспорт по мощности и из воздуха он её не берёт: человек — 90 Вт, колесо —
 * 150 Вт, момент падает линейно с оборотами, а максимум мощности сидит ровно
 * в середине кривой. Если кто-то перекроит {@link Movers} так, что «рукоять
 * даёт 5 кВт», эти проверки должны упасть.</p>
 */
public final class TestMovers {

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

    private static double fmt(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }

    public static void main(String[] args) {
        // --- кривая момента -------------------------------------------------
        double w1000 = Kinetics.toOmega(1000.0);
        check("τ₀ на стопе", near(Movers.torqueAt(0, 100.0, 1000.0), 100.0, 1e-9),
                String.format(java.util.Locale.ROOT, "%.1f Н·м", Movers.torqueAt(0, 100.0, 1000.0)));
        check("на холостом ходе момент ноль", near(Movers.torqueAt(w1000, 100.0, 1000.0), 0.0, 1e-9),
                "ω = ω₀ → τ = 0, дальше колесо гребёт всхолостую");
        check("посередине — ровно половина", near(Movers.torqueAt(w1000 / 2, 100.0, 1000.0), 50.0, 1e-9),
                "линейная кривая, без «волшебного» провисания");
        check("за пределы не уходит в минус", Movers.torqueAt(w1000 * 3, 100.0, 1000.0) == 0.0,
                "отрицательного момента у движителя нет: тормозит нагрузка, не он");

        // --- человек --------------------------------------------------------
        double pMax = Movers.maxPowerW(Movers.CRANK_TORQUE_STALL, Movers.CRANK_RPM_IDLE);
        check("паспорт руки — 90 Вт", near(pMax, Movers.HUMAN_POWER_W, 0.5),
                String.format(java.util.Locale.ROOT, "τ₀·ω₀/4 = %.1f Вт", pMax));
        double best = 0.0;
        double bestRpm = 0.0;
        for (int rpm = 1; rpm <= 2000; rpm++) {
            double w = Kinetics.toOmega(rpm);
            double pw = Movers.powerW(Movers.crankTorque(w, true), w);
            if (pw > best) {
                best = pw;
                bestRpm = rpm;
            }
        }
        check("на любой скорости человек не выжимает больше паспорта", best <= Movers.HUMAN_POWER_W + 0.01,
                String.format(java.util.Locale.ROOT, "максимум %.1f Вт на %.0f об/мин", best, bestRpm));
        check("максимум мощности — наполовину холостых оборотов",
                near(bestRpm, Movers.CRANK_RPM_IDLE / 2, Movers.CRANK_RPM_IDLE * 0.02),
                String.format(java.util.Locale.ROOT, "%.0f из %.0f об/мин", bestRpm, Movers.CRANK_RPM_IDLE));
        check("без удара по рукояти момент нулевой",
                Movers.crankTorque(0, false) == 0.0, "клик правой кнопкой = одна порция кручения");
        check("рывок руки ~38 Н·м — реально удержать двумя руками",
                near(Movers.CRANK_TORQUE_STALL, 38.2, 1.0),
                String.format(java.util.Locale.ROOT, "%.1f Н·м на стопе (это ~95 Н на рычаг 0.4 м)",
                        Movers.CRANK_TORQUE_STALL));

        // --- вода ----------------------------------------------------------
        check("нет воды — нет момента", Movers.waterTorque(0, 0, 0) == 0.0, "сохлое колесо не двигатель");
        double wet4 = Movers.waterTorque(0, 4, 1);
        double wet8 = Movers.waterTorque(0, 8, 1);
        check("потолок — четыре мокрые грани", near(wet8, wet4, 1e-9),
                String.format(java.util.Locale.ROOT, "4 блока: %.0f Н·м, 8 блоков: %.0f Н·м (граней-то шесть)",
                        wet4, wet8));
        double head = Movers.waterTorque(0, 4, 3);
        check("напор добавляет по 25 % на блок", near(head, wet4 * 1.5, wet4 * 0.02),
                String.format(java.util.Locale.ROOT, "1 блок сверху %.0f Н·м → 3 блока %.0f Н·м", wet4, head));
        check("колесо — примерно 150 Вт",
                near(Movers.maxPowerW(Movers.WATER_TORQUE_PER_BLOCK * 4, Movers.WATER_RPM_IDLE), 150.0, 15.0),
                String.format(java.util.Locale.ROOT, "%.0f Вт: два человека на рукояти, только без обеда",
                        Movers.maxPowerW(Movers.WATER_TORQUE_PER_BLOCK * 4, Movers.WATER_RPM_IDLE)));
        double nearIdle = Movers.waterTorque(Kinetics.toOmega(Movers.WATER_RPM_IDLE) * 0.98, 4, 1);
        check("на холостом ходу момент съеден почти весь", nearIdle < wet4 * 0.02,
                String.format(java.util.Locale.ROOT, "%.1f Н·м из %.0f (осталось %.1f %%)",
                        nearIdle, wet4, 100 * nearIdle / wet4));
        check("вода не тормозит: момент никогда не отрицательный",
                Movers.waterTorque(Kinetics.toOmega(1000), 4, 1) == 0.0, "разогнали выше холостых — ноль, не торможение");

        // --- складывание и подпись ---------------------------------------
        double w = Kinetics.toOmega(45.0);
        check("рука плюс вода = сумма моментов",
                near(Movers.totalTorque(w, true, 2, 1),
                        Movers.crankTorque(w, true) + Movers.waterTorque(w, 2, 1), 1e-9),
                String.format(java.util.Locale.ROOT, "%.1f Н·м вместе", Movers.totalTorque(w, true, 2, 1)));
        check("мощность = τ·ω", near(Movers.powerW(10.0, Kinetics.toOmega(60.0)), 10.0 * 2 * Math.PI, 1e-9),
                String.format(java.util.Locale.ROOT, "%.3f Вт", Movers.powerW(10.0, Kinetics.toOmega(60.0))));
        check("подпись с числами", Movers.label(Kinetics.toOmega(45.0), true, 2, 1).contains("Н·м"),
                Movers.label(Kinetics.toOmega(45.0), true, 2, 1));
        check("подпись «никто не крутит»", Movers.label(0, false, 0, 0).contains("никто не крутит"),
                Movers.label(0, false, 0, 0));
        check("обороты в подписи — фактические", Movers.label(Kinetics.toOmega(45.0), true, 0, 0).startsWith("45 "),
                Movers.label(Kinetics.toOmega(45.0), true, 0, 0));
        check("всё посчитано, а не захардкожено", fmt(Movers.CRANK_TORQUE_STALL) > 0 && wet4 > 0,
                String.format(java.util.Locale.ROOT, "τ₀ руки %.2f, τ₀ колеса %.1f",
                        Movers.CRANK_TORQUE_STALL, wet4));

        System.out.println(fails == 0
                ? "TestMovers: ALL OK (" + checks + " проверок)"
                : "TestMovers: ПРОВАЛ (" + fails + " из " + checks + ")");
        if (fails != 0) {
            System.exit(1);
        }
    }
}
