package me.arena.electronics.recipes;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Тигельная печь и прокатно-волочильный стан — вторая половина ремесла.
 *
 * <p>Почему это не обычные крафты: в верстаке нельзя смешать <em>наши</em>
 * предметы (они лежат в одном ванильном полублоке и различаются только
 * PDC/моделью), поэтому «рецепт с нашими ингредиентами» умеет исполнять только
 * плагин. {@link Soldering} — про пайку и тепловую технику; этот класс — про
 * металл: сплавить, вытянуть проволоку, обменять один концентрат на другой.</p>
 *
 * <p>Физика тут не для красоты, а как ограничение:</p>
 * <ul>
 *   <li>сплавы: бронза = Cu+Sn, латунь = Cu+Zn, и из любого тигля выходит
 *       {@code slag} — отвал, из которого потом что-то извлекают;</li>
 *   <li>обмен руд идёт <em>только в одну сторону</em> по ряду активности
 *       (Zn &gt; Sn &gt; Cu): более активный металл вытесняет менее активный,
 *       поэтому цементную медь дают цинк и олово, олово даёт цинк, а цинк —
 *       только возгонкой из полиметаллического концентрата, и то с потерями
 *       (4 куска на входе, 3 на выходе). Достать все три руды, имея одну,
 *       нельзя — и это правильно;</li>
 *   <li>волочение: слиток меди → 2 мотка эмальпровода, а лужение в ванне
 *       (олово + голый провод) даёт 3 лужёных против 1 на паяльном столе.</li>
 * </ul>
 *
 * <p>{@link #missing(Recipe, java.util.function.ToIntFunction)} — чистая
 * функция без Bukkit, её прогоняет {@code /elec selftest} и офлайн-тест.</p>
 */
public final class Metallurgy {

    public static final String CRUCIBLE_ID = "crucible_station";
    public static final String MILL_ID = "mill_station";

    /**
     * Одна операция стола.
     *
     * @param result что выдаём
     * @param need   что сжигаем (наши id → количество)
     * @param make   все выходные предметы, включая побочные (шлак)
     * @param note   на чем стоит физика — это видно игроку в подсказке
     */
    public record Recipe(String result, int count, Map<String, Integer> need,
                         Map<String, Integer> make, String note) {
    }

    private static final Map<String, Map<String, Recipe>> TABLES = new LinkedHashMap<>();

    private static void put(String station, Recipe r) {
        TABLES.computeIfAbsent(station, k -> new LinkedHashMap<>()).put(r.result(), r);
    }

    private static Map<String, Integer> mix(String a, int na) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(a, na);
        return m;
    }

    private static Map<String, Integer> mix(String a, int na, String b, int nb) {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(a, na);
        m.put(b, nb);
        return m;
    }

    static {
        // ---- тигель: сплавы, обмен руд, переработка отвала -----------------
        put(CRUCIBLE_ID, new Recipe("ingot_bronze", 1, mix("ingot_copper", 2, "ingot_tin", 1),
                mix("ingot_bronze", 1, "slag", 1),
                "бронза — 88% меди и 12% олова; тверже обеих, идёт на пружины контактов"));
        put(CRUCIBLE_ID, new Recipe("ingot_brass", 1, mix("ingot_copper", 2, "ingot_zinc", 1),
                mix("ingot_brass", 1, "slag", 1),
                "латунь — медь с цинком; её проще гнуть и она меньше искрит, поэтому из неё шина"));
        put(CRUCIBLE_ID, new Recipe("raw_copper_chunk", 2, mix("raw_zinc_chunk", 1, "raw_tin_chunk", 1),
                mix("raw_copper_chunk", 2, "slag", 1),
                "цементация: цинк и олово активнее меди (ряд активности Zn>Sn>Cu), "
                        + "оба растворяются, а медь садится на дно — 2 куска дают 2 куска и шлак"));
        put(CRUCIBLE_ID, new Recipe("raw_tin_chunk", 2, mix("raw_zinc_chunk", 2),
                mix("raw_tin_chunk", 2),
                "восстановление олова цинком: цинк активнее олова, обмен 1:1 — "
                        + "профита нет, но олово появляется там, где его не добывали"));
        put(CRUCIBLE_ID, new Recipe("raw_zinc_chunk", 3, mix("raw_copper_chunk", 2, "raw_tin_chunk", 2),
                mix("raw_zinc_chunk", 3),
                "возгонка: цинк в ряду активности самый левый, его нечем вытеснить — "
                        + "только восстановлением из штейбы с перегонкой, поэтому 4 куска в 3"));
        put(CRUCIBLE_ID, new Recipe("ingot_copper", 2, mix("slag", 3),
                mix("ingot_copper", 2),
                "переплавка отвала: в шлаке всегда остаются зёрна металла, "
                        + "3 куска шлака дают 2 слитка — вот зачем шлак носить с собой"));

        // ---- стан: волочение и лужение в ванне ------------------------------
        put(MILL_ID, new Recipe("wire_enamelled", 2, mix("ingot_copper", 1),
                mix("wire_enamelled", 2),
                "волочение: слиток протягивают через доски меньшего отверстия, "
                        + "получается 2 мотка тонкого провода в лаке (витки не коротят)"));
        put(MILL_ID, new Recipe("wire_tinned", 3, mix("wire_bare", 1, "ingot_tin", 1),
                mix("wire_tinned", 3),
                "горячее лужение в ванне: на столе это 1 деталь, здесь 3 — но нужна "
                        + "ванна олова, то есть слиток"));
        put(MILL_ID, new Recipe("wire_bare", 3, mix("ingot_copper", 1),
                mix("wire_bare", 3),
                "проволока из слитка идёт намотать обмотку, а не для монтажа: "
                        + "3 мотка из слитка против 1 детали на столе"));
    }

    private Metallurgy() {
    }

    /** Таблица стола (null — если на этом столе металл не плавят). */
    public static Map<String, Recipe> table(String stationId) {
        return stationId == null ? null : TABLES.get(stationId);
    }

    /** Все id, которые делают на металлургических столах (для подсказок и теста). */
    public static List<Recipe> recipesOf(String stationId) {
        Map<String, Recipe> t = table(stationId);
        return t == null ? List.of() : new ArrayList<>(t.values());
    }

    public static boolean isStation(String id) {
        return table(id) != null;
    }

    /** Сколько нужно наших предметов (для JEI/подсказки и для теста). */
    public static int totalNeed(Recipe r) {
        int n = 0;
        for (int v : r.need().values()) {
            n += v;
        }
        return n;
    }

    /** null — всё есть; иначе «принеси». Чистая функция: количество задаёт вызывающий. */
    public static String missing(Recipe r, java.util.function.ToIntFunction<String> have) {
        List<String> miss = new ArrayList<>();
        for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(r.need()).entrySet()) {
            int got = Math.max(0, have.applyAsInt(e.getKey()));
            if (got < e.getValue()) {
                Def d = Components.byId(e.getKey());
                miss.add((d == null ? e.getKey() : d.nameRu) + " ×" + (e.getValue() - got));
            }
        }
        return miss.isEmpty() ? null : "нужно: " + String.join(", ", miss);
    }

    /** Что выходит, включая шлак — строка для каталога и для подсказки в GUI. */
    public static String ingredientText(String id) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Map<String, Recipe>> t : TABLES.entrySet()) {
            Recipe r = t.getValue().get(id);
            if (r == null) {
                continue;
            }
            Def st = Components.byId(t.getKey());
            StringBuilder in = new StringBuilder();
            for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(r.need()).entrySet()) {
                Def d = Components.byId(e.getKey());
                if (in.length() > 0) {
                    in.append(" + ");
                }
                in.append(d == null ? e.getKey() : d.nameRu).append(" ×").append(e.getValue());
            }
            if (sb.length() > 0) {
                sb.append(" / ");
            }
            sb.append("&7на «").append(st == null ? t.getKey() : st.nameRu)
                    .append("&7»: &f").append(in).append(" &7→ &f").append(outputText(r));
        }
        return sb.toString();
    }

    /** human-readable «что выходит» (шлак видно сразу). */
    public static String outputText(Recipe r) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> e : r.make().entrySet()) {
            Def d = Components.byId(e.getKey());
            if (sb.length() > 0) {
                sb.append(" + ");
            }
            sb.append(d == null ? e.getKey() : d.nameRu).append(" ×").append(e.getValue());
        }
        return sb.toString();
    }

    public static String noteText(Recipe r) {
        return r.note();
    }

    /** Все id, которые вообще можно получить на металлургических столах. */
    public static java.util.Set<String> madeOnStations() {
        java.util.Set<String> out = new TreeSet<>();
        for (Map<String, Recipe> t : TABLES.values()) {
            out.addAll(t.keySet());
        }
        return out;
    }

    // ------------------------------------------------------------------ Bukkit

    public static int countOf(Player p, String ourId) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType() == Material.AIR) {
                continue;
            }
            Def d = Items.defOf(s);
            if (d != null && d.id.equals(ourId)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static void remove(Player p, String ourId, int amount) {
        int left = amount;
        ItemStack[] st = p.getInventory().getStorageContents();
        for (int i = 0; i < st.length && left > 0; i++) {
            ItemStack s = st[i];
            if (s == null || s.getType() == Material.AIR) {
                continue;
            }
            Def d = Items.defOf(s);
            if (d == null || !d.id.equals(ourId)) {
                continue;
            }
            int take = Math.min(left, s.getAmount());
            left -= take;
            if (take >= s.getAmount()) {
                p.getInventory().setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - take);
                p.getInventory().setItem(i, s);
            }
        }
    }

    /** Настоящая операция для игрока: списать выдать,Sound, подсказка в чат. */
    public static boolean craft(ElectronicsPlugin plugin, Player p, String stationId, Recipe r) {
        String why = missing(r, id -> countOf(p, id));
        if (why != null) {
            Items.message(p, "&c✗ " + why);
            return false;
        }
        Map<String, Integer> give = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : r.make().entrySet()) {
            if (Components.byId(e.getKey()) == null) {
                Items.message(p, "&cвнутренняя ошибка: нет компонента &f" + e.getKey());
                return false;
            }
            give.merge(e.getKey(), e.getValue(), Integer::sum);
        }
        for (Map.Entry<String, Integer> e : new java.util.TreeMap<>(r.need()).entrySet()) {
            remove(p, e.getKey(), e.getValue());
        }
        for (Map.Entry<String, Integer> e : give.entrySet()) {
            Def d = Components.byId(e.getKey());
            ItemStack stack = plugin.items().componentItem(d, e.getValue());
            Map<Integer, ItemStack> rest = p.getInventory().addItem(stack);
            for (ItemStack left : rest.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), left);
            }
        }
        p.updateInventory();
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.7f, 0.8f);
        Items.message(p, "&a⚒ Готово: &f" + outputText(r) + " &8(" + r.note() + ")");
        return true;
    }
}
