package com.veylon.gfx;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.gfx.model.BodyPosing;
import com.veylon.gfx.model.EntityModel;
import com.veylon.gfx.model.HeldItemModels;
import com.veylon.gfx.model.ModelPart;
import com.veylon.gfx.model.NpcModels;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.World;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.9.1 B04: parts built at an angle keep it through every pose. The quiver, the slung bow and the
 * slung gun hang across a person's back, and a held bow's tips and a bomb's fuse are bent; each
 * angle used to be set on the part once at build time, and the reset every pose starts from set
 * it back to zero, so they were drawn straight.
 */
class RestAngleTest {

    private static final float EPS = 1e-6f;

    @Test
    void slungGearHangsAtItsAngleThroughEveryPoseOfThePeopleWhoCarryIt() {
        Game game = new Game();
        game.newWorld(20_261_126L, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        Npc archer = person(game, NpcArchetype.ARCHER);
        Npc powderman = person(game, NpcArchetype.POWDERMAN);
        Npc guard = person(game, NpcArchetype.GUARD);
        Matrix4f frame = new Matrix4f();
        for (int i = 0; i < 12; i++) {
            double time = 40.0 + i * 0.37;
            archer.bobPhase = i * 0.5f; // walking and standing alike
            archer.vel.set(i % 2 == 0 ? 0f : 2.5f, 0f, 0f);
            BodyPosing.npc(archer, time, frame);
            EntityModel model = NpcModels.get();
            assertTrue(model.part("quiver").visible, "an archer wears a quiver");
            assertEquals(-0.35f, model.part("quiver").rotZ, EPS, "the quiver hangs angled, pose " + i);
            assertEquals(-0.55f, model.part("slungBow").rotZ, EPS, "and the bow across the back");
            assertTiltedFromSpine(model, "quiver", 0.35f);
            assertTiltedFromSpine(model, "slungBow", 0.55f);

            BodyPosing.npc(powderman, time, frame);
            assertTrue(model.part("slungGun").visible);
            assertEquals(0.5f, model.part("slungGun").rotZ, EPS, "the long gun hangs angled, pose " + i);
            assertTiltedFromSpine(model, "slungGun", 0.5f);

            BodyPosing.npc(guard, time, frame); // the shared model is posed for someone else between
        }
    }

    @Test
    void aHeldBowsTipsAndABombsFuseKeepTheirBendAfterEveryReset() {
        for (int frame = 0; frame < 3; frame++) {
            EntityModel bow = HeldItemModels.of(ItemType.PRIMITIVE_BOW);
            bow.resetPose(); // what the renderer does before drawing the held item, every frame
            assertEquals(0.5f, bow.part("tipTop").rotX, EPS);
            assertEquals(-0.5f, bow.part("tipBot").rotX, EPS);
            EntityModel bomb = HeldItemModels.of(ItemType.SCRAP_BOMB);
            bomb.resetPose();
            assertEquals(0.5f, bomb.part("fuse").rotZ, EPS);
        }
    }

    @Test
    void aPoseStillWritesItsOwnAnglesOverARestAngle() {
        ModelPart part = new ModelPart("slung").rest(0.1f, -0.2f, 0.3f);
        part.rotX = 1f;
        part.rotY = 1f;
        part.rotZ = 1f;
        part.poseY = 0.4f;
        part.scale = 0.5f;
        part.resetPose();
        assertEquals(0.1f, part.rotX, EPS);
        assertEquals(-0.2f, part.rotY, EPS);
        assertEquals(0.3f, part.rotZ, EPS);
        assertEquals(0f, part.poseY, EPS);
        assertEquals(1f, part.scale, EPS);
        assertEquals(0f, new ModelPart("plain").rotZ, EPS, "a part built without one rests straight");
    }

    private static Npc person(Game game, NpcArchetype archetype) {
        Npc n = game.entities.spawnNpc(game.world, archetype.displayName, 300.5f, 60f, 300.5f);
        n.archetype = archetype;
        return n;
    }

    /**
     * The part's long axis, as drawn in its parent's (the torso's) frame, leans from the spine by
     * {@code angle} radians: its rotations applied the way {@code ModelPart.render} applies them.
     */
    private static void assertTiltedFromSpine(EntityModel model, String name, float angle) {
        ModelPart p = model.part(name);
        Vector3f axis = new Matrix4f().rotateZ(p.rotZ).rotateY(p.rotY).rotateX(p.rotX)
                .transformDirection(new Vector3f(0, 1, 0)).normalize();
        float tilt = (float) Math.acos(Math.clamp(axis.y, -1f, 1f));
        assertEquals(angle, tilt, 1e-3f, name + " leans " + tilt + " rad from the spine");
    }
}
