package com.veylon.world;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StairGeometryTest {
    @Test
    void treadsSupportAtHalfAndFullHeightAndTargetTheActualFaceInAllFourDirections() {
        World world = new World(7, World.GEN_URBAN);
        world.getOrCreateChunk(0, 0);
        for (BlockType type : new BlockType[]{BlockType.STAIRS_NORTH, BlockType.STAIRS_SOUTH,
                BlockType.STAIRS_WEST, BlockType.STAIRS_EAST}) {
            world.setBlock(8, 50, 8, type, true);
            for (int part = 0; part < 2; part++) {
                float x = 8 + (StairGeometry.minX(type, part) + StairGeometry.maxX(type, part)) / 2;
                float z = 8 + (StairGeometry.minZ(type, part) + StairGeometry.maxZ(type, part)) / 2;
                float top = 50 + StairGeometry.height(part);
                assertFalse(VoxelClearance.collides(world, x, top, z, .2f, 1.8f, false));
                assertTrue(VoxelClearance.collides(world, x, top - .01f, z, .2f, 1.8f, false));
                Raycaster.Hit hit = Raycaster.cast(world, new Vector3f(x, 52, z), new Vector3f(0, -1, 0), 3, false);
                assertNotNull(hit);
                assertSame(type, hit.type());
                assertEquals(52 - top, hit.dist(), 1e-5);
                assertEquals(1, hit.ny(), "placement targets the actual tread top");
            }
            // Aim horizontally through the absent upper quarter, hitting the wall behind it.
            float lx = 8 + (StairGeometry.minX(type, 0) + StairGeometry.maxX(type, 0)) / 2;
            float lz = 8 + (StairGeometry.minZ(type, 0) + StairGeometry.maxZ(type, 0)) / 2;
            boolean alongX = type == BlockType.STAIRS_NORTH || type == BlockType.STAIRS_SOUTH;
            world.setBlock(alongX ? 10 : (int) lx, 50, alongX ? (int) lz : 10, BlockType.BRICK, true);
            Raycaster.Hit miss = Raycaster.cast(world,
                    new Vector3f(alongX ? 6 : lx, 50.75f, alongX ? lz : 6),
                    new Vector3f(alongX ? 1 : 0, 0, alongX ? 0 : 1), 5, false);
            assertNotNull(miss);
            assertSame(BlockType.BRICK, miss.type(), "the removed stair volume cannot intercept the crosshair");
            world.setBlock(alongX ? 10 : (int) lx, 50, alongX ? (int) lz : 10, BlockType.AIR, true);
        }
        world.setBlock(8, 50, 8, BlockType.STAIRS, true);
        assertTrue(VoxelClearance.collides(world, 8.25f, 50.5f, 8.25f, .2f, 1.8f, false),
                "historical stair blocks retain their full-cube collider");
    }
}
