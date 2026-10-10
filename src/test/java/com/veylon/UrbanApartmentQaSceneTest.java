package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Player;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelAccess;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.AccessibilityValidator;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Actual generated geometry, with camera staging kept separate from navigation evidence. */
class UrbanApartmentQaSceneTest {
    @Test
    void fullQaDispatchReportsTheInheritedStreetPlanBeforeShowingGeneratedApartments() {
        Game game = game(20260731);
        assertDoesNotThrow(() -> game.qa.applyBenchmarkScene("urban_apartments"));
        assertTrue(game.world.loadedCount() > 0);
        assertTrue(game.world.changedBlocks.isEmpty());
    }

    @Test
    void scriptedViewsShowGeneratedRoomsAcrossAChunkBoundaryWithoutStagingStructureOrLoot() {
        for (long seed : new long[]{20260731, -4242, 7}) {
            Game game = game(seed);
            UrbanApartmentQaScene scene = new UrbanApartmentQaScene(game);
            scene.stage("urban_apartments");
            BuildingPlan building = scene.building();
            assertTrue(UrbanApartmentQaScene.crossesChunkBoundary(building.parcel()));
            assertTrue(building.basement());
            assertTrue(building.storeys() >= 2);
            for (int view = 0; view < 7; view++) {
                scene.update(view * 4 + 1);
                assertFalse(VoxelClearance.collides(game.world, game.player.pos.x, game.player.pos.y,
                        game.player.pos.z, .6f, 1.8f, true), "camera body is clear, view=" + view);
                Vec3i feet = new Vec3i((int) Math.floor(game.player.pos.x), (int) game.player.pos.y,
                        (int) Math.floor(game.player.pos.z));
                assertTrue(game.world.isColumnLoaded(feet.x(), feet.z()));
                assertTrue(game.world.getBlock(feet.x(), feet.y() - 1, feet.z()).solid);
            }
            assertTrue(game.world.changedBlocks.isEmpty(), "scene never stamps a showcase house");
            assertTrue(game.world.crateContents.isEmpty(), "no placeholder contents before milestone 07");
            Vec3i groundRoom = building.floors().stream().filter(f -> f.index() == 0).findFirst()
                    .orElseThrow().rooms().getFirst().anchor();
            assertNotEquals(BlockType.AIR,
                    game.world.getBlock(groundRoom.x(), groundRoom.y() + 3, groundRoom.z()),
                    "this is a real generated floor slab above the room");
        }
    }

    @Test
    void manualSceneNeverOverridesSubsequentPlayerPoseMouseLookTimeOrInteractions() {
        Game game = game(20260731);
        UrbanApartmentQaScene scene = new UrbanApartmentQaScene(game);
        scene.stage("urban_apartments_manual");
        game.player.pos.add(2, 0, 1);
        float x = game.player.pos.x, y = game.player.pos.y, z = game.player.pos.z;
        game.camera.yaw = 123;
        game.camera.pitch = -17;
        game.time.totalMinutes = 777;
        for (int second = 0; second < 40; second++) { scene.update(second); }
        assertEquals(x, game.player.pos.x);
        assertEquals(y, game.player.pos.y);
        assertEquals(z, game.player.pos.z);
        assertEquals(123, game.camera.yaw);
        assertEquals(-17, game.camera.pitch);
        assertEquals(777, game.time.totalMinutes);
        assertEquals(GameMode.SURVIVAL, game.gameMode());
        assertTrue(game.world.changedBlocks.isEmpty());
        assertTrue(game.world.fixtures.isEmpty());
        assertTrue(UrbanApartmentQaScene.manualNavigation(" URBAN_APARTMENTS_MANUAL_NIGHT "));
        assertFalse(UrbanApartmentQaScene.manualNavigation("urban_apartments_night"));
        assertFalse(UrbanApartmentQaScene.manualNavigation(null));
    }

    @Test
    void supplementalNightLightIsExplicitPortableInstrumentationAndPreservesGeneratedFixturesAndRoutes() {
        for (long seed : new long[]{20260731, -4242}) {
            Game game = game(seed);
            UrbanApartmentQaScene scene = new UrbanApartmentQaScene(game);
            scene.stage("urban_apartments_lit_night");
            assertEquals(4, game.world.changedBlocks.size());
            game.world.changedBlocks.forEach((pos, block) -> {
                assertEquals(BlockType.TORCH.id(), block);
                assertEquals(BlockType.AIR, scene.building().voxels().getBlock(pos.x(), pos.y(), pos.z()),
                        "instrumentation only adds lights in previously empty cells");
            });
            for (int view = 1; view <= 4; view++) {
                scene.update(view * 4 + 1);
                assertTrue(game.world.blockLight((int) game.player.pos.x, (int) game.player.pos.y + 1,
                        (int) game.player.pos.z) > .15f, "room lighting reaches the camera, view=" + view);
                assertFalse(VoxelClearance.collides(game.world, game.player.pos.x, game.player.pos.y,
                        game.player.pos.z, .6f, 1.8f, true));
            }
            assertEquals(23 * 60, game.time.totalMinutes);
            assertTrue(game.world.crateContents.isEmpty());
            assertTrue(game.world.fixtures.isEmpty());
        }
    }

    @Test
    void productionSurvivalMovementEntersFindsKitchenClimbsDescendsAndLeavesTheGeneratedBuilding() {
        Game game = game(20260731);
        BuildingPlan plan = UrbanApartmentQaScene.selectBuilding(game.world);
        game.world.ensureChunks((plan.parcel().x0() + plan.parcel().x1()) / 2,
                (plan.parcel().z0() + plan.parcel().z1()) / 2, 3, 10_000);
        Vec3i outside = plan.approaches().getFirst();
        game.player.pos.set(outside.x() + .5f, outside.y(), outside.z() + .5f);
        game.player.vel.zero(); game.player.onGround = true;
        List<Vec3i> destinations = new ArrayList<>();
        for (int index : new int[]{0, 1, -1}) {
            BuildingPlan.Floor floor = plan.floors().stream().filter(f -> f.index() == index)
                    .findFirst().orElseThrow();
            BuildingPlan.Function function = index == -1 ? BuildingPlan.Function.BASEMENT_STORAGE
                    : index == 0 ? BuildingPlan.Function.KITCHEN : BuildingPlan.Function.LIVING;
            destinations.add(floor.rooms().stream().filter(r -> r.functions().contains(function))
                    .findFirst().orElseThrow().anchor());
        }
        destinations.add(plan.approaches().getLast());
        for (Vec3i destination : destinations) { walkTo(game, plan, destination); }
        assertEquals(destinations.getLast().y(), game.player.pos.y, .04f);
        assertFalse(game.player.abilities.flying());
        assertFalse(game.world.fixtures.isEmpty(), "the walk opened actual generated doors through F commands");
        assertTrue(game.world.changedBlocks.isEmpty(), "no block staging, breaking or teleport after the route starts");
    }

    private static void walkTo(Game game, BuildingPlan plan, Vec3i target) {
        Vec3i start = new Vec3i((int) Math.floor(game.player.pos.x), Math.round(game.player.pos.y),
                (int) Math.floor(game.player.pos.z));
        List<Vec3i> path = physicalRoute(game.world, plan, start, target);
        PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
        for (Vec3i next : path) {
            if (Fixtures.isClosedDoor(game.world.getBlock(next.x(), next.y(), next.z()))) {
                assertTrue(game.interactWithBlockAt(next), "open generated door from its approach");
            }
            boolean arrived = false;
            for (int frame = 0; frame < 240; frame++) {
                float dx = next.x() + .5f - game.player.pos.x, dz = next.z() + .5f - game.player.pos.z;
                boolean moving = dx * dx + dz * dz > .003f;
                float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
                command.set(yaw, moving, false, false, false, false, false, false, false);
                game.applyMovementCommand(command, 1f / 120f);
                if (!moving && Math.abs(game.player.pos.y - next.y()) < .04f) { arrived = true; break; }
            }
            assertTrue(arrived, "generated route to " + target + " stopped at " + next + " actual=" + game.player.pos);
        }
    }

    /**
     * QA routing uses the production swept-body checks over generated blocks.
     * Legacy NPC A* can choose unswept drops beside stacked flights; multi-floor
     * AI routing is milestone 10, not a premise of this player-movement test.
     * Unlocked doors are evaluated after their ordinary F action, then actually
     * opened from the preceding cell while the player follows the route.
     */
    private static List<Vec3i> physicalRoute(World world, BuildingPlan plan, Vec3i start, Vec3i goal) {
        VoxelAccess opened = new VoxelAccess() {
            @Override public boolean isColumnLoaded(int x, int z) { return world.isColumnLoaded(x, z); }
            @Override public BlockType getBlock(int x, int y, int z) {
                BlockType block = world.getBlock(x, y, z);
                return Fixtures.isClosedDoor(block) && world.fixtureLockAt(x, y, z) == FixtureStates.LOCK_NONE
                        ? Fixtures.kindOf(block).open : block;
            }
        };
        Map<Vec3i, Vec3i> parent = new HashMap<>();
        ArrayDeque<Vec3i> queue = new ArrayDeque<>();
        parent.put(start, null); queue.add(start);
        int[][] directions = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty() && !parent.containsKey(goal)) {
            Vec3i from = queue.remove();
            for (int[] direction : directions) {
                for (int dy = 1; dy >= -VoxelClearance.MAX_DROP; dy--) {
                    Vec3i to = new Vec3i(from.x() + direction[0], from.y() + dy, from.z() + direction[1]);
                    if (plan.voxels().isColumnLoaded(to.x(), to.z()) && to.y() >= plan.voxels().minY
                            && to.y() <= plan.voxels().maxY && !parent.containsKey(to)
                            && AccessibilityValidator.canMove(opened, from, to)) {
                        parent.put(to, from); queue.add(to);
                    }
                }
            }
            assertTrue(parent.size() <= plan.voxels().cellCount(), "bounded physical QA search");
        }
        assertTrue(parent.containsKey(goal), "no physically valid generated route from " + start + " to " + goal);
        List<Vec3i> path = new ArrayList<>();
        for (Vec3i at = goal; at != null; at = parent.get(at)) { path.add(at); }
        Collections.reverse(path);
        return path;
    }

    private static Game game(long seed) {
        Game game = new Game();
        game.world = new World(seed, UrbanRevision.DEVELOPMENT_GENERATOR);
        game.world.urban.resolveContentRevision(UrbanRevision.CURRENT_CONTENT);
        game.player = new Player(game.world);
        return game;
    }
}
