package com.veylon.combat;

import com.veylon.entity.Npc;

/**
 * Where a bullet or arrow entered a human body, measured as height above the
 * feet ({@code npc.pos.y}) at the exact point the projectile's path crossed
 * into the NPC's hit box. {@link ProjectileLethality} decides what each zone
 * costs.
 *
 * <p>The thresholds are read off the shared humanoid in
 * {@code NpcModels.build()} and {@code BodySkeleton.buildHumanoid()}: legs
 * pivot at the hip, 0.86 above the feet; the torso box spans 0.86-1.48; the
 * neck pivots at 1.44 and the head box sits at about 1.51-1.77. The head line
 * sits at 1.47, between the neck pivot and the top of the torso box, and
 * everything above it up to the top of the hit box (1.85) is the head. NPCs
 * are drawn without any per-archetype scale, so one set of heights serves
 * every archetype.
 */
public enum HitZone {
    HEAD, TORSO, LEGS;

    /** Entry height above the feet at and above which a hit is a head hit. */
    public static final float HEAD_MIN_HEIGHT = 1.47f;
    /** Entry height above the feet at and above which a hit is a torso hit (the hip pivot). */
    public static final float TORSO_MIN_HEIGHT = 0.86f;

    /**
     * Classifies a hit from the world Y at which the projectile entered
     * {@code n}'s hit box. A sleeping NPC lies on its back, so whatever it is
     * hit in, the shot lands in its body: every hit counts as a torso hit.
     */
    public static HitZone classify(Npc n, float entryY) {
        if (n.state == Npc.NpcState.SLEEP) {
            return TORSO;
        }
        float height = entryY - n.pos.y;
        if (height >= HEAD_MIN_HEIGHT) {
            return HEAD;
        }
        return height >= TORSO_MIN_HEIGHT ? TORSO : LEGS;
    }
}
