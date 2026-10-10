package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.StairGeometry;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShapedStairGenerationTest {
    @Test
    void newCatalogueChangesOnlyStairCellsAndPreservesAllRoomFixtureAndBuildingIdentities() {
        for (long seed : new long[]{20260731, -4242, 7}) {
            World old = new World(seed, World.GEN_URBAN_APARTMENTS);
            World shaped = new World(seed, World.GEN_URBAN_SHAPED_STAIRS);
            for (ParcelPlan parcel : old.urban.regionPlan(0, 0).parcels()) {
                BuildingPlan before = old.urban.buildingAt(parcel.x0(), parcel.z0());
                BuildingPlan after = shaped.urban.buildingAt(parcel.x0(), parcel.z0());
                assertEquals(before.id(), after.id());
                assertEquals(before.floors(), after.floors());
                assertEquals(before.fixtures(), after.fixtures());
                assertEquals(before.stairs(), after.stairs());
                assertEquals(before.variant(), after.variant());
                assertEquals(before.basement(), after.basement());
                assertTrue(AccessibilityValidator.inspect(after).ok());
                int stairs = 0;
                PlanVolume v = before.voxels();
                for (int x = v.x0; x < v.x0 + v.width; x++) {
                    for (int z = v.z0; z < v.z0 + v.depth; z++) {
                        for (int y = v.minY; y <= v.maxY; y++) {
                            BlockType a = v.getBlock(x, y, z), b = after.voxels().getBlock(x, y, z);
                            if (a == BlockType.STAIRS) {
                                assertTrue(b == BlockType.CONCRETE || StairGeometry.isStair(b));
                                if (StairGeometry.isStair(b)) { stairs++; }
                            } else { assertSame(a, b, "new template changes no non-stair cell"); }
                        }
                    }
                }
                assertEquals(after.stairs().size() * 8, stairs, "each flight has four two-wide shaped cells");
            }
        }
    }

    @Test
    void aBuildingAcrossChunkBoundariesHasTheSameDirectedTreadsInEitherLoadOrder() {
        World forward = new World(20260731, World.GEN_URBAN_SHAPED_STAIRS);
        World reverse = new World(20260731, World.GEN_URBAN_SHAPED_STAIRS);
        ParcelPlan parcel = forward.urban.regionPlan(0, 0).refuge();
        int x0 = Math.floorDiv(parcel.x0(), 16), x1 = Math.floorDiv(parcel.x1(), 16);
        int z0 = Math.floorDiv(parcel.z0(), 16), z1 = Math.floorDiv(parcel.z1(), 16);
        assertTrue(x0 != x1 || z0 != z1);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) { forward.getOrCreateChunk(x, z); }
        }
        for (int x = x1; x >= x0; x--) {
            for (int z = z1; z >= z0; z--) { reverse.getOrCreateChunk(x, z); }
        }
        for (int x = parcel.x0(); x <= parcel.x1(); x++) {
            for (int z = parcel.z0(); z <= parcel.z1(); z++) {
                for (int y = 0; y < 96; y++) {
                    assertSame(forward.getBlock(x, y, z), reverse.getBlock(x, y, z));
                }
            }
        }
        assertEquals(BuildingPlanDiagnostics.fingerprint(forward.urban.buildingAt(parcel.x0(), parcel.z0())),
                BuildingPlanDiagnostics.fingerprint(reverse.urban.buildingAt(parcel.x0(), parcel.z0())));
    }
}
