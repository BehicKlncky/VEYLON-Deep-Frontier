package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.world.World;

/**
 * Runs the {@code urban_slice} route headlessly in the order of {@link Game#run}: the scene,
 * the hotkeys, the frame's control and simulation step, chunk streaming at the render radius,
 * then the end of the frame. Only rendering, audio output and the window are absent.
 */
final class UrbanSliceRun {
    static final float DT = 1f / 60f;

    final Game game = new Game();
    final UrbanQaScenes scenes;
    final HotkeyRouter router;
    double time;

    UrbanSliceRun(long seed, GameMode mode) {
        this(seed, mode, World.GEN_URBAN_ENCOUNTERS);
    }

    UrbanSliceRun(long seed, GameMode mode, int generator) {
        game.newWorld(seed, true, generator, mode);
        game.appState = Game.AppState.PLAYING;
        scenes = new UrbanQaScenes(game);
        router = new HotkeyRouter(game);
        scenes.stage("urban_slice");
    }

    UrbanSliceQaScene slice() { return scenes.slice(); }

    /** Plays frames until the route reports; a failed route throws from the scene. */
    UrbanSliceRun play() {
        while (!slice().reported()) {
            if (time > UrbanSliceQaScene.DEADLINE_SECONDS + 5) { throw new AssertionError("route never reported"); }
            frame();
        }
        return this;
    }

    void frame() {
        scenes.update(time);
        if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
        game.playStep(DT, DT);
        game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
        game.input.endFrame();
        time += DT;
    }
}
