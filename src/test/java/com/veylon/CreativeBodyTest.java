package com.veylon;

import com.veylon.engine.AudioManager;
import com.veylon.entity.Affliction;
import com.veylon.entity.GameMode;
import com.veylon.entity.Player;
import com.veylon.entity.PlayerMovementSystem;
import com.veylon.item.EquipSlot;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.save.SaveSystem;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** R4, R8-R10: real body and movement commands, with harmful Survival twins. */
class CreativeBodyTest {
    @TempDir Path temporary;

    enum NeedHazard { STARVATION, DEHYDRATION, FREEZING, OVERHEATING, SMOKE }

    @Test
    void enteringRestoresTheWholeBodyAndLeavingKeepsThoseValues() {
        Game game = CreativeTestArena.create(GameMode.SURVIVAL);
        Player p = game.player;
        p.health = p.hunger = p.thirst = p.stamina = p.protein = p.vitamins = 7;
        p.dead = true;
        p.bodyTemp = 31;
        p.fatigue = p.wetness = p.smokeExposure = p.damageFlash = 1;
        p.addAffliction(Affliction.BLEEDING, 60);
        int logs = game.eventLog.recent(100).size();
        assertTrue(game.switchGameMode(GameMode.CREATIVE));
        assertFull(p);
        assertEquals(logs + 1, game.eventLog.recent(100).size(), "R4: entry emits one mode line");
        assertTrue(game.switchGameMode(GameMode.SURVIVAL));
        assertFull(p);
        assertTrue(game.creativeMarked(), "R5: leaving cannot erase the mark");
        assertEquals(logs + 2, game.eventLog.recent(100).size(), "R5: exit emits one mode line");
    }

    @Test
    void directLethalDamageAndPhysicalHitsCannotKillWearArmorOrRequestFeedback() {
        for (GameMode mode : GameMode.values()) {
            AudioProbe audio = new AudioProbe();
            Game game = CreativeTestArena.create(mode, audio);
            Player p = game.player;
            ItemStack coat = new ItemStack(ItemType.HIDE_COAT, 1);
            p.equipment[EquipSlot.TORSO.ordinal()] = coat;
            float durability = coat.durability;
            p.hurtPhysical(game, 15, true);
            p.knockback(p.pos.x - 1, p.pos.z, 5);
            if (mode == GameMode.CREATIVE) {
                assertFull(p);
                assertEquals(durability, coat.durability, "R8: immunity precedes armor wear");
                assertEquals(0, p.vel.lengthSquared(), "R8: no knockback");
            } else {
                assertTrue(p.health < p.maxHealth, "Survival physical damage still hurts");
                assertTrue(coat.durability < durability, "Survival armor still wears");
                assertTrue(p.vel.lengthSquared() > 0, "Survival retains knockback");
            }
            p.hurt(1000, false);
            assertEquals(mode == GameMode.SURVIVAL, p.dead, "R8: the direct lethal boundary honors abilities");
            if (mode == GameMode.CREATIVE) assertNoInjuryFeedback(game, audio);
        }
    }

    @ParameterizedTest
    @EnumSource(NeedHazard.class)
    void directNeedDamageAndSmokeAreHeldWithoutLosingEnvironmentObservations(NeedHazard hazard) {
        for (GameMode mode : GameMode.values()) {
            AudioProbe audio = new AudioProbe();
            Game game = CreativeTestArena.create(mode, audio);
            Player p = game.player;
            // Keep normal regeneration from hiding low-DPS heat and smoke damage.
            p.hunger = 20;
            switch (hazard) {
                case STARVATION -> p.hunger = 0;
                case DEHYDRATION -> p.thirst = 0;
                case FREEZING -> p.bodyTemp = 28;
                case OVERHEATING -> p.bodyTemp = 44;
                case SMOKE -> p.smokeExposure = 100;
            }
            p.envTemp = -1000;
            p.exposedToSky = false;
            for (int i = 0; i < 20; i++) p.tickNeeds(game, 0.05f);
            assertNotEquals(-1000, p.envTemp, "R10: temperature observation remains live");
            assertTrue(p.exposedToSky, "R10: the open arena refreshes sky exposure");
            assertEquals(game.world.biomeAt(310, 310), p.biome, "R10: biome refresh remains live");
            if (mode == GameMode.CREATIVE) {
                assertFull(p);
                assertNoInjuryFeedback(game, audio);
            } else assertTrue(p.health < p.maxHealth, "Survival must still suffer " + hazard);
        }
    }

    @ParameterizedTest
    @EnumSource(Affliction.class)
    void everyAfflictionIsRefusedAndExistingConditionsAreCleared(Affliction affliction) {
        Game game = CreativeTestArena.create(GameMode.SURVIVAL);
        game.player.addAffliction(affliction, 60);
        assertTrue(game.player.has(affliction), "Survival accepts " + affliction);
        game.switchGameMode(GameMode.CREATIVE);
        game.player.addAffliction(affliction, 60);
        game.player.tickNeeds(game, 0.05f);
        assertFull(game.player);
    }

    @Test
    void landingAndMidairModeSwitchCannotReleaseOldFallDamage() {
        AudioProbe audio = new AudioProbe();
        Game game = CreativeTestArena.create(GameMode.CREATIVE, audio);
        PlayerMovementSystem movement = new PlayerMovementSystem();
        var command = new PlayerMovementSystem.Command();
        var result = new PlayerMovementSystem.FrameResult();
        game.player.pos.y = 70;
        game.player.onGround = false;
        while (game.player.pos.y > 43) movement.update(game.player, game.world, command, 0.05f, result);
        game.switchGameMode(GameMode.SURVIVAL);
        for (int i = 0; i < 100 && !game.player.onGround; i++) {
            movement.update(game.player, game.world, command, 0.05f, result);
        }
        game.player.tickNeeds(game, 0.05f);
        assertEquals(game.player.maxHealth, game.player.health, "R5: the fall restarts at switch height");
        assertNoInjuryFeedback(game, audio);
        game.switchGameMode(GameMode.CREATIVE);
        game.player.pos.y = 70;
        game.player.onGround = false;
        for (int i = 0; i < 100 && !game.player.onGround; i++) {
            movement.update(game.player, game.world, command, 0.05f, result);
        }
        game.switchGameMode(GameMode.SURVIVAL);
        game.player.tickNeeds(game, 0.05f);
        assertEquals(game.player.maxHealth, game.player.health, "R8: an immune landing queues no damage");
        assertNoInjuryFeedback(game, audio);
    }

    @Test
    void creativeSprintingAndJumpingIgnoreLoadAndInjuryWithoutSpendingStamina() {
        Game game = CreativeTestArena.create(GameMode.CREATIVE);
        Player p = game.player;
        for (int i = 0; i < p.inventory.size(); i++) p.inventory.set(i, new ItemStack(ItemType.STONE, 64));
        assertTrue(p.encumbrance() > 1, "Fixture must be overloaded");
        p.afflictions.put(Affliction.SPRAIN, 60f);
        p.fatigue = 100;
        p.hunger = 0;
        assertEquals(1f, p.moveSpeedMul(), "R9: no load, sprain or fatigue speed penalty");
        assertEquals(100f, p.maxStamina(), "R9: fatigue cannot reduce maximum stamina");
        var command = new PlayerMovementSystem.Command().set(0, true, false, false, false,
                true, false, true, true);
        new PlayerMovementSystem().update(p, game.world, command, 0.05f, new PlayerMovementSystem.FrameResult());
        assertTrue(p.sprinting, "R9: hunger and load cannot prevent sprinting");
        assertEquals(100, p.stamina, "R9: sprint and jump cost no stamina");
        assertTrue(p.vel.y > 6.2f, "R9: the sprained jump penalty is bypassed");
    }

    @Test
    void creativeSaveLoadsFullStatsWithoutSwitchHealing() {
        Game game = CreativeTestArena.create(GameMode.SURVIVAL);
        game.player.health = 15;
        game.switchGameMode(GameMode.CREATIVE);
        Path save = temporary.resolve("creative-body.dat");
        assertTrue(SaveSystem.save(game, save));
        game.switchGameMode(GameMode.SURVIVAL);
        game.player.health = 20;
        assertTrue(SaveSystem.load(game, save));
        assertFull(game.player);
        assertEquals(GameMode.CREATIVE, game.gameMode());
    }

    static void assertFull(Player p) {
        assertEquals(p.maxHealth, p.health, "R8: full health");
        assertFalse(p.dead, "R8: cannot die");
        assertEquals(100, p.hunger, "R9: full hunger");
        assertEquals(100, p.thirst, "R9: full thirst");
        assertEquals(100, p.stamina, "R9: full stamina");
        assertEquals(100, p.protein, "R9: full protein");
        assertEquals(100, p.vitamins, "R9: full vitamins");
        assertEquals(37, p.bodyTemp, "R9: normal body temperature");
        assertEquals(0, p.fatigue, "R9: no fatigue");
        assertEquals(0, p.wetness, "R9: no retained wetness");
        assertEquals(0, p.smokeExposure, "R9: no retained smoke");
        assertTrue(p.afflictions.isEmpty(), "R8: no afflictions");
        assertEquals(0, p.damageFlash, "R8: no damage flash");
    }

    static void assertNoInjuryFeedback(Game game, AudioProbe audio) {
        assertEquals(0, audio.injuries, "R8: no hit or hurt sound requests, even without a device");
        assertEquals(0, game.player.damageFlash, "R8: no red damage flash");
        assertTrue(game.eventLog.recent(100).stream().noneMatch(s -> s.contains("POISONING")
                || s.contains("SICK") || s.contains("BURNS") || s.contains("BLEEDING")
                || s.contains("hit the ground") || s.contains("strikes you") || s.contains("bit you")
                || s.contains("gores you") || s.contains("rakes you") || s.contains("slashes at you")
                || s.contains("You are hit") || s.contains("feel ill") || s.contains("lungs")),
                "R8: no injury log line");
    }

    static final class AudioProbe extends AudioManager {
        int injuries;
        @Override public void playHit() { injuries++; }
        @Override public void playHurt() { injuries++; }
    }
}
