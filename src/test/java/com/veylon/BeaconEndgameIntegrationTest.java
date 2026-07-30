package com.veylon;

import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.util.AppPaths;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The distress beacon: the only way to win, and the one sequence a player
 * reaches exactly once.
 *
 * <p>Every stage is driven through the same gameplay commands the F key uses —
 * {@code placeSelectedBlockAt} and {@code interactWithBlockAt} — so the test
 * exercises the production rules rather than assigning {@code world.beaconStage}
 * directly. What it pins down is that each stage refuses to advance until its
 * cost is genuinely payable, that paying it consumes exactly what it should, and
 * that the final calibration flips the game into {@link Game.AppState#VICTORY}.
 */
class BeaconEndgameIntegrationTest {

    private static final long SEED = 20_260_729L;

    /** Mirrors WorldInteractions.BEACON_COPPER_REQUIRED. */
    private static final int COPPER_REQUIRED = 3;
    /** Mirrors WorldInteractions.BEACON_ALLY_TRUST. */
    private static final int ALLY_TRUST = 75;

    @Test
    void theThreeStageRepairEnforcesEveryCostAndEndsTheGame() {
        Game game = new Game();
        game.newWorld(SEED, true);
        Vec3i beacon = placeBeaconFrame(game);

        assertEquals(BlockType.BEACON, blockAt(game, beacon),
                "placing the frame builds the unlit beacon");
        assertEquals(beacon, game.world.beaconPos, "the world tracks the beacon it must light");
        assertEquals(0, game.world.beaconStage, "a fresh frame starts at stage 0");
        assertNotEquals(Game.AppState.VICTORY, game.appState);

        // ---- Stage 0: signal crystal ----
        game.player.inventory.clear();
        assertTrue(game.interactWithBlockAt(beacon), "the beacon always consumes the interaction");
        assertEquals(0, game.world.beaconStage,
                "without a signal crystal the beacon stays at stage 0");

        game.player.inventory.add(ItemType.SIGNAL_CRYSTAL, 2);
        assertTrue(game.interactWithBlockAt(beacon));
        assertEquals(1, game.world.beaconStage, "the crystal installs and advances to stage 1");
        assertEquals(1, game.player.inventory.count(ItemType.SIGNAL_CRYSTAL),
                "installing takes exactly one crystal");

        // ---- Stage 1: copper wiring ----
        game.player.inventory.add(ItemType.COPPER_INGOT, COPPER_REQUIRED - 1);
        assertTrue(game.interactWithBlockAt(beacon));
        assertEquals(1, game.world.beaconStage,
                "a partial pile of copper does not wire the array");
        assertEquals(COPPER_REQUIRED - 1, game.player.inventory.count(ItemType.COPPER_INGOT),
                "a refused wiring attempt consumes no copper");

        game.player.inventory.add(ItemType.COPPER_INGOT, 2); // one spare beyond the requirement
        assertTrue(game.interactWithBlockAt(beacon));
        assertEquals(2, game.world.beaconStage, "the full copper cost advances to stage 2");
        assertEquals(1, game.player.inventory.count(ItemType.COPPER_INGOT),
                "wiring takes exactly " + COPPER_REQUIRED + " ingots and leaves the spare");

        // ---- Stage 2: calibration codes, gated on Allied trust ----
        game.faction.trust = ALLY_TRUST - 1;
        assertTrue(game.interactWithBlockAt(beacon));
        assertEquals(2, game.world.beaconStage,
                "one point short of Allied does not unlock the calibration codes");
        assertEquals(BlockType.BEACON, blockAt(game, beacon), "the beacon is still dark");
        assertNotEquals(Game.AppState.VICTORY, game.appState);

        game.faction.trust = ALLY_TRUST;
        game.uiMode = Game.UiMode.INVENTORY; // victory must not leave a screen open
        assertTrue(game.interactWithBlockAt(beacon));

        assertEquals(3, game.world.beaconStage, "calibration completes the repair");
        assertEquals(BlockType.BEACON_LIT, blockAt(game, beacon), "the lit beacon replaces the frame");
        assertEquals(Game.AppState.VICTORY, game.appState, "completing the beacon wins the game");
        assertEquals(Game.UiMode.NONE, game.uiMode, "victory closes any open screen");

        // A finished beacon is inert: interacting again cannot re-trigger anything.
        assertTrue(game.interactWithBlockAt(beacon));
        assertEquals(3, game.world.beaconStage);
        assertEquals(BlockType.BEACON_LIT, blockAt(game, beacon));
    }

    @Test
    void aHalfRepairedBeaconSurvivesASaveLoadCycleAndCanStillBeFinished() {
        Game game = new Game();
        game.newWorld(SEED, true);
        Vec3i beacon = placeBeaconFrame(game);

        game.player.inventory.clear();
        game.player.inventory.add(ItemType.SIGNAL_CRYSTAL, 1);
        game.player.inventory.add(ItemType.COPPER_INGOT, COPPER_REQUIRED);
        assertTrue(game.interactWithBlockAt(beacon));
        assertTrue(game.interactWithBlockAt(beacon));
        assertEquals(2, game.world.beaconStage, "precondition: the run reaches stage 2");

        Path savePath = AppPaths.dataDirectory().resolve("build/qa/beacon-endgame.dat");
        assertTrue(SaveSystem.save(game, savePath), "save must succeed");

        Game reloaded = new Game();
        assertTrue(SaveSystem.load(reloaded, savePath), "load must succeed");

        assertEquals(2, reloaded.world.beaconStage, "repair progress survives the round trip");
        assertEquals(beacon, reloaded.world.beaconPos, "the beacon's location survives");
        assertEquals(BlockType.BEACON, blockAt(reloaded, beacon));
        assertNotEquals(Game.AppState.VICTORY, reloaded.appState,
                "loading a half-repaired beacon does not win the game");

        reloaded.faction.trust = ALLY_TRUST;
        assertTrue(reloaded.interactWithBlockAt(beacon));
        assertEquals(3, reloaded.world.beaconStage);
        assertEquals(BlockType.BEACON_LIT, blockAt(reloaded, beacon));
        assertEquals(Game.AppState.VICTORY, reloaded.appState,
                "the endgame completes normally in a loaded world");
    }

    // ------------------------------------------------------------------

    /** Places a beacon frame beside the player through the real placement command. */
    private static Vec3i placeBeaconFrame(Game game) {
        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        game.player.inventory.set(0, new ItemStack(ItemType.BEACON_FRAME, 1));

        int x = (int) game.player.pos.x + 3;
        int z = (int) game.player.pos.z + 3;
        int y = game.world.surfaceHeight(x, z) + 1;
        assertTrue(game.placeSelectedBlockAt(x, y, z),
                "precondition: the beacon frame places beside the player");
        Vec3i pos = new Vec3i(x, y, z);
        assertNotNull(game.world.beaconPos, "precondition: placement registers the beacon");
        return pos;
    }

    private static BlockType blockAt(Game game, Vec3i pos) {
        return game.world.getBlock(pos.x(), pos.y(), pos.z());
    }
}
