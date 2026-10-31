package me.arena.electronics.circuit;

/**
 * Кольцевой буфер отсчётов осциллографа и измерения по окну.
 *
 * <p>Bukkit здесь не нужен ни в какой форме: буфер — это числа, измерения —
 * арифметика. Поэтому класс проверяется «голым» тестом (tools/TestScope.java)
 * наравне с {@link me.arena.electronics.sim.Mna}: то, что экран рисует честный
 * синус, а частотомер не врёт, проверяется без сервера и без мира.</p>
 *
 * <p>Индексация — абсолютная: {@link #at(long)} считает от первого отсчёта в
 * истории, а не от начала массива. Снимок окна тогда описывается одной парой
 * «начало + длина», и стробированию (см. {@link #trigger(int, double)}) не
 * нужно ничего перекладывать — иначе на каждом шаге сдвигался бы весь массив,
 * и картинка ползла бы даже при идеально устойчивом запуске.</p>
 */
public final class ScopeTrace {

    /** Отсчётов в буфере: в 6 раз глубже окна — оттуда берётся и пре-триггер. */
    public static final int CAPACITY = 256;
    /**
     * Столбцов экрана = числу отсчётов в окне (по одному пикселю на столбец).
     * 40, а не «сколько влезет»: экран рисует {@code TextDisplay}, и 4 строки
     * по 20 знака — это ровно столько, сколько держит грань блока рядом с
     * цифровой подписью. Шире — и картинка вылезла бы за прибор.
     */
    public static final int SCREEN_SAMPLES = 40;

    private final double[] buf = new double[CAPACITY];
    /** Сколько отсчётов принято всего: счётчик не обнуляется, буфер — кольцевой. */
    private long total;
    /** Шаг симуляции, пересчитанный в секунды на один ОТСЧЁТ экрана (см. {@link #setRate}). */
    private double dt = 0.05;
    /** Каждое сколько вызовов {@link #sample} писать один отсчёт. */
    private int decimate = 1;
    private int skip;
    /** Кандидат на запись: экстремалный по модулю отклонения от последнего записанного. */
    private double candidate;

    /**
     * Частота опроса и прореживание. Вызывается раз за шаг симуляции на деталь.
     *
     * @param dtSeconds     шаг симуляции, с
     * @param decimate      писать каждый {@code n}-й отсчёт (1 = без прореживания)
     */
    public void setRate(double dtSeconds, int decimate) {
        this.dt = Math.max(1e-4, dtSeconds);
        this.decimate = Math.max(1, decimate);
        this.skip = 0;
    }

    /** Секунд на один отсчёт экрана (уже с учётом прореживания). */
    public double dt() {
        return dt * decimate;
    }

    /**
     * Один шаг симуляции. При прореживании пишется НЕ «первый попавшийся», а
     * самый отклонившийся от прошлого записанного отсчёт — peak-detect. Без него
     * синус 1 Гц на развёртке 0,4 с/столбец превращался в прямую: выборка
     * регулярно попадала около нуля, вершина амплитуды на экран не попадала
     * вовсе, и прибор врал про размах ровно вдвое-втрое.
     */
    public void sample(double v) {
        if (decimate == 1) {
            push(v);
            return;
        }
        double last = total == 0 ? 0 : buf[Math.floorMod(total - 1, CAPACITY)];
        if (skip == 0 || Math.abs(v - last) > Math.abs(candidate - last)) {
            candidate = v;
        }
        if (++skip >= decimate) {
            push(candidate);
            skip = 0;
        }
    }

    private void push(double v) {
        buf[Math.floorMod(total, CAPACITY)] = v;
        total++;
    }

    public void clear() {
        total = 0;
    }

    /** Сколько отсчётов реально накоплено (не больше {@link #CAPACITY}). */
    public int size() {
        return (int) Math.min(total, CAPACITY);
    }

    /** Абсолютный номер первого отсчёта, который ещё живёт в буфере. */
    public long oldest() {
        return total - size();
    }

    /** Отсчёт по абсолютному номеру; {@link Double#NaN} — данных нет. */
    public double at(long i) {
        if (i < oldest() || i >= total) {
            return Double.NaN;
        }
        return buf[Math.floorMod(i, CAPACITY)];
    }

    /** Число принятых отсчётов (история может быть глубже буфера). */
    public long samples() {
        return total;
    }

    // ------------------------------------------------------------------ измерения

    /**
     * Результат измерения по окну. Все напряжения — в вольтах, {@code freqHz} —
     * в герцах, {@code duty} — доля периода выше среднего (0..1).
     *
     * @param start абсолютный номер отсчёта, с которого надо рисовать окно:
     *              триггер уже выбран, картинка не ползёт
     */
    public record Stats(double vMin, double vMax, double vpp, double mean, double rms,
                        double freqHz, double duty, int edges, int len, long start) {

        /** Есть ли на экране что измерять: фронтонов меньше двух — частоты нет. */
        public boolean frequencyKnown() {
            return edges >= 2 && freqHz > 0;
        }
    }

    /**
     * Измерение последних {@code len} отсчётов.
     *
     * <p>Размах, среднее, частота и скважность считаются <b>по целому числу
     * периодов</b> — между первым и последним фронтом окна. Это не педантизм:
     * окно в 84 отсчёта при 20 отсчётах на период содержит 4,2 периода, и
     * «среднее» такого усечённого синуса смещено на десятые вольта. Из
     * смещённого среднего растёт и порог гистерезиса, и момент перехода — то
     * есть на каждом шаге симуляции точка запуска прыгала бы на отсчёт, и
     * картина дрожала бы даже на идеальном сигнале.</p>
     *
     * <p>Экран при этом рисуется шире: {@link Stats#start()} отстоит от фронта
     * на десятую часть окна, чтобы было видно и вершину до события.</p>
     */
    /**
     * Измерение по всему буферу, экран — по {@code displayLen} отсчётов.
     *
     * <p>Размах, среднее, частота и скважность считаются <b>по целому числу
     * периодов</b> — между первым и последним фронтом записи. Это не педантизм:
     * окно в 40 отсчётов при 20 отсчётах на период содержит 2 периода ровно, и
     * «среднее» такого усечённого куска смещено на десятые вольта; из смещённого
     * среднего растут и порог гистерезиса, и момент перехода — точка запуска
     * прыгала бы на отсчёт каждый шаг симуляции, и картина дрожала бы даже на
     * идеальном сигнале.</p>
     *
     * <p>Мерить по всему буферу, а не по экрану, приходится по той же причине:
     * на меандре с периодом 40 отсчётов в 40-отсчётное окно попадает ровно ОДИН
     * фронт, а по одному фронту частоты не бывает (меандр 0,5 Гц читался бы как
     * «нет сигнала»). Запись глубже экрана — как у настоящего прибора: record
     * length ≠ screen width.</p>
     */
    public Stats measure(double dt, int displayLen) {
        int n = size();
        if (n < 4) {
            return new Stats(0, 0, 0, 0, 0, 0, 0, 0, n, total);
        }
        long first0 = oldest();
        double mean = meanOf(first0, n);
        double band = (maxOf(first0, n) - minOf(first0, n)) * HYST;
        int[] e = risingEdges(first0, n, mean, band);
        boolean periodic = e.length >= 2;
        int from = periodic ? e[0] : 0;
        int to = periodic ? e[e.length - 1] : n;

        double sum = 0, sq = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = from; i < to; i++) {
            double v = zero(at(first0 + i));
            sum += v;
            sq += v * v;
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        int cnt = Math.max(1, to - from);
        double measMean = sum / cnt;
        double rms = Math.sqrt(sq / cnt);
        double freq = 0, duty = 0;
        if (periodic) {
            // Частоту и скважность берём из переходов всей записи: во
            // выровненном окне ровно целое число периодов, и второго фронта в нём
            // уже нет — пересчёт «на месте» терял и частоту, и скважность.
            double seconds = cnt * dt;
            freq = seconds > 1e-9 ? (e.length - 1) / seconds : 0;
            int prev = e[e.length - 2], next = e[e.length - 1], high = 0;
            for (int i = prev; i < next; i++) {
                if (zero(at(first0 + i)) > measMean) {
                    high++;
                }
            }
            duty = (double) high / Math.max(1, next - prev);
        }

        // Якорь кадра: ПОСЛЕДНИЙ фронт, от которого экран успевает дотянуться до
        // свежих отсчётов. Если брать просто последний фронт, кадр выходил бы за
        // конец записи и дорисовывался нулями; если брать total - displayLen —
        // картинка ползла бы на отсчёт каждый шаг (что и делал «дрожашщий» экран).
        int dl = Math.max(4, Math.min(displayLen, CAPACITY));
        int pre = dl / 10;
        long anchor = -1;
        if (periodic) {
            for (int i = e.length - 1; i >= 0; i--) {
                long abs = first0 + e[i];
                if (abs + dl - pre <= total) {
                    anchor = abs;
                    break;
                }
            }
        }
        long displayStart = anchor >= 0 ? Math.max(anchor - pre, first0) : Math.max(total - dl, first0);
        return new Stats(lo, hi, hi - lo, measMean, rms, freq, duty, e.length, cnt, displayStart);
    }

    /** Точка, с которой надо рисовать окно (фронт + пре-триггер). */
    public long triggerStart(int len, double dt) {
        return measure(dt, len).start();
    }

    /**
     * Измерение по сохранённому шагу (его задаёт {@link #setRate}). Именно
     * {@link #dt()}, а не полем {@code dt}: после прореживания один отсчёт
     * экрана стоит dt×decimate секунд, и подставить сюда шаг симуляции — значит
     * измерить частоту в «раз» выше (синус 1 Гц на развёртке 0,4 с/отсчёт
     * превращался в 8 Гц).
     */
    public Stats measure() {
        return measure(dt(), SCREEN_SAMPLES);
    }

    /** То же, но с явным числом столбцов экрана (тесты сверяют и узкие окна). */
    public Stats measure(int displayLen) {
        return measure(dt(), displayLen);
    }

    /** Точка, с которой надо рисовать окно, по сохранённому шагу. */
    public long triggerStart() {
        return measure(dt(), SCREEN_SAMPLES).start();
    }


    /**
     * Измерение произвольного окна: уровни, среднее, RMS и частота по этому же
     * окну. Нужен тестам и «ручному» режиму, где выравнивать не на что.
     */
    public Stats measureRange(long start, int n, double dt) {
        return measureRange(start, n, dt, start);
    }

    public Stats measureRange(long start, int n, double dt, long displayStart) {
        if (n < 2) {
            return new Stats(0, 0, 0, 0, 0, 0, 0, 0, Math.max(0, n), displayStart);
        }
        double sum = 0, sq = 0, lo = Double.POSITIVE_INFINITY, hi = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            double v = zero(at(start + i));
            sum += v;
            sq += v * v;
            lo = Math.min(lo, v);
            hi = Math.max(hi, v);
        }
        double mean = sum / n;
        double rms = Math.sqrt(sq / n);
        int[] e = risingEdges(start, n, mean, (hi - lo) * HYST);
        double freq = 0, duty = 0;
        if (e.length >= 2) {
            double period = (double) (e[e.length - 1] - e[0]) / (e.length - 1) * dt;
            freq = period > 1e-9 ? 1.0 / period : 0;
            int from = e[e.length - 2], to = e[e.length - 1], high = 0;
            for (int i = from; i < to; i++) {
                if (zero(at(start + i)) > mean) {
                    high++;
                }
            }
            duty = (double) high / Math.max(1, to - from);
        }
        return new Stats(lo, hi, hi - lo, mean, rms, freq, duty, e.length, n, displayStart);
    }


    /** Полоса гистерезиса вокруг среднего: без неё шум на нуле рождает «частоту». */
    private static final double HYST = 0.10;

    private double meanOf(long start, int n) {
        double sum = 0;
        for (int i = 0; i < n; i++) {
            sum += zero(at(start + i));
        }
        return sum / Math.max(1, n);
    }

    private double minOf(long start, int n) {
        double m = Double.POSITIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            m = Math.min(m, zero(at(start + i)));
        }
        return m;
    }

    private double maxOf(long start, int n) {
        double m = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < n; i++) {
            m = Math.max(m, zero(at(start + i)));
        }
        return m;
    }

    /** Нет данных — это 0 В, а не NaN: экран обязан дорисовываться до конца. */
    private static double zero(double v) {
        return Double.isNaN(v) ? 0.0 : v;
    }

    /**
     * Индексы переходов вверх через {@code mean} с гистерезисом {@value #HYST} от
     * размаха. Гистерезис нужен, чтобы «взвести» трассу: на постоянке с зерном
     * решателя (единицы нановольт) переходы случались бы в каждом втором
     * отсчёте, и прибор показал бы 5 Гц на проводе, к которому просто подключили
     * батарею. Поэтому момент перехода — первый отсчёт ВЫШЕ среднего после
     * взведения, а сам порог только решает, пора ли искать.
     */
    private int[] risingEdges(long start, int n, double mean, double band) {
        if (band < 1e-6) {
            return new int[]{0};                       // ниже микровольта это постоянка
        }
        int[] out = new int[Math.max(1, n / 2)];
        int k = 0;
        boolean below = true;
        for (int i = 0; i < n; i++) {
            double v = zero(at(start + i));
            if (below && v > mean + band) {
                below = false;
                int j = i;
                while (j > 0 && zero(at(start + j - 1)) > mean) {
                    j--;                               // к первому отсчёту выше среднего
                }
                if (j == 0 && (start == 0 || zero(at(start - 1)) > mean)) {
                    continue;                          // окно началось уже «на фронте»: это не фронт
                }
                if (k < out.length) {
                    out[k++] = j;
                }
            } else if (!below && v < mean - band) {
                below = true;
            }
        }
        if (k == 0) {
            return new int[]{0};
        }
        int[] r = new int[k];
        System.arraycopy(out, 0, r, 0, k);
        return r;
    }

    /** Последний отсчёт (для цифровой шкалы «сейчас»). */
    public double last() {
        return total == 0 ? 0 : at(total - 1);
    }
}
