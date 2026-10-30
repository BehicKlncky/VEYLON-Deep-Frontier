package com.veylon;

import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ToolKind;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Fixtures;
import com.veylon.world.urban.SalvageTable;
import com.veylon.world.urban.UrbanContainers;
import com.veylon.world.urban.UrbanIds;

import java.util.ArrayList;
import java.util.List;

/**
 * Taking things apart for their materials in a layout-fourteen city (urban milestone 18): furniture,
 * doors, guard rails, signs, the workshop bench and burnt-out cars, by the {@link SalvageTable}.
 *
 * <ul>
 *   <li><b>How.</b> Crouch and hold the attack button on the piece. Something with no form to
 *       carry away (a sign, a rail, a wreck) is taken apart by an ordinary hold as well; anything
 *       that has one is still carried away whole by a standing hold, as before.</li>
 *   <li><b>Time.</b> The entry's seconds of steady work, halved by its tool (an axe for wood, a
 *       pry tool for metal, a knife for cloth); metal will not come apart without a pry tool at
 *       all. Looking away or standing up starts the work again.</li>
 *   <li><b>Noise.</b> Every blow is a real {@code WorldNoise} event that anything in earshot may
 *       hear and come to: wood {@value #WOOD_NOISE_RADIUS} blocks, metal
 *       {@value #METAL_NOISE_RADIUS}, and the last wrench louder still.</li>
 *   <li><b>One transfer.</b> Everything a piece gives, its materials and whatever a container still
 *       held, must fit before anything moves; otherwise nothing happens and the player is told
 *       why. A container's contents leave the world's storage exactly once (rolled on first touch
 *       if it was never searched, as hand salvage always has been) and its identity is retired, so
 *       emptying it and taking it apart, or the reverse, can never pay out twice. What is taken
 *       apart is gone as a recorded edit.</li>
 *   <li><b>Owners.</b> Taking apart survivors' furniture or storage wrecks their property, with
 *       the consequence wrecking their stores always has.</li>
 * </ul>
 * Creative removes blocks outright and takes nothing, as every Creative break does.
 */
final class Dismantling {
    /** Noise of each blow while working wood, and of each wrench at metal. */
    static final float WOOD_NOISE_RADIUS = 16f;
    static final float WOOD_NOISE = 0.3f;
    static final float METAL_NOISE_RADIUS = 26f;
    static final float METAL_NOISE = 0.45f;
    /** The piece coming apart at the end. */
    static final float DONE_NOISE_RADIUS = 32f;
    static final float DONE_NOISE = 0.55f;
    static final float METAL_DONE_NOISE_RADIUS = 36f;
    static final float METAL_DONE_NOISE = 0.6f;
    /** The right tool works twice as fast. */
    static final float TOOL_SPEEDUP = 2f;
    private static final float HIT_INTERVAL = 0.32f;
    private static final int DUST = 6;
    private static final float DURABILITY_COST = 1f;

    private final Game game;
    /** The cell being worked and whether it is being taken apart, so a change of mind restarts it. */
    private Vec3i workTarget;
    private float progress;
    private float hitTimer;

    Dismantling(Game game) {
        this.game = game;
    }

    void reset() {
        workTarget = null;
        progress = 0;
        hitTimer = 0;
    }

    /** Fraction of the work done on the current piece, for the HUD's break overlay. */
    float progress() {
        return progress;
    }

    /**
     * True when holding the attack button on this block takes it apart rather than breaking it:
     * a layout-fourteen piece with salvage, crouched for, or one whose break has no job of its own.
     */
    boolean applies(Vec3i pos, BlockType t) {
        if (game.player.abilities.instantBuild() || SalvageTable.entry(game.world, pos, t) == null) {
            return false;
        }
        return game.player.crouching || !breaksAlone(pos, t);
    }

    /**
     * A standing break of this block already does something: it forces a closed door, or carries
     * away the block's own form (furniture, an open door, a legacy station).
     */
    private boolean breaksAlone(Vec3i pos, BlockType t) {
        if (Fixtures.isClosed(t)) { return true; }
        return FixtureActions.isUrbanBlock(t) ? FixtureActions.salvageOf(game.world, pos, t) != null : t.drop != null;
    }

    /** One frame of holding the attack button on a piece being taken apart. */
    void work(float dt, Vec3i pos, BlockType t) {
        if (!pos.equals(workTarget)) {
            workTarget = pos;
            progress = 0;
            hitTimer = 0;
        }
        game.miningTarget = pos;
        game.miningProgress = progress;
        SalvageTable.Entry e = SalvageTable.entry(game.world, pos, t);
        ItemStack held = game.player.selected();
        if (e == null || SalvageTable.refusal(game.world, pos, t, toolOf(held)) != null) {
            progress = 0;
            game.miningProgress = 0;
            return;
        }
        game.swingTimer = Math.max(game.swingTimer, 0.18f);
        float x = pos.x() + 0.5f, y = pos.y() + 0.5f, z = pos.z() + 0.5f;
        if ((hitTimer -= dt) <= 0) {
            hitTimer = HIT_INTERVAL;
            game.audio.playBlockHit(t, x, y, z);
            game.particles.blockDust(t, x, y, z, DUST / 2);
            game.noise.emit(game, x, y, z, e.metal() ? METAL_NOISE_RADIUS : WOOD_NOISE_RADIUS,
                    e.metal() ? METAL_NOISE : WOOD_NOISE, e.metal() ? "salvage-metal" : "salvage", true, game.player);
        }
        float speed = toolOf(held) == e.tool() ? TOOL_SPEEDUP : 1f;
        progress += dt * speed / e.seconds();
        game.miningProgress = Math.min(1f, progress);
        if (progress >= 1f) {
            complete(pos);
            reset();
            game.miningProgress = 0;
            game.miningTarget = null;
        }
    }

    private static ToolKind toolOf(ItemStack held) {
        return held == null ? ToolKind.NONE : held.type.tool;
    }

    /**
     * Takes apart the piece at a cell once its work is done: the gameplay command the held button
     * ends in, and the one tests drive. Everything it gives must fit or nothing moves.
     *
     * @return true when the piece was taken apart
     */
    boolean complete(Vec3i pos) {
        if (pos == null) { return false; }
        BlockType t = game.world.getBlock(pos.x(), pos.y(), pos.z());
        SalvageTable.Entry e = SalvageTable.entry(game.world, pos, t);
        if (e == null || game.player.abilities.instantBuild()) { return false; }
        ItemStack held = game.player.selected();
        String refusal = SalvageTable.refusal(game.world, pos, t, toolOf(held));
        if (refusal != null) {
            game.log(refusal + ".");
            return false;
        }
        UrbanContainers.Entry container = null;
        if (UrbanContainers.manages(game.world) && Fixtures.isContainer(t)) {
            container = game.world.urban.containers.touch(game.world, pos);
            if (container == null) {
                game.log("This container cannot be changed: the world's storage record is full.");
                return false;
            }
        }
        List<ItemStack> yields = SalvageTable.yields(game.world, pos, t);
        List<ItemStack> incoming = new ArrayList<>();
        Inventory contents = Fixtures.isContainer(t) ? game.world.crateContents.get(pos) : null;
        if (contents != null) {
            for (int i = 0; i < contents.size(); i++) {
                if (contents.get(i) != null) { incoming.add(contents.get(i).copy()); }
            }
        }
        incoming.addAll(yields);
        if (!FixtureActions.fits(game.player.inventory, incoming)) {
            game.log(contents != null && !contents.isEmpty()
                    ? "No room for what is inside and what it gives. Empty the " + e.noun() + " first."
                    : "No room to carry what the " + e.noun() + " gives.");
            return false;
        }
        long owner = container != null ? UrbanIds.NONE : SalvageTable.survivorOwner(game.world, pos);
        String spill = game.refuges.utilities.spillNote(pos); // a purifier's water, read before it goes
        if (container != null) {
            game.crates.onContainerSalvaged(container);
            game.world.urban.containers.moved(container);
        }
        // The one transfer: the contents leave the world's storage once, then the materials.
        Inventory moved = Fixtures.isContainer(t) ? game.world.crateContents.remove(pos) : null;
        if (moved != null) {
            for (int i = 0; i < moved.size(); i++) {
                if (moved.get(i) != null) { game.player.inventory.addStack(moved.get(i)); }
            }
        }
        Fixtures.Kind kind = Fixtures.kindOf(t);
        if (kind != null && kind.door) {
            FixtureActions.replaceOpening(game.world, pos, t, BlockType.AIR);
        } else {
            game.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.AIR, true);
        }
        if (t == BlockType.RAIN_COLLECTOR) {
            game.world.collectorWater.remove(pos); // the water runs out of a basin taken apart
        }
        for (ItemStack s : yields) { game.player.inventory.addStack(s.copy()); }
        if (owner != UrbanIds.NONE) { game.crates.onSurvivorStorageDestroyed(owner); }
        if (toolOf(held) == e.tool()) { game.combat.consumeDurability(held, DURABILITY_COST); }
        float x = pos.x() + 0.5f, y = pos.y() + 0.5f, z = pos.z() + 0.5f;
        game.audio.playBlockBreak(x, y, z);
        game.particles.blockDust(t, x, y, z, DUST * 2);
        game.noise.emit(game, x, y, z, e.metal() ? METAL_DONE_NOISE_RADIUS : DONE_NOISE_RADIUS,
                e.metal() ? METAL_DONE_NOISE : DONE_NOISE, "structure-break", true, game.player);
        game.log("You take apart the " + e.noun() + ": " + SalvageTable.describe(yields) + "."
                + (spill == null ? "" : " " + spill));
        return true;
    }

    /** The hint a standing hold's own line is followed by: crouching takes the piece apart. */
    static final String CROUCH_HINT = "crouch and hold LMB to take it apart";

    /**
     * The HUD hint for a piece that can be taken apart, or null: when a hold takes it apart, what it
     * gives (or why it cannot yet); otherwise {@link #CROUCH_HINT}.
     */
    String prompt(Vec3i pos, BlockType t) {
        SalvageTable.Entry e = SalvageTable.entry(game.world, pos, t);
        if (e == null || game.player.abilities.instantBuild()) { return null; }
        if (!applies(pos, t)) {
            // A closed door only comes down open: crouching at it would be refused, so it is no hint (urban 21).
            com.veylon.world.Fixtures.Kind kind = com.veylon.world.Fixtures.kindOf(t);
            return kind != null && kind.door && !com.veylon.world.Fixtures.isOpen(t) ? null : CROUCH_HINT;
        }
        String refusal = SalvageTable.refusal(game.world, pos, t, toolOf(game.player.selected()));
        if (refusal != null) { return refusal; }
        SalvageTable.Condition c = SalvageTable.condition(game.world, pos, t);
        return "[LMB] Hold to take apart the " + (c == SalvageTable.Condition.SOUND ? "" : c.label + " ") + e.noun()
                + " for " + SalvageTable.describe(SalvageTable.yields(game.world, pos, t)) + " (loud)";
    }

    /**
     * {@link #prompt} cut short to follow another action's line, so the HUD's single line still
     * shows what the piece gives: "hold LMB for 2 scrap metal". The crouch hint and a refusal are
     * already short and come back unchanged.
     */
    String shortPrompt(Vec3i pos, BlockType t) {
        String full = prompt(pos, t);
        if (full == null || !full.startsWith("[LMB] Hold to take apart")) { return full; }
        return "hold LMB for " + SalvageTable.describe(SalvageTable.yields(game.world, pos, t));
    }
}
