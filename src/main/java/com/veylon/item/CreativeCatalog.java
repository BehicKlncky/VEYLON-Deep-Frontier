package com.veylon.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;

/**
 * Headless model behind the Creative catalog (R16). Every {@link ItemType}
 * belongs to exactly one {@link Category}, decided once by property rules and
 * a few explicit overrides, and each category keeps declaration order.
 *
 * <p>Search is case-insensitive under {@link Locale#ROOT}, so a Turkish default
 * locale cannot turn "IRON" into "ıron". Every whitespace-separated term must
 * appear in the display name or the enum id. A non-empty query searches every
 * category; an empty one lists the selected category. The filtered view is
 * rebuilt only when the query or category changes, so drawing the screen each
 * frame allocates nothing.
 */
public final class CreativeCatalog {

    /** Catalog tabs in display order. The INVENTORY tab is screen state, not a category. */
    public enum Category {
        BUILDING("Building"),
        STATIONS("Stations"),
        MATERIALS("Materials"),
        FOOD("Food & water"),
        MEDICAL("Medical"),
        TOOLS("Tools"),
        WEAPONS("Weapons & ammo"),
        GEAR("Gear");

        public final String label;

        Category(String label) {
            this.label = label;
        }
    }

    private static final EnumMap<ItemType, Category> CATEGORY_OF = new EnumMap<>(ItemType.class);
    private static final EnumMap<Category, List<ItemType>> ITEMS_IN = new EnumMap<>(Category.class);
    private static final String[] LOWER_NAME = new String[ItemType.values().length];
    private static final String[] LOWER_ID = new String[ItemType.values().length];

    static {
        for (Category category : Category.values()) {
            ITEMS_IN.put(category, new ArrayList<>());
        }
        for (ItemType type : ItemType.values()) {
            Category category = classify(type);
            CATEGORY_OF.put(type, category);
            ITEMS_IN.get(category).add(type);
            LOWER_NAME[type.ordinal()] = type.displayName.toLowerCase(Locale.ROOT);
            LOWER_ID[type.ordinal()] = type.name().toLowerCase(Locale.ROOT);
        }
        for (Category category : Category.values()) {
            ITEMS_IN.put(category, Collections.unmodifiableList(ITEMS_IN.get(category)));
        }
    }

    private Category category = Category.BUILDING;
    private String query = "";
    private final List<ItemType> view = new ArrayList<>();
    private final List<ItemType> readOnlyView = Collections.unmodifiableList(view);
    private boolean stale = true;
    private int rebuilds;

    public static Category categoryOf(ItemType type) {
        return CATEGORY_OF.get(type);
    }

    /** Items of one category in declaration order; the list is shared and read-only. */
    public static List<ItemType> itemsIn(Category category) {
        return ITEMS_IN.get(category);
    }

    public Category category() {
        return category;
    }

    public String query() {
        return query;
    }

    public void setCategory(Category next) {
        if (next != null && next != category) {
            category = next;
            stale = true;
        }
    }

    public void setQuery(String next) {
        String normalized = next == null ? "" : next;
        if (!normalized.equals(query)) {
            query = normalized;
            stale = true;
        }
    }

    /** The current entries; rebuilt only after the query or category changed. */
    public List<ItemType> view() {
        if (stale) {
            rebuild();
        }
        return readOnlyView;
    }

    /** Number of view rebuilds, so tests can prove unchanged frames reuse the view. */
    int rebuilds() {
        return rebuilds;
    }

    /** True when every term appears in the type's display name or enum id. */
    public static boolean matches(ItemType type, String query) {
        String normalized = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            return true;
        }
        for (String term : normalized.split("\\s+")) {
            if (!LOWER_NAME[type.ordinal()].contains(term) && !LOWER_ID[type.ordinal()].contains(term)) {
                return false;
            }
        }
        return true;
    }

    private void rebuild() {
        view.clear();
        if (query.isBlank()) {
            view.addAll(ITEMS_IN.get(category));
        } else {
            for (Category each : Category.values()) {
                for (ItemType type : ITEMS_IN.get(each)) {
                    if (matches(type, query)) {
                        view.add(type);
                    }
                }
            }
        }
        stale = false;
        rebuilds++;
    }

    /** One place decides membership: explicit ammunition first, then item properties. */
    private static Category classify(ItemType type) {
        if (CreativePalette.isPaletteItem(type)) {
            // R24: the appended block forms are building material, not stations,
            // which is where the places() rule at the bottom would otherwise put them.
            return Category.BUILDING;
        }
        switch (type) {
            case MUSKET_BALL, SCRAP_SHOT, ARROW, IRON_ARROW, RIFLE_CARTRIDGE -> {
                return Category.WEAPONS;
            }
            case DIRT, STONE, SAND, GRAVEL, CLAY, SNOW, LOG, PLANK, WALL, ROPE_LADDER, TRAIL_MARKER -> {
                return Category.BUILDING;
            }
            default -> {
            }
        }
        if (type.isEquippable()) {
            return Category.GEAR;
        }
        if (type.isMedical()) {
            return Category.MEDICAL;
        }
        if (type.isEdible() || type.name().startsWith("WATERSKIN")) {
            return Category.FOOD;
        }
        return switch (type.tool) {
            case PICKAXE, AXE, KNIFE -> Category.TOOLS;
            case WEAPON, BOW, FIREARM, THROWN -> Category.WEAPONS;
            default -> type.places() != null ? Category.STATIONS : Category.MATERIALS;
        };
    }
}
