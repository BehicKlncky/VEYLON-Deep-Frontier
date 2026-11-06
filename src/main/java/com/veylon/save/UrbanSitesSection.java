package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementManager;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * urban.sites v1 (urban milestone 16): the people of every survivor camp and hidden refuge the
 * player has come near, and how they stand with the player. Required from generator 15 on,
 * rejected in any earlier world. From generator 16 (milestone 17) its rows also hold strongholds and
 * raider yards (kind codes 3 and 4) in the same layout: a compound records its own discovery like
 * a camp, never relocates or leaves its stash, and a raider yard is never betrayed, never treats
 * the player and never trusts above {@link UrbanSite#RAIDER_TRUST}.
 *
 * <p>Header: int version (1), int count (at most {@value UrbanSiteDirector#MAX_SITES}). Then, in
 * ascending id order, one row per registered site: long building id, byte kind code, byte
 * alignment (0 neutral, 1 friendly, 2 hostile), byte flags (1 discovered camp or compound, 2 betrayed,
 * 4 stash left to the player), float trust, float fear, float alarm, double needs clock (game
 * minutes, -1 before the people were met), byte relocation code, long camp id, double arrival
 * minute, int last treatment day, int items owed, byte residents (the plan's count), and per
 * resident in slot order: byte alive, float health, float hunger, float thirst, int index into the
 * core NPC list or -1.
 *
 * <p>Identity is the plan's: a row's id must name a planned site of this world and its kind, and
 * every person is the plan's slot, so no row can create or reorder a person. A refuge's discovery
 * is the {@code urban.discovery} ledger's, read beside it. Anything malformed, out of range, bound
 * to the wrong body, leaving a resident body unbound, or over the active resident cap fails the
 * whole load; a death is a tombstone with no body.
 */
final class UrbanSitesSection {
    static final String ID = "urban.sites";
    static final int VERSION = 1;
    private static final int DISCOVERED = 1, BETRAYED = 2, RELEASED = 4;

    private UrbanSitesSection() {
    }

    static byte[] write(Game g) throws IOException {
        List<UrbanSite> sites = UrbanSiteDirector.sites(g.world);
        if (sites.size() > UrbanSiteDirector.MAX_SITES) { throw new IOException("too many survivor sites"); }
        Set<Integer> bound = new HashSet<>();
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(VERSION);
            out.writeInt(sites.size());
            for (UrbanSite s : sites) {
                out.writeLong(s.id);
                out.writeByte(s.plan.kind().code);
                out.writeByte(alignmentCode(s.alignment));
                out.writeByte((!s.refuge() && s.discovered ? DISCOVERED : 0) | (s.betrayed ? BETRAYED : 0)
                        | (s.stashReleased ? RELEASED : 0));
                out.writeFloat(s.localReputation);
                out.writeFloat(s.fear);
                out.writeFloat(s.alertLevel);
                out.writeDouble(s.simMinute);
                out.writeByte(s.relocation.code);
                out.writeLong(s.relocatedTo);
                out.writeDouble(s.arriveMinute);
                out.writeInt(s.lastTreatmentDay);
                out.writeInt(s.owed);
                out.writeByte(s.residents.size());
                for (Settlement.Resident base : s.residents) {
                    UrbanSite.Resident r = (UrbanSite.Resident) base;
                    Npc live = r.live != null && !r.live.dead && r.alive ? r.live : null;
                    int index = live == null ? -1 : g.entities.npcs.indexOf(live);
                    if (index >= 0 && !bound.add(index)) { throw new IOException("a body bound to two residents"); }
                    out.writeBoolean(r.alive);
                    out.writeFloat(r.alive ? (index >= 0 ? live.health : r.health) : 0f);
                    out.writeFloat(r.hunger);
                    out.writeFloat(r.thirst);
                    out.writeInt(index);
                }
            }
        }
        // The written bytes must read back in this very world: the same checks a load makes.
        verify(g, bytes.toByteArray());
        return bytes.toByteArray();
    }

    /** Parses and checks without touching the world; the writer's own guard. */
    private static void verify(Game g, byte[] bytes) throws IOException {
        parse(g, bytes);
    }

    static void read(byte[] bytes, Game g) throws IOException {
        if (!UrbanSiteDirector.manages(g.world)) { throw new IOException("unexpected " + ID); }
        List<UrbanSite> sites = parse(g, bytes);
        for (UrbanSite s : sites) {
            if (s.refuge()) {
                s.discovered = g.world.urban.discovery.discovered(s.plan.room());
                // Needs start when people are met; a refuge is met only once its cellar is found.
                if (s.simMinute >= 0 && !s.discovered) { throw new IOException("survivor needs counted before discovery"); }
            }
        }
        for (UrbanSite s : sites) {
            g.world.settlements.put(s.id, s);
            for (Settlement.Resident r : s.residents) {
                if (r.live != null) { r.live.maxHealth = r.archetype.maxHealth; }
            }
        }
        g.settlementManager.urbanSites.restored = true;
    }

    /** Reads and validates every row into fresh records, binding live bodies; the world is not changed. */
    private static List<UrbanSite> parse(Game g, byte[] bytes) throws IOException {
        List<UrbanSite> sites = new ArrayList<>();
        Set<Integer> bound = new HashSet<>();
        double now = g.time.totalMinutes;
        int today = g.time.day();
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != VERSION) { throw new IOException("unsupported " + ID + " version"); }
            int count = SaveSystem.readCount(in, "survivor sites", UrbanSiteDirector.MAX_SITES);
            long previous = 0;
            for (int i = 0; i < count; i++) {
                long id = in.readLong();
                if (i > 0 && id <= previous) { throw new IOException("survivor sites out of order or repeated"); }
                previous = id;
                SurvivorSites.Site plan = SurvivorSites.resolve(g.world.urban, id);
                SurvivorSites.Kind kind = SurvivorSites.Kind.fromCode(in.readUnsignedByte());
                if (plan == null || kind != plan.kind()) { throw new IOException("no such planned survivor site"); }
                UrbanSite s = new UrbanSite(plan);
                int alignment = in.readUnsignedByte(), flags = in.readUnsignedByte();
                // A camp and a compound record their own discovery; only a refuge's shelf is ever left
                // to the player; raiders are never anyone's friends, so never betrayed.
                if (alignment > 2 || (flags & ~(DISCOVERED | BETRAYED | RELEASED)) != 0
                        || s.refuge() && (flags & DISCOVERED) != 0 || !s.refuge() && (flags & RELEASED) != 0
                        || s.raiders() && (flags & BETRAYED) != 0) {
                    throw new IOException("invalid survivor site alignment or flags");
                }
                s.discovered = (flags & DISCOVERED) != 0;
                s.betrayed = (flags & BETRAYED) != 0;
                s.stashReleased = (flags & RELEASED) != 0;
                s.localReputation = bounded(in.readFloat(), -100f, s.raiders() ? UrbanSite.RAIDER_TRUST : 100f, "trust");
                s.fear = bounded(in.readFloat(), 0f, 100f, "fear");
                s.alertLevel = bounded(in.readFloat(), 0f, 100f, "alarm");
                s.simMinute = in.readDouble();
                if (!Double.isFinite(s.simMinute) || s.simMinute != -1 && (s.simMinute < 0 || s.simMinute > now)) {
                    throw new IOException("invalid survivor needs clock");
                }
                UrbanSite.Relocation relocation = UrbanSite.Relocation.fromCode(in.readUnsignedByte());
                long camp = in.readLong();
                double arrive = in.readDouble();
                if (relocation == null || !s.refuge() && relocation != UrbanSite.Relocation.HOME
                        || relocation == UrbanSite.Relocation.HOME && (camp != UrbanIds.NONE || arrive != 0)
                        || relocation != UrbanSite.Relocation.HOME && !plannedCamp(g, camp)
                        || !Double.isFinite(arrive) || arrive < 0
                        || relocation != UrbanSite.Relocation.TRAVELLING && relocation != UrbanSite.Relocation.ARRIVED
                        && arrive != 0
                        || relocation != UrbanSite.Relocation.HOME && (s.simMinute < 0 || !s.stashReleased)) {
                    throw new IOException("invalid survivor relocation");
                }
                s.relocation = relocation;
                s.relocatedTo = camp;
                s.arriveMinute = arrive;
                s.lastTreatmentDay = in.readInt();
                s.owed = in.readInt();
                if (s.lastTreatmentDay < -1 || s.lastTreatmentDay > today
                        || (s.refuge() || s.raiders()) && s.lastTreatmentDay != -1 || s.owed < 0 || s.owed > 9_999) {
                    throw new IOException("invalid survivor treatment day or debt");
                }
                if (s.betrayed && s.localReputation > UrbanSite.BETRAYED_CAP || !s.refuge() && s.discovered != s.simMinute >= 0) {
                    throw new IOException("survivor trust or clock contradicts its state");
                }
                Settlement.Alignment saved = alignmentOf(alignment);
                s.settleAlignment();
                if (s.alignment != saved) { throw new IOException("survivor alignment contradicts its trust"); }
                int residents = in.readUnsignedByte();
                if (residents != plan.residents().size()) { throw new IOException("survivor head count mismatch"); }
                for (int slot = 0; slot < residents; slot++) {
                    readResident(g, in, s, s.resident(slot), bound);
                }
                sites.add(s);
            }
            if (in.available() != 0) { throw new IOException("trailing " + ID + " bytes"); }
        }
        for (int i = 0; i < g.entities.npcs.size(); i++) {
            Npc n = g.entities.npcs.get(i);
            if (n.settled() && SurvivorSites.resolve(g.world.urban, n.settlementId) != null && !bound.contains(i)) {
                throw new IOException("a survivor body no saved resident claims");
            }
        }
        if (bound.size() > SettlementManager.MAX_ACTIVE_RESIDENTS) { throw new IOException("too many active survivors"); }
        return sites;
    }

    private static void readResident(Game g, DataInputStream in, UrbanSite s, UrbanSite.Resident r, Set<Integer> bound)
            throws IOException {
        int alive = in.readUnsignedByte();
        float health = in.readFloat(), hunger = in.readFloat(), thirst = in.readFloat();
        int index = in.readInt();
        if (alive > 1 || !Float.isFinite(health) || !Float.isFinite(hunger) || !Float.isFinite(thirst)
                || hunger < 0 || hunger > 100 || thirst < 0 || thirst > 100
                || (alive == 1 ? health <= 0 || health > r.archetype.maxHealth : health != 0 || index != -1)
                || index < -1 || index >= g.entities.npcs.size()) {
            throw new IOException("invalid survivor resident");
        }
        r.alive = alive == 1;
        r.health = health;
        r.hunger = hunger;
        r.thirst = thirst;
        if (index < 0) { return; }
        Npc n = g.entities.npcs.get(index);
        if (!bound.add(index) || !s.atHome() || !n.settled() || n.settlementId != s.id || n.residentIndex != r.slot || n.archetype != r.archetype
                || !n.name.equals(r.name) || n.infected() || n.warParty || n.raider || n.faction != null
                || n.health != health) {
            throw new IOException("invalid survivor body binding");
        }
        r.live = n;
    }

    private static boolean plannedCamp(Game g, long id) {
        SurvivorSites.Site plan = SurvivorSites.resolve(g.world.urban, id);
        return plan != null && plan.kind() == SurvivorSites.Kind.CAMP;
    }

    private static float bounded(float value, float lo, float hi, String what) throws IOException {
        if (!Float.isFinite(value) || value < lo || value > hi) { throw new IOException("invalid survivor " + what); }
        return value;
    }

    private static int alignmentCode(Settlement.Alignment a) {
        return switch (a) {
            case NEUTRAL -> 0;
            case FRIENDLY -> 1;
            case HOSTILE -> 2;
        };
    }

    private static Settlement.Alignment alignmentOf(int code) {
        return switch (code) {
            case 1 -> Settlement.Alignment.FRIENDLY;
            case 2 -> Settlement.Alignment.HOSTILE;
            default -> Settlement.Alignment.NEUTRAL;
        };
    }

    /** A save of generator 15 or later without the section would resurrect nobody, but silently forget everyone: reject it. */
    static void finishLoad(Game g) throws IOException {
        if (UrbanSiteDirector.manages(g.world) && !g.settlementManager.urbanSites.restored) {
            throw new IOException("missing required " + ID);
        }
    }
}
