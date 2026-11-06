package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * Stamps one chunk's slice of a district plan (layout revision 2).
 *
 * <p>Every write lands inside the chunk being generated: the plan already
 * knows each column's surface and every planned tree, so a chunk reads what it
 * needs and never queues an edit into a neighbour ({@code pendingGen} stays
 * empty). A region is a whole number of chunks, so a chunk needs exactly one
 * plan, and its blocks are the same whichever chunk generated first. Work per
 * chunk is bounded: 256 columns plus the region's few dozen trees.
 */
public final class UrbanChunkStamper {

    private UrbanChunkStamper() {
    }

    /**
     * Stamps a chunk from the plan of the region that contains it.
     *
     * @throws IllegalArgumentException if the chunk is not inside the plan's region
     */
    public static void stamp(Chunk chunk, DistrictPlan plan, float soilMoisture) {
        int baseX = chunk.cx * Chunk.SX;
        int baseZ = chunk.cz * Chunk.SZ;
        if (!plan.contains(baseX, baseZ)) {
            throw new IllegalArgumentException("chunk " + chunk.cx + "," + chunk.cz
                    + " is outside region " + plan.rx + "," + plan.rz);
        }
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                fillColumn(chunk, lx, lz, UrbanSurfaceMaterials.blockFor(
                        plan.surfaceAt(baseX + lx, baseZ + lz)));
            }
        }
        int reach = DistrictPlan.Tree.CANOPY_RADIUS;
        for (DistrictPlan.Tree tree : plan.trees()) {
            if (tree.x() + reach >= baseX && tree.x() - reach < baseX + Chunk.SX
                    && tree.z() + reach >= baseZ && tree.z() - reach < baseZ + Chunk.SZ) {
                stampTree(chunk, baseX, baseZ, tree);
            }
        }
        chunk.moisture = soilMoisture;
    }

    /**
     * The flat street datum for a chunk no plan covers: a region beyond the
     * range urban ids can name.
     */
    public static void stampDatum(Chunk chunk, float soilMoisture) {
        BlockType top = UrbanSurfaceMaterials.blockFor(UrbanSurface.DATUM);
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                fillColumn(chunk, lx, lz, top);
            }
        }
        chunk.moisture = soilMoisture;
    }

    private static void fillColumn(Chunk chunk, int lx, int lz, BlockType top) {
        for (int y = 0; y < UrbanDatum.STREET_Y; y++) {
            chunk.set(lx, y, lz, UrbanSurfaceMaterials.FILL);
        }
        chunk.set(lx, UrbanDatum.STREET_Y, lz, top);
    }

    /**
     * A trunk of {@code trunkHeight} logs on the street datum and a rounded
     * canopy whose lowest leaves sit at least three cells above the ground, so
     * it never narrows the walkable space under it. Only cells inside the
     * chunk are written.
     */
    private static void stampTree(Chunk chunk, int baseX, int baseZ, DistrictPlan.Tree tree) {
        int ground = UrbanDatum.STREET_Y;
        int top = ground + tree.trunkHeight();
        int radius = DistrictPlan.Tree.CANOPY_RADIUS;
        for (int y = top - 1; y <= top + 1; y++) {
            int r = y == top + 1 ? 1 : radius;
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean corner = Math.abs(dx) == r && Math.abs(dz) == r;
                    if (!corner) {
                        set(chunk, baseX, baseZ, tree.x() + dx, y, tree.z() + dz, UrbanSurfaceMaterials.CANOPY);
                    }
                }
            }
        }
        for (int y = ground + 1; y <= top; y++) {
            set(chunk, baseX, baseZ, tree.x(), y, tree.z(), UrbanSurfaceMaterials.TRUNK);
        }
    }

    private static void set(Chunk chunk, int baseX, int baseZ, int x, int y, int z, BlockType type) {
        int lx = x - baseX;
        int lz = z - baseZ;
        if (lx >= 0 && lx < Chunk.SX && lz >= 0 && lz < Chunk.SZ) {
            chunk.set(lx, y, lz, type);
        }
    }
}
