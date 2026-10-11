package com.veylon.engine;

import org.junit.jupiter.api.Test;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class InfectedSoundsTest {
    @Test void cuesAreDistinctCleanAndIndependentOfExistingCatalogRandomness() {
        Map<String, float[]> cues = new LinkedHashMap<>(), catalog = new LinkedHashMap<>();
        InfectedSounds.synthesize(cues::put);
        new ProceduralAudio(new Random(7)).synthesize(catalog::put);
        assertEquals(4, cues.size());
        for (var entry : cues.entrySet()) {
            assertArrayEquals(entry.getValue(), catalog.get(entry.getKey()));
            AudioSignalAssertions.clean(entry.getKey(), PcmAudio.prepare(entry.getValue().clone()));
        }
        assertFalse(java.util.Arrays.equals(cues.get(InfectedSounds.key(InfectedSounds.Cue.NOTICE)),
                cues.get(InfectedSounds.key(InfectedSounds.Cue.STRIKE))));
    }
}
