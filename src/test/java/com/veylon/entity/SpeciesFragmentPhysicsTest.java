package com.veylon.entity;

import com.veylon.Game;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An animal blown apart at its own joints: its species' pieces, thrown by a
 * launch rule that has a floor on mass so a wing is not fired like a bullet,
 * landing on floors, stopped by walls and slowed by water exactly as a
 * person's pieces are; and, for an animal that leaves a carcass, exactly one
 * harvest record tied to its torso for as long as that record is in the
 * world.
 */
class SpeciesFragmentPhysicsTest {

    /** A powder keg's power; the strongest blast the game makes. */
    private static final float KEG = 3.8f;
    private static final float GROUND = RagdollTestArena.GROUND;
    private static final float CX = RagdollTestArena.CENTER_X;
    private static final float CZ = RagdollTestArena.CENTER_Z;

    private Game game;
    private final RagdollCollision probe = new RagdollCollision();

    @BeforeEach
    void setUp() {
        game = RagdollTestArena.create(20260926L);
        game.fragments.reset();
        game.particles.density = 0f;
    }

    // ------------------------------------------------------------------
    // The pieces
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(CreatureType.class)
    void everySpeciesComesApartIntoItsOwnPiecesWhereItWasDrawn(CreatureType type) {
        Creature c = standing(type, 37f);
        c.pos.y += 0.5f; // clear of the floor, so no piece is pushed out of it
        FragmentPose pose = game.fragments.deathPose(c);
        List<BodyFragment> pieces = blast(c, pose, 1.2f, KEG);
        FragmentAnatomy anatomy = FragmentAnatomy.of(type);

        assertEquals(anatomy.pieces.size(), pieces.size(), type + " comes apart into its own table's pieces");
        assertEquals(anatomy.pieces.size(), game.fragments.liveCount());
        Quaternionf heading = new Quaternionf().rotationY((float) Math.toRadians(-c.yaw));
        Vector3f drawn = new Vector3f();
        for (int i = 0; i < pieces.size(); i++) {
            BodyFragment f = pieces.get(i);
            assertSame(anatomy.piece(i), f.definition, "piece order is the table's");
            assertSame(BodyFamily.of(type), f.definition.family);
            assertNull(f.piece, "an animal's piece has no person's id");
            assertSame(pose, f.pose, "every piece shares the one pose the body died in");
            // Where the living model drew this piece, turned to the body's heading.
            pose.pieceCentre(f.definition.id, drawn);
            heading.transform(drawn).add(c.pos);
            assertEquals(drawn.x, f.pos.x, 1e-4f, f.definition + " x");
            assertEquals(drawn.y, f.pos.y, 1e-4f, f.definition + " y");
            assertEquals(drawn.z, f.pos.z, 1e-4f, f.definition + " z");
        }
    }

    @ParameterizedTest
    @EnumSource(CreatureType.class)
    void noPieceIsThrownFasterThanTheLaunchFloorAllows(CreatureType type) {
        // Point-blank beside the body: the strongest launch a keg can give.
        Creature c = standing(type, 0f);
        float bx = c.pos.x - 0.3f, by = c.pos.y + type.height * 0.5f, bz = c.pos.z;
        List<BodyFragment> pieces = game.fragments.spawnFromCreature(game, c,
                game.fragments.deathPose(c), bx, by, bz, KEG);
        float reach = KEG * BodyFragmentConstants.FALLOFF_RANGE;
        float ceiling = BodyFragmentConstants.IMPULSE_BASE * KEG / BodyFragmentConstants.MIN_LAUNCH_MASS;
        Vector3f dir = new Vector3f();
        float fastest = 0;
        for (BodyFragment f : pieces) {
            assertTrue(Float.isFinite(f.vel.x) && Float.isFinite(f.vel.y) && Float.isFinite(f.vel.z)
                    && Float.isFinite(f.angularVelocity.length()), f.definition + " launches finite");
            dir.set(f.pos).sub(bx, by, bz);
            float distance = dir.length();
            dir.div(distance);
            float falloff = Math.clamp(1f - distance / reach, BodyFragmentConstants.MIN_FALLOFF, 1f);
            // Scatter is perpendicular to the blast, so the speed along it is the blast's.
            float blastSpeed = f.vel.dot(dir) - BodyFragmentConstants.UPWARD_BIAS * dir.y;
            float massSpeed = BodyFragmentConstants.IMPULSE_BASE * KEG * falloff / f.definition.mass;
            float floorSpeed = BodyFragmentConstants.IMPULSE_BASE * KEG * falloff
                    / BodyFragmentConstants.MIN_LAUNCH_MASS;
            assertEquals(Math.min(massSpeed, floorSpeed), blastSpeed, 1e-3f * Math.max(1f, blastSpeed),
                    f.definition + ": a heavy piece by its mass, a light one no faster than the floor mass");
            assertTrue(blastSpeed <= ceiling + 1e-3f, f.definition + " is under the launch ceiling");
            fastest = Math.max(fastest, f.vel.length());
        }
        assertTrue(fastest < RagdollConstants.MAX_POINT_SPEED - 1f,
                type + ": no piece needs the point-speed clamp, fastest " + fastest);
        System.out.printf(Locale.ROOT, "%-10s point-blank keg: fastest piece %.1f m/s (ceiling %.1f)%n",
                type, fastest, ceiling);
    }

    @Test
    void peopleAreHeavierThanTheLaunchFloorSoTheyFlyAsBefore() {
        for (FragmentPiece p : FragmentAnatomy.humanoid().pieces) {
            assertTrue(p.mass >= BodyFragmentConstants.MIN_LAUNCH_MASS,
                    p + " weighs " + p.mass + " kg; the floor must never change a person's launch");
        }
        assertTrue(BodyFragmentConstants.MAX_ANCHORED_REMAINS < BodyFragmentConstants.MAX_SETTLED_FRAGMENTS,
                "the settled cap always has a piece without a harvest record to remove");
    }

    @Test
    void aBlastCentredOnAPieceStillThrowsItFinitely() {
        Creature c = standing(CreatureType.BIRD, 0f);
        FragmentPose pose = game.fragments.deathPose(c);
        Vector3f at = new Vector3f();
        pose.pieceCentre(0, at).add(c.pos);
        List<BodyFragment> pieces = game.fragments.spawnFromCreature(game, c, pose, at.x, at.y, at.z, KEG);
        for (BodyFragment f : pieces) {
            assertTrue(Float.isFinite(f.vel.lengthSquared()), f.definition + " stays finite");
            assertTrue(f.vel.length() < RagdollConstants.MAX_POINT_SPEED, f.definition + " is under the clamp");
        }
        assertTrue(pieces.getFirst().vel.y > 0, "a piece with no blast direction is thrown up");
    }

    // ------------------------------------------------------------------
    // Floors, walls, water
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(CreatureType.class)
    void piecesLandOnTheFloorNotInItOrAboveIt(CreatureType type) {
        Creature c = standing(type, 23f);
        blast(c, game.fragments.deathPose(c), 1f, KEG);
        settle();
        for (BodyFragment f : game.fragments.settled) {
            assertFalse(probe.blocked(game.world, f.pos.x, f.pos.y, f.pos.z, f.halfWidth, f.halfHeight),
                    f.definition + " must not rest inside the floor");
            assertEquals(GROUND, f.pos.y - f.halfHeight, 0.02f, f.definition + " rests on the floor");
            assertTrue(f.grounded, f.definition + " is supported");
        }
    }

    @ParameterizedTest
    @EnumSource(CreatureType.class)
    void aWallStopsEveryPiece(CreatureType type) {
        // A two-thick wall one step from the animal; the blast drives it into the wall.
        int wallX = (int) CX + 1;
        for (int x = wallX; x <= wallX + 1; x++) {
            for (int y = (int) GROUND; y <= (int) GROUND + 8; y++) {
                for (int z = (int) CZ - 6; z <= (int) CZ + 6; z++) {
                    game.world.setBlock(x, y, z, BlockType.STONE, false);
                }
            }
        }
        Creature c = standing(type, 90f);
        c.pos.x -= 1f;
        game.fragments.spawnFromCreature(game, c, game.fragments.deathPose(c),
                c.pos.x - 1.2f, c.pos.y + type.height * 0.5f, c.pos.z, KEG);
        settle();
        for (BodyFragment f : game.fragments.settled) {
            assertTrue(f.pos.x + f.halfWidth <= wallX + 1e-3f,
                    f.definition + " stays on its side of the wall, at x " + f.pos.x);
            assertFalse(probe.blocked(game.world, f.pos.x, f.pos.y, f.pos.z, f.halfWidth, f.halfHeight),
                    f.definition + " is not inside the wall");
        }
    }

    @ParameterizedTest
    @EnumSource(CreatureType.class)
    void waterSlowsEveryPieceAndHoldsItOnTheBottom(CreatureType type) {
        // A pool four deep: water from y = 36 to 39, stone below.
        int pool = 5;
        for (int x = (int) CX - pool; x <= (int) CX + pool; x++) {
            for (int z = (int) CZ - pool; z <= (int) CZ + pool; z++) {
                for (int y = 36; y < (int) GROUND; y++) {
                    game.world.setBlock(x, y, z, BlockType.WATER, false);
                }
            }
        }
        Creature c = standing(type, 0f);
        c.pos.y = 37f;
        game.fragments.spawnFromCreature(game, c, game.fragments.deathPose(c),
                c.pos.x - 0.6f, c.pos.y - 0.4f, c.pos.z, KEG);
        for (int frame = 0; frame < 1200 && game.fragments.liveCount() > 0; frame++) {
            List<BodyFragment> wet = new ArrayList<>();
            for (BodyFragment f : game.fragments.live) {
                if (probe.inWater(game.world, f.pos.x, f.pos.y, f.pos.z)) {
                    wet.add(f);
                }
            }
            game.fragments.update(game, RagdollConstants.FIXED_STEP);
            for (BodyFragment f : wet) {
                if (!f.settled) {
                    assertTrue(f.vel.y >= RagdollConstants.WATER_MAX_DESCENT - 1e-4f,
                            f.definition + " sinks no faster than water allows: " + f.vel.y);
                }
            }
        }
        assertEquals(0, game.fragments.liveCount(), "every piece comes to rest");
        for (BodyFragment f : game.fragments.settled) {
            assertFalse(probe.blocked(game.world, f.pos.x, f.pos.y, f.pos.z, f.halfWidth, f.halfHeight),
                    f.definition + " is not inside the pool's floor or walls");
            boolean overPool = Math.abs(f.pos.x - CX) < pool - 0.5f && Math.abs(f.pos.z - CZ) < pool - 0.5f;
            if (overPool) {
                assertEquals(36f, f.pos.y - f.halfHeight, 0.02f, f.definition + " rests on the pool bottom");
            }
        }
    }

    // ------------------------------------------------------------------
    // One harvest record per body
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(CreatureType.class)
    void anAnimalThatLeavesACarcassLeavesExactlyOneOnItsTorso(CreatureType type) {
        Creature c = standing(type, 0f);
        c.stuckArrows = 2;
        c.stuckArrowType = ItemType.ARROW;
        List<BodyFragment> pieces = blast(c, game.fragments.deathPose(c), 1f, KEG);

        if (!type.leavesCarcass()) {
            assertTrue(game.entities.carcasses.isEmpty(), type + " is too small to leave a carcass");
            for (BodyFragment f : pieces) {
                assertNull(f.harvest, "so no piece carries one");
            }
            return;
        }
        assertEquals(1, game.entities.carcasses.size(), "one carcass for one body");
        Carcass record = game.entities.carcasses.getFirst();
        BodyFragment torso = pieces.getFirst();
        assertEquals(-1, torso.definition.parent, "precondition: piece 0 is the torso");
        assertTrue(record.fragmented(), "the record belongs to remains, never to a whole body");
        assertSame(torso, record.remains);
        assertSame(record, torso.harvest);
        for (BodyFragment f : pieces.subList(1, pieces.size())) {
            assertNull(f.harvest, f.definition + ": a limb carries nothing");
        }
        assertSame(type, record.type);
        assertEquals(type.meatYield, record.meatLeft, "the species' whole meat yield, once");
        assertEquals(type.hideYield, record.hideLeft, "and its whole hide yield");
        assertEquals(2, record.stuckArrows, "the lodged arrows go with the record");
        assertSame(ItemType.ARROW, record.stuckArrowType);
    }

    @Test
    void theRecordLiesUnderItsTorsoAndIsHarvestableOnlyOnceItLands() {
        Creature c = standing(CreatureType.DEER, 0f);
        blast(c, game.fragments.deathPose(c), 1f, KEG);
        Carcass record = game.entities.carcasses.getFirst();
        BodyFragment torso = record.remains;

        for (int frame = 0; frame < 5; frame++) {
            game.fragments.update(game, RagdollConstants.FIXED_STEP);
        }
        assertFalse(torso.settled, "precondition: the torso is still in the air");
        assertFalse(record.atRest());
        assertEquals(torso.pos.x, record.pos.x, 0f, "the record goes where the torso goes");
        assertEquals(torso.pos.z, record.pos.z, 0f);
        assertEquals(torso.pos.y - torso.halfHeight, record.pos.y, 0f);
        assertNull(game.entities.nearestCarcass(record.pos.x, record.pos.y, record.pos.z, 3f),
                "a body still flying is not a carcass yet, for the player or a scavenger");

        settle();
        assertTrue(record.atRest());
        assertEquals(torso.pos.x, record.pos.x, 0f, "it lies where the torso came to rest");
        assertEquals(torso.pos.z, record.pos.z, 0f);
        assertEquals(GROUND, record.pos.y, 0.02f, "on the ground under it");
        assertSame(record, game.entities.nearestCarcass(torso.pos.x, torso.pos.y, torso.pos.z, 3f));
    }

    @Test
    void theSettledCapNeverTakesATorsoThatCarriesARecord() {
        Creature deer = standing(CreatureType.DEER, 0f);
        blast(deer, game.fragments.deathPose(deer), 1f, KEG);
        Carcass record = game.entities.carcasses.getFirst();
        settle();
        List<BodyFragment> limbs = new ArrayList<>(game.fragments.settled);
        limbs.remove(record.remains);

        int bodies = BodyFragmentConstants.MAX_SETTLED_FRAGMENTS / 10 + 3;
        for (int i = 0; i < bodies; i++) {
            Npc n = game.entities.spawnNpc(game.world, "Villager", CX, GROUND, CZ);
            game.entities.npcs.remove(n);
            game.fragments.spawnFromNpc(game, n, n.pos.x + 1f, n.pos.y + 1f, n.pos.z, KEG);
            game.fragments.settleAll(game);
            assertTrue(game.fragments.settledCount() <= BodyFragmentConstants.MAX_SETTLED_FRAGMENTS);
        }
        assertEquals(BodyFragmentConstants.MAX_SETTLED_FRAGMENTS, game.fragments.settledCount());
        assertTrue(game.fragments.settled.contains(record.remains),
                "the oldest piece of all, the torso, is passed over while its record lasts");
        assertSame(record, record.remains.harvest);
        assertEquals(List.of(record), game.entities.carcasses, "the record itself is untouched");
        for (BodyFragment limb : limbs) {
            assertFalse(game.fragments.settled.contains(limb), limb.definition + ": the limbs go first");
        }
    }

    @Test
    void pastTheCapTheOldestRecordAndItsTorsoLeaveTogether() {
        int over = 3;
        List<Carcass> records = new ArrayList<>();
        for (int i = 0; i < BodyFragmentConstants.MAX_ANCHORED_REMAINS + over; i++) {
            Creature deer = standing(CreatureType.DEER, i * 7f);
            blast(deer, game.fragments.deathPose(deer), 1f, KEG);
            records.add(game.entities.carcasses.getLast());
            if (i % 5 == 0) {
                game.fragments.settleAll(game);
            }
            assertTrue(BodyFragmentSystem.anchoredRemains(game.entities.carcasses)
                    <= BodyFragmentConstants.MAX_ANCHORED_REMAINS);
        }
        assertEquals(BodyFragmentConstants.MAX_ANCHORED_REMAINS,
                BodyFragmentSystem.anchoredRemains(game.entities.carcasses), "the cap is reachable exactly");
        assertEquals(BodyFragmentConstants.MAX_ANCHORED_REMAINS, game.entities.carcasses.size());
        for (int i = 0; i < records.size(); i++) {
            Carcass record = records.get(i);
            BodyFragment torso = record.remains;
            boolean kept = i >= over;
            assertEquals(kept, game.entities.carcasses.contains(record), "record " + i);
            assertEquals(kept, game.fragments.live.contains(torso) || game.fragments.settled.contains(torso),
                    "torso " + i + " leaves with its record, never without it");
            assertEquals(kept, torso.harvest == record, "torso " + i + " link");
        }
    }

    @Test
    void emptyingReleasesTheTorsoAndRottingTakesBoth() {
        Creature skinned = standing(CreatureType.DEER, 0f);
        blast(skinned, game.fragments.deathPose(skinned), 1f, KEG);
        Carcass emptied = game.entities.carcasses.getLast();
        Creature left = standing(CreatureType.WOLF, 0f);
        left.pos.x += 4f;
        blast(left, game.fragments.deathPose(left), 1f, KEG);
        Carcass rotting = game.entities.carcasses.getLast();
        settle();

        emptied.meatLeft = 0;
        emptied.hideLeft = 0;
        game.entities.tickWorldDetritus(game, 1f);
        assertFalse(game.entities.carcasses.contains(emptied), "an emptied carcass leaves the world");
        BodyFragment released = emptied.remains;
        assertTrue(game.fragments.settled.contains(released), "its torso stays to rot like any piece");
        assertNull(released.harvest, "no longer tied to a record");
        assertSame(rotting, rotting.remains.harvest, "the other record is untouched");

        game.entities.tickWorldDetritus(game, 300f);
        assertEquals(rotting.decay, rotting.remains.decay, 1e-3f, "a torso rots on its record's clock");
        assertTrue(rotting.rotten());

        game.entities.tickWorldDetritus(game, RagdollConstants.CORPSE_DECAY);
        assertFalse(game.entities.carcasses.contains(rotting), "the record rots away");
        assertFalse(game.fragments.settled.contains(rotting.remains), "and takes its torso with it");
        assertEquals(0, game.fragments.settledCount(), "every other piece rotted on its own clock");
    }

    @Test
    void aTorsoCarryingARecordOutlastsTheDespawnRadius() {
        Creature c = standing(CreatureType.THORNHORN, 0f);
        blast(c, game.fragments.deathPose(c), 1f, KEG);
        Carcass record = game.entities.carcasses.getFirst();
        settle();
        game.player.pos.set(CX + RagdollConstants.DESPAWN_DISTANCE + 50f, GROUND + 0.1f, CZ);

        game.entities.tickWorldDetritus(game, 1f);
        assertEquals(List.of(record.remains), game.fragments.settled,
                "far away, the limbs are culled like a corpse, the torso stays with its record");
        assertTrue(game.entities.carcasses.contains(record), "a carcass is never culled by distance");
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    /** An animal standing on the arena floor, removed from the creature list like a dying one. */
    private Creature standing(CreatureType type, float yaw) {
        Creature c = game.entities.spawnCreature(game.world, type, CX, GROUND, CZ);
        c.yaw = yaw;
        c.vel.zero();
        game.entities.creatures.remove(c);
        return c;
    }

    /**
     * Blows {@code c} apart from {@code side} blocks towards −x, at its body
     * centre's height, so the pieces fly towards the wider side of the arena.
     */
    private List<BodyFragment> blast(Creature c, FragmentPose pose, float side, float strength) {
        return game.fragments.spawnFromCreature(game, c, pose,
                c.pos.x - side, c.pos.y + c.type.height * 0.5f, c.pos.z, strength);
    }

    private void settle() {
        for (int i = 0; i < 1200 && game.fragments.liveCount() > 0; i++) {
            game.fragments.update(game, 1f / 60f);
        }
        assertEquals(0, game.fragments.liveCount(), "precondition: every piece has settled");
    }
}
