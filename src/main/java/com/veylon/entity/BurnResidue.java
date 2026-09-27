package com.veylon.entity;

import static com.veylon.entity.CombustionConstants.RESIDUE_SMOKE_SECONDS;

/**
 * What a body's fire leaves on the remains of that body: how scorched it
 * was, and for a body that died alight, flames that die down over a few
 * seconds and then smoke. Captured once, at the death transition, by {@link
 * BurnResidueSystem#capture}; presentation only.
 *
 * <p>It is a snapshot. It copies numbers from the dead body's {@link
 * BodyCombustion} and refers to nothing: not the entity, not the player, not
 * the remains that carry it. One body leaves at most one residue, and exactly
 * one thing carries it at a time — the {@link Ragdoll}, then the {@link
 * HumanCorpse} or {@link Carcass} it settles into — or every piece of the body
 * a blast blew apart shares the same one ({@link BodyFragment#burn}, each
 * piece drawing its {@link BodyFragment#burnShare} of it), so the flames of
 * one body are never counted twice.
 *
 * <p>It is not a fire. Nothing samples it as a flame, it deals no damage,
 * cannot set anything alight and is never saved: a load, a new world and
 * every reset leave remains without one. Outside this package it is read-only.
 */
public final class BurnResidue {

    /** How scorched the body was when it died, 0..1; the remains keep it as long as they last. */
    final float scorch;
    /** How strongly the body was burning when it died, 0 when it was not alight. */
    final float flame;
    /** Seconds after death the flames end; cut short when water or rain puts them out. */
    float flameSeconds;
    /** Seconds since the body died, advanced only while the world is simulated. */
    float age;
    /** Seconds of open rain on the remains while their flames last. */
    float soak;
    /** Whether water or rain put the flames out early. */
    boolean doused;
    /** Where the core of the remains is now: a ragdoll's torso, the torso piece, a corpse. */
    float x, y, z;
    /** Ragdolls, corpses, carcasses or pieces carrying it; none left releases it early. */
    int holders;
    /** Whether {@link BurnResidueSystem} is still ageing it; once not, it shows no flame or smoke. */
    boolean tracked;

    BurnResidue(float scorch, float flame, float flameSeconds, float x, float y, float z) {
        this.scorch = scorch;
        this.flame = flame;
        this.flameSeconds = flameSeconds;
        anchor(x, y, z);
    }

    /** How scorched the remains look, 0..1. It never fades. */
    public float scorch() {
        return scorch;
    }

    /** How strongly the body was burning when it died; 0 when it died without flames. */
    public float flameAtDeath() {
        return flame;
    }

    /**
     * How strongly the remains burn now: the flame the body died with,
     * falling linearly to nothing over {@link #flameSeconds()}; 0 once the
     * flames are out, water or rain put them out, or the system let the
     * residue go.
     */
    public float flame() {
        if (!tracked || age >= flameSeconds) {
            return 0f;
        }
        return flame * (1f - age / flameSeconds);
    }

    /**
     * How much the remains smoke now, 0..1: rising as the flames fall, then
     * thinning over {@link CombustionConstants#RESIDUE_SMOKE_SECONDS} once they are out. Water or
     * rain putting the flames out starts that thinning at once, from the
     * flame's full strength.
     */
    public float smoke() {
        if (!tracked || flame <= 0f) {
            return 0f;
        }
        if (age < flameSeconds) {
            return flame * age / flameSeconds;
        }
        float after = age - flameSeconds;
        return after >= RESIDUE_SMOKE_SECONDS ? 0f : flame * (1f - after / RESIDUE_SMOKE_SECONDS);
    }

    /** Whether it still shows flame or smoke. */
    public boolean active() {
        return tracked && flame > 0f && age < flameSeconds + RESIDUE_SMOKE_SECONDS;
    }

    /** Seconds after death the flames end (or ended). */
    public float flameSeconds() {
        return flameSeconds;
    }

    /** Seconds since the body died, as far as the world has been simulated. */
    public float age() {
        return age;
    }

    /** Whether water or rain put the flames out before they burned down. */
    public boolean doused() {
        return doused;
    }

    /** Where the core of the remains lies now. */
    public float x() {
        return x;
    }

    public float y() {
        return y;
    }

    public float z() {
        return z;
    }

    /** Moves the residue with the core of its remains. */
    void anchor(float x, float y, float z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    /** Puts the flames out now; the smoke follows. */
    void douse() {
        if (age < flameSeconds) {
            flameSeconds = age;
            doused = true;
        }
    }

    /** One more ragdoll, corpse, carcass or piece carries {@code r}; nothing for null. */
    static void hold(BurnResidue r) {
        if (r != null) {
            r.holders++;
        }
    }

    /** One fewer carries {@code r}; the last to let go releases it. Nothing for null. */
    static void letGo(BurnResidue r) {
        if (r != null && r.holders > 0) {
            r.holders--;
        }
    }
}
