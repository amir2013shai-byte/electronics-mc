package me.arena.electronics.gui;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.fw.Firmware;
import me.arena.electronics.registry.Def;
import me.arena.electronics.registry.Kind;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * GUI USB-программатора: состояние ног микроконтроллера и текст прошивки.
 *
 * <p>Поле ввода в сундуке — миф: в ванильном GUI нельзя печатать. Поэтому
 * прошивка живёт в «книге для записи» (единственное место, где игрок пишет
 * многострочный текст без клиентских модов и без прав на консоль). Кнопка
 * «Загрузить» берёт книгу из правой руки и отдаёт её текст чипу; «Скачать»
 * выкладывает текущую прошивку в новую книгу, чтобы код не потерялся.</p>
 *
 * <p>Значения в плитках — настоящие, их посчитал решатель: напряжение ноги,
 * 10-битное АЦП, ток порта в миллиамперах, VCC и рассеяние. Обновление — по
 * клику (симуляция шагает 20 раз в секунду, держать открытым «живой» экран
 * смысла нет: кнопки пересчитывают всё заново).</p>
 */
public final class McuGui implements Gui {

    private static final int SIZE = 36;
    /** Дальность «программатор → чип»: реально USB-кабель короткий. */
    private static final double REACH = 4.0;
    private static final int MAX_CHARS = 6000;

    private final ElectronicsPlugin plugin;
    private final Player viewer;
    private final Inventory inv;
    private final LocationKey at;

    private McuGui(ElectronicsPlugin plugin, Player p, LocationKey at) {
        this.plugin = plugin;
        this.viewer = p;
        this.at = at;
        this.inv = Bukkit.createInventory(this, SIZE, Items.color("&b⚡ Программатор"));
        rebuild();
    }

    public static void open(ElectronicsPlugin plugin, Player p, Block programmer) {
        if (plugin == null || p == null || programmer == null) {
            return;
        }
        CompInstance mcu = nearestMcu(plugin, programmer);
        if (mcu == null) {
            Items.message(p, "&eРядом с программатором нет микроконтроллера&7: поставь &fATmega328"
                    + " &7в пределах 4 блоков, подключи &fVCC&7 и &fGND&7, потом держи"
                    + " книгу в руке и жми &aЗагрузить&7.");
            return;
        }
        p.openInventory(new McuGui(plugin, p, mcu.key).inv);
    }

    private static CompInstance nearestMcu(ElectronicsPlugin plugin, Block from) {
        CompInstance best = null;
        double bestD = Double.MAX_VALUE;
        for (CompInstance c : plugin.circuit().all()) {
            if (c == null || c.def == null || c.def.kind != Kind.MCU) {
                continue;
            }
            Block b = c.key.block();
            if (b == null || !b.getWorld().equals(from.getWorld())) {
                continue;
            }
            double dist = Math.max(Math.abs(b.getX() - from.getX()),
                    Math.max(Math.abs(b.getY() - from.getY()), Math.abs(b.getZ() - from.getZ())));
            if (dist <= REACH && dist < bestD) {
                bestD = dist;
                best = c;
            }
        }
        return best;
    }

    private CompInstance mcu() {
        Block b = at == null ? null : at.block();
        CompInstance c = b == null ? null : plugin.circuit().get(b);
        return c != null && c.def != null && c.def.kind == Kind.MCU ? c : null;
    }

    @Override
    public Inventory inventory() {
        return inv;
    }

    // ------------------------------------------------------------------ разметка

    private void rebuild() {
        inv.clear();
        CompInstance c = mcu();
        if (c == null) {
            inv.setItem(13, Gui.button(Material.REDSTONE_BLOCK, "&cЧип не найден",
                    "&7Микроконтроллер убрали или чанк выгрузился.",
                    "&7Поставь &fATmega328&7 рядом с программатором",
                    "&7и открой его заново (ПКМ по программатору)."));
            inv.setItem(35, Gui.button(Material.OAK_DOOR, "&cЗакрыть"));
            return;
        }
        Firmware.Program p = c.firmware == null ? Firmware.compile(c.program, c.def.pins) : c.firmware;
        boolean running = p.ok() && p.statements() > 0 && c.mcuHalt.isEmpty();
        Def d = c.def;

        inv.setItem(0, Gui.button(Material.NETHER_STAR,
                "&b" + d.nameRu + " &7(" + d.id + ")",
                "&7Выводов: &f" + d.pins + "&7, логическое питание: &f"
                        + fmt(c.mcuVcc, 2) + " В&7 (цель &f" + fmt(d.voltage, 1) + " В)",
                "&7Сопротивление порта: &f" + fmt(d.resistance, 0) + " Ом&7, предел: &f"
                        + (int) (d.iLimit * 1000) + " мА/нога",
                running ? "&aпрошивка идёт&7: " + p.statements() + " операторов, "
                        + p.millis() + " мс с прошлой загрузки"
                        : "&eостановлено&7: " + (c.mcuHalt.isEmpty() ? "нет программы" : c.mcuHalt),
                "&7Рассеяние: &f" + (int) (c.aux[0] * 1000) + " мВт&7, ног в КЗ: &f"
                        + (int) c.aux[1]));

        inv.setItem(2, Gui.button(Material.BOOK, "&aСкачать прошивку в книгу",
                "&7Положит &fкнигу для записи&7 с текущим текстом",
                "&7в инвентарь. Правь её и грузи обратно."));
        inv.setItem(3, Gui.button(Material.WRITABLE_BOOK, "&aЗагрузить из книги в руке",
                "&7Книга (или &fкнига для записи&7) должна быть в &fправой руке&7.",
                "&7Текст читается как есть: по строке на оператор,",
                "&7точка с запятой в конце обязательна."));
        inv.setItem(4, Gui.button(Material.REPEATER, "&eReset",
                "&7Как кнопка на плате: счётчик команд в начало,",
                "&7ноги — в input, прошивка остаётся."));
        inv.setItem(5, Gui.button(Material.BARRIER, "&cСтереть прошивку",
                "&7Чип остаётся на месте, но все ноги",
                "&7становятся входами, а питание через"
                , "&7него по-прежнему идёт."));
        inv.setItem(7, Gui.button(Material.CLOCK, "&7Обновить",
                "&7Пересчитать плитки: симуляция шагает 20 раз в секунду"));
        inv.setItem(8, Gui.button(Material.CHEST, "&7Пример: мигание + кнопка",
                "&7Клик — записать готовую прошивку:",
                "&8pin 2 output;  pin 4 input;",
                "&8if (digitalRead(4) == HIGH) { pin 2 ... }",
                "&7(нога 2 — светодиод, нога 4 — кнопка)"));

        for (int pin = 2; pin <= 5; pin++) {
            inv.setItem(9 + pin, pinCard(c, pin));
        }
        inv.setItem(17, Gui.button(Material.COMPASS, "&7Язык прошивки",
                "&fpin &7N&f input|output|pwm&7; &8— направление ноги",
                "&fdigitalWrite(&7N, HIGH|LOW&7); &8— уровень",
                "&fanalogWrite(&7N, 0..255&7); &8— ШИМ (нога в pwm)",
                "&fdelay(&7мс&7); &8— пауза, квант 50 мс",
                "&fif (digitalRead(&7N&f) == HIGH) { &7...&f }",
                "&felse { &7...&f }  &8— сравнение: == != > < >= <=",
                "&fwhile (&7...&f) { &7...&f } &8— до " + Firmware.WHILE_LIMIT + " повторов на тик",
                "&fanalogRead(&7N&f) > 512 &8— АЦП 0..1023 (VCC = 1023)",
                "&7// — комментарий. Другого синтаксиса нет,",
                "&7зато ошибки объясняются построчно.",
                "&8Максимум " + Firmware.MAX_STATEMENTS + " операторов, ноги 2..5."));
        inv.setItem(35, Gui.button(Material.OAK_DOOR, "&cЗакрыть"));
    }

    /** Плитка ноги: направление, уровень, напряжение, АЦП и ток. */
    private static ItemStack pinCard(CompInstance c, int pin) {
        int dir = c.mcuDir[pin];
        double u = c.mcuVolt[pin];
        int adc = c.mcuAdc[pin];
        double ma = c.mcuPinI[pin] * 1000;
        boolean shorted = Math.abs(ma) > (c.def.iLimit > 0 ? c.def.iLimit : 1.0) * 1000 * 0.98;
        Material icon;
        String head;
        if (dir == 0) {
            icon = Material.GRAY_DYE;
            head = "&7нога " + pin + " &8— вход (hi-Z)";
        } else if (dir == 2) {
            icon = Material.LIGHT_BLUE_DYE;
            head = "&bнога " + pin + " &8— ШИМ " + c.mcuPwm[pin] + "/255";
        } else if (c.mcuLvl[pin] != 0) {
            icon = Material.LIME_DYE;
            head = "&aнога " + pin + " &8— выход HIGH";
        } else {
            icon = Material.RED_DYE;
            head = "&cнога " + pin + " &8— выход LOW";
        }
        if (shorted) {
            icon = Material.GUNPOWDER;
            head = head + " &4(предел тока)";
        }
        return Gui.button(icon, head,
                "&7Напряжение: &f" + fmt(u, 2) + " В&7, АЦП: &f" + adc + "&7/1023",
                "&7Ток: &f" + (ma >= 0 ? "+" : "") + (int) ma + " мА&7 (порт отдаёт +)",
                dir == 0 ? "&7Читает то, что посчитал решатель"
                        : "&7Держит &f" + fmt(c.mcuVcc * (dir == 2 ? c.mcuPwm[pin] / 255.0
                        : (c.mcuLvl[pin] != 0 ? 1 : 0)), 2) + " В&7 через &f"
                        + fmt(c.def.resistance, 0) + " Ом");
    }

    private static String fmt(double v, int digits) {
        return String.format(Locale.ROOT, "%." + digits + "f", v);
    }

    // -------------------------------------------------------------------- клики

    @Override
    public void onClick(Player who, InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= inv.getSize()) {
            return;
        }
        e.setCancelled(true);
        if (raw == 35 || raw == 0) {
            who.closeInventory();
            return;
        }
        if (raw == 7 || (raw >= 11 && raw <= 14)) {
            // «Обновить» и плитки ног — только пересчёт: значения живые
            rebuild();
            return;
        }
        CompInstance c = mcu();
        if (c == null) {
            rebuild();
            return;
        }
        switch (raw) {
            case 2:
                giveBook(who, c);
                return;
            case 3:
                upload(who, c);
                rebuild();
                return;
            case 4:
                c.resetFirmware();
                c.program = c.program == null ? "" : c.program;
                plugin.persistence().write(c);
                Items.message(who, "&e↻ reset: &7прошивка начата сначала, "
                        + c.def.pins + " ног в input");
                rebuild();
                return;
            case 5:
                c.program = "";
                c.resetFirmware();
                plugin.persistence().write(c);
                Items.message(who, "&7прошивка стёрта");
                rebuild();
                return;
            case 8:
                c.program = DEMO;
                c.firmware = null;
                plugin.persistence().write(c);
                Items.message(who, "&a✔ пример записан&7: нога 2 моргает, пока кнопка на ноге 4 нажата");
                rebuild();
                return;
            default:
                rebuild();
                return;
        }
    }

    private static final String DEMO = ""
            + "// мигание с кнопкой: пока вход 4 замкнут на HIGH,\n"
            + "// нога 2 моргает 1 Гц, иначе погашена\n"
            + "pin 2 output;\n"
            + "pin 4 input;\n"
            + "while (digitalRead(4) == HIGH) {\n"
            + "  digitalWrite(2, HIGH);\n"
            + "  delay(500);\n"
            + "  digitalWrite(2, LOW);\n"
            + "  delay(500);\n"
            + "}\n"
            + "digitalWrite(2, LOW);\n";

    /** Книга из правой руки -> прошивка чипа. */
    private void upload(Player who, CompInstance c) {
        ItemStack hand = who.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir() || !(hand.getItemMeta() instanceof BookMeta bm)) {
            Items.message(who, "&eДержи в правой руке &fкнигу&7 (или &fкнигу для записи&7) с текстом прошивки");
            return;
        }
        List<String> pages = bm.getPages();
        StringBuilder sb = new StringBuilder();
        for (String page : pages) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(page);
        }
        String src = sb.toString();
        if (src.length() > MAX_CHARS) {
            Items.message(who, "&c✗ прошивка длиннее " + MAX_CHARS + " символов — сократи");
            return;
        }
        Firmware.Program p = Firmware.compile(src, c.def.pins);
        if (!p.ok()) {
            // не пишем битый код: иначе чип встанет, а игрок не поймёт почему
            Items.message(who, "&c✗ компиляция: &f" + p.errorMessage());
            Items.message(who, "&7исправь строку &f" + p.errorLine() + " &7и грузи снова");
            return;
        }
        c.program = src;
        c.firmware = p;
        c.firmwareSource = src;
        plugin.persistence().write(c);
        Items.message(who, "&a✔ прошито: &f" + p.statements() + " &7операторов, "
                + "&f" + src.length() + " &7символов — счётчик команд с нуля");
    }

    private void giveBook(Player who, CompInstance c) {
        String src = c.program == null || c.program.isEmpty() ? DEMO : c.program;
        if (src.length() > MAX_CHARS) {
            src = src.substring(0, MAX_CHARS);
        }
        ItemStack book = new ItemStack(Material.WRITABLE_BOOK);
        BookMeta meta = (BookMeta) book.getItemMeta();
        List<String> pages = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String line : src.split("\n", -1)) {
            // страница ~250 символов: режем по строкам, чтобы оператор не разрывался
            if (cur.length() > 0 && cur.length() + line.length() + 1 > 240) {
                pages.add(cur.toString());
                cur.setLength(0);
            }
            if (line.length() > 240) {
                for (int i = 0; i < line.length(); i += 240) {
                    pages.add(line.substring(i, Math.min(line.length(), i + 240)));
                }
                continue;
            }
            if (cur.length() > 0) {
                cur.append('\n');
            }
            cur.append(line);
        }
        if (cur.length() > 0) {
            pages.add(cur.toString());
        }
        if (pages.isEmpty()) {
            pages.add("");
        }
        while (pages.size() > 15) {
            pages = pages.subList(0, 15);
        }
        if (meta != null) {
            meta.setPages(pages);
            book.setItemMeta(meta);
        }
        who.getInventory().addItem(book);
        who.updateInventory();
        Items.message(who, "&a✔ книга для записи&7: &f" + pages.size()
                + " &7страниц, &f" + src.length() + " &7символов. Пиши в неё, держи в руке и жми «Загрузить».");
    }
}
