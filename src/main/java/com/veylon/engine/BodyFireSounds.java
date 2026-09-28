package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

import static com.veylon.engine.AudioFilters.NOISE_SCALE;
import static com.veylon.engine.AudioFilters.alpha;

/**
 * The sounds of a burning body, synthesized like every other sound: a
 * crackle while it burns, a soft rushing flare as it catches, and a hiss of
 * steam when water or rain puts it out. No voices: a burning animal or person
 * is heard by its fire.
 *
 * <p>Made last and from a generator of their own ({@link #SEED}), so every
 * buffer synthesized before them keeps exactly the samples it had.
 */
final class BodyFireSounds {

    /** Seed of the body-fire recipes' own generator. */
    static final long SEED = 0x424f445946495245L;
    /** Buffer names, in the order they are made. */
    static final String CRACKLE = "BodyCrackle", FLARE = "BodyFlare", SIZZLE = "BodySizzle";
    /** Durations, seconds. */
    static final float CRACKLE_SECONDS = 0.35f, FLARE_SECONDS = 0.55f, SIZZLE_SECONDS = 0.7f;

    /** Clicks per second at the start of a crackle, thinning to 40 % by its end. */
    private static final float CRACKLE_CLICKS_PER_SECOND = 95f;
    private static final float ROAR_HZ = 380f, CLICK_HZ = 5200f, FLARE_THUMP_HZ = 68f, HISS_HZ = 3400f;

    private BodyFireSounds() {
    }

    static void synthesize(Random rng, BiConsumer<String, float[]> sink) {
        sink.accept(CRACKLE, crackle(rng));
        sink.accept(FLARE, flare(rng));
        sink.accept(SIZZLE, sizzle(rng));
    }

    /** Dry wood-and-fat crackle: sharp, uneven clicks over a low roar. */
    private static float[] crackle(Random rng) {
        int n = (int) (CRACKLE_SECONDS * ProceduralAudio.RATE);
        float[] out = new float[n];
        float roar = 0f, click = 0f, clickAmp = 0f;
        int clickLeft = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            if (clickLeft <= 0 && rng.nextFloat()
                    < CRACKLE_CLICKS_PER_SECOND * (1f - 0.6f * t) / ProceduralAudio.RATE) {
                clickLeft = 60 + rng.nextInt(260);
                clickAmp = 0.35f + rng.nextFloat() * 0.65f;
            }
            float s = 0f;
            if (clickLeft > 0) {
                clickLeft--;
                click += alpha(CLICK_HZ) * (x - click);
                s = (x - click) * clickAmp * Math.min(1f, clickLeft / 90f + 0.15f);
            }
            roar += alpha(ROAR_HZ) * (x - roar);
            out[i] = s * 0.9f + roar * 1.3f * (1f - 0.5f * t);
        }
        return out;
    }

    /** Catching: a soft rush of air, bright then dark, over a low thump. */
    private static float[] flare(Random rng) {
        int n = (int) (FLARE_SECONDS * ProceduralAudio.RATE);
        float[] out = new float[n];
        float y = 0f, y2 = 0f;
        for (int i = 0; i < n; i++) {
            float t = (float) i / ProceduralAudio.RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            float cutoff = 300f + 2200f * (float) Math.exp(-t * 9f);
            y += alpha(cutoff) * (x - y);
            y2 += alpha(cutoff) * (y - y2);
            float envelope = Math.min(1f, t / 0.02f) * (float) Math.exp(-t * 5.5f);
            float thump = (float) Math.sin(2 * Math.PI * FLARE_THUMP_HZ * t) * (float) Math.exp(-t * 14f) * 0.5f;
            out[i] = (y2 * 2.2f + thump) * envelope * 0.8f;
        }
        return out;
    }

    /** Steam: a bright hiss that flutters and fades. */
    private static float[] sizzle(Random rng) {
        int n = (int) (SIZZLE_SECONDS * ProceduralAudio.RATE);
        float[] out = new float[n];
        float low = 0f;
        float phase = rng.nextFloat() * 6.2831855f;
        for (int i = 0; i < n; i++) {
            float t = (float) i / ProceduralAudio.RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            low += alpha(HISS_HZ) * (x - low);
            float hiss = x - low;
            float flutter = 0.7f + 0.3f * (float) Math.sin(2 * Math.PI * 23f * t + phase);
            float envelope = Math.min(1f, t / 0.01f) * (float) Math.exp(-t * 4f);
            out[i] = hiss * envelope * flutter * 0.6f;
        }
        return out;
    }
}
