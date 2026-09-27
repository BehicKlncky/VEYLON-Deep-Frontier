package com.veylon.gfx;

import com.veylon.BodyFireArena;
import com.veylon.Game;
import com.veylon.entity.BurnResidue;
import com.veylon.entity.CombustionConstants;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Npc;
import com.veylon.entity.Ragdoll;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every stage of a body's fire, as the simulation reports it, looks like
 * that stage: catching swells and climbs from the touch, burning is full and
 * then shrinking and smokier as the fuel runs down, rain subdues and steams,
 * putting out tapers — quickly with steam for water, slowly with smoke for a
 * fire that burned out — and a body that died alight carries on as its
 * remains without a jump. Scorch only grows, and a new life starts clean.
 */
class BodyFireLookTest {

    private static final float TICK = 0.05f;

    @Test
    void anUnburnedBodyShowsNothing() {
        Game game = BodyFireArena.arena();
        Npc n = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        BodyFireLook look = new BodyFireLook().living(n);
        assertFalse(look.active());
        assertEquals(0f, look.scorch, 0f);
        assertEquals(0f, look.light, 0f);
        assertFalse(new BodyFireLook().remains(null).active());
    }

    @Test
    void catchingSwellsAndClimbsFromTheTouchCarriedWithTheBody() {
        Game game = BodyFireArena.arena();
        Npc n = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        BodyFireArena.ignite(game, n, 1);
        BodyFireArena.burn(game, TICK);
        BodyFireLook look = new BodyFireLook().living(n);
        assertTrue(look.flame > 0.9f, "full flames at once: " + look.flame);
        assertTrue(look.flare > 0.8f, "swelling as it catches: " + look.flare);
        assertTrue(look.spreading, "still climbing from where it touched");
        assertEquals(n.pos.y + 0.2f, look.touchY, 1e-4f, "from the touch, low on the body");
        float reach = look.spread;
        assertTrue(reach < n.height, "not yet over the whole body: " + reach);

        // Run two metres: the touch point goes with the body, not left behind in the world.
        n.pos.x += 2f;
        look.living(n);
        assertEquals(n.pos.x, look.touchX, 1e-4f, "the touch point moved with the body");

        BodyFireArena.burn(game, 1f);
        look.living(n);
        assertFalse(look.spreading, "a second later the whole body burns");
        assertEquals(0f, look.flare, 0f, "and the swell is over");
        assertEquals(1f, look.coverage, 1e-6f);
        assertEquals(BodyFireLook.SMOKE_STRONG, look.smoke, 1e-6f, "strong flames smoke least");
    }

    @Test
    void asTheFuelRunsDownTheFlamesShrinkAndTheSmokeThickens() {
        Game game = BodyFireArena.arena();
        Npc n = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        n.maxHealth = n.health = 500f;
        BodyFireArena.ignite(game, n, 1);
        BodyFireArena.burn(game, 1f);
        BodyFireLook look = new BodyFireLook();
        float lastFlame = 2f, lastCoverage = 2f, lastSmoke = -1f, lastScorch = -1f;
        while (n.combustion.burning()) {
            look.living(n);
            assertTrue(look.flame <= lastFlame + 1e-6f, "never flaring up again");
            assertTrue(look.coverage <= lastCoverage + 1e-6f);
            assertTrue(look.smoke >= lastSmoke - 1e-6f, "smoke only thickens as the flames weaken");
            assertTrue(look.scorch >= lastScorch, "scorch only grows");
            assertEquals(0f, look.steam, 0f, "no steam from a dry fire");
            lastFlame = look.flame;
            lastCoverage = look.coverage;
            lastSmoke = look.smoke;
            lastScorch = look.scorch;
            BodyFireArena.burn(game, TICK);
        }
        assertEquals(CombustionConstants.MIN_INTENSITY, lastFlame, 0.03f, "it burned down to its weakest");
        assertEquals(BodyFireLook.MIN_COVERAGE, lastCoverage, 0.03f, "on a third of the body");
        assertEquals(BodyFireLook.SMOKE_WEAK, lastSmoke, 0.05f, "with the most smoke");
    }

    @Test
    void aFireThatBurnsOutDiesDownThenLeavesAWispOfSmokeAndNoSteam() {
        Game game = BodyFireArena.arena();
        Creature deer = BodyFireArena.creature(game, CreatureType.DEER, 310.5f, 305.5f);
        deer.maxHealth = deer.health = 500f;
        BodyFireArena.ignite(game, deer, 1);
        while (!(game.combustion.totalBurnouts > 0)) {
            BodyFireArena.burn(game, TICK);
        }
        BodyFireLook look = new BodyFireLook().living(deer);
        assertFalse(deer.combustion.burning());
        assertFalse(deer.combustion.outDoused());
        assertTrue(look.flame > 0f, "the flames are still dying down the moment it goes out");
        assertEquals(0f, look.steam, 0f, "a fire that burned out does not steam");
        BodyFireArena.burn(game, BodyFireLook.TAPER_SECONDS);
        look.living(deer);
        assertEquals(0f, look.flame, 0f, "gone within the taper");
        assertTrue(look.smoke > 0f, "a wisp of smoke goes on");
        BodyFireArena.burn(game, BodyFireLook.WISP_SECONDS);
        assertFalse(look.living(deer).active(), "and then nothing but the scorch");
        assertTrue(look.scorch > 0f);
    }

    @Test
    void waterPutsTheFlamesOutQuicklyAndSteams() {
        Game game = BodyFireArena.arena();
        Creature wolf = BodyFireArena.creature(game, CreatureType.WOLF, 310.5f, 305.5f);
        wolf.maxHealth = wolf.health = 500f;
        BodyFireArena.ignite(game, wolf, 1);
        BodyFireArena.burn(game, 1f);
        float burningFlame = new BodyFireLook().living(wolf).flame;
        game.world.setBlock(310, 40, 305, BlockType.WATER, false);
        BodyFireArena.burn(game, TICK);
        assertFalse(wolf.combustion.burning(), "an animal in water is put out at once");
        BodyFireLook look = new BodyFireLook().living(wolf);
        assertTrue(wolf.combustion.outDoused());
        assertEquals(burningFlame, wolf.combustion.outIntensity(), 1e-6f, "it went out at the strength it burned");
        assertTrue(look.steam > 0.9f, "a breath of steam: " + look.steam);
        BodyFireArena.burn(game, BodyFireLook.DOUSED_TAPER_SECONDS);
        look.living(wolf);
        assertEquals(0f, look.flame, 0f, "doused flames die faster than burned-out ones");
        assertTrue(look.steam > 0f, "the steam outlasts them");
        BodyFireArena.burn(game, BodyFireLook.STEAM_SECONDS);
        assertEquals(0f, look.living(wolf).steam, 0f);
    }

    @Test
    void openRainSubduesAndSteamsTheFlamesAsItBuildsTowardsPuttingThemOut() {
        Game game = BodyFireArena.arena();
        Npc n = BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 305.5f);
        n.maxHealth = n.health = 500f;
        BodyFireArena.ignite(game, n, 1);
        BodyFireArena.burn(game, 1f);
        BodyFireLook dry = new BodyFireLook().living(n);
        BodyFireArena.weather(game, Weather.RAIN);
        BodyFireArena.burn(game, 1f);
        assertTrue(n.combustion.burning(), "not out yet");
        assertTrue(n.combustion.soak() > 0f, "but soaking");
        BodyFireLook wet = new BodyFireLook().living(n);
        assertTrue(wet.steam > 0.5f, "the rain on the flames steams: " + wet.steam);
        assertTrue(wet.flame < dry.flame * 0.8f, "and holds them down: " + wet.flame + " vs " + dry.flame);
        BodyFireArena.burn(game, 0.6f);
        assertFalse(n.combustion.burning(), "the rain puts them out");
        BodyFireLook out = new BodyFireLook().living(n);
        assertTrue(out.steam > 0.5f, "in steam");
        assertTrue(n.combustion.outDoused());
    }

    @Test
    void aBodyThatDiesAlightBurnsOnAsItsRemainsWithoutAJump() {
        Game game = BodyFireArena.arena();
        Creature hare = BodyFireArena.creature(game, CreatureType.HARE, 310.5f, 305.5f);
        BodyFireArena.ignite(game, hare, 1);
        BodyFireLook living = new BodyFireLook();
        while (!hare.dead) {
            living.living(hare);
            BodyFireArena.burn(game, TICK);
        }
        game.entities.fastTick(game, TICK);
        assertEquals(1, game.ragdolls.live.size(), "it falls as one body");
        Ragdoll r = game.ragdolls.live.getFirst();
        BurnResidue burn = r.burn;
        assertNotNull(burn);
        BodyFireLook dead = new BodyFireLook().remains(burn);
        assertEquals(living.flame, dead.flame, 0.05f, "the flames go on at the strength the living body showed");
        assertEquals(living.seed, dead.seed, "and flicker the same way");
        assertEquals(living.scorch, dead.scorch, 0.01f, "with the same scorch");
        float atDeath = dead.flame;
        float last = dead.flame;
        for (int i = 0; i < 60; i++) {
            game.burnResidues.update(game, 1 / 30f);
            dead.remains(burn);
            assertTrue(dead.flame <= last + 1e-6f, "the remains' flames only die down");
            last = dead.flame;
        }
        assertTrue(last < atDeath - 0.3f, "and do die down: " + last + " from " + atDeath);
        assertTrue(dead.smoke > 0f, "and smoke as they fall");
        assertSame(burn, r.burn, "the same residue, never a copy");
    }

    @Test
    void aNewLifeStartsWithNoFireScorchOrMemoryOfTheLast() {
        Game game = BodyFireArena.arena();
        BodyFireArena.ignite(game, game.player, 1);
        BodyFireArena.burn(game, 2f);
        assertTrue(new BodyFireLook().living(game.player).scorch > 0f);
        game.combustion.extinguish(game.player);
        assertTrue(game.player.combustion.outSeconds() < 0.1f && game.player.combustion.outDoused());
        game.combustion.clear(game.player);
        assertEquals(0f, game.player.combustion.outIntensity(), 0f);
        assertFalse(game.player.combustion.outDoused(), "no memory of how the last life's fire went out");
        BodyFireLook look = new BodyFireLook().living(game.player);
        assertFalse(look.active(), "no flames, smoke or steam");
        assertEquals(0f, look.scorch, 0f, "and no scorch");
        assertEquals(CombustionConstants.OUT_MEMORY_SECONDS, game.player.combustion.outSeconds(), 0f);
    }

    @Test
    void theRedFlashYieldsToTheFlamesWhileAlightAndStillShowsAFreshHurt() {
        Game game = BodyFireArena.arena();
        BodyFireArena.ignite(game, game.player, 1);
        BodyFireArena.burn(game, 1f);
        // Only the fire runs here, so the contact tick's flash has not faded: let it have.
        game.player.damageFlash = 0f;
        BodyFireArena.burn(game, TICK);
        assertEquals(CombustionConstants.AFTERBURN_FLASH * game.player.combustion.intensity(),
                game.player.damageFlash, 1e-6f, "the afterburn keeps the flash up (unchanged)");
        assertEquals(0f, BodyFireLook.shownDamageFlash(game.player), 1e-6f,
                "but the screen shows the flames for it, not a red frame");
        game.player.damageFlash = 1f;
        assertTrue(BodyFireLook.shownDamageFlash(game.player) > 0.4f, "a fresh hurt still flashes red");
        game.combustion.extinguish(game.player);
        game.player.damageFlash = 0.3f;
        assertEquals(0.3f, BodyFireLook.shownDamageFlash(game.player), 0f, "out of the fire the flash is as it was");
    }
}
