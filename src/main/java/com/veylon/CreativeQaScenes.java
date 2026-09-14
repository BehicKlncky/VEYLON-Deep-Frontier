package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.gfx.FrameProfiler;
import com.veylon.world.Chunk;

import java.util.Locale;

/**
 * Creative-only native QA. These observations belong to the automated session,
 * so a smoke load must not erase them; beginSession resets them for the next run.
 * They never influence the simulation or consume an outcome RNG stream.
 */
final class CreativeQaScenes {
    /** The smoke flight starts after the isolated load and ends before the fortress route moves the player. */
    private static final double SMOKE_FLIGHT_START_SECONDS = 5.2;
    private static final double SMOKE_FLIGHT_END_SECONDS = 6.8;
    private static final double SMOKE_FLIGHT_CLIMB_SECONDS = 1.0;
    /** VEYLON_SCENE=creative_flight flies from this time until the report. */
    private static final double SCENE_FLIGHT_START_SECONDS = 1.0;
    private static final double SCENE_FLIGHT_REPORT_SECONDS = 29.0;
    /** Blocks above the generator surface the measurement flight keeps as clearance. */
    private static final float SCENE_CLEARANCE_BLOCKS = 10f;
    /** Chunks this close to the player must be meshed, or the frame counts as a visible hole. */
    private static final int HOLE_RADIUS_CHUNKS = 4;
    private static final float EAST_YAW = 90f;

    private final Game game;
    private final PlayerMovementSystem.Command flight = new PlayerMovementSystem.Command();
    private boolean creative;
    private boolean damaged;
    private boolean restored;
    private int samples;

    // Scripted flight observations shared by the smoke window and the measurement scene.
    private double lastFlightSample = -1;
    private int flightFrames;
    private int chunksCrossed;
    private int lastChunkX;
    private int lastChunkZ;
    private boolean enteredUnloaded;
    private float flightStartX;
    private boolean smokeFlightEnded;
    private boolean flightScene;
    private boolean sceneReported;
    private int holeFrames;
    private int holeRun;
    private int longestHoleRun;
    private double holeRunStart;
    private double longestHoleRunStart;

    CreativeQaScenes(Game game) { this.game = game; }

    void beginSession() {
        creative = game.player != null && game.player.abilities.invulnerable();
        damaged = false;
        restored = false;
        samples = 0;
        lastFlightSample = -1;
        flightFrames = 0;
        chunksCrossed = 0;
        enteredUnloaded = false;
        smokeFlightEnded = false;
        sceneReported = false;
        holeFrames = 0;
        holeRun = 0;
        longestHoleRun = 0;
        holeRunStart = 0;
        longestHoleRunStart = 0;
    }

    void sampleBody() {
        if (!creative) return;
        samples++;
        damaged |= game.player == null || game.player.dead
                || game.player.health != game.player.maxHealth || game.player.damageFlash != 0;
    }

    void beforeSave() {
        if (!creative) return;
        game.player.abilities.setFlying(true);
        game.player.hurt(200, false);
        game.player.hurtPhysical(game, 200, true);
        sampleBody();
    }

    void afterLoad(boolean loaded) {
        if (!creative) return;
        restored = loaded && game.gameMode() == GameMode.CREATIVE && game.creativeMarked()
                && game.player.abilities.flying();
        sampleBody();
        game.player.abilities.setFlying(false);
    }

    /** R14 smoke exercise: a short fast flight east that must cross terrain on loaded columns. */
    void updateSmokeFlight(double elapsed) {
        if (!creative || elapsed < SMOKE_FLIGHT_START_SECONDS || smokeFlightEnded) return;
        if (elapsed > SMOKE_FLIGHT_END_SECONDS) {
            game.player.abilities.setFlying(false);
            smokeFlightEnded = true;
            return;
        }
        if (flightFrames == 0) game.player.abilities.setFlying(true);
        boolean climb = elapsed < SMOKE_FLIGHT_START_SECONDS + SMOKE_FLIGHT_CLIMB_SECONDS;
        flight.set(EAST_YAW, true, false, false, false, true, false, climb, false);
        fly(flightStep(elapsed));
    }

    /** VEYLON_SCENE=creative_flight: stage a straight fast flight that measures streaming (R14). */
    void stageFlightScene() {
        if (!game.player.abilities.mayFly()) {
            System.out.println("[flight] creative_flight needs VEYLON_GAME_MODE=creative; scene inactive");
            return;
        }
        flightScene = true;
        int x = (int) Math.floor(game.player.pos.x), z = (int) Math.floor(game.player.pos.z);
        game.player.pos.y = game.world.generator.heightAt(x, z) + SCENE_CLEARANCE_BLOCKS;
        game.player.abilities.setFlying(true);
        game.camera.yaw = EAST_YAW;
        game.camera.pitch = 12f;
    }

    /** Per-frame scene hook, before streaming and rendering; inert unless the flight scene is staged. */
    void updateFlightScene(double elapsed) {
        if (!flightScene || elapsed < SCENE_FLIGHT_START_SECONDS || sceneReported) return;
        if (elapsed >= SCENE_FLIGHT_REPORT_SECONDS) {
            sceneReported = true;
            reportFlightScene(elapsed - SCENE_FLIGHT_START_SECONDS);
            return;
        }
        // Sample first: this is the terrain the previous frame actually rendered.
        sampleHoles(elapsed);
        int aheadX = (int) Math.floor(game.player.pos.x) + 24;
        int z = (int) Math.floor(game.player.pos.z);
        boolean climb = game.player.pos.y < game.world.generator.heightAt(aheadX, z) + SCENE_CLEARANCE_BLOCKS
                || game.player.horizontalCollision;
        game.camera.yaw = EAST_YAW;
        flight.set(EAST_YAW, true, false, false, false, true, false, climb, false);
        fly(flightStep(elapsed));
    }

    /** VEYLON_FRONTEND catalog captures: the first tab, a category, a live search or the inventory tab. */
    void openCatalogForQa(String variant) {
        com.veylon.ui.CreativeCatalogScreen screen = game.creativeCatalogScreen;
        screen.reset();
        switch (variant) {
            case "catalog-tools" -> screen.selectCategory(com.veylon.item.CreativeCatalog.Category.TOOLS);
            case "catalog-search" -> screen.search("iron");
            case "catalog-inventory" -> screen.showInventory();
            default -> {
            }
        }
        game.uiMode = Game.UiMode.CREATIVE_CATALOG;
    }

    void appendSmokeFailure(StringBuilder failure) {
        if (!creative) return;
        System.out.println("[smoke] creative={mode=" + game.gameMode().id
                + ",marked=" + game.creativeMarked() + ",restoredModeMarkFlight=" + restored
                + ",damaged=" + damaged + ",bodySamples=" + samples
                + ",flightFrames=" + flightFrames + ",flightChunks=" + chunksCrossed
                + ",enteredUnloaded=" + enteredUnloaded + "}");
        if (!restored) failure.append("Creative mode, mark or flight did not survive load; ");
        if (damaged || samples == 0) failure.append("Creative damage/death check failed; ");
        if (flightFrames == 0 || chunksCrossed == 0 || enteredUnloaded) {
            failure.append("Creative smoke flight did not cross loaded terrain; ");
        }
    }

    private float flightStep(double elapsed) {
        float dt = lastFlightSample < 0 ? 0f : (float) Math.min(0.1, elapsed - lastFlightSample);
        lastFlightSample = elapsed;
        return dt;
    }

    private void fly(float dt) {
        if (flightFrames == 0) {
            flightStartX = game.player.pos.x;
            lastChunkX = chunkX();
            lastChunkZ = chunkZ();
        }
        game.applyMovementCommand(flight, dt);
        flightFrames++;
        int cx = chunkX(), cz = chunkZ();
        enteredUnloaded |= game.world.getChunk(cx, cz) == null;
        if (cx != lastChunkX || cz != lastChunkZ) {
            chunksCrossed++;
            lastChunkX = cx;
            lastChunkZ = cz;
        }
    }

    private void sampleHoles(double elapsed) {
        int cx = chunkX(), cz = chunkZ();
        boolean hole = false;
        for (int dx = -HOLE_RADIUS_CHUNKS; dx <= HOLE_RADIUS_CHUNKS && !hole; dx++) {
            for (int dz = -HOLE_RADIUS_CHUNKS; dz <= HOLE_RADIUS_CHUNKS && !hole; dz++) {
                Chunk chunk = game.world.getChunk(cx + dx, cz + dz);
                hole = chunk == null || chunk.dirty;
            }
        }
        if (!hole) {
            holeRun = 0;
            return;
        }
        if (holeRun == 0) {
            holeRunStart = elapsed;
        }
        holeFrames++;
        holeRun++;
        if (holeRun > longestHoleRun) {
            longestHoleRun = holeRun;
            longestHoleRunStart = holeRunStart;
        }
    }

    private void reportFlightScene(double seconds) {
        FrameProfiler.Snapshot timing = game.frameProfiler.snapshot();
        float distance = game.player.pos.x - flightStartX;
        System.out.printf(Locale.ROOT, "[flight] seconds=%.1f frames=%d distance=%.1f blocksPerSecond=%.2f"
                        + " chunksCrossed=%d enteredUnloaded=%s holeFrames=%d longestHoleRun=%d"
                        + " longestHoleRunStartSeconds=%.2f holeRadiusChunks=%d renderRadius=%d"
                        + " loadedChunks=%d avgFps=%.1f p95Ms=%.2f p99Ms=%.2f maxMs=%.2f%n",
                seconds, flightFrames, distance, distance / seconds, chunksCrossed, enteredUnloaded,
                holeFrames, longestHoleRun, longestHoleRunStart, HOLE_RADIUS_CHUNKS,
                game.renderer.renderRadius(), game.world.loadedCount(), timing.averageFps(),
                timing.p95Ms(), timing.p99Ms(), timing.maxMs());
    }

    private int chunkX() {
        return Math.floorDiv((int) Math.floor(game.player.pos.x), Chunk.SX);
    }

    private int chunkZ() {
        return Math.floorDiv((int) Math.floor(game.player.pos.z), Chunk.SZ);
    }
}
