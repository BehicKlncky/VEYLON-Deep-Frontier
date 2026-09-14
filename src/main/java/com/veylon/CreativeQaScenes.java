package com.veylon;

import com.veylon.entity.GameMode;

/**
 * Creative-only native QA. These observations belong to the automated session,
 * so a smoke load must not erase them; beginSession resets them for the next run.
 * They never influence the simulation or consume an outcome RNG stream.
 */
final class CreativeQaScenes {
    private final Game game;
    private boolean creative;
    private boolean damaged;
    private boolean restored;
    private int samples;

    CreativeQaScenes(Game game) { this.game = game; }

    void beginSession() {
        creative = game.player != null && game.player.abilities.invulnerable();
        damaged = false;
        restored = false;
        samples = 0;
    }

    void sampleBody() {
        if (!creative) return;
        samples++;
        damaged |= game.player == null || game.player.dead
                || game.player.health != game.player.maxHealth || game.player.damageFlash != 0;
    }

    void beforeSave() {
        if (!creative) return;
        game.player.abilities.setFlying(true);
        game.player.hurt(200, false);
        game.player.hurtPhysical(game, 200, true);
        sampleBody();
    }

    void afterLoad(boolean loaded) {
        if (!creative) return;
        restored = loaded && game.gameMode() == GameMode.CREATIVE && game.creativeMarked()
                && game.player.abilities.flying();
        sampleBody();
        // Milestone 6 adds the movement exercise; today only flight persistence exists.
        game.player.abilities.setFlying(false);
    }

    void appendSmokeFailure(StringBuilder failure) {
        if (!creative) return;
        System.out.println("[smoke] creative={mode=" + game.gameMode().id
                + ",marked=" + game.creativeMarked() + ",restoredModeMarkFlight=" + restored
                + ",damaged=" + damaged + ",bodySamples=" + samples + "}");
        if (!restored) failure.append("Creative mode, mark or flight did not survive load; ");
        if (damaged || samples == 0) failure.append("Creative damage/death check failed; ");
    }
}
