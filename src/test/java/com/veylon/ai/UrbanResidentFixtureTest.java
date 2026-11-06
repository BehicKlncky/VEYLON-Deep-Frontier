package com.veylon.ai;

import com.veylon.Game;
import com.veylon.UrbanFixtureArena;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Urban package 04: residents and fixtures. Movement goes through the
 * production path follower ({@code SettledNpcAI.moveTo}), the NPC physics step
 * and the settlement fast tick that closes residents' doors, as in the game loop.
 */
class UrbanResidentFixtureTest {

    private static final float DT = 1f / 20f;
    private static final float WALK = 2.2f;

    /** Runs a resident toward a cell; true when they arrive within {@code seconds}. */
    private static boolean walk(Game g, Npc n, Vec3i target, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            n.repathCooldown = Math.max(0, n.repathCooldown - DT);
            boolean moving = SettledNpcAI.moveTo(g, n, target, WALK, 0.6f);
            n.applyPhysics(DT, true);
            g.settlementManager.fastTick(g, DT);
            if (!moving) {
                return true;
            }
        }
        return false;
    }

    private static void idle(Game g, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            g.settlementManager.fastTick(g, DT);
        }
    }

    private static Npc resident(UrbanFixtureArena a, Vec3i at) {
        Npc n = a.game.entities.spawnNpc(a.game.world, "Resident", at.x() + 0.5f, at.y() + 0.01f, at.z() + 0.5f);
        assertNotNull(n);
        return n;
    }

    @Test
    void aResidentOpensTheDoorOnTheirPathAndItSwingsShutBehindThem() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        a.standAt(new Vec3i(a.house.x0() + 12, a.house.westRoom().y(), a.house.z0() - 4));
        Vec3i door = a.house.frontDoor();
        Npc n = resident(a, a.house.outsideFront());

        assertTrue(walk(a.game, n, a.house.westRoom(), 20f), "the resident gets inside");
        assertTrue(n.pos.z > door.z() + 1, "through the doorway, not around it");
        idle(a.game, 6f);
        assertSame(BlockType.METAL_DOOR, a.block(door), "the door swung shut behind them");
        assertSame(BlockType.METAL_DOOR, a.above(door));
        assertTrue(a.game.world.fixtures.transitTimers().isEmpty());
        assertEquals(BlockType.METAL_DOOR.id(), a.game.world.changedBlocks.get(door),
                "a resident's passage leaves no block edit");
    }

    @Test
    void aDoorStaysOpenWhileSomeoneStandsInIt() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i door = a.house.frontDoor();
        Npc n = resident(a, a.house.outsideFront());
        assertTrue(com.veylon.FixtureActions.residentOpens(a.game, door.x(), door.y(), door.z()));
        n.pos.set(door.x() + 0.5f, door.y() + 0.01f, door.z() + 0.5f);
        idle(a.game, 8f);
        assertSame(BlockType.METAL_DOOR_OPEN, a.block(door), "never shut on someone");
        n.pos.set(door.x() + 0.5f, door.y() + 0.01f, door.z() - 2.5f);
        idle(a.game, 3f);
        assertSame(BlockType.METAL_DOOR, a.block(door));
    }

    @Test
    void residentsCannotPathThroughLockedDoorsOrClosedWindowsButUseAnOpenWindow() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        World w = a.game.world;
        Vec3i door = a.house.frontDoor();
        Vec3i from = a.house.outsideFront();
        Vec3i to = a.house.westRoom();
        assertNotNull(Pathfinder.find(w, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(),
                Pathfinder.DEFAULT_BUDGET), "an unlocked door is a way in");

        w.fixtures.set(door.x(), door.y(), door.z(), FixtureStates.LOCK_KEYED);
        w.fixtures.set(door.x(), door.y() + 1, door.z(), FixtureStates.LOCK_KEYED);
        assertNull(Pathfinder.find(w, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(),
                Pathfinder.DEFAULT_BUDGET), "locked door and closed glass: no way in");

        a.game.interactWithBlockAt(a.house.window());
        List<Vec3i> path = Pathfinder.find(w, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(),
                Pathfinder.DEFAULT_BUDGET);
        assertNotNull(path, "the open window is the alternate entry");
        Vec3i window = a.house.window();
        assertTrue(path.stream().anyMatch(c -> c.x() == window.x() && c.z() == window.z()),
                "over the sill, then through the interior door");

        Npc n = resident(a, from);
        assertTrue(walk(a.game, n, to, 30f), "and a resident can follow it on foot");
    }

    @Test
    void aBarredDoorIsAWallToResidentsEvenFromTheBarSide() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i inner = a.house.innerDoor();
        World w = a.game.world;
        w.fixtures.set(inner.x(), inner.y(), inner.z(), FixtureStates.LOCK_BARRED);
        w.fixtures.set(inner.x(), inner.y() + 1, inner.z(), FixtureStates.LOCK_BARRED);
        assertTrue(!Pathfinder.passable(w, inner.x(), inner.y(), inner.z()));
        Vec3i west = a.house.westRoom(), east = a.house.eastRoom();
        List<Vec3i> path = Pathfinder.find(w, west.x(), west.y(), west.z(), east.x(), east.y(), east.z(),
                Pathfinder.DEFAULT_BUDGET);
        assertTrue(path == null || path.stream().noneMatch(c -> c.x() == inner.x() && c.z() == inner.z()));
        assertTrue(!com.veylon.FixtureActions.residentOpens(a.game, inner.x(), inner.y(), inner.z()));
        assertSame(BlockType.WOOD_DOOR, a.block(inner));
    }

    @Test
    void residentsClimbFullBlockStairsByWalkingInACity() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        a.standAt(new Vec3i(a.house.x0() + 12, a.house.westRoom().y(), a.house.z0() - 4));
        Vec3i top = a.house.stairTop();
        List<Vec3i> path = Pathfinder.find(a.game.world, a.house.stairFoot().x(), a.house.stairFoot().y(),
                a.house.stairFoot().z(), top.x(), top.y(), top.z(), Pathfinder.DEFAULT_BUDGET);
        assertNotNull(path, "the path finder accepts the flight's headroom");

        Npc n = resident(a, a.house.stairFoot());
        assertTrue(walk(a.game, n, top, 20f), "D-31: up the flight without a jump");
        assertEquals(top.y(), n.pos.y, 0.05f, "standing on the roof");
    }
}
