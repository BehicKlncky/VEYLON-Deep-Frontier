package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.Affliction;
import com.veylon.entity.Player;
import com.veylon.item.ItemStack;
import com.veylon.simulation.WeatherSystem;
import com.veylon.util.Noise;

/**
 * In-game HUD: crosshair, survival bars, nutrition, affliction chips, carry
 * weight, shelter/season readouts, hotbar with durability/freshness bars,
 * damage/cold vignettes, weather overlay, prompts and the event log.
 */
public class Hud {

    /** Exact player-facing bow ammo readout used by rendering and workflow tests. */
    public static String bowAmmoLabel(Game g) {
        int basic = g.player.inventory.count(com.veylon.item.ItemType.ARROW);
        int iron = g.player.inventory.count(com.veylon.item.ItemType.IRON_ARROW);
        return "Selected " + g.selectedBowAmmo().displayName + " [R]"
                + "   Basic " + basic + "   Iron " + iron;
    }

    /** Ammo counter, reload bar and bow-draw meter above the hotbar. */
    private void renderWeaponStatus(Game g, UiRenderer ui, int w, int h,
                                    com.veylon.combat.WeaponDefinition weapon,
                                    ItemStack held) {
        if (weapon == null || held == null) {
            return;
        }
        float cx = w / 2f;
        float y = h - 96;
        switch (weapon.category) {
            case BOW -> {
                int basic = g.player.inventory.count(com.veylon.item.ItemType.ARROW);
                int iron = g.player.inventory.count(com.veylon.item.ItemType.IRON_ARROW);
                String label = bowAmmoLabel(g);
                ui.textCentered(cx, y, 1.4f, label,
                        basic + iron > 0 ? 0.9f : 1f, basic + iron > 0 ? 0.9f : 0.4f, 0.7f, 1f);
                if (g.drawingBow) {
                    float bw = 120;
                    ui.rect(cx - bw / 2, y + 18, bw, 7, 0.08f, 0.08f, 0.08f, 0.8f);
                    boolean full = g.bowDraw >= 0.999f;
                    ui.rect(cx - bw / 2 + 1, y + 19, (bw - 2) * g.bowDraw, 5,
                            full ? 0.4f : 0.85f, full ? 0.9f : 0.7f, 0.35f, 0.95f);
                }
            }
            case FIREARM -> {
                int reserve = g.player.inventory.count(weapon.ammo);
                String label = held.charge + "/" + weapon.magazine + "   " + reserve
                        + " " + weapon.ammo.displayName;
                ui.textCentered(cx, y, 1.4f, label,
                        held.charge > 0 ? 0.9f : 1f, held.charge > 0 ? 0.9f : 0.45f, 0.7f, 1f);
                if (g.reloadTimer > 0 && g.reloadTotal > 0) {
                    float bw = 120;
                    float frac = 1f - g.reloadTimer / g.reloadTotal;
                    ui.rect(cx - bw / 2, y + 18, bw, 7, 0.08f, 0.08f, 0.08f, 0.8f);
                    ui.rect(cx - bw / 2 + 1, y + 19, (bw - 2) * frac, 5, 0.85f, 0.65f, 0.3f, 0.95f);
                    ui.textCentered(cx, y + 30, 1.15f, "Reloading...", 0.85f, 0.8f, 0.7f, 1f);
                } else if (held.charge <= 0 && reserve > 0) {
                    ui.textCentered(cx, y + 18, 1.15f, "[R] Reload", 0.9f, 0.8f, 0.5f, 1f);
                }
            }
            case THROWN -> ui.textCentered(cx, y, 1.4f,
                    held.count + "x " + held.type.displayName + " — LMB to throw",
                    0.9f, 0.85f, 0.7f, 1f);
        }
    }

    public void render(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        Player p = g.player;

        renderWeatherOverlay(g, ui, w, h);
        renderVignettes(g, ui, w, h);

        // Crosshair: spreads apart while a ranged weapon is inaccurate.
        var heldStack = p.selected();
        var weapon = com.veylon.combat.WeaponRegistry.of(heldStack == null ? null : heldStack.type);
        float spread = 0f;
        if (weapon != null && weapon.category == com.veylon.combat.WeaponDefinition.Category.BOW) {
            spread = (1f - g.bowDraw) * 7f;
        } else if (weapon != null) {
            spread = weapon.spread * 0.8f;
        }
        ui.rect(w / 2f - 8 - spread, h / 2f - 1, 6, 2, 1, 1, 1, 0.8f);
        ui.rect(w / 2f + 2 + spread, h / 2f - 1, 6, 2, 1, 1, 1, 0.8f);
        ui.rect(w / 2f - 1, h / 2f - 8 - spread, 2, 6, 1, 1, 1, 0.8f);
        ui.rect(w / 2f - 1, h / 2f + 2 + spread, 2, 6, 1, 1, 1, 0.8f);

        renderWeaponStatus(g, ui, w, h, weapon, heldStack);

        // Survival bars (bottom-left).
        float bx = 16, bw = 190, bh = 13;
        float by = h - 30;
        bar(ui, bx, by, bw, bh, p.stamina / 100f, 0.25f, 0.75f, 0.30f, "STA " + (int) p.stamina);
        by -= 18;
        bar(ui, bx, by, bw, bh, p.thirst / 100f, 0.25f, 0.55f, 0.95f, "H2O " + (int) p.thirst);
        by -= 18;
        bar(ui, bx, by, bw, bh, p.hunger / 100f, 0.92f, 0.60f, 0.20f, "FOOD " + (int) p.hunger);
        by -= 18;
        bar(ui, bx, by, bw, bh, p.health / p.maxHealth, 0.85f, 0.20f, 0.20f, "HP " + (int) p.health);
        // Nutrition micro-bars.
        by -= 12;
        microBar(ui, bx, by, bw / 2 - 2, p.protein / 100f, 0.85f, 0.45f, 0.35f, "PRO");
        microBar(ui, bx + bw / 2 + 2, by, bw / 2 - 2, p.vitamins / 100f, 0.45f, 0.8f, 0.35f, "VIT");

        // Temperature / environment block right of the bars.
        float tx = bx + bw + 12;
        ui.textShadow(tx, h - 124, 1.4f, String.format("Body %.1f C", p.bodyTemp),
                tempColorR(p.bodyTemp), tempColorG(p.bodyTemp), tempColorB(p.bodyTemp), 1f);
        ui.textShadow(tx, h - 106, 1.4f, String.format("Env  %.1f C", p.envTemp), 0.9f, 0.9f, 0.9f, 1f);
        ui.textShadow(tx, h - 88, 1.4f, String.format("Wet  %d%%", (int) (p.wetness * 100)),
                0.55f, 0.7f, 0.95f, 1f);
        ui.textShadow(tx, h - 70, 1.4f, "Fatigue " + (int) p.fatigue, 0.8f, 0.8f, 0.7f, 1f);
        String shelterLabel = p.shelter.label();
        ui.textShadow(tx, h - 52, 1.4f, shelterLabel,
                p.shelter.indoor() ? 0.5f : 0.85f, 0.9f, p.shelter.indoor() ? 0.6f : 0.6f, 1f);
        float enc = p.encumbrance();
        ui.textShadow(tx, h - 34, 1.4f,
                String.format("Load %.1f/%.0f kg%s", p.carriedWeight(), p.carryCapacity(),
                        enc > 1f ? " OVER!" : ""),
                enc > 1f ? 1f : 0.85f, enc > 1f ? 0.4f : 0.85f, enc > 0.8f ? 0.3f : 0.8f, 1f);

        // Affliction chips above the bars.
        float ay = h - 152;
        for (var e : p.afflictions.entrySet()) {
            Affliction a = e.getKey();
            String label = a.displayName + " " + (int) (float) e.getValue() + "s";
            float tw = ui.textWidth(label, 1.25f);
            ui.rect(bx - 2, ay - 2, tw + 14, 16, 0.05f, 0.05f, 0.05f, 0.65f);
            ui.rect(bx + 1, ay + 1, 8, 10, a.r, a.g, a.b, 1f);
            ui.textShadow(bx + 13, ay, 1.25f, label, a.r, a.g, a.b, 1f);
            ay -= 19;
        }
        if (p.smokeExposure > 25 && !p.has(Affliction.SMOKE)) {
            ui.textShadow(bx, ay, 1.25f, "Smoke building... ventilate!", 0.7f, 0.7f, 0.7f, 1f);
            ay -= 19;
        }

        // Warnings.
        float wy = h / 2f + 40;
        boolean blink = ((int) (g.totalTime * 3) % 2) == 0;
        if (p.bodyTemp < 33 && blink) {
            ui.textCentered(w / 2f, wy, 2f, "FREEZING!", 0.5f, 0.8f, 1f, 1f);
            wy += 22;
        }
        if (p.bodyTemp > 40.5f && blink) {
            ui.textCentered(w / 2f, wy, 2f, "OVERHEATING!", 1f, 0.5f, 0.2f, 1f);
            wy += 22;
        }
        if (p.hunger <= 5 && blink) {
            ui.textCentered(w / 2f, wy, 2f, "STARVING!", 1f, 0.6f, 0.2f, 1f);
            wy += 22;
        }
        if (p.thirst <= 5 && blink) {
            ui.textCentered(w / 2f, wy, 2f, "DEHYDRATED!", 0.4f, 0.7f, 1f, 1f);
            wy += 22;
        }
        if (enc > 1f && blink) {
            ui.textCentered(w / 2f, wy, 1.6f, "OVERLOADED - drop weight to sprint",
                    1f, 0.75f, 0.4f, 1f);
        }

        renderHotbar(g, ui, w, h);

        // Top-left info.
        ui.textShadow(12, 12, 1.5f, g.time.timeString(), 1f, 1f, 0.9f, 1f);
        var season = g.seasons.current(g.time);
        ui.textShadow(12, 32, 1.4f, season.displayName + " (" + g.seasons.daysLeft(g.time)
                + "d left)", 0.95f, 0.85f, 0.6f, 1f);
        ui.textShadow(12, 50, 1.4f, "Weather: " + g.weather.effective().displayName, 0.85f, 0.9f, 1f, 1f);
        ui.textShadow(12, 68, 1.4f, "Biome: " + p.biome.displayName, 0.8f, 1f, 0.8f, 1f);

        // Top-right: events + camp standing + quest.
        String ev = "Events: " + g.events.summary();
        ui.textShadow(w - ui.textWidth(ev, 1.3f) - 12, 12, 1.3f, ev, 1f, 0.85f, 0.6f, 1f);
        String camp = "Camp: " + g.faction.standing() + " (" + (int) g.faction.trust + ")";
        ui.textShadow(w - ui.textWidth(camp, 1.3f) - 12, 30, 1.3f, camp,
                g.faction.hostile ? 1f : 0.7f, g.faction.hostile ? 0.3f : 0.9f, 0.4f, 1f);
        if (g.faction.quest != null) {
            String q = "Request: " + g.faction.quest.describe();
            ui.textShadow(w - ui.textWidth(q, 1.25f) - 12, 48, 1.25f, q, 0.7f, 0.85f, 1f, 1f);
            String navigation = QuestObjectiveView.navigationLabel(g);
            if (!navigation.isEmpty()) {
                boolean returning = g.faction.quest.status
                        == com.veylon.ai.Quest.Status.READY_TO_TURN_IN;
                ui.textShadow(w - ui.textWidth(navigation, 1.2f) - 12, 66, 1.2f,
                        navigation, returning ? 0.45f : 1f,
                        returning ? 0.9f : 0.78f, returning ? 1f : 0.25f, 1f);
            }
        }

        // Targeted block label + mining progress.
        if (g.targetHit != null) {
            String name = g.targetHit.type().displayName;
            ui.textCentered(w / 2f, h / 2f + 18, 1.3f, name, 1f, 1f, 1f, 0.9f);
            if (g.miningProgress > 0) {
                float mw = 80;
                ui.rect(w / 2f - mw / 2, h / 2f + 34, mw, 6, 0, 0, 0, 0.6f);
                ui.rect(w / 2f - mw / 2, h / 2f + 34, mw * Math.min(1, g.miningProgress), 6,
                        1f, 0.85f, 0.3f, 0.95f);
            }
        }

        // Interaction prompt.
        if (g.interactPrompt != null && !g.interactPrompt.isEmpty()) {
            ui.textCentered(w / 2f, h / 2f + 52, 1.5f, g.interactPrompt, 1f, 1f, 0.7f, 1f);
        }

        // Event log (last 6 lines, bottom-left above bars/chips).
        var lines = g.eventLog.recent(6);
        float ly = h - 248 - lines.size() * 15;
        for (String line : lines) {
            ui.textShadow(16, ly, 1.2f, line, 0.92f, 0.92f, 0.85f, 0.85f);
            ly += 15;
        }

        if (g.simPaused) {
            ui.textCentered(w / 2f, 70, 2f, "SIMULATION PAUSED (P)", 1f, 0.8f, 0.3f, 1f);
        }

        // Sleep fade overlay.
        if (g.sleepFade > 0.01f) {
            ui.rect(0, 0, w, h, 0, 0, 0, Math.min(0.92f, g.sleepFade));
            if (g.sleeping) {
                ui.textCentered(w / 2f, h / 2f - 10, 2f, "Sleeping... (Esc to wake)",
                        0.85f, 0.85f, 0.95f, 0.9f);
            }
        }
    }

    /** Damage flash, low-health pulse and cold edges. */
    private void renderVignettes(Game g, UiRenderer ui, int w, int h) {
        Player p = g.player;
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

    private void renderHotbar(Game g, UiRenderer ui, int w, int h) {
        int slots = 9;
        float slot = 46;
        float total = slots * slot;
        float x0 = w / 2f - total / 2f;
        float y0 = h - slot - 8;
        for (int i = 0; i < slots; i++) {
            float x = x0 + i * slot;
            boolean sel = i == g.player.hotbarSel;
            ui.slot(x, y0, slot, false, sel);
            ItemStack s = g.player.inventory.get(i);
            if (s != null) {
                ui.itemIcon(s.type, x + 8, y0 + 5, slot - 16);
                if (s.count > 1) {
                    ui.textShadow(x + 6, y0 + slot - 13, 1.2f, String.valueOf(s.count), 1f, 1f, 1f, 1f);
                }
                // Durability bar.
                if (s.type.hasDurability()) {
                    float frac = s.durabilityFrac();
                    ui.rect(x + 5, y0 + slot - 7, slot - 10, 3, 0.1f, 0.1f, 0.1f, 0.9f);
                    ui.rect(x + 5, y0 + slot - 7, (slot - 10) * frac, 3,
                            1f - frac, frac, 0.15f, 1f);
                }
                // Freshness bar.
                if (s.type.spoils()) {
                    float frac = s.freshnessFrac();
                    ui.rect(x + 5, y0 + slot - 7, slot - 10, 3, 0.1f, 0.1f, 0.1f, 0.9f);
                    ui.rect(x + 5, y0 + slot - 7, (slot - 10) * frac, 3,
                            0.5f - frac * 0.2f, 0.32f + frac * 0.45f, 0.2f, 1f);
                }
            }
            ui.text(x + slot - 10, y0 + 3, 1f, String.valueOf(i + 1), 0.7f, 0.7f, 0.7f, 0.8f);
        }
        ItemStack sel = g.player.selected();
        if (sel != null) {
            String label = sel.type.displayName;
            if (sel.type.hasDurability()) {
                label += "  [" + (int) sel.durability + "/" + (int) sel.type.maxDurability + "]";
            } else if (sel.type.spoils()) {
                label += "  (" + (int) (sel.freshnessFrac() * 100) + "% fresh)";
            }
            ui.textCentered(w / 2f, y0 - 16, 1.4f, label, 1f, 1f, 1f, 0.95f);
        }
    }

    private void bar(UiRenderer ui, float x, float y, float w, float h, float frac,
                     float r, float g, float b, String label) {
        frac = Math.max(0, Math.min(1, frac));
        ui.rect(x, y, w, h, 0.05f, 0.05f, 0.05f, 0.75f);
        ui.rect(x + 1, y + 1, (w - 2) * frac, h - 2, r, g, b, 0.95f);
        ui.textShadow(x + 5, y + 2, 1.1f, label, 1f, 1f, 1f, 0.95f);
    }

    private void microBar(UiRenderer ui, float x, float y, float w, float frac,
                          float r, float g, float b, String label) {
        frac = Math.max(0, Math.min(1, frac));
        ui.rect(x, y, w, 8, 0.05f, 0.05f, 0.05f, 0.7f);
        ui.rect(x + 1, y + 1, (w - 2) * frac, 6, r, g, b, 0.9f);
        ui.text(x + 3, y, 0.9f, label, 1f, 1f, 1f, 0.85f);
    }

    /** Animated rain/snow streaks as a cheap full-screen overlay. */
    private void renderWeatherOverlay(Game g, UiRenderer ui, int w, int h) {
        if (!g.weather.isPrecip() || !g.player.exposedToSky) {
            return;
        }
        boolean snow = g.weather.effective() == WeatherSystem.Weather.SNOW;
        float intensity = g.weather.intensity();
        int n = (int) (70 * intensity);
        double t = g.totalTime;
        for (int i = 0; i < n; i++) {
            long hx = Noise.mix(i * 7919L + 13);
            long hy = Noise.mix(i * 104729L + 31);
            float px = (float) ((hx & 0xffff) / 65535.0) * w;
            float speed = snow ? 60 : 420;
            float py = (float) ((((hy & 0xffff) / 65535.0) * h + t * speed) % h);
            if (snow) {
                ui.rect(px + (float) Math.sin(t * 2 + i) * 8, py, 3, 3, 1f, 1f, 1f, 0.7f);
            } else {
                ui.rect(px, py, 1.6f, 13, 0.65f, 0.75f, 0.95f, 0.45f);
            }
        }
    }

    private float tempColorR(float t) {
        return t < 35 ? 0.5f : (t > 39 ? 1f : 0.9f);
    }

    private float tempColorG(float t) {
        return t < 35 ? 0.75f : (t > 39 ? 0.45f : 0.9f);
    }

    private float tempColorB(float t) {
        return t < 35 ? 1f : (t > 39 ? 0.3f : 0.85f);
    }
}
