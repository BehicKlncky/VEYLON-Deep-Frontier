package com.veylon.gfx;

/**
 * Deterministic, tileable 64x64 texture generator — the project's original
 * starter material pack. Every tile can be replaced later by dropping a PNG in
 * assets/textures/blocks/&lt;tileId&gt;.png (see ASSET_PIPELINE.md); this code is the
 * always-available fallback and the current shipped art style.
 *
 * All noise is lattice-wrapped so every tile is seamless, and everything is
 * seeded from (tileId, variant) so output never changes between runs.
 */
public final class ProceduralTextures {

    private ProceduralTextures() {
    }

    /** Generates a tile, or null if the id is unknown (caller reports + falls back). */
    public static int[] generate(String tileId, int variant, int size) {
        int seed = tileId.hashCode() * 31 + variant * 7919;
        Painter p = new Painter(size, seed);
        switch (tileId) {
            case "grass_top" -> grassTop(p);
            case "grass_side" -> grassSide(p);
            case "dirt" -> dirt(p);
            case "stone" -> stone(p, 0x8a8c92, 1f);
            case "ruin_stone" -> ruinStone(p);
            case "sand" -> sand(p);
            case "gravel" -> gravel(p);
            case "clay" -> clay(p);
            case "snow" -> snow(p);
            case "ice" -> ice(p);
            case "log_side" -> logSide(p, 0x4a3a2c);
            case "log_end" -> logEnd(p);
            case "plank" -> planks(p, 0x9a7444, false);
            case "wall" -> planks(p, 0x6e5638, true);
            case "leaves" -> leaves(p, 0x4e7a3e, 0x33512a);
            case "tall_grass" -> tallGrass(p);
            case "bush" -> bush(p, 0);
            case "berry_bush" -> bush(p, 1);
            case "berry_bush_empty" -> bush(p, 2);
            case "sapling" -> sapling(p);
            case "herb" -> herb(p);
            case "coal_ore" -> ore(p, 0x26262a, 0x0e0e10);
            case "copper_ore" -> ore(p, 0xb4682e, 0x2a9089);
            case "iron_ore" -> ore(p, 0xc09a70, 0x7a5c48);
            case "ash" -> ash(p);
            case "scrap" -> scrap(p);
            case "pod_hull" -> podHull(p);
            case "ruin_core" -> ruinCore(p);
            case "bone" -> bone(p);
            case "furnace_side" -> furnaceSide(p, false);
            case "furnace_front" -> furnaceSide(p, true);
            case "anvil_metal" -> metal(p, 0x3c4046);
            case "torch_head" -> torchHead(p);
            case "campfire_wood" -> logSide(p, 0x3c2c1c);
            case "crate_side" -> crate(p, false);
            case "crate_top" -> crate(p, true);
            case "workbench_top" -> workbenchTop(p);
            case "workbench_side" -> planks(p, 0x8a6a3e, true);
            case "beacon_side" -> beacon(p, false);
            case "beacon_lit" -> beacon(p, true);
            case "fabric" -> fabric(p, 0x9a8258);
            case "fabric_red" -> fabric(p, 0x8a5048);
            case "herb_station" -> herbStation(p);
            case "map_table" -> mapTable(p);
            case "water_still" -> water(p);
            case "basalt" -> basalt(p);
            case "sulfur_ore" -> sulfurOre(p);
            case "saltpeter_ore" -> saltpeterOre(p);
            case "glow_fungus" -> glowFungus(p);
            case "ladder" -> ladder(p);
            case "lantern_glass" -> lanternGlass(p);
            case "lantern_glass_unlit" -> lanternGlassUnlit(p);
            case "marker_paint" -> markerPaint(p);
            case "keg_side" -> keg(p, false);
            case "keg_top" -> keg(p, true);
            case "gate" -> gate(p);
            case "stone_brick" -> stoneBrick(p);
            case "bell_bronze" -> bellBronze(p);
            case "cage_bars" -> cageBars(p);
            default -> {
                return null;
            }
        }
        return p.pixels;
    }

    // ------------------------------------------------------------------
    // Tiles
    // ------------------------------------------------------------------

    private static void grassTop(Painter p) {
        p.fillFbm(0x63894c, 0.16f, 5, 3);
        p.fillFbmBlend(0x4e7440, 0.5f, 9, 2, 0.35f);
        p.grain(0.06f);
        // Light blade flecks.
        for (int i = 0; i < 90; i++) {
            int x = p.ri(i * 3), y = p.ri(i * 3 + 1);
            if (p.rf(i * 3 + 2) < 0.6f) {
                p.dot(x, y, p.shade(0x7fa45e, 0.9f + p.rf(i) * 0.3f));
            }
        }
    }

    private static void grassSide(Painter p) {
        dirt(p);
        // Ragged grass overhang on top.
        for (int x = 0; x < p.size; x++) {
            int depth = 6 + (int) (p.noise(x, 0, 8, 11) * 8);
            for (int y = 0; y < depth; y++) {
                float f = p.noise(x, y, 16, 12);
                p.set(x, y, p.shade(f > 0.5f ? 0x63894c : 0x54763f, 0.85f + f * 0.3f));
            }
            p.set(x, depth, p.shade(0x3f5c33, 1f));
        }
    }

    private static void dirt(Painter p) {
        p.fillFbm(0x7a5a3a, 0.22f, 5, 21);
        p.fillFbmBlend(0x644930, 0.5f, 10, 22, 0.3f);
        p.grain(0.07f);
        for (int i = 0; i < 22; i++) {
            int x = p.ri(i * 5 + 100), y = p.ri(i * 5 + 101);
            int c = p.shade(0x59452f, 0.8f + p.rf(i) * 0.3f);
            p.dot(x, y, c);
            p.dot(x + 1, y, c);
            if (i % 3 == 0) {
                p.dot(x, y + 1, c);
                p.dot(x + 1, y + 1, c);
            }
        }
    }

    private static void stone(Painter p, int base, float contrast) {
        p.fillFbm(base, 0.13f * contrast, 4, 31);
        p.fillFbmBlend(p.shade(base, 0.82f), 0.6f, 8, 32, 0.3f);
        p.grain(0.045f);
        p.cracks(0x000000, 0.35f, 33, 8);
    }

    private static void ruinStone(Painter p) {
        p.fillFbm(0x585c6e, 0.10f, 4, 41);
        // 2x2 large ashlar bricks with worn mortar seams.
        int half = p.size / 2;
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                int lx = x % half, ly = y % half;
                if (lx < 2 || ly < 2) {
                    p.set(x, y, p.shade(0x3a3d4c, 0.9f + p.noise(x, y, 16, 42) * 0.2f));
                }
            }
        }
        p.grain(0.04f);
        // Faint violet energy vein.
        p.veins(0x8a6fd0, 0.16f, 43, 0.35f);
    }

    private static void sand(Painter p) {
        p.fillFbm(0xcbb886, 0.10f, 5, 51);
        // Wind ripples: diagonal banding.
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float band = (float) Math.sin((x + y * 2.2f) * 0.35f + p.noise(x, y, 4, 52) * 4f);
                if (band > 0.75f) {
                    p.mulAt(x, y, 0.93f);
                }
            }
        }
        p.grain(0.05f);
    }

    private static void gravel(Painter p) {
        p.fillFbm(0x6f6a62, 0.12f, 6, 61);
        // Jittered pebble grid.
        int cell = 8;
        for (int cy = 0; cy < p.size / cell; cy++) {
            for (int cx = 0; cx < p.size / cell; cx++) {
                int s = cx * 73 + cy * 149;
                int x = cx * cell + 2 + (int) (p.rf(s) * (cell - 4));
                int y = cy * cell + 2 + (int) (p.rf(s + 1) * (cell - 4));
                int r = 2 + (int) (p.rf(s + 2) * 2.5f);
                float v = 0.72f + p.rf(s + 3) * 0.55f;
                int tint = p.rf(s + 4) < 0.3f ? 0x7a6a58 : 0x77757a;
                p.pebble(x, y, r, p.shade(tint, v));
            }
        }
        p.grain(0.05f);
    }

    private static void clay(Painter p) {
        p.fillFbm(0x9a9187, 0.07f, 4, 71);
        for (int y = 0; y < p.size; y++) {
            float band = 0.95f + 0.05f * (float) Math.sin(y * 0.5f + p.noise(0, y, 4, 72) * 3f);
            for (int x = 0; x < p.size; x++) {
                p.mulAt(x, y, band);
            }
        }
        p.grain(0.03f);
    }

    private static void snow(Painter p) {
        p.fillFbm(0xe8ecf4, 0.045f, 5, 81);
        p.grain(0.02f);
        for (int i = 0; i < 40; i++) {
            int x = p.ri(i * 7 + 300), y = p.ri(i * 7 + 301);
            p.dot(x, y, 0xffffff);
        }
        // Soft blue shadow patches.
        p.fillFbmBlend(0xc9d4ea, 0.6f, 10, 82, 0.25f);
    }

    private static void ice(Painter p) {
        p.fillFbm(0x9cc0dd, 0.08f, 4, 91);
        p.cracks(0xdff2ff, 0.5f, 92, 6);
        p.fillFbmBlend(0x7ea8cc, 0.55f, 9, 93, 0.3f);
        p.grain(0.02f);
    }

    private static void logSide(Painter p, int base) {
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                // Vertical bark ridges: high-frequency in x, low in y.
                float n = p.noise(x * 3, y / 2, 24, 101);
                float ridge = 1f - Math.abs(2f * n - 1f);
                float v = 0.72f + ridge * 0.5f;
                int c = p.shade(base, v);
                if (ridge > 0.86f) {
                    c = p.shade(base, 1.35f);
                }
                p.set(x, y, c);
            }
        }
        p.grain(0.05f);
    }

    private static void logEnd(Painter p) {
        float c = p.size / 2f;
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float d = (float) Math.hypot(x - c, y - c);
                float ring = (float) Math.sin(d * 1.1f + p.noise(x, y, 6, 111) * 2.2f);
                float v = 0.8f + 0.25f * ring;
                p.set(x, y, p.shade(d > c - 2 ? 0x5a4430 : 0xb08d5c, v));
            }
        }
        p.grain(0.04f);
    }

    private static void planks(Painter p, int base, boolean vertical) {
        int board = p.size / 4;
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                int along = vertical ? y : x;
                int across = vertical ? x : y;
                int b = across / board;
                float bshift = 0.9f + p.rf(b * 977) * 0.2f;
                float grain = p.noise(vertical ? across * 3 : along, vertical ? along : across * 3, 20, 121 + b);
                float v = bshift * (0.85f + grain * 0.3f);
                int ccol = p.shade(base, v);
                if (across % board == 0 || across % board == board - 1) {
                    ccol = p.shade(base, v * 0.62f);
                }
                p.set(x, y, ccol);
            }
        }
        p.grain(0.04f);
    }

    private static void leaves(Painter p, int light, int dark) {
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float n = p.noise(x, y, 12, 131);
                float m = p.noise(x, y, 5, 132);
                int c;
                if (n < 0.28f) {
                    c = p.shade(dark, 0.65f + m * 0.3f); // shadow clumps
                } else {
                    c = p.shade(n > 0.58f ? light : dark, 0.85f + m * 0.45f);
                }
                p.set(x, y, c);
            }
        }
        p.grain(0.05f);
    }

    private static void tallGrass(Painter p) {
        p.clearAlpha();
        for (int i = 0; i < 16; i++) {
            float bx = 4 + p.rf(i * 13) * (p.size - 8);
            float lean = (p.rf(i * 13 + 1) - 0.5f) * 14f;
            int h = (int) (p.size * (0.45f + p.rf(i * 13 + 2) * 0.5f));
            int col = p.shade(0x74a058, 0.75f + p.rf(i * 13 + 3) * 0.55f);
            for (int t = 0; t < h; t++) {
                float f = t / (float) h;
                int x = (int) (bx + lean * f * f);
                int y = p.size - 1 - t;
                p.set(x, y, col);
                p.set(x + 1, y, p.shade(col, 0.82f));
                if (f < 0.55f) {
                    p.set(x + 2, y, p.shade(col, 0.7f));
                }
            }
        }
    }

    private static void bush(Painter p, int kind) {
        p.clearAlpha();
        float cx = p.size / 2f, cy = p.size * 0.62f;
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float d = (float) Math.hypot((x - cx) / (p.size * 0.46f), (y - cy) / (p.size * 0.4f));
                float n = p.noise(x, y, 10, 141 + kind);
                if (d + n * 0.55f < 1.0f && n > 0.22f) {
                    int base = kind == 2 ? 0x415c38 : 0x38512f;
                    p.set(x, y, p.shade(base, 0.65f + n * 0.7f));
                }
            }
        }
        // Stems to the ground.
        for (int t = 0; t < p.size * 0.35f; t++) {
            p.set(p.size / 2, p.size - 1 - t, 0x4a3826);
        }
        if (kind == 1) {
            for (int i = 0; i < 12; i++) {
                int x = (int) (cx + (p.rf(i * 29) - 0.5f) * p.size * 0.7f);
                int y = (int) (cy + (p.rf(i * 29 + 1) - 0.5f) * p.size * 0.55f);
                if (p.get(x, y) != 0) {
                    p.blob(x, y, 1, 0xa03040);
                    p.dot(x - 1, y - 1, 0xc85a6a);
                }
            }
        }
    }

    private static void sapling(Painter p) {
        p.clearAlpha();
        int cx = p.size / 2;
        for (int t = 0; t < p.size * 0.55f; t++) {
            p.set(cx, p.size - 1 - t, 0x5a4430);
        }
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float d = (float) Math.hypot(x - cx, y - p.size * 0.32f);
                float n = p.noise(x, y, 8, 151);
                if (d < p.size * 0.22f + n * 5 && n > 0.3f) {
                    p.set(x, y, p.shade(0x4e7440, 0.7f + n * 0.5f));
                }
            }
        }
    }

    private static void herb(Painter p) {
        p.clearAlpha();
        for (int i = 0; i < 7; i++) {
            float bx = 8 + p.rf(i * 17) * (p.size - 16);
            int h = (int) (p.size * (0.4f + p.rf(i * 17 + 1) * 0.35f));
            for (int t = 0; t < h; t++) {
                int x = (int) (bx + Math.sin(t * 0.2f + i) * 2);
                p.set(x, p.size - 1 - t, 0x4c7050);
            }
            // Pale teal flower tip (alien accent).
            int tx = (int) (bx + Math.sin(h * 0.2f + i) * 2);
            p.blob(tx, p.size - 1 - h, 1, 0x8fd8c8);
            p.dot(tx, p.size - 2 - h, 0xbdf0e4);
        }
    }

    private static void ore(Painter p, int nugget, int accent) {
        stone(p, 0x8a8c92, 1f);
        for (int i = 0; i < 9; i++) {
            int x = 6 + p.ri(i * 37) % (p.size - 12);
            int y = 6 + p.ri(i * 37 + 1) % (p.size - 12);
            int r = 2 + (int) (p.rf(i * 37 + 2) * 2.5f);
            p.pebble(x, y, r + 1, p.shade(nugget, 0.5f));
            p.pebble(x, y, r, p.shade(nugget, 0.95f + p.rf(i) * 0.25f));
            if (p.rf(i * 37 + 3) < 0.5f) {
                p.dot(x - 1, y - 1, accent);
            }
        }
    }

    private static void ash(Painter p) {
        p.fillFbm(0x2c2a28, 0.16f, 5, 161);
        p.cracks(0x151312, 0.5f, 162, 10);
        // A few dying embers.
        for (int i = 0; i < 5; i++) {
            int x = p.ri(i * 53 + 900), y = p.ri(i * 53 + 901);
            p.dot(x, y, 0xb84a10);
            p.dot(x + 1, y, 0x7a2e08);
        }
        p.grain(0.04f);
    }

    private static void scrap(Painter p) {
        metal(p, 0x62666c);
        // Panel seams.
        p.line(0, p.size / 3, p.size - 1, p.size / 3, 0x3a3d42);
        p.line(p.size / 2, p.size / 3, p.size / 2, p.size - 1, 0x3a3d42);
        // Rust patches.
        p.fillFbmMasked(0x8a4a26, 10, 171, 0.62f, 0.75f);
        // Rivets.
        for (int i = 0; i < 8; i++) {
            p.dot(4 + i * 8, p.size / 3 + 3, 0x9aa0a8);
        }
    }

    private static void podHull(Painter p) {
        metal(p, 0xd8d4c8);
        int q = p.size / 2;
        p.line(0, q, p.size - 1, q, 0xa8a49a);
        p.line(q, 0, q, p.size - 1, 0xa8a49a);
        // Safety-orange stripe.
        for (int y = 6; y < 12; y++) {
            for (int x = 0; x < p.size; x++) {
                p.set(x, y, p.shade(0xe8641e, 0.9f + p.noise(x, y, 8, 181) * 0.2f));
            }
        }
        for (int i = 0; i < 10; i++) {
            p.dot(3 + i * 6, q + 3, 0x8a877e);
        }
        // Scorch smudges from reentry.
        p.fillFbmMasked(0x3a352e, 8, 182, 0.68f, 0.55f);
    }

    private static void ruinCore(Painter p) {
        p.fillFbm(0x23263a, 0.10f, 4, 191);
        p.veins(0x39d0d8, 0.55f, 192, 0.9f);
        p.veins(0x8a6fd0, 0.3f, 193, 0.6f);
        p.grain(0.03f);
    }

    private static void bone(Painter p) {
        p.fillFbm(0xd9d2bd, 0.08f, 5, 201);
        p.cracks(0x9a927c, 0.4f, 202, 7);
        p.grain(0.03f);
    }

    private static void furnaceSide(Painter p, boolean front) {
        // Rough stone bricks.
        p.fillFbm(0x66646a, 0.10f, 4, 211);
        int bh = p.size / 4;
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                int row = y / bh;
                int off = (row % 2) * (p.size / 4);
                if (y % bh == 0 || (x + off) % (p.size / 2) == 0) {
                    p.set(x, y, p.shade(0x44434a, 0.9f + p.noise(x, y, 8, 212) * 0.2f));
                }
            }
        }
        p.grain(0.05f);
        if (front) {
            // Glowing firebox mouth: dark arch, bright coals at the bottom.
            int cx = p.size / 2, w = p.size / 3, h = p.size / 4;
            for (int y = p.size - h - 6; y < p.size - 6; y++) {
                for (int x = cx - w / 2; x < cx + w / 2; x++) {
                    float fy = (y - (p.size - h - 6)) / (float) h;
                    int glow = p.lerpColor(0x1a1210, 0xff9a30, fy * fy);
                    p.set(x, y, p.shade(glow, 0.85f + p.noise(x, y, 8, 213) * 0.3f));
                }
            }
        }
    }

    private static void metal(Painter p, int base) {
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float brush = p.noise(x * 4, y, 32, 221);
                p.set(x, y, p.shade(base, 0.9f + brush * 0.18f));
            }
        }
        p.grain(0.02f);
    }

    private static void torchHead(Painter p) {
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float n = p.noise(x, y, 6, 231);
                p.set(x, y, p.lerpColor(0xffdd66, 0xff7a1a, Math.min(1f, n * 1.4f)));
            }
        }
    }

    private static void crate(Painter p, boolean top) {
        planks(p, 0x8a6a42, top);
        // Frame border and diagonal brace.
        p.box(0, 0, p.size - 1, p.size - 1, 3, 0x5a4630);
        if (!top) {
            for (int t = -1; t <= 1; t++) {
                p.line(3, 3 + t, p.size - 4, p.size - 4 + t, 0x5a4630);
            }
        }
        // Metal corner caps.
        int c = 6;
        for (int[] corner : new int[][]{{0, 0}, {p.size - c, 0}, {0, p.size - c}, {p.size - c, p.size - c}}) {
            for (int y = 0; y < c; y++) {
                for (int x = 0; x < c; x++) {
                    p.set(corner[0] + x, corner[1] + y, p.shade(0x8a8e94, 0.9f + p.rf(x + y * 7) * 0.2f));
                }
            }
        }
    }

    private static void workbenchTop(Painter p) {
        planks(p, 0x9a7444, false);
        // Tool wear marks.
        for (int i = 0; i < 12; i++) {
            int x = p.ri(i * 61 + 400) % (p.size - 8) + 4;
            int y = p.ri(i * 61 + 401) % (p.size - 8) + 4;
            p.line(x, y, x + 3 + (int) (p.rf(i) * 4), y + 1, p.shade(0x5a4430, 0.8f));
        }
        p.box(0, 0, p.size - 1, p.size - 1, 2, 0x6e5638);
    }

    // ------------------------------------------------------------------
    // Deep Frontier expansion tiles
    // ------------------------------------------------------------------

    private static void basalt(Painter p) {
        p.fillFbm(0x3a3c44, 0.10f, 4, 611);
        // Columnar cooling bands: vertical low-frequency shading.
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float col = p.noise(x * 3, y / 3, 12, 612);
                if (col > 0.72f) {
                    p.mulAt(x, y, 0.82f);
                }
            }
        }
        p.cracks(0x17181c, 0.4f, 613, 9);
        p.grain(0.045f);
    }

    private static void sulfurOre(Painter p) {
        basalt(p);
        // Crusted yellow deposits growing along fissures.
        for (int i = 0; i < 8; i++) {
            int x = 6 + p.ri(i * 41 + 620) % (p.size - 12);
            int y = 6 + p.ri(i * 41 + 621) % (p.size - 12);
            int r = 2 + (int) (p.rf(i * 41 + 622) * 3f);
            p.pebble(x, y, r + 1, p.shade(0x8a7a14, 0.7f));
            p.pebble(x, y, r, p.shade(0xd8c62e, 0.9f + p.rf(i) * 0.3f));
            p.dot(x + 1, y - 1, 0xf0e468);
        }
    }

    private static void saltpeterOre(Painter p) {
        stone(p, 0x8a8c92, 1f);
        // Pale crystalline crusts in patchy seams.
        p.fillFbmMasked(0xd8d4c2, 9, 631, 0.66f, 0.8f);
        for (int i = 0; i < 10; i++) {
            int x = 4 + p.ri(i * 43 + 632) % (p.size - 8);
            int y = 4 + p.ri(i * 43 + 633) % (p.size - 8);
            p.dot(x, y, 0xf2efdf);
            p.dot(x + 1, y, 0xcac4a8);
        }
    }

    private static void glowFungus(Painter p) {
        p.clearAlpha();
        // Cluster of stalked caps with soft-glow rims.
        for (int i = 0; i < 5; i++) {
            int bx = 8 + (int) (p.rf(i * 51 + 641) * (p.size - 16));
            int h = (int) (p.size * (0.25f + p.rf(i * 51 + 642) * 0.35f));
            for (int t = 0; t < h; t++) {
                p.set(bx, p.size - 1 - t, 0x3a5648);
                p.set(bx + 1, p.size - 1 - t, 0x2e463c);
            }
            int cy = p.size - 1 - h;
            int r = 3 + (int) (p.rf(i * 51 + 643) * 3f);
            p.pebble(bx, cy, r + 1, 0x2f6a55);
            p.pebble(bx, cy, r, 0x59c79b);
            p.dot(bx, cy - r + 1, 0xa8f2d4);
            p.dot(bx - 1, cy, 0x8ee8c4);
        }
    }

    private static void ladder(Painter p) {
        p.clearAlpha();
        int left = p.size / 5, right = p.size - p.size / 5;
        for (int y = 0; y < p.size; y++) {
            // Twisted fiber side ropes.
            int wob = (int) (p.noise(0, y, 10, 651) * 2);
            p.set(left + wob, y, 0x7a6236);
            p.set(left + 1 + wob, y, 0x93794a);
            p.set(right + wob, y, 0x7a6236);
            p.set(right - 1 + wob, y, 0x93794a);
        }
        for (int r = 0; r < 4; r++) {
            int y = 6 + r * (p.size / 4);
            for (int x = left; x <= right; x++) {
                p.set(x, y, p.shade(0x8a6a42, 0.85f + p.noise(x, y, 8, 652) * 0.3f));
                p.set(x, y + 1, p.shade(0x6e5432, 0.9f));
            }
        }
    }

    private static void lanternGlass(Painter p) {
        // Warm glass panes behind a dark iron frame.
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float n = p.noise(x, y, 7, 661);
                p.set(x, y, p.lerpColor(0xffd985, 0xdf8f2a, Math.min(1f, n * 1.3f)));
            }
        }
        p.box(0, 0, p.size - 1, p.size - 1, 3, 0x33322f);
        p.line(p.size / 2, 0, p.size / 2, p.size - 1, 0x33322f);
        p.line(0, p.size / 2, p.size - 1, p.size / 2, 0x33322f);
    }

    private static void lanternGlassUnlit(Painter p) {
        // The same physical panes without the flame's emissive amber core.
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float n = p.noise(x, y, 7, 662);
                p.set(x, y, p.lerpColor(0x5f625e, 0x353936, Math.min(1f, n * 1.3f)));
            }
        }
        p.box(0, 0, p.size - 1, p.size - 1, 3, 0x292b2a);
        p.line(p.size / 2, 0, p.size / 2, p.size - 1, 0x292b2a);
        p.line(0, p.size / 2, p.size - 1, p.size / 2, 0x292b2a);
    }

    private static void markerPaint(Painter p) {
        // Bright chalk-orange strips over weathered cloth: readable in the dark.
        fabric(p, 0x8a7c60);
        for (int y = p.size / 5; y < p.size - p.size / 5; y++) {
            for (int x = 0; x < p.size; x++) {
                if ((y / (p.size / 5)) % 2 == 1) {
                    p.set(x, y, p.shade(0xf07a28, 0.9f + p.noise(x, y, 8, 671) * 0.25f));
                }
            }
        }
    }

    private static void keg(Painter p, boolean top) {
        if (top) {
            planks(p, 0x7a5a34, false);
            // Powder emblem: black circle with a cross of grains.
            float c = p.size / 2f;
            for (int y = 0; y < p.size; y++) {
                for (int x = 0; x < p.size; x++) {
                    float d = (float) Math.hypot(x - c, y - c);
                    if (d < p.size * 0.22f) {
                        p.set(x, y, p.shade(0x1d1a18, 0.9f + p.noise(x, y, 6, 681) * 0.3f));
                    }
                }
            }
            for (int i = 0; i < 10; i++) {
                p.dot((int) (c + (p.rf(i * 3 + 682) - 0.5f) * p.size * 0.3f),
                        (int) (c + (p.rf(i * 3 + 683) - 0.5f) * p.size * 0.3f), 0x4a4440);
            }
        } else {
            // Vertical staves with two iron hoops.
            planks(p, 0x7a5634, true);
            for (int band : new int[]{p.size / 5, p.size - p.size / 5 - 4}) {
                for (int y = band; y < band + 4; y++) {
                    for (int x = 0; x < p.size; x++) {
                        p.set(x, y, p.shade(0x4a4c52, 0.85f + p.noise(x, y, 12, 684) * 0.3f));
                    }
                }
            }
        }
    }

    private static void gate(Painter p) {
        planks(p, 0x6a5030, true);
        // Heavy horizontal crossbars and iron studs.
        for (int band : new int[]{p.size / 6, p.size / 2, p.size - p.size / 6 - 5}) {
            for (int y = band; y < band + 5; y++) {
                for (int x = 0; x < p.size; x++) {
                    p.set(x, y, p.shade(0x51391f, 0.85f + p.noise(x, y, 10, 691) * 0.3f));
                }
            }
            for (int i = 0; i < 6; i++) {
                p.dot(5 + i * 10, band + 2, 0x8a8e94);
            }
        }
    }

    private static void stoneBrick(Painter p) {
        p.fillFbm(0x74767e, 0.08f, 4, 701);
        int bh = p.size / 4;
        int bw = p.size / 2;
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                int row = y / bh;
                int off = (row % 2) * (bw / 2);
                if (y % bh == 0 || (x + off) % bw == 0) {
                    p.set(x, y, p.shade(0x4a4c54, 0.9f + p.noise(x, y, 8, 702) * 0.2f));
                } else {
                    float wear = p.noise(x, y, 9, 703);
                    if (wear > 0.8f) {
                        p.mulAt(x, y, 0.88f);
                    }
                }
            }
        }
        p.grain(0.04f);
    }

    private static void bellBronze(Painter p) {
        metal(p, 0x9a7a30);
        // Verdigris streaks on old bronze.
        p.fillFbmMasked(0x4f8a6e, 8, 711, 0.7f, 0.5f);
        p.grain(0.03f);
    }

    private static void cageBars(Painter p) {
        // Dark cell interior behind a riveted bar grid.
        p.fillFbm(0x1c1d22, 0.10f, 4, 721);
        for (int x = 4; x < p.size; x += p.size / 5) {
            for (int y = 0; y < p.size; y++) {
                float brush = p.noise(x, y * 2, 14, 722);
                p.set(x, y, p.shade(0x585c64, 0.85f + brush * 0.3f));
                p.set(x + 1, y, p.shade(0x43464e, 0.85f + brush * 0.3f));
            }
        }
        for (int y = 4; y < p.size; y += p.size / 3) {
            for (int x = 0; x < p.size; x++) {
                p.set(x, y, p.shade(0x505460, 0.9f));
            }
        }
    }

    private static void beacon(Painter p, boolean lit) {
        metal(p, 0xcfd2d4);
        int stripe = lit ? 0x54f0f4 : 0x2a8a90;
        for (int y = p.size / 2 - 4; y < p.size / 2 + 4; y++) {
            for (int x = 0; x < p.size; x++) {
                p.set(x, y, p.shade(stripe, 0.9f + p.noise(x, y, 8, 241) * 0.2f));
            }
        }
        p.box(0, 0, p.size - 1, p.size - 1, 2, 0x8f9296);
        for (int i = 0; i < 6; i++) {
            p.dot(5 + i * 10, 5, 0x74787c);
            p.dot(5 + i * 10, p.size - 6, 0x74787c);
        }
    }

    private static void fabric(Painter p, int base) {
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                boolean weave = ((x / 2) + (y / 2)) % 2 == 0;
                float n = p.noise(x, y, 16, 251);
                p.set(x, y, p.shade(base, (weave ? 1.0f : 0.88f) * (0.9f + n * 0.2f)));
            }
        }
    }

    private static void herbStation(Painter p) {
        planks(p, 0x8a6a3e, true);
        p.fillFbmMasked(0x4e7440, 8, 261, 0.6f, 0.6f);
    }

    private static void mapTable(Painter p) {
        planks(p, 0x9a7444, false);
        // Blue map sheet.
        int m = p.size / 8;
        for (int y = m; y < p.size - m; y++) {
            for (int x = m; x < p.size - m; x++) {
                p.set(x, y, p.shade(0x39566e, 0.92f + p.noise(x, y, 10, 271) * 0.16f));
            }
        }
        p.line(m * 2, m * 2, p.size - m * 2, p.size / 2, 0x9ec4dd);
        p.line(p.size / 2, m * 2, p.size / 3, p.size - m * 2, 0x9ec4dd);
        p.dot(p.size / 2, p.size / 2, 0xe8641e);
    }

    private static void water(Painter p) {
        p.fillFbm(0x2a4a6e, 0.10f, 5, 281);
    }

    // ------------------------------------------------------------------
    // Painter: tiny deterministic canvas
    // ------------------------------------------------------------------

    static final class Painter {
        final int size;
        final int[] pixels;
        final int seed;

        Painter(int size, int seed) {
            this.size = size;
            this.seed = seed;
            this.pixels = new int[size * size];
            java.util.Arrays.fill(pixels, 0xFF000000);
        }

        void clearAlpha() {
            java.util.Arrays.fill(pixels, 0);
        }

        int get(int x, int y) {
            if (x < 0 || y < 0 || x >= size || y >= size) {
                return 0;
            }
            return pixels[y * size + x];
        }

        void set(int x, int y, int rgb) {
            if (x < 0 || y < 0 || x >= size || y >= size) {
                return;
            }
            pixels[y * size + x] = 0xFF000000 | (rgb & 0xFFFFFF);
        }

        void dot(int x, int y, int rgb) {
            set(x, y, rgb);
        }

        void blob(int cx, int cy, int r, int rgb) {
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    if (x * x + y * y <= r * r) {
                        set(cx + x, cy + y, rgb);
                    }
                }
            }
        }

        void pebble(int cx, int cy, int r, int rgb) {
            for (int y = -r; y <= r; y++) {
                for (int x = -r; x <= r; x++) {
                    float d = (x * x + y * y) / (float) (r * r);
                    if (d <= 1f) {
                        // Simple top-left light.
                        float l = 1f - 0.3f * d + (x + y < 0 ? 0.12f : -0.08f);
                        set(cx + x, cy + y, shade(rgb, l));
                    }
                }
            }
        }

        void line(int x0, int y0, int x1, int y1, int rgb) {
            int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0);
            int sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
            while (true) {
                set(x0, y0, rgb);
                if (x0 == x1 && y0 == y1) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 >= dy) {
                    err += dy;
                    x0 += sx;
                }
                if (e2 <= dx) {
                    err += dx;
                    y0 += sy;
                }
            }
        }

        void box(int x0, int y0, int x1, int y1, int t, int rgb) {
            for (int i = 0; i < t; i++) {
                line(x0 + i, y0 + i, x1 - i, y0 + i, rgb);
                line(x0 + i, y1 - i, x1 - i, y1 - i, rgb);
                line(x0 + i, y0 + i, x0 + i, y1 - i, rgb);
                line(x1 - i, y0 + i, x1 - i, y1 - i, rgb);
            }
        }

        /** Fills with base color modulated by tileable fbm noise. */
        void fillFbm(int base, float amp, int period, int noiseSeed) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float n = fbm(x, y, period, noiseSeed);
                    set(x, y, shade(base, 1f - amp + n * amp * 2f));
                }
            }
        }

        /** Blends toward a second color where noise exceeds a threshold. */
        void fillFbmBlend(int color, float threshold, int period, int noiseSeed, float strength) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float n = fbm(x, y, period, noiseSeed);
                    if (n > threshold) {
                        float t = Math.min(1f, (n - threshold) / (1f - threshold)) * strength;
                        set(x, y, lerpColor(get(x, y), color, t));
                    }
                }
            }
        }

        /** Overwrites pixels with color where noise exceeds threshold (patches). */
        void fillFbmMasked(int color, int period, int noiseSeed, float threshold, float strength) {
            fillFbmBlend(color, threshold, period, noiseSeed, strength);
        }

        /** Thin dark ridged-noise cracks. */
        void cracks(int color, float strength, int noiseSeed, int period) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float n = noise(x, y, period, noiseSeed);
                    float ridge = 1f - Math.abs(2f * n - 1f);
                    if (ridge > 0.93f) {
                        set(x, y, lerpColor(get(x, y), color, strength));
                    }
                }
            }
        }

        /** Bright branching energy veins. */
        void veins(int color, float coverage, int noiseSeed, float strength) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    float n = noise(x, y, 7, noiseSeed);
                    float ridge = 1f - Math.abs(2f * n - 1f);
                    if (ridge > 1f - coverage * 0.15f) {
                        set(x, y, lerpColor(get(x, y), color, strength));
                    } else if (ridge > 1f - coverage * 0.3f) {
                        set(x, y, lerpColor(get(x, y), color, strength * 0.4f));
                    }
                }
            }
        }

        /** Per-pixel value jitter. */
        void grain(float amp) {
            for (int y = 0; y < size; y++) {
                for (int x = 0; x < size; x++) {
                    if ((get(x, y) >>> 24) == 0) {
                        continue;
                    }
                    float j = 1f - amp + hash(x, y, seed ^ 0x5bd1e995) * amp * 2f;
                    mulAt(x, y, j);
                }
            }
        }

        void mulAt(int x, int y, float f) {
            int c = get(x, y);
            if ((c >>> 24) == 0) {
                return;
            }
            set(x, y, shade(c, f));
        }

        int shade(int rgb, float f) {
            int r = Math.min(255, Math.max(0, (int) (((rgb >> 16) & 0xFF) * f)));
            int g = Math.min(255, Math.max(0, (int) (((rgb >> 8) & 0xFF) * f)));
            int b = Math.min(255, Math.max(0, (int) ((rgb & 0xFF) * f)));
            return (r << 16) | (g << 8) | b;
        }

        int lerpColor(int a, int b, float t) {
            t = Math.min(1f, Math.max(0f, t));
            int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
            int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
            return ((int) (ar + (br - ar) * t) << 16)
                    | ((int) (ag + (bg - ag) * t) << 8)
                    | (int) (ab + (bb - ab) * t);
        }

        /** Deterministic 0..1 hash. */
        float hash(int x, int y, int s) {
            int h = x * 374761393 + y * 668265263 + s * 1274126177;
            h = (h ^ (h >>> 13)) * 1103515245;
            h ^= h >>> 16;
            return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
        }

        /** Tileable value noise: lattice wraps at period. */
        float noise(float x, float y, int period, int noiseSeed) {
            float fx = x / size * period;
            float fy = y / size * period;
            int x0 = (int) Math.floor(fx), y0 = (int) Math.floor(fy);
            float tx = fx - x0, ty = fy - y0;
            tx = tx * tx * (3 - 2 * tx);
            ty = ty * ty * (3 - 2 * ty);
            int s = seed * 31 + noiseSeed;
            float a = hash(Math.floorMod(x0, period), Math.floorMod(y0, period), s);
            float b = hash(Math.floorMod(x0 + 1, period), Math.floorMod(y0, period), s);
            float c = hash(Math.floorMod(x0, period), Math.floorMod(y0 + 1, period), s);
            float d = hash(Math.floorMod(x0 + 1, period), Math.floorMod(y0 + 1, period), s);
            return a + (b - a) * tx + (c - a) * ty + (a - b - c + d) * tx * ty;
        }

        /** 3-octave tileable fbm, 0..1. */
        float fbm(float x, float y, int period, int noiseSeed) {
            float sum = 0, amp = 0.55f, tot = 0;
            int per = period;
            for (int o = 0; o < 3; o++) {
                sum += noise(x, y, per, noiseSeed + o * 101) * amp;
                tot += amp;
                amp *= 0.5f;
                per *= 2;
            }
            return sum / tot;
        }

        int ri(int i) {
            return (int) (hash(i, i * 7 + 1, seed) * size) % size;
        }

        float rf(int i) {
            return hash(i * 3 + 5, i, seed);
        }
    }
}
