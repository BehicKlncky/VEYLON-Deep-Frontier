package com.veylon.save;

import com.veylon.Game;
import com.veylon.UrbanEncounterArena;
import com.veylon.entity.GameMode;
import com.veylon.entity.UrbanEncounterDirector;
import com.veylon.world.World;
import com.veylon.world.urban.EncounterAnchors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.*;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

class UrbanPopulationSaveTest {
    @TempDir Path directory;

    @Test void activeDormantAndDefeatedResidentsSurviveRepeatedLoadsWithoutHealingOrResurrection() {
        Game g = UrbanEncounterArena.session(20260731);
        var dormant = UrbanEncounterArena.activate(g, false);
        dormant.hurt(6, true); dormant.torsoWounds = 1;
        g.entities.removeNpc(g, dormant);
        var active = UrbanEncounterArena.activate(g, true);
        active.hurt(7, true); active.torsoWounds = 1;
        var defeated = UrbanEncounterArena.activate(g, false);
        defeated.hurt(100, true);
        // Save between lethal combat and the next entity tick must still write the death/remains once.
        Path save = directory.resolve("population.sav"); assertTrue(SaveSystem.save(g, save));
        assertEquals(1, g.entities.corpses.size());
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, save));
            var ledger = g.entities.encounters;
            assertEquals(3, ledger.size());
            assertTrue(ledger.resident(defeated.encounterId).dead());
            assertEquals(1, g.entities.corpses.size());
            var a = ledger.resident(active.encounterId).live;
            assertNotNull(a); assertEquals(21, a.health); assertEquals(28, a.maxHealth);
            assertEquals(1, a.torsoWounds); assertFalse(a.settled()); assertNull(a.faction);
            var d = ledger.resident(dormant.encounterId);
            assertNull(d.live); assertEquals(22, d.health); assertEquals(1, d.torsoWounds);
            var deadAnchor = EncounterAnchors.resolve(g.world, defeated.encounterId);
            UrbanEncounterArena.observeAway(g, deadAnchor);
            assertNull(ledger.tryActivate(g, deadAnchor));
            assertTrue(SaveSystem.save(g, save));
        }
        var anchor = EncounterAnchors.resolve(g.world, dormant.encounterId);
        UrbanEncounterArena.observeAway(g, anchor);
        var returned = g.entities.encounters.tryActivate(g, anchor);
        assertNotNull(returned); assertEquals(22, returned.health); assertEquals(1, returned.torsoWounds);
        assertEquals(dormant.encounterId, returned.encounterId);
    }

    @Test void projectileTorsoWoundsSaveWhileAliveAndAfterAShotKill() {
        Game g = UrbanEncounterArena.session(20260731);
        var n = UrbanEncounterArena.activate(g, false);
        long id = n.encounterId;
        Path save = directory.resolve("wounded.sav");
        // Real arrows through the production projectile path: two torso hits wound, a third kills.
        for (int arrow = 1; arrow <= 3; arrow++) {
            var body = g.entities.encounters.resident(id).live;
            g.player.pos.set(body.pos.x - 4, body.pos.y, body.pos.z);
            int before = body.torsoWounds;
            g.projectiles.fire(g, g.player, true, g.player.pos.x, body.pos.y + 1.0f, body.pos.z,
                    1, 0, 0, com.veylon.combat.WeaponRegistry.byId("primitive_bow"), null);
            for (int tick = 0; tick < 40 && body.torsoWounds == before; tick++) { g.projectiles.update(g, .05f); }
            assertEquals(before + com.veylon.combat.ProjectileLethality.ARROW_TORSO_WOUNDS, body.torsoWounds);
            if (arrow < 3) {
                assertFalse(body.dead);
                assertTrue(SaveSystem.save(g, save), "a wounded Veilbound must not block saving");
                assertTrue(SaveSystem.load(g, save));
                var live = g.entities.encounters.resident(id).live;
                assertNotNull(live); assertEquals(body.torsoWounds, live.torsoWounds);
                assertEquals(body.health, live.health);
            } else {
                assertTrue(body.dead);
            }
        }
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.save(g, save), "a shot-down Veilbound's tombstone must keep saving");
            assertTrue(SaveSystem.load(g, save));
            var r = g.entities.encounters.resident(id);
            assertTrue(r.dead()); assertEquals(3 * com.veylon.combat.ProjectileLethality.ARROW_TORSO_WOUNDS, r.torsoWounds);
            var anchor = EncounterAnchors.resolve(g.world, id);
            UrbanEncounterArena.observeAway(g, anchor);
            assertNull(g.entities.encounters.tryActivate(g, anchor));
        }
    }

    @Test void invalidCountsIdentitySpeciesScalarsBindingsAndMissingRequiredSectionKeepLiveWorld() throws Exception {
        Game g = UrbanEncounterArena.session(7); UrbanEncounterArena.activate(g, false);
        Path valid = directory.resolve("valid.sav"); assertTrue(SaveSystem.save(g, valid));
        byte[] save = Files.readAllBytes(valid), payload = UrbanPopulationSection.write(g);
        var originalWorld = g.world; var originalPlayer = g.player;
        var invalid = new java.util.ArrayList<byte[]>();
        invalid.add(null); invalid.add(Arrays.copyOf(payload, payload.length - 1));
        for (int count : new int[]{-1, UrbanEncounterDirector.MAX_RECORDS + 1, Integer.MAX_VALUE}) {
            byte[] bad = payload.clone(); ByteBuffer.wrap(bad).putInt(8, count); invalid.add(bad);
        }
        byte[] unknownVersion = payload.clone(); ByteBuffer.wrap(unknownVersion).putInt(0, 99); invalid.add(unknownVersion);
        byte[] unknownCatalogue = payload.clone(); ByteBuffer.wrap(unknownCatalogue).putInt(4, 99); invalid.add(unknownCatalogue);
        byte[] unknownSpecies = payload.clone(); unknownSpecies[22] = 'x'; invalid.add(unknownSpecies);
        byte[] badId = payload.clone(); ByteBuffer.wrap(badId).putLong(12, 0); invalid.add(badId);
        for (int offset : new int[]{31, 35, 39, 43, 47}) {
            byte[] bad = payload.clone(); ByteBuffer.wrap(bad).putFloat(offset, Float.NaN); invalid.add(bad);
        }
        for (int count : new int[]{-1, UrbanPopulationSection.MAX_LIVING_WOUNDS + 1}) {
            byte[] wounds = payload.clone(); ByteBuffer.wrap(wounds).putInt(51, count); invalid.add(wounds);
        }
        byte[] hiddenActive = payload.clone(); ByteBuffer.wrap(hiddenActive).putInt(55, -1); invalid.add(hiddenActive);
        byte[] badIndex = payload.clone(); ByteBuffer.wrap(badIndex).putInt(55, 500); invalid.add(badIndex);
        byte[] mismatch = payload.clone(); ByteBuffer.wrap(mismatch).putFloat(31, 3); invalid.add(mismatch);
        byte[] duplicate = Arrays.copyOf(payload, 12 + 2 * 47);
        ByteBuffer.wrap(duplicate).putInt(8, 2); System.arraycopy(payload, 12, duplicate, 59, 47); invalid.add(duplicate);
        for (byte[] bad : invalid) {
            Path broken = directory.resolve("broken.sav");
            Files.write(broken, CreativeSaveSections.replace(save, UrbanPopulationSection.ID, bad));
            assertFalse(SaveSystem.load(g, broken));
            assertSame(originalWorld, g.world); assertSame(originalPlayer, g.player);
            assertEquals(1, g.entities.encounters.size()); assertEquals(1, g.entities.npcs.size());
        }
        assertTrue(SaveSystem.load(g, valid));
    }

    @Test void oldWorldsRemainEncounterFreeAndAbsenceOfNewStateIsNotAnUpgrade() throws Exception {
        Game g = new Game(); Path save = directory.resolve("old.sav");
        for (int generator : new int[]{World.GEN_CAVE_IDENTITIES, World.GEN_URBAN_STARTER}) {
            g.newWorld(7, true, generator, GameMode.SURVIVAL);
            assertTrue(SaveSystem.save(g, save)); assertTrue(SaveSystem.load(g, save));
            assertEquals(generator, g.world.generatorVersion);
            assertFalse(UrbanEncounterDirector.manages(g.world));
            g.entities.encounters.slowTick(g); assertEquals(0, g.entities.encounters.size());
            byte[] unexpected = CreativeSaveSections.append(Files.readAllBytes(save), UrbanPopulationSection.ID,
                    new byte[]{0,0,0,1,0,0,0,1,0,0,0,0});
            Path broken = directory.resolve("unexpected.sav"); Files.write(broken, unexpected);
            var oldWorld = g.world; assertFalse(SaveSystem.load(g, broken)); assertSame(oldWorld, g.world);
        }
    }
}
