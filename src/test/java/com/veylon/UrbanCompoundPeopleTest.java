package com.veylon;

import com.veylon.ai.CompoundResidentAI;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanFactions;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.SurvivorSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.veylon.CompoundWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Urban milestone 17 on a generated generator-16 city, through the production ticks: a
 * stronghold's guards keep their posts, pass their own gate and shut and lock it behind them, walk
 * their patrol and keep watch by night; raiders notice a Survival player only through fair senses,
 * go at them within their leash and give up; guards fight an infected that comes to the gate and it
 * strikes back; and people keep their health and needs while dormant.
 */
class UrbanCompoundPeopleTest {

    @Test
    void aStrongholdsPeopleKeepTheirPostsAndComeBackTheSameAfterDormancy() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        setClock(g, 10 * 60);
        standFacing(g, c, street(c, 3), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        assertNotNull(s, "a stronghold near the player is registered");
        assertEquals(UrbanFactions.WARDENS, s.factionId, "an urban side of its own");
        assertEquals("Wardens", UrbanFactions.displayName(s.factionId));
        assertEquals(c.site().residents().size(), live(s).size(), "everyone is home and inside the budget");
        for (Npc n : live(s)) {
            assertEquals(UrbanSite.archetypeOf(c.kind(), s.resident(n.residentIndex).role), n.archetype);
            assertFalse(n.hostileToPlayer(), "a stronghold starts neutral");
        }
        SurvivorWorldArena.run(g, 45f);
        assertTrue(s.discovered, "a guard seen from the street makes the stronghold known");
        for (Npc n : live(s)) {
            UrbanSite.Resident r = s.resident(n.residentIndex);
            if (r.role == SurvivorSites.Role.PATROL) { continue; }
            Vec3i duty = c.site().resident(r.slot).day();
            assertTrue(near(n, duty, 1.3f), n.name + " (" + r.role + ") keeps " + duty + ", is at " + n.pos);
        }
        assertTrue(clearOfWalls(g, s), "nobody stands in a wall");
        Npc hurt = s.resident(1).live;
        hurt.health = 13f;
        SurvivorWorldArena.standAt(g, c.site().anchor().offset(330, 0, 0));
        SurvivorWorldArena.slowTick(g);
        assertTrue(live(s).isEmpty(), "far away, everyone is dormant");
        assertEquals(13f, s.resident(1).health, 1e-4f, "a dormant guard keeps his injuries");
        standFacing(g, c, street(c, 3), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        assertEquals(c.site().residents().size(), live(s).size(), "the same people come back, no more");
        assertEquals(13f, s.resident(1).live.health, 1e-4f);
        Set<Npc> bodies = new HashSet<>(live(s));
        assertEquals(live(s).size(), bodies.size(), "no guard is duplicated");
    }

    @Test
    void theGatekeeperPassesHisOwnGateAndItIsShutAndLockedBehindHim() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        setClock(g, 5 * 60);
        standFacing(g, c, street(c, 4), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        Npc keeper = s.resident(0).live;
        assertNotNull(keeper);
        assertEquals(SurvivorSites.Role.GATEKEEPER, s.resident(0).role);
        assertTrue(c.inside(SurvivorWorldArena.feet(keeper).x(), SurvivorWorldArena.feet(keeper).y(),
                SurvivorWorldArena.feet(keeper).z()), "before dawn the gatekeeper sleeps inside");
        assertTrue(gateShutAndLocked(g, c), "the gate is locked through the night");
        setClock(g, 6 * 60 + 10);
        boolean opened = false;
        for (int t = 0; t < 120 * 20 && !near(keeper, c.gate().outside(), 1.2f); t++) {
            g.advanceWorld(SurvivorWorldArena.DT);
            opened |= g.world.getBlock(c.gate().first().x(), c.gate().first().y(), c.gate().first().z())
                    == com.veylon.world.BlockType.METAL_DOOR_OPEN;
        }
        assertTrue(near(keeper, c.gate().outside(), 1.2f), "by day the gatekeeper keeps the porch, is at " + keeper.pos);
        assertTrue(opened, "he opened the gate to pass");
        SurvivorWorldArena.run(g, 8f);
        assertTrue(gateShutAndLocked(g, c), "and it was shut and locked behind him");
    }

    @Test
    void aPatrolWalksItsRoundsAndTheWatchKeepsTheGateByNight() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        setClock(g, 10 * 60);
        standFacing(g, c, street(c, 3), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        Npc patrol = s.resident(2).live;
        assertEquals(SurvivorSites.Role.PATROL, s.resident(2).role);
        Set<Vec3i> visited = new HashSet<>();
        for (int t = 0; t < 120 * 20; t++) {
            g.advanceWorld(SurvivorWorldArena.DT);
            for (Vec3i p : c.patrol()) { if (near(patrol, p, 1.0f)) { visited.add(p); } }
        }
        assertTrue(visited.size() >= 2, "the patrol walks from waypoint to waypoint: " + visited);
        setClock(g, 23 * 60 + 30);
        SurvivorWorldArena.run(g, 60f);
        Npc watch = s.resident(1).live;
        assertTrue(near(watch, c.gate().inside(), 1.3f), "the inner sentry keeps the gate by night, is at " + watch.pos);
        for (Npc n : live(s)) {
            UrbanSite.Resident r = s.resident(n.residentIndex);
            if (r.slot == 1 || r.slot == 2) { continue; }
            assertTrue(near(n, c.site().resident(r.slot).bed(), 1.3f), n.name + " (" + r.role + ") sleeps, is at " + n.pos);
        }
        assertTrue(gateShutAndLocked(g, c), "the gate is locked at night");
    }

    @ParameterizedTest
    @EnumSource(GameMode.class)
    void raidersGoAtAPlayerTheyCanSeeAndNeverAtOneTheCannot(GameMode mode) {
        Game g = session(SEED, mode);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.RAIDERS);
        setClock(g, 12 * 60);
        standFacing(g, c, street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        assertNotNull(s);
        assertTrue(s.hostile(), "raiders are hostile from the start");
        assertEquals(UrbanFactions.WRECKERS, s.factionId);
        for (Npc n : live(s)) {
            assertEquals(NpcArchetype.SCAVENGER, n.archetype);
            assertTrue(n.hostileToPlayer());
        }
        float before = g.player.health;
        boolean struck = false;
        for (int t = 0; t < 30 * 20 && !struck; t++) {
            g.advanceWorld(SurvivorWorldArena.DT);
            struck = g.player.health < before;
        }
        if (mode == GameMode.SURVIVAL) {
            assertTrue(struck, "the sentry sees a Survival player at the porch in daylight and strikes");
        } else {
            assertFalse(struck, "nobody can strike a Creative player");
            for (Npc n : live(s)) {
                assertNotEquals(Npc.NpcState.ATTACK, n.state, n.name + " never goes at a player it cannot perceive");
            }
        }
    }

    @Test
    void raidersHoldTheirLeashAndGiveUpOnAPlayerTheyLost() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.RAIDERS);
        setClock(g, 12 * 60);
        standFacing(g, c, street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        float before = g.player.health;
        for (int t = 0; t < 30 * 20 && g.player.health >= before; t++) { g.advanceWorld(SurvivorWorldArena.DT); }
        assertTrue(g.player.health < before, "they noticed the player");
        g.player.health = g.player.maxHealth;
        // Gone: far down the street, out of sight and beyond every post's leash.
        Vec3i away = alongStreet(g, c, (int) CompoundResidentAI.LEASH + 14);
        SurvivorWorldArena.standAt(g, away);
        SurvivorWorldArena.run(g, CompoundResidentAI.FORGET + 30f);
        for (Npc n : live(s)) {
            UrbanSite.Resident r = s.resident(n.residentIndex);
            Vec3i post = c.site().resident(r.slot).day();
            float dx = n.pos.x - post.x() - .5f, dz = n.pos.z - post.z() - .5f;
            assertTrue(dx * dx + dz * dz <= (CompoundResidentAI.LEASH + 2) * (CompoundResidentAI.LEASH + 2),
                    n.name + " stayed within the leash of " + post + ", is at " + n.pos);
            assertNotEquals(Npc.NpcState.ATTACK, n.state, n.name + " gave up");
        }
    }

    @Test
    void guardsFightAnInfectedAtTheGateAndItStrikesBack() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        setClock(g, 10 * 60);
        standFacing(g, c, street(c, 16), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        SurvivorWorldArena.run(g, 30f);
        Npc keeper = s.resident(0).live;
        assertTrue(near(keeper, c.gate().outside(), 1.3f), "the gatekeeper is on the porch");
        g.player.crouching = true;
        Npc dead = infected(g, street(c, 1));
        float keeperBefore = keeper.health;
        for (int t = 0; t < 60 * 20 && !dead.dead; t++) { g.advanceWorld(SurvivorWorldArena.DT); }
        assertTrue(dead.dead || dead.health < dead.maxHealth, "the guards fought the infected at their gate");
        assertTrue(keeper.dead || keeper.health < keeperBefore || anyHurt(s),
                "and the infected struck back at a guard fighting it");
    }

    private static boolean anyHurt(UrbanSite s) {
        for (Settlement.Resident r : s.residents) {
            if (!r.alive || r.live != null && r.live.health < r.live.maxHealth) { return true; }
        }
        return false;
    }

    @Test
    void earlierGeneratorsKeepTheirInfectedOutOfFightsWithTheLiving() {
        Game g = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site camp = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        assertTrue(g.world.urban.sites(0, 0).compounds().isEmpty());
        assertNull(com.veylon.world.urban.SurvivorSites.compoundOf(g.world.urban, camp.id()));
        List<SurvivorSites.Site> sites = g.world.urban.sites(0, 0).sites();
        for (SurvivorSites.Site site : sites) { assertFalse(site.kind().compound(), "generator 15 lists no compound"); }
    }
}
