package com.veylon.save;

import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * The headers of saves this build must refuse, built in code from the bytes of a save it wrote
 * (0.9.1 O1: no historical fixture is kept). Only the header changes; the body stays this build's,
 * which a refusal never reads.
 */
public final class ForeignSaves {

    private static final int FORMAT_AT = 4;
    private static final int EPOCH_AT = 8;

    private ForeignSaves() {
    }

    /**
     * The header VEYLON 0.3.0 to 0.9.0 wrote, format 3: the magic, the format, then the seed and
     * the generator, with no epoch.
     */
    public static byte[] asFormatThree(byte[] save) {
        byte[] older = withoutEpoch(save);
        ByteBuffer.wrap(older).putInt(FORMAT_AT, 3);
        return older;
    }

    /** The header VEYLON 0.1.0 and 0.2.0 wrote, format 2: the magic, the format and the seed. */
    public static byte[] asFormatTwo(byte[] save) {
        byte[] three = asFormatThree(save);
        byte[] two = new byte[three.length - Integer.BYTES];
        System.arraycopy(three, 0, two, 0, 16);
        System.arraycopy(three, 20, two, 16, three.length - 20);
        ByteBuffer.wrap(two).putInt(FORMAT_AT, 2);
        return two;
    }

    /** The same save under another format number, its header otherwise as written. */
    public static byte[] withFormat(byte[] save, int format) {
        byte[] other = save.clone();
        ByteBuffer.wrap(other).putInt(FORMAT_AT, format);
        return other;
    }

    /** The same save under another epoch. */
    public static byte[] withEpoch(byte[] save, int epoch) {
        byte[] other = save.clone();
        ByteBuffer.wrap(other).putInt(EPOCH_AT, epoch);
        return other;
    }

    /** The first {@code length} bytes. */
    public static byte[] cut(byte[] save, int length) {
        return Arrays.copyOf(save, length);
    }

    private static byte[] withoutEpoch(byte[] save) {
        byte[] out = new byte[save.length - Integer.BYTES];
        System.arraycopy(save, 0, out, 0, EPOCH_AT);
        System.arraycopy(save, EPOCH_AT + Integer.BYTES, out, EPOCH_AT, save.length - EPOCH_AT - Integer.BYTES);
        return out;
    }
}
