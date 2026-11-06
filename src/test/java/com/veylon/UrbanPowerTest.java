package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SalvageTable;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.UrbanUtilities.Device;
import com.veylon.world.urban.UrbanUtilities.Kind;
import com.veylon.world.urban.VehicleSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Power in a layout-fifteen city (urban milestone 19): a generator rebuilt from salvage feeds the
 * work lamps of its own building while it runs, burning fuel for every second and every lamp,
 * making real noise and wearing; the lamps' light follows the power exactly; a dry tank, a seized
 * engine or a generator taken away leaves them dark; the circuit holds still while paused or far
 * away; Creative spends nothing and is heard by nobody; and fuel comes from the cars that did not burn.
 */
class UrbanPowerTest {
    private static final float TICK = 0.5f;

    /** The neighbour's first-floor flat: a generator on its bed's lane and lamps on its other lanes. */
    private record Flat(Game g, BuildingPlan b, Vec3i generator, List<Vec3i> lanes, Vec3i stand) { }

    private static Flat flat(GameMode mode) {
        Game g = UtilityWorldArena.session(mode);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        Vec3i generator = RefugeWorldArena.piece(b, 1, BlockType.CAMP_BED).target();
        List<Vec3i> lanes = new ArrayList<>();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            Vec3i t = f.target();
            if (UrbanIds.floorIndex(f.id()) == 1 && t != null && !t.equals(generator) && !lanes.contains(t)
                    && (f.kind() == BuildingPlan.FixtureKind.CONTAINER || f.kind() == BuildingPlan.FixtureKind.FURNITURE)) {
                lanes.add(t);
            }
        }
        lanes.sort(Comparator.comparingDouble(c -> c.distSq(generator.x() + .5, generator.y() + .5, generator.z() + .5)));
        Vec3i stand = lanes.removeLast();
        SurvivorWorldArena.standAt(g, stand);
        for (Vec3i lane : lanes) { assertSame(BlockType.AIR, g.world.getBlock(lane.x(), lane.y(), lane.z()), "lane " + lane); }
        return new Flat(g, b, generator, lanes, stand);
    }

    /** Puts the held item down at a cell through the production placement command. */
    private static Device put(Game g, ItemType item, Vec3i cell) {
        RefugeWorldArena.holdItem(g, item, 1);
        assertTrue(g.placeSelectedBlockAt(cell.x(), cell.y(), cell.z()), "put the " + item + " down at " + cell);
        return g.world.urban.utilities.device(cell);
    }

    private static void rmb(Game g, Vec3i cell, ItemType item, int count) {
        RefugeWorldArena.holdItem(g, item, count);
        RefugeWorldArena.aim(g, cell);
        g.interactions.rightClick();
    }

    private static void f(Game g, Vec3i cell) {
        SurvivorWorldArena.pressF(g, cell);
    }

    private static boolean listed(World w, Vec3i p) {
        Chunk c = w.getChunk(Math.floorDiv(p.x(), Chunk.SX), Math.floorDiv(p.z(), Chunk.SZ));
        return c.lights.stream().anyMatch(l -> l[0] == p.x() && l[1] == p.y() && l[2] == p.z());
    }

    private static String lastLog(Game g) {
        var all = g.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }

    /** A generator with one can, started, and two lamps switched on beside it. */
    private static Flat running(GameMode mode) {
        Flat fl = flat(mode);
        Game g = fl.g();
        Device gen = put(g, ItemType.GENERATOR, fl.generator());
        assertNotNull(gen);
        for (int i = 0; i < 2; i++) {
            Device lamp = put(g, ItemType.WORK_LAMP, fl.lanes().get(i));
            f(g, lamp.pos);
            assertTrue(lamp.on && !lamp.lit(), "a lamp with nothing feeding it stays dark");
        }
        rmb(g, fl.generator(), ItemType.FUEL_CAN, 1);
        assertEquals(UrbanUtilities.CAN_SECONDS, gen.fuel, 1e-4f);
        f(g, fl.generator());
        assertTrue(gen.running(), lastLog(g));
        return fl;
    }

    @Test
    void aRunningGeneratorLightsItsBuildingsLampsAndBurnsFuelForEverySecondAndLamp() {
        Flat fl = running(GameMode.SURVIVAL);
        Game g = fl.g();
        UrbanUtilities u = g.world.urban.utilities;
        Device gen = u.device(fl.generator());
        for (int i = 0; i < 2; i++) {
            Vec3i lamp = fl.lanes().get(i);
            assertTrue(u.device(lamp).lit(), "fed lamp " + lamp);
            assertTrue(g.world.isLampLit(lamp.x(), lamp.y(), lamp.z()));
            assertTrue(listed(g.world, lamp), "its light is registered in its chunk");
        }
        assertEquals(2, u.load(gen));
        // Sixty medium ticks: thirty seconds at idle plus a quarter for each of the two lamps.
        for (int i = 0; i < 60; i++) { g.refuges.utilities.mediumTick(TICK); }
        assertEquals(UrbanUtilities.CAN_SECONDS - 30f * 1.5f, gen.fuel, 1e-2f, "fuel burnt is time times load");
        assertEquals(UrbanUtilities.SOUND - 30f / UrbanUtilities.WEAR_SECONDS, gen.condition, 1e-3f, "and it wears");
        assertTrue(g.noise.countCategory("generator") >= 1, "it is heard");

        // Switching one lamp off lightens the load and darkens only that lamp.
        Vec3i first = fl.lanes().getFirst();
        f(g, first);
        assertFalse(u.device(first).lit());
        assertFalse(listed(g.world, first), "a lamp switched off leaves its chunk's lights");
        assertEquals(1, u.load(gen));
        float before = gen.fuel;
        for (int i = 0; i < 20; i++) { g.refuges.utilities.mediumTick(TICK); }
        assertEquals(before - 10f * 1.25f, gen.fuel, 1e-2f);

        // F stops it: every lamp goes dark at once, the chunk's light list and meshes with it.
        Chunk chunk = g.world.getChunk(Math.floorDiv(fl.lanes().get(1).x(), Chunk.SX), Math.floorDiv(fl.lanes().get(1).z(), Chunk.SZ));
        chunk.dirty = false;
        f(g, fl.generator());
        assertFalse(gen.on);
        assertFalse(u.device(fl.lanes().get(1)).lit());
        assertFalse(listed(g.world, fl.lanes().get(1)));
        assertTrue(chunk.dirty, "the mesh that showed its glow is rebuilt");
        float stopped = gen.fuel;
        for (int i = 0; i < 20; i++) { g.refuges.utilities.mediumTick(TICK); }
        assertEquals(stopped, gen.fuel, "a stopped generator burns nothing");
    }

    @Test
    void theTankRunsDryAndTheLampsGoDarkWithTheGameLoopDrivingIt() {
        Flat fl = running(GameMode.SURVIVAL);
        Game g = fl.g();
        Device gen = g.world.urban.utilities.device(fl.generator());
        // One can at one and a half times idle lasts two hundred seconds of the world's own frames.
        SurvivorWorldArena.run(g, 190f);
        assertTrue(gen.running(), "still running at 190 s: " + gen.fuel);
        SurvivorWorldArena.run(g, 15f);
        assertFalse(gen.on, "the tank ran dry");
        assertEquals(0f, gen.fuel);
        assertTrue(g.eventLog.all().stream().anyMatch(l -> l.contains("The generator sputters and dies")));
        for (int i = 0; i < 2; i++) {
            Vec3i lamp = fl.lanes().get(i);
            assertFalse(g.world.isLampLit(lamp.x(), lamp.y(), lamp.z()));
            assertFalse(listed(g.world, lamp));
        }
        f(g, fl.generator());
        assertFalse(gen.on, "a dry tank does not start");
        assertTrue(lastLog(g).contains("tank is dry"), lastLog(g));
    }

    @Test
    void aCircuitIsOneBuildingsWiringAndCarriesFourLoadsWithinReach() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        long here = 1L, other = 2L;
        Vec3i at = new Vec3i(500_000, 50, 0); // far from any chunk: the circuit is pure
        Device gen = new Device(Kind.GENERATOR, at, here);
        gen.on = true;
        gen.fuel = 100f;
        List<Device> all = new ArrayList<>(List.of(gen));
        for (int d : new int[]{3, 5, 7, 9, 11}) { all.add(lamp(at.offset(d, 0, 0), here)); }
        all.add(lamp(at.offset(0, 0, 17), here));   // past the leads' reach
        all.add(lamp(at.offset(-2, 0, 0), other));  // nearer, but another building
        UrbanUtilities u = g.world.urban.utilities;
        u.restore(g.world, Map.of(), all);
        List<Integer> lit = new ArrayList<>();
        for (Device d : u.devices()) {
            if (d.kind == Kind.LAMP && d.lit()) { lit.add(d.pos.x() - at.x()); }
        }
        assertEquals(List.of(3, 5, 7, 9), lit, "the four nearest in its own building");
        assertEquals(4, u.load(gen));

        // In the world: a device goes inside a building, and a building's wiring takes one generator.
        Flat fl = flat(GameMode.SURVIVAL);
        Game w = fl.g();
        put(w, ItemType.GENERATOR, fl.generator());
        RefugeWorldArena.holdItem(w, ItemType.GENERATOR, 1);
        Vec3i second = fl.lanes().getFirst();
        assertFalse(w.placeSelectedBlockAt(second.x(), second.y(), second.z()));
        assertTrue(lastLog(w).contains("already has a generator"), lastLog(w));
        assertSame(BlockType.AIR, w.world.getBlock(second.x(), second.y(), second.z()));
        Vec3i street = new Vec3i(fl.b().parcel().x0() + 3, 41, fl.b().parcel().z0() - 3);
        SurvivorWorldArena.standAt(w, street.offset(2, 0, 0));
        RefugeWorldArena.holdItem(w, ItemType.WORK_LAMP, 1);
        assertFalse(w.placeSelectedBlockAt(street.x(), street.y(), street.z()));
        assertTrue(lastLog(w).contains("needs a building's wiring"), lastLog(w));
        assertEquals(1, w.player.inventory.count(ItemType.WORK_LAMP), "a refused placement spends nothing");
    }

    private static Device lamp(Vec3i at, long building) {
        Device d = new Device(Kind.LAMP, at, building);
        d.on = true;
        return d;
    }

    @Test
    void takingTheSourceAwayLeavesTheLampsSafelyDarkAndGivesBackItsWholeCans() {
        Flat fl = running(GameMode.SURVIVAL);
        Game g = fl.g();
        UrbanUtilities u = g.world.urban.utilities;
        Device gen = u.device(fl.generator());
        rmb(g, fl.generator(), ItemType.FUEL_CAN, 1);
        assertEquals(2 * UrbanUtilities.CAN_SECONDS, gen.fuel, 1e-3f);
        for (int i = 0; i < 4; i++) { g.refuges.utilities.mediumTick(TICK); } // three seconds: part of a can burnt
        g.player.inventory.clear();
        RefugeWorldArena.holdItem(g, ItemType.IRON_PICKAXE, 1);
        g.player.crouching = true;
        RefugeWorldArena.aim(g, fl.generator());
        assertTrue(g.blockActions.dismantling.complete(fl.generator()), lastLog(g));
        assertSame(BlockType.AIR, g.world.getBlock(fl.generator().x(), fl.generator().y(), fl.generator().z()));
        assertNull(u.device(fl.generator()));
        assertEquals(1, g.player.inventory.count(ItemType.FUEL_CAN), "the whole can left comes back; the one burning is lost");
        assertEquals(2, g.player.inventory.count(ItemType.SPARE_PARTS));
        for (int i = 0; i < 2; i++) {
            Vec3i lamp = fl.lanes().get(i);
            assertFalse(u.device(lamp).lit(), "nothing feeds it any more");
            assertFalse(listed(g.world, lamp));
        }
        // A lamp broken out of its place takes its light and its record with it.
        Vec3i lamp = fl.lanes().getFirst();
        g.world.setBlock(lamp.x(), lamp.y(), lamp.z(), BlockType.AIR, true);
        assertNull(u.device(lamp));
        assertEquals(1, u.deviceCount());
    }

    @Test
    void aWornGeneratorSeizesAndIsRebuiltOnlyWithSparePartsAndAPryTool() {
        Flat fl = running(GameMode.SURVIVAL);
        Game g = fl.g();
        Device gen = g.world.urban.utilities.device(fl.generator());
        gen.condition = 1f; // staged wear: fifteen seconds of running left in it
        for (int i = 0; i < 40; i++) { g.refuges.utilities.mediumTick(TICK); }
        assertFalse(gen.on, "it seized");
        assertEquals(0f, gen.condition);
        assertTrue(g.eventLog.all().stream().anyMatch(l -> l.contains("The generator seizes")));
        f(g, fl.generator());
        assertFalse(gen.on, "a seized generator does not start");

        g.player.inventory.clear();
        rmb(g, fl.generator(), ItemType.SPARE_PARTS, 2);
        assertEquals(0f, gen.condition, "no pry tool, no rebuild");
        assertEquals(2, g.player.inventory.count(ItemType.SPARE_PARTS));
        assertTrue(lastLog(g).contains("pry tool"), lastLog(g));

        g.player.inventory.set(5, new com.veylon.item.ItemStack(ItemType.IRON_PICKAXE, 1));
        rmb(g, fl.generator(), ItemType.SPARE_PARTS, 2);
        assertEquals(50f, gen.condition, 1e-4f);
        assertEquals(1, g.player.inventory.count(ItemType.SPARE_PARTS), "one part spent");
        rmb(g, fl.generator(), ItemType.SPARE_PARTS, 1);
        assertEquals(UrbanUtilities.SOUND, gen.condition, 1e-4f);
        f(g, fl.generator());
        assertTrue(gen.running(), "rebuilt, it starts again");
    }

    @Test
    void pausedOrFarAwayTheCircuitHoldsStillAndNothingIsCaughtUp() {
        Flat fl = running(GameMode.SURVIVAL);
        Game g = fl.g();
        Device gen = g.world.urban.utilities.device(fl.generator());
        float fuel = gen.fuel;
        // Paused: the frame step advances nothing.
        g.simPaused = true;
        for (int i = 0; i < 200; i++) { g.playStep(0.05f, 0.05); }
        assertEquals(fuel, gen.fuel, "nothing burns while paused");
        g.simPaused = false;

        // Far away: the generator, its fuel and its lamps hold still.
        Vec3i far = new Vec3i(fl.generator().x() + 200, 41, fl.generator().z());
        SurvivorWorldArena.standAt(g, far);
        assertTrue(g.refuges.utilities.dormant(gen));
        float held = gen.fuel;
        SurvivorWorldArena.run(g, 30f);
        assertEquals(held, gen.fuel, "a dormant circuit burns nothing");
        assertTrue(gen.on && g.world.urban.utilities.device(fl.lanes().getFirst()).lit(), "and keeps its state");

        // Back in range it carries on from there, with nothing made up for the time away.
        SurvivorWorldArena.standAt(g, fl.stand());
        assertFalse(g.refuges.utilities.dormant(gen));
        for (int i = 0; i < 4; i++) { g.refuges.utilities.mediumTick(TICK); }
        assertEquals(held - 2f * 1.5f, gen.fuel, 1e-3f);
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void creativeSpendsNothingAndIsHeardByNobodyWhileTheWorldRunsTheSame(GameMode mode) {
        Flat fl = flat(mode);
        Game g = fl.g();
        Device gen = put(g, ItemType.GENERATOR, fl.generator());
        boolean creative = mode == GameMode.CREATIVE;
        assertEquals(creative ? 1 : 0, g.player.inventory.count(ItemType.GENERATOR), "putting it down");
        rmb(g, fl.generator(), ItemType.FUEL_CAN, 1);
        assertEquals(creative ? 1 : 0, g.player.inventory.count(ItemType.FUEL_CAN), "pouring a can");
        g.noise.reset();
        f(g, fl.generator());
        for (int i = 0; i < 8; i++) { g.refuges.utilities.mediumTick(TICK); }
        assertEquals(creative ? 0 : 2, g.noise.countCategory("generator"), "start and one hum, unless nobody can hear");
        assertEquals(UrbanUtilities.CAN_SECONDS - 4f, gen.fuel, 1e-3f, "the world's fuel burns the same");
        gen.condition = 40f;
        g.player.inventory.clear();
        rmb(g, fl.generator(), ItemType.SPARE_PARTS, 1);
        assertEquals(creative ? 90f : 40f, gen.condition, 1e-3f, "Creative needs no pry tool");
        g.player.inventory.set(5, new com.veylon.item.ItemStack(ItemType.IRON_PICKAXE, 1));
        rmb(g, fl.generator(), ItemType.SPARE_PARTS, 1);
        assertEquals(creative ? 100f : 90f, gen.condition, 1e-3f);
        assertEquals(creative ? 1 : 0, g.player.inventory.count(ItemType.SPARE_PARTS), "Creative spends no part");
    }

    @Test
    void theRunningGeneratorDrawsARealInfectedThroughItsOrdinaryHearing() {
        Flat fl = flat(GameMode.SURVIVAL);
        Game g = fl.g();
        // The player upstairs, out of sight; one body in the generator's own room, one far down the street.
        BuildingPlan.Fixture upstairs = RefugeWorldArena.piece(fl.b(), 2, BlockType.CAMP_BED);
        SurvivorWorldArena.standAt(g, upstairs.target());
        Npc near = CompoundWorldArena.infected(g, fl.lanes().getFirst());
        Npc far = CompoundWorldArena.infected(g, new Vec3i(fl.b().parcel().x0() + 4, 41, fl.b().parcel().z0() - 45));
        for (Npc n : List.of(near, far)) {
            n.lastKnown.set(0, 0, 0);
            n.searchTimer = 0;
        }
        SurvivorWorldArena.run(g, 2f);
        assertEquals(0, near.searchTimer, 1e-6f, "nothing to hear yet");
        Device gen = put(g, ItemType.GENERATOR, fl.generator());
        rmb(g, fl.generator(), ItemType.FUEL_CAN, 1);
        f(g, fl.generator());
        assertTrue(gen.running());
        SurvivorWorldArena.run(g, 1f);
        assertTrue(near.searchTimer > 0, "it heard the generator");
        assertEquals(fl.generator().x() + .5f, near.lastKnown.x, 1e-3f, "and comes to where it runs");
        assertEquals(fl.generator().z() + .5f, near.lastKnown.z, 1e-3f);
        assertEquals(0, far.searchTimer, 1e-6f, "beyond its reach and the walls between, nothing hears it");
    }

    @Test
    void fuelComesOnlyFromTheTankOfACarThatDidNotBurnUnderGeneratorEighteen() {
        for (int generator : new int[]{World.GEN_URBAN_UTILITIES, World.GEN_URBAN_REFUGES}) {
            Game g = SurvivorWorldArena.session(UtilityWorldArena.SEED, GameMode.SURVIVAL, generator,
                    new com.veylon.engine.AudioManager());
            VehicleSites.Wreck intact = null, burnt = null;
            for (int rz = -2; rz <= 2; rz++) {
                for (int rx = -2; rx <= 2; rx++) {
                    for (VehicleSites.Wreck w : g.world.urban.vehicles(rx, rz).wrecks()) {
                        if (w.burnt() && burnt == null) { burnt = w; }
                        if (!w.burnt() && intact == null) { intact = w; }
                    }
                }
            }
            assertNotNull(intact, "a wreck that did not burn lies near the spawn");
            assertNotNull(burnt);
            List<Vec3i> tanks = new ArrayList<>();
            for (Vec3i c : intact.cells()) {
                if (intact.isTank(c.x(), c.y(), c.z())) { tanks.add(c); }
            }
            assertEquals(1, tanks.size(), "one tank to a car");
            Vec3i tank = tanks.getFirst();
            SurvivorWorldArena.standAt(g, tank.offset(0, 0, 0));
            boolean utilities = generator == World.GEN_URBAN_UTILITIES;
            assertEquals(utilities ? 1 : 0, count(SalvageTable.yields(g.world, tank, BlockType.CAR_WRECK), ItemType.FUEL_CAN),
                    "generator " + generator);
            for (Vec3i c : burnt.cells()) {
                assertEquals(0, count(SalvageTable.yields(g.world, c, BlockType.CAR_WRECK), ItemType.FUEL_CAN),
                        "a burnt-out car's tank is empty");
            }
            if (!utilities) { continue; }
            // Taken apart with a pry tool, the tank gives its can once.
            g.player.inventory.clear();
            RefugeWorldArena.holdItem(g, ItemType.IRON_PICKAXE, 1);
            g.player.crouching = true;
            RefugeWorldArena.aim(g, tank);
            assertTrue(g.blockActions.dismantling.complete(tank), lastLog(g));
            assertEquals(1, g.player.inventory.count(ItemType.FUEL_CAN));
            assertTrue(SalvageTable.yields(g.world, tank, BlockType.AIR).isEmpty(), "and the tank is gone for good");
        }
    }

    private static int count(List<com.veylon.item.ItemStack> stacks, ItemType type) {
        return stacks.stream().filter(s -> s.type == type).mapToInt(s -> s.count).sum();
    }
}
