package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.entity.UrbanEncounterDirector;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanPlaces;
import com.veylon.world.urban.UrbanRefuges;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static com.veylon.RefugeWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Claimed refuges in a generator-17 city (urban milestone 18), through the F key at a bed: the
 * starter refuge's ground floor is the first claim; an abandoned flat is claimed at its bed and
 * becomes home; a claim needs its own storey under foot, never a roof, nothing hostile on it and
 * nobody's rooms; survivors keep theirs until they are gone; three refuges at most, and giving one
 * up takes a second press; sleeping chooses home and death wakes there; claims outlast edits,
 * saves and loads; nothing new appears inside a claim; and generator 16 has no claims at all.
 */
class UrbanRefugeClaimsTest {
    @TempDir Path directory;

    private static BuildingPlan.Fixture bed(BuildingPlan b, int floor) {
        return fixture(b, floor, f -> f.block() == BlockType.CAMP_BED);
    }

    /** Stands on a bed's own cell beside it, aims at it and presses F, crouched or not. */
    private static void pressBed(Game g, BuildingPlan.Fixture bed, boolean crouch) {
        standAt(g, bed.target());
        g.player.crouching = crouch;
        SurvivorWorldArena.pressF(g, bed.position());
    }

    private static String lastLog(Game g) {
        List<String> all = g.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }

    private static String prompt(Game g, Vec3i aimed) {
        aim(g, aimed);
        g.updatePrompt();
        return g.interactPrompt;
    }

    @Test
    void aFreshCityStartsWithTheStarterRefugesGroundFloorClaimedAsHome() {
        Game g = session(GameMode.SURVIVAL);
        UrbanRefuges refuges = g.world.urban.refuges;
        BuildingPlan starter = UrbanStarterSite.of(g.world.urban).refuge();
        assertEquals(1, refuges.size());
        UrbanRefuges.Claim home = refuges.home();
        assertEquals(UrbanIds.floor(starter.id(), 0), home.floor);
        assertEquals(g.world.urban.refugeAnchor, home.sleep, "slept in at the starter bed");
        assertEquals(UrbanPlaces.Place.REFUGE, UrbanPlaces.at(g.world, home.sleep.x(), home.sleep.y(), home.sleep.z()).place());
        assertEquals("[F] Sleep in your refuge  (crouch: give it up)", prompt(g, bed(starter, 0).position()));
        // Its cupboards are the refuge's storage; the storey above it was never claimed.
        BuildingPlan.Fixture cupboard = piece(starter, 0, BlockType.CABINET);
        standAt(g, cupboard.target());
        assertTrue(g.openCrateAt(cupboard.position()));
        assertTrue(g.crateStatus().startsWith("Your refuge's storage"), g.crateStatus());
        g.closeScreens();
        BuildingPlan.Fixture upstairs = piece(starter, 1, BlockType.CABINET);
        standAt(g, upstairs.target());
        assertTrue(g.openCrateAt(upstairs.position()));
        assertTrue(g.crateStatus().startsWith("Abandoned property"), g.crateStatus());
        g.closeScreens();
        Vec3i above = bed(starter, 1).target();
        assertNotEquals(UrbanPlaces.Place.REFUGE, UrbanPlaces.at(g.world, above.x(), above.y(), above.z()).place());
    }

    @Test
    void anAbandonedFlatIsClaimedAtItsBedAndBecomesHome() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture bed = bed(b, 1);
        standAt(g, bed.target());
        assertEquals("[F] Rest here  (crouch: claim these rooms as a refuge, 2 of 3)", prompt(g, bed.position()));
        pressBed(g, bed, true);
        assertFalse(g.sleeping, "a crouched press claims instead of sleeping");
        UrbanRefuges refuges = g.world.urban.refuges;
        assertEquals(2, refuges.size());
        UrbanRefuges.Claim claim = refuges.home();
        assertEquals(UrbanIds.floor(b.id(), 1), claim.floor, "the bed's own storey");
        assertEquals(bed.target(), claim.sleep, "slept in where the planned bed is reached");
        assertTrue(lastLog(g).endsWith("You claim the first-floor flat as a refuge (2 of 3). It is home now: you will wake here."),
                lastLog(g));
        assertEquals(UrbanPlaces.Place.REFUGE, UrbanPlaces.at(g.world, bed.target().x(), bed.target().y(), bed.target().z()).place());
        Vec3i below = bed(b, 0).target();
        assertNotEquals(UrbanPlaces.Place.REFUGE, UrbanPlaces.at(g.world, below.x(), below.y(), below.z()).place(),
                "one storey, not the building");
    }

    @Test
    void aClaimNeedsItsOwnStoreyUnderFootAndNeverARoofOrAStation() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture bed = bed(b, 1);
        standAt(g, bed(b, 0).target());
        g.player.crouching = true;
        assertEquals("Stand in these rooms to claim them", g.refuges.claimRefusal(bed.position()));
        assertTrue(g.refuges.atBed(bed.position()), "the crouched press is spent on the refusal");
        assertEquals(1, g.world.urban.refuges.size(), "refused, not claimed");
        assertTrue(lastLog(g).endsWith("Stand in these rooms to claim them."), lastLog(g));
        // A bedroll put down on the roof is no refuge.
        Vec3i roof = new Vec3i(b.parcel().x0() + 4, b.roofY() + 1, b.parcel().z0() + 4);
        standAt(g, roof);
        g.world.setBlock(roof.x() + 1, roof.y(), roof.z(), BlockType.BEDROLL, true);
        assertEquals("A roof is no refuge", g.refuges.claimRefusal(roof.offset(1, 0, 0)));
        // A bedroll on the street is in no building at all.
        Vec3i street = new Vec3i(b.parcel().x0() + 4, 41, b.parcel().z0() - 3);
        assertEquals("Only a building's rooms can be claimed", g.refuges.claimRefusal(street));
    }

    @Test
    void survivorsKeepTheirRoomsUntilTheyAreGoneAndAHeldBuildingsUpperFloorsAreAnybodys() {
        Game g = session(GameMode.SURVIVAL);
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        BuildingPlan camp = SurvivorWorldArena.building(g, plan);
        BuildingPlan.Fixture campBed = bed(camp, 0);
        standAt(g, campBed.target());
        g.player.crouching = true;
        assertEquals("These rooms are the camp's: claiming them would be taking what is theirs",
                g.refuges.claimRefusal(campBed.position()));
        SurvivorWorldArena.pressF(g, campBed.position());
        assertEquals(1, g.world.urban.refuges.size(), "nothing is appropriated");
        // Once nobody of theirs is left, the rooms belong to nobody.
        UrbanSite site = g.settlementManager.urbanSites.site(g, plan.id());
        for (Settlement.Resident r : site.residents) { r.alive = false; }
        assertEquals(0, site.present());
        assertNull(g.refuges.claimRefusal(campBed.position()));

        // A held walk-up nobody lives in is still the survivors' throughout.
        BuildingPlan held = find(g, p -> p.family() == BuildingPlan.Family.APARTMENT
                && p.variant() == BuildingPlan.Variant.OCCUPIED && SurvivorSites.owner(g.world.urban, p.id()) == null);
        BuildingPlan.Fixture heldBed = bed(held, 1);
        standAt(g, heldBed.target());
        assertEquals("These rooms are held by survivors: claiming them would be taking what is theirs",
                g.refuges.claimRefusal(heldBed.position()));

        // A held commercial or civic building is theirs only at street level: its upper storeys are shared out.
        BuildingPlan shop = find(g, p -> !p.family().walkUp() && p.family() != BuildingPlan.Family.TRANSIT
                && p.variant() == BuildingPlan.Variant.OCCUPIED && p.storeys() >= 2
                && SurvivorSites.owner(g.world.urban, p.id()) == null);
        Vec3i upper = standingCell(g, shop, 1), street = standingCell(g, shop, 0);
        standAt(g, upper);
        Vec3i roll = sideOf(g, upper);
        g.world.setBlock(roll.x(), roll.y(), roll.z(), BlockType.BEDROLL, true);
        assertNull(g.refuges.claimRefusal(roll), "an abandoned upper storey of a held building");
        standAt(g, street);
        Vec3i streetRoll = sideOf(g, street);
        g.world.setBlock(streetRoll.x(), streetRoll.y(), streetRoll.z(), BlockType.BEDROLL, true);
        assertEquals("These rooms are held by survivors: claiming them would be taking what is theirs",
                g.refuges.claimRefusal(streetRoll), "its street floor stays theirs");
    }

    /** The first planned building within two regions of the spawn that passes a test. */
    private static BuildingPlan find(Game g, java.util.function.Predicate<BuildingPlan> test) {
        for (int ring = 0; ring <= 2; ring++) {
            for (int rz = -ring; rz <= ring; rz++) {
                for (int rx = -ring; rx <= ring; rx++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != ring) { continue; }
                    DistrictPlan d = g.world.urban.regionPlan(rx, rz);
                    for (ParcelPlan p : d.parcels()) {
                        BuildingPlan b = g.world.urban.buildingAt(p.x0(), p.z0());
                        if (b != null && test.test(b)) { return b; }
                    }
                }
            }
        }
        throw new AssertionError("no such building within two regions of the spawn");
    }

    /** A standing cell inside a room of a storey, its chunks loaded. */
    private static Vec3i standingCell(Game g, BuildingPlan b, int floor) {
        g.world.ensureChunks(b.parcel().x0(), b.parcel().z0(), 3, 10_000);
        for (BuildingPlan.Floor f : b.floors()) {
            if (f.index() != floor) { continue; }
            for (BuildingPlan.Room room : f.rooms()) {
                Vec3i a = room.anchor();
                if (com.veylon.ai.Pathfinder.standable(g.world, a.x(), a.y(), a.z())) { return a; }
            }
        }
        throw new AssertionError("no standing room cell on floor " + floor);
    }

    /** An empty cell beside a standing cell, on the same floor, for a bedroll. */
    private static Vec3i sideOf(Game g, Vec3i cell) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Vec3i side = cell.offset(d[0], 0, d[1]);
            if (com.veylon.ai.Pathfinder.standable(g.world, side.x(), side.y(), side.z())
                    && g.world.urbanLocation(side.x(), side.y(), side.z()) != null) { return side; }
        }
        throw new AssertionError("no room for a bedroll beside " + cell);
    }

    @Test
    void somethingHostileOnTheStoreyStopsAClaimUntilItIsDealtWith() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture bed = bed(b, 1);
        standAt(g, bed.target());
        Vec3i room = b.floors().stream().filter(f -> f.index() == 1).findFirst().orElseThrow().rooms().getLast().anchor();
        Npc infected = g.entities.spawnNpc(g.world, "Veilbound", room.x() + .5f, room.y(), room.z() + .5f);
        infected.archetype = NpcArchetype.VEILBOUND;
        assertTrue(infected.hostileToPlayer());
        g.player.crouching = true;
        assertEquals("Something hostile is still in these rooms", g.refuges.claimRefusal(bed.position()));
        infected.dead = true;
        assertNull(g.refuges.claimRefusal(bed.position()));
    }

    @Test
    void threeRefugesAtMostAndGivingOneUpTakesASecondPress() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        pressBed(g, bed(b, 1), true);
        pressBed(g, bed(b, 2), true);
        UrbanRefuges refuges = g.world.urban.refuges;
        assertEquals(3, refuges.size());
        standAt(g, bed(b, 0).target());
        g.player.crouching = true;
        assertEquals("You keep three refuges already: give one up first", g.refuges.claimRefusal(bed(b, 0).position()));
        SurvivorWorldArena.pressF(g, bed(b, 0).position());
        assertEquals(3, refuges.size());

        // The second-floor flat is home; give it up.
        long secondFloor = UrbanIds.floor(b.id(), 2);
        assertEquals(secondFloor, refuges.homeFloor());
        pressBed(g, bed(b, 2), true);
        assertEquals(3, refuges.size(), "the first press only asks");
        assertTrue(prompt(g, bed(b, 2).position()).startsWith("Crouch and press F again to give up this refuge"),
                "crouched, taking the bed apart is offered too: " + g.interactPrompt);
        pressBed(g, bed(b, 2), true);
        assertEquals(2, refuges.size());
        assertNull(refuges.claim(secondFloor));
        assertEquals(UrbanIds.floor(b.id(), 1), refuges.homeFloor(), "the most recent other refuge becomes home");
        assertTrue(lastLog(g).endsWith("You give up the second-floor flat. What you built and stored there stays."), lastLog(g));

        // A pending give-up is forgotten with its world: the same storey in a new world asks again.
        pressBed(g, bed(b, 1), true);
        g.newWorld(SEED, true, World.GEN_URBAN_REFUGES, GameMode.SURVIVAL);
        BuildingPlan again = neighbour(g);
        pressBed(g, bed(again, 1), true);
        assertEquals(2, g.world.urban.refuges.size(), "claimed anew");
        pressBed(g, bed(again, 1), true);
        assertEquals(2, g.world.urban.refuges.size(), "a stale press from the old world gives nothing up");
    }

    @Test
    void sleepingInAClaimedBedChoosesHomeAndDeathWakesThere() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture flat = bed(b, 1);
        pressBed(g, flat, true);
        BuildingPlan starter = UrbanStarterSite.of(g.world.urban).refuge();
        UrbanRefuges refuges = g.world.urban.refuges;
        pressBed(g, bed(starter, 0), false);
        assertEquals(UrbanIds.floor(starter.id(), 0), refuges.homeFloor(), "sleeping in a refuge's bed makes it home");
        assertTrue(lastLog(g).contains("is home now") || g.eventLog.all().stream().anyMatch(l -> l.contains("is home now")));
        pressBed(g, flat, false);
        assertEquals(UrbanIds.floor(b.id(), 1), refuges.homeFloor());

        standAt(g, new Vec3i(flat.target().x(), 41, b.parcel().z0() - 6));
        g.player.health = 0;
        g.player.dead = true;
        g.respawn();
        assertFalse(g.player.dead);
        assertEquals(flat.target().x() + .5f, g.player.pos.x, 1e-4f, "death wakes at home's bed");
        assertEquals(flat.target().y() + .05f, g.player.pos.y, 1e-4f);
        assertEquals(flat.target().z() + .5f, g.player.pos.z, 1e-4f);
    }

    @Test
    void claimsOutlastEditsSavesAndLoadsAndHomeFallsBackToItsStorey() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture bed = bed(b, 1);
        pressBed(g, bed, true);
        // Take the bed apart and block its cell: the claim stands, its storey still a refuge.
        g.player.crouching = false;
        assertTrue(g.blockActions.completePlayerDismantle(bed.position()));
        Vec3i sleep = bed.target();
        g.world.setBlock(sleep.x(), sleep.y(), sleep.z(), BlockType.CONCRETE, true);
        Path file = directory.resolve("claims.sav");
        assertTrue(SaveSystem.save(g, file));
        UrbanRefuges before = g.world.urban.refuges;
        List<Long> floors = before.claims().stream().map(c -> c.floor).toList();
        long home = before.homeFloor();
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file), "load " + cycle);
            UrbanRefuges loaded = g.world.urban.refuges;
            assertEquals(floors, loaded.claims().stream().map(c -> c.floor).toList(), "the same storeys, in order");
            assertEquals(home, loaded.homeFloor());
            assertEquals(sleep, loaded.home().sleep, "the chosen bed's cell is kept even with the bed gone");
        }
        g.player.health = 0;
        g.player.dead = true;
        g.respawn();
        Vec3i woke = new Vec3i((int) Math.floor(g.player.pos.x), (int) Math.floor(g.player.pos.y), (int) Math.floor(g.player.pos.z));
        var where = g.world.urbanLocation(woke.x(), woke.y(), woke.z());
        assertNotNull(where);
        assertEquals(UrbanIds.floor(b.id(), 1), where.floor().id(), "woken on home's storey, beside the blocked bed: " + woke);
        assertNotEquals(sleep, woke);
    }

    @Test
    void nothingNewAppearsInsideAClaimedStorey() {
        Game g = session(GameMode.SURVIVAL);
        // An abandoned walk-up well beyond the starter refuge's own sanctuary.
        BuildingPlan b = find(g, p -> p.family() == BuildingPlan.Family.APARTMENT && p.storeys() >= 2
                && p.variant() != BuildingPlan.Variant.OCCUPIED && !UrbanStarterSite.isNeighbour(p.parcel())
                && Math.abs(p.parcel().x0() - g.world.urban.refugeAnchor.x()) + Math.abs(p.parcel().z0()
                - g.world.urban.refugeAnchor.z()) > 90);
        Vec3i cell = standingCell(g, b, 1);
        Vec3i away = new Vec3i(b.parcel().x0() + 4, 41, b.parcel().z0() - 30);
        standAt(g, away);
        assertTrue(UrbanEncounterDirector.safeSpawn(g, cell.x() + .5f, cell.y(), cell.z() + .5f),
                "an abandoned storey admits a body before it is claimed");
        standAt(g, cell);
        Vec3i roll = sideOf(g, cell);
        g.world.setBlock(roll.x(), roll.y(), roll.z(), BlockType.BEDROLL, true);
        g.player.crouching = true;
        assertTrue(g.refuges.atBed(roll));
        assertNotNull(g.world.urban.refuges.claim(UrbanIds.floor(b.id(), 1)), lastLog(g));
        standAt(g, away);
        assertFalse(UrbanEncounterDirector.safeSpawn(g, cell.x() + .5f, cell.y(), cell.z() + .5f),
                "once claimed, nothing new appears inside it");
    }

    @Test
    void creativeClaimsAlikeAndGeneratorSixteenHasNoClaims() {
        Game creative = session(GameMode.CREATIVE);
        pressBed(creative, bed(neighbour(creative), 1), true);
        assertEquals(2, creative.world.urban.refuges.size(), "a claim costs nothing, so Creative claims alike");

        Game earlier = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, World.GEN_URBAN_STRONGHOLDS,
                new com.veylon.engine.AudioManager());
        BuildingPlan.Fixture bed = bed(neighbour(earlier), 1);
        standAt(earlier, bed.target());
        assertNull(earlier.refuges.bedPrompt(bed.position()));
        pressBed(earlier, bed, true);
        assertEquals(0, earlier.world.urban.refuges.size(), "no claims before layout fourteen");
    }
}
