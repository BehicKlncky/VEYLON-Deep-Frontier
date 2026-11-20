package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.entity.Npc;
import com.veylon.save.SaveSeal;
import com.veylon.save.SaveSlots;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.ui.NewFrontierScreen;
import com.veylon.ui.SlotPickerScreen;
import com.veylon.ui.TitleScreen;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * The way back to the title (0.9.1 milestone 06), through the seams {@code GameLoopIntegrationTest}
 * uses: it releases everything the world leaves behind, and a world started or loaded afterwards is
 * exactly the world a fresh launch starts or loads.
 */
class ReturnToTitleTest {

    private static final long CITY_SEED = 20_261_120L;
    private static final long WILD_SEED = 20_261_121L;

    @TempDir
    Path directory;

    @Test
    void theTitleHoldsNothingOfTheWorldItLeft() {
        Game g = started(directory.resolve("left"), CITY_SEED, 1);
        dirty(g);

        g.frontend.returnToTitle();

        assertEquals(Game.AppState.TITLE, g.appState);
        assertNull(g.world, "the world is let go");
        assertNull(g.player);
        assertTrue(g.entities.creatures.isEmpty() && g.entities.npcs.isEmpty() && g.entities.carcasses.isEmpty()
                && g.entities.corpses.isEmpty() && g.entities.tracks.isEmpty(), "and everyone in it");
        assertEquals(0, g.fire.count(), "no block still burns");
        assertEquals(0, g.liquidFire.count(), "no pool still burns");
        assertEquals(0, g.projectiles.liveCount(), "no shot is still in the air");
        assertEquals(0, g.fragments.liveCount(), "no piece is still flying");
        assertEquals(0, g.fragments.settledCount(), "or lying anywhere");
        assertEquals(0, g.ragdolls.liveCount());
        assertEquals(0, g.particles.count);
        assertEquals(0f, g.renderer.pendingShake(), "the camera shake of the last blast is gone");
        assertEquals(0, g.audio.pendingWeatherSounds());
        assertFalse(g.saves.bound(), "no world belongs to a slot");
        assertEquals(0, g.saves.playSeconds());
        assertTrue(g.eventLog.all().isEmpty(), "its log is gone");
        assertEquals(Game.UiMode.NONE, g.uiMode);
        assertFalse(g.simPaused || g.simPanelShown || g.sleeping);
        assertNull(g.activeNpc);
        assertNull(g.openCrate);
        assertNull(g.targetHit);
        assertEquals(0f, g.miningProgress);
        assertEquals("", g.creativeCatalogScreen.query());
        assertEquals("", g.titleScreen.notice());
    }

    @Test
    void aWorldStartedAfterTheTitleIsTheWorldAFreshLaunchStarts() throws IOException {
        Path returnedSlots = directory.resolve("returned"), freshSlots = directory.resolve("fresh");
        Game returned = started(returnedSlots, CITY_SEED, 1);
        dirty(returned);
        returned.frontend.returnToTitle();
        start(returned, WILD_SEED, 2, GLFW_KEY_W);
        Game fresh = started(freshSlots, WILD_SEED, 2, GLFW_KEY_W);

        assertFalse(returned.world.isUrban(), "the wilderness after a city");
        assertEquals(SaveAuditQaScene.durable(fresh), SaveAuditQaScene.durable(returned), "the same durable state");
        assertArrayEquals(firstSave(fresh, 2), firstSave(returned, 2), "and the same first save");
        assertEquals(play(fresh), play(returned), "and the same play that follows");
    }

    @Test
    void aWorldLoadedAfterTheTitleIsTheWorldAFreshLaunchLoads() throws IOException {
        Path slots = directory.resolve("slots");
        Game returned = started(slots, WILD_SEED, 1, GLFW_KEY_W);
        returned.world.setBlock((int) returned.player.pos.x + 2, (int) returned.player.pos.y + 1,
                (int) returned.player.pos.z + 2, BlockType.PLANK, true);
        assertTrue(returned.saves.saveActive());
        start(returned, CITY_SEED, 2);
        dirty(returned);
        returned.frontend.returnToTitle();
        load(returned, 1);
        Game fresh = title(slots);
        load(fresh, 1);

        assertEquals(SaveAuditQaScene.durable(fresh), SaveAuditQaScene.durable(returned));
        assertEquals(play(fresh), play(returned), "the loaded world plays on as it would after a fresh launch");
    }

    // ------------------------------------------------------------------
    // The player's paths
    // ------------------------------------------------------------------

    private static Game title(Path slots) {
        Game g = new Game();
        g.saves.useSlots(new SaveSlots(slots));
        return g;
    }

    /** A new Game whose NEW FRONTIER starts the world of {@code seed}, with these keys, in {@code slot}. */
    private static Game started(Path slots, long seed, int slot, int... keys) {
        return start(title(slots), seed, slot, keys);
    }

    /** From the title: N, the seed typed, these keys, Enter, then {@code slot} and Enter on the picker. */
    private static Game start(Game g, long seed, int slot, int... keys) {
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        press(g, GLFW_KEY_E);
        Long.toString(seed).codePoints().forEach(g.input::onTyped);
        g.newFrontierScreen.handleKeys(g.input);
        g.input.endFrame();
        press(g, GLFW_KEY_ENTER);
        for (int key : keys) {
            press(g, key);
        }
        g.frontend.applyNewFrontier(press(g, GLFW_KEY_ENTER));
        pick(g, GLFW_KEY_0 + slot);
        SlotPickerScreen.Action action = pick(g, GLFW_KEY_ENTER);
        if (g.frontend.slotPicker.asking() == SlotPickerScreen.Ask.REPLACE) {
            action = pick(g, GLFW_KEY_Y);
        }
        g.frontend.applySlots(action);
        assertTrue(g.frontend.performLoad(), "the new world starts");
        assertEquals(seed, g.world.seed);
        return g;
    }

    /** LOAD FRONTIER, {@code slot} and Enter. */
    private static void load(Game g, int slot) {
        g.frontend.applyTitle(TitleScreen.Action.LOAD_GAME);
        pick(g, GLFW_KEY_0 + slot);
        g.frontend.applySlots(pick(g, GLFW_KEY_ENTER));
        assertTrue(g.frontend.performLoad(), "slot " + slot + " loads");
    }

    /**
     * Every kind of state a world leaves behind when the player walks away from it: fires, a burning
     * pool, a shot in the air, flying and settled pieces, a wounded survivor, wildlife, a speaker, open
     * screens, a paused simulation, a shake, the play time and a due autosave.
     */
    private static void dirty(Game g) {
        int fx = (int) g.player.pos.x + 3, fz = (int) g.player.pos.z + 3;
        int fy = (g.world.isUrban() ? g.world.urban.groundY(fx, fz) : g.world.surfaceHeight(fx, fz)) + 1;
        g.world.setBlock(fx, fy, fz, BlockType.LOG, true);
        g.fire.ignite(g, fx, fy, fz);
        g.liquidFire.spill(g, fx + 0.5f, fy + 1.5f, fz + 0.5f, 1, 0, true);
        g.projectiles.fire(g, g.player, true, g.player.pos.x, g.player.pos.y + 1.5f, g.player.pos.z, 0, 1, 0,
                com.veylon.combat.WeaponRegistry.byId("musket"), null);
        Npc blown = g.entities.spawnNpc(g.world, "Villager", g.player.pos.x + 3, g.player.pos.y, g.player.pos.z);
        g.entities.npcs.remove(blown);
        g.fragments.spawnFromNpc(g, blown, blown.pos.x + 1, blown.pos.y + 1, blown.pos.z, 2.6f);
        g.fragments.settleAll(g);
        g.fragments.spawnFromNpc(g, blown, blown.pos.x + 1, blown.pos.y + 1, blown.pos.z, 2.6f);
        g.activeNpc = g.entities.spawnNpc(g.world, "Speaker", g.player.pos.x + 2, g.player.pos.y, g.player.pos.z);
        g.entities.spawnCreature(g.world, Creature.CreatureType.WOLF, g.player.pos.x + 4, g.player.pos.y, g.player.pos.z);
        g.player.addAffliction(Affliction.BLEEDING, 30f);
        g.creativeCatalogScreen.search("iron");
        g.renderer.addShake(0.8f);
        g.saves.countPlay(120);
        g.saves.autosave().stageElapsed(10_000);
        g.log("Something happened in the old world.");
        g.uiMode = Game.UiMode.PAUSE;
        g.simPaused = true;
        g.simPanelShown = true;
        assertTrue(g.fire.count() > 0 && g.liquidFire.count() > 0 && g.projectiles.liveCount() > 0
                && g.fragments.liveCount() > 0 && g.fragments.settledCount() > 0, "precondition: the world is busy");
    }

    /** The first save a new world wrote into {@code slot}, without the summary that says when. */
    private static byte[] firstSave(Game g, int slot) throws IOException {
        return SaveSeal.withoutSummary(Files.readAllBytes(g.saves.slots().current(slot)));
    }

    /** A few simulated seconds of the world, fingerprinted where randomness and old state would show. */
    private static String play(Game g) {
        for (int i = 0; i < 60; i++) {
            g.fastTick(SimulationScheduler.FAST_DT);
        }
        for (int i = 0; i < 4; i++) {
            g.mediumTick(SimulationScheduler.MEDIUM_DT);
        }
        g.slowTick(SimulationScheduler.SLOW_DT);
        return String.join(" ", List.of(
                "weather=" + g.weather.current + "/" + g.weather.next + "/" + Math.round(g.weather.changeTimer * 100),
                "creatures=" + g.entities.creatureCount(), "npcs=" + g.entities.npcCount(),
                "fires=" + g.fire.count(), "ignitions=" + g.fire.totalIgnitions,
                "events=" + g.events.totalEventsTriggered + "/" + g.events.active.size(),
                "growth=" + g.plants.growthEvents, "water=" + g.water.cellsProcessed,
                "trust=" + Math.round(g.faction.trust * 100), "clock=" + g.time.totalMinutes,
                "health=" + g.player.health, "afflictions=" + g.player.afflictions.size(),
                "at=" + g.player.pos, "log=" + g.eventLog.all().size(), "shake=" + g.renderer.pendingShake(),
                "pieces=" + g.fragments.liveCount() + "/" + g.fragments.settledCount(),
                "corpses=" + g.entities.corpses.size()));
    }

    private static NewFrontierScreen.Action press(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        NewFrontierScreen.Action action = g.newFrontierScreen.handleKeys(g.input);
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
        return action;
    }

    private static SlotPickerScreen.Action pick(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        SlotPickerScreen.Action action = g.frontend.slotPicker.handleKeys(g.input);
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
        return action;
    }
}
