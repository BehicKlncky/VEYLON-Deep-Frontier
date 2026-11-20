package com.veylon;

import com.veylon.save.SaveSlots;
import com.veylon.ui.NewFrontierScreen;
import com.veylon.ui.SlotPickerScreen;
import com.veylon.ui.TitleScreen;
import com.veylon.world.Chunk;
import com.veylon.world.WorldSeed;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/**
 * 0.9.1 milestone 06's seeds (D-05, D-33) through the frontend controller's seams, into a folder of
 * slots of the test's own: NEW FRONTIER shows the seed its world will use, takes a typed number or
 * words, rerolls on R and builds the world of that seed; nothing chosen on one opening carries into
 * the next; and a session that names its seed, as {@code VEYLON_SEED} does, opens on it every time
 * and rerolls the same way every run.
 */
class NewFrontierSeedTest {

    private static final long SEED = 20_261_120L;

    @TempDir
    Path directory;

    private SaveSlots slots;

    @BeforeEach
    void aFolderOfSlots() {
        slots = new SaveSlots(directory.resolve("slots"));
    }

    @Test
    void twoNewWorldsWithTheSameTypedSeedAreTheSameCity() {
        Game first = finish(typedOn(title(), "deep frontier"), 1);
        Game second = finish(typedOn(title(), "deep frontier"), 2);
        assertEquals(WorldSeed.of("deep frontier"), first.world.seed, "the words' seed builds the world");
        assertEquals(first.world.seed, second.world.seed);
        assertTrue(first.world.isUrban());
        assertEquals(city(first), city(second), "the same streets, buildings and refuge");
        assertEquals(SaveAuditQaScene.durable(first), SaveAuditQaScene.durable(second));

        Game number = finish(typedOn(title(), "  -20261120 "), 3);
        assertEquals(-20_261_120L, number.world.seed, "a typed number is the seed itself");
    }

    @Test
    void aRerollOffersAnotherSeedAndBuildsAnotherCity() {
        Game offered = finish(opened(SEED), 1);
        assertEquals(SEED, offered.world.seed, "the offered seed builds the world when nothing else is chosen");

        Game g = opened(SEED);
        g.frontend.applyNewFrontier(press(g, GLFW_KEY_R));
        long rerolled = g.newFrontierScreen.seed();
        assertNotEquals(SEED, rerolled, "R offers another seed");
        assertEquals(Long.toString(rerolled), g.newFrontierScreen.fieldText(), "and shows it");
        assertEquals(NewFrontierScreen.OFFERED_NOTE, g.newFrontierScreen.seedNote());
        Game rolled = finish(g, 2);
        assertEquals(rerolled, rolled.world.seed);
        assertNotEquals(city(offered), city(rolled), "another seed, another city");

        Game typedThenRolled = typedOn(opened(SEED), "deep frontier");
        typedThenRolled.frontend.applyNewFrontier(press(typedThenRolled, GLFW_KEY_R));
        assertEquals("", typedThenRolled.newFrontierScreen.typedSeed(), "a reroll replaces what was typed");
        assertNotEquals(WorldSeed.of("deep frontier"), typedThenRolled.newFrontierScreen.seed());
    }

    @Test
    void aSessionThatNamesItsSeedOpensOnItEveryTimeAndRerollsAlikeEveryRun() {
        Game a = title(), b = title();
        a.frontend.seeds = NewWorldSeeds.fixed(SEED); // what VEYLON_SEED does (QaHarness.configuredSeed)
        b.frontend.seeds = NewWorldSeeds.fixed(SEED);
        for (int opening = 1; opening <= 3; opening++) {
            for (Game g : new Game[]{a, b}) {
                g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
                assertEquals(SEED, g.newFrontierScreen.seed(), "opening " + opening + " offers the named seed");
                assertEquals(Long.toString(SEED), g.newFrontierScreen.fieldText());
                g.frontend.applyNewFrontier(press(g, GLFW_KEY_R));
            }
            assertEquals(a.newFrontierScreen.seed(), b.newFrontierScreen.seed(), "rerolls are the same in every run");
            a.frontend.applyNewFrontier(press(a, GLFW_KEY_ESCAPE));
            b.frontend.applyNewFrontier(press(b, GLFW_KEY_ESCAPE));
            assertEquals(Game.AppState.TITLE, a.appState);
        }
        Game named = title();
        named.frontend.seeds = NewWorldSeeds.fixed(SEED);
        assertEquals(SEED, finish(opened(named), 1).world.seed, "the named seed wins over a random roll");
        Game typed = title();
        typed.frontend.seeds = NewWorldSeeds.fixed(SEED);
        assertEquals(WorldSeed.of("deep frontier"), finish(typedOn(opened(typed), "deep frontier"), 2).world.seed,
                "what a person types there is still the seed of that world");
    }

    @Test
    void withoutANamedSeedEveryOpeningOffersANewOneAndNothingCarriesOver() {
        Game g = title();
        assertFalse(g.frontend.seeds.isFixed(), "a player's session draws its seeds");
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        long first = g.newFrontierScreen.seed();
        typedOn(g, "deep frontier");
        assertEquals(WorldSeed.of("deep frontier"), g.newFrontierScreen.seed());
        g.frontend.applyNewFrontier(press(g, GLFW_KEY_ESCAPE));
        assertEquals(Game.AppState.TITLE, g.appState, "Escape outside the field returns to the title");
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertEquals("", g.newFrontierScreen.typedSeed(), "a typed seed does not carry into the next opening");
        long second = g.newFrontierScreen.seed();
        assertNotEquals(first, second, "Game.sessionSeed no longer fixes every new world of a process");
        assertNotEquals(WorldSeed.of("deep frontier"), second);

        Game started = finish(g, 1);
        assertEquals(second, started.world.seed);
        started.frontend.returnToTitle();
        started.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertNotEquals(second, started.newFrontierScreen.seed(), "the next new world of the process has its own");
    }

    @Test
    void whileTheSeedIsTypedEveryKeyGoesToTheField() {
        Game g = opened(SEED);
        NewFrontierScreen screen = g.newFrontierScreen;
        assertEquals(NewFrontierScreen.Action.NONE, type(g, GLFW_KEY_E, 'e'));
        assertTrue(screen.typingSeed(), "E starts typing");
        assertEquals("", screen.typedSeed(), "without typing an e");
        assertEquals("", screen.fieldText(), "the field waits for the seed, with its placeholder");
        assertEquals(SEED, screen.seed(), "and the offered seed stands until something is typed");

        for (char letter : "scwr".toCharArray()) {
            assertEquals(NewFrontierScreen.Action.NONE, type(g, GLFW_KEY_A + (letter - 'a'), letter));
        }
        assertEquals("scwr", screen.typedSeed(), "S, C, W and R type letters");
        assertEquals(com.veylon.entity.GameMode.SURVIVAL, screen.selectedMode(), "instead of choosing the mode");
        assertTrue(screen.showsCity(), "or the world");
        assertEquals(WorldSeed.of("scwr"), screen.seed());
        assertEquals("These words make seed " + WorldSeed.of("scwr") + ".", screen.seedNote());

        type(g, GLFW_KEY_BACKSPACE, 0);
        assertEquals("scw", screen.typedSeed(), "Backspace deletes one");
        assertEquals(NewFrontierScreen.Action.NONE, type(g, GLFW_KEY_ENTER, 0), "Enter finishes the seed");
        assertFalse(screen.typingSeed());
        assertEquals("scw", screen.fieldText(), "which the field keeps showing");
        assertEquals(NewFrontierScreen.Action.START, type(g, GLFW_KEY_ENTER, 0), "the next Enter goes on");

        type(g, GLFW_KEY_E, 'e');
        for (int i = 0; i < 3; i++) {
            type(g, GLFW_KEY_BACKSPACE, 0);
        }
        type(g, GLFW_KEY_1, '1');
        type(g, GLFW_KEY_2, '2');
        assertEquals(NewFrontierScreen.Action.NONE, type(g, GLFW_KEY_ESCAPE, 0), "Escape finishes it too");
        assertEquals(12L, screen.seed());
        assertEquals(NewFrontierScreen.NUMBER_NOTE, screen.seedNote());

        type(g, GLFW_KEY_E, 'e');
        for (int i = 0; i < NewFrontierScreen.MAX_SEED_CODE_POINTS + 8; i++) {
            type(g, GLFW_KEY_9, '9');
        }
        assertEquals(NewFrontierScreen.MAX_SEED_CODE_POINTS, screen.typedSeed().length(), "the field is bounded");
        g.input.onTyped('\t');
        g.input.onTyped(0x7F);
        g.input.onTyped('x');
        g.input.onKey(GLFW_KEY_BACKSPACE, GLFW_PRESS);
        screen.handleKeys(g.input, codePoint -> codePoint != 'x');
        g.input.endFrame();
        g.input.onKey(GLFW_KEY_BACKSPACE, GLFW_RELEASE);
        assertEquals(NewFrontierScreen.MAX_SEED_CODE_POINTS - 1, screen.typedSeed().length(),
                "control characters and characters the font cannot draw are never typed");
        for (int i = 0; i < NewFrontierScreen.MAX_SEED_CODE_POINTS; i++) {
            type(g, GLFW_KEY_BACKSPACE, 0);
        }
        g.input.onTyped(0x1F600);
        screen.handleKeys(g.input, codePoint -> true);
        g.input.endFrame();
        assertEquals(2, screen.typedSeed().length(), "a character beyond the basic plane is one character");
        type(g, GLFW_KEY_BACKSPACE, 0);
        assertEquals("", screen.typedSeed(), "and Backspace deletes it whole");
        type(g, GLFW_KEY_ENTER, 0);
        assertEquals(SEED, screen.seed(), "with nothing typed the offered seed stands again");
        assertEquals(Long.toString(SEED), screen.fieldText());
    }

    @Test
    void theSlotPickersBackKeepsTheChosenSeed() {
        Game g = typedOn(opened(SEED), "deep frontier");
        g.frontend.applyNewFrontier(press(g, GLFW_KEY_ENTER));
        assertEquals(Game.AppState.TITLE_SLOTS, g.appState);
        g.frontend.applySlots(SlotPickerScreen.Action.BACK);
        assertEquals(Game.AppState.TITLE_NEW_WORLD, g.appState);
        assertEquals("deep frontier", g.newFrontierScreen.typedSeed(), "going back to NEW FRONTIER keeps it");
        assertEquals(WorldSeed.of("deep frontier"), finish(g, 1).world.seed);
    }

    // ------------------------------------------------------------------
    // The player's paths
    // ------------------------------------------------------------------

    private Game title() {
        Game g = new Game();
        g.saves.useSlots(slots);
        return g;
    }

    /** A title whose session names {@code seed}, with NEW FRONTIER open. */
    private Game opened(long seed) {
        Game g = title();
        g.frontend.seeds = NewWorldSeeds.fixed(seed);
        return opened(g);
    }

    private static Game opened(Game g) {
        g.frontend.applyTitle(TitleScreen.Action.NEW_GAME);
        assertEquals(Game.AppState.TITLE_NEW_WORLD, g.appState);
        return g;
    }

    /** NEW FRONTIER opened if it is not, then E, {@code text} typed and Enter, as a player types a seed. */
    private static Game typedOn(Game g, String text) {
        if (g.appState != Game.AppState.TITLE_NEW_WORLD) {
            opened(g);
        }
        type(g, GLFW_KEY_E, 'e');
        text.codePoints().forEach(g.input::onTyped);
        g.newFrontierScreen.handleKeys(g.input);
        g.input.endFrame();
        assertEquals(NewFrontierScreen.Action.NONE, type(g, GLFW_KEY_ENTER, 0));
        assertEquals(text, g.newFrontierScreen.fieldText(), "the field shows what was typed");
        return g;
    }

    /** Enter on NEW FRONTIER, {@code slot} and Enter on the picker, replacing what it holds: the new world. */
    private Game finish(Game g, int slot) {
        g.frontend.applyNewFrontier(press(g, GLFW_KEY_ENTER));
        assertEquals(Game.AppState.TITLE_SLOTS, g.appState);
        pick(g, GLFW_KEY_0 + slot);
        SlotPickerScreen.Action action = pick(g, GLFW_KEY_ENTER);
        if (g.frontend.slotPicker.asking() == SlotPickerScreen.Ask.REPLACE) {
            action = pick(g, GLFW_KEY_Y);
        }
        g.frontend.applySlots(action);
        assertTrue(g.frontend.performLoad(), "the new world starts");
        assertEquals(slot, g.saves.activeSlot());
        return g;
    }

    /** {@code key} pressed with the character it types, if any, on NEW FRONTIER. */
    private static NewFrontierScreen.Action type(Game g, int key, int typed) {
        g.input.onKey(key, GLFW_PRESS);
        if (typed != 0) {
            g.input.onTyped(typed);
        }
        NewFrontierScreen.Action action = g.newFrontierScreen.handleKeys(g.input);
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
        return action;
    }

    private static NewFrontierScreen.Action press(Game g, int key) {
        return type(g, key, 0);
    }

    private static SlotPickerScreen.Action pick(Game g, int key) {
        g.input.onKey(key, GLFW_PRESS);
        SlotPickerScreen.Action action = g.frontend.slotPicker.handleKeys(g.input);
        g.input.endFrame();
        g.input.onKey(key, GLFW_RELEASE);
        return action;
    }

    /** Every block of the chunks around the spawn, the refuge's anchor and the seed: the city as built. */
    private static long city(Game g) {
        int pcx = Math.floorDiv((int) Math.floor(g.player.pos.x), Chunk.SX);
        int pcz = Math.floorDiv((int) Math.floor(g.player.pos.z), Chunk.SZ);
        long hash = 1_125_899_906_842_597L;
        for (int cx = pcx - 2; cx <= pcx + 2; cx++) {
            for (int cz = pcz - 2; cz <= pcz + 2; cz++) {
                Chunk chunk = g.world.getChunk(cx, cz);
                assertNotNull(chunk, "the spawn's chunks were generated");
                for (int x = 0; x < Chunk.SX; x++) {
                    for (int y = 0; y < Chunk.SY; y++) {
                        for (int z = 0; z < Chunk.SZ; z++) {
                            hash = 31 * hash + chunk.get(x, y, z).ordinal();
                        }
                    }
                }
            }
        }
        return 31 * hash + (g.world.urban == null ? 0 : g.world.urban.refugeAnchor.hashCode());
    }
}
