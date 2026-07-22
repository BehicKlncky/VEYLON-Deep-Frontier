package com.veylon.simulation;

import com.veylon.Game;
import com.veylon.entity.Affliction;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SimulationRegressionTest {

    @Test
    void afflictionDurationExpiresOnSimulationTick() {
        Game game = game(701L);
        game.player.hunger = 50f;
        game.player.thirst = 50f;
        game.player.addAffliction(Affliction.SPRAIN, 0.5f);

        game.player.tickNeeds(game, 0.6f);

        assertFalse(game.player.has(Affliction.SPRAIN));
    }

    @Test
    void burnAfflictionAppliesItsDocumentedDamageRate() {
        Game game = game(702L);
        game.player.health = 90f;
        game.player.hunger = 50f;
        game.player.thirst = 50f;
        game.player.addAffliction(Affliction.BURN, 10f);

        game.player.tickNeeds(game, 1f);

        assertEquals(89.82f, game.player.health, 0.02f);
        assertTrue(game.player.has(Affliction.BURN));
    }

    @Test
    void fireRejectsNonFlammableAndDuplicateCells() {
        Game game = game(703L);
        Vec3i position = testPosition(game);
        game.world.setBlock(position.x(), position.y(), position.z(), BlockType.STONE, false);
        assertFalse(game.fire.ignite(game, position.x(), position.y(), position.z()));
        game.world.setBlock(position.x(), position.y(), position.z(), BlockType.LOG, false);

        assertTrue(game.fire.ignite(game, position.x(), position.y(), position.z()));
        assertFalse(game.fire.ignite(game, position.x(), position.y(), position.z()));
        assertEquals(1, game.fire.count());
    }

    @Test
    void replacingBurningFuelWithWaterExtinguishesItOnNextTick() {
        Game game = game(704L);
        Vec3i position = testPosition(game);
        game.world.setBlock(position.x(), position.y(), position.z(), BlockType.LOG, false);
        assertTrue(game.fire.ignite(game, position.x(), position.y(), position.z()));
        game.world.setBlock(position.x(), position.y(), position.z(), BlockType.WATER, false);

        game.fire.mediumTick(game, 0.05f);

        assertEquals(0, game.fire.count());
        assertEquals(BlockType.WATER,
                game.world.getBlock(position.x(), position.y(), position.z()));
    }

    @Test
    void exposedRainExtinguishesFuelFasterThanDryWeather() {
        Game dry = game(705L);
        Game rain = game(705L);
        Vec3i dryPosition = testPosition(dry);
        Vec3i rainPosition = testPosition(rain);
        dry.world.setBlock(dryPosition.x(), dryPosition.y(), dryPosition.z(), BlockType.LOG, false);
        rain.world.setBlock(rainPosition.x(), rainPosition.y(), rainPosition.z(), BlockType.LOG, false);
        dry.fire.setRandomSeed(55L);
        rain.fire.setRandomSeed(55L);
        dry.fire.ignite(dry, dryPosition.x(), dryPosition.y(), dryPosition.z());
        rain.fire.ignite(rain, rainPosition.x(), rainPosition.y(), rainPosition.z());
        setWeather(rain, WeatherSystem.Weather.RAIN);

        dry.fire.mediumTick(dry, 3f);
        rain.fire.mediumTick(rain, 3f);

        assertEquals(1, dry.fire.count());
        assertEquals(0, rain.fire.count());
    }

    @Test
    void firePropagationIsDeterministicWhenSeeded() {
        Game first = propagationGame(706L, 99L);
        Game second = propagationGame(706L, 99L);

        for (int i = 0; i < 30; i++) {
            first.fire.mediumTick(first, 0.1f);
            second.fire.mediumTick(second, 0.1f);
        }

        assertTrue(first.fire.totalIgnitions > 1, "seeded fire must exercise propagation");
        assertEquals(first.fire.totalIgnitions, second.fire.totalIgnitions);
        assertEquals(first.fire.burningCells(), second.fire.burningCells());
    }

    private static Game propagationGame(long worldSeed, long fireSeed) {
        Game game = game(worldSeed);
        Vec3i center = testPosition(game);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                for (int dz = -1; dz <= 1; dz++) {
                    game.world.setBlock(center.x() + dx, center.y() + dy,
                            center.z() + dz, BlockType.LOG, false);
                }
            }
        }
        game.fire.setRandomSeed(fireSeed);
        game.fire.ignite(game, center.x(), center.y(), center.z());
        return game;
    }

    private static Game game(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        return game;
    }

    private static Vec3i testPosition(Game game) {
        return new Vec3i((int) game.player.pos.x + 5,
                Math.min(90, (int) game.player.pos.y + 12), (int) game.player.pos.z + 5);
    }

    private static void setWeather(Game game, WeatherSystem.Weather weather) {
        game.weather.current = weather;
        game.weather.next = weather;
        game.weather.blend = 1f;
    }
}
