package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;

import java.util.ArrayList;
import java.util.List;

/**
 * Generated generator-18 worlds for the milestone-19 tests: the refuge city of the milestone-18
 * arena under the rules of refuge utilities. Only the player's pose, aim and kit are staged.
 */
public final class UtilityWorldArena {
    public static final long SEED = RefugeWorldArena.SEED;

    private UtilityWorldArena() {
    }

    public static Game session(GameMode mode) {
        return session(SEED, mode);
    }

    public static Game session(long seed, GameMode mode) {
        return SurvivorWorldArena.session(seed, mode, World.GEN_URBAN_UTILITIES, new AudioManager());
    }

    /** Every planned basin of a building: the taps on its pipes. */
    public static List<BuildingPlan.Fixture> basins(BuildingPlan b) {
        List<BuildingPlan.Fixture> out = new ArrayList<>();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.FURNITURE && f.block() == BlockType.RAIN_COLLECTOR) { out.add(f); }
        }
        return out;
    }

    /** Puts empty waterskins in the pack, in a slot clear of the hotbar's first. */
    public static void giveEmptySkins(Game g, int count) {
        g.player.inventory.add(com.veylon.item.ItemType.WATERSKIN_EMPTY, count);
    }

    /** Puts an item down at a cell through the production placement command, from the first hotbar slot. */
    public static com.veylon.world.urban.UrbanUtilities.Device put(Game g, com.veylon.item.ItemType item,
                                                                    com.veylon.util.Vec3i cell) {
        RefugeWorldArena.holdItem(g, item, 1);
        if (!g.placeSelectedBlockAt(cell.x(), cell.y(), cell.z())) {
            throw new AssertionError("could not put the " + item + " down at " + cell);
        }
        return g.world.urban.utilities.device(cell);
    }

    /** The use button at a cell with a stack of an item in hand. */
    public static void useOn(Game g, com.veylon.util.Vec3i cell, com.veylon.item.ItemType item, int count) {
        RefugeWorldArena.holdItem(g, item, count);
        RefugeWorldArena.aim(g, cell);
        g.interactions.rightClick();
    }

    /** The neighbour's first-floor lanes, nearest a cell first: the free cells devices go on in the tests. */
    public static List<com.veylon.util.Vec3i> lanes(BuildingPlan b, int floorIndex, com.veylon.util.Vec3i near) {
        List<com.veylon.util.Vec3i> out = new ArrayList<>();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            com.veylon.util.Vec3i t = f.target();
            if (com.veylon.world.urban.UrbanIds.floorIndex(f.id()) == floorIndex && t != null && !out.contains(t)
                    && (f.kind() == BuildingPlan.FixtureKind.CONTAINER || f.kind() == BuildingPlan.FixtureKind.FURNITURE)) {
                out.add(t);
            }
        }
        out.sort(java.util.Comparator.comparingDouble(c -> c.distSq(near.x() + .5, near.y() + .5, near.z() + .5)));
        return out;
    }
}
