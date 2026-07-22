package com.veylon.ai;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Bounded A*: walls, gates, step-up, drop limits and unloaded-chunk safety. */
class PathfinderTest {

    /** Builds a flat 3-chunk-square test arena at y=40 around the origin chunk. */
    private World flatWorld() {
        World w = new World(555L, World.GEN_LEGACY);
        for (int cx = -1; cx <= 1; cx++) {
            for (int cz = -1; cz <= 1; cz++) {
                Chunk c = w.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
            }
        }
        return w;
    }

    @Test
    void findsAStraightPathOnFlatGround() {
        World w = flatWorld();
        List<Vec3i> path = Pathfinder.find(w, 0, 40, 0, 10, 40, 0, Pathfinder.DEFAULT_BUDGET);
        assertNotNull(path);
        assertTrue(path.size() >= 10);
        assertTrue(path.getLast().distSq(10.5, 40.5, 0.5) < 4);
    }

    @Test
    void refusesToPathThroughAClosedWall() {
        World w = flatWorld();
        // A full-height wall across x=5 with no opening.
        for (int z = -8; z <= 8; z++) {
            for (int y = 40; y <= 44; y++) {
                w.setBlock(5, y, z, BlockType.WALL, false);
            }
        }
        List<Vec3i> path = Pathfinder.find(w, 0, 40, 0, 10, 40, 0, Pathfinder.DEFAULT_BUDGET);
        if (path != null) {
            // If a path exists it must go around the wall ends, never through.
            for (Vec3i cell : path) {
                assertTrue(cell.x() != 5 || Math.abs(cell.z()) > 8,
                        "path crosses the solid wall at " + cell);
            }
        }
    }

    @Test
    void routesThroughAGateInAWall() {
        World w = flatWorld();
        for (int z = -8; z <= 8; z++) {
            for (int y = 40; y <= 43; y++) {
                w.setBlock(5, y, z, BlockType.WALL, false);
            }
        }
        // Gate doorway at z=0.
        w.setBlock(5, 40, 0, BlockType.GATE, false);
        w.setBlock(5, 41, 0, BlockType.GATE, false);
        List<Vec3i> path = Pathfinder.find(w, 0, 40, 0, 10, 40, 0, Pathfinder.DEFAULT_BUDGET);
        assertNotNull(path, "gate should make the wall passable");
        boolean throughGate = false;
        for (Vec3i cell : path) {
            if (cell.x() == 5 && cell.z() == 0) {
                throughGate = true;
            }
        }
        assertTrue(throughGate, "path should pass through the gate cell");
    }

    @Test
    void handlesSingleStepUpAndRejectsTallLedges() {
        World w = flatWorld();
        // A one-block step at x=4..10 (walkable) and a 4-block cliff at x=12.
        for (int x = 4; x <= 10; x++) {
            for (int z = -3; z <= 3; z++) {
                w.setBlock(x, 40, z, BlockType.STONE, false);
            }
        }
        List<Vec3i> up = Pathfinder.find(w, 0, 40, 0, 8, 41, 0, Pathfinder.DEFAULT_BUDGET);
        assertNotNull(up, "one-block step-up must be pathable");

        World w2 = flatWorld();
        for (int x = 6; x <= 14; x++) {
            for (int z = -8; z <= 8; z++) {
                for (int y = 40; y <= 43; y++) {
                    w2.setBlock(x, y, z, BlockType.STONE, false);
                }
            }
        }
        List<Vec3i> cliff = Pathfinder.find(w2, 0, 40, 0, 10, 44, 0, 300);
        // Either no path, or the path must not teleport up more than 1 block/step.
        if (cliff != null) {
            for (int i = 1; i < cliff.size(); i++) {
                assertTrue(cliff.get(i).y() - cliff.get(i - 1).y() <= 1,
                        "path jumps more than one block upward");
            }
        }
    }

    @Test
    void neverTouchesUnloadedChunksAndRespectsBudget() {
        World w = flatWorld();
        // Target far outside the loaded 3x3 chunk area.
        assertNull(Pathfinder.find(w, 0, 40, 0, 400, 40, 400, Pathfinder.DEFAULT_BUDGET),
                "outside MAX_RANGE must be rejected outright");
        // Within range but leading into unloaded chunks: bounded failure, no crash.
        List<Vec3i> path = Pathfinder.find(w, 10, 40, 10, 40, 40, 40, 200);
        if (path != null) {
            for (Vec3i cell : path) {
                assertNotNull(w.getChunk(Math.floorDiv(cell.x(), 16), Math.floorDiv(cell.z(), 16)),
                        "path entered an unloaded chunk at " + cell);
            }
        }
    }

    @Test
    void longestMazeBelowReconstructionCapConnectsStartToGoal() {
        World world = snakeMaze(15);
        int goalX = 30;
        int goalZ = 28;

        List<Vec3i> path = Pathfinder.find(
                world, 0, 40, 0, goalX, 40, goalZ, 2_000);

        assertNotNull(path);
        assertEquals(new Vec3i(0, 40, 0), path.getFirst());
        assertTrue(path.getLast().distSq(goalX + 0.5, 40.5, goalZ + 0.5) < 4);
        assertTrue(path.size() < Pathfinder.MAX_PATH_NODES);
        assertTrue(path.size() > 450, "fixture must exercise a near-cap complete chain");
    }

    @Test
    void reconstructionCapReturnsFailureInsteadOfDisconnectedSuffix() {
        World world = snakeMaze(18);
        int goalX = 0;
        int goalZ = 34;

        assertNull(Pathfinder.find(world, 0, 40, 0,
                        goalX, 40, goalZ, 2_000),
                "a chain beyond the defensive cache cap must fail, never truncate");
    }

    @Test
    void waterCostPrefersShortDryDetour() {
        World world = flatWorld();
        for (int x = 2; x <= 8; x++) {
            world.setBlock(x, 40, 0, BlockType.WATER, false);
        }

        List<Vec3i> path = Pathfinder.find(
                world, 0, 40, 0, 10, 40, 0, Pathfinder.DEFAULT_BUDGET);

        assertNotNull(path);
        assertTrue(path.stream().noneMatch(cell ->
                        world.getBlock(cell.x(), cell.y(), cell.z()) == BlockType.WATER),
                "water cost should make the one-cell dry detour cheaper");
    }

    @Test
    void acceptsMaximumDropAndRejectsOneVoxelMore() {
        World allowed = flatWorld();
        for (int y = 40; y <= 42; y++) {
            allowed.setBlock(0, y, 0, BlockType.STONE, false);
        }
        List<Vec3i> path = Pathfinder.find(
                allowed, 0, 43, 0, 1, 40, 0, Pathfinder.DEFAULT_BUDGET);
        assertNotNull(path);
        assertEquals(-Pathfinder.MAX_DROP,
                path.get(1).y() - path.getFirst().y());

        World rejected = flatWorld();
        for (int y = 40; y <= 43; y++) {
            rejected.setBlock(0, y, 0, BlockType.STONE, false);
        }
        assertNull(Pathfinder.find(rejected, 0, 44, 0,
                1, 40, 0, Pathfinder.DEFAULT_BUDGET));
    }

    private World snakeMaze(int rows) {
        World world = new World(556L, World.GEN_LEGACY);
        for (int cx = -1; cx <= 2; cx++) {
            for (int cz = -1; cz <= 2; cz++) {
                Chunk chunk = world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            chunk.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                chunk.recomputeAllHeights();
            }
        }
        int maxZ = (rows - 1) * 2;
        for (int x = -1; x <= 31; x++) {
            for (int z = -1; z <= maxZ + 1; z++) {
                world.setBlock(x, 40, z, BlockType.WALL, false);
                world.setBlock(x, 41, z, BlockType.WALL, false);
            }
        }
        for (int row = 0; row < rows; row++) {
            int z = row * 2;
            for (int x = 0; x <= 30; x++) {
                world.setBlock(x, 40, z, BlockType.AIR, false);
                world.setBlock(x, 41, z, BlockType.AIR, false);
            }
            if (row + 1 < rows) {
                int connectorX = row % 2 == 0 ? 30 : 0;
                world.setBlock(connectorX, 40, z + 1, BlockType.AIR, false);
                world.setBlock(connectorX, 41, z + 1, BlockType.AIR, false);
            }
        }
        return world;
    }
}
