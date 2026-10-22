package com.veylon;

import com.veylon.settlement.UrbanSiteDirector;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CityPlanner;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.CompoundSites.Zone;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;

import java.util.Locale;

/**
 * Where the milestone-17 stronghold tour ({@code urban_strongholds}) goes on a generator-16 world:
 * the guarded stronghold and the raider yard nearest the spawn, read from their pure plans. For the
 * stronghold: the street in front of its gate, the porch cell its sign is read from, a yard cell
 * inside the gate, a street cell for a threat, and two courtyard doors with the cell inside and
 * the yard cell outside each: the clinic's (where visitors may go) and a barracks' (where they may
 * not), the barracks door nearest a patrol waypoint so a guard passes it. For the raider yard: its
 * back way's street approach, the leaf of its rear door into the yard and the cell inside it. And a
 * sidewalk corner far enough from both for their people to go dormant. Reads plans only; changes no
 * block and loads no chunk.
 */
record UrbanStrongholdTourPlan(Stronghold stronghold, Raiders raiders, Vec3i away) {
    /** How far from both compounds the dormancy leg stands, beyond the deactivation radius. */
    static final float AWAY = UrbanSiteDirector.DEACTIVATE + 40f;
    /** How many steps out into the street a threat is met: inside the gatekeeper's sight of infected. */
    static final int THREAT_STEPS = 5;

    record Stronghold(CompoundSites.Compound compound, Vec3i street, Vec3i signStand, Vec3i yard, Vec3i threat,
                      Vec3i clinicInside, Vec3i clinicYard, Vec3i barracksInside, Vec3i barracksYard) { }

    record Raiders(CompoundSites.Compound compound, Vec3i street, Vec3i streetDoor, Vec3i rearDoor, Vec3i rearInside) { }

    static UrbanStrongholdTourPlan of(World world) {
        if (world.urban == null || !world.urban.hasStrongholds()) {
            throw new IllegalStateException("urban_strongholds needs generator 16 (VEYLON_GENERATOR=urban-dev)");
        }
        int[] spawn = world.urban.spawnColumn();
        CompoundSites.Compound stronghold = nearest(world, spawn, SurvivorSites.Kind.STRONGHOLD);
        CompoundSites.Compound raiders = nearest(world, spawn, SurvivorSites.Kind.RAIDERS);
        return new UrbanStrongholdTourPlan(stronghold(world, stronghold), raiders(world, raiders),
                away(world, stronghold, raiders));
    }

    private static CompoundSites.Compound nearest(World world, int[] spawn, SurvivorSites.Kind kind) {
        CompoundSites.Compound best = null;
        int rx0 = StreetNetwork.regionOf(spawn[0]), rz0 = StreetNetwork.regionOf(spawn[1]);
        for (int rz = rz0 - 3; rz <= rz0 + 3; rz++) {
            for (int rx = rx0 - 3; rx <= rx0 + 3; rx++) {
                if (!CityPlanner.isPlannable(rx, rz)) { continue; }
                for (CompoundSites.Compound c : world.urban.sites(rx, rz).compounds()) {
                    if (c.kind() == kind && (best == null || distSq(c, spawn) < distSq(best, spawn))) { best = c; }
                }
            }
        }
        if (best == null) { throw new IllegalStateException("no " + kind.label.toLowerCase(Locale.ROOT) + " within three regions of the spawn"); }
        return best;
    }

    private static long distSq(CompoundSites.Compound c, int[] spawn) {
        long dx = c.site().anchor().x() - spawn[0], dz = c.site().anchor().z() - spawn[1];
        return dx * dx + dz * dz;
    }

    private static Stronghold stronghold(World world, CompoundSites.Compound c) {
        CompoundSites.Gate gate = c.gate();
        ParcelPlan.Side out = gate.outward();
        // The porch's street end in the first leaf's column, then the street beyond it.
        Vec3i stand = gate.laneOutside();
        while (c.porch().contains(stand.x() + out.dx, stand.z() + out.dz)) { stand = stand.offset(out.dx, 0, out.dz); }
        Vec3i street = feet(world, stand.offset(out.dx * 2, 0, out.dz * 2));
        Vec3i threat = street;
        for (int k = 1; k <= THREAT_STEPS; k++) {
            Vec3i next = stand.offset(out.dx * (1 + k), 0, out.dz * (1 + k));
            if (!world.urban.surfaceAt(next.x(), next.z()).street) { break; }
            threat = feet(world, next);
        }
        Vec3i yard = gate.laneInside();
        for (int k = 2; k >= 1; k--) {
            Vec3i in = gate.laneInside().offset(-out.dx * k, 0, -out.dz * k);
            if (c.zoneAt(in.x(), in.y(), in.z()) == Zone.YARD) { yard = in; break; }
        }
        Vec3i[] clinic = null, barracks = null;
        double barracksScore = Double.MAX_VALUE;
        for (int i = 0; i < c.members().size(); i++) {
            ParcelPlan.Rect lot = c.lots().get(i);
            BuildingPlan b = world.urban.buildingAt(lot.x0(), lot.z0());
            if (b == null || b.id() != c.members().get(i)) { continue; }
            for (BuildingPlan.Fixture f : b.fixtures()) {
                if (f.kind() != BuildingPlan.FixtureKind.DOOR || f.target() == null || f.locked()) { continue; }
                Vec3i p = f.position(), t = f.target();
                if (p.y() != gate.laneInside().y() || !c.inYard(t.x(), t.z())) { continue; }
                Vec3i inside = new Vec3i(2 * p.x() - t.x(), p.y(), 2 * p.z() - t.z()), yardCell = new Vec3i(t.x(), p.y(), t.z());
                Zone zone = c.zoneAt(inside.x(), inside.y(), inside.z());
                if (b.id() == c.clinic() && zone == Zone.GUEST && clinic == null) {
                    clinic = new Vec3i[]{inside, yardCell};
                } else if (b.id() != c.clinic() && zone == Zone.RESTRICTED && !yardCell.equals(c.market())
                        && !c.posts().contains(yardCell)) {
                    double score = Double.MAX_VALUE;
                    for (Vec3i w : c.patrol()) { score = Math.min(score, Math.hypot(w.x() - yardCell.x(), w.z() - yardCell.z())); }
                    if (score < barracksScore) {
                        barracksScore = score;
                        barracks = new Vec3i[]{inside, yardCell};
                    }
                }
            }
        }
        if (clinic == null || barracks == null) {
            throw new IllegalStateException("the stronghold " + UrbanIds.format(c.site().id()) + " has no courtyard door to its "
                    + (clinic == null ? "clinic" : "barracks"));
        }
        return new Stronghold(c, street, stand, yard, threat, clinic[0], clinic[1], barracks[0], barracks[1]);
    }

    /**
     * The back way's street approach and the leaf of its street door, and its rear door into the
     * yard with the hall cell inside that door.
     */
    private static Raiders raiders(World world, CompoundSites.Compound c) {
        BuildingPlan back = world.urban.buildingAt(c.backLot().x0(), c.backLot().z0());
        if (back == null || back.id() != c.backWay() || back.approaches().isEmpty()) {
            throw new IllegalStateException("the raider yard " + UrbanIds.format(c.site().id()) + " has no back way");
        }
        Vec3i street = back.approaches().getFirst(), streetDoor = null, rearDoor = null, rearInside = null;
        for (BuildingPlan.Fixture f : back.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.DOOR || f.target() == null) { continue; }
            Vec3i p = f.position(), t = f.target();
            if (streetDoor == null && Math.abs(t.x() - street.x()) + Math.abs(t.z() - street.z()) <= 1) { streetDoor = p; }
            if (rearDoor == null && t.x() == c.backDoor().x() && t.z() == c.backDoor().z()) {
                rearDoor = p;
                rearInside = new Vec3i(2 * p.x() - t.x(), p.y(), 2 * p.z() - t.z());
            }
        }
        if (streetDoor == null || rearDoor == null) {
            throw new IllegalStateException("the raider yard's back way has no " + (streetDoor == null ? "street" : "rear") + " door");
        }
        return new Raiders(c, street, streetDoor, rearDoor, rearInside);
    }

    /**
     * A sidewalk corner of a region near the stronghold, at least {@link #AWAY} blocks from both
     * compounds: the corner where its first local streets cross, as the street scenes stand.
     */
    private static Vec3i away(World world, CompoundSites.Compound a, CompoundSites.Compound b) {
        Vec3i pa = a.site().anchor(), pb = b.site().anchor();
        int crx = StreetNetwork.regionOf(pa.x()), crz = StreetNetwork.regionOf(pa.z());
        Vec3i best = null;
        double bestD = Double.MAX_VALUE;
        for (int rz = crz - 2; rz <= crz + 2; rz++) {
            for (int rx = crx - 2; rx <= crx + 2; rx++) {
                if (!CityPlanner.isPlannable(rx, rz)) { continue; }
                DistrictPlan plan = world.urban.regionPlan(rx, rz);
                int x = plan.northSouthLine(1) + 2, z = plan.eastWestLine(1) + StreetNetwork.LOCAL_HALF_WIDTH + 1;
                double toA = Math.hypot(x - pa.x(), z - pa.z()), toB = Math.hypot(x - pb.x(), z - pb.z());
                if (toA < AWAY || toB < AWAY) { continue; }
                if (toA < bestD) {
                    bestD = toA;
                    best = feet(world, new Vec3i(x, 0, z));
                }
            }
        }
        if (best == null) { throw new IllegalStateException("no street far enough from both compounds"); }
        return best;
    }

    /** A street column's feet cell, on the walkable ground slab. */
    private static Vec3i feet(World world, Vec3i column) {
        return new Vec3i(column.x(), world.urban.groundY(column.x(), column.z()) + 1, column.z());
    }

    /** A cell a visitor's route may use: anything but the stronghold's restricted rooms. */
    boolean visitorMay(Vec3i cell) {
        return stronghold.compound().zoneAt(cell.x(), cell.y(), cell.z()) != Zone.RESTRICTED;
    }

    @Override
    public String toString() {
        CompoundSites.Compound s = stronghold.compound(), r = raiders.compound();
        return "stronghold=" + UrbanIds.format(s.site().id()) + " people=" + s.site().residents().size()
                + " gate=" + s.gate().first() + " outward=" + s.gate().outward() + " street=" + stronghold.street()
                + " sign=" + s.sign() + " yard=" + stronghold.yard() + " threat=" + stronghold.threat()
                + " clinic=" + stronghold.clinicInside() + " barracks=" + stronghold.barracksInside()
                + " patrol=" + s.patrol() + " raiders=" + UrbanIds.format(r.site().id()) + " people=" + r.site().residents().size()
                + " raiderGate=" + r.gate().first() + " backWay=" + raiders.street() + " rearDoor=" + raiders.rearDoor()
                + " away=" + away.x() + "," + away.z();
    }
}
