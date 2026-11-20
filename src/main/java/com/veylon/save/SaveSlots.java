package com.veylon.save;

import com.veylon.Game;
import com.veylon.util.AppPaths;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The three save slots (0.9.1 owner decision O3, decisions D-02 and D-21). Each slot is a current
 * save and up to two backups, in one folder:
 *
 * <pre>
 * slot-1.sav             the slot's current save
 * slot-1.backup-1.sav    the save it replaced, its newest backup
 * slot-1.backup-2.sav    the one before that
 * slot-1.damaged.sav     a damaged save set aside when a backup was restored over it
 * veylon.sav             VEYLON 0.9.0's single save, which no slot reads, moves or deletes
 * </pre>
 *
 * <p>A save is written in full to a temporary file beside them first. Only then is the current
 * save copied to a second temporary file and the new one put in its place; only once that
 * replacement has happened do the backups move along, so a save that fails keeps the slot and both
 * backups exactly as they were. The game lists, moves and deletes only the names above (D-15): an
 * antivirus's copy, a player's note or any other file in the folder is left alone.
 */
public final class SaveSlots {

    /** Slots 1 to {@value}. */
    public static final int COUNT = 3;
    /** Backups kept per slot, the newest first. */
    public static final int BACKUPS = 2;
    /** The most damaged saves one slot sets aside before a restore refuses to set aside another. */
    static final int MAX_SET_ASIDE = 9;
    private static final String LEGACY_NAME = "veylon.sav";

    private final Path directory;

    /** The slots in {@code directory}. */
    public SaveSlots(Path directory) {
        this.directory = Objects.requireNonNull(directory, "directory").toAbsolutePath().normalize();
    }

    /** The player's slots: the {@code saves} folder of the data directory. */
    public static SaveSlots inDataDirectory() {
        return new SaveSlots(AppPaths.dataDirectory().resolve("saves"));
    }

    /** What a slot holds, as the picker shows it. */
    public enum State {
        /** No save and no backup. */
        EMPTY,
        /** A save this build opens. */
        READY,
        /** A save of another format or epoch, such as one VEYLON 0.9.0 wrote. */
        OTHER_VERSION,
        /** A save this build cannot read, or none while a backup remains. */
        DAMAGED
    }

    /**
     * A backup the picker may offer, read like any save.
     *
     * @param generation 1 for the newest backup
     * @param inspection what reading it found; always intact
     */
    public record Backup(int generation, SaveSummary.Inspection inspection) {
        public SaveSummary summary() {
            return inspection.summary();
        }
    }

    /**
     * One slot as the picker shows it, read without building a world.
     *
     * @param number        1 to {@value #COUNT}
     * @param state         what it holds
     * @param current       what reading the current save found
     * @param currentExists whether there is a current save file at all
     * @param backup        for a damaged slot, its newest backup that reads intact; else null
     */
    public record Slot(int number, State state, SaveSummary.Inspection current, boolean currentExists,
                       Backup backup) {
        /** The current save's summary; null unless the slot is ready. */
        public SaveSummary summary() {
            return current.summary();
        }
    }

    public Path directory() {
        return directory;
    }

    /** The slot's current save. */
    public Path current(int slot) {
        return directory.resolve("slot-" + checked(slot) + ".sav");
    }

    /** One of the slot's backups, 1 the newest. */
    public Path backup(int slot, int generation) {
        if (generation < 1 || generation > BACKUPS) {
            throw new IllegalArgumentException("backup " + generation + " of " + BACKUPS);
        }
        return directory.resolve("slot-" + checked(slot) + ".backup-" + generation + ".sav");
    }

    /** Where a restore set the slot's {@code n}th damaged save aside, 1 the first. */
    public Path setAside(int slot, int n) {
        if (n < 1 || n > MAX_SET_ASIDE) {
            throw new IllegalArgumentException("damaged copy " + n + " of " + MAX_SET_ASIDE);
        }
        return directory.resolve("slot-" + checked(slot) + (n == 1 ? ".damaged.sav" : ".damaged-" + n + ".sav"));
    }

    /** Every file a save of the slot writes: the current save and its backups. */
    public List<Path> files(int slot) {
        List<Path> files = new ArrayList<>(1 + BACKUPS);
        files.add(current(slot));
        for (int generation = 1; generation <= BACKUPS; generation++) {
            files.add(backup(slot, generation));
        }
        return files;
    }

    /** VEYLON 0.9.0's single save file, which no slot reads, imports, moves or deletes. */
    public Path legacySave() {
        return directory.resolve(LEGACY_NAME);
    }

    /** Whether VEYLON 0.9.0 or earlier left its save in the folder. */
    public boolean holdsLegacySave() {
        return Files.isRegularFile(legacySave());
    }

    /** Saves the live session into {@code slot}, keeping the save it replaces as the newest backup. */
    public boolean save(Game g, int slot) {
        return save(g, slot, null);
    }

    /** {@link #save(Game, int)} with a fixed summary, for tests that compare bytes. */
    boolean save(Game g, int slot, SaveSummary summary) {
        Path target = current(slot);
        return SaveSystem.save(g, target, SaveSystem.MAX_SAVE_BYTES, summary,
                (complete, replaced) -> publish(slot, complete, replaced));
    }

    /** Opens the slot's current save into {@code g}, as {@link SaveSystem#open} opens any. */
    public LoadResult open(Game g, int slot) {
        return SaveSystem.open(g, current(slot));
    }

    /** What the slot holds, from its files' headers, summaries and checksums. */
    public Slot describe(int slot) {
        SaveSummary.Inspection current = SaveSummary.inspect(current(slot));
        if (current.intact()) {
            return new Slot(slot, State.READY, current, true, null);
        }
        LoadResult.Outcome problem = current.problem().outcome();
        if (problem == LoadResult.Outcome.NO_SAVE) {
            boolean anyBackup = false;
            for (int generation = 1; generation <= BACKUPS; generation++) {
                anyBackup |= Files.exists(backup(slot, generation));
            }
            return anyBackup ? new Slot(slot, State.DAMAGED, current, false, newestIntactBackup(slot))
                    : new Slot(slot, State.EMPTY, current, false, null);
        }
        if (current.problem().otherVersion()) {
            return new Slot(slot, State.OTHER_VERSION, current, true, null);
        }
        return new Slot(slot, State.DAMAGED, current, true, newestIntactBackup(slot));
    }

    /** Every slot, in order. */
    public List<Slot> describeAll() {
        List<Slot> slots = new ArrayList<>(COUNT);
        for (int slot = 1; slot <= COUNT; slot++) {
            slots.add(describe(slot));
        }
        return slots;
    }

    /** The newest of the slot's backups that reads intact, or null. */
    public Backup newestIntactBackup(int slot) {
        for (int generation = 1; generation <= BACKUPS; generation++) {
            SaveSummary.Inspection inspection = SaveSummary.inspect(backup(slot, generation));
            if (inspection.intact()) {
                return new Backup(generation, inspection);
            }
        }
        return null;
    }

    /**
     * Loads one of the slot's backups into {@code g} and, once it has loaded, makes a copy of it the
     * slot's current save. The save it replaces is set aside under {@link #setAside}, never deleted;
     * the backups stay as they are. A backup that does not load changes no file.
     */
    public LoadResult restore(Game g, int slot, int generation) {
        Path backup = backup(slot, generation);
        LoadResult loaded = SaveSystem.open(g, backup);
        if (!loaded.loaded()) {
            return loaded;
        }
        Path current = current(slot);
        Path copy = null;
        try {
            if (Files.exists(current)) {
                Path aside = freeSetAside(slot);
                if (aside == null) {
                    System.err.println("Restored slot " + slot + " from backup " + generation
                            + ", but its damaged save was not replaced: " + MAX_SET_ASIDE + " are set aside already");
                    return loaded;
                }
                Files.move(current, aside);
            }
            copy = Files.createTempFile(directory, "veylon-restore-", ".tmp");
            copyDurably(backup, copy);
            SaveSystem.replaceAtomically(copy, current);
            copy = null;
        } catch (IOException | RuntimeException failed) {
            System.err.println("Restored slot " + slot + " from backup " + generation
                    + ", but could not put it back as the slot's save: " + failed);
        } finally {
            deleteQuietly(copy);
        }
        return loaded;
    }

    /**
     * Deletes the slot's current save and its backups, and nothing else: a damaged save set aside,
     * VEYLON 0.9.0's file and every file the game did not write stay.
     *
     * @return true when none of them is left
     */
    public boolean delete(int slot) {
        boolean deleted = true;
        for (Path file : files(slot)) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException | RuntimeException failed) {
                System.err.println("Could not delete " + file + ": " + failed);
                deleted = false;
            }
        }
        return deleted;
    }

    /**
     * Puts a complete save in place of the slot's current one: the current save is copied aside
     * first, so a replacement that fails leaves every file as it was, and the backups move along only
     * once the replacement has happened.
     */
    private void publish(int slot, Path complete, Path target) throws IOException {
        Path kept = null;
        if (Files.isRegularFile(target)) {
            kept = Files.createTempFile(directory, "veylon-backup-", ".tmp");
            try {
                copyDurably(target, kept);
            } catch (IOException | RuntimeException failed) {
                deleteQuietly(kept);
                throw failed;
            }
        }
        try {
            SaveSystem.replaceAtomically(complete, target);
        } catch (IOException | RuntimeException failed) {
            deleteQuietly(kept);
            throw failed;
        }
        if (kept != null) {
            rotate(slot, kept);
        }
    }

    /** The save just replaced becomes backup 1, backup 1 becomes backup 2, and the oldest goes. */
    private void rotate(int slot, Path kept) {
        try {
            for (int generation = BACKUPS; generation > 1; generation--) {
                Path newer = backup(slot, generation - 1);
                if (Files.isRegularFile(newer)) {
                    SaveSystem.replaceAtomically(newer, backup(slot, generation));
                }
            }
            SaveSystem.replaceAtomically(kept, backup(slot, 1));
        } catch (IOException | RuntimeException failed) {
            // The new save is in place already; only its backups are behind.
            System.err.println("Saved slot " + slot + ", but could not keep the save it replaced as a backup: " + failed);
            deleteQuietly(kept);
        }
    }

    private Path freeSetAside(int slot) {
        for (int n = 1; n <= MAX_SET_ASIDE; n++) {
            if (!Files.exists(setAside(slot, n))) {
                return setAside(slot, n);
            }
        }
        return null;
    }

    /** Copies {@code source} over {@code target}'s contents and forces it to the disk. */
    private static void copyDurably(Path source, Path target) throws IOException {
        try (FileChannel in = FileChannel.open(source, StandardOpenOption.READ);
             FileChannel out = FileChannel.open(target, StandardOpenOption.WRITE,
                     StandardOpenOption.TRUNCATE_EXISTING)) {
            long size = in.size();
            for (long position = 0; position < size; ) {
                long moved = in.transferTo(position, size - position, out);
                if (moved <= 0) {
                    throw new IOException(source + " ended at " + position + " of " + size + " bytes while copied");
                }
                position += moved;
            }
            out.force(true);
        }
    }

    private static void deleteQuietly(Path file) {
        if (file == null) {
            return;
        }
        try {
            Files.deleteIfExists(file);
        } catch (IOException | RuntimeException failed) {
            System.err.println("Could not remove " + file + ": " + failed);
        }
    }

    private static int checked(int slot) {
        if (slot < 1 || slot > COUNT) {
            throw new IllegalArgumentException("slot " + slot + " of " + COUNT);
        }
        return slot;
    }
}
