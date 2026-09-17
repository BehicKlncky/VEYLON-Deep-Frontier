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

    public NpcArchetype archetype;
    public boolean raider;
    public boolean trader;
    public boolean sick;
    public int campIndex;

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
