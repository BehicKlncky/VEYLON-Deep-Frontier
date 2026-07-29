package com.veylon.simulation;

/**
 * Soil moisture, regrowth chances and tree seeding tuning.
 *
 * <p>Growth chances are per sampling attempt, and each chunk gets
 * {@link #ATTEMPTS_PER_CHUNK} attempts per slow tick, so the effective rate is
 * the chance multiplied by that count and by the combined growth factor
 * (moisture x cold x event x season).
 *
 * <p>Extracted verbatim from {@link PlantSystem}; changing one is a gameplay
 * change, not a refactor.
 */
public final class PlantConstants {

    private PlantConstants() {
    }

    /** Chunks simulated in each direction around the player. */
    public static final int ACTIVE_RADIUS = 4;
    /** Random surface samples taken per chunk per slow tick. */
    public static final int ATTEMPTS_PER_CHUNK = 18;

    // Soil moisture.
    public static final float DEFAULT_MOISTURE = 0.5f;
    /** Moisture gained per second at full precipitation intensity. */
    public static final float RAIN_MOISTURE_PER_SECOND = 0.010f;
    /** Moisture lost per second during a drought. */
    public static final float DROUGHT_MOISTURE_PER_SECOND = 0.008f;
    /** Rate at which moisture drifts back toward the biome baseline. */
    public static final float MOISTURE_DRIFT_RATE = 0.002f;
    /** Soil never dries below this. */
    public static final float MIN_MOISTURE = 0.02f;
    /** Marshes never dry below this. */
    public static final float MARSH_MIN_MOISTURE = 0.5f;

    // Growth modifiers.
    /** Environment temperature below which growth is suppressed. */
    public static final float COLD_TEMP = 0f;
    /** Growth multiplier when freezing. */
    public static final float COLD_GROWTH_MULT = 0.3f;

    // Per-attempt chances, before the growth factor is applied.
    public static final float BERRY_REGROW_CHANCE = 0.10f;
    public static final float SAPLING_GROW_CHANCE = 0.15f;
    public static final float GRASS_SPREAD_CHANCE = 0.25f;
    public static final float TALL_GRASS_CHANCE = 0.05f;
    /** Upper edge of the herb band; the gap above TALL_GRASS_CHANCE is herbs. */
    public static final float HERB_CHANCE = 0.06f;
    /** Both plant chances are scaled by biome plant density times this. */
    public static final float PLANT_DENSITY_SCALE = 12;
    /** Saplings seed from the top of the random range, above this threshold. */
    public static final float SAPLING_SEED_THRESHOLD = 0.995f;
    /** Growth widens the sapling seeding band by this much. */
    public static final float SAPLING_SEED_GROWTH_BAND = 0.004f;

    // Tree proximity scan for sapling seeding.
    public static final int LEAF_SCAN_RADIUS = 4;
    public static final int LEAF_SCAN_STEP = 2;
    public static final int LEAF_SCAN_MIN_HEIGHT = 2;
    public static final int LEAF_SCAN_MAX_HEIGHT = 7;
}
