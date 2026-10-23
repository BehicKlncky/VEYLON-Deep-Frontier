package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.VehicleSites;

import java.util.function.Predicate;

/**
 * Generated generator-17 worlds for the milestone-18 tests: the starter superblock's abandoned
 * apartments and their furniture, the wreck nearest the spawn, and the production commands for
 * holding the attack button on a piece. Only the player's pose, aim and kit are staged.
 */
public final class RefugeWorldArena {
    public static final long SEED = 20_260_731L;
    public static final float DT = 1f / 20f;

    private RefugeWorldArena() {
    }

    public static Game session(GameMode mode) {
        return session(SEED, mode);
    }

    public static Game session(long seed, GameMode mode) {
        return SurvivorWorldArena.session(seed, mode, World.GEN_URBAN_REFUGES, new AudioManager());
    }

    /** The first three-storey apartment beside the starter refuge, abandoned and furnished. */
    public static BuildingPlan neighbour(Game g) {
        DistrictPlan district = g.world.urban.regionPlan(0, 0);
        for (ParcelPlan parcel : district.parcels()) {
            if (!UrbanStarterSite.isNeighbour(parcel) || parcel.use() != ParcelPlan.Use.RESIDENTIAL) { continue; }
            BuildingPlan b = g.world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (b != null && b.family() == BuildingPlan.Family.APARTMENT && b.variant() != BuildingPlan.Variant.OCCUPIED) {
                return b;
            }
        }
        throw new AssertionError("no abandoned apartment beside the starter refuge");
    }

    /** The first planned fixture of a building matching a test, on a given floor. */
    public static BuildingPlan.Fixture fixture(BuildingPlan b, int floorIndex, Predicate<BuildingPlan.Fixture> test) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (com.veylon.world.urban.UrbanIds.floorIndex(f.id()) == floorIndex && test.test(f)) { return f; }
        }
        throw new AssertionError("no such fixture on floor " + floorIndex + " of " + b.template());
    }

    /** A planned piece of furniture or a container of one block kind. */
    public static BuildingPlan.Fixture piece(BuildingPlan b, int floorIndex, BlockType block) {
        return fixture(b, floorIndex, f -> f.block() == block
                && (f.kind() == BuildingPlan.FixtureKind.FURNITURE || f.kind() == BuildingPlan.FixtureKind.CONTAINER));
    }

    /** The wreck nearest the spawn, within a region of it. */
    public static VehicleSites.Wreck nearestWreck(Game g) {
        VehicleSites.Wreck best = null;
        int[] spawn = g.world.urban.spawnColumn();
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (VehicleSites.Wreck w : g.world.urban.vehicles(rx, rz).wrecks()) {
                    long d = sq(w.x0() - spawn[0]) + sq(w.z0() - spawn[1]);
                    if (best == null || d < sq(best.x0() - spawn[0]) + sq(best.z0() - spawn[1])) { best = w; }
                }
            }
        }
        if (best == null) { throw new AssertionError("no wreck within a region of the spawn"); }
        return best;
    }

    private static long sq(long v) { return v * v; }

    /** Loads a cell's neighbourhood and stands the player on it. */
    public static void standAt(Game g, Vec3i cell) {
        SurvivorWorldArena.standAt(g, cell);
    }

    /** Aims at a cell, as the targeting raycast would. */
    public static void aim(Game g, Vec3i cell) {
        g.targetHit = new Raycaster.Hit(cell.x(), cell.y(), cell.z(), 0, 0, -1, 2,
                g.world.getBlock(cell.x(), cell.y(), cell.z()));
    }

    /** Holds the attack button on the aimed piece for a while, through the production mining step. */
    public static void hold(Game g, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            aim(g, new Vec3i(g.targetHit.x(), g.targetHit.y(), g.targetHit.z()));
            if (g.targetHit.type() == BlockType.AIR) { return; }
            g.blockActions.mine(DT);
        }
    }

    /** Puts an item in the player's hand, in the first hotbar slot. */
    public static void holdItem(Game g, com.veylon.item.ItemType type, int count) {
        g.player.inventory.set(0, new com.veylon.item.ItemStack(type, count));
        g.player.hotbarSel = 0;
    }
}
