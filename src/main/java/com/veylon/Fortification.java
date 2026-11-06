package com.veylon;

import com.veylon.entity.Entity;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.FixtureStates;
import com.veylon.world.Fixtures;
import com.veylon.world.World;
import com.veylon.world.urban.SalvageTable;
import com.veylon.world.urban.UrbanFortifications;
import com.veylon.world.urban.UrbanIds;

/**
 * Fortifying a layout-fourteen city's rooms (urban milestone 18): barricades, braced doors and
 * modest repairs, all ordinary recorded voxel edits and fixture state, with their strength kept in
 * {@link UrbanFortifications}.
 *
 * <ul>
 *   <li><b>Barricades.</b> A plank or sheet-metal barricade is placed with the use button like any
 *       block, or straight into a shattered window or a broken doorway, whose frame it replaces.
 *       It is a solid, opaque panel: bodies, sight and light stop at it and sound loses as much
 *       through it as through any wall. Nailing or bolting one up is loud.</li>
 *   <li><b>Braces.</b> Used on a closed door of the player's own, a door brace bars it from the
 *       player's side and reinforces the bar. Lifting the bar from that side takes the brace down,
 *       back into the pack while it is still sound; from the other side the door holds.</li>
 *   <li><b>Attacks.</b> A Breacher plans through plank barricades and wooden doors it means to
 *       break: each blow takes {@value #BREACHER_BLOW} strength off a barricade, which gives way at
 *       none, or off a door's brace, which splinters before the door's own blows begin. Sheet metal
 *       and metal doors hold against it, as they always have. Every other threat walks round.</li>
 *   <li><b>Repairs.</b> A plank mends a damaged plank barricade or brace, scrap a sheet-metal one;
 *       a salvaged door hangs in a broken doorway and a window fills a shattered frame.</li>
 *   <li><b>Removal.</b> A barricade the player regrets is taken down by crouching and holding the
 *       attack button, from either side and with no tool, returning most of what it cost
 *       ({@code SalvageTable}); so no fortification can shut its builder in.</li>
 * </ul>
 * Creative builds and repairs without spending anything; a Creative body is never hunted, so no
 * attack is aimed at it. Survivors' doors and a compound's gate are theirs and are not braced.
 */
public final class Fortification {
    /** Strength a Breacher's blow takes off a barricade or a brace. */
    public static final float BREACHER_BLOW = 15f;
    /** Strength the player's own blow takes off a brace from the far side of its door. */
    static final float PLAYER_BLOW = 45f;
    /** Strength a plank puts back into a plank barricade or a brace, and scrap into a metal one. */
    static final float PLANK_REPAIR = 30f, SCRAP_REPAIR = 40f;
    /** Hammering boards up, bracing a door or hanging one. */
    static final float HAMMER_NOISE_RADIUS = 20f, HAMMER_NOISE = 0.45f;
    /** A barricade giving way, or a brace splintering. */
    static final float GIVE_WAY_NOISE_RADIUS = 30f, GIVE_WAY_NOISE = 0.65f;

    /** What a Breacher's blow on a fortification did. */
    public enum Blow {
        /** No fortification stood in the way: the blow lands on the door itself. */
        NONE,
        /** A barricade or a brace took the blow and still stands, or a brace splintered. */
        ABSORBED,
        /** The barricade it struck gave way. */
        GAVE_WAY
    }

    private Fortification() {
    }

    /** True in a world whose rules include fortification: layout fourteen. */
    static boolean applies(World w) {
        return SalvageTable.applies(w);
    }

    // ------------------------------------------------------------------
    // The use button
    // ------------------------------------------------------------------

    /**
     * The use button with an item held, aimed at a cell: board it up, brace a door, repair, hang a
     * door or fill a window frame.
     *
     * @return true when the press was a fortification action, done or refused with a reason
     */
    static boolean rightClick(Game g, ItemStack held) {
        if (held == null || g.targetHit == null || !applies(g.world)) { return false; }
        Vec3i aimed = new Vec3i(g.targetHit.x(), g.targetHit.y(), g.targetHit.z());
        BlockType at = g.world.getBlock(aimed.x(), aimed.y(), aimed.z());
        BlockType panel = held.type.places();
        if (UrbanFortifications.isBarricade(panel)) {
            return barricade(g, aimed, at, panel);
        }
        if (held.type == ItemType.DOOR_BRACE && Fixtures.isClosedDoor(at)) {
            brace(g, aimed);
            return true;
        }
        if (held.type == ItemType.PLANK && at == BlockType.BARRICADE
                || held.type == ItemType.SCRAP && at == BlockType.BARRICADE_METAL) {
            repairBarricade(g, aimed, at, held);
            return true;
        }
        if (held.type == ItemType.PLANK && Fixtures.isClosedDoor(at) && braceAt(g, aimed) != null) {
            repairBrace(g, aimed, held);
            return true;
        }
        if ((held.type == ItemType.WOOD_DOOR || held.type == ItemType.METAL_DOOR) && at == BlockType.BROKEN_DOOR) {
            hangDoor(g, aimed, held);
            return true;
        }
        if (held.type == ItemType.WINDOW && at == BlockType.BROKEN_WINDOW) {
            glaze(g, aimed, held);
            return true;
        }
        return false;
    }

    /** What the use button would do with the held item at the aimed cell, or null. */
    static String prompt(Game g, ItemStack held) {
        if (held == null || g.targetHit == null || !applies(g.world)) { return null; }
        Vec3i aimed = new Vec3i(g.targetHit.x(), g.targetHit.y(), g.targetHit.z());
        BlockType at = g.world.getBlock(aimed.x(), aimed.y(), aimed.z());
        BlockType panel = held.type.places();
        if (UrbanFortifications.isBarricade(panel) && Fixtures.isBroken(at)) {
            return "[RMB] Board up the " + (at == BlockType.BROKEN_WINDOW ? "shattered window" : "broken doorway") + " (loud)";
        }
        if (held.type == ItemType.DOOR_BRACE && Fixtures.isClosedDoor(at)) {
            String refusal = braceRefusal(g, aimed);
            return refusal != null ? refusal : "[RMB] Brace the door from this side (loud)";
        }
        if (held.type == ItemType.PLANK && at == BlockType.BARRICADE || held.type == ItemType.SCRAP && at == BlockType.BARRICADE_METAL) {
            float h = g.world.urban.fortifications.health(at, aimed);
            return h >= UrbanFortifications.strength(at) ? null
                    : "[RMB] Repair the barricade (" + strength(h, UrbanFortifications.strength(at)) + ")";
        }
        if (held.type == ItemType.PLANK && Fixtures.isClosedDoor(at)) {
            Float h = braceAt(g, aimed);
            return h == null || h >= UrbanFortifications.BRACE_STRENGTH ? null
                    : "[RMB] Repair the brace (" + strength(h, UrbanFortifications.BRACE_STRENGTH) + ")";
        }
        if ((held.type == ItemType.WOOD_DOOR || held.type == ItemType.METAL_DOOR) && at == BlockType.BROKEN_DOOR) {
            return "[RMB] Hang the door in the broken doorway";
        }
        if (held.type == ItemType.WINDOW && at == BlockType.BROKEN_WINDOW) {
            return "[RMB] Fit the window into the empty frame";
        }
        return null;
    }

    /** The HUD line for an aimed barricade: its kind and strength left. */
    static String barricadeInfo(Game g, Vec3i cell, BlockType t) {
        if (!applies(g.world)) { return null; }
        return t.displayName + " (" + strength(g.world.urban.fortifications.health(t, cell), UrbanFortifications.strength(t)) + ")";
    }

    private static String strength(float h, float max) {
        return Math.round(h) + "/" + Math.round(max);
    }

    /** A barricade into a broken opening's own cell, or the empty cell in front of the aimed face. */
    private static boolean barricade(Game g, Vec3i aimed, BlockType at, BlockType panel) {
        Vec3i cell = Fixtures.isBroken(at) ? aimed
                : aimed.offset(g.targetHit.nx(), g.targetHit.ny(), g.targetHit.nz());
        BlockType there = g.world.getBlock(cell.x(), cell.y(), cell.z());
        if (!g.world.isColumnLoaded(cell.x(), cell.z()) || !(there.isReplaceable() || Fixtures.isBroken(there))) {
            return false; // nothing to board: the press falls through to whatever else it does
        }
        if (FixtureActions.occupied(g, cell.x(), cell.y(), cell.z(), 1)) {
            g.log("Something is in the way.");
            return true;
        }
        g.world.setBlock(cell.x(), cell.y(), cell.z(), panel, true);
        if (g.world.getBlock(cell.x(), cell.y(), cell.z()) != panel) {
            return true; // the world refused it
        }
        spend(g);
        hammer(g, cell, panel, HAMMER_NOISE_RADIUS, HAMMER_NOISE);
        g.log(there == BlockType.BROKEN_WINDOW ? "You board up the shattered window."
                : there == BlockType.BROKEN_DOOR ? "You barricade the broken doorway."
                : "You put up a " + panel.displayName.toLowerCase(java.util.Locale.ROOT) + ".");
        return true;
    }

    // ------------------------------------------------------------------
    // Braces
    // ------------------------------------------------------------------

    private static Vec3i bottomOf(Game g, Vec3i cell) {
        Fixtures.Kind kind = Fixtures.kindOf(g.world.getBlock(cell.x(), cell.y(), cell.z()));
        return kind == null ? cell : new Vec3i(cell.x(), Fixtures.stackBottom(g.world, cell.x(), cell.y(), cell.z(), kind), cell.z());
    }

    /** The strength left in a door's brace, by any of its cells, or null when it has none. */
    static Float braceAt(Game g, Vec3i cell) {
        if (!applies(g.world)) { return null; }
        float h = g.world.urban.fortifications.brace(g.world, bottomOf(g, cell));
        return h < 0 ? null : h;
    }

    /** Why the aimed door cannot be braced, or null when it can. */
    private static String braceRefusal(Game g, Vec3i aimed) {
        Vec3i bottom = bottomOf(g, aimed);
        World w = g.world;
        Fixtures.Kind kind = Fixtures.kindOf(w.getBlock(bottom.x(), bottom.y(), bottom.z()));
        if (kind == null || !kind.door || kind == Fixtures.Kind.HIDDEN_PANEL
                || Fixtures.stackHeight(w, bottom.x(), bottom.y(), bottom.z(), kind) < 2) {
            return "Only a whole door can be braced";
        }
        if (w.urban.compoundLockAt(bottom.x(), bottom.y(), bottom.z()) != FixtureStates.LOCK_NONE
                || SalvageTable.survivorOwner(w, bottom) != UrbanIds.NONE) {
            return "This door is not yours to brace";
        }
        int lock = w.fixtureLockAt(bottom.x(), bottom.y(), bottom.z());
        if (lock == FixtureStates.LOCK_KEYED) {
            return "Locked: a brace needs a door you can bar";
        }
        if (lock == FixtureStates.LOCK_BARRED && !FixtureActions.onPlayersBarSide(g, bottom.x(), bottom.y(), bottom.z())) {
            return "Barred from the other side";
        }
        if (w.urban.fortifications.brace(w, bottom) >= 0) {
            return "Already braced";
        }
        if (!w.urban.fortifications.canBrace(w, bottom)) {
            return "No more doors can be braced in this world";
        }
        return null;
    }

    private static void brace(Game g, Vec3i aimed) {
        String refusal = braceRefusal(g, aimed);
        if (refusal != null) {
            g.log(refusal + ".");
            return;
        }
        Vec3i bottom = bottomOf(g, aimed);
        World w = g.world;
        if (w.fixtureLockAt(bottom.x(), bottom.y(), bottom.z()) != FixtureStates.LOCK_BARRED
                && !FixtureActions.barFromPlayerSide(g, bottom.x(), bottom.y(), bottom.z(), 2)) {
            g.log("This door is jammed and will not move.");
            return;
        }
        w.urban.fortifications.setBrace(w, bottom, UrbanFortifications.BRACE_STRENGTH);
        spend(g);
        hammer(g, bottom, w.getBlock(bottom.x(), bottom.y(), bottom.z()), HAMMER_NOISE_RADIUS, HAMMER_NOISE);
        g.log("You bar the door and brace it from this side.");
    }

    /**
     * Lifting the bar of a braced door from its side takes the brace down: back into the pack
     * while it holds at least half its strength, otherwise it falls apart.
     *
     * @return false when the sound brace does not fit, so the bar stays down
     */
    static boolean takeDownBrace(Game g, int x, int bottom, int z) {
        if (!applies(g.world)) { return true; }
        Vec3i cell = new Vec3i(x, bottom, z);
        float h = g.world.urban.fortifications.brace(g.world, cell);
        if (h < 0) { return true; }
        if (h >= UrbanFortifications.BRACE_STRENGTH / 2) {
            if (g.player.inventory.addStack(new ItemStack(ItemType.DOOR_BRACE, 1)) > 0) {
                g.log("No room to carry the brace; it stays up.");
                return false;
            }
            g.log("You take down the brace.");
        } else {
            g.log("The cracked brace falls apart as you take it down.");
        }
        g.world.urban.fortifications.removeBrace(cell);
        return true;
    }

    /**
     * The player's own break on a braced door from the far side: the brace takes the blow first.
     *
     * @return true when a brace took it, so the door is not forced by this break
     */
    static boolean braceTakesPlayerBlow(Game g, Vec3i door) {
        if (!applies(g.world) || g.player.abilities.instantBuild()) { return false; }
        Vec3i bottom = bottomOf(g, door);
        float left = g.world.urban.fortifications.damageBrace(g.world, bottom, PLAYER_BLOW);
        if (left < 0) { return false; }
        float x = bottom.x() + 0.5f, y = bottom.y() + 1f, z = bottom.z() + 0.5f;
        g.audio.playBlockHit(BlockType.WOOD_DOOR, x, y, z);
        g.noise.emit(g, x, y, z, FixtureActions.FORCED_NOISE_RADIUS, FixtureActions.FORCED_NOISE, "door-forced", true, g.player);
        g.log(left > 0 ? "The door is braced from the other side; it gives a little."
                : "The brace behind the door splinters.");
        return true;
    }

    private static void repairBrace(Game g, Vec3i aimed, ItemStack held) {
        Vec3i bottom = bottomOf(g, aimed);
        float h = g.world.urban.fortifications.brace(g.world, bottom);
        if (!FixtureActions.onPlayersBarSide(g, bottom.x(), bottom.y(), bottom.z())) {
            g.log("The brace is on the other side.");
            return;
        }
        if (h >= UrbanFortifications.BRACE_STRENGTH) {
            g.log("The brace is sound.");
            return;
        }
        float mended = Math.min(UrbanFortifications.BRACE_STRENGTH, h + PLANK_REPAIR);
        g.world.urban.fortifications.setBrace(g.world, bottom, mended);
        spend(g);
        hammer(g, bottom, BlockType.PLANK, HAMMER_NOISE_RADIUS, HAMMER_NOISE);
        g.log("You nail a plank across the brace (" + strength(mended, UrbanFortifications.BRACE_STRENGTH) + ").");
    }

    // ------------------------------------------------------------------
    // Repairs
    // ------------------------------------------------------------------

    private static void repairBarricade(Game g, Vec3i cell, BlockType t, ItemStack held) {
        UrbanFortifications f = g.world.urban.fortifications;
        if (f.health(t, cell) >= UrbanFortifications.strength(t)) {
            g.log("The barricade is sound.");
            return;
        }
        float mended = f.repair(t, cell, t == BlockType.BARRICADE ? PLANK_REPAIR : SCRAP_REPAIR);
        spend(g);
        hammer(g, cell, t, HAMMER_NOISE_RADIUS, HAMMER_NOISE);
        g.log("You patch the barricade (" + strength(mended, UrbanFortifications.strength(t)) + ").");
    }

    /** A salvaged door hung whole in a broken doorway: closed and unlocked. */
    private static void hangDoor(Game g, Vec3i aimed, ItemStack held) {
        World w = g.world;
        int bottom = aimed.y();
        if (w.getBlock(aimed.x(), bottom - 1, aimed.z()) == BlockType.BROKEN_DOOR) { bottom--; }
        if (w.getBlock(aimed.x(), bottom + 1, aimed.z()) != BlockType.BROKEN_DOOR
                || w.getBlock(aimed.x(), bottom + 2, aimed.z()) == BlockType.BROKEN_DOOR) {
            g.log("A door needs a whole doorway, two cells high.");
            return;
        }
        if (FixtureActions.occupied(g, aimed.x(), bottom, aimed.z(), 2)) {
            g.log("Something is in the doorway.");
            return;
        }
        BlockType door = held.type == ItemType.METAL_DOOR ? BlockType.METAL_DOOR : BlockType.WOOD_DOOR;
        w.setBlock(aimed.x(), bottom, aimed.z(), door, true);
        w.setBlock(aimed.x(), bottom + 1, aimed.z(), door, true);
        spend(g);
        hammer(g, new Vec3i(aimed.x(), bottom, aimed.z()), door, HAMMER_NOISE_RADIUS, HAMMER_NOISE);
        g.log("You hang the door in the broken doorway.");
    }

    private static void glaze(Game g, Vec3i cell, ItemStack held) {
        if (FixtureActions.occupied(g, cell.x(), cell.y(), cell.z(), 1)) {
            g.log("Something is in the way.");
            return;
        }
        g.world.setBlock(cell.x(), cell.y(), cell.z(), BlockType.WINDOW, true);
        spend(g);
        g.audio.playWindow(cell.x() + 0.5f, cell.y() + 0.5f, cell.z() + 0.5f);
        g.log("You fit the window into the empty frame.");
    }

    /** One of the held item, unless Creative's unlimited items keep it. */
    private static void spend(Game g) {
        if (!g.player.abilities.unlimitedItems()) { g.player.inventory.shrink(g.player.hotbarSel, 1); }
    }

    /** The sound and the real noise of fixing something up. */
    private static void hammer(Game g, Vec3i cell, BlockType t, float radius, float intensity) {
        float x = cell.x() + 0.5f, y = cell.y() + 0.5f, z = cell.z() + 0.5f;
        g.audio.playBlockHit(t, x, y, z);
        g.audio.playBlockPlace(x, y, z);
        g.particles.blockDust(t, x, y, z, 4);
        g.noise.emit(g, x, y, z, radius, intensity, "barricade", true, g.player);
    }

    // ------------------------------------------------------------------
    // Attacks
    // ------------------------------------------------------------------

    /**
     * A Breacher's blow on what it is hammering at: a plank barricade cell, or a wooden door whose
     * brace takes the blow first.
     *
     * @param by the body striking, so it does not hear its own crash
     */
    public static Blow breacherBlow(Game g, Vec3i target, Entity by) {
        if (!applies(g.world)) { return Blow.NONE; }
        World w = g.world;
        BlockType t = w.getBlock(target.x(), target.y(), target.z());
        float x = target.x() + 0.5f, y = target.y() + 0.5f, z = target.z() + 0.5f;
        if (t == BlockType.BARRICADE) {
            if (w.urban.fortifications.damage(t, target, BREACHER_BLOW) > 0) { return Blow.ABSORBED; }
            w.setBlock(target.x(), target.y(), target.z(), BlockType.AIR, true);
            g.audio.playDoorForced(x, y, z);
            g.particles.blockDust(t, x, y, z, 10);
            g.noise.emit(g, x, y, z, GIVE_WAY_NOISE_RADIUS, GIVE_WAY_NOISE, "barricade-break", false, by);
            if (g.player.isPerceivableByAi() && g.player.distSqTo(x, y, z) < 40 * 40) {
                g.log("A barricade gives way with a crash.");
            }
            return Blow.GAVE_WAY;
        }
        float left = w.urban.fortifications.damageBrace(w, target, BREACHER_BLOW);
        if (left < 0) { return Blow.NONE; }
        if (left == 0) {
            g.noise.emit(g, x, y + 0.5f, z, GIVE_WAY_NOISE_RADIUS, GIVE_WAY_NOISE, "barricade-break", false, by);
            if (g.player.isPerceivableByAi() && g.player.distSqTo(x, y, z) < 40 * 40) {
                g.log("A brace splinters behind a door.");
            }
        }
        return Blow.ABSORBED;
    }
}
