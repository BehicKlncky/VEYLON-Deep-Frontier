package com.veylon.save;

import com.veylon.Game;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * urban.missions v1 (urban milestone 20): what a layout-sixteen city's survivor has learned and done
 * toward restoring its communications. Required for generator 19, rejected in any other world.
 *
 * <p>Payload: int version (1); a byte of flags (bit 0 the relay rumoured, bit 1 the relay found, bit
 * 2 a radio built, bit 3 the objective complete; no other bit); long the site that spoke of the
 * relay, or {@link UrbanIds#NONE}; int the parts recovered, as {@link CommsComponents.Kind} bits; int
 * the parts fitted to the relay ({@link UrbanMissions#RELAY_ANTENNA}, {@link UrbanMissions#RELAY_CELLS});
 * int who gave the relay's codes ({@link UrbanMissions#CODES_NONE}, {@link UrbanMissions#CODES_STRONGHOLD}
 * or {@link UrbanMissions#CODES_REFUGE}); long their site; float the seconds broadcast; double the game
 * minute the objective was completed, or -1; int the count of mission records.
 *
 * <p>A site must be a planned camp, stronghold or hidden refuge, and the codes a stronghold's or a
 * refuge's as their source says; the relay can be found only in a world that has one, and a part
 * fitted only to a relay that was found. Seconds broadcast lie between none and
 * {@value UrbanMissions#SCAN_SECONDS}, and any at all need a repaired relay and a radio; a completed
 * objective needs both and at least {@value UrbanMissions#BROADCAST_SECONDS} seconds, with a completion
 * minute, and an unfinished one has -1. Until missions are given the mission count must be zero.
 * Anything else, or trailing bytes, fails the whole load and leaves the live world alone; without the
 * section a generator-19 save is rejected rather than forgetting what its survivor learned or did.
 */
final class UrbanMissionsSection {
    static final String ID = "urban.missions";
    static final int VERSION = 1;
    private static final int RUMOURED = 1, FOUND = 2, RADIO = 4, COMPLETE = 8;

    private UrbanMissionsSection() {
    }

    static boolean manages(Game g) {
        return g.world.urban != null && g.world.urban.hasMissions();
    }

    static byte[] write(Game g) throws IOException {
        UrbanMissions.Comms c = g.world.urban.missions.comms;
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeByte((c.rumoured ? RUMOURED : 0) | (c.relayFound ? FOUND : 0) | (c.radioBuilt ? RADIO : 0)
                    | (c.complete ? COMPLETE : 0));
            out.writeLong(c.rumourFrom);
            out.writeInt(c.recovered);
            out.writeInt(c.installed);
            out.writeInt(c.codes);
            out.writeLong(c.codesFrom);
            out.writeFloat(c.broadcast);
            out.writeDouble(c.completeMinute);
            out.writeInt(0);
        }
        parse(g, bytes.toByteArray(), false);
        return bytes.toByteArray();
    }

    static void read(byte[] bytes, Game g) throws IOException {
        if (!manages(g)) { throw new IOException("unexpected " + ID); }
        parse(g, bytes, true);
    }

    /** Checks every field; restores them only when {@code apply}. */
    private static void parse(Game g, byte[] bytes, boolean apply) throws IOException {
        UrbanMissions.Comms c = new UrbanMissions.Comms();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) { throw new IOException("unsupported " + ID + " version"); }
            int flags = in.readUnsignedByte();
            c.rumoured = (flags & RUMOURED) != 0;
            c.relayFound = (flags & FOUND) != 0;
            c.radioBuilt = (flags & RADIO) != 0;
            c.complete = (flags & COMPLETE) != 0;
            c.rumourFrom = in.readLong();
            c.recovered = in.readInt();
            c.installed = in.readInt();
            c.codes = in.readInt();
            c.codesFrom = in.readLong();
            c.broadcast = in.readFloat();
            c.completeMinute = in.readDouble();
            int missions = SaveSystem.readCount(in, "mission records", 0);
            if ((flags & ~(RUMOURED | FOUND | RADIO | COMPLETE)) != 0 || missions != 0) {
                throw new IOException("invalid " + ID + " objective");
            }
            if (c.installed < 0 || (c.installed & ~UrbanMissions.RELAY_REPAIRED) != 0 || c.installed != 0 && !c.relayFound) {
                throw new IOException("invalid " + ID + " relay parts");
            }
            if (!Float.isFinite(c.broadcast) || c.broadcast < 0 || c.broadcast > UrbanMissions.SCAN_SECONDS
                    || c.broadcast > 0 && !(c.relayRepaired() && c.radioBuilt)) {
                throw new IOException("invalid " + ID + " broadcast");
            }
            boolean done = c.relayRepaired() && c.radioBuilt && c.broadcast >= UrbanMissions.BROADCAST_SECONDS
                    && Double.isFinite(c.completeMinute) && c.completeMinute >= 0;
            if (c.complete ? !done : c.completeMinute != -1) {
                throw new IOException("invalid " + ID + " completion");
            }
            SurvivorSites.Kind teller = c.rumourFrom == UrbanIds.NONE ? null : siteKind(g, c.rumourFrom);
            if (c.rumourFrom != UrbanIds.NONE && (!c.rumoured || teller == null || teller == SurvivorSites.Kind.RAIDERS)) {
                throw new IOException(ID + " rumour names no camp, stronghold or refuge");
            }
            if (c.relayFound && (g.world.urban.relay() == null || !c.rumoured)) {
                throw new IOException(ID + " found a relay this world has not");
            }
            if (c.recovered < 0 || (c.recovered & ~CommsComponents.ALL) != 0) {
                throw new IOException("invalid " + ID + " parts");
            }
            boolean codesOk = switch (c.codes) {
                case UrbanMissions.CODES_NONE -> c.codesFrom == UrbanIds.NONE;
                case UrbanMissions.CODES_STRONGHOLD -> siteKind(g, c.codesFrom) == SurvivorSites.Kind.STRONGHOLD;
                case UrbanMissions.CODES_REFUGE -> siteKind(g, c.codesFrom) == SurvivorSites.Kind.REFUGE;
                default -> false;
            };
            if (!codesOk || c.codes != UrbanMissions.CODES_NONE && !c.rumoured) {
                throw new IOException("invalid " + ID + " codes");
            }
            if (in.available() != 0) { throw new IOException("trailing " + ID + " bytes"); }
        }
        if (apply) { g.world.urban.missions.restore(c); }
    }

    /** The kind of planned site an id names, or null when it names none. Plans only. */
    private static SurvivorSites.Kind siteKind(Game g, long id) {
        if (!UrbanIds.isValid(id) || UrbanIds.kind(id) != UrbanIds.Kind.BUILDING) { return null; }
        SurvivorSites.Site site = SurvivorSites.owner(g.world.urban, id);
        return site != null && site.id() == id ? site.kind() : null;
    }

    /** A generator-19 save without the section would forget what its survivor learned: reject it. */
    static void finishLoad(Game g) throws IOException {
        if (manages(g) && !g.world.urban.missions.sectionRestored()) {
            throw new IOException("missing required " + ID);
        }
    }
}
