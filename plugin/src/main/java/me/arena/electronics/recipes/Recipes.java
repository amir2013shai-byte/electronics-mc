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

    /**
     * Таблица рецептов НЕ дублирует генератор: она собирается из реестра
     * (shared/components.json, поле craft) — см. {@link #loadFromRegistry()}.
     */
    private static final Map<String, Object> DEFS = new HashMap<>();

    /**minecraft item id -> Material (через реестр, чтобы не угадывать имена). */
    private static Material itemOf(String id) {
        String key = id.toLowerCase(java.util.Locale.ROOT);
        try {
            // в 1.21.9+ реестр предметов отдаёт ItemType, а не Material
            org.bukkit.inventory.ItemType t = org.bukkit.Registry.ITEM
                    .get(new NamespacedKey("minecraft", key));
            if (t != null) {
                Material m = Material.matchMaterial(t.getKey().getKey()
                        .toUpperCase(java.util.Locale.ROOT));
                if (m != null && m.isItem()) {
                    return m;
                }
            }
        } catch (RuntimeException ignored) {
            // реестра нет (тест вне сервера) — пробуем по имени
        }
        Material m = Material.matchMaterial(key.toUpperCase(java.util.Locale.ROOT));
        return m != null && m.isItem() ? m : null;
    }

    /**
     * Заполняет DEFS из реестра. Возвращает число неразобранных ингредиентов —
     * если оно ненулевое, в лог идёт SEVERE, а `/elec selftest` это ловит.
     */
    public static int loadFromRegistry() {
        DEFS.clear();
        int bad = 0;
        for (Def d : Components.all()) {
            Map<String, Object> craft = d.craft;
            if (craft == null) {
                continue;
            }
            Object plain = craft.get("shapeless");
            if (plain instanceof Map) {
                List<Material> list = new ArrayList<>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) plain).entrySet()) {
                    Material m = itemOf(String.valueOf(e.getKey()));
                    int n = (int) Math.max(1, Math.round(toNum(e.getValue())));
                    if (m == null) {
                        bad++;
                        continue;
                    }
                    for (int i = 0; i < n; i++) {
                        list.add(m);
                    }
                }
                if (!list.isEmpty()) {
                    DEFS.put(d.id, new Shapeless(list));
                }
                continue;
            }
            Object pat = craft.get("pattern");
            Object key = craft.get("key");
            if (pat instanceof List && key instanceof Map) {
                Map<Character, Material> map = new HashMap<>();
                for (Map.Entry<?, ?> e : ((Map<?, ?>) key).entrySet()) {
                    Material m = itemOf(String.valueOf(e.getValue()));
                    if (m == null) {
                        bad++;
                        continue;
                    }
                    String k = String.valueOf(e.getKey());
                    if (!k.isEmpty()) {
                        map.put(k.charAt(0), m);
                    }
                }
                List<?> rows = (List<?>) pat;
                String[] pattern = new String[rows.size()];
                for (int i = 0; i < rows.size(); i++) {
                    pattern[i] = String.valueOf(rows.get(i));
                }
                DEFS.put(d.id, new Shaped(pattern, map));
            }
        }
        return bad;
    }

    private static double toNum(Object o) {
        if (o instanceof Number) {
            return ((Number) o).doubleValue();
        }
        try {
            return Double.parseDouble(String.valueOf(o));
        } catch (NumberFormatException e) {
            return 1;
        }
    }

    private int smeltCount;

    public void register() {
        unregisterAll();
        smeltCount = 0;
        int badCraft = loadFromRegistry();
        if (badCraft > 0) {
            plugin.getLogger().severe("рецепты: " + badCraft + " ингредиент(ов) из craft "
                    + "не найдены в реестре предметов — крафт этих деталей собран не будет "
                    +("(имя в shared/components.json -> components[].craft должно быть "
                    + "ванильным item id, например \"copper_ingot\")"));
        }
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
            // count из реестра: датапак и плагин обязаны отдавать одинаково
            ItemStack result = plugin.items().componentItem(d, Math.max(1, d.craftCount));
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

    /** Id-шники с крафтом в верстаке по нашей таблице — ровно их несёт и датапак. */
    public static java.util.Set<String> craftIds() {
        return DEFS.keySet();
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
