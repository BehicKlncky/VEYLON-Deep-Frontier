package com.veylon.simulation;

/**
 * Tuning for the burning liquid a shattered fire bomb spills. Contact
 * affliction and keg fuse values are shared with block fire through
 * {@link FireConstants} so the two kinds of flame cannot drift apart.
 */
public final class LiquidFireConstants {

    private LiquidFireConstants() {
    }

    /** Hard cap on live patches across every spill. */
    public static final int MAX_PATCHES = 160;
    /** Surface cells one bottle can cover. */
    public static final int MAX_PATCHES_PER_SPILL = 22;
    /** Largest horizontal distance, in cells, from the impact cell to a patch. */
    public static final float SPILL_RADIUS = 3.2f;
    /** Blocks the liquid may fall between two neighbouring cells of the fill. */
    public static final int MAX_DROP = 2;
    /**
     * Blocks below the impact cell searched for ground, so a bottle that
     * shatters against a wall runs down it.
     */
    public static final int SURFACE_SEARCH_DEPTH = 3;
    /**
     * How strongly the fill prefers cells ahead of the throw: the frontier is
     * ordered by {@code distance - DIRECTION_BIAS * dot(throw, offset)}.
     */
    public static final float DIRECTION_BIAS = 0.6f;

    /** Seconds the edge of a pool burns, before the centre bonus and jitter. */
    public static final float BURN_SECONDS_MIN = 7f;
    /** Extra seconds at the centre, falling off linearly to none at the rim. */
    public static final float BURN_SECONDS_RANGE = 4f;
    /** Largest random extension of a patch's burn time. */
    public static final float BURN_SECONDS_JITTER = 1f;
    /** Intensity lost from the centre to the rim, where it is {@code 1 - this}. */
    public static final float RIM_INTENSITY_LOSS = 0.5f;

    /** Damage per second to the player standing in a patch. */
    public static final float ENTITY_DPS_PLAYER = 6f;
    /** Damage per second to an NPC standing in a patch. */
    public static final float ENTITY_DPS_NPC = 10f;
    /** Damage per second to a creature standing in a patch. */
    public static final float ENTITY_DPS_CREATURE = 12f;
    /** Feet up to this far above a patch's cell floor count as standing in it. */
    public static final float CONTACT_HALF_HEIGHT = 0.6f;
    /** Feet down to this far below a patch's cell floor still count. */
    public static final float CONTACT_BELOW = 0.1f;

    /**
     * Chance per patch per medium tick, for each flammable block in or beside
     * its cell, that a full-intensity patch sets that block alight.
     */
    public static final float IGNITE_CHANCE_PER_TICK = 0.35f;
    /** Seconds of rain that put out a patch open to the sky. */
    public static final float RAIN_EXTINGUISH_SECONDS = 1f;

    /** Radius of the noise a bottle makes when it shatters; the fire bomb's own. */
    public static final float SPILL_NOISE_RADIUS = 40f;
    public static final float SPILL_NOISE_INTENSITY = 0.8f;

    /**
     * Hard cap on remembered (NPC, spill) pairs, which is how an NPC counts as
     * attacked once per bottle however many ticks it stands in the fire.
     */
    public static final int MAX_TRACKED_NPC_SPILLS = 64;
}
