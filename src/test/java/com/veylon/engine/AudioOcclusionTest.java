package com.veylon.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioOcclusionTest {
    @Test void solidWallsMuffleWhileOpenSegmentsStayDry() {
        var budget = new AudioOcclusion.Budget();
        assertEquals(0, AudioOcclusion.trace((x,y,z) -> false, 0, 0, 0, 10, 0, 0, budget));
        assertTrue(AudioOcclusion.trace((x,y,z) -> x >= 4 && x <= 5,
                0, 0, 0, 10, 0, 0, budget) > 0.9f);
    }

    @Test void negativeCoordinatesUseFloorAndStallsCannotExceedTheSharedBudget() {
        var budget = new AudioOcclusion.Budget();
        int[] queries = {0};
        assertTrue(AudioOcclusion.trace((x,y,z) -> x == -1, -0.1f, 0, 0, -0.9f, 0, 0, budget) > 0);
        for (int i = 0; i < 100; i++) {
            AudioOcclusion.trace((x,y,z) -> { queries[0]++; return false; }, 0, 0, 0, 100000, 2, 3, budget);
        }
        assertEquals(4, budget.rays);
        assertTrue(budget.queries <= 256);
        assertTrue(queries[0] <= 3 * AudioOcclusion.MAX_STEPS);
        budget.nextFrame();
        assertEquals(0, budget.rays);
        assertEquals(4, budget.peakRays);
    }

    @Test void zeroLengthAndDisabledNativeFiltersAreSafe() {
        var budget = new AudioOcclusion.Budget();
        assertEquals(0, AudioOcclusion.trace((x,y,z) -> false, 1, 1, 1, 1, 1, 1, budget));
        AcousticSources sources = new AcousticSources(false, new int[]{123}, new int[0]);
        sources.position(123, 0, 0, 0, false);
        sources.update(100);
        sources.reset();
        sources.close();
        new AudioManager().bindWorld(null);
    }
}
