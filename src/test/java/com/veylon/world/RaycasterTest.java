package com.veylon.world;

import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RaycasterTest {

    private World world;
    private Raycaster.MutableHit result;

    @BeforeEach
    void setUp() {
        world = new World(123L, World.GEN_LEGACY);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    chunk.set(x, y, z, BlockType.AIR);
                }
            }
        }
        chunk.recomputeAllHeights();
        chunk.rebuildLights(world);
        result = new Raycaster.MutableHit();
    }

    @Test
    void castsPositiveX() {
        assertAxisHit(9, 5, 5, new Vector3f(1, 0, 0), -1, 0, 0);
    }

    @Test
    void castsNegativeX() {
        assertAxisHit(1, 5, 5, new Vector3f(-1, 0, 0), 1, 0, 0);
    }

    @Test
    void castsPositiveY() {
        assertAxisHit(5, 9, 5, new Vector3f(0, 1, 0), 0, -1, 0);
    }

    @Test
    void castsNegativeY() {
        assertAxisHit(5, 1, 5, new Vector3f(0, -1, 0), 0, 1, 0);
    }

    @Test
    void castsPositiveZ() {
        assertAxisHit(5, 5, 9, new Vector3f(0, 0, 1), 0, 0, -1);
    }

    @Test
    void castsNegativeZ() {
        assertAxisHit(5, 5, 1, new Vector3f(0, 0, -1), 0, 0, 1);
    }

    @Test
    void boundaryStartCanHitAdjacentVoxelAtZeroDistance() {
        world.setBlock(0, 5, 5, BlockType.STONE, false);

        assertTrue(Raycaster.castInto(world, new Vector3f(1, 5.5f, 5.5f),
                new Vector3f(-1, 0, 0), 2, false, result));

        assertEquals(0, result.x());
        assertEquals(0.0, result.dist(), 0.000001);
        assertEquals(1, result.nx());
    }

    @Test
    void maximumDistanceIsInclusive() {
        world.setBlock(5, 5, 5, BlockType.STONE, false);
        Vector3f origin = new Vector3f(0.5f, 5.5f, 5.5f);
        Vector3f direction = new Vector3f(1, 0, 0);

        assertFalse(Raycaster.castInto(world, origin, direction, 4.499, false, result));
        assertTrue(Raycaster.castInto(world, origin, direction, 4.5, false, result));
        assertEquals(4.5, result.dist(), 0.000001);
    }

    @Test
    void fluidsAreOptional() {
        world.setBlock(3, 5, 5, BlockType.WATER, false);
        Vector3f origin = new Vector3f(0.5f, 5.5f, 5.5f);
        Vector3f direction = new Vector3f(1, 0, 0);

        assertFalse(Raycaster.castInto(world, origin, direction, 4, false, result));
        assertTrue(Raycaster.castInto(world, origin, direction, 4, true, result));
        assertEquals(BlockType.WATER, result.type());
    }

    @Test
    void emptySpaceReturnsMissAndClearsReusableResult() {
        world.setBlock(3, 5, 5, BlockType.STONE, false);
        assertTrue(Raycaster.castInto(world, new Vector3f(0.5f, 5.5f, 5.5f),
                new Vector3f(1, 0, 0), 4, false, result));
        world.setBlock(3, 5, 5, BlockType.AIR, false);

        assertFalse(Raycaster.castInto(world, new Vector3f(0.5f, 5.5f, 5.5f),
                new Vector3f(1, 0, 0), 4, false, result));
        assertFalse(result.hit());
        assertNull(result.type());
    }

    @Test
    void zeroLengthDirectionReturnsMiss() {
        world.setBlock(3, 5, 5, BlockType.STONE, false);

        assertFalse(Raycaster.castInto(world, new Vector3f(0.5f, 5.5f, 5.5f),
                new Vector3f(), 10, false, result));
        assertFalse(result.hit());
    }

    @Test
    void independentReusableResultsDoNotOverwriteEachOther() {
        world.setBlock(3, 5, 5, BlockType.STONE, false);
        world.setBlock(5, 8, 5, BlockType.LOG, false);
        Raycaster.MutableHit first = new Raycaster.MutableHit();
        Raycaster.MutableHit second = new Raycaster.MutableHit();

        assertTrue(Raycaster.castInto(world, new Vector3f(0.5f, 5.5f, 5.5f),
                new Vector3f(1, 0, 0), 5, false, first));
        assertTrue(Raycaster.castInto(world, new Vector3f(5.5f, 5.5f, 5.5f),
                new Vector3f(0, 1, 0), 5, false, second));

        assertEquals(3, first.x());
        assertEquals(BlockType.STONE, first.type());
        assertEquals(8, second.y());
        assertEquals(BlockType.LOG, second.type());
    }

    @Test
    void immutableCompatibilityApiKeepsResultAfterLaterCasts() {
        world.setBlock(3, 5, 5, BlockType.STONE, false);
        Raycaster.Hit immutable = Raycaster.cast(world,
                new Vector3f(0.5f, 5.5f, 5.5f), new Vector3f(1, 0, 0), 5, false);
        world.setBlock(3, 5, 5, BlockType.AIR, false);
        Raycaster.castInto(world, new Vector3f(0.5f, 5.5f, 5.5f),
                new Vector3f(1, 0, 0), 5, false, result);

        assertEquals(BlockType.STONE, immutable.type());
        assertEquals(3, immutable.x());
    }

    private void assertAxisHit(int x, int y, int z, Vector3f direction,
                               int nx, int ny, int nz) {
        world.setBlock(x, y, z, BlockType.STONE, false);
        assertTrue(Raycaster.castInto(world, new Vector3f(5.5f, 5.5f, 5.5f),
                direction, 10, false, result));
        assertEquals(x, result.x());
        assertEquals(y, result.y());
        assertEquals(z, result.z());
        assertEquals(nx, result.nx());
        assertEquals(ny, result.ny());
        assertEquals(nz, result.nz());
    }
}
