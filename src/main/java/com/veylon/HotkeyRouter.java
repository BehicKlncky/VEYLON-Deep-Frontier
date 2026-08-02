package com.veylon;

import com.veylon.entity.PlayerConstants;
import com.veylon.save.SaveSystem;

import static org.lwjgl.glfw.GLFW.*;

/**
 * The global keys: screens, debug overlays, quick save/load and hotbar
 * selection.
 *
 * <p>Ordering here is the whole behaviour, so it is worth being explicit about
 * the three rules the sequence encodes:
 *
 * <ol>
 *   <li><b>The graphics options screen owns every key while it is open.</b> It
 *       binds Escape and F5 itself, so routing them here would both close the
 *       screen and quick-save.</li>
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
        if (game.uiMode == Game.UiMode.OPTIONS) {
            return; // GraphicsOptionsScreen owns Escape/F5/navigation while open.
        }
        if (game.uiMode == Game.UiMode.PAUSE && game.input.wasKeyPressed(GLFW_KEY_O)) {
            game.graphicsOptionsScreen.open(game.renderer.settings,
                    game.window.windowedWidth(), game.window.windowedHeight());
            game.uiMode = Game.UiMode.OPTIONS;
            return;
        }
        handleEscape();
        if (game.sleeping) {
            return;
        }
        handleScreenToggles();
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
            game.sleeping = false;
            game.log("You wake up early.");
        } else if (game.uiMode == Game.UiMode.NONE) {
            game.uiMode = Game.UiMode.PAUSE;
        } else {
            game.closeScreens();
        }
    }

    private void handleScreenToggles() {
        if (game.input.wasKeyPressed(GLFW_KEY_E)) {
            toggle(Game.UiMode.INVENTORY);
            game.inventoryScreen.reset();
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

    private void handleQuickSaveLoad() {
        if (game.input.wasKeyPressed(GLFW_KEY_F5)) {
            game.log(SaveSystem.save(game) ? "Game saved." : "Save FAILED (see console).");
        }
        if (game.input.wasKeyPressed(GLFW_KEY_F9)) {
            if (SaveSystem.load(game)) {
                game.closeScreens();
                game.log("Game loaded.");
            } else {
                game.log("No save found (or load failed).");
            }
        }
    }

    /** Keys whose meaning depends on which screen is open. */
    private void handleContextualKeys() {
        if (game.uiMode == Game.UiMode.PAUSE && game.input.wasKeyPressed(GLFW_KEY_Q)) {
            game.window.requestClose();
        }
        // F closes a crate or NPC screen, mirroring the key that opened it.
        if ((game.uiMode == Game.UiMode.CRATE || game.uiMode == Game.UiMode.NPC)
                && game.input.wasKeyPressed(GLFW_KEY_F)) {
            game.closeScreens();
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
        }
    }
}
