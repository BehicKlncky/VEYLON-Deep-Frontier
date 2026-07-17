package com.veylon;

import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.item.CraftingSystem;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.Recipe;
import com.veylon.item.Station;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementBuilder;
import com.veylon.settlement.SettlementType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** End-to-end late-game relic acquisition, restoration and firing coverage. */
class RelicProgressionGameplayTest {

    private static final long SEED = 20_260_716L;
    private static final Vector3f EAST = new Vector3f(1, 0, 0);

    @Test
    void generatedFortressAutoRifleIsLootedRestoredAtAnvilAndFiredWithScarceAmmo() {
        Game game = new Game();
        game.newWorld(SEED, true);
        game.player.inventory.clear();
        game.player.hotbarSel = 0;
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.projectiles.setRandomSeed(1703L);

        RelicCrate target = findGeneratedAutoRifleCrate(game);
        assertNotNull(target, "fixed seed has an elite fortress armory containing an auto-rifle");
        assertTrue(target.settlement.hostile(), "relic rifle belongs to an elite hostile site");
        assertEquals(SettlementBuilder.CRATE_RELIC,
                target.layout.crates.get(target.position));

        generateBlockChunk(game, target.position);
        assertEquals(BlockType.CRATE, game.world.getBlock(
                target.position.x(), target.position.y(), target.position.z()));
        Inventory generatedCrate = game.world.crateContents.get(target.position);
        assertNotNull(generatedCrate, "chunk generation registers the physical crate's loot");

        ItemStack foundRifle = stackOf(generatedCrate, ItemType.RELIC_RIFLE);
        assertNotNull(foundRifle);
        assertTrue(foundRifle.durability > 0f
                        && foundRifle.durability < ItemType.RELIC_RIFLE.maxDurability * 0.5f,
                "recovered relic is functional but worn enough to need rare-component restoration");
        int generatedCartridges = generatedCrate.count(ItemType.RIFLE_CARTRIDGE);
        int generatedParts = generatedCrate.count(ItemType.RELIC_PARTS);
        WeaponDefinition weapon = WeaponRegistry.of(ItemType.RELIC_RIFLE);
        assertNotNull(weapon);
        assertTrue(generatedCartridges >= 8 && generatedCartridges < weapon.magazine,
                "one rare crate cannot even fill the auto-rifle's magazine");
        assertTrue(generatedParts >= 1 && generatedParts <= 2);

        approach(game, target.position);
        assertTrue(game.openCrateAt(target.position),
                "the physical fortress crate opens through the gameplay crate command");
        assertEquals(1, takeAll(game, ItemType.RELIC_RIFLE));
        assertEquals(generatedParts, takeAll(game, ItemType.RELIC_PARTS));
        assertEquals(generatedCartridges, takeAll(game, ItemType.RIFLE_CARTRIDGE));
        assertEquals(0, generatedCrate.count(ItemType.RELIC_RIFLE));
        assertEquals(0, generatedCrate.count(ItemType.RELIC_PARTS));
        assertEquals(0, generatedCrate.count(ItemType.RIFLE_CARTRIDGE));

        ItemStack lootedRifle = stackOf(game.player.inventory, ItemType.RELIC_RIFLE);
        assertNotNull(lootedRifle);
        assertEquals(foundRifle.durability, lootedRifle.durability, 0.0001f,
                "crate transfer preserves the generated relic's worn condition");

        Vec3i generatedAnvil = findGeneratedVillageAnvil(game);
        assertNotNull(generatedAnvil, "the same fixed world provides a real settlement workshop");
        generateBlockChunk(game, generatedAnvil);
        assertEquals(BlockType.ANVIL, game.world.getBlock(
                generatedAnvil.x(), generatedAnvil.y(), generatedAnvil.z()));
        approach(game, generatedAnvil);
        assertTrue(game.nearbyStations().contains(Station.ANVIL));

        Recipe restore = recipe(ItemType.RELIC_RIFLE);
        int partsBefore = game.player.inventory.count(ItemType.RELIC_PARTS);
        int cartridgesBefore = game.player.inventory.count(ItemType.RIFLE_CARTRIDGE);
        // This is the same production crafting engine invoked by CraftingScreen's
        // mouse/Enter path, with its real nearby-station and blueprint inputs.
        assertEquals("Restore Auto-Rifle", CraftingSystem.craft(
                game.player.inventory, restore, game.nearbyStations(),
                game.player.blueprints));

        ItemStack restored = stackOf(game.player.inventory, ItemType.RELIC_RIFLE);
        assertNotNull(restored);
        assertNotSame(lootedRifle, restored, "restoration consumes the worn mechanism");
        assertEquals(ItemType.RELIC_RIFLE.maxDurability, restored.durability, 0.0001f);
        assertEquals(partsBefore - 1, game.player.inventory.count(ItemType.RELIC_PARTS));
        assertEquals(cartridgesBefore,
                game.player.inventory.count(ItemType.RIFLE_CARTRIDGE),
                "restoration itself does not fabricate or consume scarce cartridges");

        int rifleSlot = slotOf(game.player.inventory, ItemType.RELIC_RIFLE);
        assertTrue(rifleSlot >= 0 && rifleSlot < 9);
        game.player.hotbarSel = rifleSlot;
        syncCamera(game);
        assertTrue(weapon.relic && weapon.automatic);
        assertFalse(WeaponRegistry.of(ItemType.RELIC_CARBINE).automatic,
                "the found rifle's held-fire role remains distinct from the relic carbine");

        assertEquals(Game.FirearmCommandResult.RELOAD_STARTED,
                game.updateFirearmCommand(0f, false, false, true, EAST));
        game.tickReload(weapon.reloadTime + 0.01f);
        assertEquals(generatedCartridges, restored.charge,
                "partial reload uses every scarce cartridge but cannot fill the magazine");
        assertEquals(0, game.player.inventory.count(ItemType.RIFLE_CARTRIDGE));

        float durabilityBeforeFire = restored.durability;
        assertEquals(Game.FirearmCommandResult.FIRED,
                game.updateFirearmCommand(0f, true, true, false, EAST));
        assertEquals(Game.FirearmCommandResult.COOLDOWN,
                game.updateFirearmCommand(weapon.attackInterval * 0.5f,
                        true, false, false, EAST));
        assertEquals(Game.FirearmCommandResult.FIRED,
                game.updateFirearmCommand(weapon.attackInterval * 0.5f + 0.001f,
                        true, false, false, EAST),
                "continuous held input deliberately repeats only after the automatic cadence");

        assertEquals(generatedCartridges - 2, restored.charge);
        assertEquals(durabilityBeforeFire - weapon.durabilityCost * 2f,
                restored.durability, 0.0001f);
        assertEquals(2, game.projectiles.liveCount());
        assertEquals(2, game.noise.countCategory("gunshot"));

        game.projectiles.update(game, 10f);
        assertEquals(0, game.projectiles.liveCount(),
                "fired relic bullets leave the bounded projectile pool");
    }

    private static RelicCrate findGeneratedAutoRifleCrate(Game game) {
        for (int radius = 3; radius <= 32; radius++) {
            for (int rx = -radius; rx <= radius; rx++) {
                RelicCrate found = inspectRegion(game, rx, -radius);
                if (found != null) {
                    return found;
                }
                found = inspectRegion(game, rx, radius);
                if (found != null) {
                    return found;
                }
            }
            for (int rz = -radius + 1; rz < radius; rz++) {
                RelicCrate found = inspectRegion(game, -radius, rz);
                if (found != null) {
                    return found;
                }
                found = inspectRegion(game, radius, rz);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static RelicCrate inspectRegion(Game game, int regionX, int regionZ) {
        Settlement settlement = game.world.settlementForRegion(regionX, regionZ);
        if (settlement == null || settlement.type != SettlementType.FORTRESS
                || !settlement.hostile()) {
            return null;
        }
        SettlementBuilder.Layout layout = game.world.layoutFor(settlement);
        for (Map.Entry<Vec3i, Integer> crate : layout.crates.entrySet()) {
            if (crate.getValue() != SettlementBuilder.CRATE_RELIC) {
                continue;
            }
            Inventory expected = SettlementBuilder.rollCrate(
                    SEED, crate.getKey(), SettlementBuilder.CRATE_RELIC);
            if (expected.count(ItemType.RELIC_RIFLE) == 1) {
                return new RelicCrate(settlement, layout, crate.getKey());
            }
        }
        return null;
    }

    private static Vec3i findGeneratedVillageAnvil(Game game) {
        for (int radius = 1; radius <= 16; radius++) {
            for (int rx = -radius; rx <= radius; rx++) {
                Vec3i found = villageAnvil(game, rx, -radius);
                if (found != null) {
                    return found;
                }
                found = villageAnvil(game, rx, radius);
                if (found != null) {
                    return found;
                }
            }
            for (int rz = -radius + 1; rz < radius; rz++) {
                Vec3i found = villageAnvil(game, -radius, rz);
                if (found != null) {
                    return found;
                }
                found = villageAnvil(game, radius, rz);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static Vec3i villageAnvil(Game game, int regionX, int regionZ) {
        Settlement settlement = game.world.settlementForRegion(regionX, regionZ);
        if (settlement == null || settlement.type != SettlementType.VILLAGE) {
            return null;
        }
        for (Map.Entry<Long, Byte> block : game.world.layoutFor(settlement).blocks.entrySet()) {
            if (BlockType.byId(block.getValue()) == BlockType.ANVIL) {
                return new Vec3i(SettlementBuilder.unpackX(block.getKey()),
                        SettlementBuilder.unpackY(block.getKey()),
                        SettlementBuilder.unpackZ(block.getKey()));
            }
        }
        return null;
    }

    private static void generateBlockChunk(Game game, Vec3i position) {
        game.world.getOrCreateChunk(
                Math.floorDiv(position.x(), 16), Math.floorDiv(position.z(), 16));
    }

    private static void approach(Game game, Vec3i position) {
        game.player.pos.set(position.x() + 1.5f, position.y(), position.z() + 0.5f);
        syncCamera(game);
    }

    private static void syncCamera(Game game) {
        game.camera.position.set(game.player.pos.x,
                game.player.pos.y + game.player.eyeHeight(), game.player.pos.z);
    }

    private static int takeAll(Game game, ItemType type) {
        int moved = 0;
        int slot;
        while ((slot = slotOf(game.openCrate, type)) >= 0) {
            int transfer = game.transferCrateItemToPlayer(slot);
            assertTrue(transfer > 0);
            moved += transfer;
        }
        return moved;
    }

    private static ItemStack stackOf(Inventory inventory, ItemType type) {
        int slot = slotOf(inventory, type);
        return slot < 0 ? null : inventory.get(slot);
    }

    private static int slotOf(Inventory inventory, ItemType type) {
        for (int i = 0; i < inventory.size(); i++) {
            ItemStack stack = inventory.get(i);
            if (stack != null && stack.type == type) {
                return i;
            }
        }
        return -1;
    }

    private static Recipe recipe(ItemType result) {
        return CraftingSystem.RECIPES.stream()
                .filter(candidate -> candidate.result == result)
                .findFirst().orElseThrow();
    }

    private record RelicCrate(Settlement settlement, SettlementBuilder.Layout layout,
                              Vec3i position) {
    }
}
