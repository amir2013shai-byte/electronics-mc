package me.arena.electronics.gui;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.recipes.Metallurgy;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Окно тигельной печи и прокатного стана: список операций стола, у каждой —
 * что нужно, что выйдет (включая шлак) и на чём стоит физика.
 *
 * <p>Поля ввода тут нет и не нужно: игрок кладёт материалы в инвентарь и
 * нажимает на карточку — как на паяльном столе. Окно перерисовывается после
 * каждой операции, поэтому «есть в инвентаре» всегда настоящее.</p>
 */
public final class MetalGui implements Gui {

    private static final int SIZE = 27;
    private static final int FIRST = 0;

    private final ElectronicsPlugin plugin;
    private final Player viewer;
    private final String stationId;
    private final List<Metallurgy.Recipe> recipes;
    private final Inventory inv;

    private MetalGui(ElectronicsPlugin plugin, Player p, String stationId) {
        this.plugin = plugin;
        this.viewer = p;
        this.stationId = stationId;
        this.recipes = Metallurgy.recipesOf(stationId);
        Def st = Components.byId(stationId);
        this.inv = org.bukkit.Bukkit.createInventory(this, SIZE,
                Items.color("&a⚒ " + (st == null ? "Металлургия" : st.nameRu)));
        render();
    }

    public static void open(ElectronicsPlugin plugin, Player p, String stationId) {
        if (Metallurgy.table(stationId) == null) {
            return;
        }
        p.openInventory(new MetalGui(plugin, p, stationId).inv);
    }

    @Override
    public Inventory inventory() {
        return inv;
    }

    private void render() {
        inv.clear();
        for (int i = 0; i < recipes.size() && i < 9; i++) {
            inv.setItem(FIRST + i, card(recipes.get(i)));
        }
        for (int i = recipes.size(); i < 9; i++) {
            inv.setItem(FIRST + i, Gui.button(Material.LIGHT_GRAY_STAINED_GLASS_PANE,
                    "&8операция не найдена"));
        }
        inv.setItem(18, Gui.button(Material.HOPPER_MINECART,
                "&7Всё идёт из недр: &fкусок руды &7→ &fслиток &7→ &fпровод &7→ &fдеталь",
                "&7Тигель плавит и меняет концентраты, стан тянет проволоку и лудит.",
                "&7Ряд активности (Zn > Sn > Cu) запрещает получать цинк вытеснением:",
                "&7его только перегоняют, и то с потерями."));
        inv.setItem(22, Gui.button(Material.COMPASS, "&7Как это работает",
                "&7Материалы брать в инвентарь, не в окно: стол их берёт оттуда.",
                "&7Не хватает — карточка красная и объясняет, чего именно.",
                "&7Побочный шлак — не мусор: 3 куска шлака дают 2 слитка меди."));
        inv.setItem(26, Gui.button(Material.BARRIER, "&cЗакрыть"));
    }

    private ItemStack card(Metallurgy.Recipe r) {
        Def out = Components.byId(r.result());
        ItemStack stack = out == null ? new ItemStack(Material.PAPER)
                : plugin.items().componentItem(out, 1);
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(Items.color("&a" + (out == null ? r.result() : out.nameRu)
                + " &7×" + r.count()));
        List<String> lore = new ArrayList<>();
        for (var e : r.need().entrySet()) {
            Def d = Components.byId(e.getKey());
            int have = Metallurgy.countOf(viewer, e.getKey());
            lore.add(Items.color((have >= e.getValue() ? "&a  " : "&c  ")
                    + (d == null ? e.getKey() : d.nameRu) + " &7— есть &f" + have
                    + "&7, нужно &f" + e.getValue()));
        }
        lore.add(Items.color("&7Выход: &f" + Metallurgy.outputText(r)));
        for (String line : wrap(r.note(), 42)) {
            lore.add(Items.color("&8" + line));
        }
        String why = Metallurgy.missing(r, id -> Metallurgy.countOf(viewer, id));
        lore.add(Items.color(why == null ? "&eклик — сделать" : "&c" + why));
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private static List<String> wrap(String s, int width) {
        List<String> out = new ArrayList<>();
        String[] words = s.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            if (cur.length() + w.length() + 1 > width && cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(w);
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }

    @Override
    public void onClick(Player who, InventoryClickEvent e) {
        int slot = e.getRawSlot();
        e.setCancelled(true);
        if (slot == 26) {
            who.closeInventory();
            return;
        }
        if (slot < FIRST || slot >= FIRST + recipes.size()) {
            return;
        }
        Metallurgy.craft(plugin, who, stationId, recipes.get(slot - FIRST));
        render();
    }
}
