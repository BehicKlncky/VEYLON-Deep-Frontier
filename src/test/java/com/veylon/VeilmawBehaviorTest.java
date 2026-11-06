package com.veylon;

import com.veylon.ai.VeilmawAI;
import com.veylon.entity.Affliction;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.GameMode;
import com.veylon.item.ItemType;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.world.BlockType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The Veilmaw's territorial ambush (urban milestone 15) through production ticks: what wakes it,
 * the telegraph that precedes every lunge, the counters (darkness and stealth, a torch lure,
 * fire on its node) and its rewards. The arena stages geometry and the body only.
 */
class VeilmawBehaviorTest {
    private static final float X = EcologyArena.REST.x() + 0.5f, Z = EcologyArena.REST.z() + 0.5f;

    @Test
    void aCrouchedPlayerInTheDarkCrossesItsGroundWithoutWakingIt() {
        Game g = EcologyArena.create(1501);
        Creature c = EcologyArena.veilmaw(g);
        float health = g.player.health;
        // Across the territory at three blocks and more, crouched: no footstep, no light, no touch.
        for (float x = X - 8; x <= X + 8; x += 0.5f) {
            EcologyArena.stand(g, x, Z + 3.2f);
            g.player.crouching = true;
            assertFalse(g.emitPlayerFootstepNoise(false, true), "a crouched step makes no noise");
            EcologyArena.hold(g, x, Z + 3.2f, 0.1f);
            assertEquals(CreatureState.FOLDED, c.state, "still folded with the player at x=" + x);
        }
        assertEquals(health, g.player.health);
    }

    @Test
    void footstepsOnItsGroundWakeItWithATelegraphBeforeTheLunge() {
        Game g = EcologyArena.create(1502);
        Creature c = EcologyArena.veilmaw(g);
        EcologyArena.stand(g, X + 2.2f, Z);
        assertTrue(g.emitPlayerFootstepNoise(false, false));
        float health = g.player.health;
        float stirring = 0;
        for (int tick = 0; tick < 200 && c.state != CreatureState.LUNGING; tick++) {
            EcologyArena.hold(g, X + 2.2f, Z, SimulationScheduler.FAST_DT);
            if (c.state == CreatureState.STIRRING) {
                stirring += SimulationScheduler.FAST_DT;
                assertEquals(health, g.player.health, "nothing lands during the telegraph");
            }
        }
        assertEquals(CreatureState.LUNGING, c.state, "the telegraph commits to a lunge at a player in reach");
        assertTrue(stirring >= VeilmawAI.WINDUP - 0.06f, "a full telegraph first: " + stirring);
        for (int tick = 0; tick < 40 && c.state == CreatureState.LUNGING; tick++) {
            EcologyArena.hold(g, X + 2.2f, Z, SimulationScheduler.FAST_DT);
        }
        assertEquals(health - VeilmawAI.STRIKE_DAMAGE, g.player.health, 1e-3f, "one strike for the lunge");
        assertTrue(g.player.harmEvents > 0);
        assertNotEquals(CreatureState.LUNGING, c.state, "it recoils after striking");
    }

    @Test
    void backingOutOfReachDuringTheTelegraphMeansNoLunge() {
        Game g = EcologyArena.create(1503);
        Creature c = EcologyArena.veilmaw(g);
        EcologyArena.stand(g, X + 2f, Z);
        g.emitPlayerFootstepNoise(false, false);
        for (int tick = 0; tick < 40 && c.state != CreatureState.STIRRING; tick++) {
            EcologyArena.hold(g, X + 2f, Z, SimulationScheduler.FAST_DT);
        }
        assertEquals(CreatureState.STIRRING, c.state);
        float health = g.player.health;
        // Step back, crouched and silent, beyond its lunge range while it is still telegraphing.
        g.player.crouching = true;
        EcologyArena.hold(g, X + VeilmawAI.LUNGE_RANGE + 2f, Z, 3f);
        assertNotEquals(CreatureState.LUNGING, c.state);
        assertEquals(health, g.player.health);
        EcologyArena.hold(g, X + VeilmawAI.LUNGE_RANGE + 2f, Z, 10f);
        assertEquals(CreatureState.FOLDED, c.state, "it lets the player go and folds again");
        assertTrue(Math.abs(c.pos.x - X) < 0.7f && Math.abs(c.pos.z - Z) < 0.7f, "back at its node: " + c.pos);
    }

    @Test
    void aLitPlayerInSightWakesItButDaylightDoesNot() {
        Game g = EcologyArena.create(1504);
        Creature c = EcologyArena.veilmaw(g);
        g.player.crouching = true;
        EcologyArena.hold(g, X + 5f, Z, 2f);
        assertEquals(CreatureState.FOLDED, c.state, "noon daylight is not light to it");
        // A lamp beside the player, outside the territory's lure distance from the Veilmaw itself.
        int tx = (int) Math.floor(X + 6.5f), tz = (int) Math.floor(Z + 1);
        g.world.setBlock(tx, EcologyArena.FEET, tz, BlockType.TORCH, true);
        assertTrue(g.world.blockLight((int) Math.floor(X + 5f), EcologyArena.FEET + 1, (int) Math.floor(Z))
                >= VeilmawAI.PLAYER_LIGHT, "precondition: the player stands in the torch's light");
        boolean stirred = false;
        for (int tick = 0; tick < 20 && !stirred; tick++) {
            EcologyArena.hold(g, X + 5f, Z, SimulationScheduler.FAST_DT);
            stirred = c.state == CreatureState.STIRRING && c.targetIsPlayer;
        }
        assertTrue(stirred, "a lit player in plain sight wakes it: " + c.state);
    }

    @Test
    void aTorchPlacedOnItsGroundLuresItAwayAndIsSmothered() {
        Game g = EcologyArena.create(1505);
        Creature c = EcologyArena.veilmaw(g);
        EcologyArena.stand(g, X + 20f, Z); // outside the territory, quiet
        int tx = (int) Math.floor(X) - 5, tz = (int) Math.floor(Z) + 2;
        g.world.setBlock(tx, EcologyArena.FEET, tz, BlockType.TORCH, true);
        boolean crept = false;
        for (int tick = 0; tick < 400 && g.world.getBlock(tx, EcologyArena.FEET, tz) == BlockType.TORCH; tick++) {
            EcologyArena.hold(g, X + 20f, Z, SimulationScheduler.FAST_DT);
            crept |= c.state == CreatureState.CREEPING;
        }
        assertTrue(crept, "the light drew it off its node");
        assertEquals(BlockType.AIR, g.world.getBlock(tx, EcologyArena.FEET, tz), "it smothered the torch");
        EcologyArena.hold(g, X + 20f, Z, 12f);
        assertEquals(CreatureState.FOLDED, c.state);
        assertTrue(Math.abs(c.pos.x - X) < 0.7f && Math.abs(c.pos.z - Z) < 0.7f, "and walked home: " + c.pos);
    }

    @Test
    void aFlameBesideItsNodeBurnsTheNodeAndTheVeilmawWithersIntoHarvestableRemains() {
        Game g = EcologyArena.create(1506);
        Creature c = EcologyArena.veilmaw(g);
        EcologyArena.stand(g, X + 20f, Z);
        var node = EcologyArena.NODE;
        g.world.setBlock(node.x() - 1, node.y(), node.z(), BlockType.TORCH, true);
        for (int tick = 0; tick < 1200 && !c.dead; tick++) {
            EcologyArena.hold(g, X + 20f, Z, SimulationScheduler.FAST_DT);
        }
        assertNotEquals(BlockType.GROWTH_NODE, g.world.getBlock(node.x(), node.y(), node.z()), "the node burnt");
        assertEquals(BlockType.ASH, g.world.getBlock(node.x(), node.y(), node.z()), "and charred in place");
        assertTrue(c.dead && c.health <= 0, "its Veilmaw withered with it");
        EcologyArena.hold(g, X + 20f, Z, 6f);
        Carcass remains = g.entities.carcasses.stream().filter(k -> k.type == Creature.CreatureType.VEILMAW)
                .findFirst().orElseThrow();
        assertEquals(0, remains.meatLeft, "an alien body is never food");
        assertEquals(2, remains.hideLeft);
        EcologyArena.stand(g, remains.pos.x + 0.6f, remains.pos.z);
        assertTrue(g.interactions.interactWithNearbyCarcass());
        assertEquals(2, g.player.inventory.count(ItemType.MEMBRANE_SAC));
        assertEquals(0, g.player.inventory.count(ItemType.RAW_MEAT));
        assertEquals(0, g.player.inventory.count(ItemType.SPOILED_MEAT));
        assertEquals(0, remains.hideLeft);
    }

    @Test
    void breakingTheNodeByHandGivesASacAndWithersItsOrganism() {
        Game g = EcologyArena.create(1507);
        Creature c = EcologyArena.veilmaw(g);
        var node = EcologyArena.NODE;
        EcologyArena.stand(g, node.x() - 1.5f, node.z() + 0.5f);
        assertTrue(g.completePlayerBlockBreak(node));
        assertEquals(1, g.player.inventory.count(ItemType.MEMBRANE_SAC));
        EcologyArena.run(g, 1f);
        assertTrue(c.dead, "without its node the Veilmaw withers");
    }

    @Test
    void itNeverLeavesItsTerritoryHoweverItIsDrawn() {
        Game g = EcologyArena.create(1508);
        Creature c = EcologyArena.veilmaw(g);
        float cx = EcologyArena.NODE.x() + 0.5f, cz = EcologyArena.NODE.z() + 0.5f;
        float farthest = 0;
        for (int round = 0; round < 12; round++) {
            double a = round * Math.PI / 6;
            float px = cx + (float) Math.cos(a) * 8.5f, pz = cz + (float) Math.sin(a) * 8.5f;
            EcologyArena.stand(g, px, pz);
            g.emitPlayerFootstepNoise(true, false);
            for (int tick = 0; tick < 120; tick++) {
                EcologyArena.hold(g, px, pz, SimulationScheduler.FAST_DT);
                float dx = c.pos.x - cx, dz = c.pos.z - cz;
                farthest = Math.max(farthest, (float) Math.sqrt(dx * dx + dz * dz));
            }
            g.player.health = g.player.maxHealth;
        }
        assertTrue(farthest <= VeilmawAI.TERRITORY + 0.5f, "farthest from its node: " + farthest);
    }

    @Test
    void creativeInvisibilitySilencesItsSensesAndCancelsATelegraphAimedAtThePlayer() {
        Game g = EcologyArena.create(1509);
        Creature c = EcologyArena.veilmaw(g);
        // A Survival player wakes it by touch...
        EcologyArena.hold(g, X + 1.2f, Z, 0.3f);
        assertEquals(CreatureState.STIRRING, c.state);
        assertTrue(c.targetIsPlayer);
        // ...and switching to Creative mid-telegraph forgets the player at once: no lunge, no blow.
        g.gameModes.switchTo(GameMode.CREATIVE);
        assertFalse(g.player.isPerceivableByAi());
        EcologyArena.hold(g, X + 1.2f, Z, 0.2f);
        assertNotEquals(CreatureState.STIRRING, c.state);
        EcologyArena.hold(g, X + 1.2f, Z, 6f);
        assertEquals(g.player.maxHealth, g.player.health);
        assertFalse(g.player.has(Affliction.SICKNESS));
        // In Creative there is no footstep noise, and even touch goes unnoticed.
        assertFalse(g.emitPlayerFootstepNoise(true, false));
        EcologyArena.hold(g, X + 1.2f, Z, 3f);
        assertEquals(CreatureState.FOLDED, c.state);
    }

    @Test
    void itsStrikeGoesThroughThePlayersDamageAndAfflictionGates() {
        Game g = EcologyArena.create(1511);
        Creature c = EcologyArena.veilmaw(g);
        int sick = 0;
        for (int lunge = 0; lunge < 12; lunge++) {
            g.player.health = g.player.maxHealth;
            for (Affliction a : Affliction.values()) {
                g.player.cure(a);
            }
            c.attackCooldown = 0;
            c.state = CreatureState.FOLDED;
            c.decideTimer = 0;
            float before = g.player.health;
            for (int tick = 0; tick < 60 && c.state != CreatureState.RECOILING; tick++) {
                before = g.player.health;
                EcologyArena.hold(g, X + 1.2f, Z, SimulationScheduler.FAST_DT);
            }
            assertEquals(CreatureState.RECOILING, c.state, "lunge " + lunge);
            assertEquals(before - VeilmawAI.STRIKE_DAMAGE, g.player.health, 0.05f, "lunge " + lunge);
            sick += g.player.has(Affliction.SICKNESS) ? 1 : 0;
            EcologyArena.hold(g, X + 30f, Z, 6f); // out of its ground while it recoils and walks home
        }
        assertTrue(sick > 0 && sick < 12, "the sickness is a seeded chance, not certain: " + sick);
    }

    @Test
    void foldedBesideItsNodeItHealsAndBadlyHurtItGoesHomeInsteadOfFighting() {
        Game g = EcologyArena.create(1510);
        Creature c = EcologyArena.veilmaw(g);
        EcologyArena.stand(g, X + 20f, Z);
        c.health = 20;
        EcologyArena.hold(g, X + 20f, Z, 5f);
        assertEquals(23f, c.health, 0.2f, "about " + VeilmawAI.HEAL_PER_SECOND + " a second");
        c.pos.set(X - 6f, EcologyArena.FEET, Z);
        c.state = CreatureState.FOLDED;
        c.lastHealth = c.health;
        c.hurt(c.health - c.maxHealth * 0.3f, true);
        EcologyArena.stand(g, X - 4f, Z);
        EcologyArena.hold(g, X - 4f, Z, 0.2f);
        assertEquals(CreatureState.RETURNING, c.state, "below its retreat share it goes home");
    }
}
