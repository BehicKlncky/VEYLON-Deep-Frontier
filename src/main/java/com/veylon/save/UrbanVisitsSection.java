package com.veylon.save;

import com.veylon.Game;
import com.veylon.world.urban.UrbanVisits;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.function.LongPredicate;

/**
 * {@code urban.visits} v1 (urban milestone 21): the districts the survivor of a world from layout
 * sixteen (generator 19) on has walked and the buildings they have entered, which the map labels.
 * Payload: int version, int district count (at most {@link UrbanVisits#MAX_DISTRICTS}), that many
 * district ids as longs in strictly ascending order, then int building count (at most
 * {@link UrbanVisits#MAX_BUILDINGS}) and that many building ids likewise. Every such save writes it, empty or not.
 *
 * <p>Absent means nothing walked or entered, which is how a layout-sixteen save written before
 * package 21 loads. A world of an earlier layout refuses the section, and a wrong kind of id, a
 * duplicate or unordered id, a count past its cap or a length that does not match fails the whole
 * load. The section is optional to older readers: a build of package 20 skips its unknown id and
 * loads the world without it.
 */
final class UrbanVisitsSection {
    static final String ID = "urban.visits";
    private static final int VERSION = 1;

    private UrbanVisitsSection() { }

    static boolean present(Game g) {
        return g.world.urban != null && g.world.urban.hasMissions();
    }

    static byte[] write(Game g) throws IOException {
        UrbanVisits visits = g.world.urban.visits;
        List<Long> districts = visits.districts(), buildings = visits.buildings();
        if (districts.size() > UrbanVisits.MAX_DISTRICTS || buildings.size() > UrbanVisits.MAX_BUILDINGS) {
            throw new IOException("too many visited places");
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(12 + (districts.size() + buildings.size()) * 8);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(districts.size());
            for (long id : districts) { out.writeLong(id); }
            out.writeInt(buildings.size());
            for (long id : buildings) { out.writeLong(id); }
        }
        return bytes.toByteArray();
    }

    static void read(byte[] payload, Game g) throws IOException {
        if (!present(g)) { throw new IOException("unexpected " + ID + " in a world without missions"); }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.available() < 8 || in.readInt() != VERSION) { throw new IOException("unsupported or truncated " + ID); }
            List<Long> districts = ids(in, "walked districts", UrbanVisits.MAX_DISTRICTS, UrbanVisits::isDistrict);
            if (in.available() < 4) { throw new IOException("truncated " + ID); }
            List<Long> buildings = ids(in, "entered buildings", UrbanVisits.MAX_BUILDINGS, UrbanVisits::isBuilding);
            if (in.available() != 0) { throw new IOException("invalid " + ID + " length"); }
            try {
                g.world.urban.visits.restore(districts, buildings);
            } catch (IllegalArgumentException e) {
                throw new IOException("invalid " + ID + ": " + e.getMessage(), e);
            }
        }
    }

    /** A counted run of strictly ascending ids of one kind. */
    private static List<Long> ids(DataInputStream in, String label, int max, LongPredicate valid) throws IOException {
        int count = SaveSystem.readCount(in, label, max);
        if ((long) count * 8 > in.available()) { throw new IOException("truncated " + label + " in " + ID); }
        List<Long> ids = new ArrayList<>(count);
        long previous = Long.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            long id = in.readLong();
            if (i > 0 && id <= previous || !valid.test(id)) {
                throw new IOException("invalid or unordered " + label + " id " + id);
            }
            ids.add(id);
            previous = id;
        }
        return ids;
    }
}
