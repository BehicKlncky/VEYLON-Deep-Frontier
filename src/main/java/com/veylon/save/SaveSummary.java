package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.simulation.TimeConstants;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * What a save says about itself, so the slot picker can show it without building a world (0.9.1
 * milestone 04, decision D-22). It follows the header and the {@link SaveChecksum}:
 *
 * <pre>
 * int    length          the bytes that follow, at most {@value #MAX_BYTES}
 * long   savedAt         wall-clock milliseconds since 1970-01-01T00:00Z
 * UTF    version         the writing build's version, at most {@value #MAX_VERSION} characters
 * double playSeconds     real seconds the world has been played
 * UTF    mode            the {@link GameMode} id
 * bool   creativeMarked
 * double clockMinutes    the in-game clock, as the core body stores it
 * UTF    place           where the survivor stood, at most {@value #MAX_PLACE} characters
 * </pre>
 *
 * <p>The world kind follows from the header's generator. The mode, the mark and the clock repeat
 * what the body holds, and a load refuses a save whose summary disagrees with it; the play time is
 * kept here alone. The place and the version are words for the player and decide nothing.
 *
 * @param savedAt        when the save was written, in milliseconds since the epoch of 1970
 * @param version        the version of the build that wrote it
 * @param playSeconds    real seconds the world had been played when it was saved
 * @param mode           the world's game mode
 * @param creativeMarked whether the world carries the permanent Creative mark
 * @param clockMinutes   the in-game clock in minutes since the world began
 * @param place          where the survivor stood: district and building in a city, biome outside
 */
public record SaveSummary(long savedAt, String version, double playSeconds, GameMode mode,
                          boolean creativeMarked, double clockMinutes, String place) {

    /** The longest summary a reader accepts, length field excluded. */
    public static final int MAX_BYTES = 1024;
    /** The longest version string. */
    public static final int MAX_VERSION = 32;
    /** The longest place name. */
    public static final int MAX_PLACE = 80;
    /** More real seconds than any world is played: about 317 years. */
    static final double MAX_PLAY_SECONDS = 1e10;
    /** Bytes from the start of a file to the end of the longest summary. */
    static final int MAX_PREFIX = SaveChecksum.OFFSET + SaveChecksum.LENGTH + Integer.BYTES + MAX_BYTES;

    public SaveSummary {
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(mode, "mode");
        Objects.requireNonNull(place, "place");
        if (savedAt < 0) {
            throw new IllegalArgumentException("save time before 1970: " + savedAt);
        }
        if (version.isBlank() || version.length() > MAX_VERSION) {
            throw new IllegalArgumentException("invalid version: \"" + version + "\"");
        }
        if (!Double.isFinite(playSeconds) || playSeconds < 0 || playSeconds > MAX_PLAY_SECONDS) {
            throw new IllegalArgumentException("invalid play time: " + playSeconds);
        }
        if (mode == GameMode.CREATIVE && !creativeMarked) {
            throw new IllegalArgumentException("a Creative world is always marked");
        }
        if (!Double.isFinite(clockMinutes) || clockMinutes < 0) {
            throw new IllegalArgumentException("invalid clock: " + clockMinutes);
        }
        if (place.length() > MAX_PLACE) {
            throw new IllegalArgumentException("place name of " + place.length() + " characters");
        }
    }

    /** The summary of the live session {@code g}, as a save written now by this build says it. */
    static SaveSummary of(Game g, SaveStamp stamp) {
        return new SaveSummary(stamp.savedAt(), fit(stamp.version(), MAX_VERSION), g.saves.playSeconds(),
                g.gameMode(), g.creativeMarked(), g.time.totalMinutes, fit(g.saves.placeName(), MAX_PLACE));
    }

    /** The in-game day, from 1, as the HUD's clock counts it. */
    public int day() {
        return (int) (clockMinutes / TimeConstants.MINUTES_PER_DAY) + 1;
    }

    /** The in-game hour of that day. */
    public int hour() {
        return (int) ((clockMinutes % TimeConstants.MINUTES_PER_DAY) / TimeConstants.MINUTES_PER_HOUR);
    }

    /** The in-game minute of that hour. */
    public int minute() {
        return (int) (clockMinutes % TimeConstants.MINUTES_PER_HOUR);
    }

    /** Writes the length field and the summary. */
    void write(DataOutputStream out) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        try (DataOutputStream body = new DataOutputStream(bytes)) {
            body.writeLong(savedAt);
            body.writeUTF(version);
            body.writeDouble(playSeconds);
            body.writeUTF(mode.id);
            body.writeBoolean(creativeMarked);
            body.writeDouble(clockMinutes);
            body.writeUTF(place);
        }
        if (bytes.size() > MAX_BYTES) {
            throw new IOException("save summary of " + bytes.size() + " bytes");
        }
        out.writeInt(bytes.size());
        bytes.writeTo(out);
    }

    /** Reads what {@link #write} wrote, refusing anything else. */
    static SaveSummary read(DataInputStream in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_BYTES) {
            throw new IOException("invalid save summary length " + length);
        }
        byte[] bytes = in.readNBytes(length);
        if (bytes.length != length) {
            throw new IOException("the file ends inside the save summary");
        }
        try (DataInputStream body = new DataInputStream(new ByteArrayInputStream(bytes))) {
            SaveSummary summary = new SaveSummary(body.readLong(), body.readUTF(), body.readDouble(),
                    GameMode.fromId(body.readUTF()), body.readBoolean(), body.readDouble(), body.readUTF());
            if (body.available() != 0) {
                throw new IOException("unexpected bytes after the save summary");
            }
            return summary;
        } catch (IllegalArgumentException invalid) {
            throw new IOException("invalid save summary: " + invalid.getMessage(), invalid);
        }
    }

    /**
     * What a save file holds, from its header, its summary and its checksum alone. Nothing is
     * built, and at most the file's own length is read, in bounded pieces.
     *
     * @param problem   why the file would not load, or null when it is intact
     * @param generator the world generator the header names; 0 unless intact
     * @param summary   the file's summary; null unless intact
     */
    public record Inspection(LoadResult problem, int generator, SaveSummary summary) {
        /** True when the header, the summary and the checksum all read as this build writes them. */
        public boolean intact() {
            return problem == null;
        }
    }

    /** Inspects the file at {@code file}; see {@link Inspection}. */
    public static Inspection inspect(Path file) {
        long size;
        try {
            if (!Files.exists(file)) {
                return new Inspection(new LoadResult(LoadResult.Outcome.NO_SAVE, "no save at " + file), 0, null);
            }
            size = Files.size(file);
        } catch (IOException | SecurityException unreadable) {
            return new Inspection(new LoadResult(LoadResult.Outcome.UNREADABLE, unreadable.toString()), 0, null);
        }
        if (size > SaveSystem.MAX_SAVE_BYTES) {
            return new Inspection(LoadResult.damaged("save exceeds " + SaveSystem.MAX_SAVE_BYTES + " bytes"), 0, null);
        }
        try (InputStream raw = Files.newInputStream(file)) {
            byte[] start = raw.readNBytes((int) Math.min(size, MAX_PREFIX));
            LoadResult refused = SaveHeader.refusal(start);
            if (refused != null) {
                return new Inspection(refused, 0, null);
            }
            DataInputStream in = new DataInputStream(new ByteArrayInputStream(start));
            SaveHeader.skipChecked(in);
            in.readLong();
            int generator = in.readInt();
            if (start.length < SaveChecksum.OFFSET + SaveChecksum.LENGTH + Integer.BYTES) {
                throw new IOException("the file ends before its save summary");
            }
            int stored = in.readInt();
            SaveSummary summary = read(in);
            if (SaveChecksum.of(start, raw) != stored) {
                return new Inspection(LoadResult.damaged("the checksum does not match the file"), 0, null);
            }
            return new Inspection(null, generator, summary);
        } catch (IOException damaged) {
            return new Inspection(LoadResult.damaged(damaged.toString()), 0, null);
        } catch (SecurityException unreadable) {
            return new Inspection(new LoadResult(LoadResult.Outcome.UNREADABLE, unreadable.toString()), 0, null);
        }
    }

    /** {@code text} cut to at most {@code max} characters, never inside a surrogate pair. */
    static String fit(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int end = Character.isHighSurrogate(text.charAt(max - 1)) ? max - 1 : max;
        return text.substring(0, end);
    }
}
