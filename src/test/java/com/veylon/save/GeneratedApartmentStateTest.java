package com.veylon.save;

import com.veylon.FixtureActions;
import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlanDiagnostics;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Production save/load of generated interiors, rather than a hand-built fixture arena. */
class GeneratedApartmentStateTest {
    @TempDir Path directory;

    @Test
    void savedUnlockOpenDoorBreaksAndDestroyedFurnitureOverrideGeneratedDefaults() {
        Game original = new Game();
        original.newWorld(20260731, true, World.GEN_URBAN_APARTMENTS, GameMode.SURVIVAL);
        BuildingPlan plan = occupied(original.world);
        ParcelPlan p = plan.parcel();
        original.world.ensureChunks((p.x0() + p.x1()) / 2, (p.z0() + p.z1()) / 2, 2, 100);
        var locked = plan.fixtures().stream().filter(BuildingPlan.Fixture::locked).findFirst().orElseThrow();
        var untouchedLock = plan.fixtures().stream().filter(f -> f.locked() && f.id() != locked.id()).findFirst().orElseThrow();
        Vec3i lock = locked.position();
        assertEquals(FixtureStates.LOCK_KEYED, original.world.fixtureLockAt(lock.x(), lock.y(), lock.z()));
        assertFalse(FixtureActions.residentOpens(original, lock.x(), lock.y(), lock.z()));
        assertTrue(original.world.fixtures.isEmpty(), "untouched generated locks are derived");
        for (int h = 0; h < locked.height(); h++) {
            original.world.fixtures.set(lock.x(), lock.y() + h, lock.z(), FixtureStates.LOCK_NONE);
        }
        var door = plan.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR && !f.locked())
                .findFirst().orElseThrow();
        Vec3i opened = door.position();
        original.player.pos.set(door.target().x() + .5f, door.target().y(), door.target().z() + .5f);
        assertTrue(original.interactWithBlockAt(opened));
        assertEquals(BlockType.WOOD_DOOR_OPEN, original.world.getBlock(opened.x(), opened.y(), opened.z()));
        var window = plan.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.WINDOW).findFirst().orElseThrow();
        Vec3i broken = window.position();
        assertTrue(original.completePlayerBlockBreak(broken));
        Vec3i cabinet = plan.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.CONTAINER)
                .findFirst().orElseThrow().position();
        assertTrue(original.openCrateAt(cabinet));
        assertTrue(original.openCrate.isEmpty(), "07 has not rolled contents");
        original.openCrate.add(ItemType.BANDAGE, 2);
        original.closeScreens();
        original.world.setBlock(cabinet.x(), cabinet.y(), cabinet.z(), BlockType.AIR, true);
        assertFalse(original.world.crateContents.containsKey(cabinet));
        Path save = directory.resolve("generated-apartment.sav");
        assertTrue(SaveSystem.save(original, save));
        Game loaded = new Game();
        loaded.newWorld(7, true, World.GEN_URBAN, GameMode.CREATIVE);
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(GameMode.SURVIVAL, loaded.gameMode());
        assertEquals(UrbanRevision.LAYOUT_GENERATED_APARTMENTS, loaded.world.urban.layoutRevision());
        assertEquals(BuildingPlanDiagnostics.fingerprint(plan), BuildingPlanDiagnostics.fingerprint(
                loaded.world.urban.buildingAt(p.x0(), p.z0())));
        assertEquals(FixtureStates.LOCK_NONE, loaded.world.fixtureLockAt(lock.x(), lock.y(), lock.z()));
        Vec3i untouched = untouchedLock.position();
        loaded.world.getOrCreateChunk(Math.floorDiv(untouched.x(), 16), Math.floorDiv(untouched.z(), 16));
        assertEquals(FixtureStates.LOCK_KEYED, loaded.world.fixtureLockAt(untouched.x(), untouched.y(), untouched.z()));
        assertEquals(BlockType.WOOD_DOOR_OPEN, loaded.world.getBlock(opened.x(), opened.y(), opened.z()));
        assertEquals(BlockType.BROKEN_WINDOW, loaded.world.getBlock(broken.x(), broken.y(), broken.z()));
        assertEquals(BlockType.AIR, loaded.world.getBlock(cabinet.x(), cabinet.y(), cabinet.z()));
        assertFalse(loaded.openCrateAt(cabinet));
        assertFalse(loaded.world.crateContents.containsKey(cabinet));

        // Fresh chunk recreation with the restored deltas, in reverse parcel order.
        World regenerated = new World(loaded.world.seed, loaded.world.generatorVersion);
        regenerated.changedBlocks.putAll(loaded.world.changedBlocks);
        loaded.world.fixtures.forEach((at, state) -> regenerated.restoreFixtureState(at, state));
        for (int cz = Math.floorDiv(p.z1(), 16); cz >= Math.floorDiv(p.z0(), 16); cz--) {
            for (int cx = Math.floorDiv(p.x1(), 16); cx >= Math.floorDiv(p.x0(), 16); cx--) {
                regenerated.getOrCreateChunk(cx, cz);
            }
        }
        assertEquals(BlockType.AIR, regenerated.getBlock(cabinet.x(), cabinet.y(), cabinet.z()));
        assertEquals(BlockType.BROKEN_WINDOW, regenerated.getBlock(broken.x(), broken.y(), broken.z()));
        assertEquals(BlockType.WOOD_DOOR_OPEN, regenerated.getBlock(opened.x(), opened.y(), opened.z()));
        assertEquals(FixtureStates.LOCK_NONE, regenerated.fixtureLockAt(lock.x(), lock.y(), lock.z()));
    }

    private static BuildingPlan occupied(World world) {
        for (ParcelPlan p : world.urban.regionPlan(0, 0).parcels()) {
            BuildingPlan plan = world.urban.buildingAt(p.x0(), p.z0());
            if (plan.variant() == BuildingPlan.Variant.OCCUPIED) { return plan; }
        }
        throw new AssertionError("fixture seed has no occupied building");
    }
}
