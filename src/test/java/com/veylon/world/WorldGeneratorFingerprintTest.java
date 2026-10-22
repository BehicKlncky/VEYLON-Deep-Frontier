package com.veylon.world;

import org.junit.jupiter.api.Test;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins what the generator actually produces, against recorded values.
 *
 * <h2>Why this is different from the determinism tests</h2>
 *
 * <p>{@code DeepCaveGenTest.sameSeedProducesIdenticalDeepChunks} builds two
 * worlds from one seed <em>with the same build</em> and compares them. That
 * catches order dependence and unseeded state, and it is worth having — but it
 * compares the code against itself. Reshape every world on the planet and it
 * stays green, because both sides move together.
 *
 * <p>Terrain is not an implementation detail here. It is the save format. Saves
 * store deltas and re-derive structure from the seed and the generator version
 * recorded in the save, so a change in generator output silently rewrites the
 * terrain under every existing save pinned to that version: the shelter
 * someone dug into a hillside opens onto a different hillside.
 *
 * <p>So these fingerprints are recorded constants. If a change moves one, that
 * is the test working. The response is a deliberate decision — either the
 * change was not meant to alter output and is wrong, or it was, and then a new
 * generator version must be added so old saves keep their old terrain, exactly
 * as {@code GEN_DEEP} and {@code GEN_CAVE_IDENTITIES} did. Re-recording the
 * constant on its own is never the right answer.
 *
 * <p>Each pin names its generator explicitly rather than "the current one":
 * the generator new worlds use and the generators a save may name are
 * different questions, and a pin that followed the default would silently
 * re-point at a new generator when the default moves.
 *
 * <p>Generator {@code GEN_CAVE_IDENTITIES} (3) was recorded 2026-07-31 at
 * {@code 3414dbf}, before the v0.5.0 generator optimizations. Generators
 * {@code GEN_LEGACY} (1) and {@code GEN_DEEP} (2) were recorded 2026-10-07 at
 * {@code 923345f}, before the urban generator split, from unchanged generator
 * code.
 */
class WorldGeneratorFingerprintTest {

    /**
     * Seeds chosen to cover contrasting ground: one arbitrary, one that places
     * chunks at negative coordinates (where floor-versus-truncate bugs live),
     * and one small seed. Each fingerprint below is over four chunks.
     */
    private static final long[] SEEDS = {20_260_731L, -4_242L, 7L};

    private static final long[] TERRAIN_FINGERPRINTS = {
            0xd9696cfbb47f6234L, 0x677f1e5e437f686eL, 0xee26b953c30e802dL};

    private static final long[] COLUMN_QUERY_FINGERPRINTS = {
            0xc7e914449117bdd9L, 0x82273c0c999fcdc6L, 0xde29e867023f323cL};

    /** Generator 1, which every v2 save and gen-1 v3 save re-derives. */
    private static final long[] LEGACY_TERRAIN_FINGERPRINTS = {
            0x575ff79c72c5e75cL, 0xaf2846b2fa8fb028L, 0xd53be542e8a9ac72L};

    /** Generator 2, the pre-identity 0.3.0 development generator. */
    private static final long[] DEEP_TERRAIN_FINGERPRINTS = {
            0x0ac52031ca6ac5bcL, 0x23bc80e5d60717e2L, 0xb40fceda52cad351L};

    /**
     * Generator 4, urban layout revision 1: the street datum placeholder,
     * recorded 2026-10-07 when it was introduced. Saved generator-4 worlds
     * re-derive it, so the city replaces it under a new generator version
     * rather than by changing this one ({@code UrbanRevision}). Its blocks are
     * the same everywhere; only the climate biome's soil moisture varies, which
     * is why two seeds share a value.
     */
    private static final long[] URBAN_TERRAIN_FINGERPRINTS = {
            0x2a0067f62e235b25L, 0x4e5a3a9063303cfeL, 0x2a0067f62e235b25L};

    /**
     * Generator 5, urban layout revision 2: the street network, districts and
     * reserved parcels, recorded 2026-10-08 when it was introduced. The four
     * chunks straddle the region corner at the origin, so both sides of two
     * avenue seams are pinned.
     */
    private static final long[] URBAN_STREETS_TERRAIN_FINGERPRINTS = {
            0x2a9ec92f97a969a9L, 0xe22c4dabfb8606c5L, 0xd58d2e3a6d99b888L};

    /** Generator 7, layout 4 / template 2, first recorded 2026-10-10. */
    private static final long[] URBAN_APARTMENT_TERRAIN_FINGERPRINTS = {
            0x6e45f241c7882998L, 0xfce647ce212623a4L, 0x345fb9e4d83a73a8L};

    /** Every chunk intersecting the generated refuge and first shop, including their interiors. */
    private static final long[] URBAN_FURNISHED_BUILDING_FINGERPRINTS = {
            0xa508a4ab6554fd94L, 0x1a6aa1a4c08df7f3L, 0xd3544779f9af6166L};

    /**
     * Generators 8 (shaped stairs), 9 (starter) and 10 (encounters), recorded
     * 2026-10-14 at {@code daadff0} from unchanged code, before the commercial
     * and civic catalogue. Until then they were pinned only relative to
     * generator 7 and to each other. The four terrain chunks are shared streets,
     * so the refuge/shop chunks and every building plan of three regions are
     * pinned too; generators 9 and 10 share geometry by design (D-83).
     */
    private static final long[] URBAN_LATER_TERRAIN_FINGERPRINTS = {
            0x637fb4a86840a518L, 0xc5034a89e7c9e4acL, 0x7349170213056b70L};
    private static final long[][] URBAN_LATER_BUILDING_FINGERPRINTS = {
            {0x76909c0605b884deL, 0xf456c87bab7640b3L, 0x70e438a82b115748L},
            {0x63c9196652129a3aL, 0x0c27b92837b0551cL, 0x77b16ecb683f8d06L},
            {0x63c9196652129a3aL, 0x0c27b92837b0551cL, 0x77b16ecb683f8d06L}};
    private static final long[][] URBAN_LATER_PLAN_FINGERPRINTS = {
            {0x371b1e07bdda477eL, 0x2659a42d27014f65L, 0xf37efa67ed546064L},
            {0xfd5c157e318b162bL, 0x2754649b31bb5fa6L, 0x648fee1d1dd33bddL},
            {0xfd5c157e318b162bL, 0x2754649b31bb5fa6L, 0x648fee1d1dd33bddL}};
    private static final int[] URBAN_LATER_GENERATORS = {
            World.GEN_URBAN_SHAPED_STAIRS, World.GEN_URBAN_STARTER, World.GEN_URBAN_ENCOUNTERS};

    @Test
    void laterUrbanCataloguesKeepTheirRecordedBlocksAndBuildingPlans() {
        List<String> moved = new ArrayList<>();
        for (int g = 0; g < URBAN_LATER_GENERATORS.length; g++) {
            int generator = URBAN_LATER_GENERATORS[g];
            assertTerrainPinned(generator, URBAN_LATER_TERRAIN_FINGERPRINTS);
            for (int i = 0; i < SEEDS.length; i++) {
                long blocks = furnishedBuildingFingerprint(SEEDS[i], generator);
                long plans = buildingPlanFingerprint(SEEDS[i], generator);
                if (blocks != URBAN_LATER_BUILDING_FINGERPRINTS[g][i] || plans != URBAN_LATER_PLAN_FINGERPRINTS[g][i]) {
                    moved.add(String.format("generator %d seed %d: blocks 0x%016xL, plans 0x%016xL",
                            generator, SEEDS[i], blocks, plans));
                }
            }
        }
        assertTrue(moved.isEmpty(), () -> "saved urban catalogues changed; add a generator version "
                + "instead of re-recording:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 11 (layout 8, template 5), recorded 2026-10-14 when the commercial and civic
     * catalogue was introduced. Its four terrain chunks reach specialist buildings in the
     * neighbouring regions, so they differ from generator 10's; the plan pin covers every
     * building of three regions, one of them downtown.
     */
    private static final long[] URBAN_CIVIC_TERRAIN_FINGERPRINTS = {
            0x1fc4b2fa9e641966L, 0x3cb0188f5688d77cL, 0x0da8ac69b04b6045L};
    private static final long[] URBAN_CIVIC_BUILDING_FINGERPRINTS = {
            0xfe99001b21b99d30L, 0x3788fac8651d269cL, 0xded50cb05c42ee09L};
    private static final long[] URBAN_CIVIC_PLAN_FINGERPRINTS = {
            0x3fb3b0d5f7ea59c7L, 0x89e28b3e511b2b96L, 0xd9a11c2e0b203863L};

    @Test
    void theCivicCatalogueKeepsItsRecordedBlocksAndBuildingPlans() {
        assertTerrainPinned(World.GEN_URBAN_CIVIC, URBAN_CIVIC_TERRAIN_FINGERPRINTS);
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long blocks = furnishedBuildingFingerprint(SEEDS[i], World.GEN_URBAN_CIVIC);
            long plans = buildingPlanFingerprint(SEEDS[i], World.GEN_URBAN_CIVIC);
            if (blocks != URBAN_CIVIC_BUILDING_FINGERPRINTS[i] || plans != URBAN_CIVIC_PLAN_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: blocks 0x%016xL, plans 0x%016xL", SEEDS[i], blocks, plans));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 11 changed; add a generator version instead of "
                + "re-recording:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 12 (layout 9, district catalogue 6), recorded 2026-10-19 when the six districts
     * were introduced. The shared pins cover the spawn and its first ring, which are residential
     * and commercial by construction, so the district pin adds, for the first region of every
     * district within three of the spawn, its surface and overlay rasters, the plans of its
     * first eight parcels and the blocks of its corner and centre chunks: cordons, canals,
     * yards, flooding and contamination.
     */
    private static final long[] URBAN_DISTRICT_TERRAIN_FINGERPRINTS = {
            0xb38f62efad5d33bdL, 0x1025d72a34e138d2L, 0xc338c0bee51dd3eeL};
    private static final long[] URBAN_DISTRICT_BUILDING_FINGERPRINTS = {
            0xf4e0db26ba2e403eL, 0x988e52440eab6918L, 0x94fc2491802b6884L};
    private static final long[] URBAN_DISTRICT_PLAN_FINGERPRINTS = {
            0x9e7f6a4f3e4aa194L, 0x402c2ccdc6a48f23L, 0xd3cbc3142d35c8c7L};
    private static final long[] URBAN_DISTRICT_SIX_FINGERPRINTS = {
            0xd6b55a8f44c94298L, 0xcf9913a7cf6145ccL, 0x6560cc9fa6e75e7aL};

    @Test
    void theSixDistrictsKeepTheirRecordedBlocksPlansAndOverlays() {
        assertTerrainPinned(World.GEN_URBAN_DISTRICTS, URBAN_DISTRICT_TERRAIN_FINGERPRINTS);
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long blocks = furnishedBuildingFingerprint(SEEDS[i], World.GEN_URBAN_DISTRICTS);
            long plans = buildingPlanFingerprint(SEEDS[i], World.GEN_URBAN_DISTRICTS);
            long districts = districtFingerprint(SEEDS[i]);
            if (blocks != URBAN_DISTRICT_BUILDING_FINGERPRINTS[i] || plans != URBAN_DISTRICT_PLAN_FINGERPRINTS[i]
                    || districts != URBAN_DISTRICT_SIX_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: blocks 0x%016xL, plans 0x%016xL, districts 0x%016xL",
                        SEEDS[i], blocks, plans, districts));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 12 changed; add a generator version instead of "
                + "re-recording:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 13 (milestone 14), recorded 2026-10-19 at introduction: terrain at the pinned
     * chunks (the spawn junction among them), every building plan of three regions under
     * template seven, and the routes: transit segments near the spawn with their stations,
     * three regions' route catalogues and the chunks of the spawn region's station. Seed 7's
     * terrain was re-recorded once the same day, when fire-escape ladder heads gained their rail
     * cage, before any save outside the tests used the generator; it is frozen from milestone
     * 14's handoff.
     */
    private static final long[] URBAN_UNDERGROUND_TERRAIN_FINGERPRINTS = {
            0xb9036588174436d2L, 0x80223cc179da0afbL, 0x2aba8a39e5c77757L};
    private static final long[] URBAN_UNDERGROUND_PLAN_FINGERPRINTS = {
            0xb80763b64ac57e1eL, 0x736ab4750d631341L, 0xfe117dbe676a8e66L};
    private static final long[] URBAN_UNDERGROUND_ROUTE_FINGERPRINTS = {
            0x1ef3497d881038faL, 0xe3ff041873124fd6L, 0x81faa688c0675baeL};

    @Test
    void theUndergroundAndRooftopRoutesKeepTheirRecordedBlocksAndPlans() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long terrain = terrainFingerprint(SEEDS[i], World.GEN_URBAN_UNDERGROUND);
            long plans = buildingPlanFingerprint(SEEDS[i], World.GEN_URBAN_UNDERGROUND);
            long routes = routeFingerprint(SEEDS[i]);
            if (terrain != URBAN_UNDERGROUND_TERRAIN_FINGERPRINTS[i] || plans != URBAN_UNDERGROUND_PLAN_FINGERPRINTS[i]
                    || routes != URBAN_UNDERGROUND_ROUTE_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, plans 0x%016xL, routes 0x%016xL",
                        SEEDS[i], terrain, plans, routes));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 13 changed; add a generator version instead of "
                + "re-recording:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 14 (milestone 15), recorded 2026-10-21 at introduction: terrain at the pinned
     * chunks, which show the same city as generator 13 there, and its ecology plans: every growth
     * site and wildlife slot within three regions of the spawn, and the stamped chunks of the
     * first growth site.
     */
    private static final long[] URBAN_ECOLOGY_TERRAIN_FINGERPRINTS = {
            0xb9036588174436d2L, 0x80223cc179da0afbL, 0x2aba8a39e5c77757L};
    private static final long[] URBAN_ECOLOGY_PLAN_FINGERPRINTS = {
            0xd597caa1a7b79d04L, 0x1c85c2c0ef3d9a1eL, 0xf8e5c6fe62b7addeL};

    @Test
    void theAlienEcologyKeepsItsRecordedBlocksAndPlans() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long terrain = terrainFingerprint(SEEDS[i], World.GEN_URBAN_ECOLOGY);
            long plans = ecologyPlanFingerprint(SEEDS[i]);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || plans != URBAN_ECOLOGY_PLAN_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, plans 0x%016xL", SEEDS[i], terrain, plans));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 14 changed; add a generator version instead of "
                + "re-recording:\n  " + String.join("\n  ", moved));
    }

    /** Growth sites and wildlife slots near the spawn, and the first site's stamped chunks. */
    private static long ecologyPlanFingerprint(long seed) {
        World world = new World(seed, World.GEN_URBAN_ECOLOGY);
        long hash = 0xcbf29ce484222325L;
        com.veylon.world.urban.EcologySites.GrowthSite first = null;
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
                hash = mix(hash, hashChunk(world.getOrCreateChunk(Math.floorDiv(first.node().x(), Chunk.SX) + dx,
                        Math.floorDiv(first.node().z(), Chunk.SZ) + dz)));
            }
        }
        return hash;
    }

    /**
     * Encounter catalogue five on generator 14, recorded 2026-10-21 at introduction: every
     * ecology anchor within three regions of the spawn with the roof flocks, and the species at
     * every building and station anchor of those regions.
     */
    private static final long[] URBAN_ECOLOGY_ANCHOR_FINGERPRINTS = {
            0x83ce9d04ac7463f3L, 0xd28f84c72a211349L, 0xb33fde24198e00a8L};

    @Test
    void encounterCatalogueFiveKeepsItsRecordedAnchors() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long anchors = ecologyAnchorFingerprint(SEEDS[i]);
            if (anchors != URBAN_ECOLOGY_ANCHOR_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: anchors 0x%016xL", SEEDS[i], anchors));
            }
        }
        assertTrue(moved.isEmpty(), () -> "encounter catalogue 5 changed; add a generator version instead of "
                + "re-recording:\n  " + String.join("\n  ", moved));
    }

    /** Ecology anchors with roof flocks, and the species of every building and station anchor, near the spawn. */
    private static long ecologyAnchorFingerprint(long seed) {
        World world = new World(seed, World.GEN_URBAN_ECOLOGY);
        long hash = 0xcbf29ce484222325L;
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                for (int sb = 0; sb < 16; sb++) {
                    for (var a : com.veylon.world.urban.EncounterAnchors.forEcology(world, rx, rz, sb)) {
                        hash = mix(hash, a.toString().hashCode());
                    }
                }
                for (ParcelPlan parcel : world.urban.regionPlan(rx, rz).parcels()) {
                    for (var a : com.veylon.world.urban.EncounterAnchors.forParcel(world, parcel)) {
                        hash = mix(hash, a.id());
                        hash = mix(hash, a.species().id.hashCode());
                    }
                }
                for (var a : com.veylon.world.urban.EncounterAnchors.forStation(world, rx, rz)) {
                    hash = mix(hash, a.id());
                    hash = mix(hash, a.species().id.hashCode());
                }
            }
        }
        return hash;
    }

    /**
     * Generator 15 (milestone 16), recorded 2026-10-22 at introduction. Its terrain is generator
     * 14's at every pinned chunk, because survivor sites stamp nothing; its own pin is the site
     * plans: every camp and hidden refuge within three regions of the spawn with its people,
     * their beds and day cells, the stash, the entry, the safe cell and the lookout post.
     */
    private static final long[] URBAN_SURVIVOR_SITE_FINGERPRINTS = {
            0x234e41006852e24eL, 0x50eebde7ad2b9eb0L, 0x8cc166a6d2d85597L};

    @Test
    void theSurvivorSitesKeepTheCityAndTheirRecordedPlans() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long terrain = terrainFingerprint(SEEDS[i], World.GEN_URBAN_SURVIVORS);
            long sites = sitePlanFingerprint(SEEDS[i]);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || sites != URBAN_SURVIVOR_SITE_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, sites 0x%016xL", SEEDS[i], terrain, sites));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 15 changed; add a generator version instead of "
                + "re-recording:\n  " + String.join("\n  ", moved));
    }

    /** Every survivor site within three regions of the spawn, as planned. */
    private static long sitePlanFingerprint(long seed) {
        World world = new World(seed, World.GEN_URBAN_SURVIVORS);
        long hash = 0xcbf29ce484222325L;
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                for (var site : world.urban.sites(rx, rz).sites()) {
                    hash = mix(hash, site.toString().hashCode());
                }
            }
        }
        return hash;
    }

    /** Transit segments around the spawn, three route catalogues and the spawn station's chunks. */
    private static long routeFingerprint(long seed) {
        World world = new World(seed, World.GEN_URBAN_UNDERGROUND);
        long hash = 0xcbf29ce484222325L;
        for (int line = -2; line <= 2; line += 2) {
            for (int index = -1; index <= 1; index++) {
                for (boolean ns : new boolean[]{true, false}) {
                    var s = world.urban.segment(ns, line, index);
                    hash = mix(hash, (s.present() ? 1 : 0) | (s.flooded() ? 2 : 0) | (s.plugLow() ? 4 : 0) | (s.plugHigh() ? 8 : 0));
                    for (int m : s.mounds()) { hash = mix(hash, m); }
                    if (s.present()) {
                        hash = mix(hash, s.station().id());
                        hash = mix(hash, s.station().start());
                        hash = mix(hash, com.veylon.world.urban.BuildingPlanDiagnostics.fingerprint(s.station().plan()));
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
            hash = mix(hash, hashChunk(world.getOrCreateChunk(Math.floorDiv(top.x(), Chunk.SX),
                    Math.floorDiv(top.z(), Chunk.SZ) + dz)));
        }
        return hash;
    }

    /** The first region of each district near the spawn: rasters, first plans, two chunks. */
    private static long districtFingerprint(long seed) {
        World world = new World(seed, World.GEN_URBAN_DISTRICTS);
        long hash = 0xcbf29ce484222325L;
        for (com.veylon.world.urban.UrbanDistrict district : com.veylon.world.urban.UrbanDistrict.values()) {
            DistrictPlan plan = null;
            for (int rz = -3; rz <= 3 && plan == null; rz++) {
                for (int rx = -3; rx <= 3 && plan == null; rx++) {
                    if ((rx != 0 || rz != 0) && com.veylon.world.urban.DistrictMap.districtOf(seed, rx, rz) == district) {
                        plan = world.urban.regionPlan(rx, rz);
                    }
                }
            }
            assertTrue(plan != null, district + " near the spawn");
            hash = mix(hash, plan.rx * 31L + plan.rz);
            for (int z = plan.originZ(); z < plan.originZ() + com.veylon.world.urban.StreetNetwork.REGION_SIZE; z++) {
                for (int x = plan.originX(); x < plan.originX() + com.veylon.world.urban.StreetNetwork.REGION_SIZE; x++) {
                    hash = mix(hash, plan.surfaceAt(x, z).code * 64L + plan.overlayAt(x, z).code);
                }
            }
            for (ParcelPlan parcel : plan.parcels().subList(0, Math.min(8, plan.parcels().size()))) {
                hash = mix(hash, com.veylon.world.urban.BuildingPlanDiagnostics.fingerprint(
                        world.urban.buildingAt(parcel.x0(), parcel.z0())));
            }
            hash = mix(hash, hashChunk(world.getOrCreateChunk(plan.rx * 16, plan.rz * 16)));
            hash = mix(hash, hashChunk(world.getOrCreateChunk(plan.rx * 16 + 8, plan.rz * 16 + 8)));
        }
        return hash;
    }

    /** Every building plan (geometry, rooms, fixtures, ids) of three regions, in parcel order. */
    private static long buildingPlanFingerprint(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = 0xcbf29ce484222325L;
        for (int[] region : new int[][]{{0, 0}, {-1, -1}, {1, -1}}) {
            for (ParcelPlan parcel : world.urban.regionPlan(region[0], region[1]).parcels()) {
                hash = mix(hash, com.veylon.world.urban.BuildingPlanDiagnostics.fingerprint(
                        world.urban.buildingAt(parcel.x0(), parcel.z0())));
            }
        }
        return hash;
    }

    /** Chunks fingerprinted per seed: origin, offset, and two negative ones. */
    private static final int[][] CHUNKS = {{0, 0}, {3, -2}, {-1, -1}, {-4, 5}};

    @Test
    void generatedTerrainMatchesTheRecordedFingerprint() {
        assertTerrainPinned(World.GEN_CAVE_IDENTITIES, TERRAIN_FINGERPRINTS);
    }

    @Test
    void olderGeneratorsMatchTheirRecordedFingerprints() {
        // Before these, generators 1 and 2 were pinned only relative to each
        // other or through one historical save, so a shared refactor could move
        // both and stay green.
        assertTerrainPinned(World.GEN_LEGACY, LEGACY_TERRAIN_FINGERPRINTS);
        assertTerrainPinned(World.GEN_DEEP, DEEP_TERRAIN_FINGERPRINTS);
    }

    @Test
    void theUrbanGeneratorMatchesItsRecordedFingerprint() {
        assertTerrainPinned(World.GEN_URBAN, URBAN_TERRAIN_FINGERPRINTS);
    }

    @Test
    void theUrbanStreetNetworkMatchesItsRecordedFingerprint() {
        assertTerrainPinned(World.GEN_URBAN_STREETS, URBAN_STREETS_TERRAIN_FINGERPRINTS);
    }

    @Test
    void planningRevisionStampsExactlyTheFrozenStreetNetworkWithoutBuildings() {
        assertTerrainPinned(World.GEN_URBAN_PLANS, URBAN_STREETS_TERRAIN_FINGERPRINTS);
    }

    @Test
    void generatedApartmentsMatchTheRecordedTerrainFingerprint() {
        assertTerrainPinned(World.GEN_URBAN_APARTMENTS, URBAN_APARTMENT_TERRAIN_FINGERPRINTS);
    }

    @Test
    void completeGeneratedRefugeAndShopMatchTheirRecordedBlocksAndHeightmaps() {
        for (int i = 0; i < SEEDS.length; i++) {
            assertEquals(URBAN_FURNISHED_BUILDING_FINGERPRINTS[i],
                    furnishedBuildingFingerprint(SEEDS[i], World.GEN_URBAN_APARTMENTS),
                    "generator 7 furnished building cells and heights are a saved-world contract; seed=" + SEEDS[i]);
        }
    }

    /**
     * Generator 5's plans, recorded 2026-10-08 with its terrain. Parcel ids,
     * bounds and reservations are part of what an urban save re-derives —
     * later sections will store parcel ids — so a change that kept every block
     * but moved a parcel boundary or renumbered a parcel must fail here too.
     */
    private static final long[] URBAN_STREETS_PLAN_FINGERPRINTS = {
            0xb24a28dfedc8f80fL, 0xd09181b02503c697L, 0x3eb5bd37769c97d9L};

    @Test
    void theUrbanStreetPlansMatchTheirRecordedFingerprint() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long actual = planFingerprint(SEEDS[i]);
            if (actual != URBAN_STREETS_PLAN_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: recorded 0x%016xL, generated 0x%016xL",
                        SEEDS[i], URBAN_STREETS_PLAN_FINGERPRINTS[i], actual));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 5 plans changed; add a generator version "
                + "instead of re-recording:\n  " + String.join("\n  ", moved));
    }

    private static long planFingerprint(long seed) {
        World world = new World(seed, World.GEN_URBAN_STREETS);
        long hash = 0xcbf29ce484222325L;
        for (int[] region : new int[][]{{0, 0}, {-1, -1}, {1, -1}}) {
            com.veylon.world.urban.DistrictPlan plan = world.urban.regionPlan(region[0], region[1]);
            hash = mix(hash, plan.district.code);
            hash = mix(hash, Long.hashCode(plan.districtId));
            for (com.veylon.world.urban.SuperblockPlan block : plan.superblocks()) {
                hash = mix(hash, block.kind().name().hashCode());
                hash = mixRect(hash, block.bounds());
            }
            for (com.veylon.world.urban.ParcelPlan parcel : plan.parcels()) {
                hash = mix(hash, Long.hashCode(parcel.id()));
                hash = mix(hash, parcel.use().id.hashCode());
                hash = mix(hash, parcel.x0() * 31 + parcel.z0());
                hash = mix(hash, parcel.x1() * 31 + parcel.z1());
                hash = mix(hash, parcel.frontage().name().hashCode());
                for (com.veylon.world.urban.ParcelPlan.Entrance door : parcel.entrances()) {
                    hash = mix(hash, door.x() * 31 + door.z());
                    hash = mix(hash, door.facing().name().hashCode());
                }
                hash = mixRect(hash, parcel.core());
                hash = mix(hash, parcel.maxStoreys());
            }
            for (com.veylon.world.urban.DistrictPlan.Tree tree : plan.trees()) {
                hash = mix(hash, tree.x() * 31 + tree.z());
                hash = mix(hash, tree.trunkHeight());
            }
        }
        int[] spawn = world.urban.spawnColumn();
        return mix(mix(hash, spawn[0]), spawn[1]);
    }

    private static void assertTerrainPinned(int generatorVersion, long[] recorded) {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long actual = terrainFingerprint(SEEDS[i], generatorVersion);
            if (actual != recorded[i]) {
                moved.add(String.format("seed %d: recorded 0x%016xL, generated 0x%016xL",
                        SEEDS[i], recorded[i], actual));
            }
        }
        assertTrue(moved.isEmpty(), () -> """
                Generator %d output changed for %d of %d seeds:
                  %s

                Terrain is part of the save contract: saves re-derive it from the
                seed plus the generator version they recorded, so this rewrites the
                world under every existing save pinned to generator %d.

                If the change was not meant to alter output, it is a bug -- a
                refactor or optimization must be bit-identical. If it was meant to,
                add a new generator version and leave the old one producing the old
                terrain, as GEN_DEEP and GEN_CAVE_IDENTITIES did. Updating these
                constants alone silently breaks saved worlds.""".formatted(
                generatorVersion, moved.size(), SEEDS.length, String.join("\n  ", moved),
                generatorVersion));
    }

    @Test
    void columnQueriesMatchTheRecordedFingerprint() {
        // heightAt, biomeAt and the cave-zone derivation are consulted far
        // outside chunk generation -- by settlement planning, spawn placement,
        // plant growth and temperature. They are fingerprinted separately so a
        // change there is attributed to the query rather than to the terrain.
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long actual = columnQueryFingerprint(SEEDS[i]);
            if (actual != COLUMN_QUERY_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: recorded 0x%016xL, generated 0x%016xL",
                        SEEDS[i], COLUMN_QUERY_FINGERPRINTS[i], actual));
            }
        }
        assertTrue(moved.isEmpty(),
                () -> "per-column terrain queries changed:\n  " + String.join("\n  ", moved));
    }

    @Test
    void legacyGeneratorsStillProduceTheirOwnTerrain() {
        // The whole point of the generator version is that an old save keeps
        // its world. Two versions of one seed must disagree, and each must
        // agree with itself.
        long legacy = terrainFingerprint(SEEDS[0], World.GEN_LEGACY);
        long identities = terrainFingerprint(SEEDS[0], World.GEN_CAVE_IDENTITIES);

        assertNotEquals(legacy, identities,
                "the legacy generator must not have collapsed onto generator 3");
        assertEquals(legacy, terrainFingerprint(SEEDS[0], World.GEN_LEGACY),
                "the legacy generator must still be deterministic");
    }

    @Test
    void theFingerprintWouldNoticeASingleChangedBlock() {
        // A fingerprint that ignored most of the chunk would pass forever.
        World world = new World(SEEDS[0], World.GEN_CAVE_IDENTITIES);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        long before = hashChunk(chunk);

        int y = chunk.height(4, 4);
        chunk.set(4, y, 4, chunk.get(4, y, 4) == BlockType.STONE
                ? BlockType.DIRT : BlockType.STONE);

        assertNotEquals(before, hashChunk(chunk),
                "one changed block must move the fingerprint");
    }

    // ------------------------------------------------------------------

    private static long terrainFingerprint(long seed, int generatorVersion) {
        World world = new World(seed, generatorVersion);
        long hash = 0xcbf29ce484222325L;
        for (int[] chunk : CHUNKS) {
            hash = mix(hash, hashChunk(world.getOrCreateChunk(chunk[0], chunk[1])));
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

    private static long furnishedBuildingFingerprint(long seed, int generatorVersion) {
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
        long hash = 0xcbf29ce484222325L;
        for (long chunk : chunks) {
            hash = mix(hash, hashChunk(world.getOrCreateChunk((int) (chunk >> 32), (int) chunk)));
        }
        assertTrue(world.pois.isEmpty());
        return hash;
    }

    /** Every block plus the derived heightmap of one chunk. */
    private static long hashChunk(Chunk chunk) {
        long hash = 0xcbf29ce484222325L;
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

    /** The pure per-column queries, sampled over a wide, sparse grid. */
    private static long columnQueryFingerprint(long seed) {
        World world = new World(seed, World.GEN_CAVE_IDENTITIES);
        WorldGenerator generator = world.generator;
        long hash = 0xcbf29ce484222325L;
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

    /** FNV-1a style mixing: order-sensitive and cheap. */
    /** Record hash codes are not specified across JDKs, so rectangles hash their corners. */
    private static long mixRect(long hash, com.veylon.world.urban.ParcelPlan.Rect rect) {
        return mix(mix(hash, rect.x0() * 31L + rect.z0()), rect.x1() * 31L + rect.z1());
    }

    private static long mix(long hash, long value) {
        return (hash ^ value) * 0x100000001b3L;
    }
}
