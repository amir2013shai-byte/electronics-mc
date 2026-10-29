package me.arena.electronics.util;

import org.bukkit.ChatColor;

/** Цвета через &-коды (Spigot-совместимо, без Adventure). */
public final class Legacy {

    private Legacy() {
    }

    public static String color(String s) {
        return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s);
    }
}
