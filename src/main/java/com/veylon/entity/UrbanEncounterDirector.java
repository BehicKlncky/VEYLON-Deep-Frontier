package com.veylon.entity;

import com.veylon.Game;
import com.veylon.ai.InfectedAI;
import com.veylon.ai.VeilmawAI;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.SettlementManager;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.*;
import java.util.Collection;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bounded main-thread activation. Dormancy pauses injured bodies; dead slots never return.
 *
 * <p>Encounter catalogue five (urban milestone 15) adds creatures: the quarantine's Veilmaws and
 * the city's wildlife are residents too, with their own active caps ({@value #MAX_ACTIVE_ALIENS}
 * and {@value #MAX_ACTIVE_WILDLIFE}) beside the infected's {@value #MAX_ACTIVE}, admitted, made
 * dormant and remembered exactly as the infected are. A Veilmaw whose node is gone when it would
 * be admitted withers instead: its record becomes a tombstone and it never appears.
 */
public final class UrbanEncounterDirector {
    public static final int MAX_ACTIVE = 12, MAX_PER_ENCOUNTER = 2, MAX_RECORDS = 32_768;
    /** Catalogue five: Veilmaws and wildlife active at once, each against its own cap. */
    public static final int MAX_ACTIVE_ALIENS = 4, MAX_ACTIVE_WILDLIFE = 12;
    public static final int MAX_PARCELS_PER_TICK = 16, MAX_SPAWNS_PER_TICK = 2;
    public static final int MAX_SNAPSHOTS_PER_TICK = 16;
    public static final float ACTIVATION = 96, DEACTIVATION = 130, SANCTUARY = 24, BREATHING_ROOM = 16;
    private static final int[][] REGIONS = {{0,0},{-1,0},{1,0},{0,-1},{0,1},{-1,-1},{1,-1},{-1,1},{1,1}};
    private static final int SUPERBLOCKS = StreetNetwork.SUPERBLOCKS_PER_SIDE * StreetNetwork.SUPERBLOCKS_PER_SIDE;

    /**
     * Health zero is a tombstone. A live pointer and the codec's index are never a second entity
     * owner: a humanoid binds {@link #live}, a creature {@link #creature}, never both.
     */
    public static final class Resident {
        public final long id;
        public final UrbanSpecies species;
        public float health, x, y, z, yaw;
        public int torsoWounds;
        public Npc live;
        public Creature creature;
        public Resident(long id, float health, float x, float y, float z, float yaw, int wounds) {
            this(id, UrbanSpecies.VEILBOUND, health, x, y, z, yaw, wounds);
        }
        public Resident(long id, UrbanSpecies species, float health, float x, float y, float z, float yaw, int wounds) {
            this.id = id; this.species = species; this.health = health; this.x = x; this.y = y; this.z = z;
            this.yaw = yaw; this.torsoWounds = wounds;
        }
        public boolean dead() { return health == 0; }
        /** True while a live body plays this record. */
        public boolean active() { return live != null || creature != null; }
        void capture(Npc n) {
            health = Math.max(0, n.health); x = n.pos.x; y = n.pos.y; z = n.pos.z;
            yaw = n.yaw; torsoWounds = n.torsoWounds;
        }
        void capture(Creature c) {
            health = Math.max(0, c.health); x = c.pos.x; y = c.pos.y; z = c.pos.z;
            yaw = c.yaw; torsoWounds = 0;
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

    /** Active infected: Veilbound and Breachers. */
    public int activeCount(Game g) {
        int count = 0;
        for (Npc n : g.entities.npcs) { if (n.infected() && !n.dead) { count++; } }
        return count;
    }

    /** Active Veilmaws. */
    public int activeAliens(Game g) {
        int count = 0;
        for (Creature c : g.entities.creatures) { if (c.encounterId != 0 && !c.dead && c.type.territorial()) { count++; } }
        return count;
    }

    /** Active city wildlife. */
    public int activeWildlife(Game g) {
        int count = 0;
        for (Creature c : g.entities.creatures) { if (c.encounterId != 0 && !c.dead && !c.type.territorial()) { count++; } }
        return count;
    }

    public void onDeath(Npc n) {
        Resident r = residents.get(n.encounterId);
        if (r != null) { r.capture(n); r.health = 0; r.live = null; }
    }

    /** A managed creature's death is a permanent tombstone too. */
    public void onDeath(Creature c) {
        Resident r = residents.get(c.encounterId);
        if (r != null) { r.capture(c); r.health = 0; r.creature = null; }
    }

    public void sync(Game g) {
        for (Npc n : g.entities.npcs) {
            Resident r = residents.get(n.encounterId);
            if (n.infected() && r != null) { r.capture(n); }
        }
        for (Creature c : g.entities.creatures) {
            Resident r = c.encounterId == 0 ? null : residents.get(c.encounterId);
            if (r != null && r.creature == c) { r.capture(c); }
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
        for (int i = g.entities.creatures.size() - 1; i >= 0; i--) {
            Creature c = g.entities.creatures.get(i);
            if (c.encounterId == 0 || c.dead) { continue; }
            if (c.distSqTo(g.player) > DEACTIVATION * DEACTIVATION
                    || !bodyLoaded(g.world, c.pos.x, c.pos.z, c.width * 0.5f)) {
                Resident r = residents.get(c.encounterId);
                if (r != null) { r.capture(c); r.creature = null; }
                g.entities.creatures.remove(i);
                g.entities.depart(g, c);
            }
        }
        if (g.spawningPaused() || g.player.dead || !g.world.urban.contentRevisionResolved()) { return; }
        // A pursuer can leave its original parcel. Reactivate at its durable position too,
        // with bounded cursor work independent of the size of the visited-city ledger.
        int checks = Math.min(MAX_SNAPSHOTS_PER_TICK, snapshots.size());
        while (lastSnapshots < checks && lastSpawns < MAX_SPAWNS_PER_TICK && !capped(g)) {
            if (snapshotCursor >= snapshots.size()) { snapshotCursor = 0; }
            Resident r = snapshots.get(snapshotCursor++); lastSnapshots++;
            if (!r.dead() && !r.active() && g.player.distSqTo(r.x, r.y, r.z) <= ACTIVATION * ACTIVATION
                    && activate(g, EncounterAnchors.resolve(g.world, r.id)) != null) { lastSpawns++; }
        }
        int rx = StreetNetwork.regionOf(cell(g.player.pos.x)), rz = StreetNetwork.regionOf(cell(g.player.pos.z));
        if (rx != scanX || rz != scanZ) { scanX = rx; scanZ = rz; regionCursor = parcelCursor = 0; }
        boolean stations = EncounterAnchors.hasStations(g.world);
        boolean ecology = EncounterAnchors.catalogue(g.world) == 5;
        while (lastParcels < MAX_PARCELS_PER_TICK && lastSpawns < MAX_SPAWNS_PER_TICK && !capped(g)) {
            int ax = rx + REGIONS[regionCursor][0], az = rz + REGIONS[regionCursor][1];
            if (!CityPlanner.isPlannable(ax, az)) { advanceRegion(); lastParcels++; continue; }
            var parcels = g.world.urban.regionPlan(ax, az).parcels();
            // Parcels, then (catalogues four and five) the region's station, then (catalogue five)
            // its sixteen superblocks' living slots. Earlier catalogues keep their schedule.
            int ecologyStart = parcels.size() + (stations ? 1 : 0);
            int end = ecologyStart + (ecology ? SUPERBLOCKS : 0);
            if (parcelCursor >= end) { advanceRegion(); lastParcels++; continue; }
            if (stations && parcelCursor == parcels.size()) {
                // After its parcels, a region's transit station (encounter catalogue four only, so the
                // scan of every earlier catalogue keeps its schedule).
                parcelCursor++; lastParcels++;
                for (var anchor : EncounterAnchors.forStation(g.world, ax, az)) {
                    if (lastSpawns >= MAX_SPAWNS_PER_TICK) { break; }
                    if (tryNear(g, anchor)) { lastSpawns++; }
                }
                continue;
            }
            if (parcelCursor >= ecologyStart) {
                int superblock = parcelCursor - ecologyStart;
                parcelCursor++; lastParcels++;
                for (var anchor : EncounterAnchors.forEcology(g.world, ax, az, superblock)) {
                    if (lastSpawns >= MAX_SPAWNS_PER_TICK) { break; }
                    if (tryNear(g, anchor)) { lastSpawns++; }
                }
                continue;
            }
            var parcel = parcels.get(parcelCursor++); lastParcels++;
            float cx = (parcel.x0() + parcel.x1()) * 0.5f, cz = (parcel.z0() + parcel.z1()) * 0.5f;
            if (g.player.distSqTo(cx, g.player.pos.y, cz) > 112 * 112
                    || !g.world.isColumnLoaded(cell(cx), cell(cz))) { continue; }
            for (var anchor : EncounterAnchors.forParcel(g.world, parcel)) {
                if (lastSpawns >= MAX_SPAWNS_PER_TICK) { break; }
                if (activate(g, anchor) != null) { lastSpawns++; }
            }
        }
    }

    /** Within the scan radius and loaded, then admitted if it may be. */
    private boolean tryNear(Game g, EncounterAnchors.Anchor anchor) {
        var c = anchor.cell();
        return g.player.distSqTo(c.x(), g.player.pos.y, c.z()) <= 112 * 112 && g.world.isColumnLoaded(c.x(), c.z())
                && activate(g, anchor) != null;
    }

    /**
     * Whether no more bodies may be admitted this tick. Catalogues one to four stop at the
     * infected cap, as they always did; catalogue five stops only when every kind is at its cap.
     */
    private boolean capped(Game g) {
        if (EncounterAnchors.catalogue(g.world) != 5) { return activeCount(g) >= MAX_ACTIVE; }
        return activeCount(g) >= MAX_ACTIVE && activeAliens(g) >= MAX_ACTIVE_ALIENS
                && activeWildlife(g) >= MAX_ACTIVE_WILDLIFE;
    }

    private void advanceRegion() { parcelCursor = 0; regionCursor = (regionCursor + 1) % REGIONS.length; }

    /** Admits a planned body of any species; null when it may not appear now. */
    public Entity activate(Game g, EncounterAnchors.Anchor anchor) {
        return anchor.species().body == UrbanSpecies.Body.CREATURE ? tryActivateCreature(g, anchor) : tryActivate(g, anchor);
    }

    /** Shared production placement gate, also used by deterministic generated-world QA. */
    public Npc tryActivate(Game g, EncounterAnchors.Anchor anchor) {
        if (anchor.species().body != UrbanSpecies.Body.HUMANOID) { return null; }
        if (!manages(g.world) || g.spawningPaused() || g.player.dead || activeCount(g) >= MAX_ACTIVE
                || !g.world.urban.contentRevisionResolved()
                || !g.settlementManager.canSpawnNpc(g, SettlementManager.NpcCategory.INFECTED)) { return null; }
        if (!EncounterAnchors.resolve(g.world, anchor.id()).equals(anchor)) {
            throw new IllegalArgumentException("encounter does not match planned anchor");
        }
        Resident r = residents.get(anchor.id());
        if (r != null && (r.dead() || r.active())) { return null; }
        if (r == null && residents.size() >= MAX_RECORDS) { return null; }
        float x = r == null ? anchor.cell().x() + 0.5f : r.x;
        float y = r == null ? anchor.cell().y() : r.y;
        float z = r == null ? anchor.cell().z() + 0.5f : r.z;
        if (g.player.distSqTo(x, y, z) > ACTIVATION * ACTIVATION || !safeSpawn(g, x, y, z)) { return null; }
        NpcArchetype archetype = archetypeOf(anchor.species());
        Npc n = g.entities.spawnNpc(g.world, archetype.displayName, x, y, z);
        n.archetype = archetype; n.encounterId = anchor.id();
        n.maxHealth = n.archetype.maxHealth; n.campIndex = NpcAppearance.NEUTRAL_CAMP_INDEX;
        if (r == null) {
            r = new Resident(anchor.id(), anchor.species(), n.maxHealth, x, y, z,
                    UrbanHash.range(0, 359, g.world.seed, 1, UrbanSalts.ENCOUNTERS, anchor.id(), 1, 0), 0);
            residents.put(r.id, r);
            snapshots.add(r);
        }
        n.health = r.health; n.yaw = r.yaw; n.torsoWounds = r.torsoWounds; r.live = n;
        return n;
    }

    /**
     * Catalogue five's creature gate: the Veilmaw and city wildlife, each against its own active
     * cap. Ground species need loaded support and clearance, flying ones clear air; both keep the
     * player's breathing room and appear out of view. A Veilmaw whose node is gone has withered
     * with it: its record becomes a tombstone, and it never appears (explicit migration).
     */
    public Creature tryActivateCreature(Game g, EncounterAnchors.Anchor anchor) {
        UrbanSpecies species = anchor.species();
        if (species.body != UrbanSpecies.Body.CREATURE || !manages(g.world) || g.spawningPaused() || g.player.dead
                || !g.world.urban.contentRevisionResolved()
                || (species == UrbanSpecies.VEILMAW ? activeAliens(g) >= MAX_ACTIVE_ALIENS
                : activeWildlife(g) >= MAX_ACTIVE_WILDLIFE)) { return null; }
        if (!EncounterAnchors.resolve(g.world, anchor.id()).equals(anchor)) {
            throw new IllegalArgumentException("encounter does not match planned anchor");
        }
        Resident r = residents.get(anchor.id());
        if (r != null && (r.dead() || r.active())) { return null; }
        if (r == null && residents.size() >= MAX_RECORDS) { return null; }
        Creature.CreatureType type = creatureOf(species);
        Habitat home = anchor.habitat();
        if (species == UrbanSpecies.VEILMAW && home != null && home.node() != null) {
            Vec3i node = home.node();
            if (!g.world.isColumnLoaded(node.x(), node.z())) { return null; }
            if (VeilmawAI.nodeGone(g.world, node)) {
                if (r == null) {
                    r = new Resident(anchor.id(), species, 0, anchor.cell().x() + 0.5f, anchor.cell().y(),
                            anchor.cell().z() + 0.5f, 0, 0);
                    residents.put(r.id, r);
                    snapshots.add(r);
                } else {
                    r.health = 0;
                }
                return null;
            }
        }
        float x = r == null ? anchor.cell().x() + 0.5f : r.x;
        float y = r == null ? anchor.cell().y() : r.y;
        float z = r == null ? anchor.cell().z() + 0.5f : r.z;
        if (g.player.distSqTo(x, y, z) > ACTIVATION * ACTIVATION || !safeSpawn(g, type, species, x, y, z)) {
            return null;
        }
        Creature c = g.entities.spawnCreature(g.world, type, x, y, z);
        c.encounterId = anchor.id();
        c.home = home;
        if (r == null) {
            r = new Resident(anchor.id(), species, type.maxHealth, x, y, z,
                    UrbanHash.range(0, 359, g.world.seed, 1, UrbanSalts.ENCOUNTERS, anchor.id(), 1, 0), 0);
            residents.put(r.id, r);
            snapshots.add(r);
        }
        c.health = r.health; c.yaw = r.yaw; c.lastHealth = c.health; c.onGround = !type.flying;
        r.creature = c;
        return c;
    }

    /** The infected archetype of a humanoid species. */
    public static NpcArchetype archetypeOf(UrbanSpecies species) {
        return switch (species) {
            case VEILBOUND -> NpcArchetype.VEILBOUND;
            case BREACHER -> NpcArchetype.BREACHER;
            default -> throw new IllegalArgumentException(species + " is not a humanoid species");
        };
    }

    /** The creature type of a creature species. */
    public static Creature.CreatureType creatureOf(UrbanSpecies species) {
        return switch (species) {
            case VEILMAW -> Creature.CreatureType.VEILMAW;
            case SKITTERWING -> Creature.CreatureType.BIRD;
            case MURKHARE -> Creature.CreatureType.HARE;
            case GLOWDEER -> Creature.CreatureType.DEER;
            default -> throw new IllegalArgumentException(species + " is not a creature species");
        };
    }

    /** The most health a species' body can have. */
    public static float maxHealth(UrbanSpecies species) {
        return species.body == UrbanSpecies.Body.HUMANOID ? archetypeOf(species).maxHealth : creatureOf(species).maxHealth;
    }

    public static boolean safeSpawn(Game g, float x, float y, float z) {
        if (!bodyLoaded(g.world, x, z) || y < 1 || y + 1.8f >= 96
                || !supported(g.world, x, y, z, .3f)
                || VoxelClearance.collides(g.world, x, y, z, .6f, 1.8f, true)
                || g.world.getBlock(cell(x), cell(y), cell(z)) == BlockType.WATER
                || g.player.distSqTo(x, y, z) < BREATHING_ROOM * BREATHING_ROOM) { return false; }
        if (inSanctuary(g, x, y, z)) { return false; }
        var location = g.world.urbanLocation(cell(x), cell(y), cell(z));
        if (location != null && location.building().variant() == BuildingPlan.Variant.OCCUPIED) { return false; }
        for (Npc n : g.entities.npcs) {
            if (!n.dead && n.distSqTo(x, y, z) < 4) { return false; }
        }
        return outOfView(g, x, y, z, .9f, 1.65f);
    }

    /** The creature counterpart: the body's own size, support for ground species, sanctuary for hostiles. */
    static boolean safeSpawn(Game g, Creature.CreatureType type, UrbanSpecies species, float x, float y, float z) {
        float half = type.width * 0.5f;
        if (!bodyLoaded(g.world, x, z, half) || y < 1 || y + type.height >= 96
                || !type.flying && !supported(g.world, x, y, z, half)
                || VoxelClearance.collides(g.world, x, y, z, type.width, type.height, true)
                || !type.flying && g.world.getBlock(cell(x), cell(y), cell(z)) == BlockType.WATER
                || g.player.distSqTo(x, y, z) < BREATHING_ROOM * BREATHING_ROOM) { return false; }
        if (species.hostile && inSanctuary(g, x, y, z)) { return false; }
        for (Npc n : g.entities.npcs) {
            if (!n.dead && n.distSqTo(x, y, z) < 4) { return false; }
        }
        for (Creature c : g.entities.creatures) {
            if (!c.dead && c.distSqTo(x, y, z) < 1) { return false; }
        }
        return outOfView(g, x, y, z, type.height * .5f, type.height * .92f);
    }

    private static boolean inSanctuary(Game g, float x, float y, float z) {
        var site = UrbanStarterSite.of(g.world.urban);
        var door = site.refuge().approaches().getLast();
        float dx = x - door.x() - .5f, dz = z - door.z() - .5f;
        if (dx * dx + dz * dz < SANCTUARY * SANCTUARY
                || site.refuge().parcel().contains(cell(x), cell(z))) { return true; }
        return g.world.urban.refugeAnchor != null
                && g.world.urban.refugeAnchor.distSq(x, y, z) < SANCTUARY * SANCTUARY;
    }

    /** Nothing appears in the camera's forward half-space within clear sight of the eye. */
    private static boolean outOfView(Game g, float x, float y, float z, float middle, float top) {
        float vx = x - g.player.pos.x, vz = z - g.player.pos.z;
        float forward = (float) Math.sin(Math.toRadians(g.camera.yaw)) * vx
                - (float) Math.cos(Math.toRadians(g.camera.yaw)) * vz;
        return !(forward > 0 && (InfectedAI.clearSight(g.world, g.player.pos.x, g.player.pos.y + 1.4f,
                g.player.pos.z, x, y + middle, z) || InfectedAI.clearSight(g.world,
                g.player.pos.x, g.player.pos.y + 1.4f, g.player.pos.z, x, y + top, z)));
    }

    /** Whole footprint, including corners across a chunk seam; unloaded AIR is never clearance. */
    public static boolean bodyLoaded(World w, float x, float z) {
        return bodyLoaded(w, x, z, .3f);
    }

    /** As {@link #bodyLoaded(World, float, float)} for a body of half width {@code half}. */
    public static boolean bodyLoaded(World w, float x, float z, float half) {
        return w.isColumnLoaded(cell(x - half), cell(z - half))
                && w.isColumnLoaded(cell(x + half), cell(z + half))
                && w.isColumnLoaded(cell(x - half), cell(z + half))
                && w.isColumnLoaded(cell(x + half), cell(z - half));
    }
    private static boolean supported(World w, float x, float y, float z, float half) {
        int floor = cell(y) - 1;
        return w.getBlock(cell(x - half), floor, cell(z - half)).solid
                && w.getBlock(cell(x + half), floor, cell(z + half)).solid
                && w.getBlock(cell(x - half), floor, cell(z + half)).solid
                && w.getBlock(cell(x + half), floor, cell(z - half)).solid;
    }
    private static int cell(float f) { return (int) Math.floor(f); }
}
