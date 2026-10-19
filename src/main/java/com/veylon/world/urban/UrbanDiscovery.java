package com.veylon.world.urban;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * What the survivor has found of a layout-ten city's secret and underground places (milestone
 * 14): transit stations and hidden cellars, by stable id.
 *
 * <p>A site enters the ledger only when it is observed: a station when the player stands in
 * it, a cellar when its loose panel is opened or the player stands inside. Planning or
 * generating a site never adds it, so nothing (a later map layer included) can reveal a secret
 * before the player has found it. The ledger is persistent ({@code urban.discovery}), bounded
 * by {@value #MAX_SITES} sites, and belongs to one world: a new world or a load starts its own.
 */
public final class UrbanDiscovery {
    public static final int MAX_SITES = 4096;
    private final TreeSet<Long> sites = new TreeSet<>();

    /** True for an id this ledger may hold: a transit station or a hidden cellar room. */
    public static boolean isSite(long id) {
        if (!UrbanIds.isValid(id)) { return false; }
        return switch (UrbanIds.kind(id)) {
            case BUILDING -> UrbanIds.parcelIndex(id) % ParcelPlan.SLOTS_PER_SUPERBLOCK == TransitStation.ID_SLOT
                    && UrbanIds.buildingIndex(id) == 0;
            case ROOM -> UrbanIds.floorIndex(id) == -1 && UrbanIds.roomIndex(id) == RouteTemplates.CELLAR_ROOM;
            default -> false;
        };
    }

    public boolean discovered(long id) { return sites.contains(id); }
    public int size() { return sites.size(); }

    /** Every site found, ascending. */
    public List<Long> sites() { return new ArrayList<>(sites); }

    /**
     * Records an observed site.
     *
     * @return true when the site is new; false when it was already known or the ledger is full
     * @throws IllegalArgumentException for an id that names no kind of site
     */
    public boolean discover(long id) {
        if (!isSite(id)) { throw new IllegalArgumentException("not a discoverable site: " + id); }
        if (sites.contains(id) || sites.size() >= MAX_SITES) { return false; }
        return sites.add(id);
    }

    /**
     * Replaces the ledger with a loaded one.
     *
     * @throws IllegalArgumentException for an invalid, duplicate or excess id; nothing changes then
     */
    public void restore(Collection<Long> ids) {
        TreeSet<Long> next = new TreeSet<>();
        for (long id : ids) {
            if (!isSite(id) || !next.add(id) || next.size() > MAX_SITES) {
                throw new IllegalArgumentException("invalid discovered site " + id);
            }
        }
        sites.clear();
        sites.addAll(next);
    }
}
