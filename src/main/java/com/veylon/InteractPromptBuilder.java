package com.veylon;

import com.veylon.entity.Carcass;
import com.veylon.entity.Npc;
import com.veylon.entity.Track;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.SettlementManager;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.RackBatch;
import com.veylon.world.Raycaster;
import com.veylon.world.World;

/**
 * Builds the one-line "[F] ..." hint the HUD shows for whatever the player is
 * currently looking at or standing next to.
 *
 * <p>This is a pure read of world, entity and reputation state: it never mutates
 * anything. It is separate from the interaction handlers so the wording and the
 * priority order between competing prompts can be reviewed in one place, and so
 * a prompt change can never accidentally alter what pressing F actually does.
 *
 * <p>Priority order is deliberate and load-bearing: NPCs, then recoverable
 * arrows, then carcasses, then the targeted block, then tracks, then water.
 */
final class InteractPromptBuilder {

    /** Reach for talking to or rescuing an NPC, in blocks. */
    private static final float NPC_RANGE = 3.2f;
    /** Reach for picking up stuck arrows and harvesting carcasses, in blocks. */
    private static final float PICKUP_RANGE = 2.6f;
    /** Crouched reach for reading animal tracks, in blocks. */
    private static final float TRACK_RANGE = 3f;
    /** Raycast distance used to find drinkable water, in blocks. */
    private static final double WATER_REACH = 4.0;
    /** Trust required before the camp shares its beacon calibration codes. */
    private static final int BEACON_CODES_TRUST = 75;
    /** Rain collector capacity, in litres. */
    private static final float COLLECTOR_CAPACITY_L = 3.0f;

    private final Game game;

    InteractPromptBuilder(Game game) {
        this.game = game;
    }

    /**
     * Recomputes {@link Game#interactPrompt} for this frame.
     *
     * <p>Sets it to {@code null} when nothing nearby is interactable.
     */
    void update() {
        game.interactPrompt = null;
        if (npcPrompt() || stuckArrowPrompt() || carcassPrompt() || targetBlockPrompt()
                || trackPrompt()) {
            return;
        }
        waterPrompt();
    }

    private boolean npcPrompt() {
        Npc npc = game.nearestNpcForInteraction(NPC_RANGE);
        Game.NpcInteraction interaction = game.npcInteraction(npc);
        if (interaction == Game.NpcInteraction.NONE) {
            return false;
        }
        game.interactPrompt = switch (interaction) {
            case TALK -> "[F] Talk to " + npc.name
                    + (npc.settled() ? " (" + npc.jobName() + ")" : "");
            case RESCUE_BLOCKED -> "Break the cage bars to rescue " + npc.name;
            case RESCUE_READY -> "[F] Rescue " + npc.name;
            case NONE -> null;
        };
        return true;
    }

    private boolean stuckArrowPrompt() {
        var stuckArrow = game.projectiles.nearestStuckArrow(
                game.player.pos.x, game.player.pos.y + 1f, game.player.pos.z, PICKUP_RANGE);
        if (stuckArrow == null) {
            return false;
        }
        game.interactPrompt = "[F] Recover arrow";
        return true;
    }

    private boolean carcassPrompt() {
        Carcass carcass = game.entities.nearestCarcass(
                game.player.pos.x, game.player.pos.y, game.player.pos.z, PICKUP_RANGE);
        if (carcass == null) {
            return false;
        }
        game.interactPrompt = "[F] Harvest " + carcass.type.displayName + " carcass"
                + (carcass.rotten() ? " (rotting!)" : "")
                + (game.playerHasKnife() ? "" : " (no knife: scraps only)");
        return true;
    }

    /** @return true when the targeted block produced a prompt, or blocked further ones */
    private boolean targetBlockPrompt() {
        if (game.targetHit == null) {
            return false;
        }
        World world = game.world;
        int targetX = game.targetHit.x();
        int targetY = game.targetHit.y();
        int targetZ = game.targetHit.z();
        switch (game.targetHit.type()) {
            case BERRY_BUSH -> game.interactPrompt = "[F] Harvest berries";
            case HERB_PLANT -> game.interactPrompt = "[F] Gather herbs";
            case CAMPFIRE -> {
                Vec3i position = new Vec3i(targetX, targetY, targetZ);
                game.interactPrompt = "[F] Cook meat / boil water / add fuel ("
                        + (int) (float) world.campfireFuel.getOrDefault(position, 0f)
                        + "s fuel)";
            }
            case CRATE -> {
                var stores = world.settlementAt(targetX, targetZ);
                game.interactPrompt = stores != null && !stores.cleared && !stores.occupied
                        ? "[F] Open " + (stores.hostile() ? "enemy" : "restricted")
                        + " stores (taking supplies has consequences)"
                        : "[F] Open crate";
            }
            case WORKBENCH -> game.interactPrompt = "[F] Use workbench";
            case FURNACE -> game.interactPrompt = "[F] Use furnace (crafting)";
            case ANVIL -> game.interactPrompt = "[F] Use anvil (crafting)";
            case TANNERY -> game.interactPrompt = "[F] Use tannery (crafting)";
            case HERB_STATION -> game.interactPrompt = "[F] Use herbalist bench (crafting)";
            case MAP_TABLE -> game.interactPrompt = "[F] Use map table (decode blueprints)";
            case DRYING_RACK -> game.interactPrompt = rackPrompt(
                    new Vec3i(targetX, targetY, targetZ));
            case RAIN_COLLECTOR -> {
                float liters = world.collectorWater.getOrDefault(
                        new Vec3i(targetX, targetY, targetZ), 0f);
                game.interactPrompt = "[F] Rain collector: " + String.format("%.1f", liters)
                        + "/" + String.format("%.1f", COLLECTOR_CAPACITY_L) + " L"
                        + (liters >= 1f ? " (fill waterskin)" : "");
            }
            case LANTERN -> game.interactPrompt = lanternPrompt(
                    new Vec3i(targetX, targetY, targetZ));
            case BEDROLL -> game.interactPrompt = "[F] Sleep (bedroll)";
            case CAMP_BED -> {
                var beds = world.settlementAt(targetX, targetZ);
                if (beds != null) {
                    game.interactPrompt = beds.friendly() || beds.cleared
                            ? "[F] Sleep safely (settlement bed)"
                            : beds.hostile()
                            ? "Hostile bed — clear the area before sleeping"
                            : "Residents' bed — earn local friendship first";
                } else {
                    game.interactPrompt = "[F] Sleep (camp bed"
                            + (game.faction.campPrivileges() ? ")" : " - needs Friendly trust)");
                }
            }
            case BEACON -> game.interactPrompt = beaconPrompt();
            case BEACON_LIT -> game.interactPrompt = "[F] Beacon transmitting... rescue inbound";
            case POWDER_KEG -> {
                Float fuse = world.kegFuses.get(new Vec3i(targetX, targetY, targetZ));
                game.interactPrompt = fuse != null
                        ? "FUSE BURNING — " + String.format("%.1f", fuse) + "s. RUN!"
                        : "[F] Light the fuse (5s) — stand well clear";
            }
            case GATE -> {
                var gs = world.settlementAt(targetX, targetZ);
                boolean barred = gs != null && gs.hostile();
                game.interactPrompt = barred
                        ? "Barred from the inside. A powder keg could breach it."
                        : "[F] Open gate";
            }
            case GATE_OPEN -> game.interactPrompt = "Gate (closes on its own)";
            case ALARM_BELL -> {
                var bs = world.settlementAt(targetX, targetZ);
                game.interactPrompt = bs != null && bs.hostile()
                        ? "Alarm bell — destroy it to silence the garrison"
                        : "[F] Ring the alarm";
            }
            case CAGE_BARS -> game.interactPrompt =
                    "Cage bars (mine through to free captives)";
            default -> {
            }
        }
        // Outpost claim prompt overrides the campfire line.
        if (game.targetHit.type() == BlockType.CAMPFIRE) {
            var cs = world.settlementAt(targetX, targetZ);
            if (cs != null && cs.hostile()
                    && HumanFaction.FREE_SETTLERS.equals(cs.founderFaction)) {
                game.interactPrompt = "[F] Offer restitution (6 food, 2 medicine)";
            } else if (cs != null && cs.hostile() && !cs.centralObjectiveControlled) {
                game.interactPrompt = "[F] Secure central capture objective";
            } else if (cs != null && cs.cleared && !cs.occupied) {
                game.interactPrompt = "[F] Supply and claim outpost ("
                        + SettlementManager.OCCUPY_FOOD + " food, "
                        + SettlementManager.OCCUPY_WOOD + " logs)";
            }
        }
        if (game.interactPrompt != null) {
            return true;
        }
        if (game.targetHit.type().requiresTool
                && !holdingTool(game.targetHit.type().preferredTool)) {
            game.interactPrompt = "Requires "
                    + game.targetHit.type().preferredTool.name().toLowerCase();
            return true;
        }
        return false;
    }

    /** Crouching close to the ground reveals animal tracks. */
    private boolean trackPrompt() {
        if (!game.player.crouching) {
            return false;
        }
        Track track = game.entities.nearestTrack(
                game.player.pos.x, game.player.pos.y, game.player.pos.z, TRACK_RANGE);
        if (track == null) {
            return false;
        }
        game.interactPrompt = track.describe();
        return true;
    }

    private void waterPrompt() {
        Raycaster.Result fluid = Raycaster.castInto(game.world, game.camera.position,
                game.camera.front(), WATER_REACH, true, game.fluidHitBuffer)
                ? game.fluidHitBuffer : null;
        if (fluid != null && fluid.type() == BlockType.WATER) {
            game.interactPrompt = game.player.inventory.count(ItemType.WATERSKIN_EMPTY) > 0
                    ? "[F] Fill waterskin (untreated water)"
                    : "[F] Drink (untreated water - risky)";
        }
    }

    private String rackPrompt(Vec3i pos) {
        RackBatch batch = game.world.rackBatches.get(pos);
        if (batch == null) {
            return "[F] Load drying rack (raw meat or berries)";
        }
        if (batch.done()) {
            return "[F] Collect " + batch.count + "x " + batch.output().displayName;
        }
        int pct = (int) (batch.progress / batch.required() * 100);
        return "Drying " + batch.count + "x " + batch.input.displayName + " (" + pct + "%)";
    }

    /** HUD text for the same lantern state consumed by the F-key command. */
    String lanternPrompt(Vec3i pos) {
        World.LanternState state = game.world.lanternState(pos);
        if (state == null) {
            return "Lantern unavailable";
        }
        int fuel = (int) Math.ceil(state.fuelSeconds());
        ItemStack held = game.player.selected();
        if (held != null && held.type == ItemType.CHARCOAL
                && state.fuelSeconds() < World.LANTERN_MAX_FUEL) {
            return "[F] Refuel lantern with charcoal (" + fuel + "/"
                    + (int) World.LANTERN_MAX_FUEL + "s)";
        }
        if (fuel <= 0) {
            return "[F] Lantern UNLIT — hold charcoal to refuel";
        }
        return state.lit()
                ? "[F] Extinguish lantern — LIT, " + fuel + "s fuel"
                : "[F] Light lantern — UNLIT, " + fuel + "s fuel";
    }

    private String beaconPrompt() {
        return switch (game.world.beaconStage) {
            case 0 -> "[F] Install Signal Crystal (need 1, from ancient ruins)";
            case 1 -> "[F] Wire the array (need 3 copper ingots)";
            case 2 -> game.faction.trust >= BEACON_CODES_TRUST
                    ? "[F] Calibrate with the camp's codes (Allied)"
                    : "Calibration needs the camp's codes - become Allied (trust "
                    + BEACON_CODES_TRUST + "+)";
            default -> "[F] Distress beacon";
        };
    }

    private boolean holdingTool(ToolKind kind) {
        ItemStack held = game.player.selected();
        return held != null && held.type.tool == kind;
    }
}
