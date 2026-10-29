package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanSite;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.UrbanUtilities;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The taps of a layout-fifteen city (urban milestone 19): a building's planned basins share the
 * finite stale water left in its pipes, untreated, and nothing ever puts it back; a basin set down
 * by hand is still a rain collector, survivors' taps are theirs, and generator 17 is unchanged.
 */
class UrbanTapsTest {
    private static String lastLog(Game g) {
        var all = g.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }

    private static void drawAt(Game g, BuildingPlan.Fixture tap) {
        SurvivorWorldArena.standAt(g, tap.target());
        SurvivorWorldArena.pressF(g, tap.position());
    }

    @Test
    void aBuildingsTapsShareTheStaleWaterLeftInItsPipesAndNeverRefill() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        List<BuildingPlan.Fixture> taps = UtilityWorldArena.basins(b);
        assertTrue(taps.size() >= 2, "a walk-up has a basin in its kitchens and bathrooms");
        int held = UrbanUtilities.capacity(b);
        assertTrue(held > 0 && held <= taps.size() * UrbanUtilities.LITRES_PER_BASIN, "held=" + held);
        g.player.inventory.clear();
        UtilityWorldArena.giveEmptySkins(g, held + 1);
        for (int i = 0; i < held; i++) {
            drawAt(g, taps.get(i % taps.size())); // any tap of the building draws on the same pipes
            assertTrue(lastLog(g).contains("stale water"), lastLog(g));
        }
        assertEquals(held, g.player.inventory.count(ItemType.WATERSKIN_DIRTY), "every litre is untreated");
        assertEquals(0, g.player.inventory.count(ItemType.WATERSKIN_CLEAN));
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_EMPTY));
        assertEquals(0, g.world.urban.utilities.left(b));
        assertEquals(held, g.world.urban.utilities.drawn(b.id()));

        drawAt(g, taps.getLast());
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_EMPTY), "a dry tap fills nothing");
        assertTrue(lastLog(g).contains("The tap is dry"), lastLog(g));
        RefugeWorldArena.aim(g, taps.getFirst().position());
        assertEquals("Tap: dry, its building's pipes are empty", g.refuges.utilities.tapPrompt(taps.getFirst().position()));

        // Rain and time put nothing back into the pipes.
        g.weather.current = g.weather.next = WeatherSystem.Weather.RAIN;
        g.weather.blend = 1f;
        g.weather.changeTimer = 4000f;
        SurvivorWorldArena.run(g, 30f);
        assertEquals(0, g.world.urban.utilities.left(b));
    }

    @Test
    void drinkingStraightFromATapQuenchesAsUntreatedWaterDoes() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        BuildingPlan.Fixture tap = UtilityWorldArena.basins(b).getFirst();
        g.player.inventory.clear();
        g.player.thirst = 20f;
        SurvivorWorldArena.standAt(g, tap.target());
        RefugeWorldArena.aim(g, tap.position());
        assertTrue(g.refuges.utilities.tapPrompt(tap.position()).startsWith("[F] Drink stale tap water (untreated"),
                g.refuges.utilities.tapPrompt(tap.position()));
        SurvivorWorldArena.pressF(g, tap.position());
        assertEquals(55f, g.player.thirst, 1e-3f, "35 thirst, as from open water");
        assertEquals(1, g.world.urban.utilities.drawn(b.id()));
        assertTrue(lastLog(g).contains("stale water from the tap"), lastLog(g));
    }

    @Test
    void aBasinSetDownByHandIsARainCollectorAsItAlwaysWas() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        BuildingPlan.Fixture tap = UtilityWorldArena.basins(b).getFirst();
        Vec3i p = tap.position();
        SurvivorWorldArena.standAt(g, tap.target());
        g.world.setBlock(p.x(), p.y(), p.z(), BlockType.AIR, true);
        g.world.setBlock(p.x(), p.y(), p.z(), BlockType.RAIN_COLLECTOR, true);
        assertNull(UrbanUtilities.tapBuilding(g.world, p.x(), p.y(), p.z()), "a recorded basin is the player's own");
        g.player.inventory.clear();
        UtilityWorldArena.giveEmptySkins(g, 1);
        SurvivorWorldArena.pressF(g, p);
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_EMPTY));
        assertTrue(lastLog(g).contains("It fills while it rains"), lastLog(g));
        assertEquals(0, g.world.urban.utilities.tappedCount());

        // The starter courtyard's collector stands outside every building and keeps its rainwater.
        Vec3i collector = UrbanStarterSite.of(g.world.urban).collector();
        SurvivorWorldArena.standAt(g, collector.offset(1, 0, 0));
        assertNull(UrbanUtilities.tapBuilding(g.world, collector.x(), collector.y(), collector.z()));
        SurvivorWorldArena.pressF(g, collector);
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_CLEAN), "rainwater is clean");
    }

    @Test
    void theRefugeCityKeepsItsBasinsAsEmptyCollectors() {
        Game g = RefugeWorldArena.session(GameMode.SURVIVAL);
        BuildingPlan b = RefugeWorldArena.neighbour(g);
        BuildingPlan.Fixture basin = UtilityWorldArena.basins(b).getFirst();
        g.player.inventory.clear();
        UtilityWorldArena.giveEmptySkins(g, 1);
        drawAt(g, basin);
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_EMPTY), "generator 17 has no taps");
        assertTrue(lastLog(g).contains("The collector holds 0.0 L"), lastLog(g));
        assertNull(UrbanUtilities.tapBuilding(g.world, basin.position().x(), basin.position().y(), basin.position().z()));
    }

    @Test
    void survivorsTapsRunForThePlayerOnlyOnceNobodyOfTheirsIsLeft() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        BuildingPlan camp = SurvivorWorldArena.building(g, plan);
        BuildingPlan.Fixture tap = UtilityWorldArena.basins(camp).stream()
                .filter(f -> com.veylon.world.urban.UrbanIds.floorIndex(f.id()) == 0).findFirst().orElseThrow();
        g.player.inventory.clear();
        UtilityWorldArena.giveEmptySkins(g, 1);
        drawAt(g, tap);
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_EMPTY), "the camp's water is theirs");
        assertTrue(lastLog(g).contains("These taps are the survivors'"), lastLog(g));
        assertEquals("Tap: the survivors' water", g.refuges.utilities.tapPrompt(tap.position()));
        assertEquals(0, g.world.urban.utilities.drawn(camp.id()));

        UrbanSite site = g.settlementManager.urbanSites.site(g, plan.id());
        for (Settlement.Resident r : site.residents) { r.alive = false; }
        drawAt(g, tap);
        assertEquals(1, g.player.inventory.count(ItemType.WATERSKIN_DIRTY), "an abandoned building's taps are anybody's");
        assertEquals(1, g.world.urban.utilities.drawn(camp.id()));
    }

    @Test
    void wornBuildingsHoldLessAndOnlyPlannedBasinsAreTaps() {
        Game g = UtilityWorldArena.session(GameMode.SURVIVAL);
        int intact = 0, worn = 0;
        for (var parcel : g.world.urban.regionPlan(0, 0).parcels()) {
            BuildingPlan b = g.world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (b == null) { continue; }
            int basins = UtilityWorldArena.basins(b).size();
            int held = UrbanUtilities.capacity(b);
            int full = basins * UrbanUtilities.LITRES_PER_BASIN;
            switch (b.variant()) {
                case INTACT, OCCUPIED -> { assertEquals(full, held, b.template()); intact++; }
                case PICKED_CLEAN, DAMAGED -> {
                    assertTrue(held < full || basins == 0, "others drew or burst pipes drained " + b.template());
                    worn++;
                }
            }
        }
        assertTrue(intact > 0 && worn > 0, "the spawn region holds both: intact=" + intact + " worn=" + worn);
    }
}
