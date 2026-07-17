package com.veylon.settlement;

import java.util.List;

/**
 * Human factions, keyed by stable string id (never enum ordinal). The legacy
 * starter camp keeps its own {@link com.veylon.ai.FactionSystem} trust track;
 * regional reputation for these factions lives in {@link SettlementManager}.
 */
public final class HumanFaction {

    /** Friendly frontier settlers: starter camp, friendly villages and forts. */
    public static final String FRONTIER = "frontier";
    /** Unaligned homesteaders and free villages; can be won over or driven hostile. */
    public static final String FREE_SETTLERS = "settlers";
    /** Organized hostile faction: trackers, archers, brutes, powdermen, leaders. */
    public static final String HEADHUNTERS = "headhunters";
    /** Loose low-tier hostiles (the existing raid scavengers). */
    public static final String SCAVENGERS = "scavengers";

    public static final List<String> ALL =
            List.of(FRONTIER, FREE_SETTLERS, HEADHUNTERS, SCAVENGERS);

    private HumanFaction() {
    }

    public static String displayName(String id) {
        return switch (id) {
            case FRONTIER -> "Frontier Compact";
            case FREE_SETTLERS -> "Free Settlers";
            case HEADHUNTERS -> "Headhunters";
            case SCAVENGERS -> "Scavengers";
            default -> id;
        };
    }

    /** Factions that attack the player on sight regardless of reputation. */
    public static boolean innatelyHostile(String id) {
        return HEADHUNTERS.equals(id) || SCAVENGERS.equals(id);
    }
}
