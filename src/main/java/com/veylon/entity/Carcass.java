package com.veylon.entity;

import org.joml.Vector3f;

/**
 * A dead animal lying in the world. Harvest with F: a knife yields full meat,
 * hide and bone; bare hands tear off a single piece of meat. Carcasses rot
 * over time and their scent draws predators.
 */
public class Carcass {

    public final Creature.CreatureType type;
    public final Vector3f pos = new Vector3f();
    public int meatLeft;
    public int hideLeft;
    /** Real seconds until the carcass rots away entirely. */
    public float decay;
    /** Arrows recoverable when skinning (transferred from the live animal). */
    public int stuckArrows;
    public com.veylon.item.ItemType stuckArrowType;

    public Carcass(Creature.CreatureType type, float x, float y, float z) {
        this.type = type;
        pos.set(x, y, z);
        meatLeft = type.meatYield;
        hideLeft = type.hideYield;
        decay = 420f;
    }

    public boolean empty() {
        return meatLeft <= 0 && hideLeft <= 0;
    }

    /** Rotten carcasses only give spoiled meat. */
    public boolean rotten() {
        return decay < 120f;
    }
}
