package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import java.util.List;

/** Frozen catalogue one. Geometry is inherited unchanged from generator 9. */
public final class EncounterAnchors {
    public record Anchor(long id, long building, Vec3i cell, boolean indoor) { }
    private EncounterAnchors() { }

    public static boolean selected(World world, ParcelPlan parcel) {
        return parcel.use() == ParcelPlan.Use.RESIDENTIAL
                && (UrbanStarterSite.isNeighbour(parcel)
                || UrbanHash.range(0, 15, world.seed, 1, UrbanSalts.ENCOUNTERS, parcel.id(), 0, 0) == 0);
    }

    public static List<Anchor> forParcel(World world, ParcelPlan parcel) {
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
