package me.arena.electronics.command;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.circuit.Circuit;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.circuit.ModelState;
import me.arena.electronics.circuit.Simulation;
import me.arena.electronics.lab.Netlist;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.util.Num;
import org.bukkit.block.Block;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /elec — выдача, диагностика, режимы, ремонт, состояния модели. */
public final class ElecCommand implements CommandExecutor, TabCompleter {

    private final ElectronicsPlugin plugin;

    public ElecCommand(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String a0 = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "help";
        switch (a0) {
            case "help" -> help(s);
            case "give" -> give(s, args);
            case "list" -> list(s, args);
            case "model" -> model(s, args);
            case "info" -> info(s);
            case "sim" -> sim(s, args);
            case "repair" -> repair(s, args);
            case "clear" -> clear(s, args);
            case "rotate" -> rotate(s);
            case "mode" -> mode(s, args);
            case "recipe" -> recipe(s);
            case "solder" -> solder(s);
            case "reload" -> reload(s);
            case "veins" -> veins(s, args);
            case "rescan" -> rescan(s);
            case "show" -> show(s, args);
            case "selftest" -> selftest(s);
            case "schematic" -> schematic(s, args);
            case "rig" -> rig(s, args);
            case "build" -> build(s, args);
            case "dump" -> dump(s, args);
            default -> help(s);
        }
        return true;
    }

    private void help(CommandSender s) {
        s.sendMessage(Items.color("&a&l⚡ Electronics &7— учебная электроника для 1.21.11"));
        s.sendMessage(Items.color("&7Состояний модели в ресурспаке: &f" + plugin.stateModelCount()
                + " &7(45 видов × состояния × 6 ориентаций — больше 800, как и обещано)"));
        s.sendMessage(Items.color("&e/elec give <компонент|all> [число] &7— выдать"));
        s.sendMessage(Items.color("&e/elec list [фильтр] &7— список компонентов"));
        s.sendMessage(Items.color("&e/elec model <id> <состояние 0-5> <ориентация 0-5> &7— индекс модели"));
        s.sendMessage(Items.color("&e/elec info &7— состояние движка (узлы, islands, micros)"));
        s.sendMessage(Items.color("&e/elec sim [тики] &7— пересчитать сейчас"));
        s.sendMessage(Items.color("&e/elec repair [all] &7— починить (под прицелом / все)"));
        s.sendMessage(Items.color("&e/elec rotate &7— повернуть компонент под прицелом"));
        s.sendMessage(Items.color("&e/elec mode <display|none> &7— способ отрисовки"));
        s.sendMessage(Items.color("&e/elec recipe &7— рецепты предмета под прицелом/в руке"));
        s.sendMessage(Items.color("&e/elec solder &7— открыть паяльный стол (для урока; в мире — ПКМ по столу)"));
        s.sendMessage(Items.color("&e/elec schematic save|check|print <имя> &7— работа с «пустой схемой»"));
        s.sendMessage(Items.color("&e/elec build list|<имя>|save <имя> &7— готовые сборки (учебные примеры)"));
        s.sendMessage(Items.color("&e/elec rig [число] &7— что видит ближайший механизм: обороты, момент, нагрузка, ход"));
        s.sendMessage(Items.color("&e/elec dump [мир] [число] &7— U/I/P каждой детали (удобно с консоли)"));
        s.sendMessage(Items.color("&7     с консоли: /elec build <имя> <мир> <x> <y> <z>"));
        s.sendMessage(Items.color("&e/elec reload &7— конфиг + ресурспак-реестр"));
        s.sendMessage(Items.color("&7Подробнее: README (установка ресурспака, как собирать цепи)."));
    }

    // ---------------------------------------------------------------- give/list

    private void give(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        if (!p.hasPermission("electronics.give")) {
            s.sendMessage(Items.color("&cНет права electronics.give"));
            return;
        }
        int count = args.length > 2 ? parseInt(args[2], 1) : 1;
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        if (what.isEmpty()) {
            s.sendMessage(Items.color("&eУкажите компонент: /elec give led_red 8 &7(или all)"));
            return;
        }
        if (what.equals("all")) {
            for (Def d : Components.all()) {
                p.getInventory().addItem(plugin.items().componentItem(d, count));
            }
            s.sendMessage(Items.color("&aВыдано всё: " + Components.size() + " видов x" + count));
            return;
        }
        Def def = Components.byId(what);
        if (def == null) {
            s.sendMessage(Items.color("&cНеизвестный компонент «" + what + "». /elec list"));
            return;
        }
        p.getInventory().addItem(plugin.items().componentItem(def, count));
        s.sendMessage(Items.color("&a+" + count + " &f" + def.nameRu));
    }

    private void list(CommandSender s, String[] args) {
        String filter = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "";
        int n = 0;
        StringBuilder line = new StringBuilder();
        for (Def d : Components.all()) {
            if (!filter.isEmpty() && !d.id.contains(filter) && !d.kind.name().toLowerCase(Locale.ROOT).contains(filter)) {
                continue;
            }
            if (n > 0 && n % 4 == 0) {
                s.sendMessage(Items.color("&7" + line.toString().trim()));
                line.setLength(0);
            }
            line.append("&a").append(d.id).append(" &8[").append(d.kind.name()).append("]&7  ");
            n++;
        }
        if (line.length() > 0) {
            s.sendMessage(Items.color("&7" + line.toString().trim()));
        }
        s.sendMessage(Items.color("&eВсего типов: " + n + " &7(всего " + Components.size() + ")"));
    }

    // ---------------------------------------------------------------- model/info

    private void model(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Items.color("&e/elec model <id> [состояние 0-5] [ориентация 0-5]"));
            s.sendMessage(Items.color("&7Состояния: off/dim/on/burnt/rev/closed, ориентации: north/east/south/west/up/down"));
            return;
        }
        Def def = Components.byId(args[1]);
        if (def == null) {
            s.sendMessage(Items.color("&cНеизвестный компонент."));
            return;
        }
        int st = args.length > 2 ? parseInt(args[2], 0) : 0;
        int or = args.length > 3 ? parseInt(args[3], 0) : 0;
        int idx = def.stateModel(st, or);
        s.sendMessage(Items.color("&a" + def.id + " &7state=" + st + " orient=" + or
                + " &8-> &fиндекс модели #" + idx + " &8(" + ModelState.describe(idx) + ")"));
        s.sendMessage(Items.color("&7Модель ресурспака: &felectronics:" + def.id
                + (st > 0 ? "_" + Components.STATE_NAMES[Math.min(st, Components.STATE_NAMES.length - 1)] : "")));
        s.sendMessage(Items.color("&7Диапазон индексов этого компонента: &f" + def.baseModelIndex()
                + ".." + (def.baseModelIndex() + def.modelSlice() - 1)
                + " &7(всего состояний: &f" + plugin.stateModelCount() + "&7)"));
    }

    private void info(CommandSender s) {
        Circuit c = plugin.circuit();
        s.sendMessage(Items.color("&a=== Electronics ==="));
        s.sendMessage(Items.color("&7Компонентов в памяти: &f" + c.size()));
        s.sendMessage(Items.color("&7Островков в последнем расчёте: &f" + c.lastIslands()
                + " &7(участников " + c.lastComponents() + ")"));
        s.sendMessage(Items.color("&7Время расчёта: &f" + (c.lastSolveNanos() / 1000) + " мкс"));
        s.sendMessage(Items.color("&7Состояний модели: &f" + plugin.stateModelCount()));
        s.sendMessage(Items.color("&7Рендер: &f" + plugin.config().renderMode
                + " &7шкала " + plugin.config().renderScale + " offset " + plugin.config().offsetY));
        s.sendMessage(Items.color("&7Основа: &f" + plugin.items().baseMaterial().name()
                + " &7подставка: &f" + plugin.items().pedestalMaterial().name()));
        s.sendMessage(Items.color("&7Плагин: &f" + plugin.getDescription().getVersion()
                + " &7API " + plugin.getDescription().getAPIVersion()));
        if (!c.lastError().isEmpty()) {
            s.sendMessage(Items.color("&cПоследняя ошибка: &f" + c.lastError()));
        }
    }

    private void sim(CommandSender s, String[] args) {
        int ticks = args.length > 1 ? parseInt(args[1], 1) : 1;
        for (int i = 0; i < ticks; i++) {
            plugin.forceSimulate();
        }
        s.sendMessage(Items.color("&aПересчитано. t=" + plugin.circuit().tick()
                + " islands=" + plugin.circuit().lastIslands()
                + " comps=" + plugin.circuit().lastComponents()));
    }

    /** Диагностика привода: то, чем удобно проверять сборку на уроке. */
    private void rig(CommandSender s, String[] args) {
        me.arena.electronics.kinetics.KineticEngine eng = plugin.kinetics();
        if (args.length > 1 && args[1].equalsIgnoreCase("place")) {
            org.bukkit.World w;
            int x;
            int y;
            int z;
            if (args.length >= 6) {
                w = org.bukkit.Bukkit.getWorld(args[2]);
                x = parseInt(args[3], 0);
                y = parseInt(args[4], 64);
                z = parseInt(args[5], 0);
            } else if (s instanceof Player p) {
                org.bukkit.block.Block t = p.getTargetBlock(null, 16);
                w = t.getWorld();
                x = t.getX();
                y = t.getY() + 1;
                z = t.getZ();
            } else {
                s.sendMessage(Items.color("&cс консоли: /elec rig place <мир> <x> <y> <z>"));
                return;
            }
            s.sendMessage(Items.color("&a" + eng.demo(w, x, y, z)));
            s.sendMessage(Items.color("&7через 5-10 с снова набери &f/elec rig&7 — будет видно, сколько блоков ушло"));
            return;
        }
        if (args.length > 1 && args[1].equalsIgnoreCase("undo")) {
            eng.undoDemo();
            s.sendMessage(Items.color("&aучебная сборка убрана, блоки восстановлены"));
            return;
        }
        me.arena.electronics.kinetics.KineticEngine.Rig r = null;
        if (s instanceof Player p) {
            r = eng.nearest(p.getEyeLocation(), args.length > 1 ? parseInt(args[1], 24) : 12);
        }
        if (r == null && !eng.rigs().isEmpty()) {
            r = eng.rigs().iterator().next();
        }
        s.sendMessage(Items.color("&7механизмов в мире: &f" + eng.rigCount()
                + "&7, сломано блоков приводом: &f" + eng.blocksBroken()
                + "&7, привод &f" + (plugin.config().kineticsEnabled ? "включён" : "выключен")));
        if (r == null) {
            s.sendMessage(Items.color("&eпоставь детали привода рядом: вал, шестерню, пилу и мотор —"
                    + " мотор должен стоять вплотную к цепи"));
            return;
        }
        s.sendMessage(Items.color("&6— механизм &f" + r.handle));
        StringBuilder parts = new StringBuilder();
        for (CompInstance c : r.parts) {
            parts.append(c.def.nameRu).append(" (").append((int) c.spin).append("°), ");
        }
        s.sendMessage(Items.color("&7детали: &f" + (parts.length() > 2
                ? parts.substring(0, parts.length() - 2) : "—")));
        for (CompInstance m : r.motors) {
            s.sendMessage(Items.color("&7мотор: &f" + m.def.nameRu + "&7, U = &f" + Num.volt(m.voltage)
                    + "&7, I = &f" + Num.amp(m.current) + "&7, ЭДС доли = &f"
                    + String.format("%.0f%%", m.speedPrev * 100) + "&7, оборотов &f"
                    + (int) (m.speedPrev * Math.max(1.0, m.def.rpm)) + "&7 ("
                    + (int) m.def.rpm + " ХХ)"));
        }
        s.sendMessage(Items.color("&7вал: &f" + (int) r.state.rpm() + " об/мин&7, J = &f"
                + String.format("%.3f", r.spec.inertiaTotal) + " кг·м²&7, масса &f"
                + String.format("%.0f", r.spec.massTotal) + " кг&7, редуктор &f1:"
                + String.format("%.1f", r.spec.gearProduct)));
        s.sendMessage(Items.color("&7нагрузка: &f" + (int) (r.state.loadFraction * 100) + "%"
                + "&7, момент доступен &f" + String.format("%.2f", r.last.torqueAvail)
                + "&7 / нужен &f" + String.format("%.2f", r.last.torqueLoad) + " Н·м"
                + "&7, мощность &f" + Num.watt(r.last.powerW)));
        s.sendMessage(Items.color("&7инструментов: &f" + r.spec.toolCount
                + "&7, блоков обработано: &f" + r.state.blocksDone
                + "&7, проходов: &f" + r.state.strikesDone));
        if (r.spec.hasWheels) {
            s.sendMessage(Items.color("&7каретка: трение опоры &f"
                    + String.format("%.3f", r.spec.mu) + "&7, скорость &f"
                    + String.format("%.2f", r.last.speedMps) + " м/с&7, пройдено &f"
                    + String.format("%.2f", r.state.travel) + " м до шага"));
        }
        s.sendMessage(Items.color("&e" + r.state.note));
    }

    // ---------------------------------------------------------------- правка мира

    private void repair(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        if (!p.hasPermission("electronics.admin")) {
            s.sendMessage(Items.color("&cНет права electronics.admin"));
            return;
        }
        boolean admin = p.hasPermission("electronics.admin");
        int fixed = 0;
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            if (!admin) {
                s.sendMessage(Items.color("&c«all» — право администратора. Чини по одной детали,"
                        + " стоя у паяльного стола с горячим жалом."));
                return;
            }
            for (CompInstance c : plugin.circuit().all()) {
                if (Simulation.repair(c)) {
                    plugin.circuit().persistence().write(c);
                    plugin.renderer().requestUpdate(c);
                    fixed++;
                }
            }
        } else {
            CompInstance c = lookAt(p);
            if (c == null) {
                s.sendMessage(Items.color("&cНет компонента под прицелом."));
                return;
            }
            if (!c.burnt()) {
                s.sendMessage(Items.color("&eДеталь исправна — чинить нечего."));
                return;
            }
            if (!admin) {
                String why = survivalRepair(p, c);
                if (why != null) {
                    s.sendMessage(Items.color("&c" + why));
                    return;
                }
            }
            Simulation.repair(c);
            plugin.circuit().persistence().write(c);
            plugin.renderer().requestUpdate(c);
            fixed = 1;
        }
        plugin.circuit().markDirty();
        s.sendMessage(Items.color("&aПочинено компонентов: " + fixed));
    }

    /**
     * Ремонт без админского права: горячее жало, стол рядом и оплётка с припоем.
     * Возвращает причину отказа или null, если всё честно списали.
     */
    private String survivalRepair(Player p, CompInstance c) {
        Block near = null;
        double best = 25.0;
        for (CompInstance st : plugin.circuit().all()) {
            if (st.def == null || st.def.kind != me.arena.electronics.registry.Kind.STATION) {
                continue;
            }
            Block b = st.key.block();
            if (b == null) {
                continue;
            }
            double d2 = b.getLocation().distanceSquared(c.key.block().getLocation());
            if (d2 < best) {
                best = d2;
                near = b;
            }
        }
        if (near == null) {
            return "нужен паяльный стол не дальше 4 блоков от детали (и источник тепла рядом)";
        }
        if (!me.arena.electronics.recipes.Soldering.heatSourceNear(near)) {
            return "стол холодный: рядом нужен источник тепла — печь, костёр, лава или факел";
        }
        String why = me.arena.electronics.recipes.Soldering.repairBlocker(p);
        if (why != null) {
            return why;
        }
        me.arena.electronics.recipes.Soldering.consumeRepair(p);
        p.sendMessage(Items.color("&7Списаны оплётка ×1 и припой ×1 &8(стол на расстоянии "
                + String.format(java.util.Locale.US, "%.1f", Math.sqrt(best)) + " бл.)"));
        return null;
    }

    private void clear(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        if (!p.hasPermission("electronics.admin")) {
            s.sendMessage(Items.color("&cНет права electronics.admin"));
            return;
        }
        int n = 0;
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            for (CompInstance c : new ArrayList<>(plugin.circuit().all())) {
                remove(c);
                n++;
            }
        } else {
            CompInstance c = lookAt(p);
            if (c == null) {
                s.sendMessage(Items.color("&cНет компонента под прицелом."));
                return;
            }
            remove(c);
            n = 1;
        }
        plugin.circuit().markDirty();
        s.sendMessage(Items.color("&aУбрано компонентов: " + n));
    }

    private void remove(CompInstance c) {
        plugin.circuit().remove(c.key);
        plugin.circuit().persistence().clear(c.key.block());
        plugin.renderer().drop(c.key);
    }

    private void rotate(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        CompInstance c = lookAt(p);
        if (c == null) {
            s.sendMessage(Items.color("&cНет компонента под прицелом."));
            return;
        }
        c.orientation = me.arena.electronics.registry.Orientation.byIndex((c.orientation.index + 1) % 4);
        c.modelState = ModelState.of(c);
        plugin.circuit().persistence().write(c);
        plugin.renderer().requestUpdate(c);
        plugin.circuit().markDirty();
        s.sendMessage(Items.color("&a" + c.def.nameRu + " → " + c.orientation.name() + " (модель #" + c.modelState + ")"));
    }

    private void mode(CommandSender s, String[] args) {
        if (!s.hasPermission("electronics.admin")) {
            s.sendMessage(Items.color("&cНет права electronics.admin"));
            return;
        }
        if (args.length < 2) {
            s.sendMessage(Items.color("&eТекущий режим: " + plugin.config().renderMode + " &7(доступно: display, none)"));
            return;
        }
        plugin.config().renderMode = args[1].toLowerCase(Locale.ROOT);
        plugin.config().save();
        if (plugin.config().renderMode.equals("none")) {
            plugin.renderer().dropAll();
        }
        s.sendMessage(Items.color("&aРежим рендера: " + plugin.config().renderMode));
    }

    /** Открыть стол пайки руками (урок/отладка): требования к теплу мягкие. */
    private void solder(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков: в мире открывай стол ПКМ по нему."));
            return;
        }
        me.arena.electronics.gui.SolderGui.open(plugin, p, null, false);
    }

    private void recipe(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        Def def = Items.defOf(p.getInventory().getItemInMainHand());
        if (def == null) {
            CompInstance c = lookAt(p);
            def = c == null ? null : c.def;
        }
        if (def == null) {
            s.sendMessage(Items.color("&cВозьмите компонент в руку или наведите на него."));
            return;
        }
        java.util.Map<String, me.arena.electronics.recipes.Metallurgy.Recipe> metal =
                me.arena.electronics.recipes.Metallurgy.table(def.id);
        if (metal != null) {
            s.sendMessage(Items.color("&a⚒ «" + def.nameRu + "» &7— станок на &f" + metal.size()
                    + " &7операций: держи материалы в инвентаре и нажми ПКМ по станку"));
        } else {
            String mt = me.arena.electronics.recipes.Metallurgy.ingredientText(def.id);
            if (!mt.isEmpty()) {
                s.sendMessage(Items.color("&7Металлургия: " + mt));
            }
        }
        if (me.arena.electronics.registry.Components.isSoldered(def.id)) {
            s.sendMessage(Items.color("&a⚡ «" + def.nameRu + "» &7паяется на столе&8, "
                    + "в верстаке этого нет"));
            s.sendMessage(Items.color("&7Заготовка: &f"
                    + me.arena.electronics.recipes.Soldering.ingredientText(def.id)));
            s.sendMessage(Items.color("&7Припой: &f×"
                    + me.arena.electronics.recipes.Soldering.solderCost(def)
                    + (me.arena.electronics.recipes.Soldering.needsFlux(def) ? "&7, нужен &fфлюс ×1"
                    : "&7, флюс не нужен")
                    + " &7| жало: &f" + me.arena.electronics.recipes.Soldering.HEAT_HOT + "°+"));
            s.sendMessage(Items.color("&8/elec solder &7— открыть стол руками"));
            return;
        }
        org.bukkit.inventory.Recipe r = plugin.recipes().byResult(def);
        if (r == null) {
            s.sendMessage(Items.color("&eДля «" + def.nameRu + "» рецепт не задан."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        if (r instanceof org.bukkit.inventory.ShapedRecipe sh) {
            sb.append(Items.color("&aФорма: "));
            for (String row : sh.getShape()) {
                sb.append('\n').append(Items.color("&7"));
                for (char ch : row.toCharArray()) {
                    org.bukkit.inventory.RecipeChoice choice = sh.getChoiceMap().get(ch);
                    String name = " ... ";
                    if (choice instanceof org.bukkit.inventory.RecipeChoice.MaterialChoice mc) {
                        var mats = mc.getChoices();
                        name = mats.isEmpty() ? " ... " : mats.get(0).name();
                    } else if (choice instanceof org.bukkit.inventory.RecipeChoice.ExactChoice ec) {
                        var its = ec.getChoices();
                        name = its.isEmpty() ? " ... " : its.get(0).getType().name();
                    }
                    sb.append(" ").append(name).append(" ");
                }
            }
        }
        s.sendMessage(sb.toString());
    }

    /** Разведка недр вокруг игрока: что обещают недры в радиусе N чанков. */
    private void veins(CommandSender s, String[] args) {
        me.arena.electronics.deposit.Deposits d = plugin.deposits();
        if (d == null || !d.enabled()) {
            s.sendMessage(Items.color("&cЖилы выключены&7: deposits.enabled: false в config.yml"));
            return;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков: нужно стоять на месте, вокруг"
                    + " которого смотреть недра."));
            return;
        }
        int r = 2;
        if (args.length > 1) {
            try {
                r = Math.max(1, Math.min(8, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
                // остаёмся на 2 чанках
            }
        }
        for (String line : d.survey(p.getLocation().getBlockX(), p.getLocation().getBlockZ(), r).split("\n")) {
            s.sendMessage(Items.color("&7" + line));
        }
    }

    private void reload(CommandSender s) {
        if (!s.hasPermission("electronics.admin")) {
            s.sendMessage(Items.color("&cНет права electronics.admin"));
            return;
        }
        plugin.reloadAll();
        s.sendMessage(Items.color("&aПерезагружено. Компонентов: " + Components.size()
                + ", состояний модели: " + plugin.stateModelCount()));
    }

    private void rescan(CommandSender s) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        if (!p.hasPermission("electronics.admin")) {
            s.sendMessage(Items.color("&cНет права electronics.admin"));
            return;
        }
        int n = plugin.circuit().persistence().fullScan(p.getWorld());
        p.sendMessage(Items.color("&aПолное сканирование: найдено записей " + n));
    }

    /**
     * Самопроверка без игрока: рисует тестовую цепь в редакторе схемы, гоняет
     * её через решатель и сверяет показания, плюс проверяет каталог.
     */
    /** Публично: вызывает Config при selftest: true на старте сервера. */
    public void selftest(CommandSender s) {
        // (0) минимальный контур: источник и резистор в двух соседних клетках
        me.arena.electronics.gui.SchematicGui g0 =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        g0.put(0, 0, "battery_9", 1);
        g0.put(0, 1, "resistor_330r", 1);
        g0.recalcQuiet();
        double i0 = g0.currentAt(0, 1);
        s.sendMessage(Items.color("&7два узла в ряд (цепь разомкнута): I = &f" + Num.amp(i0)
                + " &7U = &f" + Num.volt(g0.voltageAt(0, 1)) + " &8— должно быть ~0: без возврата тока нет"));
        s.sendMessage(Items.color("&8nets batt: &f" + g0.netsAt(0, 0) + " &8| res: &f" + g0.netsAt(0, 1)
                + " &8| U_res = &f" + Num.volt(g0.voltageAt(0, 1))));
        me.arena.electronics.gui.SchematicGui g =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        // контур: батарейка 9 В -> 330 Ом -> светодиод -> провод по периметру -> земля
        g.put(0, 0, "wire_red", 0);
        g.put(0, 1, "battery_9", 1);
        g.put(0, 2, "wire_red", 0);
        g.put(0, 3, "resistor_330r", 1);
        g.put(0, 4, "wire_red", 0);
        g.put(0, 5, "led_red", 1);
        g.put(0, 6, "wire_red", 0);
        g.put(1, 6, "wire_black", 0);
        g.put(2, 6, "wire_black", 0);
        g.put(2, 5, "wire_black", 0);
        g.put(2, 4, "ground_bus", 0);
        g.put(2, 3, "wire_black", 0);
        g.put(2, 2, "wire_black", 0);
        g.put(2, 1, "wire_black", 0);
        g.put(1, 0, "wire_black", 0);
        g.put(2, 0, "wire_black", 0);
        g.recalcQuiet();
        double u = g.voltageAt(0, 5);
        double i = g.currentAt(0, 5);
        boolean pass = i > 0.015 && i < 0.026 && u > 1.9 && u < 2.8 && g.islands() >= 1;
        s.sendMessage(Items.color((pass ? "&aPASS " : "&cFAIL ")
                + "&7редактор схемы: LED &f" + Num.volt(u) + " &7при токе &f" + Num.amp(i)
                + "&7, островков &f" + g.islands() + " &8(ожидалось ~20 мА, ~2.3 В)"));
        for (int row = 0; row < 3; row++) {
            StringBuilder sb = new StringBuilder();
            for (int col = 0; col < 9; col++) {
                String note = g.noteAt(row, col);
                if (note != null) {
                    sb.append("[").append(row).append(",").append(col).append("] ")
                            .append(g.netsAt(row, col)).append(" | ").append(note).append("  ");
                }
            }
            if (sb.length() > 0) {
                s.sendMessage(Items.color("&8" + sb));
            }
        }

        // (2) геркон и магнит: без поля цепь разомкнута, с полком — горит
        me.arena.electronics.gui.SchematicGui gr =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintReedLoop(gr, false);
        gr.recalcQuiet();
        double iOpen = gr.currentAt(0, 4);
        paintReedLoop(gr, true);
        gr.recalcQuiet();
        double iClosed = gr.currentAt(0, 4);
        boolean reed = Math.abs(iOpen) < 1e-4 && Math.abs(iClosed) > 0.012 && Math.abs(iClosed) < 0.03;
        s.sendMessage(Items.color((reed ? "&aPASS " : "&cFAIL ")
                + "&7геркон: без магнита I = &f" + Num.amp(iOpen) + "&7, с магнитом I = &f"
                + Num.amp(iClosed) + " &8(ожидание: 0 и ~20 мА)"));

        // (3) разгон тока в дросселе: первый шаг меньше установившегося
        me.arena.electronics.gui.SchematicGui gl =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        gl.put(0, 0, "wire_red", 0);
        gl.put(0, 1, "battery_9", 1);
        gl.put(0, 2, "choke_10h", 1);
        gl.put(0, 3, "resistor_330r", 1);
        gl.put(0, 4, "wire_red", 0);
        gl.put(1, 4, "wire_black", 0);
        gl.put(2, 4, "wire_black", 0);
        gl.put(2, 3, "wire_black", 0);
        gl.put(2, 2, "wire_black", 0);
        gl.put(2, 1, "wire_black", 0);
        gl.put(1, 0, "wire_black", 0);
        gl.put(2, 0, "wire_black", 0);
        gl.recalcQuiet();
        double i1 = Math.abs(gl.currentAt(0, 2));
        gl.recalcSteps(null, 20);
        double i20 = Math.abs(gl.currentAt(0, 2));
        // рядом с катушкой ставим паяльный стол: он должен быть электрически мёртвым
        gl.put(1, 2, "soldering_station", 0);
        gl.recalcSteps(null, 20);
        double iStation = Math.abs(gl.currentAt(0, 2));
        boolean inert = Math.abs(iStation - i20) < 5e-4;
        boolean ramp = i20 > i1 && i20 > 0.010 && i20 < 0.030 && i1 > 0 && inert;
        s.sendMessage(Items.color((ramp ? "&aPASS " : "&cFAIL ")
                + "&7дроссель 10 Гн: шаг 1 -> &f" + Num.amp(i1) + "&7, 20 шагов -> &f" + Num.amp(i20)
                + " &8(ток растёт: L против изменения)"
                + "&7; стол рядом не меняет цепь: &f" + Num.amp(iStation) + " &8("
                + (inert ? "ок" : "РАСЧЁТ СЪЕХАЛ") + ")"));

        // (4) пайка: те же правила, по которым работает GUI стола (Soldering.evaluate)
        Def led = Components.byId("led_red");
        java.util.Map<org.bukkit.Material, Integer> kit =
                me.arena.electronics.recipes.Recipes.ingredientsOf("led_red");
        java.util.Map<org.bukkit.Material, Integer> none =
                new java.util.EnumMap<>(org.bukkit.Material.class);
        me.arena.electronics.recipes.Soldering.Attempt att =
                new me.arena.electronics.recipes.Soldering.Attempt(led, kit, 4, 2, 10);
        var cold = me.arena.electronics.recipes.Soldering.evaluate(att);
        var warm = me.arena.electronics.recipes.Soldering.evaluate(
                new me.arena.electronics.recipes.Soldering.Attempt(led, kit, 4, 2, 30));
        var good = me.arena.electronics.recipes.Soldering.evaluate(
                new me.arena.electronics.recipes.Soldering.Attempt(led, kit, 4, 2, 90));
        var noFlux = me.arena.electronics.recipes.Soldering.evaluate(
                new me.arena.electronics.recipes.Soldering.Attempt(led, kit, 4, 0, 90));
        var noKit = me.arena.electronics.recipes.Soldering.evaluate(
                new me.arena.electronics.recipes.Soldering.Attempt(led, none, 4, 2, 90));
        var notSoldered = me.arena.electronics.recipes.Soldering.evaluate(
                new me.arena.electronics.recipes.Soldering.Attempt(Components.byId("wire_red"),
                        none, 4, 2, 90));
        boolean noVanilla = plugin.recipes().byResult(led) == null
                && plugin.recipes().byResult(Components.byId("wire_red")) != null;
        int dispCount = plugin.recipes().displayCount();
        org.bukkit.inventory.Recipe disp = plugin.recipes().displayFor("led_red");
        boolean stationShown = false;
        if (disp instanceof org.bukkit.inventory.ShapedRecipe sh) {
            for (org.bukkit.inventory.RecipeChoice c : sh.getChoiceMap().values()) {
                if (c instanceof org.bukkit.inventory.RecipeChoice.ExactChoice ec) {
                    for (ItemStack st : ec.getChoices()) {
                        Def sd = Items.defOf(st);
                        if (sd != null && "soldering_station".equals(sd.id)) {
                            stationShown = true;
                        }
                    }
                }
            }
        }
        // (5) ни один ванильный предмет под наш переключатель моделей попасть не может:
        //     состояние выбирается компонентом item_model на СТЕКЕ, custom_model_data
        //     мы не пишем вовсе (он только fallback для старых ядер)
        int modelOk = 0;
        int cmdOnOurs = 0;
        for (Def d : Components.all()) {
            ItemStack probe = plugin.items().componentItem(d, 1);
            if (!probe.hasItemMeta()) {
                continue;
            }
            org.bukkit.inventory.meta.ItemMeta m = probe.getItemMeta();
            if (m.getItemModel() != null) {
                modelOk++;
            }
            if (m.hasCustomModelData()) {
                cmdOnOurs++;
            }
        }
        boolean modelComponent = modelOk == Components.size() && cmdOnOurs == 0;
        s.sendMessage(Items.color((modelComponent ? "&aPASS " : "&cFAIL ")
                + "&7модель задаётся компонентом item_model у &f" + modelOk + "&7/&f"
                + Components.size() + " &7предметов, custom_model_data не ставится ни на одном"
                + " &8(чужие предметы — пластинка в проигрывателе, зелья — наших моделей не видят)"));
        int inManager = 0;
        java.util.Iterator<org.bukkit.inventory.Recipe> it = plugin.getServer().recipeIterator();
        while (it.hasNext()) {
            org.bukkit.inventory.Recipe r = it.next();
            if (r instanceof org.bukkit.Keyed k && "electronics".equals(k.getKey().getNamespace())) {
                inManager++;
            }
        }
        int withStation = 0;
        for (String sid : Components.soldered()) {
            if (stationIn(plugin.recipes().displayFor(sid))) {
                withStation++;
            }
        }
        boolean jei = dispCount == Components.soldered().size() && dispCount > 40
                && plugin.recipes().byResult(led) == null
                && plugin.recipes().hasDisplay("led_red")
                && plugin.recipes().isDisplayOnly(disp) && stationShown && withStation == dispCount
                && plugin.recipes().hasDisplay("resistor_330r")
                && !plugin.recipes().hasDisplay("wire_red")
                && inManager == Components.soldered().size() + plugin.recipes().realCount();
        s.sendMessage(Items.color((jei ? "&aPASS " : "&cFAIL ")
                + "&7JEI: витрин &f" + dispCount + "&7/(нужно " + Components.soldered().size()
                + "), у светодиода реальным крафтом не собирается: &f"
                + (plugin.recipes().byResult(led) == null) + "&7, стол в сетке рецептов: &f"
                + withStation + "&7/&f" + dispCount
                + "&7, в менеджере рецептов клиента: &f" + inManager + " &8("
                + plugin.recipes().realCount() + " крафта + " + dispCount + " витрин)"));
        boolean solderRules = !cold.allowed() && cold.message().contains("холодное")
                && warm.allowed() && warm.coldJoint() && warm.solder() == 1
                && good.allowed() && !good.coldJoint() && good.consume().equals(kit)
                && !noFlux.allowed() && noFlux.message().contains("флюс")
                && !noKit.allowed() && noKit.message().contains("не хватает")
                && !notSoldered.allowed() && notSoldered.message().contains("верстаке")
                && noVanilla;
        s.sendMessage(Items.color((solderRules ? "&aPASS " : "&cFAIL ")
                + "&7пайка: 10° — «" + cold.message() + "», 30° — холодная пайка (припой ×1 в утиль), "
                + "90° — заготовок ×" + good.consume().size() + " + припой ×" + good.solder()
                + " + флюс; ванильного рецепта светодиода нет: " + noVanilla));

        // (12) защита по I²t, тепловая защита PTC, утечка варистора, стабилизация
        me.arena.electronics.gui.SchematicGui gfuse =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gfuse, "battery_9", "fuse_cartridge_1a", "resistor_10r");
        gfuse.recalcSteps(null, 30);
        double iHold = Math.abs(gfuse.currentAt(0, 2));
        boolean fuseHolds = gfuse.stateAt(0,2) != 3 && iHold > 0.5 && iHold < 1.0;
        me.arena.electronics.gui.SchematicGui gblow =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gblow, "battery_9", "fuse_cartridge_1a", "wire_black");
        gblow.recalcSteps(null, 6);
        double iAfter = Math.abs(gblow.currentAt(0, 4));
        boolean fuseBlown = gblow.stateAt(0, 2) == 3 && iAfter < 1e-3;
        CompInstance fuseInst = gblow.instanceAt(0, 2);
        boolean healed = fuseInst != null && Simulation.repair(fuseInst)
                && !fuseInst.burnt() && fuseInst.aux[0] == 0;
        s.sendMessage(Items.color((fuseHolds && fuseBlown && healed ? "&aPASS " : "&cFAIL ")
                + "&7предохранитель I²t: &f" + Num.amp(iHold) + " &7не сжигает вставку за 30 шагов, "
                + "коротыш — &cсгорел&7, нагрузку обесточило (I = &f" + Num.amp(iAfter)
                + "&7), перепайка обнуляет накопленный I²t: " + healed));

        me.arena.electronics.gui.SchematicGui gptc =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gptc, "battery_9", "ptc_500ma", "resistor_10r");
        gptc.recalcSteps(null, 100);
        double iPtcHold = Math.abs(gptc.currentAt(0, 2));
        me.arena.electronics.gui.SchematicGui gptc2 =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gptc2, "battery_9", "ptc_500ma", "wire_black");
        gptc2.recalcSteps(null, 30);           // 1.5 с коротыша: деталь обязана щёлкнуть
        double iPtcTrip = Math.abs(gptc2.currentAt(0, 2));
        CompInstance ptcInst = gptc2.instanceAt(0, 2);
        gptc2.recalcSteps(null, 400);          // новый прогон: «не сгорает» за 20 с КЗ
        double heat = ptcInst == null ? -1 : ptcInst.aux[0];
        double ptcPeak = ptcInst == null ? 0 : ptcInst.aux[1];   // пик за всю историю прогона
        boolean ptcOk = ptcInst != null && iPtcHold > 0.5 && iPtcHold < 1.2
                && iPtcTrip < iPtcHold * 0.5 && ptcPeak > 90
                && !ptcInst.burnt() && gptc2.stateAt(0, 2) != 3;
        s.sendMessage(Items.color((ptcOk ? "&aPASS " : "&cFAIL ")
                + "&7PTC держит &f" + Num.amp(iPtcHold) + " &7и не греется, при коротыше за 30 шагов "
                + "уходит в защиту: I = &f" + Num.amp(iPtcTrip) + "&7, пик перегрева &f" + (int) ptcPeak
                + " °C&7 (щелчок на 90), сейчас &f" + (int) heat + " °C&7; за 400 шагов КЗ деталь "
                + (ptcInst.burnt() ? "&cсгорела" : "&aжива") + "&7 (мягкий режим отказов)"));

        me.arena.electronics.gui.SchematicGui gmov =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gmov, "battery_9", "mov_275v", "resistor_10r");
        gmov.recalcSteps(null, 40);
        double iMov = Math.abs(gmov.currentAt(0, 2));
        s.sendMessage(Items.color((iMov < 1e-6 ? "&aPASS " : "&cFAIL ")
                + "&7варистор при 9 В не течёт: I = &f" + String.format(java.util.Locale.US, "%.2e", iMov)
                + " А&7 (порог 390 В, α=9)"));

        // (12г) аккумулятор: банк обязан тратить заряд, а не «вечно держать 12 В»
        me.arena.electronics.gui.SchematicGui gaccu =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gaccu, "battery_lead_12v", "wire_black", "resistor_10r");
        gaccu.recalcSteps(null, 3000);              // 3000 шагов (шаг схемы 25 мс) через 10 Ом
        CompInstance accu = gaccu.instanceAt(0, 1);
        double iAccu = accu == null ? 0 : accu.current;
        double socLeft = accu == null ? -1 : me.arena.electronics.circuit.Battery.soc(accu.aux);
        double ahAccu = accu == null ? 0 : accu.aux[me.arena.electronics.circuit.Battery.AMPERE_HOURS];
        int accuState = accu == null ? -1 : accu.state;
        boolean accuDrain = accu != null && iAccu > 0.3 && socLeft < 0.999 && socLeft > 0.98
                && ahAccu > 0 && accuState == me.arena.electronics.circuit.CompStates.DIM;
        double accuVolt = accu == null ? 0 : Math.abs(accu.voltage);

        s.sendMessage(Items.color((accuDrain ? "&aPASS " : "&cFAIL ")
                + "&7аккумулятор на разряде: 3000 шагов через 10 Ом — &f" + Num.amp(iAccu)
                + "&7, остаток &f" + (int) Math.round(socLeft * 100) + " %&7, счётчик &f+"
                + String.format(java.util.Locale.US, "%.4f", ahAccu)
                + " &7А·ч отдано&7, клеммы &f" + String.format(java.util.Locale.US, "%.2f", accuVolt)
                + " В&7, состояние &f" + me.arena.electronics.circuit.CompStates.name(accuState)
                + " &7(сульфатацию сверяет офлайн-тест TestBattery)"));

        // (12е) нормальный заряд: 220 В через 100 Ом дают ~2 А в банк — это меньше
        // 1C (7 А) и меньше напряжения газа, значит банк обязан просто принимать
        // ток: состояние «обратно» (ток идёт в банк), отказа нет, счётчик уходит в
        // минус, заряд упирается в 100 % и не растёт
        me.arena.electronics.gui.SchematicGui gchg =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gchg, "supply_220v", "resistor_100r", "battery_lead_12v");
        gchg.recalcSteps(null, 3000);
        CompInstance chg = gchg.instanceAt(0, 3);
        double ahIn = chg == null ? 0 : chg.aux[me.arena.electronics.circuit.Battery.AMPERE_HOURS];
        double socChg = chg == null ? -1 : me.arena.electronics.circuit.Battery.soc(chg.aux);
        int chgState = chg == null ? -1 : chg.state;
        double lossChg = chg == null ? 0 : chg.aux[me.arena.electronics.circuit.Battery.DEGRADE];
        boolean accuCharge = chg != null && ahIn < 0 && Math.abs(chg.current) > 0.5
                && Math.abs(chg.current) < me.arena.electronics.circuit.Battery.MAX_CHARGE_C * 7.0
                && chgState == me.arena.electronics.circuit.CompStates.REVERSE
                && chg.fault.isEmpty() && socChg >= 0.999 && lossChg == 0.0;
        s.sendMessage(Items.color((accuCharge ? "&aPASS " : "&cFAIL ")
                + "&7штатная зарядка (220 В через 100 Ом): &f" + Num.amp(Math.abs(chg == null ? 0 : chg.current))
                + "&7 в банк, счётчик &f" + String.format(java.util.Locale.US, "%.4f", ahIn)
                + " &7А·ч (минус = принято), заряд &f" + (int) Math.round(100 * socChg)
                + " %&7 (выше 100 % не бывает), потеря &f" + (int) Math.round(100 * lossChg)
                + " %&7, состояние &f" + me.arena.electronics.circuit.CompStates.name(chgState)
                + "&7, отказ: " + (chg == null ? "&cнет экземпляра" : "&f«" + chg.fault + "&f»")));

        // (12ж) перезаряд: те же 220 В, но через 10 Ом — ~13 А, это 1.9C. Банк
        // обязан загореться, нагреться и начать терять ёмкость (кипение)
        me.arena.electronics.gui.SchematicGui gboil =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gboil, "supply_220v", "resistor_10r", "battery_lead_12v");
        gboil.recalcSteps(null, 3000);
        CompInstance boil = gboil.instanceAt(0, 3);
        double boilHeat = boil == null ? 0 : boil.aux[me.arena.electronics.circuit.Battery.TEMP];
        double boilLoss = boil == null ? 0 : boil.aux[me.arena.electronics.circuit.Battery.DEGRADE];
        boolean accuBoil = boil != null && boil.state == me.arena.electronics.circuit.CompStates.ON
                && boil.fault.contains("кипит") && boilHeat > 0.25 && boilLoss > 0.0002
                && boil.aux[me.arena.electronics.circuit.Battery.AMPERE_HOURS] < 0;
        // (шаг схемы — 25 мс, поэтому за 3000 шаков банк «кипит» 75 с: потеря
        // ёмкости тогда ~0.06 % — порог выставлен по факту, а не по округлению)
        s.sendMessage(Items.color((accuBoil ? "&aPASS " : "&cFAIL ")
                + "&7перезаряд (220 В через 10 Ом): &f" + Num.amp(Math.abs(boil == null ? 0 : boil.current))
                + "&7 в банк при пределе 7 А (1C), нагрев +&f"
                + String.format(java.util.Locale.US, "%.1f", boilHeat) + " °C&7, потеряно &f"
                + String.format(java.util.Locale.US, "%.2f", 100 * boilLoss)
                + " %&7 ёмкости&7, состояние &f"
                + me.arena.electronics.circuit.CompStates.name(boil == null ? -1 : boil.state)
                + "&7, &c" + (boil == null ? "нет экземпляра" : boil.fault)));

        // (12д) банк обязан помнить накопленное между запусками: Persistence пишет
        // aux в registry.json и читает его обратно тем же путём, каким сервер
        // догружает компоненты из чанков (loadChunk)
        boolean auxSaved = false;
        boolean auxRead = false;
        String auxJson = "";
        String auxNote = "нет мира/дефолта";
        try {
            org.bukkit.World pw = plugin.getServer().getWorlds().isEmpty()
                    ? null : plugin.getServer().getWorlds().get(0);
            me.arena.electronics.registry.Def bd =
                    me.arena.electronics.registry.Components.byId("battery_lead_12v");
            if (pw != null && bd != null && accu != null) {
                me.arena.electronics.registry.LocationKey fk =
                        new me.arena.electronics.registry.LocationKey(pw, 300000, 64, 300000);
                CompInstance save = new CompInstance(fk, bd);
                System.arraycopy(accu.aux, 0, save.aux, 0, save.aux.length);
                save.state = accu.state;
                me.arena.electronics.circuit.Persistence reg =
                        me.arena.electronics.circuit.Persistence.get(plugin);
                reg.write(save);
                reg.save();
                String txt = java.nio.file.Files.readString(
                        plugin.getDataFolder().toPath().resolve("registry.json"),
                        java.nio.charset.StandardCharsets.UTF_8);
                int aAt = txt.indexOf("\"a\":[");
                int aEnd = aAt < 0 ? -1 : txt.indexOf(']', aAt);
                if (aAt >= 0 && aEnd > aAt) {
                    auxJson = txt.substring(aAt + 5, aEnd);
                }
                auxSaved = txt.contains("\"d\":\"battery_lead_12v\"")
                        && auxJson.split(",").length == 4;
                // читаем запись обратно тем же методом, которым её поднимает
                // реестр (read); loadChunk не используем — он тащит рендерер
                me.arena.electronics.circuit.Persistence.Entry back =
                        new me.arena.electronics.circuit.Persistence.Entry();
                back.world = pw.getName();
                back.x = 300000;
                back.y = 64;
                back.z = 300000;
                back.def = "battery_lead_12v";
                back.aux = new double[]{0.35, 0.02, 1.5, 4.25};
                CompInstance revived = reg.read(back);
                auxRead = revived != null && revived.def.kind == me.arena.electronics.registry.Kind.BATTERY
                        && Math.abs(revived.aux[0] - 0.35) < 1e-9
                        && Math.abs(revived.aux[1] - 0.02) < 1e-9
                        && Math.abs(revived.aux[2] - 1.5) < 1e-9
                        && Math.abs(revived.aux[3] - 4.25) < 1e-9;
                reg.clear(fk.block());
                reg.save();
                auxNote = auxSaved && auxRead ? "записан в registry.json и читается обратно"
                        : (auxSaved ? "в файле есть, но read() его не возвращает"
                                : "aux в реестре не найден");
            }
        } catch (RuntimeException | java.io.IOException ex) {
            auxNote = "сбой: " + ex.getClass().getSimpleName() + " " + ex.getMessage();
        }
        s.sendMessage(Items.color(((auxSaved && auxRead) ? "&aPASS " : "&cFAIL ")
                + "&7реестр помнит банк: &f" + auxNote + "&7, &fa=["
                + String.format(java.util.Locale.US, "%s", auxJson) + "] &7(разряженность,"
                + " деградация, А·ч, нагрев) — после рестарта заряд не обнуляется"));

        me.arena.electronics.gui.SchematicGui greg =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintRegulator(greg, "battery_9", false);
        greg.recalcSteps(null, 40);
        double vOut = Math.abs(greg.voltageAt(1, 3));
        String regNets = greg.netsAt(1, 4) + " | U(бат) " + Num.volt(greg.voltageAt(1, 5))
                + " U(шина) " + Num.volt(greg.voltageAt(0, 4));
        me.arena.electronics.gui.SchematicGui greg3 =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintRegulator(greg3, "battery_3", false);
        greg3.recalcSteps(null, 40);
        double vOut3 = Math.abs(greg3.voltageAt(1, 3));
        me.arena.electronics.gui.SchematicGui gregS =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintRegulator(gregS, "battery_9", true);
        gregS.recalcSteps(null, 40);
        double iShort = Math.abs(gregS.currentAt(1, 5));   // ток питания = отдача (у линейного они равны)
        double vShort = Math.abs(gregS.voltageAt(1, 3));
        boolean regOk = vOut > 4.7 && vOut < 5.3 && vOut3 < 2.0
                && iShort <= 1.25 && iShort > 0.3 && vShort < 1.5;
        s.sendMessage(Items.color((regOk ? "&aPASS " : "&cFAIL ")
                + "&77805: на 330 Ом &f" + Num.volt(vOut) + " &7при 9 В на входе, при 3 В — dropout &f"
                + Num.volt(vOut3) + "&7; коротыш: U = &f" + Num.volt(vShort) + "&7, I = &f"
                + Num.amp(iShort) + "&7 (предел 1 А, а не 8 А)"
                + "&8 [" + regDiag(greg) + "] &8" + regNets));

        // синус: источник обязан менять знак, иначе трансформатор и мост проверять нечем
        me.arena.electronics.gui.SchematicGui gac =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintLoop(gac, "source_ac", "wire_red", "resistor_10r");
        boolean acPos = false, acNeg = false;
        for (int k = 0; k < 40; k++) {
            gac.recalcSteps(null, 1);
            double uAc = gac.voltageAt(0, 3);
            if (uAc > 0.3) {
                acPos = true;
            }
            if (uAc < -0.3) {
                acNeg = true;
            }
        }
        // «крест» из 4 выводов: мост и трансформатор в сетку 9x3 не замыкаются (оба
        // контура требуют обхода вокруг детали), поэтому здесь только проверка, что
        // решатель сходится, а физику считает офлайн-тест TestCoupled
        me.arena.electronics.gui.SchematicGui gbr =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        gbr.put(1, 1, "wire_black", 0);
        gbr.put(1, 2, "bridge_kbu8", 1);
        gbr.put(1, 3, "wire_black", 0);
        gbr.put(0, 2, "resistor_330r", 2);
        gbr.put(2, 2, "wire_black", 0);
        gbr.recalcSteps(null, 5);
        double ub = gbr.voltageAt(1, 2);
        me.arena.electronics.gui.SchematicGui gxf =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        gxf.put(1, 1, "wire_black", 0);
        gxf.put(1, 2, "xfmr_230_12", 1);
        gxf.put(1, 3, "battery_9", 3);
        gxf.put(0, 2, "wire_black", 0);
        gxf.put(2, 2, "wire_black", 0);
        gxf.recalcSteps(null, 40);
        double ix = Math.abs(gxf.currentAt(1, 2));
        boolean crossOk = Double.isFinite(ub) && Double.isFinite(ix) && ix < 9.0;
        s.sendMessage(Items.color((crossOk && (acPos && acNeg) ? "&aPASS " : "&cFAIL ")
                + "&7синус меняет полярность: " + (acPos && acNeg) + "&7; мост и трансформатор "
                + "(4 вывода крестом) решатель не ломают: U_мост = &f" + Num.volt(ub)
                + "&7, I_перв. на постоянке = &f" + Num.amp(ix) + " &7→ постоянку тр-р не передаёт&7; "
                + "&8Кт и насыщение считает tools/TestCoupled"));

        // (13) руды: жила как функция координат, геозонд, плавка и место в пайке
        me.arena.electronics.Config dc = plugin.config();
        long dseed = dc.depositSeed != 0 ? dc.depositSeed
                : (plugin.getServer().getWorlds().isEmpty() ? 0L
                        : plugin.getServer().getWorlds().get(0).getSeed());
        me.arena.electronics.deposit.Deposits f1 = new me.arena.electronics.deposit.Deposits();
        me.arena.electronics.deposit.Deposits f2 = new me.arena.electronics.deposit.Deposits();
        f1.configure(dseed, true, dc.depositChance, dc.depositVeinSize, dc.depositVeinJitter,
                dc.depositYMin, dc.depositYBand, dc.depositYield);
        f2.configure(dseed, true, dc.depositChance, dc.depositVeinSize, dc.depositVeinJitter,
                dc.depositYMin, dc.depositYBand, dc.depositYield);
        boolean sameField = me.arena.electronics.deposit.Deposits.sameField(f1, f2, 16);
        int span = 24;
        int total = 0;
        int found = 0;
        int yLo = Integer.MAX_VALUE;
        int yHi = Integer.MIN_VALUE;
        int veinsizeMax = 0;
        int veinsizeMin = Integer.MAX_VALUE;
        int sampleX = 0;
        int sampleZ = 0;
        int sampleY = 0;
        int sampleSize = 0;
        for (int cx = -span; cx <= span; cx++) {
            for (int cz = -span; cz <= span; cz++) {
                total++;
                me.arena.electronics.deposit.Deposits.Vein v = f1.veinOf(cx, cz);
                if (v == null) {
                    continue;
                }
                found++;
                yLo = Math.min(yLo, v.yMin());
                yHi = Math.max(yHi, v.yMin() + v.ySpan());
                veinsizeMax = Math.max(veinsizeMax, v.size());
                veinsizeMin = Math.min(veinsizeMin, v.size());
                if (v.yMin() < 0) {                 // первый же «глубокий» пример
                    sampleX = cx * 16 + 8;
                    sampleZ = cz * 16 + 8;
                    sampleY = v.yMin() + v.ySpan() / 2;
                    sampleSize = v.size();
                }
            }
        }
        double share = 100.0 * found / Math.max(1, total);
        me.arena.electronics.deposit.Deposits.Vein sv = f1.veinAt(sampleX, sampleZ);
        boolean beforeProbe = sv != null && f1.productive(sv, sampleY);
        f1.prospect(sampleX, sampleY, sampleZ);
        boolean afterProbe = f1.productive(f1.veinAt(sampleX, sampleZ), sampleY);
        int got = 0;
        for (int step = 0; step < Math.max(1, sampleSize); step++) {
            if (f1.takeOre(sampleX, sampleY, sampleZ) != null) {
                got++;
            }
        }
        boolean spent = f1.depleted(f1.veinAt(sampleX, sampleZ))
                && f1.takeOre(sampleX, sampleY, sampleZ) == null;
        boolean oresOk = true;
        for (me.arena.electronics.deposit.Deposits.Ore o : me.arena.electronics.deposit.Deposits.ORES) {
            if (Components.byId(o.chunkId()) == null) {
                oresOk = false;
            }
        }
        int smelts = plugin.recipes().smeltCount();
        org.bukkit.inventory.Recipe sm = smelts == 0 ? null
                : plugin.getServer().getRecipe(new org.bukkit.NamespacedKey(plugin, "smelt_ingot_copper"));
        org.bukkit.inventory.RecipeChoice.ExactChoice ec =
                sm instanceof org.bukkit.inventory.FurnaceRecipe fr0
                        && fr0.getInputChoice() instanceof org.bukkit.inventory.RecipeChoice.ExactChoice e2
                        ? e2 : null;
        Def raw = Components.byId("raw_copper_chunk");
        Def ing = Components.byId("ingot_copper");
        org.bukkit.inventory.ItemStack result = sm instanceof org.bukkit.inventory.FurnaceRecipe fr1
                ? fr1.getResult() : null;
        boolean furnaceOk = ec != null && ec.getChoices().size() == 1
                && ec.getChoices().get(0).getAmount() == 2
                && raw != null && Items.defOf(ec.getChoices().get(0)) != null
                && "raw_copper_chunk".equals(Items.defOf(ec.getChoices().get(0)).id)
                && ing != null && result != null && Items.defOf(result) != null
                && "ingot_copper".equals(Items.defOf(result).id)
                && plugin.recipes().byResult(ing) == null
                && !Components.isSoldered("ingot_copper");
        boolean chainOk = oresOk && furnaceOk
                && me.arena.electronics.recipes.Soldering.oursOf("wire_bare").getOrDefault("ingot_copper", 0) == 2
                && me.arena.electronics.recipes.Soldering.oursOf("wire_tinned").getOrDefault("wire_bare", 0) == 1
                && me.arena.electronics.recipes.Soldering.oursTotal("mcu_atmega328") == 2
                && !me.arena.electronics.recipes.Soldering.oursOf("wire_tinned").isEmpty()
                && me.arena.electronics.recipes.Soldering.ingredientText("wire_bare").contains("\u00d72")
                && !me.arena.electronics.recipes.Soldering.ingredientText("mcu_atmega328")
                        .contains("нет заготовки");
        boolean oreOk = sameField && sv != null && !beforeProbe && afterProbe && spent
                && got == sampleSize && veinsizeMin > 0
                && yLo >= dc.depositYMin - 1 && yHi <= dc.depositYMin + dc.depositYBand + 1
                && Math.abs(share - 100 * dc.depositChance) < 6.0 && chainOk;
        s.sendMessage(Items.color((oreOk ? "&aPASS " : "&cFAIL ")
                + "&7недра: жила в &f" + (int) share + "&7% чанков (config &f"
                + (int) (100 * dc.depositChance) + "&7%), поле при том же сиде совпало: &f"
                + sameField + "&7; размер &f" + veinsizeMin + "&7…&f" + veinsizeMax
                + " блоков, пояс Y &f" + yLo + "&7…&f" + yHi
                + "&7; до геозонда не копается: &f" + !beforeProbe + "&7, после — выбрано &f"
                + got + "&7/&f" + sampleSize + " &7и жила пуста: &f" + spent
                + "&7; печь: &f" + smelts + " &7рецепта, 2 куска → слиток (ExactChoice, без датапака): &f"
                + furnaceOk + "&7; станок: слиток→провод, провод+припой→лужение,"
                + " провод+кристалл→МК: &f" + chainOk + "&7"));

        // (14) микроконтроллер: прошивка управляет ногами, ноги считают ток
        me.arena.electronics.gui.SchematicGui gm =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintMcu(gm, "led_red");
        gm.programAt(1, 3, "pin 2 output;\ndigitalWrite(2, HIGH);\n");
        gm.recalcSteps(null, 6);
        CompInstance mcu = gm.instanceAt(1, 3);
        double vcc = mcu == null ? 0 : mcu.mcuVcc;
        double vPin = mcu == null ? 0 : mcu.mcuVolt[2];
        double iPort = mcu == null ? 0 : Math.abs(mcu.mcuPinI[2]) * 1000;
        double iLed = Math.abs(gm.currentAt(1, 4)) * 1000;
        double uLed = Math.abs(gm.voltageAt(1, 4));
        boolean mcuOk = mcu != null && vcc > 2.8 && vcc < 3.05 && vPin > vcc - 0.25 && vPin > 2.5
                && iPort > 1.2 && iPort < 5.0 && Math.abs(iPort - iLed) < 0.6
                && mcu.mcuHalt.isEmpty() && mcu.state == me.arena.electronics.circuit.CompStates.ON;
        s.sendMessage(Items.color((mcuOk ? "&aPASS " : "&cFAIL ")
                + "&7МК: VCC &f" + Num.volt(vcc) + " &7(9 В через 330 Ом не идёт — только 3 В элемент),"
                + " нога 2 = &f" + Num.volt(vPin) + " &7HIGH, ток порта &f" + (int) iPort
                + " мА&7 = току светодиода &f" + (int) iLed + " мА&7 (U_LED &f" + Num.volt(uLed)
                + "&7), прошивка не встала: &f" + mcu.mcuHalt.isEmpty()
                + " &8[операторов " + (mcu.firmware == null ? 0 : mcu.firmware.statements()) + "]"));

        me.arena.electronics.gui.SchematicGui gsh =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintMcu(gsh, "wire_red");
        gsh.programAt(1, 3, "pin 2 output;\ndigitalWrite(2, HIGH);\n");
        gsh.recalcSteps(null, 6);
        CompInstance sh = gsh.instanceAt(1, 3);
        double iPortShort = sh == null ? 0 : Math.abs(sh.mcuPinI[2]) * 1000;
        int hotPins = sh == null ? 0 : (int) sh.aux[1];
        boolean limitOk = iPortShort > 30 && iPortShort < 45 && hotPins >= 1;
        // битая прошивка: чип останавливается, ноги уходят в input, ток через порт — ноль
        me.arena.electronics.gui.SchematicGui gerr =
                me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
        paintMcu(gerr, "wire_red");
        gerr.programAt(1, 3, "pin 0 output;");
        gerr.recalcSteps(null, 3);
        CompInstance err = gerr.instanceAt(1, 3);
        sh = err == null ? sh : err;
        double iAfterErr = sh == null ? 0 : Math.abs(sh.mcuPinI[2]) * 1000;
        boolean haltOk = sh != null && sh.firmware != null && !sh.firmware.ok()
                && sh.mcuHalt.contains("питание") && sh.state == me.arena.electronics.circuit.CompStates.REVERSE
                && sh.mcuDir[2] == 0 && sh.mcuLvl[2] == 0 && iAfterErr < 0.05;
        s.sendMessage(Items.color(((limitOk && haltOk) ? "&aPASS " : "&cFAIL ")
                + "&7коротыш порта в землю: &f" + (int) iPortShort + " мА&7 (паспорт 40 мА/нога, не 120),"
                + " счётчик «горячих» ног &f" + hotPins + "&7; синтаксическая ошибка — чип встаёт:"
                + " &f«" + sh.mcuHalt + "&f», нога отпущена, ток &f" + (int) iAfterErr + " мА"));

        // ---- (15) металлургия: тигель, стан, шунт ---------------------------
        {
            var tCruc = me.arena.electronics.recipes.Metallurgy.table(
                    me.arena.electronics.recipes.Metallurgy.CRUCIBLE_ID);
            var tMill = me.arena.electronics.recipes.Metallurgy.table(
                    me.arena.electronics.recipes.Metallurgy.MILL_ID);
            var bronze = tCruc.get("ingot_bronze");
            var brass = tCruc.get("ingot_brass");
            var cu = tCruc.get("raw_copper_chunk");
            var zinc = tCruc.get("raw_zinc_chunk");
            var enamel = tMill.get("wire_enamelled");
            java.util.function.ToIntFunction<String> full = id -> switch (id) {
                case "ingot_copper" -> 2;
                case "ingot_tin", "ingot_zinc", "raw_zinc_chunk", "raw_tin_chunk", "slag" -> 1;
                default -> 1;
            };
            boolean canAlloy = me.arena.electronics.recipes.Metallurgy.missing(bronze, full) == null
                    && me.arena.electronics.recipes.Metallurgy.missing(brass, full) == null;
            java.util.function.ToIntFunction<String> onlyCu = id -> id.equals("ingot_copper") ? 2 : 0;
            String why = me.arena.electronics.recipes.Metallurgy.missing(bronze, onlyCu);
            boolean tellsTin = why != null && why.contains("олова");
            int slagFromBronze = bronze.make().getOrDefault("slag", 0);
            boolean slagged = slagFromBronze == 1
                    && "ingot_copper".equals(brass.result()) == false
                    && tCruc.get("ingot_copper").need().getOrDefault("slag", 0) == 3;
            // обмен руд идёт только в сторону менее активного металла
            int cuOut = cu.make().getOrDefault("raw_copper_chunk", 0);
            int znOut = zinc.make().getOrDefault("raw_zinc_chunk", 0);
            boolean series = cuOut == 2 && znOut == 3
                    && zinc.need().getOrDefault("raw_copper_chunk", 0) == 2
                    && zinc.need().getOrDefault("raw_tin_chunk", 0) == 2;
            boolean enamel2 = enamel.count() == 2
                    && me.arena.electronics.recipes.Metallurgy.missing(enamel, onlyCu) == null;
            s.sendMessage(Items.color(((canAlloy && tellsTin && slagged && series && enamel2)
                    ? "&aPASS " : "&cFAIL ")
                    + "&7металлургия: 2 Cu + 1 Sn → бронза + шлак ×" + slagFromBronze
                    + ", без олова стол говорит «" + why + "&7»; цементация Cu ×" + cuOut
                    + " из Zn+Sn, цинк — только возгонкой 4→" + znOut
                    + "; слиток меди → эмальпровод ×" + enamel.count()));
            me.arena.electronics.gui.SchematicGui gshunt =
                    me.arena.electronics.gui.SchematicGui.forId(plugin, java.util.UUID.randomUUID());
            paintLoop(gshunt, "battery_9", "resistor_shunt_0r01", "resistor_10r");
            gshunt.recalcSteps(null, 20);
            double iSh = Math.abs(gshunt.currentAt(0, 2));
            double uSh = Math.abs(gshunt.voltageAt(0, 2));
            double uOhm = iSh * 0.01;
            boolean shuntOk = iSh > 0.4 && iSh < 1.2 && Math.abs(uSh - uOhm) < 2e-3
                    && uSh < 0.02;
            s.sendMessage(Items.color((shuntOk ? "&aPASS " : "&cFAIL ")
                    + "&7шунт 0,01 Ом в цепи 9 В с нагрузкой 10 Ом: I = &f" + Num.amp(iSh)
                    + "&7, на шунте &f" + (int) (uSh * 1000) + " мВ&7 (по закону Ома &f"
                    + (int) (uOhm * 1000) + " мВ&7) — так амперметр и считает большие токи"));
        }

        // (13) привод: момент из тока, работа из стружки, защита из I²t — числа сходятся
        {
            double rpmDrive = 2800.0;
            double kDrive = me.arena.electronics.kinetics.Kinetics.torqueConstant(220.0, rpmDrive);
            double iStall = me.arena.electronics.kinetics.Kinetics.motorCurrent(
                    220.0, 0.0, 220.0, rpmDrive, 22.0, 6.0);
            double tStall = kDrive * iStall;
            me.arena.electronics.kinetics.Kinetics.Spec sawSpec =
                    new me.arena.electronics.kinetics.Kinetics.Spec();
            me.arena.electronics.kinetics.Kinetics.Part sawPart =
                    new me.arena.electronics.kinetics.Kinetics.Part(
                            me.arena.electronics.kinetics.Kinetics.Role.SAW,
                            me.arena.electronics.kinetics.Kinetics.Element.SHAFT,
                            0.4, 10.0, 0.35, 0, 0, 0);
            sawPart.facing = me.arena.electronics.kinetics.Kinetics.WOOD;
            sawPart.tx = 1;
            sawSpec.add(sawPart);
            me.arena.electronics.kinetics.Kinetics.State sawSt =
                    new me.arena.electronics.kinetics.Kinetics.State();
            me.arena.electronics.kinetics.Kinetics.Result sawRes =
                    new me.arena.electronics.kinetics.Kinetics.Result();
            for (int step = 0; step < 600; step++) {
                me.arena.electronics.kinetics.Kinetics.Motor mm =
                        me.arena.electronics.kinetics.Kinetics.motorAtSpeed(rpmDrive, 220.0, 220.0,
                                sawSt.omega, 22.0, 6.0);
                me.arena.electronics.kinetics.Kinetics.step(mm, sawSpec, sawSt, Double.MAX_VALUE,
                        0.05, sawRes);
            }
            boolean cut = sawSt.blocksDone >= 4 && sawSt.blocksDone <= 12 && sawSt.omega > 60.0
                    && !sawSt.jammed;
            s.sendMessage(Items.color((cut ? "&aPASS " : "&cFAIL ")
                    + "&7привод: мотор 220 В (M₀ = &f" + String.format("%.2f", tStall)
                    + "&7 Н·м при &f" + String.format("%.1f", iStall) + " А&7) за 30 с пилит бревно ×&f"
                    + sawSt.blocksDone + "&7, оборотов &f" + (int) sawSt.rpm()
                    + "&7, на валу &f" + Num.watt(sawRes.powerW)
                    + "&7 (брёвен по 1500 Дж — проверка t = W/P)"));
            me.arena.electronics.kinetics.Kinetics.Spec jamSpec =
                    new me.arena.electronics.kinetics.Kinetics.Spec();
            me.arena.electronics.kinetics.Kinetics.Part jamPart =
                    new me.arena.electronics.kinetics.Kinetics.Part(
                            me.arena.electronics.kinetics.Kinetics.Role.DRILL,
                            me.arena.electronics.kinetics.Kinetics.Element.SHAFT,
                            0.0, 14.0, 0.1, 0, 0, 0);
            jamPart.facing = me.arena.electronics.kinetics.Kinetics.REFRACTORY;
            jamSpec.add(jamPart);
            me.arena.electronics.kinetics.Kinetics.State jamSt =
                    new me.arena.electronics.kinetics.Kinetics.State();
            for (int step = 0; step < 60; step++) {
                me.arena.electronics.kinetics.Kinetics.Motor mm =
                        me.arena.electronics.kinetics.Kinetics.motorAtSpeed(rpmDrive, 220.0, 220.0,
                                jamSt.omega, 22.0, 6.0);
                me.arena.electronics.kinetics.Kinetics.step(mm, jamSpec, jamSt, Double.MAX_VALUE, 0.05);
            }
            double iJam = me.arena.electronics.kinetics.Kinetics.motorCurrent(220.0, jamSt.omega,
                    220.0, rpmDrive, 22.0, 6.0);
            double tBlow = 45.0 / Math.max(0.01,
                    Math.max(0.0, iJam * iJam - 5.0 * 5.0));   // I²t считается от превышения 5 А
            boolean jamOk = jamSt.blocksDone == 0 && jamSt.jammed && tBlow > 0.3 && tBlow < 1.5;
            s.sendMessage(Items.color((jamOk ? "&aPASS " : "&cFAIL ")
                    + "&7бур в упор: встал («" + jamSt.note + "&7»), ток &f" + Num.amp(iJam)
                    + "&7 — плавкая вставка 5 А держит ещё &f" + String.format("%.2f", tBlow)
                    + " с&7, потом обесточивает обмотку"));
            double vRail = me.arena.electronics.kinetics.Kinetics.carriage(
                    me.arena.electronics.kinetics.Kinetics.toOmega(1400.0), 2.0, 0.15, 32.0,
                    me.arena.electronics.kinetics.Kinetics.MU_RAIL, 8.0).speedMps;
            double vGround = me.arena.electronics.kinetics.Kinetics.carriage(
                    me.arena.electronics.kinetics.Kinetics.toOmega(1400.0), 2.0, 0.15, 32.0,
                    me.arena.electronics.kinetics.Kinetics.MU_GROUND, 8.0).speedMps;
            boolean railOk = vRail > 0.0 && vGround == 0.0;
            s.sendMessage(Items.color((railOk ? "&aPASS " : "&cFAIL ")
                    + "&7каретка: по рельсу &f" + String.format("%.2f", vRail)
                    + " м/с&7, по земле — стоит (тяги не хватает: μ 0,005 против 0,35)"));
            Def shaftDef = me.arena.electronics.registry.Components.byId("shaft_kinetic");
            Def sawDef = me.arena.electronics.registry.Components.byId("saw_kinetic");
            Def motDef = me.arena.electronics.registry.Components.byId("motor_220v");
            Def supDef = me.arena.electronics.registry.Components.byId("supply_220v");
            boolean reg = shaftDef != null && sawDef != null && motDef != null && supDef != null
                    && shaftDef.kineticRole() == me.arena.electronics.kinetics.Kinetics.Role.SHAFT
                    && sawDef.kineticRole() == me.arena.electronics.kinetics.Kinetics.Role.SAW
                    && (int) motDef.rpm == 2800 && Math.abs(motDef.ratedVoltage - 220.0) < 0.5
                    && motDef.pins == 2 && shaftDef.pins == 0
                    && supDef.kind == me.arena.electronics.registry.Kind.SOURCE;
            s.sendMessage(Items.color((reg ? "&aPASS " : "&cFAIL ")
                    + "&7реестр привода: &f" + (shaftDef == null ? "нет" : shaftDef.nameRu)
                    + "&7, &f" + (sawDef == null ? "нет" : sawDef.nameRu) + "&7, &f"
                    + (motDef == null ? "нет" : motDef.nominal()) + "&7 — у вала выводов "
                    + (shaftDef == null ? "?" : shaftDef.pins) + " (цепь не трогает), у мотора "
                    + (motDef == null ? "?" : motDef.pins)));
            // живой прогон в мире: собрали установку, крутим движок, проверяем, что
            // мир изменился, а откат вернул всё как было
            long before = plugin.kinetics().blocksBroken();
            org.bukkit.World sw = org.bukkit.Bukkit.getWorlds().isEmpty() ? null
                    : org.bukkit.Bukkit.getWorlds().get(0);
            boolean cutWorld = false;
            boolean restored = false;
            String rigNote = "";
            if (sw != null) {
                org.bukkit.Location sp = sw.getSpawnLocation();
                int sx = sp.getBlockX() + 24;
                int sy = sp.getBlockY() + 1;
                int sz = sp.getBlockZ() + 24;
                String built = plugin.kinetics().demo(sw, sx, sy, sz);
                me.arena.electronics.registry.LocationKey sawHere =
                        new me.arena.electronics.registry.LocationKey(sw, sx, sy, sz);
                for (int drive = 0; drive < 600; drive++) {
                    // движок крутим вручную: 600 шагов = 30 с за пару секунд,
                    // ждать минуту реального времени в самотесте незачем
                    plugin.circuit().engineTick();
                }
                cutWorld = plugin.kinetics().blocksBroken() - before > 0;
                if (!cutWorld) {
                    // что умерло: сгоревшие детали и состояние мотора до откатки
                    StringBuilder bad = new StringBuilder();
                    for (CompInstance all : plugin.circuit().all()) {
                        if (all.burnt() || (all.fault != null && !all.fault.isEmpty())) {
                            bad.append("&8; ").append(all.def.id).append(" state=")
                                    .append(all.state).append(" «").append(all.fault).append("»");
                        }
                    }
                    CompInstance mm = plugin.circuit().get(
                            new me.arena.electronics.registry.LocationKey(sw, sx - 3, sy, sz - 1));
                    if (mm != null) {
                        bad.append("&8; мотор I=").append(Num.amp(mm.current))
                                .append(" доля оборотов ").append(String.format("%.4f", mm.speedPrev))
                                .append(" U=").append(Num.volt(mm.voltage))
                                .append(" нагрев ").append((int) mm.aux[0]).append("°");
                    }
                    rigNote += " " + bad;
                }
                me.arena.electronics.kinetics.KineticEngine.Rig dbg =
                        plugin.kinetics().rigs().isEmpty() ? null
                                : plugin.kinetics().rigs().iterator().next();
                if (dbg == null) {
                    rigNote = " &8механизм не собран: &f" + built;
                } else {
                    StringBuilder cur = new StringBuilder();
                    for (CompInstance m : dbg.motors) {
                        cur.append("&8, ток &f").append(Num.amp(m.current))
                                .append("&8, об/мин &f")
                                .append((int) (m.speedPrev * Math.max(1.0, m.def.rpm)))
                                .append(m.burnt() ? "&8 сгорел" : "");
                    }
                    rigNote = " &8деталей &f" + dbg.parts.size() + "&8, на валу &f"
                            + (int) dbg.rpm() + " об/мин&8, на валу &f"
                            + Num.watt(dbg.last.powerW) + cur + "&8, «" + dbg.state.note + "»";
                }
                plugin.kinetics().undoDemo();
                restored = plugin.kinetics().rigCount() == 0
                        && plugin.circuit().get(sawHere) == null
                        && sw.getBlockAt(sx + 1, sy, sz).getType().isAir()
                        && sw.getBlockAt(sx - 8, sy - 1, sz).getType().isAir();
                if (!cutWorld || !restored) {
                    s.sendMessage(Items.color("&8сборка: &f" + built
                            + " &8| деталей в мире: &f" + plugin.circuit().size()
                            + " &8| островков: &f" + plugin.circuit().lastIslands()
                            + " &8| err: &f" + plugin.circuit().lastError()
                            + " &8| перед пилой: &f"
                            + sw.getBlockAt(sx + 1, sy, sz).getType().name()));
                }
            }
            s.sendMessage(Items.color(((cutWorld && restored) ? "&aPASS " : "&cFAIL ")
                    + "&7живой механизм в мире: блоков срублено: &f"
                    + (plugin.kinetics().blocksBroken() - before)
                    + "&7, откат вернул мир: &f" + restored + rigNote));
            s.sendMessage(Items.color("&7в мире сейчас механизмов: &f" + plugin.kinetics().rigCount()
                    + "&7, блоков сломано приводом: &f" + plugin.kinetics().blocksBroken()
                    + (plugin.kinetics().rigCount() > 0 ? " &8(" + plugin.kinetics().lastNote() + ")" : "")));
        }

        // Осциллограф: экран собирается чистым кодом, без мира — ровно тот путь,
        // который проходит Renderer.updateScreen (трасса -> измерение -> масштаб
        // -> растер -> брайль). Если тут FAIL, в игре будет пустое стекло.
        {
            me.arena.electronics.circuit.ScopeTrace tr =
                    new me.arena.electronics.circuit.ScopeTrace();
            tr.setRate(0.05, 1);
            for (int si = 0; si < 100; si++) {
                tr.sample(6.0 * Math.sin(2 * Math.PI * 1.0 * si * 0.05));
            }
            me.arena.electronics.circuit.ScopeTrace.Stats st = tr.measure();
            double[] sc = me.arena.electronics.circuit.ScopeScreen.autoscale(
                    st.mean(), st.vpp(), 1.3);
            double[] vv = new double[me.arena.electronics.circuit.ScopeTrace.SCREEN_SAMPLES];
            for (int vi = 0; vi < vv.length; vi++) {
                vv[vi] = tr.at(st.start() + vi);
            }
            boolean[] px = me.arena.electronics.circuit.ScopeScreen.mask();
            me.arena.electronics.circuit.ScopeScreen.trace(vv, sc[0], sc[1], px);
            java.util.List<String> rows = java.util.Arrays.asList(
                    me.arena.electronics.circuit.ScopeScreen.braille(px));
            boolean frameOk = rows.size() == 4 && rows.stream().allMatch(r -> r.length() == 20);
            boolean measOk = Math.abs(st.vpp() - 12.0) < 0.05 && st.frequencyKnown()
                    && Math.abs(st.freqHz() - 1.0) < 0.02;
            s.sendMessage(Items.color((frameOk && measOk ? "&aPASS " : "&cFAIL ")
                    + "&7осциллограф: кадр &f" + me.arena.electronics.circuit.ScopeScreen.lit(px)
                    + " &7пикс., строки " + rows.size() + "×" + rows.get(0).length()
                    + " &8| Upp &f" + Num.volt(st.vpp())
                    + " &8| f &f" + String.format(java.util.Locale.ROOT, "%.3f Гц", st.freqHz())));
        }

        // Тот же путь, что выше, но через Renderer и реальный мир: TextDisplay
        // экрана обязан родиться над блоком и исчезнуть при удалении детали —
        // ровно так, как его снимает Events при поломке блока.
        {
            org.bukkit.World sw2 = org.bukkit.Bukkit.getWorlds().get(0);
            org.bukkit.Location sp = sw2.getSpawnLocation();
            me.arena.electronics.registry.LocationKey sk =
                    new me.arena.electronics.registry.LocationKey(sw2, sp.getBlockX() + 6,
                            sp.getBlockY(), sp.getBlockZ() + 6);
            me.arena.electronics.registry.Def sd =
                    me.arena.electronics.registry.Components.byId("oscilloscope");
            boolean born = false, gone = false;
            if (sd != null) {
                plugin.circuit().put(sk, sd, me.arena.electronics.registry.Orientation.NORTH);
                me.arena.electronics.circuit.CompInstance wc = plugin.circuit().get(sk);
                me.arena.electronics.circuit.ScopeTrace wt = plugin.circuit().scope(sk);
                wt.setRate(0.05, 1);
                for (int w2 = 0; w2 < 40; w2++) {
                    wt.sample(6.0 * Math.sin(2 * Math.PI * w2 / 20.0));
                }
                if (wc != null) {
                    plugin.renderer().requestUpdate(wc);
                }
                // три кадра: refresh-ticks = 2, нужен любой чётный момент
                plugin.renderer().tick();
                plugin.renderer().tick();
                plugin.renderer().tick();
                org.bukkit.Location probe = sk.block().getLocation().add(0.5, 1.6, 0.5);
                born = screensNear(probe) > 0;
                plugin.circuit().remove(sk);
                plugin.renderer().drop(sk);
                gone = screensNear(probe) == 0 && plugin.circuit().get(sk) == null
                        && !plugin.circuit().scopes().iterator().hasNext();
            }
            s.sendMessage(Items.color((born && gone ? "&aPASS " : "&cFAIL ")
                    + "&7живой экран осциллографа: TextDisplay над блоком появился: &f"
                    + born + "&7, после удаления детали исчез: &f" + gone
                    + " &8(мир " + sw2.getName() + ")"));
        }

        int missing = 0;
        for (me.arena.electronics.registry.Def d : me.arena.electronics.registry.Components.all()) {
            if (me.arena.electronics.gui.CatalogGui.whatIs(d).isEmpty()) {
                if (missing < 6) {
                    s.sendMessage(Items.color("&eнет описания для kind &f" + d.kind.name()));
                }
                missing++;
            }
            if (plugin.items().componentItem(d, 1) == null) {
                missing++;
            }
        }
        s.sendMessage(Items.color((missing == 0 ? "&aPASS " : "&cFAIL ")
                + "&7каталог: &f" + me.arena.electronics.registry.Components.size()
                + " &7предметов, описаний не хватает у &f" + missing));
    }

    /** Сколько многострочных TextDisplay рядом: экран осциллографа — единственный
     *  многострочный, у подписи значений одна строка. */
    private static int screensNear(org.bukkit.Location at) {
        int n = 0;
        for (org.bukkit.entity.Entity e : at.getWorld().getNearbyEntities(at, 2, 2, 2)) {
            if (e instanceof org.bukkit.entity.TextDisplay td && td.getText() != null
                    && td.getText().indexOf('\n') > 0) {
                n++;
            }
        }
        return n;
    }

    /**
     * Контур микроконтроллера в редакторе: чип в (1,3) на северном выводе VCC
     * держит батарейку 3 В через 330 Ом (потребление самой микросхемы — 470 кОм,
     * поэтому напряжение почти равно ЭДС), южный вывод — шина земли, а восточный
     * (нога 2 прошивки) идёт в нагрузку. Нагрузку подставляет вызывающий: светодиод
     * для нормального режима или провод для коротыша.
     */
    private static void paintMcu(me.arena.electronics.gui.SchematicGui g, String loadId) {
        g.put(1, 3, "mcu_atmega328", 0);
        g.put(0, 3, "wire_red", 0);
        g.put(0, 4, "resistor_330r", 1);
        g.put(0, 5, "battery_3", 1);
        g.put(0, 6, "wire_red", 0);
        g.put(1, 6, "wire_red", 0);
        g.put(2, 6, "wire_black", 0);
        g.put(2, 5, "wire_black", 0);
        g.put(2, 4, "ground_bus", 0);
        g.put(2, 3, "ground_bus", 0);
        if ("wire_red".equals(loadId)) {
            g.put(1, 4, "wire_red", 0);
            g.put(1, 5, "wire_red", 0);
        } else {
            g.put(1, 4, loadId, 3);           // анод на запад, к ноге 2
            g.put(1, 5, "resistor_330r", 1);
        }
    }

    /** Контур «батарейка -> резистор -> геркон -> светодиод» и магнит рядом с герконом. */
    /**
     * Контур редактора для деталей защиты: источник в (0,1), подопытная деталь в
     * (0,2), нагрузка в (0,3), возврат через шину земли в (2,3) — как в тесте
     * геркона, поэтому правило «соседства пар блоков» не надо объяснять заново.
     */
    /** Диагностика клетки стабилизатора: собралось ли вообще три вывода в узлы. */
    private static String regDiag(me.arena.electronics.gui.SchematicGui g) {
        CompInstance r = g.instanceAt(1, 4);
        if (r == null) {
            return "в клетке (1,4) никого нет";
        }
        int pins = 0;
        for (String n : r.pinNet) {
            if (n != null) {
                pins++;
            }
        }
        return r.def.id + " Uвх=" + Num.eng(r.pinVoltage[0], "\u0411")
                + " Uвых=" + Num.eng(r.pinVoltage[1], "\u0411") + " I=" + Num.amp(r.current)
                + " \u0394T=" + (int) r.aux[0] + "\u00B0 k=" + String.format(java.util.Locale.US, "%.2f", r.aux[3])
                + " state=" + r.state + " выводов в сети: " + pins
                + (r.fault.isEmpty() ? "" : " " + r.fault);
    }

    private static void paintLoop(me.arena.electronics.gui.SchematicGui g, String source,
                                  String dut, String load) {
        g.put(0, 0, "wire_red", 0);
        g.put(0, 1, source, 1);
        g.put(0, 2, dut, 1);
        g.put(0, 3, load, 1);
        g.put(0, 4, "wire_red", 0);
        g.put(0, 5, "wire_red", 0);
        g.put(1, 5, "wire_black", 0);
        g.put(2, 5, "wire_black", 0);
        g.put(2, 4, "wire_black", 0);
        g.put(2, 3, "ground_bus", 0);
        g.put(2, 2, "wire_black", 0);
        g.put(2, 1, "wire_black", 0);
        g.put(2, 0, "wire_black", 0);
        g.put(1, 0, "wire_black", 0);
    }

    /**
     * Стабилизатор в редакторе: IN/OUT вдоль ряда, GND — «северный» вывод, поэтому
     * деталь ставим в средний ряд (иначе выводу некуда смотреть). Нагрузка — между
     * OUT и общей землёй, источник — между IN и той же землёй.
     */
    private static void paintRegulator(me.arena.electronics.gui.SchematicGui g, String source,
                                       boolean shortCircuit) {
        g.put(1, 4, "reg_7805", 1);                       // IN на восток, OUT на запад, GND на север
        g.put(1, 5, source, 1);                           // «+» у источника — второй вывод,
                                                          // он смотрит на IN
        g.put(1, 6, "wire_black", 0);
        g.put(0, 6, "wire_black", 0);
        g.put(0, 5, "wire_black", 0);
        g.put(0, 4, "wire_black", 0);                     // земля stabilizatora
        g.put(1, 3, shortCircuit ? "wire_black" : "resistor_330r", 1);
        g.put(1, 2, "wire_black", 0);
        g.put(0, 3, "ground_bus", 1);                      // нулевая точка: без неё решатель
        g.put(0, 2, "wire_black", 0);                      // выбирает «массу» сам, и знак U плавает
    }

    private void paintReedLoop(me.arena.electronics.gui.SchematicGui g, boolean withMagnet) {
        g.put(0, 0, "wire_red", 0);
        g.put(0, 1, "battery_9", 1);
        g.put(0, 2, "resistor_330r", 1);
        g.put(0, 3, "reed_switch", 1);
        g.put(0, 4, "led_red", 1);
        g.put(0, 5, "wire_red", 0);
        g.put(1, 5, "wire_black", 0);
        g.put(2, 5, "wire_black", 0);
        g.put(2, 4, "wire_black", 0);
        g.put(2, 3, "ground_bus", 0);
        g.put(2, 2, "wire_black", 0);
        g.put(2, 1, "wire_black", 0);
        g.put(2, 0, "wire_black", 0);
        g.put(1, 0, "wire_black", 0);
        g.put(1, 3, withMagnet ? "magnet" : null, 0);
    }

    /** Есть ли в рецепте сам паяльный стол (тогда JEI покажет его как «станок»). */
    private static boolean stationIn(org.bukkit.inventory.Recipe r) {
        if (r instanceof org.bukkit.inventory.ShapedRecipe sh) {
            for (org.bukkit.inventory.RecipeChoice c : sh.getChoiceMap().values()) {
                if (c instanceof org.bukkit.inventory.RecipeChoice.ExactChoice ec
                        && isStation(ec.getChoices())) {
                    return true;
                }
            }
            return false;
        }
        if (r instanceof org.bukkit.inventory.ShapelessRecipe sl) {
            return isStation(sl.getIngredientList());
        }
        return false;
    }

    private static boolean isStation(java.util.Collection<? extends ItemStack> list) {
        for (ItemStack st : list) {
            Def sd = Items.defOf(st);
            if (sd != null && "soldering_station".equals(sd.id)) {
                return true;
            }
        }
        return false;
    }

    /** Каталог всех предметов плагина в GUI (то же, что /electro show). */
    private void show(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cКаталог открывается только игроку. "
                    + "Списком в чате: /elec list"));
            return;
        }
        me.arena.electronics.gui.CatalogGui.open(p, args.length > 1 ? parseInt(args[1], 0) : 0);
    }

    private void schematic(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        CompInstance c = lookAt(p);
        if (c == null) {
            // ничего под прицелом — рисуем новую схему на листе (GUI-редактор)
            me.arena.electronics.gui.SchematicGui.open(plugin, p);
            return;
        }
        List<CompInstance> island = Simulation.islandWith(plugin.circuit(), c);
        Netlist netlist = Netlist.of(island, Simulation.mergedNets(plugin.circuit()));
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "print";
        switch (what) {
            case "save" -> {
                String name = args.length > 2 ? args[2] : "my-circuit";
                c.lab = name;
                plugin.circuit().persistence().write(c);
                plugin.circuit().persistence().writeExtra(c.key.block(), netlist.serialize(), null);
                p.sendMessage(Items.color("&aСхема «" + name + "» записана в блок: " + netlist.size() + " узлов"));
            }
            case "check" -> {
                var lab = plugin.labs().byName(args.length > 2 ? args[2] : c.lab);
                if (lab == null) {
                    p.sendMessage(Items.color("&cЛабораторная не найдена. /lab list"));
                    return;
                }
                plugin.labs().report(p, lab, netlist);
            }
            case "copy" -> {
                p.sendMessage(Items.color("&7" + netlist.serialize()));
                p.sendMessage(Items.color("&eВставьте это в labs/*.yml в поле signature: &f" + netlist.signature()));
            }
            default -> {
                p.sendMessage(Items.color("&a=== Схема (" + netlist.size() + " узлов, сигнатура " + netlist.signature() + ") ==="));
                for (Netlist.Node n : netlist.nodes()) {
                    p.sendMessage(Items.color("&7" + n.id() + " &f" + n.defId() + " &8" + n.kind()
                            + (n.links().isEmpty() ? "" : " → " + String.join(", ", n.links()))));
                }
            }
        }
    }

    /** Дамп состояний цепи в консоль: /elec dump [мир] [число]. */
    private void dump(CommandSender s, String[] args) {
        plugin.forceSimulate();
        java.util.Collection<CompInstance> all;
        if (args.length > 1) {
            org.bukkit.World w = plugin.getServer().getWorld(args[1]);
            if (w == null) {
                s.sendMessage(Items.color("&cМир не найден."));
                return;
            }
            all = plugin.circuit().ofWorld(w.getName());
        } else {
            all = plugin.circuit().all();
        }
        int limit = args.length > 2 ? parseInt(args[2], 40) : 40;
        int n = 0;
        s.sendMessage(Items.color("&a=== компоненты (t=" + plugin.circuit().tick() + ") ==="));
        for (CompInstance c : all) {
            if (n++ >= limit) {
                s.sendMessage(Items.color("&7... ещё " + (all.size() - limit)));
                break;
            }
            s.sendMessage(Items.color("&7[" + c.key.x + "," + c.key.y + "," + c.key.z + "] &f" + c.def.id
                    + " &8o" + c.orientation.index + " s" + c.state
                    + " &fU=" + String.format(java.util.Locale.ROOT, "%.3f", c.voltage) + " В"
                    + " I=" + String.format(java.util.Locale.ROOT, "%.2f", c.current * 1000) + " мА"
                    + " P=" + String.format(java.util.Locale.ROOT, "%.3f", c.power) + " Вт"
                    + (c.brightness > 0.01 ? " &eb=" + String.format(java.util.Locale.ROOT, "%.2f", c.brightness) : "")
                    + (c.def.kind == me.arena.electronics.registry.Kind.BATTERY
                            ? " &e" + (int) Math.round(100 * me.arena.electronics.circuit.Battery.soc(c.aux))
                            + " %&7, через клеммы &f" + String.format(java.util.Locale.ROOT, "%+.3f",
                                    c.aux[me.arena.electronics.circuit.Battery.AMPERE_HOURS])
                            + " &7А·ч&7, потеря ёмкости &f" + (int) Math.round(100
                                    * me.arena.electronics.circuit.Battery.degrade(c.aux)) + " %"
                            + (c.aux[me.arena.electronics.circuit.Battery.TEMP] > 0.5
                                    ? " &c+&f" + (int) Math.round(
                                            c.aux[me.arena.electronics.circuit.Battery.TEMP]) + " °C"
                                    : "")
                            : "")
                    + (c.fault.isEmpty() ? "" : " &c" + c.fault)));
        }
        if (n == 0) {
            s.sendMessage(Items.color("&7Пусто. Поставь сборку: /elec build led_basic <мир> <x> <y> <z>"));
        }
    }

    // ---------------------------------------------------------------- build (пресеты)

    private void build(CommandSender s, String[] args) {
        String what = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "list";
        switch (what) {
            case "list" -> {
                List<String> names = plugin.builds().names();
                if (names.isEmpty()) {
                    s.sendMessage(Items.color("&eСборок нет. Файлы: plugins/Electronics/builds/*.json"));
                    return;
                }
                s.sendMessage(Items.color("&a=== Готовые сборки (" + names.size() + ") ==="));
                for (String n : names) {
                    var b = plugin.builds().load(n);
                    s.sendMessage(Items.color("&e/" + (b == null ? n : n + " &7— " + b.title
                            + " &8(" + b.size() + " блоков)")));
                }
                s.sendMessage(Items.color("&7/elec build <имя> &8— поставить под прицелом, "
                        + "/elec build save <имя> &8— сохранить свою цепь"));
            }
            case "save" -> {
                if (!(s instanceof Player p)) {
                    s.sendMessage(Items.color("&cТолько для игроков."));
                    return;
                }
                String name = args.length > 2 ? args[2].replaceAll("[^A-Za-z0-9_-]", "_") : "my_build";
                String json = plugin.builds().exportIsland(p);
                if (json == null) {
                    s.sendMessage(Items.color("&cНаведите на цепь, которую хотите сохранить."));
                    return;
                }
                try {
                    java.io.File f = new java.io.File(plugin.getDataFolder(), "builds/" + name + ".json");
                    f.getParentFile().mkdirs();
                    java.nio.file.Files.write(f.toPath(), json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    s.sendMessage(Items.color("&aСборка «" + name + "» сохранена: &f" + f.getPath()));
                } catch (java.io.IOException ex) {
                    s.sendMessage(Items.color("&cНе удалось записать файл: " + ex.getMessage()));
                }
            }
            default -> {
                var b = plugin.builds().load(what);
                if (b == null) {
                    s.sendMessage(Items.color("&cСборка «" + what + "» не найдена. /elec build list"));
                    return;
                }
                Block at = null;
                Player p = s instanceof Player pl ? pl : null;
                if (p != null && args.length < 6) {
                    if (!p.hasPermission("electronics.build")) {
                        s.sendMessage(Items.color("&cНет права electronics.build."));
                        return;
                    }
                    List<Block> ray = rayBlocks(p);
                    if (ray.isEmpty()) {
                        s.sendMessage(Items.color("&cНе вижу, куда ставить."));
                        return;
                    }
                    at = ray.get(ray.size() - 1).getRelative(org.bukkit.block.BlockFace.UP);
                } else if (args.length >= 6) {
                    // /elec build <имя> <мир> <x> <y> <z> — удобно с консоли
                    org.bukkit.World w = plugin.getServer().getWorld(args[2]);
                    if (w == null) {
                        s.sendMessage(Items.color("&cМир «" + args[2] + "» не найден."));
                        return;
                    }
                    at = w.getBlockAt(parseInt(args[3], 0), parseInt(args[4], 70), parseInt(args[5], 0));
                } else {
                    s.sendMessage(Items.color("&cС консоли: /elec build <имя> <мир> <x> <y> <z>"));
                    return;
                }
                var r = plugin.builds().place(p, b, at);
                s.sendMessage(Items.color("&aСборка «" + b.title + "&a»: установлено &f" + r.placed
                        + "&a, подставок &f" + r.hosts + (r.skipped > 0 ? "&e, пропущено " + r.skipped : "")
                        + (r.unknown.isEmpty() ? "" : "&c, неизвестные id: " + String.join(", ", r.unknown))));
                plugin.forceSimulate();
            }
        }
    }

    // ---------------------------------------------------------------- утилиты

    private CompInstance lookAt(Player p) {
        for (Block b : rayBlocks(p)) {
            CompInstance c = plugin.circuit().get(b);
            if (c != null) {
                return c;
            }
        }
        return null;
    }

    private List<org.bukkit.block.Block> rayBlocks(Player p) {
        List<org.bukkit.block.Block> out = new ArrayList<>();
        Location eye = p.getEyeLocation();
        org.bukkit.util.Vector dir = eye.getDirection().normalize().multiply(0.25);
        Location cur = eye.clone();
        for (int i = 0; i < 40; i++) {
            cur.add(dir);
            out.add(cur.getBlock());
            if (!cur.getBlock().getType().isAir() && out.size() > 2) {
                break;
            }
        }
        return out;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException ex) {
            return def;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String c : new String[]{"help", "give", "list", "model", "info", "sim", "repair", "clear",
                    "rotate", "mode", "recipe", "solder", "schematic", "build", "dump", "rig",
                    "show", "reload", "rescan", "veins"}) {
                if (c.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(c);
                }
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            for (String id : Components.ids()) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
            out.add("all");
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("list")) {
            for (var d : Components.all()) {
                out.add(d.kind.name().toLowerCase(Locale.ROOT));
            }
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("model")) {
            for (String id : Components.ids()) {
                if (id.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    out.add(id);
                }
            }
        }
        return out;
    }

}
