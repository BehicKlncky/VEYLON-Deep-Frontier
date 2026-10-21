package com.veylon;

import com.veylon.ai.InfectedAI;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.VoxelClearance;
import com.veylon.world.World;
import com.veylon.world.urban.CityPlanner;
import com.veylon.world.urban.EcologySites;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.StreetNetwork;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanIds;
import com.veylon.world.urban.UrbanSpecies;
import com.veylon.world.urban.UrbanSurface;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Where the milestone-15 ecology tour ({@code urban_ecology}) goes on a generator-14 world: the
 * two growth sites nearest the spawn, a park with its animals, a roof flock (seen from its
 * walk-up's roof) and a Breacher's street slot, with standing cells checked against the generated
 * blocks. Reads plans and loads the chunks it inspects; changes no block.
 */
record UrbanEcologyTourPlan(Site first, Site second, Park park, List<EncounterAnchors.Anchor> flock, Vec3i flockStand,
                            EncounterAnchors.Anchor breacher, Vec3i breacherStand) {
    private static final int FEET = UrbanDatum.STREET_Y + 1;
    private static final int SUPERBLOCKS = StreetNetwork.SUPERBLOCKS_PER_SIDE * StreetNetwork.SUPERBLOCKS_PER_SIDE;
    /** The crouched approach keeps this far from the folded body, beyond its touch. */
    static final float CLEARANCE = 2.6f;

    /**
     * A growth site and its Veilmaw's anchor. {@code stand} lies beyond the territory, far enough
     * that footsteps there are not heard, with the node in sight. {@code quiet} is a membrane cell
     * three blocks from the fold cell, reached crouched by a route that keeps
     * {@value #CLEARANCE} blocks from it; {@code torchFloor} is the floor beside the node a torch
     * goes on from there. {@code lureStand} lies just beyond the territory, from where a torch on
     * {@code lureFloor} lights the fold cell enough to draw the Veilmaw.
     */
    record Site(EcologySites.GrowthSite site, EncounterAnchors.Anchor anchor, Vec3i stand, Vec3i quiet,
                Vec3i torchFloor, Vec3i lureStand, Vec3i lureFloor) {
        /** Route cells the crouched approach may use. */
        Predicate<Vec3i> clear() {
            Vec3i rest = site.rest();
            return c -> Math.hypot(c.x() - rest.x(), c.z() - rest.z()) >= CLEARANCE;
        }
    }

    /** A park's animals and a path cell to watch them from. */
    record Park(List<EncounterAnchors.Anchor> animals, Vec3i stand, Vec3i toward) { }

    static UrbanEcologyTourPlan of(World world) {
        if (world.urban == null || !world.urban.hasEcology()) {
            throw new IllegalStateException("urban_ecology needs generator 14 (VEYLON_GENERATOR=urban-dev)");
        }
        List<EncounterAnchors.Anchor> veilmaws = new ArrayList<>(), hares = new ArrayList<>(), flocks = new ArrayList<>(),
                breachers = new ArrayList<>();
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                if (!CityPlanner.isPlannable(rx, rz)) { continue; }
                for (int sb = 0; sb < SUPERBLOCKS; sb++) {
                    for (var a : EncounterAnchors.forEcology(world, rx, rz, sb)) {
                        if (a.species() == UrbanSpecies.VEILMAW) { veilmaws.add(a); }
                        if (a.species() == UrbanSpecies.MURKHARE
                                && world.urban.surfaceAt(a.cell().x(), a.cell().z()) == UrbanSurface.PARK) { hares.add(a); }
                        if (a.species() == UrbanSpecies.SKITTERWING && UrbanIds.roomIndex(a.id()) == EcologySites.ROOM_ROOF
                                && UrbanIds.slotIndex(a.id()) == 0) { flocks.add(a); }
                    }
                }
                for (var parcel : world.urban.regionPlan(rx, rz).parcels()) {
                    for (var a : EncounterAnchors.forParcel(world, parcel)) {
                        if (a.species() == UrbanSpecies.BREACHER) { breachers.add(a); }
                    }
                }
            }
        }
        Comparator<EncounterAnchors.Anchor> nearest = Comparator.comparingDouble(a -> spawnDistance(world, a.cell()));
        veilmaws.sort(nearest);
        hares.sort(nearest);
        flocks.sort(nearest);
        breachers.sort(nearest);
        List<Site> sites = new ArrayList<>();
        for (var a : veilmaws) {
            Site s = site(world, a);
            if (s != null) { sites.add(s); }
            if (sites.size() == 2) { break; }
        }
        if (sites.size() < 2) { throw new IllegalStateException("two growth sites near the spawn: " + sites.size()); }
        Park park = null;
        for (var hare : hares) {
            park = park(world, hare);
            if (park != null) { break; }
        }
        if (park == null) { throw new IllegalStateException("no park with hares near the spawn"); }
        List<EncounterAnchors.Anchor> flock = null;
        Vec3i flockStand = null;
        for (var f : flocks) {
            flockStand = roof(world, f.cell());
            if (flockStand != null) { flock = birds(world, f); break; }
        }
        if (flock == null) { throw new IllegalStateException("no roof flock near the spawn"); }
        EncounterAnchors.Anchor breacher = null;
        Vec3i breacherStand = null;
        for (var b : breachers) {
            breacherStand = street(world, b.cell(), 5, 8);
            if (breacherStand != null) { breacher = b; break; }
        }
        if (breacher == null) { throw new IllegalStateException("no Breacher near the spawn"); }
        return new UrbanEcologyTourPlan(sites.get(0), sites.get(1), park, flock, flockStand, breacher, breacherStand);
    }

    private static double spawnDistance(World world, Vec3i c) {
        int[] spawn = world.urban.spawnColumn();
        return Math.hypot(c.x() - spawn[0], c.z() - spawn[1]);
    }

    private static double fromNode(Vec3i node, int x, int z) {
        return Math.hypot(x - node.x(), z - node.z());
    }

    private static Site site(World world, EncounterAnchors.Anchor a) {
        EcologySites.GrowthSite site = findSite(world, a);
        if (site == null) { return null; }
        Vec3i node = site.node(), rest = site.rest();
        world.ensureChunks(node.x(), node.z(), 2, 10_000);
        // The stand: beyond the territory and its two-block hearing margin, the node in sight.
        List<Vec3i> stands = new ArrayList<>();
        for (int z = node.z() - 15; z <= node.z() + 15; z++) {
            for (int x = node.x() - 15; x <= node.x() + 15; x++) {
                double d = fromNode(node, x, z);
                Vec3i c = new Vec3i(x, FEET, z);
                if (d >= 11.6 && d <= 14.5 && standable(world, c) && !membrane(world, c) && sees(world, c, node.offset(0, 1, 0))) {
                    stands.add(c);
                }
            }
        }
        stands.sort(Comparator.comparingDouble(c -> fromNode(node, c.x(), c.z())));
        // Quiet cells beside the fold cell or beyond it, away from the node, come first: from there
        // the node is never in front of the body, all the way through the telegraph leg.
        List<Vec3i> away = new ArrayList<>(), others = new ArrayList<>();
        for (Vec3i m : site.membrane()) {
            Vec3i c = new Vec3i(m.x(), FEET, m.z());
            double fromRest = Math.hypot(c.x() - rest.x(), c.z() - rest.z());
            if (fromRest < 3.0 || fromRest > 3.7 || !standable(world, c) || !InfectedAI.clearSight(world, c.x() + .5f,
                    FEET + 1.25f, c.z() + .5f, rest.x() + .5f, FEET + .45f, rest.z() + .5f)) {
                continue;
            }
            double cos = ((c.x() - rest.x()) * (rest.x() - node.x()) + (c.z() - rest.z()) * (rest.z() - node.z())) / fromRest;
            (cos >= -0.2 ? away : others).add(c);
        }
        Site approach = new Site(site, a, null, null, null, null, null);
        Vec3i stand = null, quiet = null, torchFloor = null;
        search:
        for (List<Vec3i> quiets : List.of(away, others)) {
            for (Vec3i s : stands.subList(0, Math.min(8, stands.size()))) {
                List<Vec3i> byStand = new ArrayList<>(quiets);
                byStand.sort(Comparator.comparingDouble(q -> Math.hypot(q.x() - s.x(), q.z() - s.z())));
                for (Vec3i q : byStand) {
                    Vec3i floor = torchFloor(world, node, rest, q);
                    if (floor != null && UrbanSlicePlan.route(world, s, q, approach.clear()) != null) {
                        stand = s;
                        quiet = q;
                        torchFloor = floor;
                        break search;
                    }
                }
            }
        }
        if (stand == null) { return null; }
        Vec3i[] lure = lure(world, site);
        if (lure == null) { return null; }
        return new Site(site, a, stand, quiet, torchFloor, lure[0], lure[1]);
    }

    /**
     * The floor beside the node, other than the fold cell's, nearest the quiet cell that a
     * crouched player there can reach and sees the top of.
     */
    private static Vec3i torchFloor(World world, Vec3i node, Vec3i rest, Vec3i quiet) {
        Vec3i best = null;
        double bestD = Double.MAX_VALUE;
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Vec3i c = new Vec3i(node.x() + d[0], FEET, node.z() + d[1]);
            if (c.x() == rest.x() && c.z() == rest.z() || world.getBlock(c.x(), FEET, c.z()) != BlockType.AIR
                    || !world.getBlock(c.x(), FEET - 1, c.z()).solid) { continue; }
            double dist = Math.hypot(c.x() - quiet.x(), c.z() - quiet.z());
            if (dist < bestD && dist <= 4.3 && InfectedAI.clearSight(world, quiet.x() + .5f, FEET + 1.2f, quiet.z() + .5f,
                    c.x() + .5f, FEET + .02f, c.z() + .5f)) {
                bestD = dist;
                best = new Vec3i(c.x(), FEET - 1, c.z());
            }
        }
        return best;
    }

    /**
     * A stand just beyond the territory (more than a block outside it, so a lit player there is
     * not sensed) and a floor four cells nearer the node whose torch lights the fold cell to the
     * lure threshold, in the Veilmaw's sight and inside its ground.
     */
    private static Vec3i[] lure(World world, EcologySites.GrowthSite site) {
        Vec3i node = site.node(), rest = site.rest();
        Vec3i[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int z = node.z() - 12; z <= node.z() + 12; z++) {
            for (int x = node.x() - 12; x <= node.x() + 12; x++) {
                double d = fromNode(node, x, z);
                Vec3i s = new Vec3i(x, FEET, z);
                if (d < 10.4 || d > 11.4 || !standable(world, s) || !sees(world, s, node.offset(0, 1, 0))) { continue; }
                double ux = (node.x() - x) / d, uz = (node.z() - z) / d;
                Vec3i t = new Vec3i((int) Math.round(x + ux * 4), FEET, (int) Math.round(z + uz * 4));
                double toRest = Math.sqrt(Math.pow(t.x() - rest.x(), 2) + Math.pow(t.z() - rest.z(), 2));
                if (toRest > 6.2 || fromNode(node, t.x(), t.z()) > 8.0 || fromNode(node, t.x(), t.z()) < 3
                        || world.getBlock(t.x(), FEET, t.z()) != BlockType.AIR || !world.getBlock(t.x(), FEET - 1, t.z()).solid
                        || !InfectedAI.clearSight(world, rest.x() + .5f, FEET + .8f, rest.z() + .5f, t.x() + .5f, FEET + .5f, t.z() + .5f)
                        || !InfectedAI.clearSight(world, x + .5f, FEET + 1.6f, z + .5f, t.x() + .5f, FEET + .05f, t.z() + .5f)) {
                    continue;
                }
                if (toRest < bestScore) {
                    bestScore = toRest;
                    best = new Vec3i[]{s, new Vec3i(t.x(), FEET - 1, t.z())};
                }
            }
        }
        return best;
    }

    private static EcologySites.GrowthSite findSite(World world, EncounterAnchors.Anchor a) {
        for (var site : world.urban.ecology(UrbanIds.regionX(a.id()), UrbanIds.regionZ(a.id())).growthSites()) {
            if (site.encounterId() == a.id()) { return site; }
        }
        return null;
    }

    private static Park park(World world, EncounterAnchors.Anchor hare) {
        world.ensureChunks(hare.cell().x(), hare.cell().z(), 3, 10_000);
        List<EncounterAnchors.Anchor> animals = new ArrayList<>();
        int superblock = UrbanIds.parcelIndex(hare.id()) / ParcelPlan.SLOTS_PER_SUPERBLOCK;
        for (var a : EncounterAnchors.forEcology(world, UrbanIds.regionX(hare.id()), UrbanIds.regionZ(hare.id()), superblock)) {
            if (a.species().wildlife()) { animals.add(a); }
        }
        Vec3i c = hare.cell();
        Vec3i best = null;
        double bestD = Double.MAX_VALUE;
        for (int z = c.z() - 12; z <= c.z() + 12; z++) {
            for (int x = c.x() - 12; x <= c.x() + 12; x++) {
                double d = Math.hypot(x - c.x(), z - c.z());
                Vec3i s = new Vec3i(x, FEET, z);
                if (d >= 9 && d <= 12 && d < bestD && world.urban.surfaceAt(x, z) == UrbanSurface.PARK_PATH
                        && standable(world, s) && sees(world, s, c)) {
                    bestD = d;
                    best = s;
                }
            }
        }
        return best == null ? null : new Park(animals, best, c);
    }

    /**
     * The walk-up's own roof under a flock: its roof reservation, out in the open beyond the stair
     * bulkhead, the only place the flock is in plain sight from, since the building hides its roof
     * from the street.
     */
    private static Vec3i roof(World world, Vec3i bird) {
        world.ensureChunks(bird.x(), bird.z(), 2, 10_000);
        var building = world.urban.buildingAt(bird.x(), bird.z());
        if (building == null) { return null; }
        try {
            Vec3i roof = UrbanUndergroundTourPlan.roofCell(building);
            return standable(world, roof) ? roof : null;
        } catch (IllegalStateException noReservation) {
            return null;
        }
    }

    /** Both birds of a roof flock. */
    private static List<EncounterAnchors.Anchor> birds(World world, EncounterAnchors.Anchor first) {
        int superblock = UrbanIds.parcelIndex(first.id()) / ParcelPlan.SLOTS_PER_SUPERBLOCK;
        List<EncounterAnchors.Anchor> out = new ArrayList<>();
        for (var a : EncounterAnchors.forEcology(world, UrbanIds.regionX(first.id()), UrbanIds.regionZ(first.id()), superblock)) {
            if (a.species() == UrbanSpecies.SKITTERWING && UrbanIds.roomIndex(a.id()) == EcologySites.ROOM_ROOF) { out.add(a); }
        }
        return out;
    }

    /** The nearest street or sidewalk cell between {@code min} and {@code max} blocks from a column. */
    private static Vec3i street(World world, Vec3i c, double min, double max) {
        world.ensureChunks(c.x(), c.z(), 2, 10_000);
        Vec3i best = null;
        double bestD = Double.MAX_VALUE;
        int r = (int) Math.ceil(max);
        for (int z = c.z() - r; z <= c.z() + r; z++) {
            for (int x = c.x() - r; x <= c.x() + r; x++) {
                double d = Math.hypot(x - c.x(), z - c.z());
                UrbanSurface s = world.urban.surfaceAt(x, z);
                Vec3i cell = new Vec3i(x, FEET, z);
                if (d >= min && d <= max && d < bestD && (s == UrbanSurface.SIDEWALK || s == UrbanSurface.ROADWAY)
                        && standable(world, cell)) {
                    bestD = d;
                    best = cell;
                }
            }
        }
        return best;
    }

    private static boolean standable(World world, Vec3i c) {
        return VoxelClearance.standable(world, c.x(), c.y(), c.z());
    }

    private static boolean membrane(World world, Vec3i feet) {
        return world.getBlock(feet.x(), feet.y() - 1, feet.z()) == BlockType.VEIL_MEMBRANE;
    }

    /** Clear sight from a standing eye to the top of a cell at feet height. */
    private static boolean sees(World world, Vec3i from, Vec3i to) {
        return InfectedAI.clearSight(world, from.x() + .5f, from.y() + 1.6f, from.z() + .5f, to.x() + .5f, to.y() + .6f, to.z() + .5f);
    }

    @Override
    public String toString() {
        return "first=" + describe(first) + " second=" + describe(second) + " park=" + park.toward() + " stand=" + park.stand()
                + " animals=" + park.animals().size() + " flock=" + flock.getFirst().cell() + " flockStand=" + flockStand
                + " breacher=" + breacher.cell() + " breacherStand=" + breacherStand;
    }

    private static String describe(Site s) {
        return "{id=" + UrbanIds.format(s.anchor().id()) + " node=" + s.site().node() + " rest=" + s.site().rest()
                + " membrane=" + s.site().membrane().size() + " stand=" + s.stand() + " quiet=" + s.quiet()
                + " torchFloor=" + s.torchFloor() + " lureStand=" + s.lureStand() + " lureFloor=" + s.lureFloor() + "}";
    }
}
