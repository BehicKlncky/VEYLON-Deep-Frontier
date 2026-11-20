package com.veylon.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The mouse settings and their file (0.9.1 milestone 06, D-06): the default turns the view by the
 * fixed value of before, the offered steps hold at their ends, and a relaunch reads back what was
 * written, each malformed value falling back to its own default.
 */
class ControlSettingsTest {

    @TempDir
    Path directory;

    @Test
    void theDefaultTurnsTheViewAsTheFixedValueDidAndInvertYIsOff() {
        ControlSettings settings = new ControlSettings();
        assertEquals(100, settings.sensitivity());
        assertEquals(0.115f, settings.degreesPerPixel(), "exactly the 0.115 of before 0.9.1");
        assertFalse(settings.invertY);
    }

    @Test
    void theSensitivityMovesInOfferedStepsAndHoldsAtItsEnds() {
        ControlSettings settings = new ControlSettings();
        settings.stepSensitivity(5);
        assertEquals(150, settings.sensitivity());
        assertEquals(0.115f * 1.5f, settings.degreesPerPixel(), 1e-7f);
        settings.stepSensitivity(100);
        assertEquals(ControlSettings.MAX_SENSITIVITY, settings.sensitivity(), "held at 300%");
        settings.stepSensitivity(-100);
        assertEquals(ControlSettings.MIN_SENSITIVITY, settings.sensitivity(), "and at 10%");
        settings.setSensitivity(40);
        assertEquals(40, settings.sensitivity());
        for (int refused : new int[]{0, 5, 45, 310, -10}) {
            assertThrows(IllegalArgumentException.class, () -> settings.setSensitivity(refused), refused + "%");
        }
        ControlSettings copy = new ControlSettings(settings);
        copy.invertY = true;
        copy.stepSensitivity(1);
        assertEquals(40, settings.sensitivity(), "a copy is its own");
        assertFalse(settings.invertY);
        settings.copyFrom(copy);
        assertEquals(50, settings.sensitivity());
        assertTrue(settings.invertY);
    }

    @Test
    void whatIsWrittenIsReadBackAtTheNextLaunch() {
        Path file = directory.resolve("veylon_controls.properties");
        assertEquals(100, ControlSettings.load(file).sensitivity(), "no file: the defaults");
        ControlSettings settings = new ControlSettings();
        settings.setSensitivity(170);
        settings.invertY = true;
        settings.save(file);
        ControlSettings read = ControlSettings.load(file);
        assertEquals(170, read.sensitivity());
        assertTrue(read.invertY);
    }

    @Test
    void eachMalformedValueFallsBackToItsOwnDefault() throws IOException {
        Path file = directory.resolve("veylon_controls.properties");
        Files.writeString(file, "mouseSensitivity=155\ninvertY=true\n");
        ControlSettings offStep = ControlSettings.load(file);
        assertEquals(100, offStep.sensitivity(), "a value between the steps is not offered");
        assertTrue(offStep.invertY, "and the other value still reads");
        Files.writeString(file, "mouseSensitivity=80\ninvertY=maybe\n");
        ControlSettings unclear = ControlSettings.load(file);
        assertEquals(80, unclear.sensitivity());
        assertFalse(unclear.invertY);
        Files.writeString(file, "mouseSensitivity=fast\n");
        assertEquals(100, ControlSettings.load(file).sensitivity());
        Files.write(file, new byte[]{'\\', 'u', '0', '0', 'Z'});
        assertEquals(100, ControlSettings.load(file).sensitivity(), "an unreadable file is the defaults");
    }
}
