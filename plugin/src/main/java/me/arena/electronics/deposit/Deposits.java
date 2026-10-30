package me.arena.electronics.deposit;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Закрытые жилы.
 *
 * <p>Worldgen не может донести наши данные до блока: ванильная руда — это ванильный
 * лут, а свою текстуру на чужой блок мы не вешаем (клиентских модов не делаем).
 * Поэтому жила живёт у плагина и описывается <em>функцией</em>, а не блоками:
 * из координат чанка и сида мира детерминированно следует, есть ли здесь жила,
 * какая, какого размера и на какой глубине. Геозонд её «вскрывает» — и только
 * после этого копка внутри границ жилы начинает отдавать куски руды. У жилы есть
 * запас: выбранное число блоков её вырабатывает, и геозонд больше ничего не находит.
 *
 * <p>Класс намеренно не знает про Bukkit: вся арифметика проверяется офлайн
 * ({@code tools/TestDeposits.java}), а {@code Events} только читает блоки и выдаёт
 * предметы. Формат файла — прямой JSON-подобный список, без внешних библиотек.
 */
public final class Deposits {

    /** Что добывают из жилы и как её называют в сообщениях. */
    public static final class Ore {
        private final String chunkId;
        private final String name;

        Ore(String chunkId, String name) {
            this.chunkId = chunkId;
            this.name = name;
        }

        public String chunkId() {
            return chunkId;
        }

        public String name() {
            return name;
        }
    }

    public static final Ore[] ORES = {
            new Ore("raw_copper_chunk", "медь"),
            new Ore("raw_tin_chunk", "олово"),
            new Ore("raw_zinc_chunk", "цинк"),
    };

    /**
     * Жила: центр чанка, номер руды, размер в блоках и доступная глубина.
     * Обычный класс, не record: офлайн-тесты должны собираться любым javac,
     * каким собрался бы плагин на голой системе (в песочнице JDK 21 живёт
     * ровно до следующей команды apt).
     */
    public static final class Vein {
        private final int chunkX;
        private final int chunkZ;
        private final int ore;
        private final int size;
        private final int yMin;
        private final int ySpan;
        private final int radius;

        Vein(int chunkX, int chunkZ, int ore, int size, int yMin, int ySpan, int radius) {
            this.chunkX = chunkX;
            this.chunkZ = chunkZ;
            this.ore = ore;
            this.size = size;
            this.yMin = yMin;
            this.ySpan = ySpan;
            this.radius = radius;
        }

        public int chunkX() {
            return chunkX;
        }

        public int chunkZ() {
            return chunkZ;
        }

        public int ore() {
            return ore;
        }

        public int size() {
            return size;
        }

        public int yMin() {
            return yMin;
        }

        public int ySpan() {
            return ySpan;
        }

        public int radius() {
            return radius;
        }

        public long key() {
            return Deposits.key(chunkX, chunkZ);
        }

        public String oreId() {
            return Deposits.ORES[Math.floorMod(ore, Deposits.ORES.length)].chunkId();
        }

        public String oreName() {
            return Deposits.ORES[Math.floorMod(ore, Deposits.ORES.length)].name();
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Vein)) {
                return false;
            }
            Vein v = (Vein) o;
            return chunkX == v.chunkX && chunkZ == v.chunkZ && ore == v.ore && size == v.size
                    && yMin == v.yMin && ySpan == v.ySpan && radius == v.radius;
        }

        @Override
        public int hashCode() {
            long h = key() * 31 + size;
            return (int) (h ^ (h >>> 32));
        }

        @Override
        public String toString() {
            return "Vein[" + oreName() + " @" + chunkX + "," + chunkZ + " size=" + size
                    + " Y=" + yMin + ".." + (yMin + ySpan) + " r=" + radius + "]";
        }
    }

    public static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    private long seed;
    private double chunkChance = 0.18;
    private int baseSize = 14;
    private int sizeJitter = 26;
    private int yMinBase = -48;
    private int yBand = 40;
    private int yieldPerBlock = 1;
    private boolean enabled = true;

    /** Вскрытые жилы и сколько блоков из каждой уже выбрано. */
    private final Set<Long> revealed = new HashSet<>();
    private final Map<Long, Integer> mined = new LinkedHashMap<>();

    public void configure(long seed, boolean enabled, double chunkChance, int baseSize, int sizeJitter,
                          int yMinBase, int yBand, int yieldPerBlock) {
        this.seed = seed;
        this.enabled = enabled;
        this.chunkChance = Math.max(0, Math.min(1, chunkChance));
        this.baseSize = Math.max(4, baseSize);
        this.sizeJitter = Math.max(0, sizeJitter);
        this.yMinBase = yMinBase;
        this.yBand = Math.max(8, yBand);
        this.yieldPerBlock = Math.max(1, Math.min(8, yieldPerBlock));
    }

    public boolean enabled() {
        return enabled;
    }

    public double chunkChance() {
        return chunkChance;
    }

    public int yieldPerBlock() {
        return yieldPerBlock;
    }

    /** Смешивание splitmix64: дешёвое, стабильное между запусками и языками. */
    public static long mix(long x) {
        long z = x + 0x9E3779B97F4A7C15L;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /**
     * Жила чанка или null, если чанк пустой. Всё выводится из хеша, поэтому ответ
     * не зависит от порядка запросов, от перезапуска сервера и от того, кто именно
     * стоит на поверхности.
     */
    public Vein veinAt(int blockX, int blockZ) {
        return veinOf(blockX >> 4, blockZ >> 4);
    }

    public Vein veinOf(int chunkX, int chunkZ) {
        if (!enabled) {
            return null;
        }
        long h = mix((((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL) ^ seed);
        if ((int) Math.floorMod(h, 1000) >= (int) Math.round(chunkChance * 1000)) {
            return null;
        }
        int ore = (int) Math.floorMod(h >>> 10, ORES.length);
        int size = baseSize + (int) Math.floorMod(h >>> 15, sizeJitter + 1);
        int radius = 2 + (int) Math.floorMod(h >>> 24, 3);
        // Пояс — это обещание из config.yml («от y-min на y-band блоков вверх»),
        // поэтому и низ, и верх жилы держим внутри: иначе в -48..-8 вдруг
        // выразает руду на +4, и игрок перестаёт понимать, где искать.
        int band = Math.max(13, yBand);
        int y0 = yMinBase + (int) Math.floorMod(h >>> 33, Math.max(1, band - 12));
        int span = Math.min(12 + (int) Math.floorMod(h >>> 40, 14), yMinBase + band - y0);
        return new Vein(chunkX, chunkZ, ore, size, y0, Math.max(6, span), radius);
    }

    /** Лежит ли точка внутри границ жилы (по горизонтали — квадрат радиуса, по Y — пояс). */
    public boolean inside(Vein v, int blockY) {
        return v != null && blockY >= v.yMin() && blockY < v.yMin() + v.ySpan();
    }

    public boolean isRevealed(Vein v) {
        return v != null && revealed.contains(v.key());
    }

    public int minedOf(Vein v) {
        return v == null ? 0 : mined.getOrDefault(v.key(), 0);
    }

    public boolean depleted(Vein v) {
        return v != null && minedOf(v) >= v.size();
    }

    /** Работает ли жила: вскрыта и ещё не выбрана. Только тогда копка даёт руду. */
    public boolean productive(Vein v, int blockY) {
        return inside(v, blockY) && isRevealed(v) && !depleted(v);
    }

    /**
     * Вскрыть жилу под точкой. Возвращает текст для игрока; если жилы нет —
     * сообщение «пусто», чтобы игрок понимал разницу между «нет живы» и «выбрана».
     */
    public String prospect(int blockX, int blockY, int blockZ) {
        Vein v = veinAt(blockX, blockZ);
        if (v == null) {
            return "жила не найдена: порода чистая, ищи ниже или в стороне";
        }
        if (!inside(v, blockY)) {
            return "на этой глубине жилы нет: пояс " + v.yMin() + "…" + (v.yMin() + v.ySpan())
                    + " " + v.oreName();
        }
        if (depleted(v)) {
            return "жила выработана: " + minedOf(v) + "/" + v.size() + " блоков " + v.oreName();
        }
        boolean fresh = revealed.add(v.key());
        return (fresh ? "⛏ жила вскрыта: " : "жила уже была вскрыта, прогресс: ")
                + describe(v, blockX, blockZ);
    }

    /** Что уже известно о жиле под ногами — для повторного запроса и для /elec veins. */
    public String report(int blockX, int blockY, int blockZ) {
        Vein v = veinAt(blockX, blockZ);
        if (v == null) {
            return "жила не найдена";
        }
        if (!isRevealed(v)) {
            return "жила закрыта: нужен геозонд (" + v.oreName() + "?)";
        }
        return describe(v, blockX, blockZ);
    }

    private String describe(Vein v, int blockX, int blockZ) {
        int cx = v.chunkX() * 16 + 8;
        int cz = v.chunkZ() * 16 + 8;
        int dx = cx - blockX;
        int dz = cz - blockZ;
        double dist = Math.sqrt((double) dx * dx + (double) dz * dz);
        double az = Math.toDegrees(Math.atan2(dx, -dz));
        if (az < 0) {
            az += 360;
        }
        return v.oreName() + ", " + (v.size() - minedOf(v)) + " из " + v.size() + " блоков, пояс Y "
                + v.yMin() + "…" + (v.yMin() + v.ySpan()) + ", квадрат ±" + v.radius()
                + ", центр " + (int) Math.round(az) + "° за " + String.format(Locale.US, "%.0f", dist) + " бл.";
    }

    /**
     * Один выбитый блок внутри жилы. Возвращает id куска руды, который надо выдать,
     * или null, если блок руды не даёт (не та жила, не тот пояс, жила выбрана).
     */
    public String takeOre(int blockX, int blockY, int blockZ) {
        Vein v = veinAt(blockX, blockZ);
        if (!productive(v, blockY)) {
            return null;
        }
        mined.merge(v.key(), 1, Integer::sum);
        return v.oreId();
    }

    /** Сколько руды сыпать с одного блока (медь щедрее цинка — так в паспорте). */
    public int chunksPerBlock(Vein v, int rand) {
        int base = yieldPerBlock;
        if (rand < 0) {
            return base;
        }
        return base + (Math.floorMod(rand, 3) == 0 ? 1 : 0);
    }

    /** Сводка по чанкам вокруг точки — для {@code /elec veins} и для самопроверки. */
    public String survey(int blockX, int blockZ, int radiusChunks) {
        int found = 0;
        int open = 0;
        int dry = 0;
        List<String> names = new ArrayList<>();
        for (int dx = -radiusChunks; dx <= radiusChunks; dx++) {
            for (int dz = -radiusChunks; dz <= radiusChunks; dz++) {
                Vein v = veinOf((blockX >> 4) + dx, (blockZ >> 4) + dz);
                if (v == null) {
                    dry++;
                    continue;
                }
                found++;
                if (isRevealed(v)) {
                    open++;
                }
                if (names.size() < 4) {
                    names.add(v.oreName() + "@" + v.chunkX() + "," + v.chunkZ());
                }
            }
        }
        return "чанков " + ((2 * radiusChunks + 1) * (2 * radiusChunks + 1)) + ", жив " + found
                + " (вскрыто " + open + "), пустых " + dry + (names.isEmpty() ? "" : " | " + names);
    }

    /**
     * Детерминированность поля: два независимых экземпляра с одинаковым сидом
     * обязаны отвечать одинаково на любом наборе координат (иначе «жила» зависит
     * от истории, а не от мира — и игроки будут спорить, кто её нашёл первым).
     */
    public static boolean sameField(Deposits a, Deposits b, int span) {
        for (int x = -span; x <= span; x++) {
            for (int z = -span; z <= span; z++) {
                Vein va = a.veinOf(x, z);
                Vein vb = b.veinOf(x, z);
                if (va == null ? vb != null : !va.equals(vb)) {
                    return false;
                }
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ файл

    public void load(Path file) {
        revealed.clear();
        mined.clear();
        if (file == null || !Files.isRegularFile(file)) {
            return;
        }
        try {
            // Не Files.readAllLines/writeString: в ранних сборках JDK 11 writeString
            // игнорировал переданный charset и писал UTF-16 (проверено на деле —
            // файл получался с NUL между байтами). Граница формата важнее удобства.
            for (String line : new String(Files.readAllBytes(file), StandardCharsets.UTF_8)
                    .split("\n")) {
                String t = line.trim();
                if (t.startsWith("R ")) {
                    String[] q = t.substring(2).trim().split("\\s+");
                    if (q.length >= 2) {
                        revealed.add(key(Integer.parseInt(q[0]), Integer.parseInt(q[1])));
                    }
                } else if (t.startsWith("M ")) {
                    String[] q = t.substring(2).trim().split("\\s+");
                    if (q.length >= 3) {
                        mined.put(key(Integer.parseInt(q[0]), Integer.parseInt(q[1])),
                                Integer.parseInt(q[2]));
                        revealed.add(key(Integer.parseInt(q[0]), Integer.parseInt(q[1])));
                    }
                }
            }
        } catch (IOException | NumberFormatException ignored) {
            // битая строка не должна ронять сервер: жилы просто пересчитаются заново
        }
    }

    public void save(Path file) throws IOException {
        if (file == null) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("# закрытые жилы: R <cx> <cz> — вскрыта, M <cx> <cz> <выблено блоков>\n");
        Set<Long> keys = new HashSet<>(revealed);
        keys.addAll(mined.keySet());
        for (Long k : keys) {
            int cx = (int) (k >> 32);
            int cz = (int) (long) k;
            Integer m = mined.get(k);
            sb.append(m != null && m > 0 ? "M " : "R ").append(cx).append(' ').append(cz);
            if (m != null && m > 0) {
                sb.append(' ').append(m);
            }
            sb.append('\n');
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.write(file, sb.toString().getBytes(StandardCharsets.UTF_8));
    }
}
