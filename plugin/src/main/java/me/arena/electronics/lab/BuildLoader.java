package me.arena.electronics.lab;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.circuit.Circuit;
import me.arena.electronics.circuit.Simulation;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.registry.Orientation;
import me.arena.electronics.util.Json;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Готовые сборки-пресеты ({@code plugins/Electronics/builds/*.json}).
 *
 * <p>Формат: {@code origin} — точка отсчёта, {@code blocks} — подставки
 * (хосты), {@code components} — компоненты {@code {"d": id, "p":[x,y,z], "o": 0..5}}.
 * Координаты относительные, поэтому сборку можно ставить в любом месте мира.</p>
 */
public final class BuildLoader {

    private final ElectronicsPlugin plugin;

    public BuildLoader(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- модель

    public static final class Entry {
        public final String defId;
        public final int dx, dy, dz;
        public final int orientation;
        public final double tune;
        public final boolean host;

        Entry(String defId, int dx, int dy, int dz, int orientation, double tune, boolean host) {
            this.defId = defId;
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
            this.orientation = orientation;
            this.tune = tune;
            this.host = host;
        }
    }

    /** Публичная фабрика: сборку строит и редактор схемы, а не только JSON-файл. */
    public static Entry entry(String defId, int dx, int dy, int dz, int orientation,
                              double tune, boolean host) {
        return new Entry(defId, dx, dy, dz, orientation, tune, host);
    }

    /** Пустая сборка для заполнения из GUI-редактора. */
    public Build newBuild(String name, String title) {
        return new Build(name, title);
    }

    public static final class Build {
        public final String name;
        public final String title;
        public final List<Entry> blocks = new ArrayList<>();
        public final List<Entry> components = new ArrayList<>();

        Build(String name, String title) {
            this.name = name;
            this.title = title;
        }

        public int size() {
            return blocks.size() + components.size();
        }
    }

    // ---------------------------------------------------------------- загрузка

    public File dir() {
        return new File(plugin.getDataFolder(), "builds");
    }

    /** Имена всех доступных сборок: файлы на диске + встроенные в jar. */
    public List<String> names() {
        Map<String, Boolean> out = new LinkedHashMap<>();
        File d = dir();
        File[] files = d.listFiles((f, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
        if (files != null) {
            for (File f : files) {
                out.put(f.getName().substring(0, f.getName().length() - 5), true);
            }
        }
        for (String res : EMBEDDED) {
            out.put(res.substring(0, res.length() - 5), true);
        }
        return new ArrayList<>(out.keySet());
    }

    private static final List<String> EMBEDDED = List.of("led_basic.json", "rc_blink.json");

    public Build load(String name) {
        String text = readFromDisk(name);
        if (text == null) {
            text = readFromJar(name);
        }
        if (text == null) {
            return null;
        }
        return parse(name, text);
    }

    private String readFromDisk(String name) {
        File f = new File(dir(), name + ".json");
        if (!f.isFile()) {
            return null;
        }
        try (InputStream in = new FileInputStream(f)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            plugin.getLogger().warning("не смог прочитать " + f + ": " + e.getMessage());
            return null;
        }
    }

    private String readFromJar(String name) {
        try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream("builds/" + name + ".json")) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    /** Разбор JSON-описания сборки. */
    public Build parse(String name, String text) {
        Map<String, Object> m = Json.objectOf(text);
        if (m == null) {
            return null;
        }
        Build b = new Build(name, Json.str(m, "name", name));
        int[] origin = vec3(m.get("origin"));
        readEntries(b.blocks, Json.arr(m, "blocks"), origin);
        readEntries(b.components, Json.arr(m, "components"), origin);
        return b;
    }

    private static int[] vec3(Object o) {
        int[] v = new int[]{0, 0, 0};
        if (o instanceof List<?> l) {
            for (int i = 0; i < 3 && i < l.size(); i++) {
                Object e = l.get(i);
                if (e instanceof Number n) {
                    v[i] = n.intValue();
                }
            }
        }
        return v;
    }

    private static double num(Map<String, Object> m, String k, double def) {
        Object o = m.get(k);
        return o instanceof Number n ? n.doubleValue() : def;
    }

    private void readEntries(List<Entry> into, List<Object> list, int[] origin) {
        if (list == null) {
            return;
        }
        for (Object o : list) {
            if (!(o instanceof Map)) {
                continue;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> e = (Map<String, Object>) o;
            String id = Json.str(e, "d", "");
            int[] p = vec3(e.get("p"));
            int orient = (int) num(e, "o", 0);
            double tune = num(e, "t", 1.0);
            into.add(new Entry(id, origin[0] + p[0], origin[1] + p[1], origin[2] + p[2], orient, tune, true));
        }
    }

    /** Кладёт встроенные пресеты в plugins/Electronics/builds (если их там нет). */
    public void installBuiltin() {
        File d = dir();
        if (!d.exists() && !d.mkdirs()) {
            return;
        }
        for (String res : EMBEDDED) {
            File f = new File(d, res);
            if (f.exists()) {
                continue;
            }
            try (InputStream in = plugin.getClass().getClassLoader().getResourceAsStream("builds/" + res)) {
                if (in != null) {
                    Files.write(f.toPath(), in.readAllBytes());
                }
            } catch (Exception e) {
                plugin.getLogger().warning("не удалось распаковать пресет " + res + ": " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------- установка

    /** Ставит сборку так, чтобы её origin попал в {@code at}. */
    public Result place(Player p, Build build, Block at) {
        if (at == null || build == null) {
            Result r0 = new Result();
            r0.error = "нет точки установки";
            return r0;
        }
        Result r = new Result();
        if (build == null) {
            r.error = "сборка не найдена";
            return r;
        }
        // сначала подставки
        for (Entry e : build.blocks) {
            Block b = at.getRelative(e.dx, e.dy, e.dz);
            if (!b.getType().isAir() && plugin.circuit().get(b) != null) {
                r.skipped++;
                continue;
            }
            b.setType(plugin.items().baseMaterial(), false);
            plugin.circuit().persistence().markHost(b, true);
            r.hosts++;
        }
        // затем компоненты
        for (Entry e : build.components) {
            Def def = Components.byId(e.defId);
            if (def == null) {
                r.unknown.add(e.defId);
                continue;
            }
            Block b = at.getRelative(e.dx, e.dy, e.dz);
            if (!b.getType().isAir() && !isBaseBlock(b)) {
                r.skipped++;
                continue;
            }
            if (b.getType().isAir()) {
                b.setType(plugin.items().baseMaterial(), false);
                plugin.circuit().persistence().markHost(b, true);
                r.hosts++;
            }
            LocationKey key = LocationKey.of(b);
            if (plugin.circuit().get(key) != null) {
                r.skipped++;
                continue;
            }
            CompInstance c = new CompInstance(key, def);
            c.orientation = Orientation.byIndex(e.orientation);
            if (def.tunable) {
                c.setTune(e.tune);
            }
            plugin.circuit().put(c);
            plugin.circuit().persistence().write(c);
            plugin.renderer().requestUpdate(c);
            r.placed++;
        }
        plugin.circuit().markDirty();
        if (p == null) {
            r.note = "установка с консоли/из пресета: предметы не тратятся";
        } else if (p.getGameMode() != GameMode.CREATIVE) {
            r.note = "в не-креативе компоненты берутся из инвентаря; здесь установка бесплатная";
        }
        return r;
    }

    private boolean isBaseBlock(Block b) {
        Material base = plugin.items().baseMaterial();
        return b.getType() == base;
    }

    public static final class Result {
        public int hosts;
        public int placed;
        public int skipped;
        public String error = "";
        public String note = "";
        public final List<String> unknown = new ArrayList<>();
    }

    // ---------------------------------------------------------------- сохранение

    /** Собирает JSON-описание для «острова» под курсором игрока. */
    public String exportIsland(Player p) {
        Block target = p.getTargetBlock(null, 8);
        CompInstance c = plugin.circuit().get(target);
        if (c == null && target.getRelative(org.bukkit.block.BlockFace.UP) != null) {
            c = plugin.circuit().get(target.getRelative(org.bukkit.block.BlockFace.UP));
        }
        if (c == null) {
            return null;
        }
        List<CompInstance> island = Simulation.islandWith(plugin.circuit(), c);
        if (island.isEmpty()) {
            return null;
        }
        int ox = c.key.x, oy = c.key.y, oz = c.key.z;
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"name\": \"Сборка игрока ").append(p.getName()).append("\",\n");
        sb.append("  \"origin\": [").append(ox).append(", ").append(oy).append(", ").append(oz).append("],\n");
        sb.append("  \"blocks\": [\n");
        List<String> hosts = new ArrayList<>();
        for (CompInstance k : island) {
            Block host = k.key.block();
            if (host == null) {
                continue;
            }
            Block under = host.getRelative(org.bukkit.block.BlockFace.DOWN);
            if (under != null && plugin.circuit().persistence().isHost(under)) {
                hosts.add("    {\"d\": \"bench\", \"p\": [" + (under.getX() - ox) + ", " + (under.getY() - oy)
                        + ", " + (under.getZ() - oz) + "], \"host\": true}");
            }
        }
        sb.append(String.join(",\n", hosts)).append(hosts.isEmpty() ? "" : ",\n");
        sb.append("  ],\n");
        sb.append("  \"components\": [\n");
        List<String> comps = new ArrayList<>();
        for (CompInstance k : island) {
            if (k.def.kind == Kind.BOARD) {
                continue;
            }
            comps.add("    {\"d\": \"" + k.def.id + "\", \"p\": [" + (k.key.x - ox) + ", " + (k.key.y - oy)
                    + ", " + (k.key.z - oz) + "], \"o\": " + k.orientation.index + "}");
        }
        sb.append(String.join(",\n", comps)).append("\n");
        sb.append("  ]\n}\n");
        return sb.toString();
    }

    /** Сохраняет сборку в plugins/Electronics/builds/&lt;name&gt;.json. */
    public File save(Build build) {
        File d = dir();
        // каталог создаёт saveResource при старте плагина
        File f = new File(d, build.name + ".json");
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"name\": \"").append(esc(build.title)).append("\",\n  \"origin\": [0, 0, 0],\n");
        sb.append("  \"blocks\": [");
        List<String> list = new ArrayList<>();
        for (Entry e : build.blocks) {
            list.add("{\"d\": \"" + e.defId + "\", \"p\": [" + e.dx + ", " + e.dy + ", " + e.dz + "], \"host\": true}");
        }
        sb.append(String.join(", ", list)).append("],\n  \"components\": [");
        list = new ArrayList<>();
        for (Entry e : build.components) {
            list.add("{\"d\": \"" + e.defId + "\", \"p\": [" + e.dx + ", " + e.dy + ", " + e.dz
                    + "], \"o\": " + e.orientation + "}");
        }
        sb.append(String.join(", ", list)).append("]\n}\n");
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            return f;
        } catch (Exception e) {
            plugin.getLogger().warning("не смог записать " + f + ": " + e.getMessage());
            return null;
        }
    }

    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
