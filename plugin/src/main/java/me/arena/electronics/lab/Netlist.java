package me.arena.electronics.lab;

import me.arena.electronics.circuit.Circuit;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * «Схема» цепи: описание из чего и как собрано, без напряжений.
 * Используется блоком «пустая схема» — сохранить/сверить/распечатать.
 *
 * Топология приводится к каноническому виду алгоритмом Вайсфейлера–Лемана
 * (6 итераций цветового рефинмента Вайсфейлера–Лемана), поэтому две одинаковые по структуре цепи
 * дают одинаковую сигнатуру, независимо от того, где именно в мире они собраны.
 */
public final class Netlist {

    public record Node(String id, String defId, String kind, List<String> links) {
    }

    private final List<Node> nodes;
    private final String signature;

    private Netlist(List<Node> nodes, String signature) {
        this.nodes = nodes;
        this.signature = signature;
    }

    public List<Node> nodes() {
        return nodes;
    }

    public String signature() {
        return signature;
    }

    public int size() {
        return nodes.size();
    }

    public boolean matches(Netlist other) {
        return other != null && signature.equals(other.signature);
    }

    public static Netlist of(List<CompInstance> comps, Map<String, String> netOfPin) {
        // 1. карта компонент -> узлы
        Map<String, List<String>> compNets = new LinkedHashMap<>();
        Map<String, CompInstance> byId = new HashMap<>();
        for (CompInstance c : comps) {
            if (c.def.kind == Kind.BOARD || c.def.kind == Kind.EMPTY) {
                continue;
            }
            String cid = id(c);
            byId.put(cid, c);
            List<String> nets = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                String net = c.pinNet[i];
                if (net == null) {
                    continue;
                }
                String merged = netOfPin == null ? net : netOfPin.getOrDefault(net, net);
                if (!nets.contains(merged)) {
                    nets.add(merged);
                }
            }
            compNets.put(cid, nets);
        }
        // 2. соседство: узлы, через которые компоненты соединяются
        Map<String, List<String>> neighbours = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : compNets.entrySet()) {
            List<String> list = new ArrayList<>();
            for (String net : e.getValue()) {
                for (Map.Entry<String, List<String>> o : compNets.entrySet()) {
                    if (o.getKey().equals(e.getKey())) {
                        continue;
                    }
                    if (o.getValue().contains(net)) {
                        list.add(o.getKey());
                    }
                }
            }
            list.sort(Comparator.naturalOrder());
            neighbours.put(e.getKey(), list);
        }
        // 3. рефинмент цветов
        Map<String, String> color = new HashMap<>();
        for (String cid : compNets.keySet()) {
            CompInstance c = byId.get(cid);
            color.put(cid, c.def.id + ":" + c.def.kind.name());
        }
        for (int round = 0; round < 6; round++) {
            Map<String, String> next = new HashMap<>();
            for (String cid : compNets.keySet()) {
                List<String> nb = new ArrayList<>(neighbours.getOrDefault(cid, List.of()));
                List<String> parts = new ArrayList<>();
                for (String n : nb) {
                    parts.add(color.getOrDefault(n, "?"));
                }
                parts.sort(Comparator.naturalOrder());
                next.put(cid, color.get(cid) + "(" + String.join(",", parts) + ")");
            }
            color = next;
        }
        List<String> sortedColors = new ArrayList<>(color.values());
        sortedColors.sort(Comparator.naturalOrder());

        // узел-номера вместо id с координатами: сигнатура не зависит от места сборки
        Map<String, Integer> nodeNo = new HashMap<>();
        final Map<String, String> colorKey = color;
        List<String> ids = new ArrayList<>(compNets.keySet());
        ids.sort(Comparator.comparing((String id) -> colorKey.get(id)).thenComparing(Comparator.naturalOrder()));
        for (int i = 0; i < ids.size(); i++) {
            nodeNo.put(ids.get(i), i + 1);
        }
        List<Node> nodes = new ArrayList<>();
        for (String cid : ids) {
            CompInstance c = byId.get(cid);
            List<String> links = new ArrayList<>();
            for (String nb : neighbours.getOrDefault(cid, List.of())) {
                links.add("n" + nodeNo.getOrDefault(nb, 0));
            }
            links.sort(Comparator.naturalOrder());
            nodes.add(new Node("n" + nodeNo.get(cid), c.def.id, c.def.kind.name(), links));
        }
        String sig = Integer.toHexString(String.join("|", sortedColors).hashCode()) + ":" + nodes.size();
        return new Netlist(nodes, sig);
    }

    private static String id(CompInstance c) {
        return c.def.id + "@" + c.key.x + "," + c.key.y + "," + c.key.z;
    }

    /** Текстовое представление — то, что видно на «пустой схеме». */
    public String toText() {
        StringBuilder sb = new StringBuilder();
        for (Node n : nodes) {
            sb.append(n.id()).append("  [").append(n.kind()).append("]");
            if (!n.links().isEmpty()) {
                sb.append(" -> ").append(String.join(", ", n.links()));
            }
            sb.append('\n');
        }
        return sb.toString().trim();
    }

    /** Простой сериализуемый формат (для PDC/файла). */
    public String serialize() {
        StringBuilder sb = new StringBuilder();
        sb.append(signature).append(';');
        for (Node n : nodes) {
            sb.append(n.id()).append(',').append(n.kind()).append(',').append(String.join("+", n.links())).append(';');
        }
        return sb.toString();
    }

    public static Netlist deserialize(String s) {
        if (s == null || s.isEmpty()) {
            return null;
        }
        int i = s.indexOf(';');
        if (i < 0) {
            return null;
        }
        String sig = s.substring(0, i);
        List<Node> nodes = new ArrayList<>();
        for (String part : s.substring(i + 1).split(";")) {
            if (part.isEmpty()) {
                continue;
            }
            String[] f = part.split(",", 3);
            if (f.length < 2) {
                continue;
            }
            List<String> links = new ArrayList<>();
            if (f.length > 2 && !f[2].isEmpty()) {
                for (String l : f[2].split("\\+")) {
                    links.add(l);
                }
            }
            nodes.add(new Node(f[0], "", f[1], links));
        }
        return new Netlist(nodes, sig);
    }

    /** Набор компонентов по типам (для проверки лабораторной). */
    public Map<String, Integer> counts() {
        Map<String, Integer> out = new TreeMap<>();
        for (Node n : nodes) {
            out.merge(n.defId(), 1, Integer::sum);
        }
        return out;
    }

    public String describe() {
        StringBuilder sb = new StringBuilder();
        counts().forEach((k, v) -> sb.append(k).append('x').append(v).append(' '));
        return sb.toString().trim().toLowerCase(Locale.ROOT);
    }

    /** Проверка: содержит ли схема все требуемые компоненты (по типам). */
    public boolean containsRequired(Map<String, Integer> required) {
        Map<String, Integer> have = counts();
        for (Map.Entry<String, Integer> e : required.entrySet()) {
            if (have.getOrDefault(e.getKey(), 0) < e.getValue()) {
                return false;
            }
        }
        return true;
    }
}
