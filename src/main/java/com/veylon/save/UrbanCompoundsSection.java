package com.veylon.save;

import com.veylon.Game;
import com.veylon.settlement.CompoundAccess;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.world.urban.SurvivorSites;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * urban.compounds v1 (urban milestone 17): what a compound remembers about the player beside the
 * people {@code urban.sites} keeps. Required from generator 16 on (every
 * later urban generator has strongholds), rejected in any earlier world.
 *
 * <p>Header: int version (1), int count (at most {@value UrbanSiteDirector#MAX_SITES}). Then, in
 * ascending id order, one {@value #ROW}-byte row for every registered stronghold and raider yard:
 * long site id, byte kind code, double visitor's pass until, double wanted until, double last seen
 * inside, int stores missing unseen, double hunt cooldown until, byte flags (1: a stronghold was
 * told this raider yard is taken). Times are game minutes, -1 for none.
 *
 * <p>The rows name exactly the compounds {@code urban.sites} registered, so it is read after that
 * section. A stronghold has no hunt and no reward; a raider yard has no pass, wanted status,
 * sighting or missing stores; a pass ends by the next dusk and a wanted status within
 * {@value CompoundAccess#WANTED_HOURS} hours; a sighting is never in the future; only a taken yard
 * can have been reported. Anything else fails the whole load. Whether a raider yard is taken is not
 * stored at all: it is taken exactly when none of its people is alive.
 */
final class UrbanCompoundsSection {
    static final String ID = "urban.compounds";
    static final int VERSION = 1;
    static final int ROW = 46;
    private static final int REWARDED = 1;

    private UrbanCompoundsSection() {
    }

    static boolean manages(Game g) {
        return g.world.urban != null && g.world.urban.hasStrongholds();
    }

    static byte[] write(Game g) throws IOException {
        List<UrbanSite> compounds = compounds(g);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(compounds.size());
            for (UrbanSite s : compounds) {
                out.writeLong(s.id);
                out.writeByte(s.plan.kind().code);
                out.writeDouble(s.passUntil);
                out.writeDouble(s.wantedUntil);
                out.writeDouble(s.lastSeenInside);
                out.writeInt(s.missing);
                out.writeDouble(s.huntCooldownUntil);
                out.writeByte(s.rewarded ? REWARDED : 0);
            }
        }
        parse(g, bytes.toByteArray(), false);
        return bytes.toByteArray();
    }

    static void read(byte[] bytes, Game g) throws IOException {
        if (!manages(g)) { throw new IOException("unexpected " + ID); }
        parse(g, bytes, true);
        g.settlementManager.urbanSites.compoundsRestored = true;
    }

    /** Checks every row against the registered compounds; applies the rows only when {@code apply}. */
    private static void parse(Game g, byte[] bytes, boolean apply) throws IOException {
        List<UrbanSite> compounds = compounds(g);
        double now = g.time.totalMinutes;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) { throw new IOException("unsupported " + ID + " version"); }
            int count = SaveSystem.readCount(in, "compounds", UrbanSiteDirector.MAX_SITES);
            if (count != compounds.size()) { throw new IOException(ID + " does not name the registered compounds"); }
            for (UrbanSite s : compounds) {
                long id = in.readLong();
                SurvivorSites.Kind kind = SurvivorSites.Kind.fromCode(in.readUnsignedByte());
                if (id != s.id || kind != s.plan.kind()) { throw new IOException(ID + " names another compound"); }
                double pass = minute(in.readDouble()), wanted = minute(in.readDouble()), seen = minute(in.readDouble());
                int missing = in.readInt();
                double cooldown = minute(in.readDouble());
                int flags = in.readUnsignedByte();
                if (missing < 0 || missing > 9_999 || (flags & ~REWARDED) != 0 || seen > now
                        || pass > now + 24 * 60 || wanted > now + CompoundAccess.WANTED_HOURS * 60
                        || cooldown > now + CompoundAccess.HUNT_COOLDOWN_HOURS * 60) {
                    throw new IOException("invalid " + ID + " row");
                }
                boolean taken = s.raiders() && noneAlive(s);
                if (s.stronghold() && (cooldown != -1 || flags != 0)
                        || s.raiders() && (pass != -1 || wanted != -1 || seen != -1 || missing != 0
                        || (flags & REWARDED) != 0 && !taken)) {
                    throw new IOException(ID + " row contradicts its compound");
                }
                if (!apply) { continue; }
                s.passUntil = pass;
                s.wantedUntil = wanted;
                s.lastSeenInside = seen;
                s.missing = missing;
                s.huntCooldownUntil = cooldown;
                s.rewarded = (flags & REWARDED) != 0;
                s.cleared = taken;
            }
            if (in.available() != 0) { throw new IOException("trailing " + ID + " bytes"); }
        }
    }

    private static boolean noneAlive(UrbanSite s) {
        for (var r : s.residents) {
            if (r.alive) { return false; }
        }
        return true;
    }

    /** A saved time: -1 for none, else a finite, non-negative game minute. */
    private static double minute(double value) throws IOException {
        if (value == -1) { return -1; }
        if (!Double.isFinite(value) || value < 0) { throw new IOException("invalid " + ID + " time"); }
        return value;
    }

    private static List<UrbanSite> compounds(Game g) {
        List<UrbanSite> out = new ArrayList<>();
        for (UrbanSite s : UrbanSiteDirector.sites(g.world)) {
            if (s.compound()) { out.add(s); }
        }
        return out;
    }

    /** A save of generator 16 or later without the section would forget every pass, debt and hunt: reject it. */
    static void finishLoad(Game g) throws IOException {
        if (manages(g) && !g.settlementManager.urbanSites.compoundsRestored) {
            throw new IOException("missing required " + ID);
        }
    }
}
