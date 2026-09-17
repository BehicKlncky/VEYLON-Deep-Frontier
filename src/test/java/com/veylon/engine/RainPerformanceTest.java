package com.veylon.engine;

import com.veylon.world.World;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.lang.management.ManagementFactory;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

@Tag("performance")
@Tag("rain-performance")
class RainPerformanceTest {
    @Test void fullRainBudgetUpdateAndPackingFitFrameAllowance() {
        World world=PhysicalRainTest.arena(); ParticleSystem p=PhysicalRainTest.particles();
        float[] buffer=new float[ParticleSystem.MAX*com.veylon.gfx.ParticleRenderer.INSTANCE_FLOATS];
        double total=0; int samples=1000;
        for(int frame=0;frame<samples+200;frame++) {
            p.count=0;
            for(int i=0;i<ParticleSystem.RAIN_LIMIT;i++) p.rainDrop((i%30)-15,70,(i/30%30)-15);
            long start=System.nanoTime();
            p.update(1f/60,world);
            com.veylon.gfx.ParticleRenderer.pack(p,false,1,buffer);
            if(frame>=200) total+=System.nanoTime()-start;
            assertEquals(ParticleSystem.RAIN_LIMIT,p.count);
        }
        double ms=total/1e6/samples;
        System.out.printf("rain full budget: %.6f ms/frame for %d drops plus packing%n",ms,ParticleSystem.RAIN_LIMIT);
        assertTrue(ms<1.0,"maximum rain admission plus packing must fit 1 ms on reference PC");
    }
    @Test void stormUpdateStaysBoundedAndAllocationLight() {
        World world=PhysicalRainTest.arena();
        ParticleSystem particles=PhysicalRainTest.particles();
        RainField field=new RainField(); Random rng=new Random(711);
        var bean=(com.sun.management.ThreadMXBean)ManagementFactory.getThreadMXBean();
        long thread=Thread.currentThread().threadId();
        int peak=0,probes=0;
        for(int i=0;i<2000;i++) {field.update(1f/60,world,particles,rng,0,2,0,1);particles.update(1f/60,world);}
        long before=bean.getThreadAllocatedBytes(thread),start=System.nanoTime();
        for(int i=0;i<10000;i++) {
            field.update(1f/60,world,particles,rng,0,2,0,1); particles.update(1f/60,world);
            peak=Math.max(peak,particles.count);probes=Math.max(probes,particles.collisionProbesLastUpdate);
        }
        double ms=(System.nanoTime()-start)/1e6/10000;
        long bytes=(bean.getThreadAllocatedBytes(thread)-before)/10000;
        System.out.printf("rain storm: %.6f ms/frame, %d bytes/frame, peak %d particles, %d voxel probes/frame%n",ms,bytes,peak,probes);
        assertTrue(ms<1.0,"rain field + collision must fit a 1 ms CPU budget on reference PC");
        assertTrue(bytes<4096,"bounded cosmetic update must avoid per-particle allocation");
        assertTrue(peak<ParticleSystem.SPLASH_LIMIT);assertTrue(probes<10000);
    }
}
