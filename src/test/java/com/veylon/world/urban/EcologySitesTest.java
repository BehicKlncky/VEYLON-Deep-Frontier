package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout eleven's living sites (urban milestone 15) on the design seeds: growth sites only in
 * the quarantine, on open ground that keeps every building and route clear, wildlife only
 * outside it, in parks, residential courtyards, canals and over roofs, and encounter catalogue
 * five's anchors: stable ids, Breachers only at street doors of the districts that hold them, and
 * a quarantine threat budget that counts its Veilmaws.
 */
class EcologySitesTest {
    private static final long[] SEEDS = {20_260_731L, -4_242L, 7L};
    private static final int RANGE = 3;

    @Test
    void growthSitesLieOnlyInTheQuarantineOnOpenGroundClearOfBuildingsAndRoutes() {
        int sites = 0;
        for (long seed : SEEDS) {
            World w = new World(seed, World.GEN_URBAN_ECOLOGY);
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    DistrictPlan plan = w.urban.regionPlan(rx, rz);
                    EcologySites eco = w.urban.ecology(rx, rz);
                    if (plan.district != UrbanDistrict.QUARANTINE) {
                        assertTrue(eco.growthSites().isEmpty(), "growth outside the quarantine at " + rx + "," + rz);
                        assertTrue(eco.slots().stream().noneMatch(s -> s.species() == UrbanSpecies.VEILMAW));
                        continue;
                    }
                    assertFalse(eco.growthSites().isEmpty(), "a quarantine region holds a growth site: " + rx + "," + rz);
                    assertTrue(eco.growthSites().size() <= EcologySites.GROWTH_SITES_PER_REGION);
                    for (EcologySites.GrowthSite site : eco.growthSites()) {
                        sites++;
                        Vec3i n = site.node();
                        assertEquals(UrbanDatum.STREET_Y + 1, n.y(), "the node stands on the ground slab");
                        assertTrue(site.membrane().contains(new Vec3i(n.x(), UrbanDatum.STREET_Y, n.z())),
                                "the node stands on its carpet");
                        assertEquals(1, Math.abs(site.rest().x() - n.x()) + Math.abs(site.rest().z() - n.z()),
                                "its Veilmaw folds beside the node");
                        assertTrue(site.membrane().contains(new Vec3i(site.rest().x(), UrbanDatum.STREET_Y, site.rest().z())));
                        for (Vec3i m : site.membrane()) {
                            UrbanSurface s = plan.surfaceAt(m.x(), m.z());
                            assertTrue(s == UrbanSurface.COURTYARD || s == UrbanSurface.PLAZA,
                                    "carpet only on lawn or square, never a walkway, path or tree bed: " + s + " at " + m);
                            for (int dz = -2; dz <= 2; dz++) {
                                for (int dx = -2; dx <= 2; dx++) {
                                    assertNull(plan.parcelAt(m.x() + dx, m.z() + dz), "two cells clear of every parcel (A9)");
                                }
                            }
                            assertFalse(plan.overlayAt(m.x(), m.z()).blocksWalking);
                        }
                        assertEquals(UrbanIds.Kind.ENCOUNTER, UrbanIds.kind(site.encounterId()));
                        assertTrue(EcologySites.isEcologyId(site.encounterId()));
                        assertTrue(site.territory().contains(site.rest().x() + 0.5f, site.rest().z() + 0.5f));
                    }
                }
            }
        }
        assertTrue(sites >= SEEDS.length, "every seed has growth near the spawn: " + sites);
    }

    @Test
    void wildlifeKeepsToParksCourtyardsCanalsAndRoofsOutsideTheQuarantineAndStarter() {
        int parks = 0, roofs = 0, courtyards = 0, canals = 0;
        for (long seed : SEEDS) {
            World w = new World(seed, World.GEN_URBAN_ECOLOGY);
            Set<Long> ids = new HashSet<>();
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    DistrictPlan plan = w.urban.regionPlan(rx, rz);
                    for (int sb = 0; sb < 16; sb++) {
                        SuperblockPlan block = plan.superblock(sb);
                        var anchors = EncounterAnchors.forEcology(w, rx, rz, sb);
                        if (rx == 0 && rz == 0 && sb == CityPlanner.STARTER_BLOCK) {
                            assertTrue(anchors.isEmpty(), "nothing lives in the starter superblock");
                        }
                        int hares = 0, deer = 0;
                        for (var a : anchors) {
                            assertTrue(ids.add(a.id()), "unique id " + UrbanIds.format(a.id()));
                            assertEquals(a, EncounterAnchors.resolve(w, a.id()), "resolves to itself");
                            assertNotNull(a.habitat());
                            assertTrue(a.habitat().contains(a.cell().x() + 0.5f, a.cell().z() + 0.5f),
                                    "it starts inside its own habitat");
                            if (a.species() == UrbanSpecies.VEILMAW) {
                                assertEquals(UrbanDistrict.QUARANTINE, plan.district);
                                continue;
                            }
                            assertTrue(a.species().wildlife());
                            assertNotEquals(UrbanDistrict.QUARANTINE, plan.district, "no wildlife in the quarantine");
                            switch (a.species()) {
                                case MURKHARE -> {
                                    hares++;
                                    UrbanSurface s = plan.surfaceAt(a.cell().x(), a.cell().z());
                                    assertTrue(s == UrbanSurface.PARK || s == UrbanSurface.COURTYARD, "hares on lawn: " + s);
                                    if (s == UrbanSurface.COURTYARD) {
                                        assertEquals(UrbanDistrict.RESIDENTIAL, plan.district);
                                        courtyards++;
                                    }
                                }
                                case GLOWDEER -> {
                                    deer++;
                                    assertEquals(SuperblockPlan.Kind.PARK, block.kind());
                                }
                                case SKITTERWING -> {
                                    assertTrue(a.cell().y() >= a.habitat().floorY() && a.cell().y() <= a.habitat().ceilingY());
                                    if (UrbanIds.roomIndex(a.id()) == EcologySites.ROOM_ROOF) {
                                        roofs++;
                                        assertEquals(SuperblockPlan.Kind.COURTYARD_HOUSING, block.kind());
                                    } else if (block.kind() == SuperblockPlan.Kind.CANAL) {
                                        canals++;
                                    }
                                }
                                default -> fail("unexpected species " + a.species());
                            }
                        }
                        assertTrue(deer <= 1 && hares <= EcologySites.PARK_HARES + 1, "bounded per block");
                        if (block.kind() == SuperblockPlan.Kind.PARK && plan.district != UrbanDistrict.QUARANTINE
                                && !anchors.isEmpty()) {
                            parks++;
                        }
                    }
                }
            }
        }
        assertTrue(parks > 0 && roofs > 0 && courtyards > 0 && canals > 0,
                "every habitat kind occurs: parks=" + parks + " roofs=" + roofs + " courtyards=" + courtyards
                        + " canals=" + canals);
    }

    @Test
    void theEcologyPlanIsPureAndEarlierCataloguesNeverKnowItsIds() {
        for (long seed : SEEDS) {
            World a = new World(seed, World.GEN_URBAN_ECOLOGY);
            World b = new World(seed, World.GEN_URBAN_ECOLOGY);
            World underground = new World(seed, World.GEN_URBAN_UNDERGROUND);
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    b.urban.ecology(rx, rz); // b plans in ascending order, evicting as it goes
                }
            }
            for (int rz = RANGE; rz >= -RANGE; rz--) {
                for (int rx = RANGE; rx >= -RANGE; rx--) {
                    // Different query orders and cache histories plan identical sites.
                    assertEquals(a.urban.ecology(rx, rz).growthSites(), b.urban.ecology(rx, rz).growthSites());
                    assertEquals(a.urban.ecology(rx, rz).slots(), b.urban.ecology(rx, rz).slots());
                    for (var s : a.urban.ecology(rx, rz).slots()) {
                        assertThrows(IllegalArgumentException.class, () -> EncounterAnchors.resolve(underground, s.encounterId()),
                                "catalogue four never resolves an ecology id");
                    }
                }
            }
            assertTrue(a.urban.cachedEcologyPlans() <= UrbanWorldState.MAX_REGION_PLANS);
        }
    }

    @Test
    void breachersHoldOnlyStreetDoorsAndTheQuarantineBudgetCountsItsVeilmaws() {
        int breachers = 0;
        for (long seed : SEEDS) {
            World w = new World(seed, World.GEN_URBAN_ECOLOGY);
            for (int rz = -RANGE; rz <= RANGE; rz++) {
                for (int rx = -RANGE; rx <= RANGE; rx++) {
                    DistrictPlan plan = w.urban.regionPlan(rx, rz);
                    int hostile = 0;
                    for (ParcelPlan p : plan.parcels()) {
                        for (var a : EncounterAnchors.forParcel(w, p)) {
                            hostile++;
                            assertTrue(a.species() == UrbanSpecies.VEILBOUND || a.species() == UrbanSpecies.BREACHER);
                            if (a.species() == UrbanSpecies.BREACHER) {
                                breachers++;
                                assertFalse(a.indoor(), "a Breacher keeps the street door");
                                assertTrue(plan.district == UrbanDistrict.INDUSTRIAL || plan.district == UrbanDistrict.CIVIC
                                        || plan.district == UrbanDistrict.QUARANTINE, "not in " + plan.district);
                                assertFalse(UrbanStarterSite.isNeighbour(p));
                            }
                            assertEquals(a, EncounterAnchors.resolve(w, a.id()));
                        }
                    }
                    hostile += EncounterAnchors.forStation(w, rx, rz).size();
                    hostile += w.urban.ecology(rx, rz).growthSites().size();
                    if (plan.district == UrbanDistrict.QUARANTINE) {
                        assertTrue(hostile <= DistrictRules.of(UrbanDistrict.QUARANTINE).threatMax(),
                                "the quarantine's Veilmaws spend its threat budget: " + hostile);
                    }
                }
            }
        }
        assertTrue(breachers > 0, "Breachers occur near the spawn");
    }
}
