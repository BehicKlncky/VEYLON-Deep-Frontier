package com.veylon.simulation;

/**
 * Clock rate, day phase boundaries and the diurnal temperature curve.
 *
 * <p>Extracted verbatim from {@link TimeSystem}; changing one is a gameplay
 * change, not a refactor.
 */
public final class TimeConstants {

    private TimeConstants() {
    }

    /** One full day lasts 15 real minutes at this rate. */
    public static final double MINUTES_PER_REAL_SECOND = 1.6;
    public static final int MINUTES_PER_HOUR = 60;
    public static final int MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR;
    public static final double HOURS_PER_DAY = 24.0;
    /** Worlds start on day 1 at 08:00. */
    public static final int START_HOUR = 8;

    // Day phase boundaries, in hours.
    public static final double DAWN_START_HOUR = 5;
    public static final double DAY_START_HOUR = 8;
    public static final double DUSK_START_HOUR = 17;
    public static final double NIGHT_START_HOUR = 21;
    public static final double DAWN_DURATION_HOURS = DAY_START_HOUR - DAWN_START_HOUR;
    public static final double DUSK_DURATION_HOURS = NIGHT_START_HOUR - DUSK_START_HOUR;

    // Sun light factor.
    /** Ambient light floor at night; the world is never fully black. */
    public static final double NIGHT_LIGHT = 0.10;
    public static final double FULL_LIGHT = 1.0;
    /** Range the light factor travels between night and midday. */
    public static final double LIGHT_RANGE = FULL_LIGHT - NIGHT_LIGHT;

    // Diurnal temperature: a cosine peaking at WARMEST_HOUR.
    /** Half the peak-to-trough swing, in degrees C. */
    public static final double DIURNAL_TEMP_AMPLITUDE = 7.0;
    public static final double WARMEST_HOUR = 14.0;
    /** Constant offset so the daily mean sits slightly below the biome base. */
    public static final double DIURNAL_TEMP_BIAS = 1.0;
}
