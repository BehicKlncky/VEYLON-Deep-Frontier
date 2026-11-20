package com.veylon.save;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The autosave's persisted settings (0.9.1 milestone 05, decision D-03). */
class AutosaveSettingsTest {

    @TempDir
    Path dir;

    @Test
    void autosaveIsOnEveryTenMinutesUntilThePlayerSaysOtherwise() {
        AutosaveSettings settings = AutosaveSettings.load(dir.resolve("none.properties"));
        assertTrue(settings.enabled);
        assertEquals(10, settings.intervalMinutes());
        assertEquals(600.0, settings.intervalSeconds());
    }

    @Test
    void theChoiceSurvivesARelaunch() {
        Path file = dir.resolve("veylon_autosave.properties");
        AutosaveSettings chosen = new AutosaveSettings();
        chosen.enabled = false;
        chosen.setIntervalMinutes(30);
        chosen.save(file);

        AutosaveSettings relaunched = AutosaveSettings.load(file);
        assertFalse(relaunched.enabled);
        assertEquals(30, relaunched.intervalMinutes());
    }

    @Test
    void aMalformedValueFallsBackToItsOwnDefaultAlone() throws Exception {
        Path file = dir.resolve("veylon_autosave.properties");
        Files.writeString(file, "enabled=false\nintervalMinutes=7\n");
        AutosaveSettings odd = AutosaveSettings.load(file);
        assertFalse(odd.enabled, "the valid value is kept");
        assertEquals(10, odd.intervalMinutes(), "an interval the screen does not offer is the default");

        Files.writeString(file, "enabled=maybe\nintervalMinutes=15\n");
        AutosaveSettings other = AutosaveSettings.load(file);
        assertTrue(other.enabled, "an unreadable switch is the default");
        assertEquals(15, other.intervalMinutes());
    }

    @Test
    void theIntervalStepsThroughTheOfferedOnesAndWrapsAround() {
        AutosaveSettings settings = new AutosaveSettings();
        int[] forward = new int[5];
        for (int i = 0; i < forward.length; i++) {
            settings.stepInterval(1);
            forward[i] = settings.intervalMinutes();
        }
        assertArrayEquals(new int[]{15, 30, 5, 10, 15}, forward);
        settings.stepInterval(-2);
        assertEquals(5, settings.intervalMinutes());
        assertThrows(IllegalArgumentException.class, () -> settings.setIntervalMinutes(7));
        assertEquals(5, settings.intervalMinutes(), "a refused interval changes nothing");
    }

    @Test
    void anOptionsTransactionCanBeUndone() {
        AutosaveSettings live = new AutosaveSettings();
        AutosaveSettings before = new AutosaveSettings(live);
        live.enabled = false;
        live.stepInterval(1);
        live.copyFrom(before);
        assertTrue(live.enabled);
        assertEquals(10, live.intervalMinutes());
    }
}
