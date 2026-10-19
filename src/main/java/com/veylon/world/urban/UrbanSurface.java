package com.veylon.world.urban;

/**
 * What the plan puts on one ground column of an urban world.
 *
 * <p>Every column of a planned region gets exactly one surface. The flags say
 * what kind of space it is:
 * <ul>
 *   <li>{@link #street}: part of the public street network — roadway, lane
 *       marking, crosswalk, sidewalk or boulevard median.</li>
 *   <li>{@link #route}: a reserved route no later building may obstruct — the
 *       street network plus alleys, courtyard passages and the walkways of
 *       courtyards and parks.</li>
 *   <li>{@link #publicWalkable}: open to walk on without crossing a reserved
 *       parcel. Everything but parcels and tree beds.</li>
 *   <li>{@link #parcel}: inside a reserved building parcel; still flat ground
 *       in layout revision 2, because buildings come later.</li>
 * </ul>
 *
 * <p>The codes are the plan's raster values and are not persisted; they are
 * explicit anyway so a diagnostic dump keeps its meaning if the order grows.
 * The block each surface stamps is chosen by {@link UrbanSurfaceMaterials}.
 */
public enum UrbanSurface {
    /** Not assigned. A finished plan has none; diagnostics count any that remain. */
    VOID(0, false, false, false, false),
    ROADWAY(1, true, true, true, false),
    /** Dashed centre line of a plain avenue. */
    LANE_MARK(2, true, true, true, false),
    /** A crosswalk stripe where a sidewalk band crosses a roadway. */
    CROSSWALK(3, true, true, true, false),
    SIDEWALK(4, true, true, true, false),
    /** Planted centre strip of a boulevard; flush, so it never blocks a crossing. */
    MEDIAN(5, true, true, true, false),
    /** Service alley through a commercial superblock. */
    ALLEY(6, false, true, true, false),
    /** Opening between two parcels that joins a courtyard to the street. */
    PASSAGE(7, false, true, true, false),
    /** The walkway ring inside a courtyard, onto which rear entrances open. */
    COURTYARD_PATH(8, false, true, true, false),
    /** Courtyard lawn. */
    COURTYARD(9, false, false, true, false),
    PARK_PATH(10, false, true, true, false),
    /** Park lawn. */
    PARK(11, false, false, true, false),
    /** Paved open square of a commercial district. */
    PLAZA(12, false, false, true, false),
    /** The cell a planned tree stands on; the trunk blocks it. */
    TREE_BED(13, false, false, false, false),
    /** Interior of a reserved building parcel. */
    LOT(14, false, false, false, true),
    /** The outline of a reserved building parcel. */
    LOT_EDGE(15, false, false, false, true),
    /** A reserved entrance on a parcel outline, two cells wide. */
    ENTRANCE(16, false, false, false, true),
    /** A reserved footprint for the building's stair core (vertical route). */
    STAIR_CORE(17, false, false, false, true),
    /**
     * The flat street datum without a plan: every column of layout revision 1,
     * and any column beyond the range urban ids can name.
     */
    DATUM(18, false, false, true, false),
    /**
     * Open water of a flooded district's canal basin (layout revision 9): a two-deep channel
     * below the datum. Swimmable, so it is not a reserved route and nobody needs it.
     */
    CANAL(19, false, false, false, false),
    /** A causeway across a canal basin at the street datum, joining its four streets. */
    BRIDGE(20, false, true, true, false),
    /** The paved open yard of an industrial superblock (layout revision 9). */
    YARD(21, false, false, true, false);

    private static final UrbanSurface[] BY_CODE = new UrbanSurface[22];

    static {
        for (UrbanSurface surface : values()) {
            BY_CODE[surface.code] = surface;
        }
    }

    /** Raster value; explicit, never the ordinal. */
    public final byte code;
    public final boolean street;
    public final boolean route;
    public final boolean publicWalkable;
    public final boolean parcel;

    UrbanSurface(int code, boolean street, boolean route, boolean publicWalkable, boolean parcel) {
        this.code = (byte) code;
        this.street = street;
        this.route = route;
        this.publicWalkable = publicWalkable;
        this.parcel = parcel;
    }

    /**
     * @throws IllegalArgumentException for a value no surface carries
     */
    public static UrbanSurface fromCode(byte code) {
        if (code < 0 || code >= BY_CODE.length || BY_CODE[code] == null) {
            throw new IllegalArgumentException("unknown urban surface code " + code);
        }
        return BY_CODE[code];
    }
}
