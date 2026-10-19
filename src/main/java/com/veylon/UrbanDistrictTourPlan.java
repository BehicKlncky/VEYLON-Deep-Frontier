package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.DistrictMap;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.SuperblockPlan;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanDistrict;
import com.veylon.world.urban.UrbanOverlay;
import com.veylon.world.urban.UrbanSurface;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The milestone-13 district tour's stops, read from the frozen plans only: one walked
 * transition between two districts and, for each of the six, a subject that shows what makes
 * it different and a clear walkable cell to see it from.
 *
 * <ul>
 *   <li>Residential: tall grass and bushes on a courtyard lawn.</li>
 *   <li>Commercial: across a plaza from its edge (a shop-row alley where no region near the
 *       spawn has one).</li>
 *   <li>Industrial: through a fenced yard's gateway at its kegs and spills.</li>
 *   <li>Flooded: a duckboard over a flooded street; the tour also wades into the water beside it.</li>
 *   <li>Civic: through a checkpoint into a fenced square.</li>
 *   <li>Quarantine: square-on to a cordon checkpoint, from inside.</li>
 * </ul>
 */
record UrbanDistrictTourPlan(UrbanDistrict from, UrbanDistrict to, Vec3i transitionStart, Vec3i transitionEnd,
                             Map<UrbanDistrict, Stop> stops, Vec3i wade) {

    /** Where to stand, what to look at and the look height above the subject's block. */
    record Stop(Vec3i stand, Vec3i subject, float height) { }

    private static final int FEET = UrbanDatum.STREET_Y + 1;
    private static final int REACH = 3;

    static UrbanDistrictTourPlan of(World world) {
        if (world.urban == null || !world.urban.hasDistricts()) {
            throw new IllegalStateException("the district tour needs the six-district generator");
        }
        Map<UrbanDistrict, Stop> stops = new EnumMap<>(UrbanDistrict.class);
        Vec3i wade = null;
        for (UrbanDistrict district : UrbanDistrict.values()) {
            Stop stop = null;
            for (DistrictPlan plan : regions(world, district)) {
                stop = switch (district) {
                    case RESIDENTIAL -> near(plan, overlay(plan, UrbanOverlay.PLANTS, UrbanSurface.COURTYARD), .5f);
                    case COMMERCIAL -> gate(plan, SuperblockPlan.Kind.PLAZA, 1, 1.6f);
                    case INDUSTRIAL -> gate(plan, SuperblockPlan.Kind.YARD, 4, .5f);
                    case FLOODED -> near(plan, overlay(plan, UrbanOverlay.DUCKBOARD, null), .5f);
                    case CIVIC -> gate(plan, SuperblockPlan.Kind.PLAZA, 4, .5f);
                    case QUARANTINE -> cordon(plan);
                };
                if (stop != null) {
                    if (district == UrbanDistrict.FLOODED) {
                        wade = neighbour(plan, stop.subject(), UrbanOverlay.STANDING_WATER);
                    }
                    break;
                }
            }
            if (stop == null && district == UrbanDistrict.COMMERCIAL) {
                for (DistrictPlan plan : regions(world, district)) {
                    stop = alley(plan);
                    if (stop != null) {
                        break;
                    }
                }
            }
            if (stop == null) {
                throw new IllegalStateException("no tour stop for " + district);
            }
            stops.put(district, stop);
        }
        if (wade == null) {
            throw new IllegalStateException("no flooded water beside the duckboard");
        }
        // The transition: the nearest pair of edge-sharing regions with different districts.
        for (int ring = 0; ring <= 2; ring++) {
            for (int rz = -ring; rz <= ring; rz++) {
                for (int rx = -ring; rx <= ring; rx++) {
                    if (DistrictMap.ring(rx, rz) != ring) {
                        continue;
                    }
                    for (int[] side : new int[][]{{1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
                        UrbanDistrict a = DistrictMap.districtOf(world.seed, rx, rz);
                        UrbanDistrict b = DistrictMap.districtOf(world.seed, rx + side[0], rz + side[1]);
                        if (a != b) {
                            Vec3i[] walk = crossing(world.urban.regionPlan(rx, rz), side);
                            return new UrbanDistrictTourPlan(a, b, walk[0], walk[1], stops, wade);
                        }
                    }
                }
            }
        }
        throw new IllegalStateException("no district transition near the spawn");
    }

    /** Regions of a district within three of the spawn, nearest first. */
    private static List<DistrictPlan> regions(World world, UrbanDistrict district) {
        List<DistrictPlan> out = new ArrayList<>();
        for (int ring = 0; ring <= DistrictMap.ALL_DISTRICTS_RING; ring++) {
            for (int rz = -ring; rz <= ring; rz++) {
                for (int rx = -ring; rx <= ring; rx++) {
                    if (DistrictMap.ring(rx, rz) == ring && DistrictMap.districtOf(world.seed, rx, rz) == district) {
                        out.add(world.urban.regionPlan(rx, rz));
                    }
                }
            }
        }
        return out;
    }

    /** The first cell, in row order from the region's centre outward, carrying an overlay. */
    private static Vec3i overlay(DistrictPlan plan, UrbanOverlay overlay, UrbanSurface surface) {
        int cx = plan.originX() + 128, cz = plan.originZ() + 128;
        for (int r = 0; r < 128; r++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int x = cx - r; x <= cx + r; x++) {
                    if (Math.max(Math.abs(x - cx), Math.abs(z - cz)) != r || !plan.contains(x, z)) {
                        continue;
                    }
                    if (plan.overlayAt(x, z) == overlay && (surface == null || plan.surfaceAt(x, z) == surface)) {
                        return new Vec3i(x, FEET, z);
                    }
                }
            }
        }
        return null;
    }

    /**
     * Square-on to the north gateway of an open superblock (a plaza, fenced square or yard):
     * {@code outside} cells beyond its edge, looking six cells in through the gap.
     */
    private static Stop gate(DistrictPlan plan, SuperblockPlan.Kind kind, int outside, float height) {
        for (SuperblockPlan block : plan.superblocks()) {
            if (block.kind() != kind) {
                continue;
            }
            ParcelPlan.Rect b = block.bounds();
            int cx = (b.x0() + b.x1() + 1) / 2;
            int sz = b.z0() - outside;
            if (plan.contains(cx, sz) && walkable(plan, cx, sz)) {
                return new Stop(new Vec3i(cx, FEET, sz), new Vec3i(cx, FEET, b.z0() + 6), height);
            }
        }
        return null;
    }

    /** Beside the middle of a shop row's service alley. */
    private static Stop alley(DistrictPlan plan) {
        for (SuperblockPlan block : plan.superblocks()) {
            if (block.kind() == SuperblockPlan.Kind.SHOP_ROW) {
                ParcelPlan.Rect open = block.open();
                Stop stop = near(plan, new Vec3i((open.x0() + open.x1()) / 2, FEET, (open.z0() + open.z1()) / 2), 1.6f);
                if (stop != null) {
                    return stop;
                }
            }
        }
        return null;
    }

    /** Inside the cordon, five cells square-on to the middle of one of its checkpoints. */
    private static Stop cordon(DistrictPlan plan) {
        int last = StreetNetwork.REGION_SIZE - 1;
        // North, south, west and east rows with their inward normals.
        int[][] sides = {{0, 0, 1}, {1, 0, -1}, {2, 1, 0}, {3, -1, 0}};
        for (int[] side : sides) {
            int run = 0;
            for (int l = 1; l < last; l++) {
                int lx = side[0] < 2 ? l : side[0] == 2 ? 0 : last;
                int lz = side[0] < 2 ? (side[0] == 0 ? 0 : last) : l;
                boolean gap = plan.overlayAt(plan.originX() + lx, plan.originZ() + lz) == UrbanOverlay.CHECKPOINT;
                if (gap) {
                    run++;
                    continue;
                }
                if (run > 0) {
                    int mid = l - (run + 1) / 2;
                    int mx = plan.originX() + (side[0] < 2 ? mid : lx), mz = plan.originZ() + (side[0] < 2 ? lz : mid);
                    int sx = mx + side[1] * 5, sz = mz + side[2] * 5;
                    if (walkable(plan, sx, sz)) {
                        return new Stop(new Vec3i(sx, FEET, sz), new Vec3i(mx, FEET, mz), 1f);
                    }
                    run = 0;
                }
            }
        }
        return null;
    }

    private static boolean walkable(DistrictPlan plan, int x, int z) {
        UrbanOverlay o = plan.overlayAt(x, z);
        return plan.surfaceAt(x, z).publicWalkable && !o.blocksWalking && o != UrbanOverlay.STANDING_WATER;
    }

    private static Stop near(DistrictPlan plan, Vec3i subject, float height) {
        if (subject == null) {
            return null;
        }
        Vec3i stand = stand(plan, subject);
        return stand == null ? null : new Stop(stand, subject, height);
    }

    /**
     * A plain walkable cell three to six cells from the subject, inside the subject's region,
     * with no overlay and no parcel or fence between it and the subject.
     */
    private static Vec3i stand(DistrictPlan plan, Vec3i subject) {
        for (int r = REACH; r <= REACH + 3; r++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    int x = subject.x() + dx, z = subject.z() + dz;
                    if (!plan.contains(x, z) || !plan.surfaceAt(x, z).publicWalkable
                            || plan.overlayAt(x, z) != UrbanOverlay.NONE || !clearLine(plan, x, z, subject)) {
                        continue;
                    }
                    return new Vec3i(x, FEET, z);
                }
            }
        }
        return null;
    }

    private static boolean clearLine(DistrictPlan plan, int x, int z, Vec3i subject) {
        int steps = Math.max(Math.abs(subject.x() - x), Math.abs(subject.z() - z));
        for (int i = 1; i < steps; i++) {
            int px = x + Math.round((subject.x() - x) * (float) i / steps);
            int pz = z + Math.round((subject.z() - z) * (float) i / steps);
            if (!plan.contains(px, pz) || plan.surfaceAt(px, pz).parcel || plan.overlayAt(px, pz).blocksWalking) {
                return false;
            }
        }
        return true;
    }

    private static Vec3i neighbour(DistrictPlan plan, Vec3i cell, UrbanOverlay overlay) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            int x = cell.x() + d[0], z = cell.z() + d[1];
            if (plan.contains(x, z) && plan.overlayAt(x, z) == overlay) {
                return new Vec3i(x, FEET, z);
            }
        }
        return null;
    }

    /**
     * Start and end of a walk along the outer sidewalk of a local street that runs across the
     * shared edge: forty cells inside the first region to forty cells inside the second.
     */
    private static Vec3i[] crossing(DistrictPlan plan, int[] side) {
        int line = side[0] != 0 ? plan.eastWestLine(2) : plan.northSouthLine(2);
        int across = line - StreetNetwork.LOCAL_HALF_WIDTH;
        int edge = side[0] > 0 ? plan.originX() + StreetNetwork.REGION_SIZE
                : side[0] < 0 ? plan.originX()
                : side[1] > 0 ? plan.originZ() + StreetNetwork.REGION_SIZE : plan.originZ();
        int dir = side[0] + side[1];
        int start = edge - dir * 40 - (dir > 0 ? 0 : 1);
        int end = edge + dir * 40 - (dir > 0 ? 0 : 1);
        return side[0] != 0
                ? new Vec3i[]{new Vec3i(start, FEET, across), new Vec3i(end, FEET, across)}
                : new Vec3i[]{new Vec3i(across, FEET, start), new Vec3i(across, FEET, end)};
    }
}
