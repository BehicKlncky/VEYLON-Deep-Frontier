package com.veylon.world.urban;

import com.veylon.world.World;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * What the survivor has walked and entered of a layout-sixteen city (urban milestone 21): the
 * districts they have stood in and the buildings in whose rooms they have stood, by stable id. The
 * map labels these and nothing else. Planning or generating a place, seeing its roof from the
 * street or hearing of it never adds it, so a building nobody has entered stays an unnamed block.
 *
 * <p>The ledger is persistent ({@code urban.visits}), bounded by {@value #MAX_DISTRICTS} districts
 * and {@value #MAX_BUILDINGS} buildings, and belongs to one world: a new world or a load starts its
 * own. A full ledger keeps what it has and records nothing more.
 *
 * <p>Beside it is a transient memory of the rooms entered on the last {@value #MAX_SKETCHED_FLOORS}
 * floors walked, which the map's floor sketch draws. It is never saved, so after a load the sketch
 * draws rooms again as they are entered; the floor's outline, stairs and ways out never depend on it.
 */
public final class UrbanVisits {
    public static final int MAX_DISTRICTS = 1024;
    public static final int MAX_BUILDINGS = 4096;
    /** Floors whose entered rooms the sketch remembers; the least recently walked is forgotten first. */
    public static final int MAX_SKETCHED_FLOORS = 64;

    private final TreeSet<Long> districts = new TreeSet<>();
    private final TreeSet<Long> buildings = new TreeSet<>();
    private final Map<Long, Integer> rooms = new LinkedHashMap<>(16, .75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Integer> eldest) {
            return size() > MAX_SKETCHED_FLOORS;
        }
    };

    /** True for an id the ledger may hold as a district: a region's district id. */
    public static boolean isDistrict(long id) {
        return UrbanIds.isValid(id) && UrbanIds.kind(id) == UrbanIds.Kind.DISTRICT;
    }

    /** True for an id the ledger may hold as a building: a parcel's building or a transit station. */
    public static boolean isBuilding(long id) {
        return UrbanIds.isValid(id) && UrbanIds.kind(id) == UrbanIds.Kind.BUILDING;
    }

    public boolean walked(long district) { return districts.contains(district); }

    public boolean entered(long building) { return buildings.contains(building); }

    public int districtCount() { return districts.size(); }

    public int buildingCount() { return buildings.size(); }

    /** Every district walked, ascending. */
    public List<Long> districts() { return new ArrayList<>(districts); }

    /** Every building entered, ascending. */
    public List<Long> buildings() { return new ArrayList<>(buildings); }

    /**
     * Records a district stood in.
     *
     * @return true when it is new; false when it was known or the ledger is full
     * @throws IllegalArgumentException for an id that is not a district
     */
    public boolean walk(long district) {
        if (!isDistrict(district)) { throw new IllegalArgumentException("not a district: " + district); }
        return !districts.contains(district) && districts.size() < MAX_DISTRICTS && districts.add(district);
    }

    /**
     * Records a building whose room the survivor stood in.
     *
     * @return true when it is new; false when it was known or the ledger is full
     * @throws IllegalArgumentException for an id that is not a building
     */
    public boolean enter(long building) {
        if (!isBuilding(building)) { throw new IllegalArgumentException("not a building: " + building); }
        return !buildings.contains(building) && buildings.size() < MAX_BUILDINGS && buildings.add(building);
    }

    /** Remembers, for the sketch only, a room entered on a floor. */
    public void enterRoom(long floor, int room) {
        if (room < 0 || room > UrbanIds.MAX_ROOM) { return; }
        rooms.merge(floor, 1 << room, (a, b) -> a | b);
    }

    /** Whether the sketch remembers a room of a floor as entered. */
    public boolean roomEntered(long floor, int room) {
        Integer mask = rooms.get(floor);
        return mask != null && room >= 0 && room <= UrbanIds.MAX_ROOM && (mask & (1 << room)) != 0;
    }

    /** Floors the sketch remembers rooms of; never above {@value #MAX_SKETCHED_FLOORS}. */
    public int sketchedFloors() { return rooms.size(); }

    /**
     * Replaces the ledger with a loaded one and forgets the sketch's rooms.
     *
     * @throws IllegalArgumentException for an invalid, duplicate or excess id; nothing changes then
     */
    public void restore(Collection<Long> walked, Collection<Long> entered) {
        TreeSet<Long> nextDistricts = new TreeSet<>(), nextBuildings = new TreeSet<>();
        for (long id : walked) {
            if (!isDistrict(id) || !nextDistricts.add(id) || nextDistricts.size() > MAX_DISTRICTS) {
                throw new IllegalArgumentException("invalid walked district " + id);
            }
        }
        for (long id : entered) {
            if (!isBuilding(id) || !nextBuildings.add(id) || nextBuildings.size() > MAX_BUILDINGS) {
                throw new IllegalArgumentException("invalid entered building " + id);
            }
        }
        districts.clear();
        districts.addAll(nextDistricts);
        buildings.clear();
        buildings.addAll(nextBuildings);
        rooms.clear();
    }

    /**
     * Records what a body standing on a feet cell has walked and entered: its district whenever its
     * column is loaded, a building and the room only while it stands inside one of the building's
     * rooms. Reads loaded columns and pure plans only, never creating a chunk; does nothing outside
     * a layout-sixteen city.
     */
    public static void observe(World world, int x, int y, int z) {
        if (world.urban == null || !world.urban.hasMissions() || !world.isColumnLoaded(x, z)) { return; }
        UrbanVisits visits = world.urban.visits;
        int rx = StreetNetwork.regionOf(x), rz = StreetNetwork.regionOf(z);
        if (CityPlanner.isPlannable(rx, rz)) { visits.walk(UrbanIds.district(rx, rz)); }
        UrbanIndoorQueries.Location here = UrbanIndoorQueries.location(world, x, y, z);
        if (here == null || here.room() == null) { return; }
        visits.enter(here.building().id());
        visits.enterRoom(here.floor().id(), UrbanIds.roomIndex(here.room().id()));
    }
}
