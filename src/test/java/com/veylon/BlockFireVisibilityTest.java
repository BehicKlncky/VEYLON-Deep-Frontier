package com.veylon;

import com.veylon.engine.BlockFireFaces;
import com.veylon.simulation.WeatherSystem;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.9.1 B09: a burning log or leaf block shows its fire. Every block that burns is opaque, and
 * the flames, embers and glow of a burning block started inside it, so a burning tree showed
 * little but smoke. They now start on the faces open to the air, outside the block; a block with
 * no open face shows nothing, and the emitter gives off no more than it did.
 */
class BlockFireVisibilityTest {

    private static final int FLOOR = 40;
    private static final int X = 320, Z = 320;

    @Test
    void whatABurningTrunkAndCanopyGiveOffStartsInTheAirOutsideThem() {
        Game g = arena();
        // A trunk of three logs under a leaf, the way a tree stands: every log has open sides,
        // the top log is covered by the leaf, the leaf is open all round.
        for (int y = FLOOR; y < FLOOR + 3; y++) {
            g.world.setBlock(X, y, Z, BlockType.LOG, false);
        }
        g.world.setBlock(X, FLOOR + 3, Z, BlockType.LEAVES, false);
        for (int y = FLOOR; y <= FLOOR + 3; y++) {
            assertTrue(g.fire.ignite(g, X, y, Z), "the " + g.world.getBlock(X, y, Z) + " at " + y + " catches");
        }
        g.particles.count = 0;
        int passes = 0;
        for (int i = 0; i < 40; i++) {
            g.ambience.updateEmitters(0.13f); // one pass of the emitters each time
            passes++;
        }
        assertTrue(g.particles.count >= passes * 4, "every burning block gives off smoke and a flame each pass: "
                + g.particles.count);
        assertTrue(g.particles.count <= passes * 4 * 3, "and no more than flame, smoke and an ember each: "
                + g.particles.count);
        for (int i = 0; i < g.particles.count; i++) {
            int cx = (int) Math.floor(g.particles.px[i]), cy = (int) Math.floor(g.particles.py[i]);
            int cz = (int) Math.floor(g.particles.pz[i]);
            assertFalse(g.world.getBlock(cx, cy, cz).opaque, "particle " + i + " starts in the air, not inside the "
                    + g.world.getBlock(cx, cy, cz) + " at " + cx + ", " + cy + ", " + cz);
        }
    }

    @Test
    void aBurningBlockWithNoFaceOpenToTheAirShowsNothing() {
        Game g = arena();
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                for (int y = FLOOR; y <= FLOOR + 1; y++) {
                    g.world.setBlock(X + dx, y, Z + dz, BlockType.PLANK, false);
                }
            }
        }
        assertEquals(0, BlockFireFaces.open(g.world, X, FLOOR, Z), "precondition: buried on every side and above");
        assertTrue(g.fire.ignite(g, X, FLOOR, Z));
        g.particles.count = 0;
        for (int i = 0; i < 10; i++) {
            g.ambience.updateEmitters(0.13f);
        }
        assertEquals(0, g.particles.count, "nothing of it reaches the air");
    }

    @Test
    void theFacesAreTheOpenSidesAndTopNeverTheBottomAndPicksStayAmongThem() {
        Game g = arena();
        g.world.setBlock(X, FLOOR, Z, BlockType.LOG, false);
        g.world.setBlock(X + 1, FLOOR, Z, BlockType.STONE, false);
        g.world.setBlock(X, FLOOR + 1, Z, BlockType.LEAVES, false);
        int open = BlockFireFaces.open(g.world, X, FLOOR, Z);
        assertEquals((1 << BlockFireFaces.WEST) | (1 << BlockFireFaces.SOUTH) | (1 << BlockFireFaces.NORTH), open,
                "east is walled, the top is covered, and the ground below is never a face");
        Random rng = new Random(7);
        int[] seen = new int[BlockFireFaces.UP + 1];
        for (int i = 0; i < 3000; i++) {
            seen[BlockFireFaces.pick(open, rng)]++;
        }
        assertEquals(0, seen[BlockFireFaces.EAST]);
        assertEquals(0, seen[BlockFireFaces.UP]);
        for (int face : new int[]{BlockFireFaces.WEST, BlockFireFaces.SOUTH, BlockFireFaces.NORTH}) {
            assertEquals(1000, seen[face], 120, "each open face as likely: " + face);
        }
    }

    /** A flat stone arena far from spawn, its ground at y = 40, under a clear dusk sky. */
    private static Game arena() {
        Game game = new Game();
        game.newWorld(777L, true, World.GEN_CAVE_IDENTITIES);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y < FLOOR ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        game.player.pos.set(X - 6.5f, FLOOR, Z + 0.5f);
        game.weather.current = WeatherSystem.Weather.CLEAR;
        game.weather.next = WeatherSystem.Weather.CLEAR;
        game.weather.blend = 1f;
        game.events.active.clear();
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        return game;
    }
}
