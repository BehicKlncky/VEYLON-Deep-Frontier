package com.veylon.world;

import com.veylon.gfx.MaterialRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The CPU half of chunk meshing is pinned on city chunks (urban milestone 24): every float the
 * mesher writes for a 7 x 7 block of the economy city's spawn, where torches stand near some chunks
 * and no light near others, hashes to the value recorded before any meshing optimization. A faster
 * mesher that changes one vertex is a different picture, not an optimization.
 */
class ChunkMesherFingerprintTest {
    /** Recorded on the unoptimized mesher (every cell's block light asked of the world). */
    private static final long TESSELLATION = 5943656294887753686L;

    @Test
    void cityChunksTessellateExactlyAsRecorded() {
        MaterialRegistry.initCatalog();
        try {
            World w = new World(20260731L, World.GEN_URBAN_ECONOMY);
            for (int cx = -2; cx <= 6; cx++) {
                for (int cz = -2; cz <= 6; cz++) { w.getOrCreateChunk(cx, cz); }
            }
            // Torches in two chunks near one corner: chunks beside them sample lights, the far ones none.
            int lit = torch(w, 4, 4) + torch(w, 5, 3);
            assertTrue(lit == 2, "both torches found a floor to stand on");
            ChunkMesher mesher = new ChunkMesher();
            long fingerprint = 17;
            long vertices = 0;
            for (int cx = -1; cx <= 5; cx++) {
                for (int cz = -1; cz <= 5; cz++) {
                    mesher.tessellate(w, w.getChunk(cx, cz));
                    fingerprint = fingerprint * 31 + mesher.fingerprint();
                    vertices += mesher.opaqueFloats() + mesher.waterFloats();
                }
            }
            assertTrue(vertices > 0, "the city has faces to draw");
            assertEquals(TESSELLATION, fingerprint, "tessellation fingerprint");
        } finally {
            MaterialRegistry.clearCatalog();
        }
        assertEquals(0, MaterialRegistry.layerCount(), "the test leaves the registry as it found it");
    }

    /** Sets a torch on the first open cell above a floor in a chunk, scanning in a fixed order. */
    private static int torch(World w, int cx, int cz) {
        for (int y = 41; y < 80; y++) {
            for (int lx = 0; lx < Chunk.SX; lx++) {
                for (int lz = 0; lz < Chunk.SZ; lz++) {
                    int x = cx * Chunk.SX + lx, z = cz * Chunk.SZ + lz;
                    if (w.getBlock(x, y, z) == BlockType.AIR && w.getBlock(x, y - 1, z).solid) {
                        w.setBlock(x, y, z, BlockType.TORCH, true);
                        return 1;
                    }
                }
            }
        }
        return 0;
    }
}
