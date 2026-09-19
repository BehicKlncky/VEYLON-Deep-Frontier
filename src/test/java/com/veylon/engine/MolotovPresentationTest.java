package com.veylon.engine;

import com.veylon.Game;
import com.veylon.HeadlessGames;
import com.veylon.combat.WeaponRegistry;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A fire bomb has to look like a molotov and never like a bomb: glass and a
 * splash of burning droplets when it breaks, low yellow-orange flames and a
 * glowing sheet over the pool, steam when the rain puts it out, and no
 * fireball, debris, camera shake or blast sound anywhere.
 *
 * <p>All of it is presentation, so it has to be bounded like the rest of the
 * pool of 4,000 particles: every emitter goes through the density setting and
 * stops at the splash ceiling, leaving the slots above it to blood and blasts.
 */
class MolotovPresentationTest {

    /** The smallest core spark an explosion throws; a bottle's flash stays under it. */
    private static final float BLAST_CORE_MIN_SIZE = 0.9f;
    /** Explosion debris is darker than this; molotov glass is far paler. */
    private static final float DARK_DEBRIS_MAX = 0.4f;
    /** Anything spark-shaped at least this big is the bottle's flash, not a droplet. */
    private static final float FLASH_MIN_SIZE = 0.4f;
    private static final int SHATTER_MAX = ParticleSystem.SHATTER_GLASS
            + ParticleSystem.SHATTER_DROPS + ParticleSystem.SHATTER_FLASH;

    private static ParticleSystem particles(long seed, float density) {
        ParticleSystem p = new ParticleSystem();
        p.setRandomSeed(seed);
        p.density = density;
        return p;
    }

    /** Seeds far enough apart that java.util.Random's first draws differ. */
    private static long seed(int i) {
        return 20260919L + i * 0x9E3779B97F4A7C15L;
    }

    @Test
    void shatterEmitsGlassAndBurningDropletsButNoExplosionDebris() {
        for (int run = 0; run < 24; run++) {
            ParticleSystem p = particles(seed(run), 1f);
            // Thrown towards +x+z; the direction is deliberately not normalised.
            p.molotovShatter(0f, 64f, 0f, 3f, 4f);

            int glass = 0, drops = 0, flash = 0;
            float along = 0f;
            for (int i = 0; i < p.count; i++) {
                String at = "run " + run + " particle " + i;
                assertTrue(p.kind[i] != ParticleSystem.KIND_PUFF, "no dust or smoke column: " + at);
                assertTrue(p.kind[i] != ParticleSystem.KIND_STREAK, at);
                if (p.kind[i] == ParticleSystem.KIND_DOT) {
                    glass++;
                    assertTrue(p.cr[i] > DARK_DEBRIS_MAX, "no dark blast debris: " + at);
                    assertInRange(p.cr[i], 0.70f, 0.80f, "glass is pale: " + at);
                    assertInRange(p.cg[i], 0.80f, 0.90f, "glass is pale green: " + at);
                    assertInRange(p.cb[i], 0.75f, 0.85f, at);
                    assertInRange(p.size[i], 0.04f, 0.07f, "glass shards are small: " + at);
                } else if (p.size[i] >= FLASH_MIN_SIZE) {
                    flash++;
                    assertTrue(p.size[i] < BLAST_CORE_MIN_SIZE,
                            "the flash is smaller than a blast's core: " + at);
                } else {
                    drops++;
                    assertEquals(1f, p.cr[i], 0f, at);
                    assertInRange(p.cg[i], 0.55f, 0.92f, "droplets burn yellow to orange: " + at);
                    assertInRange(p.cb[i], 0.08f, 0.45f, at);
                    assertInRange(p.size[i], 0.10f, 0.18f, at);
                    assertInRange(p.velocityY(i), 2.5f, 5f, "droplets are thrown up to arc: " + at);
                    along += p.velocityX(i) * 0.6f + p.velocityZ(i) * 0.8f;
                }
            }
            assertInRange(glass, 6, 10, "glass shards in run " + run);
            assertInRange(drops, 14, 22, "burning droplets in run " + run);
            assertInRange(flash, 2, 3, "flash sparks in run " + run);
            assertTrue(p.count <= SHATTER_MAX, "one bottle stays inside its stated bound");
            assertTrue(along / drops > 1.2f,
                    "the splash runs on the way the bottle flew: mean " + along / drops + " m/s");

            // The flash is gone within 0.12 s; glass and droplets are still in the air.
            p.update(0.11f);
            assertEquals(glass + drops, p.count, "only the flash expires that fast");
            for (int i = 0; i < p.count; i++) {
                assertTrue(p.size[i] < FLASH_MIN_SIZE, "the flash lasts at most 0.12 s");
            }
        }
    }

    @Test
    void liquidFlameColourStaysInTheYellowOrangeBand() {
        float[] intensities = {0.5f, 0.75f, 1f, 1.5f};
        float[] lives = {0f, 0.3f, 1f, 2f, Float.NaN};
        for (float intensity : intensities) {
            for (float life : lives) {
                ParticleSystem p = particles(seed(Float.floatToIntBits(intensity + life)), 1f);
                for (int n = 0; n < 200; n++) {
                    p.liquidFlame(10.5f, 40f, -3.5f, intensity, life);
                }
                assertEquals(200, p.count, "every call at full density is one tongue of flame");
                for (int i = 0; i < p.count; i++) {
                    String at = "intensity " + intensity + " life " + life + ": particle " + i;
                    assertEquals(ParticleSystem.KIND_SPARK, p.kind[i], "flames are additive " + at);
                    assertEquals(1f, p.cr[i], 0f, at);
                    assertInRange(p.cg[i], 0.45f, 0.92f, "yellow to orange, never white " + at);
                    assertTrue(p.cb[i] >= 0f && p.cb[i] <= 0.45f, "never white or blue " + at);
                    assertInRange(p.size[i], 0.25f * Math.min(1f, intensity) - 1e-6f, 0.45f, at);
                    assertInRange(p.px[i], 10f, 11f, "inside the cell's footprint " + at);
                    assertInRange(p.pz[i], -4f, -3f, "inside the cell's footprint " + at);
                    assertInRange(p.py[i], 40f, 40.2f, "low, off the pool's surface " + at);
                    assertInRange(p.velocityY(i), 0.6f, 1.3f, "rising " + at);
                }
            }
        }

        // Fresh liquid at the centre is yellower than the rim burning down.
        assertTrue(meanGreen(1f, 1f) > meanGreen(0.5f, 0f) + 0.2f,
                "the centre of a fresh spill burns yellow, a dying rim deep orange");
        ParticleSystem none = particles(seed(1), 1f);
        none.liquidFlame(0f, 40f, 0f, 0f, 1f);
        none.liquidFlame(0f, 40f, 0f, Float.NaN, 1f);
        assertEquals(0, none.count, "no liquid, no flame");
    }

    @Test
    void presentationRespectsDensityAndParticleCaps() {
        ParticleSystem full = particles(seed(2), 1f);
        emitOnePool(full);
        ParticleSystem reduced = particles(seed(2), 0.25f);
        emitOnePool(reduced);
        assertTrue(reduced.count > 0 && reduced.count < full.count,
                "0.25 density thins the same emitters: " + reduced.count + " of " + full.count);
        assertAllFinite(reduced, "at a quarter density");

        ParticleSystem off = particles(seed(2), 0f);
        emitOnePool(off);
        off.molotovShatter(0f, 64f, 0f, 0f, 0f);
        assertEquals(0, off.count, "particles Off emits nothing, with no hidden minimum");

        // Degenerate inputs never produce NaN at the lowest density that still emits.
        ParticleSystem odd = particles(seed(3), 0.25f);
        for (int i = 0; i < 40; i++) {
            odd.molotovShatter(0f, 64f, 0f, 0f, 0f);
            odd.molotovShatter(0f, 64f, 0f, Float.NaN, 1f);
            odd.liquidFlame(0.5f, 64f, 0.5f, 1f, Float.NaN);
            odd.steamPuff(0.5f, 64f, 0.5f);
        }
        assertTrue(odd.count > 0);
        assertAllFinite(odd, "for a still bottle and a pool of unknown age");

        // A pool of other effects just under the splash ceiling: molotov
        // presentation takes what is left below it and never a slot above.
        ParticleSystem busy = particles(seed(4), 1f);
        fill(busy, ParticleSystem.SPLASH_LIMIT - 5);
        for (int i = 0; i < 50; i++) {
            emitOnePool(busy);
        }
        assertEquals(ParticleSystem.SPLASH_LIMIT, busy.count,
                "shatter, flames and steam stop exactly at the splash ceiling");

        // A saturated pool is never overrun.
        ParticleSystem saturated = particles(seed(5), 1f);
        fill(saturated, ParticleSystem.MAX);
        for (int i = 0; i < 50; i++) {
            emitOnePool(saturated);
        }
        assertEquals(ParticleSystem.MAX, saturated.count, "never past the hard cap");

        full.update(10f);
        assertEquals(0, full.count, "all molotov presentation expires");
    }

    @Test
    void aMolotovImpactNeverShakesTheCameraOrPlaysAnExplosion() {
        SoundProbe sounds = new SoundProbe();
        Game g = HeadlessGames.withAudio(sounds);
        g.newWorld(777L, true);
        flatten(g);
        g.player.pos.set(310, 40.1f, 310);
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        g.projectiles.setRandomSeed(23L);
        g.particles.setRandomSeed(41L);
        g.particles.density = 1f;
        g.particles.count = 0;

        assertEquals(1, g.projectiles.fire(g, g.player, true, 330.5f, 42.5f, 330.5f, 1f, -0.6f, 0f,
                WeaponRegistry.byId("fire_bomb"), null));
        for (float t = 0; g.projectiles.liveCount() > 0 && t < 3f; t += 0.02f) {
            g.projectiles.update(g, 0.02f);
        }

        assertEquals(0, g.projectiles.liveCount(), "the bottle broke");
        assertTrue(g.liquidFire.count() > 0, "and spilled burning liquid");
        assertEquals(0f, g.renderer.pendingShake(), 0f, "a breaking bottle never shakes the camera");
        assertEquals(0, sounds.explosions, "and never sounds like a blast");
        assertEquals(1, sounds.cracks, "the glass cracks once");
        assertEquals(1, sounds.whooshes, "the liquid catches once");
        assertEquals(0, g.noise.countCategory("explosion"), "no blast went off");

        int glass = 0, droplets = 0;
        for (int i = 0; i < g.particles.count; i++) {
            ParticleSystem p = g.particles;
            assertTrue(p.kind[i] != ParticleSystem.KIND_PUFF, "no dust or smoke column");
            if (p.kind[i] == ParticleSystem.KIND_DOT) {
                assertTrue(p.cr[i] > DARK_DEBRIS_MAX, "no dark blast debris");
                glass++;
            } else if (p.kind[i] == ParticleSystem.KIND_SPARK) {
                assertTrue(p.size[i] < BLAST_CORE_MIN_SIZE, "no blast core flash");
                if (p.velocityY(i) >= 2f && p.cb[i] >= 0.08f && p.size[i] >= 0.10f
                        && p.size[i] <= 0.18f) {
                    droplets++;
                }
            }
        }
        assertTrue(glass >= 6, "glass flies where it broke: " + glass);
        assertTrue(droplets >= 14, "burning droplets splash out: " + droplets);
    }

    @Test
    void aBottleInFlightTrailsItsBurningRagAndAScrapBombDoesNot() {
        Game g = new Game();
        g.newWorld(777L, true);
        flatten(g);
        g.player.pos.set(310, 40.1f, 310);
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        g.projectiles.setRandomSeed(23L);
        g.particles.setRandomSeed(43L);
        g.particles.density = 1f;

        g.particles.count = 0;
        g.projectiles.fire(g, g.player, true, 300.5f, 44f, 330.5f, 1f, 0.3f, 0f,
                WeaponRegistry.byId("fire_bomb"), null);
        for (int step = 0; step < 15; step++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(1, g.projectiles.liveCount(), "still in the air after 0.3 s");
        int flames = 0;
        for (int i = 0; i < g.particles.count; i++) {
            if (g.particles.kind[i] == ParticleSystem.KIND_SPARK) {
                flames++;
                // The throw's 3-degree cone plus the flame's own jitter.
                assertInRange(g.particles.pz[i], 329.7f, 331.3f, "shed along the bottle's path");
            }
        }
        assertInRange(flames, 2, 4, "about one rag flame every 0.1 s");

        g.projectiles.reset();
        g.particles.count = 0;
        g.projectiles.fire(g, g.player, true, 300.5f, 44f, 330.5f, 1f, 0.3f, 0f,
                WeaponRegistry.byId("scrap_bomb"), null);
        for (int step = 0; step < 15; step++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(0, g.particles.count, "a scrap bomb has no rag to burn");
    }

    @Test
    void theSheetShrinksAwayOverItsLastSecondsAndAsTheRainSoaksIt() {
        assertEquals(1f, Renderer.liquidSheetFade(9f, 0f), 0f, "a burning pool is a whole sheet");
        assertEquals(1f, Renderer.liquidSheetFade(Renderer.SHEET_FADE_SECONDS, 0f), 0f);
        assertEquals(0.5f, Renderer.liquidSheetFade(Renderer.SHEET_FADE_SECONDS * 0.5f, 0f), 1e-6f);
        assertEquals(0.5f, Renderer.liquidSheetFade(9f, 0.5f), 1e-6f,
                "half soaked, half gone");
        assertEquals(0f, Renderer.liquidSheetFade(9f, 1f), 0f, "soaked through, out");
        assertEquals(0f, Renderer.liquidSheetFade(0f, 0f), 0f);
        assertEquals(0f, Renderer.liquidSheetFade(-1f, 0f), 0f);
        assertEquals(0f, Renderer.liquidSheetFade(Float.NaN, 0f), 0f, "never NaN");
        assertEquals(1f, Renderer.liquidSheetFade(9f, Float.NaN), 0f, "never NaN");
        float previous = 1f;
        for (float left = 3f; left > 0f; left -= 0.05f) {
            float fade = Renderer.liquidSheetFade(left, 0f);
            assertTrue(fade <= previous, "it only ever shrinks as it burns down");
            previous = fade;
        }
    }

    @Test
    void theSheetAndItsFlamesCoolFromTheCentreAndAsThePoolBurnsDown() {
        assertEquals(1f, ParticleSystem.liquidHeat(1f, 1f), 0f, "fresh centre is hottest");
        assertEquals(0.5f, ParticleSystem.liquidHeat(0.5f, 1f), 1e-6f, "the rim is cooler");
        assertEquals(0.5f, ParticleSystem.liquidHeat(1f, 0f), 1e-6f, "so is liquid burning out");
        assertEquals(1f, ParticleSystem.liquidHeat(3f, 7f), 0f, "never past fully hot");
        assertEquals(0f, ParticleSystem.liquidHeat(Float.NaN, 1f), 0f, "never NaN");
        assertEquals(0.5f, ParticleSystem.liquidHeat(1f, Float.NaN), 1e-6f, "never NaN");
        assertTrue(ParticleSystem.liquidHeat(0.75f, 0.5f) < ParticleSystem.liquidHeat(0.8f, 0.5f));
        assertTrue(ParticleSystem.liquidHeat(0.8f, 0.4f) < ParticleSystem.liquidHeat(0.8f, 0.5f));
    }

    /** One bottle's worth of presentation: its burst, a flame per patch and a breath of steam. */
    private static void emitOnePool(ParticleSystem p) {
        p.molotovShatter(0f, 64f, 0f, 1f, 0f);
        for (int patch = 0; patch < 22; patch++) {
            p.liquidFlame(patch + 0.5f, 64f, 0.5f, 1f - patch / 44f, 0.8f);
        }
        p.steamPuff(0.5f, 64f, 0.5f);
    }

    private static float meanGreen(float intensity, float life) {
        ParticleSystem p = particles(seed(9), 1f);
        for (int n = 0; n < 200; n++) {
            p.liquidFlame(0.5f, 40f, 0.5f, intensity, life);
        }
        float sum = 0f;
        for (int i = 0; i < p.count; i++) {
            sum += p.cg[i];
        }
        return sum / p.count;
    }

    private static void fill(ParticleSystem p, int upTo) {
        while (p.count < upTo) {
            p.spawn(ParticleSystem.KIND_DOT, p.count % 30, 70f, p.count / 30f,
                    0f, 0f, 0f, 0.5f, 0.6f, 0.7f, 0.02f, 3f, 0f);
        }
    }

    private static void assertAllFinite(ParticleSystem p, String when) {
        for (int i = 0; i < p.count; i++) {
            assertTrue(Float.isFinite(p.px[i]) && Float.isFinite(p.py[i]) && Float.isFinite(p.pz[i])
                    && Float.isFinite(p.velocityX(i)) && Float.isFinite(p.velocityY(i))
                    && Float.isFinite(p.velocityZ(i)) && Float.isFinite(p.size[i])
                    && Float.isFinite(p.cr[i]) && Float.isFinite(p.cg[i]) && Float.isFinite(p.cb[i]),
                    "particle " + i + " is not finite " + when);
        }
    }

    private static void assertInRange(float v, float lo, float hi, String what) {
        assertTrue(v >= lo && v <= hi, what + ": " + v + " outside " + lo + ".." + hi);
    }

    /** CombatSystemsTest's arena: four chunks of stone up to y = 39 and air above. */
    private static void flatten(Game g) {
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
    }

    /** Counts the sounds a shatter asks for; no audio device is involved. */
    private static final class SoundProbe extends AudioManager {
        int explosions, cracks, whooshes;

        @Override
        public void playExplosion(float x, float y, float z) {
            explosions++;
        }

        @Override
        public void playBulletImpact(float x, float y, float z) {
            cracks++;
        }

        @Override
        public void playFuse(float x, float y, float z) {
            whooshes++;
        }
    }
}
