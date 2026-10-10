package com.veylon;

/** Player death and respawn rules; Game retains the app-state loop and command delegates. */
final class PlayerLifeCycle {
    private final Game game;

    PlayerLifeCycle(Game game) { this.game = game; }

    /** The one death transition, including a lethal blast's player remains. */
    void enterDeathIfDue() {
        var player = game.player;
        if (player.dead && !player.abilities.invulnerable() && game.appState == Game.AppState.PLAYING) {
            game.appState = Game.AppState.DEATH;
            game.deathTimer = 3f;
            game.fragments.spawnPlayerRemains(game, player);
            game.closeScreens();
        }
    }

    void respawn() {
        var player = game.player;
        if (player.abilities.invulnerable()) {
            player.restoreCreativeBody();
            return;
        }
        game.log("You died. The frontier reclaims you... (respawned at the crash site)");
        player.dead = false;
        player.clearBlastDeath();
        game.combustion.clear(player);
        player.health = 55;
        player.hunger = Math.max(player.hunger, 50);
        player.thirst = Math.max(player.thirst, 50);
        player.bodyTemp = 37;
        player.afflictions.clear();
        player.smokeExposure = 0;
        player.protein = Math.max(player.protein, 40);
        player.vitamins = Math.max(player.vitamins, 40);
        player.vel.set(0, 0, 0);
        player.pos.set(game.spawnPos);
        game.sleeping = false;
    }
}
