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
}
