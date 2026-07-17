package com.veylon;

import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerReloadTest {

    private Game g;

    @BeforeEach
    void setUp() {
        g = new Game();
        g.newWorld(2468L, true);
        g.player.inventory.clear();
        g.player.hotbarSel = 0;
    }

    @Test
    void musketReloadCancelsWhenSwitchingToPistol() {
        ItemStack musket = put(0, ItemType.MUSKET);
        ItemStack pistol = put(1, ItemType.FLINTLOCK_PISTOL);
        g.player.inventory.add(ItemType.MUSKET_BALL, 4);
        assertTrue(g.startReloadSelected());
        g.player.hotbarSel = 1;
        g.tickReload(10f);
        assertEquals(0, musket.charge);
        assertEquals(0, pistol.charge);
        assertEquals(4, g.player.inventory.count(ItemType.MUSKET_BALL));
        assertEquals(0, g.reloadTimer);
    }

    @Test
    void firearmReloadCancelsWhenSwitchingToBowAndStaysCancelledOnReturn() {
        ItemStack musket = put(0, ItemType.MUSKET);
        put(1, ItemType.PRIMITIVE_BOW);
        g.player.inventory.add(ItemType.MUSKET_BALL, 2);
        assertTrue(g.startReloadSelected());
        g.player.hotbarSel = 1;
        g.tickReload(0.1f);
        g.player.hotbarSel = 0;
        g.tickReload(10f);
        assertEquals(0, musket.charge);
        assertEquals(2, g.player.inventory.count(ItemType.MUSKET_BALL));
        assertEquals(0, g.reloadTimer);
    }

    @Test
    void insufficientAmmoNeverStartsOrConsumesReload() {
        ItemStack blunderbuss = put(0, ItemType.BLUNDERBUSS);
        g.player.inventory.add(ItemType.SCRAP_SHOT, 1);
        assertFalse(g.startReloadSelected(), "blunderbuss needs two scrap shot");
        g.tickReload(10f);
        assertEquals(0, blunderbuss.charge);
        assertEquals(1, g.player.inventory.count(ItemType.SCRAP_SHOT));
    }

    @Test
    void partiallyLoadedRelicMagazineUsesInitiatingDefinitionExactlyOnce() {
        ItemStack carbine = put(0, ItemType.RELIC_CARBINE);
        carbine.charge = 3;
        g.player.inventory.add(ItemType.RIFLE_CARTRIDGE, 9);
        assertTrue(g.startReloadSelected());
        g.tickReload(5f);
        assertEquals(8, carbine.charge);
        assertEquals(4, g.player.inventory.count(ItemType.RIFLE_CARTRIDGE));
        g.tickReload(5f);
        assertEquals(8, carbine.charge);
        assertEquals(4, g.player.inventory.count(ItemType.RIFLE_CARTRIDGE),
                "completed reload cannot consume ammo twice");
    }

    @Test
    void saveLoadSafelyCancelsAnInProgressReload(@TempDir Path dir) {
        put(0, ItemType.MUSKET);
        g.player.inventory.add(ItemType.MUSKET_BALL, 2);
        assertTrue(g.startReloadSelected());
        Path save = dir.resolve("during-reload.sav");
        assertTrue(SaveSystem.save(g, save));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, save));
        assertEquals(0, loaded.reloadTimer);
        assertEquals(0, loaded.player.inventory.get(0).charge);
        assertEquals(2, loaded.player.inventory.count(ItemType.MUSKET_BALL));
    }

    private ItemStack put(int slot, ItemType type) {
        ItemStack stack = new ItemStack(type, 1);
        g.player.inventory.set(slot, stack);
        return stack;
    }
}
