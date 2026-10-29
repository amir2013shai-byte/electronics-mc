package me.arena.electronics.gui;

import me.arena.electronics.Items;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Общий интерфейс для GUI плагина. Держатель инвентаря = сам объект GUI, так
 * что не приходится угадывать окно по заголовку (и два редактора у одного
 * игрока не путаются).
 */
public interface Gui extends InventoryHolder {

    /** Инвентарь, который реально показан. */
    Inventory inventory();

    /** Клик по слоту; отмена уже выполнена Events#onGuiClick. */
    void onClick(Player who, InventoryClickEvent e);

    @Override
    default Inventory getInventory() {
        return inventory();
    }

    /** Кнопка/плитка с названием и подсказками. */
    static ItemStack button(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(Items.color(name));
            List<String> l = new ArrayList<>();
            for (String s : lore) {
                l.add(Items.color(s));
            }
            meta.setLore(l);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
