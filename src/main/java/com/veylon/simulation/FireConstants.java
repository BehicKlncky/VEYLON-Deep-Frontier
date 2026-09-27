package com.veylon.simulation;

/**
 * Fire spread, burn duration, suppression and flame shape tuning. What a flame
 * does to a body that touches it is tuned with the body's fire, in
 * {@code entity.CombustionConstants} and {@code entity.CombustionSource}.
 *
 * <p>Extracted verbatim from {@link FireSystem}; changing one is a gameplay
 * change, not a refactor.
 */
public final class FireConstants {

    private FireConstants() {
    }

    // The active-fire ceiling deliberately stays on FireSystem: it is already a
    // named public constant and RuntimeBudgetSnapshot and RuntimeBoundsTest
    // reference it as FireSystem.MAX_ACTIVE_FIRES.

    /** Seconds a freshly lit cell burns, before the random extension. */
    public static final float BURN_SECONDS_MIN = 4f;
    /** Additional random seconds on top of the minimum. */
    public static final float BURN_SECONDS_RANGE = 6f;

    /**
     * Chance per medium tick that a burning cell tries to spread to one of its
     * flammable neighbours that is not already burning.
     */
    public static final float SPREAD_CHANCE = 0.22f;
    /**
     * Fire climbs: a spread attempt aimed at the layer above succeeds this many
     * times as often. It is what carries a fire up a bare trunk, where the log
     * above is the only fuel within reach.
     */
    public static final float UPWARD_SPREAD_BIAS = 2f;
    /**
     * Precipitation cuts spread from sheltered cells to this fraction; cells
     * that are rained on do not spread at all.
     */
    public static final float RAIN_SPREAD_FACTOR = 0.12f;
    /** Sky light above which a cell counts as rained on. */
    public static final float RAIN_EXPOSURE_SKYLIGHT = 0.9f;
    /**
     * Seconds of rain that put a burning cell out. Its burn timer is frozen
     * while it is rained on, so the block survives unburnt.
     */
    public static final float RAIN_EXTINGUISH_SECONDS = 2f;
    /** Strength of each of the two smoke puffs a doused cell gives off. */
    public static final float EXTINGUISH_SMOKE = 0.6f;
    /** Rain drains exposed campfire fuel this many times faster. */
    public static final float RAIN_CAMPFIRE_DRAIN_MULT = 2.2f;

    /**
     * How far a burning block's flames reach out of each open face into the
     * cell beside or below it. Faces against a solid block give no flame, so
     * heat never crosses a wall or a floor.
     */
    public static final float FLAME_FACE_REACH = 0.25f;
    /** How far above a burning block's top the flames rise, when the cell above is open. */
    public static final float FLAME_PLUME_HEIGHT = 0.75f;
    /**
     * A placed torch's flame, in its own cell: the burning head, a little
     * wider and taller than the drawn head so a hand brushing it counts. It
     * stops at the cell's top, so nobody standing on the block beside a torch
     * treads in its tip.
     */
    public static final float TORCH_FLAME_HALF_WIDTH = 0.12f;
    public static final float TORCH_FLAME_BOTTOM = 0.66f;
    public static final float TORCH_FLAME_TOP = 1.0f;
    /** A fueled campfire's flames, in its own cell: over the logs, up to the cell's top. */
    public static final float CAMPFIRE_FLAME_HALF_WIDTH = 0.25f;
    public static final float CAMPFIRE_FLAME_BOTTOM = 0.15f;
    public static final float CAMPFIRE_FLAME_TOP = 1.0f;

    /** The medical burn injury's length: this, plus up to the range at random. */
    public static final float BURN_AFFLICTION_SECONDS_MIN = 60f;
    public static final float BURN_AFFLICTION_SECONDS_RANGE = 40f;

    /** Fuse length in seconds given to a keg touched by open flame. */
    public static final float KEG_FUSE_SECONDS = 1.5f;
    public static final float KEG_FUSE_NOISE_RADIUS = 12f;
    public static final float KEG_FUSE_NOISE_STRENGTH = 0.4f;

    /** Blocks within which a lantern running dry is reported to the player. */
    public static final float LANTERN_NOTICE_RANGE = 32f;
}
