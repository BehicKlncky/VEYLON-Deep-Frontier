package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.veylon.SurvivorWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * A safe move for hidden civilians (urban milestone 16): they agree only when they trust the
 * player and know of a camp that would have them, wait to leave unseen, travel off screen, and on
 * arrival live in the camp's spare beds under their own ids; the move, the empty cellar and the
 * stash left to the player all survive a save and a load.
 */
class UrbanSiteRelocationTest {
    @TempDir Path directory;

    @Test
    void hiddenCiviliansMoveToAKnownCampAndLiveInItsSpareBedsUnderTheirOwnIds() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site campPlan = nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorSites.Site refugePlan = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, campPlan.entry());
        slowTick(g);
        UrbanSite camp = site(g, campPlan);
        assertTrue(camp.discovered);
        standAt(g, hall(g, refugePlan));
        slowTick(g);
        pressF(g, panel(g, refugePlan));
        UrbanSite refuge = site(g, refugePlan);
        assertTrue(refuge.discovered);

        assertEquals("We don't know you well enough to follow you anywhere.", UrbanSiteRelocation.refusal(g, refuge, camp));
        refuge.localReputation = UrbanSiteRelocation.TRUST_TO_MOVE;
        refuge.settleAlignment();
        assertNull(UrbanSiteRelocation.refusal(g, refuge, camp), "they trust the player and know where to go");
        assertTrue(UrbanSiteRelocation.guide(g, refuge, camp));
        assertEquals(UrbanSite.Relocation.DEPARTING, refuge.relocation);
        assertTrue(refuge.stashReleased);
        slowTick(g);
        assertEquals(UrbanSite.Relocation.DEPARTING, refuge.relocation, "nobody leaves while the player watches");
        assertFalse(live(refuge).isEmpty());

        // The player goes; the cellar's people go dormant and set out.
        standAt(g, refugePlan.anchor().offset(0, 0, 400));
        slowTick(g);
        assertEquals(UrbanSite.Relocation.TRAVELLING, refuge.relocation);
        assertTrue(live(refuge).isEmpty());
        float campTrust = camp.localReputation;
        for (int hour = 0; hour < 12 && refuge.relocation == UrbanSite.Relocation.TRAVELLING; hour++) {
            g.time.totalMinutes += 60;
            slowTick(g);
        }
        assertEquals(UrbanSite.Relocation.ARRIVED, refuge.relocation, "they reached the camp");
        assertEquals(campTrust + UrbanSiteRelocation.TAKEN_IN, camp.localReputation, 1e-3f, "and the camp knows who sent them");

        standAt(g, campPlan.entry());
        slowTick(g);
        List<Npc> guests = live(refuge);
        assertEquals(refuge.present(), guests.size(), "the same people now live at the camp");
        for (Npc n : guests) {
            assertEquals(refuge.id, n.settlementId, "under their own refuge's ids");
            assertEquals(refugePlan.resident(n.residentIndex).name(), n.name);
            Vec3i at = feet(n);
            assertTrue(campPlan.guestBeds().contains(at) || campPlan.holds(at.x(), at.y(), at.z()),
                    n.name + " lives in the camp: " + at);
        }

        Path file = directory.resolve("moved.sav");
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        UrbanSite loaded = UrbanSiteDirector.registered(g.world, refugePlan.id());
        assertEquals(UrbanSite.Relocation.ARRIVED, loaded.relocation);
        assertEquals(campPlan.id(), loaded.relocatedTo);
        assertEquals(guests.size(), live(loaded).size(), "still at the camp after a load");

        // Their cellar is empty, and what they left is the player's to take.
        standAt(g, hall(g, refugePlan));
        slowTick(g);
        Vec3i stash = refugePlan.stash().getFirst();
        UrbanContainers.Entry entry = g.world.urban.containers.touch(g.world, stash);
        assertEquals(UrbanContainers.Owner.SURVIVOR, entry.owner);
        assertTrue(g.settlementManager.urbanSites.onStorageTaken(g, entry.ownerId, 3), "the site answers for it");
        assertEquals(0, loaded.owed, "and asks nothing back: it was left to the player");
    }

    @Test
    void theyStayWhenTheCampIsHostileOrFull() {
        Game g = session(SEED, GameMode.SURVIVAL);
        SurvivorSites.Site campPlan = nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorSites.Site refugePlan = nearest(g, SurvivorSites.Kind.REFUGE);
        standAt(g, campPlan.entry());
        slowTick(g);
        UrbanSite camp = site(g, campPlan);
        standAt(g, hall(g, refugePlan));
        slowTick(g);
        pressF(g, panel(g, refugePlan));
        UrbanSite refuge = site(g, refugePlan);
        refuge.localReputation = 60f;
        refuge.settleAlignment();
        camp.localReputation = -45f;
        camp.settleAlignment();
        assertTrue(camp.hostile());
        assertEquals("Those people would never take us in.", UrbanSiteRelocation.refusal(g, refuge, camp));
        camp.localReputation = 0f;
        camp.settleAlignment();
        refuge.fear = 80f;
        assertEquals("Not now. Not like this.", UrbanSiteRelocation.refusal(g, refuge, camp));
        refuge.fear = 0f;
        assertNull(UrbanSiteRelocation.refusal(g, refuge, camp));
        camp.discovered = false;
        assertEquals("We don't know of anywhere safe.", UrbanSiteRelocation.refusal(g, refuge, camp),
                "only a camp the player has found can be vouched for");
    }
}
