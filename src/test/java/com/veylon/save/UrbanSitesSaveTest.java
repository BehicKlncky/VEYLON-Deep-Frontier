package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.world.World;
import com.veylon.world.urban.SurvivorSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static com.veylon.SurvivorWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code urban.sites} v1 (urban milestone 16): the same people, injuries, needs, deaths and
 * standing come back from a save, whether they were on screen or dormant, across repeated loads
 * and reactivation; nothing is resurrected or renumbered; a generator-15 save without the section,
 * and malformed data, fail the whole load and leave the live world alone; the legacy settlement
 * codec never writes a survivor site.
 */
class UrbanSitesSaveTest {
    @TempDir Path directory;

    /** A camp met and a refuge found, with some history: injuries, needs, a death, trust and fear. */
    private static Game history() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site refuge = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, hall(g, refuge));
        slowTick(g);
        pressF(g, panel(g, refuge));
        UrbanSite r = site(g, refuge);
        assertTrue(r.discovered);
        r.resident(0).live.health = 9f;
        r.fear = 55f;
        // Then the camp, which is on screen when the game is saved.
        SurvivorSites.Site camp = nearest(g, SurvivorSites.Kind.CAMP);
        standAt(g, camp.entry());
        slowTick(g);
        UrbanSite c = site(g, camp);
        c.resident(0).live.health = 21f;
        c.resident(1).hunger = 44f;
        c.resident(1).thirst = 61f;
        c.localReputation = 12.5f;
        c.fear = 17f;
        c.owed = 3;
        Npc victim = c.resident(camp.residents().size() - 1).live;
        victim.hurt(999, true); // killed and saved before the next tick: the save commits the death once
        return g;
    }

    @Test
    void thePeopleTheirInjuriesNeedsDeathsAndStandingSurviveRepeatedLoads() {
        Game g = history();
        SurvivorSites.Site camp = nearest(g, SurvivorSites.Kind.CAMP), refuge = nearest(g, SurvivorSites.Kind.REFUGE);
        int dead = camp.residents().size() - 1;
        Path file = directory.resolve("sites.sav");
        assertTrue(SaveSystem.save(g, file));
        assertFalse(site(g, camp).resident(dead).alive, "the save committed the death");
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file), "load " + cycle);
            UrbanSite c = site(g, camp), r = site(g, refuge);
            assertNotNull(c);
            assertNotNull(r);
            assertTrue(c.discovered && r.discovered);
            assertEquals(12.5f - 60f, c.localReputation, 1e-3f, "the killing cost 60 trust, and that is kept");
            assertTrue(c.hostile(), "so the camp stays hostile");
            assertEquals(100f, c.fear, 1e-4f);
            assertEquals(3, c.owed);
            assertFalse(c.resident(dead).alive, "the dead stay dead");
            assertEquals(0f, c.resident(dead).health);
            assertNull(c.resident(dead).live);
            assertEquals(44f, c.resident(1).hunger, 1e-4f);
            assertEquals(61f, c.resident(1).thirst, 1e-4f);
            Npc lookout = c.resident(0).live;
            assertNotNull(lookout, "an active resident is bound to the same body");
            assertEquals(21f, lookout.health, 1e-4f);
            assertEquals(camp.resident(0).name(), lookout.name);
            assertEquals(c.id, lookout.settlementId);
            assertEquals(0, lookout.residentIndex);
            assertEquals(lookout.archetype.maxHealth, lookout.maxHealth, "the body's full health is its role's");
            assertEquals(9f, r.resident(0).health, 1e-4f);
            assertEquals(55f, r.fear, 1e-4f);
            for (Settlement s : g.world.settlements.values()) {
                assertInstanceOf(UrbanSite.class, s, "a city registers no wilderness settlement");
            }
        }
        // Dormant and back: the same people, the dead still dead.
        standAt(g, camp.anchor().offset(320, 0, 0));
        slowTick(g);
        assertTrue(live(site(g, camp)).isEmpty());
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        standAt(g, camp.entry());
        slowTick(g);
        UrbanSite c = site(g, camp);
        assertEquals(camp.residents().size() - 1, live(c).size(), "everyone but the dead comes back");
        assertNull(c.resident(dead).live, "the dead are never reactivated");
        assertEquals(21f, c.resident(0).live.health, 1e-4f);
    }

    @Test
    void aGeneratorFifteenSaveWithoutItsSitesFailsAndLeavesTheLiveWorldAlone() throws IOException {
        Game g = history();
        Path file = directory.resolve("missing.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] bytes = Files.readAllBytes(file);
        // Renaming the section makes it an unknown id, which the reader skips: the same as an absent one.
        byte[] id = UrbanSitesSection.ID.getBytes(StandardCharsets.UTF_8);
        int at = indexOf(bytes, id);
        assertTrue(at > 0);
        bytes[at + id.length - 1] = 'z';
        Path broken = directory.resolve("missing-broken.sav");
        Files.write(broken, bytes);
        World before = g.world;
        assertFalse(SaveSystem.load(g, broken), "a required section is missing");
        assertSame(before, g.world, "the live world is untouched");
        Game fresh = new Game();
        assertFalse(SaveSystem.load(fresh, broken));
    }

    @Test
    void malformedSiteDataFailsTheWholeLoad() throws IOException {
        Game g = history();
        // A save first: it commits the pending death, so the payload's body indices are final.
        assertTrue(SaveSystem.save(g, directory.resolve("settled.sav")));
        byte[] good = UrbanSitesSection.write(g);
        // Layout: 8-byte header, then 57-byte sites each followed by 17 bytes per resident.
        int site0 = 8, bound = boundResident(good);
        List<byte[]> bad = List.of(
                withInt(good, 0, 2),                                   // another version
                withLong(good, site0, 0x7fff_ffff_ffff_fffL),          // no planned site
                withByte(good, site0 + 8, 9),                          // unknown kind
                withFloat(good, site0 + 11, Float.NaN),                // trust
                withFloat(good, site0 + 11, 101f),                     // trust beyond its range
                withFloat(good, site0 + 15, -1f),                      // fear
                withByte(good, site0 + 31, 9),                         // unknown relocation
                withByte(good, site0 + 56, 1),                         // head count
                withFloat(good, bound + 1, 1000f),                     // health beyond the role's
                withFloat(good, bound + 1, 7f),                        // health its body does not have
                withFloat(good, bound + 5, 100.5f),                    // hunger
                withByte(good, bound, 0),                              // a dead resident still bound
                withInt(good, bound + 13, 4096),                       // a body that does not exist
                withInt(good, bound + 13, -1),                         // a live body nobody claims
                Arrays.copyOf(good, good.length + 1));                 // trailing byte
        for (int i = 0; i < bad.size(); i++) {
            byte[] payload = bad.get(i);
            int index = i;
            assertThrows(IOException.class, () -> UrbanSitesSection.read(payload, freshLoad(g)),
                    "malformed case " + index + " must fail");
        }
        assertDoesNotThrow(() -> UrbanSitesSection.read(good, freshLoad(g)), "the payload as written reads");
    }

    /** The offset of the first resident row bound to a live body. */
    private static int boundResident(byte[] payload) {
        ByteBuffer b = ByteBuffer.wrap(payload);
        int sites = b.getInt(4), offset = 8;
        for (int s = 0; s < sites; s++) {
            int residents = payload[offset + 56] & 0xff, first = offset + 57;
            for (int k = 0; k < residents; k++) {
                if (b.getInt(first + 17 * k + 13) >= 0) { return first + 17 * k; }
            }
            offset = first + 17 * residents;
        }
        throw new AssertionError("no resident bound to a body");
    }

    /** A game holding the same world and bodies as {@code g}, with no site restored yet. */
    private Game freshLoad(Game g) throws IOException {
        Path file = directory.resolve("fresh-" + System.nanoTime() + ".sav");
        if (!SaveSystem.save(g, file)) { throw new IOException("save failed"); }
        Game copy = new Game();
        if (!SaveSystem.load(copy, file)) { throw new IOException("load failed"); }
        copy.world.settlements.clear();
        copy.settlementManager.urbanSites.restored = false;
        for (Npc n : copy.entities.npcs) {
            if (UrbanSiteDirector.siteOf(copy.world, n) != null) { throw new IOException("precondition"); }
        }
        return copy;
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) { continue outer; }
            }
            return i;
        }
        return -1;
    }

    private static byte[] withInt(byte[] b, int at, int value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putInt(at, value);
        return out;
    }

    private static byte[] withLong(byte[] b, int at, long value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putLong(at, value);
        return out;
    }

    private static byte[] withFloat(byte[] b, int at, float value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putFloat(at, value);
        return out;
    }

    private static byte[] withByte(byte[] b, int at, int value) {
        byte[] out = b.clone();
        out[at] = (byte) value;
        return out;
    }
}
