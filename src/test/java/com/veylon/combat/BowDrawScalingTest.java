package com.veylon.combat;

import com.veylon.Game;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An under-drawn bow slows the arrow it fired, and nothing else.
 *
 * <p>The draw-power scale is applied after the shot spawns, because the
 * projectile system owns muzzle velocity. Through 0.4.1 that was done by
 * searching {@code live} backwards for the first player arrow and scaling it —
 * correct only while the arrow just fired is the last element.
 *
 * <p>{@code ProjectileSystem.fire} adds nothing once {@code MAX_LIVE} is
 * reached. At the cap the search therefore found the newest *earlier* arrow and
 * multiplied its velocity by as little as 0.4, so an arrow the player had
 * already released at full draw decelerated in mid-flight. The cap is 96 and a
 * musket throws eight pellets a shot, so a firefight reaches it.
 */
class BowDrawScalingTest {

    @Test
    void aShortDrawSlowsOnlyTheArrowItFired() {
        Game game = readyToShoot(20_260_731L);

        fireAt(game, 1f);
        assertEquals(1, game.projectiles.live.size(), "precondition: one arrow in the air");
        float fullDrawSpeed = speedOf(game, 0);

        fireAt(game, 0.4f);
        assertEquals(2, game.projectiles.live.size(), "the second shot must also fly");
        assertEquals(fullDrawSpeed, speedOf(game, 0), 1e-4f,
                "the first arrow was already released; a later short draw must not touch it");
        assertTrue(speedOf(game, 1) < fullDrawSpeed * 0.95f,
                "the short-drawn arrow must actually be slower: "
                        + speedOf(game, 1) + " vs " + fullDrawSpeed);
    }

    @Test
    void aShortDrawAtTheProjectileCapSlowsNothing() {
        Game game = readyToShoot(20_260_732L);
        fillProjectileCap(game);

        float[] before = speeds(game);
        // Nothing spawns here: the cap is full. The bug was that this call
        // still found "an arrow" to slow.
        fireAt(game, 0.4f);

        assertEquals(ProjectileSystem.MAX_LIVE, game.projectiles.live.size(),
                "the cap must still hold");
        float[] after = speeds(game);
        for (int i = 0; i < before.length; i++) {
            assertEquals(before[i], after[i], 1e-4f,
                    "projectile " + i + " belongs to an earlier shot and must be untouched");
        }
    }

    @Test
    void everyPelletOfAMultiPelletShotIsScaledTogether() {
        // The bow is single-pellet, but the seam is shared, so pin the contract
        // the count-returning fire() is there to provide.
        Game game = readyToShoot(20_260_733L);
        WeaponDefinition bow = WeaponRegistry.of(ItemType.PRIMITIVE_BOW);
        assertNotNull(bow, "precondition: the bow is a registered weapon");

        int spawned = game.projectiles.fire(game, game.player, true,
                game.player.pos.x, game.player.pos.y + 1.5f, game.player.pos.z,
                0, 0, -1, bow, ItemType.ARROW);
        assertEquals(Math.max(1, bow.pellets), spawned,
                "fire must report what it actually put in the air");

        float[] before = speeds(game);
        game.projectiles.scaleNewestVelocities(spawned, 0.5f);
        float[] after = speeds(game);
        for (int i = after.length - spawned; i < after.length; i++) {
            assertEquals(before[i] * 0.5f, after[i], 1e-3f,
                    "pellet " + i + " must be scaled with the rest of its shot");
        }
    }

    @Test
    void fireReportsZeroAtTheCap() {
        Game game = readyToShoot(20_260_734L);
        fillProjectileCap(game);
        WeaponDefinition bow = WeaponRegistry.of(ItemType.PRIMITIVE_BOW);

        assertEquals(0, game.projectiles.fire(game, game.player, true,
                        game.player.pos.x, game.player.pos.y + 1.5f, game.player.pos.z,
                        0, 0, -1, bow, ItemType.ARROW),
                "a shot that spawns nothing must say so");
    }

    // ------------------------------------------------------------------

    private static Game readyToShoot(long seed) {
        Game game = new Game();
        game.newWorld(seed, true);
        // Well above the terrain, so arrows do not bury themselves in a hill
        // between the shot and the assertion.
        game.player.pos.y += 40;
        game.camera.position.set(game.player.pos.x, game.player.pos.y + 1.5f, game.player.pos.z);
        game.player.hotbarSel = 0;
        game.player.inventory.set(0, new ItemStack(ItemType.PRIMITIVE_BOW, 1));
        game.player.inventory.add(ItemType.ARROW, 400);
        return game;
    }

    /**
     * Draws to {@code draw} and releases, through the production bow command.
     * The opening call advances time generously so the previous shot's
     * attack interval has expired; otherwise the command answers COOLDOWN and
     * nothing is fired.
     */
    private static void fireAt(Game game, float draw) {
        Vector3f direction = new Vector3f(0, 0.15f, -1).normalize();
        game.updateBowCommand(5f, true, true, direction);
        game.bowDraw = draw;
        game.updateBowCommand(0f, false, false, direction);
    }

    private static void fillProjectileCap(Game game) {
        WeaponDefinition bow = WeaponRegistry.of(ItemType.PRIMITIVE_BOW);
        while (game.projectiles.live.size() < ProjectileSystem.MAX_LIVE) {
            int spawned = game.projectiles.fire(game, game.player, true,
                    game.player.pos.x, game.player.pos.y + 1.5f, game.player.pos.z,
                    0, 0.3f, -1, bow, ItemType.ARROW);
            if (spawned == 0) {
                break;
            }
        }
        assertEquals(ProjectileSystem.MAX_LIVE, game.projectiles.live.size(),
                "precondition: the live projectile cap is full");
    }

    private static float speedOf(Game game, int index) {
        ProjectileSystem.Projectile p = game.projectiles.live.get(index);
        return (float) Math.sqrt(p.vx * p.vx + p.vy * p.vy + p.vz * p.vz);
    }

    private static float[] speeds(Game game) {
        float[] out = new float[game.projectiles.live.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = speedOf(game, i);
        }
        return out;
    }
}
