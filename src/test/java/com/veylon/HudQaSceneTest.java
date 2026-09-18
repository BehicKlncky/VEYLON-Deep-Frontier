package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.ui.QuestObjectiveView;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HudQaSceneTest {
    @Test
    void survivalFixtureStagesRepeatableNeedsEquipmentAndNavigation() {
        Game g = new Game();
        g.newWorld(20260918L, true);
        HudQaScene scene = new HudQaScene(g);
        scene.stage();
        assertTrue(g.simPaused);
        assertEquals(68, g.player.health);
        assertEquals(24, g.player.thirst);
        assertFalse(g.player.afflictions.isEmpty());
        assertTrue(g.player.carriedWeight() > 10);
        for (int i = 0; i < 9; i++) assertNotNull(g.player.inventory.get(i));
        assertEquals(0.62f, g.player.selected().durabilityFrac(), 0.001f);
        assertEquals(0.43f, g.player.inventory.get(3).freshnessFrac(), 0.001f);
        assertFalse(QuestObjectiveView.navigationLabel(g).isEmpty());
        scene.update(3);
        assertEquals(ItemType.PRIMITIVE_BOW, g.player.selected().type);
        assertEquals(0.72f, g.bowDraw);
        scene.update(7);
        assertEquals(ItemType.MUSKET, g.player.selected().type);
        assertEquals(1.2f, g.reloadTimer);
        scene.update(11);
        assertEquals(ItemType.SCRAP_BOMB, g.player.selected().type);
        scene.update(16);
        g.interactPrompt = null;
        g.miningProgress = 0;
        scene.updateFocus();
        assertEquals(0.58f, g.miningProgress);
        assertTrue(g.interactPrompt.startsWith("[F]"));
    }

    @Test
    void creativeFixtureKeepsEnvironmentAndFlightWithoutMedicalPressure() {
        Game g = new Game();
        g.newWorld(20260918L, true, GameMode.CREATIVE);
        new HudQaScene(g).stage();
        assertTrue(g.player.abilities.flying());
        assertTrue(g.player.afflictions.isEmpty());
        assertEquals(g.player.maxHealth, g.player.health);
        assertEquals(8.4f, g.player.envTemp);
        assertTrue(g.player.shelter.roofed());
    }

    @Test
    void unselectedSceneDoesNotTouchOrdinaryGameplay() {
        Game g = new Game();
        HudQaScene scene = new HudQaScene(g);
        scene.update(16);
        scene.updateFocus();
        assertFalse(g.simPaused);
        assertNull(g.player);
    }
}
