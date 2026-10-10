package com.veylon;

import com.veylon.entity.GameMode;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.util.Vec3i;
import com.veylon.world.Raycaster;
import com.veylon.world.World;
import org.joml.Vector3f;

/**
 * A street-network world with {@link FixtureShowcase} built near the spawn,
 * written as saved edits, for the package-04 fixture tests. It drives the
 * production commands: real movement, the F-key sweep, the break command and
 * the targeting raycast.
 */
public final class UrbanFixtureArena {

    public static final long SEED = 20260731L;
    private static final float DT = 1f / 60f;

    public final Game game;
    public final FixtureShowcase.Layout house;
    private final PlayerMovementSystem.Command walk = new PlayerMovementSystem.Command();

    private UrbanFixtureArena(Game game, FixtureShowcase.Layout house) {
        this.game = game;
        this.house = house;
    }

    public static UrbanFixtureArena create(GameMode mode) {
        return create(new Game(), mode);
    }

    public static UrbanFixtureArena create(Game game, GameMode mode) {
        game.newWorld(SEED, true, World.GEN_URBAN_STREETS, mode);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.player.inventory.clear();
        game.noise.reset();
        int[] spawn = game.world.urban.spawnColumn();
        int x0 = spawn[0] + 6, z0 = spawn[1] + 10;
        game.world.ensureChunks(x0 + FixtureShowcase.WIDTH / 2, z0, 3, 1_000);
        FixtureShowcase.Layout house = FixtureShowcase.build(game.world, x0, z0, true);
        UrbanFixtureArena arena = new UrbanFixtureArena(game, house);
        arena.standAt(house.outsideFront());
        return arena;
    }

    /** Puts the player's feet on a cell, at rest. */
    public void standAt(Vec3i cell) {
        game.player.pos.set(cell.x() + 0.5f, cell.y() + 0.001f, cell.z() + 0.5f);
        game.player.vel.zero();
        game.player.onGround = true;
        game.player.crouching = false;
    }

    /** Aims the crosshair at a cell, as a raycast hit from its -Z face would. */
    public void aim(Vec3i cell) {
        game.targetHit = new Raycaster.Hit(cell.x(), cell.y(), cell.z(), 0, 0, -1, 2,
                game.world.getBlock(cell.x(), cell.y(), cell.z()));
    }

    /** Presses F through the full sweep (aimed fixtures, NPCs, arrows, carcasses, blocks, water). */
    public void pressF() {
        game.interactions.interact();
    }

    public String prompt() {
        game.updatePrompt();
        return game.interactPrompt;
    }

    /** Walks toward a cell centre for at most {@code seconds}; true when it got within reach. */
    public boolean walkTo(Vec3i cell, float seconds) {
        float tx = cell.x() + 0.5f, tz = cell.z() + 0.5f;
        for (float t = 0; t < seconds; t += DT) {
            float dx = tx - game.player.pos.x, dz = tz - game.player.pos.z;
            if (dx * dx + dz * dz < 0.2f * 0.2f) {
                return true;
            }
            float yaw = (float) Math.toDegrees(Math.atan2(dx, -dz));
            walk.set(yaw, true, false, false, false, false, game.player.crouching, false, false);
            game.applyMovementCommand(walk, DT);
        }
        float dx = tx - game.player.pos.x, dz = tz - game.player.pos.z;
        return dx * dx + dz * dz < 0.2f * 0.2f;
    }

    /** Stands still for a while, so a body that is still dropping lands. */
    public void settle(float seconds) {
        for (float t = 0; t < seconds; t += DT) {
            walk.set(0, false, false, false, false, false, game.player.crouching, false, false);
            game.applyMovementCommand(walk, DT);
        }
    }

    /** The real targeting raycast from an eye position along a direction. */
    public Raycaster.Hit cast(float ex, float ey, float ez, float dx, float dy, float dz) {
        Raycaster.MutableHit hit = new Raycaster.MutableHit();
        return Raycaster.castInto(game.world, new Vector3f(ex, ey, ez), new Vector3f(dx, dy, dz),
                5.2, false, hit) ? hit.immutableCopy() : null;
    }

    /** Fills every inventory slot with full stacks, so nothing more fits. */
    public void fillInventory() {
        for (int i = 0; i < game.player.inventory.size(); i++) {
            game.player.inventory.set(i, new ItemStack(ItemType.STONE, ItemType.STONE.maxStack));
        }
    }

    /** Holds an item in the selected hotbar slot. */
    public void hold(ItemType item) {
        game.player.inventory.set(game.player.hotbarSel, new ItemStack(item, 1));
    }

    public com.veylon.world.BlockType block(Vec3i p) {
        return game.world.getBlock(p.x(), p.y(), p.z());
    }

    public com.veylon.world.BlockType above(Vec3i p) {
        return game.world.getBlock(p.x(), p.y() + 1, p.z());
    }
}
