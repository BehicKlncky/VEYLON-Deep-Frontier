package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.Inventory;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.LootTables;
import com.veylon.world.urban.TransitStation;
import com.veylon.world.urban.UrbanContainers;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 14: a transit station is a purposeful place. Its service room holds maintenance
 * lockers searched once from the utility table: light and pry tools for the dark below.
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
}
