package com.veylon.qa;

import com.veylon.Game;
import com.veylon.simulation.ShelterSystem;
import com.veylon.simulation.SimulationScheduler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Where the simulation half of a frame actually spends its time, system by
 * system.
 *
 * <h2>Why this exists next to PerformanceBenchmarkTest</h2>
 *
 * <p>{@code PerformanceBenchmarkTest} answers "is a tick still fast enough".
 * This answers "and what is it spending the time on", which is a different
 * question and the one you need before optimizing anything. The v0.5.0 work
 * started by guessing at two hot spots from reading the code; both measured
 * inside the noise, and the profile then showed that plant growth was over half
 * the slow tick and that every block query in the engine was boxing a
 * {@link Long} to hash a chunk key. Neither was visible from the five headline
 * figures.
 *
 * <h2>How to read it, and how not to</h2>
 *
 * <p>The absolute numbers here are <em>smaller</em> than the equivalent slice of
 * {@code PerformanceBenchmarkTest}, and deliberately so: each system is warmed
 * 200 times and sampled 40, while the headline benchmark takes 3 warmups and 7
 * samples of one whole cycle. This measures fully-JIT-warmed steady state; that
 * measures something closer to a real frame. <b>The two are not comparable, and
 * a figure from here must never be copied into the baselines table.</b> Use the
 * shares, not the milliseconds.
 *
 * <p>The budgets below are deliberately coarse — several times the recorded
 * figure — because this suite's job is to catch a system becoming an order of
 * magnitude slower, while the fine-grained 20% band stays with the five
 * headline benchmarks. A failure here means one subsystem regressed badly
 * enough to be worth naming, not that a tick got slower.
 */
@Tag("performance")
class TickProfileTest {

    private static final int WARMUP = 200;
    private static final int SAMPLES = 40;
    /** Chunks loaded, matching the headline chunk-tick fixture. */
    private static final int CHUNK_TARGET = 100;
    /**
     * Multiple of the recorded figure at which a system is called regressed.
     * Wide on purpose: see the class notes on what this suite is for.
     */
    private static final double BUDGET_MULTIPLE = 5.0;

    /**
     * Recorded 2026-07-31 on the reference machine (i7-13850HX, JDK 25.0.1),
     * after the v0.5.0 optimizations. Figures are the fastest of
     * {@link #SAMPLES} runs after {@link #WARMUP} discarded ones.
     */
    private record Part(String name, double recordedMs, Consumer<Game> work) {
    }

    @Test
    void everySimulationSystemStaysWithinItsRecordedShapeOfATick() {
        Game game = loadedWorld();
        List<Part> parts = parts();

        System.out.printf("%n  tick profile — %d chunks, %d creatures, %d NPCs%n",
                game.world.loadedChunks().size(), game.entities.creatureCount(),
                game.entities.npcCount());
        System.out.printf("  %-28s %10s %10s%n", "system", "measured", "recorded");

        List<String> regressed = new ArrayList<>();
        for (Part part : parts) {
            double ms = fastestMillis(game, part.work());
            double budget = Math.max(part.recordedMs() * BUDGET_MULTIPLE, 0.010);
            System.out.printf("  %-28s %8.4f ms %8.4f ms%s%n", part.name(), ms,
                    part.recordedMs(), ms > budget ? "   <-- OVER BUDGET" : "");
            if (ms > budget) {
                regressed.add(String.format("%s: %.4f ms against a recorded %.4f ms",
                        part.name(), ms, part.recordedMs()));
            }
        }

        assertTrue(regressed.isEmpty(), () -> """
                These simulation systems are several times slower than recorded:
                  %s

                This suite is coarse on purpose, so a failure here is a real
                order-of-magnitude regression in one system rather than ordinary
                drift. Find what changed before touching the recorded figures, and
                remember that these numbers are steady-state and are NOT
                comparable with the baselines in docs/PERFORMANCE_BENCHMARKS.md."""
                .formatted(String.join("\n  ", regressed)));
    }

    private static List<Part> parts() {
        float fast = SimulationScheduler.FAST_DT;
        float medium = SimulationScheduler.MEDIUM_DT;
        float slow = SimulationScheduler.SLOW_DT;
        return List.of(
                new Part("fast: player needs", 0.0029,
                        g -> g.player.tickNeeds(g, fast)),
                new Part("fast: entities", 0.0120,
                        g -> g.entities.fastTick(g, fast)),
                new Part("fast: settlements", 0.0006,
                        g -> g.settlementManager.fastTick(g, fast)),
                new Part("medium: weather", 0.0001,
                        g -> g.weather.mediumTick(g, medium)),
                new Part("medium: temperature", 0.0012,
                        g -> g.temperature.mediumTick(g, medium)),
                new Part("medium: water", 0.0001,
                        g -> g.water.mediumTick(g, medium)),
                new Part("medium: fire", 0.0058,
                        g -> g.fire.mediumTick(g, medium)),
                new Part("medium: shelter", 0.0018,
                        g -> ShelterSystem.evaluate(g.world, g.player.pos.x,
                                g.player.pos.y, g.player.pos.z)),
                new Part("medium: fumarole scan", 0.0056,
                        g -> g.world.nearestBasaltFumarole(g.player.pos.x,
                                g.player.pos.y + 0.5f, g.player.pos.z, 5)),
                new Part("medium: whole bucket", 0.0101,
                        g -> g.mediumTick(medium)),
                new Part("slow: plants", 0.0384, g -> g.plants.slowTick(g, slow)),
                new Part("slow: events", 0.0002, g -> g.events.slowTick(g, slow)),
                new Part("slow: faction", 0.0007, g -> g.faction.slowTick(g, slow)),
                new Part("slow: entities", 0.0021, g -> g.entities.slowTick(g)),
                new Part("slow: detritus", 0.0002,
                        g -> g.entities.tickWorldDetritus(g, slow)),
                new Part("slow: item condition", 0.0053,
                        g -> g.itemConditions.slowTick(g, slow)),
                new Part("slow: settlements", 0.0045,
                        g -> g.settlementManager.slowTick(g, slow)),
                new Part("slow: whole bucket", 0.0453, g -> g.slowTick(slow)),
                new Part("whole cycle", 0.1027, g -> {
                    g.fastTick(fast);
                    g.mediumTick(medium);
                    g.slowTick(slow);
                }));
    }

    private static Game loadedWorld() {
        Game game = new Game();
        game.newWorld(5150L, true);
        int px = (int) game.player.pos.x;
        int pz = (int) game.player.pos.z;
        for (int radius = 3;
             radius <= 12 && game.world.loadedChunks().size() < CHUNK_TARGET; radius++) {
            game.world.ensureChunks(px, pz, radius, 1_000_000);
        }
        assertTrue(game.world.loadedChunks().size() >= CHUNK_TARGET,
                "precondition: " + CHUNK_TARGET + " chunks loaded");
        return game;
    }

    private static double fastestMillis(Game game, Consumer<Game> work) {
        for (int i = 0; i < WARMUP; i++) {
            work.accept(game);
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            long start = System.nanoTime();
            work.accept(game);
            best = Math.min(best, System.nanoTime() - start);
        }
        return best / 1_000_000.0;
    }
}
