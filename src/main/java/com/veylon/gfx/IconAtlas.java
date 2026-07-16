package com.veylon.gfx;

import com.veylon.item.EquipSlot;
import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Deterministic runtime atlas for item icons and reusable UI skins.
 *
 * <p>Every item is registered by a stable string ({@code item/iron_pickaxe}),
 * never by enum ordinal. Placeable blocks use the real material-registry tile
 * pixels on a small isometric cube. Tools, food, medicine, equipment and loose
 * materials use category silhouettes with per-item details, keeping the full
 * inventory readable without maintaining seventy external image files.</p>
 */
public final class IconAtlas {

    public static final String PANEL = "ui/panel";
    public static final String BUTTON = "ui/button";
    public static final String SLOT = "ui/slot";
    public static final String MISSING = "ui/missing";

    private static final int ATLAS_SIZE = 512;
    private static final int ICON_SIZE = 32;
    private static final int PADDING = 2;

    public static final class Region {
        public final String id;
        public final int width;
        public final int height;
        public final float u0;
        public final float v0;
        public final float u1;
        public final float v1;

        Region(String id, int width, int height, float u0, float v0, float u1, float v1) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.u0 = u0;
            this.v0 = v0;
            this.u1 = u1;
            this.v1 = v1;
        }
    }

    @FunctionalInterface
    private interface IconPainter {
        void paint(Canvas canvas);
    }

    private static final class Shelf {
        int x = PADDING;
        int y = PADDING;
        int rowHeight;
    }

    /** Tiny deterministic ARGB pixel-art canvas. */
    private static final class Canvas {
        final int width;
        final int height;
        final int[] pixels;

        Canvas(int width, int height) {
            this.width = width;
            this.height = height;
            this.pixels = new int[width * height];
        }

        void clear() {
            java.util.Arrays.fill(pixels, 0);
        }

        void set(int x, int y, int argb) {
            if (x >= 0 && y >= 0 && x < width && y < height) {
                pixels[y * width + x] = argb;
            }
        }

        void blend(int x, int y, int argb) {
            if (x < 0 || y < 0 || x >= width || y >= height) {
                return;
            }
            int sa = argb >>> 24;
            if (sa == 255) {
                set(x, y, argb);
                return;
            }
            if (sa == 0) {
                return;
            }
            int dst = pixels[y * width + x];
            int da = dst >>> 24;
            int outA = sa + (da * (255 - sa) + 127) / 255;
            int sr = (argb >> 16) & 255, sg = (argb >> 8) & 255, sb = argb & 255;
            int dr = (dst >> 16) & 255, dg = (dst >> 8) & 255, db = dst & 255;
            int outR = (sr * sa + dr * da * (255 - sa) / 255) / Math.max(1, outA);
            int outG = (sg * sa + dg * da * (255 - sa) / 255) / Math.max(1, outA);
            int outB = (sb * sa + db * da * (255 - sa) / 255) / Math.max(1, outA);
            pixels[y * width + x] = (outA << 24) | (outR << 16) | (outG << 8) | outB;
        }

        void rect(int x0, int y0, int x1, int y1, int argb) {
            for (int y = y0; y <= y1; y++) {
                for (int x = x0; x <= x1; x++) {
                    blend(x, y, argb);
                }
            }
        }

        void line(int x0, int y0, int x1, int y1, int thickness, int argb) {
            int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
            int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
            int err = dx + dy;
            while (true) {
                int radius = Math.max(0, thickness / 2);
                rect(x0 - radius, y0 - radius, x0 + radius, y0 + radius, argb);
                if (x0 == x1 && y0 == y1) {
                    break;
                }
                int e2 = 2 * err;
                if (e2 >= dy) {
                    err += dy;
                    x0 += sx;
                }
                if (e2 <= dx) {
                    err += dx;
                    y0 += sy;
                }
            }
        }

        void circle(int cx, int cy, int radius, int argb) {
            int rr = radius * radius;
            for (int y = -radius; y <= radius; y++) {
                for (int x = -radius; x <= radius; x++) {
                    if (x * x + y * y <= rr) {
                        blend(cx + x, cy + y, argb);
                    }
                }
            }
        }

        void polygon(int[] xs, int[] ys, int argb) {
            int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
            for (int y : ys) {
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            for (int y = minY; y <= maxY; y++) {
                List<Integer> crossings = new ArrayList<>();
                for (int i = 0, j = xs.length - 1; i < xs.length; j = i++) {
                    if ((ys[i] > y) != (ys[j] > y)) {
                        int x = xs[i] + (y - ys[i]) * (xs[j] - xs[i]) / (ys[j] - ys[i]);
                        crossings.add(x);
                    }
                }
                crossings.sort(Integer::compareTo);
                for (int i = 0; i + 1 < crossings.size(); i += 2) {
                    rect(crossings.get(i), y, crossings.get(i + 1), y, argb);
                }
            }
        }

        void outline(int[] xs, int[] ys, int thickness, int argb) {
            for (int i = 0; i < xs.length; i++) {
                int j = (i + 1) % xs.length;
                line(xs[i], ys[i], xs[j], ys[j], thickness, argb);
            }
        }

        boolean hasVisiblePixel() {
            for (int pixel : pixels) {
                if ((pixel >>> 24) != 0) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Map<String, Region> regions = new LinkedHashMap<>();
    private final EnumMap<ItemType, String> itemIds = new EnumMap<>(ItemType.class);
    private final List<String> validationErrors = new ArrayList<>();
    private final int[] atlasPixels = new int[ATLAS_SIZE * ATLAS_SIZE];
    private final Shelf shelf = new Shelf();
    private Texture2D texture;

    public void init() {
        if (texture != null) {
            return;
        }
        registerSkin(MISSING, canvas -> {
            for (int y = 0; y < canvas.height; y++) {
                for (int x = 0; x < canvas.width; x++) {
                    canvas.set(x, y, (((x / 4) + (y / 4)) & 1) == 0
                            ? 0xFFFF00FF : 0xFF151018);
                }
            }
        });
        registerSkin(PANEL, IconAtlas::drawPanelSkin);
        registerSkin(BUTTON, IconAtlas::drawButtonSkin);
        registerSkin(SLOT, IconAtlas::drawSlotSkin);

        for (ItemType item : ItemType.values()) {
            String id = idFor(item);
            itemIds.put(item, id);
            register(id, ICON_SIZE, ICON_SIZE, canvas -> drawItem(canvas, item));
        }

        validateItems();
        if (!validationErrors.isEmpty()) {
            throw new IllegalStateException("UI icon validation failed: " + validationErrors);
        }
        texture = Texture2D.fromArgb(atlasPixels, ATLAS_SIZE, ATLAS_SIZE, false, true);
        System.out.println("[ui] icon atlas: " + itemIds.size() + "/"
                + ItemType.values().length + " item icons, stable string IDs OK");
    }

    private void registerSkin(String id, IconPainter painter) {
        register(id, 24, 24, painter);
    }

    private void register(String id, int width, int height, IconPainter painter) {
        if (regions.containsKey(id)) {
            validationErrors.add("duplicate id " + id);
            return;
        }
        if (shelf.x + width + PADDING > ATLAS_SIZE) {
            shelf.x = PADDING;
            shelf.y += shelf.rowHeight + PADDING;
            shelf.rowHeight = 0;
        }
        if (shelf.y + height + PADDING > ATLAS_SIZE) {
            throw new IllegalStateException("Icon atlas overflow at " + id);
        }

        Canvas canvas = new Canvas(width, height);
        painter.paint(canvas);
        if (!canvas.hasVisiblePixel()) {
            validationErrors.add("empty icon " + id);
        }
        for (int y = 0; y < height; y++) {
            System.arraycopy(canvas.pixels, y * width, atlasPixels,
                    (shelf.y + y) * ATLAS_SIZE + shelf.x, width);
        }
        Region region = new Region(id, width, height,
                shelf.x / (float) ATLAS_SIZE,
                shelf.y / (float) ATLAS_SIZE,
                (shelf.x + width) / (float) ATLAS_SIZE,
                (shelf.y + height) / (float) ATLAS_SIZE);
        regions.put(id, region);
        shelf.x += width + PADDING;
        shelf.rowHeight = Math.max(shelf.rowHeight, height);
    }

    public static String idFor(ItemType item) {
        return "item/" + item.name().toLowerCase(Locale.ROOT);
    }

    public Region region(String id) {
        Region region = regions.get(id);
        return region != null ? region : regions.get(MISSING);
    }

    public Region item(ItemType item) {
        String id = itemIds.get(item);
        return id == null ? region(MISSING) : region(id);
    }

    public int itemIconCount() {
        return itemIds.size();
    }

    public List<String> validationErrors() {
        return List.copyOf(validationErrors);
    }

    public void bind(int unit) {
        if (texture == null) {
            throw new IllegalStateException("IconAtlas has not been initialized");
        }
        texture.bind(unit);
    }

    public void delete() {
        if (texture != null) {
            texture.delete();
            texture = null;
        }
        regions.clear();
        itemIds.clear();
        validationErrors.clear();
    }

    private void validateItems() {
        ItemType[] items = ItemType.values();
        for (ItemType item : items) {
            String id = itemIds.get(item);
            if (id == null || !regions.containsKey(id)) {
                validationErrors.add("missing " + item.name());
            }
        }
        if (itemIds.size() != items.length) {
            validationErrors.add("registered " + itemIds.size() + " of " + items.length + " items");
        }
    }

    private static void drawItem(Canvas c, ItemType item) {
        int base = itemColor(item);
        if (item.places() != null && MaterialRegistry.layerCount() > 0) {
            drawBlock(c, item, base);
            decoratePlaceable(c, item);
            return;
        }
        if (item.tool != ToolKind.NONE) {
            drawTool(c, item, base);
            return;
        }
        if (item.equipSlot != null) {
            drawGear(c, item, base);
            return;
        }
        switch (item) {
            case BERRY, DRIED_BERRY, HERB, RAW_MEAT, COOKED_MEAT, DRIED_MEAT, SPOILED_MEAT ->
                    drawFood(c, item, base);
            case WATERSKIN_EMPTY, WATERSKIN_DIRTY, WATERSKIN_CLEAN -> drawWaterskin(c, item, base);
            case BANDAGE, SPLINT, ANTISEPTIC, HERBAL_POULTICE, MEDICINE ->
                    drawMedical(c, item, base);
            default -> drawMaterial(c, item, base);
        }
    }

    private static void drawBlock(Canvas c, ItemType item, int fallback) {
        var material = MaterialRegistry.of(item.places());
        int[] top = MaterialRegistry.layerPixels(material.topLayer[0]);
        int[] side = MaterialRegistry.layerPixels(material.sideLayer[0]);

        // Soft ground shadow.
        c.polygon(new int[]{5, 16, 28, 17}, new int[]{22, 28, 22, 17}, 0x50000000);
        texturedTriangle(c, 16, 3, 29, 10, 16, 18,
                0, 0, 1, 0, 1, 1, top, 1.08f);
        texturedTriangle(c, 16, 3, 16, 18, 3, 10,
                0, 0, 1, 1, 0, 1, top, 1.08f);
        texturedTriangle(c, 3, 10, 16, 18, 16, 30,
                0, 0, 1, 0, 1, 1, side, 0.72f);
        texturedTriangle(c, 3, 10, 16, 30, 3, 22,
                0, 0, 1, 1, 0, 1, side, 0.72f);
        texturedTriangle(c, 16, 18, 29, 10, 29, 22,
                0, 0, 1, 0, 1, 1, side, 0.88f);
        texturedTriangle(c, 16, 18, 29, 22, 16, 30,
                0, 0, 1, 1, 0, 1, side, 0.88f);

        int outline = shade(fallback, 0.35f);
        c.outline(new int[]{16, 29, 29, 16, 3, 3}, new int[]{3, 10, 22, 30, 22, 10}, 1, outline);
        c.line(3, 10, 16, 18, 1, outline);
        c.line(29, 10, 16, 18, 1, outline);
        c.line(16, 18, 16, 30, 1, outline);
    }

    private static void decoratePlaceable(Canvas c, ItemType item) {
        switch (item) {
            case TORCH -> {
                c.clear();
                c.line(10, 27, 21, 8, 4, 0xFF6F4827);
                c.line(11, 26, 22, 7, 1, 0xFFE0B16B);
                c.polygon(new int[]{18, 23, 26, 23, 19}, new int[]{10, 3, 8, 14, 13}, 0xFFFF8A24);
                c.circle(22, 8, 3, 0xFFFFD35A);
            }
            case CAMPFIRE -> {
                c.line(8, 25, 24, 19, 4, 0xFF5D3820);
                c.line(8, 19, 24, 25, 4, 0xFF7A4B27);
                c.polygon(new int[]{12, 17, 16, 22, 20, 15, 10},
                        new int[]{20, 10, 15, 6, 20, 24, 22}, 0xFFFF7925);
                c.polygon(new int[]{14, 17, 18, 16, 12}, new int[]{20, 13, 18, 22, 22}, 0xFFFFD75A);
            }
            case WORKBENCH -> {
                c.line(8, 8, 24, 24, 2, 0xFFEAC26D);
                c.line(24, 8, 8, 24, 2, 0xFFEAC26D);
            }
            case CRATE -> {
                c.line(6, 12, 26, 24, 2, 0xFFE3B75F);
                c.line(26, 12, 7, 24, 2, 0xFFE3B75F);
            }
            case FURNACE -> {
                c.circle(21, 21, 5, 0xFF211D22);
                c.circle(21, 21, 3, 0xFFFF7925);
            }
            case BEACON_FRAME -> {
                c.line(16, 5, 16, 27, 2, 0xFFB9E5EA);
                c.circle(16, 10, 4, 0xFF4BE8F1);
                c.line(8, 27, 16, 18, 2, 0xFF72B9C2);
                c.line(24, 27, 16, 18, 2, 0xFF72B9C2);
            }
            case BEDROLL -> {
                c.rect(6, 19, 25, 26, 0xFF8D7248);
                c.circle(7, 22, 4, 0xFFC5A66D);
                c.line(9, 19, 9, 26, 1, 0xFF4A392A);
            }
            default -> {
                // The real material projection is already the distinguishing mark.
            }
        }
    }

    private static void drawTool(Canvas c, ItemType item, int base) {
        int dark = shade(base, 0.38f);
        int light = shade(base, 1.35f);
        int handle = item.name().startsWith("IRON_") ? 0xFF77583A : 0xFF8B6237;
        c.line(8, 27, 23, 9, 4, 0x65000000);
        c.line(8, 26, 23, 8, 3, handle);
        c.line(9, 25, 23, 8, 1, 0xFFC99A58);

        switch (item.tool) {
            case PICKAXE -> {
                c.line(13, 6, 27, 11, 5, dark);
                c.line(12, 5, 28, 10, 3, base);
                c.line(12, 5, 10, 9, 2, light);
                c.line(28, 10, 27, 14, 2, light);
            }
            case AXE -> {
                c.polygon(new int[]{18, 22, 29, 27, 22, 18},
                        new int[]{5, 4, 9, 16, 16, 11}, dark);
                c.polygon(new int[]{19, 22, 28, 26, 22, 19},
                        new int[]{5, 5, 9, 14, 14, 10}, base);
                c.line(27, 9, 25, 14, 1, light);
            }
            case WEAPON -> {
                c.line(6, 28, 24, 7, 2, handle);
                c.polygon(new int[]{21, 28, 25, 19}, new int[]{8, 2, 10, 12}, dark);
                c.polygon(new int[]{22, 27, 25, 20}, new int[]{8, 3, 9, 11}, light);
                c.line(5, 25, 9, 29, 2, base);
            }
            case KNIFE -> {
                c.line(7, 26, 14, 19, 4, handle);
                c.polygon(new int[]{12, 21, 28, 23, 16},
                        new int[]{19, 8, 5, 13, 20}, dark);
                c.polygon(new int[]{14, 21, 27, 22, 16},
                        new int[]{18, 9, 6, 12, 19}, light);
                c.line(14, 18, 18, 22, 2, base);
            }
            default -> {
            }
        }
    }

    private static void drawFood(Canvas c, ItemType item, int base) {
        switch (item) {
            case BERRY -> {
                c.circle(12, 17, 6, shade(base, 0.8f));
                c.circle(20, 18, 6, base);
                c.circle(16, 23, 6, shade(base, 1.1f));
                c.line(16, 12, 18, 6, 2, 0xFF5D8E45);
                c.polygon(new int[]{17, 26, 20}, new int[]{8, 7, 13}, 0xFF78A958);
                c.circle(18, 16, 1, 0xFFFFB3C0);
            }
            case DRIED_BERRY -> {
                for (int i = 0; i < 6; i++) {
                    c.circle(9 + (i % 3) * 7, 14 + (i / 3) * 8, 4,
                            shade(base, 0.7f + i * 0.06f));
                }
                c.line(8, 27, 25, 7, 1, 0xFF7E9D4A);
            }
            case HERB -> {
                c.line(16, 28, 16, 8, 2, shade(base, 0.6f));
                c.polygon(new int[]{15, 5, 9, 16}, new int[]{20, 15, 10, 15}, base);
                c.polygon(new int[]{17, 27, 24, 16}, new int[]{17, 11, 20, 22}, shade(base, 1.15f));
                c.polygon(new int[]{16, 10, 15, 21}, new int[]{13, 5, 3, 12}, shade(base, 1.3f));
            }
            case RAW_MEAT, COOKED_MEAT, DRIED_MEAT, SPOILED_MEAT -> {
                int meat = item == ItemType.COOKED_MEAT ? 0xFF9C542D
                        : item == ItemType.DRIED_MEAT ? 0xFF754027
                        : item == ItemType.SPOILED_MEAT ? 0xFF727347 : base;
                c.polygon(new int[]{5, 10, 20, 28, 26, 18, 8},
                        new int[]{18, 9, 7, 13, 23, 27, 25}, shade(meat, 0.55f));
                c.polygon(new int[]{7, 11, 20, 26, 24, 17, 9},
                        new int[]{17, 10, 9, 14, 22, 25, 23}, meat);
                c.circle(18, 16, 4, shade(meat, 1.35f));
                if (item == ItemType.SPOILED_MEAT) {
                    c.circle(11, 17, 2, 0xFF9BA84B);
                    c.circle(23, 20, 2, 0xFF4A5730);
                } else if (item == ItemType.COOKED_MEAT) {
                    c.line(10, 14, 22, 20, 1, 0xFF4E2B20);
                    c.line(12, 11, 24, 17, 1, 0xFF4E2B20);
                }
            }
            default -> {
            }
        }
    }

    private static void drawWaterskin(Canvas c, ItemType item, int base) {
        int dark = shade(base, 0.52f);
        c.polygon(new int[]{13, 19, 21, 27, 25, 7, 5, 11},
                new int[]{4, 4, 10, 18, 28, 28, 18, 10}, dark);
        c.polygon(new int[]{14, 18, 19, 25, 23, 9, 7, 13},
                new int[]{5, 5, 11, 18, 26, 26, 18, 11}, base);
        c.rect(13, 3, 19, 7, 0xFFB89B70);
        c.line(9, 13, 23, 23, 1, shade(base, 1.3f));
        if (item != ItemType.WATERSKIN_EMPTY) {
            int water = item == ItemType.WATERSKIN_CLEAN ? 0xFF5FAFE0 : 0xFF8B864D;
            c.rect(9, 20, 23, 25, water);
            c.line(10, 20, 22, 20, 1, shade(water, 1.35f));
        }
    }

    private static void drawMedical(Canvas c, ItemType item, int base) {
        switch (item) {
            case BANDAGE -> {
                c.line(7, 24, 24, 7, 9, 0xFFB8B5AC);
                c.line(7, 24, 24, 7, 6, base);
                c.line(11, 22, 23, 10, 1, 0xFFFFFFFF);
                c.line(9, 18, 14, 23, 1, 0xFF8F8B83);
            }
            case SPLINT -> {
                c.line(9, 27, 15, 5, 4, 0xFF76502C);
                c.line(18, 28, 24, 6, 4, 0xFF9A6B37);
                c.line(8, 13, 24, 17, 2, base);
                c.line(7, 21, 22, 25, 2, base);
            }
            case ANTISEPTIC, MEDICINE -> {
                c.rect(10, 9, 23, 28, shade(base, 0.6f));
                c.rect(12, 10, 21, 26, base);
                c.rect(13, 4, 20, 10, 0xFFD9DCE0);
                c.rect(14, 3, 19, 5, 0xFF747980);
                c.rect(13, 15, 20, 22, 0xFFE9ECE9);
                c.rect(16, 16, 17, 21, 0xFFC64055);
                c.rect(14, 18, 19, 19, 0xFFC64055);
            }
            case HERBAL_POULTICE -> {
                c.polygon(new int[]{6, 12, 25, 28, 21, 8},
                        new int[]{12, 5, 7, 21, 28, 25}, shade(base, 0.65f));
                c.polygon(new int[]{8, 13, 24, 26, 20, 9},
                        new int[]{12, 7, 9, 20, 26, 23}, base);
                c.line(9, 23, 25, 9, 2, 0xFFB8A472);
                c.line(8, 14, 23, 24, 2, 0xFFB8A472);
                c.circle(17, 16, 3, 0xFF7FAD5A);
            }
            default -> {
            }
        }
    }

    private static void drawGear(Canvas c, ItemType item, int base) {
        int dark = shade(base, 0.45f);
        EquipSlot slot = item.equipSlot;
        switch (slot) {
            case HEAD -> {
                c.circle(16, 15, 10, dark);
                c.polygon(new int[]{7, 10, 22, 26, 23, 9},
                        new int[]{16, 8, 7, 16, 24, 24}, base);
                c.rect(9, 17, 23, 21, shade(base, 0.75f));
            }
            case TORSO -> {
                c.polygon(new int[]{5, 11, 14, 18, 21, 27, 24, 22, 10, 8},
                        new int[]{10, 5, 8, 8, 5, 10, 17, 29, 29, 17}, dark);
                c.polygon(new int[]{7, 12, 14, 18, 20, 25, 22, 20, 12, 10},
                        new int[]{10, 7, 10, 10, 7, 10, 16, 27, 27, 16}, base);
                c.line(16, 10, 16, 27, 1, shade(base, 1.35f));
            }
            case LEGS -> {
                c.polygon(new int[]{9, 23, 21, 18, 15, 12},
                        new int[]{5, 5, 28, 28, 15, 28}, dark);
                c.polygon(new int[]{11, 21, 19, 17, 15, 13},
                        new int[]{7, 7, 26, 26, 13, 26}, base);
            }
            case FEET -> {
                c.polygon(new int[]{6, 14, 15, 12, 4}, new int[]{8, 7, 22, 27, 27}, dark);
                c.polygon(new int[]{18, 26, 28, 20, 17}, new int[]{7, 9, 27, 27, 21}, dark);
                c.polygon(new int[]{8, 13, 13, 6}, new int[]{9, 9, 22, 25}, base);
                c.polygon(new int[]{19, 25, 26, 19}, new int[]{9, 10, 25, 22}, base);
            }
            case BACK -> {
                c.polygon(new int[]{9, 12, 21, 24, 26, 24, 8, 6},
                        new int[]{8, 4, 4, 8, 26, 29, 29, 25}, dark);
                c.rect(9, 8, 23, 27, base);
                c.rect(11, 18, 21, 25, shade(base, 0.75f));
                c.line(11, 8, 8, 22, 2, shade(base, 1.25f));
                c.line(21, 8, 24, 22, 2, shade(base, 1.25f));
            }
        }
    }

    private static void drawMaterial(Canvas c, ItemType item, int base) {
        switch (item) {
            case STICK -> {
                c.line(6, 27, 25, 5, 5, shade(base, 0.48f));
                c.line(7, 26, 25, 5, 3, base);
                c.line(17, 14, 26, 16, 2, shade(base, 1.2f));
            }
            case FIBER -> {
                for (int i = 0; i < 5; i++) {
                    c.line(7 + i * 2, 27, 17 + i * 2, 6, 2, shade(base, 0.75f + i * 0.1f));
                }
                c.line(8, 19, 24, 15, 2, 0xFF9B8754);
            }
            case HIDE, LEATHER -> {
                c.polygon(new int[]{5, 10, 8, 14, 18, 24, 27, 24, 25, 18, 13, 7},
                        new int[]{9, 10, 4, 8, 5, 9, 16, 20, 27, 25, 29, 23}, shade(base, 0.45f));
                c.polygon(new int[]{7, 11, 10, 14, 18, 23, 25, 22, 23, 18, 13, 9},
                        new int[]{10, 11, 7, 10, 7, 10, 16, 20, 24, 23, 26, 21}, base);
                if (item == ItemType.LEATHER) {
                    c.line(10, 13, 22, 20, 1, shade(base, 1.35f));
                    c.line(11, 19, 20, 11, 1, shade(base, 0.62f));
                }
            }
            case BONE -> {
                c.line(9, 24, 23, 9, 6, base);
                c.circle(8, 25, 4, base);
                c.circle(6, 22, 3, base);
                c.circle(24, 8, 4, base);
                c.circle(27, 11, 3, base);
                c.line(10, 22, 22, 10, 1, 0xFFFFFFFF);
            }
            case COPPER_INGOT, IRON_INGOT -> {
                c.polygon(new int[]{5, 11, 27, 23}, new int[]{21, 10, 10, 24}, shade(base, 0.45f));
                c.polygon(new int[]{7, 12, 25, 21}, new int[]{20, 12, 12, 22}, base);
                c.line(12, 13, 24, 13, 2, shade(base, 1.4f));
            }
            case SIGNAL_CRYSTAL -> {
                c.polygon(new int[]{16, 24, 21, 16, 10, 7},
                        new int[]{2, 11, 27, 31, 27, 11}, shade(base, 0.5f));
                c.polygon(new int[]{16, 22, 19, 16, 12, 9},
                        new int[]{3, 12, 26, 29, 25, 12}, base);
                c.polygon(new int[]{16, 19, 16, 12}, new int[]{4, 13, 27, 13}, shade(base, 1.5f));
                c.circle(16, 15, 3, 0xFFBDFBFF);
            }
            case BLUEPRINT_FRAGMENT -> {
                c.polygon(new int[]{7, 23, 27, 10, 5}, new int[]{5, 4, 23, 29, 21}, shade(base, 0.55f));
                c.polygon(new int[]{8, 22, 25, 10, 7}, new int[]{7, 6, 22, 27, 20}, base);
                c.line(10, 11, 20, 10, 1, 0xFFB9E7FF);
                c.line(11, 15, 22, 20, 1, 0xFFB9E7FF);
                c.rect(12, 17, 16, 22, 0xFFB9E7FF);
            }
            case SCRAP -> {
                c.polygon(new int[]{5, 10, 16, 19, 28, 25, 15, 9},
                        new int[]{18, 7, 10, 4, 11, 25, 22, 28}, shade(base, 0.5f));
                c.rect(8, 13, 23, 21, base);
                c.circle(13, 17, 4, 0xFF242B31);
                c.line(20, 7, 24, 26, 2, shade(base, 1.35f));
            }
            default -> drawRock(c, base, item.name().hashCode());
        }
    }

    private static void drawRock(Canvas c, int base, int seed) {
        int dark = shade(base, 0.48f);
        c.polygon(new int[]{4, 9, 19, 27, 29, 22, 10, 3},
                new int[]{20, 8, 4, 11, 22, 28, 29, 25}, dark);
        c.polygon(new int[]{6, 10, 19, 25, 27, 21, 11, 5},
                new int[]{19, 10, 6, 12, 21, 26, 27, 24}, base);
        int h = seed;
        for (int i = 0; i < 7; i++) {
            h = h * 1664525 + 1013904223;
            int x = 9 + Math.floorMod(h, 15);
            h = h * 1664525 + 1013904223;
            int y = 10 + Math.floorMod(h, 13);
            c.circle(x, y, 1 + (i & 1), shade(base, i % 3 == 0 ? 1.55f : 0.72f));
        }
    }

    private static void drawPanelSkin(Canvas c) {
        c.rect(0, 0, 23, 23, 0xF0121722);
        c.rect(1, 1, 22, 22, 0xF51B2230);
        c.line(2, 2, 21, 2, 1, 0xFF77859A);
        c.line(2, 2, 2, 21, 1, 0xFF66778D);
        c.line(2, 21, 21, 21, 1, 0xFF2B3443);
        c.line(21, 2, 21, 21, 1, 0xFF2B3443);
        c.rect(3, 3, 5, 5, 0xFF3BC2C9);
        c.rect(18, 3, 20, 5, 0xFF3BC2C9);
    }

    private static void drawButtonSkin(Canvas c) {
        c.rect(0, 0, 23, 23, 0xEB17212B);
        c.rect(2, 2, 21, 21, 0xF22B3A47);
        c.line(2, 2, 21, 2, 1, 0xFF7D9CAB);
        c.line(2, 2, 2, 21, 1, 0xFF6C8998);
        c.line(2, 21, 21, 21, 1, 0xFF15202A);
        c.line(21, 2, 21, 21, 1, 0xFF15202A);
    }

    private static void drawSlotSkin(Canvas c) {
        c.rect(0, 0, 23, 23, 0xD70C1118);
        c.rect(2, 2, 21, 21, 0xE719202B);
        c.line(1, 1, 22, 1, 1, 0xFF526273);
        c.line(1, 1, 1, 22, 1, 0xFF526273);
        c.line(1, 22, 22, 22, 1, 0xFF080B10);
        c.line(22, 1, 22, 22, 1, 0xFF080B10);
        c.rect(4, 4, 5, 5, 0xFF2C7981);
    }

    private static void texturedTriangle(Canvas c,
                                         float ax, float ay, float bx, float by, float cx, float cy,
                                         float au, float av, float bu, float bv, float cu, float cv,
                                         int[] texture, float light) {
        int minX = Math.max(0, (int) Math.floor(Math.min(ax, Math.min(bx, cx))));
        int maxX = Math.min(c.width - 1, (int) Math.ceil(Math.max(ax, Math.max(bx, cx))));
        int minY = Math.max(0, (int) Math.floor(Math.min(ay, Math.min(by, cy))));
        int maxY = Math.min(c.height - 1, (int) Math.ceil(Math.max(ay, Math.max(by, cy))));
        float denom = (by - cy) * (ax - cx) + (cx - bx) * (ay - cy);
        if (Math.abs(denom) < 0.0001f) {
            return;
        }
        int textureSize = (int) Math.round(Math.sqrt(texture.length));
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                float px = x + 0.5f, py = y + 0.5f;
                float wa = ((by - cy) * (px - cx) + (cx - bx) * (py - cy)) / denom;
                float wb = ((cy - ay) * (px - cx) + (ax - cx) * (py - cy)) / denom;
                float wc = 1f - wa - wb;
                if (wa >= -0.001f && wb >= -0.001f && wc >= -0.001f) {
                    float u = clamp01(wa * au + wb * bu + wc * cu);
                    float v = clamp01(wa * av + wb * bv + wc * cv);
                    int tx = Math.min(textureSize - 1, (int) (u * (textureSize - 1)));
                    int ty = Math.min(textureSize - 1, (int) (v * (textureSize - 1)));
                    c.blend(x, y, shade(texture[ty * textureSize + tx], light));
                }
            }
        }
    }

    private static int itemColor(ItemType item) {
        int r = Math.round(clamp01(item.r) * 255);
        int g = Math.round(clamp01(item.g) * 255);
        int b = Math.round(clamp01(item.b) * 255);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int shade(int argb, float amount) {
        int a = argb >>> 24;
        int r = Math.min(255, Math.max(0, Math.round(((argb >> 16) & 255) * amount)));
        int g = Math.min(255, Math.max(0, Math.round(((argb >> 8) & 255) * amount)));
        int b = Math.min(255, Math.max(0, Math.round((argb & 255) * amount)));
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    private static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }
}
