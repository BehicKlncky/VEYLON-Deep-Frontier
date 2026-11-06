package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UrbanSliceQaSceneTest {
    @Test
    void stagingChangesNothingAndTheRouteCannotPassWithoutTheFrameStep() {
        Game g = new Game();
        g.newWorld(7, true, World.GEN_URBAN_ENCOUNTERS, GameMode.SURVIVAL);
        g.appState = Game.AppState.PLAYING;
        Vector3f spawn = new Vector3f(g.player.pos);
        int berries = g.player.inventory.count(ItemType.DRIED_BERRY);
        UrbanQaScenes scenes = new UrbanQaScenes(g);
        scenes.stage("urban_slice");
        assertEquals(spawn, g.player.pos);
        assertEquals(berries, g.player.inventory.count(ItemType.DRIED_BERRY));
        assertTrue(g.world.changedBlocks.isEmpty());
        assertTrue(g.world.crateContents.isEmpty());
        // Input alone moves nothing: only the normal frame step can advance the route.
        assertThrows(IllegalStateException.class, () -> {
            for (double t = 0; t < UrbanSliceQaScene.DEADLINE_SECONDS + 10; t += .1) { scenes.update(t); }
        });
        assertFalse(scenes.slice().passed());
        assertEquals(spawn, g.player.pos);
        assertTrue(g.world.changedBlocks.isEmpty());
    }

    @Test
    void theSliceNeedsTheEncounterGenerator() {
        Game g = new Game();
        g.newWorld(7, true, World.GEN_URBAN_STARTER, GameMode.SURVIVAL);
        assertThrows(IllegalStateException.class, () -> new UrbanQaScenes(g).stage("urban_slice"));
    }
}
