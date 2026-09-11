package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class SpatialAmbienceTest {
    @Test void weatherHasThreeDirectionsWithoutIncreasingItsPowerBudget() {
        for (int layer : new int[]{0, 1, 6, 7}) {
            int count = 0;
            float x = 0;
            for (var emitter : SpatialAmbience.EMITTERS) {
                if (emitter.layer() != layer) continue;
                assertTrue(emitter.relative());
                x += emitter.x();
                count++;
            }
            assertEquals(3, count);
            assertEquals(0, x);
            assertEquals(1, count * Math.pow(SpatialAmbience.allocation(layer), 2), 0.00001);
        }
        assertFalse(SpatialAmbience.EMITTERS[2].relative(), "fire must be world positioned");
    }

    @Test void independentlySynthesizedWeatherHasNegligibleCrossCorrelation() {
        float[] a = AmbienceBeds.texture(AmbienceBeds.Bed.Rain, new Random(42));
        float[] b = AmbienceBeds.texture(AmbienceBeds.Bed.Rain, new Random(43));
        double dot = 0, aa = 0, bb = 0;
        for (int i = 0; i < a.length; i++) { dot += a[i] * b[i]; aa += a[i] * a[i]; bb += b[i] * b[i]; }
        assertTrue(Math.abs(dot / Math.sqrt(aa * bb)) < 0.02, "weather buffers are correlated");
    }

    @Test void everyEmitterNamesAnUploadedBuffer() {
        var bank = AudioCharacterizationTest.baseline();
        for (var emitter : SpatialAmbience.EMITTERS) assertNotNull(bank.get(emitter.name()), emitter.name());
    }
}
