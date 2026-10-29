package com.veylon;

import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;
import com.veylon.settlement.UrbanComms;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.RelaySites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanRefuges;
import com.veylon.world.urban.UrbanUtilities.Device;

import java.util.Locale;

/**
 * The last two stages of a layout-sixteen city's communications objective (urban milestone 20), as
 * the player works them: the relay repaired (C4) and the broadcast made (C5). What was learned and
 * gathered before (C1 to C3) is {@link UrbanComms}'s; every fact is the world's
 * {@link UrbanMissions}.
 *
 * <ul>
 *   <li><b>The relay.</b> The use button at its cabinet with antenna cable strings the antenna, with
 *       power cells seats them; in Survival each takes a pry tool in the pack and the part itself,
 *       and either is heard. Both together repair it.</li>
 *   <li><b>The radio.</b> A radio workstation, built at a workbench round a transmitter module and
 *       put down on a building's wiring, is a load on its generator while switched on. Switched on
 *       and fed in a building where the survivor holds a refuge, with the relay repaired, it
 *       transmits: by the seconds its generator runs, {@value UrbanMissions#BROADCAST_SECONDS} with
 *       the relay's codes or {@value UrbanMissions#SCAN_SECONDS} hunting the band without them, the
 *       count kept through a power cut or a load. Every {@value #NOISE_INTERVAL} seconds it is a real
 *       player-attributed {@code WorldNoise} event of {@value #RADIO_NOISE_RADIUS} blocks, beside the
 *       generator's own, so whatever hears it comes; a Creative player's is heard by nobody.</li>
 *   <li><b>The answer.</b> When the call has gone out long enough the objective is complete, once:
 *       the city answers (every camp and stronghold the survivor knows that is not against them
 *       trusts them more) and the game shows its ending card, from which the survivor carries on
 *       surviving.</li>
 * </ul>
 */
final class CommsObjective {
    /** Seconds between a transmitting radio's noise events. */
    static final float NOISE_INTERVAL = 3f;
    /** A transmitting radio's noise: static, a voice and the relay's tone through the walls. */
    static final float RADIO_NOISE_RADIUS = 32f, RADIO_NOISE = 0.6f;
    /** Working a part into the relay's cabinet. */
    static final float FIT_NOISE_RADIUS = 16f, FIT_NOISE = 0.35f;

    private final Game game;
    /** Transient: seconds of broadcast to the next noise event. */
    private float noiseTimer;
    /** Transient: a radio carried the call this tick, so one relay carries one call at a time. */
    private boolean carried;

    CommsObjective(Game game) {
        this.game = game;
    }

    void reset() {
        noiseTimer = 0f;
        carried = false;
    }

    /** True in a world whose rules include the communications objective: layout sixteen. */
    static boolean applies(World w) {
        return UrbanComms.applies(w);
    }

    private UrbanMissions.Comms comms() {
        return game.world.urban.missions.comms;
    }

    // ------------------------------------------------------------------
    // The relay
    // ------------------------------------------------------------------

    /** True when the player aims at the relay's cabinet. */
    private boolean aimsAtRelay() {
        return applies(game.world) && game.targetHit != null && game.targetHit.type() == BlockType.RELAY
                && game.world.urban.relayAt(game.targetHit.x(), game.targetHit.y(), game.targetHit.z());
    }

    /**
     * The use button with a part held, aimed at the relay: fit it.
     *
     * @return true when the press was a relay action, done or refused with a reason
     */
    boolean rightClick(ItemStack held) {
        if (held == null || !aimsAtRelay()) { return false; }
        int bit = bitOf(held.type);
        if (bit == 0) { return false; }
        UrbanMissions.Comms c = comms();
        if ((c.installed & bit) != 0) {
            game.log(bit == UrbanMissions.RELAY_ANTENNA ? "The relay's antenna is already strung."
                    : "The relay already has its power cells.");
            return true;
        }
        boolean free = game.player.abilities.unlimitedItems();
        if (!free && !hasPryTool()) {
            game.log("Opening the relay's cabinet takes a pry tool in your pack.");
            return true;
        }
        if (!free) { game.player.inventory.shrink(game.player.hotbarSel, 1); }
        c.installed |= bit;
        c.relayFound = true;
        c.rumoured = true;
        CommsComponents.Kind kind = CommsComponents.Kind.of(held.type);
        c.recovered |= kind.bit;
        Vec3i cab = game.world.urban.relay().relay();
        game.noise.emit(game, cab.x() + .5f, cab.y() + .5f, cab.z() + .5f, FIT_NOISE_RADIUS, FIT_NOISE, "repair",
                true, game.player);
        game.audio.playCraft();
        if (c.relayRepaired()) {
            game.log("The relay's cabinet hums: its cells hold a charge and its antenna is strung. It will carry a"
                    + " call from a powered radio.");
            game.audio.playDiscover();
        } else {
            game.log(bit == UrbanMissions.RELAY_ANTENNA ? "You string the antenna cable up the mast."
                    : "You seat the power cells in the relay's cabinet.");
        }
        return true;
    }

    /** What the use button would do with the held item at the relay, or null. */
    String rightClickPrompt(ItemStack held) {
        if (held == null || !aimsAtRelay()) { return null; }
        int bit = bitOf(held.type);
        if (bit == 0 || (comms().installed & bit) != 0) { return null; }
        if (!game.player.abilities.unlimitedItems() && !hasPryTool()) { return "Opening the relay's cabinet takes a pry tool"; }
        return bit == UrbanMissions.RELAY_ANTENNA ? "[RMB] String the antenna cable up the mast"
                : "[RMB] Seat the power cells in the relay's cabinet";
    }

    /** The relay cabinet's line, saying what it still lacks. */
    String relayPrompt() {
        if (!applies(game.world)) { return "Relay mast"; }
        UrbanMissions.Comms c = comms();
        if (c.complete) { return "Relay mast: carrying the city's calls again"; }
        if (c.relayRepaired()) { return "Relay mast: repaired, listening for a radio"; }
        boolean antenna = (c.installed & UrbanMissions.RELAY_ANTENNA) != 0;
        boolean cells = (c.installed & UrbanMissions.RELAY_CELLS) != 0;
        return "Relay mast: needs " + (!antenna && !cells ? "antenna cable and power cells"
                : !antenna ? "antenna cable" : "power cells");
    }

    private static int bitOf(ItemType type) {
        return type == ItemType.ANTENNA_CABLE ? UrbanMissions.RELAY_ANTENNA
                : type == ItemType.POWER_CELL ? UrbanMissions.RELAY_CELLS : 0;
    }

    private boolean hasPryTool() {
        var inv = game.player.inventory;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && s.type.tool == ToolKind.PICKAXE) { return true; }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // The radio
    // ------------------------------------------------------------------

    /** A radio workstation was put down: the objective's radio exists. */
    void radioPlaced(Device d) {
        if (!applies(game.world)) { return; }
        comms().radioBuilt = true;
        game.log("Radio workstation put down. Switch it on with [F] while a generator in this building runs.");
    }

    /** The log line for a radio's switch. */
    void radioSwitched(Device d) {
        if (!d.on) {
            game.log("You switch the radio off.");
            return;
        }
        game.log(!d.powered() ? "You switch the radio on, but nothing feeds it."
                : !comms().relayRepaired() ? "The radio hisses: nothing on the air answers. The relay is dead."
                : !atRefuge(d) ? "The radio crackles, but a call this long needs the safety of your refuge."
                : comms().complete ? "Voices come and go on the old frequency." : "The radio warms up on the relay's frequency.");
    }

    /** The radio's line: its switch and how the broadcast stands. */
    String radioPrompt(Device d) {
        UrbanMissions.Comms c = comms();
        String state = !d.powered() ? "no power" : !c.relayRepaired() ? "the relay is dead"
                : !atRefuge(d) ? "not in your refuge" : c.complete ? "the city answers"
                : String.format(Locale.ROOT, "%s %d of %d s", c.codes == UrbanMissions.CODES_NONE ? "hunting the band"
                : "broadcasting", (int) c.broadcast, (int) c.required());
        return (d.on ? "[F] Switch the radio off (" : "[F] Switch the radio on (") + state + ")";
    }

    /** Whether a radio stands in a building where the survivor holds a refuge. */
    boolean atRefuge(Device radio) {
        UrbanRefuges refuges = game.world.urban.refuges;
        for (UrbanRefuges.Claim claim : refuges.claims()) {
            if (UrbanIds.parent(claim.floor) == radio.building) { return true; }
        }
        return false;
    }

    /** Starts a utility tick: no radio has carried the call yet. */
    void beginTick() {
        carried = false;
    }

    /**
     * The radios a running generator feeds transmit for the seconds it ran this tick, through the
     * repaired relay and from a refuge; the call completes once it has gone out long enough.
     */
    void broadcast(Device generator, float run, java.util.List<Device> all) {
        UrbanMissions.Comms c = comms();
        if (!applies(game.world) || c.complete || !c.relayRepaired() || run <= 0f) { return; }
        for (int i = 0; i < all.size(); i++) {
            Device radio = all.get(i);
            if (radio.kind != com.veylon.world.urban.UrbanUtilities.Kind.RADIO || radio.feeder() != generator
                    || !radio.transmitting() || !atRefuge(radio)) {
                continue;
            }
            if (carried) { return; } // another radio already carried the call this tick
            carried = true;
            if (c.broadcast <= 0f) {
                game.log(c.codes == UrbanMissions.CODES_NONE
                        ? "Without the relay's codes the radio hunts the band for a channel it will carry. This will take a while."
                        : "You key the transmitter. On the relay's codes your call goes out over the city.");
                noiseTimer = 0f;
            }
            c.broadcast = Math.min(UrbanMissions.SCAN_SECONDS, c.broadcast + run);
            if ((noiseTimer -= run) <= 0f) {
                noiseTimer += NOISE_INTERVAL;
                game.noise.emit(game, radio.pos.x() + .5f, radio.pos.y() + .5f, radio.pos.z() + .5f, RADIO_NOISE_RADIUS,
                        RADIO_NOISE, "radio", true, game.player);
            }
            if (c.broadcast >= c.required()) { complete(); }
            return;
        }
    }

    /** The call has gone out: the city answers, once, and the ending card offers to carry on. */
    private void complete() {
        UrbanMissions.Comms c = comms();
        c.complete = true;
        c.completeMinute = game.time.totalMinutes;
        RelaySites.Site relay = game.world.urban.relay();
        game.log("=== THE CITY ANSWERS ===");
        game.log("Voices come back through the relay" + (relay == null ? "" : " on the precinct roof")
                + ": camps, the Wardens, people you never met. You are not the only one left.");
        UrbanComms.cityAnswers(game);
        game.audio.playDiscover();
        game.closeScreens();
        game.appState = Game.AppState.VICTORY;
    }
}
