import me.arena.electronics.sim.Mna;

import java.util.Map;

/**
 * Проверка связанной индуктивности — 2-порта {@link Mna#coupled}, на котором держатся
 * трансформатор и гальваническая развязка. Bukkit не нужен: голая матрица, те же
 * формулы companion-модели, что в Simulation.stampTransformer.
 *
 * <p>Три вещи, без которых «трансформатор» был бы обманкой:</p>
 * <ol>
 *   <li>при M = 0 2-порт обязан совпасть с одиночной катушкой R + L до последней цифры;</li>
 *   <li>коэффициент напряжений = отношению витков (с погрешностью на намагничивание);</li>
 *   <li>постоянку трансформатор не передаёт вовсе — вторичка обязана умереть в 0.</li>
 * </ol>
 */
public final class TestCoupled {

    private static int fails = 0;
    private static int checks = 0;

    private static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.println((ok ? "  OK    " : "  ПОЗОР ") + what + " — " + detail);
        if (!ok) {
            fails++;
        }
    }

    /** Один шаг companion-модели: возвращает [u1, u2, i1, i2] по решениям матрицы. */
    private static double[] step(Mna mna, String p, String s, double e1, double e2,
                                double a11, double a22, double a12, double det) {
        Map<String, Double> v = mna.solve();
        double u1 = v.getOrDefault(p, 0.0);
        double u2 = s == null ? 0 : v.getOrDefault(s, 0.0);
        double i1 = (a22 * (u1 + e1) - a12 * (u2 + e2)) / det;
        double i2 = s == null ? 0 : (a11 * (u2 + e2) - a12 * (u1 + e1)) / det;
        return new double[]{u1, u2, i1, i2};
    }

    public static void main(String[] args) {
        double dt = 0.002;

        // --- 1) вырождение: M = 0 -> точно та же катушка, что stampInductive --------
        double l = 0.5, r = 10.0, iPrev = 0.02, emf = 9.0, rSrc = 1.0;
        double g = dt / (l + dt * r);
        Mna single = new Mna();
        single.ground("g");
        single.stamp("a", "g", 1.0 / rSrc, emf / rSrc);
        single.stamp("a", "g", g, -(1 - g * r) * iPrev);
        double va = single.solve().getOrDefault("a", 0.0);
        double iSingle = g * va + (1 - g * r) * iPrev;

        double a11 = r + l / dt;
        Mna open = new Mna();
        open.ground("g");
        open.ground("g2");
        open.stamp("a", "g", 1.0 / rSrc, emf / rSrc);
        // тот же элемент, но оформленный как 2-порт с оборванной вторичкой
        open.coupled("a", "g", "c", "g2", 1.0 / a11, 0, 0, 1.0 / a11, (l / dt) * iPrev / a11, 0);
        double vc = open.solve().getOrDefault("a", 0.0);
        double iCoupled = (vc + (l / dt) * iPrev) / a11;
        check("M = 0 совпадает с одиночной катушкой", Math.abs(iSingle - iCoupled) < 1e-12,
                String.format(java.util.Locale.US, "i=%.9f А против %.9f А", iSingle, iCoupled));

        // --- 2) коэффициент напряжения и 3) «постоянку не передаёт» ----------------
        double l1 = 40.0, n = 19.17, r1 = 40.0, k = 0.995;
        double l2 = l1 / (n * n), r2 = r1 / (n * n), m = k * Math.sqrt(l1 * l2);
        double rload = 1000.0, freq = 50.0;
        double a1 = r1 + l1 / dt, a2 = r2 + l2 / dt, am = m / dt;
        double det = a1 * a2 - am * am;

        for (int mode = 0; mode < 2; mode++) {
            boolean ac = mode == 0;
            double v1pk = 0, v2pk = 0, i1 = 0, i2 = 0;
            for (int stepN = 0; stepN < 3000; stepN++) {
                double t = stepN * dt;
                double src = ac ? Math.sin(2 * Math.PI * freq * t) : 1.0;
                Mna g2 = new Mna();
                g2.ground("g");
                g2.stamp("p", "g", 1.0 / rSrc, src / rSrc);      // источник 1 В, r = 1 Ом
                double e1 = (l1 * i1 + m * i2) / dt;
                double e2 = (m * i1 + l2 * i2) / dt;
                g2.coupled("p", "g", "s", "g", a2 / det, -am / det, -am / det, a1 / det,
                        (a2 * e1 - am * e2) / det, (a1 * e2 - am * e1) / det);
                g2.stamp("s", "g", 1.0 / rload, 0);              // нагрузка вторички
                double[] res = step(g2, "p", "s", e1, e2, a1, a2, am, det);
                i1 = res[2];
                i2 = res[3];
                if (stepN > 2000) {                               // переходный процесс прошумел
                    v1pk = Math.max(v1pk, Math.abs(res[0]));
                    v2pk = Math.max(v2pk, Math.abs(res[1]));
                }
            }
            if (ac) {
                double ratio = v1pk / Math.max(1e-12, v2pk);
                check("Кт = 19.17 держится в нагрузке", ratio > 17.5 && ratio < 20.8,
                        String.format(java.util.Locale.US, "U1/U2 = %.2f (N1/N2 = %.2f), амплитуды %.4f В / %.4f В",
                                ratio, n, v1pk, v2pk));
            } else {
                check("постоянку трансформатор не передаёт", Math.abs(i2) < 1e-6 && v2pk < 1e-3,
                        String.format(java.util.Locale.US, "I втор. = %.2e А, U втор. = %.2e В после 3000 шагов",
                                i2, v2pk));
                check("первичка на постоянке = R + L (намагничивание не КЗ)",
                        Math.abs(v1pk - 1.0) < 0.15 && i1 > 0.019 && i1 < 0.026,
                        String.format(java.util.Locale.US, "I пер. = %.4f А (ожидание 1/(1+40) = 0.0244 А)", i1));
            }
        }

        System.out.println(fails == 0 ? "TestCoupled: ALL OK (" + checks + " проверки)"
                : "TestCoupled: ПРОВАЛЕНО " + fails + " из " + checks);
        if (fails > 0) {
            System.exit(1);
        }
    }
}
