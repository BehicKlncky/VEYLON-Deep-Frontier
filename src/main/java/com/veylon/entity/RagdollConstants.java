package com.veylon.entity;

/**
 * Tuning for death ragdolls: the fixed-step solver, the settle heuristic and
 * the resting poses bodies are steered into.
 *
 * <p>Gravity, the water clamp, the water drag and the axis sub-step deliberately
 * repeat {@link VoxelPhysics}'s numbers rather than deriving new ones, so a
 * tumbling body falls at the same rate as the animal did a moment earlier.
 */
public final class RagdollConstants {

    private RagdollConstants() {
    }

    /** Live bodies allowed at once; over the cap the oldest settles immediately. */
    public static final int MAX_LIVE = 12;

    /** Solver step. Frame time is accumulated and drained in whole steps. */
    public static final float FIXED_STEP = 1f / 60f;
    /** Catch-up cap, mirroring the scheduler's "stalls clamp, they don't spiral". */
    public static final int MAX_STEPS_PER_FRAME = 4;

    // ---- Settling ----------------------------------------------------
    /** Hard timeout: a body must never stay unsettled longer than this. */
    public static final float SETTLE_TIMEOUT = 6f;
    /** Consecutive quiet steps required before a body freezes. */
    public static final int SETTLE_STEPS = 8;
    /**
     * Quiet threshold. The measure sums squared point speeds, squared angular
     * speeds and the squared orientation error, so a body that is still sliding,
     * still spinning or still standing upright cannot pass it.
     */
    public static final float SETTLE_ENERGY = 0.55f;

    // ---- Integration -------------------------------------------------
    public static final float GRAVITY_AIR = 26f;
    public static final float GRAVITY_WATER = 7f;
    public static final float WATER_MAX_DESCENT = -2.2f;
    public static final float WATER_DRAG = 0.5f;
    /** Per-axis sub-step; matches VoxelPhysics so nothing tunnels. */
    public static final float MAX_AXIS_SUBSTEP = 0.2f;
    /** Half-extent of the little box each point mass sweeps. */
    public static final float POINT_RADIUS = 0.07f;
    /** Fraction of speed shed per fixed step in air. */
    public static final float AIR_DRAG = 0.012f;
    /** Fraction of tangential speed shed per fixed step while touching ground. */
    public static final float GROUND_FRICTION = 0.26f;
    /** Restitution on a voxel face. Bodies thud; they do not bounce. */
    public static final float BOUNCE = 0.10f;
    public static final float MAX_POINT_SPEED = 42f;

    // ---- Articulation ------------------------------------------------
    public static final int RELAX_ITERATIONS = 3;
    /** Pull toward the bone's rest offset; stands in for real joint limits. */
    public static final float JOINT_REST_PULL = 0.26f;
    /** How much of a joint correction is fed back into the torso. */
    public static final float TORSO_REACTION = 0.32f;
    /** How strongly a snagged limb torques the body. */
    public static final float TORQUE_GAIN = 2.2f;

    // ---- Angular state -----------------------------------------------
    /** Angular speed shed per second in flight. */
    public static final float ANGULAR_DAMPING = 0.55f;
    /** Angular speed shed per second once the body is on the ground. */
    public static final float GROUND_ANGULAR_DAMPING = 3.4f;
    /** Spring that rolls a grounded body onto its resting side. */
    public static final float SETTLE_TORQUE = 8.5f;
    public static final float MAX_ANGULAR_SPEED = 13f;
    /** Angular speed handed to the body by the killing blow. */
    public static final float ANGULAR_KICK = 1.4f;

    /** Animals keel over sideways; the value matches the long-standing carcass pose. */
    public static final float CREATURE_REST_ROLL = 1.45f;
    /** People fall face down or face up rather than rolling onto a flank. */
    public static final float HUMAN_REST_PITCH = 1.45f;
    public static final float CREATURE_LIFT = 0.16f;
    public static final float HUMAN_LIFT = 0.08f;

    // ---- Presentation ------------------------------------------------
    /** Seconds between blood drips while a body is still moving. */
    public static final float DRIP_INTERVAL = 0.12f;
    /** Speed a body must exceed before it drips. */
    public static final float DRIP_SPEED = 1.6f;

    // ---- Housekeeping ------------------------------------------------
    /** Matches EntityManager's creature despawn radius. */
    public static final float DESPAWN_DISTANCE = 170f;
    /** Seconds a human corpse lies in the world; matches Carcass. */
    public static final float CORPSE_DECAY = 420f;
}
