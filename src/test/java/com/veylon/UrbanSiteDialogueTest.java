package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDialogue;
import com.veylon.world.urban.SurvivorSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;

import static com.veylon.SurvivorWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Conversations with survivor-site people (urban milestone 16) through the screen's own command
 * seam: a camp trader deals from real, finite stores; hidden civilians move to a known camp only
 * once aid has earned their trust; a threat robs or turns them, and a betrayal lasts; a friendly
 * camp's medic dresses wounds once a day. Items move in Creative exactly as in Survival.
 */
class UrbanSiteDialogueTest {
    @TempDir Path directory;

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void aCampTraderDealsFromRealStoresUntilTheyRunOutAndALoadRefillsNothing(GameMode mode) {
        Game g = session(SEED, mode);
        SurvivorSites.Site plan = nearest(g, SurvivorSites.Kind.CAMP);
        standAt(g, plan.entry());
        slowTick(g);
        UrbanSite s = site(g, plan);
        Npc trader = s.resident(1).live;
        List<UrbanSiteDialogue.Offer> offers = UrbanSiteDialogue.offers(g, trader);
        assertEquals(2, offers.size(), "a trader keeps two standing offers");
        assertTrue(UrbanSiteDialogue.offers(g, s.resident(0).live).isEmpty(), "the lookout does not trade");
        UrbanSiteDialogue.Offer o = offers.getFirst();
        g.player.inventory.clear();
        g.player.inventory.add(o.give(), o.giveCount() * 40);
        int trades = 0;
        float trust = s.localReputation;
        String last = "";
        for (int i = 0; i < 40; i++) {
            assertTrue(g.npcScreen.performSiteChoice(g, trader, 4));
            last = g.npcScreen.lastResponse();
            if (!last.equals("A fair trade.")) { break; }
            trades++;
        }
        assertTrue(trades > 0, "at least one trade: " + last);
        assertEquals("We're out of " + o.get().displayName + ".", last, "the stores run out");
        assertEquals(trades * o.getCount(), g.player.inventory.count(o.get()), "what was bought arrived");
        assertEquals(o.giveCount() * (40 - trades), g.player.inventory.count(o.give()), "and what paid for it left, in " + mode);
        assertEquals(trust + trades, s.localReputation, 1e-3f, "fair dealing earns a little trust");

        Path file = directory.resolve("trade-" + mode + ".sav");
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        UrbanSite loaded = site(g, plan);
        Npc again = loaded.resident(1).live;
        assertNotNull(again);
        assertTrue(g.npcScreen.performSiteChoice(g, again, 4));
        assertEquals("We're out of " + o.get().displayName + ".", g.npcScreen.lastResponse(),
                "a load refills nothing, so no reward is had twice");
    }

    @Test
    void hiddenCiviliansMoveToAKnownCampOnlyOnceAidHasEarnedTheirTrust() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site camp = nearest(g, SurvivorSites.Kind.CAMP), refuge = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, hall(g, refuge));
        slowTick(g);
        pressF(g, panel(g, refuge));
        standAt(g, refuge.entry());
        UrbanSite r = site(g, refuge);
        Npc speaker = live(r).getFirst();
        assertTrue(g.npcScreen.siteOptions(g, speaker).contains("[4] Tell them of a safe place (you know none)"));
        g.npcScreen.performSiteChoice(g, speaker, 4);
        assertEquals("We don't know you well enough to follow you anywhere.", g.npcScreen.lastResponse());

        // Meet the camp, then come back.
        standAt(g, camp.entry());
        slowTick(g);
        standAt(g, refuge.entry());
        slowTick(g);
        speaker = live(r).getFirst();
        assertTrue(g.npcScreen.siteOptions(g, speaker).contains("[4] Tell them about the camp you found"));
        g.npcScreen.performSiteChoice(g, speaker, 4);
        assertEquals("We don't know you well enough to follow you anywhere.", g.npcScreen.lastResponse(),
                "knowing a camp is not enough without their trust");
        for (int i = 0; i < 3; i++) {
            g.player.inventory.add(ItemType.DRIED_BERRY, 3);
            g.player.inventory.add(ItemType.WATERSKIN_CLEAN, 3);
            g.npcScreen.performSiteChoice(g, speaker, 2);
        }
        assertTrue(r.localReputation >= 30f, "aid earned their trust: " + r.localReputation);
        g.npcScreen.performSiteChoice(g, speaker, 4);
        assertEquals(UrbanSite.Relocation.DEPARTING, r.relocation, g.npcScreen.lastResponse());
        assertEquals(camp.id(), r.relocatedTo);
    }

    @Test
    void aThreatRobsHiddenCiviliansTurnsACampAndABetrayalLasts() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site campPlan = nearest(g, SurvivorSites.Kind.CAMP), refugePlan = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, hall(g, refugePlan));
        slowTick(g);
        pressF(g, panel(g, refugePlan));
        standAt(g, refugePlan.entry());
        UrbanSite r = site(g, refugePlan);
        r.localReputation = 45f;
        r.settleAlignment();
        Npc civilian = live(r).getFirst();
        g.player.inventory.clear();
        g.npcScreen.performSiteChoice(g, civilian, 6);
        int handed = carried(g);
        assertTrue(handed > 0 && handed <= UrbanSiteDialogue.DEMANDED, "they hand over what they have: " + handed);
        assertEquals("Take it! Just take it and go...", g.npcScreen.lastResponse());
        assertTrue(r.betrayed, "they trusted the player: that was a betrayal");
        assertTrue(r.localReputation <= UrbanSite.BETRAYED_CAP);
        assertEquals(100f, r.fear, 1e-4f);
        assertEquals(handed, r.owed, "what was taken is owed");
        assertFalse(r.hostile(), "hidden civilians never fight");
        r.localReputation = 90f; // nothing can buy that trust back above the cap
        r.settleAlignment();
        assertEquals(UrbanSite.BETRAYED_CAP, r.localReputation, 1e-4f);

        standAt(g, campPlan.entry());
        slowTick(g);
        UrbanSite c = site(g, campPlan);
        g.npcScreen.performSiteChoice(g, c.resident(1).live, 6);
        assertEquals("You'll have to come through us.", g.npcScreen.lastResponse());
        assertTrue(c.hostile(), "a camp refuses a threat and turns on the player");
    }

    @Test
    void aFriendlyCampsMedicDressesWoundsOnceADay() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site plan = campWithMedic(g);
        standAt(g, plan.entry());
        slowTick(g);
        UrbanSite s = site(g, plan);
        Npc medic = s.resident(2).live;
        assertEquals(SurvivorSites.Role.MEDIC, s.resident(2).role);
        g.player.health = 40f;
        g.npcScreen.performSiteChoice(g, medic, 4);
        assertEquals("We save our dressings for our own.", g.npcScreen.lastResponse());
        assertEquals(40f, g.player.health, 1e-4f);
        s.localReputation = 45f;
        s.settleAlignment();
        assertTrue(s.grantsAccess());
        g.npcScreen.performSiteChoice(g, medic, 4);
        assertEquals(40f + UrbanSiteDialogue.TREATMENT, g.player.health, 1e-4f);
        g.npcScreen.performSiteChoice(g, medic, 4);
        assertEquals("I've done what I can for today. Come back tomorrow.", g.npcScreen.lastResponse());
        g.time.totalMinutes += 24 * 60;
        g.npcScreen.performSiteChoice(g, medic, 4);
        assertEquals(40f + 2 * UrbanSiteDialogue.TREATMENT, g.player.health, 1e-4f, "and again the next day");
    }

    /** Everything the player carries, counted item by item. */
    private static int carried(Game g) {
        int n = 0;
        for (int i = 0; i < g.player.inventory.size(); i++) {
            ItemStack s = g.player.inventory.get(i);
            if (s != null) { n += s.count; }
        }
        return n;
    }

    /** The camp nearest the spawn whose people number four or more, so it keeps a medic. */
    private static SurvivorSites.Site campWithMedic(Game g) {
        SurvivorSites.Site best = null;
        int[] spawn = g.world.urban.spawnColumn();
        for (int rz = -2; rz <= 2; rz++) {
            for (int rx = -2; rx <= 2; rx++) {
                SurvivorSites.Site c = g.world.urban.sites(rx, rz).camp();
                if (c == null || c.residents().size() < 4) { continue; }
                if (best == null || c.anchor().distSq(spawn[0], c.anchor().y(), spawn[1])
                        < best.anchor().distSq(spawn[0], best.anchor().y(), spawn[1])) { best = c; }
            }
        }
        assertNotNull(best, "a camp with a medic near the spawn");
        return best;
    }
}
