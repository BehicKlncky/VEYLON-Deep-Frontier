package com.veylon;

import com.veylon.ai.FactionSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.SettlementManager;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.urban.LootTables;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.settlement.HumanFaction;

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
    /**
     * The urban container whose storage screen is open, or null. Its contents
     * have one owner (D-51): if the furniture burns, is blown apart or broken
     * while the screen is open, the screen must not keep handing them out or
     * swallow deposits into an inventory the world no longer holds. Legacy
     * crates keep their rules (D-12).
     */
    private Vec3i openUrbanContainer;
    private UrbanContainers.Entry openEntry;
    private float searchDuration;
    /** Discrete harm already received when the search began; over-time drains do not interrupt it. */
    private int searchHarm;
    private float rummageCooldown;
    private String containerTitle;

    CrateTransactionSystem(Game game) {
        this.game = game;
    }

    /** Blast/fire attribution captured from the actual container before removal. */
    void onSurvivorStorageDestroyed(long ownerId) {
        if (ownerId != com.veylon.world.urban.UrbanIds.NONE) {
            game.settlementManager.addReputation(game,HumanFaction.FREE_SETTLERS,-15,
                    "Destroying survivor storage harms your standing.");
        }
    }

    /** Clears theft bookkeeping so a new or loaded world starts unattributed. */
    void reset() {
        nextTheftEventId = 1;
        activeTheftEventId = Long.MIN_VALUE;
        activeTheftEventCrate = null;
        activeTheftTransferCount = 0;
        openUrbanContainer = null;
        closeSearch();
    }

    /** Opens a real world crate or urban container; both RMB/F interaction paths use this command. */
    boolean openCrateAt(Vec3i pos) {
        if (pos == null || !Fixtures.isContainer(game.world.getBlock(pos.x(), pos.y(), pos.z()))) {
            return false;
        }
        if (UrbanContainers.manages(game.world)) {
            var entry = game.world.urban.containers.touch(game.world,pos);
            if (entry == null) {
                game.log("This container cannot be changed: the world's storage record is full.");
                return false;
            }
            openEntry = entry;
            var plan = entry.id > 0 ? UrbanContainers.planned(game.world,pos) : null;
            var use = plan == null ? null : BuildingFurnishing.containerUse(plan.building(),plan.fixture());
            boolean walkUp = plan == null || plan.building().family().walkUp();
            containerTitle = plan == null ? entry.block.displayName : switch (use) {
                case KITCHEN_CABINET -> "Kitchen cupboard";
                case FRIDGE -> "Fridge shelf";
                case PANTRY_SHELF -> "Pantry shelf";
                case BATHROOM_CABINET -> "Bathroom cabinet";
                case WARDROBE -> "Wardrobe";
                case DRESSER_DESK -> walkUp ? "Dresser" : "Desk drawers";
                case UTILITY_LOCKER -> "Utility locker";
                case SHOP_SHELF -> walkUp ? "Shop shelf" : "Grocery shelf";
                case STOCKROOM_CRATE -> "Supply cupboard";
                case MEDICINE_CABINET -> "Dispensary cabinet";
                case ARMORY_LOCKER -> "Armoury locker";
                case TREATMENT_CABINET -> "Treatment cabinet";
                case WAREHOUSE_RACK -> "Warehouse rack";
                case TOOL_CHEST -> "Tool chest";
            };
            if (plan != null && plan.table() == LootTables.Table.SECURED
                    && use != BuildingFurnishing.ContainerUse.ARMORY_LOCKER) {
                containerTitle = "Secured supply locker";
            }
            containerTitle = containerTitle.toUpperCase(java.util.Locale.ROOT);
            searchHarm = game.player.harmEvents;
            rummageCooldown = 0;
            if (!entry.state.revealed()) {
                searchDuration = LootTables.searchSeconds(plan.table());
                entry.state = UrbanContainers.State.SEARCHING;
            }
            game.openCrate = entry.state.revealed() ? game.world.crateContents.get(pos) : null;
            game.openCratePos = pos;
            game.uiMode = Game.UiMode.CRATE;
            openUrbanContainer = null;
            return true;
        }
        game.settlementManager.onRestrictedStorageOpened(game, pos);
        game.openCrate = game.world.crateContents.computeIfAbsent(
                pos, k -> new Inventory(CRATE_SLOTS));
        game.openCratePos = pos;
        game.uiMode = Game.UiMode.CRATE;
        openUrbanContainer = Fixtures.isUrbanContainer(game.world.getBlock(pos.x(), pos.y(), pos.z()))
                ? pos : null;
        return true;
    }

    /**
     * True, closing the screen, when the open storage was an urban container
     * that is gone: no longer that furniture, or no longer holding this inventory.
     */
    private boolean openUrbanContainerGone() {
        if (openEntry != null) {
            Vec3i p = openEntry.position;
            if (game.uiMode == Game.UiMode.CRATE && p.equals(game.openCratePos)
                    && game.world.isColumnLoaded(p.x(),p.z())
                    && game.world.urban.containers.at(p) == openEntry
                    && game.world.getBlock(p.x(),p.y(),p.z()) == openEntry.block
                    && !game.player.dead && game.player.harmEvents == searchHarm
                    && p.distSq(game.player.pos.x,game.player.pos.y,game.player.pos.z) <= 25) {
                return false;
            }
            game.closeScreens();
            game.log("Search interrupted. Your progress is kept.");
            return true;
        }
        Vec3i p = openUrbanContainer;
        if (p == null || !p.equals(game.openCratePos)) {
            return false;
        }
        if (Fixtures.isUrbanContainer(game.world.getBlock(p.x(), p.y(), p.z()))
                && game.world.crateContents.get(p) == game.openCrate) {
            return false;
        }
        openUrbanContainer = null;
        game.closeScreens();
        game.log("There is nothing left to search.");
        return true;
    }

    /** Transient session target only; durable progress remains on the world entry. */
    void closeSearch() {
        openEntry = null;
        rummageCooldown = 0;
        searchDuration = 0;
        containerTitle = null;
    }

    /** Runs on the normal fast simulation tick; CRATE never pauses threats, needs or fire. */
    void fastTick(float dt) {
        if (openEntry == null || openUrbanContainerGone()) { return; }
        if (!openEntry.state.revealed()) {
            openEntry.progress += dt;
            rummageCooldown -= dt;
            if (rummageCooldown <= 0) {
                rummageCooldown = 1;
                Vec3i p = openEntry.position;
                game.noise.emit(game,p.x()+.5f,p.y()+.5f,p.z()+.5f,12,.3f,"rummage",true,game.player);
                game.audio.playBlockHit(openEntry.block,p.x()+.5f,p.y()+.5f,p.z()+.5f);
            }
            if (openEntry.progress >= searchDuration) {
                openEntry.progress = 0;
                openEntry.state = UrbanContainers.State.SEARCHED;
                game.world.urban.containers.compact(game.world,openEntry);
            }
        }
        game.openCrate = openEntry.state.revealed() ? game.world.crateContents.get(openEntry.position) : null;
    }

    boolean searching() { return openEntry != null && !openEntry.state.revealed(); }
    String containerTitle() { return containerTitle; }
    float searchProgress() { return searching() ? Math.min(1,openEntry.progress / searchDuration) : 1; }
    String storageStatus() {
        if (openEntry == null) { return "Click an item to transfer it."; }
        String owner = switch (openEntry.owner) {
            case ABANDONED -> "Abandoned property";
            case SURVIVOR -> "Survivor storage: taking supplies is theft";
            case PLAYER -> "Your storage";
        };
        return owner + (searching() ? " | Searching " + Math.round(searchProgress()*100) + "%"
                : openEntry.state == UrbanContainers.State.EMPTY ? " | Empty (searched)" : " | Searched");
    }

    /** Hand salvage is one consequential action, including an empty owned cupboard. */
    void onContainerSalvaged(UrbanContainers.Entry entry) {
        if (entry.owner == UrbanContainers.Owner.SURVIVOR) {
            game.settlementManager.addReputation(game,HumanFaction.FREE_SETTLERS,-15,
                    "Dismantling survivor storage harms your standing.");
        }
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
        if (UrbanContainers.manages(game.world)) {
            var entry = game.world.urban.containers.at(cratePos);
            if (newEvent && entry != null && entry.owner == UrbanContainers.Owner.SURVIVOR) {
                game.settlementManager.addReputation(game,HumanFaction.FREE_SETTLERS,
                        -Math.min(CAMP_THEFT_PENALTY_MAX,CAMP_THEFT_PENALTY_BASE+count),
                        "Taking survivor supplies harms your standing.");
            }
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
        return transferCrateItemToPlayer(slot,Integer.MAX_VALUE);
    }

    int transferCrateItemToPlayer(int slot, int count) {
        if (count <= 0) { return 0; }
        if (openUrbanContainerGone()) {
            return 0;
        }
        if (searching()) { game.log("Finish searching before taking supplies."); return 0; }
        if (openEntry != null) { game.openCrate = game.world.crateContents.get(openEntry.position); }
        Inventory crate = game.openCrate;
        if (crate == null || slot < 0 || slot >= crate.size()) {
            return 0;
        }
        ItemStack stack = crate.get(slot);
        if (stack == null) {
            return 0;
        }
        ItemType type = stack.type;
        ItemStack offered = stack.copy();
        offered.count = Math.min(count,stack.count);
        int before = offered.count;
        int leftover = game.player.inventory.addStack(offered);
        int taken = before - leftover;
        stack.count -= taken;
        crate.set(slot, stack.count > 0 ? stack : null);
        if (taken > 0) {
            game.audio.playClick();
            long eventId = nextLogicalTheftEventId();
            onCrateItemTaken(type, taken, eventId, eventId);
            if (openEntry != null) {
                game.world.urban.containers.compact(game.world,openEntry);
                game.openCrate = game.world.crateContents.get(openEntry.position);
            }
        }
        if (leftover > 0) { game.log(taken > 0 ? "Some supplies remain: your inventory is full." : "Your inventory is full."); }
        return taken;
    }

    /** Real crate-UI deposit command; returned supplies are attributed by crate position. */
    int transferPlayerItemToCrate(int slot) {
        return transferPlayerItemToCrate(slot,Integer.MAX_VALUE);
    }

    int transferPlayerItemToCrate(int slot, int count) {
        if (count <= 0) { return 0; }
        if (openUrbanContainerGone()) {
            return 0;
        }
        if (searching()) { game.log("Finish searching before storing supplies."); return 0; }
        Inventory crate = game.openCrate;
        if (slot < 0 || slot >= game.player.inventory.size()) {
            return 0;
        }
        ItemStack stack = game.player.inventory.get(slot);
        if (stack == null) {
            return 0;
        }
        if (openEntry != null) {
            crate = game.world.crateContents.computeIfAbsent(openEntry.position,k -> new Inventory(CRATE_SLOTS));
            game.openCrate = crate;
        }
        if (crate == null) { return 0; }
        ItemType type = stack.type;
        ItemStack offered = stack.copy();
        offered.count = Math.min(count,stack.count);
        int before = offered.count;
        int leftover = crate.addStack(offered);
        int deposited = before - leftover;
        stack.count -= deposited;
        game.player.inventory.set(slot, stack.count > 0 ? stack : null);
        if (deposited > 0) {
            game.audio.playClick();
            if (openEntry != null) {
                game.world.urban.containers.compact(game.world,openEntry);
            } else if (game.openCratePos != null) {
                game.settlementManager.onSuppliesReturned(
                        game, game.openCratePos, type, deposited);
            }
        }
        if (leftover > 0) { game.log("The container is full. Remaining items stay with you."); }
        return deposited;
    }
}
