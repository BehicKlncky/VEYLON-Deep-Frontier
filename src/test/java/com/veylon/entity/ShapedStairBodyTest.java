package com.veylon.entity;

import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ShapedStairBodyTest {
    @Test
    void aFallenBodyRestsOnTheLowTreadAndProjectionDoesNotPushItIntoTheMissingCube() {
        World world = new World(7, World.GEN_URBAN);
        world.getOrCreateChunk(0, 0);
        world.setBlock(8, 50, 8, BlockType.STAIRS_EAST, true);
        RagdollCollision collision = new RagdollCollision();
        collision.move(world, 8.2f, 52, 8.5f, 0, -2, 0, .1f, .1f);
        assertTrue(collision.grounded);
        assertEquals(50.6f, collision.y, .002f);
        assertFalse(collision.blocked(world, 8.2f, 50.8f, 8.5f, .1f, .1f));
        collision.project(world, 8.2f, 50.8f, 8.5f, .1f);
        assertEquals(50.8f, collision.y, .001f);
        collision.project(world, 8.2f, 50.55f, 8.5f, .1f);
        assertTrue(collision.grounded);
        assertEquals(50.6f, collision.y, .01f);
    }
}
