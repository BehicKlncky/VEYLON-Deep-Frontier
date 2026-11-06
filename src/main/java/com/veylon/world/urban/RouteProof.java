package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.VoxelAccess;
import com.veylon.world.VoxelClearance;

import java.util.ArrayList;
import java.util.List;

/**
 * Bounded physical reachability over any voxel box, for routes that are not one building:
 * transit stations and tunnels, service passages between basements, and roof routes with their
 * fire escapes (milestone 14).
 *
 * <p>Moves are exactly the building validator's: a cardinal step on the level, one block up
 * with three clear cells over the departure cell, or a drop of at most
 * {@link VoxelClearance#MAX_DROP}, each checked by {@link AccessibilityValidator#canMove}
 * against the body's real box. A climbable cell (a rope ladder) adds a straight move up to
 * the cell above it, the player's climb (Space, or walking into the wall behind the ladder). It
 * adds no move down: on a ladder the player's body neither falls nor has a control to descend,
 * so a body that steps onto a ladder from above hangs there and can only step back. A route a
 * player walks therefore uses a ladder to go up, never down. Doors are whatever the access
 * reports; callers open the ones a route may use.
 *
 * <p>A proof floods forward from its entry cells and backward to them, so a cell that is
 * reached but has no way back is seen. {@link Result#fallTraps} lists every reached cell with
 * an open neighbour a body can walk into but that has no floor within the safe drop: an edge
 * that would surprise someone walking a route. Work is bounded by the box, at most
 * {@link #MAX_CELLS} cells.
 */
public final class RouteProof {
    public static final int MAX_CELLS = 64 * 64 * 24;
    private static final int[][] DIRS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** An inclusive box of cells; every column inside it must be readable from the access. */
    public record Box(int x0, int y0, int z0, int x1, int y1, int z1) {
        public Box {
            long cells = (long) (x1 - x0 + 1) * (y1 - y0 + 1) * (z1 - z0 + 1);
            if (x1 < x0 || y1 < y0 || z1 < z0 || y0 < 1 || y1 > 94 || cells > MAX_CELLS) {
                throw new IllegalArgumentException("route box outside proof bounds: " + this);
            }
        }

        public boolean contains(int x, int y, int z) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
        }
    }

    /** What a proof found; cells are listed only when a caller asks about them. */
    public static final class Result {
        private final Box box;
        private final boolean[] reached, escaped;
        private final List<Vec3i> fallTraps;
        private final int walkable;

        private Result(Box box, boolean[] reached, boolean[] escaped, List<Vec3i> fallTraps, int walkable) {
            this.box = box; this.reached = reached; this.escaped = escaped;
            this.fallTraps = List.copyOf(fallTraps); this.walkable = walkable;
        }

        public boolean reached(Vec3i p) { int i = index(box, p.x(), p.y(), p.z()); return i >= 0 && reached[i]; }
        public boolean escapes(Vec3i p) { int i = index(box, p.x(), p.y(), p.z()); return i >= 0 && escaped[i]; }
        /** Reached and with a way back to an entry. */
        public boolean roundTrip(Vec3i p) { return reached(p) && escapes(p); }
        /** Reached cells with an unguarded drop deeper than the safe limit beside them. */
        public List<Vec3i> fallTraps() { return fallTraps; }
        /** Standing cells in the box, reached or not. */
        public int walkable() { return walkable; }

        public int reachedCount() {
            int n = 0;
            for (boolean b : reached) { if (b) { n++; } }
            return n;
        }

        /** Reached cells without a way back: one-way drops and traps. */
        public List<Vec3i> stranded() {
            List<Vec3i> out = new ArrayList<>();
            for (int i = 0; i < reached.length; i++) {
                if (reached[i] && !escaped[i]) { out.add(cell(box, i)); }
            }
            return out;
        }
    }

    private RouteProof() { }

    /**
     * Floods the box from its entries, forward and backward.
     *
     * @throws IllegalArgumentException for an entry outside the box or one that is not a standing cell
     */
    public static Result prove(VoxelAccess world, Box box, List<Vec3i> entries) {
        // Box-local x and z keep collider arithmetic exact for far-region coordinates.
        VoxelAccess access = local(world, box.x0, box.z0);
        Box worldBox = box;
        box = new Box(0, box.y0, 0, box.x1 - box.x0, box.y1, box.z1 - box.z0);
        int n = (box.x1 - box.x0 + 1) * (box.y1 - box.y0 + 1) * (box.z1 - box.z0 + 1);
        boolean[] walk = new boolean[n];
        int walkable = 0;
        for (int i = 0; i < n; i++) {
            Vec3i p = cell(box, i);
            walk[i] = standing(access, p.x(), p.y(), p.z());
            if (walk[i]) { walkable++; }
        }
        int[] edges = new int[n * 6];
        java.util.Arrays.fill(edges, -1);
        for (int i = 0; i < n; i++) {
            if (!walk[i]) { continue; }
            Vec3i from = cell(box, i);
            for (int d = 0; d < 4; d++) {
                Vec3i to = destination(access, box, walk, from, DIRS[d][0], DIRS[d][1]);
                if (to != null && AccessibilityValidator.canMove(access, from, to)) {
                    edges[i * 6 + d] = index(box, to.x(), to.y(), to.z());
                }
            }
            if (access.getBlock(from.x(), from.y(), from.z()).isClimbable()) {
                int j = index(box, from.x(), from.y() + 1, from.z());
                if (j >= 0 && walk[j] && !VoxelClearance.collides(access, from.x() + .5f, from.y() + 1,
                        from.z() + .5f, VoxelClearance.BODY_WIDTH, VoxelClearance.BODY_HEIGHT, true)) {
                    edges[i * 6 + 4] = j;
                }
            }
        }
        int[] starts = new int[entries.size()];
        for (int k = 0; k < entries.size(); k++) {
            Vec3i e = entries.get(k);
            int i = index(box, e.x() - worldBox.x0, e.y(), e.z() - worldBox.z0);
            if (i < 0 || !walk[i]) {
                throw new IllegalArgumentException("route entry is not a standing cell in the box: " + e);
            }
            starts[k] = i;
        }
        boolean[] reached = flood(edges, n, starts, false);
        boolean[] escaped = flood(edges, n, starts, true);
        List<Vec3i> traps = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            if (reached[i] && hasFallBeside(access, box, cell(box, i))) {
                Vec3i c = cell(box, i);
                traps.add(new Vec3i(c.x() + worldBox.x0, c.y(), c.z() + worldBox.z0));
            }
        }
        return new Result(worldBox, reached, escaped, traps, walkable);
    }

    /** A cell a body can stand in: the validator's and Pathfinder's policy, with its body clear. */
    static boolean standing(VoxelAccess access, int x, int y, int z) {
        return VoxelClearance.standable(access, x, y, z)
                && !VoxelClearance.collides(access, x + .5f, y, z + .5f,
                VoxelClearance.BODY_WIDTH, VoxelClearance.BODY_HEIGHT, true);
    }

    private static Vec3i destination(VoxelAccess access, Box box, boolean[] walk, Vec3i from, int dx, int dz) {
        int x = from.x() + dx, z = from.z() + dz, y = from.y();
        if (at(box, walk, x, y, z)) { return new Vec3i(x, y, z); }
        if (at(box, walk, x, y + 1, z) && VoxelClearance.passable(access, from.x(), y + 2, from.z())) {
            return new Vec3i(x, y + 1, z);
        }
        for (int drop = 1; drop <= VoxelClearance.MAX_DROP; drop++) {
            if (!VoxelClearance.passable(access, x, y - drop + 1, z)) { return null; }
            if (at(box, walk, x, y - drop, z)) { return new Vec3i(x, y - drop, z); }
        }
        return null;
    }

    /**
     * True when a body standing here could walk sideways into open air with no floor within the
     * safe drop: both the feet and head cells of the neighbour are open and nothing below the
     * feet, down to {@link VoxelClearance#MAX_DROP} cells, is standable. Water and ladders
     * break a fall, as they do for the body. Neighbours outside the box are not judged, and
     * neither is a body standing in a window opening or on top of a guard rail: climbing onto a
     * sill, or out of one onto a rail, is a deliberate act, never a route.
     */
    static boolean hasFallBeside(VoxelAccess access, Box box, Vec3i p) {
        BlockType feet = access.getBlock(p.x(), p.y(), p.z());
        if (feet.isClimbable() || feet == BlockType.WINDOW_OPEN || feet == BlockType.BROKEN_WINDOW
                || access.getBlock(p.x(), p.y() - 1, p.z()) == BlockType.RAILING) { return false; }
        for (int[] d : DIRS) {
            int x = p.x() + d[0], z = p.z() + d[1], y = p.y();
            if (!box.contains(x, y, z) || !access.isColumnLoaded(x, z)) { continue; }
            if (!VoxelClearance.passable(access, x, y, z) || !VoxelClearance.passable(access, x, y + 1, z)
                    || VoxelClearance.collides(access, x + .5f, y, z + .5f,
                    VoxelClearance.BODY_WIDTH, VoxelClearance.BODY_HEIGHT, true)) { continue; }
            boolean floor = false;
            for (int drop = 0; drop <= VoxelClearance.MAX_DROP && !floor; drop++) {
                floor = VoxelClearance.standable(access, x, y - drop, z);
                if (!floor && !VoxelClearance.passable(access, x, y - drop, z)) { break; }
            }
            if (!floor) { return true; }
        }
        return false;
    }

    private static boolean[] flood(int[] edges, int n, int[] starts, boolean reverse) {
        int[] head = null, next = null, from = null;
        if (reverse) {
            head = new int[n]; java.util.Arrays.fill(head, -1);
            next = new int[edges.length]; from = new int[edges.length];
            int e = 0;
            for (int i = 0; i < n; i++) {
                for (int d = 0; d < 6; d++) {
                    int dest = edges[i * 6 + d];
                    if (dest < 0) { continue; }
                    from[e] = i; next[e] = head[dest]; head[dest] = e++;
                }
            }
        }
        boolean[] visited = new boolean[n];
        int[] queue = new int[n];
        int tail = 0;
        for (int s : starts) { if (!visited[s]) { visited[s] = true; queue[tail++] = s; } }
        for (int h = 0; h < tail; h++) {
            int i = queue[h];
            if (reverse) {
                for (int e = head[i]; e >= 0; e = next[e]) {
                    int j = from[e];
                    if (!visited[j]) { visited[j] = true; queue[tail++] = j; }
                }
            } else {
                for (int d = 0; d < 6; d++) {
                    int j = edges[i * 6 + d];
                    if (j >= 0 && !visited[j]) { visited[j] = true; queue[tail++] = j; }
                }
            }
        }
        return visited;
    }

    private static VoxelAccess local(VoxelAccess world, int ox, int oz) {
        return new VoxelAccess() {
            @Override public BlockType getBlock(int x, int y, int z) {
                return world.getBlock(x + ox, y, z + oz);
            }
            @Override public boolean isColumnLoaded(int x, int z) { return world.isColumnLoaded(x + ox, z + oz); }
            @Override public int fixtureLockAt(int x, int y, int z) { return world.fixtureLockAt(x + ox, y, z + oz); }
        };
    }

    private static boolean at(Box box, boolean[] walk, int x, int y, int z) {
        int i = index(box, x, y, z);
        return i >= 0 && walk[i];
    }

    static int index(Box b, int x, int y, int z) {
        if (!b.contains(x, y, z)) { return -1; }
        int w = b.x1 - b.x0 + 1, d = b.z1 - b.z0 + 1;
        return ((y - b.y0) * d + (z - b.z0)) * w + (x - b.x0);
    }

    static Vec3i cell(Box b, int i) {
        int w = b.x1 - b.x0 + 1, d = b.z1 - b.z0 + 1;
        return new Vec3i(b.x0 + i % w, b.y0 + i / (w * d), b.z0 + i / w % d);
    }
}
