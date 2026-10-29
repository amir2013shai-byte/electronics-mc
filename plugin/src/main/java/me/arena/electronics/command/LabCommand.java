package me.arena.electronics.command;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.circuit.Simulation;
import me.arena.electronics.lab.LabManager;
import me.arena.electronics.lab.Netlist;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** /lab — практические работы: список, задание, проверка, сохранение эталона. */
public final class LabCommand implements CommandExecutor, TabCompleter {

    private final ElectronicsPlugin plugin;

    public LabCommand(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        String a0 = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "list";
        switch (a0) {
            case "list" -> list(s);
            case "show" -> show(s, args);
            case "check" -> check(s, args);
            case "save" -> save(s, args);
            case "start" -> start(s, args);
            default -> {
                s.sendMessage(Items.color("&e/lab list|show <работа>|check <работа>|save <работа>"));
            }
        }
        return true;
    }

    private void list(CommandSender s) {
        List<String> names = plugin.labs().names();
        if (names.isEmpty()) {
            s.sendMessage(Items.color("&eЛабораторные не найдены. Файлы: plugins/Electronics/labs/*.yml"));
            return;
        }
        s.sendMessage(Items.color("&a=== Практические работы (" + names.size() + ") ==="));
        for (String n : names) {
            LabManager.Lab lab = plugin.labs().byName(n);
            if (lab == null) {
                continue;
            }
            s.sendMessage(Items.color("&e/" + n + " &7— " + lab.name
                    + (lab.components.isEmpty() ? "" : " &8(" + lab.components.size() + " видов)")
                    + (lab.signature.isEmpty() ? " &c[эталон не зафиксирован]" : " &a[эталон есть]")));
        }
        s.sendMessage(Items.color("&7/lab show <имя> &8— задание, /lab check <имя> &8— проверка"));
    }

    private void show(CommandSender s, String[] args) {
        LabManager.Lab lab = find(s, args);
        if (lab == null) {
            return;
        }
        s.sendMessage(Items.color("&a=== " + lab.name + " ==="));
        if (!lab.task.isEmpty()) {
            s.sendMessage(Items.color("&7" + lab.task.replace("\n", " ")));
        }
        if (!lab.components.isEmpty()) {
            s.sendMessage(Items.color("&eНужны компоненты:"));
            lab.components.forEach((k, v) -> {
                var d = me.arena.electronics.registry.Components.byId(k);
                s.sendMessage(Items.color("  &f" + (d == null ? k : d.nameRu) + " &7x" + v
                        + "  &8/elec give " + k + " " + v));
            });
        }
        if (!lab.expect.isEmpty()) {
            s.sendMessage(Items.color("&bОжидается: &f" + lab.expect));
        }
        if (!lab.signature.isEmpty()) {
            s.sendMessage(Items.color("&8Эталонная топология: " + lab.signature));
        }
    }

    private void check(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        LabManager.Lab lab = find(s, args);
        if (lab == null) {
            return;
        }
        CompInstance target = nearestComponent(p);
        if (target == null) {
            s.sendMessage(Items.color("&cРядом нет цепи: соберите схему и наведите на неё (или используйте блок «Пустая схема»)."));
            return;
        }
        List<CompInstance> island = Simulation.islandWith(plugin.circuit(), target);
        Netlist netlist = Netlist.of(island, Simulation.mergedNets(plugin.circuit()));
        plugin.labs().report(p, lab, netlist);
    }

    private void save(CommandSender s, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage(Items.color("&cТолько для игроков."));
            return;
        }
        if (!p.hasPermission("electronics.admin")) {
            s.sendMessage(Items.color("&cНет права electronics.admin"));
            return;
        }
        LabManager.Lab lab = find(s, args);
        if (lab == null) {
            return;
        }
        CompInstance target = nearestComponent(p);
        if (target == null) {
            s.sendMessage(Items.color("&cНаведите на цепь, которую хотите записать как эталон."));
            return;
        }
        List<CompInstance> island = Simulation.islandWith(plugin.circuit(), target);
        Netlist netlist = Netlist.of(island, Simulation.mergedNets(plugin.circuit()));
        lab.signature = netlist.signature();
        java.io.File f = new java.io.File(plugin.getDataFolder(), "labs/" + lab.file + ".yml");
        try {
            org.bukkit.configuration.file.YamlConfiguration y =
                    org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
            y.set("signature", lab.signature);
            y.save(f);
            s.sendMessage(Items.color("&aЭталон записан: " + lab.file + " → " + lab.signature));
        } catch (java.io.IOException ex) {
            s.sendMessage(Items.color("&cНе удалось сохранить " + f + ": " + ex.getMessage()));
        }
    }

    private void start(CommandSender s, String[] args) {
        LabManager.Lab lab = find(s, args);
        if (lab == null) {
            return;
        }
        show(s, args);
        if (!(s instanceof Player p)) {
            return;
        }
        if (p.hasPermission("electronics.give") && !lab.components.isEmpty()) {
            lab.components.forEach((k, v) -> {
                var d = me.arena.electronics.registry.Components.byId(k);
                if (d != null) {
                    p.getInventory().addItem(plugin.items().componentItem(d, v));
                }
            });
            s.sendMessage(Items.color("&aВыданы компоненты для работы «" + lab.name + "»"));
        }
    }

    private LabManager.Lab find(CommandSender s, String[] args) {
        if (args.length < 2) {
            s.sendMessage(Items.color("&eУкажите имя работы. /lab list"));
            return null;
        }
        LabManager.Lab lab = plugin.labs().byName(args[1]);
        if (lab == null) {
            s.sendMessage(Items.color("&cРабота «" + args[1] + "» не найдена. /lab list"));
        }
        return lab;
    }

    private CompInstance nearestComponent(Player p) {
        double best = 7.0;
        CompInstance found = null;
        for (CompInstance c : plugin.circuit().ofWorld(p.getWorld().getName())) {
            var b = c.key.block();
            if (b == null) {
                continue;
            }
            double d = b.getLocation().add(0.5, 0.5, 0.5).distance(p.getEyeLocation());
            if (d < best) {
                best = d;
                found = c;
            }
        }
        return found;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String x : new String[]{"list", "show", "check", "save", "start"}) {
                if (x.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    out.add(x);
                }
            }
            return out;
        }
        for (String n : plugin.labs().names()) {
            if (n.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                out.add(n);
            }
        }
        return out;
    }
}
