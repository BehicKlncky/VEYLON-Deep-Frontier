package com.veylon.entity;

import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerMovementSystemTest {

    private final PlayerMovementSystem system = new PlayerMovementSystem();
    private final PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
    private final PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();

    @Test
    void stepsUpExactlyOneVoxel() {
        World world = flatWorld(0, 1, 0, 0);
        wall(world, 1, 40, 0, 1);
        Player player = player(world, 0.5f, 40, 0.5f);

        move(player, world, false, true, 0.2f);

        assertTrue(player.pos.y >= 40.99f, "one-block ledge is the maximum valid step");
        assertTrue(player.pos.x > 1f);
    }

    @Test
    void rejectsTwoVoxelStep() {
        World world = flatWorld(0, 1, 0, 0);
        wall(world, 1, 40, 0, 2);
        Player player = player(world, 0.5f, 40, 0.5f);

        move(player, world, false, true, 0.2f);

        assertTrue(player.pos.y < 40.01f);
        assertTrue(player.pos.x < 0.8f);
        assertTrue(player.horizontalCollision);
    }

    @Test
    void diagonalMovementCannotChainTwoOneVoxelStepsInOneFrame() {
        World world = flatWorld(0, 1, -1, 0);
        wall(world, 1, 40, 0, 1);
        wall(world, 1, 40, -1, 2);
        Player player = player(world, 0.5f, 40, 0.5f);

        move(player, world, true, true, 0.2f);

        assertTrue(player.pos.y >= 40.99f && player.pos.y < 41.01f,
                "both horizontal axes must share one step allowance");
        assertTrue(player.horizontalCollision,
                "the second, two-block-high ledge must remain blocking");
    }

    @Test
    void diagonalWallCollisionSlidesAlongOpenAxis() {
        World world = flatWorld(0, 1, -1, 0);
        for (int z = -3; z <= 3; z++) {
            wall(world, 1, 40, z, 2);
        }
        Player player = player(world, 0.5f, 40, 0.5f);

        move(player, world, true, true, 0.2f);

        assertTrue(player.pos.x < 0.8f, "wall blocks the X component");
        assertTrue(player.pos.z < 0.2f, "open Z component continues sliding");
    }

    @Test
    void cornerCollisionStopsBothHorizontalAxes() {
        World world = flatWorld(0, 1, -1, 0);
        for (int z = -3; z <= 3; z++) {
            wall(world, 1, 40, z, 2);
        }
        for (int x = -2; x <= 3; x++) {
            wall(world, x, 40, -1, 2);
        }
        Player player = player(world, 0.5f, 40, 0.5f);

        move(player, world, true, true, 0.2f);

        assertTrue(player.pos.x < 0.8f);
        assertTrue(player.pos.z > 0.2f);
        assertTrue(player.horizontalCollision);
    }

    @Test
    void standingBodyKeepsGroundContact() {
        World world = flatWorld(0, 0, 0, 0);
        Player player = player(world, 0.5f, 40, 0.5f);

        move(player, world, false, false, 0.05f);

        assertTrue(player.onGround);
        assertTrue(Math.abs(player.pos.y - 40f) < 0.001f);
    }

    @Test
    void fallingBodyLandsOnGround() {
        World world = flatWorld(0, 0, 0, 0);
        Player player = player(world, 0.5f, 46, 0.5f);
        player.onGround = false;

        for (int i = 0; i < 100 && !player.onGround; i++) {
            move(player, world, false, false, 0.05f);
        }

        assertTrue(player.onGround);
        assertTrue(player.pos.y >= 39.99f && player.pos.y < 40.2f);
        assertTrue(Math.abs(player.vel.y) < 0.001f);
    }

    @Test
    void jumpHeldInWaterProducesUpwardMovement() {
        World world = flatWorld(0, 0, 0, 0);
        world.setBlock(0, 40, 0, BlockType.WATER, false);
        Player player = player(world, 0.5f, 40, 0.5f);
        move(player, world, false, false, 0.05f);
        float before = player.pos.y;

        command.set(0, false, false, false, false,
                false, false, true, true);
        system.update(player, world, command, 0.05f, result);

        assertTrue(player.inWater);
        assertTrue(player.pos.y > before);
        assertTrue(player.vel.y > 0);
    }

    @Test
    void jumpHeldOnLadderClimbsWithoutGravityLoss() {
        World world = flatWorld(0, 0, 0, 0);
        world.setBlock(0, 40, 0, BlockType.LADDER, false);
        Player player = player(world, 0.5f, 40, 0.5f);
        move(player, world, false, false, 0.05f);
        float before = player.pos.y;

        command.set(0, false, false, false, false,
                false, false, true, true);
        system.update(player, world, command, 0.05f, result);

        assertTrue(player.onLadder);
        assertTrue(player.pos.y > before);
        assertTrue(player.vel.y >= PlayerMovementSystem.LADDER_ASCEND_SPEED - 0.001f);
    }

    @Test
    void movementCrossesLoadedChunkBoundary() {
        World world = flatWorld(0, 1, 0, 0);
        Player player = player(world, 15.6f, 40, 0.5f);

        move(player, world, false, true, 0.2f);

        assertTrue(player.pos.x > 16f);
        assertTrue(player.onGround);
    }

    @Test
    void missingNeighborChunkIsSafeAndDoesNotMaterializeIt() {
        World world = flatWorld(0, 0, 0, 0);
        Player player = player(world, 15.6f, 40, 0.5f);
        int loaded = world.loadedCount();

        assertDoesNotThrow(() -> move(player, world, false, true, 0.2f));

        assertTrue(Float.isFinite(player.pos.x) && Float.isFinite(player.pos.y));
        assertTrue(world.loadedCount() == loaded);
        assertNull(world.getChunk(1, 0));
    }

    private void move(Player player, World world, boolean forward, boolean right, float dt) {
        command.set(0, forward, false, false, right,
                false, false, false, false);
        system.update(player, world, command, dt, result);
    }

    private static Player player(World world, float x, float y, float z) {
        Player player = new Player(world);
        player.pos.set(x, y, z);
        player.onGround = true;
        return player;
    }

    private static void wall(World world, int x, int y, int z, int height) {
        for (int dy = 0; dy < height; dy++) {
            world.setBlock(x, y + dy, z, BlockType.STONE, false);
        }
    }

    private static World flatWorld(int minCx, int maxCx, int minCz, int maxCz) {
        World world = new World(991L, World.GEN_LEGACY);
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Chunk chunk = world.getOrCreateChunk(cx, cz);
                for (int x = 0; x < Chunk.SX; x++) {
                    for (int z = 0; z < Chunk.SZ; z++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            chunk.set(x, y, z, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                chunk.recomputeAllHeights();
            }
        }
        return world;
    }
}
