package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

/** Original additive-synthesis motifs: muted partials, slow attacks and space between notes. */
final class MusicPhrases {
    /** D3 in Hz; all phrases share this tonal anchor without a sound font. */
    private static final double ROOT_HZ = 146.8323839587;
    /** Four note offsets in semitones, ordered by MusicMood. */
    private static final int[][] NOTES = {{0, 7, 10, 14}, {-12, 0, 3, 7}, {-24, -23, -17, -12},
            {-24, -17, -12, -11}, {-12, 3, 7, 10}, {0, 7, 12, 19}};
    /** Seconds between entries, duration per note, and soft attack/release lengths. */
    private static final float NOTE_SPACING = 2.25f, NOTE_SECONDS = 4.5f, ATTACK = 0.45f, RELEASE = 1.6f;
    /** Linear note amplitude and quieter octave pedal amplitude. */
    private static final float NOTE_GAIN = 0.11f, PEDAL_GAIN = 0.025f;
    private MusicPhrases() { }

    static String key(MusicMood mood) { return "Music" + mood.name(); }

    static void synthesize(Random rng, BiConsumer<String, float[]> sink) {
        for (MusicMood mood : MusicMood.values()) sink.accept(key(mood), phrase(mood, rng));
    }

    static float[] phrase(MusicMood mood, Random rng) {
        float[] out = new float[(int) (MusicDirector.PHRASE_SECONDS * ProceduralAudio.RATE)];
        for (int note = 0; note < NOTES[mood.ordinal()].length; note++) {
            double hz = ROOT_HZ * Math.pow(2, NOTES[mood.ordinal()][note] / 12.0);
            double phase = rng.nextDouble() * Math.PI * 2;
            int offset = (int) (note * NOTE_SPACING * ProceduralAudio.RATE);
            int length = (int) (NOTE_SECONDS * ProceduralAudio.RATE);
            for (int i = 0; i < length && offset + i < out.length; i++) {
                float t = i / (float) ProceduralAudio.RATE;
                float envelope = smooth(t / ATTACK) * smooth((NOTE_SECONDS - t) / RELEASE);
                double angle = phase + Math.PI * 2 * hz * t;
                out[offset + i] += NOTE_GAIN * envelope * (float) (Math.sin(angle)
                        + 0.25 * Math.sin(2 * angle) + 0.06 * Math.sin(3 * angle));
            }
        }
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) ProceduralAudio.RATE;
            out[i] = (out[i] + PEDAL_GAIN * (float) Math.sin(Math.PI * ROOT_HZ * t)) * MusicDirector.envelope(t);
        }
        return out;
    }

    private static float smooth(float t) { t = Math.max(0, Math.min(1, t)); return t * t * (3 - 2 * t); }
}
