package com.veylon.entity;

import com.veylon.settlement.NpcArchetype;

/**
 * The five fields the humanoid model reads to pick a vest colour and decide
 * which accessory parts are visible.
 *
 * <p>Carried off a dying {@link Npc} so a ragdoll and the corpse it becomes
 * keep looking like the person who died, without either of them holding on to
 * an {@code Npc} the world has already removed.
 */
public final class NpcAppearance {

    /**
     * The camp index of the plain look: the gatherer's brown vest, which is
     * also the vest of every archetype without one of its own.
     */
    public static final int NEUTRAL_CAMP_INDEX = 2;

    public NpcArchetype archetype;
    public boolean raider;
    public boolean trader;
    public boolean sick;
    public int campIndex;

    /**
     * A person of no role or faction: no archetype, no raider, trader or
     * sick look, the plain vest. The look of a body that has no NPC to copy,
     * which is the player's remains; the player has no appearance of its own.
     */
    public NpcAppearance setNeutral() {
        archetype = null;
        raider = false;
        trader = false;
        sick = false;
        campIndex = NEUTRAL_CAMP_INDEX;
        return this;
    }

    public void capture(Npc n) {
        archetype = n.archetype;
        raider = n.raider;
        trader = n.isTrader;
        sick = n.sick;
        campIndex = n.campIndex;
    }

    public void copyFrom(NpcAppearance other) {
        archetype = other.archetype;
        raider = other.raider;
        trader = other.trader;
        sick = other.sick;
        campIndex = other.campIndex;
    }
}
