package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Player;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureGeometry;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanRevision;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Generated double entrances must open against their outer jambs and remain targetable there. */
class GeneratedEntranceGeometryTest {
    @Test
    void generatedEntranceLeavesRestAtBothOuterJambsInEveryFrontageAndMode() {
        for (GameMode mode : GameMode.values()) {
            Game game = new Game();
            game.world = new World(20260731, UrbanRevision.DEVELOPMENT_GENERATOR);
            game.world.urban.resolveContentRevision(UrbanRevision.CURRENT_CONTENT);
            game.player = new Player(game.world);
            game.restoreGameMode(mode, mode == GameMode.CREATIVE, false);
            for (ParcelPlan.Side frontage : ParcelPlan.Side.values()) {
                BuildingPlan building = game.world.urban.regionPlan(0, 0).parcels().stream()
                        .filter(p -> p.frontage() == frontage)
                        .map(p -> game.world.urban.buildingAt(p.x0(), p.z0()))
                        .filter(p -> p.variant() != BuildingPlan.Variant.OCCUPIED)
                        .findFirst().orElseThrow();
                for (ParcelPlan.Entrance entrance : building.parcel().entrances()) {
                    checkEntrance(game, entrance);
                }
            }
            assertTrue(game.world.changedBlocks.isEmpty(), "opening leaves changes no generated block edits");
        }
    }

    private static void checkEntrance(Game game, ParcelPlan.Entrance entrance) {
        boolean alongX = entrance.facing().dx == 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                game.world.getOrCreateChunk(Math.floorDiv(entrance.x(), 16) + dx,
                        Math.floorDiv(entrance.z(), 16) + dz);
            }
        }
        Vec3i[] leaves = {new Vec3i(entrance.x(), 41, entrance.z()),
                new Vec3i(entrance.x2(), 41, entrance.z2())};
        for (Vec3i leaf : leaves) {
            assertTrue(castAt(game.world, leaf, entrance.facing(), .5f, true), "closed entrance is targetable");
            for (int h = 0; h < 2; h++) {
                int y = leaf.y() + h;
                BlockType closed = game.world.getBlock(leaf.x(), y, leaf.z());
                assertTrue(closed.solid && closed.opaque, "the thin panel still blocks movement and world light");
                float[] box = new float[6];
                assertTrue(FixtureGeometry.thinBox(game.world, leaf.x(), y, leaf.z(), closed, box));
                int axis = alongX ? 2 : 0;
                int coordinate = alongX ? leaf.z() : leaf.x();
                assertEquals(coordinate + .5f - FixtureGeometry.CLOSED_DOOR_THICKNESS / 2, box[axis]);
                assertEquals(coordinate + .5f + FixtureGeometry.CLOSED_DOOR_THICKNESS / 2, box[axis + 3]);
            }
            game.player.pos.set(leaf.x() + .5f + entrance.facing().dx,
                    leaf.y(), leaf.z() + .5f + entrance.facing().dz);
            assertTrue(game.interactWithBlockAt(leaf));
        }
        for (int i = 0; i < leaves.length; i++) {
            Vec3i leaf = leaves[i];
            for (int h = 0; h < 2; h++) {
                Vec3i cell = new Vec3i(leaf.x(), leaf.y() + h, leaf.z());
                float[] box = new float[6];
                BlockType type = game.world.getBlock(cell.x(), cell.y(), cell.z());
                assertEquals(BlockType.WOOD_DOOR_OPEN, type);
                assertTrue(FixtureGeometry.thinBox(game.world, cell.x(), cell.y(), cell.z(), type, box));
                int axis = alongX ? 0 : 2;
                int coordinate = alongX ? cell.x() : cell.z();
                assertEquals(coordinate + (i == 0 ? 0 : 1f - FixtureGeometry.LEAF_THICKNESS), box[axis]);
                assertEquals(coordinate + (i == 0 ? FixtureGeometry.LEAF_THICKNESS : 1f), box[axis + 3]);
                for (boolean outside : new boolean[]{true, false}) {
                    assertTrue(castAt(game.world, cell, entrance.facing(), i == 0 ? .06f : .94f, outside),
                            "the crosshair can close the leaf where the mesher draws it");
                    assertFalse(castAt(game.world, cell, entrance.facing(), i == 0 ? .94f : .06f, outside),
                            "no leaf stands in the middle of the double entrance");
                }
            }
        }
    }

    private static boolean castAt(World world, Vec3i cell, ParcelPlan.Side facing, float along, boolean outside) {
        float side = outside ? 1.5f : -1.5f;
        Vector3f origin = new Vector3f(cell.x() + .5f + facing.dx * side,
                cell.y() + .5f, cell.z() + .5f + facing.dz * side);
        if (facing.dx == 0) { origin.x = cell.x() + along; }
        else { origin.z = cell.z() + along; }
        Vector3f direction = new Vector3f(-facing.dx, 0, -facing.dz);
        if (!outside) { direction.negate(); }
        Raycaster.Hit hit = Raycaster.cast(world, origin, direction, 2, false);
        return hit != null && hit.x() == cell.x() && hit.y() == cell.y() && hit.z() == cell.z();
    }

    @Test
    void closedDoorSideTargetingHitsThePanelAndPassesItsFormerCubeFaces() {
        for (BlockType type : new BlockType[]{BlockType.WOOD_DOOR, BlockType.METAL_DOOR}) {
            for (boolean alongX : new boolean[]{true, false}) {
                World world = new World(7, World.GEN_URBAN);
                world.getOrCreateChunk(0, 0);
                int dx = alongX ? 1 : 0, dz = alongX ? 0 : 1;
                world.setBlock(8 - dx, 45, 8 - dz, BlockType.BRICK, false);
                world.setBlock(8 + dx, 45, 8 + dz, BlockType.BRICK, false);
                world.setBlock(8, 45, 8, type, false);
                Vector3f direction = new Vector3f(dx, 0, dz);
                Vector3f origin = new Vector3f(8.5f - dx, 45.5f, 8.5f - dz);
                Raycaster.Hit panel = Raycaster.cast(world, origin, direction, 1, false);
                assertNotNull(panel);
                assertEquals(type, panel.type());
                assertEquals(8, panel.x());
                assertEquals(8, panel.z());
                // A sideways ray in the front half-cell no longer targets a full cube face.
                origin.add(alongX ? 0 : -.3f, 0, alongX ? -.3f : 0);
                assertNull(Raycaster.cast(world, origin, direction, 1, false));
            }
        }
    }

    @Test
    void aSurvivingDoorLeafKeepsItsOuterJambAfterItsPartnerIsBrokenOrRemoved() {
        World world = new World(7, World.GEN_URBAN);
        world.getOrCreateChunk(0, 0);
        world.setBlock(9, 45, 8, BlockType.BRICK, false);
        world.setBlock(8, 45, 8, BlockType.METAL_DOOR_OPEN, false);
        for (BlockType partner : new BlockType[]{BlockType.METAL_DOOR, BlockType.METAL_DOOR_OPEN,
                BlockType.BROKEN_DOOR, BlockType.AIR}) {
            world.setBlock(7, 45, 8, partner, false);
            float[] box = new float[6];
            assertTrue(FixtureGeometry.thinBox(world, 8, 45, 8, BlockType.METAL_DOOR_OPEN, box));
            assertEquals(9f, box[3]);
            assertEquals(9f - FixtureGeometry.LEAF_THICKNESS, box[0]);
        }
        // The established single-door and casement rules retain their low-side hinge.
        world.setBlock(7, 45, 8, BlockType.BRICK, false);
        float[] box = new float[6];
        assertTrue(FixtureGeometry.thinBox(world, 8, 45, 8, BlockType.METAL_DOOR_OPEN, box));
        assertEquals(8f, box[0]);
        world.setBlock(7, 45, 8, BlockType.AIR, false);
        world.setBlock(8, 45, 8, BlockType.WINDOW_OPEN, false);
        assertTrue(FixtureGeometry.thinBox(world, 8, 45, 8, BlockType.WINDOW_OPEN, box));
        assertEquals(8f, box[0]);
    }
}
