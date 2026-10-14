package com.veylon.world.urban;

import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Pure parcel-to-building planning, with a finite validated repair sequence. */
public final class BuildingPlanner {
    public static final int MAX_ATTEMPTS = 3;
    public record Result(BuildingPlan plan, int attempts, List<String> repairs,
                         AccessibilityValidator.Report validation) {
        public Result { repairs = List.copyOf(repairs); }
    }

    private BuildingPlanner() { }

    /**
     * Recomputable without world state, content rolls, chunks or random streams. Revision five
     * chooses families from the parcel's district, so it needs {@link #plan(long, int, ParcelPlan, DistrictPlan)}.
     */
    public static Result plan(long seed, int revision, ParcelPlan parcel) {
        if (revision == UrbanRevision.BUILDING_TEMPLATE_CIVIC) {
            throw new IllegalArgumentException("template revision five plans a parcel within its district");
        }
        Family family = parcel.use() == ParcelPlan.Use.COMMERCIAL ? Family.SHOP : Family.APARTMENT;
        int storeys = family == Family.SHOP ? Math.min(2, parcel.maxStoreys()) : Math.min(5, parcel.maxStoreys());
        // Revision three refines only treads; preserve revision-two occupation and basements.
        int selectionRevision = revision == UrbanRevision.BUILDING_TEMPLATE_SHAPED_STAIRS
                || revision == UrbanRevision.BUILDING_TEMPLATE_STARTER
                ? UrbanRevision.BUILDING_TEMPLATE_FURNISHED : revision;
        Variant variant = Variant.values()[UrbanHash.range(0, 3, seed, selectionRevision,
                UrbanSalts.BUILDING_VARIANT, parcel.id(), 0, 0)];
        boolean basement = family == Family.APARTMENT && UrbanHash.range(0, 1, seed, selectionRevision,
                UrbanSalts.BUILDING_BASEMENT, parcel.id(), 0, 0) == 1;
        if (revision == UrbanRevision.BUILDING_TEMPLATE_STARTER && UrbanStarterSite.isNeighbour(parcel)) {
            variant = Variant.INTACT;
            basement = family == Family.APARTMENT;
        }
        BuildingPlan candidate = BuildingTemplates.propose(parcel, revision, family, variant, storeys, basement, false);
        return repair(candidate);
    }

    /**
     * Any supported revision. Under revision five the catalogue picks the parcel's family; a
     * walk-up there is exactly the revision-four plan, starter superblock included.
     */
    public static Result plan(long seed, int revision, ParcelPlan parcel, DistrictPlan district) {
        if (revision != UrbanRevision.BUILDING_TEMPLATE_CIVIC) { return plan(seed, revision, parcel); }
        Family family = CivicCatalogue.family(seed, district, parcel);
        if (family.walkUp()) { return plan(seed, UrbanRevision.BUILDING_TEMPLATE_STARTER, parcel); }
        int storeys = CivicTemplates.envelope(family).storeys(parcel);
        BuildingPlan candidate = BuildingTemplates.propose(parcel, revision, family,
                CivicCatalogue.variant(seed, parcel), storeys, CivicCatalogue.basement(seed, family, parcel), false);
        return repair(candidate);
    }

    /** Candidate, intact alternate, then minimal rooms on the same footprint and vertical envelope. */
    public static Result repair(BuildingPlan candidate) {
        List<String> repairs = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            AccessibilityValidator.Report report = AccessibilityValidator.inspect(candidate);
            if (report.ok()) { return new Result(candidate, attempt, repairs, report); }
            repairs.add("attempt=" + attempt + " " + identity(candidate) + " " + report.summary());
            if (attempt < MAX_ATTEMPTS) {
                candidate = BuildingTemplates.propose(candidate.parcel(), candidate.templateRevision(),
                        candidate.family(), Variant.INTACT, candidate.storeys(), candidate.basement(), attempt == 2);
            }
        }
        throw new IllegalStateException("no valid building in finite fallback: " + repairs);
    }

    /**
     * What an agent needs to find a failing template without scanning the city: its name and
     * revision, family and variant, the parcel's canonical id, frontage, size and storeys.
     */
    public static String identity(BuildingPlan plan) {
        ParcelPlan p = plan.parcel();
        return String.format(Locale.ROOT, "template=%s revision=%d family=%s variant=%s parcel=%s facing=%s"
                        + " size=%dx%d storeys=%d basement=%b", plan.template(), plan.templateRevision(),
                plan.family(), plan.variant(), UrbanIds.format(p.id()), p.frontage(), p.width(), p.depth(),
                plan.storeys(), plan.basement());
    }
}
