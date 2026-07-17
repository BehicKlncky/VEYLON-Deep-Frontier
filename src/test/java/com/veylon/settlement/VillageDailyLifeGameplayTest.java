package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.ai.Pathfinder;
import com.veylon.entity.Npc;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Acceptance scenario 3 through generated world content and the production
 * activation/AI/save paths. The test never moves a resident or preloads an AI
 * timer: residents have to traverse their village geometry themselves.
 */
class VillageDailyLifeGameplayTest {

    private static final long SEED = 20260716L;
    private static final float DT = 0.05f;

    private record Identity(String name, String archetypeId, int bedIndex,
                            int dutyIndex, float health) {
    }

    @Test
    void generatedFriendlyVillageResidentsWorkTradePatrolSleepAndPersist(
            @TempDir Path dir) {
        Game game = new Game();
        game.newWorld(SEED, true);
        Settlement village = generatedFriendlyVillageWithDailyRoles(game);
        assertNotNull(village,
                "the deterministic search must find a friendly generated village"
                        + " with trader, worker and guard roles");

        game.world.layoutFor(village);
        game.world.ensureChunks(village.center.x(), village.center.z(), 4, 10_000);
        assertFalse(village.beds.isEmpty(), "generated homes provide real beds");
        assertFalse(village.dutyPoints.isEmpty(), "generated buildings provide duty points");
        assertTrue(village.patrolPoints.size() >= 4,
                "a generated village has a real patrol circuit");

        List<Identity> identities = new ArrayList<>();
        for (Settlement.Resident resident : village.residents) {
            identities.add(identityOf(resident));
            assertFalse(resident.name.isBlank(), "every resident has a persistent identity");
            assertTrue(resident.bedIndex >= 0, resident.name + " has an assigned home");
            assertTrue(resident.dutyIndex >= 0, resident.name + " has an assigned duty");
            Vec3i bed = village.beds.get(Math.floorMod(resident.bedIndex,
                    village.beds.size()));
            Vec3i sleep = assignedHome(game, village, resident);
            assertFalse(sleep.equals(bed),
                    resident.name + " sleeps beside/on top of, never inside, the bed block");
            assertTrue(Pathfinder.standable(game.world, sleep.x(), sleep.y(), sleep.z()),
                    resident.name + " has a standable sleeping position");
        }

        Settlement.Resident traderRecord = resident(village, NpcArchetype.TRADER);
        Settlement.Resident guardRecord = resident(village, NpcArchetype.GUARD);
        Settlement.Resident workerRecord = village.residents.stream()
                .filter(r -> r.archetype == NpcArchetype.FARMER
                        || r.archetype == NpcArchetype.SMITH
                        || r.archetype == NpcArchetype.VILLAGER)
                .findFirst().orElseThrow();

        // Enter at midday. Activation naturally materializes residents at their
        // assigned homes, after which their production AI must find every route.
        game.time.totalMinutes = 12 * 60;
        game.player.pos.set(village.center.x() + 0.5f, village.center.y() + 1f,
                village.center.z() + 0.5f);
        game.settlementManager.slowTick(game, 1f);
        assertTrue(village.residents.stream().allMatch(r -> r.live != null),
                "the whole village fits the resident budget and activates");

        Npc trader = traderRecord.live;
        Npc guard = guardRecord.live;
        Npc worker = workerRecord.live;
        Vector3f guardStart = new Vector3f(guard.pos);
        int guardStartWaypoint = guard.patrolIndex;
        int foodBefore = village.foodStock;
        int woodBefore = village.woodStock;
        int metalBefore = village.metalStock;
        Vec3i traderDuty = assignedDuty(village, traderRecord);
        Vec3i workerDuty = assignedDuty(village, workerRecord);

        boolean traderAtStall = false;
        boolean workerAtDuty = false;
        boolean patrolAdvanced = false;
        boolean workProduced = false;
        for (int tick = 0; tick < 4_800
                && !(traderAtStall && workerAtDuty && patrolAdvanced && workProduced);
             tick++) {
            tickResidents(game);
            traderAtStall |= trader.state == Npc.NpcState.TRADE
                    && flatDistanceSq(trader, traderDuty) <= 3.5f * 3.5f;
            workerAtDuty |= flatDistanceSq(worker, workerDuty) <= 3.5f * 3.5f;
            patrolAdvanced |= guard.patrolIndex != guardStartWaypoint
                    && flatDistanceSq(guard, guardStart) > 4f * 4f;
            workProduced |= village.foodStock > foodBefore
                    || village.woodStock > woodBefore
                    || village.metalStock > metalBefore;
            assertResidentBounds(village);
        }
        assertTrue(traderAtStall,
                "the generated trader reaches an assigned duty point in TRADE state");
        assertTrue(workerAtDuty,
                "a generated civilian reaches the workplace assigned by its record");
        assertTrue(workProduced,
                "ordinary elapsed work time changes the village's finite stocks");
        assertTrue(patrolAdvanced,
                "a generated guard traverses far enough to advance the patrol circuit");

        // Night begins while residents are spread across work and patrol sites.
        // Every resident must find its own bed through the generated structures.
        double farthestFromHome = village.residents.stream()
                .mapToDouble(r -> flatDistanceSq(r.live, assignedHome(game, village, r)))
                .max().orElse(0);
        assertTrue(farthestFromHome > 4f * 4f,
                "daytime behavior genuinely moved residents away from home");
        game.time.totalMinutes = 22 * 60;

        boolean everyoneHome = false;
        for (int tick = 0; tick < 4_800 && !everyoneHome; tick++) {
            tickResidents(game);
            everyoneHome = village.residents.stream().allMatch(r ->
                    r.live != null
                            && r.live.state == Npc.NpcState.SLEEP
                            && flatDistanceSq(r.live, assignedHome(game, village, r))
                            <= 1.6f * 1.6f);
            assertResidentBounds(village);
        }
        assertTrue(everyoneHome,
                "all generated residents return to their assigned homes at night");
        for (Settlement.Resident resident : village.residents) {
            Npc npc = resident.live;
            assertFalse(game.world.getBlock((int) Math.floor(npc.pos.x),
                            (int) Math.floor(npc.pos.y), (int) Math.floor(npc.pos.z)).opaque,
                    npc.name + " cannot finish inside opaque structure geometry");
            assertFalse(game.world.getBlock((int) Math.floor(npc.pos.x),
                            (int) Math.floor(npc.pos.y + 1), (int) Math.floor(npc.pos.z)).opaque,
                    npc.name + " retains head clearance at home");
        }

        // Save while the village is active and asleep. Both the resident record
        // and its live binding must retain identity, role, home and duty.
        Path save = dir.resolve("village-daily-life.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        Settlement restored = loaded.world.settlements.get(village.id);
        assertNotNull(restored);
        loaded.world.layoutFor(restored);
        assertEquals(identities.size(), restored.residents.size());
        for (int i = 0; i < identities.size(); i++) {
            Identity expected = identities.get(i);
            Settlement.Resident actual = restored.residents.get(i);
            assertEquals(expected, identityOf(actual));
            assertNotNull(actual.live, actual.name + " remains bound to one active entity");
            assertEquals(actual.name, actual.live.name);
            assertEquals(i, actual.live.residentIndex);
            assertEquals(restored.id, actual.live.settlementId);
        }

        // Normal departure writes live health/state back and releases every
        // entity. Returning activates the same named residents, not replacements.
        loaded.player.pos.set(restored.center.x() + 500f, restored.center.y() + 1f,
                restored.center.z() + 500f);
        loaded.settlementManager.slowTick(loaded, 10f);
        assertTrue(restored.residents.stream().allMatch(r -> r.live == null));
        loaded.player.pos.set(restored.center.x() + 0.5f, restored.center.y() + 1f,
                restored.center.z() + 0.5f);
        loaded.settlementManager.slowTick(loaded, 10f);
        for (int i = 0; i < identities.size(); i++) {
            assertNotNull(restored.residents.get(i).live);
            assertEquals(identities.get(i).name(), restored.residents.get(i).live.name);
        }
    }

    private static Settlement generatedFriendlyVillageWithDailyRoles(Game game) {
        for (int ring = 0; ring <= 12; ring++) {
            for (int rx = -ring; rx <= ring; rx++) {
                for (int rz = -ring; rz <= ring; rz++) {
                    if (Math.max(Math.abs(rx), Math.abs(rz)) != ring) {
                        continue;
                    }
                    Settlement settlement = game.world.settlementForRegion(rx, rz);
                    if (settlement == null || settlement.type != SettlementType.VILLAGE
                            || !settlement.friendly()) {
                        continue;
                    }
                    boolean trader = settlement.residents.stream()
                            .anyMatch(r -> r.archetype == NpcArchetype.TRADER);
                    boolean guard = settlement.residents.stream()
                            .anyMatch(r -> r.archetype == NpcArchetype.GUARD);
                    boolean worker = settlement.residents.stream().anyMatch(r ->
                            r.archetype == NpcArchetype.FARMER
                                    || r.archetype == NpcArchetype.SMITH
                                    || r.archetype == NpcArchetype.VILLAGER);
                    if (trader && guard && worker) {
                        return settlement;
                    }
                }
            }
        }
        return null;
    }

    private static Settlement.Resident resident(Settlement settlement,
                                                NpcArchetype archetype) {
        return settlement.residents.stream()
                .filter(r -> r.archetype == archetype)
                .findFirst().orElseThrow();
    }

    private static Identity identityOf(Settlement.Resident resident) {
        return new Identity(resident.name, resident.archetype.id, resident.bedIndex,
                resident.dutyIndex, resident.health);
    }

    private static Vec3i assignedHome(Game game, Settlement settlement,
                                      Settlement.Resident resident) {
        return game.settlementManager.residentSleepPosition(game, settlement, resident);
    }

    private static Vec3i assignedDuty(Settlement settlement,
                                      Settlement.Resident resident) {
        return settlement.dutyPoints.get(Math.floorMod(resident.dutyIndex,
                settlement.dutyPoints.size()));
    }

    private static double flatDistanceSq(Npc npc, Vec3i point) {
        return npc.distSqTo(point.x() + 0.5f, npc.pos.y, point.z() + 0.5f);
    }

    private static double flatDistanceSq(Npc npc, Vector3f point) {
        return npc.distSqTo(point.x, npc.pos.y, point.z);
    }

    private static void tickResidents(Game game) {
        game.entities.fastTick(game, DT);
        game.settlementManager.fastTick(game, DT);
        game.projectiles.update(game, DT);
        game.noise.update(DT);
    }

    private static void assertResidentBounds(Settlement settlement) {
        for (Settlement.Resident resident : settlement.residents) {
            Npc npc = resident.live;
            assertNotNull(npc, resident.name + " unexpectedly deactivated");
            assertTrue(Float.isFinite(npc.pos.x) && Float.isFinite(npc.pos.y)
                            && Float.isFinite(npc.pos.z) && npc.pos.y > 0,
                    resident.name + " retains a finite world position");
            assertTrue(npc.path == null || npc.path.size() <= Pathfinder.MAX_PATH_NODES,
                    resident.name + " retains only a bounded cached path");
        }
    }

}
