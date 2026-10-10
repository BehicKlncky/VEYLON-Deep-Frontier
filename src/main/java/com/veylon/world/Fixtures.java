package com.veylon.world;

/**
 * Doors and windows as block-id families, and the openings they form.
 *
 * <p>A fixture kind has a closed, an open and a broken block id (decision
 * D-18, recorded by urban package 04). Collision, raycast, meshing and
 * navigation all read those ids, so they agree by construction: a closed door
 * is a solid full cell, an open one is a non-solid leaf, and a broken one is a
 * non-solid frame that can never be closed again. State that no id can
 * express, a lock or a bar, lives in {@link FixtureStates}.
 *
 * <p>An opening is a vertical stack of one kind: a door is two cells tall, a
 * window one to three. Opening, closing, locking and forcing a door act on the
 * whole stack; a window pane shatters one cell at a time. A stack is at most
 * {@link #MAX_STACK} cells, found by walking at most two cells down from the
 * cell the player touched.
 */
public final class Fixtures {

    /** Tallest opening that acts as one fixture. */
    public static final int MAX_STACK = 3;

    /** The operable fixture families. Not persisted: only block ids are saved. */
    public enum Kind {
        WOOD_DOOR(BlockType.WOOD_DOOR, BlockType.WOOD_DOOR_OPEN, BlockType.BROKEN_DOOR, true),
        METAL_DOOR(BlockType.METAL_DOOR, BlockType.METAL_DOOR_OPEN, BlockType.BROKEN_DOOR, true),
        WINDOW(BlockType.WINDOW, BlockType.WINDOW_OPEN, BlockType.BROKEN_WINDOW, false);

        public final BlockType closed;
        public final BlockType open;
        public final BlockType broken;
        public final boolean door;

        Kind(BlockType closed, BlockType open, BlockType broken, boolean door) {
            this.closed = closed;
            this.open = open;
            this.broken = broken;
            this.door = door;
        }

        /** The block of this kind in the given state. */
        public BlockType variant(boolean opened) {
            return opened ? open : closed;
        }
    }

    private Fixtures() {
    }

    /** The kind of a closed or open door or window; null for anything else, broken ones included. */
    public static Kind kindOf(BlockType t) {
        return switch (t) {
            case WOOD_DOOR, WOOD_DOOR_OPEN -> Kind.WOOD_DOOR;
            case METAL_DOOR, METAL_DOOR_OPEN -> Kind.METAL_DOOR;
            case WINDOW, WINDOW_OPEN -> Kind.WINDOW;
            default -> null;
        };
    }

    /** True for a door or window the player or a resident can open, close, lock or bar. */
    public static boolean isOperable(BlockType t) {
        return kindOf(t) != null;
    }

    public static boolean isOpen(BlockType t) {
        Kind k = kindOf(t);
        return k != null && t == k.open;
    }

    public static boolean isClosed(BlockType t) {
        Kind k = kindOf(t);
        return k != null && t == k.closed;
    }

    /** A closed door: the only fixture a resident shoulders open on a path. */
    public static boolean isClosedDoor(BlockType t) {
        return t == BlockType.WOOD_DOOR || t == BlockType.METAL_DOOR;
    }

    /** The irreversible remains of a forced door or a shattered pane. */
    public static boolean isBroken(BlockType t) {
        return t == BlockType.BROKEN_DOOR || t == BlockType.BROKEN_WINDOW;
    }

    /** Any door or window block, whole or broken: it reads as part of a wall. */
    public static boolean isFixture(BlockType t) {
        return isOperable(t) || isBroken(t);
    }

    /**
     * Urban furniture searched through the crate backend until package 07
     * replaces it. The legacy {@link BlockType#CRATE} keeps its own rules.
     */
    public static boolean isUrbanContainer(BlockType t) {
        return t == BlockType.SHELF || t == BlockType.CABINET || t == BlockType.LOCKER;
    }

    /** Every block that opens the storage screen: the legacy crate and the urban containers. */
    public static boolean isContainer(BlockType t) {
        return t == BlockType.CRATE || isUrbanContainer(t);
    }

    /**
     * The lowest cell of the opening of {@code kind} that contains {@code y}.
     * Walks down at most {@code MAX_STACK - 1} cells.
     */
    public static int stackBottom(World w, int x, int y, int z, Kind kind) {
        int bottom = y;
        for (int i = 1; i < MAX_STACK && kindOf(w.getBlock(x, y - i, z)) == kind; i++) {
            bottom = y - i;
        }
        return bottom;
    }

    /** The number of cells of {@code kind} from {@code bottom} up, at most {@link #MAX_STACK}. */
    public static int stackHeight(World w, int x, int bottom, int z, Kind kind) {
        int h = 0;
        while (h < MAX_STACK && kindOf(w.getBlock(x, bottom + h, z)) == kind) {
            h++;
        }
        return h;
    }
}
