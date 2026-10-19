package com.veylon.entity;

import com.veylon.Game;
import com.veylon.ai.InfectedAI;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.SettlementManager;
import com.veylon.world.World;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.*;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Bounded main-thread activation. Dormancy pauses injured bodies; dead slots never return. */
public final class UrbanEncounterDirector {
    public static final int MAX_ACTIVE = 12, MAX_PER_ENCOUNTER = 2, MAX_RECORDS = 32_768;
    public static final int MAX_PARCELS_PER_TICK = 16, MAX_SPAWNS_PER_TICK = 2;
    public static final int MAX_SNAPSHOTS_PER_TICK = 16;
    public static final float ACTIVATION = 96, DEACTIVATION = 130, SANCTUARY = 24, BREATHING_ROOM = 16;
    private static final int[][] REGIONS = {{0,0},{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}};

    /** Health zero is a tombstone. A live pointer and the codec's index are never a second entity owner. */
    public static final class Resident {
        public final long id;
        public float health, x, y, z, yaw;
        public int torsoWounds;
        public Npc live;
        public Resident(long id, float health, float x, float y, float z, float yaw, int wounds) {
            this.id = id; this.health = health; this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.torsoWounds = wounds;
        }
        public boolean dead() { return health == 0; }
        void capture(Npc n) {
            health = Math.max(0, n.health); x = n.pos.x; y = n.pos.y; z = n.pos.z;
            yaw = n.yaw; torsoWounds = n.torsoWounds;
        }
    }

    private final Map<Long, Resident> residents = new LinkedHashMap<>();
    private final List<Resident> snapshots = new ArrayList<>();
    private int snapshotCursor;
    private int regionCursor, parcelCursor, scanX = Integer.MIN_VALUE, scanZ;
    public int lastParcels, lastSpawns, lastSnapshots;
    public boolean restored;

    public static boolean manages(World w) { return w != null && w.urban != null && w.urban.hasEncounters(); }
    public int size() { return residents.size(); }
    public Resident resident(long id) { return residents.get(id); }
    public Collection<Resident> residents() { return Collections.unmodifiableCollection(residents.values()); }
    public void reset() {
        residents.clear(); snapshots.clear(); snapshotCursor = 0;
        regionCursor = parcelCursor = 0; scanX = Integer.MIN_VALUE;
        lastParcels = lastSpawns = lastSnapshots = 0; restored = false;
    }
    /** Codec replacement, with no eviction of deaths when capacity is reached. */
    public void restore(Collection<Resident> records) {
        if (records.size() > MAX_RECORDS) { throw new IllegalArgumentException("too many encounter records"); }
        Map<Long, Resident> replacement = new LinkedHashMap<>();
        for (Resident r : records) {
            if (replacement.put(r.id, r) != null) { throw new IllegalArgumentException("duplicate encounter"); }
        }
        reset(); residents.putAll(replacement); snapshots.addAll(replacement.values()); restored = true;
    }

    public int activeCount(Game g) {
        int count = 0;
        for (Npc n : g.entities.npcs) { if (n.infected() && !n.dead) { count++; } }
        return count;
    }

    public void onDeath(Npc n) {
        Resident r = residents.get(n.encounterId);
        if (r != null) { r.capture(n); r.health = 0; r.live = null; }
    }

    public void sync(Game g) {
        for (Npc n : g.entities.npcs) {
            Resident r = residents.get(n.encounterId);
            if (n.infected() && r != null) { r.capture(n); }
        }
    }

    public void slowTick(Game g) {
        lastParcels = lastSpawns = lastSnapshots = 0;
        if (!manages(g.world)) { return; }
        sync(g);
        for (int i = g.entities.npcs.size() - 1; i >= 0; i--) {
            Npc n = g.entities.npcs.get(i);
            if (!n.infected() || n.dead) { continue; }
            if (n.distSqTo(g.player) > DEACTIVATION * DEACTIVATION
                    || !bodyLoaded(g.world, n.pos.x, n.pos.z)) {
                Resident r = residents.get(n.encounterId);
                if (r != null) { r.live = null; g.entities.removeNpc(g, n); }
            }
        }
        if (g.spawningPaused() || g.player.dead || !g.world.urban.contentRevisionResolved()) { return; }
        // A pursuer can leave its original parcel. Reactivate at its durable position too,
        // with bounded cursor work independent of the size of the visited-city ledger.
        int checks = Math.min(MAX_SNAPSHOTS_PER_TICK, snapshots.size());
        while (lastSnapshots < checks && lastSpawns < MAX_SPAWNS_PER_TICK && activeCount(g) < MAX_ACTIVE) {
            if (snapshotCursor >= snapshots.size()) { snapshotCursor = 0; }
            Resident r = snapshots.get(snapshotCursor++); lastSnapshots++;
            if (!r.dead() && r.live == null && g.player.distSqTo(r.x, r.y, r.z) <= ACTIVATION * ACTIVATION
                    && tryActivate(g, EncounterAnchors.resolve(g.world, r.id)) != null) { lastSpawns++; }
        }
        int rx = StreetNetwork.regionOf(cell(g.player.pos.x)), rz = StreetNetwork.regionOf(cell(g.player.pos.z));
        if (rx != scanX || rz != scanZ) { scanX = rx; scanZ = rz; regionCursor = parcelCursor = 0; }
        while (lastParcels < MAX_PARCELS_PER_TICK && lastSpawns < MAX_SPAWNS_PER_TICK
                && activeCount(g) < MAX_ACTIVE) {
            int ax = rx + REGIONS[regionCursor][0], az = rz + REGIONS[regionCursor][1];
            if (!CityPlanner.isPlannable(ax, az)) { advanceRegion(); lastParcels++; continue; }
            var parcels = g.world.urban.regionPlan(ax, az).parcels();
            boolean stations = EncounterAnchors.catalogue(g.world) == 4;
            if (parcelCursor > parcels.size() || !stations && parcelCursor == parcels.size()) {
                advanceRegion(); lastParcels++; continue;
            }
            if (parcelCursor == parcels.size()) {
                // After its parcels, a region's transit station (encounter catalogue four only, so the
                // scan of every earlier catalogue keeps its schedule).
                parcelCursor++; lastParcels++;
                for (var anchor : EncounterAnchors.forStation(g.world, ax, az)) {
                    var c = anchor.cell();
                    if (lastSpawns >= MAX_SPAWNS_PER_TICK) { break; }
                    if (g.player.distSqTo(c.x(), g.player.pos.y, c.z()) > 112 * 112
                            || !g.world.isColumnLoaded(c.x(), c.z())) { continue; }
                    if (tryActivate(g, anchor) != null) { lastSpawns++; }
                }
                continue;
            }
            var parcel = parcels.get(parcelCursor++); lastParcels++;
            float cx = (parcel.x0() + parcel.x1()) * 0.5f, cz = (parcel.z0() + parcel.z1()) * 0.5f;
            if (g.player.distSqTo(cx, g.player.pos.y, cz) > 112 * 112
                    || !g.world.isColumnLoaded(cell(cx), cell(cz))) { continue; }
            for (var anchor : EncounterAnchors.forParcel(g.world, parcel)) {
                if (lastSpawns >= MAX_SPAWNS_PER_TICK) { break; }
                if (tryActivate(g, anchor) != null) { lastSpawns++; }
            }
        }
    }

    private void advanceRegion() { parcelCursor = 0; regionCursor = (regionCursor + 1) % REGIONS.length; }

    /** Shared production placement gate, also used by deterministic generated-world QA. */
    public Npc tryActivate(Game g, EncounterAnchors.Anchor anchor) {
        if (!manages(g.world) || g.spawningPaused() || g.player.dead || activeCount(g) >= MAX_ACTIVE
                || !g.world.urban.contentRevisionResolved()
                || !g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.INFECTED)) { return null; }
        if (!EncounterAnchors.resolve(g.world, anchor.id()).equals(anchor)) {
            throw new IllegalArgumentException("encounter does not match planned anchor");
        }
        Resident r = residents.get(anchor.id());
        if (r != null && (r.dead() || r.live != null)) { return null; }
        if (r == null && residents.size() >= MAX_RECORDS) { return null; }
        float x = r == null ? anchor.cell().x() + 0.5f : r.x;
        float y = r == null ? anchor.cell().y() : r.y;
        float z = r == null ? anchor.cell().z() + 0.5f : r.z;
        if (g.player.distSqTo(x, y, z) > ACTIVATION * ACTIVATION || !safeSpawn(g, x, y, z)) { return null; }
        Npc n = g.entities.spawnNpc(g.world, "Veilbound", x, y, z);
        n.archetype = NpcArchetype.VEILBOUND; n.encounterId = anchor.id();
        n.maxHealth = n.archetype.maxHealth; n.campIndex = NpcAppearance.NEUTRAL_CAMP_INDEX;
        if (r == null) {
            r = new Resident(anchor.id(), n.maxHealth, x, y, z,
                    UrbanHash.range(0, 359, g.world.seed, 1, UrbanSalts.ENCOUNTERS, anchor.id(), 1, 0), 0);
            residents.put(r.id, r);
            snapshots.add(r);
        }
        n.health = r.health; n.yaw = r.yaw; n.torsoWounds = r.torsoWounds; r.live = n;
        return n;
    }

    public static boolean safeSpawn(Game g, float x, float y, float z) {
        if (!bodyLoaded(g.world, x, z) || y < 1 || y + 1.8f >= 96
                || !supported(g.world, x, y, z)
                || VoxelClearance.collides(g.world, x, y, z, .6f, 1.8f, true)
                || g.world.getBlock(cell(x), cell(y), cell(z)) == com.veylon.world.BlockType.WATER
                || g.player.distSqTo(x, y, z) < BREATHING_ROOM * BREATHING_ROOM) { return false; }
        var site = UrbanStarterSite.of(g.world.urban);
        var door = site.refuge().approaches().getLast();
        float dx = x - door.x() - .5f, dz = z - door.z() - .5f;
        if (dx * dx + dz * dz < SANCTUARY * SANCTUARY
                || site.refuge().parcel().contains(cell(x), cell(z))) { return false; }
        if (g.world.urban.refugeAnchor != null
                && g.world.urban.refugeAnchor.distSq(x, y, z) < SANCTUARY * SANCTUARY) { return false; }
        var location = g.world.urbanLocation(cell(x), cell(y), cell(z));
        if (location != null && location.building().variant() == BuildingPlan.Variant.OCCUPIED) { return false; }
        for (Npc n : g.entities.npcs) {
            if (!n.dead && n.distSqTo(x, y, z) < 4) { return false; }
        }
        float vx = x - g.player.pos.x, vz = z - g.player.pos.z;
        float forward = (float) Math.sin(Math.toRadians(g.camera.yaw)) * vx
                - (float) Math.cos(Math.toRadians(g.camera.yaw)) * vz;
        if (forward > 0 && (InfectedAI.clearSight(g.world, g.player.pos.x, g.player.pos.y + 1.4f,
                g.player.pos.z, x, y + .9f, z) || InfectedAI.clearSight(g.world,
                g.player.pos.x, g.player.pos.y + 1.4f, g.player.pos.z, x, y + 1.65f, z))) { return false; }
        return true;
    }

    /** Whole footprint, including corners across a chunk seam; unloaded AIR is never clearance. */
    public static boolean bodyLoaded(World w, float x, float z) {
        return w.isColumnLoaded(cell(x - .3f), cell(z - .3f))
                && w.isColumnLoaded(cell(x + .3f), cell(z + .3f))
                && w.isColumnLoaded(cell(x - .3f), cell(z + .3f))
                && w.isColumnLoaded(cell(x + .3f), cell(z - .3f));
    }
    private static boolean supported(World w, float x, float y, float z) {
        int floor = cell(y) - 1;
        return w.getBlock(cell(x - .3f), floor, cell(z - .3f)).solid
                && w.getBlock(cell(x + .3f), floor, cell(z + .3f)).solid
                && w.getBlock(cell(x - .3f), floor, cell(z + .3f)).solid
                && w.getBlock(cell(x + .3f), floor, cell(z - .3f)).solid;
    }
    private static int cell(float f) { return (int) Math.floor(f); }
}
