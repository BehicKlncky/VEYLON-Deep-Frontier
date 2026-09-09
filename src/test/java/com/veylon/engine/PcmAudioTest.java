package com.veylon.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.DynamicTest;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class PcmAudioTest {
    @TestFactory Stream<DynamicTest> allUploadedBuffersHaveHeadroomZeroDcAndContinuousBoundaries() {
        return AudioCharacterizationTest.baseline().entrySet().stream().map(entry ->
                DynamicTest.dynamicTest(entry.getKey(), () -> {
                    float[] s = PcmAudio.prepare(entry.getValue());
                    AudioSignalAssertions.clean(entry.getKey(), s);
                    assertEquals(0, s[0]);
                    assertEquals(0, s[s.length - 1]);
                    for (float value : s) {
                        assertTrue(Float.isFinite(value));
                        assertTrue(Math.abs(PcmAudio.encode(value)) < 29490,
                                entry.getKey() + " reaches the hard clamp");
                    }
                }));
    }

    @Test void theHarnessRejectsDcClicksClippingAndBrokenSeams() {
        float[] clean = PcmAudio.prepare(new float[]{0, 0.1f, -0.1f, 0});
        AudioSignalAssertions.clean("fixture", clean);
        assertThrows(AssertionError.class, () -> AudioSignalAssertions.clean("DC", new float[]{0.1f, 0.1f}));
        assertThrows(AssertionError.class, () -> AudioSignalAssertions.clean("clip", new float[]{0, 1.2f, -1.2f, 0}));
        assertThrows(AssertionError.class, () -> AudioSignalAssertions.clean("seam", new float[]{0.1f, -0.1f}));
        assertThrows(AssertionError.class, () -> AudioSignalAssertions.clean("click", new float[]{0, 1, 0}));
    }

    @Test void nonFiniteInputFailsBeforeNativeUpload() {
        assertThrows(IllegalArgumentException.class, () -> PcmAudio.prepare(new float[]{Float.NaN}));
        assertThrows(IllegalArgumentException.class, () -> PcmAudio.prepare(new float[]{Float.POSITIVE_INFINITY}));
    }

    @Test void conditioningPreservesDurationAndRelativePeaks() {
        float[] s = new float[2000];
        s[500] = 2;
        s[501] = -2;
        s[1000] = 1;
        s[1001] = -1;
        PcmAudio.prepare(s);
        assertEquals(2000, s.length);
        assertEquals(2, s[500] / s[1000], 0.00001,
                "Peak normalization preserves dynamics; hard clipping would flatten this ratio");
    }
}
