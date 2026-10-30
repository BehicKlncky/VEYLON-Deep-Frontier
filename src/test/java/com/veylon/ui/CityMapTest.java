package com.veylon.ui;

import com.veylon.Game;
import com.veylon.SurvivorWorldArena;
import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.UrbanMissionBoard;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.DistrictCatalogue;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The city map of a layout-sixteen world (urban milestone 21) shows what the survivor knows and
 * nothing more: home and the district they woke in, the buildings they entered by name, camps once
 * found, hidden people only once their cellar is found, rumours as rough areas, and a journal read
 * from the saved ledger, so a load shows exactly what is true. Earlier worlds keep their map.
 */
class CityMapTest {
    private static final long SEED = SurvivorWorldArena.SEED;
    private static final Set<CityMap.Kind> SITES = Set.of(CityMap.Kind.CAMP, CityMap.Kind.STRONGHOLD,
            CityMap.Kind.RAIDERS, CityMap.Kind.HIDDEN);
    @TempDir Path directory;

    static Game city(GameMode mode) {
        Game g = SurvivorWorldArena.session(SEED, mode, World.GEN_URBAN_MISSIONS, new AudioManager());
        SurvivorWorldArena.run(g, .2f);
        return g;
    }

    private static List<CityMap.Building> entered(Game g) {
        int x = (int) g.player.pos.x, z = (int) g.player.pos.z;
        return CityMap.enteredBuildings(g.world, x - 400, z - 400, x + 400, z + 400);
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void aNewSurvivorSeesHomeAndTheirRefugeAndNothingTheyHaveNotFound(GameMode mode) {
        Game g = city(mode);
        assertTrue(CityMap.applies(g.world));
        List<CityMap.Marker> markers = CityMap.markers(g);
        List<CityMap.Marker> homes = markers.stream().filter(m -> m.kind() == CityMap.Kind.HOME).toList();
        assertEquals(1, homes.size(), "the starter refuge is home: " + markers);
        assertEquals(g.world.urban.refuges.home().sleep, homes.getFirst().position());
        assertTrue(markers.stream().noneMatch(m -> SITES.contains(m.kind())), "nothing found yet: " + markers);
        assertTrue(markers.stream().noneMatch(m -> m.kind() == CityMap.Kind.OBJECTIVE || m.kind() == CityMap.Kind.RUMOUR),
                "no objective before anyone speaks of the relay");
        List<CityMap.Building> named = entered(g);
        assertEquals(1, named.size(), "only the refuge they woke in is named: " + named);
        assertEquals(g.world.urban.refugeBuilding, named.getFirst().id());
        assertEquals("Flats", named.getFirst().name());
        assertEquals("Flats · ground floor · your refuge", CityMap.hereLine(g));
        assertEquals("Home: you are there", CityMap.homeLine(g));
        List<CityMap.Entry> journal = CityMap.journal(g);
        assertEquals(CityMap.State.COMMS, journal.getFirst().state());
        assertEquals("Ask survivors about the old radio relay", journal.getFirst().detail());
        assertEquals(CityMap.State.HINT, journal.getLast().state(), "with nothing in hand the journal says where work is");
    }

    @Test
    void enteringABuildingNamesItAndThePlanAgreesWithTheName() {
        Game g = city(GameMode.SURVIVAL);
        SurvivorSites.Site camp = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        BuildingPlan building = SurvivorWorldArena.building(g, camp);
        assertTrue(entered(g).stream().noneMatch(b -> b.id() == building.id()), "a building seen is not a building entered");
        BuildingPlan.Floor ground = building.floors().stream().filter(f -> f.index() == 0).findFirst().orElseThrow();
        SurvivorWorldArena.standAt(g, ground.rooms().getFirst().anchor());
        SurvivorWorldArena.run(g, .2f);
        CityMap.Building named = entered(g).stream().filter(b -> b.id() == building.id()).findFirst().orElseThrow();
        assertEquals(CityMap.familyName(building.family()), named.name(), "the cheap family is the plan's own");
        ParcelPlan p = building.parcel();
        assertEquals(CityMap.familyName(DistrictCatalogue.family(g.world.seed,
                g.world.urban.regionPlan(UrbanIds.regionX(building.id()), UrbanIds.regionZ(building.id())), p)), named.name());
        assertTrue(CityMap.hereLine(g).startsWith(CityMap.familyName(building.family()) + " · ground floor"), CityMap.hereLine(g));
    }

    @Test
    void hiddenPeopleStayOffTheMapUntilTheirCellarIsFound() {
        Game g = city(GameMode.SURVIVAL);
        SurvivorSites.Site refuge = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.REFUGE);
        SurvivorWorldArena.standAt(g, SurvivorWorldArena.hall(g, refuge));
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.run(g, 2f);
        assertNotNull(SurvivorWorldArena.site(g, refuge), "their site is registered nearby");
        assertTrue(CityMap.markers(g).stream().noneMatch(m -> m.kind() == CityMap.Kind.HIDDEN),
                "standing in the basement hall reveals nobody");
        assertTrue(entered(g).stream().anyMatch(b -> b.id() == refuge.id()), "the building above them was entered");
        SurvivorWorldArena.pressF(g, SurvivorWorldArena.panel(g, refuge));
        CityMap.Marker hidden = CityMap.markers(g).stream().filter(m -> m.kind() == CityMap.Kind.HIDDEN)
                .findFirst().orElseThrow(() -> new AssertionError("found behind their panel, they are on the map"));
        assertEquals(refuge.anchor(), hidden.position());
        assertEquals("Hidden refuge", hidden.label());
    }

    @Test
    void aFoundCampItsRumourAndItsMissionsFillTheMapAndJournalAcrossALoad() throws Exception {
        Game g = city(GameMode.SURVIVAL);
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.run(g, .2f);
        UrbanSite camp = SurvivorWorldArena.site(g, plan);
        assertTrue(camp.discovered);
        assertTrue(CityMap.markers(g).stream().anyMatch(m -> m.kind() == CityMap.Kind.CAMP
                && m.position().equals(plan.anchor()) && m.label().startsWith("Camp")), "a found camp is marked");

        var speaker = camp.resident(0).live;
        assertTrue(g.npcScreen.performSiteChoice(g, speaker, 9));
        CityMap.Marker relay = CityMap.markers(g).stream().filter(m -> m.kind() == CityMap.Kind.RUMOUR)
                .findFirst().orElseThrow(() -> new AssertionError("the relay is a rumour"));
        assertTrue(relay.radius() > 0 && relay.tracked(), relay.toString());
        assertTrue(g.npcScreen.performSiteChoice(g, speaker, 10));
        assertTrue(g.npcScreen.performSiteChoice(g, speaker, 10));
        List<UrbanMissions.Mission> missions = g.world.urban.missions.missions();
        assertEquals(List.of(UrbanMissions.Kind.CLINIC_SUPPLIES, UrbanMissions.Kind.HIDDEN_AID),
                missions.stream().map(m -> m.kind).toList());
        List<CityMap.Entry> journal = CityMap.journal(g);
        assertEquals(List.of(CityMap.State.COMMS, CityMap.State.OPEN, CityMap.State.OPEN),
                journal.stream().map(CityMap.Entry::state).toList(), journal.toString());
        assertTrue(journal.get(1).detail().startsWith("Take medicine from the"), journal.get(1).toString());
        assertTrue(journal.get(1).where().startsWith("For the camp · "), journal.get(1).where());
        assertTrue(journal.get(2).where().contains("(rumour)"), "the people below are a rumour: " + journal.get(2).where());
        assertTrue(CityMap.markers(g).stream().anyMatch(m -> m.kind() == CityMap.Kind.RUMOUR
                && m.label().equals(UrbanMissions.Kind.HIDDEN_AID.title) && m.radius() > 0));

        // The clinic's dispensary destroyed: the mission fails for good, and the journal says why.
        BuildingPlan clinic = g.world.urban.buildingById(missions.getFirst().target);
        for (BuildingPlan.Fixture f : clinic.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER
                    && BuildingFurnishing.containerUse(clinic, f) == BuildingFurnishing.ContainerUse.MEDICINE_CABINET) {
                Vec3i p = f.position();
                g.world.ensureChunks(p.x(), p.z(), 1, 10_000);
                g.world.setBlock(p.x(), p.y(), p.z(), BlockType.AIR, true);
            }
        }
        UrbanMissionBoard.mediumTick(g, 1f);
        assertEquals(UrbanMissions.Status.FAILED, missions.getFirst().status);
        journal = CityMap.journal(g);
        CityMap.Entry failed = journal.stream().filter(e -> e.state() == CityMap.State.FAILED).findFirst().orElseThrow();
        assertEquals("Failed: " + UrbanMissions.Kind.CLINIC_SUPPLIES.title, failed.heading());
        assertEquals("What it asked for is gone.", failed.detail());

        Path file = directory.resolve("journal.sav");
        assertTrue(SaveSystem.save(g, file));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        loaded.player.pos.set(g.player.pos);
        assertEquals(journal, CityMap.journal(loaded), "a load shows exactly the journal that was saved");
        assertEquals(CityMap.markers(g).stream().map(m -> m.kind() + "@" + m.position()).toList(),
                CityMap.markers(loaded).stream().map(m -> m.kind() + "@" + m.position()).toList());
    }

    @Test
    void earlierWorldsKeepTheirMapAndNoScreenOfTheMapPausesTheWorld() {
        Game older = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, World.GEN_URBAN_UTILITIES, new AudioManager());
        assertFalse(CityMap.applies(older.world), "a generator-18 city keeps the map it had");
        Game legacy = new Game();
        legacy.newWorld(SEED, true, World.DEFAULT_NEW_WORLD_GENERATOR, GameMode.SURVIVAL);
        assertFalse(CityMap.applies(legacy.world));
        for (Game.UiMode mode : new Game.UiMode[]{Game.UiMode.MAP, Game.UiMode.CRATE, Game.UiMode.NPC,
                Game.UiMode.INVENTORY, Game.UiMode.CRAFTING}) {
            assertFalse(mode.pausesSimulation(), mode + " leaves scavenging running");
        }
    }
}
