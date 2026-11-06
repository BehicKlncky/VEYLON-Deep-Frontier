package com.veylon.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The storage screen's header warns, in colour as well as words, where taking is theft or worse,
 * marks what is the survivor's own, and leaves abandoned property plain (urban milestone 21).
 */
class CrateScreenToneTest {
    @Test
    void theftIsAWarningTheSurvivorsOwnIsMarkedAndAbandonedIsPlain() {
        assertEquals(HudStyle.AMBER, CrateScreen.ownerTone("Camp storage: taking supplies is theft"));
        assertEquals(HudStyle.AMBER, CrateScreen.ownerTone("The raiders' stores: they will want them back"));
        assertEquals(HudStyle.AMBER, CrateScreen.ownerTone("Stronghold stores: taking supplies is theft"));
        assertEquals(HudStyle.CYAN, CrateScreen.ownerTone("Your refuge's storage"));
        assertEquals(HudStyle.CYAN, CrateScreen.ownerTone("Camp storage: shared with you as a friend"));
        assertEquals(HudStyle.CYAN, CrateScreen.ownerTone("Left to you by the people who hid here"));
        assertEquals(HudStyle.MUTED, CrateScreen.ownerTone("Abandoned property"));
        assertEquals(HudStyle.MUTED, CrateScreen.ownerTone("Left behind"));
    }
}
