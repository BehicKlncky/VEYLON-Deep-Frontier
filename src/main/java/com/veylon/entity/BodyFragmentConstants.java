package com.veylon.entity;

/**
 * Tuning for a body blown apart at its joints.
 *
 * <p>Gravity, the water rules, air drag, ground friction, the speed clamps, the
 * fixed step, the corpse decay and the despawn radius are deliberately not
 * repeated here: {@link BodyFragmentSystem} reads them from
 * {@link RagdollConstants}, so a severed limb falls, drags and rots exactly like
 * the whole body it came from. What lives here is only what a loose rigid piece
 * needs that an articulated body does not.
 */
public final class BodyFragmentConstants {

    private BodyFragmentConstants() {
    }

    // ---- Population --------------------------------------------------
    /** Pieces in flight at once: twelve bodies of ten. Over it the oldest settles. */
    public static final int MAX_LIVE_FRAGMENTS = 120;
    /** Pieces lying in the world. Over it the oldest is removed. */
    public static final int MAX_SETTLED_FRAGMENTS = 600;

    // ---- Launch ------------------------------------------------------
    /**
     * Blast impulse per unit of strength, before falloff. A piece's launch speed
     * is this times strength times falloff times its inverse mass.
     */
    public static final float IMPULSE_BASE = 9f;
    /** Upward speed every piece gets on top of the blast, so nothing skids flat. */
    public static final float UPWARD_BIAS = 4.5f;
    /** Spin per metre of lever arm between a piece and the body's mass centre. */
    public static final float SPIN_GAIN = 6f;
    /** Falloff reaches its floor at {@code strength × FALLOFF_RANGE} from the blast. */
    public static final float FALLOFF_RANGE = 1.5f;
    /** Falloff floor: a piece at the edge of the blast still gets a quarter of it. */
    public static final float MIN_FALLOFF = 0.25f;
    /**
     * Mass per cubic metre of model box. Mass is proportional to box volume, so
     * a forearm is about 14 times lighter than the torso and leaves the blast
     * about 14 times faster. At 260 a forearm 2 m from a powder keg (strength
     * 3.8) leaves at about 16 m/s and the torso at about 1.2 m/s; even
     * point-blank a forearm stays near 25 m/s, well under
     * {@link RagdollConstants#MAX_POINT_SPEED}.
     */
    public static final float DENSITY = 260f;
    /**
     * Sideways scatter as a fraction of a piece's blast speed. It is always
     * perpendicular to the blast direction, so it spreads the pieces without
     * ever turning one back towards the blast.
     */
    public static final float TANGENT_JITTER = 0.2f;
    /** Extra spin per axis from the position hash, rad/s. */
    public static final float SPIN_JITTER = 2.5f;
    /** Closer than this a piece has no usable blast direction and is thrown up. */
    public static final float DEGENERATE_DISTANCE = 1e-4f;

    // ---- Contact -----------------------------------------------------
    /** Restitution on a voxel face. Meat thuds, but a light piece still hops. */
    public static final float BOUNCE_FRAGMENT = 0.2f;
    /** Angular speed shed per second in flight. */
    public static final float AIR_ANGULAR_DAMPING = 0.4f;
    /** Angular speed shed per second while resting on or striking the ground. */
    public static final float GROUND_ANGULAR_DAMPING = 9f;
    /**
     * Rotational inertia about the contact corner, as a multiple of mass times
     * the squared half-diagonal. It turns the gravity torque about the lowest
     * corner into an angular acceleration; a box's is about 0.9, rounded up so
     * toppling reads as heavy rather than snappy.
     */
    public static final float TOPPLE_INERTIA = 1.2f;
    /**
     * How far off horizontal a box axis may tilt (as the sine of the tilt) and
     * still count as lying flat. Inside this band the support is a face or an
     * edge rather than one corner, so the gravity torque fades out smoothly
     * instead of flipping sign every step.
     */
    public static final float FLAT_BAND = 0.15f;
    /** Smallest collision half extent, so a thin piece still sweeps a real box. */
    public static final float MIN_HALF_EXTENT = 0.05f;
    /** Clearance kept when a turning piece is lifted off the face it rests on. */
    public static final float LIFT_SKIN = 1e-4f;

    // ---- Recovery ----------------------------------------------------
    /**
     * First push-out probe. A person standing exactly on a block face can put a
     * shin a rounding error into it; this frees it without a visible hop.
     */
    public static final float PUSH_FREE_SKIN = 0.002f;
    /** Distance per probe when pushing a piece out of geometry. */
    public static final float PUSH_FREE_STEP = 0.05f;
    /** Probes per direction; 24 × 0.05 m reaches 1.2 m. */
    public static final int PUSH_FREE_STEPS = 24;
    /**
     * How far a piece frozen in mid-air (by the cap, a save or the timeout) is
     * swept down to whatever is below it, so nothing is left floating for the
     * seven minutes it takes to rot.
     */
    public static final float FORCED_SETTLE_DROP = 48f;

    // ---- Settling ----------------------------------------------------
    /** Maximum squared speed plus squared angular speed of a quiet piece. */
    public static final float SETTLE_ENERGY = 0.25f;
    /** Consecutive quiet grounded steps before a piece freezes. */
    public static final int SETTLE_STEPS = 12;
    /** Hard timeout: a piece never stays unsettled longer than this. */
    public static final float SETTLE_TIMEOUT = 8f;

    // ---- Presentation ------------------------------------------------
    /**
     * Body-size scale handed to {@code ParticleSystem.bloodBurst} for each
     * severed joint. Nine joints at 0.6 emit about 144 particles, against the
     * 27 of one whole-body death burst.
     */
    public static final float JOINT_BURST_POWER = 0.6f;
}
