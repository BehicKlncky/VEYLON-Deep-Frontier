package com.veylon.save;

import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanRefuges;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.UrbanWorldState;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

/**
 * urban.refuges: the starter refuge's identity and ground-floor respawn anchor (version 1, milestone
 * 08), and from generator 17 the claimed refuges as well (version 2, milestone 18).
 *
 * <p>Version 1, for generators 9 to 16: int version, a presence byte and, when present, the
 * starter refuge's building id and its integer ground-floor anchor. Version 2, required for
 * generator 17 and accepted nowhere else: the same, then an int count of claims (at most
 * {@value UrbanRefuges#MAX_CLAIMS}), each a long floor id, a presence byte with, when present, its
 * sleeping cell, and the double game minute it was claimed; then the long floor id of home,
 * {@link UrbanIds#NONE} only when there is no claim. A world's generator decides the one version
 * it accepts.
 *
 * <p>A claim must name a planned storey of a planned building by its stable id, never a roof or a
 * station, with no claim twice; its sleeping cell must stand on that storey inside the building's
 * parcel; it cannot have been claimed in the future; and home must be one of the claims. Anything
 * else, a wrong version, an unknown presence byte or trailing bytes fails the whole load.
 */
final class UrbanRefugesSection {
    static final String ID = "urban.refuges";
    static final int STARTER_VERSION = 1;
    static final int CLAIMS_VERSION = 2;

    private UrbanRefugesSection() { }

    private static int version(UrbanWorldState urban) {
        return urban.hasRefuges() ? CLAIMS_VERSION : STARTER_VERSION;
    }

    static byte[] write(Game game) throws IOException {
        var urban = game.world.urban;
        validate(game, urban.refugeBuilding, urban.refugeAnchor);
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(version(urban));
            out.writeBoolean(urban.refugeAnchor != null);
            if (urban.refugeAnchor != null) {
                out.writeLong(urban.refugeBuilding);
                SaveSystem.writeVec(out, urban.refugeAnchor);
            }
            if (urban.hasRefuges()) {
                List<UrbanRefuges.Claim> claims = urban.refuges.claims();
                out.writeInt(claims.size());
                for (UrbanRefuges.Claim c : claims) {
                    out.writeLong(c.floor);
                    out.writeBoolean(c.sleep != null);
                    if (c.sleep != null) { SaveSystem.writeVec(out, c.sleep); }
                    out.writeDouble(c.claimedMinute);
                }
                out.writeLong(urban.refuges.homeFloor());
            }
        }
        parse(bytes.toByteArray(), game, false);
        return bytes.toByteArray();
    }

    static void read(byte[] bytes, Game game) throws IOException {
        var urban = game.world.urban;
        if (urban == null || !urban.hasStarter()) { throw new IOException("unexpected " + ID); }
        parse(bytes, game, true);
    }

    private static void parse(byte[] bytes, Game game, boolean apply) throws IOException {
        var urban = game.world.urban;
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            int version = in.readInt();
            if (version != version(urban)) { throw new IOException("unsupported " + ID + " version " + version); }
            int present = in.readUnsignedByte();
            if (present > 1) { throw new IOException("invalid refuge presence"); }
            long building = present == 1 ? in.readLong() : 0;
            Vec3i anchor = present == 1 ? SaveSystem.readVec(in) : null;
            validate(game, building, anchor);
            List<UrbanRefuges.Claim> claims = new ArrayList<>();
            long home = UrbanIds.NONE;
            if (version == CLAIMS_VERSION) {
                int count = SaveSystem.readCount(in, "claimed refuges", UrbanRefuges.MAX_CLAIMS);
                for (int i = 0; i < count; i++) {
                    long floor = in.readLong();
                    int sleeps = in.readUnsignedByte();
                    if (sleeps > 1) { throw new IOException("invalid refuge bed presence"); }
                    Vec3i sleep = sleeps == 1 ? SaveSystem.readVec(in) : null;
                    double minute = in.readDouble();
                    claims.add(claim(game, claims, floor, sleep, minute));
                }
                home = in.readLong();
                boolean homeIsClaim = false;
                for (UrbanRefuges.Claim c : claims) { homeIsClaim |= c.floor == home; }
                if (claims.isEmpty() ? home != UrbanIds.NONE : !homeIsClaim) {
                    throw new IOException("refuge home is not one of the claims");
                }
            }
            if (in.available() != 0) { throw new IOException("trailing " + ID + " bytes"); }
            if (!apply) { return; }
            urban.refugeBuilding = building; urban.refugeAnchor = anchor;
            urban.refugeSectionRestored = true;
            if (version == CLAIMS_VERSION) { urban.refuges.restore(claims, home); }
        }
    }

    /** One claim, checked against the plans: a real storey, its own sleeping cell, a past minute. */
    private static UrbanRefuges.Claim claim(Game game, List<UrbanRefuges.Claim> earlier, long floor, Vec3i sleep,
                                            double minute) throws IOException {
        if (!UrbanIds.isValid(floor) || UrbanIds.kind(floor) != UrbanIds.Kind.FLOOR) {
            throw new IOException("a refuge claim names no storey");
        }
        for (UrbanRefuges.Claim c : earlier) {
            if (c.floor == floor) { throw new IOException("a storey claimed twice"); }
        }
        BuildingPlan plan = game.world.urban.buildingById(UrbanIds.parent(floor));
        BuildingPlan.Floor storey = null;
        if (plan != null && plan.family() != BuildingPlan.Family.TRANSIT) {
            for (BuildingPlan.Floor f : plan.floors()) {
                if (f.id() == floor && f.index() < plan.storeys()) { storey = f; }
            }
        }
        if (storey == null) { throw new IOException("a refuge claim names no planned storey"); }
        if (sleep != null && (sleep.y() != storey.slabY() + 1 || !plan.parcel().contains(sleep.x(), sleep.z()))) {
            throw new IOException("a refuge's bed is not on its storey");
        }
        if (!Double.isFinite(minute) || minute < 0 || minute > game.time.totalMinutes) {
            throw new IOException("invalid refuge claim time");
        }
        return new UrbanRefuges.Claim(floor, sleep, minute);
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
