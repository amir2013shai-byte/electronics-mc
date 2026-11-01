package me.arena.electronics;

import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

/**
 * Предметы компонентов: базовый ванильный материал + `item_model` из ресурспака.
 *
 * Именно этот способ (а не клиентский мод) даёт «800+ состояний»: индекс
 * состояния модели -> модель ресурспака (assets/electronics/items/*.json) ->
 * готовый 3D-блок на руках, в инвентаре и в мире (через item_display).
 */
public final class Items {

    private final ElectronicsPlugin plugin;

    public Items(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    public Material baseMaterial() {
        Material m = plugin.config().baseBlock;
        return m == null ? Material.SMOOTH_QUARTZ_SLAB : m;
    }

    public Material pedestalMaterial() {
        Material m = plugin.config().pedestalBlock;
        return m == null ? Material.POLISHED_BLACKSTONE : m;
    }

    /**
     * Ванильный «носитель» предмета. Для деталей это базовый блок (визуал даёт
     * item_model), для инструмента — BRUSH: у него есть прочность, и шкала
     * износа жала видна прямо в инвентаре.
     */
    public Material itemMaterial(Def def) {
        return def != null && def.kind == Kind.TOOL ? Material.BRUSH : baseMaterial();
    }

    /** Предмет компонента для инвентаря/дропа. */
    public ItemStack componentItem(Def def, int count) {
        ItemStack stack = new ItemStack(itemMaterial(def), Math.max(1, count));
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return stack;
        }
        meta.setDisplayName(color("&a⚡ &f" + def.nameRu));
        List<String> lore = new ArrayList<>();
        String nominal = def.nominal();
        if (!nominal.isEmpty()) {
            lore.add(color("&7" + nominal));
        }
        lore.add(color("&8" + def.kind.name()));
        if (me.arena.electronics.registry.Components.isSoldered(def.id)) {
            lore.add(color("&6⚡ Паяется на паяльном столе"));
            String hint = me.arena.electronics.recipes.Soldering.solderHint(def);
            if (!hint.isEmpty()) {
                lore.add(color("&7" + hint + " &8(в верстаке не соберётся)"));
            }
        }
        if (def.kind == Kind.TOOL) {
            lore.add(color("&eПКМ по печи, костру или лаве &7— прогреть жало"));
            lore.add(color("&7Износ жала — по шкале прочности"));
        } else if (def.kind == Kind.SUPPLY) {
            lore.add(color("&7Расходник для паяльного стола"));
        } else if (def.kind == Kind.STATION) {
            lore.add(color("&eПКМ &7— установить как блок; рядом нужен источник тепла"));
            lore.add(color("&eПКМ по столу &7— открыть пайку"));
        } else {
            lore.add(color("&eПКМ &7— установить на блок-подставку или макетку"));
            lore.add(color("&eShift+ПКМ &7— повернуть компонент"));
        }
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(CompInstance.PDC_DEF, PersistentDataType.STRING, def.id);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ATTRIBUTES,
                org.bukkit.inventory.ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        stack.setItemMeta(meta);
        applyModel(stack, def, 0);
        return stack;
    }

    /** Предмет, который показывается item_display'ем в мире (модель = состояние). */
    public ItemStack displayItem(Def def, int state) {
        ItemStack stack = new ItemStack(baseMaterial(), 1);
        applyModel(stack, def, state);
        return stack;
    }

    /** Ставим item_model (1.21.4+); если недоступно — custom model data. */
    private void applyModel(ItemStack stack, Def def, int state) {
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        NamespacedKey model = new NamespacedKey(Components.NAMESPACE, def.id + suffix(def, state));
        boolean applied = false;
        try {
            meta.setItemModel(model);
            applied = true;
        } catch (Throwable ignored) {
            applied = false;
        }
        if (!applied) {
            meta.setCustomModelData(def.stateModel(state, 0));
        }
        stack.setItemMeta(meta);
    }

    private String suffix(Def def, int state) {
        if (state <= 0 || state >= Components.STATE_NAMES.length) {
            return "";
        }
        String name = Components.STATE_NAMES[state];
        // ресурспак хранит состояния с «короткими» именами (rev/closed)
        return "_" + name;
    }

    public static Def defOf(ItemStack stack) {
        if (stack == null || stack.getType().isAir() || !stack.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = stack.getItemMeta();
        String id = meta.getPersistentDataContainer()
                .get(CompInstance.PDC_DEF, PersistentDataType.STRING);
        Def byPdc = Components.byId(id);
        if (byPdc != null) {
            return byPdc;
        }
        // Метки в PDC может и не быть: предмет пришёл из датапака (компоненты
        // результата recipe в некоторых ядрах не перекладываются в PDC-представление)
        // или выдан командой. Тогда деталь опознаём по minecraft:item_model —
        // он у наших предметов есть всегда, а суффикс состояния отрезаем.
        NamespacedKey model = null;
        try {
            model = meta.getItemModel();
        } catch (Throwable ignored) {
            return null;                        // ядро без item_model (1.21.2-)
        }
        if (model == null || !Components.NAMESPACE.equals(model.getNamespace())) {
            return null;
        }
        Def direct = Components.byId(model.getKey());
        return direct != null ? direct : Components.byId(stripState(model.getKey()));
    }

    /** electronics:led_red_on -> led_red (нет такого — оставляем как есть). */
    private static String stripState(String key) {
        int i = key.lastIndexOf('_');
        return i > 0 ? key.substring(0, i) : key;
    }

    public ItemStack giveItem(Def def) {
        return componentItem(def, 1);
    }

    /** Одно сообщение с цветами (пусто — молчим). */
    public static void message(org.bukkit.command.CommandSender who, String text) {
        if (who != null && text != null && !text.isEmpty()) {
            who.sendMessage(color(text));
        }
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }
}
