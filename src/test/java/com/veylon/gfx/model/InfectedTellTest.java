package com.veylon.gfx.model;

import com.veylon.Game;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Urban milestone 22: the veil through an infected body glows faintly in the dark and brightens
 * through a strike's windup, the same windup its strike sound marks; nobody else glows.
 */
class InfectedTellTest {
    @Test
    void theVeilGlowsAtRestAndBrightensThroughAStrikesWindup() {
        Game game = new Game();
        game.world = new World(7L, World.GEN_URBAN_APARTMENTS);
        game.player = new Player(game.world);
        for (NpcArchetype archetype : new NpcArchetype[]{NpcArchetype.VEILBOUND, NpcArchetype.BREACHER}) {
            Npc n = game.entities.spawnNpc(game.world, archetype.name(), 0, 41, 0);
            n.archetype = archetype;
            EntityModel model = NpcModels.get();
            Animator.poseNpc(model, n, 0);
            float rest = model.part("veilRibs").emissive;
            assertEquals(Animator.VEIL_GLOW, rest, 1e-6f, archetype + " glows at rest");
            assertTrue(rest > 0f && rest <= 0.1f, "faintly: by day the entity shader multiplies emissive by 2.6");
            float windup = archetype == NpcArchetype.BREACHER ? com.veylon.ai.BreacherAI.WINDUP : com.veylon.ai.InfectedAI.WINDUP;
            n.strikeWindup = windup * 0.5f;
            Animator.poseNpc(model, n, 0);
            float half = model.part("veilRibs").emissive;
            n.strikeWindup = 0f;
            Animator.poseNpc(model, n, 0);
            float full = model.part("veilRibs").emissive;
            assertTrue(rest < half && half < full, archetype + " brightens through the windup: " + rest + " " + half + " " + full);
            assertEquals(Animator.VEIL_GLOW + Animator.VEIL_STRIKE_GLOW, full, 1e-6f);
            assertEquals(archetype == NpcArchetype.BREACHER, model.part("breacherSeal").visible);
        }
        Npc guard = game.entities.spawnNpc(game.world, "Guard", 2, 41, 0);
        guard.archetype = NpcArchetype.GUARD;
        EntityModel model = NpcModels.get();
        Animator.poseNpc(model, guard, 0);
        assertFalse(model.part("veilRibs").visible || model.part("veilShoulder").visible, "a person has no veil");
    }
}
