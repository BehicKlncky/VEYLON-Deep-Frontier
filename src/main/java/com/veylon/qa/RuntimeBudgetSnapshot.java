package com.veylon.qa;

import com.veylon.Game;
import com.veylon.ai.Pathfinder;
import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.ProjectileSystem;
import com.veylon.combat.WorldNoise;
import com.veylon.engine.ParticleSystem;
import com.veylon.entity.BodyFragmentConstants;
import com.veylon.entity.BodyFragmentSystem;
import com.veylon.entity.CombustionConstants;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.entity.RagdollConstants;
import com.veylon.settlement.CounterattackDirector;
import com.veylon.settlement.SettlementManager;
import com.veylon.simulation.FireSystem;
import com.veylon.simulation.LiquidFireConstants;
import com.veylon.world.FixtureStates;
import com.veylon.world.urban.UrbanWorldState;

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
        int liquidFirePatches,
        int particles,
        int pendingGenerationChunks,
        int pendingGenerationEdits,
        int anchoredRemains,
        int burningBodies,
        int livingBodies,
        int burnResidues,
        int liveFragments,
        int settledFragments,
        int liveRagdolls,
        int panickingBodies,
        int urbanRegionPlans,
        int urbanBuildingPlans,
        int urbanRoutePlans,
        int fixtureStates,
        int residentDoors,
        int urbanEncounterRecords,
        int urbanEcologyPlans, int activeAliens, int activeWildlife, int urbanSitePlans, int urbanSiteRecords,
        int urbanVehiclePlans, int damagedBarricades, int doorBraces, int refugeClaims, int tappedBuildings,
        int utilityDevices, int missionRecords, int visitedPlaces, int sketchedFloors, int urbanThreatBudgets,
        int navigationRoutes, int pathQueries, int pathExpansions, int pathDeferred,
        int pathFailures, int unsupportedEntries, int hearingSamples) {

    public static RuntimeBudgetSnapshot capture(Game game) {
        int pathNodes = 0;
        int largestPath = 0;
        int panicking = 0;
        for (Npc npc : game.entities.npcs) {
            panicking += npc.panic.active() ? 1 : 0;
            if (npc.path == null) {
                continue;
            }
            pathNodes += npc.path.size();
            largestPath = Math.max(largestPath, npc.path.size());
        }
        for (Creature creature : game.entities.creatures) {
            panicking += creature.panic.active() ? 1 : 0;
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
                game.liquidFire.count(),
                game.particles.count,
                game.world.pendingGenerationChunkCount(),
                game.world.pendingGenerationEditCount(),
                BodyFragmentSystem.anchoredRemains(game.entities.carcasses),
                game.combustion.burningBodies(game),
                game.entities.npcs.size() + game.entities.creatures.size() + 1,
                game.burnResidues.trackedCount(),
                game.fragments.liveCount(),
                game.fragments.settledCount(),
                game.ragdolls.liveCount(),
                panicking,
                game.world.urban == null ? 0 : game.world.urban.cachedRegionPlans(),
                game.world.urban == null ? 0 : game.world.urban.cachedBuildingPlans(),
                game.world.urban == null ? 0 : game.world.urban.cachedRoutePlans(),
                game.world.fixtures.size(),
                game.world.fixtures.transitTimers().size(), game.entities.encounters.size(),
                game.world.urban == null ? 0 : game.world.urban.cachedEcologyPlans(),
                game.entities.encounters.activeAliens(game), game.entities.encounters.activeWildlife(game),
                game.world.urban == null ? 0 : game.world.urban.cachedSitePlans(),
                com.veylon.settlement.UrbanSiteDirector.sites(game.world).size(),
                game.world.urban == null ? 0 : game.world.urban.cachedVehiclePlans(),
                game.world.urban == null ? 0 : game.world.urban.fortifications.damagedCount(),
                game.world.urban == null ? 0 : game.world.urban.fortifications.braceCount(),
                game.world.urban == null ? 0 : game.world.urban.refuges.size(),
                game.world.urban == null ? 0 : game.world.urban.utilities.tappedCount(),
                game.world.urban == null ? 0 : game.world.urban.utilities.deviceCount(),
                game.world.urban == null ? 0 : game.world.urban.missions.missions().size(),
                game.world.urban == null ? 0
                        : game.world.urban.visits.districtCount() + game.world.urban.visits.buildingCount(),
                game.world.urban == null ? 0 : game.world.urban.visits.sketchedFloors(),
                game.world.urban == null ? 0 : game.world.urban.cachedThreatBudgets(),
                game.world.navigation.size(), game.world.navigation.budget.queries(),
                game.world.navigation.budget.expansions(), game.world.navigation.budget.deferred(),
                game.world.navigation.budget.failures(), game.world.navigation.budget.unsupportedEntries(),
                game.noise.hearingSamples());
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
                && npcs.infected() <= com.veylon.entity.UrbanEncounterDirector.MAX_ACTIVE
                && urbanEncounterRecords <= com.veylon.entity.UrbanEncounterDirector.MAX_RECORDS
                && urbanEcologyPlans <= UrbanWorldState.MAX_REGION_PLANS
                && urbanSitePlans <= UrbanWorldState.MAX_REGION_PLANS
                && urbanVehiclePlans <= UrbanWorldState.MAX_REGION_PLANS
                && urbanThreatBudgets <= UrbanWorldState.MAX_REGION_PLANS
                && damagedBarricades <= com.veylon.world.urban.UrbanFortifications.MAX_DAMAGED
                && doorBraces <= com.veylon.world.urban.UrbanFortifications.MAX_BRACES
                && refugeClaims <= com.veylon.world.urban.UrbanRefuges.MAX_CLAIMS
                && tappedBuildings <= com.veylon.world.urban.UrbanUtilities.MAX_TAPPED
                && utilityDevices <= com.veylon.world.urban.UrbanUtilities.MAX_DEVICES
                && missionRecords <= com.veylon.world.urban.UrbanMissions.MAX_RECORDS
                && visitedPlaces <= com.veylon.world.urban.UrbanVisits.MAX_DISTRICTS
                + com.veylon.world.urban.UrbanVisits.MAX_BUILDINGS
                && sketchedFloors <= com.veylon.world.urban.UrbanVisits.MAX_SKETCHED_FLOORS
                && urbanSiteRecords <= com.veylon.settlement.UrbanSiteDirector.MAX_SITES
                && activeAliens <= com.veylon.entity.UrbanEncounterDirector.MAX_ACTIVE_ALIENS
                && activeWildlife <= com.veylon.entity.UrbanEncounterDirector.MAX_ACTIVE_WILDLIFE
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
                && liquidFirePatches <= LiquidFireConstants.MAX_PATCHES
                && particles <= ParticleSystem.MAX
                && anchoredRemains <= BodyFragmentConstants.MAX_ANCHORED_REMAINS
                && burningBodies <= livingBodies
                && burnResidues <= CombustionConstants.MAX_BURN_RESIDUES
                && liveFragments <= BodyFragmentConstants.MAX_LIVE_FRAGMENTS
                && settledFragments <= BodyFragmentConstants.MAX_SETTLED_FRAGMENTS
                && liveRagdolls <= RagdollConstants.MAX_LIVE
                && urbanRegionPlans <= UrbanWorldState.MAX_REGION_PLANS
                && urbanBuildingPlans <= UrbanWorldState.MAX_BUILDING_PLANS
                && urbanRoutePlans <= UrbanWorldState.MAX_ROUTE_PLANS
                && fixtureStates <= FixtureStates.MAX_ENTRIES
                && residentDoors <= FixtureStates.MAX_TRANSIT
                && navigationRoutes <= com.veylon.ai.NavigationCache.MAX_ROUTES
                && pathQueries <= com.veylon.ai.PathBudget.MAX_QUERIES
                && pathExpansions <= com.veylon.ai.PathBudget.MAX_EXPANSIONS
                && hearingSamples <= WorldNoise.MAX_HEARING_SAMPLES
                // The player never panics.
                && panickingBodies < livingBodies;
    }

    /** Compact live occupancy used verbatim by smoke output and failure logs. */
    public String occupancySummary() {
        return "npcs={total=" + npcs.total()
                + ",residents=" + npcs.residents()
                + ",parties=" + npcs.warParties()
                + ",patrols=" + npcs.patrols()
                + ",bounty=" + npcs.bountyHunters()
                + ",counterattack=" + npcs.counterattackers()
                + ",legacy=" + npcs.legacy() + ",infected=" + npcs.infected() + "}"
                + " projectiles={live=" + liveProjectiles + ",stuck=" + stuckArrows + "}"
                + " paths={nodes=" + cachedPathNodes + ",largest=" + largestCachedPath
                + ",routes=" + navigationRoutes + ",queries=" + pathQueries
                + ",expanded=" + pathExpansions + ",deferred=" + pathDeferred
                + ",failed=" + pathFailures + ",unsupported=" + unsupportedEntries + "}"
                + " hearingSamples=" + hearingSamples
                + " missions={active=" + counterattackMissions
                + ",dormantAttackers=" + dormantCounterattackers + "}"
                + " noise=" + noiseEvents
                + " fuses={timers=" + kegFuses
                + ",attribution=" + kegFuseAttributions + "}"
                + " fires=" + fires
                + " liquidFire=" + liquidFirePatches
                + " particles=" + particles
                + " generation={chunks=" + pendingGenerationChunks
                + ",edits=" + pendingGenerationEdits + "}"
                + " anchoredRemains=" + anchoredRemains
                + " burning=" + burningBodies + "/" + livingBodies
                + " burnResidues=" + burnResidues
                + " fragments={live=" + liveFragments + ",settled=" + settledFragments + "}"
                + " ragdolls=" + liveRagdolls
                + " panicking=" + panickingBodies
                + " urbanPlans=" + urbanRegionPlans
                + " buildingPlans=" + urbanBuildingPlans + " routePlans=" + urbanRoutePlans
                + " encounterRecords=" + urbanEncounterRecords
                + " ecologyPlans=" + urbanEcologyPlans + " creatures={aliens=" + activeAliens
                + ",wildlife=" + activeWildlife + "} sitePlans=" + urbanSitePlans + " sites=" + urbanSiteRecords
                + " vehiclePlans=" + urbanVehiclePlans + " threatBudgets=" + urbanThreatBudgets
                + " fortifications={damaged=" + damagedBarricades + ",braces=" + doorBraces + "}"
                + " refuges=" + refugeClaims + " tapped=" + tappedBuildings + " devices=" + utilityDevices + " urbanMissions=" + missionRecords
                + " visits={places=" + visitedPlaces + ",sketchedFloors=" + sketchedFloors + "}"
                + " fixtures={states=" + fixtureStates + ",residentDoors=" + residentDoors + "}";
    }

    public static String hardLimitSummary() {
        return "npc=" + SettlementManager.MAX_ACTIVE_NPCS
                + " infected=" + com.veylon.entity.UrbanEncounterDirector.MAX_ACTIVE
                + " encounterRecords=" + com.veylon.entity.UrbanEncounterDirector.MAX_RECORDS
                + " aliens=" + com.veylon.entity.UrbanEncounterDirector.MAX_ACTIVE_ALIENS
                + " wildlife=" + com.veylon.entity.UrbanEncounterDirector.MAX_ACTIVE_WILDLIFE
                + " ecologyPlans=" + UrbanWorldState.MAX_REGION_PLANS
                + " sitePlans=" + UrbanWorldState.MAX_REGION_PLANS
                + " vehiclePlans=" + UrbanWorldState.MAX_REGION_PLANS
                + " threatBudgets=" + UrbanWorldState.MAX_REGION_PLANS
                + " damagedBarricades=" + com.veylon.world.urban.UrbanFortifications.MAX_DAMAGED
                + " braces=" + com.veylon.world.urban.UrbanFortifications.MAX_BRACES
                + " refuges=" + com.veylon.world.urban.UrbanRefuges.MAX_CLAIMS
                + " tapped=" + com.veylon.world.urban.UrbanUtilities.MAX_TAPPED
                + " devices=" + com.veylon.world.urban.UrbanUtilities.MAX_DEVICES
                + " urbanMissions=" + com.veylon.world.urban.UrbanMissions.MAX_RECORDS
                + " visitedPlaces=" + (com.veylon.world.urban.UrbanVisits.MAX_DISTRICTS
                + com.veylon.world.urban.UrbanVisits.MAX_BUILDINGS)
                + " sketchedFloors=" + com.veylon.world.urban.UrbanVisits.MAX_SKETCHED_FLOORS
                + " sites=" + com.veylon.settlement.UrbanSiteDirector.MAX_SITES
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
                + " liquidFire=" + LiquidFireConstants.MAX_PATCHES
                + " particles=" + ParticleSystem.MAX
                + " chain=" + ExplosionSystem.MAX_CHAIN
                + " blockEdits=" + ExplosionSystem.MAX_BLOCKS
                + " anchoredRemains=" + BodyFragmentConstants.MAX_ANCHORED_REMAINS
                + " burnResidues=" + CombustionConstants.MAX_BURN_RESIDUES
                + " liveFragments=" + BodyFragmentConstants.MAX_LIVE_FRAGMENTS
                + " settledFragments=" + BodyFragmentConstants.MAX_SETTLED_FRAGMENTS
                + " ragdolls=" + RagdollConstants.MAX_LIVE
                + " urbanPlans=" + UrbanWorldState.MAX_REGION_PLANS
                + " buildingPlans=" + UrbanWorldState.MAX_BUILDING_PLANS
                + " routePlans=" + UrbanWorldState.MAX_ROUTE_PLANS
                + " fixtureStates=" + FixtureStates.MAX_ENTRIES
                + " residentDoors=" + FixtureStates.MAX_TRANSIT;
    }
}
