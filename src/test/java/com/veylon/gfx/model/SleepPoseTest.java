package com.veylon.gfx.model;

import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A sleeper lies on the floor it sleeps on, in every appearance: its body neither sunk through
 * the floor (as the pose once drew every sleeper, out of sight) nor floating above it, with what
 * is strapped to its back pressed under it. Standing, the same body's feet meet that floor, which
 * is where a sleeper's position is.
 */
class SleepPoseTest {
    private static final NpcArchetype[] LOOKS = {NpcArchetype.GUARD, NpcArchetype.SCAVENGER, NpcArchetype.TRADER,
            NpcArchetype.LEADER, NpcArchetype.MEDIC, null};
    /** The body itself, without what an appearance hangs on it. */
    private static final Set<String> BODY = Set.of("torso", "vest", "head", "arm_l", "arm_r", "forearm_l", "forearm_r",
            "leg_l", "leg_r", "shin_l", "shin_r");

    @Test
    void aSleeperRestsOnTheFloorItSleepsOn() {
        EntityModel model = NpcModels.get();
        for (NpcArchetype look : LOOKS) {
            for (boolean trader : new boolean[]{false, true}) {
                float[] standing = extent(model, npc(look, trader, Npc.NpcState.IDLE));
                float[] asleep = extent(model, npc(look, trader, Npc.NpcState.SLEEP));
                String who = look + (trader ? " trader" : "");
                assertEquals(0f, standing[0], .02f, who + ": standing, the feet are on the floor");
                assertTrue(asleep[0] >= -.01f, who + ": asleep, the body does not sink through the floor: " + asleep[0]);
                assertTrue(asleep[0] <= .05f, who + ": asleep, it rests on the floor: " + asleep[0]);
                assertTrue(asleep[1] < .6f, who + ": lying down, not standing: " + asleep[1]);
            }
        }
    }

    private static Npc npc(NpcArchetype look, boolean trader, Npc.NpcState state) {
        Npc n = new Npc(null, "Test");
        n.archetype = look;
        n.isTrader = trader;
        n.state = state;
        return n;
    }

    /** {lowest, highest} height of the body's drawn boxes, relative to the position. */
    private static float[] extent(EntityModel model, Npc n) {
        Animator.poseNpc(model, n, 2.0);
        float[] range = {Float.MAX_VALUE, -Float.MAX_VALUE};
        walk(model.root, new Matrix4f(), range);
        return range;
    }

    /** {@code ModelPart.render}'s transforms, collecting the corners of every visible body box. */
    private static void walk(ModelPart p, Matrix4f parent, float[] range) {
        if (!p.visible) { return; }
        Matrix4f local = new Matrix4f(parent).translate(p.pivotX + p.poseX, p.pivotY + p.poseY, p.pivotZ + p.poseZ)
                .rotateZ(p.rotZ).rotateY(p.rotY).rotateX(p.rotX);
        if (p.scale != 1f) { local.scale(p.scale); }
        if (p.sizeX > 0 && BODY.contains(p.name)) {
            Matrix4f box = new Matrix4f(local).translate(p.boxX, p.boxY, p.boxZ).scale(p.sizeX, p.sizeY, p.sizeZ);
            for (int i = 0; i < 8; i++) {
                Vector3f c = box.transformPosition(new Vector3f((i & 1) == 0 ? -.5f : .5f,
                        (i & 2) == 0 ? -.5f : .5f, (i & 4) == 0 ? -.5f : .5f));
                range[0] = Math.min(range[0], c.y);
                range[1] = Math.max(range[1], c.y);
            }
        }
        for (ModelPart c : p.children) { walk(c, local, range); }
    }
}
