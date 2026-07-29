package com.veylon.simulation;

/**
 * Environmental temperature model tuning: heat source ranges, altitude
 * cooling, underground stabilization and the fire comfort ceiling.
 *
 * <p>Extracted verbatim from {@link TemperatureSystem}; changing one is a
 * gameplay change, not a refactor.
 */
public final class TemperatureConstants {

    private TemperatureConstants() {
    }

    /** Chunks scanned in each direction around the player for heat sources. */
    public static final int HEAT_SOURCE_CHUNK_RADIUS = 2;
    /** Heat in degrees C attributed to a burning block. */
    public static final float BURNING_CELL_HEAT = 30f;
    /** Heat at or above which a source uses the long range. */
    public static final float LARGE_HEAT_THRESHOLD = 30f;
    /** Reach of a bonfire-scale heat source, in blocks. */
    public static final float LARGE_HEAT_RANGE = 6f;
    /** Reach of a torch or campfire, in blocks. */
    public static final float SMALL_HEAT_RANGE = 3f;

    /** Height above which thinning air starts cooling the player. */
    public static final int ALTITUDE_COOLING_START_Y = 40;
    /** Degrees lost per block above {@link #ALTITUDE_COOLING_START_Y}. */
    public static final float ALTITUDE_COOLING_PER_BLOCK = 0.28f;

    /** Deep rock sits at this temperature year round, in degrees C. */
    public static final float UNDERGROUND_STABLE_TEMP = 9f;
    /** Blocks below the surface before stabilization begins. */
    public static final int UNDERGROUND_DEPTH_START = 2;
    /** Further blocks over which the blend to stable temperature completes. */
    public static final float UNDERGROUND_BLEND_DEPTH = 8f;

    /**
     * Fire warms the player toward a comfortable value but never past it;
     * standing in flames is punished by burn damage, not by air temperature.
     */
    public static final float FIRE_COMFORT_CAP = 26f;
}
