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
        assertTrue(SaveSystem.save(g, save)); g.newWorld(42, true);
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
}
