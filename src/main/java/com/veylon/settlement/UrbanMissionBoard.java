package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CityPlanner;
import com.veylon.world.urban.CivicCatalogue;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.CompoundClaims;
import com.veylon.world.urban.DistrictCatalogue;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.RelaySites;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanMissions.Failure;
import com.veylon.world.urban.UrbanMissions.Kind;
import com.veylon.world.urban.UrbanMissions.Mission;
import com.veylon.world.urban.UrbanMissions.Status;

/**
 * The missions a layout-sixteen city's survivors give (urban milestone 20), through the same seams
 * the conversation screen, the storage screen and the tests share. A camp asks, in turn, for medicine
 * from a named clinic, for help for the people hiding below a building near it, for news of the old
 * relay mast and for clean water in its own building; a stronghold offers its transmitter module and
 * asks after the mast too. Every mission names a real object by its stable id, picked when it is
 * offered from what is still there to be had; a site gives each kind once, and a kind and object are
 * asked for once in a world, whoever asks.
 *
 * <ul>
 *   <li><b>Evidence.</b> Only the deed itself, after accepting, and only on the named object counts:
 *       medicine taken from that clinic's own containers, supplies left with or a guide given to
 *       those people, litres treated by a purifier on that camp's wiring, the survivor's own body on
 *       that roof beside the mast, the module taken from that container. Anything done elsewhere, or
 *       before, or seen from afar, counts for nothing.</li>
 *   <li><b>Reports.</b> A mission with its evidence is reported to its giver's people, who give its
 *       reward once ({@link Kind#reward} trust; the clinic's medicine handed over). The stronghold's
 *       module is the reward of its own mission: paid for with {@value #PRICE} supplies, or taken
 *       from their stores by a friend.</li>
 *   <li><b>Failure.</b> Checked every medium tick, never silently mended: the givers dead or gone, or
 *       turned hostile; the clinic's medicine stores destroyed; the hidden people dead or betrayed;
 *       the module's container destroyed; or the module taken without leave, which the stronghold's
 *       theft rules also judge. A failed mission stays failed and its reason is logged once.</li>
 * </ul>
 *
 * <p>At most {@value UrbanMissions#MAX_ACTIVE} missions are in hand at once; the records, the
 * evidence and the rewards are the world's {@link UrbanMissions}, saved with it.
 */
public final class UrbanMissionBoard {
    /** Supplies the Wardens ask for their transmitter module. */
    public static final int PRICE = 6;
    /** Medicine the clinic mission's report hands over. */
    public static final int MEDICINE = 2;

    private UrbanMissionBoard() {
    }

    /** True in a world whose rules include missions: layout sixteen. */
    public static boolean applies(World w) {
        return UrbanComms.applies(w);
    }

    private static UrbanMissions ledger(Game g) {
        return g.world.urban.missions;
    }

    // ------------------------------------------------------------------
    // Conversation
    // ------------------------------------------------------------------

    /** The conversation choice about work for a camp's or stronghold's person, or null. */
    public static String missionOption(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (!applies(g.world) || s == null || !(s.camp() || s.stronghold())) { return null; }
        Mission ready = mission(g, s, Status.READY);
        if (ready != null) { return "[0] Report: " + ready.kind.title; }
        Mission pay = mission(g, s, Kind.STRONGHOLD_COMPONENT, Status.ACTIVE);
        if (pay != null) { return "[0] Pay " + PRICE + " supplies for the transmitter module"; }
        return "[0] Ask if they need help";
    }

    /**
     * Asks a camp's or stronghold's person about work: reports a mission of theirs that is done, pays
     * for the stronghold's module, or takes the next mission they would give. Returns the answer.
     */
    public static String askForWork(Game g, Npc n) {
        UrbanSite s = UrbanSiteDirector.siteOf(g.world, n);
        if (!applies(g.world) || s == null || n.dead || !(s.camp() || s.stronghold())) { return "..."; }
        g.settlementManager.urbanSites.onResidentSeen(g, s);
        if (s.hostile()) { return "We've nothing for you."; }
        Mission ready = mission(g, s, Status.READY);
        if (ready != null) { return report(g, s, ready); }
        Mission pay = mission(g, s, Kind.STRONGHOLD_COMPONENT, Status.ACTIVE);
        if (pay != null) { return pay(g, s, pay); }
        if (s.frightened()) { return "Not now."; }
        UrbanMissions ledger = ledger(g);
        Offer offer = offer(g, s);
        if (offer == null) { return "Nothing we need from you right now."; }
        if (!ledger.canAccept()) {
            return "You've enough on your hands already (" + ledger.activeCount() + " of " + UrbanMissions.MAX_ACTIVE + ").";
        }
        Mission m = ledger.accept(offer.kind(), offer.target(), s.id, g.time.totalMinutes);
        if (m.kind == Kind.LANDMARK && !ledger.comms.rumoured) {
            // Asking the survivor to look at the mast is telling them of it: the rumour, from these people.
            ledger.comms.rumoured = true;
            ledger.comms.rumourFrom = s.id;
        }
        g.log("Mission: " + m.kind.title + ". " + objective(g, m) + ".");
        g.audio.playQuest();
        return offer.line();
    }

    /** The giver's mission of a status, or null. */
    private static Mission mission(Game g, UrbanSite s, Status status) {
        for (Mission m : ledger(g).missions()) {
            if (m.giver == s.id && m.status == status) { return m; }
        }
        return null;
    }

    private static Mission mission(Game g, UrbanSite s, Kind kind, Status status) {
        for (Mission m : ledger(g).missions()) {
            if (m.giver == s.id && m.kind == kind && m.status == status) { return m; }
        }
        return null;
    }

    /** A mission a site would give now: its kind, the object it names, and how they put it. */
    public record Offer(Kind kind, long target, String line) { }

    /**
     * The next mission a camp or stronghold would give, or null: the first kind in its order whose
     * object is still there to be had and was never asked for. Reads plans and the live world.
     */
    public static Offer offer(Game g, UrbanSite s) {
        if (!applies(g.world) || s.present() == 0 || s.hostile()) { return null; }
        UrbanMissions ledger = ledger(g);
        Vec3i home = s.plan.anchor();
        if (s.stronghold()) {
            long module = CommsComponents.strongholdModule(g.world.urban, s.id);
            if (module != UrbanIds.NONE && !gave(g, s, Kind.STRONGHOLD_COMPONENT) && ledger.find(Kind.STRONGHOLD_COMPONENT, module) == null
                    && holdsModule(g, module) && !CompoundAccess.wanted(g, s)) {
                return new Offer(Kind.STRONGHOLD_COMPONENT, module, "We keep a transmitter module in our stores. "
                        + PRICE + " supplies for the ward and it's yours, or be one of us and take it.");
            }
            return gave(g, s, Kind.LANDMARK) ? null : landmark(g, home);
        }
        long clinic = gave(g, s, Kind.CLINIC_SUPPLIES) ? UrbanIds.NONE : clinicFor(g, s);
        if (clinic != UrbanIds.NONE) {
            BuildingPlan b = g.world.urban.buildingById(clinic);
            String name = CivicCatalogue.signText(g.world.seed, b);
            return new Offer(Kind.CLINIC_SUPPLIES, clinic, "Our dressings are gone. The " + name + " " + direction(home, centre(b))
                    + " had a dispensary. Bring us " + MEDICINE + " from there and we'll make it worth your while.");
        }
        SurvivorSites.Site refuge = gave(g, s, Kind.HIDDEN_AID) ? null : refugeFor(g, s);
        if (refuge != null) {
            return new Offer(Kind.HIDDEN_AID, refuge.id(), "Some nights we hear people under the street " + direction(home,
                    refuge.anchor()) + ". Someone's hiding down there. Find them and see they have what they need.");
        }
        Offer mast = gave(g, s, Kind.LANDMARK) ? null : landmark(g, home);
        if (mast != null) { return mast; }
        if (!gave(g, s, Kind.CAMP_UTILITY) && ledger.find(Kind.CAMP_UTILITY, s.id) == null) {
            return new Offer(Kind.CAMP_UTILITY, s.id, "The taps here are nearly dry. Get a water purifier working on this"
                    + " building's wiring and we'll owe you.");
        }
        return null;
    }

    /** Whether a site has given a mission of a kind, in any state: each site gives each kind once. */
    private static boolean gave(Game g, UrbanSite s, Kind kind) {
        for (Mission m : ledger(g).missions()) {
            if (m.giver == s.id && m.kind == kind) { return true; }
        }
        return false;
    }

    private static Offer landmark(Game g, Vec3i home) {
        RelaySites.Site relay = g.world.urban.relay();
        if (relay == null || g.world.urban.missions.comms.relayFound
                || ledger(g).find(Kind.LANDMARK, relay.building()) != null) {
            return null;
        }
        return new Offer(Kind.LANDMARK, relay.building(), "There's talk of an old relay mast on a rooftop in the "
                + UrbanComms.where(home, relay) + ". Go and see if it still stands.");
    }

    /**
     * The nearest clinic to a camp, within a region of it, unheld and not picked clean, outside every
     * compound, whose dispensary still holds medicine, never asked for before; or {@link UrbanIds#NONE}.
     */
    static long clinicFor(Game g, UrbanSite camp) {
        var u = g.world.urban;
        Vec3i a = camp.plan.anchor();
        int crx = StreetNetwork.regionOf(a.x()), crz = StreetNetwork.regionOf(a.z());
        long best = UrbanIds.NONE;
        double bestD = Double.MAX_VALUE;
        for (int rz = crz - 1; rz <= crz + 1; rz++) {
            for (int rx = crx - 1; rx <= crx + 1; rx++) {
                if (!CityPlanner.isPlannable(rx, rz)) { continue; }
                DistrictPlan d = u.regionPlan(rx, rz);
                CompoundClaims claims = u.routes(rx, rz).claims();
                for (ParcelPlan p : d.parcels()) {
                    if (claims.claims(p.index()) || DistrictCatalogue.family(g.world.seed, d, p) != BuildingPlan.Family.CLINIC) {
                        continue;
                    }
                    BuildingPlan b = u.buildingAt(p.x0(), p.z0());
                    if (b == null || b.family() != BuildingPlan.Family.CLINIC || b.variant() == BuildingPlan.Variant.OCCUPIED
                            || b.variant() == BuildingPlan.Variant.PICKED_CLEAN
                            || ledger(g).find(Kind.CLINIC_SUPPLIES, b.id()) != null || !dispensaryStocked(g, b)) {
                        continue;
                    }
                    double dist = centre(b).distSq(a.x(), a.y(), a.z());
                    if (dist < bestD) {
                        bestD = dist;
                        best = b.id();
                    }
                }
            }
        }
        return best;
    }

    /** Whether some dispensary cabinet of a clinic still has medicine to give: untouched, or holding some. */
    static boolean dispensaryStocked(Game g, BuildingPlan b) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.CONTAINER
                    || BuildingFurnishing.containerUse(b, f) != BuildingFurnishing.ContainerUse.MEDICINE_CABINET) {
                continue;
            }
            UrbanContainers.Entry e = g.world.urban.containers.byId(f.id());
            if (e == null) { return true; }
            if (e.state.retired()) { continue; }
            Inventory inv = g.world.crateContents.get(e.position);
            if (inv != null && medicineIn(inv) > 0) { return true; }
        }
        return false;
    }

    /** Whether any dispensary cabinet of a clinic is still standing. */
    private static boolean dispensaryStanding(Game g, BuildingPlan b) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.CONTAINER
                    && BuildingFurnishing.containerUse(b, f) == BuildingFurnishing.ContainerUse.MEDICINE_CABINET) {
                UrbanContainers.Entry e = g.world.urban.containers.byId(f.id());
                if (e == null || !e.state.retired()) { return true; }
            }
        }
        return false;
    }

    /** The nearest hidden refuge within a region of a camp whose people are home, alive and not betrayed. */
    static SurvivorSites.Site refugeFor(Game g, UrbanSite camp) {
        var u = g.world.urban;
        Vec3i a = camp.plan.anchor();
        int crx = StreetNetwork.regionOf(a.x()), crz = StreetNetwork.regionOf(a.z());
        SurvivorSites.Site best = null;
        double bestD = Double.MAX_VALUE;
        for (int rz = crz - 1; rz <= crz + 1; rz++) {
            for (int rx = crx - 1; rx <= crx + 1; rx++) {
                if (!CityPlanner.isPlannable(rx, rz)) { continue; }
                SurvivorSites.Site r = u.sites(rx, rz).refuge();
                if (r == null || ledger(g).find(Kind.HIDDEN_AID, r.id()) != null || !hiddenAndWell(g, r.id())) { continue; }
                double dist = r.anchor().distSq(a.x(), a.y(), a.z());
                if (dist < bestD) {
                    bestD = dist;
                    best = r;
                }
            }
        }
        return best;
    }

    /** Hidden people still home, alive and not betrayed: true for a refuge nobody has met yet. */
    private static boolean hiddenAndWell(Game g, long refuge) {
        UrbanSite r = UrbanSiteDirector.registered(g.world, refuge);
        return r == null || r.present() > 0 && r.relocation == UrbanSite.Relocation.HOME && !r.betrayed && !r.stashReleased;
    }

    /** Whether a stronghold's module container still holds the module: untouched, or with it inside. */
    static boolean holdsModule(Game g, long container) {
        UrbanContainers.Entry e = g.world.urban.containers.byId(container);
        if (e == null) { return true; }
        if (e.state.retired()) { return false; }
        Inventory inv = g.world.crateContents.get(e.position);
        return inv != null && inv.count(ItemType.TRANSMITTER_MODULE) > 0;
    }

    // ------------------------------------------------------------------
    // Reports, the price and the rewards
    // ------------------------------------------------------------------

    /** A finished mission reported to its givers: the reward, once. */
    private static String report(Game g, UrbanSite s, Mission m) {
        if (m.kind == Kind.CLINIC_SUPPLIES) {
            if (medicineIn(g.player.inventory) < MEDICINE) {
                return "You found the clinic? Then bring us " + MEDICINE + " dressings or medicine from it.";
            }
            if (UrbanSiteDialogue.stashRoom(g, s, ItemType.BANDAGE, MEDICINE) < MEDICINE) { return "We've no room to keep it."; }
            handOver(g, s, MEDICINE, true);
        }
        m.status = Status.COMPLETED;
        m.rewarded = true;
        UrbanSiteRelations.trust(g, s, m.kind.reward, null);
        g.log("Mission complete: " + m.kind.title + ". " + UrbanSiteRelations.capital(UrbanSiteRelations.name(s))
                + " thanks you.");
        g.audio.playQuest();
        return switch (m.kind) {
            case CLINIC_SUPPLIES -> "That will see us through the next fever. Thank you.";
            case HIDDEN_AID -> "You found them. Good. Nobody should hide down there alone.";
            case CAMP_UTILITY -> "Clean water from our own taps. We won't forget it.";
            case LANDMARK -> "It still stands? Then maybe it can still talk.";
            case STRONGHOLD_COMPONENT -> "Use the module well. The ward doesn't give twice.";
        };
    }

    /**
     * The Wardens' price for their module: {@value #PRICE} supplies into their stores, then the
     * module out of the container it was kept in and into the pack, all or nothing.
     */
    private static String pay(Game g, UrbanSite s, Mission m) {
        if (!holdsModule(g, m.target)) {
            fail(g, m, Failure.TARGET_GONE);
            return "The module's gone. There's nothing left to sell you.";
        }
        if (suppliesIn(g.player.inventory) < PRICE) { return "Bring " + PRICE + " food, water or medicine for the ward."; }
        Vec3i cell = containerCell(g, m.target);
        if (cell == null) { return "Not now."; }
        g.world.getOrCreateChunk(Math.floorDiv(cell.x(), 16), Math.floorDiv(cell.z(), 16));
        UrbanContainers.Entry e = g.world.urban.containers.touch(g.world, cell);
        Inventory store = e == null ? null : g.world.crateContents.get(cell);
        if (store == null || store.count(ItemType.TRANSMITTER_MODULE) == 0) {
            fail(g, m, Failure.TARGET_GONE);
            return "The module's gone. There's nothing left to sell you.";
        }
        if (UrbanSiteDialogue.stashRoom(g, s, ItemType.DRIED_BERRY, PRICE) < PRICE || !roomFor(g.player.inventory)) {
            return "Make room first.";
        }
        handOver(g, s, PRICE, false);
        store.remove(ItemType.TRANSMITTER_MODULE, 1);
        UrbanSiteNeeds.compact(g, cell);
        g.player.inventory.add(ItemType.TRANSMITTER_MODULE, 1);
        UrbanComms.onTaken(g, ItemType.TRANSMITTER_MODULE, 1);
        m.progress = m.kind.required;
        m.status = Status.COMPLETED;
        m.rewarded = true;
        g.log("Mission complete: " + m.kind.title + ". The Wardens hand you their transmitter module.");
        g.audio.playQuest();
        return "A fair price. Here.";
    }

    /** Moves supplies from the pack into the site's stash: medicine only, or any food, water or medicine. */
    private static void handOver(Game g, UrbanSite s, int count, boolean medicineOnly) {
        Inventory inv = g.player.inventory;
        int left = count;
        for (int i = 0; i < inv.size() && left > 0; i++) {
            ItemStack stack = inv.get(i);
            if (stack == null || !(medicineOnly ? UrbanSiteDialogue.healing(stack.type) : supply(stack.type))) { continue; }
            ItemStack part = stack.copy();
            part.count = Math.min(stack.count, left);
            int given = UrbanSiteNeeds.give(g, s, part);
            inv.shrink(i, given);
            left -= given;
        }
    }

    private static boolean supply(ItemType t) {
        return UrbanSiteNeeds.ration(t) || t == ItemType.WATERSKIN_CLEAN || UrbanSiteDialogue.healing(t);
    }

    static int medicineIn(Inventory inv) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && UrbanSiteDialogue.healing(s.type)) { n += s.count; }
        }
        return n;
    }

    private static int suppliesIn(Inventory inv) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null && supply(s.type)) { n += s.count; }
        }
        return n;
    }

    private static boolean roomFor(Inventory inv) {
        Inventory copy = new Inventory(inv.size());
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            copy.set(i, s == null ? null : s.copy());
        }
        return copy.add(ItemType.TRANSMITTER_MODULE, 1) == 0;
    }

    /** The cell of a planned container by its fixture id, read from its building's plan. */
    static Vec3i containerCell(Game g, long fixture) {
        if (!UrbanIds.isValid(fixture) || UrbanIds.kind(fixture) != UrbanIds.Kind.FIXTURE) { return null; }
        long building = UrbanIds.parent(UrbanIds.parent(UrbanIds.parent(fixture)));
        BuildingPlan b = g.world.urban.buildingById(building);
        if (b == null) { return null; }
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.id() == fixture) { return f.position(); }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Evidence
    // ------------------------------------------------------------------

    /**
     * A stack taken from a container into the pack: medicine from the clinic a mission names, or the
     * module from the container the Wardens keep it in, by a friend or (failing the mission) a thief.
     */
    public static void onTaken(Game g, UrbanContainers.Entry entry, ItemType type, int count) {
        if (!applies(g.world) || entry == null || count <= 0) { return; }
        BuildingPlan b = g.world.urban.buildingAt(entry.position.x(), entry.position.z());
        for (Mission m : ledger(g).missions()) {
            if (m.status != Status.ACTIVE) { continue; }
            if (m.kind == Kind.CLINIC_SUPPLIES && b != null && b.id() == m.target && UrbanSiteDialogue.healing(type)) {
                advance(g, m, m.kind.required);
            } else if (m.kind == Kind.STRONGHOLD_COMPONENT && entry.id == m.target && type == ItemType.TRANSMITTER_MODULE) {
                UrbanSite giver = UrbanSiteDirector.registered(g.world, m.giver);
                if (giver != null && giver.grantsAccess()) {
                    advance(g, m, m.kind.required);
                } else {
                    fail(g, m, Failure.THEFT);
                }
            }
        }
    }

    /** Supplies the hidden people a mission names received from the survivor, beyond anything owed. */
    static void onAided(Game g, UrbanSite s, int items) {
        if (!applies(g.world) || !s.refuge() || items <= 0) { return; }
        for (Mission m : ledger(g).missions()) {
            if (m.status == Status.ACTIVE && m.kind == Kind.HIDDEN_AID && m.target == s.id) {
                advance(g, m, Math.min(m.kind.required, m.progress + items));
            }
        }
    }

    /** The hidden people a mission names agreed to move to a camp the survivor told them of. */
    static void onGuided(Game g, UrbanSite refuge) {
        if (!applies(g.world)) { return; }
        for (Mission m : ledger(g).missions()) {
            if (m.status == Status.ACTIVE && m.kind == Kind.HIDDEN_AID && m.target == refuge.id) {
                advance(g, m, m.kind.required);
            }
        }
    }

    /** Litres a purifier treated on a building's wiring this tick. */
    public static void onTreated(Game g, long building, float litres) {
        if (!applies(g.world) || litres <= 0f) { return; }
        for (Mission m : ledger(g).missions()) {
            if (m.status == Status.ACTIVE && m.kind == Kind.CAMP_UTILITY && m.target == building) {
                advance(g, m, Math.min(m.kind.required, m.progress + litres));
            }
        }
    }

    /** Raises a mission's evidence; at its requirement it is ready to report. */
    private static void advance(Game g, Mission m, float progress) {
        if (progress <= m.progress) { return; }
        m.progress = Math.min(m.kind.required, progress);
        if (m.progress + 1e-4f >= m.kind.required) {
            m.progress = m.kind.required;
            m.status = Status.READY;
            g.log("Mission ready: " + m.kind.title + ". Report back to the people who asked.");
            g.audio.playQuest();
        }
    }

    private static void fail(Game g, Mission m, Failure why) {
        if (!m.active()) { return; }
        m.status = Status.FAILED;
        m.failure = why;
        g.log("Mission failed: " + m.kind.title + ": " + why.reason + ".");
    }

    /**
     * Medium tick: the survivor's own body on the relay's roof answers a landmark mission, and every
     * mission in hand is checked against the world as it stands, failing explicitly when what it
     * needs is gone. At most {@value UrbanMissions#MAX_ACTIVE} missions are checked.
     */
    public static void mediumTick(Game g, float dt) {
        if (!applies(g.world) || g.player == null || g.player.dead) { return; }
        for (Mission m : ledger(g).missions()) {
            if (!m.active()) { continue; }
            UrbanSite giver = UrbanSiteDirector.registered(g.world, m.giver);
            if (giver == null || giver.present() == 0) {
                fail(g, m, Failure.GIVER_GONE);
                continue;
            }
            if (giver.hostile()) {
                fail(g, m, Failure.GIVER_HOSTILE);
                continue;
            }
            if (m.status != Status.ACTIVE) { continue; }
            switch (m.kind) {
                case LANDMARK -> {
                    RelaySites.Site relay = g.world.urban.relay();
                    if (relay != null && relay.building() == m.target && UrbanComms.standsAt(g, relay)) {
                        advance(g, m, m.kind.required);
                    }
                }
                case CLINIC_SUPPLIES -> {
                    BuildingPlan b = g.world.urban.buildingById(m.target);
                    if (b == null || !dispensaryStanding(g, b)) { fail(g, m, Failure.TARGET_GONE); }
                }
                case HIDDEN_AID -> {
                    UrbanSite r = UrbanSiteDirector.registered(g.world, m.target);
                    if (r != null && r.betrayed) {
                        fail(g, m, Failure.BETRAYED);
                    } else if (r != null && r.relocation == UrbanSite.Relocation.HOME && r.present() == 0) {
                        fail(g, m, Failure.TARGET_DEAD);
                    }
                }
                case STRONGHOLD_COMPONENT -> {
                    if (!holdsModule(g, m.target)) { fail(g, m, Failure.TARGET_GONE); }
                }
                case CAMP_UTILITY -> {
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // What the journal, the HUD and the map show
    // ------------------------------------------------------------------

    /** What a mission asks of the survivor now, in a few words. */
    public static String objective(Game g, Mission m) {
        if (m.status == Status.READY) { return "Report back to the people who asked"; }
        return switch (m.kind) {
            case CLINIC_SUPPLIES -> "Take medicine from the " + signOf(g, m.target) + " and bring " + MEDICINE + " back";
            case HIDDEN_AID -> "Find the people hiding below the street; leave them 3 supplies or guide them to safety";
            case CAMP_UTILITY -> "Treat 2 L in a water purifier on the camp's own wiring";
            case LANDMARK -> "See whether the old relay mast still stands";
            case STRONGHOLD_COMPONENT -> "Pay the Wardens " + PRICE + " supplies, or take the module as their friend";
        };
    }

    private static String signOf(Game g, long building) {
        BuildingPlan b = g.world.urban.buildingById(building);
        String sign = b == null ? null : CivicCatalogue.signText(g.world.seed, b);
        return sign == null ? "clinic" : sign;
    }

    /** The HUD's journal line: the first mission in hand, or null with none (or outside layout sixteen). */
    public static String journalLine(Game g) {
        if (!applies(g.world)) { return null; }
        UrbanMissions ledger = ledger(g);
        for (Mission m : ledger.missions()) {
            if (m.active()) {
                return "Missions " + ledger.activeCount() + "/" + UrbanMissions.MAX_ACTIVE + " / " + m.kind.title
                        + (m.status == Status.READY ? " (report)" : "");
            }
        }
        return null;
    }

    /**
     * Where the first mission in hand points: its givers once it is ready, otherwise what it names
     * (the clinic, the building the hidden people are under, the camp, the relay's district or roof,
     * the stronghold). Null with no mission in hand.
     */
    public static UrbanComms.Target target(Game g) {
        if (!applies(g.world)) { return null; }
        for (Mission m : ledger(g).missions()) {
            if (!m.active()) { continue; }
            Vec3i at;
            if (m.status == Status.READY) {
                UrbanSite giver = UrbanSiteDirector.registered(g.world, m.giver);
                at = giver == null ? null : giver.homeAnchor(g.world.urban);
            } else {
                at = switch (m.kind) {
                    case CLINIC_SUPPLIES -> {
                        BuildingPlan b = g.world.urban.buildingById(m.target);
                        yield b == null ? null : centre(b);
                    }
                    case HIDDEN_AID -> {
                        SurvivorSites.Site r = SurvivorSites.resolve(g.world.urban, m.target);
                        yield r == null ? null : r.anchor();
                    }
                    case CAMP_UTILITY, STRONGHOLD_COMPONENT -> {
                        UrbanSite giver = UrbanSiteDirector.registered(g.world, m.giver);
                        yield giver == null ? null : giver.plan.anchor();
                    }
                    case LANDMARK -> {
                        RelaySites.Site relay = g.world.urban.relay();
                        yield relay == null ? null : new Vec3i(relay.rx() * StreetNetwork.REGION_SIZE + StreetNetwork.REGION_SIZE / 2,
                                relay.relay().y(), relay.rz() * StreetNetwork.REGION_SIZE + StreetNetwork.REGION_SIZE / 2);
                    }
                };
            }
            if (at != null) { return new UrbanComms.Target(m.id(), at, true, m.kind.title); }
        }
        return null;
    }

    /** The middle of a building's parcel at street level. */
    private static Vec3i centre(BuildingPlan b) {
        ParcelPlan p = b.parcel();
        return new Vec3i((p.x0() + p.x1()) / 2, 41, (p.z0() + p.z1()) / 2);
    }

    /** "to the north-east", from one cell to another. */
    private static String direction(Vec3i from, Vec3i to) {
        return "to the " + UrbanComms.compass(to.x() - from.x(), to.z() - from.z());
    }
}
