package com.veylon.world.urban;

import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;

import java.util.ArrayList;
import java.util.List;

/**
 * Which building each parcel of a six-district city carries: building template revision 6
 * (milestone 13). It is a catalogue, not new geometry. Every building is a proven plan,
 * a template-four walk-up or a template-five commercial or civic family, and only the choice of
 * family and of abandonment variant follows the district. Pure: a function of the seed, the
 * district plan and stable parcel ids, with its own salts.
 *
 * <ul>
 *   <li><b>Reclaimed residential:</b> walk-ups; corner shops are grocers, clinics or offices as
 *       in template five; one school per region.</li>
 *   <li><b>Commercial downtown:</b> offices, grocers and clinics as in template five; one
 *       security post per region (the downtown police post).</li>
 *   <li><b>Industrial yards:</b> depot lots are warehouses; other lots warehouses, canteen
 *       grocers and depot offices; workers' walk-ups; one security post.</li>
 *   <li><b>Flooded neighbourhoods:</b> low walk-ups, grocers and clinics; rarely picked clean
 *       and almost never held, because looters and survivors avoided the water.</li>
 *   <li><b>Civic precinct:</b> clinics and municipal offices on the rows, and two schools, two
 *       security posts, two clinics and two office blocks among the courtyard lots.</li>
 *   <li><b>Quarantine:</b> field clinics, checkpoint security posts and cordoned walk-ups,
 *       mostly damaged or stripped and never held.</li>
 * </ul>
 *
 * <p>The starter superblock and the refuge keep their template-four plans, and residential and
 * commercial walk-ups keep exactly their template-four plans too, so the slice neighbourhood
 * and the familiar districts are unchanged. A family that does not fit its parcel falls back
 * along template five's chain, ending at the walk-up, which fits every parcel.
 */
public final class DistrictCatalogue {
    private static final int REVISION = UrbanRevision.BUILDING_TEMPLATE_DISTRICTS;

    private DistrictCatalogue() { }

    /** The family a parcel carries; walk-up families mean a template-four plan. */
    public static Family family(long seed, DistrictPlan district, ParcelPlan parcel) {
        if (UrbanStarterSite.isNeighbour(parcel) || parcel.use() == ParcelPlan.Use.REFUGE) {
            return walkUp(parcel);
        }
        Family wanted = switch (parcel.use()) {
            case SERVICE -> Family.WAREHOUSE;
            case COMMERCIAL -> commercial(seed, district.district, parcel);
            case RESIDENTIAL -> site(seed, district, parcel);
            case REFUGE -> Family.APARTMENT;
        };
        while (!wanted.walkUp() && !CivicTemplates.envelope(wanted).fits(parcel)) {
            wanted = CivicCatalogue.fallback(wanted, parcel);
        }
        return wanted;
    }

    private static Family walkUp(ParcelPlan parcel) {
        return parcel.use() == ParcelPlan.Use.COMMERCIAL ? Family.SHOP : Family.APARTMENT;
    }

    private static Family commercial(long seed, UrbanDistrict district, ParcelPlan parcel) {
        int roll = UrbanHash.range(0, 99, seed, REVISION, UrbanSalts.DISTRICT_FAMILY, parcel.id(), 0, 0);
        return switch (district) {
            case RESIDENTIAL -> roll < 60 ? Family.GROCERY : roll < 80 ? Family.CLINIC : Family.OFFICE;
            case COMMERCIAL -> roll < 45 ? Family.OFFICE : roll < 80 ? Family.GROCERY : Family.CLINIC;
            case INDUSTRIAL -> roll < 50 ? Family.WAREHOUSE : roll < 80 ? Family.GROCERY : Family.OFFICE;
            case FLOODED -> roll < 70 ? Family.GROCERY : Family.CLINIC;
            case CIVIC -> roll < 45 ? Family.CLINIC : roll < 85 ? Family.OFFICE : Family.GROCERY;
            case QUARANTINE -> roll < 60 ? Family.CLINIC : roll < 85 ? Family.SECURITY : Family.GROCERY;
        };
    }

    /** The specialist sites each district places on its residential lots, in claiming order. */
    static List<Family> sites(UrbanDistrict district) {
        return switch (district) {
            case RESIDENTIAL -> List.of(Family.SCHOOL);
            case COMMERCIAL, INDUSTRIAL -> List.of(Family.SECURITY);
            case FLOODED -> List.of();
            case CIVIC -> List.of(Family.SCHOOL, Family.SECURITY, Family.CLINIC, Family.OFFICE,
                    Family.SCHOOL, Family.SECURITY, Family.CLINIC, Family.OFFICE);
            case QUARANTINE -> List.of(Family.SECURITY, Family.SECURITY, Family.CLINIC);
        };
    }

    /**
     * A residential lot's family: a district site when it is one, otherwise a walk-up. Each site
     * takes, in order, the residential lot outside the starter superblock with the lowest hash
     * among those its family fits and no earlier site took.
     */
    private static Family site(long seed, DistrictPlan district, ParcelPlan parcel) {
        List<Family> sites = sites(district.district);
        List<ParcelPlan> taken = new ArrayList<>();
        for (int i = 0; i < sites.size(); i++) {
            ParcelPlan best = best(seed, district, sites.get(i), i, taken);
            if (best == null) {
                continue;
            }
            if (best.equals(parcel)) {
                return sites.get(i);
            }
            taken.add(best);
        }
        return Family.APARTMENT;
    }

    private static ParcelPlan best(long seed, DistrictPlan district, Family family, int order, List<ParcelPlan> taken) {
        ParcelPlan best = null;
        long bestKey = 0;
        for (ParcelPlan p : district.parcels()) {
            if (p.use() != ParcelPlan.Use.RESIDENTIAL || UrbanStarterSite.isNeighbour(p) || taken.contains(p)
                    || !CivicTemplates.envelope(family).fits(p)) {
                continue;
            }
            long key = UrbanHash.mix(seed, REVISION, UrbanSalts.DISTRICT_SITE, p.id(), order, 0);
            if (best == null || Long.compareUnsigned(key, bestKey) < 0) {
                best = p;
                bestKey = key;
            }
        }
        return best;
    }

    /**
     * The abandonment variant of a commercial or civic building. Residential and commercial
     * districts keep template five's mix; the others weight it by what happened there.
     */
    public static Variant variant(long seed, DistrictPlan district, ParcelPlan parcel) {
        int[] weights = weights(district.district);
        if (weights == null) {
            return CivicCatalogue.variant(seed, parcel);
        }
        return roll(seed, parcel, weights);
    }

    /**
     * The variant of a walk-up outside the residential and commercial districts, or null where
     * the walk-up keeps its template-four variant (and so its exact template-four plan).
     */
    public static Variant walkUpVariant(long seed, DistrictPlan district, ParcelPlan parcel) {
        int[] weights = weights(district.district);
        return weights == null || UrbanStarterSite.isNeighbour(parcel) ? null : roll(seed, parcel, weights);
    }

    /** Intact, damaged, picked clean and occupied percentages; null for template five's own mix. */
    static int[] weights(UrbanDistrict district) {
        return switch (district) {
            case RESIDENTIAL, COMMERCIAL -> null;
            case INDUSTRIAL -> new int[]{30, 35, 20, 15};
            case FLOODED -> new int[]{40, 45, 10, 5};
            case CIVIC -> new int[]{35, 25, 25, 15};
            case QUARANTINE -> new int[]{20, 50, 30, 0};
        };
    }

    private static Variant roll(long seed, ParcelPlan parcel, int[] weights) {
        int roll = UrbanHash.range(0, 99, seed, REVISION, UrbanSalts.DISTRICT_VARIANT, parcel.id(), 0, 0);
        Variant[] variants = Variant.values();
        for (int i = 0; i < weights.length; i++) {
            if (roll < weights[i]) {
                return variants[i];
            }
            roll -= weights[i];
        }
        throw new IllegalStateException("variant weights do not sum to 100");
    }
}
