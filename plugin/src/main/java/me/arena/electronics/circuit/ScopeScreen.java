package me.arena.electronics.circuit;

/**
 * Растр экрана осциллографа: маска пикселей и упаковка её в брайль.
 *
 * <p>Экран рисуется одним {@code TextDisplay}: у знака Брайля сетка 2×4 точки,
 * поэтому 42 знака в ширину и 8 в высоту дают 84×32 пикселя — ровно столько,
 * сколько нужно, чтобы разглядеть синус, фронт и пульсации после моста. Никаких
 * клиентских модов и пиксельных пакетов: это обычный текст, только символы в
 * нём — точки.</p>
 *
 * <p>Класс чистая арифметика (Bukkit не нужен), и потому крыт тестом: проверка
 * «строка на середине экрана = ровно средняя линия» дешевле, чем отладка глазами
 * на живом сервере.</p>
 */
public final class ScopeScreen {

    /** Пикселей по горизонтали (= числу отсчётов в окне, {@link ScopeTrace#SCREEN_SAMPLES}). */
    public static final int W = ScopeTrace.SCREEN_SAMPLES;
    /** Пикселей по вертикали (4 знака брайля × 4 точки = 4 строки текста). */
    public static final int H = 16;

    private ScopeScreen() {
    }

    /** Индекс пикселя: y = 0 — верхний ряд. */
    public static int idx(int x, int y) {
        return y * W + x;
    }

    public static boolean[] mask() {
        return new boolean[W * H];
    }

    /**
     * Строка пикселя для напряжения {@code v} в шкале {@code [lo, hi]}.
     * 0 — верх экрана; за границами шкалы — {@code -1} (пиксель не рисуем).
     */
    public static int rowOf(double v, double lo, double hi) {
        if (Double.isNaN(v)) {
            return -1;                                 // отсчёта нет — строки нет
        }
        double span = hi - lo;
        if (!(span > 1e-9)) {
            return v > lo ? 0 : H - 1;                 // шкалы нет: линия по краю
        }
        double t = (v - lo) / span;
        if (t < -0.02 || t > 1.02) {
            return -1;
        }
        int row = (int) Math.round((1.0 - t) * (H - 1));
        return Math.max(0, Math.min(H - 1, row));
    }

    /**
     * Автошкала: центр на среднем, половина — полуразмах с запасом. Симметрично
     * относительно среднего, а не вокруг нуля: постоянка со 100 мВ пульсаций
     * на шкале «от 0» выглядела бы прямой линией.
     */
    public static double[] autoscale(double mean, double vpp, double headroom) {
        double half = Math.max(0.05, vpp * 0.5 * Math.max(1.0, headroom));
        return new double[]{mean - half, mean + half};
    }

    /**
     * Рисует трассу. {@code v[i]} — отсчёт столбца i. Между соседними столбцами
     * вертикаль достраивается: без этого синус из 84 точек выглядит пунктиром,
     * а наклонный участок (заряд конденсатора) — лесенкой.
     */
    public static void trace(double[] v, double lo, double hi, boolean[] px) {
        int prev = -1;
        for (int x = 0; x < W; x++) {
            double val = x < v.length ? v[x] : Double.NaN;
            int row = rowOf(val, lo, hi);
            if (row < 0) {
                prev = -1;
                continue;
            }
            if (prev >= 0 && Math.abs(row - prev) > 1) {
                for (int y = Math.min(prev, row); y <= Math.max(prev, row); y++) {
                    px[idx(x, y)] = true;
                }
            }
            px[idx(x, row)] = true;
            if (prev >= 0 && row != prev) {
                px[idx(x, prev)] = true;               // ступенька: держаем прошлый уровень
            }
            prev = row;
        }
    }

    /**
     * Сетка делений: средняя горизонталь и вертикали через {@code divX} долей,
     * по боковым — короткие риски. Сетка нужна, чтобы «вольт на деление» было с
     * чем сравнить: трасса без делений не даёт измерить ничего глазом.
     */
    public static void grid(boolean[] px, int divX, int divY) {
        int mid = H / 2 - 1;
        for (int x = 0; x < W; x += 2) {
            px[idx(x, mid)] = true;
        }
        for (int i = 1; i < Math.max(1, divX); i++) {
            int x = W * i / Math.max(1, divX);
            for (int y = 0; y < H; y += 2) {
                if (Math.abs(y - mid) > 1) {           // середку не забиваем
                    px[idx(x, y)] = true;
                }
            }
        }
        for (int i = 1; i < Math.max(1, divY); i++) {
            int y = H * i / Math.max(1, divY);
            for (int x = 0; x < W; x += 12) {
                px[idx(x, y)] = true;
            }
        }
    }

    /** Точки прошлого кадра: после свечения люминофор гаснет, а не исчезает. */
    public static void afterglow(boolean[] px, boolean[] prev, int everyOtherColumn) {
        if (prev == null) {
            return;
        }
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                if (prev[idx(x, y)] && (x % Math.max(1, everyOtherColumn)) == 0) {
                    px[idx(x, y)] = true;
                }
            }
        }
    }

    /**
     * Упаковка маски в строки знаков Брайля (по {@value #H}/4 строки текста).
     * Номера точек: левый столбец 1,2,3,7, правый 4,5,6,8; код — 0x2800 + биты.
     */
    public static String[] braille(boolean[] px) {
        int rows = H / 4;
        String[] out = new String[rows];
        for (int r = 0; r < rows; r++) {
            StringBuilder sb = new StringBuilder();
            for (int c = 0; c < W / 2; c++) {
                int bits = 0;
                for (int dy = 0; dy < 4; dy++) {
                    int y = r * 4 + dy;
                    // Номера точек Брайля идут НЕ подряд: левый столбец — 1,2,3,7,
                    // правый — 4,5,6,8, то есть биты 0,1,2,6 и 3,4,5,7. Если
                    // ставить «dy» и «dy+3», нижняя строка каждого знака уезжает в
                    // чужой столбец, и трасса на 4-й строке выглядит сдвинутой на
                    // ползнака — на глаз в игре это «мыло», а не линия.
                    if (get(px, c * 2, y)) {
                        bits |= 1 << (dy == 3 ? 6 : dy);
                    }
                    if (get(px, c * 2 + 1, y)) {
                        bits |= 1 << (dy == 3 ? 7 : dy + 3);
                    }
                }
                sb.append((char) (0x2800 + bits));
            }
            out[r] = sb.toString();
        }
        return out;
    }

    private static boolean get(boolean[] px, int x, int y) {
        return x >= 0 && x < W && y >= 0 && y < H && px[idx(x, y)];
    }

    /** Экран одной строкой (для логов и теста). */
    public static String render(boolean[] px) {
        return String.join("\n", braille(px));
    }

    /** Число светящихся пикселей — тест проверяет, что трасса вообще есть. */
    public static int lit(boolean[] px) {
        int n = 0;
        for (boolean b : px) {
            if (b) {
                n++;
            }
        }
        return n;
    }
}
