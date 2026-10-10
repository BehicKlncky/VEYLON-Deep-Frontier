package com.veylon.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

/** Resolves writable user data without relying on a packaged app's working directory. */
public final class AppPaths {

    public static final String DATA_DIR_PROPERTY = "veylon.dataDir";
    private static final String APP_DIRECTORY = "VEYLON Deep Frontier";

    private AppPaths() {
    }

    public static Path dataDirectory() {
        return dataDirectory(
                System.getProperty("os.name", ""),
                System.getProperty("user.home", "."),
                System.getProperty(DATA_DIR_PROPERTY));
    }

    static Path dataDirectory(String osName, String userHome, String override) {
        if (override != null && !override.isBlank()) {
            return Path.of(override).toAbsolutePath().normalize();
        }
        if (osName.toLowerCase(Locale.ROOT).contains("mac")) {
            return Path.of(userHome, "Library", "Application Support", APP_DIRECTORY);
        }
        // Retain the existing working-directory layout on Windows.
        return Path.of("");
    }

    /**
     * True when {@value #DATA_DIR_PROPERTY} sends this process's saves, settings
     * and captures somewhere other than the player's own data directory — the
     * condition tests and development QA sessions need before they may write a
     * save. Everything is resolved inside the data directory, so a different
     * directory means a different save slot even when one contains the other.
     *
     * <p>When both directories exist, file identity decides rather than spelling,
     * so a symbolic link, junction, short name or case variant of the player's
     * directory is not isolated. An unreadable directory counts as not isolated.
     */
    public static boolean isolatedFromPlayerData() {
        return isolatedFromPlayerData(
                System.getProperty("os.name", ""),
                System.getProperty("user.home", "."),
                System.getProperty(DATA_DIR_PROPERTY));
    }

    static boolean isolatedFromPlayerData(String osName, String userHome, String override) {
        if (override == null || override.isBlank()) {
            return false;
        }
        Path player = dataDirectory(osName, userHome, null).toAbsolutePath().normalize();
        Path chosen = dataDirectory(osName, userHome, override);
        if (chosen.equals(player)) {
            return false;
        }
        try {
            return !(Files.exists(chosen) && Files.exists(player) && Files.isSameFile(chosen, player));
        } catch (IOException | SecurityException unreadable) {
            return false;
        }
    }
}
