package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.CompoundAccess;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.SurvivorSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;

import static com.veylon.CompoundWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Urban milestone 17's access rules and their consequences on a generated generator-16 city,
 * through the production ticks, the conversation screen's command, the storage seam and the F key:
 * a visitor let in by day and kept to the yard and the clinic, warned and then put out; an intruder
 * seen without leave; theft caught in sight and theft blamed only on evidence; forced entry heard
 * and answered by at most three guards; a friend let in at night by a knock and a stranger turned
 * away; raiders who send at most two hunters, once a day; and a raider yard taken and paid for once.
 */
class UrbanCompoundAccessTest {
    @TempDir Path directory;

    private static Game strongholdAtNoon(GameMode mode) {
        Game g = session(SEED, mode);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        setClock(g, 10 * 60);
        standFacing(g, c, street(c, 1), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.run(g, 20f);
        return g;
    }

    @Test
    void aVisitorIsLetInByDayAndKeptToTheYardAndTheClinic() {
        Game g = strongholdAtNoon(GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        UrbanSite s = site(g, c);
        Npc keeper = s.resident(0).live;
        assertTrue(near(keeper, c.gate().outside(), 1.3f), "the gatekeeper keeps the porch");
        assertTrue(g.npcScreen.performSiteChoice(g, keeper, 1));
        assertTrue(g.npcScreen.lastResponse().contains("the yard and the clinic"), "the rules: " + g.npcScreen.lastResponse());
        assertTrue(g.npcScreen.siteOptions(g, keeper).contains("[4] Ask to be let in"));
        assertTrue(g.npcScreen.performSiteChoice(g, keeper, 4));
        assertTrue(CompoundAccess.hasPass(g, s), "a visitor's pass until dusk: " + g.npcScreen.lastResponse());
        assertSame(BlockType.METAL_DOOR_OPEN, g.world.getBlock(c.gate().first().x(), c.gate().first().y(), c.gate().first().z()),
                "the gatekeeper opens the gate");
        assertTrue(CompoundAccess.allowed(g, s, CompoundSites.Zone.YARD));
        assertTrue(CompoundAccess.allowed(g, s, CompoundSites.Zone.GUEST));
        assertFalse(CompoundAccess.allowed(g, s, CompoundSites.Zone.RESTRICTED), "the buildings are theirs");
        // In through the open gate, then a while in the yard in full view of its guards.
        standFacing(g, c.gate().laneInside(), c.site().anchor());
        SurvivorWorldArena.run(g, 12f);
        assertTrue(s.lastSeenInside >= 0, "a guard saw the visitor inside");
        assertFalse(CompoundAccess.wanted(g, s), "a visitor in the yard breaks no rule");
        assertEquals(0f, s.warnTimer, 1e-4f, "and is not warned");
        assertTrue(gateShutAndLocked(g, c), "the gate was shut and locked behind the visitor");
        setClock(g, CompoundAccess.DUSK * 60 + 5);
        assertFalse(CompoundAccess.hasPass(g, s), "the pass runs out at dusk");
    }

    @Test
    void aVisitorSeenInTheBarracksIsWarnedOnceThenLosesThePassAndIsWanted() {
        Game g = strongholdAtNoon(GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        UrbanSite s = site(g, c);
        assertTrue(g.npcScreen.performSiteChoice(g, s.resident(0).live, 4));
        Vec3i barracks = null;
        for (SurvivorSites.Resident r : c.site().residents()) {
            if (c.zoneAt(r.bed().x(), r.bed().y(), r.bed().z()) == CompoundSites.Zone.RESTRICTED) { barracks = r.bed(); break; }
        }
        assertNotNull(barracks);
        Npc guard = s.resident(1).live;
        float trust = s.localReputation;
        CompoundAccess.sighted(g, s, c, guard, barracks);
        assertTrue(s.warnTimer > 0f, "the first sighting is a warning");
        assertTrue(CompoundAccess.hasPass(g, s), "the pass is kept");
        CompoundAccess.sighted(g, s, c, guard, barracks);
        assertTrue(CompoundAccess.hasPass(g, s), "within the grace a second sighting is the same warning");
        SurvivorWorldArena.run(g, CompoundAccess.GRACE + 1f);
        CompoundAccess.sighted(g, s, c, guard, barracks);
        assertFalse(CompoundAccess.hasPass(g, s), "seen there again: the pass is gone");
        assertTrue(CompoundAccess.wanted(g, s), "and the visitor is wanted out");
        assertTrue(s.localReputation < trust, "trespass costs trust");
        assertFalse(s.hostile(), "but the stronghold does not turn hostile over it");
        assertEquals("Not today. You know what you did.", CompoundAccess.admit(g, s, c, s.resident(0).live));
    }

    @ParameterizedTest
    @EnumSource(GameMode.class)
    void anIntruderSeenInsideWithoutLeaveIsWantedAndDrivenOut(GameMode mode) {
        Game g = strongholdAtNoon(mode);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        UrbanSite s = site(g, c);
        float trust = s.localReputation;
        // Over the wall unseen, then two steps from the guard behind the gate.
        standFacing(g, c.gate().laneInside(), c.site().anchor());
        float before = g.player.health;
        boolean struck = false;
        for (int t = 0; t < 25 * 20 && !struck; t++) {
            g.advanceWorld(SurvivorWorldArena.DT);
            struck = g.player.health < before;
        }
        if (mode == GameMode.SURVIVAL) {
            assertTrue(CompoundAccess.wanted(g, s), "an intruder seen inside is wanted at once");
            assertTrue(s.localReputation < trust);
            assertTrue(struck, "and driven out by the guards");
        } else {
            assertFalse(CompoundAccess.wanted(g, s), "nobody perceives a Creative player");
            assertFalse(struck);
            assertEquals(trust, s.localReputation, 1e-4f);
        }
    }

    @Test
    void theftInAGuardsSightIsCaughtAndTheftUnseenIsBlamedOnlyOnEvidence() {
        Game g = strongholdAtNoon(GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        UrbanSite s = site(g, c);
        long owner = c.members().getFirst();
        // Seen: a guard stands by and watches.
        Npc guard = s.resident(1).live;
        Vec3i at = c.gate().inside();
        standFacing(g, at, c.site().anchor());
        guard.pos.set(at.x() + .5f, at.y(), at.z() + 1.5f);
        guard.yaw = 180f;
        float trust = s.localReputation;
        assertTrue(g.settlementManager.urbanSites.onStorageTaken(g, owner, 2));
        assertTrue(s.localReputation < trust, "theft in sight is caught");
        assertTrue(CompoundAccess.wanted(g, s));
        // Unseen, by a stranger no guard saw inside: the stores come up short and nobody is blamed.
        Game h = strongholdAtNoon(GameMode.SURVIVAL);
        UrbanSite t = site(h, nearest(h, SurvivorSites.Kind.STRONGHOLD));
        SurvivorWorldArena.standAt(h, t.plan.anchor().offset(400, 0, 0));
        SurvivorWorldArena.slowTick(h);
        assertTrue(live(t).isEmpty(), "nobody is there to see");
        t.lastSeenInside = -1;
        float trustT = t.localReputation;
        assertTrue(h.settlementManager.urbanSites.onStorageTaken(h, owner, 3));
        assertEquals(3, t.missing, "unseen, the loss is only missing stores");
        assertEquals(trustT, t.localReputation, 1e-4f);
        h.time.totalMinutes += 61;
        SurvivorWorldArena.slowTick(h);
        assertEquals(0, t.missing, "noticed at the stronghold's next hour");
        assertEquals(trustT, t.localReputation, 1e-4f, "no evidence points at the player");
        assertFalse(CompoundAccess.wanted(h, t));
        // Unseen, but a guard saw the player inside an hour before: suspicion.
        t.lastSeenInside = h.time.totalMinutes - 30;
        assertTrue(h.settlementManager.urbanSites.onStorageTaken(h, owner, 3));
        h.time.totalMinutes += 61;
        SurvivorWorldArena.slowTick(h);
        assertTrue(t.localReputation < trustT, "seen inside not long before: they suspect the player");
        assertTrue(CompoundAccess.wanted(h, t));
    }

    @Test
    void forcedEntryIsHeardAndAtMostThreeGuardsRespondThenReturn() {
        Game g = strongholdAtNoon(GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        UrbanSite s = site(g, c);
        standFacing(g, street(c, 10), c.gate().outside());
        SurvivorWorldArena.run(g, 2f);
        // The noise a forced street door of the compound makes, heard through its walls.
        Vec3i door = c.barricades().get(1).cells().get(1);
        g.noise.emit(g, door.x() + .5f, door.y() + 1f, door.z() + .5f, 36f, 0.7f, "door-forced", true, g.player);
        for (int t = 0; t < 10 * 20 && s.responseTimer <= 0f; t++) { g.advanceWorld(SurvivorWorldArena.DT); }
        assertTrue(s.responseTimer > 0f, "the guards heard it and answer the alarm");
        int responders = Integer.bitCount(s.responders);
        assertTrue(responders >= 1 && responders <= CompoundAccess.RESPONDERS, "at most three respond: " + responders);
        assertEquals(0, s.responders & 0b11, "the gate's own guards stay at the gate");
        SurvivorWorldArena.run(g, CompoundAccess.RESPONSE_SECONDS + 2f);
        assertEquals(0, s.responders, "the response runs out and nobody new is sent for the same alarm");
        assertFalse(CompoundAccess.wanted(g, s), "a noise alone names nobody");
    }

    @Test
    void aFriendIsLetInAtNightByAKnockAndAStrangerIsTurnedAway() {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        setClock(g, 23 * 60 + 15);
        standFacing(g, c, c.gate().laneOutside(), c.gate().first());
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.run(g, 40f);
        UrbanSite s = site(g, c);
        assertTrue(near(s.resident(1).live, c.gate().inside(), 1.4f), "the watch keeps the gate");
        assertTrue(gateShutAndLocked(g, c));
        // A stranger knocks: answered through the gate, never opened.
        SurvivorWorldArena.pressF(g, c.gate().first());
        SurvivorWorldArena.run(g, 6f);
        assertTrue(gateShutAndLocked(g, c), "the watch does not open after dark for a stranger");
        // A friend knocks: opened.
        s.localReputation = 45f;
        s.settleAlignment();
        assertTrue(s.grantsAccess());
        SurvivorWorldArena.pressF(g, c.gate().first());
        boolean opened = false;
        for (int t = 0; t < 8 * 20 && !opened; t++) {
            g.advanceWorld(SurvivorWorldArena.DT);
            opened = g.world.getBlock(c.gate().first().x(), c.gate().first().y(), c.gate().first().z()) == BlockType.METAL_DOOR_OPEN;
        }
        assertTrue(opened, "the watch opens for a friend at any hour");
    }

    @Test
    void raidersSendAtMostTwoHuntersAndOnlyOnceADay() throws Exception {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.RAIDERS);
        setClock(g, 12 * 60);
        standFacing(g, c, street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite s = site(g, c);
        SurvivorWorldArena.run(g, 2f);
        s.resident(1).live.hurt(999, true); // the street sentry, killed in sight of the others
        SurvivorWorldArena.run(g, 1f);
        assertFalse(s.resident(1).alive);
        // The player slips away down the street, out of sight but within the hunters' reach.
        SurvivorWorldArena.standAt(g, alongStreet(g, c, 30));
        for (int t = 0; t < 40 * 20 && s.hunters == 0; t++) {
            g.advanceWorld(SurvivorWorldArena.DT);
            g.player.health = g.player.maxHealth;
        }
        int hunters = Integer.bitCount(s.hunters);
        assertTrue(hunters >= 1 && hunters <= CompoundAccess.HUNTERS, "at most two come after the player: " + hunters);
        assertEquals(0, s.hunters & 1, "never their boss");
        assertTrue(s.huntCooldownUntil > g.time.totalMinutes, "and not again for a day");
        double cooldown = s.huntCooldownUntil;
        SurvivorWorldArena.run(g, CompoundAccess.HUNT_SECONDS + 2f);
        assertEquals(0, s.hunters, "the hunt runs out");
        // Provoked again within the day: no second party.
        Npc another = null;
        for (Settlement.Resident r : s.residents) { if (r.alive && r.live != null) { another = r.live; } }
        assertNotNull(another);
        another.hurt(1f, true);
        SurvivorWorldArena.standAt(g, alongStreet(g, c, 30));
        SurvivorWorldArena.run(g, 30f);
        assertEquals(0, s.hunters, "the cooldown holds: hostility never becomes an endless counterattack");
        Path file = directory.resolve("hunt.sav");
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        assertEquals(cooldown, site(g, c).huntCooldownUntil, 1e-6, "the cooldown survives a load");
    }

    @Test
    void aRaiderYardWhoseRaidersAreAllDeadIsTakenAndAStrongholdIsGratefulOnce() throws Exception {
        Game g = session(SEED, GameMode.SURVIVAL);
        CompoundSites.Compound yard = nearest(g, SurvivorSites.Kind.RAIDERS);
        setClock(g, 12 * 60);
        standFacing(g, yard, street(yard, 8), yard.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite r = site(g, yard);
        for (Settlement.Resident p : r.residents) {
            if (p.live != null) { p.live.hurt(999, true); }
        }
        SurvivorWorldArena.run(g, 1f);
        assertTrue(r.cleared, "with every raider dead the yard is taken");
        assertFalse(r.hostile());
        assertEquals("Left behind", g.settlementManager.urbanSites.storageLabel(g, yard.members().getFirst()),
                "their stores are nobody's now");
        Path file = directory.resolve("taken.sav");
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        UrbanSite again = site(g, yard);
        assertTrue(again.cleared, "taken stays taken");
        standFacing(g, yard, street(yard, 8), yard.gate().outside());
        SurvivorWorldArena.slowTick(g);
        assertTrue(live(again).isEmpty(), "nobody comes back to the yard");
        // The nearest stronghold hears of it once.
        CompoundSites.Compound c = nearest(g, SurvivorSites.Kind.STRONGHOLD);
        nextDay(g, 10 * 60);
        standFacing(g, c, street(c, 1), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.run(g, 15f);
        UrbanSite s = site(g, c);
        Npc keeper = s.resident(0).live;
        assertTrue(g.npcScreen.siteOptions(g, keeper).contains("[8] Tell them the raider yard is taken"));
        float trust = s.localReputation;
        assertTrue(g.npcScreen.performSiteChoice(g, keeper, 8));
        assertEquals(trust + CompoundAccess.REWARD, s.localReputation, 1e-3f, "grateful");
        assertTrue(again.rewarded);
        assertFalse(g.npcScreen.performSiteChoice(g, keeper, 8), "and only once");
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        assertTrue(site(g, yard).rewarded, "the reward is remembered");
        assertNull(CompoundAccess.unpaidYard(g));
    }
}
