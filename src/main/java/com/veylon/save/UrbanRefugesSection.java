package com.veylon.save;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.UrbanStarterSite;

import java.io.*;

/** Starter-only urban.refuges v1. A later claim catalogue must extend this bounded contract. */
final class UrbanRefugesSection {
    static final String ID = "urban.refuges";
    private UrbanRefugesSection() { }

    static byte[] write(Game game) throws IOException {
        var urban = game.world.urban;
        validate(game, urban.refugeBuilding, urban.refugeAnchor);
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1);
            out.writeBoolean(urban.refugeAnchor != null);
            if (urban.refugeAnchor != null) {
                out.writeLong(urban.refugeBuilding);
                SaveSystem.writeVec(out, urban.refugeAnchor);
            }
        }
        return bytes.toByteArray();
    }

    static void read(byte[] bytes, Game game) throws IOException {
        var urban = game.world.urban;
        if (urban == null || !urban.hasStarter()) { throw new IOException("unexpected " + ID); }
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1) { throw new IOException("unsupported " + ID + " version"); }
            int present = in.readUnsignedByte();
            if (present > 1) { throw new IOException("invalid refuge presence"); }
            long building = present == 1 ? in.readLong() : 0;
            Vec3i anchor = present == 1 ? SaveSystem.readVec(in) : null;
            if (in.available() != 0) { throw new IOException("trailing " + ID + " bytes"); }
            validate(game, building, anchor);
            urban.refugeBuilding = building; urban.refugeAnchor = anchor;
            urban.refugeSectionRestored = true;
        }
    }

    private static void validate(Game game, long building, Vec3i anchor) throws IOException {
        if (anchor == null) {
            if (building != 0) { throw new IOException("refuge identity without anchor"); }
            return;
        }
        var plan = UrbanStarterSite.of(game.world.urban).refuge();
        if (building != plan.id() || anchor.y() != 41 || !plan.parcel().contains(anchor.x(), anchor.z())
                || plan.voxels().getBlock(anchor.x(), 41, anchor.z()).solid
                || plan.voxels().getBlock(anchor.x(), 42, anchor.z()).solid
                || !plan.voxels().getBlock(anchor.x(), 40, anchor.z()).solid) {
            throw new IOException("invalid starter refuge identity or ground-floor anchor");
        }
    }

    static void finishLoad(Game game) throws IOException {
        if (game.world.urban != null && game.world.urban.hasStarter()
                && !game.world.urban.refugeSectionRestored) { throw new IOException("missing required " + ID); }
    }
}
