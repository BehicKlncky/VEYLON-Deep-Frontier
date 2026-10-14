package com.veylon.save;

import com.veylon.Game;
import com.veylon.combat.ProjectileLethality;
import com.veylon.entity.Npc;
import com.veylon.entity.UrbanEncounterDirector;
import com.veylon.entity.UrbanEncounterDirector.Resident;
import com.veylon.world.urban.EncounterAnchors;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** urban.population v1: stable species/anchor identity, injury snapshots and permanent deaths. */
final class UrbanPopulationSection {
    static final String ID = "urban.population";
    private static final String SPECIES = "veilbound";
    private static final float HORIZONTAL = 33_000_000;
    /** A living body stays below the lethal torso wound count; the killing torso hit adds at most one hit more. */
    static final int MAX_LIVING_WOUNDS = ProjectileLethality.LETHAL_TORSO_WOUNDS - 1;
    static final int MAX_DEAD_WOUNDS = MAX_LIVING_WOUNDS
            + Math.max(ProjectileLethality.BULLET_TORSO_WOUNDS, ProjectileLethality.ARROW_TORSO_WOUNDS);
    private UrbanPopulationSection() { }

    static byte[] write(Game g) throws IOException {
        var director = g.entities.encounters;
        director.sync(g);
        if (director.size() > UrbanEncounterDirector.MAX_RECORDS) { throw new IOException("too many encounters"); }
        var bytes = new ByteArrayOutputStream();
        Set<Integer> indices = new HashSet<>();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(1); out.writeInt(1); // codec and frozen encounter catalogue
            out.writeInt(director.size());
            var ordered = director.residents().stream().sorted(java.util.Comparator.comparingLong(r -> r.id)).toList();
            for (Resident r : ordered) {
                int index = r.live == null ? -1 : g.entities.npcs.indexOf(r.live);
                validate(g, r, index, indices);
                out.writeLong(r.id); out.writeUTF(SPECIES);
                out.writeFloat(r.health); out.writeFloat(r.x); out.writeFloat(r.y); out.writeFloat(r.z);
                out.writeFloat(r.yaw); out.writeInt(r.torsoWounds); out.writeInt(index);
            }
        }
        validateBindings(g, indices);
        return bytes.toByteArray();
    }

    static void read(byte[] bytes, Game g) throws IOException {
        if (!UrbanEncounterDirector.manages(g.world)) { throw new IOException("unexpected " + ID); }
        List<Resident> rows = new ArrayList<>();
        Set<Long> ids = new HashSet<>(); Set<Integer> indices = new HashSet<>();
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            if (in.readInt() != 1 || in.readInt() != 1) { throw new IOException("unsupported population version/catalogue"); }
            int count = SaveSystem.readCount(in, "urban encounters", UrbanEncounterDirector.MAX_RECORDS);
            // Every v1 row has exactly 47 bytes, including the stable species string.
            if (in.available() != count * 47) { throw new IOException("invalid population payload length"); }
            for (int i = 0; i < count; i++) {
                long id = in.readLong();
                if (!ids.add(id) || !SPECIES.equals(in.readUTF())) { throw new IOException("duplicate encounter or unknown species"); }
                Resident r = new Resident(id, in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                        in.readFloat(), in.readInt());
                int index = in.readInt();
                validate(g, r, index, indices);
                if (index >= 0) { r.live = g.entities.npcs.get(index); }
                rows.add(r);
            }
        }
        validateBindings(g, indices);
        g.entities.encounters.restore(rows);
        for (Resident r : rows) {
            if (r.live != null) {
                r.live.encounterId = r.id; r.live.torsoWounds = r.torsoWounds;
                r.live.maxHealth = r.live.archetype.maxHealth;
            }
        }
    }

    private static void validate(Game g, Resident r, int index, Set<Integer> indices) throws IOException {
        try { EncounterAnchors.resolve(g.world, r.id); }
        catch (IllegalArgumentException bad) { throw new IOException("invalid encounter identity", bad); }
        if (!Float.isFinite(r.health) || r.health < 0 || r.health > 28
                || !Float.isFinite(r.x) || Math.abs(r.x) > HORIZONTAL
                || !Float.isFinite(r.z) || Math.abs(r.z) > HORIZONTAL
                || !Float.isFinite(r.y) || r.y < 0 || r.y > 94.2f
                || !Float.isFinite(r.yaw) || Math.abs(r.yaw) > 360
                || r.torsoWounds < 0 || r.torsoWounds > (r.dead() ? MAX_DEAD_WOUNDS : MAX_LIVING_WOUNDS)
                || index < -1 || index >= g.entities.npcs.size() || r.dead() && index != -1) {
            throw new IOException("invalid encounter snapshot");
        }
        if (index >= 0) {
            Npc n = g.entities.npcs.get(index);
            if (!indices.add(index) || !n.infected() || n.dead || n.faction != null || n.raider
                    || n.isTrader || n.warParty || n.residentIndex != -1 || n.settlementId != 0
                    || n.health != r.health || n.pos.x != r.x || n.pos.y != r.y || n.pos.z != r.z
                    || n.yaw != r.yaw) { throw new IOException("invalid active encounter binding"); }
        }
    }

    private static void validateBindings(Game g, Set<Integer> indices) throws IOException {
        int infected = 0;
        for (int i = 0; i < g.entities.npcs.size(); i++) {
            if (g.entities.npcs.get(i).infected()) {
                infected++;
                if (!indices.contains(i)) { throw new IOException("unbound infected body"); }
            }
        }
        if (infected > UrbanEncounterDirector.MAX_ACTIVE) { throw new IOException("too many active infected"); }
    }

    static void finishLoad(Game g) throws IOException {
        if (UrbanEncounterDirector.manages(g.world) && !g.entities.encounters.restored) {
            throw new IOException("missing required " + ID);
        }
    }
}
