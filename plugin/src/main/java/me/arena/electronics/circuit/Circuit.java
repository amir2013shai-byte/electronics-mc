package me.arena.electronics.circuit;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.registry.Orientation;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Хранилище компонентов и «движок»: пересчёт цепей, состояние, рендер.
 * Один экземпляр на плагин.
 */
public final class Circuit {

    private final ElectronicsPlugin plugin;
    /** world -> "x,y,z" -> компонент. */
    private final Map<String, Map<String, CompInstance>> byWorld = new HashMap<>();
    /** Позиции, чьи блоки-«подставки» держат компонент (для быстрого удаления). */
    private final Set<String> hostMarks = new HashSet<>();
    private boolean dirty = true;
    private long tick = 0;
    private long lastSolveNanos = 0;
    private int lastIslands = 0;
    private int lastComponents = 0;
    private String lastError = "";

    public Circuit(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Хранилище (реестр на диске + бонусный PDC). */
    public Persistence persistence() {
        return Persistence.get(plugin);
    }

    // ---------------------------------------------------------------- хранение

    private static String posKey(int x, int y, int z) {
        return pos(x, y, z);
    }

    public Map<String, CompInstance> world(String worldName) {
        return byWorld.computeIfAbsent(worldName, k -> new HashMap<>());
    }

    public CompInstance get(Block b) {
        return get(LocationKey.of(b));
    }

    public CompInstance get(LocationKey key) {
        Map<String, CompInstance> m = byWorld.get(key.worldName());
        return m == null ? null : m.get(pos(key.x, key.y, key.z));
    }

    public Collection<CompInstance> all() {
        List<CompInstance> out = new ArrayList<>();
        for (Map<String, CompInstance> m : byWorld.values()) {
            out.addAll(m.values());
        }
        return out;
    }

    public Collection<CompInstance> ofWorld(String worldName) {
        Map<String, CompInstance> m = byWorld.get(worldName);
        return m == null ? List.of() : m.values();
    }

    public int size() {
        int n = 0;
        for (Map<String, CompInstance> m : byWorld.values()) {
            n += m.size();
        }
        return n;
    }

    public void put(LocationKey key, Def def, Orientation orientation) {
        CompInstance c = new CompInstance(key, def);
        c.orientation = orientation;
        world(key.worldName()).put(pos(key.x, key.y, key.z), c);
        markDirty();
    }

    public void put(CompInstance c) {
        world(c.key.worldName()).put(pos(c.key.x, c.key.y, c.key.z), c);
        markDirty();
    }

    public CompInstance remove(LocationKey key) {
        Map<String, CompInstance> m = byWorld.get(key.worldName());
        if (m == null) {
            return null;
        }
        CompInstance c = m.remove(pos(key.x, key.y, key.z));
        if (c != null) {
            markDirty();
        }
        return c;
    }

    public void clearWorld(String worldName) {
        Map<String, CompInstance> m = byWorld.remove(worldName);
        if (m != null) {
            markDirty();
        }
    }

    public void clearAll() {
        byWorld.clear();
        hostMarks.clear();
        markDirty();
    }

    public void markDirty() {
        dirty = true;
    }

    public boolean isDirty() {
        return dirty;
    }

    public long tick() {
        return tick;
    }

    public long lastSolveNanos() {
        return lastSolveNanos;
    }

    public int lastIslands() {
        return lastIslands;
    }

    public int lastComponents() {
        return lastComponents;
    }

    public String lastError() {
        return lastError;
    }

    // ---------------------------------------------------------------- симуляция

    /** Тик движка: пересчёт при изменениях, непрерывно для ёмкостей/генераторов. */
    public void engineTick() {
        tick++;
        if (size() == 0) {
            dirty = false;
            return;
        }
        boolean timeDependent = plugin.config().simContinuous && hasTimeDependent();
        if (!dirty && !timeDependent) {
            return;
        }
        int budget = plugin.config().componentsPerTick;
        if (budget <= 0) {
            budget = Integer.MAX_VALUE;
        }
        long t0 = System.nanoTime();
        try {
            lastIslands = Simulation.run(plugin, this, budget);
        } catch (RuntimeException ex) {
            lastError = ex.getClass().getSimpleName() + ": " + ex.getMessage();
            plugin.getLogger().warning("ошибка расчёта цепи: " + lastError);
            if (plugin.config().debug) {
                ex.printStackTrace();
            }
        }
        lastSolveNanos = System.nanoTime() - t0;
        lastComponents = size();
        dirty = false;
    }

    private boolean hasTimeDependent() {
        for (Map<String, CompInstance> m : byWorld.values()) {
            for (CompInstance c : m.values()) {
                if (c.needsTick()) {
                    return true;
                }
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- топология

    /**
     * Узел (net) = «пара» {A,B} соседних позиций: netKey(A,B). Два компонента
     * соединены, если у них есть одно и то же имя узла. Проводники дополнительно
     * «склеивают» узлы (см. Simulation).
     */
    public static String netAt(String worldName, int x, int y, int z) {
        return worldName + "|" + x + "," + y + "," + z;
    }

    public static String netAt(Block b) {
        return netAt(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
    }

    /** Каноническое имя узла для пары соседних блоков. */
    public static String pairNet(Block a, Block b) {
        int ax = a.getX(), ay = a.getY(), az = a.getZ();
        int bx = b.getX(), by = b.getY(), bz = b.getZ();
        boolean flip = bx < ax || (bx == ax && (by < ay || (by == ay && bz < az)));
        Block first = flip ? b : a;
        Block second = flip ? a : b;
        return netAt(first) + "&" + second.getX() + "," + second.getY() + "," + second.getZ();
    }

    private static final org.bukkit.block.BlockFace[] DIR_FACE = {
            org.bukkit.block.BlockFace.NORTH, org.bukkit.block.BlockFace.EAST,
            org.bukkit.block.BlockFace.SOUTH, org.bukkit.block.BlockFace.WEST,
            org.bukkit.block.BlockFace.UP, org.bukkit.block.BlockFace.DOWN};

    /** Направления выводов (делегация в {@link Topology}). */
    public static org.bukkit.block.BlockFace[] pinFacesFor(CompInstance c) {
        int[] dirs = Topology.pinDirs(defView(c), c.orientation.index);
        org.bukkit.block.BlockFace[] out = new org.bukkit.block.BlockFace[dirs.length];
        for (int i = 0; i < dirs.length; i++) {
            out[i] = DIR_FACE[dirs[i]];
        }
        return out;
    }

    /** Сколько выводов реально участвует в электрической схеме. */
    public static int pinCount(CompInstance c) {
        return Topology.pinCount(defView(c));
    }

    /**
     * Разрешает net\'ы контактов компонента — делегирует в {@link Topology}
     * (вся логика узлов «пара блоков» живёт там и покрыта тестами).
     */
    public static void resolvePins(CompInstance c, Map<String, CompInstance> worldMap) {
        Topology.NetSource ns = new Topology.NetSource() {
            @Override
            public String netAt(int x, int y, int z) {
                return Circuit.netAt(c.key.worldName(), x, y, z);
            }

            @Override
            public boolean isConductorBelow(int x, int y, int z) {
                if (worldMap == null) {
                    return false;
                }
                CompInstance host = worldMap.get(pos(x, y, z));
                return host != null && host.isConductor();
            }
        };
        Topology.resolve(viewOf(c), c.pinNet, ns);
    }

    /** Адаптер описания компонента к «голой» топологии. */
    static Topology.DefLike defView(CompInstance c) {
        Def d = c.def;
        return new Topology.DefLike() {
            @Override
            public Kind kind() {
                return d.kind;
            }

            @Override
            public String logic() {
                return d.logic;
            }

            @Override
            public boolean stack() {
                return d.stack;
            }
        };
    }

    /** Адаптер экземпляра компонента к «голой» топологии. */
    static Topology.View viewOf(CompInstance c) {
        return new Topology.View() {
            @Override
            public void setAnchorNet(String net) {
                c.anchorNet = net;
            }

            @Override
            public void setStackedOnConductor(boolean on) {
                c.stackedOnConductor = on;
            }

            @Override
            public Topology.DefLike def() {
                return defView(c);
            }

            @Override
            public int orientIndex() {
                return c.orientation.index;
            }

            @Override
            public int x() {
                return c.key.x;
            }

            @Override
            public int y() {
                return c.key.y;
            }

            @Override
            public int z() {
                return c.key.z;
            }
        };
    }

    public static String pos(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /** Узел «под компонентом» — для стопок на проводе/шине. */
    public static String stackNet(Block device, Block host) {
        return pairNet(device, host);
    }
}
