package com.veylon;

import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.Affliction;
import com.veylon.entity.Creature;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.item.CraftingSystem;
import com.veylon.item.EquipSlot;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.Station;
import com.veylon.ui.Hud;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.HashSet;

import static org.junit.jupiter.api.Assertions.*;

/** R22-R23: use-up actions are free in Creative, while transforms, trades and transfers keep Survival rules. */
class CreativeUnlimitedUseTest {
    private static final Vector3f EAST = new Vector3f(1, 0, 0);
    private static final Vec3i STATION = new Vec3i(312, 40, 310);

    @Test
    void bowWithoutArrowsFiresTheSelectedTypeOnlyInCreative() {
        Game survival = armed(GameMode.SURVIVAL, ItemType.PRIMITIVE_BOW);
        assertEquals(Game.BowCommandResult.NO_AMMO, survival.updateBowCommand(0.9f, true, true, EAST),
                "R26: a Survival bow still needs a carried arrow");

        Game creative = armed(GameMode.CREATIVE, ItemType.PRIMITIVE_BOW);
        ItemStack bow = creative.player.selected();
        float durability = bow.durability;
        assertEquals(ItemType.ARROW, creative.selectedBowAmmo(), "R22: unlimited arrows default to ARROW");
        fullDraw(creative);
        assertEquals(ItemType.ARROW, creative.projectiles.live.getLast().ammoItem);
        assertEquals(ItemType.IRON_ARROW, creative.cycleBowAmmo(), "R22: [R] still chooses a type none carry");
        creative.updateBowCommand(5f, false, false, EAST);
        fullDraw(creative);
        assertEquals(ItemType.IRON_ARROW, creative.projectiles.live.getLast().ammoItem,
                "R22: the deliberate selection is fired without falling back to carried stock");
        assertEquals(0, creative.player.inventory.count(ItemType.ARROW)
                + creative.player.inventory.count(ItemType.IRON_ARROW), "R22: free shots neither use nor create arrows");
        assertEquals(durability, bow.durability, "R22: bow shots cause no wear");
        assertEquals("Selected Iron Arrow [R]   unlimited arrows", Hud.bowAmmoLabel(creative),
                "R23: the bow readout states unlimited arrows in ASCII");
    }

    @Test
    void firearmReloadsWithoutReserveAtTheNormalSpeedAndStillUsesItsMagazine() {
        WeaponDefinition musket = WeaponRegistry.of(ItemType.MUSKET);
        Game survival = armed(GameMode.SURVIVAL, ItemType.MUSKET);
        assertFalse(Hud.showReloadHint(survival, survival.player.selected(), musket),
                "R26: Survival offers no reload without reserve rounds");
        assertEquals("0/" + musket.magazine + "   0 " + musket.ammo.displayName,
                Hud.firearmAmmoLabel(survival, survival.player.selected(), musket),
                "R26: the Survival readout keeps counting reserve rounds");
        assertEquals(Game.FirearmCommandResult.NO_AMMO,
                survival.updateFirearmCommand(0f, false, false, true, EAST),
                "R26: a Survival reload still needs reserve ammunition");

        Game creative = armed(GameMode.CREATIVE, ItemType.MUSKET);
        ItemStack gun = creative.player.selected();
        assertTrue(Hud.showReloadHint(creative, gun, musket), "R23: an empty Creative magazine offers [R]");
        assertEquals(Game.FirearmCommandResult.RELOAD_STARTED,
                creative.updateFirearmCommand(0f, false, false, true, EAST));
        assertEquals(musket.reloadTime, creative.reloadTimer, 0.0001f, "R22: the reload keeps its normal duration");
        creative.tickReload(musket.reloadTime - 0.01f);
        assertEquals(0, gun.charge, "R22: the magazine fills only when the reload completes");
        creative.tickReload(0.02f);
        assertEquals(musket.magazine, gun.charge, "R22: an unlimited reserve fills the magazine");
        assertEquals(0, creative.player.inventory.count(musket.ammo), "R22: no reserve rounds are needed or created");

        float durability = gun.durability;
        assertEquals(Game.FirearmCommandResult.FIRED, creative.updateFirearmCommand(0f, true, true, false, EAST));
        assertEquals(musket.magazine - 1, gun.charge, "R22: each shot still uses a loaded round");
        assertEquals(durability, gun.durability, "R22: firing causes no wear");
        assertEquals((musket.magazine - 1) + "/" + musket.magazine + "   unlimited " + musket.ammo.displayName,
                Hud.firearmAmmoLabel(creative, gun, musket), "R23: the reserve reads as unlimited in ASCII");
    }

    @Test
    void bombsFoodDrinksAndMedicineAreNotUsedUpInCreative() {
        Game creative = CreativeTestArena.create(GameMode.CREATIVE);
        creative.player.hotbarSel = 0;
        creative.player.inventory.set(0, new ItemStack(ItemType.SCRAP_BOMB, 3));
        assertTrue(creative.updateThrownWeaponCommand(0.05f, true, EAST), "R22: the bomb is still lit and thrown");
        assertEquals(3, creative.player.selected().count, "R22: a thrown bomb is not used up");
        assertEquals(1, creative.projectiles.liveCount());
        assertEquals("unlimited " + ItemType.SCRAP_BOMB.displayName + " — LMB to throw",
                Hud.thrownAmmoLabel(creative, creative.player.selected()), "R23: the thrown readout shows no count");

        // Creative holds hunger full; lowering it reaches the eating branch that would shrink the stack.
        creative.player.hunger = 20f;
        creative.player.inventory.set(0, new ItemStack(ItemType.COOKED_MEAT, 3));
        creative.consumables.eat(creative.player.selected());
        assertEquals(3, creative.player.selected().count, "R22: eating does not use up food");
        assertEquals(20f + ItemType.COOKED_MEAT.food, creative.player.hunger, "R22: the meal keeps its normal benefit");

        creative.player.inventory.set(0, new ItemStack(ItemType.WATERSKIN_CLEAN, 1));
        creative.consumables.drink(creative.player.selected());
        assertEquals(1, creative.player.inventory.count(ItemType.WATERSKIN_CLEAN), "R22: drinking keeps the filled skin");
        assertEquals(0, creative.player.inventory.count(ItemType.WATERSKIN_EMPTY),
                "R22: a free drink does not duplicate an empty skin");

        // Affliction admission is closed in Creative; insert one to reach the treatment branch.
        creative.player.afflictions.put(Affliction.FOOD_POISONING, 60f);
        creative.player.inventory.set(0, new ItemStack(ItemType.MEDICINE, 2));
        creative.consumables.applyMedical(creative.player.selected());
        assertFalse(creative.player.has(Affliction.FOOD_POISONING), "R22: the treatment still takes effect");
        assertEquals(2, creative.player.selected().count, "R22: medicine is not used up");

        Game survival = CreativeTestArena.create(GameMode.SURVIVAL);
        survival.player.hotbarSel = 0;
        survival.player.inventory.set(0, new ItemStack(ItemType.WATERSKIN_CLEAN, 1));
        survival.consumables.drink(survival.player.selected());
        assertEquals(0, survival.player.inventory.count(ItemType.WATERSKIN_CLEAN), "R26: Survival drinking empties the skin");
        assertEquals(1, survival.player.inventory.count(ItemType.WATERSKIN_EMPTY));
        survival.player.addAffliction(Affliction.FOOD_POISONING, 60f);
        survival.player.inventory.set(0, new ItemStack(ItemType.MEDICINE, 2));
        survival.consumables.applyMedical(survival.player.selected());
        assertEquals(1, survival.player.selected().count, "R26: a Survival treatment uses one medicine");
    }

    @Test
    void weaponsToolsAndKnivesKeepTheirDurabilityInCreative() {
        Game creative = CreativeTestArena.create(GameMode.CREATIVE);
        creative.player.hotbarSel = 0;
        ItemStack axe = new ItemStack(ItemType.STONE_AXE, 1);
        ItemStack knife = new ItemStack(ItemType.IRON_KNIFE, 1);
        creative.player.inventory.set(0, axe);
        creative.player.inventory.set(1, knife);
        Creature deer = creative.entities.spawnCreature(creative.world, Creature.CreatureType.DEER,
                311.5f, 40.001f, 310.5f);
        float axeDurability = axe.durability;
        float knifeDurability = knife.durability;
        assertTrue(creative.performPlayerAttack(deer), "precondition: the swing lands");
        creative.combat.consumeDurability(axe, axeDurability * 3f);
        for (int i = 0; i < 5; i++) creative.combat.useKnife();
        assertEquals(axeDurability, axe.durability, "R22: melee and even breaking wear leave the weapon intact");
        assertSame(axe, creative.player.inventory.get(0), "R22: a worn-out amount cannot delete the tool");
        assertEquals(knifeDurability, knife.durability, "R22: skinning causes no knife wear");

        Game survival = CreativeTestArena.create(GameMode.SURVIVAL);
        ItemStack survivalKnife = new ItemStack(ItemType.IRON_KNIFE, 1);
        survival.player.inventory.set(1, survivalKnife);
        survival.combat.useKnife();
        assertTrue(survivalKnife.durability < knifeDurability, "R26: Survival skinning still wears the knife");
    }

    @Test
    void carriedStacksKeepFreshnessWhileCrateContentsAndSurvivalInventoriesSpoil() {
        for (GameMode mode : GameMode.values()) {
            Game game = CreativeTestArena.create(mode);
            ItemStack carried = new ItemStack(ItemType.RAW_MEAT, 2);
            ItemStack stored = new ItemStack(ItemType.RAW_MEAT, 2);
            game.player.inventory.set(0, carried);
            Inventory crate = new Inventory(12);
            crate.set(0, stored);
            game.world.crateContents.put(STATION, crate);
            float carriedBefore = carried.freshness;
            float storedBefore = stored.freshness;

            game.itemConditions.slowTick(game, 30f);

            assertTrue(stored.freshness < storedBefore, "R22: crate contents still spoil in " + mode);
            if (mode == GameMode.CREATIVE) {
                assertEquals(carriedBefore, carried.freshness, "R22: carried stacks keep their freshness");
            } else {
                assertTrue(carried.freshness < carriedBefore, "R26: carried Survival food still spoils");
            }
        }
    }

    @Test
    void craftingCookingFuelTradeGiftsDepositsAndEquippingKeepSurvivalRulesInCreative() {
        Game creative = CreativeTestArena.create(GameMode.CREATIVE);
        creative.player.hotbarSel = 0;
        Inventory inv = creative.player.inventory;

        Recipe powder = CraftingSystem.RECIPES.stream()
                .filter(r -> r.result == ItemType.BLACK_POWDER).findFirst().orElseThrow();
        inv.add(ItemType.SULFUR, 1);
        inv.add(ItemType.SALTPETER, 2);
        inv.add(ItemType.CHARCOAL, 1);
        CraftingSystem.craft(inv, powder, EnumSet.noneOf(Station.class), new HashSet<>());
        assertEquals(1, inv.count(ItemType.SULFUR), "R22: crafting still requires its station");
        assertEquals("Black Powder x3", CraftingSystem.craft(inv, powder, EnumSet.of(Station.WORKBENCH), new HashSet<>()));
        assertEquals(0, inv.count(ItemType.SULFUR) + inv.count(ItemType.SALTPETER) + inv.count(ItemType.CHARCOAL),
                "R22: crafting still consumes its ingredients");

        creative.world.setBlock(STATION.x(), STATION.y(), STATION.z(), BlockType.CAMPFIRE, false);
        creative.world.campfireFuel.put(STATION, 100f);
        inv.clear();
        inv.add(ItemType.RAW_MEAT, 1);
        assertTrue(creative.interactWithBlockAt(STATION));
        assertEquals(0, inv.count(ItemType.RAW_MEAT), "R22: cooking still transforms the raw meat");
        assertEquals(1, inv.count(ItemType.COOKED_MEAT));
        inv.clear();
        inv.add(ItemType.LOG, 1);
        assertTrue(creative.interactWithBlockAt(STATION));
        assertEquals(0, inv.count(ItemType.LOG), "R22: fueling a campfire still consumes the log");

        creative.world.setBlock(STATION.x(), STATION.y(), STATION.z(), BlockType.LANTERN, false);
        creative.world.lanternState(STATION);
        inv.clear();
        inv.set(0, new ItemStack(ItemType.CHARCOAL, 2));
        assertTrue(creative.interactLantern(STATION));
        assertEquals(1, inv.count(ItemType.CHARCOAL), "R22: lantern fuel still consumes charcoal");

        Npc trader = new Npc(creative.world, "Trader");
        trader.isTrader = true;
        inv.clear();
        inv.add(ItemType.HIDE, 2);
        assertTrue(creative.npcScreen.performTrade(creative, trader, ItemType.HIDE, 2, ItemType.BERRY, 3));
        assertEquals(0, inv.count(ItemType.HIDE), "R22: trade still removes the goods given");
        assertEquals(3, inv.count(ItemType.BERRY));
        inv.clear();
        inv.set(0, new ItemStack(ItemType.BERRY, 2));
        creative.npcScreen.performGift(creative, trader);
        assertEquals(1, inv.count(ItemType.BERRY), "R22: a gift still leaves the inventory");

        creative.openCrate = new Inventory(12);
        creative.openCratePos = null;
        inv.clear();
        inv.set(0, new ItemStack(ItemType.PLANK, 5));
        assertEquals(5, creative.transferPlayerItemToCrate(0));
        assertNull(inv.get(0), "R22: a crate deposit still moves the stack");
        assertEquals(5, creative.openCrate.count(ItemType.PLANK));

        inv.set(0, new ItemStack(ItemType.HIDE_COAT, 2));
        creative.consumables.equipHeld(creative.player.selected());
        assertEquals(1, inv.count(ItemType.HIDE_COAT), "R22: equipping still moves one item into gear");
        assertEquals(ItemType.HIDE_COAT, creative.player.equipped(EquipSlot.TORSO).type);
    }

    private static Game armed(GameMode mode, ItemType weapon) {
        Game game = CreativeTestArena.create(mode);
        game.player.inventory.set(0, new ItemStack(weapon, 1));
        game.player.hotbarSel = 0;
        return game;
    }

    private static void fullDraw(Game game) {
        assertEquals(Game.BowCommandResult.DRAWING, game.updateBowCommand(0.9f, true, true, EAST));
        assertEquals(Game.BowCommandResult.FIRED, game.updateBowCommand(0f, false, false, EAST),
                "R22: a full draw releases an arrow");
    }
}
