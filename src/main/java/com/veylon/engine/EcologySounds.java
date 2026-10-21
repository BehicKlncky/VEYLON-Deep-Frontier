package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Original cues for urban milestone 15, synthesized from their own seed after every earlier bank,
 * so no existing buffer changes a sample. No samples, clicks or copied creature calls.
 *
 * <ul>
 *   <li>{@link Cue#STIR}: the Veilmaw's telegraph, a wet membrane hum rising over its windup.</li>
 *   <li>{@link Cue#LUNGE}: the dash, a short rasp and slap.</li>
 *   <li>{@link Cue#PAIN} and {@link Cue#DEATH}: a choked flutter, and a long deflating sigh.</li>
 *   <li>{@link Cue#SMOTHER}: a hiss as a torch goes out under its hood.</li>
 * </ul>
 */
public final class EcologySounds {
    public enum Cue { STIR, LUNGE, PAIN, DEATH, SMOTHER }

    static final long SEED = 0x45434f4c4f475953L;

    private EcologySounds() {
    }

    static void synthesize(BiConsumer<String, float[]> sink) {
        Random noise = new Random(SEED);
        for (Cue cue : Cue.values()) {
            float seconds = switch (cue) {
                case STIR -> 1.1f;
                case LUNGE -> 0.35f;
                case PAIN -> 0.3f;
                case DEATH -> 1.0f;
                case SMOTHER -> 0.45f;
            };
            float[] out = new float[(int) (ProceduralAudio.RATE * seconds)];
            float low = 0, band = 0;
            for (int i = 0; i < out.length; i++) {
                float t = i / (float) ProceduralAudio.RATE, f = i / (float) out.length;
                float white = noise.nextFloat() * 2 - 1;
                low += 0.12f * (white - low);
                band += 0.45f * (white - band);
                out[i] = switch (cue) {
                    case STIR -> {
                        // A low hum climbing in pitch and loudness, warbled by a slow wet flutter.
                        float hz = 70 + 150 * f * f;
                        float hum = (float) Math.sin(2 * Math.PI * hz * t + 2.2 * Math.sin(2 * Math.PI * 9 * t));
                        yield (hum * 0.32f + low * 0.35f) * (0.2f + 0.8f * f) * (float) Math.sin(Math.PI * Math.min(1f, f * 1.1f));
                    }
                    case LUNGE -> {
                        float slap = f < 0.12f ? (1 - f / 0.12f) : 0;
                        yield (band * 0.5f * (1 - f) + white * slap * 0.6f) * (float) Math.sin(Math.PI * f);
                    }
                    case PAIN -> {
                        float flutter = (float) Math.sin(2 * Math.PI * 34 * t) * 0.5f + 0.5f;
                        float tone = (float) Math.sin(2 * Math.PI * (260 - 120 * f) * t);
                        yield (tone * 0.22f * flutter + band * 0.25f) * (float) Math.sin(Math.PI * f);
                    }
                    case DEATH -> {
                        float tone = (float) Math.sin(2 * Math.PI * (130 * (1 - 0.6f * f)) * t);
                        yield (tone * 0.18f + low * 0.55f * (1 - f)) * (float) Math.sin(Math.PI * f);
                    }
                    case SMOTHER -> {
                        float hiss = band - low;
                        yield hiss * 0.45f * (1 - f) * (float) Math.min(1.0, f * 20);
                    }
                };
            }
            sink.accept(key(cue), out);
        }
    }

    static String key(Cue cue) {
        return "Ecology" + cue.name();
    }
}
