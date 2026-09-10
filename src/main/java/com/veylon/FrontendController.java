package com.veylon;

import com.veylon.save.SaveSystem;
import com.veylon.ui.GraphicsOptionsScreen;
import com.veylon.ui.PresentationOverlay;

/**
 * The states with no world in them: title, graphics options and loading, plus
 * the transitions in and out of a live game.
 *
 * <p>These share one property that makes them worth separating from the play
 * loop: {@code world} and {@code player} are null throughout, so nothing here
 * may touch simulation state. {@code Game.frame} short-circuits into
 * {@link #frame} before any of it is dereferenced.
 *
 * <p>Loading is deliberately split across two frames.
 * {@link #beginLoading} only sets the state; the loading screen is drawn and
 * swapped, and only then does {@link #completeLoading} do the synchronous
 * worldgen or deserialization. Doing both in one frame would show the player a
 * frozen title screen for the duration and call it a loading screen.
 */
final class FrontendController {

    /** Which of the two paths into a live world the player asked for. */
    private enum LoadRequest {
        NEW_GAME, LOAD_GAME
    }

    private final Game game;
    private LoadRequest loadRequest;
    /** True once a loading frame has actually reached the screen. */
    private boolean loadingPresented;

    FrontendController(Game game) {
        this.game = game;
    }

    /** True while the frontend owns the frame, i.e. no world exists. */
    boolean owns(Game.AppState state) {
        return state == Game.AppState.TITLE || state == Game.AppState.TITLE_OPTIONS
                || state == Game.AppState.LOADING;
    }

    void frame(float dt) {
        game.window.captureCursor(false, game.input);
        game.audio.update(dt);
        game.beginUiFrame();
        switch (game.appState) {
            case TITLE -> updateTitle();
            case TITLE_OPTIONS -> updateOptions(dt);
            default -> presentLoading();
        }
        game.ui.end();
    }

    private void updateTitle() {
        switch (game.titleScreen.update(game)) {
            case NEW_GAME -> beginLoading(LoadRequest.NEW_GAME);
            case LOAD_GAME -> beginLoading(LoadRequest.LOAD_GAME);
            case OPTIONS -> {
                game.graphicsOptionsScreen.open(game.renderer.settings,
                        game.window.windowedWidth(), game.window.windowedHeight());
                game.appState = Game.AppState.TITLE_OPTIONS;
            }
            case QUIT -> game.window.requestClose();
            case NONE -> {
            }
        }
    }

    private void updateOptions(float dt) {
        game.qa.updateTitleOptionsQa(dt);
        GraphicsOptionsScreen.Result result = game.graphicsOptionsScreen.update(game);
        if (result.action() == GraphicsOptionsScreen.Action.APPLY) {
            applyGraphicsOptions(result);
            game.titleScreen.notice("Graphics settings applied.");
            game.appState = Game.AppState.TITLE;
        } else if (result.action() == GraphicsOptionsScreen.Action.CANCEL) {
            game.appState = Game.AppState.TITLE;
        }
    }

    private void presentLoading() {
        String detail = loadRequest == LoadRequest.LOAD_GAME
                ? "Restoring your frontier..." : "Mapping atmosphere and terrain...";
        PresentationOverlay.loading(game.ui, detail, game.totalTime);
        loadingPresented = true;
    }

    private void beginLoading(LoadRequest request) {
        loadRequest = request;
        loadingPresented = false;
        game.appState = Game.AppState.LOADING;
    }

    /** True when the run loop should now do the synchronous load work. */
    boolean loadingFramePresented() {
        return game.appState == Game.AppState.LOADING && loadingPresented;
    }

    /**
     * Runs after a loading frame has been swapped, which is what keeps the
     * synchronous work honest: the player is looking at a loading screen while
     * it happens rather than at a frozen menu.
     */
    void completeLoading() {
        loadingPresented = false;
        boolean loaded;
        if (loadRequest == LoadRequest.LOAD_GAME) {
            loaded = SaveSystem.load(game);
        } else {
            game.newWorld(game.sessionSeed, true);
            loaded = true;
        }
        loadRequest = null;
        if (loaded) {
            game.appState = Game.AppState.PLAYING;
            game.closeScreens();
            game.window.captureCursor(true, game.input);
        } else {
            // A failed load has already replaced the previous world, so there is
            // nothing to fall back to; drop to the title rather than to a
            // half-restored game.
            game.audio.resetWorld();
            game.releaseWorldMeshes();
            game.world = null;
            game.player = null;
            game.titleScreen.notice("No compatible save was found.");
            game.appState = Game.AppState.TITLE;
        }
    }

    /** The in-game pause menu's options screen, which returns to PAUSE, not TITLE. */
    void handlePauseOptions(GraphicsOptionsScreen.Result result) {
        if (result.action() == GraphicsOptionsScreen.Action.NONE) {
            return;
        }
        if (result.action() == GraphicsOptionsScreen.Action.APPLY) {
            applyGraphicsOptions(result);
        }
        game.uiMode = Game.UiMode.PAUSE;
    }

    void applyGraphicsOptions(GraphicsOptionsScreen.Result result) {
        game.renderer.settings.windowWidth = result.width();
        game.renderer.settings.windowHeight = result.height();
        game.window.setWindowedResolution(result.width(), result.height());
        game.window.setFullscreen(game.renderer.settings.fullscreen);
        game.window.setVsync(game.renderer.settings.vsync);
        game.renderer.settings.save();
    }

    /** Abandons the live world and returns to the title screen. */
    void returnToTitle() {
        game.audio.resetWorld();
        game.releaseWorldMeshes();
        game.world = null;
        game.player = null;
        game.particles.count = 0;
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.tracks.clear();
        game.closeScreens();
        game.simPaused = false;
        game.appState = Game.AppState.TITLE;
        game.titleScreen.notice("");
    }

    /** Opens the frontend on a specific state, for the VEYLON_FRONTEND QA captures. */
    void openForQa(String screen) {
        switch (screen) {
            case "options" -> {
                game.graphicsOptionsScreen.open(game.renderer.settings,
                        game.window.windowedWidth(), game.window.windowedHeight());
                game.appState = Game.AppState.TITLE_OPTIONS;
            }
            case "loading" -> {
                game.appState = Game.AppState.LOADING;
                game.qa.setStaticLoadingQa(true);
            }
            case "death", "victory" -> {
                game.newWorld(game.sessionSeed, true);
                game.appState = "death".equals(screen)
                        ? Game.AppState.DEATH : Game.AppState.VICTORY;
                game.deathTimer = 30f;
            }
            case "glyphs" -> game.titleScreen.notice(
                    "Türkçe glif doğrulama: Çığ, İĞÜÖŞ, çğıöşü");
            default -> {
            }
        }
    }
}
