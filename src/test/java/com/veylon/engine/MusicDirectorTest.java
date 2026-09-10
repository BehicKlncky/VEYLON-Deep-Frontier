package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class MusicDirectorTest {
    static final class Fake implements MusicDirector.Backend {
        final ArrayList<MusicMood> starts = new ArrayList<>();
        boolean active; float gain;
        public void start(MusicMood mood) { starts.add(mood); active = true; }
        public void gain(float value) { gain = value; }
        public void stop() { active = false; }
    }

    @Test void longRunIsOverNinetyPercentSilentWithNoCatchupBurst() {
        var backend = new Fake(); var director = new MusicDirector(new Random(60600), backend);
        director.scene(MusicMood.CALM);
        int audible = 0; float gap = 0; boolean wasActive = false;
        for (int i = 0; i < 100000; i++) {
            director.update(0.1f, 1);
            if (backend.active) {
                if (!wasActive && backend.starts.size() > 1) assertTrue(gap >= MusicDirector.SILENCE_MIN - 0.2f);
                audible++; gap = 0;
            } else gap += 0.1f;
            wasActive = backend.active;
        }
        assertTrue(audible < 10000, "Over 90 percent silence across 10,000 seconds");
        int before = backend.starts.size(); director.update(100000, 1);
        assertTrue(backend.starts.size() <= before + 1, "At most one event per update");
    }

    @Test void stateTransitionReleasesBeforeAnotherPhraseCanEnter() {
        var backend = new Fake(); var director = new MusicDirector(new Random(1), backend);
        director.scene(MusicMood.CALM); director.update(12, 1); director.update(4, 1);
        float previous = backend.gain; assertTrue(previous > 0);
        director.scene(MusicMood.THREAT);
        // Allow one frame at each threshold rather than relying on exact float addition.
        for (int i = 0; i < 410; i++) {
            director.update(0.01f, 1);
            assertTrue(Math.abs(backend.gain - previous) < 0.004f);
            previous = backend.gain;
        }
        assertFalse(backend.active); assertEquals(1, backend.starts.size());
        director.update(200, 1); assertEquals(MusicMood.THREAT, backend.starts.getLast());
    }

    @Test void zeroMusicFadesOutThenStaysDisabledAndWorldResetStopsImmediately() {
        var backend = new Fake(); var director = new MusicDirector(new Random(1), backend);
        director.scene(MusicMood.DEEP_CAVE); director.update(100, 0); assertTrue(backend.starts.isEmpty());
        director.update(12, 0.5f); director.update(4, 0.5f);
        float gain = backend.gain; director.update(0.1f, 0);
        assertTrue(backend.gain < gain && backend.gain > 0);
        director.update(2, 0); assertFalse(backend.active); assertEquals(0, backend.gain);
        director.update(100000, 0); assertEquals(1, backend.starts.size());
        director.reset(); director.update(100000, 1); assertEquals(1, backend.starts.size(), "No world, no music");
    }

    @Test void firstNightIsOncePerWorldSessionAndEveryMoodCanBeSelected() {
        var backend = new Fake(); var director = new MusicDirector(new Random(1), backend);
        director.scene(MusicMood.FIRST_NIGHT); director.update(12, 1); director.update(12, 1); director.update(200, 1);
        assertEquals(MusicMood.FIRST_NIGHT, backend.starts.getFirst()); assertEquals(MusicMood.NIGHT, backend.starts.getLast());
        for (MusicMood mood : MusicMood.values()) {
            director.reset(); director.scene(mood); director.update(12, 1);
            assertEquals(mood, backend.starts.getLast());
        }
    }

    @Test void phraseEnvelopeHasQuietEdgesAndSmoothInterior() {
        assertEquals(0, MusicDirector.envelope(0)); assertEquals(0, MusicDirector.envelope(12));
        assertEquals(1, MusicDirector.envelope(5));
        for (int i = 1; i <= 1200; i++) assertTrue(Math.abs(MusicDirector.envelope(i * 0.01f)
                - MusicDirector.envelope((i - 1) * 0.01f)) < 0.01f);
    }

    @Test void headlessManagerNeverStartsTheNativeDirector() {
        var audio = new AudioManager();
        for (MusicMood mood : MusicMood.values()) { audio.setMusicMood(mood); audio.update(10000); }
        audio.setMusicMood(null); audio.resetWorld(); audio.shutdown();
        assertFalse(audio.isEnabled());
    }
}
