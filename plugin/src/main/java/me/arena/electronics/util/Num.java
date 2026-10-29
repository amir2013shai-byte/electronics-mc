package me.arena.electronics.util;

/** Мелкие числовые утилиты. */
public final class Num {

    private Num() {
    }

    public static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    public static boolean eq(double a, double b, double eps) {
        return Math.abs(a - b) <= eps;
    }

    /** "3.3 кОм", "220 мкА", "12.5 мВт" — компактная инженерная запись. */
    public static String eng(double value, String unit) {
        double a = Math.abs(value);
        if (a < 1e-9) {
            return "0 " + unit;
        }
        String prefix = "";
        double scale = 1;
        if (a < 1e-3) {
            prefix = "мк";
            scale = 1e6;
        } else if (a < 1) {
            prefix = "м";
            scale = 1e3;
        } else if (a < 1e3) {
            prefix = "";
            scale = 1;
        } else if (a < 1e6) {
            prefix = "к";
            scale = 1e-3;
        } else {
            prefix = "М";
            scale = 1e-6;
        }
        double v = value * scale;
        String n;
        if (Math.abs(v) >= 100) {
            n = String.format("%.0f", v);
        } else if (Math.abs(v) >= 10) {
            n = String.format("%.1f", v);
        } else {
            n = String.format("%.2f", v);
        }
        return n + " " + prefix + unit;
    }

    public static String res(double ohm) {
        return eng(ohm, "Ом");
    }

    public static String volt(double v) {
        return eng(v, "В");
    }

    public static String amp(double a) {
        return eng(a, "А");
    }

    public static String watt(double w) {
        return eng(w, "Вт");
    }

    public static String farad(double f) {
        return eng(f, "Ф");
    }

    public static int pct(double v) {
        return (int) Math.round(clamp(v, 0, 1) * 100);
    }
}
