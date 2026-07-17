package com.veylon.engine;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExplosionPresentationTest {

    @Test
    void blastUsesLayeredBoundedParticlesAndDensityCanDisableItCompletely() {
        ParticleSystem full = new ParticleSystem();
        full.setRandomSeed(20260716L);
        full.density = 1f;
        full.explosion(2f, 40f, -3f, 4f);
        int fullCount = full.count;
        assertTrue(fullCount > 40 && fullCount < 120,
                "one blast is readable but restrained");

        Set<Byte> kinds = new HashSet<>();
        for (int i = 0; i < full.count; i++) {
            kinds.add(full.kind[i]);
        }
        assertTrue(kinds.contains(ParticleSystem.KIND_SPARK), "flash, sparks and embers");
        assertTrue(kinds.contains(ParticleSystem.KIND_DOT), "solid debris fragments");
        assertTrue(kinds.contains(ParticleSystem.KIND_PUFF), "dust and smoke");

        ParticleSystem reduced = new ParticleSystem();
        reduced.setRandomSeed(20260716L);
        reduced.density = 0.25f;
        reduced.explosion(2f, 40f, -3f, 4f);
        assertTrue(reduced.count > 0 && reduced.count < fullCount,
                "particle-density setting scales the same production emitter");

        ParticleSystem disabled = new ParticleSystem();
        disabled.setRandomSeed(20260716L);
        disabled.density = 0f;
        disabled.explosion(2f, 40f, -3f, 4f);
        assertEquals(0, disabled.count,
                "zero density produces no hidden minimum particle cost");

        for (int blast = 0; blast < 200; blast++) {
            full.explosion(blast, 40f, 0f, 8f);
        }
        assertEquals(ParticleSystem.MAX, full.count,
                "simultaneous blast presentation stops exactly at the hard cap");
        full.update(10f);
        assertEquals(0, full.count, "all explosion presentation state expires");
    }
}
