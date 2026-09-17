package com.veylon;

import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;

/** Opt-in 60-second physical_rain capture sequence; never runs in ordinary worlds. */
final class RainQaScene {
    private final Game game;
    private boolean active;
    private boolean impactDetail;
    private int phase = -1;
    private int peakParticles, peakProbes, peakSubmissions;
    RainQaScene(Game game) { this.game = game; }

    void stage() {
        active = true;
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.particles.count = 0;
        game.particles.setRandomSeed(711);
        game.ambience.reseed(711);
        for (int cx = -2; cx <= 2; cx++) for (int cz = -2; cz <= 2; cz++) {
            Chunk c = game.world.getOrCreateChunk(cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                for (int y = 35; y < Chunk.SY; y++) c.set(x,y,z,y <= 40 ? BlockType.STONE : BlockType.AIR);
            }
            c.recomputeAllHeights(); c.rebuildLights(game.world); c.dirty = true;
        }
        game.world.beginBatch();
        // Raised roof and its rear wall leave an entrance facing the scene.
        for (int x = -4; x <= 4; x++) for (int z = -2; z <= 5; z++) block(x,45,z,BlockType.PLANK);
        for (int x = -4; x <= 4; x++) for (int y = 41; y <= 44; y++) block(x,y,5,BlockType.WALL);
        for (int x = -4; x <= 4; x += 8) for (int y = 41; y <= 44; y++) block(x,y,-2,BlockType.LOG);
        for (int x = 5; x <= 11; x++) for (int z = -16; z <= -8; z++) block(x,41,z,BlockType.WATER);
        for (int x = -11; x <= -6; x++) for (int z = -15; z <= -7; z++)
            for (int y = 41; y <= 41 + Math.floorMod(x+z,3); y++) block(x,y,z,BlockType.DIRT);
        for (int y = 41; y < 47; y++) block(-7,y,-18,BlockType.LOG);
        for (int x = -9; x <= -5; x++) for (int z = -20; z <= -16; z++) block(x,47,z,BlockType.LEAVES);
        game.world.endBatch();
        game.time.totalMinutes = 12*60;
        game.camera.pitch = 10;
        game.camera.yaw = 0;
        game.player.pos.set(0.5f,41, -6);
    }

    void stageImpact() {
        stage();
        impactDetail = true;
        for (int x = 8; x <= 12; x++) for (int z = -3; z <= 2; z++) block(x,40,z,BlockType.BASALT);
    }

    private void updateImpact(double elapsed) {
        game.player.pos.set(10.5f,41,.8f);
        game.player.vel.zero();
        game.camera.yaw = 0; game.camera.pitch = 52;
        game.weather.current = game.weather.next = Weather.CLEAR;
        game.weather.blend = 1; game.weather.changeTimer = 10000;
        game.player.envTemp = 20;
        var p = game.particles;
        p.count = 0; p.density = 1; p.setRandomSeed(711); p.setRainWind(0,0);
        p.spawn(com.veylon.engine.ParticleSystem.KIND_STREAK,10.5f,42.5f,-.5f,
                0,-16,0,.52f,.61f,.72f,.05f,3.5f,0);
        int state = Math.min(3,(int)(elapsed / 2));
        p.update(state == 0 ? .05f : .1f,game.world);
        if (state >= 2) p.update(state == 2 ? .07f : .3f,game.world);
    }

    private void block(int x,int y,int z,BlockType type) { game.world.setBlock(x,y,z,type,false); }

    void update(double elapsed) {
        if (!active) return;
        if (impactDetail) { updateImpact(elapsed); return; }
        int next = Math.min(11,(int)(elapsed/5));
        if (next != phase) {
            phase = next;
            System.out.println("[rain-qa] phase=" + phase + " peakParticles=" + peakParticles
                    + " peakProbes=" + peakProbes + " peakSubmissions=" + peakSubmissions);
        }
        peakParticles = Math.max(peakParticles,game.particles.count);
        peakProbes = Math.max(peakProbes,game.particles.collisionProbesLastUpdate);
        peakSubmissions = Math.max(peakSubmissions,game.renderer.particleDrawCalls);
        game.player.pos.set(0.5f,41, -6);
        game.camera.yaw=0; game.camera.pitch=10;
        if (phase == 2) game.player.pos.set(.5f,41,1);
        if (phase == 3) { game.player.pos.set(7,47,9); game.camera.yaw=30; game.camera.pitch=30; }
        if (phase == 4) game.camera.pitch=42;
        if (phase == 5) game.player.pos.x = (float)Math.sin(elapsed*1.8)*8;
        if (phase == 6) game.camera.yaw=(float)(elapsed-30)*240;
        game.player.vel.zero();
        game.particles.density = phase == 7 ? .25f : 1;
        Weather weather = phase == 0 ? Weather.RAIN : phase == 11 ? Weather.SNOW : Weather.STORM;
        game.weather.current=game.weather.next=weather; game.weather.blend=1;
        if (phase == 9 || phase == 10) {
            game.weather.current = phase == 9 ? Weather.CLEAR : Weather.RAIN;
            game.weather.next = phase == 9 ? Weather.RAIN : Weather.CLEAR;
            game.weather.blend = (float)(elapsed%5)/5;
        }
        game.weather.changeTimer=10000;
    }
}
