package com.veylon.world;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.world.urban.AccessibilityValidator;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlanDiagnostics;
import com.veylon.world.urban.BuildingPlanner;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.DistrictRules;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.PlanVolume;
import com.veylon.world.urban.SuperblockPlan;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanOverlay;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.UrbanSurface;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/** Generator 12: the six districts, their overlays and climates, as actually generated. */
class UrbanDistrictGenerationTest {
    private static final int GEN = World.GEN_URBAN_DISTRICTS;
    private static final int Y = UrbanDatum.STREET_Y;
    private static final long SEED = 20260731L;

    @Test
    void everyDistrictGeneratesItsBuildingsAndOverlaysWithEntryAndExitProof() {
        World world = new World(SEED, GEN);
        int buildings = 0, overlayCells = 0;
        for (UrbanDistrict district : UrbanDistrict.values()) {
            DistrictPlan plan = region(world, district);
            for (int cz = plan.rz * 16; cz < plan.rz * 16 + 16; cz++) {
                for (int cx = plan.rx * 16; cx < plan.rx * 16 + 16; cx++) { world.getOrCreateChunk(cx, cz); }
            }
            int overlays = 0;
            for (int z = plan.originZ(); z < plan.originZ() + 256; z++) {
                for (int x = plan.originX(); x < plan.originX() + 256; x++) {
                    overlays += assertOverlayStamped(world, plan, x, z) ? 1 : 0;
                }
            }
            for (ParcelPlan parcel : plan.parcels()) {
                BuildingPlan building = world.urban.buildingAt(parcel.x0(), parcel.z0());
                PlanVolume v = building.voxels();
                for (int y = v.minY; y <= v.maxY; y++) {
                    for (int z = parcel.z0(); z <= parcel.z1(); z++) {
                        for (int x = parcel.x0(); x <= parcel.x1(); x++) {
                            assertEquals(v.getBlock(x, y, z), world.getBlock(x, y, z), "owned slice differs from proposal");
                        }
                    }
                }
                AccessibilityValidator.Report report = AccessibilityValidator.inspectGenerated(building, plan, world);
                assertTrue(report.ok(), () -> district + " " + BuildingPlanner.identity(building) + " " + report.summary());
                buildings++;
            }
            int cx = plan.originX() + 128, cz = plan.originZ() + 128;
            assertSame(DistrictRules.of(district).climate(), world.biomeAt(cx, cz), "district climate (D-08)");
            assertTrue(district == UrbanDistrict.COMMERCIAL || overlays > 0, district + " has overlays");
            overlayCells += overlays;
        }
        assertTrue(world.fixtures.isEmpty(), "generated locks consume no touched-state capacity");
        assertTrue(world.crateContents.isEmpty(), "reservations roll nothing until searched");
        assertTrue(world.changedBlocks.isEmpty());
        assertEquals(0, world.pendingGenerationEditCount());
        System.out.println("district-generation regions=6 buildings=" + buildings + " overlayCells=" + overlayCells);
    }

    /** The column's blocks are what its overlay and surface plan; true when it has an overlay. */
    private static boolean assertOverlayStamped(World world, DistrictPlan plan, int x, int z) {
        UrbanOverlay o = plan.overlayAt(x, z);
        String at = plan.district + " " + o + " at " + x + "," + z;
        if (plan.surfaceAt(x, z) == UrbanSurface.CANAL) {
            assertSame(BlockType.WATER, world.getBlock(x, Y, z), at);
            assertSame(BlockType.WATER, world.getBlock(x, Y + 1, z), at);
            assertTrue(world.getBlock(x, Y - 1, z).solid, "a canal has a bed " + at);
            assertSame(BlockType.AIR, world.getBlock(x, Y + 2, z), at);
        }
        switch (o) {
            case NONE -> { return false; }
            case STANDING_WATER -> {
                assertSame(BlockType.WATER, world.getBlock(x, Y + 1, z), at);
                assertTrue(world.getBlock(x, Y, z).solid, "flood water stands on the street " + at);
            }
            case DUCKBOARD -> assertSame(BlockType.PLANK, world.getBlock(x, Y + 1, z), at);
            case FENCE -> {
                assertSame(BlockType.SHEET_METAL, world.getBlock(x, Y + 1, z), at);
                assertSame(BlockType.SHEET_METAL, world.getBlock(x, Y + 2, z), at);
            }
            case CHECKPOINT -> {
                assertSame(BlockType.CLAY, world.getBlock(x, Y, z), at);
                assertSame(BlockType.AIR, world.getBlock(x, Y + 1, z), "a checkpoint is open " + at);
            }
            case CONTAMINATED -> assertSame(BlockType.CONTAMINATED_GROUND, world.getBlock(x, Y, z), at);
            case OVERGROWN -> assertSame(BlockType.GRASS, world.getBlock(x, Y, z), at);
            case PLANTS -> {
                BlockType plant = world.getBlock(x, Y + 1, z);
                assertTrue(plant == BlockType.TALL_GRASS || plant == BlockType.BUSH, at);
                assertFalse(plant.solid);
            }
            case KEG -> assertSame(BlockType.POWDER_KEG, world.getBlock(x, Y + 1, z), at);
        }
        return true;
    }

    @Test
    void floodWaterAndCanalsStayWhereTheyAreUnderTheWaterSystem() {
        Game game = new Game();
        game.newWorld(SEED, true, GEN, GameMode.SURVIVAL);
        World world = game.world;
        DistrictPlan flooded = region(world, UrbanDistrict.FLOODED);
        for (int cz = flooded.rz * 16; cz < flooded.rz * 16 + 16; cz++) {
            for (int cx = flooded.rx * 16; cx < flooded.rx * 16 + 16; cx++) { world.getOrCreateChunk(cx, cz); }
        }
        List<Vec3i> water = new ArrayList<>();
        for (int z = flooded.originZ(); z < flooded.originZ() + 256; z++) {
            for (int x = flooded.originX(); x < flooded.originX() + 256; x++) {
                for (int y = Y - 1; y <= Y + 2; y++) {
                    if (world.getBlock(x, y, z) == BlockType.WATER) {
                        water.add(new Vec3i(x, y, z));
                        assertNotSame(BlockType.AIR, world.getBlock(x, y - 1, z), "nothing to fall into");
                        game.water.notifyBlockChanged(world, x, y, z);
                    }
                }
            }
        }
        assertTrue(water.size() > 3_000, "flooded cells " + water.size());
        for (int tick = 0; tick < 400 && game.water.activeCount() > 0; tick++) {
            game.water.mediumTick(game, 0.5f);
        }
        assertEquals(0, game.water.activeCount());
        for (Vec3i cell : water) {
            assertSame(BlockType.WATER, world.getBlock(cell.x(), cell.y(), cell.z()), "water moved at " + cell);
        }
        assertTrue(world.changedBlocks.isEmpty(), "the water system edited nothing");
    }

    @Test
    void districtCornersAndCanalsGenerateIdenticallyInEveryChunkOrder() {
        World world = new World(SEED, GEN);
        DistrictPlan quarantine = region(world, UrbanDistrict.QUARANTINE);
        compareOrders(quarantine.rx * 16 - 3, quarantine.rz * 16 - 3);
        DistrictPlan flooded = region(world, UrbanDistrict.FLOODED);
        SuperblockPlan canal = flooded.superblocks().stream()
                .filter(b -> b.kind() == SuperblockPlan.Kind.CANAL).findFirst().orElseThrow();
        compareOrders(Math.floorDiv(canal.bounds().x0(), 16) - 1, Math.floorDiv(canal.bounds().z0(), 16) - 1);
    }

    @Test
    void catalogueThreeSpendsEachDistrictsThreatBudgetAndKeepsTheStarterSlots() {
        for (long seed : new long[]{SEED, -4242L, 7L}) {
            World world = new World(seed, GEN);
            World ten = new World(seed, World.GEN_URBAN_ENCOUNTERS);
            assertEquals(3, EncounterAnchors.catalogue(world));
            for (UrbanDistrict district : UrbanDistrict.values()) {
                DistrictPlan plan = region(world, district);
                DistrictRules.Profile rules = DistrictRules.of(district);
                int anchors = 0;
                for (ParcelPlan parcel : plan.parcels()) {
                    for (EncounterAnchors.Anchor anchor : EncounterAnchors.forParcel(world, parcel)) {
                        assertEquals(anchor, EncounterAnchors.resolve(world, anchor.id()));
                        BuildingPlan building = world.urban.buildingAt(parcel.x0(), parcel.z0());
                        assertEquals(building.id(), anchor.building());
                        assertNotEquals(BuildingPlan.Variant.OCCUPIED, building.variant(), "held buildings have none");
                        anchors++;
                    }
                }
                assertTrue(anchors <= rules.threatMax(), district + " " + anchors);
                assertTrue(anchors >= rules.threatMin() - 1, district + " budget spent: " + anchors);
            }
            for (ParcelPlan parcel : world.urban.regionPlan(0, 0).parcels()) {
                if (UrbanStarterSite.isNeighbour(parcel)) {
                    assertEquals(EncounterAnchors.forParcel(ten, parcel), EncounterAnchors.forParcel(world, parcel),
                            "the starter slots are generator 10's");
                    assertEquals(BuildingPlanDiagnostics.fingerprint(ten.urban.buildingAt(parcel.x0(), parcel.z0())),
                            BuildingPlanDiagnostics.fingerprint(world.urban.buildingAt(parcel.x0(), parcel.z0())));
                }
            }
        }
    }

    /** The first non-origin region of a district within three of the spawn. */
    private static DistrictPlan region(World world, UrbanDistrict district) {
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                if ((rx != 0 || rz != 0) && DistrictMap.districtOf(world.seed, rx, rz) == district) {
                    return world.urban.regionPlan(rx, rz);
                }
            }
        }
        throw new AssertionError("no " + district);
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
            if (order >= 3 && order <= 5) { Collections.shuffle(ordered, new Random(order * 11L)); }
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
                    assertEquals(a.lights.size(), b.lights.size());
                }
            } else { reference = world; }
        }
    }
}
