package com.veylon.engine;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

/** Hardware-calibrated audio budgets; excluded from portable verification. */
@Tag("performance")
class AudioPerformanceTest {
    /** Milliseconds allowed for the complete conditioned catalog, excluding device upload. */
    private static final double SYNTHESIS_BUDGET_MS = 1500;
    /** PCM payload ceiling in bytes, including the planned layered beds and variant banks. */
    private static final long PCM_BUDGET_BYTES = 64L * 1024 * 1024;
    private volatile long blackhole;

    @Test void startupSynthesisAndPcmPayloadStayBounded() {
        double best = Double.POSITIVE_INFINITY;
        long bytes = 0;
        for (int run = 0; run < 10; run++) {
            long[] sampleCount = {0};
            long start = System.nanoTime();
            new ProceduralAudio(new Random(60600)).synthesize((name, samples) -> {
                PcmAudio.prepare(samples);
                for (float sample : samples) blackhole = PcmAudio.encode(sample);
                sampleCount[0] += samples.length;
            });
            double ms = (System.nanoTime() - start) / 1e6;
            if (run >= 3) best = Math.min(best, ms);
            bytes = sampleCount[0] * 2;
        }
        System.out.printf("benchmark audio synthesis %.3f ms (budget %.0f), PCM %d bytes (budget %d)%n",
                best, SYNTHESIS_BUDGET_MS, bytes, PCM_BUDGET_BYTES);
        assertTrue(best <= SYNTHESIS_BUDGET_MS, "audio startup synthesis budget");
        assertTrue(bytes <= PCM_BUDGET_BYTES, "audio PCM payload budget");
    }
}
