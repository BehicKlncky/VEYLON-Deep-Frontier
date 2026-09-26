package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.PlayerTreatmentSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;

import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_PLAYER;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_PLAYER;
import static com.veylon.entity.CombustionConstants.FADE_SECONDS;
import static com.veylon.entity.CombustionConstants.MIN_INTENSITY;
import static com.veylon.entity.PlayerConstants.BURN_DAMAGE_PER_SECOND;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Body fire inside the real game loop: {@code Game.fastTick}'s order (needs,
 * then fire, then AI, physics and deaths), the scheduler's frame partitions,
 * the medium tick, the death transition, respawn, kill credit and the medical
 * burn injury. Contacts come from the public combustion commands; the
 * production flame sources are connected in milestone 07.
 */
class CombustionIntegrationTest {

    private static final float DT = SimulationScheduler.FAST_DT;
    private static final float FEET = 40f;

    /**
     * Equal simulated time gives equal fires and equal health however the
     * frames split it: every fire advances only on fixed fast ticks. The
     * stated tolerance is 1e-4 health (float noise); in practice it is exact.
     */
    @Test
    void equalTimeUnderDifferentFramePartitionsGivesTheSameFires() {
        // 3.125 s, both ways exact in binary, mid-way between two fast ticks.
        double[] even = new double[400];
        java.util.Arrays.fill(even, 1.0 / 128);
        int[] pattern = {1, 6, 5, 8, 4, 9, 3, 12, 2};
        double[] uneven = new double[pattern.length * 8];
        for (int i = 0; i < uneven.length; i++) {
            uneven[i] = pattern[i % pattern.length] / 128.0;
        }

        Game[] games = {arena(), arena()};
        double[][] partitions = {even, uneven};
        Npc[] people = new Npc[2];
        Creature[] thornhorns = new Creature[2];
        Creature[] hares = new Creature[2];
        for (int i = 0; i < 2; i++) {
            Game g = games[i];
            people[i] = g.entities.spawnNpc(g.world, "Villager", 320.5f, FEET, 320.5f);
            thornhorns[i] = g.entities.spawnCreature(g.world, Creature.CreatureType.THORNHORN,
                    326.5f, FEET, 320.5f);
            hares[i] = g.entities.spawnCreature(g.world, Creature.CreatureType.HARE, 320.5f, FEET, 326.5f);
            g.combustion.expose(people[i], CombustionSource.LIQUID, 1f, true, 1, 320.5f, FEET, 320.5f);
            g.combustion.expose(thornhorns[i], CombustionSource.LIQUID, 0.8f, false, 2, 326.5f, FEET, 320.5f);
            g.combustion.ignite(g, hares[i], CombustionSource.DIRECT_HIT, 1f, true, 3, 320.5f, 40.2f, 326.5f);
            g.combustion.expose(g.player, CombustionSource.LIQUID, 1f, false, 4, 310f, FEET, 310f);
            for (double frame : partitions[i]) {
                g.scheduler.update(frame, g);
            }
        }

        float tolerance = 1e-4f;
        for (int i = 1; i < 2; i++) {
            assertEquals(people[0].health, people[i].health, tolerance);
            assertEquals(people[0].combustion.fuel(), people[i].combustion.fuel(), tolerance);
            assertEquals(people[0].combustion.intensity(), people[i].combustion.intensity(), tolerance);
            assertEquals(people[0].combustion.burnSeconds(), people[i].combustion.burnSeconds(), tolerance);
            assertEquals(thornhorns[0].health, thornhorns[i].health, tolerance);
            assertEquals(thornhorns[0].combustion.fuel(), thornhorns[i].combustion.fuel(), tolerance);
            assertEquals(games[0].player.health, games[i].player.health, tolerance);
            assertEquals(games[0].player.combustion.fuel(), games[i].player.combustion.fuel(), tolerance);
            assertEquals(games[0].player.afflictions.get(Affliction.BURN),
                    games[i].player.afflictions.get(Affliction.BURN));
            assertTrue(hares[0].dead && hares[i].dead, "the hare burned to death in both");
            assertEquals(games[0].combustion.totalIgnitions, games[i].combustion.totalIgnitions);
        }
        assertEquals(62 * DT, people[0].combustion.burnSeconds(), 1e-4f, "62 fast ticks in 3.125 s");
        assertTrue(people[0].combustion.burning(), "and the fires are still going");
    }

    /** No medium or slow tick advances a fire; the fast tick does, and its ignition tick already burns. */
    @Test
    void onlyTheFastTickAdvancesAFireAndItsFirstTickBurnsAtTheContactRate() {
        Game g = arena();
        Npc n = g.entities.spawnNpc(g.world, "Villager", 320.5f, FEET, 320.5f);
        n.maxHealth = n.health = 500f;
        assertTrue(g.combustion.ignite(g, n, CombustionSource.DIRECT_HIT, 1f, true, 0, 320.5f, 41f, 320.5f));
        assertTrue(g.combustion.isBurning(n), "a direct hit lights the body in the frame it lands");

        for (int i = 0; i < 4; i++) {
            g.mediumTick(SimulationScheduler.MEDIUM_DT);
        }
        g.slowTick(SimulationScheduler.SLOW_DT);
        assertEquals(500f, n.health, 0f, "coarser ticks never burn");
        assertEquals(CombustionSource.DIRECT_HIT.fuelSeconds, n.combustion.fuel(), 0f);
        assertEquals(0f, n.combustion.burnSeconds(), 0f);

        g.fastTick(DT);
        assertEquals(500f - 10f * DT, n.health, 1e-4f, "the first fast tick burns at the contact rate");

        int[] ticks = new int[2];
        float fuel = n.combustion.fuel();
        float seconds = n.combustion.burnSeconds();
        SimulationScheduler.Ticks counted = new SimulationScheduler.Ticks() {
            @Override
            public void fastTick(float dt) {
                ticks[0]++;
                g.fastTick(dt);
            }

            @Override
            public void mediumTick(float dt) {
                ticks[1]++;
                g.mediumTick(dt);
            }

            @Override
            public void slowTick(float dt) {
                g.slowTick(dt);
            }
        };
        for (int i = 0; i < 6; i++) {
            g.scheduler.update(0.25, counted);
        }
        assertTrue(ticks[1] >= 2, "medium ticks ran in between: " + ticks[1]);
        assertEquals(ticks[0] * DT, fuel - n.combustion.fuel(), 1e-4f,
                "the fire advanced once per fast tick and never for a medium one");
        assertEquals(ticks[0] * DT, n.combustion.burnSeconds() - seconds, 1e-4f);
    }

    /**
     * A body the fire kills falls whole in that same fast tick, without one
     * last act, and a player who burns to death leaves no pieces and lives
     * again without the fire.
     */
    @Test
    void aBodyTheFireKillsFallsWholeThatTickAndWithoutALastAct() {
        Game g = arena();
        Creature hare = g.entities.spawnCreature(g.world, Creature.CreatureType.HARE, 320.5f, FEET, 320.5f);
        Npc person = g.entities.spawnNpc(g.world, "Villager", 324.5f, FEET, 320.5f);
        hare.health = 0.1f;
        person.health = 0.1f;
        g.combustion.ignite(g, hare, CombustionSource.DIRECT_HIT, 1f, true, 0, 320.5f, 40.2f, 320.5f);
        g.combustion.ignite(g, person, CombustionSource.DIRECT_HIT, 1f, false, 0, 324.5f, 41f, 320.5f);
        float hunger = hare.hunger;
        float decide = hare.decideTimer;
        long bodies = g.ragdolls.totalSpawned;

        g.fastTick(DT);

        assertTrue(hare.dead && person.dead);
        assertFalse(g.entities.creatures.contains(hare), "removed in the tick the fire killed it");
        assertFalse(g.entities.npcs.contains(person));
        assertEquals(hunger, hare.hunger, 0f, "the dead hare did not act once more");
        assertEquals(decide, hare.decideTimer, 0f);
        assertEquals(bodies + 2, g.ragdolls.totalSpawned, "each falls as one whole body");
        assertEquals(0, g.fragments.liveCount(), "a fire death never comes apart");
        assertTrue(hare.lastHitByPlayer, "the player's bottle earns the hare");
        assertFalse(person.lastHitByPlayer);

        g.appState = Game.AppState.PLAYING;
        g.player.health = 0.1f;
        g.combustion.ignite(g, g.player, CombustionSource.DIRECT_HIT, 1f, false, 0, 310f, 41f, 310f);
        g.fastTick(DT);
        assertTrue(g.player.dead);
        g.enterDeathIfDue();
        assertSame(Game.AppState.DEATH, g.appState);
        assertEquals(0, g.fragments.liveCount(), "no remains: the player burned, nothing blew them apart");

        g.respawn();
        assertFalse(g.player.combustion.burning(), "a new life starts without the old fire");
        assertEquals(0f, g.player.combustion.scorch(), 0f);
        assertEquals(0f, g.player.combustion.burnSeconds(), 0f);
        g.combustion.expose(g.player, CombustionSource.LIQUID, 1f, false, 0, g.player.pos.x, g.player.pos.y,
                g.player.pos.z);
        g.fastTick(DT);
        assertTrue(g.player.combustion.burning(), "and can catch fire again");
    }

    /** A fire keeps the credit of whoever lit it after the body has left the flame. */
    @Test
    void aFireKeepsItsOwnersKillCreditAfterTheBodyLeavesTheFlame() {
        Game g = arena();
        int meat = g.player.inventory.count(ItemType.RAW_MEAT);
        Creature lit = g.entities.spawnCreature(g.world, Creature.CreatureType.BIRD, 320.5f, 44f, 320.5f);
        Creature wild = g.entities.spawnCreature(g.world, Creature.CreatureType.BIRD, 330.5f, 44f, 330.5f);
        Creature takenOver = g.entities.spawnCreature(g.world, Creature.CreatureType.BIRD, 340.5f, 44f, 340.5f);
        g.combustion.expose(lit, CombustionSource.LIQUID, 1f, true, 5, 320.5f, 44f, 320.5f);
        g.combustion.expose(wild, CombustionSource.LIQUID, 1f, false, 6, 330.5f, 44f, 330.5f);
        g.combustion.expose(takenOver, CombustionSource.BLOCK_FIRE, 1f, false, 7, 340.5f, 44f, 340.5f);
        for (int i = 0; i < 5; i++) {
            g.combustion.expose(takenOver, CombustionSource.BLOCK_FIRE, 1f, false, 7, 340.5f, 44f, 340.5f);
            g.fastTick(DT);
        }
        assertTrue(takenOver.combustion.burning());
        assertFalse(takenOver.combustion.ownerByPlayer());
        g.combustion.expose(takenOver, CombustionSource.LIQUID, 1f, true, 8, 340.5f, 44f, 340.5f);

        int ticks = 0;
        while (g.entities.creatures.stream().anyMatch(c -> c == lit || c == wild || c == takenOver)
                && ticks < 400) {
            g.fastTick(DT);
            ticks++;
        }

        assertTrue(lit.dead && wild.dead && takenOver.dead, "every bird burned to death");
        assertTrue(ticks > 10, "long after the only contact: " + ticks + " ticks");
        assertFalse(lit.combustion.inContact());
        assertTrue(lit.lastHitByPlayer, "the player's fire is the player's kill");
        assertFalse(wild.lastHitByPlayer, "a wild fire's kill is nobody's");
        assertTrue(takenOver.lastHitByPlayer, "the player's flame took over the fire, and the kill");
        assertEquals(meat + 2, g.player.inventory.count(ItemType.RAW_MEAT),
                "one bird each for the two fires the player owned");
    }

    /**
     * Contract section 11: the flames are the only burn damage while they
     * last; the medical injury comes once per episode after a second alight,
     * waits out the flames and then hurts and heals as before. A poultice
     * treats the injury, never the flames.
     */
    @Test
    void theBurnInjuryWaitsOutTheFlamesAndThePoulticeOnlyTreatsTheInjury() {
        Game burned = arena();
        Game calm = arena();
        for (Game g : new Game[] {burned, calm}) {
            g.player.health = 50f;
        }
        int contactTicks = 10;
        int injuryTick = -1;
        int outTick = -1;
        Float frozen = null;
        for (int t = 1; t <= 240; t++) {
            if (t <= contactTicks) {
                burned.combustion.expose(burned.player, CombustionSource.LIQUID, 1f, false, 0, 310f, FEET, 310f);
            }
            boolean wasBurning = burned.player.combustion.burning();
            Float before = burned.player.afflictions.get(Affliction.BURN);
            burned.fastTick(DT);
            calm.fastTick(DT);
            Float now = burned.player.afflictions.get(Affliction.BURN);
            if (injuryTick < 0 && now != null) {
                injuryTick = t;
                frozen = now;
            } else if (now != null && wasBurning) {
                assertEquals(before, now, "the injury does not count down while the flames burn");
            } else if (now != null) {
                assertEquals(before - DT, now, 1e-4f, "and counts down again once they are out");
            }
            if (outTick < 0 && wasBurning && !burned.player.combustion.burning()) {
                outTick = t;
                assertEquals(frozen, before, "the flames ended with the injury's full length left");
                float flames = CONTACT_DPS_PLAYER * contactTicks * DT
                        + AFTERBURN_DPS_PLAYER * (CombustionSource.LIQUID.fuelSeconds - FADE_SECONDS
                        + FADE_SECONDS * (1f + MIN_INTENSITY) / 2f);
                assertEquals(flames, calm.player.health - burned.player.health, 0.06f,
                        "while alight the flames are the only burn damage (about 13 for half a second in a pool)");
            }
            assertFalse(calm.player.has(Affliction.BURN));
        }
        assertEquals(Math.round(1f / DT), injuryTick, "the injury comes after one second alight");
        assertEquals(contactTicks + Math.round(CombustionSource.LIQUID.fuelSeconds / DT), outTick);
        float flames = CONTACT_DPS_PLAYER * contactTicks * DT
                + AFTERBURN_DPS_PLAYER * (CombustionSource.LIQUID.fuelSeconds - FADE_SECONDS
                + FADE_SECONDS * (1f + MIN_INTENSITY) / 2f);
        float injury = BURN_DAMAGE_PER_SECOND * (240 - outTick) * DT;
        assertEquals(flames + injury, calm.player.health - burned.player.health, 0.08f,
                "the whole episode's budget: flames, then the injury alone");

        // The poultice: treats the injury in the middle of the flames, which burn on,
        // and no second injury comes in the same episode.
        Game treated = arena();
        treated.combustion.ignite(treated, treated.player, CombustionSource.DIRECT_HIT, 1f, false, 0,
                310f, 41f, 310f);
        for (int t = 0; t < 30; t++) {
            treated.fastTick(DT);
        }
        assertTrue(treated.player.has(Affliction.BURN));
        PlayerTreatmentSystem.Result result = new PlayerTreatmentSystem().apply(treated.player,
                new ItemStack(ItemType.HERBAL_POULTICE, 1));
        assertSame(PlayerTreatmentSystem.Result.BURN_TREATED, result);
        assertFalse(treated.player.has(Affliction.BURN));
        assertTrue(treated.player.combustion.burning(), "a poultice is not a fire extinguisher");
        float health = treated.player.health;
        for (int t = 0; t < 20; t++) {
            treated.fastTick(DT);
        }
        assertTrue(treated.player.health < health, "the flames still hurt");
        assertFalse(treated.player.has(Affliction.BURN), "and one episode leaves one injury");
    }

    @Test
    void theRuntimeBudgetCountsBurningBodiesAgainstTheLiving() {
        Game g = arena();
        Npc a = g.entities.spawnNpc(g.world, "Villager", 320.5f, FEET, 320.5f);
        Npc b = g.entities.spawnNpc(g.world, "Villager", 324.5f, FEET, 320.5f);
        g.entities.spawnCreature(g.world, Creature.CreatureType.DEER, 328.5f, FEET, 320.5f);
        g.combustion.ignite(g, a, CombustionSource.DIRECT_HIT, 1f, false, 0, 320.5f, 41f, 320.5f);
        g.combustion.ignite(g, b, CombustionSource.DIRECT_HIT, 1f, false, 0, 324.5f, 41f, 320.5f);
        g.combustion.ignite(g, g.player, CombustionSource.DIRECT_HIT, 1f, false, 0, 310f, 41f, 310f);

        RuntimeBudgetSnapshot burning = RuntimeBudgetSnapshot.capture(g);
        assertEquals(3, burning.burningBodies());
        assertEquals(4, burning.livingBodies(), "two people, a deer and the player");
        assertTrue(burning.withinHardLimits());
        assertTrue(burning.occupancySummary().contains("burning=3/4"));

        for (Npc n : new Npc[] {a, b}) {
            g.combustion.extinguish(n);
        }
        g.combustion.extinguish(g.player);
        assertEquals(0, RuntimeBudgetSnapshot.capture(g).burningBodies());
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /**
     * {@code CombatSystemsTest}'s isolated arena: four chunks of stone up to
     * y = 39, nobody about, dry, the camp forgotten so no legacy AI walks off
     * to it, and the player standing at (310, 40, 310).
     */
    private static Game arena() {
        Game game = new Game();
        game.newWorld(777L, true);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
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
        game.player.pos.set(310, FEET, 310);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.world.campPos = null;
        game.weather.current = Weather.CLEAR;
        game.weather.next = Weather.CLEAR;
        game.weather.blend = 1f;
        return game;
    }
}
