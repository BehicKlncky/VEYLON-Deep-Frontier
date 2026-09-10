package com.veylon.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import static com.veylon.engine.AudioSettings.Bus.*;
import static org.junit.jupiter.api.Assertions.*;

class AudioSettingsTest {
    @TempDir Path directory;

    @Test void missingFileUsesPreviousMasterAndIndependentDefaults() {
        var s = AudioSettings.load(directory.resolve("missing.properties"));
        assertEquals(0.85f, s.master); assertEquals(1, s.sfx); assertEquals(1, s.ambience);
        assertEquals(0.5f, s.music); assertFalse(s.mute);
    }

    @Test void malformedNonfiniteAndOutOfRangeValuesAreSafe() throws Exception {
        Path path = directory.resolve("audio.properties");
        Files.writeString(path, "master=NaN\nsfx=-2\nambience=4\nmusic=Infinity\nmute=garbage\n");
        var s = AudioSettings.load(path);
        assertEquals(0.85f, s.master); assertEquals(0, s.sfx); assertEquals(1, s.ambience);
        assertEquals(0.5f, s.music); assertFalse(s.mute);
        Files.writeString(path, "master=oops\nsfx=-Infinity\nmute= TrUe \n");
        s = AudioSettings.load(path); assertEquals(0.85f, s.master); assertEquals(1, s.sfx); assertTrue(s.mute);
        s.master = Float.NaN; assertEquals(0, s.masterGain()); s.mute = false; assertEquals(0.85f, s.masterGain());
    }

    @Test void roundTripPreservesEveryControlAndZeroMusic() {
        Path path = directory.resolve("nested/audio.properties");
        var s = new AudioSettings();
        s.master = 0.4f; s.sfx = 0.3f; s.ambience = 0.2f; s.music = 0; s.mute = true;
        s.save(path); var loaded = AudioSettings.load(path);
        for (var bus : AudioSettings.Bus.values()) assertEquals(s.level(bus), loaded.level(bus));
        assertTrue(loaded.mute); assertEquals(0, loaded.masterGain());
        loaded.mute = false; assertEquals(0.4f, loaded.masterGain());
        assertEquals(0, loaded.level(MUSIC));
    }

    @Test void snapshotRestoresAllValuesAndSliderClamps() {
        var s = new AudioSettings(); var original = new AudioSettings(s);
        s.set(MASTER, -1); s.set(SFX, 2); s.set(AMBIENCE, 0.3f); s.set(MUSIC, 0); s.mute = true;
        assertEquals(0, s.master); assertEquals(1, s.sfx);
        s.copyFrom(original);
        for (var bus : AudioSettings.Bus.values()) assertEquals(original.level(bus), s.level(bus));
        assertFalse(s.mute);
    }
}
