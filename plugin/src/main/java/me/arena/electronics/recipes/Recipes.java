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
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
    /**
     * «Витрина» для JEI/EMI/REI: рецепты спаечных деталей, которые сервер не
     * даёт собрать (CraftItemEvent отменяется), но клиент обязан показать —
     * иначе в JEI у детали вообще нет рецепта. Ключ — recipe_solder_<id>.
     */
    private final Set<String> displayIds = new java.util.LinkedHashSet<>();
    private final Set<NamespacedKey> displayKeys = new HashSet<>();
    private int okCount = 0;
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
        DEFS.put("solar_cell", new Shaped(new String[]{" GG", "QLQ", "GIG"},
                map("G", Material.TINTED_GLASS, "Q", Material.QUARTZ, "L", Material.LAPIS_BLOCK, "I", Material.IRON_INGOT)));
        DEFS.put("source_ac", new Shaped(new String[]{"GCG", "RIR", "G G"},
                map("G", Material.GOLD_NUGGET, "C", Material.COPPER_INGOT, "R", Material.REDSTONE,
                        "I", Material.IRON_INGOT)));
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
        DEFS.put("gate_and", new Shaped(new String[]{"RSR", "SCS", "R R"},
                map("R", Material.REDSTONE, "S", Material.SAND, "C", Material.COPPER_INGOT)));
        DEFS.put("gate_or", new Shaped(new String[]{"R R", "SCS", "R R"},
                map("R", Material.REDSTONE, "S", Material.SAND, "C", Material.COPPER_INGOT)));
        DEFS.put("gate_xor", new Shaped(new String[]{"RSR", "SCS", "R R"},
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

        // --- защита, выпрямление, стабилизация и трансформация (v1.3) ---
        DEFS.put("fuse_cartridge_1a", new Shaped(new String[]{"I I", "CGC", "I I"},
                map("I", Material.IRON_NUGGET, "C", Material.COPPER_INGOT, "G", Material.GLASS)));
        DEFS.put("fuse_cartridge_5a", new Shaped(new String[]{"I I", "CCC", "I I"},
                map("I", Material.IRON_NUGGET, "C", Material.COPPER_INGOT)));
        DEFS.put("fuse_slow_2a", new Shaped(new String[]{"I I", "CSC", "I I"},
                map("I", Material.IRON_NUGGET, "C", Material.COPPER_INGOT, "S", Material.SAND)));
        DEFS.put("ptc_500ma", new Shapeless(List.of(Material.CLAY_BALL, Material.COAL,
                Material.COPPER_INGOT, Material.COPPER_INGOT)));
        DEFS.put("mov_275v", new Shaped(new String[]{" C ", "CRC", " C "},
                map("C", Material.COPPER_INGOT, "R", Material.REDSTONE)));
        DEFS.put("bridge_kbu8", new Shaped(new String[]{"DRD", "R R", "DRD"},
                map("D", Material.GOLD_NUGGET, "R", Material.REDSTONE)));
        DEFS.put("shottky_sbr30", new Shapeless(List.of(Material.COPPER_INGOT, Material.QUARTZ,
                Material.REDSTONE)));
        DEFS.put("cap_elec_2200uf", new Shaped(new String[]{"PCP", "C C", "PCP"},
                map("P", Material.PAPER, "C", Material.COPPER_INGOT)));
        DEFS.put("reg_7805", new Shaped(new String[]{"III", "GSG", "C C"},
                map("I", Material.IRON_NUGGET, "G", Material.GOLD_NUGGET, "S", Material.SAND,
                        "C", Material.COPPER_INGOT)));
        DEFS.put("reg_7812", new Shaped(new String[]{"III", "GRG", "C C"},
                map("I", Material.IRON_NUGGET, "G", Material.GOLD_NUGGET, "R", Material.REDSTONE,
                        "C", Material.COPPER_INGOT)));
        DEFS.put("reg_lm317", new Shaped(new String[]{"I I", "GSG", "CRC"},
                map("I", Material.IRON_NUGGET, "G", Material.GOLD_NUGGET, "S", Material.SAND,
                        "R", Material.REDSTONE, "C", Material.COPPER_INGOT)));
        DEFS.put("xfmr_230_12", new Shaped(new String[]{"CIC", "I I", "CIC"},
                map("C", Material.COPPER_INGOT, "I", Material.IRON_INGOT)));
        DEFS.put("xfmr_230_9", new Shaped(new String[]{"CIC", "I I", "C C"},
                map("C", Material.COPPER_INGOT, "I", Material.IRON_INGOT)));
        // станочные заготовки рудной цепочки: ванильная часть плюс наши детали
        // (список ниже читает паяльный стол; датапак эти id не получает, они
        //  в meta.soldered, а ExactChoice в JSON не выразить)
        DEFS.put("wire_bare", new Shapeless(List.of(Material.STRING, Material.STRING)));
        DEFS.put("battery_zinc", new Shapeless(List.of(Material.COAL, Material.STRING)));
        DEFS.put("mcu_atmega328", new Shapeless(List.of(Material.QUARTZ, Material.GOLD_NUGGET)));
        // перед лужением провод обдирают абразивом, иначе припой ляжет шариком;
        // сам припой и голый провод стол спишет отдельно (наши предметы)
        DEFS.put("wire_tinned", new Shapeless(List.of(Material.SAND)));
        // разведка недр и зачистка: эти не паяют, их собирают руками
        DEFS.put("crystal_16mhz", new Shapeless(List.of(Material.QUARTZ, Material.QUARTZ,
                Material.GOLD_NUGGET)));
        DEFS.put("geoprobe", new Shaped(new String[]{"Q", "I", "S"},
                map("Q", Material.QUARTZ, "I", Material.IRON_INGOT, "S", Material.STICK)));
        DEFS.put("wire_stripper", new Shaped(new String[]{"SI", "CF"},
                map("S", Material.SHEARS, "I", Material.IRON_INGOT,
                        "C", Material.COPPER_INGOT, "F", Material.FLINT)));
        DEFS.put("programmer_usb", new Shaped(new String[]{"IG", "CR"},
                map("I", Material.IRON_NUGGET, "G", Material.GOLD_NUGGET,
                        "C", Material.COPPER_INGOT, "R", Material.REPEATER)));
        // металлургия: станки и рудный концентрат собираются в верстаке из
        // ванильного — это и есть второй путь к руде, кроме недр
        DEFS.put("crucible_station", new Shaped(new String[]{"B B", "BLB", "IFI"},
                map("B", Material.BRICKS, "L", Material.LAVA_BUCKET, "I", Material.IRON_INGOT,
                        "F", Material.FURNACE)));
        DEFS.put("mill_station", new Shaped(new String[]{"IRI", "CPC", "IRI"},
                map("I", Material.IRON_INGOT, "R", Material.REDSTONE_BLOCK,
                        "C", Material.COPPER_INGOT, "P", Material.PISTON)));
        DEFS.put("raw_copper_chunk", new Shapeless(List.of(Material.COPPER_INGOT,
                Material.COPPER_INGOT, Material.CALCITE)));
        DEFS.put("raw_tin_chunk", new Shapeless(List.of(Material.IRON_NUGGET, Material.IRON_NUGGET,
                Material.IRON_NUGGET, Material.IRON_NUGGET, Material.QUARTZ, Material.CALCITE)));
        DEFS.put("raw_zinc_chunk", new Shapeless(List.of(Material.IRON_NUGGET, Material.IRON_NUGGET,
                Material.COAL, Material.GUNPOWDER, Material.CALCITE)));
        DEFS.put("resistor_shunt_0r01", new Shapeless(List.of(Material.GOLD_NUGGET)));
        DEFS.put("desolder_braid", new Shapeless(List.of(Material.STRING, Material.STRING,
                Material.COPPER_INGOT)));
        DEFS.put("voltmeter", new Shaped(new String[]{"GCG", "IRI", " C "},
                map("G", Material.GLASS, "C", Material.COPPER_INGOT, "I", Material.IRON_INGOT, "R", Material.REDSTONE)));
        DEFS.put("ammeter", new Shaped(new String[]{"GCG", "ILI", " C "},
                map("G", Material.GLASS, "C", Material.COPPER_INGOT, "I", Material.IRON_INGOT, "L", Material.LAPIS_LAZULI)));
        DEFS.put("multimeter", new Shaped(new String[]{"GIG", "CRC", "W I"},
                map("G", Material.GLASS, "I", Material.IRON_INGOT, "C", Material.COPPER_INGOT,
                        "R", Material.REDSTONE, "W", Material.CLOCK)));
        // Осциллограф паяется, поэтому DEFS нужен только чтобы витрина JEI и
        // требования стола совпадали: ingredientsOf() читает эту же форму.
        // Дырка внизу по центру — под паяльный стол (used + 1 <= 9).
        DEFS.put("oscilloscope", new Shaped(new String[]{"GIG", "CRC", "R R"},
                map("G", Material.GLASS, "I", Material.IRON_INGOT, "C", Material.COPPER_INGOT,
                        "R", Material.REDSTONE)));
        DEFS.put("wire_cutter", new Shaped(new String[]{"I I", " S ", " S "},
                map("I", Material.IRON_INGOT, "S", Material.STICK)));
        DEFS.put("led_probe", new Shaped(new String[]{"G", "R", "S"},
                map("G", Material.GLASS, "R", Material.REDSTONE, "S", Material.STICK)));
        DEFS.put("schematic", new Shaped(new String[]{"PPP", "PRP", "PIP"},
                map("P", Material.PAPER, "R", Material.REDSTONE, "I", Material.COPPER_INGOT)));
        DEFS.put("empty_circuit", new Shaped(new String[]{"GRG", "RPR", "GRG"},
                map("G", Material.GOLD_NUGGET, "R", Material.REDSTONE, "P", Material.PAPER)));

        // ---- привод (v1.4.0): механика, которая живёт от тока
        // Кварц и стекло — изоляторы вводной колодки, реле — контактор, железо — корпус.
        DEFS.put("supply_220v", new Shaped(new String[]{"QGQ", "CRC", "IRI"},
                map("Q", Material.QUARTZ_BLOCK, "G", Material.GLASS, "C", Material.COPPER_INGOT,
                        "R", Material.REPEATER, "I", Material.IRON_BLOCK)));
        // Медленный предохранитель на 10 А: толстая вставка в керамике — пусковой ток
        // мотора её выдерживает, а заклинивание — нет.
        DEFS.put("fuse_slow_10a", new Shaped(new String[]{"I I", "CBC", "I I"},
                map("I", Material.IRON_NUGGET, "C", Material.COPPER_INGOT, "B", Material.CLAY)));
        // Вал — пруток: три куска железа в линию, это и есть прокатка.
        DEFS.put("shaft_kinetic", new Shaped(new String[]{"III"},
                map("I", Material.IRON_INGOT)));
        // Колесо: обод из железа, ступица — медная втулка, ось — палка.
        DEFS.put("wheel_kinetic", new Shapeless(List.of(Material.IRON_INGOT, Material.IRON_INGOT,
                Material.COPPER_INGOT, Material.STICK)));
        // Заготовки для пайки: то, что приносишь на стол (дальше — свои металлы).
        DEFS.put("motor_220v", new Shaped(new String[]{" I ", "CRC", " I "},
                map("I", Material.IRON_INGOT, "C", Material.COPPER_INGOT, "R", Material.REDSTONE)));
        DEFS.put("gearbox_3x", new Shaped(new String[]{"IR", "RI"},
                map("I", Material.IRON_NUGGET, "R", Material.REDSTONE)));
        DEFS.put("flywheel_kinetic", new Shaped(new String[]{"III", "I I", "III"},
                map("I", Material.IRON_NUGGET)));
        DEFS.put("saw_kinetic", new Shaped(new String[]{" P ", "PSP", " P "},
                map("P", Material.IRON_NUGGET, "S", Material.STICK)));
        DEFS.put("drill_kinetic", new Shaped(new String[]{"I", "S", "I"},
                map("I", Material.IRON_INGOT, "S", Material.STONE)));
    }

    private int smeltCount;

    public void register() {
        unregisterAll();
        smeltCount = 0;
        if (!plugin.config().recipesEnabled) {
            plugin.getLogger().info("рецепты отключены (world.recipes: false)");
            return;
        }
        int ok = 0;
        int skip = 0;
        okCount = 0;
        int soldered = 0;
        for (Def d : Components.all()) {
            Object spec = DEFS.get(d.id);
            if (spec == null) {
                skip++;
                continue;
            }
            if (Components.isSoldered(d.id)) {
                // ванильного рецепта нет намеренно; для JEI регистрируем витрину,
                // где показана заготовка и сам паяльный стол как «станок»
                NamespacedKey dk = new NamespacedKey(plugin, "recipe_solder_" + d.id);
                try {
                    Recipe display = buildDisplay(d, dk);
                    if (display != null && registerRecipe(display, dk)) {
                        displayKeys.add(dk);
                        displayIds.add(d.id);
                    }
                } catch (RuntimeException ex) {
                    if (plugin.config().debug) {
                        plugin.getLogger().warning("витрина для " + d.id + ": " + ex.getMessage());
                    }
                }
                soldered++;
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
                if (registerRecipe(recipe, key)) {
                    ok++;
                    okCount++;
                }
            } catch (RuntimeException ex) {
                skip++;
                if (plugin.config().debug) {
                    plugin.getLogger().warning("рецепт " + d.id + ": " + ex.getMessage());
                }
            }
        }
        int smelt = registerSmelting();
        ok += smelt;
        okCount += smelt;
        smeltCount = smelt;
        plugin.getLogger().info("зарегистрировано рецептов: " + ok + " (пайка: " + soldered
                + " витрин для JEI, пропущено " + skip + ", плавка " + smelt + ")");
    }

    /**
     * Плавка кусков руды в слитки. Единственные рецепты, где результат — наш
     * предмет: печь сравнивает ExactChoice по полному стеку (вместе с PDC), а
     * витрина JEI таким быть не умеет, поэтому её для этих трёх не делаем и
     * датапак тоже (в JSON нельзя описать наш NBT).
     */
    private int registerSmelting() {
        int n = 0;
        String[][] chain = {{"raw_copper_chunk", "ingot_copper"},
                {"raw_tin_chunk", "ingot_tin"}, {"raw_zinc_chunk", "ingot_zinc"}};
        for (String[] pair : chain) {
            Def in = Components.byId(pair[0]);
            Def out = Components.byId(pair[1]);
            if (in == null || out == null) {
                continue;
            }
            NamespacedKey key = new NamespacedKey(plugin, "smelt_" + pair[1]);
            try {
                org.bukkit.inventory.Recipe r = new org.bukkit.inventory.FurnaceRecipe(key,
                        plugin.items().componentItem(out, 1),
                        new org.bukkit.inventory.RecipeChoice.ExactChoice(
                                plugin.items().componentItem(in, 2)),
                        0.35f, 150);
                if (registerRecipe(r, key)) {
                    n++;
                }
            } catch (RuntimeException ex) {
                if (plugin.config().debug) {
                    plugin.getLogger().warning("плавка " + pair[1] + ": " + ex.getMessage());
                }
            }
        }
        return n;
    }

    /** Сколько рецептов плавки висит (3 = медь, олово, цинк). */
    public int smeltCount() {
        return smeltCount;
    }

    /** Сколько «настоящих» рецептов зарегистрировано (без витрин). */
    public int realCount() {
        return okCount;
    }

    /** Сам витринный рецепт (его видно в JEI; собран быть не может). */
    public Recipe displayFor(String id) {
        return registered.get("recipe_solder_" + id);
    }

    /** Есть ли у id «витрина»-рецепт (и, значит, что-то ли показывает JEI). */
    public boolean hasDisplay(String id) {
        return id != null && displayIds.contains(id);
    }

    public int displayCount() {
        return displayIds.size();
    }

    /** Отменяется ли крафт этим рецептом (витрина не собираётся руками). */
    public boolean isDisplayOnly(Recipe recipe) {
        if (!(recipe instanceof org.bukkit.Keyed keyed) || keyed.getKey() == null) {
            return false;
        }
        return displayKeys.contains(keyed.getKey());
    }

    /** Паяльный стол как предмет-ингредиент (ExactChoice: наш предмет, не просто полублок). */
    private org.bukkit.inventory.RecipeChoice stationChoice() {
        Def st = Components.byId("soldering_station");
        if (st == null) {
            return null;
        }
        return new org.bukkit.inventory.RecipeChoice.ExactChoice(plugin.items().componentItem(st, 1));
    }

    /**
     * Рецепт «как это делается» для JEI: те же заготовки, что требует стол, плюс
     * сам паяльный стол в свободной клетке сетки (если клетка есть — иначе его
     * видно в подсказке результата). Собран быть не может: Events отменяет его.
     */
    private Recipe buildDisplay(Def d, NamespacedKey key) {
        Object spec = DEFS.get(d.id);
        ItemStack result = plugin.items().componentItem(d, 1);
        if (spec instanceof Shaped sh) {
            String[] rows = sh.pattern();
            char[][] grid = new char[3][3];
            for (char[] row : grid) {
                java.util.Arrays.fill(row, ' ');
            }
            for (int r = 0; r < Math.min(3, rows.length); r++) {
                for (int c = 0; c < Math.min(3, rows[r].length()); c++) {
                    grid[r][c] = rows[r].charAt(c);
                }
            }
            int used = 0;
            for (char[] row : grid) {
                for (char ch : row) {
                    if (ch != ' ' && sh.key().get(ch) != null && sh.key().get(ch) != Material.AIR) {
                        used++;
                    }
                }
            }
            boolean stationFits = used + 1 <= 9;
            if (stationFits) {
                outer:
                for (int r = 0; r < 3; r++) {
                    for (int c = 0; c < 3; c++) {
                        if (grid[r][c] == ' ') {
                            grid[r][c] = 'K';
                            break outer;
                        }
                    }
                }
            }
            int maxR = 0, maxC = 0;
            for (int r = 0; r < 3; r++) {
                for (int c = 0; c < 3; c++) {
                    if (grid[r][c] != ' ') {
                        maxR = Math.max(maxR, r);
                        maxC = Math.max(maxC, c);
                    }
                }
            }
            String[] shape = new String[maxR + 1];
            for (int r = 0; r <= maxR; r++) {
                shape[r] = new String(grid[r]).substring(0, maxC + 1);
            }
            ShapedRecipe out = new ShapedRecipe(key, result);
            out.shape(shape);
            for (Map.Entry<Character, Material> e : sh.key().entrySet()) {
                if (e.getValue() != null && e.getValue() != Material.AIR) {
                    out.setIngredient(e.getKey(), e.getValue());
                }
            }
            org.bukkit.inventory.RecipeChoice st = stationChoice();
            if (stationFits && st != null) {
                out.setIngredient('K', st);
            }
            return out;
        }
        if (spec instanceof Shapeless sl) {
            java.util.List<Material> list = new ArrayList<>();
            for (Material m : sl.ingredients()) {
                if (m != null && m.isItem()) {
                    list.add(m);
                }
            }
            boolean stationFits = list.size() + 1 <= 9;
            if (stationFits) {
                list.add(null);                      // место под стол, вставим ниже
            }
            ShapelessRecipe out = new ShapelessRecipe(key, result);
            int idx = 0;
            for (Material m : list) {
                if (m == null) {
                    org.bukkit.inventory.RecipeChoice st = stationChoice();
                    if (st != null) {
                        out.addIngredient(st);
                    }
                } else {
                    out.addIngredient(m);
                }
                idx++;
            }
            return out;
        }
        return null;
    }

    private boolean registerRecipe(Recipe recipe, NamespacedKey key) {
        if (plugin.getServer().addRecipe(recipe)) {
            registered.put(key.getKey(), recipe);
            keys.add(key);
            return true;
        }
        return false;
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
        displayIds.clear();
        displayKeys.clear();
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
