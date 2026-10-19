package com.veylon.save;

import com.veylon.Game;
import com.veylon.world.urban.UrbanDiscovery;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * {@code urban.discovery} v1 (milestone 14): the transit stations and hidden cellars a layout-ten
 * world's survivor has found. Payload: int version, int count (at most
 * {@link UrbanDiscovery#MAX_SITES}), then that many site ids as longs in strictly ascending order.
 * Every layout-ten save writes it, empty or not; absent means nothing has been found. A world
 * without routes rejects it, and an invalid, duplicate or unordered id fails the whole load.
 */
final class UrbanDiscoverySection {
    static final String ID = "urban.discovery";
    private static final int VERSION = 1;

    private UrbanDiscoverySection() { }

    static boolean present(Game g) {
        return g.world.urban != null && g.world.urban.hasUnderground();
    }

    static byte[] write(Game g) throws IOException {
        List<Long> sites = g.world.urban.discovery.sites();
        if (sites.size() > UrbanDiscovery.MAX_SITES) { throw new IOException("too many discovered sites"); }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream(8 + sites.size() * 8);
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(sites.size());
            for (long id : sites) { out.writeLong(id); }
        }
        return bytes.toByteArray();
    }

    static void read(byte[] payload, Game g) throws IOException {
        if (!present(g)) { throw new IOException("unexpected " + ID + " in a world without routes"); }
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(payload))) {
            if (in.available() < 8 || in.readInt() != VERSION) { throw new IOException("unsupported or truncated " + ID); }
            int count = SaveSystem.readCount(in, "discovered sites", UrbanDiscovery.MAX_SITES);
            if ((long) count * 8 != in.available()) { throw new IOException("invalid " + ID + " length"); }
            List<Long> sites = new ArrayList<>(count);
            long previous = Long.MIN_VALUE;
            for (int i = 0; i < count; i++) {
                long id = in.readLong();
                if (i > 0 && id <= previous || !UrbanDiscovery.isSite(id)) {
                    throw new IOException("invalid or unordered discovered site " + id);
                }
                sites.add(id);
                previous = id;
            }
            g.world.urban.discovery.restore(sites);
        }
    }
}
