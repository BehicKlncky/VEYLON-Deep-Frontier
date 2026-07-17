package com.veylon.settlement;

import com.veylon.util.Noise;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.WorldGenerator;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Deterministic lazy regional settlement planning. The world is divided into
 * {@link #REGION_BLOCKS}-wide regions; each region rolls at most one settlement
 * candidate from {@code (worldSeed, regionX, regionZ)} alone, so any chunk can
 * compute the plan of its region without global state, in any generation order.
 *
 * <p>The {@link #EDGE_MARGIN} keeps every settlement footprint strictly inside
 * its own region, so a chunk only ever intersects the settlement of the region
 * it belongs to — cross-region overlap is impossible by construction, and
 * minimum spacing between settlements is at least {@code 2 * EDGE_MARGIN}.</p>
 */
public final class SettlementPlanner {

    /** Region edge length in chunks (24 chunks = 384 blocks). */
    public static final int REGION_CHUNKS = 24;
    public static final int REGION_BLOCKS = REGION_CHUNKS * Chunk.SX;
    /** Settlement centers keep this distance from region edges (>= max radius + slack). */
    public static final int EDGE_MARGIN = 56;
    /** Probability that a region hosts any settlement at all. */
    public static final double SETTLEMENT_CHANCE = 0.55;

    // Conditional tier distribution once a settlement exists (sums to 1).
    public static final double CAMP_WEIGHT = 0.25;
    public static final double VILLAGE_WEIGHT = 0.45;
    public static final double FORT_WEIGHT = 0.18;
    public static final double CASTLE_WEIGHT = 0.09;
    public static final double FORTRESS_WEIGHT = 0.03;

    /** Fortresses spawn at least this many regions (Chebyshev) from spawn. */
    public static final int FORTRESS_MIN_SPAWN_DIST = 3;
    /** Minimum Chebyshev region distance between two fortresses. */
    public static final int FORTRESS_SEPARATION = 4;
    /** Minimum Chebyshev region distance between castles (and castle-fortress). */
    public static final int CASTLE_SEPARATION = 2;

    /** Candidate positions tried per region before giving up on bad terrain. */
    private static final int PLACEMENT_ATTEMPTS = 6;
    /** Maximum height difference across a footprint before terrain is rejected. */
    private static final int MAX_FOOTPRINT_SLOPE = 9;
    private static final Map<WorldGenerator, Vec3i> STARTER_SITE_CACHE = new WeakHashMap<>();
    /** Connected regional cave network target depth. */
    public static final int CAVE_TARGET_Y = 8;
    /** Surface POIs keep their complete footprint outside this reservation. */
    public static final int CAVE_RESERVATION_RADIUS = 12;

    private SettlementPlanner() {
    }

    public static int regionOfBlock(int blockCoord) {
        return Math.floorDiv(blockCoord, REGION_BLOCKS);
    }

    public static int regionOfChunk(int chunkCoord) {
        return Math.floorDiv(chunkCoord, REGION_CHUNKS);
    }

    /** A raw candidate before neighbor constraints; pure function of (seed, region). */
    record Raw(SettlementType type, Vec3i center, long priority) {
    }

    private static Random regionRng(long seed, int rx, int rz, long salt) {
        return new Random(Noise.mix(seed ^ salt ^ (rx * 0x9E3779B97F4A7C15L)
                ^ ((long) rz * 0xC2B2AE3D27D4EB4FL)));
    }

    /**
     * Rolls the unconstrained candidate for a region. Terrain-validated but
     * blind to neighbors; used both directly and for symmetric neighbor checks.
     */
    static Raw rawPlan(long seed, WorldGenerator gen, int rx, int rz) {
        Random rng = regionRng(seed, rx, rz, 0x53455454L);
        if (rng.nextDouble() >= SETTLEMENT_CHANCE) {
            return null;
        }
        double roll = rng.nextDouble();
        SettlementType type;
        if (roll < CAMP_WEIGHT) {
            type = SettlementType.CAMP;
        } else if (roll < CAMP_WEIGHT + VILLAGE_WEIGHT) {
            type = SettlementType.VILLAGE;
        } else if (roll < CAMP_WEIGHT + VILLAGE_WEIGHT + FORT_WEIGHT) {
            type = SettlementType.FORT;
        } else if (roll < CAMP_WEIGHT + VILLAGE_WEIGHT + FORT_WEIGHT + CASTLE_WEIGHT) {
            type = SettlementType.CASTLE;
        } else {
            type = SettlementType.FORTRESS;
        }

        Vec3i center = findSite(gen, rng, rx, rz, type.radius);
        if (center == null) {
            return null;
        }
        long priority = Noise.mix(seed ^ Settlement.packId(rx, rz) ^ 0x505249L);
        return new Raw(type, center, priority);
    }

    /** Scans candidate spots inside the region interior for buildable terrain. */
    private static Vec3i findSite(WorldGenerator gen, Random rng, int rx, int rz, int radius) {
        int base0x = rx * REGION_BLOCKS + EDGE_MARGIN;
        int base0z = rz * REGION_BLOCKS + EDGE_MARGIN;
        int span = REGION_BLOCKS - 2 * EDGE_MARGIN;
        for (int attempt = 0; attempt < PLACEMENT_ATTEMPTS; attempt++) {
            int x = base0x + rng.nextInt(span);
            int z = base0z + rng.nextInt(span);
            int h = gen.heightAt(x, z);
            if (h <= World.SEA_LEVEL + 1 || h >= Chunk.SY - 24) {
                continue;
            }
            // Footprint corners must be dry and the slope manageable
            // (stepped foundations absorb the rest).
            int min = h, max = h;
            boolean ok = true;
            for (int dx = -radius; dx <= radius && ok; dx += radius) {
                for (int dz = -radius; dz <= radius && ok; dz += radius) {
                    int ch = gen.heightAt(x + dx, z + dz);
                    if (ch <= World.SEA_LEVEL) {
                        ok = false;
                    }
                    min = Math.min(min, ch);
                    max = Math.max(max, ch);
                }
            }
            if (!ok || max - min > MAX_FOOTPRINT_SLOPE) {
                continue;
            }
            return new Vec3i(x, h + 1, z);
        }
        return null;
    }

    /**
     * The final, constraint-checked plan for a region — or null. Deterministic
     * and symmetric: every region resolves rarity conflicts by comparing raw
     * candidate priorities, so all regions agree regardless of visit order.
     */
    public static Settlement plan(long seed, WorldGenerator gen, int rx, int rz) {
        Raw raw = rawPlan(seed, gen, rx, rz);
        if (rx == 0 && rz == 0) {
            Vec3i fallback = guaranteedStarterSite(gen);
            raw = new Raw(raw == null ? SettlementType.CAMP : raw.type(), fallback,
                    raw == null ? Noise.mix(seed ^ 0x53544152544552L) : raw.priority());
        }
        if (raw == null) {
            return null;
        }
        SettlementType type = raw.type();
        int spawnDist = Math.max(Math.abs(rx), Math.abs(rz));

        // Rarity separation: a fortress/castle yields to a peer with higher
        // priority inside the exclusion range.
        if (type == SettlementType.FORTRESS) {
            if (spawnDist < FORTRESS_MIN_SPAWN_DIST
                    || loses(seed, gen, rx, rz, raw, FORTRESS_SEPARATION, SettlementType.FORTRESS)) {
                type = SettlementType.FORT;
            }
        }
        if (type == SettlementType.CASTLE) {
            if (loses(seed, gen, rx, rz, raw, CASTLE_SEPARATION, SettlementType.CASTLE)
                    || anyNeighbor(seed, gen, rx, rz, CASTLE_SEPARATION, SettlementType.FORTRESS)) {
                type = SettlementType.FORT;
            }
        }

        Random rng = regionRng(seed, rx, rz, 0x414c4947L);
        String faction;
        Settlement.Alignment alignment;
        double a = rng.nextDouble();
        switch (type) {
            case CAMP -> {
                if (a < 0.40) {
                    faction = HumanFaction.FRONTIER;
                    alignment = Settlement.Alignment.FRIENDLY;
                } else if (a < 0.85) {
                    faction = HumanFaction.FREE_SETTLERS;
                    alignment = Settlement.Alignment.NEUTRAL;
                } else {
                    faction = HumanFaction.SCAVENGERS;
                    alignment = Settlement.Alignment.HOSTILE;
                }
            }
            case VILLAGE -> {
                if (a < 0.40) {
                    faction = HumanFaction.FRONTIER;
                    alignment = Settlement.Alignment.FRIENDLY;
                } else if (a < 0.85) {
                    faction = HumanFaction.FREE_SETTLERS;
                    alignment = Settlement.Alignment.NEUTRAL;
                } else {
                    faction = HumanFaction.HEADHUNTERS;
                    alignment = Settlement.Alignment.HOSTILE;
                }
            }
            case FORT -> {
                if (a < 0.30) {
                    faction = HumanFaction.FRONTIER;
                    alignment = Settlement.Alignment.FRIENDLY;
                } else if (a < 0.45) {
                    faction = HumanFaction.FREE_SETTLERS;
                    alignment = Settlement.Alignment.NEUTRAL;
                } else {
                    faction = HumanFaction.HEADHUNTERS;
                    alignment = Settlement.Alignment.HOSTILE;
                }
            }
            case CASTLE -> {
                if (a < 0.35) {
                    faction = HumanFaction.FRONTIER;
                    alignment = Settlement.Alignment.FRIENDLY;
                } else {
                    faction = HumanFaction.HEADHUNTERS;
                    alignment = Settlement.Alignment.HOSTILE;
                }
            }
            default -> {
                faction = HumanFaction.HEADHUNTERS;
                alignment = Settlement.Alignment.HOSTILE;
            }
        }

        // Player-start protection: the spawn region always offers a friendly
        // camp/village; the 8 surrounding regions never host hostile owners,
        // and the balance-controlled starter tier is capped at VILLAGE.
        if (rx == 0 && rz == 0) {
            if (type != SettlementType.CAMP) {
                type = SettlementType.VILLAGE;
            }
            faction = HumanFaction.FRONTIER;
            alignment = Settlement.Alignment.FRIENDLY;
        } else if (spawnDist <= 1) {
            if (alignment == Settlement.Alignment.HOSTILE) {
                faction = HumanFaction.FREE_SETTLERS;
                alignment = Settlement.Alignment.NEUTRAL;
            }
            if (type == SettlementType.FORTRESS || type == SettlementType.CASTLE) {
                type = SettlementType.VILLAGE;
            }
        }

        Settlement s = new Settlement(Settlement.packId(rx, rz), rx, rz, type,
                raw.center(), faction, alignment);
        seedInitialState(seed, s);
        return s;
    }

    /** Deterministic terrain-adapted fallback near spawn; never returns null. */
    private static Vec3i guaranteedStarterSite(WorldGenerator gen) {
        synchronized (STARTER_SITE_CACHE) {
            Vec3i cached = STARTER_SITE_CACHE.get(gen);
            if (cached != null) {
                return cached;
            }
        }
        Vec3i computed = computeGuaranteedStarterSite(gen);
        synchronized (STARTER_SITE_CACHE) {
            STARTER_SITE_CACHE.put(gen, computed);
        }
        return computed;
    }

    private static Vec3i computeGuaranteedStarterSite(WorldGenerator gen) {
        Vec3i best = null;
        int bestSlope = Integer.MAX_VALUE;
        Set<Long> reachable = reachableSurface(gen, -32, REGION_BLOCKS + 32,
                -32, REGION_BLOCKS + 32, 250_000);
        // Region-edge margin starts 48-56 blocks from the actual crash area.
        for (int ring = 0; ring <= 15; ring++) {
            for (int dx = -ring; dx <= ring; dx++) {
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = EDGE_MARGIN + 8 + dx * 8;
                    int z = EDGE_MARGIN + 8 + dz * 8;
                    if (x < EDGE_MARGIN || z < EDGE_MARGIN
                            || x >= REGION_BLOCKS - EDGE_MARGIN
                            || z >= REGION_BLOCKS - EDGE_MARGIN) {
                        continue;
                    }
                    int h = gen.heightAt(x, z);
                    if (h <= World.SEA_LEVEL + 1 || h >= Chunk.SY - 24) {
                        continue;
                    }
                    int min = h, max = h;
                    for (int ox : new int[]{-SettlementType.CAMP.radius, 0,
                            SettlementType.CAMP.radius}) {
                        for (int oz : new int[]{-SettlementType.CAMP.radius, 0,
                                SettlementType.CAMP.radius}) {
                            int sample = gen.heightAt(x + ox, z + oz);
                            min = Math.min(min, sample);
                            max = Math.max(max, sample);
                        }
                    }
                    int slope = max - min;
                    boolean siteReachable = reachable.contains(packSurface(x, z));
                    if (siteReachable && slope < bestSlope) {
                        bestSlope = slope;
                        best = new Vec3i(x, h + 1, z);
                    }
                    if (slope <= MAX_FOOTPRINT_SLOPE && siteReachable) {
                        return new Vec3i(x, h + 1, z);
                    }
                }
            }
        }
        if (best != null) {
            return best; // builder's stepped foundations adapt to the slope
        }
        int x = EDGE_MARGIN + 8, z = EDGE_MARGIN + 8;
        return new Vec3i(x, gen.heightAt(x, z) + 1, z);
    }

    /** Movement-rule surface proof used by starter placement and QA. */
    public static boolean surfaceReachableFromSpawn(WorldGenerator gen, int goalX, int goalZ) {
        Vec3i start = starterSpawn(gen);
        int minX = Math.min(start.x(), goalX) - 32;
        int maxX = Math.max(start.x(), goalX) + 32;
        int minZ = Math.min(start.z(), goalZ) - 32;
        int maxZ = Math.max(start.z(), goalZ) + 32;
        Set<Long> seen = reachableSurface(gen, minX, maxX, minZ, maxZ, 100_000);
        return seen.contains(packSurface(goalX, goalZ));
    }

    private static Set<Long> reachableSurface(WorldGenerator gen, int minX, int maxX,
                                               int minZ, int maxZ, int budget) {
        Vec3i start = starterSpawn(gen);
        ArrayDeque<int[]> open = new ArrayDeque<>();
        Set<Long> seen = new HashSet<>();
        open.add(new int[]{start.x(), start.z()});
        seen.add(packSurface(start.x(), start.z()));
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < budget) {
            int[] at = open.removeFirst();
            // Water is normal traversable terrain in the existing player
            // controller, so use sea level as the effective swim surface.
            int height = Math.max(gen.heightAt(at[0], at[1]), World.SEA_LEVEL);
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = at[0] + dir[0], nz = at[1] + dir[1];
                if (nx < minX || nx > maxX || nz < minZ || nz > maxZ) {
                    continue;
                }
                int nextHeight = Math.max(gen.heightAt(nx, nz), World.SEA_LEVEL);
                if (nextHeight > height + 1 || nextHeight < height - 3) {
                    continue;
                }
                long key = packSurface(nx, nz);
                if (seen.add(key)) {
                    open.addLast(new int[]{nx, nz});
                }
            }
        }
        return seen;
    }

    private static long packSurface(int x, int z) {
        return ((long) x << 32) ^ (z & 0xffffffffL);
    }

    private static Vec3i starterSpawn(WorldGenerator gen) {
        for (int ring = 0; ring <= 48; ring += 4) {
            for (int dx = -ring; dx <= ring; dx += 4) {
                for (int dz = -ring; dz <= ring; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring) {
                        continue;
                    }
                    int x = 8 + dx, z = 8 + dz;
                    int height = gen.heightAt(x, z);
                    if (height > World.SEA_LEVEL + 1) {
                        return new Vec3i(x, height + 1, z);
                    }
                }
            }
        }
        int height = gen.heightAt(8, 8);
        return new Vec3i(8, height + 1, 8);
    }

    /** True when a peer of {@code tier} with higher priority sits within range. */
    private static boolean loses(long seed, WorldGenerator gen, int rx, int rz,
                                 Raw self, int range, SettlementType tier) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Raw other = rawPlan(seed, gen, rx + dx, rz + dz);
                if (other != null && other.type() == tier && other.priority() > self.priority()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean anyNeighbor(long seed, WorldGenerator gen, int rx, int rz,
                                       int range, SettlementType tier) {
        for (int dx = -range; dx <= range; dx++) {
            for (int dz = -range; dz <= range; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                Raw other = rawPlan(seed, gen, rx + dx, rz + dz);
                if (other != null && other.type() == tier) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Guaranteed one cave entrance per region for deep-frontier worlds. */
    public static Vec3i caveEntrance(long seed, WorldGenerator gen, int rx, int rz) {
        Settlement settlement = plan(seed, gen, rx, rz);
        Random rng = regionRng(seed, rx, rz, 0x43415645L);
        for (int attempt = 0; attempt < 24; attempt++) {
            int x = rx * REGION_BLOCKS + 24 + rng.nextInt(REGION_BLOCKS - 48);
            int z = rz * REGION_BLOCKS + 24 + rng.nextInt(REGION_BLOCKS - 48);
            Vec3i site = validCaveSite(gen, settlement, x, z, true);
            if (site != null) {
                return site;
            }
        }
        // Deterministic exhaustive fallback: select the flattest eligible dry
        // landing. This removes the old probabilistic null result.
        Vec3i best = null;
        int bestScore = Integer.MAX_VALUE;
        int minX = rx * REGION_BLOCKS + 24;
        int minZ = rz * REGION_BLOCKS + 24;
        for (int x = minX; x < minX + REGION_BLOCKS - 48; x += 4) {
            for (int z = minZ; z < minZ + REGION_BLOCKS - 48; z += 4) {
                Vec3i site = validCaveSite(gen, settlement, x, z, false);
                if (site == null) {
                    continue;
                }
                int slope = localSlope(gen, x, z);
                int score = slope * 1000 + (int) (Noise.mix(seed ^ Settlement.packId(x, z)) & 0x3ff);
                if (score < bestScore) {
                    bestScore = score;
                    best = site;
                }
            }
        }
        if (best != null) {
            return best;
        }
        // Deep-generator regions are eligible when they contain dry terrain.
        // Height generation guarantees such terrain in normal worlds; retain a
        // deterministic final site so callers never silently omit the entrance.
        int x = minX + 8, z = minZ + 8;
        return new Vec3i(x, gen.heightAt(x, z), z);
    }

    private static Vec3i validCaveSite(WorldGenerator gen, Settlement settlement,
                                       int x, int z, boolean requireFlat) {
        int lx = Math.floorMod(x, Chunk.SX), lz = Math.floorMod(z, Chunk.SZ);
        if (lx < 3 || lx > 12 || lz < 3 || lz > 12) {
            return null; // landing and POI marker stay inside one chunk
        }
        int h = gen.heightAt(x, z);
        if (h <= World.SEA_LEVEL + 2 || h >= Chunk.SY - 20) {
            return null;
        }
        if (settlement != null
                && Math.abs(x - settlement.center.x()) <= settlement.radius + CAVE_RESERVATION_RADIUS
                && Math.abs(z - settlement.center.z()) <= settlement.radius + CAVE_RESERVATION_RADIUS) {
            return null;
        }
        if (requireFlat && localSlope(gen, x, z) > 2) {
            return null;
        }
        return new Vec3i(x, h, z);
    }

    private static int localSlope(WorldGenerator gen, int x, int z) {
        int min = Integer.MAX_VALUE, max = Integer.MIN_VALUE;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int h = gen.heightAt(x + dx, z + dz);
                min = Math.min(min, h);
                max = Math.max(max, h);
            }
        }
        return max - min;
    }

    // ------------------------------------------------------------------
    // Initial dynamic state
    // ------------------------------------------------------------------

    private static final String[] SETTLER_NAMES = {
            "Arden", "Brice", "Calla", "Doran", "Edda", "Fenn", "Galia", "Harrow",
            "Isla", "Joss", "Kestrel", "Lyra", "Marek", "Nils", "Odessa", "Pell",
            "Quill", "Rana", "Soren", "Tamsin", "Ulric", "Vessa", "Wren", "Yara"
    };
    private static final String[] HEADHUNTER_NAMES = {
            "Ashfang", "Bloodbriar", "Carrion", "Dusk", "Embermaw", "Flint",
            "Gravel", "Hollow", "Ironjaw", "Krail", "Lurk", "Mire", "Nettle",
            "Ossler", "Pyre", "Quarry", "Rasp", "Sable", "Tarn", "Vex"
    };

    /** Rolls the founding population and stocks; deterministic per settlement. */
    static void seedInitialState(long seed, Settlement s) {
        Random rng = new Random(Noise.mix(seed ^ s.id ^ 0x504f50L));
        int pop = s.type.minPopulation
                + rng.nextInt(s.type.maxPopulation - s.type.minPopulation + 1);
        boolean hostile = s.alignment == Settlement.Alignment.HOSTILE;
        String[] names = hostile ? HEADHUNTER_NAMES : SETTLER_NAMES;

        for (int i = 0; i < pop; i++) {
            NpcArchetype a = rollArchetype(rng, s, i, hostile);
            String name = names[rng.nextInt(names.length)]
                    + (i > 0 && rng.nextFloat() < 0.35f ? " " + (char) ('A' + rng.nextInt(26)) + "." : "");
            Settlement.Resident r = new Settlement.Resident(name, a);
            r.bedIndex = i;
            r.dutyIndex = i;
            s.residents.add(r);
        }
        // One captive to rescue in some hostile forts and every fortress prison.
        if (hostile && (s.type == SettlementType.FORTRESS
                || ((s.type == SettlementType.FORT || s.type == SettlementType.CASTLE)
                && rng.nextFloat() < 0.45f))) {
            Settlement.Resident captive = new Settlement.Resident(
                    SETTLER_NAMES[rng.nextInt(SETTLER_NAMES.length)], NpcArchetype.CAPTIVE);
            s.residents.add(captive);
        }

        s.foodStock = 6 + pop * 2 + rng.nextInt(8);
        s.woodStock = 8 + rng.nextInt(14);
        s.medStock = rng.nextInt(4) + (s.type == SettlementType.VILLAGE ? 2 : 0);
        s.metalStock = rng.nextInt(6) + (s.type.ordinal() >= SettlementType.FORT.ordinal() ? 4 : 0);
        s.morale = 60 + rng.nextInt(25);
    }

    private static NpcArchetype rollArchetype(Random rng, Settlement s, int idx, boolean hostile) {
        if (hostile) {
            boolean leaderTier = s.type == SettlementType.FORT
                    || s.type == SettlementType.CASTLE || s.type == SettlementType.FORTRESS;
            if (idx == 0 && leaderTier) {
                return NpcArchetype.LEADER;
            }
            if (HumanFaction.SCAVENGERS.equals(s.factionId)) {
                return NpcArchetype.SCAVENGER;
            }
            // Powdermen only garrison the black-powder tiers.
            boolean powderTier = s.type == SettlementType.CASTLE || s.type == SettlementType.FORTRESS;
            double r = rng.nextDouble();
            if (powderTier && r < 0.18) {
                return NpcArchetype.POWDERMAN;
            }
            if (r < 0.34) {
                return NpcArchetype.SCOUT;
            }
            if (r < 0.52) {
                return NpcArchetype.TRACKER;
            }
            if (r < 0.80) {
                return NpcArchetype.HUNTER;
            }
            return NpcArchetype.BRUTE;
        }
        // Civilian tiers: guards scale with settlement class.
        boolean martial = s.type == SettlementType.FORT || s.type == SettlementType.CASTLE;
        double r = rng.nextDouble();
        if (martial) {
            if (r < 0.45) {
                return NpcArchetype.GUARD;
            }
            if (r < 0.60) {
                return NpcArchetype.ARCHER;
            }
            if (r < 0.72) {
                return NpcArchetype.SMITH;
            }
            if (r < 0.84) {
                return NpcArchetype.MEDIC;
            }
            return NpcArchetype.VILLAGER;
        }
        if (idx == 0 && s.type == SettlementType.VILLAGE) {
            return NpcArchetype.TRADER;
        }
        if (r < 0.18) {
            return NpcArchetype.GUARD;
        }
        if (r < 0.30) {
            return NpcArchetype.MEDIC;
        }
        if (r < 0.48) {
            return NpcArchetype.FARMER;
        }
        if (r < 0.58) {
            return NpcArchetype.SMITH;
        }
        return NpcArchetype.VILLAGER;
    }
}
