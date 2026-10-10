package com.veylon.world.urban;

import com.veylon.world.BlockType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;

/** Read-only floor diagrams and stable geometry/reservation fingerprints for review and regression pins. */
public final class BuildingPlanDiagnostics {
    private BuildingPlanDiagnostics() { }

    public static long fingerprint(BuildingPlan p) {
        String metadata = p.id() + ":" + p.templateRevision() + ":" + p.template() + ":" + p.family()
                + ":" + p.variant() + ":" + p.parcel() + ":" + p.storeys() + ":" + p.basement()
                + ":" + p.floors() + ":" + p.fixtures() + ":" + p.approaches() + ":" + p.stairs();
        long hash = p.voxels().fingerprint();
        for (byte b : metadata.getBytes(StandardCharsets.UTF_8)) { hash = (hash ^ (b & 255)) * 0x100000001b3L; }
        return hash;
    }

    /** World north at the top. Digits are tread heights above the selected floor's slab. */
    public static String floor(BuildingPlan p, int floor) {
        int slab = UrbanDatum.slabY(floor);
        if (p.floors().stream().noneMatch(f -> f.index() == floor)) {
            throw new IllegalArgumentException("floor is not in the building");
        }
        StringBuilder out = new StringBuilder();
        out.append(p.template()).append(" revision=").append(p.templateRevision()).append(" floor=")
                .append(floor).append(" slab=").append(slab).append(" facing=").append(p.parcel().frontage())
                .append(" variant=").append(p.variant()).append('\n');
        for (int z = p.parcel().z0(); z <= p.parcel().z1(); z++) {
            for (int x = p.parcel().x0(); x <= p.parcel().x1(); x++) {
                BlockType t = p.voxels().getBlock(x, slab + 1, z);
                BlockType high = p.voxels().getBlock(x, slab + 2, z);
                char cell = switch (t) {
                    case BRICK, PLASTER, CONCRETE -> '#';
                    case WOOD_DOOR, METAL_DOOR -> 'D';
                    case SHELF, CABINET, LOCKER -> 'C';
                    case TABLE -> 'T';
                    case STAIRS, STAIRS_NORTH, STAIRS_SOUTH, STAIRS_WEST, STAIRS_EAST -> '1';
                    default -> '.';
                };
                if (t == BlockType.BRICK && (high == BlockType.WINDOW || high == BlockType.BROKEN_WINDOW)) { cell = 'W'; }
                if (cell == '.') {
                    for (int h = 2; h <= 4; h++) {
                        BlockType step = p.voxels().getBlock(x, slab + h, z);
                        if (step == BlockType.STAIRS || com.veylon.world.StairGeometry.isStair(step)) {
                            cell = (char) ('0' + h);
                        }
                    }
                }
                out.append(cell);
            }
            out.append('\n');
        }
        for (BuildingPlan.Floor f : p.floors()) {
            if (f.index() == floor) {
                for (BuildingPlan.Room room : f.rooms()) {
                    out.append("room=").append(UrbanIds.roomIndex(room.id())).append(' ')
                            .append(room.functions()).append(" at=").append(room.anchor()).append('\n');
                }
            }
        }
        return out.toString();
    }

    /** Headless diagnostic command; prints plans, never creates a World or accesses a save slot. */
    public static void main(String[] args) {
        for (long seed : new long[]{20260731, -4242, 7}) {
            UrbanWorldState urban = new UrbanWorldState(seed, com.veylon.world.World.GEN_URBAN_PLANS);
            DistrictPlan district = urban.regionPlan(0, 0);
            for (ParcelPlan.Use use : new ParcelPlan.Use[]{ParcelPlan.Use.REFUGE, ParcelPlan.Use.COMMERCIAL}) {
                ParcelPlan parcel = district.parcels().stream().filter(p -> p.use() == use).findFirst().orElseThrow();
                BuildingPlanner.Result result = BuildingPlanner.plan(seed, 1, parcel);
                BuildingPlan plan = result.plan();
                System.out.printf(Locale.ROOT, "seed=%d id=%s fingerprint=0x%016x attempts=%d%n%s%n%s%n",
                        seed, UrbanIds.format(plan.id()), fingerprint(plan), result.attempts(),
                        result.validation().summary(), floor(plan, 0));
            }
        }
        DistrictPlan district = CityPlanner.plan(20260731, 2, 0, 0);
        BuildingPlan good = BuildingPlanner.plan(20260731, 1, district.refuge()).plan();
        var departure = good.stairs().getFirst().route().getFirst();
        BuildingPlan invalid = good.withVoxels(good.voxels().edit()
                .set(departure.x(), departure.y() + 2, departure.z(), BlockType.CONCRETE).build());
        BuildingPlanner.Result fixed = BuildingPlanner.repair(invalid);
        System.out.println("invalid stair fixture: " + AccessibilityValidator.inspect(invalid).summary());
        System.out.println("finite repair: attempts=" + fixed.attempts() + " " + fixed.validation().summary());
    }
}
