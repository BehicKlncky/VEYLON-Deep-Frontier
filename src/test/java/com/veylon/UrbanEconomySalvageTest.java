package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.GameMode;
import com.veylon.item.CraftingSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.World;
import com.veylon.world.urban.BuildingPlan;
import com.veylon.world.urban.ParcelPlan;
import com.veylon.world.urban.SalvageTable;
import com.veylon.world.urban.UrbanStarterSite;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Salvage in the economy city cannot be turned into more than it is worth (urban milestone 23).
 *
 * <ul>
 *   <li>Nothing a recipe makes, put down and taken apart, gives back more of anything than the recipe
 *       spent, nor anything it did not spend. Generator 19 still lets a rebuilt workbench come apart
 *       for the workshop's spare part, minting the scarcest repair part from planks: the loop generator
 *       20 closes.</li>
 *   <li>A worn or wrecked piece comes apart under any hold, for what its wear leaves, instead of being
 *       carried off whole and set down sound somewhere else; only a sound piece is still carried away.</li>
 * </ul>
 */
class UrbanEconomySalvageTest {
    private static final long SEED = 20_260_731L;

    private static Game session(int generator) {
        return SurvivorWorldArena.session(SEED, GameMode.SURVIVAL, generator, new AudioManager());
    }

    /** An open courtyard cell of the starter block, beside its rain collector. */
    private static Vec3i courtyard(Game g) {
        Vec3i cell = UrbanStarterSite.of(g.world.urban).collector().offset(2, 0, 0);
        SurvivorWorldArena.standAt(g, cell.offset(1, 0, 0));
        assertEquals(BlockType.AIR, g.world.getBlock(cell.x(), cell.y(), cell.z()), "an open courtyard cell");
        return cell;
    }

    @Test
    void nothingBuiltPutDownAndTakenApartGivesBackMoreThanItsRecipeSpent() {
        Game g = session(World.GEN_URBAN_ECONOMY);
        Vec3i cell = courtyard(g);
        List<String> checked = new ArrayList<>();
        for (Recipe recipe : CraftingSystem.recipesFor(g.world)) {
            BlockType placed = recipe.result == null ? null : recipe.result.places();
            if (placed == null || SalvageTable.entry(g.world, cell, placed) == null) { continue; }
            g.world.setBlock(cell.x(), cell.y(), cell.z(), placed, true); // a recorded edit, as a placement is
            assertEquals(SalvageTable.Condition.SOUND, SalvageTable.condition(g.world, cell, placed));
            List<ItemStack> yields = SalvageTable.yields(g.world, cell, placed);
            assertFalse(yields.isEmpty(), recipe.name + " comes apart for something");
            for (ItemStack s : yields) {
                int spent = recipe.ingredients.getOrDefault(s.type, 0) / Math.max(1, recipe.resultCount);
                assertTrue(s.count <= spent, recipe.name + " gives back " + s.count + " " + s.type
                        + " for a recipe of " + recipe.ingredients);
            }
            checked.add(recipe.name + " -> " + SalvageTable.describe(yields));
            g.world.setBlock(cell.x(), cell.y(), cell.z(), BlockType.AIR, true);
        }
        System.out.println("[economy-salvage] put down and taken apart: " + String.join("; ", checked));
        assertTrue(checked.size() >= 9, "every recipe that puts down a piece with salvage: " + checked);
        assertTrue(checked.stream().anyMatch(s -> s.startsWith("Repair Workbench")), "the workbench is among them");

        // The loop generator 20 closes: generator 19 still pays a rebuilt workbench's spare part.
        Game old = session(World.GEN_URBAN_MISSIONS);
        Vec3i oldCell = courtyard(old);
        old.world.setBlock(oldCell.x(), oldCell.y(), oldCell.z(), BlockType.WORKBENCH, true);
        assertTrue(SalvageTable.yields(old.world, oldCell, BlockType.WORKBENCH).stream()
                .anyMatch(s -> s.type == ItemType.SPARE_PARTS), "generator 19 keeps its rule");
        // While the workshop's own bench, planned there, still carries its part in the economy city.
        assertTrue(SalvageTable.recipeFor(g.world, BlockType.WORKBENCH) != null);
    }

    /** A planned table of region (0, 0) in a given wear, standing as planned. */
    private static BuildingPlan.Fixture table(Game g, SalvageTable.Condition wanted) {
        for (ParcelPlan parcel : g.world.urban.regionPlan(0, 0).parcels()) {
            BuildingPlan b = g.world.urban.buildingAt(parcel.x0(), parcel.z0());
            if (b == null || b.variant() == BuildingPlan.Variant.OCCUPIED || b.parcel().use() == ParcelPlan.Use.REFUGE) {
                continue;
            }
            for (BuildingPlan.Fixture f : b.fixtures()) {
                if (f.block() != BlockType.TABLE || f.kind() != BuildingPlan.FixtureKind.FURNITURE
                        || f.target() == null || SalvageTable.condition(g.world, f.position(), BlockType.TABLE) != wanted) {
                    continue;
                }
                SurvivorWorldArena.standAt(g, f.target());
                Vec3i p = f.position();
                if (g.world.getBlock(p.x(), p.y(), p.z()) == BlockType.TABLE) { return f; }
            }
        }
        throw new AssertionError("no " + wanted + " table in region (0, 0)");
    }

    @Test
    void aWornPieceComesApartUnderAnyHoldAndOnlyASoundOneIsCarriedAwayWhole() {
        for (int generator : new int[]{World.GEN_URBAN_MISSIONS, World.GEN_URBAN_ECONOMY}) {
            Game g = session(generator);
            boolean economy = generator == World.GEN_URBAN_ECONOMY;
            BuildingPlan.Fixture worn = table(g, SalvageTable.Condition.WORN);
            BuildingPlan.Fixture sound = table(g, SalvageTable.Condition.SOUND);
            g.player.crouching = false;
            assertEquals(economy, g.blockActions.dismantling.applies(worn.position(), BlockType.TABLE),
                    "generator " + generator + ": a standing hold on a worn table takes it apart only in the economy city");
            assertFalse(g.blockActions.dismantling.applies(sound.position(), BlockType.TABLE),
                    "generator " + generator + ": a sound table is still carried away whole");
            if (!economy) { continue; }

            List<ItemStack> wornYield = SalvageTable.yields(g.world, worn.position(), BlockType.TABLE);
            assertTrue(wornYield.stream().allMatch(s -> s.count < 3), "a worn table gives less than a sound one's three planks");
            SurvivorWorldArena.standAt(g, worn.target());
            RefugeWorldArena.aim(g, worn.position());
            int planks = g.player.inventory.count(ItemType.PLANK);
            RefugeWorldArena.hold(g, 10f);
            Vec3i p = worn.position();
            assertEquals(BlockType.AIR, g.world.getBlock(p.x(), p.y(), p.z()), "the worn table came apart where it stood");
            assertEquals(0, g.player.inventory.count(ItemType.TABLE), "nothing was carried away whole");
            assertEquals(planks + wornYield.getFirst().count, g.player.inventory.count(ItemType.PLANK),
                    "it gave what its wear left: " + wornYield);

            // The sound table, carried away whole and set down, is worth exactly what it was where it stood.
            List<ItemStack> soundYield = SalvageTable.yields(g.world, sound.position(), BlockType.TABLE);
            SurvivorWorldArena.standAt(g, sound.target());
            RefugeWorldArena.aim(g, sound.position());
            RefugeWorldArena.hold(g, 10f);
            assertEquals(1, g.player.inventory.count(ItemType.TABLE), "a sound table is carried away whole");
            Vec3i cell = courtyard(g);
            g.world.setBlock(cell.x(), cell.y(), cell.z(), BlockType.TABLE, true);
            assertEquals(SalvageTable.describe(soundYield), SalvageTable.describe(SalvageTable.yields(g.world, cell, BlockType.TABLE)),
                    "set down again it gives what it gave where it stood, never more");
        }
    }
}
