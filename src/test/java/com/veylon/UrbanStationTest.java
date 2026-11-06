package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.DistrictRules;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.LootTables;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.TransitStation;
import com.veylon.world.urban.UrbanContainers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 14: a transit station is a purposeful place. Its service room holds maintenance
 * lockers searched once from the utility table (light and pry tools for the dark below), and
 * encounter catalogue four keeps two Veilbound slots in every station outside the spawn region,
 * paid for out of the district's own threat budget.
 */
class UrbanStationTest {
    private static final long SEED = 20260731L;
    @TempDir Path directory;

    private static Game underground(long seed) {
        Game g = new Game();
        g.newWorld(seed, true, World.GEN_URBAN_UNDERGROUND, GameMode.SURVIVAL);
        return g;
    }

    @Test
    void stationLockersAreSearchedOnceFromTheUtilityTableAndNeverRefill() {
        Game g = underground(SEED);
        TransitStation station = g.world.urban.segment(true, 0, 0).station();
        List<BuildingPlan.Fixture> containers = station.plan().fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.CONTAINER).toList();
        assertEquals(3, containers.size(), "two lockers and a shelf");
        Vec3i locker = containers.getFirst().position();
        g.world.ensureChunks(locker.x(), locker.z(), 2, 2_000);
        UrbanContainers.Planned planned = UrbanContainers.planned(g.world, locker);
        assertNotNull(planned, "a station reservation resolves like a building's");
        assertSame(LootTables.Table.UTILITY, planned.table());
        assertTrue(g.crates.openCrateAt(locker));
        assertTrue("Maintenance locker".equalsIgnoreCase(g.crateTitle()), g.crateTitle());
        UrbanContainers.Entry entry = g.world.urban.containers.at(locker);
        assertEquals(containers.getFirst().id(), entry.id);
        Inventory rolled = g.world.crateContents.get(locker);
        String before = rolled == null ? "empty" : rolled.toString();

        Path file = directory.resolve("station.sav");
        assertTrue(SaveSystem.save(g, file));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        loaded.world.ensureChunks(locker.x(), locker.z(), 2, 2_000);
        UrbanContainers.Entry again = loaded.world.urban.containers.touch(loaded.world, locker);
        assertEquals(entry.id, again.id);
        Inventory after = loaded.world.crateContents.get(locker);
        assertEquals(before, after == null ? "empty" : after.toString(), "the same contents, never re-rolled");
    }

    @Test
    void catalogueFourSpendsAStationOutOfEachDistrictsBudget() {
        int stations = 0;
        for (long seed : new long[]{SEED, -4242L, 7L}) {
            Game g = underground(seed);
            World world = g.world;
            assertEquals(4, EncounterAnchors.catalogue(world));
            assertTrue(EncounterAnchors.forStation(world, 0, 0).isEmpty(), "the spawn region's station is quiet");
            for (int rz = -2; rz <= 2; rz++) {
                for (int rx = -2; rx <= 2; rx++) {
                    DistrictPlan plan = world.urban.regionPlan(rx, rz);
                    DistrictRules.Profile rules = DistrictRules.of(DistrictMap.districtOf(seed, rx, rz));
                    List<EncounterAnchors.Anchor> anchors = new ArrayList<>();
                    for (ParcelPlan parcel : plan.parcels()) { anchors.addAll(EncounterAnchors.forParcel(world, parcel)); }
                    List<EncounterAnchors.Anchor> station = EncounterAnchors.forStation(world, rx, rz);
                    anchors.addAll(station);
                    if (!station.isEmpty()) {
                        stations++;
                        assertEquals(EncounterAnchors.ownedStation(world, rx, rz).id(), station.getFirst().building());
                        assertTrue(station.get(1).indoor());
                    }
                    for (EncounterAnchors.Anchor anchor : anchors) {
                        assertEquals(anchor, EncounterAnchors.resolve(world, anchor.id()), "every anchor resolves");
                    }
                    if (rx != 0 || rz != 0) {
                        assertTrue(anchors.size() <= rules.threatMax(), rx + "," + rz + " " + anchors.size());
                    }
                }
            }
        }
        assertTrue(stations > 0, "stations near the spawn carry encounters");
    }

    @Test
    void aVeilboundWaitsOnAStationPlatformAndItsCatalogueIsSaved() {
        Game g = underground(SEED);
        EncounterAnchors.Anchor platform = null;
        for (int rz = -1; rz <= 1 && platform == null; rz++) {
            for (int rx = -1; rx <= 1 && platform == null; rx++) {
                for (EncounterAnchors.Anchor a : EncounterAnchors.forStation(g.world, rx, rz)) {
                    if (a.indoor()) { platform = a; }
                }
            }
        }
        assertNotNull(platform, "a station within one region of the spawn holds a platform slot");
        UrbanEncounterArena.observeAway(g, platform);
        Npc n = g.entities.encounters.tryActivate(g, platform);
        assertNotNull(n, "the platform is a supported spawn");
        assertEquals(platform.cell().y(), n.pos.y, 1e-3f);
        assertSame(com.veylon.world.urban.UrbanPlaces.Place.STATION,
                com.veylon.world.urban.UrbanPlaces.at(g.world, platform.cell().x(), platform.cell().y(), platform.cell().z()).place());
        Path file = directory.resolve("platform.sav");
        assertTrue(SaveSystem.save(g, file));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file), "catalogue four is generator 13's own");
        assertNotNull(loaded.entities.encounters.resident(platform.id()));
    }
}
