package com.veylon;

import com.veylon.ai.Pathfinder;
import com.veylon.entity.GameMode;
import com.veylon.save.SaveSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Milestone 14: the loose panel over a hidden cellar, through the production movement, F-key,
 * break and save commands. It is wall to everyone until the player finds and works it, then a
 * doorway like any other; its open state is fixture state, saved like a door's.
 */
class UrbanLoosePanelTest {
    @TempDir Path directory;

    /** The showcase house with its interior door replaced by a panel, recorded like a plan edit. */
    private static UrbanFixtureArena arena(GameMode mode) {
        UrbanFixtureArena a = UrbanFixtureArena.create(mode);
        Vec3i panel = a.house.innerDoor();
        a.game.world.setBlock(panel.x(), panel.y(), panel.z(), BlockType.HIDDEN_PANEL, true);
        a.game.world.setBlock(panel.x(), panel.y() + 1, panel.z(), BlockType.HIDDEN_PANEL, true);
        a.standAt(west(a));
        return a;
    }

    private static Vec3i west(UrbanFixtureArena a) {
        Vec3i p = a.house.innerDoor();
        return new Vec3i(p.x() - 1, p.y(), p.z());
    }

    private static Vec3i east(UrbanFixtureArena a) {
        Vec3i p = a.house.innerDoor();
        return new Vec3i(p.x() + 1, p.y(), p.z());
    }

    @Test
    void aClosedPanelIsWallUntilTheSurvivorSlidesItAside() {
        UrbanFixtureArena a = arena(GameMode.SURVIVAL);
        Vec3i panel = a.house.innerDoor();
        assertEquals("Plaster Wall", BlockType.HIDDEN_PANEL.displayName, "closed, it is named like its wall");
        assertFalse(a.walkTo(east(a), 3f), "a closed panel blocks the walk");

        a.standAt(west(a));
        a.aim(panel);
        assertTrue(a.prompt().startsWith("[F] Slide the loose panel aside"), a.prompt());
        a.pressF();
        assertSame(BlockType.HIDDEN_PANEL_OPEN, a.block(panel));
        assertSame(BlockType.HIDDEN_PANEL_OPEN, a.above(panel), "both cells slide together");
        assertTrue(FixtureStates.isOpen(a.game.world.fixtures.stateAt(panel.x(), panel.y(), panel.z())));
        assertEquals(BlockType.HIDDEN_PANEL.id(), a.game.world.changedBlocks.get(panel),
                "sliding it is fixture state, not another block edit");
        assertTrue(a.walkTo(east(a), 4f), "an open panel is a doorway");

        a.aim(panel);
        assertEquals("[F] Close panel", a.prompt());
        a.pressF();
        assertSame(BlockType.HIDDEN_PANEL, a.block(panel));
        assertFalse(a.walkTo(west(a), 3f), "closed again, it holds from the cellar side too");
    }

    @Test
    void nobodyRoutesThroughOrShouldersOpenAClosedPanel() {
        UrbanFixtureArena a = arena(GameMode.SURVIVAL);
        Vec3i panel = a.house.innerDoor(), from = west(a), to = east(a);
        assertFalse(Fixtures.isClosedDoor(BlockType.HIDDEN_PANEL), "not a door anyone pushes through");
        assertNull(Pathfinder.find(a.game.world, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), 700),
                "no route passes a closed panel");
        assertFalse(FixtureActions.residentOpens(a.game, panel.x(), panel.y(), panel.z()),
                "a resident never shoulders it open");
        assertSame(BlockType.HIDDEN_PANEL, a.block(panel));

        a.standAt(west(a));
        a.aim(panel);
        a.pressF();
        assertNotNull(Pathfinder.find(a.game.world, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), 700),
                "once found and opened, it is an ordinary doorway");
        a.aim(panel);
        a.pressF();
        assertNull(Pathfinder.find(a.game.world, from.x(), from.y(), from.z(), to.x(), to.y(), to.z(), 700),
                "closing it shuts the route again, without a stale cached path");
    }

    @Test
    void forcingAPanelLeavesADoorwayAndNothingToCarry() {
        UrbanFixtureArena a = arena(GameMode.SURVIVAL);
        Vec3i panel = a.house.innerDoor();
        assertTrue(FixtureActions.forcesOpen(BlockType.HIDDEN_PANEL));
        assertSame(BlockType.BROKEN_DOOR, FixtureActions.remainsOf(BlockType.HIDDEN_PANEL, false));
        assertTrue(FixtureActions.breaksWholeOpening(BlockType.HIDDEN_PANEL));
        assertNull(FixtureActions.salvageOf(a.game.world, panel, BlockType.HIDDEN_PANEL));
        a.standAt(west(a));
        a.aim(panel);
        a.pressF();
        assertNull(FixtureActions.salvageOf(a.game.world, panel, BlockType.HIDDEN_PANEL_OPEN),
                "an open panel is part of its wall, not an item");
    }

    @Test
    void anOpenedPanelStaysOpenAcrossASaveAndALoad() {
        UrbanFixtureArena a = arena(GameMode.SURVIVAL);
        Vec3i panel = a.house.innerDoor();
        a.standAt(west(a));
        a.aim(panel);
        a.pressF();
        Path file = directory.resolve("panel.sav");
        assertTrue(SaveSystem.save(a.game, file));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        loaded.world.ensureChunks(panel.x(), panel.z(), 1, 1_000);
        assertSame(BlockType.HIDDEN_PANEL_OPEN, loaded.world.getBlock(panel.x(), panel.y(), panel.z()));
        assertSame(BlockType.HIDDEN_PANEL_OPEN, loaded.world.getBlock(panel.x(), panel.y() + 1, panel.z()));
    }
}
