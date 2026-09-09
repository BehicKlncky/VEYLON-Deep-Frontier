package com.veylon.engine;

import java.util.Random;

import static com.veylon.engine.AudioFilters.*;
import java.util.function.BiConsumer;

/** Pure procedural PCM recipes, independent of OpenAL and simulation randomness. */
final class ProceduralAudio {

    static final int RATE = 44100;
    private final Random rng;

    /** Declared looping durations, separate from the one-shot recipe contracts. */
    static float loopSeconds(String name) {
        return switch (name) {
            case "Rain" -> 2.5f;
            case "Wind", "Cave" -> 4f;
            case "Fire" -> 3f;
            case "Crickets", "Beacon" -> 2f;
            default -> 0f;
        };
    }


    ProceduralAudio(Random rng) {
        this.rng = rng;
    }

    /** Emits each original recipe once, retaining no sample arrays. */
    void synthesize(BiConsumer<String, float[]> sink) {
        sink.accept("FootGrass", noiseBurst(0.10f, GRASS_HZ, 3.5f, 0.5f));
        sink.accept("FootStone", noiseBurst(0.07f, GRIT_HZ, 6f, 0.7f));
        sink.accept("FootWood", mix(noiseBurst(0.08f, WOOD_HZ, 5f, 0.6f), tone(160, 0.08f, 10f, 0.3f)));
        sink.accept("FootSnow", noiseBurst(0.13f, SNOW_HZ, 3f, 0.45f));
        sink.accept("FootWater", noiseBurst(0.16f, WATER_HZ, 2.5f, 0.6f));
        sink.accept("HitSoft", noiseBurst(0.06f, SOFT_HIT_HZ, 8f, 0.6f));
        sink.accept("HitStone", mix(noiseBurst(0.05f, STONE_HZ, 12f, 0.8f), tone(900, 0.04f, 25f, 0.25f)));
        sink.accept("HitWood", mix(noiseBurst(0.06f, IMPACT_HZ, 10f, 0.7f), tone(240, 0.06f, 15f, 0.4f)));
        sink.accept("Break", mix(noiseBurst(0.22f, DEBRIS_HZ, 4f, 0.9f), tone(110, 0.18f, 7f, 0.4f)));
        sink.accept("Place", mix(noiseBurst(0.09f, PLACE_HZ, 8f, 0.7f), tone(200, 0.07f, 14f, 0.35f)));
        sink.accept("Click", tone(1400, 0.035f, 50f, 0.5f));
        sink.accept("Eat", chew());
        sink.accept("Drink", gulp());
        sink.accept("Boil", noiseBurst(0.5f, POUR_HZ, 2f, 0.5f));
        sink.accept("Hit", mix(noiseBurst(0.08f, IMPACT_HZ, 9f, 0.9f), tone(120, 0.09f, 12f, 0.6f)));
        sink.accept("Hurt", grunt());
        sink.accept("Swing", swish());
        sink.accept("Craft", mix(tone(520, 0.08f, 12f, 0.4f), tone(780, 0.12f, 8f, 0.3f)));
        sink.accept("Equip", noiseBurst(0.14f, WATER_HZ, 5f, 0.55f));
        sink.accept("ToolBreak", mix(tone(620, 0.07f, 22f, 0.6f), noiseBurst(0.2f, SWISH_HIGH_HZ, 6f, 0.7f)));
        sink.accept("Cough", cough());
        sink.accept("Sleep", chime(new float[]{392, 494, 587}, 0.32f));
        sink.accept("Discover", chime(new float[]{523, 659, 784, 1047}, 0.26f));
        sink.accept("Quest", chime(new float[]{440, 554, 659}, 0.3f));
        sink.accept("Thunder", thunder());
        sink.accept("Howl", howl());
        sink.accept("Growl", growl());
        sink.accept("Chirp", chirp());
        sink.accept("Flap", flap());
        sink.accept("Deer", deerCall());
        sink.accept("BowDraw", bowDraw());
        sink.accept("BowRelease", bowRelease());
        sink.accept("ArrowImpact", mix(noiseBurst(0.05f, IMPACT_HZ, 16f, 0.6f), tone(300, 0.05f, 30f, 0.35f)));
        sink.accept("BulletImpact", mix(noiseBurst(0.04f, BULLET_HZ, 22f, 0.7f), tone(1200, 0.03f, 40f, 0.3f)));
        sink.accept("Musket", gunshot(false));
        sink.accept("Pistol", gunshot(true));
        sink.accept("DryFire", dryFireClick());
        sink.accept("Reload", reloadRustle());
        sink.accept("Fuse", fuseHiss());
        sink.accept("Explosion", explosionBoom());
        sink.accept("AlarmBell", alarmBell());
        sink.accept("Gate", gateCreak());
        sink.accept("Rain", rainLoop());
        sink.accept("Wind", windLoop());
        sink.accept("Fire", fireLoop());
        sink.accept("Cave", caveLoop());
        sink.accept("Crickets", cricketsLoop());
        sink.accept("Beacon", beaconLoop());
    }

    // ---- 0.3.0 combat & settlement synthesis ----

    /** Rising creak of a bow stave under tension. */
    private float[] bowDraw() {
        int n = len(0.45f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(STAVE_LOW_HZ + t * (STAVE_HIGH_HZ - STAVE_LOW_HZ)) * (x - y);
            float creak = (float) Math.sin(2 * Math.PI * (90 + t * 160) * i / RATE) * 0.14f;
            out[i] = (y * 0.8f + creak) * (0.3f + t * 0.7f) * 0.5f;
        }
        return out;
    }

    /** String twang plus a short air swish. */
    private float[] bowRelease() {
        float[] twang = new float[len(0.28f)];
        double phase = 0;
        for (int i = 0; i < twang.length; i++) {
            float t = (float) i / RATE;
            float f = 210 - t * 90;
            phase += 2 * Math.PI * f / RATE;
            twang[i] = (float) (Math.sin(phase) * 0.5 + Math.sin(phase * 2.7) * 0.2)
                    * (float) Math.exp(-16 * t);
        }
        return mix(twang, swish());
    }

    /** Black-powder report: sharp crack into a low rolling boom. */
    private float[] gunshot(boolean pistol) {
        float dur = pistol ? 0.7f : 1.15f;
        int n = len(dur);
        float[] out = new float[n];
        float y = 0, y2 = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            // Crack: bright noise with a very fast decay.
            y += alpha(pistol ? PISTOL_HZ : STONE_HZ) * (x - y);
            float crack = y * (float) Math.exp(-60 * t) * 1.4f;
            // Boom: heavily low-passed noise, slower decay for muskets.
            y2 += alpha(BOOM_HZ) * (x - y2);
            float boom = y2 * (float) Math.exp(-(pistol ? 9f : 5.5f) * t) * 2.4f;
            out[i] = crack + boom;
        }
        return out;
    }

    private float[] dryFireClick() {
        float[] a = tone(1600, 0.03f, 60f, 0.4f);
        float[] out = new float[len(0.16f)];
        System.arraycopy(a, 0, out, 0, a.length);
        float[] b = tone(900, 0.04f, 45f, 0.35f);
        int off = len(0.07f);
        for (int i = 0; i < b.length && off + i < out.length; i++) {
            out[off + i] += b[i];
        }
        return out;
    }

    /** Powder pour, ball tap, ramrod slide. */
    private float[] reloadRustle() {
        float[] out = new float[len(0.9f)];
        float[] pour = noiseBurst(0.3f, GRASS_HZ, 6f, 0.4f);
        System.arraycopy(pour, 0, out, 0, pour.length);
        float[] tap = mix(tone(700, 0.05f, 30f, 0.4f), noiseBurst(0.04f, DEBRIS_HZ, 20f, 0.4f));
        int off = len(0.42f);
        for (int i = 0; i < tap.length && off + i < out.length; i++) {
            out[off + i] += tap[i];
        }
        float[] slide = noiseBurst(0.22f, WATER_HZ, 8f, 0.35f);
        off = len(0.6f);
        for (int i = 0; i < slide.length && off + i < out.length; i++) {
            out[off + i] += slide[i];
        }
        return out;
    }

    /** Sputtering fuse hiss. */
    private float[] fuseHiss() {
        int n = len(1.2f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(GRIT_HZ) * (x - y);
            float sputter = rng.nextFloat() < FUSE_EVENTS_PER_SECOND / RATE ? 0.5f : 0f;
            out[i] = y * 0.35f + sputter;
        }
        return fadeEnds(out);
    }

    /** Deep detonation: sub thump, mid boom, long rumble tail. */
    private float[] explosionBoom() {
        int n = len(2.2f);
        float[] out = new float[n];
        float y = 0;
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(DEEP_RUMBLE_HZ) * (x - y);
            float rumble = y * (float) Math.exp(-2.2 * t) * 2.6f;
            float f = 52 - t * 18;
            phase += 2 * Math.PI * Math.max(20, f) / RATE;
            float thump = (float) Math.sin(phase) * (float) Math.exp(-7 * t) * 0.9f;
            float crack = ((rng.nextFloat() * 2f - 1f) * NOISE_SCALE) * (float) Math.exp(-45 * t) * 0.8f;
            out[i] = rumble + thump + crack;
        }
        return out;
    }

    /** Three urgent bronze bell strikes. */
    private float[] alarmBell() {
        int n = len(1.6f);
        float[] out = new float[n];
        for (int strike = 0; strike < 3; strike++) {
            int off = len(0.5f * strike);
            for (float[] partial : new float[][]{{520, 0.5f}, {780, 0.3f}, {1240, 0.18f}}) {
                float[] ring = tone(partial[0], 0.55f, 5.5f, partial[1]);
                for (int i = 0; i < ring.length && off + i < n; i++) {
                    out[off + i] += ring[i];
                }
            }
            float[] clank = noiseBurst(0.03f, SWISH_HIGH_HZ, 30f, 0.4f);
            for (int i = 0; i < clank.length && off + i < n; i++) {
                out[off + i] += clank[i];
            }
        }
        return out;
    }

    /** Heavy timber gate swinging on rope hinges. */
    private float[] gateCreak() {
        int n = len(0.7f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(GROWL_HZ) * (x - y);
            float squeal = (float) Math.sin(2 * Math.PI * (140 + Math.sin(t * 9) * 60) * i / RATE)
                    * 0.16f * (float) Math.sin(Math.PI * t);
            out[i] = y * 1.2f * (float) Math.sin(Math.PI * t) + squeal;
        }
        return out;
    }

    private static int len(float seconds) {
        return (int) (seconds * RATE);
    }

    /** White noise through a one-pole lowpass with an exponential decay envelope. */
    private float[] noiseBurst(float seconds, float cutoffHz, float decay, float amp) {
        int n = len(seconds);
        float lowpass = alpha(cutoffHz);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += lowpass * (x - y);
            float t = (float) i / RATE;
            out[i] = y * (float) Math.exp(-decay * t) * amp;
        }
        return out;
    }

    private float[] tone(float freq, float seconds, float decay, float amp) {
        int n = len(seconds);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            out[i] = (float) (Math.sin(2 * Math.PI * freq * t) * Math.exp(-decay * t)) * amp;
        }
        return out;
    }

    private static float[] mix(float[] a, float[] b) {
        int n = Math.max(a.length, b.length);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float v = 0;
            if (i < a.length) v += a[i];
            if (i < b.length) v += b[i];
            out[i] = v;
        }
        return out;
    }

    private float[] chew() {
        float[] out = new float[len(0.5f)];
        for (int c = 0; c < 3; c++) {
            float[] bite = noiseBurst(0.09f, POUR_HZ, 9f, 0.55f);
            int off = len(0.16f) * c;
            for (int i = 0; i < bite.length && off + i < out.length; i++) {
                out[off + i] += bite[i];
            }
        }
        return out;
    }

    private float[] gulp() {
        float[] out = new float[len(0.6f)];
        for (int c = 0; c < 3; c++) {
            int off = len(0.18f) * c;
            float f = 300 - c * 50;
            float[] g = tone(f, 0.12f, 14f, 0.5f);
            for (int i = 0; i < g.length && off + i < out.length; i++) {
                out[off + i] += g[i] * (1 + 0.4f * (float) Math.sin(2 * Math.PI * GULP_MODULATION_HZ * i / RATE));
            }
        }
        return out;
    }

    private float[] grunt() {
        int n = len(0.22f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float f = 130 - t * 120;
            float x = (float) Math.sin(2 * Math.PI * f * t) * 0.7f + (rng.nextFloat() - 0.5f) * 0.5f * NOISE_SCALE;
            y += alpha(WATER_HZ) * (x - y);
            out[i] = y * (float) Math.exp(-8 * t);
        }
        return out;
    }

    private float[] swish() {
        int n = len(0.18f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            float lp = alpha(SWISH_LOW_HZ + (SWISH_HIGH_HZ - SWISH_LOW_HZ) * (float) Math.sin(Math.PI * t));
            y += lp * (x - y);
            out[i] = y * (float) Math.sin(Math.PI * t) * 0.45f;
        }
        return out;
    }

    private float[] cough() {
        float[] a = noiseBurst(0.12f, WATER_HZ, 10f, 0.7f);
        float[] out = new float[len(0.35f)];
        System.arraycopy(a, 0, out, 0, a.length);
        float[] b = noiseBurst(0.10f, COUGH_HZ, 12f, 0.55f);
        int off = len(0.18f);
        for (int i = 0; i < b.length && off + i < out.length; i++) {
            out[off + i] += b[i];
        }
        return out;
    }

    private float[] chime(float[] freqs, float noteSec) {
        int n = len(noteSec * freqs.length + 0.5f);
        float[] out = new float[n];
        for (int k = 0; k < freqs.length; k++) {
            int off = len(noteSec * k);
            float[] note = tone(freqs[k], 0.7f, 4f, 0.35f);
            for (int i = 0; i < note.length && off + i < n; i++) {
                out[off + i] += note[i];
            }
        }
        return out;
    }

    private float[] thunder() {
        int n = len(2.4f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(THUNDER_HZ) * (x - y);
            float env = (float) (Math.exp(-1.6 * t) * (0.6 + 0.4 * Math.sin(t * 9)));
            out[i] = y * env * 1.6f;
        }
        return out;
    }

    private float[] howl() {
        int n = len(1.9f);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float prog = t / 1.9f;
            // Rise, hold, fall with vibrato.
            float f = prog < 0.3f ? 380 + prog / 0.3f * 270
                    : (prog < 0.7f ? 650 : 650 - (prog - 0.7f) / 0.3f * 220);
            f += (float) Math.sin(t * 35) * 9;
            phase += 2 * Math.PI * f / RATE;
            float env = (float) (Math.sin(Math.PI * Math.min(1, prog * 1.04)) * 0.55);
            out[i] = (float) (Math.sin(phase) * 0.8 + Math.sin(phase * 2) * 0.2) * env;
        }
        return out;
    }

    private float[] growl() {
        int n = len(0.9f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(GROWL_HZ) * (x - y);
            float am = 0.6f + 0.4f * (float) Math.sin(2 * Math.PI * 28 * t);
            float env = (float) Math.sin(Math.PI * Math.min(1f, t / 0.9f));
            out[i] = y * am * env * 1.8f;
        }
        return out;
    }

    private float[] chirp() {
        float[] out = new float[len(0.45f)];
        for (int c = 0; c < 3; c++) {
            int off = len(0.15f * c);
            float base = 2800 + rng.nextInt(800);
            int m = len(0.09f);
            double phase = 0;
            for (int i = 0; i < m && off + i < out.length; i++) {
                float t = (float) i / RATE;
                float f = base + (float) Math.sin(t * 200) * 400;
                phase += 2 * Math.PI * f / RATE;
                out[off + i] += (float) (Math.sin(phase)
                        * Math.sin(Math.PI * i / (float) m)) * 0.3f;
            }
        }
        return out;
    }

    private float[] flap() {
        float[] out = new float[len(0.5f)];
        for (int c = 0; c < 4; c++) {
            float[] puff = noiseBurst(0.06f, WING_HZ, 14f, 0.4f);
            int off = len(0.11f * c);
            for (int i = 0; i < puff.length && off + i < out.length; i++) {
                out[off + i] += puff[i];
            }
        }
        return out;
    }

    private float[] deerCall() {
        int n = len(0.7f);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float f = 520 - t * 180 + (float) Math.sin(t * 60) * 25;
            phase += 2 * Math.PI * f / RATE;
            float env = (float) Math.sin(Math.PI * Math.min(1f, t / 0.7f));
            out[i] = (float) (Math.sin(phase) * 0.5 + Math.sin(phase * 3) * 0.15) * env * 0.7f;
        }
        return out;
    }

    // ---- Loops (seamless via full-length noise / periodic content) ----

    private float[] rainLoop() {
        int n = len(2.5f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(WOOD_HZ) * (x - y);
            out[i] = y * 0.5f;
            // Occasional droplet plinks.
            if (rng.nextFloat() < RAIN_EVENTS_PER_SECOND / RATE) {
                out[i] += 0.3f;
            }
        }
        return fadeEnds(out);
    }

    private float[] windLoop() {
        int n = len(4f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(BOOM_HZ) * (x - y);
            // Two slow gust cycles that line up with the loop length.
            float gust = 0.55f + 0.45f * (float) Math.sin(2 * Math.PI * t / 4f)
                    * (float) Math.sin(2 * Math.PI * t / 2f);
            out[i] = y * gust * 1.4f;
        }
        return fadeEnds(out);
    }

    private float[] fireLoop() {
        int n = len(3f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(SNOW_HZ) * (x - y);
            out[i] = y * 0.55f;
        }
        // Crackle pops.
        for (int p = 0; p < 26; p++) {
            int at = rng.nextInt(n - len(0.05f));
            float amp = 0.25f + rng.nextFloat() * 0.4f;
            int m = len(0.012f + rng.nextFloat() * 0.02f);
            for (int i = 0; i < m; i++) {
                out[at + i] += ((rng.nextFloat() * 2f - 1f) * NOISE_SCALE)
                        * amp * (float) Math.exp(-12.0 * i / m);
            }
        }
        return fadeEnds(out);
    }

    private float[] caveLoop() {
        int n = len(4f);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            out[i] = (float) (Math.sin(2 * Math.PI * 55 * t) * 0.35
                    + Math.sin(2 * Math.PI * 82.5 * t) * 0.2
                    + Math.sin(2 * Math.PI * 0.25 * t) * 0.08);
        }
        return out; // pure periodic content, already loop-clean at 4 s
    }

    private float[] cricketsLoop() {
        int n = len(2f);
        float[] out = new float[n];
        for (int burst = 0; burst < 14; burst++) {
            int at = rng.nextInt(n - len(0.06f));
            float f = 3800 + rng.nextInt(700);
            int m = len(0.045f);
            for (int i = 0; i < m; i++) {
                float t = (float) i / RATE;
                out[at + i] += (float) (Math.sin(2 * Math.PI * f * t)
                        * Math.sin(2 * Math.PI * 60 * t)
                        * Math.sin(Math.PI * i / (float) m)) * 0.22f;
            }
        }
        return fadeEnds(out);
    }

    private float[] beaconLoop() {
        int n = len(2f);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float pulse = 0.6f + 0.4f * (float) Math.sin(2 * Math.PI * t / 2f);
            out[i] = (float) (Math.sin(2 * Math.PI * 220 * t) * 0.3
                    + Math.sin(2 * Math.PI * 331 * t) * 0.18) * pulse;
        }
        return out;
    }

    private static float[] fadeEnds(float[] s) {
        int fade = Math.min(s.length / 8, len(LEGACY_FADE_SECONDS));
        for (int i = 0; i < fade; i++) {
            float k = (float) i / fade;
            s[i] *= k;
            s[s.length - 1 - i] *= k;
        }
        return s;
    }
}
