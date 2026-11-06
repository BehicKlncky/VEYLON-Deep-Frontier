package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanDatum;

/**
 * A small two-room house built from the urban fixtures of package 04, on the
 * street datum: the stage for the {@code urban_fixtures} QA scenes and for the
 * headless fixture tests, so both look at the same geometry.
 *
 * <pre>
 *  z=7  B B B B B B B B B B B B B B
 *  z=6  B . . s s s S # h . c . l B      s: 2-wide stair flight up to the roof
 *  z=5  B . . s s s S # . . . . . B      S: last step, level with the roof slab
 *  z=4  B . . . . . . # . . . . . B      h, c, l: shelving, cabinet, locker
 *  z=3  B . . . . . . D . . T . . B      D: interior wooden door; T: table, lantern on top
 *  z=2  B . . . . . . # . . . . . B
 *  z=1  B . R . . . . # . r . . . B      R: rubble; r: debris
 *  z=0  B B B B M B B B B W W B B B      M: metal front door, W: windows over a sill
 *       x=0     x=4   x=7   x=10   x=13
 * </pre>
 *
 * Shelving, a cabinet and a locker stand along the east room's back wall. The
 * ground floor is clear y 41-43 under a concrete slab at y 44 that is open over
 * the stairs; the slab is the roof the stairs lead to. The front windows sit in
 * the storey's clear band over a one-block sill (A7), two cells tall so a body
 * can climb through.
 */
public final class FixtureShowcase {

    /** Footprint, in blocks. */
    public static final int WIDTH = 14, DEPTH = 8;
    /** How far the cleared street extends in front of the house. */
    public static final int FRONT_CLEARANCE = 7;

    /** Where things are; every position is a block cell. */
    public record Layout(int x0, int z0,
                         Vec3i frontDoor, Vec3i innerDoor, Vec3i window, Vec3i secondWindow,
                         Vec3i outsideFront, Vec3i outsideWindow, Vec3i westRoom, Vec3i eastRoom,
                         Vec3i stairFoot, Vec3i stairTop, Vec3i shelf, Vec3i cabinet, Vec3i locker,
                         Vec3i table, Vec3i lantern, Vec3i debris) {
    }

    private FixtureShowcase() {
    }

    /**
     * Clears a lot at (x0, z0) and builds the house. The chunks must be loaded.
     *
     * @param record true to write the blocks as saved edits (tests that save),
     *               false for QA staging that never saves
     */
    public static Layout build(World w, int x0, int z0, boolean record) {
        int floor = UrbanDatum.STREET_Y;
        int g = floor + 1;
        w.beginBatch();
        for (int x = x0 - 2; x < x0 + WIDTH + 2; x++) {
            for (int z = z0 - FRONT_CLEARANCE; z < z0 + DEPTH + 2; z++) {
                w.setBlock(x, floor, z, z < z0 ? BlockType.ASPHALT : BlockType.CONCRETE, record);
                for (int y = g; y <= g + 8; y++) {
                    w.setBlock(x, y, z, BlockType.AIR, record);
                }
            }
        }
        for (int x = x0; x < x0 + WIDTH; x++) {
            for (int z = z0; z < z0 + DEPTH; z++) {
                boolean outer = x == x0 || x == x0 + WIDTH - 1 || z == z0 || z == z0 + DEPTH - 1;
                for (int y = g; y < g + 3; y++) {
                    if (outer) {
                        w.setBlock(x, y, z, BlockType.BRICK, record);
                    } else if (x == x0 + 7) {
                        w.setBlock(x, y, z, BlockType.PLASTER, record);
                    }
                }
                // Roof slab, open over the stair flight (x0+2..x0+5, z0+5..z0+6).
                boolean stairwell = x >= x0 + 2 && x <= x0 + 5 && z >= z0 + 5 && z <= z0 + 6;
                w.setBlock(x, g + 3, z, stairwell ? BlockType.AIR : BlockType.CONCRETE, record);
            }
        }
        // Front door, two cells, under a brick lintel.
        put(w, x0 + 4, g, z0, BlockType.METAL_DOOR, record);
        put(w, x0 + 4, g + 1, z0, BlockType.METAL_DOOR, record);
        // Interior door through the plaster partition.
        put(w, x0 + 7, g, z0 + 3, BlockType.WOOD_DOOR, record);
        put(w, x0 + 7, g + 1, z0 + 3, BlockType.WOOD_DOOR, record);
        // Two windows over a one-block sill, in the storey's clear band.
        for (int x = x0 + 9; x <= x0 + 10; x++) {
            put(w, x, g + 1, z0, BlockType.WINDOW, record);
            put(w, x, g + 2, z0, BlockType.WINDOW, record);
        }
        // Stairs: full-block steps rising one per cell along +x, two wide.
        for (int i = 0; i < 4; i++) {
            for (int z = z0 + 5; z <= z0 + 6; z++) {
                for (int y = g; y <= g + i; y++) {
                    put(w, x0 + 3 + i, y, z, BlockType.STAIRS, record);
                }
            }
        }
        // The east room's furniture.
        put(w, x0 + 8, g, z0 + 6, BlockType.SHELF, record);
        put(w, x0 + 10, g, z0 + 6, BlockType.CABINET, record);
        put(w, x0 + 12, g, z0 + 6, BlockType.LOCKER, record);
        put(w, x0 + 10, g, z0 + 3, BlockType.TABLE, record);
        put(w, x0 + 10, g + 1, z0 + 3, BlockType.LANTERN, record);
        put(w, x0 + 9, g, z0 + 1, BlockType.DEBRIS, record);
        put(w, x0 + 2, g, z0 + 1, BlockType.RUBBLE, record);
        w.endBatch();

        return new Layout(x0, z0,
                new Vec3i(x0 + 4, g, z0), new Vec3i(x0 + 7, g, z0 + 3),
                new Vec3i(x0 + 9, g + 1, z0), new Vec3i(x0 + 10, g + 1, z0),
                new Vec3i(x0 + 4, g, z0 - 2), new Vec3i(x0 + 9, g, z0 - 2),
                new Vec3i(x0 + 4, g, z0 + 2), new Vec3i(x0 + 9, g, z0 + 2),
                new Vec3i(x0 + 2, g, z0 + 5), new Vec3i(x0 + 8, g + 4, z0 + 2),
                new Vec3i(x0 + 8, g, z0 + 6), new Vec3i(x0 + 10, g, z0 + 6),
                new Vec3i(x0 + 12, g, z0 + 6), new Vec3i(x0 + 10, g, z0 + 3),
                new Vec3i(x0 + 10, g + 1, z0 + 3), new Vec3i(x0 + 9, g, z0 + 1));
    }

    private static void put(World w, int x, int y, int z, BlockType t, boolean record) {
        w.setBlock(x, y, z, t, record);
    }
}
