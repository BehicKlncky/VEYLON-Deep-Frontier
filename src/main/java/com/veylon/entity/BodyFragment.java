package com.veylon.entity;

import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;

/**
 * One piece of a person blown apart at the joints: a loose rigid box carrying
 * the look of the person it came from.
 *
 * <p>State only; {@link BodyFragmentSystem} moves it. {@link #pos} is the
 * centre of the piece's collision box, and a point {@code p} of the humanoid
 * model in its standing rest pose (model coordinates, feet at the origin,
 * forward -Z) that belongs to this piece is drawn at
 * {@code pos + orientation × (p − restCentre)}. At spawn the orientation is the
 * dead person's heading, so every piece starts exactly where the living model
 * drew it.
 */
public class BodyFragment {

    /**
     * The ten pieces of dismemberment (assumption A6), each rooted at the
     * {@code ModelPart} that anchors it in {@code NpcModels.build()}.
     *
     * <p>Every number is that builder's own: pivots are summed down the part
     * tree, and the arm and leg boxes are halved at the elbow and knee exactly
     * as {@code ModelPart.split} halves them. The collision box of the head
     * piece is the {@code head} box hung below the box-less {@code neck} pivot.
     * The ordinal is the piece id; append only if these ever reach a save.
     */
    public enum Piece {
        TORSO("torso", "torso", List.of("neck", "arm_l", "arm_r"),
                0, Model.HIP_Y, Model.HIP_Y + Model.TORSO_BOX_Y,
                Model.TORSO_W, Model.TORSO_H, Model.TORSO_D),
        HEAD("neck", "head", List.of(),
                0, Model.NECK_Y, Model.NECK_Y + Model.HEAD_PIVOT_Y + Model.HEAD_BOX_Y,
                Model.HEAD_SIZE, Model.HEAD_SIZE, Model.HEAD_SIZE),
        UPPER_ARM_L("arm_l", "arm_l", List.of("forearm_l"),
                -Model.SHOULDER_X, Model.SHOULDER_Y,
                Model.SHOULDER_Y + Model.ARM_BOX_Y + Model.ARM_H * 0.25f,
                Model.ARM_W, Model.ARM_H * 0.5f, Model.ARM_D),
        UPPER_ARM_R("arm_r", "arm_r", List.of("forearm_r"),
                Model.SHOULDER_X, Model.SHOULDER_Y,
                Model.SHOULDER_Y + Model.ARM_BOX_Y + Model.ARM_H * 0.25f,
                Model.ARM_W, Model.ARM_H * 0.5f, Model.ARM_D),
        FOREARM_L("forearm_l", "forearm_l", List.of(),
                -Model.SHOULDER_X, Model.ELBOW_Y, Model.ELBOW_Y - Model.ARM_H * 0.25f,
                Model.ARM_W, Model.ARM_H * 0.5f, Model.ARM_D),
        FOREARM_R("forearm_r", "forearm_r", List.of(),
                Model.SHOULDER_X, Model.ELBOW_Y, Model.ELBOW_Y - Model.ARM_H * 0.25f,
                Model.ARM_W, Model.ARM_H * 0.5f, Model.ARM_D),
        THIGH_L("leg_l", "leg_l", List.of("shin_l"),
                -Model.LEG_X, Model.HIP_Y, Model.HIP_Y + Model.LEG_BOX_Y + Model.LEG_H * 0.25f,
                Model.LEG_W, Model.LEG_H * 0.5f, Model.LEG_D),
        THIGH_R("leg_r", "leg_r", List.of("shin_r"),
                Model.LEG_X, Model.HIP_Y, Model.HIP_Y + Model.LEG_BOX_Y + Model.LEG_H * 0.25f,
                Model.LEG_W, Model.LEG_H * 0.5f, Model.LEG_D),
        SHIN_L("shin_l", "shin_l", List.of(),
                -Model.LEG_X, Model.KNEE_Y, Model.KNEE_Y - Model.LEG_H * 0.25f,
                Model.LEG_W, Model.LEG_H * 0.5f, Model.LEG_D),
        SHIN_R("shin_r", "shin_r", List.of(),
                Model.LEG_X, Model.KNEE_Y, Model.KNEE_Y - Model.LEG_H * 0.25f,
                Model.LEG_W, Model.LEG_H * 0.5f, Model.LEG_D);

        /** The part whose subtree this piece draws. */
        public final String rootPart;
        /** The part whose box is this piece's collision box. */
        public final String boxPart;
        /** Children of {@link #rootPart} that other pieces draw instead. */
        public final List<String> excludedParts;
        /** Root pivot in model coordinates, standing rest pose. */
        public final float pivotX, pivotY, pivotZ;
        /** Collision box centre in model coordinates, standing rest pose. */
        public final float centreX, centreY, centreZ;
        /** Collision box half sizes along the piece's own axes. */
        public final float halfX, halfY, halfZ;
        /**
         * The single sweep pair of the rest orientation:
         * {@code max(sx, sz) / 2} and {@code sy / 2}, each at least
         * {@link BodyFragmentConstants#MIN_HALF_EXTENT}.
         */
        public final float halfWidth, halfHeight;
        public final float volume;
        public final float mass;
        public final float inverseMass;
        /** True when the root pivot is a joint the blast cut through. */
        public final boolean severed;

        Piece(String rootPart, String boxPart, List<String> excludedParts,
              float pivotX, float pivotY, float centreY, float sx, float sy, float sz) {
            this.rootPart = rootPart;
            this.boxPart = boxPart;
            this.excludedParts = excludedParts;
            // Every human box hangs straight below or above its pivot.
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = 0;
            this.centreX = pivotX;
            this.centreY = centreY;
            this.centreZ = 0;
            this.halfX = sx * 0.5f;
            this.halfY = sy * 0.5f;
            this.halfZ = sz * 0.5f;
            this.halfWidth = Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, Math.max(sx, sz) * 0.5f);
            this.halfHeight = Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, sy * 0.5f);
            this.volume = sx * sy * sz;
            this.mass = volume * BodyFragmentConstants.DENSITY;
            this.inverseMass = 1f / mass;
            this.severed = !"torso".equals(rootPart);
        }
    }

    /** {@code NpcModels.build()}'s numbers, named. Pivots are relative to the model origin. */
    private static final class Model {
        static final float HIP_Y = 0.86f;
        static final float TORSO_BOX_Y = 0.31f;
        static final float TORSO_W = 0.46f, TORSO_H = 0.62f, TORSO_D = 0.26f;
        /** {@code neck} hangs 0.58 above the torso pivot. */
        static final float NECK_Y = HIP_Y + 0.58f;
        /** {@code head} is built at 0.64 and then moved under the neck: 0.64 − 0.58. */
        static final float HEAD_PIVOT_Y = 0.64f - 0.58f;
        static final float HEAD_BOX_Y = 0.14f;
        static final float HEAD_SIZE = 0.26f;
        static final float SHOULDER_X = 0.30f;
        static final float SHOULDER_Y = HIP_Y + 0.55f;
        static final float ARM_BOX_Y = -0.26f;
        static final float ARM_W = 0.13f, ARM_H = 0.55f, ARM_D = 0.15f;
        /** {@code split} pivots the lower half at the unsplit box centre. */
        static final float ELBOW_Y = SHOULDER_Y + ARM_BOX_Y;
        static final float LEG_X = 0.115f;
        static final float LEG_BOX_Y = -0.43f;
        static final float LEG_W = 0.16f, LEG_H = 0.86f, LEG_D = 0.18f;
        static final float KNEE_Y = HIP_Y + LEG_BOX_Y;

        private Model() {
        }
    }

    public final Piece piece;
    /** The model part whose subtree this piece draws; {@code piece.rootPart}. */
    public final String rootPart;
    /** Rest-pose offset of the root pivot from the model origin. */
    public final float restPivotX, restPivotY, restPivotZ;
    /** Rest-pose offset of the collision box centre ({@link #pos}) from the model origin. */
    public final float restCentreX, restCentreY, restCentreZ;
    /** Box half sizes along the piece's own axes. */
    public final float halfX, halfY, halfZ;
    /**
     * The sweep box for the current orientation: the world-axis extents of the
     * turned box, horizontal ones merged into one half width. At the rest
     * orientation this is exactly {@code piece.halfWidth} and
     * {@code piece.halfHeight}.
     */
    public float halfWidth, halfHeight;
    public final float inverseMass;
    /** Captured from the person who died; each piece has its own copy. */
    public final NpcAppearance appearance = new NpcAppearance();

    public final Vector3f pos = new Vector3f();
    public final Vector3f vel = new Vector3f();
    public final Quaternionf orientation = new Quaternionf();
    /** World-axis angular velocity, rad/s. */
    public final Vector3f angularVelocity = new Vector3f();

    // ---- Lifecycle --------------------------------------------------
    public float age;
    public int quietSteps;
    /** True when the last step ended resting on something below. */
    public boolean grounded;
    public boolean settled;
    /** Seconds left before a settled piece rots away. */
    public float decay = RagdollConstants.CORPSE_DECAY;
    public float dripTimer;
    /** Last measured settle energy, for tests and the debug overlay. */
    public float energy;

    public BodyFragment(Piece piece) {
        this.piece = piece;
        this.rootPart = piece.rootPart;
        this.restPivotX = piece.pivotX;
        this.restPivotY = piece.pivotY;
        this.restPivotZ = piece.pivotZ;
        this.restCentreX = piece.centreX;
        this.restCentreY = piece.centreY;
        this.restCentreZ = piece.centreZ;
        this.halfX = piece.halfX;
        this.halfY = piece.halfY;
        this.halfZ = piece.halfZ;
        this.halfWidth = piece.halfWidth;
        this.halfHeight = piece.halfHeight;
        this.inverseMass = piece.inverseMass;
    }

    /** Where a rest-pose model point of this piece currently is in the world. */
    public Vector3f modelToWorld(float x, float y, float z, Vector3f dest) {
        orientation.transform(x - restCentreX, y - restCentreY, z - restCentreZ, dest);
        return dest.add(pos);
    }

    public double distSqTo(float x, float y, float z) {
        double dx = pos.x - x;
        double dy = pos.y - y;
        double dz = pos.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
