package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Layout eleven's living sites (urban milestone 15) on the design seeds: growth sites only in
 * the quarantine, on open ground that keeps every building and route clear, planned purely and
 * never known to an earlier encounter catalogue.
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
}
