package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.Carcass;
import com.veylon.entity.Npc;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.RackBatch;
import com.veylon.world.Raycaster;
import com.veylon.world.World;

import java.util.Random;

/**
 * What the F key and right mouse button actually do against the world: talking
 * to and rescuing NPCs, harvesting, working stations, and the multi-stage
 * beacon that ends the game.
 *
 * <p>This is the routing layer, not the rules layer. Block breaking and
 * placement live in {@link PlayerBlockActions}, crate transactions in
 * {@link CrateTransactionSystem}, and self-directed item use in
 * {@link PlayerConsumables}. What is here is the priority order between them
 * and the station handlers that have nowhere else to belong.
 *
 * <p>Note the split between {@link #interactWithBlockAt(Vec3i)} and
 * {@link #interact()}: the former is a narrow command over world-state blocks
 * that tests can call directly, while the latter is the full F-key sweep that
 * also considers NPCs, arrows, carcasses and water.
 */
final class WorldInteractions {

    /** Reach for talking to or rescuing an NPC, in blocks. */
    private static final float NPC_RANGE = 3.2f;
    /** Reach for recovering arrows and harvesting carcasses, in blocks. */
    private static final float PICKUP_RANGE = 2.6f;
    /** Raycast distance for drinking from or filling at open water, in blocks. */
    private static final double WATER_REACH = 4.0;
    /** Swing feedback for a secondary action, in seconds. */
    private static final float SECONDARY_SWING_SECONDS = 0.35f;

    // Powder kegs.
    private static final float KEG_FUSE_SECONDS = 5f;
    private static final float KEG_FUSE_NOISE_RADIUS = 12f;
    private static final float KEG_FUSE_NOISE_STRENGTH = 0.4f;

    // Foraging.
    private static final int BERRY_YIELD = 2;
    private static final int HERB_YIELD = 2;

    // Open water.
    private static final float WATER_THIRST = 35f;
    private static final double WATER_POISON_CHANCE = 0.25;
    private static final float MAX_NEED = 100f;

    // Carcasses.
    private static final double CARCASS_BONE_CHANCE = 0.6;
    private static final float HARVEST_SCENT = 0.3f;

    // Campfire.
    /** Seconds of fuel one log adds. */
    private static final float LOG_FUEL_SECONDS = 120f;

    // Drying rack.
    private static final int RACK_BATCH_MAX = 4;

    // Rain collector.
    private static final float COLLECTOR_DRINK_THIRST = 40f;
    /** Litres consumed per waterskin filled or drink taken. */
    private static final float COLLECTOR_LITRES_PER_USE = 1f;

    // Beds and beacon.
    /** Blocks from the camp centre within which camp bed rules apply. */
    private static final int CAMP_BED_RADIUS = 14;
    /** Trust required for the camp to share beacon calibration codes. */
    private static final int BEACON_ALLY_TRUST = 75;
    private static final int BEACON_COPPER_REQUIRED = 3;

    private final Game game;
    /**
     * Outcome rolls for drinking untreated water and for skinning yields.
     * Reseeded per world by {@code Game.reseedSimulation}, like the simulation
     * systems this collaborator sits beside.
     */
    private final Random rng = new Random();

    WorldInteractions(Game game) {
        this.game = game;
    }

    void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    // ------------------------------------------------------------------
    // Secondary action (RMB)
    // ------------------------------------------------------------------

    void rightClick() {
        // Interactable blocks first.
        if (game.targetHit != null) {
            switch (game.targetHit.type()) {
                case WORKBENCH, FURNACE, ANVIL, TANNERY, HERB_STATION, MAP_TABLE -> {
                    game.uiMode = Game.UiMode.CRAFTING;
                    return;
                }
                case CRATE -> {
                    game.openCrateAt(targetPos());
                    return;
                }
                case CAMPFIRE -> {
                    campfireInteract(targetPos());
                    return;
                }
                default -> {
                }
            }
        }

        ItemStack held = game.player.selected();
        if (held == null) {
            return;
        }
        // A short restrained dip gives drinking, medicine, food and placement tactile feedback.
        game.swingTimer = Math.max(game.swingTimer, SECONDARY_SWING_SECONDS);
        if (held.type == ItemType.WATERSKIN_CLEAN || held.type == ItemType.WATERSKIN_DIRTY) {
            game.consumables.drink(held);
            return;
        }
        if (held.type.isMedical()) {
            game.consumables.applyMedical(held);
            return;
        }
        if (held.type.isEquippable()) {
            game.consumables.equipHeld(held);
            return;
        }
        if (held.type.isEdible()) {
            game.consumables.eat(held);
            return;
        }
        BlockType place = held.type.places();
        if (place != null && game.targetHit != null) {
            game.placeSelectedBlockAt(game.targetHit.x() + game.targetHit.nx(),
                    game.targetHit.y() + game.targetHit.ny(),
                    game.targetHit.z() + game.targetHit.nz());
        }
    }

    private Vec3i targetPos() {
        return new Vec3i(game.targetHit.x(), game.targetHit.y(), game.targetHit.z());
    }

    // ------------------------------------------------------------------
    // NPCs
    // ------------------------------------------------------------------

    /** Normal talk/trade gate. Captives use the rescue action and never this UI. */
    boolean canOpenNpcInteraction(Npc npc) {
        return npc != null && !npc.dead && !npc.raider
                && npc.archetype != NpcArchetype.CAPTIVE
                && !npc.hostileToPlayer();
    }

    /** Shared decision used by the HUD prompt and the real interaction command. */
    Game.NpcInteraction npcInteraction(Npc npc) {
        if (npc == null || npc.dead || npc.raider) {
            return Game.NpcInteraction.NONE;
        }
        if (npc.archetype == NpcArchetype.CAPTIVE) {
            if (!game.settlementManager.canRescueCaptive(game, npc)) {
                return Game.NpcInteraction.NONE;
            }
            return clearPathToNpc(npc)
                    ? Game.NpcInteraction.RESCUE_READY : Game.NpcInteraction.RESCUE_BLOCKED;
        }
        return canOpenNpcInteraction(npc)
                ? Game.NpcInteraction.TALK : Game.NpcInteraction.NONE;
    }

    /** Ignores closer hostile guards so a reachable prisoner remains selectable. */
    Npc nearestNpcForInteraction(float range) {
        Npc best = null;
        double bestD = range * range;
        for (Npc candidate : game.entities.npcs) {
            if (npcInteraction(candidate) == Game.NpcInteraction.NONE) {
                continue;
            }
            double d = candidate.distSqTo(
                    game.player.pos.x, game.player.pos.y, game.player.pos.z);
            if (d < bestD) {
                bestD = d;
                best = candidate;
            }
        }
        return best;
    }

    /** True when no cage bars or walls block the line to an NPC (rescues). */
    private boolean clearPathToNpc(Npc npc) {
        // Player position is authoritative.  The camera is synchronized later in
        // the frame and may legitimately lag during save/load or command tests.
        float ox = game.player.pos.x;
        float oy = game.player.pos.y + game.player.eyeHeight();
        float oz = game.player.pos.z;
        float dx = npc.pos.x - ox, dy = (npc.pos.y + 1.2f) - oy, dz = npc.pos.z - oz;
        float dist = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = Math.max(1, (int) (dist * 2));
        for (int i = 1; i < steps; i++) {
            float f = i / (float) steps;
            if (game.world.getBlock((int) Math.floor(ox + dx * f),
                    (int) Math.floor(oy + dy * f),
                    (int) Math.floor(oz + dz * f)).solid) {
                return false;
            }
        }
        return true;
    }

    /**
     * Gameplay command used by the F-key path and integration tests. Returns
     * true when an NPC action consumed the interaction.
     */
    boolean interactWithNearbyNpc() {
        Npc npc = nearestNpcForInteraction(NPC_RANGE);
        return switch (npcInteraction(npc)) {
            case TALK -> {
                game.activeNpc = npc;
                game.npcScreen.open();
                game.uiMode = Game.UiMode.NPC;
                yield true;
            }
            case RESCUE_BLOCKED -> {
                game.log("The cage bars are in the way — break them first.");
                yield true;
            }
            case RESCUE_READY -> {
                game.settlementManager.rescueCaptive(game, npc);
                yield true;
            }
            case NONE -> false;
        };
    }

    // ------------------------------------------------------------------
    // Primary interaction (F)
    // ------------------------------------------------------------------

    /**
     * Gameplay block-interaction command shared by the real F-key path and
     * integration tests. It deliberately covers world-state interactions;
     * UI-only containers and stations remain on their existing screen path.
     */
    boolean interactWithBlockAt(Vec3i pos) {
        if (pos == null) {
            return false;
        }
        return switch (game.world.getBlock(pos.x(), pos.y(), pos.z())) {
            case CAMPFIRE -> {
                campfireInteract(pos);
                yield true;
            }
            case LANTERN -> interactLantern(pos);
            case POWDER_KEG -> {
                lightKegFuse(pos);
                yield true;
            }
            case GATE -> {
                openGate(pos);
                yield true;
            }
            case ALARM_BELL -> {
                var settlement = game.world.settlementAt(pos.x(), pos.z());
                if (settlement != null) {
                    game.settlementManager.triggerAlarm(game, settlement);
                    game.log("The bell tolls across the settlement.");
                }
                yield true;
            }
            case BEACON -> {
                beaconInteract();
                yield true;
            }
            case BEACON_LIT -> {
                game.log("The beacon thrums steadily, its signal cutting through the sky.");
                yield true;
            }
            default -> false;
        };
    }

    private void lightKegFuse(Vec3i pos) {
        if (game.explosions.tryArmKeg(game, pos, KEG_FUSE_SECONDS, true)) {
            game.audio.playFuse(pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f);
            game.log("Fuse lit! Five seconds — RUN.");
            game.noise.emit(game, pos.x(), pos.y(), pos.z(), KEG_FUSE_NOISE_RADIUS,
                    KEG_FUSE_NOISE_STRENGTH, "fuse", true, game.player);
        } else if (!game.world.kegFuses.containsKey(pos)) {
            game.log("Too many powder-keg fuses are already burning.");
        }
    }

    private void openGate(Vec3i pos) {
        var settlement = game.world.settlementAt(pos.x(), pos.z());
        if (settlement != null && settlement.hostile()) {
            game.log("The gate is barred from the inside.");
            return;
        }
        // Open the complete two-wide/two-high doorway around the hit block.
        game.settlementManager.openGate(game, pos);
        game.settlementManager.openGate(game, pos.offset(0, 1, 0));
        game.settlementManager.openGate(game, pos.offset(0, -1, 0));
        game.settlementManager.openGate(game, pos.offset(1, 0, 0));
        game.settlementManager.openGate(game, pos.offset(-1, 0, 0));
    }

    /**
     * The full F-key sweep. Order matters: an NPC beside a berry bush should be
     * talked to, not harvested past.
     */
    void interact() {
        if (interactWithNearbyNpc() || recoverNearbyArrow() || interactWithNearbyCarcass()) {
            return;
        }
        if (game.targetHit != null && interactWithTargetBlock()) {
            return;
        }
        drinkFromOpenWater();
    }

    /** @return true when the targeted block consumed the interaction */
    private boolean interactWithTargetBlock() {
        Vec3i pos = targetPos();
        switch (game.targetHit.type()) {
            case BERRY_BUSH -> {
                game.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.BERRY_BUSH_EMPTY, true);
                game.player.inventory.add(ItemType.BERRY, BERRY_YIELD);
                game.audio.playEat();
                game.log("Harvested " + BERRY_YIELD + " berries (the bush will regrow).");
            }
            case HERB_PLANT -> {
                game.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.AIR, true);
                game.player.inventory.add(ItemType.HERB, HERB_YIELD);
                game.audio.playEat();
                game.log("Gathered " + HERB_YIELD + " medicinal herbs.");
            }
            case CAMPFIRE, LANTERN, POWDER_KEG, GATE, ALARM_BELL, BEACON, BEACON_LIT ->
                    interactWithBlockAt(pos);
            case CRATE -> game.openCrateAt(pos);
            case WORKBENCH, FURNACE, ANVIL, TANNERY, HERB_STATION, MAP_TABLE ->
                    game.uiMode = Game.UiMode.CRAFTING;
            case DRYING_RACK -> rackInteract(pos);
            case RAIN_COLLECTOR -> collectorInteract(pos);
            case BEDROLL -> game.startSleep(false);
            case CAMP_BED -> sleepInBed(pos);
            default -> {
                return false;
            }
        }
        return true;
    }

    private void sleepInBed(Vec3i pos) {
        boolean atCamp = game.world.campPos != null
                && pos.distSq(game.world.campPos.x(), game.world.campPos.y(),
                        game.world.campPos.z()) < CAMP_BED_RADIUS * CAMP_BED_RADIUS;
        if (atCamp && !game.faction.campPrivileges()) {
            game.log("The camp won't let you use their beds yet (needs Friendly trust).");
            return;
        }
        var bedSettlement = game.world.settlementAt(pos.x(), pos.z());
        if (bedSettlement != null && !bedSettlement.friendly() && !bedSettlement.cleared) {
            game.log(bedSettlement.hostile()
                    ? "Sleeping in a hostile camp? Not a chance."
                    : "These beds belong to the residents. Earn their friendship first.");
            return;
        }
        game.startSleep(true);
    }

    private void drinkFromOpenWater() {
        Raycaster.Result fluid = Raycaster.castInto(game.world, game.camera.position,
                game.camera.front(), WATER_REACH, true, game.fluidHitBuffer)
                ? game.fluidHitBuffer : null;
        if (fluid == null || fluid.type() != BlockType.WATER) {
            return;
        }
        if (game.player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0) {
            game.player.inventory.remove(ItemType.WATERSKIN_EMPTY, 1);
            game.player.inventory.add(ItemType.WATERSKIN_DIRTY, 1);
            game.audio.playDrink();
            game.log("Filled a waterskin with untreated water. Boil it at a campfire.");
            return;
        }
        game.player.thirst = Math.min(MAX_NEED, game.player.thirst + WATER_THIRST);
        game.audio.playDrink();
        if (rng.nextFloat() < WATER_POISON_CHANCE) {
            game.player.addAffliction(Affliction.FOOD_POISONING,
                    PlayerConsumables.rollPoisonDuration(rng));
            game.log("You drank dirty water and feel ill...");
        } else {
            game.log("You drink from the water (+" + (int) WATER_THIRST + " thirst).");
        }
    }

    // ------------------------------------------------------------------
    // Arrows and carcasses
    // ------------------------------------------------------------------

    /** Gameplay command shared by the real F-key path and integration tests. */
    boolean recoverNearbyArrow() {
        if (game.player == null) {
            return false;
        }
        var stuckArrow = game.projectiles.nearestStuckArrow(
                game.player.pos.x, game.player.pos.y + 1f, game.player.pos.z, PICKUP_RANGE);
        if (stuckArrow == null) {
            return false;
        }
        game.projectiles.pickUp(game, stuckArrow);
        return true;
    }

    /** Gameplay command shared by the real F-key path for harvesting carcasses. */
    boolean interactWithNearbyCarcass() {
        if (game.player == null) {
            return false;
        }
        Carcass carcass = game.entities.nearestCarcass(
                game.player.pos.x, game.player.pos.y, game.player.pos.z, PICKUP_RANGE);
        if (carcass == null) {
            return false;
        }
        harvestCarcass(carcass);
        return true;
    }

    private void harvestCarcass(Carcass carcass) {
        ItemType meatType = carcass.rotten() ? ItemType.SPOILED_MEAT : ItemType.RAW_MEAT;
        if (game.playerHasKnife()) {
            skinCarcass(carcass, meatType);
        } else if (carcass.meatLeft > 0) {
            carcass.meatLeft--;
            game.player.inventory.add(meatType, 1);
            game.audio.playEat();
            game.particles.blood(carcass.pos.x, carcass.pos.y + 0.3f, carcass.pos.z);
            game.log("You tear off some meat with your hands. A knife would salvage the hide.");
        } else {
            game.log("Nothing left worth taking.");
        }
        game.player.scent = Math.min(1f, game.player.scent + HARVEST_SCENT);
    }

    private void skinCarcass(Carcass carcass, ItemType meatType) {
        int meat = carcass.meatLeft;
        int hide = carcass.hideLeft;
        if (meat > 0) {
            game.player.inventory.add(meatType, meat);
        }
        if (hide > 0) {
            game.player.inventory.add(ItemType.HIDE, hide);
        }
        if (rng.nextFloat() < CARCASS_BONE_CHANCE) {
            game.player.inventory.add(ItemType.BONE, 1);
        }
        carcass.meatLeft = 0;
        carcass.hideLeft = 0;
        game.combat.useKnife();
        game.audio.playEat();
        game.particles.blood(carcass.pos.x, carcass.pos.y + 0.3f, carcass.pos.z);
        game.log("Skinned the " + carcass.type.displayName + ": " + meat + " meat, " + hide
                + " hide" + (carcass.rotten() ? " (the meat is spoiled)" : "") + ".");
        // Lodged arrows come back with the hide.
        if (carcass.stuckArrows > 0) {
            ItemType arrowType = carcass.stuckArrowType != null
                    ? carcass.stuckArrowType : ItemType.ARROW;
            game.player.inventory.add(arrowType, carcass.stuckArrows);
            game.log("Recovered " + carcass.stuckArrows + "x " + arrowType.displayName + ".");
            carcass.stuckArrows = 0;
        }
    }

    // ------------------------------------------------------------------
    // Stations
    // ------------------------------------------------------------------

    private void campfireInteract(Vec3i pos) {
        // A cleared settlement's fire is where the player claims the outpost.
        var cs = game.world.settlementAt(pos.x(), pos.z());
        if (cs != null && cs.hostile()
                && HumanFaction.FREE_SETTLERS.equals(cs.founderFaction)) {
            game.settlementManager.offerRestitution(game, cs);
            return;
        }
        if (cs != null && cs.hostile()) {
            game.settlementManager.controlCentralObjective(game, cs);
            return;
        }
        if (cs != null && cs.cleared && !cs.occupied) {
            game.settlementManager.occupy(game, cs);
            return;
        }
        boolean lit = game.world.campfireFuel.getOrDefault(pos, 0f) > 0;
        if (lit && game.player.inventory.has(ItemType.RAW_MEAT, 1)) {
            game.player.inventory.remove(ItemType.RAW_MEAT, 1);
            game.player.inventory.add(ItemType.COOKED_MEAT, 1);
            game.audio.playClick();
            game.particles.smoke(pos.x() + 0.5f, pos.y() + 0.8f, pos.z() + 0.5f, 1f);
            game.log("Cooked meat over the campfire.");
        } else if (lit && game.player.inventory.has(ItemType.WATERSKIN_DIRTY, 1)) {
            game.player.inventory.remove(ItemType.WATERSKIN_DIRTY, 1);
            game.player.inventory.add(ItemType.WATERSKIN_CLEAN, 1);
            game.audio.playBoil();
            game.log("Boiled the waterskin - the water is safe to drink now.");
        } else if (game.player.inventory.has(ItemType.LOG, 1)) {
            game.player.inventory.remove(ItemType.LOG, 1);
            game.world.campfireFuel.merge(pos, LOG_FUEL_SECONDS, Float::sum);
            game.audio.playClick();
            game.log("Added a log to the fire (+" + (int) LOG_FUEL_SECONDS + "s fuel).");
        } else {
            game.log(lit ? "Bring raw meat to cook, dirty water to boil, or a log for fuel."
                    : "The fire is out. Add a log to relight it.");
        }
    }

    /** Gameplay command shared by the real F-key interaction and integration tests. */
    boolean interactLantern(Vec3i pos) {
        World.LanternState state = game.world.lanternState(pos);
        if (state == null) {
            return false;
        }
        ItemStack held = game.player.selected();
        if (held != null && held.type == ItemType.CHARCOAL
                && state.fuelSeconds() < World.LANTERN_MAX_FUEL) {
            game.player.inventory.shrink(game.player.hotbarSel, 1);
            float fuel = game.world.addLanternFuel(pos, World.LANTERN_FUEL_PER_CHARCOAL);
            game.audio.playClick();
            game.log(state.lit()
                    ? "Added charcoal to the lit lantern (" + (int) fuel + "s fuel)."
                    : "Added charcoal to the lantern (" + (int) fuel
                    + "s fuel). Press [F] again to light it.");
            return true;
        }
        if (state.fuelSeconds() <= 0) {
            game.log("The lantern is empty. Hold charcoal and press [F] to refuel it.");
            return true;
        }
        boolean light = !state.lit();
        game.world.setLanternLit(pos, light);
        game.audio.playClick();
        game.log(light ? "Lantern lit."
                : "Lantern extinguished; its remaining fuel is preserved.");
        return true;
    }

    private void rackInteract(Vec3i pos) {
        RackBatch batch = game.world.rackBatches.get(pos);
        if (batch != null && batch.done()) {
            game.player.inventory.add(batch.output(), batch.count);
            game.world.rackBatches.remove(pos);
            game.audio.playClick();
            game.log("Collected " + batch.count + "x " + batch.output().displayName
                    + " from the rack.");
            return;
        }
        if (batch != null) {
            int pct = (int) (batch.progress / batch.required() * 100);
            game.log("Still drying: " + batch.count + "x " + batch.input.displayName
                    + " (" + pct + "%).");
            return;
        }
        ItemType input = null;
        if (game.player.inventory.count(ItemType.RAW_MEAT) > 0) {
            input = ItemType.RAW_MEAT;
        } else if (game.player.inventory.count(ItemType.BERRY) > 0) {
            input = ItemType.BERRY;
        }
        if (input == null) {
            game.log("You need raw meat or berries to dry on the rack.");
            return;
        }
        int count = Math.min(RACK_BATCH_MAX, game.player.inventory.count(input));
        game.player.inventory.remove(input, count);
        game.world.rackBatches.put(pos, new RackBatch(input, count));
        game.audio.playClick();
        game.log("Hung " + count + "x " + input.displayName
                + " to dry. Keep it out of the rain.");
    }

    private void collectorInteract(Vec3i pos) {
        float liters = game.world.collectorWater.getOrDefault(pos, 0f);
        if (liters >= COLLECTOR_LITRES_PER_USE
                && game.player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0) {
            game.player.inventory.remove(ItemType.WATERSKIN_EMPTY, 1);
            game.player.inventory.add(ItemType.WATERSKIN_CLEAN, 1);
            game.world.collectorWater.put(pos, liters - COLLECTOR_LITRES_PER_USE);
            game.audio.playDrink();
            game.log("Filled a waterskin with clean rainwater.");
        } else if (liters >= COLLECTOR_LITRES_PER_USE) {
            game.player.thirst = Math.min(MAX_NEED,
                    game.player.thirst + COLLECTOR_DRINK_THIRST);
            game.world.collectorWater.put(pos, liters - COLLECTOR_LITRES_PER_USE);
            game.audio.playDrink();
            game.log("You drink fresh rainwater (+" + (int) COLLECTOR_DRINK_THIRST
                    + " thirst).");
        } else {
            game.log("The collector holds " + String.format("%.1f", liters)
                    + " L. It fills while it rains.");
        }
    }

    /** The three-stage repair that ends the game when it completes. */
    private void beaconInteract() {
        switch (game.world.beaconStage) {
            case 0 -> {
                if (game.player.inventory.has(ItemType.SIGNAL_CRYSTAL, 1)) {
                    game.player.inventory.remove(ItemType.SIGNAL_CRYSTAL, 1);
                    game.world.beaconStage = 1;
                    game.audio.playCraft();
                    game.log("Signal crystal installed. Next: wire the array with "
                            + BEACON_COPPER_REQUIRED + " copper ingots.");
                } else {
                    game.log("The beacon needs a SIGNAL CRYSTAL. "
                            + "Ancient ruins hold resonant cores...");
                }
            }
            case 1 -> {
                if (game.player.inventory.has(ItemType.COPPER_INGOT, BEACON_COPPER_REQUIRED)) {
                    game.player.inventory.remove(ItemType.COPPER_INGOT, BEACON_COPPER_REQUIRED);
                    game.world.beaconStage = 2;
                    game.audio.playCraft();
                    game.log("Wiring complete. The beacon needs calibration codes "
                            + "from the camp (Allied).");
                } else {
                    game.log("Wiring requires " + BEACON_COPPER_REQUIRED
                            + " COPPER INGOTS (smelt copper ore at a furnace).");
                }
            }
            case 2 -> {
                if (game.faction.trust >= BEACON_ALLY_TRUST) {
                    game.world.beaconStage = 3;
                    Vec3i bp = game.world.beaconPos;
                    game.world.setBlock(bp.x(), bp.y(), bp.z(), BlockType.BEACON_LIT, true);
                    game.audio.playDiscover();
                    game.log("=== THE DISTRESS BEACON IS ALIVE! "
                            + "Its signal pierces the sky. ===");
                    game.log("You did it. Rescue will come. Survive until then - "
                            + "Veylon isn't done with you.");
                    game.closeScreens();
                    game.appState = Game.AppState.VICTORY;
                } else {
                    game.log("The camp must trust you as an ALLY (trust "
                            + BEACON_ALLY_TRUST + "+) to share calibration codes.");
                }
            }
            default -> {
            }
        }
    }
}
