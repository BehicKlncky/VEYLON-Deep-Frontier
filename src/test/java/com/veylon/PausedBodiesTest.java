package com.veylon;

import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.FragmentPose;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.gfx.model.AnatomyModels;
import com.veylon.gfx.model.BodyPosing;
import com.veylon.gfx.model.ModelPart;
import com.veylon.gfx.model.NpcModels;
import com.veylon.settlement.NpcArchetype;
import com.veylon.world.World;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * 0.9.1 B08: the living are drawn on the clock their flames burn by, the particle clock, which
 * only simulated frames advance. They were posed on the wall clock, so behind the pause menu a
 * person went on breathing while the flames on them stood still. The renderer, the flames a burning
 * body gives off ({@code BodyFireEffects}) and the pose a body comes apart in all pose the living at
 * {@code game.particles.time}; this proves that clock holds still through the production frame step
 * while the wall clock runs, and that a death takes its pose from it.
 */
class PausedBodiesTest {

    private static final float DT = 1f / 60f;

    @Test
    void behindThePauseMenuTheLivingHoldTheirPoseAndPlayingOnTheyBreatheAgain() {
        Game g = new Game();
        g.newWorld(20_261_126L, true, World.WILDERNESS_NEW_WORLD_GENERATOR, GameMode.SURVIVAL);
        g.appState = Game.AppState.PLAYING;
        play(g, 1f);
        Npc guard = g.entities.spawnNpc(g.world, "Guard", g.player.pos.x, g.player.pos.y, g.player.pos.z - 4f);
        guard.archetype = NpcArchetype.GUARD;
        guard.interactFreeze = 1_000f; // held still, as a conversation holds a speaker: idle, swaying
        play(g, 1f);
        guard.vel.zero();

        g.input.onKey(GLFW_KEY_ESCAPE, GLFW_PRESS);
        new HotkeyRouter(g).update();
        g.input.onKey(GLFW_KEY_ESCAPE, GLFW_RELEASE);
        g.input.endFrame();
        assertEquals(Game.UiMode.PAUSE, g.uiMode, "Escape opens the pause menu");
        double clock = g.particles.time;
        List<float[]> paused = drawn(guard, clock);
        double wall = g.totalTime;
        play(g, 2f);
        assertTrue(g.totalTime > wall + 1.9, "the wall clock runs on behind the menu");
        assertEquals(clock, g.particles.time, 0.0, "the clock the living are drawn by holds still");
        assertSamePose(paused, drawn(guard, g.particles.time), "nothing breathes while paused");

        FragmentPose death = g.fragments.deathPose(guard);
        FragmentPose drawnNow = AnatomyModels.captureNpc(guard, clock);
        for (int j = 0; j < FragmentAnatomy.humanoid().jointCount(); j++) {
            assertEquals(drawnNow.rotX(j), death.rotX(j), 0f, "a body comes apart in the pose drawn, joint " + j);
            assertEquals(drawnNow.rotZ(j), death.rotZ(j), 0f);
            assertEquals(drawnNow.scale(j), death.scale(j), 0f);
        }

        g.closeScreens();
        play(g, 1f);
        assertTrue(g.particles.time > clock + 0.9, "in play the clock runs again");
        assertNotSamePose(paused, drawn(guard, g.particles.time), "and the idle body breathes");
    }

    /** Frames as {@code Game.run} plays them: the wall clock first, then the frame's step. */
    private static void play(Game g, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            g.totalTime += DT;
            g.playStep(DT, DT);
        }
    }

    /** Every part of the person as the renderer draws them at {@code clock}. */
    private static List<float[]> drawn(Npc n, double clock) {
        BodyPosing.npc(n, clock, new Matrix4f());
        List<float[]> parts = new ArrayList<>();
        collect(NpcModels.get().root, parts);
        return parts;
    }

    private static void collect(ModelPart p, List<float[]> into) {
        into.add(new float[]{p.rotX, p.rotY, p.rotZ, p.poseX, p.poseY, p.poseZ, p.scale, p.visible ? 1 : 0});
        for (ModelPart c : p.children) {
            collect(c, into);
        }
    }

    private static void assertSamePose(List<float[]> want, List<float[]> got, String message) {
        assertEquals(want.size(), got.size());
        for (int i = 0; i < want.size(); i++) {
            assertArrayEquals(want.get(i), got.get(i), 0f, message + " (part " + i + ")");
        }
    }

    private static void assertNotSamePose(List<float[]> a, List<float[]> b, String message) {
        for (int i = 0; i < a.size(); i++) {
            for (int k = 0; k < a.get(i).length; k++) {
                if (a.get(i)[k] != b.get(i)[k]) {
                    return;
                }
            }
        }
        fail(message);
    }
}
