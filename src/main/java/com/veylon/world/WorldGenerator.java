package com.veylon.world;

import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.util.Noise;
import com.veylon.util.Vec3i;

import java.util.Random;

/**
 * Deterministic chunk generator: heightmap terrain, six biomes, caves, ores,
 * lakes, trees, surface plants and points of interest. Same seed always
 * produces the same world.
 */
public class WorldGenerator {

    private final World world;
    private final long seed;
    private final Noise heightNoise;
    private final Noise mountainNoise;
    private final Noise detailNoise;
    private final Noise tempNoise;
    private final Noise moistNoise;
    private final Noise caveNoise;
    private final Noise decoNoise;

    public WorldGenerator(World world, long seed) {
        this.world = world;
        this.seed = seed;
        heightNoise = new Noise(seed);
        mountainNoise = new Noise(seed + 101);
        detailNoise = new Noise(seed + 202);
        tempNoise = new Noise(seed + 303);
        moistNoise = new Noise(seed + 404);
        caveNoise = new Noise(seed + 505);
        decoNoise = new Noise(seed + 606);
    }

    public double mountainFactor(int x, int z) {
        double m = mountainNoise.fbm2(x * 0.004, z * 0.004, 3, 2.1, 0.5) * 0.5 + 0.5;
        return m;
    }

    public double temperature01(int x, int z) {
        return tempNoise.fbm2(x * 0.0035, z * 0.0035, 3, 2.0, 0.5) * 0.5 + 0.5;
    }

    public double moisture01(int x, int z) {
        return moistNoise.fbm2(x * 0.0042, z * 0.0042, 3, 2.0, 0.5) * 0.5 + 0.5;
    }

    public Biome biomeAt(int x, int z) {
        double m = mountainFactor(x, z);
        double t = temperature01(x, z);
        double mo = moisture01(x, z);
        if (m > 0.62) {
            return t < 0.42 ? Biome.COLD_RIDGE : Biome.ROCKY_HIGHLANDS;
        }
        if (mo > 0.68 && heightAt(x, z) <= World.SEA_LEVEL + 3) {
            return Biome.MARSH;
        }
        if (t < 0.34) {
            return Biome.PINE_FOREST;
        }
        if (t > 0.68 && mo < 0.42) {
            return Biome.SCRUBLAND;
        }
        if (mo > 0.58) {
            return Biome.PINE_FOREST;
        }
        return Biome.MEADOW;
    }

    public int heightAt(int x, int z) {
        double base = heightNoise.fbm2(x * 0.011, z * 0.011, 4, 2.05, 0.5);
        double m = mountainFactor(x, z);
        double mountain = Math.max(0, m - 0.55) / 0.45;
        double detail = detailNoise.value2(x * 0.06, z * 0.06);
        double h = 33 + base * 9 + mountain * mountain * 36 + detail * 2.2;
        return (int) Math.max(4, Math.min(Chunk.SY - 12, h));
    }

    public void generate(Chunk c) {
        int baseX = c.cx * Chunk.SX;
        int baseZ = c.cz * Chunk.SZ;

        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                int wx = baseX + lx, wz = baseZ + lz;
                int h = heightAt(wx, wz);
                Biome biome = biomeAt(wx, wz);

                for (int y = 0; y <= h; y++) {
                    BlockType t;
                    if (y <= 1) {
                        t = BlockType.STONE;
                    } else if (y < h - 3) {
                        t = BlockType.STONE;
                    } else if (y < h) {
                        t = biome.subsurface;
                    } else {
                        t = surfaceBlockFor(biome, wx, wz, h);
                    }
                    c.set(lx, y, lz, t);
                }
                // Lakes and ponds.
                if (h < World.SEA_LEVEL) {
                    for (int y = h + 1; y <= World.SEA_LEVEL; y++) {
                        c.set(lx, y, lz, BlockType.WATER);
                    }
                    // Sandy/clay shores under water.
                    BlockType bed = biome == Biome.MARSH ? BlockType.CLAY : BlockType.SAND;
                    c.set(lx, h, lz, bed);
                    // Ice sheet on cold lakes.
                    if (biome == Biome.COLD_RIDGE) {
                        c.set(lx, World.SEA_LEVEL, lz, BlockType.ICE);
                    }
                }

                carveCaves(c, lx, lz, wx, wz, h);
            }
        }

        placeOres(c);
        decorate(c, baseX, baseZ);
        placePoi(c, baseX, baseZ);

        // Initial soil moisture from the biome at the chunk center.
        c.moisture = biomeAt(baseX + 8, baseZ + 8).moisture;
    }

    private BlockType surfaceBlockFor(Biome biome, int wx, int wz, int h) {
        if (biome == Biome.COLD_RIDGE && h > 58) {
            return BlockType.SNOW;
        }
        if (biome == Biome.ROCKY_HIGHLANDS) {
            return decoNoise.rand2(wx, wz) < 0.25 ? BlockType.GRAVEL : BlockType.STONE;
        }
        if (biome == Biome.MARSH) {
            return decoNoise.rand2(wx, wz) < 0.3 ? BlockType.CLAY : BlockType.GRASS;
        }
        if (h <= World.SEA_LEVEL + 1 && biome != Biome.COLD_RIDGE) {
            return BlockType.SAND;
        }
        return biome.surface;
    }

    private void carveCaves(Chunk c, int lx, int lz, int wx, int wz, int h) {
        // Don't carve directly beneath lakes so they don't instantly drain.
        int top = h < World.SEA_LEVEL + 2 ? h - 8 : h - 4;
        for (int y = 3; y <= top; y++) {
            double n = caveNoise.fbm3(wx * 0.065, y * 0.095, wz * 0.065, 2, 2.0, 0.5);
            if (n > 0.40) {
                c.set(lx, y, lz, BlockType.AIR);
            }
        }
    }

    private void placeOres(Chunk c) {
        Random rng = new Random(Noise.mix(seed ^ World.key(c.cx, c.cz)));
        placeVeins(c, rng, BlockType.COAL_ORE, 7, 6, 52, 5);
        placeVeins(c, rng, BlockType.COPPER_ORE, 5, 5, 42, 4);
        placeVeins(c, rng, BlockType.IRON_ORE, 4, 4, 28, 4);
    }

    private void placeVeins(Chunk c, Random rng, BlockType ore, int veins, int minY, int maxY, int size) {
        for (int v = 0; v < veins; v++) {
            int x = rng.nextInt(Chunk.SX);
            int z = rng.nextInt(Chunk.SZ);
            int y = minY + rng.nextInt(Math.max(1, maxY - minY));
            int n = 2 + rng.nextInt(size);
            for (int i = 0; i < n; i++) {
                int bx = x + rng.nextInt(3) - 1;
                int by = y + rng.nextInt(3) - 1;
                int bz = z + rng.nextInt(3) - 1;
                if (bx >= 0 && bx < Chunk.SX && bz >= 0 && bz < Chunk.SZ && by > 1 && by < Chunk.SY
                        && c.get(bx, by, bz) == BlockType.STONE) {
                    c.set(bx, by, bz, ore);
                }
            }
        }
    }

    private void decorate(Chunk c, int baseX, int baseZ) {
        Random rng = new Random(Noise.mix((seed + 77) ^ World.key(c.cx, c.cz)));
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                int wx = baseX + lx, wz = baseZ + lz;
                int h = heightAt(wx, wz);
                if (h < World.SEA_LEVEL || h >= Chunk.SY - 12) {
                    continue;
                }
                BlockType surf = c.get(lx, h, lz);
                if (surf != BlockType.GRASS && surf != BlockType.DIRT && surf != BlockType.SAND
                        && surf != BlockType.SNOW && surf != BlockType.STONE && surf != BlockType.CLAY) {
                    continue;
                }
                Biome biome = biomeAt(wx, wz);
                double roll = rng.nextDouble();
                if (roll < biome.treeDensity && surf != BlockType.SAND && surf != BlockType.STONE) {
                    placeTree(rng, wx, h + 1, wz, biome);
                } else if (roll < biome.treeDensity + biome.plantDensity * 0.25) {
                    world.genSet(wx, h + 1, wz, BlockType.BERRY_BUSH, true);
                } else if (roll < biome.treeDensity + biome.plantDensity * 0.40) {
                    // Medicinal herbs favor wet ground.
                    BlockType plant = (biome == Biome.MARSH || biome == Biome.MEADOW
                            || biome == Biome.PINE_FOREST) ? BlockType.HERB_PLANT : BlockType.BUSH;
                    world.genSet(wx, h + 1, wz, plant, true);
                } else if (roll < biome.treeDensity + biome.plantDensity * 0.62) {
                    world.genSet(wx, h + 1, wz, BlockType.BUSH, true);
                } else if (roll < biome.treeDensity + biome.plantDensity) {
                    world.genSet(wx, h + 1, wz, BlockType.TALL_GRASS, true);
                } else if (roll > 0.997) {
                    // Surface boulder.
                    world.genSet(wx, h + 1, wz, BlockType.STONE, true);
                }
            }
        }
    }

    /**
     * Rolls one potential point of interest per chunk (deterministic). POIs are
     * registered with the world so discovery and map markers work after reload.
     */
    private void placePoi(Chunk c, int baseX, int baseZ) {
        Random rng = new Random(Noise.mix((seed + 991) ^ World.key(c.cx, c.cz)));
        double roll = rng.nextDouble();
        Poi.PoiType type;
        if (roll < 0.010) {
            type = Poi.PoiType.CRASH_DEBRIS;
        } else if (roll < 0.017) {
            type = Poi.PoiType.RESEARCH_POD;
        } else if (roll < 0.024) {
            type = Poi.PoiType.ANCIENT_RUIN;
        } else if (roll < 0.034) {
            type = Poi.PoiType.PREDATOR_DEN;
        } else if (roll < 0.042) {
            type = Poi.PoiType.SUPPLY_CACHE;
        } else {
            return;
        }
        int x = baseX + 4 + rng.nextInt(8);
        int z = baseZ + 4 + rng.nextInt(8);
        int h = heightAt(x, z);
        if (h <= World.SEA_LEVEL + 1 || h >= Chunk.SY - 16) {
            return;
        }
        Vec3i pos = new Vec3i(x, h + 1, z);
        switch (type) {
            case CRASH_DEBRIS -> buildCrashDebris(rng, x, h, z);
            case RESEARCH_POD -> buildResearchPod(rng, x, h, z);
            case ANCIENT_RUIN -> buildAncientRuin(rng, x, h, z);
            case PREDATOR_DEN -> buildPredatorDen(rng, x, h, z);
            case SUPPLY_CACHE -> buildSupplyCache(rng, x, h, z);
        }
        world.registerPoi(new Poi(type, pos));
    }

    private void buildCrashDebris(Random rng, int x, int h, int z) {
        int pieces = 5 + rng.nextInt(5);
        for (int i = 0; i < pieces; i++) {
            int dx = rng.nextInt(7) - 3, dz = rng.nextInt(7) - 3;
            int gy = heightAt(x + dx, z + dz);
            world.genSet(x + dx, gy + 1, z + dz,
                    rng.nextFloat() < 0.3f ? BlockType.ASH : BlockType.SCRAP_BLOCK, true);
        }
        world.genSet(x, h + 1, z, BlockType.CRATE, false);
        Inventory loot = new Inventory(12);
        loot.add(ItemType.SCRAP, 2 + rng.nextInt(3));
        loot.add(ItemType.FIBER, 2 + rng.nextInt(3));
        if (rng.nextFloat() < 0.5f) {
            loot.add(ItemType.BANDAGE, 1 + rng.nextInt(2));
        }
        if (rng.nextFloat() < 0.45f) {
            loot.add(ItemType.BLUEPRINT_FRAGMENT, 1);
        }
        world.crateContents.putIfAbsent(new Vec3i(x, h + 1, z), loot);
    }

    private void buildResearchPod(Random rng, int x, int h, int z) {
        // Hollow hull shell with a doorway facing +X.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 3; dy++) {
                    boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2 || dy == 3;
                    boolean door = dx == 2 && dz == 0 && dy <= 1;
                    if (edge && !door && Math.abs(dx) + Math.abs(dz) <= 3) {
                        world.genSet(x + dx, h + 1 + dy, z + dz, BlockType.POD_HULL, false);
                    }
                }
            }
        }
        world.genSet(x, h + 1, z, BlockType.CRATE, false);
        world.genSet(x - 1, h + 1, z - 1, BlockType.TORCH, true);
        Inventory loot = new Inventory(12);
        loot.add(ItemType.BLUEPRINT_FRAGMENT, 1 + rng.nextInt(2));
        loot.add(ItemType.MEDICINE, rng.nextFloat() < 0.6f ? 1 : 2);
        loot.add(ItemType.ANTISEPTIC, 1 + rng.nextInt(2));
        if (rng.nextFloat() < 0.4f) {
            loot.add(ItemType.SCRAP, 2);
        }
        world.crateContents.putIfAbsent(new Vec3i(x, h + 1, z), loot);
    }

    private void buildAncientRuin(Random rng, int x, int h, int z) {
        // Four weathered pillars around a glowing resonant core.
        int[][] corners = {{-3, -3}, {3, -3}, {-3, 3}, {3, 3}};
        for (int[] cnr : corners) {
            int height = 2 + rng.nextInt(3);
            int gy = heightAt(x + cnr[0], z + cnr[1]);
            for (int dy = 1; dy <= height; dy++) {
                world.genSet(x + cnr[0], gy + dy, z + cnr[1], BlockType.RUIN_STONE, false);
            }
        }
        world.genSet(x, h + 1, z, BlockType.RUIN_STONE, false);
        world.genSet(x, h + 2, z, BlockType.RUIN_CORE, false);
        world.genSet(x + 1, h + 1, z + 1, BlockType.BONE_PILE, true);
        if (rng.nextFloat() < 0.5f) {
            world.genSet(x - 1, h + 1, z + 2, BlockType.BONE_PILE, true);
        }
    }

    private void buildPredatorDen(Random rng, int x, int h, int z) {
        // A scratched-out hollow with bone piles; wolves favor this spot.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (dx * dx + dz * dz <= 4) {
                    int gy = heightAt(x + dx, z + dz);
                    world.genSet(x + dx, gy, z + dz, BlockType.DIRT, false);
                }
            }
        }
        world.genSet(x, h + 1, z, BlockType.BONE_PILE, false);
        world.genSet(x + 1 + rng.nextInt(2), h + 1, z - 1, BlockType.BONE_PILE, true);
        world.genSet(x - 1, h + 1, z + 1 + rng.nextInt(2), BlockType.BONE_PILE, true);
    }

    private void buildSupplyCache(Random rng, int x, int h, int z) {
        // A lost traveler's last camp.
        world.genSet(x, h + 1, z, BlockType.CRATE, false);
        world.genSet(x + 1, h + 1, z, BlockType.BONE_PILE, true);
        world.genSet(x - 1, h + 1, z + 1, BlockType.ASH, true);
        Inventory loot = new Inventory(12);
        loot.add(ItemType.DRIED_MEAT, 1 + rng.nextInt(3));
        loot.add(ItemType.WATERSKIN_CLEAN, 1);
        loot.add(ItemType.BANDAGE, 1 + rng.nextInt(2));
        if (rng.nextFloat() < 0.35f) {
            loot.add(ItemType.BLUEPRINT_FRAGMENT, 1);
        }
        if (rng.nextFloat() < 0.3f) {
            loot.add(ItemType.BONE_KNIFE, 1);
        }
        world.crateContents.putIfAbsent(new Vec3i(x, h + 1, z), loot);
    }

    void placeTree(Random rng, int x, int y, int z, Biome biome) {
        boolean pine = biome == Biome.PINE_FOREST || biome == Biome.COLD_RIDGE;
        int trunk = pine ? 5 + rng.nextInt(3) : 4 + rng.nextInt(2);
        for (int i = 0; i < trunk; i++) {
            world.genSet(x, y + i, z, BlockType.LOG, false);
        }
        if (pine) {
            // Cone canopy.
            int top = y + trunk;
            for (int layer = 0; layer < 4; layer++) {
                int r = Math.max(0, 3 - layer) / 2 + (layer < 2 ? 1 : 0);
                int ly = top - 3 + layer;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.abs(dx) + Math.abs(dz) <= r + (layer % 2)) {
                            world.genSet(x + dx, ly + 2, z + dz, BlockType.LEAVES, true);
                        }
                    }
                }
            }
            world.genSet(x, top + 2, z, BlockType.LEAVES, true);
        } else {
            // Round canopy.
            int cy = y + trunk;
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        int man = Math.abs(dx) + Math.abs(dy) + Math.abs(dz);
                        if (man <= 3 && !(dx == 0 && dz == 0 && dy <= 0)) {
                            world.genSet(x + dx, cy + dy, z + dz, BlockType.LEAVES, true);
                        }
                    }
                }
            }
            world.genSet(x, cy + 1, z, BlockType.LEAVES, true);
        }
    }

    /** Grows a sapling into a small tree at runtime (records changes for saving). */
    public void growTreeRuntime(int x, int y, int z, Biome biome) {
        Random rng = new Random(Noise.mix(seed ^ (x * 341873128712L + z * 132897987541L + y)));
        boolean pine = biome == Biome.PINE_FOREST || biome == Biome.COLD_RIDGE;
        int trunk = pine ? 5 : 4;
        for (int i = 0; i < trunk; i++) {
            world.setBlock(x, y + i, z, BlockType.LOG, true);
        }
        int cy = y + trunk;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int dy = 0; dy <= 1; dy++) {
                    if (world.getBlock(x + dx, cy + dy, z + dz).isAir()) {
                        world.setBlock(x + dx, cy + dy, z + dz, BlockType.LEAVES, true);
                    }
                }
            }
        }
        if (rng.nextBoolean() && world.getBlock(x, cy + 2, z).isAir()) {
            world.setBlock(x, cy + 2, z, BlockType.LEAVES, true);
        }
    }
}
