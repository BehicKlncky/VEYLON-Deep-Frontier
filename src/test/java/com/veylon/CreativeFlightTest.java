package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.save.SaveSystem;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** R5, R13-R15: Creative flight through the production movement command. */
class CreativeFlightTest {
    private static final float DT = 0.05f;
    private static final float EAST = 90f;
    private static final float WEST = 270f;

    @TempDir Path temporary;
    private final PlayerMovementSystem movement = new PlayerMovementSystem();
    private final PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
    private final PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();

    @Test
    void doubleTappingSpaceInsideTheWindowTogglesFlight() {
        Game game = CreativeTestArena.create(GameMode.CREATIVE);
        assertFalse(game.player.abilities.flying(), "R13: entering Creative does not start flight");
        tap(game);
        idle(game, 0.20f);
        tap(game);
        assertTrue(game.player.abilities.flying(), "R13: a second press within 0.30 s starts flight");
        idle(game, 0.40f);
        tap(game);
        idle(game, 0.10f);
        tap(game);
        assertFalse(game.player.abilities.flying(), "R13: double-tapping while flying ends flight");
    }

    @Test
    void pressesFurtherApartThanTheWindowNeverToggleFlight() {
        Game game = CreativeTestArena.create(GameMode.CREATIVE);
        tap(game);
        idle(game, 0.30f);
        tap(game);
        assertFalse(game.player.abilities.flying(), "R13: presses 0.35 s apart are two jumps");
        for (GameMode mode : new GameMode[]{GameMode.SURVIVAL}) {
            Game survival = CreativeTestArena.create(mode);
            tap(survival);
            idle(survival, 0.10f);
            tap(survival);
            assertFalse(survival.player.abilities.flying(), "R13: Survival never flies");
        }
    }

    @Test
    void spaceRisesCtrlDescendsAndNoInputHoversWithoutGravity() {
        Game game = flyingAt(50f);
        run(game, 10, EAST, false, false, false, true);
        assertEquals(50f + PlayerMovementSystem.FLY_VERTICAL_SPEED * 0.5f, game.player.pos.y, 0.01f,
                "R13: Space rises");
        run(game, 20, EAST, false, false, false, false);
        assertEquals(50f + PlayerMovementSystem.FLY_VERTICAL_SPEED * 0.5f, game.player.pos.y, 0.01f,
                "R13: no vertical input hovers without gravity");
        assertFalse(game.player.onGround, "R13: hovering is not standing");
        run(game, 10, EAST, false, false, true, false);
        assertEquals(50f, game.player.pos.y, 0.01f, "R13: Left Ctrl descends");
        assertFalse(game.player.crouching, "R13: descending does not crouch or lower the camera");
        assertTrue(game.player.abilities.flying());
    }

    @Test
    void horizontalFlightUsesCruiseAndShiftSpeedsWithoutStaminaNoiseOrFootingSprint() {
        Game game = flyingAt(45f);
        game.player.stamina = 50f;
        float x = game.player.pos.x;
        run(game, 4, EAST, true, false, false, false);
        assertEquals(x + PlayerMovementSystem.FLY_SPEED * 0.2f, game.player.pos.x, 0.01f,
                "R13: cruise speed");
        x = game.player.pos.x;
        run(game, 4, EAST, true, true, false, false);
        assertEquals(x + PlayerMovementSystem.FLY_FAST_SPEED * 0.2f, game.player.pos.x, 0.01f,
                "R13: Left Shift flies faster");
        assertEquals(50f, game.player.stamina, "R13: flight costs no stamina");
        assertEquals(0f, game.player.noise, "R13: flight makes no noise");
        assertFalse(game.player.sprinting, "R13: fast flight is not sprinting, so no footsteps or bob");
        assertFalse(game.player.onGround, "R13: airborne flight has no footing for footsteps");
    }

    @Test
    void flightKeepsWallAndCeilingCollision() {
        Game game = flyingAt(41f);
        for (int y = 40; y < 60; y++) {
            for (int z = 305; z <= 316; z++) {
                game.world.setBlock(315, y, z, BlockType.STONE, false);
            }
        }
        run(game, 20, EAST, true, true, false, false);
        assertTrue(game.player.pos.x + game.player.width / 2f <= 315f + 0.0001f, "D6: walls still collide");
        assertTrue(game.player.horizontalCollision, "D6: the wall hit is reported");
        int px = (int) Math.floor(game.player.pos.x), pz = (int) Math.floor(game.player.pos.z);
        for (int x = px - 1; x <= px + 1; x++) {
            for (int z = pz - 1; z <= pz + 1; z++) {
                game.world.setBlock(x, 46, z, BlockType.STONE, false);
            }
        }
        run(game, 20, EAST, false, false, false, true);
        assertTrue(game.player.pos.y + game.player.height <= 46f + 0.0001f, "D6: ceilings still collide");
        assertTrue(game.player.abilities.flying(), "R13: bumping a ceiling does not end flight");
    }

    @Test
    void descendingOntoTheGroundLandsButHoveringThereDoesNot() {
        Game game = flyingAt(40.001f);
        run(game, 20, EAST, false, false, false, false);
        assertTrue(game.player.abilities.flying(), "R13: hovering at ground level keeps flying");
        game.player.pos.y = 43f;
        run(game, 20, EAST, false, false, true, false);
        assertFalse(game.player.abilities.flying(), "R13: touching the ground while descending lands");
        assertTrue(game.player.onGround, "R13: a landed body stands");
        assertEquals(40f, game.player.pos.y, 0.21f, "R13: landing rests on the floor");
    }

    @Test
    void unloadedChunkColumnsStopFlight() {
        Game game = flyingAt(45f);
        assertNull(game.world.getChunk(18, 19), "Fixture: the column west of the arena is not loaded");
        game.player.pos.x = 305f;
        run(game, 20, WEST, true, false, false, false);
        assertTrue(game.player.pos.x - game.player.width / 2f >= 304f, "R14: flight cannot enter an unloaded column");
        assertTrue(game.player.horizontalCollision, "R14: the unloaded edge behaves like a wall");
        assertNull(game.world.getChunk(18, 19), "R14: flying toward a column never materializes it");
    }

    @Test
    void theAltitudeCeilingStopsRisingButAllowsDescent() {
        Game game = flyingAt(PlayerMovementSystem.FLIGHT_CEILING_Y - 2f);
        run(game, 40, EAST, false, false, false, true);
        assertTrue(game.player.pos.y <= PlayerMovementSystem.FLIGHT_CEILING_Y + 0.0001f, "R14: altitude ceiling");
        assertTrue(game.player.pos.y > PlayerMovementSystem.FLIGHT_CEILING_Y - 0.25f, "R14: rising reaches the ceiling");
        float y = game.player.pos.y;
        run(game, 4, EAST, false, false, true, false);
        assertTrue(game.player.pos.y < y, "R14: a body at the ceiling can descend");
    }

    @Test
    void switchingToSurvivalMidAirEndsFlightAndFallsFromTheSwitchHeight() {
        Game game = flyingAt(64f);
        run(game, 20, EAST, false, false, false, false);
        assertTrue(game.switchGameMode(GameMode.SURVIVAL));
        assertFalse(game.player.abilities.flying(), "R5: leaving Creative ends flight");
        for (int i = 0; i < 200 && !game.player.onGround; i++) {
            run(game, 1, EAST, false, false, false, false);
        }
        assertTrue(game.player.onGround, "R5: the body falls once flight ends");
        game.player.tickNeeds(game, DT);
        assertTrue(game.player.health < game.player.maxHealth, "R5: a fall from the switch height damages");
        assertFalse(game.player.dead, "Fixture: a 24-block fall is not lethal");
    }

    @Test
    void creativeSaveAndLoadRestoresFlight() {
        Game game = flyingAt(50f);
        Path save = temporary.resolve("flying.sav");
        assertTrue(SaveSystem.save(game, save));
        game.player.abilities.setFlying(false);
        assertTrue(SaveSystem.load(game, save));
        assertTrue(game.player.abilities.flying(), "R15: a Creative save restores flight");
    }

    private Game flyingAt(float y) {
        Game game = CreativeTestArena.create(GameMode.CREATIVE);
        game.player.pos.y = y;
        game.player.onGround = false;
        game.player.abilities.setFlying(true);
        return game;
    }

    private void tap(Game game) {
        command.set(EAST, false, false, false, false, false, false, true, true);
        movement.update(game.player, game.world, command, DT, result);
    }

    private void idle(Game game, float seconds) {
        int frames = Math.round(seconds / DT);
        run(game, frames, EAST, false, false, false, false);
    }

    private void run(Game game, int frames, float yaw, boolean forward, boolean sprint,
                     boolean crouch, boolean jump) {
        for (int i = 0; i < frames; i++) {
            command.set(yaw, forward, false, false, false, sprint, crouch, jump, false);
            movement.update(game.player, game.world, command, DT, result);
        }
    }
}
