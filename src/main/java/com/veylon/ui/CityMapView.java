package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.util.Vec3i;
import com.veylon.world.World;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.StreetNetwork;

import java.util.List;
import java.util.function.ToDoubleFunction;

import static com.veylon.ui.HudLayout.GAP;
import static com.veylon.ui.HudLayout.PAD;
import static com.veylon.ui.HudLayout.Rect;
import static com.veylon.ui.HudStyle.*;

/**
 * Draws the city map of a layout-sixteen world (urban milestone 21) from {@link CityMap} and
 * {@link FloorSketch}: the ground plan of the streets around the survivor with the buildings they
 * entered named, their refuges, found sites and objectives, and beside it the journal and the
 * sketch of the storey underfoot. It uses the HUD's palette and type scale, opens and closes with
 * M or Escape like the wilderness map, and, like every other screen but the pause menu, leaves the
 * world running.
 */
final class CityMapView {
    private static final int UNSEEN = 0x0b1118, ROAD = 0x1c232a, PAVEMENT = 0x35404a, GREEN_GROUND = 0x2d4a33;
    private static final int WATER = 0x1d4a68, YARD = 0x4a4233, BUILDING = 0x5a6470, ENTERED = 0x3b8088;
    private static final int HOME = 0x2fd2b4, CAMP = 0x8fbf6a, NEUTRAL = 0xd9ac66, HOSTILE = 0xe06a5a;
    private static final int STRONGHOLD = 0x7fa8e0, HIDDEN = 0xb69be0, OBJECTIVE = 0xf2c24e, WHITE = 0xffffff;
    private static final float HEADER = 44, FOOTER = 62, FRAME_PAD = 14, SIDE_GAP = 18;

    /** The screen's logical geometry; pure, so the layout tests need no GL context. */
    record Layout(Rect frame, Rect map, Rect side, Rect journal, Rect here, Rect legend) {
        static Layout of(int width, int height) {
            float sideWidth = Math.max(360, Math.min(480, width * 0.32f));
            float map = Math.min(height - 2 * HudLayout.MARGIN - HEADER - FOOTER,
                    width - 2 * HudLayout.MARGIN - SIDE_GAP - sideWidth - 2 * FRAME_PAD);
            float frameWidth = map + SIDE_GAP + sideWidth + 2 * FRAME_PAD, frameHeight = HEADER + map + FOOTER;
            float x = (width - frameWidth) / 2, y = (height - frameHeight) / 2;
            Rect mapBox = new Rect(x + FRAME_PAD, y + HEADER, map, map);
            Rect side = new Rect(mapBox.right() + SIDE_GAP, y + HEADER, sideWidth, map);
            float journalHeight = Math.round(map * 0.5f);
            return new Layout(new Rect(x, y, frameWidth, frameHeight), mapBox, side,
                    new Rect(side.x(), side.y(), side.width(), journalHeight),
                    new Rect(side.x(), side.y() + journalHeight + GAP, side.width(), map - journalHeight - GAP),
                    new Rect(x + FRAME_PAD, mapBox.bottom() + 8, frameWidth - 2 * FRAME_PAD, FOOTER - 8));
        }
    }

    void draw(Game g) {
        UiRenderer ui = g.ui;
        Layout layout = Layout.of(ui.screenW(), ui.screenH());
        Rect frame = layout.frame();
        // The HUD stays behind, dimmed, so the map reads on its own.
        ui.rect(0, 0, ui.screenW(), ui.screenH(), 0, 0, 0, .55f);
        ui.panel(frame.x(), frame.y(), frame.width(), frame.height(), PANEL_CORNER);
        World w = g.world;
        int px = (int) Math.floor(g.player.pos.x), pz = (int) Math.floor(g.player.pos.z);
        String district = CityMap.districtName(w, px, pz);
        fitted(ui, frame.x() + FRAME_PAD, frame.y() + 12, layout.map().width(), TITLE,
                "CITY MAP" + (district == null ? "" : " · " + district), TEXT);
        String across = (CityMap.CELLS * CityMap.STEP) + " m across · north is up";
        text(ui, layout.map().right() - width(ui, across, MICRO), frame.y() + 18, MICRO, across, MUTED);
        drawMap(g, ui, layout.map(), px, pz);
        drawJournal(g, ui, layout.journal());
        drawHere(g, ui, layout.here());
        drawLegend(ui, layout.legend());
    }

    // ------------------------------------------------------------------
    // The ground plan and its markers
    // ------------------------------------------------------------------

    private void drawMap(Game g, UiRenderer ui, Rect box, int px, int pz) {
        World w = g.world;
        float cell = box.width() / CityMap.CELLS;
        int half = CityMap.CELLS / 2;
        for (int cx = 0; cx < CityMap.CELLS; cx++) {
            for (int cz = 0; cz < CityMap.CELLS; cz++) {
                int bx = px + (cx - half) * CityMap.STEP, bz = pz + (cz - half) * CityMap.STEP;
                int color = switch (CityMap.ground(w, bx, bz)) {
                    case UNSEEN -> UNSEEN;
                    case ROAD -> ROAD;
                    case PAVEMENT -> PAVEMENT;
                    case GREEN -> GREEN_GROUND;
                    case WATER -> WATER;
                    case YARD -> YARD;
                    case BUILDING -> entered(w, bx, bz) ? ENTERED : BUILDING;
                };
                rect(ui, box.x() + cx * cell, box.y() + cz * cell, cell + .5f, cell + .5f, color, 1);
            }
        }
        int x0 = px - half * CityMap.STEP, z0 = pz - half * CityMap.STEP;
        int x1 = x0 + CityMap.CELLS * CityMap.STEP - 1, z1 = z0 + CityMap.CELLS * CityMap.STEP - 1;
        drawDistricts(w, ui, box, x0, z0, x1, z1, cell, StreetNetwork.regionOf(px), StreetNetwork.regionOf(pz));
        for (CityMap.Building b : CityMap.enteredBuildings(w, x0, z0, x1, z1)) {
            ParcelPlan p = b.parcel();
            float left = Math.max(box.x(), sx(box, x0, p.x0(), cell)), right = Math.min(box.right(), sx(box, x0, p.x1() + 1, cell));
            float top = Math.max(box.y(), sz(box, z0, p.z0(), cell)), bottom = Math.min(box.bottom(), sz(box, z0, p.z1() + 1, cell));
            if (right - left < 18 || bottom - top < 14) { continue; }
            String name = HudLayout.fit(b.name(), right - left - 4, s -> width(ui, s, MICRO));
            float tw = width(ui, name, MICRO);
            ui.textSemibold((left + right - tw) / 2 + 1, (top + bottom) / 2 - 7, MICRO, name, 0, 0, 0, .8f);
            text(ui, (left + right - tw) / 2, (top + bottom) / 2 - 8, MICRO, name, TEXT);
        }
        for (CityMap.Marker m : CityMap.markers(g)) {
            float mx = sx(box, x0, m.position().x(), cell) + cell / 2, mz = sz(box, z0, m.position().z(), cell) + cell / 2;
            switch (m.kind()) {
                case OBJECTIVE, RUMOUR -> objective(g, ui, box, m, mx, mz, cell);
                default -> place(ui, box, m, mx, mz);
            }
        }
        // The survivor at the middle, facing the way the camera looks.
        float cx = box.x() + half * cell + cell / 2, cz = box.y() + half * cell + cell / 2;
        double yaw = Math.toRadians(g.camera.yaw);
        for (int i = 1; i <= 3; i++) {
            float dx = (float) Math.sin(yaw) * (5 + i * 4), dz = (float) -Math.cos(yaw) * (5 + i * 4);
            rect(ui, cx + dx - 1.5f, cz + dz - 1.5f, 3, 3, WHITE, 1 - i * .22f);
        }
        rect(ui, cx - 5, cz - 5, 10, 10, NAVY, 1);
        rect(ui, cx - 4, cz - 4, 8, 8, WHITE, 1);
        outline(ui, box, 1, LINE);
    }

    private static boolean entered(World w, int x, int z) {
        ParcelPlan p = w.urban.parcelAt(x, z);
        return p != null && w.urban.visits.entered(com.veylon.world.urban.UrbanIds.building(p.id(), 0));
    }

    private static float sx(Rect box, int x0, int x, float cell) { return box.x() + (x - x0) / (float) CityMap.STEP * cell; }

    private static float sz(Rect box, int z0, int z, float cell) { return box.y() + (z - z0) / (float) CityMap.STEP * cell; }

    /** Region edges in view, and the names of the other districts walked (the one underfoot is in the header). */
    private static void drawDistricts(World w, UiRenderer ui, Rect box, int x0, int z0, int x1, int z1, float cell,
                                      int hereX, int hereZ) {
        int size = StreetNetwork.REGION_SIZE;
        for (int rx = StreetNetwork.regionOf(x0); rx <= StreetNetwork.regionOf(x1); rx++) {
            for (int rz = StreetNetwork.regionOf(z0); rz <= StreetNetwork.regionOf(z1); rz++) {
                float left = Math.max(box.x(), sx(box, x0, rx * size, cell));
                float top = Math.max(box.y(), sz(box, z0, rz * size, cell));
                if (rx * size > x0) { rect(ui, left, box.y(), 1, box.height(), AMBER, .35f); }
                if (rz * size > z0) { rect(ui, box.x(), top, box.width(), 1, AMBER, .35f); }
                String name = rx == hereX && rz == hereZ ? null : CityMap.walkedDistrict(w, rx, rz);
                if (name == null) { continue; }
                float right = Math.min(box.right(), sx(box, x0, (rx + 1) * size, cell));
                if (right - left < 60) { continue; }
                String fit = HudLayout.fit(name, right - left - 12, s -> width(ui, s, MICRO));
                rect(ui, left + 4, top + 4, width(ui, fit, MICRO) + 8, 17, NAVY, .8f);
                text(ui, left + 8, top + 4, MICRO, fit, AMBER);
            }
        }
    }

    /** A known place: a filled square with its words beside it. */
    private static void place(UiRenderer ui, Rect box, CityMap.Marker m, float mx, float mz) {
        if (mx < box.x() + 4 || mx > box.right() - 4 || mz < box.y() + 4 || mz > box.bottom() - 4) { return; }
        int color = switch (m.kind()) {
            case HOME, REFUGE -> HOME;
            case STRONGHOLD -> m.hostile() ? HOSTILE : STRONGHOLD;
            case RAIDERS -> HOSTILE;
            case HIDDEN -> HIDDEN;
            default -> m.hostile() ? HOSTILE : m.friendly() ? CAMP : NEUTRAL;
        };
        rect(ui, mx - 6, mz - 6, 12, 12, NAVY, 1);
        rect(ui, mx - 5, mz - 5, 10, 10, color, 1);
        if (m.kind() == CityMap.Kind.HOME) { ui.rectOutline(mx - 8, mz - 8, 16, 16, 2, 1, 1, 1, 1); }
        label(ui, box, mx + 10, mz - 9, m.label(), color);
    }

    /** An objective: a ring on a known place; a dashed area round a rumour; clamped to the edge when away. */
    private static void objective(Game g, UiRenderer ui, Rect box, CityMap.Marker m, float mx, float mz, float cell) {
        boolean away = mx < box.x() + 10 || mx > box.right() - 10 || mz < box.y() + 10 || mz > box.bottom() - 10;
        float cx = Math.max(box.x() + 10, Math.min(box.right() - 10, mx));
        float cz = Math.max(box.y() + 10, Math.min(box.bottom() - 10, mz));
        int color = m.tracked() ? OBJECTIVE : AMBER;
        String words = m.label();
        if (away) {
            double dx = m.position().x() + .5 - g.player.pos.x, dz = m.position().z() + .5 - g.player.pos.z;
            words += " " + (m.kind() == CityMap.Kind.RUMOUR ? "~" : "") + Math.round(Math.hypot(dx, dz)) + " m "
                    + CityMap.compass(dx, dz);
        }
        if (m.kind() == CityMap.Kind.RUMOUR) {
            float r = away ? 9 : Math.max(10, Math.min(box.width() / 3, m.radius() / (float) CityMap.STEP * cell));
            dashedSquare(ui, box, cx, cz, r, color);
            text(ui, cx - width(ui, "?", SMALL) / 2, cz - 9, SMALL, "?", color);
            label(ui, box, cx + Math.min(r, 40) + 4, cz - 9, "Rumour: " + words, color);
            return;
        }
        float ring = m.tracked() ? 3 : 2;
        rect(ui, cx - 9, cz - 9, 18, 18, NAVY, .55f);
        ui.rectOutline(cx - 8, cz - 8, 16, 16, ring, r(color), HudStyle.g(color), b(color), 1);
        rect(ui, cx - 2, cz - 2, 4, 4, color, 1);
        label(ui, box, cx + 12, cz - 9, words, color);
    }

    private static void dashedSquare(UiRenderer ui, Rect box, float cx, float cz, float r, int color) {
        for (float t = -r; t < r; t += 8) {
            float len = Math.min(4, r - t);
            dash(ui, box, cx + t, cz - r, len, 2, color);
            dash(ui, box, cx + t, cz + r - 2, len, 2, color);
            dash(ui, box, cx - r, cz + t, 2, len, color);
            dash(ui, box, cx + r - 2, cz + t, 2, len, color);
        }
    }

    private static void dash(UiRenderer ui, Rect box, float x, float y, float w, float h, int color) {
        if (x >= box.x() && x + w <= box.right() && y >= box.y() && y + h <= box.bottom()) { rect(ui, x, y, w, h, color, 1); }
    }

    /** Words on a dark plate, kept inside the map. */
    private static void label(UiRenderer ui, Rect box, float x, float y, String words, int color) {
        float room = box.right() - x - 4;
        if (room < 30) {
            float w = Math.min(width(ui, words, MICRO), 260);
            x = x - w - 34;
            room = w + 10;
        }
        String fit = HudLayout.fit(words, room - 6, s -> width(ui, s, MICRO));
        if (fit.isEmpty()) { return; }
        float y0 = Math.max(box.y() + 2, Math.min(box.bottom() - 19, y));
        rect(ui, x - 2, y0, width(ui, fit, MICRO) + 6, 17, NAVY, .82f);
        text(ui, x + 1, y0, MICRO, fit, color);
    }

    // ------------------------------------------------------------------
    // The journal
    // ------------------------------------------------------------------

    private void drawJournal(Game g, UiRenderer ui, Rect box) {
        heading(ui, box, "JOURNAL");
        float x = box.x() + PAD, width = box.width() - PAD * 2, y = box.y() + 36, bottom = box.bottom() - 6;
        ToDoubleFunction<String> micro = s -> width(ui, s, MICRO);
        List<CityMap.Entry> entries = CityMap.journal(g);
        int shown = 0;
        for (CityMap.Entry e : entries) {
            List<String> detail = HudLayout.wrap(e.detail(), width, 2, micro);
            float need = 19 + detail.size() * 16 + (e.where().isEmpty() ? 0 : 16) + 6;
            if (y + need > bottom) { break; }
            int color = switch (e.state()) {
                case COMMS -> AMBER;
                case READY -> CYAN;
                case DONE -> GREEN;
                case FAILED -> RED_TEXT;
                case HINT -> MUTED;
                case OPEN -> TEXT;
            };
            fitted(ui, x, y, width, SMALL, e.heading(), color);
            y += 19;
            for (String line : detail) {
                text(ui, x + 8, y, MICRO, line, MUTED);
                y += 16;
            }
            if (!e.where().isEmpty()) {
                fitted(ui, x + 8, y, width - 8, MICRO, e.where(), CYAN);
                y += 16;
            }
            y += 6;
            shown++;
        }
        if (shown < entries.size()) {
            fitted(ui, x, bottom - 16, width, MICRO, "…and " + (entries.size() - shown) + " more", MUTED);
        }
    }

    /** A card in the HUD's own style: its panel skin, the cyan accent bar and the heading. */
    private static void heading(UiRenderer ui, Rect box, String title) {
        ui.panel(box.x(), box.y(), box.width(), box.height(), PANEL_CORNER);
        rect(ui, box.x() + PAD, box.y() + 12, 3, 15, CYAN, 1);
        text(ui, box.x() + PAD + 11, box.y() + 10, SMALL, title, CYAN);
    }

    // ------------------------------------------------------------------
    // Here: the storey underfoot
    // ------------------------------------------------------------------

    private void drawHere(Game g, UiRenderer ui, Rect box) {
        heading(ui, box, "HERE");
        float x = box.x() + PAD, width = box.width() - PAD * 2;
        FloorSketch.Sketch sketch = FloorSketch.of(g);
        fitted(ui, x, box.y() + 34, width, BODY, sketch != null ? sketch.title() : CityMap.hereLine(g), TEXT);
        String home = CityMap.homeLine(g);
        fitted(ui, x, box.y() + 58, width, MICRO, home == null ? "No refuge claimed: crouch and press F on a bed to claim one" : home, HOME);
        Rect area = new Rect(x, box.y() + 80, width, box.bottom() - box.y() - 80 - 26);
        if (sketch == null) {
            float y = area.y() + 4;
            for (String line : HudLayout.wrap("Inside a building, this sketches the storey you stand on: its stairs, its "
                    + "ways out and the rooms you walk through.", width, 3, s -> width(ui, s, MICRO))) {
                text(ui, x, y, MICRO, line, MUTED);
                y += 16;
            }
            return;
        }
        drawSketch(ui, area, sketch);
        fitted(ui, x, box.bottom() - 22, width, MICRO,
                "Amber: stairs   Cyan: way out   Grey: door   Rooms show once entered", MUTED);
    }

    private static void drawSketch(UiRenderer ui, Rect area, FloorSketch.Sketch s) {
        ParcelPlan p = s.building().parcel();
        float stripWidth = 30;
        float availableW = area.width() - stripWidth - GAP, availableH = area.height();
        int cellsX = p.x1() - p.x0() + 1, cellsZ = p.z1() - p.z0() + 1;
        float cell = Math.max(2, Math.min(availableW / cellsX, availableH / cellsZ));
        float ox = area.x() + (availableW - cellsX * cell) / 2, oz = area.y() + (availableH - cellsZ * cell) / 2;
        rect(ui, ox, oz, cellsX * cell, cellsZ * cell, NAVY, 1);
        ui.rectOutline(ox, oz, cellsX * cell, cellsZ * cell, 2, r(LINE), HudStyle.g(LINE), b(LINE), 1);
        for (FloorSketch.Room room : s.rooms()) {
            ParcelPlan.Rect r = room.interior();
            float rx = ox + (r.x0() - p.x0()) * cell, rz = oz + (r.z0() - p.z0()) * cell;
            float rw = (r.x1() - r.x0() + 1) * cell, rh = (r.z1() - r.z0() + 1) * cell;
            rect(ui, rx, rz, rw, rh, 0x24323d, 1);
            ui.rectOutline(rx, rz, rw, rh, 1, r(LINE), HudStyle.g(LINE), b(LINE), 1);
            // A name is shown whole or not at all: a room's shape still shows that it was entered.
            if (rh >= 16 && width(ui, room.name(), MICRO) <= rw - 4) {
                text(ui, rx + 2, rz + rh / 2 - 8, MICRO, room.name(), MUTED);
            }
        }
        boolean up = false, down = false;
        float fx0 = Float.MAX_VALUE, fz0 = Float.MAX_VALUE, fx1 = -Float.MAX_VALUE, fz1 = -Float.MAX_VALUE;
        for (FloorSketch.Flight f : s.flights()) {
            up |= f.up();
            down |= !f.up();
            for (Vec3i c : f.cells()) {
                if (c.x() < p.x0() || c.x() > p.x1() || c.z() < p.z0() || c.z() > p.z1()) { continue; }
                float cx = ox + (c.x() - p.x0()) * cell, cz = oz + (c.z() - p.z0()) * cell;
                rect(ui, cx, cz, cell, cell, AMBER, .55f);
                fx0 = Math.min(fx0, cx); fz0 = Math.min(fz0, cz);
                fx1 = Math.max(fx1, cx + cell); fz1 = Math.max(fz1, cz + cell);
            }
        }
        if (up || down) {
            // One label for the stair core, beside it where there is room, else below or above it.
            String words = up && down ? "stairs up / down" : up ? "stairs up" : "stairs down";
            float tw = width(ui, words, MICRO) + 6;
            float lx = fx1 + 3 + tw <= area.right() - stripWidth - GAP ? fx1 + 3 : Math.max(area.x(), fx0 - tw - 3);
            float lz = Math.max(area.y(), Math.min(area.bottom() - 17, (fz0 + fz1) / 2 - 8));
            if (lx + tw > fx0 && lx < fx1) { lz = fz1 + 2 + 17 <= area.bottom() ? fz1 + 2 : Math.max(area.y(), fz0 - 19); }
            rect(ui, lx, lz, tw, 17, NAVY, .85f);
            text(ui, lx + 3, lz, MICRO, words, AMBER);
        }
        for (FloorSketch.Door d : s.doors()) {
            int color = d.exit() ? CYAN : MUTED;
            float dx = ox + (d.position().x() - p.x0()) * cell, dz = oz + (d.position().z() - p.z0()) * cell;
            if (d.state() == FloorSketch.DoorState.CLOSED) {
                rect(ui, dx, dz, cell, cell, color, 1);
            } else {
                ui.rectOutline(dx, dz, cell, cell, Math.max(1, cell / 4),
                        r(d.state() == FloorSketch.DoorState.BROKEN ? RED_TEXT : color),
                        HudStyle.g(d.state() == FloorSketch.DoorState.BROKEN ? RED_TEXT : color),
                        b(d.state() == FloorSketch.DoorState.BROKEN ? RED_TEXT : color), 1);
            }
        }
        for (Vec3i e : s.escapes()) {
            rect(ui, ox + (e.x() - p.x0()) * cell, oz + (e.z() - p.z0()) * cell, cell, cell, CYAN, .8f);
        }
        float px = ox + (s.playerX() - p.x0()) * cell, pz = oz + (s.playerZ() - p.z0()) * cell;
        double yaw = Math.toRadians(s.yaw());
        for (int i = 1; i <= 2; i++) {
            rect(ui, px + (float) Math.sin(yaw) * i * 5 - 1.5f, pz - (float) Math.cos(yaw) * i * 5 - 1.5f, 3, 3, WHITE, .8f);
        }
        rect(ui, px - 4, pz - 4, 8, 8, NAVY, 1);
        rect(ui, px - 3, pz - 3, 6, 6, WHITE, 1);
        // The storeys, roof at the top, the one underfoot lit.
        List<Integer> floors = s.floors();
        float sx = area.right() - stripWidth, pitch = Math.min(24, area.height() / Math.max(1, floors.size()));
        for (int i = 0; i < floors.size(); i++) {
            int index = floors.get(floors.size() - 1 - i);
            float y = area.y() + i * pitch;
            boolean here = index == s.floor().index();
            String tag = index < 0 ? "B" : index == 0 ? "G" : index >= s.building().storeys() ? "R" : Integer.toString(index);
            if (here) { rect(ui, sx, y, stripWidth, pitch - 3, CYAN, 1); }
            else { ui.rectOutline(sx, y, stripWidth, pitch - 3, 1, r(LINE), HudStyle.g(LINE), b(LINE), 1); }
            text(ui, sx + stripWidth / 2 - width(ui, tag, MICRO) / 2, y + (pitch - 3) / 2 - 8, MICRO, tag, here ? NAVY : TEXT);
        }
    }

    // ------------------------------------------------------------------
    // The legend
    // ------------------------------------------------------------------

    private static final Object[][] LEGEND = {
            {ROAD, "Street"}, {PAVEMENT, "Pavement"}, {GREEN_GROUND, "Green"}, {WATER, "Water"},
            {BUILDING, "Building"}, {ENTERED, "Entered"}, {HOME, "Refuge"}, {CAMP, "Camp"},
            {STRONGHOLD, "Stronghold"}, {HOSTILE, "Hostile"}, {HIDDEN, "Hidden refuge"},
            {OBJECTIVE, "Objective"}, {AMBER, "? Rumour: a rough area"}};

    private static void drawLegend(UiRenderer ui, Rect box) {
        float x = box.x(), y = box.y();
        for (Object[] entry : LEGEND) {
            String words = (String) entry[1];
            float w = 16 + width(ui, words, MICRO) + 14;
            if (x + w > box.right()) {
                x = box.x();
                y += 18;
            }
            rect(ui, x, y + 3, 11, 11, (int) entry[0], 1);
            ui.rectOutline(x, y + 3, 11, 11, 1, 0, 0, 0, .8f);
            text(ui, x + 16, y, MICRO, words, MUTED);
            x += w;
        }
        String close = "[M or Esc] close · the world keeps moving";
        text(ui, box.right() - width(ui, close, MICRO), box.bottom() - 16, MICRO, close, MUTED);
    }
}
