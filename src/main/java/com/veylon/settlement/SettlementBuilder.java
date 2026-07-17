package com.veylon.settlement;

import com.veylon.util.Noise;
import com.veylon.util.Vec3i;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.WorldGenerator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Deterministic modular settlement layout: reusable building modules composed
 * per tier, adapted to the biome. Produces a complete block-edit map plus AI
 * metadata (beds, duty points, patrols, gates, bell, prison, magazine) — pure
 * function of (worldSeed, settlement plan, terrain heights), so any chunk can
 * apply its slice independently of generation order.
 */
public final class SettlementBuilder {

    /** Crate stock archetypes; contents are rolled when the crate registers. */
    public static final int CRATE_SUPPLY = 0;
    public static final int CRATE_ARMORY = 1;
    public static final int CRATE_POWDER = 2;
    public static final int CRATE_RELIC = 3;

    /** One settlement's computed layout. */
    public static final class Layout {
        /** Packed block position -> BlockType id. */
        public final Map<Long, Byte> blocks = new LinkedHashMap<>();
        /** Crate positions -> crate kind. */
        public final Map<Vec3i, Integer> crates = new LinkedHashMap<>();
        /** Campfire positions (fuel registered on placement). */
        public final Map<Vec3i, Float> campfires = new LinkedHashMap<>();
        /** Walkable side/rear entries intentionally left through defensive rings. */
        public final List<Vec3i> infiltrationPoints = new ArrayList<>();

        void set(int x, int y, int z, BlockType t) {
            if (y < 1 || y >= Chunk.SY) {
                return;
            }
            blocks.put(pack(x, y, z), t.id());
        }
    }

    public static long pack(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    /** Sign-extending unpack helpers matching {@link #pack}. */
    public static int unpackX(long k) {
        return (int) (k << 0 >> 38);
    }

    public static int unpackZ(long k) {
        return (int) (k << 26 >> 38);
    }

    public static int unpackY(long k) {
        return (int) (k & 0xFFFL);
    }

    // Biome material palette.
    private record Palette(BlockType wall, BlockType floor, BlockType roof,
                           BlockType fence, BlockType foundation, BlockType path) {
    }

    private final Settlement s;
    private final WorldGenerator gen;
    private final Random rng;
    private final Layout layout = new Layout();
    private final Palette pal;
    private final boolean stilts;

    private SettlementBuilder(long worldSeed, Settlement s, WorldGenerator gen) {
        this.s = s;
        this.gen = gen;
        this.rng = new Random(Noise.mix(worldSeed ^ s.id ^ 0x4255494cL));
        Biome biome = gen.biomeAt(s.center.x(), s.center.z());
        this.stilts = biome == Biome.MARSH;
        boolean stoneTier = s.type == SettlementType.CASTLE || s.type == SettlementType.FORTRESS;
        this.pal = switch (biome) {
            case PINE_FOREST -> new Palette(BlockType.LOG, BlockType.PLANK, BlockType.PLANK,
                    stoneTier ? BlockType.STONE_BRICK : BlockType.WALL, BlockType.STONE, BlockType.GRAVEL);
            case ROCKY_HIGHLANDS -> new Palette(BlockType.STONE, BlockType.PLANK, BlockType.PLANK,
                    stoneTier ? BlockType.STONE_BRICK : BlockType.STONE, BlockType.STONE, BlockType.GRAVEL);
            case MARSH -> new Palette(BlockType.PLANK, BlockType.PLANK, BlockType.PLANK,
                    stoneTier ? BlockType.STONE_BRICK : BlockType.WALL, BlockType.LOG, BlockType.PLANK);
            case COLD_RIDGE -> new Palette(BlockType.STONE, BlockType.PLANK, BlockType.PLANK,
                    stoneTier ? BlockType.STONE_BRICK : BlockType.STONE, BlockType.STONE, BlockType.GRAVEL);
            case SCRUBLAND -> new Palette(BlockType.CLAY, BlockType.PLANK, BlockType.PLANK,
                    stoneTier ? BlockType.STONE_BRICK : BlockType.WALL, BlockType.CLAY, BlockType.GRAVEL);
            default -> new Palette(BlockType.PLANK, BlockType.PLANK, BlockType.WALL,
                    stoneTier ? BlockType.STONE_BRICK : BlockType.WALL, BlockType.STONE, BlockType.GRAVEL);
        };
    }

    /** Builds (or returns) the cached layout for a settlement. */
    public static Layout layout(long worldSeed, Settlement s, WorldGenerator gen) {
        SettlementBuilder b = new SettlementBuilder(worldSeed, s, gen);
        b.build();
        s.layoutBuilt = true;
        return b.layout;
    }

    // ------------------------------------------------------------------
    // Tier composition
    // ------------------------------------------------------------------

    private void build() {
        int cx = s.center.x(), cz = s.center.z();
        switch (s.type) {
            case CAMP -> buildCamp(cx, cz);
            case VILLAGE -> buildVillage(cx, cz);
            case FORT -> buildFort(cx, cz);
            case CASTLE -> buildCastle(cx, cz);
            case FORTRESS -> buildFortress(cx, cz);
        }
    }

    private void buildCamp(int cx, int cz) {
        campfireArea(cx, cz);
        int tents = 1 + rng.nextInt(3);
        int[][] spots = {{-6, -2}, {5, -4}, {-2, 6}, {6, 4}};
        for (int i = 0; i < tents; i++) {
            hut(cx + spots[i][0], cz + spots[i][1], 4, 4, true);
        }
        crate(cx + 2, cz - 3, CRATE_SUPPLY);
        s.dutyPoints.add(new Vec3i(cx, groundY(cx, cz) + 1, cz));
        s.dutyPoints.add(new Vec3i(cx + 2, groundY(cx + 2, cz - 3) + 1, cz - 3));
        patrolSquare(cx, cz, 7);
    }

    private void buildVillage(int cx, int cz) {
        campfireArea(cx, cz);
        well(cx + 3, cz + 3);
        // Ring of houses around the central square.
        int houses = Math.max(3, s.residents.size() / 2);
        int[][] slots = {{-13, -4}, {-13, 7}, {-4, -14}, {7, -14}, {13, -3},
                {13, 8}, {-3, 13}, {8, 13}, {-14, -13}, {12, -12}};
        for (int i = 0; i < Math.min(houses, slots.length - 3); i++) {
            house(cx + slots[i][0], cz + slots[i][1], 5, 6);
        }
        // Workplaces.
        workshop(cx + slots[slots.length - 3][0], cz + slots[slots.length - 3][1]);
        storage(cx + slots[slots.length - 2][0], cz + slots[slots.length - 2][1]);
        traderStall(cx - 4, cz + 4);
        farmPlot(cx + slots[slots.length - 1][0], cz + slots[slots.length - 1][1]);
        if (s.residents.size() >= 8) {
            // Keep the clinic clear of the north-west home's only doorway.
            // The previous (+1 Z) placement overlapped that wall exactly.
            medicHut(cx - 8, cz + 12);
        }
        // Paths from the square out to each building slot.
        for (int[] slot : slots) {
            path(cx, cz, cx + slot[0], cz + slot[1]);
        }
        patrolSquare(cx, cz, s.radius - 4);
    }

    private void buildFort(int cx, int cz) {
        int r = 15;
        wallRing(cx, cz, r, 3, pal.fence);
        gate(cx, cz + r, true);
        watchtower(cx - r + 2, cz - r + 2);
        watchtower(cx + r - 2, cz + r - 2);
        campfireArea(cx, cz);
        barracks(cx - 8, cz - 6, 4);
        storage(cx + 6, cz - 8);
        alarmBell(cx + 2, cz + r - 4);
        armory(cx + 7, cz + 5);
        if (hasCaptive()) {
            prisonCage(cx - 7, cz + 6);
        }
        path(cx, cz + r - 1, cx, cz);
        patrolSquare(cx, cz, r - 3);
    }

    private void buildCastle(int cx, int cz) {
        int r = 20;
        wallRing(cx, cz, r, 5, BlockType.STONE_BRICK);
        gate(cx, cz + r, true);
        gateTowers(cx, cz + r);
        keep(cx, cz - 8, 9, 2);
        barracks(cx - 12, cz + 2, 5);
        storage(cx + 11, cz + 3);
        well(cx + 5, cz + 6);
        campfireArea(cx - 4, cz + 8);
        alarmBell(cx + 2, cz + r - 4);
        if (hasCaptive()) {
            prisonCage(cx + 12, cz - 8);
        }
        path(cx, cz + r - 1, cx, cz - 4);
        patrolSquare(cx, cz, r - 3);
    }

    private void buildFortress(int cx, int cz) {
        int outer = 29, inner = 14;
        // Outer defense zone.
        wallRing(cx, cz, outer, 5, BlockType.STONE_BRICK);
        gate(cx, cz + outer, true);
        gateTowers(cx, cz + outer);
        for (int[] corner : new int[][]{{-outer, -outer}, {outer, -outer},
                {-outer, outer}, {outer, outer}}) {
            watchtower(cx + corner[0] / outer * (outer - 3), cz + corner[1] / outer * (outer - 3));
        }
        // Concealed rear postern: a 1x2 gap in the north wall for infiltration.
        posternGap(cx + 6, cz - outer);

        // Courtyard zone between walls.
        barracks(cx - 21, cz + 8, 5);
        barracks(cx + 15, cz + 9, 5);
        storage(cx - 19, cz - 10);
        prisonCage(cx + 19, cz - 12);
        powderMagazine(cx - 9, cz + 19);
        well(cx + 8, cz + 18);
        campfireArea(cx - 8, cz - 18);
        alarmBell(cx + 3, cz + outer - 4);

        // Inner command zone.
        wallRing(cx, cz, inner, 4, BlockType.STONE_BRICK);
        gate(cx, cz + inner, false);
        keep(cx, cz - 3, 11, 2);
        alarmBell(cx + 4, cz + inner - 3);

        path(cx, cz + outer - 1, cx, cz + inner + 1);
        path(cx, cz + inner - 1, cx, cz);
        // The corner watchtowers are centered at outer-3. Keeping the patrol
        // circuit on the same coordinates put all four waypoints inside solid
        // tower pillars, so active patrols could never actually reach them.
        patrolSquare(cx, cz, outer - 5);
        patrolSquare(cx, cz, inner - 3);
    }

    private boolean hasCaptive() {
        for (Settlement.Resident r : s.residents) {
            if (r.archetype == NpcArchetype.CAPTIVE) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Modules
    // ------------------------------------------------------------------

    private int groundY(int x, int z) {
        return gen.heightAt(x, z);
    }

    /** Clears vegetation and levels a rectangular pad; returns the pad floor Y. */
    private int pad(int x0, int z0, int w, int d) {
        int base = groundY(x0 + w / 2, z0 + d / 2);
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                int g = groundY(x, z);
                // Stepped foundation up to the pad level; never floats.
                for (int y = Math.min(g, base) ; y <= base; y++) {
                    layout.set(x, y, z, stilts && y > g ? BlockType.LOG : pal.foundation);
                }
                // Clear anything above the pad (vegetation, small terrain lips).
                for (int y = base + 1; y <= base + 6; y++) {
                    layout.set(x, y, z, BlockType.AIR);
                }
            }
        }
        return base;
    }

    /** Small shelter: walls, doorway, bedroll. Used for camp tents. */
    private void hut(int x0, int z0, int w, int d, boolean bedroll) {
        int base = pad(x0, z0, w, d);
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                boolean edge = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1;
                if (edge) {
                    for (int y = 1; y <= 2; y++) {
                        layout.set(x, base + y, z, pal.wall);
                    }
                }
                layout.set(x, base + 3, z, pal.roof);
            }
        }
        // South doorway.
        int door = x0 + w / 2;
        layout.set(door, base + 1, z0 + d - 1, BlockType.AIR);
        layout.set(door, base + 2, z0 + d - 1, BlockType.AIR);
        doorwayApproach(door, z0 + d - 1, base);
        if (bedroll) {
            layout.set(x0 + 1, base + 1, z0 + 1, BlockType.BEDROLL);
            s.beds.add(new Vec3i(x0 + 1, base + 1, z0 + 1));
        }
    }

    /** Standard house: floor, walls, roof, door, bed, torch. */
    private void house(int x0, int z0, int w, int d) {
        int base = pad(x0, z0, w, d);
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                layout.set(x, base, z, pal.floor);
                boolean edge = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1;
                if (edge) {
                    for (int y = 1; y <= 3; y++) {
                        layout.set(x, base + y, z, pal.wall);
                    }
                }
                layout.set(x, base + 4, z, pal.roof);
            }
        }
        int door = x0 + w / 2;
        layout.set(door, base + 1, z0 + d - 1, BlockType.AIR);
        layout.set(door, base + 2, z0 + d - 1, BlockType.AIR);
        doorwayApproach(door, z0 + d - 1, base);
        layout.set(x0 + 1, base + 1, z0 + 1, BlockType.CAMP_BED);
        s.beds.add(new Vec3i(x0 + 1, base + 1, z0 + 1));
        layout.set(x0 + w - 2, base + 1, z0 + 1, BlockType.TORCH);
        s.dutyPoints.add(new Vec3i(door, base + 1, z0 + d));
    }

    private void storage(int x0, int z0) {
        int base = pad(x0, z0, 5, 5);
        hutShell(x0, z0, 5, 5, base, 3);
        crate(x0 + 1, z0 + 1, CRATE_SUPPLY);
        crate(x0 + 3, z0 + 1, CRATE_SUPPLY);
        s.dutyPoints.add(new Vec3i(x0 + 2, base + 1, z0 + 5));
    }

    private void workshop(int x0, int z0) {
        int base = pad(x0, z0, 6, 5);
        hutShell(x0, z0, 6, 5, base, 3);
        layout.set(x0 + 1, base + 1, z0 + 1, BlockType.WORKBENCH);
        layout.set(x0 + 4, base + 1, z0 + 1, BlockType.FURNACE);
        layout.set(x0 + 2, base + 1, z0 + 1, BlockType.ANVIL);
        s.dutyPoints.add(new Vec3i(x0 + 3, base + 1, z0 + 3));
    }

    private void medicHut(int x0, int z0) {
        int base = pad(x0, z0, 5, 5);
        hutShell(x0, z0, 5, 5, base, 3);
        layout.set(x0 + 1, base + 1, z0 + 1, BlockType.HERB_STATION);
        layout.set(x0 + 3, base + 1, z0 + 1, BlockType.CAMP_BED);
        s.beds.add(new Vec3i(x0 + 3, base + 1, z0 + 1));
        s.dutyPoints.add(new Vec3i(x0 + 2, base + 1, z0 + 2));
    }

    private void traderStall(int x0, int z0) {
        int base = pad(x0, z0, 4, 3);
        for (int x = x0; x < x0 + 4; x++) {
            layout.set(x, base + 3, z0 + 1, pal.roof);
        }
        layout.set(x0, base + 1, z0, pal.wall);
        layout.set(x0, base + 2, z0, pal.wall);
        layout.set(x0 + 3, base + 1, z0, pal.wall);
        layout.set(x0 + 3, base + 2, z0, pal.wall);
        crate(x0 + 1, z0, CRATE_SUPPLY);
        s.dutyPoints.add(new Vec3i(x0 + 2, base + 1, z0 + 1));
    }

    private void farmPlot(int x0, int z0) {
        int base = pad(x0, z0, 6, 5);
        for (int x = x0; x < x0 + 6; x++) {
            for (int z = z0; z < z0 + 5; z++) {
                layout.set(x, base, z, BlockType.DIRT);
                if ((x + z) % 2 == 0) {
                    layout.set(x, base + 1, z,
                            rng.nextFloat() < 0.5f ? BlockType.BERRY_BUSH : BlockType.HERB_PLANT);
                }
            }
        }
        s.dutyPoints.add(new Vec3i(x0 + 3, base + 1, z0 + 2));
    }

    private void well(int x0, int z0) {
        int base = pad(x0, z0, 3, 3);
        for (int x = x0; x < x0 + 3; x++) {
            for (int z = z0; z < z0 + 3; z++) {
                boolean edge = x == x0 || x == x0 + 2 || z == z0 || z == z0 + 2;
                layout.set(x, base, z, BlockType.STONE);
                if (edge) {
                    layout.set(x, base + 1, z, BlockType.STONE);
                } else {
                    layout.set(x, base + 1, z, BlockType.WATER);
                }
            }
        }
    }

    private void campfireArea(int cx, int cz) {
        int base = pad(cx - 2, cz - 2, 5, 5);
        layout.set(cx, base + 1, cz, BlockType.CAMPFIRE);
        layout.campfires.put(new Vec3i(cx, base + 1, cz), 900f);
        layout.set(cx - 2, base + 1, cz - 1, BlockType.LOG);
        layout.set(cx + 2, base + 1, cz + 1, BlockType.LOG);
        s.dutyPoints.add(new Vec3i(cx + 1, base + 1, cz + 1));
    }

    private void barracks(int x0, int z0, int beds) {
        int w = 7, d = 9;
        int base = pad(x0, z0, w, d);
        hutShell(x0, z0, w, d, base, 3);
        for (int i = 0; i < beds; i++) {
            int bx = x0 + 1 + (i % 2) * (w - 3);
            int bz = z0 + 1 + (i / 2) * 2;
            layout.set(bx, base + 1, bz, BlockType.CAMP_BED);
            s.beds.add(new Vec3i(bx, base + 1, bz));
        }
        layout.set(x0 + w / 2, base + 1, z0 + 1, BlockType.TORCH);
        s.dutyPoints.add(new Vec3i(x0 + w / 2, base + 1, z0 + d));
    }

    private void armory(int x0, int z0) {
        int base = pad(x0, z0, 4, 4);
        hutShell(x0, z0, 4, 4, base, 3);
        crate(x0 + 1, z0 + 1, CRATE_ARMORY);
        s.dutyPoints.add(new Vec3i(x0 + 2, base + 1, z0 + 4));
    }

    private void prisonCage(int x0, int z0) {
        int base = pad(x0, z0, 4, 4);
        for (int x = x0; x < x0 + 4; x++) {
            for (int z = z0; z < z0 + 4; z++) {
                boolean edge = x == x0 || x == x0 + 3 || z == z0 || z == z0 + 3;
                layout.set(x, base, z, BlockType.STONE);
                if (edge) {
                    for (int y = 1; y <= 2; y++) {
                        layout.set(x, base + y, z, BlockType.CAGE_BARS);
                    }
                }
                layout.set(x, base + 3, z, BlockType.CAGE_BARS);
            }
        }
        s.prisonPos = new Vec3i(x0 + 1, base + 1, z0 + 1);
        layout.set(x0 + 1, base + 1, z0 + 2, BlockType.BONE_PILE);
    }

    private void powderMagazine(int x0, int z0) {
        int base = pad(x0, z0, 5, 5);
        hutShell(x0, z0, 5, 5, base, 3);
        layout.set(x0 + 1, base + 1, z0 + 1, BlockType.POWDER_KEG);
        layout.set(x0 + 3, base + 1, z0 + 1, BlockType.POWDER_KEG);
        layout.set(x0 + 1, base + 1, z0 + 3, BlockType.POWDER_KEG);
        crate(x0 + 3, z0 + 3, CRATE_POWDER);
        s.magazinePos = new Vec3i(x0 + 2, base + 1, z0 + 2);
    }

    /** Two-story leader keep with an armory and the leader's post. */
    private void keep(int cx, int cz, int size, int floors) {
        int half = size / 2;
        int x0 = cx - half, z0 = cz - half;
        int base = pad(x0, z0, size, size);
        BlockType wall = BlockType.STONE_BRICK;
        for (int f = 0; f < floors; f++) {
            int fy = base + f * 4;
            for (int x = x0; x < x0 + size; x++) {
                for (int z = z0; z < z0 + size; z++) {
                    boolean edge = x == x0 || x == x0 + size - 1 || z == z0 || z == z0 + size - 1;
                    layout.set(x, fy, z, f == 0 ? pal.floor : BlockType.PLANK);
                    if (edge) {
                        for (int y = 1; y <= 3; y++) {
                            layout.set(x, fy + y, z, wall);
                        }
                    }
                }
            }
        }
        int roofY = base + floors * 4;
        for (int x = x0; x < x0 + size; x++) {
            for (int z = z0; z < z0 + size; z++) {
                layout.set(x, roofY, z, wall);
            }
        }
        // Door and interior ladder between floors.
        int door = cx;
        layout.set(door, base + 1, z0 + size - 1, BlockType.AIR);
        layout.set(door, base + 2, z0 + size - 1, BlockType.AIR);
        for (int y = 1; y <= floors * 4; y++) {
            layout.set(x0 + 1, base + y, z0 + 1, BlockType.LADDER);
        }
        layout.set(x0 + 2, base + 1, z0 + 1, BlockType.AIR);
        layout.set(cx, base + 1, cz, BlockType.TORCH);
        // Leader holds the top floor; armory crate beside the post.
        int topY = base + (floors - 1) * 4 + 1;
        s.leaderPost = new Vec3i(cx, topY, cz);
        Vec3i armoryPos = new Vec3i(cx + 2, topY, cz + 2);
        layout.set(armoryPos.x(), armoryPos.y(), armoryPos.z(), BlockType.CRATE);
        layout.crates.put(armoryPos,
                s.type == SettlementType.FORTRESS || s.type == SettlementType.CASTLE
                        ? CRATE_RELIC : CRATE_ARMORY);
        s.dutyPoints.add(s.leaderPost);
    }

    private void hutShell(int x0, int z0, int w, int d, int base, int height) {
        for (int x = x0; x < x0 + w; x++) {
            for (int z = z0; z < z0 + d; z++) {
                layout.set(x, base, z, pal.floor);
                boolean edge = x == x0 || x == x0 + w - 1 || z == z0 || z == z0 + d - 1;
                if (edge) {
                    for (int y = 1; y <= height; y++) {
                        layout.set(x, base + y, z, pal.wall);
                    }
                }
                layout.set(x, base + height + 1, z, pal.roof);
            }
        }
        int door = x0 + w / 2;
        layout.set(door, base + 1, z0 + d - 1, BlockType.AIR);
        layout.set(door, base + 2, z0 + d - 1, BlockType.AIR);
        doorwayApproach(door, z0 + d - 1, base);
    }

    /**
     * Joins a level building pad to the terrain south of its doorway one
     * block at a time. Without this short graded approach, a structure on the
     * allowed settlement slope can have a three-or-more-block threshold: an
     * NPC can drop outside, then become trapped beneath the raised floor.
     */
    private void doorwayApproach(int x, int doorwayZ, int floorY) {
        int walkY = floorY;
        for (int step = 1; step <= 10; step++) {
            int z = doorwayZ + step;
            int terrainY = groundY(x, z);
            // The first exterior cell is a level landing matching the
            // threshold (and the duty coordinates recorded by several
            // modules); grading starts beyond it.
            if (step > 1) {
                walkY += Integer.signum(terrainY - walkY);
            }

            for (int y = Math.min(terrainY, walkY); y <= walkY; y++) {
                layout.set(x, y, z, pal.foundation);
            }
            layout.set(x, walkY, z, pal.path);
            layout.set(x, walkY + 1, z, BlockType.AIR);
            layout.set(x, walkY + 2, z, BlockType.AIR);
            layout.set(x, walkY + 3, z, BlockType.AIR);

            if (walkY == terrainY && step >= 3) {
                break;
            }
        }
    }

    private void crate(int x, int z, int kind) {
        int y = groundY(x, z) + 1;
        // If a pad already leveled this spot, sit on the pad instead.
        Byte existing = layout.blocks.get(pack(x, y - 1, z));
        if (existing != null && BlockType.byId(existing) == BlockType.AIR) {
            for (int yy = y - 1; yy > 1; yy--) {
                Byte b = layout.blocks.get(pack(x, yy, z));
                if (b == null || BlockType.byId(b) != BlockType.AIR) {
                    y = yy + 1;
                    break;
                }
            }
        }
        layout.set(x, y, z, BlockType.CRATE);
        layout.crates.put(new Vec3i(x, y, z), kind);
    }

    /** Perimeter wall ring that follows the terrain (stepped, never floating). */
    private void wallRing(int cx, int cz, int r, int height, BlockType material) {
        for (int d = -r; d <= r; d++) {
            wallColumn(cx + d, cz - r, height, material);
            wallColumn(cx + d, cz + r, height, material);
            if (Math.abs(d) < r) {
                wallColumn(cx - r, cz + d, height, material);
                wallColumn(cx + r, cz + d, height, material);
            }
        }
    }

    private void wallColumn(int x, int z, int height, BlockType material) {
        int g = groundY(x, z);
        for (int y = g + 1; y <= g + height; y++) {
            layout.set(x, y, z, material);
        }
        // Clear overhanging vegetation above the parapet.
        for (int y = g + height + 1; y <= g + height + 3; y++) {
            layout.set(x, y, z, BlockType.AIR);
        }
    }

    /** Gate opening in the south wall: 2 wide, 2 tall, with GATE blocks. */
    private void gate(int cx, int gateZ, boolean outer) {
        int g = groundY(cx, gateZ);
        for (int dx = 0; dx <= 1; dx++) {
            for (int y = g + 1; y <= g + 2; y++) {
                layout.set(cx + dx, y, gateZ, BlockType.GATE);
            }
            // Keep a third clearance cell above the two-block gate. The stepped
            // terrain immediately inside a wall can be one block higher than
            // the gate column; a header at g+3 made a legitimately breached
            // doorway too short for the player to step through.
            layout.set(cx + dx, g + 3, gateZ, BlockType.AIR);
            for (int y = g + 4; y <= g + 5; y++) {
                layout.set(cx + dx, y, gateZ, pal.fence);
            }
        }
        Vec3i gatePos = new Vec3i(cx, g + 1, gateZ);
        s.gates.add(gatePos);
    }

    private void gateTowers(int cx, int gateZ) {
        watchtower(cx - 3, gateZ - 2);
        watchtower(cx + 4, gateZ - 2);
    }

    /** Guard tower: solid pillar, platform, parapet, torch, ladder access. */
    private void watchtower(int x, int z) {
        int g = groundY(x, z);
        int h = 5;
        for (int y = g + 1; y <= g + h; y++) {
            layout.set(x, y, z, pal.wall);
            layout.set(x + 1, y, z, pal.wall);
            layout.set(x, y, z + 1, pal.wall);
            layout.set(x + 1, y, z + 1, pal.wall);
        }
        for (int dx = -1; dx <= 2; dx++) {
            for (int dz = -1; dz <= 2; dz++) {
                layout.set(x + dx, g + h + 1, z + dz, BlockType.PLANK);
            }
        }
        layout.set(x - 1, g + h + 2, z - 1, BlockType.TORCH);
        for (int y = g + 1; y <= g + h + 1; y++) {
            layout.set(x - 1, y, z, BlockType.LADDER);
        }
        s.dutyPoints.add(new Vec3i(x, g + h + 2, z));
    }

    private void alarmBell(int x, int z) {
        int g = groundY(x, z);
        layout.set(x, g + 1, z, BlockType.ALARM_BELL);
        Vec3i pos = new Vec3i(x, g + 1, z);
        if (s.alarmBell == null) {
            s.alarmBell = pos;
        }
    }

    /** Narrow rear infiltration gap left in a fortress outer wall. */
    private void posternGap(int x, int z) {
        int g = groundY(x, z);
        layout.set(x, g + 1, z, BlockType.AIR);
        layout.set(x, g + 2, z, BlockType.AIR);
        layout.infiltrationPoints.add(new Vec3i(x, g + 1, z));
    }

    /** Straight L-shaped path connecting two points along the surface. */
    private void path(int x0, int z0, int x1, int z1) {
        int x = x0, z = z0;
        while (x != x1) {
            pathCell(x, z);
            x += Integer.signum(x1 - x);
        }
        while (z != z1) {
            pathCell(x, z);
            z += Integer.signum(z1 - z);
        }
        pathCell(x1, z1);
    }

    private void pathCell(int x, int z) {
        int g = groundY(x, z);
        // Don't punch paths through building floors already laid.
        long key = pack(x, g, z);
        Byte existing = layout.blocks.get(key);
        if (existing == null) {
            layout.set(x, g, z, pal.path);
            layout.set(x, g + 1, z, BlockType.AIR);
        }
    }

    private void patrolSquare(int cx, int cz, int r) {
        int[][] corners = {{-r, -r}, {r, -r}, {r, r}, {-r, r}};
        for (int[] c : corners) {
            int x = cx + c[0], z = cz + c[1];
            s.patrolPoints.add(new Vec3i(x, groundY(x, z) + 1, z));
        }
    }

    // ------------------------------------------------------------------
    // Crate stocking (deterministic per position)
    // ------------------------------------------------------------------

    /** Rolls a settlement crate's contents; pure function of (seed, pos, kind). */
    public static com.veylon.item.Inventory rollCrate(long worldSeed, Vec3i pos, int kind) {
        Random rng = new Random(Noise.mix(worldSeed ^ pack(pos.x(), pos.y(), pos.z()) ^ 0x435254L));
        com.veylon.item.Inventory inv = new com.veylon.item.Inventory(12);
        switch (kind) {
            case CRATE_ARMORY -> {
                inv.add(com.veylon.item.ItemType.ARROW, 4 + rng.nextInt(8));
                inv.add(com.veylon.item.ItemType.IRON_INGOT, 1 + rng.nextInt(2));
                if (rng.nextFloat() < 0.5f) {
                    inv.add(com.veylon.item.ItemType.IRON_ARROW, 2 + rng.nextInt(4));
                }
                if (rng.nextFloat() < 0.35f) {
                    inv.add(com.veylon.item.ItemType.MUSKET_BALL, 2 + rng.nextInt(4));
                }
                if (rng.nextFloat() < 0.25f) {
                    inv.add(com.veylon.item.ItemType.SPEAR, 1);
                }
            }
            case CRATE_POWDER -> {
                inv.add(com.veylon.item.ItemType.BLACK_POWDER, 2 + rng.nextInt(4));
                inv.add(com.veylon.item.ItemType.SULFUR, 1 + rng.nextInt(3));
                if (rng.nextFloat() < 0.4f) {
                    inv.add(com.veylon.item.ItemType.SCRAP_BOMB, 1);
                }
            }
            case CRATE_RELIC -> {
                // Extremely rare relic weapons live only in castle/fortress armories.
                float roll = rng.nextFloat();
                if (roll < 0.30f) {
                    inv.add(com.veylon.item.ItemType.RELIC_CARBINE, 1);
                } else if (roll < 0.45f) {
                    inv.add(com.veylon.item.ItemType.RELIC_RIFLE, 1);
                }
                inv.add(com.veylon.item.ItemType.RIFLE_CARTRIDGE, 8 + rng.nextInt(12));
                inv.add(com.veylon.item.ItemType.RELIC_PARTS, 1 + rng.nextInt(2));
                inv.add(com.veylon.item.ItemType.MUSKET_BALL, 3 + rng.nextInt(5));
                // Relics survive as recoverable mechanisms, not pristine free upgrades.
                // Use a separate position hash so adding condition does not perturb the
                // established weapon/ammunition loot rolls.
                long conditionBits = Noise.mix(worldSeed
                        ^ pack(pos.x(), pos.y(), pos.z()) ^ 0x52454c4943434f4eL);
                float condition = 0.18f
                        + ((conditionBits >>> 40) & 0xFFFFL) / 65535f * 0.24f;
                for (int i = 0; i < inv.size(); i++) {
                    com.veylon.item.ItemStack stack = inv.get(i);
                    if (stack != null && (stack.type == com.veylon.item.ItemType.RELIC_CARBINE
                            || stack.type == com.veylon.item.ItemType.RELIC_RIFLE)) {
                        stack.durability = Math.max(1f, stack.type.maxDurability * condition);
                    }
                }
            }
            default -> {
                inv.add(com.veylon.item.ItemType.DRIED_MEAT, 1 + rng.nextInt(3));
                inv.add(com.veylon.item.ItemType.FIBER, 2 + rng.nextInt(4));
                if (rng.nextFloat() < 0.5f) {
                    inv.add(com.veylon.item.ItemType.BANDAGE, 1 + rng.nextInt(2));
                }
                if (rng.nextFloat() < 0.3f) {
                    inv.add(com.veylon.item.ItemType.COAL, 2);
                }
                if (rng.nextFloat() < 0.15f) {
                    inv.add(com.veylon.item.ItemType.BLUEPRINT_FRAGMENT, 1);
                }
            }
        }
        return inv;
    }
}
