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
        assertTrue(first.size() >= 48, "All original sounds remain available");
        first.forEach((name, samples) -> assertArrayEquals(samples, second.get(name), name));
    }

    @Test void measureCatalogBeforeNativeUpload() {
        long start = System.nanoTime();
        var bank = baseline();
        double ms = (System.nanoTime() - start) / 1e6;
        long count = bank.values().stream().mapToLong(a -> a.length).sum();
        System.out.printf("AUDIO_CATALOG rate=%d buffers=%d samples=%d pcmBytes=%d floatBytes=%d synthesisMs=%.3f%n",
                ProceduralAudio.RATE, bank.size(), count, count * 2, count * 4, ms);
        bank.forEach((name, s) -> System.out.printf(
                "%s samples=%d mean=%.7f peak=%.5f rms=%.5f seam=%.6f hash=%d low=%.9f high=%.9f%n",
                name, s.length, AudioSignalAssertions.mean(s), AudioSignalAssertions.peak(s),
                AudioSignalAssertions.rms(s), Math.abs(s[0] - s[s.length - 1]),
                java.util.Arrays.hashCode(s), AudioSignalAssertions.band(s, ProceduralAudio.RATE, 100, 1000),
                AudioSignalAssertions.band(s, ProceduralAudio.RATE, 2000, 10000)));
        assertEquals((int) (ProceduralAudio.loopSeconds("Rain") * ProceduralAudio.RATE), bank.get("Rain").length);
        assertEquals((int) (ProceduralAudio.loopSeconds("Wind") * ProceduralAudio.RATE), bank.get("Wind").length);
        bank.forEach((name, samples) -> assertTrue(AudioSignalAssertions.peak(samples) > 0,
                name + " recipe unexpectedly silent"));
    }

    @Test void everyBufferRetainsItsDeclaredDuration() {
        float[] seconds = {0.10f, 0.07f, 0.08f, 0.13f, 0.16f, 0.06f, 0.05f, 0.06f,
                0.22f, 0.09f, 0.035f, 0.5f, 0.6f, 0.5f, 0.09f, 0.22f, 0.18f, 0.12f,
                0.14f, 0.2f, 0.35f, 1.46f, 1.54f, 1.4f, 2.4f, 1.9f, 0.9f, 0.45f,
                0.5f, 0.7f, 0.45f, 0.28f, 0.05f, 0.04f, 1.15f, 0.7f, 0.16f, 0.9f,
                1.2f, 2.2f, 1.6f, 0.7f};
        int index = 0;
        for (var entry : baseline().entrySet()) {
            float loopSeconds = ProceduralAudio.loopSeconds(entry.getKey());
            if (loopSeconds > 0) {
                assertEquals((int) (loopSeconds * ProceduralAudio.RATE), entry.getValue().length, entry.getKey());
            } else if (index < seconds.length) {
                assertTrue(Math.abs(entry.getValue().length - (int) (seconds[index++] * ProceduralAudio.RATE)) <= 1,
                        entry.getKey() + " one-shot duration changed");
            }
        }
        assertEquals(seconds.length, index);
    }

    @Test void disabledManagerPublicPlaybackNeverTouchesNativeAudio() throws Exception {
        AudioManager audio = new AudioManager();
        for (var method : AudioManager.class.getMethods()) {
            if (!method.getName().startsWith("play")) continue;
            Object[] args = new Object[method.getParameterCount()];
            Class<?>[] types = method.getParameterTypes();
            for (int i = 0; i < args.length; i++) {
                args[i] = types[i] == float.class ? 0f : types[i] == boolean.class ? false : null;
            }
            method.invoke(audio, args);
        }
        audio.setAmbience(1, 1, 1, 1, 1, 1);
        audio.setListener(0, 0, 0, 0);
        audio.update(1);
        audio.shutdown();
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
