package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.DistrictRules;
import com.veylon.world.urban.RelaySites;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;

/**
 * The communications objective of a layout-sixteen city (urban milestone 20), as the survivor learns
 * of it and gathers for it: the rumour of the old relay mast (C1), the parts it and a radio need
 * (C2) and the relay's codes (C3). The facts are the world's {@link UrbanMissions}; these commands
 * are the gameplay seams the conversation screen, the storage screen, the HUD and the tests share.
 *
 * <ul>
 *   <li><b>The rumour.</b> Anyone of a camp, a stronghold or a hidden refuge, asked about the radio,
 *       says where the relay stands: a rooftop in the precinct's district, in a direction from
 *       where they are, never the building. Standing at the relay counts as hearing of it too, so
 *       no death and no hostility can keep the survivor from it.</li>
 *   <li><b>The parts.</b> A part taken from any container is recovered, once per kind
 *       ({@link CommsComponents} says where they are).</li>
 *   <li><b>The codes.</b> A stronghold that counts the survivor a friend, or hidden civilians who
 *       trust them as far as they would follow them ({@value #CODES_TRUST}), share the relay's codes.
 *       Without them the relay can still be made to carry a call, the long and loud way.</li>
 * </ul>
 */
public final class UrbanComms {
    /** Trust hidden civilians need before one of them shares the relay's codes; what moving takes. */
    public static final float CODES_TRUST = UrbanSiteRelocation.TRUST_TO_MOVE;
    /** Horizontal blocks from the relay cabinet within which a survivor standing on its roof has found it. */
    public static final float FOUND_REACH = 2.5f;

    private UrbanComms() {
    }

    /** True in a world whose rules include the communications objective: layout sixteen. */
    public static boolean applies(World w) {
        return w != null && w.urban != null && w.urban.hasMissions();
    }

    private static UrbanMissions.Comms comms(Game g) {
        return g.world.urban.missions.comms;
    }

    // ------------------------------------------------------------------
    // Conversation: the rumour and the codes
    // ------------------------------------------------------------------

    /** The conversation choice about the radio for a site's person, or null where it is no subject. */
    public static String radioOption(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (!applies(g.world) || s == null || s.raiders()) { return null; }
        return "[9] Ask about the old radio relay";
    }

    /**
     * Asks a camp's, a stronghold's or hidden civilians' person about the radio: the rumour of the
     * relay if the survivor has not heard of it, the relay's codes from those who keep them and
     * trust the survivor, or what they know of the parts. Returns the answer.
     */
    public static String askAboutRadio(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (!applies(g.world) || s == null || n.dead || s.raiders()) { return "..."; }
        // Talking to one of them makes their site known, as any conversation does (D-144).
        g.settlementManager.urbanSites.onResidentSeen(g, s);
        if (s.hostile()) { return "We've nothing to say to you."; }
        RelaySites.Site relay = g.world.urban.relay();
        if (relay == null) { return "The radio's been dead since the quarantine. Nothing anyone can do."; }
        UrbanMissions.Comms c = comms(g);
        if (!c.rumoured) {
            c.rumoured = true;
            c.rumourFrom = s.id;
            String where = where(s.plan.anchor(), relay);
            g.log("Rumour: an old relay mast still stands on a rooftop in the " + where + ".");
            g.audio.playQuest();
            if (s.refuge()) {
                return "One of us kept the relay running before the quarantine. The mast is on a roof in the "
                        + where + ".";
            }
            return "There's an old relay mast on a rooftop in the " + where + ". Nobody's had the parts to make it"
                    + " talk again.";
        }
        if (c.codes == UrbanMissions.CODES_NONE) {
            String codes = shareCodes(g, s);
            if (codes != null) { return codes; }
        }
        return partsLine(c, s);
    }

    /** The relay's codes from a site that keeps them and trusts the survivor; a refusal or a hint otherwise. */
    private static String shareCodes(Game g, UrbanSite s) {
        UrbanMissions.Comms c = comms(g);
        if (s.stronghold()) {
            if (!s.grantsAccess()) { return "The relay's codes are the ward's business. Show us you're a friend first."; }
            give(g, c, UrbanMissions.CODES_STRONGHOLD, s, "The Wardens give you the relay's codes.");
            return "The ward kept the relay's codes. Learn them, then burn the card.";
        }
        if (s.refuge()) {
            if (s.betrayed || s.localReputation < CODES_TRUST || s.present() == 0) {
                return "We don't know you well enough for that.";
            }
            give(g, c, UrbanMissions.CODES_REFUGE, s, "One of the hidden civilians gives you the relay's codes.");
            return "The relay listens for a code before it carries anything. Here. We never thought we'd use it again.";
        }
        return null;
    }

    private static void give(Game g, UrbanMissions.Comms c, int source, UrbanSite s, String line) {
        c.codes = source;
        c.codesFrom = s.id;
        g.log(line);
        g.audio.playQuest();
    }

    /** What a person says of the parts still missing, once the rumour is known. */
    private static String partsLine(UrbanMissions.Comms c, UrbanSite s) {
        if ((c.recovered & CommsComponents.Kind.TRANSMITTER.bit) == 0) {
            return s.stronghold() ? "We keep a transmitter module in our stores. The security posts had them in their"
                    + " armouries, too." : "The security posts kept transmitter modules in their armouries.";
        }
        if ((c.recovered & CommsComponents.Kind.CELLS.bit) == 0) {
            return "Power cells? Warehouse tool chests, or a clinic's utility room. They kept spares for the lights.";
        }
        if ((c.recovered & CommsComponents.Kind.ANTENNA.bit) == 0) {
            return "The office blocks strung their own antennas. Look in their utility lockers, or up on the roofs.";
        }
        if (c.codes == UrbanMissions.CODES_NONE) {
            return "The Wardens kept the relay's codes. Or whoever hid from the quarantine: the old relay crews went to"
                    + " ground.";
        }
        return "Then all that's left is to get up on that roof.";
    }

    /** Where the relay is, as the people of a site would put it: its district and a direction. */
    static String where(Vec3i from, RelaySites.Site relay) {
        int cx = relay.rx() * StreetNetwork.REGION_SIZE + StreetNetwork.REGION_SIZE / 2;
        int cz = relay.rz() * StreetNetwork.REGION_SIZE + StreetNetwork.REGION_SIZE / 2;
        return DistrictRules.of(UrbanDistrict.CIVIC).displayName() + " " + compass(cx - from.x(), cz - from.z())
                + " of here";
    }

    /** One of eight compass words for a direction; north is toward negative z. */
    static String compass(double dx, double dz) {
        double angle = Math.atan2(dx, -dz);
        int sector = Math.floorMod((int) Math.round(angle / (Math.PI / 4.0)), 8);
        return switch (sector) {
            case 0 -> "north";
            case 1 -> "north-east";
            case 2 -> "east";
            case 3 -> "south-east";
            case 4 -> "south";
            case 5 -> "south-west";
            case 6 -> "west";
            default -> "north-west";
        };
    }

    // ------------------------------------------------------------------
    // The parts and the relay itself
    // ------------------------------------------------------------------

    /** A stack taken from a container into the pack: a part of a kind not yet recovered is recovered. */
    public static void onTaken(Game g, ItemType type, int count) {
        CommsComponents.Kind kind = CommsComponents.Kind.of(type);
        if (!applies(g.world) || kind == null || count <= 0) { return; }
        UrbanMissions.Comms c = comms(g);
        if ((c.recovered & kind.bit) != 0) { return; }
        c.recovered |= kind.bit;
        g.log("You have the relay's " + kind.noun + " (" + c.partsRecovered() + " of 3 kinds of part).");
        g.audio.playQuest();
    }

    /**
     * Medium tick: a survivor standing on the relay's roof beside its cabinet has found it, which
     * counts as hearing of it. The body's own place decides, in a loaded column; nothing seen from
     * afar or from a camera does.
     */
    public static void mediumTick(Game g, float dt) {
        if (!applies(g.world) || g.player == null || g.player.dead) { return; }
        UrbanMissions.Comms c = comms(g);
        if (c.relayFound) { return; }
        RelaySites.Site r = g.world.urban.relay();
        if (r == null || !standsAt(g, r)) { return; }
        c.relayFound = true;
        g.log(c.rumoured ? "You find the old relay mast. Its cabinet is dead and its antenna gone."
                : "An old relay mast stands on this roof. Its cabinet is dead and its antenna gone.");
        c.rumoured = true;
        g.audio.playDiscover();
    }

    /** Whether the player's body stands on the relay's roof within reach of its cabinet. */
    public static boolean standsAt(Game g, RelaySites.Site r) {
        Vec3i cab = r.relay();
        if (!g.world.isColumnLoaded(cab.x(), cab.z())) { return false; }
        float dx = g.player.pos.x - (cab.x() + .5f), dz = g.player.pos.z - (cab.z() + .5f);
        float dy = g.player.pos.y - cab.y();
        return dx * dx + dz * dz <= FOUND_REACH * FOUND_REACH && dy > -.5f && dy < 1.5f;
    }

    // ------------------------------------------------------------------
    // What the HUD and the map show
    // ------------------------------------------------------------------

    /** The HUD's line for the objective's next step, or null outside a layout-sixteen world. */
    public static String hudLine(Game g) {
        if (!applies(g.world)) { return null; }
        UrbanMissions.Comms c = comms(g);
        if (g.world.urban.relay() == null) { return "Comms / the city's radio is silent"; }
        if (c.complete) { return "Comms / restored: the city answered"; }
        if (!c.rumoured) { return "Comms / ask survivors about the old radio relay"; }
        int parts = c.partsRecovered();
        if (parts < 3) { return "Comms / recover the relay's parts: " + parts + " of 3"; }
        // C3 is the better way, never a lock: without codes the call takes longer and is heard longer.
        if (c.codes == UrbanMissions.CODES_NONE && !c.relayRepaired()) { return "Comms / learn the relay's codes (or do without)"; }
        if (!c.relayRepaired()) { return c.relayFound ? "Comms / fit the parts to the relay mast" : "Comms / find the relay mast"; }
        if (!c.radioBuilt) { return "Comms / build a radio workstation at a workbench"; }
        if (c.broadcast > 0f) { return "Comms / broadcasting: " + (int) c.broadcast + " of " + (int) c.required() + " s"; }
        return "Comms / broadcast from a powered radio in your refuge";
    }

    /** Trust each camp and stronghold the survivor knows gains when the city answers the call. */
    public static final float CITY_ANSWERS = 10f;

    /**
     * The city answers the survivor's call (C5): every camp and stronghold the survivor has found,
     * with people at home, that is not hostile and was never betrayed, trusts them
     * {@value #CITY_ANSWERS} more. Called once, when the objective completes.
     */
    public static void cityAnswers(Game g) {
        int heard = 0;
        for (UrbanSite s : UrbanSiteDirector.sites(g.world)) {
            if (!(s.camp() || s.stronghold()) || !s.discovered || s.present() == 0 || s.hostile() || s.betrayed) {
                continue;
            }
            UrbanSiteRelations.trust(g, s, CITY_ANSWERS, null);
            heard++;
        }
        if (heard > 0) {
            g.log("Word of your call reaches " + (heard == 1 ? "the survivors you know" : heard + " of the survivors' holds you"
                    + " know") + ": they think better of you.");
        }
    }

    /**
     * Where the objective points.
     *
     * @param id       a stable identity for the marker
     * @param position the cell it points at
     * @param exact    false for a rumour's rough place
     * @param label    words for the navigation line
     */
    public record Target(String id, Vec3i position, boolean exact, String label) {
    }

    /**
     * The objective's marker: the rumoured relay's district centre, the relay's cabinet once found,
     * then, with the relay repaired, the home refuge's bed where the call must go out from. Null
     * before the rumour, once the city has answered, or outside a layout-sixteen world.
     */
    public static Target target(Game g) {
        if (!applies(g.world)) { return null; }
        UrbanMissions.Comms c = comms(g);
        RelaySites.Site r = g.world.urban.relay();
        if (r == null || !c.rumoured || c.complete) { return null; }
        if (c.relayRepaired()) {
            var home = g.world.urban.refuges.home();
            return home == null || home.sleep == null ? null
                    : new Target("urban:refuge:" + UrbanIds.format(home.floor), home.sleep, true, "Your refuge");
        }
        String id = "urban:relay:" + UrbanIds.format(r.building());
        if (c.relayFound) { return new Target(id, r.relay(), true, "Relay mast"); }
        int cx = r.rx() * StreetNetwork.REGION_SIZE + StreetNetwork.REGION_SIZE / 2;
        int cz = r.rz() * StreetNetwork.REGION_SIZE + StreetNetwork.REGION_SIZE / 2;
        return new Target(id, new Vec3i(cx, r.relay().y(), cz), false, "Rumoured relay");
    }
}
