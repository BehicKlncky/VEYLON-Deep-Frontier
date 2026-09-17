package com.veylon.entity;

import com.veylon.Game;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The solver's guarantees: a body stays inside the world, comes to rest in open
 * space, and gets there whatever the frame rate or the terrain under it.
 */
class RagdollPhysicsTest {

    private Game game;

    @BeforeEach
    void setUp() {
        game = RagdollTestArena.create(770077L);
    }

    @Test
    void aBodyNeverComesToRestInsideASolidVoxel() {
        // Sweep several launch directions so this is not one lucky trajectory.
        for (int i = 0; i < 8; i++) {
            double angle = i * Math.PI / 4;
            Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                    RagdollTestArena.CENTER_X, RagdollTestArena.GROUND + 0.1f,
                    RagdollTestArena.CENTER_Z);
            RagdollTestArena.kill(wolf, (float) Math.cos(angle) * 7f, 3f,
                    (float) Math.sin(angle) * 7f);
            game.entities.fastTick(game, 0.05f);
            assertEquals(1, game.ragdolls.liveCount(),
                    "precondition: the kill produced a falling body to settle");
            RagdollTestArena.settleAll(game);
        }
        assertEquals(8, game.entities.carcasses.size());
        for (Carcass carcass : game.entities.carcasses) {
            assertFalse(solidAt(carcass.pos.x, carcass.pos.y, carcass.pos.z),
                    "a body came to rest inside a solid voxel at " + carcass.pos);
        }
    }

    @Test
    void aBodyIsPushedClearWhenTheWorldClosesAroundItMidFall() {
        Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                RagdollTestArena.CENTER_X, RagdollTestArena.GROUND + 4f,
                RagdollTestArena.CENTER_Z);
        RagdollTestArena.kill(wolf, 0f, 0f, 0f);
        game.entities.fastTick(game, 0.05f);
        game.ragdolls.update(game, 1f / 60f);

        // Wall the falling body in, the way an explosion backfill or a placed
        // block can. Settling must not leave it embedded in stone.
        Ragdoll body = game.ragdolls.live.getFirst();
        int bx = (int) Math.floor(body.px[Ragdoll.TORSO]);
        int by = (int) Math.floor(body.py[Ragdoll.TORSO]);
        int bz = (int) Math.floor(body.pz[Ragdoll.TORSO]);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    game.world.setBlock(bx + dx, by + dy, bz + dz, BlockType.STONE, false);
                }
            }
        }
        RagdollTestArena.settleAll(game);

        Carcass carcass = game.entities.carcasses.getFirst();
        assertFalse(solidAt(carcass.pos.x, carcass.pos.y, carcass.pos.z),
                "a body walled in mid-fall must still settle in open space");
    }

    @Test
    void aBodyDoesNotTunnelThroughTheFloorAtALargeFrameDelta() {
        Creature deer = game.entities.spawnCreature(game.world, Creature.CreatureType.DEER,
                RagdollTestArena.CENTER_X, RagdollTestArena.GROUND + 20f,
                RagdollTestArena.CENTER_Z);
        // Slam it downward far faster than gravity would ever manage.
        RagdollTestArena.kill(deer, 0f, -60f, 0f);
        game.entities.fastTick(game, 0.05f);
        assertEquals(1, game.ragdolls.liveCount(),
                "precondition: there is a body to drive through the floor");

        // The frame clamp in Game is 0.25 s; feed the worst case repeatedly.
        for (int i = 0; i < 200 && game.ragdolls.liveCount() > 0; i++) {
            for (Ragdoll body : game.ragdolls.live) {
                for (int p = 0; p < body.pointCount; p++) {
                    assertTrue(body.py[p] > RagdollTestArena.GROUND - 1f,
                            "a body point tunnelled below the floor to " + body.py[p]);
                }
            }
            game.ragdolls.update(game, 0.25f);
        }

        assertEquals(0, game.ragdolls.liveCount(), "the body must still settle");
        Carcass carcass = game.entities.carcasses.getFirst();
        assertTrue(carcass.pos.y > RagdollTestArena.GROUND - 0.5f,
                "the carcass ended below the floor at y=" + carcass.pos.y);
    }

    @Test
    void aBodyOverAnUnloadedColumnDoesNotFallToTheWorldFloor() {
        // Far outside the generated arena: World.getBlock reads AIR there, which
        // means "not built yet", not "empty".
        float farX = 12_000.5f;
        float farZ = 12_000.5f;
        assertEquals(null, game.world.getChunk(Math.floorDiv((int) farX, 16),
                Math.floorDiv((int) farZ, 16)), "precondition: the column is unloaded");

        Creature deer = game.entities.spawnCreature(game.world, Creature.CreatureType.DEER,
                farX, 64f, farZ);
        RagdollTestArena.kill(deer, 0f, 0f, 0f);
        game.entities.fastTick(game, 0.05f);
        assertEquals(1, game.ragdolls.liveCount(),
                "precondition: a body exists over the unloaded column");
        RagdollTestArena.settleAll(game);

        Carcass carcass = game.entities.carcasses.getFirst();
        assertTrue(carcass.pos.y > 60f,
                "a body over ungenerated space fell to y=" + carcass.pos.y
                        + " instead of staying put");
    }

    @Test
    void aBodyKilledInMidAirSettlesWithinTheTimeout() {
        assertSettlesWithinTimeout(spawnAt(RagdollTestArena.CENTER_X,
                RagdollTestArena.GROUND + 25f, RagdollTestArena.CENTER_Z), 4f, 2f, 0f);
    }

    @Test
    void aBodyKilledOnASlopeSettlesWithinTheTimeout() {
        // A four-step staircase the body can tumble down.
        for (int step = 1; step <= 4; step++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy < step; dy++) {
                    game.world.setBlock((int) RagdollTestArena.CENTER_X + step,
                            (int) RagdollTestArena.GROUND + dy,
                            (int) RagdollTestArena.CENTER_Z + dz, BlockType.STONE, false);
                }
            }
        }
        assertSettlesWithinTimeout(spawnAt(RagdollTestArena.CENTER_X + 4.5f,
                RagdollTestArena.GROUND + 4.2f, RagdollTestArena.CENTER_Z), -6f, 1f, 0f);
    }

    @Test
    void aBodyKilledInWaterSettlesWithinTheTimeout() {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                for (int dy = 0; dy < 4; dy++) {
                    game.world.setBlock((int) RagdollTestArena.CENTER_X + dx,
                            (int) RagdollTestArena.GROUND + dy,
                            (int) RagdollTestArena.CENTER_Z + dz, BlockType.WATER, false);
                }
            }
        }
        assertSettlesWithinTimeout(spawnAt(RagdollTestArena.CENTER_X,
                RagdollTestArena.GROUND + 3f, RagdollTestArena.CENTER_Z), 2f, 0f, 2f);
    }

    @Test
    void everySpeciesSettlesWithinTheTimeout() {
        for (Creature.CreatureType type : Creature.CreatureType.values()) {
            Game fresh = RagdollTestArena.create(991L);
            Creature c = fresh.entities.spawnCreature(fresh.world, type,
                    RagdollTestArena.CENTER_X, RagdollTestArena.GROUND + 3f,
                    RagdollTestArena.CENTER_Z);
            RagdollTestArena.kill(c, 5f, 2f, 1f);
            fresh.entities.fastTick(fresh, 0.05f);
            assertEquals(1, fresh.ragdolls.liveCount(), type + " left no falling body");
            int frames = RagdollTestArena.settleAll(fresh);
            assertEquals(0, fresh.ragdolls.liveCount(), type + " never settled");
            assertTrue(frames / 60f <= RagdollConstants.SETTLE_TIMEOUT + 0.2f,
                    type + " took " + (frames / 60f) + "s, past the "
                            + RagdollConstants.SETTLE_TIMEOUT + "s guarantee");
        }
    }

    @Test
    void theFixedStepSolverReachesTheSameRestWhateverTheFrameRate() {
        Game slow = killedWolf();
        Game fast = killedWolf();
        // One second of simulation, delivered as 30 frames and as 60.
        for (int i = 0; i < 30; i++) {
            slow.ragdolls.update(slow, 1f / 30f);
        }
        for (int i = 0; i < 60; i++) {
            fast.ragdolls.update(fast, 1f / 60f);
        }

        Ragdoll a = slow.ragdolls.live.getFirst();
        Ragdoll b = fast.ragdolls.live.getFirst();
        assertEquals(a.px[Ragdoll.TORSO], b.px[Ragdoll.TORSO], 1e-4f,
                "the same elapsed time must advance a body identically at any frame rate");
        assertEquals(a.py[Ragdoll.TORSO], b.py[Ragdoll.TORSO], 1e-4f);
        assertEquals(a.roll, b.roll, 1e-4f);
    }

    @Test
    void aStalledFrameClampsInsteadOfReplayingHundredsOfSteps() {
        Game stalled = killedWolf();
        stalled.ragdolls.update(stalled, 30f);
        assertTrue(stalled.ragdolls.stepsLastUpdate <= RagdollConstants.MAX_STEPS_PER_FRAME,
                "a 30-second stall ran " + stalled.ragdolls.stepsLastUpdate
                        + " solver steps instead of clamping");
    }

    @Test
    void aPausedGameDoesNotAdvanceABody() {
        Game paused = killedWolf();
        Ragdoll body = paused.ragdolls.live.getFirst();
        float before = body.py[Ragdoll.TORSO];
        // The simulate gate simply never calls update; a zero or negative delta
        // arriving from anywhere else must be inert too.
        paused.ragdolls.update(paused, 0f);
        paused.ragdolls.update(paused, -1f);
        paused.ragdolls.update(paused, Float.NaN);
        assertEquals(before, body.py[Ragdoll.TORSO], 0f,
                "a body advanced without a positive time step");
    }

    private Game killedWolf() {
        Game g = RagdollTestArena.create(555L);
        Creature wolf = g.entities.spawnCreature(g.world, Creature.CreatureType.WOLF,
                RagdollTestArena.CENTER_X, RagdollTestArena.GROUND + 6f,
                RagdollTestArena.CENTER_Z);
        RagdollTestArena.kill(wolf, 3f, 1f, -2f);
        g.entities.fastTick(g, 0.05f);
        return g;
    }

    private Creature spawnAt(float x, float y, float z) {
        return game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF, x, y, z);
    }

    private void assertSettlesWithinTimeout(Creature victim, float vx, float vy, float vz) {
        RagdollTestArena.kill(victim, vx, vy, vz);
        game.entities.fastTick(game, 0.05f);
        assertEquals(1, game.ragdolls.liveCount(),
                "precondition: the kill produced a falling body");
        int frames = RagdollTestArena.settleAll(game);
        assertEquals(0, game.ragdolls.liveCount(), "the body never settled");
        assertTrue(frames / 60f <= RagdollConstants.SETTLE_TIMEOUT + 0.2f,
                "settling took " + (frames / 60f) + "s, past the "
                        + RagdollConstants.SETTLE_TIMEOUT + "s guarantee");
        Carcass carcass = game.entities.carcasses.getFirst();
        assertFalse(solidAt(carcass.pos.x, carcass.pos.y, carcass.pos.z),
                "the body came to rest inside a solid voxel");
    }

    private boolean solidAt(float x, float y, float z) {
        return game.world.isSolid((int) Math.floor(x), (int) Math.floor(y),
                (int) Math.floor(z));
    }
}
