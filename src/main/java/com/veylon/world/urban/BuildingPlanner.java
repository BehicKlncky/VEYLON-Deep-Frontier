package com.veylon.world.urban;

import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;

import java.util.ArrayList;
import java.util.List;

/** Pure parcel-to-building planning, with a finite validated repair sequence. */
public final class BuildingPlanner {
    public static final int MAX_ATTEMPTS = 3;
    public record Result(BuildingPlan plan, int attempts, List<String> repairs,
                         AccessibilityValidator.Report validation) {
        public Result { repairs = List.copyOf(repairs); }
    }

    private BuildingPlanner() { }

    /** Recomputable without world state, content rolls, chunks or random streams. */
    public static Result plan(long seed, int revision, ParcelPlan parcel) {
        Family family = parcel.use() == ParcelPlan.Use.COMMERCIAL ? Family.SHOP : Family.APARTMENT;
        int storeys = family == Family.SHOP ? Math.min(2, parcel.maxStoreys()) : Math.min(5, parcel.maxStoreys());
        Variant variant = Variant.values()[UrbanHash.range(0, 3, seed, revision,
                UrbanSalts.BUILDING_VARIANT, parcel.id(), 0, 0)];
        boolean basement = family == Family.APARTMENT && UrbanHash.range(0, 1, seed, revision,
                UrbanSalts.BUILDING_BASEMENT, parcel.id(), 0, 0) == 1;
        BuildingPlan candidate = BuildingTemplates.propose(parcel, revision, family, variant, storeys, basement, false);
        return repair(candidate);
    }

    /** Candidate, intact alternate, then minimal rooms on the same footprint and vertical envelope. */
    public static Result repair(BuildingPlan candidate) {
        List<String> repairs = new ArrayList<>();
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            AccessibilityValidator.Report report = AccessibilityValidator.inspect(candidate);
            if (report.ok()) { return new Result(candidate, attempt, repairs, report); }
            repairs.add("attempt=" + attempt + " " + report.summary());
            if (attempt < MAX_ATTEMPTS) {
                candidate = BuildingTemplates.propose(candidate.parcel(), candidate.templateRevision(),
                        candidate.family(), Variant.INTACT, candidate.storeys(), candidate.basement(), attempt == 2);
            }
        }
        throw new IllegalStateException("no valid building in finite fallback: " + repairs);
    }
}
