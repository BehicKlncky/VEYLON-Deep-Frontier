package com.veylon.world;

import com.veylon.world.urban.BuildingPlanDiagnostics;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.EcologySites;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.SuperblockPlan;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.VehicleSites;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Fingerprints of what a world generates from its seed and generator: terrain, the per-column
 * queries, and the city's plans, sites and stamped chunks. {@code WorldGeneratorFingerprintTest}
 * pins them per generator against recorded values; {@code SaveEpochGuardTest} digests them for the
 * two world kinds new worlds use. Each takes the generator it fingerprints, so one function serves
 * every generator that has the planned feature.
 *
 * <p>Mixing is FNV-1a style: order-sensitive and cheap. Record hash codes are not specified across
 * JDKs, so rectangles hash their corners; the plan records hashed through {@code toString} are the
 * game's own, whose text is part of what the pins record.
 */
public final class WorldFingerprints {

    /** Chunks fingerprinted per seed: origin, offset, and two negative ones. */
    private static final int[][] CHUNKS = {{0, 0}, {3, -2}, {-1, -1}, {-4, 5}};
    private static final long FNV = 0xcbf29ce484222325L;

    private WorldFingerprints() {
    }

    /** Four chunks and every POI the world placed. */
    public static long terrain(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (int[] chunk : CHUNKS) {
            hash = mix(hash, chunk(world.getOrCreateChunk(chunk[0], chunk[1])));
        }
        // POIs are placed by generation and persisted by position, so they are
        // part of what a save re-derives.
        List<String> pois = new ArrayList<>();
        for (Poi poi : world.pois) {
            pois.add(poi.type + "@" + poi.pos);
        }
        pois.sort(null);
        for (String poi : pois) {
            hash = mix(hash, poi.hashCode());
        }
        return hash;
    }

    /** The pure per-column queries, sampled over a wide, sparse grid. */
    public static long columnQueries(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        WorldGenerator generator = world.generator;
        long hash = FNV;
        for (int x = -600; x <= 600; x += 37) {
            for (int z = -600; z <= 600; z += 41) {
                int height = generator.heightAt(x, z);
                hash = mix(hash, height);
                hash = mix(hash, generator.biomeAt(x, z).ordinal());
                hash = mix(hash, Double.doubleToLongBits(generator.mountainFactor(x, z)));
                hash = mix(hash, Double.doubleToLongBits(generator.temperature01(x, z)));
                hash = mix(hash, Double.doubleToLongBits(generator.moisture01(x, z)));
                hash = mix(hash, generator.caveZoneAt(x, height - 20, z).ordinal());
            }
        }
        return hash;
    }

    /** Districts, superblocks, parcels, entrances and trees of three regions, then the spawn column. */
    public static long streetPlans(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (int[] region : new int[][]{{0, 0}, {-1, -1}, {1, -1}}) {
            DistrictPlan plan = world.urban.regionPlan(region[0], region[1]);
            hash = mix(hash, plan.district.code);
            hash = mix(hash, Long.hashCode(plan.districtId));
            for (SuperblockPlan block : plan.superblocks()) {
                hash = mix(hash, block.kind().name().hashCode());
                hash = mixRect(hash, block.bounds());
            }
            for (ParcelPlan parcel : plan.parcels()) {
                hash = mix(hash, Long.hashCode(parcel.id()));
                hash = mix(hash, parcel.use().id.hashCode());
                hash = mix(hash, parcel.x0() * 31 + parcel.z0());
                hash = mix(hash, parcel.x1() * 31 + parcel.z1());
                hash = mix(hash, parcel.frontage().name().hashCode());
                for (ParcelPlan.Entrance door : parcel.entrances()) {
                    hash = mix(hash, door.x() * 31 + door.z());
                    hash = mix(hash, door.facing().name().hashCode());
                }
                hash = mixRect(hash, parcel.core());
                hash = mix(hash, parcel.maxStoreys());
            }
            for (DistrictPlan.Tree tree : plan.trees()) {
                hash = mix(hash, tree.x() * 31 + tree.z());
                hash = mix(hash, tree.trunkHeight());
            }
        }
        int[] spawn = world.urban.spawnColumn();
        return mix(mix(hash, spawn[0]), spawn[1]);
    }

    /** Every building plan (geometry, rooms, fixtures, ids) of three regions, in parcel order. */
    public static long buildingPlans(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (int[] region : new int[][]{{0, 0}, {-1, -1}, {1, -1}}) {
            for (ParcelPlan parcel : world.urban.regionPlan(region[0], region[1]).parcels()) {
                hash = mix(hash, BuildingPlanDiagnostics.fingerprint(
                        world.urban.buildingAt(parcel.x0(), parcel.z0())));
            }
        }
        return hash;
    }

    /** Every chunk intersecting the origin region's refuge and first shop, interiors included. */
    public static long furnishedBuildings(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        DistrictPlan district = world.urban.regionPlan(0, 0);
        LinkedHashSet<Long> chunks = new LinkedHashSet<>();
        for (ParcelPlan.Use use : new ParcelPlan.Use[]{ParcelPlan.Use.REFUGE, ParcelPlan.Use.COMMERCIAL}) {
            ParcelPlan parcel = district.parcels().stream().filter(p -> p.use() == use).findFirst().orElseThrow();
            for (int cx = Math.floorDiv(parcel.x0(), Chunk.SX); cx <= Math.floorDiv(parcel.x1(), Chunk.SX); cx++) {
                for (int cz = Math.floorDiv(parcel.z0(), Chunk.SZ); cz <= Math.floorDiv(parcel.z1(), Chunk.SZ); cz++) {
                    chunks.add(((long) cx << 32) | (cz & 0xffffffffL));
                }
            }
        }
        assertTrue(chunks.size() >= 10, "the frozen refuge/shop sample covers both complete crossing buildings");
        long hash = FNV;
        for (long chunk : chunks) {
            hash = mix(hash, chunk(world.getOrCreateChunk((int) (chunk >> 32), (int) chunk)));
        }
        assertTrue(world.pois.isEmpty());
        return hash;
    }

    /** The first region of each district near the spawn: rasters, first plans, two chunks. */
    public static long districts(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (UrbanDistrict district : UrbanDistrict.values()) {
            DistrictPlan plan = null;
            for (int rz = -3; rz <= 3 && plan == null; rz++) {
                for (int rx = -3; rx <= 3 && plan == null; rx++) {
                    if ((rx != 0 || rz != 0) && DistrictMap.districtOf(seed, rx, rz) == district) {
                        plan = world.urban.regionPlan(rx, rz);
                    }
                }
            }
            assertTrue(plan != null, district + " near the spawn");
            hash = mix(hash, plan.rx * 31L + plan.rz);
            for (int z = plan.originZ(); z < plan.originZ() + StreetNetwork.REGION_SIZE; z++) {
                for (int x = plan.originX(); x < plan.originX() + StreetNetwork.REGION_SIZE; x++) {
                    hash = mix(hash, plan.surfaceAt(x, z).code * 64L + plan.overlayAt(x, z).code);
                }
            }
            for (ParcelPlan parcel : plan.parcels().subList(0, Math.min(8, plan.parcels().size()))) {
                hash = mix(hash, BuildingPlanDiagnostics.fingerprint(world.urban.buildingAt(parcel.x0(), parcel.z0())));
            }
            hash = mix(hash, chunk(world.getOrCreateChunk(plan.rx * 16, plan.rz * 16)));
            hash = mix(hash, chunk(world.getOrCreateChunk(plan.rx * 16 + 8, plan.rz * 16 + 8)));
        }
        return hash;
    }

    /** Transit segments around the spawn, three route catalogues and the spawn station's chunks. */
    public static long routes(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (int line = -2; line <= 2; line += 2) {
            for (int index = -1; index <= 1; index++) {
                for (boolean ns : new boolean[]{true, false}) {
                    var s = world.urban.segment(ns, line, index);
                    hash = mix(hash, (s.present() ? 1 : 0) | (s.flooded() ? 2 : 0) | (s.plugLow() ? 4 : 0) | (s.plugHigh() ? 8 : 0));
                    for (int m : s.mounds()) { hash = mix(hash, m); }
                    if (s.present()) {
                        hash = mix(hash, s.station().id());
                        hash = mix(hash, s.station().start());
                        hash = mix(hash, BuildingPlanDiagnostics.fingerprint(s.station().plan()));
                    }
                }
            }
        }
        for (int[] region : new int[][]{{0, 0}, {-1, -1}, {1, -1}}) {
            var routes = world.urban.routes(region[0], region[1]);
            hash = mix(hash, routes.corridors().toString().hashCode());
            hash = mix(hash, routes.fireEscapes().toString().hashCode());
            hash = mix(hash, routes.connectors().toString().hashCode());
            hash = mix(hash, routes.hiddenCellar());
        }
        var top = world.urban.segment(true, 0, 0).station().plan().approaches().getFirst();
        for (int dz = -2; dz <= 0; dz++) {
            hash = mix(hash, chunk(world.getOrCreateChunk(Math.floorDiv(top.x(), Chunk.SX),
                    Math.floorDiv(top.z(), Chunk.SZ) + dz)));
        }
        return hash;
    }

    /** Growth sites and wildlife slots near the spawn, and the first site's stamped chunks. */
    public static long ecologyPlans(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        EcologySites.GrowthSite first = null;
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                var eco = world.urban.ecology(rx, rz);
                for (var site : eco.growthSites()) {
                    hash = mix(hash, site.toString().hashCode());
                    if (first == null) { first = site; }
                }
                for (var slot : eco.slots()) {
                    hash = mix(hash, slot.toString().hashCode());
                }
            }
        }
        assertTrue(first != null, "a growth site near the spawn");
        for (int dz = -1; dz <= 1; dz++) {
            for (int dx = -1; dx <= 1; dx++) {
                hash = mix(hash, chunk(world.getOrCreateChunk(Math.floorDiv(first.node().x(), Chunk.SX) + dx,
                        Math.floorDiv(first.node().z(), Chunk.SZ) + dz)));
            }
        }
        return hash;
    }

    /** Ecology anchors with roof flocks, and the species of every building and station anchor, near the spawn. */
    public static long ecologyAnchors(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                for (int sb = 0; sb < 16; sb++) {
                    for (var a : EncounterAnchors.forEcology(world, rx, rz, sb)) {
                        hash = mix(hash, a.toString().hashCode());
                    }
                }
                for (ParcelPlan parcel : world.urban.regionPlan(rx, rz).parcels()) {
                    for (var a : EncounterAnchors.forParcel(world, parcel)) {
                        hash = mix(hash, a.id());
                        hash = mix(hash, a.species().id.hashCode());
                    }
                }
                for (var a : EncounterAnchors.forStation(world, rx, rz)) {
                    hash = mix(hash, a.id());
                    hash = mix(hash, a.species().id.hashCode());
                }
            }
        }
        return hash;
    }

    /** Every survivor site within three regions of the spawn, as planned. */
    public static long survivorSites(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                for (var site : world.urban.sites(rx, rz).sites()) {
                    hash = mix(hash, site.toString().hashCode());
                }
            }
        }
        return hash;
    }

    /** Every compound claim and plan within three regions of the spawn, with the camps and refuges beside them. */
    public static long compounds(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                var claims = world.urban.routes(rx, rz).claims();
                hash = mix(hash, String.valueOf(claims.stronghold()).hashCode());
                hash = mix(hash, String.valueOf(claims.raiders()).hashCode());
                var sites = world.urban.sites(rx, rz);
                hash = mix(hash, String.valueOf(sites.camp()).hashCode());
                hash = mix(hash, String.valueOf(sites.refuge()).hashCode());
                for (var c : sites.compounds()) { hash = mix(hash, c.toString().hashCode()); }
            }
        }
        return hash;
    }

    /** The stamped chunks of the nearest stronghold's and raider yard's gates. */
    public static long compoundChunks(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (var kind : new SurvivorSites.Kind[]{SurvivorSites.Kind.STRONGHOLD, SurvivorSites.Kind.RAIDERS}) {
            CompoundSites.Compound nearest = null;
            for (int rz = -3; rz <= 3; rz++) {
                for (int rx = -3; rx <= 3; rx++) {
                    for (var c : world.urban.sites(rx, rz).compounds()) {
                        if (c.kind() == kind && (nearest == null
                                || c.site().anchor().distSq(0, 41, 0) < nearest.site().anchor().distSq(0, 41, 0))) {
                            nearest = c;
                        }
                    }
                }
            }
            assertTrue(nearest != null, "a " + kind + " lies within three regions of the spawn");
            for (var line : nearest.barricades()) {
                var cell = line.cells().get(1);
                hash = mix(hash, chunk(world.getOrCreateChunk(Math.floorDiv(cell.x(), Chunk.SX),
                        Math.floorDiv(cell.z(), Chunk.SZ))));
            }
        }
        return hash;
    }

    /** Every wreck within three regions of the spawn, and the compounds and sites planned beside them. */
    public static long vehicles(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = FNV;
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                for (var w : world.urban.vehicles(rx, rz).wrecks()) { hash = mix(hash, w.toString().hashCode()); }
                var sites = world.urban.sites(rx, rz);
                for (var s : sites.sites()) { hash = mix(hash, s.toString().hashCode()); }
                for (var c : sites.compounds()) { hash = mix(hash, c.toString().hashCode()); }
            }
        }
        return hash;
    }

    /** The stamped chunks under the wreck nearest the spawn. */
    public static long vehicleChunks(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        VehicleSites.Wreck nearest = null;
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (var w : world.urban.vehicles(rx, rz).wrecks()) {
                    if (nearest == null || (long) w.x0() * w.x0() + (long) w.z0() * w.z0()
                            < (long) nearest.x0() * nearest.x0() + (long) nearest.z0() * nearest.z0()) {
                        nearest = w;
                    }
                }
            }
        }
        assertTrue(nearest != null, "a wreck lies within a region of the spawn");
        long hash = FNV;
        LinkedHashSet<Long> chunks = new LinkedHashSet<>();
        for (var cell : nearest.cells()) {
            chunks.add(((long) Math.floorDiv(cell.x(), Chunk.SX) << 32) | (Math.floorDiv(cell.z(), Chunk.SZ) & 0xffffffffL));
        }
        for (long chunk : chunks) {
            hash = mix(hash, chunk(world.getOrCreateChunk((int) (chunk >> 32), (int) chunk)));
        }
        return hash;
    }

    /** The relay mast's plan: building, region, cabinet, stand and mast cells. */
    public static long relay(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        var relay = world.urban.relay();
        assertTrue(relay != null, "seed " + seed + " has a relay");
        return mix(FNV, relay.toString().hashCode());
    }

    /** The stamped chunk that holds the relay's cabinet. */
    public static long relayChunk(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        var relay = world.urban.relay();
        assertTrue(relay != null, "seed " + seed + " has a relay");
        return chunk(world.getOrCreateChunk(Math.floorDiv(relay.relay().x(), Chunk.SX),
                Math.floorDiv(relay.relay().z(), Chunk.SZ)));
    }

    /** Every block plus the derived heightmap of one chunk. */
    public static long chunk(Chunk chunk) {
        long hash = FNV;
        for (int y = 0; y < Chunk.SY; y++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                for (int x = 0; x < Chunk.SX; x++) {
                    hash = mix(hash, chunk.get(x, y, z).ordinal());
                }
            }
        }
        for (int x = 0; x < Chunk.SX; x++) {
            for (int z = 0; z < Chunk.SZ; z++) {
                hash = mix(hash, chunk.height(x, z));
            }
        }
        return mix(hash, Float.floatToIntBits(chunk.moisture));
    }

    /** FNV-1a style mixing: order-sensitive and cheap. */
    public static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }

    private static long mixRect(long hash, ParcelPlan.Rect rect) {
        return mix(mix(hash, rect.x0() * 31L + rect.z0()), rect.x1() * 31L + rect.z1());
    }
}
