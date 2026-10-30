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
        // «Зачистка/лужение»: ПКМ в воздухе с инструментом в руке — без цели,
        // чтобы не спорить с установкой блоков и с открытием столов.
        if (held != null && held.id.equals("wire_stripper")
                && e.getAction().name().contains("AIR") && p.isSneaking()) {
            e.setCancelled(true);
            stripWires(p, held);
            return;
        }
        // «USB-программатор»: ПКМ по поставленному программатору — окно прошивки.
        if (e.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Block pb = e.getClickedBlock();
            if (pb != null) {
                me.arena.electronics.circuit.CompInstance pc = plugin.circuit().get(pb);
                if (pc != null && pc.def != null && pc.def.kind == me.arena.electronics.registry.Kind.STATION
                        && pc.def.id.equals("programmer_usb")) {
                    e.setCancelled(true);
                    me.arena.electronics.gui.McuGui.open(plugin, p, pb);
                    return;
                }
            }
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Block b = e.getClickedBlock();
        if (b == null) {
            return;
        }
        // (00) геозонд: ПКМ по породе — разведка недр. Дешевле сделать это до
        // ветки «тул в руке греет жало», иначе зонд грел бы паяльник.
        if (held != null && held.id.equals("geoprobe")) {
            e.setCancelled(true);
            prospect(p, b);
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
            case STATION -> {
                // у тигля и стана своё окно: там нет паяльника, припоя и жара,
                // там материалы из инвентаря уходит в операцию целиком
                if (me.arena.electronics.recipes.Metallurgy.isStation(existing.def.id)) {
                    me.arena.electronics.gui.MetalGui.open(plugin, p, existing.def.id);
                } else {
                    me.arena.electronics.gui.SolderGui.open(plugin, p, b, true);
                }
            }
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
        veinBonus(e, b);
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

    // --------------------------------------------------------- руды и инструменты

    /**
     * Куски руды из вскрытой жилы. Обычная порода вокруг тоже ломается, но руда
     * идёт только пока точка внутри пояса живой (вскрытой и не выбранной) жилы:
     * drops=false, и вместо булыжника сыпется наш предмет.
     */
    @SuppressWarnings("unused")
    private void veinBonus(BlockBreakEvent e, Block b) {
        me.arena.electronics.deposit.Deposits d = plugin.deposits();
        if (d == null || !d.enabled() || e.isCancelled()) {
            return;
        }
        if (!isRock(b.getType())) {
            return;
        }
        me.arena.electronics.deposit.Deposits.Vein vein = d.veinAt(b.getX(), b.getZ());
        if (!d.productive(vein, b.getY())) {
            return;
        }
        org.bukkit.inventory.ItemStack hand = e.getPlayer().getInventory().getItemInMainHand();
        String tool = hand.getType().name();
        if (!tool.contains("PICKAXE")) {
            Items.message(e.getPlayer(), "&7кирка: без инструментов породу не раздробить");
            return;
        }
        String oreId = d.takeOre(b.getX(), b.getY(), b.getZ());
        if (oreId == null) {
            return;
        }
        me.arena.electronics.registry.Def ore = me.arena.electronics.registry.Components.byId(oreId);
        if (ore == null) {
            return;
        }
        int n = d.chunksPerBlock(vein, (b.getX() * 31 + b.getY() * 17 + b.getZ()) & 1023);
        e.setDropItems(false);
        e.setExpToDrop(0);
        for (int i = 0; i < n; i++) {
            b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5),
                    plugin.items().componentItem(ore, 1));
        }
        b.getWorld().spawnParticle(org.bukkit.Particle.ELECTRIC_SPARK,
                b.getLocation().add(0.5, 0.5, 0.5), 14, 0.3, 0.3, 0.3, 0.02);
        b.getWorld().playSound(b.getLocation().add(0.5, 0.5, 0.5),
                org.bukkit.Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.9f);
    }

    /** Порода, в которой живёт жила. Глубинные версии включены, воздух — нет. */
    private static boolean isRock(org.bukkit.Material m) {
        String n = m.name();
        return n.equals("STONE") || n.startsWith("DEEPSLATE") && !n.contains("ORE")
                || n.startsWith("COBBLED_DEEPSLATE") || n.equals("TUFF") || n.equals("GRANITE")
                || n.equals("DIORITE") || n.equals("ANDESITE") || n.equals("NETHERRACK");
    }

    /** Геозонд: вскрыть/осмотреть жилу под точкой. Тратит прочность зонда. */
    private void prospect(Player p, Block b) {
        me.arena.electronics.deposit.Deposits d = plugin.deposits();
        if (d == null || !d.enabled()) {
            Items.message(p, "&7разведка недр выключена в config.yml (deposits.enabled)");
            return;
        }
        if (!isRock(b.getType())) {
            Items.message(p, "&7жило ищут по породе, а не по &f" + b.getType().name().toLowerCase(java.util.Locale.ROOT));
            return;
        }
        String msg = d.prospect(b.getX(), b.getY(), b.getZ());
        Items.message(p, "&b⛏ &f" + msg);
        wear(p, 1);
        p.getWorld().spawnParticle(org.bukkit.Particle.SOUL, b.getLocation().add(0.5, 1.1, 0.5),
                22, 0.4, 0.5, 0.4, 0.01);
        b.getWorld().playSound(b.getLocation(), org.bukkit.Sound.BLOCK_COMPOSTER_EMPTY, 0.8f, 1.3f);
    }

    /**
     * Зачистка: изолированные провода в инвентаре -> голый монтажный провод.
     * 1 зачистка = 1 провод, за раз не больше 4, инструмент садится на прочность.
     */
    private void stripWires(Player p, me.arena.electronics.registry.Def tool) {
        int done = 0;
        org.bukkit.inventory.ItemStack[] slots = p.getInventory().getStorageContents();
        for (int i = 0; i < slots.length && done < 4; i++) {
            org.bukkit.inventory.ItemStack s = slots[i];
            if (s == null || s.getType().isAir()) {
                continue;
            }
            me.arena.electronics.registry.Def d = Items.defOf(s);
            if (d == null || !isInsulated(d.id)) {
                continue;
            }
            s.setAmount(s.getAmount() - 1);
            if (s.getAmount() <= 0) {
                p.getInventory().setItem(i, null);
            }
            done++;
        }
        if (done == 0) {
            Items.message(p, "&7нечего зачищать: положи в инвентарь изолированный провод");
            return;
        }
        me.arena.electronics.registry.Def bare =
                me.arena.electronics.registry.Components.byId("wire_bare");
        if (bare != null) {
            java.util.Map<Integer, ? extends org.bukkit.inventory.ItemStack> left =
                    p.getInventory().addItem(plugin.items().componentItem(bare, done));
            for (org.bukkit.inventory.ItemStack overflow : left.values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
        wear(p, done);
        p.updateInventory();
        Items.message(p, "&a⚡ Зачищено проводов: &f" + done + " &7→ голый монтажный провод"
                + " &8(лудить на столе: голый + припой)");
        p.getWorld().playSound(p.getLocation(), org.bukkit.Sound.ITEM_ARMOR_EQUIP_CHAIN, 0.7f, 1.5f);
    }

    /** Изолированный провод (тот, что зачищают). Голый и лужёный — уже металл. */
    private static boolean isInsulated(String id) {
        return id.equals("wire_red") || id.equals("wire_black")
                || id.equals("wire_yellow") || id.equals("wire_blue");
    }

    /**
     * Списать N прочности с инструмента в правой руке. Носитель — BRUSH, у него
     * прочность есть; когда она кончается, предмет ломается и исчезает, как
     * настоящий инструмент (молча: у нас нет права «бессмертный инструмент»).
     */
    private void wear(Player p, int amount) {
        org.bukkit.inventory.ItemStack stack = p.getInventory().getItemInMainHand();
        if (stack == null || stack.getType().isAir() || amount <= 0) {
            return;
        }
        int max = stack.getType().getMaxDurability();
        if (max <= 1 || !(stack.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dm)) {
            return;
        }
        int now = dm.getDamage() + amount;
        if (now >= max) {
            p.getInventory().setItemInMainHand(null);
            Items.message(p, "&cинструмент сломался — спаяй новый");
            p.updateInventory();
            return;
        }
        dm.setDamage(now);
        stack.setItemMeta((org.bukkit.inventory.meta.ItemMeta) dm);
        p.updateInventory();
    }

    private void hint(Player p, String text) {
        if (!plugin.config().hintsEnabled) {
            return;
        }
        me.arena.electronics.util.Text.action(p, "&b\uD83D\uDCA1 " + text);
    }
}
