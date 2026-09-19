package com.veylon.qa;

import com.sun.management.ThreadMXBean;
import com.veylon.Game;
import com.veylon.ai.Pathfinder;
import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.combat.WorldNoise;
import com.veylon.engine.ParticleSystem;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.Npc;
import com.veylon.entity.RagdollConstants;
import com.veylon.item.ItemType;
import com.veylon.settlement.CounterattackDirector;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementManager;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.FireSystem;
import com.veylon.simulation.LiquidFireConstants;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Long-running release QA for every persistent or high-churn bounded collection. */
class RuntimeBoundsTest {

    private static final long[] REQUIRED_SEEDS = {
            1L, 42L, 999L, -1234567L, 20260716L, 987654321L
    };
    /** {@code RagdollAllocationTest}'s allowance for the per-frame body step. */
    private static final long FRAGMENT_BYTES_PER_FRAME_ALLOWANCE = 4_096;

    @Test
    void allRequiredSeedsKeepPendingGenerationBoundedAcrossNegativeAndReversedChunkOrder() {
        List<int[]> forward = new ArrayList<>();
        for (int cx = -5; cx <= -1; cx++) {
            for (int cz = -4; cz <= 0; cz++) {
                forward.add(new int[]{cx, cz});
            }
        }
        List<int[]> reverse = new ArrayList<>(forward);
        Collections.reverse(reverse);

        for (long seed : REQUIRED_SEEDS) {
            World a = generate(seed, forward);
            World b = generate(seed, reverse);

            assertEquals(forward.size(), a.loadedCount(), "forward loaded count, seed " + seed);
            assertEquals(forward.size(), b.loadedCount(), "reverse loaded count, seed " + seed);
            assertPendingFrontierBound(a, seed, "forward");
            assertPendingFrontierBound(b, seed, "reverse");
            assertEquals(a.pendingGenerationChunkCount(), b.pendingGenerationChunkCount(),
                    "generation order cannot create extra pending chunks, seed " + seed);
            assertEquals(a.pendingGenerationEditCount(), b.pendingGenerationEditCount(),
                    "generation order cannot create extra pending edits, seed " + seed);
            assertSettlementRegistrationIsIdempotent(a, forward, seed);
            assertSettlementRegistrationIsIdempotent(b, reverse, seed);
        }
    }

    @Test
    void highChurnRuntimeCollectionsHitTheirCapsThenExpireWithoutGrowth() {
        Game game = new Game();
        game.newWorld(20260716L, true);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.noise.reset();
        game.projectiles.reset();
        game.fire.reset();
        game.liquidFire.reset();
        game.particles.count = 0;

        stressNoise(game);
        stressParticles(game);
        stressProjectiles(game);
        stressFire(game);
        stressLiquidFire(game);
        stressFusesAndChains(game);
        stressBodyFragments(game);
        stressMissions(game);

        RuntimeBudgetSnapshot peak = RuntimeBudgetSnapshot.capture(game);
        assertTrue(peak.withinHardLimits(), RuntimeBudgetSnapshot.hardLimitSummary());

        int pendingChunks = peak.pendingGenerationChunks();
        int pendingEdits = peak.pendingGenerationEdits();
        for (int tick = 0; tick < 3_200; tick++) {
            game.noise.update(0.05f);
            game.projectiles.update(game, 0.05f);
            game.particles.update(0.05f, game.world);
            game.explosions.tickFuses(game, 0.05f);
            game.fire.mediumTick(game, 0.05f);
            game.liquidFire.mediumTick(game, 0.05f);
            game.settlementManager.fastTick(game, 0.05f);
            game.fragments.update(game, 0.05f);
            assertTrue(RuntimeBudgetSnapshot.capture(game).withinHardLimits(),
                    "runtime ceiling exceeded at stress tick " + tick);
            assertFragmentCaps(game);
        }

        RuntimeBudgetSnapshot settled = RuntimeBudgetSnapshot.capture(game);
        assertEquals(0, settled.liveProjectiles(), "short-lived projectiles are reclaimed");
        assertEquals(0, settled.stuckArrows(), "expired recoverable arrows are reclaimed");
        assertEquals(0, settled.noiseEvents(), "perception events expire");
        assertEquals(0, settled.kegFuses(), "resolved/removed kegs leave no stale fuse state");
        assertEquals(0, settled.kegFuseAttributions(),
                "resolved/removed kegs leave no stale source attribution");
        assertEquals(0, settled.fires(), "burned-out cells leave no fire state");
        assertEquals(0, settled.liquidFirePatches(), "spilled liquid burns out");
        assertEquals(0, game.liquidFire.trackedNpcSpills(), "burned-out spills are forgotten");
        assertEquals(0, settled.particles(), "visual effects expire");
        assertEquals(0, settled.counterattackMissions(), "resolved missions clean up");
        assertEquals(0, settled.dormantCounterattackers(), "mission cleanup releases dormant capacity");
        assertEquals(pendingChunks, settled.pendingGenerationChunks(),
                "simulation without chunk generation cannot grow the pending frontier");
        assertEquals(pendingEdits, settled.pendingGenerationEdits(),
                "simulation without chunk generation cannot grow pending edits");
        assertEquals(0, game.fragments.liveCount(), "flying body pieces settle");
        game.entities.tickWorldDetritus(game, 0.05f);
        assertEquals(0, game.fragments.settledCount(),
                "body pieces left far behind the player are reclaimed");
    }

    /**
     * The most this release's combat can put in the world at once, stepped the
     * way the frame and the medium tick drive it: twelve people blown apart by
     * three scrap-bomb blasts (exactly the live fragment cap), seven fire bombs
     * burning where they broke, and every block fire the fire cap allows.
     * Every ceiling holds on every frame until the pieces have come to rest.
     *
     * <p>The fragment step is measured the way {@code RagdollAllocationTest}
     * measures the body solver, against the same 4 KB/frame allowance, but over
     * real blast trajectories from launch to rest, with the rest of the scene at
     * full load. Only the {@code fragments.update} calls are inside the
     * measurement; settling may grow the settled list, which the allowance
     * covers, and nothing else in a step may allocate.
     */
    @Test
    void blastsMolotovsAndAFullFireCapTogetherStayWithinEveryCap() {
        Game game = flatArena(20260716L);
        float feet = 40.1f;

        // Every block fire the cap allows, on a plank floor away from the rest.
        int admitted = 0;
        for (int x = 290; x < 312; x++) {
            for (int z = 290; z < 300; z++) {
                game.world.setBlock(x, 40, z, BlockType.PLANK, false);
                admitted += game.fire.ignite(game, x, 40, z) ? 1 : 0;
            }
        }
        assertEquals(FireSystem.MAX_ACTIVE_FIRES, admitted, "precondition: the fire cap is full");

        // Seven fire bombs dropped onto bare floor, far enough apart not to meet.
        WeaponDefinition fireBomb = WeaponRegistry.byId("fire_bomb");
        for (int i = 0; i < 7; i++) {
            game.projectiles.fire(game, game.player, true, 293.5f + i * 8, 42f, 340.5f,
                    0, -1, 0, fireBomb, null);
        }
        for (int i = 0; i < 100 && game.projectiles.liveCount() > 0; i++) {
            game.projectiles.update(game, 0.01f);
        }
        assertEquals(7 * LiquidFireConstants.MAX_PATCHES_PER_SPILL, game.liquidFire.count(),
                "precondition: seven full pools are burning");

        // Twelve people in three groups of four, each group caught by one blast.
        int victims = BodyFragmentConstants.MAX_LIVE_FRAGMENTS / 10;
        float[][] ring = {{1.5f, 0}, {-1.5f, 0}, {0, 1.5f}, {0, -1.5f}};
        for (int blast = 0; blast < victims / ring.length; blast++) {
            float bx = 300.5f + blast * 20;
            for (float[] offset : ring) {
                game.entities.spawnNpc(game.world, "QA victim", bx + offset[0], feet,
                        320.5f + offset[1]);
            }
            game.explosions.explode(game, bx, feet + 0.875f, 320.5f, 2.6f, 14f, 0f, true, true);
        }
        game.entities.fastTick(game, SimulationScheduler.FAST_DT);
        assertEquals(0, game.entities.npcCount(), "precondition: every one of them was killed");
        assertEquals(BodyFragmentConstants.MAX_LIVE_FRAGMENTS, game.fragments.liveCount(),
                "precondition: twelve bodies blown apart fill the live fragment cap");
        assertEquals(0, game.ragdolls.liveCount(), "none of them fell whole");
        assertTrue(RuntimeBudgetSnapshot.capture(game).withinHardLimits(),
                RuntimeBudgetSnapshot.hardLimitSummary());

        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        float dt = 1f / 60f;
        int framesPerTick = Math.round(SimulationScheduler.MEDIUM_DT / dt);
        long fragmentBytes = 0;
        int flyingFrames = 0;
        for (int frame = 1; frame <= 12 * 60; frame++) {
            game.projectiles.update(game, dt);
            game.explosions.tickFuses(game, dt);
            game.noise.update(dt);
            game.particles.update(dt, game.world);
            boolean flying = game.fragments.liveCount() > 0;
            long before = bean.getThreadAllocatedBytes(thread);
            game.fragments.update(game, dt);
            long after = bean.getThreadAllocatedBytes(thread);
            if (flying) {
                fragmentBytes += after - before;
                flyingFrames++;
            }
            if (frame % framesPerTick == 0) {
                game.fire.mediumTick(game, SimulationScheduler.MEDIUM_DT);
                game.liquidFire.mediumTick(game, SimulationScheduler.MEDIUM_DT);
            }
            assertTrue(RuntimeBudgetSnapshot.capture(game).withinHardLimits(),
                    "runtime ceiling exceeded at frame " + frame + ": "
                            + RuntimeBudgetSnapshot.capture(game).occupancySummary());
            assertFragmentCaps(game);
            assertTrue(game.ragdolls.liveCount() <= RagdollConstants.MAX_LIVE);
        }

        assertEquals(0, game.fragments.liveCount(), "every piece came to rest");
        assertEquals(BodyFragmentConstants.MAX_LIVE_FRAGMENTS, game.fragments.settledCount(),
                "and lies in the world, under the settled cap");
        long perFrame = fragmentBytes / flyingFrames;
        System.out.println("worst-case fragment allocation: " + perFrame + " bytes/frame over "
                + flyingFrames + " frames in flight");
        assertTrue(perFrame < FRAGMENT_BYTES_PER_FRAME_ALLOWANCE, "the fragment step allocated "
                + perFrame + " bytes/frame, over the " + FRAGMENT_BYTES_PER_FRAME_ALLOWANCE
                + " byte allowance");
    }

    @Test
    void repeatedPathQueriesStayBudgetedAndNeverLoadChunks() {
        Game game = new Game();
        game.newWorld(-1234567L, true);
        int loaded = game.world.loadedCount();
        int sx = (int) Math.floor(game.player.pos.x);
        int sy = (int) Math.floor(game.player.pos.y);
        int sz = (int) Math.floor(game.player.pos.z);
        Npc cached = game.entities.spawnNpc(game.world, "Path-cache QA",
                sx + 0.5f, sy, sz + 0.5f);

        int longest = 0;
        for (int i = 0; i < 2_000; i++) {
            int dx = (i % (Pathfinder.MAX_RANGE * 2 + 1)) - Pathfinder.MAX_RANGE;
            int dz = ((i * 37) % (Pathfinder.MAX_RANGE * 2 + 1)) - Pathfinder.MAX_RANGE;
            var path = Pathfinder.find(game.world, sx, sy, sz,
                    sx + dx, sy, sz + dz, Pathfinder.DEFAULT_BUDGET);
            if (path != null) {
                longest = Math.max(longest, path.size());
                assertTrue(path.size() <= Pathfinder.MAX_PATH_NODES);
                cached.path = path;
                assertEquals(path.size(), RuntimeBudgetSnapshot.capture(game).cachedPathNodes(),
                        "repath replaces the one cache instead of appending another route");
            }
        }

        assertTrue(longest > 0, "the stress run must exercise at least one successful path");
        assertTrue(longest <= Pathfinder.MAX_PATH_NODES);
        assertEquals(loaded, game.world.loadedCount(),
                "bounded A* never materializes an unloaded search chunk");
    }

    private static World generate(long seed, List<int[]> chunks) {
        World world = new World(seed, World.CURRENT_GENERATOR);
        for (int[] chunk : chunks) {
            world.getOrCreateChunk(chunk[0], chunk[1]);
        }
        return world;
    }

    private static void assertPendingFrontierBound(World world, long seed, String order) {
        // Cross-border decorations reach only the immediate exterior frontier.
        // These proportional limits are deliberately generous enough for a
        // fortress slice but catch permanent/super-linear pending growth.
        assertTrue(world.pendingGenerationChunkCount() <= world.loadedCount() * 4,
                order + " pending chunk frontier grew without bound, seed " + seed);
        assertTrue(world.pendingGenerationEditCount() <= world.loadedCount() * 2_048,
                order + " pending edit frontier grew without bound, seed " + seed);
    }

    private static void assertSettlementRegistrationIsIdempotent(
            World world, List<int[]> chunks, long seed) {
        int registered = world.settlements.size();
        for (int[] chunk : chunks) {
            int rx = SettlementPlanner.regionOfChunk(chunk[0]);
            int rz = SettlementPlanner.regionOfChunk(chunk[1]);
            world.settlementForRegion(rx, rz);
            world.settlementForRegion(rx, rz);
        }
        assertEquals(registered, world.settlements.size(),
                "revisiting planned regions cannot duplicate settlements, seed " + seed);
    }

    private static void stressNoise(Game game) {
        for (int i = 0; i < WorldNoise.MAX_EVENTS * 8; i++) {
            game.noise.emit(game, i, 50, -i, 10, 0.5f,
                    "qa-noise", false, null);
        }
        assertEquals(WorldNoise.MAX_EVENTS, game.noise.count());
    }

    private static void stressParticles(Game game) {
        for (int i = 0; i < ParticleSystem.MAX + 500; i++) {
            game.particles.spawn(ParticleSystem.KIND_DOT, 0, 60, 0,
                    0, 0, 0, 1, 1, 1, 0.1f, 0.2f, 0);
        }
        assertEquals(ParticleSystem.MAX, game.particles.count);
    }

    private static void stressProjectiles(Game game) {
        int x = (int) Math.floor(game.player.pos.x);
        int y = (int) Math.floor(game.player.pos.y) + 1;
        int z = (int) Math.floor(game.player.pos.z);
        for (int dz = -3; dz <= 3; dz++) {
            for (int dy = -2; dy <= 3; dy++) {
                game.world.setBlock(x + 5, y + dy, z + dz, BlockType.STONE, false);
            }
        }
        WeaponDefinition bow = WeaponRegistry.byId("primitive_bow");
        game.projectiles.setRandomSeed(0xB0A11L);
        for (int i = 0; i < ProjectileSystem.MAX_LIVE + 80; i++) {
            game.projectiles.fire(game, game.player, true,
                    x + 0.5f, y + 0.5f, z + 0.5f,
                    1, 0, 0, bow, ItemType.ARROW);
        }
        assertEquals(ProjectileSystem.MAX_LIVE, game.projectiles.liveCount());
        for (int i = 0; i < 200 && game.projectiles.liveCount() > 0; i++) {
            game.projectiles.update(game, 0.05f);
        }
        assertTrue(game.projectiles.stuck.size() <= ProjectileSystem.MAX_STUCK);
    }

    private static void stressFire(Game game) {
        int x = (int) Math.floor(game.player.pos.x) - 8;
        int y = Math.min(100, (int) Math.floor(game.player.pos.y) + 12);
        int z = (int) Math.floor(game.player.pos.z) - 8;
        int admitted = 0;
        for (int dx = 0; dx < 15; dx++) {
            for (int dz = 0; dz < 15; dz++) {
                game.world.setBlock(x + dx, y, z + dz, BlockType.LOG, false);
                if (game.fire.ignite(game, x + dx, y, z + dz)) {
                    admitted++;
                } else {
                    // Do not leave the five deliberately rejected fuel blocks
                    // available for a random final-tick spread after every
                    // admitted fire has consumed its own log.
                    game.world.setBlock(x + dx, y, z + dz, BlockType.AIR, false);
                }
            }
        }
        assertEquals(FireSystem.MAX_ACTIVE_FIRES, admitted,
                "the exact fire cap is reachable but cannot be exceeded");
        assertEquals(FireSystem.MAX_ACTIVE_FIRES, game.fire.count());
        game.fire.mediumTick(game, 20f);
        assertEquals(0, game.fire.count());
    }

    /**
     * Ten molotovs on a bare stone platform high above the terrain, with
     * clear air around it so the liquid has nothing to light: more liquid
     * than the cap allows, which the oldest spills give way to.
     */
    private static void stressLiquidFire(Game game) {
        int x = (int) Math.floor(game.player.pos.x) - 24;
        int y = Math.min(Chunk.SY - 6, (int) Math.floor(game.player.pos.y) + 24);
        int z = (int) Math.floor(game.player.pos.z) - 40;
        for (int dx = -1; dx <= 45; dx++) {
            for (int dz = -1; dz <= 19; dz++) {
                boolean platform = dx >= 0 && dx <= 44 && dz >= 0 && dz <= 18;
                game.world.setBlock(x + dx, y, z + dz,
                        platform ? BlockType.STONE : BlockType.AIR, false);
                for (int dy = 1; dy <= 3; dy++) {
                    game.world.setBlock(x + dx, y + dy, z + dz, BlockType.AIR, false);
                }
            }
        }
        int bottles = LiquidFireConstants.MAX_PATCHES / LiquidFireConstants.MAX_PATCHES_PER_SPILL + 3;
        for (int i = 0; i < bottles; i++) {
            game.liquidFire.spill(game, x + 4.5f + (i % 5) * 9, y + 1.5f, z + 4.5f + (i / 5) * 10,
                    1, 0, false);
            assertTrue(game.liquidFire.count() <= LiquidFireConstants.MAX_PATCHES,
                    "liquid fire over its cap after bottle " + i);
        }
        assertEquals(LiquidFireConstants.MAX_PATCHES, game.liquidFire.count(),
                "the exact liquid fire cap is reachable");
    }

    private static void stressFusesAndChains(Game game) {
        int x = (int) Math.floor(game.player.pos.x) - 12;
        int y = Math.min(105, (int) Math.floor(game.player.pos.y) + 16);
        int z = (int) Math.floor(game.player.pos.z) + 14;
        for (int i = 0; i < ExplosionSystem.MAX_CHAIN + 4; i++) {
            game.world.setBlock(x + i * 2, y, z, BlockType.POWDER_KEG, false);
        }
        game.explosions.explode(game, x - 1, y + 0.5f, z + 0.5f,
                3.8f, 30f, 0, true);
        int survivors = 0;
        for (int i = 0; i < ExplosionSystem.MAX_CHAIN + 4; i++) {
            Vec3i pos = new Vec3i(x + i * 2, y, z);
            if (game.world.getBlock(pos.x(), pos.y(), pos.z()) == BlockType.POWDER_KEG) {
                survivors++;
            }
        }
        assertTrue(survivors >= 2, "one detonation cannot recursively consume an unbounded chain");

        int fuseX = x - 6;
        int fuseZ = z + 10;
        int admitted = 0;
        for (int i = 0; i < ExplosionSystem.MAX_ACTIVE_FUSES + 9; i++) {
            Vec3i pos = new Vec3i(fuseX + (i % 10) * 2, y, fuseZ + (i / 10) * 2);
            game.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.POWDER_KEG, false);
            if (game.explosions.tryArmKeg(game, pos, 0.05f)) {
                admitted++;
            }
        }
        assertEquals(ExplosionSystem.MAX_ACTIVE_FUSES, admitted,
                "each arming attempt re-evaluates the global fuse budget");
        assertEquals(ExplosionSystem.MAX_ACTIVE_FUSES, game.world.kegFuses.size());
        assertEquals(game.world.kegFuses.size(),
                game.world.kegFusePlayerAttribution.size());
        assertFalse(game.explosions.tryArmKeg(game, new Vec3i(fuseX, y + 1, fuseZ), 1f),
                "air cannot consume fuse capacity");
        game.explosions.tickFuses(game, 0.1f);
        assertTrue(game.world.kegFuses.isEmpty());
        assertTrue(game.world.kegFusePlayerAttribution.isEmpty());
        assertTrue(game.particles.count <= ParticleSystem.MAX);
        assertTrue(game.noise.count() <= WorldNoise.MAX_EVENTS);
        assertTrue(game.fire.count() <= FireSystem.MAX_ACTIVE_FIRES);
    }

    /**
     * Seventy-five people blown apart on the spot: the first twelve fill the
     * live cap, every later one pushes the oldest ten pieces to the ground, and
     * the ground fills its own cap and starts dropping the oldest.
     */
    private static void stressBodyFragments(Game game) {
        Npc victim = game.entities.spawnNpc(game.world, "QA victim",
                game.player.pos.x + 3f, game.player.pos.y, game.player.pos.z);
        game.entities.npcs.remove(victim);
        int bodies = (BodyFragmentConstants.MAX_LIVE_FRAGMENTS
                + BodyFragmentConstants.MAX_SETTLED_FRAGMENTS) / 10 + 3;
        for (int i = 0; i < bodies; i++) {
            game.fragments.spawnFromNpc(game, victim, victim.pos.x + 1f, victim.pos.y + 1f,
                    victim.pos.z, 3.8f);
            assertFragmentCaps(game);
        }
        assertEquals(BodyFragmentConstants.MAX_LIVE_FRAGMENTS, game.fragments.liveCount(),
                "the exact live fragment cap is reachable");
        assertEquals(BodyFragmentConstants.MAX_SETTLED_FRAGMENTS, game.fragments.settledCount(),
                "the exact settled fragment cap is reachable");
    }

    /**
     * {@code CombatSystemsTest}'s isolated arena: four chunks of stone up to
     * y = 39, nobody about, dry, and fixed seeds.
     */
    private static Game flatArena(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
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
        game.player.pos.set(310, 40.1f, 310);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.noise.reset();
        game.particles.count = 0;
        game.projectiles.setRandomSeed(0xC0B7L);
        game.fire.setRandomSeed(0xF12EL);
        game.liquidFire.setRandomSeed(0x11F1L);
        game.weather.current = WeatherSystem.Weather.CLEAR;
        game.weather.next = WeatherSystem.Weather.CLEAR;
        game.weather.blend = 1f;
        return game;
    }

    private static void assertFragmentCaps(Game game) {
        assertTrue(game.fragments.liveCount() <= BodyFragmentConstants.MAX_LIVE_FRAGMENTS,
                "live body pieces over their cap: " + game.fragments.liveCount());
        assertTrue(game.fragments.settledCount() <= BodyFragmentConstants.MAX_SETTLED_FRAGMENTS,
                "settled body pieces over their cap: " + game.fragments.settledCount());
    }

    private static void stressMissions(Game game) {
        game.settlementManager.counterattacks.reset();
        game.entities.npcs.clear();
        game.world.settlements.clear();
        Settlement origin = settlement(game, 100, -100, false, 0);
        game.world.settlements.put(origin.id, origin);

        List<Settlement> targets = new ArrayList<>();
        for (int i = 0; i < CounterattackDirector.MAX_MISSIONS + 3; i++) {
            Settlement target = settlement(game, 180 + i * 70, -100, true, 8);
            game.world.settlements.put(target.id, target);
            targets.add(target);
            game.settlementManager.dispatchCounterattack(game, target, 4);
        }
        RuntimeBudgetSnapshot dispatched = RuntimeBudgetSnapshot.capture(game);
        assertEquals(CounterattackDirector.MAX_MISSIONS, dispatched.counterattackMissions());
        assertEquals(CounterattackDirector.MAX_DORMANT_ATTACKERS,
                dispatched.dormantCounterattackers());
        assertTrue(dispatched.withinHardLimits());

        int loaded = game.world.loadedCount();
        game.player.pos.set(50_000, 60, 50_000);
        for (int i = 0; i < 3_000
                && !game.settlementManager.counterattacks.missions.isEmpty(); i++) {
            game.settlementManager.fastTick(game, 2f);
        }
        assertTrue(game.settlementManager.counterattacks.missions.isEmpty(),
                "bounded dormant missions resolve, report and clean up");
        assertEquals(loaded, game.world.loadedCount(),
                "dormant travel and cleanup do not require destination chunks");
        assertFalse(targets.isEmpty());
    }

    private static Settlement settlement(Game game, int x, int z,
                                         boolean occupied, int defenders) {
        int rx = SettlementPlanner.regionOfBlock(x);
        int rz = SettlementPlanner.regionOfBlock(z);
        long id = Settlement.packId(rx, rz) ^ (((long) x) << 17) ^ z;
        int y = game.world.generator.heightAt(x, z) + 1;
        Settlement settlement = new Settlement(id, rx, rz, SettlementType.FORT,
                new Vec3i(x, y, z), HumanFaction.HEADHUNTERS,
                occupied ? Settlement.Alignment.FRIENDLY : Settlement.Alignment.HOSTILE);
        settlement.cleared = occupied;
        settlement.occupied = occupied;
        if (occupied) {
            settlement.factionId = HumanFaction.FRONTIER;
            settlement.commandNeutralized = true;
            settlement.alarmNeutralized = true;
            settlement.centralObjectiveControlled = true;
            for (int i = 0; i < defenders; i++) {
                settlement.residents.add(new Settlement.Resident(
                        "QA defender " + i, NpcArchetype.GUARD));
            }
        }
        return settlement;
    }
}
