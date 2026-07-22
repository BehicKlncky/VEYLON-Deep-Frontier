package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class SettlementTheftTransactionTest {

    private Game game;
    private Settlement settlement;
    private Vec3i cratePosition;

    @BeforeEach
    void setUp() {
        game = new Game();
        game.newWorld(424242L, true);
        settlement = findNeutralSettlement(game);
        assertNotNull(settlement);
        cratePosition = settlement.center;
        game.world.surfaceHeight(cratePosition.x(), cratePosition.z());
        game.world.setBlock(cratePosition.x(), cratePosition.y(), cratePosition.z(),
                BlockType.CRATE, false);
        game.world.crateContents.put(cratePosition, new Inventory(12));
        game.openCratePos = cratePosition;
        game.openCrate = game.world.crateContents.get(cratePosition);
    }

    @Test
    void oneStolenStackUpdatesInventoryStockReputationAndAlert() {
        game.openCrate.set(0, new ItemStack(ItemType.BERRY, 3));
        settlement.foodStock = 10;
        int berriesBefore = game.player.inventory.count(ItemType.BERRY);

        assertEquals(3, game.transferCrateItemToPlayer(0));

        assertEquals(berriesBefore + 3, game.player.inventory.count(ItemType.BERRY));
        assertEquals(7, settlement.foodStock);
        assertEquals(-7f, settlement.localReputation, 0.001f);
        assertEquals(15f, settlement.alertLevel, 0.001f);
    }

    @Test
    void multipleItemCategoriesInOneEventUpdateAllStockButApplyOnePenalty() {
        settlement.foodStock = 10;
        settlement.medStock = 6;

        game.settlementManager.onCrateTheft(
                game, cratePosition, ItemType.BERRY, 2, 1001L, 1L);
        game.settlementManager.onCrateTheft(
                game, cratePosition, ItemType.MEDICINE, 3, 1001L, 2L);

        assertEquals(8, settlement.foodStock);
        assertEquals(3, settlement.medStock);
        assertEquals(-6f, settlement.localReputation, 0.001f);
        assertEquals(15f, settlement.alertLevel, 0.001f);
    }

    @Test
    void repeatedCallbackForSameLogicalTheftIsIdempotent() {
        settlement.foodStock = 10;

        game.onCrateItemTaken(ItemType.BERRY, 2, 2002L, 1L);
        game.onCrateItemTaken(ItemType.BERRY, 2, 2002L, 1L);

        assertEquals(8, settlement.foodStock);
        assertEquals(-6f, settlement.localReputation, 0.001f);
        assertEquals(15f, settlement.alertLevel, 0.001f);
    }

    @Test
    void distinctSameTypeStacksInOneEventAllUpdateStockButApplyOnePenalty() {
        settlement.foodStock = 10;

        game.onCrateItemTaken(ItemType.BERRY, 2, 6001L, 1L);
        game.onCrateItemTaken(ItemType.BERRY, 2, 6001L, 2L);

        assertEquals(6, settlement.foodStock);
        assertEquals(-6f, settlement.localReputation, 0.001f);
        assertEquals(15f, settlement.alertLevel, 0.001f);
    }

    @Test
    void separateTheftEventsReceiveSeparateConsequences() {
        settlement.foodStock = 10;

        game.onCrateItemTaken(ItemType.BERRY, 1, 3001L);
        game.onCrateItemTaken(ItemType.BERRY, 1, 3002L);

        assertEquals(8, settlement.foodStock);
        assertEquals(-10f, settlement.localReputation, 0.001f);
        assertEquals(30f, settlement.alertLevel, 0.001f);
    }

    @Test
    void hostileClearedAndOccupiedStoresRemainLawfulButStockAccurate() {
        settlement.foodStock = 12;
        settlement.alignment = Settlement.Alignment.HOSTILE;
        game.settlementManager.onCrateTheft(
                game, cratePosition, ItemType.BERRY, 2, 4001L);
        assertEquals(10, settlement.foodStock);
        assertEquals(0f, settlement.localReputation, 0.001f);

        settlement.alignment = Settlement.Alignment.NEUTRAL;
        settlement.cleared = true;
        game.settlementManager.onCrateTheft(
                game, cratePosition, ItemType.BERRY, 2, 4002L);
        assertEquals(8, settlement.foodStock);
        assertEquals(0f, settlement.localReputation, 0.001f);

        settlement.cleared = false;
        settlement.occupied = true;
        game.settlementManager.onCrateTheft(
                game, cratePosition, ItemType.BERRY, 2, 4003L);
        assertEquals(6, settlement.foodStock);
        assertEquals(0f, settlement.localReputation, 0.001f);
        assertEquals(0f, settlement.alertLevel, 0.001f);
    }

    @Test
    void friendlyOrHighReputationAccessAvoidsCrimePenalty() {
        settlement.foodStock = 10;
        settlement.alignment = Settlement.Alignment.FRIENDLY;
        game.settlementManager.onCrateTheft(
                game, cratePosition, ItemType.BERRY, 2, 5001L);
        assertEquals(8, settlement.foodStock);
        assertEquals(0f, settlement.localReputation, 0.001f);

        settlement.alignment = Settlement.Alignment.NEUTRAL;
        settlement.localReputation = 40f;
        game.settlementManager.onCrateTheft(
                game, cratePosition, ItemType.BERRY, 2, 5002L);
        assertEquals(6, settlement.foodStock);
        assertEquals(40f, settlement.localReputation, 0.001f);
        assertEquals(0f, settlement.alertLevel, 0.001f);
    }

    private static Settlement findNeutralSettlement(Game game) {
        for (int rx = -8; rx <= 8; rx++) {
            for (int rz = -8; rz <= 8; rz++) {
                Settlement candidate = game.world.settlementForRegion(rx, rz);
                if (candidate != null
                        && candidate.alignment == Settlement.Alignment.NEUTRAL) {
                    return candidate;
                }
            }
        }
        return null;
    }
}
