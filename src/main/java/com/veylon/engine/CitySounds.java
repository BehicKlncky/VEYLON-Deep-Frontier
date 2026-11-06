package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

/**
 * Original sounds of the abandoned city (urban milestone 22), synthesized from their own seed after
 * every earlier bank, so no existing buffer changes a sample. No recordings, voices or words.
 *
 * <p>Footsteps: {@value #FOOT_METAL} (a hollow clank on sheet metal, a car's roof, a grating) and
 * {@value #FOOT_RUBBLE} (grit and broken plaster crunching), four independent takes each.
 *
 * <p>{@link Cue}s, heard where they happen:
 * <ul>
 *   <li>{@link Cue#METAL_GROAN}: a loose roof sheet or a cordon fence grinding in the wind, far off.</li>
 *   <li>{@link Cue#SHUTTER_BANG}: a loose shutter or door slapping its frame twice.</li>
 *   <li>{@link Cue#GLASS_TINKLE}: a pane letting go somewhere, its pieces ringing down.</li>
 *   <li>{@link Cue#MASONRY_FALL}: a distant crumble of brick and plaster.</li>
 *   <li>{@link Cue#WATER_LAP}: standing water slopping against a wall in the flooded quarter.</li>
 *   <li>{@link Cue#WOOD_CREAK}: a floor or beam settling in an empty building.</li>
 *   <li>{@link Cue#PIPE_KNOCK}: a pipe knocking twice inside a wall or under a basement ceiling.</li>
 *   <li>{@link Cue#INFECTED_RASP}: the slow wet breath of a Veilbound that has not noticed anyone.</li>
 *   <li>{@link Cue#VEIL_PULSE}: the low throb of a Veilmaw resting in its growth.</li>
 *   <li>{@link Cue#LAMP_HUM}: a fed work lamp's hum, whole cycles so one play runs into the next.</li>
 *   <li>{@link Cue#SHOUT}: a raider's or guard's gruff bark as they turn on the survivor.</li>
 * </ul>
 */
public final class CitySounds {
    /** The sounds the city plays where something happens, with their lengths in seconds. */
    public enum Cue {
        METAL_GROAN(1.6f), SHUTTER_BANG(0.9f), GLASS_TINKLE(1.1f), MASONRY_FALL(1.8f), WATER_LAP(1.2f),
        WOOD_CREAK(0.7f), PIPE_KNOCK(0.5f), INFECTED_RASP(1.2f), VEIL_PULSE(1.0f), LAMP_HUM(1.0f), SHOUT(0.6f);

        public final float seconds;

        Cue(float seconds) {
            this.seconds = seconds;
        }
    }

    static final long SEED = 0x43495459534f554eL;
    static final String FOOT_METAL = "FootMetal", FOOT_RUBBLE = "FootRubble";
    /** Footstep banks, each of {@link VariantBank#COUNT} takes named by {@link VariantBank#key}. */
    static final String[] FOOTSTEPS = {FOOT_METAL, FOOT_RUBBLE};
    /** The lamp hum's fundamental: whole cycles fill its one second exactly. */
    static final float HUM_HZ = 100f;
    private static final int RATE = ProceduralAudio.RATE;

    private CitySounds() {
    }

    static void synthesize(BiConsumer<String, float[]> sink) {
        Random noise = new Random(SEED);
        for (String step : FOOTSTEPS) {
            for (int take = 0; take < VariantBank.COUNT; take++) {
                sink.accept(VariantBank.key(step, take), step.equals(FOOT_METAL) ? metalStep(noise) : rubbleStep(noise));
            }
        }
        for (Cue cue : Cue.values()) {
            sink.accept(key(cue), switch (cue) {
                case METAL_GROAN -> groan(noise);
                case SHUTTER_BANG -> shutter(noise);
                case GLASS_TINKLE -> tinkle(noise);
                case MASONRY_FALL -> masonry(noise);
                case WATER_LAP -> lap(noise);
                case WOOD_CREAK -> creak(noise);
                case PIPE_KNOCK -> knock(noise);
                case INFECTED_RASP -> rasp(noise);
                case VEIL_PULSE -> pulse(noise);
                case LAMP_HUM -> hum(noise);
                case SHOUT -> shout(noise);
            });
        }
    }

    public static String key(Cue cue) {
        return "City" + cue.name();
    }

    private static float[] buffer(float seconds) {
        return new float[(int) (seconds * RATE)];
    }

    private static float white(Random noise) {
        return noise.nextFloat() * 2 - 1;
    }

    /** One step of a two-pole resonator at {@code hz}; {@code r} near 1 rings longer. */
    private static float resonate(float in, float[] state, double hz, double r) {
        double w = 2 * Math.PI * hz / RATE;
        float y = (float) (in * (1 - r) + 2 * r * Math.cos(w) * state[0] - r * r * state[1]);
        state[1] = state[0];
        state[0] = y;
        return y;
    }

    // ------------------------------------------------------------------
    // Footsteps
    // ------------------------------------------------------------------

    /** A heel on thin metal: a short scuff exciting three ringing modes, each take slightly retuned. */
    private static float[] metalStep(Random noise) {
        float[] out = buffer(0.12f);
        float tune = 0.92f + noise.nextFloat() * 0.16f;
        float[] modes = {410 * tune, 1120 * tune, 2390 * tune};
        float[] decay = {26, 38, 58}, gain = {0.55f, 0.35f, 0.2f};
        float scuff = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float s = 0;
            for (int m = 0; m < modes.length; m++) {
                s += (float) (Math.sin(2 * Math.PI * modes[m] * t) * Math.exp(-decay[m] * t)) * gain[m];
            }
            float w = white(noise);
            scuff += 0.55f * (w - scuff);
            s += (w - scuff) * (float) Math.exp(-t * 70) * 0.35f;
            out[i] = s * Math.min(1f, t / 0.002f);
        }
        return out;
    }

    /** Grit underfoot: three to five small crunches, the first with a dull thump. */
    private static float[] rubbleStep(Random noise) {
        float[] out = buffer(0.15f);
        int grains = 3 + noise.nextInt(3);
        for (int g = 0; g < grains; g++) {
            int at = g == 0 ? 0 : (int) (RATE * (0.01f + noise.nextFloat() * 0.08f));
            int length = (int) (RATE * (0.006f + noise.nextFloat() * 0.01f));
            float centre = 1500 + noise.nextFloat() * 2600, level = g == 0 ? 0.7f : 0.3f + noise.nextFloat() * 0.35f;
            float[] state = new float[2];
            for (int i = 0; i < length && at + i < out.length; i++) {
                float local = i / (float) length;
                float env = Math.min(1f, i / (RATE * 0.001f)) * (1 - local) * (1 - local);
                out[at + i] += resonate(white(noise), state, centre, 0.93) * env * level * 3f;
            }
        }
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            out[i] += (float) (Math.sin(2 * Math.PI * 170 * t) * Math.exp(-t * 32)) * 0.25f * Math.min(1f, t / 0.002f);
        }
        return out;
    }

    // ------------------------------------------------------------------
    // The city around the survivor
    // ------------------------------------------------------------------

    /** A slow stick-slip grind of sheet metal: a gliding buzz through two plate resonances. */
    private static float[] groan(Random noise) {
        float[] out = buffer(Cue.METAL_GROAN.seconds);
        float[] a = new float[2], b = new float[2];
        double phase = 0;
        float rumble = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE, f = i / (float) out.length;
            double hz = 62 - 14 * f + 3 * Math.sin(2 * Math.PI * 2.7 * t);
            phase += hz / RATE;
            float saw = (float) (2 * (phase - Math.floor(phase + 0.5)));
            float slip = saw * (0.8f + 0.2f * white(noise));
            float body = resonate(slip, a, 340, 0.985) * 6f + resonate(slip, b, 910, 0.98) * 4f;
            rumble += 0.01f * (white(noise) - rumble);
            float env = (float) (Math.min(1.0, t / 0.45) * Math.min(1.0, (Cue.METAL_GROAN.seconds - t) / 0.55))
                    * (0.8f + 0.2f * (float) Math.sin(2 * Math.PI * 3.1 * t));
            out[i] = (body * 0.6f + rumble * 2.5f) * env;
        }
        return out;
    }

    /** A loose shutter: two slaps of a panel on its frame, each with a short rattle after it. */
    private static float[] shutter(Random noise) {
        float[] out = buffer(Cue.SHUTTER_BANG.seconds);
        float low = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float s = 0;
            for (int k = 0; k < 2; k++) {
                float local = t - (k == 0 ? 0.02f : 0.38f), level = k == 0 ? 1f : 0.62f;
                if (local < 0) { continue; }
                float w = white(noise);
                low += 0.18f * (w - low);
                float slap = low * (float) Math.exp(-local * 38) * 1.4f;
                float thunk = (float) (Math.sin(2 * Math.PI * 138 * local) * Math.exp(-local * 26));
                float rattle = local < 0.14f ? w * 0.22f * (float) Math.max(0, Math.sin(2 * Math.PI * 37 * local))
                        * (1 - local / 0.14f) : 0;
                s += (slap + thunk * 0.7f + rattle) * level * Math.min(1f, local / 0.001f);
            }
            out[i] = s;
        }
        return out;
    }

    /** A pane giving way far off: a crunch, then its pieces ringing down, ever sparser. */
    private static float[] tinkle(Random noise) {
        float[] out = buffer(Cue.GLASS_TINKLE.seconds);
        float at = 0.03f, level = 0.75f;
        for (int k = 0; k < 11; k++) {
            float hz = 2600 + noise.nextFloat() * 2700, decay = 30 + noise.nextFloat() * 35;
            int start = (int) (at * RATE);
            for (int i = start; i < out.length; i++) {
                float local = (i - start) / (float) RATE;
                float env = (float) Math.exp(-decay * local) * Math.min(1f, local / 0.0008f);
                if (env < 0.001f) { break; }
                out[i] += (float) (Math.sin(2 * Math.PI * hz * local) + 0.45 * Math.sin(2 * Math.PI * hz * 2.76 * local))
                        * env * level;
            }
            at += 0.025f + noise.nextFloat() * 0.06f * (1 + k * 0.35f);
            level *= 0.82f;
        }
        float hiss = 0;
        for (int i = 0; i < (int) (0.05f * RATE); i++) {
            float w = white(noise);
            hiss += 0.6f * (w - hiss);
            out[i] += (w - hiss) * 0.5f * (1 - i / (0.05f * RATE));
        }
        return out;
    }

    /** Brick and plaster coming down somewhere: a low rumble with grit and a few heavy thuds. */
    private static float[] masonry(Random noise) {
        float[] out = buffer(Cue.MASONRY_FALL.seconds);
        float rumble = 0, rumble2 = 0;
        float[] thuds = {0.05f + noise.nextFloat() * 0.1f, 0.4f + noise.nextFloat() * 0.3f, 0.9f + noise.nextFloat() * 0.3f};
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float w = white(noise);
            rumble += 0.012f * (w - rumble);
            rumble2 += 0.03f * (rumble - rumble2);
            float env = (float) (Math.min(1.0, t / 0.15) * Math.exp(-t * 1.6));
            float s = rumble2 * 9f * env;
            for (int k = 0; k < thuds.length; k++) {
                float local = t - thuds[k];
                if (local > 0) {
                    s += (float) (Math.sin(2 * Math.PI * (75 - 10 * k) * local) * Math.exp(-local * 14)) * (0.6f - 0.15f * k);
                }
            }
            out[i] = s;
        }
        for (int c = 0; c < 14; c++) {
            int at = (int) (RATE * noise.nextFloat() * 1.2f);
            float[] state = new float[2];
            float centre = 1100 + noise.nextFloat() * 2000, level = 0.15f + noise.nextFloat() * 0.25f;
            for (int i = 0; i < RATE * 0.012f && at + i < out.length; i++) {
                float local = i / (RATE * 0.012f);
                out[at + i] += resonate(white(noise), state, centre, 0.9) * (1 - local) * level * 3f;
            }
        }
        return out;
    }

    /** Standing water slopping twice against a wall, with a small gurgle. */
    private static float[] lap(Random noise) {
        float[] out = buffer(Cue.WATER_LAP.seconds);
        float lowState = 0, highState = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float w = white(noise);
            highState += 0.17f * (w - highState);
            lowState += 0.04f * (highState - lowState);
            float band = highState - lowState;
            float env = bump(t, 0.12f, 0.36f) + 0.7f * bump(t, 0.66f, 0.4f);
            float gurgle = (float) (Math.sin(2 * Math.PI * (190 + 70 * Math.sin(2 * Math.PI * 7 * t)) * t)
                    * Math.max(0, Math.sin(2 * Math.PI * 11 * t))) * 0.12f;
            out[i] = (band * 1.8f + gurgle) * env;
        }
        return out;
    }

    /** A board or beam settling: slow micro-slips gliding down through a woody resonance. */
    private static float[] creak(Random noise) {
        float[] out = buffer(Cue.WOOD_CREAK.seconds);
        float[] wood = new float[2], body = new float[2];
        double phase = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE, f = i / (float) out.length;
            double hz = 96 - 38 * f + 6 * Math.sin(2 * Math.PI * 5 * t);
            double before = phase;
            phase += hz / RATE;
            float slip = Math.floor(phase) != Math.floor(before) ? 1f + 0.4f * white(noise) : 0f;
            float s = resonate(slip, wood, 640, 0.975) * 9f + resonate(slip, body, 1700, 0.95) * 4f;
            out[i] = s * (float) Math.sin(Math.PI * f);
        }
        return out;
    }

    /** A pipe struck twice inside a wall: a click, then a metal ring. */
    private static float[] knock(Random noise) {
        float[] out = buffer(Cue.PIPE_KNOCK.seconds);
        float[] modes = {312, 868, 1655};
        for (int k = 0; k < 2; k++) {
            int start = (int) (RATE * (k == 0 ? 0.005f : 0.215f));
            float level = k == 0 ? 1f : 0.7f;
            for (int i = start; i < out.length; i++) {
                float local = (i - start) / (float) RATE;
                float s = 0;
                for (int m = 0; m < modes.length; m++) {
                    s += (float) (Math.sin(2 * Math.PI * modes[m] * local) * Math.exp(-(16 + 10 * m) * local)) / (m + 1);
                }
                float click = local < 0.004f ? white(noise) * (1 - local / 0.004f) * 0.6f : 0;
                out[i] += (s * 0.6f + click) * level * Math.min(1f, local / 0.0008f);
            }
        }
        return out;
    }

    /** A Veilbound at rest: a wet rising breath in, then a rasping flutter out. */
    private static float[] rasp(Random noise) {
        float[] out = buffer(Cue.INFECTED_RASP.seconds);
        float[] inhale = new float[2], throat = new float[2];
        float low = 0;
        double phase = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float w = white(noise);
            low += 0.1f * (w - low);
            float s = 0;
            if (t < 0.5f) {
                float env = (float) Math.sin(Math.PI * t / 0.5f);
                s += resonate(w, inhale, 700 + 500 * t, 0.97) * env * 2.5f;
            }
            if (t > 0.52f && t < 1.17f) {
                float local = (t - 0.52f) / 0.65f;
                phase += (64 - 14 * local) / RATE;
                float glottal = (float) Math.max(0, Math.sin(2 * Math.PI * phase)) * (0.6f + 0.4f * w);
                float env = (float) Math.sin(Math.PI * local);
                s += (resonate(glottal, throat, 480, 0.96) * 3.5f + low * 0.5f) * env;
            }
            out[i] = s;
        }
        return out;
    }

    /** A Veilmaw resting in its growth: a deep throb with a wet membrane flutter and a faint shimmer. */
    private static float[] pulse(Random noise) {
        float[] out = buffer(Cue.VEIL_PULSE.seconds);
        float[] membrane = new float[2];
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float throb = (float) ((Math.sin(2 * Math.PI * 41 * t) + 0.4 * Math.sin(2 * Math.PI * 82 * t))
                    * Math.min(1.0, t / 0.06) * Math.exp(-t * 3.2));
            float flutter = resonate(white(noise), membrane, 230, 0.985) * 4f
                    * (0.5f + 0.5f * (float) Math.sin(2 * Math.PI * 14 * t)) * (float) Math.exp(-t * 2.5);
            float shimmer = (float) (Math.sin(2 * Math.PI * 2210 * t) * Math.sin(Math.PI * t / Cue.VEIL_PULSE.seconds)) * 0.04f;
            out[i] = throb * 0.8f + flutter * 0.5f + shimmer;
        }
        return out;
    }

    /** A fed lamp's mains hum and buzz: whole cycles of {@value #HUM_HZ} Hz, so plays join seamlessly. */
    private static float[] hum(Random noise) {
        float[] out = buffer(Cue.LAMP_HUM.seconds);
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) RATE;
            double x = 2 * Math.PI * HUM_HZ * t;
            double s = Math.sin(x) * 0.5 + Math.sin(2 * x) * 0.3 + Math.sin(3 * x) * 0.12;
            double buzz = Math.abs(Math.sin(x)) - 2 / Math.PI; // a rectified edge, zero mean
            out[i] = (float) (s * 0.5 + buzz * 0.18) + white(noise) * 0.004f;
        }
        return out;
    }

    /** A gruff wordless bark: a voiced pulse train through two vowel formants, falling in pitch. */
    private static float[] shout(Random noise) {
        float[] out = buffer(Cue.SHOUT.seconds);
        float[] f1 = new float[2], f2 = new float[2], f3 = new float[2];
        double phase = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE, f = i / (float) out.length;
            double before = phase;
            phase += (152 - 34 * f) / RATE;
            float pulse = Math.floor(phase) != Math.floor(before) ? 1f : 0f;
            float source = pulse + white(noise) * 0.04f;
            float s = resonate(source, f1, 720, 0.985) * 10f + resonate(source, f2, 1180, 0.98) * 7f
                    + resonate(source, f3, 2550, 0.97) * 3f;
            float env = Math.min(1f, t / 0.02f) * (f < 0.7f ? 1f : (1 - f) / 0.3f);
            out[i] = s * env;
        }
        return out;
    }

    /** A raised-cosine swell centred at {@code centre} lasting {@code width} seconds. */
    private static float bump(float t, float centre, float width) {
        float local = (t - centre) / width;
        return Math.abs(local) > 0.5f ? 0 : (float) (0.5 + 0.5 * Math.cos(2 * Math.PI * local));
    }
}
