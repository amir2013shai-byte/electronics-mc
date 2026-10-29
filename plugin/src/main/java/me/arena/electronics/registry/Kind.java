package me.arena.electronics.registry;

/**
 * Электрические «родители» компонентов. От них зависит, как компонент
 * внедряется в матрицуModified Nodal Analysis (MNA) и как он соединяется с соседями.
 */
public enum Kind {
    /** Подставка/плата, ничего не проводит, только держит визуал. */
    BOARD,
    /** Макетная плата со своими шинами (6 гнёзд + 2 рейки). */
    BREADBOARD,
    /** Провод: идеальный контакт (большая проводимость), соединяет соседей. */
    WIRE,
    /** Источник напряжения (батарея, блок питания) + внутреннее сопротивление. */
    SOURCE,
    /** Шина земли: фиксирует узел в 0 В. */
    GROUND,
    /** Резистор (омический). */
    RESISTOR,
    /** Потенциометр: два резистора от ползунка. */
    POT,
    /** Конденсатор (дискретное интегрирование). */
    CAPACITOR,
    /** Светодиод: кусочно-линейная модель с порогом и защитой от обратного напряжения. */
    LED,
    /** Лампа накаливания: холодное/горячее сопротивление + яркость. */
    LAMP,
    /** Диод. */
    DIODE,
    /** Ключ (выключатель/переключатель). */
    SWITCH,
    BUTTON,
    TOGGLE,
    /** Биполярный транзистор (упрощённая модель). */
    TRANSISTOR,
    /** Логический элемент (AND/OR/XOR/NOT/CMP). */
    GATE,
    BUZZER,
    MOTOR,
    FUSE,
    VOLTMETER,
    AMMETER,
    MULTIMETER,
    CUTTER,
    /** Пустая схема/заготовка платы — сохраняет и сверяет цепь. */
    SCHEMATIC,
    PROBE,
    GENERATOR,
    SOLAR,
    DISTRIBUTOR,
    EMPTY;

    /** Проводит ли компонент «сквозь себя» на соседние блоки (для объединения узлов). */
    public boolean mergesNeighbours() {
        return this == WIRE || this == BREADBOARD || this == DISTRIBUTOR;
    }

    public boolean twoTerminal() {
        return switch (this) {
            case RESISTOR, CAPACITOR, LED, LAMP, DIODE, SWITCH, BUTTON, TOGGLE, FUSE,
                 VOLTMETER, AMMETER, WIRE, PROBE, SOURCE, GROUND, GENERATOR, SOLAR, POT -> true;
            default -> false;
        };
    }
}
