package com.veylon.save;

import com.veylon.util.AppPaths;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The structural seam that keeps tests away from the player's save slot
 * (urban-survival decision D-23). On Windows the default data directory is
 * the working directory — the repository checkout under Gradle — so without
 * it any test reaching {@link SaveSystem#SAVE_PATH} would write the real slot.
 * Since 0.9.1 every worker of a test task has its own directory, so parallel
 * forks never share a slot either ({@code WorkerDataDirectory}).
 */
class SaveSlotIsolationTest {

    @Test
    void theBuildGivesEveryTestADataDirectoryUnderBuild() {
        String override = System.getProperty(AppPaths.DATA_DIR_PROPERTY);
        assertNotNull(override, "build.gradle sets " + AppPaths.DATA_DIR_PROPERTY
                + " for every Test task; run tests through Gradle");
        Path dataDirectory = AppPaths.dataDirectory();
        Path build = Path.of("build").toAbsolutePath().normalize();

        assertTrue(dataDirectory.startsWith(build), () -> dataDirectory + " is not under " + build);
        assertTrue(AppPaths.isolatedFromPlayerData());
        assertEquals(dataDirectory.resolve("saves/veylon.sav"),
                SaveSystem.SAVE_PATH.toAbsolutePath().normalize(),
                "the save slot every default save and load uses resolves inside it");
    }

    /**
     * 0.9.1 milestone 04: every file a slot writes, its backups, a damaged save a restore sets aside
     * and VEYLON 0.9.0's file the picker mentions all resolve inside this worker's data directory.
     */
    @Test
    void everySlotAndBackupFileResolvesInsideTheDataDirectory() {
        Path dataDirectory = AppPaths.dataDirectory().toAbsolutePath().normalize();
        SaveSlots slots = SaveSlots.inDataDirectory();
        assertEquals(dataDirectory.resolve("saves"), slots.directory(), "the player's slots are its saves folder");
        java.util.List<Path> files = new java.util.ArrayList<>();
        for (int slot = 1; slot <= SaveSlots.COUNT; slot++) {
            files.addAll(slots.files(slot));
            for (int n = 1; n <= SaveSlots.MAX_SET_ASIDE; n++) {
                files.add(slots.setAside(slot, n));
            }
        }
        files.add(slots.legacySave());
        assertEquals(SaveSlots.COUNT * (1 + SaveSlots.BACKUPS + SaveSlots.MAX_SET_ASIDE) + 1,
                new java.util.HashSet<>(files).size(), "every file has its own name");
        for (Path file : files) {
            Path resolved = file.toAbsolutePath().normalize();
            assertTrue(resolved.startsWith(dataDirectory.resolve("saves")), () -> resolved + " is outside the data directory");
            assertTrue(AppPaths.isolatedFromPlayerData());
        }
    }

    @Test
    void everyWorkerOfATestTaskHasItsOwnDataDirectory() {
        String worker = System.getProperty("org.gradle.test.worker");
        assertNotNull(worker, "Gradle names every test worker; run tests through Gradle");
        Path dataDirectory = AppPaths.dataDirectory();
        Path testData = Path.of("build", "test-data").toAbsolutePath().normalize();

        assertEquals("worker-" + worker, dataDirectory.getFileName().toString(),
                "this worker's own directory, which no other fork writes");
        assertTrue(dataDirectory.getParent().startsWith(testData),
                () -> dataDirectory + " belongs to a task directory under " + testData);
        assertEquals(dataDirectory.resolve("saves/veylon.sav"),
                SaveSystem.SAVE_PATH.toAbsolutePath().normalize(),
                "chosen before SaveSystem resolved its slot");
    }
}
