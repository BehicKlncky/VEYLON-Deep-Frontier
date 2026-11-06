package com.veylon.world.urban;

import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * How much punishment a layout-fourteen city's fortifications have left (urban milestone 18): the
 * barricades the player boards up and the braces set behind barred doors.
 *
 * <p>A barricade is a block the player placed, so its cell is always a recorded edit; this ledger
 * holds only the ones that have taken damage. An absent cell is at full strength, so the ledger
 * stays as small as the fighting that happened, at most {@value #MAX_DAMAGED} cells. A brace is a
 * reinforced bar: it is recorded for the bottom cell of a closed door barred from the brace's side,
 * at most {@value #MAX_BRACES} of them, and it means nothing once that door is opened, unbarred,
 * forced or gone, which every read checks, so no stale brace can outlive its door.
 *
 * <p>Persistent through {@code urban.fortifications}; a new or loaded world gets a fresh ledger with
 * its world. Main thread only, like the rest of the world.
 */
public final class UrbanFortifications {
    /** Most damaged barricade cells remembered; a blow on one more past this breaks it outright. */
    public static final int MAX_DAMAGED = 2_048;
    /** Most braced doors in a world. */
    public static final int MAX_BRACES = 256;
    /** Full strength of a plank barricade, a sheet-metal barricade and a door brace. */
    public static final float PLANK_STRENGTH = 60f, METAL_STRENGTH = 160f, BRACE_STRENGTH = 90f;

    private final Map<Vec3i, Float> damaged = new HashMap<>();
    private final Map<Vec3i, Float> braces = new HashMap<>();
    private boolean sectionRestored;

    /** True for a barricade block of either kind. */
    public static boolean isBarricade(BlockType t) {
        return t == BlockType.BARRICADE || t == BlockType.BARRICADE_METAL;
    }

    /** Full strength of a barricade block, or 0 for anything else. */
    public static float strength(BlockType t) {
        return t == BlockType.BARRICADE ? PLANK_STRENGTH : t == BlockType.BARRICADE_METAL ? METAL_STRENGTH : 0f;
    }

    /** The strength a barricade cell has left: its recorded damage, or full strength. */
    public float health(BlockType t, Vec3i cell) {
        Float h = damaged.get(cell);
        return h == null ? strength(t) : h;
    }

    /**
     * Takes a blow off a barricade cell.
     *
     * @return the strength left; zero or less means it gave way. A cell the full ledger cannot
     *         remember gives way at once, since its damage could not be kept.
     */
    public float damage(BlockType t, Vec3i cell, float amount) {
        float left = health(t, cell) - amount;
        if (left <= 0) {
            damaged.remove(cell);
            return 0f;
        }
        if (!damaged.containsKey(cell) && damaged.size() >= MAX_DAMAGED) { return 0f; }
        damaged.put(cell, left);
        return left;
    }

    /** Mends a barricade cell by up to {@code amount}; returns its new strength. */
    public float repair(BlockType t, Vec3i cell, float amount) {
        float mended = Math.min(strength(t), health(t, cell) + amount);
        if (mended >= strength(t)) {
            damaged.remove(cell);
        } else {
            damaged.put(cell, mended);
        }
        return mended;
    }

    /** A barricade cell stopped being one (taken down, burnt, blown out): its damage goes with it. */
    public void barricadeGone(Vec3i cell) {
        damaged.remove(cell);
    }

    public int damagedCount() { return damaged.size(); }

    public Map<Vec3i, Float> damagedCells() { return Collections.unmodifiableMap(damaged); }

    /**
     * The strength left in the brace behind a door, by the door's bottom cell, or -1 when it has
     * none. A brace whose door is no longer a closed, barred door counts as none; it is dropped
     * the next time braces are written or set, so this read changes nothing.
     */
    public float brace(World w, Vec3i bottom) {
        Float h = braces.get(bottom);
        return h == null || !braceHolds(w, bottom) ? -1f : h;
    }

    /** True while a brace's door is still a closed door barred from one side. */
    private static boolean braceHolds(World w, Vec3i bottom) {
        if (w.fixtureLockAt(bottom.x(), bottom.y(), bottom.z()) != FixtureStates.LOCK_BARRED) { return false; }
        return !w.isColumnLoaded(bottom.x(), bottom.z()) || Fixtures.isClosedDoor(w.getBlock(bottom.x(), bottom.y(), bottom.z()));
    }

    /** True when another door could be braced, or this one already is; stale braces make room. */
    public boolean canBrace(World w, Vec3i bottom) {
        if (braces.containsKey(bottom) || braces.size() < MAX_BRACES) { return true; }
        prepareSave(w);
        return braces.size() < MAX_BRACES;
    }

    /** Sets or mends a brace to the given strength. */
    public boolean setBrace(World w, Vec3i bottom, float strength) {
        if (!canBrace(w, bottom) || !(strength > 0) || strength > BRACE_STRENGTH) { return false; }
        braces.put(bottom, strength);
        return true;
    }

    public void removeBrace(Vec3i bottom) {
        braces.remove(bottom);
    }

    /**
     * Takes a blow off the brace behind a door.
     *
     * @return the strength left, zero when the brace splintered (and is gone), or -1 for no brace
     */
    public float damageBrace(World w, Vec3i bottom, float amount) {
        float h = brace(w, bottom);
        if (h < 0) {
            braces.remove(bottom); // a stale brace, if any, means nothing any more
            return -1f;
        }
        float left = h - amount;
        if (left <= 0) {
            braces.remove(bottom);
            return 0f;
        }
        braces.put(bottom, left);
        return left;
    }

    public int braceCount() { return braces.size(); }

    public Map<Vec3i, Float> braceCells() { return Collections.unmodifiableMap(braces); }

    /** Drops every brace whose door no longer holds it, so a save writes only standing braces. */
    public void prepareSave(World w) {
        braces.keySet().removeIf(bottom -> !braceHolds(w, bottom));
    }

    public boolean sectionRestored() { return sectionRestored; }

    /** Load only: the validated rows of {@code urban.fortifications}. */
    public void restore(Map<Vec3i, Float> damagedRows, Map<Vec3i, Float> braceRows) {
        damaged.clear();
        braces.clear();
        damaged.putAll(damagedRows);
        braces.putAll(braceRows);
        sectionRestored = true;
    }
}
