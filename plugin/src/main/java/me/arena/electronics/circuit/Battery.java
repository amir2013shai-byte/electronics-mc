package me.arena.electronics.circuit;

import java.util.Locale;

/**
 * Свинцовый аккумулятор: модель, которая копит, а не показывает.
 *
 * <p>Источник ({@link Kind#SOURCE}) отдаёт своё напряжение вечно. Аккумулятор —
 * нет: у него есть запас, и он меняется от того, что происходит в цепи. Здесь
 * ровно то, что отличает «батарейку- decorator» от аккумулятора:</p>
 * <ol>
 *   <li><b>ЭДС зависит от заряда.</b> Полная банка 12 В — 12.65 В, пустая —
 *       11.85 В: весь разброс {@link #OCV_SPAN} = 0.8 В, то есть 4 %. Поэтому
 *       ни в игре, ни в жизни заряд по вольтметру не определяют — только
 *       кулонами или плотностью электролита.</li>
 *   <li><b>Кулоновский счёт.</b> Заряд — интеграл тока: {@code Δsoc = I·dt /
 *       (3600·C)}. При 7 А·ч и токе 2 А банка сядет за 3.5 ч, и это правда только
 *       для маленького тока — см. пункт 3.</li>
 *   <li><b>Эффект Пейкерта.</b> Отдаваемая ёмкость падает с ростом тока:
 *       {@code t = C_p / I^n}. При 5 С свинцовая банка отдаёт не 7 А·ч, а около
 *       2.2 — «высокий ток съедает ёмкость», из-за чего стартерный аккумулятор
 *       и лампочка на 20 Вт садятся по-разному.</li>
 *   <li><b>КПД заряда.</b> В банку возвращается не всё: {@code charge-efficiency}
 *       (у VRLA ~0.95), остаток уходит в тепло и газование.</li>
 *   <li><b>Разряженный в ноль не «выключается», а деградирует.</b> Ниже
 *       {@link #SULFATION_BELOW} начинается сульфатация пластин: ёмкость теряется
 *       безвозвратно. А севшая банка ещё и растит внутреннее сопротивление —
 *       поэтому «проверка нагрузкой» честнее проверки вольтметром.</li>
 *   <li><b>Нагрев.</b> {@code I²·r + I·η} греет корпус, корпус остывает в среду.
 *       Выкипание (напряжение выше {@link #V_GAS}) добавляет тепла и убивает
 *       пластины — это «закипевший» аккумулятор с характерным вздутым корпусом.</li>
 * </ol>
 *
 * <p><b>Знак тока.</b> Решатель работает с модулями, поэтому банку передают
*   «знаковый» ток вывода 0: положительный — банк <em>отдаёт</em>, отрицательный —
*   принимает. Разряд — это {@code U*I > 0}. Важно: модель <b>не различает</b>
*   «банк стоит навыворот» и «банк стоит как надо» — в одинарной цепи знак
*   {@code c.voltage} зависит от того, к каким узлам попали выводы детали, а не от
*   её полярности, так что любое такое различение было бы обманом. Всё, что модель
*   видит честно: сколько энергии банк принял или отдал, каким током и как нагрелся.
*   Состояния поэтому расшифровываются так: {@code rev} — ток идёт обратно в банк
*   (зарядка), {@code on} — заряд выше предела (банк «кипит»), {@code dim} —
*   разряд, {@code closed} — полон и в покое, {@code burnt} — осыпался. Знаки и
*   пороги сверены прогоном на живом сервере (/elec selftest, строки «аккумулятор»)
*   и офлайн-тестом tools/TestBattery.java.</p>
 *
 * <p>Состояние живёт в {@code aux[]} экземпляра (см. {@link CompInstance#aux}), а не
 * в отдельной карте: его не надо чистить при разборке схемы и оно переживает рестарт
 * сервера, потому что {@code Persistence} пишет {@code aux}. Класс намеренно не знает
 * про Bukkit — вся физика проверяется офлайн: {@code tools/TestBattery.java}.</p>
 */
public final class Battery {

    /**
     * Индексы aux[]: разряженность, безвозвратная потеря ёмкости, счётчик, нагрев.
     *
     * <p>Хранится именно {@code 1 - soc}, а не soc: у freshly поставленной детали
     * весь aux нулевой, то есть банк полон — инициализация не нужна. Побочный
     * плюс: реестр ({@code Persistence}) пишет aux только когда он ненулевой, а у
     * девяноста процентов банок в проекте там и reste пусто.</p>
     */
    public static final int DISCHARGED = 0;
    public static final int DEGRADE = 1;
    public static final int AMPERE_HOURS = 2;
    public static final int TEMP = 3;

    /** Вольт между полной и пустой банкой. Весь «датчик заряда по напряжению» — 0.8 В. */
    public static final double OCV_SPAN = 0.80;
    /** Напряжение газа: выше — электролиз, кипение, потеря воды. */
    public static final double V_GAS = 14.40;
    /** Ниже этого SOC пластины сульфатируются, ёмкость теряется. */
    public static final double SULFATION_BELOW = 0.20;
    /** SOC, при котором внутреннее сопротивление начинает расти («банка села»). */
    public static final double V_DEAD_SOC = 0.05;
    /** Во сколько раз вырастает r_int у полностью севшей банки. */
    public static final double DEAD_RESISTANCE_FACTOR = 40.0;
    /** °C сверх окружающей среды, после которых корпус вздувается. */
    public static final double T_SWELL = 55.0;
    /** Потеря ёмкости в час при кипении (доля от номинала). */
    public static final double GASSING_DEGRADE_PER_HOUR = 0.010;
    /** В: перенапряжение пластин. Тепло в банке — это I²r плюс I·η. */
    public static final double OVERPOTENTIAL_V = 0.30;
    /** Дж/°C: теплоёмкость корпуса ~1.5 кг (важна инерция: минуты, не тики). */
    public static final double HEAT_J_PER_C = 400.0;
    /** Вт/°C: отдача корпуса в воздух (равновесие ΔT = P/k: 36 Вт -> +40 °C). */
    public static final double COOL_W_PER_C = 0.9;
    /** Ток меньше этого — покой: ни заряда, ни разряда, ни нагрева. */
    public static final double I_IDLE = 0.05;
    /**
     * Максимальный ток заряда, выраженный в C: 1C для свинцового банка, то есть
     * для 7 А·ч это 7 А. Выше — банк «кипит»: электролиз идёт быстрее, чем
     * успевает восстановиться пластина, ёмкость теряется безвозвратно.
     */
    public static final double MAX_CHARGE_C = 1.0;

    /** Параметры одной модели аккумулятора: реестр (Def) + config.yml. */
    public static final class Params {

        /** ЭДС полностью заряженной банки в покое, В (обычно d.voltage). */
        public final double emf;
        /** Внутреннее сопротивление, Ом (d.rInt): у 12 В/7 А·ч ~20 мОм. */
        public final double rInt;
        /** Номинальная ёмкость при 20-часовом разряде, А·ч. */
        public final double capacityAh;
        /** Показатель Пейкерта: 1 = ёмкость не зависит от тока, 1.25 — свинец. */
        public final double peukert;
        /** Доля ампер-часов, возвращаемая в банку при заряде. */
        public final double chargeEff;
        /** Саморазряд, доля ёмкости в час (0.002 = 4.8 %/сутки). */
        public final double selfDischargePerHour;
        /** Сульфатация: доля ёмкости, теряемая за час простоя в ноль. */
        public final double sulfationPerHour;

        public Params(double emf, double rInt, double capacityAh, double peukert,
                      double chargeEff, double selfDischargePerHour, double sulfationPerHour) {
            this.emf = emf;
            this.rInt = Math.max(1e-4, rInt);
            this.capacityAh = Math.max(0.001, capacityAh);
            this.peukert = Math.max(1.0, Math.min(2.0, peukert));
            this.chargeEff = Math.max(0.10, Math.min(1.0, chargeEff));
            this.selfDischargePerHour = Math.max(0.0, selfDischargePerHour);
            this.sulfationPerHour = Math.max(0.0, sulfationPerHour);
        }

        /** ЭДС покоя при данном запасе заряда. */
        public double emfAt(double soc) {
            return emf - OCV_SPAN * (1.0 - clamp01(soc));
        }
    }

    /** Заряженность 0..1 (в aux лежит 1 − soc, см. DISCHARGED). */
    public static double soc(double[] aux) {
        return clamp01(1.0 - aux[DISCHARGED]);
    }

    /** Выставить заряд вручную (для теста, selftest'а и «поверки» банок). */
    public static void setSoc(double[] aux, double soc) {
        aux[DISCHARGED] = 1.0 - clamp01(soc);
    }

    /** ЭДС покоя банки в текущем заряде. */
    public static double emf(Params p, double[] aux) {
        return p.emfAt(soc(aux));
    }

    public static double degrade(double[] aux) {
        return Math.min(0.95, Math.max(0.0, aux[DEGRADE]));
    }

    /** Ёмкость, которая осталась в банке с учётом сульфатации, А·ч. */
    public static double capacityLeft(Params p, double[] aux) {
        return p.capacityAh * (1.0 - degrade(aux)) * soc(aux);
    }

    /** Внутреннее сопротивление с учётом «сел — impedance вырос». */
    public static double resistance(Params p, double[] aux) {
        double s = soc(aux);
        if (s >= V_DEAD_SOC) {
            return p.rInt;
        }
        return p.rInt * (1.0 + (DEAD_RESISTANCE_FACTOR - 1.0) * (V_DEAD_SOC - s) / V_DEAD_SOC);
    }

    /**
     * Ёмкость, которую банк отдаст при токе {@code amps}: номинал, поделённый на
     * Пейкерта. При токе 20-часового разряда возвращает ровно номинал.
     */
    public static double availableAh(Params p, double[] aux, double amps) {
        double nom = p.capacityAh * (1.0 - degrade(aux));
        if (amps <= 1e-9) {
            return nom;
        }
        double i20 = nom / 20.0;
        double peukertConstant = Math.pow(i20, p.peukert) * 20.0;
        double avail = peukertConstant / Math.pow(amps, p.peukert - 1.0);
        return Math.max(nom * 0.05, Math.min(nom, avail));
    }

    /** «Новая банка»: полна, без деградации, холодная. */
    public static void reset(Params p, double[] aux) {
        aux[DISCHARGED] = 0.0;
        aux[DEGRADE] = 0.0;
        aux[AMPERE_HOURS] = 0.0;
        aux[TEMP] = 0.0;
    }

    /**
     * Шаг модели: пересчитывает заряд, деградацию и нагрев в {@code aux}.
     *
     * @param voltage  напряжение на выводах детали, В (для источника — почти ЭДС)
     * @param current  ток, отдаваемый банком в вывод 0, А: положительный — банк
     *                 разряжается, отрицательный — его заряжают
     * @param dt       шаг решателя, с
     * @return мощность на клеммах, Вт (по модулю), для диагностики
     */
    public static double step(Params p, double[] aux, double voltage, double current, double dt) {
        if (dt <= 0.0) {
            return 0.0;
        }
        double soc = soc(aux);
        double i = current;
        double pDeliver = voltage * i;                 // >0 — отдаёт (разряд), <0 — заряд
        double amps = Math.abs(i);
        boolean idle = amps <= I_IDLE;
        boolean charging = pDeliver < 0.0 && !idle;
        double nom = p.capacityAh * (1.0 - degrade(aux));   // А·ч, сколько в банке есть
        double hours = dt / 3600.0;
        boolean boil = boiling(p, amps, voltage, charging);

        // Приращение заряда. Заряд делим на номинал, разряд — на «ёмкость при этом
        // токе»: именно так Пейкерт превращает высокий ток в быстро тающие проценты.
        double dsoc = 0.0;
        if (!idle) {
            dsoc = charging ? amps * hours * p.chargeEff / nom
                    : -amps * hours / availableAh(p, aux, amps);
        }
        double ns = clamp01(soc + dsoc - p.selfDischargePerHour * hours);
        // саморазряд идёт даже в оборванной цепи; счётчик — до всяких КПД:
        // сколько ампер-часов прошло через клеммы
        aux[DISCHARGED] = 1.0 - ns;
        aux[AMPERE_HOURS] += idle ? 0.0 : (charging ? -amps * hours : amps * hours);

        // сульфатация: стоявший разряженным аккумулятор теряет ёмкость навсегда
        double s2 = ns;
        if (s2 < SULFATION_BELOW) {
            aux[DEGRADE] = Math.min(0.95, aux[DEGRADE]
                    + p.sulfationPerHour * (SULFATION_BELOW - s2) / SULFATION_BELOW * hours);
        }
        // тепло: I²r + перенапряжение + газование; остывание экспоненциальное
        // (aux[TEMP] — превышение над средой, постоянная времени C/k ≈ 7,4 мин)
        double heat = i * i * p.rInt + OVERPOTENTIAL_V * amps;
        if (boil) {
            // кижение: лишняя энергия уходит в электролиз воды и в тепло
            heat += amps * 1.2;
            aux[DEGRADE] = Math.min(0.95, aux[DEGRADE]
                    + GASSING_DEGRADE_PER_HOUR * (1.0 + amps / Math.max(0.1, p.capacityAh)) * hours);
        }
        double dTemp = (heat - COOL_W_PER_C * aux[TEMP]) * dt / HEAT_J_PER_C;
        aux[TEMP] = Math.max(0.0, aux[TEMP] + dTemp);
        if (aux[TEMP] > T_SWELL) {
            aux[DEGRADE] = Math.min(0.95, aux[DEGRADE] + 0.02 * hours);
        }
        return Math.abs(voltage * i);
    }

    /** Заряд «кипит»: напряжение выше газа или ток выше 1C. */
    public static boolean boiling(Params p, double amps, double voltage, boolean charging) {
        return charging && (Math.abs(voltage) > V_GAS || amps > p.capacityAh * MAX_CHARGE_C);
    }

    /** Визуальное состояние детали: 6 штук, все задействованы. */
    public static int stateOf(Params p, double[] aux, double voltage, double current) {
        if (degrade(aux) >= 0.5 || aux[TEMP] >= T_SWELL) {
            return CompStates.BURNT;              // вздулся / осыпался
        }
        double soc = soc(aux);
        double amps = Math.abs(current);
        boolean charging = voltage * current < 0.0 && amps > I_IDLE;
        if (charging && boiling(p, amps, voltage, true)) {
            return CompStates.ON;                 // заряд выше предела: банк «кипит»
        }
        if (amps <= I_IDLE) {
            return soc >= 0.98 ? CompStates.CLOSED : CompStates.OFF;   // полна / покой
        }
        // «rev» = ток идёт обратно, в банк: это зарядка, а не поломка
        return charging ? CompStates.REVERSE : CompStates.DIM;
    }

    /** Подпись на TextDisplay: чем банка занята, видно издалека. */
    public static String label(double[] aux, double voltage, double current) {
        double soc = soc(aux);
        double amps = Math.abs(current);
        String flow = amps <= I_IDLE ? "покой" : (voltage * current > 0 ? "разряд" : "заряд");
        return String.format(Locale.ROOT, "%.2f В · %.0f %% · %s %.1f А",
                Math.abs(voltage), soc * 100.0, flow, amps);
    }

    /** Почему банка плохо выглядит (пустая строка = всё в порядке). */
    public static String faultOf(Params p, double[] aux, double voltage, double current) {
        double soc = soc(aux);
        if (aux[TEMP] >= T_SWELL) {
            return "перегрет (" + (int) aux[TEMP] + " °C) — корпус вздулся, ёмкость потеряна";
        }
        if (degrade(aux) >= 0.5) {
            return "сульфатация: потеряно " + (int) (degrade(aux) * 100) + " % ёмкости";
        }
        double pDeliver = voltage * current;
        boolean charging = pDeliver < 0.0 && Math.abs(current) > I_IDLE;
        if (charging && Math.abs(voltage) > V_GAS) {
            return "выкипание: заряд при " + String.format(Locale.ROOT, "%.1f", Math.abs(voltage))
                    + " В — выше " + String.format(Locale.ROOT, "%.1f", V_GAS) + " В, вода уходит";
        }
        if (charging && Math.abs(current) > p.capacityAh * MAX_CHARGE_C) {
            return "заряд выше предела: " + String.format(Locale.ROOT, "%.1f", Math.abs(current))
                    + " А при допустимых " + String.format(Locale.ROOT, "%.1f",
                            p.capacityAh * MAX_CHARGE_C) + " А (1C) — банк кипит и теряет ёмкость";
        }
        if (soc <= 0.001 && Math.abs(current) > I_IDLE && pDeliver > 0.0) {
            return "разряжен в ноль — внутреннее сопротивление выросло в "
                    + (int) DEAD_RESISTANCE_FACTOR + " раз";
        }
        return "";
    }

    private static double clamp01(double v) {
        return v < 0.0 ? 0.0 : (v > 1.0 ? 1.0 : v);
    }

    private Battery() {
    }
}
