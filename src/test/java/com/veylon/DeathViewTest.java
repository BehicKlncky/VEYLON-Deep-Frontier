package com.veylon;

import com.veylon.entity.BodyFragment;
import com.veylon.entity.GameMode;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.9.1 B13: the death card after a lethal blast. The player's remains are placed where the living
 * body stood, so the camera, kept at the eye, sat inside the remains' head piece; and the world is
 * paused behind the card, so the remains hung where they were thrown from until the player woke.
 * The card now watches the remains from a clear point behind and above the eye while they fall
 * and come to rest; the world itself stays paused.
 */
class DeathViewTest {

    private static final float DT = 1f / 60f;
    private static final float FEET = 40f;
    private static final float SCRAP_POWER = 2.6f, SCRAP_DAMAGE = 14f;

    @Test
    void aLethalBlastsCardWatchesTheRemainsFallFromClearOfThem() {
        Game g = arena();
        play(g, 0.5f);
        assertTrue(g.player.onGround, "precondition: standing");
        g.camera.yaw = 30f;
        g.camera.pitch = 10f;
        play(g, 0.1f);
        Vector3f eye = new Vector3f(g.player.pos).add(0, g.player.eyeHeight(), 0);

        // The player's own scrap bomb at their feet: inside its lethal radius, a Survival player dies.
        g.explosions.explode(g, g.player.pos.x + 0.3f, FEET + 0.3f, g.player.pos.z, SCRAP_POWER, SCRAP_DAMAGE,
                0f, true, true);
        g.playStep(DT, DT);
        assertEquals(Game.AppState.DEATH, g.appState, "the death card");
        double minutes = g.time.totalMinutes;
        List<BodyFragment> remains = new ArrayList<>(g.fragments.live);
        assertEquals(10, remains.size(), "the player's remains");
        List<Vector3f> thrownFrom = remains.stream().map(f -> new Vector3f(f.pos)).toList();

        g.playStep(DT, DT);
        Vector3f camera = new Vector3f(g.camera.position);
        assertTrue(camera.y > eye.y + 0.5f, "above where the eye was: " + camera.y + " over " + eye.y);
        assertTrue(Math.hypot(camera.x - eye.x, camera.z - eye.z) > 1.5, "and back from it");
        for (BodyFragment f : remains) {
            assertFalse(inside(camera, f), "the camera is not inside the " + f.definition);
        }
        Vector3f look = g.camera.front();
        Vector3f toBody = new Vector3f(eye).sub(0, PlayerLifeCycle.VIEW_AIM_DROP, 0).sub(camera).normalize();
        assertTrue(look.dot(toBody) > 0.99f, "looking at the body as it died");

        play(g, 2.5f);
        assertEquals(Game.AppState.DEATH, g.appState, "the card is still up");
        for (int i = 0; i < remains.size(); i++) {
            assertTrue(remains.get(i).pos.distance(thrownFrom.get(i)) > 0.05f,
                    remains.get(i).definition + " is falling or fell behind the card");
        }
        assertTrue(g.fragments.liveCount() <= 2, "most have come to rest in the card's three seconds: "
                + g.fragments.liveCount() + " still moving");
        play(g, 6f); // a card held longer (a test's play steps never end it) sees every piece down
        assertEquals(0, g.fragments.liveCount(), "the remains have come to rest behind it");
        for (int i = 0; i < remains.size(); i++) {
            BodyFragment f = remains.get(i);
            assertTrue(f.pos.distance(thrownFrom.get(i)) > 0.05f, f.definition + " fell from where it was thrown");
            assertEquals(FEET, f.pos.y - f.halfHeight, 0.05f, f.definition + " lies on the ground");
        }
        assertEquals(minutes, g.time.totalMinutes, 0.0, "the world itself stays paused behind the card");
        assertEquals(camera, g.camera.position, "and the view stays where it was chosen");

        g.respawn();
        g.appState = Game.AppState.PLAYING;
        g.playStep(DT, DT);
        assertEquals(30f, g.camera.yaw, 1e-4f, "the survivor wakes looking the way they looked");
        assertEquals(10f, g.camera.pitch, 1e-4f);
        assertEquals(g.player.pos.y + g.player.eyeHeight(), g.camera.position.y, 1e-4f, "from their own eyes");
    }

    @Test
    void aWalledInDeathWatchesFromStraightAboveAndAnOrdinaryDeathKeepsTheEye() {
        Game g = arena();
        play(g, 0.5f);
        int x = (int) Math.floor(g.player.pos.x), z = (int) Math.floor(g.player.pos.z);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    for (int y = (int) FEET; y < FEET + 3; y++) {
                        g.world.setBlock(x + dx, y, z + dz, BlockType.STONE, false);
                    }
                }
            }
        }
        g.player.pos.x = x + 0.5f;
        g.player.pos.z = z + 0.5f;
        play(g, 0.2f);
        Vector3f eye = new Vector3f(g.player.pos).add(0, g.player.eyeHeight(), 0);
        g.explosions.explode(g, g.player.pos.x, FEET + 0.3f, g.player.pos.z, SCRAP_POWER, SCRAP_DAMAGE,
                0f, true, true);
        g.playStep(DT, DT);
        g.playStep(DT, DT);
        assertEquals(Game.AppState.DEATH, g.appState);
        assertEquals(eye.x, g.camera.position.x, 1e-4f, "a shaft with no room behind: straight up");
        assertEquals(eye.z, g.camera.position.z, 1e-4f);
        assertTrue(g.camera.position.y > eye.y + 0.5f, "out of the head piece");

        Game ordinary = arena();
        play(ordinary, 0.5f);
        ordinary.player.hurt(1000f, false);
        ordinary.playStep(DT, DT);
        assertEquals(Game.AppState.DEATH, ordinary.appState);
        ordinary.playStep(DT, DT);
        assertEquals(ordinary.player.pos.y + ordinary.player.eyeHeight(), ordinary.camera.position.y, 1e-4f,
                "a death that leaves no remains is seen from the eye, as always");
        double particles = ordinary.particles.time;
        play(ordinary, 1f);
        assertEquals(particles, ordinary.particles.time, 0.0, "and nothing behind its card moves");
    }

    private static boolean inside(Vector3f p, BodyFragment f) {
        return Math.abs(p.x - f.pos.x) < f.halfWidth && Math.abs(p.z - f.pos.z) < f.halfWidth
                && Math.abs(p.y - f.pos.y) < f.halfHeight;
    }

    private static void play(Game g, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            g.playStep(DT, DT);
        }
    }

    /** A flat stone arena far from anything, its ground at y = 40; a Survival player stands on it. */
    private static Game arena() {
        Game game = new Game();
        game.newWorld(777L, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y < FEET ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        game.player.pos.set(310.5f, FEET, 310.5f);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.world.campPos = null;
        game.appState = Game.AppState.PLAYING;
        return game;
    }
}
