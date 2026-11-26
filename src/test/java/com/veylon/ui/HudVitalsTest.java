package com.veylon.ui;

import com.veylon.Game;
import com.veylon.entity.GameMode;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 0.9.1 B15: what the vitals panel shows after an overkill hit. */
class HudVitalsTest {

    @Test
    void anOverkillDeathShowsNoHealthBelowZeroWhileHealthItselfStaysTheDeathTest() {
        Game game = new Game();
        game.newWorld(20261126L, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        // The native route's kill: one 1,000-damage hit through the player's damage gate.
        game.player.hurt(1000f, false);
        assertTrue(game.player.dead, "the hit kills");
        assertTrue(game.player.health < -800f, "health itself is not clamped: " + game.player.health);

        assertEquals("0 / 100", Hud.vitalFigure(game.player.health, game.player.maxHealth),
                "the death card read -900 / 100");
        for (double t = 0; t < 2; t += 0.05) {
            assertTrue(Hud.lowHealthEdge(game.player.health, t) <= Hud.lowHealthEdge(0f, t) + 1e-6f,
                    "the low-health edge is no stronger than at zero health, at " + t);
        }
    }

    @Test
    void figuresAndThePulseAreUnchangedWhereHealthIsInRange() {
        assertEquals("55 / 100", Hud.vitalFigure(55.9f, 100f));
        assertEquals("0 / 100", Hud.vitalFigure(0f, 100f));
        assertEquals("100 / 100", Hud.vitalFigure(100f, 100f));
        assertEquals(0f, Hud.lowHealthEdge(25f, 0.3));
        assertEquals(0f, Hud.lowHealthEdge(80f, 0.3));
        // The pulse as it always was below 25: (0.5 + 0.5 sin 4t)(1 - h / 25) 0.30.
        double t = 0.4;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 4);
        assertEquals(pulse * (1f - 10f / 25f) * 0.30f, Hud.lowHealthEdge(10f, t), 1e-6f);
        assertEquals(pulse * 0.30f, Hud.lowHealthEdge(-900f, t), 1e-6f);
    }
}
