package com.veylon.item;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** R17: where catalog grants land and what a granted stack looks like. */
class CreativeGrantsTest {

    @Test
    void anEmptySelectedHotbarSlotReceivesTheGrant() {
        Inventory inventory = new Inventory(36);
        inventory.set(0, new ItemStack(ItemType.STONE, 3));
        assertEquals(CreativeGrants.Result.GRANTED, CreativeGrants.grant(inventory, 4, ItemType.PLANK, true));
        assertEquals(ItemType.PLANK, inventory.get(4).type, "R17: the empty selected slot is preferred");
        assertEquals(ItemType.PLANK.maxStack, inventory.get(4).count, "R17: left click grants a full stack");
    }

    @Test
    void anOccupiedSelectedSlotSendsTheGrantToTheFirstEmptySlot() {
        Inventory inventory = new Inventory(36);
        inventory.set(0, new ItemStack(ItemType.STONE, 3));
        inventory.set(1, new ItemStack(ItemType.DIRT, 3));
        assertEquals(CreativeGrants.Result.GRANTED, CreativeGrants.grant(inventory, 0, ItemType.BANDAGE, false));
        assertEquals(ItemType.STONE, inventory.get(0).type, "R17: the occupied selected slot is untouched");
        assertEquals(ItemType.BANDAGE, inventory.get(2).type, "R17: the first empty slot receives it");
        assertEquals(1, inventory.get(2).count, "R17: right click grants one item");
    }

    @Test
    void aFullInventoryReportsInsteadOfReplacingAnything() {
        Inventory inventory = new Inventory(4);
        for (int i = 0; i < 4; i++) inventory.set(i, new ItemStack(ItemType.STONE, 1));
        assertEquals(CreativeGrants.Result.INVENTORY_FULL, CreativeGrants.grant(inventory, 0, ItemType.PLANK, true));
        for (int i = 0; i < 4; i++) {
            assertEquals(ItemType.STONE, inventory.get(i).type, "R17: a full inventory changes nothing");
        }
    }

    @Test
    void grantedStacksAreFreshWithFullDurabilityAndAnEmptyMagazine() {
        ItemStack musket = CreativeGrants.fresh(ItemType.MUSKET, true);
        assertEquals(ItemType.MUSKET.maxDurability, musket.durability, "R17: full durability");
        assertEquals(0, musket.charge, "R17: an empty magazine, like a crafted firearm");
        ItemStack meat = CreativeGrants.fresh(ItemType.RAW_MEAT, false);
        assertEquals(ItemType.RAW_MEAT.spoilSeconds, meat.freshness, "R17: full freshness");
        assertEquals(1, meat.count);
    }

    @Test
    void aHotbarKeyReplacesThatSlotWithAFullStack() {
        Inventory inventory = new Inventory(36);
        inventory.set(2, new ItemStack(ItemType.STONE, 5));
        CreativeGrants.putInHotbar(inventory, 2, ItemType.TORCH);
        assertEquals(ItemType.TORCH, inventory.get(2).type, "R17: the hotbar key replaces the slot");
        assertEquals(ItemType.TORCH.maxStack, inventory.get(2).count, "R17: with a full stack");
    }
}
