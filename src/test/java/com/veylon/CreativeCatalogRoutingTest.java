package com.veylon;

import com.veylon.engine.Input;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.ui.CreativeCatalogScreen;
import org.junit.jupiter.api.Test;

import java.util.function.IntPredicate;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.glfw.GLFW.*;

/** R16-R18: catalog routing, key ownership, typed search, hotbar keys and the trash slot. */
class CreativeCatalogRoutingTest {
    /** Headless stand-in for the font: ASCII is drawable, everything else is not. */
    private static final IntPredicate DRAWABLE = codePoint -> codePoint < 0x80;

    @Test
    void eOpensTheCatalogInCreativeAndTheInventoryInSurvival() throws Exception {
        for (GameMode mode : GameMode.values()) {
            Game game = playing(mode);
            route(game, new HotkeyRouter(game), GLFW_KEY_E);
            if (mode == GameMode.CREATIVE) {
                assertEquals(Game.UiMode.CREATIVE_CATALOG, game.uiMode, "R16: E opens the Creative catalog");
            } else {
                assertEquals(Game.UiMode.INVENTORY, game.uiMode, "R16: Survival E still opens the inventory");
            }
            assertFalse(game.uiMode.pausesSimulation(), "R16: the world keeps running as with the inventory");
        }
    }

    @Test
    void noKeyReachesTheRouterWhileTheCatalogIsOpen() throws Exception {
        Game game = playing(GameMode.CREATIVE);
        HotkeyRouter router = new HotkeyRouter(game);
        route(game, router, GLFW_KEY_E);
        int logLines = game.eventLog.recent(200).size();
        route(game, router, GLFW_KEY_F5, GLFW_KEY_F9, GLFW_KEY_Q, GLFW_KEY_C, GLFW_KEY_M, GLFW_KEY_TAB,
                GLFW_KEY_P, GLFW_KEY_F3, GLFW_KEY_ESCAPE, GLFW_KEY_E, GLFW_KEY_3);
        assertEquals(Game.UiMode.CREATIVE_CATALOG, game.uiMode, "R18: the catalog owns every key");
        assertEquals(logLines, game.eventLog.recent(200).size(), "R18: F5 never saves and F9 never loads");
        assertFalse(game.simPaused || game.simPanelShown || game.debugShown, "R18: P, Tab and F3 stay inert");
        assertEquals(0, game.player.hotbarSel, "R18: digits do not change the hotbar selection");
    }

    @Test
    void typedLettersAndDigitsEditOnlyTheFocusedSearch() throws Exception {
        Game game = playing(GameMode.CREATIVE);
        CreativeCatalogScreen screen = game.creativeCatalogScreen;
        ItemStack firstSlot = game.player.inventory.get(0);
        assertEquals(CreativeCatalogScreen.Action.NONE, keys(game, screen, ItemType.TORCH, "/", GLFW_KEY_SLASH));
        assertTrue(screen.searchFocused(), "R16: / focuses the search field");
        assertEquals("", screen.query(), "R16: the slash that focuses the field is not typed");
        assertEquals(CreativeCatalogScreen.Action.NONE, keys(game, screen, ItemType.TORCH, "epm1",
                GLFW_KEY_E, GLFW_KEY_P, GLFW_KEY_M, GLFW_KEY_1), "R18: E does not close a focused search");
        assertEquals("epm1", screen.query(), "R18: typed E, P, M and 1 change only the query");
        assertSame(firstSlot, game.player.inventory.get(0), "R18: a focused search never fills the hotbar");
        keys(game, screen, null, "", GLFW_KEY_BACKSPACE);
        assertEquals("epm", screen.query(), "R16: Backspace removes the last character");
        keys(game, screen, null, "☃");
        assertEquals("epm", screen.query(), "R16: characters the font cannot draw are ignored");
        assertEquals(CreativeCatalogScreen.Action.NONE, keys(game, screen, null, "", GLFW_KEY_ESCAPE));
        assertFalse(screen.searchFocused(), "R18: Escape first leaves the search field");
        assertEquals("epm", screen.query(), "R18: leaving the field keeps the query");
        assertEquals(CreativeCatalogScreen.Action.CLOSE, keys(game, screen, null, "", GLFW_KEY_ESCAPE),
                "R18: a second Escape closes the catalog");
    }

    @Test
    void eClosesTheCatalogOnlyWhenSearchIsNotFocused() throws Exception {
        Game game = playing(GameMode.CREATIVE);
        CreativeCatalogScreen screen = game.creativeCatalogScreen;
        screen.search("");
        assertEquals(CreativeCatalogScreen.Action.NONE, keys(game, screen, null, "e", GLFW_KEY_E),
                "R18: E types into a focused search");
        assertEquals("e", screen.query());
        keys(game, screen, null, "", GLFW_KEY_ENTER);
        assertEquals(CreativeCatalogScreen.Action.CLOSE, keys(game, screen, null, "e", GLFW_KEY_E),
                "R18: E closes once the field is unfocused");
        assertEquals("e", screen.query(), "R18: the closing E is not typed");
    }

    @Test
    void aHoveredEntryAndADigitReplaceThatHotbarSlotWithAFullStack() throws Exception {
        Game game = playing(GameMode.CREATIVE);
        CreativeCatalogScreen screen = game.creativeCatalogScreen;
        game.player.inventory.set(2, new ItemStack(ItemType.STONE, 5));
        keys(game, screen, ItemType.TORCH, "3", GLFW_KEY_3);
        assertEquals(ItemType.TORCH, game.player.inventory.get(2).type, "R17: digit 3 fills hotbar slot 3");
        assertEquals(ItemType.TORCH.maxStack, game.player.inventory.get(2).count, "R17: with a full stack");
        ItemStack fifth = game.player.inventory.get(4);
        keys(game, screen, null, "5", GLFW_KEY_5);
        assertSame(fifth, game.player.inventory.get(4), "R17: without a hovered entry a digit does nothing");
    }

    @Test
    void theTrashDeletesStacksOnlyForACreativePlayer() {
        for (GameMode mode : GameMode.values()) {
            Game game = playing(mode);
            game.player.inventory.set(10, new ItemStack(ItemType.STONE, 5));
            boolean deleted = game.creativeCatalogScreen.trash(game, 10);
            if (mode == GameMode.CREATIVE) {
                assertTrue(deleted, "R16: the Creative trash deletes the stack");
                assertNull(game.player.inventory.get(10));
            } else {
                assertFalse(deleted, "R16: Survival has no trash");
                assertEquals(ItemType.STONE, game.player.inventory.get(10).type);
            }
        }
    }

    @Test
    void closingTheCatalogClearsQueryTabAndFocus() {
        Game game = playing(GameMode.CREATIVE);
        CreativeCatalogScreen screen = game.creativeCatalogScreen;
        screen.search("iron");
        game.uiMode = Game.UiMode.CREATIVE_CATALOG;
        game.closeScreens();
        assertEquals(Game.UiMode.NONE, game.uiMode);
        assertEquals("", screen.query(), "R16: closing clears the query");
        assertFalse(screen.searchFocused() || screen.inventoryTab(), "R16: closing clears focus and tab");
    }

    private static Game playing(GameMode mode) {
        Game game = new Game();
        game.newWorld(20260910L, true, mode);
        game.appState = Game.AppState.PLAYING;
        return game;
    }

    private static boolean[] pressed(Game game) throws ReflectiveOperationException {
        var field = Input.class.getDeclaredField("keyPressed");
        field.setAccessible(true);
        return (boolean[]) field.get(game.input);
    }

    private static void route(Game game, HotkeyRouter router, int... keys) throws ReflectiveOperationException {
        for (int key : keys) pressed(game)[key] = true;
        router.update();
        game.input.endFrame();
    }

    private static CreativeCatalogScreen.Action keys(Game game, CreativeCatalogScreen screen, ItemType hovered,
                                                      String typed, int... keyCodes)
            throws ReflectiveOperationException {
        for (int key : keyCodes) pressed(game)[key] = true;
        typed.codePoints().forEach(game.input::onTyped);
        CreativeCatalogScreen.Action action = screen.handleKeys(game, hovered, DRAWABLE);
        game.input.endFrame();
        return action;
    }
}
