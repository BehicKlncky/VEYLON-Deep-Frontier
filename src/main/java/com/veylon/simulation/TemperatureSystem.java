package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

import java.util.ArrayList;
import java.util.List;

/**
 * Environmental temperature model: biome + time of day + weather + altitude +
 * underground stabilization + nearby fire heat.
 */
public class TemperatureSystem {

    /** Cached heat sources near the player (campfires, torches, burning blocks). */
    private final List<float[]> heatSources = new ArrayList<>();

    /** Refreshed on the medium tick; scans loaded light sources near the player. */
    public void mediumTick(Game g, float dt) {
        heatSources.clear();
        int pcx = Math.floorDiv((int) g.player.pos.x, 16);
        int pcz = Math.floorDiv((int) g.player.pos.z, 16);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Chunk c = g.world.getChunk(pcx + dx, pcz + dz);
                if (c == null) {
                    continue;
                }
                for (int[] l : c.lights) {
                    BlockType t = g.world.getBlock(l[0], l[1], l[2]);
                    if (t.heat > 0) {
                        heatSources.add(new float[]{l[0] + 0.5f, l[1] + 0.5f, l[2] + 0.5f, t.heat});
                    }
                }
            }
        }
        for (Vec3i p : g.fire.burningCells()) {
            heatSources.add(new float[]{p.x() + 0.5f, p.y() + 0.5f, p.z() + 0.5f, 30f});
        }
    }

    /** Heat in degrees C contributed by nearby fires at a point. */
    public float fireHeatAt(float x, float y, float z) {
        float best = 0;
        for (float[] s : heatSources) {
            float dx = s[0] - x, dy = s[1] - y, dz = s[2] - z;
            float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
            float range = s[3] >= 30 ? 6f : 3f;
            if (dist < range) {
                float v = s[3] * (1f - dist / range);
                if (v > best) {
                    best = v;
                }
            }
        }
        return best;
    }

    public float envTempAt(Game g, float fx, float fy, float fz) {
        int x = (int) Math.floor(fx);
        int y = (int) Math.floor(fy);
        int z = (int) Math.floor(fz);
        Biome biome = g.world.biomeAt(x, z);
        float t = biome.baseTemp;
        t += g.time.tempOffset();
        t += g.weather.tempOffset();
        t += g.events.tempOffset();
        t += g.seasons.current(g.time).tempOffset;

        // Altitude cooling above the base terrain level.
        if (y > 40) {
            t -= (y - 40) * 0.28f;
        }

        // Underground: temperature stabilizes toward 9 C with depth.
        var chunk = g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (chunk != null) {
            int surface = chunk.height(Math.floorMod(x, 16), Math.floorMod(z, 16));
            int depth = surface - y;
            if (depth > 2) {
                float blend = Math.min(1f, (depth - 2) / 8f);
                t = t * (1 - blend) + 9f * blend;
            }
        }

        // Fire warms you up toward a comfortable ~26 C but never roasts the air
        // beyond that (standing inside flames is handled by burn damage instead).
        float heat = fireHeatAt(fx, fy, fz);
        if (heat > 0) {
            t = Math.max(t, Math.min(t + heat, 26f));
        }
        return t;
    }
}
