package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code urban.discovery} v1 (milestone 14): what a layout-ten survivor has found survives a save
 * and a load, absence means nothing found, a world without routes refuses the section, and a
 * damaged section fails the whole load while the live world carries on.
 */
class UrbanDiscoverySaveTest {
    private static final long SEED = 20260731L;
    @TempDir Path directory;

    private static Game underground() {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_URBAN_UNDERGROUND, GameMode.SURVIVAL);
        return g;
    }

    private static long station(Game g) { return g.world.urban.segment(true, 0, 0).station().id(); }

    private static long cellar(Game g) {
        var parcel = g.world.urban.regionPlan(0, 0).parcel(g.world.urban.routes(0, 0).hiddenCellar());
        return UrbanIds.room(UrbanIds.floor(UrbanIds.building(parcel.id(), 0), -1), 8);
    }

    private static byte[] payload(int version, long... ids) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
            out.writeInt(ids.length);
            for (long id : ids) { out.writeLong(id); }
        }
        return bytes.toByteArray();
    }

    @Test
    void foundSitesSurviveASaveAndALoadAndAbsenceMeansNothingFound() throws Exception {
        Game g = underground();
        Path empty = directory.resolve("empty.sav");
        assertTrue(SaveSystem.save(g, empty));
        Game fresh = new Game();
        assertTrue(SaveSystem.load(fresh, empty));
        assertEquals(0, fresh.world.urban.discovery.size(), "an empty ledger is written and read");

        assertTrue(g.world.urban.discovery.discover(station(g)));
        assertTrue(g.world.urban.discovery.discover(cellar(g)));
        Path file = directory.resolve("found.sav");
        assertTrue(SaveSystem.save(g, file));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        assertEquals(g.world.urban.discovery.sites(), loaded.world.urban.discovery.sites());
        assertTrue(loaded.world.urban.discovery.discovered(station(loaded)));
        assertTrue(loaded.world.urban.discovery.discovered(cellar(loaded)));

        Path absent = directory.resolve("absent.sav");
        Files.write(absent, CreativeSaveSections.replace(Files.readAllBytes(file), UrbanDiscoverySection.ID, null));
        Game none = new Game();
        assertTrue(SaveSystem.load(none, absent));
        assertEquals(0, none.world.urban.discovery.size(), "absent means nothing has been found");
    }

    @Test
    void aDamagedSectionFailsTheLoadAndTheLiveWorldCarriesOn() throws Exception {
        Game g = underground();
        long station = station(g), cellar = cellar(g);
        long fixture = UrbanIds.fixture(UrbanIds.room(UrbanIds.floor(UrbanIds.building(
                UrbanIds.parcel(UrbanIds.district(0, 0), 3), 0), 0), 1), 2);
        Path valid = directory.resolve("valid.sav");
        assertTrue(SaveSystem.save(g, valid));
        byte[] bytes = Files.readAllBytes(valid);
        long low = Math.min(station, cellar), high = Math.max(station, cellar);
        byte[] trailing = java.util.Arrays.copyOf(payload(1, low), payload(1, low).length + 1);
        List<byte[]> broken = List.of(payload(2), payload(1, high, low), payload(1, low, low), payload(1, fixture),
                trailing, payload(1, UrbanIds.NONE));
        for (int i = 0; i < broken.size(); i++) {
            Path file = directory.resolve("broken-" + i + ".sav");
            Files.write(file, CreativeSaveSections.replace(bytes, UrbanDiscoverySection.ID, broken.get(i)));
            var world = g.world;
            assertFalse(SaveSystem.load(g, file), "damaged section " + i + " is refused");
            assertSame(world, g.world, "the live world survives a rejected load");
        }
        Game twelve = new Game();
        twelve.newWorld(SEED, true, World.GEN_URBAN_DISTRICTS, GameMode.SURVIVAL);
        Path older = directory.resolve("twelve.sav");
        assertTrue(SaveSystem.save(twelve, older));
        Path foreign = directory.resolve("twelve-with-discovery.sav");
        Files.write(foreign, CreativeSaveSections.append(Files.readAllBytes(older), UrbanDiscoverySection.ID, payload(1)));
        var world = twelve.world;
        assertFalse(SaveSystem.load(twelve, foreign), "a world without routes has nothing to discover");
        assertSame(world, twelve.world);
    }
}
