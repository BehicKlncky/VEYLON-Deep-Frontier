package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.RouteCatalogue;
import com.veylon.world.urban.TransitNetwork;
import com.veylon.world.urban.TransitStation;
import com.veylon.world.urban.UrbanPlaces.Place;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 14: the HUD names the route place underfoot, and a station or hidden cellar is
 * discovered only when the survivor observes it: by standing in it, or for a cellar by sliding
 * its loose panel aside. Nothing is found at the start or from outside, each discovery is
 * announced once, and earlier worlds name no route place at all.
 */
class UrbanDiscoveryTest {
    private static final long SEED = 20260731L;

    static Game game(int generator) {
        Game g = new Game();
        g.newWorld(SEED, true, generator, GameMode.SURVIVAL);
        g.entities.npcs.clear();
        return g;
    }

    /** Puts the survivor's feet on a cell and runs the production needs tick that follows the place. */
    static void standAt(Game g, Vec3i cell) {
        g.world.ensureChunks(cell.x(), cell.z(), 2, 2_000);
        g.player.pos.set(cell.x() + .5f, cell.y() + .001f, cell.z() + .5f);
        g.player.vel.zero();
        g.player.tickNeeds(g, 0.05f);
    }

    static long logged(Game g, String text) {
        return g.eventLog.recent(64).stream().filter(s -> s.contains(text)).count();
    }

    @Test
    void aStationIsFoundOnlyWhenTheSurvivorStandsInIt() {
        Game g = game(World.GEN_URBAN_UNDERGROUND);
        assertEquals(0, g.world.urban.discovery.size(), "nothing is known at the start");
        TransitStation station = g.world.urban.segment(true, 0, 0).station();
        standAt(g, station.plan().approaches().getFirst());
        assertSame(Place.NONE, g.player.place, "the sidewalk over the station is street");
        assertFalse(g.world.urban.discovery.discovered(station.id()), "seeing the stair is not being inside");

        Vec3i platform = station.plan().floors().getFirst().landing();
        standAt(g, platform);
        assertSame(Place.STATION, g.player.place);
        assertTrue(g.world.urban.discovery.discovered(station.id()));
        assertTrue(g.player.locationLine().endsWith(" / Transit station"), g.player.locationLine());
        assertEquals(1, logged(g, "An abandoned transit station."));
        standAt(g, platform.offset(station.frame().alongDx() * 2, 0, station.frame().alongDz() * 2));
        assertSame(Place.STATION, g.player.place);
        assertEquals(1, logged(g, "An abandoned transit station."), "announced once");
    }

    @Test
    void aHiddenCellarIsFoundBySlidingItsPanelAsideNotByWalkingPast() {
        Game g = game(World.GEN_URBAN_UNDERGROUND);
        RouteCatalogue routes = g.world.urban.routes(0, 0);
        var parcel = g.world.urban.regionPlan(0, 0).parcel(routes.hiddenCellar());
        BuildingPlan plan = g.world.urban.buildingAt(parcel.x0(), parcel.z0());
        BuildingPlan.Fixture panel = plan.fixtures().stream()
                .filter(f -> f.block() == BlockType.HIDDEN_PANEL).findFirst().orElseThrow();
        long cellar = com.veylon.world.urban.UrbanIds.parent(panel.id());
        Vec3i p = panel.position(), inside = panel.target();
        Vec3i hall = new Vec3i(2 * p.x() - inside.x(), p.y(), 2 * p.z() - inside.z());

        standAt(g, hall);
        assertSame(Place.BASEMENT, g.player.place, "the hall in front of the panel is the basement");
        assertFalse(g.world.urban.discovery.discovered(cellar), "walking past a panel finds nothing");
        assertSame(BlockType.DEBRIS, g.world.getBlock(hall.x(), hall.y(), hall.z()), "the drag marks are there to see");

        assertTrue(FixtureActions.interact(g, p.x(), p.y(), p.z()));
        assertSame(BlockType.HIDDEN_PANEL_OPEN, g.world.getBlock(p.x(), p.y(), p.z()));
        assertTrue(g.world.urban.discovery.discovered(cellar));
        assertEquals(1, logged(g, "A hidden cellar."));
        standAt(g, inside);
        assertSame(Place.HIDDEN_CELLAR, g.player.place);
        assertTrue(g.player.locationLine().endsWith(" / Hidden cellar"));
        assertEquals(1, logged(g, "A hidden cellar."), "found once, whichever way in");
    }

    @Test
    void tunnelsServicePassagesAndRoofsAreNamedButRecordNothing() {
        Game g = game(World.GEN_URBAN_UNDERGROUND);
        TransitNetwork.Segment s = g.world.urban.segment(true, 0, 0);
        int a = s.station().low() - 3;
        standAt(g, new Vec3i(-2, TransitNetwork.FEET_Y, a));
        assertSame(Place.TUNNEL, g.player.place, "the far half of the track");
        RouteCatalogue.Corridor c = g.world.urban.routes(0, 0).corridors().getFirst();
        standAt(g, new Vec3i(c.x0() + 1, RouteCatalogue.SERVICE_Y, c.z0()));
        assertSame(Place.SERVICE_PASSAGE, g.player.place);
        BuildingPlan roofed = g.world.urban.buildingAt(c.west().first().x(), c.west().first().z());
        Vec3i roof = roofed.floors().stream().filter(f -> f.index() == roofed.storeys()).findFirst().orElseThrow()
                .rooms().getFirst().anchor();
        standAt(g, roof);
        assertSame(Place.ROOF, g.player.place);
        assertTrue(g.player.locationLine().endsWith(" / Rooftop"));
        assertEquals(0, g.world.urban.discovery.size(), "only stations and cellars are discoveries");
    }

    @Test
    void earlierWorldsNameNoRoutePlace() {
        Game g = game(World.GEN_URBAN_DISTRICTS);
        var parcel = g.world.urban.regionPlan(0, 0).parcels().getFirst();
        BuildingPlan plan = g.world.urban.buildingAt(parcel.x0(), parcel.z0());
        for (BuildingPlan.Floor floor : plan.floors()) {
            standAt(g, floor.rooms().getFirst().anchor());
            assertSame(Place.NONE, g.player.place, "generator 12 floor " + floor.index());
            assertTrue(g.player.locationLine().startsWith("District / ") && !g.player.locationLine().contains(" / Base"));
        }
    }
}
