package com.veylon.entity;

/**
 * Tuning for death ragdolls: fixed-step integration, joint limits and contacts.
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
    public static final int SETTLE_STEPS = 12;
    /**
     * Maximum squared point speed plus squared angular speed. No attitude target
     * participates in settling; support and joint contacts stop the body.
     */
    public static final float SETTLE_ENERGY = 0.25f;
    /** Sleep through bounded contact jitter, but never through a moving limb. */
    public static final int SUPPORT_SLEEP_STEPS = 30;
    public static final float SLEEP_DISTANCE = 0.04f;
    public static final float SLEEP_ANGLE = 0.06f;

    // ---- Integration -------------------------------------------------
    public static final float GRAVITY_AIR = 26f;
    public static final float GRAVITY_WATER = 7f;
    public static final float WATER_MAX_DESCENT = -2.2f;
    public static final float WATER_DRAG = 0.5f;
    /** Per-axis sub-step; matches VoxelPhysics so nothing tunnels. */
    public static final float MAX_AXIS_SUBSTEP = 0.2f;
    /** Fraction of speed shed per fixed step in air. */
    public static final float AIR_DRAG = 0.012f;
    /** Fraction of tangential speed shed per fixed step while touching ground. */
    public static final float GROUND_FRICTION = 0.26f;
    /** Coulomb tangent correction relative to the contact's normal correction. */
    public static final float CONTACT_FRICTION = 0.65f;
    /** Restitution on a voxel face. Bodies thud; they do not bounce. */
    public static final float BOUNCE = 0.10f;
    public static final float MAX_POINT_SPEED = 42f;

    // ---- Articulation ------------------------------------------------
    public static final int RELAX_ITERATIONS = 6;
    public static final float SHOULDER_CONE = 2.4f;
    public static final float HIP_CONE = 1.35f;
    public static final float NECK_CONE = 0.7f;
    public static final float HEAD_CONE = 0.85f;
    public static final float TAIL_CONE = 1.4f;
    public static final float WING_CONE = 1.9f;
    public static final float ELBOW_BEND = 2.6f;
    public static final float KNEE_BEND = 2.5f;
    /** Segment contacts have no gap larger than this, including narrow appendages. */
    public static final float CONTACT_SPACING = 0.10f;
    public static final float CONTACT_SKIN = 0.004f;
    /** Contact persistence across the collision skin, preventing sleep chatter. */
    public static final float SUPPORT_SLOP = 0.012f;
    public static final float ROOT_INVERSE_MASS = 0.08f;
    public static final float JOINT_REACTION = 0.35f;
    public static final float SUPPORT_INERTIA = 4f;

    // ---- Angular state -----------------------------------------------
    /** Angular speed shed per second in flight. */
    public static final float ANGULAR_DAMPING = 0.55f;
    /** Angular speed shed per second once the body is on the ground. */
    public static final float GROUND_ANGULAR_DAMPING = 3.4f;
    public static final float MAX_ANGULAR_SPEED = 13f;
    /** Angular speed handed to the body by the killing blow. */
    public static final float ANGULAR_KICK = 1.4f;
    public static final float MIN_TOPPLE_SPEED = 1.5f;

    /** Only for unsolved legacy/QA carcasses; never read by the physics solver. */
    public static final float FALLBACK_CREATURE_ROLL = 1.45f;

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
