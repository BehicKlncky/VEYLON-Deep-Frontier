package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

import java.util.Random;

/**
 * Slow-tick ecology: soil moisture per chunk, berry regrowth, grass spread,
 * sapling growth and tree seeding. Rain raises moisture, drought lowers it,
 * cold slows everything.
 */
public class PlantSystem {

    private static final int ACTIVE_RADIUS = 4;
    private static final int ATTEMPTS_PER_CHUNK = 18;

    private final Random rng = new Random();
    public long growthEvents = 0;
    public float lastAvgMoisture = 0.5f;

    public void slowTick(Game g, float dt) {
        World world = g.world;
        int pcx = Math.floorDiv((int) g.player.pos.x, 16);
        int pcz = Math.floorDiv((int) g.player.pos.z, 16);

        float moistureSum = 0;
        int chunkCount = 0;

        for (int dx = -ACTIVE_RADIUS; dx <= ACTIVE_RADIUS; dx++) {
            for (int dz = -ACTIVE_RADIUS; dz <= ACTIVE_RADIUS; dz++) {
                Chunk c = world.getChunk(pcx + dx, pcz + dz);
                if (c == null || !c.generated) {
                    continue;
                }
                updateMoisture(g, c, dt);
                moistureSum += c.moisture;
                chunkCount++;
                growChunk(g, c);
            }
        }
        if (chunkCount > 0) {
            lastAvgMoisture = moistureSum / chunkCount;
        }
    }

    private void updateMoisture(Game g, Chunk c, float dt) {
        Biome biome = g.world.biomeAt(c.cx * 16 + 8, c.cz * 16 + 8);
        float delta;
        if (g.weather.isPrecip()) {
            delta = 0.010f * g.weather.intensity() * dt;
        } else if (g.events.isDrought()) {
            delta = -0.008f * dt;
        } else {
            // Drift back toward the biome baseline.
            delta = (biome.moisture - c.moisture) * 0.002f * dt;
        }
        c.moisture = Math.max(0.02f, Math.min(1f, c.moisture + delta));
        // Marshes never fully dry out.
        if (biome == Biome.MARSH && c.moisture < 0.5f) {
            c.moisture = 0.5f;
        }
    }

    private void growChunk(Game g, Chunk c) {
        World world = g.world;
        float cold = g.player.envTemp < 0 ? 0.3f : 1f;
        float growth = c.moisture * cold * g.events.growthMul()
                * g.seasons.current(g.time).growthMul;

        for (int i = 0; i < ATTEMPTS_PER_CHUNK; i++) {
            int lx = rng.nextInt(16);
            int lz = rng.nextInt(16);
            int wx = c.cx * 16 + lx;
            int wz = c.cz * 16 + lz;
            int h = c.height(lx, lz);
            BlockType surface = c.get(lx, h, lz);
            BlockType above = world.getBlock(wx, h + 1, wz);

            // Berry bushes regrow their berries.
            if (above == BlockType.BERRY_BUSH_EMPTY) {
                if (rng.nextFloat() < 0.10f * growth * g.events.berryMul()) {
                    world.setBlock(wx, h + 1, wz, BlockType.BERRY_BUSH, true);
                    growthEvents++;
                }
                continue;
            }
            // Saplings grow into trees.
            if (above == BlockType.SAPLING) {
                if (rng.nextFloat() < 0.15f * growth) {
                    world.generator.growTreeRuntime(wx, h + 1, wz, world.biomeAt(wx, wz));
                    growthEvents++;
                }
                continue;
            }
            // Dirt regrows grass when next to grass.
            if (surface == BlockType.DIRT && above == BlockType.AIR) {
                if (hasNeighborGrass(world, wx, h, wz) && rng.nextFloat() < 0.25f * growth) {
                    world.setBlock(wx, h, wz, BlockType.GRASS, true);
                    growthEvents++;
                }
                continue;
            }
            if (surface == BlockType.GRASS && above == BlockType.AIR) {
                float r = rng.nextFloat();
                Biome biome = world.biomeAt(wx, wz);
                // Tall grass and bushes slowly recolonize; herbs sprout in damp biomes.
                if (r < 0.05f * growth * biome.plantDensity * 12) {
                    world.setBlock(wx, h + 1, wz, BlockType.TALL_GRASS, true);
                    growthEvents++;
                } else if (r < 0.06f * growth * biome.plantDensity * 12
                        && (biome == Biome.MARSH || biome == Biome.MEADOW)) {
                    world.setBlock(wx, h + 1, wz, BlockType.HERB_PLANT, true);
                    growthEvents++;
                } else if (r > 0.995f - 0.004f * growth && nearLeaves(world, wx, h + 1, wz)) {
                    // Trees seed saplings nearby.
                    world.setBlock(wx, h + 1, wz, BlockType.SAPLING, true);
                    growthEvents++;
                }
            }
        }
    }

    private boolean hasNeighborGrass(World world, int x, int y, int z) {
        return world.getBlock(x + 1, y, z) == BlockType.GRASS
                || world.getBlock(x - 1, y, z) == BlockType.GRASS
                || world.getBlock(x, y, z + 1) == BlockType.GRASS
                || world.getBlock(x, y, z - 1) == BlockType.GRASS;
    }

    private boolean nearLeaves(World world, int x, int y, int z) {
        for (int dx = -4; dx <= 4; dx += 2) {
            for (int dz = -4; dz <= 4; dz += 2) {
                for (int dy = 2; dy <= 7; dy++) {
                    if (world.getBlock(x + dx, y + dy, z + dz) == BlockType.LEAVES) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
