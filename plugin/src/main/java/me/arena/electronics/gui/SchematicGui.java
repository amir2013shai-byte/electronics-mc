package me.arena.electronics.gui;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.circuit.Circuit;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.circuit.CompStates;
import me.arena.electronics.circuit.Simulation;
import me.arena.electronics.lab.BuildLoader;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.registry.Orientation;
import me.arena.electronics.util.Num;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Редактор схемы в GUI: «Пустая схема» — это не подставка под блоки, а лист,
 * на котором цепь рисуется и тут же считается (U/I/P каждой детали видно в
 * подсказке предмета). Физика — тот же {@link Simulation}, только на
 * «бумажных» координатах: соседство по сетке = тот же узел-пара, провод =
 * тот же hub-перекрёсток.
 *
 * <p>Нарисованное можно разомкнуть в мир одной кнопкой (там детали уже
 * ставятся реальными блоками, в том числе на вертикальную поверхность) или
 * сохранить как сборку {@code /elec build <имя>}.</p>
 */
public final class SchematicGui implements Gui {

    private static final Map<UUID, SchematicGui> SESSIONS = new LinkedHashMap<>();

    private static final int COLS = 9;
    private static final int ROWS = 3;
    private static final int CELLS = COLS * ROWS;
    private static final int CANVAS = 18;          // слоты 18..44
    private static final int PALETTE = 0;          // 0..15 — кисть
    private static final int PER_PAGE = 16;

    private final ElectronicsPlugin plugin;
    private final Inventory inv;
    private final String[] defId = new String[CELLS];
    private final int[] orient = new int[CELLS];
    private final boolean[] closed = new boolean[CELLS];
    /** Прошивка микроконтроллера в клетке (Kind.MCU) — текст, как его набрал игрок. */
    private final String[] program = new String[CELLS];
    private final double[] solvedVoltage = new double[CELLS];
    private final double[] solvedPotential = new double[CELLS];
    private final double[] solvedCurrent = new double[CELLS];
    private final String[] solvedNote = new String[CELLS];
    private List<CompInstance> last = List.of();
    private Map<String, CompInstance> lastByPos = new HashMap<>();
    private final int[] cellOf = new int[CELLS];
    private int brush = 0;
    private int page = 0;
    private int islands = -1;
    private String hint = "";

    private SchematicGui(ElectronicsPlugin plugin) {
        this.plugin = plugin;
        this.inv = Bukkit.createInventory(this, 6 * 9,
                Items.color("&a⚡ Схема — редактор &7(сетка 9×3, ЛКМ ставить, ПКМ повернуть)"));
        redraw();
    }

    /** Одна сессия на игрока: закрыл — открыл снова, рисунок цел. */
    public static SchematicGui forPlayer(ElectronicsPlugin plugin, Player p) {
        return forId(plugin, p.getUniqueId());
    }

    /** Тот же лист по UUID (нужно отладке/самопроверке без игрока). */
    public static SchematicGui forId(ElectronicsPlugin plugin, UUID id) {
        return SESSIONS.computeIfAbsent(id, k -> new SchematicGui(plugin));
    }

    public static int cellIndex(int row, int col) {
        return row * COLS + col;
    }

    /** Положить деталь в клетку (для самопроверки и скриптов); null — стереть. */
    public void put(int row, int col, String defId, int orientation) {
        int cell = cellIndex(row, col);
        this.defId[cell] = defId;
        this.orient[cell] = orientation;
        this.program[cell] = null;      // деталь сменилась — её прошивка не остаётся
        islands = -1;
    }

    /**
     * Прошивка для микроконтроллера в клетке. Живёт рядом с клеткой (а не на
     * экземпляре), потому что решатель пересобирает экземпляры на каждом прогоне:
     * иначе тест и редактор теряли бы код после первого шага.
     */
    public void programAt(int row, int col, String text) {
        program[cellIndex(row, col)] = text;
        islands = -1;
    }

    public String programTextAt(int row, int col) {
        return program[cellIndex(row, col)];
    }

    public double voltageAt(int row, int col) {
        return solvedVoltage[cellIndex(row, col)];
    }

    public double currentAt(int row, int col) {
        return solvedCurrent[cellIndex(row, col)];
    }

    public String noteAt(int row, int col) {
        return solvedNote[cellIndex(row, col)];
    }

    public int islands() {
        return islands;
    }

    /** Последний решённый экземпляр клетки (диагностика/тесты). */
    public CompInstance instanceAt(int row, int col) {
        int idx = cellOf[cellIndex(row, col)];
        return idx >= 0 && idx < last.size() ? last.get(idx) : null;
    }

    /** Имена узлов «как их видит решатель» — одна строка на клетку. */
    public String netsAt(int row, int col) {
        CompInstance c = instanceAt(row, col);
        if (c == null) {
            return "—";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            if (c.pinNets[i] == null) {
                continue;
            }
            sb.append(sb.length() == 0 ? "" : " ").append(Simulation.netShort(c.pinNets[i]));
        }
        return sb.toString();
    }

    /** Пересчитать молча (самопроверка/скрипты). */
    public void recalcQuiet() {
        recalc(null);
    }

    public static void clearSessions() {
        SESSIONS.clear();
    }

    public static void open(ElectronicsPlugin plugin, Player p) {
        p.openInventory(forPlayer(plugin, p).inv);
    }

    // ------------------------------------------------------------------ отрисовка

    private void redraw() {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, null);
        }
        List<Def> all = Components.all();
        int pages = Math.max(1, (all.size() + PER_PAGE - 1) / PER_PAGE);
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= all.size()) {
                break;
            }
            Def d = all.get(idx);
            ItemStack card = plugin.items().componentItem(d, 1);
            if (idx == brush) {
                ItemMeta meta = card.getItemMeta();
                if (meta != null) {
                    meta.setEnchantmentGlintOverride(true);
                    meta.setDisplayName(Items.color("&a▶ &f" + d.nameRu));
                    card.setItemMeta(meta);
                }
            }
            inv.setItem(PALETTE + i, card);
        }
        inv.setItem(PALETTE + 16, Gui.button(Material.ARROW, "&e◀ страницы кисти"));
        inv.setItem(PALETTE + 17, Gui.button(Material.ARROW, "&e страницы кисти ▶"));

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                inv.setItem(CANVAS + r * COLS + c, cellItem(r * COLS + c));
            }
        }

        int a = 5 * 9;
        inv.setItem(a, Gui.button(Material.BOOK, "&aПересчитать схему",
                "&7Решает цепь методом узловых потенциалов",
                "&7и вписывает U/I/P в подсказки деталей.",
                "&8В редакторе конденсатор всегда стартует с нуля —",
                "&8это «момент подачи питания». Для переходного",
                "&8процесса нажми «Симулировать 20 тиков»."));
        inv.setItem(a + 1, Gui.button(Material.GRASS_BLOCK, "&aПоставить в мире",
                "&7Строит нарисованное блоками там, куда",
                "&7смотришь (нужно право electronics.build)."));
        inv.setItem(a + 2, Gui.button(Material.CHEST, "&eСохранить сборкой",
                "&7В файл plugins/Electronics/builds/schem_<ник>.json,",
                "&7потом /elec build schem_<ник>."));
        inv.setItem(a + 3, Gui.button(Material.BARRIER, "&cОчистить лист"));
        inv.setItem(a + 4, Gui.button(Material.BRUSH, "&eЛастик",
                "&7Выбрать ластик: ЛКМ по клетке стирает её.",
                "&7Shift+ПКМ — стереть всегда, ПКМ — повернуть.",
                "&7Shift+ЛКМ на ключе — замкнуть/разомкнуть."));
        inv.setItem(a + 6, Gui.button(Material.REPEATER, "&aСимулировать 20 тиков",
                "&7Прогоняет цепь на 20 шагов по 50 мс, не сбрасывая",
                "&7заряды конденсаторов и ток катушек: видно, как",
                "&7разгоняется дроссель и заряжается конденсатор."));
        inv.setItem(a + 5, Gui.button(Material.OAK_SIGN, "&eКак это работает",
                "&7Узел — пара соседних клеток, как в мире:",
                "&7стоящие вплотную детали соединены, провод",
                "&7— перекрёсток (объединяет всё вокруг).",
                "&7Нужен источник и земля, иначе «нет земли»."));
        inv.setItem(a + 7, Gui.button(Material.PAPER, "&7" + filled() + " &8из " + CELLS + " клеток"
                + (islands < 0 ? " · не считано" : " · островков: " + islands)
                + (hint.isEmpty() ? "" : " · " + hint)));
    }

    private ItemStack cellItem(int cell) {
        String id = defId[cell];
        if (id == null) {
            return Gui.button(Material.LIGHT_BLUE_STAINED_GLASS_PANE, "&8" + slotName(cell),
                    "&7пусто — выбери деталь сверху и ЛКМ");
        }
        Def d = Components.byId(id);
        if (d == null) {
            return Gui.button(Material.BEDROCK, "&c" + id);
        }
        ItemStack stack = plugin.items().componentItem(d, 1);
        List<String> lore = new ArrayList<>();
        String nominal = d.nominal();
        if (!nominal.isEmpty()) {
            lore.add(Items.color("&7" + nominal));
        }
        lore.add(Items.color("&7ориентация: &f" + Orientation.byIndex(orient[cell]).name()
                + (closed[cell] ? " &a(замкнуто)" : "")));
        if (solvedNote[cell] != null) {
            if (d.hub) {
                lore.add(Items.color("&7потенциал узла: &f" + Num.volt(solvedPotential[cell])
                        + (solvedPotential[cell] < 0.05 ? " &8(земля)" : "")));
            }
            lore.add(Items.color("&e" + solvedNote[cell]));
            lore.add(Items.color(String.format("&7U = &f%s &7| I = &f%s &7| P = &f%s",
                    Num.volt(solvedVoltage[cell]), Num.amp(solvedCurrent[cell]),
                    Num.watt(Math.abs(solvedVoltage[cell] * solvedCurrent[cell])))));
        } else {
            lore.add(Items.color("&8нажми «Пересчитать» — тут появятся U и I"));
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            // подсветка имени по потенциалу: сразу видно, где «плюс», где земля
            double v = d.hub ? solvedPotential[cell] : solvedVoltage[cell];
            String color = islands < 0 ? "&f" : bucket(v);
            meta.setDisplayName(Items.color(color + "⚡ " + d.nameRu + " &7" + slotName(cell)));
            stack.setItemMeta(meta);
        }
        return stack;
    }

    private static String bucket(double v) {
        double a = Math.abs(v);
        if (a < 0.05) {
            return "&8";
        }
        if (v > 0) {
            return a > 8 ? "&c" : (a > 2 ? "&e" : "&a");
        }
        return a > 8 ? "&9" : (a > 2 ? "&3" : "&b");
    }

    private static String slotName(int cell) {
        return "[" + (cell % COLS + 1) + "," + (cell / COLS + 1) + "]";
    }

    private int filled() {
        int n = 0;
        for (String s : defId) {
            if (s != null) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ расчёт

    /** Собирает из сетки компоненты и гоняет их через «взрослый» решатель. */
    /** Визуальное состояние детали в клетке (0 off … 3 burnt … 5 closed), −1 если клетки нет. */
    public int stateAt(int row, int col) {
        CompInstance ci = instanceAt(row, col);
        return ci == null ? -1 : ci.state;
    }

    private List<CompInstance> buildGrid() {
        lastByPos = new HashMap<>();
        List<CompInstance> list = new ArrayList<>();
        for (int cell = 0; cell < CELLS; cell++) {
            cellOf[cell] = -1;
            if (defId[cell] == null) {
                continue;
            }
            Def d = Components.byId(defId[cell]);
            if (d == null) {
                continue;
            }
            int c = cell % COLS, r = cell / COLS;
            CompInstance inst = new CompInstance(new LocationKey((World) null, c, 0, r), d);
            inst.orientation = Orientation.byIndex(orient[cell]);
            inst.state = closed[cell] ? CompStates.CLOSED : CompStates.OFF;
            if (program[cell] != null) {
                inst.program = program[cell];
            }
            list.add(inst);
            lastByPos.put(Circuit.pos(c, 0, r), inst);
            cellOf[cell] = list.size() - 1;
        }
        last = list;
        return list;
    }

    /** Один прогон решателя на текущей сетке. */
    private int solveOnce(List<CompInstance> list) {
        return Simulation.solveVirtual(plugin, lastByPos, list);
    }

    private void annotate(List<CompInstance> list) {
        for (int cell = 0; cell < CELLS; cell++) {
            solvedNote[cell] = null;
            if (cellOf[cell] < 0 || cellOf[cell] >= list.size()) {
                continue;
            }
            CompInstance inst = list.get(cellOf[cell]);
            solvedVoltage[cell] = inst.voltage;
            solvedPotential[cell] = inst.def.hub
                    ? Math.max(Math.abs(inst.pinVoltage[0]), Math.abs(inst.voltage))
                    : Math.abs(inst.voltage);
            solvedCurrent[cell] = inst.current;
            String text = Simulation.describe(inst);
            solvedNote[cell] = text == null || text.isEmpty()
                    ? (inst.burnt() ? "сгорел: " + inst.fault : "посчитано") : text;
        }
        hint = "";
        double maxAbs = 0;
        for (CompInstance inst : list) {
            maxAbs = Math.max(maxAbs, Math.abs(inst.current));
        }
        boolean noSource = list.stream().noneMatch(i -> i.def.kind == Kind.SOURCE
                || i.def.kind == Kind.GENERATOR || i.def.kind == Kind.SOLAR);
        if (noSource) {
            hint = "нет источника питания";
        } else if (maxAbs < 1e-9) {
            hint = "ток нулевой — цепь разомкнута (клетки должны касаться друг друга,"
                    + " угол делается проводом, нужна и земля)";
        }
        for (CompInstance inst : list) {
            if (!inst.fault.isEmpty()) {
                hint = inst.def.nameRu + ": " + inst.fault;
                break;
            }
        }
    }

    private void recalc(org.bukkit.command.CommandSender who) {
        List<CompInstance> list = buildGrid();
        islands = solveOnce(list);
        annotate(list);
        redraw();
        if (who != null) {
            who.sendMessage(Items.color("&aСхема пересчитана: &f" + list.size()
                    + " &7деталей, островков &f" + islands
                    + (hint.isEmpty() ? "" : " &e⚠ " + hint)));
        }
    }

    /**
     * Прогон нескольких шагов по времени на одних и тех же экземплярах:
     * переходный процесс конденсаторов и катушек (заряд/разряд, разгон тока
     * в дросселе). Кнопка «Симулировать 20 тиков» и /elec selftest.
     */
    public void recalcSteps(org.bukkit.command.CommandSender who, int steps) {
        List<CompInstance> list = buildGrid();
        islands = solveOnce(list);
        for (int i = 0; i < Math.max(1, steps); i++) {
            islands = solveOnce(list);
        }
        annotate(list);
        redraw();
        if (who != null) {
            who.sendMessage(Items.color("&aПрогон &f" + Math.max(1, steps)
                    + " &7шагов по &f" + (int) (plugin.config().dt() * 1000)
                    + " мс&7; ток/напряжение — в подсказках деталей."));
        }
    }

    /** Сколько шагов показал последний прогон (для самопроверки). */
    public double currentAbsMax() {
        double m = 0;
        for (CompInstance inst : last) {
            m = Math.max(m, Math.abs(inst.current));
        }
        return m;
    }
    /** Компоненты того же листа, но для раскладки в мире/файле. */
    private BuildLoader.Build toBuild(String name, String title) {
        BuildLoader.Build b = plugin.builds().newBuild(name, title);
        for (int cell = 0; cell < CELLS; cell++) {
            if (defId[cell] == null) {
                continue;
            }
            int c = cell % COLS, r = cell / COLS;
            b.blocks.add(BuildLoader.entry("bench", c, 0, r, 0, 1.0, true));
            b.components.add(BuildLoader.entry(defId[cell], c, 1, r, orient[cell],
                    closed[cell] ? 2000.0 : 1.0, false));
        }
        return b;
    }

    private void placeInWorld(Player who) {
        BuildLoader.Build b = toBuild("schematic", "Схема из редактора");
        if (b.components.isEmpty()) {
            who.sendMessage(Items.color("&cЛист пустой."));
            return;
        }
        if (!who.hasPermission("electronics.build")) {
            who.sendMessage(Items.color("&cНет права electronics.build."));
            return;
        }
        Block target = who.getTargetBlockExact(8);
        Block at = target == null ? who.getLocation().getBlock().getRelative(BlockFace.UP)
                : target.getRelative(BlockFace.UP);
        BuildLoader.Result r = plugin.builds().place(who, b, at);
        if (!r.error.isEmpty()) {
            who.sendMessage(Items.color("&c" + r.error));
        } else {
            who.sendMessage(Items.color("&aПоставлено в мире: &f" + r.placed + " &7деталей, подставок &f"
                    + r.hosts + (r.skipped > 0 ? " &8(пропущено " + r.skipped + ")" : "")));
        }
    }

    private void saveBuild(Player who) {
        BuildLoader.Build b = toBuild("schem_" + who.getName(), "Схема игрока " + who.getName());
        if (b.components.isEmpty()) {
            who.sendMessage(Items.color("&cЛист пустой."));
            return;
        }
        StringBuilder sb = new StringBuilder();
        sb.append("{\n  \"name\": \"").append(b.title).append("\",\n  \"origin\": [0, 0, 0],\n");
        sb.append("  \"blocks\": [");
        boolean first = true;
        for (BuildLoader.Entry e : b.blocks) {
            sb.append(first ? "\n" : ",\n").append("    {\"d\":\"bench\",\"p\":[")
                    .append(e.dx).append(',').append(e.dy).append(',').append(e.dz)
                    .append("],\"host\":true}");
            first = false;
        }
        sb.append(first ? "],\n" : "\n  ],\n  \"components\": [");
        first = true;
        for (BuildLoader.Entry e : b.components) {
            sb.append(first ? "\n" : ",\n").append("    {\"d\":\"").append(e.defId).append("\",\"p\":[")
                    .append(e.dx).append(',').append(e.dy).append(',').append(e.dz)
                    .append("],\"o\":").append(e.orientation).append("}");
            first = false;
        }
        sb.append(first ? "]\n}\n" : "\n  ]\n}\n");
        File dir = new File(plugin.getDataFolder(), "builds");
        dir.mkdirs();
        File f = new File(dir, "schem_" + who.getName() + ".json");
        try {
            Files.write(f.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
            who.sendMessage(Items.color("&aСохранено: &f" + f.getName()
                    + " &7→ /elec build schem_" + who.getName()));
        } catch (java.io.IOException ex) {
            who.sendMessage(Items.color("&cНе записалось: " + ex.getMessage()));
        }
    }

    // ------------------------------------------------------------------ клики

    @Override
    public Inventory inventory() {
        return inv;
    }

    @Override
    public void onClick(Player who, InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= inv.getSize()) {
            e.setCancelled(false);          // свой инвентарь — трогаем свободно
            return;
        }
        int a = 5 * 9;
        if (raw == a) {
            recalc(who);
            return;
        }
        if (raw == a + 1) {
            placeInWorld(who);
            return;
        }
        if (raw == a + 2) {
            saveBuild(who);
            return;
        }
        if (raw == a + 3) {
            for (int i = 0; i < CELLS; i++) {
                defId[i] = null;
                orient[i] = 1;
                closed[i] = false;
                solvedNote[i] = null;
            }
            islands = -1;
            hint = "";
            redraw();
            return;
        }
        if (raw == a + 4) {
            brush = -1;                       // ластик
            redraw();
            who.sendMessage(Items.color("&eЛастик выбран &7(ЛКМ по клетке стирает)"));
            return;
        }
        if (raw == a + 6) {
            recalcSteps(who, 20);
            return;
        }
        if (raw == a + 5) {
            who.sendMessage(Items.color("&a⚡ Редактор схемы: &7узел — пара соседних клеток, "
                    + "провод объединяет всё вокруг, поэтому угол делается проводом, а не «жилой» клеткой."));
            who.sendMessage(Items.color("&7Нужны источник и &fground_bus&7, иначе потенциалы "
                    + "считаются от минуса источника."));
            return;
        }
        if (raw == PALETTE + 16 || raw == PALETTE + 17) {
            int pages = Math.max(1, (Components.size() + PER_PAGE - 1) / PER_PAGE);
            page = Math.floorMod(page + (raw == PALETTE + 16 ? -1 : 1), pages);
            redraw();
            return;
        }
        if (raw >= PALETTE && raw < PALETTE + PER_PAGE) {
            int idx = page * PER_PAGE + (raw - PALETTE);
            if (idx < Components.size()) {
                brush = idx;
                redraw();
                who.sendMessage(Items.color("&aКисть: &f" + Components.byIndex(idx).nameRu));
            }
            return;
        }
        if (raw >= CANVAS && raw < CANVAS + CELLS) {
            int cell = raw - CANVAS;
            if (e.isShiftClick() && e.isRightClick()) {
                defId[cell] = null;
                orient[cell] = 1;
                closed[cell] = false;
                solvedNote[cell] = null;
            } else if (e.isRightClick()) {
                if (defId[cell] != null) {
                    if (e.isShiftClick()) {
                        closed[cell] = !closed[cell];
                    } else {
                        orient[cell] = nextInPlane(orient[cell]);
                    }
                }
            } else if (brush >= 0) {
                Def d = Components.byIndex(brush);
                if (d != null) {
                    defId[cell] = d.id;
                    orient[cell] = d.orientable ? 1 : 0;
                    closed[cell] = false;
                    solvedNote[cell] = null;
                }
            } else {
                defId[cell] = null;
                solvedNote[cell] = null;
            }
            islands = -1;            // правки делают прежний расчёт недействительным
            redraw();
        }
    }

    /** Поворот только «в плоскости листа»: восток → юг → запад → север. */
    private static int nextInPlane(int o) {
        return switch (o) {
            case 1 -> 2;
            case 2 -> 3;
            case 3 -> 0;
            default -> 1;
        };
    }
}
