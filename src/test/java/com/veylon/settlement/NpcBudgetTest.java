package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NpcBudgetTest {

    @Test
    void partyLargerThanRemainingCombinedBudgetIsClampedPerSpawn() {
        Game g = game(73001L);
        Settlement origin = hostileOrigin(g, 2400, -2400);
        addResidents(g, 24);
        addLegacyNpcs(g, 6);
        int loadedBefore = g.world.loadedCount();

        List<Npc> party = g.settlementManager.spawnParty(g, origin, 20,
                HumanFaction.HEADHUNTERS, origin.center.offset(80, 0, 0),
                Npc.PartyKind.PATROL, "patrol-budget", 0L);

        assertEquals(2, party.size(), "only the two non-mission slots are admitted");
        assertEquals(SettlementManager.MAX_ACTIVE_NPCS
                        - SettlementManager.COUNTERATTACK_SLOT_RESERVE,
                g.settlementManager.npcCounts(g).total());
        assertEquals(loadedBefore, g.world.loadedCount(),
                "dispatch at an unloaded settlement must not generate its chunks");
        assertTrue(party.stream().allMatch(n -> n.abstractTravel));
    }

    @Test
    void simultaneousPartyKindsShareGlobalAndCategoryLimits() {
        Game g = game(73002L);
        Settlement origin = hostileOrigin(g, -2600, 2100);
        addResidents(g, 20);
        addLegacyNpcs(g, 4);
        Vec3i destination = origin.center.offset(120, 0, -20);

        assertEquals(4, g.settlementManager.spawnParty(g, origin, 4,
                HumanFaction.HEADHUNTERS, destination, Npc.PartyKind.COUNTERATTACK,
                "counter-a", origin.id).size());
        assertEquals(5, g.settlementManager.spawnParty(g, origin, 5,
                HumanFaction.HEADHUNTERS, destination, Npc.PartyKind.PATROL,
                "patrol-a", 0L).size());
        assertEquals(3, g.settlementManager.spawnParty(g, origin, 5,
                HumanFaction.HEADHUNTERS, destination, Npc.PartyKind.PATROL,
                "patrol-b", 0L).size());
        assertTrue(g.settlementManager.spawnParty(g, origin, 20,
                HumanFaction.HEADHUNTERS, destination, Npc.PartyKind.BOUNTY_HUNTER,
                "bounty-a", 0L).isEmpty(),
                "the shared war-party ceiling applies across categories");

        SettlementManager.NpcCounts counts = g.settlementManager.npcCounts(g);
        assertEquals(36, counts.total());
        assertEquals(20, counts.residents());
        assertEquals(12, counts.warParties());
        assertEquals(8, counts.patrols());
        assertEquals(0, counts.bountyHunters());
        assertEquals(4, counts.counterattackers());
        assertEquals(4, counts.legacy());
        assertFalse(g.settlementManager.canSpawnNpc(g,
                SettlementManager.NpcCategory.LEGACY));
    }

    @Test
    void occupiedOutpostAssaultCanMaterializeInsideReservedGlobalSlots() {
        Game g = game(73005L);
        addResidents(g, SettlementManager.MAX_ACTIVE_RESIDENTS);
        Settlement origin = hostileOrigin(g, -180, 0);
        Settlement target = new Settlement(Settlement.packId(22, 0), 22, 0,
                SettlementType.FORT, new Vec3i(180, 40, 0), HumanFaction.HEADHUNTERS,
                Settlement.Alignment.FRIENDLY);
        target.factionId = HumanFaction.FRONTIER;
        target.occupied = true;
        target.cleared = true;
        target.residents.add(new Settlement.Resident("Outpost guard", NpcArchetype.GUARD));
        g.world.settlements.put(target.id, target);

        var mission = g.settlementManager.dispatchCounterattack(g, target,
                SettlementManager.MAX_ACTIVE_COUNTERATTACKERS);
        assertNotNull(mission);
        g.player.pos.set(mission.x, mission.y, mission.z);
        g.settlementManager.fastTick(g, 0.6f);
        g.settlementManager.fastTick(g, 0.1f);

        SettlementManager.NpcCounts counts = g.settlementManager.npcCounts(g);
        assertEquals(SettlementManager.MAX_ACTIVE_NPCS, counts.total());
        assertEquals(SettlementManager.MAX_ACTIVE_RESIDENTS, counts.residents());
        assertEquals(SettlementManager.MAX_ACTIVE_COUNTERATTACKERS,
                counts.counterattackers());
        assertEquals(mission.survivors,
                g.settlementManager.counterattacks.members(g, mission.id).size(),
                "a nearby defense objective gets real combatants, not a zero-actor timeout");
    }

    @Test
    void distantPartyLifecycleStaysBoundedWithoutChunksAndCleanupReturnsCapacity() {
        Game g = game(73003L);
        Settlement origin = hostileOrigin(g, 3000, 3000);
        int loadedBefore = g.world.loadedCount();
        Vec3i destination = origin.center.offset(35, 0, 0);
        List<Npc> party = g.settlementManager.spawnParty(g, origin, 100,
                HumanFaction.HEADHUNTERS, destination, Npc.PartyKind.PATROL,
                "bounded-long-run", 0L);
        assertEquals(SettlementManager.MAX_ACTIVE_PATROL_NPCS, party.size());

        int peak = 0;
        for (int tick = 0; tick < 4_000; tick++) {
            g.entities.fastTick(g, 0.05f);
            SettlementManager.NpcCounts counts = g.settlementManager.npcCounts(g);
            peak = Math.max(peak, counts.total());
            assertTrue(counts.total() <= SettlementManager.MAX_ACTIVE_NPCS);
            assertTrue(counts.warParties() <= SettlementManager.MAX_ACTIVE_WAR_PARTY_NPCS);
        }

        assertEquals(party.size(), peak);
        assertEquals(0, g.settlementManager.npcCounts(g).warParties(),
                "return/report retires every party entity from the collection");
        assertEquals(loadedBefore, g.world.loadedCount(),
                "abstract outbound, search and return never require loaded chunks");
        assertEquals(1, g.settlementManager.spawnParty(g, origin, 1,
                HumanFaction.HEADHUNTERS, destination, Npc.PartyKind.PATROL,
                "capacity-reused", 0L).size(),
                "completed-party cleanup releases the slot for a later dispatch");
    }

    @Test
    void saveLoadRebindsOneResidentAndDoesNotDuplicateActiveOrDormantNpcs(@TempDir Path dir) {
        Game g = game(73004L);
        Settlement origin = findHostileSettlement(g);
        assertNotNull(origin);
        Settlement.Resident resident = origin.residents.getFirst();
        Npc live = g.entities.spawnNpc(g.world, resident.name,
                origin.center.x() + 0.5f, origin.center.y() + 0.4f, origin.center.z() + 0.5f);
        live.archetype = resident.archetype;
        live.settlementId = origin.id;
        live.residentIndex = origin.residents.indexOf(resident);
        resident.live = live;
        g.settlementManager.spawnParty(g, origin, 3, HumanFaction.HEADHUNTERS,
                origin.center.offset(50, 0, 0), Npc.PartyKind.PATROL,
                "saved-patrol", 0L);
        SettlementManager.NpcCounts before = g.settlementManager.npcCounts(g);

        Path save = dir.resolve("npc-budget.sav");
        assertTrue(SaveSystem.save(g, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        Settlement restored = loaded.world.settlements.get(origin.id);
        assertNotNull(restored);
        assertNotNull(restored.residents.getFirst().live);
        assertEquals(before.total(), loaded.settlementManager.npcCounts(loaded).total());
        assertEquals(1, loaded.entities.npcs.stream()
                .filter(n -> n.settled() && n.settlementId == origin.id && n.residentIndex == 0)
                .count());

        Npc rebound = restored.residents.getFirst().live;
        loaded.settlementManager.onWorldLoaded(loaded);
        assertEquals(before.total(), loaded.settlementManager.npcCounts(loaded).total());
        assertEquals(1, loaded.entities.npcs.stream()
                .filter(n -> n.settled() && n.settlementId == origin.id && n.residentIndex == 0)
                .count());
        assertSame(rebound, restored.residents.getFirst().live,
                "re-binding is idempotent and cannot materialize a second resident entity");
    }

    private static Game game(long seed) {
        Game g = new Game();
        g.newWorld(seed, true);
        g.entities.npcs.clear();
        return g;
    }

    private static Settlement hostileOrigin(Game g, int x, int z) {
        int rx = SettlementPlanner.regionOfBlock(x);
        int rz = SettlementPlanner.regionOfBlock(z);
        int y = g.world.generator.heightAt(x, z) + 1;
        Settlement s = new Settlement(Settlement.packId(rx, rz), rx, rz,
                SettlementType.FORT, new Vec3i(x, y, z), HumanFaction.HEADHUNTERS,
                Settlement.Alignment.HOSTILE);
        g.world.settlements.put(s.id, s);
        return s;
    }

    private static Settlement findHostileSettlement(Game g) {
        for (int rx = -8; rx <= 8; rx++) {
            for (int rz = -8; rz <= 8; rz++) {
                Settlement settlement = g.world.settlementForRegion(rx, rz);
                if (settlement != null && settlement.hostile() && !settlement.residents.isEmpty()) {
                    return settlement;
                }
            }
        }
        return null;
    }

    private static void addResidents(Game g, int count) {
        long settlementId = Settlement.packId(900, 900);
        for (int i = 0; i < count; i++) {
            Npc npc = g.entities.spawnNpc(g.world, "Resident " + i, i, 40, 0);
            npc.archetype = NpcArchetype.VILLAGER;
            npc.settlementId = settlementId;
            npc.residentIndex = i;
        }
    }

    private static void addLegacyNpcs(Game g, int count) {
        for (int i = 0; i < count; i++) {
            g.entities.spawnNpc(g.world, "Legacy " + i, i, 40, 4);
        }
    }
}
