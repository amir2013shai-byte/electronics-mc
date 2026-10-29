package me.arena.electronics.util;

import org.bukkit.entity.Player;

import java.lang.reflect.Method;

/**
 * Мост к action bar: в Paper это Player#sendActionBar(String), в чистом Spigot
 * ищем совместимый метод рефлексией (в 1.21.x sendMessage с Component /
 * spigot-специфичные варианты) и тихо откатываемся к обычному сообщению.
 */
public final class Text {

    private static Method actionBar;
    private static boolean resolved;

    private Text() {
    }

    public static void action(Player p, String legacyText) {
        String msg = me.arena.electronics.util.Legacy.color(legacyText);
        if (!resolved) {
            resolved = true;
            try {
                actionBar = Player.class.getMethod("sendActionBar", String.class);
            } catch (NoSuchMethodException ignored) {
                actionBar = null;
            }
        }
        if (actionBar != null) {
            try {
                actionBar.invoke(p, msg);
                return;
            } catch (ReflectiveOperationException | RuntimeException ignored) {
                // падаем в sendMessage
            }
        }
        p.sendMessage(msg);
    }
}
