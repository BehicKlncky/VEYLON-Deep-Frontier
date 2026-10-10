package com.veylon.engine;

import java.util.Random;
import java.util.function.BiConsumer;

import static com.veylon.engine.AudioFilters.NOISE_SCALE;
import static com.veylon.engine.AudioFilters.alpha;

/**
 * Doors and windows (urban package 04): a wooden door's creak and latch, its
 * thud shut, a metal door's clank, a locked handle's rattle, a door forced off
 * its frame, glass shattering and a sash sliding.
 *
 * <p>Synthesized from a generator of their own ({@link #SEED}) after every
 * earlier recipe and before the body-fire sounds, so no existing buffer
 * changes a single sample.
 */
final class UrbanFixtureSounds {

    /** Seed of the fixture recipes' own generator. */
    static final long SEED = 0x55524241_4e444f52L;
    /** Buffer names, in the order they are made. */
    static final String DOOR_OPEN = "DoorOpen", DOOR_CLOSE = "DoorClose", METAL_DOOR = "MetalDoor",
            DOOR_RATTLE = "DoorRattle", DOOR_FORCED = "DoorForced", GLASS_BREAK = "GlassBreak",
            WINDOW_SLIDE = "WindowSlide";

    private static final int RATE = ProceduralAudio.RATE;

    private UrbanFixtureSounds() {
    }

    static void synthesize(Random rng, BiConsumer<String, float[]> sink) {
        sink.accept(DOOR_OPEN, doorOpen(rng));
        sink.accept(DOOR_CLOSE, doorClose(rng));
        sink.accept(METAL_DOOR, metalDoor(rng));
        sink.accept(DOOR_RATTLE, rattle(rng));
        sink.accept(DOOR_FORCED, forced(rng));
        sink.accept(GLASS_BREAK, glass(rng));
        sink.accept(WINDOW_SLIDE, slide(rng));
    }

    private static float[] buffer(float seconds) {
        return new float[(int) (seconds * RATE)];
    }

    /** A short latch click at the start of a sample. */
    private static void latch(float[] out, int at, float gain) {
        for (int i = 0; i < 900 && at + i < out.length; i++) {
            float t = i / (float) RATE;
            out[at + i] += (float) Math.sin(2 * Math.PI * 2300 * t) * (float) Math.exp(-t * 260) * gain;
        }
    }

    /** Latch, then a dry hinge creak rising and falling in pitch. */
    private static float[] doorOpen(Random rng) {
        float[] out = buffer(0.55f);
        latch(out, 0, 0.35f);
        float y = 0;
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / out.length;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(900) * (x - y);
            float pitch = 210 + (float) Math.sin(t * Math.PI) * 90;
            float creak = (float) Math.sin(2 * Math.PI * pitch * i / RATE) * 0.12f
                    * (0.5f + 0.5f * (float) Math.sin(i / (float) RATE * 2 * Math.PI * 23));
            float env = (float) Math.sin(Math.PI * Math.min(1f, t * 1.1f));
            out[i] += (y * 0.7f + creak) * env;
        }
        return out;
    }

    /** A soft wooden thud and the latch catching. */
    private static float[] doorClose(Random rng) {
        float[] out = buffer(0.35f);
        float y = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(420) * (x - y);
            float body = (float) Math.sin(2 * Math.PI * 95 * t) * 0.55f;
            out[i] = (y * 1.4f + body) * (float) Math.exp(-t * 18);
        }
        latch(out, (int) (0.03f * RATE), 0.3f);
        return out;
    }

    /** Steel on steel: a struck plate ringing out, with a little grit. */
    private static float[] metalDoor(Random rng) {
        float[] out = buffer(0.7f);
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            float ring = (float) (Math.sin(2 * Math.PI * 178 * t) * 0.35
                    + Math.sin(2 * Math.PI * 431 * t) * 0.22
                    + Math.sin(2 * Math.PI * 1163 * t) * 0.10);
            out[i] = ring * (float) Math.exp(-t * 6) + x * 0.25f * (float) Math.exp(-t * 40);
        }
        return out;
    }

    /** A locked handle tried several times. */
    private static float[] rattle(Random rng) {
        float[] out = buffer(0.42f);
        for (int k = 0; k < 5; k++) {
            int at = (int) ((0.02f + k * 0.075f + rng.nextFloat() * 0.015f) * RATE);
            float gain = 0.5f - k * 0.06f;
            for (int i = 0; i < 1600 && at + i < out.length; i++) {
                float t = i / (float) RATE;
                float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
                out[at + i] += ((float) Math.sin(2 * Math.PI * 1650 * t) * 0.5f + x * 0.4f)
                        * (float) Math.exp(-t * 120) * gain;
            }
        }
        return out;
    }

    /** A door forced off its frame: a hard crack, splinters, then the slab falling. */
    private static float[] forced(Random rng) {
        float[] out = buffer(0.9f);
        float crack = 0, low = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            crack += alpha(3000) * (x - crack);
            low += alpha(260) * (x - low);
            float snap = (x - crack) * (float) Math.exp(-t * 30) * 1.1f;
            float splinter = rng.nextFloat() < 0.004f * (float) Math.exp(-t * 4) ? x * 3f : 0f;
            float slab = t > 0.32f ? (float) Math.sin(2 * Math.PI * 70 * (t - 0.32f))
                    * (float) Math.exp(-(t - 0.32f) * 11) * 0.8f + low * 1.6f * (float) Math.exp(-(t - 0.32f) * 9) : 0f;
            out[i] = snap + splinter + slab;
        }
        return out;
    }

    /** Glass breaking: a bright burst, then shards tinkling down. */
    private static float[] glass(Random rng) {
        float[] out = buffer(1.0f);
        float hp = 0;
        for (int i = 0; i < out.length; i++) {
            float t = i / (float) RATE;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            hp += alpha(4200) * (x - hp);
            out[i] = (x - hp) * 1.2f * (float) Math.exp(-t * 14);
        }
        for (int k = 0; k < 22; k++) {
            int at = (int) ((0.05f + rng.nextFloat() * 0.8f) * RATE);
            float hz = 2400 + rng.nextFloat() * 4200;
            float gain = 0.12f + rng.nextFloat() * 0.18f;
            for (int i = 0; i < 2600 && at + i < out.length; i++) {
                float t = i / (float) RATE;
                out[at + i] += (float) Math.sin(2 * Math.PI * hz * t) * (float) Math.exp(-t * 70) * gain;
            }
        }
        return out;
    }

    /** A sash or casement scraping in its frame. */
    private static float[] slide(Random rng) {
        float[] out = buffer(0.32f);
        float y = 0;
        for (int i = 0; i < out.length; i++) {
            float t = (float) i / out.length;
            float x = (rng.nextFloat() * 2f - 1f) * NOISE_SCALE;
            y += alpha(1300) * (x - y);
            out[i] = y * 0.8f * (float) Math.sin(Math.PI * t);
        }
        return out;
    }
}
