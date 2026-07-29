package com.veylon.simulation;

import static com.veylon.simulation.TimeConstants.*;

/**
 * In-game clock. One full day lasts 15 real minutes
 * (1 real second = 1.6 game minutes).
 */
public class TimeSystem {

    /** Total game minutes elapsed since world start (starts at day 1, 08:00). */
    public double totalMinutes = START_HOUR * MINUTES_PER_HOUR;

    public void advance(double realSeconds) {
        totalMinutes += realSeconds * MINUTES_PER_REAL_SECOND;
    }

    public int day() {
        return (int) (totalMinutes / MINUTES_PER_DAY) + 1;
    }

    public int hour() {
        return (int) ((totalMinutes % MINUTES_PER_DAY) / MINUTES_PER_HOUR);
    }

    public int minute() {
        return (int) (totalMinutes % MINUTES_PER_HOUR);
    }

    /** Hour of day as a fraction, e.g. 13.5 for 13:30. */
    public double hourF() {
        return (totalMinutes % MINUTES_PER_DAY) / (double) MINUTES_PER_HOUR;
    }

    /** Sun light factor {@value TimeConstants#NIGHT_LIGHT} (night) .. 1.0 (midday). */
    public double dayLight() {
        double h = hourF();
        if (h < DAWN_START_HOUR || h >= NIGHT_START_HOUR) {
            return NIGHT_LIGHT;
        }
        if (h < DAY_START_HOUR) {
            // Dawn: ramp up.
            return NIGHT_LIGHT + (h - DAWN_START_HOUR) / DAWN_DURATION_HOURS * LIGHT_RANGE;
        }
        if (h < DUSK_START_HOUR) {
            return FULL_LIGHT;
        }
        // Dusk: ramp down.
        return FULL_LIGHT - (h - DUSK_START_HOUR) / DUSK_DURATION_HOURS * LIGHT_RANGE;
    }

    public boolean isNight() {
        double h = hourF();
        return h >= NIGHT_START_HOUR || h < DAWN_START_HOUR;
    }

    public String phase() {
        double h = hourF();
        if (h >= DAWN_START_HOUR && h < DAY_START_HOUR) {
            return "Dawn";
        }
        if (h >= DAY_START_HOUR && h < DUSK_START_HOUR) {
            return "Day";
        }
        if (h >= DUSK_START_HOUR && h < NIGHT_START_HOUR) {
            return "Dusk";
        }
        return "Night";
    }

    /** Diurnal temperature offset in degrees C: coldest ~04:00, warmest ~14:00. */
    public float tempOffset() {
        double h = hourF();
        return (float) (-DIURNAL_TEMP_AMPLITUDE
                * Math.cos((h - WARMEST_HOUR) / HOURS_PER_DAY * 2 * Math.PI)
                - DIURNAL_TEMP_BIAS);
    }

    public String timeString() {
        return String.format("Day %d  %02d:%02d (%s)", day(), hour(), minute(), phase());
    }
}
