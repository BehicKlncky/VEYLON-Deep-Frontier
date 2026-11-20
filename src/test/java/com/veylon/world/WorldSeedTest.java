package com.veylon.world;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The one rule by which a seed is read from text (0.9.1 milestone 06, D-33), shared by NEW
 * FRONTIER's seed field and {@code VEYLON_SEED}: a number is the seed itself, any other text is
 * hashed the way {@code VEYLON_SEED} has always hashed it.
 */
class WorldSeedTest {

    @Test
    void aWholeNumberIsTheSeedItselfWithItsSignAndWithoutTheSpacesAroundIt() {
        assertEquals(20_261_120L, WorldSeed.of("20261120"));
        assertEquals(-42L, WorldSeed.of("-42"));
        assertEquals(42L, WorldSeed.of("+42"));
        assertEquals(7L, WorldSeed.of("  7 \t"));
        assertEquals(Long.MIN_VALUE, WorldSeed.of(Long.toString(Long.MIN_VALUE)));
        assertEquals(Long.MAX_VALUE, WorldSeed.of(Long.toString(Long.MAX_VALUE)));
        assertTrue(WorldSeed.isNumber(" -9223372036854775808 "));
        for (long seed : new long[]{0, 1, -1, 20_260_731L, -7_046_029_254_386_353_131L}) {
            assertEquals(seed, WorldSeed.of(Long.toString(seed)), "a shown seed typed back is the same seed");
        }
    }

    @Test
    void anyOtherTextIsHashedAsVeylonSeedHashesIt() {
        // The rule QaHarness.configuredSeed applied to VEYLON_SEED before 0.9.1 milestone 06.
        for (String text : new String[]{"deep frontier", "  Varrow  ", "Çığ İĞÜÖŞ", "1.5", "12 34",
                "9223372036854775808", "0x10", "seed-7"}) {
            assertEquals((long) text.trim().hashCode(), WorldSeed.of(text), text);
            assertFalse(WorldSeed.isNumber(text), text);
        }
        assertEquals(WorldSeed.of("deep frontier"), WorldSeed.of("deep frontier "), "spaces around words do not count");
        assertNotEquals(WorldSeed.of("deep frontier"), WorldSeed.of("Deep frontier"), "case does");
    }

    @Test
    void blankTextNamesNoSeed() {
        assertThrows(IllegalArgumentException.class, () -> WorldSeed.of(""));
        assertThrows(IllegalArgumentException.class, () -> WorldSeed.of("   "));
        assertFalse(WorldSeed.isNumber(""));
    }
}
