package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.UrbanSite;
import com.veylon.ui.CraftingScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.RelaySites;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanMissions.Kind;
import com.veylon.world.urban.UrbanMissions.Mission;
import com.veylon.world.urban.UrbanMissions.Status;
import com.veylon.world.urban.UrbanUtilities.Device;
import com.veylon.world.urban.VehicleSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The acceptance route of urban milestone 20 on the generated design city of seed 20260731
 * (generator 19, Survival): the long-term objective advanced from the first rumour to the city's
 * answer through urban exploration, survivors, salvage and repairs alone, with no tree felled and no
 * ore mined, and with two missions on the way that matter: one that genuinely visits a hidden refuge
 * behind its loose panel and one through a stronghold's guarded access.
 *
 * <p>Only the poses between legs are staged (the player is set down at each leg's first cell) and any
 * infected that come near are put down through the production damage path; everything the route
 * gains comes out of the city's own containers, wrecks and furniture, its people's conversation, the
 * workbench and the relay. Then a save and a load repeat nothing: no reward twice, no second ending.
 */
class UrbanMissionRouteTest {
    @TempDir Path directory;

    private final List<String> deeds = new ArrayList<>();

    @Test
    void theCityAnswersThroughExplorationSurvivorsSalvageAndRepairsAlone() {
        Game g = UrbanCommsTest.session(GameMode.SURVIVAL);
        UrbanMissions ledger = g.world.urban.missions;
        BuildingPlan refuge = g.world.urban.buildingById(g.world.urban.refugeBuilding);

        // The starter refuge's own stores: a pry tool, food, water and salvage to start from.
        searchAll(g, refuge, 0, any -> true);
        assertTrue(count(g, ItemType.IRON_PICKAXE) >= 1, "the refuge keeps a pry tool: " + deeds);

        // C1: the nearest camp speaks of the relay, and gives three missions.
        SurvivorSites.Site campPlan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        UrbanSite camp = meet(g, campPlan);
        say(g, camp.resident(0).live, 9);
        assertTrue(ledger.comms.rumoured, "the rumour");
        for (int i = 0; i < 3; i++) { say(g, camp.resident(0).live, 10); }
        Mission clinic = mission(ledger, Kind.CLINIC_SUPPLIES), aid = mission(ledger, Kind.HIDDEN_AID),
                mast = mission(ledger, Kind.LANDMARK);

        // The clinic it names: medicine out of its dispensary.
        BuildingPlan named = g.world.urban.buildingById(clinic.target);
        searchAll(g, named, -99, f -> BuildingFurnishing.containerUse(named, f) == BuildingFurnishing.ContainerUse.MEDICINE_CABINET);
        assertEquals(Status.READY, clinic.status, deeds.toString());

        // Food and water from the abandoned flats around the courtyard.
        for (BuildingPlan flat : neighbours(g)) {
            if (supplies(g) >= 20) { break; }
            searchAll(g, flat, -99, f -> kitchen(flat, f));
        }
        assertTrue(supplies(g) >= 20, "enough to share: " + supplies(g));

        // A mission that genuinely visits a hidden refuge: the panel found, the people helped.
        SurvivorSites.Site hidden = SurvivorSites.resolve(g.world.urban, aid.target);
        SurvivorWorldArena.standAt(g, SurvivorWorldArena.hall(g, hidden));
        SurvivorWorldArena.slowTick(g);
        SurvivorWorldArena.pressF(g, SurvivorWorldArena.panel(g, hidden));
        SurvivorWorldArena.standAt(g, hidden.entry());
        UrbanSite civilians = SurvivorWorldArena.site(g, hidden);
        assertTrue(civilians.discovered && g.world.urban.discovery.discovered(hidden.room()), "found behind the panel");
        say(g, civilians.resident(0).live, 2);
        assertEquals(Status.READY, aid.status, "they have what they need: " + g.npcScreen.lastResponse());
        deeds.add("aided the hidden civilians of " + UrbanIds.format(hidden.id()));

        // Back at the camp: two reports, each rewarded once.
        meet(g, campPlan);
        float before = camp.localReputation;
        say(g, camp.resident(0).live, 10);
        say(g, camp.resident(0).live, 10);
        assertEquals(Status.COMPLETED, clinic.status);
        assertEquals(Status.COMPLETED, aid.status);
        assertEquals(before + Kind.CLINIC_SUPPLIES.reward + Kind.HIDDEN_AID.reward, camp.localReputation, 1e-3f);

        // C3 and a mission through guarded access: the Wardens' friendship, their codes and their module.
        CompoundSites.Compound c = CompoundWorldArena.nearest(g, SurvivorSites.Kind.STRONGHOLD);
        CompoundWorldArena.load(g, c);
        CompoundWorldArena.standFacing(g, c, CompoundWorldArena.street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite ward = CompoundWorldArena.site(g, c);
        Npc keeper = CompoundWorldArena.live(ward).getFirst();
        for (int i = 0; i < 4 && !ward.grantsAccess(); i++) { say(g, keeper, 2); }
        assertTrue(ward.grantsAccess(), "the ward counts the survivor a friend: " + ward.localReputation);
        say(g, keeper, 9);
        assertEquals(UrbanMissions.CODES_STRONGHOLD, ledger.comms.codes, g.npcScreen.lastResponse());
        say(g, keeper, 10);
        Mission module = mission(ledger, Kind.STRONGHOLD_COMPONENT);
        Vec3i store = CommsComponents.strongholdModuleCell(g.world.urban, c);
        BuildingPlan stores = g.world.urban.buildingAt(store.x(), store.z());
        BuildingPlan.Fixture shelf = stores.fixtures().stream().filter(f -> f.position().equals(store)).findFirst().orElseThrow();
        assertEquals(CompoundSites.Zone.RESTRICTED, c.zoneAt(shelf.target().x(), shelf.target().y(), shelf.target().z()));
        double wantedBefore = ward.wantedUntil;
        take(g, shelf, ItemType.TRANSMITTER_MODULE);
        assertEquals(Status.READY, module.status, "a friend may take it from the restricted stores");
        assertEquals(wantedBefore, ward.wantedUntil, "nobody wants a friend gone");
        say(g, CompoundWorldArena.live(ward).getFirst(), 10);
        assertEquals(Status.COMPLETED, module.status);

        // C2: the other parts from the warehouse and an office.
        UrbanCommsTest.Source cells = UrbanCommsTest.source(g, BuildingPlan.Family.WAREHOUSE,
                BuildingFurnishing.ContainerUse.TOOL_CHEST, false);
        UrbanCommsTest.Source cable = UrbanCommsTest.source(g, BuildingPlan.Family.OFFICE,
                BuildingFurnishing.ContainerUse.UTILITY_LOCKER, false);
        clearThreats(g, cells.f().target());
        UrbanCommsTest.take(g, cells);
        clearThreats(g, cable.f().target());
        UrbanCommsTest.take(g, cable);
        assertEquals(3, ledger.comms.partsRecovered(), "every kind of part");

        // Salvage: engines for spare parts, a tank for fuel, a locker for sheet metal.
        salvageLocker(g);
        salvageWrecks(g, 4, 6);
        assertTrue(count(g, ItemType.SPARE_PARTS) >= 4 && count(g, ItemType.FUEL_CAN) >= 1, deeds.toString());
        assertTrue(count(g, ItemType.SHEET_METAL) >= 1 && count(g, ItemType.SCRAP) >= 5,
                "sheet metal " + count(g, ItemType.SHEET_METAL) + ", scrap " + count(g, ItemType.SCRAP) + ": " + deeds);

        // C4: the generator and the radio built at the refuge's workbench, the relay repaired on its roof.
        BuildingPlan.Fixture bench = refuge.fixtures().stream().filter(f -> f.block() == BlockType.WORKBENCH)
                .findFirst().orElseThrow();
        SurvivorWorldArena.standAt(g, bench.target());
        craft(g, "Rebuild Generator");
        craft(g, "Build Radio Workstation");
        RelaySites.Site relay = g.world.urban.relay();
        SurvivorWorldArena.standAt(g, relay.stand());
        clearThreats(g, relay.stand());
        fit(g, relay, ItemType.ANTENNA_CABLE);
        fit(g, relay, ItemType.POWER_CELL);
        assertTrue(ledger.comms.relayRepaired(), deeds.toString());
        SurvivorWorldArena.run(g, 0.5f);
        assertEquals(Status.READY, mast.status, "the survivor stood on the mast's roof");

        // C5: the call from the refuge, on the generator's power.
        List<Vec3i> lanes = lanes(refuge, bench.target());
        SurvivorWorldArena.standAt(g, lanes.getLast());
        put(g, ItemType.GENERATOR, lanes.get(0));
        useOn(g, lanes.get(0), ItemType.FUEL_CAN);
        SurvivorWorldArena.pressF(g, lanes.get(0));
        Device radio = put(g, ItemType.RADIO, lanes.get(1));
        SurvivorWorldArena.pressF(g, lanes.get(1));
        assertTrue(radio.transmitting(), lastLog(g));
        for (int i = 0; i < 100 && !ledger.comms.complete; i++) {
            clearThreats(g, lanes.getLast());
            SurvivorWorldArena.run(g, 1f);
        }
        assertTrue(ledger.comms.complete, "the city answered: " + ledger.comms.broadcast + " " + deeds);
        assertEquals(Game.AppState.VICTORY, g.appState);
        g.appState = Game.AppState.PLAYING; // carrying on surviving

        // The mast's report, then a save and a load: nothing twice.
        meet(g, campPlan);
        say(g, camp.resident(0).live, 10);
        assertEquals(Status.COMPLETED, mast.status);
        float trust = camp.localReputation, wardTrust = ward.localReputation;
        Path file = directory.resolve("route.sav");
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        UrbanMissions loaded = g.world.urban.missions;
        assertTrue(loaded.comms.complete);
        assertEquals(4, loaded.missions().stream().filter(m -> m.status == Status.COMPLETED && m.rewarded).count());
        UrbanSite campAgain = SurvivorWorldArena.site(g, campPlan);
        assertEquals(trust, campAgain.localReputation, 1e-3f);
        meet(g, campPlan);
        say(g, campAgain.resident(0).live, 10);
        assertEquals(trust, campAgain.localReputation, 1e-3f, "no report pays twice after a load");
        assertEquals(wardTrust, UrbanSiteRefuges.registered(g, ward.id).localReputation, 1e-3f);
        SurvivorWorldArena.run(g, 5f);
        assertEquals(Game.AppState.PLAYING, g.appState, "no second ending after a load");

        // Nothing came from a tree or an ore vein, nor what the veins give.
        for (ItemType wild : List.of(ItemType.LOG, ItemType.COAL, ItemType.COPPER_ORE, ItemType.IRON_ORE,
                ItemType.SULFUR, ItemType.SALTPETER)) {
            assertEquals(0, count(g, wild), "no " + wild);
        }
        System.out.println("[mission-route] " + String.join(" | ", deeds));
    }

    // ------------------------------------------------------------------
    // Legs
    // ------------------------------------------------------------------

    private static final class UrbanSiteRefuges {
        static UrbanSite registered(Game g, long id) {
            return com.veylon.settlement.UrbanSiteDirector.registered(g.world, id);
        }
    }

    private static UrbanSite meet(Game g, SurvivorSites.Site plan) {
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        return SurvivorWorldArena.site(g, plan);
    }

    private String say(Game g, Npc n, int choice) {
        assertNotNull(n, "someone to talk to");
        assertTrue(g.npcScreen.performSiteChoice(g, n, choice), "choice " + choice + " offered");
        String said = g.npcScreen.lastResponse();
        deeds.add(n.name + "[" + choice + "]: " + said);
        return said;
    }

    private static Mission mission(UrbanMissions ledger, Kind kind) {
        return ledger.missions().stream().filter(m -> m.kind == kind).findFirst()
                .orElseThrow(() -> new AssertionError("no " + kind + " mission"));
    }

    /** The courtyard's apartments of the starter superblock, nearest the refuge first. */
    private static List<BuildingPlan> neighbours(Game g) {
        List<BuildingPlan> out = new ArrayList<>();
        BuildingPlan refuge = g.world.urban.buildingById(g.world.urban.refugeBuilding);
        for (ParcelPlan p : g.world.urban.regionPlan(0, 0).parcels()) {
            BuildingPlan b = g.world.urban.buildingAt(p.x0(), p.z0());
            if (b != null && b.id() != refuge.id() && b.variant() != BuildingPlan.Variant.OCCUPIED
                    && SurvivorSites.owner(g.world.urban, b.id()) == null) {
                out.add(b);
            }
        }
        out.sort(Comparator.comparingDouble(b -> b.parcel().x0() * (double) b.parcel().x0() + b.parcel().z0() * (double) b.parcel().z0()));
        return out;
    }

    private static boolean kitchen(BuildingPlan b, BuildingPlan.Fixture f) {
        var use = BuildingFurnishing.containerUse(b, f);
        return use == BuildingFurnishing.ContainerUse.KITCHEN_CABINET || use == BuildingFurnishing.ContainerUse.PANTRY_SHELF
                || use == BuildingFurnishing.ContainerUse.FRIDGE;
    }

    /** Searches a building's containers (on one storey, or every storey for -99) and takes all they hold. */
    private void searchAll(Game g, BuildingPlan b, int floor, java.util.function.Predicate<BuildingPlan.Fixture> which) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.CONTAINER || (floor != -99 && UrbanIds.floorIndex(f.id()) != floor)
                    || !which.test(f)) {
                continue;
            }
            UrbanContainers.Entry e = g.world.urban.containers.byId(f.id());
            if (e != null && e.state.retired()) { continue; }
            clearThreats(g, f.target());
            SurvivorWorldArena.standAt(g, f.target());
            if (!g.openCrateAt(f.position())) { continue; }
            SurvivorWorldArena.run(g, 4f);
            Inventory crate = g.world.crateContents.get(f.position());
            int taken = 0;
            for (int i = 0; crate != null && i < crate.size(); i++) {
                if (crate.get(i) != null) { taken += g.transferCrateItemToPlayer(i); }
                crate = g.world.crateContents.get(f.position());
            }
            g.closeScreens();
            deeds.add("searched " + UrbanIds.format(f.id()) + " took " + taken);
        }
    }

    /** Searches one container and takes the stack of an item it holds. */
    private void take(Game g, BuildingPlan.Fixture f, ItemType item) {
        clearThreats(g, f.target());
        SurvivorWorldArena.standAt(g, f.target());
        assertTrue(g.openCrateAt(f.position()));
        SurvivorWorldArena.run(g, 4f);
        Inventory crate = g.world.crateContents.get(f.position());
        assertNotNull(crate);
        for (int i = 0; i < crate.size(); i++) {
            ItemStack s = crate.get(i);
            if (s != null && s.type == item) {
                assertTrue(g.transferCrateItemToPlayer(i) > 0);
                g.closeScreens();
                deeds.add("took the " + item + " from " + UrbanIds.format(f.id()));
                return;
            }
        }
        throw new AssertionError("no " + item + " in " + UrbanIds.format(f.id()));
    }

    /**
     * Takes apart the engines, the tank and the bodies of the nearest cars that did not burn until
     * enough spare parts, a fuel can and scrap are in hand.
     */
    private void salvageWrecks(Game g, int parts, int scrap) {
        List<VehicleSites.Wreck> wrecks = new ArrayList<>();
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (VehicleSites.Wreck w : g.world.urban.vehicles(rx, rz).wrecks()) {
                    if (!w.burnt()) { wrecks.add(w); }
                }
            }
        }
        wrecks.sort(Comparator.comparingLong(w -> (long) w.x0() * w.x0() + (long) w.z0() * w.z0()));
        for (VehicleSites.Wreck w : wrecks) {
            if (count(g, ItemType.SPARE_PARTS) >= parts && count(g, ItemType.FUEL_CAN) >= 1
                    && count(g, ItemType.SCRAP) >= scrap) { break; }
            SurvivorWorldArena.standAt(g, new Vec3i(w.x0() - 1, VehicleSites.FEET, w.z0() - 1));
            clearThreats(g, new Vec3i(w.x0(), VehicleSites.FEET, w.z0()));
            for (Vec3i cell : w.cells()) {
                VehicleSites.Part part = w.partAt(cell.x(), cell.y(), cell.z());
                boolean engine = part == VehicleSites.Part.ENGINE && count(g, ItemType.SPARE_PARTS) < parts;
                boolean isTank = w.isTank(cell.x(), cell.y(), cell.z());
                boolean tank = isTank && count(g, ItemType.FUEL_CAN) == 0;
                boolean body = part == VehicleSites.Part.BODY && !isTank && count(g, ItemType.SCRAP) < scrap;
                if (!engine && !tank && !body) { continue; }
                holdPryTool(g);
                assertTrue(g.blockActions.completePlayerDismantle(cell), "took apart " + cell);
                deeds.add("took apart the " + (engine ? "engine" : tank ? "tank" : "body") + " at " + cell);
            }
        }
    }

    /**
     * Takes apart a sound metal locker of an abandoned building near the courtyard for its sheet metal
     * and scrap; a worn one would lose its sheet metal, so the salvage table's own yield picks it.
     */
    private void salvageLocker(Game g) {
        for (BuildingPlan b : neighbours(g)) {
            for (BuildingPlan.Fixture f : b.fixtures()) {
                Vec3i p = f.position();
                if (f.kind() != BuildingPlan.FixtureKind.CONTAINER || f.block() != BlockType.LOCKER) { continue; }
                g.world.ensureChunks(p.x(), p.z(), 1, 10_000);
                if (g.world.getBlock(p.x(), p.y(), p.z()) != BlockType.LOCKER
                        || com.veylon.world.urban.SalvageTable.yields(g.world, p, BlockType.LOCKER).stream()
                        .noneMatch(s -> s.type == ItemType.SHEET_METAL)) {
                    continue;
                }
                clearThreats(g, f.target());
                SurvivorWorldArena.standAt(g, f.target());
                holdPryTool(g);
                if (g.blockActions.completePlayerDismantle(p)) {
                    deeds.add("took apart the locker at " + p);
                    return;
                }
            }
        }
        throw new AssertionError("no sound locker near the courtyard");
    }

    /** Puts the pry tool in the hand, from wherever it is in the pack, without dropping anything. */
    private static void holdPryTool(Game g) {
        Inventory inv = g.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == ItemType.IRON_PICKAXE) {
                if (i >= 9) {
                    ItemStack held = inv.get(8);
                    inv.set(8, s);
                    inv.set(i, held);
                    i = 8;
                }
                g.player.hotbarSel = i;
                return;
            }
        }
        throw new AssertionError("no pry tool in the pack");
    }

    private void craft(Game g, String name) {
        Recipe recipe = com.veylon.item.CraftingSystem.recipesFor(g.world).stream().filter(r -> r.name.equals(name))
                .findFirst().orElseThrow();
        assertNotNull(CraftingScreen.craft(g, recipe, g.nearbyStations()), "crafted " + name + ": " + lastLog(g));
        deeds.add("crafted " + name);
    }

    private void fit(Game g, RelaySites.Site relay, ItemType part) {
        Inventory inv = g.player.inventory;
        for (int i = 0; i < 9; i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == part) { g.player.hotbarSel = i; break; }
            if (i == 8) {
                for (int j = 9; j < inv.size(); j++) {
                    ItemStack t = inv.get(j);
                    if (t != null && t.type == part) {
                        ItemStack held = inv.get(0);
                        inv.set(0, t);
                        inv.set(j, held);
                        g.player.hotbarSel = 0;
                        break;
                    }
                }
            }
        }
        RefugeWorldArena.aim(g, relay.relay());
        g.interactions.rightClick();
        deeds.add("fitted the " + part + ": " + lastLog(g));
    }

    private Device put(Game g, ItemType item, Vec3i cell) {
        selectItem(g, item);
        assertTrue(g.placeSelectedBlockAt(cell.x(), cell.y(), cell.z()), "put the " + item + " down: " + lastLog(g));
        deeds.add("put the " + item + " down at " + cell);
        return g.world.urban.utilities.device(cell);
    }

    private static void useOn(Game g, Vec3i cell, ItemType item) {
        selectItem(g, item);
        RefugeWorldArena.aim(g, cell);
        g.interactions.rightClick();
    }

    /** Selects a stack of an item, swapping it into the first hotbar slot when it is in the pack. */
    private static void selectItem(Game g, ItemType item) {
        Inventory inv = g.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type == item) {
                if (i >= 9) {
                    ItemStack held = inv.get(0);
                    inv.set(0, s);
                    inv.set(i, held);
                    i = 0;
                }
                g.player.hotbarSel = i;
                return;
            }
        }
        throw new AssertionError("no " + item + " in the pack");
    }

    /** The refuge's free ground-floor lanes, nearest the bench first, the stand last. */
    private static List<Vec3i> lanes(BuildingPlan refuge, Vec3i bench) {
        List<Vec3i> lanes = new ArrayList<>();
        for (BuildingPlan.Fixture f : refuge.fixtures()) {
            Vec3i t = f.target();
            if (UrbanIds.floorIndex(f.id()) == 0 && t != null && !lanes.contains(t) && !t.equals(bench)
                    && (f.kind() == BuildingPlan.FixtureKind.CONTAINER || f.kind() == BuildingPlan.FixtureKind.FURNITURE)) {
                lanes.add(t);
            }
        }
        lanes.sort(Comparator.comparingDouble(c -> c.distSq(bench.x() + .5, bench.y() + .5, bench.z() + .5)));
        return lanes;
    }

    /** Puts down, through the production damage path, any infected within 24 blocks of a cell. */
    private void clearThreats(Game g, Vec3i near) {
        for (Npc n : new ArrayList<>(g.entities.npcs)) {
            if (!n.dead && n.archetype != null && n.encounterId != 0
                    && n.distSqTo(near.x() + .5f, near.y(), near.z() + .5f) < 24 * 24) {
                n.hurt(999, true);
                deeds.add("put down the " + n.archetype.displayName + " at " + SurvivorWorldArena.feet(n));
            }
        }
    }

    private static int count(Game g, ItemType t) {
        return g.player.inventory.count(t);
    }

    private static int supplies(Game g) {
        int n = 0;
        Inventory inv = g.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && (s.type == ItemType.DRIED_BERRY || s.type == ItemType.DRIED_MEAT
                    || s.type == ItemType.WATERSKIN_CLEAN)) {
                n += s.count;
            }
        }
        return n;
    }

    private static String lastLog(Game g) {
        var all = g.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }
}
