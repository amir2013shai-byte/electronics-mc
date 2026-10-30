package me.arena.electronics.gui;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.Items;
import me.arena.electronics.registry.Components;
import me.arena.electronics.registry.Def;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Каталог всех предметов плагина: {@code /electro show} (и {@code /elec show}).
 *
 * <p>Страницы по 36 предметов, у каждого — описание, номиналы и что с ним
 * делает решатель. Клик выдаёт предмет (нужно право {@code electronics.give}),
 * правый клик печатает описание в чат — удобно на уроке, когда у половины
 * класса нет-op.</p>
 */
public final class CatalogGui implements Gui {

    /** Объяснения по kind — по имени, чтобы не зависеть от порядка enum'а. */
    private static final Map<String, String> WHAT = new HashMap<>();

    static {
        WHAT.put("RESISTOR", "Сопротивление по закону Ома; при P > 3·номинала — `burnt`.");
        WHAT.put("POT", "Подстроечный делитель: доля R задаётся tune (ПКМ или /elec tune).");
        WHAT.put("CAPACITOR", "Companion-модель C/dt, шаг 50 мс; полярные боятся обратной полярности.");
        WHAT.put("LED", "Кусочно-линейный: порог U, 25 Ом внутри, яркость = I/20 мА.");
        WHAT.put("LAMP", "Нелинейное R(T): холодная в ~6 раз легче, разгорается плавно.");
        WHAT.put("DIODE", "Ключ в одну сторону, обратный пробой по U обр.");
        WHAT.put("TRANSISTOR", "Упрощённое β-усиление: ток базы управляет коллектором, есть насыщение.");
        WHAT.put("GATE", "Цифра: выход через 100 Ом; нужны VCC сверху и GND снизу.");
        WHAT.put("SOURCE", "Тевенин: ЭДС + внутреннее сопротивление; на коротыше ток = ЭДС/r.");
        WHAT.put("GENERATOR", "ЭДС зависит от оборотов (крутится рядом с водой/ветром).");
        WHAT.put("SOLAR", "ЭДС × освещённость блока сверху: ночью 0 В.");
        WHAT.put("AMMETER", "0.5 Ом в разрыве, показывает ток своей ветви.");
        WHAT.put("VOLTMETER", "1 МОм параллельно, показывает разность своих контактов.");
        WHAT.put("MULTIMETER", "Прибор 3-в-одном: вольтметр / омметр / прозвонка (режим — ПКМ).");
        WHAT.put("PROBE", "Щуп: подсвечивается, если на контакте есть потенциал.");
        WHAT.put("WIRE", "Провод-перекрёсток: объединяет все шесть соседних сетей, имеет ножку вверх/вниз.");
        WHAT.put("GROUND", "Шина земли: её узел принимается за 0 В.");
        WHAT.put("DISTRIBUTOR", "Шина питания: как земля, но раздаёт плюс.");
        WHAT.put("SWITCH", "Ключ: ПКМ замыкает/размыкает; spdt перекидывает между контактами.");
        WHAT.put("BUTTON", "Кнопка: возврат через button-hold-ticks.");
        WHAT.put("TOGGLE", "Тумблер с фиксацией.");
        WHAT.put("FUSE", "Плавкая вставка копится I²t, а не рвётся мгновенно: 2× номинала — "
                + "доли секунды, 1.2× — минуты. Сгоревшую перепаять на столе (/elec repair).");
        WHAT.put("PTC", "Самоввосстанавливающийся: перегрев → R на порядки выше, остыл → "
                + "снова низкий. Ток удержания и ток срабатывания — разные числа, это не баг.");
        WHAT.put("VARISTOR", "I = K·U^α: до порога — обрыв, после — почти КЗ, и держит "
                + "напряжение. С каждым импульсом деградирует (утечка ×10 за удар).");
        WHAT.put("BRIDGE", "Четыре диода в одном корпусе: 0/1 — «~», 2 — «+», 3 — «−». "
                + "Падает ~1.1 В (два перехода в работе всегда).");
        WHAT.put("REGULATOR", "Uвых = min(Uцель, Uвх − dropout); не хватает входа — «проседает» "
                + "и греется. Ток ограничен: при КЗ выход проседает, а не горит.");
        WHAT.put("TRANSFORMER", "Две связанные обмотки: u = L·di/dt + M·di₂/dt, Кт = √(L1/L2). "
                + "Постоянку не передаёт вовсе — проверь на опыте.");
        WHAT.put("MOTOR", "Обмотка + ЭДС вращения; перегруз по U сжигает обмотку.");
        WHAT.put("BUZZER", "Пьезо/динамик: играет, когда через него идёт ток.");
        WHAT.put("BOARD", "Подставка: держит деталь, электрически inert — сети не объединяет.");
        WHAT.put("BREADBOARD", "Макетка: ряды контактов отдельными сетями.");
        WHAT.put("SCHEMATIC", "Планшет-схема: открыть редактор (ПКМ с предметом в руке).");
        WHAT.put("CUTTER", "Инструмент: снять провод, не сломав подставку.");
        WHAT.put("MAGNET", "Поле 1.4 мТл у полюса; цепь не замыкает, но рядом с ним "
                + "срабатывает геркон. Ставится и на деталь, и в клетку рядом.");
        WHAT.put("ELECTROMAGNET", "Катушка на сердечнике: R + L, поле = мТл/А × ток. "
                + "Притягивает геркон и якорь реле, но не мгновенно (L тормозит ток).");
        WHAT.put("INDUCTOR", "L против изменения тока: I = I_prev + dt/(L + dt·R)·(U − R·I_prev). "
                + "Первый пересчёт — почти обрыв, дальше разгон.");
        WHAT.put("RELAY", "Катушка (вдоль оси) + нормально разомкнутый контакт (вбок). "
                + "Якорь притягивается при токе выше operate_ma и держит до release_ma — гистерезис.");
        WHAT.put("REED", "Колба с контактами: замыкается, если рядом магнит или "
                + "намагниченная катушка (порог operate_mt в мТл).");
        WHAT.put("EMPTY", "Заглушка реестра.");
        WHAT.put("STATION", "Паяльный стол: на нём детали превращаются из заготовок в "
                + "электронику. Нужен источник тепла рядом (печь, костёр, лава) — от него "
                + "греют жало. Ставится как плата, цепь не проводит.");
        WHAT.put("TOOL", "Паяльник: жало греется до 100°, остывает ~1.2°/с. До 60° — "
                + "нормальная пайка, 20…59° — холодная (припой не растекается, припой сгорает "
                + "зря), ниже 20° — отказа. Износ: 64 пайки на жало.");
        WHAT.put("SUPPLY", "Расходники пайки: припой (1…2 шт. на деталь) и флюс "
                + "(обязателен для полупроводников, реле, катушек и приборов — без него "
                + "площадка окисляется).");
    }

    /** Точечные подписи там, где kind общий (фоторезистор и резистор — один kind). */
    private static final Map<String, String> WHAT_ID = new HashMap<>();

    static {
        WHAT_ID.put("desolder_braid", "Оплётка для выпайки: расплавленный припой затекает в "
                + "медные жилки по капилляру. Нужна, чтобы починить сгоревшую деталь: "
                + "ПКМ по столу или /elec repair (оплётка 1 + припой 1).");
        WHAT_ID.put("xfmr_230_12", "Первичка 230 В, вторичка 12 В при 10 ВА. Гальваническая "
                + "развязка: прикоснуться к вторичке безопаснее, а земля вторички — своя.");
        WHAT_ID.put("reg_7805", "Три ноги: IN (вдоль оси), OUT (вдоль оси), GND (вбок). "
                + "Нужно минимум 7 В на входе, иначе 5 В не держатся. На корпусе рассеивается "
                + "(Uвх−5)·I — потому и просят радиатор.");
        WHAT_ID.put("mov_275v", "Ставится на вход сети: нормальное 230 В он «не видит», а "
                + "импульс в киловольты срезает до ~700 В. Ставить параллельно входу!");
        WHAT_ID.put("ptc_500ma", "В отличие от плавкого, не требует замены: КЗ ушло — деталь "
                + "остыла за пару секунд и снова проводит.");
        WHAT_ID.put("fuse_cartridge_1a", "Стеклянная трубка 5×20 видна глазами: при 0.45·предела "
                + "I²t вставка краснеет (состояние dim), потом рвётся.");
        WHAT_ID.put("photoresistor", "Фоторезистор: R падает на свету (в темноте ~50× номинала). "
                + "В мире смотрит на освещённость блока сверху, в редакторе — «день».");
        WHAT_ID.put("thermistor_ntc", "NTC-термистор: R = R25·exp(B(1/T − 1/298)); греется током "
                + "сам и от лавы/огня рядом — можно увидеть тепловой пробой.");
    }

    private static final int PER_PAGE = 36;

    private final ElectronicsPlugin plugin;
    private final Inventory inv;
    private final int page;
    private final List<Def> defs;

    private CatalogGui(ElectronicsPlugin plugin, int page) {
        this.plugin = plugin;
        this.page = page;
        this.defs = Components.all();
        int pages = Math.max(1, (defs.size() + PER_PAGE - 1) / PER_PAGE);
        int size = 5 * 9;
        this.inv = Bukkit.createInventory(this, size,
                Items.color("&a⚡ Каталог &7(" + (page + 1) + "/" + pages + ") · "
                        + defs.size() + " предметов"));
        fill(pages);
    }

    /** Открыть каталог (или страницу) игроку. */
    public static void open(Player p, int page) {
        ElectronicsPlugin plugin = (ElectronicsPlugin) Bukkit.getPluginManager().getPlugin("Electronics");
        if (plugin == null) {
            return;
        }
        p.openInventory(new CatalogGui(plugin, Math.max(0, page)).inv);
    }

    private void fill(int pages) {
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= defs.size()) {
                break;
            }
            inv.setItem(i, card(defs.get(idx)));
        }
        int base = 4 * 9;
        if (page > 0) {
            inv.setItem(base, Gui.button(Material.ARROW, "&e◀ предыдущая страница"));
        }
        if (page + 1 < pages) {
            inv.setItem(base + 1, Gui.button(Material.ARROW, "&e следующая страница ▶"));
        }
        inv.setItem(base + 4, Gui.button(Material.BOOK, "&a" + defs.size() + " &7предметов, "
                + "&a" + plugin.stateModelCount() + " &7состояний модели",
                "&7ЛКМ — выдать 1, Shift+ЛКМ — 8,",
                "&7ПКМ — описание в чат (без права на выдачу)."));
    }

    /** Короткое объяснение «зачем эта деталь в цепи». */
    public static String whatIs(Def d) {
        return WHAT.getOrDefault(d.kind.name(), "");
    }

    private ItemStack card(Def d) {
        ItemStack stack = plugin.items().componentItem(d, 1);
        List<String> lore = new ArrayList<>();
        if (stack.hasItemMeta() && stack.getItemMeta().hasLore()) {
            lore.addAll(stack.getItemMeta().getLore());
        }
        String nominal = d.nominal();
        if (!nominal.isEmpty()) {
            lore.add(Items.color("&7Электрика: &f" + nominal));
        }
        String what = WHAT_ID.getOrDefault(d.id, WHAT.getOrDefault(d.kind.name(), ""));
        if (!what.isEmpty()) {
            for (String line : wrap(what)) {
                lore.add(Items.color("&b" + line));
            }
        }
        lore.add(Items.color("&8id: " + d.id + " · состояний модели: " + d.states
                + (d.hub ? " · hub" : "") + (d.polar ? " · полярный" : "")
                + (d.tunable ? " · регулируемый" : "")));
        if (!d.nameEn.isEmpty() && !d.nameEn.equalsIgnoreCase(d.nameRu)) {
            lore.add(Items.color("&8en: " + d.nameEn));
        }
        apply(stack, lore);
        return stack;
    }

    private static void apply(ItemStack stack, List<String> lore) {
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
    }

    private static List<String> wrap(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        for (String word : text.split(" ")) {
            if (sb.length() + word.length() > 46) {
                out.add(sb.toString());
                sb.setLength(0);
            }
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(word);
        }
        if (sb.length() > 0) {
            out.add(sb.toString());
        }
        return out;
    }

    @Override
    public Inventory inventory() {
        return inv;
    }

    @Override
    public void onClick(Player who, InventoryClickEvent e) {
        int raw = e.getRawSlot();
        if (raw < 0 || raw >= inv.getSize()) {
            return;                                  // клик по своему инвентарю — разрешаем
        }
        ItemStack current = inv.getItem(raw);
        int base = 4 * 9;
        if (raw == base || raw == base + 1) {
            open(who, page + (raw == base ? -1 : 1));
            return;
        }
        Def def = current == null ? null : Items.defOf(current);
        if (def == null) {
            return;
        }
        if (e.isRightClick()) {
            who.sendMessage(Items.color("&a⚡ " + def.nameRu + " &8(" + def.id + ")"));
            String nominal = def.nominal();
            if (!nominal.isEmpty()) {
                who.sendMessage(Items.color("&7Электрика: &f" + nominal));
            }
            String what = WHAT_ID.getOrDefault(def.id, WHAT.getOrDefault(def.kind.name(), ""));
            if (!what.isEmpty()) {
                who.sendMessage(Items.color("&b" + what));
            }
            return;
        }
        if (!who.hasPermission("electronics.give")) {
            who.sendMessage(Items.color("&eНужно право &felectronics.give&7"
                    + " — правым кликом просто читаешь описание."));
            return;
        }
        int count = e.isShiftClick() ? 8 : 1;
        who.getInventory().addItem(plugin.items().componentItem(def, count));
        who.updateInventory();
    }
}
