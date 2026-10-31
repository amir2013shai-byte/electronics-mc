package me.arena.electronics.render;

import me.arena.electronics.ElectronicsPlugin;
import me.arena.electronics.circuit.CompInstance;
import me.arena.electronics.circuit.CompStates;
import me.arena.electronics.circuit.ScopeScreen;
import me.arena.electronics.circuit.ScopeTrace;
import me.arena.electronics.registry.LocationKey;
import me.arena.electronics.registry.Orientation;
import me.arena.electronics.util.Num;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.TextDisplay;
import org.bukkit.Color;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Рендер компонентов: item_display (визуал модели) + text_display (показания).
 * Визуал выбирается ресурспаком по `item_model`, поэтому клиентские моды не нужны.
 */
public final class Renderer {

    private final ElectronicsPlugin plugin;
    /** Позиция -> сущности отображения. */
    private final Map<LocationKey, Ent> entities = new HashMap<>();
    /** Очередь изменений (обновляем пачкой на следующем тике). */
    private final Map<LocationKey, CompInstance> pending = new HashMap<>();
    private long lastSweep;

    private static final class Ent {
        ItemDisplay item;
        TextDisplay text;
        String modelKey = "";
        String textKey = "";
        int lightLevel = -1;
        /** Экран осциллографа: отдельный TextDisplay, потому что он живой. */
        TextDisplay screen;
        /** Прошлый растр — для послесвечения люминофора. */
        boolean[] prevMask;
        String screenKey = "";
        /** Последний угол, который мы отдали детали привода (чтобы не дёргать сущность зря). */
        float spin = -9999f;
    }

    public Renderer(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    public void requestUpdate(CompInstance c) {
        if ("none".equalsIgnoreCase(plugin.config().renderMode)) {
            return;
        }
        pending.put(c.key, c);
    }

    public void drop(LocationKey key) {
        Ent e = entities.remove(key);
        kill(e);
        pending.remove(key);
    }

    /** Убирает все сущности одной позиции: модель, подпись, экран. */
    private static void kill(Ent e) {
        if (e == null) {
            return;
        }
        for (org.bukkit.entity.Entity x : new org.bukkit.entity.Entity[]{e.item, e.text, e.screen}) {
            if (x != null && x.isValid()) {
                x.remove();
            }
        }
    }

    public void dropAll() {
        for (Ent e : new ArrayList<>(entities.values())) {
            kill(e);
        }
        entities.clear();
        pending.clear();
    }

    /** Применяет накопленные изменения немедленно (тот же тик). */
    public void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Map<LocationKey, CompInstance> batch = new HashMap<>(pending);
        pending.clear();
        for (CompInstance c : batch.values()) {
            update(c);
        }
    }

    /** Вызывается из основного тика: применяет накопленные изменения. */
    public void tick() {
        if (!pending.isEmpty()) {
            flush();
        }
        // Экран осциллографа обновляется сам: pending наполняется действиями
        // игрока и загрузкой из реестра, а не шагами симуляции, — иначе трасса
        // замирала бы между кликами.
        refreshScopes();
        sweepStale();
    }

    private long screenTick;

    /**
     * Перерисовка экранов осциллографов — отдельно от {@link #update}: трасса
     * меняется каждый тик, а {@code update} вызывается только когда у детали
     * сменились модель или подпись. Без этого хода экран стоял бы мёртвым.
     */
    private void refreshScopes() {
        if ("none".equalsIgnoreCase(plugin.config().renderMode)) {
            return;                             // render.mode: none — экранов нет
        }
        int every = Math.max(1, plugin.config().scopeRefreshTicks);
        if (screenTick++ % every != 0) {
            return;
        }
        for (Map.Entry<LocationKey, Ent> row : entities.entrySet()) {
            CompInstance c = plugin.circuit().get(row.getKey());
            if (c == null || c.def.kind != me.arena.electronics.registry.Kind.SCOPE) {
                continue;
            }
            updateScreen(c, row.getValue());
        }
    }

    private void update(CompInstance c) {
        Block base = c.key.block();
        if (base == null) {
            return;
        }
        Ent ent = entities.computeIfAbsent(c.key, k -> new Ent());
        Location loc = base.getLocation().add(0.5, 0.5 + plugin.config().offsetY, 0.5);
        float yaw = yawOf(c);
        if (c.def.kind == me.arena.electronics.registry.Kind.KINETIC) {
            // деталь привода крутится: угол модели — это и есть «движение» без клиентского мода
            yaw = (yaw + c.spin) % 360.0f;
        }
        String modelKey = c.def.id + "|" + c.state + "|" + c.orientation.name();

        if (ent.item == null || !ent.item.isValid()) {
            ent.item = spawnItem(loc, c, yaw, modelKey);
            ent.modelKey = modelKey;
        } else if (!modelKey.equals(ent.modelKey)) {
            ent.item.setItemStack(plugin.items().displayItem(c.def, c.state));
            ent.item.setTransformation(transformation(yaw));
            ent.modelKey = modelKey;
            ent.spin = c.spin;
        } else if (c.def.kind == me.arena.electronics.registry.Kind.KINETIC
                && Math.abs(ent.spin - c.spin) > 2.0f) {
            // тот же предмет, другой угол: только трансформация, без пересоздания сущности
            ent.item.setTransformation(transformation(yaw));
            ent.spin = c.spin;
        }

        // подсветка блока (для «лампочка/светодиод горит»): пробуем свет через API,
        // если ядро его не даёт — просто пропускаем (Spigot API не имеет setLight).
        int wantLight = lightFor(c);
        if (wantLight != ent.lightLevel && plugin.config().autoLight) {
            ent.lightLevel = wantLight;
            applyBlockLight(base, wantLight);
        }

        // подпись с показаниями
        String text = labelText(c);
        if (plugin.config().textLabels && !text.isEmpty()) {
            if (ent.text == null || !ent.text.isValid()) {
                ent.text = spawnText(loc.clone().add(0, labelY(c), 0), text);
                ent.textKey = text;
            } else if (!text.equals(ent.textKey)) {
                ent.text.setText(text);
                ent.textKey = text;
            }
        } else if (ent.text != null && ent.text.isValid() && text.isEmpty()) {
            ent.text.remove();
            ent.text = null;
        }

        if (c.def.kind == me.arena.electronics.registry.Kind.SCOPE) {
            updateScreen(c, ent);
        }
    }

    /**
     * Подпись осциллографа живёт ВЫШЕ экрана: экран — это 4 строки по 20 знаков,
     * и на одной высоте с ними они слились бы в кашу.
     */
    private double labelY(CompInstance c) {
        return c.def.kind == me.arena.electronics.registry.Kind.SCOPE ? 1.55 : 0.75;
    }

    /**
     * Экран осциллографа: 40×16 точек, упакованных в 4 строки по 20 знака
     * Брайля. Один TextDisplay, а не россыпь мелких: меньше сущностей, меньше
     * пакетов, и картинка не разъезжается по строкам на лаге.
     */
    private void updateScreen(CompInstance c, Ent ent) {
        Block anchorBlock = c.key.block();
        if (anchorBlock == null) {
            return;
        }
        ScopeTrace tr = plugin.circuit().scope(c.key);
        if (tr.size() < 2) {
            if (ent.screen != null && ent.screen.isValid()) {
                ent.screen.remove();
                ent.screen = null;
            }
            return;
        }
        Location base = anchorBlock.getLocation().add(0.5, 0.5, 0.5);
        ScopeTrace.Stats st = tr.measure();
        double[] sc = ScopeScreen.autoscale(st.mean(), st.vpp(), 1.3);
        double[] v = new double[ScopeTrace.SCREEN_SAMPLES];
        for (int i = 0; i < v.length; i++) {
            v[i] = tr.at(st.start() + i);
        }
        boolean[] px = ScopeScreen.mask();
        if (plugin.config().scopeAfterglow) {
            ScopeScreen.afterglow(px, ent.prevMask, 3);
        }
        ScopeScreen.trace(v, sc[0], sc[1], px);
        ent.prevMask = px;
        String pic = String.join("\n", ScopeScreen.braille(px));
        if (pic.equals(ent.screenKey) && ent.screen != null && ent.screen.isValid()) {
            return;                                     // кадры совпали — не дёргаем сущность
        }
        ent.screenKey = pic;
        if (ent.screen == null || !ent.screen.isValid()) {
            org.bukkit.entity.Entity spawned = base.getWorld().spawnEntity(
                    base.clone().add(0, 1.05 + plugin.config().offsetY, 0), EntityType.TEXT_DISPLAY);
            if (!(spawned instanceof TextDisplay td)) {
                spawned.remove();
                return;
            }
            td.setBillboard(Display.Billboard.CENTER);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            // Фон — org.bukkit.Color, а не int. Отдельного «размера текста» у
            // TextDisplay в Spigot нет: масштаб задаётся трансформацией (той же,
            // что и у модели), поэтому 0.24 = 20 знаков брайля на грань блока.
            td.setBackgroundColor(Color.fromARGB(0xE6, 0x0A, 0x24, 0x10));
            td.setShadowed(false);
            td.setSeeThrough(false);
            float ts = (float) plugin.config().scopeTextSize;
            Quaternionf ident = new Quaternionf();
            td.setTransformation(new Transformation(new Vector3f(0f, 0f, 0f), ident,
                    new Vector3f(ts, ts, ts), ident));
            td.setViewRange((float) plugin.config().viewRange);
            td.setPersistent(false);
            td.setInvulnerable(true);
            applyPdc(td, c.key);
            ent.screen = td;
        }
        // Цвет — &-кодом (см. util/Legacy): setText у TextDisplay принимает
        // только String, а клиент разбирает § и в тексте дисплеев.
        ent.screen.setText(me.arena.electronics.util.Legacy.color("&a" + pic));
    }

    private void applyBlockLight(Block base, int level) {
        try {
            java.lang.reflect.Method m = base.getClass().getMethod("setLight", int.class);
            m.invoke(base, level);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Spigot не даёт setLight: свет ставят только Paper-подобные ядра
        }
    }

    private ItemDisplay spawnItem(Location loc, CompInstance c, float yaw, String modelKey) {
        org.bukkit.entity.Entity spawned = loc.getWorld().spawnEntity(loc, EntityType.ITEM_DISPLAY);
        if (!(spawned instanceof ItemDisplay display)) {
            spawned.remove();
            return null;
        }
        display.setItemStack(plugin.items().displayItem(c.def, c.state));
        display.setBillboard(Display.Billboard.VERTICAL);
        display.setTransformation(transformation(yaw));
        display.setViewRange((float) plugin.config().viewRange);
        display.setPersistent(false);
        display.setInvulnerable(true);
        applyPdc(display, c.key);
        return display;
    }

    private TextDisplay spawnText(Location loc, String text) {
        org.bukkit.entity.Entity spawned = loc.getWorld().spawnEntity(loc, EntityType.TEXT_DISPLAY);
        if (!(spawned instanceof TextDisplay td)) {
            spawned.remove();
            return null;
        }
        td.setText(text);
        td.setBillboard(Display.Billboard.CENTER);
        td.setAlignment(TextDisplay.TextAlignment.CENTER);
        td.setViewRange(0.55f);
        td.setPersistent(false);
        td.setInvulnerable(true);
        td.setSeeThrough(false);
        return td;
    }

    private void applyPdc(org.bukkit.entity.Entity e, LocationKey key) {
        var pdc = e.getPersistentDataContainer();
        pdc.set(KEY_MARK, org.bukkit.persistence.PersistentDataType.STRING, key.flat());
    }

    public static final org.bukkit.NamespacedKey KEY_MARK =
            new org.bukkit.NamespacedKey("electronics", "owner_block");

    private Transformation transformation(float yawDegrees) {
        double s = plugin.config().renderScale;
        Quaternionf q = new Quaternionf().rotateY((float) Math.toRadians(yawDegrees));
        return new Transformation(new Vector3f(0f, 0f, 0f), q, new Vector3f((float) s, (float) s, (float) s), q);
    }

    private float yawOf(CompInstance c) {
        Orientation o = c.orientation == null ? Orientation.NORTH : c.orientation;
        return switch (o) {
            case NORTH -> 0.0f;
            case EAST -> 90.0f;
            case SOUTH -> 180.0f;
            case WEST -> 270.0f;
            case UP, DOWN -> 0.0f;
        };
    }

    private int lightFor(CompInstance c) {
        if (!c.def.hasGlow()) {
            return 0;
        }
        if (c.state == CompStates.BURNT) {
            return 0;
        }
        int level = (int) Math.round(Num.clamp(c.brightness, 0, 1) * (c.def.glow > 0 ? c.def.glow : 12));
        return Math.max(0, Math.min(15, level));
    }

    private String labelText(CompInstance c) {
        return switch (c.def.kind) {
            case VOLTMETER -> "V " + Num.volt(c.reading);
            case AMMETER -> "A " + Num.amp(c.reading);
            case LED, LAMP, DIODE -> c.state == CompStates.BURNT ? "!" : (c.brightness > 0.02 ? Num.watt(c.power) : "");
            case RESISTOR -> c.state == CompStates.BURNT ? "!" : (c.power > 0.01 ? Num.watt(c.power) : "");
            case CAPACITOR -> c.voltage > 0.05 ? Num.volt(c.voltage) : "";
            case SCOPE -> scopeLabel(c);
            case MOTOR -> c.speedPrev > 0.02 ? "n=" + Num.pct(c.speedPrev) + "%" : "";
            case GENERATOR -> "ген " + Num.volt(c.tunedVoltage());
            default -> "";
        };
    }

    /** Строка под экраном: размах, вольт на деление и измеренная частота. */
    private String scopeLabel(CompInstance c) {
        ScopeTrace tr = plugin.circuit().scope(c.key);
        if (tr.size() < 4) {
            return "осц: нет сигнала";
        }
        ScopeTrace.Stats st = tr.measure();
        double tdiv = ScopeTrace.SCREEN_SAMPLES / 10.0 * tr.dt();
        return "pk-pk " + Num.volt(st.vpp()) + " · " + Num.volt(st.vpp() / 10.0) + "/дел · "
                + String.format(Locale.US, "%.2f Гц · %.2f с/дел", st.freqHz(), tdiv);
    }

    /** Раз в ~5 секунд убираем сирот-сущности (например, после /reload или взрыва чанка). */
    private void sweepStale() {
        long now = System.currentTimeMillis();
        if (now - lastSweep < 5000) {
            return;
        }
        lastSweep = now;
        Iterator<Map.Entry<LocationKey, Ent>> it = entities.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<LocationKey, Ent> e = it.next();
            if (plugin.circuit().get(e.getKey()) == null) {
                kill(e.getValue());
                it.remove();
            }
        }
    }

    /** Удаление всех наших сущностей в мире (для /elec clear). */
    public void removeAllInWorld(World world) {
        List<org.bukkit.entity.Entity> doomed = new ArrayList<>();
        for (org.bukkit.entity.Entity e : world.getEntities()) {
            if (e instanceof ItemDisplay || e instanceof TextDisplay) {
                String mark = e.getPersistentDataContainer().get(KEY_MARK, org.bukkit.persistence.PersistentDataType.STRING);
                if (mark != null) {
                    doomed.add(e);
                }
            }
        }
        for (org.bukkit.entity.Entity e : doomed) {
            e.remove();
        }
    }
}
