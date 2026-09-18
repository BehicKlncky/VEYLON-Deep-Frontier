package com.veylon.ui;

import com.veylon.Game;
import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.Affliction;
import com.veylon.entity.Player;
import com.veylon.gfx.IconAtlas;
import com.veylon.item.ItemStack;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Noise;

import java.util.Locale;

import static com.veylon.ui.HudLayout.*;
import static com.veylon.ui.HudStyle.*;

/** Read-only gameplay HUD. All widgets append to the existing ordered UI batch. */
public class Hud {
    /** Exact player-facing bow ammo readout used by rendering and workflow tests. */
    public static String bowAmmoLabel(Game g) {
        if (g.player.abilities.unlimitedItems()) {
            return "Selected " + g.selectedBowAmmo().displayName + " [R]   unlimited arrows";
        }
        int basic = g.player.inventory.count(com.veylon.item.ItemType.ARROW);
        int iron = g.player.inventory.count(com.veylon.item.ItemType.IRON_ARROW);
        return "Selected " + g.selectedBowAmmo().displayName + " [R]"
                + "   Basic " + basic + "   Iron " + iron;
    }

    /** R23: the magazine stays visible while an unlimited reserve reads as ASCII text. */
    public static String firearmAmmoLabel(Game g, ItemStack held,
                                          com.veylon.combat.WeaponDefinition weapon) {
        String reserve = g.player.abilities.unlimitedItems() ? "unlimited"
                : Integer.toString(g.player.inventory.count(weapon.ammo));
        return held.charge + "/" + weapon.magazine + "   " + reserve + " " + weapon.ammo.displayName;
    }

    /** R23: an empty magazine offers a reload whenever one can start. */
    public static boolean showReloadHint(Game g, ItemStack held,
                                         com.veylon.combat.WeaponDefinition weapon) {
        return held.charge <= 0 && (g.player.abilities.unlimitedItems()
                || g.player.inventory.count(weapon.ammo) > 0);
    }

    /** R23: thrown-weapon readout; an unlimited stack shows no count. */
    public static String thrownAmmoLabel(Game g, ItemStack held) {
        return (g.player.abilities.unlimitedItems() ? "unlimited " : held.count + "x ")
                + held.type.displayName + " — LMB to throw";
    }

    public void render(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        Player p = g.player;
        boolean creative = p.abilities.invulnerable();
        boolean smokeBuilding = p.smokeExposure > 25 && !p.has(Affliction.SMOKE);
        HudLayout layout = new HudLayout(w, h, creative,
                p.afflictions.size() + (smokeBuilding ? 1 : 0));
        renderWeatherOverlay(g, ui, w, h);
        renderVignettes(g, ui, w, h);

        ItemStack held = p.selected();
        WeaponDefinition weapon = WeaponRegistry.of(held == null ? null : held.type);
        renderCrosshair(g, ui, w, h, weapon);
        if (creative) renderCreativeStatus(g, ui, layout.status);
        else renderSurvivalStatus(g, ui, layout.status, smokeBuilding);
        renderHotbar(g, ui, layout);
        renderWeaponStatus(g, ui, layout.weapon, weapon, held);
        renderContext(g, ui, layout.context);
        renderMission(g, ui, layout.mission);
        renderFocus(g, ui, layout);
        renderLog(g, ui, layout);

        if (g.simPaused) {
            // Bounded to the gap between the two corner cards.
            Rect pause = new Rect(layout.context.right() + GAP, MARGIN,
                    layout.mission.x() - layout.context.right() - GAP * 2, 30);
            ui.panel(pause.x(), pause.y(), pause.width(), pause.height(), PANEL_CORNER);
            centered(ui, pause, pause.y() + 6, SMALL, "SIMULATION PAUSED [P]", AMBER);
        }
        if (g.sleepFade > 0.01f) {
            ui.rect(0, 0, w, h, 0, 0, 0, Math.min(0.92f, g.sleepFade));
            if (g.sleeping) {
                ui.textCentered(w / 2f, h / 2f - 10, 2f, "Sleeping... (Esc to wake)",
                        0.85f, 0.85f, 0.95f, 0.9f);
            }
        }
    }

    private void renderCrosshair(Game g, UiRenderer ui, int w, int h, WeaponDefinition weapon) {
        float spread = 0;
        if (weapon != null && weapon.category == WeaponDefinition.Category.BOW) {
            spread = (1f - g.bowDraw) * 7f;
        } else if (weapon != null) spread = weapon.spread * 0.8f;
        // Preserve the original aim/spread geometry; a thin shadow survives bright terrain.
        float cx = w / 2f, cy = h / 2f;
        crosshairArm(ui, cx - 8 - spread, cy - 1, 6, 2);
        crosshairArm(ui, cx + 2 + spread, cy - 1, 6, 2);
        crosshairArm(ui, cx - 1, cy - 8 - spread, 2, 6);
        crosshairArm(ui, cx - 1, cy + 2 + spread, 2, 6);
    }

    private void crosshairArm(UiRenderer ui, float x, float y, float w, float h) {
        rect(ui, x - 1, y - 1, w + 2, h + 2, NAVY, 0.65f);
        rect(ui, x, y, w, h, TEXT, 0.95f);
    }

    private void panel(UiRenderer ui, Rect box, String heading, int accent) {
        ui.panel(box.x(), box.y(), box.width(), box.height(), PANEL_CORNER);
        rect(ui, box.x() + PAD, box.y() + 12, 3, 15, accent, 1);
        fitted(ui, box.x() + PAD + 11, box.y() + 10, box.width() - PAD * 2 - 11,
                SMALL, heading, accent);
    }

    private void renderSurvivalStatus(Game g, UiRenderer ui, Rect box, boolean smokeBuilding) {
        Player p = g.player;
        panel(ui, box, "SURVIVAL / VITALS", CYAN);
        float x = box.x() + PAD, y = box.y() + VITAL_TOP;
        vital(ui, x, y, "Health", IconAtlas.HEALTH, p.health, p.maxHealth, RED);
        vital(ui, x, y + VITAL_PITCH, "Hunger", IconAtlas.HUNGER, p.hunger, 100, AMBER);
        vital(ui, x, y + VITAL_PITCH * 2, "Hydration", IconAtlas.HYDRATION, p.thirst, 100, TEAL);
        vital(ui, x, y + VITAL_PITCH * 3, "Stamina", IconAtlas.STAMINA, p.stamina, 100, GREEN);
        float half = (BAR_WIDTH - GAP) / 2;
        nutrition(ui, x, box.y() + NUTRITION_TOP, half, "Protein", p.protein);
        nutrition(ui, x + half + GAP, box.y() + NUTRITION_TOP, half, "Vitamins", p.vitamins);
        float ty = box.y() + TELEMETRY_TOP;
        rect(ui, x, ty - GAP, BAR_WIDTH, 1, LINE, 0.6f);
        String body = p.bodyTemp < 33 ? "FREEZING" : p.bodyTemp > 40.5f ? "OVERHEATING" : "Body";
        telemetry(ui, x, ty, half, body + " " + format("%.1f C", p.bodyTemp),
                p.bodyTemp < 33 || p.bodyTemp > 40.5f ? AMBER : TEXT);
        telemetry(ui, x + half + GAP, ty, half, "Air " + format("%.1f C", p.envTemp), TEXT);
        telemetry(ui, x, ty + TELEMETRY_PITCH, half, "Wetness " + (int) (p.wetness * 100) + "%", MUTED);
        telemetry(ui, x + half + GAP, ty + TELEMETRY_PITCH, half, "Fatigue " + (int) p.fatigue + "%", MUTED);
        telemetry(ui, x, ty + TELEMETRY_PITCH * 2, half, p.shelter.label(), AMBER);
        telemetry(ui, x + half + GAP, ty + TELEMETRY_PITCH * 2, half,
                (p.encumbrance() > 1 ? "OVER " : "Load ")
                        + format("%.1f/%.0f kg", p.carriedWeight(), p.carryCapacity()),
                p.encumbrance() > 1 ? RED_TEXT : TEXT);
        int index = 0;
        for (var entry : p.afflictions.entrySet()) {
            chip(ui, box, index++, entry.getKey().displayName,
                    (int) (float) entry.getValue() + "s", entry.getKey() == Affliction.BLEEDING ? RED_TEXT : AMBER);
        }
        if (smokeBuilding) chip(ui, box, index++, "Smoke building", "Ventilate shelter", AMBER);
        if (index == 0) {
            text(ui, x, box.y() + CHIP_TOP + 8, SMALL, "No active afflictions", MUTED);
        }
    }

    private void vital(UiRenderer ui, float x, float y, String label, String icon,
                       float value, float maximum, int color) {
        float fraction = maximum > 0 ? clamp(value / maximum) : 0;
        String state = vitalState(fraction);
        ui.sprite(icon, x, y, VITAL_ICON, VITAL_ICON);
        text(ui, x + VITAL_LABEL_X, y - 2, TITLE, label, TEXT);
        if (!state.isEmpty()) {
            text(ui, x + 126, y, MICRO, "! " + state, fraction <= 0.1f ? RED_TEXT : AMBER);
        }
        String number = (int) value + " / " + (int) maximum;
        text(ui, x + BAR_WIDTH - width(ui, number, SMALL), y, SMALL, number, TEXT);
        meter(ui, x, y + VITAL_METER_Y, BAR_WIDTH, BAR_HEIGHT, fraction,
                fraction <= 0.1f ? RED : color);
        if (!state.isEmpty()) {
            outline(ui, new Rect(x, y + VITAL_METER_Y, BAR_WIDTH, BAR_HEIGHT), fraction <= 0.1f ? 2 : 1, AMBER);
        }
    }

    private void meter(UiRenderer ui, float x, float y, float width, float height,
                       float fraction, int color) {
        rect(ui, x, y, width, height, NAVY, 1);
        rect(ui, x + 2, y + 2, (width - 4) * clamp(fraction), height - 4, color, 0.95f);
        outline(ui, new Rect(x, y, width, height), 1, LINE);
        // Quarter marks remain visible when the bar is empty or desaturated.
        for (int tick = 1; tick < 4; tick++) {
            rect(ui, x + width * tick / 4, y + height - 5, 1, 4, TEXT, 0.45f);
        }
    }

    private void nutrition(UiRenderer ui, float x, float y, float width, String label, float value) {
        text(ui, x, y, MICRO, label + " " + (int) value, MUTED);
        rect(ui, x, y + 18, width, 5, NAVY, 1);
        rect(ui, x, y + 18, width * clamp(value / 100), 5, LINE, 1);
    }

    private void telemetry(UiRenderer ui, float x, float y, float width, String label, int color) {
        fitted(ui, x, y, width, SMALL, label, color);
    }

    private void chip(UiRenderer ui, Rect box, int index, String label, String detail, int color) {
        float width = (BAR_WIDTH - GAP) / 2;
        float x = box.x() + PAD + index % 2 * (width + GAP);
        float y = box.y() + CHIP_TOP + index / 2 * CHIP_PITCH;
        ui.nineSlice(IconAtlas.SLOT, x, y, width, CHIP_HEIGHT, 4);
        ui.sprite(IconAtlas.AFFLICTION, x + 5, y + 8, 18, 18, r(color), g(color), b(color), 1);
        fitted(ui, x + 26, y + 2, width - 30, MICRO, label, TEXT);
        fitted(ui, x + 26, y + 17, width - 30, MICRO, detail, color);
    }

    private void renderCreativeStatus(Game g, UiRenderer ui, Rect box) {
        Player p = g.player;
        panel(ui, box, "FRONTIER / CREATIVE", CYAN);
        float x = box.x() + PAD;
        text(ui, x, box.y() + 41, 2.6f, "CREATIVE", TEXT);
        text(ui, x, box.y() + 78, BODY, "Explore. Build. Shape the frontier.", MUTED);
        String[][] rows = {{"MOVEMENT", p.abilities.flying() ? "FLYING" : "GROUNDED"},
                {"ENVIRONMENT", format("%.1f C", p.envTemp)}, {"SHELTER", p.shelter.label()},
                {"TERRAIN", p.biome.displayName}};
        for (int i = 0; i < rows.length; i++) {
            float y = box.y() + 116 + i * 46;
            rect(ui, x, y, BAR_WIDTH, 1, LINE, 0.65f);
            text(ui, x, y + 11, MICRO, rows[i][0], MUTED);
            float available = BAR_WIDTH - 112;
            String value = fit(rows[i][1], available, s -> width(ui, s, TITLE));
            text(ui, x + BAR_WIDTH - width(ui, value, TITLE), y + 7, TITLE, value,
                    i == 0 ? CYAN : i == 2 ? AMBER : TEXT);
        }
        rect(ui, x, box.y() + CHIP_TOP, BAR_WIDTH, CHIP_HEIGHT, NAVY, 0.8f);
        text(ui, x + 10, box.y() + CHIP_TOP + 8, SMALL, "Unlimited supplies / Free building", CYAN);
    }

    private void renderContext(Game g, UiRenderer ui, Rect box) {
        panel(ui, box, "FIELD CONDITIONS", CYAN);
        float x = box.x() + PAD, width = box.width() - PAD * 2;
        fitted(ui, x, box.y() + 32, width, TITLE, g.time.timeString(), TEXT);
        fitted(ui, x, box.y() + 56, width, SMALL,
                g.seasons.current(g.time).displayName + " / " + g.seasons.daysLeft(g.time) + " days left", AMBER);
        fitted(ui, x, box.y() + 77, width, SMALL, "Weather / " + g.weather.effective().displayName, TEXT);
        fitted(ui, x, box.y() + 97, width, SMALL, "Biome / " + g.player.biome.displayName, MUTED);
    }

    private void renderMission(Game g, UiRenderer ui, Rect box) {
        panel(ui, box, "FRONTIER / OPERATIONS", CYAN);
        float x = box.x() + PAD, width = box.width() - PAD * 2;
        fitted(ui, x, box.y() + 33, width, SMALL, "Events / " + g.events.summary(), AMBER);
        fitted(ui, x, box.y() + 55, width, SMALL,
                "Camp / " + g.faction.standing() + " (" + (int) g.faction.trust + ")",
                g.faction.hostile ? RED_TEXT : TEXT);
        rect(ui, x, box.y() + 79, width, 1, LINE, 0.65f);
        fitted(ui, x, box.y() + 89, width, BODY,
                g.faction.quest == null ? "No active request" : g.faction.quest.describe(), TEXT);
        String navigation = QuestObjectiveView.navigationLabel(g);
        fitted(ui, x, box.y() + 120, width, SMALL,
                navigation.isEmpty() ? "[M] Map & navigation" : navigation, CYAN);
    }

    private void renderHotbar(Game g, UiRenderer ui, HudLayout layout) {
        for (int i = 0; i < SLOT_COUNT; i++) {
            boolean selected = i == g.player.hotbarSel;
            Rect box = layout.slot(i, selected);
            float x = box.x(), y = box.y(), size = box.width();
            ui.slot(x, y, size, false, selected);
            if (selected) {
                outline(ui, box, 3, CYAN);
                rect(ui, x + 4, y + size - 4, size - 8, 3, CYAN, 1);
            }
            ItemStack stack = g.player.inventory.get(i);
            if (stack != null) {
                ui.itemIcon(stack.type, x + 7, y + 5, ITEM_ICON);
                if (stack.count > 1) {
                    String count = Integer.toString(stack.count);
                    float countWidth = width(ui, count, SMALL);
                    rect(ui, x + size - countWidth - 8, y + size - 25, countWidth + 4, 17, NAVY, 0.95f);
                    text(ui, x + size - countWidth - 6, y + size - 25, SMALL, count, TEXT);
                }
                if (stack.type.hasDurability()) {
                    condition(ui, box, stack.durabilityFrac(), false);
                } else if (stack.type.spoils()) {
                    condition(ui, box, stack.freshnessFrac(), true);
                }
            }
            rect(ui, x + 4, y + 4, 13, 16, NAVY, 0.9f);
            text(ui, x + 6, y + 3, SMALL, Integer.toString(i + 1), selected ? CYAN : TEXT);
        }
        ItemStack held = g.player.selected();
        if (held != null) {
            String label = held.type.displayName;
            if (held.type.hasDurability()) label += " / " + (int) held.durability + "/"
                    + (int) held.type.maxDurability + " condition";
            else if (held.type.spoils()) label += " / " + (int) (held.freshnessFrac() * 100) + "% fresh";
            Rect box = layout.heldItem;
            ui.nineSlice(IconAtlas.SLOT, box.x(), box.y(), box.width(), box.height(), 4);
            centered(ui, box, box.y() + 4, BODY, label, TEXT);
        }
    }

    /** Durability is a continuous teal rail; freshness uses separated amber segments. */
    private void condition(UiRenderer ui, Rect box, float fraction, boolean freshness) {
        float x = box.x() + 6, y = box.bottom() - 9, width = box.width() - 12;
        rect(ui, x, y, width, CONDITION_HEIGHT, NAVY, 1);
        rect(ui, x, y, width * clamp(fraction), CONDITION_HEIGHT, freshness ? AMBER : CYAN, 1);
        if (freshness) for (int i = 1; i < 5; i++) {
            rect(ui, x + width * i / 5, y, 2, CONDITION_HEIGHT, NAVY, 1);
        }
    }

    private void renderWeaponStatus(Game g, UiRenderer ui, Rect box, WeaponDefinition weapon, ItemStack held) {
        if (weapon == null || held == null) return;
        ui.panel(box.x(), box.y(), box.width(), box.height(), PANEL_CORNER);
        String label = switch (weapon.category) {
            case BOW -> bowAmmoLabel(g);
            case FIREARM -> firearmAmmoLabel(g, held, weapon);
            case THROWN -> thrownAmmoLabel(g, held);
        };
        centered(ui, box, box.y() + 8, BODY, label, TEXT);
        String action;
        float progress = -1;
        if (weapon.category == WeaponDefinition.Category.BOW) {
            action = g.drawingBow ? (g.bowDraw >= 0.999f ? "READY" : "DRAW " + (int) (g.bowDraw * 100) + "%")
                    : "Hold LMB to draw";
            if (!g.player.abilities.unlimitedItems()
                    && g.player.inventory.count(com.veylon.item.ItemType.ARROW)
                    + g.player.inventory.count(com.veylon.item.ItemType.IRON_ARROW) == 0) {
                action = "NO ARROWS";
            }
            if (g.drawingBow) progress = g.bowDraw;
        } else if (weapon.category == WeaponDefinition.Category.FIREARM) {
            if (g.reloadTimer > 0 && g.reloadTotal > 0) {
                progress = 1 - g.reloadTimer / g.reloadTotal;
                action = "RELOADING " + (int) (clamp(progress) * 100) + "%";
            } else action = showReloadHint(g, held, weapon) ? "[R] Reload"
                    : held.charge <= 0 ? "NO AMMUNITION" : "LMB to fire / [R] Reload";
        } else action = "THROWABLE / Ready";
        if (progress >= 0) {
            text(ui, box.x() + PAD, box.y() + 36, SMALL, action, AMBER);
            meter(ui, box.x() + WEAPON_METER_X, box.y() + 36,
                    box.width() - WEAPON_METER_X - PAD, 16, progress, AMBER);
        } else centered(ui, box, box.y() + 36, SMALL, action, AMBER);
    }

    private void renderFocus(Game g, UiRenderer ui, HudLayout layout) {
        if (g.targetHit != null) {
            Rect box = layout.target;
            ui.nineSlice(IconAtlas.SLOT, box.x(), box.y(), box.width(), box.height(), 4);
            centered(ui, box, box.y() + 4, BODY, g.targetHit.type().displayName, TEXT);
            if (g.miningProgress > 0) {
                meter(ui, box.x() + PAD, box.y() + 26, box.width() - PAD * 2, 9, g.miningProgress, AMBER);
            }
        }
        if (g.interactPrompt == null || g.interactPrompt.isBlank()) return;
        Rect box = layout.prompt;
        ui.panel(box.x(), box.y(), box.width(), box.height(), PANEL_CORNER);
        String prompt = g.interactPrompt;
        if (prompt.startsWith("[") && prompt.indexOf(']') > 0 && prompt.indexOf(']') <= 6) {
            int end = prompt.indexOf(']');
            String key = prompt.substring(1, end);
            Rect cap = new Rect(box.x() + 8, box.y() + 6, 48, 24);
            outline(ui, cap, 1, AMBER);
            centered(ui, new Rect(cap.x() - PAD, cap.y(), cap.width() + PAD * 2, cap.height()),
                    cap.y() + 2, BODY, key, AMBER);
            fitted(ui, box.x() + 66, box.y() + 8, box.width() - 66 - PAD,
                    BODY, prompt.substring(end + 1).stripLeading(), TEXT);
        } else centered(ui, box, box.y() + 8, BODY, prompt, AMBER);
    }

    private void renderLog(Game g, UiRenderer ui, HudLayout layout) {
        var lines = g.eventLog.recent(layout.logLines);
        if (lines.isEmpty()) return;
        float height = lines.size() * LOG_LINE + PAD;
        Rect box = new Rect(layout.eventLog.x(), layout.status.y() - GAP - height,
                layout.eventLog.width(), height);
        ui.nineSlice(IconAtlas.SLOT, box.x(), box.y(), box.width(), box.height(), 4);
        float y = box.y() + PAD / 2;
        for (String line : lines) {
            fitted(ui, box.x() + PAD, y, box.width() - PAD * 2, SMALL, line, MUTED);
            y += LOG_LINE;
        }
    }

    private static float clamp(float value) { return Math.max(0, Math.min(1, value)); }
    private static String format(String pattern, Object... values) { return String.format(Locale.ROOT, pattern, values); }

    /** Damage flash, low-health pulse and cold edges. */
    private void renderVignettes(Game g, UiRenderer ui, int w, int h) {
        Player p = g.player;
        if (p.abilities.invulnerable()) return;
        float red = p.damageFlash * 0.45f;
        if (p.health < 25) {
            red = Math.max(red, (0.5f + 0.5f * (float) Math.sin(g.totalTime * 4))
                    * (1f - p.health / 25f) * 0.30f);
        }
        if (red > 0.01f) {
            edge(ui, w, h, 0.75f, 0.05f, 0.05f, red);
        }
        if (p.bodyTemp < 34.5f) {
            float cold = Math.min(0.4f, (34.5f - p.bodyTemp) / 5f * 0.4f);
            edge(ui, w, h, 0.45f, 0.65f, 0.95f, cold);
        }
        if (p.has(Affliction.SMOKE)) {
            edge(ui, w, h, 0.35f, 0.35f, 0.35f, 0.3f);
        }
    }

    private void edge(UiRenderer ui, int w, int h, float r, float g, float b, float a) {
        float t = Math.min(w, h) * 0.10f;
        ui.rect(0, 0, w, t, r, g, b, a);
        ui.rect(0, h - t, w, t, r, g, b, a);
        ui.rect(0, t, t, h - 2 * t, r, g, b, a);
        ui.rect(w - t, t, t, h - 2 * t, r, g, b, a);
    }

    /** Preserve the legacy snow overlay; rain is rendered exclusively in the depth-tested world pass. */
    private void renderWeatherOverlay(Game g, UiRenderer ui, int w, int h) {
        if (g.weather.effective() != WeatherSystem.Weather.SNOW || !g.player.exposedToSky) {
            return;
        }
        float intensity = g.weather.intensity();
        int n = (int) (70 * intensity);
        double t = g.totalTime;
        for (int i = 0; i < n; i++) {
            long hx = Noise.mix(i * 7919L + 13);
            long hy = Noise.mix(i * 104729L + 31);
            float px = (float) ((hx & 0xffff) / 65535.0) * w;
            float speed = 60;
            float py = (float) ((((hy & 0xffff) / 65535.0) * h + t * speed) % h);
            ui.rect(px + (float) Math.sin(t * 2 + i) * 8, py, 3, 3, 1f, 1f, 1f, 0.7f);
        }
    }

}
