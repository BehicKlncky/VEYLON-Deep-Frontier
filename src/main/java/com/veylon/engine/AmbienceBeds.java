package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

/** Continuous textures only: audible individual events belong to the frame scheduler. */
final class AmbienceBeds {
    /** Per-layer duration in seconds, lower/upper pole in Hz and target linear RMS. */
    enum Bed {
        Rain(17, 650, 5800, 0.13f), Wind(19, 90, 1800, 0.11f),
        Fire(23, 60, 1200, 0.10f), Cave(29, 35, 280, 0.08f),
        Crickets(31, 4500, 7500, 0.025f), Beacon(37, 180, 800, 0.07f),
        RainHigh(19, 2600, 10000, 0.12f), WindHigh(23, 1200, 6500, 0.08f);

        final float seconds, lowHz, highHz, rms;
        Bed(float seconds, float lowHz, float highHz, float rms) {
            this.seconds = seconds;
            this.lowHz = lowHz;
            this.highHz = highHz;
            this.rms = rms;
        }
    }

    /** Seconds and frequency endpoints for the four separately scheduled details. */
    static final float[] EVENT_SECONDS = {0.014f, 0.028f, 0.065f, 0.09f};
    static final String[] EVENT_NAMES = {"Droplet", "FirePop", "CaveDrip", "Cricket"};
    private static final float[] EVENT_START_HZ = {1600, 800, 650, 5400};
    private static final float[] EVENT_END_HZ = {2500, 500, 1100, 5600};

    private AmbienceBeds() { }

    static void synthesize(Random rng, BiConsumer<String, float[]> sink) {
        for (Bed bed : Bed.values()) sink.accept(bed.name(), texture(bed, rng));
        for (Bed bed : new Bed[]{Bed.Rain, Bed.Wind, Bed.RainHigh, Bed.WindHigh}) {
            sink.accept(bed.name() + "Left", texture(bed, rng));
            sink.accept(bed.name() + "Right", texture(bed, rng));
        }
        for (int i = 0; i < EVENT_NAMES.length; i++) sink.accept(EVENT_NAMES[i], event(i, rng));
    }

    static float seconds(String name) {
        name = name.replace("Left", "").replace("Right", "");
        for (Bed bed : Bed.values()) if (bed.name().equals(name)) return bed.seconds;
        return 0;
    }

    static float[] texture(Bed bed, Random rng) {
        float[] samples = new float[(int) (bed.seconds * ProceduralAudio.RATE)];
        float upper = 0, lower = 0;
        float highAlpha = AudioFilters.alpha(bed.highHz), lowAlpha = AudioFilters.alpha(bed.lowHz);
        double energy = 0;
        for (int i = 0; i < samples.length; i++) {
            float noise = rng.nextFloat() * 2 - 1;
            upper += highAlpha * (noise - upper);
            lower += lowAlpha * (upper - lower);
            float sample = upper - lower;
            if (bed == Bed.Beacon) {
                double time = i / (double) ProceduralAudio.RATE;
                sample = sample * 0.12f + (float) (0.3 * Math.sin(2 * Math.PI * 220 * time)
                        + 0.12 * Math.sin(2 * Math.PI * 331 * time));
            }
            samples[i] = sample;
            energy += sample * sample;
        }
        float scale = (float) (bed.rms / Math.sqrt(energy / samples.length));
        for (int i = 0; i < samples.length; i++) samples[i] *= scale;
        return samples;
    }

    /** Pitched resonances with finite attacks; no single-sample impulses. */
    static float[] event(int kind, Random rng) {
        float[] samples = new float[(int) (EVENT_SECONDS[kind] * ProceduralAudio.RATE)];
        double phase = 0;
        float filtered = 0;
        float alpha = AudioFilters.alpha(2600);
        for (int i = 0; i < samples.length; i++) {
            float progress = i / (float) (samples.length - 1);
            phase += 2 * Math.PI * (EVENT_START_HZ[kind]
                    + progress * (EVENT_END_HZ[kind] - EVENT_START_HZ[kind])) / ProceduralAudio.RATE;
            filtered += alpha * (rng.nextFloat() * 2 - 1 - filtered);
            double envelope = Math.sin(Math.PI * progress) * Math.exp(-3 * progress);
            samples[i] = (float) ((kind == 1 ? filtered : Math.sin(phase)) * envelope * 0.5);
        }
        return samples;
    }
}
