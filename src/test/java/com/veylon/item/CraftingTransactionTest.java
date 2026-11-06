package com.veylon.item;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CraftingTransactionTest {
    @Test
    void failedPartialOutputPreservesExistingOutputAndIngredientMetadata() {
        Inventory inv = new Inventory(2);
        ItemStack wood = new ItemStack(ItemType.PLANK, 2);
        wood.charge = 7;
        inv.set(0, wood);
        ItemStack sticks = new ItemStack(ItemType.STICK, ItemType.STICK.maxStack - 1);
        inv.set(1, sticks);
        Recipe recipe = new Recipe("Sticks x4", ItemType.STICK, 4, Station.HAND, ItemType.PLANK, 1);
        assertNull(CraftingSystem.craft(inv, recipe, Set.of(Station.HAND), new HashSet<>()));
        assertSame(wood, inv.get(0));
        assertEquals(2, wood.count);
        assertEquals(7, wood.charge);
        assertSame(sticks, inv.get(1));
        assertEquals(ItemType.STICK.maxStack - 1, sticks.count);
    }

    @Test
    void consumedIngredientSlotCanHoldTheResult() {
        Inventory inv = new Inventory(1);
        inv.add(ItemType.LOG, 1);
        Recipe recipe = CraftingSystem.RECIPES.getFirst();
        assertNotNull(CraftingSystem.craft(inv, recipe, Set.of(Station.HAND), new HashSet<>()));
        assertEquals(4, inv.count(ItemType.PLANK));
    }
}
