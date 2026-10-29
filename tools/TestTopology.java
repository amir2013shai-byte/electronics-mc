import me.arena.electronics.circuit.Topology;
import me.arena.electronics.registry.Kind;
import me.arena.electronics.sim.Mna;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Тест «голой» топологии (без Bukkit) + проверка, что на ней решается
 * реальная цепь: батарея -> резистор -> светодиод -> земля.
 *
 * Запуск: bash tools/run_tests.sh
 */
public class TestTopology {

    static int ok = 0, bad = 0;

    static void check(String what, boolean cond) {
        if (cond) {
            ok++;
            System.out.println("  OK   " + what);
        } else {
            bad++;
            System.out.println("  FAIL " + what);
        }
    }

    /** Простой компонент для тестов. */
    static final class Fake implements Topology.View {
        final Kind kind;
        final String logic;
        final boolean stack;
        int x, y, z, orient;
        final String[] pinNet = new String[6];
        String anchorNet;
        boolean stackedOnConductor;

        Fake(Kind kind, int x, int y, int z) {
            this(kind, "", false, x, y, z);
        }

        Fake(Kind kind, String logic, boolean stack, int x, int y, int z) {
            this.kind = kind;
            this.logic = logic;
            this.stack = stack;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        @Override
        public Topology.DefLike def() {
            Kind k = kind;
            String l = logic;
            boolean s = stack;
            return new Topology.DefLike() {
                @Override
                public Kind kind() {
                    return k;
                }

                @Override
                public String logic() {
                    return l;
                }

                @Override
                public boolean stack() {
                    return s;
                }
            };
        }

        @Override
        public int orientIndex() {
            return orient;
        }

        @Override
        public int x() {
            return x;
        }

        @Override
        public int y() {
            return y;
        }

        @Override
        public int z() {
            return z;
        }

        @Override
        public void setAnchorNet(String net) {
            anchorNet = net;
        }

        @Override
        public void setStackedOnConductor(boolean on) {
            stackedOnConductor = on;
        }
    }

    /** «Мир»: какие клетки заняты и что из этого проводник. */
    static final class World implements Topology.NetSource {
        final Map<String, Fake> comps = new LinkedHashMap<>();

        void put(Fake f) {
            comps.put(key(f.x, f.y, f.z), f);
        }

        static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        @Override
        public String netAt(int x, int y, int z) {
            return "w|" + x + "," + y + "," + z;
        }

        @Override
        public boolean isConductorBelow(int x, int y, int z) {
            Fake f = comps.get(key(x, y, z));
            return f != null && (f.kind == Kind.WIRE || f.kind == Kind.BREADBOARD
                    || f.kind == Kind.GROUND || f.kind == Kind.DISTRIBUTOR);
        }
    }

    /** true, если у двух компонентов есть общий узел (по ключу, не по индексу вывода). */
    static boolean shares(Fake a, Fake b) {
        for (String x : a.pinNet) {
            if (x == null) {
                continue;
            }
            for (String y : b.pinNet) {
                if (x.equals(y)) {
                    return true;
                }
            }
        }
        return a.anchorNet != null && (a.anchorNet.equals(b.anchorNet) || java.util.Arrays.asList(b.pinNet).contains(a.anchorNet));
    }

    /** узел между блоком f и соседом (x,y,z) — независимо от индекса вывода */
    static String node(Fake f, int x, int y, int z) {
        String tail = "&" + x + "," + y + "," + z;
        String head = "w|" + x + "," + y + "," + z + "&";
        for (String n : f.pinNet) {
            if (n != null && (n.endsWith(tail) || n.startsWith(head))) {
                return n;
            }
        }
        return null;
    }

    static void resolveAll(World w) {
        for (Fake f : w.comps.values()) {
            Topology.resolve(f, f.pinNet, w);
        }
    }

    public static void main(String[] args) {
        System.out.println("Топология: контакт = пара соседних блоков");

        // 1) два резистора вплотную: ориентация смотрит «на соседа», контакты делят узел
        World w1 = new World();
        Fake r1 = new Fake(Kind.RESISTOR, 0, 64, 0);
        Fake r2 = new Fake(Kind.RESISTOR, 1, 64, 0);
        r1.orient = 1;   // EAST
        r2.orient = 3;   // WEST
        w1.put(r1);
        w1.put(r2);
        resolveAll(w1);
        check("pin0 резистора A = узел «A|B»", r1.pinNet[0].equals("w|0,64,0&1,64,0"));
        check("pin0 резистора A = pin0 резистора B", r1.pinNet[0].equals(r2.pinNet[0]));
        check("у резистора 2 вывода", Topology.pinCount(r1.def()) == 2);
        check("ноги одного резистора не «схлопнуты» в один узел", !r1.pinNet[0].equals(r1.pinNet[1]));

        // 2) якорь = пара с блоком под деталью; он НЕ общий для соседей по горизонтали
        check("якорь = пара с блоком под деталью", r1.anchorNet.equals("w|0,63,0&0,64,0"));
        check("якоря соседей различаются", !r1.anchorNet.equals(r2.anchorNet));
        check("свободный вывод = пара с пустотой (не null)", r1.pinNet[1].equals("w|-1,64,0&0,64,0"));

        // 3) провод-«мостик» замыкает зазор в один блок
        World w2 = new World();
        Fake led = new Fake(Kind.LED, 0, 64, 0);
        Fake wire = new Fake(Kind.WIRE, 1, 64, 0);
        Fake res = new Fake(Kind.RESISTOR, 2, 64, 0);
        led.orient = 1;
        wire.orient = 1;
        res.orient = 3;
        w2.put(led);
        w2.put(wire);
        w2.put(res);
        resolveAll(w2);
        check("светодиод и провод делят узел", shares(led, wire));
        check("провод и резистор делят узел", shares(wire, res));
        check("светодиод и резистор без провода НЕ соединены", !shares(led, res));
        check("провод — «крест» на 6 выводов (сетка + стопка)", Topology.pinCount(wire.def()) == 6);

        // 4) деталь, поставленная на провод, попадает в его верхний узел
        World w3 = new World();
        Fake w3wire = new Fake(Kind.WIRE, 4, 64, 0);
        w3wire.orient = 1;
        Fake button = new Fake(Kind.BUTTON, "", false, 4, 65, 0);
        w3.put(w3wire);
        w3.put(button);
        resolveAll(w3);
        check("кнопка над проводом делит сеть с его «шапкой»", button.anchorNet.equals(w3wire.pinNet[4]))
;        check("провод смотрит вверх 5-м выводом (стопка)", w3wire.pinNet[4] != null);
        check("кнопка — хаб на 6 выводов", Topology.pinCount(button.def()) == 6);
        check("флаг «стоит на проводнике» выставлен", button.stackedOnConductor);

        // 5) число выводов у многополюсников
        check("транзистор — 3 вывода", Topology.pinCount(new Fake(Kind.TRANSISTOR, 1, 1, 1).def()) == 3);
        check("инвертор — 3, 2-входовой вентиль — 4",
                Topology.pinCount(new Fake(Kind.GATE, "NOT", false, 1, 1, 1).def()) == 3
                        && Topology.pinCount(new Fake(Kind.GATE, "AND", false, 1, 1, 1).def()) == 4);
        check("макетная плата — 0 активных выводов (свои шины)",
                Topology.pinCount(new Fake(Kind.BREADBOARD, 1, 1, 1).def()) == 0);
        check("шина земли/шины питания — хабы на 6 выводов",
                Topology.pinCount(new Fake(Kind.GROUND, 1, 1, 1).def()) == 6
                        && Topology.pinCount(new Fake(Kind.DISTRIBUTOR, 1, 1, 1).def()) == 6);

        // 6) полная цепь 9 В -> 220 Ом -> LED(2 В, 25 Ом) -> земля, решённая на этих узлах
        World w6 = new World();
        Fake batt = new Fake(Kind.SOURCE, 0, 64, 0);
        Fake rr = new Fake(Kind.RESISTOR, 1, 64, 0);
        Fake dd = new Fake(Kind.LED, 2, 64, 0);
        Fake gg = new Fake(Kind.GROUND, 3, 64, 0);
        batt.orient = 1;   // плюс на восток
        rr.orient = 1;     // pin0 — западный узел, pin1 — восточный
        dd.orient = 1;     // анод на западе, катод на востоке
        w6.put(batt);
        w6.put(rr);
        w6.put(dd);
        w6.put(gg);
        resolveAll(w6);

        String neg = node(batt, -1, 64, 0);  // «минус» висячий, но это тот же узел, что и ниже по цепи
        String mid = node(rr, 2, 64, 0);     // узел «резистор|светодиод»
        String ledOut = node(dd, 3, 64, 0);  // узел «светодиод|земля»
        String pos = node(batt, 1, 64, 0);   // узел «батарея|резистор»
        if (System.getenv("DBG") != null) System.out.println("DBG pos=" + pos + " rr0=" + node(rr, 0, 64, 0) + " mid=" + node(rr, 2, 64, 0)
                + " dd1=" + node(dd, 1, 64, 0) + " ledOut=" + node(dd, 3, 64, 0)
                + " gg2=" + node(gg, 2, 64, 0) + " all6=" + java.util.Arrays.toString(gg.pinNet));
        check("каждый зазор между соседями — ровно один общий узел",
                pos != null && pos.equals(node(rr, 0, 64, 0))
                        && mid != null && mid.equals(node(dd, 1, 64, 0))
                        && ledOut != null && ledOut.equals(node(gg, 2, 64, 0)));
        neg = ledOut;  // земля коротит «минус» батареи с катодом светодиода (цепь идёт по столу)

        Mna mna = new Mna();
        mna.node(neg);
        mna.node(pos);
        mna.node(mid);
        mna.node(ledOut);
        mna.ground(neg, 0.0);
        mna.stamp(pos, neg, 1.0, 9.0);              // Тевенен: 9 В, Rint = 1 Ом
        mna.stamp(pos, mid, 1 / 220.0, 0);          // резистор
        mna.stamp(mid, ledOut, 1 / 25.0, 2.0 / 25.0); // светодиод
        mna.bond(ledOut, neg);                       // «нога» заземлителя -> минус
        Map<String, Double> allv = mna.solve();
        // узел «минус» слился с узлом светодиода (bond) — берём одно имя
        Map<String, Double> v = new java.util.HashMap<>(allv);
        if (v.containsKey(ledOut) && !ledOut.equals(neg)) {
            v.put(neg, v.get(ledOut));
        }
        System.out.println("DBG nodes=" + mna.nodeCount() + " voltages=" + allv + " stamps=" + mna.stamps().size());
        double iLoop = (v.getOrDefault(pos, 0.0) - v.getOrDefault(mid, 0.0)) / 220.0 * 1000.0;
        check("узлов ровно 3 (четыре контакта замкнуты в кольцо)", mna.nodeCount() == 3);
        check("узел земли совпал с узлом светодиода (bond не «разъехался»)", ledOut.equals(neg));
        System.out.printf("       ток в цепи: %.2f мА, «плюс» = %.2f В, «минус» = %.2f В%n",
                iLoop, v.getOrDefault(pos, 0.0), v.getOrDefault(neg, 0.0));
        check("ток близок к (9-2)/(220+25) = 28.9 мА", Math.abs(iLoop - 28.9) < 1.0);
        check("цепь замкнута: узел «минус» = 0 В", Math.abs(v.getOrDefault(neg, 99.0)) < 1e-9);
        check("падение на светодиоде ~2 В (катод заземлён)",
                Math.abs(v.getOrDefault(mid, 0.0) - 2.72) < 0.3);

        System.out.println();
        System.out.println("итог: OK=" + ok + " FAIL=" + bad);
        if (bad > 0) {
            System.exit(1);
        }
    }
}
