package com.veylon.world.urban;

/**
 * What lives at an encounter anchor, by a stable id the {@code urban.population} ledger stores
 * (urban milestone 15). Catalogues one to four hold only the Veilbound; catalogue five adds the
 * Breacher, the Veilmaw and three wildlife species.
 *
 * <p>The ids are a save format: a new species appends an id and an existing one is never renamed.
 * The enum order is never saved. Which body plays a species is the entity layer's business: a
 * humanoid species is an infected archetype, a creature species a creature type.
 */
public enum UrbanSpecies {
    VEILBOUND("veilbound", Body.HUMANOID, true),
    BREACHER("breacher", Body.HUMANOID, true),
    VEILMAW("veilmaw", Body.CREATURE, true),
    SKITTERWING("skitterwing", Body.CREATURE, false),
    MURKHARE("murkhare", Body.CREATURE, false),
    GLOWDEER("glowdeer", Body.CREATURE, false);

    /** Which kind of live body an encounter of this species is. */
    public enum Body { HUMANOID, CREATURE }

    /** Stable save id. */
    public final String id;
    public final Body body;
    /** Hostile life: kept out of the refuge sanctuary and counted against a district's threat budget. */
    public final boolean hostile;

    UrbanSpecies(String id, Body body, boolean hostile) {
        this.id = id;
        this.body = body;
        this.hostile = hostile;
    }

    /** True for the city wildlife of milestone 15: optional hunting, never a threat budget. */
    public boolean wildlife() {
        return !hostile;
    }

    /** The species with this save id, or null for an id this build does not know. */
    public static UrbanSpecies byId(String id) {
        for (UrbanSpecies s : values()) {
            if (s.id.equals(id)) {
                return s;
            }
        }
        return null;
    }
}
