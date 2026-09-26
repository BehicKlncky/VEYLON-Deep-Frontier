package com.veylon.entity;

import static com.veylon.entity.CombustionConstants.FADE_SECONDS;
import static com.veylon.entity.CombustionConstants.MAX_FUEL_SECONDS;
import static com.veylon.entity.CombustionConstants.MIN_INTENSITY;

/**
 * One living body's fire: whether it is alight, how much afterburn it has
 * left, how strongly it burns, what set it alight, and how close it is to
 * catching or to being put out by rain.
 *
 * <p>Every {@link Entity} embeds exactly one, so there is no registry to
 * purge or cap: the state lives and dies with its body and is bounded by the
 * entity lists. It holds one fire, never a list of burns, so overlapping
 * flames cannot stack. It is transient: never saved, and a new world or a
 * load builds new bodies.
 *
 * <p>Outside this package it is read-only. {@link CombustionSystem} is the only
 * writer (the player's own Creative reset aside); AI and presentation read the
 * getters and never own any of it.
 *
 * <p>Contacts are not applied when they are reported. Each one is offered as a
 * candidate, the strongest kept, and the fast tick applies the single survivor,
 * so the order sources report in never decides anything.
 */
public final class BodyCombustion {

    boolean burning;
    /** Seconds of afterburn left, in [0, {@link CombustionConstants#MAX_FUEL_SECONDS}]. */
    float fuel;
    /** Strongest flame intensity of this episode, in (0, 1]. */
    float peakIntensity;
    /** Ignition progress in [0, 1] while not burning; the body catches at 1. */
    float heat;
    /** Seconds of rain on the burning body's head, decaying under cover. */
    float soak;
    /** Seconds alight in the current, or else the last, episode. */
    float burnSeconds;
    /** How scorched the body is, 0..1; only ever grows. Presentation input. */
    float scorch;
    /** Whether a flame touched the body on the last fast tick. */
    boolean contact;

    /** The kind of the contact that last lit or refreshed the fire; null when not burning. */
    CombustionSource owner;
    boolean ownerByPlayer;
    int ownerSourceId;

    /** Whether the body has ever been touched by a flame; the point below is meaningful then. */
    boolean exposed;
    float exposureX, exposureY, exposureZ;

    /** The strongest contact offered since the last fast tick, or null. */
    CombustionSource pending;
    float pendingIntensity;
    boolean pendingByPlayer;
    int pendingSourceId;
    float pendingX, pendingY, pendingZ;

    /** Whether the body is alight. Kept as it was when the body died; see {@link CombustionSystem#isBurning}. */
    public boolean burning() {
        return burning;
    }

    /**
     * How strongly the body burns, 0 when it is not alight. Full strength
     * while at least {@link CombustionConstants#FADE_SECONDS} of fuel remain,
     * then falling linearly to {@link CombustionConstants#MIN_INTENSITY} (or
     * to the peak, if the fire never burned stronger than that).
     */
    public float intensity() {
        if (!burning) {
            return 0f;
        }
        float floor = Math.min(MIN_INTENSITY, peakIntensity);
        float left = Math.min(1f, Math.max(0f, fuel / FADE_SECONDS));
        return floor + (peakIntensity - floor) * left;
    }

    public float fuel() {
        return fuel;
    }

    public float peakIntensity() {
        return peakIntensity;
    }

    public float heat() {
        return heat;
    }

    public float soak() {
        return soak;
    }

    public float burnSeconds() {
        return burnSeconds;
    }

    public float scorch() {
        return scorch;
    }

    /** Whether a flame touched the body on the last fast tick (as opposed to afterburn). */
    public boolean inContact() {
        return contact;
    }

    /** The kind of flame that last lit or refreshed this fire; null when it is not burning. */
    public CombustionSource owner() {
        return owner;
    }

    /** Whether the player's flame owns this fire, and so earns the kill if it kills. */
    public boolean ownerByPlayer() {
        return ownerByPlayer;
    }

    public int ownerSourceId() {
        return ownerSourceId;
    }

    /** Whether a flame has ever touched this body, so {@link #exposureX()} and friends mean something. */
    public boolean hasExposure() {
        return exposed;
    }

    /** Where the last applied contact touched the body, for heat avoidance. */
    public float exposureX() {
        return exposureX;
    }

    public float exposureY() {
        return exposureY;
    }

    public float exposureZ() {
        return exposureZ;
    }

    /**
     * Keeps a contact as this tick's candidate if it beats the one already
     * held: an earlier {@link CombustionSource} first, then the higher
     * intensity, then the player's flame over an environmental one, then the
     * lower source id, and finally the lower point, so equal contacts
     * reported in any order leave the same winner.
     *
     * @return whether the contact is now the candidate
     */
    boolean offer(CombustionSource kind, float intensity, boolean byPlayer, int sourceId,
                  float x, float y, float z) {
        if (pending != null && !outranksPending(kind, intensity, byPlayer, sourceId, x, y, z)) {
            return false;
        }
        pending = kind;
        pendingIntensity = intensity;
        pendingByPlayer = byPlayer;
        pendingSourceId = sourceId;
        pendingX = x;
        pendingY = y;
        pendingZ = z;
        return true;
    }

    private boolean outranksPending(CombustionSource kind, float intensity, boolean byPlayer,
                                    int sourceId, float x, float y, float z) {
        if (kind != pending) {
            return kind.ordinal() < pending.ordinal();
        }
        if (intensity != pendingIntensity) {
            return intensity > pendingIntensity;
        }
        if (byPlayer != pendingByPlayer) {
            return byPlayer;
        }
        if (sourceId != pendingSourceId) {
            return sourceId < pendingSourceId;
        }
        if (x != pendingX) {
            return x < pendingX;
        }
        if (y != pendingY) {
            return y < pendingY;
        }
        return z < pendingZ;
    }

    /** Sets a body that is not burning alight: one new episode, owned by this contact. */
    void ignite(CombustionSource kind, float intensity, boolean byPlayer, int sourceId) {
        burning = true;
        fuel = Math.min(MAX_FUEL_SECONDS, kind.fuelSeconds);
        peakIntensity = intensity;
        heat = 0f;
        soak = 0f;
        burnSeconds = 0f;
        own(kind, byPlayer, sourceId);
    }

    /**
     * Feeds the fire already burning: fuel back up to this kind's grant (never
     * added on top, never past the cap), the peak raised, and the latest
     * dominant contact takes the fire over. Still one fire, one timer.
     */
    void refresh(CombustionSource kind, float intensity, boolean byPlayer, int sourceId) {
        fuel = Math.min(MAX_FUEL_SECONDS, Math.max(fuel, kind.fuelSeconds));
        peakIntensity = Math.max(peakIntensity, intensity);
        own(kind, byPlayer, sourceId);
    }

    private void own(CombustionSource kind, boolean byPlayer, int sourceId) {
        owner = kind;
        ownerByPlayer = byPlayer;
        ownerSourceId = sourceId;
    }

    void touchedAt(float x, float y, float z) {
        exposed = true;
        exposureX = x;
        exposureY = y;
        exposureZ = z;
    }

    /** Puts the flames out and forgets any heat; the scorch, the last exposure and the episode's length stay. */
    void extinguish() {
        burning = false;
        fuel = 0f;
        peakIntensity = 0f;
        heat = 0f;
        soak = 0f;
        contact = false;
        owner = null;
        ownerByPlayer = false;
        ownerSourceId = 0;
        pending = null;
    }

    /** Forgets everything, scorch included: for a body that starts a new life. */
    void clear() {
        extinguish();
        burnSeconds = 0f;
        scorch = 0f;
        exposed = false;
        exposureX = exposureY = exposureZ = 0f;
    }
}
