package com.veylon.ui;

import com.veylon.Game;
import com.veylon.engine.Input;
import com.veylon.engine.UiRenderer;
import com.veylon.entity.PlayerConstants;
import com.veylon.gfx.FontRenderer;
import com.veylon.item.CreativeCatalog;
import com.veylon.item.CreativeGrants;
import com.veylon.item.ItemType;

import java.util.List;
import java.util.function.IntPredicate;
import java.util.function.ToDoubleFunction;

import static org.lwjgl.glfw.GLFW.*;

/**
 * Creative catalog (R16-R18): category tabs, a search field, a scrolling icon
 * grid with tooltips, and an INVENTORY tab that reuses the inventory screen and
 * adds a trash slot. While open it owns every key, and typed characters reach
 * only the focused search field. Keyboard rules live in {@link #handleKeys} and
 * never touch the renderer, so they can be tested without a GL context.
 *
 * <p>Query, tab, scroll and focus are transient: {@link #reset} runs when the
 * screen closes and when a new world is built, and none of it is saved.
 */
public final class CreativeCatalogScreen {

    /** Result of one frame; CLOSE returns to gameplay. */
    public enum Action { NONE, CLOSE }

    private static final CreativeCatalog.Category[] CATEGORIES = CreativeCatalog.Category.values();
    private static final String TITLE = "CREATIVE CATALOG";
    private static final String INVENTORY_LABEL = "Inventory";
    private static final String SEARCH_RESULTS = "Search results";
    private static final String PLACEHOLDER = "Search items  ( / )";
    private static final String NO_MATCH = "No items match this search.";
    private static final String HINT =
            "LMB full stack     RMB one item     1-9 put in hotbar     / search     E or Esc close";
    private static final String INVENTORY_HINT = "Select a stack, then click TRASH to delete it.";
    private static final String TRASH_LABEL = "TRASH";
    private static final String FULL_NOTICE = "Inventory full";
    private static final int MAX_QUERY_CODE_POINTS = 32;
    private static final float PANEL_MAX_W = 920;
    private static final float PANEL_MAX_H = 600;
    private static final float SLOT = 46;
    private static final float SLOT_STEP = 50;
    private static final float TAB_H = 28;
    private static final float TAB_PAD = 20;
    private static final float TAB_GAP = 4;
    private static final float TAB_SCALE = 1.05f;
    private static final float SEARCH_W = 320;
    private static final float SEARCH_H = 28;
    private static final float SEARCH_SCALE = 1.15f;
    private static final float SEARCH_TEXT_SPACE = SEARCH_W - 24;
    private static final float NOTICE_SECONDS = 2f;
    private static final float LABEL_PAD = 12;
    private static final float LABEL_BACKING_H = 22;

    private final CreativeCatalog catalog = new CreativeCatalog();
    private final StringBuilder queryText = new StringBuilder();
    private String queryDisplay = "";
    private String queryTail = "";
    private float queryTailWidth;
    private boolean queryLayoutDirty = true;
    private boolean inventoryTab;
    private boolean searchFocused;
    private boolean openingFrame;
    private int scrollRows;
    private float noticeSeconds;
    private double lastFrameTime = -1;
    private ItemType tooltipType;
    private String tooltipLine = "";
    private IntPredicate drawable;
    private ToDoubleFunction<String> queryWidth;
    private final InventoryScreen inventoryScreen;

    /** The INVENTORY tab draws this screen, so selection, moves and gear behave as in Survival. */
    public CreativeCatalogScreen(InventoryScreen inventoryScreen) {
        this.inventoryScreen = java.util.Objects.requireNonNull(inventoryScreen, "inventoryScreen");
    }

    /** Returns to the first category with an empty, unfocused search and no scroll. */
    public void reset() {
        catalog.setCategory(CATEGORIES[0]);
        queryText.setLength(0);
        queryDisplay = "";
        queryTail = "";
        queryTailWidth = 0;
        queryLayoutDirty = true;
        catalog.setQuery("");
        inventoryTab = false;
        searchFocused = false;
        openingFrame = false;
        scrollRows = 0;
        noticeSeconds = 0;
        tooltipType = null;
        tooltipLine = "";
        lastFrameTime = -1;
    }

    /** The opening key belongs to the router; the first screen frame must not act on it again. */
    public void open() {
        reset();
        openingFrame = true;
    }

    public String query() {
        return queryDisplay;
    }

    public boolean searchFocused() {
        return searchFocused;
    }

    public boolean inventoryTab() {
        return inventoryTab;
    }

    /** Shows one category; clicking a tab also clears a search. */
    public void selectCategory(CreativeCatalog.Category category) {
        inventoryTab = false;
        searchFocused = false;
        if (queryText.length() > 0) {
            queryText.setLength(0);
            queryChanged();
        }
        catalog.setCategory(category);
        scrollRows = 0;
    }

    public void showInventory() {
        inventoryTab = true;
        searchFocused = false;
    }

    /** Sets the query and focuses the field, as typing would; QA captures and tests use it. */
    public void search(String text) {
        inventoryTab = false;
        searchFocused = true;
        queryText.setLength(0);
        if (text != null) {
            queryText.append(text);
        }
        queryChanged();
    }

    /** Deletes one inventory stack; only a player with unlimited items has a trash slot (R16). */
    public boolean trash(Game g, int slot) {
        if (!g.player.abilities.unlimitedItems() || g.player.inventory.get(slot) == null) {
            return false;
        }
        g.player.inventory.set(slot, null);
        return true;
    }

    /**
     * R18 keyboard rules. While the search field is focused, typed characters
     * edit only the query, so E, digits and letters never act; Escape or Enter
     * leaves the field first. Unfocused, E or Escape closes and / focuses the
     * field without typing the slash. A digit with an entry hovered puts a full
     * stack of it into that hotbar slot (R17).
     *
     * @param hovered  the catalog entry under the cursor, or null
     * @param drawable whether the font can draw a typed code point
     */
    public Action handleKeys(Game g, ItemType hovered, IntPredicate drawable) {
        if (openingFrame) {
            openingFrame = false;
            return Action.NONE;
        }
        Input input = g.input;
        if (searchFocused) {
            if (input.wasKeyPressed(GLFW_KEY_ESCAPE) || input.wasKeyPressed(GLFW_KEY_ENTER)) {
                searchFocused = false;
                return Action.NONE;
            }
            boolean edited = false;
            if (input.wasKeyPressed(GLFW_KEY_BACKSPACE) && queryText.length() > 0) {
                queryText.setLength(queryText.offsetByCodePoints(queryText.length(), -1));
                edited = true;
            }
            for (int i = 0; i < input.typedCount(); i++) {
                int codePoint = input.typedCodePoint(i);
                if (codePoint >= ' ' && codePoint != 0x7F && drawable.test(codePoint)
                        && queryText.codePointCount(0, queryText.length()) < MAX_QUERY_CODE_POINTS) {
                    queryText.appendCodePoint(codePoint);
                    edited = true;
                }
            }
            if (edited) {
                queryChanged();
            }
            return Action.NONE;
        }
        if (input.wasKeyPressed(GLFW_KEY_ESCAPE) || input.wasKeyPressed(GLFW_KEY_E)) {
            return Action.CLOSE;
        }
        if (input.wasKeyPressed(GLFW_KEY_SLASH)) {
            inventoryTab = false;
            searchFocused = true;
            return Action.NONE;
        }
        if (hovered != null && !inventoryTab) {
            for (int slot = 0; slot < PlayerConstants.HOTBAR_SLOTS; slot++) {
                if (input.wasKeyPressed(GLFW_KEY_1 + slot)) {
                    CreativeGrants.putInHotbar(g.player.inventory, slot, hovered);
                    g.audio.playClick();
                }
            }
        }
        return Action.NONE;
    }

    /** Draws the open tab, applies mouse grants and returns the keyboard decision. */
    public Action update(Game g) {
        if (drawable == null) {
            FontRenderer font = g.ui.fontRenderer();
            drawable = codePoint -> font.hasGlyph(codePoint, FontRenderer.Weight.REGULAR);
            queryWidth = text -> font.textWidth(text, SEARCH_SCALE);
            queryLayoutDirty = true;
        }
        float dt = lastFrameTime < 0 ? 0f : (float) Math.max(0, Math.min(0.1, g.totalTime - lastFrameTime));
        lastFrameTime = g.totalTime;
        noticeSeconds = Math.max(0, noticeSeconds - dt);

        UiRenderer ui = g.ui;
        int w = ui.screenW(), h = ui.screenH();
        double mx = g.input.cursorX(), my = g.input.cursorY();
        boolean leftClick = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_LEFT);
        boolean rightClick = g.input.wasMousePressed(GLFW_MOUSE_BUTTON_RIGHT);
        ItemType hovered = inventoryTab
                ? updateInventoryTab(g, ui, w, h, mx, my, leftClick)
                : updateCatalogTab(g, ui, w, h, mx, my, leftClick, rightClick);
        return handleKeys(g, hovered, drawable);
    }

    private ItemType updateCatalogTab(Game g, UiRenderer ui, int w, int h, double mx, double my,
                                      boolean leftClick, boolean rightClick) {
        float pw = Math.min(PANEL_MAX_W, w - 32f), ph = Math.min(PANEL_MAX_H, h - 32f);
        float x0 = (w - pw) / 2f, y0 = (h - ph) / 2f;
        ui.rect(0, 0, w, h, 0, 0, 0, 0.55f);
        ui.panel(x0, y0, pw, ph);
        ui.textCentered(w / 2f, y0 + 14, 1.8f, TITLE, 1f, 0.8f, 0.4f, 1f);
        drawTabs(ui, w / 2f, y0 + 48, mx, my, leftClick);

        float searchX = x0 + 24, searchY = y0 + 48 + TAB_H + 12;
        boolean searchHover = inside(mx, my, searchX, searchY, SEARCH_W, SEARCH_H);
        if (leftClick) {
            searchFocused = searchHover;
        }
        ui.rect(searchX, searchY, SEARCH_W, SEARCH_H, 0.03f, 0.06f, 0.08f, 0.95f);
        ui.rectOutline(searchX, searchY, SEARCH_W, SEARCH_H, searchFocused ? 2 : 1,
                0.28f, searchFocused ? 0.9f : 0.5f, searchFocused ? 0.95f : 0.55f, 0.9f);
        if (queryText.length() == 0 && !searchFocused) {
            ui.text(searchX + 10, searchY + 6, SEARCH_SCALE, PLACEHOLDER, 0.5f, 0.58f, 0.62f, 1f);
        } else {
            if (queryLayoutDirty) {
                queryTail = fittedQueryTail(queryDisplay, queryWidth);
                queryTailWidth = (float) queryWidth.applyAsDouble(queryTail);
                queryLayoutDirty = false;
            }
            ui.text(searchX + 10, searchY + 6, SEARCH_SCALE, queryTail, 0.92f, 0.97f, 0.98f, 1f);
            if (searchFocused && ((int) (g.totalTime * 2) & 1) == 0) {
                float caretX = searchX + 11 + queryTailWidth;
                ui.rect(caretX, searchY + 6, 2, SEARCH_H - 12, 0.9f, 0.95f, 1f, 1f);
            }
        }
        ui.textShadow(searchX + SEARCH_W + 16, searchY + 6, 1.15f,
                catalog.query().isBlank() ? catalog.category().label : SEARCH_RESULTS,
                0.85f, 0.9f, 0.92f, 1f);

        float gridX = x0 + 24, gridY = searchY + SEARCH_H + 14;
        float gridW = pw - 48, gridH = y0 + ph - 84 - gridY;
        int cols = Math.max(1, (int) ((gridW + SLOT_STEP - SLOT) / SLOT_STEP));
        int rows = Math.max(1, (int) ((gridH + SLOT_STEP - SLOT) / SLOT_STEP));
        List<ItemType> entries = catalog.view();
        int totalRows = (entries.size() + cols - 1) / cols;
        int maxScroll = Math.max(0, totalRows - rows);
        int wheel = (int) Math.signum(g.input.scrollDelta());
        scrollRows = Math.max(0, Math.min(scrollRows - wheel, maxScroll));
        float left = gridX + (gridW - (cols * SLOT_STEP - (SLOT_STEP - SLOT))) / 2f;
        ItemType hovered = null;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int index = (scrollRows + row) * cols + col;
                if (index >= entries.size()) {
                    break;
                }
                ItemType type = entries.get(index);
                float sx = left + col * SLOT_STEP, sy = gridY + row * SLOT_STEP;
                boolean hover = inside(mx, my, sx, sy, SLOT, SLOT);
                ui.slot(sx, sy, SLOT, hover, false);
                ui.itemIcon(type, sx + 7, sy + 7, SLOT - 14);
                if (hover) {
                    hovered = type;
                }
            }
        }
        if (entries.isEmpty()) {
            ui.textCentered(w / 2f, gridY + 24, 1.25f, NO_MATCH, 0.7f, 0.76f, 0.8f, 1f);
        }
        if (maxScroll > 0) {
            float trackH = rows * SLOT_STEP - (SLOT_STEP - SLOT);
            float thumbH = Math.max(18f, trackH * rows / totalRows);
            float thumbY = gridY + (trackH - thumbH) * scrollRows / maxScroll;
            ui.rect(x0 + pw - 14, gridY, 4, trackH, 0.2f, 0.28f, 0.3f, 0.8f);
            ui.rect(x0 + pw - 14, thumbY, 4, thumbH, 0.35f, 0.85f, 0.9f, 1f);
        }

        if (hovered != null && (leftClick || rightClick)) {
            if (CreativeGrants.grant(g.player.inventory, g.player.hotbarSel, hovered, leftClick)
                    == CreativeGrants.Result.INVENTORY_FULL) {
                noticeSeconds = NOTICE_SECONDS;
            } else {
                g.audio.playClick();
            }
        }
        if (hovered != tooltipType) {
            tooltipType = hovered;
            tooltipLine = hovered == null ? "" : ItemDetails.line(CreativeGrants.fresh(hovered, false));
        }
        float infoY = y0 + ph - 76;
        if (noticeSeconds > 0) {
            ui.textCentered(w / 2f, infoY, 1.4f, FULL_NOTICE, 1f, 0.65f, 0.42f, 1f);
        } else if (hovered != null) {
            ui.textCentered(w / 2f, infoY, 1.4f, hovered.displayName, 1f, 1f, 0.85f, 1f);
            ui.textCentered(w / 2f, infoY + 20, 1.15f, tooltipLine, 0.75f, 0.8f, 0.85f, 1f);
        }
        ui.textCentered(w / 2f, y0 + ph - 30, 1.05f, HINT, 0.58f, 0.7f, 0.73f, 1f);
        return hovered;
    }

    private ItemType updateInventoryTab(Game g, UiRenderer ui, int w, int h, double mx, double my,
                                        boolean click) {
        inventoryScreen.update(g);
        drawTabs(ui, w / 2f, InventoryScreen.panelTop(h) - TAB_H - 10, mx, my, click);
        float tx = InventoryScreen.panelRight(w) + 18, ty = InventoryScreen.gridTop(h);
        boolean hover = inside(mx, my, tx, ty, SLOT, SLOT);
        ui.slot(tx, ty, SLOT, hover, false);
        ui.rect(tx + 12, ty + SLOT / 2f - 2, SLOT - 24, 4, 0.9f, 0.35f, 0.3f, 1f);
        // This tab draws over the live world without a dimmed backdrop, so its
        // own labels sit on dark backing to stay readable against bright terrain.
        float labelW = ui.textWidth(TRASH_LABEL, 1.05f) + LABEL_PAD;
        ui.rect(tx + SLOT / 2f - labelW / 2f, ty + SLOT + 3, labelW, LABEL_BACKING_H,
                0.02f, 0.03f, 0.04f, 0.78f);
        ui.textCentered(tx + SLOT / 2f, ty + SLOT + 6, 1.05f, TRASH_LABEL, 1f, 0.55f, 0.45f, 1f);
        float hintY = InventoryScreen.panelBottom(h) + 10;
        float hintW = ui.textWidth(INVENTORY_HINT, 1.1f) + 2 * LABEL_PAD;
        ui.rect(w / 2f - hintW / 2f, hintY - 3, hintW, LABEL_BACKING_H + 2, 0.02f, 0.03f, 0.04f, 0.78f);
        ui.textCentered(w / 2f, hintY, 1.1f, INVENTORY_HINT, 0.72f, 0.84f, 0.88f, 1f);
        if (click && hover && trash(g, inventoryScreen.selectedSlot())) {
            inventoryScreen.reset();
            g.audio.playClick();
        }
        return null;
    }

    private void drawTabs(UiRenderer ui, float centerX, float y, double mx, double my, boolean click) {
        float total = -TAB_GAP;
        for (int i = 0; i <= CATEGORIES.length; i++) {
            total += tabWidth(ui, i) + TAB_GAP;
        }
        float x = centerX - total / 2f;
        for (int i = 0; i <= CATEGORIES.length; i++) {
            float tabW = tabWidth(ui, i);
            boolean active = i == CATEGORIES.length ? inventoryTab
                    : !inventoryTab && catalog.query().isBlank() && catalog.category() == CATEGORIES[i];
            boolean hover = inside(mx, my, x, y, tabW, TAB_H);
            ui.rect(x, y, tabW, TAB_H, active ? 0.10f : 0.045f, active ? 0.28f : 0.07f,
                    active ? 0.30f : 0.10f, 0.96f);
            ui.rectOutline(x, y, tabW, TAB_H, active ? 2 : 1, 0.28f, active || hover ? 0.85f : 0.4f,
                    active || hover ? 0.9f : 0.45f, 0.9f);
            ui.textCentered(x + tabW / 2f, y + 6, TAB_SCALE, tabLabel(i),
                    active ? 1f : 0.72f, active ? 1f : 0.8f, active ? 1f : 0.84f, 1f);
            if (hover && click) {
                if (i == CATEGORIES.length) {
                    showInventory();
                } else {
                    selectCategory(CATEGORIES[i]);
                }
            }
            x += tabW + TAB_GAP;
        }
    }

    private void queryChanged() {
        queryDisplay = queryText.toString();
        queryLayoutDirty = true;
        catalog.setQuery(queryDisplay);
        scrollRows = 0;
    }

    /** Keep the edited end visible without changing the query or splitting a Unicode code point. */
    static String fittedQueryTail(String text, ToDoubleFunction<String> width) {
        String tail = text;
        int offset = 0;
        while (offset < text.length() && width.applyAsDouble(tail) > SEARCH_TEXT_SPACE) {
            offset = text.offsetByCodePoints(offset, 1);
            tail = text.substring(offset);
        }
        return tail;
    }

    private static String tabLabel(int index) {
        return index == CATEGORIES.length ? INVENTORY_LABEL : CATEGORIES[index].label;
    }

    private static float tabWidth(UiRenderer ui, int index) {
        return ui.textWidth(tabLabel(index), TAB_SCALE) + TAB_PAD;
    }

    private static boolean inside(double mx, double my, float x, float y, float w, float h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }
}
