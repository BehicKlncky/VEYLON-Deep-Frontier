package com.veylon;

import com.veylon.entity.Affliction;
import com.veylon.entity.GameMode;
import com.veylon.item.EquipSlot;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Milestone 13: contaminated ground is a Survival hazard worth boots, and only where it lies. */
class ContaminatedGroundTest {
    private static final int TICKS = 400;

    @Test
    void standingOnContaminatedGroundMakesASurvivorSickAndBootsCutTheOdds() {
        int bare = sicknesses(false);
        int booted = sicknesses(true);
        // 400 medium ticks at 10 % and 2.5 %: about 40 and 10.
        assertTrue(bare >= 25 && bare <= 60, "bare feet: " + bare);
        assertTrue(booted >= 1 && booted * 2 < bare, "boots: " + booted + " against " + bare);
    }

    private static int sicknesses(boolean boots) {
        Game game = CreativeTestArena.create(GameMode.SURVIVAL);
        game.world.setBlock(310, 39, 310, BlockType.CONTAMINATED_GROUND, false);
        if (boots) {
            game.player.equipment[EquipSlot.FEET.ordinal()] = new ItemStack(ItemType.HIDE_BOOTS, 1);
        }
        int count = 0;
        for (int i = 0; i < TICKS; i++) {
            if (game.player.tickContaminationExposure(game)) {
                assertTrue(game.player.has(Affliction.SICKNESS));
                assertTrue(game.eventLog.recent(1).getFirst().contains("contaminated ground"));
                count++;
                game.player.afflictions.clear();
            }
        }
        return count;
    }

    @Test
    void cleanGroundRollsNothingAndTheCrustSalvagesNothing() {
        Game game = CreativeTestArena.create(GameMode.SURVIVAL);
        for (int i = 0; i < TICKS; i++) {
            assertFalse(game.player.tickContaminationExposure(game));
        }
        // Scraped away, the crust leaves a shallow pit with no hazard left in it.
        game.world.setBlock(310, 39, 310, BlockType.CONTAMINATED_GROUND, false);
        game.world.setBlock(310, 39, 310, BlockType.AIR, true);
        game.player.pos.set(310.5f, 39.001f, 310.5f);
        for (int i = 0; i < TICKS; i++) {
            assertFalse(game.player.tickContaminationExposure(game));
        }
        assertTrue(game.player.afflictions.isEmpty());
        assertNull(BlockType.CONTAMINATED_GROUND.drop, "scraping it away yields nothing");
        assertTrue(BlockType.CONTAMINATED_GROUND.solid && BlockType.CONTAMINATED_GROUND.hardness > 0);
    }
}
