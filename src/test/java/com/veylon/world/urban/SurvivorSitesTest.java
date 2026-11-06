package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout twelve's survivor sites (urban milestone 16), planned on the design seeds: camps only in
 * held walk-up apartments outside the starter superblock and the quarantine, hidden refuges only
 * in the region's sealed cellar, stable resident ids and names, and every cell a person stands
 * on a planned, dry, standable cell inside its own site, the hidden ones out of reach of the hall.
 */
class SurvivorSitesTest {
    static final long[] DESIGN_SEEDS = {20_260_731L, -4_242L, 7L, 0L, 987_654_321L, -9_000_000_000_000_000_000L};
    private static final int RANGE = 3;

    @Test
    void campsHoldTheGroundFloorOfAHeldWalkUpOutsideTheStarterAndTheQuarantine() {
        int camps = 0;
        for (long seed : DESIGN_SEEDS) {
            World w = new World(seed, World.GEN_URBAN_SURVIVORS);
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    DistrictPlan plan = w.urban.regionPlan(rx, rz);
                    SurvivorSites.Site camp = w.urban.sites(rx, rz).camp();
                    if (plan.district == UrbanDistrict.QUARANTINE) {
                        assertNull(camp, "no camp holds out in the quarantine");
                    }
                    if (camp == null) { continue; }
                    camps++;
                    assertEquals(SurvivorSites.Kind.CAMP, camp.kind());
                    ParcelPlan parcel = plan.parcel(UrbanIds.parcelIndex(camp.id()));
                    assertFalse(UrbanStarterSite.isNeighbour(parcel), "the starter superblock keeps its own people");
                    BuildingPlan b = w.urban.buildingAt(parcel.x0(), parcel.z0());
                    assertEquals(b.id(), camp.id(), "the site is named by the building it occupies");
                    assertEquals(BuildingPlan.Variant.OCCUPIED, b.variant(), "a camp is a held building");
                    assertEquals(BuildingPlan.Family.APARTMENT, b.family());
                    assertNotEquals(w.urban.routes(rx, rz).hiddenCellar(), parcel.index());
                    assertEquals(0, camp.floor());
                    assertTrue(camp.residents().size() >= SurvivorSites.MIN_CAMP
                            && camp.residents().size() <= SurvivorSites.MAX_CAMP, "3 to 6 people: " + camp.residents().size());
                    assertEquals(SurvivorSites.Role.LOOKOUT, camp.residents().get(0).role());
                    assertEquals(SurvivorSites.Role.TRADER, camp.residents().get(1).role());
                    assertNotNull(camp.post());
                    assertEquals(camp.post(), camp.residents().getFirst().day(), "the lookout keeps the post by day");
                    for (Vec3i guest : camp.guestBeds()) {
                        assertTrue(standable(b, guest), "a guest bed is a standable cell: " + guest);
                        assertTrue(camp.holds(guest.x(), guest.y(), guest.z()));
                    }
                    checkCells(b, camp);
                }
            }
        }
        assertTrue(camps >= DESIGN_SEEDS.length, "camps exist near the spawn: " + camps);
    }

    @Test
    void hiddenRefugesShelterCiviliansInTheSealedCellarOutOfReachOfTheHall() {
        int refuges = 0;
        for (long seed : DESIGN_SEEDS) {
            World w = new World(seed, World.GEN_URBAN_SURVIVORS);
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    SurvivorSites.Site refuge = w.urban.sites(rx, rz).refuge();
                    int cellar = w.urban.routes(rx, rz).hiddenCellar();
                    if (cellar < 0) {
                        assertNull(refuge, "no sealed cellar, no hidden refuge");
                        continue;
                    }
                    if (refuge == null) { continue; }
                    refuges++;
                    ParcelPlan parcel = w.urban.regionPlan(rx, rz).parcel(cellar);
                    BuildingPlan b = w.urban.buildingAt(parcel.x0(), parcel.z0());
                    assertEquals(b.id(), refuge.id());
                    assertEquals(SurvivorSites.Kind.REFUGE, refuge.kind());
                    assertEquals(-1, refuge.floor());
                    assertEquals(UrbanIds.Kind.ROOM, UrbanIds.kind(refuge.room()));
                    BuildingPlan.Room room = room(b, refuge.room());
                    assertTrue(room.functions().contains(BuildingPlan.Function.HIDDEN_REFUGE), "the sealed room");
                    assertTrue(refuge.residents().size() >= SurvivorSites.MIN_REFUGE
                            && refuge.residents().size() <= SurvivorSites.MAX_REFUGE, "2 to 5 people");
                    for (var r : refuge.residents()) {
                        assertEquals(SurvivorSites.Role.CIVILIAN, r.role(), "civilians, not a faction or captives");
                        assertNull(refuge.post());
                        for (int dz = -2; dz <= 2; dz++) {
                            for (int dx = -2; dx <= 2; dx++) {
                                Vec3i n = r.bed().offset(dx, 0, dz);
                                if (dx * dx + dz * dz > 4 || room.interior().contains(n.x(), n.z())) { continue; }
                                assertFalse(VoxelClearance.standable(b.voxels(), n.x(), n.y(), n.z()),
                                        "nobody rests within reach of a cell outside the room: " + r.bed() + " by " + n);
                            }
                        }
                    }
                    assertTrue(refuge.guestBeds().isEmpty());
                    checkCells(b, refuge);
                }
            }
        }
        assertTrue(refuges >= DESIGN_SEEDS.length, "refuges exist near the spawn: " + refuges);
    }

    @Test
    void everyDesignSeedHasACampAndARefugeWithinTwoRegionsOfTheSpawn() {
        for (long seed : DESIGN_SEEDS) {
            World w = new World(seed, World.GEN_URBAN_SURVIVORS);
            boolean camp = false, refuge = false;
            for (int rz = -2; rz <= 2; rz++) {
                for (int rx = -2; rx <= 2; rx++) {
                    camp |= w.urban.sites(rx, rz).camp() != null;
                    refuge |= w.urban.sites(rx, rz).refuge() != null;
                }
            }
            assertTrue(camp, "a camp within two regions of the spawn for seed " + seed);
            assertTrue(refuge, "a hidden refuge within two regions of the spawn for seed " + seed);
        }
    }

    @Test
    void sitePlansArePureStableAndResolvableByTheirBuildingId() {
        for (long seed : new long[]{20_260_731L, -4_242L}) {
            World a = new World(seed, World.GEN_URBAN_SURVIVORS), b = new World(seed, World.GEN_URBAN_SURVIVORS);
            List<SurvivorSites.Site> first = new ArrayList<>(), second = new ArrayList<>();
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    first.addAll(a.urban.sites(rx, rz).sites());
                }
            }
            // The second world plans them in the opposite order, past its cache, then again.
            for (int rz = RANGE; rz >= -RANGE; rz--) {
                for (int rx = RANGE; rx >= -RANGE; rx--) {
                    second.addAll(0, b.urban.sites(rx, rz).sites());
                }
            }
            assertTrue(b.urban.cachedSitePlans() <= UrbanWorldState.MAX_REGION_PLANS);
            first.sort(java.util.Comparator.comparingLong(SurvivorSites.Site::id));
            second.sort(java.util.Comparator.comparingLong(SurvivorSites.Site::id));
            assertEquals(first, second, "the same sites in any order and after eviction");
            Set<Long> ids = new HashSet<>();
            for (SurvivorSites.Site s : first) {
                assertEquals(s, SurvivorSites.resolve(a.urban, s.id()));
                Set<String> names = new HashSet<>();
                for (var r : s.residents()) {
                    assertTrue(ids.add(r.id()), "unique resident id");
                    assertEquals(UrbanIds.resident(s.id(), r.slot(), 0), r.id(), "a stable slot of its building");
                    assertTrue(names.add(r.name()), "names are unique within a site");
                }
            }
            assertNull(SurvivorSites.resolve(a.urban, UrbanIds.building(UrbanIds.parcel(UrbanIds.district(0, 0),
                    CityPlanner.STARTER_BLOCK * ParcelPlan.SLOTS_PER_SUPERBLOCK), 0)), "the starter refuge is no site");
        }
    }

    @Test
    void earlierGeneratorsHaveNoSurvivorSites() {
        World w = new World(7L, World.GEN_URBAN_ECOLOGY);
        assertFalse(w.urban.hasSurvivors());
        assertTrue(w.urban.hasEcology(), "layout eleven is unchanged");
        assertThrows(IllegalStateException.class, () -> w.urban.sites(0, 0));
        assertNull(SurvivorSites.resolve(w.urban, UrbanIds.building(UrbanIds.parcel(UrbanIds.district(0, 0), 0), 0)));
        World s = new World(7L, World.GEN_URBAN_SURVIVORS);
        assertTrue(s.urban.hasSurvivors() && s.urban.hasEcology() && s.urban.hasUnderground());
    }

    /** Beds, day cells, the safe cell, the entry and the stash: planned, standable, dry and inside the site. */
    private static void checkCells(BuildingPlan b, SurvivorSites.Site site) {
        Set<Vec3i> beds = new HashSet<>();
        Set<Vec3i> fixtureCells = new HashSet<>();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.block() != BlockType.CAMP_BED && f.block() != BlockType.BEDROLL) { fixtureCells.add(f.position()); }
        }
        for (var r : site.residents()) {
            assertTrue(beds.add(r.bed()), "every bed is somebody's own: " + r.bed());
            for (Vec3i c : List.of(r.bed(), r.day())) {
                assertTrue(standable(b, c), "a person stands on a standable planned cell: " + c);
                assertTrue(site.holds(c.x(), c.y(), c.z()), "inside the site: " + c);
                assertFalse(fixtureCells.contains(c), "never on a door, window or container: " + c);
            }
        }
        assertFalse(beds.contains(site.safe()), "the safe cell is nobody's bed");
        for (Vec3i c : List.of(site.safe(), site.entry())) {
            assertTrue(standable(b, c), "standable: " + c);
            assertTrue(site.holds(c.x(), c.y(), c.z()), "inside the site: " + c);
        }
        assertFalse(site.stash().isEmpty(), "a site eats from its own storage");
        assertEquals(site.stash().size(), site.meals().size());
        for (int i = 0; i < site.stash().size(); i++) {
            Vec3i s = site.stash().get(i);
            assertTrue(Fixtures.isContainer(b.voxels().getBlock(s.x(), s.y(), s.z())), "the stash is furniture: " + s);
            assertTrue(standable(b, site.meals().get(i)));
        }
        assertEquals(site.feetY(), site.anchor().y());
    }

    private static boolean standable(BuildingPlan b, Vec3i c) {
        BlockType feet = b.voxels().getBlock(c.x(), c.y(), c.z());
        return VoxelClearance.standable(b.voxels(), c.x(), c.y(), c.z()) && feet != BlockType.WATER
                && Fixtures.kindOf(feet) == null;
    }

    private static BuildingPlan.Room room(BuildingPlan b, long id) {
        for (var f : b.floors()) {
            for (var r : f.rooms()) {
                if (r.id() == id) { return r; }
            }
        }
        throw new AssertionError("no room " + UrbanIds.format(id));
    }
}
