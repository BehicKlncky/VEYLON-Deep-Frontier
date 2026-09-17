package com.veylon.engine;

import com.veylon.world.Chunk;
import com.veylon.world.World;
import java.util.Random;

/** Bounded weather emission in loaded world columns, independent of player shelter exposure. */
public final class RainField {
    private static final float RADIUS = 22f;
    private static final float DROPS_PER_SECOND = 640f;
    private static final float MAX_EMISSION_STEP = 0.2f;
    private static final float MAX_CEILING_ABOVE_PLAYER = 24f;
    private static final float SPAWN_ABOVE_PLAYER = 8f;
    private static final float SPAWN_HEIGHT_RANGE = 10f;
    private float pending;
    private double time;

    public void reset() { pending = 0; time = 0; }

    public void update(float dt, World world, ParticleSystem particles, Random rng,
                       float x, float y, float z, float intensity) {
        if (!(dt > 0) || !Float.isFinite(dt)) return;
        // No catch-up burst after a paused frame. Wind is smooth and never consumes simulation RNG.
        float step = Math.min(dt, MAX_EMISSION_STEP);
        time += step;
        float strength = 0.5f + 4f * intensity * intensity;
        particles.setRainWind(strength * (0.75f + 0.25f * (float) Math.sin(time * 0.63)),
                strength * (0.3f + 0.20f * (float) Math.sin(time * 0.41 + 1.7)));
        particles.cullRain(x, y, z, RADIUS + 10f);
        pending += Math.max(0, Math.min(1, intensity)) * DROPS_PER_SECOND * step;
        int attempts = (int) pending;
        pending -= attempts;
        for (int i = 0; i < attempts; i++) {
            float angle = rng.nextFloat() * (float) (Math.PI * 2);
            float radius = RADIUS * (float) Math.sqrt(rng.nextFloat());
            float sx = x + (float) Math.cos(angle) * radius;
            float sz = z + (float) Math.sin(angle) * radius;
            int bx = (int) Math.floor(sx), bz = (int) Math.floor(sz);
            Chunk chunk = world.getChunk(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16));
            if (chunk == null) continue;
            int surface = chunk.height(Math.floorMod(bx, 16), Math.floorMod(bz, 16));
            // Reject distant cave ceilings column by column; entrance/outdoor columns still emit.
            if (surface > y + MAX_CEILING_ABOVE_PLAYER) continue;
            // Heightmap is only a sky-entry bound. Every contact uses the actual swept voxel path.
            float sy = Math.max(y + SPAWN_ABOVE_PLAYER, surface + 1.5f) + rng.nextFloat() * SPAWN_HEIGHT_RANGE;
            particles.rainDrop(sx, sy, sz);
        }
    }
}
