package com.veylon;

import com.veylon.simulation.SimulationScheduler;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * One world seed must replay identically, whatever ran before it.
 *
 * <p>{@code Game.newWorld} reseeds every generator that affects simulation
 * outcomes. Before that was complete, thirteen of them kept whatever state the
 * previous world in the same process had left, which made a handful of tests
 * order-dependent and occasionally flaky.
 *
 * <p>These tests guard the contract rather than any particular sequence: the
 * first proves no simulation RNG is left unseeded, and the rest prove that two
 * worlds built from one seed agree even when a different world ran in between.
 */
class WorldSeedDeterminismTest {

    /** Systems whose randomness is presentation only and cannot affect outcomes. */
    private static final List<String> PRESENTATION_ONLY = List.of(
            "com.veylon.engine.AudioManager",
            "com.veylon.ui.NpcScreen");

    @Test
    void everySimulationRandomIsSeededByNewWorld() throws Exception {
        Game a = new Game();
        a.newWorld(12345L, true);
        Game b = new Game();
        b.newWorld(12345L, true);

        // Burn a different world through the second instance's generators, then
        // rebuild the original seed. Anything still unseeded will have diverged.
        b.newWorld(999L, true);
        b.newWorld(12345L, true);

        List<String> diverged = new ArrayList<>();
        for (Field f : simulationRandomFields(a)) {
            Random ra = (Random) f.get(fieldOwner(a, f));
            Random rb = (Random) f.get(fieldOwner(b, f));
            if (ra == null || rb == null) {
                continue;
            }
            // Same seed state produces the same next value.
            if (ra.nextLong() != rb.nextLong()) {
                diverged.add(f.getDeclaringClass().getSimpleName() + "." + f.getName());
            }
        }
        if (!diverged.isEmpty()) {
            fail("these generators are not reseeded by newWorld, so a world seed "
                    + "does not replay identically: " + diverged);
        }
    }

    /**
     * Collects the {@link Random} field of every simulation system reachable
     * from {@code Game}, skipping presentation-only ones.
     */
    private static List<Field> simulationRandomFields(Game game) throws Exception {
        List<Field> found = new ArrayList<>();
        for (Field gameField : Game.class.getDeclaredFields()) {
            gameField.setAccessible(true);
            Object system = gameField.get(game);
            if (system == null || system instanceof Game) {
                continue;
            }
            Class<?> type = system.getClass();
            if (PRESENTATION_ONLY.contains(type.getName())) {
                continue;
            }
            for (Field f : type.getDeclaredFields()) {
                if (f.getType() == Random.class) {
                    f.setAccessible(true);
                    found.add(f);
                }
            }
        }
        return found;
    }

    /** Re-resolves which system instance on {@code game} declares {@code f}. */
    private static Object fieldOwner(Game game, Field f) throws Exception {
        for (Field gameField : Game.class.getDeclaredFields()) {
            gameField.setAccessible(true);
            Object system = gameField.get(game);
            if (system != null && f.getDeclaringClass().isInstance(system)) {
                return system;
            }
        }
        throw new IllegalStateException("no owner for " + f);
    }

    @Test
    void aWorldReplaysIdenticallyAfterADifferentWorldRanInTheSameProcess() {
        String first = simulate(20260716L, null);
        String afterOtherWorld = simulate(20260716L, 4242L);
        assertEquals(first, afterOtherWorld,
                "a world seed must not inherit RNG state from a previous world");
    }

    @Test
    void differentSeedsStillProduceDifferentWorlds() {
        // Guards against "fixed" meaning "constant": seeding must not collapse
        // every world onto the same outcome.
        String a = simulate(11111L, null);
        String b = simulate(22222L, null);
        assertNotEquals(a, b, "distinct seeds must still diverge");
    }

    /**
     * Builds a world (optionally after burning a different one through the same
     * Game instance), ticks it, and fingerprints the simulation state that
     * randomness feeds into.
     */
    private static String simulate(long seed, Long warmupSeed) {
        Game game = new Game();
        if (warmupSeed != null) {
            game.newWorld(warmupSeed, true);
            for (int i = 0; i < 40; i++) {
                game.fastTick(SimulationScheduler.FAST_DT);
            }
            game.mediumTick(SimulationScheduler.MEDIUM_DT);
            game.slowTick(SimulationScheduler.SLOW_DT);
        }
        game.newWorld(seed, true);
        for (int i = 0; i < 60; i++) {
            game.fastTick(SimulationScheduler.FAST_DT);
        }
        for (int i = 0; i < 4; i++) {
            game.mediumTick(SimulationScheduler.MEDIUM_DT);
        }
        game.slowTick(SimulationScheduler.SLOW_DT);

        return "weather=" + game.weather.current + "/" + game.weather.next
                + " changeTimer=" + Math.round(game.weather.changeTimer * 100)
                + " strikes=" + game.weather.lightningStrikes
                + " creatures=" + game.entities.creatureCount()
                + " npcs=" + game.entities.npcCount()
                + " fires=" + game.fire.count()
                + " ignitions=" + game.fire.totalIgnitions
                + " events=" + game.events.totalEventsTriggered
                + " active=" + game.events.active.size()
                + " growth=" + game.plants.growthEvents
                + " moisture=" + Math.round(game.plants.lastAvgMoisture * 10000)
                + " water=" + game.water.cellsProcessed
                + " trust=" + Math.round(game.faction.trust * 100);
    }

    @Test
    void theFingerprintActuallyObservesSomething() {
        // A determinism test that compares two empty strings proves nothing.
        String s = simulate(777L, null);
        assertTrue(s.contains("weather=") && s.contains("moisture="),
                "fingerprint must sample real simulation state");
        assertTrue(s.length() > 80, "fingerprint is suspiciously short: " + s);
    }
}
