package com.veylon.ai;

import com.veylon.Game;
import com.veylon.UrbanFixtureArena;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.gfx.model.Animator;
import com.veylon.gfx.model.NpcModels;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class InfectedBehaviorTest {
    private static Npc body(Game g, float x, float y, float z) {
        Npc n = g.entities.spawnNpc(g.world, "Veilbound", x, y, z);
        n.archetype = NpcArchetype.VEILBOUND;
        n.health = n.maxHealth = n.archetype.maxHealth;
        return n;
    }

    @Test void windupAllowsDodgingAndSurvivalDamageUsesThePhysicalGate() {
        var arena = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Game g = arena.game;
        Npc n = body(g, g.player.pos.x, g.player.pos.y, g.player.pos.z - 1.2f);
        n.yaw = 180;
        float hp = g.player.health;
        g.entities.fastTick(g, 0.05f);
        assertEquals(hp, g.player.health);
        assertTrue(n.strikeWindup > 0);
        for (int i = 0; i < 14; i++) { g.entities.fastTick(g, 0.05f); }
        assertTrue(g.player.health < hp);
        assertTrue(n.attackCooldown > 1);
        n.attackCooldown = 0;
        n.pos.set(g.player.pos.x, g.player.pos.y, g.player.pos.z - 1.2f);
        g.entities.fastTick(g, 0.05f);
        g.player.pos.x += 4;
        hp = g.player.health;
        for (int i = 0; i < 14; i++) { g.entities.fastTick(g, 0.05f); }
        assertEquals(hp, g.player.health);
    }

    @Test void creativeImmunityAndPerceptionGatesRemainEffective() {
        var arena = UrbanFixtureArena.create(GameMode.CREATIVE);
        Game g = arena.game;
        Npc n = body(g, g.player.pos.x, g.player.pos.y, g.player.pos.z - 1.2f);
        n.yaw = 180;
        for (int i = 0; i < 30; i++) { g.entities.fastTick(g, 0.05f); }
        assertEquals(g.player.maxHealth, g.player.health);
        assertEquals(0, n.searchTimer);
        g.player.hurtPhysical(g, 6, true);
        assertEquals(g.player.maxHealth, g.player.health);
        n.searchTimer = 10;
        n.strikeWindup = 0.05f;
        g.entities.fastTick(g, 0.05f);
        assertEquals(-1, n.strikeWindup);
        assertEquals(0, n.searchTimer);
    }

    @Test void wallsBlockSightButRealNoiseLeadsToItsPositionAndPursuitMovesOnSupportedGround() {
        var arena = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Game g = arena.game;
        Vec3i at = arena.house.outsideFront();
        Npc n = body(g, at.x() + 0.5f, at.y(), at.z() - 6.5f);
        n.yaw = 180;
        for (int x = at.x() - 3; x <= at.x() + 3; x++) {
            for (int y = at.y(); y <= at.y() + 2; y++) {
                g.world.setBlock(x, y, at.z() - 3, BlockType.CONCRETE, true);
            }
        }
        g.entities.fastTick(g, 0.05f);
        assertEquals(0, n.searchTimer);
        float noiseX = n.pos.x + 3, noiseZ = n.pos.z;
        g.noise.emit(g, noiseX, n.pos.y, noiseZ, 12, 0.8f, "rummage", true, g.player);
        float startX = n.pos.x;
        for (int i = 0; i < 60; i++) { g.entities.fastTick(g, 0.05f); }
        assertEquals(noiseX, n.lastKnown.x);
        assertEquals(noiseZ, n.lastKnown.z);
        assertTrue(n.pos.x > startX + 1);
        assertFalse(n.collidesAt(n.pos.x, n.pos.y, n.pos.z));
        assertEquals(at.y(), n.pos.y, 0.02f);
    }

    @Test void painCancelsStrikeAndHumanoidDeathHasNoMeatHideOrHumanAllegiance() {
        var arena = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Game g = arena.game;
        Npc n = body(g, g.player.pos.x, g.player.pos.y, g.player.pos.z - 1.2f);
        n.yaw = 180;
        g.entities.fastTick(g, 0.05f);
        float trust = g.faction.trust;
        assertTrue(g.performPlayerAttack(n));
        assertEquals(trust, g.faction.trust);
        assertEquals(-1, n.strikeWindup);
        g.entities.fastTick(g, 0.05f);
        assertEquals(0, n.vel.x);
        assertEquals(0, n.vel.z);
        assertFalse(n.settled());
        assertEquals("urban.infected", n.sideId());
        Animator.poseNpc(NpcModels.get(), n, 0);
        assertTrue(NpcModels.get().part("veilShoulder").visible);
        assertFalse(NpcModels.get().part("friendlyBadge").visible);
        int scrap = g.player.inventory.count(com.veylon.item.ItemType.SCRAP);
        n.hurt(100, true);
        g.entities.fastTick(g, 0.05f);
        assertEquals(scrap, g.player.inventory.count(com.veylon.item.ItemType.SCRAP));
        assertTrue(g.entities.carcasses.isEmpty());
        assertEquals(1, g.ragdolls.liveCount());
        g.ragdolls.settleAll(g);
        assertEquals(NpcArchetype.VEILBOUND, g.entities.corpses.getFirst().appearance.archetype);
    }
}
