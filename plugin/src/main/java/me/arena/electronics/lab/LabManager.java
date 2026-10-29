package me.arena.electronics.lab;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Практические работы. labs/*.yml:
 * <pre>
 * name: "Делитель напряжения"
 * task: "Собери делитель: батарея 9 В, два резистора, измерь среднюю точку"
 * components: {battery_9: 1, resistor_1k: 1, resistor_2k: 1, voltmeter: 1}
 * expect: "U средней точки ~ 6 В"
 * </pre>
 */
public final class LabManager {

    public static final class Lab {
        public String file;
        public String name = "";
        public String task = "";
        public String expect = "";
        public final Map<String, Integer> components = new HashMap<>();
        public String signature = "";
    }

    private final ElectronicsPlugin plugin;
    private final Map<String, Lab> labs = new HashMap<>();
    /** Последняя схема игрока (для /lab check без блока «схема»). */
    private final Map<String, String> lastResult = new HashMap<>();

    public LabManager(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        labs.clear();
        File dir = new File(plugin.getDataFolder(), "labs");
        if (!dir.isDirectory()) {
            boolean made = dir.mkdirs();
            if (made) {
                writeExamples(dir);
            }
        }
        File[] files = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return;
        }
        for (File f : files) {
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                Lab lab = new Lab();
                lab.file = f.getName().replace(".yml", "");
                lab.name = y.getString("name", lab.file);
                lab.task = y.getString("task", "");
                lab.expect = y.getString("expect", "");
                ConfigurationSection cs = y.getConfigurationSection("components");
                if (cs != null) {
                    for (String k : cs.getKeys(false)) {
                        Def d = Components.byId(k);
                        if (d != null) {
                            lab.components.put(d.id, cs.getInt(k, 1));
                        }
                    }
                }
                lab.signature = y.getString("signature", "");
                labs.put(lab.file.toLowerCase(Locale.ROOT), lab);
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("лаб " + f.getName() + ": " + ex.getMessage());
            }
        }
        plugin.getLogger().info("загружено практических работ: " + labs.size());
    }

    private void writeExamples(File dir) {
        String[] examples = {
                "01-led.yaml", "02-divider.yaml", "03-transistor-key.yaml"
        };
        for (String name : examples) {
            try {
                File f = new File(dir, name.replace(".yaml", ".yml"));
                String body = switch (name) {
                    case "01-led.yaml" -> """
                            name: "Светодиод и гасящий резистор"
                            task: >
                              Поставьте подставку (bench), на неё: батарею 9 В, резистор 330 Ом
                              и красный светодиод в одну линию (контакты соприкасаются), замкните
                              цепь проводом. Сравните: без резистора светодиод сгорает.
                            expect: "LED горит, I ~ 20 мА, P ~ 0.05 Вт"
                            components:
                              battery_9: 1
                              resistor_330r: 1
                              led_red: 1
                              wire_red: 2
                              ground_bus: 1
                            """;
                    case "02-divider.yaml" -> """
                            name: "Делитель напряжения"
                            task: "Собери цепочку: батарея 4.5 В -> резистор 1k -> резистор 4.7k -> шина земли. Поставь вольтметр на среднюю точку (стопкой на узел между резисторами)."
                            expect: "Средняя точка ~ 3.52 В (4.5 · 4.7 / (1 + 4.7))"
                            components:
                              battery_9: 1
                              resistor_1k: 1
                              resistor_4k7: 1
                              ground_bus: 1
                              voltmeter: 1
                            """;
                    default -> """
                            name: "Транзистор как ключ"
                            task: "База через резистор 4.7к к кнопке, коллектор к светодиоду с резистором, эмиттер на землю."
                            expect: "Светодиод горит только при нажатой кнопке"
                            components:
                              transistor_npn: 1
                              button: 1
                              resistor_4k7: 1
                              led_green: 1
                              resistor_330r: 1
                              ground_bus: 1
                              battery_9: 1
                            """;
                };
                java.nio.file.Files.writeString(f.toPath(), body);
            } catch (java.io.IOException ignored) {
                // не страшно: примеры не записались
            }
        }
    }

    public Map<String, Lab> labs() {
        return labs;
    }

    public Lab byName(String name) {
        return name == null ? null : labs.get(name.toLowerCase(Locale.ROOT));
    }

    /** ПКМ по блоку «пустая схема». */
    public void onSchematicClick(Player p, CompInstance c) {
        List<CompInstance> island = me.arena.electronics.circuit.Simulation.islandWith(plugin.circuit(), c);
        Map<String, String> nets = me.arena.electronics.circuit.Simulation.mergedNets(plugin.circuit());
        Netlist netlist = Netlist.of(island, nets);
        String key = c.lab == null || c.lab.isEmpty() ? "" : c.lab;
        if (key.isEmpty()) {
            String msg = netlist.nodes().isEmpty()
                    ? "Схема пуста: сначала соберите цепь рядом с этой платой."
                    : "Записано узлов: " + netlist.size() + " (" + netlist.describe() + ")";
            c.lab = "";
            plugin.circuit().persistence().writeExtra(c.key.block(), netlist.serialize(), msg);
            p.sendMessage(Items.color("&aЗаписано: ") + netlist.size() + " компонентов, сигнатура " + netlist.signature());
            p.sendMessage(Items.color("&7" + netlist.toText().replace("\n", "\n&7")));
            return;
        }
        Lab lab = byName(key);
        if (lab == null) {
            p.sendMessage(Items.color("&cЛабораторная «" + key + "» не найдена. /lab list"));
            return;
        }
        report(p, lab, netlist);
    }

    public void report(Player p, Lab lab, Netlist netlist) {
        StringBuilder sb = new StringBuilder();
        sb.append(Items.color("&a=== Практическая работа: " + lab.name + " ===\n"));
        if (!lab.task.isEmpty()) {
            sb.append(Items.color("&7" + lab.task.replace("\n", " ") + "\n"));
        }
        if (lab.components.isEmpty()) {
            sb.append(Items.color("&eВ работе не задан список компонентов — сверка только по топологии.\n"));
        } else if (netlist.containsRequired(lab.components)) {
            sb.append(Items.color("&a✔ Состав схемы подходит.\n"));
        } else {
            sb.append(Items.color("&c✗ Не хватает компонентов:\n"));
            Map<String, Integer> have = netlist.counts();
            for (Map.Entry<String, Integer> e : lab.components.entrySet()) {
                int got = have.getOrDefault(e.getKey(), 0);
                if (got < e.getValue()) {
                    Def d = Components.byId(e.getKey());
                    sb.append(Items.color("  &c- "))
                            .append(d == null ? e.getKey() : d.nameRu)
                            .append(Items.color(" &7(" + got + "/" + e.getValue() + ")"));
                    sb.append('\n');
                }
            }
        }
        if (!lab.signature.isEmpty()) {
            if (lab.signature.equals(netlist.signature())) {
                sb.append(Items.color("&a✔ Топология совпадает с эталоном. Работа зачтена!\n"));
            } else {
                sb.append(Items.color("&eТопология отличается от эталона (сигнатура " + netlist.signature()
                        + " / " + lab.signature + ")\n"));
                sb.append(Items.color("&7Чтобы зафиксировать эталон: /lab save " + lab.file + "\n"));
            }
        }
        if (!lab.expect.isEmpty()) {
            sb.append(Items.color("&bОжидаемый результат: &f" + lab.expect + "\n"));
        }
        p.sendMessage(sb.toString().trim());
        lastResult.put(p.getName(), netlist.signature());
    }

    public String lastResult(String player) {
        return lastResult.getOrDefault(player, "");
    }

    public List<String> names() {
        return new ArrayList<>(labs.keySet());
    }
}
