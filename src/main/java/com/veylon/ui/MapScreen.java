package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
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

        ui.panel(x0 - 14, y0 - 44, size + 28, size + 132);
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

        // Settlement markers. Rumors deliberately disclose neither exact tier,
        // owner nor alignment until the player performs normal discovery.
        for (var s : world.settlements.values()) {
            if (!s.discovered && !s.rumored) {
                continue;
            }
            int dx = (s.center.x() - px) / STEP + half;
            int dz = (s.center.z() - pz) / STEP + half;
            if (dx < 1 || dx >= CELLS - 1 || dz < 1 || dz >= CELLS - 1) {
                continue;
            }
            float r, gg, b;
            if (!s.discovered) {
                r = 0.48f;
                gg = 0.40f;
                b = 0.60f;
            } else if (s.occupied) {
                r = 0.35f;
                gg = 0.85f;
                b = 0.95f;
            } else if (s.cleared) {
                r = 0.55f;
                gg = 0.55f;
                b = 0.55f;
            } else {
                switch (s.alignment) {
                    case FRIENDLY -> {
                        r = 0.35f;
                        gg = 0.85f;
                        b = 0.40f;
                    }
                    case HOSTILE -> {
                        r = 0.90f;
                        gg = 0.25f;
                        b = 0.20f;
                    }
                    default -> {
                        r = 0.85f;
                        gg = 0.80f;
                        b = 0.40f;
                    }
                }
            }
            int mark = s.discovered ? 5 + s.type.ordinal() * 2 : 7;
            ui.rect(x0 + dx * cell - mark / 2f, y0 + dz * cell - mark / 2f, mark, mark, r, gg, b, 1f);
            ui.rectOutline(x0 + dx * cell - mark / 2f - 1, y0 + dz * cell - mark / 2f - 1,
                    mark + 2, mark + 2, 1, 0, 0, 0, 0.9f);
            String tag = markerTag(s);
            ui.textShadow(x0 + dx * cell + mark / 2f + 2, y0 + dz * cell - 6, 1.1f,
                    tag, r, gg, b, 1f);
        }
        // Exact quest target (or turn-in provider once the task is complete).
        // The generic gold/cyan overlay discloses coordinates, not hidden tier,
        // faction, alignment or services. Off-map objectives clamp to the edge.
        QuestObjectiveView.Objective objective = QuestObjectiveView.resolve(g);
        if (objective != null) {
            float targetX = (objective.position().x() - px) / (float) STEP + half;
            float targetZ = (objective.position().z() - pz) / (float) STEP + half;
            boolean offMap = targetX < 2 || targetX > CELLS - 3
                    || targetZ < 2 || targetZ > CELLS - 3;
            targetX = Math.max(2, Math.min(CELLS - 3, targetX));
            targetZ = Math.max(2, Math.min(CELLS - 3, targetZ));
            float mx = x0 + targetX * cell;
            float mz = y0 + targetZ * cell;
            boolean returning = objective.phase()
                    == QuestObjectiveView.Phase.RETURN_TO_PROVIDER;
            float r = returning ? 0.35f : 1f;
            float gg = returning ? 0.90f : 0.78f;
            float b = returning ? 1f : 0.20f;
            ui.rectOutline(mx - 7, mz - 7, 15, 15, 2, r, gg, b, 1f);
            ui.rect(mx - 2, mz - 2, 5, 5, r, gg, b, 1f);
            ui.textShadow(mx + 9, mz - 7, 1.15f,
                    returning ? "RETURN" : offMap ? "! EDGE" : "!", r, gg, b, 1f);
        }

        // Player marker (center).
        ui.rect(x0 + half * cell - 3, y0 + half * cell - 3, 7, 7, 1f, 1f, 1f, 1f);
        ui.rectOutline(x0 + half * cell - 4, y0 + half * cell - 4, 9, 9, 1, 0f, 0f, 0f, 1f);

        ui.textCentered(w / 2f, y0 + size + 8, 1.2f,
                "White = you   Orange = camp   Cyan = beacon   ? = settlement rumor   Gold ! = objective   "
                        + "Green/yellow/red = discovered settlement (C/V/F/K/X; $/+/Z services)   POIs: " + discovered
                        + "/" + world.pois.size(), 0.75f, 0.75f, 0.75f, 1f);
        // POI legend (wraps into rows now that cave POIs exist).
        float lx = x0;
        float lyy = y0 + size + 26;
        for (Poi.PoiType t : Poi.PoiType.values()) {
            float entryWidth = ui.textWidth(t.displayName, 1.1f) + 34;
            if (lx + entryWidth > x0 + size) {
                lx = x0;
                lyy += 15;
            }
            ui.rect(lx, lyy + 2, 8, 8, t.r, t.g, t.b, 1f);
            ui.textShadow(lx + 12, lyy, 1.1f, t.displayName, 0.8f, 0.8f, 0.8f, 1f);
            lx += entryWidth;
        }
        ui.textCentered(w / 2f, lyy + 20, 1.15f, "[M or Esc] close", 0.7f, 0.7f, 0.7f, 1f);
    }

    static String markerTag(Settlement s) {
        if (!s.discovered) {
            return "?";
        }
        String tier = switch (s.type) {
            case CAMP -> "C";
            case VILLAGE -> "V";
            case FORT -> "F";
            case CASTLE -> "K";
            case FORTRESS -> "X";
        };
        String services = serviceTag(s);
        return services.isEmpty() ? tier : tier + " " + services;
    }

    static String serviceTag(Settlement s) {
        if (!s.discovered || (!s.friendly() && !s.occupied)) {
            return "";
        }
        boolean trader = false, medic = false;
        for (Settlement.Resident resident : s.residents) {
            if (!resident.alive || resident.rescued || resident.routed || resident.surrendered) {
                continue;
            }
            trader |= resident.archetype == NpcArchetype.TRADER;
            medic |= resident.archetype == NpcArchetype.MEDIC && s.medStock > 0;
        }
        return (trader ? "$" : "") + (medic ? "+" : "")
                + ((!s.beds.isEmpty() || s.occupied) ? "Z" : "");
    }
}
