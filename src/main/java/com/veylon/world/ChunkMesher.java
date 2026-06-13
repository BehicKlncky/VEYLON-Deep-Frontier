package com.veylon.world;

import com.veylon.engine.Mesh;
import com.veylon.util.FloatList;

/**
 * Builds chunk meshes with hidden-face removal. Vertex layout:
 * position(3), color(3), skyLight(1), blockLight(1).
 */
public class ChunkMesher {

    public static final int[] ATTRIBS = {3, 3, 1, 1};

    private final FloatList opaque = new FloatList(1 << 16);
    private final FloatList water = new FloatList(1 << 12);

    public void buildChunk(World world, Chunk c) {
        opaque.clear();
        water.clear();
        int baseX = c.cx * Chunk.SX;
        int baseZ = c.cz * Chunk.SZ;

        for (int y = 0; y < Chunk.SY; y++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    BlockType t = c.get(lx, y, lz);
                    if (t == BlockType.AIR) {
                        continue;
                    }
                    int wx = baseX + lx, wz = baseZ + lz;
                    switch (t.shape) {
                        case CUBE -> emitCube(world, t, wx, y, wz);
                        case CROSS -> emitCross(world, t, wx, y, wz);
                        case TORCH -> {
                            emitBox(world, wx + 0.44f, y, wz + 0.44f, wx + 0.56f, y + 0.65f, wz + 0.56f,
                                    0.45f, 0.32f, 0.18f, wx, y, wz, 0.6f);
                            emitBox(world, wx + 0.42f, y + 0.65f, wz + 0.42f, wx + 0.58f, y + 0.82f, wz + 0.58f,
                                    1.0f, 0.78f, 0.25f, wx, y, wz, 1.0f);
                        }
                        case CAMPFIRE -> {
                            emitBox(world, wx + 0.12f, y, wz + 0.12f, wx + 0.88f, y + 0.22f, wz + 0.88f,
                                    t.r, t.g, t.b, wx, y, wz, 0.5f);
                            emitBox(world, wx + 0.32f, y + 0.22f, wz + 0.32f, wx + 0.68f, y + 0.62f, wz + 0.68f,
                                    1.0f, 0.55f, 0.12f, wx, y, wz, 1.0f);
                        }
                        case PANEL -> {
                            // Flat slab: bedrolls, beds, anvils, bone piles.
                            float ph = t == BlockType.ANVIL ? 0.55f : (t == BlockType.CAMP_BED ? 0.40f : 0.18f);
                            emitBox(world, wx + 0.06f, y, wz + 0.06f, wx + 0.94f, y + ph, wz + 0.94f,
                                    t.r, t.g, t.b, wx, y, wz, 0f);
                            if (t == BlockType.BEDROLL || t == BlockType.CAMP_BED) {
                                // Pillow end.
                                emitBox(world, wx + 0.10f, y + ph, wz + 0.10f, wx + 0.40f, y + ph + 0.10f, wz + 0.90f,
                                        t.r * 1.25f, t.g * 1.25f, t.b * 1.25f, wx, y, wz, 0f);
                            }
                        }
                        case RACK -> {
                            // Two posts and a crossbar (drying rack / tannery frame).
                            emitBox(world, wx + 0.08f, y, wz + 0.42f, wx + 0.20f, y + 0.95f, wz + 0.58f,
                                    t.r * 0.8f, t.g * 0.8f, t.b * 0.8f, wx, y, wz, 0f);
                            emitBox(world, wx + 0.80f, y, wz + 0.42f, wx + 0.92f, y + 0.95f, wz + 0.58f,
                                    t.r * 0.8f, t.g * 0.8f, t.b * 0.8f, wx, y, wz, 0f);
                            emitBox(world, wx + 0.05f, y + 0.78f, wz + 0.44f, wx + 0.95f, y + 0.90f, wz + 0.56f,
                                    t.r, t.g, t.b, wx, y, wz, 0f);
                            // Hanging strips (visual hint for the drying rack).
                            if (t == BlockType.DRYING_RACK) {
                                emitBox(world, wx + 0.30f, y + 0.42f, wz + 0.46f, wx + 0.42f, y + 0.78f, wz + 0.54f,
                                        0.62f, 0.34f, 0.24f, wx, y, wz, 0f);
                                emitBox(world, wx + 0.56f, y + 0.42f, wz + 0.46f, wx + 0.68f, y + 0.78f, wz + 0.54f,
                                        0.62f, 0.34f, 0.24f, wx, y, wz, 0f);
                            }
                        }
                        case BASIN -> {
                            // Open-topped collection basin.
                            emitBox(world, wx + 0.08f, y, wz + 0.08f, wx + 0.92f, y + 0.12f, wz + 0.92f,
                                    t.r, t.g, t.b, wx, y, wz, 0f);
                            emitBox(world, wx + 0.08f, y, wz + 0.08f, wx + 0.92f, y + 0.55f, wz + 0.18f,
                                    t.r, t.g, t.b, wx, y, wz, 0f);
                            emitBox(world, wx + 0.08f, y, wz + 0.82f, wx + 0.92f, y + 0.55f, wz + 0.92f,
                                    t.r, t.g, t.b, wx, y, wz, 0f);
                            emitBox(world, wx + 0.08f, y, wz + 0.08f, wx + 0.18f, y + 0.55f, wz + 0.92f,
                                    t.r, t.g, t.b, wx, y, wz, 0f);
                            emitBox(world, wx + 0.82f, y, wz + 0.08f, wx + 0.92f, y + 0.55f, wz + 0.92f,
                                    t.r, t.g, t.b, wx, y, wz, 0f);
                            // Water surface inside.
                            emitBox(world, wx + 0.18f, y + 0.12f, wz + 0.18f, wx + 0.82f, y + 0.34f, wz + 0.82f,
                                    0.20f, 0.38f, 0.62f, wx, y, wz, 0.25f);
                        }
                        case LIQUID -> emitWater(world, t, wx, y, wz);
                        case NONE -> {
                        }
                    }
                }
            }
        }

        if (c.meshOpaque == null) {
            c.meshOpaque = new Mesh(ATTRIBS);
        }
        if (c.meshWater == null) {
            c.meshWater = new Mesh(ATTRIBS);
        }
        c.meshOpaque.upload(opaque.array(), opaque.size());
        c.meshWater.upload(water.array(), water.size());
        c.dirty = false;
    }

    private void emitCube(World w, BlockType t, int x, int y, int z) {
        float r = t.r, g = t.g, b = t.b;
        // Grass gets dirt-colored sides for readability.
        if (!w.getBlock(x, y + 1, z).opaque) {
            emitFace(opaque, x, y + 1, z, x + 1, y + 1, z, x + 1, y + 1, z + 1, x, y + 1, z + 1,
                    r, g, b, 1.0f, w.skyLight(x, y + 1, z), w.blockLight(x, y + 1, z));
        }
        if (!w.getBlock(x, y - 1, z).opaque) {
            emitFace(opaque, x, y, z, x, y, z + 1, x + 1, y, z + 1, x + 1, y, z,
                    r, g, b, 0.45f, w.skyLight(x, y - 1, z), w.blockLight(x, y - 1, z));
        }
        float sr = r, sg = g, sb = b;
        if (t == BlockType.GRASS) {
            sr = 0.42f;
            sg = 0.32f;
            sb = 0.22f;
        }
        if (!w.getBlock(x, y, z - 1).opaque) {
            emitFace(opaque, x, y, z, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z,
                    sr, sg, sb, 0.78f, w.skyLight(x, y, z - 1), w.blockLight(x, y, z - 1));
        }
        if (!w.getBlock(x, y, z + 1).opaque) {
            emitFace(opaque, x, y, z + 1, x, y + 1, z + 1, x + 1, y + 1, z + 1, x + 1, y, z + 1,
                    sr, sg, sb, 0.78f, w.skyLight(x, y, z + 1), w.blockLight(x, y, z + 1));
        }
        if (!w.getBlock(x - 1, y, z).opaque) {
            emitFace(opaque, x, y, z, x, y + 1, z, x, y + 1, z + 1, x, y, z + 1,
                    sr, sg, sb, 0.62f, w.skyLight(x - 1, y, z), w.blockLight(x - 1, y, z));
        }
        if (!w.getBlock(x + 1, y, z).opaque) {
            emitFace(opaque, x + 1, y, z, x + 1, y, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z,
                    sr, sg, sb, 0.62f, w.skyLight(x + 1, y, z), w.blockLight(x + 1, y, z));
        }
    }

    private void emitCross(World w, BlockType t, int x, int y, int z) {
        float sky = w.skyLight(x, y, z);
        float blk = w.blockLight(x, y, z);
        float h = 0.85f;
        quad(opaque, x + 0.12f, y, z + 0.12f, x + 0.88f, y, z + 0.88f,
                x + 0.88f, y + h, z + 0.88f, x + 0.12f, y + h, z + 0.12f,
                t.r, t.g, t.b, 0.92f, sky, blk);
        quad(opaque, x + 0.88f, y, z + 0.12f, x + 0.12f, y, z + 0.88f,
                x + 0.12f, y + h, z + 0.88f, x + 0.88f, y + h, z + 0.12f,
                t.r, t.g, t.b, 0.92f, sky, blk);
    }

    private void emitWater(World w, BlockType t, int x, int y, int z) {
        float sky = w.skyLight(x, y + 1, z);
        float blk = w.blockLight(x, y + 1, z);
        BlockType above = w.getBlock(x, y + 1, z);
        if (above != BlockType.WATER && !above.opaque) {
            float top = y + 0.88f;
            quad(water, x, top, z, x + 1, top, z, x + 1, top, z + 1, x, top, z + 1,
                    t.r, t.g, t.b, 1.0f, sky, blk);
        }
        // Side faces only against open air (lets you see water walls when digging in).
        if (sideOpen(w, x, y, z - 1)) {
            quad(water, x, y, z, x + 1, y, z, x + 1, y + 1, z, x, y + 1, z, t.r, t.g, t.b, 0.85f, sky, blk);
        }
        if (sideOpen(w, x, y, z + 1)) {
            quad(water, x, y, z + 1, x + 1, y, z + 1, x + 1, y + 1, z + 1, x, y + 1, z + 1, t.r, t.g, t.b, 0.85f, sky, blk);
        }
        if (sideOpen(w, x - 1, y, z)) {
            quad(water, x, y, z, x, y, z + 1, x, y + 1, z + 1, x, y + 1, z, t.r, t.g, t.b, 0.85f, sky, blk);
        }
        if (sideOpen(w, x + 1, y, z)) {
            quad(water, x + 1, y, z, x + 1, y, z + 1, x + 1, y + 1, z + 1, x + 1, y + 1, z, t.r, t.g, t.b, 0.85f, sky, blk);
        }
    }

    private boolean sideOpen(World w, int x, int y, int z) {
        BlockType n = w.getBlock(x, y, z);
        return n != BlockType.WATER && !n.opaque;
    }

    private void emitBox(World w, float x0, float y0, float z0, float x1, float y1, float z1,
                         float r, float g, float b, int lx, int ly, int lz, float emissive) {
        float sky = Math.max(w.skyLight(lx, ly, lz), emissive);
        float blk = Math.max(w.blockLight(lx, ly, lz), emissive);
        quad(opaque, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, r, g, b, 1.0f, sky, blk);
        quad(opaque, x0, y0, z0, x0, y0, z1, x1, y0, z1, x1, y0, z0, r, g, b, 0.5f, sky, blk);
        quad(opaque, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, r, g, b, 0.78f, sky, blk);
        quad(opaque, x0, y0, z1, x0, y1, z1, x1, y1, z1, x1, y0, z1, r, g, b, 0.78f, sky, blk);
        quad(opaque, x0, y0, z0, x0, y1, z0, x0, y1, z1, x0, y0, z1, r, g, b, 0.62f, sky, blk);
        quad(opaque, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, r, g, b, 0.62f, sky, blk);
    }

    private void emitFace(FloatList buf,
                          float ax, float ay, float az, float bx, float by, float bz,
                          float cx, float cy, float cz, float dx, float dy, float dz,
                          float r, float g, float b, float shade, float sky, float blk) {
        quad(buf, ax, ay, az, bx, by, bz, cx, cy, cz, dx, dy, dz, r, g, b, shade, sky, blk);
    }

    private void quad(FloatList buf,
                      float ax, float ay, float az, float bx, float by, float bz,
                      float cx, float cy, float cz, float dx, float dy, float dz,
                      float r, float g, float b, float shade, float sky, float blk) {
        float cr = r * shade, cg = g * shade, cb = b * shade;
        buf.vertex(ax, ay, az, cr, cg, cb, sky, blk);
        buf.vertex(bx, by, bz, cr, cg, cb, sky, blk);
        buf.vertex(cx, cy, cz, cr, cg, cb, sky, blk);
        buf.vertex(ax, ay, az, cr, cg, cb, sky, blk);
        buf.vertex(cx, cy, cz, cr, cg, cb, sky, blk);
        buf.vertex(dx, dy, dz, cr, cg, cb, sky, blk);
    }
}
