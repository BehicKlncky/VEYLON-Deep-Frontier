package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.util.MathUtil;
import com.veylon.world.BlockType;
import com.veylon.world.Biome;

import java.util.Random;

/** Dynamic weather with gradual transitions, biome tendencies and storm lightning. */
public class WeatherSystem {

    public enum Weather {
        CLEAR("Clear"), CLOUDY("Cloudy"), RAIN("Rain"), STORM("Storm"), FOG("Fog"), SNOW("Snow");

        public final String displayName;

        Weather(String displayName) {
            this.displayName = displayName;
        }
    }

    private final Random rng = new Random();

    public Weather current = Weather.CLEAR;
    public Weather next = Weather.CLEAR;
    /** 0..1 progress of the transition from current to next. */
    public float blend = 1f;
    /** Seconds until a new target weather is rolled. */
    public float changeTimer = 120;
    private float flashTimer = 0;
    public int lightningStrikes = 0;

    public void mediumTick(Game g, float dt) {
        flashTimer = Math.max(0, flashTimer - dt);

        if (blend < 1f) {
            blend = Math.min(1f, blend + dt / 25f);
            if (blend >= 1f) {
                current = next;
                announce(g);
            }
        }

        changeTimer -= dt;
        if (changeTimer <= 0) {
            changeTimer = 90 + rng.nextFloat() * 150;
            Weather target = pickNext(g);
            if (target != current) {
                next = target;
                blend = 0f;
            }
        }

        // Lightning during storms.
        if (effective() == Weather.STORM && rng.nextFloat() < 0.05f) {
            strikeLightning(g);
        }
    }

    private Weather pickNext(Game g) {
        Biome biome = g.player.biome;
        float envTemp = g.player.envTemp;
        if (g.events.forcesClear()) {
            return Weather.CLEAR;
        }
        float r = rng.nextFloat();
        float wet = biome.moisture;
        // Seasons shift the whole distribution: wet season rains, dry season bakes.
        float seasonRain = g.seasons.current(g.time).rainBias;
        r += seasonRain;
        // Wetter biomes rain more; scrubland almost never.
        if (r < 0.34f + wet * 0.08f) {
            return Weather.CLEAR;
        }
        if (r < 0.58f) {
            return Weather.CLOUDY;
        }
        if (r < 0.62f + wet * 0.05f) {
            return Weather.FOG;
        }
        if (r < 0.86f) {
            return envTemp < 0 ? Weather.SNOW : Weather.RAIN;
        }
        return Weather.STORM;
    }

    private void announce(Game g) {
        switch (current) {
            case RAIN -> g.log("Rain begins to fall.");
            case STORM -> {
                g.log("A storm front rolls in!");
                g.events.onStormStarted(g);
            }
            case FOG -> g.log("A thick fog settles over the land.");
            case SNOW -> g.log("Snow drifts down from a cold sky.");
            case CLEAR -> g.log("The sky clears.");
            case CLOUDY -> g.log("Clouds gather overhead.");
        }
    }

    public void strikeLightning(Game g) {
        flashTimer = 0.35f;
        lightningStrikes++;
        g.audio.playThunder();
        int x = (int) (g.player.pos.x + rng.nextInt(81) - 40);
        int z = (int) (g.player.pos.z + rng.nextInt(81) - 40);
        if (g.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
            return;
        }
        int y = g.world.surfaceHeight(x, z);
        BlockType t = g.world.getBlock(x, y, z);
        if (t == BlockType.LEAVES || t == BlockType.LOG) {
            g.fire.ignite(g, x, y, z);
            g.log("Lightning struck a tree - fire!");
            g.events.onLightningFire(g);
        }
        // Panic nearby wildlife.
        for (var c : g.entities.creatures) {
            if (c.distSqTo(x, y, z) < 30 * 30) {
                c.fear = 1f;
            }
        }
    }

    /** The weather players currently experience (snaps halfway through a transition). */
    public Weather effective() {
        return blend < 0.5f ? current : next;
    }

    public boolean isPrecip() {
        Weather w = effective();
        return w == Weather.RAIN || w == Weather.STORM || w == Weather.SNOW;
    }

    public boolean isStormy() {
        return effective() == Weather.STORM;
    }

    /** Precipitation strength 0..1 considering the transition blend. */
    public float intensity() {
        float a = rawIntensity(current);
        float b = rawIntensity(next);
        return MathUtil.lerp(a, b, blend);
    }

    private float rawIntensity(Weather w) {
        return switch (w) {
            case RAIN -> 0.6f;
            case STORM -> 1f;
            case SNOW -> 0.5f;
            default -> 0f;
        };
    }

    public float lightMul() {
        return MathUtil.lerp(rawLight(current), rawLight(next), blend);
    }

    private float rawLight(Weather w) {
        return switch (w) {
            case CLEAR -> 1f;
            case CLOUDY -> 0.85f;
            case FOG -> 0.75f;
            case RAIN -> 0.7f;
            case SNOW -> 0.8f;
            case STORM -> 0.55f;
        };
    }

    /** How desaturated/gray the sky looks. */
    public float grayness() {
        return MathUtil.lerp(rawGray(current), rawGray(next), blend);
    }

    private float rawGray(Weather w) {
        return switch (w) {
            case CLEAR -> 0f;
            case CLOUDY -> 0.45f;
            case FOG -> 0.7f;
            case RAIN -> 0.6f;
            case SNOW -> 0.5f;
            case STORM -> 0.75f;
        };
    }

    public float fogStart() {
        return MathUtil.lerp(rawFogStart(current), rawFogStart(next), blend);
    }

    public float fogEnd() {
        return MathUtil.lerp(rawFogEnd(current), rawFogEnd(next), blend);
    }

    private float rawFogStart(Weather w) {
        return switch (w) {
            case CLEAR -> 65f;
            case CLOUDY -> 55f;
            case RAIN -> 38f;
            case SNOW -> 35f;
            case STORM -> 28f;
            case FOG -> 10f;
        };
    }

    private float rawFogEnd(Weather w) {
        return switch (w) {
            case CLEAR -> 115f;
            case CLOUDY -> 100f;
            case RAIN -> 80f;
            case SNOW -> 72f;
            case STORM -> 60f;
            case FOG -> 34f;
        };
    }

    /** Temperature offset from weather in degrees C. */
    public float tempOffset() {
        return MathUtil.lerp(rawTemp(current), rawTemp(next), blend);
    }

    private float rawTemp(Weather w) {
        return switch (w) {
            case CLEAR -> 1f;
            case CLOUDY -> -1f;
            case FOG -> -2f;
            case RAIN -> -4f;
            case SNOW -> -7f;
            case STORM -> -6f;
        };
    }

    /** Lightning screen flash 0..1. */
    public float flashLight() {
        return flashTimer > 0 ? Math.min(1f, flashTimer / 0.35f) : 0f;
    }
}
