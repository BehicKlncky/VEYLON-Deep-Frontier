package com.veylon.world;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WorldCoordinateContractTest {

    private World world;

    @BeforeEach
    void setUp() {
        world = new World(801L, World.GEN_LEGACY);
        world.getOrCreateChunk(0, 0);
    }

    @Test
    void negativeYWriteIsSafeNoOp() {
        assertDoesNotThrow(() -> world.setBlock(0, -1, 0, BlockType.LOG, true));
        assertEquals(BlockType.AIR, world.getBlock(0, -1, 0));
    }

    @Test
    void upperYWriteIsSafeNoOp() {
        assertDoesNotThrow(() -> world.setBlock(0, Chunk.SY, 0, BlockType.LOG, true));
        assertEquals(BlockType.AIR, world.getBlock(0, Chunk.SY, 0));
    }

    @Test
    void extremeHorizontalCoordinatesDoNotOverflowOrCreateChunks() {
        int before = world.loadedCount();
        assertDoesNotThrow(() -> world.setBlock(Integer.MIN_VALUE, 40,
                Integer.MAX_VALUE, BlockType.LOG, true));
        assertEquals(before, world.loadedCount());
    }

    @Test
    void unavailableChunkWriteIsSafeNoOp() {
        world.setBlock(32, 40, 0, BlockType.LOG, true);
        assertEquals(BlockType.AIR, world.getBlock(32, 40, 0));
        assertFalse(world.changedBlocks.containsKey(new com.veylon.util.Vec3i(32, 40, 0)));
    }

    @Test
    void validLoadedChunkBoundaryWriteUsesFloorCoordinates() {
        world.getOrCreateChunk(-1, 0);
        world.setBlock(-1, 40, 0, BlockType.LOG, true);

        assertEquals(BlockType.LOG, world.getBlock(-1, 40, 0));
        assertEquals(BlockType.LOG, world.getChunk(-1, 0).get(15, 40, 0));
    }
}
