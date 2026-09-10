package com.veylon.engine;

import java.util.Arrays;
import java.util.function.Consumer;

/** Transient presentation queue, discarded on load/world replacement and never saved. */
final class ThunderScheduler {
    /** Pending strikes; exceptional storms keep the nearer, more audible events. */
    static final int CAPACITY = 16;
    /** Metres per second at temperate conditions, with four acoustic metres per voxel. */
    static final float SOUND_SPEED = 343, DISTANCE_SCALE = 4;
    /** Distance in voxels that halves the supplemental gain and high-frequency contribution. */
    static final float GAIN_DISTANCE = 100, HF_DISTANCE = 30;
    /** Minimum direct HF transmission of distant thunder and sheltered rain. */
    static final float DISTANT_HF = 0.12f, SHELTER_HF = 0.15f;
    /** Rain filter convergence per second. */
    static final float SHELTER_BLEND = 3;
    record Strike(float x, float y, float z, float distance) { }
    private final Strike[] strikes = new Strike[CAPACITY];
    private final float[] remaining = new float[CAPACITY];
    int played, cancelled;

    void schedule(float x, float y, float z, float lx, float ly, float lz) {
        float dx = x - lx, dy = y - ly, dz = z - lz;
        float distance = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!Float.isFinite(distance)) return;
        int slot = -1, farthest = 0;
        for (int i = 0; i < CAPACITY; i++) {
            if (strikes[i] == null) { slot = i; break; }
            if (strikes[i].distance > strikes[farthest].distance) farthest = i;
        }
        if (slot < 0) {
            if (strikes[farthest].distance <= distance) return;
            slot = farthest;
        }
        strikes[slot] = new Strike(x, y, z, distance);
        remaining[slot] = delay(distance);
    }

    void update(float dt, Consumer<Strike> player) {
        for (int i = 0; i < CAPACITY; i++) {
            if (strikes[i] == null) continue;
            remaining[i] -= Math.max(0, dt);
            if (remaining[i] > 0) continue;
            Strike strike = strikes[i];
            strikes[i] = null;
            played++;
            player.accept(strike);
        }
    }

    static float delay(float distance) { return distance * DISTANCE_SCALE / SOUND_SPEED; }
    static float gain(float distance) { return 1 / (1 + distance / GAIN_DISTANCE); }
    static float highFrequency(float distance) { return DISTANT_HF + (1 - DISTANT_HF) / (1 + distance / HF_DISTANCE); }
    static float shelterBlend(float current, boolean sheltered, float dt) {
        return current + ((sheltered ? SHELTER_HF : 1) - current)
                * (float) -Math.expm1(-Math.max(0, dt) * SHELTER_BLEND);
    }

    int pending() { int count = 0; for (Strike strike : strikes) if (strike != null) count++; return count; }
    void reset() { cancelled += pending(); Arrays.fill(strikes, null); Arrays.fill(remaining, 0); }
}
