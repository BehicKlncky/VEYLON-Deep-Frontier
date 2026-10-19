package com.veylon.world.urban;

import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout revision 9 (milestone 13): six districts on the frozen street lattice, with their
 * overlays, as pure plans across the urban design seeds.
 */
class DistrictPlannerTest {
    private static final int LAYOUT = UrbanRevision.LAYOUT_DISTRICTS;
    private static final int SIZE = StreetNetwork.REGION_SIZE;

    @Test
    void everySeedPutsAllSixDistrictsWithinThreeRegionsOfTheSpawn() {
        for (long n = 0; n < 300; n++) {
            long seed = n < CityPlannerTest.SEEDS.length ? CityPlannerTest.SEEDS[(int) n] : n * 0x9E3779B97F4A7C15L;
            Set<UrbanDistrict> nearby = EnumSet.noneOf(UrbanDistrict.class);
            Set<UrbanDistrict> firstRing = EnumSet.noneOf(UrbanDistrict.class);
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    UrbanDistrict d = DistrictMap.districtOf(seed, rx, rz);
                    assertEquals(d, DistrictMap.districtOf(seed, rx, rz), "pure");
                    nearby.add(d);
                    int ring = DistrictMap.ring(rx, rz);
                    if (ring == 1) {
                        firstRing.add(d);
                    }
                    if (ring < DistrictMap.ALL_DISTRICTS_RING) {
                        assertNotEquals(UrbanDistrict.QUARANTINE, d, "seed " + seed + " " + rx + "," + rz);
                    }
                }
            }
            assertEquals(UrbanDistrict.RESIDENTIAL, DistrictMap.districtOf(seed, 0, 0), "the spawn region (D-09)");
            assertTrue(firstRing.contains(UrbanDistrict.COMMERCIAL), "commercial next to the spawn, seed " + seed);
            assertTrue(EnumSet.of(UrbanDistrict.RESIDENTIAL, UrbanDistrict.COMMERCIAL).containsAll(firstRing),
                    "the spawn's neighbours are residential or commercial (D-09), seed " + seed);
            assertEquals(EnumSet.allOf(UrbanDistrict.class), nearby, "all six within three regions, seed " + seed);
        }
    }

    @Test
    void farRegionsDrawEveryDistrictAtItsWeight() {
        Map<UrbanDistrict, Integer> counts = new EnumMap<>(UrbanDistrict.class);
        int samples = 0;
        for (long seed : CityPlannerTest.SEEDS) {
            for (int rz = 20; rz < 40; rz++) {
                for (int rx = -40; rx < -20; rx++) {
                    counts.merge(DistrictMap.districtOf(seed, rx, rz), 1, Integer::sum);
                    samples++;
                }
            }
        }
        UrbanDistrict[] districts = UrbanDistrict.values();
        for (int i = 0; i < districts.length; i++) {
            double share = 100.0 * counts.getOrDefault(districts[i], 0) / samples;
            assertEquals(DistrictMap.WEIGHTS[i], share, 3.0, districts[i] + " share " + counts);
        }
    }

    @Test
    void everyDistrictPlanPassesItsRulesSeamsAndOverlayRules() {
        Map<UrbanDistrict, Integer> inspected = new EnumMap<>(UrbanDistrict.class);
        for (long seed : CityPlannerTest.SEEDS) {
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    inspect(seed, rx, rz, inspected);
                }
            }
            for (int[] far : new int[][]{{4_096, -4_096}, {-4_097, 4_095}}) {
                inspect(seed, far[0], far[1], inspected);
            }
        }
        for (UrbanDistrict district : UrbanDistrict.values()) {
            assertTrue(inspected.getOrDefault(district, 0) >= 6, district + " inspected " + inspected);
        }
    }

    private static void inspect(long seed, int rx, int rz, Map<UrbanDistrict, Integer> inspected) {
        CityPlanDiagnostics.Report report = CityPlanDiagnostics.inspect(seed, LAYOUT, rx, rz);
        assertTrue(report.ok(), () -> report.summary() + "\n  " + String.join("\n  ", report.messages()));
        assertEquals(4, report.seamsChecked());
        assertEquals(LAYOUT, report.layoutRevision());
        assertTrue(report.parcels() > 0, report.summary());
        inspected.merge(report.district(), 1, Integer::sum);
    }

    @Test
    void residentialAndCommercialRegionsKeepTheLayoutTwoPlanBeneathTheirOverlays() {
        int compared = 0;
        for (long seed : CityPlannerTest.SEEDS) {
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    DistrictPlan nine = CityPlanner.planDistricts(seed, rx, rz);
                    if (nine.district != CityPlanner.districtOf(seed, UrbanRevision.LAYOUT_STREET_NETWORK, rx, rz)) {
                        continue;
                    }
                    if (nine.district != UrbanDistrict.RESIDENTIAL && nine.district != UrbanDistrict.COMMERCIAL) {
                        continue;
                    }
                    DistrictPlan two = CityPlanner.planStarter(seed, rx, rz);
                    assertEquals(two.parcels(), nine.parcels(), "parcels " + seed + " " + rx + "," + rz);
                    assertEquals(two.superblocks(), nine.superblocks());
                    assertEquals(two.trees(), nine.trees());
                    for (int z = nine.originZ(); z < nine.originZ() + SIZE; z++) {
                        for (int x = nine.originX(); x < nine.originX() + SIZE; x++) {
                            if (nine.surfaceAt(x, z) != two.surfaceAt(x, z)) {
                                throw new AssertionError("surface differs at " + x + "," + z);
                            }
                        }
                    }
                    compared++;
                }
            }
            // The slice neighbourhood: the starter superblock carries no overlay at all.
            DistrictPlan origin = CityPlanner.planDistricts(seed, 0, 0);
            ParcelPlan.Rect starter = origin.superblock(CityPlanner.STARTER_BLOCK).bounds();
            for (int z = starter.z0(); z <= starter.z1(); z++) {
                for (int x = starter.x0(); x <= starter.x1(); x++) {
                    assertEquals(UrbanOverlay.NONE, origin.overlayAt(x, z));
                }
            }
            assertEquals(CityPlanner.planStarter(seed, 0, 0).refuge(), origin.refuge());
        }
        assertTrue(compared > 30, "compared " + compared);
    }

    @Test
    void floodedStreetsHoldWaterUnderDuckboardedCrossingsAndCanalsHaveCauseways() {
        DistrictPlan flooded = first(UrbanDistrict.FLOODED);
        int water = 0, duckboards = 0, canal = 0;
        for (int lz = 0; lz < SIZE; lz++) {
            for (int lx = 0; lx < SIZE; lx++) {
                int x = flooded.originX() + lx, z = flooded.originZ() + lz;
                UrbanSurface s = flooded.surfaceAt(x, z);
                UrbanOverlay o = flooded.overlayAt(x, z);
                boolean roadway = s == UrbanSurface.ROADWAY || s == UrbanSurface.CROSSWALK || s == UrbanSurface.LANE_MARK;
                if (DistrictOverlays.inAvenueBand(lx, lz)) {
                    assertEquals(UrbanOverlay.NONE, o, "avenues stay dry: the raised routes between districts");
                } else if (roadway) {
                    assertTrue(o == UrbanOverlay.STANDING_WATER || o == UrbanOverlay.DUCKBOARD, "every local roadway floods");
                }
                if (s == UrbanSurface.SIDEWALK || s == UrbanSurface.COURTYARD_PATH || s == UrbanSurface.PASSAGE) {
                    assertEquals(UrbanOverlay.NONE, o, "walkways stay dry");
                }
                water += o == UrbanOverlay.STANDING_WATER ? 1 : 0;
                duckboards += o == UrbanOverlay.DUCKBOARD ? 1 : 0;
                canal += s == UrbanSurface.CANAL ? 1 : 0;
            }
        }
        assertTrue(water > 3_000 && duckboards > 100 && canal > 1_000, water + " " + duckboards + " " + canal);
        for (SuperblockPlan block : flooded.superblocks()) {
            if (block.kind() != SuperblockPlan.Kind.CANAL) {
                continue;
            }
            ParcelPlan.Rect b = block.bounds();
            int cx = (b.x0() + b.x1() + 1) / 2, cz = (b.z0() + b.z1() + 1) / 2;
            // Causeways reach all four streets of the basin.
            assertEquals(UrbanSurface.BRIDGE, flooded.surfaceAt(cx, b.z0()));
            assertEquals(UrbanSurface.BRIDGE, flooded.surfaceAt(cx, b.z1()));
            assertEquals(UrbanSurface.BRIDGE, flooded.surfaceAt(b.x0(), cz));
            assertEquals(UrbanSurface.BRIDGE, flooded.surfaceAt(b.x1(), cz));
            assertEquals(UrbanSurface.SIDEWALK, flooded.surfaceAt(cx, b.z0() - 1));
        }
    }

    @Test
    void quarantineCordonsFollowSharedEdgesAndOpenAtEveryLocalStreet() {
        int cordons = 0;
        for (long seed : CityPlannerTest.SEEDS) {
            for (int rz = -6; rz <= 6; rz++) {
                for (int rx = -6; rx <= 6; rx++) {
                    if (DistrictMap.districtOf(seed, rx, rz) != UrbanDistrict.QUARANTINE) {
                        continue;
                    }
                    DistrictPlan plan = CityPlanner.planDistricts(seed, rx, rz);
                    int[][] sides = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
                    for (int[] side : sides) {
                        boolean shared = DistrictMap.districtOf(seed, rx + side[0], rz + side[1]) == UrbanDistrict.QUARANTINE;
                        int fences = 0, checkpoints = 0;
                        for (int l = 1; l < SIZE - 1; l++) {
                            int lx = side[0] == 0 ? l : side[0] < 0 ? 0 : SIZE - 1;
                            int lz = side[1] == 0 ? l : side[1] < 0 ? 0 : SIZE - 1;
                            UrbanOverlay o = plan.overlayAt(plan.originX() + lx, plan.originZ() + lz);
                            fences += o == UrbanOverlay.FENCE ? 1 : 0;
                            checkpoints += o == UrbanOverlay.CHECKPOINT ? 1 : 0;
                        }
                        if (shared) {
                            assertEquals(0, fences + checkpoints, "a shared quarantine edge stays open");
                        } else {
                            // Three local streets cross each side, each 8 wide: three checkpoints.
                            assertEquals(3 * 2 * StreetNetwork.LOCAL_HALF_WIDTH, checkpoints);
                            assertEquals(SIZE - 2 - checkpoints, fences);
                            cordons++;
                        }
                    }
                }
            }
        }
        assertTrue(cordons > 20, "cordon sides checked: " + cordons);
    }

    @Test
    void industrialRegionsAreDepotRowsAroundFencedYards() {
        DistrictPlan industrial = first(UrbanDistrict.INDUSTRIAL);
        long service = industrial.parcels().stream().filter(p -> p.use() == ParcelPlan.Use.SERVICE).count();
        assertTrue(service >= 20, "depot lots: " + service);
        int kegs = 0, gates = 0;
        for (SuperblockPlan block : industrial.superblocks()) {
            if (block.kind() != SuperblockPlan.Kind.YARD) {
                continue;
            }
            ParcelPlan.Rect b = block.bounds();
            for (int z = b.z0(); z <= b.z1(); z++) {
                for (int x = b.x0(); x <= b.x1(); x++) {
                    UrbanOverlay o = industrial.overlayAt(x, z);
                    boolean ring = x == b.x0() || x == b.x1() || z == b.z0() || z == b.z1();
                    if (ring && o != UrbanOverlay.FENCE) {
                        gates++;
                    }
                    assertTrue(ring || o != UrbanOverlay.FENCE);
                    kegs += o == UrbanOverlay.KEG ? 1 : 0;
                }
            }
        }
        assertEquals(4 * DistrictOverlays.GATE_WIDTH * yards(industrial), gates, "four gateways per yard");
        assertTrue(kegs > 0 && kegs <= DistrictOverlays.MAX_KEGS_PER_YARD * yards(industrial), "kegs " + kegs);
        for (UrbanDistrict district : UrbanDistrict.values()) {
            DistrictPlan plan = first(district);
            assertTrue(plan.parcels().stream().anyMatch(p -> p.use() == ParcelPlan.Use.SERVICE),
                    district + " keeps a service reservation");
        }
    }

    @Test
    void districtPlansDoNotDependOnPlanningOrder() {
        for (UrbanDistrict district : UrbanDistrict.values()) {
            DistrictPlan a = first(district);
            CityPlanner.planDistricts(a.seed, a.rx + 1, a.rz);  // unrelated work in between
            DistrictPlan b = CityPlanner.planDistricts(a.seed, a.rx, a.rz);
            assertEquals(a.parcels(), b.parcels());
            for (int z = a.originZ(); z < a.originZ() + SIZE; z += 3) {
                for (int x = a.originX(); x < a.originX() + SIZE; x += 3) {
                    assertEquals(a.overlayAt(x, z), b.overlayAt(x, z));
                    assertEquals(a.surfaceAt(x, z), b.surfaceAt(x, z));
                }
            }
        }
    }

    @Test
    void diagnosticsCatchOverlaysThatWouldBlockOrBuryAnything() {
        DistrictPlan quarantine = first(UrbanDistrict.QUARANTINE);
        ParcelPlan parcel = quarantine.parcels().getFirst();
        ParcelPlan.Entrance door = parcel.primaryEntrance();
        // A fence across a street door's approach: inside the parcel clearance.
        CityPlanDiagnostics.Report blocked = CityPlanDiagnostics.inspect(
                withOverlay(quarantine, door.outsideX(), door.outsideZ(), UrbanOverlay.FENCE), null, null, null, null);
        assertFalse(blocked.ok());
        assertTrue(blocked.overlayViolations() > 0);
        assertTrue(blocked.messages().stream().anyMatch(m -> m.contains("clearance")), blocked.messages().toString());

        // Water in a quarantine region, or contamination on an avenue, breaks the surface rules.
        DistrictPlan flooded = first(UrbanDistrict.FLOODED);
        int ax = flooded.originX() + 2, az = flooded.originZ() + 100;
        CityPlanDiagnostics.Report onAvenue = CityPlanDiagnostics.inspect(
                withOverlay(flooded, ax, az, UrbanOverlay.CONTAMINATED), null, null, null, null);
        assertTrue(onAvenue.messages().stream().anyMatch(m -> m.startsWith("CONTAMINATED on")), onAvenue.messages().toString());

        // Closing every checkpoint of a cordon side is caught even though no cell is cut off.
        int[][] sideCells = {{0, -1}, {0, 1}, {-1, 0}, {1, 0}};
        int sealedSide = -1;
        DistrictPlan sealed = quarantine;
        for (int side = 0; side < 4 && sealedSide < 0; side++) {
            for (int l = 1; l < SIZE - 1; l++) {
                int lx = sideCells[side][0] == 0 ? l : sideCells[side][0] < 0 ? 0 : SIZE - 1;
                int lz = sideCells[side][1] == 0 ? l : sideCells[side][1] < 0 ? 0 : SIZE - 1;
                int x = quarantine.originX() + lx, z = quarantine.originZ() + lz;
                if (quarantine.overlayAt(x, z) == UrbanOverlay.CHECKPOINT) {
                    sealed = withOverlay(sealed, x, z, UrbanOverlay.FENCE);
                    sealedSide = side;
                }
            }
        }
        assertTrue(sealedSide >= 0, "the first quarantine region has a cordon side");
        String expected = "cordon side " + sealedSide + " has no checkpoint";
        CityPlanDiagnostics.Report closed = CityPlanDiagnostics.inspect(sealed, null, null, null, null);
        assertTrue(closed.messages().stream().anyMatch(expected::equals), closed.messages().toString());

        // A keg ring around a lawn cell cuts it off from the streets.
        DistrictPlan industrial = first(UrbanDistrict.INDUSTRIAL);
        SuperblockPlan yard = industrial.superblocks().stream()
                .filter(b -> b.kind() == SuperblockPlan.Kind.YARD).findFirst().orElseThrow();
        int cx = (yard.bounds().x0() + yard.bounds().x1()) / 2, cz = (yard.bounds().z0() + yard.bounds().z1()) / 2;
        DistrictPlan walled = industrial;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            walled = withOverlay(walled, cx + d[0], cz + d[1], UrbanOverlay.KEG);
        }
        walled = withOverlay(walled, cx, cz, UrbanOverlay.NONE);
        CityPlanDiagnostics.Report cutOff = CityPlanDiagnostics.inspect(walled, null, null, null, null);
        assertTrue(cutOff.routeViolations() > 0, cutOff.summary());
    }

    private static int yards(DistrictPlan plan) {
        return (int) plan.superblocks().stream().filter(b -> b.kind() == SuperblockPlan.Kind.YARD).count();
    }

    /** The first region of a district near the origin of the first design seed that has one. */
    static DistrictPlan first(UrbanDistrict district) {
        for (long seed : CityPlannerTest.SEEDS) {
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    if (DistrictMap.districtOf(seed, rx, rz) == district) {
                        return CityPlanner.planDistricts(seed, rx, rz);
                    }
                }
            }
        }
        throw new AssertionError("no " + district);
    }

    /** A copy of a plan with one overlay cell replaced. */
    private static DistrictPlan withOverlay(DistrictPlan plan, int x, int z, UrbanOverlay overlay) {
        byte[] surfaces = new byte[SIZE * SIZE];
        byte[] parcels = new byte[SIZE * SIZE];
        byte[] overlays = new byte[SIZE * SIZE];
        for (int lz = 0; lz < SIZE; lz++) {
            for (int lx = 0; lx < SIZE; lx++) {
                int wx = plan.originX() + lx, wz = plan.originZ() + lz, c = lz * SIZE + lx;
                surfaces[c] = plan.surfaceAt(wx, wz).code;
                parcels[c] = (byte) plan.parcelIndexAt(wx, wz);
                overlays[c] = plan.overlayAt(wx, wz).code;
            }
        }
        overlays[(z - plan.originZ()) * SIZE + (x - plan.originX())] = overlay.code;
        int[] northSouth = {plan.northSouthLine(1), plan.northSouthLine(2), plan.northSouthLine(3)};
        int[] eastWest = {plan.eastWestLine(1), plan.eastWestLine(2), plan.eastWestLine(3)};
        return new DistrictPlan(plan.seed, plan.layoutRevision, plan.rx, plan.rz, plan.district, northSouth, eastWest,
                plan.superblocks(), plan.parcels(), plan.trees(), surfaces, parcels, plan.refuge(), plan.spawnAnchor(),
                plan.overlapConflicts(), plan.fallbackSuperblocks(), overlays);
    }
}
