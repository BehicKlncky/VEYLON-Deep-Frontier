package com.veylon.save;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Door and window state ({@code world.fixtures} v1, decision D-49): which
 * openings stand open, and which are locked or barred and from which side.
 *
 * <p>Payload: int section version, int count (at most
 * {@link FixtureStates#MAX_ENTRIES}, the same cap the live map enforces), then
 * per cell int x, int y, int z and one state byte, sorted by y, x, z so a world
 * saves to the same bytes every time. A resident's door, open only while they
 * pass, is written closed.
 *
 * <p>Written only when some fixture has state, so a save without doors or
 * windows keeps exactly the sections it had. Absent means every door and
 * window stands as generated or edited, unlocked. A cell outside the chunk
 * height, an invalid state byte, a duplicate cell, a count above the cap, an
 * unknown section version or trailing bytes fail the whole load (C-5, C-10).
 * A valid entry whose cell no longer holds a door or window is dropped, never
 * applied to something else ({@link World#restoreFixtureState}).
 *
 * <p>Compatibility: builds without this section skip it by its length and
 * load the doors and windows closed and unlocked (and, before package 04,
 * read their block ids as air); a later incompatible change gets a new
 * section id.
 */
final class FixtureStatesSection {

    static final String ID = "world.fixtures";
    private static final int VERSION = 1;
    private static final int ENTRY_BYTES = 13;

    private FixtureStatesSection() {
    }

    /** True when the world has fixture state worth a section. */
    static boolean present(Game g) {
        return !g.world.fixtures.isEmpty();
    }

    static byte[] write(Game g) throws IOException {
        World w = g.world;
        if (w.fixtures.size() > FixtureStates.MAX_ENTRIES) {
            throw new IOException("too many fixture states: " + w.fixtures.size());
        }
        List<long[]> rows = new ArrayList<>(w.fixtures.size());
        w.fixtures.forEach((pos, state) -> rows.add(new long[]{pos.y(), pos.x(), pos.z(),
                w.fixtures.savedState(w, pos, state)}));
        rows.sort(Comparator.<long[]>comparingLong(r -> r[0])
                .thenComparingLong(r -> r[1]).thenComparingLong(r -> r[2]));
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + rows.size() * ENTRY_BYTES);
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(VERSION);
        out.writeInt(rows.size());
        for (long[] r : rows) {
            out.writeInt((int) r[1]);
            out.writeInt((int) r[0]);
            out.writeInt((int) r[2]);
            out.writeByte((int) r[3]);
        }
        out.flush();
        return bytes.toByteArray();
    }

    static void read(byte[] payload, Game g) throws IOException {
        DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload));
        if (in.available() < 8) {
            throw new IOException("truncated " + ID);
        }
        int version = in.readInt();
        if (version != VERSION) {
            throw new IOException("unsupported " + ID + " version " + version);
        }
        int count = in.readInt();
        if (count < 0 || count > FixtureStates.MAX_ENTRIES) {
            throw new IOException("invalid " + ID + " count " + count);
        }
        if ((long) count * ENTRY_BYTES != in.available()) {
            throw new IOException(ID + " length does not match its count");
        }
        Set<Vec3i> seen = new HashSet<>();
        for (int i = 0; i < count; i++) {
            Vec3i pos = new Vec3i(in.readInt(), in.readInt(), in.readInt());
            int state = in.readUnsignedByte();
            if (pos.y() < 0 || pos.y() >= Chunk.SY || !seen.add(pos)) {
                throw new IOException("invalid " + ID + " cell " + pos);
            }
            if (!g.world.restoreFixtureState(pos, state)) {
                throw new IOException("invalid " + ID + " state " + state + " at " + pos);
            }
        }
    }
}
