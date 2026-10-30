package me.arena.electronics.circuit;

import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.registry.Orientation;
import org.bukkit.NamespacedKey;

/**
 * Экземпляр компонента в мире + результаты последнего расчёта.
 *
 * Топология: у 2-полюсника контакты — это блоки, в которые «смотрят» его
 * контакты по направлению {@link #orientation} (плюс верх/низ, если включён
 * режим extra_pins). Два компонента соединены, если их контакты попали в один
 * и тот же узел (net). Провод/шина земли/замкнутый ключ объединяют узлы.
 */
public final class CompInstance {

    public static final NamespacedKey PDC_DEF = new NamespacedKey("electronics", "def");
    public static final NamespacedKey PDC_ORIENT = new NamespacedKey("electronics", "orient");
    public static final NamespacedKey PDC_STATE = new NamespacedKey("electronics", "state");
    public static final NamespacedKey PDC_TUNE = new NamespacedKey("electronics", "tune");
    public static final NamespacedKey PDC_FAULT = new NamespacedKey("electronics", "fault");
    public static final NamespacedKey PDC_LAB = new NamespacedKey("electronics", "lab");
    public static final NamespacedKey PDC_NETLIST = new NamespacedKey("electronics", "netlist");
    public static final NamespacedKey PDC_NOTE = new NamespacedKey("electronics", "note");

    /** 4 стороны + верх/низ. */
    public static final org.bukkit.block.BlockFace[] FACES = {
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST,
            org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN
    };

    public final LocationKey key;
    public Def def;
    public Orientation orientation = Orientation.NORTH;

    /** 0 выкл / 1 тускло / 2 горит / 3 сгорел / 4 обратное включение / 5 замкнут. */
    public int state;
    public String fault = "";
    /** Подстройка (напряжение БП, ползунок) 0..1000. */
    public int tune = 1000;
    /** Имя схемы для SCHEMATIC. */
    public String lab = "";

    // --- результаты расчёта ---
    /** Узел (net) для каждого из контактов FACES[]; null — контакт в никуда. */
    public String[] pinNet = new String[6];
    /** Направление вывода для каждого контакта. */
    public org.bukkit.block.BlockFace[] pinFaces = new org.bukkit.block.BlockFace[6];
    /** Якорный узел: {блок, блок под ним}. */
    public String anchorNet;
    /** Стоим ли на проводящей опоре (провод/шина) — тогда узел общий. */
    public boolean stackedOnConductor;
    /** Узел контакта после union-find (для построения схем). */
    public String[] pinNets = new String[6];
    public double[] pinVoltage = new double[6];
    public double voltage;
    public double current;
    public double power;
    public double brightness;
    /** Для приборов (вольтметр/амперметр/мультиметр). */
    public double reading;
    public double vcPrev;
    /**
     * Локальная память детали между шагами симуляции. На диск не пишется: это
     * переходные величины, после перезагрузки сервера они честно начинаются
     * заново (сгоревшее состояние при этом живёт в {@link #state}/{@link #fault}).
     * aux[0] —integral I²t (предохранитель), перегрев °C (PTC, стабилизатор),
     *          накопленная энергия Дж (варистор), ток первички А (трансформатор);
     * aux[1] — ток вторички (трансформатор), счётчик сработок (варистор/PTC);
     * aux[2..3] — служебное.
     */
    public final double[] aux = new double[4];
    /** Ток через катушку на прошлом шаге (L: I = I_prev + dt/(L+dt*R)*U). */
    public double iPrev;
    /** Суммарное магнитное поле соседей, мТл (для геркона). */
    public double field;
    public double speedPrev;
    /**
     * Привод: как только деталь забирает механизм, обороты считает механика
     * ({@code KineticEngine}), а не формула от напряжения на клеммах. Тогда speedPrev —
     * только передатчик обратной ЭДС в решатель.
     */
    public boolean kinOwned;
    /** Угловая скорость собственного вала, рад/с. */
    public double kinOmega;
    /** Доля момента заклинивания, которую отбирает нагрузка (0..1+). */
    public double kinLoad;
    /**
     * Сглаженный момент на валу, Н·м (его считает {@code KineticEngine}). Нужен
     * именно сглаженным: у безындуксной обмотки мгновенный ток решателя скачет от
     * «короткого» до «нуля» за один тик, а реальный якорь и драйвер таких скачков
     * не видят — у них своя электромагнитная постоянная в десятки миллисекунд.
     */
    public double kinTorque;
    /** Угол модели в мире, градусы: из-за него вал и пила заметно крутятся. */
    public float spin;
    /** Индекс состояния модели ресурспака (0..stateModels). */
    public int modelState;

    // --- микроконтроллер (Kind.MCU) ------------------------------------------

    /**
     * Текст прошивки — единственное, что улетает на диск. После рестарта сервера
     * чип стартует заново, как настоящая Arduino после сброса питания: ноги и
     * счётчик команд не восстанавливаем намеренно.
     */
    public String program = "";
    /** Скомпилированная прошивка (кэш) и текст, из которого она собрана. */
    public transient me.arena.electronics.fw.Firmware.Program firmware;
    public transient String firmwareSource = "";
    /** 0 = вход (hi-Z), 1 = выход, 2 = выход с ШИМ. Индекс = номер ноги. */
    public final int[] mcuDir = new int[6];
    public final int[] mcuLvl = new int[6];
    public final int[] mcuPwm = new int[6];
    /** 10-битное АЦП по ноге и её напряжение (для GUI и для条件的). */
    public final int[] mcuAdc = new int[6];
    public final double[] mcuVolt = new double[6];
    /** Ток, который нога отдаёт/принимает, А (знак: + = отдаёт в цепь). */
    public final double[] mcuPinI = new double[6];
    /** Напряжение питания VCC относительно GND этой же микросхемы. */
    public double mcuVcc;
    /** Причина остановки прошивки (пусто = работает). */
    public String mcuHalt = "";

    /** Сброс ног и счётчика команд, но текст прошивки остаётся (пропало питание). */
    public void resetFirmwareKeepProgram() {
        resetFirmware();
        firmware = null;
        firmwareSource = "";
    }

    /** Отпустить все ноги: прошивка битая или её нет — чип никого не держит. */
    public void clearMcuPins() {
        for (int i = 0; i < mcuDir.length; i++) {
            mcuDir[i] = 0;
            mcuLvl[i] = 0;
            mcuPwm[i] = 0;
        }
    }

    /** Сброс прошивки — кнопка reset на плате. */
    public void resetFirmware() {
        firmware = null;
        firmwareSource = "";
        mcuHalt = "";
        for (int i = 0; i < mcuDir.length; i++) {
            mcuDir[i] = 0;
            mcuLvl[i] = 0;
            mcuPwm[i] = 0;
            mcuAdc[i] = 0;
            mcuPinI[i] = 0;
        }
    }

    public CompInstance(LocationKey key, Def def) {
        this.key = key;
        this.def = def;
    }

    public boolean burnt() {
        return state == 3;
    }

    public boolean isClosed() {
        return state == CompStates.CLOSED;
    }

    /** Проводящий «сквозь себя» элемент, который объединяет узлы (wire/bus/closed switch). */
    public boolean isConductor() {
        if (burnt()) {
            return false;
        }
        if (def.hub) {
            return true;   // провод / шина земли / шина питания
        }
        return switch (def.kind) {
            case SWITCH, BUTTON, TOGGLE, PROBE -> isClosed();
            default -> false;
        };
    }

    /** Является ли блок «бесполезной» подставкой (её узлы не объединяются). */
    public boolean isHostOnly() {
        return def.kind == me.arena.electronics.registry.Kind.BOARD
                || def.kind == me.arena.electronics.registry.Kind.EMPTY
                || def.kind == me.arena.electronics.registry.Kind.SCHEMATIC;
    }

    public double tunedVoltage() {
        if (def.tunable) {
            return def.voltage * (tune / 1000.0);
        }
        return def.voltage;
    }

    public double tuneFraction() {
        return tune / 1000.0;
    }

    public void setTune(double fraction) {
        tune = (int) Math.round(Math.max(0, Math.min(1, fraction)) * 1000);
    }

    public void burn(String reason) {
        if (state == 3) {
            return;
        }
        state = 3;
        fault = reason;
        brightness = 0;
        voltage = 0;
        current = 0;
        power = 0;
    }

    public void clearFault() {
        state = 0;
        fault = "";
    }

    public boolean needsTick() {
        return def.isTimeDependent();
    }

    @Override
    public String toString() {
        return def.id + "@" + key;
    }
}
