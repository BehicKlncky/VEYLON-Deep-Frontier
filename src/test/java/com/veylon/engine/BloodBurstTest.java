package com.veylon.engine;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The death burst has to be visible and it has to be bounded, and the graphics
 * density setting has to be able to switch it off completely.
 *
 * <p>The pool is 4,000 slots with rain reserving up to 2,400 and its splashes
 * up to 2,800, deliberately leaving at least 1,200 for combat and fire. Blood
 * has to fit inside that reservation even when everything dies at once during a
 * storm.
 */
class BloodBurstTest {

    private static ParticleSystem particles() {
        ParticleSystem p = new ParticleSystem();
        p.setRandomSeed(20260918L);
        return p;
    }

    @Test
    void oneDeathBurstStaysWithinItsStatedCountAtFullDensity() {
        ParticleSystem p = particles();
        p.density = 1f;
        p.bloodBurst(0, 64, 0, 0, 1, 0, 1f);

        assertTrue(p.count > 0, "a death must actually throw blood");
        assertTrue(p.count <= ParticleSystem.BURST_DROPS + ParticleSystem.BURST_MIST,
                "one burst emitted " + p.count + " particles, over its stated bound of "
                        + (ParticleSystem.BURST_DROPS + ParticleSystem.BURST_MIST));
    }

    @Test
    void aBiggerBodyThrowsMoreBloodButNeverMoreThanTheBound() {
        ParticleSystem small = particles();
        small.density = 1f;
        small.bloodBurst(0, 64, 0, 0, 1, 0, 0.4f);

        ParticleSystem large = particles();
        large.density = 1f;
        large.bloodBurst(0, 64, 0, 0, 1, 0, 1.5f);

        assertTrue(large.count > small.count,
                "a thornhorn must not spray the same as a hare");
        assertTrue(large.count <= ParticleSystem.BURST_DROPS + ParticleSystem.BURST_MIST,
                "even the largest body stays inside the burst bound");
    }

    @Test
    void aDeathBurstEmitsNothingAtZeroDensity() {
        ParticleSystem p = particles();
        p.density = 0f;
        p.bloodBurst(0, 64, 0, 0, 1, 0, 1.5f);
        p.bloodDrip(0, 64, 0, 0, -4f, 0);
        assertEquals(0, p.count,
                "particleDensity = 0 must disable emission, not merely reduce it");
    }

    @Test
    void halfDensityEmitsFewerParticlesThanFull() {
        ParticleSystem half = particles();
        half.density = 0.5f;
        half.bloodBurst(0, 64, 0, 0, 1, 0, 1f);

        ParticleSystem full = particles();
        full.density = 1f;
        full.bloodBurst(0, 64, 0, 0, 1, 0, 1f);

        assertTrue(half.count < full.count,
                "the density setting must actually thin the burst");
    }

    @Test
    void bloodStopsAtItsCeilingRatherThanCrowdingAFullPool() {
        ParticleSystem p = particles();
        p.density = 1f;
        // Fill the pool past the blood ceiling the way a storm does.
        for (int i = 0; i < ParticleSystem.BLOOD_LIMIT + 200; i++) {
            p.spawn(ParticleSystem.KIND_DOT, i % 30, 70, i / 30f,
                    0, 0, 0, 0.5f, 0.6f, 0.7f, 0.02f, 3f, 0f);
        }
        int before = p.count;

        // Far more deaths than the live-body cap could ever produce.
        for (int i = 0; i < 200; i++) {
            p.bloodBurst(i, 64, 0, 0, 1, 0, 1.5f);
            p.bloodDrip(i, 64, 0, 0, -4f, 0);
        }

        assertEquals(before, p.count,
                "blood must stop at its ceiling instead of filling the last slots");
        assertTrue(p.count <= ParticleSystem.MAX);
    }

    @Test
    void theWorstCaseTheBodyCapAllowsFitsInsideTheCombatReserve() {
        ParticleSystem p = particles();
        p.density = 1f;
        // Every one of the 12 allowed bodies bursts and then drips for the whole
        // settle timeout. This is strictly pessimistic — drips live 0.45 s, so
        // they never all coexist — and it still has to fit.
        int bodies = com.veylon.entity.RagdollConstants.MAX_LIVE;
        int dripsPerBody = (int) Math.ceil(
                com.veylon.entity.RagdollConstants.SETTLE_TIMEOUT
                        / com.veylon.entity.RagdollConstants.DRIP_INTERVAL);
        for (int b = 0; b < bodies; b++) {
            p.bloodBurst(b, 64, 0, 0, 1, 0, 1.5f);
            for (int d = 0; d < dripsPerBody; d++) {
                p.bloodDrip(b, 64, 0, 0, -4f, 0);
            }
        }

        int reserve = ParticleSystem.MAX - ParticleSystem.SPLASH_LIMIT;
        assertTrue(p.count <= reserve,
                "a full field of bodies emitted " + p.count + " particles, over the "
                        + reserve + " slots weather leaves for combat and fire");
    }
}
