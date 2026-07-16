package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.world.BlockType;
import com.veylon.world.Poi;
import com.veylon.world.World;

/**
 * Top-down map of explored terrain around the player. Shows the NPC camp,
 * discovered points of interest and the distress beacon.
 */
public class MapScreen {

    private static final int CELLS = 64;
    private static final int STEP = 2;

    public void update(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        float size = Math.min(w, h) * 0.72f;
        float cell = size / CELLS;
        float x0 = w / 2f - size / 2f, y0 = h / 2f - size / 2f;

        ui.panel(x0 - 14, y0 - 44, size + 28, size + 96);
        ui.textCentered(w / 2f, y0 - 34, 1.8f, "MAP  (" + (CELLS * STEP) + "m across)", 1f, 1f, 1f, 1f);

        World world = g.world;
        int px = (int) g.player.pos.x;
        int pz = (int) g.player.pos.z;
        int half = CELLS / 2;

        for (int cx = 0; cx < CELLS; cx++) {
            for (int cz = 0; cz < CELLS; cz++) {
                int bx = px + (cx - half) * STEP;
                int bz = pz + (cz - half) * STEP;
                float r = 0.05f, gg = 0.05f, b = 0.07f;
                var chunk = world.getChunk(Math.floorDiv(bx, 16), Math.floorDiv(bz, 16));
                if (chunk != null && chunk.generated) {
                    int y = chunk.height(Math.floorMod(bx, 16), Math.floorMod(bz, 16));
                    BlockType t = world.getBlock(bx, y, bz);
                    if (world.getBlock(bx, y + 1, bz) == BlockType.WATER
                            || world.getBlock(bx, World.SEA_LEVEL, bz) == BlockType.WATER) {
                        t = BlockType.WATER;
                    }
                    r = t.r;
                    gg = t.g;
                    b = t.b;
                    // Height shading.
                    float shade = 0.6f + (y - 28) / 60f;
                    shade = Math.max(0.4f, Math.min(1.15f, shade));
                    r *= shade;
                    gg *= shade;
                    b *= shade;
                }
                ui.rect(x0 + cx * cell, y0 + cz * cell, cell + 0.5f, cell + 0.5f, r, gg, b, 1f);
            }
        }

        // Discovered POI markers.
        int discovered = 0;
        for (Poi poi : world.pois) {
            if (!poi.discovered) {
                continue;
            }
            discovered++;
            int dx = (poi.pos.x() - px) / STEP + half;
            int dz = (poi.pos.z() - pz) / STEP + half;
            if (dx >= 1 && dx < CELLS - 1 && dz >= 1 && dz < CELLS - 1) {
                var t = poi.type;
                ui.rect(x0 + dx * cell - 3, y0 + dz * cell - 3, 7, 7, t.r, t.g, t.b, 1f);
                ui.rectOutline(x0 + dx * cell - 4, y0 + dz * cell - 4, 9, 9, 1, 0, 0, 0, 0.9f);
            }
        }

        // Beacon marker.
        if (world.beaconPos != null) {
            int dx = (world.beaconPos.x() - px) / STEP + half;
            int dz = (world.beaconPos.z() - pz) / STEP + half;
            if (dx >= 0 && dx < CELLS && dz >= 0 && dz < CELLS) {
                boolean lit = world.beaconStage >= 3;
                ui.rect(x0 + dx * cell - 3, y0 + dz * cell - 3, 7, 7,
                        lit ? 0.4f : 0.3f, lit ? 0.95f : 0.6f, 1f, 1f);
            }
        }

        // Camp marker.
        if (world.campPos != null) {
            int dx = (world.campPos.x() - px) / STEP + half;
            int dz = (world.campPos.z() - pz) / STEP + half;
            if (dx >= 0 && dx < CELLS && dz >= 0 && dz < CELLS) {
                ui.rect(x0 + dx * cell - 3, y0 + dz * cell - 3, 7, 7, 1f, 0.6f, 0.1f, 1f);
            }
        }
        // Player marker (center).
        ui.rect(x0 + half * cell - 3, y0 + half * cell - 3, 7, 7, 1f, 1f, 1f, 1f);
        ui.rectOutline(x0 + half * cell - 4, y0 + half * cell - 4, 9, 9, 1, 0f, 0f, 0f, 1f);

        ui.textCentered(w / 2f, y0 + size + 8, 1.2f,
                "White = you   Orange = camp   Cyan = beacon   POIs discovered: " + discovered
                        + " / " + world.pois.size() + " known area", 0.75f, 0.75f, 0.75f, 1f);
        // POI legend.
        float lx = x0;
        float lyy = y0 + size + 26;
        for (Poi.PoiType t : Poi.PoiType.values()) {
            ui.rect(lx, lyy + 2, 8, 8, t.r, t.g, t.b, 1f);
            ui.textShadow(lx + 12, lyy, 1.1f, t.displayName, 0.8f, 0.8f, 0.8f, 1f);
            lx += ui.textWidth(t.displayName, 1.1f) + 34;
        }
        ui.textCentered(w / 2f, y0 + size + 44, 1.15f, "[M or Esc] close", 0.7f, 0.7f, 0.7f, 1f);
    }
}
