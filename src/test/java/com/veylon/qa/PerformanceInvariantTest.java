package com.veylon.qa;

import com.veylon.Game;
import com.veylon.ai.NpcAI;
import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PerformanceInvariantTest {

    @Test
    void runtimeLightChangesUsePointUpdatesInsteadOfFullChunkRescans() {
        Game game = game(901L);
        int x = (int) game.player.pos.x + 2;
        int y = (int) game.player.pos.y + 5;
        int z = (int) game.player.pos.z + 2;
        Chunk chunk = game.world.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        long fullBefore = chunk.fullLightRebuilds;
        long pointBefore = chunk.incrementalLightUpdates;

        game.world.setBlock(x, y, z, BlockType.TORCH, false);
        game.world.setBlock(x, y, z, BlockType.AIR, false);

        assertEquals(fullBefore, chunk.fullLightRebuilds);
        assertEquals(pointBefore + 2, chunk.incrementalLightUpdates);
    }

    @Test
    void expiredProjectileObjectIsReusedAndPoolRemainsBounded() {
        Game game = game(902L);
        var weapon = WeaponRegistry.byId("musket");
        game.projectiles.fire(game, game.player, true,
                game.player.pos.x, game.player.pos.y + 1, game.player.pos.z,
                1, 0, 0, weapon, ItemType.MUSKET_BALL);
        ProjectileSystem.Projectile first = game.projectiles.live.getFirst();
        first.life = 0.001f;
        game.projectiles.update(game, 0.01f);
        assertEquals(1, game.projectiles.pooledCount());

        game.projectiles.fire(game, game.player, true,
                game.player.pos.x, game.player.pos.y + 1, game.player.pos.z,
                1, 0, 0, weapon, ItemType.MUSKET_BALL);

        assertSame(first, game.projectiles.live.getFirst());
        assertTrue(game.projectiles.pooledCount()
                <= ProjectileSystem.MAX_LIVE + ProjectileSystem.MAX_STUCK);
    }

    @Test
    void settledNpcPerceptionRunsAtConfiguredCadenceNotTwicePerTick() {
        Game game = game(903L);
        int x = (int) game.player.pos.x + 5;
        int z = (int) game.player.pos.z;
        int y = game.world.surfaceHeight(x, z) + 1;
        Settlement home = new Settlement(123456L, 0, 0, SettlementType.VILLAGE,
                new Vec3i(x, y, z), HumanFaction.HEADHUNTERS,
                Settlement.Alignment.HOSTILE);
        game.world.settlements.put(home.id, home);
        Npc npc = game.entities.spawnNpc(game.world, "cadence guard", x + 0.5f, y, z + 0.5f);
        npc.archetype = NpcArchetype.GUARD;
        npc.settlementId = home.id;
        npc.residentIndex = 0;
        home.residents.add(new Settlement.Resident("cadence guard", NpcArchetype.GUARD));
        npc.decideTimer = 0;

        for (int i = 0; i < 20; i++) {
            NpcAI.update(game, npc, 0.05f);
        }

        assertTrue(npc.perceptionChecks >= 3 && npc.perceptionChecks <= 4,
                "one second should contain only the scheduled LOS queries: "
                        + npc.perceptionChecks);
    }

    private static Game game(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        return game;
    }
}
