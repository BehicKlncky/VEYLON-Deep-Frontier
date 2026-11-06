package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.item.CraftingSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.UrbanSite;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.SalvageTable;
import com.veylon.world.urban.SurvivorSites;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanStarterSite;
import com.veylon.world.urban.VehicleSites;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static com.veylon.RefugeWorldArena.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Taking things apart in a generator-17 city (urban milestone 18), through the production mining
 * step and its completion command: furniture, doors, metal and wrecks come apart for materials
 * after steady, loud work, faster with the right tool and less when worn; containers give up
 * their contents once whether searched first or not; a full pack refuses; survivors' furniture is
 * theirs; Creative takes nothing; generator 16 keeps carrying furniture away whole; and what is
 * taken apart stays gone through a save and a load.
 */
class UrbanSalvageTest {
    @TempDir Path directory;

    private static Map<ItemType, Integer> counts(Inventory inv) {
        Map<ItemType, Integer> out = new EnumMap<>(ItemType.class);
        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.get(i);
            if (s != null) { out.merge(s.type, s.count, Integer::sum); }
        }
        return out;
    }

    private static Map<ItemType, Integer> gained(Map<ItemType, Integer> before, Inventory after) {
        Map<ItemType, Integer> out = new EnumMap<>(ItemType.class);
        counts(after).forEach((type, n) -> {
            int d = n - before.getOrDefault(type, 0);
            if (d != 0) { out.put(type, d); }
        });
        return out;
    }

    private static Map<ItemType, Integer> of(List<ItemStack> stacks) {
        Map<ItemType, Integer> out = new EnumMap<>(ItemType.class);
        for (ItemStack s : stacks) { out.merge(s.type, s.count, Integer::sum); }
        return out;
    }

    private static void crouchAt(Game g, BuildingPlan.Fixture f) {
        standAt(g, f.target());
        aim(g, f.position());
        g.player.crouching = true;
    }

    @Test
    void aTableComesApartForPlanksAfterSteadyLoudWorkAndAnAxeHalvesTheTime() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture table = piece(b, 0, BlockType.TABLE);
        Vec3i pos = table.position();
        crouchAt(g, table);
        Map<ItemType, Integer> before = counts(g.player.inventory);
        List<ItemStack> expected = SalvageTable.yields(g.world, pos, BlockType.TABLE);
        assertEquals(ItemType.PLANK, expected.getFirst().type);

        hold(g, 2.5f);
        assertSame(BlockType.TABLE, g.world.getBlock(pos.x(), pos.y(), pos.z()), "three seconds of work by hand");
        assertTrue(g.noise.countCategory("salvage") > 0, "every blow is heard");
        hold(g, 0.7f);
        assertSame(BlockType.AIR, g.world.getBlock(pos.x(), pos.y(), pos.z()), "taken apart");
        assertEquals(of(expected), gained(before, g.player.inventory), "planks, after its wear, and nothing else");
        assertEquals(BlockType.AIR.id(), g.world.changedBlocks.get(pos), "the table is gone as a recorded edit");

        BuildingPlan.Fixture second = fixture(b, 0, f -> f.block() == BlockType.TABLE && !f.position().equals(pos));
        holdItem(g, ItemType.IRON_AXE, 1);
        crouchAt(g, second);
        float durability = g.player.selected().durability;
        hold(g, 1.6f);
        assertSame(BlockType.AIR, g.world.getBlock(second.position().x(), second.position().y(), second.position().z()),
                "an axe halves the work");
        assertEquals(durability - 1, g.player.selected().durability, 1e-4f, "and wears for it");
    }

    @Test
    void metalNeedsAPryToolAndComesApartWithWhatTheLockerHeld() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture locker = piece(b, 0, BlockType.LOCKER);
        Vec3i pos = locker.position();
        crouchAt(g, locker);
        hold(g, 7f);
        assertSame(BlockType.LOCKER, g.world.getBlock(pos.x(), pos.y(), pos.z()), "bare hands do not strip metal");
        assertEquals("Needs a pry tool to strip the metal locker",
                g.blockActions.dismantling.prompt(pos, BlockType.LOCKER));

        holdItem(g, ItemType.IRON_PICKAXE, 1);
        Map<ItemType, Integer> before = counts(g.player.inventory);
        Map<ItemType, Integer> expected = of(SalvageTable.yields(g.world, pos, BlockType.LOCKER));
        Inventory held = UrbanStarterSite.supplies(b, locker);
        assertNotNull(held, "a starter neighbour's ground-floor locker keeps its reserved supplies");
        for (int i = 0; i < held.size(); i++) {
            if (held.get(i) != null) { expected.merge(held.get(i).type, held.get(i).count, Integer::sum); }
        }
        hold(g, 3.1f);
        assertSame(BlockType.AIR, g.world.getBlock(pos.x(), pos.y(), pos.z()));
        assertEquals(expected, gained(before, g.player.inventory),
                "a never-searched locker gives up what it held once, beside its scrap and sheet metal");
        assertSame(UrbanContainers.State.MOVED, g.world.urban.containers.byId(locker.id()).state, "its identity retired");
        assertNull(g.world.crateContents.get(pos));
    }

    @Test
    void searchingEmptyingAndThenTakingApartACupboardPaysItsContentsOnce() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture cabinet = piece(b, 0, BlockType.CABINET);
        Vec3i pos = cabinet.position();
        standAt(g, cabinet.target());
        assertTrue(g.openCrateAt(pos));
        for (int i = 0; i < 200 && g.crates.searching(); i++) { g.crates.fastTick(0.05f); }
        assertFalse(g.crates.searching(), "the search finished");
        Map<ItemType, Integer> beforeTaking = counts(g.player.inventory);
        Inventory inside = g.world.crateContents.get(pos);
        Map<ItemType, Integer> contents = counts(inside);
        assertFalse(contents.isEmpty(), "the cupboard held something");
        for (int slot = 0; slot < inside.size(); slot++) { g.transferCrateItemToPlayer(slot); }
        g.closeScreens();
        assertEquals(contents, gained(beforeTaking, g.player.inventory));

        Map<ItemType, Integer> beforeTakingApart = counts(g.player.inventory);
        Map<ItemType, Integer> materials = of(SalvageTable.yields(g.world, pos, BlockType.CABINET));
        aim(g, pos);
        g.player.crouching = true;
        hold(g, 4.1f);
        assertSame(BlockType.AIR, g.world.getBlock(pos.x(), pos.y(), pos.z()));
        assertEquals(materials, gained(beforeTakingApart, g.player.inventory),
                "only its wood and fittings: what it held was taken once already");

        // Something built there later is the player's own empty storage, never the cupboard again.
        holdItem(g, ItemType.CABINET, 1);
        assertTrue(g.blockActions.placeSelectedBlockAt(pos.x(), pos.y(), pos.z()));
        UrbanContainers.Entry placed = g.world.urban.containers.touch(g.world, pos);
        assertEquals(UrbanContainers.Owner.PLAYER, placed.owner);
        assertTrue(g.world.crateContents.get(pos) == null || g.world.crateContents.get(pos).isEmpty());
    }

    @Test
    void aFullPackRefusesAndMovesNothing() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture cabinet = piece(b, 0, BlockType.CABINET);
        Vec3i pos = cabinet.position();
        for (int i = 0; i < g.player.inventory.size(); i++) {
            g.player.inventory.set(i, new ItemStack(ItemType.STONE, ItemType.STONE.maxStack));
        }
        crouchAt(g, cabinet);
        assertFalse(g.blockActions.completePlayerDismantle(pos), "nowhere to put what it gives");
        assertSame(BlockType.CABINET, g.world.getBlock(pos.x(), pos.y(), pos.z()));
        UrbanContainers.Entry entry = g.world.urban.containers.byId(cabinet.id());
        assertNotSame(UrbanContainers.State.MOVED, entry.state, "the cupboard keeps its identity");
        assertNotNull(g.world.crateContents.get(pos), "and what it holds");
        assertTrue(g.eventLog.all().getLast().contains("No room"), g.eventLog.all().getLast());
    }

    @Test
    void aWreckGivesPartsFromItsEngineSeatFabricFromItsCabinAndScrapFromItsBody() {
        Game g = session(GameMode.SURVIVAL);
        VehicleSites.Wreck wreck = nearestWreck(g);
        Vec3i engine = null, cabin = null, body = null;
        for (Vec3i c : wreck.cells()) {
            switch (wreck.partAt(c.x(), c.y(), c.z())) {
                case ENGINE -> engine = engine == null ? c : engine;
                case CABIN -> cabin = cabin == null ? c : cabin;
                case BODY -> body = body == null ? c : body;
            }
        }
        standAt(g, new Vec3i(wreck.x0() - 1, VehicleSites.FEET, wreck.z0() - 1));
        assertFalse(g.blockActions.completePlayerDismantle(engine), "no pry tool, no parts");
        holdItem(g, ItemType.IRON_PICKAXE, 1);
        Map<ItemType, Integer> before = counts(g.player.inventory);
        assertTrue(g.blockActions.completePlayerDismantle(engine));
        assertTrue(gained(before, g.player.inventory).getOrDefault(ItemType.SPARE_PARTS, 0) >= 1, "the engine gives parts");
        before = counts(g.player.inventory);
        assertTrue(g.blockActions.completePlayerDismantle(cabin));
        assertEquals(!wreck.burnt(), gained(before, g.player.inventory).containsKey(ItemType.FIBER),
                "seat fabric unless the cabin burnt");
        before = counts(g.player.inventory);
        assertTrue(g.blockActions.completePlayerDismantle(body));
        assertTrue(gained(before, g.player.inventory).getOrDefault(ItemType.SCRAP, 0) >= 1, "the body gives scrap");
        for (Vec3i c : List.of(engine, cabin, body)) {
            assertSame(BlockType.AIR, g.world.getBlock(c.x(), c.y(), c.z()));
            assertEquals(BlockType.AIR.id(), g.world.changedBlocks.get(c));
        }
        // A wreck has no form to carry away: a standing hold takes it apart too.
        Vec3i next = wreck.cells().stream().filter(c -> g.world.getBlock(c.x(), c.y(), c.z()) == BlockType.CAR_WRECK)
                .findFirst().orElseThrow();
        g.player.crouching = false;
        assertTrue(g.blockActions.dismantling.applies(next, BlockType.CAR_WRECK));
    }

    @Test
    void takingApartACampsFurnitureWrecksWhatIsTheirs() {
        Game g = session(GameMode.SURVIVAL);
        SurvivorSites.Site plan = SurvivorWorldArena.nearest(g, SurvivorSites.Kind.CAMP);
        BuildingPlan camp = SurvivorWorldArena.building(g, plan);
        BuildingPlan.Fixture bed = fixture(camp, 0, f -> f.block() == BlockType.CAMP_BED);
        standAt(g, bed.target());
        UrbanSite site = g.settlementManager.urbanSites.site(g, plan.id());
        assertNotNull(site);
        float trust = site.trust();
        assertEquals(camp.id(), SalvageTable.survivorOwner(g.world, bed.position()), "a held walk-up's bed is the camp's");
        assertTrue(g.blockActions.completePlayerDismantle(bed.position()));
        assertTrue(site.trust() <= trust - 19.9f, "taking their bed apart wrecks what is theirs: " + trust + " -> " + site.trust());

        // Abandoned furniture belongs to nobody.
        BuildingPlan.Fixture table = piece(neighbour(g), 0, BlockType.TABLE);
        assertEquals(com.veylon.world.urban.UrbanIds.NONE, SalvageTable.survivorOwner(g.world, table.position()));
    }

    @Test
    void creativeRemovesAPieceOutrightAndTakesNothingFromIt() {
        Game g = session(GameMode.CREATIVE);
        BuildingPlan.Fixture table = piece(neighbour(g), 0, BlockType.TABLE);
        crouchAt(g, table);
        Map<ItemType, Integer> before = counts(g.player.inventory);
        assertFalse(g.blockActions.dismantling.applies(table.position(), BlockType.TABLE), "Creative builds and breaks freely");
        hold(g, RefugeWorldArena.DT);
        assertSame(BlockType.AIR, g.world.getBlock(table.position().x(), table.position().y(), table.position().z()));
        assertEquals(before, counts(g.player.inventory), "no salvage in Creative");
        assertFalse(g.blockActions.completePlayerDismantle(table.position()));
    }

    @Test
    void generatorSixteenStillCarriesFurnitureAwayWholeAndKeepsItsRecipes() {
        Game g = SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, World.GEN_URBAN_STRONGHOLDS,
                new com.veylon.engine.AudioManager());
        BuildingPlan.Fixture table = piece(neighbour(g), 0, BlockType.TABLE);
        crouchAt(g, table);
        assertNull(SalvageTable.entry(g.world, table.position(), BlockType.TABLE), "no salvage table before layout fourteen");
        int tables = g.player.inventory.count(ItemType.TABLE);
        hold(g, 1.6f);
        assertEquals(tables + 1, g.player.inventory.count(ItemType.TABLE), "the table is carried away whole");
        assertTrue(CraftingSystem.recipesFor(g.world).stream().anyMatch(r -> r.ingredients.containsKey(ItemType.TABLE)));

        Game refuges = session(GameMode.SURVIVAL);
        assertTrue(CraftingSystem.recipesFor(refuges.world).stream().noneMatch(r -> r.ingredients.containsKey(ItemType.TABLE)),
                "generator 17 breaks furniture down only where it stands");
        // Standing, generator 17 still carries furniture away whole.
        BuildingPlan.Fixture other = piece(neighbour(refuges), 0, BlockType.TABLE);
        standAt(refuges, other.target());
        aim(refuges, other.position());
        int before = refuges.player.inventory.count(ItemType.TABLE);
        hold(refuges, 1.6f);
        assertEquals(before + 1, refuges.player.inventory.count(ItemType.TABLE));
    }

    @Test
    void theHudSaysWhatTakingAPieceApartGivesAndThatCrouchingDoesIt() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan.Fixture table = piece(neighbour(g), 0, BlockType.TABLE);
        standAt(g, table.target());
        aim(g, table.position());
        g.updatePrompt();
        assertEquals("[LMB] Hold to carry it away  ·  crouch and hold LMB to take it apart", g.interactPrompt);
        g.player.crouching = true;
        g.updatePrompt();
        String expected = SalvageTable.describe(SalvageTable.yields(g.world, table.position(), BlockType.TABLE));
        assertTrue(g.interactPrompt.startsWith("[LMB] Hold to take apart the ")
                && g.interactPrompt.contains(" table for " + expected), g.interactPrompt);

        // A search keeps its F line, and the dismantle follows it in short, to fit the HUD's one line.
        BuildingPlan.Fixture cabinet = piece(neighbour(g), 0, BlockType.CABINET);
        standAt(g, cabinet.target());
        aim(g, cabinet.position());
        g.updatePrompt();
        assertEquals("[F] Search cabinet  ·  hold LMB for "
                + SalvageTable.describe(SalvageTable.yields(g.world, cabinet.position(), BlockType.CABINET)), g.interactPrompt);

        VehicleSites.Wreck wreck = nearestWreck(g);
        g.player.crouching = false;
        aim(g, wreck.cells().getFirst());
        g.updatePrompt();
        assertTrue(g.interactPrompt.startsWith("Needs a pry tool to strip the wrecked car"),
                "the salvage line replaces the generic tool hint: " + g.interactPrompt);
    }

    @Test
    void piecesTakenApartStayGoneThroughASaveAndALoad() {
        Game g = session(GameMode.SURVIVAL);
        BuildingPlan b = neighbour(g);
        BuildingPlan.Fixture cabinet = piece(b, 0, BlockType.CABINET);
        VehicleSites.Wreck wreck = nearestWreck(g);
        Vec3i cell = wreck.cells().getFirst();
        standAt(g, cabinet.target());
        assertTrue(g.blockActions.completePlayerDismantle(cabinet.position()));
        holdItem(g, ItemType.IRON_PICKAXE, 1);
        standAt(g, new Vec3i(wreck.x0() - 1, VehicleSites.FEET, wreck.z0() - 1));
        assertTrue(g.blockActions.completePlayerDismantle(cell));
        Map<ItemType, Integer> carried = counts(g.player.inventory);
        Path file = directory.resolve("salvage.sav");
        assertTrue(SaveSystem.save(g, file));
        for (int cycle = 0; cycle < 2; cycle++) {
            assertTrue(SaveSystem.load(g, file), "load " + cycle);
            g.world.ensureChunks(cabinet.position().x(), cabinet.position().z(), 1, 100);
            g.world.ensureChunks(cell.x(), cell.z(), 1, 100);
            assertSame(BlockType.AIR, g.world.getBlock(cabinet.position().x(), cabinet.position().y(), cabinet.position().z()));
            assertSame(BlockType.AIR, g.world.getBlock(cell.x(), cell.y(), cell.z()), "the wreck stays stripped");
            assertSame(UrbanContainers.State.MOVED, g.world.urban.containers.byId(cabinet.id()).state,
                    "the cupboard's identity stays retired, so nothing refills");
            assertNull(g.world.crateContents.get(cabinet.position()));
            assertEquals(carried, counts(g.player.inventory));
        }
    }
}
