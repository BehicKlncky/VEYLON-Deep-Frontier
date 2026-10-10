package com.veylon.entity;

import com.veylon.ai.Pathfinder;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingTemplates;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.PlanVolume;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Headless plan-debug staging; this is deliberately not generated/furnished exploration QA. */
class BuildingPlanTraversalTest {
    @Test
    void playerWalksEverySwitchbackUpAndBackInSurvivalAndCreativeWithoutJumping() {
        for (GameMode mode : GameMode.values()) {
            for (ParcelPlan.Side side : ParcelPlan.Side.values()) {
                World world = new World(20260731, World.GEN_URBAN_PLANS);
                ParcelPlan parcel = world.urban.regionPlan(0, 0).parcels().stream()
                        .filter(p -> p.frontage() == side)
                        .max(Comparator.comparingInt(ParcelPlan::maxStoreys)).orElseThrow();
                BuildingPlan plan = BuildingTemplates.propose(parcel, 1, BuildingPlan.Family.APARTMENT,
                        BuildingPlan.Variant.INTACT, parcel.maxStoreys(), true, false);
                stage(world, plan.voxels());
                Player player = new Player(world);
                player.abilities.apply(mode);
                PlayerMovementSystem movement = new PlayerMovementSystem();
                PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
                PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();
                for (BuildingPlan.Stair stair : plan.stairs()) {
                    List<Vec3i> route = new ArrayList<>(stair.route());
                    for (int direction = 0; direction < 2; direction++) {
                        if (direction == 1) { Collections.reverse(route); }
                        Vec3i start = route.getFirst(), end = route.getLast();
                        assertNotNull(Pathfinder.find(world, start.x(), start.y(), start.z(), end.x(), end.y(), end.z(),
                                2000), "production pathfinder accepts the proposed switchback");
                        player.pos.set(start.x() + .5f, start.y(), start.z() + .5f);
                        player.vel.zero(); player.onGround = true;
                        for (int i = 1; i < route.size(); i++) {
                            Vec3i target = route.get(i);
                            boolean arrived = false;
                            for (int frame = 0; frame < 120; frame++) {
                                float dx = target.x() + .5f - player.pos.x;
                                float dz = target.z() + .5f - player.pos.z;
                                boolean moving = dx * dx + dz * dz > .003f;
                                float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
                                command.set(yaw, moving, false, false, false, false, false, false, false);
                                movement.update(player, world, command, 1f / 120f, result);
                                if (!moving && Math.abs(player.pos.y - target.y()) < .04f) { arrived = true; break; }
                            }
                            assertTrue(arrived, mode + " " + side + " flight=" + stair.fromFloor() + " direction=" + direction
                                    + " target=" + target + " actual=" + player.pos);
                            assertFalse(player.abilities.flying());
                        }
                        assertEquals(end.y(), player.pos.y, .04f);
                    }
                }
            }
        }
    }

    private static void stage(World world, PlanVolume volume) {
        for (int cx = Math.floorDiv(volume.x0, Chunk.SX); cx <= Math.floorDiv(volume.x0 + volume.width - 1, Chunk.SX); cx++) {
            for (int cz = Math.floorDiv(volume.z0, Chunk.SZ); cz <= Math.floorDiv(volume.z0 + volume.depth - 1, Chunk.SZ); cz++) {
                world.getOrCreateChunk(cx, cz);
            }
        }
        world.beginBatch();
        for (int x = volume.x0; x < volume.x0 + volume.width; x++) {
            for (int z = volume.z0; z < volume.z0 + volume.depth; z++) {
                for (int y = volume.minY; y <= volume.maxY; y++) {
                    world.setBlock(x, y, z, volume.getBlock(x, y, z), false);
                }
            }
        }
        world.endBatch();
    }
}
