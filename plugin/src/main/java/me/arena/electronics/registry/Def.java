package me.arena.electronics.registry;

import java.util.Locale;

/**
 * Описание одного типа компонента. Загружается из {@code components.json}
 * (тот же файл, что и для генератора ресурспака) — поэтому реестр синхронен:
 * id/модели/индексы состояний не разъезжаются.
 */
public final class Def {

    /** Индекс строки в components.json (0..N-1). */
    public int index;
    /** Ключ (namespace:component). */
    public String key = "";
    public String id = "";
    public String nameRu = "";
    public String nameEn = "";
    /** Тип поведения в симуляторе. */
    public Kind kind = Kind.EMPTY;
    public String shape = "box";
    /** Логика для GATE. */
    public String logic = "";

    // --- электрика ---
    /** Сопротивление, Ом (RESISTOR/FUSE/AMMETER/LAMP). */
    public double resistance = 0.0;
    /** Внутреннее сопротивление источника, Ом. */
    public double rInt = 1.0;
    /** ЭДС, В. */
    public double voltage = 0.0;
    /** Порог светодиодов/диодов, В. */
    public double vForward = 0.0;
    /** Падение напряжения, В. */
    public double vDrop = 0.0;
    /** Максимально обратное напряжение, В. */
    public double vReverseMax = 0.0;
    /** Номинальная мощность, Вт. */
    public double ratedPower = 0.0;
    public double ratedVoltage = 0.0;
    public double maxCurrent = 0.0;
    public double minCurrent = 0.0;
    /** Ёмкость, Ф. */
    public double capacitance = 0.0;
    /** Коэффициент усиления. */
    public double beta = 0.0;
    /** Сопротивление спирали лампы в нагретом состоянии. */
    public double resistanceHot = 0.0;
    public double coldFactor = 1.0;
    /** Внутреннее сопротивление прибора. */
    public double internalResistance = 0.0;
    /** Для мотора: доля противо-ЭДС от напряжения на якоре. */
    public double backEmfPerVolt = 0.0;
    public double winding = 0.0;
    /** Может ли компонент быть повернут (4 ориентации). */
    public boolean orientable = true;
    /** Ставится «стопкой» на другой компонент (резистор/конденсатор на макетке). */
    public boolean stack = false;
    /** Шина: склеивает все узлы вокруг себя (провод, шина земли/питания). */
    public boolean hub = false;
    /** Трёхполюсный переключатель. */
    public boolean spdt = false;
    /** Полярный компонент (электролит). */
    public boolean polar = false;
    /** Подстроечник/блок питания: значение меняется кручением. */
    public boolean tunable = false;
    /** Свечение (0..15) при полном ходе. */
    public int glow = 0;
    public int[] glowColor = {255, 255, 255};
    /** Количество визуальных состояний (off / on / dim / burnt / rev / closed). */
    public int states = 1;
    /** Число проводимых «ног» для симуляции: 2 или 3. */
    public int pins = 2;

    // --- ресурсы ресурспака (для диагностики) ---
    public String modelBase = "";
    public String iconTexture = "";

    public boolean hasGlow() {
        return kind == Kind.LED || kind == Kind.LAMP || kind == Kind.BUZZER || kind == Kind.PROBE;
    }

    public boolean isTimeDependent() {
        return kind == Kind.CAPACITOR || kind == Kind.GENERATOR || kind == Kind.MOTOR || kind == Kind.LAMP;
    }

    /** Максимум визуальных состояний, которые есть у компонента. */
    public static final int MAX_STATE_SLOTS = 6;

    /**
     * Индекс состояния модели (0..{@link Components#STATE_MODELS}-1).
     * Это и есть «реестр состояний»: 45 компонентов x (1..6 состояний) x 6 ориентаций.
     */
    public int stateModel(int stateIndex, int orientationIndex) {
        return baseModelIndex() + clampState(stateIndex) * 6 + clampOrientation(orientationIndex);
    }

    /** Кэш первого индекса среза состояний (считается один раз в Components.recalc). */
    public int cachedBase = -1;

    /** Первый индекс блока состояний этого компонента. */
    public int baseModelIndex() {
        if (cachedBase >= 0) {
            return cachedBase;
        }
        int base = 0;
        for (int i = 0; i < index; i++) {
            base += Components.byIndex(i).modelSlice();
        }
        return base;
    }

    /** Сколько индексов состояния модели занимает компонент (состояния x ориентации). */
    public int modelSlice() {
        return stateCount() * 6;
    }

    public int stateCount() {
        return Math.max(1, Math.min(states, MAX_STATE_SLOTS));
    }

    public int clampState(int stateIndex) {
        return Math.max(0, Math.min(stateIndex, stateCount() - 1));
    }

    public static int clampOrientation(int orientationIndex) {
        return Math.max(0, Math.min(orientationIndex, 5));
    }

    public String displayName(Locale locale) {
        return locale != null && locale.getLanguage().equals("ru") ? nameRu : (nameEn.isEmpty() ? nameRu : nameEn);
    }

    /** Короткая строка номинала для подсказок/лора. */
    public String nominal() {
        return switch (kind) {
            case RESISTOR, FUSE, AMMETER, VOLTMETER -> "R=" + trim(resistance) + " Ом";
            case SOURCE, GENERATOR, SOLAR -> "U=" + trim(voltage) + " В";
            case CAPACITOR -> "C=" + trim(capacitance) + " Ф";
            case LED, DIODE -> "U=" + trim(vForward > 0 ? vForward : vDrop) + " В";
            case TRANSISTOR -> "h21e=" + trim(beta);
            default -> "";
        };
    }

    private static String trim(double v) {
        if (v == Math.rint(v) && Math.abs(v) < 1e9) {
            return String.valueOf((long) v);
        }
        return String.format(Locale.US, "%.4g", v);
    }
}
