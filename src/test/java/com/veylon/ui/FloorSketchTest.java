package com.veylon.ui;

import com.veylon.Game;
import com.veylon.SurvivorWorldArena;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The sketch of the storey underfoot (urban milestone 21): every stair, door and way out it draws is
 * a real fixture of the building, doors read their state from the world, rooms appear only once
 * entered, and no container or unfound hidden cellar is ever on it.
 */
class FloorSketchTest {

    private static Set<Vec3i> positions(BuildingPlan b, BuildingPlan.FixtureKind kind, int floor) {
        Set<Vec3i> out = new HashSet<>();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() == kind && UrbanIds.floorIndex(f.id()) == floor) { out.add(f.position()); }
        }
        return out;
    }

    @Test
    void theRefugeStoreyShowsRealStairsAndWaysOutAndOnlyTheRoomsEntered() {
        Game g = CityMapTest.city(GameMode.SURVIVAL);
        FloorSketch.Sketch s = FloorSketch.of(g);
        assertNotNull(s, "the survivor wakes inside the refuge");
        BuildingPlan b = s.building();
        assertEquals(g.world.urban.refugeBuilding, b.id());
        assertEquals("Flats · ground floor", s.title());
        assertEquals(0, s.floor().index());
        BuildingPlan.Stair up = b.stairs().stream().filter(st -> st.fromFloor() == 0).findFirst().orElseThrow();
        assertTrue(s.flights().stream().anyMatch(f -> f.up() && f.cells().equals(up.route())), "the plan's own flight up");
        assertTrue(s.flights().stream().noneMatch(f -> !f.up()) || b.basement(), "a flight down only above a basement");
        Set<Vec3i> doors = positions(b, BuildingPlan.FixtureKind.DOOR, 0);
        Set<Vec3i> containers = positions(b, BuildingPlan.FixtureKind.CONTAINER, 0);
        assertFalse(s.doors().isEmpty());
        for (FloorSketch.Door d : s.doors()) {
            assertTrue(doors.contains(d.position()), "a sketched door is a door of the plan: " + d);
            assertFalse(containers.contains(d.position()), "never a container: " + d);
        }
        assertTrue(s.doors().stream().anyMatch(FloorSketch.Door::exit), "the storey has a way out");
        assertTrue(positions(b, BuildingPlan.FixtureKind.ROUTE, 0).containsAll(s.escapes()));
        assertEquals(1, s.rooms().size(), "only the room woken in is sketched: " + s.rooms());

        BuildingPlan.Room other = s.floor().rooms().stream()
                .filter(r -> s.rooms().stream().noneMatch(seen -> seen.interior().equals(r.interior())))
                .findFirst().orElseThrow();
        SurvivorWorldArena.standAt(g, other.anchor());
        SurvivorWorldArena.run(g, .2f);
        FloorSketch.Sketch after = FloorSketch.of(g);
        assertEquals(2, after.rooms().size(), "the room walked into joins the sketch");
        assertTrue(after.rooms().stream().anyMatch(r -> r.name().equals(CityMap.roomName(other))));
    }

    @Test
    void aDoorOpenedInTheWorldIsOpenOnTheSketch() {
        Game g = CityMapTest.city(GameMode.SURVIVAL);
        FloorSketch.Door exit = FloorSketch.of(g).doors().stream().filter(FloorSketch.Door::exit).findFirst().orElseThrow();
        assertEquals(FloorSketch.DoorState.CLOSED, exit.state());
        SurvivorWorldArena.pressF(g, exit.position());
        FloorSketch.Door opened = FloorSketch.of(g).doors().stream().filter(d -> d.position().equals(exit.position()))
                .findFirst().orElseThrow();
        assertEquals(FloorSketch.DoorState.OPEN, opened.state(), "the sketch reads the door as the world holds it");
    }

    @Test
    void aHiddenCellarAndItsPanelAreSketchedOnlyOnceFound() {
        Game g = CityMapTest.city(GameMode.SURVIVAL);
        SurvivorSites.Site refuge = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.REFUGE);
        Vec3i panel = SurvivorWorldArena.panel(g, refuge);
        SurvivorWorldArena.standAt(g, SurvivorWorldArena.hall(g, refuge));
        SurvivorWorldArena.run(g, .2f);
        FloorSketch.Sketch basement = FloorSketch.of(g);
        assertNotNull(basement);
        assertEquals(-1, basement.floor().index());
        assertEquals(CityMap.familyName(basement.building().family()) + " · basement", basement.title());
        assertTrue(basement.doors().stream().noneMatch(d -> d.position().equals(panel)), "the loose panel is not a door yet");
        assertTrue(basement.rooms().stream().noneMatch(r -> r.name().equals("Hidden cellar")));

        SurvivorWorldArena.pressF(g, panel);
        assertTrue(FloorSketch.of(g).doors().stream().anyMatch(d -> d.position().equals(panel)), "found, it is a way in");
        SurvivorWorldArena.standAt(g, refuge.entry());
        SurvivorWorldArena.run(g, .2f);
        List<FloorSketch.Room> rooms = FloorSketch.of(g).rooms();
        assertTrue(rooms.stream().anyMatch(r -> r.name().equals("Hidden cellar")), "stood in, it is sketched: " + rooms);
    }

    @Test
    void outsideABuildingThereIsNoSketch() {
        Game g = CityMapTest.city(GameMode.SURVIVAL);
        int[] spawn = g.world.urban.spawnColumn();
        SurvivorWorldArena.standAt(g, new Vec3i(spawn[0], 41, spawn[1]));
        SurvivorWorldArena.run(g, .2f);
        assertNull(FloorSketch.of(g), "on the street the panel invites a look inside instead");
        assertFalse(CityMap.hereLine(g).contains("·"), CityMap.hereLine(g));
    }
}
