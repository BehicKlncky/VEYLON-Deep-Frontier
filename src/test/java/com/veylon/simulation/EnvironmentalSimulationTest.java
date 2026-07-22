package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EnvironmentalSimulationTest {

    @Test
    void waterFallsIntoEmptyCellBeforeSpreadingSideways() {
        Game game = game(1001L);
        int x = (int) game.player.pos.x + 3;
        int y = Math.min(90, (int) game.player.pos.y + 8);
        int z = (int) game.player.pos.z;
        game.world.setBlock(x, y - 2, z, BlockType.STONE, false);
        game.world.setBlock(x, y - 1, z, BlockType.AIR, false);
        game.world.setBlock(x, y, z, BlockType.WATER, false);

        game.water.mediumTick(game, 0.5f);

        assertEquals(BlockType.AIR, game.world.getBlock(x, y, z));
        assertEquals(BlockType.WATER, game.world.getBlock(x, y - 1, z));
    }

    @Test
    void waterSpreadsAtSeaLevelButNotAboveIt() {
        Game sea = game(1002L);
        int x = (int) sea.player.pos.x + 4;
        int z = (int) sea.player.pos.z;
        prepareSupportedWater(sea, x, World.SEA_LEVEL, z);
        sea.water.mediumTick(sea, 0.5f);
        int seaNeighbors = horizontalWaterNeighbors(sea, x, World.SEA_LEVEL, z);

        Game high = game(1002L);
        prepareSupportedWater(high, x, World.SEA_LEVEL + 1, z);
        high.water.mediumTick(high, 0.5f);

        assertEquals(4, seaNeighbors);
        assertEquals(0, horizontalWaterNeighbors(high, x, World.SEA_LEVEL + 1, z));
    }

    @Test
    void deepUndergroundTemperatureStabilizesNearNineDegrees() {
        Game game = game(1003L);
        int x = (int) game.player.pos.x;
        int z = (int) game.player.pos.z;
        int surface = game.world.surfaceHeight(x, z);

        float temperature = game.temperature.envTempAt(game, x + 0.5f, surface - 12, z + 0.5f);

        assertEquals(9f, temperature, 0.01f);
    }

    @Test
    void altitudeCoolsTheSameBiomeColumn() {
        Game game = game(1004L);
        int x = (int) game.player.pos.x;
        int z = (int) game.player.pos.z;

        float low = game.temperature.envTempAt(game, x + 0.5f, 40, z + 0.5f);
        float high = game.temperature.envTempAt(game, x + 0.5f, 70, z + 0.5f);

        assertTrue(high < low - 8f);
    }

    @Test
    void roofAndWallsProduceIndoorShelterCoverage() {
        World world = new World(1005L, World.GEN_LEGACY);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    chunk.set(x, y, z, BlockType.AIR);
                }
            }
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    world.setBlock(8 + dx, 10, 8 + dz, BlockType.STONE, false);
                    world.setBlock(8 + dx, 11, 8 + dz, BlockType.STONE, false);
                }
            }
        }
        world.setBlock(8, 12, 8, BlockType.STONE, false);

        ShelterSystem.Shelter shelter = ShelterSystem.evaluate(world, 8.5f, 10, 8.5f);

        assertTrue(shelter.indoor());
        assertEquals(1f, shelter.coverage(), 0.001f);
    }

    @Test
    void seededBerryBushRegrowthProducesRealBlocksAndGrowthEvents() {
        Game game = game(1006L);
        Chunk chunk = game.world.getChunk(
                Math.floorDiv((int) game.player.pos.x, 16),
                Math.floorDiv((int) game.player.pos.z, 16));
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                int h = chunk.height(lx, lz);
                game.world.setBlock(chunk.cx * 16 + lx, h + 1,
                        chunk.cz * 16 + lz, BlockType.BERRY_BUSH_EMPTY, false);
            }
        }
        chunk.moisture = 1f;
        game.player.envTemp = 15f;
        game.plants.setRandomSeed(77L);

        for (int i = 0; i < 5 && game.plants.growthEvents == 0; i++) {
            game.plants.slowTick(game, 10f);
        }

        assertTrue(game.plants.growthEvents > 0);
        int bushes = 0;
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                int h = chunk.height(lx, lz);
                if (chunk.get(lx, h + 1, lz) == BlockType.BERRY_BUSH) {
                    bushes++;
                }
            }
        }
        assertTrue(bushes > 0);
    }

    private static Game game(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        return game;
    }

    private static void prepareSupportedWater(Game game, int x, int y, int z) {
        game.world.setBlock(x, y - 1, z, BlockType.STONE, false);
        game.world.setBlock(x, y, z, BlockType.WATER, false);
        game.world.setBlock(x + 1, y, z, BlockType.AIR, false);
        game.world.setBlock(x - 1, y, z, BlockType.AIR, false);
        game.world.setBlock(x, y, z + 1, BlockType.AIR, false);
        game.world.setBlock(x, y, z - 1, BlockType.AIR, false);
    }

    private static int horizontalWaterNeighbors(Game game, int x, int y, int z) {
        int count = 0;
        if (game.world.getBlock(x + 1, y, z) == BlockType.WATER) count++;
        if (game.world.getBlock(x - 1, y, z) == BlockType.WATER) count++;
        if (game.world.getBlock(x, y, z + 1) == BlockType.WATER) count++;
        if (game.world.getBlock(x, y, z - 1) == BlockType.WATER) count++;
        return count;
    }
}
