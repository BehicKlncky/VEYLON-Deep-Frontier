package com.veylon;

import com.veylon.ai.InfectedAI;
import com.veylon.ai.Pathfinder;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.CraftingSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.Station;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.NpcArchetype;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.VoxelClearance;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.UrbanFortifications;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static com.veylon.RefugeWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Fortification in a generator-17 city (urban milestone 18), on the abandoned apartment beside the
 * starter refuge and through the production commands: barricades made from salvage and put up
 * with the use button stop bodies, sight and light and are loud to put up; a Breacher hammers a
 * barricaded doorway down blow by blow while a Veilbound waits outside, and walks round half of
 * one; a brace takes a door's blows first and comes down with its bar; repairs mend, hang doors
 * and glaze windows; a misplaced barricade comes down from either side; Creative builds for free
 * and is never hunted; and damage survives a save and a load.
 */
class UrbanFortificationTest {
    private static final float DT = 0.05f;
    @TempDir Path directory;

    /** The neighbour apartment's street entrance: its two door leaves, bottom cells. */
    private static List<Vec3i> streetDoor(BuildingPlan b) {
        return b.fixtures().stream().filter(f -> f.kind() == BuildingPlan.FixtureKind.DOOR && f.position().y() == 41
                        && f.target().z() < f.position().z() && f.position().z() == b.parcel().z0())
                .map(BuildingPlan.Fixture::position).toList();
    }

    /** Forces a door leaf open with the player's own break: both of its cells become a broken doorway. */
    private static void force(Game g, Vec3i leaf) {
        assertTrue(g.blockActions.completePlayerBlockBreak(leaf), "forced " + leaf);
        assertSame(BlockType.BROKEN_DOOR, g.world.getBlock(leaf.x(), leaf.y() + 1, leaf.z()));
    }

    /** The use button, aimed at a cell, with the current hand. */
    private static void use(Game g, Vec3i cell) {
        aim(g, cell);
        g.interactions.rightClick();
    }

    private static Npc spawn(Game g, NpcArchetype archetype, Vec3i cell) {
        Npc n = g.entities.spawnNpc(g.world, archetype.displayName, cell.x() + .5f, cell.y(), cell.z() + .5f);
        n.archetype = archetype;
        n.health = n.maxHealth = archetype.maxHealth;
        return n;
    }

    /** Barricades every cell of the neighbour's street doorway, from inside, with crafted barricades. */
    private static List<Vec3i> barricadeStreetDoor(Game g, BuildingPlan b) {
        List<Vec3i> leaves = streetDoor(b);
        assertEquals(2, leaves.size(), "a double street door");
        standAt(g, leaves.getFirst().offset(0, 0, 2));
        for (Vec3i leaf : leaves) { force(g, leaf); }
        holdItem(g, ItemType.BARRICADE, 4);
        List<Vec3i> cells = List.of(leaves.get(0), leaves.get(0).offset(0, 1, 0), leaves.get(1), leaves.get(1).offset(0, 1, 0));
        for (Vec3i c : cells) {
            use(g, c);
            assertSame(BlockType.BARRICADE, g.world.getBlock(c.x(), c.y(), c.z()), "boarded at " + c);
        }
        return cells;
    }

    @Test
    void aShatteredWindowIsBoardedUpWithASalvagedBarricadeThatStopsBodiesSightAndLightLoudly() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture window = fixture(b, 0, f -> f.kind() == BuildingPlan.FixtureKind.WINDOW);
        Vec3i pane = window.position();
        standAt(g, window.target());
        assertTrue(g.blockActions.completePlayerBlockBreak(pane));
        assertSame(BlockType.BROKEN_WINDOW, g.world.getBlock(pane.x(), pane.y(), pane.z()));

        // Made by hand from what salvage recovers.
        g.player.inventory.clear();
        g.player.inventory.add(ItemType.PLANK, 3);
        g.player.inventory.add(ItemType.SCRAP, 1);
        Recipe recipe = CraftingSystem.recipesFor(g.world).stream().filter(r -> r.result == ItemType.BARRICADE)
                .findFirst().orElseThrow();
        assertEquals(Station.HAND, recipe.station);
        assertNotNull(CraftingSystem.craft(g.player.inventory, recipe, Set.of(Station.HAND), Set.of()));
        assertEquals(1, g.player.inventory.count(ItemType.BARRICADE));
        assertEquals(0, g.player.inventory.count(ItemType.PLANK));
        g.player.hotbarSel = slotOf(g, ItemType.BARRICADE);

        aim(g, pane);
        g.updatePrompt();
        assertEquals("[RMB] Board up the shattered window (loud)", g.interactPrompt);
        int heard = g.noise.countCategory("barricade");
        use(g, pane);
        assertSame(BlockType.BARRICADE, g.world.getBlock(pane.x(), pane.y(), pane.z()), "boarded into the empty frame itself");
        assertEquals(BlockType.BARRICADE.id(), g.world.changedBlocks.get(pane), "a recorded edit");
        assertEquals(0, g.player.inventory.count(ItemType.BARRICADE), "it was used up");
        assertTrue(g.noise.countCategory("barricade") > heard, "nailing it up is heard");
        assertTrue(VoxelClearance.collides(g.world, pane.x() + .5f, pane.y() - 0.4f, pane.z() + .5f, .6f, 1.8f, false),
                "a body cannot pass");
        assertFalse(Pathfinder.standable(g.world, pane.x(), pane.y(), pane.z()));
        assertTrue(g.world.getBlock(pane.x(), pane.y(), pane.z()).opaque, "it blocks light");
        Vec3i outside = new Vec3i(2 * pane.x() - window.target().x(), pane.y(), pane.z());
        assertFalse(InfectedAI.clearSight(g.world, outside.x() + .5f, pane.y() + .5f, outside.z() + .5f,
                window.target().x() + .5f, pane.y() + .5f, window.target().z() + .5f), "nobody sees in");
        g.player.hotbarSel = 8;
        aim(g, pane);
        g.updatePrompt();
        assertTrue(g.interactPrompt.startsWith("Plank Barricade (60/60)"), g.interactPrompt);
    }

    private static int slotOf(Game g, ItemType type) {
        for (int i = 0; i < g.player.inventory.size(); i++) {
            ItemStack s = g.player.inventory.get(i);
            if (s != null && s.type == type) { return i; }
        }
        throw new AssertionError("no " + type);
    }

    @Test
    void aBreacherHammersABarricadedDoorwayDownBlowByBlowWhileAVeilboundWaitsOutside() {
        for (NpcArchetype archetype : new NpcArchetype[]{NpcArchetype.BREACHER, NpcArchetype.VEILBOUND}) {
            Game g = session(GameMode.SURVIVAL);
            g.entities.npcs.clear();
            BuildingPlan b = neighbour(g);
            lockRearDoor(g, b);
            List<Vec3i> cells = barricadeStreetDoor(g, b);
            Vec3i inside = cells.getFirst().offset(0, 0, 2);
            standAt(g, inside);
            Npc n = spawn(g, archetype, cells.getFirst().offset(0, 0, -2));
            UrbanFortifications f = g.world.urban.fortifications;
            float firstDamage = -1, gaveWay = -1;
            for (int tick = 0; tick < 900 && gaveWay < 0; tick++) {
                if (tick % 30 == 0) { g.emitPlayerFootstepNoise(false, false); }
                g.entities.fastTick(g, DT);
                g.noise.update(DT);
                for (Vec3i c : cells) {
                    if (f.health(BlockType.BARRICADE, c) < UrbanFortifications.PLANK_STRENGTH && firstDamage < 0) {
                        firstDamage = tick * DT;
                    }
                    if (g.world.getBlock(c.x(), c.y(), c.z()) != BlockType.BARRICADE && gaveWay < 0) {
                        gaveWay = tick * DT;
                    }
                }
            }
            if (archetype == NpcArchetype.VEILBOUND) {
                for (Vec3i c : cells) {
                    assertSame(BlockType.BARRICADE, g.world.getBlock(c.x(), c.y(), c.z()), "a Veilbound never breaks in");
                    assertEquals(UrbanFortifications.PLANK_STRENGTH, f.health(BlockType.BARRICADE, c));
                }
                assertTrue(n.pos.z < cells.getFirst().z(), "it waits outside: " + n.pos);
                continue;
            }
            assertTrue(firstDamage >= 0, "the Breacher hammered at the barricade");
            assertTrue(gaveWay > 0, "and broke one cell of it");
            float blows = UrbanFortifications.PLANK_STRENGTH / com.veylon.Fortification.BREACHER_BLOW;
            assertTrue(gaveWay - firstDamage >= (blows - 1) * com.veylon.ai.BreacherAI.BLOW_SECONDS - 0.06f,
                    "a barricade holds for its blows: " + (gaveWay - firstDamage));
            assertEquals(1, cells.stream().filter(c -> g.world.getBlock(c.x(), c.y(), c.z()) != BlockType.BARRICADE).count(),
                    "one cell at a time");
        }
    }

    @Test
    void aVeilboundWalksRoundHalfABarricadedDoorwayWithoutTouchingIt() {
        Game g = session(GameMode.SURVIVAL);
        g.entities.npcs.clear();
        BuildingPlan b = neighbour(g);
        List<Vec3i> leaves = streetDoor(b);
        standAt(g, leaves.getFirst().offset(0, 0, 2));
        for (Vec3i leaf : leaves) { force(g, leaf); }
        holdItem(g, ItemType.BARRICADE, 2);
        Vec3i boarded = leaves.getFirst();
        use(g, boarded);
        use(g, boarded.offset(0, 1, 0));
        Vec3i inside = leaves.get(1).offset(0, 0, 3);
        standAt(g, inside);
        Npc n = spawn(g, NpcArchetype.VEILBOUND, boarded.offset(0, 0, -3));
        boolean through = false;
        for (int tick = 0; tick < 400; tick++) {
            if (tick % 20 == 0) { g.emitPlayerFootstepNoise(false, false); }
            g.entities.fastTick(g, DT);
            g.noise.update(DT);
            through |= n.pos.z > boarded.z() + 1;
        }
        assertTrue(through, "it came in through the open half: " + n.pos);
        assertSame(BlockType.BARRICADE, g.world.getBlock(boarded.x(), boarded.y(), boarded.z()));
        assertEquals(UrbanFortifications.PLANK_STRENGTH, g.world.urban.fortifications.health(BlockType.BARRICADE, boarded),
                "nobody laid a hand on it");
    }

    /**
     * The first ground-floor room door of the apartment whose planned interaction cell lies in its
     * room, with the hall cell just outside it and a cell well inside the room.
     */
    private record RoomDoor(Vec3i door, Vec3i hall, Vec3i room) { }

    private static RoomDoor roomDoor(BuildingPlan b) {
        BuildingPlan.Fixture d = fixture(b, 0, f -> f.kind() == BuildingPlan.FixtureKind.DOOR
                && f.position().x() != f.target().x() && f.position().z() == f.target().z()
                && f.position().z() > b.parcel().z0() && f.position().z() < b.parcel().z1());
        Vec3i door = d.position();
        int out = Integer.signum(door.x() - d.target().x()); // from the room towards the hall
        return new RoomDoor(door, door.offset(out, 0, 0), door.offset(-3 * out, 0, 0));
    }

    /** Locks both leaves of the apartment's rear door, so the street door is the only way in. */
    private static void lockRearDoor(Game g, BuildingPlan b) {
        for (BuildingPlan.Fixture f : b.fixtures()) {
            if (f.kind() == BuildingPlan.FixtureKind.DOOR && f.position().y() == 41
                    && f.position().z() == b.parcel().z1() && f.target().z() > f.position().z()) {
                for (int y = 0; y < 2; y++) {
                    assertTrue(g.world.fixtures.set(f.position().x(), f.position().y() + y, f.position().z(), FixtureStates.LOCK_KEYED));
                }
            }
        }
    }

    @Test
    void aBraceTakesABreachersBlowsBeforeTheDoorDoesAndComesDownWithItsBar() {
        Game g = session(GameMode.SURVIVAL);
        g.entities.npcs.clear();
        BuildingPlan b = neighbour(g);
        RoomDoor rd = roomDoor(b);
        Vec3i door = rd.door();
        standAt(g, rd.hall());
        holdItem(g, ItemType.DOOR_BRACE, 1);
        aim(g, door);
        g.updatePrompt();
        assertEquals("[RMB] Brace the door from this side (loud)", g.interactPrompt);
        use(g, door);
        assertEquals(FixtureStates.LOCK_BARRED, g.world.fixtureLockAt(door.x(), door.y(), door.z()), "barred from the hall");
        assertEquals(UrbanFortifications.BRACE_STRENGTH, g.world.urban.fortifications.brace(g.world, door));
        assertEquals(0, g.player.inventory.count(ItemType.DOOR_BRACE));

        // From the hall the bar lifts and the brace comes back; set it again for the attack.
        g.player.inventory.clear();
        g.player.hotbarSel = 1;
        aim(g, door);
        g.updatePrompt();
        assertTrue(g.interactPrompt.startsWith("[F] Take down the brace and lift the bar"), g.interactPrompt);
        g.interactions.interact();
        assertEquals(FixtureStates.LOCK_NONE, g.world.fixtureLockAt(door.x(), door.y(), door.z()));
        assertEquals(-1f, g.world.urban.fortifications.brace(g.world, door));
        assertEquals(1, g.player.inventory.count(ItemType.DOOR_BRACE), "a sound brace goes back into the pack");
        g.player.hotbarSel = slotOf(g, ItemType.DOOR_BRACE);
        use(g, door);
        assertNull(Pathfinder.find(g.world, rd.room().x(), rd.room().y(), rd.room().z(),
                rd.hall().x(), rd.hall().y(), rd.hall().z(), Pathfinder.DEFAULT_BUDGET), "the room's only way out is the door");

        Npc breacher = spawn(g, NpcArchetype.BREACHER, rd.room());
        int absorbedFirst = 0;
        float forcedAt = -1;
        for (int tick = 0; tick < 1200 && forcedAt < 0; tick++) {
            if (tick % 30 == 0) { g.emitPlayerFootstepNoise(false, false); }
            g.entities.fastTick(g, DT);
            g.noise.update(DT);
            if (g.world.getBlock(door.x(), door.y(), door.z()) == BlockType.BROKEN_DOOR) {
                forcedAt = tick * DT;
            } else if (breacher.breachBlows == 0 && breacher.breachStruck > absorbedFirst) {
                absorbedFirst = breacher.breachStruck;
            }
        }
        assertTrue(forcedAt > 0, "the Breacher broke through in the end");
        int braceBlows = (int) Math.ceil(UrbanFortifications.BRACE_STRENGTH / com.veylon.Fortification.BREACHER_BLOW);
        assertTrue(absorbedFirst >= braceBlows, "the brace took " + absorbedFirst + " blows before the door took any");
        assertEquals(-1f, g.world.urban.fortifications.brace(g.world, door), "a forced door keeps no brace");
    }

    @Test
    void fromTheFarSideABracedDoorHoldsAndThePlayersOwnBlowsGoIntoItsBrace() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        RoomDoor rd = roomDoor(b);
        Vec3i door = rd.door();
        standAt(g, rd.hall());
        holdItem(g, ItemType.DOOR_BRACE, 1);
        use(g, door);
        standAt(g, rd.room());
        g.player.hotbarSel = 1;
        aim(g, door);
        g.updatePrompt();
        assertTrue(g.interactPrompt.startsWith("Braced from inside."), g.interactPrompt);
        assertTrue(g.blockActions.completePlayerBlockBreak(door));
        assertSame(BlockType.WOOD_DOOR, g.world.getBlock(door.x(), door.y(), door.z()), "the brace took the blow");
        assertEquals(UrbanFortifications.BRACE_STRENGTH - com.veylon.Fortification.PLAYER_BLOW,
                g.world.urban.fortifications.brace(g.world, door), 1e-4f);
        assertTrue(g.blockActions.completePlayerBlockBreak(door));
        assertTrue(g.blockActions.completePlayerBlockBreak(door), "the brace splintered; this blow forces the door");
        assertSame(BlockType.BROKEN_DOOR, g.world.getBlock(door.x(), door.y(), door.z()));
    }

    @Test
    void repairsMendABarricadeAndABraceHangADoorAndGlazeAWindow() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        List<Vec3i> cells = barricadeStreetDoor(g, b);
        Vec3i cell = cells.getFirst();
        UrbanFortifications f = g.world.urban.fortifications;
        f.damage(BlockType.BARRICADE, cell, 45f);
        assertEquals(15f, f.health(BlockType.BARRICADE, cell));
        holdItem(g, ItemType.PLANK, 3);
        aim(g, cell);
        g.updatePrompt();
        assertEquals("[RMB] Repair the barricade (15/60)", g.interactPrompt);
        use(g, cell);
        assertEquals(45f, f.health(BlockType.BARRICADE, cell));
        use(g, cell);
        assertEquals(UrbanFortifications.PLANK_STRENGTH, f.health(BlockType.BARRICADE, cell), "mended to full");
        assertEquals(1, g.player.inventory.count(ItemType.PLANK), "a plank a patch");
        assertEquals(0, f.damagedCount(), "a sound barricade is not remembered");

        // A salvaged door hangs in a broken doorway; a window fills a shattered frame.
        RoomDoor rd = roomDoor(b);
        standAt(g, rd.hall());
        force(g, rd.door());
        holdItem(g, ItemType.WOOD_DOOR, 1);
        use(g, rd.door().offset(0, 1, 0));
        assertSame(BlockType.WOOD_DOOR, g.world.getBlock(rd.door().x(), rd.door().y(), rd.door().z()));
        assertSame(BlockType.WOOD_DOOR, g.world.getBlock(rd.door().x(), rd.door().y() + 1, rd.door().z()));
        assertEquals(0, g.player.inventory.count(ItemType.WOOD_DOOR));
        BuildingPlan.Fixture window = fixture(b, 0, x -> x.kind() == BuildingPlan.FixtureKind.WINDOW);
        standAt(g, window.target());
        assertTrue(g.blockActions.completePlayerBlockBreak(window.position()));
        holdItem(g, ItemType.WINDOW, 1);
        use(g, window.position());
        assertSame(BlockType.WINDOW, g.world.getBlock(window.position().x(), window.position().y(), window.position().z()));

        // A damaged brace takes a plank from its own side.
        standAt(g, rd.hall());
        holdItem(g, ItemType.DOOR_BRACE, 1);
        use(g, rd.door());
        f.damageBrace(g.world, rd.door(), 50f);
        holdItem(g, ItemType.PLANK, 1);
        use(g, rd.door());
        assertEquals(70f, f.brace(g.world, rd.door()), 1e-4f);
    }

    @Test
    void aMisplacedBarricadeComesDownFromEitherSideWithMostOfWhatItCost() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        List<Vec3i> cells = barricadeStreetDoor(g, b);
        g.player.inventory.clear();
        g.player.hotbarSel = 0;
        // From outside, standing, with bare hands.
        standAt(g, cells.getFirst().offset(0, 0, -2));
        aim(g, cells.getFirst());
        hold(g, 1.6f);
        assertSame(BlockType.AIR, g.world.getBlock(cells.getFirst().x(), cells.getFirst().y(), cells.getFirst().z()));
        assertEquals(2, g.player.inventory.count(ItemType.PLANK), "a sound barricade gives back two of its three planks");
        // A battered one gives back less.
        Vec3i second = cells.get(2);
        g.world.urban.fortifications.damage(BlockType.BARRICADE, second, 40f);
        standAt(g, second.offset(0, 0, 2));
        aim(g, second);
        hold(g, 1.6f);
        assertSame(BlockType.AIR, g.world.getBlock(second.x(), second.y(), second.z()));
        assertEquals(3, g.player.inventory.count(ItemType.PLANK));
        assertEquals(0, g.world.urban.fortifications.damagedCount(), "its damage went with it");
    }

    @Test
    void creativeFortifiesForFreeAndNoBreacherComesForItsBuilder() {
        Game g = session(GameMode.CREATIVE);
        g.entities.npcs.clear();
        BuildingPlan b = neighbour(g);
        List<Vec3i> leaves = streetDoor(b);
        standAt(g, leaves.getFirst().offset(0, 0, 2));
        for (Vec3i leaf : leaves) {
            for (int y = 0; y < 2; y++) { g.world.setBlock(leaf.x(), leaf.y() + y, leaf.z(), BlockType.BROKEN_DOOR, true); }
        }
        holdItem(g, ItemType.BARRICADE, 1);
        for (Vec3i leaf : leaves) {
            use(g, leaf);
            use(g, leaf.offset(0, 1, 0));
        }
        assertEquals(1, g.player.inventory.count(ItemType.BARRICADE), "Creative spends nothing");
        Npc breacher = spawn(g, NpcArchetype.BREACHER, leaves.getFirst().offset(0, 0, -2));
        for (int tick = 0; tick < 300; tick++) {
            g.emitPlayerFootstepNoise(true, false);
            g.entities.fastTick(g, DT);
            g.noise.update(DT);
        }
        assertNull(breacher.breachDoor, "a Creative builder is never heard");
        assertEquals(0, g.world.urban.fortifications.damagedCount());
    }

    @Test
    void battleDamageOnBarricadesAndBracesSurvivesASaveAndALoad() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        List<Vec3i> cells = barricadeStreetDoor(g, b);
        RoomDoor rd = roomDoor(b);
        standAt(g, rd.hall());
        holdItem(g, ItemType.DOOR_BRACE, 1);
        use(g, rd.door());
        UrbanFortifications f = g.world.urban.fortifications;
        f.damage(BlockType.BARRICADE, cells.get(1), 30f);
        f.damageBrace(g.world, rd.door(), 30f);
        Path file = directory.resolve("fortified.sav");
        assertTrue(SaveSystem.save(g, file));
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file), "load " + cycle);
            UrbanFortifications loaded = g.world.urban.fortifications;
            assertNotSame(f, loaded, "a load brings a fresh ledger with its world");
            g.world.ensureChunks(cells.get(1).x(), cells.get(1).z(), 1, 100);
            for (Vec3i c : cells) { assertSame(BlockType.BARRICADE, g.world.getBlock(c.x(), c.y(), c.z())); }
            assertEquals(30f, loaded.health(BlockType.BARRICADE, cells.get(1)), 1e-4f, "its damage held");
            assertEquals(UrbanFortifications.PLANK_STRENGTH, loaded.health(BlockType.BARRICADE, cells.get(0)));
            assertEquals(60f, loaded.brace(g.world, rd.door()), 1e-4f, "and the brace's");
            assertEquals(FixtureStates.LOCK_BARRED, g.world.fixtureLockAt(rd.door().x(), rd.door().y(), rd.door().z()));
        }
    }
}
