package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.gfx.ResourceManager;
import com.veylon.item.ItemNames;
import com.veylon.item.ItemType;
import com.veylon.item.UrbanCrafting;
import com.veylon.ui.HudLayout;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SurvivorSites;
import org.junit.jupiter.api.Test;
import org.lwjgl.stb.STBTTFontinfo;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.lwjgl.stb.STBTruetype.*;

/**
 * The interaction line in a layout-sixteen city (urban milestone 21) explains what a press does and
 * what it costs in the words the player sees: the pry tool a lock asks for is the item the pack calls
 * a pry tool; a locked or barred door names the control that forces it; a container says what it is,
 * whose it is and what searching costs, without touching the container; and no line carries an
 * internal name. The wider prompt box holds these lines, and long answers wrap in conversation.
 */
class UrbanInteractionPromptsTest {
    /** Internal words: an enum constant or an identifier with underscores, once key caps are removed. */
    private static final Pattern INTERNAL = Pattern.compile("\\b[A-Z]{3,}(_[A-Z]+)*\\b|\\b[a-z]+_[a-z_]+\\b");

    private static Game city() {
        Game g = SurvivorWorldArena.session(SurvivorWorldArena.SEED, GameMode.SURVIVAL, World.GEN_URBAN_MISSIONS,
                new AudioManager());
        SurvivorWorldArena.run(g, .2f);
        return g;
    }

    private static String promptAt(Game g, Vec3i stand, Vec3i aim) {
        SurvivorWorldArena.standAt(g, stand);
        RefugeWorldArena.aim(g, aim);
        g.updatePrompt();
        return g.interactPrompt;
    }

    private static void plainWords(String prompt) {
        assertNotNull(prompt);
        String words = prompt.replaceAll("\\[[A-Za-z0-9 ]+]", "").replaceAll("\\b(LMB|RMB)\\b", "");
        assertFalse(INTERNAL.matcher(words).find(), "an internal name in: " + prompt);
    }

    @Test
    void aCityCallsThePryToolWhatItsPromptsAskFor() {
        assertEquals("Pry Tool", ItemNames.of(ItemType.IRON_PICKAXE, true));
        assertEquals("Iron Pickaxe", ItemNames.of(ItemType.IRON_PICKAXE, false), "the wilderness keeps its name");
        assertEquals("pry tool", ItemNames.toolNoun(com.veylon.item.ToolKind.PICKAXE, true));
        var repair = UrbanCrafting.RECIPES.stream().filter(r -> r.name.equals("Repair Pry Tool")).findFirst().orElseThrow();
        assertEquals(ItemNames.PRY_TOOL, ItemNames.of(repair.result, true), "the recipe makes what it says");

        Game legacy = new Game();
        legacy.newWorld(SurvivorWorldArena.SEED, true, World.DEFAULT_NEW_WORLD_GENERATOR, GameMode.SURVIVAL);
        Vec3i ore = new Vec3i((int) legacy.player.pos.x + 2, (int) legacy.player.pos.y, (int) legacy.player.pos.z);
        legacy.world.setBlock(ore.x(), ore.y(), ore.z(), BlockType.IRON_ORE, true);
        legacy.player.inventory.clear();
        RefugeWorldArena.aim(legacy, ore);
        legacy.updatePrompt();
        assertEquals("Requires pickaxe", legacy.interactPrompt, "the wilderness line is unchanged and locale-free");
    }

    @Test
    void lockedDoorsNameTheControlAndTheToolThatForceThem() {
        Game g = city();
        g.player.inventory.clear();
        List<BuildingPlan.Fixture> locked = new ArrayList<>();
        for (int rx = -1; rx <= 1; rx++) {
            for (int rz = -1; rz <= 1; rz++) {
                for (ParcelPlan p : g.world.urban.regionPlan(rx, rz).parcels()) {
                    BuildingPlan b = g.world.urban.buildingAt(p.x0(), p.z0());
                    if (b == null) { continue; }
                    for (BuildingPlan.Fixture f : b.fixtures()) {
                        if (f.kind() == BuildingPlan.FixtureKind.DOOR && f.locked()
                                && locked.stream().noneMatch(l -> l.block().requiresTool == f.block().requiresTool)) {
                            locked.add(f);
                        }
                    }
                }
            }
        }
        assertTrue(locked.stream().anyMatch(f -> !f.block().requiresTool), "a locked wooden door near the spawn");
        for (BuildingPlan.Fixture door : locked) {
            String prompt = promptAt(g, door.target(), door.position());
            assertTrue(prompt.startsWith("Locked.") && prompt.contains("[LMB]"), prompt);
            assertFalse(prompt.contains("take it apart"), "a closed door only comes down open: " + prompt);
            if (door.block().requiresTool) {
                assertTrue(prompt.contains("A pry tool forces it"), "the tool the pack names: " + prompt);
            } else {
                assertTrue(prompt.contains("or go around"), prompt);
            }
            plainWords(prompt);
        }
    }

    @Test
    void cityContainersSayWhatTheyAreWhoseAndWhatSearchingCostsWithoutTouchingThem() {
        Game g = city();
        BuildingPlan neighbour = RefugeWorldArena.neighbour(g);
        BuildingPlan.Fixture cupboard = RefugeWorldArena.piece(neighbour, 0, BlockType.CABINET);
        String first = promptAt(g, cupboard.target(), cupboard.position());
        assertTrue(first.startsWith("[F] Search the ") && first.contains("(noisy, about "), first);
        assertNull(g.world.urban.containers.at(cupboard.position()), "a prompt rolls and records nothing");
        assertFalse(first.contains("·"), "standing, the line is the search alone: " + first);
        plainWords(first);
        g.player.crouching = true;
        String crouched = promptAt(g, cupboard.target(), cupboard.position());
        assertTrue(crouched.startsWith(first) && crouched.contains("hold LMB for "), "crouching shows its salvage: " + crouched);
        g.player.crouching = false;

        assertTrue(g.openCrateAt(cupboard.position()));
        SurvivorWorldArena.run(g, .6f);
        g.closeScreens();
        String partial = promptAt(g, cupboard.target(), cupboard.position());
        assertTrue(partial.startsWith("[F] Keep searching the ") && partial.contains("% searched)"), partial);
        assertTrue(g.openCrateAt(cupboard.position()));
        SurvivorWorldArena.run(g, 5f);
        g.closeScreens();
        String searched = promptAt(g, cupboard.target(), cupboard.position());
        assertTrue(searched.startsWith("[F] Open the ") && (searched.contains("(searched)") || searched.contains("(empty)")),
                searched);

        BuildingPlan refuge = g.world.urban.buildingById(g.world.urban.refugeBuilding);
        BuildingPlan.Fixture home = refuge.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.CONTAINER
                && f.position().y() == 41).findFirst().orElseThrow();
        String own = promptAt(g, home.target(), home.position());
        assertTrue(own.contains("your refuge"), "storage in a claimed storey is the refuge's: " + own);

        SurvivorSites.Site camp = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        SurvivorWorldArena.standAt(g, camp.entry());
        SurvivorWorldArena.slowTick(g);
        Vec3i stash = camp.stash().getFirst();
        BuildingPlan held = g.world.urban.buildingAt(stash.x(), stash.z());
        Vec3i front = held.fixtures().stream().filter(f -> f.position().equals(stash)).findFirst().orElseThrow().target();
        String theirs = promptAt(g, front, stash);
        assertTrue(theirs.contains("camp stores: theft"), "a camp's stash warns before it is touched: " + theirs);
        plainWords(theirs);
        for (String p : List.of(partial, searched, own)) { plainWords(p); }
    }

    @Test
    void theNewLinesFitTheWiderPromptBoxAndAnswersWrapInConversation() {
        ByteBuffer data = ResourceManager.readBuffer("fonts/SourceSans3-Semibold.ttf");
        try (STBTTFontinfo info = STBTTFontinfo.malloc()) {
            assertTrue(stbtt_InitFont(info, data));
            ToDoubleFunction<String> body = measure(info, 12 * 1.6f);
            // A keyed line loses its "[F] " to the key cap (66 px); a plain line is centred in the box.
            float keyed = HudLayout.PROMPT_WIDTH - 66 - HudLayout.PAD, plain = HudLayout.PROMPT_WIDTH - HudLayout.PAD * 2;
            for (String line : List.of("Search the dispensary cabinet (noisy, about 4 s)",
                    "Keep searching the kitchen cupboard (60% searched)", "Open the supply cupboard (camp stores: theft)")) {
                assertTrue(body.applyAsDouble(line) <= keyed, "fits beside its key: " + line);
            }
            for (String line : List.of("Locked. Hold [LMB] to force it (loud), or go around",
                    "Locked. A pry tool forces it: hold [LMB] (loud)", "Barred from inside. A pry tool forces it: hold [LMB]")) {
                assertTrue(body.applyAsDouble(line) <= plain, "fits the box: " + line);
            }
            ToDoubleFunction<String> answer = measure(info, 12 * 1.4f);
            String said = "There's an old relay mast on a rooftop in the Civic Precinct north-east of here. Nobody's had"
                    + " the parts to make it talk again.";
            List<String> lines = com.veylon.ui.NpcScreen.responseLines(said, 540, 80, answer);
            assertTrue(lines.size() >= 2 && lines.size() <= 4, lines.toString());
            for (String line : lines) { assertTrue(answer.applyAsDouble(line) <= 540, "inside the panel: " + line); }
            assertEquals('"' + said + '"', String.join(" ", lines), "the whole answer is shown");
        }
    }

    private static ToDoubleFunction<String> measure(STBTTFontinfo info, float pixels) {
        float scale = stbtt_ScaleForPixelHeight(info, pixels);
        return text -> {
            int[] advance = new int[1], bearing = new int[1];
            double width = 0;
            int previous = -1;
            for (int cp : text.codePoints().toArray()) {
                stbtt_GetCodepointHMetrics(info, cp, advance, bearing);
                width += advance[0] * scale;
                if (previous >= 0) width += stbtt_GetCodepointKernAdvance(info, previous, cp) * scale;
                previous = cp;
            }
            return width;
        };
    }
}
