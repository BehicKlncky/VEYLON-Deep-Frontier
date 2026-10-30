package com.veylon.ui;

import com.veylon.Game;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.UrbanComms;
import com.veylon.settlement.UrbanMissionBoard;
import com.veylon.settlement.UrbanSite;
import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CityPlanner;
import com.veylon.world.urban.DistrictCatalogue;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.DistrictRules;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanIndoorQueries;
import com.veylon.world.urban.UrbanMissions;
import com.veylon.world.urban.UrbanPlaces;
import com.veylon.world.urban.UrbanRefuges;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What the map and the HUD of a layout-sixteen city show (urban milestone 21), as plain data the
 * map screen draws and the tests read. Everything here is a read of what the survivor knows:
 *
 * <ul>
 *   <li>the ground plan of the streets around them, only where the city has been generated near
 *       them, and nothing beyond;</li>
 *   <li>the buildings they have entered, by name ({@code urban.visits}), while every other building
 *       is an unnamed block;</li>
 *   <li>their refuges and home; camps, strongholds and raider yards once found; hidden people only
 *       once their cellar is found;</li>
 *   <li>the objective and every mission in hand, a rumoured place as a rough area, never a point;</li>
 *   <li>a journal of the communications step and the missions, in hand, finished and failed, read
 *       from the saved ledger, so a load shows exactly what is true.</li>
 * </ul>
 *
 * <p>It reads loaded columns and pure plans only, never creating a chunk, and no planned container,
 * hidden room or unfound site ever reaches it.
 */
public final class CityMap {
    /** Cells across the map, each {@value #STEP} blocks: 128 m, as the wilderness map. */
    public static final int CELLS = 64, STEP = 2;
    /** Finished and failed missions the journal keeps showing, newest first. */
    public static final int CLOSED_SHOWN = 2;

    private CityMap() { }

    /** True in a world that draws the city map: a layout-sixteen city. */
    public static boolean applies(World w) {
        return w != null && w.urban != null && w.urban.hasMissions();
    }

    // ------------------------------------------------------------------
    // The ground plan
    // ------------------------------------------------------------------

    /** What a map cell shows. */
    public enum Ground { UNSEEN, ROAD, PAVEMENT, GREEN, WATER, YARD, BUILDING }

    /** The ground of a column: its planned surface where its chunk was generated, else unseen. */
    public static Ground ground(World w, int x, int z) {
        var chunk = w.getChunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16));
        if (chunk == null || !chunk.generated) { return Ground.UNSEEN; }
        return switch (w.urban.surfaceAt(x, z)) {
            case ROADWAY, LANE_MARK, CROSSWALK, MEDIAN -> Ground.ROAD;
            case COURTYARD, PARK, TREE_BED -> Ground.GREEN;
            case CANAL -> Ground.WATER;
            case YARD -> Ground.YARD;
            case LOT, LOT_EDGE, ENTRANCE, STAIR_CORE -> Ground.BUILDING;
            default -> Ground.PAVEMENT;
        };
    }

    // ------------------------------------------------------------------
    // Names
    // ------------------------------------------------------------------

    /** What the city calls a building family, on the map and the HUD. */
    public static String familyName(BuildingPlan.Family family) {
        return switch (family) {
            case APARTMENT -> "Flats";
            case SHOP -> "Corner shop";
            case GROCERY -> "Grocery";
            case CLINIC -> "Clinic";
            case SCHOOL -> "School";
            case OFFICE -> "Offices";
            case SECURITY -> "Security post";
            case WAREHOUSE -> "Warehouse";
            case TRANSIT -> "Transit station";
        };
    }

    /** A floor in words: basement, ground floor, floor n or roof. */
    public static String floorWords(int index, int storeys) {
        if (index < 0) { return "basement"; }
        if (index == 0) { return "ground floor"; }
        return index >= storeys ? "roof" : "floor " + index;
    }

    /** A room in words, by its first function. */
    public static String roomName(BuildingPlan.Room room) {
        if (room.functions().isEmpty()) { return "Room"; }
        return switch (room.functions().getFirst()) {
            case ENTRY -> "Entry";
            case KITCHEN -> "Kitchen";
            case LIVING -> "Living room";
            case BEDROOM -> "Bedroom";
            case BATHROOM -> "Bathroom";
            case STORAGE, BASEMENT_STORAGE -> "Storage";
            case UTILITY, BASEMENT_UTILITY -> "Utility";
            case CORRIDOR_STAIRS -> "Stairs";
            case SHOP_FLOOR -> "Shop floor";
            case STOCKROOM -> "Stockroom";
            case CLINIC -> "Clinic";
            case PHARMACY -> "Dispensary";
            case CLASSROOM -> "Classroom";
            case OFFICE -> "Office";
            case SECURITY -> "Armoury";
            case WAREHOUSE -> "Warehouse";
            case WORKSHOP -> "Workshop";
            case HIDDEN_REFUGE -> "Hidden cellar";
            case PLATFORM -> "Platform";
            case TRACK -> "Track";
            case SERVICE -> "Service room";
        };
    }

    /** The district name of a column's region, or null where nothing is planned. */
    public static String districtName(World w, int x, int z) {
        UrbanDistrict d = w.urban.districtAt(x, z);
        return d == null ? null : DistrictRules.of(d).displayName();
    }

    /**
     * The HUD's line for where the survivor stands: the building and floor inside one ("Clinic ·
     * floor 2", with the route place where it says more, such as "your refuge"), or the kind of
     * ground outside ("Street", "Courtyard", "Transit tunnel").
     */
    public static String hereLine(Game g) {
        World w = g.world;
        int x = (int) Math.floor(g.player.pos.x), y = (int) Math.floor(g.player.pos.y + .01f);
        int z = (int) Math.floor(g.player.pos.z);
        UrbanPlaces.Place place = g.player.place;
        UrbanIndoorQueries.Location here = UrbanIndoorQueries.location(w, x, y, z);
        if (here != null && (here.room() != null || here.floor().index() >= here.building().storeys())) {
            BuildingPlan b = here.building();
            String line = familyName(b.family()) + " · "
                    + (b.family() == BuildingPlan.Family.TRANSIT ? "below the street"
                    : floorWords(here.floor().index(), b.storeys()));
            String extra = switch (place) {
                case REFUGE -> "your refuge";
                case CAMP -> "survivor camp";
                case STRONGHOLD -> "stronghold";
                case RAIDER_YARD -> "raider yard";
                case HIDDEN_CELLAR -> "hidden cellar";
                default -> null;
            };
            return extra == null ? line : line + " · " + extra;
        }
        if (place != UrbanPlaces.Place.NONE) { return place.label; }
        if (!w.isColumnLoaded(x, z)) { return "Out in the city"; }
        return switch (w.urban.surfaceAt(x, z)) {
            case ROADWAY, LANE_MARK, CROSSWALK, MEDIAN -> "Street";
            case SIDEWALK -> "Pavement";
            case ALLEY -> "Alley";
            case PASSAGE -> "Passage between buildings";
            case COURTYARD, COURTYARD_PATH -> "Courtyard";
            case PARK, PARK_PATH, TREE_BED -> "Park";
            case PLAZA -> "Square";
            case CANAL -> "Canal";
            case BRIDGE -> "Bridge";
            case YARD -> "Yard";
            case LOT, LOT_EDGE, ENTRANCE, STAIR_CORE -> "Beside a building";
            default -> "Out in the city";
        };
    }

    // ------------------------------------------------------------------
    // Buildings the survivor entered
    // ------------------------------------------------------------------

    /** An entered building: its id, its parcel on the ground and its name. */
    public record Building(long id, ParcelPlan parcel, String name) { }

    /**
     * The entered buildings whose parcels lie in a block rectangle, from the visits ledger and the
     * regions' pure plans; no building plan is built and no chunk created.
     */
    public static List<Building> enteredBuildings(World w, int x0, int z0, int x1, int z1) {
        List<Building> out = new ArrayList<>();
        if (w.urban.visits.buildingCount() == 0) { return out; }
        for (int rx = StreetNetwork.regionOf(x0); rx <= StreetNetwork.regionOf(x1); rx++) {
            for (int rz = StreetNetwork.regionOf(z0); rz <= StreetNetwork.regionOf(z1); rz++) {
                if (!CityPlanner.isPlannable(rx, rz)) { continue; }
                DistrictPlan plan = w.urban.regionPlan(rx, rz);
                for (ParcelPlan p : plan.parcels()) {
                    long id = UrbanIds.building(p.id(), 0);
                    if (p.x1() < x0 || p.x0() > x1 || p.z1() < z0 || p.z0() > z1 || !w.urban.visits.entered(id)) { continue; }
                    out.add(new Building(id, p, familyName(DistrictCatalogue.family(w.seed, plan, p))));
                }
            }
        }
        return out;
    }

    /** The district of a region, when the survivor has walked it; null otherwise. */
    public static String walkedDistrict(World w, int rx, int rz) {
        if (!CityPlanner.isPlannable(rx, rz) || !w.urban.visits.walked(UrbanIds.district(rx, rz))) { return null; }
        return DistrictRules.of(w.urban.regionPlan(rx, rz).district).displayName();
    }

    // ------------------------------------------------------------------
    // Markers
    // ------------------------------------------------------------------

    /** What a marker stands for. */
    public enum Kind { HOME, REFUGE, CAMP, STRONGHOLD, RAIDERS, HIDDEN, OBJECTIVE, RUMOUR }

    /**
     * A place on the map.
     *
     * @param kind     what it is
     * @param position the cell it marks; a rumour's middle
     * @param label    its words
     * @param radius   blocks a rumour may lie within; 0 for a known place
     * @param tracked  whether the HUD's navigation line follows it
     * @param hostile  a site that turned on the survivor, or raiders
     * @param friendly a site that counts the survivor a friend
     */
    public record Marker(Kind kind, Vec3i position, String label, int radius, boolean tracked,
                         boolean hostile, boolean friendly) { }

    /** Blocks a hidden-people rumour may lie within: their city block. */
    private static final int BLOCK_RUMOUR = 40;
    /** Blocks a district rumour may lie within: half a region. */
    private static final int DISTRICT_RUMOUR = StreetNetwork.REGION_SIZE / 2;

    /**
     * Every marker the survivor's knowledge allows: refuges and home, found sites, the objective and
     * each mission in hand. The order is drawing order: the tracked objective last, on top.
     */
    public static List<Marker> markers(Game g) {
        List<Marker> out = new ArrayList<>();
        World w = g.world;
        UrbanRefuges refuges = w.urban.refuges;
        for (UrbanRefuges.Claim claim : refuges.claims()) {
            if (claim.sleep == null) { continue; }
            boolean home = claim.floor == refuges.homeFloor();
            out.add(new Marker(home ? Kind.HOME : Kind.REFUGE, claim.sleep, home ? "Home" : "Refuge", 0, false, false, false));
        }
        for (UrbanSite s : UrbanSiteDirector.sites(w)) {
            Marker m = site(w, s);
            if (m != null) { out.add(m); }
        }
        QuestObjectiveView.Objective tracked = QuestObjectiveView.resolve(g);
        List<Marker> objectives = new ArrayList<>();
        UrbanComms.Target comms = UrbanComms.target(g);
        if (comms != null) { objectives.add(objective(comms, comms.exact() ? 0 : DISTRICT_RUMOUR, tracked)); }
        for (UrbanMissions.Mission m : w.urban.missions.missions()) {
            UrbanComms.Target t = UrbanMissionBoard.target(g, m);
            if (t == null) { continue; }
            int radius = t.exact() ? 0 : m.kind == UrbanMissions.Kind.HIDDEN_AID ? BLOCK_RUMOUR : DISTRICT_RUMOUR;
            objectives.add(objective(t, radius, tracked));
        }
        objectives.sort((a, b) -> Boolean.compare(a.tracked(), b.tracked()));
        out.addAll(objectives);
        return out;
    }

    private static Marker objective(UrbanComms.Target t, int radius, QuestObjectiveView.Objective tracked) {
        boolean follows = tracked != null && tracked.targetId().equals(t.id());
        return new Marker(t.exact() ? Kind.OBJECTIVE : Kind.RUMOUR, t.position(), t.label(), radius, follows, false, false);
    }

    /** A found site's marker; hidden people only once their cellar is in the discovery ledger. */
    private static Marker site(World w, UrbanSite s) {
        if (!s.discovered) { return null; }
        Kind kind;
        String label;
        if (s.raiders()) {
            kind = Kind.RAIDERS;
            label = s.cleared ? "Raider yard (taken)" : "Raider yard";
        } else if (s.refuge()) {
            if (!w.urban.discovery.discovered(s.plan.room())) { return null; }
            kind = Kind.HIDDEN;
            label = s.relocation == UrbanSite.Relocation.HOME ? "Hidden refuge" : "Hidden refuge (moved)";
        } else {
            kind = s.stronghold() ? Kind.STRONGHOLD : Kind.CAMP;
            label = (s.stronghold() ? "Stronghold" : "Camp")
                    + (s.alignment == Settlement.Alignment.HOSTILE ? " (hostile)"
                    : s.alignment == Settlement.Alignment.FRIENDLY ? " (friends)" : "");
        }
        return new Marker(kind, s.plan.anchor(), label, 0, false,
                s.raiders() || s.alignment == Settlement.Alignment.HOSTILE, s.alignment == Settlement.Alignment.FRIENDLY);
    }

    // ------------------------------------------------------------------
    // The journal
    // ------------------------------------------------------------------

    /** How a journal entry stands. */
    public enum State { COMMS, OPEN, READY, DONE, FAILED, HINT }

    /**
     * One journal entry.
     *
     * @param state   how it stands
     * @param heading its first line
     * @param detail  what to do, or why it ended
     * @param where   who it is for and where it points, or empty
     */
    public record Entry(State state, String heading, String detail, String where) { }

    /**
     * The journal: the communications step, each mission in hand in the order accepted, then the
     * last {@value #CLOSED_SHOWN} finished or failed, newest first, and a hint when nothing is in
     * hand. Read straight from the world's mission ledger, never from a cache.
     */
    public static List<Entry> journal(Game g) {
        List<Entry> out = new ArrayList<>();
        UrbanMissions ledger = g.world.urban.missions;
        String comms = UrbanComms.hudLine(g);
        if (comms != null) {
            UrbanComms.Target t = UrbanComms.target(g);
            out.add(new Entry(State.COMMS, "Restore the city's radio", capital(comms.replaceFirst("^Comms / ", "")),
                    t == null ? "" : QuestObjectiveView.navigationLabel(g, objective(t)).replace("  [M]", "")));
        }
        for (UrbanMissions.Mission m : ledger.missions()) {
            if (!m.active()) { continue; }
            boolean ready = m.status == UrbanMissions.Status.READY;
            UrbanComms.Target t = UrbanMissionBoard.target(g, m);
            String where = "For " + giver(g, m.giver) + (t == null ? ""
                    : " · " + QuestObjectiveView.navigationLabel(g, objective(t)).replaceFirst("^.* - ", ""));
            out.add(new Entry(ready ? State.READY : State.OPEN, m.kind.title + (ready ? ": report back" : ""),
                    UrbanMissionBoard.objective(g, m), where.replace("  [M]", "")));
        }
        int closed = 0;
        List<UrbanMissions.Mission> all = ledger.missions();
        for (int i = all.size() - 1; i >= 0 && closed < CLOSED_SHOWN; i--) {
            UrbanMissions.Mission m = all.get(i);
            if (m.active()) { continue; }
            boolean failed = m.status == UrbanMissions.Status.FAILED;
            out.add(new Entry(failed ? State.FAILED : State.DONE, (failed ? "Failed: " : "Done: ") + m.kind.title,
                    failed ? capital(m.failure.reason) + "." : "Reported to " + giver(g, m.giver) + ".", ""));
            closed++;
        }
        if (ledger.missions().isEmpty() && !ledger.comms.rumoured) {
            // A new survivor's first steps, at more length than the HUD's log can hold.
            out.add(new Entry(State.HINT, "First steps",
                    "Search [F] cupboards, shelves and lockers for supplies. Searching takes time and makes noise.", ""));
            out.add(new Entry(State.HINT, "Your refuge",
                    "Bar its doors with crouch and F, then rest in its bed [F]. Craft [C] at its workbench.", ""));
            out.add(new Entry(State.HINT, "Survivors",
                    "Camps and strongholds trade and talk. Ask them for work [0] and about the radio [9].", ""));
        } else if (ledger.activeCount() == 0) {
            out.add(new Entry(State.HINT, "No missions in hand",
                    "Survivors at camps and strongholds may need help: ask them [0] when you talk.", ""));
        }
        return out;
    }

    private static QuestObjectiveView.Objective objective(UrbanComms.Target t) {
        return new QuestObjectiveView.Objective(t.id(), t.id(), t.position(), QuestObjectiveView.Phase.TARGET,
                t.exact(), t.label());
    }

    /** Who a mission is for, as the journal names them. */
    private static String giver(Game g, long site) {
        UrbanSite s = UrbanSiteDirector.registered(g.world, site);
        if (s == null) { return "survivors who are gone"; }
        return s.stronghold() ? "the Wardens" : s.refuge() ? "the hidden people" : "the camp";
    }

    /** "Home: 45 m SW", or null without a home with a bed. */
    public static String homeLine(Game g) {
        UrbanRefuges.Claim home = g.world.urban.refuges.home();
        if (home == null || home.sleep == null) { return null; }
        double dx = home.sleep.x() + .5 - g.player.pos.x, dz = home.sleep.z() + .5 - g.player.pos.z;
        int metres = (int) Math.round(Math.hypot(dx, dz));
        return metres < 3 ? "Home: you are there" : "Home: " + metres + " m " + compass(dx, dz);
    }

    /** One of eight compass letters; north is toward negative z. */
    static String compass(double dx, double dz) {
        int sector = Math.floorMod((int) Math.round(Math.atan2(dx, -dz) / (Math.PI / 4.0)), 8);
        return new String[]{"N", "NE", "E", "SE", "S", "SW", "W", "NW"}[sector];
    }

    private static String capital(String s) {
        return s == null || s.isEmpty() ? "" : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }
}
