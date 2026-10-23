package com.veylon.save;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.FixtureStates;
import com.veylon.world.urban.UrbanFortifications;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * urban.fortifications v1 (urban milestone 18): the strength left in a layout-fourteen city's
 * damaged barricades and braced doors. Required for generator 17, rejected in any other world.
 *
 * <p>Payload: int version (1); int count of damaged barricade cells (at most
 * {@value UrbanFortifications#MAX_DAMAGED}), then per cell int x, y, z and float strength; int
 * count of braces (at most {@value UrbanFortifications#MAX_BRACES}), then per brace the int x, y, z
 * of its door's bottom cell and float strength. Rows are written sorted by y, x, z.
 *
 * <p>A barricade is always a recorded edit, so a damaged cell must hold a barricade in the save's
 * block edits and less than that kind's full strength, more than none; a cell at full strength is
 * never written. A brace must stand behind a door barred in {@code world.fixtures}, with strength
 * above none and at most a brace's full strength. The section is read once every other section is
 * in, so the order sections appear in never matters. A duplicate, a count over its cap or not
 * matching the length, a non-finite value or anything above fails the whole load and leaves the
 * live world alone; without the section a generator-17 save is rejected rather than having its
 * battered barricades come back whole.
 */
final class UrbanFortificationsSection {
    static final String ID = "urban.fortifications";
    static final int VERSION = 1;

    private UrbanFortificationsSection() {
    }

    static boolean manages(Game g) {
        return g.world.urban != null && g.world.urban.hasRefuges();
    }

    static byte[] write(Game g) throws IOException {
        UrbanFortifications f = g.world.urban.fortifications;
        f.prepareSave(g.world);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            writeRows(out, f.damagedCells());
            writeRows(out, f.braceCells());
        }
        parse(g, bytes.toByteArray(), false);
        return bytes.toByteArray();
    }

    private static void writeRows(DataOutputStream out, Map<Vec3i, Float> rows) throws IOException {
        List<Map.Entry<Vec3i, Float>> sorted = new ArrayList<>(rows.entrySet());
        sorted.sort(Comparator.comparingInt((Map.Entry<Vec3i, Float> e) -> e.getKey().y())
                .thenComparingInt(e -> e.getKey().x()).thenComparingInt(e -> e.getKey().z()));
        out.writeInt(sorted.size());
        for (Map.Entry<Vec3i, Float> e : sorted) {
            SaveSystem.writeVec(out, e.getKey());
            out.writeFloat(e.getValue());
        }
    }

    static void read(byte[] bytes, Game g) throws IOException {
        if (!manages(g)) { throw new IOException("unexpected " + ID); }
        parse(g, bytes, true);
    }

    /** Checks every row; restores them only when {@code apply}. */
    private static void parse(Game g, byte[] bytes, boolean apply) throws IOException {
        Map<Vec3i, Float> damaged = new HashMap<>();
        Map<Vec3i, Float> braces = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) { throw new IOException("unsupported " + ID + " version"); }
            int count = SaveSystem.readCount(in, "damaged barricades", UrbanFortifications.MAX_DAMAGED);
            for (int i = 0; i < count; i++) {
                Vec3i cell = cell(in);
                Byte edit = g.world.changedBlocks.get(cell);
                BlockType t = edit == null ? BlockType.AIR : BlockType.byId(edit);
                float h = in.readFloat();
                if (!UrbanFortifications.isBarricade(t)) { throw new IOException(ID + " names no barricade"); }
                if (!Float.isFinite(h) || h <= 0 || h >= UrbanFortifications.strength(t) || damaged.put(cell, h) != null) {
                    throw new IOException("invalid " + ID + " barricade row");
                }
            }
            count = SaveSystem.readCount(in, "door braces", UrbanFortifications.MAX_BRACES);
            for (int i = 0; i < count; i++) {
                Vec3i cell = cell(in);
                float h = in.readFloat();
                if (!Float.isFinite(h) || h <= 0 || h > UrbanFortifications.BRACE_STRENGTH || braces.put(cell, h) != null) {
                    throw new IOException("invalid " + ID + " brace row");
                }
                int state = g.world.fixtures.stateAt(cell.x(), cell.y(), cell.z());
                if (state < 0 || FixtureStates.lock(state) != FixtureStates.LOCK_BARRED) {
                    throw new IOException(ID + " braces a door that is not barred");
                }
            }
            if (in.available() != 0) { throw new IOException("trailing " + ID + " bytes"); }
        }
        if (apply) { g.world.urban.fortifications.restore(damaged, braces); }
    }

    private static Vec3i cell(DataInputStream in) throws IOException {
        Vec3i cell = SaveSystem.readVec(in);
        if (cell.y() < 0 || cell.y() >= Chunk.SY) { throw new IOException("invalid " + ID + " cell"); }
        return cell;
    }

    /** A generator-17 save without the section would bring battered barricades back whole: reject it. */
    static void finishLoad(Game g) throws IOException {
        if (manages(g) && !g.world.urban.fortifications.sectionRestored()) {
            throw new IOException("missing required " + ID);
        }
    }
}
