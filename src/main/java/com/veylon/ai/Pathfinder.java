package com.veylon.ai;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.World;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bounded voxel A* for settlement NPCs: walkable-cell evaluation with 1-block
 * step-up, limited step-down, water cost, gate/ladder awareness and a strict
 * expansion budget shared by populations. No route means waiting safely.
 */
public final class Pathfinder {

    /** Default expansion budget per query; wall time depends on the host. */
    public static final int DEFAULT_BUDGET = 700;
    /** Maximum straight-line range accepted for a query. */
    public static final int MAX_RANGE = 56;
    /** Deepest safe drop while following a path. */
    public static final int MAX_DROP = com.veylon.world.VoxelClearance.MAX_DROP;
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

    /**
     * True when the cell can contain a standing NPC body (feet cell). Nobody
     * walks or stands in a fire: a torch's or campfire's cell is not passable,
     * so routes and the sleeping places beside beds keep out of the flames
     * that set a body alight.
     */
    static boolean passable(World w, int x, int y, int z) {
        return com.veylon.world.VoxelClearance.passable(w, x, y, z);
    }

    private static boolean chunkLoaded(World w, int x, int z) {
        return w.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)) != null;
    }

    /** A cell an NPC can stand in: 2-high clearance over solid ground. */
    public static boolean standable(World w, int x, int y, int z) {
        return com.veylon.world.VoxelClearance.standable(w, x, y, z);
    }

    private static float moveCost(World w, int x, int y, int z, boolean breach) {
        BlockType feet = w.getBlock(x, y, z);
        if (feet == BlockType.WATER) {
            return 4f;
        }
        BlockType head = w.getBlock(x, y + 1, z);
        if (breach && (lockedWood(w, x, y, z, feet) || lockedWood(w, x, y + 1, z, head))) {
            return BREACH_DOOR_COST;
        }
        if (feet == BlockType.GATE || head == BlockType.GATE
                || Fixtures.isClosedDoor(feet) || Fixtures.isClosedDoor(head)) {
            return 2.5f;
        }
        return 1f;
    }

    /** A closed wooden door held shut by a lock or a bar: a wall to everyone but a Breacher. */
    private static boolean lockedWood(World w, int x, int y, int z, BlockType b) {
        return b == BlockType.WOOD_DOOR && w.fixtureLockAt(x, y, z) != com.veylon.world.FixtureStates.LOCK_NONE;
    }

    /**
     * Step cost of a locked or barred wooden door on a Breacher's route: it would rather walk
     * a little further through an open one than stop to hammer.
     */
    public static final float BREACH_DOOR_COST = 8f;

    /**
     * Finds a path of standable cells from start to goal, or null. Both ends
     * snap at most one cell to find footing. Budgeted; never exhaustive.
     */
    public static List<Vec3i> find(World w, int sx, int sy, int sz,
                                   int tx, int ty, int tz, int budget) {
        return findBounded(w, sx, sy, sz, tx, ty, tz, budget, false);
    }

    /** A safe connected prefix may advance a long route when this tick's local search runs out. */
    public static List<Vec3i> findSegment(World w, int sx, int sy, int sz,
                                         int tx, int ty, int tz, int budget) {
        return findBounded(w, sx, sy, sz, tx, ty, tz, budget, true);
    }

    /**
     * A Breacher's segment (urban milestone 15): closed wooden doors count as passable whatever
     * their lock or bar, at {@link #BREACH_DOOR_COST} for a locked one; metal doors, glass and
     * walls still stop it. Its routes live in their own part of the cache, so no resident is ever
     * handed a route through a locked door. Same budget, range and connected-prefix rule.
     */
    public static List<Vec3i> findBreach(World w, int sx, int sy, int sz,
                                         int tx, int ty, int tz, int budget) {
        budget = w.navigation.budget.query(Math.min(budget, DEFAULT_BUDGET));
        if (budget <= 0) { return null; }
        List<Vec3i> result = search(w, breachAccess(w), true, sx, sy, sz, tx, ty, tz, budget, true);
        if (result == null) { w.navigation.budget.failed(); }
        return result;
    }

    private static List<Vec3i> findBounded(World w, int sx, int sy, int sz,
                                          int tx, int ty, int tz, int budget, boolean partial) {
        budget = w.navigation.budget.query(Math.min(budget, DEFAULT_BUDGET));
        if (budget <= 0) { return null; }
        List<Vec3i> result = search(w, w, false, sx, sy, sz, tx, ty, tz, budget, partial);
        if (result == null) { w.navigation.budget.failed(); }
        return result;
    }

    /**
     * @param v the voxels passability is judged in: the world itself, or for a Breacher a view
     *        of it in which closed wooden doors are unlocked
     */
    private static List<Vec3i> search(World w, com.veylon.world.VoxelAccess v, boolean breach,
                                      int sx, int sy, int sz,
                                      int tx, int ty, int tz, int budget, boolean partial) {
        long dx = (long) tx - sx, dz = (long) tz - sz;
        if (Math.abs(dx) > MAX_RANGE || Math.abs(dz) > MAX_RANGE
                || dx * dx + dz * dz > MAX_RANGE * MAX_RANGE) {
            return null;
        }
        sy = snapDown(v, sx, sy, sz);
        ty = snapDown(v, tx, ty, tz);
        if (sy < 0 || ty < 0) {
            return null;
        }
        Vec3i start = new Vec3i(sx, sy, sz), goal = new Vec3i(tx, ty, tz);
        List<Vec3i> cached = w.navigation.get(start, goal, breach);
        if (cached != null && cached.stream().allMatch(p -> w.isColumnLoaded(p.x(), p.z()))) {
            w.navigation.budget.hit();
            return cached;
        }
        long startKey = key(sx, sy, sz);
        long goalKey = key(tx, ty, tz);
        if (startKey == goalKey) {
            List<Vec3i> route = List.of(goal);
            w.navigation.put(start, goal, route, breach);
            return route;
        }
        com.veylon.world.VoxelAccess opened = traversalAccess(v);

        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Float.compare(a.f, b.f));
        Map<Long, Float> gScore = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        gScore.put(startKey, 0f);
        open.add(new Node(startKey, 0f, heuristic(sx, sy, sz, tx, ty, tz)));

        int expansions = 0;
        long nearest = startKey;
        float nearestDistance = heuristic(sx, sy, sz, tx, ty, tz);
        while (!open.isEmpty() && expansions < budget) {
            Node cur = open.poll();
            if (cur.g > gScore.getOrDefault(cur.key, Float.MAX_VALUE)) {
                continue; // stale entry
            }
            expansions++;
            w.navigation.budget.expanded();
            int cx = kx(cur.key), cy = ky(cur.key), cz = kz(cur.key);
            float remaining = heuristic(cx, cy, cz, tx, ty, tz);
            if (remaining < nearestDistance) { nearest = cur.key; nearestDistance = remaining; }
            if (cur.key == goalKey) {
                List<Vec3i> route = reconstruct(parent, cur.key, startKey);
                if (route != null) { w.navigation.put(start, goal, route, breach); }
                return route;
            }

            // Ladder cells allow direct vertical movement.
            if (w.getBlock(cx, cy, cz).isClimbable()) {
                relax(w, v, breach, opened, open, gScore, parent, cur, cx, cy + 1, cz, tx, ty, tz, 1.2f);
                relax(w, v, breach, opened, open, gScore, parent, cur, cx, cy - 1, cz, tx, ty, tz, 1.2f);
            }

            for (int[] d : DIRS) {
                int nx = cx + d[0], nz = cz + d[1];
                if (!chunkLoaded(w, nx, nz)) {
                    continue;
                }
                // Same level, step up 1, or step down up to MAX_DROP.
                if (com.veylon.world.VoxelClearance.standable(v, nx, cy, nz)) {
                    relax(w, v, breach, opened, open, gScore, parent, cur, nx, cy, nz, tx, ty, tz, 0f);
                } else if (com.veylon.world.VoxelClearance.standable(v, nx, cy + 1, nz)
                        && com.veylon.world.VoxelClearance.passable(v, cx, cy + 2, cz)) {
                    relax(w, v, breach, opened, open, gScore, parent, cur, nx, cy + 1, nz, tx, ty, tz, 0.6f);
                } else {
                    for (int drop = 1; drop <= MAX_DROP; drop++) {
                        if (!com.veylon.world.VoxelClearance.passable(v, nx, cy - drop + 1, nz)) {
                            break;
                        }
                        if (com.veylon.world.VoxelClearance.standable(v, nx, cy - drop, nz)) {
                            relax(w, v, breach, opened, open, gScore, parent, cur, nx, cy - drop, nz,
                                    tx, ty, tz, drop * 0.3f);
                            break;
                        }
                    }
                }
            }
        }
        if (partial && expansions == budget && nearest != startKey) {
            List<Vec3i> prefix = reconstruct(parent, nearest, startKey);
            if (prefix != null) {
                // This is complete to its own endpoint, never cached as a complete route to the original goal.
                w.navigation.put(start, prefix.getLast(), prefix, breach);
                w.navigation.budget.failed();
                return prefix;
            }
        }
        return null;
    }

    private static void relax(World w, com.veylon.world.VoxelAccess v, boolean breach,
                              com.veylon.world.VoxelAccess opened, PriorityQueue<Node> open, Map<Long, Float> gScore,
                              Map<Long, Long> parent, Node cur, int nx, int ny, int nz,
                              int tx, int ty, int tz, float extra) {
        int cx = kx(cur.key), cy = ky(cur.key), cz = kz(cur.key);
        if (nx == cx && nz == cz) {
            if (ny < 1 || ny + 1 >= com.veylon.world.Chunk.SY
                    || !com.veylon.world.VoxelClearance.standable(v, nx, ny, nz)) { return; }
        } else if (!com.veylon.world.urban.AccessibilityValidator.canMove(opened,
                new Vec3i(cx, cy, cz), new Vec3i(nx, ny, nz))) { return; }
        long nk = key(nx, ny, nz);
        float ng = cur.g + moveCost(w, nx, ny, nz, breach) + extra;
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
                return List.copyOf(out);
            }
            k = parent.get(k);
        }
        return null;
    }

    /** Snaps one cell to footing to tolerate body/step rounding; never guesses another floor. */
    private static int snapDown(com.veylon.world.VoxelAccess w, int x, int y, int z) {
        if (!w.isColumnLoaded(x, z) || y < 1 || y + 1 >= com.veylon.world.Chunk.SY) { return -1; }
        // A flight/teleport target must never silently become a different floor.
        for (int dy = 0; dy <= 1; dy++) {
            if (com.veylon.world.VoxelClearance.standable(w, x, y - dy, z)) {
                return y - dy;
            }
        }
        // Also allow snapping up one (target on a step).
        return com.veylon.world.VoxelClearance.standable(w, x, y + 1, z) ? y + 1 : -1;
    }

    /** Planning may open unlocked doors, but cannot erase any other solid or unloaded cell. */
    static com.veylon.world.VoxelAccess traversalAccess(com.veylon.world.VoxelAccess w) {
        return new com.veylon.world.VoxelAccess() {
            public boolean isColumnLoaded(int x, int z) { return w.isColumnLoaded(x, z); }
            public int fixtureLockAt(int x, int y, int z) { return w.fixtureLockAt(x, y, z); }
            public BlockType getBlock(int x, int y, int z) {
                if (!w.isColumnLoaded(x, z)) { return BlockType.STONE; }
                BlockType b = w.getBlock(x, y, z);
                return (b == BlockType.GATE || Fixtures.isClosedDoor(b))
                        && com.veylon.world.VoxelClearance.passable(w, x, y, z) ? BlockType.AIR : b;
            }
        };
    }

    /**
     * The world as a Breacher plans in it (urban milestone 15): a closed wooden door reads as
     * unlocked whatever its lock or bar says, because it means to break it. Nothing else changes:
     * metal doors, glass, walls and unloaded columns stop it as they stop anyone.
     */
    static com.veylon.world.VoxelAccess breachAccess(World w) {
        return new com.veylon.world.VoxelAccess() {
            public boolean isColumnLoaded(int x, int z) { return w.isColumnLoaded(x, z); }
            public int fixtureLockAt(int x, int y, int z) {
                return w.getBlock(x, y, z) == BlockType.WOOD_DOOR ? com.veylon.world.FixtureStates.LOCK_NONE
                        : w.fixtureLockAt(x, y, z);
            }
            public BlockType getBlock(int x, int y, int z) { return w.getBlock(x, y, z); }
        };
    }
}
