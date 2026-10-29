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
            case "reload" -> reload(s);
            case "rescan" -> rescan(s);
            case "show" -> show(s, args);
            case "selftest" -> selftest(s);
            case "schematic" -> schematic(s, args);
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
        s.sendMessage(Items.color("&e/elec schematic save|check|print <имя> &7— работа с «пустой схемой»"));
        s.sendMessage(Items.color("&e/elec build list|<имя>|save <имя> &7— готовые сборки (учебные примеры)"));
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
        int fixed = 0;
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            for (CompInstance c : plugin.circuit().all()) {
                if (c.burnt()) {
                    c.clearFault();
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
            c.clearFault();
            plugin.circuit().persistence().write(c);
            plugin.renderer().requestUpdate(c);
            fixed = 1;
        }
        plugin.circuit().markDirty();
        s.sendMessage(Items.color("&aПочинено компонентов: " + fixed));
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
                    "rotate", "mode", "recipe", "schematic", "reload", "rescan"}) {
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
