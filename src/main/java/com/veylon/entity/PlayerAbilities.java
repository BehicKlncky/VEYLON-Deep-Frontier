package com.veylon.entity;

import java.util.Objects;

/**
 * One derivation boundary keeps gameplay independent of named mode presets.
 * Only flight is independent state; every other flag is reconstructed on load.
 */
public final class PlayerAbilities {
    private boolean invulnerable;
    private boolean mayFly;
    private boolean flying;
    private boolean instantBuild;
    private boolean unlimitedItems;
    private boolean perceivableByAi = true;

    public boolean invulnerable() { return invulnerable; }
    public boolean mayFly() { return mayFly; }
    public boolean flying() { return flying; }
    public boolean instantBuild() { return instantBuild; }
    public boolean unlimitedItems() { return unlimitedItems; }
    public boolean perceivableByAi() { return perceivableByAi; }

    public void apply(GameMode mode) {
        Objects.requireNonNull(mode, "mode");
        boolean creative = mode == GameMode.CREATIVE;
        invulnerable = creative;
        mayFly = creative;
        instantBuild = creative;
        unlimitedItems = creative;
        perceivableByAi = !creative;
        if (!mayFly) flying = false;
    }

    public void setFlying(boolean value) {
        flying = mayFly && value;
    }
}
