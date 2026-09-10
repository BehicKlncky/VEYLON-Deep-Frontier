package com.veylon.engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

@Tag("performance")
class AudioOcclusionPerformanceTest {
    /** Milliseconds for a complete four-ray frame allowance, excluding native filter submission. */
    private static final double BUDGET_MS = 0.10;
    private volatile float blackhole;

    @Test void aFullRayAllowanceStaysWithinBudget() {
        double best = Double.POSITIVE_INFINITY;
        com.veylon.world.World world = new com.veylon.world.World(42);
        for (int cx = 0; cx < 15; cx++) {
            var chunk = world.getOrCreateChunk(cx, 0);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                chunk.set(x, 95, z, com.veylon.world.BlockType.AIR);
        }
        AudioOcclusion.Voxels voxels = (x, y, z) -> world.getBlock(x, y, z).solid;
        var budget = new AudioOcclusion.Budget();
        for (int run = 0; run < 10; run++) {
            long start = System.nanoTime();
            float sum = 0;
            for (int frame = 0; frame < 10000; frame++) {
                budget.nextFrame();
                for (int ray = 0; ray < 4; ray++) sum += AudioOcclusion.trace(voxels, frame % 7, 95, 8, 220, 95, 8, budget);
            }
            blackhole = sum;
            if (run >= 3) best = Math.min(best, (System.nanoTime() - start) / 1e6 / 10000);
        }
        System.out.printf("benchmark audio occlusion %.6f ms/frame (budget %.2f; 4 rays, 256 probes)%n", best, BUDGET_MS);
        assertTrue(best <= BUDGET_MS);
        assertEquals(256, budget.peakQueries);
    }
}
