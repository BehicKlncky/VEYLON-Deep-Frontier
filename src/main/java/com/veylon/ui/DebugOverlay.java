package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.UiRenderer;

/** F3 developer overlay. */
public class DebugOverlay {

    public void render(Game g) {
        UiRenderer ui = g.ui;
        var p = g.player;
        int cx = Math.floorDiv((int) Math.floor(p.pos.x), 16);
        int cz = Math.floorDiv((int) Math.floor(p.pos.z), 16);
        long heapUsed = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long heapMax = Runtime.getRuntime().maxMemory() / (1024 * 1024);

        String[] lines = {
                String.format("FPS: %d  (%.2f ms)  particles: %d", g.fps, g.frameMs, g.particles.count),
                String.format("Pos: %.2f / %.2f / %.2f", p.pos.x, p.pos.y, p.pos.z),
                String.format("Chunk: %d, %d   Loaded: %d   Rendered: %d",
                        cx, cz, g.world.loadedCount(), g.renderer.chunksRendered),
                "Seed: " + g.world.seed,
                "Biome: " + p.biome.displayName + "   Time: " + g.time.timeString()
                        + "   " + g.seasons.current(g.time).displayName,
                "Weather: " + g.weather.effective().displayName
                        + " (next " + g.weather.next.displayName
                        + String.format(" blend %.2f)", g.weather.blend),
                String.format("EnvTemp: %.1f C  Body: %.1f C  Wet: %.2f  Shelter: %s (encl %.2f)",
                        p.envTemp, p.bodyTemp, p.wetness, p.shelter.label(), p.shelter.enclosure()),
                String.format("Noise: %.2f  Scent: %.2f  Smoke: %.0f", p.noise, p.scent, p.smokeExposure),
                "Creatures: " + g.entities.creatureCount() + "   NPCs: " + g.entities.npcCount()
                        + "   Carcasses: " + g.entities.carcasses.size()
                        + "   Tracks: " + g.entities.tracks.size(),
                "Water queue: " + g.water.activeCount() + "   Fires: " + g.fire.count()
                        + "   POIs: " + g.world.pois.size(),
                "OnGround: " + p.onGround + "  InWater: " + p.inWater + "  Exposed: " + p.exposedToSky,
                String.format("Heap: %d / %d MB", heapUsed, heapMax),
        };
        float y = 74;
        for (String line : lines) {
            ui.rect(8, y - 2, ui.textWidth(line, 1.3f) + 8, 16, 0, 0, 0, 0.45f);
            ui.textShadow(12, y, 1.3f, line, 0.95f, 1f, 0.95f, 1f);
            y += 17;
        }
    }
}
