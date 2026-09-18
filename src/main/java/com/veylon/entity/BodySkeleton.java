package com.veylon.entity;

import com.veylon.entity.Creature.CreatureType;
import java.util.EnumMap;
import java.util.Map;

/**
 * Parent-first joint tree matching the models. Root pivots use model coordinates;
 * child pivots and rest vectors use the parent's frame. Limits are radians.
 * See docs/engineering/RAGDOLL_JOINTS.md for the authoring contract.
 */
public final class BodySkeleton {
    public static final int MAX_BONES = 12;
    public static final int TORSO = -1;
    public enum Joint { CONE, HINGE }
    public final String[] part = new String[MAX_BONES];
    public final int[] parent = new int[MAX_BONES];
    public final Joint[] joint = new Joint[MAX_BONES];
    public final float[] pivotX = new float[MAX_BONES], pivotY = new float[MAX_BONES], pivotZ = new float[MAX_BONES];
    public final float[] restX = new float[MAX_BONES], restY = new float[MAX_BONES], restZ = new float[MAX_BONES];
    public final float[] length = new float[MAX_BONES], radius = new float[MAX_BONES];
    /** Authored socket overlap is exempt from self-contact; distal samples are not. */
    public final float[] selfCollisionStart = new float[MAX_BONES];
    public final float[] minAngle = new float[MAX_BONES], maxAngle = new float[MAX_BONES];
    public int boneCount;
    public float torsoY, torsoRadius;
    /** Oriented support box and self-collision proxy about the torso centre. */
    public float halfX, halfY, halfZ;
    private static final Map<CreatureType, BodySkeleton> CREATURES = new EnumMap<>(CreatureType.class);
    private static final BodySkeleton HUMAN = buildHumanoid();

    private BodySkeleton() { }
    public static synchronized BodySkeleton of(CreatureType type) {
        return CREATURES.computeIfAbsent(type, BodySkeleton::buildCreature);
    }
    public static BodySkeleton humanoid() { return HUMAN; }
    public static BodySkeleton rigid(float width, float height) {
        BodySkeleton s = new BodySkeleton();
        s.body(height * 0.5f, width * 0.5f, height * 0.5f, width * 0.5f);
        return s;
    }
    private void body(float y, float x, float h, float z) {
        torsoY = y; halfX = x; halfY = h; halfZ = z;
        torsoRadius = Math.min(x, Math.min(h, z));
    }
    private int cone(String name, int ancestor, float x, float y, float z,
                     float dx, float dy, float dz, float r, float limit) {
        if (boneCount == MAX_BONES || ancestor >= boneCount || ancestor < TORSO) {
            throw new IllegalArgumentException("invalid skeleton hierarchy: " + name);
        }
        int b = boneCount++;
        part[b] = name; parent[b] = ancestor; joint[b] = Joint.CONE;
        pivotX[b] = x; pivotY[b] = y; pivotZ[b] = z;
        length[b] = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        restX[b] = dx / length[b]; restY[b] = dy / length[b]; restZ[b] = dz / length[b];
        radius[b] = r; maxAngle[b] = limit;
        float sx = x, sy = y - torsoY, sz = z;
        for (int p = ancestor; p >= 0; p = parent[p]) {
            sx += pivotX[p]; sy += pivotY[p]; sz += pivotZ[p];
        }
        float hx = halfX + r, hy = halfY + r, hz = halfZ + r;
        if (Math.abs(sx) <= halfX + 1e-5f && Math.abs(sy) <= halfY + 1e-5f
                && Math.abs(sz) <= halfZ + 1e-5f) {
            float exit = Float.POSITIVE_INFINITY;
            if (Math.abs(dx) > 1e-6f) exit = Math.min(exit, (Math.copySign(hx, dx) - sx) / dx);
            if (Math.abs(dy) > 1e-6f) exit = Math.min(exit, (Math.copySign(hy, dy) - sy) / dy);
            if (Math.abs(dz) > 1e-6f) exit = Math.min(exit, (Math.copySign(hz, dz) - sz) / dz);
            selfCollisionStart[b] = Math.min(1, exit);
        }
        return b;
    }
    private void limb(String upper, String lower, float x, float y, float z,
                      float top, float bottom, float r, boolean reverse) {
        int b = cone(upper, TORSO, x, y, z, 0, -top, 0, r, RagdollConstants.HIP_CONE);
        int c = cone(lower, b, 0, -top, 0, 0, -bottom, 0, r, 0);
        joint[c] = Joint.HINGE;
        minAngle[c] = reverse ? -RagdollConstants.KNEE_BEND : 0;
        maxAngle[c] = reverse ? 0 : RagdollConstants.KNEE_BEND;
    }
    private void tail(float y, float z, float split, float tip, float r) {
        int b = cone("tail", TORSO, 0, y, z, 0, 0, split, r, RagdollConstants.TAIL_CONE);
        cone("tail_tip", b, 0, 0, split, 0, 0, tip, r, RagdollConstants.TAIL_CONE);
    }
    private static BodySkeleton buildHumanoid() {
        BodySkeleton s = new BodySkeleton();
        s.body(1.17f, 0.25f, 0.31f, 0.15f);
        int neck = s.cone("neck", TORSO, 0, 1.44f, 0, 0, 0.06f, 0, 0.06f, RagdollConstants.NECK_CONE);
        s.cone("head", neck, 0, 0.06f, 0, 0, 0.26f, 0, 0.13f, RagdollConstants.HEAD_CONE);
        for (int side = -1; side <= 1; side += 2) {
            String suffix = side < 0 ? "_l" : "_r";
            int arm = s.boneCount;
            s.limb("arm" + suffix, "forearm" + suffix, side * 0.30f, 1.41f, 0,
                    0.26f, 0.275f, 0.075f, false);
            s.maxAngle[arm] = RagdollConstants.SHOULDER_CONE;
            s.maxAngle[arm + 1] = RagdollConstants.ELBOW_BEND;
            s.limb("leg" + suffix, "shin" + suffix, side * 0.115f, 0.86f, 0,
                    0.43f, 0.43f, 0.09f, true);
        }
        return s;
    }
    private static BodySkeleton buildCreature(CreatureType type) {
        return switch (type) {
            case DEER -> quadruped(0.85f, 0.42f, 0.42f, 0.58f, 0.11f,
                    0.16f, -0.06f, 0.30f, 0.12f, 0.44f, 0.04f, 0.06f, 0.04f);
            case WOLF -> quadruped(0.80f, 0.34f, 0.34f, 0.42f, 0.10f,
                    0.06f, -0.05f, 0.26f, 0.10f, 0.42f, 0.16f, 0.17f, 0.05f);
            case THORNHORN -> quadruped(1.25f, 0.72f, 0.72f, 0.55f, 0.20f,
                    -0.02f, -0.10f, 0.40f, 0.10f, 0.65f, 0.10f, 0.13f, 0.07f);
            case STALKER -> quadruped(0.70f, 0.26f, 0.26f, 0.72f, 0.06f,
                    0.10f, -0.06f, 0.30f, 0.02f, 0.36f, 0.18f, 0.20f, 0.025f);
            case HARE -> hare();
            case BIRD -> bird();
            default -> rigid(type.width, type.height);
        };
    }
    private static BodySkeleton quadruped(float len, float width, float height, float leg,
            float legWidth, float headY, float headZ, float headLen,
            float tailY, float tailZ, float tailSplit, float tailTip, float tailRadius) {
        BodySkeleton s = new BodySkeleton();
        float y = leg + height * 0.5f;
        s.body(y, width * 0.5f, height * 0.5f, len * 0.5f);
        String[] names = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
        for (int i = 0; i < 4; i++) {
            s.limb(names[i], names[i] + "_lower", (i % 2 == 0 ? -1 : 1) * width * 0.32f,
                    leg, (i < 2 ? -1 : 1) * len * 0.38f, leg * 0.5f, leg * 0.5f,
                    legWidth * 0.5f, i < 2);
        }
        int neck = s.cone("neck", TORSO, 0, y + height * 0.30f, -len * 0.48f,
                0, headY, headZ, height * 0.22f, RagdollConstants.NECK_CONE);
        s.cone("head", neck, 0, headY, headZ, 0, 0, -headLen,
                height * 0.26f, RagdollConstants.HEAD_CONE);
        s.tail(y + tailY, tailZ, tailSplit, tailTip, tailRadius);
        return s;
    }
    private static BodySkeleton hare() {
        BodySkeleton s = new BodySkeleton();
        s.body(0.18f, 0.13f, 0.11f, 0.19f);
        String[] names = {"leg_fl", "leg_fr", "leg_bl", "leg_br"};
        for (int i = 0; i < 4; i++) {
            s.limb(names[i], names[i] + "_lower", (i % 2 == 0 ? -1 : 1) * (i < 2 ? 0.07f : 0.09f),
                    0.12f, i < 2 ? -0.10f : 0.14f, 0.06f, 0.06f, 0.025f, i < 2);
        }
        int neck = s.cone("head_joint", TORSO, 0, 0.23f, -0.18f, 0, 0.03f, 0, 0.035f, RagdollConstants.NECK_CONE);
        s.cone("head", neck, 0, 0.03f, 0, 0, 0, -0.12f, 0.075f, RagdollConstants.HEAD_CONE);
        s.tail(0.22f, 0.22f, 0.02f, 0.03f, 0.035f);
        return s;
    }
    private static BodySkeleton bird() {
        BodySkeleton s = new BodySkeleton();
        s.body(0.18f, 0.08f, 0.07f, 0.15f);
        s.cone("head", TORSO, 0, 0.23f, -0.17f, 0, 0, -0.10f, 0.05f, RagdollConstants.HEAD_CONE);
        s.cone("tail", TORSO, 0, 0.18f, 0.17f, 0, 0, 0.11f, 0.025f, RagdollConstants.TAIL_CONE);
        for (int pair = 0; pair < 2; pair++) {
            for (int side = -1; side <= 1; side += 2) {
                s.cone("wing" + pair + (side < 0 ? "_l" : "_r"), TORSO,
                        side * 0.08f, 0.23f, -0.06f + pair * 0.14f,
                        side * 0.31f, 0, 0, 0.018f, RagdollConstants.WING_CONE);
            }
        }
        return s;
    }
}
