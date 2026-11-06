package com.veylon.world.urban;

import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.World;

/**
 * What a survivor site's stash holds when it is first opened or eaten from (urban milestone 16):
 * the rations and water its people live on, in place of the container's ordinary loot roll. A
 * pure function of (seed, fixture id), like every roll, so the first touch by the player or by
 * the people themselves gives the same contents, and nothing ever refills it.
 *
 * <ul>
 *   <li>A camp's first stash container: five to seven rations, three or four clean waterskins
 *       and a bandage or two; any further one three to five rations, two or three waterskins
 *       and, half the time, dried meat.</li>
 *   <li>A hidden refuge's: two rations and two waterskins a person plus one each, split over its
 *       containers. They cannot go out for more.</li>
 * </ul>
 */
public final class SurvivorStash {
    private static final int LAYOUT = UrbanRevision.LAYOUT_SURVIVORS;

    private SurvivorStash() {
    }

    /** The planned contents of a site's stash container, or null when the container is no site's stash. */
    public static Inventory supplies(World world, BuildingPlan building, BuildingPlan.Fixture fixture) {
        if (world.urban == null || !world.urban.hasSurvivors()) { return null; }
        SurvivorSites.Site site = SurvivorSites.owner(world.urban, building.id());
        if (site == null) { return null; }
        int index = site.stash().indexOf(fixture.position());
        if (index < 0) { return null; }
        long seed = world.seed, id = fixture.id();
        Inventory out = new Inventory(UrbanContainers.SLOTS);
        if (site.kind().compound()) {
            return compound(site, index, seed, id, out);
        }
        if (site.kind() == SurvivorSites.Kind.CAMP) {
            boolean first = index == 0;
            out.add(ItemType.DRIED_BERRY, roll(seed, id, 0, first ? 5 : 3, first ? 7 : 5));
            out.add(ItemType.WATERSKIN_CLEAN, roll(seed, id, 1, first ? 3 : 2, first ? 4 : 3));
            if (first) {
                out.add(ItemType.BANDAGE, roll(seed, id, 2, 1, 2));
            } else if (roll(seed, id, 3, 0, 1) == 1) {
                out.add(ItemType.DRIED_MEAT, 1);
            }
            return out;
        }
        int people = site.residents().size(), containers = site.stash().size();
        int each = 2 * people + 1;
        out.add(ItemType.DRIED_BERRY, share(each, containers, index));
        out.add(ItemType.WATERSKIN_CLEAN, share(each, containers, index));
        return out.isEmpty() ? null : out;
    }

    /**
     * A compound's stash (layout thirteen). A stronghold's stores feed a garrison and are foraged
     * for: its first container holds seven to nine rations, four or five clean waterskins and two
     * bandages, each other one three to five rations, two or three waterskins and, half the time,
     * dried meat. A raider yard's are finite, two to four rations and one or two waterskins a
     * container, with scrap and a bandage in the first.
     */
    private static Inventory compound(SurvivorSites.Site site, int index, long seed, long id, Inventory out) {
        boolean first = index == 0;
        if (site.kind() == SurvivorSites.Kind.STRONGHOLD) {
            out.add(ItemType.DRIED_BERRY, roll(seed, id, 4, first ? 7 : 3, first ? 9 : 5));
            out.add(ItemType.WATERSKIN_CLEAN, roll(seed, id, 5, first ? 4 : 2, first ? 5 : 3));
            if (first) {
                out.add(ItemType.BANDAGE, 2);
            } else if (roll(seed, id, 6, 0, 1) == 1) {
                out.add(ItemType.DRIED_MEAT, 1);
            }
            return out;
        }
        out.add(ItemType.DRIED_BERRY, roll(seed, id, 7, 2, 4));
        out.add(ItemType.WATERSKIN_CLEAN, roll(seed, id, 8, 1, 2));
        if (first) {
            out.add(ItemType.SCRAP, roll(seed, id, 9, 2, 4));
            out.add(ItemType.BANDAGE, 1);
        }
        return out;
    }

    /** Whether a cell is one of a layout-twelve site's stash containers. */
    public static boolean isStash(World world, Vec3i position) {
        if (world.urban == null || !world.urban.hasSurvivors()) { return false; }
        BuildingPlan building = world.urban.buildingAt(position.x(), position.z());
        SurvivorSites.Site site = building == null ? null : SurvivorSites.owner(world.urban, building.id());
        return site != null && site.stash().contains(position);
    }

    private static int share(int total, int parts, int index) {
        return total / parts + (index < total % parts ? 1 : 0);
    }

    private static int roll(long seed, long id, int what, int lo, int hi) {
        return UrbanHash.range(lo, hi, seed, LAYOUT, UrbanSalts.SITE_STASH, id, what, 0);
    }
}
