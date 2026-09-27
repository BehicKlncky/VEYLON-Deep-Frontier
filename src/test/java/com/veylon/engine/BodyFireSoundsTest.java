package com.veylon.engine;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A burning body sounds like fire, synthesized like every other sound: a
 * bright crackle, a dark rush as it catches, a hiss of steam as it is put
 * out. They are made last, from their own generator, so the rest of the
 * catalog is untouched by their existence; and a headless game or a device
 * without audio plays them as silence.
 */
class BodyFireSoundsTest {

    private static final int RATE = ProceduralAudio.RATE;

    @Test
    void theThreeSoundsComeLastAndFromTheirOwnGenerator() {
        Map<String, float[]> catalog = new LinkedHashMap<>();
        new ProceduralAudio(new Random(60600)).synthesize(catalog::put);
        List<String> names = new ArrayList<>(catalog.keySet());
        int n = names.size();
        assertEquals(List.of(BodyFireSounds.CRACKLE, BodyFireSounds.FLARE, BodyFireSounds.SIZZLE),
                names.subList(n - 3, n), "made after everything else");

        Map<String, float[]> alone = new LinkedHashMap<>();
        BodyFireSounds.synthesize(new Random(BodyFireSounds.SEED), alone::put);
        for (String name : alone.keySet()) {
            assertArrayEquals(alone.get(name), catalog.get(name),
                    name + " does not depend on the catalog's generator, so it cannot shift it");
        }

        Map<String, float[]> otherSeed = new LinkedHashMap<>();
        new ProceduralAudio(new Random(1)).synthesize(otherSeed::put);
        assertArrayEquals(catalog.get(BodyFireSounds.FLARE), otherSeed.get(BodyFireSounds.FLARE));
    }

    @Test
    void theyLastAsLongAsTheyShouldAndSoundLikeWhatTheyAre() {
        Map<String, float[]> sounds = new LinkedHashMap<>();
        BodyFireSounds.synthesize(new Random(BodyFireSounds.SEED), sounds::put);
        float[] crackle = sounds.get(BodyFireSounds.CRACKLE);
        float[] flare = sounds.get(BodyFireSounds.FLARE);
        float[] sizzle = sounds.get(BodyFireSounds.SIZZLE);
        assertEquals((int) (BodyFireSounds.CRACKLE_SECONDS * RATE), crackle.length);
        assertEquals((int) (BodyFireSounds.FLARE_SECONDS * RATE), flare.length);
        assertEquals((int) (BodyFireSounds.SIZZLE_SECONDS * RATE), sizzle.length);
        for (float[] s : new float[][] {crackle, flare, sizzle}) {
            assertTrue(AudioSignalAssertions.peak(s) > 0.05, "audible");
            assertTrue(AudioSignalAssertions.peak(PcmAudio.prepare(s.clone())) <= 1.0, "prepared without clipping");
        }
        double crackleBright = brightness(crackle);
        double flareBright = brightness(flare);
        double sizzleBright = brightness(sizzle);
        assertTrue(sizzleBright > crackleBright && crackleBright > flareBright,
                "steam hisses brightest, the crackle snaps, the flare rushes dark: "
                        + sizzleBright + " / " + crackleBright + " / " + flareBright);
    }

    @Test
    void aDeviceWithoutAudioPlaysThemAsSilence() {
        AudioManager silent = new AudioManager();
        silent.playBodyCrackle(1f, 2f, 3f, 1f);
        silent.playBodyFlare(1f, 2f, 3f, 1f);
        silent.playBodySizzle(1f, 2f, 3f, Float.NaN);
        assertTrue(!silent.isEnabled());
    }

    /** High band energy over low band energy. */
    private static double brightness(float[] s) {
        return AudioSignalAssertions.band(s, RATE, 2000, 10000) / Math.max(1e-12, AudioSignalAssertions.band(s, RATE, 100, 1000));
    }
}
