package com.veylon;

import com.veylon.ai.PlayerAwareness;
import com.veylon.entity.GameMode;
import com.veylon.ui.GameModeScreen;

import java.util.Objects;

/**
 * Owns world policy independently of the player instance. Loading restores a
 * snapshot, whereas an explicit switch permanently marks this world's history.
 */
final class GameModeController {
    private final Game game;
    private GameMode mode = GameMode.SURVIVAL;
    private boolean creativeMarked;

    GameModeController(Game game) {
        this.game = game;
    }

    GameMode mode() { return mode; }
    boolean creativeMarked() { return creativeMarked; }

    void reset(GameMode initialMode) {
        mode = Objects.requireNonNull(initialMode, "initialMode");
        creativeMarked = mode == GameMode.CREATIVE;
        game.creativeControls.clear();
    }

    void applyToPlayer() {
        game.player.abilities.apply(mode);
    }

    boolean switchTo(GameMode target) {
        Objects.requireNonNull(target, "target");
        if (game.player == null || target == mode) return false;
        mode = target;
        creativeMarked |= target == GameMode.CREATIVE;
        applyToPlayer();
        game.player.resetFallState();
        game.blockActions.reset();
        // R25: leaving Creative releases every world control, and entering it
        // starts from none. One unconditional call covers both directions.
        game.creativeControls.clear();
        if (game.player.abilities.invulnerable()) game.player.restoreCreativeBody();
        // R4: perception gates stop new observations; this retires the old ones.
        if (!game.player.isPerceivableByAi()) PlayerAwareness.forgetPlayer(game);
        game.log("Game mode: " + target.id + (creativeMarked ? " (Creative world)." : "."));
        return true;
    }

    /** Restoration must not run switch logs, healing or perception side effects. */
    void restore(GameMode loadedMode, boolean marked, boolean flying) {
        mode = Objects.requireNonNull(loadedMode, "loadedMode");
        creativeMarked = marked || mode == GameMode.CREATIVE;
        applyToPlayer();
        game.player.abilities.setFlying(flying);
        // A Survival world holds no world controls, whatever a save claims.
        if (mode != GameMode.CREATIVE) game.creativeControls.clear();
    }

    /** Pause [G] (R3): the paused confirmation for switching to the other mode. */
    void openScreen() {
        game.gameModeScreen.open(mode, creativeMarked);
        game.uiMode = Game.UiMode.GAME_MODE;
    }

    /** Applies the confirmation's answer; either answer returns to the pause menu. */
    void handleScreen(GameModeScreen.Action action) {
        if (action == GameModeScreen.Action.NONE) return;
        if (action == GameModeScreen.Action.CONFIRM) switchTo(GameModeScreen.target(mode));
        game.uiMode = Game.UiMode.PAUSE;
    }
}
