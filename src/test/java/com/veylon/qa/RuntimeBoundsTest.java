package com.veylon.qa;

import com.veylon.Game;
import com.veylon.ai.Pathfinder;
import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.combat.WorldNoise;
import com.veylon.engine.ParticleSystem;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.settlement.CounterattackDirector;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementManager;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.FireSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

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
        game.particles.count = 0;

        stressNoise(game);
        stressParticles(game);
        stressProjectiles(game);
        stressFire(game);
        stressFusesAndChains(game);
        stressMissions(game);

        RuntimeBudgetSnapshot peak = RuntimeBudgetSnapshot.capture(game);
        assertTrue(peak.withinHardLimits(), RuntimeBudgetSnapshot.hardLimitSummary());

        int pendingChunks = peak.pendingGenerationChunks();
        int pendingEdits = peak.pendingGenerationEdits();
        for (int tick = 0; tick < 3_200; tick++) {
            game.noise.update(0.05f);
            game.projectiles.update(game, 0.05f);
            game.particles.update(0.05f);
            game.explosions.tickFuses(game, 0.05f);
            game.fire.mediumTick(game, 0.05f);
            game.settlementManager.fastTick(game, 0.05f);
            assertTrue(RuntimeBudgetSnapshot.capture(game).withinHardLimits(),
                    "runtime ceiling exceeded at stress tick " + tick);
        }

        RuntimeBudgetSnapshot settled = RuntimeBudgetSnapshot.capture(game);
        assertEquals(0, settled.liveProjectiles(), "short-lived projectiles are reclaimed");
        assertEquals(0, settled.stuckArrows(), "expired recoverable arrows are reclaimed");
        assertEquals(0, settled.noiseEvents(), "perception events expire");
        assertEquals(0, settled.kegFuses(), "resolved/removed kegs leave no stale fuse state");
        assertEquals(0, settled.kegFuseAttributions(),
                "resolved/removed kegs leave no stale source attribution");
        assertEquals(0, settled.fires(), "burned-out cells leave no fire state");
        assertEquals(0, settled.particles(), "visual effects expire");
        assertEquals(0, settled.counterattackMissions(), "resolved missions clean up");
        assertEquals(0, settled.dormantCounterattackers(), "mission cleanup releases dormant capacity");
        assertEquals(pendingChunks, settled.pendingGenerationChunks(),
                "simulation without chunk generation cannot grow the pending frontier");
        assertEquals(pendingEdits, settled.pendingGenerationEdits(),
                "simulation without chunk generation cannot grow pending edits");
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
