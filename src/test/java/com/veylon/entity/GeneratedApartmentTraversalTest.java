package com.veylon.entity;

import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanRevision;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Production generated voxels and ordinary movement, without plan-debug block staging. */
class GeneratedApartmentTraversalTest {
    @Test
    void generatedFurnishedStairsAreWalkableBothWaysInEveryFrontageAndGameMode() {
        World world = new World(20260731, World.GEN_URBAN_APARTMENTS);
        world.urban.resolveContentRevision(UrbanRevision.CURRENT_CONTENT);
        for (ParcelPlan.Side side : ParcelPlan.Side.values()) {
            BuildingPlan plan = world.urban.regionPlan(0, 0).parcels().stream()
                    .filter(p -> p.frontage() == side && p.use() != ParcelPlan.Use.COMMERCIAL)
                    .map(p -> world.urban.buildingAt(p.x0(), p.z0()))
                    .filter(p -> p.basement() && p.storeys() >= 2).findFirst().orElseThrow();
            loadBuilding(world, plan);
            for (GameMode mode : GameMode.values()) {
                Player player = new Player(world);
                player.abilities.apply(mode);
                PlayerMovementSystem movement = new PlayerMovementSystem();
                PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
                PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();
                for (BuildingPlan.Stair stair : plan.stairs()) {
                    List<Vec3i> route = new ArrayList<>(stair.route());
                    for (int direction = 0; direction < 2; direction++) {
                        if (direction == 1) { Collections.reverse(route); }
                        Vec3i start = route.getFirst();
                        player.pos.set(start.x() + .5f, start.y(), start.z() + .5f);
                        player.vel.zero(); player.onGround = true;
                        walk(player, world, movement, command, result, route,
                                mode + " " + side + " floor=" + stair.fromFloor() + " direction=" + direction);
                    }
                }
            }
        }
        assertTrue(world.changedBlocks.isEmpty(), "test uses generated structures only");
        assertTrue(world.crateContents.isEmpty(), "contextual loot is a later milestone");
    }

    private static void walk(Player player, World world, PlayerMovementSystem movement,
                             PlayerMovementSystem.Command command, PlayerMovementSystem.FrameResult result,
                             List<Vec3i> route, String context) {
        for (int i = 1; i < route.size(); i++) {
            Vec3i target = route.get(i);
            boolean arrived = false;
            for (int frame = 0; frame < 120; frame++) {
                float dx = target.x() + .5f - player.pos.x, dz = target.z() + .5f - player.pos.z;
                boolean moving = dx * dx + dz * dz > .003f;
                float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
                command.set(yaw, moving, false, false, false, false, false, false, false);
                movement.update(player, world, command, 1f / 120f, result);
                if (!moving && Math.abs(player.pos.y - target.y()) < .04f) { arrived = true; break; }
                assertTrue(world.isColumnLoaded((int) Math.floor(player.pos.x), (int) Math.floor(player.pos.z)));
            }
            assertTrue(arrived, context + " target=" + target + " actual=" + player.pos);
            assertFalse(player.abilities.flying());
        }
    }

    private static void loadBuilding(World world, BuildingPlan plan) {
        ParcelPlan parcel = plan.parcel();
        for (int cx = Math.floorDiv(parcel.x0(), Chunk.SX); cx <= Math.floorDiv(parcel.x1(), Chunk.SX); cx++) {
            for (int cz = Math.floorDiv(parcel.z0(), Chunk.SZ); cz <= Math.floorDiv(parcel.z1(), Chunk.SZ); cz++) {
                world.getOrCreateChunk(cx, cz);
            }
        }
    }
}
