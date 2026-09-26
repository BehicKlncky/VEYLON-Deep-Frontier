package com.veylon.gfx.model;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.Creature;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.FragmentPose;
import com.veylon.entity.Npc;

import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Where the simulation's fragment tables meet the models they describe.
 *
 * <p>{@link FragmentAnatomy} writes out each model's joints by hand, because
 * the simulation does not read the renderer's models. {@link #validate} holds
 * every such number to the real part tree and checks that every visible box is
 * drawn by exactly one piece; {@link #modelOf} runs it once per family before
 * handing a model out. {@link #captureCreature} and {@link #captureNpc} pose the
 * shared model as the living animation last drew the body and copy the joints
 * into an immutable {@link FragmentPose}; {@link #applyPose} writes one back.
 *
 * <p>CPU only: no GL call is made here. Main thread only, like the models.
 */
public final class AnatomyModels {

    /** How far a table's number may differ from the model builder's: float rounding only. */
    public static final float MATCH_TOLERANCE = 1e-6f;

    private static final Set<BodyFamily> VALIDATED = EnumSet.noneOf(BodyFamily.class);

    private AnatomyModels() {
    }

    /** The shared model a family is drawn with, checked against its table on first use. */
    public static EntityModel modelOf(BodyFamily family) {
        EntityModel model = family == BodyFamily.HUMANOID
                ? NpcModels.get() : CreatureModels.of(family.creature);
        if (!VALIDATED.contains(family)) {
            validate(family.anatomy(), model);
            VALIDATED.add(family);
        }
        return model;
    }

    // ------------------------------------------------------------------
    // Validation
    // ------------------------------------------------------------------

    /**
     * Checks a table against a model: every joint is a real part hanging from
     * the same parent, with the same pivot, box and split, and every box the
     * model has is drawn by exactly one piece once that piece is isolated the
     * way {@link Animator#isolatePart} isolates it.
     *
     * @throws IllegalStateException naming the first disagreement
     */
    public static void validate(FragmentAnatomy anatomy, EntityModel model) {
        String who = anatomy.family + " anatomy";
        ModelPart root = model.root;
        for (int j = 0; j < anatomy.jointCount(); j++) {
            FragmentAnatomy.Joint joint = anatomy.joint(j);
            ModelPart part = root.find(joint.name);
            if (part == null) {
                throw new IllegalStateException(who + " names part " + joint.name + ", which its model does not have");
            }
            ModelPart parent = parentOf(root, part);
            if (joint.parent < 0) {
                if (part != root) {
                    throw new IllegalStateException(who + ": " + joint.name + " is not the model root");
                }
            } else {
                String want = anatomy.joint(joint.parent).name;
                if (parent == null || !parent.name.equals(want)) {
                    throw new IllegalStateException(who + ": " + joint.name + " hangs from "
                            + (parent == null ? "nothing" : parent.name) + " in the model, not " + want);
                }
            }
            same(who, joint.name, "pivot", joint.pivotX, joint.pivotY, joint.pivotZ,
                    part.pivotX, part.pivotY, part.pivotZ);
            boolean boxed = part.sizeX > 0;
            if (boxed != joint.boxed) {
                throw new IllegalStateException(who + ": " + joint.name + (boxed ? " has" : " has no")
                        + " box in the model");
            }
            if (boxed) {
                same(who, joint.name, "box centre", joint.boxX, joint.boxY, joint.boxZ,
                        part.boxX, part.boxY, part.boxZ);
                same(who, joint.name, "box size", joint.sizeX, joint.sizeY, joint.sizeZ,
                        part.sizeX, part.sizeY, part.sizeZ);
            }
            boolean tip = parent != null && parent.isSplitTip(part);
            if (tip != joint.splitTip) {
                throw new IllegalStateException(who + ": " + joint.name + (tip ? " is" : " is not")
                        + " a split half in the model");
            }
        }

        Map<ModelPart, FragmentPiece> drawnBy = new IdentityHashMap<>();
        for (FragmentPiece piece : anatomy.pieces) {
            claim(who, root.find(piece.rootPart), piece, drawnBy);
        }
        checkDrawn(who, root, drawnBy);
    }

    private static void claim(String who, ModelPart part, FragmentPiece piece,
                              Map<ModelPart, FragmentPiece> drawnBy) {
        if (part.sizeX > 0) {
            FragmentPiece other = drawnBy.put(part, piece);
            if (other != null) {
                throw new IllegalStateException(who + ": box " + part.name + " is drawn by both "
                        + other.name + " and " + piece.name);
            }
        }
        for (ModelPart child : part.children) {
            if (!piece.excludedParts.contains(child.name)) {
                claim(who, child, piece, drawnBy);
            }
        }
    }

    private static void checkDrawn(String who, ModelPart part, Map<ModelPart, FragmentPiece> drawnBy) {
        if (part.sizeX > 0 && !drawnBy.containsKey(part)) {
            throw new IllegalStateException(who + ": box " + part.name + " is drawn by no piece");
        }
        for (ModelPart child : part.children) {
            checkDrawn(who, child, drawnBy);
        }
    }

    private static void same(String who, String name, String what, float x, float y, float z,
                             float mx, float my, float mz) {
        if (Math.abs(x - mx) > MATCH_TOLERANCE || Math.abs(y - my) > MATCH_TOLERANCE
                || Math.abs(z - mz) > MATCH_TOLERANCE) {
            throw new IllegalStateException(who + ": " + name + " " + what + " (" + x + ", " + y + ", " + z
                    + ") is not the model's (" + mx + ", " + my + ", " + mz + ")");
        }
    }

    private static ModelPart parentOf(ModelPart at, ModelPart child) {
        for (ModelPart c : at.children) {
            if (c == child) {
                return at;
            }
            ModelPart hit = parentOf(c, child);
            if (hit != null) {
                return hit;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Death poses
    // ------------------------------------------------------------------

    /**
     * The pose a creature is drawn in at {@code time} — pass the clock the
     * renderer animates the living with, {@code Game.totalTime}, so the pieces
     * leave the body in the pose last drawn. The shared model is left reset.
     */
    public static FragmentPose captureCreature(Creature c, double time) {
        EntityModel model = modelOf(BodyFamily.of(c.type));
        Animator.poseCreature(model, c, time);
        FragmentPose pose = record(model, FragmentAnatomy.of(c.type));
        model.resetPose();
        return pose;
    }

    /** The pose a person is drawn in at {@code time}; see {@link #captureCreature}. */
    public static FragmentPose captureNpc(Npc n, double time) {
        EntityModel model = modelOf(BodyFamily.HUMANOID);
        Animator.poseNpc(model, n, time);
        FragmentPose pose = record(model, FragmentAnatomy.humanoid());
        model.resetPose();
        return pose;
    }

    /**
     * Copies the joints of a posed model into a snapshot. Only joints are
     * read: everything else the animation could move is covered by the table,
     * and appearance is re-applied from the piece's own copy when it is drawn.
     */
    public static FragmentPose record(EntityModel posed, FragmentAnatomy anatomy) {
        FragmentPose.Recorder recorder = new FragmentPose.Recorder(anatomy);
        for (int j = 0; j < anatomy.jointCount(); j++) {
            String name = anatomy.joint(j).name;
            ModelPart part = posed.root.find(name);
            if (part == null) {
                throw new IllegalStateException(anatomy.family + " anatomy names part " + name
                        + ", which the posed model does not have");
            }
            recorder.set(j, part.rotX, part.rotY, part.rotZ, part.poseX, part.poseY, part.poseZ, part.scale);
        }
        return recorder.snapshot();
    }

    /**
     * Writes a snapshot's joint transforms into a model, after the caller's
     * {@code resetPose} and appearance. Parts outside the table keep what the
     * caller gave them.
     */
    public static void applyPose(EntityModel model, FragmentPose pose) {
        FragmentAnatomy anatomy = pose.anatomy;
        for (int j = 0; j < anatomy.jointCount(); j++) {
            ModelPart part = model.part(anatomy.joint(j).name);
            part.rotX = pose.rotX(j);
            part.rotY = pose.rotY(j);
            part.rotZ = pose.rotZ(j);
            part.poseX = pose.poseX(j);
            part.poseY = pose.poseY(j);
            part.poseZ = pose.poseZ(j);
            part.scale = pose.scale(j);
        }
    }
}
