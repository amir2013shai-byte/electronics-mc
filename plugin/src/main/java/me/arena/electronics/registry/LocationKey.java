package me.arena.electronics.registry;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;

import java.util.Locale;
import java.util.Objects;

/** Лёгкий ключ позиции блока (world + целые координаты). */
public final class LocationKey {

    private final int worldHash;
    private final String worldName;
    public final int x;
    public final int y;
    public final int z;

    public LocationKey(World world, int x, int y, int z) {
        this.worldHash = world == null ? 0 : world.hashCode();
        this.worldName = world == null ? "?" : world.getName();
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public static LocationKey of(Block b) {
        return new LocationKey(b.getWorld(), b.getX(), b.getY(), b.getZ());
    }

    public static LocationKey of(Location l) {
        return new LocationKey(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    public LocationKey shift(int dx, int dy, int dz) {
        World w = org.bukkit.Bukkit.getWorld(worldName);
        return new LocationKey(w, x + dx, y + dy, z + dz);
    }

    public static LocationKey of(String worldName, double x, double y, double z) {
        World w = org.bukkit.Bukkit.getWorld(worldName);
        return new LocationKey(w, (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
    }

    public String worldName() {
        return worldName;
    }

    public Block block() {
        World w = org.bukkit.Bukkit.getWorld(worldName);
        return w == null ? null : w.getBlockAt(x, y, z);
    }

    public Block block(BlockFace face) {
        Block b = block();
        return b == null ? null : b.getRelative(face);
    }

    public boolean chunkLoaded() {
        World w = org.bukkit.Bukkit.getWorld(worldName);
        return w != null && w.isChunkLoaded(x >> 4, z >> 4);
    }

    public Location center() {
        Block b = block();
        return b == null ? null : b.getLocation().add(0.5, 0.0, 0.5);
    }

    public String flat() {
        return worldName + ";" + x + ";" + y + ";" + z;
    }

    public static LocationKey fromFlat(String s) {
        String[] p = s.split(";");
        if (p.length != 4) {
            return null;
        }
        try {
            return new LocationKey(org.bukkit.Bukkit.getWorld(p[0]),
                    Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LocationKey k)) {
            return false;
        }
        return worldHash == k.worldHash && x == k.x && y == k.y && z == k.z;
    }

    @Override
    public int hashCode() {
        return Objects.hash(worldHash, x, y, z);
    }

    @Override
    public String toString() {
        return String.format(Locale.US, "%s %d %d %d", worldName, x, y, z);
    }
}
