package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan.Family;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Frozen encounter catalogues. Catalogue one (generator 10) reserves two ground-floor slots in
 * selected residential buildings. Catalogue two (generator 11) keeps exactly that rule for its
 * walk-ups and adds guard reservations to the commercial and civic families: every clinic and
 * security post keeps its lobby occupied (the encounter cost of secured medicine and
 * ammunition), and one in four other specialist buildings keeps its main back room.
 *
 * <p>Catalogue three (generator 12, milestone 13) gives every planning region its district's
 * threat budget ({@link DistrictRules.Profile#threatMin} to {@link DistrictRules.Profile#threatMax}
 * anchors, DESIGN §4.3), two anchors per building. It spends the budget on unheld buildings
 * outside the starter superblock: clinics and security posts first, then the rest by a stable
 * hash. A walk-up gets catalogue one's room, a specialist building catalogue two's. The
 * starter superblock keeps catalogue one's slots, and they count against the spawn region's
 * budget. The threat itself is still the Veilbound: alien organisms are milestone 15.
 */
public final class EncounterAnchors {
    public record Anchor(long id, long building, Vec3i cell, boolean indoor) { }
    private EncounterAnchors() { }

    /** The catalogue a world's generator freezes; it is written beside the population codec. */
    public static int catalogue(World world) {
        if (world.urban == null) {
            return 1;
        }
        return world.urban.hasUnderground() ? 4 : world.urban.hasDistricts() ? 3 : world.urban.hasCivicCatalogue() ? 2 : 1;
    }

    public static boolean selected(World world, ParcelPlan parcel) {
        return parcel.use() == ParcelPlan.Use.RESIDENTIAL
                && (UrbanStarterSite.isNeighbour(parcel)
                || UrbanHash.range(0, 15, world.seed, 1, UrbanSalts.ENCOUNTERS, parcel.id(), 0, 0) == 0);
    }

    public static List<Anchor> forParcel(World world, ParcelPlan parcel) {
        int catalogue = catalogue(world);
        if (catalogue >= 3 && !UrbanStarterSite.isNeighbour(parcel)) {
            return budgeted(world, parcel);
        }
        if (catalogue == 2) {
            BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (!plan.family().walkUp()) { return civic(world, plan); }
        }
        if (!selected(world, parcel)) { return List.of(); }
        return walkUp(world.urban.buildingAt(parcel.x0(), parcel.z0()));
    }

    /** Catalogue one's slots: the street approach and the first ground-floor living room or kitchen. */
    private static List<Anchor> walkUp(BuildingPlan plan) {
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
        boolean guarded = guarded(plan.family());
        if (!guarded && UrbanHash.range(0, 3, world.seed, 2, UrbanSalts.CIVIC_ENCOUNTERS, plan.parcel().id(), 0, 0) != 0) {
            return List.of();
        }
        return specialist(plan);
    }

    private static boolean guarded(Family family) {
        return family == Family.CLINIC || family == Family.SECURITY;
    }

    private static List<Anchor> specialist(BuildingPlan plan) {
        int roomIndex = guarded(plan.family()) ? 0 : 6;
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

    /**
     * Catalogues three and four: a building inside its region's budget keeps its catalogue-one or
     * -two slots.
     */
    private static List<Anchor> budgeted(World world, ParcelPlan parcel) {
        DistrictPlan district = world.urban.regionPlan(UrbanIds.regionX(parcel.id()), UrbanIds.regionZ(parcel.id()));
        List<Integer> chosen = budget(world.seed, district);
        if (catalogue(world) == 4 && !forStation(world, district.rx, district.rz).isEmpty() && !chosen.isEmpty()) {
            // The station spends one building's share of the district budget first.
            chosen = chosen.subList(0, chosen.size() - 1);
        }
        if (!chosen.contains(parcel.index())) { return List.of(); }
        BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
        if (plan.variant() == BuildingPlan.Variant.OCCUPIED) { return List.of(); }
        return plan.family().walkUp() ? walkUp(plan) : specialist(plan);
    }

    /**
     * The parcel indices catalogue three occupies in a region: half its district's anchor
     * budget in buildings, less the spawn region's starter slots, chosen among unheld buildings
     * outside the starter superblock, clinics and security posts first. Pure.
     */
    public static List<Integer> budget(long seed, DistrictPlan district) {
        DistrictRules.Profile rules = DistrictRules.of(district.district);
        int anchors = UrbanHash.range(rules.threatMin(), rules.threatMax(), seed, UrbanRevision.LAYOUT_DISTRICTS,
                UrbanSalts.DISTRICT_THREATS, district.rx, district.rz, 0);
        int buildings = Math.max(1, anchors / 2);
        List<ParcelPlan> candidates = new ArrayList<>();
        for (ParcelPlan parcel : district.parcels()) {
            if (UrbanStarterSite.isNeighbour(parcel)) {
                if (parcel.use() == ParcelPlan.Use.RESIDENTIAL) { buildings--; }
                continue;
            }
            if (parcel.use() != ParcelPlan.Use.REFUGE
                    && DistrictCatalogue.expectedVariant(seed, district, parcel) != BuildingPlan.Variant.OCCUPIED) {
                candidates.add(parcel);
            }
        }
        candidates.sort(Comparator.comparing((ParcelPlan p) -> !guarded(DistrictCatalogue.family(seed, district, p)))
                .thenComparing(p -> UrbanHash.mix(seed, UrbanRevision.LAYOUT_DISTRICTS, UrbanSalts.DISTRICT_THREATS,
                        p.id(), 1, 0), Long::compareUnsigned));
        List<Integer> chosen = new ArrayList<>();
        for (int i = 0; i < candidates.size() && i < buildings; i++) {
            chosen.add(candidates.get(i).index());
        }
        return chosen;
    }

    /**
     * Catalogue four (generator 13, milestone 14): the station a region owns keeps two slots,
     * the street at the top of its stair and its platform, outside the spawn region. They are
     * part of the district's threat budget, which spends one building's share on them first.
     */
    public static List<Anchor> forStation(World world, int rx, int rz) {
        if (catalogue(world) != 4 || rx == 0 && rz == 0) { return List.of(); }
        TransitStation station = ownedStation(world, rx, rz);
        if (station == null) { return List.of(); }
        BuildingPlan plan = station.plan();
        BuildingPlan.Room platform = plan.floors().getFirst().rooms().getFirst();
        return List.of(new Anchor(UrbanIds.encounter(platform.id(), 0), plan.id(), plan.approaches().getFirst(), false),
                new Anchor(UrbanIds.encounter(platform.id(), 1), plan.id(), platform.anchor(), true));
    }

    /** The station of the one candidate segment a region owns, or null when that segment is absent. */
    public static TransitStation ownedStation(World world, int rx, int rz) {
        int ns = TransitNetwork.candidate(rx) ? rx : rx + 1, ew = TransitNetwork.candidate(rz) ? rz : rz + 1;
        boolean nsOwner = java.util.Arrays.equals(TransitNetwork.region(true, ns, rz,
                TransitNetwork.ownerPositive(true, rz)), new int[]{rx, rz});
        TransitNetwork.Segment s = nsOwner ? world.urban.segment(true, ns, rz) : world.urban.segment(false, ew, rx);
        return s.present() ? s.station() : null;
    }

    /** Resolves against the frozen plan, never accepts a merely well-shaped arbitrary id. */
    public static Anchor resolve(World world, long id) {
        UrbanIds.requireKind(id, UrbanIds.Kind.ENCOUNTER);
        if (UrbanIds.floorIndex(id) == -2 && UrbanIds.parcelIndex(id) % ParcelPlan.SLOTS_PER_SUPERBLOCK == TransitStation.ID_SLOT) {
            for (Anchor anchor : forStation(world, UrbanIds.regionX(id), UrbanIds.regionZ(id))) {
                if (anchor.id == id) { return anchor; }
            }
            throw new IllegalArgumentException("encounter has no planned station anchor");
        }
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
