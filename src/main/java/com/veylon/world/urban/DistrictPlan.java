package com.veylon.world.urban;

import java.util.List;

/**
 * The plan of one planning region: its district identity, street lattice,
 * superblocks, reserved parcels, planned trees and, for region (0, 0), the
 * starter refuge and spawn anchor.
 *
 * <p>Built by {@link CityPlanner} as a pure function of (seed, layout revision,
 * rx, rz) and immutable afterwards, so it can be dropped from a cache and
 * rebuilt identically. The plan rasterises every column of the region to one
 * {@link UrbanSurface} and, inside parcels, to the parcel's index; chunk
 * stamping and the ground and parcel queries read those rasters, and nothing
 * outside the region is ever read or written.
 */
public final class DistrictPlan {

    /** Value of {@link #parcelIndexAt} outside every parcel. */
    public static final int NO_PARCEL = -1;

    /** A planned tree: a trunk on a {@link UrbanSurface#TREE_BED} cell. */
    public record Tree(int x, int z, int trunkHeight) {
        /** Canopy reach from the trunk, in blocks. */
        public static final int CANOPY_RADIUS = 2;
    }

    public final long seed;
    public final int layoutRevision;
    public final int rx;
    public final int rz;
    public final UrbanDistrict district;
    /** Packed {@link UrbanIds.Kind#DISTRICT} id of this region. */
    public final long districtId;
    private final int[] northSouthLines;
    private final int[] eastWestLines;
    private final List<SuperblockPlan> superblocks;
    private final List<ParcelPlan> parcels;
    private final ParcelPlan[] parcelsByIndex;
    private final List<Tree> trees;
    private final byte[] surface;
    private final byte[] parcelIndex;
    private final ParcelPlan refuge;
    private final int[] spawnAnchor;
    private final int overlapConflicts;
    private final int fallbackSuperblocks;

    DistrictPlan(long seed, int layoutRevision, int rx, int rz, UrbanDistrict district,
                 int[] northSouthLines, int[] eastWestLines, List<SuperblockPlan> superblocks,
                 List<ParcelPlan> parcels, List<Tree> trees, byte[] surface, byte[] parcelIndex,
                 ParcelPlan refuge, int[] spawnAnchor, int overlapConflicts, int fallbackSuperblocks) {
        this.seed = seed;
        this.layoutRevision = layoutRevision;
        this.rx = rx;
        this.rz = rz;
        this.district = district;
        this.districtId = UrbanIds.district(rx, rz);
        this.northSouthLines = northSouthLines.clone();
        this.eastWestLines = eastWestLines.clone();
        this.superblocks = List.copyOf(superblocks);
        this.parcels = List.copyOf(parcels);
        this.parcelsByIndex = new ParcelPlan[UrbanIds.MAX_PARCEL + 1];
        for (ParcelPlan parcel : this.parcels) {
            parcelsByIndex[parcel.index()] = parcel;
        }
        this.trees = List.copyOf(trees);
        this.surface = surface;
        this.parcelIndex = parcelIndex;
        this.refuge = refuge;
        this.spawnAnchor = spawnAnchor == null ? null : spawnAnchor.clone();
        this.overlapConflicts = overlapConflicts;
        this.fallbackSuperblocks = fallbackSuperblocks;
    }

    public int originX() {
        return StreetNetwork.regionOrigin(rx);
    }

    public int originZ() {
        return StreetNetwork.regionOrigin(rz);
    }

    public boolean contains(int x, int z) {
        return StreetNetwork.regionOf(x) == rx && StreetNetwork.regionOf(z) == rz;
    }

    /**
     * The surface of a column of this region.
     *
     * @throws IllegalArgumentException for a column of another region
     */
    public UrbanSurface surfaceAt(int x, int z) {
        return UrbanSurface.fromCode(surface[cell(x, z)]);
    }

    /** The index of the parcel covering a column of this region, or {@link #NO_PARCEL}. */
    public int parcelIndexAt(int x, int z) {
        return parcelIndex[cell(x, z)];
    }

    /** The parcel covering a column of this region, or null. */
    public ParcelPlan parcelAt(int x, int z) {
        int index = parcelIndexAt(x, z);
        return index == NO_PARCEL ? null : parcelsByIndex[index];
    }

    /** The parcel with an index, or null when the region has none there. */
    public ParcelPlan parcel(int index) {
        return index < 0 || index >= parcelsByIndex.length ? null : parcelsByIndex[index];
    }

    /** Centre lines of the region's three north-south local streets (x coordinates). */
    public int northSouthLine(int k) {
        return northSouthLines[k - 1];
    }

    /** Centre lines of the region's three east-west local streets (z coordinates). */
    public int eastWestLine(int k) {
        return eastWestLines[k - 1];
    }

    public List<SuperblockPlan> superblocks() {
        return superblocks;
    }

    public SuperblockPlan superblock(int index) {
        return superblocks.get(index);
    }

    /** Every reserved parcel, by ascending index. */
    public List<ParcelPlan> parcels() {
        return parcels;
    }

    public List<Tree> trees() {
        return trees;
    }

    /** The starter refuge site; only region (0, 0) has one. */
    public ParcelPlan refuge() {
        return refuge;
    }

    /** {x, z} of the safe spawn column in front of the refuge door, or null outside region (0, 0). */
    public int[] spawnAnchor() {
        return spawnAnchor == null ? null : spawnAnchor.clone();
    }

    /** Cells the planner refused to overwrite because a higher-priority surface held them. */
    public int overlapConflicts() {
        return overlapConflicts;
    }

    /** Superblocks whose layout fell back to open space because no valid subdivision fitted. */
    public int fallbackSuperblocks() {
        return fallbackSuperblocks;
    }

    private int cell(int x, int z) {
        int lx = x - originX();
        int lz = z - originZ();
        if (lx < 0 || lx >= StreetNetwork.REGION_SIZE || lz < 0 || lz >= StreetNetwork.REGION_SIZE) {
            throw new IllegalArgumentException("column " + x + "," + z + " is outside region "
                    + rx + "," + rz);
        }
        return lz * StreetNetwork.REGION_SIZE + lx;
    }
}
