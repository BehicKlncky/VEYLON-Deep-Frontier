package com.veylon.engine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class SurvivorSoundsTest {
    @Test
    void cuesAreDistinctCleanAndIndependentOfExistingCatalogRandomness() {
        Map<String, float[]> cues = new LinkedHashMap<>(), catalog = new LinkedHashMap<>();
        SurvivorSounds.synthesize(cues::put);
        new ProceduralAudio(new Random(7)).synthesize(catalog::put);
        assertEquals(SurvivorSounds.Cue.values().length, cues.size());
        for (var entry : cues.entrySet()) {
            assertArrayEquals(entry.getValue(), catalog.get(entry.getKey()), entry.getKey() + " comes from its own seed");
            AudioSignalAssertions.clean(entry.getKey(), PcmAudio.prepare(entry.getValue().clone()));
        }
        float[][] all = cues.values().toArray(new float[0][]);
        for (int a = 0; a < all.length; a++) {
            for (int b = a + 1; b < all.length; b++) {
                assertFalse(Arrays.equals(all[a], all[b]), "each sound of hiding people is its own");
            }
        }
    }

    @Test
    void theyAreQuieterThanTheNoisesThatThreatenThePeopleMakingThem() {
        Map<String, float[]> catalog = new LinkedHashMap<>();
        new ProceduralAudio(new Random(7)).synthesize(catalog::put);
        double loudest = 0;
        for (SurvivorSounds.Cue cue : SurvivorSounds.Cue.values()) {
            loudest = Math.max(loudest, AudioSignalAssertions.rms(PcmAudio.prepare(catalog.get(SurvivorSounds.key(cue)).clone())));
        }
        double hammer = AudioSignalAssertions.rms(PcmAudio.prepare(
                catalog.get(EcologySounds.key(EcologySounds.Cue.HAMMER)).clone()));
        assertTrue(loudest < hammer, "people in hiding are hushed: " + loudest + " vs a Breacher's blow " + hammer);
    }
}
