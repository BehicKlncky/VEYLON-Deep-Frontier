package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.SurvivorSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.veylon.CompoundWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Compounds in {@code urban.sites} v1 (urban milestone 17): a stronghold's guards, their injuries,
 * deaths and the stronghold's standing, and a raider yard's dead and its locked gate, come back from
 * repeated loads and reactivation with no guard duplicated or resurrected; a compound row that
 * claims what a compound can never be fails the whole load.
 */
class UrbanCompoundSaveTest {
    @TempDir Path directory;

    /** The stronghold nearest the spawn met by day, with an injured guard, a killing and its standing. */
    private static Game strongholdHistory() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        setClock(g, 10 * 60);
        standFacing(g, c, street(c, 3), c.gate().outside());
        com.veylon.SurvivorWorldArena.slowTick(g);
        com.veylon.SurvivorWorldArena.run(g, 20f);
        UrbanSite s = site(g, c);
        assertTrue(s.discovered);
        s.resident(1).live.health = 17f;
        s.fear = 12f;
        Npc victim = s.resident(c.site().residents().size() - 1).live;
        victim.hurt(999, true); // killed and saved before the next tick: the save commits the death once
        return g;
    }

    @Test
    void aStrongholdsGuardsTheirInjuriesTheDeadAndItsStandingSurviveRepeatedLoads() {
        Game g = strongholdHistory();
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        int dead = c.site().residents().size() - 1;
        Path file = directory.resolve("stronghold.sav");
        assertTrue(SaveSystem.save(g, file));
        UrbanSite before = site(g, c);
        float trust = before.localReputation;
        assertFalse(before.resident(dead).alive, "the save committed the death");
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file), "load " + cycle);
            UrbanSite s = site(g, c);
            assertNotNull(s);
            assertTrue(s.discovered);
            assertTrue(s.stronghold());
            assertEquals(trust, s.localReputation, 1e-3f, "the killing's cost is kept");
            assertFalse(s.resident(dead).alive, "the dead stay dead");
            assertNull(s.resident(dead).live);
            Npc sentry = s.resident(1).live;
            assertNotNull(sentry, "an active guard is bound to the same body");
            assertEquals(17f, sentry.health, 1e-4f);
            assertEquals(c.site().resident(1).name(), sentry.name);
            assertEquals(c.site().residents().size() - 1, live(s).size(), "every living guard is there once");
        }
        // Dormant and back: the same people, the dead still dead, nobody twice.
        com.veylon.SurvivorWorldArena.standAt(g, c.site().anchor().offset(340, 0, 0));
        com.veylon.SurvivorWorldArena.slowTick(g);
        assertTrue(live(site(g, c)).isEmpty());
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        standFacing(g, c, street(c, 3), c.gate().outside());
        com.veylon.SurvivorWorldArena.slowTick(g);
        com.veylon.SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        assertEquals(c.site().residents().size() - 1, live(s).size(), "everyone but the dead comes back");
        assertNull(s.resident(dead).live, "the dead are never reactivated");
        Set<Npc> bodies = new HashSet<>(live(s));
        assertEquals(live(s).size(), bodies.size());
        int bound = 0;
        for (Npc n : g.entities.npcs) {
            if (!n.dead && n.settled() && n.settlementId == s.id) { bound++; }
        }
        assertEquals(live(s).size(), bound, "no duplicate guard in the world");
        assertEquals(17f, s.resident(1).live.health, 1e-4f);
    }

    @Test
    void aRaiderYardComesBackHostileWithItsDeadAndItsGateLocked() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.RAIDERS);
        setClock(g, 12 * 60);
        standFacing(g, c, street(c, 8), c.gate().outside());
        com.veylon.SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        Npc sentry = s.resident(1).live;
        sentry.hurt(999, true);
        Path file = directory.resolve("raiders.sav");
        assertTrue(SaveSystem.save(g, file));
        float trust = site(g, c).localReputation;
        assertTrue(trust <= UrbanSite.RAIDER_TRUST, "raiders never trust a stranger");
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file));
            UrbanSite r = site(g, c);
            assertTrue(r.raiders() && r.hostile(), "raiders stay hostile");
            assertFalse(r.resident(1).alive, "their dead stay dead");
            assertEquals(trust, r.localReputation, 1e-4f);
            load(g, c);
            assertTrue(gateShutAndLocked(g, c), "their gate is still shut and locked");
            assertEquals(c.site().residents().size() - 1, live(r).size());
        }
    }

    @Test
    void aCompoundRowClaimingWhatACompoundCannotBeFailsTheWholeLoad() throws IOException {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.RAIDERS);
        setClock(g, 12 * 60);
        standFacing(g, c, street(c, 8), c.gate().outside());
        com.veylon.SurvivorWorldArena.slowTick(g);
        assertTrue(SaveSystem.save(g, directory.resolve("settled.sav")));
        byte[] good = UrbanSitesSection.write(g);
        int row = rowOf(good, c.site().id());
        List<byte[]> bad = List.of(
                withFloat(good, row + 11, -10f),        // a raider yard trusting above its cap
                withByte(good, row + 10, good[row + 10] | 4),  // a compound's stash left to the player
                withByte(good, row + 10, good[row + 10] | 2),  // a raider yard betrayed
                withByte(good, row + 31, 1),            // a compound setting out to move
                withInt(good, row + 48, 0));            // a raider medic who treated the player
        for (int i = 0; i < bad.size(); i++) {
            byte[] payload = bad.get(i);
            int index = i;
            assertThrows(IOException.class, () -> UrbanSitesSection.read(payload, freshLoad(g)),
                    "malformed compound case " + index + " must fail");
        }
        assertDoesNotThrow(() -> UrbanSitesSection.read(good, freshLoad(g)), "the payload as written reads");
    }

    /** The offset of the row of a site id in an {@code urban.sites} payload. */
    private static int rowOf(byte[] payload, long id) {
        ByteBuffer b = ByteBuffer.wrap(payload);
        int sites = b.getInt(4), offset = 8;
        for (int s = 0; s < sites; s++) {
            int residents = payload[offset + 56] & 0xff;
            if (b.getLong(offset) == id) { return offset; }
            offset += 57 + 17 * residents;
        }
        throw new AssertionError("no row for the compound");
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

    private static byte[] withInt(byte[] b, int at, int value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putInt(at, value);
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
