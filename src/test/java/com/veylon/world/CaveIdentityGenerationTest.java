package com.veylon.world;

import com.veylon.Game;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Production evidence for the distinct Root- and Basalt-depth identities.
 */
class CaveIdentityGenerationTest {

    private static final long[] REQUIRED_SEEDS = {
            1L, 42L, 999L, -1234567L, 20260716L, 987654321L
    };

    @Test
    void requiredSeedsGenerateRootLifeAndBasaltHazardsAtNegativeCoordinates() {
        for (long seed : REQUIRED_SEEDS) {
            World world = new World(seed, World.CURRENT_GENERATOR);
            for (int cx = -4; cx <= 4; cx++) {
                for (int cz = -4; cz <= 4; cz++) {
                    world.getOrCreateChunk(cx, cz);
                }
            }

            IdentityCounts all = countIdentities(world, -4, 4, -4, 4);
            IdentityCounts negative = countIdentities(world, -4, -1, -4, -1);
            assertTrue(all.rootPatches > 0,
                    "seed " + seed + " must expose roots and dirt in Root Caves");
            assertTrue(all.seepages > 0,
                    "seed " + seed + " must contain Root-Cave water seepage");
            assertTrue(all.rootDens > 0,
                    "seed " + seed + " must contain discoverable underground animal dens");
            assertTrue(all.fumaroles > 0,
                    "seed " + seed + " must contain real Basalt smoke hazards");
            assertTrue(all.verticalShafts > 0,
                    "seed " + seed + " must contain explicit Basalt vertical shafts");
            assertTrue(negative.rootPatches > 0 && negative.fumaroles > 0,
                    "seed " + seed + " must retain both identities at negative coordinates");

            int stalkerNests = 0;
            for (int rx = -2; rx <= 2; rx++) {
                for (int rz = -2; rz <= 2; rz++) {
                    for (Poi poi : world.generator.plannedUndergroundPois(rx, rz)) {
                        if (poi.type != Poi.PoiType.STALKER_NEST) {
                            continue;
                        }
                        stalkerNests++;
                        assertEquals(WorldGenerator.CaveZone.BASALT,
                                world.generator.caveZoneAt(
                                        poi.pos.x(), poi.pos.y(), poi.pos.z()),
                                "strong predator nest escaped the Basalt band for seed " + seed);
                    }
                }
            }
            assertTrue(stalkerNests > 0,
                    "seed " + seed + " must plan stronger Basalt predators");
        }
    }

    @Test
    void caveIdentityBlocksAndPoiTagsIgnoreReversedChunkGenerationOrder() {
        long seed = -1234567L;
        List<int[]> chunks = new ArrayList<>();
        for (int cx = -3; cx <= 3; cx++) {
            for (int cz = -3; cz <= 3; cz++) {
                chunks.add(new int[]{cx, cz});
            }
        }
        World forward = new World(seed, World.CURRENT_GENERATOR);
        World reverse = new World(seed, World.CURRENT_GENERATOR);
        for (int[] chunk : chunks) {
            forward.getOrCreateChunk(chunk[0], chunk[1]);
        }
        for (int i = chunks.size() - 1; i >= 0; i--) {
            int[] chunk = chunks.get(i);
            reverse.getOrCreateChunk(chunk[0], chunk[1]);
        }

        for (int[] pos : chunks) {
            Chunk a = forward.getChunk(pos[0], pos[1]);
            Chunk b = reverse.getChunk(pos[0], pos[1]);
            for (int lx = 0; lx < Chunk.SX; lx++) {
                for (int lz = 0; lz < Chunk.SZ; lz++) {
                    for (int y = 3; y < Chunk.SY; y++) {
                        assertEquals(a.get(lx, y, lz), b.get(lx, y, lz),
                                "reverse order changed cave identity cell "
                                        + pos[0] + "," + pos[1] + ":"
                                        + lx + "," + y + "," + lz);
                    }
                }
            }
        }
        assertEquals(identityPoiTags(forward), identityPoiTags(reverse),
                "derived den/nest identities must be order independent");
    }

    @Test
    void generatedFumaroleHazardCanBeMinedAndStaysRemovedAfterSaveLoad(
            @TempDir Path tempDir) {
        Game game = new Game();
        game.newWorld(20260716L, true);
        Vec3i vent = null;
        for (int radius = 0; radius <= 6 && vent == null; radius++) {
            for (int cx = -radius; cx <= radius && vent == null; cx++) {
                for (int cz = -radius; cz <= radius && vent == null; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != radius) {
                        continue;
                    }
                    game.world.getOrCreateChunk(cx, cz);
                    vent = firstFumarole(game.world, cx, cx, cz, cz);
                }
            }
        }
        assertNotNull(vent, "fixed-seed gameplay search must find a generated fumarole");

        game.player.pos.set(vent.x() + 0.5f, vent.y() + 0.1f, vent.z() + 0.5f);
        game.player.smokeExposure = 0f;
        int particlesBefore = game.particles.count;
        game.mediumTick(1f);

        assertTrue(game.player.smokeExposure >= 13.5f,
                "the production environmental tick must apply the vent hazard");
        assertTrue(game.particles.count > particlesBefore,
                "the smoky hazard needs visible feedback, not an invisible counter");

        Vec3i sulfurSource = vent.offset(0, -1, 0);
        game.player.inventory.set(0, new ItemStack(ItemType.IRON_PICKAXE, 1));
        game.player.hotbarSel = 0;
        assertTrue(game.completePlayerBlockBreak(sulfurSource),
                "the ordinary mining command must remove the exposed sulfur source");
        assertFalse(game.world.isBasaltFumarole(vent.x(), vent.y(), vent.z()),
                "mining the source must immediately stop the local hazard");

        Path save = tempDir.resolve("mined-fumarole.sav");
        assertTrue(SaveSystem.save(game, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(World.CURRENT_GENERATOR, loaded.world.generatorVersion);
        assertEquals(BlockType.AIR, loaded.world.getBlock(
                sulfurSource.x(), sulfurSource.y(), sulfurSource.z()));
        assertFalse(loaded.world.isBasaltFumarole(vent.x(), vent.y(), vent.z()),
                "changed-block persistence must not regenerate the mined hazard");
        assertTrue(loaded.player.smokeExposure >= 13.5f,
                "exposure state already in flight must also round-trip");
    }

    private static IdentityCounts countIdentities(World world, int minCx, int maxCx,
                                                   int minCz, int maxCz) {
        int roots = 0;
        int seepages = 0;
        int fumaroles = 0;
        int shafts = 0;
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                Chunk chunk = world.getChunk(cx, cz);
                if (chunk == null) {
                    continue;
                }
                int baseX = cx * Chunk.SX;
                int baseZ = cz * Chunk.SZ;
                for (int lx = 1; lx < Chunk.SX - 1; lx++) {
                    for (int lz = 1; lz < Chunk.SZ - 1; lz++) {
                        int surface = world.generator.heightAt(baseX + lx, baseZ + lz);
                        for (int y = 4; y < Chunk.SY - 2; y++) {
                            int depth = surface - y;
                            if (depth >= 5 && depth < WorldGenerator.ROOT_DEPTH
                                    && chunk.get(lx, y - 1, lz) == BlockType.LOG
                                    && adjacentFloor(chunk, lx, y, lz, BlockType.DIRT)
                                    && adjacentFloor(chunk, lx, y, lz, BlockType.WATER)) {
                                roots++;
                            }
                            if (chunk.get(lx, y, lz) == BlockType.WATER
                                    && world.generator.caveZoneAt(
                                    baseX + lx, y + 1, baseZ + lz)
                                    == WorldGenerator.CaveZone.ROOT
                                    && containedWater(chunk, lx, y, lz)) {
                                seepages++;
                            }
                            if (world.isBasaltFumarole(baseX + lx, y, baseZ + lz)) {
                                fumaroles++;
                            }
                            if (isShaftBase(world, chunk, baseX, baseZ, lx, y, lz)) {
                                shafts++;
                            }
                        }
                    }
                }
            }
        }
        int dens = 0;
        for (Poi poi : world.pois) {
            if (poi.type != Poi.PoiType.PREDATOR_DEN
                    || Math.floorDiv(poi.pos.x(), Chunk.SX) < minCx
                    || Math.floorDiv(poi.pos.x(), Chunk.SX) > maxCx
                    || Math.floorDiv(poi.pos.z(), Chunk.SZ) < minCz
                    || Math.floorDiv(poi.pos.z(), Chunk.SZ) > maxCz) {
                continue;
            }
            int depth = world.generator.heightAt(poi.pos.x(), poi.pos.z()) - poi.pos.y();
            if (depth >= 5 && depth < WorldGenerator.ROOT_DEPTH
                    && world.getBlock(poi.pos.x(), poi.pos.y(), poi.pos.z())
                    == BlockType.BONE_PILE) {
                dens++;
            }
        }
        return new IdentityCounts(roots, seepages, dens, fumaroles, shafts);
    }

    private static Vec3i firstFumarole(World world, int minCx, int maxCx,
                                       int minCz, int maxCz) {
        for (int cx = minCx; cx <= maxCx; cx++) {
            for (int cz = minCz; cz <= maxCz; cz++) {
                for (int lx = 1; lx < Chunk.SX - 1; lx++) {
                    for (int lz = 1; lz < Chunk.SZ - 1; lz++) {
                        for (int y = 4; y < Chunk.SY - 2; y++) {
                            int x = cx * Chunk.SX + lx;
                            int z = cz * Chunk.SZ + lz;
                            if (world.isBasaltFumarole(x, y, z)) {
                                return new Vec3i(x, y, z);
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private static boolean adjacentFloor(Chunk chunk, int lx, int y, int lz,
                                         BlockType expected) {
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (chunk.get(lx + dir[0], y - 1, lz + dir[1]) == expected) {
                return true;
            }
        }
        return false;
    }

    private static boolean containedWater(Chunk chunk, int lx, int y, int lz) {
        if (!chunk.get(lx, y - 1, lz).solid) {
            return false;
        }
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            if (!chunk.get(lx + dir[0], y, lz + dir[1]).solid) {
                return false;
            }
        }
        return true;
    }

    private static boolean isShaftBase(World world, Chunk chunk, int baseX, int baseZ,
                                       int lx, int y, int lz) {
        if (lx >= Chunk.SX - 2 || lz >= Chunk.SZ - 2
                || world.generator.caveZoneAt(baseX + lx, y, baseZ + lz)
                != WorldGenerator.CaveZone.BASALT
                || world.generator.caveZoneAt(baseX + lx,
                y + WorldGenerator.BASALT_SHAFT_HEIGHT - 1, baseZ + lz)
                != WorldGenerator.CaveZone.BASALT) {
            return false;
        }
        for (int dx = 0; dx <= 1; dx++) {
            for (int dz = 0; dz <= 1; dz++) {
                if (chunk.get(lx + dx, y - 1, lz + dz) != BlockType.BASALT) {
                    return false;
                }
                for (int dy = 0; dy < WorldGenerator.BASALT_SHAFT_HEIGHT; dy++) {
                    if (chunk.get(lx + dx, y + dy, lz + dz) != BlockType.AIR) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static Set<String> identityPoiTags(World world) {
        Set<String> ids = new HashSet<>();
        for (Poi poi : world.pois) {
            if (poi.type == Poi.PoiType.PREDATOR_DEN
                    || poi.type == Poi.PoiType.STALKER_NEST) {
                ids.add(poi.type.name() + ":" + poi.pos.x() + ":"
                        + poi.pos.y() + ":" + poi.pos.z());
            }
        }
        return ids;
    }

    private record IdentityCounts(int rootPatches, int seepages, int rootDens,
                                  int fumaroles, int verticalShafts) {
    }
}
