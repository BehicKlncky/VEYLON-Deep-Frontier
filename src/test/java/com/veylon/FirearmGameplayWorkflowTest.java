package com.veylon;

import com.veylon.ai.SettledNpcAI;
import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Player-facing trigger and reload coverage through Game's native firearm command. */
class FirearmGameplayWorkflowTest {

    private static final Vector3f EAST = new Vector3f(1, 0, 0);

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newWorld(20260716L, true);
        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        flattenArena();
        game.player.pos.set(310.5f, 40.1f, 310.5f);
        game.camera.position.set(game.player.pos.x,
                game.player.pos.y + game.player.eyeHeight(), game.player.pos.z);
        game.camera.pitch = 6f;
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.projectiles.setRandomSeed(91L);
    }

    @ParameterizedTest(name = "{0} uses the native trigger/reload workflow")
    @EnumSource(value = ItemType.class, names = {"MUSKET", "FLINTLOCK_PISTOL"})
    void musketAndPistolCommandReloadsThenFiresWithCostsFeedbackAndCleanup(ItemType type) {
        ItemStack firearm = equip(type);
        WeaponDefinition definition = WeaponRegistry.of(type);
        game.player.inventory.add(definition.ammo, definition.ammoPerShot * 2);

        assertEquals(Game.FirearmCommandResult.RELOAD_STARTED,
                game.updateFirearmCommand(0f, false, false, true, EAST));
        assertEquals(Game.FirearmCommandResult.RELOADING,
                game.updateFirearmCommand(0.1f, true, true, false, EAST),
                "the trigger cannot bypass an active reload");
        assertTrue(game.reloadTimer > 0f);

        game.tickReload(definition.reloadTime + 0.01f);
        assertEquals(definition.magazine, firearm.charge);
        assertEquals(definition.ammoPerShot,
                game.player.inventory.count(definition.ammo),
                "reload transfers ammunition exactly once");

        float durabilityBefore = firearm.durability;
        float pitchBefore = game.camera.pitch;
        assertEquals(Game.FirearmCommandResult.FIRED,
                game.updateFirearmCommand(0f, true, true, false, EAST));

        assertEquals(0, firearm.charge);
        assertEquals(definition.ammoPerShot, game.player.inventory.count(definition.ammo),
                "firing consumes the loaded round, not loose ammunition again");
        assertEquals(durabilityBefore - definition.durabilityCost,
                firearm.durability, 0.0001f);
        assertEquals(pitchBefore - definition.recoil * 4.5f,
                game.camera.pitch, 0.0001f, "the shot applies view recoil");
        assertEquals(1f, game.player.noise, 0.0001f);
        assertEquals(1, game.projectiles.liveCount());
        assertEquals(ProjectileSystem.Kind.BULLET, game.projectiles.live.getFirst().kind);
        assertEquals(1, game.noise.countCategory("gunshot"));

        var distantGunshot = game.noise.loudestAudible(
                game.camera.position.x + definition.noiseRadius * 0.75f,
                game.camera.position.y, game.camera.position.z, 0f);
        assertNotNull(distantGunshot, "the configured firearm noise carries through the world");
        assertEquals("gunshot", distantGunshot.category);

        assertEquals(Game.FirearmCommandResult.COOLDOWN,
                game.updateFirearmCommand(0f, true, true, false, EAST),
                "the attack interval blocks an immediate second trigger pull");
        assertEquals(Game.FirearmCommandResult.NONE,
                game.updateFirearmCommand(definition.attackInterval + 0.01f,
                        true, false, false, EAST),
                "a held semi-automatic trigger does not synthesize a new press");
        assertEquals(Game.FirearmCommandResult.DRY_FIRE,
                game.updateFirearmCommand(0f, false, true, false, EAST));
        assertEquals(1, game.projectiles.liveCount(), "dry fire creates no projectile");

        game.projectiles.update(game, 10f);
        assertEquals(0, game.projectiles.liveCount(),
                "spent bullets leave the bounded live-projectile collection");
    }

    @Test
    void musketGunshotIsHeardByDistantHostileAiThroughTheGameplayCommand() {
        ItemStack musket = equip(ItemType.MUSKET);
        WeaponDefinition definition = WeaponRegistry.of(musket.type);
        game.player.inventory.add(ItemType.MUSKET_BALL, 1);
        assertEquals(Game.FirearmCommandResult.RELOAD_STARTED,
                game.updateFirearmCommand(0f, false, false, true, EAST));
        game.tickReload(definition.reloadTime + 0.01f);

        Npc listener = new Npc(game.world, "Distant tracker");
        listener.archetype = NpcArchetype.TRACKER;
        listener.raider = true;
        listener.pos.set(game.player.pos.x + 50f, game.player.pos.y, game.player.pos.z);
        listener.lastKnownAge = 999f;
        listener.decideTimer = 0f;
        game.entities.npcs.add(listener);

        assertEquals(Game.FirearmCommandResult.FIRED,
                game.updateFirearmCommand(0f, true, true, false, EAST));
        SettledNpcAI.update(game, listener, 0.31f);

        assertTrue(listener.lastKnownAge < 10f,
                "a hostile beyond sight range learns the gunshot position by hearing");
        assertEquals(game.camera.position.x, listener.lastKnown.x, 0.001f);
        assertEquals(game.camera.position.z, listener.lastKnown.z, 0.001f);
        assertTrue(listener.searchTimer > 0f,
                "the heard shot creates a real investigate/combat response");
    }

    @Test
    void relicAutoRifleFiresAgainFromAContinuousHeldTriggerAfterCooldown() {
        ItemStack rifle = equip(ItemType.RELIC_RIFLE);
        WeaponDefinition definition = WeaponRegistry.of(rifle.type);
        game.player.inventory.add(ItemType.RIFLE_CARTRIDGE, 3);

        assertTrue(definition.relic && definition.automatic);
        assertEquals(Game.FirearmCommandResult.RELOAD_STARTED,
                game.updateFirearmCommand(0f, false, false, true, EAST));
        game.tickReload(definition.reloadTime + 0.01f);
        assertEquals(3, rifle.charge);

        float durabilityBefore = rifle.durability;
        assertEquals(Game.FirearmCommandResult.FIRED,
                game.updateFirearmCommand(0f, true, true, false, EAST));
        assertEquals(Game.FirearmCommandResult.COOLDOWN,
                game.updateFirearmCommand(0.05f, true, false, false, EAST));
        assertEquals(Game.FirearmCommandResult.FIRED,
                game.updateFirearmCommand(definition.attackInterval - 0.05f + 0.001f,
                        true, false, false, EAST),
                "automatic fire deliberately uses the held trigger after the interval");

        assertEquals(1, rifle.charge);
        assertEquals(durabilityBefore - definition.durabilityCost * 2f,
                rifle.durability, 0.0001f);
        assertEquals(2, game.projectiles.liveCount());
        assertEquals(2, game.noise.countCategory("gunshot"));

        game.projectiles.update(game, 10f);
        assertEquals(0, game.projectiles.liveCount());
    }

    private ItemStack equip(ItemType type) {
        ItemStack stack = new ItemStack(type, 1);
        game.player.inventory.set(0, stack);
        return stack;
    }

    private void flattenArena() {
        Chunk chunk = game.world.getOrCreateChunk(19, 19);
        for (int lx = 0; lx < Chunk.SX; lx++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                for (int y = 0; y < Chunk.SY; y++) {
                    chunk.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                }
            }
        }
        chunk.recomputeAllHeights();
        chunk.rebuildLights();
    }
}
