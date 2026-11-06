package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.StairGeometry;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlanDiagnostics;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class GeneratedShapedStairStateTest {
    @TempDir Path directory;

    @Test
    void aBrokenGeneratedTreadStaysAbsentAfterLoadingOverALiveOldWorld() {
        Game game = new Game();
        game.newWorld(20260731, true, World.GEN_URBAN_SHAPED_STAIRS, GameMode.SURVIVAL);
        var parcel = game.world.urban.regionPlan(0, 0).refuge();
        var plan = game.world.urban.buildingAt(parcel.x0(), parcel.z0());
        Vec3i feet = plan.stairs().getFirst().route().get(1);
        Vec3i tread = new Vec3i(feet.x(), feet.y() - 1, feet.z());
        game.world.getOrCreateChunk(Math.floorDiv(tread.x(), 16), Math.floorDiv(tread.z(), 16));
        assertTrue(StairGeometry.isStair(game.world.getBlock(tread.x(), tread.y(), tread.z())));
        assertTrue(game.completePlayerBlockBreak(tread));
        Path save = directory.resolve("broken-generated-stair.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        loaded.newWorld(7, true, World.GEN_URBAN_APARTMENTS, GameMode.CREATIVE);
        assertTrue(SaveSystem.load(loaded, save));
        loaded.world.getOrCreateChunk(Math.floorDiv(tread.x(), 16), Math.floorDiv(tread.z(), 16));
        assertEquals(World.GEN_URBAN_SHAPED_STAIRS, loaded.world.generatorVersion);
        assertEquals(UrbanRevision.LAYOUT_SHAPED_STAIRS, loaded.world.urban.layoutRevision());
        assertSame(BlockType.AIR, loaded.world.getBlock(tread.x(), tread.y(), tread.z()));
        assertEquals(BuildingPlanDiagnostics.fingerprint(plan), BuildingPlanDiagnostics.fingerprint(
                loaded.world.urban.buildingAt(parcel.x0(), parcel.z0())));
    }
}
