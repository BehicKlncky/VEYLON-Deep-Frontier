package com.veylon.entity;

import com.veylon.Game;
import com.veylon.engine.ParticleSystem;
import com.veylon.entity.BodyFragment.Piece;
import com.veylon.gfx.model.ModelPart;
import com.veylon.gfx.model.NpcModels;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.BlockType;
import org.joml.Matrix3f;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A person blown apart at the joints: ten pieces cut where the model is cut,
 * thrown away from the blast by their mass, landing on the ground rather than
 * in it or above it, and settling, rotting and despawning like a corpse.
 */
class BodyFragmentPhysicsTest {

    /** A powder keg's power; the strongest blast the game makes. */
    private static final float KEG = 3.8f;

    private Game game;
    private final RagdollCollision probe = new RagdollCollision();

    @TempDir
    Path tmp;

    @BeforeEach
    void setUp() {
        game = RagdollTestArena.create(20260919L);
        game.fragments.reset();
    }

    // ------------------------------------------------------------------
    // The cut
    // ------------------------------------------------------------------

    @Test
    void anNpcSplitsIntoExactlyTenPiecesAtItsJoints() {
        Npc n = standing(game, 0f);
        n.archetype = NpcArchetype.GUARD;
        n.raider = true;
        n.isTrader = true;
        n.sick = true;
        n.campIndex = 3;
        List<BodyFragment> pieces = game.fragments.spawnFromNpc(game, n,
                n.pos.x + 2f, n.pos.y + 1f, n.pos.z, KEG);

        assertEquals(10, pieces.size(), "one body is ten pieces");
        assertEquals(10, game.fragments.liveCount());
        String[][] table = {
                {"TORSO", "torso", "neck,arm_l,arm_r"},
                {"HEAD", "neck", ""},
                {"UPPER_ARM_L", "arm_l", "forearm_l"},
                {"UPPER_ARM_R", "arm_r", "forearm_r"},
                {"FOREARM_L", "forearm_l", ""},
                {"FOREARM_R", "forearm_r", ""},
                {"THIGH_L", "leg_l", "shin_l"},
                {"THIGH_R", "leg_r", "shin_r"},
                {"SHIN_L", "shin_l", ""},
                {"SHIN_R", "shin_r", ""},
        };
        Vector3f expected = new Vector3f();
        for (int i = 0; i < table.length; i++) {
            BodyFragment f = pieces.get(i);
            assertEquals(table[i][0], f.piece.name(), "piece order is the A6 table");
            assertEquals(table[i][1], f.rootPart, f.piece + " root part");
            assertEquals(table[i][2], String.join(",", f.piece.excludedParts),
                    f.piece + " leaves these children to other pieces");
            assertEquals(f.piece.pivotX, f.restPivotX);
            assertEquals(f.piece.pivotY, f.restPivotY);
            assertEquals(f.piece.pivotZ, f.restPivotZ);

            assertNotSame(pieces.get((i + 1) % 10).appearance, f.appearance,
                    "every piece carries its own copy of the appearance");
            assertSame(NpcArchetype.GUARD, f.appearance.archetype);
            assertTrue(f.appearance.raider && f.appearance.trader && f.appearance.sick);
            assertEquals(3, f.appearance.campIndex);

            // Facing the model's own heading, every piece starts where the
            // living model drew it.
            expected.set(n.pos).add(f.restCentreX, f.restCentreY, f.restCentreZ);
            assertEquals(expected.x, f.pos.x, 1e-5f, f.piece + " x");
            assertEquals(expected.y, f.pos.y, 1e-5f, f.piece + " y");
            assertEquals(expected.z, f.pos.z, 1e-5f, f.piece + " z");
            assertEquals(f.piece.halfWidth, f.halfWidth, 1e-6f,
                    "at rest orientation the sweep pair is max(sx, sz) / 2");
            assertEquals(f.piece.halfHeight, f.halfHeight, 1e-6f, "and sy / 2");
        }
        assertFalse(pieces.getFirst().piece.severed, "the torso is what the joints are cut from");
    }

    @Test
    void piecesStartWhereTheLivingModelDrewThemWhateverTheHeading() {
        Npc n = standing(game, 0f);
        n.yaw = 117f;
        List<BodyFragment> pieces = game.fragments.spawnFromNpc(game, n,
                n.pos.x, n.pos.y - 3f, n.pos.z, 0.5f);
        // Exactly the renderer's NPC transform: translate(pos).rotateY(-yaw).
        org.joml.Matrix4f model = new org.joml.Matrix4f().translate(n.pos)
                .rotateY((float) Math.toRadians(-n.yaw));
        Vector3f want = new Vector3f();
        Vector3f got = new Vector3f();
        for (BodyFragment f : pieces) {
            model.transformPosition(f.restCentreX, f.restCentreY, f.restCentreZ, want);
            assertEquals(want.x, f.pos.x, 1e-5f, f.piece + " centre x");
            assertEquals(want.y, f.pos.y, 1e-5f, f.piece + " centre y");
            assertEquals(want.z, f.pos.z, 1e-5f, f.piece + " centre z");
            model.transformPosition(f.restPivotX, f.restPivotY, f.restPivotZ, want);
            f.modelToWorld(f.restPivotX, f.restPivotY, f.restPivotZ, got);
            assertEquals(want.x, got.x, 1e-5f, f.piece + " joint x");
            assertEquals(want.y, got.y, 1e-5f, f.piece + " joint y");
            assertEquals(want.z, got.z, 1e-5f, f.piece + " joint z");
        }
    }

    @Test
    void thePieceTableIsReadFromTheNpcModel() {
        ModelPart root = NpcModels.get().root;
        List<String> roots = new ArrayList<>();
        for (Piece p : Piece.values()) {
            roots.add(p.rootPart);
        }
        for (Piece p : Piece.values()) {
            List<ModelPart> chain = chainTo(root, p.rootPart);
            assertNotNull(chain, p + " names a real model part: " + p.rootPart);
            Vector3f pivot = pivotOf(chain);
            assertEquals(pivot.x, p.pivotX, 1e-6f, p + " pivot x");
            assertEquals(pivot.y, p.pivotY, 1e-6f, p + " pivot y");
            assertEquals(pivot.z, p.pivotZ, 1e-6f, p + " pivot z");

            List<ModelPart> boxChain = chainTo(root, p.boxPart);
            assertNotNull(boxChain, p + " names a real box part: " + p.boxPart);
            ModelPart box = boxChain.getLast();
            Vector3f boxPivot = pivotOf(boxChain);
            assertEquals(boxPivot.x + box.boxX, p.centreX, 1e-6f, p + " box centre x");
            assertEquals(boxPivot.y + box.boxY, p.centreY, 1e-6f, p + " box centre y");
            assertEquals(boxPivot.z + box.boxZ, p.centreZ, 1e-6f, p + " box centre z");
            assertEquals(box.sizeX, p.halfX * 2f, 1e-6f, p + " box width");
            assertEquals(box.sizeY, p.halfY * 2f, 1e-6f, p + " box height");
            assertEquals(box.sizeZ, p.halfZ * 2f, 1e-6f, p + " box depth");
            assertEquals(Math.max(box.sizeX, box.sizeZ) / 2f, p.halfWidth, 1e-6f);
            assertEquals(box.sizeY / 2f, p.halfHeight, 1e-6f);
            assertEquals(box.sizeX * box.sizeY * box.sizeZ * BodyFragmentConstants.DENSITY,
                    p.mass, 1e-4f, p + " mass is proportional to box volume");

            // The excluded children are exactly the piece roots directly below.
            List<String> below = new ArrayList<>();
            collectPieceRoots(chain.getLast(), roots, below);
            assertEquals(below, p.excludedParts, p + " exclusions");
        }
    }

    // ------------------------------------------------------------------
    // Launch
    // ------------------------------------------------------------------

    @Test
    void piecesFlyAwayFromTheBlastCentre() {
        Npc n = standing(game, 27f);
        List<BodyFragment> near = game.fragments.spawnFromNpc(game, n,
                n.pos.x + 1.5f, n.pos.y + 1f, n.pos.z, KEG);
        List<BodyFragment> far = game.fragments.spawnFromNpc(game, n,
                n.pos.x + 3f, n.pos.y + 1f, n.pos.z, KEG);
        for (int i = 0; i < near.size(); i++) {
            BodyFragment a = near.get(i);
            BodyFragment b = far.get(i);
            assertTrue(a.vel.x < 0, a.piece + " must leave away from a blast on its +X side: " + a.vel);
            assertTrue(b.vel.x < 0, b.piece + " must leave away from a blast on its +X side: " + b.vel);
            assertTrue(horizontal(a) > horizontal(b),
                    a.piece + ": a nearer blast must throw it harder, " + horizontal(a)
                            + " vs " + horizontal(b));
            assertTrue(a.vel.length() <= RagdollConstants.MAX_POINT_SPEED + 1e-3f);
            assertTrue(a.angularVelocity.length() <= RagdollConstants.MAX_ANGULAR_SPEED + 1e-3f);
            assertTrue(a.angularVelocity.lengthSquared() > 0.01f, a.piece + " leaves spinning");
        }
    }

    @Test
    void lighterPiecesAreThrownFasterThanTheTorso() {
        Npc n = standing(game, 0f);
        List<BodyFragment> pieces = game.fragments.spawnFromNpc(game, n,
                n.pos.x + 2f, n.pos.y + 1.1f, n.pos.z, KEG);
        BodyFragment torso = pieces.getFirst();
        assertSame(Piece.TORSO, torso.piece);
        for (BodyFragment f : pieces.subList(1, pieces.size())) {
            assertTrue(f.inverseMass > torso.inverseMass, f.piece + " is lighter than the torso");
            assertTrue(horizontal(f) > horizontal(torso),
                    f.piece + " must be thrown faster than the torso: " + horizontal(f)
                            + " vs " + horizontal(torso));
        }
        System.out.printf(Locale.ROOT, "launch at 2 m from strength %.1f:%n", KEG);
        for (BodyFragment f : pieces) {
            System.out.printf(Locale.ROOT, "  %-12s mass %6.2f  horizontal %6.2f m/s  up %5.2f m/s  spin %5.2f rad/s%n",
                    f.piece, f.piece.mass, horizontal(f), f.vel.y, f.angularVelocity.length());
        }
    }

    @Test
    void bloodMarksEverySeveredJointAndTrailsFastPieces() {
        game.particles.density = 1f;
        game.particles.count = 0;
        Npc n = standing(game, 0f);
        game.fragments.spawnFromNpc(game, n, n.pos.x + 1.5f, n.pos.y + 1f, n.pos.z, KEG);
        int severed = 0;
        for (Piece p : Piece.values()) {
            if (p.severed) {
                severed++;
            }
        }
        assertEquals(9, severed, "neck, two shoulders, two elbows, two hips, two knees");
        int burst = game.particles.count;
        assertTrue(burst >= severed * ParticleSystem.BURST_MIST
                        && burst <= severed * (ParticleSystem.BURST_DROPS + ParticleSystem.BURST_MIST),
                "one burst per severed joint, got " + burst + " particles");
        for (int i = 0; i < 20; i++) {
            game.fragments.update(game, RagdollConstants.FIXED_STEP);
        }
        assertTrue(game.particles.count > burst, "fast pieces drip as they fly");

        Game quiet = RagdollTestArena.create(20260919L);
        quiet.particles.density = 0f;
        Npc m = standing(quiet, 0f);
        quiet.fragments.spawnFromNpc(quiet, m, m.pos.x + 1.5f, m.pos.y + 1f, m.pos.z, KEG);
        for (int i = 0; i < 20; i++) {
            quiet.fragments.update(quiet, RagdollConstants.FIXED_STEP);
        }
        assertEquals(0, quiet.particles.count, "a zero particle density emits no blood at all");
    }

    // ------------------------------------------------------------------
    // Flight and rest
    // ------------------------------------------------------------------

    @Test
    void piecesFallUnderGravityCollideWithTheFloorAndSettle() {
        Npc n = standing(game, 27f);
        List<BodyFragment> pieces = game.fragments.spawnFromNpc(game, n,
                n.pos.x + 1.5f, n.pos.y + 0.5f, n.pos.z + 0.5f, KEG);
        float[] start = new float[pieces.size()];
        float[] peak = new float[pieces.size()];
        for (int i = 0; i < pieces.size(); i++) {
            start[i] = pieces.get(i).pos.y;
        }
        int frames = 0;
        while (game.fragments.liveCount() > 0 && frames < 600) {
            game.fragments.update(game, 1f / 60f);
            frames++;
            for (int i = 0; i < pieces.size(); i++) {
                BodyFragment f = pieces.get(i);
                assertClearOfTheWorld(f, "frame " + frames);
                assertTrue(lowestCorner(f) >= RagdollTestArena.GROUND - 1e-3f,
                        f.piece + " dipped into the floor to " + lowestCorner(f) + " at frame " + frames);
                peak[i] = Math.max(peak[i], f.pos.y - start[i]);
            }
        }
        assertEquals(0, game.fragments.liveCount(), "every piece settles within 10 s");
        assertEquals(10, game.fragments.settledCount());

        System.out.println("fragment settle times (strength 3.8, blast 1.6 m off the hip):");
        for (int i = 0; i < pieces.size(); i++) {
            BodyFragment f = pieces.get(i);
            assertTrue(f.settled);
            assertTrue(f.age < BodyFragmentConstants.SETTLE_TIMEOUT,
                    f.piece + " must come to rest by itself, not by the timeout");
            assertTrue(peak[i] > 0.05f, f.piece + " was thrown up before falling: " + peak[i]);
            assertEquals(RagdollTestArena.GROUND, f.pos.y - f.halfHeight, 0.01f,
                    f.piece + " rests on the floor, neither sunk nor hovering");
            assertEquals(RagdollTestArena.GROUND, lowestCorner(f), 0.01f,
                    f.piece + "'s lowest drawn corner touches the floor");
            assertTrue(uprightAxis(f) > 0.95f,
                    f.piece + " must topple onto a face, not rest on an edge: " + uprightAxis(f));
            assertClearOfTheWorld(f, "at rest");
            System.out.printf(Locale.ROOT, "  %-12s %5.2f s  peak +%4.2f m  rests %5.2f m from the body%n",
                    f.piece, f.age, peak[i], (float) Math.hypot(f.pos.x - n.pos.x, f.pos.z - n.pos.z));
        }
    }

    @Test
    void piecesDoNotTunnelThroughAOneBlockWall() {
        int wallX = (int) RagdollTestArena.CENTER_X - 3;
        for (int z = -6; z <= 6; z++) {
            for (int y = 0; y < 8; y++) {
                game.world.setBlock(wallX, (int) RagdollTestArena.GROUND + y,
                        (int) RagdollTestArena.CENTER_Z + z, BlockType.STONE, false);
            }
        }
        Npc n = standing(game, 0f);
        List<BodyFragment> pieces = game.fragments.spawnFromNpc(game, n,
                n.pos.x + 0.8f, n.pos.y + 1.1f, n.pos.z, 12f);
        float fastest = 0;
        for (BodyFragment f : pieces) {
            fastest = Math.max(fastest, -f.vel.x);
        }
        assertTrue(fastest > 30f, "precondition: the blast drives pieces at the wall hard: " + fastest);
        for (int frame = 0; frame < 600 && game.fragments.liveCount() > 0; frame++) {
            game.fragments.update(game, frame % 3 == 0 ? 0.25f : 1f / 60f);
            for (BodyFragment f : pieces) {
                assertTrue(f.pos.x - f.halfWidth >= wallX + 1 - 1e-3f,
                        f.piece + " passed into or through the wall to x=" + f.pos.x);
                assertClearOfTheWorld(f, "frame " + frame);
            }
        }
        assertEquals(0, game.fragments.liveCount(), "the pieces still settle");
    }

    @Test
    void spawnIsDeterministicForTheSameInputs() {
        Game other = RagdollTestArena.create(20260919L);
        Npc a = standing(game, 41f);
        Npc b = standing(other, 41f);
        game.fragments.spawnFromNpc(game, a, a.pos.x - 1.2f, a.pos.y + 0.7f, a.pos.z + 0.9f, KEG);
        other.fragments.spawnFromNpc(other, b, b.pos.x - 1.2f, b.pos.y + 0.7f, b.pos.z + 0.9f, KEG);
        // The same world time delivered at 30 and at 144 frames per second.
        for (int i = 0; i < 2000 && game.fragments.liveCount() > 0; i++) {
            game.fragments.update(game, 1f / 30f);
        }
        for (int i = 0; i < 4000 && other.fragments.liveCount() > 0; i++) {
            other.fragments.update(other, 1f / 144f);
        }
        assertEquals(10, game.fragments.settledCount());
        assertEquals(10, other.fragments.settledCount());
        for (int i = 0; i < 10; i++) {
            BodyFragment x = byPiece(game, Piece.values()[i]);
            BodyFragment y = byPiece(other, Piece.values()[i]);
            String what = x.piece + " came to rest differently";
            assertEquals(bits(x.pos.x), bits(y.pos.x), what);
            assertEquals(bits(x.pos.y), bits(y.pos.y), what);
            assertEquals(bits(x.pos.z), bits(y.pos.z), what);
            assertEquals(bits(x.orientation.x), bits(y.orientation.x), what);
            assertEquals(bits(x.orientation.y), bits(y.orientation.y), what);
            assertEquals(bits(x.orientation.z), bits(y.orientation.z), what);
            assertEquals(bits(x.orientation.w), bits(y.orientation.w), what);
            assertEquals(bits(x.age), bits(y.age), what);
        }
    }

    @Test
    void aPieceSpawnedInsideGeometryIsPushedFreeAndStaysFree() {
        // Hugging a wall, so the right arm is in it, and sunk shin-deep in the floor.
        int wallX = (int) RagdollTestArena.CENTER_X + 1;
        for (int z = -2; z <= 2; z++) {
            for (int y = 0; y < 3; y++) {
                game.world.setBlock(wallX, (int) RagdollTestArena.GROUND + y,
                        (int) RagdollTestArena.CENTER_Z + z, BlockType.STONE, false);
            }
        }
        Npc n = game.entities.spawnNpc(game.world, "Villager", RagdollTestArena.CENTER_X + 0.2f,
                RagdollTestArena.GROUND - 0.3f, RagdollTestArena.CENTER_Z);
        game.entities.npcs.remove(n);
        n.vel.zero();
        assertTrue(probe.blocked(game.world, n.pos.x + 0.30f, n.pos.y + 1.29f, n.pos.z,
                        Piece.UPPER_ARM_R.halfWidth, Piece.UPPER_ARM_R.halfHeight),
                "precondition: the right upper arm starts inside the wall");
        List<BodyFragment> pieces = game.fragments.spawnFromNpc(game, n,
                n.pos.x - 1f, n.pos.y + 1f, n.pos.z, 2f);
        for (BodyFragment f : pieces) {
            assertClearOfTheWorld(f, "after spawn");
        }
        // Close the world around one piece mid-flight, as a placed block can.
        game.fragments.update(game, RagdollConstants.FIXED_STEP);
        BodyFragment head = pieces.get(Piece.HEAD.ordinal());
        game.world.setBlock((int) Math.floor(head.pos.x), (int) Math.floor(head.pos.y),
                (int) Math.floor(head.pos.z), BlockType.STONE, false);
        for (int frame = 0; frame < 600 && game.fragments.liveCount() > 0; frame++) {
            game.fragments.update(game, RagdollConstants.FIXED_STEP);
            for (BodyFragment f : pieces) {
                assertClearOfTheWorld(f, "frame " + frame);
            }
        }
        assertEquals(0, game.fragments.liveCount());
    }

    @Test
    void aStalledOrPausedFrameCannotReplayOrAdvancePieces() {
        Npc n = standing(game, 0f);
        List<BodyFragment> pieces = game.fragments.spawnFromNpc(game, n,
                n.pos.x + 1.5f, n.pos.y + 1f, n.pos.z, KEG);
        game.fragments.update(game, 30f);
        assertTrue(game.fragments.stepsLastUpdate <= RagdollConstants.MAX_STEPS_PER_FRAME,
                "a 30-second stall ran " + game.fragments.stepsLastUpdate + " steps");
        BodyFragment f = pieces.get(Piece.FOREARM_L.ordinal());
        float x = f.pos.x;
        game.fragments.update(game, 0f);
        game.fragments.update(game, -1f);
        game.fragments.update(game, Float.NaN);
        assertEquals(x, f.pos.x, 0f, "a piece advanced without a positive time step");
    }

    // ------------------------------------------------------------------
    // Housekeeping
    // ------------------------------------------------------------------

    @Test
    void liveAndSettledCapsCannotBeExceeded() {
        game.particles.density = 0f;
        int bodies = BodyFragmentConstants.MAX_LIVE_FRAGMENTS / 10 + 2;
        for (int i = 0; i < bodies; i++) {
            Npc n = standing(game, i * 30f);
            game.fragments.spawnFromNpc(game, n, n.pos.x + 1f, n.pos.y + 1f, n.pos.z, KEG);
            assertTrue(game.fragments.liveCount() <= BodyFragmentConstants.MAX_LIVE_FRAGMENTS,
                    "live pieces exceeded their cap: " + game.fragments.liveCount());
        }
        assertEquals(BodyFragmentConstants.MAX_LIVE_FRAGMENTS, game.fragments.liveCount(),
                "the live cap is reachable exactly");
        assertEquals(bodies * 10L, game.fragments.totalSpawned);
        assertEquals(bodies * 10 - BodyFragmentConstants.MAX_LIVE_FRAGMENTS,
                game.fragments.settledCount(),
                "pieces pushed over the cap settle rather than disappearing");
        for (BodyFragment f : game.fragments.settled) {
            assertEquals(RagdollTestArena.GROUND, f.pos.y - f.halfHeight, 0.01f,
                    "a piece settled by the cap is laid on the ground, not frozen in the air");
        }

        game.fragments.settleAll(game);
        BodyFragment oldest = game.fragments.settled.getFirst();
        int more = BodyFragmentConstants.MAX_SETTLED_FRAGMENTS / 10 + 3;
        for (int i = 0; i < more; i++) {
            Npc n = standing(game, i * 11f);
            game.fragments.spawnFromNpc(game, n, n.pos.x + 1f, n.pos.y + 1f, n.pos.z, KEG);
            game.fragments.settleAll(game);
            assertTrue(game.fragments.settledCount() <= BodyFragmentConstants.MAX_SETTLED_FRAGMENTS,
                    "settled pieces exceeded their cap: " + game.fragments.settledCount());
        }
        assertEquals(BodyFragmentConstants.MAX_SETTLED_FRAGMENTS, game.fragments.settledCount(),
                "the settled cap is reachable exactly");
        assertFalse(game.fragments.settled.contains(oldest), "the oldest piece goes first");
        assertEquals(0, game.fragments.liveCount());
    }

    @Test
    void settledPiecesDecayAndAreRemoved() {
        Npc n = standing(game, 0f);
        game.fragments.spawnFromNpc(game, n, n.pos.x + 1f, n.pos.y + 1f, n.pos.z, KEG);
        settle(game);
        assertEquals(10, game.fragments.settledCount());
        for (BodyFragment f : game.fragments.settled) {
            assertEquals(RagdollConstants.CORPSE_DECAY, f.decay, 1e-3f, "pieces rot on the corpse clock");
        }

        game.entities.tickWorldDetritus(game, RagdollConstants.CORPSE_DECAY * 0.5f);
        assertEquals(10, game.fragments.settledCount(), "half-way through, the pieces are still there");
        game.entities.tickWorldDetritus(game, RagdollConstants.CORPSE_DECAY * 0.5f + 1f);
        assertEquals(0, game.fragments.settledCount(), "pieces must rot away like a corpse");
    }

    @Test
    void farPiecesAreCulled() {
        Npc n = standing(game, 0f);
        game.fragments.spawnFromNpc(game, n, n.pos.x + 1f, n.pos.y + 1f, n.pos.z, KEG);
        settle(game);
        assertEquals(10, game.fragments.settledCount());
        walkAway(game);
        game.entities.tickWorldDetritus(game, 1f);
        assertEquals(0, game.fragments.settledCount(),
                "pieces must not accumulate behind a player walking away");

        // A piece still flying when the player is already that far away is
        // settled at once rather than simulated.
        Game flying = RagdollTestArena.create(20260919L);
        Npc m = standing(flying, 0f);
        flying.fragments.spawnFromNpc(flying, m, m.pos.x + 1f, m.pos.y + 1f, m.pos.z, KEG);
        walkAway(flying);
        flying.fragments.update(flying, RagdollConstants.FIXED_STEP);
        assertEquals(0, flying.fragments.liveCount(), "far pieces stop simulating");
        flying.entities.tickWorldDetritus(flying, 1f);
        assertEquals(0, flying.fragments.settledCount(), "and are culled like corpses");
    }

    @Test
    void aSaveFreezesPiecesInFlightInsteadOfDroppingThem() {
        Npc n = standing(game, 0f);
        game.fragments.spawnFromNpc(game, n, n.pos.x + 1f, n.pos.y + 1f, n.pos.z, KEG);
        game.fragments.update(game, 0.05f);
        assertEquals(10, game.fragments.liveCount(), "precondition: the pieces are in the air");
        assertTrue(SaveSystem.save(game, tmp.resolve("fragments.sav")));
        assertEquals(0, game.fragments.liveCount(), "a save settles every piece in flight");
        assertEquals(10, game.fragments.settledCount(), "and keeps them");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** A person standing on the arena floor, removed from the NPC list like a dying one. */
    private static Npc standing(Game g, float yaw) {
        Npc n = g.entities.spawnNpc(g.world, "Villager", RagdollTestArena.CENTER_X,
                RagdollTestArena.GROUND, RagdollTestArena.CENTER_Z);
        n.yaw = yaw;
        n.vel.zero();
        g.entities.npcs.remove(n);
        return n;
    }

    private static void settle(Game g) {
        for (int i = 0; i < 1200 && g.fragments.liveCount() > 0; i++) {
            g.fragments.update(g, 1f / 60f);
        }
        assertEquals(0, g.fragments.liveCount(), "precondition: every piece has settled");
    }

    private static void walkAway(Game g) {
        g.player.pos.set(RagdollTestArena.CENTER_X + RagdollConstants.DESPAWN_DISTANCE + 50f,
                RagdollTestArena.GROUND + 0.1f, RagdollTestArena.CENTER_Z);
    }

    private static BodyFragment byPiece(Game g, Piece piece) {
        for (BodyFragment f : g.fragments.settled) {
            if (f.piece == piece) {
                return f;
            }
        }
        throw new AssertionError("no settled " + piece);
    }

    private static float horizontal(BodyFragment f) {
        return (float) Math.hypot(f.vel.x, f.vel.z);
    }

    private static int bits(float v) {
        return Float.floatToIntBits(v);
    }

    /** Height of the lowest corner of the turned box, as the renderer draws it. */
    private static float lowestCorner(BodyFragment f) {
        Matrix3f m = f.orientation.get(new Matrix3f());
        return f.pos.y - (Math.abs(m.m01) * f.halfX + Math.abs(m.m11) * f.halfY
                + Math.abs(m.m21) * f.halfZ);
    }

    /** How vertical the piece's most vertical axis is; 1 when it lies square on a face. */
    private static float uprightAxis(BodyFragment f) {
        Matrix3f m = f.orientation.get(new Matrix3f());
        return Math.max(Math.abs(m.m01), Math.max(Math.abs(m.m11), Math.abs(m.m21)));
    }

    private void assertClearOfTheWorld(BodyFragment f, String when) {
        assertTrue(Float.isFinite(f.pos.x) && Float.isFinite(f.pos.y) && Float.isFinite(f.pos.z),
                f.piece + " left the number line " + when);
        assertFalse(probe.blocked(game.world, f.pos.x, f.pos.y, f.pos.z, f.halfWidth, f.halfHeight),
                f.piece + " is inside a solid voxel " + when + " at " + f.pos);
    }

    private static List<ModelPart> chainTo(ModelPart from, String name) {
        if (from.name.equals(name)) {
            List<ModelPart> chain = new ArrayList<>();
            chain.add(from);
            return chain;
        }
        for (ModelPart child : from.children) {
            List<ModelPart> chain = chainTo(child, name);
            if (chain != null) {
                chain.addFirst(from);
                return chain;
            }
        }
        return null;
    }

    private static Vector3f pivotOf(List<ModelPart> chain) {
        Vector3f p = new Vector3f();
        for (ModelPart part : chain) {
            p.add(part.pivotX, part.pivotY, part.pivotZ);
        }
        return p;
    }

    private static void collectPieceRoots(ModelPart part, List<String> roots, List<String> out) {
        for (ModelPart child : part.children) {
            if (roots.contains(child.name)) {
                out.add(child.name);
            } else {
                collectPieceRoots(child, roots, out);
            }
        }
    }
}
