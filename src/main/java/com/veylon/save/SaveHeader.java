package com.veylon.save;

import com.veylon.world.World;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * The fixed start of every save file, and the first thing a load reads (0.9.1 decisions D-01 and
 * D-16). A build opens only saves of its own format and epoch, and decides that from these bytes
 * before it builds anything, so a refused file costs neither the live world nor the isolated
 * verifier pass.
 *
 * <pre>
 * int  magic      "VEYL"
 * int  format     {@value #FORMAT}: the layout of everything after the header
 * int  epoch      {@value #EPOCH}: the save epoch of the build that wrote the file
 * long seed
 * int  generator  a version {@link World#isSupportedGenerator} accepts
 * </pre>
 *
 * <p>The magic, the format and the epoch keep these offsets in every later format, so any build
 * can tell a save it must refuse from a damaged one. Formats 2 (0.1.0 to 0.2.0) and 3 (0.3.0 to
 * 0.9.0) put the seed straight after the format and carry no epoch; they are older by format alone.
 *
 * <p>Two builds share an epoch only if each opens every save the other writes, into the same world
 * and state. {@code SaveEpochGuardTest} digests what decides that and fails, once an epoch has
 * been released, when any of it changes without a new epoch.
 */
public final class SaveHeader {

    /** "VEYL". */
    public static final int MAGIC = 0x5645594C;
    /** The layout this build writes and the only one it reads. */
    public static final int FORMAT = 4;
    /** The save epoch this build writes and the only one it opens. */
    public static final int EPOCH = 1;
    /** Bytes from the start of the file to the end of the generator id. */
    public static final int LENGTH = 4 + 4 + 4 + 8 + 4;

    private SaveHeader() {
    }

    /** Writes the header of a save of this build. */
    static void write(DataOutputStream out, long seed, int generator) throws IOException {
        out.writeInt(MAGIC);
        out.writeInt(FORMAT);
        out.writeInt(EPOCH);
        out.writeLong(seed);
        out.writeInt(generator);
    }

    /**
     * Why this build must refuse a file that begins with {@code bytes}, decided from the header
     * alone; null when it may go on to read the rest.
     *
     * @param bytes the start of the file, or all of it; shorter than {@link #LENGTH} means the
     *              file ends inside its header
     */
    static LoadResult refusal(byte[] bytes) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (bytes.length < Integer.BYTES) {
                return LoadResult.damaged("the file ends before the save header (" + bytes.length + " bytes)");
            }
            int magic = in.readInt();
            if (magic != MAGIC) {
                return new LoadResult(LoadResult.Outcome.NOT_A_SAVE,
                        String.format("no save magic (0x%08X)", magic));
            }
            if (bytes.length < 2 * Integer.BYTES) {
                return LoadResult.damaged("the file ends before the save format");
            }
            int format = in.readInt();
            if (format < FORMAT && format > 0) {
                return new LoadResult(LoadResult.Outcome.OLDER_VERSION, "save format " + format
                        + " (VEYLON 0.9.0 or earlier); this build reads format " + FORMAT + " only");
            }
            if (format > FORMAT) {
                return new LoadResult(LoadResult.Outcome.NEWER_VERSION, "save format " + format
                        + "; this build reads format " + FORMAT + " only");
            }
            if (format != FORMAT) {
                return LoadResult.damaged("invalid save format " + format);
            }
            if (bytes.length < 3 * Integer.BYTES) {
                return LoadResult.damaged("the file ends before the save epoch");
            }
            int epoch = in.readInt();
            if (epoch < EPOCH && epoch > 0) {
                return new LoadResult(LoadResult.Outcome.OLDER_VERSION, "save epoch " + epoch
                        + "; this build opens epoch " + EPOCH + " only");
            }
            if (epoch > EPOCH) {
                return new LoadResult(LoadResult.Outcome.NEWER_VERSION, "save epoch " + epoch
                        + "; this build opens epoch " + EPOCH + " only");
            }
            if (epoch != EPOCH) {
                return LoadResult.damaged("invalid save epoch " + epoch);
            }
            if (bytes.length < LENGTH) {
                return LoadResult.damaged("the file ends inside the save header (" + bytes.length + " bytes)");
            }
            in.readLong();
            int generator = in.readInt();
            if (!World.isSupportedGenerator(generator)) {
                return LoadResult.damaged("unsupported world generator version " + generator);
            }
            return null;
        } catch (IOException impossible) {
            // Every read above is bounded by the length checks before it.
            throw new IllegalStateException(impossible);
        }
    }

    /**
     * Reads past the magic, the format and the epoch of a payload that {@link #refusal} accepted,
     * checking them again so the reader never depends on having been called in order.
     */
    static void skipChecked(DataInputStream in) throws IOException {
        if (in.readInt() != MAGIC || in.readInt() != FORMAT || in.readInt() != EPOCH) {
            throw new IOException("the save header changed after it was checked");
        }
    }
}
