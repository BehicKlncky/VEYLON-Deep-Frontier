package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class AudioCharacterizationTest {
    static Map<String, float[]> baseline() {
        Map<String, float[]> bank = new LinkedHashMap<>();
        new ProceduralAudio(new Random(60600)).synthesize(bank::put);
        return bank;
    }

    @Test void everyRecipeReplaysWithoutAnAudioDevice() {
        var first = baseline();
        var second = baseline();
        assertEquals(48, first.size());
        first.forEach((name, samples) -> assertArrayEquals(samples, second.get(name), name));
    }

    @Test void recordOriginalBuffersBeforeChangingTheirOutput() {
        long start = System.nanoTime();
        var bank = baseline();
        double ms = (System.nanoTime() - start) / 1e6;
        long count = bank.values().stream().mapToLong(a -> a.length).sum();
        System.out.printf("AUDIO_BASELINE rate=%d buffers=%d samples=%d pcmBytes=%d floatBytes=%d synthesisMs=%.3f%n",
                ProceduralAudio.RATE, bank.size(), count, count * 2, count * 4, ms);
        bank.forEach((name, s) -> System.out.printf(
                "%s samples=%d mean=%.7f peak=%.5f rms=%.5f seam=%.6f hash=%d low=%.9f high=%.9f%n",
                name, s.length, AudioSignalAssertions.mean(s), AudioSignalAssertions.peak(s),
                AudioSignalAssertions.rms(s), Math.abs(s[0] - s[s.length - 1]),
                java.util.Arrays.hashCode(s), AudioSignalAssertions.band(s, ProceduralAudio.RATE, 100, 1000),
                AudioSignalAssertions.band(s, ProceduralAudio.RATE, 2000, 10000)));
        assertEquals(55125, bank.get("Rain").length);
        assertEquals(88200, bank.get("Wind").length);
        assertTrue(AudioSignalAssertions.peak(bank.get("Musket")) > 1,
                "Characterizes the existing upload clipping defect before repairing it");
    }

    @Test void spectralHelperLocatesAKnownTone() {
        float[] tone = new float[22050];
        for (int i = 0; i < tone.length; i++) tone[i] = (float) Math.sin(2 * Math.PI * 1000 * i / 22050);
        assertTrue(AudioSignalAssertions.energy(tone, 22050, 1000)
                > 1000 * AudioSignalAssertions.energy(tone, 22050, 3000));
    }

    @Test void originalUploadConversionIsPinned() {
        assertEquals(29490, PcmAudio.encode(1));
        assertEquals(-29490, PcmAudio.encode(-1));
        assertEquals(PcmAudio.encode(1), PcmAudio.encode(2));
    }
}
