package com.veylon.settlement;

/**
 * The sides of an urban city (milestone 17): stable string ids of their own, with their own
 * display names, beside the legacy factions of {@link HumanFaction}, which keep their ids and
 * names in every world. Nothing is renamed: a camp of layout twelve still belongs to the Free
 * Settlers, and a saved legacy faction reads as it always has.
 *
 * <p>Urban sides carry no faction-wide standing. The saved faction map ({@code World.factionReputation})
 * accepts only legacy ids, so a stronghold's or a raider yard's view of the player is the site's
 * own trust, saved with the site.
 */
public final class UrbanFactions {
    /** The people who hold a civic precinct's guarded stronghold. */
    public static final String WARDENS = "urban.wardens";
    /** The raiders of an industrial depot row: hostile to strangers on sight. */
    public static final String WRECKERS = "urban.wreckers";

    private UrbanFactions() {
    }

    /** What the screen and the log call a side: urban sides by their own names, legacy ones as ever. */
    public static String displayName(String id) {
        return switch (id) {
            case WARDENS -> "Wardens";
            case WRECKERS -> "Wreckers";
            case UrbanSite.CIVILIANS -> "Civilians";
            default -> HumanFaction.displayName(id);
        };
    }

    /** True for a side that attacks strangers on sight whatever they have done. */
    public static boolean innatelyHostile(String id) {
        return WRECKERS.equals(id);
    }
}
