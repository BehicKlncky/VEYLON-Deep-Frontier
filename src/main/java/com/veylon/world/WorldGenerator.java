package com.veylon.world;

import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.util.Noise;
import com.veylon.util.Vec3i;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Deterministic chunk generator: heightmap terrain, six biomes, caves, ores,
 * lakes, trees, surface plants and points of interest. Same seed always
 * produces the same world.
 */
public class WorldGenerator {

    /** Generator-2 compatibility boundary; generator 3 derives zones from depth. */
    public static final int BASALT_TOP = 22;
    /** Generator-2 compatibility boundary for its absolute resonant band. */
    public static final int RESONANT_TOP = 12;
    /** Depth below the local surface where root caves end. */
    public static final int ROOT_DEPTH = 14;
    /** Depth below local surface where the rare Resonant band begins. */
    public static final int RESONANT_DEPTH = 28;
    /** Minimum open height of the deliberately carved Basalt-depth shafts. */
    public static final int BASALT_SHAFT_HEIGHT = 7;
    /**
     * How near zero both worm-tunnel fields must be for a cell to be carved.
     * Widening this thickens every tunnel in every world, so it is part of the
     * generator contract, not a tuning knob.
     */
    private static final double TUNNEL_THRESHOLD = 0.065;

    private final World world;
    private final long seed;
    private final Noise heightNoise;
    private final Noise mountainNoise;
    private final Noise detailNoise;
    private final Noise tempNoise;
    private final Noise moistNoise;
    private final Noise caveNoise;
    private final Noise decoNoise;
    // Deep-frontier cave fields: two ridged tunnel fields + domain warp + chambers.
    private final Noise tunnelNoiseA;
    private final Noise tunnelNoiseB;
    private final Noise warpNoise;
    private final Noise chamberNoise;

    /**
     * Memos for the three pure per-column queries.
     *
     * <p>{@code heightAt}, {@code mountainFactor} and {@code biomeAt} depend on
     * nothing but the seed and the column, and they are asked for the same
     * column two to four times over a single chunk generation:
     * {@code generate} calls both, {@code biomeAt} recomputes
     * {@code mountainFactor} and may call {@code heightAt} again,
     * {@code decorate} repeats both for all 256 columns,
     * {@code decorateCaveIdentities} repeats {@code heightAt} for 144 of them,
     * and {@code placePoi} and the settlement queries repeat it again from
     * outside the loop. Each of those is a multi-octave fBm.
     *
     * <p>These return the identical {@code double}/{@code int}/{@code Biome},
     * so downstream arithmetic is bit-identical; a collision recomputes and is
     * still exact. Held per {@link WorldGenerator}, which is per {@link World},
     * so nothing is shared between worlds.
     */
    private final ColumnMemo.OfInt heightMemo = new ColumnMemo.OfInt();
    private final ColumnMemo.OfDouble mountainMemo = new ColumnMemo.OfDouble();
    private final ColumnMemo.OfBiome biomeMemo = new ColumnMemo.OfBiome();

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
        tunnelNoiseA = new Noise(seed + 707);
        tunnelNoiseB = new Noise(seed + 808);
        warpNoise = new Noise(seed + 909);
        chamberNoise = new Noise(seed + 1010);
    }

    private boolean deep() {
        return world.generatorVersion >= World.GEN_DEEP;
    }

    private boolean caveIdentities() {
        return world.generatorVersion >= World.GEN_CAVE_IDENTITIES;
    }

    /** Derived terrain identity; names are stable but the enum is never serialized. */
    public enum CaveZone {
        SURFACE,
        ROOT,
        BASALT,
        RESONANT
    }

    public CaveZone caveZoneAt(int x, int y, int z) {
        return caveZone(heightAt(x, z), y);
    }

    private static CaveZone caveZone(int surface, int y) {
        int depth = surface - y;
        if (depth < 5) {
            return CaveZone.SURFACE;
        }
        if (depth < ROOT_DEPTH) {
            return CaveZone.ROOT;
        }
        if (depth < RESONANT_DEPTH) {
            return CaveZone.BASALT;
        }
        return CaveZone.RESONANT;
    }

    public double mountainFactor(int x, int z) {
        int slot = mountainMemo.slot(x, z);
        if (mountainMemo.holds(slot, x, z)) {
            return mountainMemo.value(slot);
        }
        double m = mountainNoise.fbm2(x * 0.004, z * 0.004, 3, 2.1, 0.5) * 0.5 + 0.5;
        return mountainMemo.store(slot, x, z, m);
    }

    public double temperature01(int x, int z) {
        return tempNoise.fbm2(x * 0.0035, z * 0.0035, 3, 2.0, 0.5) * 0.5 + 0.5;
    }

    public double moisture01(int x, int z) {
        return moistNoise.fbm2(x * 0.0042, z * 0.0042, 3, 2.0, 0.5) * 0.5 + 0.5;
    }

    public Biome biomeAt(int x, int z) {
        int slot = biomeMemo.slot(x, z);
        if (biomeMemo.holds(slot, x, z)) {
            return biomeMemo.value(slot);
        }
        return biomeMemo.store(slot, x, z, computeBiomeAt(x, z));
    }

    private Biome computeBiomeAt(int x, int z) {
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
        int slot = heightMemo.slot(x, z);
        if (heightMemo.holds(slot, x, z)) {
            return heightMemo.value(slot);
        }
        double base = heightNoise.fbm2(x * 0.011, z * 0.011, 4, 2.05, 0.5);
        double m = mountainFactor(x, z);
        double mountain = Math.max(0, m - 0.55) / 0.45;
        double detail = detailNoise.value2(x * 0.06, z * 0.06);
        double h = 33 + base * 9 + mountain * mountain * 36 + detail * 2.2;
        return heightMemo.store(slot, x, z, (int) Math.max(4, Math.min(Chunk.SY - 12, h)));
    }

    public void generate(Chunk c) {
        int baseX = c.cx * Chunk.SX;
        int baseZ = c.cz * Chunk.SZ;
        boolean deep = deep();
        boolean identities = caveIdentities();

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
                        // Deep-frontier worlds have a dark basalt identity below.
                        CaveZone zone = identities ? caveZone(h, y) : null;
                        boolean basaltHost = deep && (identities
                                ? zone == CaveZone.BASALT || zone == CaveZone.RESONANT
                                : y < BASALT_TOP);
                        t = basaltHost ? BlockType.BASALT : BlockType.STONE;
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

                if (deep) {
                    carveCavesDeep(c, lx, lz, wx, wz, h);
                } else {
                    carveCaves(c, lx, lz, wx, wz, h);
                }
            }
        }

        placeOres(c);
        if (identities) {
            decorateCaveIdentities(c, baseX, baseZ);
        }
        decorate(c, baseX, baseZ);
        placePoi(c, baseX, baseZ);

        if (deep) {
            placeUndergroundPoiSlices(c, baseX, baseZ);
            carveCaveEntranceSlice(c, baseX, baseZ);
            applySettlementSlice(c, baseX, baseZ);
        }

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

    /**
     * Deep-frontier cave column: three depth identities (root caves, basalt
     * depths, resonant depths), domain-warped worm tunnels for connectivity,
     * seeded chambers, and sparse cave life. Lake protection matches legacy.
     */
    private void carveCavesDeep(Chunk c, int lx, int lz, int wx, int wz, int h) {
        int top = h < World.SEA_LEVEL + 2 ? h - 8 : h - 4;
        boolean identities = caveIdentities();
        boolean carvedBelow = false;
        for (int y = 3; y <= top; y++) {
            int depth = h - y;
            boolean carve = false;
            CaveZone zone = identities ? caveZone(h, y) : null;

            // Density chambers: threshold loosens with depth so the basalt
            // layer opens into larger rooms; the resonant band is rare but big.
            double n = caveNoise.fbm3(wx * 0.065, y * 0.095, wz * 0.065, 2, 2.0, 0.5);
            if (depth >= 4) {
                double threshold;
                if (identities) {
                    threshold = switch (zone) {
                        case SURFACE, ROOT -> 0.42;
                        case BASALT -> 0.40;
                        case RESONANT -> 0.355;
                    };
                } else {
                    if (depth < ROOT_DEPTH) {
                        threshold = 0.42;
                    } else if (y >= BASALT_TOP) {
                        threshold = 0.40;
                    } else {
                        threshold = 0.355;
                    }
                }
                carve = n > threshold;
            }

            // Domain-warped worm tunnels: the intersection of two ridged fields
            // traces long connected passages with natural vertical wander.
            //
            // The second field is evaluated inside the condition, not before
            // it. A cell is a tunnel only where both fields are near zero, and
            // the first rejects the large majority — so computing t2 eagerly
            // spent a full two-octave 3D fbm (sixteen hashed lattice samples)
            // on cells whose fate was already decided. This is the hottest
            // arithmetic in the codebase: a load regenerates every chunk from
            // the seed, which is why loading costs 500x what saving does.
            // Same values, same order, same terrain.
            if (!carve && depth >= 5) {
                double warp = warpNoise.fbm3(wx * 0.012, y * 0.02, wz * 0.012, 2, 2.0, 0.5) * 14.0;
                double t1 = tunnelNoiseA.fbm3((wx + warp) * 0.021, y * 0.045, wz * 0.021, 2, 2.0, 0.5);
                carve = Math.abs(t1) < TUNNEL_THRESHOLD
                        && Math.abs(tunnelNoiseB.fbm3(wx * 0.021, y * 0.045,
                                (wz - warp) * 0.021, 2, 2.0, 0.5)) < TUNNEL_THRESHOLD;
            }

            // Resonant depths: rare crystal chambers at the very bottom.
            boolean resonant = false;
            if (identities ? zone == CaveZone.RESONANT : y < RESONANT_TOP) {
                double r = chamberNoise.fbm3(wx * 0.030, y * 0.040, wz * 0.030, 2, 2.0, 0.5);
                if (r > 0.52) {
                    carve = true;
                    resonant = true;
                }
            }

            if (carve) {
                c.set(lx, y, lz, BlockType.AIR);
                // Floor dressing when we just opened air above solid ground.
                if (!carvedBelow && y > 3) {
                    double deco = decoNoise.rand2(wx * 7 + y, wz * 7 - y);
                    if (resonant && deco < 0.030) {
                        c.set(lx, y - 1, lz, BlockType.RUIN_CORE);
                    } else if (resonant && deco < 0.10) {
                        c.set(lx, y, lz, BlockType.GLOW_FUNGUS);
                    } else if ((identities ? zone == CaveZone.BASALT : depth > 10)
                            && deco < 0.018) {
                        c.set(lx, y, lz, BlockType.GLOW_FUNGUS);
                    }
                }
                carvedBelow = true;
            } else {
                carvedBelow = false;
            }
        }
    }

    private record CaveFloor(int lx, int y, int lz, int depth) {
    }

    /**
     * Gives the three depth bands gameplay-readable identities after the base
     * density/worm pass. The plan is local to one chunk and derived only from
     * seed/chunk coordinates, so it is independent of neighbour generation
     * order and never creates pending cross-chunk edits.
     */
    private void decorateCaveIdentities(Chunk c, int baseX, int baseZ) {
        List<CaveFloor> rootFloors = new ArrayList<>();
        List<CaveFloor> basaltFloors = new ArrayList<>();
        for (int lx = 2; lx < Chunk.SX - 2; lx++) {
            for (int lz = 2; lz < Chunk.SZ - 2; lz++) {
                int h = heightAt(baseX + lx, baseZ + lz);
                int top = Math.min(h - 4, Chunk.SY - 3);
                for (int y = 4; y <= top; y++) {
                    if (c.get(lx, y, lz) != BlockType.AIR
                            || c.get(lx, y + 1, lz) != BlockType.AIR
                            || !naturalCaveFloor(c.get(lx, y - 1, lz))) {
                        continue;
                    }
                    int depth = h - y;
                    CaveZone zone = caveZone(h, y);
                    if (zone == CaveZone.ROOT) {
                        rootFloors.add(new CaveFloor(lx, y, lz, depth));
                    } else if (zone == CaveZone.BASALT) {
                        basaltFloors.add(new CaveFloor(lx, y, lz, depth));
                    }
                }
            }
        }

        long chunkHash = Noise.mix(seed ^ World.key(c.cx, c.cz)
                ^ 0x434156454944454eL);
        if (!rootFloors.isEmpty()) {
            CaveFloor root = select(rootFloors, chunkHash ^ 0x524f4f54534cL);
            placeRootIdentity(c, baseX, baseZ, root, chunkHash);
        }
        if (!basaltFloors.isEmpty()) {
            if (Math.floorMod(chunkHash, 3) == 0) {
                CaveFloor vent = select(basaltFloors, chunkHash ^ 0x46554d41524f4cL);
                placeBasaltFumarole(c, vent);
            }
            if (Math.floorMod(chunkHash >>> 7, 4) == 0) {
                List<CaveFloor> shaftFloors = basaltFloors.stream()
                        .filter(f -> f.lx <= Chunk.SX - 4 && f.lz <= Chunk.SZ - 4
                                && f.depth >= ROOT_DEPTH + BASALT_SHAFT_HEIGHT - 1)
                        .toList();
                if (!shaftFloors.isEmpty()) {
                    CaveFloor shaft = select(shaftFloors,
                            chunkHash ^ 0x53484146544cL);
                    carveBasaltShaft(c, shaft);
                }
            }
        }
    }

    private void placeRootIdentity(Chunk c, int baseX, int baseZ,
                                   CaveFloor floor, long chunkHash) {
        int[][] dirs = ((chunkHash >>> 11) & 1L) == 0
                ? new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}
                : new int[][]{{0, 1}, {0, -1}, {1, 0}, {-1, 0}};

        // An exposed root crosses a damp dirt intrusion. Side pockets are
        // deliberately only one cell deep, keeping the carve bounded.
        c.set(floor.lx, floor.y - 1, floor.lz, BlockType.LOG);
        if (naturalCaveFloor(c.get(floor.lx, floor.y + 2, floor.lz))) {
            c.set(floor.lx, floor.y + 2, floor.lz, BlockType.LOG);
        }
        for (int i = 0; i < 2; i++) {
            int lx = floor.lx + dirs[i][0], lz = floor.lz + dirs[i][1];
            if (i == 0) {
                placeContainedRootSeep(c, lx, floor.y - 1, lz);
            } else {
                c.set(lx, floor.y - 1, lz, BlockType.DIRT);
            }
            c.set(lx, floor.y, lz, BlockType.AIR);
            c.set(lx, floor.y + 1, lz, BlockType.AIR);
        }

        // A bounded fraction of root-cave chunks contain a real, discoverable
        // animal den. It reuses the historical stable PREDATOR_DEN identity.
        if (Math.floorMod(chunkHash >>> 17, 8) == 0) {
            int lx = floor.lx + dirs[2][0], lz = floor.lz + dirs[2][1];
            c.set(lx, floor.y - 1, lz, BlockType.DIRT);
            c.set(lx, floor.y, lz, BlockType.BONE_PILE);
            c.set(lx, floor.y + 1, lz, BlockType.AIR);
            int lx2 = floor.lx + dirs[3][0], lz2 = floor.lz + dirs[3][1];
            c.set(lx2, floor.y - 1, lz2, BlockType.DIRT);
            c.set(lx2, floor.y, lz2, BlockType.BONE_PILE);
            c.set(lx2, floor.y + 1, lz2, BlockType.AIR);
            world.registerPoi(new Poi(Poi.PoiType.PREDATOR_DEN,
                    new Vec3i(baseX + lx, floor.y, baseZ + lz)));
        }
    }

    private static void placeContainedRootSeep(Chunk c, int lx, int y, int lz) {
        c.set(lx, y - 1, lz, BlockType.DIRT);
        c.set(lx, y, lz, BlockType.WATER);
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int nx = lx + dir[0], nz = lz + dir[1];
            if (c.get(nx, y, nz) != BlockType.LOG) {
                c.set(nx, y, nz, BlockType.DIRT);
            }
        }
    }

    private static void placeBasaltFumarole(Chunk c, CaveFloor floor) {
        c.set(floor.lx, floor.y - 1, floor.lz, BlockType.SULFUR_ORE);
        c.set(floor.lx, floor.y, floor.lz, BlockType.AIR);
        c.set(floor.lx, floor.y + 1, floor.lz, BlockType.AIR);
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int lx = floor.lx + dir[0], lz = floor.lz + dir[1];
            c.set(lx, floor.y - 1, lz, BlockType.ASH);
            c.set(lx, floor.y, lz, BlockType.AIR);
            c.set(lx, floor.y + 1, lz, BlockType.AIR);
        }
    }

    private static void carveBasaltShaft(Chunk c, CaveFloor floor) {
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                c.set(floor.lx + dx, floor.y - 1, floor.lz + dz,
                        BlockType.BASALT);
                for (int dy = 0; dy < BASALT_SHAFT_HEIGHT; dy++) {
                    c.set(floor.lx + dx, floor.y + dy, floor.lz + dz,
                            BlockType.AIR);
                }
            }
        }
    }

    private static boolean naturalCaveFloor(BlockType type) {
        return type == BlockType.STONE || type == BlockType.BASALT
                || type == BlockType.DIRT || type == BlockType.GRAVEL
                || type == BlockType.CLAY || type == BlockType.SAND;
    }

    private static CaveFloor select(List<CaveFloor> floors, long mixed) {
        return floors.get(Math.floorMod((int) Noise.mix(mixed), floors.size()));
    }

    private void placeOres(Chunk c) {
        Random rng = new Random(Noise.mix(seed ^ World.key(c.cx, c.cz)));
        placeVeins(c, rng, BlockType.COAL_ORE, 7, 6, 52, 5);
        placeVeins(c, rng, BlockType.COPPER_ORE, 5, 5, 42, 4);
        placeVeins(c, rng, BlockType.IRON_ORE, 4, 4, 28, 4);
        if (deep()) {
            // Black-powder progression lives in the basalt depths.
            placeVeins(c, rng, BlockType.SULFUR_ORE, 4, 4, BASALT_TOP, 4);
            placeVeins(c, rng, BlockType.SALTPETER_ORE, 4, 6, 30, 4);
            // Extra iron rewards deep expeditions.
            placeVeins(c, rng, BlockType.IRON_ORE, 3, 3, 18, 5);
        }
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
                if (bx >= 0 && bx < Chunk.SX && bz >= 0 && bz < Chunk.SZ && by > 1 && by < Chunk.SY) {
                    BlockType host = c.get(bx, by, bz);
                    if (host == BlockType.STONE || host == BlockType.BASALT) {
                        c.set(bx, by, bz, ore);
                    }
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
                // Keep settlement footprints clear of trees and brush.
                if (deep() && world.settlementAt(wx, wz) != null) {
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
        // Settlements reserve their bounds; POIs never overlap them.
        if (deep() && world.settlementAt(x, z) != null) {
            return;
        }
        if (deep()) {
            int rx = com.veylon.settlement.SettlementPlanner.regionOfBlock(x);
            int rz = com.veylon.settlement.SettlementPlanner.regionOfBlock(z);
            var settlement = world.settlementForRegion(rx, rz);
            // Reserve complete footprints, not only POI center points.
            if (settlement != null
                    && Math.abs(x - settlement.center.x()) <= settlement.radius + 8
                    && Math.abs(z - settlement.center.z()) <= settlement.radius + 8) {
                return;
            }
            Vec3i mouth = com.veylon.settlement.SettlementPlanner.caveEntrance(
                    seed, this, rx, rz);
            if (Math.abs(x - mouth.x()) <= com.veylon.settlement.SettlementPlanner.CAVE_RESERVATION_RADIUS
                    && Math.abs(z - mouth.z()) <= com.veylon.settlement.SettlementPlanner.CAVE_RESERVATION_RADIUS) {
                return;
            }
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
        // Expedition-issue relic gear: very rare, loot-only (deep worlds).
        if (deep()) {
            if (rng.nextFloat() < 0.05f) {
                loot.add(ItemType.RELIC_CARBINE, 1);
            }
            if (rng.nextFloat() < 0.25f) {
                loot.add(ItemType.RIFLE_CARTRIDGE, 4 + rng.nextInt(6));
            }
            if (rng.nextFloat() < 0.15f) {
                loot.add(ItemType.RELIC_PARTS, 1);
            }
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

    // ------------------------------------------------------------------
    // Deep-frontier additions (generator version 2+)
    // ------------------------------------------------------------------

    /** Pure per-chunk POI plan, later sliced into every intersecting chunk. */
    private record UndergroundPoiPlan(Poi.PoiType type, int x, int y, int z, long lootSeed) {
    }

    /** Maximum carved cells for one connector (two-high tunnel + floor + ladder). */
    public static final int MAX_POI_CONNECTOR_EDITS =
            com.veylon.settlement.SettlementPlanner.REGION_BLOCKS * 7;

    private UndergroundPoiPlan undergroundPoiPlan(int cx, int cz,
                                                   com.veylon.settlement.Settlement settlement) {
        Random rng = new Random(Noise.mix((seed + 1991) ^ World.key(cx, cz)));
        double roll = rng.nextDouble();
        Poi.PoiType type;
        int y;
        if (roll < 0.008) {
            type = Poi.PoiType.ABANDONED_MINE;
            y = 18 + rng.nextInt(14);
        } else if (roll < 0.014) {
            type = Poi.PoiType.SMUGGLER_CACHE;
            y = 16 + rng.nextInt(16);
        } else if (roll < 0.019) {
            type = Poi.PoiType.HIDEOUT_CAVE;
            y = 20 + rng.nextInt(12);
        } else if (roll < 0.023) {
            type = Poi.PoiType.RESONANT_SHRINE;
            y = caveIdentities() ? -1 : 6 + rng.nextInt(5);
        } else if (roll < 0.030) {
            type = Poi.PoiType.STALKER_NEST;
            // Stronger predators belong to the Basalt Depths, not the safer
            // near-surface Root Caves or rare Resonant band.
            y = caveIdentities() ? -1 : 10 + rng.nextInt(14);
        } else if (roll < 0.036) {
            type = Poi.PoiType.EXPEDITION_CAMP;
            y = 14 + rng.nextInt(16);
        } else {
            return null;
        }
        int x = cx * Chunk.SX + 5 + rng.nextInt(6);
        int z = cz * Chunk.SZ + 5 + rng.nextInt(6);
        int surface = heightAt(x, z);
        if (caveIdentities() && type == Poi.PoiType.STALKER_NEST) {
            int depthSpan = RESONANT_DEPTH - ROOT_DEPTH - 4;
            y = surface - (ROOT_DEPTH + 2 + rng.nextInt(depthSpan));
        } else if (caveIdentities() && type == Poi.PoiType.RESONANT_SHRINE) {
            y = surface - (RESONANT_DEPTH + 2 + rng.nextInt(6));
        }
        if (surface <= World.SEA_LEVEL + 1 || y < 4 || y > surface - 12) {
            return null;
        }
        if (settlement != null
                && Math.abs(x - settlement.center.x()) <= settlement.radius + 4
                && Math.abs(z - settlement.center.z()) <= settlement.radius + 4) {
            return null;
        }
        return new UndergroundPoiPlan(type, x, y, z,
                Noise.mix(seed ^ World.key(cx, cz) ^ 0x504f494cL));
    }

    private List<UndergroundPoiPlan> undergroundPoiPlansForRegion(int rx, int rz) {
        List<UndergroundPoiPlan> plans = new ArrayList<>();
        var settlement = world.settlementForRegion(rx, rz);
        int startCx = rx * com.veylon.settlement.SettlementPlanner.REGION_CHUNKS;
        int startCz = rz * com.veylon.settlement.SettlementPlanner.REGION_CHUNKS;
        for (int cx = startCx;
             cx < startCx + com.veylon.settlement.SettlementPlanner.REGION_CHUNKS; cx++) {
            for (int cz = startCz;
                 cz < startCz + com.veylon.settlement.SettlementPlanner.REGION_CHUNKS; cz++) {
                UndergroundPoiPlan plan = undergroundPoiPlan(cx, cz, settlement);
                if (plan != null) {
                    plans.add(plan);
                }
            }
        }
        return plans;
    }

    /** Package-visible deterministic QA view; does not generate or register chunks. */
    List<Poi> plannedUndergroundPois(int rx, int rz) {
        List<Poi> out = new ArrayList<>();
        for (UndergroundPoiPlan plan : undergroundPoiPlansForRegion(rx, rz)) {
            out.add(new Poi(plan.type, new Vec3i(plan.x, plan.y, plan.z)));
        }
        return out;
    }

    static int connectorEditUpperBound(Vec3i mouth, Vec3i poi) {
        int horizontal = Math.abs(mouth.x() - (poi.x() - 2))
                + Math.abs(mouth.z() - poi.z()) + 2;
        int vertical = Math.abs(com.veylon.settlement.SettlementPlanner.CAVE_TARGET_Y
                - poi.y()) + 1;
        return horizontal * 3 + vertical;
    }

    /**
     * Every underground POI chamber and connector is a pure regional plan.
     * Each generated chunk applies only its own slice, so reversed generation
     * order cannot duplicate or truncate cross-chunk tunnels.
     */
    private void placeUndergroundPoiSlices(Chunk c, int baseX, int baseZ) {
        int rx = com.veylon.settlement.SettlementPlanner.regionOfChunk(c.cx);
        int rz = com.veylon.settlement.SettlementPlanner.regionOfChunk(c.cz);
        Vec3i mouth = com.veylon.settlement.SettlementPlanner.caveEntrance(seed, this, rx, rz);
        for (UndergroundPoiPlan plan : undergroundPoiPlansForRegion(rx, rz)) {
            carveUndergroundChamberSlice(c, baseX, baseZ, plan);
            carvePoiConnectorSlice(c, baseX, baseZ, mouth, plan);
            if (Math.floorDiv(plan.x, Chunk.SX) == c.cx
                    && Math.floorDiv(plan.z, Chunk.SZ) == c.cz) {
                furnishUndergroundPoi(new Random(plan.lootSeed),
                        plan.type, plan.x, plan.y, plan.z);
                world.registerPoi(new Poi(plan.type, new Vec3i(plan.x, plan.y, plan.z)));
            }
        }
    }

    private void carveUndergroundChamberSlice(Chunk c, int baseX, int baseZ,
                                               UndergroundPoiPlan plan) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                int x = plan.x + dx, z = plan.z + dz;
                if (x < baseX || x >= baseX + Chunk.SX
                        || z < baseZ || z >= baseZ + Chunk.SZ) {
                    continue;
                }
                for (int dy = 0; dy <= 3; dy++) {
                    if (dx * dx + dz * dz + dy * dy <= 12) {
                        c.set(x - baseX, plan.y + dy, z - baseZ, BlockType.AIR);
                    }
                }
            }
        }
    }

    private void carvePoiConnectorSlice(Chunk c, int baseX, int baseZ,
                                        Vec3i mouth, UndergroundPoiPlan plan) {
        int y = com.veylon.settlement.SettlementPlanner.CAVE_TARGET_Y;
        int shaftX = plan.x - 2, shaftZ = plan.z;
        boolean xFirst = (Noise.mix(plan.lootSeed ^ 0x54554e4e454cL) & 1L) == 0;
        if (xFirst) {
            carveHorizontalX(c, baseX, baseZ, mouth.x(), shaftX, mouth.z(), y);
            carveHorizontalZ(c, baseX, baseZ, shaftX, mouth.z(), shaftZ, y);
        } else {
            carveHorizontalZ(c, baseX, baseZ, mouth.x(), mouth.z(), shaftZ, y);
            carveHorizontalX(c, baseX, baseZ, mouth.x(), shaftX, shaftZ, y);
        }
        int minY = Math.min(y, plan.y), maxY = Math.max(y, plan.y);
        if (shaftX >= baseX && shaftX < baseX + Chunk.SX
                && shaftZ >= baseZ && shaftZ < baseZ + Chunk.SZ) {
            int lx = shaftX - baseX, lz = shaftZ - baseZ;
            for (int yy = minY; yy <= maxY; yy++) {
                c.set(lx, yy, lz, BlockType.LADDER);
            }
        }
    }

    private void carveHorizontalX(Chunk c, int baseX, int baseZ,
                                  int x0, int x1, int z, int y) {
        int from = Math.min(x0, x1), to = Math.max(x0, x1);
        for (int x = from; x <= to; x++) {
            carveTunnelCell(c, baseX, baseZ, x, y, z);
        }
    }

    private void carveHorizontalZ(Chunk c, int baseX, int baseZ,
                                  int x, int z0, int z1, int y) {
        int from = Math.min(z0, z1), to = Math.max(z0, z1);
        for (int z = from; z <= to; z++) {
            carveTunnelCell(c, baseX, baseZ, x, y, z);
        }
    }

    private void carveTunnelCell(Chunk c, int baseX, int baseZ,
                                 int x, int y, int z) {
        if (x < baseX || x >= baseX + Chunk.SX || z < baseZ || z >= baseZ + Chunk.SZ) {
            return;
        }
        int lx = x - baseX, lz = z - baseZ;
        if (!c.get(lx, y - 1, lz).solid) {
            c.set(lx, y - 1, lz, BlockType.BASALT);
        }
        c.set(lx, y, lz, BlockType.AIR);
        c.set(lx, y + 1, lz, BlockType.AIR);
    }

    private void furnishUndergroundPoi(Random rng, Poi.PoiType type, int x, int y, int z) {
        switch (type) {
            case ABANDONED_MINE -> {
                for (int i = -2; i <= 2; i += 2) {
                    world.genSet(x + i, y, z - 2, BlockType.LOG, false);
                    world.genSet(x + i, y + 1, z - 2, BlockType.LOG, false);
                    world.genSet(x + i, y + 2, z - 2, BlockType.PLANK, false);
                }
                world.genSet(x, y, z, BlockType.CRATE, false);
                Inventory loot = new Inventory(12);
                loot.add(ItemType.COAL, 3 + rng.nextInt(4));
                loot.add(ItemType.IRON_ORE, 2 + rng.nextInt(3));
                if (rng.nextFloat() < 0.4f) {
                    loot.add(ItemType.SALTPETER, 2);
                }
                world.crateContents.putIfAbsent(new Vec3i(x, y, z), loot);
                world.genSet(x - 1, y, z + 1, BlockType.TORCH, true);
            }
            case SMUGGLER_CACHE -> {
                world.genSet(x, y, z, BlockType.CRATE, false);
                Inventory loot = new Inventory(12);
                loot.add(ItemType.BLACK_POWDER, 1 + rng.nextInt(3));
                loot.add(ItemType.SCRAP, 2 + rng.nextInt(3));
                loot.add(ItemType.DRIED_MEAT, 1 + rng.nextInt(3));
                if (rng.nextFloat() < 0.3f) {
                    loot.add(ItemType.SCRAP_BOMB, 1);
                }
                world.crateContents.putIfAbsent(new Vec3i(x, y, z), loot);
                world.genSet(x + 1, y, z, BlockType.BONE_PILE, true);
            }
            case HIDEOUT_CAVE -> {
                world.genSet(x, y, z, BlockType.CAMPFIRE, false);
                world.campfireFuel.putIfAbsent(new Vec3i(x, y, z), 500f);
                world.genSet(x - 2, y, z - 1, BlockType.BEDROLL, false);
                world.genSet(x + 2, y, z + 1, BlockType.BEDROLL, false);
                world.genSet(x + 2, y, z - 2, BlockType.CRATE, false);
                Inventory loot = new Inventory(12);
                loot.add(ItemType.ARROW, 4 + rng.nextInt(6));
                loot.add(ItemType.RAW_MEAT, 1 + rng.nextInt(2));
                if (rng.nextFloat() < 0.4f) {
                    loot.add(ItemType.MUSKET_BALL, 2 + rng.nextInt(3));
                }
                world.crateContents.putIfAbsent(new Vec3i(x + 2, y, z - 2), loot);
            }
            case RESONANT_SHRINE -> {
                world.genSet(x, y, z, BlockType.RUIN_STONE, false);
                world.genSet(x, y + 1, z, BlockType.RUIN_CORE, false);
                world.genSet(x - 2, y, z - 2, BlockType.RUIN_STONE, false);
                world.genSet(x + 2, y, z + 2, BlockType.RUIN_STONE, false);
                world.genSet(x + 2, y, z - 2, BlockType.GLOW_FUNGUS, true);
                world.genSet(x - 2, y, z + 2, BlockType.GLOW_FUNGUS, true);
            }
            case STALKER_NEST -> {
                world.genSet(x, y, z, BlockType.BONE_PILE, false);
                world.genSet(x + 1, y, z - 1, BlockType.BONE_PILE, true);
                world.genSet(x - 1, y, z + 1, BlockType.BONE_PILE, true);
                world.genSet(x - 1, y, z - 1, BlockType.BONE_PILE, true);
            }
            case EXPEDITION_CAMP -> {
                world.genSet(x, y, z, BlockType.CRATE, false);
                Inventory loot = new Inventory(12);
                loot.add(ItemType.LANTERN, 1);
                loot.add(ItemType.BANDAGE, 1 + rng.nextInt(2));
                loot.add(ItemType.TRAIL_MARKER, 2 + rng.nextInt(4));
                if (rng.nextFloat() < 0.35f) {
                    loot.add(ItemType.BLUEPRINT_FRAGMENT, 1);
                }
                world.crateContents.putIfAbsent(new Vec3i(x, y, z), loot);
                world.genSet(x - 1, y, z, BlockType.BEDROLL, true);
                world.genSet(x + 1, y, z + 1, BlockType.BONE_PILE, true);
                world.genSet(x + 1, y, z - 1, BlockType.TORCH, true);
            }
            default -> {
            }
        }
    }

    /**
     * Guaranteed regional cave entrance: a climbable, two-cell-clear ladder
     * shaft from a dry surface landing into the connected regional backbone.
     * It occupies one chunk, while POI connectors are applied slice-by-slice.
     */
    private void carveCaveEntranceSlice(Chunk c, int baseX, int baseZ) {
        int rx = com.veylon.settlement.SettlementPlanner.regionOfChunk(c.cx);
        int rz = com.veylon.settlement.SettlementPlanner.regionOfChunk(c.cz);
        Vec3i mouth = com.veylon.settlement.SettlementPlanner.caveEntrance(seed, this, rx, rz);
        if (mouth.x() + 3 < baseX || mouth.x() - 3 >= baseX + Chunk.SX
                || mouth.z() + 3 < baseZ || mouth.z() - 3 >= baseZ + Chunk.SZ) {
            return;
        }
        int surfaceY = mouth.y();
        int bottom = com.veylon.settlement.SettlementPlanner.CAVE_TARGET_Y;
        if (mouth.x() >= baseX && mouth.x() < baseX + Chunk.SX
                && mouth.z() >= baseZ && mouth.z() < baseZ + Chunk.SZ) {
            int lx = mouth.x() - baseX, lz = mouth.z() - baseZ;
            if (!c.get(lx, bottom - 1, lz).solid) {
                c.set(lx, bottom - 1, lz, BlockType.BASALT);
            }
            for (int y = bottom; y <= surfaceY + 1; y++) {
                c.set(lx, y, lz, BlockType.LADDER);
            }
            c.set(lx, surfaceY + 2, lz, BlockType.AIR);
            // Dry surface landing beside the ladder, with full head clearance.
            int landingX = lx + 1;
            c.set(landingX, surfaceY, lz, BlockType.STONE);
            c.set(landingX, surfaceY + 1, lz, BlockType.AIR);
            c.set(landingX, surfaceY + 2, lz, BlockType.AIR);
        }
        // Open a small navigable chamber at the bottom of the shaft/backbone.
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = 0; dy <= 2; dy++) {
                    int px = mouth.x() + dx, pz = mouth.z() + dz;
                    if (px >= baseX && px < baseX + Chunk.SX && pz >= baseZ && pz < baseZ + Chunk.SZ
                            && dx * dx + dz * dz <= 5) {
                        if (dx == 0 && dz == 0) {
                            c.set(px - baseX, bottom + dy, pz - baseZ, BlockType.LADDER);
                        } else {
                            c.set(px - baseX, bottom + dy, pz - baseZ, BlockType.AIR);
                        }
                        if (!c.get(px - baseX, bottom - 1, pz - baseZ).solid) {
                            c.set(px - baseX, bottom - 1, pz - baseZ, BlockType.BASALT);
                        }
                    }
                }
            }
        }
        // The mouth's own chunk registers the POI (single registration).
        if (Math.floorDiv(mouth.x(), 16) == c.cx && Math.floorDiv(mouth.z(), 16) == c.cz) {
            world.registerPoi(new Poi(Poi.PoiType.CAVE_MOUTH,
                    new Vec3i(mouth.x(), surfaceY + 1, mouth.z())));
        }
    }

    /**
     * Applies this chunk's slice of its region's settlement layout. The layout
     * is a pure function of the seed, so every chunk gets a consistent slice
     * regardless of generation order, and structures never duplicate.
     */
    private void applySettlementSlice(Chunk c, int baseX, int baseZ) {
        int rx = com.veylon.settlement.SettlementPlanner.regionOfChunk(c.cx);
        int rz = com.veylon.settlement.SettlementPlanner.regionOfChunk(c.cz);
        com.veylon.settlement.Settlement s = world.settlementForRegion(rx, rz);
        if (s == null) {
            return;
        }
        int pad = 4;
        if (s.center.x() + s.radius + pad < baseX || s.center.x() - s.radius - pad >= baseX + Chunk.SX
                || s.center.z() + s.radius + pad < baseZ || s.center.z() - s.radius - pad >= baseZ + Chunk.SZ) {
            return;
        }
        var layout = world.layoutFor(s);
        for (var e : layout.blocks.entrySet()) {
            long k = e.getKey();
            int x = com.veylon.settlement.SettlementBuilder.unpackX(k);
            int z = com.veylon.settlement.SettlementBuilder.unpackZ(k);
            if (x < baseX || x >= baseX + Chunk.SX || z < baseZ || z >= baseZ + Chunk.SZ) {
                continue;
            }
            int y = com.veylon.settlement.SettlementBuilder.unpackY(k);
            c.set(x - baseX, y, z - baseZ, BlockType.byId(e.getValue()));
        }
        for (var e : layout.crates.entrySet()) {
            Vec3i pos = e.getKey();
            if (pos.x() >= baseX && pos.x() < baseX + Chunk.SX
                    && pos.z() >= baseZ && pos.z() < baseZ + Chunk.SZ) {
                world.crateContents.putIfAbsent(pos,
                        com.veylon.settlement.SettlementBuilder.rollCrate(seed, pos, e.getValue()));
            }
        }
        for (var e : layout.campfires.entrySet()) {
            Vec3i pos = e.getKey();
            if (pos.x() >= baseX && pos.x() < baseX + Chunk.SX
                    && pos.z() >= baseZ && pos.z() < baseZ + Chunk.SZ) {
                world.campfireFuel.putIfAbsent(pos, e.getValue());
            }
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
