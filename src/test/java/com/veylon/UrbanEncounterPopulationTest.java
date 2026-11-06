package com.veylon;

import com.veylon.entity.UrbanEncounterDirector;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.settlement.SettlementManager;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UrbanEncounterPopulationTest {
    @Test void freshStarterHasAnEncounterOutsideTheRefugeAndInitialBreathingRoomOnEverySeed() {
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            Game g = UrbanEncounterArena.session(seed);
            for (int tick = 0; tick < 32; tick++) { g.entities.encounters.slowTick(g); }
            assertTrue(g.entities.encounters.activeCount(g) > 0, "starter encounter seed=" + seed);
            var byBuilding = new java.util.HashMap<Long, Integer>();
            var site = UrbanStarterSite.of(g.world.urban);
            var door = site.refuge().approaches().getLast();
            for (var n : g.entities.npcs) {
                var a = EncounterAnchors.resolve(g.world, n.encounterId);
                assertTrue(n.distSqTo(g.player) >= 16 * 16);
                assertTrue(n.distSqTo(door.x() + .5f, n.pos.y, door.z() + .5f) >= 24 * 24);
                assertFalse(site.refuge().parcel().contains((int) n.pos.x, (int) n.pos.z));
                assertTrue(UrbanEncounterDirector.bodyLoaded(g.world, n.pos.x, n.pos.z));
                assertFalse(n.collidesAt(n.pos.x, n.pos.y, n.pos.z));
                byBuilding.merge(a.building(), 1, Integer::sum);
            }
            assertTrue(byBuilding.values().stream().allMatch(count -> count <= 2));
            assertTrue(RuntimeBudgetSnapshot.capture(g).withinHardLimits());
        }
    }

    @Test void seededStreetAndIndoorAnchorsUseActualGroundFloorSupport() {
        for (long seed : UrbanStarterProgressionTest.SEEDS) {
            Game g = UrbanEncounterArena.session(seed);
            for (boolean indoor : new boolean[]{false, true}) {
                var n = UrbanEncounterArena.activate(g, indoor);
                var a = EncounterAnchors.resolve(g.world, n.encounterId);
                assertEquals(indoor, a.indoor());
                assertEquals(0, UrbanIds.floorIndex(a.id()));
                assertEquals(a, EncounterAnchors.resolve(g.world, UrbanIds.parse(UrbanIds.format(a.id()))));
                assertEquals(a.cell().y(), n.pos.y);
                assertTrue(g.world.getBlock(a.cell().x(), a.cell().y() - 1, a.cell().z()).solid);
                assertFalse(n.collidesAt(n.pos.x, n.pos.y, n.pos.z));
                if (indoor) {
                    var location = g.world.urbanLocation(a.cell().x(), a.cell().y(), a.cell().z());
                    assertEquals(0, location.floor().index());
                    assertNotEquals(BuildingPlan.Variant.OCCUPIED, location.building().variant());
                }
            }
        }
    }

    @Test void admissionRejectsBreathingRoomSanctuaryVisibleBodiesMissingSupportAndClearance() {
        Game g = UrbanEncounterArena.session(20260731);
        var n = UrbanEncounterArena.activate(g, false);
        var a = EncounterAnchors.resolve(g.world, n.encounterId);
        g.entities.removeNpc(g, n);
        float x = n.pos.x, y = n.pos.y, z = n.pos.z;
        assertTrue(UrbanEncounterDirector.safeSpawn(g, x, y, z));
        g.player.pos.set(x + 2, y, z);
        assertFalse(UrbanEncounterDirector.safeSpawn(g, x, y, z));
        // Same generated street, clear view along its approach rather than through an apartment.
        boolean clearView = false;
        for (int angle = 0; angle < 360; angle += 15) {
            float vx = x + (float) Math.sin(Math.toRadians(angle)) * 20;
            float vz = z - (float) Math.cos(Math.toRadians(angle)) * 20;
            if (com.veylon.ai.InfectedAI.clearSight(g.world, vx, y + 1.4f, vz, x, y + 1.4f, z)) {
                g.player.pos.set(vx, y, vz); g.camera.yaw = angle + 180;
                assertFalse(UrbanEncounterDirector.safeSpawn(g, x, y, z)); clearView = true; break;
            }
        }
        assertTrue(clearView, "a generated street lane must exercise the visibility guard");
        UrbanEncounterArena.observeAway(g, a);
        int bx = a.cell().x(), by = a.cell().y(), bz = a.cell().z();
        BlockType support = g.world.getBlock(bx, by - 1, bz);
        g.world.setBlock(bx, by - 1, bz, BlockType.AIR, true);
        assertFalse(UrbanEncounterDirector.safeSpawn(g, x, y, z));
        g.world.setBlock(bx, by - 1, bz, support, true);
        g.world.setBlock(bx, by + 1, bz, BlockType.CONCRETE, true);
        assertFalse(UrbanEncounterDirector.safeSpawn(g, x, y, z));
        assertFalse(UrbanEncounterDirector.safeSpawn(g, 32_000_000.5f, 41, 32_000_000.5f));
        var home = g.world.urban.refugeAnchor;
        g.player.pos.set(home.x() + 40, home.y(), home.z());
        assertFalse(UrbanEncounterDirector.safeSpawn(g, home.x() + .5f, home.y(), home.z() + .5f));
    }

    @Test void injuredDormantResidentsKeepIdentityAndDeathDepletesWithoutEviction() {
        Game g = UrbanEncounterArena.session(7);
        var n = UrbanEncounterArena.activate(g, false);
        long id = n.encounterId; var a = EncounterAnchors.resolve(g.world, id);
        n.hurt(7, true); n.torsoWounds = 1;
        g.player.pos.x += 200; g.entities.encounters.slowTick(g);
        assertFalse(g.entities.npcs.contains(n));
        assertNull(g.entities.encounters.resident(id).live);
        UrbanEncounterArena.observeAway(g, a);
        var returned = g.entities.encounters.tryActivate(g, a);
        assertNotNull(returned); assertEquals(id, returned.encounterId);
        assertEquals(21, returned.health); assertEquals(1, returned.torsoWounds);
        returned.hurt(100, true); g.entities.fastTick(g, .05f);
        assertTrue(g.entities.encounters.resident(id).dead());
        assertNull(g.entities.encounters.tryActivate(g, a));
        assertTrue(g.entities.carcasses.isEmpty()); assertEquals(1, g.ragdolls.liveCount());
        int count = g.entities.encounters.size();
        assertThrows(IllegalArgumentException.class, () -> g.entities.encounters.restore(
                java.util.Collections.nCopies(UrbanEncounterDirector.MAX_RECORDS + 1,
                        g.entities.encounters.resident(id))));
        assertEquals(count, g.entities.encounters.size()); assertTrue(g.entities.encounters.resident(id).dead());
        g.newWorld(7, true, World.GEN_URBAN_ENCOUNTERS);
        assertEquals(0, g.entities.encounters.size()); assertEquals(0, g.entities.encounters.lastSpawns);
    }

    @Test void directorBudgetsPopulationWorkAndSharedNpcCapacity() {
        Game g = UrbanEncounterArena.session(7);
        for (var a : UrbanEncounterArena.anchors(g)) {
            UrbanEncounterArena.observeAway(g, a); g.entities.encounters.tryActivate(g, a);
            if (g.entities.encounters.activeCount(g) == UrbanEncounterDirector.MAX_ACTIVE) { break; }
        }
        assertEquals(12, g.entities.encounters.activeCount(g));
        assertFalse(g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.INFECTED));
        assertTrue(RuntimeBudgetSnapshot.capture(g).withinHardLimits());
        assertEquals(12, RuntimeBudgetSnapshot.capture(g).npcs().infected());
        g.newWorld(7, true, World.GEN_URBAN_ENCOUNTERS);
        for (int i = 0; i < 32; i++) {
            var n = g.entities.spawnNpc(g.world, "reserved capacity", 0, 41, 0);
            if (i < 24) { n.archetype = com.veylon.settlement.NpcArchetype.GUARD; n.residentIndex = i; }
        }
        assertFalse(g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.INFECTED));
        assertTrue(g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.COUNTERATTACK));
        g.newWorld(7, true, World.GEN_URBAN_ENCOUNTERS);
        var a = UrbanEncounterArena.anchors(g).stream().filter(x -> !x.indoor()).findFirst().orElseThrow();
        UrbanEncounterArena.observeAway(g, a);
        for (int i = 0; i < 30; i++) {
            g.entities.encounters.slowTick(g);
            assertTrue(g.entities.encounters.lastParcels <= 16);
            assertTrue(g.entities.encounters.lastSnapshots <= 16);
            assertTrue(g.entities.encounters.lastSpawns <= 2);
            assertTrue(RuntimeBudgetSnapshot.capture(g).withinHardLimits());
        }
        assertTrue(g.entities.encounters.size() > 0, "normal slow ticks activate generated anchors");
    }

    @Test void creativeSpawnPauseAndModeTransitionsUseProductionControls() {
        Game g = UrbanEncounterArena.session(7);
        var n = UrbanEncounterArena.activate(g, false);
        var a = EncounterAnchors.resolve(g.world, n.encounterId);
        g.entities.removeNpc(g, n);
        g.restoreCreativeControls(false, false, true);
        assertFalse(g.spawningPaused(), "Survival rejects Creative flags");
        g.gameModes.switchTo(com.veylon.entity.GameMode.CREATIVE);
        g.creativeControls.toggleSpawning(); assertTrue(g.spawningPaused());
        assertNull(g.entities.encounters.tryActivate(g, a));
        g.entities.encounters.slowTick(g); assertEquals(0, g.entities.encounters.activeCount(g));
        g.creativeControls.toggleSpawning();
        var returned = g.entities.encounters.tryActivate(g, a); assertNotNull(returned);
        returned.strikeWindup = .05f; returned.searchTimer = 10;
        g.entities.fastTick(g, .05f);
        assertEquals(-1, returned.strikeWindup); assertEquals(0, returned.searchTimer);
        assertEquals(g.player.maxHealth, g.player.health);
        g.gameModes.switchTo(com.veylon.entity.GameMode.SURVIVAL);
        assertFalse(g.spawningPaused()); assertTrue(g.player.isPerceivableByAi());
    }

    @Test void unloadedBodiesFreezeBeforeDormancyAndMovedResidentsReactivateNearTheirSavedPosition() {
        Game g = UrbanEncounterArena.session(7);
        var n = UrbanEncounterArena.activate(g, false); long id = n.encounterId;
        n.pos.set(32_000_000.5f, 41, 32_000_000.5f); n.vel.set(2, -4, 2);
        for (int tick = 0; tick < 20; tick++) { g.entities.fastTick(g, .05f); }
        assertEquals(41, n.pos.y, "unloaded AIR must not make gravity corrupt the saved floor");
        g.entities.encounters.slowTick(g);
        assertNull(g.entities.encounters.resident(id).live);
        assertEquals(41, g.entities.encounters.resident(id).y);
        var plan = g.world.urban.regionPlan(3, 0);
        float x = plan.northSouthLine(1) + .5f, z = plan.eastWestLine(1) + .5f;
        g.world.ensureChunks((int) x, (int) z, 3, 1000);
        var r = g.entities.encounters.resident(id); r.x = x; r.z = z; r.health = 19;
        g.player.pos.set(x + 24, 41, z); g.camera.yaw = 90;
        g.entities.encounters.slowTick(g);
        assertNotNull(r.live, "snapshot cursor must find a pursuer far from its original building");
        assertEquals(id, r.live.encounterId); assertEquals(19, r.live.health);
        assertEquals(x, r.live.pos.x); assertEquals(z, r.live.pos.z);
    }
}
