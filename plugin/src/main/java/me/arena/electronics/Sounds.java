package me.arena.electronics;

import me.arena.electronics.registry.LocationKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.HashMap;
import java.util.Map;

/** Звуки с «газом»: не чаще одного звука на позицию каждые N тиков. */
public final class Sounds {

    private final ElectronicsPlugin plugin;
    private final Map<String, Long> last = new HashMap<>();
    private long tick;

    public Sounds(ElectronicsPlugin plugin) {
        this.plugin = plugin;
    }

    public void tick() {
        tick++;
        if (tick % 200 == 0) {
            last.clear();
        }
    }

    public void buzzer(LocationKey key) {
        play(key, Sound.BLOCK_NOTE_BLOCK_BELL, 0.6f, 1.2f, 6);
    }

    public void click(LocationKey key, boolean on) {
        play(key, on ? Sound.BLOCK_STONE_BUTTON_CLICK_ON : Sound.BLOCK_STONE_BUTTON_CLICK_OFF,
                0.8f, 1.0f, 2);
    }

    public void zap(LocationKey key) {
        play(key, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.35f, 1.8f, 1);
    }

    public void pop(LocationKey key) {
        play(key, Sound.BLOCK_LAVA_EXTINGUISH, 0.5f, 1.4f, 1);
    }

    private void play(LocationKey key, Sound sound, float volume, float pitch, int cooldownTicks) {
        if (!plugin.config().soundsEnabled) {
            return;
        }
        long prev = last.getOrDefault(key.flat(), -1000L);
        if (tick - prev < cooldownTicks) {
            return;
        }
        last.put(key.flat(), tick);
        Block b = key.block();
        if (b == null) {
            return;
        }
        World w = b.getWorld();
        w.playSound(b.getLocation().add(0.5, 0.7, 0.5), sound, volume, pitch);
    }
}
