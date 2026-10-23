package com.veylon;

import com.veylon.entity.Npc;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.RefugeExposure;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanIndoorQueries;
import com.veylon.world.urban.UrbanRefuges;

import java.util.Locale;

/**
 * Claiming refuges in a layout-fourteen city (urban milestone 18), at a bed.
 *
 * <ul>
 *   <li><b>What is claimed.</b> One storey of one building: the rooms the plan draws on it, named
 *       by its floor id. Never a roof, a station or anything beyond that storey, and at most
 *       {@value UrbanRefuges#MAX_CLAIMS} at once.</li>
 *   <li><b>How.</b> Crouch and F on a bed (a bed of the building's or a bedroll put down) while
 *       standing on that storey, with the whole building in view. Crouch and F on a claimed bed
 *       twice gives the refuge up; its barricades and storage stay as they are.</li>
 *   <li><b>Survivors.</b> A storey survivors hold is theirs: a held walk-up throughout, a held
 *       commercial or civic building's street floors (its upper storeys are abandoned and may be
 *       claimed, so a building can be shared), every storey of a compound and a hidden refuge's
 *       cellar. Claiming one is refused while anyone of theirs is left; once its people are all
 *       gone, the stores left to the player or the raiders driven out, it is anybody's.</li>
 *   <li><b>Hostiles.</b> A storey with something hostile on it cannot be claimed; once claimed,
 *       nothing new is let in to appear inside it, as with rooms survivors hold.</li>
 *   <li><b>Bed and home.</b> The claiming bed is where the refuge is slept in, and the refuge
 *       becomes home. Sleeping in another bed of a claimed refuge makes that bed its sleeping
 *       place and that refuge home. After death the survivor wakes at home's bed, or the nearest
 *       safe cell of its storey, before falling back to the starter refuge.</li>
 * </ul>
 * The only transient state is a pending give-up, which a new or loaded world forgets.
 */
final class RefugeSystem {
    /** Game minutes within which a second crouched F on a claimed bed gives the refuge up. */
    static final double GIVE_UP_WINDOW_MINUTES = 12;

    private final Game game;
    private long pendingGiveUp = UrbanIds.NONE;
    private double pendingUntil;

    RefugeSystem(Game game) {
        this.game = game;
    }

    void reset() {
        pendingGiveUp = UrbanIds.NONE;
        pendingUntil = 0;
    }

    static boolean applies(World w) {
        return w.urban != null && w.urban.hasRefuges();
    }

    static boolean isBed(BlockType t) {
        return t == BlockType.CAMP_BED || t == BlockType.BEDROLL;
    }

    private UrbanIndoorQueries.Location locate(Vec3i p) {
        return UrbanIndoorQueries.location(game.world, p.x(), p.y(), p.z());
    }

    private static Vec3i feet(float x, float y, float z) {
        return new Vec3i((int) Math.floor(x), (int) Math.floor(y + .01f), (int) Math.floor(z));
    }

    // ------------------------------------------------------------------
    // The rules
    // ------------------------------------------------------------------

    /** Why the storey a bed stands on cannot be claimed, or null when it can. Changes nothing. */
    String claimRefusal(Vec3i bed) {
        UrbanIndoorQueries.Location here = locate(bed);
        if (here == null || here.building().family() == BuildingPlan.Family.TRANSIT) {
            return "Only a building's rooms can be claimed";
        }
        BuildingPlan b = here.building();
        BuildingPlan.Floor floor = here.floor();
        if (floor.index() >= b.storeys()) { return "A roof is no refuge"; }
        UrbanIndoorQueries.Location mine = locate(feet(game.player.pos.x, game.player.pos.y, game.player.pos.z));
        if (mine == null || mine.floor().id() != floor.id()) { return "Stand in these rooms to claim them"; }
        if (!loaded(b.parcel())) { return "Look over the whole building first"; }
        String held = heldBy(b, floor);
        if (held != null) { return held; }
        for (Npc n : game.entities.npcs) {
            if (n.dead || !n.hostileToPlayer()) { continue; }
            UrbanIndoorQueries.Location there = locate(feet(n.pos.x, n.pos.y, n.pos.z));
            if (there != null && there.floor().id() == floor.id()) { return "Something hostile is still in these rooms"; }
        }
        if (game.world.urban.refuges.full()) {
            return "You keep three refuges already: give one up first";
        }
        return null;
    }

    /** Every chunk column of the building is generated, so its storey is all there. */
    private boolean loaded(ParcelPlan parcel) {
        for (int x = parcel.x0(); ; x = Math.min(parcel.x1(), x + 16)) {
            for (int z = parcel.z0(); ; z = Math.min(parcel.z1(), z + 16)) {
                if (!game.world.isColumnLoaded(x, z)) { return false; }
                if (z == parcel.z1()) { break; }
            }
            if (x == parcel.x1()) { return true; }
        }
    }

    /**
     * Whose rooms these are, as the reason a claim is refused, or null when nobody living holds
     * them. Reads registered sites only: one never met still has all its people.
     */
    private String heldBy(BuildingPlan b, BuildingPlan.Floor floor) {
        SurvivorSites.Site plan = SurvivorSites.owner(game.world.urban, b.id());
        boolean held = b.variant() == BuildingPlan.Variant.OCCUPIED && (b.family().walkUp() || floor.index() <= 0);
        if (plan != null && plan.kind().compound()) { held = true; }
        if (plan != null && plan.kind() == SurvivorSites.Kind.REFUGE && UrbanIds.parent(plan.room()) == floor.id()) {
            held = true;
        }
        if (!held) { return null; }
        UrbanSite site = plan == null ? null : UrbanSiteDirector.registered(game.world, plan.id());
        if (site != null && UrbanSiteDirector.abandoned(site)) { return null; }
        if (plan == null) { return "These rooms are held by survivors: claiming them would be taking what is theirs"; }
        return switch (plan.kind()) {
            case RAIDERS -> "The raiders hold these rooms";
            case STRONGHOLD -> "These rooms belong to the stronghold";
            case REFUGE -> "People are hiding here: these rooms are theirs";
            case CAMP -> "These rooms are the camp's: claiming them would be taking what is theirs";
        };
    }

    // ------------------------------------------------------------------
    // Beds
    // ------------------------------------------------------------------

    /** The cell a bed is slept in: the planned bed's own standing cell, else the bed's or one beside it. */
    Vec3i sleepCell(Vec3i bed) {
        UrbanIndoorQueries.Location here = locate(bed);
        if (here != null && !game.world.changedBlocks.containsKey(bed)) {
            for (BuildingPlan.Fixture f : here.building().fixtures()) {
                if (f.position().equals(bed) && isBed(f.block())) { return f.target(); }
            }
        }
        if (standable(bed)) { return bed; }
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Vec3i side = bed.offset(d[0], 0, d[1]);
            if (standable(side)) { return side; }
        }
        return bed;
    }

    private boolean standable(Vec3i p) {
        World w = game.world;
        return w.isColumnLoaded(p.x(), p.z()) && w.getBlock(p.x(), p.y() - 1, p.z()).solid
                && !VoxelClearance.collides(w, p.x() + .5f, p.y() + .05f, p.z() + .5f, .6f, 1.8f, true);
    }

    /**
     * F on a bed: crouched, claim its storey or give the refuge up; standing, a bed of a claimed
     * refuge becomes where it is slept in and that refuge home, and the sleep goes ahead.
     *
     * @return true when a crouched press was a claim or a give-up, so nobody sleeps
     */
    boolean atBed(Vec3i bed) {
        if (!applies(game.world)) { return false; }
        UrbanRefuges refuges = game.world.urban.refuges;
        UrbanIndoorQueries.Location here = locate(bed);
        UrbanRefuges.Claim claim = here == null ? null : refuges.claim(here.floor().id());
        if (!game.player.crouching) {
            if (claim != null) { chooseBed(claim, bed); }
            return false;
        }
        if (claim != null) {
            giveUp(claim);
            return true;
        }
        String refusal = claimRefusal(bed);
        if (refusal != null) {
            game.log(refusal + ".");
            return true;
        }
        UrbanRefuges.Claim made = refuges.add(here.floor().id(), sleepCell(bed), game.time.totalMinutes);
        game.player.forgetPlace();
        game.log("You claim " + describe(made.floor) + " as a refuge (" + refuges.size() + " of "
                + UrbanRefuges.MAX_CLAIMS + "). It is home now: you will wake here.");
        return true;
    }

    private void chooseBed(UrbanRefuges.Claim claim, Vec3i bed) {
        UrbanRefuges refuges = game.world.urban.refuges;
        Vec3i cell = sleepCell(bed);
        boolean wasHome = refuges.homeFloor() == claim.floor;
        claim.sleep = cell;
        refuges.setHome(claim.floor);
        if (!wasHome) {
            game.log(capitalize(describe(claim.floor)) + " is home now: you will wake here.");
        }
    }

    private void giveUp(UrbanRefuges.Claim claim) {
        double now = game.time.totalMinutes;
        if (pendingGiveUp != claim.floor || now > pendingUntil) {
            pendingGiveUp = claim.floor;
            pendingUntil = now + GIVE_UP_WINDOW_MINUTES;
            game.log("Crouch and press F on the bed again to give up " + describe(claim.floor) + ".");
            return;
        }
        reset();
        game.world.urban.refuges.remove(claim.floor);
        game.player.forgetPlace();
        game.log("You give up " + describe(claim.floor) + ". What you built and stored there stays.");
    }

    /** The HUD line for an aimed bed in a layout-fourteen city, or null elsewhere. */
    String bedPrompt(Vec3i bed) {
        if (!applies(game.world)) { return null; }
        UrbanRefuges refuges = game.world.urban.refuges;
        UrbanIndoorQueries.Location here = locate(bed);
        UrbanRefuges.Claim claim = here == null ? null : refuges.claim(here.floor().id());
        if (claim != null) {
            if (pendingGiveUp == claim.floor && game.time.totalMinutes <= pendingUntil) {
                return "Crouch and press F again to give up this refuge";
            }
            boolean homeBed = refuges.homeFloor() == claim.floor && sleepCell(bed).equals(claim.sleep);
            var exposure = RefugeExposure.assess(game.world, claim);
            String note = (exposure == null ? "" : exposureLine(exposure, game.time.isNight()) + "; ")
                    + "crouch: give it up";
            return homeBed ? "[F] Sleep in your refuge  (" + note + ")"
                    : "[F] Sleep here and make this refuge home  (" + note + ")";
        }
        String refusal = claimRefusal(bed);
        return refusal == null
                ? "[F] Rest here  (crouch: claim these rooms as a refuge, " + (refuges.size() + 1) + " of "
                + UrbanRefuges.MAX_CLAIMS + ")"
                : "[F] Rest here  (" + refusal.toLowerCase(Locale.ROOT).charAt(0) + refusal.substring(1) + ")";
    }

    // ------------------------------------------------------------------
    // The night: exposure draws what is really out there
    // ------------------------------------------------------------------

    /** Blocks from an open or unbarred way in within which an infected body notices a sleeper's refuge. */
    static final float DRAW_RADIUS = 32f;
    /** Blocks from a window or open way within which a light showing at night draws one. */
    static final float LIGHT_DRAW_RADIUS = 48f;
    /** Seconds a drawn body keeps looking at the way in it was drawn to. */
    static final float DRAWN_SEARCH_SECONDS = 40f;
    /** Game minutes per block of its distance before a drawn body wakes the sleeper, and the bounds. */
    static final double WAKE_MINUTES_PER_BLOCK = 1.5, MIN_WAKE_MINUTES = 15, MAX_WAKE_MINUTES = 90;

    /**
     * What a night holds for a sleeper in a claimed refuge.
     *
     * @param exposure   the refuge's ways in and light as they stand
     * @param light      a lit light shows from it into the night
     * @param drawn      the infected body drawn to it, or null
     * @param wakeMinute the game minute the drawn body wakes the sleeper, or -1
     */
    record Night(RefugeExposure.Assessment exposure, boolean light, Npc drawn, double wakeMinute) {
        /** No way in stands open or unbarred, and no light shows into the night. */
        boolean shutTight() {
            return exposure.shutTight() && !light;
        }
    }

    /**
     * The rule for sleeping in a claimed refuge, applied once as sleep begins. A refuge shut tight,
     * with no light showing, draws nothing. Otherwise the nearest living infected body that is
     * actually out there and not panicking, within {@value #DRAW_RADIUS} blocks of a way in that
     * stands open or unbarred, or at night within {@value #LIGHT_DRAW_RADIUS} blocks of a way a
     * light shows through, comes to look at that way, and the sleeper wakes sooner the nearer it
     * was. Nothing is ever conjured: with no such body the night passes quietly, and a Creative
     * sleeper, whom no threat perceives, draws nothing.
     *
     * @return null when the sleeper is not in a claimed refuge of a layout-fourteen city
     */
    Night settleForNight() {
        if (!applies(game.world)) { return null; }
        Vec3i feet = feet(game.player.pos.x, game.player.pos.y, game.player.pos.z);
        UrbanRefuges.Claim claim = game.world.urban.refuges.at(game.world, feet.x(), feet.y(), feet.z());
        var exposure = claim == null ? null : RefugeExposure.assess(game.world, claim);
        if (exposure == null) { return null; }
        boolean light = game.time.isNight() && exposure.lightShows();
        if (exposure.shutTight() && !light || !game.player.isPerceivableByAi()) {
            return new Night(exposure, light, null, -1);
        }
        Npc drawn = null;
        RefugeExposure.Way to = null;
        double best = Double.MAX_VALUE;
        for (Npc n : game.entities.npcs) {
            if (n.dead || !n.infected() || n.panic.active()) { continue; }
            for (var way : exposure.ways()) {
                Vec3i o = way.outside();
                double d = n.distSqTo(o.x() + .5f, o.y(), o.z() + .5f);
                boolean reaches = way.admits() && d <= DRAW_RADIUS * DRAW_RADIUS
                        || light && way.showsLight() && d <= LIGHT_DRAW_RADIUS * LIGHT_DRAW_RADIUS;
                if (reaches && d < best) {
                    best = d;
                    drawn = n;
                    to = way;
                }
            }
        }
        if (drawn == null) { return new Night(exposure, light, null, -1); }
        Vec3i o = to.outside();
        drawn.lastKnown.set(o.x() + .5f, o.y(), o.z() + .5f);
        drawn.lastKnownAge = 0;
        drawn.searchTimer = Math.max(drawn.searchTimer, DRAWN_SEARCH_SECONDS);
        double wake = Math.max(MIN_WAKE_MINUTES, Math.min(MAX_WAKE_MINUTES, Math.sqrt(best) * WAKE_MINUTES_PER_BLOCK));
        return new Night(exposure, light, drawn, game.time.totalMinutes + wake);
    }

    /** A short account of a refuge's exposure for the bed prompt and the log. */
    static String exposureLine(RefugeExposure.Assessment a, boolean night) {
        int open = a.count(RefugeExposure.State.OPEN)
                + a.count(RefugeExposure.State.UNBARRED);
        String ways = open == 0 ? "shut tight" : open + (open == 1 ? " way in open" : " ways in open");
        return night && a.lightShows() ? ways + ", a light showing" : ways;
    }

    // ------------------------------------------------------------------
    // Home and description
    // ------------------------------------------------------------------

    /**
     * Claims the starter refuge's ground floor at a fresh generator-17 bootstrap: the survivor's
     * first refuge and home, slept in at the starter bed.
     */
    void claimStarter(BuildingPlan refuge, Vec3i anchor) {
        UrbanRefuges refuges = game.world.urban.refuges;
        if (refuges.size() == 0) {
            refuges.add(UrbanIds.floor(refuge.id(), 0), anchor, game.time.totalMinutes);
        }
    }

    /**
     * Stands the respawn at home: its bed's cell when safe, else the safe cell of its storey nearest
     * to it. Loads the building's chunks first; never searches beyond the storey.
     *
     * @return false when there is no home or no safe cell on it
     */
    boolean restoreRespawn() {
        if (!applies(game.world)) { return false; }
        UrbanRefuges.Claim home = game.world.urban.refuges.home();
        if (home == null || home.sleep == null) { return false; }
        Vec3i sleep = home.sleep;
        game.world.ensureChunks(sleep.x(), sleep.z(), 2, 10_000);
        UrbanIndoorQueries.Location here = locate(sleep);
        if (here == null || here.floor().id() != home.floor) { return false; }
        Vec3i best = standable(sleep) ? sleep : null;
        long bestD = Long.MAX_VALUE;
        if (best == null) {
            int y = here.floor().slabY() + 1;
            for (BuildingPlan.Room room : here.floor().rooms()) {
                ParcelPlan.Rect r = room.interior();
                for (int x = r.x0(); x <= r.x1(); x++) {
                    for (int z = r.z0(); z <= r.z1(); z++) {
                        long d = (long) (x - sleep.x()) * (x - sleep.x()) + (long) (z - sleep.z()) * (z - sleep.z());
                        Vec3i p = new Vec3i(x, y, z);
                        if (d < bestD && standable(p)) { best = p; bestD = d; }
                    }
                }
            }
        }
        if (best == null) { return false; }
        game.spawnPos.set(best.x() + .5f, best.y() + .05f, best.z() + .5f);
        return true;
    }

    /** "the first-floor flat", "the ground floor of the clinic" and the like. */
    String describe(long floor) {
        long building = UrbanIds.parent(floor);
        int index = UrbanIds.floorIndex(floor);
        String level = index < 0 ? "basement" : index == 0 ? "ground-floor" : ordinal(index) + "-floor";
        BuildingPlan b = game.world.urban.buildingById(building);
        if (b == null || b.family() == BuildingPlan.Family.APARTMENT) { return "the " + level + " flat"; }
        String place = switch (b.family()) {
            case SHOP -> "shop";
            case GROCERY -> "grocer's";
            case CLINIC -> "clinic";
            case SCHOOL -> "school";
            case OFFICE -> "office block";
            case SECURITY -> "security post";
            case WAREHOUSE -> "warehouse";
            case APARTMENT, TRANSIT -> "building";
        };
        return "the " + (index < 0 ? "basement" : level.replace("-floor", " floor")) + " of the " + place;
    }

    private static String ordinal(int n) {
        return switch (n) {
            case 1 -> "first";
            case 2 -> "second";
            case 3 -> "third";
            case 4 -> "fourth";
            default -> n + "th";
        };
    }

    private static String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
