package me.arena.electronics.registry;

import org.bukkit.block.BlockFace;

/**
 * Направление «вдоль» компонента. 0..3 — горизонтальные (по грани блока,
 * на который он поставили / по взгляду игрока), 4 — вверх, 5 — вниз.
 */
public enum Orientation {

    NORTH(BlockFace.NORTH, BlockFace.WEST, BlockFace.EAST, 0),
    EAST(BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH, 1),
    SOUTH(BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, 2),
    WEST(BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH, 3),
    UP(BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, 4),
    DOWN(BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH, 5);

    /** Вдоль компонента (вывод 1 -> вывод 2). */
    public final BlockFace axis;
    /** «Левый» вывод. */
    public final BlockFace left;
    /** «Правый» вывод. */
    public final BlockFace right;
    public final int index;

    Orientation(BlockFace axis, BlockFace left, BlockFace right, int index) {
        this.axis = axis;
        this.left = left;
        this.right = right;
        this.index = index;
    }

    public float yaw() {
        return switch (this) {
            case NORTH -> 180.0f;
            case SOUTH -> 0.0f;
            case EAST -> 270.0f;
            case WEST -> 90.0f;
            case UP, DOWN -> 0.0f;
        };
    }

    public static Orientation byIndex(int i) {
        Orientation[] v = values();
        return (i < 0 || i >= v.length) ? NORTH : v[i];
    }

    public static Orientation fromYaw(float yaw) {
        float a = ((yaw % 360) + 360) % 360;
        if (a >= 315 || a < 45) {
            return SOUTH;
        }
        if (a < 135) {
            return WEST;
        }
        if (a < 225) {
            return NORTH;
        }
        return EAST;
    }

    public static Orientation fromBlockFace(BlockFace face) {
        for (Orientation o : values()) {
            if (o.axis == face) {
                return o;
            }
        }
        return NORTH;
    }
}
