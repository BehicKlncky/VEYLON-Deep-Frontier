package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.world.World;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanVisits;
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
 * {@code urban.visits} v1 (urban milestone 21): the districts walked and buildings entered survive a
 * save and a load; absence (a save written before package 21) means nothing walked or entered; an
 * earlier city refuses the section; and a damaged section fails the whole load while the live world
 * carries on.
 */
class UrbanVisitsSectionTest {
    private static final long SEED = 20260731L;
    @TempDir Path directory;

    private static Game city() {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_URBAN_MISSIONS, GameMode.SURVIVAL);
        return g;
    }

    private static long building(int parcel) {
        return UrbanIds.building(UrbanIds.parcel(UrbanIds.district(0, 0), parcel), 0);
    }

    private static byte[] payload(int version, long[] districts, long... buildings) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(bytes)) {
            out.writeInt(version);
            out.writeInt(districts.length);
            for (long id : districts) { out.writeLong(id); }
            out.writeInt(buildings.length);
            for (long id : buildings) { out.writeLong(id); }
        }
        return bytes.toByteArray();
    }

    @Test
    void visitsSurviveASaveAndALoadAndAbsenceMeansNothingVisited() throws Exception {
        Game g = city();
        Path empty = directory.resolve("empty.sav");
        assertTrue(SaveSystem.save(g, empty));
        Game fresh = new Game();
        assertTrue(SaveSystem.load(fresh, empty));
        assertEquals(0, fresh.world.urban.visits.buildingCount(), "an empty ledger is written and read");

        UrbanVisits visits = g.world.urban.visits;
        assertTrue(visits.walk(UrbanIds.district(0, 0)));
        assertTrue(visits.walk(UrbanIds.district(-1, 0)));
        assertTrue(visits.enter(building(9)));
        assertTrue(visits.enter(building(2)));
        visits.enterRoom(UrbanIds.floor(building(2), 0), 4);
        Path file = directory.resolve("visited.sav");
        assertTrue(SaveSystem.save(g, file));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        assertEquals(visits.districts(), loaded.world.urban.visits.districts());
        assertEquals(visits.buildings(), loaded.world.urban.visits.buildings());
        assertEquals(0, loaded.world.urban.visits.sketchedFloors(), "the sketch's rooms are never saved");

        Path absent = directory.resolve("absent.sav");
        Files.write(absent, CreativeSaveSections.replace(Files.readAllBytes(file), UrbanVisitsSection.ID, null));
        Game none = new Game();
        assertTrue(SaveSystem.load(none, absent), "a package-20 save of the same city still loads");
        assertEquals(0, none.world.urban.visits.districtCount() + none.world.urban.visits.buildingCount());
    }

    @Test
    void aDamagedSectionFailsTheLoadAndTheLiveWorldCarriesOn() throws Exception {
        Game g = city();
        Path valid = directory.resolve("valid.sav");
        assertTrue(SaveSystem.save(g, valid));
        byte[] bytes = Files.readAllBytes(valid);
        long district = UrbanIds.district(0, 0), low = building(1), high = building(5);
        long room = UrbanIds.room(UrbanIds.floor(low, 0), 1);
        byte[] good = payload(1, new long[]{district}, low, high);
        byte[] trailing = java.util.Arrays.copyOf(good, good.length + 1);
        byte[] truncated = java.util.Arrays.copyOf(good, good.length - 3);
        ByteArrayOutputStream overCount = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(overCount)) {
            out.writeInt(1);
            out.writeInt(UrbanVisits.MAX_DISTRICTS + 1);
        }
        List<byte[]> broken = List.of(payload(2, new long[0]), payload(1, new long[]{district}, high, low),
                payload(1, new long[]{district}, low, low), payload(1, new long[]{low}), payload(1, new long[0], room),
                payload(1, new long[0], UrbanIds.NONE), trailing, truncated, overCount.toByteArray());
        for (int i = 0; i < broken.size(); i++) {
            Path file = directory.resolve("broken-" + i + ".sav");
            Files.write(file, CreativeSaveSections.replace(bytes, UrbanVisitsSection.ID, broken.get(i)));
            var world = g.world;
            assertFalse(SaveSystem.load(g, file), "damaged section " + i + " is refused");
            assertSame(world, g.world, "the live world survives a rejected load");
        }
        Path fine = directory.resolve("fine.sav");
        Files.write(fine, CreativeSaveSections.replace(bytes, UrbanVisitsSection.ID, good));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, fine), "the same ids in order load");
        assertTrue(loaded.world.urban.visits.entered(high));
    }

    @Test
    void anEarlierCityRefusesTheSectionAndWritesNone() throws Exception {
        Game eighteen = new Game();
        eighteen.newWorld(SEED, true, World.GEN_URBAN_UTILITIES, GameMode.SURVIVAL);
        Path older = directory.resolve("eighteen.sav");
        assertTrue(SaveSystem.save(eighteen, older));
        byte[] saved = Files.readAllBytes(older);
        assertThrows(IOException.class, () -> CreativeSaveSections.replace(saved, UrbanVisitsSection.ID, null),
                "a generator-18 save carries no urban.visits");
        Path foreign = directory.resolve("eighteen-with-visits.sav");
        Files.write(foreign, CreativeSaveSections.append(saved, UrbanVisitsSection.ID, payload(1, new long[0])));
        var world = eighteen.world;
        assertFalse(SaveSystem.load(eighteen, foreign), "an earlier city keeps no visits");
        assertSame(world, eighteen.world);
    }
}
