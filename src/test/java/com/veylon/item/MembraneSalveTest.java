package com.veylon.item;

import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** Generator 14's membrane salve: the one craft use of a Veilmaw's sacs, by hand (D-137). */
class MembraneSalveTest {

    @Test
    void generatorFourteenWorksTwoPoulticesFromASacAndAFibreByHand() {
        List<Recipe> recipes = CraftingSystem.recipesFor(new World(7, World.GEN_URBAN_ECOLOGY));
        Recipe salve = recipes.stream()
                .filter(r -> r.ingredients.containsKey(ItemType.MEMBRANE_SAC))
                .findFirst().orElseThrow();
        assertSame(ItemType.HERBAL_POULTICE, salve.result);
        assertSame(Station.HAND, salve.station);
        assertEquals(UrbanCrafting.RECIPES, recipes.subList(0, UrbanCrafting.RECIPES.size()),
                "generator 13's recipes come first, unchanged");
        assertEquals(UrbanCrafting.RECIPES.size() + 1, recipes.size());

        Inventory inv = new Inventory(36);
        inv.add(ItemType.MEMBRANE_SAC, 2);
        inv.add(ItemType.FIBER, 1);
        assertEquals(salve.name, CraftingSystem.craft(inv, salve, Set.of(), new HashSet<>()));
        assertEquals(2, inv.count(ItemType.HERBAL_POULTICE));
        assertEquals(1, inv.count(ItemType.MEMBRANE_SAC));
        assertEquals(0, inv.count(ItemType.FIBER));
        assertNull(CraftingSystem.craft(inv, salve, Set.of(), new HashSet<>()), "no fibre left");
    }

    @Test
    void earlierUrbanWorldsKeepTheirFifteenRecipesWithoutIt() {
        assertSame(UrbanCrafting.RECIPES, CraftingSystem.recipesFor(new World(7, World.GEN_URBAN_UNDERGROUND)));
        assertEquals(15, UrbanCrafting.RECIPES.size());
        assertTrue(UrbanCrafting.RECIPES.stream().noneMatch(r -> r.ingredients.containsKey(ItemType.MEMBRANE_SAC)));
    }
}
