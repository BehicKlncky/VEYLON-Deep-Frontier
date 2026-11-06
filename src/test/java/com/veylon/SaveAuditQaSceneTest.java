package com.veylon;

import com.veylon.save.SaveSystem;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code save_audit} tour, played headlessly in {@link Game#run}'s frame order from a save placed in
 * the test JVM's isolated slot: the integrated city and genuine saves of the first and last legacy
 * releases, Survival and Creative. Each is loaded as F9 loads one, walked, its screens opened with
 * their keys, then F5 and F9 restore what F5 wrote. Captures are the native run's to judge.
 */
class SaveAuditQaSceneTest {
    private static final float DT = 1f / 60f;

    @AfterEach
    void emptyTheSlot() throws Exception {
        Files.deleteIfExists(SaveSystem.SAVE_PATH);
    }

    @Test
    void theIntegratedCityIsLoadedWalkedOpenedSavedAndLoadedAgain() throws Exception {
        IntegratedCityFixture.Built built = IntegratedCityFixture.build();
        Path file = Files.createTempFile("integrated-", ".sav");
        assertTrue(SaveSystem.save(built.game(), file));
        byte[] bytes = Files.readAllBytes(file);
        Files.delete(file);
        SaveAuditQaScene tour = play(bytes);
        assertEquals(IntegratedCityFixture.GENERATOR, tour.durable().isEmpty() ? -1 : generatorOf(tour));
    }

    @Test
    void genuineLegacyWorldsAreLoadedWalkedOpenedSavedAndLoadedAgain() throws Exception {
        for (String resource : List.of("v0.8.1-survival-authentic.sav.b64", "v0.8.1-creative-authentic.sav.b64",
                "v0.3.0-authentic.sav.b64")) {
            SaveAuditQaScene tour = play(fixture(resource));
            assertEquals(3, generatorOf(tour), resource);
            emptyTheSlot();
        }
    }

    @Test
    void theTourRefusesAnEmptySlot() throws Exception {
        Files.deleteIfExists(SaveSystem.SAVE_PATH);
        Game game = new Game();
        game.newWorld(7, true);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("save_audit"));
    }

    private static SaveAuditQaScene play(byte[] save) throws Exception {
        Files.createDirectories(SaveSystem.SAVE_PATH.getParent());
        Files.write(SaveSystem.SAVE_PATH, save);
        Game game = new Game();
        game.newWorld(4_242L, true);
        game.appState = Game.AppState.PLAYING;
        UrbanQaScenes scenes = new UrbanQaScenes(game);
        HotkeyRouter router = new HotkeyRouter(game);
        SaveAuditQaScene tour = scenes.saveAudit();
        scenes.stage("save_audit");
        double time = 0;
        while (!tour.reported()) {
            if (time > SaveAuditQaScene.DEADLINE_SECONDS) { fail("the tour never reported"); }
            scenes.update(time);
            if (game.appState != Game.AppState.DEATH) { router.update(); }
            game.playStep(DT, DT);
            game.world.ensureChunks((int) game.player.pos.x, (int) game.player.pos.z, game.renderer.renderRadius(), 2);
            game.input.endFrame();
            time += DT;
        }
        assertTrue(tour.passed());
        Game reloaded = new Game();
        assertTrue(SaveSystem.load(reloaded, SaveSystem.SAVE_PATH), "the slot holds what F5 wrote");
        return tour;
    }

    private static int generatorOf(SaveAuditQaScene tour) {
        String world = tour.durable().getFirst();
        return Integer.parseInt(world.substring(world.indexOf("generator=") + 10, world.indexOf(" mode=")));
    }

    private static byte[] fixture(String resource) throws Exception {
        try (InputStream in = Objects.requireNonNull(SaveAuditQaSceneTest.class.getResourceAsStream(
                "/com/veylon/save/" + resource), resource)) {
            return Base64.getDecoder().decode(new String(in.readAllBytes(), StandardCharsets.US_ASCII).trim());
        }
    }
}
