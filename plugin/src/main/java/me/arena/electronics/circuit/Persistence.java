package me.arena.electronics.circuit;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.registry.Orientation;
import me.arena.electronics.util.Json;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Хранилище электроники.
 *
 * Важно для Spigot: у обычного блока нет PersistentDataContainer (PDC есть
 * только у TileState — сундуки, таблички и т.п.). Поэтому «вторая половина»
 * данных (тип компонента, поворот, состояние, поломка, записанная схема)
 * живёт в текстовом реестре plugins/Electronics/registry.json, а отметка на
 * блоке (если ядро позволяет — Paper-совместимые билды) пишется в PDC
 * тайл-состояния как бонус для совместимости с другими плагинами.
 */
public final class Persistence {

    private static final Map<String, Persistence> INSTANCES = new ConcurrentHashMap<>();

    private final ElectronicsPlugin plugin;
    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private boolean dirty;
    private int loadedCount;
    private long lastSaveTick;

    /** Одна запись реестра. */
    public static final class Entry {
        public String world;
        public int x;
        public int y;
        public int z;
        public String def = "";
        public int orient;
        public int state;
        public int tune = 1000;
        public String fault = "";
        public String lab = "";
        public String netlist = "";
        public String note = "";
        /** Прошивка микроконтроллера (Kind.MCU). */
        public String program = "";
        public boolean host;

        public String key() {
            return world + ";" + x + ";" + y + ";" + z;
        }
    }

    public Persistence(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    public static Persistence get(ElectronicsPlugin plugin) {
        return INSTANCES.computeIfAbsent(plugin.getName(), k -> new Persistence(plugin));
    }

    private File file() {
        return new File(plugin.getDataFolder(), "registry.json");
    }

    // ------------------------------------------------------------- запись в реестр

    public void write(CompInstance c) {
        Entry e = entryFor(c.key, true);
        e.def = c.def.id;
        e.orient = c.orientation == null ? 0 : c.orientation.index;
        e.state = c.state;
        e.tune = c.tune;
        e.fault = c.fault == null ? "" : c.fault;
        e.lab = c.lab == null ? "" : c.lab;
        e.program = c.program == null ? "" : c.program;
        dirty = true;
        writePdcBonus(c.key.block(), e);
    }

    public void writeExtra(Block b, String netlist, String note) {
        if (b == null) {
            return;
        }
        Entry e = entryFor(LocationKey.of(b), true);
        if (netlist != null) {
            e.netlist = netlist;
        }
        if (note != null) {
            e.note = note;
        }
        dirty = true;
        writePdcBonus(b, e);
    }

    public String[] readExtra(Block b) {
        Entry e = b == null ? null : entries.get(LocationKey.of(b).flat());
        if (e == null) {
            return new String[]{"", ""};
        }
        return new String[]{e.netlist == null ? "" : e.netlist, e.note == null ? "" : e.note};
    }

    public void clear(Block b) {
        if (b == null) {
            return;
        }
        entries.remove(LocationKey.of(b).flat());
        dirty = true;
    }

    public void markHost(Block b, boolean add) {
        if (b == null) {
            return;
        }
        LocationKey key = LocationKey.of(b);
        Entry e = entries.get(key.flat());
        if (e == null) {
            if (!add) {
                return;
            }
            e = entryFor(key, true);
            e.def = "bench";
            e.host = true;
        } else {
            e.host = add;
            if (!add && (e.def == null || e.def.isEmpty() || "bench".equals(e.def))) {
                entries.remove(key.flat());
            }
        }
        dirty = true;
    }

    public boolean isHost(Block b) {
        if (b == null || b.getType().isAir()) {
            return false;
        }
        Entry e = entries.get(LocationKey.of(b).flat());
        return e != null && e.host;
    }

    public boolean isMine(Block b) {
        if (b == null) {
            return false;
        }
        Entry e = entries.get(LocationKey.of(b).flat());
        return e != null && e.def != null && !e.def.isEmpty() && Components.byId(e.def) != null;
    }

    public boolean hasEntry(Block b) {
        return b != null && entries.containsKey(LocationKey.of(b).flat());
    }

    private Entry entryFor(LocationKey key, boolean create) {
        Entry e = entries.get(key.flat());
        if (e == null && create) {
            e = new Entry();
            e.world = key.worldName();
            e.x = key.x;
            e.y = key.y;
            e.z = key.z;
            entries.put(key.flat(), e);
        }
        return e;
    }

    /** Бонус: если ядро даёт PDC на блоке (Paper), дублируем туда же. */
    private void writePdcBonus(Block b, Entry e) {
        if (b == null) {
            return;
        }
        try {
            Object state = b.getState();
            if (state instanceof org.bukkit.block.TileState ts) {
                var pdc = ts.getPersistentDataContainer();
                pdc.set(CompInstance.PDC_DEF, org.bukkit.persistence.PersistentDataType.STRING, e.def);
                pdc.set(CompInstance.PDC_ORIENT, org.bukkit.persistence.PersistentDataType.INTEGER, e.orient);
                pdc.set(CompInstance.PDC_STATE, org.bukkit.persistence.PersistentDataType.INTEGER, e.state);
                ts.update(true, false);
            }
        } catch (RuntimeException | Error ignored) {
            // не критично: реестр на диске — источник истины
        }
    }

    // ---------------------------------------------------------------- загрузка

    public CompInstance read(Entry e) {
        Def def = Components.byId(e.def);
        if (def == null) {
            return null;
        }
        LocationKey key = new LocationKey(org.bukkit.Bukkit.getWorld(e.world), e.x, e.y, e.z);
        CompInstance c = new CompInstance(key, def);
        c.orientation = Orientation.byIndex(e.orient);
        c.state = e.state;
        c.tune = e.tune;
        c.fault = e.fault == null ? "" : e.fault;
        c.lab = e.lab == null ? "" : e.lab;
        c.program = e.program == null ? "" : e.program;
        loadedCount++;
        return c;
    }

    /** Читает реестр с диска и возвращает записи, у которых компонент ещё не в памяти. */
    public List<CompInstance> loadInto(Circuit circuit) {
        List<CompInstance> out = new ArrayList<>();
        File f = file();
        if (f.isFile()) {
            try {
                parse(Files.readString(f.toPath(), StandardCharsets.UTF_8));
            } catch (IOException | RuntimeException ex) {
                plugin.getLogger().warning("реестр повреждён: " + ex.getMessage());
            }
        }
        for (Entry e : entries.values()) {
            if (e.def == null || e.def.isEmpty() || e.host && circuit.get(key(e)) != null) {
                continue;
            }
            if (circuit.get(key(e)) != null) {
                continue;
            }
            Def def = Components.byId(e.def);
            if (def == null) {
                continue;
            }
            if (!chunkLoaded(e)) {
                continue;   // догрузится вместе с чанком
            }
            CompInstance c = read(e);
            if (c != null) {
                circuit.put(c);
                out.add(c);
            }
        }
        for (CompInstance c : out) {
            plugin.renderer().requestUpdate(c);
        }
        if (!out.isEmpty()) {
            plugin.getLogger().info("восстановлено компонентов из реестра: " + out.size());
        }
        circuit.markDirty();
        return out;
    }

    private static LocationKey key(Entry e) {
        return new LocationKey(org.bukkit.Bukkit.getWorld(e.world), e.x, e.y, e.z);
    }

    /**
     * Дозагружает записи реестра для чанка, который только что загрузился.
     * Вызывается из ChunkLoadEvent и из периодического «свипа» (Paper грузит
     * чанки асинхронно, и не на всякую загрузку прилетает событие).
     */
    public int loadChunk(String worldName, int cx, int cz) {
        List<CompInstance> out = new ArrayList<>();
        for (Entry e : new ArrayList<>(entries.values())) {
            if (e.def == null || e.def.isEmpty() || !e.world.equals(worldName)) {
                continue;
            }
            if ((e.x >> 4) != cx || (e.z >> 4) != cz) {
                continue;
            }
            LocationKey key = key(e);
            if (plugin.circuit().get(key) != null) {
                continue;
            }
            Def def = Components.byId(e.def);
            if (def == null) {
                continue;
            }
            CompInstance c = read(e);
            if (c != null) {
                plugin.circuit().put(c);
                out.add(c);
            }
        }
        for (CompInstance c : out) {
            plugin.renderer().requestUpdate(c);
        }
        if (!out.isEmpty()) {
            plugin.circuit().markDirty();
            loadedCount += out.size();
        }
        return out.size();
    }

    /** Сколько записей реестра ждут, пока их чанк загрузится. */
    public int pendingCount() {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.def == null || e.def.isEmpty()) {
                continue;
            }
            if (plugin.circuit().get(key(e)) == null && !chunkLoaded(e)) {
                n++;
            }
        }
        return n;
    }

    /** Проходит по записям и подхватывает те, чьи чанки уже загружены. */
    public int loadPending() {
        int total = 0;
        for (Entry e : new ArrayList<>(entries.values())) {
            if (e.def == null || e.def.isEmpty() || chunkLoaded(e)) {
                if (e.def != null && !e.def.isEmpty() && chunkLoaded(e)
                        && plugin.circuit().get(key(e)) == null) {
                    total += loadChunk(e.world, e.x >> 4, e.z >> 4);
                }
            }
        }
        return total;
    }

    private static boolean chunkLoaded(Entry e) {
        World w = org.bukkit.Bukkit.getWorld(e.world);
        return w != null && w.isChunkLoaded(e.x >> 4, e.z >> 4);
    }

    @SuppressWarnings("unchecked")
    private void parse(String json) {
        entries.clear();
        Map<String, Object> root = Json.objectOf(json);
        for (Map<String, Object> o : Json.objs(root, "components")) {
            Entry e = new Entry();
            e.world = Json.str(o, "w", "");
            e.x = Json.integer(o, "x", 0);
            e.y = Json.integer(o, "y", 0);
            e.z = Json.integer(o, "z", 0);
            e.def = Json.str(o, "d", "");
            e.orient = Json.integer(o, "o", 0);
            e.state = Json.integer(o, "s", 0);
            e.tune = Json.integer(o, "t", 1000);
            e.fault = Json.str(o, "f", "");
            e.lab = Json.str(o, "l", "");
            e.netlist = Json.str(o, "n", "");
            e.note = Json.str(o, "m", "");
            e.program = Json.str(o, "p", "");
            e.host = Json.bool(o, "h", false);
            if (!e.world.isEmpty()) {
                entries.put(e.key(), e);
            }
        }
    }

    private String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"components\": [\n");
        int i = 0;
        for (Entry e : entries.values()) {
            if (i++ > 0) {
                sb.append(",\n");
            }
            sb.append("    {\"w\":\"").append(e.world).append("\",\"x\":").append(e.x)
                    .append(",\"y\":").append(e.y).append(",\"z\":").append(e.z)
                    .append(",\"d\":\"").append(e.def).append("\"")
                    .append(",\"o\":").append(e.orient)
                    .append(",\"s\":").append(e.state)
                    .append(",\"t\":").append(e.tune);
            if (!e.fault.isEmpty()) {
                sb.append(",\"f\":\"").append(esc(e.fault)).append("\"");
            }
            if (!e.lab.isEmpty()) {
                sb.append(",\"l\":\"").append(esc(e.lab)).append("\"");
            }
            if (!e.netlist.isEmpty()) {
                sb.append(",\"n\":\"").append(esc(e.netlist)).append("\"");
            }
            if (!e.note.isEmpty()) {
                sb.append(",\"m\":\"").append(esc(e.note)).append("\"");
            }
            if (!e.program.isEmpty()) {
                sb.append(",\"p\":\"").append(esc(e.program)).append("\"");
            }
            if (e.host) {
                sb.append(",\"h\":true");
            }
            sb.append("}");
        }
        sb.append("\n  ]\n}\n");
        return sb.toString();
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    /** Автосохранение раз в ~5 секунд при изменениях. */
    public void autosave() {
        lastSaveTick++;
        if (dirty && lastSaveTick % 100 == 0) {
            save();
        }
    }

    public void save() {
        if (!dirty && !file().exists()) {
            return;
        }
        File f = file();
        f.getParentFile().mkdirs();
        Path tmp = new File(f.getParentFile(), "registry.json.tmp").toPath();
        try {
            Files.writeString(tmp, serialize(), StandardCharsets.UTF_8);
            Files.move(tmp, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
            dirty = false;
        } catch (IOException ex) {
            plugin.getLogger().warning("не удалось сохранить реестр: " + ex.getMessage());
        }
    }

    public void flush() {
        for (CompInstance c : plugin.circuit().all()) {
            write(c);
        }
        save();
    }

    /** Полное пересканирование не нужно: реестр сам является индексом. */
    public void rescanAllWorlds() {
        loadInto(plugin.circuit());
    }

    public int fullScan(World world) {
        int n = 0;
        for (Entry e : entries.values()) {
            if (e.world.equals(world.getName()) && e.def != null && !e.def.isEmpty()) {
                n++;
            }
        }
        return n;
    }

    public int entryCount() {
        return entries.size();
    }

    public int loadedCount() {
        return loadedCount;
    }

    public Map<String, Entry> raw() {
        return new HashMap<>(entries);
    }

    public void remove(LocationKey key) {
        entries.remove(key.flat());
        dirty = true;
    }
}
