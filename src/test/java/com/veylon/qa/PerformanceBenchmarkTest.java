package com.veylon.qa;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfEnvironmentVariable;

import java.nio.file.Path;
import java.util.function.LongSupplier;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Wall-clock budgets for the simulation work a frame depends on, so an
 * order-of-magnitude regression fails the build instead of being noticed in
 * play.
 *
 * <h2>What is and is not measured</h2>
 *
 * <p>These are <em>tick</em> budgets, not frame budgets. Rendering needs a GL
 * context that the test JVM does not have, so what is timed here is the
 * simulation half of a frame: the fast/medium/slow tick buckets, settlement
 * work and save serialization. A rendering regression will not show up here.
 *
 * <h2>How to read a failure</h2>
 *
 * <p>Budgets are the baselines recorded in {@code docs/PERFORMANCE_BENCHMARKS.md}
 * plus the 20% allowance (or {@link #NOISE_FLOOR_MS}, whichever is larger), and
 * those baselines were measured on one machine. They are deliberately coarse:
 * they catch "this got several times slower", not "this got 5% slower", and a
 * failure on much slower hardware than the reference means the budget needs
 * re-baselining rather than that the code regressed. Set
 * {@code VEYLON_SKIP_BENCHMARKS=1} to skip the suite on hardware where the
 * budgets do not apply.
 *
 * <p>Each measurement is the fastest of {@link #SAMPLES} runs after
 * {@link #WARMUP} discarded ones. The minimum is used on purpose: it is the run
 * least disturbed by GC and OS scheduling, which makes it far more repeatable
 * than a mean without hiding a genuine slowdown — real regressions move the
 * floor too.
 */
@DisabledIfEnvironmentVariable(named = "VEYLON_SKIP_BENCHMARKS", matches = "1",
        disabledReason = "benchmark budgets are calibrated for the reference machine")
class PerformanceBenchmarkTest {

    /** Discarded runs, so JIT compilation is not part of any measurement. */
    private static final int WARMUP = 3;
    /** Measured runs; the fastest is the reported figure. */
    private static final int SAMPLES = 7;
    /** Headroom over the recorded baseline before a benchmark fails. */
    private static final double ALLOWANCE = 1.20;
    /**
     * Absolute headroom added on top of the percentage, because 20% of a
     * sub-millisecond figure is smaller than the measurement noise. Repeated
     * runs of the tick benchmarks moved by 25-100% on the reference machine
     * while the code was identical, so a flat percentage band on them would
     * fail at random. With this floor the small benchmarks still catch a
     * doubling; only the load benchmark is large enough for the 20% rule to be
     * the binding constraint, which is where it belongs.
     */
    private static final double NOISE_FLOOR_MS = 0.5;

    // Baselines in milliseconds, from docs/PERFORMANCE_BENCHMARKS.md. Update
    // that document and these constants together, and only with a stated reason.
    private static final double BASELINE_CHUNK_TICK_MS = 0.50;
    private static final double BASELINE_ENTITY_TICK_MS = 0.55;
    private static final double BASELINE_SETTLEMENT_TICK_MS = 0.05;
    private static final double BASELINE_SAVE_MS = 0.60;
    private static final double BASELINE_LOAD_MS = 270.0;

    /** Chunks loaded for the world-scale benchmark. */
    private static final int CHUNK_TARGET = 100;
    /** Creatures and NPCs alive for the entity benchmark. */
    private static final int ENTITY_TARGET = 40;
    /** Residents in the benchmarked settlement. */
    private static final int RESIDENT_TARGET = 20;

    @Test
    void aFullTickCycleStaysWithinBudgetWithAHundredChunksLoaded() {
        Game game = new Game();
        game.newWorld(5150L, true);
        int loaded = loadChunks(game, CHUNK_TARGET);
        assertTrue(loaded >= CHUNK_TARGET,
                "precondition: " + CHUNK_TARGET + " chunks loaded, was " + loaded);

        record("chunk tick", tickCycleNanos(game), BASELINE_CHUNK_TICK_MS);
    }

    @Test
    void aFullTickCycleStaysWithinBudgetWithFortyEntitiesActive() {
        Game game = new Game();
        game.newWorld(5151L, true);
        loadChunks(game, 25);
        spawnEntities(game, ENTITY_TARGET);
        assertTrue(game.entities.creatureCount() + game.entities.npcCount() >= ENTITY_TARGET,
                "precondition: " + ENTITY_TARGET + " entities alive");

        record("entity tick", tickCycleNanos(game), BASELINE_ENTITY_TICK_MS);
    }

    @Test
    void aSettlementSlowTickStaysWithinBudgetWithTwentyResidents() {
        Game game = new Game();
        game.newWorld(5152L, true);
        Settlement settlement = populatedSettlement(game, RESIDENT_TARGET);
        assertTrue(settlement.aliveResidents() >= RESIDENT_TARGET,
                "precondition: " + RESIDENT_TARGET + " residents");

        record("settlement tick",
                () -> {
                    long start = System.nanoTime();
                    game.settlementManager.slowTick(game, SimulationScheduler.SLOW_DT);
                    return System.nanoTime() - start;
                },
                BASELINE_SETTLEMENT_TICK_MS);
    }

    @Test
    void savingAndLoadingATypicalWorldStaysWithinBudget() {
        Game game = new Game();
        game.newWorld(5153L, true);
        loadChunks(game, 60);
        spawnEntities(game, 20);
        editBlocks(game, 400);
        populatedSettlement(game, 10);

        Path savePath = AppPaths.dataDirectory().resolve("build/qa/benchmark-save.dat");
        assertTrue(SaveSystem.save(game, savePath), "precondition: the world saves");

        record("save", () -> {
            long start = System.nanoTime();
            boolean ok = SaveSystem.save(game, savePath);
            long elapsed = System.nanoTime() - start;
            assertTrue(ok, "save must succeed");
            return elapsed;
        }, BASELINE_SAVE_MS);

        record("load", () -> {
            Game target = new Game();
            long start = System.nanoTime();
            boolean ok = SaveSystem.load(target, savePath);
            long elapsed = System.nanoTime() - start;
            assertTrue(ok, "load must succeed");
            return elapsed;
        }, BASELINE_LOAD_MS);
    }

    // ------------------------------------------------------------------
    // Harness
    // ------------------------------------------------------------------

    /** Times {@code work}, reports the figure, and fails if it exceeds budget. */
    private static void record(String name, LongSupplier work, double baselineMs) {
        for (int i = 0; i < WARMUP; i++) {
            work.getAsLong();
        }
        long best = Long.MAX_VALUE;
        for (int i = 0; i < SAMPLES; i++) {
            best = Math.min(best, work.getAsLong());
        }
        double ms = best / 1_000_000.0;
        double budget = Math.max(baselineMs * ALLOWANCE, baselineMs + NOISE_FLOOR_MS);
        System.out.printf("benchmark %-16s %8.3f ms (baseline %.2f, budget %.2f)%n",
                name, ms, baselineMs, budget);
        assertTrue(ms <= budget, String.format(
                "%s took %.3f ms, over the %.2f ms budget (baseline %.2f ms, +%d%% or "
                        + "+%.1f ms, whichever is larger). Re-baseline in "
                        + "docs/PERFORMANCE_BENCHMARKS.md if this machine is slower than the "
                        + "reference, otherwise this is a regression.",
                name, ms, budget, baselineMs, Math.round((ALLOWANCE - 1) * 100),
                NOISE_FLOOR_MS));
    }

    /** One fast + medium + slow cycle: the full simulation half of a frame. */
    private static LongSupplier tickCycleNanos(Game game) {
        return () -> {
            long start = System.nanoTime();
            game.fastTick(SimulationScheduler.FAST_DT);
            game.mediumTick(SimulationScheduler.MEDIUM_DT);
            game.slowTick(SimulationScheduler.SLOW_DT);
            return System.nanoTime() - start;
        };
    }

    /** Loads chunks in a widening square until at least {@code target} are resident. */
    private static int loadChunks(Game game, int target) {
        int px = (int) game.player.pos.x;
        int pz = (int) game.player.pos.z;
        for (int radius = 3; radius <= 12 && game.world.loadedChunks().size() < target; radius++) {
            game.world.ensureChunks(px, pz, radius, 1_000_000);
        }
        return game.world.loadedChunks().size();
    }

    /** Half creatures, half NPCs, spread around the player. */
    private static void spawnEntities(Game game, int total) {
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        Creature.CreatureType[] types = Creature.CreatureType.values();
        for (int i = 0; i < total; i++) {
            double angle = i * 2 * Math.PI / total;
            float x = game.player.pos.x + (float) Math.cos(angle) * (6 + i % 9);
            float z = game.player.pos.z + (float) Math.sin(angle) * (6 + i % 9);
            float y = game.world.surfaceHeight((int) x, (int) z) + 1;
            if (i % 2 == 0) {
                game.entities.spawnCreature(game.world, types[i % types.length], x, y, z);
            } else {
                game.entities.spawnNpc(game.world, "bench " + i, x, y, z);
            }
        }
    }

    /** A dormant settlement with a working mix of roles and real stocks. */
    private static Settlement populatedSettlement(Game game, int residents) {
        int x = (int) game.player.pos.x + 300;
        int z = (int) game.player.pos.z + 300;
        Settlement settlement = new Settlement(Settlement.packId(20, 20), 20, 20,
                SettlementType.VILLAGE, new Vec3i(x, 40, z), HumanFaction.FRONTIER,
                Settlement.Alignment.FRIENDLY);
        NpcArchetype[] roles = {NpcArchetype.FARMER, NpcArchetype.VILLAGER,
                NpcArchetype.GUARD, NpcArchetype.MEDIC, NpcArchetype.SMITH};
        for (int i = 0; i < residents; i++) {
            settlement.residents.add(new Settlement.Resident(
                    "resident " + i, roles[i % roles.length]));
        }
        settlement.foodStock = 200;
        settlement.medStock = 40;
        settlement.woodStock = 60;
        game.world.settlements.put(settlement.id, settlement);
        return settlement;
    }

    /** Dirties block storage so the save has real edits to serialize. */
    private static void editBlocks(Game game, int count) {
        int px = (int) game.player.pos.x;
        int pz = (int) game.player.pos.z;
        for (int i = 0; i < count; i++) {
            int x = px + (i % 20) - 10;
            int z = pz + (i / 20) - 10;
            int y = game.world.surfaceHeight(x, z) + 1;
            game.world.setBlock(x, y, z, i % 3 == 0 ? BlockType.PLANK : BlockType.STONE, true);
        }
        game.player.inventory.add(ItemType.IRON_INGOT, 20);
    }
}
