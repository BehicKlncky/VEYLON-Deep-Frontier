package com.veylon;

import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.Chunk;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.UrbanIds;

import java.util.List;

/**
 * Generated apartment QA, without staged structure or container contents.
 * {@code urban_apartments} and {@code urban_apartments_night} hold six camera
 * views for appearance captures: street, entrance, kitchen, upper floor,
 * basement and exit, at four-second intervals. These are explicitly scripted
 * camera positions, not evidence of player navigation.
 *
 * <p>{@code urban_apartments_manual} and {@code urban_apartments_manual_night}
 * only place a Survival player outside the same building at session start.
 * Subsequent mouse look, movement and interactions are ordinary player input;
 * this class never moves the player again. A human must perform and record
 * the walk. Raw night QA equips a handheld torch but adds no world lights.
 * {@code urban_apartments_lit_night} separately adds four portable QA torches
 * through the ordinary block notification path for supplemental readability
 * captures. Those are labelled instruments, not generated building utilities.
 */
final class UrbanApartmentQaScene {
    private final Game game;
    private BuildingPlan building;
    private List<View> views = List.of();
    private boolean manual;
    private boolean night;
    private boolean supplementalLighting;
    private int viewIndex = -1;

    private record View(String name, Vec3i feet, Vec3i lookAt, float pitch) { }

    UrbanApartmentQaScene(Game game) { this.game = game; }

    /** True only for the start-only scenes that must retain normal mouse look. */
    static boolean manualNavigation(String scene) {
        if (scene == null) { return false; }
        String normalized = scene.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.equals("urban_apartments_manual")
                || normalized.equals("urban_apartments_manual_night");
    }

    void stage(String scene) {
        World world = game.world;
        if (world.generatorVersion != World.GEN_URBAN_APARTMENTS) {
            System.out.println("[urban-qa] apartments require the generated-apartment urban-dev revision; inactive");
            return;
        }
        manual = manualNavigation(scene);
        night = scene.endsWith("_night");
        supplementalLighting = scene.equals("urban_apartments_lit_night");
        building = selectBuilding(world);
        ParcelPlan parcel = building.parcel();
        int centreX = (parcel.x0() + parcel.x1()) / 2;
        int centreZ = (parcel.z0() + parcel.z1()) / 2;
        world.ensureChunks(centreX, centreZ, 4, 10_000);
        game.time.totalMinutes = night ? 23 * 60 : 12 * 60;
        if (night) {
            game.player.inventory.set(game.player.hotbarSel, new ItemStack(ItemType.TORCH, 1));
        }
        views = createViews(building);
        if (supplementalLighting) { placeSupplementalTorches(); }
        System.out.println("[urban-qa] apartments building=" + UrbanIds.format(building.id())
                + " template=" + building.templateRevision() + " variant=" + building.variant()
                + " bounds=" + parcel.x0() + "," + parcel.z0() + ".." + parcel.x1() + "," + parcel.z1()
                + " chunkBoundary=" + crossesChunkBoundary(parcel)
                + " storeys=" + building.storeys() + " basement=" + building.basement()
                + " evidence=" + (manual ? "manual-navigation-pending" : "scripted-camera-appearance-only")
                + " lighting=" + (supplementalLighting ? "qa-placed-portable-torches" : "generated-world-only"));
        applyView(0);
        if (manual) {
            System.out.println("[urban-qa] apartments manual scene: normal Survival movement, mouse look and F interactions;"
                    + " enter, find kitchen, climb to upper floor, descend to basement, return outside."
                    + " No manual walk has been recorded by the scene.");
        }
    }

    private void placeSupplementalTorches() {
        ParcelPlan.Entrance entry = building.parcel().primaryEntrance();
        Vec3i entranceLight = new Vec3i(entry.outsideX() - (entry.facing().dx == 0 ? 1 : 0),
                41, entry.outsideZ() - (entry.facing().dz == 0 ? 1 : 0));
        placeQaTorch(entranceLight);
        // On the existing cabinet/locker tops: no generated fixture is replaced
        // and no standing or stair route receives a torch at the player's feet.
        for (int index : new int[]{2, 3, 4}) {
            Vec3i fixture = views.get(index).lookAt();
            placeQaTorch(new Vec3i(fixture.x(), fixture.y() + 1, fixture.z()));
        }
        System.out.println("[urban-qa] apartments supplemental light: four QA-placed portable torches;"
                + " generated structure/fixtures preserved; no loot added;"
                + " not generated utilities or manual navigation evidence");
    }

    private void placeQaTorch(Vec3i at) {
        if (game.world.getBlock(at.x(), at.y(), at.z()) != BlockType.AIR) {
            throw new IllegalStateException("QA torch would replace generated geometry at " + at);
        }
        game.world.setBlock(at.x(), at.y(), at.z(), BlockType.TORCH, true);
    }

    /** Selects from actual pure city plans, never creates or replaces a building. */
    static BuildingPlan selectBuilding(World world) {
        for (ParcelPlan parcel : world.urban.regionPlan(0, 0).parcels()) {
            if (parcel.use() == ParcelPlan.Use.COMMERCIAL || !crossesChunkBoundary(parcel)) { continue; }
            BuildingPlan plan = world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (plan != null && plan.family() == BuildingPlan.Family.APARTMENT && plan.basement()
                    && plan.storeys() >= 2 && plan.variant() != BuildingPlan.Variant.OCCUPIED) {
                return plan;
            }
        }
        throw new IllegalStateException("urban apartment QA seed has no unlocked multi-floor boundary apartment with basement");
    }

    static boolean crossesChunkBoundary(ParcelPlan parcel) {
        return Math.floorDiv(parcel.x0(), Chunk.SX) != Math.floorDiv(parcel.x1(), Chunk.SX)
                || Math.floorDiv(parcel.z0(), Chunk.SZ) != Math.floorDiv(parcel.z1(), Chunk.SZ);
    }

    private static List<View> createViews(BuildingPlan plan) {
        ParcelPlan.Entrance entry = plan.parcel().primaryEntrance();
        Vec3i door = new Vec3i(entry.x(), 41, entry.z());
        Vec3i street = new Vec3i(entry.outsideX() + 4 * entry.facing().dx, 41,
                entry.outsideZ() + 4 * entry.facing().dz);
        Vec3i inside = new Vec3i(entry.x() - 3 * entry.facing().dx, 41, entry.z() - 3 * entry.facing().dz);
        BuildingPlan.Floor ground = floor(plan, 0), upper = floor(plan, 1), basement = floor(plan, -1);
        View kitchen = roomView(plan, ground, BuildingPlan.Function.KITCHEN, "kitchen");
        View upstairs = roomView(plan, upper, BuildingPlan.Function.LIVING, "upper-floor");
        View downstairs = roomView(plan, basement, BuildingPlan.Function.BASEMENT_STORAGE, "basement");
        ParcelPlan.Entrance rear = plan.parcel().entrances().getLast();
        Vec3i rearDoor = new Vec3i(rear.x(), 41, rear.z());
        Vec3i rearOutside = new Vec3i(rear.outsideX() + 2 * rear.facing().dx, 41,
                rear.outsideZ() + 2 * rear.facing().dz);
        return List.of(new View("street", street, door, 0f), new View("entrance", inside, door, 8f),
                kitchen, upstairs, downstairs, new View("exit", rearOutside, rearDoor, 4f));
    }

    private static BuildingPlan.Floor floor(BuildingPlan plan, int index) {
        return plan.floors().stream().filter(f -> f.index() == index).findFirst().orElseThrow();
    }

    private static View roomView(BuildingPlan plan, BuildingPlan.Floor floor,
                                 BuildingPlan.Function function, String label) {
        BuildingPlan.Room room = floor.rooms().stream().filter(r -> r.functions().contains(function))
                .findFirst().orElseThrow();
        for (BuildingPlan.Fixture fixture : plan.fixtures()) {
            Vec3i p = fixture.position();
            if (fixture.kind() == BuildingPlan.FixtureKind.CONTAINER && p.y() == floor.slabY() + 1
                    && room.interior().contains(p.x(), p.z())) {
                return new View(label, room.anchor(), p, 12f);
            }
        }
        return new View(label, room.anchor(), floor.landing(), 6f);
    }

    /** Holds scripted appearance views only; manual scenes have no per-frame intervention. */
    void update(double elapsed) {
        if (building == null || manual) { return; }
        game.time.totalMinutes = night ? 23 * 60 : 12 * 60;
        int next = Math.min(views.size() - 1, Math.max(0, (int) (elapsed / 4)));
        applyView(next);
    }

    private void applyView(int index) {
        View view = views.get(index);
        Vec3i p = view.feet(), target = view.lookAt();
        game.player.pos.set(p.x() + .5f, p.y(), p.z() + .5f);
        game.player.vel.zero();
        game.camera.yaw = (float) Math.toDegrees(Math.atan2(target.x() - p.x(), p.z() - target.z()));
        game.camera.pitch = view.pitch();
        if (index != viewIndex) {
            System.out.println("[urban-qa] apartments view=" + view.name() + " feet=" + p
                    + " evidence=" + (manual ? "manual-start-only" : "scripted-camera-appearance-only"));
            viewIndex = index;
        }
    }

    BuildingPlan building() { return building; }
}
