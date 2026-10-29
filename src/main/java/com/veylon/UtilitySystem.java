package com.veylon;

import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanIndoorQueries;
import com.veylon.world.urban.UrbanUtilities;

/**
 * The water and power of a layout-fifteen city's refuges (urban milestone 19), as the player works
 * them. The world's side, what is left and what was drawn, is {@link UrbanUtilities}.
 *
 * <h2>Taps</h2>
 *
 * <p>A planned basin is a tap on its building's pipes. F at one with an empty waterskin in the pack
 * fills it with a litre of stale water, untreated: it must be boiled at a lit fire before it is
 * safe. With no waterskin, F drinks from the tap as from any untreated water, and risks the same
 * sickness. Either way the building's pipes hold one litre less, and once they are dry every tap
 * of the building is. Taps on a storey survivors hold are theirs, and do not run for the player.
 */
final class UtilitySystem {
    private final Game game;
    private final RefugeSystem refuges;

    UtilitySystem(Game game, RefugeSystem refuges) {
        this.game = game;
        this.refuges = refuges;
    }

    void reset() {
    }

    /** True in a world whose rules include refuge utilities: layout fifteen. */
    static boolean applies(World w) {
        return w != null && w.urban != null && w.urban.hasUtilities();
    }

    // ------------------------------------------------------------------
    // Taps
    // ------------------------------------------------------------------

    /**
     * F at a cell that may be a tap: fill an empty waterskin with stale water, or drink from it.
     *
     * @return true when the cell is a tap, whether or not water ran
     */
    boolean tap(Vec3i pos) {
        BuildingPlan b = UrbanUtilities.tapBuilding(game.world, pos.x(), pos.y(), pos.z());
        if (b == null) { return false; }
        String refusal = tapRefusal(b, pos);
        if (refusal != null) {
            game.log(refusal + ".");
            return true;
        }
        UrbanUtilities u = game.world.urban.utilities;
        if (!u.canRecord(b)) {
            game.log("The taps will not run: the world's water record is full.");
            return true;
        }
        if (game.player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0) {
            if (!SurvivalTransfers.exchange(game, ItemType.WATERSKIN_EMPTY, ItemType.WATERSKIN_DIRTY)) { return true; }
            u.draw(b);
            game.audio.playDrink();
            game.log("Filled a waterskin with stale water from the pipes (" + u.left(b)
                    + " L left in the building). Boil it before you drink it.");
            return true;
        }
        u.draw(b);
        game.interactions.drinkUntreated("stale water from the tap");
        return true;
    }

    /** Why a tap gives nothing, or null when it runs. */
    private String tapRefusal(BuildingPlan b, Vec3i pos) {
        UrbanIndoorQueries.Location here = UrbanIndoorQueries.location(game.world, pos.x(), pos.y(), pos.z());
        if (here != null && refuges.heldBy(b, here.floor()) != null) {
            return "These taps are the survivors' who hold this building";
        }
        if (game.world.urban.utilities.left(b) <= 0) {
            return "The tap is dry: the stale water left in this building's pipes is gone";
        }
        return null;
    }

    /** The HUD line for an aimed tap, or null when the cell is no tap. */
    String tapPrompt(Vec3i pos) {
        BuildingPlan b = UrbanUtilities.tapBuilding(game.world, pos.x(), pos.y(), pos.z());
        if (b == null) { return null; }
        String refusal = tapRefusal(b, pos);
        if (refusal != null) {
            return refusal.startsWith("The tap is dry") ? "Tap: dry, its building's pipes are empty"
                    : "Tap: the survivors' water";
        }
        int left = game.world.urban.utilities.left(b);
        return game.player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0
                ? "[F] Fill a waterskin: stale tap water, " + left + " L left"
                : "[F] Drink stale tap water (untreated, " + left + " L left)";
    }
}
