package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The layered tick loop and world lifecycle, which every other system rides on.
 *
 * <p>Individual systems are covered by their own suites; what is exercised here
 * is the wiring between them — that the scheduler keeps its three cadences under
 * uneven frame times, that a stall cannot spiral, and that starting a new world
 * genuinely clears the previous one instead of leaking state into it.
 */
class GameLoopIntegrationTest {

    /** Records which bucket fired, in order, so cadence and ordering are both visible. */
    private static final class TickRecorder implements SimulationScheduler.Ticks {
        final List<String> order = new ArrayList<>();
        int fast;
        int medium;
        int slow;

        @Override
        public void fastTick(float dt) {
            fast++;
            order.add("fast");
        }

        @Override
        public void mediumTick(float dt) {
            medium++;
            order.add("medium");
        }

        @Override
        public void slowTick(float dt) {
            slow++;
            order.add("slow");
        }
    }

    // ------------------------------------------------------------------
    // Tick cadence
    // ------------------------------------------------------------------

    @Test
    void eachBucketRunsAtItsOwnAdvertisedRate() {
        SimulationScheduler scheduler = new SimulationScheduler();
        TickRecorder ticks = new TickRecorder();

        // 10 simulated seconds of steady 60 FPS frames.
        for (int frame = 0; frame < 600; frame++) {
            scheduler.update(1.0 / 60.0, ticks);
        }

        // FAST_DT is 1/20s, so 10s is 200 fast ticks; allow one frame of slack
        // for accumulator phase at the boundary.
        assertEquals(200, ticks.fast, 1,
                "fast bucket runs at 20 Hz");
        assertEquals(20, ticks.medium, 1,
                "medium bucket runs at 2 Hz");
        assertEquals(1, ticks.slow, 1,
                "slow bucket runs once per 10 s");
    }

    @Test
    void mediumAndSlowBucketsNeverRunWithoutAFastTickInTheSameUpdate() {
        SimulationScheduler scheduler = new SimulationScheduler();
        TickRecorder ticks = new TickRecorder();
        for (int frame = 0; frame < 1200; frame++) {
            scheduler.update(1.0 / 60.0, ticks);
        }

        // The scheduler drains the fast bucket before considering the slower
        // ones, so entity/needs state is always current when the coarser
        // simulation systems read it.
        int seenFast = 0;
        for (String bucket : ticks.order) {
            switch (bucket) {
                case "fast" -> seenFast++;
                case "medium", "slow" -> assertTrue(seenFast > 0,
                        "a coarse tick ran before any fast tick had advanced state");
            }
        }
        assertTrue(ticks.slow >= 1, "the 20 s run must reach the slow bucket");
    }

    @Test
    void aLongStallIsClampedSoCatchUpCannotSpiral() {
        SimulationScheduler scheduler = new SimulationScheduler();
        TickRecorder ticks = new TickRecorder();

        // A 30-second stall (window drag, GC pause, breakpoint) must not try to
        // replay 600 fast ticks in one frame.
        scheduler.update(30.0, ticks);

        assertTrue(ticks.fast <= 10,
                "fast catch-up is bounded by the loop guard, was " + ticks.fast);
        // The stall is clamped to 0.25 s of simulated time, which is below the
        // 0.5 s medium and 10 s slow thresholds: a stall advances the world a
        // little rather than teleporting weather, growth and spoilage forward.
        assertEquals(0, ticks.medium, "a single clamped stall cannot reach the medium bucket");
        assertEquals(0, ticks.slow, "a single clamped stall cannot reach the slow bucket");
    }

    @Test
    void repeatedStallsDoNotAccumulateUnboundedBacklog() {
        SimulationScheduler scheduler = new SimulationScheduler();
        TickRecorder ticks = new TickRecorder();
        for (int i = 0; i < 20; i++) {
            scheduler.update(5.0, ticks);
        }
        // Each update is clamped to 0.25 s of simulated time, so 20 stalls can
        // never bank more than 20 * 0.25 s / FAST_DT = 100 fast ticks.
        assertTrue(ticks.fast <= 100,
                "backlog stays bounded across repeated stalls, was " + ticks.fast);
    }

    @Test
    void resetClearsBankedTimeSoANewWorldStartsOnAFreshCadence() {
        SimulationScheduler scheduler = new SimulationScheduler();
        TickRecorder ticks = new TickRecorder();
        scheduler.update(0.24, ticks); // bank time just under the clamp
        scheduler.reset();

        TickRecorder afterReset = new TickRecorder();
        scheduler.update(0.01, afterReset);
        assertEquals(0, afterReset.fast,
                "reset drops banked time instead of firing a burst on the next frame");
    }

    // ------------------------------------------------------------------
    // World lifecycle
    // ------------------------------------------------------------------

    @Test
    void newWorldClearsSimulationQueuesAndPlayerActionState() {
        Game game = new Game();
        game.newWorld(4242L, true);

        // Dirty every category of cross-world state the reset is meant to clear.
        game.uiMode = Game.UiMode.INVENTORY;
        game.simPaused = true;
        game.miningProgress = 0.7f;
        game.drawingBow = true;
        game.bowDraw = 0.9f;
        game.swingTimer = 0.5f;
        game.sleeping = true;
        game.openCrate = new com.veylon.item.Inventory(4);
        game.openCratePos = new Vec3i(1, 2, 3);
        int fx = (int) game.player.pos.x + 3;
        int fz = (int) game.player.pos.z + 3;
        int fy = game.world.surfaceHeight(fx, fz) + 1;
        game.world.setBlock(fx, fy, fz, BlockType.LOG, true);
        game.fire.ignite(game, fx, fy, fz);
        assertTrue(game.fire.count() > 0, "precondition: a fire is burning");
        game.player.addAffliction(Affliction.BLEEDING, 30f);
        game.entities.spawnCreature(game.world, com.veylon.entity.Creature.CreatureType.WOLF,
                game.player.pos.x + 4, game.player.pos.y, game.player.pos.z);
        assertTrue(game.entities.creatureCount() > 0, "precondition: a creature exists");

        var previousWorld = game.world;
        var previousPlayer = game.player;
        game.newWorld(4242L, true);

        assertFalse(game.world == previousWorld, "a new World instance replaces the old one");
        assertFalse(game.player == previousPlayer, "a new Player replaces the old one");
        assertSame(game, game.world.listener, "the new world reports block changes to Game");
        assertEquals(0, game.fire.count(), "burning blocks do not survive into a new world");
        assertEquals(0, game.projectiles.liveCount(), "projectiles do not survive");
        assertEquals(Game.UiMode.NONE, game.uiMode);
        assertFalse(game.simPaused);
        assertFalse(game.sleeping);
        assertFalse(game.drawingBow);
        assertEquals(0f, game.bowDraw);
        assertEquals(0f, game.miningProgress);
        assertEquals(0f, game.swingTimer);
        assertEquals(0f, game.reloadTimer);
        assertNull(game.targetHit);
        assertTrue(game.player.afflictions.isEmpty(), "a fresh player starts healthy");
        assertEquals(ItemType.ARROW, game.selectedBowAmmo(),
                "ammo selection returns to the default");
    }

    @Test
    void meleeCooldownDoesNotCarryIntoTheNextWorld() {
        Game game = new Game();
        game.newWorld(7717L, true);

        // Land a swing so the melee cooldown is genuinely running, and confirm
        // it is: a second immediate swing must be refused.
        assertTrue(game.performPlayerAttack(wolfInReach(game)),
                "precondition: the first swing lands");
        assertFalse(game.performPlayerAttack(wolfInReach(game)),
                "precondition: the melee cooldown blocks an immediate second swing");

        game.newWorld(7717L, true);

        assertTrue(game.performPlayerAttack(wolfInReach(game)),
                "the first swing in a new world must not be eaten by the previous world's cooldown");
    }

    /** A fresh wolf placed one block from the player, inside melee reach. */
    private static com.veylon.entity.Creature wolfInReach(Game game) {
        com.veylon.entity.Creature wolf = game.entities.spawnCreature(game.world,
                com.veylon.entity.Creature.CreatureType.WOLF,
                game.player.pos.x + 1f, game.player.pos.y, game.player.pos.z);
        assertNotNull(wolf, "precondition: a wolf spawns beside the player");
        return wolf;
    }

    @Test
    void twoWorldsFromTheSameSeedAgreeOnSpawnAndTerrain() {
        Game first = new Game();
        first.newWorld(31337L, true);
        Game second = new Game();
        second.newWorld(31337L, true);

        assertEquals(first.player.pos.x, second.player.pos.x, 1e-4f);
        assertEquals(first.player.pos.y, second.player.pos.y, 1e-4f);
        assertEquals(first.player.pos.z, second.player.pos.z, 1e-4f);
        assertEquals(first.world.generatorVersion, second.world.generatorVersion);
        for (int dx = -24; dx <= 24; dx += 8) {
            for (int dz = -24; dz <= 24; dz += 8) {
                int x = (int) first.player.pos.x + dx;
                int z = (int) first.player.pos.z + dz;
                assertEquals(first.world.surfaceHeight(x, z), second.world.surfaceHeight(x, z),
                        "surface height differs at " + x + "," + z);
                assertEquals(first.world.biomeAt(x, z), second.world.biomeAt(x, z),
                        "biome differs at " + x + "," + z);
            }
        }
    }

    @Test
    void aSaveLoadCycleThroughTheRealGameLoopKeepsNeedsInventoryAndWorldEdits() {
        Game game = new Game();
        game.newWorld(90210L, true);

        // Make the world and the player distinguishable from a fresh start.
        int bx = (int) game.player.pos.x + 2;
        int bz = (int) game.player.pos.z + 2;
        int by = game.world.surfaceHeight(bx, bz) + 1;
        game.world.setBlock(bx, by, bz, BlockType.TORCH, true);
        game.player.inventory.add(ItemType.IRON_PICKAXE, 1);
        game.player.hunger = 61.5f;
        game.player.thirst = 42.25f;
        game.player.addAffliction(Affliction.BLEEDING, 20f);

        // Advance through the production tick entry points, not a bespoke loop.
        for (int i = 0; i < 20; i++) {
            game.fastTick(SimulationScheduler.FAST_DT);
        }
        game.mediumTick(SimulationScheduler.MEDIUM_DT);
        game.slowTick(SimulationScheduler.SLOW_DT);

        float hunger = game.player.hunger;
        float thirst = game.player.thirst;
        int pickaxes = game.player.inventory.count(ItemType.IRON_PICKAXE);
        long seed = game.world.seed;

        Path savePath = AppPaths.dataDirectory().resolve("build/qa/game-loop-integration.dat");
        assertTrue(SaveSystem.save(game, savePath), "save must succeed");

        Game reloaded = new Game();
        assertTrue(SaveSystem.load(reloaded, savePath), "load must succeed");

        assertEquals(seed, reloaded.world.seed, "the world seed round-trips");
        assertEquals(hunger, reloaded.player.hunger, 1e-3f);
        assertEquals(thirst, reloaded.player.thirst, 1e-3f);
        assertEquals(pickaxes, reloaded.player.inventory.count(ItemType.IRON_PICKAXE));
        assertEquals(BlockType.TORCH, reloaded.world.getBlock(bx, by, bz),
                "player block edits survive the round trip");
        assertTrue(reloaded.player.has(Affliction.BLEEDING),
                "active afflictions survive the round trip");
        assertSame(reloaded, reloaded.world.listener,
                "the restored world is wired back to its Game");

        // A loaded world must keep ticking without tripping over restored state.
        for (int i = 0; i < 20; i++) {
            reloaded.fastTick(SimulationScheduler.FAST_DT);
        }
        reloaded.mediumTick(SimulationScheduler.MEDIUM_DT);
        reloaded.slowTick(SimulationScheduler.SLOW_DT);
        assertFalse(reloaded.player.dead, "a healthy restored player survives a second of ticks");
    }

    @Test
    void loadingOverALiveWorldReplacesItRatherThanMerging() {
        Game game = new Game();
        game.newWorld(555L, true);
        int markX = (int) game.player.pos.x + 4;
        int markZ = (int) game.player.pos.z + 4;
        int markY = game.world.surfaceHeight(markX, markZ) + 1;
        game.world.setBlock(markX, markY, markZ, BlockType.CRATE, true);
        game.player.inventory.set(0, new ItemStack(ItemType.TORCH, 5));

        Path savePath = AppPaths.dataDirectory().resolve("build/qa/game-loop-replace.dat");
        assertTrue(SaveSystem.save(game, savePath));

        // A second, different world in the same Game instance, then load back.
        game.newWorld(556L, true);
        assertNotNull(game.world);
        game.entities.spawnCreature(game.world, com.veylon.entity.Creature.CreatureType.WOLF,
                game.player.pos.x + 3, game.player.pos.y, game.player.pos.z);

        assertTrue(SaveSystem.load(game, savePath), "loading over a live world must succeed");
        assertEquals(555L, game.world.seed, "the loaded seed replaces the live one");
        assertEquals(BlockType.CRATE, game.world.getBlock(markX, markY, markZ),
                "the loaded world's edits are present");
        assertEquals(0, game.fire.count(), "no simulation queue leaks from the discarded world");
    }
}
