package com.veylon.entity;

import com.veylon.item.ItemType;

/**
 * One body in the seconds between the killing blow and coming to rest.
 *
 * <p>A ragdoll owns its own point-mass state and writes a {@link BodyPose} the
 * renderer stamps into the species' shared {@code EntityModel} immediately
 * before drawing. Nothing here holds the dead {@link Entity} — that is removed
 * from the world on the tick it died, with every consequence already fired —
 * so the ragdoll also carries whatever the resting corpse will need: the
 * creature's species and lodged arrows, or the person's appearance.
 *
 * <p>The torso is one point carrying the orientation; every other point is an
 * appendage tied to a bone by a distance constraint. Solving happens in
 * {@link RagdollSystem}; this is state only.
 */
public class Ragdoll {

    /** Point 0 is always the torso; 1..boneCount are the appendages. */
    private static final int MAX_POINTS = BodySkeleton.MAX_BONES + 1;
    public static final int TORSO = 0;

    public final BodySkeleton skeleton;
    /** Null for a human body. */
    public final Creature.CreatureType creatureType;
    /** Birds tumble and vanish; everything else leaves something behind. */
    public final boolean leavesBody;

    // ---- Carried payload -------------------------------------------
    /** Arrows lodged in the animal, handed on to the carcass so they stay recoverable. */
    public int stuckArrows;
    public ItemType stuckArrowType;
    public final NpcAppearance appearance = new NpcAppearance();

    // ---- Point masses ----------------------------------------------
    public final float[] px = new float[MAX_POINTS];
    public final float[] py = new float[MAX_POINTS];
    public final float[] pz = new float[MAX_POINTS];
    public final float[] vx = new float[MAX_POINTS];
    public final float[] vy = new float[MAX_POINTS];
    public final float[] vz = new float[MAX_POINTS];
    public int pointCount;

    // ---- Orientation ------------------------------------------------
    public float yaw;
    public float pitch;
    public float roll;
    public float yawVel;
    public float pitchVel;
    public float rollVel;
    /** Orientation a grounded body is steered toward; how it ends up lying. */
    public float restPitch;
    public float restRoll;

    // ---- Lifecycle --------------------------------------------------
    public float age;
    public int quietSteps;
    public boolean grounded;
    public boolean settled;
    public float dripTimer;
    /** Last measured settle energy, for tests and the debug overlay. */
    public float energy;

    public final BodyPose pose = new BodyPose();

    public Ragdoll(BodySkeleton skeleton, Creature.CreatureType creatureType,
                   boolean leavesBody) {
        this.skeleton = skeleton;
        this.creatureType = creatureType;
        this.leavesBody = leavesBody;
        this.pointCount = skeleton.boneCount + 1;
        pose.boneCount = skeleton.boneCount;
        pose.pivotY = skeleton.torsoY;
        pose.solved = true;
    }

    public boolean human() {
        return creatureType == null;
    }

    public float torsoSpeedSq() {
        return vx[TORSO] * vx[TORSO] + vy[TORSO] * vy[TORSO] + vz[TORSO] * vz[TORSO];
    }

    public double distSqTo(float x, float y, float z) {
        double dx = px[TORSO] - x;
        double dy = py[TORSO] - y;
        double dz = pz[TORSO] - z;
        return dx * dx + dy * dy + dz * dz;
    }
}
