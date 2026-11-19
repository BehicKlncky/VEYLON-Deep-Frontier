package com.veylon.testsupport;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the B02 retry is in force for the whole suite: {@code junit-platform.properties} makes
 * {@link TrackedTempDirFactory} the default factory, and extension autodetection registers
 * {@link TempDirCleanup}. Without either, the retry would silently stop applying.
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TempDirCleanupWiringTest {

    private static Path firstTestsDirectory;

    @Test
    @Order(1)
    void everyTempDirTheSuiteCreatesIsTracked(@TempDir Path directory) {
        assertTrue(TempDirCleanup.isTracked(directory), "created by the suite's default factory");
        firstTestsDirectory = directory;
    }

    @Test
    @Order(2)
    void theExtensionDeletedThePreviousTestsDirectory() {
        assertNotNull(firstTestsDirectory, "the first test ran");
        assertFalse(TempDirCleanup.isTracked(firstTestsDirectory), "its after-each callback took it");
        assertTrue(Files.notExists(firstTestsDirectory));
    }

    @Test
    @Order(3)
    void aDirectoryWithItsOwnCleanupModeIsLeftToJUnit(@TempDir(cleanup = CleanupMode.ON_SUCCESS) Path kept) {
        assertFalse(TempDirCleanup.isTracked(kept), "ON_SUCCESS keeps its meaning");
    }
}
