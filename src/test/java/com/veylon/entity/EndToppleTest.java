package com.veylon.entity;

import com.veylon.Game;
import com.veylon.entity.Creature.CreatureType;
import org.joml.Matrix3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.9.1 B07: a long piece does not come to rest standing on its end. The box model stood a piece
 * on an end face as stably as it laid one on a side, so a deer's leg that landed on its end, as a
 * grazing deer's legs start, stayed upright; in {@code dismember_species_close} all eight deer leg
 * pieces did. Such a piece is now tipped over its end's edge for a bounded time, and a stubby piece
 * stands where it lands as before.
 */
class EndToppleTest {

    private static final float GROUND = RagdollTestArena.GROUND;
    private static final float CX = RagdollTestArena.CENTER_X;
    private static final float CZ = RagdollTestArena.CENTER_Z;
    private static final float STEP = RagdollConstants.FIXED_STEP;

    private Game game;

    @BeforeEach
    void setUp() {
        game = RagdollTestArena.create(20_261_126L);
        game.fragments.reset();
        game.particles.density = 0f;
    }

    @Test
    void aDeerLegSetDownExactlyOnItsEndFallsOverAndLiesDownNearWhereItStood() {
        BodyFragment leg = standingAlone(CreatureType.DEER, "lower_leg_fl");
        assertTrue(leg.halfY >= BodyFragmentConstants.END_TOPPLE_RATIO * Math.max(leg.halfX, leg.halfZ),
                "precondition: a long piece, its long axis its own y");
        float x = leg.pos.x, z = leg.pos.z;
        settle();
        assertTrue(Math.abs(longAxisUp(leg)) < 0.2f, "it lies down: its long axis is " + longAxisUp(leg) + " up");
        assertEquals(GROUND, leg.pos.y - leg.halfHeight, 0.02f, "on the ground");
        assertTrue(Math.hypot(leg.pos.x - x, leg.pos.z - z) < leg.halfY + 0.2f, "where it fell, not thrown");
        assertTrue(leg.endTopple > 0f && leg.endTopple <= BodyFragmentConstants.END_TOPPLE_SECONDS,
                "tipped for a bounded time: " + leg.endTopple + " s");
    }

    @Test
    void theSameDropTwiceEndsTheSame() {
        BodyFragment first = standingAlone(CreatureType.WOLF, "upper_leg_fl");
        settle();
        float[] a = {first.pos.x, first.pos.y, first.pos.z, first.orientation.x, first.orientation.y,
                first.orientation.z, first.orientation.w};
        setUp();
        BodyFragment second = standingAlone(CreatureType.WOLF, "upper_leg_fl");
        settle();
        float[] b = {second.pos.x, second.pos.y, second.pos.z, second.orientation.x, second.orientation.y,
                second.orientation.z, second.orientation.w};
        assertArrayEquals(a, b, 0f, "deterministic");
        assertTrue(Math.abs(longAxisUp(second)) < 0.2f);
    }

    @Test
    void aStubbyPieceOnItsEndAndALongPieceOnItsSideRestAsTheyLand() {
        BodyFragment stub = standingAlone(CreatureType.THORNHORN, "upper_leg_fl");
        assertTrue(stub.halfY < BodyFragmentConstants.END_TOPPLE_RATIO * Math.max(stub.halfX, stub.halfZ),
                "precondition: a stubby piece");
        settle();
        assertEquals(1f, longAxisUp(stub), 1e-3f, "a stubby piece stands where it was set down, as before");
        assertEquals(0f, stub.endTopple, "and is never tipped");

        setUp();
        BodyFragment lying = standingAlone(CreatureType.DEER, "lower_leg_fl");
        lying.orientation.rotationZ((float) (Math.PI / 2));
        lying.halfWidth = Math.max(lying.halfY, lying.halfZ);
        lying.halfHeight = lying.halfX;
        lying.pos.y = GROUND + lying.halfHeight + 0.001f;
        settle();
        assertEquals(0f, lying.endTopple, "a long piece on its side is never tipped");
        assertTrue(Math.abs(longAxisUp(lying)) < 0.05f, "and lies as it lay");
    }

    /** One piece of {@code type}, alone in the world, set down upright on the arena floor, at rest. */
    private BodyFragment standingAlone(CreatureType type, String name) {
        Creature c = game.entities.spawnCreature(game.world, type, CX, GROUND, CZ);
        game.entities.creatures.remove(c);
        List<BodyFragment> pieces = game.fragments.spawnFromCreature(game, c,
                FragmentAnatomy.of(type).restPose(), CX, GROUND + 0.5f, CZ + 4f, 0.1f);
        BodyFragment piece = pieces.stream().filter(p -> p.definition.name.equals(name)).findFirst().orElseThrow();
        game.fragments.live.removeIf(p -> p != piece);
        piece.orientation.identity();
        piece.vel.zero();
        piece.angularVelocity.zero();
        piece.halfWidth = Math.max(piece.halfX, piece.halfZ);
        piece.halfHeight = piece.halfY;
        piece.pos.set(CX + 0.5f, GROUND + piece.halfHeight + 0.001f, CZ + 0.5f);
        return piece;
    }

    private void settle() {
        for (int i = 0; i < 1200 && game.fragments.liveCount() > 0; i++) {
            game.fragments.update(game, STEP);
        }
        assertEquals(0, game.fragments.liveCount(), "precondition: the piece has settled");
    }

    /** The world-up component of the piece's longest axis. */
    private static float longAxisUp(BodyFragment f) {
        Matrix3f m = f.orientation.get(new Matrix3f());
        if (f.halfY >= f.halfX && f.halfY >= f.halfZ) {
            return m.m11;
        }
        return f.halfX >= f.halfZ ? m.m01 : m.m21;
    }
}
