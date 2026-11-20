package com.veylon.save;

import java.io.IOException;
import java.io.InputStream;
import java.util.zip.CRC32C;

/**
 * The checksum every save carries right after its {@link SaveHeader} (0.9.1 decision D-22): the
 * CRC-32C of the whole file with its own four bytes read as zero. The writer fills it in once
 * everything else is on disk; the slot picker checks it without building a world, and a load
 * checks it after reading everything else, so the reader's own refusals still name their cause
 * first and the checksum catches damage that happens to parse.
 */
public final class SaveChecksum {

    /** Where the checksum sits: straight after the header. */
    public static final int OFFSET = SaveHeader.LENGTH;
    /** Its width. */
    public static final int LENGTH = Integer.BYTES;
    private static final byte[] ZEROS = new byte[LENGTH];

    private SaveChecksum() {
    }

    /**
     * The checksum of a whole save file.
     *
     * @param file every byte of the file; shorter than the checksum's end means no checksum fits
     * @throws IllegalArgumentException when the file ends before its checksum
     */
    public static int of(byte[] file) {
        if (file.length < OFFSET + LENGTH) {
            throw new IllegalArgumentException("a save of " + file.length + " bytes ends before its checksum");
        }
        CRC32C crc = new CRC32C();
        crc.update(file, 0, OFFSET);
        crc.update(ZEROS);
        crc.update(file, OFFSET + LENGTH, file.length - OFFSET - LENGTH);
        return (int) crc.getValue();
    }

    /** The checksum the file says it has. */
    static int stored(byte[] file) {
        return ((file[OFFSET] & 255) << 24) | ((file[OFFSET + 1] & 255) << 16)
                | ((file[OFFSET + 2] & 255) << 8) | (file[OFFSET + 3] & 255);
    }

    /**
     * The checksum of a file read as a stream: {@code start}, its first bytes, which hold at least
     * the header and the checksum, then the rest of {@code tail}.
     */
    static int of(byte[] start, InputStream tail) throws IOException {
        if (start.length < OFFSET + LENGTH) {
            throw new IOException("the file ends before its checksum");
        }
        CRC32C crc = new CRC32C();
        crc.update(start, 0, OFFSET);
        crc.update(ZEROS);
        crc.update(start, OFFSET + LENGTH, start.length - OFFSET - LENGTH);
        byte[] buffer = new byte[64 * 1024];
        for (int read = tail.read(buffer); read >= 0; read = tail.read(buffer)) {
            crc.update(buffer, 0, read);
        }
        return (int) crc.getValue();
    }
}
