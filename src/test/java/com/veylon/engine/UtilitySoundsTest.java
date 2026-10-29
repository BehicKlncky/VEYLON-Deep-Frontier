package com.veylon.engine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class UtilitySoundsTest {
    @Test
    void cuesAreDistinctCleanAndIndependentOfExistingCatalogRandomness() {
        Map<String, float[]> cues = new LinkedHashMap<>(), catalog = new LinkedHashMap<>();
        UtilitySounds.synthesize(cues::put);
        new ProceduralAudio(new Random(7)).synthesize(catalog::put);
        assertEquals(UtilitySounds.Cue.values().length, cues.size());
        for (var entry : cues.entrySet()) {
            assertArrayEquals(entry.getValue(), catalog.get(entry.getKey()), entry.getKey() + " comes from its own seed");
            AudioSignalAssertions.clean(entry.getKey(), PcmAudio.prepare(entry.getValue().clone()));
        }
        float[][] all = cues.values().toArray(new float[0][]);
        for (int a = 0; a < all.length; a++) {
            for (int b = a + 1; b < all.length; b++) {
                assertFalse(Arrays.equals(all[a], all[b]), "each generator sound is its own");
            }
        }
        for (UtilitySounds.Cue cue : UtilitySounds.Cue.values()) {
            assertEquals((int) (ProceduralAudio.RATE * UtilitySounds.seconds(cue)), cues.get(UtilitySounds.key(cue)).length,
                    cue + " lasts as long as the rules replay it");
        }
    }

    @Test
    void theRunningEngineThumpsAtItsFiringRateAndDominatesTheBandAboveIt() {
        float[] run = PcmAudio.prepare(catalogue().get(UtilitySounds.key(UtilitySounds.Cue.RUN)).clone());
        double firing = AudioSignalAssertions.energy(run, ProceduralAudio.RATE, UtilitySounds.FIRING_HZ);
        double offBeat = AudioSignalAssertions.energy(run, ProceduralAudio.RATE, UtilitySounds.FIRING_HZ * 1.5);
        assertTrue(firing > offBeat * 4, "a steady chug, not noise: " + firing + " vs " + offBeat);
    }

    private static Map<String, float[]> catalogue() {
        Map<String, float[]> catalog = new LinkedHashMap<>();
        new ProceduralAudio(new Random(7)).synthesize(catalog::put);
        return catalog;
    }
}
