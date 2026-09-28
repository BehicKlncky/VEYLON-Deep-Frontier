package com.veylon;

import com.veylon.combat.ExplosionSystem;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Carcass;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Entity;
import com.veylon.entity.FragmentAnatomy;
import com.veylon.entity.GameMode;
import com.veylon.entity.Npc;
import com.veylon.entity.NpcAppearance;
import com.veylon.entity.Player;
import com.veylon.item.ItemType;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scrap bomb or a powder keg kills every living body whose centre is inside
 * {@code power × LETHAL_RADIUS_FACTOR} — every animal, every kind of person
 * and a Survival player — whatever its health and whatever stands between,
 * and the one death that follows blows it apart at its own joints: one death
 * event, one body made of its species' pieces, one harvest record, and
 * nothing again on later ticks, a second blast or a respawn. A Creative
 * player is the only body the rule passes over.
 *
 * <p>Blasts go off at the height of the target's body centre, so the
 * distances below are the horizontal distances the rule measures.
 */
class AllLivingBlastDeathTest {

    private static final float SCRAP_POWER = 2.6f, SCRAP_DAMAGE = 14f;
    private static final float KEG_POWER = 3.8f;
    private static final float FEET = 40.1f;
    private static final float BX = 320.5f, BZ = 320.5f;
    private static final float TOUGH = 10_000f;

    /** Every kind of living body the lethal rule covers. */
    enum Target {
        DEER, WOLF, BIRD, HARE, THORNHORN, STALKER,
        CAMP_MEMBER, WANDERING_TRADER, RAIDER,
        VILLAGER, SETTLEMENT_TRADER, CAPTIVE,
        PATROL, BOUNTY_HUNTER, COUNTERATTACKER,
        SURVIVAL_PLAYER;

        CreatureType species() {
            return ordinal() <= STALKER.ordinal() ? CreatureType.values()[ordinal()] : null;
        }

        boolean player() {
            return this == SURVIVAL_PLAYER;
        }

        BodyFamily family() {
            return species() != null ? BodyFamily.of(species()) : BodyFamily.HUMANOID;
        }

        int pieces() {
            return family().anatomy().pieces.size();
        }

        boolean leavesCarcass() {
            return species() != null && species().leavesCarcass();
        }

        /** This body, standing with its feet at {@code (x, feet, z)}. */
        Entity spawn(Game g, float x, float feet, float z) {
            if (species() != null) {
                return g.entities.spawnCreature(g.world, species(), x, feet, z);
            }
            if (player()) {
                g.player.pos.set(x, feet, z);
                g.player.vel.zero();
                return g.player;
            }
            Npc n = g.entities.spawnNpc(g.world, name(), x, feet, z);
            switch (this) {
                case CAMP_MEMBER -> {
                    n.faction = g.faction;
                    n.campIndex = 1;
                }
                case WANDERING_TRADER -> n.isTrader = true;
                case RAIDER -> n.raider = true;
                case VILLAGER -> reside(g, n, NpcArchetype.VILLAGER, Settlement.Alignment.NEUTRAL);
                case SETTLEMENT_TRADER -> reside(g, n, NpcArchetype.TRADER, Settlement.Alignment.NEUTRAL);
                case CAPTIVE -> reside(g, n, NpcArchetype.CAPTIVE, Settlement.Alignment.HOSTILE);
                case PATROL -> party(n, Npc.PartyKind.PATROL);
                case BOUNTY_HUNTER -> party(n, Npc.PartyKind.BOUNTY_HUNTER);
                case COUNTERATTACKER -> party(n, Npc.PartyKind.COUNTERATTACK);
                default -> throw new AssertionError(this);
            }
            if (n.archetype != null) {
                n.maxHealth = n.archetype.maxHealth;
                n.health = n.maxHealth;
            }
            return n;
        }

        private static void reside(Game g, Npc n, NpcArchetype archetype, Settlement.Alignment side) {
            Settlement home = settlement(g, side == Settlement.Alignment.HOSTILE ? -44 : -45,
                    side == Settlement.Alignment.HOSTILE ? HumanFaction.HEADHUNTERS : HumanFaction.FREE_SETTLERS,
                    side, n.pos.x, n.pos.z);
            Settlement.Resident record = new Settlement.Resident(n.name, archetype);
            home.residents.add(record);
            n.archetype = archetype;
            n.settlementId = home.id;
            n.residentIndex = home.residents.size() - 1;
            record.live = n;
        }

        private static void party(Npc n, Npc.PartyKind kind) {
            n.archetype = NpcArchetype.HUNTER;
            n.warParty = true;
            n.partyKind = kind;
            n.partyMissionId = "test-" + kind.name();
            n.partyMemberId = n.partyMissionId + ":0";
            n.partyFactionId = HumanFaction.HEADHUNTERS;
        }
    }

    // ------------------------------------------------------------------
    // The lethal radius, for every body
    // ------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(Target.class)
    void insideTheRadiusEveryLivingBodyDiesAndComesApartExactlyOnce(Target t) {
        Game g = arena();
        Entity e = t.spawn(g, BX + 2f, FEET, BZ);
        float by = centreY(e);

        g.explosions.explode(g, BX, by, BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, true);
        assertTrue(e.dead && e.health <= 0, t + " dies outright, a real death not a despawn");
        assertBlastRecord(e, BX, by, BZ, SCRAP_POWER, t + ": the record holds the blast that killed");
        if (!t.player()) {
            assertTrue(e.lastHitByPlayer, t + ": the kill is credited to whoever threw the bomb");
        }

        // A second blast before the body is taken finds it already dead.
        g.explosions.explode(g, BX - 1f, by, BZ + 1f, KEG_POWER, 30f, 0f, true, true);
        assertBlastRecord(e, BX, by, BZ, SCRAP_POWER, t + ": the first fatal blast's record is kept");

        takeTheDead(g, t);
        assertOneBody(g, t, e);
        if (!t.player()) {
            assertFalse(g.entities.npcs.contains(e) || g.entities.creatures.contains(e),
                    t + " leaves the living on that tick");
        }
        List<String> log = g.eventLog.all();

        // Later ticks, repeated transitions and the pieces landing change nothing.
        for (int tick = 0; tick < 40; tick++) {
            takeTheDead(g, t);
            g.fragments.update(g, 1f / 60f);
        }
        settle(g);
        g.entities.tickWorldDetritus(g, 10f);
        assertEquals(t.pieces(), g.fragments.totalSpawned, t + " comes apart once");
        assertEquals(t.pieces(), g.fragments.settledCount());
        assertEquals(t.leavesCarcass() ? 1 : 0, g.entities.carcasses.size(), t + ": one harvest record at most");
        assertEquals(0L, g.ragdolls.totalSpawned, t + " never also falls whole");
        assertTrue(g.entities.corpses.isEmpty());
        assertEquals(log, g.eventLog.all(), t + ": nothing more happens as the pieces land");

        if (t.player()) {
            g.respawn();
            g.appState = Game.AppState.PLAYING;
            assertFalse(g.player.dead, "precondition: the player lives again");
            assertFalse(g.player.dismemberOnDeath, "respawning leaves no blast record behind");
            g.enterDeathIfDue();
            assertEquals(t.pieces(), g.fragments.totalSpawned, "a respawn spawns no second body");
        }
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void theBoundaryIsInclusiveAndOneStepOutsideKeepsTheOrdinaryModel(Target t) {
        float radius = SCRAP_POWER * ExplosionSystem.LETHAL_RADIUS_FACTOR;
        float inside = BX + radius;
        while (inside - BX > radius) {
            inside = Math.nextDown(inside);
        }
        while (Math.nextUp(inside) - BX <= radius) {
            inside = Math.nextUp(inside);
        }
        float outside = Math.nextUp(inside);
        assertTrue(inside - BX <= radius && outside - BX > radius, "precondition: adjacent floats straddle the radius");

        Game edge = arena();
        Entity atEdge = tough(t.spawn(edge, inside, FEET, BZ));
        edge.explosions.explode(edge, BX, centreY(atEdge), BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, true);
        assertTrue(atEdge.dead && atEdge.dismemberOnDeath,
                t + " with its centre exactly on the radius dies and comes apart, " + TOUGH + " health or not");

        Game beyond = arena();
        Entity past = tough(t.spawn(beyond, outside, FEET, BZ));
        beyond.explosions.explode(beyond, BX, centreY(past), BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, true);
        Game ordinary = arena();
        Entity twin = tough(t.spawn(ordinary, outside, FEET, BZ));
        ordinary.explosions.explode(ordinary, BX, centreY(twin), BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, false);

        assertFalse(past.dead || past.dismemberOnDeath, t + " one float beyond the radius is outside it");
        assertTrue(past.health < TOUGH, t + " still takes the blast");
        assertEquals(twin.health, past.health, 0f, t + ": exactly the falloff damage of a blast that is not lethal");
        assertEquals(twin.vel.x, past.vel.x, 0f, t + ": and the same knockback");
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void coverDoesNotSaveABodyInsideTheRadius(Target t) {
        Game g = arena();
        for (int x = 321; x <= 322; x++) {
            for (int y = 40; y <= 44; y++) {
                for (int z = 315; z <= 326; z++) {
                    g.world.setBlock(x, y, z, BlockType.STONE_BRICK, false);
                }
            }
        }
        Entity e = tough(t.spawn(g, BX + 3.5f, FEET, BZ));
        g.explosions.explode(g, BX, centreY(e), BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, true);

        assertEquals(BlockType.STONE_BRICK, g.world.getBlock(321, 41, 320), "precondition: the cover stands");
        assertTrue(e.dead && e.dismemberOnDeath, t + " behind a wall inside the radius still dies and comes apart");
    }

    @ParameterizedTest
    @EnumSource(Target.class)
    void creativeProtectsOnlyThePlayer(Target t) {
        Game g = arena();
        g.restoreGameMode(GameMode.CREATIVE, true, false);
        assertTrue(g.player.abilities.invulnerable(), "precondition: Creative");
        Entity e = t.spawn(g, BX + 2f, FEET, BZ);
        float health = e.health;

        g.explosions.explode(g, BX, centreY(e), BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, true);
        if (t.player()) {
            assertFalse(e.dead, "a Creative player is never killed");
            assertEquals(health, e.health, 0f, "nor hurt");
            assertFalse(e.dismemberOnDeath, "nor marked to come apart");
            assertEquals(0f, e.vel.length(), 0f, "nor thrown");
            g.appState = Game.AppState.PLAYING;
            g.enterDeathIfDue();
            assertEquals(Game.AppState.PLAYING, g.appState, "there is no death to transition to");
            assertEquals(0L, g.fragments.totalSpawned, "and no remains");
            return;
        }
        assertTrue(e.dead && e.dismemberOnDeath, t + " stays mortal in Creative");
        takeTheDead(g, t);
        assertOneBody(g, t, e);
    }

    // ------------------------------------------------------------------
    // Production paths
    // ------------------------------------------------------------------

    @Test
    void aThrownScrapBombBlowsAWolfApartForOneCreditOneRecordAndOneHarvest() {
        Game g = arena();
        g.projectiles.setRandomSeed(11L);
        Creature wolf = (Creature) Target.WOLF.spawn(g, 313.5f, FEET, 310.5f);
        wolf.stuckArrows = 2;
        wolf.stuckArrowType = ItemType.ARROW;
        Settlement village = settlement(g, -46, HumanFaction.FREE_SETTLERS, Settlement.Alignment.NEUTRAL,
                wolf.pos.x, wolf.pos.z);
        // Two watchers far out of the blast that still hold the wolf as their target.
        Npc guard = g.entities.spawnNpc(g.world, "Guard", 330.5f, FEET, 330.5f);
        guard.combatTarget = wolf;
        Creature rival = g.entities.spawnCreature(g.world, CreatureType.WOLF, 345.5f, FEET, 345.5f);
        rival.hunger = 0f; // fed, so it neither hunts nor scavenges the record
        rival.targetEntity = wolf;

        g.projectiles.fire(g, g.player, true, 310.5f, 41.1f, 310.5f, 1, 0, 0,
                WeaponRegistry.byId("scrap_bomb"), null);
        for (int i = 0; i < 200 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.02f);
        }
        assertEquals(1, g.noise.countCategory("explosion"), "precondition: the bomb went off on its fuse");
        assertTrue(wolf.dead && wolf.dismemberOnDeath && wolf.lastHitByPlayer,
                "the bomb that stopped at the wolf's feet kills it and blows it apart");
        assertEquals(SCRAP_POWER, wolf.blastStrength, 0f);

        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        assertEquals(Target.WOLF.pieces(), g.fragments.liveCount(), "the wolf comes apart at its own joints");
        assertEquals(0, g.ragdolls.liveCount());
        assertEquals(6f, village.localReputation, 1e-4f, "the kill is credited once, on the tick of the death");
        assertEquals(1, logLines(g, "saw you clear a nearby threat"));
        assertNull(guard.combatTarget, "no one keeps the dead wolf as a target");
        assertNull(rival.targetEntity);
        assertFalse(g.entities.creatures.contains(wolf));
        assertTrue(g.entities.nearestCreature(wolf.pos.x, wolf.pos.y, wolf.pos.z, 40f, c -> true) != wolf);
        assertEquals(1, g.entities.carcasses.size(), "one harvest record");
        Carcass record = g.entities.carcasses.getFirst();
        assertTrue(record.fragmented() && record.remains == g.fragments.live.getFirst(),
                "tied to the torso, never drawn whole");

        g.player.inventory.add(ItemType.BONE_KNIFE, 1);
        g.player.pos.set(record.pos.x, FEET, record.pos.z);
        assertFalse(g.interactWithNearbyCarcass(), "the body is still falling: nothing to harvest yet");

        for (int tick = 0; tick < 1200 && g.fragments.liveCount() > 0; tick++) {
            if (tick % 3 == 0) {
                g.entities.fastTick(g, SimulationScheduler.FAST_DT);
            }
            g.fragments.update(g, 1f / 60f);
        }
        assertEquals(0, g.fragments.liveCount(), "precondition: the pieces have landed");
        assertEquals(Target.WOLF.pieces(), g.fragments.totalSpawned, "and none came again");
        assertEquals(List.of(record), g.entities.carcasses, "the record is still the only one");
        assertEquals(6f, village.localReputation, 1e-4f, "and the credit still single");

        int meat = g.player.inventory.count(ItemType.RAW_MEAT);
        int hide = g.player.inventory.count(ItemType.HIDE);
        int arrows = g.player.inventory.count(ItemType.ARROW);
        g.player.pos.set(record.pos.x + 0.5f, record.pos.y, record.pos.z);
        assertTrue(g.interactWithNearbyCarcass(), "the landed torso is harvested through the F-key command");
        assertEquals(meat + CreatureType.WOLF.meatYield, g.player.inventory.count(ItemType.RAW_MEAT));
        assertEquals(hide + CreatureType.WOLF.hideYield, g.player.inventory.count(ItemType.HIDE));
        assertEquals(arrows + 2, g.player.inventory.count(ItemType.ARROW), "the lodged arrows come back");
        g.interactWithNearbyCarcass();
        assertEquals(meat + CreatureType.WOLF.meatYield, g.player.inventory.count(ItemType.RAW_MEAT),
                "one body's yield, once");
        assertEquals(hide + CreatureType.WOLF.hideYield, g.player.inventory.count(ItemType.HIDE));
        assertEquals(arrows + 2, g.player.inventory.count(ItemType.ARROW));

        g.entities.tickWorldDetritus(g, 10f);
        assertTrue(g.entities.carcasses.isEmpty(), "the emptied record leaves the world");
        assertTrue(g.fragments.settled.contains(record.remains), "its torso stays to rot like any piece");
        assertFalse(g.interactWithNearbyCarcass(), "and no limb has anything to give");
    }

    @Test
    void chainedKegsKillEachBodyOnceAndThePlayerComesApartAtTheDeathTransition() {
        Game g = arena();
        Vec3i kegA = new Vec3i(320, 40, 320);
        Vec3i kegB = new Vec3i(323, 40, 320);
        g.world.setBlock(kegA.x(), kegA.y(), kegA.z(), BlockType.POWDER_KEG, false);
        g.world.setBlock(kegB.x(), kegB.y(), kegB.z(), BlockType.POWDER_KEG, false);
        Creature hare = (Creature) Target.HARE.spawn(g, 318.5f, FEET, 320.5f);
        // Eight blocks from the lit keg, five from the keg it sets off.
        Creature deer = (Creature) Target.DEER.spawn(g, 328.5f, FEET, 320.5f);
        Player player = (Player) Target.SURVIVAL_PLAYER.spawn(g, 320.5f, FEET, 324.5f);
        g.appState = Game.AppState.PLAYING;

        assertTrue(g.explosions.tryArmKeg(g, kegA, 0.1f, true));
        g.explosions.tickFuses(g, 0.2f);
        assertEquals(BlockType.AIR, g.world.getBlock(kegB.x(), kegB.y(), kegB.z()),
                "precondition: the first keg set off the second");

        assertTrue(hare.dead && hare.dismemberOnDeath, "a lit keg is lethal to an animal");
        assertEquals(kegA.x() + 0.5f, hare.blastX, 0f, "the first fatal blast is the one kept");
        assertTrue(deer.dead && deer.dismemberOnDeath, "the keg it set off is lethal too");
        assertEquals(kegB.x() + 0.5f, deer.blastX, 0f,
                "the deer out of the first keg's radius was killed by the second");
        assertTrue(player.dead && player.dismemberOnDeath, "and a Survival player dies to the first");
        assertEquals(kegA.x() + 0.5f, player.blastX, 0f);
        assertEquals(KEG_POWER, player.blastStrength, 0f);

        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        int animals = Target.HARE.pieces() + Target.DEER.pieces();
        assertEquals(animals, g.fragments.liveCount(), "each animal comes apart at its own joints");
        assertEquals(2, g.entities.carcasses.size(), "one harvest record each");

        g.enterDeathIfDue();
        assertEquals(Game.AppState.DEATH, g.appState, "the player's death screen is the ordinary one");
        int all = animals + Target.SURVIVAL_PLAYER.pieces();
        assertEquals(all, g.fragments.liveCount(), "the death transition blows the player apart");
        assertFalse(player.dismemberOnDeath, "spending the record");
        List<BodyFragment> remains = new ArrayList<>(g.fragments.live.subList(animals, all));
        for (BodyFragment f : remains) {
            assertSame(BodyFamily.HUMANOID, f.definition.family, "the player's remains are a person's pieces");
            assertNull(f.appearance.archetype, "in the neutral look");
            assertFalse(f.appearance.raider || f.appearance.trader || f.appearance.sick);
            assertEquals(NpcAppearance.NEUTRAL_CAMP_INDEX, f.appearance.campIndex);
        }
        Vector3f torso = new Vector3f(remains.getFirst().pos);

        for (int i = 0; i < 5; i++) {
            g.enterDeathIfDue();
            g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        }
        assertEquals(all, g.fragments.totalSpawned, "no body comes apart twice");

        g.respawn();
        g.appState = Game.AppState.PLAYING;
        assertFalse(player.dead, "the same player object lives again");
        assertTrue(player.pos.distance(torso) > 1f, "precondition: respawned somewhere else");
        assertEquals(torso, remains.getFirst().pos, "the remains do not follow the living player");
        g.enterDeathIfDue();
        assertEquals(all, g.fragments.totalSpawned, "and respawning spawns nothing");

        player.hurt(player.health + 1f, false);
        g.enterDeathIfDue();
        assertEquals(Game.AppState.DEATH, g.appState, "precondition: an ordinary death");
        assertEquals(all, g.fragments.totalSpawned, "a death that was not a blast leaves no remains");
    }

    @Test
    void anAirborneBirdIsBlownApartAndItsPiecesFallToTheGround() {
        Game g = arena();
        Creature bird = (Creature) Target.BIRD.spawn(g, BX + 1.5f, FEET + 6f, BZ);
        int meat = g.player.inventory.count(ItemType.RAW_MEAT);

        g.explosions.explode(g, BX, centreY(bird), BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, true);
        assertTrue(bird.dead && bird.dismemberOnDeath, "a flying bird inside the radius dies and comes apart");
        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        assertEquals(Target.BIRD.pieces(), g.fragments.liveCount());
        assertEquals(meat + 1, g.player.inventory.count(ItemType.RAW_MEAT), "the hunter's meat, once");
        assertTrue(g.entities.carcasses.isEmpty(), "a bird leaves no carcass");

        settle(g);
        for (BodyFragment f : g.fragments.settled) {
            assertEquals(40f, f.pos.y - f.halfHeight, 0.02f, f.definition + " falls to the ground");
        }
        assertEquals(meat + 1, g.player.inventory.count(ItemType.RAW_MEAT));
    }

    @Test
    void deathsThatAreNotALethalBlastStillFallWhole() {
        Game g = arena();
        // Killed by a blast that is not lethal, and by the falloff just outside a lethal one.
        Creature hare = (Creature) Target.HARE.spawn(g, BX + 2f, FEET, BZ);
        hare.health = 1f;
        g.explosions.explode(g, BX, centreY(hare), BZ, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, false);
        Creature deer = (Creature) Target.DEER.spawn(g, BX + 4.5f, FEET, BZ + 20f);
        deer.health = 1f;
        g.explosions.explode(g, BX, centreY(deer), BZ + 20f, SCRAP_POWER, SCRAP_DAMAGE, 0f, true, true);
        assertTrue(hare.dead && deer.dead, "precondition: both blasts killed");
        assertFalse(hare.dismemberOnDeath || deer.dismemberOnDeath, "neither was a lethal-radius death");
        g.player.hurt(g.player.health + 1f, false);
        g.appState = Game.AppState.PLAYING;

        g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        g.enterDeathIfDue();
        assertEquals(2, g.ragdolls.liveCount(), "each animal falls whole");
        assertEquals(0L, g.fragments.totalSpawned, "and nobody comes apart");
        for (int frame = 0; frame < 1200 && g.ragdolls.liveCount() > 0; frame++) {
            g.ragdolls.update(g, 1f / 60f);
        }
        assertEquals(2, g.entities.carcasses.size());
        for (Carcass c : g.entities.carcasses) {
            assertFalse(c.fragmented(), c.type + " leaves an ordinary whole carcass");
            assertTrue(c.atRest());
        }
    }

    // ------------------------------------------------------------------

    private static void assertBlastRecord(Entity e, float x, float y, float z, float power, String message) {
        assertTrue(e.dismemberOnDeath, message);
        assertEquals(x, e.blastX, 0f, message);
        assertEquals(y, e.blastY, 0f, message);
        assertEquals(z, e.blastZ, 0f, message);
        assertEquals(power, e.blastStrength, 0f, message);
    }

    /** The one body a blast death leaves: its family's pieces, and a record for an animal that has one. */
    private static void assertOneBody(Game g, Target t, Entity e) {
        assertEquals(t.pieces(), g.fragments.liveCount(), t + " becomes its family's pieces");
        assertEquals(t.pieces(), g.fragments.totalSpawned);
        for (BodyFragment f : g.fragments.live) {
            assertSame(t.family(), f.definition.family, t + " comes apart at its own joints");
        }
        assertEquals(0, g.ragdolls.liveCount(), t + " does not also fall whole");
        assertTrue(g.entities.corpses.isEmpty());
        assertEquals(t.leavesCarcass() ? 1 : 0, g.entities.carcasses.size(),
                t + ": exactly one harvest record for an animal that leaves a carcass");
        if (t.leavesCarcass()) {
            Carcass record = g.entities.carcasses.getFirst();
            assertSame(g.fragments.live.getFirst(), record.remains, "tied to the torso");
            assertEquals(t.species().meatYield, record.meatLeft);
            assertEquals(t.species().hideYield, record.hideLeft);
        }
        if (t.player()) {
            assertEquals(Game.AppState.DEATH, g.appState);
            assertFalse(e.dismemberOnDeath, "the transition spends the player's record");
        }
    }

    /** Runs the one step that takes a dead body out of the world: the entity tick, or the player's death transition. */
    private static void takeTheDead(Game g, Target t) {
        if (t.player()) {
            if (g.appState != Game.AppState.DEATH) {
                g.appState = Game.AppState.PLAYING;
            }
            g.enterDeathIfDue();
        } else {
            g.entities.fastTick(g, SimulationScheduler.FAST_DT);
        }
    }

    private static Entity tough(Entity e) {
        e.maxHealth = TOUGH;
        e.health = TOUGH;
        return e;
    }

    private static float centreY(Entity e) {
        return e.pos.y + e.height * 0.5f;
    }

    private static void settle(Game g) {
        for (int i = 0; i < 1200 && g.fragments.liveCount() > 0; i++) {
            g.fragments.update(g, 1f / 60f);
        }
        assertEquals(0, g.fragments.liveCount(), "precondition: every piece has settled");
    }

    private static int logLines(Game g, String fragment) {
        int count = 0;
        for (String line : g.eventLog.all()) {
            if (line.contains(fragment)) {
                count++;
            }
        }
        return count;
    }

    /** A settlement keyed by a far region, centred on {@code (x, z)}; it owns no blocks here. */
    private static Settlement settlement(Game g, int region, String faction, Settlement.Alignment alignment,
                                         float x, float z) {
        Settlement s = new Settlement(Settlement.packId(region, region), region, region,
                SettlementType.VILLAGE, new Vec3i((int) x, (int) FEET, (int) z), faction, alignment);
        s.factionId = faction;
        g.world.settlements.put(s.id, s);
        return s;
    }

    /** {@code BlastLethalityTest}'s isolated arena: four chunks of stone up to y = 39, nobody about. */
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
        game.player.pos.set(300.5f, FEET, 300.5f);
        game.entities.creatures.clear();
        game.entities.npcs.clear();
        game.entities.carcasses.clear();
        game.world.campPos = null;
        return game;
    }
}
