package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/**
 * Stamps one chunk's slice of a layout-eleven region's growth sites (urban milestone 15), after
 * everything else: the membrane carpet replaces the open ground's top block, clearing any plant
 * on it, and the node stands on the carpet. Like every urban stamper it writes only inside the
 * chunk being generated, from the pure plan, so a site is the same whichever chunk generates
 * first; work is the region's few dozen carpet cells. Wildlife habitats stamp nothing.
 */
public final class UrbanEcologyStamper {
    private UrbanEcologyStamper() {
    }

    public static void stamp(Chunk chunk, EcologySites sites) {
        int x0 = chunk.cx * Chunk.SX, z0 = chunk.cz * Chunk.SZ;
        for (EcologySites.GrowthSite site : sites.growthSites()) {
            for (Vec3i m : site.membrane()) {
                int lx = m.x() - x0, lz = m.z() - z0;
                if (lx < 0 || lz < 0 || lx >= Chunk.SX || lz >= Chunk.SZ) {
                    continue;
                }
                chunk.set(lx, m.y(), lz, BlockType.VEIL_MEMBRANE);
                BlockType above = chunk.get(lx, m.y() + 1, lz);
                if (above == BlockType.TALL_GRASS || above == BlockType.BUSH) {
                    chunk.set(lx, m.y() + 1, lz, BlockType.AIR);
                }
            }
            Vec3i n = site.node();
            int lx = n.x() - x0, lz = n.z() - z0;
            if (lx >= 0 && lz >= 0 && lx < Chunk.SX && lz < Chunk.SZ) {
                chunk.set(lx, n.y(), lz, BlockType.GROWTH_NODE);
            }
        }
    }
}
