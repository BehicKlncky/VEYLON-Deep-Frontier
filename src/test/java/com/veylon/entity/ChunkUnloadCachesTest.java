package com.veylon.entity;

import com.veylon.engine.RainCollision;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The caches that hold chunk references, rain's ray and a body's collision, never answer from a chunk
 * the world dropped (urban milestone 24): the world's unload count flushes them, so a dropped column
 * reads as absent and a regenerated one as itself.
 */
class ChunkUnloadCachesTest {

    @Test
    void droppedChunksAreNeverAnsweredFromACachedReference() {
        World w = new World(20260731L, World.GEN_URBAN_ECONOMY);
        w.ensureChunks(88, 88, 2, 10_000);
        float x = 88.5f, z = 88.5f;
        RagdollCollision body = new RagdollCollision();
        RainCollision rain = new RainCollision();
        assertTrue(body.loaded(w, x, z));
        assertEquals(RainCollision.HIT, rain.trace(w, x, 90f, z, x, 1f, z), "rain meets the city");

        assertTrue(w.unloadChunk(Math.floorDiv(88, 16), Math.floorDiv(88, 16)));
        assertFalse(body.loaded(w, x, z), "a body's cache drops the column with the world");
        assertEquals(RainCollision.UNAVAILABLE, rain.trace(w, x, 90f, z, x, 1f, z), "and so does rain's");

        w.ensureChunks(88, 88, 0, 10_000);
        assertTrue(body.loaded(w, x, z));
        assertEquals(RainCollision.HIT, rain.trace(w, x, 90f, z, x, 1f, z), "the regenerated column answers again");
    }
}
