package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.simulation.EventSystem;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The survival layer's failure model must replay from the world seed.
 *
 * <p>{@code WorldSeedDeterminismTest} proves the structural half: every
 * {@link java.util.Random} reachable from {@code Game} is reseeded by
 * {@code newWorld}. It is blind to {@code Math.random()}, and through 0.4.1
 * fourteen gameplay rolls used exactly that — whether a hit made you bleed,
 * whether a fall sprained a leg, whether an untreated wound went septic,
 * whether food or water poisoned you, whether a miserable night left you sick,
 * whether toxic fog took hold, and whether leaves and carcasses gave up their
 * drops.
 *
 * <p>That is not a rounding error in the simulation. It is the entire way this
 * game kills you, and none of it reproduced. A bug report with a seed, a seeded
 * QA capture and a fixed-seed regression test each observed a different game
 * from the one being described.
 *
 * <p>These tests observe the outcomes rather than the generators, through the
 * same production commands play uses, so they would still fail if the rolls
 * were reseeded but wired to the wrong stream.
 */
class PlayerOutcomeDeterminismTest {

    @Test
    void oneSeedReplaysEveryPlayerOutcome() {
        assertEquals(outcomes(770_001L, null), outcomes(770_001L, null),
                "the same seed must produce the same survival outcomes");
    }

    @Test
    void outcomesSurviveADifferentWorldRunningInTheSameProcess() {
        // The collaborators that own these rolls share Game's lifetime, not a
        // world's, so without an explicit reseed they inherit whatever the
        // previous world left behind. This is the case that motivated the fix.
        assertEquals(outcomes(770_002L, null), outcomes(770_002L, 999_777L),
                "a seed must not inherit roll state from a previous world");
    }

    @Test
    void differentSeedsStillDiverge() {
        // Guards against "deterministic" quietly meaning "constant": a fix that
        // pinned every roll to the same value would satisfy the tests above.
        assertNotEquals(outcomes(770_003L, null), outcomes(770_004L, null),
                "distinct seeds must still produce distinct outcomes");
    }

    @Test
    void theFingerprintObservesRealOutcomesAndNotJustZeroes() {
        // A fingerprint of "nothing ever happened" would compare equal forever.
        String sample = outcomes(770_005L, null);

        assertTrue(sample.contains("bleeds=") && sample.contains("poisonings=")
                        && sample.contains("bones=") && sample.contains("leafDrops=")
                        && sample.contains("fogSickness="),
                "the fingerprint must sample every reseeded roll: " + sample);
        assertTrue(counted(sample, "bleeds") > 0, "no bleeding was ever rolled: " + sample);
        assertTrue(counted(sample, "poisonings") > 0, "no poisoning was ever rolled: " + sample);
        assertTrue(counted(sample, "bones") > 0, "no bone drop was ever rolled: " + sample);
        assertTrue(counted(sample, "leafDrops") > 0, "no leaf drop was ever rolled: " + sample);
        assertTrue(counted(sample, "fogSickness") > 0, "toxic fog never took hold: " + sample);
    }

    // ------------------------------------------------------------------

    /**
     * Builds a world (optionally after burning a different one through a fresh
     * {@code Game}) and drives every reseeded player-outcome roll a fixed
     * number of times, returning what happened.
     */
    private static String outcomes(long seed, Long warmupSeed) {
        if (warmupSeed != null) {
            Game warmup = new Game();
            warmup.newWorld(warmupSeed, true);
            drive(warmup);
        }
        Game game = new Game();
        game.newWorld(seed, true);
        return drive(game);
    }

    private static String drive(Game game) {
        return "bleeds=" + bleedRolls(game)
                + " fogSickness=" + toxicFogRolls(game)
                + " poisonings=" + foodPoisoningRolls(game)
                + " leafDrops=" + leafDropRolls(game)
                + " bones=" + carcassBoneRolls(game);
    }

    /** Player's affliction stream: does this hit open a bleeding wound? */
    private static int bleedRolls(Game game) {
        int bleeds = 0;
        for (int i = 0; i < 60; i++) {
            game.player.cure(Affliction.BLEEDING);
            game.player.health = 100f;
            game.player.hurtPhysical(game, 12f, true);
            if (game.player.has(Affliction.BLEEDING)) {
                bleeds++;
            }
        }
        game.player.cure(Affliction.BLEEDING);
        return bleeds;
    }

    /** Player's affliction stream again, through the medium-tick fog path. */
    private static int toxicFogRolls(Game game) {
        game.events.active.clear();
        game.events.active.add(new EventSystem.ActiveEvent(
                EventSystem.EventType.TOXIC_FOG, 10_000f, 1f));
        game.player.exposedToSky = true;
        game.player.shelter = com.veylon.simulation.ShelterSystem.OPEN;

        int sickened = 0;
        for (int i = 0; i < 200; i++) {
            game.player.cure(Affliction.SICKNESS);
            if (game.player.tickToxicFogExposure(game)) {
                sickened++;
            }
        }
        game.player.cure(Affliction.SICKNESS);
        game.events.active.clear();
        return sickened;
    }

    /** PlayerConsumables' stream: does raw meat make the player ill? */
    private static int foodPoisoningRolls(Game game) {
        int poisonings = 0;
        for (int i = 0; i < 60; i++) {
            game.player.cure(Affliction.FOOD_POISONING);
            game.player.hunger = 10f;
            game.player.inventory.set(game.player.hotbarSel,
                    new ItemStack(ItemType.RAW_MEAT, 1));
            game.consumables.eat(game.player.selected());
            if (game.player.has(Affliction.FOOD_POISONING)) {
                poisonings++;
            }
        }
        game.player.cure(Affliction.FOOD_POISONING);
        return poisonings;
    }

    /** PlayerBlockActions' stream: do broken leaves give up a stick? */
    private static int leafDropRolls(Game game) {
        int x = (int) game.player.pos.x + 3;
        int z = (int) game.player.pos.z + 3;
        int y = game.world.surfaceHeight(x, z) + 3;
        Vec3i pos = new Vec3i(x, y, z);

        int drops = 0;
        for (int i = 0; i < 60; i++) {
            game.world.setBlock(x, y, z, BlockType.LEAVES, true);
            int before = game.player.inventory.count(ItemType.STICK);
            game.completePlayerBlockBreak(pos);
            if (game.player.inventory.count(ItemType.STICK) > before) {
                drops++;
            }
        }
        return drops;
    }

    /** WorldInteractions' stream: does skinning a carcass yield a bone? */
    private static int carcassBoneRolls(Game game) {
        game.player.inventory.add(ItemType.IRON_KNIFE, 1);
        int bones = 0;
        for (int i = 0; i < 40; i++) {
            game.entities.carcasses.clear();
            Carcass carcass = new Carcass(Creature.CreatureType.DEER,
                    game.player.pos.x + 0.5f, game.player.pos.y, game.player.pos.z + 0.5f);
            game.entities.carcasses.add(carcass);

            int before = game.player.inventory.count(ItemType.BONE);
            // A blunted knife would stop the harvest and silently stop rolling.
            refreshKnife(game);
            game.interactWithNearbyCarcass();
            if (game.player.inventory.count(ItemType.BONE) > before) {
                bones++;
            }
        }
        game.entities.carcasses.clear();
        return bones;
    }

    private static void refreshKnife(Game game) {
        for (int i = 0; i < game.player.inventory.size(); i++) {
            ItemStack stack = game.player.inventory.get(i);
            if (stack != null && stack.type == ItemType.IRON_KNIFE) {
                stack.durability = ItemType.IRON_KNIFE.maxDurability;
                return;
            }
        }
        game.player.inventory.add(ItemType.IRON_KNIFE, 1);
    }

    private static int counted(String fingerprint, String key) {
        int start = fingerprint.indexOf(key + "=") + key.length() + 1;
        int end = fingerprint.indexOf(' ', start);
        return Integer.parseInt(fingerprint.substring(start, end < 0 ? fingerprint.length() : end));
    }
}
