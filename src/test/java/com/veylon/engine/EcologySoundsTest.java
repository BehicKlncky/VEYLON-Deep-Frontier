package com.veylon.engine;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class EcologySoundsTest {
    @Test
    void cuesAreDistinctCleanAndIndependentOfExistingCatalogRandomness() {
        Map<String, float[]> cues = new LinkedHashMap<>(), catalog = new LinkedHashMap<>();
        EcologySounds.synthesize(cues::put);
        new ProceduralAudio(new Random(7)).synthesize(catalog::put);
        assertEquals(EcologySounds.Cue.values().length, cues.size());
        for (var entry : cues.entrySet()) {
            assertArrayEquals(entry.getValue(), catalog.get(entry.getKey()));
            AudioSignalAssertions.clean(entry.getKey(), PcmAudio.prepare(entry.getValue().clone()));
        }
        // The telegraph is what a player learns to react to: it must not sound like the lunge.
        assertFalse(Arrays.equals(cues.get(EcologySounds.key(EcologySounds.Cue.STIR)),
                cues.get(EcologySounds.key(EcologySounds.Cue.LUNGE))));
    }
}
