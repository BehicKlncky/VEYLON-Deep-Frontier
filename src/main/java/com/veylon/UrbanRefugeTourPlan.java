package com.veylon;

import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.ToolKind;
import com.veylon.item.UrbanCrafting;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SalvageTable;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanStarterSite;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where the milestone-18 refuge tour ({@code urban_refuge}) goes on a generator-17 world, read from
 * pure plans:
 * <ul>
 *   <li>the abandoned apartment beside the starter refuge nearest the refuge's bed, and on its first
 *       floor the bed to claim, the cupboard nearest that bed for storage and, nearest the bed
 *       first, the furniture whose salvage covers a door brace and three plank barricades;</li>
 *   <li>its street door's two leaves, the first to brace and the second to bar, the street cells
 *       before them, two hall cells behind the braced leaf and a street cell before it where a
 *       threat is met; and its other outer doors, to bar;</li>
 *   <li>the camp nearest the spawn: the yard outside its way in and the bed nearest that way on a
 *       storey the camp holds.</li>
 * </ul>
 * Reads plans and recipes only; changes no block and loads no chunk.
 */
record UrbanRefugeTourPlan(BuildingPlan flat, Fixture bed, Fixture store, List<Fixture> pieces,
                           Fixture braced, Fixture barred, List<Fixture> rear, Vec3i street, Vec3i hallNear,
                           Vec3i hallFar, Vec3i threat, UrbanSurvivorTourPlan.Camp camp, Fixture campBed) {
    /** The storey of the flat the tour claims. */
    static final int STOREY = 1;
    /** What the tour builds from salvage: one door brace and this many plank barricades. */
    static final int BARRICADES = 3;
    /** How many cells out into the street a threat is met, from the braced leaf's outer cell. */
    static final int THREAT_STEPS = 3;

    UrbanRefugeTourPlan {
        pieces = List.copyOf(pieces);
        rear = List.copyOf(rear);
    }

    static UrbanRefugeTourPlan of(World world) {
        if (world.urban == null || !world.urban.hasRefuges()) {
            throw new IllegalStateException("urban_refuge needs generator 17 (VEYLON_GENERATOR=urban-dev)");
        }
        BuildingPlan flat = flat(world);
        Fixture bed = flat.fixtures().stream()
                .filter(f -> UrbanIds.floorIndex(f.id()) == STOREY && RefugeSystem.isBed(f.block()))
                .min(Comparator.comparingLong(Fixture::id)).orElseThrow();
        Fixture store = flat.fixtures().stream()
                .filter(f -> UrbanIds.floorIndex(f.id()) == STOREY && f.kind() == BuildingPlan.FixtureKind.CONTAINER)
                .min(Comparator.comparingInt((Fixture f) -> distSq(f.position(), bed.position())).thenComparingLong(Fixture::id))
                .orElseThrow();
        List<Fixture> pieces = pieces(world, flat, bed, store);
        Vec3i approach = flat.approaches().getFirst();
        List<Fixture> street = flat.fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR && f.target().y() == approach.y()
                        && Math.abs(f.target().x() - approach.x()) + Math.abs(f.target().z() - approach.z()) <= 1)
                .sorted(Comparator.comparingLong(Fixture::id)).toList();
        if (street.size() != 2) {
            throw new IllegalStateException("the flat's street door has " + street.size() + " leaves, not two");
        }
        ParcelPlan parcel = flat.parcel();
        List<Fixture> rear = flat.fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR && !street.contains(f) && UrbanIds.floorIndex(f.id()) == 0
                        && (f.position().x() == parcel.x0() || f.position().x() == parcel.x1()
                        || f.position().z() == parcel.z0() || f.position().z() == parcel.z1()))
                .sorted(Comparator.comparingLong(Fixture::id)).toList();
        Fixture braced = street.get(0);
        Vec3i p = braced.position(), t = braced.target();
        int inX = Integer.signum(p.x() - t.x()), inZ = Integer.signum(p.z() - t.z());
        Vec3i hallNear = p.offset(2 * inX, 0, 2 * inZ), hallFar = p.offset(4 * inX, 0, 4 * inZ);
        Vec3i threat = t.offset(-THREAT_STEPS * inX, 0, -THREAT_STEPS * inZ);
        Vec3i before = t.offset(-inX, 0, -inZ);
        UrbanSurvivorTourPlan.Camp camp = UrbanSurvivorTourPlan.nearestCamp(world);
        return new UrbanRefugeTourPlan(flat, bed, store, pieces, braced, street.get(1), rear, before, hallNear, hallFar,
                threat, camp, campBed(world, camp));
    }

    /** The abandoned three-storey apartment beside the starter refuge nearest the refuge's bed. */
    private static BuildingPlan flat(World world) {
        DistrictPlan district = world.urban.regionPlan(0, 0);
        Vec3i home = world.urban.refugeAnchor;
        BuildingPlan best = null;
        for (ParcelPlan parcel : district.parcels()) {
            if (!UrbanStarterSite.isNeighbour(parcel) || parcel.use() != ParcelPlan.Use.RESIDENTIAL) { continue; }
            BuildingPlan b = world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (b == null || b.family() != BuildingPlan.Family.APARTMENT || b.variant() == BuildingPlan.Variant.OCCUPIED
                    || b.storeys() <= STOREY || SurvivorSites.owner(world.urban, b.id()) != null) { continue; }
            if (best == null || centreDistSq(b, home) < centreDistSq(best, home)) { best = b; }
        }
        if (best == null) { throw new IllegalStateException("no abandoned apartment beside the starter refuge"); }
        return best;
    }

    /**
     * The first-floor furniture nearest the bed whose salvage, after its wear, covers what the tour
     * builds; never the bed or the storage cupboard.
     */
    private static List<Fixture> pieces(World world, BuildingPlan flat, Fixture bed, Fixture store) {
        Map<ItemType, Integer> need = materials();
        List<Fixture> candidates = new ArrayList<>();
        for (Fixture f : flat.fixtures()) {
            boolean furniture = f.kind() == BuildingPlan.FixtureKind.FURNITURE || f.kind() == BuildingPlan.FixtureKind.CONTAINER;
            if (UrbanIds.floorIndex(f.id()) == STOREY && furniture && f != bed && f != store && !RefugeSystem.isBed(f.block())
                    && f.block() != BlockType.WORKBENCH && !SalvageTable.yields(world, f.position(), f.block()).isEmpty()
                    && SalvageTable.refusal(world, f.position(), f.block(), ToolKind.NONE) == null) {
                candidates.add(f);
            }
        }
        candidates.sort(Comparator.comparingInt((Fixture f) -> distSq(f.position(), bed.position()))
                .thenComparingLong(Fixture::id));
        List<Fixture> chosen = new ArrayList<>();
        for (Fixture f : candidates) {
            if (covered(need)) { break; }
            chosen.add(f);
            for (ItemStack s : SalvageTable.yields(world, f.position(), f.block())) {
                need.computeIfPresent(s.type, (k, v) -> v - s.count);
            }
        }
        if (!covered(need)) { throw new IllegalStateException("the flat's first floor cannot be salvaged for " + materials()); }
        return chosen;
    }

    /** Planks and scrap for one door brace and {@value #BARRICADES} plank barricades, by the refuge recipes. */
    static Map<ItemType, Integer> materials() {
        Map<ItemType, Integer> need = new EnumMap<>(ItemType.class);
        add(need, recipe(ItemType.DOOR_BRACE), 1);
        add(need, recipe(ItemType.BARRICADE), BARRICADES);
        return need;
    }

    static Recipe recipe(ItemType result) {
        return UrbanCrafting.REFUGE_RECIPES.stream().filter(r -> r.result == result).findFirst().orElseThrow();
    }

    private static void add(Map<ItemType, Integer> need, Recipe r, int times) {
        r.ingredients.forEach((type, count) -> need.merge(type, count * times, Integer::sum));
    }

    private static boolean covered(Map<ItemType, Integer> need) {
        return need.values().stream().allMatch(v -> v <= 0);
    }

    /** The camp's planned bed nearest its way in, on the storey of that way: the camp holds it. */
    private static Fixture campBed(World world, UrbanSurvivorTourPlan.Camp camp) {
        BuildingPlan b = world.urban.buildingAt(camp.door().x(), camp.door().z());
        Vec3i in = camp.site().entry();
        return b.fixtures().stream()
                .filter(f -> RefugeSystem.isBed(f.block()) && f.position().y() == in.y())
                .min(Comparator.comparingInt((Fixture f) -> distSq(f.position(), in)).thenComparingLong(Fixture::id))
                .orElseThrow(() -> new IllegalStateException("the camp " + UrbanIds.format(camp.site().id()) + " has no bed"));
    }

    private static long centreDistSq(BuildingPlan b, Vec3i p) {
        long dx = (b.parcel().x0() + b.parcel().x1()) / 2 - p.x(), dz = (b.parcel().z0() + b.parcel().z1()) / 2 - p.z();
        return dx * dx + dz * dz;
    }

    private static int distSq(Vec3i a, Vec3i b) {
        int dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    @Override
    public String toString() {
        StringBuilder s = new StringBuilder("flat=" + UrbanIds.format(flat.id()) + " " + flat.variant()
                + " bed=" + bed.position() + " store=" + store.position() + " pieces=");
        for (Fixture f : pieces) { s.append(f.block()).append('@').append(f.position()).append(' '); }
        return s + "braced=" + braced.position() + " barred=" + barred.position() + " rear=" + rear.size()
                + " hall=" + hallNear + ".." + hallFar + " threat=" + threat + " camp=" + UrbanIds.format(camp.site().id())
                + " campBed=" + campBed.position();
    }
}
