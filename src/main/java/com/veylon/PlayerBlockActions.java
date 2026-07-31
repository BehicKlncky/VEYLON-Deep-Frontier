package com.veylon;

import com.veylon.entity.Creature;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.item.Inventory;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.item.ToolKind;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.RackBatch;

import java.util.Random;

/**
 * Breaking and placing blocks: the hold-to-mine timer, what a broken block
 * drops or spills, and the consequences of destroying something that belongs
 * to someone.
 *
 * <p>Mining is split in two on purpose. {@link #mine(float)} advances the timer
 * and the feedback (swing, dust, sound, noise events) each frame;
 * {@link #completePlayerBlockBreak(Vec3i)} is the command that actually removes
 * the block. Keeping the outcome in its own command is what lets gameplay tests
 * exercise real drops, durability, container spill and reputation without
 * simulating five seconds of held mouse button.
 */
final class PlayerBlockActions {

    // Mining feedback.
    /** Swing animation floor while mining, in seconds. */
    private static final float MINE_SWING_SECONDS = 0.18f;
    /** Player noise added per second of mining. */
    private static final float MINE_NOISE_PER_SECOND = 0.35f;
    /** Minimum gap between mining impact sounds, in seconds. */
    private static final float HIT_SOUND_INTERVAL = 0.32f;
    private static final int MINE_DUST_PARTICLES = 3;
    private static final float MINE_NOISE_RADIUS = 24f;
    private static final float MINE_NOISE_STRENGTH = 0.35f;
    /** Hardness floor, so a near-zero value cannot divide progress to infinity. */
    private static final float MIN_EFFECTIVE_HARDNESS = 0.05f;

    // Breaking.
    /** Leaves only sometimes drop, so canopies do not flood the inventory. */
    private static final double LEAF_DROP_CHANCE = 0.35;
    private static final int BREAK_DUST_PARTICLES = 12;
    private static final float BREAK_DURABILITY_COST = 1f;
    private static final float BREAK_NOISE_SELF = 0.3f;
    /** Breaking built structures carries much further than breaking terrain. */
    private static final float STRUCTURE_NOISE_RADIUS = 32f;
    private static final float STRUCTURE_NOISE_STRENGTH = 0.55f;
    private static final float TERRAIN_NOISE_RADIUS = 22f;
    private static final float TERRAIN_NOISE_STRENGTH = 0.35f;
    /** Trust lost for wrecking camp property, and the radius the camp watches. */
    private static final int CAMP_VANDALISM_TRUST = -15;
    private static final int CAMP_VANDALISM_RADIUS = 14;

    // Placing.
    /** Seconds of fuel a freshly placed campfire starts with. */
    private static final float CAMPFIRE_INITIAL_FUEL = 300f;
    private static final int CRATE_SLOTS = 12;
    private static final int PLACE_DUST_PARTICLES = 5;

    private final Game game;
    /** Break-outcome rolls; reseeded per world by {@code Game.reseedSimulation}. */
    private final Random rng = new Random();

    PlayerBlockActions(Game game) {
        this.game = game;
    }

    void setRandomSeed(long seed) {
        rng.setSeed(seed);
    }

    // ------------------------------------------------------------------
    // Mining
    // ------------------------------------------------------------------

    /** Advances the hold-to-mine timer against the currently targeted block. */
    void mine(float dt) {
        BlockType t = game.targetHit.type();
        if (t.hardness < 0) {
            return; // indestructible
        }
        Vec3i pos = new Vec3i(game.targetHit.x(), game.targetHit.y(), game.targetHit.z());
        if (!pos.equals(game.miningTarget)) {
            game.miningTarget = pos;
            game.miningProgress = 0;
        }
        ItemStack held = game.player.selected();
        float mult = 1f;
        if (held != null && held.type.tool != ToolKind.NONE && held.type.tool == t.preferredTool) {
            mult = held.type.toolPower;
        }
        if (t.requiresTool && (held == null || held.type.tool != t.preferredTool)) {
            game.miningProgress = 0;
            return;
        }
        game.swingTimer = Math.max(game.swingTimer, MINE_SWING_SECONDS);
        game.player.noise = Math.min(1f, game.player.noise + MINE_NOISE_PER_SECOND * dt);
        if (game.hitSoundTimer <= 0) {
            game.hitSoundTimer = HIT_SOUND_INTERVAL;
            game.audio.playBlockHit(t, pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f);
            game.particles.blockDust(t, pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f,
                    MINE_DUST_PARTICLES);
            game.noise.emit(game, pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f,
                    MINE_NOISE_RADIUS, MINE_NOISE_STRENGTH, "mining", true, game.player);
        }
        game.miningProgress += dt * mult / Math.max(MIN_EFFECTIVE_HARDNESS, t.hardness);
        if (game.miningProgress >= 1f) {
            completePlayerBlockBreak(pos);
            game.miningProgress = 0;
            game.miningTarget = null;
        }
    }

    /**
     * Completes a player mining action after the hold-to-mine timer succeeds.
     * Keeping the block outcome in this command lets gameplay integration tests
     * exercise real drops, durability, changed-block persistence and hooks.
     */
    boolean completePlayerBlockBreak(Vec3i pos) {
        if (pos == null) {
            return false;
        }
        BlockType type = game.world.getBlock(pos.x(), pos.y(), pos.z());
        ItemStack held = game.player.selected();
        if (type.hardness < 0
                || (type.requiresTool && (held == null || held.type.tool != type.preferredTool))) {
            return false;
        }
        breakBlock(pos, type, held);
        return true;
    }

    private void breakBlock(Vec3i pos, BlockType t, ItemStack held) {
        int brokenCrateContents = spillContainerContents(pos, t);
        awardDrops(t, held);

        game.world.setBlock(pos.x(), pos.y(), pos.z(), BlockType.AIR, true);
        game.audio.playBlockBreak(pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f);
        game.particles.blockDust(t, pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f,
                BREAK_DUST_PARTICLES);
        game.combat.consumeDurability(held, BREAK_DURABILITY_COST);
        game.player.noise = Math.min(1f, game.player.noise + BREAK_NOISE_SELF);

        boolean structure = isBuiltStructure(t);
        game.noise.emit(game, pos.x() + 0.5f, pos.y() + 0.5f, pos.z() + 0.5f,
                structure ? STRUCTURE_NOISE_RADIUS : TERRAIN_NOISE_RADIUS,
                structure ? STRUCTURE_NOISE_STRENGTH : TERRAIN_NOISE_STRENGTH,
                structure ? "structure-break" : "mining-break", true, game.player);

        applyVandalismConsequences(pos, t, brokenCrateContents);
        if (t == BlockType.POWDER_KEG) {
            game.world.kegFuses.remove(pos);
        }
    }

    /**
     * Empties any container state the block held.
     *
     * @return the item count spilled out of a crate, which drives the theft
     *         penalty a settlement applies for smashing its storage
     */
    private int spillContainerContents(Vec3i pos, BlockType t) {
        int brokenCrateContents = 0;
        if (t == BlockType.CRATE) {
            Inventory crate = game.world.crateContents.remove(pos);
            if (crate != null) {
                for (int i = 0; i < crate.size(); i++) {
                    ItemStack s = crate.get(i);
                    if (s != null) {
                        brokenCrateContents += s.count;
                        game.player.inventory.addStack(s);
                    }
                }
            }
        }
        if (t == BlockType.CAMPFIRE) {
            game.world.campfireFuel.remove(pos);
        }
        if (t == BlockType.DRYING_RACK) {
            RackBatch batch = game.world.rackBatches.remove(pos);
            if (batch != null) {
                game.player.inventory.add(
                        batch.done() ? batch.output() : batch.input, batch.count);
            }
        }
        if (t == BlockType.RAIN_COLLECTOR) {
            game.world.collectorWater.remove(pos);
        }
        if (t == BlockType.BEACON && pos.equals(game.world.beaconPos)) {
            game.world.beaconPos = null;
            game.world.beaconStage = -1;
            game.log("You dismantled the distress beacon.");
        }
        return brokenCrateContents;
    }

    private void awardDrops(BlockType t, ItemStack held) {
        if (t.drop != null) {
            boolean dropOk = !t.requiresTool || (held != null && held.type.tool == t.preferredTool);
            if (t == BlockType.LEAVES) {
                dropOk = rng.nextFloat() < LEAF_DROP_CHANCE;
            }
            if (dropOk) {
                game.player.inventory.add(t.drop, t.dropCount);
            }
        }
        if (t == BlockType.BERRY_BUSH) {
            game.player.inventory.add(ItemType.FIBER, 1);
        }
    }

    /** Built blocks make a louder, more suspicious noise than natural terrain. */
    private static boolean isBuiltStructure(BlockType t) {
        return t == BlockType.WALL || t == BlockType.STONE_BRICK
                || t == BlockType.GATE || t == BlockType.CRATE || t == BlockType.CAMPFIRE
                || t == BlockType.CAMP_BED || t == BlockType.PLANK || t == BlockType.LOG
                || t == BlockType.WORKBENCH || t == BlockType.FURNACE || t == BlockType.ANVIL;
    }

    /** Camp trust and settlement reputation both react to destroyed property. */
    private void applyVandalismConsequences(Vec3i pos, BlockType t, int brokenCrateContents) {
        if (game.world.campPos != null && !game.faction.hostile
                && isCampProperty(t)
                && pos.distSq(game.world.campPos.x(), game.world.campPos.y(),
                        game.world.campPos.z()) < CAMP_VANDALISM_RADIUS * CAMP_VANDALISM_RADIUS) {
            game.faction.addTrust(game, CAMP_VANDALISM_TRUST,
                    "The camp saw you wreck their property!");
        }

        var bs = game.world.settlementAt(pos.x(), pos.z());
        if (bs == null) {
            return;
        }
        if (t == BlockType.ALARM_BELL && bs.hostile()) {
            game.log("The alarm bell clatters down — the garrison can't ring it now.");
            game.settlementManager.onAlarmSabotaged(game, bs);
        } else if (t == BlockType.CRATE) {
            game.settlementManager.onContainerBroken(game, bs, brokenCrateContents);
        } else {
            game.settlementManager.onStructureDestroyed(game, bs, t);
        }
    }

    private static boolean isCampProperty(BlockType t) {
        return t == BlockType.CRATE || t == BlockType.CAMPFIRE || t == BlockType.WALL
                || t == BlockType.WORKBENCH || t == BlockType.TORCH || t == BlockType.CAMP_BED
                || t == BlockType.HERB_STATION || t == BlockType.DRYING_RACK;
    }

    // ------------------------------------------------------------------
    // Placing
    // ------------------------------------------------------------------

    /** Gameplay placement command shared by RMB and integration tests. */
    boolean placeSelectedBlockAt(int px, int py, int pz) {
        ItemStack held = game.player.selected();
        BlockType place = held == null ? null : held.type.places();
        if (place == null || !game.world.getBlock(px, py, pz).isReplaceable()) {
            return false;
        }
        // Don't place inside the player or an entity.
        if (place.solid && wouldCollide(px, py, pz)) {
            return false;
        }
        game.world.setBlock(px, py, pz, place, true);
        if (game.world.getBlock(px, py, pz) != place) {
            return false; // the world rejected it (protected region, chunk edge)
        }
        Vec3i pos = new Vec3i(px, py, pz);
        switch (place) {
            case CAMPFIRE -> game.world.campfireFuel.put(pos, CAMPFIRE_INITIAL_FUEL);
            case CRATE -> game.world.crateContents.put(pos, new Inventory(CRATE_SLOTS));
            case RAIN_COLLECTOR -> game.world.collectorWater.put(pos, 0f);
            case LANTERN -> game.log(
                    "Lantern placed empty. Hold charcoal and press [F] to refuel it.");
            case BEACON -> {
                game.world.beaconPos = pos;
                game.world.beaconStage = 0;
                game.log("Beacon frame placed. It needs a signal crystal, "
                        + "copper wiring and calibration.");
            }
            default -> {
            }
        }
        game.player.inventory.shrink(game.player.hotbarSel, 1);
        game.audio.playBlockPlace(px + 0.5f, py + 0.5f, pz + 0.5f);
        game.particles.blockDust(place, px + 0.5f, py + 0.8f, pz + 0.5f, PLACE_DUST_PARTICLES);
        return true;
    }

    /** True when a solid block at these coordinates would trap the player or an entity. */
    private boolean wouldCollide(int bx, int by, int bz) {
        if (aabbIntersectsBlock(game.player, bx, by, bz)) {
            return true;
        }
        for (Creature c : game.entities.creatures) {
            if (aabbIntersectsBlock(c, bx, by, bz)) {
                return true;
            }
        }
        for (Npc n : game.entities.npcs) {
            if (aabbIntersectsBlock(n, bx, by, bz)) {
                return true;
            }
        }
        return false;
    }

    private static boolean aabbIntersectsBlock(Entity e, int bx, int by, int bz) {
        float hw = e.width / 2f;
        return e.pos.x + hw > bx && e.pos.x - hw < bx + 1
                && e.pos.y + e.height > by && e.pos.y < by + 1
                && e.pos.z + hw > bz && e.pos.z - hw < bz + 1;
    }
}
