package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Streaming builds each chunk mesh once (urban milestone 24): it generates one ring beyond the render
 * radius, and the renderer takes only dirty chunks whose eight neighbours exist, nearest first,
 * within the frame's budget, reading only the render window and reusing one list.
 */
class ChunkStreamingScheduleTest {

    @Test
    void theRendererTakesOnlyFinishedNeighbourhoodsNearestFirst() {
        Game g = new Game();
        g.newWorld(20260731L, true, World.GEN_URBAN_ECONOMY, GameMode.CREATIVE);
        int r = g.renderer.renderRadius();
        int pcx = Math.floorDiv((int) g.player.pos.x, 16), pcz = Math.floorDiv((int) g.player.pos.z, 16);
        g.world.ensureChunks((int) g.player.pos.x, (int) g.player.pos.z, r, 10_000); // the render window, not the ring
        List<Chunk> first = g.renderer.selectDirty(g.world, pcx, pcz, 10_000);
        assertFalse(first.isEmpty());
        int lastSteps = -1;
        for (Chunk c : first) {
            int steps = Math.abs(c.cx - pcx) + Math.abs(c.cz - pcz);
            assertTrue(steps >= lastSteps, "nearest first by chunk steps");
            lastSteps = steps;
        }
        int waiting = 0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                Chunk c = g.world.getChunk(pcx + dx, pcz + dz);
                boolean ready = true;
                for (int nx = -1; nx <= 1; nx++) {
                    for (int nz = -1; nz <= 1; nz++) { ready &= g.world.getChunk(c.cx + nx, c.cz + nz) != null; }
                }
                assertEquals(ready, first.contains(c), "taken exactly when its neighbourhood is finished: " + c.cx + "," + c.cz);
                waiting += ready ? 0 : 1;
            }
        }
        assertTrue(waiting > 0, "some edge chunks wait for the ring beyond");
        g.world.ensureChunks((int) g.player.pos.x, (int) g.player.pos.z, ChunkStreaming.generationRadius(r), 10_000);
        List<Chunk> all = g.renderer.selectDirty(g.world, pcx, pcz, 10_000);
        assertEquals((2 * r + 1) * (2 * r + 1), all.size(), "with the ring generated, the whole window is ready");
        assertSame(first, all, "one reused list: selection allocates nothing");
        assertEquals(ChunkStreaming.MESH_PER_FRAME, g.renderer.selectDirty(g.world, pcx, pcz, ChunkStreaming.MESH_PER_FRAME).size());
        // The window is all it reads: chunks far beyond it, dirty or not, never come up.
        g.world.ensureChunks((int) g.player.pos.x + 4096, (int) g.player.pos.z, 3, 10_000);
        for (Chunk c : g.renderer.selectDirty(g.world, pcx, pcz, 10_000)) {
            assertTrue(Math.max(Math.abs(c.cx - pcx), Math.abs(c.cz - pcz)) <= r);
        }
        assertEquals(0, g.renderer.selectVisible(g.world, pcx, pcz), "nothing is meshed headless");
    }
}
