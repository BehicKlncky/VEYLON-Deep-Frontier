package com.veylon.ai;

import com.veylon.UrbanFixtureArena;
import com.veylon.combat.WorldNoise;
import com.veylon.entity.GameMode;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IndoorPerceptionTest {
    @Test void doorAttenuationAgreesForLockedAndUnlockedDoorsAndOpeningRestoresQuietHearing() {
        var a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        var g = a.game;
        Vec3i door = a.house.frontDoor();
        float x = door.x() + .5f, y = door.y(), z = door.z() - 1.5f;
        g.noise.emit(g, x, y, door.z() + 2.5f, 8, .2f, "rummage", true, g.player);
        assertNull(g.noise.loudestAudible(g.world, x, y, z, 0, null, null));
        g.world.fixtures.set(door.x(), door.y(), door.z(), com.veylon.world.FixtureStates.LOCK_KEYED);
        assertNull(g.noise.loudestAudible(g.world, x, y, z, 0, null, null));
        g.world.fixtures.set(door.x(), door.y(), door.z(), com.veylon.world.FixtureStates.LOCK_NONE);
        assertTrue(com.veylon.FixtureActions.residentOpens(g, door.x(), door.y(), door.z()));
        var heard = g.noise.loudestAudible(g.world, x, y, z, 0, null, null);
        assertNotNull(heard);
        assertNull(g.noise.loudestAudible(g.world, x, y, z, 0, heard, null), "evidence is consumed once");
    }

    @Test void floorsAndPartitionsOccludeSightAndAttenuateHearingWithoutLeakingPlayerPosition() {
        var a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        var g = a.game;
        Vec3i at = a.house.westRoom();
        assertFalse(InfectedAI.clearSight(g.world, at.x() + .5f, at.y() + 1.4f, at.z() + .5f,
                at.x() + .5f, at.y() + 6.4f, at.z() + .5f));
        for (int y = at.y() + 3; y < at.y() + 6; y++) {
            g.world.setBlock(at.x(), y, at.z(), BlockType.CONCRETE, true);
        }
        g.noise.emit(g, at.x() + .5f, at.y() + 7, at.z() + .5f, 12, .5f, "rummage", true, g.player);
        assertNull(g.noise.loudestAudible(g.world, at.x() + .5f, at.y(), at.z() + .5f, 0, null, null));
        g.noise.emit(g, at.x() + .5f, at.y() + 7, at.z() + .5f, 90, .9f, "gunshot", true, g.player);
        var heard = g.noise.loudestAudible(g.world, at.x() + .5f, at.y(), at.z() + .5f, 0, null, null);
        assertNotNull(heard); assertEquals(at.y() + 7, heard.y);
        assertNotEquals(g.player.pos.y, heard.y, "event position is the evidence");
    }

    @Test void hearingWorkIsGloballyBoundedAndUnloadedRaysAreInaudible() {
        var a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        var g = a.game;
        for (int i = 0; i < WorldNoise.MAX_EVENTS; i++) {
            g.noise.emit(g, g.player.pos.x + i + 1, g.player.pos.y, g.player.pos.z,
                    128, .8f, "gunshot", true, g.player);
        }
        g.noise.beginTick();
        for (int i = 0; i < 48; i++) {
            g.noise.loudestAudible(g.world, g.player.pos.x, g.player.pos.y, g.player.pos.z, 0, null, null);
        }
        assertTrue(g.noise.hearingSamples() <= WorldNoise.MAX_HEARING_SAMPLES);
        assertEquals(WorldNoise.MAX_HEARING_QUERIES, g.noise.hearingQueries());
        g.noise.reset();
        float far = g.player.pos.x + 100;
        g.noise.emit(g, far, g.player.pos.y, g.player.pos.z, 128, 1, "gunshot", true, g.player);
        assertNull(g.noise.loudestAudible(g.world, g.player.pos.x, g.player.pos.y, g.player.pos.z, 0, null, null));
    }

    @Test void departingPursuerDropsLiveRouteAndEvidenceReferences() {
        var a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        var g = a.game;
        var n = IndoorNavigationTest.infected(g, a.house.outsideFront());
        assertTrue(IndoorNavigation.moveTo(g, n, a.house.westRoom(), 2, .5f));
        assertNotNull(n.path);
        g.noise.emit(g, n.pos.x, n.pos.y, n.pos.z, 20, 1, "gunshot", true, g.player);
        n.lastHeard = g.noise.loudestAudible(g.world, n.pos.x, n.pos.y, n.pos.z, 0, null, n);
        assertNotNull(n.lastHeard);
        n.searchTimer = 14; n.strikeWindup = .5f; n.combatTarget = g.player;
        assertTrue(g.entities.removeNpc(g, n));
        assertNull(n.path); assertNull(n.navigationGoal); assertNull(n.combatTarget); assertNull(n.lastHeard);
        assertEquals(0, n.searchTimer); assertEquals(-1, n.strikeWindup);
    }
}
