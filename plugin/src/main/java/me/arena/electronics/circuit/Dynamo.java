package me.arena.electronics.circuit;

/**
 * Машинный генератор: ЭДС из оборотов вала, тормозной момент — из тока.
 *
 * <p>Это зеркало {@code MOTOR} из {@link Simulation}: там решатель отдаёт ток, а
 * механика превращает его в момент; здесь механика отдаёт обороты, а электрика
 * превращает их в ЭДС. Константа одна и та же, {@code k = U₀/ω₀} — поэтому и
 * проверка честная: механическая мощность на валу равна электрической на клеммах
 * плюс потери на обмотке и щётках, ниоткуда мощность не берётся.
 *
 * <p>Три вещи, которые модель говорит ученику лучше любых слов:
 * <ul>
 *   <li><b>пока не раскрутишь — не будет тока</b>: ЭДС линейна по оборотам;</li>
 *   <li><b>чем больше отдаёшь, тем тяжелее крутить</b>: τ = k·I, и это
 *       единственный «тормоз», который у генератора есть;</li>
 *   <li><b>замкнутые клеммы = электротормоз</b>: ток течёт в машину, и она
 *       становится мотором (состояние {@code REVERSE}).</li>
 * </ul>
 *
 * <p>Знак тока здесь определён физически, а не геометрически: {@code amps > 0} —
 * машина отдаёт, {@code amps < 0} — принимает. В отличие от аккумулятора, тут это
 * можно измерить одним лишь вольтметром с амперметром (сравнить U·I с нулём), так
 * что «обратно» для генератора — честное состояние, а не догадка о разводке.
 */
public final class Dynamo {

    /** Падение на щётках, В. Пока ЭДС меньше него, наружу ничего не выйдет. */
    public static final double BRUSH_V = 0.6;

    /** Теплоёмкость корпуса, Дж/°C (медь + сталь ~0.9 кг). */
    public static final double HEAT_J_PER_C = 260.0;

    /** Отвод в среду, Вт/°C. */
    public static final double COOL_W_PER_C = 0.8;

    /** Нагрев, при котором изоляция обмотки уже «поплыла», °C сверх среды. */
    public static final double SCALD_C = 120.0;

    /** Доля повреждения, после которой деталь считается сгоревшей. */
    public static final double BURNT_AT = 0.5;

    /** Порог «ток есть», А: ниже — лампочка не светится, и состояние «тускло». */
    public static final double LIVE_A = 0.02;

    /** aux[0] — нагрев выше среды, °C. */
    public static final int TEMP = 0;
    /** aux[1] — ампер-часы, отданные в цепь (минус = принято). */
    public static final int AMPERE_HOURS = 1;
    /** aux[2] — энергия на клеммах, Вт·ч. */
    public static final int WATT_HOURS = 2;
    /** aux[3] — повреждение изоляции, 0..1 (≥ {@link #BURNT_AT} = сгорел). */
    public static final int DAMAGE = 3;

    /** Сколько ячеек aux занимает деталь этого вида. */
    public static final int N_AUX = 4;

    private Dynamo() {
    }

    /** Паспорт машины — берётся из {@code components.json}, ничего не додумываем. */
    public static final class Params {

        /** Номинальная ЭДС на паспортных оборотах, В (d.ratedVoltage). */
        public final double ratedVoltage;
        /** Паспортные обороты, об/мин (d.rpm). */
        public final double ratedRpm;
        /** Сопротивление обмотки, Ом (d.winding). */
        public final double rOhm;
        /** Долгий ток, который машина держит, А (d.maxCurrent). */
        public final double ratedA;

        public Params(double ratedVoltage, double ratedRpm, double rOhm, double ratedA) {
            this.ratedVoltage = Math.max(0.5, ratedVoltage);
            this.ratedRpm = Math.max(1.0, ratedRpm);
            this.rOhm = Math.max(0.05, rOhm);
            this.ratedA = ratedA > 0.01 ? ratedA
                    : Math.max(0.05, 200.0 / this.ratedVoltage);
        }

        /** k = U₀/ω₀ — и генераторная, и моторная константа (одна и та же машина). */
        public double kVsPerRad() {
            return ratedVoltage / (ratedRpm * 2.0 * Math.PI / 60.0);
        }
    }

    // ------------------------------------------------------------------ физика

    /** Внутренняя ЭДС, В: строго пропорциональна оборотам (закон Фарадея для машины). */
    public static double emf(Params p, double rpm) {
        double w = Math.max(0.0, rpm) / p.ratedRpm;
        return p.ratedVoltage * Math.min(w, 1.5);      // 1.5 — потолок: разнос щёток
    }

    /**
     * Ток, который машина отдаёт на клеммы с напряжением {@code uTerm}, А.
     * Положительный — отдаёт, отрицательный — принимает (электротормоз).
     */
    public static double current(Params p, double rpm, double uTerm) {
        return (emf(p, rpm) - BRUSH_V - Math.max(0.0, uTerm)) / p.rOhm;
    }

    /** Тормозной момент на валу, Н·м: |k·I|. Именно его снимает механика. */
    public static double torque(Params p, double amps) {
        return p.kVsPerRad() * Math.abs(amps);
    }

    /** Паспорт машины из реестра: U₀ и ω₀ там же, где у мотора. */
    public static Params of(me.arena.electronics.registry.Def d) {
        return new Params(d.ratedVoltage > 0 ? d.ratedVoltage : Math.max(0.5, d.voltage),
                d.rpm, d.winding, d.maxCurrent);
    }

    /**
     * Один шаг длительностью {@code dt} секунд (как у {@code Battery.step}):
     * нагрев, счётчики, повреждение изоляции.
     *
     * @param rpm  текущие обороты вала
     * @param amps ток через машину (плюс — отдаёт)
     * @return losses, Вт — что ушло в тепло за этот шаг
     */
    public static double step(Params p, double[] aux, double rpm, double amps, double dt) {
        if (aux == null || p == null || dt <= 0.0) {
            return 0.0;
        }
        double a = Math.abs(amps);
        double losses = a * a * p.rOhm + a * BRUSH_V;
        double hours = dt / 3600.0;
        // Релаксация к равновесию P/k берётся аналитически (экспонентой), а не
        // явным Эйлером: шаг схемы мелкий, но {@code /elec sim}, отладка и
        // «промотка суток» зовут нас с dt в часы, и там явный шаг скачет через
        // равновесие туда-сюда. Экспонента устойчива при любом dt и на мелком
        // шаге даёт тот же ответ с точностью до O(dt²).
        double tau = HEAT_J_PER_C / COOL_W_PER_C;                  // °C·с/Вт → с
        double relax = 1.0 - Math.exp(-dt / tau);
        aux[TEMP] = Math.max(0.0, aux[TEMP] + (losses / COOL_W_PER_C - aux[TEMP]) * relax);
        // Повреждение растёт только там, где изоляции жарко, и не убывает само:
        // сгоревшая обмотка не «восстанавливается», остыв she.
        if (aux[TEMP] > SCALD_C) {
            aux[DAMAGE] = Math.min(1.0, aux[DAMAGE]
                    + (aux[TEMP] - SCALD_C) / SCALD_C * hours);
        }
        if (amps > 0.0) {
            aux[AMPERE_HOURS] += amps * hours;
            aux[WATT_HOURS] += amps * Math.max(0.0, emf(p, rpm) - BRUSH_V) * hours * 0.98;
        } else if (amps < 0.0) {
            aux[AMPERE_HOURS] += amps * hours;          // минус = приняли обратно
        }
        return losses;
    }

    /** Потери в тепле сейчас, Вт — для подсказки «сколько зря сжигает». */
    public static double lossesW(Params p, double amps) {
        double a = Math.abs(amps);
        return a * a * p.rOhm + a * BRUSH_V;
    }

    /** Мощность на валу, которую машина снимает с механики, Вт. */
    public static double shaftPowerW(Params p, double rpm, double amps) {
        return torque(p, amps) * (Math.max(0.0, rpm) * 2.0 * Math.PI / 60.0);
    }

    // -------------------------------------------------------------- отчётность

    public static double temp(double[] aux) {
        return aux == null ? 0.0 : aux[TEMP];
    }

    public static double damage(double[] aux) {
        return aux == null ? 0.0 : aux[DAMAGE];
    }

    public static double ahOut(double[] aux) {
        return aux == null ? 0.0 : aux[AMPERE_HOURS];
    }

    public static double whOut(double[] aux) {
        return aux == null ? 0.0 : aux[WATT_HOURS];
    }

    /** Состояние: off покой, dim крутится без тока, on отдаёт, reverse принимает, burnt сгорел. */
    public static int stateOf(Params p, double[] aux, double rpm, double amps) {
        if (aux != null && damage(aux) >= BURNT_AT) {
            return CompStates.BURNT;
        }
        if (amps < -LIVE_A) {
            return CompStates.REVERSE;
        }
        if (amps > LIVE_A) {
            return CompStates.ON;
        }
        return rpm > 0.5 ? CompStates.DIM : CompStates.OFF;
    }

    /** На что пожаловаться игроку; пусто — если всё в порядке. */
    public static String faultOf(Params p, double[] aux, double rpm, double amps) {
        if (p == null) {
            return "";
        }
        double a = Math.abs(amps);
        if (aux != null && damage(aux) >= BURNT_AT) {
            return String.format(java.util.Locale.ROOT,
                    "обмотка сгорела: изоляция поплыла на +%.0f °C (потеряно %.0f %% витков)",
                    temp(aux), 100 * damage(aux));
        }
        if (temp(aux) > SCALD_C) {
            return String.format(java.util.Locale.ROOT,
                    "перегрев: +%.0f °C — обмотка горит, снимай нагрузку или дай остыть", temp(aux));
        }
        if (a > p.ratedA) {
            return String.format(java.util.Locale.ROOT,
                    "перегруз: %.1f А при долгом допустимом %.1f А — тормозной момент %.1f Н·м, вал сядет",
                    a, p.ratedA, torque(p, a));
        }
        if (amps < -LIVE_A) {
            return String.format(java.util.Locale.ROOT,
                    "ток идёт в машину (%.1f А): это уже мотор — он тормозит вал", Math.abs(amps));
        }
        if (rpm > 0.5 && a <= LIVE_A && rpm < 0.1 * p.ratedRpm) {
            return String.format(java.util.Locale.ROOT,
                    "мало оборотов: %.0f из %.0f — ЭДС %.2f В, на щётках гаснет %.1f В",
                    rpm, p.ratedRpm, emf(p, rpm), BRUSH_V);
        }
        return "";
    }

    /** Подпись на грани: что покажет вольтметр, обороты и ток. */
    public static String label(Params p, double[] aux, double rpm, double amps) {
        double u = emf(p, rpm) - BRUSH_V - Math.abs(amps) * p.rOhm;
        return String.format(java.util.Locale.ROOT, "%.2f В · %.0f об/мин · %+.2f А · %.0f Вт",
                Math.max(0.0, u), rpm, amps, Math.max(0.0, u) * Math.max(0.0, amps));
    }
}
