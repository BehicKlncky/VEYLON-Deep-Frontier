package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.9.1 milestone 04 (D-22): every save describes itself in a summary the slot picker reads from
 * the file's first bytes, and carries a checksum of the whole file. Inspecting a file builds no
 * world; a file changed after it was written is damaged to both the picker and a load.
 */
class SaveSummaryTest {

    @TempDir
    Path directory;

    @Test
    void aCitySaveSaysWhatItIsWithoutBeingLoaded() throws IOException {
        Game game = new Game();
        game.newWorld(20_261_120L, true, World.DEFAULT_NEW_WORLD_GENERATOR);
        game.saves.restorePlayTime(3_725);
        game.time.totalMinutes += 3 * 24 * 60 + 5 * 60 + 20;
        long before = System.currentTimeMillis();
        Path file = directory.resolve("city.sav");
        assertTrue(SaveSystem.save(game, file));
        long after = System.currentTimeMillis();

        SaveSummary.Inspection seen = SaveSummary.inspect(file);
        assertTrue(seen.intact(), () -> "an intact save: " + seen.problem());
        assertEquals(World.DEFAULT_NEW_WORLD_GENERATOR, seen.generator(), "the world kind follows from the header");
        SaveSummary summary = seen.summary();
        assertEquals(GameMode.SURVIVAL, summary.mode());
        assertFalse(summary.creativeMarked());
        assertEquals(3_725, summary.playSeconds(), 1e-9, "the play time");
        assertEquals(4, summary.day(), "day 1 at 08:00 plus three days");
        assertEquals(13, summary.hour());
        assertEquals(20, summary.minute());
        assertEquals(game.saves.placeName(), summary.place(), "the place the survivor stood in");
        assertTrue(summary.place().contains(" · "), "a city names its district and the building or ground: "
                + summary.place());
        assertEquals(SaveStamp.buildVersion(), summary.version(), "the writing build's version");
        assertTrue(summary.savedAt() >= before && summary.savedAt() <= after, "when it was written");
    }

    @Test
    void aCreativeWildernessSaveNamesItsModeMarkAndBiome() throws IOException {
        Game game = new Game();
        game.newWorld(20_261_121L, true, World.WILDERNESS_NEW_WORLD_GENERATOR, GameMode.CREATIVE);
        Path file = directory.resolve("wild.sav");
        assertTrue(SaveSystem.save(game, file));

        SaveSummary.Inspection seen = SaveSummary.inspect(file);
        assertTrue(seen.intact(), () -> String.valueOf(seen.problem()));
        assertEquals(World.WILDERNESS_NEW_WORLD_GENERATOR, seen.generator());
        assertEquals(GameMode.CREATIVE, seen.summary().mode());
        assertTrue(seen.summary().creativeMarked(), "a Creative world is always marked");
        int x = (int) Math.floor(game.player.pos.x), z = (int) Math.floor(game.player.pos.z);
        assertEquals(game.world.biomeAt(x, z).displayName, seen.summary().place(), "the biome underfoot");
        assertEquals(0, seen.summary().playSeconds(), "a new world is unplayed");
    }

    @Test
    void thePlayTimeTravelsWithTheSaveAndANewWorldStartsAtNone() throws IOException {
        Game game = new Game();
        game.newWorld(20_261_122L, true, World.GEN_CAVE_IDENTITIES);
        game.saves.restorePlayTime(812.5);
        Path file = directory.resolve("played.sav");
        assertTrue(SaveSystem.save(game, file));

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        assertEquals(812.5, loaded.saves.playSeconds(), 1e-9, "the load restores the summary's play time");
        loaded.newWorld(20_261_123L, true, World.GEN_CAVE_IDENTITIES);
        assertEquals(0, loaded.saves.playSeconds(), "and a new world starts its own count");
    }

    @Test
    void aByteChangedAfterTheWriteIsDamagedToThePickerAndToALoad() throws IOException {
        Game game = new Game();
        game.newWorld(20_261_124L, true, World.GEN_CAVE_IDENTITIES);
        Path file = directory.resolve("whole.sav");
        assertTrue(SaveSystem.save(game, file));
        byte[] whole = Files.readAllBytes(file);
        // The lowest byte of the player's x, which every reader accepts in any value.
        int playerX = playerXOffset(whole) + 3;
        byte[] changed = whole.clone();
        changed[playerX] ^= 0x01;
        Path damaged = Files.write(directory.resolve("changed.sav"), changed);

        SaveSummary.Inspection seen = SaveSummary.inspect(damaged);
        assertFalse(seen.intact(), "the picker sees the change");
        assertEquals(LoadResult.Outcome.DAMAGED, seen.problem().outcome());
        assertNull(seen.summary());

        Game live = new Game();
        live.newWorld(20_261_125L, true, World.GEN_CAVE_IDENTITIES);
        World kept = live.world;
        LoadResult result = SaveSystem.open(live, damaged);
        assertEquals(LoadResult.Outcome.DAMAGED, result.outcome(), "and so does a load");
        assertTrue(result.reason().contains("checksum"), result.reason());
        assertSame(kept, live.world, "the live world is kept");
        assertArrayEquals(changed, Files.readAllBytes(damaged), "the file is left as it was");

        Path resealed = Files.write(directory.resolve("resealed.sav"), SaveSeal.resealed(changed));
        assertTrue(SaveSystem.load(new Game(), resealed), "the change itself reads; only the checksum refused it");
    }

    @Test
    void aSummaryThatDisagreesWithTheSaveIsRefused() throws IOException {
        Game game = new Game();
        game.newWorld(20_261_126L, true, World.GEN_CAVE_IDENTITIES);
        Path creative = directory.resolve("creative-summary.sav");
        assertTrue(SaveSystem.save(game, creative, SaveSystem.MAX_SAVE_BYTES, new SaveSummary(0L, "test", 0,
                GameMode.CREATIVE, true, game.time.totalMinutes, "")));
        LoadResult mode = SaveSystem.open(new Game(), creative);
        assertEquals(LoadResult.Outcome.DAMAGED, mode.outcome());
        assertTrue(mode.reason().contains("game mode"), mode.reason());

        Path later = directory.resolve("later-clock.sav");
        assertTrue(SaveSystem.save(game, later, SaveSystem.MAX_SAVE_BYTES, new SaveSummary(0L, "test", 0,
                GameMode.SURVIVAL, false, game.time.totalMinutes + 1, "")));
        LoadResult clock = SaveSystem.open(new Game(), later);
        assertEquals(LoadResult.Outcome.DAMAGED, clock.outcome());
        assertTrue(clock.reason().contains("clock"), clock.reason());
    }

    @Test
    void aSummaryLongerThanAnyWriterWritesIsDamaged() throws IOException {
        byte[] whole = savedBytes(20_261_127L);
        for (int length : new int[]{SaveSummary.MAX_BYTES + 1, -1, 0}) {
            byte[] edited = whole.clone();
            ByteBuffer.wrap(edited).putInt(SaveChecksum.OFFSET + SaveChecksum.LENGTH, length);
            Path file = Files.write(directory.resolve("length-" + length + ".sav"), SaveSeal.resealed(edited));
            SaveSummary.Inspection seen = SaveSummary.inspect(file);
            assertEquals(LoadResult.Outcome.DAMAGED, seen.problem().outcome(), "summary length " + length);
            assertEquals(LoadResult.Outcome.DAMAGED, SaveSystem.open(new Game(), file).outcome(),
                    "summary length " + length);
        }
    }

    @Test
    void inspectingSaysWhyAFileWouldNotOpen() throws IOException {
        byte[] whole = savedBytes(20_261_128L);
        assertEquals(LoadResult.Outcome.NO_SAVE, SaveSummary.inspect(directory.resolve("absent.sav")).problem().outcome());
        assertEquals(LoadResult.Outcome.OLDER_VERSION,
                inspect("older.sav", ForeignSaves.asFormatThree(whole)).problem().outcome(), "0.9.0's header");
        assertEquals(LoadResult.Outcome.NEWER_VERSION,
                inspect("newer.sav", ForeignSaves.withEpoch(whole, SaveHeader.EPOCH + 1)).problem().outcome());
        assertEquals(LoadResult.Outcome.NOT_A_SAVE,
                inspect("text.sav", "not a save at all".getBytes()).problem().outcome());
        for (int length : new int[]{SaveHeader.LENGTH, SaveHeader.LENGTH + 6, whole.length / 2, whole.length - 1}) {
            assertEquals(LoadResult.Outcome.DAMAGED,
                    inspect("cut-" + length + ".sav", Arrays.copyOf(whole, length)).problem().outcome(),
                    "cut to " + length + " bytes");
        }
        byte[] longer = Arrays.copyOf(whole, whole.length + 1);
        assertEquals(LoadResult.Outcome.DAMAGED, inspect("longer.sav", longer).problem().outcome(),
                "a byte appended after the write");
        assertTrue(inspect("intact.sav", whole).intact());
    }

    @Test
    void aSummaryHoldsOnlyWhatAWriterWrites() {
        assertThrows(IllegalArgumentException.class,
                () -> new SaveSummary(0L, " ", 0, GameMode.SURVIVAL, false, 480, ""), "a blank version");
        assertThrows(IllegalArgumentException.class,
                () -> new SaveSummary(0L, "x".repeat(SaveSummary.MAX_VERSION + 1), 0, GameMode.SURVIVAL, false, 480, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new SaveSummary(-1L, "test", 0, GameMode.SURVIVAL, false, 480, ""), "before 1970");
        assertThrows(IllegalArgumentException.class,
                () -> new SaveSummary(0L, "test", -1, GameMode.SURVIVAL, false, 480, ""), "negative play time");
        assertThrows(IllegalArgumentException.class,
                () -> new SaveSummary(0L, "test", Double.NaN, GameMode.SURVIVAL, false, 480, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new SaveSummary(0L, "test", 0, GameMode.CREATIVE, false, 480, ""), "an unmarked Creative world");
        assertThrows(IllegalArgumentException.class,
                () -> new SaveSummary(0L, "test", 0, GameMode.SURVIVAL, false, Double.POSITIVE_INFINITY, ""));
        assertThrows(IllegalArgumentException.class,
                () -> new SaveSummary(0L, "test", 0, GameMode.SURVIVAL, false, 480, "p".repeat(SaveSummary.MAX_PLACE + 1)));
        assertEquals(SaveSummary.MAX_PLACE, SaveSummary.fit("p".repeat(200), SaveSummary.MAX_PLACE).length(),
                "the writer cuts a long place name instead");
        String pair = "😀";
        assertEquals("a", SaveSummary.fit("a" + pair, 2), "never inside a surrogate pair");
    }

    private SaveSummary.Inspection inspect(String name, byte[] bytes) throws IOException {
        return SaveSummary.inspect(Files.write(directory.resolve(name), bytes));
    }

    private byte[] savedBytes(long seed) throws IOException {
        Game game = new Game();
        game.newWorld(seed, true, World.GEN_CAVE_IDENTITIES);
        Path file = directory.resolve("saved-" + seed + ".sav");
        assertTrue(SaveSystem.save(game, file));
        byte[] bytes = Files.readAllBytes(file);
        Files.delete(file);
        return bytes;
    }

    /** The player's x: after the header, the checksum, the summary, the clock and the weather. */
    private static int playerXOffset(byte[] save) {
        int summaryLength = ByteBuffer.wrap(save).getInt(SaveChecksum.OFFSET + SaveChecksum.LENGTH);
        return SaveChecksum.OFFSET + SaveChecksum.LENGTH + Integer.BYTES + summaryLength + 8 + 4 + 4 + 4 + 4;
    }
}
