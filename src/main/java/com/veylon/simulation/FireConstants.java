package com.veylon.simulation;

/**
 * Fire spread, burn duration, suppression and contact damage tuning.
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

    /** Squared distance within which fire damages an entity. */
    public static final float CONTACT_RANGE_SQ = 2.4f;
    /** Damage per second dealt to the player standing in fire. */
    public static final float PLAYER_BURN_DPS = 4f * 2;
    /** Damage per second dealt to creatures. */
    public static final float CREATURE_BURN_DPS = 5f * 2;
    /** Damage per second dealt to NPCs. */
    public static final float NPC_BURN_DPS = 4f * 2;
    /** Chance per damaging tick that contact inflicts the burn affliction. */
    public static final float BURN_AFFLICTION_CHANCE = 0.5f;
    public static final float BURN_AFFLICTION_SECONDS_MIN = 60f;
    public static final float BURN_AFFLICTION_SECONDS_RANGE = 40f;

    /** Fuse length in seconds given to a keg touched by open flame. */
    public static final float KEG_FUSE_SECONDS = 1.5f;
    public static final float KEG_FUSE_NOISE_RADIUS = 12f;
    public static final float KEG_FUSE_NOISE_STRENGTH = 0.4f;

    /** Blocks within which a lantern running dry is reported to the player. */
    public static final float LANTERN_NOTICE_RANGE = 32f;
}
