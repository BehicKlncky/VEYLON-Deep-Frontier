package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Gameplay-facing coverage for every regional reputation action category. */
class SettlementReputationGameplayTest {

    @Test
    void dialogueTradeGiftAndMedicineCreditOnlyTheResidentsHomeSettlement() {
        Game game = game(6101L);
        Settlement home = settlement(game, -20, -20, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Settlement unrelated = settlement(game, -19, -20, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Npc resident = resident(game, home, "Mira", NpcArchetype.TRADER);
        resident.isTrader = true;
        float campTrust = game.faction.trust;

        game.player.inventory.clear();
        game.player.inventory.add(ItemType.BERRY, 2);
        assertTrue(game.npcScreen.performTrade(game, resident,
                ItemType.BERRY, 2, ItemType.PLANK, 1));
        assertEquals(3f, home.localReputation, 0.001f);
        assertEquals(2, home.foodStock);

        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        game.player.inventory.set(0, new ItemStack(ItemType.COOKED_MEAT, 1));
        assertTrue(game.npcScreen.performGift(game, resident));
        assertEquals(11f, home.localReputation, 0.001f);
        assertEquals(3, home.foodStock);

        resident.sick = true;
        home.residents.getFirst().sick = true;
        game.player.inventory.set(0, new ItemStack(ItemType.MEDICINE, 1));
        assertTrue(game.npcScreen.performGift(game, resident));
        assertFalse(resident.sick);
        assertFalse(home.residents.getFirst().sick,
                "the persisted resident record is cured with the live entity");
        assertEquals(23f, home.localReputation, 0.001f);
        assertEquals(0f, unrelated.localReputation, 0.001f);
        assertEquals(campTrust, game.faction.trust, 0.001f,
                "regional help must not reward the unrelated starter camp");
    }

    @Test
    void patrolDefenseAndPredatorKillCreditOnlyTheirExplicitCommunities() {
        Game game = game(6102L);
        Settlement defended = settlement(game, -18, -18, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Settlement other = settlement(game, -17, -18, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        float campTrust = game.faction.trust;

        Npc attacker = game.entities.spawnNpc(game.world, "Marked patrol",
                defended.center.x() + 1f, defended.center.y(), defended.center.z());
        attacker.archetype = NpcArchetype.TRACKER;
        attacker.warParty = true;
        attacker.partyKind = Npc.PartyKind.PATROL;
        attacker.partyMissionId = "patrol:defense-test";
        attacker.partyMemberId = "patrol:defense-test:0";
        attacker.partyFactionId = HumanFaction.HEADHUNTERS;
        attacker.originSettlementId = 12345L;
        attacker.partyTargetSettlementId = defended.id;
        attacker.health = 1f;
        game.player.pos.set(attacker.pos.x - 1f, attacker.pos.y, attacker.pos.z);
        game.player.inventory.clear();
        game.player.inventory.set(0, new ItemStack(ItemType.STONE_AXE, 1));
        assertTrue(game.performPlayerAttack(attacker));
        game.entities.fastTick(game, 0.05f);

        assertEquals(8f, defended.localReputation, 0.001f);
        assertEquals(0f, other.localReputation, 0.001f);
        assertEquals(campTrust, game.faction.trust, 0.001f,
                "killing a hostile war party is not an attack on the starter camp");

        Creature wolf = game.entities.spawnCreature(game.world, Creature.CreatureType.WOLF,
                other.center.x() + 1f, other.center.y(), other.center.z());
        wolf.health = 1f;
        game.player.pos.set(wolf.pos.x - 1f, wolf.pos.y, wolf.pos.z);
        game.advancePlayerAttackCooldown(1f);
        assertTrue(game.performPlayerAttack(wolf));
        game.entities.fastTick(game, 0.05f);
        assertEquals(6f, other.localReputation, 0.001f);
        assertEquals(8f, defended.localReputation, 0.001f,
                "the remote settlement does not receive nearby-threat credit");
    }

    @Test
    void crateUiSeparatesRestrictedOpeningTheftReturnedSuppliesAndContainerBreaking() {
        Game game = game(6103L);
        Settlement settlement = settlement(game, -16, -16, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Vec3i cratePos = crate(game, settlement, 1, 0);
        Inventory crate = game.world.crateContents.get(cratePos);
        crate.set(0, new ItemStack(ItemType.BERRY, 2));
        settlement.foodStock = 5;
        game.player.pos.set(cratePos.x() + 1.5f, cratePos.y(), cratePos.z() + 0.5f);

        assertTrue(game.openCrateAt(cratePos));
        assertEquals(-4f, settlement.localReputation, 0.001f,
                "opening restricted stores has its own consequence");
        game.closeScreens();
        assertTrue(game.openCrateAt(cratePos));
        assertEquals(-4f, settlement.localReputation, 0.001f,
                "reopening during the throttle cannot duplicate the open penalty");

        assertEquals(2, game.transferCrateItemToPlayer(0));
        assertEquals(-10f, settlement.localReputation, 0.001f);
        assertEquals(3, settlement.foodStock,
                "taking food reduces the correct settlement ledger");

        game.player.inventory.clear();
        game.player.inventory.set(0, new ItemStack(ItemType.MEDICINE, 3));
        assertEquals(3, game.transferPlayerItemToCrate(0));
        assertEquals(3, settlement.medStock);
        assertEquals(-6f, settlement.localReputation, 0.001f,
                "returning supplies is a distinct positive local action");

        game.closeScreens();
        game.player.inventory.clear();
        game.player.inventory.set(0, new ItemStack(ItemType.STONE_AXE, 1));
        float beforeBreak = settlement.localReputation;
        assertTrue(game.completePlayerBlockBreak(cratePos));
        assertEquals(beforeBreak - 13f, settlement.localReputation, 0.001f,
                "three stored items produce one container-breaking consequence");
    }

    @Test
    void meleeAttackAndKillPenalizeOnlyTheVictimsHome() {
        Game game = game(6104L);
        Settlement victimHome = settlement(game, -14, -14, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Settlement unrelated = settlement(game, -13, -14, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Npc victim = resident(game, victimHome, "Tovan", NpcArchetype.VILLAGER);
        victim.health = 1f;
        victimHome.residents.getFirst().health = 1f;
        game.player.pos.set(victim.pos.x - 1f, victim.pos.y, victim.pos.z);
        game.player.inventory.clear();
        game.player.inventory.set(0, new ItemStack(ItemType.STONE_AXE, 1));
        float campTrust = game.faction.trust;

        assertTrue(game.performPlayerAttack(victim));
        assertEquals(-18f, victimHome.localReputation, 0.001f,
                "the attack hook runs through normal melee");
        game.entities.fastTick(game, 0.05f);
        assertEquals(-53f, victimHome.localReputation, 0.001f,
                "the death has one additional kill consequence");
        assertEquals(Settlement.Alignment.HOSTILE, victimHome.alignment);
        assertEquals(0f, unrelated.localReputation, 0.001f);
        assertEquals(campTrust, game.faction.trust, 0.001f);
    }

    @Test
    void occupiedOutpostDamageAndExplosionChainsApplySinglePropertyAndEntityConsequences() {
        Game game = game(6105L);
        Settlement outpost = settlement(game, -12, -12, Settlement.Alignment.FRIENDLY,
                HumanFaction.FRONTIER);
        outpost.cleared = true;
        outpost.occupied = true;
        Vec3i wall = new Vec3i(outpost.center.x() + 2, outpost.center.y(), outpost.center.z());
        game.world.setBlock(wall.x(), wall.y(), wall.z(), BlockType.WALL, true);
        game.player.pos.set(wall.x() + 1.5f, wall.y(), wall.z() + 0.5f);
        game.player.inventory.clear();
        game.player.inventory.set(0, new ItemStack(ItemType.STONE_AXE, 1));
        assertTrue(game.completePlayerBlockBreak(wall));
        assertEquals(-8f, outpost.localReputation, 0.001f,
                "occupied structures still belong to their resident community");

        Game blastGame = game(6106L);
        Settlement blastHome = settlement(blastGame, -10, -10, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Settlement unrelated = settlement(blastGame, -9, -10, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Npc resident = resident(blastGame, blastHome, "Blast witness", NpcArchetype.GUARD);
        resident.maxHealth = 1000f;
        resident.health = 1000f;
        Vec3i kegA = new Vec3i(blastHome.center.x() + 1, blastHome.center.y(), blastHome.center.z());
        Vec3i kegB = kegA.offset(1, 0, 0);
        blastGame.world.setBlock(kegA.x(), kegA.y(), kegA.z(), BlockType.POWDER_KEG, true);
        blastGame.world.setBlock(kegB.x(), kegB.y(), kegB.z(), BlockType.POWDER_KEG, true);
        blastGame.player.pos.set(blastHome.center.x() + 50, blastHome.center.y(),
                blastHome.center.z() + 50);
        blastGame.explosions.explode(blastGame, blastHome.center.x() + 0.5f,
                blastHome.center.y() + 0.5f, blastHome.center.z() + 0.5f,
                3.8f, 30f, 0f, true);
        assertEquals(-18f, blastHome.localReputation, 0.001f,
                "one resident hit by the initial and chained blasts is attributed once");
        assertEquals(0f, unrelated.localReputation, 0.001f);

        resident.pos.x += 80f;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                blastGame.world.setBlock(blastHome.center.x() + dx, blastHome.center.y(),
                        blastHome.center.z() + dz, BlockType.PLANK, true);
            }
        }
        float beforeProperty = blastHome.localReputation;
        blastGame.explosions.explode(blastGame, blastHome.center.x() + 0.5f,
                blastHome.center.y() + 0.5f, blastHome.center.z() + 0.5f,
                3.8f, 0f, 0f, true);
        float propertyPenalty = beforeProperty - blastHome.localReputation;
        assertTrue(propertyPenalty >= 8f && propertyPenalty <= 24f,
                "many destroyed blocks produce one bounded property consequence");
    }

    @Test
    void playerLitKegChainKeepsPlayerAttributionAndPenalizesEachResidentOnlyOnce() {
        Game game = game(6109L);
        Settlement home = settlement(game, -6, -6, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Npc resident = resident(game, home, "Chain witness", NpcArchetype.GUARD);
        resident.maxHealth = 1_000f;
        resident.health = 1_000f;

        Vec3i first = prepareKegCell(game, home.center, 0);
        Vec3i second = prepareKegCell(game, home.center, 1);
        resident.pos.set(second.x() + 2.2f, second.y(), second.z() + 0.5f);
        game.player.pos.set(first.x() - 3f, first.y(), first.z() + 0.5f);
        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        game.player.inventory.set(0, new ItemStack(ItemType.POWDER_KEG, 2));

        assertTrue(game.placeSelectedBlockAt(first.x(), first.y(), first.z()));
        assertTrue(game.placeSelectedBlockAt(second.x(), second.y(), second.z()));
        assertTrue(game.interactWithBlockAt(first),
                "the same F-key gameplay command lights the first fuse");
        assertEquals(Boolean.TRUE, game.world.kegFusePlayerAttribution.get(first));

        game.explosions.tickFuses(game, 5.1f);

        assertEquals(BlockType.AIR,
                game.world.getBlock(first.x(), first.y(), first.z()));
        assertEquals(BlockType.AIR,
                game.world.getBlock(second.x(), second.y(), second.z()),
                "the adjacent keg inherits the player source through the real chain");
        assertTrue(resident.health < resident.maxHealth,
                "both chain stages resolve against the live resident");
        assertEquals(-18f, home.localReputation, 0.001f,
                "one resident hit by the initial and chained blasts is penalized once");
        assertTrue(game.world.kegFuses.isEmpty());
        assertTrue(game.world.kegFusePlayerAttribution.isEmpty());
    }

    @Test
    void environmentalCampfireArmedKegDoesNotInventPlayerReputationDamage() {
        Game game = game(6110L);
        Settlement home = settlement(game, -5, -5, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Npc resident = resident(game, home, "Fire witness", NpcArchetype.GUARD);
        resident.maxHealth = 1_000f;
        resident.health = 1_000f;

        Vec3i keg = prepareKegCell(game, home.center, 0);
        Vec3i campfire = keg.offset(-1, 0, 0);
        game.world.setBlock(campfire.x(), campfire.y() - 1, campfire.z(),
                BlockType.STONE, false);
        game.world.setBlock(campfire.x(), campfire.y(), campfire.z(),
                BlockType.CAMPFIRE, false);
        game.world.campfireFuel.put(campfire, 60f);
        game.player.pos.set(keg.x() - 5f, keg.y(), keg.z() + 0.5f);
        resident.pos.set(keg.x() + 2.5f, keg.y(), keg.z() + 0.5f);
        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        game.player.inventory.set(0, new ItemStack(ItemType.POWDER_KEG, 1));
        assertTrue(game.placeSelectedBlockAt(keg.x(), keg.y(), keg.z()));

        game.fire.mediumTick(game, 0.1f);
        assertEquals(1.5f, game.world.kegFuses.get(keg), 0.001f);
        assertEquals(Boolean.FALSE, game.world.kegFusePlayerAttribution.get(keg),
                "an existing open flame is an environmental source");
        game.explosions.tickFuses(game, 1.6f);

        assertTrue(resident.health < resident.maxHealth);
        assertEquals(0f, home.localReputation, 0.001f,
                "environmental damage is never charged to the player");
        assertTrue(game.world.kegFuses.isEmpty());
        assertTrue(game.world.kegFusePlayerAttribution.isEmpty());
    }

    @Test
    void depositingSuppliesInEnemyStoresChangesFactionStandingNotAnUnrelatedSettlement() {
        Game game = game(6107L);
        Settlement hostile = settlement(game, -8, -8, Settlement.Alignment.HOSTILE,
                HumanFaction.HEADHUNTERS);
        Settlement unrelated = settlement(game, -7, -8, Settlement.Alignment.NEUTRAL,
                HumanFaction.FREE_SETTLERS);
        Vec3i cratePos = crate(game, hostile, 1, 0);
        game.player.pos.set(cratePos.x() + 1f, cratePos.y(), cratePos.z());
        game.player.inventory.clear();
        game.player.inventory.set(0, new ItemStack(ItemType.LOG, 4));
        float campTrust = game.faction.trust;

        assertTrue(game.openCrateAt(cratePos));
        assertEquals(4, game.transferPlayerItemToCrate(0));
        assertEquals(4, hostile.woodStock);
        assertTrue(game.world.factionReputation.getOrDefault(HumanFaction.FRONTIER, 0f) < 0f);
        assertTrue(game.world.factionReputation.getOrDefault(HumanFaction.HEADHUNTERS, 0f) > 0f);
        assertEquals(0f, unrelated.localReputation, 0.001f);
        assertEquals(campTrust, game.faction.trust, 0.001f);
    }

    @Test
    void restrictedTrespassIsLocationAwareThrottledAndPersistsAcrossSaveLoad(
            @TempDir Path tempDir) {
        Game game = game(6108L);
        Settlement settlement = findNeutralSettlement(game);
        assertNotNull(settlement, "seed should provide a deterministic neutral settlement");
        Vec3i restrictedCrate = game.world.layoutFor(settlement).crates.keySet().stream()
                .findFirst().orElseThrow();
        game.world.surfaceHeight(restrictedCrate.x(), restrictedCrate.z());
        game.world.setBlock(restrictedCrate.x(), restrictedCrate.y(), restrictedCrate.z(),
                BlockType.CRATE, true);
        game.world.crateContents.putIfAbsent(restrictedCrate, new Inventory(12));
        game.player.pos.set(restrictedCrate.x() + 0.5f, restrictedCrate.y(),
                restrictedCrate.z() + 0.5f);

        float before = settlement.localReputation;
        assertTrue(game.openCrateAt(restrictedCrate));
        game.settlementManager.fastTick(game, 0.05f);
        assertEquals(before - 9f, settlement.localReputation, 0.001f,
                "restricted storage open and restricted-area entry are distinct once-only actions");
        for (int i = 0; i < 100; i++) {
            game.settlementManager.fastTick(game, 0.05f);
        }
        assertEquals(before - 9f, settlement.localReputation, 0.001f,
                "standing in the area is not penalized every frame");

        Vec3i safe = safePoint(game, settlement);
        game.player.pos.set(safe.x() + 0.5f, safe.y(), safe.z() + 0.5f);
        game.settlementManager.fastTick(game, SettlementManager.TRESPASS_INTERVAL + 1f);
        assertEquals(before - 9f, settlement.localReputation, 0.001f,
                "ordinary settlement space is not a restricted area");
        game.player.pos.set(restrictedCrate.x() + 0.5f, restrictedCrate.y(),
                restrictedCrate.z() + 0.5f);
        game.settlementManager.fastTick(game, 0.05f);
        assertEquals(before - 14f, settlement.localReputation, 0.001f);

        // Refresh both persisted throttles without changing the already-tested standing.
        settlement.restrictedStorageCooldown = SettlementManager.RESTRICTED_STORAGE_INTERVAL;
        Path save = tempDir.resolve("reputation-actions.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        Settlement restored = loaded.world.settlements.get(settlement.id);
        assertNotNull(restored);
        assertEquals(settlement.localReputation, restored.localReputation, 0.001f);
        assertTrue(restored.trespassCooldown > 0f);
        assertEquals(SettlementManager.RESTRICTED_STORAGE_INTERVAL,
                restored.restrictedStorageCooldown, 0.001f);
        float restoredRep = restored.localReputation;
        loaded.world.surfaceHeight(restrictedCrate.x(), restrictedCrate.z());
        assertTrue(loaded.openCrateAt(restrictedCrate));
        loaded.settlementManager.fastTick(loaded, 0.05f);
        assertEquals(restoredRep, restored.localReputation, 0.001f,
                "load cannot duplicate storage or trespass consequences");
    }

    private static Game game(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        return game;
    }

    private static Settlement settlement(Game game, int rx, int rz,
                                         Settlement.Alignment alignment, String faction) {
        int x = rx * SettlementPlanner.REGION_BLOCKS + 120;
        int z = rz * SettlementPlanner.REGION_BLOCKS + 120;
        int y = game.world.surfaceHeight(x, z) + 1;
        Settlement settlement = new Settlement(Settlement.packId(rx, rz), rx, rz,
                SettlementType.VILLAGE, new Vec3i(x, y, z), faction, alignment);
        settlement.factionId = faction;
        game.world.settlements.put(settlement.id, settlement);
        return settlement;
    }

    private static Npc resident(Game game, Settlement home, String name,
                                NpcArchetype archetype) {
        Settlement.Resident record = new Settlement.Resident(name, archetype);
        home.residents.add(record);
        Npc npc = game.entities.spawnNpc(game.world, name,
                home.center.x() + 0.5f, home.center.y(), home.center.z() + 0.5f);
        npc.archetype = archetype;
        npc.settlementId = home.id;
        npc.residentIndex = home.residents.size() - 1;
        record.live = npc;
        return npc;
    }

    private static Vec3i crate(Game game, Settlement settlement, int dx, int dz) {
        int x = settlement.center.x() + dx;
        int z = settlement.center.z() + dz;
        Vec3i pos = new Vec3i(x, game.world.surfaceHeight(x, z) + 1, z);
        game.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.CRATE, true);
        game.world.crateContents.put(pos, new Inventory(12));
        return pos;
    }

    private static Vec3i prepareKegCell(Game game, Vec3i center, int dx) {
        Vec3i pos = center.offset(dx, 0, 0);
        game.world.setBlock(pos.x(), pos.y() - 1, pos.z(), BlockType.STONE, false);
        game.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.AIR, false);
        game.world.setBlock(pos.x(), pos.y() + 1, pos.z(), BlockType.AIR, false);
        return pos;
    }

    private static Settlement findNeutralSettlement(Game game) {
        for (int rx = -8; rx <= 8; rx++) {
            for (int rz = -8; rz <= 8; rz++) {
                Settlement settlement = game.world.settlementForRegion(rx, rz);
                if (settlement != null && settlement.alignment == Settlement.Alignment.NEUTRAL) {
                    return settlement;
                }
            }
        }
        return null;
    }

    private static Vec3i safePoint(Game game, Settlement settlement) {
        for (int dx = -settlement.radius + 1; dx < settlement.radius; dx++) {
            for (int dz = -settlement.radius + 1; dz < settlement.radius; dz++) {
                float x = settlement.center.x() + dx + 0.5f;
                float z = settlement.center.z() + dz + 0.5f;
                if (!game.settlementManager.isRestrictedArea(game, settlement, x, z)) {
                    int bx = (int) Math.floor(x), bz = (int) Math.floor(z);
                    return new Vec3i(bx, game.world.surfaceHeight(bx, bz) + 1, bz);
                }
            }
        }
        throw new AssertionError("settlement has no unrestricted public space");
    }
}
