package com.veylon.engine;

import java.util.Arrays;
import java.util.Random;
import java.util.function.IntConsumer;

/** Four bounded presentation clocks; no event timing is baked into a looping buffer. */
final class AmbientEvents {
    /** Mean waits in seconds at full channel intensity: rain, fire, cave, insects. */
    private static final float[] MEAN_WAIT = {0.22f, 0.8f, 8f, 0.7f};
    private static final int[] CHANNEL = {0, 2, 3, 4};
    /** Seconds between independent wind target changes, plus uniformly random variation. */
    private static final float GUST_WAIT = 2f, GUST_VARIATION = 5f;
    private final Random rng;
    private final float[] remaining = new float[4];
    private float gustWait;
    private float gust = 0.75f, gustTarget = 0.75f;

    AmbientEvents(Random rng) { this.rng = rng; reset(); }

    void reset() {
        Arrays.fill(remaining, 1f);
        gustWait = 0;
        gust = gustTarget = 0.75f;
    }

    /** At most one event per kind, even after a stall; no catch-up burst. */
    void update(float dt, float[] gains, IntConsumer play) {
        dt = Math.max(0, Math.min(0.1f, dt));
        gustWait -= dt;
        if (gustWait <= 0) {
            gustWait = GUST_WAIT + rng.nextFloat() * GUST_VARIATION;
            gustTarget = 0.45f + rng.nextFloat() * 0.55f;
        }
        gust += (gustTarget - gust) * (float) -Math.expm1(-dt * 0.7f);
        for (int i = 0; i < remaining.length; i++) {
            float gain = gains[CHANNEL[i]];
            if (gain < 0.01f) { remaining[i] = MEAN_WAIT[i]; continue; }
            remaining[i] -= dt;
            if (remaining[i] > 0) continue;
            play.accept(i);
            remaining[i] = Math.max(0.04f, (float) (-Math.log(1 - rng.nextDouble())
                    * MEAN_WAIT[i] / Math.max(0.1f, gain)));
        }
    }

    float gust() { return gust; }

    /** Relative layer weights; increasing rain intensity shifts energy into its body. */
    static float layerWeight(int layer, float intensity, float gust) {
        return switch (layer) {
            case 0 -> 0.20f + 0.65f * intensity;
            case 6 -> 0.80f - 0.55f * intensity;
            case 1 -> (0.45f + 0.25f * intensity) * gust;
            case 7 -> (0.55f - 0.25f * intensity) * gust;
            default -> 1;
        };
    }
}
