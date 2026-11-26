package com.veylon.save;

import com.veylon.Game;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.world.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 0.9.1 B05: the arrows lodged in a carcass are part of its record in the core body, whole or blown
 * apart. Before, only the link of a body blown apart to its torso piece carried them, so a whole
 * carcass came back from a save without the arrows its skinning would have returned.
 */
class CarcassArrowsSaveTest {

    private static final long SEED = 20_261_126L;
    /** An arrow count no other field of the test's save holds next to an arrow item. */
    private static final int ARROWS = 77;

    @TempDir
    Path dir;

    @Test
    void aWholeCarcassKeepsItsArrowsThroughASaveAndGivesThemBackWhenSkinned() throws Exception {
        Game g = deerShotDead(3);
        Path file = dir.resolve("whole-carcass.sav");
        assertTrue(SaveSystem.save(g, file), "the save settles the falling body into its carcass");
        Carcass whole = deerCarcass(g);
        assertFalse(whole.fragmented(), "precondition: a whole body");
        assertEquals(3, whole.stuckArrows, "precondition: the arrows went from the deer to its carcass");

        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        Carcass back = deerCarcass(loaded);
        assertFalse(back.fragmented());
        assertEquals(3, back.stuckArrows, "the lodged arrows come back with the carcass");
        assertSame(ItemType.IRON_ARROW, back.stuckArrowType, "and so does their kind");

        loaded.player.inventory.set(0, new ItemStack(ItemType.IRON_KNIFE, 1));
        loaded.player.hotbarSel = 0;
        loaded.player.pos.set(back.pos.x + 0.6f, back.pos.y, back.pos.z);
        int before = loaded.player.inventory.count(ItemType.IRON_ARROW);
        assertTrue(loaded.interactWithNearbyCarcass(), "F skins it");
        assertEquals(before + 3, loaded.player.inventory.count(ItemType.IRON_ARROW),
                "skinning returns the arrows the save kept");
        assertEquals(0, back.stuckArrows);

        Path again = dir.resolve("skinned.sav");
        assertTrue(SaveSystem.save(loaded, again));
        Game reloaded = new Game();
        assertTrue(SaveSystem.load(reloaded, again));
        assertEquals(0, deerCarcass(reloaded).stuckArrows, "taken arrows stay taken");
    }

    @Test
    void aMalformedArrowCountOrKindInTheRecordFailsTheWholeLoadAndLeavesTheLiveWorld() throws Exception {
        Game g = deerShotDead(ARROWS);
        Path file = dir.resolve("arrows.sav");
        assertTrue(SaveSystem.save(g, file));
        byte[] saved = Files.readAllBytes(file);
        int item = ItemType.IRON_ARROW.ordinal() + 1;
        int at = onlyOffsetOf(saved, ARROWS, item);

        Map<String, byte[]> corrupt = new LinkedHashMap<>();
        corrupt.put("negative arrows", edited(saved, at, -1, item));
        corrupt.put("more arrows than any animal holds", edited(saved, at, SaveSystem.MAX_STUCK_ARROWS + 1, item));
        corrupt.put("an arrow item past the enum", edited(saved, at, ARROWS, ItemType.values().length + 1));
        corrupt.put("a negative arrow item", edited(saved, at, ARROWS, -1));

        Files.write(file, SaveSeal.resealed(edited(saved, at, SaveSystem.MAX_STUCK_ARROWS, 0)));
        Game probe = new Game();
        assertTrue(SaveSystem.load(probe, file), "control: the most arrows a record holds, of no kind, load");
        assertEquals(SaveSystem.MAX_STUCK_ARROWS, deerCarcass(probe).stuckArrows);
        assertNull(deerCarcass(probe).stuckArrowType);

        var world = g.world;
        List<Carcass> carcasses = new ArrayList<>(g.entities.carcasses);
        for (Map.Entry<String, byte[]> c : corrupt.entrySet()) {
            Files.write(file, SaveSeal.resealed(c.getValue()));
            assertFalse(SaveSystem.load(g, file), c.getKey() + " fails the load");
            assertSame(world, g.world, c.getKey() + ": the live world stays");
            assertEquals(carcasses, g.entities.carcasses, c.getKey() + ": and its carcasses");
            assertEquals(ARROWS, deerCarcass(g).stuckArrows, c.getKey() + ": with their arrows");
        }
    }

    /**
     * A wilderness world whose deer took {@code arrows} iron arrows (the count the projectile system
     * keeps on a creature it hits) and was then killed by the player, through the ordinary death
     * path: the fast tick credits the kill and lets the body fall.
     */
    private Game deerShotDead(int arrows) {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_CAVE_IDENTITIES, GameMode.SURVIVAL);
        g.entities.creatures.clear();
        g.entities.carcasses.clear();
        Creature deer = g.entities.spawnCreature(g.world, CreatureType.DEER,
                g.player.pos.x + 3f, g.player.pos.y + 0.5f, g.player.pos.z);
        deer.stuckArrows = arrows;
        deer.stuckArrowType = ItemType.IRON_ARROW;
        deer.killBy(true);
        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        assertFalse(g.entities.creatures.contains(deer), "precondition: the death went through the fast tick");
        return g;
    }

    private static Carcass deerCarcass(Game g) {
        List<Carcass> deer = g.entities.carcasses.stream().filter(c -> c.type == CreatureType.DEER).toList();
        assertEquals(1, deer.size(), "one deer carcass");
        return deer.getFirst();
    }

    /** The one offset where the save holds {@code count} followed by {@code item}. */
    private static int onlyOffsetOf(byte[] save, int count, int item) {
        ByteBuffer b = ByteBuffer.wrap(save);
        int found = -1;
        for (int i = 0; i + 8 <= save.length; i++) {
            if (b.getInt(i) == count && b.getInt(i + 4) == item) {
                assertEquals(-1, found, "the arrows' fields are unique in the save");
                found = i;
            }
        }
        assertTrue(found > SaveHeader.LENGTH, "the carcass record holds its arrows");
        return found;
    }

    private static byte[] edited(byte[] save, int at, int count, int item) {
        byte[] copy = save.clone();
        ByteBuffer.wrap(copy).putInt(at, count).putInt(at + 4, item);
        return copy;
    }
}
