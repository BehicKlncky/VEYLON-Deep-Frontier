package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelAccess;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;

import java.util.ArrayList;
import java.util.List;

/**
 * Production proof that a transit station is a real, safe place (milestone 14): from the street
 * cell at the top of its stair, with its unlocked door opened, a body reaches every platform,
 * track, room and stair cell, every container's standing cell and the door itself, and walks
 * back out from each; nothing reached is stranded, and no reached cell stands beside a drop
 * deeper than the safe limit (the guard rails around the stair opening are what make that
 * true at the street). A station failing it is never sited.
 */
public final class TransitValidator {
    public static final int MAX_MESSAGES = 16;

    public record Report(List<String> messages, int reached) {
        public Report { messages = List.copyOf(messages); }
        public boolean ok() { return messages.isEmpty(); }
    }

    private TransitValidator() { }

    public static Report inspect(TransitStation station) {
        return inspect(station.plan(), station.plan().voxels());
    }

    /**
     * Validates a station plan against any access holding its cells: the plan's own volume, or a
     * generated world whose columns around the station are loaded.
     */
    public static Report inspect(BuildingPlan plan, VoxelAccess cells) {
        List<String> messages = new ArrayList<>();
        PlanVolume v = plan.voxels();
        VoxelAccess opened = openDoors(plan, cells);
        RouteProof.Box box = new RouteProof.Box(v.x0, TransitNetwork.SLAB_Y, v.z0,
                v.x0 + v.width - 1, v.maxY, v.z0 + v.depth - 1);
        RouteProof.Result proof;
        try {
            proof = RouteProof.prove(opened, box, plan.approaches());
        } catch (IllegalArgumentException e) {
            return new Report(List.of("APPROACH: " + e.getMessage()), 0);
        }
        for (BuildingPlan.Floor floor : plan.floors()) {
            for (BuildingPlan.Room room : floor.rooms()) {
                if (room.functions().contains(BuildingPlan.Function.CORRIDOR_STAIRS)) { continue; }
                ParcelPlan.Rect r = room.interior();
                for (int x = r.x0(); x <= r.x1(); x++) {
                    for (int z = r.z0(); z <= r.z1(); z++) {
                        Vec3i at = new Vec3i(x, room.anchor().y(), z);
                        if (!occupied(plan, at)) { check(proof, at, "ROOM " + room.functions(), messages); }
                    }
                }
            }
        }
        for (BuildingPlan.Stair stair : plan.stairs()) {
            for (Vec3i at : stair.route()) { check(proof, at, "STAIR", messages); }
        }
        for (Fixture f : plan.fixtures()) {
            check(proof, f.target(), f.kind().name(), messages);
            if (f.kind() == FixtureKind.DOOR && !f.locked()) { check(proof, f.position(), "DOOR", messages); }
        }
        for (Vec3i trap : proof.fallTraps()) { fail(messages, "FALL: unguarded drop beside " + trap); }
        for (Vec3i lost : proof.stranded()) { fail(messages, "STRANDED: no way back from " + lost); }
        return new Report(messages, proof.reachedCount());
    }

    private static boolean occupied(BuildingPlan plan, Vec3i at) {
        for (Fixture f : plan.fixtures()) {
            if (f.kind() != FixtureKind.QUEST && f.position().equals(at)) { return true; }
        }
        return false;
    }

    private static void check(RouteProof.Result proof, Vec3i at, String what, List<String> messages) {
        if (!proof.reached(at)) { fail(messages, what + ": unreachable " + at); }
        else if (!proof.escapes(at)) { fail(messages, what + ": no way back " + at); }
    }

    private static void fail(List<String> messages, String message) {
        if (messages.size() < MAX_MESSAGES) { messages.add(message); }
    }

    /** The station's unlocked doors as their ordinary open action leaves them. */
    private static VoxelAccess openDoors(BuildingPlan plan, VoxelAccess cells) {
        List<Vec3i> doors = new ArrayList<>();
        for (Fixture f : plan.fixtures()) {
            if (f.kind() == FixtureKind.DOOR && !f.locked()) {
                for (int h = 0; h < f.height(); h++) { doors.add(f.position().offset(0, h, 0)); }
            }
        }
        return new VoxelAccess() {
            @Override public BlockType getBlock(int x, int y, int z) {
                BlockType b = cells.getBlock(x, y, z);
                Fixtures.Kind kind = Fixtures.kindOf(b);
                return kind != null && kind.door && doors.contains(new Vec3i(x, y, z)) ? kind.open : b;
            }
            @Override public boolean isColumnLoaded(int x, int z) { return cells.isColumnLoaded(x, z); }
            @Override public int fixtureLockAt(int x, int y, int z) { return cells.fixtureLockAt(x, y, z); }
        };
    }
}
