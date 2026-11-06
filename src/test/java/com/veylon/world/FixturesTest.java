package com.veylon.world;

import com.veylon.item.Inventory;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Urban package 04: fixture families, the state map, its world hooks and the shared thin geometry. */
class FixturesTest {

    private static final int Y = 41;

    /** A loaded street-network chunk cleared to air above a concrete floor. */
    private static World arena() {
        World w = new World(20260731L, World.GEN_URBAN_STREETS);
        for (int cx = -1; cx <= 0; cx++) {
            for (int cz = -1; cz <= 0; cz++) {
                w.getOrCreateChunk(cx, cz);
            }
        }
        for (int x = -16; x < 16; x++) {
            for (int z = -16; z < 16; z++) {
                w.setBlock(x, Y - 1, z, BlockType.CONCRETE, true);
                for (int y = Y; y < Y + 6; y++) {
                    w.setBlock(x, y, z, BlockType.AIR, true);
                }
            }
        }
        return w;
    }

    /** A door in a wall that runs along x, at (0, Y, 0). */
    private static void doorInWall(World w, BlockType door) {
        for (int y = Y; y < Y + 3; y++) {
            w.setBlock(-1, y, 0, BlockType.BRICK, true);
            w.setBlock(1, y, 0, BlockType.BRICK, true);
        }
        w.setBlock(0, Y, 0, door, true);
        w.setBlock(0, Y + 1, 0, door, true);
        w.setBlock(0, Y + 2, 0, BlockType.BRICK, true);
    }

    @Test
    void everyFixtureKindHasAClosedOpenAndBrokenFormThatAgreeOnCollision() {
        for (Fixtures.Kind k : Fixtures.Kind.values()) {
            assertSame(k, Fixtures.kindOf(k.closed));
            assertSame(k, Fixtures.kindOf(k.open));
            assertNull(Fixtures.kindOf(k.broken), "broken remains are not operable");
            assertTrue(k.closed.solid, k + " closed is a wall");
            assertFalse(k.open.solid, k + " open is passable");
            assertFalse(k.broken.solid, k + " broken is passable");
            assertSame(BlockType.Shape.LEAF, k.open.shape);
            assertSame(BlockType.Shape.PANE, k.broken.shape);
            assertTrue(Fixtures.isFixture(k.broken));
        }
        assertTrue(BlockType.WINDOW.solid && !BlockType.WINDOW.opaque, "glass: solid, seen through");
        assertTrue(Fixtures.isClosedDoor(BlockType.WOOD_DOOR) && !Fixtures.isClosedDoor(BlockType.WINDOW));
        assertTrue(Fixtures.isContainer(BlockType.CRATE) && !Fixtures.isUrbanContainer(BlockType.CRATE));
        assertTrue(Fixtures.isUrbanContainer(BlockType.CABINET));
    }

    @Test
    void stateBytesFollowTheirRules() {
        assertTrue(FixtureStates.isValid(0));
        assertTrue(FixtureStates.isValid(FixtureStates.OPEN));
        assertTrue(FixtureStates.isValid(FixtureStates.LOCK_KEYED));
        assertTrue(FixtureStates.isValid(FixtureStates.LOCK_BARRED | FixtureStates.BAR_POSITIVE
                | FixtureStates.BAR_AXIS_Z));
        assertFalse(FixtureStates.isValid(FixtureStates.OPEN | FixtureStates.LOCK_BARRED), "open is never locked");
        assertFalse(FixtureStates.isValid(FixtureStates.LOCK_MASK), "reserved lock value");
        assertFalse(FixtureStates.isValid(FixtureStates.BAR_POSITIVE), "a side needs a bar");
        assertFalse(FixtureStates.isValid(0x20));
        assertFalse(FixtureStates.isValid(-1));
    }

    @Test
    void theStateMapIsBoundedOnInsertAndStillUpdatesWhatItHolds() {
        FixtureStates s = new FixtureStates();
        for (int i = 0; i < FixtureStates.MAX_ENTRIES; i++) {
            assertTrue(s.set(i % 512, 41 + i / (512 * 512), (i / 512) % 512, 0));
        }
        assertEquals(FixtureStates.MAX_ENTRIES, s.size());
        assertFalse(s.canHold(10_000, 41, 10_000));
        assertFalse(s.set(10_000, 41, 10_000, FixtureStates.OPEN), "a new cell over the cap is refused");
        assertTrue(s.set(0, 41, 0, FixtureStates.OPEN), "a held cell still changes");
        assertEquals(FixtureStates.OPEN, s.stateAt(0, 41, 0));
        s.remove(0, 41, 0);
        assertEquals(FixtureStates.MAX_ENTRIES - 1, s.size());
        assertTrue(s.set(10_000, 41, 10_000, FixtureStates.OPEN));
    }

    @Test
    void anOpeningIsAStackOfOneKindAtMostThreeCellsTall() {
        World w = arena();
        for (int y = Y; y < Y + 5; y++) {
            w.setBlock(4, y, 4, BlockType.WINDOW, true);
        }
        w.setBlock(6, Y, 4, BlockType.WOOD_DOOR, true);
        w.setBlock(6, Y + 1, 4, BlockType.WOOD_DOOR_OPEN, true);
        w.setBlock(6, Y + 2, 4, BlockType.METAL_DOOR, true);
        assertEquals(Y + 2, Fixtures.stackBottom(w, 4, Y + 4, 4, Fixtures.Kind.WINDOW));
        assertEquals(3, Fixtures.stackHeight(w, 4, Y + 2, 4, Fixtures.Kind.WINDOW));
        assertEquals(Y, Fixtures.stackBottom(w, 6, Y + 1, 4, Fixtures.Kind.WOOD_DOOR));
        assertEquals(2, Fixtures.stackHeight(w, 6, Y, 4, Fixtures.Kind.WOOD_DOOR),
                "the metal door above is another opening");
    }

    @Test
    void stateFollowsItsFixtureAndDiesWithIt() {
        World w = arena();
        doorInWall(w, BlockType.WOOD_DOOR);
        w.fixtures.set(0, Y, 0, FixtureStates.OPEN);
        w.setBlock(0, Y, 0, BlockType.WOOD_DOOR_OPEN, false);
        assertEquals(FixtureStates.OPEN, w.fixtures.stateAt(0, Y, 0), "a toggle keeps the state");
        w.setBlock(0, Y, 0, BlockType.BROKEN_DOOR, true);
        assertEquals(-1, w.fixtures.stateAt(0, Y, 0), "forced: no state survives");
        w.fixtures.set(0, Y + 1, 0, FixtureStates.LOCK_KEYED);
        w.setBlock(0, Y + 1, 0, BlockType.AIR, true);
        assertEquals(-1, w.fixtures.stateAt(0, Y + 1, 0), "removed: no state survives");
    }

    @Test
    void urbanContainerContentsLeaveWithTheFurnitureButLegacyCratesKeepTheirRules() {
        World w = arena();
        Vec3i cab = new Vec3i(3, Y, 3), crate = new Vec3i(5, Y, 3);
        w.setBlock(cab.x(), cab.y(), cab.z(), BlockType.CABINET, true);
        w.setBlock(crate.x(), crate.y(), crate.z(), BlockType.CRATE, true);
        Inventory inv = new Inventory(12);
        inv.add(ItemType.BANDAGE, 2);
        w.crateContents.put(cab, inv);
        w.crateContents.put(crate, new Inventory(12));
        w.setBlock(cab.x(), cab.y(), cab.z(), BlockType.AIR, true);
        w.setBlock(crate.x(), crate.y(), crate.z(), BlockType.AIR, true);
        assertNull(w.crateContents.get(cab));
        assertTrue(w.crateContents.containsKey(crate), "the legacy crate is unchanged (D-12)");
    }

    @Test
    void aChunkGeneratedAfterLoadTakesItsSavedFixtureStateAndDropsStaleOnes() {
        World w = new World(20260731L, World.GEN_URBAN_STREETS);
        int cx = 40, cz = 40, x = cx * 16 + 5, z = cz * 16 + 5;
        assertNull(w.getChunk(cx, cz));
        // Structure that will contain a door, as a generator's slice would.
        w.genSet(x, Y, z, BlockType.WOOD_DOOR, false);
        w.genSet(x, Y + 1, z, BlockType.WOOD_DOOR, false);
        assertTrue(w.restoreFixtureState(new Vec3i(x, Y, z), FixtureStates.OPEN));
        assertTrue(w.restoreFixtureState(new Vec3i(x, Y + 1, z), FixtureStates.OPEN));
        assertTrue(w.restoreFixtureState(new Vec3i(x + 3, Y + 20, z), FixtureStates.LOCK_KEYED));
        assertNull(w.getChunk(cx, cz), "restoring state never generates a chunk");

        w.getOrCreateChunk(cx, cz);
        assertSame(BlockType.WOOD_DOOR_OPEN, w.getBlock(x, Y, z));
        assertSame(BlockType.WOOD_DOOR_OPEN, w.getBlock(x, Y + 1, z));
        assertEquals(FixtureStates.OPEN, w.fixtures.stateAt(x, Y, z));
        assertEquals(-1, w.fixtures.stateAt(x + 3, Y + 20, z), "nothing there to apply it to");
        assertEquals(2, w.fixtures.size());
    }

    @Test
    void restoringIntoALoadedChunkAppliesTheVariantOrDropsAStaleEntry() {
        World w = arena();
        doorInWall(w, BlockType.METAL_DOOR);
        assertTrue(w.restoreFixtureState(new Vec3i(0, Y, 0), FixtureStates.OPEN));
        assertSame(BlockType.METAL_DOOR_OPEN, w.getBlock(0, Y, 0));
        assertTrue(w.restoreFixtureState(new Vec3i(5, Y, 5), FixtureStates.OPEN));
        assertSame(BlockType.AIR, w.getBlock(5, Y, 5));
        assertEquals(-1, w.fixtures.stateAt(5, Y, 5));
        assertFalse(w.restoreFixtureState(new Vec3i(0, Y, 0), 0x40), "an invalid byte is refused");
    }

    @Test
    void thinFixturesSitWhereTheWallSaysAndTheRayHitsOnlyThere() {
        World w = arena();
        doorInWall(w, BlockType.WOOD_DOOR_OPEN);
        assertTrue(FixtureGeometry.wallAlongX(w, 0, Y, 0));
        float[] box = new float[6];
        assertTrue(FixtureGeometry.thinBox(w, 0, Y, 0, BlockType.WOOD_DOOR_OPEN, box));
        assertEquals(0f, box[0]);
        assertEquals(FixtureGeometry.LEAF_THICKNESS, box[3], 1e-6f);
        assertEquals(0f, box[2]);
        assertEquals(1f, box[5]);
        assertFalse(FixtureGeometry.thinBox(w, 0, Y, 0, BlockType.BRICK, box), "a cube has no thin box");

        // A pane in a wall running along z faces x.
        for (int y = Y; y < Y + 2; y++) {
            w.setBlock(8, y, -1, BlockType.BRICK, true);
            w.setBlock(8, y, 1, BlockType.BRICK, true);
        }
        w.setBlock(8, Y, 0, BlockType.BROKEN_WINDOW, true);
        assertFalse(FixtureGeometry.wallAlongX(w, 8, Y, 0));
        assertTrue(FixtureGeometry.thinBox(w, 8, Y, 0, BlockType.BROKEN_WINDOW, box));
        assertEquals(8.5f - FixtureGeometry.PANE_THICKNESS / 2, box[0], 1e-6f);
        assertEquals(8.5f + FixtureGeometry.PANE_THICKNESS / 2, box[3], 1e-6f);

        assertTrue(FixtureGeometry.rayHitsThin(w, 0, Y, 0, BlockType.WOOD_DOOR_OPEN,
                0.05, Y + 0.5, -2, 0, 0, 1, 10), "along the leaf");
        assertFalse(FixtureGeometry.rayHitsThin(w, 0, Y, 0, BlockType.WOOD_DOOR_OPEN,
                0.5, Y + 0.5, -2, 0, 0, 1, 10), "through the doorway");
        assertFalse(FixtureGeometry.rayHitsThin(w, 0, Y, 0, BlockType.WOOD_DOOR_OPEN,
                0.05, Y + 0.5, -2, 0, 0, 1, 1.5), "out of reach");
    }
}
