package com.veylon;

import com.veylon.engine.Input;
import com.veylon.ui.AudioOptionsScreen;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

class AudioMenuRoutingTest {
    @Test void pauseAudioShortcutAndOwnedKeysNeverReachGameplay() throws Exception {
        var game = new Game(); game.uiMode = Game.UiMode.PAUSE;
        var field = Input.class.getDeclaredField("keyPressed"); field.setAccessible(true);
        boolean[] pressed = (boolean[]) field.get(game.input);
        pressed[GLFW_KEY_V] = true;
        var router = new HotkeyRouter(game); router.update();
        assertEquals(Game.UiMode.AUDIO_OPTIONS, game.uiMode);
        pressed[GLFW_KEY_ESCAPE] = true; pressed[GLFW_KEY_F5] = true;
        router.update();
        assertEquals(Game.UiMode.AUDIO_OPTIONS, game.uiMode, "Screen owns Escape and F5, so no quick-save occurs");
        game.frontend.handlePauseAudio(AudioOptionsScreen.Action.CANCEL);
        assertEquals(Game.UiMode.PAUSE, game.uiMode);
    }

    @Test void titleAudioIsAWorldlessFrontendState() {
        var game = new Game(); game.frontend.openForQa("audio");
        assertEquals(Game.AppState.TITLE_AUDIO_OPTIONS, game.appState);
        assertTrue(game.frontend.owns(game.appState)); assertFalse(game.appState.rendersWorld());
        assertNull(game.world); assertNull(game.player);
    }
}
