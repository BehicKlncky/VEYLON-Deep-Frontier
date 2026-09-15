package com.veylon;

import com.veylon.engine.Input;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.ui.GameModeScreen;
import com.veylon.ui.NewFrontierScreen;
import com.veylon.ui.PauseMenu;
import com.veylon.ui.TitleScreen;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** R2-R7: worldless mode choice, the paused confirmation and the permanent Creative mark. */
class ModeSelectionRoutingTest {
    @TempDir Path temporary;

    @Test
    void newFrontierOpensAWorldlessChoiceAndEscapeReturnsToTheTitle() throws Exception {
        Game game = new Game();
        game.frontend.savePath = temporary.resolve("absent.sav");
        game.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertEquals(Game.AppState.TITLE_NEW_WORLD, game.appState, "R2: NEW FRONTIER and N open the mode choice");
        assertTrue(game.frontend.owns(game.appState), "R2: the frontend owns the worldless choice");
        assertFalse(game.appState.rendersWorld(), "R2: no world is drawn behind the choice");
        assertNull(game.world, "R2: opening the choice creates no world");
        assertEquals(GameMode.SURVIVAL, game.newFrontierScreen.selectedMode(), "R2: Survival is the default");
        assertFalse(game.newFrontierScreen.showsReplaceNotice(), "D7: no replace notice without a save file");

        game.frontend.applyNewFrontier(press(game, game.newFrontierScreen, GLFW_KEY_ESCAPE));
        assertEquals(Game.AppState.TITLE, game.appState, "R2: Escape returns to the title");
        assertNull(game.world, "R2: going back creates no world");
        assertNull(game.player);
    }

    @Test
    void keysChooseTheModeAndEnterStarts() throws Exception {
        Game game = new Game();
        NewFrontierScreen screen = game.newFrontierScreen;
        screen.open(false);
        assertEquals(NewFrontierScreen.Action.NONE, press(game, screen, GLFW_KEY_C));
        assertEquals(GameMode.CREATIVE, screen.selectedMode(), "R2: C chooses Creative");
        press(game, screen, GLFW_KEY_S);
        assertEquals(GameMode.SURVIVAL, screen.selectedMode(), "R2: S chooses Survival");
        press(game, screen, GLFW_KEY_RIGHT);
        assertEquals(GameMode.CREATIVE, screen.selectedMode(), "R2: arrows choose");
        press(game, screen, GLFW_KEY_TAB);
        assertEquals(GameMode.SURVIVAL, screen.selectedMode(), "R2: Tab moves between the cards");
        press(game, screen, GLFW_KEY_DOWN);
        assertEquals(GameMode.CREATIVE, screen.selectedMode(), "R2: vertical arrows also move");
        assertEquals(NewFrontierScreen.Action.START, press(game, screen, GLFW_KEY_ENTER), "R2: Enter starts");

        screen.open(true);
        assertEquals(GameMode.SURVIVAL, screen.selectedMode(), "R2: every opening defaults to Survival");
        assertTrue(screen.showsReplaceNotice(), "D7: an existing save shows the replace notice");
    }

    @Test
    void startingCreativeBuildsACreativeWorldWithTheSurvivalTerrainCampAndKit() throws Exception {
        Game creative = startFromNewFrontier(GLFW_KEY_C);
        Game survival = startFromNewFrontier(GLFW_KEY_S);
        assertEquals(Game.AppState.PLAYING, creative.appState, "R2: the load completes into play");
        assertEquals(GameMode.CREATIVE, creative.gameMode(), "R2: the chosen mode reaches the new world");
        assertTrue(creative.creativeMarked(), "R1: a Creative world starts marked");
        assertEquals(GameMode.SURVIVAL, survival.gameMode());
        assertEquals(survival.spawnPos, creative.spawnPos, "R7: same spawn");
        assertEquals(survival.world.campPos, creative.world.campPos, "R7: same camp");
        for (int dx = -40; dx <= 40; dx += 8) {
            for (int dz = -40; dz <= 40; dz += 8) {
                int x = (int) survival.spawnPos.x + dx, z = (int) survival.spawnPos.z + dz;
                assertEquals(survival.world.surfaceHeight(x, z), creative.world.surfaceHeight(x, z),
                        "R7: same terrain at " + x + "," + z);
            }
        }
        for (ItemType type : ItemType.values()) {
            assertEquals(survival.player.inventory.count(type), creative.player.inventory.count(type),
                    "R7: same starter kit for " + type);
        }
        assertEquals(survival.entities.creatureCount(), creative.entities.creatureCount(),
                "R7: same initial wildlife");
        assertEquals(survival.entities.npcCount(), creative.entities.npcCount(), "R7: same camp residents");
        assertTrue(creative.eventLog.recent(10).stream().noneMatch(line -> line.endsWith("Survive.")),
                "Creative welcome hints replace the Survival instruction");
        assertTrue(survival.eventLog.recent(10).stream().anyMatch(line -> line.endsWith("Survive.")),
                "Survival keeps its welcome lines");
    }

    @Test
    void gOpensTheConfirmationOnlyFromPauseAndItOwnsEveryKey() throws Exception {
        Game game = playing(GameMode.SURVIVAL);
        HotkeyRouter router = new HotkeyRouter(game);
        route(game, router, GLFW_KEY_G);
        assertEquals(Game.UiMode.NONE, game.uiMode, "D1: G is not a gameplay mode switch");

        game.uiMode = Game.UiMode.PAUSE;
        route(game, router, GLFW_KEY_G);
        assertEquals(Game.UiMode.GAME_MODE, game.uiMode, "R3: G opens the confirmation from pause");
        assertTrue(game.uiMode.pausesSimulation(), "R3: the confirmation pauses the world");

        int logLines = game.eventLog.recent(200).size();
        route(game, router, GLFW_KEY_ESCAPE, GLFW_KEY_F5, GLFW_KEY_F9, GLFW_KEY_Q,
                GLFW_KEY_O, GLFW_KEY_V, GLFW_KEY_E, GLFW_KEY_G);
        assertEquals(Game.UiMode.GAME_MODE, game.uiMode,
                "R3: Escape, F5, F9, Q, O and V never reach the router while the confirmation is open");
        assertEquals(logLines, game.eventLog.recent(200).size(), "R3: F5 did not save and F9 did not load");

        game.gameModes.handleScreen(press(game, game.gameModeScreen, GLFW_KEY_ESCAPE));
        assertEquals(Game.UiMode.PAUSE, game.uiMode, "R3: Escape cancels back to pause");
        assertEquals(GameMode.SURVIVAL, game.gameMode(), "R3: cancelling changes nothing");
        assertFalse(game.creativeMarked(), "R3: cancelling cannot mark the world");
    }

    @Test
    void onlyTheFirstSwitchToCreativeWarnsAndMarksTheWorld() throws Exception {
        assertTrue(String.join(" ", GameModeScreen.confirmation(GameMode.SURVIVAL, false))
                        .contains("permanently marks this frontier as a Creative world"),
                "R3: the first switch to Creative warns about the permanent mark");
        assertFalse(String.join(" ", GameModeScreen.confirmation(GameMode.SURVIVAL, true))
                .contains("permanently"), "R3: a marked world gets a short confirmation");
        assertEquals("Hunger, injuries and hostile creatures return.",
                GameModeScreen.confirmation(GameMode.CREATIVE, true)[0],
                "R3: leaving Creative first names what returns");

        Game game = playing(GameMode.SURVIVAL);
        game.uiMode = Game.UiMode.PAUSE;
        game.gameModes.openScreen();
        game.gameModes.handleScreen(press(game, game.gameModeScreen, GLFW_KEY_N));
        assertEquals(GameMode.SURVIVAL, game.gameMode(), "R3: N cancels");
        assertFalse(game.creativeMarked(), "R3: a cancelled switch leaves no mark");

        game.gameModes.openScreen();
        game.gameModes.handleScreen(press(game, game.gameModeScreen, GLFW_KEY_Y));
        assertEquals(GameMode.CREATIVE, game.gameMode(), "R3: Y confirms");
        assertTrue(game.creativeMarked(), "R3: the first activation marks the world");
        assertEquals(Game.UiMode.PAUSE, game.uiMode, "R3: confirming returns to pause");

        game.gameModes.openScreen();
        game.gameModes.handleScreen(press(game, game.gameModeScreen, GLFW_KEY_ENTER));
        assertEquals(GameMode.SURVIVAL, game.gameMode(), "R3: Enter confirms");
        assertTrue(game.creativeMarked(), "R5: leaving Creative keeps the mark");
        assertEquals("SURVIVAL  /  Creative world", PauseMenu.modeLabel(game.gameMode(), game.creativeMarked()),
                "R6: the pause header names the mode and the mark");
        assertEquals("SURVIVAL", PauseMenu.modeLabel(GameMode.SURVIVAL, false), "R6: unmarked Survival");
    }

    @Test
    void theMarkSurvivesSaveAndLoadAfterReturningToSurvival() {
        Game game = playing(GameMode.SURVIVAL);
        assertTrue(game.switchGameMode(GameMode.CREATIVE));
        assertTrue(game.switchGameMode(GameMode.SURVIVAL));
        Path save = temporary.resolve("marked.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(GameMode.SURVIVAL, loaded.gameMode(), "R1: the loaded mode is Survival");
        assertTrue(loaded.creativeMarked(), "R1: the permanent mark survives save and load");
    }

    @Test
    void aFailedLoadAfterChoosingCreativeReturnsToTheTitleWithoutAWorld() throws Exception {
        Game game = new Game();
        Path corrupt = temporary.resolve("corrupt.sav");
        Files.write(corrupt, new byte[]{1, 2, 3, 4});
        game.frontend.savePath = corrupt;
        game.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertTrue(game.newFrontierScreen.showsReplaceNotice(), "D7: an existing save file shows the notice");
        press(game, game.newFrontierScreen, GLFW_KEY_C);
        game.frontend.applyNewFrontier(press(game, game.newFrontierScreen, GLFW_KEY_ESCAPE));

        game.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        assertEquals(Game.AppState.LOADING, game.appState);
        assertFalse(game.frontend.performLoad(), "Fixture: the corrupt save cannot load");
        assertEquals(Game.AppState.TITLE, game.appState, "a failed load returns to the title");
        assertNull(game.world, "a failed load leaves no world");
        assertNull(game.player, "a failed load leaves no player");

        game.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertEquals(GameMode.SURVIVAL, game.newFrontierScreen.selectedMode(),
                "R2: an abandoned Creative choice does not carry over");
    }

    private Game startFromNewFrontier(int modeKey) throws Exception {
        Game game = new Game();
        game.sessionSeed = 20260910L;
        game.frontend.savePath = temporary.resolve("absent.sav");
        game.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        press(game, game.newFrontierScreen, modeKey);
        game.frontend.applyNewFrontier(press(game, game.newFrontierScreen, GLFW_KEY_ENTER));
        assertEquals(Game.AppState.LOADING, game.appState, "R2: Start begins the two-frame loading");
        assertTrue(game.frontend.performLoad(), "R2: the new frontier loads");
        return game;
    }

    private static Game playing(GameMode mode) {
        Game game = new Game();
        game.newWorld(20260910L, true, mode);
        game.appState = Game.AppState.PLAYING;
        return game;
    }

    private static boolean[] pressed(Game game) throws ReflectiveOperationException {
        var field = Input.class.getDeclaredField("keyPressed");
        field.setAccessible(true);
        return (boolean[]) field.get(game.input);
    }

    private static NewFrontierScreen.Action press(Game game, NewFrontierScreen screen, int... keys)
            throws ReflectiveOperationException {
        for (int key : keys) pressed(game)[key] = true;
        NewFrontierScreen.Action action = screen.handleKeys(game.input);
        game.input.endFrame();
        return action;
    }

    private static GameModeScreen.Action press(Game game, GameModeScreen screen, int... keys)
            throws ReflectiveOperationException {
        for (int key : keys) pressed(game)[key] = true;
        GameModeScreen.Action action = screen.handleKeys(game.input);
        game.input.endFrame();
        return action;
    }

    private static void route(Game game, HotkeyRouter router, int... keys) throws ReflectiveOperationException {
        for (int key : keys) pressed(game)[key] = true;
        router.update();
        game.input.endFrame();
    }
}
