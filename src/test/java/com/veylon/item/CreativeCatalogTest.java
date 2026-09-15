package com.veylon.item;

import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;

/** R16: headless catalog categories, ordering and Locale.ROOT search. */
class CreativeCatalogTest {

    @Test
    void everyItemBelongsToExactlyOneCategoryAndNoCategoryIsEmpty() {
        int listed = 0;
        EnumSet<ItemType> seen = EnumSet.noneOf(ItemType.class);
        for (CreativeCatalog.Category category : CreativeCatalog.Category.values()) {
            List<ItemType> items = CreativeCatalog.itemsIn(category);
            assertFalse(items.isEmpty(), "R16: " + category + " has entries");
            for (ItemType type : items) {
                assertTrue(seen.add(type), "R16: " + type + " appears in only one category");
                assertEquals(category, CreativeCatalog.categoryOf(type), "R16: membership agrees for " + type);
            }
            listed += items.size();
        }
        assertEquals(ItemType.values().length, listed, "R16: every item type is catalogued");
    }

    @Test
    void categoriesKeepDeclarationOrder() {
        for (CreativeCatalog.Category category : CreativeCatalog.Category.values()) {
            List<ItemType> items = CreativeCatalog.itemsIn(category);
            for (int i = 1; i < items.size(); i++) {
                assertTrue(items.get(i - 1).ordinal() < items.get(i).ordinal(),
                        "R16: " + category + " keeps declaration order");
            }
        }
    }

    @Test
    void anEmptyQueryListsTheSelectedCategory() {
        CreativeCatalog catalog = new CreativeCatalog();
        catalog.setCategory(CreativeCatalog.Category.TOOLS);
        assertEquals(CreativeCatalog.itemsIn(CreativeCatalog.Category.TOOLS), catalog.view(),
                "R16: no query shows the category");
        catalog.setQuery("   ");
        assertEquals(CreativeCatalog.itemsIn(CreativeCatalog.Category.TOOLS), catalog.view(),
                "R16: a blank query still shows the category");
    }

    @Test
    void searchIsCaseInsensitiveUnderATurkishDefaultLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            CreativeCatalog catalog = new CreativeCatalog();
            catalog.setQuery("IRON");
            assertTrue(catalog.view().contains(ItemType.IRON_INGOT), "R16: Locale.ROOT keeps IRON matching iron");
            catalog.setQuery("iron");
            assertTrue(catalog.view().contains(ItemType.IRON_PICKAXE), "R16: lowercase finds the same items");
            assertTrue(catalog.view().stream().allMatch(type -> CreativeCatalog.matches(type, "iron")));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void everyTermMustMatchTheDisplayNameOrEnumId() {
        CreativeCatalog catalog = new CreativeCatalog();
        catalog.setQuery("iron pick");
        assertEquals(List.of(ItemType.IRON_PICKAXE), catalog.view(), "R16: terms combine with AND");
        catalog.setQuery("musket_ball");
        assertEquals(List.of(ItemType.MUSKET_BALL), catalog.view(), "R16: the enum id is searchable");
        catalog.setQuery("iron ball");
        assertEquals(List.of(ItemType.MUSKET_BALL), catalog.view(), "R16: the display name is searchable");
        catalog.setQuery("iron zzz");
        assertTrue(catalog.view().isEmpty(), "R16: one missing term excludes the item");
    }

    @Test
    void theViewIsRebuiltOnlyWhenTheQueryOrCategoryChanges() {
        CreativeCatalog catalog = new CreativeCatalog();
        catalog.view();
        catalog.view();
        assertEquals(1, catalog.rebuilds(), "Unchanged frames reuse the view");
        catalog.setQuery("");
        catalog.setCategory(CreativeCatalog.Category.BUILDING);
        catalog.view();
        assertEquals(1, catalog.rebuilds(), "Setting identical values does not rebuild");
        catalog.setCategory(CreativeCatalog.Category.GEAR);
        catalog.view();
        catalog.setQuery("hide");
        catalog.view();
        assertEquals(3, catalog.rebuilds(), "Each real change rebuilds once");
    }
}
