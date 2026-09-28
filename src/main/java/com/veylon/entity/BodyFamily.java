package com.veylon.entity;

import com.veylon.entity.Creature.CreatureType;

/**
 * Which kind of body a piece of remains came from: the humanoid shared by every
 * person (and the player), or one {@link CreatureType}.
 *
 * <p>Each family has its own {@link FragmentAnatomy} and its own piece ids, so a
 * piece is identified by (family, piece id), never by an index shared between
 * families. The ordinal is meant to reach a save alongside the piece id:
 * append only, and add a family for every new creature type.
 */
public enum BodyFamily {
    HUMANOID(null),
    DEER(CreatureType.DEER),
    WOLF(CreatureType.WOLF),
    BIRD(CreatureType.BIRD),
    HARE(CreatureType.HARE),
    THORNHORN(CreatureType.THORNHORN),
    STALKER(CreatureType.STALKER);

    /** The species this family draws, or null for the humanoid. */
    public final CreatureType creature;

    BodyFamily(CreatureType creature) {
        this.creature = creature;
    }

    public static BodyFamily of(CreatureType type) {
        return switch (type) {
            case DEER -> DEER;
            case WOLF -> WOLF;
            case BIRD -> BIRD;
            case HARE -> HARE;
            case THORNHORN -> THORNHORN;
            case STALKER -> STALKER;
        };
    }

    /** This family's fragment table; built once, shared, immutable. */
    public FragmentAnatomy anatomy() {
        return FragmentAnatomy.of(this);
    }
}
