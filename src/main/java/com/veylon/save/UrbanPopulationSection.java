package com.veylon.save;

import com.veylon.Game;
import com.veylon.combat.ProjectileLethality;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.UrbanEncounterDirector;
import com.veylon.entity.UrbanEncounterDirector.Resident;
import com.veylon.world.World;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.UrbanSpecies;
import java.io.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * urban.population: stable species/anchor identity, injury snapshots and permanent deaths.
 *
 * <ul>
 *   <li><b>Version 1</b> (encounter catalogues one to four): Veilbound only, fixed 47-byte rows,
 *       each bound to a core NPC index or -1. Unchanged since milestone 09.</li>
 *   <li><b>Version 2</b> (catalogue five, urban milestone 15): the same row with the species'
 *       stable id ({@link UrbanSpecies}) in place of the fixed string, so rows vary in length,
 *       and a binding into the core NPC list for a humanoid species or the core creature list for
 *       a creature species. A creature's yaw lives only here; the frozen creature record has
 *       none.</li>
 * </ul>
 * Each generator freezes one catalogue and so one version; any other is rejected, never
 * reinterpreted (D-83). A row's species must be its planned anchor's.
 */
final class UrbanPopulationSection {
    static final String ID = "urban.population";
    private static final String SPECIES = "veilbound";
    private static final float HORIZONTAL = 33_000_000;
    /** A living body stays below the lethal torso wound count; the killing torso hit adds at most one hit more. */
    static final int MAX_LIVING_WOUNDS = ProjectileLethality.LETHAL_TORSO_WOUNDS - 1;
    static final int MAX_DEAD_WOUNDS = MAX_LIVING_WOUNDS
            + Math.max(ProjectileLethality.BULLET_TORSO_WOUNDS, ProjectileLethality.ARROW_TORSO_WOUNDS);
    private UrbanPopulationSection() { }

    /** The codec version a world's frozen catalogue writes: 2 for catalogue five, 1 before it. */
    static int codecFor(World world) {
        return EncounterAnchors.catalogue(world) == 5 ? 2 : 1;
    }

    static byte[] write(Game g) throws IOException {
        var director = g.entities.encounters;
        director.sync(g);
        if (director.size() > UrbanEncounterDirector.MAX_RECORDS) { throw new IOException("too many encounters"); }
        int codec = codecFor(g.world);
        var bytes = new ByteArrayOutputStream();
        Set<Integer> npcIndices = new HashSet<>(), creatureIndices = new HashSet<>();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(codec); out.writeInt(EncounterAnchors.catalogue(g.world)); // codec and frozen catalogue
            out.writeInt(director.size());
            var ordered = director.residents().stream().sorted(java.util.Comparator.comparingLong(r -> r.id)).toList();
            for (Resident r : ordered) {
                int index = r.live != null ? g.entities.npcs.indexOf(r.live)
                        : r.creature != null ? g.entities.creatures.indexOf(r.creature) : -1;
                validate(g, codec, r, index, npcIndices, creatureIndices);
                out.writeLong(r.id); out.writeUTF(codec == 1 ? SPECIES : r.species.id);
                out.writeFloat(r.health); out.writeFloat(r.x); out.writeFloat(r.y); out.writeFloat(r.z);
                out.writeFloat(r.yaw); out.writeInt(r.torsoWounds); out.writeInt(index);
            }
        }
        validateBindings(g, codec, npcIndices, creatureIndices);
        return bytes.toByteArray();
    }

    static void read(byte[] bytes, Game g) throws IOException {
        if (!UrbanEncounterDirector.manages(g.world)) { throw new IOException("unexpected " + ID); }
        int codec = codecFor(g.world);
        List<Resident> rows = new ArrayList<>();
        Set<Long> ids = new HashSet<>();
        Set<Integer> npcIndices = new HashSet<>(), creatureIndices = new HashSet<>();
        try (var in = new DataInputStream(new ByteArrayInputStream(bytes))) {
            // Each generator freezes one catalogue; any other is rejected, never reinterpreted (D-83).
            if (in.readInt() != codec || in.readInt() != EncounterAnchors.catalogue(g.world)) {
                throw new IOException("unsupported population version/catalogue");
            }
            int count = SaveSystem.readCount(in, "urban encounters", UrbanEncounterDirector.MAX_RECORDS);
            // Every v1 row has exactly 47 bytes, including the stable species string.
            if (codec == 1 && in.available() != count * 47) { throw new IOException("invalid population payload length"); }
            for (int i = 0; i < count; i++) {
                long id = in.readLong();
                String speciesId = in.readUTF();
                UrbanSpecies species = codec == 1 ? (SPECIES.equals(speciesId) ? UrbanSpecies.VEILBOUND : null)
                        : UrbanSpecies.byId(speciesId);
                if (!ids.add(id) || species == null) { throw new IOException("duplicate encounter or unknown species"); }
                Resident r = new Resident(id, species, in.readFloat(), in.readFloat(), in.readFloat(), in.readFloat(),
                        in.readFloat(), in.readInt());
                int index = in.readInt();
                validate(g, codec, r, index, npcIndices, creatureIndices);
                if (index >= 0) {
                    if (species.body == UrbanSpecies.Body.HUMANOID) { r.live = g.entities.npcs.get(index); }
                    else { r.creature = g.entities.creatures.get(index); }
                }
                rows.add(r);
            }
            if (in.available() != 0) { throw new IOException("trailing population bytes"); }
        }
        validateBindings(g, codec, npcIndices, creatureIndices);
        g.entities.encounters.restore(rows);
        for (Resident r : rows) {
            if (r.live != null) {
                r.live.encounterId = r.id; r.live.torsoWounds = r.torsoWounds;
                r.live.maxHealth = r.live.archetype.maxHealth;
            }
            if (r.creature != null) {
                Creature c = r.creature;
                c.encounterId = r.id;
                c.home = EncounterAnchors.resolve(g.world, r.id).habitat();
                c.yaw = r.yaw;
                c.lastHealth = c.health;
            }
        }
    }

    private static void validate(Game g, int codec, Resident r, int index, Set<Integer> npcIndices,
                                 Set<Integer> creatureIndices) throws IOException {
        EncounterAnchors.Anchor anchor;
        try { anchor = EncounterAnchors.resolve(g.world, r.id); }
        catch (IllegalArgumentException bad) { throw new IOException("invalid encounter identity", bad); }
        if (anchor.species() != r.species || codec == 1 && r.species != UrbanSpecies.VEILBOUND) {
            throw new IOException("encounter species does not match its planned anchor");
        }
        boolean humanoid = r.species.body == UrbanSpecies.Body.HUMANOID;
        int bodies = humanoid ? g.entities.npcs.size() : g.entities.creatures.size();
        if (!Float.isFinite(r.health) || r.health < 0 || r.health > UrbanEncounterDirector.maxHealth(r.species)
                || !Float.isFinite(r.x) || Math.abs(r.x) > HORIZONTAL
                || !Float.isFinite(r.z) || Math.abs(r.z) > HORIZONTAL
                || !Float.isFinite(r.y) || r.y < 0 || r.y > 94.2f
                || !Float.isFinite(r.yaw) || Math.abs(r.yaw) > 360
                || (humanoid ? r.torsoWounds < 0 || r.torsoWounds > (r.dead() ? MAX_DEAD_WOUNDS : MAX_LIVING_WOUNDS)
                : r.torsoWounds != 0)
                || index < -1 || index >= bodies || r.dead() && index != -1) {
            throw new IOException("invalid encounter snapshot");
        }
        if (index < 0) {
            return;
        }
        if (humanoid) {
            Npc n = g.entities.npcs.get(index);
            if (!npcIndices.add(index) || !n.infected() || n.dead || n.faction != null || n.raider
                    || n.archetype != UrbanEncounterDirector.archetypeOf(r.species)
                    || n.isTrader || n.warParty || n.residentIndex != -1 || n.settlementId != 0
                    || n.health != r.health || n.pos.x != r.x || n.pos.y != r.y || n.pos.z != r.z
                    || n.yaw != r.yaw) { throw new IOException("invalid active encounter binding"); }
        } else {
            Creature c = g.entities.creatures.get(index);
            // The frozen creature record has no yaw, so only health and position must agree.
            if (!creatureIndices.add(index) || c.dead || c.type != UrbanEncounterDirector.creatureOf(r.species)
                    || c.encounterId != 0 && c.encounterId != r.id
                    || c.health != r.health || c.pos.x != r.x || c.pos.y != r.y || c.pos.z != r.z) {
                throw new IOException("invalid active encounter binding");
            }
        }
    }

    private static void validateBindings(Game g, int codec, Set<Integer> npcIndices, Set<Integer> creatureIndices)
            throws IOException {
        int infected = 0;
        for (int i = 0; i < g.entities.npcs.size(); i++) {
            if (g.entities.npcs.get(i).infected()) {
                infected++;
                if (!npcIndices.contains(i)) { throw new IOException("unbound infected body"); }
            }
        }
        if (infected > UrbanEncounterDirector.MAX_ACTIVE) { throw new IOException("too many active infected"); }
        if (codec == 1) {
            return;
        }
        int aliens = 0, wildlife = 0;
        for (int i = 0; i < g.entities.creatures.size(); i++) {
            Creature c = g.entities.creatures.get(i);
            boolean bound = creatureIndices.contains(i);
            if (c.type.territorial()) {
                aliens++;
                if (!bound) { throw new IOException("unbound alien body"); }
            } else if (bound) {
                wildlife++;
            }
        }
        if (aliens > UrbanEncounterDirector.MAX_ACTIVE_ALIENS || wildlife > UrbanEncounterDirector.MAX_ACTIVE_WILDLIFE) {
            throw new IOException("too many active encounter creatures");
        }
    }

    static void finishLoad(Game g) throws IOException {
        if (UrbanEncounterDirector.manages(g.world) && !g.entities.encounters.restored) {
            throw new IOException("missing required " + ID);
        }
    }
}
