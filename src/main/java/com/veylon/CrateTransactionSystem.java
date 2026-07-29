package com.veylon;

import com.veylon.ai.FactionSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.SettlementManager;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

/**
 * Opening crates and moving items in and out of them, including the crime
 * attribution that taking from someone else's storage triggers.
 *
 * <p>The subtle part is deduplication. One player gesture can produce several
 * callbacks — the crate UI reports per stack, and a batched "take all" reports
 * several stacks of the same item type. Each is a distinct <em>physical
 * transfer</em> that must update stock, but together they are one <em>logical
 * crime</em> that must be punished once. This class tracks both ids so a
 * repeated callback is idempotent while genuinely distinct stacks still count.
 *
 * <p>{@code openCrate} and {@code openCratePos} stay on {@link Game} because
 * {@code CrateScreen} reads them directly.
 */
final class CrateTransactionSystem {

    /** Slots in a crate created on first open. */
    private static final int CRATE_SLOTS = 12;
    /** Camp trust at or above which taking from camp crates is not theft. */
    private static final int CAMP_TRUST_THRESHOLD = 75;
    /** Blocks from the camp centre within which the camp notices a theft. */
    private static final int CAMP_THEFT_RADIUS = 9;
    /** Trust lost for a camp theft is 3 + count, capped here. */
    private static final int CAMP_THEFT_PENALTY_BASE = 3;
    private static final int CAMP_THEFT_PENALTY_MAX = 12;

    private final Game game;

    /** Monotonic source of logical crime-event ids. */
    private long nextTheftEventId = 1;
    /** The logical event currently accumulating transfers. */
    private long activeTheftEventId = Long.MIN_VALUE;
    private Vec3i activeTheftEventCrate;
    private final long[] activeTheftTransferIds =
            new long[SettlementManager.MAX_THEFT_TRANSFERS_PER_EVENT];
    private int activeTheftTransferCount;

    CrateTransactionSystem(Game game) {
        this.game = game;
    }

    /** Clears theft bookkeeping so a new or loaded world starts unattributed. */
    void reset() {
        nextTheftEventId = 1;
        activeTheftEventId = Long.MIN_VALUE;
        activeTheftEventCrate = null;
        activeTheftTransferCount = 0;
    }

    /** Opens a real world crate; both RMB/F interaction paths use this command. */
    boolean openCrateAt(Vec3i pos) {
        if (pos == null || game.world.getBlock(pos.x(), pos.y(), pos.z()) != BlockType.CRATE) {
            return false;
        }
        game.settlementManager.onRestrictedStorageOpened(game, pos);
        game.openCrate = game.world.crateContents.computeIfAbsent(
                pos, k -> new Inventory(CRATE_SLOTS));
        game.openCratePos = pos;
        game.uiMode = Game.UiMode.CRATE;
        return true;
    }

    /** Called by the crate UI; taking from camp crates is stealing unless trusted. */
    void onCrateItemTaken(ItemType type, int count) {
        long eventId = nextLogicalTheftEventId();
        onCrateItemTaken(type, count, eventId, eventId);
    }

    /**
     * Compatibility overload for a logical event with at most one transfer per
     * item type. Callers batching multiple same-type stacks must supply distinct
     * transfer ids through the four-argument overload.
     */
    void onCrateItemTaken(ItemType type, int count, long logicalEventId) {
        onCrateItemTaken(type, count, logicalEventId, type == null ? 0 : type.ordinal() + 1L);
    }

    /**
     * Attributes a callback to one logical crime event and one physical transfer.
     * Repeated delivery of the same transfer id is idempotent, while different
     * transfer ids update every stack even when their item types are identical.
     * The fixed window is cleared by the next event/crate.
     */
    void onCrateItemTaken(ItemType type, int count, long logicalEventId, long transferId) {
        Vec3i cratePos = game.openCratePos;
        if (cratePos == null || type == null || count <= 0) {
            return;
        }
        boolean newEvent = activeTheftEventId != logicalEventId
                || !cratePos.equals(activeTheftEventCrate);
        if (newEvent) {
            activeTheftEventId = logicalEventId;
            activeTheftEventCrate = cratePos;
            activeTheftTransferCount = 0;
        }
        if (!rememberTheftTransfer(transferId)) {
            return;
        }
        if (newEvent && game.world.campPos != null && game.faction.trust < CAMP_TRUST_THRESHOLD
                && cratePos.distSq(game.world.campPos.x(), game.world.campPos.y(),
                        game.world.campPos.z()) < CAMP_THEFT_RADIUS * CAMP_THEFT_RADIUS) {
            game.faction.addTrust(game,
                    -Math.min(CAMP_THEFT_PENALTY_MAX, CAMP_THEFT_PENALTY_BASE + count),
                    "The camp caught you stealing!");
        }
        // Settlement crates: theft angers the locals.
        game.settlementManager.onCrateTheft(
                game, cratePos, type, count, logicalEventId, transferId);
        var settlement = game.world.settlementAt(cratePos.x(), cratePos.z());
        if (settlement != null && settlement.hostile()) {
            game.faction.onStolenSuppliesRecovered(game, settlement,
                    FactionSystem.settlementStorageId(settlement.id, cratePos), type, count);
        }
    }

    /** @return false when this physical transfer was already counted */
    private boolean rememberTheftTransfer(long transferId) {
        for (int i = 0; i < activeTheftTransferCount; i++) {
            if (activeTheftTransferIds[i] == transferId) {
                return false;
            }
        }
        if (activeTheftTransferCount >= activeTheftTransferIds.length) {
            throw new IllegalStateException("logical theft event exceeds transfer limit");
        }
        activeTheftTransferIds[activeTheftTransferCount++] = transferId;
        return true;
    }

    private long nextLogicalTheftEventId() {
        long id = nextTheftEventId++;
        if (nextTheftEventId <= 0) {
            nextTheftEventId = 1;
        }
        return id;
    }

    /** Real crate-UI transfer command used by mouse input and integration tests. */
    int transferCrateItemToPlayer(int slot) {
        Inventory crate = game.openCrate;
        if (crate == null || slot < 0 || slot >= crate.size()) {
            return 0;
        }
        ItemStack stack = crate.get(slot);
        if (stack == null) {
            return 0;
        }
        ItemType type = stack.type;
        int before = stack.count;
        int leftover = game.player.inventory.addStack(stack);
        int taken = before - leftover;
        crate.set(slot, leftover > 0 ? stack : null);
        if (taken > 0) {
            game.audio.playClick();
            long eventId = nextLogicalTheftEventId();
            onCrateItemTaken(type, taken, eventId, eventId);
        }
        return taken;
    }

    /** Real crate-UI deposit command; returned supplies are attributed by crate position. */
    int transferPlayerItemToCrate(int slot) {
        Inventory crate = game.openCrate;
        if (crate == null || slot < 0 || slot >= game.player.inventory.size()) {
            return 0;
        }
        ItemStack stack = game.player.inventory.get(slot);
        if (stack == null) {
            return 0;
        }
        ItemType type = stack.type;
        int before = stack.count;
        int leftover = crate.addStack(stack);
        int deposited = before - leftover;
        game.player.inventory.set(slot, leftover > 0 ? stack : null);
        if (deposited > 0) {
            game.audio.playClick();
            if (game.openCratePos != null) {
                game.settlementManager.onSuppliesReturned(
                        game, game.openCratePos, type, deposited);
            }
        }
        return deposited;
    }
}
