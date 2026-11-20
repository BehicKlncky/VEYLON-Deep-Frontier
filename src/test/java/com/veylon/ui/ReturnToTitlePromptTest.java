package com.veylon.ui;

import com.veylon.engine.Input;
import com.veylon.gfx.ResourceManager;
import com.veylon.save.SaveSlots;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.util.function.ToDoubleFunction;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * The question on the way back to the title, and the pause menu's keys that lead to it (0.9.1
 * milestone 06): what each key answers, what the lines say, and that every line fits where it is
 * drawn.
 */
class ReturnToTitlePromptTest {

    @Test
    void enterSavesAndReturnsNReturnsWithoutSavingAndEscapeStays() {
        ReturnToTitlePrompt prompt = new ReturnToTitlePrompt();
        Input input = new Input();
        assertEquals(ReturnToTitlePrompt.Action.SAVE_AND_RETURN, press(prompt, input, GLFW_KEY_ENTER));
        assertEquals(ReturnToTitlePrompt.Action.RETURN, press(prompt, input, GLFW_KEY_N));
        assertEquals(ReturnToTitlePrompt.Action.CANCEL, press(prompt, input, GLFW_KEY_ESCAPE));
        for (int key : new int[]{GLFW_KEY_Y, GLFW_KEY_S, GLFW_KEY_Q, GLFW_KEY_R, GLFW_KEY_F5, GLFW_KEY_SPACE}) {
            assertEquals(ReturnToTitlePrompt.Action.NONE, press(prompt, input, key), "no other key answers");
        }
        assertArrayEquals(new ReturnToTitlePrompt.Action[]{ReturnToTitlePrompt.Action.SAVE_AND_RETURN,
                ReturnToTitlePrompt.Action.RETURN, ReturnToTitlePrompt.Action.CANCEL}, ReturnToTitlePrompt.ACTIONS,
                "the buttons, the saving one first");
    }

    @Test
    void itSaysHowMuchPlayTheSlotIsBehindAndAFailureIsShownUntilItOpensAgain() {
        ReturnToTitlePrompt prompt = new ReturnToTitlePrompt();
        prompt.open(2, true, 30);
        assertEquals("Since slot 2 was last saved: under a minute of play.", prompt.unsavedLine());
        prompt.open(2, true, 3_900);
        assertEquals("Since slot 2 was last saved: 1 h 05 min of play.", prompt.unsavedLine());
        assertEquals("Returning without saving leaves slot 2 as it is.", prompt.consequenceLine());
        prompt.notice("Could not save to slot 2. The world is still open.");
        prompt.open(3, false, 0);
        assertEquals("", prompt.notice(), "an opening forgets the last failure");
        assertEquals("This world is in no slot yet: saving puts it in slot 3.", prompt.unsavedLine());
    }

    @Test
    void everyLineFitsThePromptAndThePauseMenu() {
        ToDoubleFunction<String> title = regular(2.15f), line = regular(1.15f), small = regular(1.05f),
                button = regular(1.28f), keys = regular(1.10f);
        assertTrue(title.applyAsDouble(ReturnToTitlePrompt.TITLE) <= ReturnToTitlePrompt.PANEL_W - 40);
        ReturnToTitlePrompt prompt = new ReturnToTitlePrompt();
        for (int slot = 1; slot <= SaveSlots.COUNT; slot++) {
            for (boolean bound : new boolean[]{true, false}) {
                prompt.open(slot, bound, 999 * 3_600 + 59 * 60);
                assertTrue(line.applyAsDouble(prompt.unsavedLine()) <= ReturnToTitlePrompt.PANEL_W - 40, prompt.unsavedLine());
                assertTrue(small.applyAsDouble(prompt.consequenceLine()) <= ReturnToTitlePrompt.PANEL_W - 40);
            }
            String failed = "Could not save to slot " + slot + ". The world is still open.";
            assertTrue(regular(1.1f).applyAsDouble(failed) <= ReturnToTitlePrompt.PANEL_W - 40, failed);
        }
        for (String label : ReturnToTitlePrompt.BUTTONS) {
            assertTrue(button.applyAsDouble(label) <= ReturnToTitlePrompt.BUTTON_W - 16, label);
        }
        assertTrue(small.applyAsDouble(ReturnToTitlePrompt.KEYS) <= ReturnToTitlePrompt.PANEL_W - 40);

        for (boolean creative : new boolean[]{true, false}) {
            String[] lines = PauseMenu.keyLines(creative, SaveSlots.COUNT);
            assertEquals(3, lines.length);
            for (String l : lines) {
                assertTrue(keys.applyAsDouble(l) <= 600 - 40, "fits the 600-wide pause menu: " + l);
            }
            assertEquals(creative, lines[0].contains("[T] World controls"), "world controls only in Creative");
            assertTrue(lines[2].contains("[R] Return to title") && lines[2].contains("[Q] Quit"),
                    "Q keeps quitting the game beside the way back to the title");
        }
    }

    private static ReturnToTitlePrompt.Action press(ReturnToTitlePrompt prompt, Input input, int key) {
        input.onKey(key, GLFW_PRESS);
        ReturnToTitlePrompt.Action action = prompt.handleKeys(input);
        input.endFrame();
        input.onKey(key, GLFW_RELEASE);
        return action;
    }

    /**
     * The game's regular face. stb keeps a pointer into the buffer it was given, so the buffer is
     * held for as long as the class is loaded.
     */
    private static final ByteBuffer FONT = ResourceManager.readBuffer("fonts/SourceSans3-Regular.ttf");
    private static final STBTTFontinfo FACE = STBTTFontinfo.malloc();

    static {
        if (!stbtt_InitFont(FACE, FONT)) {
            throw new IllegalStateException("the bundled regular face does not load");
        }
    }

    private static ToDoubleFunction<String> regular(float scale) {
        float px = stbtt_ScaleForPixelHeight(FACE, 12 * scale);
        return text -> {
            int[] advance = new int[1], bearing = new int[1];
            double width = 0;
            int previous = -1;
            for (int cp : text.codePoints().toArray()) {
                stbtt_GetCodepointHMetrics(FACE, cp, advance, bearing);
                width += advance[0] * px;
                if (previous >= 0) width += stbtt_GetCodepointKernAdvance(FACE, previous, cp) * px;
                previous = cp;
            }
            return width;
        };
    }
}
