package com.veylon.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what the generator actually produces, against recorded values.
 *
 * <h2>Why this is different from the determinism tests</h2>
 *
 * <p>{@code DeepCaveGenTest.sameSeedProducesIdenticalDeepChunks} builds two
 * worlds from one seed <em>with the same build</em> and compares them. That
 * catches order dependence and unseeded state, and it is worth having — but it
 * compares the code against itself. Reshape every world on the planet and it
 * stays green, because both sides move together.
 *
 * <p>Terrain is not an implementation detail here. It is the save format. Saves
 * store deltas and re-derive structure from the seed and the generator version
 * recorded in the save, so a change in generator output silently rewrites the
 * terrain under every existing save pinned to that version: the shelter
 * someone dug into a hillside opens onto a different hillside.
 *
 * <p>So these fingerprints are recorded constants. If a change moves one, that
 * is the test working. The response is a deliberate decision — either the
 * change was not meant to alter output and is wrong, or it was, and then a new
 * generator version must be added so old saves keep their old terrain, exactly
 * as {@code GEN_DEEP} and {@code GEN_CAVE_IDENTITIES} did. Re-recording the
 * constant on its own is never the right answer.
 *
 * <p>Each pin names its generator explicitly rather than "the current one":
 * the generator new worlds use and the generators a save may name are
 * different questions, and a pin that followed the default would silently
 * re-point at a new generator when the default moves.
 *
 * <p>Generator {@code GEN_CAVE_IDENTITIES} (3) was recorded 2026-07-31 at
 * {@code 3414dbf}, before the v0.5.0 generator optimizations. Generators
 * {@code GEN_LEGACY} (1) and {@code GEN_DEEP} (2) were recorded 2026-10-07 at
 * {@code 923345f}, before the urban generator split, from unchanged generator
 * code.
 */
class WorldGeneratorFingerprintTest {

    /**
     * Seeds chosen to cover contrasting ground: one arbitrary, one that places
     * chunks at negative coordinates (where floor-versus-truncate bugs live),
     * and one small seed. Each fingerprint below is over four chunks.
     */
    private static final long[] SEEDS = {20_260_731L, -4_242L, 7L};

    private static final long[] TERRAIN_FINGERPRINTS = {
            0xd9696cfbb47f6234L, 0x677f1e5e437f686eL, 0xee26b953c30e802dL};

    private static final long[] COLUMN_QUERY_FINGERPRINTS = {
            0xc7e914449117bdd9L, 0x82273c0c999fcdc6L, 0xde29e867023f323cL};

    /** Generator 1, which every v2 save and gen-1 v3 save re-derives. */
    private static final long[] LEGACY_TERRAIN_FINGERPRINTS = {
            0x575ff79c72c5e75cL, 0xaf2846b2fa8fb028L, 0xd53be542e8a9ac72L};

    /** Generator 2, the pre-identity 0.3.0 development generator. */
    private static final long[] DEEP_TERRAIN_FINGERPRINTS = {
            0x0ac52031ca6ac5bcL, 0x23bc80e5d60717e2L, 0xb40fceda52cad351L};

    /** Chunks fingerprinted per seed: origin, offset, and two negative ones. */
    private static final int[][] CHUNKS = {{0, 0}, {3, -2}, {-1, -1}, {-4, 5}};

    @Test
    void generatedTerrainMatchesTheRecordedFingerprint() {
        assertTerrainPinned(World.GEN_CAVE_IDENTITIES, TERRAIN_FINGERPRINTS);
    }

    @Test
    void olderGeneratorsMatchTheirRecordedFingerprints() {
        // Before these, generators 1 and 2 were pinned only relative to each
        // other or through one historical save, so a shared refactor could move
        // both and stay green.
        assertTerrainPinned(World.GEN_LEGACY, LEGACY_TERRAIN_FINGERPRINTS);
        assertTerrainPinned(World.GEN_DEEP, DEEP_TERRAIN_FINGERPRINTS);
    }

    private static void assertTerrainPinned(int generatorVersion, long[] recorded) {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long actual = terrainFingerprint(SEEDS[i], generatorVersion);
            if (actual != recorded[i]) {
                moved.add(String.format("seed %d: recorded 0x%016xL, generated 0x%016xL",
                        SEEDS[i], recorded[i], actual));
            }
        }
        assertTrue(moved.isEmpty(), () -> """
                Generator %d output changed for %d of %d seeds:
                  %s

                Terrain is part of the save contract: saves re-derive it from the
                seed plus the generator version they recorded, so this rewrites the
                world under every existing save pinned to generator %d.

                If the change was not meant to alter output, it is a bug -- a
                refactor or optimization must be bit-identical. If it was meant to,
                add a new generator version and leave the old one producing the old
                terrain, as GEN_DEEP and GEN_CAVE_IDENTITIES did. Updating these
                constants alone silently breaks saved worlds.""".formatted(
                generatorVersion, moved.size(), SEEDS.length, String.join("\n  ", moved),
                generatorVersion));
    }

    @Test
    void columnQueriesMatchTheRecordedFingerprint() {
        // heightAt, biomeAt and the cave-zone derivation are consulted far
        // outside chunk generation -- by settlement planning, spawn placement,
        // plant growth and temperature. They are fingerprinted separately so a
        // change there is attributed to the query rather than to the terrain.
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long actual = columnQueryFingerprint(SEEDS[i]);
            if (actual != COLUMN_QUERY_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: recorded 0x%016xL, generated 0x%016xL",
                        SEEDS[i], COLUMN_QUERY_FINGERPRINTS[i], actual));
            }
        }
        assertTrue(moved.isEmpty(),
                () -> "per-column terrain queries changed:\n  " + String.join("\n  ", moved));
    }

    @Test
    void legacyGeneratorsStillProduceTheirOwnTerrain() {
        // The whole point of the generator version is that an old save keeps
        // its world. Two versions of one seed must disagree, and each must
        // agree with itself.
        long legacy = terrainFingerprint(SEEDS[0], World.GEN_LEGACY);
        long identities = terrainFingerprint(SEEDS[0], World.GEN_CAVE_IDENTITIES);

        assertNotEquals(legacy, identities,
                "the legacy generator must not have collapsed onto generator 3");
        assertEquals(legacy, terrainFingerprint(SEEDS[0], World.GEN_LEGACY),
                "the legacy generator must still be deterministic");
    }

    @Test
    void theFingerprintWouldNoticeASingleChangedBlock() {
        // A fingerprint that ignored most of the chunk would pass forever.
        World world = new World(SEEDS[0], World.GEN_CAVE_IDENTITIES);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        long before = hashChunk(chunk);

        int y = chunk.height(4, 4);
        chunk.set(4, y, 4, chunk.get(4, y, 4) == BlockType.STONE
                ? BlockType.DIRT : BlockType.STONE);

        assertNotEquals(before, hashChunk(chunk),
                "one changed block must move the fingerprint");
    }

    // ------------------------------------------------------------------

    private static long terrainFingerprint(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = 0xcbf29ce484222325L;
        for (int[] chunk : CHUNKS) {
            hash = mix(hash, hashChunk(world.getOrCreateChunk(chunk[0], chunk[1])));
        }
        // POIs are placed by generation and persisted by position, so they are
        // part of what a save re-derives.
        List<String> pois = new ArrayList<>();
        for (Poi poi : world.pois) {
            pois.add(poi.type + "@" + poi.pos);
        }
        pois.sort(null);
        for (String poi : pois) {
            hash = mix(hash, poi.hashCode());
        }
        return hash;
    }

    /** Every block plus the derived heightmap of one chunk. */
    private static long hashChunk(Chunk chunk) {
        long hash = 0xcbf29ce484222325L;
        for (int y = 0; y < Chunk.SY; y++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int x = 0; x < Chunk.SX; x++) {
                    hash = mix(hash, chunk.get(x, y, z).ordinal());
                }
            }
        }
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                hash = mix(hash, chunk.height(x, z));
            }
        }
        return mix(hash, Float.floatToIntBits(chunk.moisture));
    }

    /** The pure per-column queries, sampled over a wide, sparse grid. */
    private static long columnQueryFingerprint(long seed) {
        World world = new World(seed, World.GEN_CAVE_IDENTITIES);
        WorldGenerator generator = world.generator;
        long hash = 0xcbf29ce484222325L;
        for (int x = -600; x <= 600; x += 37) {
            for (int z = -600; z <= 600; z += 41) {
                int height = generator.heightAt(x, z);
                hash = mix(hash, height);
                hash = mix(hash, generator.biomeAt(x, z).ordinal());
                hash = mix(hash, Double.doubleToLongBits(generator.mountainFactor(x, z)));
                hash = mix(hash, Double.doubleToLongBits(generator.temperature01(x, z)));
                hash = mix(hash, Double.doubleToLongBits(generator.moisture01(x, z)));
                hash = mix(hash, generator.caveZoneAt(x, height - 20, z).ordinal());
            }
        }
        return hash;
    }

    /** FNV-1a style mixing: order-sensitive and cheap. */
    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }
}
