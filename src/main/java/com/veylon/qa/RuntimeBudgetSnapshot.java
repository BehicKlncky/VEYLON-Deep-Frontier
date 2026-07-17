package com.veylon.qa;

import com.veylon.Game;
import com.veylon.ai.Pathfinder;
import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WorldNoise;
import com.veylon.engine.ParticleSystem;
import com.veylon.entity.Npc;
import com.veylon.settlement.CounterattackDirector;
import com.veylon.settlement.SettlementManager;
import com.veylon.simulation.FireSystem;

/**
 * Read-only occupancy snapshot for the bounded runtime systems introduced by
 * the world expansion. It is shared by the F3 overlay, smoke diagnostics and
 * deterministic release tests so reported values match the live collections.
 */
public record RuntimeBudgetSnapshot(
        SettlementManager.NpcCounts npcs,
        int liveProjectiles,
        int stuckArrows,
        int cachedPathNodes,
        int largestCachedPath,
        int counterattackMissions,
        int dormantCounterattackers,
        int noiseEvents,
        int kegFuses,
        int kegFuseAttributions,
        int fires,
        int particles,
        int pendingGenerationChunks,
        int pendingGenerationEdits) {

    public static RuntimeBudgetSnapshot capture(Game game) {
        int pathNodes = 0;
        int largestPath = 0;
        for (Npc npc : game.entities.npcs) {
            if (npc.path == null) {
                continue;
            }
            pathNodes += npc.path.size();
            largestPath = Math.max(largestPath, npc.path.size());
        }
        int dormantAttackers = game.settlementManager.counterattacks.missions.values().stream()
                .mapToInt(mission -> Math.max(0, mission.survivors))
                .sum();
        return new RuntimeBudgetSnapshot(
                game.settlementManager.npcCounts(game),
                game.projectiles.liveCount(),
                game.projectiles.stuck.size(),
                pathNodes,
                largestPath,
                game.settlementManager.counterattacks.missions.size(),
                dormantAttackers,
                game.noise.count(),
                game.world.kegFuses.size(),
                game.world.kegFusePlayerAttribution.size(),
                game.fire.count(),
                game.particles.count,
                game.world.pendingGenerationChunkCount(),
                game.world.pendingGenerationEditCount());
    }

    /** True when every collection with a hard runtime ceiling is within it. */
    public boolean withinHardLimits() {
        return npcs.total() <= SettlementManager.MAX_ACTIVE_NPCS
                && npcs.residents() <= SettlementManager.MAX_ACTIVE_RESIDENTS
                && npcs.warParties() <= SettlementManager.MAX_ACTIVE_WAR_PARTY_NPCS
                && npcs.patrols() <= SettlementManager.MAX_ACTIVE_PATROL_NPCS
                && npcs.bountyHunters() <= SettlementManager.MAX_ACTIVE_BOUNTY_HUNTERS
                && npcs.counterattackers() <= SettlementManager.MAX_ACTIVE_COUNTERATTACKERS
                && npcs.legacy() <= SettlementManager.MAX_ACTIVE_LEGACY_NPCS
                && liveProjectiles <= ProjectileSystem.MAX_LIVE
                && stuckArrows <= ProjectileSystem.MAX_STUCK
                && largestCachedPath <= Pathfinder.MAX_PATH_NODES
                && cachedPathNodes <= SettlementManager.MAX_ACTIVE_NPCS
                * Pathfinder.MAX_PATH_NODES
                && counterattackMissions <= CounterattackDirector.MAX_MISSIONS
                && dormantCounterattackers <= CounterattackDirector.MAX_DORMANT_ATTACKERS
                && noiseEvents <= WorldNoise.MAX_EVENTS
                && kegFuses <= ExplosionSystem.MAX_ACTIVE_FUSES
                && kegFuseAttributions <= kegFuses
                && fires <= FireSystem.MAX_ACTIVE_FIRES
                && particles <= ParticleSystem.MAX;
    }

    /** Compact live occupancy used verbatim by smoke output and failure logs. */
    public String occupancySummary() {
        return "npcs={total=" + npcs.total()
                + ",residents=" + npcs.residents()
                + ",parties=" + npcs.warParties()
                + ",patrols=" + npcs.patrols()
                + ",bounty=" + npcs.bountyHunters()
                + ",counterattack=" + npcs.counterattackers()
                + ",legacy=" + npcs.legacy() + "}"
                + " projectiles={live=" + liveProjectiles + ",stuck=" + stuckArrows + "}"
                + " paths={nodes=" + cachedPathNodes + ",largest=" + largestCachedPath + "}"
                + " missions={active=" + counterattackMissions
                + ",dormantAttackers=" + dormantCounterattackers + "}"
                + " noise=" + noiseEvents
                + " fuses={timers=" + kegFuses
                + ",attribution=" + kegFuseAttributions + "}"
                + " fires=" + fires
                + " particles=" + particles
                + " generation={chunks=" + pendingGenerationChunks
                + ",edits=" + pendingGenerationEdits + "}";
    }

    public static String hardLimitSummary() {
        return "npc=" + SettlementManager.MAX_ACTIVE_NPCS
                + " resident=" + SettlementManager.MAX_ACTIVE_RESIDENTS
                + " party=" + SettlementManager.MAX_ACTIVE_WAR_PARTY_NPCS
                + " projectile=" + ProjectileSystem.MAX_LIVE
                + " stuck=" + ProjectileSystem.MAX_STUCK
                + " pathQuery=" + Pathfinder.DEFAULT_BUDGET
                + " pathNodes=" + Pathfinder.MAX_PATH_NODES
                + " pathCache=" + SettlementManager.MAX_ACTIVE_NPCS * Pathfinder.MAX_PATH_NODES
                + " mission=" + CounterattackDirector.MAX_MISSIONS
                + " dormantAttackers=" + CounterattackDirector.MAX_DORMANT_ATTACKERS
                + " noise=" + WorldNoise.MAX_EVENTS
                + " fuses=" + ExplosionSystem.MAX_ACTIVE_FUSES
                + " fire=" + FireSystem.MAX_ACTIVE_FIRES
                + " particles=" + ParticleSystem.MAX
                + " chain=" + ExplosionSystem.MAX_CHAIN
                + " blockEdits=" + ExplosionSystem.MAX_BLOCKS;
    }
}
