package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.save.SaveSystem;
import com.veylon.ui.GraphicsOptionsScreen;
import com.veylon.ui.AudioOptionsScreen;
import com.veylon.ui.NewFrontierScreen;
import com.veylon.ui.PresentationOverlay;
import com.veylon.ui.TitleScreen;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The states with no world in them: title, new-frontier mode choice, graphics
 * and audio options, and loading, plus the transitions in and out of a live game.
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
    /** The mode chosen on the new-frontier screen; transient, consumed by one load. */
    private GameMode newWorldMode = GameMode.SURVIVAL;
    /** The single save slot. Visible for testing so routing tests use an isolated file. */
    Path savePath = SaveSystem.SAVE_PATH;

    FrontendController(Game game) {
        this.game = game;
    }

    /** True while the frontend owns the frame, i.e. no world exists. */
    boolean owns(Game.AppState state) {
        return state == Game.AppState.TITLE || state == Game.AppState.TITLE_OPTIONS
                || state == Game.AppState.TITLE_AUDIO_OPTIONS || state == Game.AppState.LOADING
                || state == Game.AppState.TITLE_NEW_WORLD;
    }

    void frame(float dt) {
        game.window.captureCursor(false, game.input);
        game.audio.update(dt);
        game.beginUiFrame();
        switch (game.appState) {
            case TITLE -> applyTitle(game.titleScreen.update(game));
            case TITLE_NEW_WORLD -> applyNewFrontier(game.newFrontierScreen.update(game));
            case TITLE_OPTIONS -> updateOptions(dt);
            case TITLE_AUDIO_OPTIONS -> updateAudio();
            default -> presentLoading();
        }
        game.ui.end();
    }

    /** Applies a title action; NEW FRONTIER opens the mode choice instead of loading (R2). */
    void applyTitle(TitleScreen.Action action) {
        switch (action) {
            case NEW_GAME -> openNewFrontier(Files.exists(savePath));
            case LOAD_GAME -> beginLoading(LoadRequest.LOAD_GAME);
            case OPTIONS -> {
                game.graphicsOptionsScreen.open(game.renderer.settings,
                        game.window.windowedWidth(), game.window.windowedHeight());
                game.appState = Game.AppState.TITLE_OPTIONS;
            }
            case AUDIO -> {
                game.audioOptionsScreen.open(game.audio.settings);
                game.appState = Game.AppState.TITLE_AUDIO_OPTIONS;
            }
            case QUIT -> game.window.requestClose();
            case NONE -> {
            }
        }
    }

    /** Opens the worldless mode choice; the save check happens once, not every frame. */
    void openNewFrontier(boolean saveExists) {
        game.newFrontierScreen.open(saveExists);
        game.appState = Game.AppState.TITLE_NEW_WORLD;
    }

    /** START carries the chosen mode into the two-frame loading; BACK creates nothing. */
    void applyNewFrontier(NewFrontierScreen.Action action) {
        switch (action) {
            case START -> {
                newWorldMode = game.newFrontierScreen.selectedMode();
                beginLoading(LoadRequest.NEW_GAME);
            }
            case BACK -> game.appState = Game.AppState.TITLE;
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

    private void updateAudio() {
        AudioOptionsScreen.Action result = game.audioOptionsScreen.update(game);
        if (result == AudioOptionsScreen.Action.NONE) return;
        if (result == AudioOptionsScreen.Action.APPLY) {
            game.audio.settings.save();
            game.titleScreen.notice("Audio settings applied.");
        }
        game.appState = Game.AppState.TITLE;
    }

    void handlePauseAudio(AudioOptionsScreen.Action result) {
        if (result == AudioOptionsScreen.Action.NONE) return;
        if (result == AudioOptionsScreen.Action.APPLY) game.audio.settings.save();
        game.uiMode = Game.UiMode.PAUSE;
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
        if (performLoad()) {
            game.window.captureCursor(true, game.input);
        }
    }

    /**
     * The synchronous load and its state transition without the native cursor
     * capture, so headless routing tests exercise the production path.
     *
     * @return true when a playable world is now live
     */
    boolean performLoad() {
        loadingPresented = false;
        boolean loaded;
        if (loadRequest == LoadRequest.LOAD_GAME) {
            loaded = SaveSystem.load(game, savePath);
        } else {
            game.newWorld(game.sessionSeed, true, newWorldMode);
            loaded = true;
        }
        loadRequest = null;
        newWorldMode = GameMode.SURVIVAL;
        if (loaded) {
            game.appState = Game.AppState.PLAYING;
            game.closeScreens();
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
        return loaded;
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

    /**
     * Opens a specific screen for the VEYLON_FRONTEND QA captures. A
     * {@code -creative} suffix stages the world in Creative; {@code newworld-save}
     * shows the replace notice without writing a save file.
     */
    void openForQa(String screen) {
        GameMode qaMode = screen.endsWith("-creative") ? GameMode.CREATIVE : GameMode.SURVIVAL;
        switch (screen) {
            case "audio" -> {
                game.audioOptionsScreen.open(game.audio.settings);
                game.appState = Game.AppState.TITLE_AUDIO_OPTIONS;
            }
            case "options" -> {
                game.graphicsOptionsScreen.open(game.renderer.settings,
                        game.window.windowedWidth(), game.window.windowedHeight());
                game.appState = Game.AppState.TITLE_OPTIONS;
            }
            case "loading" -> {
                game.appState = Game.AppState.LOADING;
                game.qa.setStaticLoadingQa(true);
            }
            case "newworld" -> openNewFrontier(Files.exists(savePath));
            case "newworld-save" -> openNewFrontier(true);
            case "pause", "pause-creative", "gamemode", "gamemode-creative" -> {
                game.newWorld(game.sessionSeed, true, qaMode);
                game.appState = Game.AppState.PLAYING;
                game.uiMode = Game.UiMode.PAUSE;
                if (screen.startsWith("gamemode")) {
                    game.gameModes.openScreen();
                }
            }
            case "worldcontrols", "worldcontrols-held" -> {
                game.newWorld(game.sessionSeed, true, GameMode.CREATIVE);
                game.appState = Game.AppState.PLAYING;
                game.creativeQa.openWorldControlsForQa(screen);
            }
            case "catalog", "catalog-tools", "catalog-search", "catalog-inventory" -> {
                game.newWorld(game.sessionSeed, true, GameMode.CREATIVE);
                game.appState = Game.AppState.PLAYING;
                game.creativeQa.openCatalogForQa(screen);
            }
            case "death", "victory", "victory-creative" -> {
                game.newWorld(game.sessionSeed, true, qaMode);
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
