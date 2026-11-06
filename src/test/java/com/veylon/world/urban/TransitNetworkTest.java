package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.TransitNetwork.Segment;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 14: the transit layer as a pure plan. Tunnels run only under candidate boundary
 * avenues, both bordering regions get the same segment, no region holds more than two
 * segments or one station, every present segment has a validated station, and stations stay
 * inside the avenue band clear of every parcel and entrance.
 */
class TransitNetworkTest {
    static final long[] SEEDS = {20260731L, -4242L, 7L, 0L, 987654321L, -9000000000000000000L};

    /** A memo over the pure planner, standing in for the world's bounded cache. */
    static TransitNetwork.RegionPlans plans(long seed) {
        Map<Long, DistrictPlan> memo = new HashMap<>();
        return (rx, rz) -> memo.computeIfAbsent(UrbanIds.district(rx, rz), k -> CityPlanner.planDistricts(seed, rx, rz));
    }

    @Test
    void everyRegionBordersOneCandidateOfEachAxisAndOwnsExactlyOneSegment() {
        for (int rx = -5; rx <= 5; rx++) {
            for (int rz = -5; rz <= 5; rz++) {
                int ns = TransitNetwork.candidate(rx) ? rx : rx + 1;
                int ew = TransitNetwork.candidate(rz) ? rz : rz + 1;
                assertTrue(TransitNetwork.candidate(ns) && TransitNetwork.candidate(ew));
                int owned = 0;
                if (isOwner(true, ns, rz, rx, rz)) { owned++; }
                if (isOwner(false, ew, rx, rx, rz)) { owned++; }
                assertEquals(1, owned, "region " + rx + "," + rz + " owns exactly one station segment");
            }
        }
    }

    private static boolean isOwner(boolean ns, int line, int index, int rx, int rz) {
        int[] owner = TransitNetwork.region(ns, line, index, TransitNetwork.ownerPositive(ns, index));
        return owner[0] == rx && owner[1] == rz;
    }

    @Test
    void bothBorderingRegionsPlanTheSameSegmentAndThePlanIsDeterministic() {
        for (long seed : new long[]{SEEDS[0], SEEDS[1]}) {
            for (int line = -2; line <= 2; line += 2) {
                for (int index = -1; index <= 1; index++) {
                    for (boolean ns : new boolean[]{true, false}) {
                        Segment a = TransitNetwork.plan(seed, ns, line, index, plans(seed));
                        Segment b = TransitNetwork.plan(seed, ns, line, index, plans(seed));
                        assertSame(a.present(), b.present());
                        assertEquals(a.flooded(), b.flooded());
                        assertEquals(a.plugLow(), b.plugLow());
                        assertEquals(a.plugHigh(), b.plugHigh());
                        assertArrayEquals(a.mounds(), b.mounds());
                        if (a.present()) {
                            assertEquals(a.station().id(), b.station().id());
                            assertEquals(a.station().start(), b.station().start());
                            assertEquals(a.station().plan().voxels(), b.station().plan().voxels());
                        }
                    }
                }
            }
        }
    }

    @Test
    void thePlainTunnelIsTheSameCrossSectionOnBothSidesOfItsLine() {
        long seed = SEEDS[0];
        Segment s = TransitNetwork.plan(seed, true, 0, 0, plans(seed));
        assertTrue(s.present(), "the spawn region's own segment always runs");
        for (int a = 0; a < 256; a++) {
            if (s.station().covers(a)) { continue; }
            for (int m = 0; m < TransitNetwork.BAND; m++) {
                for (int y = 30; y <= 42; y++) {
                    assertEquals(TransitNetwork.blockAt(s, true, m, a, y), TransitNetwork.blockAt(s, false, m, a, y),
                            "mirror at m=" + m + " a=" + a + " y=" + y);
                }
            }
        }
    }

    @Test
    void presentSegmentsHaveAValidatedStationInsideTheOwnersAvenueBand() {
        int present = 0, candidates = 0;
        for (long seed : SEEDS) {
            TransitNetwork.RegionPlans plans = plans(seed);
            for (int line = -2; line <= 2; line += 2) {
                for (int index = -2; index <= 2; index++) {
                    for (boolean ns : new boolean[]{true, false}) {
                        candidates++;
                        Segment s = TransitNetwork.plan(seed, ns, line, index, plans);
                        if (!s.present()) { continue; }
                        present++;
                        TransitStation station = s.station();
                        TransitValidator.Report report = TransitValidator.inspect(station);
                        assertTrue(report.ok(), () -> "station " + UrbanIds.format(station.id()) + " " + report.messages());
                        int[] o = TransitNetwork.region(ns, line, index, s.ownerPositive());
                        assertEquals(o[0], UrbanIds.regionX(station.id()), "owner region names the station");
                        assertEquals(o[1], UrbanIds.regionZ(station.id()));
                        assertEquals(TransitStation.ID_SLOT, UrbanIds.parcelIndex(station.id()) % ParcelPlan.SLOTS_PER_SUPERBLOCK);
                        DistrictPlan owner = plans.plan(o[0], o[1]);
                        assertNull(owner.parcel(UrbanIds.parcelIndex(station.id())), "no planned parcel uses slot 7");
                        ParcelPlan footprint = station.plan().parcel();
                        for (int x = footprint.x0(); x <= footprint.x1(); x++) {
                            for (int z = footprint.z0(); z <= footprint.z1(); z++) {
                                assertTrue(StreetNetwork.onBoundaryAvenue(x, z), "station stays under its avenue " + x + "," + z);
                                assertTrue(owner.contains(x, z), "station stays in its owner region");
                                assertNull(owner.parcelAt(x, z), "station touches no parcel");
                            }
                        }
                    }
                }
            }
        }
        double share = present / (double) candidates;
        System.out.println("transit candidates=" + candidates + " present=" + present);
        assertTrue(share > 0.3 && share < 0.8, "about half the candidate segments carry a tunnel: " + share);
    }

    @Test
    void theSpawnRegionOwnsAStationOnItsWestAvenueForEveryDesignSeed() {
        for (long seed : SEEDS) {
            Segment s = TransitNetwork.plan(seed, true, 0, 0, plans(seed));
            assertTrue(s.present(), "seed " + seed);
            assertTrue(s.ownerPositive(), "region (0,0) is the east side of line 0");
            assertEquals(0, UrbanIds.regionX(s.station().id()));
            assertEquals(0, UrbanIds.regionZ(s.station().id()));
        }
    }

    @Test
    void theStationStairIsGuardedAtTheStreetAndTheTrackStaysBelowThePlatform() {
        Segment s = TransitNetwork.plan(SEEDS[0], true, 0, 0, plans(SEEDS[0]));
        TransitStation st = s.station();
        int y = UrbanDatum.STREET_Y;
        for (int r = TransitStation.STAIR_LOW + 2; r <= TransitStation.STAIR_HIGH - 1; r++) {
            assertSame(BlockType.RAILING, st.blockAt(2, st.start() + r, y + 1));
            assertSame(BlockType.RAILING, st.blockAt(2, st.start() + r, y + 2), "two courses: nobody steps over");
            assertSame(BlockType.RAILING, st.blockAt(5, st.start() + r, y + 2));
        }
        for (int m = 3; m <= 4; m++) {
            assertSame(BlockType.RAILING, st.blockAt(m, st.start() + TransitStation.STAIR_LOW + 2, y + 2), "end rail");
            assertTrue(st.blockAt(m, st.start() + TransitStation.STAIR_HIGH, y).solid, "the top tread is flush with the sidewalk");
        }
        Vec3i platform = st.plan().floors().getFirst().landing();
        assertEquals(TransitNetwork.FEET_Y + 1, platform.y(), "the platform is one step above the track");
        assertEquals(BlockType.METAL_DOOR, st.blockAt(4, st.start() + TransitStation.PARTITION, TransitStation.PLATFORM_Y));
    }

    @Test
    void theStationProofCatchesAMissingRailAndABlockedStair() {
        TransitStation st = TransitNetwork.plan(SEEDS[0], true, 0, 0, plans(SEEDS[0])).station();
        BuildingPlan plan = st.plan();
        assertTrue(TransitValidator.inspect(st).ok());
        assertTrue(TransitValidator.inspect(st).reached() > 200, "the proof walks the whole station");
        int y = UrbanDatum.STREET_Y;
        TransitNetwork.Frame f = st.frame();
        // Open the wall and pavement between the top of the stair and the track: an eight-block drop.
        int a = st.start() + TransitStation.STAIR_HIGH;
        PlanVolume.Builder unguarded = plan.voxels().edit();
        for (int h = TransitNetwork.FEET_Y; h <= y; h++) { unguarded.set(f.x(2, a), h, f.z(2, a), BlockType.AIR); }
        TransitValidator.Report open = TransitValidator.inspect(plan, unguarded.build());
        assertFalse(open.ok());
        assertTrue(open.messages().stream().anyMatch(m -> m.startsWith("FALL")), open.messages().toString());
        PlanVolume.Builder blocked = plan.voxels().edit();
        int b = st.start() + TransitStation.STAIR_LOW + 2;
        for (int m = 3; m <= 4; m++) {
            for (int h = 0; h < 3; h++) { blocked.set(f.x(m, b), TransitStation.PLATFORM_Y + 3 + h, f.z(m, b), BlockType.CONCRETE); }
        }
        TransitValidator.Report shut = TransitValidator.inspect(plan, blocked.build());
        assertTrue(shut.messages().stream().anyMatch(m -> m.contains("unreachable")), shut.messages().toString());
    }

    @Test
    void crossingTunnelsJoinBecauseOpenSpaceWinsInTheJunction() {
        assertSame(BlockType.AIR, TransitNetwork.union(BlockType.CONCRETE, BlockType.AIR));
        assertSame(BlockType.AIR, TransitNetwork.union(BlockType.AIR, BlockType.CONCRETE));
        assertSame(BlockType.WATER, TransitNetwork.union(BlockType.WATER, BlockType.CONCRETE));
        assertSame(BlockType.CONCRETE, TransitNetwork.union(BlockType.CONCRETE, null));
        assertNull(TransitNetwork.union(null, null));
    }

    @Test
    void floodedTunnelsAreThoseBesideAFloodedDistrictAndHoldWaterOnlyOnTheTrack() {
        int flooded = 0;
        for (long seed : new long[]{SEEDS[0], SEEDS[2]}) {
            TransitNetwork.RegionPlans plans = plans(seed);
            for (int line = -4; line <= 4; line += 2) {
                for (int index = -4; index <= 4; index++) {
                    for (boolean ns : new boolean[]{true, false}) {
                        Segment s = TransitNetwork.plan(seed, ns, line, index, plans);
                        if (!s.present()) { continue; }
                        int[] p = TransitNetwork.region(ns, line, index, true), q = TransitNetwork.region(ns, line, index, false);
                        boolean beside = DistrictMap.districtOf(seed, p[0], p[1]) == UrbanDistrict.FLOODED
                                || DistrictMap.districtOf(seed, q[0], q[1]) == UrbanDistrict.FLOODED;
                        assertEquals(beside, s.flooded());
                        if (!s.flooded()) { continue; }
                        flooded++;
                        int a = s.station().low() - 3;
                        assertSame(BlockType.WATER, TransitNetwork.blockAt(s, true, 1, a, TransitNetwork.FEET_Y));
                        assertNotSame(BlockType.WATER, TransitNetwork.blockAt(s, true, 1, a, TransitNetwork.FEET_Y + 1));
                        assertTrue(TransitNetwork.blockAt(s, true, 1, a, TransitNetwork.SLAB_Y).solid, "water stands on a bed");
                        TransitStation st = s.station();
                        int pr = st.start() + TransitStation.PLATFORM_LOW;
                        assertNotSame(BlockType.WATER, st.blockAt(3, pr, TransitStation.PLATFORM_Y), "the platform stays dry");
                    }
                }
            }
        }
        assertTrue(flooded > 0, "the matrix reaches a flooded district");
    }
}
