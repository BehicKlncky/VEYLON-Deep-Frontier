package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.BlockType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FireAudioLocatorTest {
    @Test void nearestFueledCampfireWinsAndExtinguishedFireIsSilent() {
        World world = new World(42);
        world.getOrCreateChunk(0, 0);
        Vec3i near = new Vec3i(4, 90, 4), far = new Vec3i(7, 90, 4);
        for (Vec3i fire : List.of(near, far)) {
            world.setBlock(fire.x(), fire.y(), fire.z(), BlockType.CAMPFIRE, false);
            world.campfireFuel.put(fire, 100f);
        }
        Vector3f out = new Vector3f();
        assertTrue(FireAudioLocator.nearest(world, List.of(), 3.5f, 90.5f, 4.5f, out));
        assertEquals(new Vector3f(4.5f, 90.5f, 4.5f), out);
        world.campfireFuel.put(near, 0f);
        assertTrue(FireAudioLocator.nearest(world, List.of(), 3.5f, 90.5f, 4.5f, out));
        assertEquals(new Vector3f(7.5f, 90.5f, 4.5f), out);
    }

    @Test void burningCellsWorkAcrossNegativeCoordinatesWithoutGeneratingChunks() {
        World world = new World(42);
        int chunks = world.loadedCount();
        Vector3f out = new Vector3f();
        assertTrue(FireAudioLocator.nearest(world, List.of(new Vec3i(-2, 40, -2)), -0.2f, 40, -0.2f, out));
        assertEquals(new Vector3f(-1.5f, 40.5f, -1.5f), out);
        assertEquals(chunks, world.loadedCount());
        assertFalse(FireAudioLocator.nearest(world, List.of(new Vec3i(100, 40, 100)), 0, 40, 0, out));
        assertEquals(2197, FireAudioLocator.MAX_CELL_QUERIES);
    }
}
