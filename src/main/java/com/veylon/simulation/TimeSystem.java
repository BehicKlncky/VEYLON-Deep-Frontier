package com.veylon.simulation;

/**
 * In-game clock. One full day lasts 15 real minutes
 * (1 real second = 1.6 game minutes).
 */
public class TimeSystem {

    public static final double MINUTES_PER_REAL_SECOND = 1.6;

    /** Total game minutes elapsed since world start (starts at day 1, 08:00). */
    public double totalMinutes = 8 * 60;

    public void advance(double realSeconds) {
        totalMinutes += realSeconds * MINUTES_PER_REAL_SECOND;
    }

    public int day() {
        return (int) (totalMinutes / 1440) + 1;
    }

    public int hour() {
        return (int) ((totalMinutes % 1440) / 60);
    }

    public int minute() {
        return (int) (totalMinutes % 60);
    }

    public double hourF() {
        return (totalMinutes % 1440) / 60.0;
    }

    /** Sun light factor 0.10 (night) .. 1.0 (midday). */
    public double dayLight() {
        double h = hourF();
        if (h < 5 || h >= 21) {
            return 0.10;
        }
        if (h < 8) {
            return 0.10 + (h - 5) / 3.0 * 0.90;
        }
        if (h < 17) {
            return 1.0;
        }
        return 1.0 - (h - 17) / 4.0 * 0.90;
    }

    public boolean isNight() {
        double h = hourF();
        return h >= 21 || h < 5;
    }

    public String phase() {
        double h = hourF();
        if (h >= 5 && h < 8) {
            return "Dawn";
        }
        if (h >= 8 && h < 17) {
            return "Day";
        }
        if (h >= 17 && h < 21) {
            return "Dusk";
        }
        return "Night";
    }

    /** Diurnal temperature offset in degrees C: coldest ~04:00, warmest ~14:00. */
    public float tempOffset() {
        double h = hourF();
        return (float) (-7.0 * Math.cos((h - 14.0) / 24.0 * 2 * Math.PI) - 1.0);
    }

    public String timeString() {
        return String.format("Day %d  %02d:%02d (%s)", day(), hour(), minute(), phase());
    }
}
