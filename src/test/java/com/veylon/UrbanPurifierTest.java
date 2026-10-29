package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.UrbanUtilities.Device;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The water utility of a layout-fifteen refuge (urban milestone 19): a purifier built with its
 * filter spent works only once packed with fibre, then, fed by a running generator, pumps its
 * building's stale tap water and treats it into clean water; every litre only moves, from the pipes
 * and the player's skins through its two tanks into clean skins; without power it does nothing, a
 * clogged filter stops it, taking it apart spills it, survivors' pipes are not pumped, and Creative
 * spends nothing.
 */
class UrbanPurifierTest {
    private static final float TICK = 0.5f;

    private record Rig(Game g, BuildingPlan b, Device generator, Device purifier) { }

    /** A running generator with two cans on the neighbour's first floor and a purifier beside it. */
    private static Rig rig(GameMode mode) {
        Game g = UtilityWorldArena.session(mode);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        Vec3i genCell = RefugeWorldArena.piece(b, 1, BlockType.CAMP_BED).target();
        List<Vec3i> lanes = UtilityWorldArena.lanes(b, 1, genCell);
        lanes.remove(genCell);
        SurvivorWorldArena.standAt(g, lanes.getLast());
        Device generator = UtilityWorldArena.put(g, ItemType.GENERATOR, genCell);
        UtilityWorldArena.useOn(g, genCell, ItemType.FUEL_CAN, 2);
        UtilityWorldArena.useOn(g, genCell, ItemType.FUEL_CAN, 2);
        SurvivorWorldArena.pressF(g, genCell);
        assertTrue(generator.running());
        Device purifier = UtilityWorldArena.put(g, ItemType.WATER_PURIFIER, lanes.getFirst());
        g.player.inventory.clear();
        return new Rig(g, b, generator, purifier);
    }

    private static void tick(Game g, int ticks) {
        for (int i = 0; i < ticks; i++) { g.refuges.utilities.mediumTick(TICK); }
    }

    private static String lastLog(Game g) {
        var all = g.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }

    @Test
    void aPurifierBuiltWithItsFilterSpentWorksOnlyOncePackedWithFibre() {
        Rig r = rig(GameMode.SURVIVAL);
        Game g = r.g();
        Device p = r.purifier();
        assertEquals(0f, p.condition, "built with its filter spent");
        assertTrue(p.powered(), "on the running generator's wiring");
        SurvivorWorldArena.pressF(g, p.pos);
        assertTrue(p.on);
        assertTrue(lastLog(g).contains("its filter is spent"), lastLog(g));
        UtilityWorldArena.useOn(g, p.pos, ItemType.WATERSKIN_DIRTY, 1);
        assertEquals(1f, p.untreated, 1e-6f);
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_EMPTY), "the skin comes back empty");
        tick(g, 40);
        assertEquals(0f, p.clean, "a spent filter treats nothing");
        assertEquals(0, g.world.urban.utilities.drawn(r.b().id()), "and its pump does not run");

        g.player.inventory.clear();
        UtilityWorldArena.useOn(g, p.pos, ItemType.FIBER, 1);
        assertEquals(0f, p.condition, "one fibre is not a filter");
        assertTrue(lastLog(g).contains("takes 2 fibre"), lastLog(g));
        UtilityWorldArena.useOn(g, p.pos, ItemType.FIBER, 2);
        assertEquals(UrbanUtilities.SOUND, p.condition, 1e-6f, "packed: the purifier's repair");
        assertEquals(0, g.player.inventory.count(ItemType.FIBER));

        // Twenty seconds of power: a litre treated, and two of the building's stale litres pumped up.
        tick(g, 40);
        assertEquals(1f, p.clean, 1e-3f);
        assertEquals(2, g.world.urban.utilities.drawn(r.b().id()));
        assertEquals(2f, p.untreated, 1e-3f, "one poured and two pumped, less the litre treated");
        assertEquals(UrbanUtilities.SOUND - UrbanUtilities.FILTER_WEAR, p.condition, 1e-2f, "a litre's wear");
    }

    @Test
    void waterOnlyMovesFromThePipesAndSkinsThroughItsTanksIntoCleanSkins() {
        Rig r = rig(GameMode.SURVIVAL);
        Game g = r.g();
        Device p = r.purifier();
        UrbanUtilities u = g.world.urban.utilities;
        int pipes = UrbanUtilities.capacity(r.b());
        UtilityWorldArena.useOn(g, p.pos, ItemType.FIBER, 2);
        SurvivorWorldArena.pressF(g, p.pos);
        int poured = 0, filled = 0;
        for (int i = 0; i < 2; i++) {
            UtilityWorldArena.useOn(g, p.pos, ItemType.WATERSKIN_DIRTY, 1);
            poured++;
        }
        // Long enough for the pump to empty the building's pipes; clean water is drawn off as it comes.
        for (int round = 0; round < 20; round++) {
            tick(g, 40);
            while (p.clean >= 1f) {
                UtilityWorldArena.useOn(g, p.pos, ItemType.WATERSKIN_EMPTY, 1);
                assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_CLEAN), lastLog(g));
                g.player.inventory.clear();
                filled++;
            }
            float in = poured + u.drawn(r.b().id());
            assertEquals(in, p.untreated + p.clean + filled, 1e-2f, "round " + round + ": nothing made, nothing lost");
            assertEquals(UrbanUtilities.SOUND - UrbanUtilities.FILTER_WEAR * (p.clean + filled), p.condition, 0.05f,
                    "the filter wears by the litre");
        }
        assertEquals(pipes, u.drawn(r.b().id()), "the pump drew every stale litre the pipes held");
        assertEquals(0, u.left(r.b()));
        assertTrue(filled >= pipes, "most of it came out clean: " + filled + " of " + (pipes + poured));
        assertTrue(r.generator().running(), "two cans outlast the run");
        assertTrue(p.drawing(), "on dry pipes it still draws power, pumping dry, until it is switched off");
        SurvivorWorldArena.pressF(g, p.pos);
        assertFalse(p.drawing());
        assertEquals(0, u.load(r.generator()));
    }

    @Test
    void withoutPowerNothingIsTreatedAndItsWorkWeighsOnTheFuel() {
        Rig r = rig(GameMode.SURVIVAL);
        Game g = r.g();
        Device p = r.purifier();
        UtilityWorldArena.useOn(g, p.pos, ItemType.FIBER, 2);
        SurvivorWorldArena.pressF(g, p.pos);
        UtilityWorldArena.useOn(g, p.pos, ItemType.WATERSKIN_DIRTY, 1);
        assertEquals(1, g.world.urban.utilities.load(r.generator()), "a working purifier is a load");
        float fuel = r.generator().fuel;
        tick(g, 10);
        assertEquals(fuel - 5f * (1f + UrbanUtilities.LOAD_BURN), r.generator().fuel, 1e-3f);
        SurvivorWorldArena.pressF(g, r.generator().pos);
        assertFalse(p.powered());
        float clean = p.clean, untreated = p.untreated;
        tick(g, 40);
        assertEquals(clean, p.clean, "no power, no clean water");
        assertEquals(untreated, p.untreated);
        assertTrue(g.refuges.utilities.devicePrompt(p.pos).contains("no power"), g.refuges.utilities.devicePrompt(p.pos));
    }

    @Test
    void aCloggedFilterStopsItUntilFreshFibreIsPacked() {
        Rig r = rig(GameMode.SURVIVAL);
        Game g = r.g();
        Device p = r.purifier();
        UtilityWorldArena.useOn(g, p.pos, ItemType.FIBER, 2);
        SurvivorWorldArena.pressF(g, p.pos);
        p.condition = UrbanUtilities.FILTER_WEAR; // staged wear: one litre of filter left
        p.untreated = 3f;
        tick(g, 120);
        assertEquals(0f, p.condition, "clogged");
        assertEquals(1f, p.clean, 1e-3f, "after exactly the litre it had left");
        assertTrue(g.eventLog.all().stream().anyMatch(l -> l.contains("filter clogs")));
        float clean = p.clean;
        tick(g, 40);
        assertEquals(clean, p.clean, "a clogged filter treats nothing");
        UtilityWorldArena.useOn(g, p.pos, ItemType.FIBER, 2);
        tick(g, 40);
        assertEquals(clean + 1f, p.clean, 1e-3f, "fresh fibre, and it works again");
    }

    @Test
    void takingItApartSpillsWhatItHeldAndGivesBackItsParts() {
        Rig r = rig(GameMode.SURVIVAL);
        Game g = r.g();
        Device p = r.purifier();
        p.untreated = 2f;
        p.clean = 1.5f;
        g.player.inventory.clear();
        RefugeWorldArena.holdItem(g, ItemType.IRON_PICKAXE, 1);
        g.player.crouching = true;
        RefugeWorldArena.aim(g, p.pos);
        assertTrue(g.blockActions.dismantling.complete(p.pos), lastLog(g));
        assertTrue(lastLog(g).contains("Its 3.5 L of water run away."), lastLog(g));
        assertNull(g.world.urban.utilities.device(p.pos));
        assertEquals(1, g.player.inventory.count(ItemType.SHEET_METAL));
        assertEquals(1, g.player.inventory.count(ItemType.SPARE_PARTS));
        assertEquals(0, g.player.inventory.count(ItemType.WATERSKIN_CLEAN), "the water is spilt, not carried");
    }

    @Test
    void survivorsPipesAreNotPumpedWhileAnyoneOfTheirsIsLeft() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        BuildingPlan camp = SurvivorWorldArena.building(g, plan);
        Vec3i bed = camp.fixtures().stream()
                .filter(f -> UrbanIds.floorIndex(f.id()) == 0 && RefugeSystem.isBed(f.block())).findFirst().orElseThrow().target();
        List<Vec3i> lanes = UtilityWorldArena.lanes(camp, 0, bed);
        lanes.remove(bed);
        SurvivorWorldArena.standAt(g, lanes.getLast());
        Device generator = UtilityWorldArena.put(g, ItemType.GENERATOR, bed);
        UtilityWorldArena.useOn(g, bed, ItemType.FUEL_CAN, 1);
        SurvivorWorldArena.pressF(g, bed);
        Device p = UtilityWorldArena.put(g, ItemType.WATER_PURIFIER, lanes.getFirst());
        UtilityWorldArena.useOn(g, p.pos, ItemType.FIBER, 2);
        SurvivorWorldArena.pressF(g, p.pos);
        assertTrue(generator.running() && p.working());
        tick(g, 60);
        assertEquals(0, g.world.urban.utilities.drawn(camp.id()), "the camp's water is theirs");
        assertEquals(0f, p.untreated);
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void creativePacksTheFilterForNothingWhileTheWaterIsTheWorldsAsEver(GameMode mode) {
        Rig r = rig(mode);
        Game g = r.g();
        Device p = r.purifier();
        boolean creative = mode == GameMode.CREATIVE;
        UtilityWorldArena.useOn(g, p.pos, ItemType.FIBER, 2);
        assertEquals(UrbanUtilities.SOUND, p.condition, 1e-6f);
        assertEquals(creative ? 2 : 0, g.player.inventory.count(ItemType.FIBER), "Creative spends no fibre");
        g.player.inventory.clear();
        UtilityWorldArena.useOn(g, p.pos, ItemType.WATERSKIN_DIRTY, 1);
        assertEquals(1f, p.untreated, 1e-6f, "poured water is real water in either mode");
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_EMPTY));
    }
}
