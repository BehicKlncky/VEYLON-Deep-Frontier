package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The refuge utilities of a layout-fifteen city (urban milestone 19), as the world keeps them.
 *
 * <h2>Taps</h2>
 *
 * <p>The mains died with the city. What is left in a building's pipes and roof tank is a little
 * stale water, untreated, shared by every tap of the building: the basins its plan puts in
 * kitchens, bathrooms and clinics, standing where the plan put them. A basin the player sets down
 * is a rain collector, as it always was. The building's utility id is its stable building id.
 * <ul>
 *   <li><b>How much.</b> {@value #LITRES_PER_BASIN} litres for each planned basin of an intact or
 *       a held building; one for each in a picked-clean building, where others drew most of it
 *       first; one for every two in a damaged building, whose burst pipes drained the rest. A
 *       building without a planned basin has no taps.</li>
 *   <li><b>No refill.</b> Nothing ever puts water back: no rain, no time, no load and no
 *       regeneration. Each litre drawn is recorded against the building, and a building whose taps
 *       were never opened has no row.</li>
 *   <li><b>Bounded.</b> At most {@value #MAX_TAPPED} buildings' rows; once the ledger is full a
 *       new building's taps refuse to run, so nothing drawn is ever forgotten.</li>
 * </ul>
 * Persistent through {@code urban.utilities} version 1; a new or loaded world gets a fresh ledger
 * with its world. Main thread only.
 */
public final class UrbanUtilities {
    /** Litres of stale water each planned basin of an intact building still holds. */
    public static final int LITRES_PER_BASIN = 2;
    /** Buildings whose taps have been drawn from, kept at once. */
    public static final int MAX_TAPPED = 1_024;

    /** Litres drawn from each building's taps, by building id. */
    private final Map<Long, Integer> drawn = new HashMap<>();
    private boolean sectionRestored;

    // ------------------------------------------------------------------
    // Taps
    // ------------------------------------------------------------------

    /**
     * The building whose taps a cell is one of, or null: a planned basin of a layout-fifteen
     * building, still standing where its plan put it with no recorded edit. Reads plans and the
     * loaded block only.
     */
    public static BuildingPlan tapBuilding(World w, int x, int y, int z) {
        if (w == null || w.urban == null || !w.urban.hasUtilities()
                || w.getBlock(x, y, z) != BlockType.RAIN_COLLECTOR) {
            return null;
        }
        Vec3i p = new Vec3i(x, y, z);
        if (w.changedBlocks.containsKey(p)) { return null; }
        BuildingPlan b = w.urban.buildingAt(x, z);
        if (b == null) { return null; }
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (isBasin(f) && f.position().equals(p)) { return b; }
        }
        return null;
    }

    private static boolean isBasin(BuildingPlan.Fixture f) {
        return f.kind() == BuildingPlan.FixtureKind.FURNITURE && f.block() == BlockType.RAIN_COLLECTOR;
    }

    /** The stale water a building's pipes held when the city fell, in litres. */
    public static int capacity(BuildingPlan b) {
        int basins = 0;
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (isBasin(f)) { basins++; }
        }
        return switch (b.variant()) {
            case INTACT, OCCUPIED -> basins * LITRES_PER_BASIN;
            case PICKED_CLEAN -> basins;
            case DAMAGED -> basins / 2;
        };
    }

    /** Litres already drawn from a building's taps. */
    public int drawn(long building) {
        return drawn.getOrDefault(building, 0);
    }

    /** Litres a building's taps can still give. */
    public int left(BuildingPlan b) {
        return Math.max(0, capacity(b) - drawn(b.id()));
    }

    /** True when the ledger can record one more litre from this building. */
    public boolean canRecord(BuildingPlan b) {
        return drawn.containsKey(b.id()) || drawn.size() < MAX_TAPPED;
    }

    /**
     * Draws one litre from a building's taps.
     *
     * @return false, changing nothing, when they are dry or the ledger cannot record it
     */
    public boolean draw(BuildingPlan b) {
        if (left(b) <= 0 || !canRecord(b)) { return false; }
        drawn.merge(b.id(), 1, Integer::sum);
        return true;
    }

    /** Every building's litres drawn, for the save. */
    public Map<Long, Integer> drawnRows() {
        return Collections.unmodifiableMap(drawn);
    }

    public int tappedCount() {
        return drawn.size();
    }

    // ------------------------------------------------------------------
    // Saving
    // ------------------------------------------------------------------

    public boolean sectionRestored() {
        return sectionRestored;
    }

    /** Load only: the validated rows of {@code urban.utilities}. */
    public void restore(Map<Long, Integer> rows) {
        if (rows.size() > MAX_TAPPED) { throw new IllegalArgumentException("too many tapped buildings"); }
        drawn.clear();
        drawn.putAll(rows);
        sectionRestored = true;
    }
}
