package com.veylon;

import com.veylon.entity.UrbanEncounterDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.VoxelAccess;
import com.veylon.world.World;
import com.veylon.world.urban.AccessibilityValidator;
import com.veylon.world.urban.BuildingFurnishing.ContainerUse;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.UrbanStarterSite;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import static com.veylon.world.urban.BuildingFurnishing.containerUse;

/**
 * The milestone-11 playable slice of a generated starter neighbourhood: the refuge, one
 * multi-floor apartment that hosts an encounter, the corner shop and the places the route
 * uses in them. It is derived from the frozen plan only; nothing here touches the world.
 */
record UrbanSlicePlan(BuildingPlan refuge, BuildingPlan apartment, BuildingPlan shop, Vec3i home,
                      Fixture locker, Fixture kitchen, Fixture bathroom, Fixture upstairs,
                      Fixture shelf, Fixture workbench, Fixture store,
                      List<Fixture> streetDoors, Fixture forcedDoor) {
    /** Bounded route search: a starter route stays within this box and node count. */
    static final int ROUTE_RADIUS = 128, ROUTE_NODES = 60_000;

    static UrbanSlicePlan of(World world) {
        if (world.urban == null || !world.urban.hasEncounters()) {
            throw new IllegalStateException("the playable slice needs the urban encounter generator");
        }
        var site = UrbanStarterSite.of(world.urban);
        BuildingPlan refuge = site.refuge(), shop = null;
        List<BuildingPlan> apartments = new ArrayList<>();
        for (var parcel : world.urban.regionPlan(0, 0).parcels()) {
            if (!UrbanStarterSite.isNeighbour(parcel)) { continue; }
            BuildingPlan b = world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (b.id() == refuge.id()) { continue; }
            if (b.family() == BuildingPlan.Family.SHOP) { shop = b; } else { apartments.add(b); }
        }
        if (shop == null || apartments.isEmpty()) { throw new IllegalStateException("starter block lacks a shop or apartment"); }
        Vec3i court = refuge.approaches().getLast();
        // Catalogue six (milestone 23) keeps only the farthest starter apartment's slots, which cannot be
        // admitted while the survivor stands inside it: the route searches the nearest other apartment and
        // meets the threat that comes from across the block, as DESIGN §3.1's first session does.
        boolean eased = EncounterAnchors.catalogue(world) == 6;
        BuildingPlan apartment = apartments.stream().min(Comparator
                .comparingInt((BuildingPlan b) -> eased ? -threatRank(world, b, refuge) : threatRank(world, b, refuge))
                .thenComparingInt(b -> distSq(b.approaches().getLast(), court))
                .thenComparingLong(BuildingPlan::id)).orElseThrow();
        Fixture locker = container(apartment, 0, ContainerUse.UTILITY_LOCKER);
        Fixture bench = refuge.fixtures().stream().filter(f -> f.block() == BlockType.WORKBENCH
                && f.position().y() == groundY(refuge)).findFirst().orElseThrow();
        Fixture store = refuge.fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.CONTAINER && f.position().y() == groundY(refuge))
                .min(Comparator.comparingInt((Fixture f) -> distSq(f.target(), bench.target()))
                        .thenComparingLong(Fixture::id)).orElseThrow();
        Vec3i street = refuge.approaches().getFirst();
        List<Fixture> streetDoors = refuge.fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR && f.target().y() == street.y()
                        && Math.abs(f.target().x() - street.x()) + Math.abs(f.target().z() - street.z()) <= 1)
                .sorted(Comparator.comparingLong(Fixture::id)).toList();
        Fixture forced = apartment.fixtures().stream()
                .filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR && f.position().y() == groundY(apartment)
                        && apartment.parcel().contains(f.target().x(), f.target().z())
                        && !entrance(apartment, f))
                .min(Comparator.comparingInt((Fixture f) -> distSq(f.target(), locker.target()))
                        .thenComparingLong(Fixture::id)).orElseThrow();
        if (streetDoors.isEmpty()) { throw new IllegalStateException("refuge has no street door"); }
        return new UrbanSlicePlan(refuge, apartment, shop, world.urban.refugeAnchor, locker,
                container(apartment, 0, ContainerUse.KITCHEN_CABINET), container(apartment, 0, ContainerUse.BATHROOM_CABINET),
                container(apartment, 1, ContainerUse.KITCHEN_CABINET), container(shop, 0, ContainerUse.SHOP_SHELF),
                bench, store, streetDoors, forced);
    }

    /** The inside cell from which a door leaf is faced, one step beyond its outer target. */
    static Vec3i inside(Fixture door) {
        Vec3i p = door.position(), t = door.target();
        return new Vec3i(2 * p.x() - t.x(), t.y(), 2 * p.z() - t.z());
    }

    /**
     * Bounded breadth-first walking route over the production step/drop/clearance rules,
     * treating closed unlocked doors as the opening a player's F makes. Never loads a chunk.
     *
     * @return the cells after {@code start} up to {@code goal}, or null when unreachable
     */
    static List<Vec3i> route(World world, Vec3i start, Vec3i goal) {
        return route(world, start, goal, cell -> true);
    }

    /** {@link #route(World, Vec3i, Vec3i)} restricted to the cells {@code allowed} accepts. */
    static List<Vec3i> route(World world, Vec3i start, Vec3i goal, Predicate<Vec3i> allowed) {
        VoxelAccess opened = new VoxelAccess() {
            @Override public boolean isColumnLoaded(int x, int z) { return world.isColumnLoaded(x, z); }
            @Override public BlockType getBlock(int x, int y, int z) {
                BlockType b = world.getBlock(x, y, z);
                return Fixtures.isClosedDoor(b) && world.fixtureLockAt(x, y, z) == FixtureStates.LOCK_NONE
                        ? Fixtures.kindOf(b).open : b;
            }
        };
        Map<Vec3i, Vec3i> parents = new HashMap<>();
        ArrayDeque<Vec3i> queue = new ArrayDeque<>();
        parents.put(start, null);
        queue.add(start);
        while (!queue.isEmpty() && !parents.containsKey(goal) && parents.size() < ROUTE_NODES) {
            Vec3i from = queue.remove();
            for (int d = 0; d < 4; d++) {
                int dx = d == 0 ? 1 : d == 1 ? -1 : 0, dz = d == 2 ? 1 : d == 3 ? -1 : 0;
                for (int dy = 1; dy >= -3; dy--) {
                    Vec3i to = from.offset(dx, dy, dz);
                    if (Math.abs(to.x() - start.x()) > ROUTE_RADIUS || Math.abs(to.z() - start.z()) > ROUTE_RADIUS
                            || parents.containsKey(to) || !allowed.test(to)
                            || !AccessibilityValidator.canMove(opened, from, to)) {
                        continue;
                    }
                    parents.put(to, from);
                    queue.add(to);
                    break;
                }
            }
        }
        if (!parents.containsKey(goal)) { return null; }
        List<Vec3i> path = new ArrayList<>();
        for (Vec3i at = goal; at != null && !at.equals(start); at = parents.get(at)) { path.add(at); }
        Collections.reverse(path);
        return path;
    }

    private static Fixture container(BuildingPlan b, int floor, ContainerUse use) {
        int y = groundY(b) + floor * 4;
        return b.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.CONTAINER
                        && f.position().y() == y && containerUse(b, f) == use)
                .min(Comparator.comparingLong(Fixture::id))
                .orElseThrow(() -> new IllegalStateException("no " + use + " on floor " + floor + " of " + b.id()));
    }

    private static boolean entrance(BuildingPlan b, Fixture door) {
        for (Vec3i a : b.approaches()) {
            if (Math.abs(door.target().x() - a.x()) + Math.abs(door.target().z() - a.z()) <= 1) { return true; }
        }
        return false;
    }

    /** 0: the ground-floor room anchor can admit a body; 1: only the street anchor; 2: neither. */
    private static int threatRank(World world, BuildingPlan b, BuildingPlan refuge) {
        int rank = 2;
        for (var anchor : EncounterAnchors.forParcel(world, b.parcel())) {
            if (admissible(world, anchor.cell(), refuge)) { rank = Math.min(rank, anchor.indoor() ? 0 : 1); }
        }
        return rank;
    }

    /** The static sanctuary half of {@link UrbanEncounterDirector#safeSpawn}. */
    private static boolean admissible(World world, Vec3i cell, BuildingPlan refuge) {
        Vec3i door = refuge.approaches().getLast();
        float dx = cell.x() - door.x(), dz = cell.z() - door.z();
        float sanctuary = UrbanEncounterDirector.SANCTUARY;
        return dx * dx + dz * dz >= sanctuary * sanctuary && !refuge.parcel().contains(cell.x(), cell.z())
                && (world.urban.refugeAnchor == null
                || world.urban.refugeAnchor.distSq(cell.x() + .5, cell.y(), cell.z() + .5) >= sanctuary * sanctuary);
    }

    private static int groundY(BuildingPlan b) { return b.floors().stream().filter(f -> f.index() == 0).findFirst().orElseThrow().slabY() + 1; }
    private static int distSq(Vec3i a, Vec3i b) {
        int dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
        return dx * dx + dy * dy + dz * dz;
    }
}
