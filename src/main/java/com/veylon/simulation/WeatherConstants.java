package com.veylon.simulation;

/**
 * Weather scheduling and lightning tuning.
 *
 * <p>The per-weather appearance and temperature values are <em>not</em> here:
 * they live as fields on {@link WeatherSystem.Weather} so that one row of the
 * enum declares everything about a weather state. This class holds only the
 * values that govern transitions between states.
 *
 * <p>Extracted verbatim from {@link WeatherSystem}; changing one is a gameplay
 * change, not a refactor.
 */
public final class WeatherConstants {

    private WeatherConstants() {
    }

    /** Seconds a transition from one weather state to the next takes. */
    public static final float TRANSITION_SECONDS = 25f;
    /**
     * Blend progress at which the player is considered to be experiencing the
     * incoming weather rather than the outgoing one.
     */
    public static final float TRANSITION_SNAP = 0.5f;

    /**
     * Seconds before the first weather roll, for a {@link WeatherSystem} that
     * has never been given a world. Every real world goes through
     * {@link WeatherSystem#reset()} and therefore uses
     * {@link #NEW_WORLD_CHANGE_TIMER} instead.
     */
    public static final float INITIAL_CHANGE_TIMER = 120f;
    /** Seconds of guaranteed opening weather when a world starts or loads. */
    public static final float NEW_WORLD_CHANGE_TIMER = 100f;
    /** Minimum seconds between weather rolls. */
    public static final float CHANGE_TIMER_MIN = 90f;
    /** Additional random seconds on top of the minimum. */
    public static final float CHANGE_TIMER_RANGE = 150f;

    // Weather roll thresholds, applied to one random 0..1 shifted by the
    // season's rain bias. Each band runs from the previous threshold up to its
    // own, so they must stay in ascending order.
    /** Below this, clear. Wetter biomes widen the band that follows it. */
    public static final float CLEAR_THRESHOLD = 0.34f;
    /** Extra clear chance per point of biome moisture. */
    public static final float CLEAR_MOISTURE_BIAS = 0.08f;
    public static final float CLOUDY_THRESHOLD = 0.58f;
    public static final float FOG_THRESHOLD = 0.62f;
    /** Extra fog chance per point of biome moisture. */
    public static final float FOG_MOISTURE_BIAS = 0.05f;
    /** Above this, storm; below it (and above fog), rain or snow. */
    public static final float PRECIPITATION_THRESHOLD = 0.86f;
    /** Environment temperature below which precipitation falls as snow. */
    public static final float SNOW_TEMP = 0f;

    /** Chance per medium tick of a lightning strike during a storm. */
    public static final float LIGHTNING_CHANCE_PER_TICK = 0.05f;
    /** Duration of the lightning screen flash, in seconds. */
    public static final float FLASH_SECONDS = 0.35f;
    /** Blocks either side of the player a strike can land. */
    public static final int LIGHTNING_RADIUS = 40;
    /** Width of the sampled square, i.e. 2 * radius + 1. */
    public static final int LIGHTNING_SPAN = 2 * LIGHTNING_RADIUS + 1;
    /** Blocks from a strike within which creatures panic. */
    public static final float LIGHTNING_PANIC_RADIUS = 30f;
}
