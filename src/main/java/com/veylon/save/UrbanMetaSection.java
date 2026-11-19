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
 * The section {@code urban.meta}: the revisions an urban world was created
 * under (urban-survival decision D-04). Every city writes it and a city save
 * without it is refused; a wilderness world never writes it.
 *
 * <p>Version 1 holds the layout revision and the content revision as two ints.
 *
 * <p>Reading it validates rather than trusts. The layout revision must be the
 * one the save's generator builds — the generator, read from the header before
 * any chunk generates, is what actually selected the geometry — and the
 * content revision must be the one a new world of that generator records
 * ({@link UrbanRevision#contentFor}), the only one this build writes for it.
 * Any mismatch, a section in a non-urban world, another section version or
 * trailing bytes fails the whole load.
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
            int written = UrbanRevision.contentFor(game.world.generatorVersion);
            if (contentRevision != written) {
                throw new IOException("urban content revision " + contentRevision + " under generator "
                        + game.world.generatorVersion + ", whose worlds this build writes with "
                        + written);
            }
            try {
                urban.resolveContentRevision(contentRevision);
            } catch (IllegalArgumentException | IllegalStateException invalid) {
                throw new IOException(invalid.getMessage(), invalid);
            }
        }
    }

    /** Refuses an urban save without the section. */
    static void finishLoad(Game game) throws IOException {
        UrbanWorldState urban = game.world.urban;
        if (urban != null && !urban.contentRevisionResolved()) {
            throw new IOException("missing required " + ID);
        }
    }
}
