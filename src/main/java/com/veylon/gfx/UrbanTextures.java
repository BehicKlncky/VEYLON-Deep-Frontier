package com.veylon.gfx;

import com.veylon.gfx.ProceduralTextures.Painter;

/**
 * Procedural tiles for the urban materials and fixtures of package 04: a
 * restrained set of concrete, brick, plaster, asphalt, paving and sheet metal,
 * with weathered and damaged looks as position-hash variants rather than new
 * block ids, plus doors, windows, stairs and furniture.
 *
 * <p>Palette per {@code docs/graphics/ART_DIRECTION.md}: cold greys and worn
 * off-white for human structures, dark grey metal, and safety orange only as a
 * small accent where it helps reading a hazard (stair edges). Windows and the
 * remains of broken fixtures use binary cutout alpha, the only transparency the
 * chunk shader has; clear glass is drawn as frame, reflection streaks and grime
 * over transparent texels.
 *
 * <p>Deterministic like {@link ProceduralTextures}: every tile derives from its
 * id and variant only.
 */
final class UrbanTextures {

    private UrbanTextures() {
    }

    /** Paints an urban tile; false for an id this class does not own. */
    static boolean paint(String tileId, int variant, Painter p) {
        switch (tileId) {
            case "asphalt" -> asphalt(p, variant);
            case "paving" -> paving(p, variant);
            case "concrete" -> concrete(p, variant);
            case "brick" -> brick(p, variant);
            case "plaster" -> plaster(p, variant);
            case "sheet_metal" -> sheetMetal(p);
            case "rubble" -> rubble(p);
            case "door_wood_lower" -> woodDoor(p, false);
            case "door_wood_upper" -> woodDoor(p, true);
            case "door_metal_lower" -> metalDoor(p, false);
            case "door_metal_upper" -> metalDoor(p, true);
            case "door_broken" -> brokenDoor(p);
            case "window_glass" -> windowGlass(p);
            case "window_broken" -> brokenWindow(p);
            case "stair_tread" -> stairTread(p);
            case "stair_riser" -> stairRiser(p);
            case "shelf_side" -> shelfSide(p);
            case "shelf_top" -> planksFlat(p, 0x7a6448);
            case "cabinet_front" -> cabinetFront(p);
            case "cabinet_top" -> counterTop(p);
            case "locker_front" -> lockerFront(p);
            case "locker_top" -> lockerTop(p);
            case "table_top" -> planksFlat(p, 0x8a6a44);
            case "debris" -> debris(p);
            case "sign_board" -> signBoard(p, variant);
            case "sign_edge" -> planksFlat(p, 0x5a4a36);
            case "contaminated" -> contaminated(p, variant);
            case "railing" -> railing(p);
            case "railing_top" -> railingTop(p);
            case "hidden_panel" -> hiddenPanel(p, variant);
            case "growth_node" -> growthNode(p, false);
            case "growth_node_top" -> growthNode(p, true);
            case "veil_membrane" -> veilMembrane(p, variant);
            case "car_wreck_side" -> carWreck(p, false);
            case "car_wreck_top" -> carWreck(p, true);
            case "barricade" -> barricade(p);
            case "barricade_metal" -> metalBarricade(p);
            default -> {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Structural materials
    // ------------------------------------------------------------------

    private static void asphalt(Painter p, int variant) {
        p.fillFbm(0x3c3d40, 0.10f, 6, 1101);
        p.grain(0.08f);
        // Aggregate: pale chips in the binder.
        for (int i = 0; i < 70; i++) {
            int x = p.ri(i * 3 + 1102), y = p.ri(i * 3 + 1103);
            p.dot(x, y, p.shade(0x8a8a86, 0.8f + p.rf(i + 1104) * 0.4f));
        }
        if (variant == 1) {
            // Weathered: a sealed crack line and a darker patch repair.
            p.cracks(0x18181a, 0.7f, 1105, 5);
            int x0 = 10, y0 = 34;
            for (int y = y0; y < y0 + 18; y++) {
                for (int x = x0; x < x0 + 26; x++) {
                    p.set(x, y, p.shade(0x2c2d30, 0.92f + p.noise(x, y, 9, 1106) * 0.16f));
                }
            }
            p.box(x0, y0, x0 + 25, y0 + 17, 1, 0x232426);
        }
    }

    private static void paving(Painter p, int variant) {
        int slab = p.size / 2;
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                int sx = x / slab, sy = y / slab;
                float tone = 0.9f + p.hash(sx, sy, 1111) * 0.14f;
                float n = p.fbm(x, y, 6, 1112);
                p.set(x, y, p.shade(0x8f8c86, tone * (0.92f + n * 0.14f)));
                if (x % slab == 0 || y % slab == 0) {
                    p.set(x, y, 0x5c5953);
                }
            }
        }
        p.grain(0.05f);
        if (variant == 1) {
            // One cracked, grimy slab.
            p.line(6, 4, 22, 26, 0x45423e);
            p.line(22, 26, 30, 30, 0x45423e);
            p.fillFbmBlend(0x5e5a50, 0.62f, 7, 1113, 0.45f);
        }
    }

    private static void concrete(Painter p, int variant) {
        p.fillFbm(0x9a9a96, 0.06f, 5, 1121);
        p.fillFbmBlend(0x8a8a86, 0.55f, 9, 1122, 0.3f);
        p.grain(0.05f);
        // Faint formwork board lines.
        for (int y = 15; y < p.size; y += 16) {
            for (int x = 0; x < p.size; x++) {
                p.mulAt(x, y, 0.96f);
            }
        }
        // A few pores.
        for (int i = 0; i < 18; i++) {
            p.dot(p.ri(i * 2 + 1123), p.ri(i * 2 + 1124), 0x7e7e7a);
        }
        if (variant == 1) {
            // Rain-streaked: slightly darker vertical runs from the top.
            for (int i = 0; i < 5; i++) {
                int x = p.ri(i + 1125);
                int len = 18 + (int) (p.rf(i + 1126) * 40);
                for (int y = 0; y < len; y++) {
                    float f = 0.88f + 0.1f * y / len;
                    p.mulAt(x, y, f);
                    p.mulAt(x + 1, y, Math.min(1f, f + 0.05f));
                }
            }
        } else if (variant == 2) {
            // Damaged: hairline cracks and a small spalled corner.
            p.cracks(0x5a5a56, 0.35f, 1127, 6);
            for (int y = 50; y < p.size; y++) {
                for (int x = 0; x < 14 - (y - 50); x++) {
                    p.set(x, y, p.shade(0x80807a, 0.9f + p.noise(x, y, 12, 1128) * 0.2f));
                }
            }
        }
    }

    private static void brick(Painter p, int variant) {
        int bw = 16, bh = 8;
        for (int y = 0; y < p.size; y++) {
            int row = y / bh;
            int off = (row % 2) * (bw / 2);
            for (int x = 0; x < p.size; x++) {
                int col = Math.floorMod(x + off, p.size) / bw;
                boolean mortar = y % bh == 0 || (x + off) % bw == 0;
                if (mortar) {
                    p.set(x, y, p.shade(0x8c857a, 0.9f + p.noise(x, y, 16, 1131) * 0.2f));
                } else {
                    float tone = 0.86f + p.hash(col, row, 1132) * 0.24f;
                    p.set(x, y, p.shade(0x8a4a3a, tone * (0.92f + p.noise(x, y, 12, 1133) * 0.16f)));
                }
            }
        }
        p.grain(0.05f);
        if (variant == 1) {
            // Soot-stained: dark smoke shadow from above.
            for (int y = 0; y < p.size; y++) {
                for (int x = 0; x < p.size; x++) {
                    float n = p.fbm(x, y, 5, 1134);
                    float soot = Math.max(0f, 0.75f - y / (float) p.size) * n;
                    p.mulAt(x, y, 1f - soot * 0.8f);
                }
            }
        }
    }

    private static void plaster(Painter p, int variant) {
        p.fillFbm(0xc9c2b2, 0.05f, 4, 1141);
        p.fillFbmBlend(0xbab3a3, 0.6f, 8, 1142, 0.25f);
        p.grain(0.03f);
        if (variant == 1) {
            // Water stain: a yellowed tide mark.
            p.fillFbmBlend(0xa89a78, 0.6f, 3, 1143, 0.55f);
        } else if (variant == 2) {
            // Peeling: patches where the coat has fallen away to grey render.
            for (int y = 0; y < p.size; y++) {
                for (int x = 0; x < p.size; x++) {
                    float n = p.fbm(x, y, 4, 1144);
                    if (n > 0.66f) {
                        p.set(x, y, p.shade(0x8e8a82, 0.9f + p.noise(x, y, 14, 1145) * 0.2f));
                    } else if (n > 0.63f) {
                        p.set(x, y, 0x6e6a62);
                    }
                }
            }
        }
    }

    private static void sheetMetal(Painter p) {
        // Corrugated panel: vertical ribs, rust bloom and a rivet line.
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float rib = (float) Math.sin(x / (float) p.size * Math.PI * 16) * 0.5f + 0.5f;
                p.set(x, y, p.shade(0x6f777c, 0.78f + rib * 0.3f));
            }
        }
        p.fillFbmBlend(0x7a4a2e, 0.68f, 6, 1151, 0.65f);
        p.grain(0.04f);
        for (int x = 2; x < p.size; x += 8) {
            p.dot(x, 3, 0x9aa2a6);
            p.dot(x, p.size - 4, 0x9aa2a6);
        }
    }

    private static void rubble(Painter p) {
        p.fillFbm(0x3a3836, 0.12f, 6, 1161);
        // Broken concrete and brick lumps over dark voids.
        for (int i = 0; i < 26; i++) {
            int x = p.ri(i * 4 + 1162), y = p.ri(i * 4 + 1163);
            int r = 3 + (int) (p.rf(i + 1164) * 5);
            int base = p.rf(i + 1165) < 0.3f ? 0x7a4636 : 0x8e8c86;
            p.pebble(x, y, r, p.shade(base, 0.8f + p.rf(i + 1166) * 0.3f));
        }
        p.grain(0.06f);
    }

    // ------------------------------------------------------------------
    // Doors and windows
    // ------------------------------------------------------------------

    private static void woodDoor(Painter p, boolean upper) {
        // Painted panel door, worn to wood at the edges; the handle sits at the
        // top of the lower cell, about a metre off the floor.
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float grain = p.noise(x * 0.4f, y * 3f, 8, 1201);
                p.set(x, y, p.shade(0x7a5a3c, 0.88f + grain * 0.2f));
            }
        }
        p.box(0, 0, p.size - 1, p.size - 1, 4, 0x4e3824);
        int top = upper ? 10 : 22, bottom = upper ? p.size - 4 : p.size - 10;
        p.box(12, top, p.size - 13, bottom, 2, 0x5e432c);
        p.fillFbmBlend(0x9a7c58, 0.7f, 6, 1202, 0.4f);
        if (!upper) {
            p.blob(p.size - 12, 9, 2, 0xb0a890);
            p.dot(p.size - 12, 9, 0xe0dcd0);
        }
        p.grain(0.04f);
    }

    private static void metalDoor(Painter p, boolean upper) {
        p.fillFbm(0x56606a, 0.07f, 5, 1211);
        p.box(0, 0, p.size - 1, p.size - 1, 3, 0x3a3d42);
        if (upper) {
            // Small wired-glass vision panel.
            for (int y = 12; y < 36; y++) {
                for (int x = 22; x < 42; x++) {
                    p.set(x, y, p.shade(0x2a3a44, 0.9f + p.noise(x, y, 10, 1212) * 0.3f));
                }
            }
            for (int i = 22; i < 42; i += 5) {
                p.line(i, 12, i, 35, 0x5a6670);
            }
            for (int j = 12; j < 36; j += 5) {
                p.line(22, j, 41, j, 0x5a6670);
            }
            p.box(21, 11, 42, 36, 1, 0x30343a);
        } else {
            // Push bar and a scuffed kick plate.
            for (int x = 8; x < p.size - 8; x++) {
                p.set(x, 10, 0x9aa2a8);
                p.set(x, 11, 0x6e767c);
            }
            for (int y = p.size - 16; y < p.size - 3; y++) {
                for (int x = 3; x < p.size - 3; x++) {
                    p.set(x, y, p.shade(0x6e767c, 0.85f + p.noise(x, y, 14, 1213) * 0.3f));
                }
            }
        }
        // Paint wear and a little rust.
        p.fillFbmBlend(0x7a5a40, 0.74f, 7, 1214, 0.5f);
        p.grain(0.04f);
    }

    private static void brokenDoor(Painter p) {
        // What is left of a forced door: splintered stiles on the hinge and
        // latch sides and a ragged stub at the bottom; the middle is open.
        p.clearAlpha();
        for (int y = 0; y < p.size; y++) {
            int left = 6 + (int) (p.noise(0, y, 9, 1221) * 8);
            int right = 4 + (int) (p.noise(5, y, 9, 1222) * 6);
            for (int x = 0; x < left; x++) {
                p.set(x, y, p.shade(0x6a4c30, 0.8f + p.noise(x * 3f, y, 8, 1223) * 0.3f));
            }
            for (int x = p.size - right; x < p.size; x++) {
                p.set(x, y, p.shade(0x6a4c30, 0.8f + p.noise(x * 3f, y, 8, 1224) * 0.3f));
            }
        }
        for (int x = 0; x < p.size; x++) {
            int stub = 3 + (int) (p.noise(x, 0, 11, 1225) * 7);
            for (int y = p.size - stub; y < p.size; y++) {
                p.set(x, y, p.shade(0x5e4228, 0.8f + p.noise(x, y, 8, 1226) * 0.3f));
            }
        }
        // Pale splinter ends.
        for (int i = 0; i < 10; i++) {
            p.dot(9 + (int) (p.rf(i + 1227) * 4), p.ri(i + 1228), 0xb89a70);
        }
    }

    private static void windowGlass(Painter p) {
        p.clearAlpha();
        // Clear pane: diagonal reflection streaks and corner grime only.
        for (int k = 0; k < 3; k++) {
            int start = 10 + k * 18;
            for (int t = 0; t < p.size; t++) {
                int x = start + t / 2 - 8, y = t;
                if (x > 4 && x < p.size - 5 && y > 4 && y < p.size - 5 && (t / 3 + k) % 4 != 0) {
                    p.set(x, y, 0xb8ccd8);
                    if (k == 1) {
                        p.set(x + 1, y, 0xa2b8c6);
                    }
                }
            }
        }
        for (int i = 0; i < 40; i++) {
            int x = 5 + (int) (p.rf(i + 1231) * 10), y = p.size - 6 - (int) (p.rf(i + 1232) * 9);
            p.dot(x, y, 0x7c8078);
        }
        // Painted sash frame and a meeting rail across the middle.
        p.box(0, 0, p.size - 1, p.size - 1, 4, 0xbfb8a8);
        p.box(4, 4, p.size - 5, p.size - 5, 1, 0x8a8478);
        for (int x = 4; x < p.size - 4; x++) {
            p.set(x, p.size / 2 - 1, 0xbfb8a8);
            p.set(x, p.size / 2, 0xa8a294);
        }
    }

    private static void brokenWindow(Painter p) {
        p.clearAlpha();
        p.box(0, 0, p.size - 1, p.size - 1, 4, 0xa8a294);
        // Jagged shards still caught in the frame.
        int[][] shards = {{4, 4, 22, 4, 4, 20}, {p.size - 5, 4, p.size - 5, 26, p.size - 18, 4},
                          {4, p.size - 5, 18, p.size - 5, 4, p.size - 24},
                          {p.size - 5, p.size - 5, p.size - 26, p.size - 5, p.size - 5, p.size - 14}};
        for (int[] s : shards) {
            fillTriangle(p, s[0], s[1], s[2], s[3], s[4], s[5], 0x9fb6c4);
        }
        for (int i = 0; i < 12; i++) {
            p.dot(5 + p.ri(i + 1241) % 10, 5 + p.ri(i + 1242) % 10, 0xdce8ee);
        }
    }

    private static void fillTriangle(Painter p, int ax, int ay, int bx, int by, int cx, int cy, int rgb) {
        int minX = Math.min(ax, Math.min(bx, cx)), maxX = Math.max(ax, Math.max(bx, cx));
        int minY = Math.min(ay, Math.min(by, cy)), maxY = Math.max(ay, Math.max(by, cy));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                int w0 = (bx - ax) * (y - ay) - (by - ay) * (x - ax);
                int w1 = (cx - bx) * (y - by) - (cy - by) * (x - bx);
                int w2 = (ax - cx) * (y - cy) - (ay - cy) * (x - cx);
                boolean in = (w0 >= 0 && w1 >= 0 && w2 >= 0) || (w0 <= 0 && w1 <= 0 && w2 <= 0);
                if (in) {
                    p.set(x, y, p.shade(rgb, 0.9f + p.noise(x, y, 12, 1243) * 0.2f));
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Stairs and furniture
    // ------------------------------------------------------------------

    private static void stairTread(Painter p) {
        concrete(p, 0);
        // Worn safety-orange edge strips: a step reads as a step in poor light.
        int c = 0xb8763a;
        for (int i = 0; i < p.size; i++) {
            for (int d = 1; d < 3; d++) {
                float wear = p.noise(i, d, 12, 1251);
                if (wear > 0.25f) {
                    int col = p.shade(c, 0.8f + wear * 0.25f);
                    p.set(i, d, col);
                    p.set(i, p.size - 1 - d, col);
                    p.set(d, i, col);
                    p.set(p.size - 1 - d, i, col);
                }
            }
        }
    }

    private static void stairRiser(Painter p) {
        concrete(p, 0);
        // The nosing's shadow under the tread above, then its worn edge strip.
        for (int x = 0; x < p.size; x++) {
            for (int y = 0; y < 3; y++) {
                p.set(x, y, p.shade(0xc8742e, 0.8f + p.noise(x, y, 12, 1252) * 0.2f));
            }
            for (int y = 3; y < 8; y++) {
                p.mulAt(x, y, 0.62f + (y - 3) * 0.07f);
            }
        }
    }

    private static void shelfSide(Painter p) {
        // Steel uprights, three boards and a scatter of what was left behind.
        p.fillFbm(0x2e2c2a, 0.10f, 5, 1261);
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < 5; x++) {
                p.set(x, y, p.shade(0x6e767c, 0.9f + x * 0.03f));
                p.set(p.size - 1 - x, y, p.shade(0x6e767c, 0.9f + x * 0.03f));
            }
        }
        for (int board : new int[]{18, 39, 60}) {
            for (int y = board; y < board + 4; y++) {
                for (int x = 5; x < p.size - 5; x++) {
                    p.set(x, y, p.shade(0x7a6448, 0.9f + p.noise(x, y, 10, 1262) * 0.2f));
                }
            }
            // Boxes and tins standing on the board.
            int x = 8;
            for (int i = 0; i < 5 && x < p.size - 12; i++) {
                float r = p.rf(board * 7 + i + 1263);
                int w = 4 + (int) (r * 6);
                int h = 6 + (int) (p.rf(board * 5 + i + 1264) * 10);
                if (r > 0.25f) {
                    int col = r > 0.7f ? 0x8c7a5a : (r > 0.5f ? 0x6a7a7e : 0x9a8670);
                    for (int yy = board - h; yy < board; yy++) {
                        for (int xx = x; xx < x + w; xx++) {
                            p.set(xx, yy, p.shade(col, 0.85f + p.noise(xx, yy, 9, 1265) * 0.25f));
                        }
                    }
                }
                x += w + 2;
            }
        }
    }

    private static void planksFlat(Painter p, int base) {
        int board = p.size / 4;
        for (int y = 0; y < p.size; y++) {
            for (int x = 0; x < p.size; x++) {
                float grain = p.noise(x * 3f, y * 0.4f, 8, 1271);
                p.set(x, y, p.shade(base, 0.86f + grain * 0.22f));
                if (y % board == 0) {
                    p.set(x, y, p.shade(base, 0.6f));
                }
            }
        }
        p.fillFbmBlend(p.shade(base, 0.7f), 0.72f, 5, 1272, 0.4f);
        p.grain(0.04f);
    }

    private static void cabinetFront(Painter p) {
        // Two painted doors over a plinth, with the counter edge on top.
        p.fillFbm(0x8a7a62, 0.06f, 5, 1281);
        for (int x = 0; x < p.size; x++) {
            for (int y = 0; y < 5; y++) {
                p.set(x, y, 0xb9b2a0);
            }
            for (int y = p.size - 6; y < p.size; y++) {
                p.set(x, y, 0x3e3830);
            }
        }
        p.box(3, 7, p.size / 2 - 2, p.size - 9, 2, 0x5e5240);
        p.box(p.size / 2 + 1, 7, p.size - 4, p.size - 9, 2, 0x5e5240);
        p.blob(p.size / 2 - 6, 18, 1, 0xb0a890);
        p.blob(p.size / 2 + 5, 18, 1, 0xb0a890);
        p.fillFbmBlend(0x6a5c48, 0.72f, 6, 1282, 0.4f);
        p.grain(0.04f);
    }

    private static void counterTop(Painter p) {
        p.fillFbm(0xb9b2a0, 0.05f, 6, 1291);
        p.cracks(0x8a8474, 0.4f, 1292, 7);
        p.grain(0.03f);
    }

    private static void lockerFront(Painter p) {
        p.fillFbm(0x5a6f6a, 0.06f, 5, 1301);
        p.box(0, 0, p.size - 1, p.size - 1, 2, 0x3c4a46);
        // Vent slots, a handle and a blank number plate.
        for (int s = 0; s < 4; s++) {
            int y = 8 + s * 4;
            for (int x = 20; x < 44; x++) {
                p.set(x, y, 0x26302c);
                p.set(x, y + 1, 0x7a8c86);
            }
        }
        for (int y = 30; y < 44; y++) {
            p.set(48, y, 0x9aa2a6);
            p.set(49, y, 0x6e767c);
        }
        for (int y = 26; y < 32; y++) {
            for (int x = 24; x < 40; x++) {
                p.set(x, y, 0xc8c4b8);
            }
        }
        p.fillFbmBlend(0x7a5a40, 0.75f, 7, 1302, 0.45f);
        p.grain(0.04f);
    }

    private static void lockerTop(Painter p) {
        p.fillFbm(0x56685f, 0.07f, 5, 1311);
        p.grain(0.05f);
    }

    /**
     * A painted frontage board: a worn cream frame around a faded slate field with blocky
     * pale lettering, peeling to bare wood. The words themselves are read from the building
     * plan through the interaction prompt; the tile only has to read as "a sign" at a distance.
     */
    private static void signBoard(Painter p, int variant) {
        int n = p.size;
        p.fillFbm(0x34495a, 0.07f, 6, 1331 + variant);
        p.box(0, 0, n - 1, n - 1, 3, 0xcfc4a4);
        p.box(3, 3, n - 4, n - 4, 1, 0x1e2a34);
        // Letter rows: runs of narrow blocks with word gaps, one or two lines by layout.
        int rows = variant == 0 ? 2 : 1;
        int glyph = variant == 0 ? 6 : 9;
        for (int r = 0; r < rows; r++) {
            int top = rows == 1 ? n / 2 - glyph / 2 : n / 3 - glyph / 2 + r * (n / 3);
            int x = 8;
            for (int i = 0; x < n - 10; i++) {
                float roll = p.rf(1340 + variant * 97 + r * 31 + i);
                if (roll < 0.16f) {
                    x += glyph / 2 + 2; // a word gap
                    continue;
                }
                int w = Math.max(2, (int) (glyph * (0.45f + roll * 0.4f)));
                for (int yy = top; yy < top + glyph; yy++) {
                    for (int xx = x; xx < Math.min(n - 10, x + w); xx++) {
                        p.set(xx, yy, p.shade(0xe2d9bc, 0.9f + p.noise(xx, yy, 5, 1335) * 0.15f));
                    }
                }
                x += w + 2;
            }
        }
        // Peeling paint shows the board beneath; rust runs from the fixing bolts.
        p.fillFbmBlend(0x6e5a40, 0.7f, 8, 1336 + variant, 0.5f);
        p.blob(5, 5, 1, 0x5a4a3a);
        p.blob(n - 6, 5, 1, 0x5a4a3a);
        p.grain(0.05f);
    }

    /**
     * Contaminated ground (milestone 13): a dark oily crust with sickly yellow-green blooms and
     * glossy black seeps, so it reads as "do not stand here" against asphalt, ash and grass.
     */
    private static void contaminated(Painter p, int variant) {
        int salt = variant * 61;
        p.fillFbm(0x2f3426, 0.14f, 6, 1401 + salt);
        for (int i = 0; i < 14; i++) {
            int x = p.ri(i * 3 + 1402 + salt), y = p.ri(i * 3 + 1403 + salt);
            int r = 2 + (int) (p.rf(i + 1404 + salt) * 4);
            p.blob(x, y, r, p.shade(0x9cb23c, 0.7f + p.rf(i + 1405 + salt) * 0.35f));
        }
        for (int i = 0; i < 5; i++) {
            int x = p.ri(i * 2 + 1406 + salt), y = p.ri(i * 2 + 1407 + salt);
            p.blob(x, y, 2 + (int) (p.rf(i + 1408 + salt) * 3), 0x15170f);
        }
        p.grain(0.05f);
    }

    private static void debris(Painter p) {
        // Litter on a floor: plaster flakes, paper and glass, over open texels.
        p.clearAlpha();
        for (int i = 0; i < 38; i++) {
            int x = p.ri(i * 3 + 1321), y = p.ri(i * 3 + 1322);
            float r = p.rf(i + 1323);
            if (r < 0.45f) {
                p.pebble(x, y, 1 + (int) (r * 6), p.shade(0x9a968c, 0.85f + r * 0.3f));
            } else if (r < 0.8f) {
                int w = 3 + (int) (r * 5);
                for (int yy = y; yy < y + 3; yy++) {
                    for (int xx = x; xx < x + w; xx++) {
                        p.set(xx, yy, p.shade(0xd8d4c8, 0.85f + p.noise(xx, yy, 8, 1324) * 0.2f));
                    }
                }
            } else {
                p.dot(x, y, 0xc4d4dc);
                p.dot(x + 1, y, 0x8ea4b0);
            }
        }
    }

    /**
     * A guard rail in side view (milestone 14): a dark steel top rail and a kick rail joined
     * by square balusters, the paint chipped to rust at the joints. Everything between the
     * bars is cutout, so a rail on a parapet keeps the view over the edge.
     */
    private static void railing(Painter p) {
        int n = p.size;
        p.clearAlpha();
        int rail = Math.max(3, n / 9);
        for (int x = 0; x < n; x++) {
            for (int y = 0; y < rail; y++) {
                p.set(x, y, p.shade(0x4e5458, y == 0 ? 1.18f : 0.92f + p.noise(x, y, 8, 1351) * 0.12f));
            }
            for (int y = n - rail + 1; y < n; y++) {
                p.set(x, y, p.shade(0x464c50, 0.88f + p.noise(x, y, 8, 1352) * 0.12f));
            }
        }
        int pitch = Math.max(6, n / 4), width = Math.max(2, n / 16);
        for (int x0 = pitch / 2 - width / 2; x0 < n; x0 += pitch) {
            for (int x = x0; x < x0 + width && x < n; x++) {
                for (int y = rail; y < n - rail + 1; y++) {
                    p.set(x, y, p.shade(0x50565a, (x == x0 ? 1.1f : 0.86f) + p.noise(x, y, 6, 1353) * 0.1f));
                }
            }
        }
        // Rust where water sits: under the top rail and at each baluster foot.
        for (int i = 0; i < 14; i++) {
            int x = p.ri(i + 1354);
            p.dot(x, rail, 0x7a4a2e);
            p.dot(x, n - rail, 0x6e4228);
        }
    }

    /**
     * A growth node (milestone 15): a swollen sac of dark violet membrane, wet highlights on its
     * folds and pale yellow-green veins converging on a pore. The material is emissive, so the
     * veins glow at night while the dark membrane stays dark: a landmark, never a lamp.
     */
    private static void growthNode(Painter p, boolean top) {
        int n = p.size;
        p.fillFbm(0x3a2f45, 0.18f, 5, 1501);
        p.fillFbmBlend(0x56456a, 0.55f, 9, 1502, 0.5f);
        // Wet folds: soft bright crescents.
        for (int i = 0; i < 9; i++) {
            int x = p.ri(i * 3 + 1503), y = p.ri(i * 3 + 1504);
            p.pebble(x, y, 2 + (int) (p.rf(i + 1505) * 3), p.shade(0x6c5a80, 0.9f + p.rf(i + 1506) * 0.3f));
        }
        if (top) {
            // Seen from above, veins run to a central pore.
            int c = n / 2;
            for (int k = 0; k < 7; k++) {
                double a = k * Math.PI * 2 / 7 + p.rf(k + 1507) * 0.4;
                int r = n / 2 - 1;
                p.line(c, c, c + (int) (Math.cos(a) * r), c + (int) (Math.sin(a) * r), 0xcfe88a);
            }
            p.blob(c, c, Math.max(2, n / 9), 0x1a1420);
            p.blob(c, c, Math.max(1, n / 16), 0xe4f6a0);
        } else {
            p.veins(0xcfe88a, 0.55f, 1508, 0.9f);
            // A seam where the sac bulges over the ground.
            for (int x = 0; x < n; x++) {
                p.mulAt(x, n - 1, 0.6f);
                p.mulAt(x, n - 2, 0.75f);
            }
        }
        p.grain(0.04f);
    }

    /**
     * The veil membrane carpeting a growth territory (milestone 15): a dull violet-grey skin over
     * the lawn, ridged where it has crept over old paving, with faint yellow-green capillaries.
     * Only the capillaries are bright enough for the faint emissive term to pick out at night,
     * so the territory's edge reads in the dark without lighting anything.
     */
    private static void veilMembrane(Painter p, int variant) {
        int salt = variant * 53;
        p.fillFbm(0x353040, 0.14f, 6, 1511 + salt);
        p.fillFbmBlend(0x4a3f58, 0.6f, 8, 1512 + salt, 0.45f);
        p.cracks(0x231d2a, 0.6f, 1513 + salt, 4);
        p.veins(0xb8d27a, 0.35f, 1514 + salt, 0.65f);
        p.grain(0.05f);
    }

    /** A rail seen from above: a narrow steel frame, open in the middle. */
    /**
     * A burnt-out car (milestone 18): steel scorched to a dark brown-grey, rust blooming through
     * it, scraps of faded teal paint where the fire did not reach and soot rising up the panels.
     * The side carries a pressed body crease and a panel seam; the top a dented roof with a seam
     * across it. Every cell of a wreck shares the tiles, so nothing in them must line up.
     */
    private static void carWreck(Painter p, boolean top) {
        int n = p.size;
        p.fillFbm(0x3e3632, 0.16f, 6, 1601);
        p.fillFbmBlend(0x7a4a2e, 0.56f, 7, 1602, 0.75f);
        p.fillFbmBlend(0x4f6e6a, 0.74f, 9, 1603, 0.6f);
        if (top) {
            // Dents: soft dark hollows with a light rim on their far side.
            for (int i = 0; i < 5; i++) {
                int x = p.ri(i * 3 + 1604), y = p.ri(i * 3 + 1605);
                int r = 3 + (int) (p.rf(i + 1606) * 4);
                p.pebble(x, y, r, p.shade(0x3a322e, 0.85f + p.rf(i + 1607) * 0.2f));
            }
            for (int x = 0; x < n; x++) {
                p.set(x, n / 2, 0x241e1c);
                p.mulAt(x, n / 2 + 1, 1.25f);
            }
        } else {
            // The body crease catches light above a shadowed groove.
            int crease = n * 3 / 8;
            for (int x = 0; x < n; x++) {
                p.mulAt(x, crease, 1.35f);
                p.mulAt(x, crease + 1, 0.6f);
            }
            for (int y = 0; y < n; y++) {
                p.set(n - 3, y, 0x221c1a);
            }
            // Soot thickens towards the top, where the flames licked out.
            for (int y = 0; y < n / 3; y++) {
                for (int x = 0; x < n; x++) {
                    p.mulAt(x, y, 0.6f + 0.4f * y / (n / 3f) + p.noise(x, y, 6, 1608) * 0.15f);
                }
            }
        }
        p.cracks(0x1c1816, 0.5f, 1609, 10);
        p.grain(0.05f);
    }

    /**
     * A plank barricade (milestone 18): three salvaged boards of mismatched tone nailed across the
     * opening with dark gaps between them, a diagonal brace over them and a bright nail head at each
     * crossing, so it reads as the player's work rather than the building's.
     */
    private static void barricade(Painter p) {
        int n = p.size, board = n / 3;
        for (int y = 0; y < n; y++) {
            int b = y / board;
            float tone = 0.82f + p.rf(b * 31 + 1611) * 0.3f;
            for (int x = 0; x < n; x++) {
                float grain = p.noise(x / 3f, y * 2f, 9, 1612 + b);
                boolean gap = y % board == board - 1 || y % board == 0;
                p.set(x, y, gap ? 0x1c1612 : p.shade(0x7a5a38, tone * (0.86f + grain * 0.28f)));
            }
        }
        for (int i = 0; i < n; i++) {
            for (int w = -2; w <= 2; w++) {
                p.set(i, i + w, p.shade(0x6e4f30, 0.95f + p.noise(i, w, 4, 1613) * 0.2f));
            }
        }
        for (int b = 0; b < 3; b++) {
            int y = b * board + board / 2;
            p.dot(2, y, 0xc8c4b8);
            p.dot(n - 3, y, 0xc8c4b8);
            p.dot(y, y, 0xd8d4c8);
        }
        p.grain(0.05f);
    }

    /** A sheet-metal barricade: a dented corrugated panel bolted over the opening, its edge rusting. */
    private static void metalBarricade(Painter p) {
        int n = p.size;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                float rib = (float) Math.sin(y / (float) n * Math.PI * 12) * 0.5f + 0.5f;
                p.set(x, y, p.shade(0x6a7276, 0.76f + rib * 0.32f));
            }
        }
        p.fillFbmBlend(0x7a4a2e, 0.62f, 6, 1621, 0.7f);
        p.box(0, 0, n - 1, n - 1, 2, 0x3a3f42);
        for (int[] bolt : new int[][]{{3, 3}, {n - 4, 3}, {3, n - 4}, {n - 4, n - 4}, {n / 2, 3}, {n / 2, n - 4}}) {
            p.blob(bolt[0], bolt[1], 1, 0xa8b0b4);
        }
        p.grain(0.04f);
    }

    private static void railingTop(Painter p) {
        int n = p.size;
        p.clearAlpha();
        p.box(0, 0, n - 1, n - 1, Math.max(2, n / 12), 0x4e5458);
        p.grain(0.04f);
    }

    /**
     * The loose panel over a hidden cellar (milestone 14): the same worn coat as the plaster
     * around it, so it reads as wall from across a room, but with a hairline seam inset from
     * the edges, grey scuffing along the foot where it has been dragged, and on one layout a
     * small chalk tally, the kind of mark people leave for each other.
     */
    private static void hiddenPanel(Painter p, int variant) {
        int n = p.size;
        plaster(p, 0);
        int inset = Math.max(2, n / 14);
        for (int i = inset; i < n - inset; i++) {
            p.mulAt(i, inset, 0.86f);
            p.mulAt(i, n - 1 - inset, 0.86f);
            p.mulAt(inset, i, 0.86f);
            p.mulAt(n - 1 - inset, i, 0.86f);
        }
        for (int y = n - n / 5; y < n; y++) {
            for (int x = 0; x < n; x++) {
                if (p.noise(x, y, 5, 1361 + variant) > 0.55f) {
                    p.mulAt(x, y, 0.8f);
                }
            }
        }
        if (variant == 1) {
            int x0 = n - n / 3, y0 = n / 4, h = n / 7;
            for (int k = 0; k < 3; k++) {
                p.line(x0 + k * 3, y0, x0 + k * 3, y0 + h, 0xe6e3da);
            }
            p.line(x0 - 2, y0 + h, x0 + 9, y0, 0xe6e3da);
        }
        p.grain(0.02f);
    }
}
