package com.veylon.world;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.AccessibilityValidator;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlanner;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.PlanVolume;
import com.veylon.world.urban.RouteCatalogue;
import com.veylon.world.urban.RouteProof;
import com.veylon.world.urban.TransitNetwork;
import com.veylon.world.urban.TransitStation;
import com.veylon.world.urban.TransitValidator;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanDistrict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generator 13 (milestone 14) as actually generated: transit under the boundary avenues, the
 * route catalogue's buildings, and the outside parts of their routes. Every route the package
 * promises is walked by the physical route proof in the generated world: street, basement,
 * service passage and back to the street; street, stairs, roof, connector and fire escape;
 * street, station and the tunnel beyond. Nothing touches a parcel's own cells, the tunnel
 * halves agree across the region boundary and chunk order changes nothing.
 */
class UrbanUndergroundGenerationTest {
    private static final int GEN = World.GEN_URBAN_UNDERGROUND;
    private static final long SEED = 20260731L;

    /** A world with region (0,0) and the bands of its west and north neighbours generated. */
    private static World spawnRegion() {
        World world = new World(SEED, GEN);
        for (int cz = -1; cz < 16; cz++) {
            for (int cx = -1; cx < 16; cx++) { world.getOrCreateChunk(cx, cz); }
        }
        return world;
    }

    /** Closed unlocked doors open, as a body walking the route opens them; panels stay shut. */
    static VoxelAccess opened(World w) {
        return new VoxelAccess() {
            @Override public BlockType getBlock(int x, int y, int z) {
                BlockType b = w.getBlock(x, y, z);
                return Fixtures.isClosedDoor(b) && w.fixtureLockAt(x, y, z) == FixtureStates.LOCK_NONE
                        ? Fixtures.kindOf(b).open : b;
            }
            @Override public boolean isColumnLoaded(int x, int z) { return w.isColumnLoaded(x, z); }
            @Override public int fixtureLockAt(int x, int y, int z) { return w.fixtureLockAt(x, y, z); }
        };
    }

    @Test
    void transitBuildingsAndRoutesAreStampedFromTheirPlansWithoutTouchingAParcel() {
        World world = spawnRegion();
        DistrictPlan plan = world.urban.regionPlan(0, 0);
        int transit = 0;
        for (int z = 0; z < 256; z++) {
            for (int x = 0; x < 256; x++) {
                for (int y = TransitNetwork.SLAB_Y; y <= UrbanDatum.STREET_Y + 3; y++) {
                    BlockType expected = TransitNetwork.blockAt(world.urban::segment, x, y, z);
                    if (expected == null) { continue; }
                    assertSame(expected, world.getBlock(x, y, z), "transit cell " + x + "," + y + "," + z);
                    transit++;
                }
            }
        }
        for (ParcelPlan parcel : plan.parcels()) {
            BuildingPlan building = world.urban.buildingAt(parcel.x0(), parcel.z0());
            PlanVolume v = building.voxels();
            for (int y = v.minY; y <= v.maxY; y++) {
                for (int z = parcel.z0(); z <= parcel.z1(); z++) {
                    for (int x = parcel.x0(); x <= parcel.x1(); x++) {
                        assertEquals(v.getBlock(x, y, z), world.getBlock(x, y, z), "owned slice differs from its plan");
                    }
                }
            }
            AccessibilityValidator.Report report = AccessibilityValidator.inspectGenerated(building, plan, world);
            assertTrue(report.ok(), () -> BuildingPlanner.identity(building) + " " + report.summary());
        }
        RouteCatalogue routes = world.urban.routes(0, 0);
        for (RouteCatalogue.Corridor c : routes.corridors()) {
            for (int x = c.x0(); x <= c.x1(); x++) {
                for (int z = c.z0(); z <= c.z1(); z++) {
                    for (int y = RouteCatalogue.SERVICE_Y; y < UrbanDatum.STREET_Y; y++) {
                        assertFalse(world.getBlock(x, y, z).solid, "service passage cell " + x + "," + y + "," + z);
                    }
                    assertTrue(world.getBlock(x, UrbanDatum.STREET_Y, z).solid, "the passage paving roofs it");
                }
            }
        }
        for (RouteCatalogue.FireEscape f : routes.fireEscapes()) {
            for (int y = UrbanDatum.STREET_Y + 1; y <= f.ladderTop().y(); y++) {
                assertSame(BlockType.LADDER, world.getBlock(f.ladderTop().x(), y, f.ladderTop().z()));
            }
        }
        for (RouteCatalogue.Connector c : routes.connectors()) {
            if (c.bridge()) { assertSame(BlockType.PLANK, world.getBlock(c.x0() + 1, c.deckY(), c.z0())); }
        }
        TransitStation station = world.urban.segment(true, 0, 0).station();
        TransitValidator.Report report = TransitValidator.inspect(station.plan(), world);
        assertTrue(report.ok(), report.messages().toString());
        assertSame(station, world.urban.stationAt(station.plan().approaches().getFirst().x(),
                station.plan().approaches().getFirst().z()));
        assertEquals(0, world.pendingGenerationEditCount());
        assertTrue(world.changedBlocks.isEmpty());
        System.out.println("underground-generation transitCells=" + transit + " corridors=" + routes.corridors().size()
                + " escapes=" + routes.fireEscapes().size() + " connectors=" + routes.connectors().size());
    }

    @Test
    void theStreetBasementServicePassageRouteAndTheRoofRoutesHoldInTheGeneratedWorld() {
        World world = spawnRegion();
        VoxelAccess access = opened(world);
        RouteCatalogue routes = world.urban.routes(0, 0);
        int passages = 0;
        for (RouteCatalogue.Corridor c : routes.corridors()) {
            BuildingPlan west = world.urban.buildingAt(c.west().first().x(), c.west().first().z());
            BuildingPlan east = world.urban.buildingAt(c.east().first().x(), c.east().first().z());
            if (west.variant() == BuildingPlan.Variant.OCCUPIED || east.variant() == BuildingPlan.Variant.OCCUPIED) {
                continue;
            }
            Vec3i passage = new Vec3i(c.x0() + 1, RouteCatalogue.SERVICE_Y, c.z0());
            // Each leg alone: a box holding one building and the corridor, but not the other building.
            for (BuildingPlan building : List.of(west, east)) {
                ParcelPlan p = building.parcel();
                RouteProof.Box box = new RouteProof.Box(Math.min(p.x0() - 1, c.x0()), UrbanDatum.slabY(-1),
                        p.z0() - 1, Math.max(p.x1() + 1, c.x1()), UrbanDatum.STREET_Y + 3, p.z1() + 1);
                RouteProof.Result proof = RouteProof.prove(access, box, List.of(building.approaches().getFirst()));
                assertTrue(proof.roundTrip(passage), "street, " + p.index() + "'s basement and the service passage");
            }
            passages++;
        }
        assertTrue(passages > 0, "an unheld pair shares a service passage");

        int roofs = 0;
        for (RouteCatalogue.Connector c : routes.connectors()) {
            BuildingPlan a = world.urban.buildingAt(c.a().first().x(), c.a().first().z());
            BuildingPlan b = world.urban.buildingAt(c.b().first().x(), c.b().first().z());
            ParcelPlan pa = a.parcel(), pb = b.parcel();
            int roof = a.roofY();
            RouteProof.Box box = new RouteProof.Box(Math.min(pa.x0(), pb.x0()) - 1, roof, Math.min(pa.z0(), pb.z0()) - 1,
                    Math.max(pa.x1(), pb.x1()) + 1, roof + 4, Math.max(pa.z1(), pb.z1()) + 1);
            RouteProof.Result proof = RouteProof.prove(access, box, List.of(roofAnchor(a)));
            assertTrue(proof.roundTrip(roofAnchor(b)), "roof to roof over " + (c.bridge() ? "a bridge" : "a shared parapet"));
            assertTrue(proof.fallTraps().isEmpty(), "no unguarded edge on the roof route: " + proof.fallTraps());
            roofs++;
        }
        assertTrue(roofs > 0);

        int escapes = 0;
        for (RouteCatalogue.FireEscape f : routes.fireEscapes()) {
            BuildingPlan building = world.urban.buildingAt(f.gap().x(), f.gap().z());
            ParcelPlan p = building.parcel();
            // Two rings round the parcel, so the cells beyond the ladder's head are judged too.
            RouteProof.Box box = new RouteProof.Box(p.x0() - 2, UrbanDatum.STREET_Y, p.z0() - 2,
                    p.x1() + 2, building.roofY() + 4, p.z1() + 2);
            Vec3i foot = new Vec3i(f.ladderTop().x(), UrbanDatum.STREET_Y + 1, f.ladderTop().z());
            RouteProof.Result up = RouteProof.prove(access, box, List.of(foot));
            assertTrue(up.reached(roofAnchor(building)), "up the ladder to the roof at " + f.gap());
            assertTrue(up.reached(f.ladderTop()) && up.escapes(f.ladderTop()) || up.reached(f.gap()),
                    "the ladder head joins the gap");
            RouteProof.Result down = RouteProof.prove(access, box, List.of(roofAnchor(building)));
            assertTrue(building.approaches().stream().anyMatch(down::reached), "and down the stairs to the street");
            assertTrue(up.fallTraps().isEmpty(), "no unguarded edge on a fire-escape roof: " + up.fallTraps());
            assertTrue(down.fallTraps().isEmpty(), "none from the roof either: " + down.fallTraps());
            escapes++;
        }
        assertTrue(escapes > 0);
    }

    private static Vec3i roofAnchor(BuildingPlan plan) {
        for (BuildingPlan.Floor floor : plan.floors()) {
            if (floor.index() == plan.storeys()) { return floor.rooms().getFirst().anchor(); }
        }
        throw new AssertionError("no roof");
    }

    @Test
    void theTunnelRunsFromItsStationAlongTheWholeSegmentAndMirrorsAcrossTheBoundary() {
        World world = spawnRegion();
        TransitNetwork.Segment s = world.urban.segment(true, 0, 0);
        TransitStation st = s.station();
        RouteProof.Box box = new RouteProof.Box(-TransitNetwork.BAND, TransitNetwork.SLAB_Y, 0,
                TransitNetwork.BAND - 1, UrbanDatum.STREET_Y + 2, 255);
        Vec3i platform = st.plan().floors().getFirst().landing();
        RouteProof.Result proof = RouteProof.prove(opened(world), box, List.of(platform));
        int low = s.plugLow() ? TransitNetwork.PLUG_LOW + 1 : 0, high = s.plugHigh() ? TransitNetwork.PLUG_HIGH - 1 : 255;
        for (int a = low; a <= high; a++) {
            for (int m = 0; m < TransitNetwork.TRACK; m++) {
                Vec3i west = new Vec3i(-1 - m, TransitNetwork.FEET_Y, a);
                assertTrue(proof.roundTrip(west) || world.getBlock(west.x(), west.y(), west.z()) == BlockType.RUBBLE,
                        "track cell " + west + " is walkable from the platform");
            }
        }
        assertTrue(proof.fallTraps().isEmpty(), "no unguarded edge in the station or tunnel: " + proof.fallTraps());
        for (int a = 0; a < 256; a++) {
            if (st.covers(a)) { continue; }
            for (int m = 0; m < TransitNetwork.BAND; m++) {
                for (int y = TransitNetwork.SLAB_Y; y <= TransitNetwork.CEILING_Y; y++) {
                    assertSame(world.getBlock(m, y, a), world.getBlock(-1 - m, y, a),
                            "both regions stamp the same half at m=" + m + " a=" + a + " y=" + y);
                }
            }
        }
    }

    @Test
    void theSpawnStationAndTheJunctionGenerateIdenticallyInEveryChunkOrder() {
        World probe = new World(SEED, GEN);
        TransitStation st = probe.urban.segment(true, 0, 0).station();
        Vec3i top = st.plan().approaches().getFirst();
        compareOrders(Math.floorDiv(top.x(), 16) - 2, Math.floorDiv(top.z(), 16) - 3);
        compareOrders(-3, -3);
    }

    @Test
    void floodedTunnelsAndBasementsHoldTheirWaterUnderTheWaterSystem() {
        Game game = new Game();
        game.newWorld(SEED, true, GEN, GameMode.SURVIVAL);
        World world = game.world;
        DistrictPlan flooded = null;
        for (int r = 1; r <= 3 && flooded == null; r++) {
            for (int rx = -r; rx <= r && flooded == null; rx++) {
                for (int rz = -r; rz <= r && flooded == null; rz++) {
                    if (DistrictMap.districtOf(SEED, rx, rz) == UrbanDistrict.FLOODED) { flooded = world.urban.regionPlan(rx, rz); }
                }
            }
        }
        assertNotNull(flooded);
        for (int cz = flooded.rz * 16 - 1; cz <= flooded.rz * 16 + 16; cz++) {
            for (int cx = flooded.rx * 16 - 1; cx <= flooded.rx * 16 + 16; cx++) { world.getOrCreateChunk(cx, cz); }
        }
        List<Vec3i> water = new ArrayList<>();
        int underground = 0;
        for (int z = flooded.originZ() - 6; z < flooded.originZ() + 262; z++) {
            for (int x = flooded.originX() - 6; x < flooded.originX() + 262; x++) {
                for (int y = TransitNetwork.FEET_Y; y <= UrbanDatum.STREET_Y + 2; y++) {
                    if (world.getBlock(x, y, z) != BlockType.WATER) { continue; }
                    water.add(new Vec3i(x, y, z));
                    if (y < UrbanDatum.STREET_Y) { underground++; }
                    assertTrue(world.getBlock(x, y - 1, z).solid || world.getBlock(x, y - 1, z) == BlockType.WATER,
                            "water stands on a bed at " + x + "," + y + "," + z);
                    game.water.notifyBlockChanged(world, x, y, z);
                }
            }
        }
        assertTrue(underground > 500, "flooded basements, passages and tunnels hold water: " + underground);
        for (int tick = 0; tick < 400 && game.water.activeCount() > 0; tick++) { game.water.mediumTick(game, 0.5f); }
        for (Vec3i cell : water) {
            assertSame(BlockType.WATER, world.getBlock(cell.x(), cell.y(), cell.z()), "water moved at " + cell);
        }
        assertTrue(world.changedBlocks.isEmpty(), "the water system edited nothing");
    }

    private static void compareOrders(int x0, int z0) {
        List<int[]> chunks = new ArrayList<>();
        for (int z = z0; z < z0 + 6; z++) {
            for (int x = x0; x < x0 + 6; x++) { chunks.add(new int[]{x, z}); }
        }
        World reference = null;
        for (int order = 0; order < 7; order++) {
            List<int[]> ordered = new ArrayList<>(chunks);
            if (order == 1) { Collections.reverse(ordered); }
            if (order == 2) { ordered.sort(Comparator.<int[]>comparingInt(c -> c[0]).thenComparingInt(c -> c[1])); }
            if (order >= 3 && order <= 5) { Collections.shuffle(ordered, new Random(order * 13L)); }
            if (order == 6) {
                ordered.sort(Comparator.<int[]>comparingInt(c -> Math.max(Math.abs(c[0] - x0 - 2),
                        Math.abs(c[1] - z0 - 2))).thenComparingDouble(c -> Math.atan2(c[1] - z0 - 2, c[0] - x0 - 2)));
            }
            World world = new World(SEED, GEN);
            for (int[] c : ordered) { world.getOrCreateChunk(c[0], c[1]); }
            assertEquals(0, world.pendingGenerationEditCount());
            if (reference != null) {
                for (int[] c : chunks) {
                    Chunk a = reference.getChunk(c[0], c[1]), b = world.getChunk(c[0], c[1]);
                    assertArrayEquals(UrbanApartmentGenerationTest.bytes(a), UrbanApartmentGenerationTest.bytes(b));
                    assertArrayEquals(a.heightMap, b.heightMap);
                }
            } else { reference = world; }
        }
    }
}
