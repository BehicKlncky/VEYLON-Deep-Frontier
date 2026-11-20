package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.save.SaveSlots;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** The {@code urban_autosave} tour, played headlessly in {@link Game#run}'s frame order. */
class AutosaveQaSceneTest {
    private static final float DT = 1f / 60f;
    private static final long SEED = 20_261_120L;

    @TempDir
    Path dir;

    private Game city(GameMode mode) {
        Game game = new Game();
        game.saves.useSlots(new SaveSlots(dir.resolve("saves")));
        game.newWorld(SEED, true, World.DEFAULT_NEW_WORLD_GENERATOR, mode);
        game.appState = Game.AppState.PLAYING;
        return game;
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void everyTriggerAutosavesIntoSlotOneAndAHuntHoldsTheTimersAutosave(GameMode mode) {
        Game game = city(mode);
        game.saves.autosave().permit(true); // VEYLON_AUTOSAVE=on in a native run
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        scenes.stage("urban_autosave");
        AutosaveQaScene tour = scenes.autosave();
        double time = 0;
        while (!tour.reported()) {
            if (time > AutosaveQaScene.DEADLINE_SECONDS + 5) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH && game.appState != Game.AppState.VICTORY) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        int expected = mode == GameMode.SURVIVAL ? 4 : 3;
        assertEquals(expected, tour.written().size(), "claim, wake, timer" + (mode == GameMode.SURVIVAL ? " and the held timer" : "")
                + ": " + tour.written());
        assertEquals(expected, game.saves.autosave().written());
        assertEquals(1, game.saves.activeSlot());
    }

    @Test
    void theTourRefusesToRunWithoutTheAutomationSwitch() {
        Game game = city(GameMode.SURVIVAL);
        IllegalStateException refused = assertThrows(IllegalStateException.class,
                () -> new UrbanQaScenes(game).stage("urban_autosave"));
        assertTrue(refused.getMessage().contains("VEYLON_AUTOSAVE=on"), refused.getMessage());
    }
}
