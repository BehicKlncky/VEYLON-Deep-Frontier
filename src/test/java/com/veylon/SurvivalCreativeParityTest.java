package com.veylon;

import com.veylon.ai.SettledNpcAI;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.Raycaster;
import org.joml.Vector3f;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** R26: characterize Survival before abilities can change these production paths. */
class SurvivalCreativeParityTest {

    private Game game;
    private static final Vec3i BLOCK = new Vec3i(312, 40, 310);

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newWorld(20260910L, true);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        game.fire.reset();
        game.noise.reset();
        for (int cx = 19; cx <= 20; cx++) {
            for (int cz = 19; cz <= 20; cz++) {
                Chunk chunk = game.world.getOrCreateChunk(cx, cz);
                for (int x = 0; x < 16; x++) {
                    for (int z = 0; z < 16; z++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            chunk.set(x, y, z, y < 40 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                chunk.recomputeAllHeights();
                chunk.rebuildLights();
            }
        }
        game.player.pos.set(310.5f, 40.001f, 310.5f);
        game.player.vel.zero();
        game.player.onGround = true;
        game.camera.position.set(310.5f, 41.6f, 310.5f);
    }

    @Test
    void placementConsumesExactlyOneHeldItem() {
        game.player.inventory.set(0, new ItemStack(ItemType.PLANK, 5));
        assertTrue(game.placeSelectedBlockAt(BLOCK.x(), BLOCK.y(), BLOCK.z()),
                "R26: a valid Survival placement must succeed");
        assertEquals(4, game.player.selected().count,
                "R26: Survival placement consumes exactly one item");
        assertEquals(BlockType.PLANK, game.world.getBlock(BLOCK.x(), BLOCK.y(), BLOCK.z()));
    }

    @ParameterizedTest
    @CsvSource({"DIRT, NONE", "LOG, NONE", "LOG, STONE_AXE", "STONE, STONE_PICKAXE"})
    void holdingMineUsesTheOriginalHardnessAndToolRate(BlockType block, String tool) {
        ItemType item = tool.equals("NONE") ? null : ItemType.valueOf(tool);
        if (item != null) game.player.inventory.set(0, new ItemStack(item, 1));
        target(block);
        game.mine(0.025f);
        float multiplier = item != null && item.tool == block.preferredTool ? item.toolPower : 1f;
        assertEquals(0.025f * multiplier / Math.max(0.05f, block.hardness),
                game.miningProgress, 0.000001f,
                "R26: hold-to-mine progress must retain the v0.6.0 hardness/tool rate");
        assertEquals(block, game.world.getBlock(BLOCK.x(), BLOCK.y(), BLOCK.z()),
                "R26: a short Survival press cannot instantly break this block");
    }

    @Test
    void requiredToolCannotBeBypassedByHoldingOrCompletingABreak() {
        target(BlockType.IRON_ORE);
        game.mine(100f);
        assertEquals(0f, game.miningProgress, "R26: bare hands make no progress on tool-only ore");
        assertFalse(game.completePlayerBlockBreak(BLOCK),
                "R26: the completion command also enforces the required tool");
        assertEquals(BlockType.IRON_ORE, game.world.getBlock(BLOCK.x(), BLOCK.y(), BLOCK.z()));
    }

    @Test
    void breakingAwardsDefinedDropsAndWearsTheToolOnce() {
        ItemStack pick = new ItemStack(ItemType.STONE_PICKAXE, 1);
        game.player.inventory.set(0, pick);
        target(BlockType.IRON_ORE);
        float durability = pick.durability;
        assertTrue(game.completePlayerBlockBreak(BLOCK), "R26: the correct tool breaks ore");
        assertEquals(BlockType.IRON_ORE.dropCount,
                game.player.inventory.count(BlockType.IRON_ORE.drop),
                "R26: Survival mining awards the existing enum-defined drop count");
        assertEquals(durability - 1f, pick.durability,
                "R26: one successful break wears the tool exactly once");
    }

    @Test
    void meleeWearsTheWeaponAndHonorsItsCooldown() {
        ItemStack weapon = new ItemStack(ItemType.STONE_AXE, 1);
        game.player.inventory.set(0, weapon);
        Creature victim = game.entities.spawnCreature(game.world, Creature.CreatureType.DEER,
                311.5f, 40.001f, 310.5f);
        float durability = weapon.durability;
        assertTrue(game.performPlayerAttack(victim), "R26: an adjacent target receives the swing");
        assertEquals(durability - 1f, weapon.durability, "R26: a melee hit wears the weapon");
        assertFalse(game.performPlayerAttack(victim), "R26: cooldown refuses a second instant swing");
        assertEquals(durability - 1f, weapon.durability, "R26: refused swings do not cost durability");
    }

    @Test
    void throwingConsumesOneBombThroughTheNativeCommand() {
        game.player.inventory.set(0, new ItemStack(ItemType.SCRAP_BOMB, 3));
        assertTrue(game.updateThrownWeaponCommand(0.05f, true, new Vector3f(1, 0, 0)),
                "R26: the production throw command lights a bomb");
        assertEquals(2, game.player.selected().count, "R26: throwing consumes exactly one bomb");
        assertEquals(1, game.projectiles.liveCount(), "R26: one press produces one live bomb");
    }

    @Test
    void eatingConsumesOneItemAndRestoresHunger() {
        game.player.hunger = 20f;
        game.player.inventory.set(0, new ItemStack(ItemType.COOKED_MEAT, 3));
        game.consumables.eat(game.player.selected());
        assertEquals(2, game.player.selected().count, "R26: eating consumes exactly one item");
        assertEquals(20f + ItemType.COOKED_MEAT.food, game.player.hunger,
                "R26: fresh food retains its full hunger benefit");
    }

    @ParameterizedTest
    @CsvSource({"0, 100", "100, 0"})
    void starvationAndDehydrationDamageTheSurvivalPlayer(float hunger, float thirst) {
        game.player.hunger = hunger;
        game.player.thirst = thirst;
        game.player.tickNeeds(game, 0.05f);
        assertTrue(game.player.health < game.player.maxHealth,
                "R26: each exhausted need independently damages the Survival player");
    }

    @Test
    void fireContactDamagesAndFlashesTheSurvivalPlayer() {
        game.world.setBlock(310, 40, 310, BlockType.LOG, false);
        assertTrue(game.fire.ignite(game, 310, 40, 310), "R26: fixture fire must ignite");
        game.fire.mediumTick(game, 0.5f);
        assertTrue(game.player.health < game.player.maxHealth, "R26: real fire contact burns");
        assertEquals(1f, game.player.damageFlash, "R26: fire damage retains feedback");
    }

    @Test
    void aFallBeyondTheSafeDistanceDamagesAfterLanding() {
        game.player.pos.y = 49f;
        game.player.onGround = false;
        PlayerMovementSystem movement = new PlayerMovementSystem();
        PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
        PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();
        for (int i = 0; i < 100 && !game.player.onGround; i++) {
            movement.update(game.player, game.world, command, 0.05f, result);
        }
        assertTrue(game.player.onGround, "R26: isolated fall must reach the floor");
        game.player.tickNeeds(game, 0.05f);
        assertTrue(game.player.health < 90f, "R26: the landing queues meaningful fall damage");
        assertTrue(game.eventLog.recent(30).stream().anyMatch(s -> s.contains("hit the ground")),
                "R26: a damaging fall retains its log feedback");
    }

    @Test
    void anAdjacentWolfAttacksWithinTwentyFastTicks() {
        Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                311.5f, 40.001f, 310.5f);
        wolf.hunger = 80f;
        for (int i = 0; i < 20 && game.player.health == game.player.maxHealth; i++) {
            game.entities.fastTick(game, 0.05f);
        }
        assertTrue(game.player.health < game.player.maxHealth,
                "R26: a fixed-seed adjacent predator must detect and attack the player");
    }

    @Test
    void hostileSightRaisesAnAlarmThroughTheSettlerTick() {
        long id = Settlement.packId(700, 700);
        Settlement settlement = new Settlement(id, 700, 700, SettlementType.FORT,
                new Vec3i(320, 40, 310), HumanFaction.HEADHUNTERS, Settlement.Alignment.HOSTILE);
        game.world.settlements.put(id, settlement);
        Npc guard = game.entities.spawnNpc(game.world, "Parity guard", 312f, 40.001f, 310.5f);
        guard.archetype = NpcArchetype.GUARD;
        guard.settlementId = id;
        guard.residentIndex = 0;
        Settlement.Resident resident = new Settlement.Resident("Parity guard", NpcArchetype.GUARD);
        resident.live = guard;
        settlement.residents.add(resident);
        guard.yaw = 270f;
        SettledNpcAI.update(game, guard, 0.31f);
        assertTrue(settlement.alertLevel >= 45f, "R26: first hostile sight raises the alarm");
        assertEquals(1, game.noise.countCategory("alarm-shout"),
                "R26: the first contact produces a positioned alarm shout");
    }

    @Test
    void doubleTappingJumpNeverProducesSustainedSurvivalFlight() {
        PlayerMovementSystem movement = new PlayerMovementSystem();
        PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
        PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();
        for (int i = 0; i < 80; i++) {
            command.jumpPressed = i == 0 || i == 4;
            command.jumpHeld = i <= 4;
            movement.update(game.player, game.world, command, 0.05f, result);
        }
        assertTrue(game.player.onGround, "R26: double-tapping must still land in Survival");
        assertEquals(40f, game.player.pos.y, 0.065f,
                "R26: Survival lands within one gravity step of the floor, never hovering");
    }

    @Test
    void aKillInSurvivalLeavesAFallingBodyThatSettlesIntoACarcass() {
        assertEquals(bodiesLeftByAKill(game), 1,
                "R26: a Survival kill leaves exactly one body behind");
    }

    @Test
    void aKillInCreativeLeavesTheSameBodyAndDoesNotPauseSpawning() {
        // The player is invulnerable in Creative, but everything else still
        // dies, and a game mode must not change what a death leaves behind.
        Game creative = CreativeTestArena.create(com.veylon.entity.GameMode.CREATIVE);
        assertFalse(creative.spawningPaused(),
                "R26: the spawning control is untouched by a kill");
        assertEquals(bodiesLeftByAKill(creative), 1,
                "R26: a Creative kill leaves exactly the same body a Survival kill does");
        assertFalse(creative.spawningPaused(),
                "R26: settling a body must not disturb the world controls");
    }

    /**
     * Kills one deer from a fixed impulse and returns how many carcasses the
     * settled body produced. Shared so the Creative case is the same fixture as
     * the Survival one, which is the only way a mode-dependent gate shows up.
     */
    private static int bodiesLeftByAKill(Game world) {
        world.entities.carcasses.clear();
        world.ragdolls.reset();
        Creature deer = world.entities.spawnCreature(world.world, Creature.CreatureType.DEER,
                world.player.pos.x + 2f, world.player.pos.y, world.player.pos.z);
        deer.hurt(deer.health + 100f, true);
        deer.vel.set(2f, 1f, 0f);
        world.entities.fastTick(world, 0.05f);
        assertEquals(1, world.ragdolls.liveCount(),
                "R26: the body falls before it becomes a carcass in either mode");
        for (int i = 0; i < 1200 && world.ragdolls.liveCount() > 0; i++) {
            world.ragdolls.update(world, 1f / 60f);
        }
        return world.entities.carcasses.size();
    }

    private void target(BlockType type) {
        game.world.setBlock(BLOCK.x(), BLOCK.y(), BLOCK.z(), type, false);
        game.targetHit = new Raycaster.Hit(BLOCK.x(), BLOCK.y(), BLOCK.z(), -1, 0, 0, 2, type);
    }
}
