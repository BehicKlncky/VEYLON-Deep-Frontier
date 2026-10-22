package com.veylon.settlement;

import com.veylon.FixtureActions;
import com.veylon.Game;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.urban.CompoundSites;

/**
 * A compound's gate in use (urban milestone 17): its guards swing both leaves open to pass, or to
 * let someone through, and shut and lock them again once nobody stands in the opening. The leaves
 * are ordinary metal door fixtures, so their state is the world's saved fixture state; what is
 * transient is only how long a guard holds the gate open ({@link UrbanSite#gateTimer}).
 */
public final class CompoundGate {
    /** Seconds a gate stays open after a guard opens it, before it is shut behind them. */
    public static final float HOLD_SECONDS = 4f;

    private CompoundGate() {
    }

    /** True when both leaves stand open. */
    public static boolean open(Game g, CompoundSites.Compound c) {
        for (Vec3i leaf : c.gate().leaves()) {
            if (g.world.getBlock(leaf.x(), leaf.y(), leaf.z()) != BlockType.METAL_DOOR_OPEN) { return false; }
        }
        return true;
    }

    /** True while either leaf is still a closed door: forced or broken leaves no longer close anything. */
    public static boolean shuttable(Game g, CompoundSites.Compound c) {
        for (Vec3i leaf : c.gate().leaves()) {
            if (Fixtures.kindOf(g.world.getBlock(leaf.x(), leaf.y(), leaf.z())) == null) { return false; }
        }
        return true;
    }

    /**
     * A guard opens the gate, unlocking it: both leaves swing open with the metal door's sound and
     * noise. Returns true when it stands open now.
     */
    public static boolean openFor(Game g, UrbanSite s, CompoundSites.Compound c, float seconds) {
        if (!g.world.isColumnLoaded(c.gate().first().x(), c.gate().first().z())) { return false; }
        boolean moved = false;
        for (Vec3i leaf : c.gate().leaves()) {
            moved |= FixtureActions.guardSwings(g, leaf, true);
        }
        s.gateTimer = Math.max(s.gateTimer, seconds);
        return open(g, c) || moved;
    }

    /** Ticks a gate's hold; shuts and locks it once the time is up and the opening is clear. */
    public static void tick(Game g, UrbanSite s, CompoundSites.Compound c, float dt) {
        if (s.gateTimer <= 0f && !open(g, c)) { return; }
        s.gateTimer = Math.max(0f, s.gateTimer - dt);
        if (s.gateTimer > 0f || !g.world.isColumnLoaded(c.gate().first().x(), c.gate().first().z())) { return; }
        for (Vec3i leaf : c.gate().leaves()) {
            if (FixtureActions.bodyInDoor(g, leaf)) {
                s.gateTimer = 1f;
                return;
            }
        }
        for (Vec3i leaf : c.gate().leaves()) {
            FixtureActions.guardSwings(g, leaf, false);
        }
    }
}
