package com.veylon.entity;

/**
 * The kinds of flame that can set a living body alight, and how each one does
 * it.
 *
 * <p>The declaration order is the dominance order when several flames touch
 * one body in the same fast tick: an earlier kind wins (see
 * {@link BodyCombustion}). The enum is transient — active combustion is never
 * saved — so the order may change with the rules; it is not a save format.
 *
 * <p>An immediate kind ({@link #ignitesOnContact()}) lights a body on its first
 * contact. The others build heat at {@link #heatGainPerSecond} times the
 * contact's intensity and light it when the heat reaches 1, so brushing past
 * a torch is safe and standing in a campfire is not.
 */
public enum CombustionSource {
    //          heat gain /s                 fuel s  nominal intensity
    DIRECT_HIT(Float.POSITIVE_INFINITY, 6f, 1.0f),
    LIQUID(Float.POSITIVE_INFINITY, 6f, 1.0f),
    BLOCK_FIRE(4f, 4f, 1.0f),
    CAMPFIRE(1f, 3f, 0.8f),
    TORCH(1f, 2f, 0.6f);

    /** Heat gained per second of contact at intensity 1; infinite for an immediate kind. */
    public final float heatGainPerSecond;
    /** Seconds of afterburn a contact grants: the body keeps burning this long after leaving it. */
    public final float fuelSeconds;
    /**
     * The intensity a source of this kind reports. A burning-liquid patch
     * reports its own intensity instead, which falls from 1 at the centre of
     * a pool to 0.5 at its rim.
     */
    public final float nominalIntensity;

    CombustionSource(float heatGainPerSecond, float fuelSeconds, float nominalIntensity) {
        this.heatGainPerSecond = heatGainPerSecond;
        this.fuelSeconds = fuelSeconds;
        this.nominalIntensity = nominalIntensity;
    }

    /** Whether the first contact alone sets a body alight. */
    public boolean ignitesOnContact() {
        return heatGainPerSecond == Float.POSITIVE_INFINITY;
    }
}
