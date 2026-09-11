package com.veylon.engine;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.veylon.engine.AudioEnvironment.Zone.*;

class AudioEnvironmentTest {
    @Test void syntheticWorldStatesDistinguishAllSixZones() {
        assertEquals(OPEN, AudioEnvironment.classify(0, true, false, false));
        assertEquals(FOREST, AudioEnvironment.classify(0, true, true, false));
        assertEquals(SHELTER, AudioEnvironment.classify(0, false, true, false));
        assertEquals(STONE_STRUCTURE, AudioEnvironment.classify(0, false, false, true));
        assertEquals(SHALLOW, AudioEnvironment.classify(6, false, true, true));
        assertEquals(DEEP_CAVE, AudioEnvironment.classify(18, false, true, true));
        assertEquals(SHELTER, AudioEnvironment.classify(5, false, false, false));
    }

    @Test void parameterTransitionsAreSmoothAndIndependentOfFramePartition() {
        float[] a = new float[6], b = new float[6];
        for (int i = 0; i < 6; i++) a[i] = b[i] = ReverbPresets.value(OPEN, i);
        ReverbPresets.blend(a, DEEP_CAVE, 0.1f);
        assertTrue(a[0] > ReverbPresets.value(OPEN, 0) && a[0] < ReverbPresets.value(DEEP_CAVE, 0));
        for (int i = 0; i < 10; i++) ReverbPresets.blend(b, DEEP_CAVE, 0.01f);
        assertArrayEquals(a, b, 0.000001f);
    }

    @Test void presetsStayWithinStandardEfxRanges() {
        for (var zone : AudioEnvironment.Zone.values()) {
            assertTrue(ReverbPresets.value(zone, 0) >= 0.1 && ReverbPresets.value(zone, 0) <= 20);
            for (int i = 1; i < 6; i++) assertTrue(ReverbPresets.value(zone, i) >= 0 && ReverbPresets.value(zone, i) <= 1);
        }
        assertTrue(ReverbPresets.value(DEEP_CAVE, 0) > 8 * ReverbPresets.value(OPEN, 0));
    }

    @Test void absentEfxIsDryAndEveryOperationIsSafeWithoutANativeContext() {
        EfxProcessor effects = new EfxProcessor();
        effects.init(false);
        effects.init(false);
        assertFalse(effects.enabled());
        effects.zone(DEEP_CAVE);
        effects.route(123, true);
        effects.update(10);
        effects.reset();
        effects.close();
        new AudioManager().setEnvironment(DEEP_CAVE);
    }
}
