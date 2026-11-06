package com.veylon.save;

import com.veylon.Game;
import com.veylon.world.urban.UrbanRevision;
import com.veylon.world.urban.UrbanWorldState;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Optional v3 section {@code urban.meta}: the revisions an urban world was
 * created under (urban-survival decision D-04).
 *
 * <p>Version 1 holds the layout revision and the content revision as two ints.
 * Only urban worlds write it, so a legacy world's save is unchanged and older
 * builds read it exactly as before. An older build never reaches this section
 * in an urban save: it rejects the generator version first, before replacing
 * the live world.
 *
 * <p>Reading it validates rather than trusts. The layout revision must be the
 * one the save's generator builds — the generator, read from the base before
 * any chunk generates, is what actually selected the geometry — and the
 * content revision must be one this build supports. Any mismatch, a section in
 * a non-urban world, another section version or trailing bytes fails the whole
 * load. An urban save without the section takes
 * {@link UrbanRevision#ABSENT_CONTENT} once every section has been read.
 */
final class UrbanMetaSection {
    static final String ID = "urban.meta";
    private static final int VERSION = 1;

    private UrbanMetaSection() {
    }

    static byte[] write(Game game) throws IOException {
        UrbanWorldState urban = game.world.urban;
        if (urban == null || !urban.contentRevisionResolved()) {
            throw new IOException("urban revisions are not resolved for this world");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(urban.layoutRevision());
            out.writeInt(urban.contentRevision());
        }
        return bytes.toByteArray();
    }

    static void read(byte[] payload, Game game) throws IOException {
        UrbanWorldState urban = game.world.urban;
        if (urban == null) {
            throw new IOException("urban.meta section in a non-urban world (generator "
                    + game.world.generatorVersion + ")");
        }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("unsupported urban.meta section version " + version);
            }
            int layoutRevision = in.readInt();
            int contentRevision = in.readInt();
            if (in.available() != 0) {
                throw new IOException("unexpected bytes after urban.meta section");
            }
            if (layoutRevision != urban.layoutRevision()) {
                throw new IOException("urban layout revision " + layoutRevision
                        + " does not match generator " + game.world.generatorVersion
                        + ", which builds layout revision " + urban.layoutRevision());
            }
            try {
                urban.resolveContentRevision(contentRevision);
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                throw new IOException(invalid.getMessage(), invalid);
            }
        }
    }

    /** Gives an urban world without the section its documented default. */
    static void finishLoad(Game game) {
        UrbanWorldState urban = game.world.urban;
        if (urban != null && !urban.contentRevisionResolved()) {
            urban.resolveContentRevision(UrbanRevision.ABSENT_CONTENT);
        }
    }
}
