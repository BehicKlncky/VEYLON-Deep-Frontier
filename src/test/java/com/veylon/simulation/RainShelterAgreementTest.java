package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.item.ItemType;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.RackBatch;
import com.veylon.world.World;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.veylon.simulation.FireConstants.RAIN_EXTINGUISH_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 0.9.1 B10: a drying rack, a rain collector and a flame agree on whether the rain reaches them,
 * because all three ask the one predicate ({@link FireSystem#isRainedOn}). Each shelter below
 * covers a rack, a collector and a burning log alike; through the systems' own ticks the rack
 * stalls, the collector fills and the rain puts the flame out in exactly the same shelters.
 */
class RainShelterAgreementTest {

    private static final float SLOW = 10f;
    private static final float MEDIUM = 0.5f;
    private static final int FLOOR = 40;
    private static final int ROW_Z = 330;

    private Game g;

    @BeforeEach
    void setUp() {
        g = new Game();
        g.newWorld(777L, true, World.GEN_CAVE_IDENTITIES);
        // A private flat arena far from spawn structures, its ground at y = 39.
        for (int cx = 18; cx <= 22; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = g.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y < FLOOR ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        g.player.pos.set(300.5f, FLOOR + 0.1f, 300.5f);
        g.player.envTemp = 15f; // below the warm-air drying boost
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        g.weather.current = Weather.RAIN;
        g.weather.next = Weather.RAIN;
        g.weather.blend = 1f;
    }

    /** Shelters over a rack, a collector and a log, each kind over its own strip of the arena. */
    private enum Shelter {
        /** Nothing overhead. */
        OPEN(0, null),
        /** A stone roof with a block of headroom. */
        ROOF(2, BlockType.STONE),
        /** A stone slab resting on the block: the cell above is the top of its column, so open. */
        LID(1, BlockType.STONE),
        /** A leaf canopy with headroom. */
        CANOPY(2, BlockType.LEAVES);

        final int height;
        final BlockType cover;

        Shelter(int height, BlockType cover) {
            this.height = height;
            this.cover = cover;
        }
    }

    @Test
    void racksCollectorsAndFlamesAgreeUnderEveryShelterInRainAndSnow() {
        int x = 310;
        int sheltered = 0, exposed = 0;
        for (Shelter shelter : Shelter.values()) {
            Vec3i rack = new Vec3i(x, FLOOR, ROW_Z);
            Vec3i collector = new Vec3i(x + 3, FLOOR, ROW_Z);
            Vec3i log = new Vec3i(x + 6, FLOOR, ROW_Z);
            for (Vec3i p : new Vec3i[]{rack, collector, log}) {
                if (shelter.cover != null) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            g.world.setBlock(p.x() + dx, p.y() + shelter.height, p.z() + dz, shelter.cover, false);
                        }
                    }
                }
            }
            g.world.setBlock(rack.x(), rack.y(), rack.z(), BlockType.DRYING_RACK, false);
            g.world.rackBatches.put(rack, new RackBatch(ItemType.RAW_MEAT, 2));
            g.world.setBlock(collector.x(), collector.y(), collector.z(), BlockType.RAIN_COLLECTOR, false);
            g.world.collectorWater.put(collector, 0f);
            g.world.setBlock(log.x(), log.y(), log.z(), BlockType.LOG, false);

            g.itemConditions.slowTick(g, SLOW);
            float dried = g.world.rackBatches.get(rack).progress;
            boolean rackStalled = dried < SLOW * 0.5f;
            boolean collectorFilled = g.world.collectorWater.get(collector) > 0f;

            g.fire.reset();
            g.fire.setRandomSeed(5L);
            assertTrue(g.fire.ignite(g, log.x(), log.y(), log.z()), shelter + ": the log lights");
            for (float t = 0; t <= RAIN_EXTINGUISH_SECONDS + MEDIUM; t += MEDIUM) {
                g.fire.mediumTick(g, MEDIUM);
            }
            boolean flameOut = g.fire.totalExtinguished == 1;

            assertEquals(flameOut, rackStalled, shelter + ": the rack and the flame disagree (dried "
                    + dried + " s in " + SLOW + " s)");
            assertEquals(flameOut, collectorFilled, shelter + ": the collector and the flame disagree");
            assertEquals(flameOut, g.fire.isRainedOn(g, rack.x(), rack.y(), rack.z()), shelter + ": the predicate");
            if (flameOut) { exposed++; } else { sheltered++; }

            // Snow reaches the same places; it stalls a rack and puts a flame out, and a
            // collector gathers nothing from it, as before.
            g.weather.current = Weather.SNOW;
            g.weather.next = Weather.SNOW;
            g.world.rackBatches.get(rack).progress = 0f;
            g.world.collectorWater.put(collector, 0f);
            g.itemConditions.slowTick(g, SLOW);
            assertEquals(flameOut, g.world.rackBatches.get(rack).progress < SLOW * 0.5f, shelter + ": snow on the rack");
            assertEquals(0f, g.world.collectorWater.get(collector), shelter + ": a collector gathers no snow");
            g.weather.current = Weather.RAIN;
            g.weather.next = Weather.RAIN;
            x += 12;
        }
        assertTrue(exposed >= 2 && sheltered >= 1, "both outcomes are exercised: " + exposed + " exposed, "
                + sheltered + " sheltered");
    }
}
