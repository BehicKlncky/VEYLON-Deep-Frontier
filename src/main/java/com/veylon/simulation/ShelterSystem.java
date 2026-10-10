package com.veylon.simulation;

import com.veylon.world.BlockType;
import com.veylon.world.World;

/**
 * Evaluates how protected a position is: roof above, wall enclosure around,
 * and whether it counts as "indoors". Used for wetness, wind chill, sleep
 * quality and campfire smoke buildup.
 */
public final class ShelterSystem {
    private static final int[][] DIRECTIONS = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    /** Result of a shelter scan. */
    public record Shelter(boolean roofed, float enclosure, boolean indoor) {

        /** Overall rain/wind coverage 0..1. */
        public float coverage() {
            return (roofed ? 0.55f : 0f) + enclosure * 0.45f;
        }

        public String label() {
            if (indoor) {
                return "Indoors";
            }
            if (roofed) {
                return "Sheltered";
            }
            return enclosure > 0.5f ? "Windbreak" : "Exposed";
        }
    }

    public static final Shelter OPEN = new Shelter(false, 0f, false);

    private ShelterSystem() {
    }

    /** Scans the voxel surroundings of a (feet-level) position. */
    public static Shelter evaluate(World world, float fx, float fy, float fz) {
        int x = (int) Math.floor(fx);
        int y = (int) Math.floor(fy);
        int z = (int) Math.floor(fz);
        boolean generatedUrban = world.urban != null && world.urban.hasGeneratedBuildings();
        if (generatedUrban && !world.isColumnLoaded(x, z)) { return OPEN; }

        // Generated rooms use the live whole-column roof/closed-glass bound.
        // Keep the legacy fourteen-cell opaque roof scan for older generators.
        boolean roofed = generatedUrban && !world.isSkyExposed(x, y + 1, z);
        if (!generatedUrban) {
            for (int dy = 2; dy <= 14; dy++) {
                BlockType t = world.getBlock(x, y + dy, z);
                if (t.opaque) {
                    roofed = true;
                    break;
                }
            }
        }

        // Real planned rooms can be wider than the legacy four-block scan.
        // Plan metadata selects the bound; live rays still observe destroyed
        // partitions, opened windows and unloaded neighboring columns.
        var location = generatedUrban && roofed ? world.urbanLocation(x, y, z) : null;
        int wallRange = location != null && location.room() != null ? 32 : 4;

        // Eight horizontal rays, checked at two heights so single-block
        // fences do not count as full walls.
        int blocked = 0;
        for (int[] d : DIRECTIONS) {
            for (int dist = 1; dist <= wallRange; dist++) {
                int bx = x + d[0] * dist, bz = z + d[1] * dist;
                if (generatedUrban && !world.isColumnLoaded(bx, bz)) { break; }
                if (world.getBlock(bx, y, bz).solid && world.getBlock(bx, y + 1, bz).solid) {
                    blocked++;
                    break;
                }
            }
        }
        float enclosure = blocked / 8f;
        boolean indoor = roofed && enclosure >= 0.72f;
        return new Shelter(roofed, enclosure, indoor);
    }
}
