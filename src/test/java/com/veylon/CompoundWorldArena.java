package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.SurvivorSites;

import java.util.ArrayList;
import java.util.List;

/**
 * Generated generator-16 worlds for the milestone-17 tests: the planned stronghold and raider yard
 * nearest the spawn, and the production frame step. Only the player's pose, the clock and, where a
 * test says so, an infected body are staged; guards, gates, needs and storage go through the
 * game's own ticks and commands.
 */
public final class CompoundWorldArena {
    public static final long SEED = 20_260_731L;
    private static final int RANGE = 3;

    private CompoundWorldArena() {
    }

    public static Game session(long seed, GameMode mode) {
        return SurvivorWorldArena.session(seed, mode, World.GEN_URBAN_STRONGHOLDS, new AudioManager());
    }

    /** The planned compound of a kind whose anchor lies nearest the spawn, within three regions. */
    public static CompoundSites.Compound nearest(Game g, SurvivorSites.Kind kind) {
        CompoundSites.Compound best = null;
        int[] spawn = g.world.urban.spawnColumn();
        for (int rz = -RANGE; rz <= RANGE; rz++) {
            for (int rx = -RANGE; rx <= RANGE; rx++) {
                for (CompoundSites.Compound c : g.world.urban.sites(rx, rz).compounds()) {
                    if (c.kind() != kind) { continue; }
                    if (best == null || c.site().anchor().distSq(spawn[0], 41, spawn[1])
                            < best.site().anchor().distSq(spawn[0], 41, spawn[1])) {
                        best = c;
                    }
                }
            }
        }
        if (best == null) { throw new AssertionError("no " + kind + " within three regions of the spawn"); }
        return best;
    }

    /** The registered record of a compound, or null. */
    public static UrbanSite site(Game g, CompoundSites.Compound c) {
        return UrbanSiteDirector.registered(g.world, c.site().id());
    }

    /** The live bodies of a site's people, in slot order. */
    public static List<Npc> live(UrbanSite s) {
        List<Npc> out = new ArrayList<>();
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead) { out.add(r.live); }
        }
        return out;
    }

    /** The street cell a few steps beyond a compound's porch, facing back at the gate. */
    public static Vec3i street(CompoundSites.Compound c, int steps) {
        var out = c.gate().outward();
        Vec3i cell = c.gate().laneOutside();
        while (c.porch().contains(cell.x(), cell.z())) { cell = cell.offset(out.dx, 0, out.dz); }
        return cell.offset(out.dx * steps, 0, out.dz * steps);
    }

    /**
     * A street cell some steps along the street in front of the gate, out of the gate's line of
     * sight: the direction along the street that stays on street surface.
     */
    public static Vec3i alongStreet(Game g, CompoundSites.Compound c, int steps) {
        Vec3i front = street(c, 2);
        var out = c.gate().outward();
        int ax = out.dz != 0 ? 1 : 0, az = out.dx != 0 ? 1 : 0;
        for (int sign : new int[]{1, -1}) {
            Vec3i cell = front.offset(ax * sign * steps, 0, az * sign * steps);
            boolean street = true;
            for (int k = 1; k <= steps && street; k++) {
                var surface = g.world.urban.surfaceAt(front.x() + ax * sign * k, front.z() + az * sign * k);
                street = surface.street;
            }
            if (street) { return cell; }
        }
        throw new AssertionError("no street runs " + steps + " cells either way past the gate");
    }

    /** Moves the clock forward to a time of the next day (the clock only ever runs forward). */
    public static void nextDay(Game g, int minutes) {
        long day = (long) (g.time.totalMinutes / (24 * 60));
        g.time.totalMinutes = (day + 1) * 24 * 60 + minutes;
    }

    /** Generates every chunk of a compound's block and its streets, so no planned cell is unloaded. */
    public static void load(Game g, CompoundSites.Compound c) {
        var b = c.site().bounds();
        for (int cz = Math.floorDiv(b.z0() - 16, 16); cz <= Math.floorDiv(b.z1() + 16, 16); cz++) {
            for (int cx = Math.floorDiv(b.x0() - 16, 16); cx <= Math.floorDiv(b.x1() + 16, 16); cx++) {
                g.world.getOrCreateChunk(cx, cz);
            }
        }
    }

    /** Stands the player on a cell, at rest, looking toward another, with the compound around it loaded. */
    public static void standFacing(Game g, CompoundSites.Compound c, Vec3i cell, Vec3i toward) {
        load(g, c);
        standFacing(g, cell, toward);
    }

    /** Stands the player on a cell, at rest, looking toward another. */
    public static void standFacing(Game g, Vec3i cell, Vec3i toward) {
        SurvivorWorldArena.standAt(g, cell);
        g.camera.yaw = (float) Math.toDegrees(Math.atan2(toward.x() - cell.x(), -(toward.z() - cell.z())));
        g.player.crouching = false;
    }

    public static void setClock(Game g, int minutes) {
        long day = (long) (g.time.totalMinutes / (24 * 60));
        g.time.totalMinutes = day * 24 * 60 + minutes;
    }

    public static boolean near(Npc n, Vec3i cell, float radius) {
        float dx = n.pos.x - cell.x() - .5f, dz = n.pos.z - cell.z() - .5f;
        return dx * dx + dz * dz < radius * radius && Math.abs(n.pos.y - cell.y()) < .6f;
    }

    /** True when every leaf of the gate is a closed metal door its guards hold locked. */
    public static boolean gateShutAndLocked(Game g, CompoundSites.Compound c) {
        for (Vec3i leaf : c.gate().leaves()) {
            for (int y = leaf.y(); y <= leaf.y() + 1; y++) {
                if (g.world.getBlock(leaf.x(), y, leaf.z()) != BlockType.METAL_DOOR
                        || g.world.fixtureLockAt(leaf.x(), y, leaf.z()) != com.veylon.world.FixtureStates.LOCK_KEYED) {
                    return false;
                }
            }
        }
        return true;
    }

    /** A Veilbound body on a cell, as the behaviour fixtures stage one. */
    public static Npc infected(Game g, Vec3i cell) {
        Npc n = g.entities.spawnNpc(g.world, "Veilbound", cell.x() + .5f, cell.y() + .02f, cell.z() + .5f);
        n.archetype = NpcArchetype.VEILBOUND;
        n.health = n.maxHealth = n.archetype.maxHealth;
        return n;
    }

    /** No live body of a site stands in a solid block. */
    public static boolean clearOfWalls(Game g, UrbanSite s) {
        for (Npc n : live(s)) {
            if (VoxelClearance.collides(g.world, n.pos.x, n.pos.y + .05f, n.pos.z, n.width * .9f, n.height * .9f, true)) {
                return false;
            }
        }
        return true;
    }
}
