package me.arena.electronics.circuit;

/** Номера визуальных состояний (совпадают с visual_states в components.json). */
public final class CompStates {

    public static final int OFF = 0;
    public static final int DIM = 1;
    public static final int ON = 2;
    public static final int BURNT = 3;
    public static final int REVERSE = 4;
    public static final int CLOSED = 5;

    public static final String[] NAMES = {"выключен", "тускло", "горит", "сгорел", "обратно", "замкнут"};

    private CompStates() {
    }

    public static String name(int state) {
        return state >= 0 && state < NAMES.length ? NAMES[state] : "состояние " + state;
    }
}
