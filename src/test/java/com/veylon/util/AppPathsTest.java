package com.veylon.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

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

    @Test
    void onlyAnOverrideThatLeavesThePlayersDirectoryIsolatesASession() {
        Path home = Path.of("home", "player").toAbsolutePath();
        Path macPlayer = home.resolve("Library/Application Support/VEYLON Deep Frontier");
        String workingDirectory = Path.of("").toAbsolutePath().toString();

        assertFalse(AppPaths.isolatedFromPlayerData("Windows 11", home.toString(), null));
        assertFalse(AppPaths.isolatedFromPlayerData("Windows 11", home.toString(), "  "));
        assertFalse(AppPaths.isolatedFromPlayerData("Windows 11", home.toString(), workingDirectory),
                "on Windows the player's data directory is the working directory");
        assertFalse(AppPaths.isolatedFromPlayerData("Windows 11", home.toString(), "."));
        assertFalse(AppPaths.isolatedFromPlayerData("Mac OS X", home.toString(),
                macPlayer.toString()));

        // A subdirectory is isolated: every file resolves inside the data directory.
        assertTrue(AppPaths.isolatedFromPlayerData("Windows 11", home.toString(), "build/qa/urban"));
        assertTrue(AppPaths.isolatedFromPlayerData("Mac OS X", home.toString(),
                home.resolve("qa").toString()));
    }

    // "Windows 11" makes the working directory the player's directory on any host.

    @Test
    void aCaseVariantOfThePlayersDirectoryIsNotIsolated(@TempDir Path temporary) {
        Path player = Path.of("").toAbsolutePath();
        Path upperCase = Path.of(player.toString().toUpperCase(Locale.ROOT));
        if (Files.exists(upperCase)) {
            assertFalse(AppPaths.isolatedFromPlayerData("Windows 11", "unused",
                    upperCase.toString()), "a case variant on a case-insensitive volume");
        }
        assertTrue(AppPaths.isolatedFromPlayerData("Windows 11", "unused", temporary.toString()),
                "an existing, different directory is isolated");
    }

    @Test
    void aLinkToThePlayersDirectoryIsNotIsolated(@TempDir Path temporary) throws IOException {
        Path player = Path.of("").toAbsolutePath();
        Path alias = temporary.resolve("alias");
        try {
            Files.createSymbolicLink(alias, player);
        } catch (IOException | UnsupportedOperationException | SecurityException noLinks) {
            assumeTrue(false, "this host cannot create symbolic links: " + noLinks);
        }
        try {
            assertFalse(AppPaths.isolatedFromPlayerData("Windows 11", "unused", alias.toString()),
                    "a link to the player's directory writes the player's save slot");
        } finally {
            // Remove the link itself before temp-dir cleanup could ever walk through it.
            Files.delete(alias);
        }
    }
}
