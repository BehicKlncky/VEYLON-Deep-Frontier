package com.veylon.util;

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
}
