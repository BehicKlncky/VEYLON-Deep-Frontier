package com.veylon;

import com.veylon.entity.CombustionSource;
import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.save.SaveSlots;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanRefuges;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * Autosave (0.9.1 milestone 05, decision D-03) through the production frame step: each trigger
 * writes one ordinary save into the live world's slot, each unsafe moment holds it until the next
 * safe one, and nothing autosaves a session nobody permitted, a world in no slot or one whose
 * player turned autosave off. Every session saves into this test's own folder of slots.
 */
class AutosaveTest {

    private static final long SEED = 20_261_120L;
    private static final float DT = 1f / 20f;

    @TempDir
    Path dir;

    private SaveSlots slots() {
        return new SaveSlots(dir.resolve("saves"));
    }

    /** A wilderness world started in slot 2, as NEW FRONTIER starts one, standing on its feet. */
    private Game session(GameMode mode) {
        Game g = new Game();
        g.saves.useSlots(slots());
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR, mode);
        g.appState = Game.AppState.PLAYING;
        g.saves.autosave().permit(true);
        assertTrue(g.saves.startIn(2), "the new world's first save");
        play(g, 1f);
        assertTrue(g.player.onGround, "the survivor stands");
        assertEquals(0, g.saves.autosave().written());
        return g;
    }

    private static void play(Game g, float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            g.playStep(DT, DT);
        }
    }

    /** Plays until an autosave is written, at most {@code seconds}; returns the seconds it took. */
    private static float playUntilWritten(Game g, float seconds) {
        int before = g.saves.autosave().written();
        for (float t = 0; t < seconds; t += DT) {
            g.playStep(DT, DT);
            if (g.saves.autosave().written() > before) {
                return t;
            }
        }
        return fail("no autosave within " + seconds + " s; it waits on " + g.saves.autosave().blocker());
    }

    private static String lastLog(Game g) {
        List<String> all = g.eventLog.all();
        return all.isEmpty() ? "" : all.getLast();
    }

    private static void dueNow(Game g) {
        g.saves.autosave().stageElapsed(g.saves.autosave().settings().intervalSeconds());
    }

    // ------------------------------------------------------------------
    // The three triggers
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void theIntervalWritesOneAutosaveIntoTheActiveSlotAndKeepsTheSaveItReplaced(GameMode mode) throws Exception {
        Game g = session(mode);
        if (mode == GameMode.CREATIVE) {
            g.restoreCreativeControls(true, true, true); // the world controls an autosave must keep
        }
        SaveSlots slots = slots();
        byte[] first = Files.readAllBytes(slots.current(2));
        g.saves.autosave().stageElapsed(g.saves.autosave().settings().intervalSeconds() - 0.5);
        float waited = playUntilWritten(g, 2f);
        assertTrue(waited >= 0.5f - DT, "not before the interval: " + waited);
        assertEquals(Autosave.savedLine(2), lastLog(g).substring(lastLog(g).indexOf(']') + 2));
        assertArrayEquals(first, Files.readAllBytes(slots.backup(2, 1)), "the save it replaced is the newest backup");
        assertFalse(Files.exists(slots.current(1)) || Files.exists(slots.current(3)), "only the active slot");
        play(g, 3f);
        assertEquals(1, g.saves.autosave().written(), "the interval starts again after the autosave");

        // An autosave is an ordinary save: the mode, its mark and Creative's world controls come back.
        Game loaded = new Game();
        loaded.saves.useSlots(slots);
        assertTrue(loaded.saves.load(2).loaded());
        assertEquals(mode, loaded.gameMode());
        assertEquals(mode == GameMode.CREATIVE, loaded.creativeMarked());
        assertEquals(mode == GameMode.CREATIVE, loaded.daylightFrozen());
        assertEquals(mode == GameMode.CREATIVE, loaded.weatherLocked());
        assertEquals(mode == GameMode.CREATIVE, loaded.spawningPaused());
        assertTrue(loaded.saves.bound(), "the loaded world belongs to its slot");
    }

    @ParameterizedTest
    @EnumSource(value = GameMode.class, names = {"SURVIVAL", "CREATIVE"})
    void wakingFromSleepAutosavesOnceTheSleeperIsUp(GameMode mode) throws Exception {
        Game g = session(mode);
        g.entities.creatures.clear();
        g.time.advanceToNextHour(23);
        g.player.fatigue = 90;
        g.startSleep(false);
        assertTrue(g.sleeping, "asleep: " + lastLog(g));
        for (int i = 0; i < 20_000 && g.sleeping; i++) {
            g.playStep(DT, DT);
            assertEquals(0, g.saves.autosave().written(), "nothing is saved in the middle of the night");
        }
        assertFalse(g.sleeping, "the sleeper woke");
        assertTrue(lastLog(g).contains("You wake after"), lastLog(g));
        playUntilWritten(g, 1f);
        assertTrue(lastLog(g).endsWith(Autosave.savedLine(2)), lastLog(g));
        assertEquals(g.time.totalMinutes, savedClock(), 1.0, "the morning is what the slot holds");
        play(g, 2f);
        assertEquals(1, g.saves.autosave().written(), "one waking, one autosave");
    }

    /** The clock the slot's save holds, read without building a world. */
    private double savedClock() {
        return com.veylon.save.SaveSummary.inspect(slots().current(2)).summary().clockMinutes();
    }

    @Test
    void wakingEarlyWithEscapeAsksForTheSameAutosave() {
        Game g = session(GameMode.SURVIVAL);
        g.entities.creatures.clear();
        g.time.advanceToNextHour(23);
        g.player.fatigue = 90;
        g.startSleep(false);
        play(g, 0.5f);
        assertTrue(g.sleeping);
        g.input.onKey(GLFW_KEY_ESCAPE, GLFW_PRESS);
        new HotkeyRouter(g).update();
        g.input.onKey(GLFW_KEY_ESCAPE, GLFW_RELEASE);
        g.input.endFrame();
        assertFalse(g.sleeping);
        assertTrue(lastLog(g).endsWith("You wake up early."), lastLog(g));
        playUntilWritten(g, 1f);
        assertTrue(lastLog(g).endsWith(Autosave.savedLine(2)), lastLog(g));
    }

    @Test
    void claimingARefugeAtABedAutosavesOnce() {
        Game g = RefugeWorldArena.session(GameMode.SURVIVAL);
        g.saves.useSlots(slots());
        g.saves.autosave().permit(true);
        assertTrue(g.saves.startIn(1));
        BuildingPlan flat = RefugeWorldArena.neighbour(g);
        BuildingPlan.Fixture bed = RefugeWorldArena.fixture(flat, 1, f -> RefugeSystem.isBed(f.block()));
        RefugeWorldArena.standAt(g, bed.target());
        play(g, 0.5f);
        g.player.crouching = true;
        assertTrue(g.refuges.atBed(bed.position()), "a crouched press on the bed");
        g.player.crouching = false;
        assertEquals(2, g.world.urban.refuges.size(), "claimed: " + lastLog(g));
        assertEquals(Autosave.Trigger.CLAIM, g.saves.autosave().due());
        playUntilWritten(g, 2f);
        assertTrue(lastLog(g).endsWith(Autosave.savedLine(1)), lastLog(g));
        play(g, 1f);
        assertEquals(1, g.saves.autosave().written());

        Game loaded = new Game();
        loaded.saves.useSlots(slots());
        assertTrue(loaded.saves.load(1).loaded());
        UrbanRefuges refuges = loaded.world.urban.refuges;
        assertEquals(2, refuges.size(), "the slot keeps the claim");
        assertEquals(g.world.urban.refuges.homeFloor(), refuges.homeFloor());
    }

    // ------------------------------------------------------------------
    // The gate: an unsafe moment holds the autosave until the next safe one
    // ------------------------------------------------------------------

    /** Holds a due autosave with an unsafe state for two seconds, then writes it once that is undone. */
    private void heldUntilUndone(Autosave.Blocker expected, Consumer<Game> unsafe, Consumer<Game> undo) {
        Game g = session(GameMode.SURVIVAL);
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        dueNow(g);
        unsafe.accept(g);
        Autosave autosave = g.saves.autosave();
        for (int i = 0; i < 40; i++) {
            autosave.frame(DT);
        }
        assertEquals(0, autosave.written(), expected + " holds the autosave");
        assertFalse(autosave.announcing(), "nothing is announced while it waits");
        assertEquals(Autosave.Trigger.TIMER, autosave.due(), "still due, not dropped");
        if (g.simulates()) {
            assertEquals(expected, autosave.blocker());
        }
        undo.accept(g);
        for (int i = 0; i < 40 && autosave.written() == 0; i++) {
            autosave.frame(DT);
        }
        assertEquals(1, autosave.written(), "written at the first safe moment after " + expected);
    }

    @Test
    void deathAndTheDeathCardHoldIt() {
        heldUntilUndone(Autosave.Blocker.DEAD, g -> g.player.dead = true, g -> g.player.dead = false);
        heldUntilUndone(Autosave.Blocker.DEAD, g -> g.appState = Game.AppState.DEATH,
                g -> g.appState = Game.AppState.PLAYING);
    }

    @Test
    void anEndCardHoldsIt() {
        heldUntilUndone(Autosave.Blocker.END_CARD, g -> g.appState = Game.AppState.VICTORY,
                g -> g.appState = Game.AppState.PLAYING);
    }

    @Test
    void aPausingScreenOrThePauseKeyHoldsIt() {
        heldUntilUndone(Autosave.Blocker.PAUSED, g -> g.uiMode = Game.UiMode.PAUSE, g -> g.uiMode = Game.UiMode.NONE);
        heldUntilUndone(Autosave.Blocker.PAUSED, g -> g.simPaused = true, g -> g.simPaused = false);
    }

    @Test
    void sleepHoldsIt() {
        heldUntilUndone(Autosave.Blocker.SLEEPING, g -> g.sleeping = true, g -> g.sleeping = false);
    }

    @Test
    void aScreenOverPlayHoldsIt() {
        heldUntilUndone(Autosave.Blocker.SCREEN_OPEN, g -> g.uiMode = Game.UiMode.INVENTORY,
                g -> g.uiMode = Game.UiMode.NONE);
        heldUntilUndone(Autosave.Blocker.SCREEN_OPEN, g -> g.uiMode = Game.UiMode.NPC,
                g -> g.uiMode = Game.UiMode.NONE);
    }

    @Test
    void burningHoldsIt() {
        heldUntilUndone(Autosave.Blocker.BURNING,
                g -> assertTrue(g.combustion.ignite(g, g.player, CombustionSource.DIRECT_HIT, 1f, false, 1,
                        g.player.pos.x, g.player.pos.y + 1f, g.player.pos.z)),
                g -> assertTrue(g.combustion.extinguish(g.player)));
    }

    @Test
    void beingOffTheGroundHoldsIt() {
        heldUntilUndone(Autosave.Blocker.AIRBORNE, g -> g.player.onGround = false, g -> g.player.onGround = true);
    }

    @Test
    void aCreativeFlightHoldsIt() {
        Game g = session(GameMode.CREATIVE);
        dueNow(g);
        g.player.abilities.setFlying(true);
        for (int i = 0; i < 40; i++) {
            g.saves.autosave().frame(DT);
        }
        assertEquals(Autosave.Blocker.AIRBORNE, g.saves.autosave().blocker());
        assertEquals(0, g.saves.autosave().written());
        g.player.abilities.setFlying(false);
        for (int i = 0; i < 40 && g.saves.autosave().written() == 0; i++) {
            g.saves.autosave().frame(DT);
        }
        assertEquals(1, g.saves.autosave().written(), "landed, then saved");
    }

    @Test
    void aTumblingBodyHoldsItSoNoSaveSettlesItMidFall() {
        heldUntilUndone(Autosave.Blocker.BODIES_FALLING, g -> {
            Creature deer = g.entities.spawnCreature(g.world, Creature.CreatureType.DEER,
                    g.player.pos.x + 6, g.player.pos.y, g.player.pos.z);
            g.ragdolls.spawn(g, deer);
            g.entities.creatures.remove(deer);
        }, g -> g.ragdolls.settleAll(g));
    }

    @Test
    void aPursuitHoldsIt() {
        heldUntilUndone(Autosave.Blocker.PURSUED, g -> {
            Creature wolf = g.entities.spawnCreature(g.world, Creature.CreatureType.WOLF,
                    g.player.pos.x + 14, g.player.pos.y, g.player.pos.z);
            wolf.state = Creature.CreatureState.STALK;
        }, g -> g.entities.creatures.clear());
    }

    @Test
    void aHitHoldsItForTheGracePeriodAndNotLonger() {
        Game g = session(GameMode.SURVIVAL);
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        dueNow(g);
        Autosave autosave = g.saves.autosave();
        g.player.damageFlash = 1f;
        autosave.frame(DT);
        g.player.damageFlash = 0f;
        float held = 0;
        while (autosave.written() == 0 && held < 30) {
            autosave.frame(DT);
            held += DT;
        }
        assertEquals(1, autosave.written());
        assertTrue(held >= Autosave.GRACE_SECONDS - DT, "held through the grace: " + held);
        assertTrue(held <= Autosave.GRACE_SECONDS + Autosave.LEAD_SECONDS + 3 * DT, "and no longer: " + held);
    }

    @Test
    void aTimedSearchHoldsIt() {
        Game g = RefugeWorldArena.session(GameMode.SURVIVAL);
        g.saves.useSlots(slots());
        g.saves.autosave().permit(true);
        assertTrue(g.saves.startIn(1));
        BuildingPlan flat = RefugeWorldArena.neighbour(g);
        for (BuildingPlan.Fixture f : flat.fixtures()) {
            if (f.kind() != BuildingPlan.FixtureKind.CONTAINER) { continue; }
            RefugeWorldArena.standAt(g, f.target());
            if (g.openCrateAt(f.position()) && g.crateSearching()) {
                g.uiMode = Game.UiMode.CRATE;
                assertEquals(Autosave.Blocker.SEARCHING, g.saves.autosave().blocker());
                g.closeScreens();
                assertNotEquals(Autosave.Blocker.SEARCHING, g.saves.autosave().blocker());
                return;
            }
            g.closeScreens();
        }
        fail("no container of the abandoned flat needed a search");
    }

    // ------------------------------------------------------------------
    // Off, unpermitted, in no slot, failed
    // ------------------------------------------------------------------

    @Test
    void turningAutosaveOffStopsAllThreeTriggers() throws Exception {
        Game g = session(GameMode.SURVIVAL);
        byte[] first = Files.readAllBytes(slots().current(2));
        g.saves.autosave().settings().enabled = false;
        g.saves.autosave().request(Autosave.Trigger.WAKE);
        g.saves.autosave().request(Autosave.Trigger.CLAIM);
        dueNow(g);
        play(g, 2f);
        assertEquals(0, g.saves.autosave().written());
        assertEquals(Autosave.Trigger.TIMER, g.saves.autosave().due(),
                "no wake or claim waits for autosave to be turned back on; only the interval has passed");
        assertArrayEquals(first, Files.readAllBytes(slots().current(2)), "the slot is as it was");
        assertFalse(Files.exists(slots().backup(2, 1)));
    }

    @Test
    void aSessionNobodyPermittedNeverAutosaves() {
        Game g = session(GameMode.SURVIVAL);
        g.saves.autosave().permit(false);
        g.saves.autosave().request(Autosave.Trigger.WAKE);
        dueNow(g);
        play(g, 2f);
        assertEquals(0, g.saves.autosave().written());
        assertFalse(Files.exists(slots().backup(2, 1)));
        assertFalse(new Game().saves.autosave().permitted(), "a game nobody ran is not permitted");
    }

    @Test
    void onlyAPlayersSessionAutosavesUnlessAutomationAsks() {
        assertTrue(Autosave.permitted(false, null), "a player's session");
        assertTrue(Autosave.permitted(false, " "), "a blank switch is no switch");
        assertFalse(Autosave.permitted(true, null), "a scene, capture, route or the smoke");
        assertTrue(Autosave.permitted(true, "on"));
        assertTrue(Autosave.permitted(true, " ON "));
        assertFalse(Autosave.permitted(false, "off"));
        assertThrows(IllegalArgumentException.class, () -> Autosave.permitted(true, "yes"));
    }

    @Test
    void aWorldInNoSlotIsNeverAutosaved() {
        Game g = new Game();
        g.saves.useSlots(slots());
        g.newWorld(SEED, true, World.WILDERNESS_NEW_WORLD_GENERATOR);
        g.appState = Game.AppState.PLAYING;
        g.saves.autosave().permit(true);
        assertFalse(g.saves.bound());
        dueNow(g);
        play(g, 2f);
        assertEquals(0, g.saves.autosave().written());
        assertFalse(Files.exists(slots().directory()), "no slot was written: " + slots().directory());
    }

    @Test
    void aSaveWithF5StartsTheIntervalAgain() {
        Game g = session(GameMode.SURVIVAL);
        g.saves.autosave().stageElapsed(g.saves.autosave().settings().intervalSeconds() - 0.5);
        assertTrue(g.saves.saveActive());
        assertEquals(0, g.saves.autosave().sinceSave());
        play(g, 2f);
        assertEquals(0, g.saves.autosave().written());
    }

    @Test
    void aFailedAutosaveSaysSoAndTriesAgainAfterTheNextInterval() throws Exception {
        Game g = session(GameMode.SURVIVAL);
        Path blocked = dir.resolve("blocked");
        Files.writeString(blocked, "a file where the slots' folder should be");
        g.saves.useSlots(new SaveSlots(blocked.resolve("saves")));
        dueNow(g);
        play(g, 1f);
        assertTrue(lastLog(g).endsWith(Autosave.failedLine(2)), lastLog(g));
        assertEquals(0, g.saves.autosave().written());
        int lines = g.eventLog.all().size();
        play(g, 2f);
        assertEquals(lines, g.eventLog.all().size(), "no retry every frame");
        assertNull(g.saves.autosave().due());
    }

    // ------------------------------------------------------------------
    // The interval and the HUD
    // ------------------------------------------------------------------

    @Test
    void theHudAnnouncesTheAutosaveBeforeTheWriteAndNotAfter() {
        Game g = session(GameMode.SURVIVAL);
        Autosave autosave = g.saves.autosave();
        dueNow(g);
        int announced = 0;
        while (autosave.written() == 0 && announced < 100) {
            g.playStep(DT, DT);
            if (autosave.announcing()) {
                announced++;
                assertEquals(0, autosave.written(), "announced before it is written");
            }
        }
        assertEquals(1, autosave.written());
        assertTrue(announced * DT >= Autosave.LEAD_SECONDS - DT, "shown for " + announced + " frames");
        assertFalse(autosave.announcing(), "gone once written");
    }

    @Test
    void onlyFramesThatAdvanceTheWorldCountTowardTheInterval() {
        Game g = session(GameMode.SURVIVAL);
        Autosave autosave = g.saves.autosave();
        double before = autosave.sinceSave();
        play(g, 1f);
        assertEquals(before + 1, autosave.sinceSave(), 2 * DT, "a second of play");
        g.uiMode = Game.UiMode.PAUSE;
        play(g, 5f);
        assertEquals(before + 1, autosave.sinceSave(), 2 * DT, "the pause menu adds nothing");
        g.uiMode = Game.UiMode.NONE;
        autosave.settings().setIntervalMinutes(5);
        assertEquals(300.0, autosave.settings().intervalSeconds());
    }

    @Test
    void aNewOrLoadedWorldForgetsWhatWasAskedFor() {
        Game g = session(GameMode.SURVIVAL);
        g.saves.autosave().request(Autosave.Trigger.WAKE);
        assertEquals(Autosave.Trigger.WAKE, g.saves.autosave().due());
        assertTrue(g.saves.openActive().loaded(), "F9");
        assertNull(g.saves.autosave().due());
        assertTrue(g.saves.bound());
        assertEquals(0, g.saves.autosave().sinceSave());
    }
}
