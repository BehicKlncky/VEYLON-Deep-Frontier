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
    /**
     * One planned body: its stable encounter id, the building (or ecology site) it belongs to, the
     * cell it appears at, whether that cell is indoors, its species and, for a creature, where it
     * may go. Catalogues one to four plan only Veilbound, with no habitat.
     */
    public record Anchor(long id, long building, Vec3i cell, boolean indoor, UrbanSpecies species, Habitat habitat) {
        public Anchor(long id, long building, Vec3i cell, boolean indoor) {
            this(id, building, cell, indoor, UrbanSpecies.VEILBOUND, null);
        }

        Anchor as(UrbanSpecies other) {
            return new Anchor(id, building, cell, indoor, other, habitat);
        }
    }
    private EncounterAnchors() { }

    /** The catalogue a world's generator freezes; it is written beside the population codec. */
    public static int catalogue(World world) {
        if (world.urban == null) {
            return 1;
        }
        return world.urban.hasEconomy() ? 6 : world.urban.hasEcology() ? 5 : world.urban.hasUnderground() ? 4
                : world.urban.hasDistricts() ? 3 : world.urban.hasCivicCatalogue() ? 2 : 1;
    }

    /** Catalogues four to six keep a region's station slots; earlier ones have no stations. */
    public static boolean hasStations(World world) {
        int catalogue = catalogue(world);
        return catalogue == 4 || catalogue == 5 || catalogue == 6;
    }

    /**
     * Catalogues five and six plan creatures beside the infected: the quarantine's Veilmaws, the
     * Breachers and city wildlife (milestone 15). Catalogue six is catalogue five with the starter
     * block's pressure eased (milestone 23).
     */
    public static boolean hasCreatures(World world) {
        int catalogue = catalogue(world);
        return catalogue == 5 || catalogue == 6;
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
        if (catalogue == 6 && UrbanStarterSite.isNeighbour(parcel) && parcel.index() != starterThreat(world)) {
            return List.of();
        }
        return walkUp(world.urban.buildingAt(parcel.x0(), parcel.z0()));
    }

    /**
     * Catalogue six (layout seventeen, milestone 23): the one starter apartment that keeps catalogue
     * one's two slots, the one whose street door lies farthest from the refuge's courtyard door (ties by
     * parcel index), so the first session meets the one or two bodies DESIGN §4.4 asks for instead of up to
     * eight. Pure in the plans.
     */
    static int starterThreat(World world) {
        UrbanStarterSite site = UrbanStarterSite.of(world.urban);
        Vec3i door = site.refuge().approaches().getLast();
        int best = -1;
        long bestDistance = -1;
        for (ParcelPlan parcel : world.urban.regionPlan(0, 0).parcels()) {
            if (!UrbanStarterSite.isNeighbour(parcel) || parcel.use() != ParcelPlan.Use.RESIDENTIAL) { continue; }
            BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (plan == null || plan.variant() == BuildingPlan.Variant.OCCUPIED) { continue; }
            Vec3i street = plan.approaches().getFirst();
            long dx = street.x() - door.x(), dz = street.z() - door.z(), distance = dx * dx + dz * dz;
            if (distance > bestDistance) {
                best = parcel.index();
                bestDistance = distance;
            }
        }
        return best;
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
        // Every parcel of a region asks for the same budget, and the director asks a parcel at a time:
        // the region's choice is planned once and cached beside its plan (milestone 24).
        List<Integer> chosen = world.urban.threatBudget(district.rx, district.rz, () -> chosen(world, district));
        if (!chosen.contains(parcel.index())) { return List.of(); }
        boolean ecology = hasCreatures(world);
        BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
        if (plan.variant() == BuildingPlan.Variant.OCCUPIED) { return List.of(); }
        List<Anchor> anchors = plan.family().walkUp() ? walkUp(plan) : specialist(plan);
        if (ecology && breacher(world.seed, district, plan)) {
            List<Anchor> out = new ArrayList<>();
            for (Anchor a : anchors) {
                out.add(a.indoor() ? a : a.as(UrbanSpecies.BREACHER));
            }
            return out;
        }
        return anchors;
    }

    /** The parcel indices a region's budget keeps for its buildings, after its station and growth sites. Pure. */
    private static List<Integer> chosen(World world, DistrictPlan district) {
        // Layout thirteen: a compound's claimed parcels are planned held (or a back way unheld).
        List<Integer> chosen = budget(world.seed, district, world.urban.hasStrongholds()
                ? world.urban.routes(district.rx, district.rz).claims() : CompoundClaims.NONE);
        if (hasStations(world) && !forStation(world, district.rx, district.rz).isEmpty() && !chosen.isEmpty()) {
            // The station spends one building's share of the district budget first.
            chosen = chosen.subList(0, chosen.size() - 1);
        }
        if (hasCreatures(world) && district.district == UrbanDistrict.QUARANTINE && !chosen.isEmpty()) {
            // Catalogue five: the quarantine's Veilmaws are part of its threat budget, one building's
            // share (two anchors) for every two growth sites, rounded up, keeping one building.
            int sites = world.urban.ecology(district.rx, district.rz).growthSites().size();
            chosen = chosen.subList(0, chosen.size() - Math.min(chosen.size() - 1, (sites + 1) / 2));
        }
        return chosen;
    }

    /**
     * The parcel indices catalogue three occupies in a region: half its district's anchor
     * budget in buildings, less the spawn region's starter slots, chosen among unheld buildings
     * outside the starter superblock, clinics and security posts first. Pure.
     */
    public static List<Integer> budget(long seed, DistrictPlan district) {
        return budget(seed, district, CompoundClaims.NONE);
    }

    /**
     * As {@link #budget(long, DistrictPlan)}, with layout thirteen's compound claims on each
     * parcel's variant: a compound's held buildings are never candidates.
     */
    public static List<Integer> budget(long seed, DistrictPlan district, CompoundClaims claims) {
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
            if (parcel.use() != ParcelPlan.Use.REFUGE && claims.variant(parcel,
                    DistrictCatalogue.expectedVariant(seed, district, parcel)) != BuildingPlan.Variant.OCCUPIED) {
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
        if (!hasStations(world) || rx == 0 && rz == 0) { return List.of(); }
        TransitStation station = ownedStation(world, rx, rz);
        if (station == null) { return List.of(); }
        BuildingPlan plan = station.plan();
        BuildingPlan.Room platform = plan.floors().getFirst().rooms().getFirst();
        return List.of(new Anchor(UrbanIds.encounter(platform.id(), 0), plan.id(), plan.approaches().getFirst(), false),
                new Anchor(UrbanIds.encounter(platform.id(), 1), plan.id(), platform.anchor(), true));
    }

    /**
     * Catalogue five (milestone 15): one in three budgeted buildings of the industrial yards, the
     * civic precinct and the quarantine keeps a Breacher at its street door instead of a
     * Veilbound; its indoor slot stays a Veilbound. Never in the starter area, which keeps
     * catalogue one's slots.
     */
    static boolean breacher(long seed, DistrictPlan district, BuildingPlan plan) {
        return (district.district == UrbanDistrict.INDUSTRIAL || district.district == UrbanDistrict.CIVIC
                || district.district == UrbanDistrict.QUARANTINE)
                && UrbanHash.range(0, 2, seed, UrbanRevision.LAYOUT_ECOLOGY, UrbanSalts.BREACHER, plan.id(), 0, 0) == 0;
    }

    /**
     * Catalogue five's living slots of one superblock (milestone 15): its growth site's Veilmaw,
     * its wildlife ({@link EcologySites}) and, for one courtyard block in three outside the
     * quarantine, a flock of Skitterwings over the roof of its tallest walk-up of three storeys or
     * more. Every slot belongs to the superblock's ecology site id. Empty under every other
     * catalogue.
     */
    public static List<Anchor> forEcology(World world, int rx, int rz, int superblock) {
        if (!hasCreatures(world) || !CityPlanner.isPlannable(rx, rz)) { return List.of(); }
        DistrictPlan district = world.urban.regionPlan(rx, rz);
        long site = UrbanIds.building(UrbanIds.parcel(district.districtId,
                superblock * ParcelPlan.SLOTS_PER_SUPERBLOCK + EcologySites.ID_SLOT), 0);
        List<Anchor> out = new ArrayList<>();
        for (EcologySites.Slot s : world.urban.ecology(rx, rz).slots(superblock)) {
            out.add(new Anchor(s.encounterId(), site, s.cell(), false, s.species(), s.habitat()));
        }
        roofFlock(world, district, superblock, site, out);
        return out;
    }

    private static void roofFlock(World world, DistrictPlan district, int superblock, long site, List<Anchor> out) {
        SuperblockPlan sb = district.superblock(superblock);
        if (sb.kind() != SuperblockPlan.Kind.COURTYARD_HOUSING || district.district == UrbanDistrict.QUARANTINE
                || district.rx == 0 && district.rz == 0 && superblock == CityPlanner.STARTER_BLOCK
                || UrbanHash.unit(world.seed, UrbanRevision.LAYOUT_ECOLOGY, UrbanSalts.WILDLIFE_ROOF,
                district.rx, district.rz, superblock) >= 1.0 / 3) {
            return;
        }
        ParcelPlan tallest = null;
        for (ParcelPlan p : district.parcels()) {
            if (p.superblock() == superblock && p.use() == ParcelPlan.Use.RESIDENTIAL
                    && (tallest == null || p.maxStoreys() > tallest.maxStoreys())) {
                tallest = p;
            }
        }
        if (tallest == null) { return; }
        BuildingPlan plan = world.urban.buildingAt(tallest.x0(), tallest.z0());
        if (plan == null || !plan.family().walkUp() || plan.storeys() < 3) { return; }
        int roof = plan.roofY();
        int cx = (tallest.x0() + tallest.x1()) / 2, cz = (tallest.z0() + tallest.z1()) / 2;
        Habitat air = Habitat.area(tallest.x0(), tallest.z0(), tallest.x1(), tallest.z1(), roof + 2, roof + 10);
        for (int i = 0; i < EcologySites.FLOCK; i++) {
            out.add(new Anchor(EcologySites.encounterId(district, superblock, EcologySites.ROOM_ROOF, i), site,
                    new Vec3i(cx - 1 + 2 * i, roof + 5, cz), false, UrbanSpecies.SKITTERWING, air));
        }
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
        if (EcologySites.isEcologyId(id)) {
            int superblock = UrbanIds.parcelIndex(id) / ParcelPlan.SLOTS_PER_SUPERBLOCK;
            for (Anchor anchor : forEcology(world, UrbanIds.regionX(id), UrbanIds.regionZ(id), superblock)) {
                if (anchor.id == id) { return anchor; }
            }
            throw new IllegalArgumentException("encounter has no planned ecology anchor");
        }
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
