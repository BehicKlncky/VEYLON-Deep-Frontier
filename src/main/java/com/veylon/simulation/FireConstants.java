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

    /** Chance per medium tick that a burning cell tries to spread. */
    public static final float SPREAD_CHANCE = 0.22f;
    /** Precipitation cuts spread to this fraction. */
    public static final float RAIN_SPREAD_FACTOR = 0.12f;
    /** Sky light above which a cell counts as rained on. */
    public static final float RAIN_EXPOSURE_SKYLIGHT = 0.9f;
    /** Rain burns exposed fires out this many times faster. */
    public static final float RAIN_BURN_RATE_MULT = 3.5f;
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
