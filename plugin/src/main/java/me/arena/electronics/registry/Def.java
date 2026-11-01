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
    /** Ёмкость аккумулятора при 20-часовом разряде, А·ч (Kind.BATTERY). */
    public double capacityAh = 0.0;
    /** Показатель Пейкерта: 1 — ёмкость не зависит от тока, 1.25 — свинец. */
    public double peukert = 1.25;
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
    /** Индуктивность, Гн (INDUCTOR / ELECTROMAGNET / RELAY). */
    public double inductance = 0.0;
    /** мТл на ампер витка (электромагнит). */
    public double fieldPerAmp = 0.0;
    /** Собственное поле постоянного магнита, мТл. */
    public double fieldMt = 0.0;
    /** Ток притяжения якоря реле, мА. */
    public double operateMa = 0.0;
    /** Ток отпускания реле (гистерезис), мА. */
    public double releaseMa = 0.0;
    /** Порог поля геркона, мТл. */
    public double operateMt = 0.0;
    /** Сопротивление зависит от освещённости (фоторезистор). */
    public boolean lightDependent = false;
    /** Бета NTC-термистора, К. */
    public double bConstant = 0.0;
    /** Градусы нагрева на ватт рассеивания (саморазогрев термистора). */
    public double selfHeat = 0.0;
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
        return kind == Kind.LED || kind == Kind.LAMP || kind == Kind.BUZZER || kind == Kind.PROBE
                || kind == Kind.ELECTROMAGNET || kind == Kind.RELAY || kind == Kind.SCOPE;
    }

    /** Переменное напряжение (у источника с ac_hz каждый шаг — новый эмис). */
    public boolean isAc() {
        return acHz > 0;
    }

    public boolean isTimeDependent() {
        // KINETIC: у привода есть обороты и инерция — состояние живёт между тиками,
        // значит остров с деталями привода обязан тикать, даже когда в нём нет конденсаторов
        return kind == Kind.CAPACITOR || kind == Kind.GENERATOR || kind == Kind.MOTOR || kind == Kind.LAMP
                || kind == Kind.KINETIC
                || kind == Kind.INDUCTOR || kind == Kind.ELECTROMAGNET || kind == Kind.RELAY
                // тепловая инерция PTC и стабилизатора, накопленный I²t предохранителя,
                // поток в трансформаторе и деградация варистора — это тоже «память
                // между шагами»: без неё переходный процесс не увидеть
                || kind == Kind.PTC || kind == Kind.REGULATOR || kind == Kind.TRANSFORMER
                || kind == Kind.FUSE || kind == Kind.VARISTOR
                // синусоидальный источник — тоже «память»: фаза пересчитывается на
                // каждом шаге, а значит остров с ним обязан тикать и без конденсаторов
                || (kind == Kind.SOURCE && acHz > 0);
    }

    /** Катушка: ток в ней — это «память», без неё шаг по времени бессмыслен. */
    public boolean isInductive() {
        return inductance > 0 && (kind == Kind.INDUCTOR || kind == Kind.ELECTROMAGNET || kind == Kind.RELAY
                || kind == Kind.TRANSFORMER);
    }

    /** Максимум визуальных состояний, которые есть у компонента. */
    /** Плавкий предохранитель: I²t (А²·с), при котором плавится вставка. */
    public double blowI2t = 0.0;
    /** PTC: ток удержания и ток срабатывания, А. */
    public double holdCurrent = 0.0;
    public double tripCurrent = 0.0;
    /** PTC/стабилизатор: температура срабатывания (°C выше среды), К/Вт и Дж/К. */
    public double tripTemp = 90.0;
    public double thermalK = 8.0;
    public double heatJ = 1.5;
    /** Варистор: порог (В, при котором через деталь пойдёт 1 мА), крутизна α, энергия, Дж. */
    public double vClamp = 0.0;
    public double nlAlpha = 8.0;
    public double energyJ = 0.0;
    /** Стабилизатор: dropout, предел тока, собственное потребление. */
    public double dropout = 2.0;
    public double iLimit = 1.0;
    public double iQuiescent = 0.005;
    /** Подстроечный стабилизатор (LM317): пределы выходного напряжения, В. */
    public double vOutMin = 0.0;
    public double vOutMax = 0.0;
    /** Синусоидальный источник: частота, Гц (0 = постоянка). */
    public double acHz = 0.0;
    /** Трансформатор: коэффициент витков и коэффициент связи. */
    public double ratio = 1.0;
    public double coupling = 0.98;
    /**
     * Привод (KINETIC) и моторы: обороты холостого хода, момент инерции кг·м²,
     * масса кг и рабочий радиус м. Для KINETIC ratio — передаточное отношение,
     * coupling — КПД пары (у вала это потери подшипников).
     */
    public double rpm = 0.0;
    public double inertia = 0.0;
    public double mass = 0.0;
    public double radius = 0.15;

    /** Роль детали в кинематической цепочке — берётся из «shape». */
    public me.arena.electronics.kinetics.Kinetics.Role kineticRole() {
        if (kind == Kind.MOVER) {
            // Движитель — деталь вала, но не «вал»: у него своя роль и свой
            // вклад в момент, и редуктор он не множит.
            return me.arena.electronics.kinetics.Kinetics.Role.MOVER;
        }
        return kind == Kind.KINETIC ? me.arena.electronics.kinetics.Kinetics.Role.of(shape) : null;
    }

    /** Машина, которую крутят, чтобы она давала ток (в отличие от мотора). */
    public boolean isDynamo() {
        return kind == Kind.DYNAMO;
    }

    /** Ручную рукоять надо крутить; колесо работает само, пока мокро. */
    public boolean isHandCrank() {
        return kind == Kind.MOVER && "crank".equals(shape);
    }

    /** Мотор с обмоткой: k = U₀/ω₀ — по этой константе решатель и «крутит» вал. */
    public boolean drivesShaft() {
        return rpm > 0 && (kind == Kind.MOTOR || kind == Kind.GENERATOR);
    }

    /** Целевое напряжение стабилизатора с учётом подстройки (для LM317). */
    public double regulatorTarget(double tuneFraction) {
        if (vOutMax > vOutMin && vOutMin > 0) {
            return vOutMin + Math.max(0.0, Math.min(1.0, tuneFraction)) * (vOutMax - vOutMin);
        }
        return voltage;
    }

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
            case RESISTOR, AMMETER, VOLTMETER -> "R=" + trim(resistance) + " Ом";
            case KINETIC -> switch (shape == null ? "" : shape) {
                case "gear" -> "редуктор 1:" + trim(ratio) + ": момент ×" + trim(ratio * coupling)
                        + ", обороты ÷" + trim(ratio);
                case "flywheel" -> "маховик J=" + trim(inertia) + " кг·м², " + trim(mass) + " кг";
                case "wheel" -> "колесо r=" + trim(radius) + " м, редуктор хода 1:" + trim(ratio);
                case "saw" -> "пила: режет блок перед собой, дерево валит целиком";
                case "drill" -> "бур: шпуры по породе, руду отдаёт куском";
                default -> "вал: J=" + trim(inertia) + " кг·м², потери "
                        + (int) Math.round(Math.max(0.0, 1.0 - coupling) * 100) + "%";
            };
            case SOURCE, GENERATOR, SOLAR -> (acHz > 0 ? "U=" + trim(voltage) + " В, синус "
                    + trim(acHz) + " Гц" : "U=" + trim(voltage) + " В");
            case CAPACITOR -> "C=" + trim(capacitance) + " Ф";
            case BATTERY -> "U=" + trim(voltage) + " В, ёмкость " + trim(capacityAh)
                    + " А·ч (Пейкерт " + trim(peukert) + ")";
            case DYNAMO -> "U=" + trim(ratedVoltage) + " В на " + trim(rpm) + " об/мин, обмотка "
                    + trim(winding) + " Ом, долгий ток " + trim(maxCurrent) + " А";
            case MOVER -> "до " + trim(rpm) + " об/мин, " + trim(ratedPower) + " Вт, момент "
                    + trim(me.arena.electronics.kinetics.Movers.stallTorque(ratedPower, rpm)) + " Н·м на стопе";
            case SCOPE -> "вход " + trim(internalResistance / 1e6) + " МОм, экран 40×16, "
                    + "10 делений по горизонтали";
            case MOTOR -> "U=" + trim(ratedVoltage) + " В, R обм. " + trim(winding) + " Ом"
                    + (rpm > 0 ? ", " + (int) rpm + " об/мин ХХ" : "");
            case INDUCTOR -> "L=" + (inductance >= 1 ? trim(inductance) + " Гн"
                    : trim(inductance * 1000) + " мГн") + ", R=" + trim(resistance) + " Ом";
            case ELECTROMAGNET -> "R=" + trim(resistance) + " Ом, L=" + trim(inductance * 1000)
                    + " мГн, " + trim(fieldPerAmp) + " мТл/А";
            case RELAY -> "катушка " + trim(resistance) + " Ом; втягивание " + trim(operateMa)
                    + " мА, отпускание " + trim(releaseMa) + " мА";
            case REED -> "замыкается от поля " + trim(operateMt) + " мТл";
            case PTC -> "удержание " + trim(holdCurrent * 1000) + " мА, срабатывание "
                    + trim(tripCurrent * 1000) + " мА, R холодного " + trim(resistance) + " Ом";
            case VARISTOR -> "порог " + trim(vClamp) + " В, α=" + trim(nlAlpha)
                    + ", энергия " + trim(energyJ) + " Дж";
            case BRIDGE -> "4 диода внутри: ~" + trim(2 * (vForward > 0 ? vForward : 0.55))
                    + " В на мосту, " + trim(maxCurrent) + " А / " + trim(vReverseMax) + " В";
            case REGULATOR -> (vOutMax > vOutMin && vOutMin > 0
                    ? trim(vOutMin) + "–" + trim(vOutMax) + " В (подстроечный)"
                    : "Uвых " + trim(voltage) + " В") + ", dropout " + trim(dropout)
                    + " В, предел " + trim(iLimit) + " А, " + trim(iQuiescent * 1000) + " мА на себя";
            case TRANSFORMER -> "Кт = 1:" + trim(ratio) + ", L перв. " + trim(inductance * 1000)
                    + " мГн, связь k=" + trim(coupling) + ", " + trim(ratedPower) + " ВА";
            case FUSE -> maxCurrent > 0 ? "на " + (maxCurrent >= 1 ? trim(maxCurrent) + " А"
                    : trim(maxCurrent * 1000) + " мА") + ", вставка плавится при I²t=" + trim(blowI2t)
                    : "R=" + trim(resistance) + " Ом";
            case MAGNET -> trim(fieldMt) + " мТл у полюса";
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
