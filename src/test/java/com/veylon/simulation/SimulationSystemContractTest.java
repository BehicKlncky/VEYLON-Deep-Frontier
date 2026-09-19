package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link SimulationSystem} contract, checked against the systems
 * {@link Game} actually drives.
 *
 * <p>Two things are pinned here. First, that a system's declared cadence
 * matches the bucket Game ticks it from — a system that says
 * {@link MediumTickSystem} but is driven on the slow tick would be quietly
 * wrong. Second, and more usefully, that {@code reset()} means what the
 * interface says it means: starting a new world must leave no system holding
 * state from the previous one.
 */
class SimulationSystemContractTest {

    @Test
    void everySystemDeclaresTheCadenceGameActuallyDrivesItFrom() {
        Game game = new Game();

        assertInstanceOf(FastTickSystem.class, game.settlementManager,
                "settlements are driven from Game.fastTick");

        for (Object mediumSystem : List.of(game.weather, game.temperature, game.water, game.fire,
                game.liquidFire)) {
            assertInstanceOf(MediumTickSystem.class, mediumSystem,
                    mediumSystem.getClass().getSimpleName() + " is driven from Game.mediumTick");
        }

        for (Object slowSystem : List.of(game.plants, game.events, game.itemConditions,
                game.settlementManager)) {
            assertInstanceOf(SlowTickSystem.class, slowSystem,
                    slowSystem.getClass().getSimpleName() + " is driven from Game.slowTick");
        }

        // The clock has no bucket of its own — the run loop advances it with real
        // frame time — but it is still per-world state, so it is a SimulationSystem.
        assertInstanceOf(SimulationSystem.class, game.time);

        // Falling bodies are the same shape: no bucket, because they are driven
        // per frame beside particles and projectiles (a tumbling body turns far
        // faster than the 20 Hz gait it had a moment earlier), but very much
        // per-world state that must not outlive a world.
        assertInstanceOf(SimulationSystem.class, game.ragdolls);
        // Severed pieces are driven beside them, for the same reason.
        assertInstanceOf(SimulationSystem.class, game.fragments);
    }

    @Test
    void aNewWorldClearsEveryPerWorldSystemState() {
        Game game = new Game();
        game.newWorld(31_415L, true);

        // Dirty one piece of state on each system, through its own API where
        // there is one.
        // Burning liquid on a stone slab with someone standing in it, while
        // the new world's sky is still clear.
        int lx = (int) game.player.pos.x - 4;
        int lz = (int) game.player.pos.z - 4;
        int ly = game.world.surfaceHeight(lx, lz) + 1;
        game.world.setBlock(lx, ly, lz, BlockType.STONE, true);
        game.world.setBlock(lx, ly + 1, lz, BlockType.AIR, true);
        game.world.setBlock(lx, ly + 2, lz, BlockType.AIR, true);
        var bather = game.entities.spawnNpc(game.world, "Villager", lx + 0.5f, ly + 1.1f, lz + 0.5f);
        assertTrue(game.liquidFire.spill(game, lx + 0.5f, ly + 1.5f, lz + 0.5f, 1, 0, true) > 0,
                "precondition: liquid is burning");
        game.liquidFire.mediumTick(game, SimulationScheduler.MEDIUM_DT);
        assertTrue(bather.health < bather.maxHealth && game.liquidFire.trackedNpcSpills() > 0,
                "precondition: the liquid has burned someone it now remembers");
        game.liquidFire.totalPatchIgnitions = 4;
        game.time.advance(9_000);
        game.weather.current = WeatherSystem.Weather.STORM;
        game.weather.next = WeatherSystem.Weather.SNOW;
        game.weather.blend = 0.25f;
        game.weather.changeTimer = 3f;
        game.weather.strikeLightning(game);
        assertTrue(game.weather.flashLight() > 0,
                "precondition: lightning has armed the screen flash");
        game.weather.lightningStrikes = 7;
        int fx = (int) game.player.pos.x + 5;
        int fz = (int) game.player.pos.z + 5;
        int fy = game.world.surfaceHeight(fx, fz) + 1;
        game.world.setBlock(fx, fy, fz, BlockType.LOG, true);
        // The storm puts the first fire out and leaves the log to light again.
        game.fire.ignite(game, fx, fy, fz);
        game.fire.mediumTick(game, FireConstants.RAIN_EXTINGUISH_SECONDS);
        assertTrue(game.fire.totalExtinguished > 0, "precondition: the storm has put a fire out");
        game.fire.ignite(game, fx, fy, fz);
        assertTrue(game.fire.count() > 0, "precondition: something is burning");
        int hx = (int) game.player.pos.x + 2;
        int hz = (int) game.player.pos.z + 2;
        int hy = game.world.surfaceHeight(hx, hz) + 1;
        game.world.setBlock(hx, hy, hz, BlockType.TORCH, true);
        game.temperature.mediumTick(game, 0f);
        assertTrue(game.temperature.fireHeatAt(hx + 0.5f, hy + 0.5f, hz + 0.5f) > 0,
                "precondition: the temperature cache contains the old world's torch");
        game.events.onStormStarted(game);
        assertTrue(game.events.totalEventsTriggered > 0, "precondition: an event is running");

        // A body mid-fall, plus a part-consumed solver accumulator and the two
        // lifetime counters the debug overlay reads.
        var victim = game.entities.spawnCreature(game.world, com.veylon.entity.Creature
                .CreatureType.HARE, game.player.pos.x + 2f, game.player.pos.y, game.player.pos.z);
        victim.hurt(victim.health + 100f, true);
        victim.vel.set(2f, 1f, 0f);
        game.entities.fastTick(game, 0.05f);
        assertTrue(game.ragdolls.liveCount() > 0, "precondition: a body is falling");
        assertTrue(game.ragdolls.totalSpawned > 0, "precondition: the spawn counter moved");
        // Leaves most of a step banked in the accumulator.
        game.ragdolls.update(game, 0.016f);

        // A body blown apart earlier and lying in pieces, a second one still in
        // the air, and most of a fragment step banked.
        blowApart(game);
        game.fragments.settleAll(game);
        blowApart(game);
        game.fragments.update(game, 0.016f);
        assertTrue(game.fragments.liveCount() > 0 && game.fragments.settledCount() > 0,
                "precondition: pieces are both flying and lying in the world");

        game.newWorld(31_415L, true);

        assertEquals(8 * 60, game.time.totalMinutes, 1e-6,
                "a new world starts at day 1, 08:00");
        assertEquals(1, game.time.day());
        assertEquals(WeatherSystem.Weather.CLEAR, game.weather.current,
                "a new world starts on settled clear weather");
        assertEquals(WeatherSystem.Weather.CLEAR, game.weather.next);
        assertEquals(1f, game.weather.blend, 1e-6f, "and not mid-transition");
        assertEquals(WeatherConstants.NEW_WORLD_CHANGE_TIMER, game.weather.changeTimer, 1e-6f);
        assertEquals(0, game.weather.lightningStrikes,
                "storm statistics do not carry into the next world");
        assertEquals(0f, game.weather.flashLight(), 1e-6f,
                "a lightning flash does not carry into the next world");
        assertEquals(0f, game.temperature.fireHeatAt(
                        hx + 0.5f, hy + 0.5f, hz + 0.5f), 1e-6f,
                "cached heat-source positions do not carry into the next world");
        assertEquals(0, game.fire.count(), "burning cells do not carry over");
        assertEquals(0, game.fire.totalIgnitions, "fire statistics do not carry over");
        assertEquals(0, game.fire.totalExtinguished);
        assertEquals(0, game.liquidFire.count(), "burning liquid does not carry over");
        assertEquals(0, game.liquidFire.trackedNpcSpills(), "nor who it has burned");
        assertEquals(0, game.liquidFire.totalSpills, "liquid fire statistics do not carry over");
        assertEquals(0, game.liquidFire.totalPatchIgnitions);
        int nx = (int) game.player.pos.x - 4;
        int nz = (int) game.player.pos.z - 4;
        int ny = game.world.surfaceHeight(nx, nz) + 1;
        game.world.setBlock(nx, ny, nz, BlockType.STONE, true);
        game.world.setBlock(nx, ny + 1, nz, BlockType.AIR, true);
        game.liquidFire.spill(game, nx + 0.5f, ny + 1.5f, nz + 0.5f, 0, 0, false);
        assertEquals(0, game.liquidFire.patches().getFirst().spillId(),
                "spill ids start again from zero in the next world");
        assertTrue(game.events.active.isEmpty(), "active events do not carry over");
        assertEquals(0, game.events.totalEventsTriggered);

        assertEquals(0, game.ragdolls.liveCount(),
                "a falling body does not carry into the next world");
        assertEquals(0, game.ragdolls.totalSpawned,
                "body statistics do not carry into the next world");
        assertEquals(0, game.ragdolls.totalSettled);
        assertEquals(0, game.ragdolls.stepsLastUpdate);
        // The solver accumulator is behind no getter, so observe it: a body fed
        // less than one fixed step must not move, which it would if most of a
        // step were still banked from the previous world.
        var fresh = game.entities.spawnCreature(game.world, com.veylon.entity.Creature
                .CreatureType.HARE, game.player.pos.x + 2f, game.player.pos.y, game.player.pos.z);
        fresh.hurt(fresh.health + 100f, true);
        game.entities.fastTick(game, 0.05f);
        game.ragdolls.update(game, SimulationScheduler.FAST_DT / 10f);
        assertEquals(0, game.ragdolls.stepsLastUpdate,
                "a part-consumed solver accumulator carried into the next world");

        assertEquals(0, game.fragments.liveCount(), "flying pieces do not carry into the next world");
        assertEquals(0, game.fragments.settledCount(), "nor do pieces lying on the ground");
        assertEquals(0, game.fragments.totalSpawned, "fragment statistics do not carry over");
        assertEquals(0, game.fragments.totalSettled);
        assertEquals(0, game.fragments.stepsLastUpdate);
        blowApart(game);
        game.fragments.update(game, SimulationScheduler.FAST_DT / 10f);
        assertEquals(0, game.fragments.stepsLastUpdate,
                "a part-consumed fragment accumulator carried into the next world");
    }

    private static void blowApart(Game game) {
        var victim = game.entities.spawnNpc(game.world, "Villager",
                game.player.pos.x + 3f, game.player.pos.y, game.player.pos.z);
        game.entities.npcs.remove(victim);
        game.fragments.spawnFromNpc(game, victim, victim.pos.x + 1f, victim.pos.y + 1f,
                victim.pos.z, 2.6f);
    }

    @Test
    void resettingThroughTheInterfaceIsEnoughToClearASystem() {
        // Nothing about reset() may depend on knowing the concrete type: a future
        // registry that holds List<SimulationSystem> has to be able to do this.
        Game game = new Game();
        game.newWorld(2_718L, true);
        game.time.advance(5_000);
        game.weather.current = WeatherSystem.Weather.RAIN;

        for (SimulationSystem system : List.of(game.time, game.weather, game.temperature,
                game.water, game.fire, game.liquidFire, game.plants, game.events, game.itemConditions,
                game.settlementManager, game.ragdolls, game.fragments)) {
            system.reset();
        }

        assertEquals(8 * 60, game.time.totalMinutes, 1e-6);
        assertEquals(WeatherSystem.Weather.CLEAR, game.weather.current);
    }
}
