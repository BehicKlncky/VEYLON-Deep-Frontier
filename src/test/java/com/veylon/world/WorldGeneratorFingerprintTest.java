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
 * ({@code World.CURRENT_GENERATOR}), so a change in generator output silently
 * rewrites the terrain under every existing save pinned to that version: the
 * shelter someone dug into a hillside opens onto a different hillside.
 *
 * <p>So these fingerprints are recorded constants. If a change moves one, that
 * is the test working. The response is a deliberate decision — either the
 * change was not meant to alter output and is wrong, or it was, and then
 * {@code CURRENT_GENERATOR} must be incremented so old saves keep their old
 * terrain, exactly as {@code GEN_DEEP} and {@code GEN_CAVE_IDENTITIES} did.
 * Re-recording the constant on its own is never the right answer.
 *
 * <p>Recorded 2026-07-31 against generator {@code GEN_CAVE_IDENTITIES} (3) at
 * {@code 3414dbf}, before the v0.5.0 generator optimizations.
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

    /** Chunks fingerprinted per seed: origin, offset, and two negative ones. */
    private static final int[][] CHUNKS = {{0, 0}, {3, -2}, {-1, -1}, {-4, 5}};

    @Test
    void generatedTerrainMatchesTheRecordedFingerprint() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long actual = terrainFingerprint(SEEDS[i]);
            if (actual != TERRAIN_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: recorded 0x%016xL, generated 0x%016xL",
                        SEEDS[i], TERRAIN_FINGERPRINTS[i], actual));
            }
        }
        assertTrue(moved.isEmpty(), () -> """
                Generator output changed for %d of %d seeds:
                  %s

                Terrain is part of the save contract: saves re-derive it from the
                seed plus World.CURRENT_GENERATOR, so this rewrites the world under
                every existing save pinned to generator %d.

                If the change was not meant to alter output, it is a bug -- a
                refactor or optimization must be bit-identical. If it was meant to,
                add a new generator version and leave the old one producing the old
                terrain, as GEN_DEEP and GEN_CAVE_IDENTITIES did. Updating these
                constants alone silently breaks saved worlds.""".formatted(
                moved.size(), SEEDS.length, String.join("\n  ", moved),
                World.CURRENT_GENERATOR));
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
        long current = terrainFingerprint(SEEDS[0], World.CURRENT_GENERATOR);

        assertNotEquals(legacy, current,
                "the legacy generator must not have collapsed onto the current one");
        assertEquals(legacy, terrainFingerprint(SEEDS[0], World.GEN_LEGACY),
                "the legacy generator must still be deterministic");
    }

    @Test
    void theFingerprintWouldNoticeASingleChangedBlock() {
        // A fingerprint that ignored most of the chunk would pass forever.
        World world = new World(SEEDS[0], World.CURRENT_GENERATOR);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        long before = hashChunk(chunk);

        int y = chunk.height(4, 4);
        chunk.set(4, y, 4, chunk.get(4, y, 4) == BlockType.STONE
                ? BlockType.DIRT : BlockType.STONE);

        assertNotEquals(before, hashChunk(chunk),
                "one changed block must move the fingerprint");
    }

    // ------------------------------------------------------------------

    private static long terrainFingerprint(long seed) {
        return terrainFingerprint(seed, World.CURRENT_GENERATOR);
    }

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
        World world = new World(seed, World.CURRENT_GENERATOR);
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
