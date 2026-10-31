import me.arena.electronics.circuit.ScopeScreen;
import me.arena.electronics.circuit.ScopeTrace;

/**
 * Проверка ядра осциллографа: кольцевого буфера, измерений и растра экрана.
 *
 * <p>Bukkit не нужен — те же классы, что видит сервер. Здесь ровно то, за чем
 * обычно «осциллограф в Minecraft» и ломают физику:</p>
 * <ol>
 *   <li>буфер не должен врать на переходе через кольцо (300 отсчётов в 256);</li>
 *   <li>на постоянке частоты быть не может: без гистерезиса шум решателя
 *       рождает 5 Гц из ниоткуда, и прибор начинает врать ученику;</li>
 *   <li>частотомер и скважность обязаны попадать в настройки сигнала, иначе
 *       цифры на экране — декорация;</li>
 *   <li>стрелка трассы — линия, а не пунктир: между столбцами пиксели
 *       достраиваются, иначе синус из 84 точек читается как «точки».</li>
 * </ol>
 */
public final class TestScope {

    private static int fails = 0;
    private static int checks = 0;

    private static void check(String what, boolean ok, String detail) {
        checks++;
        System.out.println((ok ? "  OK    " : "  ПОЗОР ") + what + " — " + detail);
        if (!ok) {
            fails++;
        }
    }

    private static double sine(double t, double hz, double amp, double offset) {
        return offset + amp * Math.sin(2 * Math.PI * hz * t);
    }

    public static void main(String[] args) {
        final double dt = 0.05;                       // шаг решателя: 20 отсчётов на период при 1 Гц

        // --- 1. кольцо: старые отсчёты умирают, новые не сдвигаются -------------
        {
            ScopeTrace tr = new ScopeTrace();
            for (int i = 0; i < 300; i++) {
                tr.sample(i);
            }
            check("кольцо держит ровно CAPACITY отсчётов",
                    tr.size() == ScopeTrace.CAPACITY && tr.samples() == 300,
                    "size=" + tr.size() + ", принято=" + tr.samples());
            check("последний отсчёт — самый свежий, а не первый",
                    tr.last() == 299.0 && tr.at(299) == 299.0,
                    "last=" + tr.last());
            check("за границей буфера данных нет (NaN), а не мусор",
                    Double.isNaN(tr.at(10)) && Double.isNaN(tr.at(301)) && tr.at(300 - 256) == 44.0,
                    "oldest=" + tr.oldest() + ", at(44)=" + tr.at(44));
        }

        // --- 1b. прореживание с peak-detect: вершины не должны теряться ---------
        {
            ScopeTrace d = new ScopeTrace();
            d.setRate(dt, 8);                          // 0,4 с на отсчёт экрана
            check("шаг отсчёта экрана = шаг симуляции × прореживание",
                    Math.abs(d.dt() - 0.4) < 1e-12, "dt=" + d.dt() + " с");
            for (int i = 0; i < 320; i++) {
                d.sample(sine(i * dt, 1.0, 6.0, 0.0));
            }
            check("записан каждый 8-й отсчёт, а не все 320",
                    d.size() == 40, "size=" + d.size());
            ScopeTrace.Stats st = d.measure();
            check("peak-detect сохраняет размах на медленной развёртке",
                    Math.abs(st.vpp() - 12.0) < 1.5,
                    String.format(java.util.Locale.US, "vpp=%.2f В при амплитуде 6 В "
                            + "(без peak-detect было бы ~6 В)", st.vpp()));
            check("и на медленной развёртке частота всё ещё 1 Гц",
                    Math.abs(st.freqHz() - 1.0) < 0.1,
                    String.format(java.util.Locale.US, "f=%.3f Гц", st.freqHz()));
        }

        // --- 2. постоянка: нулевой размах и НИКАКОЙ частоты --------------------
        {
            ScopeTrace tr = new ScopeTrace();
            for (int i = 0; i < 200; i++) {
                tr.sample(5.0 + ((i % 3) - 1) * 1e-9); // постоянка с шумом решателя
            }
            ScopeTrace.Stats st = tr.measure(dt, ScopeTrace.SCREEN_SAMPLES);
            check("постоянка: размах почти ноль, среднее = 5 В",
                    st.vpp() < 1e-6 && Math.abs(st.mean() - 5.0) < 1e-6,
                    String.format(java.util.Locale.US, "vpp=%.2e, mean=%.6f", st.vpp(), st.mean()));
            check("постоянка не рождает ложную частоту",
                    !st.frequencyKnown() && st.freqHz() == 0,
                    "edges=" + st.edges() + ", freq=" + st.freqHz());
            check("rms на постоянке = самому напряжению",
                    Math.abs(st.rms() - 5.0) < 1e-6,
                    String.format(java.util.Locale.US, "rms=%.6f", st.rms()));
        }

        // --- 3. синус 1 Гц: частота, размах, центр ------------------------------
        {
            ScopeTrace tr = new ScopeTrace();
            for (int i = 0; i < 200; i++) {
                tr.sample(sine(i * dt, 1.0, 6.0, 0.0));
            }
            ScopeTrace.Stats st = tr.measure(dt, ScopeTrace.SCREEN_SAMPLES);
            check("синус 1 Гц измерен как 1 Гц",
                    Math.abs(st.freqHz() - 1.0) < 0.05,
                    String.format(java.util.Locale.US, "f=%.4f Гц (период 20 отсчётов при dt=50 мс)", st.freqHz()));
            check("Vpp = 2×амплитуды (с квантованием сетки решателя)",
                    Math.abs(st.vpp() - 12.0) < 1.0,
                    String.format(java.util.Locale.US, "vpp=%.3f В при амплитуде 6 В", st.vpp()));
            check("среднее синуса = 0",
                    Math.abs(st.mean()) < 0.4,
                    String.format(java.util.Locale.US, "mean=%.3f В", st.mean()));
            check("скважность синуса ~ 50 %",
                    st.duty() > 0.4 && st.duty() < 0.6,
                    String.format(java.util.Locale.US, "duty=%.3f", st.duty()));
        }

        // --- 4. меандр 25 %: скважность считается по полному периоду ------------
        {
            ScopeTrace tr = new ScopeTrace();
            for (int i = 0; i < 200; i++) {
                int ph = i % 40;                      // период 40 отсчётов = 2 с → 0.5 Гц
                tr.sample(ph < 10 ? 1.0 : 0.0);
            }
            ScopeTrace.Stats st = tr.measure(dt, ScopeTrace.SCREEN_SAMPLES);
            check("меандр 0.5 Гц измерен как 0.5 Гц",
                    Math.abs(st.freqHz() - 0.5) < 0.02,
                    String.format(java.util.Locale.US, "f=%.4f Гц", st.freqHz()));
            check("скважность 25 % не уползла из-за усечённого хвоста окна",
                    Math.abs(st.duty() - 0.25) < 0.02,
                    String.format(java.util.Locale.US, "duty=%.3f", st.duty()));
            check("уровни меандра = 0 В и 1 В",
                    Math.abs(st.vMin()) < 1e-9 && Math.abs(st.vMax() - 1.0) < 1e-9,
                    String.format(java.util.Locale.US, "%.3f..%.3f В", st.vMin(), st.vMax()));
        }

        // --- 5. триггер: картинка стоит на фронте -------------------------------
        {
            ScopeTrace a = new ScopeTrace();
            ScopeTrace b = new ScopeTrace();
            for (int i = 0; i < 100; i++) {
                a.sample(sine(i * dt, 1.0, 6.0, 0.0));
            }
            for (int i = 0; i < 101; i++) {           // на один шаг свежее: без триггера картинка уехала бы
                b.sample(sine(i * dt, 1.0, 6.0, 0.0));
            }
            long sa = a.triggerStart(ScopeTrace.SCREEN_SAMPLES, dt);
            long sb = b.triggerStart(ScopeTrace.SCREEN_SAMPLES, dt);
            // сравнение не «значит на фронте», а всего кадра: 84 отсчёта подряд
            boolean same = true;
            for (int i = 0; i < ScopeTrace.SCREEN_SAMPLES; i++) {
                if (Math.abs(a.at(sa + i) - b.at(sb + i)) > 1e-9) {
                    same = false;
                }
            }
            check("кадр не дрожит: тот же сигнал на шаг позже даёт тот же экран",
                    same,
                    String.format(java.util.Locale.US, "%d отсчётов кадра совпали; u(0)=%.3f В",
                            ScopeTrace.SCREEN_SAMPLES, a.at(sa)));
            int pre = ScopeTrace.SCREEN_SAMPLES / 10;
            check("пре-триггер: окно начинается ДО фронта, а не на нём",
                    a.at(sa) < 0 && a.at(sa + pre) > a.at(sa + pre - 1),
                    String.format(java.util.Locale.US,
                            "u(start)=%.3f В, фронт на отсчёте %d: %.3f > %.3f",
                            a.at(sa), pre, a.at(sa + pre), a.at(sa + pre - 1)));
        }

        // --- 6. экран: постоянка = средняя линия, наклон = линия, а не пунктир --
        {
            double[] flat = new double[ScopeScreen.W];
            java.util.Arrays.fill(flat, 0.0);
            boolean[] px = ScopeScreen.mask();
            ScopeScreen.trace(flat, -6, 6, px);
            int lit = ScopeScreen.lit(px);
            check("ровная линия = ровно по пикселю на столбец",
                    lit == ScopeScreen.W,
                    "lit=" + lit + " при ширине " + ScopeScreen.W);
            int mid = ScopeScreen.rowOf(0.0, -6, 6);
            check("ноль шкалы — середина экрана",
                    Math.abs(mid - (ScopeScreen.H - 1) / 2.0) <= 1,
                    "row=" + mid + " из " + ScopeScreen.H);
            check("брайль упакован в " + (ScopeScreen.H / 4) + " строк по " + (ScopeScreen.W / 2) + " знака",
                    ScopeScreen.braille(px).length == ScopeScreen.H / 4
                            && ScopeScreen.braille(px)[0].length() == ScopeScreen.W / 2,
                    ScopeScreen.braille(px).length + "×" + ScopeScreen.braille(px)[0].length());

            // обратное развёртывание: то, что увидит клиент, должно быть тем же
            // растром, что мы собрали. Брайль — единственное место, где картинка
            // превращается в символы, и ошибись я в битах на единицу — синус
            // вышел бы перевёрнутым или сдвинутым на ползнака.
            String[] scr = ScopeScreen.braille(px);
            boolean[] back = ScopeScreen.mask();
            // Развёртка здесь написана по ОПРЕДЕЛению Брайля, а не по коду
            // ScopeScreen: точки занумерованы 1 4 / 2 5 / 3 6 / 7 8, в слове —
            // битами 0..7 именно в этом порядке. Если бы я повторил формулу из
            // реализации, проверка стала бы самоцитатой и не поймала бы ровно
            // тот баг, который она поймала (перепутанные столбцы нижнего ряда).
            final int[] LEFT = {0, 1, 2, 6};
            final int[] RIGHT = {3, 4, 5, 7};
            for (int r = 0; r < scr.length; r++) {
                for (int c = 0; c < scr[r].length(); c++) {
                    int bits = scr[r].charAt(c) - 0x2800;
                    for (int dy = 0; dy < 4; dy++) {
                        if ((bits & (1 << LEFT[dy])) != 0) {
                            back[ScopeScreen.idx(c * 2, r * 4 + dy)] = true;
                        }
                        if ((bits & (1 << RIGHT[dy])) != 0) {
                            back[ScopeScreen.idx(c * 2 + 1, r * 4 + dy)] = true;
                        }
                    }
                }
            }
            boolean sameBits = true;
            for (int i = 0; i < px.length; i++) {
                if (px[i] != back[i]) {
                    sameBits = false;
                }
            }
            check("растр проходит брайль туда и обратно без потерь", sameBits,
                    "пикселей до/после: " + ScopeScreen.lit(px) + "/" + ScopeScreen.lit(back));

            // крутой участок: два отсчёта на 10 строк друг от друга обязаны быть
            // соединены — иначе синус читается пунктиром
            double[] step = new double[ScopeScreen.W];
            step[0] = -5;
            step[1] = 5;
            boolean[] px2 = ScopeScreen.mask();
            ScopeScreen.trace(step, -6, 6, px2);
            // стык двух отсчётов: вертикаль обязана закрыть разрыв суммарно по
            // двум столбцам (сам отрезок рисуют в столбце правого отсчёта)
            int cnt = 0;
            for (int y = 0; y < ScopeScreen.H; y++) {
                if (px2[ScopeScreen.idx(0, y)] || px2[ScopeScreen.idx(1, y)]) {
                    cnt++;
                }
            }
            int expect = Math.abs(ScopeScreen.rowOf(-5, -6, 6) - ScopeScreen.rowOf(5, -6, 6)) + 1;
            check("наклон достраивается вертикалью (не лесенка)",
                    cnt == expect,
                    "на стыке столбцов 0-1 светится " + cnt + " из " + ScopeScreen.H
                            + " строк, шкала ±6 В ждёт " + expect);

            boolean[] grid = ScopeScreen.mask();
            ScopeScreen.grid(grid, 12, 8);
            check("сетка рисует среднюю горизонталь",
                    grid[ScopeScreen.idx(20, ScopeScreen.H / 2 - 1)],
                    "риск по середине экрана есть");

            double[] as = ScopeScreen.autoscale(5.0, 12.0, 1.3);
            check("автошкала центрируется на среднем и даёт запас 30 %",
                    Math.abs(as[0] + 2.8) < 1e-9 && Math.abs(as[1] - 12.8) < 1e-9,
                    String.format(java.util.Locale.US, "[%.2f .. %.2f] В", as[0], as[1]));

            // послесвечение: кадр, которого уже нет, должен виднеться через один столбец
            boolean[] cur = ScopeScreen.mask();
            boolean[] prev = ScopeScreen.mask();
            prev[ScopeScreen.idx(2, 4)] = true;
            prev[ScopeScreen.idx(3, 12)] = true;
            ScopeScreen.afterglow(cur, prev, 2);
            check("люминофор гаснет, а не исчезает (столбец 2 остался, 3 — нет)",
                    cur[ScopeScreen.idx(2, 4)] && !cur[ScopeScreen.idx(3, 12)],
                    "afterglow по чётным столбцам");
        }

        // --- 7. экран по данным: синус виден как синус --------------------------
        {
            ScopeTrace tr = new ScopeTrace();
            for (int i = 0; i < ScopeTrace.SCREEN_SAMPLES; i++) {
                tr.sample(sine(i * dt, 1.0, 6.0, 0.0));
            }
            double[] v = new double[ScopeTrace.SCREEN_SAMPLES];
            long start = tr.size() - v.length;
            for (int i = 0; i < v.length; i++) {
                v[i] = tr.at(start + i);
            }
            boolean[] px = ScopeScreen.mask();
            double[] sc = ScopeScreen.autoscale(0, 12, 1.3);
            ScopeScreen.trace(v, sc[0], sc[1], px);
            int lit = ScopeScreen.lit(px);
            ScopeTrace.Stats st2 = tr.measure(dt, ScopeTrace.SCREEN_SAMPLES);
            check("синус занимает экран, но не закрашивает его",
                    lit > ScopeScreen.W && lit < 4 * ScopeScreen.W,
                    "lit=" + lit + " при " + ScopeScreen.W + " столбцах и высоте " + ScopeScreen.H);
            check("выравнивание по целому числу периодов: f ровно 1 Гц",
                    Math.abs(st2.freqHz() - 1.0) < 1e-9 && st2.edges() >= 2
                            && st2.len() % 20 == 0,
                    "f=" + st2.freqHz() + ", фронтов=" + st2.edges()
                            + ", измерено отсчётов=" + st2.len() + " (окно экрана 40)");
            int rowsUsed = 0;
            for (int y = 0; y < ScopeScreen.H; y++) {
                for (int x = 0; x < ScopeScreen.W; x++) {
                    if (px[ScopeScreen.idx(x, y)]) {
                        rowsUsed++;
                        break;
                    }
                }
            }
            check("трасса растянута по всей высоте шкалы",
                    rowsUsed > ScopeScreen.H * 0.7,
                    "затронуто строк: " + rowsUsed + " из " + ScopeScreen.H);
        }

        System.out.println(fails == 0 ? "TestScope: ALL OK (" + checks + " проверок)"
                : "TestScope: ПРОВАЛЕНО " + fails + " из " + checks);
        if (fails > 0) {
            System.exit(1);
        }
    }
}
