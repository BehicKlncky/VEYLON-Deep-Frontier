package com.veylon.combat;

import com.veylon.entity.Npc;

/**
 * What a bullet or an arrow does to a person ({@link Npc}), by the
 * {@link HitZone} it went in through. The player and creatures keep the plain
 * projectile damage model, and bombs and fire bombs deal no impact damage, so
 * none of them ever reach this table.
 *
 * <ul>
 *   <li><b>Head</b>: one hit kills, bullet or arrow, whatever the NPC's health.
 *   <li><b>Torso</b>: every hit adds wound units, a bullet
 *       {@value #BULLET_TORSO_WOUNDS} and an arrow {@value #ARROW_TORSO_WOUNDS},
 *       and at {@value #LETHAL_TORSO_WOUNDS} the NPC dies. That is two bullets
 *       or three arrows in the chest for every archetype, from a 24-health
 *       captive to a 90-health leader, and mixed hits add up. Healing between
 *       hits does not remove wounds. A torso hit short of the lethal count
 *       costs a fixed share of max health, or the projectile's own damage if
 *       that is more, and the arrow's share is the smaller one.
 *   <li><b>Legs</b>: the projectile's own damage, exactly as before.
 * </ul>
 *
 * <p>Wounds count once per trigger pull. Every pellet of one blunderbuss shot
 * carries the same {@link ProjectileSystem.Projectile#shotId}, so only the
 * first pellet into an NPC's torso adds wounds and the others deal their
 * ordinary pellet damage.
 *
 * <p>All damage goes through {@code Entity.hurt}, a kill included, so
 * {@code lastHitByPlayer}, reputation and the death pipeline in
 * {@code EntityManager.fastTick} see an ordinary hit.
 */
public final class ProjectileLethality {

    /** Torso wound units at which an NPC dies. */
    public static final int LETHAL_TORSO_WOUNDS = 6;
    /** Wound units a bullet in the torso adds: two bullets kill. */
    public static final int BULLET_TORSO_WOUNDS = 3;
    /** Wound units an arrow in the torso adds: three arrows kill. */
    public static final int ARROW_TORSO_WOUNDS = 2;
    /** Share of max health a non-lethal torso bullet costs at least. */
    public static final float BULLET_TORSO_HEALTH_FRACTION = 0.50f;
    /** Share of max health a non-lethal torso arrow costs at least. */
    public static final float ARROW_TORSO_HEALTH_FRACTION = 0.33f;

    private ProjectileLethality() {
    }

    /**
     * Torso wound units one hit of {@code kind} adds.
     *
     * @throws IllegalArgumentException for a kind with no row in the table.
     *         The switch has no default on purpose: a new {@code Kind} does not
     *         compile until it is given a row here or added to the rejected ones.
     */
    public static int torsoWounds(ProjectileSystem.Kind kind) {
        return switch (kind) {
            case BULLET -> BULLET_TORSO_WOUNDS;
            case ARROW -> ARROW_TORSO_WOUNDS;
            case BOMB, FIRE_BOMB -> throw noRow(kind);
        };
    }

    /**
     * Share of max health a non-lethal torso hit of {@code kind} costs at least.
     *
     * @throws IllegalArgumentException for a kind with no row in the table
     */
    public static float torsoHealthFraction(ProjectileSystem.Kind kind) {
        return switch (kind) {
            case BULLET -> BULLET_TORSO_HEALTH_FRACTION;
            case ARROW -> ARROW_TORSO_HEALTH_FRACTION;
            case BOMB, FIRE_BOMB -> throw noRow(kind);
        };
    }

    /** Health a torso hit that stays short of the lethal wound count takes. */
    public static float torsoHitDamage(ProjectileSystem.Kind kind, float projectileDamage,
                                       float maxHealth) {
        return Math.max(projectileDamage, torsoHealthFraction(kind) * maxHealth);
    }

    /** Applies one bullet or arrow hit that entered {@code n} through {@code zone}. */
    static void applyHit(Npc n, HitZone zone, ProjectileSystem.Projectile p) {
        switch (zone) {
            case HEAD -> n.killBy(p.fromPlayer);
            case TORSO -> applyTorsoHit(n, p);
            case LEGS -> n.hurt(p.damage, p.fromPlayer);
        }
    }

    private static void applyTorsoHit(Npc n, ProjectileSystem.Projectile p) {
        if (p.shotId == n.lastTorsoShotId) {
            // Another pellet of a shot that has already wounded this torso.
            n.hurt(p.damage, p.fromPlayer);
            return;
        }
        n.torsoWounds += torsoWounds(p.kind);
        n.lastTorsoShotId = p.shotId;
        if (n.torsoWounds >= LETHAL_TORSO_WOUNDS) {
            n.killBy(p.fromPlayer);
        } else {
            n.hurt(torsoHitDamage(p.kind, p.damage, n.maxHealth), p.fromPlayer);
        }
    }

    private static IllegalArgumentException noRow(ProjectileSystem.Kind kind) {
        return new IllegalArgumentException(
                "no lethality row for " + kind + ": it deals no impact damage");
    }
}
