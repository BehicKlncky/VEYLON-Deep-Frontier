package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerAbilities;
import com.veylon.item.ItemStack;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** R1, R7, R15: policy must not change generation or inherit another world's mode. */
class GameModeModelTest {
    @Test
    void abilitiesDeriveFromOneModeAndSurvivalAlwaysEndsFlight() {
        PlayerAbilities abilities = new PlayerAbilities();
        assertFalse(abilities.invulnerable());
        assertTrue(abilities.perceivableByAi());
        abilities.setFlying(true);
        assertFalse(abilities.flying(), "R15: Survival cannot restore flight");
        abilities.apply(GameMode.CREATIVE);
        assertTrue(abilities.invulnerable() && abilities.mayFly() && abilities.instantBuild()
                && abilities.unlimitedItems() && !abilities.perceivableByAi(),
                "R1: Creative derives the complete fixed ruleset");
        assertFalse(abilities.flying(), "R15: entering Creative alone does not take off");
        abilities.setFlying(true);
        abilities.apply(GameMode.SURVIVAL);
        assertFalse(abilities.flying() || abilities.invulnerable() || abilities.mayFly()
                || abilities.instantBuild() || abilities.unlimitedItems(),
                "R1: Survival must remove every Creative ability");
        assertTrue(abilities.perceivableByAi());
    }

    @Test
    void switchesMarkPermanentlyAndRestoreDoesNotHealOrLog() {
        Game game = new Game();
        game.newWorld(20260910, true);
        assertTrue(game.switchGameMode(GameMode.CREATIVE), "R1: first switch succeeds");
        int lines = game.eventLog.recent(30).size();
        assertFalse(game.switchGameMode(GameMode.CREATIVE), "R1: repeating a mode is inert");
        assertEquals(lines, game.eventLog.recent(30).size(), "R1: repeated switches cannot spam logs");
        assertTrue(game.switchGameMode(GameMode.SURVIVAL));
        assertTrue(game.creativeMarked(), "R1: returning to Survival keeps the mark");
        game.player.health = 73f;
        lines = game.eventLog.recent(30).size();
        game.restoreGameMode(GameMode.CREATIVE, true, true);
        assertEquals(73f, game.player.health, "R1: restoration never runs switch healing");
        assertEquals(lines, game.eventLog.recent(30).size(), "R1: restoration emits no switch log");
        assertTrue(game.player.abilities.flying(), "R15: Creative flight restores");
        game.restoreGameMode(GameMode.SURVIVAL, false, true);
        assertFalse(game.creativeMarked(), "R1: loaded world history replaces outgoing history");
        assertFalse(game.player.abilities.flying(), "R15: Survival rejects stored flight");
    }

    @Test
    void initialModeLeavesTerrainCampKitWildlifeAndAiDrawsIdentical() {
        Game survival = new Game();
        Game creative = new Game();
        survival.newWorld(20260910, true);
        creative.newWorld(20260910, true, GameMode.CREATIVE);
        assertEquals(survival.player.pos, creative.player.pos, "R7: mode cannot move spawn");
        assertEquals(survival.world.campPos, creative.world.campPos, "R7: mode cannot move camp");
        assertEquals(survival.world.changedBlocks, creative.world.changedBlocks,
                "R7: camp and initial world edits must be identical");
        assertEquals(survival.world.settlements.keySet(), creative.world.settlements.keySet(),
                "R7: initial settlement planning must be identical");
        assertEquals(survival.world.loadedCount(), creative.world.loadedCount());
        for (Chunk chunk : survival.world.loadedChunks()) {
            Chunk twin = creative.world.getChunk(chunk.cx, chunk.cz);
            assertNotNull(twin, "R7: both modes must generate each spawn chunk");
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    for (int y = 0; y < Chunk.SY; y++) {
                        assertEquals(chunk.get(x, y, z), twin.get(x, y, z),
                                "R7: initial terrain must be bit-identical");
                    }
                }
            }
        }
        for (int i = 0; i < survival.player.inventory.size(); i++) {
            ItemStack a = survival.player.inventory.get(i);
            ItemStack b = creative.player.inventory.get(i);
            if (a == null) assertNull(b, "R7: Creative cannot add extra starter items");
            else {
                assertNotNull(b);
                assertEquals(a.type, b.type); assertEquals(a.count, b.count);
                assertEquals(a.durability, b.durability); assertEquals(a.freshness, b.freshness);
                assertEquals(a.charge, b.charge);
            }
        }
        assertEquals(survival.entities.creatureCount(), creative.entities.creatureCount());
        for (int i = 0; i < survival.entities.creatureCount(); i++) {
            var a = survival.entities.creatures.get(i);
            var b = creative.entities.creatures.get(i);
            assertEquals(a.type, b.type); assertEquals(a.pos, b.pos);
            assertEquals(a.health, b.health); assertEquals(a.hunger, b.hunger);
            assertEquals(a.yaw, b.yaw);
        }
        assertEquals(survival.entities.npcCount(), creative.entities.npcCount());
        for (int i = 0; i < survival.entities.npcCount(); i++) {
            var a = survival.entities.npcs.get(i);
            var b = creative.entities.npcs.get(i);
            assertEquals(a.name, b.name); assertEquals(a.pos, b.pos);
            assertEquals(a.archetype, b.archetype); assertEquals(a.health, b.health);
        }
        for (int i = 0; i < 16; i++) {
            assertEquals(survival.entities.nextCreatureAiFloat(), creative.entities.nextCreatureAiFloat());
            assertEquals(survival.entities.nextNpcAiFloat(), creative.entities.nextNpcAiFloat());
            assertEquals(survival.entities.nextSettledNpcAiFloat(), creative.entities.nextSettledNpcAiFloat());
        }
        assertEquals(GameMode.CREATIVE, creative.gameMode());
        assertTrue(creative.creativeMarked(), "R1: a newly created Creative world is marked");
    }

    @Test
    void automationParsingUsesRootLocaleAndRejectsMistakes() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals(GameMode.SURVIVAL, AutomatedRunDriver.configuredGameMode(null));
            assertEquals(GameMode.SURVIVAL, AutomatedRunDriver.configuredGameMode(" SURVIVAL "));
            assertEquals(GameMode.CREATIVE, AutomatedRunDriver.configuredGameMode("CREATIVE"));
            for (String invalid : new String[]{"", "builder", "creativee"}) {
                IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                        () -> AutomatedRunDriver.configuredGameMode(invalid));
                assertTrue(error.getMessage().contains("VEYLON_GAME_MODE"));
            }
            assertThrows(IllegalArgumentException.class, () -> GameMode.fromId("CREATIVE"),
                    "R1: saved mode IDs are strict stable IDs, not localized names");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
