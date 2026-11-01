package me.arena.electronics.kinetics;

/**
 * Механические движители — то, что крутит вал само, без единого вольта.
 *
 * <p>До v1.7.0 привод умел только <em>consume</em>: электричество → момент →
 * работа в мире. Здесь — обратная стрелка: вода и мускулы отдают момент, а
 * дальше его принимает {@link Kinetics#step} ровно тем же кодом, что и мотор.
 * Для ученика в этом главный смысл: у «источника вращения» тоже есть
 * паспорт, и паспорт этот — <b>мощность</b>, а не «сколько колёс приделано».
 *
 * <p>Оба движителя описаны одной линейной кривой {@code τ(ω) = τ₀·(1 − ω/ω₀)}:
 * на стопе максимальный момент, на холостом ходу — максимальные обороты, а
 * мощность максимум в середине (τ₀·ω₀/4). Это не упрощение «для красоты»:
 * у человека и у водяного колеса кривая момента именно такая — момент падает,
 * когда скорость растёт. Из неё же честно следует, зачем редуктор: колесо даёт
 * 220 Н·м на 26 об/мин, а генератор хочет 20 Н·м на 1500 об/мин.
 */
public final class Movers {

    // ---------------------------------------------------------------- человек

    /**
     * Устойчивая мускульная мощность человека, Вт. 90 Вт — это то, что
     * нетренированный человек держит minutes, а не рывок: педальный генератор
     * в учебниках дают 100–150 Вт, и то на короткой дистанции.
     */
    public static final double HUMAN_POWER_W = 90.0;

    /** Обороты холостого хода рукояти: быстрее крутить уже неудобно. */
    public static final double CRANK_RPM_IDLE = 90.0;

    /**
     * Момент на рукояти при остановленном вале, Н·м. Взят из условия «максимум
     * мощности линейной кривой = τ₀·ω₀/4 = {@link #HUMAN_POWER_W}», то есть
     * 4·90/(90 об/мин в рад/с) ≈ 38 Н·м — ровно то, что можно удержать двумя
     * руками за рычаг 0.4 м (95 Н ≈ 10 кг силы: тяжело, но возможно).
     */
    public static final double CRANK_TORQUE_STALL =
            4.0 * HUMAN_POWER_W / Kinetics.toOmega(CRANK_RPM_IDLE);

    /**
     * Сколько секунд ручка «помнит» удар по ней. Правая кнопка в Minecraft —
     * событие, а не удержание, поэтому кручение живёт короткое время после
     * клика: редкий клик даёт рывки, частый — непрерывный ход.
     */
    public static final double CRANK_MEMORY_S = 0.55;

    /** Удар рукояти, который добавляет {@link #CRANK_MEMORY_S} сверху. */
    public static double crankBurstMs() {
        return CRANK_MEMORY_S * 1000.0;
    }

    // ------------------------------------------------------------------- вода

    /**
     * Момент от одного блока проточной воды, Н·м. Колесо с четырьмя
     * «мокрыми» соседями даёт 220 Н·м на 26 об/мин — то есть те же ~150 Вт,
     * что и два человека на рукояти, только без перерыва на обед.
     */
    public static final double WATER_TORQUE_PER_BLOCK = 55.0;

    /** Обороты холостого хода колеса: дальше лопатки просто гребут всхолостую. */
    public static final double WATER_RPM_IDLE = 26.0;

    /** Больше четырёх боковых граней у колеса и нет — потолок момента. */
    public static final int WATER_MAX_BLOCKS = 4;

    /** Прибавка к моменту на каждый блок напора сверх первого (вода сверху). */
    public static final double WATER_HEAD_BONUS = 0.25;

    /** Момент, который колесо теряет на трении подшипника и брызг, Н·м. */
    public static final double WATER_IDLE_DRAG_NM = 1.5;

    private Movers() {
    }

    /**
     * Момент на стопе по паспорту детали: из условия, что максимум мощности
     * линейной кривой равен τ₀·ω₀/4, — τ₀ = 4P/ω₀. Реестр хранит ватты
     * ({@code rated_power}) и обороты холостого хода ({@code rpm}); момент никто
     * руками не выписывает, поэтому «рукоять на 5 кВт» из конфига не получится.
     */
    public static double stallTorque(double powerW, double rpmIdle) {
        if (powerW <= 0.0 || rpmIdle <= 0.0) {
            return 0.0;
        }
        return 4.0 * powerW / Math.max(1e-6, Kinetics.toOmega(rpmIdle));
    }

    /**
     * Линейная кривая движителя: τ₀ на стопе, 0 на ω₀, между — по прямой.
     * Отрицательного момента не отдаёт: движитель не тормозит, его тормозит
     * нагрузка (и генератор через {@code Dynamo.torque}).
     */
    public static double torqueAt(double omega, double torqueStall, double rpmIdle) {
        double w0 = Kinetics.toOmega(rpmIdle);
        if (torqueStall <= 0.0 || w0 <= 0.0) {
            return 0.0;
        }
        double t = torqueStall * (1.0 - Math.max(0.0, omega) / w0);
        return t > 0.0 ? t : 0.0;
    }

    /** Ручка: момент есть только пока по ней «бьют». */
    public static double crankTorque(double omega, boolean turning) {
        return turning ? torqueAt(omega, CRANK_TORQUE_STALL, CRANK_RPM_IDLE) : 0.0;
    }

    /**
     * Вода: момент пропорционален числу мокрых соседей (не больше
     * {@link #WATER_MAX_BLOCKS}) и напору, и слегка убывает на трение.
     *
     * @param flowing    сколько блоков проточной воды касается колеса
     * @param headBlocks сколько блоков воды над колесом (стоячая вода напора не даёт)
     */
    public static double waterTorque(double omega, int flowing, int headBlocks) {
        int n = Math.min(WATER_MAX_BLOCKS, Math.max(0, flowing));
        if (n == 0) {
            return 0.0;
        }
        double t0 = WATER_TORQUE_PER_BLOCK * n * (1.0 + WATER_HEAD_BONUS * Math.max(0, headBlocks - 1));
        double t = torqueAt(omega, t0, WATER_RPM_IDLE) - WATER_IDLE_DRAG_NM * Math.min(1.0, omega / Math.max(1.0, Kinetics.toOmega(WATER_RPM_IDLE)));
        return t > 0.0 ? t : 0.0;
    }

    /** Момент от руки плюс момент от воды (оба движителя на одном валу складываются). */
    public static double totalTorque(double omega, boolean cranking, int flowing, int headBlocks) {
        return crankTorque(omega, cranking) + waterTorque(omega, flowing, headBlocks);
    }

    /** Механическая мощность, Вт: τ·ω. Единственный честный ответ на «сколько даёт». */
    public static double powerW(double torqueNm, double omega) {
        return Math.max(0.0, torqueNm) * Math.max(0.0, omega);
    }

    /** Потолок мощности движителя, Вт: середина линейной кривой. */
    public static double maxPowerW(double torqueStall, double rpmIdle) {
        return torqueStall * Kinetics.toOmega(rpmIdle) / 4.0;
    }

    /** Подсказка на грани и в {@code /elec dump}. */
    public static String label(double omega, boolean cranking, int flowing, int headBlocks) {
        double t = totalTorque(omega, cranking, flowing, headBlocks);
        String rpm = String.format(java.util.Locale.ROOT, "%.0f", Kinetics.toRpm(omega));
        if (t <= 0.001) {
            return flowing > 0 || cranking
                    ? rpm + " об/мин · движитель на пределе (" + String.format(java.util.Locale.ROOT,
                    "%.0f", maxPowerW(cranking ? CRANK_TORQUE_STALL : WATER_TORQUE_PER_BLOCK * flowing,
                            cranking ? CRANK_RPM_IDLE : WATER_RPM_IDLE)) + " Вт)"
                    : rpm + " об/мин · никто не крутит";
        }
        return rpm + " об/мин · " + String.format(java.util.Locale.ROOT, "%.1f", t) + " Н·м · "
                + String.format(java.util.Locale.ROOT, "%.0f", powerW(t, omega)) + " Вт";
    }
}
