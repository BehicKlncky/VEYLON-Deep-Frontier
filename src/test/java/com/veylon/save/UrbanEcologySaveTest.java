package com.veylon.save;

import com.veylon.EcologyWorldArena;
import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanSpecies;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code urban.population} version 2 (encounter catalogue five, urban milestone 15): every
 * species keeps its identity, injuries, bindings and tombstones across repeated loads; a burnt
 * node keeps its Veilmaw dead; malformed data and the wrong version fail the whole load and leave
 * the live world alone; catalogues one to four keep version 1.
 */
class UrbanEcologySaveTest {
    @TempDir Path directory;

    @Test
    void everySpeciesKeepsItsInjuriesBindingsAndTombstonesAcrossRepeatedLoads() {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var breacherAnchor = EcologyWorldArena.first(g, UrbanSpecies.BREACHER);
        EcologyWorldArena.observeAway(g, breacherAnchor);
        Npc breacher = g.entities.encounters.tryActivate(g, breacherAnchor);
        breacher.hurt(5, true);
        breacher.torsoWounds = 1;
        var veilmaws = EcologyWorldArena.anchors(g, a -> a.species() == UrbanSpecies.VEILMAW);
        EcologyWorldArena.observeAway(g, veilmaws.get(0));
        Creature active = g.entities.encounters.tryActivateCreature(g, veilmaws.get(0));
        active.hurt(4, true);
        active.yaw = 123f;
        EcologyWorldArena.observeAway(g, veilmaws.get(1));
        Creature dormant = g.entities.encounters.tryActivateCreature(g, veilmaws.get(1));
        dormant.hurt(6, true);
        g.entities.creatures.remove(dormant);
        g.entities.depart(g, dormant);
        var hareAnchor = EcologyWorldArena.first(g, UrbanSpecies.MURKHARE);
        EcologyWorldArena.observeAway(g, hareAnchor);
        Creature hare = g.entities.encounters.tryActivateCreature(g, hareAnchor);
        hare.hurt(100, true); // killed and saved before the next tick: the save commits the death once
        var birdAnchor = EcologyWorldArena.first(g, UrbanSpecies.SKITTERWING);
        EcologyWorldArena.observeAway(g, birdAnchor);
        Creature bird = g.entities.encounters.tryActivateCreature(g, birdAnchor);
        assertNotNull(bird);
        Path save = directory.resolve("ecology.sav");
        assertTrue(SaveSystem.save(g, save));
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, save));
            var ledger = g.entities.encounters;
            var b = ledger.resident(breacherAnchor.id());
            assertEquals(UrbanSpecies.BREACHER, b.species);
            assertNotNull(b.live);
            assertEquals(NpcArchetype.BREACHER, b.live.archetype);
            assertEquals(39f, b.live.health);
            assertEquals(1, b.live.torsoWounds);
            var m = ledger.resident(veilmaws.get(0).id());
            assertNotNull(m.creature, "the active Veilmaw is bound to its core body");
            assertEquals(Creature.CreatureType.VEILMAW, m.creature.type);
            assertEquals(30f, m.creature.health);
            assertEquals(123f, m.creature.yaw, "a creature's yaw lives in the ledger");
            assertEquals(veilmaws.get(0).id(), m.creature.encounterId);
            assertEquals(veilmaws.get(0).habitat(), m.creature.home, "its territory is derived again from the plan");
            var d = ledger.resident(veilmaws.get(1).id());
            assertNull(d.creature);
            assertEquals(28f, d.health, "dormant, still hurt");
            assertTrue(ledger.resident(hareAnchor.id()).dead(), "the hunted hare stays dead");
            assertEquals(Creature.CreatureType.HARE, g.entities.carcasses.getFirst().type);
            assertNotNull(ledger.resident(birdAnchor.id()).creature);
            EcologyWorldArena.observeAway(g, hareAnchor);
            assertNull(ledger.tryActivateCreature(g, hareAnchor), "no resurrection after a load");
            assertTrue(SaveSystem.save(g, save));
        }
    }

    @Test
    void aBurntNodeKeepsItsVeilmawDeadWhetherItBurntWhileActiveOrBeforeItWasEverSeen() {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var veilmaws = EcologyWorldArena.anchors(g, a -> a.species() == UrbanSpecies.VEILMAW);
        var seen = veilmaws.get(0);
        EcologyWorldArena.observeAway(g, seen);
        Creature c = g.entities.encounters.tryActivateCreature(g, seen);
        var node = c.home.node();
        g.world.setBlock(node.x(), node.y(), node.z(), BlockType.ASH, true);
        for (int i = 0; i < 20 && !c.dead; i++) { g.entities.fastTick(g, .05f); }
        var unseen = veilmaws.get(1);
        var unseenNode = unseen.habitat().node();
        g.world.ensureChunks(unseenNode.x(), unseenNode.z(), 1, 1000);
        g.world.setBlock(unseenNode.x(), unseenNode.y(), unseenNode.z(), BlockType.AIR, true);
        Path save = directory.resolve("burnt.sav");
        assertTrue(SaveSystem.save(g, save));
        assertTrue(SaveSystem.load(g, save));
        g.world.ensureChunks(node.x(), node.z(), 1, 1000);
        assertSame(BlockType.ASH, g.world.getBlock(node.x(), node.y(), node.z()), "the burnt node is a saved edit");
        assertTrue(g.entities.encounters.resident(seen.id()).dead());
        EcologyWorldArena.observeAway(g, seen);
        assertNull(g.entities.encounters.tryActivateCreature(g, seen));
        EcologyWorldArena.observeAway(g, unseen);
        assertNull(g.entities.encounters.tryActivateCreature(g, unseen), "a node lost before its Veilmaw was ever seen");
        assertTrue(g.entities.encounters.resident(unseen.id()).dead());
        assertTrue(SaveSystem.save(g, save));
        assertTrue(SaveSystem.load(g, save));
        assertTrue(g.entities.encounters.resident(unseen.id()).dead(), "the migration is saved as a tombstone");
    }

    /** The byte offsets of each row of a version-2 payload. */
    private static List<Integer> rows(byte[] payload) {
        List<Integer> starts = new ArrayList<>();
        ByteBuffer in = ByteBuffer.wrap(payload);
        int count = in.getInt(8);
        int at = 12;
        for (int i = 0; i < count; i++) {
            starts.add(at);
            int utf = Short.toUnsignedInt(in.getShort(at + 8));
            at += 8 + 2 + utf + 5 * 4 + 4 + 4;
        }
        assertEquals(payload.length, at);
        return starts;
    }

    private static String species(byte[] payload, int row) {
        int utf = Short.toUnsignedInt(ByteBuffer.wrap(payload).getShort(row + 8));
        return new String(payload, row + 10, utf, StandardCharsets.UTF_8);
    }

    /** Offset of a row's first float (health), after its id and species. */
    private static int scalars(byte[] payload, int row) {
        return row + 10 + Short.toUnsignedInt(ByteBuffer.wrap(payload).getShort(row + 8));
    }

    @Test
    void malformedVersionTwoDataFailsTheWholeLoadAndKeepsTheLiveWorld() throws Exception {
        Game g = EcologyWorldArena.session(EcologyWorldArena.SEED, GameMode.SURVIVAL);
        var breacherAnchor = EcologyWorldArena.first(g, UrbanSpecies.BREACHER);
        EcologyWorldArena.observeAway(g, breacherAnchor);
        assertNotNull(g.entities.encounters.tryActivate(g, breacherAnchor));
        var veilmawAnchor = EcologyWorldArena.first(g, UrbanSpecies.VEILMAW);
        EcologyWorldArena.observeAway(g, veilmawAnchor);
        Creature mawBody = g.entities.encounters.tryActivateCreature(g, veilmawAnchor);
        assertNotNull(mawBody);
        // An unmanaged hare beside it: a body of the wrong species to bind the Veilmaw's row to.
        Creature stray = g.entities.spawnCreature(g.world, Creature.CreatureType.HARE, mawBody.pos.x + 3, mawBody.pos.y, mawBody.pos.z);
        Path valid = directory.resolve("valid.sav");
        assertTrue(SaveSystem.save(g, valid));
        byte[] save = Files.readAllBytes(valid), payload = UrbanPopulationSection.write(g);
        assertEquals(2, ByteBuffer.wrap(payload).getInt(0), "catalogue five writes version 2");
        assertEquals(5, ByteBuffer.wrap(payload).getInt(4));
        int maw = -1, ram = -1;
        for (int row : rows(payload)) {
            if (species(payload, row).equals("veilmaw")) { maw = row; }
            if (species(payload, row).equals("breacher")) { ram = row; }
        }
        assertTrue(maw > 0 && ram > 0);
        java.util.Map<String, byte[]> invalid = new java.util.LinkedHashMap<>();
        byte[] versionOne = payload.clone(); ByteBuffer.wrap(versionOne).putInt(0, 1); invalid.put("version one", versionOne);
        byte[] catalogueFour = payload.clone(); ByteBuffer.wrap(catalogueFour).putInt(4, 4); invalid.put("catalogue four", catalogueFour);
        byte[] unknown = payload.clone(); unknown[maw + 10 + 6] = 'x'; invalid.put("unknown species", unknown); // "veilmax"
        byte[] wrongSpecies = payload.clone(); // "breacher" and "murkhare" are both eight letters
        System.arraycopy("murkhare".getBytes(StandardCharsets.UTF_8), 0, wrongSpecies, ram + 10, 8); invalid.put("species unlike its plan", wrongSpecies);
        byte[] tooHealthy = payload.clone(); ByteBuffer.wrap(tooHealthy).putFloat(scalars(payload, maw), 35f); invalid.put("health above the species", tooHealthy);
        byte[] woundedCreature = payload.clone(); ByteBuffer.wrap(woundedCreature).putInt(scalars(payload, maw) + 20, 1);
        invalid.put("torso wounds on a creature", woundedCreature);
        byte[] unbound = payload.clone(); ByteBuffer.wrap(unbound).putInt(scalars(payload, maw) + 24, -1); invalid.put("an unbound Veilmaw", unbound);
        byte[] outOfRange = payload.clone(); ByteBuffer.wrap(outOfRange).putInt(scalars(payload, maw) + 24, 9999);
        invalid.put("binding out of range", outOfRange);
        byte[] wrongBody = payload.clone(); // the Veilmaw row bound to the stray hare
        ByteBuffer.wrap(wrongBody).putInt(scalars(payload, maw) + 24, g.entities.creatures.indexOf(stray));
        invalid.put("bound to another species", wrongBody);
        byte[] moved = payload.clone(); ByteBuffer.wrap(moved).putFloat(scalars(payload, maw) + 4, 3f); invalid.put("bound body moved", moved);
        invalid.put("trailing byte", Arrays.copyOf(payload, payload.length + 1));
        invalid.put("truncated", Arrays.copyOf(payload, payload.length - 3));
        var originalWorld = g.world;
        for (var bad : invalid.entrySet()) {
            Path broken = directory.resolve("broken.sav");
            Files.write(broken, CreativeSaveSections.replace(save, UrbanPopulationSection.ID, bad.getValue()));
            assertFalse(SaveSystem.load(g, broken), bad.getKey());
            assertSame(originalWorld, g.world);
            assertEquals(2, g.entities.encounters.size());
        }
        assertTrue(SaveSystem.load(g, valid));
    }

    @Test
    void earlierCataloguesKeepVersionOneAndRejectVersionTwo() throws Exception {
        Game g = new Game();
        g.newWorld(EcologyWorldArena.SEED, true, World.GEN_URBAN_UNDERGROUND, GameMode.SURVIVAL);
        Path save = directory.resolve("underground.sav");
        assertTrue(SaveSystem.save(g, save));
        byte[] payload = UrbanPopulationSection.write(g);
        assertEquals(1, ByteBuffer.wrap(payload).getInt(0));
        assertEquals(4, ByteBuffer.wrap(payload).getInt(4));
        var bytes = new ByteArrayOutputStream();
        try (var out = new DataOutputStream(bytes)) {
            out.writeInt(2); out.writeInt(4); out.writeInt(0);
        }
        Path broken = directory.resolve("v2-in-13.sav");
        Files.write(broken, CreativeSaveSections.replace(Files.readAllBytes(save), UrbanPopulationSection.ID, bytes.toByteArray()));
        var world = g.world;
        assertFalse(SaveSystem.load(g, broken));
        assertSame(world, g.world);
        assertTrue(SaveSystem.load(g, save));
    }

    @Test
    void aVersionOneRowNeverNamesANewSpecies() throws IOException {
        // Catalogue five never writes version 1, and version 1 knows only the Veilbound.
        assertNull(UrbanSpecies.byId("veilmax"));
        assertEquals(UrbanSpecies.VEILBOUND, UrbanSpecies.byId("veilbound"));
        for (UrbanSpecies s : UrbanSpecies.values()) {
            assertSame(s, UrbanSpecies.byId(s.id), "stable ids round-trip");
        }
    }
}
