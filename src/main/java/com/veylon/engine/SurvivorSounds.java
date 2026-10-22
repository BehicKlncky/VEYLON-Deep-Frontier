package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Original cues for urban milestone 16, synthesized from their own seed after every earlier bank,
 * so no existing buffer changes a sample. They are the small, close sounds of people trying to
 * stay quiet, played where they hide and heard through a wall: the clue that a hidden refuge is
 * still lived in. No samples, voices or words.
 *
 * <ul>
 *   <li>{@link Cue#COUGH}: a stifled cough, two breathy bursts with a little throat in them, the
 *       second cut short.</li>
 *   <li>{@link Cue#SHUFFLE}: feet scuffing on concrete, then something set down with a dull knock.</li>
 *   <li>{@link Cue#WHISPER}: four hushed syllables, unvoiced breath shaped by a moving resonance.</li>
 * </ul>
 */
public final class SurvivorSounds {
    public enum Cue { COUGH, SHUFFLE, WHISPER }

    static final long SEED = 0x5355525649564f52L;
    /** Resonance centres of the whisper's four syllables, in Hz. */
    private static final float[] SYLLABLES = {1_150f, 700f, 1_600f, 900f};

    private SurvivorSounds() {
    }

    static void synthesize(BiConsumer<String, float[]> sink) {
        Random noise = new Random(SEED);
        for (Cue cue : Cue.values()) {
            float seconds = switch (cue) {
                case COUGH -> 0.55f;
                case SHUFFLE -> 0.75f;
                case WHISPER -> 0.95f;
            };
            float[] out = new float[(int) (ProceduralAudio.RATE * seconds)];
            float low = 0, band = 0, y1 = 0, y2 = 0;
            for (int i = 0; i < out.length; i++) {
                float t = i / (float) ProceduralAudio.RATE, f = i / (float) out.length;
                float white = noise.nextFloat() * 2 - 1;
                low += 0.08f * (white - low);
                band += 0.35f * (white - band);
                float breath = band - low;
                out[i] = switch (cue) {
                    case COUGH -> {
                        // Two bursts: a sharp attack decaying fast, the second weaker and clipped.
                        float env = burst(t, 0f, 0.2f, 13f) + 0.65f * burst(t, 0.27f, 0.13f, 18f);
                        float throat = (float) (Math.sin(2 * Math.PI * 150 * t) * (0.6 + 0.4 * Math.sin(2 * Math.PI * 300 * t)));
                        yield env * (breath * 0.8f + throat * 0.18f + low * 0.3f);
                    }
                    case SHUFFLE -> {
                        // Three scuffs of a sole, then a knock as something is set down.
                        float scuff = f < 0.62f ? (float) Math.pow(Math.max(0, Math.sin(Math.PI * f / 0.62f * 3)), 2) : 0;
                        float knockT = t - 0.5f;
                        float knock = knockT > 0 ? (float) (Math.sin(2 * Math.PI * (95 - 30 * knockT) * knockT) * Math.exp(-knockT * 28)) : 0;
                        yield scuff * (low * 0.7f + breath * 0.25f) + knock * 0.35f;
                    }
                    case WHISPER -> {
                        // Breath through a resonance that jumps per syllable; each syllable swells and fades.
                        int s = Math.min(SYLLABLES.length - 1, (int) (f * SYLLABLES.length));
                        float local = f * SYLLABLES.length - s;
                        double w = 2 * Math.PI * SYLLABLES[s] / ProceduralAudio.RATE, r = 0.985;
                        float y = (float) (white * (1 - r) + 2 * r * Math.cos(w) * y1 - r * r * y2);
                        y2 = y1;
                        y1 = y;
                        float env = (float) Math.pow(Math.sin(Math.PI * local), 1.5) * (s == 2 ? 0.7f : 1f);
                        yield (y * 2.2f + breath * 0.12f) * env;
                    }
                };
            }
            sink.accept(key(cue), out);
        }
    }

    /** A burst starting at {@code start} lasting {@code length} seconds with a fast attack and exponential decay. */
    private static float burst(float t, float start, float length, float decay) {
        float local = t - start;
        if (local < 0 || local > length) { return 0; }
        float attack = Math.min(1f, local / 0.008f), release = Math.min(1f, (length - local) / 0.02f);
        return attack * release * (float) Math.exp(-local * decay);
    }

    static String key(Cue cue) {
        return "Survivor" + cue.name();
    }
}
