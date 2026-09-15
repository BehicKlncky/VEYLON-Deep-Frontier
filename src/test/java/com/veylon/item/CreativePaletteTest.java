package com.veylon.item;

import com.veylon.gfx.IconAtlas;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** R24: the audited building palette, and the economy it must stay out of. */
class CreativePaletteTest {

    /** Files allowed to name a palette constant; every other file reaches it generically. */
    private static final Set<String> PALETTE_DECLARATION_SITES = Set.of(
            "com/veylon/item/ItemType.java",
            "com/veylon/item/CreativePalette.java");

    @Test
    void everyBlockIsEitherBuildableOrCarriesAWrittenExclusionReason() {
        List<String> undecided = new ArrayList<>();
        for (BlockType block : BlockType.values()) {
            ItemType form = BlockItemForms.of(block);
            String reason = CreativePalette.exclusionReason(block);
            if (form == null && reason == null) {
                undecided.add(block.name());
            }
            assertFalse(form != null && reason != null,
                    "R24: " + block + " cannot be both placeable and excluded");
            if (reason != null) {
                assertFalse(reason.isBlank(), "R24: " + block + " needs a real reason, not an empty string");
            }
        }
        assertTrue(undecided.isEmpty(), "R24: these blocks passed the audit undecided - give each one a "
                + "palette item form or a recorded exclusion reason in CreativePalette: " + undecided);
    }

    @Test
    void everyPaletteItemPlacesExactlyOneFormerlyUnbuildableBlock() {
        assertEquals(19, CreativePalette.items().size(), "R24: the audited palette size");
        Set<BlockType> blocks = EnumSet.noneOf(BlockType.class);
        for (ItemType item : CreativePalette.items()) {
            BlockType block = item.places();
            assertNotNull(block, "R24: " + item + " exists to place a block");
            assertTrue(blocks.add(block), "R24: " + block + " has only one palette form");
            assertSame(item, BlockItemForms.of(block),
                    "R24: pick block resolves " + block + " to its palette form");
            assertNull(CreativePalette.exclusionReason(block),
                    "R24: an included block is not also excluded");
            assertTrue(item.name().endsWith("_BLOCK"),
                    "R24: " + item + " keeps the suffix that separates it from same-named materials");
        }
    }

    @Test
    void paletteItemsAreInertBuildingMaterialWithTheirOwnIconAndName() {
        Set<String> names = new HashSet<>();
        for (ItemType item : ItemType.values()) {
            assertTrue(names.add(item.displayName), "R24: display names stay unique: " + item.displayName);
        }
        for (ItemType item : CreativePalette.items()) {
            assertEquals(CreativeCatalog.Category.BUILDING, CreativeCatalog.categoryOf(item),
                    "R24: " + item + " is building material, not a station");
            assertTrue(CreativeCatalog.itemsIn(CreativeCatalog.Category.BUILDING).contains(item),
                    "R24: " + item + " is reachable from the Building tab");
            assertEquals("item/" + item.name().toLowerCase(java.util.Locale.ROOT), IconAtlas.idFor(item),
                    "R24: " + item + " has its own stable icon id");
            assertEquals(ToolKind.NONE, item.tool, "R24: " + item + " is not a tool");
            assertNull(item.equipSlot, "R24: " + item + " is not gear");
            assertFalse(item.isEdible(), "R24: " + item + " is not food");
            assertFalse(item.spoils(), "R24: " + item + " does not spoil");
            assertFalse(item.hasDurability(), "R24: " + item + " has no durability to lose");
        }
    }

    @Test
    void survivalDropTablesAndRecipesNeverProduceAPaletteItem() {
        for (BlockType block : BlockType.values()) {
            assertFalse(block.drop != null && CreativePalette.isPaletteItem(block.drop),
                    "R24: breaking " + block + " in Survival must still yield its old drop");
        }
        assertEquals(ItemType.DIRT, BlockType.GRASS.drop, "R24: grass still drops dirt");
        assertEquals(ItemType.STICK, BlockType.LEAVES.drop, "R24: leaves still drop a stick");
        assertEquals(ItemType.STONE, BlockType.RUIN_STONE.drop, "R24: ruin stone still drops stone");
        for (Recipe recipe : CraftingSystem.RECIPES) {
            assertFalse(recipe.result != null && CreativePalette.isPaletteItem(recipe.result),
                    "R24: no recipe crafts " + recipe.name);
            for (ItemType ingredient : recipe.ingredients.keySet()) {
                assertFalse(CreativePalette.isPaletteItem(ingredient),
                        "R24: no recipe consumes a palette item in " + recipe.name);
            }
        }
    }

    @Test
    void noLootTradeOrGameplayCodeNamesAPaletteConstant() throws IOException {
        Pattern names = Pattern.compile("\\b(" + String.join("|",
                CreativePalette.items().stream().map(Enum::name).toList()) + ")\\b");
        Path root = sourceRoot();
        List<String> mentions = new ArrayList<>();
        try (Stream<Path> files = Files.walk(root)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                if (PALETTE_DECLARATION_SITES.contains(relative)) {
                    continue;
                }
                if (names.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                    mentions.add(relative);
                }
            }
        }
        assertTrue(mentions.isEmpty(), "R24: palette items are reachable only through the Creative "
                + "catalog and pick block, so no recipe, drop table, loot table, trade or quest may "
                + "name one. Named in: " + mentions);
    }

    private static Path sourceRoot() {
        Path candidate = Path.of("src", "main", "java");
        if (Files.isDirectory(candidate)) {
            return candidate;
        }
        Path fromModule = Path.of("..").resolve(candidate);
        assertTrue(Files.isDirectory(fromModule),
                "cannot find src/main/java from " + Path.of("").toAbsolutePath());
        return fromModule;
    }
}
