package com.veylon.save;

import com.veylon.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Whether the game saves by itself and how often (0.9.1 milestone 05, decision D-03), persisted
 * beside the graphics and audio settings as {@code veylon_autosave.properties}. A missing or
 * malformed value falls back to its default alone.
 */
public final class AutosaveSettings {

    /** The intervals the options screen offers, in real minutes of play. */
    private static final int[] INTERVALS = {5, 10, 15, 30};
    /** D-03's interval. */
    public static final int DEFAULT_INTERVAL_MINUTES = 10;
    private static final Path FILE = AppPaths.dataDirectory().resolve("veylon_autosave.properties");

    /** On by default (D-03). */
    public boolean enabled = true;
    private int intervalMinutes = DEFAULT_INTERVAL_MINUTES;

    /** The defaults. */
    public AutosaveSettings() {
    }

    /** A copy, for an options screen's transaction. */
    public AutosaveSettings(AutosaveSettings source) {
        copyFrom(source);
    }

    public void copyFrom(AutosaveSettings source) {
        enabled = source.enabled;
        intervalMinutes = source.intervalMinutes;
    }

    /** Real minutes of play between two timed autosaves: 5, 10, 15 or 30. */
    public int intervalMinutes() {
        return intervalMinutes;
    }

    /** Sets an offered interval. */
    public void setIntervalMinutes(int minutes) {
        if (!offered(minutes)) {
            throw new IllegalArgumentException("autosave interval " + minutes + " is not one of 5, 10, 15 or 30 minutes");
        }
        intervalMinutes = minutes;
    }

    /** The offered interval {@code steps} away from the current one, wrapping around. */
    public void stepInterval(int steps) {
        int at = 0;
        while (INTERVALS[at] != intervalMinutes) {
            at++;
        }
        intervalMinutes = INTERVALS[Math.floorMod(at + steps, INTERVALS.length)];
    }

    /** Real seconds of play between two timed autosaves. */
    public double intervalSeconds() {
        return intervalMinutes * 60.0;
    }

    private static boolean offered(int minutes) {
        for (int interval : INTERVALS) {
            if (interval == minutes) {
                return true;
            }
        }
        return false;
    }

    /** Where the player's settings live: the data directory. */
    static Path file() {
        return FILE;
    }

    /** The player's settings from the data directory, or the defaults. */
    public static AutosaveSettings loadOrDefaults() {
        return load(FILE);
    }

    static AutosaveSettings load(Path path) {
        AutosaveSettings settings = new AutosaveSettings();
        if (!Files.isRegularFile(path)) {
            return settings;
        }
        Properties values = new Properties();
        try (var in = Files.newInputStream(path)) {
            values.load(in);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[settings] failed to read " + path + ": " + e.getMessage());
            return settings;
        }
        String enabled = values.getProperty("enabled", "").trim();
        if (enabled.equalsIgnoreCase("true") || enabled.equalsIgnoreCase("false")) {
            settings.enabled = Boolean.parseBoolean(enabled);
        }
        try {
            int minutes = Integer.parseInt(values.getProperty("intervalMinutes", "").trim());
            if (offered(minutes)) {
                settings.intervalMinutes = minutes;
            }
        } catch (NumberFormatException ignored) {
            // Keep the default interval.
        }
        return settings;
    }

    /** Writes these settings to the data directory. */
    public void save() {
        save(FILE);
    }

    void save(Path path) {
        Properties values = new Properties();
        values.setProperty("enabled", Boolean.toString(enabled));
        values.setProperty("intervalMinutes", Integer.toString(intervalMinutes));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (var out = Files.newOutputStream(path)) {
                values.store(out, "VEYLON autosave settings");
            }
        } catch (IOException e) {
            System.err.println("[settings] failed to write " + path + ": " + e.getMessage());
        }
    }
}
