package com.veylon.entity;

import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerTreatmentSystemTest {

    private Player player;
    private PlayerTreatmentSystem treatments;

    @BeforeEach
    void setUp() {
        player = new Player(new World(1L, World.GEN_LEGACY));
        treatments = new PlayerTreatmentSystem();
    }

    @Test
    void repeatedAfflictionUsesLongestDurationInsteadOfStackingDamageInstances() {
        player.addAffliction(Affliction.BURN, 10f);
        player.addAffliction(Affliction.BURN, 4f);
        player.addAffliction(Affliction.BURN, 25f);

        assertEquals(1, player.afflictions.size());
        assertEquals(25f, player.afflictions.get(Affliction.BURN), 0.001f);
    }

    @Test
    void bandageStopsBleedingCleansWoundAndRestoresSmallHealthAmount() {
        player.health = 80f;
        player.addAffliction(Affliction.BLEEDING, 30f);

        PlayerTreatmentSystem.Result result = treatments.apply(
                player, new ItemStack(ItemType.BANDAGE, 1));

        assertEquals(PlayerTreatmentSystem.Result.BANDAGED, result);
        assertFalse(player.has(Affliction.BLEEDING));
        assertTrue(player.woundClean);
        assertEquals(83f, player.health, 0.001f);
    }

    @Test
    void poulticeReducesInfectionDurationWithoutAddingAnotherInstance() {
        player.addAffliction(Affliction.INFECTION, 100f);

        PlayerTreatmentSystem.Result result = treatments.apply(
                player, new ItemStack(ItemType.HERBAL_POULTICE, 1));

        assertEquals(PlayerTreatmentSystem.Result.INFECTION_REDUCED, result);
        assertEquals(40f, player.afflictions.get(Affliction.INFECTION), 0.001f);
        assertEquals(1, player.afflictions.size());
    }

    @Test
    void medicineClearsEverySupportedIllnessInOneTreatment() {
        player.addAffliction(Affliction.FOOD_POISONING, 20f);
        player.addAffliction(Affliction.SICKNESS, 30f);
        player.addAffliction(Affliction.INFECTION, 40f);

        PlayerTreatmentSystem.Result result = treatments.apply(
                player, new ItemStack(ItemType.MEDICINE, 1));

        assertEquals(PlayerTreatmentSystem.Result.ILLNESS_CURED, result);
        assertFalse(player.has(Affliction.FOOD_POISONING));
        assertFalse(player.has(Affliction.SICKNESS));
        assertFalse(player.has(Affliction.INFECTION));
    }

    @Test
    void treatmentIsNotConsumedWhenMatchingConditionIsAbsent() {
        PlayerTreatmentSystem.Result result = treatments.apply(
                player, new ItemStack(ItemType.SPLINT, 1));

        assertEquals(PlayerTreatmentSystem.Result.NO_SPRAIN, result);
        assertFalse(result.used());
    }
}
