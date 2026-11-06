package com.veylon.world.urban;

import com.veylon.world.urban.ParcelPlan.Rect;

import java.util.List;

/**
 * Plans the {@link UrbanOverlay} raster of one layout-nine region (milestone 13): what each
 * district lays over its streets and open ground.
 *
 * <ul>
 *   <li><b>Reclaimed residential:</b> tall grass and bushes on courtyard and park lawns, grass
 *       breaking through the outer sidewalk.</li>
 *   <li><b>Commercial downtown:</b> planted boulevard medians.</li>
 *   <li><b>Industrial yards:</b> fenced yards with gateways, chemical spills and powder
 *       kegs.</li>
 *   <li><b>Flooded neighbourhoods:</b> a block of standing water on every local roadway and
 *       courtyard lawn, with raised duckboards where the crosswalks were. Avenues stay dry, so
 *       they are the raised routes between districts and every seam agrees.</li>
 *   <li><b>Civic precinct:</b> a fenced square entered through four checkpoints, a little
 *       lawn growth.</li>
 *   <li><b>Quarantine:</b> a sheet-metal cordon along the middle of every avenue it shares
 *       with a district outside the quarantine, open only at checkpoints where the local
 *       streets cross; a fenced square; contaminated lawns, plaza and roadway patches.</li>
 * </ul>
 *
 * <p>Rules that keep every building accessible (rule A9): nothing within
 * {@value #PARCEL_CLEARANCE} cells of a parcel (so no overlay enters a building's validated
 * volume or its approach halo, and entrances, approaches and inner sidewalks stay clear),
 * nothing on courtyard walkways, passages, alleys, park paths or causeways, nothing in the
 * starter superblock, and no water, duckboard or contamination inside an avenue band. Solid
 * overlays (fences, kegs) only stand on open squares and yards or on the avenue centre line,
 * always leaving gaps. {@link CityPlanDiagnostics} re-checks all of it.
 */
final class DistrictOverlays {

    static final int SIZE = StreetNetwork.REGION_SIZE;
    /** Chebyshev distance every overlay keeps from every parcel cell. */
    static final int PARCEL_CLEARANCE = 2;
    /** Width of a gateway or checkpoint in a fenced square or yard. */
    static final int GATE_WIDTH = 4;
    static final int MAX_KEGS_PER_YARD = 6;
    private static final int LAYOUT = UrbanRevision.LAYOUT_DISTRICTS;

    private final long seed;
    private final int rx;
    private final int rz;
    private final int ox;
    private final int oz;
    private final UrbanDistrict district;
    private final byte[] surface;
    private final byte[] parcelIndex;
    private final int[] northSouthParts;
    private final int[] eastWestParts;
    private final List<SuperblockPlan> superblocks;
    private final byte[] out = new byte[SIZE * SIZE];

    DistrictOverlays(long seed, int rx, int rz, UrbanDistrict district, byte[] surface, byte[] parcelIndex,
                     int[] northSouthParts, int[] eastWestParts, List<SuperblockPlan> superblocks) {
        this.seed = seed;
        this.rx = rx;
        this.rz = rz;
        this.ox = StreetNetwork.regionOrigin(rx);
        this.oz = StreetNetwork.regionOrigin(rz);
        this.district = district;
        this.surface = surface;
        this.parcelIndex = parcelIndex;
        this.northSouthParts = northSouthParts;
        this.eastWestParts = eastWestParts;
        this.superblocks = superblocks;
    }

    byte[] plan() {
        boolean[] blocked = clearance();
        for (int lz = 0; lz < SIZE; lz++) {
            for (int lx = 0; lx < SIZE; lx++) {
                int c = lz * SIZE + lx;
                if (!blocked[c]) {
                    out[c] = surfaceOverlay(UrbanSurface.fromCode(surface[c]), lx, lz).code;
                }
            }
        }
        for (SuperblockPlan block : superblocks) {
            if (block.kind() == SuperblockPlan.Kind.PLAZA
                    && (district == UrbanDistrict.CIVIC || district == UrbanDistrict.QUARANTINE)) {
                fenceRing(block.bounds(), UrbanOverlay.CHECKPOINT);
            } else if (block.kind() == SuperblockPlan.Kind.YARD) {
                fenceRing(block.bounds(), UrbanOverlay.NONE);
                kegs(block.bounds());
            }
        }
        if (district == UrbanDistrict.QUARANTINE) {
            cordon();
        }
        return out;
    }

    /** Cells no overlay may take: near a parcel, or inside the starter superblock. */
    private boolean[] clearance() {
        boolean[] blocked = new boolean[SIZE * SIZE];
        int reach = PARCEL_CLEARANCE - 1;
        for (int lz = 0; lz < SIZE; lz++) {
            for (int lx = 0; lx < SIZE; lx++) {
                if (parcelIndex[lz * SIZE + lx] == DistrictPlan.NO_PARCEL) {
                    continue;
                }
                for (int dz = -reach; dz <= reach; dz++) {
                    for (int dx = -reach; dx <= reach; dx++) {
                        int x = lx + dx;
                        int z = lz + dz;
                        if (x >= 0 && z >= 0 && x < SIZE && z < SIZE) {
                            blocked[z * SIZE + x] = true;
                        }
                    }
                }
            }
        }
        if (rx == 0 && rz == 0) {
            Rect starter = superblocks.get(CityPlanner.STARTER_BLOCK).bounds();
            for (int z = starter.z0(); z <= starter.z1(); z++) {
                for (int x = starter.x0(); x <= starter.x1(); x++) {
                    blocked[(z - oz) * SIZE + (x - ox)] = true;
                }
            }
        }
        return blocked;
    }

    private UrbanOverlay surfaceOverlay(UrbanSurface s, int lx, int lz) {
        int x = ox + lx;
        int z = oz + lz;
        boolean lawn = s == UrbanSurface.COURTYARD || s == UrbanSurface.PARK;
        return switch (district) {
            case RESIDENTIAL -> lawn ? plant(x, z, 0.15)
                    : s == UrbanSurface.SIDEWALK && patch(x, z, 1, 0.10) ? UrbanOverlay.OVERGROWN : UrbanOverlay.NONE;
            case COMMERCIAL -> s == UrbanSurface.MEDIAN ? plant(x, z, 0.12) : UrbanOverlay.NONE;
            case INDUSTRIAL -> s == UrbanSurface.YARD && patch(x, z, 2, 0.14)
                    ? UrbanOverlay.CONTAMINATED : UrbanOverlay.NONE;
            case FLOODED -> s == UrbanSurface.COURTYARD ? UrbanOverlay.STANDING_WATER
                    : localRoadway(s, lx, lz)
                    ? (crossing(lx, lz) ? UrbanOverlay.DUCKBOARD : UrbanOverlay.STANDING_WATER) : UrbanOverlay.NONE;
            case CIVIC -> lawn ? plant(x, z, 0.06) : UrbanOverlay.NONE;
            case QUARANTINE -> {
                boolean tainted = lawn ? patch(x, z, 3, 0.35)
                        : s == UrbanSurface.PLAZA ? patch(x, z, 4, 0.2)
                        : localRoadway(s, lx, lz) && patch(x, z, 5, 0.12);
                yield tainted ? UrbanOverlay.CONTAMINATED : UrbanOverlay.NONE;
            }
        };
    }

    private UrbanOverlay plant(int x, int z, double chance) {
        return UrbanHash.unit(seed, LAYOUT, UrbanSalts.OVERLAY_PLANT, x, z, 0) < chance
                ? UrbanOverlay.PLANTS : UrbanOverlay.NONE;
    }

    /** Two-by-two patches, so spills and overgrowth read as shapes rather than noise. */
    private boolean patch(int x, int z, int kind, double chance) {
        return UrbanHash.unit(seed, LAYOUT, UrbanSalts.OVERLAY_PATCH, x >> 1, z >> 1, kind) < chance;
    }

    /** True inside one of the region's four avenue half-bands. */
    static boolean inAvenueBand(int lx, int lz) {
        int edge = StreetNetwork.AVENUE_HALF_WIDTH;
        return lx < edge || lz < edge || lx >= SIZE - edge || lz >= SIZE - edge;
    }

    /** Roadway of a local street: lane, crossing or intersection, never an avenue. */
    private static boolean localRoadway(UrbanSurface s, int lx, int lz) {
        return (s == UrbanSurface.ROADWAY || s == UrbanSurface.LANE_MARK || s == UrbanSurface.CROSSWALK)
                && !inAvenueBand(lx, lz);
    }

    /** Where a sidewalk band crosses a roadway: the crosswalk zone. */
    private boolean crossing(int lx, int lz) {
        int ns = northSouthParts[lx] >> 8;
        int ew = eastWestParts[lz] >> 8;
        return ns == StreetNetwork.WALK && road(ew) || ew == StreetNetwork.WALK && road(ns);
    }

    private static boolean road(int part) {
        return part == StreetNetwork.ROAD || part == StreetNetwork.CENTRE_LANE
                || part == StreetNetwork.CENTRE_MEDIAN;
    }

    /** Fences the outer ring of an open superblock, with a gateway in the middle of each side. */
    private void fenceRing(Rect b, UrbanOverlay gate) {
        int cx = (b.x0() + b.x1() + 1) / 2;
        int cz = (b.z0() + b.z1() + 1) / 2;
        int half = GATE_WIDTH / 2;
        for (int z = b.z0(); z <= b.z1(); z++) {
            for (int x = b.x0(); x <= b.x1(); x++) {
                boolean northSouthSide = z == b.z0() || z == b.z1();
                boolean westEastSide = x == b.x0() || x == b.x1();
                if (!northSouthSide && !westEastSide) {
                    continue;
                }
                boolean corner = northSouthSide && westEastSide;
                boolean gap = !corner && (northSouthSide ? x >= cx - half && x < cx + half
                        : z >= cz - half && z < cz + half);
                set(x, z, gap ? gate : UrbanOverlay.FENCE);
            }
        }
    }

    /** At most {@value #MAX_KEGS_PER_YARD} kegs on a coarse grid well inside a yard's fence. */
    private void kegs(Rect b) {
        int placed = 0;
        for (int z = b.z0() + 3; z <= b.z1() - 3 && placed < MAX_KEGS_PER_YARD; z += 6) {
            for (int x = b.x0() + 3; x <= b.x1() - 3 && placed < MAX_KEGS_PER_YARD; x += 6) {
                if (UrbanHash.unit(seed, LAYOUT, UrbanSalts.OVERLAY_KEG, x, z, 0) < 0.3) {
                    set(x, z, UrbanOverlay.KEG);
                    placed++;
                }
            }
        }
    }

    /**
     * The quarantine cordon: the centre row of each avenue half the region shares with a
     * district outside the quarantine, fenced except where a local street crosses it. Those
     * gaps are the checkpoints. Neighbouring quarantine regions leave their shared avenue open,
     * so a quarantine area is one enclosure whichever region is planned first.
     */
    private void cordon() {
        int edge = StreetNetwork.AVENUE_HALF_WIDTH;
        boolean north = !quarantine(rx, rz - 1);
        boolean south = !quarantine(rx, rz + 1);
        boolean west = !quarantine(rx - 1, rz);
        boolean east = !quarantine(rx + 1, rz);
        for (int l = 0; l < SIZE; l++) {
            boolean localX = l >= edge && l < SIZE - edge && (northSouthParts[l] >> 8) != StreetNetwork.NONE;
            boolean localZ = l >= edge && l < SIZE - edge && (eastWestParts[l] >> 8) != StreetNetwork.NONE;
            UrbanOverlay acrossX = localX ? UrbanOverlay.CHECKPOINT : UrbanOverlay.FENCE;
            UrbanOverlay acrossZ = localZ ? UrbanOverlay.CHECKPOINT : UrbanOverlay.FENCE;
            if (north) { set(ox + l, oz, acrossX); }
            if (south) { set(ox + l, oz + SIZE - 1, acrossX); }
            if (west) { set(ox, oz + l, acrossZ); }
            if (east) { set(ox + SIZE - 1, oz + l, acrossZ); }
        }
        // Row and column ends lie in avenue bands, so every corner of a fenced side is fenced.
    }

    private boolean quarantine(int x, int z) {
        return CityPlanner.isPlannable(x, z) && DistrictMap.districtOf(seed, x, z) == UrbanDistrict.QUARANTINE;
    }

    private void set(int x, int z, UrbanOverlay overlay) {
        out[(z - oz) * SIZE + (x - ox)] = overlay.code;
    }
}
