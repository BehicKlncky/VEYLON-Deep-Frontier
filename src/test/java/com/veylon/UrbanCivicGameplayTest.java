package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.BuildingPlan.Family;
import com.veylon.world.urban.BuildingPlan.Fixture;
import com.veylon.world.urban.BuildingPlan.FixtureKind;
import com.veylon.world.urban.BuildingPlan.Variant;
import com.veylon.world.urban.CivicCatalogue;
import com.veylon.world.urban.EncounterAnchors;
import com.veylon.world.urban.LootTables;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Milestone 12 through production commands on generated generator-11 buildings: the F sweep,
 * the break command, container search and transfer, prompts, encounters, save and load.
 * Only the player's pose is staged.
 */
class UrbanCivicGameplayTest {
    private static final long SEED = 20260731L;
    @TempDir Path directory;

    @Test
    void dispensaryMedicineCostsALoudForcedEntryAndStaysTakenAfterReload() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan clinic = find(g, b -> b.family() == Family.CLINIC
                && (b.variant() == Variant.INTACT || b.variant() == Variant.DAMAGED));
        Fixture door = securedDoor(clinic);
        assertSame(BlockType.WOOD_DOOR, block(g, door.position()));
        stand(g, outside(door));
        aim(g, door.position());
        assertTrue(prompt(g).startsWith("Locked. Hold [LMB] to force it (loud)"), prompt(g));
        g.interactions.interact();
        assertSame(BlockType.WOOD_DOOR, block(g, door.position()), "F on the dispensary lock fails");
        assertEquals(1, g.noise.countCategory("door-rattle"));

        assertTrue(g.completePlayerBlockBreak(door.position()), "a wooden lock can be forced by hand");
        assertSame(BlockType.BROKEN_DOOR, block(g, door.position()));
        assertSame(BlockType.BROKEN_DOOR, block(g, door.position().offset(0, 1, 0)));
        assertEquals(1, g.noise.countCategory("door-forced"), "forcing it is loud");
        assertEquals(BlockType.BROKEN_DOOR.id(), g.world.changedBlocks.get(door.position()));

        Fixture cabinet = clinic.fixtures().stream().filter(f -> f.kind() == FixtureKind.CONTAINER
                && UrbanIds.parent(f.id()) == UrbanIds.parent(door.id())).findFirst().orElseThrow();
        assertEquals(LootTables.Table.CLINIC, UrbanContainers.planned(g.world, cabinet.position()).table());
        stand(g, cabinet.target());
        Set<ItemType> taken = takeAll(g, cabinet.position());
        assertTrue(taken.contains(ItemType.BANDAGE), "dispensary stock: " + taken);
        assertTrue(EnumSet.of(ItemType.BANDAGE, ItemType.ANTISEPTIC, ItemType.MEDICINE, ItemType.SPLINT)
                .containsAll(taken), "only dispensary supplies: " + taken);

        Path save = directory.resolve("dispensary.sav");
        assertTrue(SaveSystem.save(g, save));
        assertTrue(SaveSystem.load(g, save));
        assertSame(BlockType.BROKEN_DOOR, block(g, door.position()), "a forced lock stays forced");
        assertTrue(g.openCrateAt(cabinet.position()));
        assertFalse(g.crateSearching());
        assertNull(g.openCrate, "searched dispensary stock never refills");
    }

    @Test
    void armouryAmmunitionNeedsAPryToolAndCreativeBreaksLocksSilently() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan post = find(g, b -> b.family() == Family.SECURITY
                && (b.variant() == Variant.INTACT || b.variant() == Variant.DAMAGED));
        Fixture door = securedDoor(post);
        stand(g, outside(door));
        aim(g, door.position());
        assertTrue(prompt(g).startsWith("Locked. A pry tool forces it: hold [LMB]"), prompt(g));
        assertFalse(g.completePlayerBlockBreak(door.position()), "bare hands cannot force a metal lock");
        g.player.inventory.set(g.player.hotbarSel, new ItemStack(ItemType.IRON_PICKAXE, 1));
        assertTrue(g.completePlayerBlockBreak(door.position()));
        assertSame(BlockType.BROKEN_DOOR, block(g, door.position()));
        Fixture locker = post.fixtures().stream().filter(f -> f.kind() == FixtureKind.CONTAINER
                && UrbanIds.parent(f.id()) == UrbanIds.parent(door.id())).findFirst().orElseThrow();
        assertEquals(LootTables.Table.SECURED, UrbanContainers.planned(g.world, locker.position()).table());
        stand(g, locker.target());
        assertTrue(EnumSet.of(ItemType.SCRAP, ItemType.MUSKET_BALL, ItemType.BLACK_POWDER, ItemType.FLINTLOCK_PISTOL)
                .containsAll(takeAll(g, locker.position())), "armoury stock");

        Game creative = session(GameMode.CREATIVE);
        BuildingPlan clinic = find(creative, b -> b.family() == Family.CLINIC
                && (b.variant() == Variant.INTACT || b.variant() == Variant.DAMAGED));
        Fixture lock = securedDoor(clinic);
        stand(creative, outside(lock));
        aim(creative, lock.position());
        creative.interactions.interact();
        assertSame(BlockType.WOOD_DOOR, block(creative, lock.position()), "Creative meets the lock like anyone");
        assertTrue(creative.completePlayerBlockBreak(lock.position()));
        assertSame(BlockType.AIR, block(creative, lock.position()), "a Creative break removes the door");
        assertEquals(0, creative.noise.countCategory("door-forced"), "Creative is not heard");
    }

    @Test
    void signsReadTheirPlanAndLockedAlternativeEntriesPointToTheOtherDoor() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan clinic = find(g, b -> b.family() == Family.CLINIC && b.variant() == Variant.INTACT);
        Vec3i sign = cell(clinic, BlockType.SIGN);
        assertSame(BlockType.SIGN, block(g, sign));
        aim(g, sign);
        String text = CivicCatalogue.signText(SEED, clinic);
        assertTrue(text.endsWith("Clinic & Pharmacy"), text);
        assertEquals("Sign: \"" + text + "\"", prompt(g));
        aim(g, cell(clinic, BlockType.CAGE_BARS));
        assertEquals("Security bars over a window", prompt(g));

        // The staff door at the back is locked; the street door opens with F.
        Fixture rear = clinic.fixtures().stream().filter(f -> f.kind() == FixtureKind.DOOR
                && UrbanIds.roomIndex(f.id()) == 0 && UrbanIds.slotIndex(f.id()) == 9).findFirst().orElseThrow();
        Fixture front = clinic.fixtures().stream().filter(f -> f.kind() == FixtureKind.DOOR
                && UrbanIds.roomIndex(f.id()) == 0 && UrbanIds.slotIndex(f.id()) == 8).findFirst().orElseThrow();
        assertTrue(rear.locked());
        stand(g, rear.target());
        aim(g, rear.position());
        assertTrue(prompt(g).startsWith("Locked. Hold [LMB] to force it (loud), or go around"), prompt(g));
        stand(g, front.target());
        aim(g, front.position());
        g.interactions.interact();
        assertSame(BlockType.WOOD_DOOR_OPEN, block(g, front.position()), "the street door is the way in");
    }

    @Test
    void aHeldStreetFloorIsSurvivorPropertyWhileUpperStoreysAreAbandoned() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan held = find(g, b -> !b.family().walkUp() && b.variant() == Variant.OCCUPIED && b.storeys() >= 2);
        Fixture ground = held.fixtures().stream().filter(f -> f.kind() == FixtureKind.CONTAINER
                && UrbanIds.floorIndex(f.id()) == 0).findFirst().orElseThrow();
        Fixture upper = held.fixtures().stream().filter(f -> f.kind() == FixtureKind.CONTAINER
                && UrbanIds.floorIndex(f.id()) == 1).findFirst().orElseThrow();
        stand(g, ground.target());
        assertTrue(g.openCrateAt(ground.position()));
        assertEquals(UrbanContainers.Owner.SURVIVOR, g.world.urban.containers.at(ground.position()).owner);
        assertEquals(held.id(), g.world.urban.containers.at(ground.position()).ownerId);
        g.closeScreens();
        stand(g, upper.target());
        assertTrue(g.openCrateAt(upper.position()));
        assertEquals(UrbanContainers.Owner.ABANDONED, g.world.urban.containers.at(upper.position()).owner,
                "the holders did not take the upper storeys");
        g.closeScreens();
        Path save = directory.resolve("held.sav");
        assertTrue(SaveSystem.save(g, save));
        assertTrue(SaveSystem.load(g, save), "partial ownership passes the container codec's plan check");
        assertEquals(UrbanContainers.Owner.SURVIVOR, g.world.urban.containers.at(ground.position()).owner);
        assertEquals(UrbanContainers.Owner.ABANDONED, g.world.urban.containers.at(upper.position()).owner);
    }

    @Test
    void aClinicLobbyGuardIsAProductionEncounterSavedUnderCatalogueTwo() {
        Game g = session(GameMode.SURVIVAL);
        EncounterAnchors.Anchor guard = null;
        for (var anchor : UrbanEncounterArena.anchors(g)) {
            BuildingPlan b = g.world.urban.buildingAt(anchor.cell().x(), anchor.cell().z());
            if (anchor.indoor() && b != null && b.family() == Family.CLINIC) { guard = anchor; break; }
        }
        assertNotNull(guard, "every unheld clinic keeps a lobby guard");
        assertEquals(0, UrbanIds.roomIndex(guard.id()), "the guard waits in the lobby by the dispensary");
        UrbanEncounterArena.observeAway(g, guard);
        Npc body = g.entities.encounters.tryActivate(g, guard);
        assertNotNull(body);
        assertTrue(body.infected());
        Path save = directory.resolve("guard.sav");
        assertTrue(SaveSystem.save(g, save));
        assertTrue(SaveSystem.load(g, save));
        assertNotNull(g.entities.encounters.resident(guard.id()), "the guard is a durable resident");
        assertEquals(2, EncounterAnchors.catalogue(g.world));
    }

    // ------------------------------------------------------------------

    static Game session(GameMode mode) {
        Game g = new Game();
        g.newWorld(SEED, true, World.GEN_URBAN_CIVIC, mode);
        g.appState = Game.AppState.PLAYING;
        g.noise.reset();
        return g;
    }

    /** The first generated building around the origin that matches, with its chunks loaded. */
    static BuildingPlan find(Game g, Predicate<BuildingPlan> wanted) {
        for (int[] r : new int[][]{{0, 0}, {-1, -1}, {1, -1}, {-1, 1}, {1, 1}, {1, 0}, {0, 1}, {-1, 0}, {0, -1}}) {
            for (var parcel : g.world.urban.regionPlan(r[0], r[1]).parcels()) {
                BuildingPlan b = g.world.urban.buildingAt(parcel.x0(), parcel.z0());
                if (wanted.test(b)) {
                    g.world.ensureChunks((parcel.x0() + parcel.x1()) / 2, (parcel.z0() + parcel.z1()) / 2, 3, 10_000);
                    return b;
                }
            }
        }
        throw new AssertionError("no matching generated building near the origin");
    }

    static Fixture securedDoor(BuildingPlan b) {
        return b.fixtures().stream().filter(f -> f.kind() == FixtureKind.DOOR && f.locked()
                && UrbanIds.slotIndex(f.id()) == 0).findFirst().orElseThrow();
    }

    /** The corridor cell in front of a door, opposite its room-side target. */
    static Vec3i outside(Fixture door) {
        Vec3i p = door.position(), t = door.target();
        return new Vec3i(2 * p.x() - t.x(), t.y(), 2 * p.z() - t.z());
    }

    static Set<ItemType> takeAll(Game g, Vec3i container) {
        assertTrue(g.openCrateAt(container));
        for (int i = 0; i < 100 && g.crateSearching(); i++) { assertTrue(g.simulates()); g.advanceWorld(.05); }
        assertFalse(g.crateSearching(), "the search finished");
        Inventory stock = g.openCrate;
        assertNotNull(stock);
        Set<ItemType> taken = EnumSet.noneOf(ItemType.class);
        for (int slot = 0; slot < stock.size(); slot++) {
            ItemStack s = stock.get(slot);
            if (s == null) { continue; }
            taken.add(s.type);
            assertTrue(g.transferCrateItemToPlayer(slot) > 0);
        }
        g.closeScreens();
        return taken;
    }

    static void stand(Game g, Vec3i cell) {
        g.player.pos.set(cell.x() + .5f, cell.y() + .001f, cell.z() + .5f);
        g.player.vel.zero();
        g.player.onGround = true;
        g.player.crouching = false;
    }

    static void aim(Game g, Vec3i cell) {
        g.targetHit = new Raycaster.Hit(cell.x(), cell.y(), cell.z(), 0, 0, -1, 2,
                g.world.getBlock(cell.x(), cell.y(), cell.z()));
    }

    static String prompt(Game g) {
        g.updatePrompt();
        return g.interactPrompt;
    }

    static BlockType block(Game g, Vec3i p) { return g.world.getBlock(p.x(), p.y(), p.z()); }

    static Vec3i cell(BuildingPlan plan, BlockType block) {
        var v = plan.voxels();
        for (int y = v.minY; y <= v.maxY; y++) {
            for (int z = v.z0; z < v.z0 + v.depth; z++) {
                for (int x = v.x0; x < v.x0 + v.width; x++) {
                    if (v.getBlock(x, y, z) == block) { return new Vec3i(x, y, z); }
                }
            }
        }
        throw new AssertionError("no " + block + " in " + plan.template());
    }
}
