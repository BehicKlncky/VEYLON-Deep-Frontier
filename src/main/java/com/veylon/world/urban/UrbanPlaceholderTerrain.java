package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * Layout revision 1, generator {@code World.GEN_URBAN}: the street datum and
 * nothing else — solid stone up to the street, a flat stone-brick street at
 * {@link UrbanDatum#STREET_Y}, and open air above.
 *
 * <p>It exists so the urban branch has real, deterministic geometry before the
 * city planner does. Its output is frozen like every other generator's, because
 * saved generator-4 worlds re-derive it: the city that replaces it arrives
 * under a new generator version (see {@link UrbanRevision}).
 */
public final class UrbanPlaceholderTerrain {

    private UrbanPlaceholderTerrain() {
    }

    /** Stamps one chunk; writes only inside it, so chunk order cannot matter. */
    public static void stamp(Chunk chunk, float soilMoisture) {
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int y = 0; y < UrbanDatum.STREET_Y; y++) {
                    chunk.set(x, y, z, BlockType.STONE);
                }
                chunk.set(x, UrbanDatum.STREET_Y, z, BlockType.STONE_BRICK);
            }
        }
        chunk.moisture = soilMoisture;
    }
}
