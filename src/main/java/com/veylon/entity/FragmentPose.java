package com.veylon.entity;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.Objects;

/**
 * The pose a body died in: one local transform per joint of its
 * {@link FragmentAnatomy}, frozen when the body comes apart.
 *
 * <p>Models are shared and re-posed for every draw, so a piece cannot keep
 * the model it came from. It keeps this instead: a bounded CPU copy of what the
 * living animation had written into the joint parts — rotation, pose offset
 * and scale, the {@code ModelPart} fields — plus everything derived from it
 * once: each joint's frame in model space and each piece's rigid box frame. A
 * running leg therefore leaves the body mid-stride and a flapping wing
 * mid-beat, rather than snapping to a standing rest pose.
 *
 * <p>Immutable. All pieces of one body share one instance, and it holds
 * nothing but numbers: no model, no entity. Frames compose exactly as
 * {@code ModelPart.render} does: translate by pivot plus pose offset, then
 * Rz·Ry·Rx, then scale.
 */
public final class FragmentPose {

    /** rotX, rotY, rotZ, poseX, poseY, poseZ, scale. */
    private static final int STRIDE = 7;
    private static final int SCALE = 6;

    public final FragmentAnatomy anatomy;
    private final float[] local;
    private final Matrix4f[] frames;
    private final Vector3f[] centres;
    private final Quaternionf[] rotations;
    private final float[] scales;
    private final boolean rest;

    /** @param values {@code STRIDE} values per joint, or null for the rest pose */
    FragmentPose(FragmentAnatomy anatomy, float[] values) {
        this.anatomy = anatomy;
        int n = anatomy.jointCount();
        this.local = new float[n * STRIDE];
        boolean still = true;
        for (int j = 0; j < n; j++) {
            local[j * STRIDE + SCALE] = 1f;
            if (values == null) {
                continue;
            }
            for (int k = 0; k < STRIDE; k++) {
                float v = values[j * STRIDE + k];
                if (!Float.isFinite(v)) {
                    throw new IllegalArgumentException(anatomy.family + " pose: joint "
                            + anatomy.joint(j).name + " has a non-finite transform");
                }
                local[j * STRIDE + k] = v;
                still &= v == (k == SCALE ? 1f : 0f);
            }
            if (!(local[j * STRIDE + SCALE] > 0)) {
                throw new IllegalArgumentException(anatomy.family + " pose: joint "
                        + anatomy.joint(j).name + " has a scale that is not positive");
            }
        }
        this.rest = still;

        frames = new Matrix4f[n];
        for (int j = 0; j < n; j++) {
            FragmentAnatomy.Joint joint = anatomy.joint(j);
            int o = j * STRIDE;
            Matrix4f m = joint.parent < 0 ? new Matrix4f() : new Matrix4f(frames[joint.parent]);
            m.translate(joint.pivotX + local[o + 3], joint.pivotY + local[o + 4], joint.pivotZ + local[o + 5])
                    .rotateZ(local[o + 2]).rotateY(local[o + 1]).rotateX(local[o]);
            if (local[o + SCALE] != 1f) {
                m.scale(local[o + SCALE]);
            }
            frames[j] = m;
        }
        int pieces = anatomy.pieces.size();
        centres = new Vector3f[pieces];
        rotations = new Quaternionf[pieces];
        scales = new float[pieces];
        for (FragmentPiece p : anatomy.pieces) {
            Matrix4f box = frames[p.boxJoint];
            centres[p.id] = box.transformPosition(p.boxOffsetX, p.boxOffsetY, p.boxOffsetZ, new Vector3f());
            // A breathing body is scaled; take the rotation from normalised
            // columns and the (uniform) scale from their length.
            rotations[p.id] = box.getUnnormalizedRotation(new Quaternionf());
            scales[p.id] = rest ? 1f : box.getScale(new Vector3f()).x;
        }
    }

    /** The standing rest pose of this body family. */
    public static FragmentPose rest(FragmentAnatomy anatomy) {
        return anatomy.restPose();
    }

    /** True when every joint is at rest: no rotation, no pose offset, scale 1. */
    public boolean isRest() {
        return rest;
    }

    public float rotX(int joint) {
        return local[joint * STRIDE];
    }

    public float rotY(int joint) {
        return local[joint * STRIDE + 1];
    }

    public float rotZ(int joint) {
        return local[joint * STRIDE + 2];
    }

    public float poseX(int joint) {
        return local[joint * STRIDE + 3];
    }

    public float poseY(int joint) {
        return local[joint * STRIDE + 4];
    }

    public float poseZ(int joint) {
        return local[joint * STRIDE + 5];
    }

    public float scale(int joint) {
        return local[joint * STRIDE + SCALE];
    }

    /** The joint's frame in model space: where its part's own box and children hang. */
    public Matrix4f jointFrame(int joint, Matrix4f dest) {
        return dest.set(frames[joint]);
    }

    /** {@code dest × jointFrame(joint)}, without a scratch matrix. */
    public Matrix4f mulJointFrame(int joint, Matrix4f dest) {
        return dest.mul(frames[joint]);
    }

    /** The joint's pivot in model space: where it was cut, for a piece's root. */
    public Vector3f jointOrigin(int joint, Vector3f dest) {
        return frames[joint].getTranslation(dest);
    }

    /** Model-space centre of the piece's collision box in this pose. */
    public Vector3f pieceCentre(int piece, Vector3f dest) {
        return dest.set(centres[piece]);
    }

    /** Model-space orientation of the piece's collision box: its box part's rotation, scale removed. */
    public Quaternionf pieceRotation(int piece, Quaternionf dest) {
        return dest.set(rotations[piece]);
    }

    /**
     * How much larger than at rest the piece's box is drawn in this pose: the
     * box part's scale times its ancestors', 1 at rest. Only the animation's
     * breathing sets one, by about a percent.
     */
    public float pieceScale(int piece) {
        return scales[piece];
    }

    /**
     * Collects a pose joint by joint, starting from rest. Used once per death;
     * {@link #snapshot} copies, so later changes never reach a snapshot.
     */
    public static final class Recorder {
        private final FragmentAnatomy anatomy;
        private final float[] values;

        public Recorder(FragmentAnatomy anatomy) {
            this.anatomy = Objects.requireNonNull(anatomy, "anatomy");
            this.values = new float[anatomy.jointCount() * STRIDE];
            for (int j = 0; j < anatomy.jointCount(); j++) {
                values[j * STRIDE + SCALE] = 1f;
            }
        }

        public Recorder set(int joint, float rotX, float rotY, float rotZ,
                            float poseX, float poseY, float poseZ, float scale) {
            Objects.checkIndex(joint, anatomy.jointCount());
            int o = joint * STRIDE;
            values[o] = rotX;
            values[o + 1] = rotY;
            values[o + 2] = rotZ;
            values[o + 3] = poseX;
            values[o + 4] = poseY;
            values[o + 5] = poseZ;
            values[o + SCALE] = scale;
            return this;
        }

        /** @throws IllegalArgumentException when a value is not finite or a scale not positive */
        public FragmentPose snapshot() {
            return new FragmentPose(anatomy, values.clone());
        }
    }
}
