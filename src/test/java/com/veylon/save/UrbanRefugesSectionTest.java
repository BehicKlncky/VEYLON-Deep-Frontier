package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UrbanRefugesSectionTest {
    @TempDir Path directory;

    @Test
    void anchorRoundTripsAndLoadNeverRestocksConsumedCourtyardResources() {
        Game g = session(); var anchor = g.world.urban.refugeAnchor;
        var site = com.veylon.world.urban.UrbanStarterSite.of(g.world.urban);
        g.world.collectorWater.put(site.collector(), 0f); g.world.campfireFuel.put(site.fire(), 0f);
        Path save = directory.resolve("refuge.sav");
        assertTrue(SaveSystem.save(g, save)); g.newWorld(42, true, World.GEN_CAVE_IDENTITIES);
        assertTrue(SaveSystem.load(g, save));
        assertEquals(anchor, g.world.urban.refugeAnchor);
        assertEquals(0f, g.world.collectorWater.get(site.collector()));
        assertEquals(0f, g.world.campfireFuel.get(site.fire()));
        g.newWorld(7, false, World.GEN_URBAN_STARTER);
        assertNull(g.world.urban.refugeAnchor, "load bootstrap cannot claim or refill");
        assertTrue(g.world.collectorWater.isEmpty()); assertTrue(g.world.campfireFuel.isEmpty());
    }

    @Test
    void badVersionsIdsCoordinatesAndTrailingBytesAreRejected() throws IOException {
        Game g = session(); byte[] good = UrbanRefugesSection.write(g);
        byte[][] corrupt = {good.clone(), good.clone(), good.clone(), good.clone(),
                java.util.Arrays.copyOf(good, good.length + 1), java.util.Arrays.copyOf(good, good.length - 1)};
        ByteBuffer.wrap(corrupt[0]).putInt(0, 2);
        ByteBuffer.wrap(corrupt[1]).putLong(5, 7);
        ByteBuffer.wrap(corrupt[2]).putInt(17, 45);
        corrupt[3][4] = 2;
        for (byte[] payload : corrupt) {
            assertThrows(IOException.class, () -> UrbanRefugesSection.read(payload, g));
        }
        Vec3i anchor = g.world.urban.refugeAnchor;
        assertEquals(41, anchor.y());
        Game legacy = new Game(); legacy.newWorld(7, true, World.GEN_LEGACY);
        assertDoesNotThrow(() -> UrbanRefugesSection.finishLoad(legacy));
        assertThrows(IOException.class, () -> UrbanRefugesSection.read(good, legacy));
    }

    @Test
    void missingRequiredAnchorSectionFailsWithoutReplacingALiveWorld() throws Exception {
        Game g = session(); Path save = directory.resolve("missing.sav");
        assertTrue(SaveSystem.save(g, save));
        byte[] bytes = Files.readAllBytes(save);
        byte[] id = UrbanRefugesSection.ID.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int start = -1;
        for (int i = 0; i <= bytes.length - id.length; i++) {
            if (java.util.Arrays.equals(id, java.util.Arrays.copyOfRange(bytes, i, i + id.length))) { start = i; break; }
        }
        assertTrue(start > 0); bytes[start] = 'x'; Files.write(save, bytes);
        var original = g.world;
        assertFalse(SaveSystem.load(g, save)); assertSame(original, g.world);
    }

    private static Game session() {
        Game g = new Game(); g.newWorld(7, true, World.GEN_URBAN_STARTER, GameMode.SURVIVAL); return g;
    }

    /** Generator 17 with the starter claim and the neighbour's first-floor flat claimed as home. */
    private static Game refuges() {
        Game g = com.veylon.RefugeWorldArena.session(GameMode.SURVIVAL);
        var b = com.veylon.RefugeWorldArena.neighbour(g);
        var bed = com.veylon.RefugeWorldArena.fixture(b, 1, f -> f.block() == com.veylon.world.BlockType.CAMP_BED);
        g.world.urban.refuges.add(com.veylon.world.urban.UrbanIds.floor(b.id(), 1), bed.target(), g.time.totalMinutes);
        return g;
    }

    @Test
    void generatorSeventeenClaimsTheirBedsAndHomeComeBackExactly() {
        Game g = refuges();
        var before = g.world.urban.refuges;
        Path save = directory.resolve("claims.sav");
        assertTrue(SaveSystem.save(g, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        var after = loaded.world.urban.refuges;
        assertEquals(before.size(), after.size());
        for (int i = 0; i < before.size(); i++) {
            assertEquals(before.claims().get(i).floor, after.claims().get(i).floor);
            assertEquals(before.claims().get(i).sleep, after.claims().get(i).sleep);
            assertEquals(before.claims().get(i).claimedMinute, after.claims().get(i).claimedMinute, 1e-9);
        }
        assertEquals(before.homeFloor(), after.homeFloor());
        assertEquals(g.world.urban.refugeAnchor, loaded.world.urban.refugeAnchor, "the starter anchor rides along");
    }

    @Test
    void everyMalformedClaimFailsTheWholeLoadAndEachWorldReadsOnlyItsOwnVersion() throws IOException {
        Game g = refuges();
        byte[] good = UrbanRefugesSection.write(g);
        // version, starter (presence, building, anchor), count, then two 29-byte claims and home.
        assertEquals(4 + 1 + 8 + 12 + 4 + 2 * 29 + 8, good.length);
        int first = 29, second = 58, home = 87;
        long secondFloor = ByteBuffer.wrap(good).getLong(second);
        var neighbour = com.veylon.RefugeWorldArena.neighbour(g);
        long roof = com.veylon.world.urban.UrbanIds.floor(neighbour.id(), neighbour.storeys());
        long other = com.veylon.world.urban.UrbanIds.floor(com.veylon.world.urban.UrbanIds.building(
                com.veylon.world.urban.UrbanIds.parcel(com.veylon.world.urban.UrbanIds.district(0, 0), 127), 0), 0);
        java.util.List<byte[]> bad = java.util.List.of(
                withInt(good, 0, 1),                                     // generator 17 reads only version 2
                withInt(good, 25, 4),                                    // more claims than any survivor keeps
                withLong(good, second, com.veylon.world.urban.UrbanIds.parent(secondFloor)), // a building, not a storey
                withLong(good, second, other),                           // a storey of no planned building
                withLong(good, second, roof),                            // a roof
                withLong(good, second, ByteBuffer.wrap(good).getLong(first)), // a storey claimed twice
                withByte(good, second + 8, 2),                           // an unknown bed presence
                withInt(good, second + 13, 41),                          // the bed on another storey
                withInt(good, second + 9, neighbour.parcel().x1() + 3),  // the bed outside the building
                withDouble(good, second + 21, g.time.totalMinutes + 60), // claimed in the future
                withDouble(good, second + 21, Double.NaN),
                withLong(good, home, com.veylon.world.urban.UrbanIds.floor(neighbour.id(), 2)), // home is no claim
                java.util.Arrays.copyOf(good, good.length + 1));         // trailing byte
        for (int i = 0; i < bad.size(); i++) {
            byte[] payload = bad.get(i);
            int index = i;
            assertThrows(IOException.class, () -> UrbanRefugesSection.read(payload, com.veylon.RefugeWorldArena.session(GameMode.SURVIVAL)),
                    "malformed case " + index + " must fail");
        }
        assertDoesNotThrow(() -> UrbanRefugesSection.read(good, com.veylon.RefugeWorldArena.session(GameMode.SURVIVAL)));
        // An earlier starter world reads only version 1, so a claims payload is no use there.
        Game starter = session();
        assertThrows(IOException.class, () -> UrbanRefugesSection.read(good, starter));
        byte[] v1 = UrbanRefugesSection.write(starter);
        assertEquals(1, ByteBuffer.wrap(v1).getInt(0), "generators before 17 keep writing version 1");
        assertEquals(25, v1.length, "byte for byte the starter layout");
    }

    private static byte[] withInt(byte[] b, int at, int value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putInt(at, value);
        return out;
    }

    private static byte[] withLong(byte[] b, int at, long value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putLong(at, value);
        return out;
    }

    private static byte[] withDouble(byte[] b, int at, double value) {
        byte[] out = b.clone();
        ByteBuffer.wrap(out).putDouble(at, value);
        return out;
    }

    private static byte[] withByte(byte[] b, int at, int value) {
        byte[] out = b.clone();
        out[at] = (byte) value;
        return out;
    }
}
