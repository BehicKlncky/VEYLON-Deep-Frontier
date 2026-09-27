package com.veylon;

import com.veylon.entity.BodyFragment;
import com.veylon.entity.BurnResidue;
import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The body-fire capture scenes stage what their captures are read as
 * showing: held bodies catch at 1 s, burn on the spot and go out without
 * dying; rain puts out exactly the bodies open to the sky; a burning row blown
 * apart leaves one fire per body on its pieces; live bodies run; the weak
 * fall burning; the player burns and the rain puts them out. Held scenes
 * replay identically.
 */
class BodyFireQaSceneTest {

    private static final Vec3i SITE = new Vec3i(310, 40, 310);

    @ParameterizedTest
    @ValueSource(strings = {"body_fire_row", "body_fire_row_night", "body_fire_close"})
    void heldBodiesCatchBurnOnTheSpotAndGoOutAlive(String name) {
        Game game = BodyFireArena.arena();
        BodyFireQaScene scene = new BodyFireQaScene(game);
        scene.stage(SITE, name);
        List<Entity> bodies = bodies(game);
        assertTrue(bodies.size() >= 4, name + " stages a crowd");
        List<float[]> start = positions(bodies);
        scene.update(0.9);
        assertEquals(0, game.combustion.burningBodies(game), "nothing burns before 1 s");
        scene.update(1.3);
        assertEquals(bodies.size(), game.combustion.burningBodies(game), "every body catches");
        scene.update(10.0);
        assertEquals(0, game.combustion.burningBodies(game), "and burns out");
        for (Entity e : bodies) {
            assertFalse(e.dead, e + " is held alive for the capture");
            assertTrue(e.combustion.scorch() > 0.3f, "and scorched");
        }
        List<float[]> end = positions(bodies);
        for (int i = 0; i < bodies.size(); i++) {
            for (int k = 0; k < 3; k++) {
                assertEquals(start.get(i)[k], end.get(i)[k], 0f, "held bodies stay where they were put");
            }
        }
        assertFalse(game.player.dead);
        assertFalse(game.combustion.isBurning(game.player), "the camera's player never burns");
    }

    @Test
    void rainPutsOutExactlyTheBodiesOpenToTheSky() {
        Game game = BodyFireArena.arena();
        BodyFireQaScene scene = new BodyFireQaScene(game);
        scene.stage(SITE, "body_fire_out");
        List<Entity> bodies = bodies(game);
        scene.update(2.4);
        assertEquals(bodies.size(), game.combustion.burningBodies(game));
        scene.update(4.3);
        assertEquals(3, game.combustion.totalRainedOut, "the three bodies in the open rain go out");
        assertEquals(1, game.combustion.totalBurnouts, "the one with its feet in water burned out sooner");
        assertEquals(3, game.combustion.burningBodies(game), "the three under the roof burn on");
        for (Entity e : bodies) {
            if (e.combustion.burning()) {
                assertTrue(e.pos.x >= SITE.x() + 2, e + " burning on is under the roof");
            }
        }
        scene.update(8.0);
        assertEquals(0, game.combustion.burningBodies(game));
        assertEquals(4, game.combustion.totalBurnouts, "the roofed ones burned to the end");
    }

    @Test
    void aBurningRowBlownApartCarriesOneFirePerBodyOnItsPieces() {
        Game game = BodyFireArena.arena();
        BodyFireQaScene scene = new BodyFireQaScene(game);
        scene.stage(SITE, "body_fire_blast");
        int bodies = bodies(game).size();
        scene.update(1.9);
        assertEquals(bodies, game.combustion.burningBodies(game));
        scene.update(2.2);
        assertTrue(bodies(game).isEmpty(), "every body came apart");
        Set<BurnResidue> fires = new HashSet<>();
        for (BodyFragment f : game.fragments.live) {
            assertTrue(f.burn != null && f.burn.flame() > 0f, f.definition + " burns on");
            fires.add(f.burn);
        }
        assertEquals(bodies, fires.size(), "one fire per body, shared by its pieces");
        assertEquals(bodies, game.burnResidues.trackedCount());
        scene.update(9.5);
        assertEquals(0, game.burnResidues.trackedCount(), "the flames and smoke are over");
    }

    @Test
    void liveBodiesRunTheWeakFallBurningAndBurnOnAsBodies() {
        Game game = BodyFireArena.arena();
        BodyFireQaScene scene = new BodyFireQaScene(game);
        scene.stage(SITE, "body_fire_panic");
        List<Entity> bodies = bodies(game);
        List<float[]> start = positions(bodies);
        scene.update(3.0);
        int moved = 0;
        for (int i = 0; i < bodies.size(); i++) {
            float[] was = start.get(i);
            Entity e = bodies.get(i);
            moved += Math.hypot(e.pos.x - was[0], e.pos.z - was[2]) > 1.5 ? 1 : 0;
        }
        assertTrue(moved >= bodies.size() - 1, "burning bodies flee: " + moved + " of " + bodies.size());

        Game weak = BodyFireArena.arena();
        BodyFireQaScene fall = new BodyFireQaScene(weak);
        fall.stage(SITE, "body_fire_ragdoll");
        int crowd = bodies(weak).size();
        fall.update(3.5);
        assertTrue(bodies(weak).isEmpty(), "none outlives its fire");
        assertTrue(weak.burnResidues.totalCaptured >= crowd - 1, "they fall burning: " + weak.burnResidues.totalCaptured);
        fall.update(6.0);
        boolean burningBody = false;
        for (var c : weak.entities.corpses) {
            burningBody |= c.burn != null && c.burn.active();
        }
        for (var c : weak.entities.carcasses) {
            burningBody |= c.burn != null && c.burn.active();
        }
        assertTrue(burningBody || !weak.ragdolls.live.isEmpty(), "and burn or smoke on as bodies");
    }

    @Test
    void birdsCatchInFlight() {
        Game game = BodyFireArena.arena();
        BodyFireQaScene scene = new BodyFireQaScene(game);
        scene.stage(SITE, "body_fire_bird");
        scene.update(0.8);
        int flying = 0;
        for (Creature c : game.entities.creatures) {
            flying += game.combustion.isBurning(c) && c.pos.y > SITE.y() + 1.5f ? 1 : 0;
        }
        assertEquals(3, flying, "three birds alight in the air");
    }

    @Test
    void thePlayerBurnsAndTheRainPutsThemOut() {
        Game game = BodyFireArena.arena();
        BodyFireQaScene scene = new BodyFireQaScene(game);
        scene.stage(SITE, "body_fire_player");
        scene.update(1.5);
        assertTrue(game.combustion.isBurning(game.player), "the player is alight");
        assertTrue(game.player.selected() != null, "with an item in hand");
        scene.update(7.5);
        assertFalse(game.combustion.isBurning(game.player));
        assertTrue(game.player.combustion.outDoused() || game.combustion.totalRainedOut > 0,
                "put out by the rain");
        assertFalse(game.player.dead);
    }

    @Test
    void aHeldSceneReplaysIdentically() {
        assertEquals(snapshot(), snapshot());
    }

    // ------------------------------------------------------------------

    private static String snapshot() {
        Game game = BodyFireArena.arena();
        BodyFireQaScene scene = new BodyFireQaScene(game);
        scene.stage(SITE, "body_fire_blast");
        scene.update(4.0);
        StringBuilder s = new StringBuilder();
        for (BodyFragment f : game.fragments.live) {
            s.append(f.definition).append(f.pos).append(f.burn.flame()).append('\n');
        }
        for (BodyFragment f : game.fragments.settled) {
            s.append(f.definition).append(f.pos).append(f.burn.flame()).append('\n');
        }
        return s.toString();
    }

    private static List<Entity> bodies(Game game) {
        List<Entity> out = new ArrayList<>();
        for (Npc n : game.entities.npcs) {
            out.add(n);
        }
        for (Creature c : game.entities.creatures) {
            out.add(c);
        }
        return out;
    }

    private static List<float[]> positions(List<Entity> bodies) {
        List<float[]> out = new ArrayList<>();
        for (Entity e : bodies) {
            out.add(new float[] {e.pos.x, e.pos.y, e.pos.z});
        }
        return out;
    }
}
