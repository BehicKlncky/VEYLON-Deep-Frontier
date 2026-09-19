package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;

import static com.veylon.simulation.FireConstants.BURN_SECONDS_MIN;
import static com.veylon.simulation.FireConstants.BURN_SECONDS_RANGE;
import static com.veylon.simulation.FireConstants.RAIN_EXTINGUISH_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Rain puts out fires that are open to the sky and leaves the block they were
 * burning; sheltered fires burn on. In dry weather fire climbs trees and
 * crosses wooden walls.
 *
 * <p>Weather is set directly and only {@link FireSystem#mediumTick} is driven:
 * a weather tick could roll a new target mid-test.
 */
class FireWeatherTest {

    private static final float TICK = 0.5f;
    private static final int SEEDS = 10;
    /** Seeds out of {@link #SEEDS} a dry-weather fire must succeed in. */
    private static final int REQUIRED_SEEDS = 9;
    private static final float DRY_SPREAD_SECONDS = 30f;

    private Game g;

    @BeforeEach
    void setUp() {
        g = new Game();
        g.newWorld(777L, true);
        // Flatten a private arena far from spawn structures at y=40.
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = g.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        g.player.pos.set(310, 40.1f, 310);
        g.entities.creatures.clear();
        g.entities.npcs.clear();
    }

    @Test
    void rainExtinguishesAnExposedFireWithinTheBoundAndKeepsTheBlock() {
        Vec3i log = new Vec3i(330, 40, 330);
        place(log, BlockType.LOG);
        setWeather(Weather.RAIN);
        assertTrue(g.fire.isRainedOn(g, log.x(), log.y(), log.z()), "precondition: open to the sky");
        assertTrue(g.fire.ignite(g, log.x(), log.y(), log.z()),
                "a rained-on block still lights, so a thrown flame can flare");
        g.particles.count = 0;

        g.fire.mediumTick(g, TICK);
        assertTrue(burning(log), "the flame is not snuffed out on the tick it is lit");
        for (float t = TICK; t < RAIN_EXTINGUISH_SECONDS + TICK; t += TICK) {
            g.fire.mediumTick(g, TICK);
        }

        assertFalse(burning(log), "rain puts an exposed fire out within "
                + (RAIN_EXTINGUISH_SECONDS + TICK) + " s");
        assertEquals(BlockType.LOG, block(log), "an extinguished block is not consumed");
        assertEquals(1, g.fire.totalExtinguished);
        assertEquals(2, g.particles.count, "two smoke puffs mark where the rain put it out");
    }

    @Test
    void rainedOnFiresDoNotSpread() {
        for (long seed = 1; seed <= SEEDS; seed++) {
            layLogLine();
            g.fire.reset();
            g.fire.setRandomSeed(fireSeed(seed));
            setWeather(Weather.RAIN);
            assertTrue(g.fire.ignite(g, 330, 40, 330));

            for (int tick = 0; tick < 20; tick++) {
                g.fire.mediumTick(g, TICK);
                assertTrue(g.fire.count() <= 1, "a rained-on fire spread, seed " + seed);
            }
            assertEquals(1, g.fire.totalIgnitions, "only the lit log ever burned, seed " + seed);
            assertEquals(1, g.fire.totalExtinguished, "and rain put it out, seed " + seed);
            for (int x = 330; x <= 336; x++) {
                assertEquals(BlockType.LOG, g.world.getBlock(x, 40, 330),
                        "the whole line is still wood, seed " + seed);
            }
        }

        // The same line does carry a fire once the sky is clear, so the
        // absence of spread above is the rain's doing.
        int spread = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            layLogLine();
            g.fire.reset();
            g.fire.setRandomSeed(fireSeed(seed));
            setWeather(Weather.CLEAR);
            g.fire.ignite(g, 330, 40, 330);
            for (int tick = 0; tick < 20; tick++) {
                g.fire.mediumTick(g, TICK);
            }
            if (g.fire.totalIgnitions > 1) {
                spread++;
            }
        }
        assertTrue(spread >= REQUIRED_SEEDS, "control: the dry line spread in only " + spread
                + " of " + SEEDS + " seeds");
    }

    @Test
    void shelteredFiresKeepBurningInTheRain() {
        Vec3i log = new Vec3i(330, 40, 330);
        place(log, BlockType.LOG);
        // A roof with headroom. A roof resting on the block itself does not
        // shelter it: exposure is the sky light of the cell above, and the top
        // of a column is always fully lit.
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                g.world.setBlock(log.x() + dx, log.y() + 2, log.z() + dz, BlockType.STONE, false);
            }
        }
        setWeather(Weather.RAIN);
        assertFalse(g.fire.isRainedOn(g, log.x(), log.y(), log.z()), "precondition: under the roof");
        g.fire.setRandomSeed(3L);
        assertTrue(g.fire.ignite(g, log.x(), log.y(), log.z()));

        float t = 0;
        for (; t < RAIN_EXTINGUISH_SECONDS + TICK; t += TICK) {
            g.fire.mediumTick(g, TICK);
        }
        assertTrue(burning(log), "rain does not reach a fire under a roof");

        for (; t < BURN_SECONDS_MIN + BURN_SECONDS_RANGE + TICK; t += TICK) {
            g.fire.mediumTick(g, TICK);
        }
        assertFalse(burning(log));
        assertEquals(BlockType.ASH, block(log), "a sheltered log still burns down to ash");
        assertEquals(0, g.fire.totalExtinguished, "nothing was put out");
    }

    @Test
    void snowAndStormAlsoExtinguish() {
        Set<Weather> precipitation = EnumSet.of(Weather.RAIN, Weather.STORM, Weather.SNOW);
        Vec3i log = new Vec3i(330, 40, 330);
        for (Weather weather : Weather.values()) {
            place(log, BlockType.LOG);
            g.fire.reset();
            setWeather(weather);
            assertTrue(g.fire.ignite(g, log.x(), log.y(), log.z()));

            for (float t = 0; t < RAIN_EXTINGUISH_SECONDS + TICK; t += TICK) {
                g.fire.mediumTick(g, TICK);
            }

            if (precipitation.contains(weather)) {
                assertFalse(burning(log), weather + " puts an exposed fire out");
                assertEquals(1, g.fire.totalExtinguished, weather.displayName);
            } else {
                assertTrue(burning(log), weather + " is dry: the fire burns on");
                assertEquals(0, g.fire.totalExtinguished, weather.displayName);
            }
            assertEquals(BlockType.LOG, block(log), weather + " leaves the log unburnt so far");
        }
    }

    @Test
    void rainFreezesTheBurnSoAnAlmostBurntLogStillSurvives() {
        int maxTicks = (int) ((BURN_SECONDS_MIN + BURN_SECONDS_RANGE) / TICK) + 1;
        Vec3i log = new Vec3i(330, 40, 330);
        for (long seed = 1; seed <= SEEDS; seed++) {
            // Dry, this seed's log is ash after ticksToAsh ticks.
            relight(log, seed);
            int ticksToAsh = 0;
            while (block(log) == BlockType.LOG && ticksToAsh <= maxTicks) {
                g.fire.mediumTick(g, TICK);
                ticksToAsh++;
            }
            assertEquals(BlockType.ASH, block(log), "precondition: a dry log burns down, seed " + seed);

            // The same seed draws the same fuel. Rain arrives one tick before
            // the end, so a burn that kept running under rain would take the
            // log on the next tick.
            relight(log, seed);
            for (int i = 0; i < ticksToAsh - 1; i++) {
                g.fire.mediumTick(g, TICK);
            }
            assertTrue(burning(log), "precondition: still alight when the rain starts, seed " + seed);
            setWeather(Weather.RAIN);
            tick(RAIN_EXTINGUISH_SECONDS + TICK);

            assertFalse(burning(log), "rain put it out, seed " + seed);
            assertEquals(BlockType.LOG, block(log), "rain saved the almost-burnt log, seed " + seed);
        }
    }

    @Test
    void wetnessDriesOffWhenTheRainStops() {
        Vec3i log = new Vec3i(330, 40, 330);
        place(log, BlockType.LOG);
        g.fire.setRandomSeed(5L);
        assertTrue(g.fire.ignite(g, log.x(), log.y(), log.z()));
        float shower = RAIN_EXTINGUISH_SECONDS - TICK;

        setWeather(Weather.RAIN);
        tick(shower);
        setWeather(Weather.CLEAR);
        // Shorter than the shortest burn, so the log is still there after it.
        tick(shower);
        setWeather(Weather.RAIN);
        tick(shower);
        assertTrue(burning(log), "two showers split by a dry spell do not add up");

        tick(TICK);
        assertFalse(burning(log), "a full " + RAIN_EXTINGUISH_SECONDS + " s of rain does");
        assertEquals(BlockType.LOG, block(log));
    }

    @Test
    void fireClimbsATreeInDryWeather() {
        int reached = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            clearArena();
            // A generated-style tree: a five-log trunk under a 5x5, two-deep crown.
            for (int y = 40; y <= 44; y++) {
                g.world.setBlock(330, y, 330, BlockType.LOG, false);
            }
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int y = 45; y <= 46; y++) {
                        g.world.setBlock(330 + dx, y, 330 + dz, BlockType.LEAVES, false);
                    }
                }
            }
            g.fire.reset();
            g.fire.setRandomSeed(fireSeed(seed));
            setWeather(Weather.CLEAR);
            assertTrue(g.fire.ignite(g, 330, 40, 330), "lit at the foot of the trunk");

            if (burnsUntil(45)) {
                reached++;
            }
        }
        assertTrue(reached >= REQUIRED_SEEDS, "fire reached the crown within "
                + DRY_SPREAD_SECONDS + " s in only " + reached + " of " + SEEDS + " seeds");
    }

    @Test
    void fireSpreadsThroughAWoodenWall() {
        int crossed = 0;
        for (long seed = 1; seed <= SEEDS; seed++) {
            clearArena();
            // Five planks wide, three high, lit at one bottom corner.
            for (int x = 330; x <= 334; x++) {
                for (int y = 40; y <= 42; y++) {
                    g.world.setBlock(x, y, 330, BlockType.PLANK, false);
                }
            }
            g.fire.reset();
            g.fire.setRandomSeed(fireSeed(seed));
            setWeather(Weather.CLEAR);
            assertTrue(g.fire.ignite(g, 330, 40, 330));

            boolean farEnd = false;
            boolean top = false;
            for (float t = 0; t < DRY_SPREAD_SECONDS && !(farEnd && top); t += TICK) {
                g.fire.mediumTick(g, TICK);
                for (Vec3i p : g.fire.burningCells()) {
                    farEnd |= p.x() == 334;
                    top |= p.y() == 42;
                }
            }
            if (farEnd && top) {
                crossed++;
            }
        }
        assertTrue(crossed >= REQUIRED_SEEDS, "fire crossed and climbed the wall within "
                + DRY_SPREAD_SECONDS + " s in only " + crossed + " of " + SEEDS + " seeds");
    }

    @Test
    void fireCapStillHolds() {
        // A solid block of fuel far larger than the cap, lit from one cell;
        // spread now targets only fuel, so it reaches the cap quickly.
        for (int x = 320; x < 328; x++) {
            for (int y = 40; y < 48; y++) {
                for (int z = 320; z < 328; z++) {
                    g.world.setBlock(x, y, z, BlockType.LOG, false);
                }
            }
        }
        g.fire.setRandomSeed(11L);
        setWeather(Weather.CLEAR);
        assertTrue(g.fire.ignite(g, 323, 43, 323));

        int peak = 0;
        for (float t = 0; t < 60f; t += TICK) {
            g.fire.mediumTick(g, TICK);
            assertTrue(g.fire.count() <= FireSystem.MAX_ACTIVE_FIRES,
                    "spread exceeded the active-fire cap at " + t + " s");
            peak = Math.max(peak, g.fire.count());
        }
        assertEquals(FireSystem.MAX_ACTIVE_FIRES, peak, "the cap is reachable through spread alone");
    }

    /**
     * Spreads a loop index over the seed space. {@link java.util.Random} gives
     * nearly the same first float for small consecutive seeds, so seeds 1 to 10
     * would light every log with the same burn time.
     */
    private static long fireSeed(long i) {
        return i * 0x9E3779B97F4A7C15L;
    }

    /** Ticks dry fire until something at or above {@code crownY} burns, or time runs out. */
    private boolean burnsUntil(int crownY) {
        for (float t = 0; t < DRY_SPREAD_SECONDS; t += TICK) {
            g.fire.mediumTick(g, TICK);
            for (Vec3i p : g.fire.burningCells()) {
                if (p.y() >= crownY) {
                    return true;
                }
            }
        }
        return false;
    }

    /** A fresh log at {@code p}, lit in clear weather from a known seed. */
    private void relight(Vec3i p, long seed) {
        place(p, BlockType.LOG);
        g.fire.reset();
        g.fire.setRandomSeed(fireSeed(seed));
        setWeather(Weather.CLEAR);
        assertTrue(g.fire.ignite(g, p.x(), p.y(), p.z()));
    }

    private void tick(float seconds) {
        for (float t = 0; t < seconds; t += TICK) {
            g.fire.mediumTick(g, TICK);
        }
    }

    private void layLogLine() {
        clearArena();
        for (int x = 330; x <= 336; x++) {
            g.world.setBlock(x, 40, 330, BlockType.LOG, false);
        }
    }

    private void clearArena() {
        for (int x = 325; x <= 340; x++) {
            for (int z = 325; z <= 335; z++) {
                for (int y = 40; y <= 48; y++) {
                    g.world.setBlock(x, y, z, BlockType.AIR, false);
                }
            }
        }
    }

    private void place(Vec3i p, BlockType type) {
        g.world.setBlock(p.x(), p.y(), p.z(), type, false);
    }

    private BlockType block(Vec3i p) {
        return g.world.getBlock(p.x(), p.y(), p.z());
    }

    private boolean burning(Vec3i p) {
        return g.fire.burningCells().contains(p);
    }

    private void setWeather(Weather weather) {
        g.weather.current = weather;
        g.weather.next = weather;
        g.weather.blend = 1f;
    }
}
