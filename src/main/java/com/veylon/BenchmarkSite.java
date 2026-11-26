package com.veylon;

import com.veylon.util.Vec3i;
import com.veylon.world.Biome;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;

/**
 * Where a benchmark scene stands the QA camera in a biome: the nearest dry site of that biome to
 * the spawn, found the same way for every scene so captures stay comparable across runs.
 */
final class BenchmarkSite {

    private BenchmarkSite() {
    }

    /** Finds a nearby deterministic dry site in the requested biome and moves the QA camera there. */
    static Vec3i move(Game game, Biome desired) {
        World world = game.world;
        int ox = (int) Math.floor(game.spawnPos.x);
        int oz = (int) Math.floor(game.spawnPos.z);
        int bx = ox, bz = oz;
        boolean found = false;
        outer:
        for (int radius = 0; radius <= 1024; radius += 8) {
            for (int dx = -radius; dx <= radius; dx += 8) {
                int[] zs = radius == 0 ? new int[]{0} : new int[]{-radius, radius};
                for (int dz : zs) {
                    if (world.biomeAt(ox + dx, oz + dz) == desired) {
                        bx = ox + dx;
                        bz = oz + dz;
                        found = true;
                        break outer;
                    }
                }
            }
            for (int dz = -radius + 8; dz <= radius - 8; dz += 8) {
                for (int dx : new int[]{-radius, radius}) {
                    if (world.biomeAt(ox + dx, oz + dz) == desired) {
                        bx = ox + dx;
                        bz = oz + dz;
                        found = true;
                        break outer;
                    }
                }
            }
        }
        if (!found) {
            System.err.println("[scene] no nearby " + desired.displayName + "; using spawn biome");
        }
        world.ensureChunks(bx, bz, 4, 10_000);

        int sx = bx, sz = bz;
        search:
        for (int radius = 0; radius <= 48; radius += 4) {
            for (int dx = -radius; dx <= radius; dx += 4) {
                for (int dz = -radius; dz <= radius; dz += 4) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) continue;
                    int x = bx + dx, z = bz + dz;
                    int surface = world.surfaceHeight(x, z);
                    if (world.biomeAt(x, z) == desired && surface > World.SEA_LEVEL + 1
                            && world.getBlock(x, surface + 1, z) != BlockType.WATER) {
                        sx = x;
                        sz = z;
                        break search;
                    }
                }
            }
        }
        int naturalGround = world.surfaceHeight(sx, sz);
        int ground = Math.max(naturalGround, World.SEA_LEVEL + 3);
        Biome siteBiome = world.biomeAt(sx, sz);
        for (int y = naturalGround + 1; y < ground; y++) {
            world.setBlock(sx, y, sz, siteBiome.subsurface, false);
        }
        world.setBlock(sx, ground, sz, siteBiome.surface, false);
        for (int y = ground + 1; y <= Math.min(Chunk.SY - 1, ground + 5); y++) {
            world.setBlock(sx, y, sz, BlockType.AIR, false);
        }
        game.player.pos.set(sx + 0.5f, ground + 1.2f, sz + 0.5f);
        game.player.vel.zero();
        game.player.biome = world.biomeAt(sx, sz);
        game.camera.yaw = 0f;
        game.camera.pitch = 4f;
        System.out.println("[scene] camera site=" + sx + "," + (ground + 1) + "," + sz
                + " biome=" + game.player.biome.displayName
                + " sea=" + World.SEA_LEVEL
                + " feet=" + world.getBlock(sx, ground + 1, sz)
                + " eye=" + world.getBlock(sx, ground + 2, sz));
        return new Vec3i(sx, ground + 1, sz);
    }
}
