package com.veylon.save;

import com.veylon.Game;
import com.veylon.UrbanEncounterArena;
import com.veylon.entity.GameMode;
import com.veylon.world.World;
import com.veylon.world.urban.EncounterAnchors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Generator 11 freezes encounter catalogue two; neither catalogue is reinterpreted as the other. */
class UrbanCivicSaveTest {
    @TempDir Path directory;

    @Test
    void eachEncounterGeneratorReadsOnlyItsOwnFrozenCatalogue() throws Exception {
        for (int generator : new int[]{World.GEN_URBAN_ENCOUNTERS, World.GEN_URBAN_CIVIC}) {
            Game g = new Game();
            g.newWorld(7, true, generator, GameMode.SURVIVAL);
            UrbanEncounterArena.activate(g, true);
            int catalogue = generator == World.GEN_URBAN_CIVIC ? 2 : 1;
            assertEquals(catalogue, EncounterAnchors.catalogue(g.world));
            byte[] payload = UrbanPopulationSection.write(g);
            assertEquals(1, ByteBuffer.wrap(payload).getInt(0), "codec version is unchanged");
            assertEquals(catalogue, ByteBuffer.wrap(payload).getInt(4));
            Path valid = directory.resolve("catalogue-" + generator + ".sav");
            assertTrue(SaveSystem.save(g, valid));
            assertTrue(SaveSystem.load(g, valid));
            assertEquals(1, g.entities.encounters.size());

            byte[] other = payload.clone();
            ByteBuffer.wrap(other).putInt(4, 3 - catalogue);
            Path broken = directory.resolve("other-" + generator + ".sav");
            Files.write(broken, CreativeSaveSections.replace(Files.readAllBytes(valid), UrbanPopulationSection.ID, other));
            var world = g.world;
            var player = g.player;
            assertFalse(SaveSystem.load(g, broken), "generator " + generator + " rejects the other catalogue");
            assertSame(world, g.world);
            assertSame(player, g.player);
        }
    }
}
