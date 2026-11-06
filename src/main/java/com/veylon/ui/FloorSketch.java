package com.veylon.ui;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanIndoorQueries;

import java.util.ArrayList;
import java.util.List;

/**
 * The sketch of the storey the survivor stands on (urban milestone 21): a bounded, discovered-floor
 * map, not a plan of the city's interiors. It is drawn from the building's own plan and the live
 * world, and only for the storey underfoot:
 *
 * <ul>
 *   <li>the building's outline and the storey's stairs, up and down, and its ways out: outer doors
 *       and fire-escape openings, every one a real fixture of the building, with its door's state
 *       read from the loaded world (open, closed or broken);</li>
 *   <li>only the rooms the survivor has stood in, named by their use; a hidden cellar, and the loose
 *       panel before it, only once the cellar is in the discovery ledger;</li>
 *   <li>never a container, a piece of furniture or anything the survivor has not seen.</li>
 * </ul>
 */
public final class FloorSketch {
    /** A door's state as the world holds it now. */
    public enum DoorState { CLOSED, OPEN, BROKEN }

    /** A door of the storey: an outer one is a way out. */
    public record Door(Vec3i position, DoorState state, boolean exit) { }

    /** A flight of the storey's stairs: its cells, the cell its words go on and its direction. */
    public record Flight(List<Vec3i> cells, Vec3i label, boolean up) { }

    /** A room the survivor has stood in. */
    public record Room(ParcelPlan.Rect interior, String name) { }

    /**
     * One storey's sketch.
     *
     * @param building the building
     * @param floor    the storey underfoot
     * @param title    "Clinic · floor 2"
     * @param rooms    rooms entered, by use
     * @param flights  stairs up from it and down from it
     * @param doors    its doors, outer ones marked as ways out
     * @param escapes  fire-escape and roof openings on it
     * @param floors   every storey index of the building, lowest first
     * @param playerX  the survivor's position
     * @param playerZ  the survivor's position
     * @param yaw      the way the survivor faces, in degrees
     */
    public record Sketch(BuildingPlan building, BuildingPlan.Floor floor, String title, List<Room> rooms,
                         List<Flight> flights, List<Door> doors, List<Vec3i> escapes, List<Integer> floors,
                         float playerX, float playerZ, float yaw) { }

    private FloorSketch() { }

    /** The sketch of the storey underfoot in a layout-sixteen city, or null outside a building. */
    public static Sketch of(Game g) {
        World w = g.world;
        if (!CityMap.applies(w)) { return null; }
        int x = (int) Math.floor(g.player.pos.x), y = (int) Math.floor(g.player.pos.y + .01f);
        int z = (int) Math.floor(g.player.pos.z);
        UrbanIndoorQueries.Location here = UrbanIndoorQueries.location(w, x, y, z);
        if (here == null || here.building().family() == BuildingPlan.Family.TRANSIT) { return null; }
        BuildingPlan b = here.building();
        BuildingPlan.Floor floor = here.floor();
        int index = floor.index();

        List<Room> rooms = new ArrayList<>();
        for (BuildingPlan.Room r : floor.rooms()) {
            boolean hidden = r.functions().contains(BuildingPlan.Function.HIDDEN_REFUGE);
            if (hidden && !w.urban.discovery.discovered(r.id())) { continue; }
            if (w.urban.visits.roomEntered(floor.id(), UrbanIds.roomIndex(r.id()))) {
                rooms.add(new Room(r.interior(), CityMap.roomName(r)));
            }
        }

        List<Flight> flights = new ArrayList<>();
        for (BuildingPlan.Stair s : b.stairs()) {
            if (s.route().isEmpty()) { continue; }
            if (s.fromFloor() == index) {
                flights.add(new Flight(s.route(), s.route().getFirst(), true));
            } else if (s.fromFloor() == index - 1) {
                flights.add(new Flight(s.route(), s.route().getLast(), false));
            }
        }

        List<Door> doors = new ArrayList<>();
        List<Vec3i> escapes = new ArrayList<>();
        ParcelPlan parcel = b.parcel();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (UrbanIds.floorIndex(f.id()) != index) { continue; }
            if (f.kind() == BuildingPlan.FixtureKind.ROUTE) {
                escapes.add(f.position());
                continue;
            }
            if (f.kind() != BuildingPlan.FixtureKind.DOOR) { continue; }
            if (f.block() == BlockType.HIDDEN_PANEL && !w.urban.discovery.discovered(UrbanIds.parent(f.id()))) { continue; }
            Vec3i p = f.position();
            doors.add(new Door(p, state(w, p), parcel.onOutline(p.x(), p.z())));
        }

        List<Integer> floors = new ArrayList<>();
        for (BuildingPlan.Floor fl : b.floors()) { floors.add(fl.index()); }
        floors.sort(Integer::compare);
        String title = CityMap.familyName(b.family()) + " · " + CityMap.floorWords(index, b.storeys());
        return new Sketch(b, floor, title, rooms, flights, doors, escapes, floors,
                g.player.pos.x, g.player.pos.z, g.camera.yaw);
    }

    /** A door's state from the loaded world; an unloaded cell reads as closed. */
    static DoorState state(World w, Vec3i p) {
        if (!w.isColumnLoaded(p.x(), p.z())) { return DoorState.CLOSED; }
        BlockType t = w.getBlock(p.x(), p.y(), p.z());
        if (Fixtures.isClosed(t)) { return DoorState.CLOSED; }
        return Fixtures.isOpen(t) ? DoorState.OPEN : DoorState.BROKEN;
    }
}
