package com.veylon.world.urban;

import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;

import java.util.List;

/**
 * Which family each parcel carries under template revision five, and the per-building
 * choices around it (milestone 12, D-98). Pure: a function of the seed, the frozen district
 * plan and stable parcel ids, with its own salts, so neither visit order nor cache eviction
 * can change a building.
 *
 * <ul>
 *   <li>The starter superblock keeps its template-four refuge, corner shop and apartments,
 *       so the frozen slice route is the same on generators 10 and 11.</li>
 *   <li>A service parcel is a warehouse with a workshop.</li>
 *   <li>A commercial parcel is a grocery or convenience store (most often), a clinic or
 *       pharmacy (one in five), or, where its envelope reaches three storeys, an office block;
 *       downtown leans to offices.</li>
 *   <li>Each region turns one residential parcel into a school and another into a security
 *       post, chosen by hash among parcels the family fits.</li>
 * </ul>
 *
 * <p>A family whose envelope does not fit the parcel falls back along a finite chain that ends
 * in the walk-up catalogue, which fits every parcel.
 */
public final class CivicCatalogue {
    /** Every commercial and civic family, in declaration order. */
    public static final List<Family> FAMILIES = List.of(Family.GROCERY, Family.CLINIC, Family.SCHOOL,
            Family.OFFICE, Family.SECURITY, Family.WAREHOUSE);
    /** Original colony names painted on frontage signs; presentation only. */
    private static final String[] NAMES = {"Halvard", "Meridian", "Corvane", "Tessaly", "Brightwater",
        "Lumen", "Kestrel", "Ashgrove", "Northlight", "Varrow", "Selden", "Orrin", "Calder", "Wrenfield",
        "Quillon", "Dunmore"};
    private static final int REVISION = UrbanRevision.BUILDING_TEMPLATE_CIVIC;

    private CivicCatalogue() { }

    /** The family a parcel carries; walk-up families mean the frozen template-four plan. */
    public static Family family(long seed, DistrictPlan district, ParcelPlan parcel) {
        if (UrbanStarterSite.isNeighbour(parcel) || parcel.use() == ParcelPlan.Use.REFUGE) {
            return walkUp(parcel);
        }
        Family wanted = switch (parcel.use()) {
            case SERVICE -> Family.WAREHOUSE;
            case COMMERCIAL -> commercial(seed, district, parcel);
            case RESIDENTIAL -> parcel.equals(site(seed, district, Family.SCHOOL)) ? Family.SCHOOL
                    : parcel.equals(site(seed, district, Family.SECURITY)) ? Family.SECURITY : Family.APARTMENT;
            case REFUGE -> Family.APARTMENT;
        };
        while (!wanted.walkUp() && !CivicTemplates.envelope(wanted).fits(parcel)) {
            wanted = fallback(wanted, parcel);
        }
        return wanted;
    }

    /**
     * The next, smaller family to try; every chain ends at the parcel's walk-up. Specialist
     * families fall back to a grocer, so a misfit never multiplies dispensaries or armouries.
     */
    static Family fallback(Family family, ParcelPlan parcel) {
        return switch (family) {
            case SCHOOL -> Family.OFFICE;
            case OFFICE, SECURITY, WAREHOUSE, CLINIC -> Family.GROCERY;
            case GROCERY, APARTMENT, SHOP -> walkUp(parcel);
        };
    }

    private static Family walkUp(ParcelPlan parcel) {
        return parcel.use() == ParcelPlan.Use.COMMERCIAL ? Family.SHOP : Family.APARTMENT;
    }

    private static Family commercial(long seed, DistrictPlan district, ParcelPlan parcel) {
        int roll = UrbanHash.range(0, 99, seed, REVISION, UrbanSalts.CIVIC_FAMILY, parcel.id(), 0, 0);
        if (district.district == UrbanDistrict.COMMERCIAL) {
            return roll < 45 ? Family.OFFICE : roll < 80 ? Family.GROCERY : Family.CLINIC;
        }
        return roll < 60 ? Family.GROCERY : roll < 80 ? Family.CLINIC : Family.OFFICE;
    }

    /**
     * The region's school or security-post parcel: the residential parcel outside the starter
     * superblock with the lowest hash among those the family fits, or null when none does.
     * The security post never takes the school's parcel.
     */
    public static ParcelPlan site(long seed, DistrictPlan district, Family family) {
        if (family != Family.SCHOOL && family != Family.SECURITY) {
            throw new IllegalArgumentException(family + " has no regional site");
        }
        ParcelPlan school = best(seed, district, Family.SCHOOL, null);
        return family == Family.SCHOOL ? school : best(seed, district, Family.SECURITY, school);
    }

    private static ParcelPlan best(long seed, DistrictPlan district, Family family, ParcelPlan excluded) {
        ParcelPlan best = null;
        long bestKey = 0;
        for (ParcelPlan p : district.parcels()) {
            if (p.use() != ParcelPlan.Use.RESIDENTIAL || UrbanStarterSite.isNeighbour(p) || p.equals(excluded)
                    || !CivicTemplates.envelope(family).fits(p)) {
                continue;
            }
            long key = UrbanHash.mix(seed, REVISION, UrbanSalts.CIVIC_SITE, p.id(), family == Family.SCHOOL ? 0 : 1, 0);
            if (best == null || Long.compareUnsigned(key, bestKey) < 0) {
                best = p;
                bestKey = key;
            }
        }
        return best;
    }

    /** Intact 35 %, damaged 25 %, picked clean 25 %, partially occupied 15 % (initial values). */
    public static Variant variant(long seed, ParcelPlan parcel) {
        int roll = UrbanHash.range(0, 99, seed, REVISION, UrbanSalts.CIVIC_VARIANT, parcel.id(), 0, 0);
        return roll < 35 ? Variant.INTACT : roll < 60 ? Variant.DAMAGED
                : roll < 85 ? Variant.PICKED_CLEAN : Variant.OCCUPIED;
    }

    /** Schools and office blocks may keep a first basement for plant rooms and archives. */
    public static boolean basement(long seed, Family family, ParcelPlan parcel) {
        return CivicTemplates.envelope(family).basements()
                && UrbanHash.range(0, 1, seed, REVISION, UrbanSalts.CIVIC_BASEMENT, parcel.id(), 0, 0) == 1;
    }

    /** The words on a building's frontage sign, or null for a walk-up, which has none. */
    public static String signText(long seed, BuildingPlan plan) {
        if (plan.family().walkUp()) { return null; }
        String name = NAMES[UrbanHash.range(0, NAMES.length - 1, seed, REVISION, UrbanSalts.CIVIC_NAME, plan.id(), 0, 0)];
        return switch (plan.family()) {
            case GROCERY -> name + (plan.template().startsWith("convenience") ? " Corner Market" : " Grocers");
            case CLINIC -> name + " Clinic & Pharmacy";
            case SCHOOL -> name + " Colony School";
            case OFFICE -> name + " House offices";
            case SECURITY -> name + " Security Post";
            case WAREHOUSE -> name + " Depot & Workshop";
            case APARTMENT, SHOP -> null;
        };
    }
}
