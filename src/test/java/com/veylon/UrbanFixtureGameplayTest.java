package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.Raycaster;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Urban package 04: doors, windows, stairs and searchable furniture through the
 * production movement, F-key, break and placement commands, in Survival and
 * Creative. Collision is checked by walking, raycasts by the targeting cast.
 */
class UrbanFixtureGameplayTest {

    private static final float EYE = 1.62f;

    @Test
    void theFrontDoorBlocksUntilOpenedAndTogglingNeverGrowsTheEditLog() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i door = a.house.frontDoor();
        a.aim(door);
        assertTrue(a.prompt().startsWith("[F] Open door"), a.prompt());

        // Closed: the solid door stops the walk at the threshold.
        assertFalse(a.walkTo(a.house.westRoom(), 3f));
        assertTrue(a.game.player.pos.z < door.z(), "a closed door is a wall");

        a.standAt(a.house.outsideFront());
        a.aim(door);
        a.pressF();
        assertSame(BlockType.METAL_DOOR_OPEN, a.block(door));
        assertSame(BlockType.METAL_DOOR_OPEN, a.above(door), "both cells of the door open together");
        assertTrue(FixtureStates.isOpen(a.game.world.fixtures.stateAt(door.x(), door.y(), door.z())));
        assertEquals(BlockType.METAL_DOOR.id(), a.game.world.changedBlocks.get(door),
                "a toggle is fixture state, not another block edit");
        assertTrue(a.game.noise.countCategory("door") > 0, "opening a door is heard");

        assertTrue(a.walkTo(a.house.westRoom(), 4f), "an open doorway lets the player through");

        a.aim(door);
        assertEquals("[F] Close door", a.prompt());
        a.pressF();
        assertSame(BlockType.METAL_DOOR, a.block(door));
        assertSame(BlockType.METAL_DOOR, a.above(door));
        assertFalse(a.walkTo(a.house.outsideFront(), 3f), "closed again, it holds from inside too");
    }

    @Test
    void theCrosshairSeesThroughAnOpenDoorwayButHitsTheLeafWhereItIsDrawn() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i door = a.house.frontDoor();
        float eyeY = door.y() + EYE;
        Raycaster.Hit closed = a.cast(door.x() + 0.5f, eyeY, door.z() - 1.5f, 0, 0, 1);
        assertNotNull(closed);
        assertEquals(door.z(), closed.z(), "a closed door is hit as its whole cell");

        a.game.interactWithBlockAt(door);
        Raycaster.Hit middle = a.cast(door.x() + 0.5f, eyeY, door.z() - 1.5f, 0, 0, 1);
        assertTrue(middle == null || middle.z() != door.z() || middle.x() != door.x(),
                "aiming through the open doorway passes the leaf");
        // The leaf hangs on the low-x jamb (the front wall runs along x).
        Raycaster.Hit leaf = a.cast(door.x() + 0.06f, eyeY, door.z() - 1.5f, 0, 0, 1);
        assertNotNull(leaf);
        assertEquals(new Vec3i(door.x(), door.y() + 1, door.z()), new Vec3i(leaf.x(), leaf.y(), leaf.z()));
        assertSame(BlockType.METAL_DOOR_OPEN, leaf.type());
    }

    @Test
    void anAimedDoorOutranksANearbyPersonForTheFKey() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i door = a.house.frontDoor();
        Npc npc = a.game.entities.spawnNpc(a.game.world, "Ilse", door.x() + 1.5f, door.y(), door.z() - 1.5f);
        assertNotNull(npc);
        assertEquals(Game.NpcInteraction.TALK, a.game.npcInteraction(npc));

        a.aim(door);
        assertTrue(a.prompt().startsWith("[F] Open door"), "the prompt names the door, not the person");
        a.pressF();
        assertSame(BlockType.METAL_DOOR_OPEN, a.block(door));
        assertNotEquals(Game.UiMode.NPC, a.game.uiMode);

        a.game.targetHit = null;
        assertTrue(a.prompt().startsWith("[F] Talk to"), "without a door in the crosshair she is talkable");
    }

    @Test
    void aLockedDoorRefusesEntryUntilForcedAndForcingIsLoudAndPermanent() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i door = a.house.frontDoor();
        a.game.world.fixtures.set(door.x(), door.y(), door.z(), FixtureStates.LOCK_KEYED);
        a.game.world.fixtures.set(door.x(), door.y() + 1, door.z(), FixtureStates.LOCK_KEYED);

        a.aim(door);
        assertTrue(a.prompt().startsWith("Locked. A pry tool forces it: hold [LMB]"), a.prompt());
        a.pressF();
        assertSame(BlockType.METAL_DOOR, a.block(door), "F on a locked door is a failed entry");
        assertEquals(1, a.game.noise.countCategory("door-rattle"), "trying the handle is heard");
        assertFalse(a.walkTo(a.house.westRoom(), 3f));

        assertFalse(a.game.completePlayerBlockBreak(door), "a metal door cannot be forced bare-handed");
        a.hold(ItemType.IRON_PICKAXE);
        assertTrue(a.game.completePlayerBlockBreak(door));
        assertSame(BlockType.BROKEN_DOOR, a.block(door));
        assertSame(BlockType.BROKEN_DOOR, a.above(door), "the whole door is forced");
        assertEquals(BlockType.BROKEN_DOOR.id(), a.game.world.changedBlocks.get(door), "forcing is permanent");
        assertEquals(-1, a.game.world.fixtures.stateAt(door.x(), door.y(), door.z()), "no lock outlives the door");
        assertEquals(0, a.game.player.inventory.count(ItemType.METAL_DOOR), "a forced door is not salvage");
        assertEquals(1, a.game.noise.countCategory("door-forced"));
        a.standAt(a.house.outsideFront());
        assertTrue(a.walkTo(a.house.westRoom(), 4f), "a broken doorway is passable");
        a.aim(door);
        assertEquals("Broken doorway", a.prompt());
    }

    @Test
    void aBarHoldsAgainstTheOtherSideAndLiftsOnlyFromItsOwn() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i door = a.house.innerDoor();
        a.standAt(a.house.westRoom());
        a.game.player.crouching = true;
        a.aim(door);
        assertEquals("[F] Bar the door from this side", a.prompt());
        a.pressF();
        assertEquals(FixtureStates.LOCK_BARRED, a.game.world.fixtures.lockAt(door.x(), door.y(), door.z()));

        a.standAt(a.house.eastRoom());
        a.aim(door);
        assertTrue(a.prompt().startsWith("Barred from inside. Hold [LMB] to force it"), a.prompt());
        a.pressF();
        assertSame(BlockType.WOOD_DOOR, a.block(door));

        a.standAt(a.house.westRoom());
        a.aim(door);
        assertEquals("[F] Lift the bar", a.prompt());
        a.pressF();
        assertSame(BlockType.WOOD_DOOR, a.block(door), "lifting the bar does not open the door");
        a.pressF();
        assertSame(BlockType.WOOD_DOOR_OPEN, a.block(door));
    }

    @Test
    void creativeCrouchFTogglesAKeyedLockForStagingButKeepsTheRules() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.CREATIVE);
        Vec3i door = a.house.frontDoor();
        a.game.player.crouching = true;
        a.aim(door);
        assertEquals("[F] Lock door (Creative)", a.prompt());
        a.pressF();
        assertEquals(FixtureStates.LOCK_KEYED, a.game.world.fixtures.lockAt(door.x(), door.y() + 1, door.z()));
        a.game.player.crouching = false;
        a.pressF();
        assertSame(BlockType.METAL_DOOR, a.block(door), "Creative meets a lock like anyone else");
        assertTrue(a.game.completePlayerBlockBreak(door));
        assertSame(BlockType.AIR, a.block(door), "a Creative break removes the door outright");
        assertSame(BlockType.AIR, a.above(door));
        assertEquals(0, a.game.noise.countCategory("door-forced"), "Creative is not heard");
    }

    @Test
    void anOpenWindowLetsThePlayerClimbOverTheSillIntoTheRoom() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i window = a.house.window();
        a.standAt(a.house.outsideWindow());
        assertFalse(a.walkTo(a.house.eastRoom(), 3f), "glass is solid");

        a.standAt(a.house.outsideWindow());
        a.aim(window);
        assertTrue(a.prompt().startsWith("[F] Open window"), a.prompt());
        a.pressF();
        assertSame(BlockType.WINDOW_OPEN, a.block(window));
        assertSame(BlockType.WINDOW_OPEN, a.above(window));
        assertTrue(a.walkTo(a.house.eastRoom(), 6f), "over the one-block sill and in");
        a.settle(0.5f);
        assertEquals(a.house.eastRoom().y(), a.game.player.pos.y, 0.05f, "back on the floor inside");
    }

    @Test
    void shatteringAPaneIsLoudCutsABareHandAndOpensAWayIn() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i pane = a.house.secondWindow();
        a.standAt(new Vec3i(pane.x(), pane.y() - 1, pane.z() - 2));
        float health = a.game.player.health;
        assertTrue(a.game.completePlayerBlockBreak(pane));
        assertSame(BlockType.BROKEN_WINDOW, a.block(pane));
        assertSame(BlockType.WINDOW, a.above(pane), "panes shatter one at a time");
        assertTrue(a.game.player.health < health, "glass cuts a bare hand");
        assertEquals(1, a.game.noise.countCategory("glass-break"));
        assertEquals(0, a.game.player.inventory.count(ItemType.WINDOW), "shattered glass is not salvage");

        a.hold(ItemType.STONE_AXE);
        float before = a.game.player.health;
        Vec3i upper = new Vec3i(pane.x(), pane.y() + 1, pane.z());
        assertTrue(a.game.completePlayerBlockBreak(upper));
        assertEquals(before, a.game.player.health, 1e-4f, "a tool keeps the hand out of the glass");
        assertTrue(a.walkTo(new Vec3i(pane.x(), pane.y() - 1, pane.z() + 2), 6f),
                "both panes gone: climb through the frame");
    }

    @Test
    void creativeBreaksGlassWithoutInjuryOrNoise() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.CREATIVE);
        Vec3i pane = a.house.window();
        float health = a.game.player.health;
        assertTrue(a.game.completePlayerBlockBreak(pane));
        assertSame(BlockType.AIR, a.block(pane));
        assertEquals(health, a.game.player.health, 1e-4f);
        assertEquals(0, a.game.noise.countCategory("glass-break"));
    }

    @Test
    void stairsCarryThePlayerToTheRoofOnFoot() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        a.standAt(a.house.stairFoot());
        Vec3i top = a.house.stairTop();
        assertTrue(a.walkTo(new Vec3i(top.x() - 1, top.y(), a.house.stairFoot().z()), 8f),
                "up four full-block steps without jumping");
        assertEquals(top.y(), a.game.player.pos.y, 0.05f, "standing on the roof slab");
        assertTrue(a.walkTo(top, 4f));
    }

    @Test
    void aCabinetIsSearchedLikeACrateAndItsContentsNeverVanishOrDouble() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i cabinet = a.house.cabinet();
        a.standAt(a.house.eastRoom());
        a.aim(cabinet);
        assertEquals("[F] Search cabinet", a.prompt());
        a.pressF();
        assertEquals(Game.UiMode.CRATE, a.game.uiMode);
        Inventory stash = a.game.openCrate;
        assertSame(stash, a.game.world.crateContents.get(cabinet));
        stash.add(ItemType.BANDAGE, 5);
        stash.add(ItemType.COOKED_MEAT, 3);
        a.game.closeScreens();

        a.fillInventory();
        assertFalse(a.game.completePlayerBlockBreak(cabinet), "no room: the cabinet stays whole");
        assertSame(BlockType.CABINET, a.block(cabinet));
        assertEquals(5, a.game.world.crateContents.get(cabinet).count(ItemType.BANDAGE));
        assertEquals(0, a.game.player.inventory.count(ItemType.BANDAGE));

        a.game.player.inventory.clear();
        assertTrue(a.game.completePlayerBlockBreak(cabinet));
        assertSame(BlockType.AIR, a.block(cabinet));
        assertNull(a.game.world.crateContents.get(cabinet));
        assertEquals(5, a.game.player.inventory.count(ItemType.BANDAGE));
        assertEquals(3, a.game.player.inventory.count(ItemType.COOKED_MEAT));
        assertEquals(1, a.game.player.inventory.count(ItemType.CABINET), "the cabinet itself, once");

        a.game.player.hotbarSel = slotOf(a.game.player.inventory, ItemType.CABINET);
        assertTrue(a.game.placeSelectedBlockAt(cabinet.x(), cabinet.y(), cabinet.z()));
        assertTrue(a.game.world.crateContents.get(cabinet).isEmpty(), "a rebuilt cabinet is empty");
    }

    @Test
    void creativeEmptiesAContainerIntoTheInventoryButGainsNoFurniture() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.CREATIVE);
        Vec3i locker = a.house.locker();
        a.game.openCrateAt(locker);
        a.game.openCrate.add(ItemType.SCRAP, 4);
        a.game.closeScreens();
        assertTrue(a.game.completePlayerBlockBreak(locker));
        assertEquals(4, a.game.player.inventory.count(ItemType.SCRAP));
        assertEquals(0, a.game.player.inventory.count(ItemType.LOCKER));
    }

    @Test
    void aBlastOrFireTakesAContainersContentsWithItSoNothingResurrects() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i shelf = a.house.shelf();
        a.game.openCrateAt(shelf);
        a.game.openCrate.add(ItemType.BANDAGE, 2);
        a.game.closeScreens();
        // The path a blast takes (ExplosionSystem) and a fire's burn-out (ASH).
        a.game.world.setBlock(shelf.x(), shelf.y(), shelf.z(), BlockType.ASH, true);
        assertNull(a.game.world.crateContents.get(shelf), "no orphaned inventory remains");
        a.game.world.setBlock(shelf.x(), shelf.y(), shelf.z(), BlockType.AIR, true);
        a.game.player.inventory.add(ItemType.SHELF, 1);
        a.game.player.hotbarSel = slotOf(a.game.player.inventory, ItemType.SHELF);
        assertTrue(a.game.placeSelectedBlockAt(shelf.x(), shelf.y(), shelf.z()));
        a.game.openCrateAt(shelf);
        assertTrue(a.game.openCrate.isEmpty(), "the burnt shelf's bandages do not come back");
    }

    @Test
    void anOpenStorageScreenStopsTradingOnceItsFurnitureIsGone() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i cabinet = a.house.cabinet();
        a.standAt(a.house.eastRoom());
        assertTrue(a.game.openCrateAt(cabinet));
        a.game.openCrate.add(ItemType.BANDAGE, 3);
        a.game.player.inventory.add(ItemType.SCRAP, 2);
        // A blast takes the cabinet while its screen is open.
        a.game.world.setBlock(cabinet.x(), cabinet.y(), cabinet.z(), BlockType.AIR, true);
        assertEquals(0, a.game.transferCrateItemToPlayer(0), "destroyed contents are not handed out");
        assertEquals(0, a.game.player.inventory.count(ItemType.BANDAGE));
        assertNotEquals(Game.UiMode.CRATE, a.game.uiMode, "the screen closes");
        assertEquals(0, a.game.transferPlayerItemToCrate(slotOf(a.game.player.inventory, ItemType.SCRAP)),
                "nothing is deposited into storage the world no longer holds");
        assertEquals(2, a.game.player.inventory.count(ItemType.SCRAP));
    }

    @Test
    void anOpenDoorDismantlesIntoExactlyOneDoorAndAFullInventoryRefuses() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i door = a.house.innerDoor();
        a.game.interactWithBlockAt(door);
        assertSame(BlockType.WOOD_DOOR_OPEN, a.block(door));

        a.fillInventory();
        assertFalse(a.game.completePlayerBlockBreak(door));
        assertSame(BlockType.WOOD_DOOR_OPEN, a.block(door), "refused, not lost");
        assertSame(BlockType.WOOD_DOOR_OPEN, a.above(door));

        a.game.player.inventory.clear();
        assertTrue(a.game.completePlayerBlockBreak(new Vec3i(door.x(), door.y() + 1, door.z())));
        assertSame(BlockType.AIR, a.block(door));
        assertSame(BlockType.AIR, a.above(door));
        assertEquals(1, a.game.player.inventory.count(ItemType.WOOD_DOOR), "two cells, one door");

        a.game.player.hotbarSel = slotOf(a.game.player.inventory, ItemType.WOOD_DOOR);
        assertTrue(a.game.placeSelectedBlockAt(door.x(), door.y(), door.z()));
        assertSame(BlockType.WOOD_DOOR, a.block(door));
        assertSame(BlockType.WOOD_DOOR, a.above(door), "a door is placed whole");
        assertEquals(0, a.game.player.inventory.count(ItemType.WOOD_DOOR));
    }

    @Test
    void aDoorNeedsRoomForBothHalves() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i spot = a.house.eastRoom();
        a.game.world.setBlock(spot.x(), spot.y() + 1, spot.z(), BlockType.CONCRETE, true);
        a.game.player.inventory.add(ItemType.WOOD_DOOR, 1);
        a.game.player.hotbarSel = slotOf(a.game.player.inventory, ItemType.WOOD_DOOR);
        a.standAt(a.house.westRoom());
        assertFalse(a.game.placeSelectedBlockAt(spot.x(), spot.y(), spot.z()));
        assertSame(BlockType.AIR, a.block(spot));
        assertEquals(1, a.game.player.inventory.count(ItemType.WOOD_DOOR));
    }

    @Test
    void aDoorCannotSpendItsItemOnAnIncompleteOrUnloadedOpening() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        a.hold(ItemType.WOOD_DOOR);
        Vec3i room = a.house.eastRoom();
        int ceiling = com.veylon.world.Chunk.SY - 1;
        assertFalse(a.game.placeSelectedBlockAt(room.x(), ceiling, room.z()),
                "the upper half must fit inside the world");
        assertSame(BlockType.AIR, a.game.world.getBlock(room.x(), ceiling, room.z()));
        assertEquals(1, a.game.player.inventory.count(ItemType.WOOD_DOOR));

        assertFalse(a.game.world.isColumnLoaded(5_000, 5_000));
        assertFalse(a.game.placeSelectedBlockAt(5_000, room.y(), 5_000));
        assertFalse(a.game.world.isColumnLoaded(5_000, 5_000), "placement never generates a column");
        assertEquals(1, a.game.player.inventory.count(ItemType.WOOD_DOOR));
    }

    @Test
    void debrisIsWalkedThroughAndRubbleBreaksDownToGravelOnly() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i debris = a.house.debris();
        a.standAt(a.house.eastRoom());
        assertTrue(a.walkTo(debris, 3f), "floor litter has no collider");
        assertTrue(a.game.completePlayerBlockBreak(debris));
        assertTrue(a.game.player.inventory.isEmpty(), "debris yields nothing");

        Vec3i rubble = new Vec3i(a.house.x0() + 2, a.house.westRoom().y(), a.house.z0() + 1);
        assertSame(BlockType.RUBBLE, a.block(rubble));
        assertTrue(a.game.completePlayerBlockBreak(rubble));
        assertEquals(1, a.game.player.inventory.count(ItemType.GRAVEL));
        assertEquals(1, countAll(a.game.player.inventory), "and only one gravel");
    }

    @Test
    void urbanMaterialsSalvageOneForOneSoPlacingAndBreakingNeverMultiplies() {
        UrbanFixtureArena a = UrbanFixtureArena.create(GameMode.SURVIVAL);
        Vec3i spot = new Vec3i(a.house.x0() + 3, a.house.westRoom().y(), a.house.z0() + 3);
        a.standAt(a.house.eastRoom());
        a.hold(ItemType.IRON_PICKAXE);
        for (ItemType item : new ItemType[]{ItemType.CONCRETE, ItemType.BRICK, ItemType.PLASTER,
                ItemType.ASPHALT, ItemType.PAVING, ItemType.SHEET_METAL, ItemType.STAIRS}) {
            int slot = (a.game.player.hotbarSel + 1) % 9;
            a.game.player.inventory.set(slot, new ItemStack(item, 1));
            int pick = a.game.player.hotbarSel;
            a.game.player.hotbarSel = slot;
            assertTrue(a.game.placeSelectedBlockAt(spot.x(), spot.y(), spot.z()), item.name());
            a.game.player.hotbarSel = pick;
            assertTrue(a.game.completePlayerBlockBreak(spot), item.name());
            assertEquals(1, a.game.player.inventory.count(item), item + " comes back once");
            a.game.player.inventory.remove(item, 1);
        }
    }

    private static int slotOf(Inventory inv, ItemType type) {
        for (int i = 0; i < inv.size(); i++) {
            if (inv.get(i) != null && inv.get(i).type == type) {
                return i;
            }
        }
        throw new AssertionError("no " + type);
    }

    private static int countAll(Inventory inv) {
        int n = 0;
        for (int i = 0; i < inv.size(); i++) {
            if (inv.get(i) != null) {
                n += inv.get(i).count;
            }
        }
        return n;
    }
}
