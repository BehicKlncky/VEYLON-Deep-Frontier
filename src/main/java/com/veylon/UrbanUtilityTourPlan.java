package com.veylon;

import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.UrbanCrafting;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.Function;
import com.veylon.world.urban.BuildingPlan.Room;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.UrbanUtilities;
import com.veylon.world.urban.VehicleSites;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where the milestone-19 utility tour ({@code urban_utilities}) goes on a generator-18 world, read
 * from pure plans:
 * <ul>
 *   <li>the starter refuge, the first claimed refuge and home, and on its ground floor the
 *       workbench and the kitchen tap nearest it;</li>
 *   <li>the generator's cell, the kitchen's free floor cell farthest from the bench; a work lamp at
 *       the middle of the kitchen and one at the middle of the bedroom; the purifier's cell, the
 *       free facade cell beside the tap; and the cell each is put down from;</li>
 *   <li>the wreck nearest the spawn that did not burn, its tank cell and the road cell behind it;</li>
 *   <li>a courtyard cell out from the refuge's courtyard door, where a threat is staged.</li>
 * </ul>
 * Reads plans and recipes only; changes no block and loads no chunk.
 */
record UrbanUtilityTourPlan(BuildingPlan refuge, Fixture bench, Fixture tap, Vec3i generator, Vec3i generatorFrom,
                            Vec3i kitchenLamp, Vec3i kitchenLampFrom, Vec3i bedroomLamp, Vec3i bedroomLampFrom,
                            Vec3i purifier, Vec3i purifierFrom, VehicleSites.Wreck wreck, Vec3i tank, Vec3i atTank,
                            Vec3i threat) {
    /** Cells out from the courtyard door a threat is staged. */
    static final int THREAT_STEPS = 4;

    static UrbanUtilityTourPlan of(World world) {
        if (world.urban == null || !world.urban.hasUtilities()) {
            throw new IllegalStateException("urban_utilities needs generator 18 (VEYLON_GENERATOR=urban-dev)");
        }
        BuildingPlan refuge = UrbanStarterSite.of(world.urban).refuge();
        Fixture bench = groundFloor(refuge).stream().filter(f -> f.block() == BlockType.WORKBENCH).findFirst()
                .orElseThrow(() -> new IllegalStateException("the starter refuge has no workbench"));
        Room kitchen = room(refuge, bench.position(), Function.KITCHEN);
        Room bedroom = refuge.floors().stream().filter(f -> f.index() == 0).findFirst().orElseThrow().rooms().stream()
                .filter(r -> r.functions().contains(Function.BEDROOM)).findFirst()
                .orElseThrow(() -> new IllegalStateException("the starter refuge has no bedroom"));
        Fixture tap = groundFloor(refuge).stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.FURNITURE && f.block() == BlockType.RAIN_COLLECTOR
                        && kitchen.interior().contains(f.position().x(), f.position().z()))
                .min(Comparator.comparingInt((Fixture f) -> distSq(f.position(), bench.position())).thenComparingLong(Fixture::id))
                .orElseThrow(() -> new IllegalStateException("the starter refuge's kitchen has no tap"));
        int y = bench.position().y();
        List<Vec3i> taken = new ArrayList<>();
        for (Fixture f : refuge.fixtures()) {
            taken.add(f.position());
            if (f.target() != null) { taken.add(f.target()); }
        }
        Vec3i generator = free(refuge, kitchen, y, taken).stream()
                .max(Comparator.comparingInt((Vec3i c) -> distSq(c, bench.position())).thenComparingInt(c -> -c.x() - c.z()))
                .orElseThrow();
        // The purifier stands in the facade row beside the tap, where the kitchen's fittings stand.
        int facadeZ = tap.position().z(), facadeX = tap.position().x();
        Vec3i purifier = free(refuge, kitchen, y, taken).stream()
                .filter(c -> tap.target().x() == facadeX ? c.z() == facadeZ : c.x() == facadeX)
                .min(Comparator.comparingInt((Vec3i c) -> distSq(c, tap.position())).thenComparingInt(c -> c.x() + c.z()))
                .orElseThrow(() -> new IllegalStateException("no free cell beside the kitchen tap"));
        Vec3i purifierFrom = purifier.offset(2 * (tap.target().x() - tap.position().x()), 0,
                2 * (tap.target().z() - tap.position().z()));
        Vec3i kitchenLamp = middle(kitchen, y), bedroomLamp = middle(bedroom, y);
        VehicleSites.Wreck wreck = nearestIntact(world);
        Vec3i tank = wreck.cells().stream().filter(c -> wreck.isTank(c.x(), c.y(), c.z())).findFirst().orElseThrow();
        int back = wreck.frontLow() ? 1 : -1;
        Vec3i atTank = wreck.alongZ() ? tank.offset(0, 0, back) : tank.offset(back, 0, 0);
        Vec3i yard = refuge.approaches().stream().filter(a -> a.x() > refuge.parcel().x1() || a.x() < refuge.parcel().x0()
                        || a.z() > refuge.parcel().z1() || a.z() < refuge.parcel().z0())
                .max(Comparator.comparingInt(a -> distSq(a, refuge.approaches().getFirst()))).orElseThrow();
        ParcelPlan p = refuge.parcel();
        int ox = yard.x() > p.x1() ? 1 : yard.x() < p.x0() ? -1 : 0, oz = yard.z() > p.z1() ? 1 : yard.z() < p.z0() ? -1 : 0;
        Vec3i threat = yard.offset(THREAT_STEPS * ox, 0, THREAT_STEPS * oz);
        return new UrbanUtilityTourPlan(refuge, bench, tap, generator, toward(generator, kitchenLamp, 2),
                kitchenLamp, kitchenLamp.offset(0, 0, 2 * signToward(kitchenLamp.z(), generator.z())),
                bedroomLamp, bedroomLamp.offset(0, 0, 2 * signToward(bedroomLamp.z(), generator.z())),
                purifier, purifierFrom, wreck, tank, atTank, threat);
    }

    private static List<Fixture> groundFloor(BuildingPlan b) {
        return b.fixtures().stream().filter(f -> UrbanIds.floorIndex(f.id()) == 0).toList();
    }

    private static Room room(BuildingPlan b, Vec3i at, Function function) {
        for (BuildingPlan.Floor f : b.floors()) {
            if (f.index() != 0) { continue; }
            for (Room r : f.rooms()) {
                if (r.functions().contains(function) && r.interior().contains(at.x(), at.z())) { return r; }
            }
        }
        throw new IllegalStateException("no " + function + " room at " + at);
    }

    /** Floor cells of a room that its plan leaves open, with headroom, clear of every fixture and its lane. */
    private static List<Vec3i> free(BuildingPlan b, Room room, int y, List<Vec3i> taken) {
        List<Vec3i> out = new ArrayList<>();
        ParcelPlan.Rect r = room.interior();
        for (int x = r.x0(); x <= r.x1(); x++) {
            for (int z = r.z0(); z <= r.z1(); z++) {
                Vec3i c = new Vec3i(x, y, z);
                if (b.voxels().getBlock(x, y, z) == BlockType.AIR && b.voxels().getBlock(x, y + 1, z) == BlockType.AIR
                        && b.voxels().getBlock(x, y - 1, z).solid && !taken.contains(c)) {
                    out.add(c);
                }
            }
        }
        return out;
    }

    private static Vec3i middle(Room room, int y) {
        ParcelPlan.Rect r = room.interior();
        return new Vec3i((r.x0() + r.x1()) / 2, y, (r.z0() + r.z1()) / 2);
    }

    /** A cell {@code steps} from {@code from} toward {@code to}, along the longer axis. */
    private static Vec3i toward(Vec3i from, Vec3i to, int steps) {
        int dx = to.x() - from.x(), dz = to.z() - from.z();
        return Math.abs(dx) >= Math.abs(dz) ? from.offset(steps * Integer.signum(dx), 0, Integer.signum(dz))
                : from.offset(Integer.signum(dx), 0, steps * Integer.signum(dz));
    }

    private static int signToward(int from, int to) {
        return to >= from ? 1 : -1;
    }

    /** The wreck nearest the spawn, within a region of it, whose tank still holds fuel. */
    private static VehicleSites.Wreck nearestIntact(World world) {
        int[] spawn = world.urban.spawnColumn();
        VehicleSites.Wreck best = null;
        for (int rz = -1; rz <= 1; rz++) {
            for (int rx = -1; rx <= 1; rx++) {
                for (VehicleSites.Wreck w : world.urban.vehicles(rx, rz).wrecks()) {
                    if (w.burnt()) { continue; }
                    if (best == null || sq(w.x0() - spawn[0]) + sq(w.z0() - spawn[1])
                            < sq(best.x0() - spawn[0]) + sq(best.z0() - spawn[1])) {
                        best = w;
                    }
                }
            }
        }
        if (best == null) { throw new IllegalStateException("no wreck with fuel within a region of the spawn"); }
        return best;
    }

    /** What the tour builds at the bench: a generator, two work lamps and a purifier, by the utility recipes. */
    static Map<ItemType, Integer> materials() {
        Map<ItemType, Integer> need = new EnumMap<>(ItemType.class);
        add(need, recipe(ItemType.GENERATOR), 1);
        add(need, recipe(ItemType.WORK_LAMP), 2);
        add(need, recipe(ItemType.WATER_PURIFIER), 1);
        return need;
    }

    static Recipe recipe(ItemType result) {
        return UrbanCrafting.UTILITY_RECIPES.stream().filter(r -> r.result == result).findFirst().orElseThrow();
    }

    private static void add(Map<ItemType, Integer> need, Recipe r, int times) {
        r.ingredients.forEach((type, count) -> need.merge(type, count * times, Integer::sum));
    }

    private static long sq(long v) {
        return v * v;
    }

    private static int distSq(Vec3i a, Vec3i b) {
        int dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** True when every device of the tour is within the generator's reach, as the circuit requires. */
    boolean withinReach() {
        for (Vec3i c : List.of(kitchenLamp, bedroomLamp, purifier)) {
            if (c.distSq(generator.x() + .5, generator.y() + .5, generator.z() + .5) > UrbanUtilities.REACH * UrbanUtilities.REACH) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return "refuge=" + UrbanIds.format(refuge.id()) + " bench=" + bench.position() + " tap=" + tap.position()
                + " generator=" + generator + " lamps=" + kitchenLamp + "," + bedroomLamp + " purifier=" + purifier
                + " wreck=" + wreck.x0() + "," + wreck.z0() + " tank=" + tank + " atTank=" + atTank + " threat=" + threat;
    }
}
