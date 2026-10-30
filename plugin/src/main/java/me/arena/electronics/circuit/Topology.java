package me.arena.electronics.circuit;

import me.arena.electronics.registry.Kind;

/**
 * Голая (без Bukkit) топология соединений.
 *
 * <p>Узел — это упорядоченная пара соседних блоков {A,B}: контакт
 * компонента «смотрит» на соседа в направлении вывода, и любой другой
 * контакт, глядящий на ту же пару, оказывается в том же узле. Так
 * стоящие вплотную детали соединяются, «стопка» на проводе — тоже, а
 * инертная подставка (стол, каменная плита) сети не объединяет и потому
 * не замыкает цепь.</p>
 *
 * <p>Класс намеренно не зависит от Bukkit — его можно тестировать без
 * запуска сервера (см. tools/TestTopology.java).</p>
 */
public final class Topology {

    private Topology() {
    }

    /** Направления: 0 NORTH(-z), 1 EAST(+x), 2 SOUTH(+z), 3 WEST(-x), 4 UP(+y), 5 DOWN(-y). */
    public static final int[] DX = {0, 1, 0, -1, 0, 0};
    public static final int[] DY = {0, 0, 0, 0, 1, -1};
    public static final int[] DZ = {-1, 0, 1, 0, 0, 0};

    public static int opposite(int dir) {
        return dir ^ 3;
    }

    /** Как плагин называет блоки и что знает о «опоре» под компонентом. */
    public interface NetSource {
        /** Имя блока (обычно {@code world|x,y,z}). */
        String netAt(int x, int y, int z);

        /** true, если блок под компонентом — провод/шина (на него можно ставить детали). */
        boolean isConductorBelow(int x, int y, int z);
    }

    /** Описание компонента, нужное топологии, + приёмник результатов. */
    public interface View {
        DefLike def();

        /** 0 north, 1 east, 2 south, 3 west, 4 up, 5 down. */
        int orientIndex();

        int x();

        int y();

        int z();

        void setAnchorNet(String net);

        void setStackedOnConductor(boolean onConductor);
    }

    public interface DefLike {
        Kind kind();

        String logic();

        boolean stack();
    }

    /** Каноническое имя узла для пары блоков (порядок координат фиксируется). */
    public static String pairNet(NetSource ns, int ax, int ay, int az, int bx, int by, int bz) {
        boolean flip = bx < ax || (bx == ax && (by < ay || (by == ay && bz < az)));
        int x1 = flip ? bx : ax, y1 = flip ? by : ay, z1 = flip ? bz : az;
        int x2 = flip ? ax : bx, y2 = flip ? ay : by, z2 = flip ? az : bz;
        return ns.netAt(x1, y1, z1) + "&" + x2 + "," + y2 + "," + z2;
    }

    /** Число выводов, участвующих в схеме. */
    public static int pinCount(DefLike def) {
        switch (def.kind()) {
            case TRANSISTOR:
                return 3;
            case GATE:
                return "NOT".equalsIgnoreCase(safe(def.logic())) ? 3 : 4;
            case POT:
                return 3;
            case RELAY:
                return 4;      // катушка вдоль оси + контакт вбок
            case BRIDGE:
            case TRANSFORMER:
                return 4;      // плечи вдоль оси + два боковых (у моста «+»/«−», у тр-ра вторичка)
            case REGULATOR:
                return 3;      // IN/OUT вдоль оси, GND вбок — как у транзистора
            case BREADBOARD:
            case STATION:
            case TOOL:
            case SUPPLY:
                return 0;
            case WIRE:
                return 6;      // «крест»: 4 стороны + вверх/вниз (провод = сетка)
            case FUSE:
                return 4;      // плечи вдоль оси + «нога» вниз + «шапка» вверх
            default:
                if (def.kind() == Kind.GROUND || def.kind() == Kind.DISTRIBUTOR) {
                    return 6;  // шина — хаб: соединяет всё вокруг
                }
                if (def.stack() || def.kind() == Kind.SWITCH || def.kind() == Kind.BUTTON
                        || def.kind() == Kind.TOGGLE || def.kind() == Kind.PROBE) {
                    return 6;
                }
                return 2;
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    /**
     * Направления выводов. Индексы 0/1 — «плечи» вдоль оси компонента,
     * 2..5 — дополнительные выводы (3-полюсники, ИС, «хабы»).
     */
    public static int[] pinDirs(DefLike def, int orient) {
        int n = 0, e = 1, s = 2, w = 3, up = 4, down = 5;
        int along0, along1, a, b;
        switch (orient) {
            default:
            case 0:                      // NORTH
                along0 = n; along1 = s; a = e; b = w;
                break;
            case 2:                      // SOUTH
                along0 = s; along1 = n; a = w; b = e;
                break;
            case 1:                      // EAST
                along0 = e; along1 = w; a = n; b = s;
                break;
            case 3:                      // WEST
                along0 = w; along1 = e; a = s; b = n;
                break;
            case 4:                      // UP
                along0 = up; along1 = down; a = n; b = e;
                break;
            case 5:                      // DOWN
                along0 = down; along1 = up; a = s; b = w;
                break;
        }
        switch (def.kind()) {
            case TRANSISTOR:
            case REGULATOR:
                return new int[]{along0, along1, a, up, down, b};
            case GATE:
                if ("NOT".equalsIgnoreCase(safe(def.logic()))) {
                    return new int[]{along0, along1, a, up, down, b};
                }
                return new int[]{along0, along1, a, b, up, down};
            case POT:
                return new int[]{along0, along1, up, a, b, down};
            case WIRE:
                // провод = «крест»: соединяется со всем вокруг, включая стопку
                // (иначе цепь нельзя замкнуть в плоскости, не строя лесенки)
                return new int[]{n, e, s, w, up, down};
            case FUSE:
                // плечи вдоль оси + «нога» вниз + «шапка» вверх (на предохранитель ставят детали)
                return new int[]{along0, along1, down, up, a, b};
            default:
                // два полюса — строго вдоль оси компонента; иначе соседние
                // детали «схлопываются» в один узел и цепь коротит
                return new int[]{along0, along1, a, b, up, down};
        }
    }

    /**
     * Считает узлы выводов компонента и пишет их в {@code view}
     * (а массив {@code pinNetOut} — тот же массив, что хранит CompInstance).
     *
     * @return true, если хоть один вывод разрешён (цепь «живая»)
     */
    public static boolean resolve(View view, String[] pinNetOut, NetSource ns) {
        DefLike def = view.def();
        for (int i = 0; i < pinNetOut.length; i++) {
            pinNetOut[i] = null;
        }
        String anchor = pairNet(ns, view.x(), view.y(), view.z(), view.x(), view.y() - 1, view.z());
        int[] dirs = pinDirs(def, view.orientIndex());
        int count = Math.max(2, Math.min(dirs.length, Math.max(0, pinCount(def))));
        for (int i = 0; i < count; i++) {
            int d = dirs[i];
            pinNetOut[i] = pairNet(ns, view.x(), view.y(), view.z(),
                    view.x() + DX[d], view.y() + DY[d], view.z() + DZ[d]);
        }
        view.setAnchorNet(anchor);
        view.setStackedOnConductor(ns.isConductorBelow(view.x(), view.y() - 1, view.z()));
        boolean any = anchor != null;
        for (int i = 0; i < count; i++) {
            if (pinNetOut[i] != null) {
                any = true;
            }
        }
        return any;
    }
}
