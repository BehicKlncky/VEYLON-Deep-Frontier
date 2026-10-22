package com.veylon.settlement;

import com.veylon.Game;
import com.veylon.ai.UrbanResidentAI;
import com.veylon.entity.Npc;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import com.veylon.world.urban.CityPlanner;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.SurvivorSites;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * The survivor camps and hidden refuges of layout twelve (urban milestone 16), run beside the
 * legacy settlements by {@link SettlementManager}.
 *
 * <ul>
 *   <li><b>Registration.</b> A planned site becomes a settlement record ({@link UrbanSite}) the
 *       first time the player comes within {@value #ACTIVATE} blocks of it, never by the legacy
 *       wilderness planner, at most {@value #MAX_SITES} in a world.</li>
 *   <li><b>Activation.</b> Measured in three dimensions from the site's planned anchor, with its
 *       column loaded: residents appear on their scheduled planned cells within
 *       {@value #ACTIVATE} blocks and go dormant beyond {@value #DEACTIVATE}, inside the shared
 *       resident budget. A dormant resident keeps health and needs, never position.</li>
 *   <li><b>Discovery.</b> Never from proximity alone: a camp is found when the player sees one of
 *       its people within {@value #DISCOVERY_SIGHT} blocks or stands inside it, a refuge when its
 *       loose panel is opened or the player stands in its cellar ({@code UrbanDiscovery}).</li>
 *   <li><b>Needs.</b> Every met site steps hour by hour in order of game minute, active or not
 *       ({@link UrbanSiteNeeds}).</li>
 * </ul>
 * All of it runs on the main thread inside the slow tick, with bounded work: nine regions' plans
 * to register from, the registered sites, and a step budget.
 */
public final class UrbanSiteDirector {
    public static final float ACTIVATE = SettlementManager.ACTIVATE_RADIUS, DEACTIVATE = SettlementManager.DEACTIVATE_RADIUS;
    public static final int MAX_SITES = 1_024;
    public static final float DISCOVERY_SIGHT = 24f;
    private static final int[][] REGIONS = {{0, 0}, {-1, 0}, {1, 0}, {0, -1}, {0, 1}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}};

    /** Set when {@code urban.sites} is read; a generator-15 save without it fails to load. */
    public boolean restored;
    /** Diagnostics of the last slow tick. */
    public int lastSteps, lastActivated;

    public static boolean manages(World w) {
        return w != null && w.urban != null && w.urban.hasSurvivors();
    }

    /** The survivor site a settled person belongs to, or null for anyone else. */
    public static UrbanSite siteOf(World w, Npc n) {
        if (w == null || n == null || !n.settled()) { return null; }
        return w.settlements.get(n.settlementId) instanceof UrbanSite s ? s : null;
    }

    /** A registered site by its building id, or null. */
    public static UrbanSite registered(World w, long id) {
        return w.settlements.get(id) instanceof UrbanSite s ? s : null;
    }

    public void reset() {
        restored = false;
        lastSteps = lastActivated = 0;
    }

    /** Every registered site, in id order. */
    public static List<UrbanSite> sites(World w) {
        List<UrbanSite> out = new ArrayList<>();
        for (Settlement s : w.settlements.values()) {
            if (s instanceof UrbanSite u) { out.add(u); }
        }
        out.sort(Comparator.comparingLong(s -> s.id));
        return out;
    }

    /** The record of a planned site, created on first need; null beyond the cap. */
    public UrbanSite register(Game g, SurvivorSites.Site plan) {
        UrbanSite existing = registered(g.world, plan.id());
        if (existing != null) { return existing; }
        int count = 0;
        for (Settlement s : g.world.settlements.values()) {
            if (s instanceof UrbanSite) { count++; }
        }
        if (count >= MAX_SITES) { return null; }
        UrbanSite site = new UrbanSite(plan);
        g.world.settlements.put(site.id, site);
        return site;
    }

    /** The site a building id names, registering a planned one; null when it names none. */
    public UrbanSite site(Game g, long id) {
        UrbanSite s = registered(g.world, id);
        if (s != null || !manages(g.world)) { return s; }
        SurvivorSites.Site plan = SurvivorSites.resolve(g.world.urban, id);
        return plan == null ? null : register(g, plan);
    }

    // ---- the slow tick -----------------------------------------------------------------------

    public void slowTick(Game g, float dt) {
        lastSteps = lastActivated = 0;
        if (!manages(g.world) || g.player == null || !g.world.urban.contentRevisionResolved()) { return; }
        registerNear(g);
        List<UrbanSite> all = sites(g.world);
        for (UrbanSite s : all) {
            syncLive(s);
            if (s.refuge() && !s.discovered && g.world.urban.discovery.discovered(s.plan.room())) {
                discover(g, s);
            } else if (s.camp() && !s.discovered && inside(g, s)) {
                discover(g, s);
            }
        }
        simulate(g, all);
        for (UrbanSite s : all) {
            SurvivorSites.Site home = s.home(g.world.urban);
            Vec3i anchor = home.anchor();
            boolean loaded = g.world.isColumnLoaded(anchor.x(), anchor.z());
            double d = Math.sqrt(g.player.distSqTo(anchor.x() + .5f, anchor.y(), anchor.z() + .5f));
            boolean live = anyLive(s);
            if (live && (d > DEACTIVATE || !loaded || !s.atHome())) {
                deactivate(g, s);
            } else if (d <= ACTIVATE && loaded && s.atHome() && !g.player.dead) {
                activate(g, s);
            }
            // People who agreed to move set out the moment nobody is watching.
            UrbanSiteRelocation.depart(g, this, s);
        }
    }

    /** Registers the planned sites of the player's region and its neighbours that lie within reach. */
    private void registerNear(Game g) {
        int rx = StreetNetwork.regionOf((int) Math.floor(g.player.pos.x));
        int rz = StreetNetwork.regionOf((int) Math.floor(g.player.pos.z));
        for (int[] d : REGIONS) {
            if (!CityPlanner.isPlannable(rx + d[0], rz + d[1])) { continue; }
            for (SurvivorSites.Site plan : g.world.urban.sites(rx + d[0], rz + d[1]).sites()) {
                Vec3i a = plan.anchor();
                if (g.player.distSqTo(a.x() + .5f, a.y(), a.z() + .5f) <= ACTIVATE * ACTIVATE
                        && g.world.isColumnLoaded(a.x(), a.z())) {
                    register(g, plan);
                }
            }
        }
    }

    /** Health of the living on screen is written back every tick, so a save or dormancy keeps it. */
    private static void syncLive(UrbanSite s) {
        for (Settlement.Resident r : s.residents) {
            Npc n = r.live;
            if (n != null && !n.dead && n.health > 0) { r.health = n.health; }
        }
    }

    private static boolean anyLive(UrbanSite s) {
        for (Settlement.Resident r : s.residents) {
            if (r.live != null && !r.live.dead) { return true; }
        }
        return false;
    }

    /** The player's feet stand inside a camp's held storeys. */
    private static boolean inside(Game g, UrbanSite s) {
        int x = (int) Math.floor(g.player.pos.x), y = (int) Math.floor(g.player.pos.y + .01f);
        int z = (int) Math.floor(g.player.pos.z);
        return s.plan.holds(x, y, z);
    }

    // ---- needs, in lockstep ----------------------------------------------------------------

    /**
     * Steps every met site whose stash is loaded, one hour at a time in order of game minute (ties
     * by id), so camps and the guests they feed always meet in the same order. One lagging site a
     * tick gets its stash chunk generated so it can catch up next time.
     */
    private void simulate(Game g, List<UrbanSite> all) {
        double now = g.time.totalMinutes;
        PriorityQueue<UrbanSite> queue = new PriorityQueue<>(Comparator.comparingDouble((UrbanSite s) -> s.simMinute)
                .thenComparingLong(s -> s.id));
        UrbanSite lagging = null;
        for (UrbanSite s : all) {
            if (s.simMinute < 0 || s.relocation == UrbanSite.Relocation.ARRIVED) { continue; }
            if (ready(g, s)) {
                queue.add(s);
            } else if (s.simMinute + UrbanSiteNeeds.STEP_MINUTES <= now
                    && (lagging == null || s.simMinute < lagging.simMinute)) {
                lagging = s;
            }
        }
        while (!queue.isEmpty() && lastSteps < UrbanSiteNeeds.MAX_STEPS_PER_TICK) {
            UrbanSite s = queue.poll();
            if (s.simMinute + UrbanSiteNeeds.STEP_MINUTES > now) { break; }
            UrbanSiteNeeds.step(g, s, guestsOf(all, s));
            UrbanSiteRelocation.arrive(g, s);
            lastSteps++;
            if (s.relocation != UrbanSite.Relocation.ARRIVED && ready(g, s)) { queue.add(s); }
        }
        if (lagging != null) {
            for (Vec3i p : UrbanSiteNeeds.stashOf(g, lagging)) {
                g.world.getOrCreateChunk(Math.floorDiv(p.x(), 16), Math.floorDiv(p.z(), 16));
            }
        }
    }

    /** A site may step: its stash is loaded, or its people are on the road with nothing to eat from. */
    private static boolean ready(Game g, UrbanSite s) {
        if (s.relocation == UrbanSite.Relocation.TRAVELLING) { return UrbanSiteRelocation.mayStep(g, s); }
        return UrbanSiteNeeds.stashLoaded(g, s);
    }

    private static List<UrbanSite> guestsOf(List<UrbanSite> all, UrbanSite camp) {
        if (!camp.camp()) { return List.of(); }
        List<UrbanSite> out = new ArrayList<>(1);
        for (UrbanSite s : all) {
            if (s.relocation == UrbanSite.Relocation.ARRIVED && s.relocatedTo == camp.id) { out.add(s); }
        }
        return out;
    }

    // ---- activation --------------------------------------------------------------------------

    /** Brings the missing living people of a site in, on their scheduled cells, inside the resident budget. */
    void activate(Game g, UrbanSite s) {
        for (Settlement.Resident base : s.residents) {
            UrbanSite.Resident r = (UrbanSite.Resident) base;
            if (!r.alive || r.live != null) { continue; }
            if (!g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.SETTLEMENT_RESIDENT)) { return; }
            Vec3i cell = spawnCell(g, s, r);
            if (cell == null) { continue; }
            Npc n = g.entities.spawnNpc(g.world, r.name, cell.x() + .5f, cell.y() + .02f, cell.z() + .5f);
            n.archetype = r.archetype;
            n.settlementId = s.id;
            n.residentIndex = r.slot;
            n.maxHealth = r.archetype.maxHealth;
            n.health = Math.min(r.health, n.maxHealth);
            n.hunger = r.hunger;
            n.isTrader = r.role == SurvivorSites.Role.TRADER;
            n.yaw = (r.slot * 97 + (int) (s.id & 0xff)) % 360;
            r.live = n;
            lastActivated++;
        }
    }

    /** The first clear cell among where the schedule wants them, their bed, their day cell and the safe cell. */
    private static Vec3i spawnCell(Game g, UrbanSite s, UrbanSite.Resident r) {
        SurvivorSites.Site home = s.home(g.world.urban);
        for (Vec3i c : List.of(UrbanResidentAI.scheduledCell(g, s, r), UrbanResidentAI.bedOf(g, s, r),
                UrbanResidentAI.dayOf(g, s, r), home.safe())) {
            if (c != null && clear(g, c)) { return c; }
        }
        return null;
    }

    private static boolean clear(Game g, Vec3i c) {
        if (!VoxelClearance.standable(g.world, c.x(), c.y(), c.z())
                || VoxelClearance.collides(g.world, c.x() + .5f, c.y() + .02f, c.z() + .5f, .55f, 1.75f, true)) {
            return false;
        }
        for (Npc other : g.entities.npcs) {
            if (!other.dead && other.distSqTo(c.x() + .5f, c.y(), c.z() + .5f) < .36f) { return false; }
        }
        return true;
    }

    /**
     * Writes the living back and takes them out of the world without a death. A body that fell
     * since the last entity tick is left to that tick, which reports its death with all of its
     * consequences; dormancy never swallows a killing.
     */
    void deactivate(Game g, UrbanSite s) {
        for (Settlement.Resident r : s.residents) {
            Npc n = r.live;
            if (n == null) { continue; }
            if (n.dead || n.health <= 0) {
                n.dead = true;
                continue;
            }
            r.health = n.health;
            r.live = null;
            g.entities.removeNpc(g, n);
        }
    }

    // ---- discovery ---------------------------------------------------------------------------

    /** The player has found a site: it is known from now on, and its people's needs start to count. */
    public void discover(Game g, UrbanSite s) {
        if (s.discovered) { return; }
        s.discovered = true;
        if (s.simMinute < 0) { s.simMinute = g.time.totalMinutes; }
        g.audio.playDiscover();
        int people = s.present();
        if (s.camp()) {
            g.log("Survivors hold this building: " + count(people) + (people == 1 ? " keeps" : " keep") + " a camp here.");
        } else if (s.relocation != UrbanSite.Relocation.HOME) {
            g.log("Their bedding is rolled up. Whoever hid here has moved on.");
        } else if (people > 0) {
            g.log(UrbanSiteRelations.capital(count(people)) + " huddle at the back of the cellar, watching you.");
        } else {
            g.log("No one answers. Whoever hid here is gone.");
        }
    }

    private static String count(int n) {
        return switch (n) {
            case 1 -> "one person";
            case 2 -> "two people";
            case 3 -> "three people";
            case 4 -> "four people";
            case 5 -> "five people";
            case 6 -> "six people";
            default -> n + " people";
        };
    }

    /**
     * A hidden cellar was just found (its panel slid aside, or the player stepped in): its site is
     * registered, its people brought in if the slow tick has not yet, and it is discovered.
     */
    public void onCellarFound(Game g, long room) {
        if (!manages(g.world) || !g.world.urban.contentRevisionResolved()) { return; }
        int rx = com.veylon.world.urban.UrbanIds.regionX(room), rz = com.veylon.world.urban.UrbanIds.regionZ(room);
        SurvivorSites.Site plan = g.world.urban.sites(rx, rz).refuge();
        // The discovery ledger is the record of what was found: a refuge is met only once it is there.
        if (plan == null || plan.room() != room || !g.world.urban.discovery.discovered(room)) { return; }
        UrbanSite s = register(g, plan);
        if (s == null) { return; }
        if (s.atHome() && s.relocation == UrbanSite.Relocation.HOME) { activate(g, s); }
        discover(g, s);
    }

    /** The spare camp bed an arrived guest sleeps in (see {@link UrbanSiteRelocation}). */
    public static Vec3i guestBed(Game g, UrbanSite guest, UrbanSite.Resident r) {
        return UrbanSiteRelocation.guestBed(g, guest, r);
    }

    /** A camp member the player can see from close by makes the camp known. */
    public void onResidentSeen(Game g, UrbanSite s) {
        if (s.camp() && !s.discovered) { discover(g, s); }
    }

    // ---- consequences the rest of the game reports -----------------------------------------

    /** A player's attack on a resident of a site. */
    public void onAttacked(Game g, UrbanSite s, Npc n) {
        UrbanSiteRelations.attacked(g, s, n);
    }

    /** A resident killed by the player. */
    public void onKilled(Game g, UrbanSite s, Npc n) {
        UrbanSiteRelations.killed(g, s, n);
    }

    /** A resident died of anything else. */
    public void onDied(UrbanSite s) {
        UrbanSiteRelations.died(s);
    }

    /**
     * Supplies taken from survivor storage. True when the storage is a site's, which then decides
     * the consequence; false for held storage no site lives in, which keeps milestone 07's rule.
     */
    public boolean onStorageTaken(Game g, long owner, int count) {
        UrbanSite s = site(g, owner);
        if (s == null) { return false; }
        if (abandoned(s)) { return true; }
        UrbanSiteRelations.taken(g, s, count);
        return true;
    }

    /** Supplies the player put into a site's stash: aid, once anything owed is repaid. */
    public void onStorageGiven(Game g, long owner, ItemType type, int count) {
        UrbanSite s = site(g, owner);
        if (s == null || abandoned(s) || count <= 0) { return; }
        boolean medicine = UrbanSiteDialogue.healing(type);
        boolean ration = UrbanSiteNeeds.ration(type) || type == ItemType.WATERSKIN_CLEAN;
        if (medicine || ration) {
            UrbanSiteRelations.aided(g, s, ration ? count : 0, medicine ? count : 0);
        }
    }

    /** Survivor storage the player wrecked. True when it was a site's. */
    public boolean onStorageWrecked(Game g, long owner) {
        UrbanSite s = site(g, owner);
        if (s == null) { return false; }
        if (!abandoned(s)) { UrbanSiteRelations.wrecked(g, s); }
        return true;
    }

    /** The storage screen's owner line for a site's storage, or null when no site holds it. */
    public String storageLabel(Game g, long owner) {
        UrbanSite s = registered(g.world, owner);
        if (s == null && manages(g.world) && SurvivorSites.resolve(g.world.urban, owner) != null) {
            s = site(g, owner);
        }
        if (s == null) { return null; }
        if (abandoned(s)) { return s.refuge() && s.stashReleased ? "Left to you by the people who hid here" : "Left behind"; }
        if (s.grantsAccess()) { return "Camp storage: shared with you as a friend"; }
        return s.camp() ? "Camp storage: taking supplies is theft" : "Their last supplies: taking them is theft";
    }

    /** Storage whose people are gone, or left to the player, is nobody's to defend. */
    private static boolean abandoned(UrbanSite s) {
        return s.present() == 0 || s.refuge() && s.stashReleased;
    }

    /** An infected the player put down within sight of a site's people. */
    public void onInfectedKilled(Game g, Npc infected) {
        if (!manages(g.world)) { return; }
        for (UrbanSite s : sites(g.world)) {
            if (!anyLive(s)) { continue; }
            Vec3i a = s.home(g.world.urban).anchor();
            if (infected.distSqTo(a.x() + .5f, a.y(), a.z() + .5f)
                    <= UrbanSiteRelations.DEFEND_RADIUS * UrbanSiteRelations.DEFEND_RADIUS) {
                UrbanSiteRelations.defended(g, s, infected);
            }
        }
    }
}
