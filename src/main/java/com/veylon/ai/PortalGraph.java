package com.veylon.ai;

import com.veylon.util.Vec3i;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanIndoorQueries;

/**
 * Modest derived hierarchy: building entries join room/floor destinations through
 * declared stair landings. Every segment still needs live bounded voxel A*;
 * plan connectivity never grants passage through a wall or an unloaded column.
 * Immutable plans already have a bounded world cache, so no second graph cache is retained.
 */
public final class PortalGraph {
    private PortalGraph() { }

    public static Vec3i waypoint(World w, Vec3i from, Vec3i goal) {
        UrbanIndoorQueries.Location a = w.urbanLocation(from.x(), from.y(), from.z());
        UrbanIndoorQueries.Location b = w.urbanLocation(goal.x(), goal.y(), goal.z());
        if (a != null && (b == null || a.building().id() != b.building().id())) {
            if (a.floor().index() != 0) { return stair(w, a.building(), a.floor().index(), 0); }
            return local(w, from, nearest(w, a.building(), from));
        }
        if (b != null) {
            if (a == null) {
                Vec3i entry = nearest(w, b.building(), from);
                if (entry != null && from.distSq(entry.x(), entry.y(), entry.z()) > 4) { return local(w, from, entry); }
                // At the street entry, aim at the ground-floor landing before going upstairs.
                for (var floor : b.building().floors()) {
                    if (floor.index() == 0 && b.floor().index() != 0) { return floor.landing(); }
                }
            } else if (a.floor().index() != b.floor().index()) {
                return stair(w, a.building(), a.floor().index(), b.floor().index());
            }
            if (b.room() != null && (a == null || a.room() == null || a.room().id() != b.room().id())) {
                return b.room().anchor();
            }
        }
        return local(w, from, goal);
    }

    /** Long exterior legs advance through loaded supported local anchors, never heightmap roofs. */
    private static Vec3i local(World w, Vec3i from, Vec3i goal) {
        if (goal == null) { w.navigation.budget.unsupported(); return null; }
        int dx = goal.x() - from.x(), dz = goal.z() - from.z();
        if (Math.abs((long) dx) + Math.abs((long) dz) <= 40) { return goal; }
        boolean xFirst = Math.abs(dx) >= Math.abs(dz);
        for (int axis = 0; axis < 2; axis++) {
            boolean alongX = axis == 0 ? xFirst : !xFirst;
            int x = from.x() + (alongX ? Integer.signum(dx) * Math.min(16, Math.abs(dx)) : 0);
            int z = from.z() + (alongX ? 0 : Integer.signum(dz) * Math.min(16, Math.abs(dz)));
            for (int dy = 0; dy <= 3; dy++) {
                if ((x != from.x() || z != from.z()) && Pathfinder.standable(w, x, from.y() - dy, z)) {
                    return new Vec3i(x, from.y() - dy, z);
                }
                if (dy > 0 && (x != from.x() || z != from.z()) && Pathfinder.standable(w, x, from.y() + dy, z)) {
                    return new Vec3i(x, from.y() + dy, z);
                }
            }
        }
        w.navigation.budget.unsupported();
        return null;
    }

    private static Vec3i stair(World w, BuildingPlan plan, int from, int to) {
        int flight = to > from ? from : from - 1;
        for (var stair : plan.stairs()) {
            if (stair.fromFloor() == flight) {
                return to > from ? stair.route().getLast() : stair.route().getFirst();
            }
        }
        w.navigation.budget.unsupported();
        return null;
    }

    /**
     * The nearest entrance whose door is usable now; a locked or barred one (a clinic's staff
     * door, a security post's sally port, a held street door, a barred refuge) only when every
     * entrance is shut. Live state wins over the plan, so a forced lock opens the route again.
     */
    private static Vec3i nearest(World w, BuildingPlan plan, Vec3i from) {
        Vec3i open = null, shut = null;
        double openD = Double.MAX_VALUE, shutD = Double.MAX_VALUE;
        for (Vec3i entry : plan.approaches()) {
            double d = from.distSq(entry.x(), entry.y(), entry.z());
            if (locked(w, plan, entry)) {
                if (d < shutD) { shutD = d; shut = entry; }
            } else if (d < openD) { openD = d; open = entry; }
        }
        return open != null ? open : shut;
    }

    /** An entrance's first leaf faces its approach; it is shut when that leaf is closed and locked. */
    private static boolean locked(World w, BuildingPlan plan, Vec3i approach) {
        for (BuildingPlan.Fixture f : plan.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.DOOR && f.target().equals(approach)) {
                Vec3i p = f.position();
                return Fixtures.isClosedDoor(w.getBlock(p.x(), p.y(), p.z()))
                        && w.fixtureLockAt(p.x(), p.y(), p.z()) != FixtureStates.LOCK_NONE;
            }
        }
        return false;
    }
}
