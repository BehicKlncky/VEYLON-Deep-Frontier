package com.veylon.entity;

/**
 * The orientation and bone angles a body is drawn with.
 *
 * <p>This is the whole contract between the solver and the renderer. A ragdoll
 * writes one of these every step; the corpse it settles into keeps the final
 * copy, so a body is drawn in the pose it actually came to rest in rather than
 * snapping into a hard-coded one.
 *
 * <p>Angles are radians. {@code yaw}/{@code pitch}/{@code roll} are applied in
 * that order about the body's own axes and belong on the draw transform, not on
 * the model root; {@code lift} is a world-space rise applied before them so a
 * body on its side clears the ground. Bone angles are written into the shared
 * model's parts by {@code Animator.poseBody}.
 */
public final class BodyPose {

    public float yaw;
    public float pitch;
    public float roll;
    public float lift;
    /**
     * Height of {@code pos} above the model origin, along the body's own down
     * axis. Solved bodies are positioned by their torso, so the model is hung
     * off it; a carcass that never ran through the solver keeps 0 and is drawn
     * from its feet exactly as it always was.
     */
    public float pivotY;
    public final float[] boneRotX = new float[BodySkeleton.MAX_BONES];
    public final float[] boneRotZ = new float[BodySkeleton.MAX_BONES];
    public int boneCount;
    /**
     * False for a body that never ran through the solver — a carcass restored
     * from an older save, or one a QA scene placed directly. Those fall back to
     * the fixed keeled-over pose instead of reading garbage bone angles.
     */
    public boolean solved;

    public void copyFrom(BodyPose other) {
        yaw = other.yaw;
        pitch = other.pitch;
        roll = other.roll;
        lift = other.lift;
        pivotY = other.pivotY;
        boneCount = other.boneCount;
        solved = other.solved;
        System.arraycopy(other.boneRotX, 0, boneRotX, 0, BodySkeleton.MAX_BONES);
        System.arraycopy(other.boneRotZ, 0, boneRotZ, 0, BodySkeleton.MAX_BONES);
    }

    public void clear() {
        yaw = pitch = roll = lift = pivotY = 0;
        boneCount = 0;
        solved = false;
        java.util.Arrays.fill(boneRotX, 0f);
        java.util.Arrays.fill(boneRotZ, 0f);
    }
}
