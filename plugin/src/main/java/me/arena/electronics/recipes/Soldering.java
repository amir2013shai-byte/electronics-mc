package me.arena.electronics.recipes;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Пайка — «особый метод крафта».
 *
 * <p>Никаких клиентских модов, только правила на сервере: деталь из
 * {@code meta.soldered} нельзя собрать в верстаке (рецепт для неё не
 * регистрируется), её можно только спаять на паяльном столе, горячим
 * паяльником, с припоем — а для чувствительного к перегреву корпуса ещё и с
 * флюсом.</p>
 *
 * <p>Температура жала хранится в PDC предмета: {@code iron_heat} (0…100°) и
 * {@code iron_heat_ts} (миллисекунды последнего замера). Жало остывает
 * {@value #COOL_DEG_PER_S}° в секунду, поэтому «прогреть» и «спаять» — два
 * разных действия, как в жизни. Выше {@value #HEAT_HOT}° — нормальная пайка,
 * {@value #HEAT_WARM}…{@value #HEAT_HOT}° — холодная (припой не растекается:
 * припой сгорает, деталь не получается), ниже — отказа.</p>
 *
 * <p>Все решения принимает {@link #evaluate(Attempt)} — чистая функция без
 * Bukkit-состояний, поэтому её же прогоняет {@code /elec selftest}.</p>
 */
public final class Soldering {

    public static final int HEAT_MAX = 100;
    public static final int HEAT_HOT = 60;
    public static final int HEAT_WARM = 20;
    public static final double COOL_DEG_PER_S = 1.2;
    /** Сколько паянок живёт жало (совпадает с прочностью BRUSH = 64). */
    public static final int IRON_LIVES = 64;

    public static final NamespacedKey PDC_HEAT = new NamespacedKey("electronics", "iron_heat");
    public static final NamespacedKey PDC_HEAT_TS = new NamespacedKey("electronics", "iron_heat_ts");

    public static final String IRON_ID = "soldering_iron";
    public static final String SOLDER_ID = "solder";
    public static final String FLUX_ID = "flux";

    /** Корпуса, которые портятся перегревом/окислением — им нужен флюс. */
    private static final Set<Kind> NEEDS_FLUX = EnumSet.of(Kind.LED, Kind.LAMP, Kind.TRANSISTOR,
            Kind.GATE, Kind.RELAY, Kind.REED, Kind.ELECTROMAGNET, Kind.INDUCTOR, Kind.CAPACITOR,
            Kind.MOTOR, Kind.BUZZER, Kind.VOLTMETER, Kind.AMMETER, Kind.MULTIMETER, Kind.DIODE);

    private Soldering() {
    }

    /** Всё, что влияет на результат пайки. */
    public record Attempt(Def target, Map<Material, Integer> have, int solder, int flux, int heat) {
    }

    /**
     * @param allowed  разрешено ли паять вообще (иначе ничего не тратится)
     * @param coldJoint разрешено, но жало тёплое, а не горячее — пайка бракуется
     * @param consume  что вычесть из инвентаря при успешной пайке
     */
    public record Result(boolean allowed, boolean coldJoint, String message,
                         Map<Material, Integer> consume, int solder, int flux, int heat) {
    }

    /** Расход припоя: 1 на 2-выводную деталь, 2 на многовыводную. */
    public static int solderCost(Def d) {
        return d != null && d.pins >= 3 ? 2 : 1;
    }

    public static boolean needsFlux(Def d) {
        return d != null && NEEDS_FLUX.contains(d.kind);
    }

    // ------------------------------------------------------------- чистые правила

    public static Result evaluate(Attempt a) {
        Def d = a.target();
        if (d == null) {
            return new Result(false, false, "неизвестная деталь", Map.of(), 0, 0, a.heat());
        }
        if (!Components.isSoldered(d.id)) {
            return new Result(false, false, "«" + d.nameRu + "» не паяют — собери её в верстаке",
                    Map.of(), 0, 0, a.heat());
        }
        if (a.heat() < 0) {
            return new Result(false, false,
                    "нужен паяльник: возьми «Паяльник» и прогрей жало (ПКМ по печи, костру или лаве)",
                    Map.of(), 0, 0, a.heat());
        }
        Map<Material, Integer> need = Recipes.ingredientsOf(d.id);
        if (need.isEmpty()) {
            return new Result(false, false, "для «" + d.nameRu + "» нет заготовки-рецепта",
                    Map.of(), 0, 0, a.heat());
        }
        List<String> missing = new ArrayList<>();
        Map<Material, Integer> short_ = new EnumMap<>(Material.class);
        for (Map.Entry<Material, Integer> e : need.entrySet()) {
            int have = a.have().getOrDefault(e.getKey(), 0);
            if (have < e.getValue()) {
                missing.add(e.getKey().name().toLowerCase(java.util.Locale.ROOT)
                        + " ×" + (e.getValue() - have));
                short_.put(e.getKey(), e.getValue());
            } else {
                short_.put(e.getKey(), e.getValue());
            }
        }
        if (!missing.isEmpty()) {
            return new Result(false, false, "не хватает заготовок: " + String.join(", ", missing),
                    Map.of(), 0, 0, a.heat());
        }
        int cost = solderCost(d);
        if (a.solder() < cost) {
            return new Result(false, false, "нужен припой ×" + cost + " (есть " + a.solder() + ")",
                    Map.of(), 0, 0, a.heat());
        }
        boolean flux = needsFlux(d);
        if (flux && a.flux() < 1) {
            return new Result(false, false, "нужен флюс: площадка " + d.nameRu
                    + " окислится и припой ляжет шариком", Map.of(), 0, 0, a.heat());
        }
        if (a.heat() < HEAT_WARM) {
            return new Result(false, false, "жало холодное (" + a.heat() + "°): припой не потечёт. "
                    + "Прогрей паяльник об источник тепла рядом со столом",
                    Map.of(), 0, 0, a.heat());
        }
        if (a.heat() < HEAT_HOT) {
            return new Result(true, true, "холодная пайка: " + a.heat() + "° мало для "
                    + d.nameRu + " — припой сгорел, деталь не спаяна. Прогрей жало до "
                    + HEAT_HOT + "°", short_, 1, 0, a.heat());
        }
        return new Result(true, false, "спаяно при " + a.heat() + "°", short_, cost, flux ? 1 : 0,
                a.heat());
    }

    /** Человекочитаемый список ингредиентов — в подсказку кнопки GUI. */
    public static String ingredientText(String id) {
        Map<Material, Integer> need = Recipes.ingredientsOf(id);
        if (need.isEmpty()) {
            return "нет заготовки";
        }
        List<String> parts = new ArrayList<>();
        for (Map.Entry<Material, Integer> e : need.entrySet()) {
            parts.add(pretty(e.getKey()) + (e.getValue() > 1 ? " ×" + e.getValue() : ""));
        }
        return String.join(", ", parts);
    }

    private static String pretty(Material m) {
        String n = m.name().toLowerCase(java.util.Locale.ROOT).replace('_', ' ');
        return n;
    }

    // --------------------------------------------------------------- мир и предметы

    /** Есть ли рядом с клеткой что-то горячее (печь, костёр, лава, магма). */
    public static boolean heatSourceNear(Block b) {
        if (b == null || b.getWorld() == null) {
            return false;
        }
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH,
                BlockFace.WEST, BlockFace.UP, BlockFace.DOWN}) {
            if (isHot(b.getRelative(f).getType())) {
                return true;
            }
        }
        return false;
    }

    public static boolean isHot(Material m) {
        if (m == null) {
            return false;
        }
        return switch (m) {
            case FURNACE, BLAST_FURNACE, SMOKER, CAMPFIRE, SOUL_CAMPFIRE, LAVA, LAVA_CAULDRON,
                 MAGMA_BLOCK, FIRE, SOUL_FIRE -> true;
            default -> m.name().contains("LAVA") || m.name().contains("MAGMA");
        };
    }

    public static boolean isIron(ItemStack s) {
        Def d = Items.defOf(s);
        return d != null && IRON_ID.equals(d.id);
    }

    /** Индекс паяльника в основном отсеке инвентаря (0…35) или −1. */
    public static int ironSlot(Player p) {
        ItemStack[] st = p.getInventory().getStorageContents();
        for (int i = 0; i < st.length; i++) {
            if (isIron(st[i])) {
                return i;
            }
        }
        return -1;
    }

    /** Текущая температура жала с учётом остывания. */
    public static int heat(ItemStack iron) {
        if (!isIron(iron) || !iron.hasItemMeta()) {
            return -1;
        }
        ItemMeta meta = iron.getItemMeta();
        Integer h = meta.getPersistentDataContainer().get(PDC_HEAT, PersistentDataType.INTEGER);
        Long ts = meta.getPersistentDataContainer().get(PDC_HEAT_TS, PersistentDataType.LONG);
        if (h == null || ts == null) {
            return 0;
        }
        double secs = (System.currentTimeMillis() - ts) / 1000.0;
        return (int) Math.max(0, Math.min(HEAT_MAX, Math.round(h - secs * COOL_DEG_PER_S)));
    }

    /** Вернуть копию предмета с заданной температурой (и свежим временем замера). */
    public static ItemStack withHeat(ItemStack iron, int heat) {
        ItemStack copy = iron == null ? null : iron.clone();
        if (copy == null) {
            return null;
        }
        ItemMeta meta = copy.getItemMeta();
        meta.getPersistentDataContainer().set(PDC_HEAT, PersistentDataType.INTEGER,
                Math.max(0, Math.min(HEAT_MAX, heat)));
        meta.getPersistentDataContainer().set(PDC_HEAT_TS, PersistentDataType.LONG,
                System.currentTimeMillis());
        copy.setItemMeta(meta);
        return copy;
    }

    /**
     * ПКМ паяльником по блоку: если блок горячий — жало прогревается до 100°,
     * иначе — подсказка. Возвращает true, если событие нужно отменить.
     */
    public static boolean reheat(Player p, Block b) {
        int slot = ironSlot(p);
        if (slot < 0) {
            return false;
        }
        ItemStack iron = p.getInventory().getItem(slot);
        if (b == null || !isHot(b.getType())) {
            Items.message(p, "&eПаяльник &7(" + heat(iron) + "°). Греют об &cпечь, костёр или лаву"
                    + " — нажми ПКМ паяльником по такому блоку.");
            return true;
        }
        p.getInventory().setItem(slot, withHeat(iron, HEAT_MAX));
        p.updateInventory();
        Items.message(p, "&aЖало прогрето до " + HEAT_MAX + "°&7 — паять можно, остывает за "
                + Math.round(HEAT_MAX / COOL_DEG_PER_S) + " с.");
        return true;
    }

    /** Сколько предметов нашего реестра с таким id лежит в инвентаре. */
    public static int countOf(Player p, String defId) {
        int n = 0;
        for (ItemStack s : p.getInventory().getStorageContents()) {
            Def d = Items.defOf(s);
            if (d != null && d.id.equals(defId)) {
                n += s.getAmount();
            }
        }
        return n;
    }

    private static void removeOurs(Player p, String defId, int amount) {
        ItemStack[] st = p.getInventory().getStorageContents();
        for (int i = 0; i < st.length && amount > 0; i++) {
            Def d = Items.defOf(st[i]);
            if (d == null || !d.id.equals(defId)) {
                continue;
            }
            int take = Math.min(amount, st[i].getAmount());
            amount -= take;
            if (st[i].getAmount() - take <= 0) {
                p.getInventory().setItem(i, null);
            } else {
                st[i].setAmount(st[i].getAmount() - take);
                p.getInventory().setItem(i, st[i]);
            }
        }
    }

    private static void removeVanilla(Player p, Material m, int amount) {
        ItemStack[] st = p.getInventory().getStorageContents();
        for (int i = 0; i < st.length && amount > 0; i++) {
            ItemStack s = st[i];
            if (s == null || s.getType() != m || Items.defOf(s) != null) {
                continue;                       // наши предметы — не ванильные стеки
            }
            int take = Math.min(amount, s.getAmount());
            amount -= take;
            if (s.getAmount() - take <= 0) {
                p.getInventory().setItem(i, null);
            } else {
                s.setAmount(s.getAmount() - take);
                p.getInventory().setItem(i, s);
            }
        }
    }

    /** Износ жала: +1 к прочности; при нуле прочности паяльник сгорает. */
    private static void wearIron(ElectronicsPlugin plugin, Player p) {
        int slot = ironSlot(p);
        if (slot < 0) {
            return;
        }
        ItemStack iron = p.getInventory().getItem(slot);
        ItemMeta meta = iron.getItemMeta();
        int max = iron.getType().getMaxDurability();
        int dmg = (meta instanceof Damageable d ? d.getDamage() : 0) + 1;
        if (max > 0 && dmg >= max) {
            p.getInventory().setItem(slot, null);
            Items.message(p, "&cЖало выгорело — паяльник сломан. Спаяй новый "
                    + "&8(медное жало + железный стержень + деревянная ручка)&7.");
            return;
        }
        if (meta instanceof Damageable d) {
            d.setDamage(dmg);
            iron.setItemMeta(meta);
        }
        p.getInventory().setItem(slot, iron);
    }

    /** Полная операция для игрока: проверка, списание, выдача, износ. */
    public static Result craft(ElectronicsPlugin plugin, Player p, Def target) {
        int slot = ironSlot(p);
        ItemStack iron = slot < 0 ? null : p.getInventory().getItem(slot);
        Map<Material, Integer> have = new EnumMap<>(Material.class);
        for (ItemStack s : p.getInventory().getStorageContents()) {
            if (s == null || s.getType().isAir() || Items.defOf(s) != null) {
                continue;
            }
            have.merge(s.getType(), s.getAmount(), Integer::sum);
        }
        Result r = evaluate(new Attempt(target, have, countOf(p, SOLDER_ID), countOf(p, FLUX_ID),
                heat(iron)));
        if (!r.allowed()) {
            Items.message(p, "&c✗ " + r.message());
            return r;
        }
        if (r.coldJoint()) {
            removeOurs(p, SOLDER_ID, r.solder());
            p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_LAVA_EXTINGUISH, 1f, 1.6f);
            Items.message(p, "&e⚠ " + r.message());
            p.updateInventory();
            return r;
        }
        for (Map.Entry<Material, Integer> e : r.consume().entrySet()) {
            removeVanilla(p, e.getKey(), e.getValue());
        }
        removeOurs(p, SOLDER_ID, r.solder());
        if (r.flux() > 0) {
            removeOurs(p, FLUX_ID, r.flux());
        }
        p.getInventory().addItem(plugin.items().componentItem(target, 1));
        wearIron(plugin, p);
        p.updateInventory();
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.BLOCK_ANVIL_USE, 0.8f, 1.4f);
        Items.message(p, "&a⚡ Спаяно: &f" + target.nameRu + "&7 (припой ×" + r.solder()
                + (r.flux() > 0 ? ", флюс ×1" : "") + ", " + r.heat() + "°)&8 — "
                + r.consume().size() + " вида заготовок ушло");
        return r;
    }
}
