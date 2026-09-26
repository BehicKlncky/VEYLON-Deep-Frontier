package com.veylon.entity;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.List;
import java.util.Locale;

/**
 * One piece of a body blown apart at the joints: a loose rigid box carrying
 * the look of the body it came from.
 *
 * <p>State only; {@link BodyFragmentSystem} moves it. What the piece is comes
 * from its {@link #definition} in its body's {@link FragmentAnatomy}; the pose
 * the body died in is {@link #pose}, an immutable snapshot every piece of that
 * body shares. {@link #pos} is the centre of the piece's collision box, and a
 * point {@code q} of the model as posed at death that belongs to this piece is
 * drawn at {@code pos + orientation × poseRotation⁻¹ × (q − poseCentre)}
 * ({@link #modelToWorld}). {@link #placeAt} turns the posed model to the dead
 * body's heading, so every piece starts exactly where the living model drew
 * it.
 *
 * <p>A piece placed with the rest pose — every human piece today, and every
 * piece read from a {@code world.fragments} version 1 record — has
 * {@code poseRotation} the identity and {@code poseCentre} its rest centre, so
 * the rule above is the one this class always had: a rest-pose model point
 * {@code p} is drawn at {@code pos + orientation × (p − restCentre)}.
 */
public class BodyFragment {

    /**
     * The ten pieces of a person (assumption A6), in the order of
     * {@link FragmentAnatomy#humanoid()}'s pieces: the ordinal is the piece id
     * {@code world.fragments} version 1 stores, so append only.
     *
     * <p>Every number is the humanoid table's, which repeats
     * {@code NpcModels.build()}: pivots summed down the part tree, arm and leg
     * boxes halved at the elbow and knee exactly as {@code ModelPart.split}
     * halves them, and the head piece's collision box the {@code head} box hung
     * below the box-less {@code neck} pivot.
     */
    public enum Piece {
        TORSO, HEAD, UPPER_ARM_L, UPPER_ARM_R, FOREARM_L, FOREARM_R, THIGH_L, THIGH_R, SHIN_L, SHIN_R;

        /** This piece in the humanoid table; its id is this ordinal. */
        public final FragmentPiece definition;
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

        Piece() {
            definition = FragmentAnatomy.humanoid().piece(ordinal());
            if (!definition.name.equals(name().toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("legacy piece " + name() + " maps to humanoid piece "
                        + definition.name + "; world.fragments v1 ids would change");
            }
            rootPart = definition.rootPart;
            boxPart = definition.boxPart;
            excludedParts = definition.excludedParts;
            pivotX = definition.pivotX;
            pivotY = definition.pivotY;
            pivotZ = definition.pivotZ;
            centreX = definition.centreX;
            centreY = definition.centreY;
            centreZ = definition.centreZ;
            halfX = definition.halfX;
            halfY = definition.halfY;
            halfZ = definition.halfZ;
            halfWidth = definition.halfWidth;
            halfHeight = definition.halfHeight;
            volume = definition.volume;
            mass = definition.mass;
            inverseMass = definition.inverseMass;
            severed = definition.severed;
        }
    }

    private static final Piece[] PIECES = Piece.values();

    /** What this piece is, in its body family's table. */
    public final FragmentPiece definition;
    /**
     * The legacy human identity, the {@code world.fragments} version 1 id;
     * null for a piece of any other body.
     */
    public final Piece piece;
    /** The pose the body died in, shared by all its pieces; its family's rest pose when none was captured. */
    public final FragmentPose pose;
    /** The model part whose subtree this piece draws; {@code definition.rootPart}. */
    public final String rootPart;
    /** Rest-pose offset of the root pivot from the model origin. */
    public final float restPivotX, restPivotY, restPivotZ;
    /** Rest-pose offset of the collision box centre from the model origin. */
    public final float restCentreX, restCentreY, restCentreZ;
    /**
     * Box half sizes along the piece's own axes: the definition's, times the
     * scale the box was drawn with at death ({@link FragmentPose#pieceScale},
     * 1 in the rest pose).
     */
    public final float halfX, halfY, halfZ;
    /**
     * The sweep box for the current orientation: the world-axis extents of the
     * turned box, horizontal ones merged into one half width. In the rest pose
     * and orientation this is exactly {@code definition.halfWidth} and
     * {@code definition.halfHeight}.
     */
    public float halfWidth, halfHeight;
    public final float inverseMass;
    /** Collision box centre in the model space of {@link #pose}. */
    public final Vector3f poseCentre = new Vector3f();
    /** Collision box orientation in the model space of {@link #pose}; the identity at rest. */
    public final Quaternionf poseRotation = new Quaternionf();
    private final Quaternionf poseRotationInverse = new Quaternionf();
    /**
     * Captured from the person who died, or the neutral look for the
     * player's remains; each piece has its own copy. Unused by other bodies.
     */
    public final NpcAppearance appearance = new NpcAppearance();
    /**
     * The harvest record this piece carries, while it is the torso of an
     * animal blown apart and that record is in the world; null otherwise.
     * Such a piece is kept past the settled cap and the despawn radius, and
     * goes when its carcass rots away.
     */
    public Carcass harvest;

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

    /** A human piece in the standing rest pose, as {@code world.fragments} version 1 restores it. */
    public BodyFragment(Piece piece) {
        this(piece.definition, piece.definition.anatomy.restPose());
    }

    /**
     * A piece of any body, in the pose that body died in.
     *
     * @throws IllegalArgumentException when the pose belongs to another body family
     */
    public BodyFragment(FragmentPiece definition, FragmentPose pose) {
        if (pose.anatomy != definition.anatomy) {
            throw new IllegalArgumentException("a " + pose.anatomy.family + " pose cannot place the "
                    + definition + " piece");
        }
        this.definition = definition;
        this.piece = definition.family == BodyFamily.HUMANOID ? PIECES[definition.id] : null;
        this.pose = pose;
        this.rootPart = definition.rootPart;
        this.restPivotX = definition.pivotX;
        this.restPivotY = definition.pivotY;
        this.restPivotZ = definition.pivotZ;
        this.restCentreX = definition.centreX;
        this.restCentreY = definition.centreY;
        this.restCentreZ = definition.centreZ;
        // The box as drawn at death; the pose's scale is 1 except for breathing.
        float scale = pose.pieceScale(definition.id);
        this.halfX = definition.halfX * scale;
        this.halfY = definition.halfY * scale;
        this.halfZ = definition.halfZ * scale;
        this.halfWidth = Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, Math.max(halfX, halfZ));
        this.halfHeight = Math.max(BodyFragmentConstants.MIN_HALF_EXTENT, halfY);
        this.inverseMass = definition.inverseMass;
        pose.pieceCentre(definition.id, poseCentre);
        pose.pieceRotation(definition.id, poseRotation);
        poseRotationInverse.set(poseRotation).conjugate();
    }

    /**
     * Puts the piece where the living model drew it: the body's feet at
     * {@code (x, y, z)}, turned by {@code yaw} radians about Y — the draw
     * transform's {@code rotateY(toRadians(-entity.yaw))}. Sets {@link #pos}
     * and {@link #orientation} only; the caller refits the sweep box.
     */
    public BodyFragment placeAt(float x, float y, float z, float yaw) {
        orientation.rotationY(yaw);
        orientation.transform(poseCentre.x, poseCentre.y, poseCentre.z, pos).add(x, y, z);
        orientation.mul(poseRotation);
        return this;
    }

    /** Where a point of the model, as posed at death, that belongs to this piece is now. */
    public Vector3f modelToWorld(float x, float y, float z, Vector3f dest) {
        poseRotationInverse.transform(x - poseCentre.x, y - poseCentre.y, z - poseCentre.z, dest);
        orientation.transform(dest);
        return dest.add(pos);
    }

    /** Where a joint of this piece is now; for {@code definition.rootJoint}, the cut. */
    public Vector3f jointToWorld(int joint, Vector3f dest) {
        pose.jointOrigin(joint, dest);
        return modelToWorld(dest.x, dest.y, dest.z, dest);
    }

    /**
     * The matrix taking a point of the model, as posed at death, to where this
     * piece has it now: {@code translate(pos) × rotate(orientation) ×
     * rotate(poseRotation⁻¹) × translate(−poseCentre)}.
     */
    public Matrix4f modelTransform(Matrix4f dest) {
        return dest.translation(pos).rotate(orientation).rotate(poseRotationInverse)
                .translate(-poseCentre.x, -poseCentre.y, -poseCentre.z);
    }

    /**
     * The frame the root part is drawn in — the parent matrix to hand
     * {@code ModelPart.render} for {@link #rootPart} once the model carries
     * {@link #pose}: {@link #modelTransform} times the pose frame of the root
     * part's parent. In the rest pose that frame is the translation by the
     * pivots above the root part.
     */
    public Matrix4f rootTransform(Matrix4f dest) {
        modelTransform(dest);
        return pose.mulJointFrame(definition.anatomy.joint(definition.rootJoint).parent, dest);
    }

    public double distSqTo(float x, float y, float z) {
        double dx = pos.x - x;
        double dy = pos.y - y;
        double dz = pos.z - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
