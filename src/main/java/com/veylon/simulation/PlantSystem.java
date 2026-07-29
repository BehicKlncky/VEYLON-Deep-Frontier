package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

import java.util.Random;

import static com.veylon.simulation.PlantConstants.*;

/**
 * Slow-tick ecology: soil moisture per chunk, berry regrowth, grass spread,
 * sapling growth and tree seeding. Rain raises moisture, drought lowers it,
 * cold slows everything.
 */
public class PlantSystem {

    private final Random rng = new Random();
    public long growthEvents = 0;
    public float lastAvgMoisture = DEFAULT_MOISTURE;

    public void reset() {
        growthEvents = 0;
        lastAvgMoisture = DEFAULT_MOISTURE;
    }

    /** Deterministic QA hook; normal gameplay retains organic growth sampling. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

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
            delta = RAIN_MOISTURE_PER_SECOND * g.weather.intensity() * dt;
        } else if (g.events.isDrought()) {
            delta = -DROUGHT_MOISTURE_PER_SECOND * dt;
        } else {
            // Drift back toward the biome baseline.
            delta = (biome.moisture - c.moisture) * MOISTURE_DRIFT_RATE * dt;
        }
        c.moisture = Math.max(MIN_MOISTURE, Math.min(1f, c.moisture + delta));
        // Marshes never fully dry out.
        if (biome == Biome.MARSH && c.moisture < MARSH_MIN_MOISTURE) {
            c.moisture = MARSH_MIN_MOISTURE;
        }
    }

    private void growChunk(Game g, Chunk c) {
        World world = g.world;
        float cold = g.player.envTemp < COLD_TEMP ? COLD_GROWTH_MULT : 1f;
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
                if (rng.nextFloat() < BERRY_REGROW_CHANCE * growth * g.events.berryMul()) {
                    world.setBlock(wx, h + 1, wz, BlockType.BERRY_BUSH, true);
                    growthEvents++;
                }
                continue;
            }
            // Saplings grow into trees.
            if (above == BlockType.SAPLING) {
                if (rng.nextFloat() < SAPLING_GROW_CHANCE * growth) {
                    world.generator.growTreeRuntime(wx, h + 1, wz, world.biomeAt(wx, wz));
                    growthEvents++;
                }
                continue;
            }
            // Dirt regrows grass when next to grass.
            if (surface == BlockType.DIRT && above == BlockType.AIR) {
                if (hasNeighborGrass(world, wx, h, wz)
                        && rng.nextFloat() < GRASS_SPREAD_CHANCE * growth) {
                    world.setBlock(wx, h, wz, BlockType.GRASS, true);
                    growthEvents++;
                }
                continue;
            }
            if (surface == BlockType.GRASS && above == BlockType.AIR) {
                float r = rng.nextFloat();
                Biome biome = world.biomeAt(wx, wz);
                // Tall grass and bushes slowly recolonize; herbs sprout in damp biomes.
                // Factor order matches the original expression: float multiplication
                // is not associative, so regrouping these could shift a threshold.
                if (r < TALL_GRASS_CHANCE * growth * biome.plantDensity
                        * PLANT_DENSITY_SCALE) {
                    world.setBlock(wx, h + 1, wz, BlockType.TALL_GRASS, true);
                    growthEvents++;
                } else if (r < HERB_CHANCE * growth * biome.plantDensity * PLANT_DENSITY_SCALE
                        && (biome == Biome.MARSH || biome == Biome.MEADOW)) {
                    world.setBlock(wx, h + 1, wz, BlockType.HERB_PLANT, true);
                    growthEvents++;
                } else if (r > SAPLING_SEED_THRESHOLD - SAPLING_SEED_GROWTH_BAND * growth
                        && nearLeaves(world, wx, h + 1, wz)) {
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
        for (int dx = -LEAF_SCAN_RADIUS; dx <= LEAF_SCAN_RADIUS; dx += LEAF_SCAN_STEP) {
            for (int dz = -LEAF_SCAN_RADIUS; dz <= LEAF_SCAN_RADIUS; dz += LEAF_SCAN_STEP) {
                for (int dy = LEAF_SCAN_MIN_HEIGHT; dy <= LEAF_SCAN_MAX_HEIGHT; dy++) {
                    if (world.getBlock(x + dx, y + dy, z + dz) == BlockType.LEAVES) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
