package me.arena.electronics.gui;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.recipes.Soldering;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * GUI паяльного стола: 36 деталей на странице, у каждой — что положить в
 * заготовку, сколько припоя и нужен ли флюс. Клик = попытка спаять; правила
 * целиком в {@link Soldering}, поэтому {@code /elec selftest} проверяет их же.
 *
 * <p>Открывается ПКМ по паяльному столу (только если рядом есть источник
 * тепла) или {@code /elec solder} — второй вариант для уроков и отладки.</p>
 */
public final class SolderGui implements Gui {

    private static final int SIZE = 54;
    private static final int FIRST = 9;
    private static final int PER_PAGE = 36;

    private final ElectronicsPlugin plugin;
    private final Player viewer;
    private final Inventory inv;
    private final boolean heated;
    private int page;
    private List<Def> targets = List.of();

    private SolderGui(ElectronicsPlugin plugin, Player p, boolean heated) {
        this.plugin = plugin;
        this.viewer = p;
        this.heated = heated;
        this.inv = Bukkit.createInventory(this, SIZE, Items.color("&a⚡ Паяльный стол"));
        rebuild();
    }

    public static void open(ElectronicsPlugin plugin, Player p, Block station, boolean strict) {
        if (plugin == null || p == null) {
            return;
        }
        boolean hot = Soldering.heatSourceNear(station);
        if (strict && !hot) {
            Items.message(p, "&eРядом со столом нужен источник тепла&7: печь, костёр, лава или"
                    + " магма — от него греют жало. &8(поставь рядом и нажми ПКМ по столу)");
            return;
        }
        p.openInventory(new SolderGui(plugin, p, hot).inv);
    }

    @Override
    public Inventory inventory() {
        return inv;
    }

    // ------------------------------------------------------------------ разметка

    private void rebuild() {
        List<Def> all = new ArrayList<>();
        for (Def d : Components.all()) {
            if (Components.isSoldered(d.id) && Recipes0.has(d.id)) {
                all.add(d);                     // нет заготовки — нечего паять
            }
        }
        targets = all;
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        page = Math.max(0, Math.min(page, pages - 1));
        inv.clear();
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.size()) {
                break;
            }
            inv.setItem(FIRST + i, card(all.get(idx)));
        }
        int ironSlot = Soldering.ironSlot(viewer);
        ItemStack iron = ironSlot < 0 ? null : viewer.getInventory().getItem(ironSlot);
        int heat = Soldering.heat(iron);
        inv.setItem(0, Gui.button(iron == null ? Material.BRUSH : iron.getType(),
                heat < 0 ? "&cПаяльника нет" : "&aПаяльник &7" + heat + "°",
                heat < 0 ? "&7Скрафти его (медь + железо + палка)"
                        : "&7Жало: &f" + heat + "°&7, нужна &f" + Soldering.HEAT_HOT + "°+",
                "&7Остывает " + Soldering.COOL_DEG_PER_S + "°/с; греют ПКМ по печи/костру",
                heat >= Soldering.HEAT_HOT ? "&aможно паять"
                        : heat >= Soldering.HEAT_WARM ? "&eтёплое жало — будет холодная пайка"
                        : "&cслишком холодно",
                "&8" + (all.size()) + " деталей на этом столе"));
        inv.setItem(1, Gui.button(Material.GUNPOWDER, "&aПрипой &7×"
                        + Soldering.countOf(viewer, Soldering.SOLDER_ID),
                "&71 шт. на 2-выводную деталь, 2 шт. на 3…4-выводную",
                "&7Крафт: 2 железных самородка + кресало"));
        inv.setItem(2, Gui.button(Material.SPLASH_POTION, "&aФлюс &7×"
                        + Soldering.countOf(viewer, Soldering.FLUX_ID),
                "&7Нужен светодиодам, транзисторам, микросхемам,",
                "&7реле, катушкам, мотору и приборам — без него",
                "&7площадка окислится и припой ляжет шариком"));
        inv.setItem(3, Gui.button(heated ? Material.FURNACE : Material.BOOKSHELF,
                heated ? "&aТепло рядом" : "&eНет источника тепла",
                heated ? "&7Жало можно прогреть в любой момент"
                        : "&7Поставь рядом печь, костёр, лава или магма,",
                "&7иного стол не греется"));
        inv.setItem(4, Gui.button(Material.COMPASS, "&7Как паять",
                "&71) прогреть жало (ПКМ паяльником по горячему блоку)",
                "&72) положить заготовки из подсказки в инвентарь",
                "&73) иметь припой (и флюс, если он нужен)",
                "&74) кликнуть по детали — стол всё проверит и спишет"));
        if (page > 0) {
            inv.setItem(45, Gui.button(Material.ARROW, "&e◀ предыдущая страница"));
        }
        if (page + 1 < pages) {
            inv.setItem(46, Gui.button(Material.ARROW, "&e следующая страница ▶"));
        }
        inv.setItem(47, Gui.button(Material.REPEATER, "&aОбновить",
                "&7Пересчитать температуру жала и запасы"));
        inv.setItem(49, Gui.button(Material.BOOK, "&7Страница &f" + (page + 1) + "&7/&f" + pages
                + " &8(" + all.size() + " деталей)"));
        inv.setItem(53, Gui.button(Material.OAK_DOOR, "&cЗакрыть"));
    }

    /** Плитка детали: номиналы + что положить + расходники. */
    private ItemStack card(Def d) {
        ItemStack stack = plugin.items().componentItem(d, 1);
        List<String> lore = new ArrayList<>();
        if (stack.hasItemMeta() && stack.getItemMeta().hasLore()) {
            lore.addAll(stack.getItemMeta().getLore());
        }
        String nominal = d.nominal();
        if (!nominal.isEmpty()) {
            lore.add(Items.color("&7Электрика: &f" + nominal));
        }
        lore.add(Items.color("&eЗаготовка: &f" + Soldering.ingredientText(d.id)));
        lore.add(Items.color("&eПрипой: &f×" + Soldering.solderCost(d)
                + (Soldering.needsFlux(d) ? "  &e+ флюс ×1" : "  &7флюс не нужен")));
        lore.add(Items.color("&7Выводов: &f" + d.pins + " &8(пины > 2 — больше жидкого припоя)"));
        lore.add(Items.color("&aКлик &7— спаять"));
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }

    // -------------------------------------------------------------------- клики

    @Override
    public void onClick(Player who, InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= inv.getSize()) {
            return;
        }
        e.setCancelled(true);
        if (raw == 45) {
            page--;
            rebuild();
            return;
        }
        if (raw == 46) {
            page++;
            rebuild();
            return;
        }
        if (raw == 47) {
            rebuild();
            return;
        }
        if (raw == 53) {
            who.closeInventory();
            return;
        }
        if (raw < FIRST || raw >= FIRST + PER_PAGE) {
            return;
        }
        int idx = page * PER_PAGE + (raw - FIRST);
        if (idx < 0 || idx >= targets.size()) {
            return;
        }
        Def target = targets.get(idx);
        if (!heated) {
            Items.message(who, "&eСтол без источника тепла: жало стынет вдвое быстрее —"
                    + " прогрети его у печи и паяй быстро.");
        }
        Soldering.craft(plugin, who, target);
        rebuild();
    }

    /** Есть ли у id заготовка-рецепт (иначе паять нечего). */
    private static final class Recipes0 {
        static boolean has(String id) {
            return !me.arena.electronics.recipes.Recipes.ingredientsOf(id).isEmpty();
        }
    }
}
