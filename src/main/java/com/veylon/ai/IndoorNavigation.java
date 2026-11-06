package com.veylon.ai;

import com.veylon.FixtureActions;
import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelClearance;

/** Shared bounded path follower for residents and infected, with no unsafe steering fallback. */
public final class IndoorNavigation {
    private IndoorNavigation() { }

    /** False only on arrival; blocked/deferred routes wait and retry after a staggered delay. */
    public static boolean moveTo(Game g, Npc n, Vec3i goal, float speed, float arrive) {
        return moveTo(g, n, goal, speed, arrive, false);
    }

    /**
     * As {@link #moveTo(Game, Npc, Vec3i, float, float)}; with {@code breach}, a Breacher (urban
     * milestone 15) that finds no ordinary route plans through closed wooden doors and plank
     * barricades ({@link Pathfinder#findBreach}), and stops at a locked or barred door or a
     * barricade on its route, naming it in {@link Npc#breachDoor} for its brain to hammer down. It
     * opens unlocked doors as anyone does.
     */
    public static boolean moveTo(Game g, Npc n, Vec3i goal, float speed, float arrive, boolean breach) {
        if (n.distSqTo(goal.x() + .5f, goal.y(), goal.z() + .5f) < arrive * arrive
                && Math.abs(n.pos.y - goal.y()) < .5f) {
            n.path = null; Steering.stop(n);
            if (n.onLadder) { n.vel.y = 0; }
            return false;
        }
        if (!goal.equals(n.navigationGoal)) {
            n.navigationGoal = goal; n.path = null;
        }
        if (n.path != null && (!g.world.navigation.valid(n.path) || n.pathIndex >= n.path.size())) {
            n.path = null;
        }
        if (n.path == null && n.repathCooldown <= 0) {
            Vec3i from = new Vec3i(cell(n.pos.x), cell(n.pos.y + .01f), cell(n.pos.z));
            Vec3i waypoint = PortalGraph.waypoint(g.world, from, goal);
            n.repathCooldown = 0.8f + g.entities.nextSettledNpcAiFloat() * .7f;
            if (waypoint != null) {
                n.path = Pathfinder.findSegment(g.world, from.x(), from.y(), from.z(),
                        waypoint.x(), waypoint.y(), waypoint.z(), Pathfinder.DEFAULT_BUDGET);
                if (n.path == null && breach) {
                    n.path = Pathfinder.findBreach(g.world, from.x(), from.y(), from.z(),
                            waypoint.x(), waypoint.y(), waypoint.z(), Pathfinder.DEFAULT_BUDGET);
                }
            }
            n.pathIndex = 0;
        }
        if (n.path == null) { Steering.stop(n); return true; }
        Vec3i step = n.path.get(n.pathIndex);
        if (n.distSqTo(step.x() + .5f, step.y(), step.z() + .5f) < .2f * .2f) {
            n.pathIndex++;
            if (n.pathIndex == n.path.size()) { n.path = null; n.repathCooldown = 0; }
            Steering.stop(n); return true;
        }
        // Never interact with a remote cached waypoint after displacement.
        if (Math.abs(step.x() + .5f - n.pos.x) > 1.6f
                || Math.abs(step.z() + .5f - n.pos.z) > 1.6f
                || step.y() > n.pos.y + 1.05f || n.pos.y - step.y() > Pathfinder.MAX_DROP + .05f) {
            n.path = null; Steering.stop(n); return true;
        }
        for (int y = step.y(); y <= step.y() + 1; y++) {
            BlockType b = g.world.getBlock(step.x(), y, step.z());
            if (Fixtures.isClosedDoor(b)) { FixtureActions.residentOpens(g, step.x(), y, step.z()); }
            if (b == BlockType.GATE) { g.settlementManager.openGate(g, new Vec3i(step.x(), y, step.z())); }
        }
        if (breach) {
            for (int y = step.y(); y <= step.y() + 1; y++) {
                BlockType b = g.world.getBlock(step.x(), y, step.z());
                // Still closed after the shoulder above: locked or barred. Stop here and break it.
                if (b == BlockType.WOOD_DOOR) {
                    n.breachDoor = new Vec3i(step.x(), Fixtures.stackBottom(g.world, step.x(), y, step.z(),
                            Fixtures.Kind.WOOD_DOOR), step.z());
                    Steering.stop(n);
                    return true;
                }
                // Milestone 18: boards nailed across the way, hammered down one cell at a time.
                if (b == BlockType.BARRICADE) {
                    n.breachDoor = new Vec3i(step.x(), y, step.z());
                    Steering.stop(n);
                    return true;
                }
            }
        }
        if (!safeStride(g, n, step)) {
            n.path = null; Steering.stop(n); return true;
        }
        Steering.moveToward(n, step.x() + .5f, step.z() + .5f, speed);
        if (n.onLadder && Math.abs(step.y() - n.pos.y) > .2f) {
            n.vel.y = step.y() > n.pos.y ? 2.4f : -2.4f;
        }
        return true;
    }

    private static boolean safeStride(Game g, Npc n, Vec3i step) {
        if (!Pathfinder.standable(g.world, step.x(), step.y(), step.z())) { return false; }
        float high = Math.max(n.pos.y, step.y());
        for (int i = 0; i <= 5; i++) {
            float f = i / 5f;
            float x = n.pos.x + (step.x() + .5f - n.pos.x) * f;
            float z = n.pos.z + (step.z() + .5f - n.pos.z) * f;
            int x0 = cell(x - n.width / 2), x1 = cell(x + n.width / 2);
            int z0 = cell(z - n.width / 2), z1 = cell(z + n.width / 2);
            for (int bx = x0; bx <= x1; bx++) {
                for (int bz = z0; bz <= z1; bz++) {
                    if (!g.world.isColumnLoaded(bx, bz)) { return false; }
                }
            }
            if (VoxelClearance.collides(g.world, x, high + .002f, z, n.width, n.height, true)) { return false; }
        }
        for (float y = step.y(); y < high; y += .2f) {
            if (VoxelClearance.collides(g.world, step.x() + .5f, y, step.z() + .5f,
                    n.width, n.height, true)) { return false; }
        }
        return true;
    }
    private static int cell(float value) { return (int) Math.floor(value); }
}
