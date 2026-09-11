package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class MusicPhrasesTest {
    @Test void sixDistinctPhrasesHaveDeclaredLengthHeadroomAndQuietEdges() {
        float[] previous = null;
        for (MusicMood mood : MusicMood.values()) {
            float[] samples = MusicPhrases.phrase(mood, new Random(60600));
            assertEquals(529200, samples.length);
            PcmAudio.prepare(samples);
            AudioSignalAssertions.clean(mood.name(), samples);
            assertTrue(AudioSignalAssertions.peak(samples) < 0.35);
            assertEquals(0, samples[0]); assertEquals(0, samples[samples.length - 1]);
            if (previous != null) assertFalse(Arrays.equals(previous, samples));
            previous = samples;
        }
    }

    @Test void deepCaveAndBeaconOccupyDifferentRegisterWithoutBrightAliasEnergy() {
        float[] deep = MusicPhrases.phrase(MusicMood.DEEP_CAVE, new Random(1));
        float[] beacon = MusicPhrases.phrase(MusicMood.BEACON, new Random(1));
        assertTrue(AudioSignalAssertions.band(deep, ProceduralAudio.RATE, 30, 180)
                > 10 * AudioSignalAssertions.band(deep, ProceduralAudio.RATE, 2000, 12000));
        assertTrue(AudioSignalAssertions.band(beacon, ProceduralAudio.RATE, 250, 650)
                > 10 * AudioSignalAssertions.band(beacon, ProceduralAudio.RATE, 2000, 12000));
    }
}
