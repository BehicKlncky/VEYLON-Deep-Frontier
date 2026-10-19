package com.veylon.world.urban;

import com.veylon.world.Biome;

/**
 * What each of the six districts means to the player under layout revision 9 (milestone 13).
 *
 * <p>The district stays a separate urban layer over the climate {@link Biome} (D-08): it maps
 * to one existing biome, so temperature, weather, sky, ambience and plant consumers keep their
 * meaning and no biome ordinal changes. The rest of a district's identity lives where it acts:
 * superblocks, uses, storeys and overlays in {@link CityPlanner}, the building mix in the
 * district catalogue, and loot through the frozen content-three district biases.
 *
 * <ul>
 *   <li>{@link Profile#climate}: the biome every column of the district reports.</li>
 *   <li>{@link Profile#visibility}: a presentation-only scale on fog distance (mist, haze).</li>
 *   <li>{@link Profile#threatMin} to {@link Profile#threatMax}: encounter anchors per planning
 *       region (DESIGN §4.3), two per selected building.</li>
 *   <li>{@link Profile#arrival}: the line the player reads on entering it.</li>
 * </ul>
 */
public final class DistrictRules {

    /** One district's rules. */
    public record Profile(UrbanDistrict district, String displayName, Biome climate, float visibility,
                          int threatMin, int threatMax, String arrival) {
    }

    private static final Profile[] PROFILES = {
        new Profile(UrbanDistrict.RESIDENTIAL, "Reclaimed Residential", Biome.MEADOW, 1f, 2, 6,
                "Reclaimed residential blocks: kitchens, bathrooms and corner shops, overgrown"
                        + " courtyards, few infected."),
        new Profile(UrbanDistrict.COMMERCIAL, "Commercial Downtown", Biome.MEADOW, 1f, 6, 12,
                "Downtown: grocers, offices and a police post along long, open avenues. More"
                        + " infected, and they see you coming."),
        new Profile(UrbanDistrict.INDUSTRIAL, "Industrial Yards", Biome.SCRUBLAND, 0.85f, 4, 10,
                "Industrial yards: tools and repair stock in the warehouses. Dry heat, chemical"
                        + " spills and powder kegs; carry water."),
        new Profile(UrbanDistrict.FLOODED, "Flooded Quarter", Biome.MARSH, 0.7f, 2, 6,
                "Flooded streets: wading soaks and chills you, and the canals run deep. Quiet,"
                        + " seldom-looted buildings."),
        new Profile(UrbanDistrict.CIVIC, "Civic Precinct", Biome.MEADOW, 1f, 4, 10,
                "Civic precinct: clinics, schools and security posts around fenced squares."
                        + " Guarded medicine."),
        new Profile(UrbanDistrict.QUARANTINE, "Quarantine Zone", Biome.ROCKY_HIGHLANDS, 0.6f, 6, 12,
                "Quarantine cordon: contaminated ground, cold and the densest infected. Field"
                        + " clinics and armouries.")
    };

    private DistrictRules() {
    }

    /** The rules of a district. */
    public static Profile of(UrbanDistrict district) {
        Profile profile = PROFILES[district.ordinal()];
        if (profile.district() != district) {
            throw new IllegalStateException("district rules out of order at " + district);
        }
        return profile;
    }
}
