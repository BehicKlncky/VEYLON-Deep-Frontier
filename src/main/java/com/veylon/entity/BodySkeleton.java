package com.veylon.entity;

import com.veylon.entity.Creature.CreatureType;

import java.util.EnumMap;
import java.util.Map;

/**
 * The point-mass layout of one species, expressed in the coordinates its
 * {@code EntityModel} is actually built in.
 *
 * <p>A body is one torso point carrying the orientation, plus up to
 * {@link #MAX_BONES} appendage points. Each appendage names a real
 * {@code ModelPart}, the pivot that part hangs from <em>in the model root's
 * frame</em>, the axis it is authored along, and how far out along that axis
 * its handle sits. Everything an appendage needs is therefore derivable from
 * the model builders, and nothing here clones a model.
 *
 * <p>Ancestors of every listed part are unrotated by the ragdoll pose, which is
 * what lets a pivot be quoted in the root frame: a quadruped's legs and neck
 * hang off {@code root}, its tail off the unrotated {@code body}, a humanoid's
 * arms and head off the unrotated {@code torso}. A species with no entry
 * degrades to {@link #rigid} — a torso and no articulation — rather than
 * guessing bone names.
 */
public final class BodySkeleton {

    /** Widest layout is the quadruped: four legs, a neck and a tail. */
    public static final int MAX_BONES = 6;

    /** Rest axis of a bone handle, in the body's local frame. Forward is -Z. */
    public static final int AXIS_DOWN = 0;
    public static final int AXIS_FORWARD = 1;
    public static final int AXIS_BACK = 2;
    public static final int AXIS_UP = 3;

    private static final Map<CreatureType, BodySkeleton> CREATURES =
            new EnumMap<>(CreatureType.class);
    private static BodySkeleton humanoid;

    public final String[] part = new String[MAX_BONES];
    public final float[] pivotX = new float[MAX_BONES];
    public final float[] pivotY = new float[MAX_BONES];
    public final float[] pivotZ = new float[MAX_BONES];
    public final float[] length = new float[MAX_BONES];
    public final int[] axis = new int[MAX_BONES];
    public int boneCount;

    /** Height of the torso point above the model origin (the ground contact). */
    public float torsoY;
    /**
     * Half-extent of the cube the torso point sweeps through voxels. It is
     * deliberately isotropic: the point is a centre-of-mass proxy, and a body
     * that has rolled onto its side must not keep a standing torso's height.
     */
    public float torsoRadius = 0.22f;

    private BodySkeleton() {
    }

    /**
     * Rotation about local X that aims the "down" handle along {@code axis}.
     * Chosen so a bone at rest reads {@code rotX = rotZ = 0} and keeps whatever
     * pose its model builder authored.
     */
    public static float restAngle(int axis) {
        return switch (axis) {
            case AXIS_FORWARD -> (float) (Math.PI * 0.5);
            case AXIS_BACK -> (float) (-Math.PI * 0.5);
            case AXIS_UP -> (float) Math.PI;
            default -> 0f;
        };
    }

    /** Unit rest direction of {@code axis} in the body frame. */
    public static float restX(int axis) {
        return 0f;
    }

    public static float restY(int axis) {
        return axis == AXIS_DOWN ? -1f : (axis == AXIS_UP ? 1f : 0f);
    }

    public static float restZ(int axis) {
        return axis == AXIS_FORWARD ? -1f : (axis == AXIS_BACK ? 1f : 0f);
    }

    public static synchronized BodySkeleton of(CreatureType type) {
        return CREATURES.computeIfAbsent(type, BodySkeleton::buildCreature);
    }

    public static synchronized BodySkeleton humanoid() {
        if (humanoid == null) {
            humanoid = buildHumanoid();
        }
        return humanoid;
    }

    /**
     * Fallback for a body whose bones were never mapped: a torso and nothing
     * else, sized from the entity itself. It tumbles as one rigid piece instead
     * of reaching for part names the model may not have.
     */
    public static BodySkeleton rigid(float width, float height) {
        BodySkeleton s = new BodySkeleton();
        s.torsoY = height * 0.5f;
        s.torsoRadius = Math.max(0.09f, Math.min(width, height) * 0.4f);
        return s;
    }

    private void bone(String name, float px, float py, float pz, int boneAxis, float len) {
        if (boneCount >= MAX_BONES) {
            return;
        }
        int i = boneCount++;
        part[i] = name;
        pivotX[i] = px;
        pivotY[i] = py;
        pivotZ[i] = pz;
        axis[i] = boneAxis;
        length[i] = len;
    }

    private static BodySkeleton buildCreature(CreatureType type) {
        return switch (type) {
            // CreatureModels.quadruped(tag, bodyLen, bodyW, bodyH, legH, legW, ...)
            case DEER -> quadruped(0.85f, 0.42f, 0.42f, 0.58f);
            case WOLF -> quadruped(0.80f, 0.34f, 0.34f, 0.42f);
            case THORNHORN -> quadruped(1.25f, 0.72f, 0.72f, 0.55f);
            case STALKER -> quadruped(0.70f, 0.26f, 0.26f, 0.72f);
            case HARE -> hare();
            case BIRD -> bird();
            // A creature type appended without a bone mapping tumbles as one
            // rigid piece instead of reaching for part names it does not have.
            default -> rigid(type.width, type.height);
        };
    }

    /** Mirrors {@code CreatureModels.quadruped}: root -> body/legs/neck, body -> tail. */
    private static BodySkeleton quadruped(float bodyLen, float bodyW, float bodyH,
                                          float legH) {
        BodySkeleton s = new BodySkeleton();
        float shoulderY = legH + bodyH * 0.5f;
        float hx = bodyW * 0.32f;
        float fz = -bodyLen * 0.38f;
        float bz = bodyLen * 0.38f;
        s.bone("leg_fl", -hx, legH, fz, AXIS_DOWN, legH);
        s.bone("leg_fr", hx, legH, fz, AXIS_DOWN, legH);
        s.bone("leg_bl", -hx, legH, bz, AXIS_DOWN, legH);
        s.bone("leg_br", hx, legH, bz, AXIS_DOWN, legH);
        // The neck carries the head: rotating it swings the whole skull.
        s.bone("neck", 0, shoulderY + bodyH * 0.30f, -bodyLen * 0.48f,
                AXIS_FORWARD, bodyLen * 0.38f);
        // Tail hangs off the body, whose pivot sits at the shoulder height.
        s.bone("tail", 0, shoulderY + bodyH * 0.26f, bodyLen * 0.50f,
                AXIS_BACK, bodyLen * 0.30f);
        s.torsoY = shoulderY;
        s.torsoRadius = Math.max(0.12f, Math.min(bodyW, bodyH) * 0.5f);
        return s;
    }

    /** Mirrors {@code CreatureModels.murkhare}: short legs, head and tail on the body. */
    private static BodySkeleton hare() {
        BodySkeleton s = new BodySkeleton();
        s.bone("leg_fl", -0.07f, 0.12f, -0.10f, AXIS_DOWN, 0.12f);
        s.bone("leg_fr", 0.07f, 0.12f, -0.10f, AXIS_DOWN, 0.12f);
        s.bone("leg_bl", -0.09f, 0.12f, 0.14f, AXIS_DOWN, 0.12f);
        s.bone("leg_br", 0.09f, 0.12f, 0.14f, AXIS_DOWN, 0.12f);
        s.bone("head", 0, 0.26f, -0.18f, AXIS_FORWARD, 0.14f);
        s.bone("tail", 0, 0.22f, 0.22f, AXIS_BACK, 0.08f);
        s.torsoY = 0.16f;
        s.torsoRadius = 0.11f;
        return s;
    }

    /**
     * Mirrors {@code CreatureModels.skitterwing}. The four wings are authored
     * along +/-X, an axis this two-angle parameterisation cannot aim, so they
     * stay at rest and only the body, head and tail tumble.
     */
    private static BodySkeleton bird() {
        BodySkeleton s = new BodySkeleton();
        s.bone("head", 0, 0.23f, -0.17f, AXIS_FORWARD, 0.10f);
        s.bone("tail", 0, 0.18f, 0.17f, AXIS_BACK, 0.09f);
        s.torsoY = 0.18f;
        s.torsoRadius = 0.08f;
        return s;
    }

    /** Mirrors {@code NpcModels.build}: legs on the root, arms and head on the torso. */
    private static BodySkeleton buildHumanoid() {
        BodySkeleton s = new BodySkeleton();
        float hipY = 0.86f;
        s.bone("head", 0, hipY + 0.64f, 0, AXIS_UP, 0.26f);
        s.bone("arm_l", -0.30f, hipY + 0.55f, 0, AXIS_DOWN, 0.55f);
        s.bone("arm_r", 0.30f, hipY + 0.55f, 0, AXIS_DOWN, 0.55f);
        s.bone("leg_l", -0.115f, hipY, 0, AXIS_DOWN, 0.86f);
        s.bone("leg_r", 0.115f, hipY, 0, AXIS_DOWN, 0.86f);
        s.torsoY = hipY + 0.31f;
        s.torsoRadius = 0.20f;
        return s;
    }
}
