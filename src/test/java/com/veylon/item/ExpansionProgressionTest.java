package com.veylon.item;

import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.settlement.SettlementBuilder;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExpansionProgressionTest {

    @Test
    void deepResourcesCraftBlackPowderAtAWorkbench() {
        Recipe recipe = recipe(ItemType.BLACK_POWDER);
        Inventory inv = new Inventory(20);
        inv.add(ItemType.SULFUR, 1);
        inv.add(ItemType.SALTPETER, 2);
        inv.add(ItemType.CHARCOAL, 1);

        assertEquals("Black Powder x3", CraftingSystem.craft(inv, recipe,
                EnumSet.of(Station.WORKBENCH), new HashSet<>()));
        assertEquals(3, inv.count(ItemType.BLACK_POWDER));
        assertEquals(0, inv.count(ItemType.SULFUR));
        assertEquals(0, inv.count(ItemType.SALTPETER));
    }

    @Test
    void caveTraversalGearHasRecipesAndFunctionalBlocks() {
        assertNotNull(recipe(ItemType.ROPE_LADDER));
        assertNotNull(recipe(ItemType.LANTERN));
        assertNotNull(recipe(ItemType.TRAIL_MARKER));
        assertTrue(BlockType.LADDER.isClimbable());
        assertTrue(BlockType.LANTERN.light >= 12, "lantern is a meaningful cave light");
        assertFalse(BlockType.TRAIL_MARKER.opaque, "trail marker remains readable without blocking a route");
    }

    @Test
    void relicWeaponsAreDistinctRestorationsRestrictedToRareCrates() {
        Recipe carbineRepair = recipe(ItemType.RELIC_CARBINE);
        Recipe rifleRepair = recipe(ItemType.RELIC_RIFLE);
        assertTrue(carbineRepair.ingredients.containsKey(ItemType.RELIC_CARBINE));
        assertTrue(rifleRepair.ingredients.containsKey(ItemType.RELIC_RIFLE));
        assertTrue(CraftingSystem.RECIPES.stream()
                .filter(r -> r.result == ItemType.RELIC_CARBINE || r.result == ItemType.RELIC_RIFLE)
                .allMatch(r -> r.ingredients.containsKey(r.result)),
                "there is no recipe that creates a relic weapon from ordinary materials");

        WeaponDefinition carbine = WeaponRegistry.of(ItemType.RELIC_CARBINE);
        WeaponDefinition rifle = WeaponRegistry.of(ItemType.RELIC_RIFLE);
        assertTrue(carbine.relic && rifle.relic);
        assertFalse(carbine.automatic);
        assertTrue(rifle.automatic);
        assertTrue(carbine.range > rifle.range && carbine.spread < rifle.spread);
        assertTrue(rifle.attackInterval < carbine.attackInterval);

        int rareWeapons = 0;
        for (int seed = 0; seed < 200; seed++) {
            Inventory ordinary = SettlementBuilder.rollCrate(seed,
                    new Vec3i(seed, 40, -seed), SettlementBuilder.CRATE_ARMORY);
            assertEquals(0, ordinary.count(ItemType.RELIC_CARBINE)
                    + ordinary.count(ItemType.RELIC_RIFLE));
            Inventory rare = SettlementBuilder.rollCrate(seed,
                    new Vec3i(seed, 40, -seed), SettlementBuilder.CRATE_RELIC);
            rareWeapons += rare.count(ItemType.RELIC_CARBINE) + rare.count(ItemType.RELIC_RIFLE);
            assertTrue(rare.count(ItemType.RELIC_PARTS) > 0);
            assertTrue(rare.count(ItemType.RIFLE_CARTRIDGE) > 0);
        }
        assertTrue(rareWeapons > 50 && rareWeapons < 130,
                "relic-tier armories sometimes, but not always, contain one weapon");
    }

    private static Recipe recipe(ItemType result) {
        return CraftingSystem.RECIPES.stream()
                .filter(r -> r.result == result)
                .findFirst().orElseThrow();
    }
}
