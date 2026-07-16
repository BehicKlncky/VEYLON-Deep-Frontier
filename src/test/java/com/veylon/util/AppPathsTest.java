package com.veylon.util;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AppPathsTest {

    @Test
    void macOsUsesApplicationSupport() {
        Path home = Path.of("home", "player");

        assertEquals(
                home.resolve("Library/Application Support/VEYLON Deep Frontier"),
                AppPaths.dataDirectory("Mac OS X", home.toString(), null));
    }

    @Test
    void windowsRetainsTheExistingWorkingDirectoryLayout() {
        assertEquals(Path.of(""),
                AppPaths.dataDirectory("Windows 11", "unused", null));
    }

    @Test
    void explicitDataDirectoryOverridesThePlatformDefault() {
        Path override = Path.of("build", "test-data").toAbsolutePath().normalize();

        assertEquals(override,
                AppPaths.dataDirectory("Mac OS X", "unused", override.toString()));
    }
}
