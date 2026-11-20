package com.veylon;

import com.veylon.entity.PlayerConstants;
import com.veylon.save.LoadResult;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The global keys: screens, debug overlays, quick save/load and hotbar
 * selection.
 *
 * <p>Ordering here is the whole behaviour, so it is worth being explicit about
 * the three rules the sequence encodes:
 *
 * <ol>
 *   <li><b>The options screens and the game-mode confirmation own every key
 *       while open.</b> They bind Escape, Enter or F5 themselves, so routing
 *       them here would both close the screen and quick-save or quit.</li>
 *   <li><b>Escape is contextual</b>: it wakes a sleeper, opens the pause menu
 *       from gameplay, and otherwise closes whatever screen is open.</li>
 *   <li><b>Sleeping consumes everything except Escape.</b> A player cannot open
 *       the inventory, save or change hotbar slot mid-night; the only way out
 *       is to wake.</li>
 * </ol>
 */
final class HotkeyRouter {

    private final Game game;

    HotkeyRouter(Game game) {
        this.game = game;
    }

    void update() {
        if (game.uiMode == Game.UiMode.OPTIONS || game.uiMode == Game.UiMode.AUDIO_OPTIONS
                || game.uiMode == Game.UiMode.GAME_MODE || game.uiMode == Game.UiMode.CREATIVE_CATALOG
                || game.uiMode == Game.UiMode.WORLD_CONTROLS || game.uiMode == Game.UiMode.AUTOSAVE_OPTIONS) {
            return; // These screens own Escape, F5, typed text and navigation while open.
        }
        if (game.uiMode == Game.UiMode.PAUSE && game.input.wasKeyPressed(GLFW_KEY_G)) {
            game.gameModes.openScreen();
            return;
        }
        if (game.uiMode == Game.UiMode.PAUSE && game.input.wasKeyPressed(GLFW_KEY_T)
                && game.player != null && game.player.abilities.mayFly()) {
            game.creativeControls.openScreen(); // R25; Survival has nothing to control
            return;
        }
        if (game.uiMode == Game.UiMode.PAUSE && game.input.wasKeyPressed(GLFW_KEY_O)) {
            game.graphicsOptionsScreen.open(game.renderer.settings,
                    game.window.windowedWidth(), game.window.windowedHeight());
            game.uiMode = Game.UiMode.OPTIONS;
            return;
        }
        if (game.uiMode == Game.UiMode.PAUSE && game.input.wasKeyPressed(GLFW_KEY_V)) {
            game.audioOptionsScreen.open(game.audio.settings);
            game.uiMode = Game.UiMode.AUDIO_OPTIONS;
            return;
        }
        if (game.uiMode == Game.UiMode.PAUSE && game.input.wasKeyPressed(GLFW_KEY_A)) {
            game.frontend.openPauseAutosave();
            game.input.consumePress(GLFW_KEY_A); // the screen opened this frame reads A as "less"
            return;
        }
        handleEscape();
        if (game.sleeping) {
            return;
        }
        handleScreenToggles();
        if (game.uiMode == Game.UiMode.CREATIVE_CATALOG) {
            return; // Ownership starts in the opening frame, including simultaneous F5/F9.
        }
        handleDebugToggles();
        handleQuickSaveLoad();
        handleContextualKeys();
        if (game.uiMode == Game.UiMode.NONE) {
            handleHotbarSelection();
        }
    }

    private void handleEscape() {
        if (!game.input.wasKeyPressed(GLFW_KEY_ESCAPE)) {
            return;
        }
        if (game.sleeping) {
            game.sleep.wakeEarly();
        } else if (game.uiMode == Game.UiMode.NONE) {
            game.uiMode = Game.UiMode.PAUSE;
        } else {
            game.closeScreens();
        }
    }

    private void handleScreenToggles() {
        if (game.input.wasKeyPressed(GLFW_KEY_E)) {
            if (game.player.abilities.unlimitedItems()) {
                game.creativeCatalogScreen.open();
                toggle(Game.UiMode.CREATIVE_CATALOG); // R16; the open catalog handles its own E
                return;
            } else {
                toggle(Game.UiMode.INVENTORY);
                game.inventoryScreen.reset();
            }
        }
        if (game.input.wasKeyPressed(GLFW_KEY_C)) {
            toggle(Game.UiMode.CRAFTING);
        }
        if (game.input.wasKeyPressed(GLFW_KEY_M)) {
            toggle(Game.UiMode.MAP);
        }
    }

    private void handleDebugToggles() {
        if (game.input.wasKeyPressed(GLFW_KEY_TAB)) {
            game.simPanelShown = !game.simPanelShown;
        }
        if (game.input.wasKeyPressed(GLFW_KEY_F3)) {
            game.debugShown = !game.debugShown;
        }
        if (game.input.wasKeyPressed(GLFW_KEY_F2)) {
            game.pendingScreenshot = true;
        }
        if (game.input.wasKeyPressed(GLFW_KEY_P)) {
            game.simPaused = !game.simPaused;
        }
    }

    /** F5 saves into the active slot and F9 opens it (0.9.1 milestone 04); the log names the slot. */
    private void handleQuickSaveLoad() {
        int slot = game.saves.activeSlot();
        if (game.input.wasKeyPressed(GLFW_KEY_F5)) {
            game.log(game.saves.saveActive() ? SaveSession.savedLine(slot) : SaveSession.saveFailedLine(slot));
        }
        if (game.input.wasKeyPressed(GLFW_KEY_F9)) {
            // A refused or failed load leaves the live world as it was; the log says why.
            LoadResult result = game.saves.openActive();
            if (result.loaded()) {
                game.closeScreens();
            }
            game.log(result.loaded() ? SaveSession.loadedLine(slot) : SaveSession.notLoadedLine(slot, result));
        }
    }

    /** Keys whose meaning depends on which screen is open. */
    private void handleContextualKeys() {
        if (game.uiMode == Game.UiMode.PAUSE && game.input.wasKeyPressed(GLFW_KEY_Q)) {
            game.window.requestClose();
        }
        // F closes a crate or NPC screen, mirroring the key that opened it. The press is
        // consumed so the same frame's interaction pass cannot reopen what it faces.
        if ((game.uiMode == Game.UiMode.CRATE || game.uiMode == Game.UiMode.NPC)
                && game.input.wasKeyPressed(GLFW_KEY_F)) {
            game.closeScreens();
            game.input.consumePress(GLFW_KEY_F);
        }
    }

    private void handleHotbarSelection() {
        for (int i = 0; i < PlayerConstants.HOTBAR_SLOTS; i++) {
            if (game.input.wasKeyPressed(GLFW_KEY_1 + i)) {
                game.player.hotbarSel = i;
            }
        }
        int scroll = (int) game.input.scrollDelta();
        if (scroll != 0) {
            game.player.hotbarSel = Math.floorMod(game.player.hotbarSel - scroll,
                    PlayerConstants.HOTBAR_SLOTS);
        }
    }

    /** Opens {@code mode}, or closes it if it is already the active screen. */
    private void toggle(Game.UiMode mode) {
        game.uiMode = game.uiMode == mode ? Game.UiMode.NONE : mode;
        if (game.uiMode == Game.UiMode.NONE) {
            game.closeScreens();
        } else if (game.uiMode != Game.UiMode.NPC) {
            // Another screen replaced the conversation: its speaker is let go.
            game.activeNpc = null;
        }
    }
}
