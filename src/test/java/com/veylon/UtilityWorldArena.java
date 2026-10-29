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
}
