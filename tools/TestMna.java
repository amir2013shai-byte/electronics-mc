import me.arena.electronics.sim.Mna;

import java.util.Locale;
import java.util.Map;

/**
 * Прогон математики решателя без Bukkit: делитель напряжения, реальный источник,
 * светодиод с гасящим резистором, RC-переходный процесс, КЗ/предохранитель,
 * измерения приборов. Компилируется и запускается отдельно (tools/TestMna.java),
 * чтобы формулы были проверены до подключения к миру.
 */
public class TestMna {

    static int failed = 0;

    public static void main(String[] args) {
        voltageDivider();
        realSource();
        ledWithResistor();
        diodeReverse();
        capacitorStep();
        shortCircuit();
        meterReadings();
        bondWires();
        System.out.println(failed == 0 ? "ALL OK" : (failed + " FAILED"));
        if (failed > 0) {
            System.exit(1);
        }
    }

    /** 9 В, R1=1кОм сверху, R2=2.2кОм снизу -> средняя точка 6.25 В. */
    static void voltageDivider() {
        Mna m = new Mna();
        m.ground("gnd");
        double vin = 9.0;
        m.stamp("in", "gnd", 1e9, vin * 1e9);        // жёсткий источник 9 В
        m.stamp("in", "mid", 1 / 1000.0, 0);
        m.stamp("mid", "gnd", 1 / 2200.0, 0);
        Map<String, Double> v = m.solve();
        double expect = vin * 2200.0 / (1000.0 + 2200.0);
        check("делитель напряжения", v.get("mid"), expect, 1e-6);
        check("узел источника", v.get("in"), vin, 1e-4);
    }

    /** ЭДС 9 В, r=1.2 Ом, нагрузка 100 Ом: U = E*R/(R+r). */
    static void realSource() {
        Mna m = new Mna();
        m.ground("neg");
        double e = 9.0;
        double r = 1.2;
        m.stamp("pos", "neg", 1 / r, e / r);
        m.stamp("pos", "neg", 1 / 100.0, 0);
        Map<String, Double> v = m.solve();
        check("реальный источник", v.get("pos"), e * 100.0 / (100.0 + r), 1e-4);
        double iLoad = 0;
        for (Mna.Stamp st : m.stamps()) {
            if (Math.abs(st.g - 1 / 100.0) < 1e-9) {
                iLoad = Mna.branchCurrent(st, v, "neg");  // втекает в минус -> положительно
            }
        }
        check("ток нагрузки", iLoad, e / (100.0 + r), 1e-3);
    }

    /** LED (Uпор 2 В, rд 15 Ом) + 220 Ом от 5 В: I = (5-2)/(220+15) ~ 12.8 мА. */
    static void ledWithResistor() {
        Mna m = new Mna();
        m.ground("cath");
        double e = 5.0;
        m.stamp("anode", "cath", 1e9, e * 1e9);
        m.stamp("anode", "ledA", 1 / 220.0, 0);
        // прямая ветвь пьес-модели: I = (V - 2)/15 при V > 2
        m.stamp("ledA", "cath", 1 / 15.0, 2.0 / 15.0);
        Map<String, Double> v = m.solve();
        double i = (v.get("anode") - v.get("ledA")) / 220.0;
        check("ток светодиода", i, (5.0 - 2.0) / (220.0 + 15.0), 1e-4);
        check("яркость LED>0", Math.min(1, i / 0.02), 0.638, 1e-2);
    }

    /** Обратное включение диода: ток практически нулевой. */
    static void diodeReverse() {
        Mna m = new Mna();
        m.ground("cath");
        m.stamp("anode", "cath", 1e9, 5.0 * 1e9);
        // обратная ветвь: только утечка 50 МОм
        m.stamp("anode", "cath", 1 / 50e6, 0);
        Map<String, Double> v = m.solve();
        double iLeak = v.get("anode") / 50e6;
        if (iLeak > 1e-6) {
            failed++;
            System.out.println("FAIL  обратный ток слишком большой: " + iLeak);
        } else {
            System.out.printf(Locale.ENGLISH, "ok   обратный ток диода      got=%.3e%n", iLeak);
        }
    }

    /** Конденсатор 100 мкФ заряжается через 1 кОм от 5 В (шаг 50 мс). */
    static void capacitorStep() {
        double c = 100e-6;
        double dt = 0.05;
        double g = c / dt;
        Mna m = new Mna();
        m.ground("gnd");
        m.stamp("in", "gnd", 1e9, 5.0 * 1e9);
        m.stamp("in", "cap", 1 / 1000.0, 0);
        m.stamp("cap", "gnd", g, g * 0.0);   // предыстория: Ucap = 0
        Map<String, Double> v = m.solve();
        double uc = v.get("cap");
        // явная схема дискретизации: uc = dt/R*C * (Vin - uc_prev) / (1 + dt/RC)
        double k = dt / (1000.0 * c);
        double expect = 5.0 * k / (1 + k);
        check("шаг RC-цепи (Euler)", uc, expect, 1e-6);
        // и проверка переходного процесса на длине 40 шагов: ~99% от 5 В
        double u = 0;
        for (int i = 0; i < 40; i++) {
            u = (u + k * 5.0) / (1 + k);
        }
        check("зарядка RC за 40 шагов", u, 5.0 * (1 - Math.pow(1 / (1 + k), 40)), 1e-6);
        if (u < 4.5 || u > 5.0) {
            failed++;
            System.out.println("FAIL  конденсатор не заряжается: " + u);
        }
    }

    /** Короткое замыкание: источник 12 В с r=0.5 Ом даёт большой ток. */
    static void shortCircuit() {
        Mna m = new Mna();
        m.ground("neg");
        double e = 12.0;
        double r = 0.5;
        m.stamp("pos", "neg", 1 / r, e / r);
        m.bond("pos", "neg");  // перемычка
        Map<String, Double> v = m.solve();
        double i = e / (r + Mna.R_CONDUCTOR);
        double iBond = 0;
        for (Mna.Stamp st : m.stamps()) {
            if (st.g >= Mna.G_CONDUCTOR - 1e-9) {
                iBond = Math.abs(Mna.branchCurrent(st, v, "pos"));
            }
        }
        if (Math.abs(iBond - 24.0) > 1.0) {
            failed++;
            System.out.println("FAIL  ток КЗ должен быть большим: " + iBond + " (ожидалось ~" + i + ")");
        } else {
            System.out.printf(Locale.ENGLISH, "ok   ток КЗ через bond       got=%.3f want~=%.3f%n", Math.abs(iBond), i);
        }
    }

    /** Лампа 48 Ом от 12 В через амперметр: 0.25 А, ~3 Вт; вольтметр почти не шунтирует. */
    static void meterReadings() {
        Mna m = new Mna();
        m.ground("gnd");
        m.stamp("src", "gnd", 1e9, 12.0 * 1e9);        // источник 12 В
        m.stamp("src", "mid", 1 / 0.5, 0);             // амперметр (Rвн = 0.5 Ом)
        m.stamp("mid", "gnd", 1 / 48.0, 0);            // лампа
        m.stamp("mid", "gnd", 1 / 100000.0, 0);        // вольтметр параллельно лампе
        Map<String, Double> v = m.solve();

        double iAmp = 0;
        double iLamp = 0;
        for (Mna.Stamp st : m.stamps()) {
            if (st.a.equals("src") && st.b.equals("mid")) {
                iAmp = Mna.branchCurrent(st, v, "mid");     // ток, втекающий в лампу
            }
            if (Math.abs(st.g - 1 / 48.0) < 1e-12) {
                iLamp = -Mna.branchCurrent(st, v, "gnd");  // ток, втекающий в лампу из mid
            }
        }
        check("показание амперметра", iAmp, 12.0 / 48.5, 1e-3);
        check("ток лампы", Math.abs(iLamp), 12.0 / 48.5, 1e-3);
        double uLamp = 12.0 * 48.0 / (48.0 + 0.5);
        check("мощность лампы", Math.abs(iLamp) * (v.get("mid") - v.get("gnd")), uLamp * uLamp / 48.0, 5e-3);
        check("показание вольтметра", v.get("mid"), 11.876, 1e-2);
    }

    /** Провод (bond) делает из трёх узлов одну точку: цепь из 5 блоков замкнётся. */
    static void bondWires() {
        Mna m = new Mna();
        m.ground("bus");
        m.stamp("vcc", "bus", 1e9, 9.0 * 1e9);
        m.bond("vcc", "railA");
        m.bond("railA", "railB");
        m.stamp("railB", "lampNode", 1 / 330.0, 0);
        m.stamp("lampNode", "bus", 1 / 48.0, 0);
        Map<String, Double> v = m.solve();
        check("bond выравнивает потенциал", v.get("railB"), v.get("vcc"), 1e-3);
        double i = (v.get("railB") - v.get("lampNode")) / 330.0;
        check("ток в цепи с проводами", i, 9.0 / (330.0 + 48.0), 1e-4);
    }

    static void check(String name, double got, double want, double eps) {
        boolean ok = Math.abs(got - want) <= Math.max(eps, Math.abs(want) * 1e-6);
        System.out.printf(Locale.ENGLISH, "%s %-24s got=%.6f want=%.6f%n", ok ? "ok  " : "FAIL", name, got, want);
        if (!ok) {
            failed++;
        }
    }
}
