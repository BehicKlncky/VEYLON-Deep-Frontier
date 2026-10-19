package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import com.veylon.world.urban.RouteCatalogue;
import com.veylon.world.urban.TransitStation;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generator 13 (milestone 14) persists through the existing codecs: its layout revision in
 * {@code urban.meta}, what a survivor does to tunnels, stations and service passages as
 * recorded block edits, and an opened door's state in {@code world.fixtures}. Nothing is
 * refilled or re-closed by regeneration after a load.
 */
class UrbanUndergroundSaveTest {
    private static final long SEED = 20260731L;
    @TempDir Path directory;

    @Test
    void routeEditsAndOpenedDoorsSurviveASaveAndALoad() {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_URBAN_UNDERGROUND, GameMode.SURVIVAL);
        World w = g.world;
        TransitStation station = w.urban.segment(true, 0, 0).station();
        Vec3i door = station.plan().fixtures().stream()
                .filter(f -> f.kind() == com.veylon.world.urban.BuildingPlan.FixtureKind.DOOR).findFirst().orElseThrow().position();
        Vec3i platform = station.plan().floors().getFirst().landing();
        w.ensureChunks(platform.x(), platform.z(), 2, 1_000);
        RouteCatalogue.Corridor corridor = w.urban.routes(0, 0).corridors().getFirst();
        Vec3i passage = new Vec3i(corridor.x0() + 1, RouteCatalogue.SERVICE_Y, corridor.z0());
        w.ensureChunks(passage.x(), passage.z(), 1, 1_000);

        assertSame(BlockType.METAL_DOOR, w.getBlock(door.x(), door.y(), door.z()));
        for (int h = 0; h < 2; h++) {
            w.setBlock(door.x(), door.y() + h, door.z(), BlockType.METAL_DOOR_OPEN, false);
            w.fixtures.set(door.x(), door.y() + h, door.z(), FixtureStates.OPEN);
        }
        // A survivor walls the service passage off and digs into the platform.
        w.setBlock(passage.x(), passage.y(), passage.z(), BlockType.CONCRETE, true);
        w.setBlock(platform.x(), platform.y() - 1, platform.z(), BlockType.AIR, true);

        Path file = directory.resolve("underground.sav");
        assertTrue(SaveSystem.save(g, file));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        World l = loaded.world;
        assertEquals(World.GEN_URBAN_UNDERGROUND, l.generatorVersion);
        assertEquals(UrbanRevision.LAYOUT_UNDERGROUND, l.urban.layoutRevision());
        assertTrue(l.urban.hasDistricts() && l.urban.hasUnderground());
        l.ensureChunks(platform.x(), platform.z(), 2, 1_000);
        l.ensureChunks(passage.x(), passage.z(), 1, 1_000);
        assertSame(BlockType.METAL_DOOR_OPEN, l.getBlock(door.x(), door.y(), door.z()), "the opened door stays open");
        assertSame(BlockType.METAL_DOOR_OPEN, l.getBlock(door.x(), door.y() + 1, door.z()));
        assertSame(BlockType.CONCRETE, l.getBlock(passage.x(), passage.y(), passage.z()), "the wall-up persists");
        assertSame(BlockType.AIR, l.getBlock(platform.x(), platform.y() - 1, platform.z()), "the dug platform persists");
    }
}
