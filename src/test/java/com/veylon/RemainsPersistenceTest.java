package com.veylon;

import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.NpcAppearance;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remains a real lethal blast leaves survive saving and loading through
 * the game's own paths: an animal's one harvest record gives exactly one
 * animal's meat, hide and arrows however the harvest is split across saves
 * and loads, and the player's remains stay where the player fell whatever the
 * player does next, and never come apart a second time.
 */
class RemainsPersistenceTest {

    private static final float KEG_POWER = 3.8f;
    private static final float FEET = 40.1f;
    private static final float BX = 320.5f, BZ = 320.5f;

    @TempDir
    Path directory;

    @Test
    void anAnimalBlownApartIsHarvestedOnceHoweverItsHarvestIsSplitAcrossSaves() {
        Game g = arena();
        Creature wolf = g.entities.spawnCreature(g.world, CreatureType.WOLF, BX + 2.5f, FEET, BZ);
        wolf.stuckArrows = 3;
        wolf.stuckArrowType = ItemType.IRON_ARROW;
        g.entities.spawnCreature(g.world, CreatureType.DEER, BX - 2.5f, FEET, BZ);
        g.explosions.explode(g, BX, FEET + 0.5f, BZ, KEG_POWER, 30f, 0f, true, true);
        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        settle(g);
        assertTrue(g.entities.creatures.isEmpty(), "precondition: the keg killed both animals");
        assertEquals(2, g.entities.carcasses.size(), "precondition: one record per animal");
        assertTrue(g.entities.carcasses.stream().allMatch(Carcass::fragmented));

        Path untouched = save(g, "untouched.sav");
        Game first = load(untouched);
        Carcass record = wolfRecord(first);
        assertEquals(3, record.stuckArrows, "the wolf's arrows survive the save");
        assertSame(ItemType.IRON_ARROW, record.stuckArrowType);
        standAt(first, record);
        int meat = first.player.inventory.count(ItemType.RAW_MEAT);
        assertTrue(first.interactions.interactWithNearbyCarcass(), "a loaded record can be harvested");
        assertEquals(meat + 1, first.player.inventory.count(ItemType.RAW_MEAT), "bare hands tear off one piece");
        assertEquals(CreatureType.WOLF.meatYield - 1, record.meatLeft);
        Path torn = save(first, "torn.sav");

        for (int load = 0; load < 3; load++) {
            Game again = load(torn);
            assertEquals(2, again.entities.carcasses.size(), "load " + load + ": the same two records, no more");
            Carcass r = wolfRecord(again);
            assertEquals(CreatureType.WOLF.meatYield - 1, r.meatLeft, "load " + load + ": the meat already taken stays taken");
            assertEquals(CreatureType.WOLF.hideYield, r.hideLeft);
            assertEquals(3, r.stuckArrows);
            assertTrue(r.fragmented(), "load " + load + ": still the wolf's pieces, never a whole wolf too");
            assertEquals(FragmentAnatomy.of(CreatureType.WOLF).pieces.size(), pieces(again, BodyFamily.WOLF));
        }

        Game second = load(torn);
        Carcass rest = wolfRecord(second);
        standAt(second, rest);
        second.player.inventory.add(ItemType.BONE_KNIFE, 1);
        int meatBefore = second.player.inventory.count(ItemType.RAW_MEAT);
        int hideBefore = second.player.inventory.count(ItemType.HIDE);
        int arrowsBefore = second.player.inventory.count(ItemType.IRON_ARROW);
        assertTrue(second.interactions.interactWithNearbyCarcass());
        assertEquals(meatBefore + CreatureType.WOLF.meatYield - 1, second.player.inventory.count(ItemType.RAW_MEAT),
                "skinning gives the meat that was left, not a whole wolf's");
        assertEquals(hideBefore + CreatureType.WOLF.hideYield, second.player.inventory.count(ItemType.HIDE));
        assertEquals(arrowsBefore + 3, second.player.inventory.count(ItemType.IRON_ARROW),
                "and the arrows lodged in the living wolf");
        BodyFragment torso = rest.remains;
        second.entities.tickWorldDetritus(second, 1f);
        assertFalse(second.entities.carcasses.contains(rest), "the emptied record leaves the world");
        assertNull(torso.harvest, "and lets go of its torso");

        Game third = load(save(second, "skinned.sav"));
        assertEquals(1, third.entities.carcasses.size(), "only the deer's record is left");
        assertSame(CreatureType.DEER, third.entities.carcasses.getFirst().type);
        assertEquals(FragmentAnatomy.of(CreatureType.WOLF).pieces.size(), pieces(third, BodyFamily.WOLF),
                "the wolf's pieces still lie there");
        assertTrue(third.fragments.settled.stream()
                        .filter(f -> f.definition.family == BodyFamily.WOLF).allMatch(f -> f.harvest == null),
                "and none of them carries a reward any more");
    }

    @Test
    void thePlayersRemainsStayWhereThePlayerFellAndNeverComeApartTwice() {
        Game g = arena();
        g.appState = Game.AppState.PLAYING;
        g.player.pos.set(BX, FEET, BZ);
        g.explosions.explode(g, BX + 1f, FEET + 0.9f, BZ, KEG_POWER, 30f, 0f, true, true);
        assertTrue(g.player.dead && g.player.dismemberOnDeath, "precondition: the keg killed the player");
        g.enterDeathIfDue();
        assertEquals(Game.AppState.DEATH, g.appState);
        assertEquals(BodyFragment.Piece.values().length, g.fragments.liveCount());

        // Saved while the death screen is up: the save lays the remains down.
        Game whileDead = load(save(g, "dead.sav"));
        List<BodyFragment> remains = List.copyOf(g.fragments.settled);
        assertEquals(remains.size(), whileDead.fragments.settledCount(), "the remains are saved once");
        assertPlayerRemains(whileDead, remains);
        whileDead.appState = Game.AppState.PLAYING;
        whileDead.player.tickNeeds(whileDead, SimulationScheduler.FAST_DT);
        assertTrue(whileDead.player.dead, "precondition: the loaded player is still dead");
        whileDead.enterDeathIfDue();
        assertEquals(0L, whileDead.fragments.totalSpawned,
                "the blast record is not saved, so the loaded death does not come apart again");
        assertEquals(remains.size(), whileDead.fragments.settledCount());

        // Saved after respawning: the player is elsewhere and well; the remains are not.
        g.respawn();
        g.appState = Game.AppState.PLAYING;
        Game alive = load(save(g, "respawned.sav"));
        assertFalse(alive.player.dead);
        assertEquals(g.player.health, alive.player.health, 0f, "the player comes back as respawned");
        assertEquals(g.player.pos.x, alive.player.pos.x, 0f);
        assertEquals(g.player.pos.z, alive.player.pos.z, 0f);
        assertTrue(alive.player.pos.distance(BX, FEET, BZ) > 5f, "precondition: the respawn is away from the blast");
        assertPlayerRemains(alive, remains);
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static void assertPlayerRemains(Game loaded, List<BodyFragment> expected) {
        assertEquals(expected.size(), loaded.fragments.settledCount());
        for (int i = 0; i < expected.size(); i++) {
            BodyFragment was = expected.get(i);
            BodyFragment is = loaded.fragments.settled.get(i);
            assertSame(was.piece, is.piece, "piece " + i);
            assertEquals(was.pos.x, is.pos.x, 0f, is.piece + " stays where it fell");
            assertEquals(was.pos.y, is.pos.y, 0f);
            assertEquals(was.pos.z, is.pos.z, 0f);
            assertNull(is.appearance.archetype, "the player's plain look");
            assertEquals(NpcAppearance.NEUTRAL_CAMP_INDEX, is.appearance.campIndex, "no camp badge");
            assertTrue(is.pos.distance(BX, FEET, BZ) < 12f, is.piece + " lies about the spot the player fell");
        }
    }

    private static Carcass wolfRecord(Game g) {
        return g.entities.carcasses.stream().filter(c -> c.type == CreatureType.WOLF).findFirst().orElseThrow();
    }

    private static long pieces(Game g, BodyFamily family) {
        return g.fragments.settled.stream().filter(f -> f.definition.family == family).count();
    }

    private static void standAt(Game g, Carcass c) {
        g.player.pos.set(c.pos);
        assertSame(c, g.entities.nearestCarcass(c.pos.x, c.pos.y, c.pos.z, 2.6f),
                "precondition: the nearest record within reach is " + c.type + "'s");
    }

    private Path save(Game g, String name) {
        Path path = directory.resolve(name);
        assertTrue(SaveSystem.save(g, path), "save " + name);
        return path;
    }

    private static Game load(Path path) {
        Game g = new Game();
        assertTrue(SaveSystem.load(g, path), "load " + path.getFileName());
        return g;
    }

    private static void settle(Game g) {
        for (int i = 0; i < 1200 && g.fragments.liveCount() > 0; i++) {
            g.fragments.update(g, 1f / 60f);
        }
        assertEquals(0, g.fragments.liveCount(), "precondition: every piece has settled");
    }

    private static Game arena() {
        Game game = new Game();
        game.newWorld(777L, true);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        game.player.pos.set(300.5f, FEET, 300.5f);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.world.campPos = null;
        return game;
    }
}
