package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanVisits;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a layout-sixteen survivor has walked and entered (urban milestone 21), recorded only from
 * where their body stands: the district under a loaded column, a building only from inside one of
 * its rooms. Planning, generating and passing in the street add nothing, and the ledger is bounded.
 */
class UrbanVisitsTest {
    private static Game session(GameMode mode, int generator) {
        return SurvivorWorldArena.session(SurvivorWorldArena.SEED, mode, generator, new AudioManager());
    }

    private static void tick(Game g) {
        SurvivorWorldArena.run(g, .2f);
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void aFreshCityKnowsOnlyTheRefugeItWakesInAndItsDistrict(GameMode mode) {
        Game g = session(mode, World.GEN_URBAN_MISSIONS);
        UrbanVisits visits = g.world.urban.visits;
        assertEquals(0, visits.buildingCount(), "generating the spawn's chunks enters nothing");
        tick(g);
        assertEquals(List.of(g.world.urban.refugeBuilding), visits.buildings(), "the survivor wakes inside the refuge");
        assertEquals(List.of(UrbanIds.district(0, 0)), visits.districts());
    }

    @Test
    void theStreetWalksTheDistrictButEntersNoBuildingAndARoomEntersItsOwn() {
        Game g = session(GameMode.SURVIVAL, World.GEN_URBAN_MISSIONS);
        tick(g);
        UrbanVisits visits = g.world.urban.visits;
        SurvivorSites.Site camp = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        BuildingPlan building = SurvivorWorldArena.building(g, camp);
        // Beside the building, outside its parcel: the district is walked, the building not entered.
        Vec3i outside = outsideOf(g, building);
        SurvivorWorldArena.standAt(g, outside);
        tick(g);
        assertFalse(visits.entered(building.id()), "standing at " + outside + " outside its rooms enters nothing");
        assertTrue(visits.walked(UrbanIds.district(StreetNetwork.regionOf(outside.x()), StreetNetwork.regionOf(outside.z()))));

        BuildingPlan.Floor ground = building.floors().stream().filter(f -> f.index() == 0).findFirst().orElseThrow();
        BuildingPlan.Room room = ground.rooms().getFirst();
        SurvivorWorldArena.standAt(g, room.anchor());
        tick(g);
        assertTrue(visits.entered(building.id()), "a room's anchor " + room.anchor() + " is inside it");
        assertTrue(visits.roomEntered(ground.id(), UrbanIds.roomIndex(room.id())));
        assertFalse(visits.roomEntered(ground.id(), UrbanIds.roomIndex(ground.rooms().getLast().id()))
                || ground.rooms().size() == 1, "only the room stood in is remembered for the sketch");
    }

    @Test
    void anUnloadedColumnAndAnEarlierCityRecordNothing() {
        Game g = session(GameMode.SURVIVAL, World.GEN_URBAN_MISSIONS);
        UrbanVisits.observe(g.world, 50_000, 41, 50_000);
        assertEquals(0, g.world.urban.visits.districtCount(), "nothing is read from an unloaded column");
        Game older = session(GameMode.SURVIVAL, World.GEN_URBAN_UTILITIES);
        tick(older);
        assertEquals(0, older.world.urban.visits.buildingCount() + older.world.urban.visits.districtCount(),
                "a generator-18 city keeps its own rules: no visits are recorded");
    }

    @Test
    void theLedgerIsBoundedAndRestoreRefusesWhatItCannotHold() {
        Game g = session(GameMode.SURVIVAL, World.GEN_URBAN_MISSIONS);
        UrbanVisits visits = g.world.urban.visits;
        List<Long> districts = new ArrayList<>(), buildings = new ArrayList<>();
        for (int i = 0; i < UrbanVisits.MAX_DISTRICTS; i++) { districts.add(UrbanIds.district(i, 7)); }
        for (int i = 0; i < UrbanVisits.MAX_BUILDINGS; i++) { buildings.add(building(i)); }
        visits.restore(districts, buildings);
        assertFalse(visits.walk(UrbanIds.district(-1, -1)), "a full ledger records no new district");
        assertFalse(visits.enter(building(UrbanVisits.MAX_BUILDINGS)), "nor a new building");
        for (int floor = 0; floor < 3 * UrbanVisits.MAX_SKETCHED_FLOORS; floor++) {
            visits.enterRoom(UrbanIds.floor(building(floor), 0), 3);
        }
        assertEquals(UrbanVisits.MAX_SKETCHED_FLOORS, visits.sketchedFloors(), "the sketch forgets the oldest floors");
        RuntimeBudgetSnapshot full = RuntimeBudgetSnapshot.capture(g);
        assertEquals(UrbanVisits.MAX_DISTRICTS + UrbanVisits.MAX_BUILDINGS, full.visitedPlaces());
        assertTrue(full.withinHardLimits(), full.occupancySummary());
        assertTrue(RuntimeBudgetSnapshot.hardLimitSummary().contains("sketchedFloors=" + UrbanVisits.MAX_SKETCHED_FLOORS));

        long room = UrbanIds.room(UrbanIds.floor(building(1), 0), 2);
        assertThrows(IllegalArgumentException.class, () -> visits.restore(List.of(room), List.of()));
        assertThrows(IllegalArgumentException.class, () -> visits.restore(List.of(), List.of(UrbanIds.district(0, 0))));
        assertThrows(IllegalArgumentException.class, () -> visits.restore(List.of(), List.of(building(1), building(1))));
        List<Long> excess = new ArrayList<>(buildings);
        excess.add(building(UrbanVisits.MAX_BUILDINGS));
        assertThrows(IllegalArgumentException.class, () -> visits.restore(List.of(), excess));
        assertEquals(UrbanVisits.MAX_BUILDINGS, visits.buildingCount(), "a refused restore changes nothing");
        assertThrows(IllegalArgumentException.class, () -> visits.enter(room));
    }

    /** A distinct building id for an index: parcel and region from the index, never a real lookup. */
    private static long building(int i) {
        return UrbanIds.building(UrbanIds.parcel(UrbanIds.district(i / 100, -9), i % 100), 0);
    }

    /** A loaded, standable cell in the building's parcel that lies in none of its ground-floor rooms. */
    private static Vec3i outsideOf(Game g, BuildingPlan building) {
        var parcel = building.parcel();
        for (int x = parcel.x0() - 2; x <= parcel.x1() + 2; x++) {
            for (int z = parcel.z0() - 2; z <= parcel.z1() + 2; z++) {
                if (parcel.contains(x, z)) { continue; }
                g.world.ensureChunks(x, z, 1, 10_000);
                if (com.veylon.world.VoxelClearance.standable(g.world, x, 41, z)) { return new Vec3i(x, 41, z); }
            }
        }
        throw new AssertionError("no street cell beside " + UrbanIds.format(building.id()));
    }
}
