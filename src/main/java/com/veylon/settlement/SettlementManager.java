package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.ai.Pathfinder;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.util.MathUtil;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Random;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Settlement runtime: resident activation/dormancy, abstract off-screen
 * simulation (food, replenishment, morale), local + faction reputation,
 * capture/occupation/counterattack flow, patrols, bounty hunter parties,
 * alarm state and gate auto-closing.
 */
public class SettlementManager {

    /** Residents materialize as live NPCs inside this range of the player. */
    public static final float ACTIVATE_RADIUS = 96f;
    /** ...and write back / despawn beyond this range (hysteresis). */
    public static final float DEACTIVATE_RADIUS = 130f;
    /** Hard bound on every simultaneously active human NPC, not just residents. */
    public static final int MAX_ACTIVE_NPCS = 40;
    /** Resident ceiling; other categories can never consume the whole global budget. */
    public static final int MAX_ACTIVE_RESIDENTS = 32;
    /** All patrol, hunter and counterattack entities combined. */
    public static final int MAX_ACTIVE_WAR_PARTY_NPCS = 12;
    public static final int MAX_ACTIVE_PATROL_NPCS = 8;
    public static final int MAX_ACTIVE_BOUNTY_HUNTERS = 4;
    public static final int MAX_ACTIVE_COUNTERATTACKERS = 8;
    /**
     * Counterattacks are player-facing defense missions, so lower-priority
     * residents, ambient actors and ordinary parties may not consume these
     * slots.  The reservation is still inside {@link #MAX_ACTIVE_NPCS}; it
     * merely guarantees that a nearby assault can materialize for real combat.
     */
    public static final int COUNTERATTACK_SLOT_RESERVE = MAX_ACTIVE_COUNTERATTACKERS;
    /** Starter-camp residents, wandering traders and event raiders. */
    public static final int MAX_ACTIVE_LEGACY_NPCS = 8;
    /** Leaves at least 24 of the global slots available to nearby settlements. */
    public static final int MAX_ACTIVE_NON_RESIDENT_NPCS = 16;
    /** Discovery range for map markers. */
    public static final float DISCOVER_RADIUS = 70f;
    /** Seconds an opened gate stays open. */
    public static final float GATE_OPEN_SECONDS = 4f;

    /** Supplies required to occupy a cleared hostile settlement. */
    public static final int OCCUPY_FOOD = 10;
    public static final int OCCUPY_WOOD = 10;
    public static final int RESTITUTION_FOOD = 6;
    public static final int RESTITUTION_MEDICINE = 2;
    /** A lingering trespass is penalized at most once per minute. */
    public static final float TRESPASS_INTERVAL = 60f;
    /** Reopening the same community stores cannot apply a penalty every frame. */
    public static final float RESTRICTED_STORAGE_INTERVAL = 30f;
    /** Crates have 12 slots; leave bounded headroom for composite UI transfers. */
    public static final int MAX_THEFT_TRANSFERS_PER_EVENT = 16;

    private final Random rng = new Random();
    private float bountyPartyCooldown = 120f;
    private long partySequence;
    private long generatedTheftEventId;
    private long activeTheftEventId = Long.MIN_VALUE;
    private Vec3i activeTheftCrate;
    private final long[] activeTheftTransferIds = new long[MAX_THEFT_TRANSFERS_PER_EVENT];
    private int activeTheftTransferCount;
    private boolean activeTheftPenaltyApplied;
    /** Persistent group-level state for occupied-outpost counterattacks. */
    public final CounterattackDirector counterattacks = new CounterattackDirector();

    /** Admission categories for the single combined NPC budget. */
    public enum NpcCategory {
        SETTLEMENT_RESIDENT,
        PATROL,
        BOUNTY_HUNTER,
        COUNTERATTACK,
        LEGACY
    }

    /** Immutable measured snapshot used by gameplay, QA and the debug overlay. */
    public record NpcCounts(int total, int residents, int warParties, int patrols,
                            int bountyHunters, int counterattackers, int legacy) {
        public int nonResidents() {
            return warParties + legacy;
        }
    }

    public void reset() {
        bountyPartyCooldown = 120f;
        partySequence = 0;
        generatedTheftEventId = 0;
        activeTheftEventId = Long.MIN_VALUE;
        activeTheftCrate = null;
        activeTheftTransferCount = 0;
        activeTheftPenaltyApplied = false;
        counterattacks.reset();
    }

    // ------------------------------------------------------------------
    // Reputation
    // ------------------------------------------------------------------

    public float reputation(Game g, String factionId) {
        return g.world.factionReputation.getOrDefault(factionId, 0f);
    }

    public void addReputation(Game g, String factionId, float delta, String reason) {
        float now = MathUtil.clamp(reputation(g, factionId) + delta, -100f, 100f);
        g.world.factionReputation.put(factionId, now);
        if (reason != null && Math.abs(delta) >= 2) {
            g.log(reason + " (" + HumanFaction.displayName(factionId) + " "
                    + (delta > 0 ? "+" : "") + (int) delta + ")");
        }
        if (delta < 0 && HumanFaction.innatelyHostile(factionId)) {
            float bounty = g.world.factionBounty.getOrDefault(factionId, 0f);
            g.world.factionBounty.put(factionId,
                    MathUtil.clamp(bounty + Math.abs(delta) * 0.6f, 0f, 100f));
        }
    }

    /** Local settlement reputation; flips neutral settlements at thresholds. */
    public void addLocalReputation(Game g, Settlement s, float delta, String reason) {
        s.localReputation = MathUtil.clamp(s.localReputation + delta, -100f, 100f);
        if (reason != null && Math.abs(delta) >= 2) {
            g.log(reason);
        }
        if (s.founderFaction.equals(HumanFaction.FREE_SETTLERS) && !s.cleared) {
            if (s.alignment == Settlement.Alignment.NEUTRAL && s.localReputation >= 40) {
                s.alignment = Settlement.Alignment.FRIENDLY;
                g.log("The " + s.type.displayName.toLowerCase() + " now counts you as a friend.");
                g.audio.playQuest();
            } else if (s.alignment != Settlement.Alignment.HOSTILE && s.localReputation <= -30) {
                s.alignment = Settlement.Alignment.HOSTILE;
                g.log("The " + s.type.displayName.toLowerCase() + " has turned HOSTILE toward you!");
            }
        }
        addReputation(g, s.factionId, delta * 0.3f, null);
    }

    /** A completed local trade benefits the actual resident's settlement. */
    public void onTradeCompleted(Game g, Settlement s, ItemType received, int count) {
        if (s == null || s.hostile() || s.cleared && !s.occupied || count <= 0) {
            return;
        }
        addSupplyStock(s, received, count);
        addLocalReputation(g, s, 3f, "Fair dealing improves your local standing");
    }

    /** Gifts are credited locally before their smaller faction-wide effect. */
    public void onGiftGiven(Game g, Settlement s, ItemType type, int count) {
        if (s == null || s.hostile() || s.cleared && !s.occupied || type == null || count <= 0) {
            return;
        }
        addSupplyStock(s, type, count);
        addLocalReputation(g, s, type.isEdible() || type == ItemType.MEDICINE ? 8f : 5f,
                "Your gift helps the " + s.type.displayName.toLowerCase());
    }

    /** Curing a regional resident never credits the unrelated starter camp. */
    public boolean onResidentHealed(Game g, Npc resident) {
        if (resident == null || !resident.settled() || resident.dead) {
            return false;
        }
        Settlement s = g.world.settlements.get(resident.settlementId);
        if (s == null || s.hostile() || s.cleared && !s.occupied || resident.residentIndex < 0
                || resident.residentIndex >= s.residents.size()) {
            return false;
        }
        Settlement.Resident record = s.residents.get(resident.residentIndex);
        if (!resident.sick && !record.sick) {
            return false;
        }
        resident.sick = false;
        record.sick = false;
        record.sicknessTimer = 0;
        record.health = Math.max(record.health, resident.health);
        addLocalReputation(g, s, 12f, "You cured " + resident.name + " with medicine!");
        return true;
    }

    /** Supplies placed in community stores feed both the ledger and local goodwill. */
    public boolean onSuppliesReturned(Game g, Vec3i cratePos, ItemType type, int count) {
        if (cratePos == null || type == null || count <= 0 || !isSettlementSupply(type)) {
            return false;
        }
        Settlement s = g.world.settlementAt(cratePos.x(), cratePos.z());
        if (s == null || s.cleared && !s.occupied) {
            return false;
        }
        addSupplyStock(s, type, count);
        if (s.hostile()) {
            onEnemyFactionHelped(g, s, count);
        } else {
            addLocalReputation(g, s, Math.min(6f, 1f + count),
                    "You returned useful supplies to the settlement");
        }
        return true;
    }

    /** Predator/threat credit is tied to the nearby regional community. */
    public boolean onNearbyThreatCleared(Game g, float x, float z) {
        Settlement s = nearest(g, x, z, 30f);
        if (s == null || s.hostile() || s.cleared && !s.occupied
                || s.distSqTo(x, z) > 30f * 30f) {
            return false;
        }
        addLocalReputation(g, s, 6f,
                "The " + s.type.displayName.toLowerCase() + " saw you clear a nearby threat");
        return true;
    }

    /** Defense rewards are only paid by the mission's explicit destination. */
    public boolean onSettlementDefended(Game g, long targetSettlementId, float x, float z) {
        Settlement s = g.world.settlements.get(targetSettlementId);
        if (s == null || s.hostile() || s.cleared && !s.occupied
                || s.distSqTo(x, z) > 70f * 70f) {
            return false;
        }
        addLocalReputation(g, s, 8f,
                "You defended the " + s.type.displayName.toLowerCase() + "!");
        return true;
    }

    /**
     * Explicit peace transition for Free Settlers. Generic positive reputation
     * cannot silently erase hostility; food and medicine must be returned at
     * the settlement fire.
     */
    public boolean offerRestitution(Game g, Settlement s) {
        if (s == null || s.cleared || s.occupied
                || s.alignment != Settlement.Alignment.HOSTILE
                || !HumanFaction.FREE_SETTLERS.equals(s.founderFaction)) {
            return false;
        }
        int food = g.player.inventory.count(ItemType.COOKED_MEAT)
                + g.player.inventory.count(ItemType.DRIED_MEAT)
                + g.player.inventory.count(ItemType.BERRY)
                + g.player.inventory.count(ItemType.DRIED_BERRY);
        if (food < RESTITUTION_FOOD
                || g.player.inventory.count(ItemType.MEDICINE) < RESTITUTION_MEDICINE) {
            g.log("Restitution requires " + RESTITUTION_FOOD + " food and "
                    + RESTITUTION_MEDICINE + " medicine at the settlement fire.");
            return false;
        }
        int remaining = RESTITUTION_FOOD;
        for (ItemType type : new ItemType[]{ItemType.COOKED_MEAT, ItemType.DRIED_MEAT,
                ItemType.BERRY, ItemType.DRIED_BERRY}) {
            int take = Math.min(remaining, g.player.inventory.count(type));
            g.player.inventory.remove(type, take);
            remaining -= take;
        }
        g.player.inventory.remove(ItemType.MEDICINE, RESTITUTION_MEDICINE);
        s.foodStock += RESTITUTION_FOOD;
        s.medStock += RESTITUTION_MEDICINE;
        s.localReputation = -5;
        s.alignment = Settlement.Alignment.NEUTRAL;
        s.alertLevel = 0;
        for (Settlement.Resident resident : s.residents) {
            if (resident.live != null) {
                resident.live.lastKnownAge = 999f;
                resident.live.searchTimer = 0;
                resident.live.combatTarget = null;
            }
        }
        g.log("The Free Settlers accept your restitution. The settlement returns to neutrality.");
        addReputation(g, HumanFaction.FREE_SETTLERS, 5, null);
        return true;
    }

    /** Called for player attacks on any NPC (may be null for non-player blasts). */
    public void onNpcAttackedByPlayer(Game g, Npc n) {
        if (n == null || !n.settled()) {
            return;
        }
        Settlement s = g.world.settlements.get(n.settlementId);
        if (s == null) {
            return;
        }
        if (n.archetype == NpcArchetype.CAPTIVE) {
            addReputation(g, HumanFaction.FRONTIER, -18,
                    "You harmed a protected captive");
            addReputation(g, HumanFaction.FREE_SETTLERS, -10, null);
        } else if (!s.hostile()) {
            addLocalReputation(g, s, -18, "You attacked " + n.name + "!");
        }
        s.alertLevel = Math.min(100, s.alertLevel + 40);
        n.lastKnown.set(g.player.pos);
        n.lastKnownAge = 0;
        n.searchTimer = 20f;
    }

    public void onNpcKilledByPlayer(Game g, Npc n) {
        if (!n.settled()) {
            return;
        }
        Settlement s = g.world.settlements.get(n.settlementId);
        if (s == null) {
            return;
        }
        if (n.residentIndex >= 0 && n.residentIndex < s.residents.size()) {
            Settlement.Resident r = s.residents.get(n.residentIndex);
            r.alive = false;
            r.live = null;
        }
        if (n.archetype == NpcArchetype.CAPTIVE) {
            addReputation(g, HumanFaction.FRONTIER, -35,
                    "You killed a protected captive");
            addReputation(g, HumanFaction.FREE_SETTLERS, -20, null);
        } else if (s.hostile()) {
            addReputation(g, s.factionId, -4, null); // hostiles hold grudges too
            s.morale = Math.max(0, s.morale - (n.archetype.leader ? 45 : 7));
            if (n.archetype.leader) {
                g.log("The " + HumanFaction.displayName(s.factionId)
                        + " leader has fallen! The garrison wavers.");
            }
            checkCleared(g, s);
        } else {
            addLocalReputation(g, s, -35, "You killed " + n.name + "!");
        }
        s.alertLevel = Math.min(100, s.alertLevel + 50);
    }

    /** Theft from settlement crates. */
    public void onCrateTheft(Game g, Vec3i cratePos, int count) {
        long eventId = --generatedTheftEventId;
        onCrateTheft(g, cratePos, null, count, eventId, eventId);
    }

    public void onCrateTheft(Game g, Vec3i cratePos, ItemType type, int count) {
        long eventId = --generatedTheftEventId;
        onCrateTheft(g, cratePos, type, count, eventId, eventId);
    }

    /**
     * Compatibility overload for an event with at most one transfer per item type.
     */
    public void onCrateTheft(Game g, Vec3i cratePos, ItemType type, int count,
                             long logicalEventId) {
        onCrateTheft(g, cratePos, type, count, logicalEventId,
                type == null ? 0 : type.ordinal() + 1L);
    }

    /**
     * Applies one uniquely identified transfer within a logical theft event.
     * Event state is a single fixed-size window: changing the event or crate
     * clears it, duplicate transfer callbacks are ignored, distinct same-type
     * stacks all update stock, and the crime penalty is applied only once.
     */
    public void onCrateTheft(Game g, Vec3i cratePos, ItemType type, int count,
                             long logicalEventId, long transferId) {
        if (g == null || cratePos == null || count <= 0) {
            return;
        }
        if (activeTheftEventId != logicalEventId || !cratePos.equals(activeTheftCrate)) {
            activeTheftEventId = logicalEventId;
            activeTheftCrate = cratePos;
            activeTheftTransferCount = 0;
            activeTheftPenaltyApplied = false;
        }
        if (!rememberTheftTransfer(transferId)) {
            return;
        }
        Settlement s = g.world.settlementAt(cratePos.x(), cratePos.z());
        if (s != null) {
            // The physical inventory transfer always changes the matching
            // settlement ledger, even when access makes the transfer lawful.
            removeSupplyStock(s, type, count);
        }
        if (s != null && !hasSettlementAccess(s) && !activeTheftPenaltyApplied) {
            addLocalReputation(g, s, -Math.min(15, 4 + count),
                    "They saw you stealing from their stores!");
            s.alertLevel = Math.min(100, s.alertLevel + 15);
            activeTheftPenaltyApplied = true;
        }
    }

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

    /** Opening a restricted store is an explicit crime separate from taking goods. */
    public boolean onRestrictedStorageOpened(Game g, Vec3i cratePos) {
        if (cratePos == null) {
            return false;
        }
        Settlement s = g.world.settlementAt(cratePos.x(), cratePos.z());
        if (s == null || hasSettlementAccess(s) || s.restrictedStorageCooldown > 0) {
            return false;
        }
        s.restrictedStorageCooldown = RESTRICTED_STORAGE_INTERVAL;
        addLocalReputation(g, s, -4f, "You entered restricted settlement storage");
        s.alertLevel = Math.min(100, s.alertLevel + 8);
        return true;
    }

    /** Breaking a container is attributed once, independently of its item transfers. */
    public void onContainerBroken(Game g, Settlement s, int contents) {
        if (s == null || s.hostile() || s.cleared && !s.occupied) {
            return;
        }
        addLocalReputation(g, s, -Math.min(20f, 10f + Math.max(0, contents)),
                "You smashed a settlement container and took its contents!");
        s.alertLevel = Math.min(100, s.alertLevel + 25);
    }

    /** Player mined/blasted a settlement structure block. */
    public void onStructureDestroyed(Game g, Settlement s, BlockType t) {
        if (s.hostile() || s.cleared && !s.occupied) {
            return;
        }
        if (t == BlockType.WALL || t == BlockType.STONE_BRICK || t == BlockType.GATE
                || t == BlockType.CRATE || t == BlockType.CAMPFIRE || t == BlockType.CAMP_BED
                || t == BlockType.PLANK || t == BlockType.LOG || t == BlockType.TORCH
                || t == BlockType.WORKBENCH || t == BlockType.FURNACE || t == BlockType.ANVIL) {
            addLocalReputation(g, s, -8, "You wrecked settlement property!");
            s.alertLevel = Math.min(100, s.alertLevel + 10);
        }
    }

    /** One aggregate property consequence for a complete player-caused blast chain. */
    public void onExplosionPropertyDamaged(Game g, Settlement s, int blocks,
                                           boolean containerDestroyed) {
        if (s == null || s.hostile() || s.cleared && !s.occupied || blocks <= 0) {
            return;
        }
        float penalty = Math.min(24f, 8f + blocks * 0.75f
                + (containerDestroyed ? 5f : 0f));
        addLocalReputation(g, s, -penalty,
                "Your explosion damaged settlement property!");
        s.alertLevel = Math.min(100, s.alertLevel + 35);
    }

    /** Supplying an innately hostile faction costs Frontier standing. */
    public void onEnemyFactionHelped(Game g, Settlement beneficiary, int count) {
        if (beneficiary == null || !HumanFaction.innatelyHostile(beneficiary.factionId)) {
            return;
        }
        float magnitude = Math.min(10f, 3f + Math.max(1, count));
        addReputation(g, beneficiary.factionId, Math.max(1f, magnitude * 0.35f), null);
        addReputation(g, HumanFaction.FRONTIER, -magnitude,
                "You supplied enemies of the Frontier Compact");
        g.world.factionBounty.computeIfPresent(beneficiary.factionId,
                (id, bounty) -> Math.max(0f, bounty - 2f));
    }

    /** Friendship/occupation grants access; ordinary neutral standing does not. */
    public boolean hasSettlementAccess(Settlement s) {
        return s == null || s.hostile() || s.cleared || s.occupied || s.friendly()
                || s.localReputation >= 40f;
    }

    /** Only storehouses and command/prison/magazine rooms count as restricted. */
    public boolean isRestrictedArea(Game g, Settlement s, float x, float z) {
        if (s == null || hasSettlementAccess(s) || !s.containsBlock((int) Math.floor(x),
                (int) Math.floor(z))) {
            return false;
        }
        if (nearXZ(s.leaderPost, x, z, 5f) || nearXZ(s.prisonPos, x, z, 5f)
                || nearXZ(s.magazinePos, x, z, 5f)) {
            return true;
        }
        for (Vec3i crate : g.world.layoutFor(s).crates.keySet()) {
            if (nearXZ(crate, x, z, 5f)) {
                return true;
            }
        }
        return false;
    }

    private static boolean nearXZ(Vec3i pos, float x, float z, float radius) {
        if (pos == null) {
            return false;
        }
        float dx = pos.x() + 0.5f - x, dz = pos.z() + 0.5f - z;
        return dx * dx + dz * dz <= radius * radius;
    }

    private static boolean isSettlementSupply(ItemType type) {
        return type == ItemType.MEDICINE || type.isEdible() || type == ItemType.LOG
                || type == ItemType.PLANK || type == ItemType.SCRAP
                || type == ItemType.IRON_ORE || type == ItemType.COPPER_ORE
                || type == ItemType.IRON_INGOT || type == ItemType.COPPER_INGOT;
    }

    private static void addSupplyStock(Settlement s, ItemType type, int count) {
        if (type == null || count <= 0) {
            return;
        }
        if (type == ItemType.MEDICINE) {
            s.medStock += count;
        } else if (type.isEdible()) {
            s.foodStock += count;
        } else if (type == ItemType.LOG || type == ItemType.PLANK) {
            s.woodStock += count;
        } else if (isMetalSupply(type)) {
            s.metalStock += count;
        }
    }

    private static void removeSupplyStock(Settlement s, ItemType type, int count) {
        if (type == null || count <= 0) {
            return;
        }
        if (type == ItemType.MEDICINE) {
            s.medStock = Math.max(0, s.medStock - count);
        } else if (type.isEdible()) {
            s.foodStock = Math.max(0, s.foodStock - count);
        } else if (type == ItemType.LOG || type == ItemType.PLANK) {
            s.woodStock = Math.max(0, s.woodStock - count);
        } else if (isMetalSupply(type)) {
            s.metalStock = Math.max(0, s.metalStock - count);
        }
    }

    private static boolean isMetalSupply(ItemType type) {
        return type == ItemType.SCRAP || type == ItemType.IRON_ORE
                || type == ItemType.COPPER_ORE || type == ItemType.IRON_INGOT
                || type == ItemType.COPPER_INGOT;
    }

    // ------------------------------------------------------------------
    // Ticks
    // ------------------------------------------------------------------

    /** Fast tick (20 Hz): gates plus bounded active/abstract counterattacks. */
    public void fastTick(Game g, float dt) {
        tickGates(g, dt);
        counterattacks.tick(g, dt);
        tickRestrictedAreas(g, dt);
    }

    private void tickRestrictedAreas(Game g, float dt) {
        if (g.player == null) {
            return;
        }
        for (Settlement s : g.world.settlements.values()) {
            s.trespassCooldown = Math.max(0f, s.trespassCooldown - dt);
            s.restrictedStorageCooldown = Math.max(0f, s.restrictedStorageCooldown - dt);
        }
        Settlement current = g.world.settlementAt((int) Math.floor(g.player.pos.x),
                (int) Math.floor(g.player.pos.z));
        if (current != null && current.trespassCooldown <= 0
                && isRestrictedArea(g, current, g.player.pos.x, g.player.pos.z)) {
            current.trespassCooldown = TRESPASS_INTERVAL;
            addLocalReputation(g, current, -5f,
                    "Residents warn you out of a restricted area");
            current.alertLevel = Math.min(100f, current.alertLevel + 10f);
        }
    }

    /** Slow tick (~10 s): discovery, activation, dormant sim, parties. */
    public void slowTick(Game g, float dt) {
        float px = g.player.pos.x, pz = g.player.pos.z;

        // Activation can lazily generate a layout/chunk, which may register more
        // regional settlements. Iterate a stable snapshot so discovery work never
        // mutates the map underneath this tick.
        for (Settlement s : new ArrayList<>(g.world.settlements.values())) {
            double dist = Math.sqrt(s.distSqTo(px, pz));

            // Discovery.
            if (!s.discovered && dist < DISCOVER_RADIUS + s.radius) {
                s.discovered = true;
                g.audio.playDiscover();
                g.log("DISCOVERED: " + s.label() + " (marked on your map)");
                g.faction.onSettlementDiscovered(g, s);
                revealFortressRumors(g, s);
            }

            // Activation / deactivation with hysteresis.
            boolean anyLive = anyLiveResident(s);
            if (dist < ACTIVATE_RADIUS && !anyLive && (!s.cleared || s.occupied)) {
                activate(g, s);
                anyLive = anyLiveResident(s);
            } else if (dist > DEACTIVATE_RADIUS && anyLive) {
                deactivate(g, s);
                anyLive = false;
            }

            // Never double-simulate active residents and their dormant records.
            if (!anyLive) {
                dormantSim(g, s, dt);
            }

            // Occupied outposts face counterattacks.
            if (s.occupied && s.counterattackTimer > 0) {
                s.counterattackTimer -= dt;
                if (s.counterattackTimer <= 0) {
                    launchCounterattack(g, s, dist);
                }
            }

            s.alertLevel = Math.max(0, s.alertLevel - 2.5f * dt);
        }

        tickBountyParties(g, dt);
    }

    private boolean anyLiveResident(Settlement s) {
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Activation / dormancy
    // ------------------------------------------------------------------

    /** Materializes dormant residents as live NPCs at their beds/duty points. */
    private void activate(Game g, Settlement s) {
        // Ensure the layout metadata exists (also builds beds/duty lists).
        g.world.layoutFor(s);
        for (int i = 0; i < s.residents.size(); i++) {
            Settlement.Resident r = s.residents.get(i);
            if (!r.alive || r.rescued || r.routed || r.surrendered || r.live != null) {
                continue;
            }
            // Re-evaluate after every spawn. Parties and legacy event NPCs share
            // this same global count and therefore cannot bypass the budget.
            if (!canSpawnNpc(g, NpcCategory.SETTLEMENT_RESIDENT)) {
                break;
            }
            Vec3i spawn = spawnPointFor(g, s, r);
            boolean specialPost = r.archetype == NpcArchetype.CAPTIVE
                    || r.archetype.leader || s.beds.isEmpty();
            float spawnY = spawn.y() + (specialPost ? 0.4f : 0.02f);
            Npc n = g.entities.spawnNpc(g.world, r.name,
                    spawn.x() + 0.5f, spawnY, spawn.z() + 0.5f);
            n.archetype = r.archetype;
            n.settlementId = s.id;
            n.residentIndex = i;
            n.maxHealth = r.archetype.maxHealth;
            n.health = Math.min(r.health, r.archetype.maxHealth);
            n.yaw = rng.nextFloat() * 360;
            // Settlement traders reuse the existing trade screen.
            n.isTrader = r.archetype == NpcArchetype.TRADER;
            n.sick = r.sick;
            n.sickTimer = r.sicknessTimer;
            n.hunger = r.hunger;
            r.live = n;
        }
    }

    private Vec3i spawnPointFor(Game g, Settlement s, Settlement.Resident r) {
        if (r.archetype == NpcArchetype.CAPTIVE && s.prisonPos != null) {
            return s.prisonPos;
        }
        if (r.archetype.leader && s.leaderPost != null) {
            return s.leaderPost;
        }
        if (!s.beds.isEmpty()) {
            return residentSleepPosition(g, s, r);
        }
        return s.center;
    }

    /**
     * Stable standable sleep cell associated with a resident's persisted bed
     * index. The bed block itself is solid and is never a valid entity origin.
     */
    public Vec3i residentSleepPosition(Game g, Settlement s, Settlement.Resident resident) {
        if (s.beds.isEmpty()) {
            return s.center;
        }
        Vec3i bed = s.beds.get(Math.floorMod(resident.bedIndex, s.beds.size()));
        int[][] offsets = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] offset : offsets) {
            int x = bed.x() + offset[0], z = bed.z() + offset[1];
            if (Pathfinder.standable(g.world, x, bed.y(), z)) {
                return new Vec3i(x, bed.y(), z);
            }
        }
        if (Pathfinder.standable(g.world, bed.x(), bed.y() + 1, bed.z())) {
            return bed.offset(0, 1, 0);
        }
        return bed.offset(0, 1, 0);
    }

    /** Writes live state back into resident records and despawns entities. */
    public void deactivate(Game g, Settlement s) {
        for (Settlement.Resident r : s.residents) {
            Npc n = r.live;
            if (n == null) {
                continue;
            }
            r.health = n.health;
            r.alive = !n.dead;
            r.sick = n.sick;
            r.sicknessTimer = n.sickTimer;
            r.hunger = n.hunger;
            r.live = null;
            g.entities.npcs.remove(n);
        }
    }

    /** Rebinds live NPC references after a save load and clamps malformed/old over-cap saves. */
    public void onWorldLoaded(Game g) {
        for (Settlement settlement : g.world.settlements.values()) {
            for (Settlement.Resident resident : settlement.residents) {
                resident.live = null;
            }
        }

        Set<String> boundResidents = new HashSet<>();
        for (Iterator<Npc> it = g.entities.npcs.iterator(); it.hasNext(); ) {
            Npc n = it.next();
            if (n.dead) {
                it.remove();
                continue;
            }
            if (!n.settled()) {
                continue;
            }
            Settlement s = g.world.settlements.get(n.settlementId);
            String key = n.settlementId + ":" + n.residentIndex;
            if (s == null || n.residentIndex < 0 || n.residentIndex >= s.residents.size()
                    || !boundResidents.add(key)) {
                it.remove();
            }
        }

        // Category limits first, then the shared non-resident and global limits.
        trimLoadedNpcs(g, n -> n.settled(), MAX_ACTIVE_RESIDENTS);
        trimLoadedNpcs(g, n -> categoryOf(n) == NpcCategory.PATROL, MAX_ACTIVE_PATROL_NPCS);
        trimLoadedNpcs(g, n -> categoryOf(n) == NpcCategory.BOUNTY_HUNTER,
                MAX_ACTIVE_BOUNTY_HUNTERS);
        trimLoadedNpcs(g, n -> categoryOf(n) == NpcCategory.COUNTERATTACK,
                MAX_ACTIVE_COUNTERATTACKERS);
        trimLoadedNpcs(g, n -> n.warParty, MAX_ACTIVE_WAR_PARTY_NPCS);
        trimLoadedNpcs(g, n -> categoryOf(n) == NpcCategory.LEGACY, MAX_ACTIVE_LEGACY_NPCS);
        trimLoadedNpcs(g, n -> !n.settled(), MAX_ACTIVE_NON_RESIDENT_NPCS);
        int residentCount = npcCounts(g).residents();
        // Keep the same reservation after load. Counterattack members are
        // excluded from this trim and occupy their own eight slots above it.
        trimLoadedNpcs(g, n -> !n.settled()
                        && categoryOf(n) != NpcCategory.COUNTERATTACK,
                Math.max(0, MAX_ACTIVE_NPCS - COUNTERATTACK_SLOT_RESERVE - residentCount));

        for (Npc n : g.entities.npcs) {
            if (n.settled()) {
                Settlement s = g.world.settlements.get(n.settlementId);
                if (s != null && n.residentIndex >= 0 && n.residentIndex < s.residents.size()) {
                    s.residents.get(n.residentIndex).live = n;
                }
            }
        }
        counterattacks.reconcileLoaded(g);
    }

    private void trimLoadedNpcs(Game g, Predicate<Npc> filter, int limit) {
        int count = 0;
        for (Npc npc : g.entities.npcs) {
            if (!npc.dead && filter.test(npc)) {
                count++;
            }
        }
        if (count <= limit) {
            return;
        }
        // Preserve earlier serialized entities deterministically. Residents
        // removed here remain represented by their dormant resident record.
        ListIterator<Npc> it = g.entities.npcs.listIterator(g.entities.npcs.size());
        while (it.hasPrevious() && count > limit) {
            Npc npc = it.previous();
            if (!npc.dead && filter.test(npc)) {
                it.remove();
                count--;
            }
        }
    }

    /** Measures the actual combined active NPC population. Dead entities awaiting cleanup are excluded. */
    public NpcCounts npcCounts(Game g) {
        int total = 0;
        int residents = 0;
        int parties = 0;
        int patrols = 0;
        int bounty = 0;
        int counterattack = 0;
        int legacy = 0;
        for (Npc npc : g.entities.npcs) {
            if (npc.dead) {
                continue;
            }
            total++;
            NpcCategory category = categoryOf(npc);
            switch (category) {
                case SETTLEMENT_RESIDENT -> residents++;
                case PATROL -> {
                    parties++;
                    patrols++;
                }
                case BOUNTY_HUNTER -> {
                    parties++;
                    bounty++;
                }
                case COUNTERATTACK -> {
                    parties++;
                    counterattack++;
                }
                case LEGACY -> legacy++;
            }
        }
        return new NpcCounts(total, residents, parties, patrols, bounty, counterattack, legacy);
    }

    /** Classifies an NPC without relying on resident-index sentinel values for parties. */
    public NpcCategory categoryOf(Npc npc) {
        if (npc.settled()) {
            return NpcCategory.SETTLEMENT_RESIDENT;
        }
        if (npc.warParty) {
            Npc.PartyKind kind = npc.partyKind == null ? Npc.PartyKind.PATROL : npc.partyKind;
            return switch (kind) {
                case PATROL -> NpcCategory.PATROL;
                case BOUNTY_HUNTER -> NpcCategory.BOUNTY_HUNTER;
                case COUNTERATTACK -> NpcCategory.COUNTERATTACK;
            };
        }
        return NpcCategory.LEGACY;
    }

    /** True when one more live NPC of the requested category fits every applicable limit. */
    public boolean canSpawnNpc(Game g, NpcCategory category) {
        if (category == null) {
            return false;
        }
        NpcCounts c = npcCounts(g);
        int globalLimit = category == NpcCategory.COUNTERATTACK
                ? MAX_ACTIVE_NPCS
                : MAX_ACTIVE_NPCS - Math.max(0,
                COUNTERATTACK_SLOT_RESERVE - c.counterattackers());
        if (c.total() >= globalLimit) {
            return false;
        }
        if (category == NpcCategory.SETTLEMENT_RESIDENT) {
            return c.residents() < MAX_ACTIVE_RESIDENTS;
        }
        if (c.nonResidents() >= MAX_ACTIVE_NON_RESIDENT_NPCS) {
            return false;
        }
        return switch (category) {
            case SETTLEMENT_RESIDENT -> true;
            case PATROL -> c.warParties() < MAX_ACTIVE_WAR_PARTY_NPCS
                    && c.patrols() < MAX_ACTIVE_PATROL_NPCS;
            case BOUNTY_HUNTER -> c.warParties() < MAX_ACTIVE_WAR_PARTY_NPCS
                    && c.bountyHunters() < MAX_ACTIVE_BOUNTY_HUNTERS;
            case COUNTERATTACK -> c.warParties() < MAX_ACTIVE_WAR_PARTY_NPCS
                    && c.counterattackers() < MAX_ACTIVE_COUNTERATTACKERS;
            case LEGACY -> c.legacy() < MAX_ACTIVE_LEGACY_NPCS;
        };
    }

    /** Number that can be admitted now; used to keep dispatch decisions deterministic and bounded. */
    public int availableNpcCapacity(Game g, NpcCategory category) {
        int available = 0;
        // Constants are small; measuring after a hypothetical spawn would be
        // overkill, so derive the exact intersection of applicable caps.
        NpcCounts c = npcCounts(g);
        if (category == null) {
            return 0;
        }
        int globalLimit = category == NpcCategory.COUNTERATTACK
                ? MAX_ACTIVE_NPCS
                : MAX_ACTIVE_NPCS - Math.max(0,
                COUNTERATTACK_SLOT_RESERVE - c.counterattackers());
        if (c.total() >= globalLimit) {
            return 0;
        }
        int global = globalLimit - c.total();
        if (category == NpcCategory.SETTLEMENT_RESIDENT) {
            return Math.max(0, Math.min(global, MAX_ACTIVE_RESIDENTS - c.residents()));
        }
        int nonResident = MAX_ACTIVE_NON_RESIDENT_NPCS - c.nonResidents();
        available = Math.min(global, nonResident);
        available = switch (category) {
            case PATROL -> Math.min(available, Math.min(
                    MAX_ACTIVE_WAR_PARTY_NPCS - c.warParties(),
                    MAX_ACTIVE_PATROL_NPCS - c.patrols()));
            case BOUNTY_HUNTER -> Math.min(available, Math.min(
                    MAX_ACTIVE_WAR_PARTY_NPCS - c.warParties(),
                    MAX_ACTIVE_BOUNTY_HUNTERS - c.bountyHunters()));
            case COUNTERATTACK -> Math.min(available, Math.min(
                    MAX_ACTIVE_WAR_PARTY_NPCS - c.warParties(),
                    MAX_ACTIVE_COUNTERATTACKERS - c.counterattackers()));
            case LEGACY -> Math.min(available, MAX_ACTIVE_LEGACY_NPCS - c.legacy());
            case SETTLEMENT_RESIDENT -> available;
        };
        return Math.max(0, available);
    }

    // ------------------------------------------------------------------
    // Dormant simulation
    // ------------------------------------------------------------------

    /** Abstract jobs/stocks/population for settlements without live entities. */
    private void dormantSim(Game g, Settlement s, float dt) {
        int pop = s.aliveResidents();
        if (pop <= 0 && !s.occupied) {
            return;
        }
        s.dormantAccumulator += dt;
        int steps = 0;
        while (s.dormantAccumulator >= 60f && steps++ < 20) {
            s.dormantAccumulator -= 60f;
            simulateDormantMinute(g, s);
        }

        // Population replenishment: capacity + food + time + not cleared.
        s.replenishTimer -= dt;
        if (s.replenishTimer <= 0) {
            s.replenishTimer = 420 + rng.nextInt(300);
            int security = 0;
            for (Settlement.Resident r : s.residents) {
                if (r.alive && !r.rescued && !r.routed && !r.surrendered
                        && (r.archetype == NpcArchetype.GUARD
                        || r.archetype == NpcArchetype.ARCHER
                        || r.archetype.hostileArchetype())) {
                    security++;
                }
            }
            if (!s.cleared && pop > 0 && pop < s.type.maxPopulation
                    && s.foodStock >= 6 && s.medStock >= 1 && s.morale > 35
                    && s.alertLevel < 60 && security > 0) {
                Settlement.Resident template = null;
                for (Settlement.Resident r : s.residents) {
                    if (r.alive && !r.rescued && !r.archetype.leader) {
                        template = r;
                        break;
                    }
                }
                if (template != null) {
                    Settlement.Resident newcomer = new Settlement.Resident(
                            template.name + " kin", template.archetype);
                    newcomer.bedIndex = s.residents.size();
                    newcomer.dutyIndex = s.residents.size();
                    s.residents.add(newcomer);
                    s.foodStock -= 3;
                }
            }
        }
    }

    private void simulateDormantMinute(Game g, Settlement s) {
        s.dormantStep++;
        int pop = s.aliveResidents();
        int farmers = 0, workers = 0, medics = 0, smiths = 0;
        for (Settlement.Resident resident : s.residents) {
            if (!resident.alive || resident.rescued || resident.routed || resident.surrendered) {
                continue;
            }
            switch (resident.archetype) {
                case FARMER -> farmers++;
                case MEDIC -> medics++;
                case SMITH -> smiths++;
                case VILLAGER, GUARD, ARCHER, TRADER -> workers++;
                default -> {
                }
            }
        }
        // Role-appropriate fixed-step outputs, all capped.
        s.foodStock = Math.min(999, s.foodStock + Math.max(0, farmers));
        s.woodStock = Math.min(999, s.woodStock + workers / 3);
        if (s.woodStock > 0 && smiths > 0 && s.dormantStep % 3 == 0) {
            s.woodStock--;
            s.metalStock = Math.min(999, s.metalStock + smiths);
        }
        if (medics > 0 && s.foodStock > 0 && s.dormantStep % 4 == 0) {
            s.foodStock--;
            s.medStock = Math.min(999, s.medStock + 1);
        }

        if (s.dormantStep % 2 == 0) {
            int meals = Math.max(1, (pop + 5) / 6);
            if (s.foodStock >= meals) {
                s.foodStock -= meals;
                for (Settlement.Resident resident : s.residents) {
                    if (resident.alive) {
                        resident.hunger = Math.max(0, resident.hunger - 35);
                    }
                }
                s.morale = MathUtil.clamp(s.morale + 0.5f, 0,
                        s.leaderResident() != null ? 95 : 80);
            } else {
                s.foodStock = 0;
                s.morale = Math.max(0, s.morale - 3f);
                for (Settlement.Resident resident : s.residents) {
                    if (resident.alive) {
                        resident.hunger = Math.min(100, resident.hunger + 20);
                    }
                }
            }
        }

        for (int i = 0; i < s.residents.size(); i++) {
            Settlement.Resident resident = s.residents.get(i);
            if (!resident.alive || resident.rescued || resident.routed || resident.surrendered) {
                continue;
            }
            long rollBits = com.veylon.util.Noise.mix(g.world.seed ^ s.id
                    ^ (s.dormantStep * 0x9E3779B97F4A7C15L) ^ i);
            float roll = (rollBits >>> 40) / (float) (1 << 24);
            if (!resident.sick) {
                float risk = (s.foodStock == 0 ? 0.025f : 0.002f)
                        + (s.medStock == 0 ? 0.006f : 0f)
                        + (s.alertLevel > 70 ? 0.004f : 0f);
                if (roll < risk) {
                    resident.sick = true;
                    resident.sicknessTimer = 0;
                }
                continue;
            }
            resident.sicknessTimer += 60;
            if (s.medStock > 0 && medics > 0 && roll < 0.45f) {
                s.medStock--;
                resident.sick = false;
                resident.sicknessTimer = 0;
                resident.health = Math.min(resident.archetype.maxHealth, resident.health + 8);
            } else if (resident.sicknessTimer > 600) {
                resident.health -= s.foodStock == 0 ? 3f : 1.2f;
                if (resident.health <= 0) {
                    resident.health = 0;
                    resident.alive = false;
                    s.morale = Math.max(0, s.morale - 8);
                }
            }
        }
        if (s.alertLevel > 70) {
            s.morale = Math.max(0, s.morale - 1);
        }
    }

    // ------------------------------------------------------------------
    // Capture / occupation / counterattack
    // ------------------------------------------------------------------

    /** Evaluates the cleared condition after hostile deaths or routs. */
    public void checkCleared(Game g, Settlement s) {
        if (s.cleared || !s.hostile()) {
            return;
        }
        Settlement.Resident leader = s.leaderResident();
        boolean leaderDown = leader == null || !leader.alive;
        boolean stagedObjective = s.type == SettlementType.FORT
                || s.type == SettlementType.CASTLE || s.type == SettlementType.FORTRESS;
        if (leaderDown) {
            s.commandNeutralized = true;
        }
        if (s.alarmBell == null || g.world.getBlock(s.alarmBell.x(), s.alarmBell.y(),
                s.alarmBell.z()) != BlockType.ALARM_BELL) {
            s.alarmNeutralized = true;
        }
        int defenders = 0;
        for (Settlement.Resident r : s.residents) {
            if (s.countsAsDefender(r) && !r.archetype.leader) {
                defenders++;
            }
        }
        boolean routed = s.morale < 15 && defenders <= 2;
        if (leaderDown && routed) {
            // These survivors explicitly rout rather than silently becoming dead.
            for (Settlement.Resident r : s.residents) {
                if (s.countsAsDefender(r)) {
                    r.routed = true;
                    if (r.live != null) {
                        // EntityManager owns its iterator; flag for deferred removal
                        // instead of structurally modifying the live list here.
                        r.live.dead = true;
                        r.live.lastHitByPlayer = false;
                        r.live = null;
                    }
                }
            }
            defenders = 0;
        }
        boolean objectivesReady = !stagedObjective
                || (s.commandNeutralized && s.alarmNeutralized && s.centralObjectiveControlled);
        if (leaderDown && defenders == 0 && objectivesReady) {
            s.cleared = true;
            s.alertLevel = 0;
            g.log("=== The " + s.type.displayName.toLowerCase() + " has been CLEARED. ===");
            g.log("Supply it with " + OCCUPY_FOOD + " food and " + OCCUPY_WOOD
                    + " wood at the campfire to claim it as an outpost.");
            g.audio.playQuest();
            addReputation(g, HumanFaction.FRONTIER, 12,
                    "Word spreads of the cleared " + s.type.displayName.toLowerCase());
            g.world.factionBounty.merge(s.factionId, -25f, (a, b) -> Math.max(0, a + b));
            g.faction.onSettlementCleared(g, s);
        }
    }

    public void onAlarmSabotaged(Game g, Settlement s) {
        if (s == null || !s.hostile()) {
            return;
        }
        s.alarmNeutralized = true;
        s.alertLevel = Math.min(s.alertLevel, 45f);
        g.faction.onAlarmSabotaged(g, s);
        checkCleared(g, s);
    }

    /** Player reaches and holds the central campfire/command objective. */
    public boolean controlCentralObjective(Game g, Settlement s) {
        if (s == null || !s.hostile()) {
            return false;
        }
        s.centralObjectiveControlled = true;
        g.log("Central objective secured. Neutralize command, alarm and remaining defenders.");
        checkCleared(g, s);
        return true;
    }

    /** Player supplies a cleared settlement and claims it. */
    public boolean occupy(Game g, Settlement s) {
        if (!s.cleared || s.occupied) {
            return false;
        }
        var inv = g.player.inventory;
        int foodHave = inv.count(ItemType.COOKED_MEAT) + inv.count(ItemType.DRIED_MEAT)
                + inv.count(ItemType.BERRY) + inv.count(ItemType.DRIED_BERRY);
        if (foodHave < OCCUPY_FOOD || inv.count(ItemType.LOG) < OCCUPY_WOOD) {
            g.log("Claiming this outpost needs " + OCCUPY_FOOD + " food items and "
                    + OCCUPY_WOOD + " logs.");
            return false;
        }
        int need = OCCUPY_FOOD;
        for (ItemType t : new ItemType[]{ItemType.COOKED_MEAT, ItemType.DRIED_MEAT,
                ItemType.BERRY, ItemType.DRIED_BERRY}) {
            int take = Math.min(need, inv.count(t));
            inv.remove(t, take);
            need -= take;
            if (need <= 0) {
                break;
            }
        }
        inv.remove(ItemType.LOG, OCCUPY_WOOD);
        s.occupied = true;
        s.alignment = Settlement.Alignment.FRIENDLY;
        s.factionId = HumanFaction.FRONTIER;
        s.foodStock += OCCUPY_FOOD;
        s.woodStock += OCCUPY_WOOD;
        s.counterattackTimer = 700 + rng.nextInt(500);
        // A small frontier garrison moves in.
        for (int i = 0; i < 2; i++) {
            Settlement.Resident guard = new Settlement.Resident(
                    (i == 0 ? "Bram" : "Tilda"), NpcArchetype.GUARD);
            guard.bedIndex = i;
            guard.dutyIndex = i;
            s.residents.add(guard);
        }
        Settlement.Resident trader = new Settlement.Resident("Mara", NpcArchetype.TRADER);
        trader.bedIndex = 2;
        trader.dutyIndex = 2;
        s.residents.add(trader);
        g.log("=== OUTPOST CLAIMED. Frontier settlers garrison the "
                + s.type.displayName.toLowerCase() + ". ===");
        g.log("You can sleep, store goods and resupply here. Expect the "
                + HumanFaction.displayName(s.founderFaction) + " to strike back.");
        g.audio.playQuest();
        addReputation(g, HumanFaction.FRONTIER, 10, null);
        g.faction.onFortCaptured(g, s);
        return true;
    }

    /** Schedules a group-level mission; the occupied outpost is always its destination. */
    private void launchCounterattack(Game g, Settlement s, double playerDist) {
        s.counterattackTimer = 900 + rng.nextInt(600);
        CounterattackMission mission = counterattacks.dispatch(g, s, 3 + rng.nextInt(4));
        if (mission == null) {
            // The director is deliberately bounded. Retry later rather than
            // growing mission state or resolving an attack without an identity.
            s.counterattackTimer = Math.min(s.counterattackTimer, 120f);
        }
    }

    /** Production/QA seam used by occupation scheduling and integration tests. */
    public CounterattackMission dispatchCounterattack(Game g, Settlement target,
                                                       int requestedAttackers) {
        return counterattacks.dispatch(g, target, requestedAttackers);
    }

    /**
     * Spawns a categorized traveling party at a real settlement gate. The
     * requested size is clamped against the global, shared-party and category
     * budgets, re-measured after every entity is admitted.
     */
    public List<Npc> spawnParty(Game g, Settlement s, int requestedCount, String factionId,
                               Vec3i destination, Npc.PartyKind kind, String missionId,
                               long targetSettlementId) {
        return spawnParty(g, s, requestedCount, factionId, destination, kind,
                missionId, targetSettlementId, null);
    }

    /** Explicit-position materialization for persistent missions; never generates the spawn chunk. */
    public List<Npc> spawnParty(Game g, Settlement s, int requestedCount, String factionId,
                               Vec3i destination, Npc.PartyKind kind, String missionId,
                               long targetSettlementId, Vec3i explicitSpawn) {
        List<Npc> party = new ArrayList<>();
        if (g == null || s == null || destination == null || requestedCount <= 0) {
            return party;
        }
        if (kind == null) {
            kind = Npc.PartyKind.PATROL;
        }
        String effectiveMissionId = missionId;
        if (effectiveMissionId == null || effectiveMissionId.isBlank()) {
            do {
                effectiveMissionId = "party:" + kind.name().toLowerCase(java.util.Locale.ROOT)
                        + ":" + Long.toUnsignedString(s.id, 16) + ":"
                        + Long.toUnsignedString(++partySequence, 16);
            } while (partyMissionExists(g, effectiveMissionId));
        }
        int memberSequence = 0;
        for (Npc existing : g.entities.npcs) {
            if (effectiveMissionId.equals(existing.partyMissionId)) {
                memberSequence++;
            }
        }
        NpcCategory category = partyCategory(kind);
        Vec3i origin;
        if (explicitSpawn != null) {
            origin = explicitSpawn;
        } else {
            // Layout metadata supplies the real gate even when the distant
            // chunks have not been materialized yet.
            g.world.layoutFor(s);
            origin = s.gates.isEmpty() ? s.center : s.gates.getFirst();
        }
        for (int i = 0; i < requestedCount; i++) {
            if (!canSpawnNpc(g, category)) {
                break;
            }
            NpcArchetype a = switch (rng.nextInt(4)) {
                case 0 -> NpcArchetype.SCOUT;
                case 1 -> NpcArchetype.TRACKER;
                case 2 -> NpcArchetype.BRUTE;
                default -> NpcArchetype.HUNTER;
            };
            if (HumanFaction.SCAVENGERS.equals(factionId)) {
                a = NpcArchetype.SCAVENGER;
            }
            int gx = origin.x() + rng.nextInt(5) - 2;
            int gz = origin.z() + rng.nextInt(5) - 2;
            // Do not generate a distant chunk merely to obtain a Y coordinate.
            // The deterministic layout gate already carries the correct floor.
            int gy = origin.y();
            Npc n = g.entities.spawnNpc(g.world, a.displayName, gx + 0.5f, gy + 0.4f, gz + 0.5f);
            n.archetype = a;
            n.settlementId = s.id;
            n.residentIndex = -1;
            n.warParty = true;
            n.partyKind = kind;
            n.partyMissionId = effectiveMissionId;
            n.partyMemberId = effectiveMissionId + ":member:" + memberSequence++;
            n.partyTargetSettlementId = targetSettlementId;
            n.partyFactionId = factionId;
            n.originSettlementId = s.id;
            n.partyMission = Npc.PartyMission.OUTBOUND;
            n.partyDestination.set(destination.x() + 0.5f, destination.y(), destination.z() + 0.5f);
            n.partyMissionTimer = 75f;
            n.partyContact = false;
            n.maxHealth = a.maxHealth;
            n.health = a.maxHealth;
            n.lastKnownAge = 999f;
            n.searchTimer = 0;
            n.abstractTravel = g.world.getChunk(Math.floorDiv(gx, 16), Math.floorDiv(gz, 16)) == null;
            party.add(n);
        }
        return party;
    }

    private boolean partyMissionExists(Game g, String missionId) {
        for (Npc npc : g.entities.npcs) {
            if (missionId.equals(npc.partyMissionId)) {
                return true;
            }
        }
        return counterattacks.get(missionId) != null;
    }

    /** Deterministic gameplay/QA seam for dispatching a real-origin patrol. */
    public List<Npc> dispatchWarParty(Game g, Settlement origin, int count,
                                     String factionId, Vec3i destination) {
        return spawnParty(g, origin, count, factionId, destination,
                Npc.PartyKind.PATROL, "", 0L);
    }

    private NpcCategory partyCategory(Npc.PartyKind kind) {
        return switch (kind) {
            case PATROL -> NpcCategory.PATROL;
            case BOUNTY_HUNTER -> NpcCategory.BOUNTY_HUNTER;
            case COUNTERATTACK -> NpcCategory.COUNTERATTACK;
        };
    }

    // ------------------------------------------------------------------
    // Bounty hunter parties
    // ------------------------------------------------------------------

    /** High bounty sends hunter parties out from real hostile settlements. */
    private void tickBountyParties(Game g, float dt) {
        bountyPartyCooldown -= dt;
        if (bountyPartyCooldown > 0) {
            return;
        }
        bountyPartyCooldown = 240 + rng.nextInt(240);
        float bounty = g.world.factionBounty.getOrDefault(HumanFaction.HEADHUNTERS, 0f);
        if (bounty < 40 || rng.nextFloat() > bounty / 130f) {
            return;
        }
        // The party must originate from a real hostile settlement in range —
        // never on top of the player.
        Settlement origin = null;
        double bestDist = Double.MAX_VALUE;
        for (Settlement s : g.world.settlements.values()) {
            if (!s.hostile() || !HumanFaction.HEADHUNTERS.equals(s.factionId)) {
                continue;
            }
            double d = Math.sqrt(s.distSqTo(g.player.pos.x, g.player.pos.z));
            if (d > 60 && d < 320 && d < bestDist) {
                origin = s;
                bestDist = d;
            }
        }
        if (origin != null) {
            int size = 2 + (int) (bounty / 40f);
            g.log("You feel watched... a hunting party has picked up your trail.");
            spawnParty(g, origin, size, HumanFaction.HEADHUNTERS,
                    new Vec3i((int) g.player.pos.x, (int) g.player.pos.y, (int) g.player.pos.z),
                    Npc.PartyKind.BOUNTY_HUNTER, "", 0L);
        }
    }

    // ------------------------------------------------------------------
    // Rescue & alarm & gates
    // ------------------------------------------------------------------

    /** Whether this entity still represents a live, unrescued captive record. */
    public boolean canRescueCaptive(Game g, Npc captive) {
        if (captive == null || captive.dead || captive.archetype != NpcArchetype.CAPTIVE
                || !captive.settled() || !g.entities.npcs.contains(captive)) {
            return false;
        }
        Settlement s = g.world.settlements.get(captive.settlementId);
        if (s == null || captive.residentIndex < 0
                || captive.residentIndex >= s.residents.size()) {
            return false;
        }
        Settlement.Resident r = s.residents.get(captive.residentIndex);
        return r.archetype == NpcArchetype.CAPTIVE && r.alive && !r.rescued
                && r.name.equals(captive.name) && (r.live == null || r.live == captive);
    }

    /**
     * Frees a captive exactly once: reputation and population rewards are part
     * of the same guarded transition, and the captive leaves the active scene.
     */
    public boolean rescueCaptive(Game g, Npc captive) {
        if (!canRescueCaptive(g, captive)) {
            return false;
        }
        Settlement s = g.world.settlements.get(captive.settlementId);
        Settlement.Resident r = s.residents.get(captive.residentIndex);
        r.rescued = true;
        r.live = null;
        g.entities.npcs.remove(captive);
        g.log(captive.name + " is free! \"I owe you my life. I'll make for friendly walls.\"");
        g.audio.playQuest();
        g.faction.onCaptiveRescued(g, s, captive.residentIndex);
        addReputation(g, HumanFaction.FRONTIER, 15, "Word of the rescue spreads");
        addReputation(g, HumanFaction.FREE_SETTLERS, 10, null);
        // The rescued settler joins the nearest friendly settlement's population.
        Settlement home = null;
        double best = Double.MAX_VALUE;
        for (Settlement other : g.world.settlements.values()) {
            if (other.friendly() && !other.cleared) {
                double d = other.distSqTo(s.center.x(), s.center.z());
                if (d < best) {
                    best = d;
                    home = other;
                }
            }
        }
        if (home != null && home.aliveResidents() < home.type.maxPopulation) {
            Settlement.Resident settled = new Settlement.Resident(r.name, NpcArchetype.VILLAGER);
            settled.bedIndex = home.residents.size();
            settled.dutyIndex = home.residents.size();
            home.residents.add(settled);
        }
        return true;
    }

    /** Rings a settlement's bell: full alert. Sabotage (breaking it) prevents this. */
    public void triggerAlarm(Game g, Settlement s) {
        if (s.alarmBell != null
                && g.world.getBlock(s.alarmBell.x(), s.alarmBell.y(), s.alarmBell.z())
                != BlockType.ALARM_BELL) {
            return; // bell destroyed — no alarm
        }
        if (s.alertLevel < 70) {
            s.alertLevel = 100;
            Vec3i bell = s.alarmBell != null ? s.alarmBell : s.center;
            g.audio.playAlarmBell(bell.x(), bell.y(), bell.z());
            g.noise.emit(g, bell.x(), bell.y(), bell.z(), 90f, 0.9f, "alarm", false, null);
        } else {
            s.alertLevel = 100;
        }
    }

    /** Opens a gate block for a few seconds (NPC or player passage). */
    public void openGate(Game g, Vec3i pos) {
        if (g.world.getBlock(pos.x(), pos.y(), pos.z()) != BlockType.GATE) {
            return;
        }
        g.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.GATE_OPEN, true);
        g.world.gateTimers.put(pos, GATE_OPEN_SECONDS);
        g.audio.playGate(pos.x() + 0.5f, pos.y(), pos.z() + 0.5f);
    }

    /** Ticks gate auto-close timers. */
    public void tickGates(Game g, float dt) {
        if (g.world.gateTimers.isEmpty()) {
            return;
        }
        var it = g.world.gateTimers.entrySet().iterator();
        while (it.hasNext()) {
            var e = it.next();
            float left = e.getValue() - dt;
            if (left <= 0) {
                Vec3i p = e.getKey();
                if (g.world.getBlock(p.x(), p.y(), p.z()) != BlockType.GATE_OPEN) {
                    it.remove(); // destroyed/replaced gate: discard stale timer
                } else if (entityIn(g, p)) {
                    // Updating the existing entry is iterator-safe; inserting after
                    // iterator.remove() caused ConcurrentModificationException.
                    e.setValue(1.5f);
                } else {
                    it.remove();
                    g.world.setBlock(p.x(), p.y(), p.z(), BlockType.GATE, true);
                    g.audio.playGate(p.x() + 0.5f, p.y(), p.z() + 0.5f);
                }
            } else {
                e.setValue(left);
            }
        }
    }

    private boolean entityIn(Game g, Vec3i p) {
        if (aabbIn(g.player.pos.x, g.player.pos.y, g.player.pos.z,
                g.player.width, g.player.height, p)) {
            return true;
        }
        for (Npc n : g.entities.npcs) {
            if (!n.dead && aabbIn(n.pos.x, n.pos.y, n.pos.z, n.width, n.height, p)) {
                return true;
            }
        }
        return false;
    }

    private void revealFortressRumors(Game g, Settlement source) {
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                Settlement candidate = g.world.settlementForRegion(
                        source.regionX + dx, source.regionZ + dz);
                if (candidate != null && candidate.type == SettlementType.FORTRESS
                        && !candidate.discovered) {
                    candidate.rumored = true;
                }
            }
        }
    }

    private static boolean aabbIn(float x, float y, float z, float w, float h, Vec3i p) {
        float hw = w / 2f;
        return x + hw > p.x() && x - hw < p.x() + 1
                && y + h > p.y() && y < p.y() + 1
                && z + hw > p.z() && z - hw < p.z() + 1;
    }

    /** Nearest registered settlement within range of a position, or null. */
    public Settlement nearest(Game g, float x, float z, float range) {
        Settlement best = null;
        double bestD = range * range;
        for (Settlement s : g.world.settlements.values()) {
            double d = s.distSqTo(x, z);
            if (d < bestD) {
                bestD = d;
                best = s;
            }
        }
        return best;
    }
}
