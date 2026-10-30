package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lamp and fire light in a generated city stops at walls (urban milestone 22): a torch lights its
 * own side of a wall and whatever it sees through a doorway, an open leaf or glass, never the room
 * behind solid masonry or a closed door. The wilderness keeps its open falloff. Built in open air
 * above the city, where nothing generated stands, so only the staged cells decide.
 */
class UrbanBlockLightTest {
    private static final long SEED = 20260731L;
    private static final int Y = 90;

    @Test
    void aTorchLightsItsOwnSideOfAWallAndNotTheOther() {
        World city = staged(new World(SEED, World.GEN_URBAN_APARTMENTS));
        float open = city.blockLight(-4, Y, 0), behind = city.blockLight(4, Y, 0);
        assertTrue(open > .3f, "four cells away in the open the torch still lights: " + open);
        assertEquals(0f, behind, "the cell behind the wall is dark");
        assertTrue(city.blockLight(1, Y, 0) > open, "the torch's own side of the wall is lit");

        World wilderness = staged(new World(SEED, World.GEN_CAVE_IDENTITIES));
        assertEquals(wilderness.blockLight(-4, Y, 0), wilderness.blockLight(4, Y, 0), 1e-6f,
                "outside a city light still falls off through walls, unchanged");
        assertEquals(open, wilderness.blockLight(-4, Y, 0), 1e-6f, "the falloff itself is unchanged");
    }

    @Test
    void doorwaysOpenLeavesAndGlassLetLightThroughAndClosedDoorsDoNot() {
        World city = staged(new World(SEED, World.GEN_URBAN_APARTMENTS));
        float open = city.blockLight(-4, Y, 0);
        city.setBlock(2, Y, 0, BlockType.AIR, false);
        assertEquals(open, city.blockLight(4, Y, 0), 1e-6f, "a doorway in line with the torch lets it through");
        city.setBlock(2, Y, 0, BlockType.WOOD_DOOR, false);
        assertEquals(0f, city.blockLight(4, Y, 0), "a closed door stops it");
        city.setBlock(2, Y, 0, BlockType.WOOD_DOOR_OPEN, false);
        assertEquals(open, city.blockLight(4, Y, 0), 1e-6f, "an open leaf lets it through");
        city.setBlock(2, Y, 0, BlockType.WINDOW, false);
        assertEquals(open, city.blockLight(4, Y, 0), 1e-6f, "glass lets it through");
        assertEquals(0f, city.blockLight(4, Y, 3), "beside the opening, still behind the wall, it stays dark");
    }

    @Test
    void aRayCannotSqueezeBetweenTwoBlocksThatMeetAtAnEdge() {
        World city = new World(SEED, World.GEN_URBAN_APARTMENTS);
        load(city);
        city.setBlock(1, Y, 0, BlockType.CONCRETE, false);
        city.setBlock(0, Y, 1, BlockType.CONCRETE, false);
        assertFalse(UrbanIndoorQueries.lightReaches(city, 0, Y, 0, 2, Y, 2));
        assertFalse(UrbanIndoorQueries.lightReaches(city, 2, Y, 2, 0, Y, 0));
        city.setBlock(0, Y, 1, BlockType.AIR, false);
        assertTrue(UrbanIndoorQueries.lightReaches(city, 0, Y, 0, 2, Y, 2), "one side open: the ray passes the corner");
        assertTrue(UrbanIndoorQueries.lightReaches(city, 0, Y, 0, 0, Y, 0), "a light reaches its own cell");
        city.setBlock(0, Y + 1, 0, BlockType.CONCRETE, false);
        assertTrue(UrbanIndoorQueries.lightReaches(city, 0, Y, 0, 0, Y + 1, 0), "the end cells never block");
    }

    /** A torch at (0, Y, 0) and a concrete wall at x = 2 spanning five cells each way. */
    private static World staged(World world) {
        load(world);
        for (int dy = -2; dy <= 2; dy++) {
            for (int z = -5; z <= 5; z++) { world.setBlock(2, Y + dy, z, BlockType.CONCRETE, false); }
        }
        world.setBlock(0, Y, 0, BlockType.TORCH, false);
        return world;
    }

    private static void load(World world) {
        for (int cx = -2; cx <= 1; cx++) {
            for (int cz = -2; cz <= 1; cz++) { world.getOrCreateChunk(cx, cz); }
        }
        for (int x = -8; x <= 8; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = Y - 3; y <= Y + 3; y++) {
                    assertEquals(BlockType.AIR, world.getBlock(x, y, z), "the staging volume is open air");
                }
            }
        }
        assertTrue(Y + 3 < Chunk.SY);
    }
}
