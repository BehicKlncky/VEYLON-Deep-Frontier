package com.veylon.entity;

import com.veylon.Game;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;

/**
 * The solver's guarantees: a body stays inside the world, comes to rest in open
 * space, and gets there whatever the frame rate or the terrain under it.
 */
class RagdollPhysicsTest {

    private Game game;

    @Test
    void anUnpushedDeathCannotFreezeBalancedOnItsFeet() {
        Ragdoll r = RagdollTestArena.human(game, 0.05f, 0, 0, 0);
        RagdollTestArena.settleAll(game);
        assertTrue(r.py[0] < RagdollTestArena.GROUND + 0.55f,
                "torso must land, not freeze standing: " + r.py[0]);
        assertTrue(r.torsoGrounded, "natural settling requires torso support");
        assertTrue(r.age < RagdollConstants.SETTLE_TIMEOUT);
    }

    @Test
    void elbowsAndKneesStayInTheirHingePlanesAndLimitsThroughoutAFullFall() {
        float elbowTravel = 0, kneeTravel = 0;
        for (int fall = 0; fall < 4; fall++) {
            Ragdoll r = RagdollTestArena.human(game, 4, (fall % 2 == 0 ? 1 : -1) * 3, 2,
                    fall < 2 ? -3 : 3);
            for (int tick = 0; !r.settled && tick < 361; tick++) {
                game.ragdolls.update(game, RagdollConstants.FIXED_STEP);
                assertJoints(r);
                for (int b = 0; b < r.skeleton.boneCount; b++) {
                    if (r.skeleton.part[b].startsWith("forearm")) {
                        elbowTravel = Math.max(elbowTravel, Math.abs(r.pose.boneRotX[b]));
                    }
                    if (r.skeleton.part[b].startsWith("shin")) {
                        kneeTravel = Math.max(kneeTravel, Math.abs(r.pose.boneRotX[b]));
                    }
                }
            }
            assertTrue(r.settled);
        }
        assertTrue(elbowTravel > 0.25f, "elbows must visibly fold: " + elbowTravel);
        assertTrue(kneeTravel > 0.25f, "knees must visibly fold: " + kneeTravel);
    }

    @Test
    void groundedQuadrupedsGiveWayInsteadOfBalancingOnStraightLegs() {
        for (Creature.CreatureType type : Creature.CreatureType.values()) {
            if (type == Creature.CreatureType.BIRD || type == Creature.CreatureType.HARE) continue;
            for (int direction = 0; direction < 4; direction++) {
                Creature c = game.entities.spawnCreature(game.world, type,
                        RagdollTestArena.CENTER_X, RagdollTestArena.GROUND + 0.05f, RagdollTestArena.CENTER_Z);
                c.yaw = direction * 90;
                RagdollTestArena.kill(c, direction == 0 ? 0 : 7.5f, direction == 0 ? 0 : 3.4f, 0);
                Ragdoll r = game.ragdolls.spawn(game, c);
                game.entities.creatures.remove(c);
                for (int tick = 0; !r.settled && tick < 361; tick++) {
                    game.ragdolls.update(game, RagdollConstants.FIXED_STEP);
                }
                assertTrue(r.torsoGrounded,
                        type + " remained standing after direction " + direction + " at " + r.py[0]);
            }
        }
    }

    @Test
    void insetShortLegSocketsDoNotGenerateLiftInFreeFall() {
        Creature hare = game.entities.spawnCreature(game.world, Creature.CreatureType.HARE,
                RagdollTestArena.CENTER_X, RagdollTestArena.GROUND + 5, RagdollTestArena.CENTER_Z);
        RagdollTestArena.kill(hare, 0, 0, 0);
        Ragdoll body = game.ragdolls.spawn(game, hare);
        float initialY = body.py[0];
        for (int tick = 0; tick < 20; tick++) game.ragdolls.update(game, RagdollConstants.FIXED_STEP);
        assertTrue(body.py[0] < initialY - 0.7f,
                "internal socket contacts must not cancel gravity or propel a small body");
        assertFalse(body.grounded);
    }

    @Test
    void freeLimbsLagBehindTheTorsoAndContinueMovingAfterContact() {
        Ragdoll r = RagdollTestArena.human(game, 2, 2, 1, 1);
        int arm = 2;
        float early = 0, postContact = 0, previous = 0;
        boolean touched = false;
        for (int tick = 0; !r.settled && tick < 361; tick++) {
            game.ragdolls.update(game, RagdollConstants.FIXED_STEP);
            float angle = r.jointRotation[arm].angle();
            early = Math.max(early, angle);
            if (touched) postContact += Math.abs(angle - previous);
            touched |= r.grounded;
            previous = angle;
        }
        assertTrue(early > 0.5f, "a shoulder must swing freely relative to the torso: " + early);
        assertTrue(postContact > 0.1f, "limbs must keep swinging after the first ground contact: " + postContact);
    }

    @Test
    void everySpeciesKeepsItsWholeChainClearOnStepsAndSettlesBeforeTheBackstop() {
        RagdollTestArena.steps(game);
        for (int kind = 0; kind <= Creature.CreatureType.values().length; kind++) {
            Ragdoll r;
            if (kind == Creature.CreatureType.values().length) r = RagdollTestArena.human(game, 5, 2, 0, 1);
            else {
                Creature c = game.entities.spawnCreature(game.world, Creature.CreatureType.values()[kind],
                        RagdollTestArena.CENTER_X, RagdollTestArena.GROUND + 5, RagdollTestArena.CENTER_Z);
                RagdollTestArena.kill(c, 2, 0, 1);
                r = game.ragdolls.spawn(game, c);
                game.entities.creatures.remove(c);
            }
            for (int tick = 0; !r.settled && tick < 361; tick++) {
                game.ragdolls.update(game, RagdollConstants.FIXED_STEP);
                assertJoints(r);
                for (int p = 0; p < r.pointCount; p++) {
                    assertTrue(Float.isFinite(r.px[p]) && Float.isFinite(r.py[p]) && Float.isFinite(r.pz[p]));
                    assertFalse(solidAt(r.px[p], r.py[p], r.pz[p]), "endpoint in rock: " + kind + "/" + p);
                }
                for (int b = 0; b < r.skeleton.boneCount; b++) {
                    for (int sample = 0; sample <= 10; sample++) {
                        float t = sample / 10f;
                        assertFalse(solidAt(r.jointX[b] + (r.px[b + 1] - r.jointX[b]) * t,
                                r.jointY[b] + (r.py[b + 1] - r.jointY[b]) * t,
                                r.jointZ[b] + (r.pz[b + 1] - r.jointZ[b]) * t),
                                "segment in rock: " + kind + "/" + b + " at " + tick);
                    }
                }
            }
            assertTrue(r.settled, "species " + kind + " did not terminate");
            System.out.println("settle species " + kind + ": " + r.age + " energy " + r.energy);
            assertTrue(r.age < RagdollConstants.SETTLE_TIMEOUT,
                    "ordinary falls must settle from energy, not the hard backstop: " + kind);
        }
    }

    @Test
    void identicalDeathsHaveBitIdenticalFinalPosesAtThirtyAndOneHundredFortyFourFps() {
        Game other = RagdollTestArena.create(770077L);
        Ragdoll a = RagdollTestArena.human(game, 4, 3, 2, -1);
        Ragdoll b = RagdollTestArena.human(other, 4, 3, 2, -1);
        for (int tick = 0; !a.settled && tick < 2000; tick++) game.ragdolls.update(game, 1f / 30);
        for (int tick = 0; !b.settled && tick < 2000; tick++) other.ragdolls.update(other, 1f / 144);
        assertArrayEquals(a.px, b.px);
        assertArrayEquals(a.py, b.py);
        assertArrayEquals(a.pz, b.pz);
        assertArrayEquals(a.pose.boneRotX, b.pose.boneRotX);
        assertArrayEquals(a.pose.boneRotY, b.pose.boneRotY);
        assertArrayEquals(a.pose.boneRotZ, b.pose.boneRotZ);
        assertEquals(Float.floatToIntBits(a.pose.yaw), Float.floatToIntBits(b.pose.yaw));
        assertEquals(Float.floatToIntBits(a.pose.pitch), Float.floatToIntBits(b.pose.pitch));
        assertEquals(Float.floatToIntBits(a.pose.roll), Float.floatToIntBits(b.pose.roll));
    }

    private static void assertJoints(Ragdoll r) {
        BodySkeleton s = r.skeleton;
        org.joml.Vector3f direction = new org.joml.Vector3f();
        for (int b = 0; b < s.boneCount; b++) {
            direction.set(r.px[b + 1] - r.jointX[b], r.py[b + 1] - r.jointY[b],
                    r.pz[b + 1] - r.jointZ[b]);
            assertEquals(s.length[b], direction.length(), 0.0002f, "bone length: " + s.part[b]);
            (s.parent[b] < 0 ? r.orientation : r.worldRotation[s.parent[b]]).transformInverse(direction);
            direction.normalize();
            if (s.joint[b] == BodySkeleton.Joint.HINGE) {
                float angle = (float) Math.atan2(s.restY[b] * direction.z - s.restZ[b] * direction.y,
                        s.restY[b] * direction.y + s.restZ[b] * direction.z);
                assertTrue(angle >= s.minAngle[b] - 0.001f && angle <= s.maxAngle[b] + 0.001f,
                        s.part[b] + " hinge outside limits: " + angle);
                assertEquals(0, direction.x, 0.001f, "hinge left its plane");
            } else {
                float angle = (float) Math.acos(Math.clamp(direction.x * s.restX[b]
                        + direction.y * s.restY[b] + direction.z * s.restZ[b], -1f, 1f));
                assertTrue(angle <= s.maxAngle[b] + 0.001f, s.part[b] + " outside cone: " + angle);
            }
        }
    }

    @Test
    void foldedArmsAndLegsDoNotPassThroughTheTorso() {
        Ragdoll r = RagdollTestArena.human(game, 3, 5, 1, -2);
        org.joml.Vector3f v = new org.joml.Vector3f();
        for (int tick = 0; !r.settled && tick < 361; tick++) {
            game.ragdolls.update(game, RagdollConstants.FIXED_STEP);
            for (int b = 2; b < r.skeleton.boneCount; b++) {
                for (int j = 1; j <= 4; j++) {
                    float t = j / 4f;
                    v.set(r.jointX[b] + (r.px[b + 1] - r.jointX[b]) * t - r.px[0],
                            r.jointY[b] + (r.py[b + 1] - r.jointY[b]) * t - r.py[0],
                            r.jointZ[b] + (r.pz[b + 1] - r.jointZ[b]) * t - r.pz[0]);
                    r.orientation.transformInverse(v);
                    assertFalse(Math.abs(v.x) < r.skeleton.halfX - 0.015f
                                    && Math.abs(v.y) < r.skeleton.halfY - 0.015f
                                    && Math.abs(v.z) < r.skeleton.halfZ - 0.015f,
                            r.skeleton.part[b] + " passed through torso at tick " + tick + ": " + v);
                }
            }
        }
    }

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
        for (int p = 0; p < body.pointCount; p++) {
            assertFalse(solidAt(body.px[p], body.py[p], body.pz[p]),
                    "world-edit recovery must clear the entire chain, point " + p);
        }
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
