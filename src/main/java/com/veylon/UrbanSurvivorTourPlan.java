package com.veylon;

import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CityPlanner;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;

import java.util.Comparator;
import java.util.Locale;

/**
 * Where the milestone-16 survivor tour ({@code urban_survivors}) goes on a generator-15 world: the
 * camp and the hidden refuge nearest the spawn, read from their pure plans. For the camp, the leaf
 * of its unlatched rear door (the street door of a held building is locked) and the yard cell
 * outside it; for the refuge, its building's street approach, the basement hall in front of the
 * loose panel, the panel and the first cell inside; and a sidewalk corner far enough from both
 * for their people to go dormant. Reads plans only; changes no block and loads no chunk.
 */
record UrbanSurvivorTourPlan(Camp camp, Refuge refuge, Vec3i away) {
    /** How far from both sites the dormancy leg stands, beyond the deactivation radius. */
    static final float AWAY = UrbanSiteDirector.DEACTIVATE + 40f;

    /** A camp: its plan, the rear door leaf its way in passes, and the yard cell outside that door. */
    record Camp(SurvivorSites.Site site, Vec3i door, Vec3i yard) { }

    /** A refuge: its plan, its building's street approach, the hall cell, the panel and the cell inside. */
    record Refuge(SurvivorSites.Site site, Vec3i street, Vec3i hall, Vec3i panel, Vec3i inside) { }

    static UrbanSurvivorTourPlan of(World world) {
        if (world.urban == null || !world.urban.hasSurvivors()) {
            throw new IllegalStateException("urban_survivors needs generator 15 (VEYLON_GENERATOR=urban-dev)");
        }
        int[] spawn = world.urban.spawnColumn();
        SurvivorSites.Site camp = nearest(world, spawn, SurvivorSites.Kind.CAMP);
        SurvivorSites.Site refuge = nearest(world, spawn, SurvivorSites.Kind.REFUGE);
        return new UrbanSurvivorTourPlan(camp(world, camp), refuge(world, refuge), away(world, camp, refuge));
    }

    private static SurvivorSites.Site nearest(World world, int[] spawn, SurvivorSites.Kind kind) {
        SurvivorSites.Site best = null;
        int rx0 = StreetNetwork.regionOf(spawn[0]), rz0 = StreetNetwork.regionOf(spawn[1]);
        Comparator<SurvivorSites.Site> near = Comparator.comparingDouble(s -> s.anchor().distSq(spawn[0], s.anchor().y(), spawn[1]));
        for (int rz = rz0 - 2; rz <= rz0 + 2; rz++) {
            for (int rx = rx0 - 2; rx <= rx0 + 2; rx++) {
                if (!CityPlanner.isPlannable(rx, rz)) { continue; }
                for (SurvivorSites.Site s : world.urban.sites(rx, rz).sites()) {
                    if (s.kind() == kind && (best == null || near.compare(s, best) < 0)) { best = s; }
                }
            }
        }
        if (best == null) { throw new IllegalStateException("no " + kind.label.toLowerCase(Locale.ROOT) + " within two regions of the spawn"); }
        return best;
    }

    /** The rear door leaf whose inside cell is the camp's way in, and the yard cell beyond it. */
    private static Camp camp(World world, SurvivorSites.Site site) {
        BuildingPlan b = world.urban.buildingAt(site.anchor().x(), site.anchor().z());
        Vec3i in = site.entry();
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.DOOR || f.target() == null) { continue; }
            Vec3i p = f.position(), t = f.target();
            if (2 * p.x() - t.x() == in.x() && 2 * p.z() - t.z() == in.z() && p.y() == in.y()) {
                return new Camp(site, p, new Vec3i(t.x(), in.y(), t.z()));
            }
        }
        throw new IllegalStateException("the camp " + UrbanIds.format(site.id()) + " has no rear door before " + in);
    }

    private static Refuge refuge(World world, SurvivorSites.Site site) {
        BuildingPlan b = world.urban.buildingAt(site.anchor().x(), site.anchor().z());
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.block() != BlockType.HIDDEN_PANEL) { continue; }
            Vec3i p = f.position(), t = f.target();
            return new Refuge(site, b.approaches().getFirst(), new Vec3i(2 * p.x() - t.x(), p.y(), 2 * p.z() - t.z()), p, t);
        }
        throw new IllegalStateException("the refuge " + UrbanIds.format(site.id()) + " has no loose panel");
    }

    /**
     * A sidewalk corner of a region near the camp, at least {@link #AWAY} blocks from both sites:
     * the corner where its first local streets cross, as the street scenes stand. Its height is
     * read on arrival.
     */
    private static Vec3i away(World world, SurvivorSites.Site camp, SurvivorSites.Site refuge) {
        int crx = StreetNetwork.regionOf(camp.anchor().x()), crz = StreetNetwork.regionOf(camp.anchor().z());
        Vec3i best = null;
        double bestD = Double.MAX_VALUE;
        for (int rz = crz - 2; rz <= crz + 2; rz++) {
            for (int rx = crx - 2; rx <= crx + 2; rx++) {
                if (!CityPlanner.isPlannable(rx, rz)) { continue; }
                DistrictPlan plan = world.urban.regionPlan(rx, rz);
                int x = plan.northSouthLine(1) + 2, z = plan.eastWestLine(1) + StreetNetwork.LOCAL_HALF_WIDTH + 1;
                double toCamp = Math.hypot(x - camp.anchor().x(), z - camp.anchor().z());
                double toRefuge = Math.hypot(x - refuge.anchor().x(), z - refuge.anchor().z());
                if (toCamp < AWAY || toRefuge < AWAY) { continue; }
                if (toCamp < bestD) {
                    bestD = toCamp;
                    best = new Vec3i(x, 0, z);
                }
            }
        }
        if (best == null) { throw new IllegalStateException("no street far enough from both sites"); }
        return best;
    }

    @Override
    public String toString() {
        return "camp=" + UrbanIds.format(camp.site().id()) + " people=" + camp.site().residents().size()
                + " door=" + camp.door() + " yard=" + camp.yard() + " guestBeds=" + camp.site().guestBeds().size()
                + " refuge=" + UrbanIds.format(refuge.site().id()) + " people=" + refuge.site().residents().size()
                + " street=" + refuge.street() + " panel=" + refuge.panel() + " away=" + away.x() + "," + away.z();
    }
}
