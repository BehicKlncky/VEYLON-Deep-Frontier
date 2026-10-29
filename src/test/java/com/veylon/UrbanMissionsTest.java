package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanMissionBoard;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanMissions.Failure;
import com.veylon.world.urban.UrbanMissions.Kind;
import com.veylon.world.urban.UrbanMissions.Mission;
import com.veylon.world.urban.UrbanMissions.Status;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The missions of a layout-sixteen city (urban milestone 20), through the conversation screen's own
 * command, the storage screen's transfers and the game's ticks: each offer names a real object still
 * there to be had, once in a world; at most four are in hand; only the deed on the named object after
 * accepting counts; a report rewards once, across saves and loads; the Wardens' module comes by price
 * or friendship and a theft fails its mission; and every failure is explicit and final.
 */
class UrbanMissionsTest {
    @TempDir Path directory;

    private record Camp(Game g, SurvivorSites.Site plan, UrbanSite site, Npc speaker) { }

    private static Camp camp(GameMode mode) {
        Game g = UrbanCommsTest.session(mode);
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        UrbanSite site = SurvivorWorldArena.site(g, plan);
        return new Camp(g, plan, site, site.resident(0).live);
    }

    private static String say(Game g, Npc n, int choice) {
        assertTrue(g.npcScreen.performSiteChoice(g, n, choice), "choice " + choice + " offered");
        return g.npcScreen.lastResponse();
    }

    private static UrbanMissions ledger(Game g) {
        return g.world.urban.missions;
    }

    private static Mission accepted(Game g, Kind kind) {
        for (Mission m : ledger(g).missions()) {
            if (m.kind == kind) { return m; }
        }
        throw new AssertionError("no " + kind + " mission in " + ledger(g).missions().size());
    }

    /** Accepts missions from a camp's speaker until the kind turns up; fails past the journal's room. */
    private static Mission acceptUntil(Camp c, Kind kind) {
        for (int i = 0; i < UrbanMissions.MAX_ACTIVE; i++) {
            say(c.g(), c.speaker(), 10);
            for (Mission m : ledger(c.g()).missions()) {
                if (m.kind == kind) { return m; }
            }
        }
        throw new AssertionError("the camp never offered " + kind);
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void aCampGivesItsMissionsInTurnEachNamingARealObjectOnlyOnce(GameMode mode) {
        Camp c = camp(mode);
        Game g = c.g();
        assertTrue(g.npcScreen.siteOptions(g, c.speaker()).contains("[0] Ask if they need help"));
        List<Kind> kinds = new ArrayList<>();
        for (int i = 0; i < UrbanMissions.MAX_ACTIVE; i++) {
            say(g, c.speaker(), 10);
            kinds.add(ledger(g).missions().getLast().kind);
        }
        assertEquals(List.of(Kind.CLINIC_SUPPLIES, Kind.HIDDEN_AID, Kind.LANDMARK, Kind.CAMP_UTILITY), kinds,
                "a camp's order, every object there to be had on this seed");
        assertEquals(UrbanMissions.MAX_ACTIVE, ledger(g).activeCount());
        assertEquals("Nothing we need from you right now.", say(g, c.speaker(), 10), "a camp gives each kind once");
        // The Wardens would give their module, but four missions are already in hand.
        Ward w = ward(g);
        assertNotNull(UrbanMissionBoard.offer(g, w.site()));
        String full = say(g, w.speaker(), 10);
        assertTrue(full.contains("4 of 4"), full);
        assertEquals(UrbanMissions.MAX_ACTIVE, ledger(g).missions().size(), "no fifth mission in hand");
        SurvivorWorldArena.standAt(g, c.plan().entry());
        // Each names its real object.
        BuildingPlan clinic = g.world.urban.buildingById(accepted(g, Kind.CLINIC_SUPPLIES).target);
        assertNotNull(clinic);
        assertEquals(BuildingPlan.Family.CLINIC, clinic.family());
        assertNotEquals(BuildingPlan.Variant.OCCUPIED, clinic.variant());
        assertNotEquals(BuildingPlan.Variant.PICKED_CLEAN, clinic.variant());
        assertEquals(SurvivorSites.Kind.REFUGE,
                SurvivorSites.resolve(g.world.urban, accepted(g, Kind.HIDDEN_AID).target).kind());
        assertEquals(g.world.urban.relay().building(), accepted(g, Kind.LANDMARK).target);
        assertTrue(ledger(g).comms.rumoured, "being sent to look at the mast is hearing of it");
        assertEquals(c.site().id, accepted(g, Kind.CAMP_UTILITY).target);
        for (Mission m : ledger(g).missions()) { assertEquals(c.site().id, m.giver); }
        assertTrue(UrbanMissionBoard.journalLine(g).startsWith("Missions 4/4 / "), UrbanMissionBoard.journalLine(g));
        assertNotNull(UrbanMissionBoard.target(g), "the first mission in hand points somewhere");
    }

    @Test
    void clinicMedicineCountsOnlyFromTheNamedClinicAfterAcceptingAndItsReportRewardsOnce() {
        Camp c = camp(GameMode.SURVIVAL);
        Game g = c.g();
        // Medicine taken from the clinic a camp would name, before accepting, counts for nothing later.
        UrbanMissionBoard.Offer offer = UrbanMissionBoard.offer(g, c.site());
        assertEquals(Kind.CLINIC_SUPPLIES, offer.kind());
        BuildingPlan named = g.world.urban.buildingById(offer.target());
        takeHealing(g, dispensary(g, named));
        SurvivorWorldArena.standAt(g, c.plan().entry());
        Mission m = acceptUntil(c, Kind.CLINIC_SUPPLIES);
        assertEquals(named.id(), m.target, "the dispensary still has medicine, so the same clinic is named");
        assertEquals(0f, m.progress, "a deed before accepting is not evidence");
        BuildingPlan.Fixture other = dispensary(g, otherClinic(g, named));
        takeHealing(g, other);
        assertEquals(Status.ACTIVE, m.status, "another clinic's medicine is not this clinic's");
        assertEquals(0f, m.progress);
        takeHealing(g, dispensary(g, named));
        assertEquals(Status.READY, m.status);
        assertTrue(g.npcScreen.siteOptions(g, c.speaker()).contains("[0] Report: " + Kind.CLINIC_SUPPLIES.title));
        // The report hands over the medicine and rewards once.
        g.player.inventory.clear();
        String wanting = say(g, c.speaker(), 10);
        assertTrue(wanting.contains("bring us"), wanting);
        assertEquals(Status.READY, m.status, "no medicine, no report");
        g.player.inventory.add(ItemType.BANDAGE, 3);
        float trust = c.site().localReputation;
        say(g, c.speaker(), 10);
        assertEquals(Status.COMPLETED, m.status);
        assertTrue(m.rewarded);
        assertEquals(trust + Kind.CLINIC_SUPPLIES.reward, c.site().localReputation, 1e-3f);
        assertEquals(1, g.player.inventory.count(ItemType.BANDAGE), "two dressings handed over");
        // Saved, loaded and reported again: nothing twice, and the clinic is never asked for again.
        Path file = directory.resolve("clinic.sav");
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        Mission loaded = accepted(g, Kind.CLINIC_SUPPLIES);
        assertEquals(Status.COMPLETED, loaded.status);
        assertTrue(loaded.rewarded);
        assertEquals(m.target, loaded.target);
        UrbanSite again = SurvivorWorldArena.site(g, c.plan());
        float after = again.localReputation;
        SurvivorWorldArena.standAt(g, c.plan().entry());
        SurvivorWorldArena.slowTick(g);
        say(g, again.resident(0).live, 10);
        assertEquals(after, again.localReputation, 1e-3f, "a load never pays a reward twice");
        assertEquals(1, ledger(g).missions().stream().filter(x -> x.kind == Kind.CLINIC_SUPPLIES).count());
    }

    @Test
    void theHiddenPeopleMissionGenuinelyVisitsTheirRefuge() {
        Camp c = camp(GameMode.SURVIVAL);
        Game g = c.g();
        Mission m = acceptUntil(c, Kind.HIDDEN_AID);
        SurvivorSites.Site refuge = SurvivorSites.resolve(g.world.urban, m.target);
        SurvivorWorldArena.standAt(g, SurvivorWorldArena.hall(g, refuge));
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.pressF(g, SurvivorWorldArena.panel(g, refuge));
        SurvivorWorldArena.standAt(g, refuge.entry());
        UrbanSite hidden = SurvivorWorldArena.site(g, refuge);
        assertTrue(hidden.discovered, "found behind its panel");
        Npc civilian = hidden.resident(0).live;
        g.player.inventory.clear();
        g.player.inventory.add(ItemType.DRIED_BERRY, 2);
        say(g, civilian, 2);
        assertEquals(2f, m.progress, 1e-4f, "two supplies left with them");
        assertEquals(Status.ACTIVE, m.status);
        g.player.inventory.add(ItemType.WATERSKIN_CLEAN, 2);
        say(g, civilian, 2);
        assertEquals(Status.READY, m.status, "three supplies and more");
        SurvivorWorldArena.standAt(g, c.plan().entry());
        SurvivorWorldArena.slowTick(g);
        float trust = c.site().localReputation;
        say(g, c.speaker(), 10);
        assertEquals(Status.COMPLETED, m.status);
        assertEquals(trust + Kind.HIDDEN_AID.reward, c.site().localReputation, 1e-3f);
    }

    @Test
    void theWardensModuleComesByPriceOrFriendshipAndATheftFailsItsMission() {
        // By price.
        Game g = UrbanCommsTest.session(GameMode.SURVIVAL);
        Ward w = ward(g);
        say(g, w.speaker(), 10);
        Mission m = accepted(g, Kind.STRONGHOLD_COMPONENT);
        assertEquals(CommsComponents.strongholdModule(g.world.urban, w.site().id), m.target);
        assertTrue(g.npcScreen.siteOptions(g, w.speaker()).contains("[0] Pay 6 supplies for the transmitter module"));
        g.player.inventory.clear();
        g.player.inventory.add(ItemType.DRIED_BERRY, 3);
        assertTrue(say(g, w.speaker(), 10).contains("Bring 6"), "not enough to pay");
        g.player.inventory.add(ItemType.WATERSKIN_CLEAN, 3);
        assertEquals("A fair price. Here.", say(g, w.speaker(), 10));
        assertEquals(Status.COMPLETED, m.status);
        assertEquals(1, g.player.inventory.count(ItemType.TRANSMITTER_MODULE));
        assertEquals(0, g.player.inventory.count(ItemType.DRIED_BERRY) + g.player.inventory.count(ItemType.WATERSKIN_CLEAN));
        Inventory store = g.world.crateContents.get(w.cell());
        assertTrue(store == null || store.count(ItemType.TRANSMITTER_MODULE) == 0, "it left their stores");
        assertTrue((ledger(g).comms.recovered & CommsComponents.Kind.TRANSMITTER.bit) != 0);

        // By friendship: a friend walks into their stores and takes it, then says so.
        Game f = UrbanCommsTest.session(GameMode.SURVIVAL);
        Ward friend = ward(f);
        say(f, friend.speaker(), 10);
        Mission fm = accepted(f, Kind.STRONGHOLD_COMPONENT);
        friend.site().localReputation = UrbanSite.FRIEND_TRUST + 5;
        friend.site().settleAlignment();
        takeModule(f, friend);
        assertEquals(Status.READY, fm.status, "a friend may take it");
        float trust = friend.site().localReputation;
        say(f, friend.speaker(), 10);
        assertEquals(Status.COMPLETED, fm.status);
        assertEquals(Math.min(100f, trust + Kind.STRONGHOLD_COMPONENT.reward), friend.site().localReputation, 1e-3f);

        // By theft: the module is the thief's, but the mission fails and the theft is judged.
        Game t = UrbanCommsTest.session(GameMode.SURVIVAL);
        Ward thief = ward(t);
        say(t, thief.speaker(), 10);
        Mission tm = accepted(t, Kind.STRONGHOLD_COMPONENT);
        float before = thief.site().localReputation;
        takeModule(t, thief);
        assertEquals(Status.FAILED, tm.status);
        assertEquals(Failure.THEFT, tm.failure);
        assertEquals(1, t.player.inventory.count(ItemType.TRANSMITTER_MODULE), "nothing is conjured or taken back");
        assertTrue(thief.site().localReputation < before || thief.site().missing > 0, "the stronghold's theft rules judge it");
        UrbanMissionBoard.Offer next = UrbanMissionBoard.offer(t, thief.site());
        assertTrue(next == null || next.kind() != Kind.STRONGHOLD_COMPONENT, "the module is never offered again");
    }

    @Test
    void failuresAreExplicitAndFinal() {
        // The givers dead.
        Camp c = camp(GameMode.SURVIVAL);
        Game g = c.g();
        Mission utility = acceptUntil(c, Kind.CAMP_UTILITY);
        for (Settlement.Resident r : c.site().residents) {
            if (r.live != null) { r.live.hurt(999, true); }
        }
        SurvivorWorldArena.run(g, 1f);
        assertEquals(0, c.site().present());
        for (Mission m : ledger(g).missions()) {
            assertEquals(Status.FAILED, m.status, m.kind + " with nobody to report to");
            assertEquals(Failure.GIVER_GONE, m.failure);
        }
        assertTrue(g.eventLog.all().stream().anyMatch(l -> l.contains("Mission failed: " + utility.kind.title)));
        assertEquals(0, ledger(g).activeCount(), "failed missions leave the journal");
        assertNull(UrbanMissionBoard.offer(g, c.site()), "the dead give nothing");

        // The givers turned hostile.
        Camp h = camp(GameMode.SURVIVAL);
        Mission landmark = acceptUntil(h, Kind.LANDMARK);
        h.site().localReputation = UrbanSite.HOSTILE_TRUST - 5;
        h.site().settleAlignment();
        SurvivorWorldArena.run(h.g(), 0.5f);
        assertEquals(Failure.GIVER_HOSTILE, landmark.failure);

        // The clinic's dispensary destroyed before its medicine was taken.
        Camp k = camp(GameMode.SURVIVAL);
        Mission clinic = acceptUntil(k, Kind.CLINIC_SUPPLIES);
        BuildingPlan named = k.g().world.urban.buildingById(clinic.target);
        for (BuildingPlan.Fixture f : named.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER
                    && BuildingFurnishing.containerUse(named, f) == BuildingFurnishing.ContainerUse.MEDICINE_CABINET) {
                k.g().world.ensureChunks(f.position().x(), f.position().z(), 1, 10_000);
                k.g().world.setBlock(f.position().x(), f.position().y(), f.position().z(), BlockType.AIR, true);
            }
        }
        SurvivorWorldArena.standAt(k.g(), k.plan().entry());
        SurvivorWorldArena.run(k.g(), 0.5f);
        assertEquals(Failure.TARGET_GONE, clinic.failure);

        // The hidden people dead before help came.
        Camp d = camp(GameMode.SURVIVAL);
        Mission aid = acceptUntil(d, Kind.HIDDEN_AID);
        SurvivorSites.Site refuge = SurvivorSites.resolve(d.g().world.urban, aid.target);
        SurvivorWorldArena.standAt(d.g(), SurvivorWorldArena.hall(d.g(), refuge));
        SurvivorWorldArena.slowTick(d.g());
        SurvivorWorldArena.pressF(d.g(), SurvivorWorldArena.panel(d.g(), refuge));
        UrbanSite hidden = SurvivorWorldArena.site(d.g(), refuge);
        for (Settlement.Resident r : hidden.residents) {
            if (r.live != null) { r.live.hurt(999, true); }
        }
        SurvivorWorldArena.run(d.g(), 1f);
        assertEquals(Failure.TARGET_DEAD, aid.failure);
        UrbanMissionBoard.Offer again = UrbanMissionBoard.offer(d.g(), d.site());
        assertTrue(again == null || again.kind() != Kind.HIDDEN_AID, "no new mission for the same people");
    }

    @Test
    void theLandmarkCountsOnlyTheSurvivorsBodyOnTheRoofAfterAccepting() {
        Camp c = camp(GameMode.SURVIVAL);
        Game g = c.g();
        Mission m = acceptUntil(c, Kind.LANDMARK);
        var relay = g.world.urban.relay();
        SurvivorWorldArena.standAt(g, relay.stand().offset(0, -4, 0));
        SurvivorWorldArena.run(g, 0.5f);
        assertEquals(Status.ACTIVE, m.status, "the storey below the roof is not the roof");
        SurvivorWorldArena.standAt(g, relay.stand());
        SurvivorWorldArena.run(g, 0.5f);
        assertEquals(Status.READY, m.status);
        assertTrue(ledger(g).comms.relayFound);
        // Once the relay is found, nobody sends anyone to look at it.
        Camp other = camp(GameMode.SURVIVAL);
        SurvivorWorldArena.standAt(other.g(), other.g().world.urban.relay().stand());
        SurvivorWorldArena.run(other.g(), 0.5f);
        SurvivorWorldArena.standAt(other.g(), other.plan().entry());
        SurvivorWorldArena.slowTick(other.g());
        for (int i = 0; i < UrbanMissions.MAX_ACTIVE; i++) { say(other.g(), other.speaker(), 10); }
        assertTrue(ledger(other.g()).missions().stream().noneMatch(x -> x.kind == Kind.LANDMARK));
    }

    @Test
    void campUtilityCountsOnlyWaterTreatedOnTheCampsOwnWiring() {
        Camp c = camp(GameMode.SURVIVAL);
        Game g = c.g();
        Mission m = acceptUntil(c, Kind.CAMP_UTILITY);
        UrbanMissionBoard.onTreated(g, RefugeWorldArena.neighbour(g).id(), 5f);
        assertEquals(0f, m.progress, "water treated in another building");
        UrbanMissionBoard.onTreated(g, c.site().id, 1.5f);
        assertEquals(Status.ACTIVE, m.status);
        UrbanMissionBoard.onTreated(g, c.site().id, 1f);
        assertEquals(Status.READY, m.status);
        assertEquals(Kind.CAMP_UTILITY.required, m.progress, 1e-4f);
    }

    @Test
    void earlierCitiesGiveNoMissions() {
        Game g = SurvivorWorldArena.session(SurvivorWorldArena.SEED, GameMode.SURVIVAL, World.GEN_URBAN_UTILITIES,
                new AudioManager());
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        Npc lookout = SurvivorWorldArena.site(g, plan).resident(0).live;
        assertFalse(g.npcScreen.siteOptions(g, lookout).stream().anyMatch(o -> o.startsWith("[0]")));
        assertFalse(g.npcScreen.performSiteChoice(g, lookout, 10));
        assertNull(UrbanMissionBoard.journalLine(g));
        assertNull(g.faction.quest, "the legacy request slot is untouched");
    }

    // ------------------------------------------------------------------

    private record Ward(UrbanSite site, Npc speaker, CompoundSites.Compound compound, Vec3i cell) { }

    private static Ward ward(Game g) {
        CompoundSites.Compound c = CompoundWorldArena.nearest(g, SurvivorSites.Kind.STRONGHOLD);
        CompoundWorldArena.load(g, c);
        CompoundWorldArena.standFacing(g, c, CompoundWorldArena.street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite s = CompoundWorldArena.site(g, c);
        return new Ward(s, CompoundWorldArena.live(s).getFirst(), c, CommsComponents.strongholdModuleCell(g.world.urban, c));
    }

    /** Walks into the Wardens' stores and takes their module through the storage screen. */
    private static void takeModule(Game g, Ward w) {
        BuildingPlan holder = g.world.urban.buildingAt(w.cell().x(), w.cell().z());
        Vec3i front = holder.fixtures().stream().filter(f -> f.position().equals(w.cell())).findFirst().orElseThrow().target();
        SurvivorWorldArena.standAt(g, front);
        assertTrue(g.openCrateAt(w.cell()));
        SurvivorWorldArena.run(g, 4f);
        Inventory crate = g.world.crateContents.get(w.cell());
        assertNotNull(crate);
        for (int i = 0; i < crate.size(); i++) {
            ItemStack s = crate.get(i);
            if (s != null && s.type == ItemType.TRANSMITTER_MODULE) {
                assertEquals(1, g.transferCrateItemToPlayer(i));
                g.closeScreens();
                return;
            }
        }
        throw new AssertionError("no module in the Wardens' stores");
    }

    /** A clinic's first dispensary cabinet. */
    private static BuildingPlan.Fixture dispensary(Game g, BuildingPlan clinic) {
        for (BuildingPlan.Fixture f : clinic.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER
                    && BuildingFurnishing.containerUse(clinic, f) == BuildingFurnishing.ContainerUse.MEDICINE_CABINET) {
                return f;
            }
        }
        throw new AssertionError("no dispensary in " + clinic.id());
    }

    /** Another unheld clinic with a dispensary, not picked clean, near the named one. */
    private static BuildingPlan otherClinic(Game g, BuildingPlan named) {
        int rx = com.veylon.world.urban.UrbanIds.regionX(named.id()), rz = com.veylon.world.urban.UrbanIds.regionZ(named.id());
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (ParcelPlan p : g.world.urban.regionPlan(rx + dx, rz + dz).parcels()) {
                    BuildingPlan b = g.world.urban.buildingAt(p.x0(), p.z0());
                    if (b != null && b.id() != named.id() && b.family() == BuildingPlan.Family.CLINIC
                            && b.variant() != BuildingPlan.Variant.OCCUPIED && b.variant() != BuildingPlan.Variant.PICKED_CLEAN
                            && SurvivorSites.owner(g.world.urban, b.id()) == null) {
                        return b;
                    }
                }
            }
        }
        throw new AssertionError("no second clinic near " + named.id());
    }

    /** Searches a container and takes its first dressing or medicine through the storage screen. */
    private static void takeHealing(Game g, BuildingPlan.Fixture f) {
        SurvivorWorldArena.standAt(g, f.target());
        assertTrue(g.openCrateAt(f.position()));
        SurvivorWorldArena.run(g, 4f);
        Inventory crate = g.world.crateContents.get(f.position());
        assertNotNull(crate, "searched");
        for (int i = 0; i < crate.size(); i++) {
            ItemStack s = crate.get(i);
            if (s != null && (s.type == ItemType.BANDAGE || s.type == ItemType.MEDICINE || s.type == ItemType.ANTISEPTIC)) {
                assertTrue(g.transferCrateItemToPlayer(i) > 0);
                g.closeScreens();
                return;
            }
        }
        throw new AssertionError("no medicine in the dispensary");
    }
}
