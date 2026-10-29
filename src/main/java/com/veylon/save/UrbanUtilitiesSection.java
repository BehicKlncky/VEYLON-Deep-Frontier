package com.veylon.save;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.UrbanUtilities.Device;
import com.veylon.world.urban.UrbanUtilities.Kind;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * urban.utilities v1 (urban milestone 19): what a layout-fifteen city's refuge utilities keep.
 * Required for generator 18, rejected in any other world.
 *
 * <p>Payload: int version (1); int count of tapped buildings (at most
 * {@value UrbanUtilities#MAX_TAPPED}), then per building its long building id and the int litres
 * drawn from its taps, sorted by id; int count of devices (at most
 * {@value UrbanUtilities#MAX_DEVICES}), then per device, in the order they were put down, a byte
 * kind code, its int x, y, z, a byte switch (0 off, 1 on), float condition (a generator's wear, a
 * purifier's filter), float a and float b: a generator's fuel and zero, a purifier's untreated and
 * clean litres, zero and zero for a lamp.
 *
 * <p>A tap row must name a planned building whose taps held something, with at least one litre and
 * no more than they held drawn, once. A device row must stand on a recorded edit of its own block
 * inside a planned building, once, with condition from none to full; a generator's fuel no more
 * than a full tank and only a generator that can run switched on; one generator to a building; a
 * lamp's condition full; a purifier's tanks each from empty to full. Anything else, a count over its cap or not matching the
 * length, a non-finite value or trailing bytes fails the whole load and leaves the live world
 * alone; without the section a generator-18 save is rejected rather than having its emptied pipes
 * come back full and its generators forgotten.
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
            out.writeInt(u.deviceCount());
            for (Device d : u.devices()) {
                out.writeByte(d.kind.code);
                SaveSystem.writeVec(out, d.pos);
                out.writeByte(d.on ? 1 : 0);
                out.writeFloat(d.condition);
                out.writeFloat(d.kind == Kind.PURIFIER ? d.untreated : d.fuel);
                out.writeFloat(d.kind == Kind.PURIFIER ? d.clean : 0f);
            }
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
        List<Device> devices = new ArrayList<>();
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
            count = SaveSystem.readCount(in, "utility devices", UrbanUtilities.MAX_DEVICES);
            Set<Vec3i> cells = new HashSet<>();
            Set<Long> powered = new HashSet<>();
            for (int i = 0; i < count; i++) {
                Kind kind = Kind.byCode(in.readUnsignedByte());
                Vec3i cell = SaveSystem.readVec(in);
                int on = in.readUnsignedByte();
                float condition = in.readFloat(), a = in.readFloat(), b = in.readFloat();
                if (kind == null) { throw new IOException(ID + " names no kind of device"); }
                Byte edit = UrbanUtilities.inRange(cell) ? g.world.changedBlocks.get(cell) : null;
                if (edit == null || BlockType.byId(edit) != kind.block || !cells.add(cell)) {
                    throw new IOException(ID + " device stands on no " + kind.noun);
                }
                long building = UrbanUtilities.buildingOf(g.world, cell);
                if (building == UrbanIds.NONE) { throw new IOException(ID + " device outside every building"); }
                if (on > 1 || !finite(condition, a, b) || condition < 0 || condition > UrbanUtilities.SOUND) {
                    throw new IOException("invalid " + ID + " device row");
                }
                Device d = new Device(kind, cell, building);
                d.on = on == 1;
                d.condition = condition;
                switch (kind) {
                    case GENERATOR -> {
                        d.fuel = a;
                        if (a < 0 || a > UrbanUtilities.TANK_SECONDS || b != 0f || d.on && !d.running()
                                || !powered.add(building)) {
                            throw new IOException("invalid " + ID + " generator row");
                        }
                    }
                    case LAMP -> {
                        if (condition != UrbanUtilities.SOUND || a != 0f || b != 0f) {
                            throw new IOException("invalid " + ID + " lamp row");
                        }
                    }
                    case PURIFIER -> {
                        d.untreated = a;
                        d.clean = b;
                        if (a < 0 || a > UrbanUtilities.PURIFIER_LITRES || b < 0 || b > UrbanUtilities.PURIFIER_LITRES) {
                            throw new IOException("invalid " + ID + " purifier row");
                        }
                    }
                }
                devices.add(d);
            }
            if (in.available() != 0) { throw new IOException("trailing " + ID + " bytes"); }
        }
        if (apply) { g.world.urban.utilities.restore(g.world, drawn, devices); }
    }

    private static boolean finite(float... values) {
        for (float v : values) {
            if (!Float.isFinite(v)) { return false; }
        }
        return true;
    }

    /** A generator-18 save without the section would bring emptied pipes back full: reject it. */
    static void finishLoad(Game g) throws IOException {
        if (manages(g) && !g.world.urban.utilities.sectionRestored()) {
            throw new IOException("missing required " + ID);
        }
    }
}
