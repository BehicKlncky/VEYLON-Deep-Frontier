package com.veylon.engine;

import org.junit.jupiter.api.Test;
import static com.veylon.engine.VoicePool.Priority.*;
import static org.junit.jupiter.api.Assertions.*;

class VoicePoolTest {
    static final class Fake implements VoicePool.Backend {
        final boolean[] playing = new boolean[VoicePool.CAPACITY];
        final float[] gain = new float[VoicePool.CAPACITY];
        final int[] buffer = new int[VoicePool.CAPACITY];
        int starts;
        public boolean playing(int i) { return playing[i]; }
        public void start(int i, VoicePool.Request r) { playing[i] = true; gain[i] = r.gain(); buffer[i] = r.buffer(); starts++; }
        public void gain(int i, float value) { gain[i] = value; }
        public void stop(int i) { playing[i] = false; }
    }

    static VoicePool.Request request(int buffer, VoicePool.Priority priority, float gain) {
        return new VoicePool.Request(buffer, 0, 0, 0, gain, 1, 3, 44, true, true, false, priority);
    }

    @Test void criticalReplacesLowestPriorityThenQuietestThenOldest() {
        var backend = new Fake(); var pool = new VoicePool(backend);
        for (int i = 0; i < VoicePool.CAPACITY; i++) pool.play(request(i + 1, i < 3 ? BACKGROUND : IMPORTANT, i == 0 ? 0.8f : 0.2f));
        assertTrue(pool.play(request(99, CRITICAL, 1)));
        assertEquals(VoicePool.CAPACITY, backend.starts, "Victim must fade before replacement");
        pool.update(VoicePool.STEAL_SECONDS);
        assertEquals(0, backend.gain[1]);
        assertEquals(2, backend.buffer[1], "Zero gain is submitted before reusing the source");
        pool.update(0.001f);
        assertEquals(99, backend.buffer[1], "Equal quiet backgrounds steal the oldest");
        assertEquals(1, backend.buffer[0]);
        assertEquals(1, pool.steals);
    }

    @Test void distanceCountsAndPendingCriticalCannotBeOverwrittenByFootsteps() {
        var backend = new Fake(); var pool = new VoicePool(backend);
        for (int i = 0; i < VoicePool.CAPACITY; i++) pool.play(request(i + 1, CRITICAL, 0.5f));
        assertFalse(pool.play(request(70, BACKGROUND, 1)));
        assertTrue(pool.play(request(99, CRITICAL, 1)));
        assertFalse(pool.play(request(71, ORDINARY, 1)));
        assertEquals(2, pool.dropped);
        var far = new VoicePool.Request(1, 100, 0, 0, 1, 1, 3, 220, false, true, false, ORDINARY);
        assertTrue(far.audibility(0, 0, 0) < 0.03f);
        assertEquals(1, far.audibility(100, 0, 0));
    }

    @Test void fadeIsContinuousMonotonicAndResetCancelsPendingPlayback() {
        float previous = 1;
        for (int i = 0; i <= 1000; i++) {
            float gain = VoicePool.fadeGain(i * VoicePool.STEAL_SECONDS / 1000);
            assertTrue(gain <= previous && previous - gain < 0.002f);
            previous = gain;
        }
        assertEquals(0, previous);
        var backend = new Fake(); var pool = new VoicePool(backend);
        for (int i = 0; i < VoicePool.CAPACITY; i++) pool.play(request(i + 1, BACKGROUND, 1));
        pool.play(request(99, CRITICAL, 1)); pool.update(0.01f);
        assertTrue(backend.gain[0] < 1 && backend.gain[0] > 0);
        pool.reset(); pool.update(1);
        assertEquals(VoicePool.CAPACITY, backend.starts);
        for (boolean playing : backend.playing) assertFalse(playing);
    }

    @Test void aDistantLoudSourceYieldsBeforeANearQuietSource() {
        var backend = new Fake(); var pool = new VoicePool(backend);
        for (int i = 0; i < VoicePool.CAPACITY - 1; i++) pool.play(request(i + 1, ORDINARY, 0.2f));
        pool.play(new VoicePool.Request(80, 100, 0, 0, 1, 1, 3, 220, false, true, false, ORDINARY));
        pool.play(request(99, IMPORTANT, 1));
        pool.update(1); pool.update(0);
        assertEquals(99, backend.buffer[VoicePool.CAPACITY - 1]);
        assertEquals(1, backend.buffer[0]);
    }
}
