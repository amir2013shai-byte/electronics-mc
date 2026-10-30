import me.arena.electronics.deposit.Deposits;

/**
 * Офлайн-проверки поля жил: детерминированность, частота жил по конфигу,
 * пояс по Y, «вскрыл → копаешь → выбрал» и то, что без геозонда руды нет.
 * Bukkit не нужен вовсе — Deposits живёт на целых числах.
 */
public class TestDeposits {

    private static int fails;

    static void check(String what, boolean ok, String detail) {
        System.out.println((ok ? "  ok   " : "  FAIL ") + what + "  —  " + detail);
        if (!ok) {
            fails++;
        }
    }

    static Deposits mk(long seed, double chance) {
        Deposits d = new Deposits();
        d.configure(seed, true, chance, 14, 26, -48, 40, 1);
        return d;
    }

    public static void main(String[] args) throws Exception {
        Deposits a = mk(20261030L, 0.18);
        Deposits b = mk(20261030L, 0.18);
        Deposits c = mk(11L, 0.18);

        check("одно поле при одном сиде", Deposits.sameField(a, b, 64),
                "65×65 чанков совпали у двух экземпляров");
        check("разные сиды — разные недра", !Deposits.sameField(a, c, 64),
                "на 129×129 чанках найдено расхождение");

        int span = 100;
        int total = (2 * span + 1) * (2 * span + 1);
        int found = 0;
        java.util.Map<String, Integer> byOre = new java.util.TreeMap<>();
        for (int x = -span; x <= span; x++) {
            for (int z = -span; z <= span; z++) {
                Deposits.Vein v = a.veinOf(x, z);
                if (v != null) {
                    found++;
                    byOre.merge(v.oreName(), 1, Integer::sum);
                }
            }
        }
        double rate = (double) found / total;
        check("частота жил = config", Math.abs(rate - 0.18) < 0.02,
                String.format(java.util.Locale.US, "%.3f%% чанков с жилой (ждали 18±2%%, выборка %d)",
                        rate * 100, total));
        check("все три металла встречаются", byOre.size() == 3 && byOre.values().stream().allMatch(n -> n > 200),
                byOre.toString());

        Deposits d = mk(20261030L, 0.18);
        int cx = 0;
        int cz = 0;
        Deposits.Vein v = null;
        for (int i = 0; i < 4096 && v == null; i++) {
            v = d.veinOf(i >> 4, i & 15);
            if (v != null) {
                cx = v.chunkX();
                cz = v.chunkZ();
            } else if ((i & 15) == 15) {
                cx = (i >> 4) + 1;
            }
        }
        if (v == null) {
            check("жила вообще нашлась", false, "4096 чанков — ни одной");
            System.exit(1);
        }
        int bx = cx * 16 + 8;
        int bz = cz * 16 + 8;
        check("жила описана числами", v.size() >= 14 && v.ySpan() >= 12 && v.radius() >= 2,
                "размер " + v.size() + ", Y " + v.yMin() + "…" + (v.yMin() + v.ySpan())
                        + ", ±" + v.radius() + ", " + v.oreName());

        String msg = d.prospect(bx, v.yMin() + 2, bz);
        check("геозонд вскрывает жилу", msg.startsWith("⛏"), msg);
        check("без вскрытия руды не было", !d.productive(v, v.yMin() + 2) || d.isRevealed(v),
                "сразу после prospect — продуктивна");

        Deposits dry = mk(20261030L, 0.18);
        check("пока жила закрыта, копка пуста", dry.takeOre(bx, v.yMin() + 2, bz) == null,
                "no reveal → null");
        check("не тот пояс по Y — пусто", dry.prospect(bx, 90, bz).contains("глубине")
                || dry.prospect(bx, 90, bz).contains("не найдена"), dry.prospect(bx, 90, bz));

        Deposits e = mk(20261030L, 0.18);
        e.prospect(bx, v.yMin() + 2, bz);
        int got = 0;
        String id = null;
        for (int i = 0; i < v.size(); i++) {
            id = e.takeOre(bx, v.yMin() + 2, bz);
            if (id != null) {
                got++;
            }
        }
        check("выборка = размер жилы", got == v.size(), got + " кусков из " + v.size() + " попыток");
        check(" id куска совпадает с металлом", v.oreId().equals(id), id);
        check("жила выработана", e.depleted(v) && e.takeOre(bx, v.yMin() + 2, bz) == null,
                "mined=" + e.minedOf(v) + ", после этого null");
        check("в отчёте видно выработку", e.report(bx, v.yMin() + 2, bz).contains("из " + v.size()),
                e.report(bx, v.yMin() + 2, bz));

        java.nio.file.Path tmp = java.nio.file.Files.createTempDirectory("deposits");
        java.nio.file.Path file = tmp.resolve("deposits.json");
        e.save(file);
        Deposits r = mk(20261030L, 0.18);
        r.load(file);
        check("сохранено/прочитано: вскрытие и счётчик", r.minedOf(v) == v.size() && r.depleted(v),
                new String(java.nio.file.Files.readAllBytes(file),
                        java.nio.charset.StandardCharsets.UTF_8).lines()
                        .filter(l -> !l.startsWith("#")).findFirst().orElse("пусто"));

        Deposits off = new Deposits();
        off.configure(1L, false, 0.9, 10, 0, -40, 40, 1);
        check("config выключает руды целиком", off.veinOf(0, 0) == null, "enabled=false → жив нет");

        System.out.println(fails == 0 ? "\nTestDeposits: ALL OK" : "\nTestDeposits: ПОЗОР, провалов " + fails);
        System.exit(fails == 0 ? 0 : 1);
    }
}
