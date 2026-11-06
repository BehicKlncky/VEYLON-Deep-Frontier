package com.veylon.world.urban;

import java.util.List;

/**
 * One reserved building parcel of a district plan.
 *
 * <p>A parcel is a rectangle of 12 to 32 blocks per side (decision D-06)
 * fronting a street. Layout revision 2 reserves it and stamps it flat: a stone
 * outline around a dirt lot, with its entrances and its stair-core footprint
 * marked. The building that will stand on it comes from package 05 under a
 * later generator version; until then the parcel is a promise about where a
 * building may go and which cells it must keep open, not a building.
 *
 * <p>Its index is {@code superblock * 8 + slot}: stable however many parcels the
 * other superblocks of the region hold, and within the seven bits
 * {@link UrbanIds} gives a parcel.
 *
 * @param index 0..127, {@code superblock * SLOTS_PER_SUPERBLOCK + slot}
 * @param id the packed {@link UrbanIds.Kind#PARCEL} id
 * @param superblock 0..15, row-major from the region's north-west corner
 * @param use what the parcel is reserved for
 * @param x0 west edge, inclusive (world block coordinate)
 * @param z0 north edge, inclusive
 * @param x1 east edge, inclusive
 * @param z1 south edge, inclusive
 * @param frontage the side that faces the street
 * @param entrances the primary street entrance first, then the rear entrance
 *        onto a courtyard or alley
 * @param core reserved stair-core footprint, inside the outline
 * @param maxStoreys storey envelope: the tallest building the parcel may carry
 */
public record ParcelPlan(int index, long id, int superblock, Use use,
                         int x0, int z0, int x1, int z1,
                         Side frontage, List<Entrance> entrances, Rect core, int maxStoreys) {

    /** Parcel slots per superblock in the index; a superblock holds at most six parcels. */
    public static final int SLOTS_PER_SUPERBLOCK = 8;
    public static final int MIN_SIDE = 12;
    public static final int MAX_SIDE = 32;
    /** Reserved stair core: four cells along the frontage, six deep (rule A3 leaves room for a switchback). */
    public static final int CORE_WIDTH = 4;
    public static final int CORE_DEPTH = 6;
    /** Entrances are two cells wide (rule A11). */
    public static final int ENTRANCE_WIDTH = 2;

    /** What a parcel is reserved for. The ids are stable text for later sections. */
    public enum Use {
        RESIDENTIAL("residential"),
        /** A shop, including a corner shop in a residential district. */
        COMMERCIAL("commercial"),
        /** Utility or service reservation for later infrastructure. */
        SERVICE("service"),
        /** The starter refuge site of region (0, 0). */
        REFUGE("refuge");

        public final String id;

        Use(String id) {
            this.id = id;
        }
    }

    /** A compass side; north is towards negative z. */
    public enum Side {
        NORTH(0, -1), SOUTH(0, 1), WEST(-1, 0), EAST(1, 0);

        public final int dx;
        public final int dz;

        Side(int dx, int dz) {
            this.dx = dx;
            this.dz = dz;
        }

        public Side opposite() {
            return switch (this) {
                case NORTH -> SOUTH;
                case SOUTH -> NORTH;
                case WEST -> EAST;
                case EAST -> WEST;
            };
        }
    }

    /** An inclusive block rectangle. */
    public record Rect(int x0, int z0, int x1, int z1) {
        public boolean contains(int x, int z) {
            return x >= x0 && x <= x1 && z >= z0 && z <= z1;
        }
    }

    /**
     * A reserved two-cell entrance on the parcel outline. {@code (x, z)} and
     * {@code (x + along.dx, z + along.dz)} are its cells; stepping
     * {@code facing} from either leaves the parcel onto open ground.
     */
    public record Entrance(int x, int z, Side facing, boolean primary) {
        /** The second cell of the entrance. */
        public int x2() {
            return x + (facing.dx == 0 ? 1 : 0);
        }

        public int z2() {
            return z + (facing.dz == 0 ? 1 : 0);
        }

        /** The open cell in front of the first entrance cell. */
        public int outsideX() {
            return x + facing.dx;
        }

        public int outsideZ() {
            return z + facing.dz;
        }
    }

    public int width() {
        return x1 - x0 + 1;
    }

    public int depth() {
        return z1 - z0 + 1;
    }

    public boolean contains(int x, int z) {
        return x >= x0 && x <= x1 && z >= z0 && z <= z1;
    }

    /** True on the outline ring of the parcel. */
    public boolean onOutline(int x, int z) {
        return contains(x, z) && (x == x0 || x == x1 || z == z0 || z == z1);
    }

    public int slot() {
        return index % SLOTS_PER_SUPERBLOCK;
    }

    public Entrance primaryEntrance() {
        return entrances.getFirst();
    }

    /** The slab y of the parcel's storey envelope roof; an envelope, not a built roof. */
    public int plannedRoofY() {
        return UrbanDatum.slabY(maxStoreys);
    }
}
