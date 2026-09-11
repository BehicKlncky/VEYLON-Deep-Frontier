package com.veylon.ui;

import com.veylon.engine.AudioSettings;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AudioOptionsScreenTest {
    @Test void keyboardAndDragEditTheSharedMixAndCancelRestoresIt() {
        var settings = new AudioSettings(); var screen = new AudioOptionsScreen();
        screen.open(settings);
        AudioOptionsScreen.adjust(settings, 0, -1);
        assertEquals(0.8f, settings.master, 0.00001f);
        AudioOptionsScreen.drag(settings, 1, 150, 100, 200); assertEquals(0.25f, settings.sfx);
        AudioOptionsScreen.drag(settings, 2, 999, 100, 200); assertEquals(1, settings.ambience);
        AudioOptionsScreen.drag(settings, 3, -10, 100, 200); assertEquals(0, settings.music);
        AudioOptionsScreen.adjust(settings, 4, 1); assertTrue(settings.mute);
        screen.cancel(settings);
        assertEquals(0.85f, settings.master); assertEquals(1, settings.sfx); assertEquals(0.5f, settings.music);
        assertFalse(settings.mute);
    }

    @Test void reopeningUsesTheCurrentMixAsItsCancelSnapshot() {
        var settings = new AudioSettings(); var screen = new AudioOptionsScreen();
        settings.sfx = 0.25f; screen.open(settings);
        AudioOptionsScreen.adjust(settings, 1, 1); screen.cancel(settings);
        assertEquals(0.25f, settings.sfx);
        settings.sfx = 0.6f; screen.open(settings);
        AudioOptionsScreen.adjust(settings, 1, -1); screen.cancel(settings);
        assertEquals(0.6f, settings.sfx);
    }
}
