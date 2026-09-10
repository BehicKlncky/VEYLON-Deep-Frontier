package com.veylon.engine;

import com.veylon.world.World;
import java.util.Arrays;
import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.EXTEfx.*;

/** Fixed native direct filters, shared ray budget and smoothed per-source occlusion. */
final class AcousticSources {
    /** Seconds before a source may be sampled again. */
    private static final float RESAMPLE_SECONDS = 0.10f;
    /** Per-second convergence of direct-path attenuation. */
    private static final float FILTER_BLEND_SPEED = 8;
    /** EFX air absorption multiplier for positional sounds; zero for listener-relative beds/UI. */
    private static final float AIR_ABSORPTION = 1;
    /** Full occlusion retains these broadband and high-frequency gain fractions. */
    private static final float OCCLUDED_GAIN = 0.45f, OCCLUDED_HF = 0.08f;

    private final boolean enabled;
    private final int[] sources, filters;
    private final float[] x, y, z, age, current, target;
    private final boolean[] spatial;
    private final AudioOcclusion.Budget budget = new AudioOcclusion.Budget();
    private World world;
    private final AudioOcclusion.Voxels voxels = (a, b, c) -> world != null && world.getBlock(a, b, c).solid;
    private float listenerX, listenerY, listenerZ;
    private int cursor;

    AcousticSources(boolean enabled, int[] voices, int[] loops) {
        this.enabled = enabled;
        sources = Arrays.copyOf(voices, voices.length + loops.length);
        System.arraycopy(loops, 0, sources, voices.length, loops.length);
        filters = new int[sources.length];
        x = new float[sources.length]; y = new float[sources.length]; z = new float[sources.length];
        age = new float[sources.length]; current = new float[sources.length]; target = new float[sources.length];
        spatial = new boolean[sources.length];
        if (!enabled) return;
        for (int i = 0; i < filters.length; i++) {
            filters[i] = alGenFilters();
            alFilteri(filters[i], AL_FILTER_TYPE, AL_FILTER_LOWPASS);
            apply(i);
        }
    }

    void world(World world) { if (enabled) this.world = world; }
    void listener(float x, float y, float z) { listenerX = x; listenerY = y; listenerZ = z; }

    void position(int source, float px, float py, float pz, boolean relative) {
        if (!enabled) return;
        int i = index(source);
        if (i < 0) return;
        x[i] = px; y[i] = py; z[i] = pz; spatial[i] = !relative;
        target[i] = current[i] = 0;
        if (!relative && world != null && budget.available()) target[i] = current[i] = sample(i);
        age[i] = RESAMPLE_SECONDS;
        alSourcef(source, AL_AIR_ABSORPTION_FACTOR, relative ? 0 : AIR_ABSORPTION);
        apply(i);
    }

    void update(float dt) {
        if (!enabled) return;
        float blend = (float) -Math.expm1(-Math.max(0, dt) * FILTER_BLEND_SPEED);
        for (int i = 0; i < sources.length; i++) age[i] -= dt;
        // Examination itself is bounded, even when every voice is idle.
        for (int examined = 0; examined < AudioOcclusion.RAYS_PER_FRAME; examined++) {
            int i = cursor;
            cursor = (cursor + 1) % sources.length;
            if (world == null || !spatial[i] || age[i] > 0 || !budget.available()
                    || alGetSourcei(sources[i], AL_SOURCE_STATE) != AL_PLAYING) continue;
            target[i] = sample(i);
            age[i] = RESAMPLE_SECONDS;
        }
        for (int i = 0; i < sources.length; i++) {
            if (Math.abs(current[i] - target[i]) < 0.0001f) continue;
            current[i] += (target[i] - current[i]) * blend;
            apply(i);
        }
        budget.nextFrame();
    }

    private float sample(int i) {
        return AudioOcclusion.trace(voxels, listenerX, listenerY, listenerZ, x[i], y[i], z[i], budget);
    }

    private void apply(int i) {
        alFilterf(filters[i], AL_LOWPASS_GAIN, 1 - (1 - OCCLUDED_GAIN) * current[i]);
        alFilterf(filters[i], AL_LOWPASS_GAINHF, 1 - (1 - OCCLUDED_HF) * current[i]);
        alSourcei(sources[i], AL_DIRECT_FILTER, filters[i]);
    }

    private int index(int source) {
        for (int i = 0; i < sources.length; i++) if (sources[i] == source) return i;
        return -1;
    }

    void reset() {
        world = null;
        Arrays.fill(spatial, false);
        Arrays.fill(current, 0);
        Arrays.fill(target, 0);
        if (enabled) for (int i = 0; i < sources.length; i++) apply(i);
    }

    void close() {
        if (!enabled) return;
        budget.nextFrame();
        System.out.printf("[audio] occlusion peakRays=%d/%d peakVoxelQueries=%d/%d%n",
                budget.peakRays, AudioOcclusion.RAYS_PER_FRAME, budget.peakQueries,
                AudioOcclusion.RAYS_PER_FRAME * AudioOcclusion.MAX_STEPS);
        for (int filter : filters) if (filter != 0) alDeleteFilters(filter);
    }
}
