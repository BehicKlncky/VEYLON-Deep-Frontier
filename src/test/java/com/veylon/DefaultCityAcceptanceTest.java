package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.TimePreset;
import com.veylon.simulation.WeatherSystem;
import com.veylon.ui.NewFrontierScreen;
import com.veylon.ui.TitleScreen;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * Urban milestone 26, the final acceptance on the default new-world path. NEW FRONTIER's defaults
 * (the abandoned city, Survival) build exactly the world every generator-20 route test plays, so
 * the multi-day economy route, the integrated save audit and the slice routes hold on the path a
 * player takes. On that path: package 25's integrated city (an apartment searched, an infected and
 * an alien killed, a camp's mission done for hidden civilians, a raider yard taken and a stronghold
 * grateful, a claimed home with power and water) is made through the game's own commands, saved,
 * the title reached and the save loaded from it fact for fact; death wakes the survivor at the
 * refuge with what they carried; the pause menu and its screens route as they always did; and
 * Creative on the default city flies, builds free and rules the clock and the sky.
 */
class DefaultCityAcceptanceTest {
    private static final long SEED = IntegratedCityFixture.SEED;
    private static final float DT = 1f / 20f;

    @TempDir Path directory;

    @Test
    void newFrontiersDefaultsBuildTheWorldEveryCityRouteTestPlays() {
        Game front = newFrontier(SEED, directory.resolve("slot.sav"));
        Game direct = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, World.GEN_URBAN_ECONOMY, new AudioManager());
        assertEquals(World.GEN_URBAN_ECONOMY, front.world.generatorVersion, "the default is the economy city");
        assertEquals(GameMode.SURVIVAL, front.gameMode(), "Survival is the default mode");
        assertEquals(direct.world.urban.contentRevision(), front.world.urban.contentRevision());
        assertEquals(direct.spawnPos, front.spawnPos, "the same refuge bed");
        assertEquals(direct.world.urban.refugeBuilding, front.world.urban.refugeBuilding);
        assertEquals(SaveAuditQaScene.contents(direct.player.inventory), SaveAuditQaScene.contents(front.player.inventory));
        assertEquals(direct.eventLog.all(), front.eventLog.all(), "the same first session");
        int cx = Math.floorDiv((int) Math.floor(front.spawnPos.x), Chunk.SX);
        int cz = Math.floorDiv((int) Math.floor(front.spawnPos.z), Chunk.SZ);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                Chunk a = front.world.getOrCreateChunk(cx + dx, cz + dz), b = direct.world.getOrCreateChunk(cx + dx, cz + dz);
                for (int x = 0; x < Chunk.SX; x++) {
                    for (int z = 0; z < Chunk.SZ; z++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            assertEquals(b.get(x, y, z), a.get(x, y, z), "the same city at chunk " + (cx + dx) + "," + (cz + dz));
                        }
                    }
                }
            }
        }
    }

    @Test
    void theIntegratedCityTravelsFromNewFrontierThroughASaveAndTheTitleBackIntoPlay() {
        Path slot = directory.resolve("slot.sav");
        Game g = newFrontier(SEED, slot);
        IntegratedCityFixture.Built built = IntegratedCityFixture.build(g);
        List<String> saved = IntegratedCityFixture.describe(g, built.facts());
        List<String> durable = SaveAuditQaScene.durable(g);

        assertTrue(SaveSystem.save(g, slot), "F5 writes the city");
        g.frontend.returnToTitle();
        assertNull(g.world, "the title holds no world");
        g.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        assertEquals("Restoring your frontier...", g.frontend.loadingDetail());
        assertTrue(g.frontend.performLoad(), "LOAD FRONTIER restores it");
        assertEquals(Game.AppState.PLAYING, g.appState);
        assertEquals(World.GEN_URBAN_ECONOMY, g.world.generatorVersion);
        assertEquals(saved, IntegratedCityFixture.describe(g, built.facts()), "every fact, as it was saved");
        assertEquals(durable, SaveAuditQaScene.durable(g), "and everything a save must hold");

        // NEW FRONTIER over it and back again: the next new city inherits nothing, the slot still loads.
        g.frontend.returnToTitle();
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertEquals(NewFrontierScreen.CITY_REPLACE_NOTICE, g.newFrontierScreen.replaceNotice(),
                "the shared slot's notice names the new city");
        assertTrue(g.newFrontierScreen.showsReplaceNotice());
        g.frontend.applyNewFrontier(NewFrontierScreen.Action.START);
        assertTrue(g.frontend.performLoad());
        assertEquals(0, g.world.changedBlocks.size(), "a new city starts unedited");
        assertEquals(0, g.world.urban.missions.missions().size(), "and owes nobody anything");
        g.frontend.returnToTitle();
        g.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        assertTrue(g.frontend.performLoad());
        assertEquals(saved, IntegratedCityFixture.describe(g, built.facts()), "an unsaved new city never touched the slot");
    }

    @Test
    void deathOnTheDefaultCityWakesTheSurvivorAtTheRefugeWithWhatTheyCarried() {
        Game g = newFrontier(SEED, directory.resolve("slot.sav"));
        BuildingPlan neighbour = RefugeWorldArena.neighbour(g);
        SurvivorWorldArena.standAt(g, neighbour.approaches().getFirst());
        g.player.inventory.add(ItemType.SCRAP, 3);
        String carried = SaveAuditQaScene.contents(g.player.inventory);

        g.player.hurt(1000f, false);
        g.enterDeathIfDue();
        assertEquals(Game.AppState.DEATH, g.appState, "the damage gate kills a Survival body");
        g.respawn(); // what Enter or the card's timer does
        g.appState = Game.AppState.PLAYING;

        assertFalse(g.player.dead);
        assertEquals(55f, g.player.health, 1e-4f);
        assertEquals(ParcelPlan.Use.REFUGE, g.world.urban.parcelAt((int) Math.floor(g.player.pos.x),
                (int) Math.floor(g.player.pos.z)).use(), "P-08: the survivor wakes in the refuge");
        assertEquals(carried, SaveAuditQaScene.contents(g.player.inventory), "with what they carried");
        assertTrue(g.eventLog.recent(3).stream().anyMatch(l -> l.endsWith("Your carried supplies remain with you.")));
    }

    @Test
    void thePauseMenuAndItsScreensRouteOnTheDefaultCity() {
        Game g = newFrontier(SEED, directory.resolve("slot.sav"));
        HotkeyRouter router = new HotkeyRouter(g);
        route(g, router, GLFW_KEY_ESCAPE);
        assertEquals(Game.UiMode.PAUSE, g.uiMode, "Esc pauses");
        assertTrue(g.uiMode.pausesSimulation());
        route(g, router, GLFW_KEY_O);
        assertEquals(Game.UiMode.OPTIONS, g.uiMode, "O opens graphics");
        g.uiMode = Game.UiMode.PAUSE; // the screen's Back
        route(g, router, GLFW_KEY_V);
        assertEquals(Game.UiMode.AUDIO_OPTIONS, g.uiMode, "V opens audio");
        g.frontend.handlePauseAudio(com.veylon.ui.AudioOptionsScreen.Action.CANCEL);
        assertEquals(Game.UiMode.PAUSE, g.uiMode, "audio returns to pause");
        route(g, router, GLFW_KEY_G);
        assertEquals(Game.UiMode.GAME_MODE, g.uiMode, "G opens the mode switch");
        g.gameModes.handleScreen(com.veylon.ui.GameModeScreen.Action.CANCEL);
        assertEquals(Game.UiMode.PAUSE, g.uiMode);
        route(g, router, GLFW_KEY_ESCAPE);
        assertEquals(Game.UiMode.NONE, g.uiMode, "Esc resumes");
    }

    @Test
    void creativeOnTheDefaultCityFliesBuildsFreeAndRulesTheClockAndTheSky() {
        Game g = newFrontier(SEED, directory.resolve("slot.sav"), GLFW_KEY_C);
        assertEquals(World.DEFAULT_NEW_WORLD_GENERATOR, g.world.generatorVersion);
        assertEquals(GameMode.CREATIVE, g.gameMode());
        assertTrue(g.creativeMarked());
        g.player.hurt(1000f, false);
        assertFalse(g.player.dead, "nothing hurts a Creative body");

        PlayerMovementSystem movement = new PlayerMovementSystem();
        PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
        PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();
        float y0 = g.player.pos.y;
        for (int tap = 0; tap < 2; tap++) {
            command.set(90f, false, false, false, false, false, false, true, true);
            movement.update(g.player, g.world, command, DT, result);
            for (int i = 0; i < 3; i++) {
                command.set(90f, false, false, false, false, false, false, false, false);
                movement.update(g.player, g.world, command, DT, result);
            }
        }
        assertTrue(g.player.abilities.flying(), "a double-tapped Space flies");
        for (int i = 0; i < 10; i++) {
            command.set(90f, false, false, false, false, false, false, true, false);
            movement.update(g.player, g.world, command, DT, result);
        }
        assertTrue(g.player.pos.y > y0, "Space rises");

        // Free building in the street: a plank placed without spending it, then broken without a drop.
        Vec3i street = RefugeWorldArena.neighbour(g).approaches().getFirst();
        SurvivorWorldArena.standAt(g, street);
        g.player.abilities.setFlying(false);
        Vec3i cell = street.offset(2, 0, 0);
        assertEquals(BlockType.AIR, g.world.getBlock(cell.x(), cell.y(), cell.z()), "an open street cell");
        g.player.inventory.set(0, new ItemStack(ItemType.PLANK, 2));
        g.player.hotbarSel = 0;
        assertTrue(g.placeSelectedBlockAt(cell.x(), cell.y(), cell.z()));
        assertEquals(2, g.player.selected().count, "placing spends nothing");
        g.targetHit = new Raycaster.Hit(cell.x(), cell.y(), cell.z(), -1, 0, 0, 2, BlockType.PLANK);
        g.mine(0.05f);
        assertEquals(BlockType.AIR, g.world.getBlock(cell.x(), cell.y(), cell.z()), "one press breaks it");
        assertEquals(2, g.player.inventory.count(ItemType.PLANK), "and drops nothing");

        // The world controls: the clock and the sky.
        g.creativeControls.setTime(TimePreset.NOON);
        assertEquals(12, g.time.hour(), "the clock is set");
        g.creativeControls.setWeather(WeatherSystem.Weather.STORM);
        assertEquals(WeatherSystem.Weather.STORM, g.weather.next);
        g.creativeControls.toggleDaylightFreeze();
        assertTrue(g.daylightFrozen());
        g.creativeControls.toggleWeatherLock();
        assertTrue(g.weatherLocked());
    }

    /** NEW FRONTIER from the title with the given keys, then Enter: the path a player takes. */
    static Game newFrontier(long seed, Path slot, int... keys) {
        Game g = new Game();
        g.sessionSeed = seed;
        g.frontend.savePath = slot;
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertTrue(g.newFrontierScreen.showsCity(), "NEW FRONTIER opens on the city");
        for (int key : keys) {
            press(g, key);
        }
        g.frontend.applyNewFrontier(press(g, GLFW_KEY_ENTER));
        assertEquals("Mapping the abandoned city...", g.frontend.loadingDetail());
        assertTrue(g.frontend.performLoad());
        assertEquals(Game.AppState.PLAYING, g.appState);
        return g;
    }

    private static NewFrontierScreen.Action press(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        NewFrontierScreen.Action action = g.newFrontierScreen.handleKeys(g.input);
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
        return action;
    }

    private static void route(Game g, HotkeyRouter router, int key) {
        g.input.onKey(key, GLFW_PRESS);
        router.update();
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
    }
}
