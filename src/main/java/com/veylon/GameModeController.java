package com.veylon;

import com.veylon.entity.GameMode;

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
        game.log("Game mode: " + target.id + (creativeMarked ? " (Creative world)." : "."));
        return true;
    }

    /** Restoration must not run switch logs, healing or perception side effects. */
    void restore(GameMode loadedMode, boolean marked, boolean flying) {
        mode = Objects.requireNonNull(loadedMode, "loadedMode");
        creativeMarked = marked || mode == GameMode.CREATIVE;
        applyToPlayer();
        game.player.abilities.setFlying(flying);
    }
}
