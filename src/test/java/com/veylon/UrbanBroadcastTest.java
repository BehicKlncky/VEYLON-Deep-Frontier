package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.UrbanComms;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.RelaySites;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanUtilities.Device;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The last stages of a layout-sixteen city's communications objective (urban milestone 20), through
 * the game's own commands and ticks: the relay's parts fitted at its cabinet with a pry tool; a radio
 * workstation built and put down on a generator's wiring in the refuge; the call carried through the
 * repaired relay by the seconds the generator runs, heard by whatever is near (by nobody, in
 * Creative); and the city answering once, with the ending card, a save and a load changing nothing.
 */
class UrbanBroadcastTest {
    @TempDir Path directory;

    private record Refuge(Game g, BuildingPlan b, Vec3i generator, Vec3i radio, Vec3i stand) { }

    /** The starter refuge, claimed from the start, with free cells on its ground floor for the devices. */
    private static Refuge refuge(GameMode mode) {
        Game g = UrbanCommsTest.session(mode);
        BuildingPlan b = g.world.urban.buildingById(g.world.urban.refugeBuilding);
        List<Vec3i> lanes = new ArrayList<>();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            Vec3i t = f.target();
            if (UrbanIds.floorIndex(f.id()) == 0 && t != null && !lanes.contains(t)
                    && (f.kind() == BuildingPlan.FixtureKind.CONTAINER || f.kind() == BuildingPlan.FixtureKind.FURNITURE)) {
                lanes.add(t);
            }
        }
        Vec3i generator = lanes.getFirst();
        lanes.sort(Comparator.comparingDouble(c -> c.distSq(generator.x() + .5, generator.y() + .5, generator.z() + .5)));
        Vec3i radio = lanes.get(1), stand = lanes.getLast();
        SurvivorWorldArena.standAt(g, stand);
        assertSame(BlockType.AIR, g.world.getBlock(generator.x(), generator.y(), generator.z()));
        assertSame(BlockType.AIR, g.world.getBlock(radio.x(), radio.y(), radio.z()));
        return new Refuge(g, b, generator, radio, stand);
    }

    /** Stands at the relay with a pry tool and fits both parts through the use button. */
    private static void repair(Game g) {
        RelaySites.Site r = g.world.urban.relay();
        SurvivorWorldArena.standAt(g, r.stand());
        // In the pack, clear of the first hotbar slot the parts are held in.
        g.player.inventory.set(20, new com.veylon.item.ItemStack(ItemType.IRON_PICKAXE, 1));
        useOnRelay(g, ItemType.ANTENNA_CABLE);
        useOnRelay(g, ItemType.POWER_CELL);
        assertTrue(g.world.urban.missions.comms.relayRepaired(), lastLog(g));
    }

    private static void useOnRelay(Game g, ItemType part) {
        RefugeWorldArena.holdItem(g, part, 1);
        RefugeWorldArena.aim(g, g.world.urban.relay().relay());
        g.interactions.rightClick();
    }

    /** A generator with two cans running and a radio switched on beside it, in the refuge. */
    private static Device powerUp(Refuge rf) {
        Game g = rf.g();
        SurvivorWorldArena.standAt(g, rf.stand());
        Device gen = UtilityWorldArena.put(g, ItemType.GENERATOR, rf.generator());
        UtilityWorldArena.useOn(g, rf.generator(), ItemType.FUEL_CAN, 1);
        UtilityWorldArena.useOn(g, rf.generator(), ItemType.FUEL_CAN, 1);
        SurvivorWorldArena.pressF(g, rf.generator());
        assertTrue(gen.running(), lastLog(g));
        Device radio = UtilityWorldArena.put(g, ItemType.RADIO, rf.radio());
        assertNotNull(radio, "the radio goes on the refuge's wiring");
        assertTrue(g.world.urban.missions.comms.radioBuilt);
        SurvivorWorldArena.pressF(g, rf.radio());
        assertTrue(radio.transmitting(), lastLog(g));
        return radio;
    }

    private static String lastLog(Game g) {
        var all = g.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }

    private static int radioNoises(Game g) {
        return g.noise.countCategory("radio");
    }

    @Test
    void fittingThePartsTakesAPryToolAndThePartsThemselves() {
        Game g = UrbanCommsTest.session(GameMode.SURVIVAL);
        RelaySites.Site r = g.world.urban.relay();
        SurvivorWorldArena.standAt(g, r.stand());
        g.player.inventory.clear();
        RefugeWorldArena.holdItem(g, ItemType.ANTENNA_CABLE, 1);
        RefugeWorldArena.aim(g, r.relay());
        g.updatePrompt();
        assertEquals("Opening the relay's cabinet takes a pry tool", g.interactPrompt);
        g.interactions.rightClick();
        UrbanMissions.Comms c = g.world.urban.missions.comms;
        assertEquals(0, c.installed, "no pry tool, no repair");
        assertEquals(1, g.player.inventory.count(ItemType.ANTENNA_CABLE));
        g.player.inventory.add(ItemType.IRON_PICKAXE, 1);
        g.updatePrompt();
        assertEquals("[RMB] String the antenna cable up the mast", g.interactPrompt);
        g.interactions.rightClick();
        assertEquals(UrbanMissions.RELAY_ANTENNA, c.installed);
        assertEquals(0, g.player.inventory.count(ItemType.ANTENNA_CABLE), "the cable is strung, not kept");
        assertTrue(c.relayFound, "whoever fits a part has found the relay");
        assertTrue(g.noise.countCategory("repair") >= 1, "the work is heard");
        RefugeWorldArena.holdItem(g, ItemType.ANTENNA_CABLE, 1);
        g.interactions.rightClick();
        assertEquals(1, g.player.inventory.count(ItemType.ANTENNA_CABLE), "a second cable is not wanted");
        useOnRelay(g, ItemType.POWER_CELL);
        assertTrue(c.relayRepaired());
        assertEquals("Relay mast: repaired, listening for a radio", g.refuges.comms.relayPrompt());
        assertEquals(BlockType.RELAY, g.world.getBlock(r.relay().x(), r.relay().y(), r.relay().z()), "nothing broke");

        // Creative fits parts with no tool and spends nothing.
        Game cr = UrbanCommsTest.session(GameMode.CREATIVE);
        SurvivorWorldArena.standAt(cr, cr.world.urban.relay().stand());
        cr.player.inventory.clear();
        useOnRelay(cr, ItemType.ANTENNA_CABLE);
        useOnRelay(cr, ItemType.POWER_CELL);
        assertTrue(cr.world.urban.missions.comms.relayRepaired());
        assertEquals(1, cr.player.inventory.count(ItemType.POWER_CELL), "Creative keeps what it holds");
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void aPoweredRadioInTheRefugeCarriesTheCallAndTheCityAnswersOnce(GameMode mode) {
        Refuge rf = refuge(mode);
        Game g = rf.g();
        // A camp the survivor knows, to hear the answer.
        SurvivorSites.Site campPlan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, campPlan.entry());
        SurvivorWorldArena.slowTick(g);
        UrbanSite camp = SurvivorWorldArena.site(g, campPlan);
        assertTrue(camp.discovered);
        // The codes from a stronghold that counts the survivor a friend.
        CompoundSites.Compound c = CompoundWorldArena.nearest(g, SurvivorSites.Kind.STRONGHOLD);
        CompoundWorldArena.load(g, c);
        CompoundWorldArena.standFacing(g, c, CompoundWorldArena.street(c, 2), c.gate().outside());
        SurvivorWorldArena.slowTick(g);
        UrbanSite ward = CompoundWorldArena.site(g, c);
        ward.localReputation = UrbanSite.FRIEND_TRUST + 5;
        ward.settleAlignment();
        UrbanComms.askAboutRadio(g, CompoundWorldArena.live(ward).getFirst());
        UrbanComms.askAboutRadio(g, CompoundWorldArena.live(ward).getFirst());
        UrbanMissions.Comms comms = g.world.urban.missions.comms;
        assertEquals(UrbanMissions.CODES_STRONGHOLD, comms.codes);
        repair(g);
        float campTrust = camp.localReputation, wardTrust = ward.localReputation;

        Device radio = powerUp(rf);
        assertEquals("Comms / broadcast from a powered radio in your refuge", UrbanComms.hudLine(g));
        SurvivorWorldArena.run(g, 30f);
        assertTrue(comms.broadcast > 25f && comms.broadcast < 35f, "half the call is out: " + comms.broadcast);
        assertFalse(comms.complete);
        if (mode == GameMode.SURVIVAL) {
            assertTrue(radioNoises(g) > 0, "the transmitter is heard");
        } else {
            assertEquals(0, radioNoises(g), "a Creative player's radio is heard by nobody");
        }
        assertTrue(UrbanComms.hudLine(g).startsWith("Comms / broadcasting: "), UrbanComms.hudLine(g));
        // A power cut keeps what went out.
        SurvivorWorldArena.pressF(g, rf.generator());
        float held = comms.broadcast;
        SurvivorWorldArena.run(g, 10f);
        assertEquals(held, comms.broadcast, 1e-4f, "nothing goes out without power");
        SurvivorWorldArena.pressF(g, rf.generator());
        SurvivorWorldArena.run(g, 40f);
        assertTrue(comms.complete, "the call went out: " + comms.broadcast);
        assertEquals(Game.AppState.VICTORY, g.appState, "the ending card");
        assertTrue(comms.completeMinute >= 0);
        assertEquals(campTrust + UrbanComms.CITY_ANSWERS, camp.localReputation, 1e-3f, "the camp heard");
        assertEquals(Math.min(100f, wardTrust + UrbanComms.CITY_ANSWERS), ward.localReputation, 1e-3f, "the ward heard");
        assertEquals("Comms / restored: the city answered", UrbanComms.hudLine(g));

        // Carrying on: once only, whatever the radio does next.
        g.appState = Game.AppState.PLAYING;
        double done = comms.completeMinute;
        float trust = camp.localReputation;
        SurvivorWorldArena.run(g, 20f);
        assertEquals(Game.AppState.PLAYING, g.appState, "no second card");
        assertEquals(done, comms.completeMinute);
        assertEquals(trust, camp.localReputation, 1e-4f, "no second answer");
        assertTrue(radio.on);
        Path file = directory.resolve("answered-" + mode + ".sav");
        assertTrue(SaveSystem.save(g, file));
        assertTrue(SaveSystem.load(g, file));
        assertTrue(g.world.urban.missions.comms.complete);
        assertEquals(done, g.world.urban.missions.comms.completeMinute);
        SurvivorWorldArena.standAt(g, rf.stand());
        SurvivorWorldArena.run(g, 5f);
        assertEquals(Game.AppState.PLAYING, g.appState, "a load never shows the card again");
        assertEquals(trust, SurvivorWorldArena.site(g, campPlan).localReputation, 1e-4f);
    }

    @Test
    void withoutTheCodesTheCallTakesFourTimesAsLongAndNothingGoesOutUnrepairedOrAway() {
        Refuge rf = refuge(GameMode.SURVIVAL);
        Game g = rf.g();
        Device radio = powerUp(rf);
        UrbanMissions.Comms comms = g.world.urban.missions.comms;
        SurvivorWorldArena.run(g, 10f);
        assertEquals(0f, comms.broadcast, "a dead relay carries nothing");
        assertTrue(g.refuges.utilities.devicePrompt(rf.radio()).contains("the relay is dead"));
        repair(g);
        SurvivorWorldArena.standAt(g, rf.stand());
        assertEquals(UrbanMissions.SCAN_SECONDS, comms.required());
        SurvivorWorldArena.run(g, 70f);
        assertFalse(comms.complete, "without codes a minute is not enough: " + comms.broadcast);
        assertTrue(comms.broadcast > 60f);
        assertTrue(radio.transmitting());

        // A radio on a building the survivor holds no refuge in carries nothing.
        Game away = UrbanCommsTest.session(GameMode.SURVIVAL);
        repair(away);
        BuildingPlan flat = RefugeWorldArena.neighbour(away);
        Vec3i gen = RefugeWorldArena.piece(flat, 1, BlockType.CAMP_BED).target();
        Vec3i set = RefugeWorldArena.piece(flat, 1, BlockType.CABINET).target();
        SurvivorWorldArena.standAt(away, RefugeWorldArena.piece(flat, 1, BlockType.LOCKER).target());
        UtilityWorldArena.put(away, ItemType.GENERATOR, gen);
        UtilityWorldArena.useOn(away, gen, ItemType.FUEL_CAN, 1);
        SurvivorWorldArena.pressF(away, gen);
        Device elsewhere = UtilityWorldArena.put(away, ItemType.RADIO, set);
        SurvivorWorldArena.pressF(away, set);
        assertTrue(elsewhere.transmitting());
        SurvivorWorldArena.run(away, 20f);
        assertEquals(0f, away.world.urban.missions.comms.broadcast, "the call goes out from the refuge");
        assertTrue(away.refuges.utilities.devicePrompt(set).contains("not in your refuge"));
    }

    @Test
    void earlierCitiesCannotWireARadio() {
        Game g = UtilityWorldArena.session(GameMode.CREATIVE);
        BuildingPlan flat = RefugeWorldArena.neighbour(g);
        Vec3i cell = RefugeWorldArena.piece(flat, 1, BlockType.CABINET).target();
        SurvivorWorldArena.standAt(g, cell.offset(0, 0, 0));
        RefugeWorldArena.holdItem(g, ItemType.RADIO, 1);
        assertFalse(g.placeSelectedBlockAt(cell.x(), cell.y(), cell.z()), "generator 18 has no radio to wire");
        assertNull(g.world.urban.utilities.device(cell));
    }
}
