package com.veylon.world.urban;

import com.veylon.world.Chunk;

/**
 * The urban state of one world: its layout and content revisions and, for the
 * street-network layout, its bounded cache of district plans and the plan
 * queries built on it.
 *
 * <p>{@code World} owns exactly one of these when its generator is urban and
 * none otherwise, so a legacy world never carries urban state, and a new world
 * or a load — each a new {@code World} — can never inherit the previous
 * world's.
 *
 * <p>The layout revision follows from the generator version at construction.
 * The content revision is resolved separately: bootstrap records the current
 * one for a fresh world before any chunk generates, while a load leaves it
 * unresolved until the {@code urban.meta} section (or its absent default) has
 * been read. Reading it before then throws, so content can never be rolled
 * against a revision a save is about to replace.
 *
 * <h2>Plans and queries</h2>
 *
 * <p>District plans are pure, so the cache holds at most
 * {@value #MAX_REGION_PLANS} of them and rebuilds an evicted one identically;
 * nothing persistent lives there. The queries answer from the plan, never from
 * the column top, because {@code World.surfaceHeight} reports whatever is
 * highest — a tree crown now, a roof once buildings exist:
 * <ul>
 *   <li>{@link #groundY}: the walkable ground slab of a column (the street
 *       datum everywhere in layouts 1 and 2);</li>
 *   <li>{@link UrbanDatum#slabY} and {@link UrbanDatum#floorContaining}: the
 *       floors of the datum;</li>
 *   <li>{@link #plannedRoofY}: the storey envelope over a parcel — a
 *       reservation, not a built roof — and the ground elsewhere;</li>
 *   <li>{@link #surfaceAt}, {@link #parcelAt} and {@link #districtAt}: plan
 *       metadata for a column.</li>
 * </ul>
 * Queries never generate chunks and never touch world blocks.
 */
public final class UrbanWorldState {

    /** Region plans kept at once (DESIGN §10, package 03). */
    public static final int MAX_REGION_PLANS = 16;
    /** Pure building plans only; no fixture or container state lives in this cache. */
    public static final int MAX_BUILDING_PLANS = 256;
    /** Transit segments of layout ten kept at once; a region reads two, a junction chunk four. */
    public static final int MAX_TRANSIT_SEGMENTS = 64;
    /**
     * Route catalogues (one per cached region, at most {@value #MAX_REGION_PLANS}) and transit
     * segments kept at once, the runtime snapshot's {@code routePlans} cap.
     */
    public static final int MAX_ROUTE_PLANS = MAX_REGION_PLANS + MAX_TRANSIT_SEGMENTS;

    /** Spawn column of the street-datum placeholder (layout revision 1). */
    static final int PLACEHOLDER_SPAWN_X = 8;
    static final int PLACEHOLDER_SPAWN_Z = 8;

    private static final int UNRESOLVED = 0;

    private final long seed;
    private final int layoutRevision;
    private final BoundedPlanCache<Long, DistrictPlan> regionPlans;
    private final int buildingTemplateRevision;
    private final BoundedPlanCache<Long, BuildingPlan> buildingPlans;
    /** Layout ten: each region's route catalogue, beside its plan. */
    private final BoundedPlanCache<Long, RouteCatalogue> routeCatalogues;
    private final BoundedPlanCache<Long, TransitNetwork.Segment> transitSegments;
    /** Layout eleven: each region's growth sites and wildlife habitats, beside its plan. */
    private final BoundedPlanCache<Long, EcologySites> ecologySites;
    private int contentRevision = UNRESOLVED;
    /** Persistent sparse state, separate from evictable pure plan caches. */
    public final UrbanContainers containers = new UrbanContainers();
    /** Layout ten: the stations and hidden cellars this world's survivor has found. */
    public final UrbanDiscovery discovery = new UrbanDiscovery();
    /** Starter anchor only; claims and upgrades are introduced in milestone 18. */
    public long refugeBuilding;
    public com.veylon.util.Vec3i refugeAnchor;
    public boolean refugeSectionRestored;

    /**
     * @throws IllegalArgumentException for a generator that is not urban
     */
    public UrbanWorldState(long seed, int generatorVersion) {
        this.seed = seed;
        this.layoutRevision = UrbanRevision.layoutRevisionOf(generatorVersion);
        this.buildingTemplateRevision = UrbanRevision.buildingTemplateRevisionOf(generatorVersion);
        this.regionPlans = hasCityPlan() ? new BoundedPlanCache<>(MAX_REGION_PLANS) : null;
        this.buildingPlans = buildingTemplateRevision == 0 ? null : new BoundedPlanCache<>(MAX_BUILDING_PLANS);
        this.routeCatalogues = hasUnderground() ? new BoundedPlanCache<>(MAX_REGION_PLANS) : null;
        this.transitSegments = hasUnderground() ? new BoundedPlanCache<>(MAX_TRANSIT_SEGMENTS) : null;
        this.ecologySites = hasEcology() ? new BoundedPlanCache<>(MAX_REGION_PLANS) : null;
    }

    public int layoutRevision() {
        return layoutRevision;
    }

    /** True for a layout with streets, parcels and districts; false for the bare datum. */
    public boolean hasCityPlan() {
        return layoutRevision == UrbanRevision.LAYOUT_STREET_NETWORK
                || layoutRevision == UrbanRevision.LAYOUT_BUILDING_PLANS || hasGeneratedBuildings();
    }

    /** Earlier urban generators keep their unbuilt parcel geometry. */
    public boolean hasGeneratedBuildings() {
        return layoutRevision == UrbanRevision.LAYOUT_GENERATED_APARTMENTS
                || layoutRevision == UrbanRevision.LAYOUT_SHAPED_STAIRS || hasStarter();
    }

    public boolean hasStarter() {
        return layoutRevision == UrbanRevision.LAYOUT_STARTER || hasEncounters() || hasCivicCatalogue();
    }
    public boolean hasEncounters() {
        return layoutRevision == UrbanRevision.LAYOUT_ENCOUNTERS || hasCivicCatalogue();
    }
    /**
     * Commercial and civic families exist: layout eight on the generator-ten starter city, and
     * layout nine, whose district catalogue places them by district.
     */
    public boolean hasCivicCatalogue() { return layoutRevision == UrbanRevision.LAYOUT_CIVIC || hasDistricts(); }
    /**
     * Six districts with overlays and district climates: layout nine, and layout ten, which
     * keeps that city exactly and adds its routes.
     */
    public boolean hasDistricts() { return layoutRevision == UrbanRevision.LAYOUT_DISTRICTS || hasUnderground(); }
    /**
     * Transit under the boundary avenues and template seven's routes (milestone 14): layout ten,
     * and layout eleven, which keeps them exactly.
     */
    public boolean hasUnderground() { return layoutRevision == UrbanRevision.LAYOUT_UNDERGROUND || hasEcology(); }
    /** Layout eleven: growth sites in the quarantine and city wildlife (milestone 15). */
    public boolean hasEcology() { return layoutRevision == UrbanRevision.LAYOUT_ECOLOGY; }

    public boolean contentRevisionResolved() {
        return contentRevision != UNRESOLVED;
    }

    /**
     * @throws IllegalStateException while a load has not yet restored the revision
     */
    public int contentRevision() {
        if (contentRevision == UNRESOLVED) {
            throw new IllegalStateException("urban content revision is not resolved yet");
        }
        return contentRevision;
    }

    /**
     * Fixes this world's content revision. A world resolves it exactly once.
     *
     * @throws IllegalArgumentException for a revision this build cannot honour
     * @throws IllegalStateException if the revision was already resolved
     */
    public void resolveContentRevision(int revision) {
        UrbanRevision.requireSupportedContent(revision);
        if (hasStarter() && revision < 3) {
            throw new IllegalArgumentException("starter layout requires urban content revision 3 or later");
        }
        if (contentRevision != UNRESOLVED) {
            throw new IllegalStateException("urban content revision is already resolved ("
                    + contentRevision + ")");
        }
        contentRevision = revision;
    }

    // ---- generation ----------------------------------------------------------

    /** Generates one chunk of this world's urban layout; writes only inside it. */
    public void stamp(Chunk chunk, float soilMoisture) {
        if (!hasCityPlan()) {
            UrbanPlaceholderTerrain.stamp(chunk, soilMoisture);
            return;
        }
        int rx = StreetNetwork.regionOf(chunk.cx * Chunk.SX);
        int rz = StreetNetwork.regionOf(chunk.cz * Chunk.SZ);
        if (!CityPlanner.isPlannable(rx, rz)) {
            UrbanChunkStamper.stampDatum(chunk, soilMoisture);
            return;
        }
        DistrictPlan district = regionPlan(rx, rz);
        UrbanChunkStamper.stamp(chunk, district, soilMoisture);
        if (hasGeneratedBuildings()) {
            UrbanBuildingStamper.stamp(chunk, district, this);
            if (hasDistricts()) { UrbanOverlayStamper.stamp(chunk, district); }
            if (hasStarter() && rx == 0 && rz == 0) { UrbanStarterSite.of(this).stamp(chunk); }
            // Routes come last: tunnels under the avenues, then what buildings declared outside them.
            if (hasUnderground()) { UrbanRouteStamper.stamp(chunk, district, this); }
            // Growth last of all: its carpet and node lie over the courtyard and square.
            if (hasEcology()) { UrbanEcologyStamper.stamp(chunk, ecology(rx, rz)); }
        }
    }

    // ---- plan queries ------------------------------------------------------

    /**
     * The plan of a region, built on first request and kept in the bounded cache.
     *
     * @throws IllegalStateException for a layout without a city plan
     * @throws IllegalArgumentException for a region urban ids cannot name
     */
    public DistrictPlan regionPlan(int rx, int rz) {
        if (!hasCityPlan()) {
            throw new IllegalStateException("layout revision " + layoutRevision + " has no city plan");
        }
        long key = UrbanIds.district(rx, rz);
        // Generators 6 and 7 explicitly inherit layout-2 city fabric, not new hash inputs;
        // layout nine keeps that lattice and adds its districts.
        return regionPlans.get(key, ignored -> hasDistricts() ? CityPlanner.planDistricts(seed, rx, rz)
                : hasStarter() ? CityPlanner.planStarter(seed, rx, rz)
                : CityPlanner.plan(seed, UrbanRevision.LAYOUT_STREET_NETWORK, rx, rz));
    }

    public int buildingTemplateRevision() { return buildingTemplateRevision; }

    public int cachedBuildingPlans() { return buildingPlans == null ? 0 : buildingPlans.size(); }

    /** A validated whole-building proposal; generators 4 and 5 continue to have none. */
    public BuildingPlan buildingAt(int x, int z) {
        if (buildingPlans == null) { return null; }
        ParcelPlan parcel = parcelAt(x, z);
        if (parcel == null) { return null; }
        // Template five chooses each family from the parcel's own (pure, cached) district plan;
        // template seven adds the region's routes from its cached catalogue.
        return buildingPlans.get(UrbanIds.building(parcel.id(), 0), ignored -> hasUnderground()
                ? BuildingPlanner.planRoutes(seed, parcel, planAt(x, z), routes(StreetNetwork.regionOf(x),
                        StreetNetwork.regionOf(z))).plan()
                : BuildingPlanner.plan(seed, buildingTemplateRevision, parcel, planAt(x, z)).plan());
    }

    /**
     * The route catalogue of a layout-ten region: its service passages, hidden cellar, fire
     * escapes and roof connectors. Pure and rebuilt identically after eviction.
     *
     * @throws IllegalStateException for a layout without routes
     */
    public RouteCatalogue routes(int rx, int rz) {
        if (!hasUnderground()) { throw new IllegalStateException("layout " + layoutRevision + " has no routes"); }
        return routeCatalogues.get(UrbanIds.district(rx, rz), ignored -> RouteCatalogue.of(seed, regionPlan(rx, rz)));
    }

    /**
     * The growth sites and wildlife habitats of a layout-eleven region, planned once and cached.
     * Pure, and rebuilt identically after eviction.
     *
     * @throws IllegalStateException for a layout without ecology
     */
    public EcologySites ecology(int rx, int rz) {
        if (!hasEcology()) { throw new IllegalStateException("layout " + layoutRevision + " has no ecology"); }
        return ecologySites.get(UrbanIds.district(rx, rz), ignored -> EcologySites.of(seed, regionPlan(rx, rz)));
    }

    /** Ecology plans currently cached, for the runtime bounds snapshot; never above {@value #MAX_REGION_PLANS}. */
    public int cachedEcologyPlans() {
        return ecologySites == null ? 0 : ecologySites.size();
    }

    /** One transit segment of layout ten, planned once and cached; absent beyond the planned city. */
    public TransitNetwork.Segment segment(boolean northSouth, int line, int index) {
        if (!hasUnderground()) { throw new IllegalStateException("layout " + layoutRevision + " has no transit"); }
        return transitSegments.get(TransitNetwork.key(northSouth, line, index),
                ignored -> TransitNetwork.plan(seed, northSouth, line, index, this::regionPlan));
    }

    /** The segment a band position belongs to. */
    public TransitNetwork.Segment segment(TransitNetwork.Band band) {
        return segment(band.northSouth(), band.line(), band.index());
    }

    /**
     * The transit station whose footprint holds a column, or null: only boundary-avenue columns
     * of a layout-ten world can have one. Reads plans, never chunks.
     */
    public TransitStation stationAt(int x, int z) {
        if (!hasUnderground()) { return null; }
        for (boolean northSouth : new boolean[]{true, false}) {
            TransitNetwork.Band band = TransitNetwork.band(northSouth, x, z);
            if (band == null || !CityPlanner.isPlannable(StreetNetwork.regionOf(x), StreetNetwork.regionOf(z))) { continue; }
            TransitNetwork.Segment s = segment(band);
            if (s.present() && s.station().containsColumn(x, z)) { return s.station(); }
        }
        return null;
    }

    /**
     * Route catalogues and transit segments currently cached, for the runtime bounds snapshot;
     * never above {@value #MAX_ROUTE_PLANS}.
     */
    public int cachedRoutePlans() {
        return (routeCatalogues == null ? 0 : routeCatalogues.size()) + (transitSegments == null ? 0 : transitSegments.size());
    }

    /** Region plans currently cached; never above {@value #MAX_REGION_PLANS}. */
    public int cachedRegionPlans() {
        return regionPlans == null ? 0 : regionPlans.size();
    }

    /** The planned surface of a column; {@link UrbanSurface#DATUM} where nothing is planned. */
    public UrbanSurface surfaceAt(int x, int z) {
        DistrictPlan plan = planAt(x, z);
        return plan == null ? UrbanSurface.DATUM : plan.surfaceAt(x, z);
    }

    /** The walkable ground slab of a column; feet stand one block above it. */
    public int groundY(int x, int z) {
        return UrbanDatum.STREET_Y;
    }

    /**
     * The slab y of the storey envelope reserved over a column: the parcel's
     * planned roof inside a parcel, the ground elsewhere. Layout revision 2
     * builds nothing to that height yet.
     */
    public int plannedRoofY(int x, int z) {
        ParcelPlan parcel = parcelAt(x, z);
        return parcel == null ? groundY(x, z) : parcel.plannedRoofY();
    }

    /** The reserved parcel covering a column, or null. */
    public ParcelPlan parcelAt(int x, int z) {
        DistrictPlan plan = planAt(x, z);
        return plan == null ? null : plan.parcelAt(x, z);
    }

    /** The district identity of a column's region, or null where nothing is planned. */
    public UrbanDistrict districtAt(int x, int z) {
        DistrictPlan plan = planAt(x, z);
        return plan == null ? null : plan.district;
    }

    /**
     * {x, z} of the column a new player stands on: in front of the starter
     * refuge's courtyard door, or the fixed placeholder column of layout 1.
     */
    public int[] spawnColumn() {
        if (!hasCityPlan()) {
            return new int[]{PLACEHOLDER_SPAWN_X, PLACEHOLDER_SPAWN_Z};
        }
        return regionPlan(0, 0).spawnAnchor();
    }

    private DistrictPlan planAt(int x, int z) {
        if (!hasCityPlan()) {
            return null;
        }
        int rx = StreetNetwork.regionOf(x);
        int rz = StreetNetwork.regionOf(z);
        return CityPlanner.isPlannable(rx, rz) ? regionPlan(rx, rz) : null;
    }
}
