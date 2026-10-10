package com.veylon.world.urban;

/**
 * One street-bounded superblock of a district plan.
 *
 * @param index 0..15, {@code j * 4 + i}
 * @param i column within the region, west to east
 * @param j row within the region, north to south
 * @param kind how the superblock is subdivided
 * @param bounds the buildable area inside its street bands
 * @param open the open space inside it: the courtyard of courtyard housing,
 *        the whole block of a park or plaza, the service alley of a shop row
 */
public record SuperblockPlan(int index, int i, int j, Kind kind,
                             ParcelPlan.Rect bounds, ParcelPlan.Rect open) {

    /** How a superblock is subdivided. */
    public enum Kind {
        /** A ring of six parcels around a courtyard, reached through two passages. */
        COURTYARD_HOUSING,
        /** Two rows of shop parcels back to back along a through service alley. */
        SHOP_ROW,
        /** Lawn, crossing paths and trees; at most one park or plaza per region. */
        PARK,
        /** A paved square with planted trees; commercial districts only. */
        PLAZA
    }

    public int width() {
        return bounds.x1() - bounds.x0() + 1;
    }

    public int depth() {
        return bounds.z1() - bounds.z0() + 1;
    }

    /** True for kinds that carry building parcels. */
    public boolean hasParcels() {
        return kind == Kind.COURTYARD_HOUSING || kind == Kind.SHOP_ROW;
    }
}
