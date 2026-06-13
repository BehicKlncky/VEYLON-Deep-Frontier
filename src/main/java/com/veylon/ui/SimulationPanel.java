package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;

/** Tab panel: a live view into every simulation system. */
public class SimulationPanel {

    public void render(Game g) {
        UiRenderer ui = g.ui;
        int w = ui.screenW();
        var p = g.player;
        int ccx = Math.floorDiv((int) Math.floor(p.pos.x), 16);
        int ccz = Math.floorDiv((int) Math.floor(p.pos.z), 16);
        int discovered = 0;
        for (var poi : g.world.pois) {
            if (poi.discovered) {
                discovered++;
            }
        }

        String[] lines = {
                "=== SIMULATION ===",
                String.format("FPS %d   frame %.2f ms   particles %d", g.fps, g.frameMs, g.particles.count),
                String.format("Player  %.1f / %.1f / %.1f", p.pos.x, p.pos.y, p.pos.z),
                "Chunk   " + ccx + ", " + ccz + "   loaded " + g.world.loadedCount(),
                "Biome   " + p.biome.displayName + "   " + g.seasons.current(g.time).displayName,
                "Time    " + g.time.timeString(),
                "Weather " + g.weather.effective().displayName
                        + "  intensity " + String.format("%.2f", g.weather.intensity()),
                String.format("Env %.1f C  body %.1f C  wet %d%%  %s",
                        p.envTemp, p.bodyTemp, (int) (p.wetness * 100), p.shelter.label()),
                String.format("Nutrition  protein %d  vitamins %d", (int) p.protein, (int) p.vitamins),
                String.format("Load %.1f/%.0f kg   noise %.2f   scent %.2f",
                        p.carriedWeight(), p.carryCapacity(), p.noise, p.scent),
                "Afflictions " + (p.afflictions.isEmpty() ? "none" : p.afflictions.keySet().toString()),
                "",
                "--- Entities ---",
                "Creatures " + g.entities.creatureCount() + "   NPCs " + g.entities.npcCount()
                        + "   carcasses " + g.entities.carcasses.size()
                        + "   tracks " + g.entities.tracks.size(),
                "",
                "--- Water / Items ---",
                "Active cells " + g.water.activeCount() + "   processed " + g.water.cellsProcessed,
                "Racks " + g.world.rackBatches.size() + "   collectors " + g.world.collectorWater.size()
                        + "   spoiled " + g.itemConditions.itemsSpoiled,
                "",
                "--- Plants ---",
                String.format("Soil moisture (avg) %.2f   growth events %d",
                        g.plants.lastAvgMoisture, g.plants.growthEvents),
                "",
                "--- Fire ---",
                "Burning " + g.fire.count() + "   ignitions " + g.fire.totalIgnitions
                        + "   lightning " + g.weather.lightningStrikes,
                "",
                "--- NPC Camp ---",
                "Standing " + g.faction.standing() + "   trust " + (int) g.faction.trust
                        + "   stage " + g.faction.upgradeStage,
                "Food " + g.faction.foodStock + "   wood " + g.faction.woodStock
                        + "   alert " + (int) g.faction.alert,
                "Quest " + (g.faction.quest == null ? "none" : g.faction.quest.describe()),
                "",
                "--- Exploration ---",
                "POIs discovered " + discovered + " / " + g.world.pois.size() + " generated",
                "Blueprints " + g.player.blueprints.size() + " / 3   beacon stage "
                        + g.world.beaconStage,
                "",
                "--- Events (" + g.events.totalEventsTriggered + " total) ---",
                g.events.summary(),
        };

        float pw = 400;
        float x0 = w - pw - 10;
        float y = 60;
        ui.rect(x0 - 10, y - 10, pw + 10, lines.length * 17 + 20, 0.04f, 0.04f, 0.07f, 0.85f);
        for (String line : lines) {
            ui.textShadow(x0, y, 1.3f, line, 0.8f, 1f, 0.9f, 1f);
            y += 17;
        }
    }
}
