package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R25: the world controls persist without weakening the failure-atomic load. */
class CreativeControlsSectionTest {
    @TempDir Path directory;

    @Test
    void everyCombinationOfControlsRoundTrips() throws Exception {
        for (int mask = 0; mask < 8; mask++) {
            boolean frozen = (mask & 1) != 0;
            boolean locked = (mask & 2) != 0;
            boolean spawnsPaused = (mask & 4) != 0;
            Game original = creativeGame();
            original.restoreCreativeControls(frozen, locked, spawnsPaused);
            Game loaded = creativeGame();
            CreativeControlsSection.read(CreativeControlsSection.write(original), loaded);
            assertEquals(frozen, loaded.daylightFrozen(), "R25: the freeze flag round-trips");
            assertEquals(locked, loaded.weatherLocked(), "R25: the weather lock round-trips");
            assertEquals(spawnsPaused, loaded.spawningPaused(), "R25: the spawn flag round-trips");
        }
    }

    @Test
    void anAbsentSectionMeansEveryControlIsOff() throws Exception {
        Game original = creativeGame();
        original.restoreCreativeControls(true, true, true);
        Path save = directory.resolve("without-controls.sav");
        assertTrue(SaveSystem.save(original, save));
        Files.write(save, CreativeSaveSections.replace(Files.readAllBytes(save),
                CreativeControlsSection.ID, null));
        assertTrue(SaveSystem.load(original, save), "R25: the section is optional");
        assertFalse(original.daylightFrozen() || original.weatherLocked() || original.spawningPaused(),
                "R25: a save written before 0.6.11 holds no control");
    }

    @Test
    void malformedSectionsFailBeforeChangingTheLiveWorld() throws Exception {
        Game game = creativeGame();
        game.restoreCreativeControls(true, false, true);
        Path save = directory.resolve("malformed-controls.sav");
        assertTrue(SaveSystem.save(game, save));
        byte[] validSave = Files.readAllBytes(save);
        byte[] valid = payload(1);
        List<byte[]> invalid = List.of(payload(2), Arrays.copyOf(valid, valid.length - 1),
                new byte[0], Arrays.copyOf(valid, valid.length + 1));
        var world = game.world;
        var player = game.player;
        player.health = 73f;
        for (byte[] corruption : invalid) {
            assertThrows(IOException.class, () -> CreativeControlsSection.read(corruption, creativeGame()),
                    "R25: malformed control state must fail explicitly");
            Files.write(save, CreativeSaveSections.replace(validSave,
                    CreativeControlsSection.ID, corruption));
            assertFalse(SaveSystem.load(game, save), "R25: it must reject the entire load");
            assertSame(world, game.world, "R25: failed verification cannot release the live world");
            assertSame(player, game.player, "R25: failed verification cannot replace the live player");
            assertEquals(73f, game.player.health);
            assertTrue(game.daylightFrozen() && game.spawningPaused(),
                    "R25: the live world keeps its own controls");
        }
    }

    @Test
    void aSurvivalSaveCannotSmuggleInAFrozenClock() throws Exception {
        Game game = creativeGame();
        game.restoreCreativeControls(true, true, true);
        Path save = directory.resolve("survival-with-controls.sav");
        assertTrue(SaveSystem.save(game, save));
        assertTrue(game.switchGameMode(GameMode.SURVIVAL));
        // Write the Creative controls back into a Survival world's save by hand.
        Path survivalSave = directory.resolve("survival.sav");
        assertTrue(SaveSystem.save(game, survivalSave));
        Files.write(survivalSave, CreativeSaveSections.replace(Files.readAllBytes(survivalSave),
                CreativeControlsSection.ID, payload(1)));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, survivalSave), "R25: the forged save still loads");
        assertEquals(GameMode.SURVIVAL, loaded.gameMode());
        assertFalse(loaded.daylightFrozen() || loaded.weatherLocked() || loaded.spawningPaused(),
                "R25: a Survival world releases controls whatever the save claims");
    }

    private static Game creativeGame() {
        Game game = new Game();
        game.newWorld(20260910, true, GameMode.CREATIVE);
        return game;
    }

    private static byte[] payload(int version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
            out.writeBoolean(true);
            out.writeBoolean(true);
            out.writeBoolean(true);
        }
        return bytes.toByteArray();
    }
}
