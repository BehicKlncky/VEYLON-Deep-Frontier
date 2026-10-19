package com.veylon.world.urban;

import com.veylon.world.urban.ParcelPlan.Entrance;
import com.veylon.world.urban.ParcelPlan.Rect;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bounded checks of a district plan against the rules of layout revisions 2
 * and 9, for tests, QA scenes and logs.
 *
 * <p>Each report covers one region: its seed and district, cell counts per kind
 * of space, and five violation counters — overlaps (a cell two plan elements
 * wanted, or one left unassigned), parcel rules (sizes, counts, ids,
 * entrances, stair cores, storeys), routes (a parcel covering a reserved
 * route, open ground unreachable from the street around solid overlays),
 * seams (a border column that does not mirror its neighbour) and, in layout
 * 9, overlay rules (an overlay too near a parcel, on the wrong surface, or a
 * cordon side without a checkpoint). Work is fixed per region and the
 * message list keeps only the first {@value #MAX_MESSAGES}; the counters keep
 * the true totals.
 */
public final class CityPlanDiagnostics {

    public static final int MAX_MESSAGES = 16;

    private CityPlanDiagnostics() {
    }

    /** One region's result. {@link #ok()} is true when every violation counter is zero. */
    public record Report(long seed, int layoutRevision, int rx, int rz, UrbanDistrict district,
                         int streetCells, int routeCells, int parcelCells, int openCells,
                         int parcels, int courtyards, int shopRows, int parks, int plazas, int canals, int yards,
                         int trees, int overlayCells, int overlapViolations, int parcelViolations,
                         int routeViolations, int seamViolations, int overlayViolations, int seamsChecked,
                         List<String> messages, int droppedMessages) {

        public boolean ok() {
            return overlapViolations == 0 && parcelViolations == 0 && routeViolations == 0
                    && seamViolations == 0 && overlayViolations == 0;
        }

        /** One log line. */
        public String summary() {
            return String.format(Locale.ROOT, "region=%d,%d seed=%d layout=%d district=%s"
                            + " streets=%d routes=%d parcelCells=%d open=%d parcels=%d courtyards=%d"
                            + " shopRows=%d parks=%d plazas=%d canals=%d yards=%d trees=%d overlays=%d"
                            + " overlaps=%d parcelRules=%d routeRules=%d seams=%d/%d overlayRules=%d ok=%s",
                    rx, rz, seed, layoutRevision, district.id, streetCells, routeCells, parcelCells,
                    openCells, parcels, courtyards, shopRows, parks, plazas, canals, yards, trees, overlayCells,
                    overlapViolations, parcelViolations, routeViolations, seamViolations, seamsChecked,
                    overlayViolations, ok());
        }
    }

    /** Plans a region and its four neighbours afresh, without any cache, and checks it. */
    public static Report inspect(long seed, int layoutRevision, int rx, int rz) {
        DistrictPlan plan = CityPlanner.plan(seed, layoutRevision, rx, rz);
        return inspect(plan,
                neighbour(seed, layoutRevision, rx - 1, rz), neighbour(seed, layoutRevision, rx + 1, rz),
                neighbour(seed, layoutRevision, rx, rz - 1), neighbour(seed, layoutRevision, rx, rz + 1));
    }

    private static DistrictPlan neighbour(long seed, int layoutRevision, int rx, int rz) {
        return CityPlanner.isPlannable(rx, rz) ? CityPlanner.plan(seed, layoutRevision, rx, rz) : null;
    }

    /**
     * Checks a plan; a null neighbour skips that seam.
     *
     * @throws IllegalArgumentException if a neighbour is not adjacent on its side
     */
    public static Report inspect(DistrictPlan plan, DistrictPlan west, DistrictPlan east,
                                 DistrictPlan north, DistrictPlan south) {
        Collector c = new Collector(plan);
        c.countCells();
        c.checkParcels();
        c.checkRoutes();
        c.checkOverlays();
        c.seam(west, -1, 0);
        c.seam(east, 1, 0);
        c.seam(north, 0, -1);
        c.seam(south, 0, 1);
        return c.report();
    }

    /**
     * Number of border columns where two plans fail to mirror each other along
     * their shared edge: the cell {@code d} steps inside one must equal the cell
     * {@code d} steps inside the other, for the six cells of each avenue half.
     *
     * @throws IllegalArgumentException unless {@code b} is the east or south neighbour of {@code a}
     */
    public static int seamMismatches(DistrictPlan a, DistrictPlan b) {
        boolean eastward = b.rx == a.rx + 1 && b.rz == a.rz;
        boolean southward = b.rz == a.rz + 1 && b.rx == a.rx;
        if (!eastward && !southward) {
            throw new IllegalArgumentException("regions " + a.rx + "," + a.rz + " and " + b.rx + ","
                    + b.rz + " do not share an east or south edge");
        }
        int mismatches = 0;
        int edge = eastward ? b.originX() : b.originZ();
        for (int along = 0; along < StreetNetwork.REGION_SIZE; along++) {
            for (int d = 0; d < StreetNetwork.AVENUE_HALF_WIDTH; d++) {
                UrbanSurface inA;
                UrbanSurface inB;
                if (eastward) {
                    int z = a.originZ() + along;
                    inA = a.surfaceAt(edge - 1 - d, z);
                    inB = b.surfaceAt(edge + d, z);
                } else {
                    int x = a.originX() + along;
                    inA = a.surfaceAt(x, edge - 1 - d);
                    inB = b.surfaceAt(x, edge + d);
                }
                if (inA != inB || !inA.street) {
                    mismatches++;
                }
            }
        }
        return mismatches;
    }

    private static final class Collector {
        final DistrictPlan plan;
        final List<String> messages = new ArrayList<>();
        int dropped;
        int streetCells;
        int routeCells;
        int parcelCells;
        int openCells;
        int overlaps;
        int parcelRules;
        int routeRules;
        int seams;
        int seamsChecked;
        int overlayCells;
        int overlayRules;

        Collector(DistrictPlan plan) {
            this.plan = plan;
            overlaps = plan.overlapConflicts();
            if (overlaps > 0) {
                note("%d overlapping claims while planning", overlaps);
            }
            if (plan.fallbackSuperblocks() > 0) {
                parcelRules += plan.fallbackSuperblocks();
                note("%d superblocks fell back to open space", plan.fallbackSuperblocks());
            }
        }

        void note(String format, Object... args) {
            if (messages.size() < MAX_MESSAGES) {
                messages.add(String.format(Locale.ROOT, format, args));
            } else {
                dropped++;
            }
        }

        void countCells() {
            int voids = 0;
            for (int z = plan.originZ(); z < plan.originZ() + StreetNetwork.REGION_SIZE; z++) {
                for (int x = plan.originX(); x < plan.originX() + StreetNetwork.REGION_SIZE; x++) {
                    UrbanSurface s = plan.surfaceAt(x, z);
                    if (s == UrbanSurface.VOID) {
                        voids++;
                    } else if (s.street) {
                        streetCells++;
                    } else if (s.parcel) {
                        parcelCells++;
                    } else if (s.route) {
                        routeCells++;
                    } else {
                        openCells++;
                    }
                }
            }
            if (voids > 0) {
                overlaps += voids;
                note("%d cells left unassigned", voids);
            }
        }

        void checkParcels() {
            int[] perBlock = new int[StreetNetwork.SUPERBLOCKS_PER_SIDE * StreetNetwork.SUPERBLOCKS_PER_SIDE];
            for (ParcelPlan p : plan.parcels()) {
                perBlock[p.superblock()]++;
                String name = "parcel " + p.index();
                rule(p.width() >= ParcelPlan.MIN_SIDE && p.width() <= ParcelPlan.MAX_SIDE
                        && p.depth() >= ParcelPlan.MIN_SIDE && p.depth() <= ParcelPlan.MAX_SIDE,
                        "%s is %dx%d, outside %d..%d", name, p.width(), p.depth(),
                        ParcelPlan.MIN_SIDE, ParcelPlan.MAX_SIDE);
                rule(p.index() == p.superblock() * ParcelPlan.SLOTS_PER_SUPERBLOCK + p.slot()
                        && p.slot() < 6 && p.index() <= UrbanIds.MAX_PARCEL,
                        "%s has an index outside its superblock", name);
                rule(UrbanIds.kind(p.id()) == UrbanIds.Kind.PARCEL && UrbanIds.parent(p.id()) == plan.districtId
                        && UrbanIds.parcelIndex(p.id()) == p.index(), "%s has a wrong id", name);
                int ceiling = p.use() == ParcelPlan.Use.RESIDENTIAL ? 5 : 8;
                rule(p.maxStoreys() >= 1 && p.maxStoreys() <= ceiling,
                        "%s envelope of %d storeys", name, p.maxStoreys());
                // The whole rectangle is this parcel's: nothing else, least of all a route, inside it.
                int foreign = 0;
                for (int z = p.z0(); z <= p.z1(); z++) {
                    for (int x = p.x0(); x <= p.x1(); x++) {
                        if (plan.parcelIndexAt(x, z) != p.index() || !plan.surfaceAt(x, z).parcel) {
                            foreign++;
                        }
                    }
                }
                if (foreign > 0) {
                    routeRules++;
                    note("%s shares %d cells with another plan element", name, foreign);
                }
                checkEntrances(p, name);
                Rect core = p.core();
                boolean coreInside = core.x0() > p.x0() && core.x1() < p.x1()
                        && core.z0() > p.z0() && core.z1() < p.z1();
                boolean coreMarked = true;
                for (int z = core.z0(); z <= core.z1() && coreInside; z++) {
                    for (int x = core.x0(); x <= core.x1(); x++) {
                        coreMarked &= plan.surfaceAt(x, z) == UrbanSurface.STAIR_CORE;
                    }
                }
                rule(coreInside && coreMarked
                                && core.x1() - core.x0() + core.z1() - core.z0() + 2
                                == ParcelPlan.CORE_WIDTH + ParcelPlan.CORE_DEPTH,
                        "%s stair core is not a reserved %dx%d footprint inside its outline", name,
                        ParcelPlan.CORE_WIDTH, ParcelPlan.CORE_DEPTH);
            }
            for (SuperblockPlan block : plan.superblocks()) {
                int count = perBlock[block.index()];
                boolean expected = block.hasParcels() ? count >= 2 && count <= 6 : count == 0;
                rule(expected, "superblock %d (%s) holds %d parcels", block.index(), block.kind(), count);
            }
            if (plan.rx == 0 && plan.rz == 0) {
                ParcelPlan refuge = plan.refuge();
                int[] anchor = plan.spawnAnchor();
                rule(refuge != null && anchor != null
                                && plan.surfaceAt(anchor[0], anchor[1]) == UrbanSurface.COURTYARD_PATH,
                        "the starter region has no refuge with a spawn anchor on its courtyard walk");
            } else {
                rule(plan.refuge() == null && plan.spawnAnchor() == null,
                        "a refuge was reserved outside the starter region");
            }
        }

        void checkEntrances(ParcelPlan p, String name) {
            rule(p.entrances().size() == 2 && p.entrances().get(0).primary()
                    && p.entrances().get(0).facing() == p.frontage(), "%s lacks its entrances", name);
            for (Entrance door : p.entrances()) {
                boolean marked = plan.surfaceAt(door.x(), door.z()) == UrbanSurface.ENTRANCE
                        && plan.surfaceAt(door.x2(), door.z2()) == UrbanSurface.ENTRANCE;
                boolean onOutline = p.onOutline(door.x(), door.z()) && p.onOutline(door.x2(), door.z2());
                UrbanSurface outside = plan.contains(door.outsideX(), door.outsideZ())
                        ? plan.surfaceAt(door.outsideX(), door.outsideZ()) : UrbanSurface.VOID;
                UrbanSurface outside2 = plan.contains(door.x2() + door.facing().dx, door.z2() + door.facing().dz)
                        ? plan.surfaceAt(door.x2() + door.facing().dx, door.z2() + door.facing().dz)
                        : UrbanSurface.VOID;
                boolean opens = door.primary()
                        ? outside == UrbanSurface.SIDEWALK && outside2 == UrbanSurface.SIDEWALK
                        : outside.route && outside2.route;
                rule(marked && onOutline && opens, "%s %s entrance at %d,%d opens onto %s/%s", name,
                        door.primary() ? "street" : "rear", door.x(), door.z(), outside, outside2);
            }
        }

        void rule(boolean holds, String format, Object... args) {
            if (!holds) {
                parcelRules++;
                note(format, args);
            }
        }

        /** Open to walk on: a public surface without a solid overlay (fence or keg) on it. */
        boolean walkable(int lx, int lz) {
            int x = plan.originX() + lx;
            int z = plan.originZ() + lz;
            return plan.surfaceAt(x, z).publicWalkable && !plan.overlayAt(x, z).blocksWalking;
        }

        /**
         * Every walkable cell of the region must be reachable from its streets
         * without crossing a parcel, a tree bed or a solid overlay.
         */
        void checkRoutes() {
            int size = StreetNetwork.REGION_SIZE;
            boolean[] seen = new boolean[size * size];
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    if (plan.surfaceAt(plan.originX() + lx, plan.originZ() + lz).street && walkable(lx, lz)) {
                        seen[lz * size + lx] = true;
                        queue.add(new int[]{lx, lz});
                    }
                }
            }
            int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            while (!queue.isEmpty()) {
                int[] at = queue.poll();
                for (int[] step : steps) {
                    int lx = at[0] + step[0];
                    int lz = at[1] + step[1];
                    if (lx < 0 || lz < 0 || lx >= size || lz >= size || seen[lz * size + lx]) {
                        continue;
                    }
                    if (walkable(lx, lz)) {
                        seen[lz * size + lx] = true;
                        queue.add(new int[]{lx, lz});
                    }
                }
            }
            int unreachable = 0;
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    if (walkable(lx, lz) && !seen[lz * size + lx]) {
                        unreachable++;
                    }
                }
            }
            if (unreachable > 0) {
                routeRules += unreachable;
                note("%d walkable cells are cut off from the streets", unreachable);
            }
            for (DistrictPlan.Tree tree : plan.trees()) {
                if (plan.surfaceAt(tree.x(), tree.z()) != UrbanSurface.TREE_BED) {
                    routeRules++;
                    note("tree at %d,%d is not on a tree bed", tree.x(), tree.z());
                }
            }
        }

        /**
         * Layout-nine overlays keep the A9 rules ({@link DistrictOverlays}): each stands at least
         * two cells from every parcel and outside the starter superblock, on a surface its kind
         * allows, with no water, duckboard or contamination in an avenue band; and every fenced
         * side of a quarantine cordon has a checkpoint.
         */
        void checkOverlays() {
            if (!plan.hasOverlays()) {
                return;
            }
            int size = StreetNetwork.REGION_SIZE;
            boolean[] nearParcel = new boolean[size * size];
            int reach = DistrictOverlays.PARCEL_CLEARANCE - 1;
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    if (plan.parcelIndexAt(plan.originX() + lx, plan.originZ() + lz) == DistrictPlan.NO_PARCEL) {
                        continue;
                    }
                    for (int dz = -reach; dz <= reach; dz++) {
                        for (int dx = -reach; dx <= reach; dx++) {
                            int x = lx + dx;
                            int z = lz + dz;
                            if (x >= 0 && z >= 0 && x < size && z < size) {
                                nearParcel[z * size + x] = true;
                            }
                        }
                    }
                }
            }
            Rect starter = plan.rx == 0 && plan.rz == 0
                    ? plan.superblock(CityPlanner.STARTER_BLOCK).bounds() : null;
            boolean[] fencedSide = new boolean[4];
            int[] checkpoints = new int[4];
            for (int lz = 0; lz < size; lz++) {
                for (int lx = 0; lx < size; lx++) {
                    int x = plan.originX() + lx;
                    int z = plan.originZ() + lz;
                    UrbanOverlay o = plan.overlayAt(x, z);
                    if (o == UrbanOverlay.NONE) {
                        continue;
                    }
                    overlayCells++;
                    UrbanSurface s = plan.surfaceAt(x, z);
                    if (nearParcel[lz * size + lx]) {
                        overlayRule("%s at %d,%d is inside a parcel's clearance", o, x, z);
                    } else if (starter != null && starter.contains(x, z)) {
                        overlayRule("%s at %d,%d is in the starter superblock", o, x, z);
                    } else if (!fits(o, s, lx, lz)) {
                        overlayRule("%s on %s at %d,%d in a %s district", o, s, x, z, plan.district.id);
                    }
                    if ((o == UrbanOverlay.FENCE || o == UrbanOverlay.CHECKPOINT) && s.street) {
                        // A corner closes two sides at once and belongs to neither.
                        boolean alongX = lx > 0 && lx < size - 1;
                        boolean alongZ = lz > 0 && lz < size - 1;
                        int side = lz == 0 && alongX ? 0 : lz == size - 1 && alongX ? 1
                                : lx == 0 && alongZ ? 2 : lx == size - 1 && alongZ ? 3 : -1;
                        if (side >= 0) {
                            fencedSide[side] = true;
                            checkpoints[side] += o == UrbanOverlay.CHECKPOINT ? 1 : 0;
                        }
                    }
                }
            }
            for (int side = 0; side < 4; side++) {
                if (fencedSide[side] && checkpoints[side] == 0) {
                    overlayRule("cordon side %d has no checkpoint", side);
                }
            }
        }

        boolean fits(UrbanOverlay o, UrbanSurface s, int lx, int lz) {
            boolean avenue = DistrictOverlays.inAvenueBand(lx, lz);
            boolean roadway = s == UrbanSurface.ROADWAY || s == UrbanSurface.LANE_MARK || s == UrbanSurface.CROSSWALK;
            boolean fencedSquare = s == UrbanSurface.PLAZA
                    && (plan.district == UrbanDistrict.CIVIC || plan.district == UrbanDistrict.QUARANTINE);
            int last = StreetNetwork.REGION_SIZE - 1;
            boolean boundary = lx == 0 || lz == 0 || lx == last || lz == last;
            return switch (o) {
                case NONE -> true;
                case STANDING_WATER -> plan.district == UrbanDistrict.FLOODED && !avenue
                        && (roadway || s == UrbanSurface.COURTYARD);
                case DUCKBOARD -> plan.district == UrbanDistrict.FLOODED && !avenue && roadway;
                case FENCE, CHECKPOINT -> fencedSquare || s == UrbanSurface.YARD
                        || plan.district == UrbanDistrict.QUARANTINE && s.street && boundary;
                case CONTAMINATED -> !avenue && (roadway || s == UrbanSurface.COURTYARD || s == UrbanSurface.PARK
                        || s == UrbanSurface.PLAZA || s == UrbanSurface.YARD);
                case OVERGROWN -> s == UrbanSurface.SIDEWALK;
                case PLANTS -> s == UrbanSurface.COURTYARD || s == UrbanSurface.PARK || s == UrbanSurface.MEDIAN;
                case KEG -> s == UrbanSurface.YARD;
            };
        }

        void overlayRule(String format, Object... args) {
            overlayRules++;
            note(format, args);
        }

        void seam(DistrictPlan other, int dx, int dz) {
            if (other == null) {
                return;
            }
            if (other.rx != plan.rx + dx || other.rz != plan.rz + dz) {
                throw new IllegalArgumentException("region " + other.rx + "," + other.rz
                        + " is not the neighbour at " + dx + "," + dz);
            }
            int mismatches = dx + dz > 0 ? seamMismatches(plan, other) : seamMismatches(other, plan);
            seamsChecked++;
            if (mismatches > 0) {
                seams += mismatches;
                note("%d border cells do not mirror region %d,%d", mismatches, other.rx, other.rz);
            }
        }

        Report report() {
            int parcels = plan.parcels().size();
            int courtyards = 0;
            int shopRows = 0;
            int parks = 0;
            int plazas = 0;
            int canals = 0;
            int yards = 0;
            for (SuperblockPlan block : plan.superblocks()) {
                switch (block.kind()) {
                    case COURTYARD_HOUSING -> courtyards++;
                    case SHOP_ROW -> shopRows++;
                    case PARK -> parks++;
                    case PLAZA -> plazas++;
                    case CANAL -> canals++;
                    case YARD -> yards++;
                }
            }
            // Layout two keeps one park or plaza; a layout-nine district has at most two open
            // superblocks of its own (canals, yards, or a square and a park).
            int open = parks + plazas + canals + yards;
            int limit = (plan.hasOverlays() ? 2 : 1) + plan.fallbackSuperblocks();
            if (open > limit) {
                parcelRules++;
                note("%d open superblocks; this layout allows %d", open, limit);
            }
            return new Report(plan.seed, plan.layoutRevision, plan.rx, plan.rz, plan.district,
                    streetCells, routeCells, parcelCells, openCells, parcels, courtyards, shopRows, parks,
                    plazas, canals, yards, plan.trees().size(), overlayCells, overlaps, parcelRules, routeRules,
                    seams, overlayRules, seamsChecked, List.copyOf(messages), dropped);
        }
    }
}
