package com.veylon.ai;

/**
 * Tuning for people and animals fleeing the fire on their own bodies
 * ({@link FirePanic}). The numbers are the proposals of the all-living combat
 * and fire contract, sections 12 and 15, plus the few the milestone that built
 * the panic added; changing one is a gameplay change.
 */
public final class PanicConstants {

    private PanicConstants() {
    }

    /** Least seconds a goal is kept before a new one is drawn. */
    public static final float GOAL_INTERVAL = 0.6f;
    /** Most seconds a seeded draw adds to {@link #GOAL_INTERVAL}, so a crowd does not turn in step. */
    public static final float GOAL_INTERVAL_JITTER = 0.6f;
    /** How far away a walker's goal is drawn, blocks. */
    public static final float GOAL_DISTANCE_MIN = 4f;
    public static final float GOAL_DISTANCE_MAX = 8f;
    /** How far away, level, a bird's goal is drawn, blocks. */
    public static final float FLIGHT_DISTANCE_MIN = 6f;
    public static final float FLIGHT_DISTANCE_MAX = 10f;
    /** How far a bird's goal climbs above it, blocks. */
    public static final float CLIMB_MIN = 3f;
    public static final float CLIMB_MAX = 6f;
    /** Highest a bird's goal lies above the ground under it, blocks: about the top of its ordinary flight. */
    public static final float FLIGHT_CEILING_ABOVE_GROUND = 16f;
    /** Largest turn of a drawn goal away from the escape direction, degrees either side. */
    public static final float SPREAD_DEGREES = 70f;
    /**
     * Weight of "away from where the flame touched" in the escape direction
     * once the body is out of the flames; the rest is the way it is already
     * running. While a flame touches it, it runs straight away.
     */
    public static final float AFTER_CONTACT_AWAY_WEIGHT = 0.5f;

    /** An animal's panic speed as a multiple of its species' speed; the existing flee multiplier. */
    public static final float CREATURE_SPEED_MUL = 1.6f;
    /** A person's panic speed as a multiple of their walking speed. */
    public static final float NPC_SPEED_MUL = 1.35f;
    /** Walking speed of a person with no archetype (camp member, wandering trader, raider): a villager's. */
    public static final float LEGACY_PERSON_SPEED = 3.2f;
    /** Least share of the panic speed a goal is run at; a seeded draw decides each goal's, up to all of it. */
    public static final float MIN_STRIDE = 0.85f;
    /** Share of the panic speed left at the end of the recovery; all of it at its start. */
    public static final float RECOVERY_STRIDE = 0.55f;
    /** Fastest a panicking body turns, degrees per second. */
    public static final float TURN_RATE_DEGREES = 270f;

    /** Least seconds between two goals, whatever asks for one: at most four a second per body. */
    public static final float REPLAN_COOLDOWN = 0.25f;
    /** Seconds of blocked progress after which a body gives up its goal. */
    public static final float STUCK_SECONDS = 0.2f;
    /** Share of the step it meant to take below which a tick counts as blocked. */
    public static final float STUCK_PROGRESS = 0.3f;
    /** A goal is reached within this many blocks. */
    public static final float ARRIVE = 0.8f;
    /**
     * Turn between the directions a goal tries after the drawn one, degrees:
     * 50, 100 and 150 either side, then straight back, eight in all.
     */
    public static final float CANDIDATE_STEP_DEGREES = 50f;
    /** A blocked body's next goal leaves its blocked heading by at least this much, degrees. */
    public static final float BLOCKED_EXCLUSION_DEGREES = 30f;

    /** Seconds a body keeps fleeing after its flames go out, before it decides afresh. */
    public static final float RECOVERY_SECONDS = 1.5f;
    /** Slack on the recovery timer, so float drift over 0.05 s ticks cannot add or drop a tick. */
    public static final float TIMER_EPSILON = 1e-4f;
}
