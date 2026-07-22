package com.veylon.world;

import com.veylon.engine.Mesh;
import com.veylon.gfx.MaterialRegistry;
import com.veylon.gfx.MaterialRegistry.BlockMaterial;
import com.veylon.gfx.MaterialRegistry.TintMode;
import com.veylon.util.FloatList;

/**
 * Builds chunk meshes with hidden-face removal, per-vertex ambient occlusion,
 * smooth (4-cell averaged) lighting, texture-array layers and natural variants.
 *
 * Opaque vertex: pos(3) uv(2) layer(1) dir(1) sky(1) block(1) ao(1) tint(3) flags(1).
 * Water vertex:  pos(3) sky(1) block(1) columnDepth(1).
 */
public class ChunkMesher {

    public static final int[] ATTRIBS = {3, 2, 1, 1, 1, 1, 1, 3, 1};
    public static final int[] WATER_ATTRIBS = {3, 1, 1, 1};

    /** Flags bits (must match chunk.vert). */
    private static final int FLAG_FOLIAGE = 1;
    private static final int FLAG_SWAY = 2;

    // Face tables: dir 0..5 = +Y,-Y,-Z,+Z,-X,+X. Corners wound CCW from outside.
    private static final int[][] NORMAL = {
            {0, 1, 0}, {0, -1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};
    private static final int[][][] CORNERS = {
            {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}}, // +Y
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}}, // -Y
            {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}}, // -Z
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}}, // +Z
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}}, // -X
            {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}}, // +X
    };
    // In-plane tangent axes per dir (axis index 0=x,1=y,2=z) for AO sampling.
    private static final int[][] TANGENTS = {
            {0, 2}, {0, 2}, {0, 1}, {0, 1}, {2, 1}, {2, 1}};

    private static final float[] AO_LEVELS = {0.42f, 0.62f, 0.82f, 1.0f};

    private final FloatList opaque = new FloatList(1 << 17);
    private final FloatList water = new FloatList(1 << 12);

    // Per-face scratch is retained with the mesher. A typical terrain rebuild
    // emits thousands of faces, so allocating these arrays per face dominated
    // otherwise avoidable young-generation churn.
    private final float[] faceX = new float[4];
    private final float[] faceY = new float[4];
    private final float[] faceZ = new float[4];
    private final float[] faceU = new float[4];
    private final float[] faceV = new float[4];
    private final float[] faceAo = new float[4];
    private final float[] faceSky = new float[4];
    private final float[] faceBlock = new float[4];
    private static final int[] QUAD_ORDER = {0, 1, 2, 0, 2, 3};
    private static final int[] FLIPPED_QUAD_ORDER = {1, 2, 3, 1, 3, 0};

    // Per-build caches over the chunk neighborhood: x,z in [-1,17), y in [-1,SY+1).
    private static final int CW = Chunk.SX + 2, CH = Chunk.SY + 2;
    private final byte[] solidCache = new byte[CW * CH * CW];      // 0 unknown, 1 opaque, 2 open
    private final float[] skyCache = new float[CW * CH * CW];
    private final float[] blockCache = new float[CW * CH * CW];
    private final byte[] lightSet = new byte[CW * CH * CW];
    private int baseX, baseZ;
    private World world;

    public void buildChunk(World world, Chunk c) {
        this.world = world;
        opaque.clear();
        water.clear();
        baseX = c.cx * Chunk.SX;
        baseZ = c.cz * Chunk.SZ;
        java.util.Arrays.fill(solidCache, (byte) 0);
        java.util.Arrays.fill(lightSet, (byte) 0);

        for (int y = 0; y < Chunk.SY; y++) {
            for (int lz = 0; lz < Chunk.SZ; lz++) {
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    BlockType t = c.get(lx, y, lz);
                    if (t == BlockType.AIR) {
                        continue;
                    }
                    int wx = baseX + lx, wz = baseZ + lz;
                    switch (t.shape) {
                        case CUBE -> emitCube(t, wx, y, wz);
                        case CROSS -> emitCross(t, wx, y, wz);
                        case TORCH -> {
                            if (t == BlockType.LANTERN) {
                                boolean lit = world.isLanternLit(wx, y, wz);
                                box(wx + 0.38f, y, wz + 0.38f, wx + 0.62f, y + 0.10f, wz + 0.62f,
                                        layer("anvil_metal"), wx, y, wz, 1f, 1f, 1f);
                                box(wx + 0.34f, y + 0.10f, wz + 0.34f, wx + 0.66f, y + 0.52f, wz + 0.66f,
                                        layer(lit ? "lantern_glass" : "lantern_glass_unlit"),
                                        wx, y, wz, lit ? 1f : 0.55f,
                                        lit ? 1f : 0.58f, lit ? 1f : 0.62f);
                                box(wx + 0.40f, y + 0.52f, wz + 0.40f, wx + 0.60f, y + 0.62f, wz + 0.60f,
                                        layer("anvil_metal"), wx, y, wz, 1f, 1f, 1f);
                            } else if (t == BlockType.TRAIL_MARKER) {
                                box(wx + 0.46f, y, wz + 0.46f, wx + 0.54f, y + 0.55f, wz + 0.54f,
                                        layer("log_side"), wx, y, wz, 1f, 1f, 1f);
                                box(wx + 0.42f, y + 0.42f, wz + 0.42f, wx + 0.58f, y + 0.58f, wz + 0.58f,
                                        layer("marker_paint"), wx, y, wz, 1f, 1f, 1f);
                            } else if (t == BlockType.ALARM_BELL) {
                                box(wx + 0.44f, y, wz + 0.44f, wx + 0.56f, y + 0.92f, wz + 0.56f,
                                        layer("log_side"), wx, y, wz, 1f, 1f, 1f);
                                box(wx + 0.32f, y + 0.52f, wz + 0.32f, wx + 0.68f, y + 0.84f, wz + 0.68f,
                                        layer("bell_bronze"), wx, y, wz, 1f, 1f, 1f);
                            } else {
                                box(wx + 0.44f, y, wz + 0.44f, wx + 0.56f, y + 0.68f, wz + 0.56f,
                                        layer("log_side"), wx, y, wz, 1f, 1f, 1f);
                                box(wx + 0.40f, y + 0.68f, wz + 0.40f, wx + 0.60f, y + 0.86f, wz + 0.60f,
                                        layer("torch_head"), wx, y, wz, 1f, 1f, 1f);
                            }
                        }
                        case CAMPFIRE -> {
                            box(wx + 0.12f, y, wz + 0.12f, wx + 0.88f, y + 0.20f, wz + 0.88f,
                                    layer("campfire_wood"), wx, y, wz, 1f, 1f, 1f);
                            box(wx + 0.30f, y + 0.20f, wz + 0.30f, wx + 0.70f, y + 0.62f, wz + 0.70f,
                                    layer("torch_head"), wx, y, wz, 1f, 1f, 1f);
                        }
                        case PANEL -> {
                            BlockMaterial m = MaterialRegistry.of(t);
                            float ph = t == BlockType.ANVIL ? 0.55f : (t == BlockType.CAMP_BED ? 0.40f : 0.18f);
                            box(wx + 0.06f, y, wz + 0.06f, wx + 0.94f, y + ph, wz + 0.94f,
                                    m.topLayer[0], wx, y, wz, 1f, 1f, 1f);
                            if (t == BlockType.BEDROLL || t == BlockType.CAMP_BED) {
                                box(wx + 0.10f, y + ph, wz + 0.10f, wx + 0.40f, y + ph + 0.10f, wz + 0.90f,
                                        m.topLayer[0], wx, y, wz, 1.25f, 1.25f, 1.25f);
                            }
                        }
                        case RACK -> {
                            int wood = layer("log_side");
                            box(wx + 0.08f, y, wz + 0.42f, wx + 0.20f, y + 0.95f, wz + 0.58f,
                                    wood, wx, y, wz, 0.85f, 0.85f, 0.85f);
                            box(wx + 0.80f, y, wz + 0.42f, wx + 0.92f, y + 0.95f, wz + 0.58f,
                                    wood, wx, y, wz, 0.85f, 0.85f, 0.85f);
                            box(wx + 0.05f, y + 0.78f, wz + 0.44f, wx + 0.95f, y + 0.90f, wz + 0.56f,
                                    wood, wx, y, wz, 1f, 1f, 1f);
                            if (t == BlockType.DRYING_RACK) {
                                int fab = layer("fabric");
                                box(wx + 0.30f, y + 0.42f, wz + 0.46f, wx + 0.42f, y + 0.78f, wz + 0.54f,
                                        fab, wx, y, wz, 0.9f, 0.5f, 0.38f);
                                box(wx + 0.56f, y + 0.42f, wz + 0.46f, wx + 0.68f, y + 0.78f, wz + 0.54f,
                                        fab, wx, y, wz, 0.9f, 0.5f, 0.38f);
                            } else if (t == BlockType.TANNERY) {
                                box(wx + 0.24f, y + 0.30f, wz + 0.47f, wx + 0.76f, y + 0.80f, wz + 0.53f,
                                        layer("fabric"), wx, y, wz, 0.8f, 0.62f, 0.45f);
                            }
                        }
                        case BASIN -> {
                            int metal = layer("scrap");
                            box(wx + 0.08f, y, wz + 0.08f, wx + 0.92f, y + 0.12f, wz + 0.92f,
                                    metal, wx, y, wz, 1f, 1f, 1f);
                            box(wx + 0.08f, y, wz + 0.08f, wx + 0.92f, y + 0.55f, wz + 0.18f,
                                    metal, wx, y, wz, 1f, 1f, 1f);
                            box(wx + 0.08f, y, wz + 0.82f, wx + 0.92f, y + 0.55f, wz + 0.92f,
                                    metal, wx, y, wz, 1f, 1f, 1f);
                            box(wx + 0.08f, y, wz + 0.08f, wx + 0.18f, y + 0.55f, wz + 0.92f,
                                    metal, wx, y, wz, 1f, 1f, 1f);
                            box(wx + 0.82f, y, wz + 0.08f, wx + 0.92f, y + 0.55f, wz + 0.92f,
                                    metal, wx, y, wz, 1f, 1f, 1f);
                            box(wx + 0.18f, y + 0.12f, wz + 0.18f, wx + 0.82f, y + 0.34f, wz + 0.82f,
                                    layer("water_still"), wx, y, wz, 0.7f, 0.85f, 1f);
                        }
                        case LIQUID -> emitWater(wx, y, wz);
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
            c.meshWater = new Mesh(WATER_ATTRIBS);
        }
        c.meshOpaque.upload(opaque.array(), opaque.size());
        c.meshWater.upload(water.array(), water.size());
        c.dirty = false;
        this.world = null;
    }

    private static int layer(String tileId) {
        return MaterialRegistry.layer(tileId);
    }

    // ------------------------------------------------------------------
    // Cached neighborhood queries
    // ------------------------------------------------------------------

    private int cidx(int x, int y, int z) {
        int lx = x - baseX + 1, lz = z - baseZ + 1;
        if (lx < 0 || lx >= CW || lz < 0 || lz >= CW || y < -1 || y > Chunk.SY) {
            return -1;
        }
        return (y + 1) * CW * CW + lz * CW + lx;
    }

    private boolean opaqueAt(int x, int y, int z) {
        int i = cidx(x, y, z);
        if (i < 0) {
            return world.getBlock(x, y, z).opaque;
        }
        byte v = solidCache[i];
        if (v == 0) {
            v = world.getBlock(x, y, z).opaque ? (byte) 1 : (byte) 2;
            solidCache[i] = v;
        }
        return v == 1;
    }

    private void ensureLight(int x, int y, int z, int i) {
        if (lightSet[i] == 0) {
            skyCache[i] = world.skyLight(x, y, z);
            blockCache[i] = world.blockLight(x, y, z);
            lightSet[i] = 1;
        }
    }

    private float skyAt(int x, int y, int z) {
        int i = cidx(x, y, z);
        if (i < 0) {
            return world.skyLight(x, y, z);
        }
        ensureLight(x, y, z, i);
        return skyCache[i];
    }

    private float blockAt(int x, int y, int z) {
        int i = cidx(x, y, z);
        if (i < 0) {
            return world.blockLight(x, y, z);
        }
        ensureLight(x, y, z, i);
        return blockCache[i];
    }

    // ------------------------------------------------------------------
    // Cube faces
    // ------------------------------------------------------------------

    private void emitCube(BlockType t, int x, int y, int z) {
        BlockMaterial m = MaterialRegistry.of(t);
        int variant = m.variantOf(x, y, z);
        // Subtle per-block value jitter breaks up large same-material areas.
        float jitter = 0.95f + hash(x, y, z) * 0.1f;

        for (int d = 0; d < 6; d++) {
            int[] n = NORMAL[d];
            int nx = x + n[0], ny = y + n[1], nz = z + n[2];
            if (opaqueAt(nx, ny, nz)) {
                continue;
            }
            int layer = switch (d) {
                case 0 -> m.topLayer[variant];
                case 1 -> m.bottomLayer[variant];
                default -> m.sideLayer[variant];
            };
            int flags = 0;
            if (m.tint == TintMode.FOLIAGE || (m.tint == TintMode.GRASS_TOP && d == 0)) {
                flags |= FLAG_FOLIAGE;
            }
            emitFace(d, x, y, z, layer, flags, jitter, jitter, jitter);
        }
    }

    /** One cube face with per-vertex AO + smoothed light and world-space UVs. */
    private void emitFace(int d, int x, int y, int z, int layer, int flags,
                          float tr, float tg, float tb) {
        int[] n = NORMAL[d];
        int fx = x + n[0], fy = y + n[1], fz = z + n[2]; // cell in front of the face
        int a1 = TANGENTS[d][0], a2 = TANGENTS[d][1];

        for (int i = 0; i < 4; i++) {
            int[] csel = CORNERS[d][i];
            faceX[i] = x + csel[0];
            faceY[i] = y + csel[1];
            faceZ[i] = z + csel[2];
            // World-space UVs (repeat wrapping in the array sampler).
            switch (d) {
                case 0, 1 -> {
                    faceU[i] = faceX[i];
                    faceV[i] = faceZ[i];
                }
                case 2, 3 -> {
                    faceU[i] = faceX[i];
                    faceV[i] = -faceY[i];
                }
                default -> {
                    faceU[i] = faceZ[i];
                    faceV[i] = -faceY[i];
                }
            }
            // Signed tangent direction for this corner: corner coord 1 => +axis, 0 => -axis.
            int s1 = cornerAxis(csel, a1) == 1 ? 1 : -1;
            int s2 = cornerAxis(csel, a2) == 1 ? 1 : -1;
            int e1x = fx + (a1 == 0 ? s1 : 0), e1y = fy + (a1 == 1 ? s1 : 0), e1z = fz + (a1 == 2 ? s1 : 0);
            int e2x = fx + (a2 == 0 ? s2 : 0), e2y = fy + (a2 == 1 ? s2 : 0), e2z = fz + (a2 == 2 ? s2 : 0);
            int ecx = fx + (a1 == 0 ? s1 : 0) + (a2 == 0 ? s2 : 0);
            int ecy = fy + (a1 == 1 ? s1 : 0) + (a2 == 1 ? s2 : 0);
            int ecz = fz + (a1 == 2 ? s1 : 0) + (a2 == 2 ? s2 : 0);

            boolean o1 = opaqueAt(e1x, e1y, e1z);
            boolean o2 = opaqueAt(e2x, e2y, e2z);
            boolean oc = opaqueAt(ecx, ecy, ecz);
            int level = (o1 && o2) ? 0 : 3 - ((o1 ? 1 : 0) + (o2 ? 1 : 0) + (oc ? 1 : 0));
            faceAo[i] = AO_LEVELS[level];

            // Smooth light: average the open cells of the same neighborhood.
            float sSum = skyAt(fx, fy, fz);
            float bSum = blockAt(fx, fy, fz);
            int cnt = 1;
            if (!o1) {
                sSum += skyAt(e1x, e1y, e1z);
                bSum += blockAt(e1x, e1y, e1z);
                cnt++;
            }
            if (!o2) {
                sSum += skyAt(e2x, e2y, e2z);
                bSum += blockAt(e2x, e2y, e2z);
                cnt++;
            }
            if (!oc && (!o1 || !o2)) {
                sSum += skyAt(ecx, ecy, ecz);
                bSum += blockAt(ecx, ecy, ecz);
                cnt++;
            }
            faceSky[i] = sSum / cnt;
            faceBlock[i] = bSum / cnt;
        }

        // Flip the quad diagonal toward the brighter pair to avoid AO artifacts.
        boolean flip = faceAo[0] + faceAo[2] < faceAo[1] + faceAo[3];
        int[] order = flip ? FLIPPED_QUAD_ORDER : QUAD_ORDER;
        for (int oi : order) {
            vertex(faceX[oi], faceY[oi], faceZ[oi], faceU[oi], faceV[oi], layer, d,
                    faceSky[oi], faceBlock[oi], faceAo[oi], tr, tg, tb, flags);
        }
    }

    private static int cornerAxis(int[] corner, int axis) {
        return corner[axis];
    }

    // ------------------------------------------------------------------
    // Cross shapes (plants)
    // ------------------------------------------------------------------

    private void emitCross(BlockType t, int x, int y, int z) {
        BlockMaterial m = MaterialRegistry.of(t);
        int variant = m.variantOf(x, y, z);
        int layer = m.sideLayer[variant];
        float sky = skyAt(x, y, z);
        float blk = blockAt(x, y, z);
        int flags = FLAG_SWAY | (m.tint == TintMode.FOLIAGE ? FLAG_FOLIAGE : 0);
        float jitter = 0.92f + hash(x, y, z) * 0.16f;
        float h = 0.92f;
        float i0 = 0.10f, i1 = 0.90f;

        // Two diagonal quads, each emitted double-sided (culling is enabled).
        crossQuad(x + i0, y, z + i0, x + i1, y, z + i1, h, layer, 6, sky, blk, jitter, flags);
        crossQuad(x + i1, y, z + i0, x + i0, y, z + i1, h, layer, 7, sky, blk, jitter, flags);
    }

    private void crossQuad(float x0, float y, float z0, float x1, float y2, float z1, float h,
                           int layer, int dir, float sky, float blk, float tint, int flags) {
        // Front.
        vertex(x0, y, z0, 0, 1, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x1, y, z1, 1, 1, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x1, y + h, z1, 1, 0, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x0, y, z0, 0, 1, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x1, y + h, z1, 1, 0, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x0, y + h, z0, 0, 0, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        // Back.
        vertex(x1, y, z1, 1, 1, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x0, y, z0, 0, 1, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x0, y + h, z0, 0, 0, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x1, y, z1, 1, 1, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x0, y + h, z0, 0, 0, layer, dir, sky, blk, 1, tint, tint, tint, flags);
        vertex(x1, y + h, z1, 1, 0, layer, dir, sky, blk, 1, tint, tint, tint, flags);
    }

    // ------------------------------------------------------------------
    // Water
    // ------------------------------------------------------------------

    private void emitWater(int x, int y, int z) {
        float sky = skyAt(x, y + 1, z);
        float blk = blockAt(x, y + 1, z);
        BlockType above = world.getBlock(x, y + 1, z);
        if (above != BlockType.WATER && !above.opaque) {
            float top = y + 0.88f;
            // Column depth per corner: how much water lies below (for shore color).
            float d00 = waterDepth(x, y, z);
            float d10 = waterDepth(x + 1, y, z);
            float d11 = waterDepth(x + 1, y, z + 1);
            float d01 = waterDepth(x, y, z + 1);
            // Corner depths average the 4 columns around each corner (cheap approx: use own).
            waterQuad(x, top, z, x + 1, top, z, x + 1, top, z + 1, x, top, z + 1,
                    sky, blk, d00, d10, d11, d01);
            // Visible from below too.
            waterQuad(x, top, z + 1, x + 1, top, z + 1, x + 1, top, z, x, top, z,
                    sky, blk, d01, d11, d10, d00);
        }
        if (sideOpen(x, y, z - 1)) {
            waterSide(x, y, z, x + 1, y, z, sky, blk);
        }
        if (sideOpen(x, y, z + 1)) {
            waterSide(x + 1, y, z + 1, x, y, z + 1, sky, blk);
        }
        if (sideOpen(x - 1, y, z)) {
            waterSide(x, y, z + 1, x, y, z, sky, blk);
        }
        if (sideOpen(x + 1, y, z)) {
            waterSide(x + 1, y, z, x + 1, y, z + 1, sky, blk);
        }
    }

    /** Depth of the contiguous water column below (x,y,z), for shoreline shading. */
    private float waterDepth(int x, int y, int z) {
        int d = 1;
        while (d < 6 && world.getBlock(x, y - d, z) == BlockType.WATER) {
            d++;
        }
        return d;
    }

    private void waterSide(float x0, float y, float z0, float x1, float y1unused, float z1,
                           float sky, float blk) {
        float top = y + 0.88f;
        water.add(x0, y, z0);
        water.add(sky, blk, 2f);
        water.add(x1, y, z1);
        water.add(sky, blk, 2f);
        water.add(x1, top, z1);
        water.add(sky, blk, 0.6f);
        water.add(x0, y, z0);
        water.add(sky, blk, 2f);
        water.add(x1, top, z1);
        water.add(sky, blk, 0.6f);
        water.add(x0, top, z0);
        water.add(sky, blk, 0.6f);
    }

    private void waterQuad(float ax, float ay, float az, float bx, float by, float bz,
                           float cx, float cy, float cz, float dx, float dy, float dz,
                           float sky, float blk, float da, float db, float dc, float dd) {
        water.add(ax, ay, az);
        water.add(sky, blk, da);
        water.add(bx, by, bz);
        water.add(sky, blk, db);
        water.add(cx, cy, cz);
        water.add(sky, blk, dc);
        water.add(ax, ay, az);
        water.add(sky, blk, da);
        water.add(cx, cy, cz);
        water.add(sky, blk, dc);
        water.add(dx, dy, dz);
        water.add(sky, blk, dd);
    }

    private boolean sideOpen(int x, int y, int z) {
        BlockType n = world.getBlock(x, y, z);
        return n != BlockType.WATER && !n.opaque;
    }

    // ------------------------------------------------------------------
    // Free boxes (torches, campfires, panels, racks, basins)
    // ------------------------------------------------------------------

    private void box(float x0, float y0, float z0, float x1, float y1, float z1,
                     int layer, int lx, int ly, int lz, float tr, float tg, float tb) {
        float sky = skyAt(lx, ly, lz);
        float blk = blockAt(lx, ly, lz);
        // 6 faces, same winding tables as cubes, UVs spanning the box extents.
        boxFace(0, x0, y1, z0, x1, y1, z1, layer, sky, blk, tr, tg, tb);
        boxFace(1, x0, y0, z0, x1, y0, z1, layer, sky, blk, tr, tg, tb);
        boxFace(2, x0, y0, z0, x1, y1, z0, layer, sky, blk, tr, tg, tb);
        boxFace(3, x0, y0, z1, x1, y1, z1, layer, sky, blk, tr, tg, tb);
        boxFace(4, x0, y0, z0, x0, y1, z1, layer, sky, blk, tr, tg, tb);
        boxFace(5, x1, y0, z0, x1, y1, z1, layer, sky, blk, tr, tg, tb);
    }

    private void boxFace(int d, float x0, float y0, float z0, float x1, float y1, float z1,
                         int layer, float sky, float blk, float tr, float tg, float tb) {
        float[][] q = switch (d) {
            case 0 -> new float[][]{{x0, y1, z1}, {x1, y1, z1}, {x1, y1, z0}, {x0, y1, z0}};
            case 1 -> new float[][]{{x0, y0, z0}, {x1, y0, z0}, {x1, y0, z1}, {x0, y0, z1}};
            case 2 -> new float[][]{{x1, y0, z0}, {x0, y0, z0}, {x0, y1, z0}, {x1, y1, z0}};
            case 3 -> new float[][]{{x0, y0, z1}, {x1, y0, z1}, {x1, y1, z1}, {x0, y1, z1}};
            case 4 -> new float[][]{{x0, y0, z0}, {x0, y0, z1}, {x0, y1, z1}, {x0, y1, z0}};
            default -> new float[][]{{x1, y0, z1}, {x1, y0, z0}, {x1, y1, z0}, {x1, y1, z1}};
        };
        for (int idx : new int[]{0, 1, 2, 0, 2, 3}) {
            float[] p = q[idx];
            float u;
            float v;
            switch (d) {
                case 0, 1 -> {
                    u = p[0];
                    v = p[2];
                }
                case 2, 3 -> {
                    u = p[0];
                    v = -p[1];
                }
                default -> {
                    u = p[2];
                    v = -p[1];
                }
            }
            vertex(p[0], p[1], p[2], u, v, layer, d, sky, blk, 1f, tr, tg, tb, 0);
        }
    }

    // ------------------------------------------------------------------

    private void vertex(float x, float y, float z, float u, float v, int layer, int dir,
                        float sky, float blk, float ao, float tr, float tg, float tb, int flags) {
        opaque.add(x, y, z);
        opaque.add(u, v);
        opaque.add(layer, dir);
        opaque.add(sky, blk);
        opaque.add(ao);
        opaque.add(tr, tg, tb);
        opaque.add(flags);
    }

    private static float hash(int x, int y, int z) {
        int h = x * 374761393 + y * 668265263 + z * 1274126177;
        h = (h ^ (h >>> 13)) * 1103515245;
        return ((h >>> 16) & 0xFFFF) / 65535f;
    }
}
