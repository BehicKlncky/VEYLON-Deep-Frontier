package com.veylon;

import com.veylon.engine.ParticleSystem;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ambient emitter is what makes a pool of burning liquid read as fire
 * between the bottle breaking and the pool going out: flames, embers and
 * smoke over every patch near the player, steam instead once the rain is
 * soaking it, and all of it bounded by range and by the splash ceiling.
 *
 * <p>CombatSystemsTest's flat stone arena, floor top at y = 39. Nothing else
 * in it emits: no campfire, no block fire, clear weather and a warm player,
 * so every particle counted here came off the liquid.
 */
class LiquidFireEmitterTest {

    /** One emitter pass. */
    private static final float PASS = 0.12f;
    /** Liquid flames are wider than this; an ember never is. */
    private static final float EMBER_MAX_SIZE = 0.1f;
    /** Steam is paler than this; smoke never is. */
    private static final float STEAM_MIN_SHADE = 0.8f;

    private Game g;

    @BeforeEach
    void setUp() {
        g = new Game();
        g.newWorld(777L, true);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = g.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        g.player.pos.set(310, 40.1f, 310);
        g.player.envTemp = 20f;
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        g.world.campfireFuel.clear();
        g.fire.reset();
        g.liquidFire.setRandomSeed(29L);
        g.particles.setRandomSeed(47L);
        g.particles.density = 1f;
        g.ambience.reseed(53L);
        setWeather(Weather.CLEAR);
    }

    @Test
    void aBurningPoolGivesOffOneOrTwoFlamesPerPatchWithEmbersAndSmoke() {
        int patches = g.liquidFire.spill(g, 325.5f, 40.5f, 310.5f, 1f, 0f, true);
        assertEquals(22, patches);
        g.particles.count = 0;

        g.ambience.updateEmitters(PASS);
        int flames = 0;
        for (int i = 0; i < g.particles.count; i++) {
            if (g.particles.kind[i] == ParticleSystem.KIND_SPARK && g.particles.size[i] > EMBER_MAX_SIZE) {
                flames++;
                assertEquals(1f, g.particles.cr[i], 0f);
                assertTrue(g.particles.cg[i] >= 0.45f && g.particles.cg[i] <= 0.92f,
                        "liquid flames burn yellow to orange: g " + g.particles.cg[i]);
                assertTrue(g.particles.py[i] < 40.3f, "low, off the pool's surface");
            }
        }
        assertTrue(flames >= patches && flames <= 2 * patches,
                "one or two flames per patch per pass: " + flames);

        for (int pass = 0; pass < 10; pass++) {
            g.ambience.updateEmitters(PASS);
        }
        int embers = 0, smoke = 0, steam = 0;
        for (int i = 0; i < g.particles.count; i++) {
            byte kind = g.particles.kind[i];
            if (kind == ParticleSystem.KIND_SPARK && g.particles.size[i] <= EMBER_MAX_SIZE) {
                embers++;
            } else if (kind == ParticleSystem.KIND_PUFF) {
                if (g.particles.cr[i] >= STEAM_MIN_SHADE) {
                    steam++;
                } else {
                    smoke++;
                }
            }
        }
        assertTrue(embers > 0, "embers rise off the pool");
        assertTrue(smoke > 0, "and dark smoke above it");
        assertEquals(0, steam, "a dry pool does not steam");
    }

    @Test
    void aPoolTheRainIsSoakingSteamsInsteadOfBurningAndGoesOutInSteam() {
        int patches = g.liquidFire.spill(g, 325.5f, 40.5f, 310.5f, 1f, 0f, true);
        setWeather(Weather.RAIN);
        g.liquidFire.mediumTick(g, SimulationScheduler.MEDIUM_DT);
        assertEquals(patches, g.liquidFire.count(), "soaking, not out yet");
        g.particles.count = 0;

        for (int pass = 0; pass < 4; pass++) {
            g.ambience.updateEmitters(PASS);
        }
        int steam = 0;
        for (int i = 0; i < g.particles.count; i++) {
            assertTrue(g.particles.kind[i] != ParticleSystem.KIND_SPARK,
                    "a pool the rain is soaking gives off no flame or ember");
            if (g.particles.kind[i] == ParticleSystem.KIND_PUFF) {
                assertTrue(g.particles.cr[i] >= STEAM_MIN_SHADE, "only light steam, no smoke");
                steam++;
            }
        }
        assertTrue(steam > 0, "it hisses into steam");

        int before = g.particles.count;
        g.liquidFire.mediumTick(g, SimulationScheduler.MEDIUM_DT);
        assertEquals(0, g.liquidFire.count(), "the rain has put the pool out");
        int puffs = 0;
        for (int i = before; i < g.particles.count; i++) {
            assertEquals(ParticleSystem.KIND_PUFF, g.particles.kind[i]);
            assertTrue(g.particles.cr[i] >= STEAM_MIN_SHADE, "a patch goes out in steam, not smoke");
            puffs++;
        }
        assertEquals(patches * ParticleSystem.STEAM_PUFFS, puffs, "one breath of steam per patch");
    }

    @Test
    void poolsOutOfRangeEmitNothingAndNoPassCrossesTheSplashCeiling() {
        g.player.pos.set(290.5f, 40.1f, 290.5f);
        g.liquidFire.spill(g, 345.5f, 40.5f, 345.5f, 1f, 0f, true);
        g.particles.count = 0;
        for (int pass = 0; pass < 5; pass++) {
            g.ambience.updateEmitters(PASS);
        }
        assertEquals(0, g.particles.count, "a pool 77 blocks away costs no particles");

        g.player.pos.set(335.5f, 40.1f, 345.5f);
        while (g.particles.count < ParticleSystem.SPLASH_LIMIT - 3) {
            g.particles.spawn(ParticleSystem.KIND_DOT, 300f, 70f, 300f,
                    0f, 0f, 0f, 0.5f, 0.6f, 0.7f, 0.02f, 3f, 0f);
        }
        for (int pass = 0; pass < 5; pass++) {
            g.ambience.updateEmitters(PASS);
        }
        assertEquals(ParticleSystem.SPLASH_LIMIT, g.particles.count,
                "the pool takes what is left below the splash ceiling and nothing above it");
    }

    private void setWeather(Weather w) {
        g.weather.current = w;
        g.weather.next = w;
        g.weather.blend = 1f;
        g.weather.changeTimer = 4000f;
    }
}
