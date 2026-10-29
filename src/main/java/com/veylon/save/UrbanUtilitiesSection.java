package com.veylon.save;

import com.veylon.Game;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanUtilities;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * urban.utilities v1 (urban milestone 19): what a layout-fifteen city's refuge utilities keep.
 * Required for generator 18, rejected in any other world.
 *
 * <p>Payload: int version (1); int count of tapped buildings (at most
 * {@value UrbanUtilities#MAX_TAPPED}), then per building its long building id and the int litres
 * drawn from its taps, sorted by id; int count of devices, which this version keeps none of and
 * must be zero.
 *
 * <p>A row must name a planned building whose taps held something, with at least one litre and no
 * more than they held drawn, once. Anything else, a count over its cap or not matching the length,
 * or trailing bytes fails the whole load and leaves the live world alone; without the section a
 * generator-18 save is rejected rather than having its emptied pipes come back full.
 */
final class UrbanUtilitiesSection {
    static final String ID = "urban.utilities";
    static final int VERSION = 1;

    private UrbanUtilitiesSection() {
    }

    static boolean manages(Game g) {
        return g.world.urban != null && g.world.urban.hasUtilities();
    }

    static byte[] write(Game g) throws IOException {
        UrbanUtilities u = g.world.urban.utilities;
        List<Map.Entry<Long, Integer>> rows = new ArrayList<>(u.drawnRows().entrySet());
        rows.sort(Map.Entry.comparingByKey());
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(rows.size());
            for (Map.Entry<Long, Integer> row : rows) {
                out.writeLong(row.getKey());
                out.writeInt(row.getValue());
            }
            out.writeInt(0);
        }
        parse(g, bytes.toByteArray(), false);
        return bytes.toByteArray();
    }

    static void read(byte[] bytes, Game g) throws IOException {
        if (!manages(g)) { throw new IOException("unexpected " + ID); }
        parse(g, bytes, true);
    }

    /** Checks every row; restores them only when {@code apply}. */
    private static void parse(Game g, byte[] bytes, boolean apply) throws IOException {
        Map<Long, Integer> drawn = new HashMap<>();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) { throw new IOException("unsupported " + ID + " version"); }
            int count = SaveSystem.readCount(in, "tapped buildings", UrbanUtilities.MAX_TAPPED);
            for (int i = 0; i < count; i++) {
                long building = in.readLong();
                int litres = in.readInt();
                if (!UrbanIds.isValid(building) || UrbanIds.kind(building) != UrbanIds.Kind.BUILDING) {
                    throw new IOException(ID + " names no building");
                }
                BuildingPlan plan = g.world.urban.buildingById(building);
                if (plan == null) { throw new IOException(ID + " names a building nothing planned"); }
                if (litres < 1 || litres > UrbanUtilities.capacity(plan) || drawn.put(building, litres) != null) {
                    throw new IOException("invalid " + ID + " tap row");
                }
            }
            if (in.readInt() != 0) { throw new IOException(ID + " version 1 keeps no devices"); }
            if (in.available() != 0) { throw new IOException("trailing " + ID + " bytes"); }
        }
        if (apply) { g.world.urban.utilities.restore(drawn); }
    }

    /** A generator-18 save without the section would bring emptied pipes back full: reject it. */
    static void finishLoad(Game g) throws IOException {
        if (manages(g) && !g.world.urban.utilities.sectionRestored()) {
            throw new IOException("missing required " + ID);
        }
    }
}
