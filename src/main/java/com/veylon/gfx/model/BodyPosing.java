package com.veylon.gfx.model;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BodyPose;
import com.veylon.entity.BodySkeleton;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.HumanCorpse;
import com.veylon.entity.Npc;
import com.veylon.entity.Ragdoll;
import org.joml.Matrix4f;

/**
 * The one way every kind of body is posed for drawing: a living animal or
 * person, a falling ragdoll, a corpse, a carcass, a piece of a body blown
 * apart. Each call resets the body's shared model, puts on its look and pose,
 * writes the frame its part tree is drawn in, and returns the part to draw
 * from. The renderer draws that part in that frame; the flames on a burning
 * body are found on the same posed parts ({@link FlameAnchors}), so they can
 * never sit on a pose other than the one on screen.
 *
 * <p>CPU only, main thread only, like the models. The shared models keep the
 * last pose written; the next call resets them.
 */
public final class BodyPosing {

    private BodyPosing() {
    }

    /** A living animal as drawn at {@code time} (the renderer's animation clock). */
    public static ModelPart creature(Creature c, double time, Matrix4f frame) {
        EntityModel m = CreatureModels.of(c.type);
        Animator.poseCreature(m, c, time);
        frame.identity().translate(c.pos.x, c.pos.y, c.pos.z).rotateY((float) Math.toRadians(-c.yaw));
        return m.root;
    }

    /** A living person as drawn at {@code time}, in their own look. */
    public static ModelPart npc(Npc n, double time, Matrix4f frame) {
        EntityModel m = NpcModels.get();
        Animator.poseNpc(m, n, time);
        frame.identity().translate(n.pos.x, n.pos.y, n.pos.z).rotateY((float) Math.toRadians(-n.yaw));
        return m.root;
    }

    /** A body still falling, hung off its torso point. */
    public static ModelPart ragdoll(Ragdoll r, Matrix4f frame) {
        float x = r.px[Ragdoll.TORSO], y = r.py[Ragdoll.TORSO], z = r.pz[Ragdoll.TORSO];
        EntityModel m = r.human() ? NpcModels.get() : CreatureModels.of(r.creatureType);
        Animator.poseBody(m, r.skeleton, r.pose);
        if (r.human()) {
            // resetPose inside poseBody makes every archetype accessory visible again.
            Animator.applyAppearance(m, r.appearance);
        }
        bodyFrame(x, y, z, r.pose, frame);
        return m.root;
    }

    /** A person's body at rest. */
    public static ModelPart corpse(HumanCorpse c, Matrix4f frame) {
        EntityModel m = NpcModels.get();
        Animator.poseBody(m, BodySkeleton.humanoid(), c.pose);
        Animator.applyAppearance(m, c.appearance);
        bodyFrame(c.pos.x, c.pos.y, c.pos.z, c.pose, frame);
        return m.root;
    }

    /**
     * An animal's body at rest, in the pose it settled in, or the fixed
     * sprawl for one no solver ran for (an older save, a QA stage).
     */
    public static ModelPart carcass(Carcass c, Matrix4f frame) {
        EntityModel m = CreatureModels.of(c.type);
        if (c.pose.solved) {
            Animator.poseBody(m, BodySkeleton.of(c.type), c.pose);
        } else {
            Animator.poseCarcass(m);
        }
        bodyFrame(c.pos.x, c.pos.y, c.pos.z, c.pose, frame);
        return m.root;
    }

    /**
     * One piece of a body blown apart: its own body's model isolated to the
     * piece ({@link Animator#poseFragment}); {@code frame} is the frame of the
     * piece's root part's parent ({@link FragmentModels#rootFrame}).
     */
    public static ModelPart fragment(BodyFragment f, Matrix4f frame) {
        ModelPart root = Animator.poseFragment(f);
        FragmentModels.rootFrame(f, frame);
        return root;
    }

    /** The family whose model a falling body is drawn with. */
    public static BodyFamily family(Ragdoll r) {
        return r.human() ? BodyFamily.HUMANOID : BodyFamily.of(r.creatureType);
    }

    /**
     * The draw transform for a body with a {@link BodyPose}.
     *
     * <p>Orientation belongs here rather than on the model root: applied after
     * the heading, {@code rotateZ} is a roll about the body's own spine, which
     * is what rolling onto a flank means. The trailing translate hangs the
     * model off the point the pose is positioned by — the torso for a solved
     * body, the feet for one that never ran through the solver.
     */
    public static Matrix4f bodyFrame(float x, float y, float z, BodyPose pose, Matrix4f frame) {
        return frame.identity()
                .translate(x, y + pose.lift, z)
                .rotateY(pose.yaw).rotateX(pose.pitch).rotateZ(pose.roll)
                .translate(0, -pose.pivotY, 0);
    }
}
