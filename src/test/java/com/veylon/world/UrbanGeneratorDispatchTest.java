package com.veylon.world;

import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The urban generator is reached by exact dispatch and inherits nothing from
 * generators 1-3, and generators 1-3 keep their own gates (decision D-02).
 */
class UrbanGeneratorDispatchTest {

    /** The candidate urban design seeds recorded in DESIGN §11. */
    private static final long[] SEEDS = {20_260_731L, -4_242L, 7L, 0L, 987_654_321L,
            -9_000_000_000_000_000_000L};

    /** Origin, negatives, a region seam and the far pair at about ±4096 regions. */
    private static final int[][] CHUNKS = {{0, 0}, {-1, -1}, {3, -2}, {-4, 5}, {15, 16},
            {65_536, -65_536}, {-65_537, 65_535}};

    @Test
    void generatorGatesNameTheirVersionsExplicitly() {
        assertEquals(World.GEN_CAVE_IDENTITIES, World.DEFAULT_NEW_WORLD_GENERATOR,
                "ordinary new worlds stay on generator 3 until the urban rollout (package 26)");
        assertEquals(4, World.GEN_URBAN, "the first unused version after 1-3");
        assertEquals(5, World.GEN_URBAN_STREETS, "the street network got its own version (D-35)");
        assertEquals(6, World.GEN_URBAN_PLANS);
        assertEquals(7, World.GEN_URBAN_APARTMENTS);
        assertEquals(8, World.GEN_URBAN_SHAPED_STAIRS);
        assertEquals(9, World.GEN_URBAN_STARTER);
        assertEquals(10, World.GEN_URBAN_ENCOUNTERS);
        assertEquals(11, World.GEN_URBAN_CIVIC, "the commercial and civic catalogue (D-98)");
        assertEquals(12, World.GEN_URBAN_DISTRICTS, "the six districts (milestone 13)");
        assertEquals(13, World.GEN_URBAN_UNDERGROUND, "underground and rooftop routes (milestone 14)");
        assertEquals(14, World.GEN_URBAN_ECOLOGY, "alien ecology and city wildlife (milestone 15)");
        assertEquals(15, World.GEN_URBAN_SURVIVORS, "survivor camps and hidden civilians (milestone 16)");
        assertEquals(16, World.GEN_URBAN_STRONGHOLDS, "guarded strongholds and a raider yard (milestone 17)");
        assertEquals(17, World.GEN_URBAN_REFUGES, "safehouses, salvage and fortification (milestone 18)");
        assertEquals(18, World.GEN_URBAN_UTILITIES, "water, power and refuge utilities (milestone 19)");
        assertEquals(19, World.GEN_URBAN_MISSIONS, "urban missions and the long-term objective (milestone 20)");
        assertEquals(20, World.GEN_URBAN_ECONOMY, "the survival economy (milestone 23)");
        assertEquals(World.GEN_URBAN_ECONOMY, World.MAX_SUPPORTED_GENERATOR);
        for (int version = -1; version <= World.MAX_SUPPORTED_GENERATOR + 2; version++) {
            boolean known = version >= World.GEN_LEGACY && version <= World.MAX_SUPPORTED_GENERATOR;
            assertEquals(known, World.isSupportedGenerator(version), "supported " + version);
            assertEquals(version == World.GEN_URBAN || version == World.GEN_URBAN_STREETS
                            || version == World.GEN_URBAN_PLANS || version == World.GEN_URBAN_APARTMENTS
                            || version == World.GEN_URBAN_SHAPED_STAIRS || version == World.GEN_URBAN_STARTER
                            || version == World.GEN_URBAN_ENCOUNTERS || version == World.GEN_URBAN_CIVIC
                            || version == World.GEN_URBAN_DISTRICTS || version == World.GEN_URBAN_UNDERGROUND
                            || version == World.GEN_URBAN_ECOLOGY || version == World.GEN_URBAN_SURVIVORS
                            || version == World.GEN_URBAN_STRONGHOLDS || version == World.GEN_URBAN_REFUGES
                            || version == World.GEN_URBAN_UTILITIES || version == World.GEN_URBAN_MISSIONS
                            || version == World.GEN_URBAN_ECONOMY,
                    World.isUrbanGenerator(version), "urban " + version);
            assertEquals(version == World.GEN_DEEP || version == World.GEN_CAVE_IDENTITIES,
                    World.hasDeepFrontier(version), "deep frontier " + version);
            assertEquals(version == World.GEN_CAVE_IDENTITIES, World.hasCaveIdentities(version));
        }
    }

    @Test
    void onlyUrbanWorldsCarryUrbanState() {
        for (int version : new int[]{World.GEN_LEGACY, World.GEN_DEEP, World.GEN_CAVE_IDENTITIES}) {
            World world = new World(1L, version);
            assertFalse(world.isUrban());
            assertNull(world.urban);
        }
        World urban = new World(1L, World.GEN_URBAN);
        assertTrue(urban.isUrban());
        assertNotNull(urban.urban);
        assertEquals(UrbanRevision.LAYOUT_DATUM_PLACEHOLDER, urban.urban.layoutRevision());
        assertFalse(urban.urban.contentRevisionResolved(),
                "a bare World does not know which content revision a caller will restore");
    }

    @Test
    void aWorldRefusesAGeneratorThisBuildCannotBuild() {
        for (int version : new int[]{0, -3, World.MAX_SUPPORTED_GENERATOR + 1, 99}) {
            assertThrows(IllegalArgumentException.class, () -> new World(1L, version));
        }
    }

    @Test
    void urbanChunksAreTheStreetDatumAndNothingElse() {
        for (long seed : SEEDS) {
            World world = new World(seed, World.GEN_URBAN);
            for (int[] at : CHUNKS) {
                Chunk chunk = world.getOrCreateChunk(at[0], at[1]);
                for (int x = 0; x < Chunk.SX; x++) {
                    for (int z = 0; z < Chunk.SZ; z++) {
                        assertEquals(UrbanDatum.STREET_Y, chunk.height(x, z));
                        for (int y = 0; y < Chunk.SY; y++) {
                            BlockType expected = y < UrbanDatum.STREET_Y ? BlockType.STONE
                                    : y == UrbanDatum.STREET_Y ? BlockType.STONE_BRICK
                                    : BlockType.AIR;
                            if (chunk.get(x, y, z) != expected) {
                                throw new AssertionError("seed " + seed + " chunk " + at[0] + ","
                                        + at[1] + " block " + x + "," + y + "," + z + " is "
                                        + chunk.get(x, y, z) + ", expected " + expected);
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    void noWildernessFeatureReachesAnUrbanWorld() {
        // A naive version 4 would have inherited caves, ores, POIs, cave mouths,
        // settlements, fumaroles and trees through the old >= comparisons.
        World world = new World(20_260_731L, World.GEN_URBAN);
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                world.getOrCreateChunk(cx, cz);
            }
        }
        assertTrue(world.pois.isEmpty(), "no surface or underground POI");
        assertTrue(world.pendingGen.isEmpty(), "no decoration spilled across a chunk border");
        assertTrue(world.crateContents.isEmpty(), "no generated loot crate");
        for (int rx = -3; rx <= 3; rx++) {
            for (int rz = -3; rz <= 3; rz++) {
                assertNull(world.settlementForRegion(rx, rz), "legacy settlement planned");
            }
        }
        assertTrue(world.settlements.isEmpty());
        for (int x = -90; x <= 90; x += 9) {
            for (int z = -90; z <= 90; z += 9) {
                assertFalse(world.isBasaltFumarole(x, UrbanDatum.STREET_Y + 1, z));
            }
        }
    }

    @Test
    void urbanChunksDoNotDependOnGenerationOrder() {
        List<int[]> chunks = new ArrayList<>();
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        World forward = new World(-4_242L, World.GEN_URBAN);
        World reverse = new World(-4_242L, World.GEN_URBAN);
        for (int[] chunk : chunks) {
            forward.getOrCreateChunk(chunk[0], chunk[1]);
        }
        for (int i = chunks.size() - 1; i >= 0; i--) {
            reverse.getOrCreateChunk(chunks.get(i)[0], chunks.get(i)[1]);
        }
        for (int[] chunk : chunks) {
            assertArrayEquals(blocks(forward.getChunk(chunk[0], chunk[1])),
                    blocks(reverse.getChunk(chunk[0], chunk[1])));
            assertEquals(forward.getChunk(chunk[0], chunk[1]).moisture,
                    reverse.getChunk(chunk[0], chunk[1]).moisture);
        }
    }

    private static byte[] blocks(Chunk chunk) {
        byte[] out = new byte[Chunk.SX * Chunk.SY * Chunk.SZ];
        int i = 0;
        for (int y = 0; y < Chunk.SY; y++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int x = 0; x < Chunk.SX; x++) {
                    out[i++] = (byte) chunk.get(x, y, z).ordinal();
                }
            }
        }
        return out;
    }
}
