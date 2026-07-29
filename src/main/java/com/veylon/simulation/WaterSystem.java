package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

import static com.veylon.simulation.WaterConstants.*;

/**
 * Cellular tick-based water: flows down into air, spreads sideways only at or
 * below sea level (self-limiting), refills rain puddles in depressions.
 * Budgeted per tick so it can never stall the game.
 */
public class WaterSystem {

    private final ArrayDeque<Vec3i> queue = new ArrayDeque<>();
    private final Set<Vec3i> scheduled = new HashSet<>();
    private final Random rng = new Random();
    public long cellsProcessed = 0;

    /** Seeded per world so a given world seed replays identically. */
    public void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    public void notifyBlockChanged(World world, int x, int y, int z) {
        // Wake water adjacent to any change (including the changed cell itself).
        scheduleIfWater(world, x, y, z);
        scheduleIfWater(world, x + 1, y, z);
        scheduleIfWater(world, x - 1, y, z);
        scheduleIfWater(world, x, y + 1, z);
        scheduleIfWater(world, x, y, z + 1);
        scheduleIfWater(world, x, y, z - 1);
    }

    private void scheduleIfWater(World world, int x, int y, int z) {
        if (world.getBlock(x, y, z) == BlockType.WATER) {
            Vec3i p = new Vec3i(x, y, z);
            if (scheduled.add(p)) {
                queue.add(p);
            }
        }
    }

    public int activeCount() {
        return queue.size();
    }

    public void reset() {
        queue.clear();
        scheduled.clear();
        cellsProcessed = 0;
    }

    public void mediumTick(Game g, float dt) {
        World world = g.world;
        int processed = 0;
        while (!queue.isEmpty() && processed < BUDGET_PER_TICK) {
            Vec3i p = queue.poll();
            scheduled.remove(p);
            processed++;
            cellsProcessed++;
            if (world.getBlock(p.x(), p.y(), p.z()) != BlockType.WATER) {
                continue;
            }
            BlockType below = world.getBlock(p.x(), p.y() - 1, p.z());
            if (below == BlockType.AIR) {
                // Fall: move the water down.
                world.setBlock(p.x(), p.y(), p.z(), BlockType.AIR, true);
                world.setBlock(p.x(), p.y() - 1, p.z(), BlockType.WATER, true);
                continue;
            }
            // Sideways spread, bounded by sea level so lakes equalize but never flood uphill.
            if (p.y() <= World.SEA_LEVEL) {
                spreadTo(world, p.x() + 1, p.y(), p.z());
                spreadTo(world, p.x() - 1, p.y(), p.z());
                spreadTo(world, p.x(), p.y(), p.z() + 1);
                spreadTo(world, p.x(), p.y(), p.z() - 1);
            }
        }

        // Rain slowly fills shallow depressions and refreshes lakes.
        if (g.weather.isPrecip() && g.weather.effective() != WeatherSystem.Weather.SNOW) {
            for (int i = 0; i < RAIN_FILL_ATTEMPTS; i++) {
                int x = (int) (g.player.pos.x + rng.nextInt(RAIN_FILL_SPAN) - RAIN_FILL_RADIUS);
                int z = (int) (g.player.pos.z + rng.nextInt(RAIN_FILL_SPAN) - RAIN_FILL_RADIUS);
                if (world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) == null) {
                    continue;
                }
                int h = world.surfaceHeight(x, z);
                if (h + 1 <= World.SEA_LEVEL && world.getBlock(x, h + 1, z) == BlockType.AIR
                        && world.getBlock(x, h, z).solid) {
                    world.setBlock(x, h + 1, z, BlockType.WATER, true);
                }
            }
        }
    }

    private void spreadTo(World world, int x, int y, int z) {
        if (world.getBlock(x, y, z) == BlockType.AIR) {
            world.setBlock(x, y, z, BlockType.WATER, true);
        }
    }
}
