package me.arena.electronics.registry;

import me.arena.electronics.util.Json;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Реестр типов компонентов. Читается из {@code components.json} — того же файла,
 * из которого генерируется ресурспак, поэтому id предметов, модели и индексы
 * состояний на клиенте и сервере гарантированно совпадают.
 */
public final class Components {

    /** Префикс namespace ресурспака/моделей. */
    public static final String NAMESPACE = "electronics";

    /** Индексы состояний (см. visual_states в components.json). */
    public static final int ST_OFF = 0;
    public static final int ST_DIM = 1;
    public static final int ST_ON = 2;
    public static final int ST_BURNT = 3;
    public static final int ST_REVERSE = 4;
    public static final int ST_CLOSED = 5;

    public static final String[] STATE_NAMES = {"off", "dim", "on", "burnt", "rev", "closed"};

    private static final Map<String, Def> BY_ID = new LinkedHashMap<>();
    private static final List<Def> ORDER = new ArrayList<>();
    private static int totalModels = 0;

    private Components() {
    }

    public static synchronized void reload(InputStream jarResource, File extraDir) {
        BY_ID.clear();
        ORDER.clear();
        load(jarResource, extraDir);
    }

    public static synchronized void load(InputStream jarResource, File extraDir) {
        BY_ID.clear();
        ORDER.clear();
        String json = readJar(jarResource);
        if (json != null) {
            parse(json);
        }
        if (extraDir != null && extraDir.isDirectory()) {
            File[] files = extraDir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".json"));
            if (files != null) {
                for (File f : files) {
                    try {
                        parse(java.nio.file.Files.readString(f.toPath(), StandardCharsets.UTF_8));
                    } catch (IOException | RuntimeException ex) {
                        System.err.println("[Electronics] не удалось прочитать " + f.getName() + ": " + ex.getMessage());
                    }
                }
            }
        }
        recalc();
    }

    private static void recalc() {
        for (int i = 0; i < ORDER.size(); i++) {
            ORDER.get(i).index = i;
        }
        int sum = 0;
        for (Def d : ORDER) {
            d.cachedBase = sum;   // инкрементально: рендер не пересчитывает с нуля
            sum += d.modelSlice();
        }
        totalModels = sum;
    }

    private static void parse(String json) {
        Map<String, Object> root = Json.objectOf(json);
        for (Map<String, Object> o : Json.objs(root, "components")) {
            Def d = fromJson(o);
            if (BY_ID.putIfAbsent(d.id, d) == null) {
                ORDER.add(d);
            }
        }
    }

    private static Def fromJson(Map<String, Object> o) {
        Def d = new Def();
        d.id = Json.str(o, "id", "unknown");
        d.key = NAMESPACE + ":" + d.id;
        d.nameRu = Json.str(o, "ru", d.id);
        d.nameEn = Json.str(o, "en", "");
        d.kind = Kind.valueOf(Json.str(o, "kind", "EMPTY").toUpperCase(Locale.ROOT));
        d.shape = Json.str(o, "shape", "box");
        d.logic = Json.str(o, "logic", "");
        d.resistance = Json.num(o, "resistance", 0);
        d.rInt = Json.num(o, "r_int", 1.0);
        d.voltage = Json.num(o, "voltage", 0);
        d.vForward = Json.num(o, "v_forward", 0);
        d.vDrop = Json.num(o, "v_drop", 0);
        // обе орфографии: 52 def-а используют v_reverse_max, diode — v_rev_max;
        // раньше читалась только одна, и у LED лимит обратного напряжения молча
        // оставался 0 (защита от лавинного пробоя не срабатывала вовсе)
        d.vReverseMax = Json.num(o, "v_rev_max", Json.num(o, "v_reverse_max", 0));
        d.ratedPower = Json.num(o, "rated_power", 0);
        d.ratedVoltage = Json.num(o, "rated_voltage", 0);
        d.maxCurrent = Json.num(o, "max_current", 0);
        d.minCurrent = Json.num(o, "min_current", 0);
        d.capacitance = Json.num(o, "capacitance", 0);
        d.beta = Json.num(o, "beta", 0);
        d.resistanceHot = Json.num(o, "resistance_hot", 0);
        d.coldFactor = Json.num(o, "cold_factor", 1.0);
        d.internalResistance = Json.num(o, "internal_resistance", 0);
        d.backEmfPerVolt = Json.num(o, "back_emf_per_volt", 0);
        d.winding = Json.num(o, "winding", 0);
        d.inductance = Json.num(o, "inductance", 0);
        d.fieldPerAmp = Json.num(o, "field_per_amp", 0);
        d.fieldMt = Json.num(o, "field_mt", 0);
        d.operateMa = Json.num(o, "operate_ma", 0);
        d.releaseMa = Json.num(o, "release_ma", 0);
        d.operateMt = Json.num(o, "operate_mt", 0);
        d.lightDependent = Json.bool(o, "light_dependent", false);
        d.bConstant = Json.num(o, "b_constant", 0);
        d.selfHeat = Json.num(o, "self_heat", 0);
        d.orientable = Json.bool(o, "orientable", false);
        d.stack = Json.bool(o, "stack", false);
        d.hub = Json.bool(o, "hub", false) || d.kind == Kind.GROUND || d.kind == Kind.DISTRIBUTOR;
        d.spdt = Json.bool(o, "spdt", false);
        d.polar = Json.bool(o, "polar", false);
        d.tunable = Json.bool(o, "tunable", false);
        d.states = Json.integer(o, "states", 1);
        d.pins = Json.integer(o, "pins", defaultPins(d.kind, d.spdt));
        d.glow = Json.integer(o, "glow", d.hasGlow() ? 12 : 0);
        List<Object> gc = Json.arr(o, "glow_color");
        if (gc.size() >= 3) {
            d.glowColor = new int[]{(int) Math.round(((Number) gc.get(0)).doubleValue()),
                    (int) Math.round(((Number) gc.get(1)).doubleValue()),
                    (int) Math.round(((Number) gc.get(2)).doubleValue())};
        }
        d.modelBase = NAMESPACE + ":" + d.id;
        d.iconTexture = "electronics:item/" + d.id;
        if (d.kind == Kind.LED && d.vDrop <= 0) {
            d.vDrop = d.vForward;
        }
        if (d.kind == Kind.DIODE && d.vForward <= 0) {
            d.vForward = d.vDrop;
        }
        return d;
    }

    private static int defaultPins(Kind kind, boolean spdt) {
        return switch (kind) {
            case RELAY -> 4;              // 0/1 — катушка, 2/3 — контакт
            case MAGNET -> 0;
            case TRANSISTOR, POT -> 3;
            case GATE -> 3;
            case SWITCH -> spdt ? 3 : 2;
            default -> 2;
        };
    }

    private static String readJar(InputStream in) {
        if (in == null) {
            return null;
        }
        try (InputStream is = in) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            return null;
        }
    }

    public static Def byIndex(int i) {
        return (i < 0 || i >= ORDER.size()) ? null : ORDER.get(i);
    }

    public static Def byId(String id) {
        if (id == null) {
            return null;
        }
        String plain = id.startsWith(NAMESPACE + ":") ? id.substring(NAMESPACE.length() + 1) : id;
        return BY_ID.get(plain.toLowerCase(Locale.ROOT));
    }

    public static List<Def> all() {
        return Collections.unmodifiableList(ORDER);
    }

    public static int size() {
        return ORDER.size();
    }

    /** Сколько всего состояний модели доступно ресурспаку (цель проекта — 800+). */
    public static int stateModels() {
        return totalModels;
    }

    public static String stateModelName(int stateModel) {
        int acc = 0;
        for (Def d : ORDER) {
            int slice = d.modelSlice();
            if (stateModel < acc + slice) {
                int rel = stateModel - acc;
                int st = rel / 6;
                int or = rel % 6;
                return d.id + " [" + (st < STATE_NAMES.length ? STATE_NAMES[st] : st) + "/" + or + "]";
            }
            acc += slice;
        }
        return "неизвестное состояние " + stateModel;
    }

    /** Сортированный список id (для таб-автодополнения). */
    public static List<String> ids() {
        List<String> out = new ArrayList<>(BY_ID.keySet());
        Collections.sort(out);
        return out;
    }
}
