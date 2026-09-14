package com.veylon;

import com.veylon.ai.CreatureAI;
import com.veylon.ai.NpcAI;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.EventSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import static com.veylon.CreativeBodyTest.*;
import static org.junit.jupiter.api.Assertions.*;

/** R8: each external damage/illness command has a harmful Survival twin. */
class CreativeHazardsTest {
    enum Hazard { FIRE, EXPLOSION, ARROW, BULLET, FOOD, DIRTY_WATER, OPEN_WATER, TOXIC_FOG, SLEEP }

    @ParameterizedTest
    @EnumSource(Hazard.class)
    void externalHazardsCannotInjureTheCreativeBody(Hazard hazard) {
        for (GameMode mode : GameMode.values()) {
            AudioProbe audio = new AudioProbe();
            Game game = CreativeTestArena.create(mode, audio);
            apply(game, hazard);
            if (mode == GameMode.CREATIVE) {
                assertEquals(game.player.maxHealth, game.player.health, "R8: " + hazard + " does no damage");
                assertFalse(game.player.dead, "R8: " + hazard + " cannot kill");
                assertTrue(game.player.afflictions.isEmpty(), "R8: " + hazard + " causes no illness");
                assertNoInjuryFeedback(game, audio);
            } else {
                assertTrue(game.player.health < game.player.maxHealth || !game.player.afflictions.isEmpty(),
                        "Survival fixture must still suffer " + hazard);
            }
        }
    }

    private void apply(Game game, Hazard hazard) {
        switch (hazard) {
            case FIRE -> {
                game.world.setBlock(310, 40, 310, BlockType.LOG, false);
                assertTrue(game.fire.ignite(game, 310, 40, 310));
                game.fire.mediumTick(game, 0.5f);
            }
            case EXPLOSION -> game.explosions.explode(game, 311.5f, 41, 310.5f, 2, 30, 0, false);
            case ARROW, BULLET -> {
                Npc shooter = game.entities.spawnNpc(game.world, "Shooter", 314.5f, 40, 310.5f);
                int before = game.particles.count;
                game.projectiles.fire(game, shooter, false, 313.5f, 41, 310.5f, -1, 0, 0,
                        WeaponRegistry.byId(hazard == Hazard.ARROW ? "primitive_bow" : "musket"),
                        hazard == Hazard.ARROW ? ItemType.ARROW : null);
                for (int i = 0; i < 20; i++) game.projectiles.update(game, 0.01f);
                if (game.player.abilities.invulnerable()) {
                    assertEquals(before, game.particles.count, "R8: immune projectile impact emits no blood");
                }
            }
            case FOOD -> {
                for (int i = 0; i < 32; i++) {
                    game.player.hunger = 20;
                    game.player.inventory.set(0, new ItemStack(ItemType.SPOILED_MEAT, 1));
                    game.consumables.eat(game.player.selected());
                }
            }
            case DIRTY_WATER -> {
                for (int i = 0; i < 32; i++) {
                    game.player.inventory.set(0, new ItemStack(ItemType.WATERSKIN_DIRTY, 1));
                    game.consumables.drink(game.player.selected());
                }
            }
            case OPEN_WATER -> {
                game.world.setBlock(310, 41, 308, BlockType.WATER, false);
                for (int i = 0; i < 32; i++) game.interactions.interact();
                assertTrue(game.eventLog.recent(100).stream().anyMatch(s -> s.contains("water")),
                        "Fixture F-key ray must reach the open water");
            }
            case TOXIC_FOG -> {
                game.events.active.add(new EventSystem.ActiveEvent(EventSystem.EventType.TOXIC_FOG, 10000, 1));
                for (int i = 0; i < 512; i++) game.player.tickToxicFogExposure(game);
            }
            case SLEEP -> {
                for (int i = 0; i < 32; i++) {
                    game.time.totalMinutes = 22 * 60;
                    game.player.envTemp = -20;
                    game.startSleep(false);
                    assertTrue(game.sleeping, "Fixture must enter poor outdoor sleep");
                    game.sleep.tickSleep(2);
                }
            }
        }
    }

    @ParameterizedTest
    @EnumSource(value = Creature.CreatureType.class, names = {"THORNHORN", "WOLF", "STALKER"})
    void everyCreatureMeleeSourceHasNoCreativeInjuryFeedback(Creature.CreatureType type) {
        for (GameMode mode : GameMode.values()) {
            AudioProbe audio = new AudioProbe();
            Game game = CreativeTestArena.create(mode, audio);
            game.time.totalMinutes = 0;
            Creature attacker = game.entities.spawnCreature(game.world, type, 311.5f, 40.001f, 310.5f);
            attacker.hunger = 80;
            if (type == Creature.CreatureType.THORNHORN) {
                attacker.health -= 1;
                attacker.fear = 1;
            }
            for (int i = 0; i < 20; i++) CreatureAI.update(game, attacker, 0.05f);
            if (mode == GameMode.CREATIVE) {
                assertFull(game.player);
                assertNoInjuryFeedback(game, audio);
                assertEquals(0, game.player.vel.lengthSquared(), "R8: creature cannot knock the player back");
            } else assertTrue(game.player.health < game.player.maxHealth, "Survival " + type + " still attacks");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"camp", "raider", "settler"})
    void everyHumanMeleeSourceHasNoCreativeInjuryFeedback(String source) {
        for (GameMode mode : GameMode.values()) {
            AudioProbe audio = new AudioProbe();
            Game game = CreativeTestArena.create(mode, audio);
            Npc attacker = game.entities.spawnNpc(game.world, "Attacker", 311.5f, 40.001f, 310.5f);
            switch (source) {
                case "camp" -> {
                    attacker.faction = game.faction;
                    game.faction.hostile = true;
                }
                case "raider" -> {
                    attacker.raider = true;
                    attacker.leaveTimer = 60;
                }
                case "settler" -> {
                    long id = Settlement.packId(700, 700);
                    Settlement settlement = new Settlement(id, 700, 700, SettlementType.FORT,
                            new Vec3i(320, 40, 310), HumanFaction.HEADHUNTERS, Settlement.Alignment.HOSTILE);
                    game.world.settlements.put(id, settlement);
                    attacker.archetype = NpcArchetype.BRUTE;
                    attacker.settlementId = id;
                    attacker.residentIndex = 0;
                    Settlement.Resident resident = new Settlement.Resident("Attacker", NpcArchetype.BRUTE);
                    resident.live = attacker;
                    settlement.residents.add(resident);
                    attacker.lastKnown.set(game.player.pos);
                    attacker.lastKnownAge = 0;
                }
                default -> throw new AssertionError(source);
            }
            for (int i = 0; i < 10; i++) NpcAI.update(game, attacker, 0.05f);
            if (mode == GameMode.CREATIVE) {
                assertFull(game.player);
                assertNoInjuryFeedback(game, audio);
                assertEquals(0, game.player.vel.lengthSquared(), "R8: human cannot knock the player back");
            } else assertTrue(game.player.health < game.player.maxHealth, "Survival " + source + " still attacks");
        }
    }

    @Test
    void indoorFireKeepsShelterObservationButCannotAccumulateCreativeSmoke() {
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            for (int x = 307; x <= 314; x++) {
                for (int z = 307; z <= 314; z++) {
                    game.world.setBlock(x, 44, z, BlockType.STONE, false);
                    if (x == 307 || x == 314 || z == 307 || z == 314) {
                        for (int y = 40; y < 44; y++) game.world.setBlock(x, y, z, BlockType.STONE, false);
                    }
                }
            }
            Vec3i fire = new Vec3i(311, 40, 311);
            game.world.setBlock(fire.x(), fire.y(), fire.z(), BlockType.CAMPFIRE, false);
            game.world.campfireFuel.put(fire, 100f);
            game.mediumTick(0.5f);
            assertTrue(game.player.shelter.indoor(), "R10: Creative still observes the built shelter");
            assertTrue(game.player.nearFireHeat(game) > 3, "Fixture must expose enclosed fire heat");
            if (mode == GameMode.CREATIVE) {
                assertEquals(0, game.player.smokeExposure, "R8: environmental systems cannot accumulate smoke");
                game.switchGameMode(GameMode.SURVIVAL);
                game.player.tickNeeds(game, 0.05f);
                assertFalse(game.player.has(Affliction.SMOKE), "R5: no stored smoke burst on exit");
            } else assertTrue(game.player.smokeExposure > 0, "Survival enclosed fire still accumulates smoke");
        }
    }

    @Test
    void aBasaltFumaroleStillEmitsWorldSmokeWithoutFillingCreativeLungs() {
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            int x = 310, z = 310;
            int y = game.world.generator.heightAt(x, z) - 20;
            game.world.setBlock(x, y - 1, z, BlockType.SULFUR_ORE, false);
            game.world.setBlock(x, y, z, BlockType.AIR, false);
            game.world.setBlock(x, y + 1, z, BlockType.AIR, false);
            for (int[] direction : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
                game.world.setBlock(x + direction[0], y - 1, z + direction[1], BlockType.ASH, false);
                game.world.setBlock(x + direction[0], y, z + direction[1], BlockType.AIR, false);
            }
            assertTrue(game.world.isBasaltFumarole(x, y, z), "Fixture must be a real basalt-depth vent");
            game.player.pos.set(x + 0.5f, y, z + 0.5f);
            int particles = game.particles.count;
            new PlayerEnvironmentSystem(game).mediumTick(0.5f);
            assertTrue(game.particles.count > particles, "R10: physical vent smoke remains visible");
            if (mode == GameMode.CREATIVE) assertEquals(0, game.player.smokeExposure, "R8: no fumarole exposure");
            else assertTrue(game.player.smokeExposure > 0, "Survival vent still accumulates smoke");
        }
    }
}
