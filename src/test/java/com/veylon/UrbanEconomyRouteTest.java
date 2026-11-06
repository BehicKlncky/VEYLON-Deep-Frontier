package com.veylon;

import com.veylon.engine.AudioManager;
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
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingFurnishing.ContainerUse;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.RelaySites;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanMissions.Kind;
import com.veylon.world.urban.UrbanMissions.Mission;
import com.veylon.world.urban.UrbanMissions.Status;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.UrbanUtilities.Device;
import com.veylon.world.urban.VehicleSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The acceptance route of urban milestone 23 on the generated design city of seed 20260731 under the
 * survival economy (generator 20, content revision 4, Survival): the first upgrade after one trip out of
 * a refuge that cannot fund it, a camp's rumour and missions, then a setback the route chooses on
 * purpose (stealing from that camp until it turns hostile, so every mission it gave fails), and the
 * long-term objective completed anyway without anyone's help: the relay's codes replaced by the long
 * band scan and the transmitter module forced out of a security post's armoury. Then four more in-game
 * days of survival on what the city gives: searched flats, the refuge's taps boiled at the courtyard
 * fire, the rain collector, sleep in the refuge. No tree is felled and no ore mined.
 *
 * <p>Accelerated headless days, separate evidence from native or human play. Only the poses between legs
 * are staged (the survivor is set down at each leg's first cell, inside a flat where a door would have
 * to be opened or forced, as the milestone-20 route did); the armoury's lock, the access cost of the
 * module, is forced with the pry tool. Infected that come near are put down through the production
 * damage path. Everything gained comes from the city's containers, cars and furniture, its people's
 * words, the workbench, the relay, the taps, the fire and the rain.
 */
class UrbanEconomyRouteTest {
    private static final long SEED = 20_260_731L;
    /** In-game days of continued survival after the city answers. */
    private static final int DAYS = 4;
    private static final Set<BlockType> WILD = EnumSet.of(BlockType.LOG, BlockType.LEAVES, BlockType.COAL_ORE,
            BlockType.COPPER_ORE, BlockType.IRON_ORE, BlockType.SULFUR_ORE, BlockType.SALTPETER_ORE);

    @TempDir Path directory;

    private final List<String> deeds = new ArrayList<>();
    private int searched, threats;
    private float lowestHunger = Float.MAX_VALUE, lowestThirst = Float.MAX_VALUE, lowestHealth = Float.MAX_VALUE;

    @Test
    void afterACampTurnsAwayTheCityStillAnswersAndTheSurvivorLivesOnForDays() {
        Game g = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, World.GEN_URBAN_ECONOMY, new AudioManager());
        UrbanMissions ledger = g.world.urban.missions;
        BuildingPlan refuge = g.world.urban.buildingById(g.world.urban.refugeBuilding);
        BuildingPlan.Fixture bench = refuge.fixtures().stream().filter(f -> f.block() == BlockType.WORKBENCH)
                .findFirst().orElseThrow();

        // The refuge's own stores feed and treat the survivor, but fund no upgrade: no scrap in them.
        searchAll(g, refuge, -99, any -> true);
        assertEquals(0, count(g, ItemType.SCRAP), "content four keeps scrap out of the refuge: " + deeds);
        assertNull(tryCraft(g, bench, "Reinforced Spear"), "the refuge alone cannot fund the first upgrade");
        // One trip next door does.
        BuildingPlan neighbour = starterApartments(g).getFirst();
        searchAll(g, neighbour, 0, any -> true);
        assertNotNull(tryCraft(g, bench, "Reinforced Spear"), "one trip funds the first upgrade: " + deeds);
        assertTrue(count(g, ItemType.IRON_PICKAXE) >= 1, "a pry tool next door: " + deeds);

        // C1 and the camp's missions.
        SurvivorSites.Site campPlan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        UrbanSite camp = meet(g, campPlan);
        Npc speaker = camp.resident(0).live;
        say(g, speaker, 9);
        assertTrue(ledger.comms.rumoured, "the rumour");
        for (int i = 0; i < 3; i++) { say(g, speaker, 10); }
        List<Mission> given = new ArrayList<>(ledger.missions());
        assertFalse(given.isEmpty(), "the camp gave work");

        // The setback, chosen on purpose: the survivor robs the camp's stash, then threatens it for the rest.
        for (Vec3i p : camp.plan.stash()) {
            float before = camp.localReputation;
            if (stealFrom(g, camp, p) > 0) {
                assertTrue(camp.localReputation < before, "every theft costs trust: " + camp.localReputation);
            }
        }
        SurvivorWorldArena.standAt(g, campPlan.entry());
        if (!camp.hostile()) { say(g, speaker, 6); }
        assertTrue(camp.hostile(), "the camp turned on the survivor: " + camp.localReputation);
        SurvivorWorldArena.standAt(g, refuge.approaches().getLast());
        SurvivorWorldArena.run(g, 3f); // medium ticks judge the missions
        for (Mission m : given) {
            assertEquals(Status.FAILED, m.status, m.kind + " fails for good once its givers turn hostile");
        }
        deeds.add("every mission the camp gave failed: " + given.stream().map(m -> m.kind.name()).toList());

        // C2 without anyone's help: the module forced out of a security post's armoury, the cells and the cable.
        UrbanCommsTest.Source module = UrbanCommsTest.source(g, BuildingPlan.Family.SECURITY, ContainerUse.ARMORY_LOCKER, false);
        forceTheLockOf(g, module);
        UrbanCommsTest.take(g, module);
        UrbanCommsTest.Source cells = UrbanCommsTest.source(g, BuildingPlan.Family.WAREHOUSE, ContainerUse.TOOL_CHEST, false);
        clearThreats(g, cells.f().target());
        UrbanCommsTest.take(g, cells);
        UrbanCommsTest.Source cable = UrbanCommsTest.source(g, BuildingPlan.Family.OFFICE, ContainerUse.UTILITY_LOCKER, false);
        clearThreats(g, cable.f().target());
        UrbanCommsTest.take(g, cable);
        assertEquals(3, ledger.comms.partsRecovered(), "every kind of part, none of them given");
        assertEquals(UrbanMissions.CODES_NONE, ledger.comms.codes, "nobody shared the codes");

        // Salvage for the generator and the radio, and fuel for the long band scan.
        salvageLocker(g);
        salvageWrecks(g, 4, 2, 6);

        // C4 and C5: the generator and radio built at the refuge, the relay repaired, the band scanned.
        SurvivorWorldArena.standAt(g, bench.target());
        assertNotNull(tryCraft(g, bench, "Rebuild Generator"), "the generator: " + inventory(g));
        assertNotNull(tryCraft(g, bench, "Build Radio Workstation"), "the radio: " + inventory(g));
        RelaySites.Site relay = g.world.urban.relay();
        SurvivorWorldArena.standAt(g, relay.stand());
        clearThreats(g, relay.stand());
        fit(g, relay, ItemType.ANTENNA_CABLE);
        fit(g, relay, ItemType.POWER_CELL);
        assertTrue(ledger.comms.relayRepaired(), deeds.toString());
        List<Vec3i> lanes = lanes(refuge, bench.target());
        SurvivorWorldArena.standAt(g, lanes.getLast());
        put(g, ItemType.GENERATOR, lanes.get(0));
        useOn(g, lanes.get(0), ItemType.FUEL_CAN);
        useOn(g, lanes.get(0), ItemType.FUEL_CAN);
        SurvivorWorldArena.pressF(g, lanes.get(0));
        Device radio = put(g, ItemType.RADIO, lanes.get(1));
        SurvivorWorldArena.pressF(g, lanes.get(1));
        assertTrue(radio.transmitting(), lastLog(g));
        for (int i = 0; i < 400 && !ledger.comms.complete; i++) {
            clearThreats(g, lanes.getLast());
            SurvivorWorldArena.run(g, 1f);
        }
        assertTrue(ledger.comms.complete, "the city answered without codes: " + ledger.comms.broadcast + " " + deeds);
        assertEquals(Game.AppState.VICTORY, g.appState);
        g.appState = Game.AppState.PLAYING; // carrying on surviving
        deeds.add("the city answered on day " + day(g));
        SurvivorWorldArena.pressF(g, lanes.get(1)); // the radio off: the generator idles out its last fuel
        SurvivorWorldArena.pressF(g, lanes.get(0));

        // Four more days on what the city gives.
        Deque<BuildingPlan> flats = flats(g, refuge);
        BuildingPlan.Fixture bed = refuge.fixtures().stream().filter(f -> f.block() == BlockType.CAMP_BED
                && UrbanIds.floorIndex(f.id()) == 0).findFirst().orElseThrow();
        int firstDay = day(g);
        UrbanContainers.Entry probe = null;
        for (int d = 1; d <= DAYS; d++) {
            int searchedBefore = searched, threatsBefore = threats;
            sleepThroughTheNight(g, bed);
            liveTheMorning(g, refuge, flats);
            passTheDay(g, bed, refuge);
            System.out.printf(Locale.ROOT, "[economy-route] day %d (game day %d) %s health %.0f hunger %.0f thirst %.0f"
                            + " | food %d servings, clean water %d | searched %d containers today (%d in all) | threats put"
                            + " down %d | refuge taps %d L left | %s%n", d, day(g), clock(g), g.player.health, g.player.hunger,
                    g.player.thirst, food(g), count(g, ItemType.WATERSKIN_CLEAN), searched - searchedBefore, searched,
                    threats - threatsBefore, g.world.urban.utilities.left(refuge), condition(g));
            if (d == 2) {
                // A save and a load mid-way: nothing refills, nothing is forgotten.
                probe = g.world.urban.containers.entries().stream().filter(e -> e.id > 0 && e.state.revealed())
                        .findFirst().orElseThrow();
                int contents = contents(g, probe.position);
                Path file = directory.resolve("economy-route.sav");
                assertTrue(SaveSystem.save(g, file));
                assertTrue(SaveSystem.load(g, file));
                ledger = g.world.urban.missions;
                assertTrue(ledger.comms.complete);
                assertTrue(ledger.missions().stream().filter(m -> m.status == Status.FAILED).count() >= given.size());
                assertEquals(contents, contents(g, probe.position), "a searched container stays as it was left");
                refuge = g.world.urban.buildingById(g.world.urban.refugeBuilding);
            }
        }
        assertTrue(day(g) >= firstDay + DAYS, "four more days: from day " + firstDay + " to " + day(g));
        assertFalse(g.player.dead, deeds.toString());
        assertTrue(lowestHunger > 0 && lowestThirst > 0, "never starved or parched: hunger " + lowestHunger
                + ", thirst " + lowestThirst);
        assertTrue(lowestHealth > 40, "the survivor stayed on their feet: lowest health " + lowestHealth);

        // Nothing came from a tree or an ore vein: no such item, and no such block was ever broken.
        for (ItemType wild : List.of(ItemType.LOG, ItemType.COAL, ItemType.COPPER_ORE, ItemType.IRON_ORE,
                ItemType.SULFUR, ItemType.SALTPETER)) {
            assertEquals(0, count(g, wild), "no " + wild);
        }
        World fresh = new World(SEED, World.GEN_URBAN_ECONOMY);
        for (Vec3i p : g.world.changedBlocks.keySet()) {
            fresh.getOrCreateChunk(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16));
            BlockType was = fresh.getBlock(p.x(), p.y(), p.z());
            assertFalse(WILD.contains(was), "a " + was + " at " + p + " was changed");
        }
        System.out.println("[economy-route] " + String.join(" | ", deeds));
    }

    // ------------------------------------------------------------------
    // The days
    // ------------------------------------------------------------------

    /** Sleeps in the refuge's bed from evening to morning; whatever wakes the survivor is dealt with. */
    private void sleepThroughTheNight(Game g, BuildingPlan.Fixture bed) {
        runUntil(g, 21 * 60, bed.target());
        double morning = (Math.floor(g.time.totalMinutes / 1440) + 1) * 1440 + 6 * 60; // 06:00 tomorrow
        int nights = 0;
        for (int attempt = 0; attempt < 8 && g.time.totalMinutes < morning; attempt++) {
            SurvivorWorldArena.standAt(g, bed.target());
            clearThreats(g, bed.target());
            if (g.interactWithBlockAt(bed.position()) && g.sleeping) {
                nights++;
                for (int tick = 0; tick < 2_000 && g.sleeping; tick++) { g.sleep.tickSleep(.1f); }
            } else {
                SurvivorWorldArena.run(g, 10f);
            }
            note(g);
        }
        assertTrue(nights > 0, "the survivor slept in the refuge: " + lastLog(g));
        // Woken rested before dawn: the survivor waits indoors for the morning.
        runUntilMinute(g, morning, bed.target());
        deeds.add("slept and woke on day " + day(g) + " at " + clock(g));
    }

    /** Eats and drinks, then fetches food and water for the day: always one more building, more if short. */
    private void liveTheMorning(Game g, BuildingPlan refuge, Deque<BuildingPlan> flats) {
        eatAndDrink(g);
        int buildings = 0;
        while ((buildings == 0 || food(g) < 6 || count(g, ItemType.WATERSKIN_CLEAN) + count(g, ItemType.WATERSKIN_EMPTY) < 3)
                && !flats.isEmpty() && buildings < 4) {
            BuildingPlan b = flats.removeFirst();
            buildings++;
            searchAll(g, b, -99, f -> food(b, f));
            eatAndDrink(g);
        }
        deeds.add("day " + day(g) + " scavenged " + buildings + " buildings");
        water(g, refuge);
        eatAndDrink(g);
    }

    /** Waits out the day indoors at the refuge, eating, drinking and putting down whatever comes near. */
    private void passTheDay(Game g, BuildingPlan.Fixture bed, BuildingPlan refuge) {
        runUntil(g, 20 * 60, bed.target());
        water(g, refuge);
    }

    /** Runs the world's own frame advance until a game minute of the current day, in chunks. */
    private void runUntil(Game g, int minuteOfDay, Vec3i at) {
        runUntilMinute(g, Math.floor(g.time.totalMinutes / 1440) * 1440 + minuteOfDay, at);
    }

    private void runUntilMinute(Game g, double target, Vec3i at) {
        if (target <= g.time.totalMinutes) { return; }
        SurvivorWorldArena.standAt(g, at);
        while (g.time.totalMinutes < target) {
            clearThreats(g, at);
            SurvivorWorldArena.run(g, 20f);
            eatAndDrink(g);
            note(g);
            assertFalse(g.player.dead, "the survivor died at " + clock(g) + " (" + condition(g) + "): " + deeds);
        }
    }

    private static String condition(Game g) {
        return String.format(Locale.ROOT, "health %.0f body %.1f C wet %.2f env %.1f C afflictions %s", g.player.health,
                g.player.bodyTemp, g.player.wetness, g.player.envTemp, g.player.afflictions.keySet());
    }

    /** Clean water for the day: the rain collector, then the refuge's taps boiled at the courtyard fire. */
    private void water(Game g, BuildingPlan refuge) {
        UrbanStarterSite site = UrbanStarterSite.of(g.world.urban);
        SurvivorWorldArena.standAt(g, site.collector().offset(1, 0, 0));
        while (count(g, ItemType.WATERSKIN_EMPTY) > 0 && g.world.collectorWater.getOrDefault(site.collector(), 0f) >= 1f) {
            int clean = count(g, ItemType.WATERSKIN_CLEAN);
            g.interactWithBlockAt(site.collector());
            if (count(g, ItemType.WATERSKIN_CLEAN) == clean) { break; }
            deeds.add("rain water");
        }
        if (count(g, ItemType.WATERSKIN_CLEAN) >= 3) { return; }
        for (BuildingPlan.Fixture basin : refuge.fixtures()) {
            if (basin.block() != BlockType.RAIN_COLLECTOR || basin.target() == null) { continue; }
            SurvivorWorldArena.standAt(g, basin.target());
            while (count(g, ItemType.WATERSKIN_EMPTY) > 0 && g.world.urban.utilities.left(refuge) > 0) {
                int dirty = count(g, ItemType.WATERSKIN_DIRTY);
                g.interactWithBlockAt(basin.position());
                if (count(g, ItemType.WATERSKIN_DIRTY) == dirty) { break; }
            }
        }
        if (count(g, ItemType.WATERSKIN_DIRTY) == 0) { return; }
        SurvivorWorldArena.standAt(g, site.fire().offset(1, 0, 0));
        for (int i = 0; i < 12 && count(g, ItemType.WATERSKIN_DIRTY) > 0; i++) {
            if (g.world.campfireFuel.getOrDefault(site.fire(), 0f) <= 0 && count(g, ItemType.PLANK) == 0) {
                salvageATable(g);
                SurvivorWorldArena.standAt(g, site.fire().offset(1, 0, 0));
            }
            g.interactWithBlockAt(site.fire());
        }
        deeds.add("boiled tap water to " + count(g, ItemType.WATERSKIN_CLEAN) + " clean skins");
    }

    /** Eats while hungry and drinks while thirsty, from what is in the pack. */
    private void eatAndDrink(Game g) {
        for (int i = 0; i < 6 && g.player.hunger < 70 && food(g) > 0; i++) {
            select(g, count(g, ItemType.DRIED_MEAT) > 0 ? ItemType.DRIED_MEAT : ItemType.DRIED_BERRY);
            g.consumables.eat(g.player.selected());
        }
        for (int i = 0; i < 4 && g.player.thirst < 60 && count(g, ItemType.WATERSKIN_CLEAN) > 0; i++) {
            select(g, ItemType.WATERSKIN_CLEAN);
            g.consumables.drink(g.player.selected());
        }
        note(g);
    }

    private void note(Game g) {
        lowestHunger = Math.min(lowestHunger, g.player.hunger);
        lowestThirst = Math.min(lowestThirst, g.player.thirst);
        lowestHealth = Math.min(lowestHealth, g.player.health);
    }

    // ------------------------------------------------------------------
    // Legs
    // ------------------------------------------------------------------

    private static UrbanSite meet(Game g, SurvivorSites.Site plan) {
        SurvivorWorldArena.standAt(g, plan.entry());
        SurvivorWorldArena.slowTick(g);
        return SurvivorWorldArena.site(g, plan);
    }

    private void say(Game g, Npc n, int choice) {
        assertNotNull(n, "someone to talk to");
        assertTrue(g.npcScreen.performSiteChoice(g, n, choice), "choice " + choice + " offered");
        deeds.add(n.name + "[" + choice + "]: " + g.npcScreen.lastResponse());
    }

    /** One theft: everything one of the camp's stash containers holds, taken through the storage screen. */
    private int stealFrom(Game g, UrbanSite camp, Vec3i p) {
        BuildingPlan b = g.world.urban.buildingAt(p.x(), p.z());
        BuildingPlan.Fixture f = b.fixtures().stream().filter(x -> x.position().equals(p)).findFirst().orElseThrow();
        SurvivorWorldArena.standAt(g, f.target());
        assertTrue(g.openCrateAt(p), "the camp's store opens: " + lastLog(g));
        SurvivorWorldArena.run(g, 4f);
        Inventory crate = g.world.crateContents.get(p);
        int taken = 0;
        for (int i = 0; crate != null && i < crate.size(); i++) {
            if (crate.get(i) != null) { taken += g.transferCrateItemToPlayer(i); }
            crate = g.world.crateContents.get(p);
        }
        g.closeScreens();
        deeds.add("stole " + taken + " from the camp's " + UrbanIds.format(f.id()) + ", trust now "
                + Math.round(camp.localReputation));
        return taken;
    }

    /** Forces the locked door between a source container and the rest of its building with the pry tool. */
    private void forceTheLockOf(Game g, UrbanCommsTest.Source source) {
        BuildingPlan.Fixture locker = source.f();
        BuildingPlan.Fixture door = source.b().fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR && f.locked()
                        && UrbanIds.floorIndex(f.id()) == UrbanIds.floorIndex(locker.id()))
                .min(Comparator.comparingDouble(f -> f.position().distSq(locker.target().x() + .5,
                        locker.target().y(), locker.target().z() + .5)))
                .orElseThrow(() -> new AssertionError("the armoury is locked"));
        clearThreats(g, door.target());
        SurvivorWorldArena.standAt(g, door.target());
        holdPryTool(g);
        RefugeWorldArena.aim(g, door.position());
        RefugeWorldArena.hold(g, 30f);
        Vec3i p = door.position();
        assertFalse(com.veylon.world.Fixtures.isClosedDoor(g.world.getBlock(p.x(), p.y(), p.z())),
                "the armoury's lock gave way to the pry tool: " + g.world.getBlock(p.x(), p.y(), p.z()));
        deeds.add("forced the armoury door at " + p);
        clearThreats(g, locker.target());
    }

    /** The starter block's apartments, nearest the refuge's courtyard door first. */
    private static List<BuildingPlan> starterApartments(Game g) {
        BuildingPlan refuge = g.world.urban.buildingById(g.world.urban.refugeBuilding);
        Vec3i court = refuge.approaches().getLast();
        List<BuildingPlan> out = new ArrayList<>();
        for (ParcelPlan p : g.world.urban.regionPlan(0, 0).parcels()) {
            BuildingPlan b = g.world.urban.buildingAt(p.x0(), p.z0());
            if (b != null && b.id() != refuge.id() && UrbanStarterSite.isNeighbour(p)
                    && b.family() == BuildingPlan.Family.APARTMENT) {
                out.add(b);
            }
        }
        out.sort(Comparator.comparingDouble(b -> b.approaches().getLast().distSq(court.x() + .5, court.y(), court.z() + .5)));
        return out;
    }

    /** Abandoned flats and grocers outside the starter block, nearest the refuge first. */
    private static Deque<BuildingPlan> flats(Game g, BuildingPlan refuge) {
        Vec3i home = refuge.approaches().getLast();
        List<BuildingPlan> out = new ArrayList<>();
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (ParcelPlan p : g.world.urban.regionPlan(rx, rz).parcels()) {
                    BuildingPlan b = g.world.urban.buildingAt(p.x0(), p.z0());
                    if (b == null || UrbanStarterSite.isNeighbour(p) || b.variant() == BuildingPlan.Variant.OCCUPIED
                            || b.variant() == BuildingPlan.Variant.PICKED_CLEAN
                            || SurvivorSites.owner(g.world.urban, b.id()) != null
                            || (b.family() != BuildingPlan.Family.APARTMENT && b.family() != BuildingPlan.Family.GROCERY)) {
                        continue;
                    }
                    out.add(b);
                }
            }
        }
        out.sort(Comparator.comparingDouble(b -> b.approaches().getFirst().distSq(home.x() + .5, home.y(), home.z() + .5)));
        return new ArrayDeque<>(out);
    }

    private static boolean food(BuildingPlan b, BuildingPlan.Fixture f) {
        ContainerUse use = BuildingFurnishing.containerUse(b, f);
        return use == ContainerUse.KITCHEN_CABINET || use == ContainerUse.PANTRY_SHELF || use == ContainerUse.FRIDGE
                || use == ContainerUse.SHOP_SHELF || use == ContainerUse.BATHROOM_CABINET
                || use == ContainerUse.STOCKROOM_CRATE && b.family() == BuildingPlan.Family.GROCERY;
    }

    /** Searches a building's containers (on one storey, or every storey for -99) and takes all they hold. */
    private void searchAll(Game g, BuildingPlan b, int floor, java.util.function.Predicate<BuildingPlan.Fixture> which) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.CONTAINER || (floor != -99 && UrbanIds.floorIndex(f.id()) != floor)
                    || !which.test(f)) {
                continue;
            }
            UrbanContainers.Entry e = g.world.urban.containers.byId(f.id());
            if (e != null && (e.state.retired() || e.state.revealed())) { continue; }
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
            searched++;
            if (taken > 0) { deeds.add("searched " + UrbanIds.format(f.id()) + " took " + taken); }
            note(g);
        }
    }

    /** Takes apart a sound metal locker near the courtyard for its sheet metal and scrap. */
    private void salvageLocker(Game g) {
        for (BuildingPlan b : starterApartments(g)) {
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
        throw new AssertionError("no sound locker in the starter block");
    }

    /** Takes apart one sound table of the starter block for planks, the fire's fuel. */
    private void salvageATable(Game g) {
        for (BuildingPlan b : starterApartments(g)) {
            for (BuildingPlan.Fixture f : b.fixtures()) {
                Vec3i p = f.position();
                if (f.block() != BlockType.TABLE || f.target() == null) { continue; }
                SurvivorWorldArena.standAt(g, f.target());
                if (g.world.getBlock(p.x(), p.y(), p.z()) != BlockType.TABLE) { continue; }
                if (g.blockActions.completePlayerDismantle(p)) {
                    deeds.add("took apart a table for fuel");
                    return;
                }
            }
        }
        throw new AssertionError("no table left to burn");
    }

    /** Takes apart engines, tanks and bodies of the nearest cars that did not burn until enough is in hand. */
    private void salvageWrecks(Game g, int parts, int cans, int scrap) {
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
            if (count(g, ItemType.SPARE_PARTS) >= parts && count(g, ItemType.FUEL_CAN) >= cans
                    && count(g, ItemType.SCRAP) >= scrap) { break; }
            SurvivorWorldArena.standAt(g, new Vec3i(w.x0() - 1, VehicleSites.FEET, w.z0() - 1));
            clearThreats(g, new Vec3i(w.x0(), VehicleSites.FEET, w.z0()));
            for (Vec3i cell : w.cells()) {
                VehicleSites.Part part = w.partAt(cell.x(), cell.y(), cell.z());
                boolean engine = part == VehicleSites.Part.ENGINE && count(g, ItemType.SPARE_PARTS) < parts;
                boolean isTank = w.isTank(cell.x(), cell.y(), cell.z());
                boolean tank = isTank && count(g, ItemType.FUEL_CAN) < cans;
                boolean body = part == VehicleSites.Part.BODY && !isTank && count(g, ItemType.SCRAP) < scrap;
                if (!engine && !tank && !body) { continue; }
                holdPryTool(g);
                assertTrue(g.blockActions.completePlayerDismantle(cell), "took apart " + cell + ": " + lastLog(g));
                deeds.add("took apart the " + (engine ? "engine" : tank ? "tank" : "body") + " at " + cell);
            }
        }
        assertTrue(count(g, ItemType.SPARE_PARTS) >= parts && count(g, ItemType.FUEL_CAN) >= cans
                && count(g, ItemType.SCRAP) >= scrap, "salvage: " + inventory(g));
    }

    private String tryCraft(Game g, BuildingPlan.Fixture bench, String name) {
        SurvivorWorldArena.standAt(g, bench.target());
        Recipe recipe = com.veylon.item.CraftingSystem.recipesFor(g.world).stream().filter(r -> r.name.equals(name))
                .findFirst().orElseThrow();
        String made = CraftingScreen.craft(g, recipe, g.nearbyStations());
        if (made != null) { deeds.add("crafted " + name); }
        return made;
    }

    private void fit(Game g, RelaySites.Site relay, ItemType part) {
        select(g, part);
        RefugeWorldArena.aim(g, relay.relay());
        g.interactions.rightClick();
        deeds.add("fitted the " + part + ": " + lastLog(g));
    }

    private Device put(Game g, ItemType item, Vec3i cell) {
        select(g, item);
        assertTrue(g.placeSelectedBlockAt(cell.x(), cell.y(), cell.z()), "put the " + item + " down: " + lastLog(g));
        deeds.add("put the " + item + " down at " + cell);
        return g.world.urban.utilities.device(cell);
    }

    private static void useOn(Game g, Vec3i cell, ItemType item) {
        select(g, item);
        RefugeWorldArena.aim(g, cell);
        g.interactions.rightClick();
    }

    /** Selects a stack of an item, swapping it into the first hotbar slot when it is in the pack. */
    private static void select(Game g, ItemType item) {
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

    private static void holdPryTool(Game g) {
        select(g, ItemType.IRON_PICKAXE);
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
                threats++;
            }
        }
    }

    private static int count(Game g, ItemType t) {
        return g.player.inventory.count(t);
    }

    private static int food(Game g) {
        return count(g, ItemType.DRIED_BERRY) + count(g, ItemType.DRIED_MEAT);
    }

    private static int contents(Game g, Vec3i p) {
        Inventory inv = g.world.crateContents.get(p);
        int n = 0;
        for (int i = 0; inv != null && i < inv.size(); i++) {
            if (inv.get(i) != null) { n += inv.get(i).count; }
        }
        return n;
    }

    private static int day(Game g) {
        return (int) Math.floor(g.time.totalMinutes / 1440) + 1;
    }

    private static String clock(Game g) {
        int minute = (int) Math.floor(g.time.totalMinutes % 1440);
        return String.format(Locale.ROOT, "%02d:%02d", minute / 60, minute % 60);
    }

    private static String inventory(Game g) {
        StringBuilder sb = new StringBuilder();
        Inventory inv = g.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.get(i) != null) { sb.append(inv.get(i).type).append('x').append(inv.get(i).count).append(' '); }
        }
        return sb.toString();
    }

    private static String lastLog(Game g) {
        var all = g.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }
}
