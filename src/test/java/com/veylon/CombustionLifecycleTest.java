package com.veylon;

import com.veylon.AllLivingBlastDeathTest.Target;
import com.veylon.entity.Affliction;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BurnResidue;
import com.veylon.entity.Carcass;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Entity;
import com.veylon.entity.FragmentPiece;
import com.veylon.entity.GameMode;
import com.veylon.entity.HumanCorpse;
import com.veylon.entity.Npc;
import com.veylon.entity.Ragdoll;
import com.veylon.entity.RagdollConstants;
import com.veylon.item.ItemType;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

import static com.veylon.entity.CombustionConstants.MAX_BURN_RESIDUES;
import static com.veylon.entity.CombustionConstants.RESIDUE_FLAME_SECONDS;
import static com.veylon.entity.CombustionConstants.RESIDUE_SMOKE_SECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.lwjgl.glfw.GLFW.GLFW_KEY_M;
import static org.lwjgl.glfw.GLFW.GLFW_PRESS;
import static org.lwjgl.glfw.GLFW.GLFW_RELEASE;

/**
 * Body fire across every change of life (contract section 14, milestone 09):
 * the death transition hands a burning body's fire on to the one body it
 * leaves, as a residue that refers to nothing and dies down; whichever lethal
 * cause comes first decides the death, its credit and its body; a body that
 * leaves the world alive takes no fire, panic or body with it; saving leaves
 * a fire burning and loading builds bodies without one; a new life, world or
 * mode starts clear; and only a simulated frame advances anything, whatever
 * screen is open or however fast a night's sleep runs the clock.
 */
class CombustionLifecycleTest {

    private static final float DT = SimulationScheduler.FAST_DT;
    private static final double FRAME = 1.0 / 60.0;
    private static final float FEET = 40f;
    private static final float KEG_POWER = 3.8f;
    private static final float STURDY = 500f;

    @TempDir
    Path directory;

    // ------------------------------------------------------------------
    // The death transition
    // ------------------------------------------------------------------

    /**
     * A body the fire kills falls once, and the fire it died with goes on on
     * that body — the falling ragdoll, then the corpse or carcass it settles
     * into, the very same residue — dying down in a few seconds and never
     * touching anyone. A bird leaves no body, so its fire lets go.
     */
    @Test
    void aBodyTheFireKillsFallsOnceAndItsFlamesGoOnOnTheBodyItLeaves() {
        Game g = arena();
        Creature hare = sturdy(g.entities.spawnCreature(g.world, CreatureType.HARE, 320.5f, FEET, 320.5f));
        Creature bird = sturdy(g.entities.spawnCreature(g.world, CreatureType.BIRD, 330.5f, FEET, 320.5f));
        Npc person = sturdy(g.entities.spawnNpc(g.world, "Villager", 320.5f, FEET, 332.5f));
        int meat = g.player.inventory.count(ItemType.RAW_MEAT);
        light(g, hare, true, 1);
        light(g, bird, true, 2);
        light(g, person, false, 3);
        // Late in the afterburn: less than a residue's longest flames left, already fading.
        fastTicks(g, 70);
        for (Entity e : List.<Entity>of(hare, bird, person)) {
            assertTrue(g.combustion.isBurning(e) && e.combustion.scorch() > 0f, "precondition: burning, scorched");
            assertTrue(e.combustion.fuel() < RESIDUE_FLAME_SECONDS
                    && e.combustion.intensity() < e.combustion.peakIntensity(), "precondition: fading");
            e.health = 0.01f;
        }

        g.fastTick(DT);

        assertTrue(hare.dead && bird.dead && person.dead, "the fire killed all three");
        assertEquals(3, g.ragdolls.totalSpawned, "each falls as one body");
        assertEquals(0, g.fragments.totalSpawned, "and none comes apart");
        Ragdoll hareBody = ragdoll(g, CreatureType.HARE);
        Ragdoll birdBody = ragdoll(g, CreatureType.BIRD);
        Ragdoll personBody = ragdoll(g, null);
        for (Ragdoll r : List.of(hareBody, birdBody, personBody)) {
            assertNotNull(r.burn, "a body that died alight carries its fire");
        }
        assertResidueOf(hare, hareBody.burn);
        assertResidueOf(bird, birdBody.burn);
        assertResidueOf(person, personBody.burn);
        assertEquals(3, distinct(hareBody.burn, birdBody.burn, personBody.burn));
        assertEquals(3, g.burnResidues.trackedCount());
        assertEquals(meat + 1, g.player.inventory.count(ItemType.RAW_MEAT), "the bird the player lit: one meat");

        float hareSeconds = hare.combustion.burnSeconds();
        for (int i = 0; i < 20; i++) {
            g.fastTick(DT);
        }
        assertEquals(3, g.ragdolls.totalSpawned, "later ticks bring no second body");
        assertEquals(meat + 1, g.player.inventory.count(ItemType.RAW_MEAT), "and no second reward");
        assertEquals(hareSeconds, hare.combustion.burnSeconds(), 0f, "a dead body's fire is never ticked");

        BurnResidue hareFire = hareBody.burn;
        BurnResidue birdFire = birdBody.burn;
        BurnResidue personFire = personBody.burn;
        Creature onlooker = sturdy(g.entities.spawnCreature(g.world, CreatureType.DEER, 320.5f, FEET, 332.5f));
        frames(g, 0.25);
        if (g.ragdolls.live.contains(hareBody)) {
            assertEquals(hareBody.px[Ragdoll.TORSO], hareFire.x(), 0f, "the fire goes where the falling torso goes");
            assertEquals(hareBody.py[Ragdoll.TORSO], hareFire.y(), 0f);
        }
        assertTrue(hareFire.flame() > 0f && hareFire.flame() < hareFire.flameAtDeath(), "dying down");
        assertTrue(hareFire.smoke() > 0f, "and smoking as it does");

        for (int i = 0; i < 600 && g.ragdolls.liveCount() > 0; i++) {
            frames(g, FRAME);
        }
        assertEquals(0, g.ragdolls.liveCount(), "precondition: every body has come to rest");
        assertEquals(1, g.entities.corpses.size());
        assertEquals(1, g.entities.carcasses.size());
        HumanCorpse corpse = g.entities.corpses.getFirst();
        Carcass carcass = g.entities.carcasses.getFirst();
        assertSame(personFire, corpse.burn, "the corpse carries the ragdoll's own residue, not a copy");
        assertSame(hareFire, carcass.burn);
        assertNull(personBody.burn, "which the ragdoll handed on");
        assertNull(hareBody.burn);
        assertNull(birdBody.burn, "a bird leaves nothing to carry its fire");
        assertFalse(birdFire.active(), "so its fire is let go at once");
        assertEquals(corpse.pos.x, personFire.x(), 0f, "the corpse's fire lies where the corpse lies");

        for (int i = 0; i < 900 && g.burnResidues.trackedCount() > 0; i++) {
            frames(g, FRAME);
        }
        assertEquals(0, g.burnResidues.trackedCount(), "every residue dies down");
        assertTrue(personFire.age() <= RESIDUE_FLAME_SECONDS + RESIDUE_SMOKE_SECONDS + 0.05f,
                "within its flames and smoke: " + personFire.age());
        assertEquals(0f, personFire.flame(), 0f);
        assertEquals(0f, personFire.smoke(), 0f);
        assertEquals(person.combustion.scorch(), corpse.burn.scorch(), 0f, "the scorch stays on the corpse");
        assertFalse(onlooker.combustion.hasExposure(), "remains on fire never set anyone alight");
    }

    /**
     * A blast that kills a burning body blows it apart once, and every piece
     * shares the one fire the body died with, each by its mass, so a body in
     * pieces burns no more than it did whole. The fire is never ticked again.
     */
    @ParameterizedTest
    @EnumSource(Target.class)
    void aBurningBodyABlastKillsComesApartOnceAndItsPiecesShareOneFire(Target t) {
        Game g = arena();
        Entity e = sturdy(t.spawn(g, 320.5f, FEET, 320.5f));
        light(g, e, false, 4);
        fastTicks(g, 6);
        assertTrue(g.combustion.isBurning(e), "precondition: alight");
        float intensity = e.combustion.intensity();
        float scorch = e.combustion.scorch();
        float fuel = e.combustion.fuel();
        float seconds = e.combustion.burnSeconds();

        g.explosions.explode(g, e.pos.x + 0.5f, e.pos.y + e.height * 0.5f, e.pos.z, KEG_POWER, 30f, 0f,
                false, true);
        assertTrue(e.dead && e.dismemberOnDeath, "precondition: the blast killed it");
        if (t.player()) {
            g.enterDeathIfDue();
        } else {
            g.fastTick(DT);
        }

        assertEquals(seconds, e.combustion.burnSeconds(), 0f, "the fire of a dead body is never ticked");
        assertEquals(t.pieces(), g.fragments.liveCount(), "one body, in its own pieces");
        assertEquals(0, g.ragdolls.totalSpawned, "and no whole body besides");
        BurnResidue fire = g.fragments.live.getFirst().burn;
        assertNotNull(fire);
        float totalMass = 0f;
        for (FragmentPiece p : t.family().anatomy().pieces) {
            totalMass += p.mass;
        }
        float shares = 0f;
        for (BodyFragment f : g.fragments.live) {
            assertSame(fire, f.burn, f.definition + " shares the body's one fire");
            assertEquals(f.definition.mass / totalMass, f.burnShare, 1e-6f, f.definition + " by its mass");
            shares += f.burnShare;
        }
        assertEquals(1f, shares, 1e-5f, "the shares make one body's fire");
        assertEquals(intensity, fire.flameAtDeath(), 0f);
        assertEquals(scorch, fire.scorch(), 0f);
        assertEquals(Math.min(RESIDUE_FLAME_SECONDS, fuel), fire.flameSeconds(), 0f);
        assertEquals(1, g.burnResidues.trackedCount(), "one fire for one body");
        if (t.leavesCarcass()) {
            assertEquals(1, g.entities.carcasses.size());
            assertNull(g.entities.carcasses.getFirst().burn, "the harvest record's torso carries the fire");
        }

        BodyFragment torso = g.fragments.live.getFirst();
        for (int i = 0; i < 12; i++) {
            g.fragments.update(g, (float) FRAME);
        }
        assertEquals(torso.pos.x, fire.x(), 0f, "the fire goes where the torso goes");
        assertEquals(torso.pos.z, fire.z(), 0f);
        g.fragments.settleAll(g);
        assertEquals(t.pieces(), g.fragments.totalSpawned, "settling adds nothing");
        for (BodyFragment f : g.fragments.settled) {
            assertSame(fire, f.burn, "settled pieces keep the same fire");
        }
    }

    /**
     * Whichever lethal cause comes first decides the death: the fire's tick
     * runs before any blast in a frame, a body already dead refuses a blast
     * record, and a dead body's fire never takes its kill back.
     */
    @Test
    void theFirstLethalCauseDecidesTheDeathItsCreditAndItsOneBody() {
        // The fire kills the player in a fast tick; a keg goes off at them later that frame.
        Game g = arena();
        g.player.health = 0.01f;
        light(g, g.player, false, 1);
        g.fastTick(DT);
        assertTrue(g.player.dead, "precondition: the fire killed the player");
        g.explosions.explode(g, g.player.pos.x + 0.5f, g.player.pos.y + 0.9f, g.player.pos.z,
                KEG_POWER, 30f, 0f, true, true);
        assertFalse(g.player.dismemberOnDeath, "a body already dead never takes a blast record");
        g.enterDeathIfDue();
        assertSame(Game.AppState.DEATH, g.appState);
        assertEquals(0, g.fragments.totalSpawned, "the burned player does not come apart");
        assertEquals(0, g.burnResidues.totalCaptured, "and leaves no remains to carry the fire");

        // A blast kills a burning camper whose fire is the player's: the blast's death and credit.
        Game h = arena();
        Npc camper = sturdy(h.entities.spawnNpc(h.world, "Camper", 320.5f, FEET, 320.5f));
        camper.faction = h.faction;
        camper.campIndex = 1;
        light(h, camper, true, 2);
        fastTicks(h, 4);
        assertTrue(camper.combustion.ownerByPlayer(), "precondition: the player's fire");
        float trust = h.faction.trust;
        h.explosions.explode(h, camper.pos.x + 0.5f, camper.pos.y + 0.9f, camper.pos.z, KEG_POWER, 30f, 0f,
                false, true);
        assertTrue(camper.dead && camper.dismemberOnDeath);
        assertFalse(camper.lastHitByPlayer, "nobody threw the blast that killed first");
        float health = camper.health;
        float seconds = camper.combustion.burnSeconds();
        for (int i = 0; i < 10; i++) {
            h.fastTick(DT);
        }
        assertEquals(health, camper.health, 0f, "no fire hurts a body the blast killed");
        assertEquals(seconds, camper.combustion.burnSeconds(), 0f);
        assertFalse(camper.lastHitByPlayer, "and its owner never takes the kill back");
        assertEquals(trust, h.faction.trust, 0f, "so the camp does not blame the player");
        assertEquals(Target.CAMP_MEMBER.pieces(), h.fragments.totalSpawned, "one body, blown apart once");
        assertEquals(0, h.ragdolls.totalSpawned);

        // Birds: a wild fire and the player's bomb; the player's fire and a wild blast.
        Game k = arena();
        Creature wildFire = sturdy(k.entities.spawnCreature(k.world, CreatureType.BIRD, 320.5f, FEET, 320.5f));
        Creature playersFire = sturdy(k.entities.spawnCreature(k.world, CreatureType.BIRD, 336.5f, FEET, 336.5f));
        light(k, wildFire, false, 3);
        light(k, playersFire, true, 4);
        fastTicks(k, 4);
        int meat = k.player.inventory.count(ItemType.RAW_MEAT);
        k.explosions.explode(k, wildFire.pos.x + 0.5f, wildFire.pos.y, wildFire.pos.z, 2.6f, 14f, 0f, true, true);
        k.explosions.explode(k, playersFire.pos.x + 0.5f, playersFire.pos.y, playersFire.pos.z, 2.6f, 14f, 0f,
                false, true);
        assertTrue(wildFire.dead && playersFire.dead);
        for (int i = 0; i < 10; i++) {
            k.fastTick(DT);
        }
        assertTrue(wildFire.lastHitByPlayer, "the player's bomb killed the bird the wild fire was burning");
        assertFalse(playersFire.lastHitByPlayer, "a wild blast killed the bird the player's fire was burning");
        assertEquals(meat + 1, k.player.inventory.count(ItemType.RAW_MEAT), "one meat, for the player's kill, once");
    }

    /**
     * Up to ten fast ticks can follow a death inside one frame before its
     * transition: a player the fire killed stays dead and unharmed by the
     * rest, with the fire and the burn injury frozen, and dies once.
     */
    @Test
    void theTicksAfterADeathInItsFrameDoNothingToTheBody() {
        Game g = arena();
        light(g, g.player, false, 1);
        fastTicks(g, 30);
        assertNotNull(g.player.afflictions.get(Affliction.BURN), "precondition: the burn injury");
        g.player.health = 0.01f;
        g.fastTick(DT);
        assertTrue(g.player.dead, "precondition: the fire killed the player");
        float health = g.player.health;
        float seconds = g.player.combustion.burnSeconds();
        float injury = g.player.afflictions.get(Affliction.BURN);
        float hunger = g.player.hunger;

        fastTicks(g, 9);

        assertTrue(g.player.dead);
        assertEquals(health, g.player.health, 0f, "no healing and no injury damage while dead");
        assertEquals(injury, g.player.afflictions.get(Affliction.BURN), 0f, "the injury waits for the next life");
        assertEquals(hunger, g.player.hunger, 0f, "a dead body has no needs");
        assertEquals(seconds, g.player.combustion.burnSeconds(), 0f, "and its fire is frozen");
        g.enterDeathIfDue();
        float timer = g.deathTimer;
        g.enterDeathIfDue();
        assertEquals(timer, g.deathTimer, 0f, "one death transition");
        assertEquals(0, g.fragments.totalSpawned);
    }

    // ------------------------------------------------------------------
    // Leaving the world alive
    // ------------------------------------------------------------------

    /**
     * A body flagged gone with its health left — routed, gone home, faded —
     * leaves no body, no residue and no death; its fire and panic go with it
     * rather than on, and nobody keeps it as a target or a speaker.
     */
    @Test
    void aBodyThatLeavesTheWorldAliveLeavesNoBodyAndTakesNoFireOrPanic() {
        Game g = arena();
        Npc routed = sturdy(g.entities.spawnNpc(g.world, "Routed", 320.5f, FEET, 320.5f));
        Npc watcher = sturdy(g.entities.spawnNpc(g.world, "Watcher", 330.5f, FEET, 330.5f));
        light(g, routed, true, 1);
        fastTicks(g, 3);
        assertTrue(routed.panic.active(), "precondition: burning and panicking");
        watcher.combatTarget = routed;
        g.activeNpc = routed;
        g.uiMode = Game.UiMode.CRAFTING;
        float health = routed.health;

        // How routing, a trader's departure and a fading raider flag a body that leaves.
        routed.dead = true;
        routed.lastHitByPlayer = false;
        g.fastTick(DT);

        assertFalse(g.entities.npcs.contains(routed), "gone from the world");
        assertEquals(health, routed.health, 0f, "no fire hurt it on its way out");
        assertEquals(0, g.ragdolls.totalSpawned, "no body");
        assertEquals(0, g.fragments.totalSpawned);
        assertTrue(g.entities.corpses.isEmpty());
        assertEquals(0, g.burnResidues.totalCaptured, "and no fire left behind");
        assertTrue(g.eventLog.all().stream().noneMatch(line -> line.contains("has died")), "nobody died");
        assertFalse(routed.combustion.burning() || routed.combustion.hasExposure(), "its fire is forgotten");
        assertEquals(0f, routed.combustion.scorch(), 0f);
        assertFalse(routed.panic.active(), "and so is its panic");
        assertNotSame(routed, watcher.combatTarget, "nobody keeps it as a target");
        assertNull(g.activeNpc, "nor as a speaker");
        assertSame(Game.UiMode.CRAFTING, g.uiMode, "the screen in front of the player stays");

        // An animal left far behind despawns the same way.
        Creature deer = sturdy(g.entities.spawnCreature(g.world, CreatureType.DEER, 320.5f, FEET, 316.5f));
        light(g, deer, true, 2);
        fastTicks(g, 3);
        watcher.combatTarget = deer;
        deer.pos.set(900.5f, FEET, 900.5f);
        g.entities.slowTick(g);
        assertFalse(g.entities.creatures.contains(deer));
        assertFalse(deer.combustion.burning() || deer.panic.active(), "no fire or panic goes with it");
        assertNull(watcher.combatTarget);
        assertEquals(0, g.ragdolls.totalSpawned);
    }

    /**
     * Contract section 14: a settlement going dormant writes each resident's
     * health back as it is and puts out their fire and panic, because the
     * dormant settlement has no fire; the resident wakes as a new body, calm,
     * unburned and as hurt as it left. No body, reward or reputation comes of
     * it. A party going abstract, a garrison retired or a captive led away
     * leaves through the same path.
     */
    @Test
    void aSettlementGoingDormantPutsItsResidentsFireOutAndKeepsTheirHealth() {
        Game g = arena();
        Settlement s = new Settlement(Settlement.packId(-45, -45), -45, -45, SettlementType.VILLAGE,
                new Vec3i(320, 40, 320), HumanFaction.FRONTIER, Settlement.Alignment.FRIENDLY);
        s.foodStock = 20;
        s.beds.add(s.center);
        s.dutyPoints.add(s.center);
        s.residents.add(new Settlement.Resident("Ember", NpcArchetype.VILLAGER));
        s.residents.add(new Settlement.Resident("Calm", NpcArchetype.VILLAGER));
        s.residents.add(new Settlement.Resident("Prisoner", NpcArchetype.CAPTIVE));
        g.world.settlements.put(s.id, s);
        g.player.pos.set(320.5f, FEET, 320.5f);
        g.settlementManager.slowTick(g, 1f);
        Settlement.Resident record = s.residents.getFirst();
        Npc ember = record.live;
        assertNotNull(ember, "precondition: the residents are awake");
        light(g, ember, true, 7);
        fastTicks(g, 8);
        assertTrue(g.combustion.isBurning(ember) && ember.panic.active(), "precondition: burning, panicking");
        Npc onlooker = sturdy(g.entities.spawnNpc(g.world, "Onlooker", 300.5f, FEET, 300.5f));
        onlooker.combatTarget = ember;
        g.activeNpc = ember;
        g.uiMode = Game.UiMode.INVENTORY;
        float reputation = s.localReputation;
        float health = ember.health;
        assertTrue(health < ember.maxHealth, "precondition: the fire has hurt them");

        g.player.pos.set(320.5f + 500f, FEET, 320.5f);
        g.settlementManager.slowTick(g, 1f);

        assertNull(record.live, "the settlement is dormant");
        assertFalse(g.entities.npcs.contains(ember));
        assertEquals(health, record.health, 0f, "their health is kept as it was");
        assertTrue(record.alive, "a resident leaving alive is still alive");
        assertFalse(ember.combustion.burning() || ember.panic.active(), "no fire or panic follows them");
        assertNotSame(ember, onlooker.combatTarget);
        assertNull(g.activeNpc);
        assertSame(Game.UiMode.INVENTORY, g.uiMode);
        assertEquals(reputation, s.localReputation, 0f, "going dormant is not an attack");
        assertEquals(0, g.ragdolls.totalSpawned, "nor a death");
        assertEquals(0, g.burnResidues.totalCaptured);
        float x = ember.pos.x;
        fastTicks(g, 10);
        assertEquals(x, ember.pos.x, 0f, "the dormant body no longer acts");
        assertEquals(health, record.health, 0f, "and no fire burns on in the dormant settlement");

        g.player.pos.set(320.5f, FEET, 320.5f);
        g.settlementManager.slowTick(g, 1f);
        Npc woken = record.live;
        assertNotNull(woken, "the resident wakes with the settlement");
        assertNotSame(ember, woken, "as a new body");
        assertEquals(health, woken.health, 0f, "as hurt as it left");
        assertFalse(woken.combustion.burning() || woken.combustion.hasExposure() || woken.panic.active(),
                "calm and unburned");
        assertEquals(0f, woken.combustion.scorch(), 0f);
        fastTicks(g, 10);
        assertEquals(reputation, s.localReputation, 0f, "and no attack is counted again");
        light(g, woken, false, 8);
        g.fastTick(DT);
        assertTrue(g.combustion.isBurning(woken), "fresh flames catch it as ever");

        // A burning captive led away by the player takes no fire along.
        Npc prisoner = s.residents.get(2).live;
        assertNotNull(prisoner, "precondition: the captive is awake");
        light(g, prisoner, false, 10);
        g.fastTick(DT);
        assertTrue(g.settlementManager.rescueCaptive(g, prisoner), "precondition: rescued");
        assertFalse(g.entities.npcs.contains(prisoner));
        assertFalse(prisoner.combustion.burning() || prisoner.panic.active());

        // A war party going abstract, through EntityManager.removeNpcs like CounterattackDirector.
        Npc raider = sturdy(g.entities.spawnNpc(g.world, "Raider", 300.5f, FEET, 330.5f));
        raider.warParty = true;
        raider.partyMissionId = "raid";
        light(g, raider, true, 9);
        fastTicks(g, 3);
        assertEquals(1, g.entities.removeNpcs(g, n -> "raid".equals(n.partyMissionId)));
        assertFalse(raider.combustion.burning() || raider.panic.active());

        // A speaker leaving mid-conversation closes the conversation.
        Npc trader = sturdy(g.entities.spawnNpc(g.world, "Trader", 336.5f, FEET, 336.5f));
        trader.isTrader = true;
        g.activeNpc = trader;
        g.uiMode = Game.UiMode.NPC;
        assertTrue(g.entities.removeNpc(g, trader));
        assertSame(Game.UiMode.NONE, g.uiMode);
        assertNull(g.activeNpc);
    }

    /** Another screen taking over from a conversation lets its speaker go. */
    @Test
    void aConversationAnotherScreenReplacesLetsItsSpeakerGo() {
        Game g = arena();
        Npc speaker = g.entities.spawnNpc(g.world, "Speaker", 320.5f, FEET, 320.5f);
        g.activeNpc = speaker;
        g.uiMode = Game.UiMode.NPC;
        HotkeyRouter router = new HotkeyRouter(g);
        g.input.onKey(GLFW_KEY_M, GLFW_PRESS);
        router.update();
        g.input.endFrame();
        g.input.onKey(GLFW_KEY_M, GLFW_RELEASE);
        assertSame(Game.UiMode.MAP, g.uiMode);
        assertNull(g.activeNpc, "the map does not hold on to the last speaker");
    }

    // ------------------------------------------------------------------
    // Saving and loading
    // ------------------------------------------------------------------

    /**
     * Saving settles the falling bodies and touches no fire: people, animals
     * and the player burn and panic on, and remains keep their flames. Loading
     * — here over the live session, replacing it — builds every body without a
     * fire, a panic or a residue, keeps the saved health, burn injury and
     * remains exactly once, and the injury then runs on as it always did.
     */
    @Test
    void savingLeavesEveryFireBurningAndLoadingBuildsBodiesWithoutOne() {
        Game g = arena();
        Npc keeper = sturdy(g.entities.spawnNpc(g.world, "Keeper", 295.5f, FEET, 295.5f));
        Creature hare = sturdy(g.entities.spawnCreature(g.world, CreatureType.HARE, 330.5f, FEET, 300.5f));
        Creature deer = sturdy(g.entities.spawnCreature(g.world, CreatureType.DEER, 340.5f, FEET, 340.5f));
        light(g, keeper, true, 1);
        light(g, hare, false, 2);
        light(g, deer, false, 3);
        light(g, g.player, false, 4);
        fastTicks(g, 30);
        assertNotNull(g.player.afflictions.get(Affliction.BURN), "precondition: the player's burn injury");
        hare.health = 0.01f;
        g.explosions.explode(g, deer.pos.x + 0.5f, deer.pos.y + 0.7f, deer.pos.z, KEG_POWER, 30f, 0f, false, true);
        g.fastTick(DT);
        assertTrue(hare.dead && deer.dead, "precondition: the fire killed the hare, a keg the deer");
        assertEquals(1, g.ragdolls.liveCount(), "precondition: the hare is still falling");
        assertTrue(g.fragments.liveCount() > 0, "and the deer's pieces flying");
        BurnResidue hareFire = g.ragdolls.live.getFirst().burn;
        BurnResidue deerFire = g.fragments.live.getFirst().burn;
        assertTrue(keeper.panic.active(), "precondition: the keeper is panicking");

        float fuel = keeper.combustion.fuel();
        float seconds = keeper.combustion.burnSeconds();
        float keeperHealth = keeper.health;
        float playerFuel = g.player.combustion.fuel();
        float playerHealth = g.player.health;
        float injury = g.player.afflictions.get(Affliction.BURN);
        int deerPieces = g.fragments.liveCount();
        Path file = directory.resolve("burning.sav");
        assertTrue(SaveSystem.save(g, file));

        assertEquals(fuel, keeper.combustion.fuel(), 0f, "saving leaves the keeper's fire as it was");
        assertEquals(seconds, keeper.combustion.burnSeconds(), 0f);
        assertTrue(g.combustion.isBurning(keeper) && keeper.panic.active(), "burning and panicking on");
        assertEquals(playerFuel, g.player.combustion.fuel(), 0f, "and the player's");
        assertEquals(0, g.ragdolls.liveCount(), "the save laid the hare down");
        Carcass hareCarcass = g.entities.carcasses.stream().filter(c -> c.type == CreatureType.HARE)
                .findFirst().orElseThrow();
        assertSame(hareFire, hareCarcass.burn, "still carrying its own fire");
        assertTrue(hareFire.active() && deerFire.active(), "which the save did not put out");
        for (BodyFragment f : g.fragments.settled) {
            assertSame(deerFire, f.burn);
        }
        g.fastTick(DT);
        assertTrue(keeper.combustion.burnSeconds() > seconds, "the fire burns on after the save");

        assertTrue(SaveSystem.load(g, file), "the load replaces the live world");

        assertEquals(0, g.combustion.burningBodies(g), "nobody comes back alight");
        assertEquals(0, g.burnResidues.trackedCount(), "and no remains come back burning");
        for (Npc n : g.entities.npcs) {
            assertFalse(n.combustion.burning() || n.combustion.hasExposure() || n.panic.active(), n.name);
            assertEquals(0f, n.combustion.scorch(), 0f, n.name);
        }
        for (Creature c : g.entities.creatures) {
            assertFalse(c.combustion.burning() || c.panic.active(), c.type.name());
        }
        assertFalse(g.player.combustion.burning() || g.player.combustion.hasExposure());
        Npc loaded = g.entities.npcs.stream().filter(n -> n.name.equals("Keeper")).findFirst().orElseThrow();
        assertEquals(keeperHealth, loaded.health, 0f, "the keeper's health as saved");
        assertEquals(playerHealth, g.player.health, 0f, "the player's too");
        assertEquals(injury, g.player.afflictions.get(Affliction.BURN), 0f, "and the burn injury");
        assertEquals(2, g.entities.carcasses.size(), "the hare's carcass and the deer's record, once each");
        for (Carcass c : g.entities.carcasses) {
            assertNull(c.burn);
            assertEquals(c.type.meatYield, c.meatLeft, "every bit of meat, once");
        }
        assertEquals(deerPieces, g.fragments.settledCount(), "the deer's pieces, once");
        for (BodyFragment f : g.fragments.settled) {
            assertNull(f.burn, "without their fire");
        }
        for (HumanCorpse corpse : g.entities.corpses) {
            assertNull(corpse.burn);
        }

        g.fastTick(DT);
        assertEquals(injury - DT, g.player.afflictions.get(Affliction.BURN), 1e-4f,
                "out of the flames the injury counts down again");
        // The arena was not saved; stand the keeper on the regenerated world's dry spawn.
        loaded.pos.set(g.spawnPos);
        g.combustion.expose(loaded, CombustionSource.LIQUID, 1f, false, 5, loaded.pos.x, loaded.pos.y,
                loaded.pos.z);
        g.fastTick(DT);
        assertTrue(g.combustion.isBurning(loaded), "and a fresh flame lights the loaded keeper");
    }

    // ------------------------------------------------------------------
    // A new life, a new world, another mode
    // ------------------------------------------------------------------

    /**
     * Creative forgets the player's fire while people burn on; Survival again
     * brings nothing back until a fresh flame. Respawning and a new world
     * start clear, and a new world keeps none of the old remains' fires.
     */
    @Test
    void aNewLifeAModeOrAWorldStartsWithoutTheOldFire() {
        Game g = arena();
        Npc n = sturdy(g.entities.spawnNpc(g.world, "Villager", 320.5f, FEET, 320.5f));
        light(g, n, true, 1);
        light(g, g.player, false, 2);
        fastTicks(g, 5);

        assertTrue(g.switchGameMode(GameMode.CREATIVE));
        assertFalse(g.player.combustion.burning() || g.player.combustion.hasExposure(),
                "Creative forgets the player's fire");
        assertEquals(0f, g.player.combustion.scorch(), 0f);
        assertTrue(g.combustion.isBurning(n) && n.panic.active(), "people burn on in either mode");
        assertFalse(g.combustion.expose(g.player, CombustionSource.LIQUID, 1f, false, 3,
                g.player.pos.x, g.player.pos.y, g.player.pos.z));
        fastTicks(g, 3);
        assertFalse(g.player.combustion.burning());

        assertTrue(g.switchGameMode(GameMode.SURVIVAL));
        fastTicks(g, 3);
        assertFalse(g.player.combustion.burning() || g.player.combustion.hasExposure(),
                "Survival again brings no old flame back");
        g.combustion.expose(g.player, CombustionSource.LIQUID, 1f, false, 4,
                g.player.pos.x, g.player.pos.y, g.player.pos.z);
        g.fastTick(DT);
        assertTrue(g.combustion.isBurning(g.player), "a fresh flame catches the player again");

        g.player.health = 0.01f;
        g.fastTick(DT);
        g.enterDeathIfDue();
        assertSame(Game.AppState.DEATH, g.appState);
        g.respawn();
        g.appState = Game.AppState.PLAYING;
        assertFalse(g.player.combustion.burning() || g.player.combustion.hasExposure(), "a new life, no fire");
        assertEquals(0f, g.player.combustion.scorch(), 0f);

        n.health = 0.01f;
        g.fastTick(DT);
        assertEquals(1, g.burnResidues.trackedCount(), "precondition: a body burning on the ground");
        g.activeNpc = g.entities.spawnNpc(g.world, "Speaker", 300.5f, FEET, 300.5f);
        g.newWorld(777L, true);
        assertEquals(0, g.burnResidues.trackedCount(), "a new world keeps no remains' fire");
        assertEquals(0, g.burnResidues.totalCaptured);
        assertEquals(0, g.combustion.burningBodies(g));
        assertNull(g.activeNpc, "nor the last world's speaker");
    }

    // ------------------------------------------------------------------
    // Pause and sleep
    // ------------------------------------------------------------------

    /**
     * Only a simulated frame advances a fire, a panic or a remains' flames:
     * the pause menu and the screens it opens, the debug pause and the death
     * screen freeze all of it. The inventory, crafting, map, crate, catalog
     * and conversation screens do not pause the game, and burning goes on
     * under them.
     */
    @Test
    void onlyASimulatedFrameAdvancesAFireAPanicOrARemainsFlame() {
        Game g = arena();
        Set<Game.UiMode> pausing = Set.of(Game.UiMode.PAUSE, Game.UiMode.OPTIONS, Game.UiMode.AUDIO_OPTIONS,
                Game.UiMode.GAME_MODE, Game.UiMode.WORLD_CONTROLS);
        for (Game.UiMode mode : Game.UiMode.values()) {
            g.uiMode = mode;
            assertEquals(!pausing.contains(mode), g.simulates(), mode + " pauses the game");
        }
        g.uiMode = Game.UiMode.NONE;
        g.simPaused = true;
        assertFalse(g.simulates(), "the debug pause");
        g.simPaused = false;
        g.appState = Game.AppState.DEATH;
        assertFalse(g.simulates(), "the death screen");
        g.appState = Game.AppState.PLAYING;

        Npc runner = sturdy(g.entities.spawnNpc(g.world, "Runner", 320.5f, FEET, 320.5f));
        Creature hare = sturdy(g.entities.spawnCreature(g.world, CreatureType.HARE, 330.5f, FEET, 330.5f));
        light(g, runner, false, 1);
        light(g, hare, false, 2);
        fastTicks(g, 3);
        hare.health = 0.01f;
        g.fastTick(DT);
        Ragdoll body = g.ragdolls.live.getFirst();
        BurnResidue fire = body.burn;
        assertTrue(runner.panic.active() && fire.active(), "precondition: a panicking fire and a burning body");

        for (Runnable pause : List.<Runnable>of(
                () -> g.uiMode = Game.UiMode.PAUSE,
                () -> g.uiMode = Game.UiMode.WORLD_CONTROLS,
                () -> g.simPaused = true,
                () -> g.appState = Game.AppState.DEATH)) {
            pause.run();
            float health = runner.health;
            float fuel = runner.combustion.fuel();
            float x = runner.pos.x;
            int goals = runner.panic.goals();
            float age = fire.age();
            float torso = body.px[Ragdoll.TORSO];
            double minutes = g.time.totalMinutes;
            frames(g, 1.0);
            assertEquals(health, runner.health, 0f, "no burn while paused");
            assertEquals(fuel, runner.combustion.fuel(), 0f);
            assertEquals(x, runner.pos.x, 0f, "no panic step");
            assertEquals(goals, runner.panic.goals());
            assertEquals(age, fire.age(), 0f, "no remains' flame dies down");
            assertEquals(torso, body.px[Ragdoll.TORSO], 0f, "no body falls");
            assertEquals(minutes, g.time.totalMinutes, 0.0, "no clock");
            g.uiMode = Game.UiMode.NONE;
            g.simPaused = false;
            g.appState = Game.AppState.PLAYING;
        }

        g.uiMode = Game.UiMode.INVENTORY;
        float fuel = runner.combustion.fuel();
        float age = fire.age();
        frames(g, 0.5);
        assertTrue(runner.combustion.fuel() < fuel, "under the inventory the fire burns on");
        assertEquals(age + 0.5f, fire.age(), 1e-3f, "and the remains' flame dies down in real time");
    }

    /**
     * Nobody sleeps through a fire: sleep is refused while the player burns,
     * and flames wake a sleeper even when they are too weak to flash red.
     * The night sleep runs through the clock burns nothing faster: fires and
     * remains' flames advance by the frames' real seconds, never by the hours
     * it skips.
     */
    @Test
    void nobodySleepsThroughAFireAndSleepSkipsNoFireAhead() {
        Game g = arena();
        g.time.totalMinutes = 22 * 60;
        g.player.fatigue = 90f;
        light(g, g.player, false, 1);
        g.startSleep(false);
        assertFalse(g.sleeping, "refused while alight");
        assertTrue(g.eventLog.recent(1).getFirst().contains("can't sleep while you're on fire"));
        g.combustion.extinguish(g.player);
        g.startSleep(false);
        assertTrue(g.sleeping, "precondition: put out, the player sleeps");

        Npc neighbour = sturdy(g.entities.spawnNpc(g.world, "Neighbour", 330.5f, FEET, 330.5f));
        Creature hare = sturdy(g.entities.spawnCreature(g.world, CreatureType.HARE, 300.5f, FEET, 330.5f));
        light(g, neighbour, false, 2);
        light(g, hare, false, 3);
        hare.health = 0.01f;
        g.fastTick(DT);
        BurnResidue fire = g.ragdolls.live.getFirst().burn;
        float seconds = neighbour.combustion.burnSeconds();
        float age = fire.age();
        double minutes = g.time.totalMinutes;
        for (int i = 0; i < 60; i++) {
            if (g.sleeping && g.simulates()) {
                g.sleep.tickSleep((float) FRAME);
            }
            if (g.simulates()) {
                g.advanceWorld(FRAME);
            }
        }
        assertTrue(g.sleeping, "precondition: still asleep");
        assertTrue(g.time.totalMinutes - minutes > 120.0, "the night runs by in a second");
        assertEquals(1f, neighbour.combustion.burnSeconds() - seconds, DT + 1e-4f,
                "a second of frames is a second of fire");
        assertEquals(1f, fire.age() - age, 1e-3f, "and a second of the remains' flames");

        g.combustion.ignite(g, g.player, CombustionSource.DIRECT_HIT, 1f, false, 4,
                g.player.pos.x, g.player.pos.y + 1f, g.player.pos.z);
        g.player.damageFlash = 0f;
        g.sleep.tickSleep((float) FRAME);
        assertFalse(g.sleeping, "flames wake the sleeper, flash or not");
        assertTrue(g.eventLog.recent(1).getFirst().contains("You wake up on fire!"));
    }

    // ------------------------------------------------------------------
    // Remains' flames: weather, bounds, references
    // ------------------------------------------------------------------

    /** Water at the remains puts their flames out at once, open rain as it does a living body's; a roof keeps it off. */
    @Test
    void waterOrOpenRainPutsARemainsFlameOutAsItWouldALivingBody() {
        Game g = arena();
        fill(g, 317, 323, 40, 40, 317, 323, BlockType.STONE);
        fill(g, 318, 322, 40, 40, 318, 322, BlockType.WATER);
        Npc wader = sturdy(g.entities.spawnNpc(g.world, "Wader", 320.5f, FEET, 320.5f));
        Npc dry = sturdy(g.entities.spawnNpc(g.world, "Dry", 300.5f, FEET, 300.5f));
        light(g, wader, false, 1);
        light(g, dry, false, 2);
        g.fastTick(DT);
        assertTrue(g.combustion.isBurning(wader), "precondition: hip-deep water does not put a person out");
        wader.health = dry.health = 0.01f;
        g.fastTick(DT);
        BurnResidue drowned = residueOfRagdoll(g, 0);
        BurnResidue burning = residueOfRagdoll(g, 1);
        frames(g, 2.0);
        assertTrue(drowned.doused(), "the body falls into the water");
        assertTrue(drowned.flameSeconds() < 2f, "which puts it out long before it burns down");
        assertEquals(0f, drowned.flame(), 0f, "its flames are out");
        assertTrue(drowned.smoke() > 0f, "and it steams");
        assertFalse(burning.doused());
        assertTrue(burning.flame() > 0f, "a body on dry ground burns on");

        Game wet = arena();
        wet.weather.current = Weather.RAIN;
        wet.weather.next = Weather.RAIN;
        wet.weather.blend = 1f;
        wet.weather.changeTimer = 1e6f;
        fill(wet, 290, 310, 43, 43, 290, 310, BlockType.STONE);
        Npc open = sturdy(wet.entities.spawnNpc(wet.world, "Open", 320.5f, FEET, 320.5f));
        Npc roofed = sturdy(wet.entities.spawnNpc(wet.world, "Roofed", 300.5f, FEET, 300.5f));
        light(wet, open, false, 3);
        light(wet, roofed, false, 4);
        // A second of rain on the living body, which dies before the rain puts it out.
        fastTicks(wet, 20);
        assertTrue(wet.combustion.isBurning(open), "precondition: not rained out yet");
        open.health = roofed.health = 0.01f;
        wet.fastTick(DT);
        BurnResidue rained = residueOfRagdoll(wet, 0);
        BurnResidue sheltered = residueOfRagdoll(wet, 1);
        frames(wet, 0.3);
        assertFalse(rained.doused(), "the rain takes a moment more");
        frames(wet, 0.3);
        assertTrue(rained.doused(), "and puts the open flames out once 1.5 s of rain have fallen, "
                + "counting the rain on the living body");
        assertTrue(rained.flameSeconds() < 0.6f, "not 1.5 s after death: " + rained.flameSeconds());
        assertFalse(sheltered.doused(), "a roofed body burns on");
        assertTrue(sheltered.flame() > 0f);
    }

    /**
     * At most {@code MAX_BURN_RESIDUES} remains flame at once: past it the
     * oldest lose their flames, never their scorch. Whatever carries a fire
     * leaving the world — a corpse rotting, the last piece of a body — lets
     * it go, and the residue holds no reference to any body.
     */
    @Test
    void atMostThirtyTwoRemainsFlameAtOnceAndWhatLeavesTheWorldLetsItsFireGo() {
        for (Field f : BurnResidue.class.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers())) {
                assertTrue(f.getType().isPrimitive(), f.getName() + " is a copied value, not a reference");
            }
        }

        Game g = arena();
        List<Npc> crowd = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            Npc n = sturdy(g.entities.spawnNpc(g.world, "Crowd " + i,
                    292.5f + (i % 10) * 5f, FEET, 292.5f + (i / 10) * 5f));
            light(g, n, false, 10 + i);
            crowd.add(n);
        }
        fastTicks(g, 4);
        for (Npc n : crowd) {
            n.health = 0.01f;
        }
        g.fastTick(DT);

        assertEquals(40, g.ragdolls.totalSpawned);
        assertEquals(RagdollConstants.MAX_LIVE, g.ragdolls.liveCount(), "the ragdoll cap laid the oldest down");
        assertEquals(40 - RagdollConstants.MAX_LIVE, g.entities.corpses.size());
        assertEquals(MAX_BURN_RESIDUES, g.burnResidues.trackedCount(), "the residue cap holds");
        assertEquals(40 - MAX_BURN_RESIDUES, g.burnResidues.totalEvicted);
        assertTrue(RuntimeBudgetSnapshot.capture(g).withinHardLimits());
        assertEquals(MAX_BURN_RESIDUES, RuntimeBudgetSnapshot.capture(g).burnResidues());
        List<BurnResidue> fires = new ArrayList<>();
        for (HumanCorpse corpse : g.entities.corpses) {
            fires.add(corpse.burn);
        }
        for (Ragdoll r : g.ragdolls.live) {
            fires.add(r.burn);
        }
        assertEquals(40, distinct(fires.toArray(new BurnResidue[0])), "each body its own fire");
        for (int i = 0; i < 40; i++) {
            BurnResidue fire = fires.get(i);
            assertEquals(i >= 40 - MAX_BURN_RESIDUES, fire.active(), "the oldest " + (40 - MAX_BURN_RESIDUES)
                    + " lost their flames: body " + i);
            assertEquals(i >= 40 - MAX_BURN_RESIDUES, fire.flame() > 0f, "body " + i);
            assertTrue(fire.scorch() > 0f, "every body keeps its scorch");
        }

        g.entities.tickWorldDetritus(g, RagdollConstants.CORPSE_DECAY + 1f);
        assertTrue(g.entities.corpses.isEmpty(), "precondition: the corpses have rotted away");
        g.burnResidues.update(g, (float) FRAME);
        assertEquals(RagdollConstants.MAX_LIVE, g.burnResidues.trackedCount(),
                "their fires went with them; the falling bodies keep theirs");

        Npc blown = sturdy(g.entities.spawnNpc(g.world, "Blown", 320.5f, FEET, 340.5f));
        light(g, blown, false, 99);
        fastTicks(g, 2);
        g.explosions.explode(g, blown.pos.x + 0.5f, blown.pos.y + 0.9f, blown.pos.z, KEG_POWER, 30f, 0f,
                false, true);
        g.fastTick(DT);
        BurnResidue pieces = g.fragments.live.getFirst().burn;
        assertTrue(pieces.active());
        g.fragments.settleAll(g);
        g.fragments.slowTick(g, RagdollConstants.CORPSE_DECAY + 1f);
        assertEquals(0, g.fragments.settledCount(), "precondition: every piece has rotted away");
        g.burnResidues.update(g, (float) FRAME);
        assertFalse(pieces.active(), "the last piece to go let the body's fire go");
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private static void assertResidueOf(Entity e, BurnResidue r) {
        assertEquals(e.combustion.intensity(), r.flameAtDeath(), 0f, "the strength it burned with");
        assertEquals(e.combustion.scorch(), r.scorch(), 0f, "the scorch it had");
        assertEquals(Math.min(RESIDUE_FLAME_SECONDS, e.combustion.fuel()), r.flameSeconds(), 0f,
                "as long as its afterburn would have lasted, at most " + RESIDUE_FLAME_SECONDS + " s");
        assertTrue(r.active());
    }

    private static int distinct(BurnResidue... residues) {
        Set<BurnResidue> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Collections.addAll(seen, residues);
        return seen.size();
    }

    private static Ragdoll ragdoll(Game g, CreatureType type) {
        return g.ragdolls.live.stream().filter(r -> r.creatureType == type).findFirst().orElseThrow();
    }

    private static BurnResidue residueOfRagdoll(Game g, int index) {
        BurnResidue r = g.ragdolls.live.get(index).burn;
        assertNotNull(r, "precondition: body " + index + " died alight");
        return r;
    }

    private static void light(Game g, Entity e, boolean byPlayer, int bottle) {
        assertTrue(g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, byPlayer, bottle,
                e.pos.x, e.pos.y + e.height * 0.5f, e.pos.z), "precondition: lit");
    }

    private static <T extends Entity> T sturdy(T e) {
        e.maxHealth = STURDY;
        e.health = STURDY;
        return e;
    }

    private static void fastTicks(Game g, int n) {
        for (int i = 0; i < n; i++) {
            g.fastTick(DT);
        }
    }

    /** Frames exactly as {@code Game.frame} advances the world: only while it simulates. */
    private static void frames(Game g, double seconds) {
        int n = (int) Math.round(seconds / FRAME);
        for (int i = 0; i < n; i++) {
            if (g.simulates()) {
                g.advanceWorld(FRAME);
            }
        }
    }

    private static void fill(Game g, int x0, int x1, int y0, int y1, int z0, int z1, BlockType type) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    g.world.setBlock(x, y, z, type, false);
                }
            }
        }
    }

    /**
     * {@code CombustionIntegrationTest}'s arena: four chunks of stone up to
     * y = 39, nobody about, dry and settled weather, the camp forgotten, the
     * player standing at (310, 40, 310) in a world that is being played.
     */
    private static Game arena() {
        Game game = new Game();
        game.newWorld(777L, true);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = game.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < 16; lx++) {
                    for (int lz = 0; lz < 16; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        game.player.pos.set(310, FEET, 310);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.entities.corpses.clear();
        game.world.campPos = null;
        game.weather.current = Weather.CLEAR;
        game.weather.next = Weather.CLEAR;
        game.weather.blend = 1f;
        game.weather.changeTimer = 1e6f;
        game.appState = Game.AppState.PLAYING;
        return game;
    }
}
