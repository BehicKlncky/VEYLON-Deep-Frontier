package com.veylon.world;

import com.veylon.ai.Pathfinder;
import com.veylon.settlement.SettlementPlanner;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Deep-frontier cave generation: determinism, depth zones, lake protection. */
class DeepCaveGenTest {

    private static final long SEED = 987654321L;

    @Test
    void sameSeedProducesIdenticalDeepChunks() {
        World w1 = new World(SEED, World.GEN_DEEP);
        World w2 = new World(SEED, World.GEN_DEEP);
        for (int cx = -2; cx <= 2; cx++) {
            for (int cz = -2; cz <= 2; cz++) {
                Chunk c1 = w1.getOrCreateChunk(cx, cz);
                Chunk c2 = w2.getOrCreateChunk(cx, cz);
                for (int y = 0; y < Chunk.SY; y++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            assertEquals(c1.get(lx, y, lz), c2.get(lx, y, lz),
                                    "chunk " + cx + "," + cz + " differs at "
                                            + lx + "," + y + "," + lz);
                        }
                    }
                }
            }
        }
    }

    @Test
    void legacyWorldsKeepLegacyTerrain() {
        // A legacy world and a deep world share surface terrain but the legacy
        // one must contain none of the new deep-frontier blocks.
        World legacy = new World(SEED, World.GEN_LEGACY);
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                Chunk c = legacy.getOrCreateChunk(cx, cz);
                for (int y = 0; y < Chunk.SY; y++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            BlockType t = c.get(lx, y, lz);
                            assertTrue(t != BlockType.BASALT && t != BlockType.SULFUR_ORE
                                            && t != BlockType.SALTPETER_ORE && t != BlockType.GLOW_FUNGUS
                                            && t != BlockType.STONE_BRICK && t != BlockType.GATE,
                                    "legacy world contains new-generator block " + t);
                        }
                    }
                }
            }
        }
        assertTrue(legacy.settlements.isEmpty(), "legacy worlds never plan settlements");
    }

    @Test
    void deepWorldsContainPowderResourcesAndCaveLife() {
        World w = new World(SEED, World.GEN_DEEP);
        int sulfur = 0, saltpeter = 0, basalt = 0, fungus = 0, air = 0;
        for (int cx = -6; cx <= 6; cx++) {
            for (int cz = -6; cz <= 6; cz++) {
                Chunk c = w.getOrCreateChunk(cx, cz);
                for (int y = 2; y < 40; y++) {
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            switch (c.get(lx, y, lz)) {
                                case SULFUR_ORE -> sulfur++;
                                case SALTPETER_ORE -> saltpeter++;
                                case BASALT -> basalt++;
                                case GLOW_FUNGUS -> fungus++;
                                case AIR -> air++;
                                default -> {
                                }
                            }
                        }
                    }
                }
            }
        }
        assertTrue(sulfur > 0, "sulfur must generate in the depths");
        assertTrue(saltpeter > 0, "saltpeter must generate in the depths");
        assertTrue(basalt > 1000, "basalt depth identity must exist");
        assertTrue(fungus > 0, "cave life (glow fungus) must exist");
        assertTrue(air > 500, "caves must actually be carved");
    }

    @Test
    void lakesAreNeverCarvedOpenFromBelow() {
        World w = new World(SEED, World.GEN_DEEP);
        int lakes = 0;
        for (int cx = -8; cx <= 8 && lakes < 40; cx++) {
            for (int cz = -8; cz <= 8 && lakes < 40; cz++) {
                Chunk c = w.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        int wx = cx * 16 + lx, wz = cz * 16 + lz;
                        int h = w.generator.heightAt(wx, wz);
                        if (h >= World.SEA_LEVEL) {
                            continue;
                        }
                        lakes++;
                        // The 8 blocks directly beneath a lake bed stay solid.
                        for (int y = Math.max(3, h - 7); y <= h; y++) {
                            assertTrue(c.get(lx, y, lz) != BlockType.AIR,
                                    "lake floor carved open at " + wx + "," + y + "," + wz);
                        }
                    }
                }
            }
        }
        assertTrue(lakes > 0, "test area should contain lake columns");
    }

    @Test
    void cavesAreBoundedNotOneGiantVoid() {
        World w = new World(SEED, World.GEN_DEEP);
        // Sample columns: carved fraction below the surface stays within sane
        // bounds so meshes and navigation stay bounded.
        int carved = 0, total = 0;
        for (int cx = -4; cx <= 4; cx++) {
            for (int cz = -4; cz <= 4; cz++) {
                Chunk c = w.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx += 4) {
                    for (int lz = 0; lz < 16; lz += 4) {
                        int wx = cx * 16 + lx, wz = cz * 16 + lz;
                        int h = w.generator.heightAt(wx, wz);
                        for (int y = 4; y < h - 4; y++) {
                            total++;
                            if (c.get(lx, y, lz) == BlockType.AIR) {
                                carved++;
                            }
                        }
                    }
                }
            }
        }
        double frac = carved / (double) Math.max(1, total);
        assertTrue(frac > 0.03, "underground should be meaningfully carved: " + frac);
        assertTrue(frac < 0.45, "underground must not be one huge cavern: " + frac);
    }

    @Test
    void everyGeneratedEntranceIsClimbableFromSurfaceToTargetDepth() {
        World w = new World(SEED, World.GEN_DEEP);
        for (int[] region : new int[][]{{0, 0}, {-1, 0}, {0, -1}, {-2, -2}, {2, 1}}) {
            Vec3i mouth = SettlementPlanner.caveEntrance(
                    SEED, w.generator, region[0], region[1]);
            assertNotNull(mouth);
            w.getOrCreateChunk(Math.floorDiv(mouth.x(), 16), Math.floorDiv(mouth.z(), 16));
            assertEquals(BlockType.LADDER, w.getBlock(mouth.x(), mouth.y() + 1, mouth.z()),
                    "the planned entrance survives chunk generation");
            assertEquals(BlockType.LADDER,
                    w.getBlock(mouth.x(), SettlementPlanner.CAVE_TARGET_Y, mouth.z()));
            List<Vec3i> path = Pathfinder.find(w,
                    mouth.x(), mouth.y() + 1, mouth.z(),
                    mouth.x(), SettlementPlanner.CAVE_TARGET_Y, mouth.z(), 2_000);
            assertNotNull(path, "player/NPC movement rules must traverse the entrance ladder");
            assertTrue(path.size() >= mouth.y() - SettlementPlanner.CAVE_TARGET_Y);
        }
    }

    @Test
    void entranceBlocksAreIndependentOfReverseChunkGenerationOrder() {
        World forward = new World(SEED, World.GEN_DEEP);
        World reverse = new World(SEED, World.GEN_DEEP);
        Vec3i mouth = SettlementPlanner.caveEntrance(SEED, forward.generator, -1, -1);
        int cx = Math.floorDiv(mouth.x(), 16), cz = Math.floorDiv(mouth.z(), 16);
        List<int[]> chunks = new ArrayList<>();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                chunks.add(new int[]{cx + dx, cz + dz});
            }
        }
        for (int[] pos : chunks) {
            forward.getOrCreateChunk(pos[0], pos[1]);
        }
        for (int i = chunks.size() - 1; i >= 0; i--) {
            int[] pos = chunks.get(i);
            reverse.getOrCreateChunk(pos[0], pos[1]);
        }
        for (int x = mouth.x() - 3; x <= mouth.x() + 3; x++) {
            for (int z = mouth.z() - 3; z <= mouth.z() + 3; z++) {
                for (int y = SettlementPlanner.CAVE_TARGET_Y - 1; y <= mouth.y() + 2; y++) {
                    assertEquals(forward.getBlock(x, y, z), reverse.getBlock(x, y, z),
                            "reverse order changed entrance cell " + x + "," + y + "," + z);
                }
            }
        }
    }

    @Test
    void requiredUndergroundPoiHasBoundedReachableOrderIndependentConnector() {
        World probe = new World(SEED, World.GEN_DEEP);
        Vec3i mouth = null;
        Poi target = null;
        int targetRx = 0, targetRz = 0;
        int bestDistance = Integer.MAX_VALUE;
        for (int rx = -2; rx <= 2; rx++) {
            for (int rz = -2; rz <= 2; rz++) {
                Vec3i candidateMouth = SettlementPlanner.caveEntrance(SEED, probe.generator, rx, rz);
                for (Poi poi : probe.generator.plannedUndergroundPois(rx, rz)) {
                    int distance = Math.abs(candidateMouth.x() - poi.pos.x())
                            + Math.abs(candidateMouth.z() - poi.pos.z());
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        target = poi;
                        mouth = candidateMouth;
                        targetRx = rx;
                        targetRz = rz;
                    }
                }
            }
        }
        assertNotNull(target, "fixed seed must plan at least one cave POI");
        assertTrue(WorldGenerator.connectorEditUpperBound(mouth, target.pos)
                        <= WorldGenerator.MAX_POI_CONNECTOR_EDITS,
                "connector carve volume has a defined hard upper bound");
        var settlement = probe.settlementForRegion(targetRx, targetRz);
        if (settlement != null) {
            assertTrue(Math.abs(target.pos.x() - settlement.center.x()) > settlement.radius + 3
                            || Math.abs(target.pos.z() - settlement.center.z()) > settlement.radius + 3,
                    "POI chamber cannot overlap protected settlement bounds");
        }

        int minCx = Math.floorDiv(Math.min(mouth.x(), target.pos.x() - 2), 16) - 1;
        int maxCx = Math.floorDiv(Math.max(mouth.x(), target.pos.x()), 16) + 1;
        int minCz = Math.floorDiv(Math.min(mouth.z(), target.pos.z()), 16) - 1;
        int maxCz = Math.floorDiv(Math.max(mouth.z(), target.pos.z()), 16) + 1;
        List<int[]> chunks = new ArrayList<>();
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        World forward = new World(SEED, World.GEN_DEEP);
        World reverse = new World(SEED, World.GEN_DEEP);
        for (int[] pos : chunks) {
            forward.getOrCreateChunk(pos[0], pos[1]);
        }
        for (int i = chunks.size() - 1; i >= 0; i--) {
            int[] pos = chunks.get(i);
            reverse.getOrCreateChunk(pos[0], pos[1]);
        }

        Vec3i start = new Vec3i(mouth.x(), mouth.y() + 1, mouth.z());
        Vec3i goal = new Vec3i(target.pos.x() - 2, target.pos.y(), target.pos.z());
        assertTrue(reachableByMovementRules(forward, start, goal, minCx, maxCx, minCz, maxCz),
                "regional entrance must reach the required POI without mining");
        assertTrue(reachableByMovementRules(reverse, start, goal, minCx, maxCx, minCz, maxCz));
        Vec3i targetPos = target.pos;
        assertTrue(forward.pois.stream().anyMatch(p -> p.pos.equals(targetPos)),
                "POI is registered after actual chunk generation");

        int maxY = Math.max(35, target.pos.y() + 4);
        for (int[] pos : chunks) {
            Chunk a = forward.getChunk(pos[0], pos[1]);
            Chunk b = reverse.getChunk(pos[0], pos[1]);
            for (int lx = 0; lx < Chunk.SX; lx++) {
                for (int lz = 0; lz < Chunk.SZ; lz++) {
                    for (int y = SettlementPlanner.CAVE_TARGET_Y - 1; y <= maxY; y++) {
                        assertEquals(a.get(lx, y, lz), b.get(lx, y, lz),
                                "connector/POI differs under reversed generation order");
                    }
                }
            }
        }
    }

    private static boolean reachableByMovementRules(World world, Vec3i start, Vec3i goal,
                                                     int minCx, int maxCx,
                                                     int minCz, int maxCz) {
        ArrayDeque<Vec3i> open = new ArrayDeque<>();
        Set<Vec3i> seen = new HashSet<>();
        open.add(start);
        seen.add(start);
        int expansions = 0;
        while (!open.isEmpty() && expansions++ < 120_000) {
            Vec3i cur = open.removeFirst();
            if (cur.distSq(goal.x() + 0.5, goal.y() + 0.5, goal.z() + 0.5) < 2.1) {
                return true;
            }
            if (world.getBlock(cur.x(), cur.y(), cur.z()).isClimbable()) {
                addIfStandable(world, open, seen, cur.offset(0, 1, 0),
                        minCx, maxCx, minCz, maxCz);
                addIfStandable(world, open, seen, cur.offset(0, -1, 0),
                        minCx, maxCx, minCz, maxCz);
            }
            for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                int nx = cur.x() + dir[0], nz = cur.z() + dir[1];
                if (Pathfinder.standable(world, nx, cur.y(), nz)) {
                    addIfStandable(world, open, seen, new Vec3i(nx, cur.y(), nz),
                            minCx, maxCx, minCz, maxCz);
                } else if (Pathfinder.standable(world, nx, cur.y() + 1, nz)) {
                    addIfStandable(world, open, seen, new Vec3i(nx, cur.y() + 1, nz),
                            minCx, maxCx, minCz, maxCz);
                } else {
                    for (int drop = 1; drop <= Pathfinder.MAX_DROP; drop++) {
                        if (Pathfinder.standable(world, nx, cur.y() - drop, nz)) {
                            addIfStandable(world, open, seen,
                                    new Vec3i(nx, cur.y() - drop, nz),
                                    minCx, maxCx, minCz, maxCz);
                            break;
                        }
                    }
                }
            }
        }
        return false;
    }

    private static void addIfStandable(World world, ArrayDeque<Vec3i> open, Set<Vec3i> seen,
                                       Vec3i cell, int minCx, int maxCx,
                                       int minCz, int maxCz) {
        int cx = Math.floorDiv(cell.x(), 16), cz = Math.floorDiv(cell.z(), 16);
        if (cx < minCx || cx > maxCx || cz < minCz || cz > maxCz
                || cell.y() < 3 || cell.y() >= Chunk.SY - 2
                || !Pathfinder.standable(world, cell.x(), cell.y(), cell.z())) {
            return;
        }
        if (seen.add(cell)) {
            open.addLast(cell);
        }
    }
}
