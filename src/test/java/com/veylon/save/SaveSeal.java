package com.veylon.save;

/**
 * Gives save bytes a test has edited the checksum they would have had if the writer had written
 * them (0.9.1 decision D-22), so that only the reader's own checks judge the edit. A test of the
 * checksum itself leaves its edit unsealed.
 */
public final class SaveSeal {

    private SaveSeal() {
    }

    /**
     * The header and the body of {@code save}, without its checksum and its summary: what two saves
     * of one state share. The summary says when and where a save was written, and the place it
     * names reads chunks and trackers a save does not hold, so two saves of one state may differ
     * there and nowhere else.
     */
    public static byte[] withoutSummary(byte[] save) {
        int summaryAt = SaveChecksum.OFFSET + SaveChecksum.LENGTH;
        int length = java.nio.ByteBuffer.wrap(save).getInt(summaryAt);
        int bodyAt = summaryAt + Integer.BYTES + length;
        byte[] out = new byte[SaveHeader.LENGTH + save.length - bodyAt];
        System.arraycopy(save, 0, out, 0, SaveHeader.LENGTH);
        System.arraycopy(save, bodyAt, out, SaveHeader.LENGTH, save.length - bodyAt);
        return out;
    }

    /** A copy of {@code save} carrying the checksum of its own contents. */
    public static byte[] resealed(byte[] save) {
        if (save.length < SaveChecksum.OFFSET + SaveChecksum.LENGTH) {
            return save.clone(); // too short to hold a checksum: the header check refuses it first
        }
        byte[] copy = save.clone();
        int checksum = SaveChecksum.of(copy);
        copy[SaveChecksum.OFFSET] = (byte) (checksum >>> 24);
        copy[SaveChecksum.OFFSET + 1] = (byte) (checksum >>> 16);
        copy[SaveChecksum.OFFSET + 2] = (byte) (checksum >>> 8);
        copy[SaveChecksum.OFFSET + 3] = (byte) checksum;
        return copy;
    }
}
