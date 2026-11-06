package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingFurnishing;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.CommsComponents;
import com.veylon.world.urban.CompoundSites;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.RelaySites;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanIds;

/**
 * The places of the milestone-20 mission tour ({@link UrbanMissionQaScene}), read from a generated
 * generator-19 world's plans before anything is staged: the camp nearest the spawn and where its
 * people are met, the stronghold nearest the spawn, the street before its gate and the restricted
 * container its transmitter module is kept in, the nearest warehouse tool chest and office utility
 * locker that keep the relay's other parts, the relay itself, and the starter refuge's workbench and
 * the cells its generator and radio go on, where the utility tour ({@link UrbanUtilityTourPlan}) put
 * its generator and kitchen lamp. The hidden refuge is whichever the camp's mission names, read when
 * it is given.
 */
record UrbanMissionTourPlan(SurvivorSites.Site camp, CompoundSites.Compound stronghold, Vec3i gateStreet,
                            BuildingPlan.Fixture module, BuildingPlan.Fixture cells, BuildingPlan.Fixture cable,
                            RelaySites.Site relay, BuildingPlan refuge, BuildingPlan.Fixture bench,
                            Vec3i generator, Vec3i generatorFrom, Vec3i radio, Vec3i radioFrom) {

    static UrbanMissionTourPlan of(World world) {
        if (world.urban == null || !world.urban.hasMissions()) {
            throw new IllegalStateException("urban_missions needs a generator-19 world (VEYLON_GENERATOR=urban-dev)");
        }
        int[] spawn = world.urban.spawnColumn();
        SurvivorSites.Site camp = null;
        CompoundSites.Compound stronghold = null;
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                var sites = world.urban.sites(rx, rz);
                if (Math.abs(rx) <= 2 && Math.abs(rz) <= 2 && sites.camp() != null
                        && (camp == null || distSq(sites.camp().anchor(), spawn) < distSq(camp.anchor(), spawn))) {
                    camp = sites.camp();
                }
                if (sites.stronghold() != null && (stronghold == null
                        || distSq(sites.stronghold().site().anchor(), spawn) < distSq(stronghold.site().anchor(), spawn))) {
                    stronghold = sites.stronghold();
                }
            }
        }
        if (camp == null || stronghold == null) { throw new IllegalStateException("no camp or stronghold near the spawn"); }
        RelaySites.Site relay = world.urban.relay();
        if (relay == null) { throw new IllegalStateException("this world has no relay"); }
        // The starter refuge keeps generator 18's geometry: the generator where the utility tour put it, the
        // radio where that tour's kitchen lamp stood, each put down from the same cell.
        UrbanUtilityTourPlan home = UrbanUtilityTourPlan.of(world);
        return new UrbanMissionTourPlan(camp, stronghold, gateStreet(world, stronghold), moduleShelf(world, stronghold),
                source(world, BuildingPlan.Family.WAREHOUSE, BuildingFurnishing.ContainerUse.TOOL_CHEST),
                source(world, BuildingPlan.Family.OFFICE, BuildingFurnishing.ContainerUse.UTILITY_LOCKER),
                relay, home.refuge(), home.bench(), home.generator(), home.generatorFrom(), home.kitchenLamp(),
                home.kitchenLampFrom());
    }

    /** The street two cells beyond a stronghold's porch, in line with its gate. */
    private static Vec3i gateStreet(World world, CompoundSites.Compound c) {
        CompoundSites.Gate gate = c.gate();
        ParcelPlan.Side out = gate.outward();
        Vec3i stand = gate.laneOutside();
        while (c.porch().contains(stand.x() + out.dx, stand.z() + out.dz)) { stand = stand.offset(out.dx, 0, out.dz); }
        return stand.offset(out.dx * 2, 0, out.dz * 2);
    }

    /** The planned container a stronghold keeps its transmitter module in. */
    private static BuildingPlan.Fixture moduleShelf(World world, CompoundSites.Compound c) {
        Vec3i cell = CommsComponents.strongholdModuleCell(world.urban, c);
        if (cell == null) { throw new IllegalStateException("the stronghold keeps no module"); }
        BuildingPlan holder = world.urban.buildingAt(cell.x(), cell.z());
        return holder.fixtures().stream().filter(f -> f.position().equals(cell)).findFirst().orElseThrow();
    }

    /**
     * The first container of a use, below the roof, in the unheld building of a family nearest the
     * spawn that is not picked clean and no compound holds, where the relay's part lies.
     */
    private static BuildingPlan.Fixture source(World world, BuildingPlan.Family family, BuildingFurnishing.ContainerUse use) {
        BuildingPlan.Fixture best = null;
        double bestD = Double.MAX_VALUE;
        for (int rz = -2; rz <= 2; rz++) {
            for (int rx = -2; rx <= 2; rx++) {
                for (ParcelPlan p : world.urban.regionPlan(rx, rz).parcels()) {
                    BuildingPlan b = world.urban.buildingAt(p.x0(), p.z0());
                    if (b == null || b.family() != family || b.variant() == BuildingPlan.Variant.OCCUPIED
                            || b.variant() == BuildingPlan.Variant.PICKED_CLEAN || SurvivorSites.owner(world.urban, b.id()) != null) {
                        continue;
                    }
                    for (BuildingPlan.Fixture f : b.fixtures()) {
                        if (f.kind() == BuildingPlan.FixtureKind.CONTAINER && BuildingFurnishing.containerUse(b, f) == use
                                && UrbanIds.floorIndex(f.id()) != b.storeys() && CommsComponents.sourceOf(world.urban, b, f) != null) {
                            double d = (double) p.x0() * p.x0() + (double) p.z0() * p.z0();
                            if (d < bestD) {
                                bestD = d;
                                best = f;
                            }
                        }
                    }
                }
            }
        }
        if (best == null) { throw new IllegalStateException("no " + family + " " + use + " keeps a part near the spawn"); }
        return best;
    }

    private static long distSq(Vec3i a, int[] spawn) {
        long dx = a.x() - spawn[0], dz = a.z() - spawn[1];
        return dx * dx + dz * dz;
    }

    @Override
    public String toString() {
        return "camp=" + UrbanIds.format(camp.id()) + " entry=" + camp.entry() + " stronghold=" + UrbanIds.format(stronghold.site().id())
                + " street=" + gateStreet + " module=" + module.position() + " from " + module.target()
                + " cells=" + cells.position() + " cable=" + cable.position() + " relay=" + UrbanIds.format(relay.building())
                + " cabinet=" + relay.relay() + " stand=" + relay.stand() + " refuge=" + UrbanIds.format(refuge.id())
                + " bench=" + bench.position() + " generator=" + generator + " from " + generatorFrom + " radio=" + radio
                + " from " + radioFrom;
    }
}
