package com.veylon.engine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** The city's sounds (urban milestone 22): their own seed, clean signals, distinct takes and cues. */
class CitySoundsTest {
    @Test
    void theCityBankComesLastFromItsOwnSeedAndLeavesEveryEarlierBufferUnchanged() {
        Map<String, float[]> city = new LinkedHashMap<>(), catalog = catalogue(7), other = catalogue(8);
        CitySounds.synthesize(city::put);
        assertEquals(CitySounds.FOOTSTEPS.length * VariantBank.COUNT + CitySounds.Cue.values().length, city.size());
        for (var entry : city.entrySet()) {
            assertArrayEquals(entry.getValue(), catalog.get(entry.getKey()), entry.getKey() + " comes from its own seed");
            assertArrayEquals(entry.getValue(), other.get(entry.getKey()), entry.getKey() + " ignores the catalogue's generator");
            AudioSignalAssertions.clean(entry.getKey(), PcmAudio.prepare(entry.getValue().clone()));
        }
        var names = catalog.keySet().stream().toList();
        int first = names.indexOf(VariantBank.key(CitySounds.FOOT_METAL, 0));
        assertEquals(names.size() - city.size(), first, "the city's sounds are synthesized after every earlier one");
        assertEquals(VariantBank.key("FootGrass", 0), names.getFirst(), "the catalogue still opens with its first recipe");
    }

    @Test
    void everyCueAndEveryFootstepTakeIsItsOwnSoundOfItsDeclaredLength() {
        Map<String, float[]> city = new LinkedHashMap<>();
        CitySounds.synthesize(city::put);
        float[][] all = city.values().toArray(new float[0][]);
        for (int a = 0; a < all.length; a++) {
            for (int b = a + 1; b < all.length; b++) {
                assertFalse(Arrays.equals(all[a], all[b]), "two of the city's sounds are identical");
            }
        }
        for (CitySounds.Cue cue : CitySounds.Cue.values()) {
            assertEquals((int) (ProceduralAudio.RATE * cue.seconds), city.get(CitySounds.key(cue)).length, cue.name());
            assertEquals(0f, ProceduralAudio.loopSeconds(CitySounds.key(cue)), cue + " is a one-shot, not a bed");
        }
        for (String step : CitySounds.FOOTSTEPS) {
            for (int take = 0; take < VariantBank.COUNT; take++) {
                float seconds = city.get(VariantBank.key(step, take)).length / (float) ProceduralAudio.RATE;
                assertTrue(seconds > 0.08f && seconds < 0.2f, step + " take " + take + " is a short step: " + seconds);
            }
        }
    }

    @Test
    void theLampHumsAtItsMainsPitchAndAMetalStepRingsAboveARubbleStepsThump() {
        Map<String, float[]> city = new LinkedHashMap<>();
        CitySounds.synthesize(city::put);
        float[] hum = PcmAudio.prepare(city.get(CitySounds.key(CitySounds.Cue.LAMP_HUM)).clone());
        double mains = AudioSignalAssertions.energy(hum, ProceduralAudio.RATE, CitySounds.HUM_HZ);
        double between = AudioSignalAssertions.energy(hum, ProceduralAudio.RATE, CitySounds.HUM_HZ * 1.5);
        assertTrue(mains > between * 20, "a steady hum, not noise: " + mains + " vs " + between);
        float[] metal = PcmAudio.prepare(city.get(CitySounds.FOOT_METAL).clone());
        float[] rubble = PcmAudio.prepare(city.get(CitySounds.FOOT_RUBBLE).clone());
        double metalRing = AudioSignalAssertions.band(metal, ProceduralAudio.RATE, 300, 1500);
        double rubbleRing = AudioSignalAssertions.band(rubble, ProceduralAudio.RATE, 300, 1500);
        assertTrue(metalRing > rubbleRing, "metal rings in the plate's band more than grit does");
        float[] pulse = PcmAudio.prepare(city.get(CitySounds.key(CitySounds.Cue.VEIL_PULSE)).clone());
        assertTrue(AudioSignalAssertions.band(pulse, ProceduralAudio.RATE, 20, 120)
                > AudioSignalAssertions.band(pulse, ProceduralAudio.RATE, 2000, 10000), "the Veilmaw's throb is low");
    }

    private static Map<String, float[]> catalogue(long seed) {
        Map<String, float[]> catalog = new LinkedHashMap<>();
        new ProceduralAudio(new Random(seed)).synthesize(catalog::put);
        return catalog;
    }
}
