package com.veylon;

import com.veylon.ai.Quest;
import com.veylon.entity.Affliction;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.simulation.ShelterSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Raycaster;

/** Paused, opt-in HUD fixture. Wall-clock phases select fixed presentation states. */
final class HudQaScene {
    private final Game game;
    private boolean active;
    private boolean mining;

    HudQaScene(Game game) {
        this.game = game;
    }

    void stage() {
        active = true;
        game.simPaused = true;
        var p = game.player;
        p.inventory.clear();
        ItemType[] items = {ItemType.PRIMITIVE_BOW, ItemType.MUSKET, ItemType.STONE_AXE,
                ItemType.BERRY, ItemType.COOKED_MEAT, ItemType.WATERSKIN_CLEAN,
                ItemType.BANDAGE, ItemType.PLANK, ItemType.SCRAP_BOMB};
        int[] counts = {1, 1, 1, 12, 3, 2, 6, 8, 3};
        for (int i = 0; i < items.length; i++) {
            var stack = new ItemStack(items[i], counts[i]);
            if (stack.type.hasDurability()) stack.durability *= 0.62f;
            if (stack.type.spoils()) stack.freshness *= 0.43f;
            p.inventory.set(i, stack);
        }
        p.inventory.set(9, new ItemStack(ItemType.ARROW, 24));
        p.inventory.set(10, new ItemStack(ItemType.IRON_ARROW, 8));
        p.inventory.set(11, new ItemStack(ItemType.MUSKET_BALL, 12));
        p.envTemp = 8.4f;
        p.shelter = new ShelterSystem.Shelter(true, 0.3f, false);
        if (p.abilities.invulnerable()) {
            p.abilities.setFlying(true);
        } else {
            p.health = 68;
            p.hunger = 42;
            p.thirst = 24;
            p.stamina = 57;
            p.protein = 38;
            p.vitamins = 62;
            p.bodyTemp = 35.8f;
            p.wetness = 0.36f;
            p.fatigue = 31;
            p.afflictions.clear();
            p.addAffliction(Affliction.SPRAIN, 124);
        }
        game.faction.campPos = new Vec3i((int) p.pos.x + 80, (int) p.pos.y, (int) p.pos.z - 120);
        game.faction.quest = new Quest(Quest.Type.HUNT_PREDATOR, null, 3, 1800,
                10, ItemType.BANDAGE, 2, "Frontier camp")
                .bind("hud-showcase", "camp", Quest.NO_SETTLEMENT, "camp");
        game.faction.quest.targetPoiId = "camp-region:hud-showcase";
        game.faction.quest.progress = 1;
        game.eventLog.clear();
        game.log("Request accepted: secure the camp perimeter.");
        game.log("A sheltered route lies ahead.");
        update(0);
    }

    void update(double elapsed) {
        if (!active) return;
        // No simulation step, RNG or production input is involved in these poses.
        int phase = Math.min(3, (int) (elapsed / 5));
        game.player.hotbarSel = switch (phase) { case 0 -> 0; case 1 -> 1; case 2 -> 8; default -> 2; };
        game.drawingBow = phase == 0;
        game.bowDraw = phase == 0 ? 0.72f : 0;
        game.reloadTotal = phase == 1 ? 3 : 0;
        game.reloadTimer = phase == 1 ? 1.2f : 0;
        mining = phase == 3;
        updateFocus();
    }

    /** Applied after the paused-frame input path clears transient focus feedback. */
    void updateFocus() {
        if (!active) return;
        game.interactPrompt = "[F] Inspect frontier cache";
        game.targetHit = new Raycaster.Hit((int) game.player.pos.x, (int) game.player.pos.y,
                (int) game.player.pos.z - 3, 0, 0, 1, 3, BlockType.CRATE);
        game.miningProgress = mining ? 0.58f : 0;
    }
}
