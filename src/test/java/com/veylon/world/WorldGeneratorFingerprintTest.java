package com.veylon.world;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
 * change was not meant to alter output and is wrong, or it was. Under the
 * owner's save policy (0.9.1 O1) only the generators new worlds use, the
 * wilderness's 3 and the city's 20, may then change: re-record their pins, and
 * {@code SaveEpochGuardTest}, which digests the same fingerprints through
 * {@link WorldFingerprints}, decides whether the save epoch must rise. No new
 * generator version is added to keep an old save's world. Generators 4–19 stay
 * exactly as they are until 0.9.2 retires them, as it retired 1 and 2.
 *
 * <p>Each pin names its generator explicitly rather than "the current one":
 * the generator new worlds use and the generators a save may name are
 * different questions, and a pin that followed the default would silently
 * re-point at a new generator when the default moves.
 *
 * <p>Generator {@code GEN_CAVE_IDENTITIES} (3) was recorded 2026-07-31 at
 * {@code 3414dbf}, before the v0.5.0 generator optimizations.
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
                long blocks = WorldFingerprints.furnishedBuildings(SEEDS[i], generator);
                long plans = WorldFingerprints.buildingPlans(SEEDS[i], generator);
                if (blocks != URBAN_LATER_BUILDING_FINGERPRINTS[g][i] || plans != URBAN_LATER_PLAN_FINGERPRINTS[g][i]) {
                    moved.add(String.format("generator %d seed %d: blocks 0x%016xL, plans 0x%016xL",
                            generator, SEEDS[i], blocks, plans));
                }
            }
        }
        assertTrue(moved.isEmpty(), () -> "saved urban catalogues changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
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
            long blocks = WorldFingerprints.furnishedBuildings(SEEDS[i], World.GEN_URBAN_CIVIC);
            long plans = WorldFingerprints.buildingPlans(SEEDS[i], World.GEN_URBAN_CIVIC);
            if (blocks != URBAN_CIVIC_BUILDING_FINGERPRINTS[i] || plans != URBAN_CIVIC_PLAN_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: blocks 0x%016xL, plans 0x%016xL", SEEDS[i], blocks, plans));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 11 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
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
            long blocks = WorldFingerprints.furnishedBuildings(SEEDS[i], World.GEN_URBAN_DISTRICTS);
            long plans = WorldFingerprints.buildingPlans(SEEDS[i], World.GEN_URBAN_DISTRICTS);
            long districts = WorldFingerprints.districts(SEEDS[i], World.GEN_URBAN_DISTRICTS);
            if (blocks != URBAN_DISTRICT_BUILDING_FINGERPRINTS[i] || plans != URBAN_DISTRICT_PLAN_FINGERPRINTS[i]
                    || districts != URBAN_DISTRICT_SIX_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: blocks 0x%016xL, plans 0x%016xL, districts 0x%016xL",
                        SEEDS[i], blocks, plans, districts));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 12 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
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
            long terrain = WorldFingerprints.terrain(SEEDS[i], World.GEN_URBAN_UNDERGROUND);
            long plans = WorldFingerprints.buildingPlans(SEEDS[i], World.GEN_URBAN_UNDERGROUND);
            long routes = WorldFingerprints.routes(SEEDS[i], World.GEN_URBAN_UNDERGROUND);
            if (terrain != URBAN_UNDERGROUND_TERRAIN_FINGERPRINTS[i] || plans != URBAN_UNDERGROUND_PLAN_FINGERPRINTS[i]
                    || routes != URBAN_UNDERGROUND_ROUTE_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, plans 0x%016xL, routes 0x%016xL",
                        SEEDS[i], terrain, plans, routes));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 13 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
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
            long terrain = WorldFingerprints.terrain(SEEDS[i], World.GEN_URBAN_ECOLOGY);
            long plans = WorldFingerprints.ecologyPlans(SEEDS[i], World.GEN_URBAN_ECOLOGY);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || plans != URBAN_ECOLOGY_PLAN_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, plans 0x%016xL", SEEDS[i], terrain, plans));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 14 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
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
            long anchors = WorldFingerprints.ecologyAnchors(SEEDS[i], World.GEN_URBAN_ECOLOGY);
            if (anchors != URBAN_ECOLOGY_ANCHOR_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: anchors 0x%016xL", SEEDS[i], anchors));
            }
        }
        assertTrue(moved.isEmpty(), () -> "encounter catalogue 5 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
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
            long terrain = WorldFingerprints.terrain(SEEDS[i], World.GEN_URBAN_SURVIVORS);
            long sites = WorldFingerprints.survivorSites(SEEDS[i], World.GEN_URBAN_SURVIVORS);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || sites != URBAN_SURVIVOR_SITE_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, sites 0x%016xL", SEEDS[i], terrain, sites));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 15 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 16 (milestone 17), recorded 2026-10-22 at introduction. Its terrain is generator
     * 14's at every pinned chunk, because no compound lies within a region of the spawn; its own
     * pins are the compound plans (every claim and compound within three regions of the spawn,
     * with its people, gate, barricades, posts and stash) and the stamped chunks of the nearest
     * stronghold's and raider yard's gate lines.
     */
    private static final long[] URBAN_COMPOUND_FINGERPRINTS = {
            0x8bd36bfeca2d66b8L, 0x215cd4c07258f90aL, 0x35f0cd95a186be60L};
    private static final long[] URBAN_COMPOUND_CHUNK_FINGERPRINTS = {
            0xefccb539249bc24cL, 0x92dadb31446b4242L, 0xc444381a488ff68aL};

    @Test
    void theCompoundsKeepTheCityAndTheirRecordedPlans() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long terrain = WorldFingerprints.terrain(SEEDS[i], World.GEN_URBAN_STRONGHOLDS);
            long compounds = WorldFingerprints.compounds(SEEDS[i], World.GEN_URBAN_STRONGHOLDS);
            long chunks = WorldFingerprints.compoundChunks(SEEDS[i], World.GEN_URBAN_STRONGHOLDS);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || compounds != URBAN_COMPOUND_FINGERPRINTS[i]
                    || chunks != URBAN_COMPOUND_CHUNK_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, compounds 0x%016xL, chunks 0x%016xL",
                        SEEDS[i], terrain, compounds, chunks));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 16 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 17 (milestone 18), recorded 2026-10-23 at introduction. Its terrain is generator
     * 14's at every pinned chunk, because no wreck stands in them; its own pins are the vehicle
     * plans (every wreck within three regions of the spawn, with the survivor sites and compounds
     * planned beside them, which are layout thirteen's) and the stamped chunks of the wreck nearest
     * the spawn.
     */
    private static final long[] URBAN_VEHICLE_FINGERPRINTS = {
            0x9d00f321b44c234eL, 0xe04266f32dd7abdbL, 0xb83f5b8e2f88b2ecL};
    private static final long[] URBAN_VEHICLE_CHUNK_FINGERPRINTS = {
            0x239a0a97d395c20dL, 0x8e19509e968a91f3L, 0x61c8f5407890e29cL};

    @Test
    void theWrecksKeepTheCityAndTheirRecordedPlans() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long terrain = WorldFingerprints.terrain(SEEDS[i], World.GEN_URBAN_REFUGES);
            long vehicles = WorldFingerprints.vehicles(SEEDS[i], World.GEN_URBAN_REFUGES);
            long chunks = WorldFingerprints.vehicleChunks(SEEDS[i], World.GEN_URBAN_REFUGES);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || vehicles != URBAN_VEHICLE_FINGERPRINTS[i]
                    || chunks != URBAN_VEHICLE_CHUNK_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, vehicles 0x%016xL, chunks 0x%016xL",
                        SEEDS[i], terrain, vehicles, chunks));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 17 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 18 (milestone 19), recorded 2026-10-29 at introduction: refuge utilities are rules
     * over generator 17's city, so its terrain, wrecks, sites, compounds and the stamped chunks under
     * the nearest wreck are generator 17's pins exactly.
     */
    @Test
    void theUtilityCityIsTheRefugeCityExactly() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long terrain = WorldFingerprints.terrain(SEEDS[i], World.GEN_URBAN_UTILITIES);
            long vehicles = WorldFingerprints.vehicles(SEEDS[i], World.GEN_URBAN_UTILITIES);
            long chunks = WorldFingerprints.vehicleChunks(SEEDS[i], World.GEN_URBAN_UTILITIES);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || vehicles != URBAN_VEHICLE_FINGERPRINTS[i]
                    || chunks != URBAN_VEHICLE_CHUNK_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, vehicles 0x%016xL, chunks 0x%016xL",
                        SEEDS[i], terrain, vehicles, chunks));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 18 left generator 17's city; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 19 (milestone 20), recorded 2026-10-29 at introduction: urban missions are rules over
     * generator 18's city plus one relay mast on a precinct roof. Its terrain, wrecks, sites, compounds
     * and the stamped chunks under the nearest wreck are generator 17's pins exactly; its own pins are
     * the relay's plan (building, region, cabinet, stand and mast cells) and the stamped chunk holding
     * the cabinet.
     */
    private static final long[] URBAN_RELAY_FINGERPRINTS = {
            0xa69545d61a0ae1f7L, 0x900bb3142bc8a524L, 0x81ca4e455a8f8685L};
    private static final long[] URBAN_RELAY_CHUNK_FINGERPRINTS = {
            0x6c583644a421997fL, 0xf7d3584d05da8759L, 0xc9142dbd24c3d5c3L};

    @Test
    void theMissionCityIsTheUtilityCityWithOneRelayMast() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long terrain = WorldFingerprints.terrain(SEEDS[i], World.GEN_URBAN_MISSIONS);
            long vehicles = WorldFingerprints.vehicles(SEEDS[i], World.GEN_URBAN_MISSIONS);
            long chunks = WorldFingerprints.vehicleChunks(SEEDS[i], World.GEN_URBAN_MISSIONS);
            long site = WorldFingerprints.relay(SEEDS[i], World.GEN_URBAN_MISSIONS);
            long relayChunk = WorldFingerprints.relayChunk(SEEDS[i], World.GEN_URBAN_MISSIONS);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || vehicles != URBAN_VEHICLE_FINGERPRINTS[i]
                    || chunks != URBAN_VEHICLE_CHUNK_FINGERPRINTS[i] || site != URBAN_RELAY_FINGERPRINTS[i]
                    || relayChunk != URBAN_RELAY_CHUNK_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, vehicles 0x%016xL, chunks 0x%016xL, relay 0x%016xL,"
                        + " relay chunk 0x%016xL", SEEDS[i], terrain, vehicles, chunks, site, relayChunk));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 19 changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
    }

    /**
     * Generator 20 (milestone 23), checked at introduction: the survival economy is rules and content
     * over generator 19's city. Every pin generator 19 holds (terrain, wrecks, sites, compounds, the
     * chunks under the nearest wreck, the relay and its chunk) must hold for it unchanged; it has no pin
     * of its own because it stamps nothing new.
     */
    @Test
    void theEconomyCityIsTheMissionCityExactly() {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long terrain = WorldFingerprints.terrain(SEEDS[i], World.GEN_URBAN_ECONOMY);
            long vehicles = WorldFingerprints.vehicles(SEEDS[i], World.GEN_URBAN_ECONOMY);
            long chunks = WorldFingerprints.vehicleChunks(SEEDS[i], World.GEN_URBAN_ECONOMY);
            long site = WorldFingerprints.relay(SEEDS[i], World.GEN_URBAN_ECONOMY);
            long relayChunk = WorldFingerprints.relayChunk(SEEDS[i], World.GEN_URBAN_ECONOMY);
            if (terrain != URBAN_ECOLOGY_TERRAIN_FINGERPRINTS[i] || vehicles != URBAN_VEHICLE_FINGERPRINTS[i]
                    || chunks != URBAN_VEHICLE_CHUNK_FINGERPRINTS[i] || site != URBAN_RELAY_FINGERPRINTS[i]
                    || relayChunk != URBAN_RELAY_CHUNK_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: terrain 0x%016xL, vehicles 0x%016xL, chunks 0x%016xL, relay 0x%016xL,"
                        + " relay chunk 0x%016xL", SEEDS[i], terrain, vehicles, chunks, site, relayChunk));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 20 left generator 19's city; its economy must be rules and "
                + "content only:\n  " + String.join("\n  ", moved));
    }

    @Test
    void generatedTerrainMatchesTheRecordedFingerprint() {
        assertTerrainPinned(World.GEN_CAVE_IDENTITIES, TERRAIN_FINGERPRINTS);
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
                    WorldFingerprints.furnishedBuildings(SEEDS[i], World.GEN_URBAN_APARTMENTS),
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
            long actual = WorldFingerprints.streetPlans(SEEDS[i], World.GEN_URBAN_STREETS);
            if (actual != URBAN_STREETS_PLAN_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: recorded 0x%016xL, generated 0x%016xL",
                        SEEDS[i], URBAN_STREETS_PLAN_FINGERPRINTS[i], actual));
            }
        }
        assertTrue(moved.isEmpty(), () -> "generator 5 plans changed; it stays as it is until 0.9.2 "
                + "retires it:\n  " + String.join("\n  ", moved));
    }

    private static void assertTerrainPinned(int generatorVersion, long[] recorded) {
        List<String> moved = new ArrayList<>();
        for (int i = 0; i < SEEDS.length; i++) {
            long actual = WorldFingerprints.terrain(SEEDS[i], generatorVersion);
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
                refactor or optimization must be bit-identical. Only the generators
                new worlds use (3 and 20) may change on purpose: re-record their pins
                and let SaveEpochGuardTest decide whether the save epoch must rise.
                Every other generator stays as it is until 0.9.2 retires it.""".formatted(
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
            long actual = WorldFingerprints.columnQueries(SEEDS[i], World.GEN_CAVE_IDENTITIES);
            if (actual != COLUMN_QUERY_FINGERPRINTS[i]) {
                moved.add(String.format("seed %d: recorded 0x%016xL, generated 0x%016xL",
                        SEEDS[i], COLUMN_QUERY_FINGERPRINTS[i], actual));
            }
        }
        assertTrue(moved.isEmpty(),
                () -> "per-column terrain queries changed:\n  " + String.join("\n  ", moved));
    }

    @Test
    void theFingerprintWouldNoticeASingleChangedBlock() {
        // A fingerprint that ignored most of the chunk would pass forever.
        World world = new World(SEEDS[0], World.GEN_CAVE_IDENTITIES);
        Chunk chunk = world.getOrCreateChunk(0, 0);
        long before = WorldFingerprints.chunk(chunk);

        int y = chunk.height(4, 4);
        chunk.set(4, y, 4, chunk.get(4, y, 4) == BlockType.STONE
                ? BlockType.DIRT : BlockType.STONE);

        assertNotEquals(before, WorldFingerprints.chunk(chunk),
                "one changed block must move the fingerprint");
    }
}
