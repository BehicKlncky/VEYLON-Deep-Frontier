package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

/** Original membrane rasp and strained air; no samples, clicks or copied creature calls. */
public final class InfectedSounds {
    public enum Cue { NOTICE, STRIKE, PAIN, DEATH }
    private InfectedSounds() { }

    static void synthesize(BiConsumer<String, float[]> sink) {
        Random noise = new Random(0x5645494c534f554eL);
        for (Cue cue : Cue.values()) {
            float seconds = switch (cue) {
                case NOTICE -> 0.7f;
                case STRIKE -> 0.5f;
                case PAIN -> 0.28f;
                case DEATH -> 0.85f;
            };
            float[] out = new float[(int) (ProceduralAudio.RATE * seconds)];
            float low = 0;
            for (int i = 0; i < out.length; i++) {
                float t = i / (float) ProceduralAudio.RATE, f = i / (float) out.length;
                low += 0.16f * (noise.nextFloat() * 2 - 1 - low);
                float hz = (cue == Cue.PAIN ? 170 : 95) * (1 - 0.35f * f);
                float pulse = (float) Math.sin(2 * Math.PI * hz * t
                        + 1.5 * Math.sin(t * 2 * Math.PI * 27));
                out[i] = (pulse * 0.21f + low * 0.55f) * (float) Math.sin(Math.PI * f);
            }
            sink.accept(key(cue), out);
        }
    }

    static String key(Cue cue) { return "Veilbound" + cue.name(); }
}
