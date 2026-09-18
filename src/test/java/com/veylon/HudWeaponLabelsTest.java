package com.veylon;

import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.ui.Hud;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class HudWeaponLabelsTest {
    @ParameterizedTest
    @EnumSource(GameMode.class)
    void exactWeaponLabelsAndEmptyMagazineHintsRemainStable(GameMode mode) {
        Game g = new Game();
        g.newWorld(20260918L, true, mode);
        g.player.inventory.clear();
        boolean creative = mode == GameMode.CREATIVE;
        assertEquals(creative ? "Selected Arrow [R]   unlimited arrows"
                : "Selected Arrow [R]   Basic 0   Iron 0", Hud.bowAmmoLabel(g));
        g.player.inventory.add(ItemType.ARROW, 24);
        g.player.inventory.add(ItemType.IRON_ARROW, 8);
        assertEquals(creative ? "Selected Arrow [R]   unlimited arrows"
                : "Selected Arrow [R]   Basic 24   Iron 8", Hud.bowAmmoLabel(g));
        g.cycleBowAmmo();
        assertEquals(creative ? "Selected Iron Arrow [R]   unlimited arrows"
                : "Selected Iron Arrow [R]   Basic 24   Iron 8", Hud.bowAmmoLabel(g));

        ItemStack gun = new ItemStack(ItemType.MUSKET, 1);
        var weapon = WeaponRegistry.of(gun.type);
        assertEquals(creative, Hud.showReloadHint(g, gun, weapon));
        assertEquals(creative ? "0/1   unlimited Iron Ball" : "0/1   0 Iron Ball",
                Hud.firearmAmmoLabel(g, gun, weapon));
        g.player.inventory.add(ItemType.MUSKET_BALL, 12);
        assertTrue(Hud.showReloadHint(g, gun, weapon));
        gun.charge = 1;
        assertFalse(Hud.showReloadHint(g, gun, weapon));
        assertEquals(creative ? "1/1   unlimited Iron Ball" : "1/1   12 Iron Ball",
                Hud.firearmAmmoLabel(g, gun, weapon));
        assertEquals(creative ? "unlimited Scrap Bomb — LMB to throw" : "3x Scrap Bomb — LMB to throw",
                Hud.thrownAmmoLabel(g, new ItemStack(ItemType.SCRAP_BOMB, 3)));
    }
}
