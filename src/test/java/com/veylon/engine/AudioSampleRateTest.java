package com.veylon.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioSampleRateTest {
    @Test void catalogUsesFullBandwidthPcm() {
        assertEquals(44100, ProceduralAudio.RATE);
    }

    @Test void filterCutoffsRemainInHzWhenTheRateDoubles() {
        for (int rate : new int[]{22050, 44100}) {
            float alpha = AudioFilters.alpha(1000, rate);
            float[] output = new float[rate];
            float value = 0;
            for (int i = 0; i < output.length; i++) {
                value += alpha * ((float) Math.sin(2 * Math.PI * 1000 * i / rate) - value);
                output[i] = value;
            }
            double amplitude = Math.sqrt(16 * AudioSignalAssertions.energy(output, rate, 1000));
            assertEquals(Math.sqrt(0.5), amplitude, 0.01, "-3 dB near 1 kHz at " + rate);
        }
    }

    @Test void migratedPolesRetainTheOriginalTimeConstants() {
        assertEquals(0.35, AudioFilters.alpha(AudioFilters.WOOD_HZ, 22050), 0.000001);
        assertEquals(0.05, AudioFilters.alpha(AudioFilters.BOOM_HZ, 22050), 0.000001);
        assertEquals(1 - Math.sqrt(0.65), AudioFilters.alpha(AudioFilters.WOOD_HZ), 0.000001);
    }

    @Test void chirpEnergyStaysInItsIntendedBandWithoutTheOldPhaseAliasing() {
        float[] chirp = AudioCharacterizationTest.baseline().get("Chirp");
        assertTrue(AudioSignalAssertions.band(chirp, 44100, 2400, 4200)
                > 100 * AudioSignalAssertions.band(chirp, 44100, 100, 1800));
        assertTrue(AudioSignalAssertions.band(chirp, 44100, 2400, 4200)
                > 100 * AudioSignalAssertions.band(chirp, 44100, 12000, 21000));
    }
}
