package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.settlement.UrbanComms;
import com.veylon.settlement.UrbanMissionBoard;
import com.veylon.settlement.UrbanSite;
import com.veylon.ui.QuestObjectiveView;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.SuperblockPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanMissions.Kind;
import com.veylon.world.urban.UrbanMissions.Mission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What survivors tell is a rumour until the survivor confirms it (urban milestone 21): a mission to
 * help hidden people marks their city block, never their building, until their cellar is found; the
 * old relay is marked by its district until the survivor stands beside it; and the navigation line
 * says which is which. A stronghold given supplies is not called a camp.
 */
class UrbanMissionRumourTest {
    private record Camp(Game g, SurvivorSites.Site plan, UrbanSite site, Npc speaker) { }

    private static Camp camp() {
        Game g = UrbanCommsTest.session(GameMode.SURVIVAL);
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        UrbanSite site = SurvivorWorldArena.site(g, plan);
        return new Camp(g, plan, site, site.resident(0).live);
    }

    private static Mission acceptUntil(Camp c, Kind kind) {
        for (int i = 0; i < UrbanMissions.MAX_ACTIVE; i++) {
            assertTrue(c.g().npcScreen.performSiteChoice(c.g(), c.speaker(), 10));
            for (Mission m : c.g().world.urban.missions.missions()) {
                if (m.kind == kind) { return m; }
            }
        }
        throw new AssertionError("the camp never offered " + kind);
    }

    private static QuestObjectiveView.Objective objective(UrbanComms.Target t) {
        return new QuestObjectiveView.Objective(t.id(), t.id(), t.position(), QuestObjectiveView.Phase.TARGET,
                t.exact(), t.label());
    }

    @Test
    void hiddenPeopleAreARumourOfTheirBlockUntilTheirCellarIsFound() {
        Camp c = camp();
        Game g = c.g();
        Mission m = acceptUntil(c, Kind.HIDDEN_AID);
        SurvivorSites.Site refuge = SurvivorSites.resolve(g.world.urban, m.target);
        BuildingPlan building = g.world.urban.buildingById(refuge.id());
        UrbanComms.Target rumour = UrbanMissionBoard.target(g, m);
        assertNotNull(rumour);
        assertFalse(rumour.exact(), "the camp's word is not a find");
        assertFalse(building.parcel().contains(rumour.position().x(), rumour.position().z()),
                "the marker never stands on the building above them");
        SuperblockPlan block = g.world.urban.regionPlan(UrbanIds.regionX(building.id()), UrbanIds.regionZ(building.id()))
                .superblocks().stream().filter(b -> b.index() == building.parcel().superblock()).findFirst().orElseThrow();
        assertTrue(block.bounds().contains(rumour.position().x(), rumour.position().z()), "it marks their block");
        var parcel = building.parcel();
        assertTrue(block.bounds().contains(parcel.x0(), parcel.z0()) && block.bounds().contains(parcel.x1(), parcel.z1()),
                "and the block holds their building");
        String line = QuestObjectiveView.navigationLabel(g, objective(rumour));
        assertTrue(line.startsWith(Kind.HIDDEN_AID.title + " - ~") && line.contains("(rumour)"), line);

        SurvivorWorldArena.standAt(g, SurvivorWorldArena.hall(g, refuge));
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.pressF(g, SurvivorWorldArena.panel(g, refuge));
        assertTrue(g.world.urban.discovery.discovered(refuge.room()));
        UrbanComms.Target found = UrbanMissionBoard.target(g, m);
        assertTrue(found.exact(), "found, their place is known exactly");
        assertEquals(refuge.anchor(), found.position());
        String exact = QuestObjectiveView.navigationLabel(g, objective(found));
        assertFalse(exact.contains("~") || exact.contains("(rumour)"), exact);
    }

    @Test
    void theOldRelayIsARumourOfItsDistrictUntilTheSurvivorStandsBesideIt() {
        Camp c = camp();
        Game g = c.g();
        Mission m = acceptUntil(c, Kind.LANDMARK);
        var relay = g.world.urban.relay();
        UrbanComms.Target rumour = UrbanMissionBoard.target(g, m);
        assertFalse(rumour.exact());
        assertEquals(relay.rx() * StreetNetwork.REGION_SIZE + StreetNetwork.REGION_SIZE / 2, rumour.position().x());
        assertEquals(relay.rz() * StreetNetwork.REGION_SIZE + StreetNetwork.REGION_SIZE / 2, rumour.position().z());
        // Asked about, the relay itself is a rumour of its district on the comms line too.
        UrbanComms.Target comms = UrbanComms.target(g);
        assertFalse(comms.exact());
        assertTrue(QuestObjectiveView.navigationLabel(g, objective(comms)).contains("(rumour)"));

        SurvivorWorldArena.standAt(g, relay.stand());
        SurvivorWorldArena.run(g, .5f);
        assertTrue(g.world.urban.missions.comms.relayFound);
        assertEquals(UrbanMissions.Status.READY, m.status);
        UrbanComms.Target report = UrbanMissionBoard.target(g, m);
        assertTrue(report.exact(), "a ready mission points at its givers, a place the survivor knows");
        assertEquals(c.site().homeAnchor(g.world.urban), report.position());
        assertTrue(UrbanComms.target(g).exact(), "the relay found is a place, not a rumour");
    }

    @Test
    void theWardensAreNotCalledACampWhenTheyTakeSupplies() {
        Game g = UrbanCommsTest.session(GameMode.SURVIVAL);
        CompoundSites.Compound c = CompoundWorldArena.nearest(g, SurvivorSites.Kind.STRONGHOLD);
        CompoundWorldArena.load(g, c);
        CompoundWorldArena.standFacing(g, c, CompoundWorldArena.street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite ward = CompoundWorldArena.site(g, c);
        Npc speaker = CompoundWorldArena.live(ward).getFirst();
        g.player.inventory.clear();
        g.player.inventory.add(ItemType.DRIED_BERRY, 2);
        assertTrue(g.npcScreen.performSiteChoice(g, speaker, 2));
        var lines = g.eventLog.recent(6);
        assertTrue(lines.stream().anyMatch(l -> l.endsWith("The Wardens take in your supplies.")), String.join(" | ", lines));
        assertFalse(lines.stream().anyMatch(l -> l.contains("The camp takes in")), String.join(" | ", lines));
    }
}
