package me.arena.electronics.circuit;

import java.util.Locale;

import me.arena.electronics.util.Num;

/**
 * Фотоэлемент: источник <em>тока</em>, у которого спрашивают, сколько света.
 *
 * <p>Источник ({@link me.arena.electronics.registry.Kind#SOURCE}) отдаёт своё
 * напряжение всегда. Панель — нет: она отдаёт ток, и ток этот равен световому
 * потоку. Отсюда вся модель, по пунктам:</p>
 * <ol>
 *   <li><b>Ток короткого замыкания.</b> {@code Isc(f) = Isc_ном · f}, и он же
 *       потолок: сколько бы ни просил потребитель, больше {@code Isc} панель не
 *       отдаст. Это и есть причина, по которой «панель на 39 Вт» не тянет
 *       чайник, даже если на клеммах ещё есть вольты.</li>
 *   <li><b>Холостой ход — логарифмом.</b> {@code Uxx(f) = Uxx_ном · ln(1 + f·(e−1))}:
 *       при f = 1 точно {@code Uxx_ном}, при f = 0.5 уже 0.84 номинала, при f = 0
 *       — ноль. Реальная вольт-амперная кривая именно такая: напряжение растёт
 *       быстро и упирается, а ток линейен по свету.</li>
 *   <li><b>Поток = 0.88 · sin(высота солнца) + 0.12 · (день).</b> Прямая
 *       составляющая зависит от угла, рассеянная — от самого факта дня; в пасмурный
 *       полдень панель даёт не ноль, а ~12 %, в дождь — ещё ×{@link #STORM_FACTOR}.
 *       Ночью поток ровно 0, и ни какой-нибудь «минимум для игры».</li>
 *   <li><b>Небо — геометрией, а не светом блока.</b> Над панелью смотрим 16
 *       блоков вверх: сплошной блок обрывает поток (крыша, перекрытие, другой
 *       блок), стекло/лёд/ковёр проходят почти свободно (×0.95), листва ×0.75,
 *       вода ×0.55, прочая полупрозрачность ×0.85 — см. {@link #attenuation}.
 *       Свет в чанке ({@code getLightFromSky}) для этого не годится: он
 *       пересчитывается на тик позже и зависит от соседних блоков, а не от
 *       того, что прямо над стеклом.</li>
 *   <li><b>В темноте панель — это резистор утечки.</b> {@code resistance} из
 *       паспорта (30 Ом у панели, 25 Ом у элемента) стоит между клеммами всегда,
 *       поэтому банк, подключённый к панели без блокирующего ключа, разряжается
 *       током {@code U/r} = 0.42 А ночью — 10 А·ч за сутки. Ровно ради этого
 *       между банкой и панелью ставят {@link ChargeCtrl}.</li>
 * </ol>
 *
 * <p>Класс намеренно не знает про Bukkit: {@link #irradiance}, {@link #openCircuit}
 * и {@link #shortCircuit} — чистая арифметика, и вся физика проверяется офлайн
 * ({@code tools/TestPower.java}). Мир здесь только поставляет два числа: часы
 * {@code dayTime} и флаг погоды.</p>
 */
public final class Solar {

    /** Доля рассеянного света: она есть весь день, даже когда солнца не видно. */
    public static final double DIFFUSE = 0.12;
    /** Доля прямой составляющей: она следует за высотой солнца. */
    public static final double DIRECT_SHARE = 0.88;
    /** Дождь/снег: поток умножается на это. */
    public static final double STORM_FACTOR = 0.30;
    /** Тиков в сутках Minecraft. */
    public static final long DAY_TICKS = 24000L;
    /** Тик захода солнца: с него поток строго нулевой. */
    public static final long SUNSET_TICKS = 12000L;

    private Solar() {
    }

    /** Синус высоты солнца: 0 на закате и рассвете, 1 в зените, 0 ночью. */
    public static double sunElevation(long dayTime) {
        long t = Math.floorMod(dayTime, DAY_TICKS);
        if (t >= SUNSET_TICKS) {
            return 0.0;
        }
        return Math.max(0.0, Math.sin(Math.PI * t / SUNSET_TICKS));
    }

    /** День по часам мира (светлая половина суток). */
    public static boolean isDaytime(long dayTime) {
        return Math.floorMod(dayTime, DAY_TICKS) < SUNSET_TICKS;
    }

    /**
     * Световой поток от 0 до 1: высота солнца + рассеянная составляющая, поправка
     * на погоду и на то, какая часть неба реально видна.
     *
     * @param skyLight доля видимого неба 0..1 (см. {@link #attenuation}); 0 — потолок
     */
    public static double irradiance(long dayTime, boolean storm, double skyLight) {
        double e = sunElevation(dayTime);
        double sky = Math.max(0.0, Math.min(1.0, skyLight));
        if (sky <= 0.0) {
            return 0.0;
        }
        double f = e * DIRECT_SHARE + (isDaytime(dayTime) ? DIFFUSE : 0.0);
        if (storm) {
            f *= STORM_FACTOR;
        }
        return Math.max(0.0, Math.min(1.0, f * sky));
    }

    /** Напряжение холостого хода при потоке f: логарифм, ровно номинал при f = 1. */
    public static double openCircuit(double uocNom, double f) {
        if (f <= 0.0 || uocNom <= 0.0) {
            return 0.0;
        }
        return uocNom * Math.log1p(f * (Math.E - 1.0));
    }

    /** Ток короткого замыкания при потоке f; больше этого панель не отдаст. */
    public static double shortCircuit(double iscNom, double f) {
        if (iscNom <= 0.0) {
            return 0.0;
        }
        return iscNom * Math.max(0.0, Math.min(1.0, f));
    }

    /** Сопротивление утечки в темноте; 0 в паспорте = «утечки нет» = 1 МОм. */
    public static double darkResistance(double rDark) {
        return rDark > 1.0 ? rDark : 1e6;
    }

    /**
     * Сколько света пропускает блок над панельей — по имени материала, чтобы
     * класс не зависел от Bukkit. Порядок проверок важен: «AIR» ищется первым,
     * иначе {@code CAVE_AIR} попал бы в общую корзину.
     */
    public static double attenuation(String materialName) {
        String n = materialName == null ? "" : materialName;
        if (n.isEmpty() || n.contains("AIR")) {
            return 1.0;
        }
        if (n.contains("GLASS") || n.contains("ICE") || n.contains("CARPET")) {
            return 0.95;
        }
        if (n.contains("WATER")) {
            return 0.55;
        }
        if (n.contains("LEAVES")) {
            return 0.75;
        }
        return 0.85;
    }

    /** Подпись на панели: «62% · 20.1 В»; ночью — слово, а не нули. */
    public static String label(double f, double uOc) {
        if (f <= 5e-4) {
            return "ночь";
        }
        return String.format(Locale.ROOT, "%d%% · %s", (int) Math.round(f * 100), Num.volt(uOc));
    }
}
