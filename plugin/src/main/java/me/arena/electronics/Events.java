package me.arena.electronics;

import me.arena.electronics.circuit.Circuit;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.circuit.CompStates;
import me.arena.electronics.circuit.ModelState;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.registry.Orientation;
import me.arena.electronics.util.Num;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Взаимодействие с миром: установка/слом/вращение компонентов, измерения,
 * переключение ключей, защита «наших» блоков.
 *
 * Схема простая: в руке игрока — ванильный предмет (гладкий кварцевый полублок
 * по умолчанию) с item_model из ресурспака и PDC-меткой типа. ПКМ по «платe»
 * (подставке) ставит компонент; сам компонент — это обычный блок + визуал.
 */
public final class Events implements Listener {

    private final ElectronicsPlugin plugin;

    public Events(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------ взаимодействие

    @EventHandler(priority = org.bukkit.event.EventPriority.LOW)
    public void onChunkLoad(org.bukkit.event.world.ChunkLoadEvent e) {
        if (e.getWorld() == null || plugin.circuit() == null) {
            return;
        }
        int n = plugin.persistence().loadChunk(e.getWorld().getName(),
                e.getChunk().getX(), e.getChunk().getZ());
        if (n > 0 && plugin.config().debug) {
            plugin.getLogger().info("чанк " + e.getWorld().getName() + " " + e.getChunk().getX() + ","
                    + e.getChunk().getZ() + ": поднято компонентов " + n);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        Def held = Items.defOf(p.getInventory().getItemInMainHand());
        // «Пустая схема» в руке: ПКМ в воздух — открыть лист-редактор.
        if (held != null && held.kind == Kind.SCHEMATIC && !e.getAction().name().contains("BLOCK")) {
            e.setCancelled(true);
            me.arena.electronics.gui.SchematicGui.open(plugin, p);
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }
        // (0) паяльник и расходники в мир не ставятся: ПКМ по горячему блоку греет жало
        if (held != null && (held.kind == Kind.TOOL || held.kind == Kind.SUPPLY)) {
            e.setCancelled(true);
            if (held.kind == Kind.TOOL) {
                me.arena.electronics.recipes.Soldering.reheat(p, b);
            } else {
                Items.message(p, "&7«" + held.nameRu + "» — расходник паяльного стола: "
                        + "держи его в инвентаре и нажми ПКМ по паяльному столу.");
            }
            return;
        }
        CompInstance existing = plugin.circuit().get(b);

        // (1) устанавливаем компонент из руки на подставку/плату
        if (held != null && existing == null) {
            e.setCancelled(true);
            placeFromHand(p, b, held);
            return;
        }
        if (existing == null) {
            if (plugin.circuit().persistence().isHost(b) && held == null && plugin.config().hintsEnabled) {
                hint(p, "Пустая плата. Возьмите компонент в руку и нажмите ПКМ.");
            }
            return;
        }
        // (2) на компоненте — свои действия
        e.setCancelled(true);
        if (p.isSneaking()) {
            rotate(existing);
            return;
        }
        if (held != null && held.kind == Kind.MULTIMETER) {
            measure(p, existing);
            return;
        }
        switch (existing.def.kind) {
            case SWITCH, TOGGLE, BUTTON, PROBE -> toggle(existing);
            case POT -> tune(p, existing, 0.05);
            case SOURCE -> {
                if (existing.def.tunable) {
                    tune(p, existing, 0.1);
                } else {
                    info(p, existing);
                }
            }
            case STATION -> me.arena.electronics.gui.SolderGui.open(plugin, p, b, true);
            case SCHEMATIC -> {
                // планшет без привязанной лабораторной = лист для рисования
                if (existing.lab.isEmpty()) {
                    me.arena.electronics.gui.SchematicGui.open(plugin, p);
                } else {
                    plugin.labs().onSchematicClick(p, existing);
                }
            }
            default -> info(p, existing);
        }
    }

    /**
     * Рецепты спаечных деталей регистрируются только чтобы их показывал JEI.
     * Собрать их в верстаке нельзя: вместо этого — подсказка, где их делают.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCraftDisplay(org.bukkit.event.inventory.CraftItemEvent e) {
        if (!plugin.recipes().isDisplayOnly(e.getRecipe())) {
            return;
        }
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) {
            return;
        }
        Def out = Items.defOf(e.getRecipe().getResult());
        String name = out == null ? "деталь" : out.nameRu;
        Items.message(p, "&e«" + name + "» &7в верстаке не собирается — её &aпаивают&7: "
                + "нужен &aпаяльный стол&7, прогретое жало, припой"
                + (out != null && me.arena.electronics.recipes.Soldering.needsFlux(out)
                        ? " и флюс" : "") + ".");
        Items.message(p, "&8Открой стол ПКМ по нему (рядом должна стоять печь/костёр) "
                + "или &felec solder&8; список заготовок — в подсказке детали и в &felec recipe&8.");
        p.updateInventory();
    }

    /** Любой клик внутри GUI плагина: окно наше, предметы не таскаются. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGuiClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (e.getInventory().getHolder() instanceof me.arena.electronics.gui.Gui gui
                && e.getWhoClicked() instanceof Player p) {
            e.setCancelled(true);
            gui.onClick(p, e);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onGuiDrag(org.bukkit.event.inventory.InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof me.arena.electronics.gui.Gui) {
            e.setCancelled(true);
        }
    }

    private void placeFromHand(Player p, Block host, Def def) {
        Circuit circuit = plugin.circuit();
        Block target;
        Block up = host.getRelative(BlockFace.UP);
        if (up.getType().isAir() && circuit.get(up) == null) {
            target = up;
        } else if (host.getType().isAir() || def.kind == Kind.BOARD) {
            target = host;
        } else {
            hint(p, "На плате нет свободного места — освободите верх блока.");
            return;
        }
        Block support = target.getRelative(BlockFace.DOWN);
        if (def.kind != Kind.BOARD && support.getType().isAir()) {
            hint(p, "Компонентам нужна опора: поставьте блок-подставку или плату.");
            return;
        }
        ItemStack hand = p.getInventory().getItemInMainHand();
        Orientation o = Orientation.fromYaw(p.getLocation().getYaw());

        if (def.kind == Kind.BOARD) {
            // «подставка/стол» — это просто блок, без компонента
            if (target.getType().isAir()) {
                target.setType(plugin.items().baseMaterial());
                circuit.persistence().markHost(target, true);
            }
            if (p.getGameMode() != GameMode.CREATIVE) {
                consume(hand, p);
            }
            hint(p, "Рабочий стол установлен. ПКМ по нему — поставить компонент.");
            return;
        }

        CompInstance c = new CompInstance(LocationKey.of(target), def);
        c.orientation = o;
        if (def.tunable) {
            c.setTune(1.0);
        }
        if (p.getGameMode() != GameMode.CREATIVE) {
            consume(hand, p);
        }
        if (target.getType().isAir()) {
            target.setType(plugin.items().baseMaterial());
            circuit.persistence().markHost(target, true);
        }
        circuit.put(c);
        circuit.persistence().write(c);
        plugin.renderer().requestUpdate(c);
        circuit.markDirty();
        hint(p, def.nameRu + " установлен. Shift+ПКМ — повернуть.");
    }

    private void consume(ItemStack hand, Player p) {
        if (hand.getAmount() <= 1) {
            p.getInventory().setItemInMainHand(null);
        } else {
            hand.setAmount(hand.getAmount() - 1);
        }
    }

    private void toggle(CompInstance c) {
        boolean on = c.isClosed();
        c.state = on ? CompStates.OFF : CompStates.CLOSED;
        c.modelState = ModelState.of(c);
        plugin.sounds().click(c.key, !on);
        plugin.circuit().persistence().write(c);
        plugin.renderer().requestUpdate(c);
        plugin.circuit().markDirty();
    }

    private void tune(Player p, CompInstance c, double step) {
        double dir = p.isSneaking() ? -1 : 1;
        c.setTune(c.tuneFraction() + dir * step);
        plugin.circuit().persistence().write(c);
        plugin.renderer().requestUpdate(c);
        plugin.circuit().markDirty();
        String value = c.def.tunable ? Num.volt(c.tunedVoltage()) + " (" + Num.pct(c.tuneFraction()) + "%)"
                : "ползунок " + Num.pct(c.tuneFraction()) + "%";
        hint(p, c.def.nameRu + ": " + value);
    }

    private void rotate(CompInstance c) {
        int next = c.def.orientable ? (c.orientation.index + 1) % 4 : 0;
        c.orientation = Orientation.byIndex(next);
        c.modelState = ModelState.of(c);
        plugin.circuit().persistence().write(c);
        plugin.renderer().requestUpdate(c);
        plugin.circuit().markDirty();
    }

    private void measure(Player p, CompInstance c) {
        double r = Math.abs(c.current) > 1e-9 ? Math.abs(c.voltage / c.current) : 0;
        String line = String.format(java.util.Locale.US, "%s: U=%.3f В  I=%.4f А  P=%.3f Вт  R=%.1f Ом",
                c.def.nameRu, c.voltage, c.current, c.power, r);
        me.arena.electronics.util.Text.action(p, "&e" + line);
        if (c.state == CompStates.BURNT) {
            hint(p, "Неисправен: " + c.fault);
        }
    }

    public void info(Player p, CompInstance c) {
        StringBuilder sb = new StringBuilder();
        sb.append(Items.color("&a⚡ " + c.def.nameRu));
        String nominal = c.def.nominal();
        if (!nominal.isEmpty()) {
            sb.append(Items.color(" &7(" + nominal + ")"));
        }
        sb.append("\n");
        sb.append(Items.color("&7U=&f")).append(Num.volt(c.voltage))
                .append(Items.color("  &7I=&f")).append(Num.amp(Math.abs(c.current)))
                .append(Items.color("  &7P=&f")).append(Num.watt(c.power));
        sb.append("\n");
        sb.append(Items.color("&7Состояние: &f")).append(CompStates.name(c.state))
                .append(Items.color("  &7Ориентация: &f")).append(c.orientation.name())
                .append(Items.color("  &7Индекс модели: &f")).append(c.modelState)
                .append(Items.color(" &8(/elec model)"));
        if (c.state == CompStates.BURNT) {
            sb.append("\n").append(Items.color("&cНеисправен: " + c.fault));
            sb.append("\n").append(Items.color("&e/elec repair &7— починить"));
        }
        p.sendMessage(sb.toString());
    }

    // ------------------------------------------------------------ ломание

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        CompInstance c = plugin.circuit().get(b);
        if (c != null) {
            e.setCancelled(true);
            if (!e.getPlayer().hasPermission("electronics.build")) {
                return;
            }
            plugin.circuit().remove(c.key);
            plugin.circuit().persistence().clear(b);
            plugin.renderer().drop(c.key);
            if (plugin.config().dropAsItem) {
                b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.4, 0.5),
                        plugin.items().componentItem(c.def, 1));
            }
            b.setType(Material.AIR);
            plugin.circuit().markDirty();
            return;
        }
        if (plugin.circuit().persistence().isHost(b)) {
            Block up = b.getRelative(BlockFace.UP);
            if (plugin.circuit().get(up) != null && !e.getPlayer().isSneaking()) {
                e.setCancelled(true);
                hint(e.getPlayer(), "Сначала уберите компонент с платы (сломите его).");
                return;
            }
            plugin.circuit().persistence().markHost(b, false);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (plugin.config().protectBlocks && plugin.circuit().get(e.getBlock()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(org.bukkit.event.entity.EntityExplodeEvent e) {
        if (!plugin.config().protectBlocks) {
            return;
        }
        e.blockList().removeIf(b -> plugin.circuit().get(b) != null || plugin.circuit().persistence().isHost(b));
    }

    private void hint(Player p, String text) {
        if (!plugin.config().hintsEnabled) {
            return;
        }
        me.arena.electronics.util.Text.action(p, "&b\uD83D\uDCA1 " + text);
    }
}
