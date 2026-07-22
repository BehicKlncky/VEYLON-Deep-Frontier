package com.veylon.ai;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bounded voxel A* for settlement NPCs: walkable-cell evaluation with 1-block
 * step-up, limited step-down, water cost, gate/ladder awareness and a strict
 * expansion budget. Never touches unloaded chunks. Callers cache the result
 * and fall back to direct steering when no path is found.
 */
public final class Pathfinder {

    /** Default expansion budget per query (keeps worst case ~0.1 ms). */
    public static final int DEFAULT_BUDGET = 700;
    /** Maximum straight-line range accepted for a query. */
    public static final int MAX_RANGE = 56;
    /** Deepest safe drop while following a path. */
    public static final int MAX_DROP = 3;
    /** Defensive ceiling for a reconstructed cached path. */
    public static final int MAX_PATH_NODES = 512;

    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private record Node(long key, float g, float f) {
    }

    private Pathfinder() {
    }

    private static long key(int x, int y, int z) {
        return ((x & 0x3FFFFFFL) << 38) | ((z & 0x3FFFFFFL) << 12) | (y & 0xFFFL);
    }

    private static int kx(long k) {
        return (int) (k >> 38);
    }

    private static int kz(long k) {
        return (int) (k << 26 >> 38);
    }

    private static int ky(long k) {
        return (int) (k & 0xFFFL);
    }

    /** True when the cell can contain a standing NPC body (feet cell). */
    private static boolean passable(World w, int x, int y, int z) {
        BlockType t = w.getBlock(x, y, z);
        if (t == BlockType.GATE) {
            return true; // NPCs shoulder gates open; cost handled below
        }
        return !t.solid || t.isClimbable();
    }

    private static boolean chunkLoaded(World w, int x, int z) {
        return w.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) != null;
    }

    /** A cell an NPC can stand in: 2-high clearance over solid ground. */
    public static boolean standable(World w, int x, int y, int z) {
        if (!chunkLoaded(w, x, z)) {
            return false;
        }
        if (!passable(w, x, y, z) || !passable(w, x, y + 1, z)) {
            return false;
        }
        BlockType below = w.getBlock(x, y - 1, z);
        BlockType feet = w.getBlock(x, y, z);
        return below.solid || feet.isClimbable() || feet == BlockType.WATER;
    }

    private static float moveCost(World w, int x, int y, int z) {
        BlockType feet = w.getBlock(x, y, z);
        if (feet == BlockType.WATER) {
            return 4f;
        }
        if (feet == BlockType.GATE || w.getBlock(x, y + 1, z) == BlockType.GATE) {
            return 2.5f;
        }
        return 1f;
    }

    /**
     * Finds a path of standable cells from start to goal, or null. Both ends
     * snap down up to 2 cells to find footing. Budgeted; never exhaustive.
     */
    public static List<Vec3i> find(World w, int sx, int sy, int sz,
                                   int tx, int ty, int tz, int budget) {
        int dx = tx - sx, dz = tz - sz;
        if (dx * dx + dz * dz > MAX_RANGE * MAX_RANGE) {
            return null;
        }
        sy = snapDown(w, sx, sy, sz);
        ty = snapDown(w, tx, ty, tz);
        if (sy < 0 || ty < 0) {
            return null;
        }
        long startKey = key(sx, sy, sz);
        long goalKey = key(tx, ty, tz);
        if (startKey == goalKey) {
            return List.of(new Vec3i(tx, ty, tz));
        }

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
        Map<Long, Float> gScore = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        gScore.put(startKey, 0f);
        open.add(new Node(startKey, 0f, heuristic(sx, sy, sz, tx, ty, tz)));

        int expansions = 0;
        while (!open.isEmpty() && expansions < budget) {
            Node cur = open.poll();
            if (cur.g > gScore.getOrDefault(cur.key, Float.MAX_VALUE)) {
                continue; // stale entry
            }
            expansions++;
            int cx = kx(cur.key), cy = ky(cur.key), cz = kz(cur.key);
            if (cur.key == goalKey
                    || (Math.abs(cx - tx) + Math.abs(cz - tz) <= 1 && Math.abs(cy - ty) <= 1)) {
                return reconstruct(parent, cur.key, startKey);
            }

            // Ladder cells allow direct vertical movement.
            if (w.getBlock(cx, cy, cz).isClimbable()) {
                relax(w, open, gScore, parent, cur, cx, cy + 1, cz, tx, ty, tz, 1.2f);
                relax(w, open, gScore, parent, cur, cx, cy - 1, cz, tx, ty, tz, 1.2f);
            }

            for (int[] d : DIRS) {
                int nx = cx + d[0], nz = cz + d[1];
                if (!chunkLoaded(w, nx, nz)) {
                    continue;
                }
                // Same level, step up 1, or step down up to MAX_DROP.
                if (standable(w, nx, cy, nz)) {
                    relax(w, open, gScore, parent, cur, nx, cy, nz, tx, ty, tz, 0f);
                } else if (standable(w, nx, cy + 1, nz) && passable(w, cx, cy + 2, cz)) {
                    relax(w, open, gScore, parent, cur, nx, cy + 1, nz, tx, ty, tz, 0.6f);
                } else {
                    for (int drop = 1; drop <= MAX_DROP; drop++) {
                        if (!passable(w, nx, cy - drop + 1, nz)) {
                            break;
                        }
                        if (standable(w, nx, cy - drop, nz)) {
                            relax(w, open, gScore, parent, cur, nx, cy - drop, nz,
                                    tx, ty, tz, drop * 0.3f);
                            break;
                        }
                    }
                }
            }
        }
        return null;
    }

    private static void relax(World w, PriorityQueue<Node> open, Map<Long, Float> gScore,
                              Map<Long, Long> parent, Node cur, int nx, int ny, int nz,
                              int tx, int ty, int tz, float extra) {
        long nk = key(nx, ny, nz);
        float ng = cur.g + moveCost(w, nx, ny, nz) + extra;
        Float old = gScore.get(nk);
        if (old != null && old <= ng) {
            return;
        }
        gScore.put(nk, ng);
        parent.put(nk, cur.key);
        open.add(new Node(nk, ng, ng + heuristic(nx, ny, nz, tx, ty, tz)));
    }

    private static float heuristic(int x, int y, int z, int tx, int ty, int tz) {
        return Math.abs(tx - x) + Math.abs(tz - z) + Math.abs(ty - y) * 0.6f;
    }

    /**
     * Reconstructs only a complete end-to-start chain. The defensive cap is a
     * failure boundary: returning a reversed suffix would make an NPC follow a
     * path disconnected from its current position.
     */
    private static List<Vec3i> reconstruct(Map<Long, Long> parent, long end, long start) {
        ArrayList<Vec3i> out = new ArrayList<>();
        Long k = end;
        while (k != null && out.size() < MAX_PATH_NODES) {
            out.add(new Vec3i(kx(k), ky(k), kz(k)));
            if (k == start) {
                java.util.Collections.reverse(out);
                return out;
            }
            k = parent.get(k);
        }
        return null;
    }

    /** Snaps a cell downward to footing (max 3); -1 when none. */
    private static int snapDown(World w, int x, int y, int z) {
        for (int dy = 0; dy <= 3; dy++) {
            if (standable(w, x, y - dy, z)) {
                return y - dy;
            }
        }
        // Also allow snapping up one (target on a step).
        return standable(w, x, y + 1, z) ? y + 1 : -1;
    }
}
