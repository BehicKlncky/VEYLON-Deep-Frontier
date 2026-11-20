package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.save.SaveSlots;
import com.veylon.save.SaveSystem;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The {@code save_audit} tour, played headlessly in {@link Game#run}'s frame order from a save placed in
 * the active slot of a folder of slots of the test's own: the integrated city, and a wilderness world
 * in each mode. Each is loaded as F9 loads one, walked, its screens opened with their keys, then F5
 * and F9 restore what F5 wrote. Captures are the native run's to judge.
 */
class SaveAuditQaSceneTest {
    private static final float DT = 1f / 60f;

    @TempDir
    Path directory;

    @Test
    void theIntegratedCityIsLoadedWalkedOpenedSavedAndLoadedAgain() throws Exception {
        IntegratedCityFixture.Built built = IntegratedCityFixture.build();
        SaveAuditQaScene tour = play(bytesOf(built.game()), directory.resolve("city"));
        assertEquals(IntegratedCityFixture.GENERATOR, tour.durable().isEmpty() ? -1 : generatorOf(tour));
    }

    @Test
    void wildernessWorldsInBothModesAreLoadedWalkedOpenedSavedAndLoadedAgain() throws Exception {
        for (GameMode mode : GameMode.values()) {
            Game world = new Game();
            world.newWorld(81_081_928L, true, World.GEN_CAVE_IDENTITIES, mode);
            SaveAuditQaScene tour = play(bytesOf(world), directory.resolve(mode.id));
            assertEquals(World.GEN_CAVE_IDENTITIES, generatorOf(tour), mode.name());
        }
    }

    @Test
    void theTourRefusesAnEmptySlot() {
        Game game = new Game();
        game.saves.useSlots(new SaveSlots(directory.resolve("empty")));
        game.newWorld(7, true, World.GEN_CAVE_IDENTITIES);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(game).stage("save_audit"));
    }

    private static SaveAuditQaScene play(byte[] save, Path folder) throws Exception {
        SaveSlots slots = new SaveSlots(folder);
        Files.createDirectories(slots.directory());
        Files.write(slots.current(1), save);
        Game game = new Game();
        game.saves.useSlots(slots);
        game.newWorld(4_242L, true, World.GEN_CAVE_IDENTITIES);
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
        assertTrue(SaveSystem.load(reloaded, slots.current(1)), "the slot holds what F5 wrote");
        assertNotNull(slots.newestIntactBackup(1), "and the save F5 replaced is its newest backup");
        return tour;
    }

    private static int generatorOf(SaveAuditQaScene tour) {
        String world = tour.durable().getFirst();
        return Integer.parseInt(world.substring(world.indexOf("generator=") + 10, world.indexOf(" mode=")));
    }

    /** A save of {@code game} written by this build, as its bytes. */
    private static byte[] bytesOf(Game game) throws Exception {
        Path file = Files.createTempFile("audit-", ".sav");
        try {
            assertTrue(SaveSystem.save(game, file));
            return Files.readAllBytes(file);
        } finally {
            Files.delete(file);
        }
    }
}
