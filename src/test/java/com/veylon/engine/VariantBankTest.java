package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class VariantBankTest {
    @Test void roundRobinNeverRepeatsIncludingWraparoundAndBanksAreIndependent() {
        var a = new VariantBank(new int[]{7, 9, 11, 13});
        var b = new VariantBank(new int[]{20, 21, 22, 23});
        int previous = -1;
        for (int i = 0; i < 1000; i++) {
            int next = a.next();
            assertNotEquals(previous, next);
            assertEquals(new int[]{7, 9, 11, 13}[i % 4], next);
            previous = next;
        }
        assertEquals(20, b.next());
        assertThrows(IllegalArgumentException.class, () -> new VariantBank(new int[]{1, 1}));
    }

    @Test void everyFrequentSoundHasFourDistinctEqualDurationTakes() {
        var catalog = AudioCharacterizationTest.baseline();
        long addedBytes = 0;
        for (String name : VariantBank.NAMES) {
            float[] original = catalog.get(name);
            for (int i = 1; i < VariantBank.COUNT; i++) {
                float[] take = catalog.get(VariantBank.key(name, i));
                assertNotNull(take, name);
                assertEquals(original.length, take.length, name);
                for (int j = 0; j < i; j++) {
                    assertFalse(Arrays.equals(take, catalog.get(VariantBank.key(name, j))), name);
                }
                addedBytes += take.length * 2L;
            }
        }
        System.out.println("[audio-variants] banks=" + VariantBank.NAMES.length + " extraPcmBytes=" + addedBytes);
        assertTrue(addedBytes < 2 * 1024 * 1024, "Frequent takes remain below 2 MiB extra PCM");
    }
}
