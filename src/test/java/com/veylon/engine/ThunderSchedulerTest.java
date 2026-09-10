package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

class ThunderSchedulerTest {
    @Test void strikePositionAndDistanceSetDelayGainAndBrightness() {
        var queue = new ThunderScheduler(); var heard = new ArrayList<ThunderScheduler.Strike>();
        queue.schedule(343, 0, 0, 0, 0, 0);
        queue.update(3.9f, heard::add); assertTrue(heard.isEmpty());
        queue.update(0.11f, heard::add); assertEquals(1, heard.size());
        assertEquals(343, heard.getFirst().x()); assertEquals(343, heard.getFirst().distance());
        assertEquals(1, ThunderScheduler.gain(0));
        assertTrue(ThunderScheduler.gain(343) < 0.23f);
        assertEquals(1, ThunderScheduler.highFrequency(0));
        assertTrue(ThunderScheduler.highFrequency(343) < 0.20f);
    }

    @Test void boundedQueueKeepsNearEventsAndResetCannotFireOldWorldStrikes() {
        var queue = new ThunderScheduler(); var heard = new ArrayList<ThunderScheduler.Strike>();
        for (int i = 0; i < 1000; i++) queue.schedule(1000 - i, 0, 0, 0, 0, 0);
        assertEquals(ThunderScheduler.CAPACITY, queue.pending());
        queue.update(1, heard::add);
        assertEquals(ThunderScheduler.CAPACITY, heard.size());
        assertTrue(heard.stream().allMatch(s -> s.distance() <= 16));
        queue.schedule(200, 0, 0, 0, 0, 0); queue.reset(); queue.update(100, heard::add);
        assertEquals(ThunderScheduler.CAPACITY, heard.size()); assertEquals(1, queue.cancelled);
        assertEquals(0, queue.pending());
    }

    @Test void shelterFilterMovesContinuouslyAndIsFramePartitionIndependent() {
        float a = ThunderScheduler.shelterBlend(1, true, 1), b = 1;
        for (int i = 0; i < 100; i++) b = ThunderScheduler.shelterBlend(b, true, 0.01f);
        assertEquals(a, b, 0.00001f);
        assertTrue(a < 0.2f && a > ThunderScheduler.SHELTER_HF);
        float opening = ThunderScheduler.shelterBlend(a, false, 0.01f);
        assertTrue(opening > a && opening < 0.23f);
        assertEquals(ThunderScheduler.SHELTER_HF, ThunderScheduler.shelterBlend(1, true, 100), 0.000001f);
    }

    @Test void disabledManagerDoesNotQueueOrTouchNativeFilters() {
        var audio = new AudioManager();
        audio.scheduleThunder(100, 100, 100);
        audio.setSheltered(true);
        audio.update(100);
        assertEquals(0, audio.pendingWeatherSounds());
        var filters = new AcousticSources(false, new int[]{100}, new int[0]);
        filters.sheltered(true); filters.tint(100, 0.1f, true); filters.update(1); filters.close();
    }
}
