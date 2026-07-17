package com.veylon.ai;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Gameplay-facing perception coverage: native movement/mining commands and
 * real creature attacks emit positioned sounds, while human sight respects
 * occlusion/FOV and ranged actors avoid firing through allies.
 */
class HumanPerceptionGameplayTest {

    private Game game;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newWorld(20260716L, true);
        game.entities.npcs.clear();
        game.entities.creatures.clear();
        game.noise.reset();
        flattenArena();
        game.player.pos.set(310.5f, 40.1f, 310.5f);
        game.camera.position.set(game.player.pos.x,
                game.player.pos.y + game.player.eyeHeight(), game.player.pos.z);
    }

    @Test
    void sprintAndStructureBreakEmitPositionedEventsThatOccludedGuardInvestigates() {
        Npc guard = hostileResident("Listener", NpcArchetype.GUARD,
                328.5f, 40.1f, 310.5f, 0);
        buildSightWall(319);

        assertTrue(game.emitPlayerFootstepNoise(false, false));
        SettledNpcAI.update(game, guard, 0.31f);
        assertTrue(guard.lastKnownAge > 900f,
                "an occluded ordinary footstep outside its audible radius reveals nothing");

        game.noise.reset();
        guard.decideTimer = 0f;
        assertFalse(game.emitPlayerFootstepNoise(true, true),
                "crouching suppresses the native footstep event");
        assertEquals(0, game.noise.countCategory("sprint"));

        assertTrue(game.emitPlayerFootstepNoise(true, false));
        SettledNpcAI.update(game, guard, 0.31f);
        assertEquals(1, game.noise.countCategory("sprint"));
        assertEquals(game.player.pos.x, guard.lastKnown.x, 0.001f);
        assertEquals(game.player.pos.z, guard.lastKnown.z, 0.001f);
        assertTrue(guard.searchTimer > 0f,
                "the guard investigates the heard world position, not a telepathic target");

        game.noise.reset();
        guard.lastKnownAge = 999f;
        guard.searchTimer = 0f;
        guard.decideTimer = 0f;
        Vec3i plank = new Vec3i(312, 40, 310);
        game.world.setBlock(plank.x(), plank.y(), plank.z(), BlockType.PLANK, false);
        assertTrue(game.completePlayerBlockBreak(plank));
        assertEquals(1, game.noise.countCategory("structure-break"));
        SettledNpcAI.update(game, guard, 0.31f);
        assertEquals(plank.x() + 0.5f, guard.lastKnown.x, 0.001f);
        assertEquals(plank.z() + 0.5f, guard.lastKnown.z, 0.001f);
    }

    @Test
    void viewConeAndWallsGateSightThenSearchTimesOutAndReturnsToDuty() {
        Npc guard = hostileResident("Watcher", NpcArchetype.GUARD,
                320.5f, 40.1f, 310.5f, 0);
        guard.yaw = 90f; // faces east, away from the player to its west
        SettledNpcAI.update(game, guard, 0.31f);
        assertTrue(guard.lastKnownAge > 900f, "a target behind the view cone is not seen");

        buildSightWall(315);
        guard.yaw = 270f;
        guard.decideTimer = 0f;
        SettledNpcAI.update(game, guard, 0.31f);
        assertTrue(guard.lastKnownAge > 900f, "opaque voxels block human line of sight");

        clearSightWall(315);
        guard.yaw = 270f; // daily duty may have turned the guard while blind
        guard.decideTimer = 0f;
        SettledNpcAI.update(game, guard, 0.31f);
        assertEquals(0f, guard.lastKnownAge, 0.001f);
        assertTrue(guard.searchTimer > 0f);

        game.player.pos.set(350.5f, 40.1f, 350.5f);
        for (int second = 0; second < 45; second++) {
            SettledNpcAI.update(game, guard, 1f);
        }
        assertTrue(guard.lastKnownAge > 25f);
        assertEquals(0f, guard.searchTimer, 0.001f,
                "stale searches retire instead of freezing a residual timer forever");
        assertEquals(Npc.NpcState.GUARD, guard.state,
                "after search timeout a guard resumes its settlement duty");
    }

    @Test
    void rangedGuardHoldsFireForAllyThenUsesSharedProjectileRulesWhenLaneClears() {
        Npc archer = hostileResident("Archer", NpcArchetype.ARCHER,
                310.5f, 40.1f, 310.5f, 0);
        Npc ally = hostileResident("Shield", NpcArchetype.GUARD,
                315.5f, 40.1f, 310.5f, 1);
        game.player.pos.set(320.5f, 40.1f, 310.5f);
        archer.yaw = 90f;

        SettledNpcAI.update(game, archer, 0.31f);
        assertEquals(0, game.projectiles.liveCount(),
                "the archer does not fire through a same-side resident");

        ally.pos.z += 3f;
        SettledNpcAI.update(game, archer, 0.31f);
        assertEquals(1, game.projectiles.liveCount(),
                "with a clear lane the same AI fires through the shared projectile system");
    }

    @Test
    void realCreatureAttackEmitsItsOwnWorldPosition() {
        Creature thornhorn = game.entities.spawnCreature(game.world,
                Creature.CreatureType.THORNHORN, 311.7f, 40.1f, 310.5f);
        thornhorn.health -= 1f;
        thornhorn.fear = 1f;
        float healthBefore = game.player.health;

        game.entities.fastTick(game, 0.05f);

        assertTrue(game.player.health < healthBefore,
                "the production creature tick lands the charge attack");
        assertEquals(1, game.noise.countCategory("creature-attack"));
        var heard = game.noise.loudestAudible(thornhorn.pos.x, thornhorn.pos.y,
                thornhorn.pos.z, 0f);
        assertEquals(thornhorn.pos.x, heard.x, 0.001f);
        assertEquals(thornhorn.pos.z, heard.z, 0.001f);
        assertFalse(heard.playerSource);
    }

    private Npc hostileResident(String name, NpcArchetype archetype,
                                float x, float y, float z, int index) {
        long id = Settlement.packId(700, 700);
        Settlement settlement = game.world.settlements.get(id);
        if (settlement == null) {
            settlement = new Settlement(id, 700, 700, SettlementType.FORT,
                    new Vec3i(320, 40, 310), HumanFaction.HEADHUNTERS,
                    Settlement.Alignment.HOSTILE);
            game.world.settlements.put(id, settlement);
        }
        while (settlement.residents.size() <= index) {
            settlement.residents.add(new Settlement.Resident(
                    "Resident " + settlement.residents.size(), NpcArchetype.GUARD));
        }
        Settlement.Resident record = settlement.residents.get(index);
        record.archetype = archetype;
        Npc npc = game.entities.spawnNpc(game.world, name, x, y, z);
        npc.archetype = archetype;
        npc.settlementId = id;
        npc.residentIndex = index;
        npc.maxHealth = archetype.maxHealth;
        npc.health = archetype.maxHealth;
        record.live = npc;
        return npc;
    }

    private void buildSightWall(int x) {
        for (int y = 39; y <= 44; y++) {
            for (int z = 308; z <= 312; z++) {
                game.world.setBlock(x, y, z, BlockType.STONE_BRICK, false);
            }
        }
    }

    private void clearSightWall(int x) {
        for (int y = 40; y <= 44; y++) {
            for (int z = 308; z <= 312; z++) {
                game.world.setBlock(x, y, z, BlockType.AIR, false);
            }
        }
    }

    private void flattenArena() {
        for (int cx = 18; cx <= 22; cx++) {
            for (int cz = 18; cz <= 22; cz++) {
                Chunk chunk = game.world.getOrCreateChunk(cx, cz);
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
    }
}
