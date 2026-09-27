package com.veylon.ai;

/**
 * One person's or animal's flight from the fire on its own body: whether it is
 * panicking, the goal it is running to, how it is turning and how long until it
 * decides again. Every {@code Npc} and {@code Creature} carries one.
 *
 * <p>Only {@link FirePanic} writes it: the fields are package-private and the
 * rest of the game reads the getters. Transient: never saved. A body that
 * leaves the world and comes back, by loading or a settlement waking, is a new
 * entity with a calm intent.
 */
public final class PanicIntent {

    /** Panicking: alight now, or in the recovery after the flames went out. */
    boolean active;
    /** Whether the body was alight at its last AI tick. */
    boolean alight;
    /** Seconds of recovery left; held full while alight. */
    float recovery;

    boolean hasGoal;
    float goalX, goalY, goalZ;
    /** Seconds until the next timed goal. */
    float goalTimer;
    /** Seconds until any new goal may be chosen. */
    float replanCooldown;
    /** Unit heading in the horizontal plane, turned toward the goal at a limited rate. */
    float headingX, headingZ;
    /** Share of the panic speed this goal is run at. */
    float stride;

    /** Seconds of blocked progress. */
    float stuckSeconds;
    /** Where the body stood when its last tick ended, and how far it meant to move then. */
    float lastX, lastY, lastZ, lastStep;

    int goals;
    int replans;
    int trappedGoals;

    /** Calm. */
    public PanicIntent() {
    }

    /** Panicking: alight now, or still fleeing in the recovery after the flames went out. */
    public boolean active() {
        return active;
    }

    /** Panicking though no longer alight: the recovery before it decides afresh. */
    public boolean recovering() {
        return active && !alight;
    }

    /** Seconds of recovery left; {@link PanicConstants#RECOVERY_SECONDS} while alight, 0 when calm. */
    public float recoverySeconds() {
        return active ? recovery : 0f;
    }

    public boolean hasGoal() {
        return hasGoal;
    }

    public float goalX() {
        return goalX;
    }

    public float goalY() {
        return goalY;
    }

    public float goalZ() {
        return goalZ;
    }

    /** Unit horizontal heading the body is running on. */
    public float headingX() {
        return headingX;
    }

    public float headingZ() {
        return headingZ;
    }

    /** Goals chosen in this panic, timed, reached and blocked alike. */
    public int goals() {
        return goals;
    }

    /** Of those, the ones chosen because the body stopped making progress. */
    public int replans() {
        return replans;
    }

    /** Of those, the ones that found no way out at all: the body struggles where it is. */
    public int trappedGoals() {
        return trappedGoals;
    }

    void clear() {
        active = false;
        alight = false;
        recovery = 0f;
        hasGoal = false;
        goalX = goalY = goalZ = 0f;
        goalTimer = 0f;
        replanCooldown = 0f;
        headingX = 0f;
        headingZ = 0f;
        stride = 1f;
        stuckSeconds = 0f;
        lastX = lastY = lastZ = lastStep = 0f;
        goals = 0;
        replans = 0;
        trappedGoals = 0;
    }
}
