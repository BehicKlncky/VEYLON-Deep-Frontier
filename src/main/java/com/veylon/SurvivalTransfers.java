package com.veylon;

import com.veylon.item.CraftingSystem;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.Station;
import java.util.Set;

/** Station transformations use the same atomic capacity check as crafting. */
final class SurvivalTransfers {
    private SurvivalTransfers() { }
    static boolean exchange(Game game, ItemType from, ItemType to) {
        if (CraftingSystem.craft(game.player.inventory,
                new Recipe("Transfer", to, 1, Station.HAND, from, 1), Set.of(Station.HAND), Set.of()) == null) {
            game.log("Make room in your inventory first; your supplies are still here.");
            return false;
        }
        return true;
    }
}
