package com.veylon;

import com.veylon.engine.ParticleSystem;
import com.veylon.simulation.WeatherSystem.Weather;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AmbienceRainTest {
    private static Game scene() {
        Game g=new Game(); g.newWorld(711,true);
        new RainQaScene(g).stage();
        g.player.envTemp=20;
        g.weather.current=g.weather.next=Weather.STORM; g.weather.blend=1;
        return g;
    }
    private static int rains(Game g) {
        int count=0;
        for(int i=0;i<g.particles.count;i++) if(g.particles.kind[i]==ParticleSystem.KIND_STREAK) count++;
        return count;
    }
    @Test void shelterExposureDoesNotDisableOutdoorEmissionOrChangeGameplayState() {
        Game g=scene(); g.player.pos.set(.5f,41,1); g.player.exposedToSky=false;
        int edits=g.world.changedBlocks.size();
        g.ambience.updateEmitters(.1f); int sheltered=rains(g);
        assertTrue(sheltered>40); assertFalse(g.player.exposedToSky);
        assertEquals(edits,g.world.changedBlocks.size());
        g.particles.count=0;g.ambience.reseed(711);g.particles.setRandomSeed(711);
        g.player.exposedToSky=true;g.ambience.updateEmitters(.1f);
        assertEquals(sheltered,rains(g));
    }
    @Test void rainStartsBeforeWeatherSnapAndStopsSmoothlyAfterIt() {
        Game g=scene();g.weather.current=Weather.CLEAR;g.weather.next=Weather.RAIN;g.weather.blend=.25f;
        assertFalse(g.weather.isPrecip());g.ambience.updateEmitters(.1f);assertTrue(rains(g)>0);
        g.particles.count=0;g.weather.current=Weather.RAIN;g.weather.next=Weather.CLEAR;g.weather.blend=.75f;
        assertFalse(g.weather.isPrecip());g.ambience.updateEmitters(.1f);assertTrue(rains(g)>0);
        g.particles.count=0;g.weather.blend=1;g.ambience.updateEmitters(.1f);assertEquals(0,rains(g));
    }
    @Test void snowRemainsOnItsExistingEmitterPath() {
        Game g=scene();g.player.exposedToSky=true;g.weather.current=g.weather.next=Weather.SNOW;
        g.ambience.updateEmitters(.12f);assertEquals(0,rains(g));assertTrue(g.particles.count>0);
        for(int i=0;i<g.particles.count;i++) assertFalse(g.particles.isRainSplash(i));
    }
}
