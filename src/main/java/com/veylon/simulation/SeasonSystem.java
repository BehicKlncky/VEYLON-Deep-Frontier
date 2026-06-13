package com.veylon.simulation;

/**
 * Multi-day climate cycle layered on top of daily weather. Seasons rotate
 * every {@link #SEASON_DAYS} in-game days and bias temperature, precipitation,
 * plant growth and wildlife.
 */
public class SeasonSystem {

    public static final int SEASON_DAYS = 6;

    public enum Season {
        TEMPERATE("Mild Season", 0f, 0f, 1f),
        WET("Wet Season", -2f, 0.28f, 1.25f),
        COLD("Frost Season", -10f, 0.05f, 0.45f),
        DRY("Dry Season", 7f, -0.30f, 0.6f);

        public final String displayName;
        /** Degrees C added to ambient temperature. */
        public final float tempOffset;
        /** Added to the chance of rain/storm/snow when weather rolls. */
        public final float rainBias;
        /** Plant growth multiplier. */
        public final float growthMul;

        Season(String displayName, float tempOffset, float rainBias, float growthMul) {
            this.displayName = displayName;
            this.tempOffset = tempOffset;
            this.rainBias = rainBias;
            this.growthMul = growthMul;
        }
    }

    public Season current(TimeSystem time) {
        int idx = ((time.day() - 1) / SEASON_DAYS) % Season.values().length;
        return Season.values()[idx];
    }

    /** Days until the season changes. */
    public int daysLeft(TimeSystem time) {
        return SEASON_DAYS - ((time.day() - 1) % SEASON_DAYS);
    }

    /** Wildlife activity multiplier (fewer animals in frost, fewer in scorch). */
    public float spawnMul(TimeSystem time) {
        return switch (current(time)) {
            case COLD -> 0.55f;
            case DRY -> 0.75f;
            default -> 1f;
        };
    }
}
