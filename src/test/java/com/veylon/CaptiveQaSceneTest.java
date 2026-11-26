package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@code VEYLON_SCENE=fort_captive} (0.9.1 milestone 07, B03) headlessly: it finds a generated
 * hostile settlement's caged captive, lets the ordinary activation put it in its cage, opens a
 * window of two bars toward the camera and puts the camera outside it, looking in; a Survival
 * player, whom the garrison would fight, is refused.
 */
class CaptiveQaSceneTest {

    private static final long SEED = 20_261_126L;

    @Test
    void theSceneLooksThroughAWindowAtTheCaptiveTheActivationCaged() {
        Game g = new Game();
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR, GameMode.CREATIVE);
        g.appState = Game.AppState.PLAYING;
        CaptiveQaScene scene = new CaptiveQaScene(g);
        scene.stage();

        Settlement fort = scene.settlement();
        Npc captive = scene.captive();
        assertNotNull(captive, "the activation materialized the captive");
        assertSame(NpcArchetype.CAPTIVE, captive.archetype);
        assertSame(captive, fort.residents.get(captive.residentIndex).live);
        Vec3i floor = fort.prisonPos;
        assertTrue(captive.pos.x > floor.x() && captive.pos.x < floor.x() + 2
                && captive.pos.z > floor.z() && captive.pos.z < floor.z() + 2, "inside its cage");

        int open = 0;
        for (int x = floor.x() - 1; x <= floor.x() + 2; x++) {
            for (int z = floor.z() - 1; z <= floor.z() + 2; z++) {
                boolean edge = x == floor.x() - 1 || x == floor.x() + 2 || z == floor.z() - 1 || z == floor.z() + 2;
                if (edge && g.world.getBlock(x, floor.y(), z) == BlockType.AIR) {
                    open++;
                }
            }
        }
        assertEquals(2, open, "a window of two bars at the captive's feet, the rest of the cage intact");
        assertEquals(BlockType.CAGE_BARS, g.world.getBlock(floor.x(), floor.y() + 2, floor.z()), "under its roof");

        float dx = captive.pos.x - g.player.pos.x, dz = captive.pos.z - g.player.pos.z;
        assertTrue(Math.hypot(dx, dz) > 2.5, "the camera stands outside the cage");
        var front = g.camera.front();
        assertTrue((front.x * dx + front.z * dz) / Math.hypot(dx, dz) > 0.9, "looking at the captive");
        assertFalse(g.player.isPerceivableByAi(), "nobody in the garrison sees a Creative visitor");
    }

    @Test
    void theCaptiveStandsClearOfItsRoofAndStrugglesWhenSetAlightSoTheScenePasses() {
        Game g = new Game();
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR, GameMode.CREATIVE);
        g.appState = Game.AppState.PLAYING;
        CaptiveQaScene scene = new CaptiveQaScene(g);
        scene.stage();
        float dt = 1f / 30f;
        for (float t = 0; t <= 6.1f; t += dt) {
            g.playStep(dt, dt);
            scene.update(t);
        }
        Npc captive = scene.captive();
        assertTrue(captive.panic.active(), "the fire panics it");
        assertTrue(scene.passed(), "clear of every block and moving (0.9.1 B03): feet " + captive.pos.y
                + ", the cage floor " + scene.settlement().prisonPos.y());
    }

    @Test
    void aSurvivalPlayerIsRefusedBeforeAnythingIsStaged() {
        Game g = new Game();
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR, GameMode.SURVIVAL);
        CaptiveQaScene scene = new CaptiveQaScene(g);
        IllegalStateException refusal = assertThrows(IllegalStateException.class, scene::stage);
        assertTrue(refusal.getMessage().contains("VEYLON_GAME_MODE=creative"), refusal.getMessage());
        assertNull(scene.captive());
    }
}
