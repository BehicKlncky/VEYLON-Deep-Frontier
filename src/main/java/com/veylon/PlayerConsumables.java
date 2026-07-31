package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.Player;
import com.veylon.entity.PlayerTreatmentSystem;
import com.veylon.item.EquipSlot;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;

import java.util.Random;

/**
 * Using a held item on yourself: eating, drinking, treating wounds and wearing
 * gear. These are the branches of the secondary (right-click) action that act
 * on the player rather than on the world.
 *
 * <p>Food poisoning risk is the reason eating is not a one-liner: it depends on
 * both the item type and how far the stack has spoiled, and the same affliction
 * can also arrive from untreated water.
 */
final class PlayerConsumables {

    /** Hunger at or above which eating is refused, so food is not wasted. */
    private static final float TOO_FULL_HUNGER = 98f;
    /** Ceiling shared by hunger, thirst and the nutrition meters. */
    private static final float MAX_NEED = 100f;
    /** A fully spoiled stack still restores this fraction of its food value. */
    private static final float SPOILED_FOOD_FLOOR = 0.5f;
    /** Protein gained per point of food from meat. */
    private static final float MEAT_PROTEIN_RATIO = 0.9f;
    /** Vitamins gained per point of food from plants. */
    private static final float PLANT_VITAMIN_RATIO = 1.1f;

    // Food poisoning chances by cause.
    private static final float SPOILED_MEAT_POISON_CHANCE = 0.75f;
    private static final float RAW_MEAT_POISON_CHANCE = 0.25f;
    private static final float STALE_FOOD_POISON_CHANCE = 0.35f;
    /** Freshness below which any food risks poisoning. */
    private static final float STALE_FRESHNESS = 0.3f;
    /** Freshness below which the log warns the food is going off. */
    private static final float GOING_OFF_FRESHNESS = 0.5f;
    static final float POISON_DURATION_MIN = 90f;
    static final float POISON_DURATION_RANGE = 60f;

    // Drinking.
    private static final float CLEAN_WATER_THIRST = 60f;
    private static final float DIRTY_WATER_THIRST = 35f;
    private static final double DIRTY_WATER_POISON_CHANCE = 0.30;

    private final Game game;
    /**
     * Poisoning rolls. This collaborator shares {@code Game}'s lifetime rather
     * than a world's, so it needs the explicit reseed hook the simulation
     * systems have; a {@code Math.random()} here meant that whether a meal
     * poisoned you was the one thing a replayed seed would not reproduce.
     */
    private final Random rng = new Random();

    PlayerConsumables(Game game) {
        this.game = game;
    }

    /** Reseeded per world by {@code Game.reseedSimulation}. */
    void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    void eat(ItemStack held) {
        Player p = game.player;
        if (p.hunger > TOO_FULL_HUNGER) {
            return;
        }
        ItemType t = held.type;
        float freshness = held.freshnessFrac();
        p.hunger = Math.min(MAX_NEED,
                p.hunger + t.food * (SPOILED_FOOD_FLOOR + SPOILED_FOOD_FLOOR * freshness));
        p.thirst = Math.min(MAX_NEED, p.thirst + t.hydration);
        switch (t.group) {
            case MEAT -> p.protein = Math.min(MAX_NEED, p.protein + t.food * MEAT_PROTEIN_RATIO);
            case PLANT -> p.vitamins = Math.min(MAX_NEED,
                    p.vitamins + t.food * PLANT_VITAMIN_RATIO);
            case NONE -> {
            }
        }

        float poisonChance = poisonChanceFor(t, freshness);
        if (poisonChance > 0 && rng.nextFloat() < poisonChance) {
            p.addAffliction(Affliction.FOOD_POISONING, rollPoisonDuration(rng));
            game.log("That food didn't sit well... FOOD POISONING sets in.");
        }
        p.inventory.shrink(p.hotbarSel, 1);
        game.audio.playEat();
        game.log("Ate " + t.displayName + " (+" + t.food + " food"
                + (freshness < GOING_OFF_FRESHNESS && t.spoils() ? ", going off" : "") + ")");
    }

    private static float poisonChanceFor(ItemType t, float freshness) {
        if (t == ItemType.SPOILED_MEAT) {
            return SPOILED_MEAT_POISON_CHANCE;
        }
        if (t == ItemType.RAW_MEAT) {
            return RAW_MEAT_POISON_CHANCE;
        }
        return freshness < STALE_FRESHNESS ? STALE_FOOD_POISON_CHANCE : 0f;
    }

    /**
     * Shared by every food-poisoning source so they all last the same range.
     * The generator is a parameter because the callers live in different
     * systems, and each owns its own per-world stream.
     */
    static float rollPoisonDuration(Random rng) {
        return POISON_DURATION_MIN + rng.nextFloat() * POISON_DURATION_RANGE;
    }

    void drink(ItemStack held) {
        Player p = game.player;
        boolean clean = held.type == ItemType.WATERSKIN_CLEAN;
        p.thirst = Math.min(MAX_NEED,
                p.thirst + (clean ? CLEAN_WATER_THIRST : DIRTY_WATER_THIRST));
        p.inventory.shrink(p.hotbarSel, 1);
        p.inventory.add(ItemType.WATERSKIN_EMPTY, 1);
        game.audio.playDrink();
        if (clean) {
            game.log("You drink clean water (+" + (int) CLEAN_WATER_THIRST + " thirst).");
        } else if (rng.nextFloat() < DIRTY_WATER_POISON_CHANCE) {
            p.addAffliction(Affliction.FOOD_POISONING, rollPoisonDuration(rng));
            game.log("The dirty water churns in your gut... FOOD POISONING.");
        } else {
            game.log("You drink dirty water (+" + (int) DIRTY_WATER_THIRST
                    + " thirst). You got lucky this time.");
        }
    }

    void applyMedical(ItemStack held) {
        PlayerTreatmentSystem.Result result = game.playerTreatments.apply(game.player, held);
        if (result != PlayerTreatmentSystem.Result.INVALID) {
            game.log(result.message());
        }
        if (result.used()) {
            game.player.inventory.shrink(game.player.hotbarSel, 1);
            game.audio.playEquip();
        }
    }

    void equipHeld(ItemStack held) {
        EquipSlot slot = held.type.equipSlot;
        ItemStack prev = game.player.equipment[slot.ordinal()];
        game.player.equipment[slot.ordinal()] = held.copy();
        game.player.equipment[slot.ordinal()].count = 1;
        game.player.inventory.shrink(game.player.hotbarSel, 1);
        if (prev != null) {
            game.player.inventory.addStack(prev);
        }
        game.audio.playEquip();
        game.log("Equipped " + held.type.displayName + " (" + slot.displayName + ").");
    }
}
