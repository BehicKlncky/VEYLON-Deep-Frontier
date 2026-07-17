package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Owns the bounded, persistent lifecycle of hostile attempts to retake player
 * outposts.  Live NPCs are a materialized view of a mission; the mission is
 * authoritative while the party is outside the active simulation radius.
 */
public final class CounterattackDirector {

    public static final int MAX_MISSIONS = 6;
    public static final int MAX_DORMANT_ATTACKERS = 24;
    public static final float MATERIALIZE_RADIUS = 118f;
    public static final float DEMATERIALIZE_RADIUS = 148f;
    public static final float TRAVEL_SPEED = 5.2f;
    public static final float APPROACH_RADIUS = 30f;
    public static final float ASSAULT_RADIUS = 8f;
    public static final float ACTIVE_ASSAULT_SECONDS = 75f;
    public static final float DORMANT_ASSAULT_SECONDS = 12f;

    /** Insertion order makes save output and cleanup deterministic. */
    public final Map<String, CounterattackMission> missions = new LinkedHashMap<>();

    public void reset() {
        missions.clear();
    }

    public CounterattackMission get(String id) {
        return id == null ? null : missions.get(id);
    }

    public CounterattackMission forTarget(long settlementId) {
        for (CounterattackMission mission : missions.values()) {
            if (mission.targetSettlementId == settlementId
                    && mission.phase != CounterattackMission.Phase.CLEANUP) {
                return mission;
            }
        }
        return null;
    }

    /**
     * Creates one mission for an occupied outpost.  The hostile origin is a
     * real matching settlement when one exists; otherwise a stable regional
     * force position is recorded explicitly and persisted with the mission.
     */
    public CounterattackMission dispatch(Game g, Settlement target, int requestedAttackers) {
        if (g == null || g.world == null || target == null || !target.occupied
                || missions.size() >= MAX_MISSIONS) {
            return null;
        }
        CounterattackMission existing = forTarget(target.id);
        if (existing != null) {
            return existing;
        }
        int dormant = missions.values().stream().mapToInt(m -> Math.max(0, m.survivors)).sum();
        int count = Math.min(SettlementManager.MAX_ACTIVE_COUNTERATTACKERS,
                Math.min(Math.max(0, requestedAttackers), MAX_DORMANT_ATTACKERS - dormant));
        if (count <= 0) {
            return null;
        }

        Settlement originSettlement = selectOrigin(g, target);
        long salt = mix64(g.world.seed ^ target.id ^ target.dormantStep
                ^ Float.floatToIntBits(target.counterattackTimer));
        Vec3i origin;
        long originId;
        if (originSettlement != null) {
            g.world.layoutFor(originSettlement);
            origin = originSettlement.gates.isEmpty()
                    ? originSettlement.center : originSettlement.gates.getFirst();
            originId = originSettlement.id;
        } else {
            // The regional force is not a fabricated settlement: its exact
            // staging coordinate becomes explicit mission state.
            double angle = ((salt >>> 11) * 0x1.0p-53) * Math.PI * 2.0;
            int range = 180 + (int) Math.floorMod(salt >>> 32, 81);
            origin = new Vec3i(target.center.x() + (int) Math.round(Math.cos(angle) * range),
                    target.center.y(),
                    target.center.z() + (int) Math.round(Math.sin(angle) * range));
            originId = CounterattackMission.REGIONAL_FORCE_ORIGIN;
        }
        g.world.layoutFor(target);
        Vec3i destination = target.gates.isEmpty() ? target.center : target.gates.getFirst();
        String id = "counterattack:" + Long.toUnsignedString(target.id, 16) + ":"
                + Long.toUnsignedString(salt, 16);
        int collision = 0;
        while (missions.containsKey(id)) {
            id = "counterattack:" + Long.toUnsignedString(target.id, 16) + ":"
                    + Long.toUnsignedString(mix64(salt + ++collision), 16);
        }
        CounterattackMission mission = new CounterattackMission(id, originId, target.id,
                target.founderFaction, origin, destination, mix64(salt ^ 0x434f554e544552L), count);
        mission.phaseTimer = 0.5f;
        missions.put(id, mission);
        g.faction.bindMission(com.veylon.ai.Quest.Type.DEFEND_OUTPOST, mission.id,
                target.id, mission.attackerFactionId);
        return mission;
    }

    private Settlement selectOrigin(Game g, Settlement target) {
        Settlement best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Settlement candidate : g.world.settlements.values()) {
            if (candidate == target || candidate.cleared || !candidate.hostile()) {
                continue;
            }
            boolean matchingForce = target.founderFaction.equals(candidate.factionId)
                    || target.founderFaction.equals(candidate.founderFaction);
            if (!matchingForce) {
                continue;
            }
            double distance = candidate.distSqTo(target.center.x(), target.center.z());
            if (distance < bestDistance) {
                best = candidate;
                bestDistance = distance;
            }
        }
        return best;
    }

    /** Advances every mission with bounded work and removes completed state. */
    public void tick(Game g, float dt) {
        if (missions.isEmpty() || g.player == null || dt <= 0) {
            return;
        }
        float step = Math.min(dt, 2f);
        Iterator<CounterattackMission> iterator = missions.values().iterator();
        int processed = 0;
        while (iterator.hasNext() && processed++ < MAX_MISSIONS) {
            CounterattackMission mission = iterator.next();
            Settlement target = g.world.settlements.get(mission.targetSettlementId);
            if (target == null) {
                removeMembers(g, mission.id);
                iterator.remove();
                continue;
            }
            switch (mission.phase) {
                case DISPATCH -> tickDispatch(g, mission, step);
                case OUTBOUND, APPROACH -> tickTravel(g, mission, target, step);
                case ASSAULT -> tickAssault(g, mission, target, step);
                case RETREAT -> tickRetreat(g, mission, step);
                case CLEANUP -> {
                    removeMembers(g, mission.id);
                    iterator.remove();
                }
            }
        }
    }

    private void tickDispatch(Game g, CounterattackMission mission, float dt) {
        mission.phaseTimer -= dt;
        if (mission.phaseTimer > 0) {
            return;
        }
        mission.phase = CounterattackMission.Phase.OUTBOUND;
        mission.phaseTimer = 0;
        g.log("=== " + HumanFaction.displayName(mission.attackerFactionId)
                + " forces are marching on an occupied outpost. ===");
    }

    private void tickTravel(Game g, CounterattackMission mission, Settlement target, float dt) {
        List<Npc> members = members(g, mission.id);
        if (!members.isEmpty()) {
            syncCoarsePosition(mission, members);
            if (distanceSqToPlayer(g, mission) > DEMATERIALIZE_RADIUS * DEMATERIALIZE_RADIUS) {
                dematerialize(g, mission, members);
                members = List.of();
            }
        }
        if (members.isEmpty()) {
            advance(mission, mission.target, TRAVEL_SPEED * dt);
        }

        double remaining = distance(mission.x, mission.z,
                mission.target.x() + 0.5f, mission.target.z() + 0.5f);
        mission.phase = remaining <= APPROACH_RADIUS
                ? CounterattackMission.Phase.APPROACH : CounterattackMission.Phase.OUTBOUND;
        if (remaining <= ASSAULT_RADIUS) {
            mission.x = mission.target.x() + 0.5f;
            mission.y = mission.target.y() + 0.4f;
            mission.z = mission.target.z() + 0.5f;
            mission.phase = CounterattackMission.Phase.ASSAULT;
            mission.phaseTimer = isPlayerNearTarget(g, target)
                    ? ACTIVE_ASSAULT_SECONDS : DORMANT_ASSAULT_SECONDS;
            for (Npc member : members) {
                member.partyMission = Npc.PartyMission.SEARCHING;
                member.partyMissionTimer = mission.phaseTimer;
            }
            g.log(HumanFaction.displayName(mission.attackerFactionId)
                    + " attackers have reached the outpost!");
            if (isPlayerNearTarget(g, target)) {
                g.audio.playAlarmBell(target.center.x(), target.center.y(), target.center.z());
            }
            return;
        }
        materializeIfNear(g, mission);
    }

    private void tickAssault(Game g, CounterattackMission mission, Settlement target, float dt) {
        if (!target.occupied && !mission.outcomeApplied) {
            // An external explicit ownership transition already resolved this
            // objective; never resurrect or silently overwrite it.
            mission.phase = CounterattackMission.Phase.CLEANUP;
            return;
        }
        boolean active = isPlayerNearTarget(g, target);
        if (active) {
            materializeIfNear(g, mission);
        } else {
            List<Npc> live = members(g, mission.id);
            if (!live.isEmpty()) {
                dematerialize(g, mission, live);
            }
        }
        int defenders = defenderCount(target);
        if (mission.survivors <= 0) {
            resolve(g, mission, target, CounterattackMission.Outcome.DEFENDER_VICTORY);
            return;
        }
        if (defenders <= 0) {
            resolve(g, mission, target, CounterattackMission.Outcome.ATTACKER_VICTORY);
            return;
        }
        mission.phaseTimer -= dt;
        if (mission.phaseTimer > 0) {
            return;
        }
        CounterattackMission.Outcome outcome = active
                ? resolveFromLiveCombat(g, mission, target)
                : dormantOutcome(mission, defenders);
        resolve(g, mission, target, outcome);
    }

    private CounterattackMission.Outcome resolveFromLiveCombat(Game g,
                                                                CounterattackMission mission,
                                                                Settlement target) {
        float attackerHealth = 0;
        for (Npc member : members(g, mission.id)) {
            attackerHealth += Math.max(0, member.health) / Math.max(1f, member.maxHealth);
        }
        float defenderHealth = 0;
        for (Settlement.Resident resident : target.residents) {
            if (isOutpostDefender(resident)) {
                defenderHealth += resident.live == null
                        ? Math.max(0, resident.health) / Math.max(1f, resident.archetype.maxHealth)
                        : Math.max(0, resident.live.health) / Math.max(1f, resident.live.maxHealth);
            }
        }
        return attackerHealth > defenderHealth
                ? CounterattackMission.Outcome.ATTACKER_VICTORY
                : CounterattackMission.Outcome.DEFENDER_VICTORY;
    }

    /** Stable outcome: it depends only on persisted mission state and defenders. */
    private CounterattackMission.Outcome dormantOutcome(CounterattackMission mission,
                                                         int defenders) {
        Random deterministic = new Random(mission.resolutionSeed);
        int attackScore = mission.survivors * 10 + deterministic.nextInt(18);
        int defenseScore = defenders * 12 + deterministic.nextInt(18);
        return attackScore > defenseScore
                ? CounterattackMission.Outcome.ATTACKER_VICTORY
                : CounterattackMission.Outcome.DEFENDER_VICTORY;
    }

    private void resolve(Game g, CounterattackMission mission, Settlement target,
                         CounterattackMission.Outcome outcome) {
        if (mission.resolved()) {
            return;
        }
        mission.outcome = outcome;
        applyOutcome(g, mission, target);
        mission.phase = CounterattackMission.Phase.RETREAT;
        mission.phaseTimer = outcome == CounterattackMission.Outcome.DEFENDER_VICTORY ? 120f : 2f;
        for (Npc member : members(g, mission.id)) {
            member.partyMission = Npc.PartyMission.RETURNING;
            member.partyDestination.set(mission.origin.x() + 0.5f,
                    mission.origin.y() + 0.4f, mission.origin.z() + 0.5f);
            member.partyMissionTimer = mission.phaseTimer;
        }
    }

    private void applyOutcome(Game g, CounterattackMission mission, Settlement target) {
        if (mission.outcomeApplied) {
            return;
        }
        // Guard before mutating ownership/rewards; a save at any following line
        // cannot replay the transition after loading.
        mission.outcomeApplied = true;
        if (mission.outcome == CounterattackMission.Outcome.DEFENDER_VICTORY) {
            g.log("Your outpost garrison repelled the "
                    + HumanFaction.displayName(mission.attackerFactionId) + " counterattack.");
            g.settlementManager.addReputation(g, HumanFaction.FRONTIER, 8, null);
            g.settlementManager.addLocalReputation(g, target, 10,
                    "You defended the occupied outpost");
            g.faction.onOutpostDefended(g, target, mission.id);
            return;
        }

        // Explicit attacker victory: retire the frontier population and replace
        // it once with a fresh hostile garrison.
        g.entities.npcs.removeIf(n -> n.settled() && n.settlementId == target.id);
        for (Settlement.Resident resident : target.residents) {
            resident.live = null;
        }
        target.residents.clear();
        target.occupied = false;
        target.cleared = false;
        target.alignment = Settlement.Alignment.HOSTILE;
        target.factionId = mission.attackerFactionId;
        target.commandNeutralized = false;
        target.centralObjectiveControlled = false;
        target.alarmNeutralized = target.alarmBell == null
                || g.world.getBlock(target.alarmBell.x(), target.alarmBell.y(), target.alarmBell.z())
                != com.veylon.world.BlockType.ALARM_BELL;
        target.morale = 65;
        SettlementPlanner.seedInitialState(g.world.seed ^ mission.resolutionSeed, target);
        g.log("Grim news: " + HumanFaction.displayName(mission.attackerFactionId)
                + " forces retook the " + target.type.displayName.toLowerCase() + ".");
    }

    private void tickRetreat(Game g, CounterattackMission mission, float dt) {
        mission.phaseTimer -= dt;
        if (mission.outcome == CounterattackMission.Outcome.ATTACKER_VICTORY) {
            if (mission.phaseTimer <= 0) {
                mission.phase = CounterattackMission.Phase.CLEANUP;
            }
            return;
        }
        List<Npc> members = members(g, mission.id);
        if (!members.isEmpty()) {
            syncCoarsePosition(mission, members);
            if (distanceSqToPlayer(g, mission) > DEMATERIALIZE_RADIUS * DEMATERIALIZE_RADIUS) {
                dematerialize(g, mission, members);
                members = List.of();
            }
        }
        if (members.isEmpty()) {
            advance(mission, mission.origin, TRAVEL_SPEED * dt);
        }
        double remaining = distance(mission.x, mission.z,
                mission.origin.x() + 0.5f, mission.origin.z() + 0.5f);
        if (remaining <= ASSAULT_RADIUS || mission.phaseTimer <= 0) {
            mission.phase = CounterattackMission.Phase.CLEANUP;
        }
    }

    /** Called once by entity cleanup for a genuinely dead mission member. */
    public void onMemberDied(Npc member) {
        if (member == null || member.partyKind != Npc.PartyKind.COUNTERATTACK) {
            return;
        }
        CounterattackMission mission = get(member.partyMissionId);
        if (mission != null && mission.phase != CounterattackMission.Phase.CLEANUP) {
            mission.survivors = Math.max(0, mission.survivors - 1);
        }
    }

    /** Reconciles bounded live views after all optional save sections are read. */
    public void reconcileLoaded(Game g) {
        // Unknown/orphan group members are never allowed to become immortal
        // hostile entities after a partial or old save.
        g.entities.npcs.removeIf(n -> n.partyKind == Npc.PartyKind.COUNTERATTACK
                && (n.partyMissionId == null || !missions.containsKey(n.partyMissionId)));
        for (CounterattackMission mission : new ArrayList<>(missions.values())) {
            Settlement target = g.world.settlements.get(mission.targetSettlementId);
            if (target == null || mission.survivors < 0
                    || mission.survivors > SettlementManager.MAX_ACTIVE_COUNTERATTACKERS) {
                removeMembers(g, mission.id);
                missions.remove(mission.id);
                continue;
            }
            List<Npc> live = members(g, mission.id);
            if (mission.phase == CounterattackMission.Phase.CLEANUP) {
                removeMembers(g, mission.id);
                continue;
            }
            if (!live.isEmpty() && live.size() < mission.survivors) {
                // onWorldLoaded may have trimmed an over-cap active view. A
                // trimmed entity cannot remain as a hidden extra survivor.
                mission.survivors = live.size();
            }
            if (!live.isEmpty()) {
                syncCoarsePosition(mission, live);
            }
        }
    }

    private void materializeIfNear(Game g, CounterattackMission mission) {
        if (!members(g, mission.id).isEmpty()
                || distanceSqToPlayer(g, mission) > MATERIALIZE_RADIUS * MATERIALIZE_RADIUS
                || mission.survivors <= 0) {
            return;
        }
        int capacity = g.settlementManager.availableNpcCapacity(g,
                SettlementManager.NpcCategory.COUNTERATTACK);
        if (capacity <= 0) {
            return;
        }
        int wanted = Math.min(mission.survivors, capacity);
        float sx = mission.x;
        float sz = mission.z;
        double pdx = sx - g.player.pos.x;
        double pdz = sz - g.player.pos.z;
        double playerDistance = Math.sqrt(pdx * pdx + pdz * pdz);
        if (playerDistance < 18) {
            // Spawn on the approach vector, never on top of the player.
            double len = Math.max(0.01, distance(mission.x, mission.z,
                    mission.target.x() + 0.5f, mission.target.z() + 0.5f));
            sx = (float) (g.player.pos.x
                    + (mission.x - mission.target.x() - 0.5f) / len * 20.0);
            sz = (float) (g.player.pos.z
                    + (mission.z - mission.target.z() - 0.5f) / len * 20.0);
            mission.x = sx;
            mission.z = sz;
        }
        Settlement origin = g.world.settlements.get(mission.originSettlementId);
        if (origin == null) {
            // spawnParty needs only the stable gate/center metadata.  A small
            // synthetic descriptor is deliberately avoided: use the target as
            // a carrier, then overwrite the true persisted origin below.
            origin = g.world.settlements.get(mission.targetSettlementId);
        }
        if (origin == null) {
            return;
        }
        Vec3i spawnCell = new Vec3i((int) Math.floor(sx), (int) Math.floor(mission.y),
                (int) Math.floor(sz));
        List<Npc> spawned = g.settlementManager.spawnParty(g, origin, wanted,
                mission.attackerFactionId, mission.target, Npc.PartyKind.COUNTERATTACK,
                mission.id, mission.targetSettlementId, spawnCell);
        if (spawned.size() < mission.survivors) {
            // Admission limits explicitly clamp the active party.  Dropped
            // members are not retained as invisible attackers.
            mission.survivors = spawned.size();
        }
        for (Npc member : spawned) {
            member.originSettlementId = mission.originSettlementId;
            member.partyDestination.set(mission.target.x() + 0.5f,
                    mission.target.y() + 0.4f, mission.target.z() + 0.5f);
            member.partyMission = mission.phase == CounterattackMission.Phase.ASSAULT
                    ? Npc.PartyMission.SEARCHING : Npc.PartyMission.OUTBOUND;
        }
    }

    private void dematerialize(Game g, CounterattackMission mission, List<Npc> members) {
        syncCoarsePosition(mission, members);
        g.entities.npcs.removeIf(n -> mission.id.equals(n.partyMissionId));
    }

    private void removeMembers(Game g, String missionId) {
        g.entities.npcs.removeIf(n -> missionId.equals(n.partyMissionId));
    }

    public List<Npc> members(Game g, String missionId) {
        List<Npc> found = new ArrayList<>();
        for (Npc npc : g.entities.npcs) {
            if (!npc.dead && npc.partyKind == Npc.PartyKind.COUNTERATTACK
                    && missionId.equals(npc.partyMissionId)) {
                found.add(npc);
            }
        }
        return found;
    }

    private void syncCoarsePosition(CounterattackMission mission, List<Npc> members) {
        if (members.isEmpty()) {
            return;
        }
        float x = 0, y = 0, z = 0;
        for (Npc member : members) {
            x += member.pos.x;
            y += member.pos.y;
            z += member.pos.z;
        }
        mission.x = x / members.size();
        mission.y = y / members.size();
        mission.z = z / members.size();
    }

    private static void advance(CounterattackMission mission, Vec3i destination, float distance) {
        float tx = destination.x() + 0.5f;
        float ty = destination.y() + 0.4f;
        float tz = destination.z() + 0.5f;
        float dx = tx - mission.x;
        float dy = ty - mission.y;
        float dz = tz - mission.z;
        float horizontal = (float) Math.sqrt(dx * dx + dz * dz);
        if (horizontal <= 0.001f) {
            mission.x = tx;
            mission.y = ty;
            mission.z = tz;
            return;
        }
        float step = Math.min(horizontal, Math.max(0, distance));
        mission.x += dx / horizontal * step;
        mission.z += dz / horizontal * step;
        mission.y += dy * (step / horizontal);
    }

    private static int defenderCount(Settlement target) {
        int defenders = 0;
        for (Settlement.Resident resident : target.residents) {
            if (isOutpostDefender(resident)) {
                defenders++;
            }
        }
        return defenders;
    }

    private static boolean isOutpostDefender(Settlement.Resident resident) {
        return resident.alive && !resident.rescued && !resident.routed && !resident.surrendered
                && resident.archetype != NpcArchetype.CAPTIVE && resident.archetype != NpcArchetype.TRADER;
    }

    private static boolean isPlayerNearTarget(Game g, Settlement target) {
        return target.distSqTo(g.player.pos.x, g.player.pos.z)
                <= DEMATERIALIZE_RADIUS * DEMATERIALIZE_RADIUS;
    }

    private static double distanceSqToPlayer(Game g, CounterattackMission mission) {
        double dx = mission.x - g.player.pos.x;
        double dz = mission.z - g.player.pos.z;
        return dx * dx + dz * dz;
    }

    private static double distance(double ax, double az, double bx, double bz) {
        double dx = bx - ax;
        double dz = bz - az;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
