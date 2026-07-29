package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.util.MathUtil;
import com.veylon.world.BlockType;
import com.veylon.world.Biome;

import java.util.Random;

import static com.veylon.simulation.WeatherConstants.*;

/** Dynamic weather with gradual transitions, biome tendencies and storm lightning. */
public class WeatherSystem {

    /**
     * A weather state and everything that distinguishes it.
     *
     * <p>These used to be six parallel {@code switch} statements over this
     * enum, so adding a weather state meant finding all six. Declaring the
     * values here makes each row the complete definition of one state, and the
     * compiler will not let a new constant omit any of them.
     *
     * <p>Persisted by ordinal — append only, never reorder.
     *
     * @param displayName  shown in the HUD and the simulation panel
     * @param intensity    precipitation strength, 0..1
     * @param lightMul     daylight multiplier, 0..1
     * @param grayness     how desaturated the sky looks, 0..1
     * @param fogStart     distance in blocks at which fog begins
     * @param fogEnd       distance in blocks at which fog is opaque
     * @param tempOffset   temperature offset in degrees C
     */
    public enum Weather {
        CLEAR("Clear", 0f, 1f, 0f, 65f, 115f, 1f),
        CLOUDY("Cloudy", 0f, 0.85f, 0.45f, 55f, 100f, -1f),
        RAIN("Rain", 0.6f, 0.7f, 0.6f, 38f, 80f, -4f),
        STORM("Storm", 1f, 0.55f, 0.75f, 28f, 60f, -6f),
        FOG("Fog", 0f, 0.75f, 0.7f, 10f, 34f, -2f),
        SNOW("Snow", 0.5f, 0.8f, 0.5f, 35f, 72f, -7f);

        public final String displayName;
        public final float intensity;
        public final float lightMul;
        public final float grayness;
        public final float fogStart;
        public final float fogEnd;
        public final float tempOffset;

        Weather(String displayName, float intensity, float lightMul, float grayness,
                float fogStart, float fogEnd, float tempOffset) {
            this.displayName = displayName;
            this.intensity = intensity;
            this.lightMul = lightMul;
            this.grayness = grayness;
            this.fogStart = fogStart;
            this.fogEnd = fogEnd;
            this.tempOffset = tempOffset;
        }
    }

    private final Random rng = new Random();

    public Weather current = Weather.CLEAR;
    public Weather next = Weather.CLEAR;
    /** 0..1 progress of the transition from current to next. */
    public float blend = 1f;
    /** Seconds until a new target weather is rolled. */
    public float changeTimer = INITIAL_CHANGE_TIMER;
    private float flashTimer = 0;
    public int lightningStrikes = 0;

    public void mediumTick(Game g, float dt) {
        flashTimer = Math.max(0, flashTimer - dt);

        if (blend < 1f) {
            blend = Math.min(1f, blend + dt / TRANSITION_SECONDS);
            if (blend >= 1f) {
                current = next;
                announce(g);
            }
        }

        changeTimer -= dt;
        if (changeTimer <= 0) {
            changeTimer = CHANGE_TIMER_MIN + rng.nextFloat() * CHANGE_TIMER_RANGE;
            Weather target = pickNext(g);
            if (target != current) {
                next = target;
                blend = 0f;
            }
        }

        // Lightning during storms.
        if (effective() == Weather.STORM && rng.nextFloat() < LIGHTNING_CHANCE_PER_TICK) {
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
        r += g.seasons.current(g.time).rainBias;
        // Wetter biomes rain more; scrubland almost never.
        if (r < CLEAR_THRESHOLD + wet * CLEAR_MOISTURE_BIAS) {
            return Weather.CLEAR;
        }
        if (r < CLOUDY_THRESHOLD) {
            return Weather.CLOUDY;
        }
        if (r < FOG_THRESHOLD + wet * FOG_MOISTURE_BIAS) {
            return Weather.FOG;
        }
        if (r < PRECIPITATION_THRESHOLD) {
            return envTemp < SNOW_TEMP ? Weather.SNOW : Weather.RAIN;
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
        flashTimer = FLASH_SECONDS;
        lightningStrikes++;
        g.audio.playThunder();
        int x = (int) (g.player.pos.x + rng.nextInt(LIGHTNING_SPAN) - LIGHTNING_RADIUS);
        int z = (int) (g.player.pos.z + rng.nextInt(LIGHTNING_SPAN) - LIGHTNING_RADIUS);
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
            if (c.distSqTo(x, y, z) < LIGHTNING_PANIC_RADIUS * LIGHTNING_PANIC_RADIUS) {
                c.fear = 1f;
            }
        }
    }

    /** The weather players currently experience (snaps halfway through a transition). */
    public Weather effective() {
        return blend < TRANSITION_SNAP ? current : next;
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
        return MathUtil.lerp(current.intensity, next.intensity, blend);
    }

    public float lightMul() {
        return MathUtil.lerp(current.lightMul, next.lightMul, blend);
    }

    /** How desaturated/gray the sky looks. */
    public float grayness() {
        return MathUtil.lerp(current.grayness, next.grayness, blend);
    }

    public float fogStart() {
        return MathUtil.lerp(current.fogStart, next.fogStart, blend);
    }

    public float fogEnd() {
        return MathUtil.lerp(current.fogEnd, next.fogEnd, blend);
    }

    /** Temperature offset from weather in degrees C. */
    public float tempOffset() {
        return MathUtil.lerp(current.tempOffset, next.tempOffset, blend);
    }

    /** Lightning screen flash 0..1. */
    public float flashLight() {
        return flashTimer > 0 ? Math.min(1f, flashTimer / FLASH_SECONDS) : 0f;
    }
}
