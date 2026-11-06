package com.veylon.world.urban;

/**
 * What a district adds on top of one ground column of layout revision 9 (milestone 13).
 *
 * <p>Overlays live in their own raster beside the {@link UrbanSurface} raster, so the street
 * lattice underneath stays exactly the one every region agrees on at its seams. They are
 * bounded by construction: one value per column, planned from (seed, layout, region) like the
 * rest of the plan, never placed within two cells of a parcel (outside every building's
 * validated volume and its one-cell approach halo), never on an entrance, a courtyard
 * walkway, a passage, an alley or a causeway, and never in the starter superblock.
 *
 * <p>{@link #blocksWalking} overlays are solid where they stand; the plan diagnostics prove
 * every walkable cell still reaches the streets around them. Codes are raster values, not
 * persisted, and explicit so a diagnostic dump keeps its meaning if the order grows.
 */
public enum UrbanOverlay {
    NONE(0, false),
    /** One block of standing water over a flooded street or courtyard lawn: wading, wet, slow. */
    STANDING_WATER(1, false),
    /** A raised plank walk across a flooded street where its crosswalk was; one step up. */
    DUCKBOARD(2, false),
    /** Two-high sheet-metal hoarding: a quarantine cordon, a yard fence or a square's barrier. */
    FENCE(3, true),
    /** The painted gap in a cordon or barrier through which a street or path passes. */
    CHECKPOINT(4, false),
    /** Contaminated ground: walkable, but standing on it risks sickness in Survival. */
    CONTAMINATED(5, false),
    /** Grass breaking through paving: flush and walkable. */
    OVERGROWN(6, false),
    /** Tall grass or a bush on a lawn; not solid. */
    PLANTS(7, false),
    /** A powder keg left in an industrial yard: an obstacle, salvage and an explosive hazard. */
    KEG(8, true);

    private static final UrbanOverlay[] BY_CODE = new UrbanOverlay[9];

    static {
        for (UrbanOverlay overlay : values()) {
            BY_CODE[overlay.code] = overlay;
        }
    }

    /** Raster value; explicit, never the ordinal. */
    public final byte code;
    /** True when the overlay is solid at the walking height and must be walked around. */
    public final boolean blocksWalking;

    UrbanOverlay(int code, boolean blocksWalking) {
        this.code = (byte) code;
        this.blocksWalking = blocksWalking;
    }

    /**
     * @throws IllegalArgumentException for a value no overlay carries
     */
    public static UrbanOverlay fromCode(byte code) {
        if (code < 0 || code >= BY_CODE.length || BY_CODE[code] == null) {
            throw new IllegalArgumentException("unknown urban overlay code " + code);
        }
        return BY_CODE[code];
    }
}
