package com.veylon.engine;

import com.veylon.gfx.ParticleRenderer;
import com.veylon.world.*;
import org.junit.jupiter.api.Test;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class PhysicalRainTest {
    static World arena() {
        World w = new World(711);
        for (int cx = -2; cx <= 1; cx++) for (int cz = -2; cz <= 1; cz++) {
            Chunk c = w.getOrCreateChunk(cx, cz);
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                for (int y = 0; y < Chunk.SY; y++) c.set(x, y, z, y == 0 ? BlockType.STONE : BlockType.AIR);
            }
            c.recomputeAllHeights();
        }
        return w;
    }
    static ParticleSystem particles() { ParticleSystem p = new ParticleSystem(); p.setRandomSeed(711); return p; }
    static void drop(ParticleSystem p, float x, float y, float z, float vx, float vy, float vz) {
        p.spawn(ParticleSystem.KIND_STREAK, x,y,z,vx,vy,vz,.5f,.6f,.7f,.05f,4,0);
    }
    @Test void fallsAndAcceleratesWithoutPrematureSplash() {
        World w = arena(); ParticleSystem p = particles(); drop(p,.5f,20,.5f,0,-10,0);
        p.update(.1f,w);
        assertTrue(p.py[0] < 19); assertTrue(p.velocityY(0) < -10);
        assertEquals(1,p.count); assertEquals(0,p.impactsLastUpdate); assertFalse(p.isRainSplash(0));
        assertTrue(Float.isFinite(p.py[0]));
    }
    @Test void roofIsFirstHitAndSplashStartsAtExactContact() {
        World w = arena(); w.setBlock(0,10,0,BlockType.PLANK,false); w.setBlock(0,5,0,BlockType.STONE,false);
        ParticleSystem p = particles(); drop(p,.25f,20,.75f,0,-100,0);
        p.update(1,w);
        assertEquals(1,p.impactsLastUpdate); assertTrue(p.count >= 2 && p.count <= 4);
        for(int i=0;i<p.count;i++) {
            assertTrue(p.isRainSplash(i)); assertEquals(.25f,p.px[i],.0001f);
            assertEquals(11.006f,p.py[i],.0001f); assertEquals(.75f,p.pz[i],.0001f);
            assertTrue(p.size[i] < .02f); assertTrue(p.velocityY(i)>0);
        }
        float up=p.velocityY(0); p.update(.02f,w); assertTrue(p.velocityY(0)<up);
        p.update(.4f,w); assertEquals(0,p.count); assertEquals(0,p.impactsLastUpdate);
    }
    @Test void sweptTraversalHandlesFastDiagonalNegativeCoordinatesAndSideFaces() {
        World w=arena(); w.setBlock(-2,10,-2,BlockType.STONE,false);
        RainCollision c=new RainCollision();
        assertEquals(RainCollision.HIT,c.trace(w,-5,10.3f,-1.7f,4,10.3f,-1.7f));
        assertEquals(-2,c.x,.0001f); assertEquals(10.3f,c.y,.0001f); assertEquals(-1,c.nx);
        assertEquals(RainCollision.HIT,c.trace(w,-4,13,-4,0,9,0));
        assertEquals(11,c.y,.0001f);
    }
    @Test void thinRoofCannotTunnelAcrossLargeSegment() {
        World w=arena(); w.setBlock(0,40,0,BlockType.STONE,false);
        RainCollision c=new RainCollision();
        assertEquals(RainCollision.HIT,c.trace(w,.2f,120,.7f,.2f,2,.7f));
        assertEquals(41,c.y,.0001f); assertTrue(c.probes<100);
    }
    @Test void waterUsesVisibleTopAndFoliageStopsRain() {
        World w=arena(); RainCollision c=new RainCollision();
        w.setBlock(0,8,0,BlockType.WATER,false);
        assertEquals(RainCollision.HIT,c.trace(w,.2f,9,.7f,.2f,8,.7f));
        assertEquals(8.88f,c.y,.0001f); assertEquals(BlockType.WATER,c.material);
        w.setBlock(0,12,0,BlockType.LEAVES,false);
        assertEquals(RainCollision.HIT,c.trace(w,.2f,15,.7f,.2f,8,.7f));
        assertEquals(13,c.y,.0001f); assertEquals(BlockType.LEAVES,c.material);
    }
    @Test void unavailableColumnsRetireWithoutInventingImpactOrGeneratingChunks() {
        World w=arena(); int loaded=w.loadedCount(); ParticleSystem p=particles();
        drop(p,31.9f,20,.5f,100,-10,0); p.setRainWind(100,0); p.update(.1f,w);
        assertEquals(0,p.count); assertEquals(0,p.impactsLastUpdate); assertEquals(loaded,w.loadedCount());
        RainCollision c=new RainCollision();
        assertEquals(RainCollision.UNAVAILABLE,c.trace(w,100,100,100,100,-5,100));
        assertEquals(RainCollision.UNAVAILABLE,c.trace(w,Float.NaN,2,0,0,0,0));
    }
    @Test void collisionCacheSeesEditsNewColumnsAndNewWorlds() {
        World a=arena(),b=arena(); RainCollision c=new RainCollision();
        assertEquals(RainCollision.CLEAR,c.trace(a,.5f,12,.5f,.5f,8,.5f));
        a.setBlock(0,10,0,BlockType.STONE,false);
        assertEquals(RainCollision.HIT,c.trace(a,.5f,12,.5f,.5f,8,.5f));
        assertEquals(RainCollision.CLEAR,c.trace(b,.5f,12,.5f,.5f,8,.5f));
        assertEquals(RainCollision.UNAVAILABLE,c.trace(b,100,90,100,100,88,100));
        b.getOrCreateChunk(6,6);
        assertEquals(RainCollision.CLEAR,c.trace(b,100,90,100,100,88,100));
    }
    @Test void ordinaryEffectsDoNotSpendVoxelQueriesAndNoWorldRainRetires() {
        ParticleSystem p=particles();p.smoke(0,3,0,1);p.update(.02f,arena());
        assertEquals(0,p.collisionProbesLastUpdate);
        drop(p,0,10,0,0,-10,0);p.update(.02f);assertEquals(1,p.count);
    }
    @Test void diagonalGrazingDoesNotInventContactInUntouchedVoxel() {
        World w=arena();w.setBlock(1,10,0,BlockType.STONE,false);
        RainCollision c=new RainCollision();
        assertEquals(RainCollision.CLEAR,c.trace(w,.5f,10.5f,.5f,2.5f,10.5f,2.5f));
    }
    @Test void zeroSegmentAndGridBoundaryHaveFiniteDefinedResults() {
        World w=arena(); RainCollision c=new RainCollision();
        assertEquals(RainCollision.CLEAR,c.trace(w,0,12,0,0,12,0));
        w.setBlock(-1,10,0,BlockType.STONE,false);
        assertEquals(RainCollision.HIT,c.trace(w,0,10.5f,.5f,-2,10.5f,.5f));
        assertEquals(0,c.x); assertEquals(1,c.nx);
        assertEquals(RainCollision.UNAVAILABLE,c.trace(w,-.5f,10.5f,.5f,-.5f,9,.5f));
    }
    @Test void descendingRemovalDoesNotSkipOtherDropsOrAdvanceNewSplashes() {
        World w=arena(); ParticleSystem p=particles();
        for(int i=0;i<12;i++) drop(p,.5f,1.2f,.5f,0,-10,0);
        p.update(.1f,w); assertEquals(12,p.impactsLastUpdate);
        for(int i=0;i<p.count;i++) { assertTrue(p.isRainSplash(i)); assertEquals(1.006f,p.py[i],.0001f); }
    }
    @Test void rainAndImpactPressurePreservePoolSpaceForCombat() {
        World w=arena(); ParticleSystem p=particles();
        for(int i=0;i<10000;i++) p.rainDrop(.5f,1.2f,.5f);
        assertEquals(ParticleSystem.RAIN_LIMIT,p.count); p.update(.1f,w);
        assertTrue(p.count<=ParticleSystem.SPLASH_LIMIT);
        int before=p.count; p.explosion(2,2,2,2); assertTrue(p.count>before);
        for(int i=0;i<100;i++) p.explosion(2,2,2,2);
        assertEquals(ParticleSystem.MAX,p.count); p.update(20,w); assertEquals(0,p.count);
    }
    static ParticleSystem emit(World w, float intensity, float density) {
        ParticleSystem p=particles(); p.density=density;
        RainField f=new RainField(); Random r=new Random(7);
        for(int i=0;i<60;i++) f.update(1f/60,w,p,r,0,2,0,intensity);
        return p;
    }
    @Test void densityAndStormIntensityScaleEmission() {
        World w=arena(); int rain=emit(w,.6f,1).count, storm=emit(w,1,1).count;
        int low=emit(w,1,.25f).count;
        assertTrue(storm>rain*1.5f); assertTrue(low<storm*.4f && low>storm*.1f);
        assertEquals(0,emit(w,1,0).count); assertEquals(0,emit(w,0,1).count);
    }
    @Test void seedAndStepsReproduceWindMotionAndImpacts() {
        World w=arena(); ParticleSystem a=particles(),b=particles();
        RainField fa=new RainField(),fb=new RainField(); Random ra=new Random(8),rb=new Random(8);
        int impacts=0;
        for(int t=0;t<300;t++) {
            fa.update(.016f,w,a,ra,0,2,0,1); fb.update(.016f,w,b,rb,0,2,0,1);
            a.update(.016f,w); b.update(.016f,w); impacts+=a.impactsLastUpdate;
            assertEquals(a.count,b.count);
            for(int i=0;i<a.count;i++) {assertEquals(a.px[i],b.px[i]);assertEquals(a.py[i],b.py[i]);assertEquals(a.kind[i],b.kind[i]);}
        }
        assertTrue(impacts>100);
    }
    @Test void roofSpawningRemainsAboveRoofAndUndergroundColumnsAreRejected() {
        World w=arena();
        for(int x=-24;x<24;x++) for(int z=-24;z<24;z++) w.setBlock(x,8,z,BlockType.PLANK,false);
        ParticleSystem p=emit(w,1,1); assertTrue(p.count>500);
        for(int i=0;i<p.count;i++) assertTrue(p.py[i]>9);
        RainField f=new RainField(); p.count=0; f.update(.2f,w,p,new Random(1),0,-30,0,1);
        assertEquals(0,p.count);
    }
    @Test void teleportRecyclesOnlyPrecipitation() {
        World w=arena(); ParticleSystem p=emit(w,1,1); p.smoke(0,2,0,1);
        p.cullRain(1000,2,1000,32); assertEquals(1,p.count); assertEquals(ParticleSystem.KIND_PUFF,p.kind[0]);
    }
    @Test void allOrdinaryEmittersKeepFiniteStateAndBlendClassification() {
        ParticleSystem p=particles();
        p.smoke(0,3,0,1); p.flame(0,3,0); p.ember(0,3,0); p.ashFlake(0,3,0); p.snowflake(0,3,0);
        p.blood(0,3,0); p.beaconMote(0,3,0); p.breath(0,3,0,0,1); p.explosion(0,3,0,1);
        p.blockDust(BlockType.STONE,0,3,0,4); p.toxicMote(0,3,0); p.meteorImpact(0,3,0);
        p.muzzleFlash(0,3,0,0,0,1); p.splash(0,3,0);
        p.update(.02f); assertTrue(p.count>50);
        float[] data=new float[ParticleSystem.MAX*ParticleRenderer.INSTANCE_FLOATS];
        int alpha=ParticleRenderer.pack(p,false,1,data),add=ParticleRenderer.pack(p,true,1,data);
        assertTrue(alpha>0 && add>0); assertEquals(p.count,alpha+add);
        for(int i=0;i<p.count;i++) assertTrue(Float.isFinite(p.py[i]));
        p.update(20); assertEquals(0,p.count);
    }
    @Test void rendererPacksActualVelocityWithCorrectStride() {
        ParticleSystem p=particles(); drop(p,1,2,3,4,-15,6); drop(p,7,8,9,-2,-12,-3);
        float[] data=new float[26]; assertEquals(2,ParticleRenderer.pack(p,false,.5f,data));
        assertEquals(1,data[0]);assertEquals(7,data[13]);
        assertEquals(4,data[10]);assertEquals(-15,data[11]);assertEquals(6,data[12]);
        assertEquals(-2,data[23]);assertEquals(-12,data[24]);assertEquals(-3,data[25]);
        assertEquals(2,data[8]); assertEquals(0,ParticleRenderer.pack(p,true,1,data));
    }
}
