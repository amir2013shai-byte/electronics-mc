import me.arena.electronics.circuit.CompStates;
import me.arena.electronics.circuit.Dynamo;

/**
 * Проверка модели машинного генератора: ЭДС из оборотов, момент из тока,
 * тепло из потерь и ни одного ватта из воздуха.
 *
 * <p>Главная проверка — энергетическая тождественность: то, что вал потерял
 * (τ·ω), обязано ровно равняться тому, что уехало в цепь (U·I), плюс то, что
 * сгорело на обмотке и щётках. Если кто-то «подправит» одну из трёх формул, не
 * тронув остальные, генератор начнёт печатать энергию из ничего — и тест это
 * поймает на первом же шаге.</p>
 */
public final class TestDynamo {

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

    /** 12 В на 1500 об/мин, обмотка 0.8 Ом, долгий ток 5 А. */
    private static Dynamo.Params p() {
        return new Dynamo.Params(12.0, 1500.0, 0.8, 5.0);
    }

    public static void main(String[] args) {
        Dynamo.Params p = p();

        // --- ЭДС и ток ------------------------------------------------------
        check("ЭДС линейна по оборотам", near(Dynamo.emf(p, 750.0), p.ratedVoltage / 2, 1e-9),
                String.format(java.util.Locale.ROOT, "750 об/мин → %.2f В", Dynamo.emf(p, 750.0)));
        check("на паспортных — паспорт", near(Dynamo.emf(p, 1500.0), 12.0, 1e-9), "12.00 В");
        check("в покое ЭДС нет", Dynamo.emf(p, 0.0) == 0.0, "не крутят — не генерирует");
        check("разнос ограничен 1.5×", near(Dynamo.emf(p, 4500.0), 18.0, 1e-9),
                "3× паспортных → 18 В, а не 36: щётки уже летят");
        check("холостые клеммы: тока нет",
                Math.abs(Dynamo.current(p, 1500.0, Dynamo.emf(p, 1500.0) - Dynamo.BRUSH_V)) < 1e-9,
                String.format(java.util.Locale.ROOT, "U = E − щётки = %.1f В → 0 А",
                        Dynamo.emf(p, 1500.0) - Dynamo.BRUSH_V));
        double short1500 = Dynamo.current(p, 1500.0, 0.0);
        check("короткое замыкание = (E − щётки)/r", near(short1500, (12.0 - 0.6) / 0.8, 1e-9),
                String.format(java.util.Locale.ROOT, "%.2f А — вот почему без предохранителя нельзя", short1500));
        check("машину можно гнать током назад", Dynamo.current(p, 1500.0, 14.0) < 0.0,
                String.format(java.util.Locale.ROOT, "U=14 В > E → %.2f А: это уже мотор",
                        Dynamo.current(p, 1500.0, 14.0)));
        check("ниже щёточного порока наружу ничего", Dynamo.emf(p, 60.0) < Dynamo.BRUSH_V + 0.2,
                String.format(java.util.Locale.ROOT, "60 об/мин → ЭДС %.2f В, щётки берут %.1f В",
                        Dynamo.emf(p, 60.0), Dynamo.BRUSH_V));

        // --- момент и энергия ---------------------------------------------
        double k = p.kVsPerRad();
        check("тормозной момент = k·I", near(Dynamo.torque(p, 2.0), k * 2.0, 1e-9),
                String.format(java.util.Locale.ROOT, "k = %.4f В·с/рад → 2 А дают %.2f Н·м", k, Dynamo.torque(p, 2.0)));
        double rpm = 1500.0, u = 10.0, i = Dynamo.current(p, rpm, u);
        double shaft = Dynamo.shaftPowerW(p, rpm, i), elec = u * i, loss = Dynamo.lossesW(p, i);
        check("вал потерял ровно то, что получили цепь и нагрев",
                near(shaft, elec + loss, 1e-9),
                String.format(java.util.Locale.ROOT, "%.3f Вт = %.3f Вт в цепь + %.3f Вт в тепло",
                        shaft, elec, loss));
        check("момент не зависит от знака тока", near(Dynamo.torque(p, -3.0), Dynamo.torque(p, 3.0), 1e-12),
                "тормозит в обе стороны — как и настоящий");

        // --- тепло ---------------------------------------------------------
        double[] a = new double[4];
        for (int n = 0; n < 20000; n++) {
            Dynamo.step(p, a, rpm, i, 3600.0);
        }
        double equilibrium = loss / Dynamo.COOL_W_PER_C;
        check("нагрев идёт к равновесию P/k, а не в бесконечность",
                near(Dynamo.temp(a), equilibrium, 0.02 * equilibrium),
                String.format(java.util.Locale.ROOT, "+%.1f °C при потолке %.1f °C",
                        Dynamo.temp(a), equilibrium));
        double[] hot = new double[4];
        hot[Dynamo.TEMP] = 100.0;
        for (int n = 0; n < 20000; n++) {
            Dynamo.step(p, hot, 0.0, 0.0, 3600.0);
        }
        check("в покое остывает к среде", Dynamo.temp(hot) < 0.01,
                String.format(java.util.Locale.ROOT, "остыл до +%.3f °C", Dynamo.temp(hot)));
        double[] dead = new double[4];
        for (int n = 0; n < 400; n++) {
            Dynamo.step(p, dead, rpm, short1500, 1800.0);
        }
        check("коротыш на полчаса жжёт изоляцию", Dynamo.damage(dead) > 0.0,
                String.format(java.util.Locale.ROOT, "+%.0f °C, повреждение %.0f %%",
                        Dynamo.temp(dead), 100 * Dynamo.damage(dead)));
        double[] burnt = dead.clone();
        for (int n = 0; n < 4000; n++) {
            Dynamo.step(p, burnt, 0.0, 0.0, 1800.0);
        }
        check("сгоревшая обмотка не восстанавливается, остыв",
                Dynamo.temp(burnt) < 1.0 && Dynamo.damage(burnt) >= Dynamo.BURNT_AT,
                String.format(java.util.Locale.ROOT, "остыл до +%.1f °C, а повреждение осталось %.0f %%",
                        Dynamo.temp(burnt), 100 * Dynamo.damage(burnt)));

        // --- состояния -----------------------------------------------------
        check("отдаёт ток — «горит»",
                Dynamo.stateOf(p, new double[4], rpm, 1.75) == CompStates.ON, "1.75 А наружу");
        check("крутится без тока — «тускло»",
                Dynamo.stateOf(p, new double[4], rpm, 0.0) == CompStates.DIM,
                "клеммы висят в воздухе: ЭДС есть, мощности нет");
        check("ток в машину — «обратно»",
                Dynamo.stateOf(p, new double[4], rpm, -0.5) == CompStates.REVERSE, "электротормоз");
        check("не крутят и не текут — «выключен»",
                Dynamo.stateOf(p, new double[4], 0.0, 0.0) == CompStates.OFF, "покой");
        double[] dmg = new double[4];
        dmg[Dynamo.DAMAGE] = 0.6;
        check("повреждение 60 % — «сгорел»",
                Dynamo.stateOf(p, dmg, rpm, 1.0) == CompStates.BURNT, "aux[3] = 0.6");

        // --- отказы и подпись ---------------------------------------------

        check("сгоревшая машина говорит, что сгорела",
                Dynamo.faultOf(p, dmg, rpm, 1.0).contains("сгорела"), Dynamo.faultOf(p, dmg, rpm, 1.0));
        check("перегруз по току назван перегрузом",
                Dynamo.faultOf(p, new double[4], rpm, 6.0).contains("перегруз"),
                Dynamo.faultOf(p, new double[4], rpm, 6.0));
        check("электротормоз объяснён",
                Dynamo.faultOf(p, new double[4], 0.0, -1.0).contains("мотор"),
                Dynamo.faultOf(p, new double[4], 0.0, -1.0));
        check("мало оборотов — отдельная подсказка про щётки",
                Dynamo.faultOf(p, new double[4], 100.0, 0.0).contains("мало оборотов"),
                Dynamo.faultOf(p, new double[4], 100.0, 0.0));
        check("в штатном режиме жаловаться не на что",
                Dynamo.faultOf(p, new double[4], rpm, 1.75).isEmpty(), "12 В, 1.75 А, холодная");
        String lbl = Dynamo.label(p, new double[4], rpm, 1.75);
        check("подпись читается", lbl.contains("В ·") && lbl.contains("об/мин") && lbl.contains("Вт"), lbl);

        // --- счётчики ------------------------------------------------------
        double[] cnt = new double[4];
        Dynamo.step(p, cnt, rpm, 2.0, 3600.0);
        check("час током 2 А = 2 А·ч", near(Dynamo.ahOut(cnt), 2.0, 1e-9),
                String.format(java.util.Locale.ROOT, "%.2f А·ч", Dynamo.ahOut(cnt)));
        check("Вт·ч считаются с учётом щёток",
                near(Dynamo.whOut(cnt), 2.0 * (12.0 - Dynamo.BRUSH_V) * 0.98, 1e-9),
                String.format(java.util.Locale.ROOT, "%.2f Вт·ч (не 24: половина ватта ушла на нагрев)",
                        Dynamo.whOut(cnt)));
        double[] back = new double[4];
        Dynamo.step(p, back, rpm, -2.0, 3600.0);
        check("принятый ток пишется минусом", near(Dynamo.ahOut(back), -2.0, 1e-9),
                String.format(java.util.Locale.ROOT, "%.2f А·ч", Dynamo.ahOut(back)));
        check("принятую энергию счётчик не отменяет", Dynamo.whOut(back) == 0.0,
                "Вт·ч — про отданное в цепь, не про «сальдо»");
        check("нулевой шаг ничего не меняет",
                Dynamo.step(p, cnt, rpm, 2.0, 0.0) == 0.0 && near(Dynamo.ahOut(cnt), 2.0, 1e-12),
                "dt ≤ 0 → без последствий");
        check("вся память влезает в aux детали", Dynamo.N_AUX == 4
                        && Dynamo.TEMP == 0 && Dynamo.WATT_HOURS < 4 && Dynamo.DAMAGE == 3,
                "aux[0..3] = нагрев, А·ч, Вт·ч, повреждение — как у аккумулятора");

        System.out.println(fails == 0
                ? "TestDynamo: ALL OK (" + checks + " проверок)"
                : "TestDynamo: ПРОВАЛ (" + fails + " из " + checks + ")");
        if (fails != 0) {
            System.exit(1);
        }
    }
}
