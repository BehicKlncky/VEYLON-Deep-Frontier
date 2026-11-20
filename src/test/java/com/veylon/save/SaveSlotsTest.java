package com.veylon.save;

import com.veylon.Game;
import com.veylon.combat.ExplosionSystem;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.9.1 milestone 04 (O3, D-02, D-21): three slots, each a current save and two rotating backups.
 * A save keeps the one it replaces as the newest backup; a failed save changes nothing; a damaged
 * slot is restored from its newest intact backup with the damaged save set aside; deleting a slot
 * removes its own files only. VEYLON 0.9.0's file and every file the game did not write are left
 * alone (D-15).
 */
class SaveSlotsTest {

    @TempDir
    Path folder;

    private SaveSlots slots;
    private Game game;

    @BeforeEach
    void aWorldAndAnEmptyFolder() {
        slots = new SaveSlots(folder);
        game = new Game();
        game.newWorld(20_261_140L, true, World.GEN_CAVE_IDENTITIES);
    }

    @Test
    void everySlotFileIsNamedForItsSlotInsideTheFolder() {
        for (int slot = 1; slot <= SaveSlots.COUNT; slot++) {
            assertEquals(folder.resolve("slot-" + slot + ".sav"), slots.current(slot));
            assertEquals(folder.resolve("slot-" + slot + ".backup-1.sav"), slots.backup(slot, 1));
            assertEquals(folder.resolve("slot-" + slot + ".backup-2.sav"), slots.backup(slot, 2));
            assertEquals(folder.resolve("slot-" + slot + ".damaged.sav"), slots.setAside(slot, 1));
            assertEquals(List.of(slots.current(slot), slots.backup(slot, 1), slots.backup(slot, 2)), slots.files(slot));
        }
        assertEquals(folder.resolve("veylon.sav"), slots.legacySave(), "0.9.0's file keeps its own name");
        assertThrows(IllegalArgumentException.class, () -> slots.current(0));
        assertThrows(IllegalArgumentException.class, () -> slots.current(SaveSlots.COUNT + 1));
        assertThrows(IllegalArgumentException.class, () -> slots.backup(1, SaveSlots.BACKUPS + 1));
    }

    @Test
    void eachSaveKeepsTheOneItReplacesAsTheNewestBackup() throws IOException {
        byte[] first = saveAt(1, 1_000);
        assertNull(slots.newestIntactBackup(1), "the first save replaces nothing");
        byte[] second = saveAt(1, 2_000);
        byte[] third = saveAt(1, 3_000);
        assertArrayEquals(third, read(slots.current(1)));
        assertArrayEquals(second, read(slots.backup(1, 1)), "the save it replaced is the newest backup");
        assertArrayEquals(first, read(slots.backup(1, 2)), "and the one before it the oldest");

        byte[] fourth = saveAt(1, 4_000);
        assertArrayEquals(fourth, read(slots.current(1)));
        assertArrayEquals(third, read(slots.backup(1, 1)));
        assertArrayEquals(second, read(slots.backup(1, 2)), "the oldest backup made way");
        assertEquals(Set.of("slot-1.sav", "slot-1.backup-1.sav", "slot-1.backup-2.sav"), names(),
                "and no temporary file is left");
        assertEquals(3_000, slots.newestIntactBackup(1).summary().clockMinutes());
    }

    @Test
    void aFailedSaveKeepsTheSlotAndBothBackupsAsTheyWere() throws IOException {
        saveAt(1, 1_000);
        saveAt(1, 2_000);
        saveAt(1, 3_000);
        byte[] current = read(slots.current(1)), newest = read(slots.backup(1, 1)), oldest = read(slots.backup(1, 2));

        // The writer finds this invalid state at the end of the payload, after everything else.
        for (int i = 0; i <= ExplosionSystem.MAX_ACTIVE_FUSES; i++) {
            game.world.kegFuses.put(new Vec3i(i, 1, 0), 1f);
        }
        assertFalse(slots.save(game, 1), "an invalid snapshot is not saved");
        assertArrayEquals(current, read(slots.current(1)), "the current save stays");
        assertArrayEquals(newest, read(slots.backup(1, 1)), "the newest backup stays");
        assertArrayEquals(oldest, read(slots.backup(1, 2)), "a failed save never costs a backup");
        assertEquals(Set.of("slot-1.sav", "slot-1.backup-1.sav", "slot-1.backup-2.sav"), names());
    }

    @Test
    void aReplacementThatCannotHappenKeepsTheBackups() throws IOException {
        saveAt(1, 1_000);
        saveAt(1, 2_000);
        byte[] newest = read(slots.backup(1, 1));
        Files.delete(slots.current(1));
        Files.createDirectory(slots.current(1));
        Files.write(slots.current(1).resolve("keep.txt"), new byte[]{7});

        assertFalse(slots.save(game, 1), "a folder where the save belongs cannot be replaced");
        assertArrayEquals(newest, read(slots.backup(1, 1)));
        assertArrayEquals(new byte[]{7}, read(slots.current(1).resolve("keep.txt")));
        assertEquals(Set.of("slot-1.sav", "slot-1.backup-1.sav"), names(), "no temporary file is left");
    }

    @Test
    void aSlotsSavesLeaveTheOtherSlotsAndVeylon090sFileUntouched() throws IOException {
        saveAt(2, 500);
        saveAt(3, 700);
        Path legacy = Files.write(slots.legacySave(), new byte[]{'V', 'E', 'Y', 'L', 0, 0, 0, 3});
        FileTime legacyTime = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(legacy, legacyTime);
        byte[] two = read(slots.current(2)), three = read(slots.current(3));

        saveAt(1, 1_000);
        saveAt(1, 2_000);
        saveAt(1, 3_000);
        damage(slots.current(1));
        assertTrue(slots.restore(new Game(), 1, 1).loaded());
        assertTrue(slots.delete(1));

        assertArrayEquals(two, read(slots.current(2)));
        assertArrayEquals(three, read(slots.current(3)));
        assertArrayEquals(new byte[]{'V', 'E', 'Y', 'L', 0, 0, 0, 3}, read(legacy), "0.9.0's file is never touched");
        assertEquals(legacyTime, Files.getLastModifiedTime(legacy));
        assertTrue(slots.holdsLegacySave());
    }

    @Test
    void theListingNamesWhatEverySlotHoldsWithoutLoadingIt() throws IOException {
        assertEquals(SaveSlots.State.EMPTY, slots.describe(1).state(), "nothing in it");

        saveAt(1, 1_000);
        SaveSlots.Slot ready = slots.describe(1);
        assertEquals(SaveSlots.State.READY, ready.state());
        assertEquals(1_000, ready.summary().clockMinutes());
        assertEquals(GameMode.SURVIVAL, ready.summary().mode());

        Files.write(slots.current(2), ForeignSaves.asFormatThree(read(slots.current(1))));
        SaveSlots.Slot older = slots.describe(2);
        assertEquals(SaveSlots.State.OTHER_VERSION, older.state(), "0.9.0's header");
        assertEquals(LoadResult.Outcome.OLDER_VERSION, older.current().problem().outcome());
        assertNull(older.summary());

        saveAt(3, 2_000);
        saveAt(3, 3_000);
        damage(slots.current(3));
        SaveSlots.Slot damaged = slots.describe(3);
        assertEquals(SaveSlots.State.DAMAGED, damaged.state());
        assertTrue(damaged.currentExists());
        assertNotNull(damaged.backup(), "with a backup to restore");
        assertEquals(1, damaged.backup().generation());
        assertEquals(2_000, damaged.backup().summary().clockMinutes(), "the backup's own summary");

        damage(slots.backup(3, 1));
        assertNull(slots.describe(3).backup(), "a damaged backup is not offered");

        Files.delete(slots.current(3));
        SaveSlots.Slot missing = slots.describe(3);
        assertEquals(SaveSlots.State.DAMAGED, missing.state(), "a slot whose save is gone but whose backups remain");
        assertFalse(missing.currentExists());
        assertEquals(List.of(SaveSlots.State.READY, SaveSlots.State.OTHER_VERSION, SaveSlots.State.DAMAGED),
                slots.describeAll().stream().map(SaveSlots.Slot::state).toList());
    }

    @Test
    void aDamagedSlotIsRestoredFromItsNewestIntactBackupAndTheDamagedSaveIsSetAside() throws IOException {
        saveAt(1, 1_000);
        saveAt(1, 2_000);
        saveAt(1, 3_000);
        damage(slots.current(1));
        damage(slots.backup(1, 1));
        byte[] damagedCurrent = read(slots.current(1)), damagedNewest = read(slots.backup(1, 1));
        byte[] oldest = read(slots.backup(1, 2));

        SaveSlots.Backup offered = slots.describe(1).backup();
        assertEquals(2, offered.generation(), "the newest backup that reads intact");
        Game restored = new Game();
        assertTrue(slots.restore(restored, 1, offered.generation()).loaded());
        assertEquals(1_000, restored.time.totalMinutes, "the backup's world is the live one");
        assertArrayEquals(oldest, read(slots.current(1)), "the backup is the slot's save again");
        assertArrayEquals(damagedCurrent, read(slots.setAside(1, 1)), "the damaged save is kept, not deleted");
        assertArrayEquals(damagedNewest, read(slots.backup(1, 1)), "the backups stay as they were");
        assertArrayEquals(oldest, read(slots.backup(1, 2)));
        assertEquals(SaveSlots.State.READY, slots.describe(1).state());

        damage(slots.current(1));
        assertTrue(slots.restore(new Game(), 1, 2).loaded());
        assertTrue(Files.exists(slots.setAside(1, 2)), "a second damaged save is set aside beside the first");
    }

    @Test
    void aBackupThatDoesNotLoadChangesNoFile() throws IOException {
        saveAt(1, 1_000);
        // Intact to the picker, refused by a load: its summary names another mode than its body.
        assertTrue(SaveSystem.save(game, slots.backup(1, 1), SaveSystem.MAX_SAVE_BYTES, new SaveSummary(0L, "test", 0,
                GameMode.CREATIVE, true, game.time.totalMinutes, "")));
        damage(slots.current(1));
        byte[] current = read(slots.current(1)), backup = read(slots.backup(1, 1));

        Game live = new Game();
        live.newWorld(20_261_141L, true, World.GEN_CAVE_IDENTITIES);
        World kept = live.world;
        assertFalse(slots.restore(live, 1, 1).loaded());
        assertSame(kept, live.world, "the live world is kept");
        assertArrayEquals(current, read(slots.current(1)));
        assertArrayEquals(backup, read(slots.backup(1, 1)));
        assertFalse(Files.exists(slots.setAside(1, 1)));
    }

    @Test
    void deletingASlotRemovesItsSaveAndBackupsAndNothingElse() throws IOException {
        saveAt(1, 1_000);
        saveAt(1, 2_000);
        saveAt(1, 3_000);
        saveAt(2, 500);
        Files.write(slots.setAside(1, 1), new byte[]{1});
        Files.write(folder.resolve("notes.txt"), new byte[]{2});
        Files.write(folder.resolve("slot-1.sav.tmp"), new byte[]{3});
        byte[] two = read(slots.current(2));

        assertTrue(slots.delete(1));
        assertEquals(SaveSlots.State.EMPTY, slots.describe(1).state());
        assertEquals(Set.of("slot-1.damaged.sav", "notes.txt", "slot-1.sav.tmp", "slot-2.sav"), names(),
                "a damaged save set aside and files the game did not write stay");
        assertArrayEquals(two, read(slots.current(2)));
        assertTrue(slots.delete(1), "deleting an empty slot is no failure");
    }

    @Test
    void filesTheGameDidNotWriteNeverChangeTheListingOrTheRotation() throws IOException {
        // An antivirus copy beside a slot file (D-10, D-15), a slot number past the last, a backup
        // generation past the last and a player's note.
        for (String name : List.of("slot-1.sav.tmp", "slot-4.sav", "slot-1.backup-3.sav", "notes.txt")) {
            Files.write(folder.resolve(name), new byte[]{9, 9, 9});
        }
        assertEquals(List.of(SaveSlots.State.EMPTY, SaveSlots.State.EMPTY, SaveSlots.State.EMPTY),
                slots.describeAll().stream().map(SaveSlots.Slot::state).toList());
        saveAt(1, 1_000);
        saveAt(1, 2_000);
        saveAt(1, 3_000);
        saveAt(1, 4_000);
        for (String name : List.of("slot-1.sav.tmp", "slot-4.sav", "slot-1.backup-3.sav", "notes.txt")) {
            assertArrayEquals(new byte[]{9, 9, 9}, read(folder.resolve(name)), name + " is left alone");
        }
    }

    /** Saves the world with its clock set to {@code minutes} and a fixed summary; returns the bytes. */
    private byte[] saveAt(int slot, double minutes) throws IOException {
        game.time.totalMinutes = minutes;
        assertTrue(slots.save(game, slot, new SaveSummary(0L, "test", 0, game.gameMode(), game.creativeMarked(),
                minutes, "")), "slot " + slot + " saves");
        return read(slots.current(slot));
    }

    /** Changes one byte in the middle of a file, as damage on the disk would. */
    private static void damage(Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        bytes[bytes.length / 2] ^= 0x5A;
        Files.write(file, bytes);
    }

    private static byte[] read(Path file) throws IOException {
        return Files.readAllBytes(file);
    }

    private Set<String> names() throws IOException {
        try (var files = Files.list(folder)) {
            Set<String> names = new TreeSet<>();
            files.forEach(f -> names.add(f.getFileName().toString()));
            return names;
        }
    }
}
