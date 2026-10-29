package me.arena.electronics.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Минимальный читатель JSON (объекты, массивы, строки, числа, true/false/null).
 * Нужен, чтобы не тянуть сторонние библиотеки: Bukkit не публикует Gson в API.
 */
public final class Json {

    public static Map<String, Object> objectOf(String src) {
        Object o = parse(src);
        if (o instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) o;
            return m;
        }
        throw new IllegalArgumentException("Ожидался JSON-объект");
    }

    public static Object parse(String src) {
        Parser p = new Parser(src);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        return v;
    }

    private Json() {
    }

    /** Безопасные приведения типов. */
    public static String str(Map<String, Object> o, String k, String def) {
        Object v = o.get(k);
        return v == null ? def : String.valueOf(v);
    }

    public static double num(Map<String, Object> o, String k, double def) {
        Object v = o.get(k);
        if (v instanceof Number n) {
            return n.doubleValue();
        }
        if (v instanceof String s) {
            try {
                return Double.parseDouble(s);
            } catch (NumberFormatException ignored) {
                return def;
            }
        }
        return def;
    }

    public static int integer(Map<String, Object> o, String k, int def) {
        return (int) Math.round(num(o, k, def));
    }

    public static boolean bool(Map<String, Object> o, String k, boolean def) {
        Object v = o.get(k);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof Number n) {
            return n.intValue() != 0;
        }
        if (v instanceof String s) {
            return Boolean.parseBoolean(s);
        }
        return def;
    }

    @SuppressWarnings("unchecked")
    public static List<Object> arr(Map<String, Object> o, String k) {
        Object v = o.get(k);
        return v instanceof List ? (List<Object>) v : List.of();
    }

    @SuppressWarnings("unchecked")
    public static List<Map<String, Object>> objs(Map<String, Object> o, String k) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object v : arr(o, k)) {
            if (v instanceof Map) {
                out.add((Map<String, Object>) v);
            }
        }
        return out;
    }

    private static final class Parser {
        private final String s;
        private int i;

        Parser(String s) {
            this.s = s;
        }

        void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }

        Object value() {
            skipWs();
            if (i >= s.length()) {
                throw err("Неожиданный конец файла");
            }
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> obj();
                case '[' -> array();
                case '"' -> string();
                case 't', 'f' -> literal();
                case 'n' -> {
                    expect("null");
                    yield null;
                }
                default -> number();
            };
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; // {
            skipWs();
            if (peek() == '}') {
                i++;
                return m;
            }
            while (true) {
                skipWs();
                String k = string();
                skipWs();
                if (peek() != ':') {
                    throw err("Ожидалось ':'");
                }
                i++;
                m.put(k, value());
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                } else if (c == '}') {
                    i++;
                    return m;
                } else {
                    throw err("Ожидался ',' или '}'");
                }
            }
        }

        List<Object> array() {
            List<Object> l = new ArrayList<>();
            i++; // [
            skipWs();
            if (peek() == ']') {
                i++;
                return l;
            }
            while (true) {
                l.add(value());
                skipWs();
                char c = peek();
                if (c == ',') {
                    i++;
                } else if (c == ']') {
                    i++;
                    return l;
                } else {
                    throw err("Ожидался ',' или ']'");
                }
            }
        }

        String string() {
            skipWs();
            if (peek() != '"') {
                throw err("Ожидалась строка");
            }
            i++;
            StringBuilder b = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '"') {
                    return b.toString();
                }
                if (c == '\\') {
                    char e = s.charAt(i++);
                    switch (e) {
                        case 'n' -> b.append('\n');
                        case 't' -> b.append('\t');
                        case 'r' -> b.append('\r');
                        case 'b' -> b.append('\b');
                        case 'f' -> b.append('\f');
                        case '/' -> b.append('/');
                        case '"' -> b.append('"');
                        case '\\' -> b.append('\\');
                        case 'u' -> {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw err("Неизвестный экранирующий символ \\" + e);
                    }
                } else {
                    b.append(c);
                }
            }
            throw err("Незакрытая строка");
        }

        Object literal() {
            if (s.startsWith("true", i)) {
                i += 4;
                return Boolean.TRUE;
            }
            if (s.startsWith("false", i)) {
                i += 5;
                return Boolean.FALSE;
            }
            throw err("Некорректное литеральное значение");
        }

        void expect(String lit) {
            if (!s.startsWith(lit, i)) {
                throw err("Ожидалось " + lit);
            }
            i += lit.length();
        }

        Object number() {
            int start = i;
            if (peek() == '-' || peek() == '+') {
                i++;
            }
            while (i < s.length() && "0123456789.eE+-".indexOf(s.charAt(i)) >= 0) {
                i++;
            }
            String raw = s.substring(start, i).trim();
            if (raw.isEmpty()) {
                throw err("Ожидалось число");
            }
            try {
                if (raw.contains(".") || raw.contains("e") || raw.contains("E")) {
                    return Double.parseDouble(raw);
                }
                long l = Long.parseLong(raw);
                if (l >= Integer.MIN_VALUE && l <= Integer.MAX_VALUE) {
                    return (int) l;
                }
                return (double) l;
            } catch (NumberFormatException ex) {
                throw err("Некорректное число '" + raw + "'");
            }
        }

        char peek() {
            return i < s.length() ? s.charAt(i) : '\0';
        }

        IllegalArgumentException err(String msg) {
            int line = 1;
            for (int k = 0; k < i && k < s.length(); k++) {
                if (s.charAt(k) == '\n') {
                    line++;
                }
            }
            return new IllegalArgumentException("JSON: " + msg + " (позиция " + i + ", строка " + line + ")");
        }
    }
}
