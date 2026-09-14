package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.entity.Player;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R1/R15: optional stable-ID state must not compromise failure-atomic loads. */
class GameModeSectionTest {
    @TempDir Path directory;

    @Test
    void everyModeMarkAndFlightCombinationRoundTripsThroughNormalizedAbilities() throws Exception {
        for (GameMode mode : GameMode.values()) {
            for (boolean marked : new boolean[]{false, true}) {
                for (boolean flying : new boolean[]{false, true}) {
                    Game original = minimalGame();
                    original.restoreGameMode(mode, marked, flying);
                    Game loaded = minimalGame();
                    loaded.player.health = 63f;
                    GameModeSection.read(GameModeSection.write(original), loaded);
                    assertEquals(mode, loaded.gameMode(), "R1: stable mode ID must round-trip");
                    assertEquals(marked || mode == GameMode.CREATIVE, loaded.creativeMarked(),
                            "R1: a Creative world must always carry its permanent mark");
                    assertEquals(flying && mode == GameMode.CREATIVE, loaded.player.abilities.flying(),
                            "R15: persisted flight applies only in Creative");
                    assertEquals(63f, loaded.player.health, "R1: restore does not run switch healing");
                    assertTrue(loaded.eventLog.recent(30).isEmpty(), "R1: restore does not add switch logs");
                }
            }
        }
    }

    @Test
    void absentSectionLoadsAnOldV3WorldAsUnmarkedSurvival() throws Exception {
        Game original = new Game();
        original.newWorld(20260910, true);
        Path save = directory.resolve("without-mode.sav");
        assertTrue(SaveSystem.save(original, save));
        Files.write(save, CreativeSaveSections.replace(Files.readAllBytes(save), GameModeSection.ID, null));
        original.switchGameMode(GameMode.CREATIVE);
        original.player.abilities.setFlying(true);
        assertTrue(SaveSystem.load(original, save), "R1: optional-section absence must remain loadable");
        assertEquals(GameMode.SURVIVAL, original.gameMode());
        assertFalse(original.creativeMarked(), "R1: old saves cannot inherit the live world's mark");
        assertFalse(original.player.abilities.flying());
    }

    @Test
    void actualCreativeSaveRestoresModeMarkAndFlightOverALiveWorld() {
        Game original = new Game();
        original.newWorld(20260910, true, GameMode.CREATIVE);
        original.player.abilities.setFlying(true);
        Path save = directory.resolve("creative.sav");
        assertTrue(SaveSystem.save(original, save));
        original.newWorld(4422, true);
        assertTrue(SaveSystem.load(original, save), "R15: real Creative save must load successfully");
        assertEquals(GameMode.CREATIVE, original.gameMode());
        assertTrue(original.creativeMarked() && original.player.abilities.flying());
    }

    @Test
    void authenticV020FixtureLoadsAsSurvivalWithLegacyTerrain() throws Exception {
        Path save = directory.resolve("historical.sav");
        try (var input = getClass().getResourceAsStream("/com/veylon/save/v0.2.0-authentic.sav.b64")) {
            assertNotNull(input, "R1: authentic historical fixture must remain available");
            Files.write(save, Base64.getDecoder().decode(
                    new String(input.readAllBytes(), StandardCharsets.US_ASCII).trim()));
        }
        Game game = new Game();
        game.newWorld(4422, true, GameMode.CREATIVE);
        assertTrue(SaveSystem.load(game, save), "R1: authentic v2 saves must still load");
        assertEquals(World.GEN_LEGACY, game.world.generatorVersion);
        assertEquals(GameMode.SURVIVAL, game.gameMode());
        assertFalse(game.creativeMarked() || game.player.abilities.flying());
    }

    @Test
    void malformedSectionsFailBeforeChangingTheLiveWorld() throws Exception {
        Game game = new Game();
        game.newWorld(20260910, true, GameMode.CREATIVE);
        Path save = directory.resolve("malformed-mode.sav");
        assertTrue(SaveSystem.save(game, save));
        byte[] validSave = Files.readAllBytes(save);
        byte[] valid = payload(1, "creative");
        byte[] trailing = Arrays.copyOf(valid, valid.length + 1);
        List<byte[]> invalid = List.of(payload(2, "creative"), payload(1, "unknown"),
                Arrays.copyOf(valid, valid.length - 1), new byte[0], trailing);
        var world = game.world;
        var player = game.player;
        player.health = 73f;
        for (byte[] corruption : invalid) {
            assertThrows(IOException.class, () -> GameModeSection.read(corruption, minimalGame()),
                    "R1: malformed known sections must fail explicitly");
            Files.write(save, CreativeSaveSections.replace(validSave, GameModeSection.ID, corruption));
            assertFalse(SaveSystem.load(game, save), "R1: corrupt mode state must reject the entire load");
            assertSame(world, game.world, "R1: failed verification cannot release the live world");
            assertSame(player, game.player, "R1: failed verification cannot replace the live player");
            assertEquals(73f, game.player.health);
            assertEquals(GameMode.CREATIVE, game.gameMode());
            assertTrue(game.creativeMarked());
        }
    }

    private static Game minimalGame() {
        Game game = new Game();
        game.world = new World(1);
        game.player = new Player(game.world);
        return game;
    }

    private static byte[] payload(int version, String id) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
            out.writeUTF(id);
            out.writeBoolean(true);
            out.writeBoolean(true);
        }
        return bytes.toByteArray();
    }
}
