package com.veylon.entity;

/**
 * Tuning for living bodies on fire ({@link CombustionSystem}). Per-kind values
 * (heat gain, afterburn granted, nominal intensity) live on
 * {@link CombustionSource}. The numbers are the proposals of the all-living
 * combat and fire contract, section 15; changing one is a gameplay change.
 */
public final class CombustionConstants {

    private CombustionConstants() {
    }

    /** Heat a body loses per second out of contact, so brief grazes never add up. */
    public static final float HEAT_DECAY_PER_SECOND = 2f;
    /** Hard ceiling on afterburn fuel, whatever refreshes it. */
    public static final float MAX_FUEL_SECONDS = 8f;
    /** The last seconds of fuel over which the flames shrink to {@link #MIN_INTENSITY}. */
    public static final float FADE_SECONDS = 3f;
    /** Intensity of a burning body whose fuel is nearly gone. */
    public static final float MIN_INTENSITY = 0.35f;
    /**
     * Slack on every timer threshold (fuel gone, rain soak, injury time), so
     * float drift over a run of 0.05 s ticks cannot add or drop a tick.
     */
    public static final float TIMER_EPSILON = 1e-4f;

    /** Damage per second while a flame touches the body, at intensity 1; the legacy pool rates. */
    public static final float CONTACT_DPS_PLAYER = 6f;
    public static final float CONTACT_DPS_NPC = 10f;
    public static final float CONTACT_DPS_CREATURE = 12f;
    /** Damage per second of afterburn, at intensity 1. */
    public static final float AFTERBURN_DPS_PLAYER = 2f;
    public static final float AFTERBURN_DPS_NPC = 3f;
    public static final float AFTERBURN_DPS_CREATURE = 2.5f;

    /**
     * Share of a body's height under water, in the column under its centre, at
     * which its torso counts as immersed: the flames go out at once and it
     * cannot catch. Water is whole cells, so a person standing in one cell of
     * water (1 of 1.75 blocks, legs and hips) is not immersed and every animal
     * is.
     */
    public static final float IMMERSION_FRACTION = 0.6f;
    /** Out of contact, fuel burns this many times faster while some of the body is in water. */
    public static final float SHALLOW_WATER_DRAIN = 3f;
    /** Seconds of rain falling on a burning body's head that put it out. */
    public static final float RAIN_EXTINGUISH_SECONDS = 1.5f;

    /** Seconds alight in one episode after which the Survival player carries a burn injury. */
    public static final float BURN_INJURY_AFTER_SECONDS = 1f;
    /** Seconds at intensity 1 that scorch a body completely; presentation input only. */
    public static final float SCORCH_SECONDS = 10f;
    /** Longest episode {@code burnSeconds} counts, so a body left in a campfire stays finite. */
    public static final float MAX_BURN_SECONDS = 600f;
    /** Least red flash kept on the player's screen per unit of intensity during afterburn. */
    public static final float AFTERBURN_FLASH = 0.5f;
    /**
     * The player's bottles a body remembers having reported as attacks, so a
     * person is attacked once per bottle; beyond this the oldest is forgotten.
     */
    public static final int REPORTED_BOTTLES = 4;
}
