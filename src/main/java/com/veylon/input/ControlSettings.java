package com.veylon.input;

import com.veylon.util.AppPaths;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * How the mouse turns the view (0.9.1 milestone 06, decision D-06), persisted beside the graphics,
 * audio and autosave settings as {@code veylon_controls.properties}. The sensitivity is a percentage
 * of the view's turn before 0.9.1, so its default, 100%, turns it exactly as that fixed value did.
 * A missing or malformed value falls back to its default alone.
 */
public final class ControlSettings {

    /** Degrees the view turns for one pixel of mouse movement at 100%: the value fixed before 0.9.1. */
    public static final float BASE_DEGREES_PER_PIXEL = 0.115f;
    /** The sensitivities the options screen offers, in percent, from the least to the most. */
    public static final int MIN_SENSITIVITY = 10, MAX_SENSITIVITY = 300, SENSITIVITY_STEP = 10;
    public static final int DEFAULT_SENSITIVITY = 100;
    private static final Path FILE = AppPaths.dataDirectory().resolve("veylon_controls.properties");

    private int sensitivity = DEFAULT_SENSITIVITY;
    /** Whether moving the mouse forward looks down rather than up. */
    public boolean invertY;

    /** The defaults. */
    public ControlSettings() {
    }

    /** A copy, for an options screen's transaction. */
    public ControlSettings(ControlSettings source) {
        copyFrom(source);
    }

    public void copyFrom(ControlSettings source) {
        sensitivity = source.sensitivity;
        invertY = source.invertY;
    }

    /** The mouse sensitivity in percent: one of the offered steps from 10 to 300. */
    public int sensitivity() {
        return sensitivity;
    }

    /** Sets an offered sensitivity. */
    public void setSensitivity(int percent) {
        if (!offered(percent)) {
            throw new IllegalArgumentException("mouse sensitivity " + percent
                    + "% is not a step of " + SENSITIVITY_STEP + " from " + MIN_SENSITIVITY + " to " + MAX_SENSITIVITY);
        }
        sensitivity = percent;
    }

    /** The offered sensitivity {@code steps} away from the current one, held at the ends. */
    public void stepSensitivity(int steps) {
        sensitivity = Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, sensitivity + steps * SENSITIVITY_STEP));
    }

    /** Degrees the view turns for one pixel of mouse movement. */
    public float degreesPerPixel() {
        return BASE_DEGREES_PER_PIXEL * (sensitivity / 100f);
    }

    private static boolean offered(int percent) {
        return percent >= MIN_SENSITIVITY && percent <= MAX_SENSITIVITY
                && (percent - MIN_SENSITIVITY) % SENSITIVITY_STEP == 0;
    }

    /** Where the player's settings live: the data directory. */
    public static Path file() {
        return FILE;
    }

    /** The player's settings from the data directory, or the defaults. */
    public static ControlSettings loadOrDefaults() {
        return load(FILE);
    }

    static ControlSettings load(Path path) {
        ControlSettings settings = new ControlSettings();
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
        try {
            int percent = Integer.parseInt(values.getProperty("mouseSensitivity", "").trim());
            if (offered(percent)) {
                settings.sensitivity = percent;
            }
        } catch (NumberFormatException ignored) {
            // Keep the default sensitivity.
        }
        String invert = values.getProperty("invertY", "").trim();
        if (invert.equalsIgnoreCase("true") || invert.equalsIgnoreCase("false")) {
            settings.invertY = Boolean.parseBoolean(invert);
        }
        return settings;
    }

    /** Writes these settings to the data directory. */
    public void save() {
        save(FILE);
    }

    void save(Path path) {
        Properties values = new Properties();
        values.setProperty("mouseSensitivity", Integer.toString(sensitivity));
        values.setProperty("invertY", Boolean.toString(invertY));
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (var out = Files.newOutputStream(path)) {
                values.store(out, "VEYLON control settings");
            }
        } catch (IOException e) {
            System.err.println("[settings] failed to write " + path + ": " + e.getMessage());
        }
    }
}
