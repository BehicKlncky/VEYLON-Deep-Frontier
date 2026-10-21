package com.veylon.world;

import com.veylon.util.Vec3i;
import com.veylon.world.urban.DistrictPlan;
import com.veylon.world.urban.EcologySites;
import com.veylon.world.urban.RouteProof;
import com.veylon.world.urban.UrbanDatum;
import com.veylon.world.urban.UrbanDistrict;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Generator 14 (milestone 15) as actually generated: each quarantine growth site's node and
 * membrane carpet are stamped from the plan, in any chunk order, over open ground a body still
 * walks across; generator 13 keeps the same city without them, and wildlife stamps nothing.
 */
class UrbanEcologyGenerationTest {
    private static final long SEED = 20_260_731L;

    /** The first growth site near the spawn, with its region. */
    private record Found(int rx, int rz, EcologySites.GrowthSite site) { }

    private static Found firstSite(World w) {
        for (int rz = -3; rz <= 3; rz++) {
            for (int rx = -3; rx <= 3; rx++) {
                if (w.urban.regionPlan(rx, rz).district == UrbanDistrict.QUARANTINE
                        && !w.urban.ecology(rx, rz).growthSites().isEmpty()) {
                    return new Found(rx, rz, w.urban.ecology(rx, rz).growthSites().getFirst());
                }
            }
        }
        throw new AssertionError("no quarantine growth site near the spawn");
    }

    /** Generates the chunks around a site in the given order. */
    private static void generate(World w, Vec3i node, List<int[]> order) {
        for (int[] c : order) {
            w.getOrCreateChunk(Math.floorDiv(node.x(), 16) + c[0], Math.floorDiv(node.z(), 16) + c[1]);
        }
    }

    private static List<int[]> square() {
        List<int[]> out = new ArrayList<>();
        for (int dz = -2; dz <= 2; dz++) {
            for (int dx = -2; dx <= 2; dx++) { out.add(new int[]{dx, dz}); }
        }
        return out;
    }

    @Test
    void theNodeAndItsCarpetAreStampedFromThePlanInAnyChunkOrder() {
        World a = new World(SEED, World.GEN_URBAN_ECOLOGY);
        Found found = firstSite(a);
        EcologySites.GrowthSite site = found.site();
        List<int[]> forward = square();
        generate(a, site.node(), forward);
        assertSame(BlockType.GROWTH_NODE, a.getBlock(site.node().x(), site.node().y(), site.node().z()));
        for (Vec3i m : site.membrane()) {
            assertSame(BlockType.VEIL_MEMBRANE, a.getBlock(m.x(), m.y(), m.z()), "carpet at " + m);
            if (m.x() != site.node().x() || m.z() != site.node().z()) {
                BlockType above = a.getBlock(m.x(), m.y() + 1, m.z());
                assertFalse(above.solid, "nothing stands on the carpet but the node: " + above + " at " + m);
                assertNotSame(BlockType.TALL_GRASS, above);
                assertNotSame(BlockType.BUSH, above);
            }
        }
        // Reverse and shuffled orders stamp the same blocks.
        for (long shuffle : new long[]{-1, 11, 12}) {
            World b = new World(SEED, World.GEN_URBAN_ECOLOGY);
            List<int[]> order = new ArrayList<>(forward);
            if (shuffle < 0) { Collections.reverse(order); } else { Collections.shuffle(order, new Random(shuffle)); }
            generate(b, site.node(), order);
            for (int z = site.node().z() - 10; z <= site.node().z() + 10; z++) {
                for (int x = site.node().x() - 10; x <= site.node().x() + 10; x++) {
                    for (int y = UrbanDatum.STREET_Y - 1; y <= UrbanDatum.STREET_Y + 3; y++) {
                        assertSame(a.getBlock(x, y, z), b.getBlock(x, y, z), "order " + shuffle + " at " + x + "," + y + "," + z);
                    }
                }
            }
        }
    }

    @Test
    void theCourtyardIsStillWalkedAroundTheNodeAndGeneratorThirteenHasNoGrowth() {
        World w = new World(SEED, World.GEN_URBAN_ECOLOGY);
        Found found = firstSite(w);
        EcologySites.GrowthSite site = found.site();
        generate(w, site.node(), square());
        // A body crosses the carpet from one side of the node to the other.
        Vec3i from = new Vec3i(site.node().x() - 2, UrbanDatum.STREET_Y + 1, site.node().z());
        Vec3i to = new Vec3i(site.node().x() + 2, UrbanDatum.STREET_Y + 1, site.node().z());
        var box = new RouteProof.Box(site.node().x() - 6, UrbanDatum.STREET_Y - 1, site.node().z() - 6,
                site.node().x() + 6, UrbanDatum.STREET_Y + 4, site.node().z() + 6);
        var proof = RouteProof.prove(w, box, List.of(from));
        assertTrue(proof.reached(to), "the carpet is ground: a body walks round the node");

        World before = new World(SEED, World.GEN_URBAN_UNDERGROUND);
        generate(before, site.node(), square());
        assertNotSame(BlockType.GROWTH_NODE, before.getBlock(site.node().x(), site.node().y(), site.node().z()));
        for (Vec3i m : site.membrane()) {
            assertNotSame(BlockType.VEIL_MEMBRANE, before.getBlock(m.x(), m.y(), m.z()), "generator 13 is unchanged");
        }
        // Outside the quarantine the two generators stamp the same city: wildlife stamps nothing.
        DistrictPlan spawn = w.urban.regionPlan(0, 0);
        World w13 = new World(SEED, World.GEN_URBAN_UNDERGROUND);
        for (int cz = 0; cz < 4; cz++) {
            for (int cx = 0; cx < 4; cx++) {
                Chunk c14 = w.getOrCreateChunk(spawn.rx * 16 + cx, spawn.rz * 16 + cz);
                Chunk c13 = w13.getOrCreateChunk(spawn.rx * 16 + cx, spawn.rz * 16 + cz);
                for (int y = 0; y < Chunk.SY; y++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int lx = 0; lx < Chunk.SX; lx++) {
                            assertSame(c13.get(lx, y, lz), c14.get(lx, y, lz));
                        }
                    }
                }
            }
        }
    }
}
