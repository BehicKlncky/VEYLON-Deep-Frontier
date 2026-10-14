package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import java.util.List;

/**
 * Frozen encounter catalogues. Catalogue one (generator 10) reserves two ground-floor slots in
 * selected residential buildings. Catalogue two (generator 11) keeps exactly that rule for its
 * walk-ups and adds guard reservations to the commercial and civic families: every clinic and
 * security post keeps its lobby occupied (the encounter cost of secured medicine and
 * ammunition), and one in four other specialist buildings keeps its main back room.
 */
public final class EncounterAnchors {
    public record Anchor(long id, long building, Vec3i cell, boolean indoor) { }
    private EncounterAnchors() { }

    /** The catalogue a world's generator freezes; it is written beside the population codec. */
    public static int catalogue(World world) {
        return world.urban != null && world.urban.hasCivicCatalogue() ? 2 : 1;
    }

    public static boolean selected(World world, ParcelPlan parcel) {
        return parcel.use() == ParcelPlan.Use.RESIDENTIAL
                && (UrbanStarterSite.isNeighbour(parcel)
                || UrbanHash.range(0, 15, world.seed, 1, UrbanSalts.ENCOUNTERS, parcel.id(), 0, 0) == 0);
    }

    public static List<Anchor> forParcel(World world, ParcelPlan parcel) {
        if (catalogue(world) == 2) {
            BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (!plan.family().walkUp()) { return civic(world, plan); }
        }
        if (!selected(world, parcel)) { return List.of(); }
        BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
        if (plan.variant() == BuildingPlan.Variant.OCCUPIED) { return List.of(); }
        for (var floor : plan.floors()) {
            if (floor.index() != 0) { continue; }
            for (var room : floor.rooms()) {
                if (!room.functions().contains(BuildingPlan.Function.LIVING)
                        && !room.functions().contains(BuildingPlan.Function.KITCHEN)) { continue; }
                return List.of(new Anchor(UrbanIds.encounter(room.id(), 0), plan.id(),
                                plan.approaches().getFirst(), false),
                        new Anchor(UrbanIds.encounter(room.id(), 1), plan.id(), room.anchor(), true));
            }
        }
        return List.of();
    }

    /**
     * Catalogue two's specialist reservations: the street approach plus the lobby of a clinic
     * or security post, or the first back room of a selected grocer, school, office or
     * warehouse. Held (occupied) buildings have none, as in catalogue one.
     */
    private static List<Anchor> civic(World world, BuildingPlan plan) {
        if (plan.variant() == BuildingPlan.Variant.OCCUPIED) { return List.of(); }
        boolean guarded = plan.family() == BuildingPlan.Family.CLINIC || plan.family() == BuildingPlan.Family.SECURITY;
        if (!guarded && UrbanHash.range(0, 3, world.seed, 2, UrbanSalts.CIVIC_ENCOUNTERS, plan.parcel().id(), 0, 0) != 0) {
            return List.of();
        }
        int roomIndex = guarded ? 0 : 6;
        for (var floor : plan.floors()) {
            if (floor.index() != 0) { continue; }
            for (var room : floor.rooms()) {
                if (UrbanIds.roomIndex(room.id()) != roomIndex) { continue; }
                return List.of(new Anchor(UrbanIds.encounter(room.id(), 0), plan.id(), plan.approaches().getFirst(), false),
                        new Anchor(UrbanIds.encounter(room.id(), 1), plan.id(), room.anchor(), true));
            }
        }
        return List.of();
    }

    /** Resolves against the frozen plan, never accepts a merely well-shaped arbitrary id. */
    public static Anchor resolve(World world, long id) {
        UrbanIds.requireKind(id, UrbanIds.Kind.ENCOUNTER);
        if (UrbanIds.floorIndex(id) != 0 || UrbanIds.slotIndex(id) > 1) {
            throw new IllegalArgumentException("invalid encounter floor or slot");
        }
        for (var parcel : world.urban.regionPlan(UrbanIds.regionX(id), UrbanIds.regionZ(id)).parcels()) {
            if (parcel.index() != UrbanIds.parcelIndex(id)) { continue; }
            for (Anchor anchor : forParcel(world, parcel)) {
                if (anchor.id == id) { return anchor; }
            }
        }
        throw new IllegalArgumentException("encounter has no planned anchor");
    }
}
