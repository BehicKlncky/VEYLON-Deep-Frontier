package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.BlockItemForms;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.RackBatch;
import com.veylon.world.Raycaster;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** R19-R21 exercise the production mining, placement, cleanup and pick commands. */
class CreativeBuildingTest {
    private static final Vec3i POS = new Vec3i(312, 40, 310);
    /** The reach {@code Game.updateActions} casts each native frame. */
    private static final double NATIVE_REACH = 5.2;
    private Game game;

    @BeforeEach
    void setUp() { game = CreativeTestArena.create(GameMode.CREATIVE); }

    @Test
    void pressBreaksOreWithoutAToolAndHeldInputRepeatsAfterPointThreeSeconds() {
        target(BlockType.IRON_ORE, POS);
        game.mine(0.05f);
        assertEquals(BlockType.AIR, block(), "R19: the first press breaks even tool-only ore");
        target(BlockType.IRON_ORE, POS);
        for (int i = 0; i < 5; i++) game.mine(0.05f);
        assertEquals(BlockType.IRON_ORE, block(), "R19: a held action waits the complete repeat interval");
        game.mine(0.05f);
        assertEquals(BlockType.AIR, block(), "R19: the sixth 50ms step repeats the break");
        assertEquals(0, game.player.inventory.count(ItemType.IRON_ORE), "R19: ore awards no drops");
        assertEquals(0, game.noise.count(), "R11: Creative building produces no perception event");
        assertEquals(0, game.player.noise, "R11: Creative breaking produces no body noise");
    }

    @Test
    void releaseTargetChangeAndModeSwitchResetTheRepeatTimer() {
        target(BlockType.DIRT, POS);
        game.mine(0.01f);
        game.blockActions.reset();
        target(BlockType.DIRT, POS);
        game.mine(0.01f);
        assertEquals(BlockType.AIR, block(), "R19: a new press has no inherited delay");
        Vec3i next = new Vec3i(313, 40, 310);
        target(BlockType.LOG, next);
        for (int i = 0; i < 5; i++) game.mine(0.05f);
        assertEquals(BlockType.LOG, game.world.getBlock(next.x(), next.y(), next.z()),
                "R19: a held button that reaches another block restarts the repeat interval");
        game.mine(0.05f);
        assertEquals(BlockType.AIR, game.world.getBlock(next.x(), next.y(), next.z()),
                "R19: the new target breaks when the restarted interval completes");
        assertTrue(game.blockActions.creativeBreakRemaining() > 0, "precondition: timer is active");
        game.switchGameMode(GameMode.SURVIVAL);
        assertEquals(0, game.blockActions.creativeBreakRemaining(), "R19: switching ends Creative action state");
    }

    @Test
    void heldButtonDigsRetargetedBlocksOncePerRepeatIntervalInsteadOfEveryFrame() {
        // Mirror the native frame: recast the target, then route the held primary button.
        game.camera.pitch = 89.5f;
        Raycaster.MutableHit buffer = new Raycaster.MutableHit();
        List<Integer> breakFrames = new ArrayList<>();
        for (int frame = 0; frame < 13; frame++) {
            game.targetHit = Raycaster.castInto(game.world, game.camera.position, game.camera.front(),
                    NATIVE_REACH, false, buffer) ? buffer : null;
            assertNotNull(game.targetHit, "precondition: the dug column stays within reach");
            int before = solidColumnBelowPlayer();
            game.combat.updatePrimaryAction(0.05f, game.camera.front(), frame == 0);
            if (solidColumnBelowPlayer() < before) breakFrames.add(frame);
        }
        assertEquals(List.of(0, 6, 12), breakFrames,
                "R19: the block behind each break waits 0.30s instead of breaking on the next frame");
    }

    @Test
    void indestructibleBlocksStayAndBerryFiberAndToolWearAreSuppressed() {
        target(BlockType.BEACON_LIT, POS);
        game.mine(1f);
        assertFalse(game.completePlayerBlockBreak(POS), "R19: the completion seam also protects indestructible blocks");
        assertEquals(BlockType.BEACON_LIT, block());
        ItemStack axe = new ItemStack(ItemType.STONE_AXE, 1);
        game.player.inventory.set(0, axe);
        float durability = axe.durability;
        for (BlockType type : new BlockType[]{BlockType.LOG, BlockType.BERRY_BUSH, BlockType.LEAVES}) {
            target(type, POS);
            assertTrue(game.completePlayerBlockBreak(POS));
        }
        assertEquals(durability, axe.durability, "R19: successful breaks never wear the held tool");
        for (int i = 1; i < game.player.inventory.size(); i++) {
            assertNull(game.player.inventory.get(i), "R19: neither ordinary drops nor berry fiber appear");
        }
    }

    @Test
    void breakingContainersSpillsTheirContentsAndClearsAllBlockState() {
        target(BlockType.CRATE, POS);
        Inventory crate = new Inventory(12);
        crate.add(ItemType.COOKED_MEAT, 3);
        game.world.crateContents.put(POS, crate);
        assertTrue(game.completePlayerBlockBreak(POS));
        assertEquals(3, game.player.inventory.count(ItemType.COOKED_MEAT), "R19: contents are transferred");
        assertFalse(game.world.crateContents.containsKey(POS));
        assertEquals(0, game.player.inventory.count(ItemType.CRATE), "R19: the container itself is not a drop");
        for (boolean done : new boolean[]{false, true}) {
            target(BlockType.DRYING_RACK, POS);
            RackBatch batch = new RackBatch(ItemType.RAW_MEAT, 2);
            if (done) batch.progress = batch.required();
            game.world.rackBatches.put(POS, batch);
            assertTrue(game.completePlayerBlockBreak(POS));
            assertFalse(game.world.rackBatches.containsKey(POS));
        }
        assertEquals(2, game.player.inventory.count(ItemType.RAW_MEAT));
        assertEquals(2, game.player.inventory.count(ItemType.DRIED_MEAT));
        target(BlockType.CAMPFIRE, POS);
        game.world.campfireFuel.put(POS, 100f);
        game.completePlayerBlockBreak(POS);
        assertFalse(game.world.campfireFuel.containsKey(POS));
        target(BlockType.RAIN_COLLECTOR, POS);
        game.world.collectorWater.put(POS, 2f);
        game.completePlayerBlockBreak(POS);
        assertFalse(game.world.collectorWater.containsKey(POS));
        target(BlockType.LANTERN, POS);
        game.world.lanternState(POS);
        game.completePlayerBlockBreak(POS);
        assertFalse(game.world.lanterns.containsKey(POS));
        target(BlockType.BEACON, POS);
        game.world.beaconPos = POS;
        game.world.beaconStage = 2;
        game.completePlayerBlockBreak(POS);
        assertNull(game.world.beaconPos);
        assertEquals(-1, game.world.beaconStage);
        target(BlockType.POWDER_KEG, POS);
        game.world.kegFuses.put(POS, 2f);
        game.completePlayerBlockBreak(POS);
        assertFalse(game.world.kegFuses.containsKey(POS));
    }

    @Test
    void repeatedPlacementKeepsTheStackAndAllCollisionChecks() {
        game.player.inventory.set(0, new ItemStack(ItemType.PLANK, 2));
        for (int x = 312; x < 318; x++) assertTrue(game.placeSelectedBlockAt(x, 40, 310));
        assertEquals(2, game.player.selected().count, "R20: repeated placement does not shrink the stack");
        assertFalse(game.placeSelectedBlockAt(312, 40, 310), "R20: occupied blocks still reject placement");
        assertFalse(game.placeSelectedBlockAt(310, 40, 310), "R20: player collision still rejects placement");
        game.player.inventory.set(0, new ItemStack(ItemType.CRATE, 1));
        assertTrue(game.placeSelectedBlockAt(318, 40, 310));
        assertNotNull(game.world.crateContents.get(new Vec3i(318, 40, 310)), "R20: normal initialization remains");
    }

    @Test
    void pickSelectsExistingThenEmptyThenReplacesSelectedHotbarOnly() {
        target(BlockType.BEACON, POS);
        assertEquals(ItemType.BEACON_FRAME, BlockItemForms.of(BlockType.BEACON));
        ItemStack existing = new ItemStack(ItemType.BEACON_FRAME, 1);
        game.player.inventory.set(5, existing);
        assertTrue(game.pickBlock());
        assertEquals(5, game.player.hotbarSel);
        assertSame(existing, game.player.selected(), "R21: existing stacks are selected without replacing condition");
        target(BlockType.PLANK, POS);
        assertTrue(game.pickBlock());
        assertEquals(0, game.player.hotbarSel, "R21: pick prefers the first empty hotbar slot");
        assertEquals(ItemType.PLANK.maxStack, game.player.selected().count);
        for (int i = 0; i < 9; i++) game.player.inventory.set(i, new ItemStack(ItemType.BERRY, 1));
        game.player.hotbarSel = 4;
        assertTrue(game.pickBlock());
        assertEquals(4, game.player.hotbarSel);
        assertEquals(ItemType.PLANK, game.player.selected().type);
        assertNull(game.player.inventory.get(9), "R21: pick never spills into non-hotbar inventory");
    }

    @Test
    void pickWithoutAFormReportsAndSurvivalPickDoesNothing() {
        target(BlockType.BEACON_LIT, POS);
        assertFalse(game.pickBlock());
        assertTrue(game.eventLog.recent(1).getFirst().contains("no item form"));
        game.switchGameMode(GameMode.SURVIVAL);
        target(BlockType.PLANK, POS);
        assertFalse(game.pickBlock(), "R21: Survival cannot grant items through pick");
        assertNull(game.player.selected());
        for (ItemType item : ItemType.values()) {
            if (item.places() != null) assertSame(item, BlockItemForms.of(item.places()), "R21: forms follow places()");
        }
    }

    private BlockType block() { return game.world.getBlock(POS.x(), POS.y(), POS.z()); }

    private int solidColumnBelowPlayer() {
        int solid = 0;
        for (int y = 30; y < 40; y++) {
            if (game.world.getBlock(310, y, 310) != BlockType.AIR) solid++;
        }
        return solid;
    }

    private void target(BlockType type, Vec3i pos) {
        game.world.setBlock(pos.x(), pos.y(), pos.z(), type, false);
        game.targetHit = new Raycaster.Hit(pos.x(), pos.y(), pos.z(), -1, 0, 0, 2, type);
    }
}
