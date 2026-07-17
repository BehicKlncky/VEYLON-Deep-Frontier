package com.veylon.settlement;

/**
 * Settlement tiers. Persisted by {@link #name()} string, never by ordinal, so
 * this enum stays freely reorderable.
 */
public enum SettlementType {
    //        display       radius  minPop maxPop
    CAMP("Camp", 9, 2, 4),
    VILLAGE("Village", 20, 6, 12),
    FORT("Fort", 17, 8, 14),
    CASTLE("Castle", 22, 12, 18),
    FORTRESS("Fortress", 30, 16, 24);

    public final String displayName;
    /** Reserved half-extent in blocks around the center (structures stay inside). */
    public final int radius;
    public final int minPopulation;
    public final int maxPopulation;

    SettlementType(String displayName, int radius, int minPopulation, int maxPopulation) {
        this.displayName = displayName;
        this.radius = radius;
        this.minPopulation = minPopulation;
        this.maxPopulation = maxPopulation;
    }

    public static SettlementType byName(String name) {
        for (SettlementType t : values()) {
            if (t.name().equals(name)) {
                return t;
            }
        }
        return CAMP;
    }
}
