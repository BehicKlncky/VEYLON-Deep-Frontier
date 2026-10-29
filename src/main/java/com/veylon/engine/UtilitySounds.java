package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Original cues for urban milestone 19, synthesized from their own seed after every earlier bank,
 * so no existing buffer changes a sample. They are a small salvaged petrol generator's: the sounds
 * that tell the player it is running, and that tell anything listening where the refuge is. The
 * threat-attracting noise itself is a {@code WorldNoise} event the utility rules emit; these are
 * presentation only.
 *
 * <ul>
 *   <li>{@link Cue#START}: a pull cord rattling out twice, the second pull catching into a few
 *       ragged firing strokes.</li>
 *   <li>{@link Cue#RUN}: one second of the engine at its working speed, a low chug of firing
 *       strokes with valve tick and exhaust hiss, played over and over while it runs.</li>
 *   <li>{@link Cue#STOP}: the engine starved of fuel, misfiring, slowing and coasting to a stop.</li>
 * </ul>
 * No samples, voices or words.
 */
public final class UtilitySounds {
    public enum Cue { START, RUN, STOP }

    static final long SEED = 0x47454e4552415452L;
    /** Firing strokes per second at the working speed, a single cylinder at about 1,800 rpm. */
    static final float FIRING_HZ = 15f;

    private UtilitySounds() {
    }

    /** Seconds a cue lasts; {@link Cue#RUN} is played again each time it ends while the engine runs. */
    public static float seconds(Cue cue) {
        return switch (cue) {
            case START -> 1.1f;
            case RUN -> 1.0f;
            case STOP -> 0.9f;
        };
    }

    static void synthesize(BiConsumer<String, float[]> sink) {
        Random noise = new Random(SEED);
        for (Cue cue : Cue.values()) {
            float[] out = new float[(int) (ProceduralAudio.RATE * seconds(cue))];
            float low = 0, band = 0;
            double phase = 0;
            for (int i = 0; i < out.length; i++) {
                float t = i / (float) ProceduralAudio.RATE, f = i / (float) out.length;
                float white = noise.nextFloat() * 2 - 1;
                low += 0.05f * (white - low);
                band += 0.3f * (white - band);
                float hiss = band - low;
                out[i] = switch (cue) {
                    case START -> {
                        // Two cord pulls, a ratcheting rattle each, then the engine catches.
                        float pull = rattle(t, 0.0f, 0.28f, low, hiss) + rattle(t, 0.38f, 0.26f, low, hiss) * 0.9f;
                        float caught = t < 0.66f ? 0 : Math.min(1f, (t - 0.66f) / 0.08f);
                        float rate = FIRING_HZ * (0.55f + 0.45f * Math.min(1f, (t - 0.66f) / 0.3f));
                        phase += 2 * Math.PI * rate / ProceduralAudio.RATE;
                        yield pull + caught * stroke(phase, low, hiss) * fade(f);
                    }
                    case RUN -> {
                        // Whole firing cycles fill the second exactly, so one play runs into the next.
                        phase += 2 * Math.PI * FIRING_HZ / ProceduralAudio.RATE;
                        yield stroke(phase, low, hiss);
                    }
                    case STOP -> {
                        // Misfires as the fuel runs out, the strokes slowing until the flywheel coasts.
                        float rate = FIRING_HZ * (1f - 0.8f * f);
                        phase += 2 * Math.PI * rate / ProceduralAudio.RATE;
                        boolean misfire = ((int) (phase / (2 * Math.PI))) % 3 == 1 && f > 0.15f;
                        float s = stroke(phase, low, hiss) * (misfire ? 0.25f : 1f);
                        yield s * (float) Math.pow(1f - f, 1.6f);
                    }
                };
            }
            sink.accept(key(cue), out);
        }
    }

    /** One firing stroke: a thump at the start of each cycle, a little valve tick and the exhaust's hiss. */
    private static float stroke(double phase, float low, float hiss) {
        double cycle = phase % (2 * Math.PI) / (2 * Math.PI);
        float thump = (float) (Math.exp(-cycle * 9) * Math.sin(2 * Math.PI * 3 * cycle));
        float body = (float) Math.sin(phase) * 0.35f + (float) Math.sin(phase * 2) * 0.18f;
        float tick = cycle > 0.42 && cycle < 0.46 ? hiss * 0.6f : 0;
        float exhaust = low * (float) Math.exp(-cycle * 4) * 0.9f;
        return (thump * 0.55f + body + exhaust) * 0.5f + tick * 0.2f + hiss * 0.05f;
    }

    /** A cord pull: a fast ratchet of clicks with a rising whirr. */
    private static float rattle(float t, float start, float length, float low, float hiss) {
        float local = t - start;
        if (local < 0 || local > length) { return 0; }
        float env = Math.min(1f, local / 0.01f) * Math.min(1f, (length - local) / 0.04f);
        double clicks = Math.sin(2 * Math.PI * (40 + 120 * local / length) * local);
        return env * (float) (clicks > 0.85 ? hiss * 0.8 : 0) + env * low * 0.3f;
    }

    /** Fades the last tenth to silence so the buffer ends where it began. */
    private static float fade(float f) {
        return f > 0.9f ? (1f - f) / 0.1f : 1f;
    }

    static String key(Cue cue) {
        return "Utility" + cue.name();
    }
}
