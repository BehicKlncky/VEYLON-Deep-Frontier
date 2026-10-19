package com.veylon.world.urban;

import java.util.Locale;

/**
 * The six urban district identities (decision D-07), one per planning region.
 *
 * <p>District identity is a separate layer over the climate {@code Biome},
 * which keeps its ordinals and meaning (D-08). Each identity carries an
 * explicit numeric code and a text id; neither is the ordinal, so the order
 * below may grow without changing what any saved or logged value means. Codes
 * are append-only.
 *
 * <p>Layout revision 2 plans only {@link #RESIDENTIAL} and {@link #COMMERCIAL}
 * regions. Layout revision 9 (milestone 13) plans all six through
 * {@link DistrictMap}.
 */
public enum UrbanDistrict {
    /** Reclaimed residential: walk-ups around courtyards, corner shops; the starter district. */
    RESIDENTIAL(1, "residential", true),
    /** Commercial downtown: shop rows and offices along service alleys, plazas. */
    COMMERCIAL(2, "commercial", true),
    /** Industrial yards: warehouses, workshops, fenced service roads. */
    INDUSTRIAL(3, "industrial", false),
    /** Flooded neighbourhoods: standing water, canals, raised walkways. */
    FLOODED(4, "flooded", false),
    /** Civic and medical precinct: clinic, school, security site, squares. */
    CIVIC(5, "civic", false),
    /** Quarantine and alien growth: cordoned blocks behind checkpoints. */
    QUARANTINE(6, "quarantine", false);

    /** Stable numeric code; never the ordinal. */
    public final int code;
    /** Stable text id for logs, diagnostics and later sections. */
    public final String id;
    /** True when the street-network layout (revision 2) can plan this district. */
    public final boolean plannedInStreetLayout;

    UrbanDistrict(int code, String id, boolean plannedInStreetLayout) {
        this.code = code;
        this.id = id;
        this.plannedInStreetLayout = plannedInStreetLayout;
    }

    /**
     * @throws IllegalArgumentException for a code no district carries
     */
    public static UrbanDistrict fromCode(int code) {
        for (UrbanDistrict district : values()) {
            if (district.code == code) {
                return district;
            }
        }
        throw new IllegalArgumentException("unknown urban district code " + code);
    }

    /**
     * @throws IllegalArgumentException for an id no district carries
     */
    public static UrbanDistrict fromId(String id) {
        String normalized = id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
        for (UrbanDistrict district : values()) {
            if (district.id.equals(normalized)) {
                return district;
            }
        }
        throw new IllegalArgumentException("unknown urban district id '" + id + "'");
    }
}
