package me.arena.electronics.sim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Модифицированный узловой анализ (MNA) на проводимостях.
 *
 * Элемент схемы описывается «stamp'ом»: проводимость G между узлами a и b
 * плюс источник тока i, втекающий в a (и вытекающий из b).
 * Узловые уравнения: G * V = -I.
 *
 * Индексы узлов назначаются один раз при решении (solve), поэтому порядок
 * вставки элементов не влияет на раскладку матрицы. Класс не зависит от Bukkit,
 * что позволяет прогнать его через отдельный тест (tools/TestMna.java).
 */
public final class Mna {

    /** Сопротивление «идеального» контакта (провод, замкнутый ключ), Ом. */
    public static final double R_CONDUCTOR = 1e-6;
    public static final double G_CONDUCTOR = 1 / R_CONDUCTOR;

    private final List<Stamp> stamps = new ArrayList<>();
    private final List<Coupled> coupled = new ArrayList<>();
    private final Set<String> nodes = new LinkedHashSet<>();
    private final Map<String, Double> ground = new HashMap<>();

    /**
     * Связанный 2-порт (трансформатор): токи на портах линейны по напряжениям,
     * причём перекрёстные проводимости отрицательные — обычной «проводимостью
     * между двумя узлами» это не выразить, поэтому коэффициентная запись.
     *
     * <p>Знаки согласованы с {@link #stamp}: строка узла a содержит
     * y11·(Va−Vb) + y12·(Vc−Vd) = −h1 (история тока уносится в правую часть).</p>
     */
    public static final class Coupled {
        final String a;
        final String b;
        final String c;
        final String e;
        final double y11;
        final double y12;
        final double y21;
        final double y22;
        final double h1;
        final double h2;

        Coupled(String a, String b, String c, String e, double y11, double y12,
                double y21, double y22, double h1, double h2) {
            this.a = a;
            this.b = b;
            this.c = c;
            this.e = e;
            this.y11 = y11;
            this.y12 = y12;
            this.y21 = y21;
            this.y22 = y22;
            this.h1 = h1;
            this.h2 = h2;
        }
    }

    /** Запись о вставленном элементе. */
    public static final class Stamp {
        public final String a;
        public final String b;
        public final double g;
        public final double i;

        Stamp(String a, String b, double g, double i) {
            this.a = a;
            this.b = b;
            this.g = g;
            this.i = i;
        }
    }

    public void node(String key) {
        nodes.add(key);
    }

    /** Узел фиксируется в 0 В. */
    public void ground(String key) {
        ground(key, 0.0);
    }

    public void ground(String key, double voltage) {
        node(key);
        ground.put(key, voltage);
    }

    /** Элемент: проводимость g между from/to, ток i втекает в from. */
    public void stamp(String from, String to, double conductance, double current) {
        if (from.equals(to) || !(conductance > 0)) {
            return;
        }
        node(from);
        node(to);
        stamps.add(new Stamp(from, to, conductance, current));
        inflowCache = null;
    }

    /** Связанные обмотки: порты (a,b) и (c,e) с проводимостями y** и источниками истории h**. */
    public void coupled(String a, String b, String c, String e,
                        double y11, double y12, double y21, double y22, double h1, double h2) {
        if (a.equals(b) || c.equals(e) || !(y11 > 0) || !(y22 > 0)) {
            return;
        }
        node(a);
        node(b);
        node(c);
        node(e);
        coupled.add(new Coupled(a, b, c, e, y11, y12, y21, y22, h1, h2));
        inflowCache = null;
    }

    /** Количество связанных 2-портов (для диагностики: у трансформатора их один). */
    public int coupledCount() {
        return coupled.size();
    }

    /** Идеальный проводник между узлами (для проводов, шин, замкнутых ключей). */
    public void bond(String from, String to) {
        stamp(from, to, G_CONDUCTOR, 0);
    }

    public int nodeCount() {
        return nodes.size();
    }

    public boolean isFloating() {
        return ground.isEmpty();
    }

    public List<Stamp> stamps() {
        return stamps;
    }

    /** Решает систему и возвращает напряжения по именам узлов. */
    public Map<String, Double> solve() {
        Map<String, Integer> idx = new HashMap<>();
        List<String> list = new ArrayList<>();
        for (String key : nodes) {
            idx.put(key, list.size());
            list.add(key);
        }
        for (String key : ground.keySet()) {
            idx.computeIfAbsent(key, k -> {
                list.add(k);
                return list.size() - 1;
            });
        }
        int n = list.size();
        Map<String, Double> out = new HashMap<>(Math.max(4, n * 2));
        if (n == 0) {
            return out;
        }
        double[][] a = new double[n][n + 1];

        for (Stamp s : stamps) {
            int ia = idx.get(s.a);
            int ib = idx.get(s.b);
            a[ia][ia] += s.g;
            a[ib][ib] += s.g;
            a[ia][ib] -= s.g;
            a[ib][ia] -= s.g;
            // G*V = I, где в узел a втекает +i, из b вытекает
            a[ia][n] += s.i;
            a[ib][n] -= s.i;
        }

        for (Coupled k : coupled) {
            int ka = idx.get(k.a);
            int kb = idx.get(k.b);
            int kc = idx.get(k.c);
            int ke = idx.get(k.e);
            // порт 1: i1 = y11*u1 + y12*u2 + h1  (уходит из a, приходит в b)
            a[ka][ka] += k.y11;
            a[kb][kb] += k.y11;
            a[ka][kb] -= k.y11;
            a[kb][ka] -= k.y11;
            a[ka][kc] += k.y12;
            a[kb][kc] -= k.y12;
            a[ka][ke] -= k.y12;
            a[kb][ke] += k.y12;
            a[ka][n] -= k.h1;
            a[kb][n] += k.h1;
            // порт 2: i2 = y21*u1 + y22*u2 + h2
            a[kc][kc] += k.y22;
            a[ke][ke] += k.y22;
            a[kc][ke] -= k.y22;
            a[ke][kc] -= k.y22;
            a[kc][ka] += k.y21;
            a[ke][ka] -= k.y21;
            a[kc][kb] -= k.y21;
            a[ke][kb] += k.y21;
            a[kc][n] -= k.h2;
            a[ke][n] += k.h2;
        }

        boolean floating = ground.isEmpty();
        if (floating) {
            // ни одной земли: фиксируем первый узел, чтобы система была разрешима
            ground.put(list.get(0), 0.0);
        }

        // фиксированные узлы: большой диагональный вес (аналог elimination)
        List<Integer> free = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            if (!ground.containsKey(list.get(i))) {
                free.add(i);
            }
        }
        for (Map.Entry<String, Double> e : ground.entrySet()) {
            Integer gi = idx.get(e.getKey());
            if (gi == null) {
                continue;
            }
            for (int r = 0; r < n; r++) {
                if (r != gi) {
                    a[r][n] -= a[r][gi] * e.getValue();
                    a[r][gi] = 0;
                }
            }
            for (int c = 0; c <= n; c++) {
                a[gi][c] = 0;
            }
            a[gi][gi] = 1;
            a[gi][n] = e.getValue();
        }

        int m = free.size();
        double[][] sub = new double[m][m + 1];
        for (int r = 0; r < m; r++) {
            for (int c = 0; c < m; c++) {
                sub[r][c] = a[free.get(r)][free.get(c)];
            }
            sub[r][m] = a[free.get(r)][n];
        }
        gaussian(sub, m);
        double[] v = new double[n];
        for (int i = 0; i < n; i++) {
            Double g = ground.get(list.get(i));
            v[i] = g != null ? g : 0;
        }
        for (int r = 0; r < m; r++) {
            v[free.get(r)] = sub[r][m];
        }
        for (int i = 0; i < n; i++) {
            out.put(list.get(i), v[i]);
        }
        return out;
    }

    /** Ток, втекающий в узел {@code node} со стороны элемента stamp. */
    public static double branchCurrent(Stamp s, Map<String, Double> v, String node) {
        double va = v.getOrDefault(s.a, 0.0);
        double vb = v.getOrDefault(s.b, 0.0);
        double i = s.g * (vb - va) + s.i;
        return s.a.equals(node) ? i : -i;
    }

    /** Сумма токов, втекающих в узел (для амперметра). */
    public double netInflow(String node, Map<String, Double> v) {
        Map<String, List<Stamp>> byNode = inflowIndex();
        double sum = 0;
        List<Stamp> list = byNode.get(node);
        if (list != null) {
            for (Stamp s : list) {
                sum += branchCurrent(s, v, node);
            }
        }
        return sum;
    }

    /** Индекс «узел -> элементы» (ленивая сборка, инвалидируется новым stamp). */
    private Map<String, List<Stamp>> inflowCache;

    private Map<String, List<Stamp>> inflowIndex() {
        if (inflowCache == null) {
            Map<String, List<Stamp>> m = new HashMap<>();
            for (Stamp s : stamps) {
                m.computeIfAbsent(s.a, k -> new ArrayList<>()).add(s);
                m.computeIfAbsent(s.b, k -> new ArrayList<>()).add(s);
            }
            inflowCache = m;
        }
        return inflowCache;
    }

    /** Элемент между двумя узлами с наибольшей проводимостью (для измерений). */
    public Stamp findBranch(String a, String b) {
        Stamp best = null;
        for (Stamp s : stamps) {
            if ((s.a.equals(a) && s.b.equals(b)) || (s.a.equals(b) && s.b.equals(a))) {
                if (best == null || s.g < best.g) {
                    // берём реальный элемент, а не идеальный контакт
                    best = s;
                }
            }
        }
        return best;
    }

    /** Гаусс с выбором ведущего элемента (по всем строкам, с фиксированными узлами). */
    static void gaussian(double[][] a, int n) {
        for (int col = 0; col < n; col++) {
            int piv = col;
            double best = Math.abs(a[col][col]);
            for (int r = col + 1; r < n; r++) {
                double cur = Math.abs(a[r][col]);
                if (cur > best) {
                    best = cur;
                    piv = r;
                }
            }
            if (best < 1e-14) {
                for (int c = col; c <= n; c++) {
                    a[col][c] = 0;
                }
                continue;
            }
            double[] tmp = a[col];
            a[col] = a[piv];
            a[piv] = tmp;
            double d = a[col][col];
            for (int c = col; c <= n; c++) {
                a[col][c] /= d;
            }
            for (int r = 0; r < n; r++) {
                if (r == col) {
                    continue;
                }
                double f = a[r][col];
                if (f == 0) {
                    continue;
                }
                for (int c = col; c <= n; c++) {
                    a[r][c] -= f * a[col][c];
                }
            }
        }
    }
}
