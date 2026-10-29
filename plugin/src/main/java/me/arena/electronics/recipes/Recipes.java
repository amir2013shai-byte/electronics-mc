package me.arena.electronics.recipes;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Крафт компонентов обычными ванильными рецептами (никаких клиентских модов:
 * результат — тот же предмет с item_model из ресурспака).
 */
public final class Recipes {

    private record Shaped(String[] pattern, Map<Character, Material> key) {
    }

    private record Shapeless(List<Material> ingredients) {
    }

    private final ElectronicsPlugin plugin;
    private final Map<String, Recipe> registered = new HashMap<>();
    private final List<NamespacedKey> keys = new ArrayList<>();

    public Recipes(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Ингредиенты id как мультимножество — по ним паяльный стол сверяет, что
     * игрок положил в сетку заготовок (и что списать при успехе).
     */
    public static Map<Material, Integer> ingredientsOf(String id) {
        Map<Material, Integer> out = new java.util.LinkedHashMap<>();
        Object spec = DEFS.get(id);
        if (spec instanceof Shaped sh) {
            for (String row : sh.pattern()) {
                for (int i = 0; i < row.length(); i++) {
                    Material m = sh.key().get(row.charAt(i));
                    if (m != null && m != Material.AIR) {
                        out.merge(m, 1, Integer::sum);
                    }
                }
            }
        } else if (spec instanceof Shapeless sl) {
            for (Material m : sl.ingredients()) {
                if (m != null && m.isItem()) {
                    out.merge(m, 1, Integer::sum);
                }
            }
        }
        return out;
    }

    private static Map<Character, Material> map(Object... pairs) {
        Map<Character, Material> m = new HashMap<>();
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            m.put(((String) pairs[i]).charAt(0), (Material) pairs[i + 1]);
        }
        return m;
    }

    private static final Map<String, Object> DEFS = new HashMap<>();

    static {
        // id -> Shaped/Shapeless
        DEFS.put("bench", new Shapeless(List.of(Material.SMOOTH_QUARTZ_SLAB, Material.REPEATER)));
        DEFS.put("breadboard", new Shaped(new String[]{"HRH", "RRR", "RSR"},
                map("H", Material.HOPPER, "R", Material.REPEATER, "S", Material.REDSTONE)));
        DEFS.put("wire_red", new Shapeless(List.of(Material.STRING, Material.COPPER_INGOT, Material.REDSTONE)));
        DEFS.put("wire_black", new Shapeless(List.of(Material.STRING, Material.COPPER_INGOT, Material.INK_SAC)));
        DEFS.put("wire_yellow", new Shapeless(List.of(Material.STRING, Material.COPPER_INGOT, Material.YELLOW_DYE)));
        DEFS.put("wire_blue", new Shapeless(List.of(Material.STRING, Material.COPPER_INGOT, Material.LAPIS_LAZULI)));
        DEFS.put("battery_aa", new Shaped(new String[]{"I", "R", "G"},
                map("I", Material.IRON_NUGGET, "R", Material.REDSTONE, "G", Material.GOLD_NUGGET)));
        DEFS.put("battery_3", new Shapeless(List.of(Material.COPPER_INGOT, Material.COPPER_INGOT, Material.HONEYCOMB)));
        DEFS.put("battery_9", new Shaped(new String[]{"GGG", "CRC", "GGG"},
                map("G", Material.GOLD_NUGGET, "C", Material.COPPER_INGOT, "R", Material.REDSTONE)));
        DEFS.put("power_supply", new Shaped(new String[]{"CGC", "RBR", "IRI"},
                map("C", Material.COPPER_INGOT, "G", Material.GLASS, "B", Material.COPPER_BULB,
                        "R", Material.REPEATER, "I", Material.IRON_INGOT)));
        DEFS.put("solar_cell", new Shaped(new String[]{"GGG", "QLQ", "GIG"},
                map("G", Material.TINTED_GLASS, "Q", Material.QUARTZ, "L", Material.LAPIS_BLOCK, "I", Material.IRON_INGOT)));
        DEFS.put("generator", new Shaped(new String[]{"ICI", "RPR", "ICI"},
                map("I", Material.IRON_INGOT, "C", Material.COPPER_INGOT, "P", Material.STICKY_PISTON, "R", Material.REPEATER)));
        DEFS.put("ground_bus", new Shaped(new String[]{"CCC", "GRG", "CCC"},
                map("C", Material.COPPER_BLOCK, "G", Material.LIGHTNING_ROD, "R", Material.REDSTONE)));
        DEFS.put("resistor_10r", new Shapeless(List.of(Material.COPPER_INGOT, Material.COAL)));
        DEFS.put("resistor_100r", new Shapeless(List.of(Material.COPPER_INGOT, Material.CHARCOAL)));
        DEFS.put("resistor_220r", new Shapeless(List.of(Material.COPPER_INGOT, Material.NETHER_BRICK)));
        DEFS.put("resistor_330r", new Shapeless(List.of(Material.COPPER_INGOT, Material.BRICK)));
        DEFS.put("resistor_1k", new Shapeless(List.of(Material.COPPER_INGOT, Material.CLAY)));
        DEFS.put("resistor_4k7", new Shapeless(List.of(Material.COPPER_INGOT, Material.TERRACOTTA)));
        DEFS.put("resistor_10k", new Shapeless(List.of(Material.COPPER_INGOT, Material.AMETHYST_SHARD)));
        DEFS.put("resistor_100k", new Shapeless(List.of(Material.COPPER_INGOT, Material.QUARTZ)));
        DEFS.put("magnet", new Shaped(new String[]{"IRI"},
                map("I", Material.IRON_INGOT, "R", Material.REDSTONE_BLOCK)));
        DEFS.put("electromagnet", new Shaped(new String[]{" C ", "CIC", " C "},
                map("C", Material.COPPER_INGOT, "I", Material.IRON_INGOT)));
        DEFS.put("inductor_10mh", new Shaped(new String[]{"S", "I", "S"},
                map("S", Material.STRING, "I", Material.IRON_NUGGET)));
        DEFS.put("choke_10h", new Shaped(new String[]{"CCC", "CIC", " C "},
                map("C", Material.COPPER_INGOT, "I", Material.IRON_BLOCK)));
        DEFS.put("relay_5v", new Shaped(new String[]{"CR", "RI", "CG"},
                map("C", Material.COPPER_INGOT, "R", Material.REDSTONE, "I", Material.IRON_INGOT,
                        "G", Material.GOLD_NUGGET)));
        DEFS.put("reed_switch", new Shaped(new String[]{"G", "I", "G"},
                map("G", Material.GLASS, "I", Material.IRON_NUGGET)));
        DEFS.put("photoresistor", new Shaped(new String[]{"G", "S", "C"},
                map("G", Material.GLASS, "S", Material.GLOWSTONE_DUST, "C", Material.COPPER_INGOT)));
        DEFS.put("thermistor_ntc", new Shapeless(List.of(Material.CLAY_BALL, Material.COPPER_INGOT,
                Material.COPPER_INGOT, Material.REDSTONE)));
        DEFS.put("soldering_station", new Shaped(new String[]{"P P", "PFP", "ICI"},
                map("P", Material.OAK_PLANKS, "F", Material.FURNACE,
                        "I", Material.IRON_NUGGET, "C", Material.COPPER_INGOT)));
        DEFS.put("soldering_iron", new Shaped(new String[]{"  C", " I ", "S  "},
                map("C", Material.COPPER_INGOT, "I", Material.IRON_NUGGET, "S", Material.STICK)));
        DEFS.put("solder", new Shapeless(List.of(Material.IRON_NUGGET, Material.IRON_NUGGET,
                Material.FLINT)));
        DEFS.put("flux", new Shapeless(List.of(Material.GLOWSTONE_DUST, Material.HONEY_BOTTLE)));
        DEFS.put("potentiometer", new Shaped(new String[]{" R ", "RCR", " I "},
                map("R", Material.RESIN_CLUMP, "C", Material.COPPER_INGOT, "I", Material.IRON_NUGGET)));
        DEFS.put("cap_100nf", new Shapeless(List.of(Material.COPPER_INGOT, Material.PAPER)));
        DEFS.put("cap_100uf", new Shaped(new String[]{"P", "C", "P"},
                map("P", Material.PAPER, "C", Material.COPPER_INGOT)));
        DEFS.put("cap_1000uf", new Shaped(new String[]{"PCP"},
                map("P", Material.PAPER, "C", Material.COPPER_INGOT)));
        DEFS.put("diode", new Shaped(new String[]{"CR"},
                map("C", Material.COPPER_INGOT, "R", Material.REDSTONE)));
        DEFS.put("led_red", new Shaped(new String[]{"G", "R", "C"},
                map("G", Material.GLASS, "R", Material.REDSTONE, "C", Material.COPPER_INGOT)));
        DEFS.put("led_green", new Shaped(new String[]{"G", "V", "C"},
                map("G", Material.GLASS, "V", Material.LIME_DYE, "C", Material.COPPER_INGOT)));
        DEFS.put("led_blue", new Shaped(new String[]{"G", "B", "C"},
                map("G", Material.GLASS, "B", Material.LAPIS_LAZULI, "C", Material.COPPER_INGOT)));
        DEFS.put("led_yellow", new Shaped(new String[]{"G", "Y", "C"},
                map("G", Material.GLASS, "Y", Material.YELLOW_DYE, "C", Material.COPPER_INGOT)));
        DEFS.put("led_white", new Shaped(new String[]{"G", "Q", "C"},
                map("G", Material.GLASS, "Q", Material.QUARTZ, "C", Material.COPPER_INGOT)));
        DEFS.put("lamp", new Shaped(new String[]{" G ", "GCG", " R "},
                map("G", Material.GLASS, "C", Material.COPPER_INGOT, "R", Material.REDSTONE)));
        DEFS.put("button", new Shaped(new String[]{"R", "S"},
                map("R", Material.REDSTONE, "S", Material.STONE_PRESSURE_PLATE)));
        DEFS.put("switch_spst", new Shaped(new String[]{"L", "R", "S"},
                map("L", Material.LEVER, "R", Material.REDSTONE, "S", Material.STONE_BUTTON)));
        DEFS.put("switch_spdt", new Shaped(new String[]{"LL", "RS", "SS"},
                map("L", Material.LEVER, "R", Material.REDSTONE, "S", Material.STONE_BUTTON)));
        DEFS.put("transistor_npn", new Shaped(new String[]{"R R", "GSG"},
                map("R", Material.REDSTONE, "G", Material.GOLD_NUGGET, "S", Material.SAND)));
        DEFS.put("transistor_pnp", new Shaped(new String[]{"R R", "ISI"},
                map("R", Material.REDSTONE, "I", Material.IRON_NUGGET, "S", Material.SAND)));
        DEFS.put("gate_and", new Shaped(new String[]{"RSR", "SCS", "RSR"},
                map("R", Material.REDSTONE, "S", Material.SAND, "C", Material.COPPER_INGOT)));
        DEFS.put("gate_or", new Shaped(new String[]{"R R", "SCS", "R R"},
                map("R", Material.REDSTONE, "S", Material.SAND, "C", Material.COPPER_INGOT)));
        DEFS.put("gate_xor", new Shaped(new String[]{"RSR", "SCS", "RSR"},
                map("R", Material.REDSTONE, "S", Material.SAND, "C", Material.GOLD_NUGGET)));
        DEFS.put("gate_not", new Shaped(new String[]{" R ", "R R", " C "},
                map("R", Material.REDSTONE, "C", Material.COPPER_INGOT)));
        DEFS.put("comparator", new Shaped(new String[]{"AG", " C", "GA"},
                map("A", Material.AMETHYST_SHARD, "G", Material.GLASS, "C", Material.COPPER_INGOT)));
        DEFS.put("buzzer", new Shaped(new String[]{" C ", "CGC", " R "},
                map("C", Material.COPPER_INGOT, "G", Material.GOLD_NUGGET, "R", Material.REDSTONE_BLOCK)));
        DEFS.put("motor", new Shaped(new String[]{" C ", "CRC", " I "},
                map("C", Material.COPPER_INGOT, "R", Material.REDSTONE, "I", Material.IRON_INGOT)));
        DEFS.put("fuse", new Shaped(new String[]{"GC", "CR", "CG"},
                map("G", Material.GLASS, "C", Material.COPPER_INGOT, "R", Material.REDSTONE)));
        DEFS.put("voltmeter", new Shaped(new String[]{"GCG", "IRI", " C "},
                map("G", Material.GLASS, "C", Material.COPPER_INGOT, "I", Material.IRON_INGOT, "R", Material.REDSTONE)));
        DEFS.put("ammeter", new Shaped(new String[]{"GCG", "ILI", " C "},
                map("G", Material.GLASS, "C", Material.COPPER_INGOT, "I", Material.IRON_INGOT, "L", Material.LAPIS_LAZULI)));
        DEFS.put("multimeter", new Shaped(new String[]{"GIG", "CRC", "WIW"},
                map("G", Material.GLASS, "I", Material.IRON_INGOT, "C", Material.COPPER_INGOT,
                        "R", Material.REDSTONE, "W", Material.CLOCK)));
        DEFS.put("wire_cutter", new Shaped(new String[]{"I I", " S ", " S "},
                map("I", Material.IRON_INGOT, "S", Material.STICK)));
        DEFS.put("led_probe", new Shaped(new String[]{"G", "R", "S"},
                map("G", Material.GLASS, "R", Material.REDSTONE, "S", Material.STICK)));
        DEFS.put("schematic", new Shaped(new String[]{"PPP", "PRP", "PIP"},
                map("P", Material.PAPER, "R", Material.REDSTONE, "I", Material.COPPER_INGOT)));
        DEFS.put("empty_circuit", new Shaped(new String[]{"GRG", "RPR", "GRG"},
                map("G", Material.GOLD_NUGGET, "R", Material.REDSTONE, "P", Material.PAPER)));
    }

    public void register() {
        unregisterAll();
        if (!plugin.config().recipesEnabled) {
            plugin.getLogger().info("рецепты отключены (world.recipes: false)");
            return;
        }
        int ok = 0;
        int skip = 0;
        int soldered = 0;
        for (Def d : Components.all()) {
            Object spec = DEFS.get(d.id);
            if (spec == null) {
                skip++;
                continue;
            }
            if (Components.isSoldered(d.id)) {
                soldered++;          // только пайкой на столе: ванильного рецепта нет
                continue;
            }
            ItemStack result = plugin.items().componentItem(d, 1);
            NamespacedKey key = new NamespacedKey(plugin, "recipe_" + d.id);
            try {
                Recipe recipe;
                if (spec instanceof Shaped sh) {
                    ShapedRecipe r = new ShapedRecipe(key, result);
                    r.shape(sh.pattern());
                    for (Map.Entry<Character, Material> e : sh.key().entrySet()) {
                        r.setIngredient(e.getKey(), e.getValue());
                    }
                    recipe = r;
                } else {
                    ShapelessRecipe r = new ShapelessRecipe(key, result);
                    for (Material m : ((Shapeless) spec).ingredients()) {
                        if (m != null && m.isItem()) {
                            r.addIngredient(m);
                        }
                    }
                    recipe = r;
                }
                registerRecipe(recipe, key);
                ok++;
            } catch (RuntimeException ex) {
                skip++;
                if (plugin.config().debug) {
                    plugin.getLogger().warning("рецепт " + d.id + ": " + ex.getMessage());
                }
            }
        }
        plugin.getLogger().info("зарегистрировано рецептов: " + ok + " (пайка: " + soldered
                + ", пропущено " + skip + ")");
    }

    private void registerRecipe(Recipe recipe, NamespacedKey key) {
        if (plugin.getServer().addRecipe(recipe)) {
            registered.put(key.getKey(), recipe);
            keys.add(key);
        }
    }

    public void unregisterAll() {
        for (NamespacedKey key : keys) {
            try {
                plugin.getServer().removeRecipe(key);
            } catch (RuntimeException | Error ignored) {
                // API могло измениться — просто перерегистрируем поверх
            }
        }
        registered.clear();
        keys.clear();
    }

    public Recipe byResult(Def def) {
        return registered.get("recipe_" + def.id);
    }

    public int count() {
        return registered.size();
    }

    public List<String> missing() {
        List<String> out = new ArrayList<>();
        for (Def d : Components.all()) {
            if (!DEFS.containsKey(d.id)) {
                out.add(d.id);
            }
        }
        return out;
    }

    public boolean has(String id) {
        return DEFS.containsKey(id.toLowerCase(Locale.ROOT));
    }
}
