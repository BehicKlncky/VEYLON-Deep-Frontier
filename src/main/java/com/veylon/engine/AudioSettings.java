package com.veylon.engine;

import com.veylon.util.AppPaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

/** Live user mix, persisted beside graphics settings as veylon_audio.properties. */
public final class AudioSettings {
    /** Independent mix controls; these ordinals are never persisted in world saves. */
    public enum Bus { MASTER, SFX, AMBIENCE, MUSIC }
    /** Overall linear gain, defaulting to the previous hardcoded master level. */
    public float master = 0.85f;
    /** Linear gain for gameplay and interface one-shots. */
    public float sfx = 1;
    /** Linear gain for ambience beds and runtime details. */
    public float ambience = 1;
    /** Linear gain for sparse music; zero disables the director. */
    public float music = 0.5f;
    /** Silences every bus without losing the chosen levels. */
    public boolean mute;
    private static final Path FILE = AppPaths.dataDirectory().resolve("veylon_audio.properties");

    /** Creates the documented default mix. */
    public AudioSettings() { }

    /** Copies a mix for an options transaction. */
    public AudioSettings(AudioSettings source) { copyFrom(source); }

    /** Restores all levels and mute, leaving the shared live settings object intact. */
    public void copyFrom(AudioSettings source) {
        master = source.level(Bus.MASTER); sfx = source.level(Bus.SFX);
        ambience = source.level(Bus.AMBIENCE); music = source.level(Bus.MUSIC); mute = source.mute;
    }

    /** Finite, clamped linear level, including when a caller has directly edited a field. */
    public float level(Bus bus) {
        return valid(switch (bus) { case MASTER -> master; case SFX -> sfx; case AMBIENCE -> ambience; case MUSIC -> music; }, bus);
    }

    /** Sets a slider level in the inclusive zero-to-one interval. */
    public void set(Bus bus, float value) {
        float level = valid(value, bus);
        switch (bus) { case MASTER -> master = level; case SFX -> sfx = level; case AMBIENCE -> ambience = level; case MUSIC -> music = level; }
    }

    /** Effective listener gain, with mute taking precedence. */
    public float masterGain() { return mute ? 0 : level(Bus.MASTER); }

    /** Loads user preferences, falling back independently for missing or malformed values. */
    public static AudioSettings loadOrDefaults() { return load(FILE); }

    static AudioSettings load(Path path) {
        var settings = new AudioSettings();
        if (!Files.isRegularFile(path)) return settings;
        var values = new Properties();
        try (var in = Files.newInputStream(path)) {
            values.load(in);
            for (Bus bus : Bus.values()) {
                try { settings.set(bus, Float.parseFloat(values.getProperty(key(bus), "").trim())); }
                catch (NumberFormatException ignored) { /* Retain this bus's default. */ }
            }
            String muted = values.getProperty("mute", "false").trim();
            if (muted.equalsIgnoreCase("true") || muted.equalsIgnoreCase("false")) settings.mute = Boolean.parseBoolean(muted);
        } catch (IOException | IllegalArgumentException e) {
            System.err.println("[settings] failed to read " + path + ": " + e.getMessage());
        }
        return settings;
    }

    /** Saves the current mix using the same location and Properties convention as graphics. */
    public void save() { save(FILE); }

    void save(Path path) {
        var values = new Properties();
        for (Bus bus : Bus.values()) values.setProperty(key(bus), Float.toString(level(bus)));
        values.setProperty("mute", Boolean.toString(mute));
        try {
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            try (var out = Files.newOutputStream(path)) { values.store(out, "VEYLON audio settings"); }
        } catch (IOException e) {
            System.err.println("[settings] failed to write " + path + ": " + e.getMessage());
        }
    }

    private static String key(Bus bus) { return bus.name().toLowerCase(Locale.ROOT); }
    private static float valid(float value, Bus bus) {
        if (!Float.isFinite(value)) return switch (bus) { case MASTER -> 0.85f; case MUSIC -> 0.5f; default -> 1; };
        return Math.max(0, Math.min(1, value));
    }
}
