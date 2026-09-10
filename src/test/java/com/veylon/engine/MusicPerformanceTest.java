package com.veylon.engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Measured frame cost of the allocation-free music state machine, excluding native driver calls. */
@Tag("performance")
class MusicPerformanceTest {
    /** Milliseconds per director update on the reference machine. */
    private static final double BUDGET_MS = 0.02;
    private volatile float consumed;

    @Test void sparseDirectorFrameWorkStaysBelowBudget() {
        var backend = new MusicDirector.Backend() {
            public void start(MusicMood mood) { consumed = mood.ordinal(); }
            public void gain(float gain) { consumed = gain; }
            public void stop() { consumed = 0; }
        };
        var director = new MusicDirector(new Random(60600), backend);
        double best = Double.POSITIVE_INFINITY;
        for (int run = 0; run < 10; run++) {
            director.reset(); director.scene(MusicMood.CALM);
            long start = System.nanoTime();
            for (int frame = 0; frame < 100000; frame++) {
                if (frame % 1000 == 0) director.scene(frame % 2000 == 0 ? MusicMood.THREAT : MusicMood.CALM);
                director.update(1f / 60, 0.5f);
            }
            double ms = (System.nanoTime() - start) / 1e6 / 100000;
            if (run >= 3) best = Math.min(best, ms);
        }
        System.out.printf("benchmark music director %.6f ms (budget %.2f)%n", best, BUDGET_MS);
        assertTrue(best <= BUDGET_MS, "music frame budget");
    }
}
