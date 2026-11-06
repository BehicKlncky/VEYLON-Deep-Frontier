package com.veylon.world.urban;

import com.veylon.world.BlockType;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Variant;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * Read-only floor diagrams, stable geometry/reservation fingerprints and catalogue reports for
 * review and regression pins. The catalogue reports name the failing template, family,
 * variant and parcel directly, so nobody has to walk the city to find a broken building.
 */
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

    /**
     * World north at the top. Digits are tread heights above the selected floor's slab; {@code L}
     * is a locked door, {@code R} a rack upright and {@code =} security bars.
     */
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
                    case WOOD_DOOR, METAL_DOOR -> lockedDoorAt(p, x, slab + 1, z) ? 'L' : 'D';
                    case SHELF, CABINET, LOCKER -> 'C';
                    case TABLE -> 'T';
                    case STAIRS, STAIRS_NORTH, STAIRS_SOUTH, STAIRS_WEST, STAIRS_EAST -> '1';
                    case SHEET_METAL -> p.templateRevision() == UrbanRevision.BUILDING_TEMPLATE_CIVIC ? 'R' : '.';
                    case CAGE_BARS -> '=';
                    default -> '.';
                };
                if (t == CivicTemplates.facadeOf(p) && p.parcel().onOutline(x, z)) { cell = '#'; }
                if (high == BlockType.CAGE_BARS) { cell = '='; }
                if (t == CivicTemplates.facadeOf(p) && (high == BlockType.WINDOW || high == BlockType.BROKEN_WINDOW)) {
                    cell = 'W';
                }
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

    private static boolean lockedDoorAt(BuildingPlan p, int x, int y, int z) {
        for (BuildingPlan.Fixture f : p.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.DOOR && f.locked() && f.position().x() == x
                    && f.position().z() == z && y >= f.position().y() && y < f.position().y() + f.height()) {
                return true;
            }
        }
        return false;
    }

    /**
     * One region under template revision five: family and template counts, every repaired
     * building with each attempt's identity and diagnostics, and every parcel whose finite
     * fallback failed. Plans only; no world, chunk or save slot is touched.
     */
    public static String catalogue(long seed, int rx, int rz) {
        return catalogue(seed, CityPlanner.planStarter(seed, rx, rz), UrbanRevision.BUILDING_TEMPLATE_CIVIC);
    }

    /** The same report for a layout-nine region under the district catalogue (revision six). */
    public static String districts(long seed, int rx, int rz) {
        return catalogue(seed, CityPlanner.planDistricts(seed, rx, rz), UrbanRevision.BUILDING_TEMPLATE_DISTRICTS);
    }

    private static String catalogue(long seed, DistrictPlan district, int revision) {
        int rx = district.rx, rz = district.rz;
        StringBuilder out = new StringBuilder();
        Map<String, Integer> templates = new TreeMap<>();
        int repaired = 0, failed = 0;
        out.append(String.format(Locale.ROOT, "seed=%d region=%d,%d district=%s parcels=%d%n",
                seed, rx, rz, district.district, district.parcels().size()));
        for (ParcelPlan parcel : district.parcels()) {
            try {
                BuildingPlanner.Result result = BuildingPlanner.plan(seed, revision, parcel, district);
                templates.merge(result.plan().family() + "/" + result.plan().template(), 1, Integer::sum);
                if (result.attempts() > 1) {
                    repaired++;
                    out.append("REPAIRED attempts=").append(result.attempts()).append(' ')
                            .append(BuildingPlanner.identity(result.plan())).append(' ').append(result.repairs()).append('\n');
                }
            } catch (RuntimeException e) {
                failed++;
                out.append("FAILED parcel=").append(UrbanIds.format(parcel.id())).append(' ')
                        .append(e.getMessage()).append('\n');
            }
        }
        out.append("templates=").append(templates).append(" repaired=").append(repaired)
                .append(" failed=").append(failed).append('\n');
        return out.toString();
    }

    /**
     * Every commercial and civic family across sizes, frontages, variants, storeys and
     * basements, proposed without repair. Returns the identity and diagnostics of each invalid
     * proposal, so a regression names its template; an empty list means all passed.
     */
    public static List<String> familyMatrix(int[][] sizes, long[] seeds) {
        List<String> failures = new ArrayList<>();
        for (Family family : CivicCatalogue.FAMILIES) {
            CivicTemplates.Envelope envelope = CivicTemplates.envelope(family);
            for (int[] size : sizes) {
                for (ParcelPlan.Side side : ParcelPlan.Side.values()) {
                    for (long seed : seeds) {
                        ParcelPlan parcel = testParcel(size[0], size[1], side, seed, 8);
                        if (!envelope.fits(parcel)) { continue; }
                        for (Variant variant : Variant.values()) {
                            for (int storeys = envelope.minStoreys(); storeys <= envelope.storeys(parcel); storeys++) {
                                for (boolean basement : envelope.basements() ? new boolean[]{false, true} : new boolean[]{false}) {
                                    BuildingPlan plan = BuildingTemplates.propose(parcel, UrbanRevision.BUILDING_TEMPLATE_CIVIC,
                                            family, variant, storeys, basement, false);
                                    AccessibilityValidator.Report report = AccessibilityValidator.inspect(plan);
                                    if (!report.ok()) { failures.add(BuildingPlanner.identity(plan) + " " + report.summary()); }
                                }
                            }
                        }
                    }
                }
            }
        }
        return failures;
    }

    /**
     * A standalone parcel with the planner's reservations: primary entrance centred on the
     * frontage, the stair core beside it and the rear entrance opposite. The seed only moves
     * the parcel, so far regions and negative coordinates are exercised too.
     */
    public static ParcelPlan testParcel(int width, int depth, ParcelPlan.Side side, long seed, int maxStoreys) {
        int x0 = (int) Math.floorMod(seed * 37, 4096L) - 2048, z0 = (int) Math.floorMod(seed * 53, 4096L) - 2048;
        boolean alongX = side.dx == 0;
        int wx = alongX ? width : depth, wz = alongX ? depth : width;
        int x1 = x0 + wx - 1, z1 = z0 + wz - 1;
        int e = (width - 2) / 2;
        ParcelPlan.Entrance front = switch (side) {
            case NORTH -> new ParcelPlan.Entrance(x0 + e, z0, side, true);
            case SOUTH -> new ParcelPlan.Entrance(x0 + e, z1, side, true);
            case WEST -> new ParcelPlan.Entrance(x0, z0 + e, side, true);
            case EAST -> new ParcelPlan.Entrance(x1, z0 + e, side, true);
        };
        ParcelPlan.Entrance rear = switch (side) {
            case NORTH -> new ParcelPlan.Entrance(x0 + e, z1, side.opposite(), false);
            case SOUTH -> new ParcelPlan.Entrance(x0 + e, z0, side.opposite(), false);
            case WEST -> new ParcelPlan.Entrance(x1, z0 + e, side.opposite(), false);
            case EAST -> new ParcelPlan.Entrance(x0, z0 + e, side.opposite(), false);
        };
        ParcelPlan.Rect core = switch (side) {
            case NORTH -> new ParcelPlan.Rect(x0 + e + 2, z0 + 1, x0 + e + 5, z0 + 6);
            case SOUTH -> new ParcelPlan.Rect(x0 + e + 2, z1 - 6, x0 + e + 5, z1 - 1);
            case WEST -> new ParcelPlan.Rect(x0 + 1, z0 + e + 2, x0 + 6, z0 + e + 5);
            case EAST -> new ParcelPlan.Rect(x1 - 6, z0 + e + 2, x1 - 1, z0 + e + 5);
        };
        long id = UrbanIds.parcel(UrbanIds.district(Math.floorDiv(x0, 256), Math.floorDiv(z0, 256)), 0);
        return new ParcelPlan(0, id, 0, ParcelPlan.Use.COMMERCIAL, x0, z0, x1, z1, side,
                List.of(front, rear), core, maxStoreys);
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
        if (args.length > 0 && args[0].equals("catalogue")) { civicCatalogue(); }
    }

    /** Template revision five: region reports plus one ground and one upper diagram per family. */
    private static void civicCatalogue() {
        long seed = 20260731;
        for (int[] region : new int[][]{{0, 0}, {1, -1}, {-1, -1}}) {
            System.out.print(catalogue(seed, region[0], region[1]));
        }
        List<String> failures = familyMatrix(new int[][]{{12, 12}, {16, 12}, {20, 16}, {24, 24}, {32, 32}},
                new long[]{seed, -4242});
        System.out.println("family-matrix failures=" + failures.size() + (failures.isEmpty() ? "" : " " + failures));
        for (Family family : CivicCatalogue.FAMILIES) {
            BuildingPlan plan = example(seed, family);
            System.out.printf(Locale.ROOT, "%s id=%s sign=\"%s\" fingerprint=0x%016x%n%s%n", family,
                    UrbanIds.format(plan.id()), CivicCatalogue.signText(seed, plan), fingerprint(plan), floor(plan, 0));
            if (plan.storeys() > 1) { System.out.println(floor(plan, 1)); }
        }
    }

    /** The first building of a family in the regions around the origin, in parcel order. */
    public static BuildingPlan example(long seed, Family family) {
        for (int[] region : new int[][]{{0, 0}, {1, -1}, {-1, -1}, {-1, 1}, {1, 1}, {2, 3}, {-3, -2}}) {
            DistrictPlan district = CityPlanner.planStarter(seed, region[0], region[1]);
            for (ParcelPlan parcel : district.parcels()) {
                if (CivicCatalogue.family(seed, district, parcel) == family) {
                    return BuildingPlanner.plan(seed, UrbanRevision.BUILDING_TEMPLATE_CIVIC, parcel, district).plan();
                }
            }
        }
        throw new IllegalStateException("no " + family + " near the origin for seed " + seed);
    }
}
