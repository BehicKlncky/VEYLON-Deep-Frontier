package com.veylon;

import com.veylon.ai.PanicIntent;
import com.veylon.ai.Quest;
import com.veylon.combat.WeaponDefinition;
import com.veylon.combat.WeaponRegistry;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BurnResidue;
import com.veylon.entity.Carcass;
import com.veylon.entity.CombustionSource;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureState;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Entity;
import com.veylon.entity.GameMode;
import com.veylon.entity.HumanCorpse;
import com.veylon.entity.Npc;
import com.veylon.entity.Npc.NpcState;
import com.veylon.entity.Ragdoll;
import com.veylon.gfx.model.FlameAnchors;
import com.veylon.item.ItemStack;
import com.veylon.item.ItemType;
import com.veylon.qa.RuntimeBudgetSnapshot;
import com.veylon.save.SaveSystem;
import com.veylon.settlement.CounterattackMission;
import com.veylon.settlement.HumanFaction;
import com.veylon.settlement.NpcArchetype;
import com.veylon.settlement.Settlement;
import com.veylon.settlement.SettlementType;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.util.Vec3i;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The whole feature end to end: every kind of living body the game registers,
 * set alight or blown apart by the player's own commands — a scrap bomb or a
 * fire bomb thrown with the attack command, a powder keg lit with the
 * interaction key — and then left to the frame's world step
 * ({@code Game.advanceWorld}, as {@code Game.frame} runs it while the game
 * simulates) and the player's death transition, with a save and a load where
 * that matters.
 *
 * <p>The matrix is built from the registries rather than a list kept here:
 * every {@code CreatureType}, every {@code NpcArchetype} as a settlement
 * resident on the side its settlements take, every
 * {@code Settlement.Alignment}, every {@code Npc.PartyKind}, the three legacy
 * people the camp code dispatches (camp member, wandering trader, raider) and
 * the Survival player. A species, archetype or party added later runs through
 * every case below and fails where its anatomy, flames, fire or panic were
 * never registered.
 *
 * <p>Isolated mechanics fixture: a flat stone arena (floor top at y = 40) over
 * chunks 18-21, clear noon, nobody else about, settlements keyed to far
 * regions so nothing generated interferes. A flight is stepped on the
 * projectile system alone, so a bird startled by the thrower cannot dodge the
 * bottle; everything after it runs whole frames.
 */
class AllLivingEndToEndTest {

    private static final double FRAME = 1.0 / 60.0;
    private static final float FEET = 40f;
    /** The middle of the 2 x 2 sealed pen a blast target stands in. */
    private static final float PEN_X = 331f, PEN_Z = 331f;
    /** Where a burning body stands when the bottle is thrown: open ground. */
    private static final float OPEN_X = 330.5f, OPEN_Z = 318.5f;
    private static final float STURDY = 1_000f;
    /** The regions this fixture keys its settlements to (friendly, neutral, hostile), far from anything generated. */
    private static final int[] FIXTURE_REGIONS = {-43, -44, -45};

    @TempDir
    Path saves;

    // ------------------------------------------------------------------
    // The registry-driven matrix
    // ------------------------------------------------------------------

    /** How the game dispatches a body's decisions and its death. */
    enum Route { ANIMAL, CAMP_MEMBER, WANDERING_TRADER, RAIDER, RESIDENT, WAR_PARTY, PLAYER }

    /** One kind of living body and how it is staged. */
    record Body(String label, Route route, CreatureType species, NpcArchetype archetype,
                Settlement.Alignment side, Npc.PartyKind party) {

        @Override
        public String toString() {
            return label;
        }

        boolean player() {
            return route == Route.PLAYER;
        }

        BodyFamily family() {
            return species != null ? BodyFamily.of(species) : BodyFamily.HUMANOID;
        }

        int pieces() {
            return family().anatomy().pieces.size();
        }

        boolean leavesCarcass() {
            return species != null && species.leavesCarcass();
        }

        /** Stands this body with its feet at {@code (x, y, z)} (a bird hovers there), dispatched on its route. */
        Entity stage(Game g, float x, float y, float z) {
            switch (route) {
                case ANIMAL -> {
                    return g.entities.spawnCreature(g.world, species, x, y, z);
                }
                case PLAYER -> {
                    g.player.pos.set(x, y, z);
                    g.player.vel.zero();
                    return g.player;
                }
                default -> {
                }
            }
            Npc n = g.entities.spawnNpc(g.world, label, x, y, z);
            switch (route) {
                case CAMP_MEMBER -> {
                    g.world.campPos = new Vec3i(300, 40, 300);
                    n.faction = g.faction;
                    n.campIndex = 1;
                }
                case WANDERING_TRADER -> {
                    n.isTrader = true;
                    n.leaveTimer = 600f;
                }
                case RAIDER -> {
                    g.world.campPos = new Vec3i(300, 40, 300);
                    n.raider = true;
                    n.leaveTimer = 600f;
                }
                case RESIDENT -> {
                    Settlement home = settlement(g, side);
                    Settlement.Resident record = new Settlement.Resident(label, archetype);
                    home.residents.add(record);
                    n.archetype = archetype;
                    n.settlementId = home.id;
                    n.residentIndex = home.residents.size() - 1;
                    record.live = n;
                }
                case WAR_PARTY -> {
                    Settlement fort = settlement(g, Settlement.Alignment.HOSTILE);
                    n.archetype = archetype;
                    n.warParty = true;
                    n.partyKind = party;
                    n.partyMissionId = "matrix-" + party.name();
                    n.partyMemberId = n.partyMissionId + ":0";
                    n.partyFactionId = fort.factionId;
                    n.originSettlementId = fort.id;
                    n.partyMission = Npc.PartyMission.OUTBOUND;
                    n.partyDestination.set(350.5f, FEET, 350.5f);
                    if (party == Npc.PartyKind.COUNTERATTACK) {
                        // A counterattacker needs its mission (an orphan is removed); the route is long enough
                        // that the emptied mission does not arrive and resolve inside the test.
                        Settlement outpost = settlement(g, Settlement.Alignment.FRIENDLY);
                        CounterattackMission mission = new CounterattackMission(n.partyMissionId, fort.id,
                                outpost.id, fort.factionId, new Vec3i(300, 40, 300), new Vec3i(700, 40, 700), 1L, 1);
                        mission.phase = CounterattackMission.Phase.OUTBOUND;
                        g.settlementManager.counterattacks.missions.put(mission.id, mission);
                        n.partyTargetSettlementId = outpost.id;
                    }
                }
                default -> throw new AssertionError(route);
            }
            if (n.archetype != null) {
                n.maxHealth = n.archetype.maxHealth;
                n.health = n.maxHealth;
            }
            return n;
        }
    }

    /**
     * The side a settlement of this archetype takes in the generated world:
     * headhunters, scavengers and the captives they hold in hostile forts,
     * settlers alternating between friendly and neutral villages.
     */
    static Settlement.Alignment sideOf(NpcArchetype a) {
        if (a == NpcArchetype.CAPTIVE || a.hostileArchetype()) {
            return Settlement.Alignment.HOSTILE;
        }
        return a.ordinal() % 2 == 0 ? Settlement.Alignment.FRIENDLY : Settlement.Alignment.NEUTRAL;
    }

    /** The archetype a party of this kind is drawn from: headhunters. */
    static NpcArchetype partyArchetype(Npc.PartyKind kind) {
        return switch (kind) {
            case PATROL -> NpcArchetype.HUNTER;
            case BOUNTY_HUNTER -> NpcArchetype.TRACKER;
            case COUNTERATTACK -> NpcArchetype.BRUTE;
        };
    }

    static Stream<Body> everyLivingBody() {
        List<Body> bodies = new ArrayList<>();
        for (CreatureType type : CreatureType.values()) {
            bodies.add(new Body(type.name(), Route.ANIMAL, type, null, null, null));
        }
        bodies.add(new Body("camp member", Route.CAMP_MEMBER, null, null, null, null));
        bodies.add(new Body("wandering trader", Route.WANDERING_TRADER, null, null, null, null));
        bodies.add(new Body("raider", Route.RAIDER, null, null, null, null));
        for (NpcArchetype a : NpcArchetype.values()) {
            Settlement.Alignment side = sideOf(a);
            bodies.add(new Body(a.name() + " resident (" + side.name().toLowerCase(java.util.Locale.ROOT) + ")",
                    Route.RESIDENT, null, a, side, null));
        }
        for (Npc.PartyKind kind : Npc.PartyKind.values()) {
            bodies.add(new Body(kind.name() + " war party", Route.WAR_PARTY, null, partyArchetype(kind),
                    Settlement.Alignment.HOSTILE, kind));
        }
        bodies.add(new Body("Survival player", Route.PLAYER, null, null, null, null));
        return bodies.stream();
    }

    /**
     * The matrix reaches every registered species, archetype, side, party and
     * dispatch route, and every body family it produces has its anatomy and
     * its flame anchors; staging really puts each body on its route.
     */
    @Test
    void theMatrixReachesEveryRegisteredKindOfBodyAndEachHasItsAnatomyAndFlames() {
        List<Body> bodies = everyLivingBody().toList();
        Set<CreatureType> species = EnumSet.noneOf(CreatureType.class);
        Set<NpcArchetype> archetypes = EnumSet.noneOf(NpcArchetype.class);
        Set<Settlement.Alignment> sides = EnumSet.noneOf(Settlement.Alignment.class);
        Set<Npc.PartyKind> parties = EnumSet.noneOf(Npc.PartyKind.class);
        Set<Route> routes = EnumSet.noneOf(Route.class);
        Set<BodyFamily> families = EnumSet.noneOf(BodyFamily.class);
        for (Body b : bodies) {
            routes.add(b.route());
            families.add(b.family());
            if (b.species() != null) {
                species.add(b.species());
            }
            if (b.route() == Route.RESIDENT) {
                archetypes.add(b.archetype());
                sides.add(b.side());
            }
            if (b.party() != null) {
                parties.add(b.party());
            }
        }
        assertEquals(EnumSet.allOf(CreatureType.class), species);
        assertEquals(EnumSet.allOf(NpcArchetype.class), archetypes, "every archetype lives in a settlement");
        assertEquals(EnumSet.allOf(Settlement.Alignment.class), sides, "friendly, neutral and hostile residents");
        assertEquals(EnumSet.allOf(Npc.PartyKind.class), parties);
        assertEquals(EnumSet.allOf(Route.class), routes);
        assertEquals(EnumSet.allOf(BodyFamily.class), families, "every body family is exercised");
        for (BodyFamily family : families) {
            assertTrue(family.anatomy().pieces.size() >= 2, family + " comes apart into pieces");
            assertTrue(FlameAnchors.of(family).count > 0, family + " has somewhere for its flames to stand");
        }

        Game g = arena();
        for (Body b : bodies) {
            Entity e = b.stage(g, OPEN_X, FEET, OPEN_Z);
            switch (b.route()) {
                case ANIMAL -> assertSame(b.species(), ((Creature) e).type);
                case PLAYER -> assertSame(g.player, e);
                case CAMP_MEMBER -> assertTrue(((Npc) e).faction != null && !((Npc) e).settled());
                case WANDERING_TRADER -> assertTrue(((Npc) e).isTrader && !((Npc) e).settled());
                case RAIDER -> assertTrue(((Npc) e).raider && ((Npc) e).hostileToPlayer());
                case RESIDENT -> {
                    Npc n = (Npc) e;
                    assertTrue(n.settled(), b + " is a resident");
                    Settlement home = g.world.settlements.get(n.settlementId);
                    assertSame(b.side(), home.alignment);
                    assertEquals(b.archetype() != NpcArchetype.CAPTIVE && b.side() == Settlement.Alignment.HOSTILE,
                            n.hostileToPlayer(), b + ": its settlement's side, a captive never hostile");
                }
                case WAR_PARTY -> assertTrue(((Npc) e).warParty && ((Npc) e).hostileToPlayer());
            }
        }
    }

    /**
     * A scrap bomb thrown with the attack command onto the roof of a sealed
     * pen kills whatever body is inside, whatever it was doing for the 2.4 s
     * of the fuse, and the one death that follows blows it apart at its own
     * joints: its family's pieces once, one harvest record for an animal that
     * leaves a carcass, never a ragdoll, and one death's consequences that
     * the pieces landing and a slow tick never repeat. The Survival player,
     * throwing it at their own feet, comes apart at the death transition.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void aScrapBombThrownWithTheAttackCommandBlowsEveryBodyApartOnce(Body b) {
        Game g = arena();
        Entity e;
        hold(g, ItemType.SCRAP_BOMB);
        if (b.player()) {
            e = b.stage(g, 320.5f, FEET, PEN_Z);
            look(g);
            assertTrue(g.updateThrownWeaponCommand(2f, true, new Vector3f(0.05f, -1f, 0f)), "thrown at their feet");
        } else {
            pen(g);
            e = b.stage(g, PEN_X, FEET + (b.species() != null && b.species().flying ? 0.4f : 0f), PEN_Z);
            g.player.pos.set(323.5f, FEET, PEN_Z);
            look(g);
            assertTrue(g.updateThrownWeaponCommand(2f, true,
                    aim(g, "scrap_bomb", PEN_X, FEET + 3.3f, PEN_Z)), "thrown onto the pen's roof");
        }
        float before = g.player.health;

        frames(g, 3.0);
        assertTrue(e.dead && e.health <= 0f, b + " died a real death on the fuse");
        assertEquals(b.pieces(), g.fragments.totalSpawned, b + " came apart into its family's pieces, once");
        for (BodyFragment f : allPieces(g)) {
            assertSame(b.family(), f.definition.family, b + " comes apart at its own joints");
        }
        assertEquals(0L, g.ragdolls.totalSpawned, b + " never also fell whole");
        assertTrue(g.entities.corpses.isEmpty());
        assertEquals(b.leavesCarcass() ? 1 : 0, g.entities.carcasses.size(), b + ": one harvest record at most");
        if (b.leavesCarcass()) {
            Carcass record = g.entities.carcasses.getFirst();
            assertTrue(record.fragmented(), "never drawn whole");
            assertEquals(b.species().meatYield, record.meatLeft);
        }
        if (b.player()) {
            assertSame(Game.AppState.DEATH, g.appState, "the ordinary death screen");
            assertFalse(e.dismemberOnDeath, "the transition spent the record");
        } else {
            assertTrue(e.dismemberOnDeath, b + " carries the blast that killed it");
            assertEquals(2.6f, e.blastStrength, 0f);
            assertTrue(e.lastHitByPlayer, b + ": the thrower's kill");
            assertFalse(g.entities.npcs.contains(e) || g.entities.creatures.contains(e), "gone from the living");
            assertEquals(before, g.player.health, 0f, "the thrower stood clear");
            assertPaidOnce(g, b, e instanceof Npc n ? n : null);
        }

        Ledger atDeath = Ledger.of(g);
        frames(g, 10.5);
        assertEquals(b.pieces(), g.fragments.totalSpawned, b + ": landing and a slow tick spawn nothing");
        assertEquals(atDeath, Ledger.of(g), b + ": nothing is awarded or charged twice");
        assertTrue(RuntimeBudgetSnapshot.capture(g).withinHardLimits());
    }

    /**
     * A fire bomb thrown with the attack command sets every body alight — on
     * the body, or at the Survival player's feet — for the thrower. Every
     * person and animal then drops what it was doing and flees on its own
     * legs or wings; the player has no panic to run. Burning to death, each
     * dies whole, once, for the thrower: a person or an animal falls as one
     * ragdoll carrying its fire onto the one corpse or carcass it leaves, and
     * nothing the death awarded or cost is paid again over the next ten
     * seconds. The player's burn death is the ordinary death screen with no
     * remains.
     */
    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void aFireBombThrownWithTheAttackCommandSetsEveryBodyAlightAndItsBurnDeathIsPaidOnce(Body b) {
        Game g = arena();
        Entity e;
        hold(g, ItemType.FIRE_BOMB);
        if (b.player()) {
            e = b.stage(g, 320.5f, FEET, OPEN_Z);
            look(g);
            assertTrue(g.updateThrownWeaponCommand(2f, true, new Vector3f(0.35f, -1f, 0f)), "thrown at their feet");
        } else {
            boolean flying = b.species() != null && b.species().flying;
            e = sturdy(b.stage(g, OPEN_X, flying ? FEET + 4f : FEET, OPEN_Z));
            // Clear of the spill round a body on the ground; closer to a small bird, which spills nothing.
            g.player.pos.set(OPEN_X - (flying ? 2.5f : 5f), FEET, OPEN_Z);
            look(g);
            assertTrue(g.updateThrownWeaponCommand(2f, true,
                    aim(g, "fire_bomb", OPEN_X, e.pos.y + e.height * 0.5f, OPEN_Z)), "thrown at the body");
        }
        fly(g);
        if (!b.player()) {
            assertTrue(g.combustion.isBurning(e), b + " is alight the moment the bottle breaks on it");
            assertSame(CombustionSource.DIRECT_HIT, e.combustion.owner());
        }
        frames(g, 0.1);
        assertTrue(g.combustion.isBurning(e), b + " is alight through the bottle");
        assertTrue(e.combustion.ownerByPlayer(), b + ": the thrower's fire");

        // Flight, not a fall: the body runs from where it caught.
        Vector3f caught = new Vector3f(e.pos);
        frames(g, 1.5);
        assertTrue(g.combustion.isBurning(e), b + " burns on");
        if (b.player()) {
            assertTrue(e.combustion.inContact(), "the player stays where they stand, in the pool");
        } else {
            PanicIntent panic = e instanceof Npc n ? n.panic : ((Creature) e).panic;
            assertTrue(panic.active(), b + " panics");
            assertTrue(e instanceof Npc n ? n.state == NpcState.FLEE : ((Creature) e).state == CreatureState.FLEE);
            assertTrue(e.pos.distance(caught) > 1.5f, b + " fled: " + e.pos.distance(caught));
            assertEquals(0, g.projectiles.liveCount(), b + " neither shoots nor throws while alight");
            assertFalse(g.player.combustion.hasExposure(), "the thrower stood clear of the spill");
        }

        // Weakened, it dies of the burn itself.
        e.health = Math.min(e.health, 1f);
        for (int i = 0; i < 6 * 60 && !takenOut(g, e); i++) {
            frames(g, FRAME);
        }
        assertTrue(e.dead && e.health <= 0f, b + " burned to death");
        assertFalse(e.dismemberOnDeath, b + ": a burn death is never a blast death");
        assertEquals(0L, g.fragments.totalSpawned, b + " falls whole");
        if (b.player()) {
            assertSame(Game.AppState.DEATH, g.appState);
            assertEquals(0L, g.ragdolls.totalSpawned, "no player body exists to fall");
            assertEquals(0, g.burnResidues.trackedCount(), "and none carries a fire");
            return;
        }
        assertTrue(e.lastHitByPlayer, b + ": the thrower's kill, long after the bottle broke");
        assertEquals(1L, g.ragdolls.totalSpawned, b + " falls as one body");
        Ragdoll body = g.ragdolls.live.getFirst();
        BurnResidue fire = body.burn;
        assertNotNull(fire, b + " falls still burning");
        assertTrue(fire.flame() > 0f);
        assertPaidOnce(g, b, e instanceof Npc n ? n : null);

        Ledger atDeath = Ledger.of(g);
        frames(g, 10.5);
        assertEquals(atDeath, Ledger.of(g), b + ": nothing is awarded or charged twice");
        assertEquals(1L, g.ragdolls.totalSpawned);
        assertEquals(0L, g.fragments.totalSpawned);
        assertEquals(b.leavesCarcass() ? 1 : 0, g.entities.carcasses.size());
        assertEquals(e instanceof Npc ? 1 : 0, g.entities.corpses.size());
        if (e instanceof Npc) {
            HumanCorpse corpse = g.entities.corpses.getFirst();
            assertSame(fire, corpse.burn, "the corpse carries the same fire the falling body did");
        } else if (b.leavesCarcass()) {
            Carcass carcass = g.entities.carcasses.getFirst();
            assertFalse(carcass.fragmented());
            assertSame(fire, carcass.burn, "the carcass carries the same fire the falling body did");
        }
        assertEquals(0f, fire.flame(), 0f, "the remains' flames have died down");
        assertTrue(fire.scorch() > 0f, "and left the body charred");
    }

    // ------------------------------------------------------------------
    // Everything at once, in one room
    // ------------------------------------------------------------------

    /**
     * A powder keg lit with the interaction key sets off a second one in a
     * sealed storeroom. A burning thornhorn, a hare and a bird inside come
     * apart once each (the thornhorn's pieces sharing its one fire); a
     * villager the fire killed before the kegs went off stays one whole body
     * with no blast record; a raider who left the world just before the blast
     * leaves no body at all. Nothing is paid twice, and a save and a load
     * restore every piece and harvest record once, with no fire.
     */
    @Test
    void aKegLitWithTheInteractionKeyChainsThroughABurningRoomAndEachBodyDiesOnce() {
        Game g = arena();
        g.world.campPos = new Vec3i(300, 40, 300);
        // A storeroom: nine by three inside, two high, roofed, the kegs three apart.
        room(g, 316, 324, 319, 321);
        Vec3i kegA = new Vec3i(317, 40, 320);
        Vec3i kegB = new Vec3i(320, 40, 320);
        g.world.setBlock(kegA.x(), kegA.y(), kegA.z(), BlockType.POWDER_KEG, false);
        g.world.setBlock(kegB.x(), kegB.y(), kegB.z(), BlockType.POWDER_KEG, false);
        Creature thornhorn = sturdy(g.entities.spawnCreature(g.world, CreatureType.THORNHORN, 322.5f, FEET, 320.5f));
        Creature hare = g.entities.spawnCreature(g.world, CreatureType.HARE, 318.5f, FEET, 319.5f);
        Creature bird = g.entities.spawnCreature(g.world, CreatureType.BIRD, 316.5f, FEET + 0.5f, 321.5f);
        // A villager of no settlement: hand-made settlements have no plan a load could restore.
        Npc villager = g.entities.spawnNpc(g.world, "Villager", 324.5f, FEET, 319.5f);
        Npc raider = g.entities.spawnNpc(g.world, "Raider", 319.5f, FEET, 321.5f);
        raider.raider = true;
        raider.leaveTimer = -3.1f; // leaving already; gone about a tenth of a second before the blast
        assertTrue(g.combustion.ignite(g, thornhorn, CombustionSource.DIRECT_HIT, 1f, true, 7,
                thornhorn.pos.x, thornhorn.pos.y + 0.6f, thornhorn.pos.z));
        assertTrue(g.combustion.ignite(g, villager, CombustionSource.DIRECT_HIT, 1f, true, 8,
                villager.pos.x, villager.pos.y + 0.8f, villager.pos.z));
        villager.health = 2f;
        g.player.pos.set(305.5f, FEET, 320.5f);
        look(g);
        int meat = g.player.inventory.count(ItemType.RAW_MEAT);

        assertTrue(g.interactWithBlockAt(kegA), "the interaction key lights the fuse");
        assertEquals(1, lines(g, "Fuse lit! Five seconds"), "one fuse lit");
        frames(g, 5.5);

        assertEquals(BlockType.AIR, g.world.getBlock(kegA.x(), kegA.y(), kegA.z()));
        assertEquals(BlockType.AIR, g.world.getBlock(kegB.x(), kegB.y(), kegB.z()), "the first keg set off the second");
        assertTrue(villager.dead && !villager.dismemberOnDeath, "the fire killed the villager first: no blast record");
        assertEquals(1L, g.ragdolls.totalSpawned, "and it lies whole");
        assertTrue(raider.dead && raider.health > 0f && !raider.dismemberOnDeath, "the raider left without dying");
        assertFalse(raider.combustion.burning());
        assertEquals(0, lines(g, "looted the fallen"), "and is nobody's kill");
        for (Creature c : List.of(thornhorn, hare, bird)) {
            assertTrue(c.dead && c.dismemberOnDeath && c.lastHitByPlayer, c.type + " died in the lit keg's blast");
        }
        int animalPieces = BodyFamily.of(CreatureType.THORNHORN).anatomy().pieces.size()
                + BodyFamily.of(CreatureType.HARE).anatomy().pieces.size()
                + BodyFamily.of(CreatureType.BIRD).anatomy().pieces.size();
        assertEquals(animalPieces, g.fragments.totalSpawned, "three animals, their own pieces, once each");
        assertEquals(meat + 1, g.player.inventory.count(ItemType.RAW_MEAT), "the bird's meat, once");
        assertEquals(2, g.entities.carcasses.size(), "the thornhorn's and the hare's records");

        // The thornhorn's pieces share one fire, split by mass.
        Map<BurnResidue, Float> shares = new IdentityHashMap<>();
        for (BodyFragment f : allPieces(g)) {
            if (f.definition.family == BodyFamily.of(CreatureType.THORNHORN)) {
                assertNotNull(f.burn, "every piece of the burning body carries its fire");
                shares.merge(f.burn, f.burnShare, Float::sum);
            } else {
                assertNull(f.burn, f.definition + " was not burning");
            }
        }
        assertEquals(1, shares.size(), "one fire for the whole body");
        assertEquals(1f, shares.values().iterator().next(), 1e-4f);

        Ledger atBlast = Ledger.of(g);
        frames(g, 12.0);
        assertEquals(atBlast, Ledger.of(g), "nothing is paid twice as the pieces land and the slow tick runs");
        assertEquals(0, g.fragments.liveCount(), "precondition: every piece has landed");
        Map<BodyFamily, Integer> landed = countByFamily(g.fragments.settled);

        Path file = saves.resolve("room.dat");
        assertTrue(SaveSystem.save(g, file));
        Game loaded = new Game();
        assertTrue(SaveSystem.load(loaded, file));
        assertEquals(landed, countByFamily(loaded.fragments.settled), "every piece once");
        assertEquals(2, loaded.entities.carcasses.size());
        for (Carcass c : loaded.entities.carcasses) {
            assertTrue(c.fragmented() && c.remains != null, c.type + ": its record tied to its torso again");
            assertNull(c.burn);
        }
        for (BodyFragment f : loaded.fragments.settled) {
            assertNull(f.burn, "a load brings no fire back");
        }
        assertEquals(0, loaded.burnResidues.trackedCount());
        assertEquals(0, loaded.combustion.burningBodies(loaded));
    }

    /**
     * The camp's hunt request counts a predator the player kills near camp
     * once, whether the player's fire burns it to death well after the bottle
     * broke or the player's keg blows it apart, and the camp's gratitude is
     * paid once either way.
     */
    @Test
    void aPredatorThePlayerKillsNearCampCountsForTheHuntOnceWhicheverWayItDies() {
        for (boolean byFire : new boolean[] {true, false}) {
            Game g = arena();
            // The wolf dies within the camp's reach however far it runs, but the keg breaks no camp property.
            g.world.campPos = byFire
                    ? new Vec3i((int) OPEN_X, 40, (int) OPEN_Z)
                    : new Vec3i((int) PEN_X - 15, 40, (int) PEN_Z);
            Quest hunt = new Quest(Quest.Type.HUNT_PREDATOR, null, 1, 600f, 5, null, 0, "Camp")
                    .bind("hunt-" + byFire, "camp", Quest.NO_SETTLEMENT, "camp");
            g.faction.quest = hunt;
            float trust = g.faction.trust;
            Creature wolf;
            if (byFire) {
                wolf = sturdy(g.entities.spawnCreature(g.world, CreatureType.WOLF, OPEN_X, FEET, OPEN_Z));
                wolf.hunger = 0f;
                g.player.pos.set(OPEN_X - 5f, FEET, OPEN_Z);
                look(g);
                hold(g, ItemType.FIRE_BOMB);
                assertTrue(g.updateThrownWeaponCommand(2f, true,
                        aim(g, "fire_bomb", OPEN_X, FEET + 0.5f, OPEN_Z)));
                fly(g);
                frames(g, 1.0);
                assertTrue(g.combustion.isBurning(wolf) && wolf.panic.active(), "precondition: alight and fleeing");
                wolf.health = 1f;
            } else {
                pen(g);
                Vec3i keg = new Vec3i((int) PEN_X - 1, 40, (int) PEN_Z - 1);
                g.world.setBlock(keg.x(), keg.y(), keg.z(), BlockType.POWDER_KEG, false);
                wolf = g.entities.spawnCreature(g.world, CreatureType.WOLF, PEN_X + 0.3f, FEET, PEN_Z + 0.3f);
                g.player.pos.set(318.5f, FEET, PEN_Z);
                look(g);
                assertTrue(g.interactWithBlockAt(keg));
            }
            for (int i = 0; i < 8 * 60 && g.entities.creatures.contains(wolf); i++) {
                frames(g, FRAME);
            }
            assertTrue(wolf.dead && wolf.lastHitByPlayer, "precondition: the player's kill");
            assertEquals(!byFire, wolf.dismemberOnDeath);
            assertEquals(1, hunt.progress, "the hunt counts it");
            assertEquals(1, lines(g, "Request updated"));
            assertEquals(1, lines(g, "The camp saw you slay a predator"));
            assertEquals(Math.min(100f, trust + 10f), g.faction.trust, 1e-4f, "the camp's gratitude, once");
            Ledger after = Ledger.of(g);
            frames(g, 10.5);
            assertEquals(after, Ledger.of(g), (byFire ? "fire" : "keg") + ": nothing counted again");
        }
    }

    /**
     * The Creative player stands on their own scrap bomb and in their own
     * burning liquid untouched — no damage, no fire, no remains, no death —
     * while the people and animals beside them die and burn as in Survival.
     */
    @Test
    void theCreativePlayerStandsUnhurtOnTheirOwnBombAndInTheirOwnFireWhileOthersDoNot() {
        Game g = arena(GameMode.CREATIVE);
        assertTrue(g.player.abilities.invulnerable(), "precondition: Creative");
        // A shed round the player and two neighbours, so nobody wanders out of the blast.
        room(g, 319, 322, 319, 322);
        g.player.pos.set(320.5f, FEET, 320.5f);
        Creature deer = g.entities.spawnCreature(g.world, CreatureType.DEER, 322.3f, FEET, 320.5f);
        Npc camper = g.entities.spawnNpc(g.world, "Camper", 320.5f, FEET, 322.3f);
        look(g);
        float health = g.player.health;

        hold(g, ItemType.SCRAP_BOMB);
        assertTrue(g.updateThrownWeaponCommand(2f, true, new Vector3f(0.05f, -1f, 0f)));
        frames(g, 3.0);
        assertTrue(deer.dead && deer.dismemberOnDeath, "the deer beside the player dies and comes apart");
        assertTrue(camper.dead && camper.dismemberOnDeath, "and so does the camper");
        assertFalse(g.player.dead);
        assertEquals(health, g.player.health, 0f, "the Creative player is untouched");
        assertSame(Game.AppState.PLAYING, g.appState);
        assertEquals(BodyFamily.of(CreatureType.DEER).anatomy().pieces.size()
                + BodyFamily.HUMANOID.anatomy().pieces.size(), g.fragments.totalSpawned, "and leaves no remains");

        // A second shed on unbroken ground: the bomb left a crater under the first.
        room(g, 299, 302, 329, 332);
        g.player.pos.set(300.5f, FEET, 330.5f);
        look(g);
        Npc second = sturdy(g.entities.spawnNpc(g.world, "Second", 300.5f, FEET, 331.4f));
        hold(g, ItemType.FIRE_BOMB);
        assertTrue(g.updateThrownWeaponCommand(2f, true, new Vector3f(0.2f, -1f, 0.3f)));
        fly(g);
        assertTrue(g.liquidFire.patches().stream().anyMatch(p -> p.x == 300 && p.y == 40 && p.z == 330),
                "precondition: the liquid burns in the player's own cell");
        frames(g, 2.0);
        assertTrue(g.combustion.isBurning(second), "the person beside the player catches");
        assertFalse(g.player.combustion.burning() || g.player.combustion.hasExposure(),
                "the Creative player never does");
        assertEquals(health, g.player.health, 0f);
    }

    // ------------------------------------------------------------------
    // Every effect at its ceiling at once
    // ------------------------------------------------------------------

    /**
     * Every effect at its ceiling at once, stepped by whole frames in a storm:
     * forty people (the settlement cap: 32 residents of every archetype, 8
     * legacy) and 35 animals of every species all burning and fleeing in a
     * half-roofed yard, 220 burning blocks and eight bottles' worth of burning
     * liquid under the roof, eleven wolves blown apart (past the live-piece cap) and
     * sixteen hares burning to death (past the ragdoll cap). Every hard limit
     * holds on every frame and the particle system reaches its splash ceiling,
     * yet every body, piece and flame is exactly what it is in the same world
     * stepped with no particles at all: presentation limits never touch the
     * simulation. A new world afterwards keeps none of it, and the one
     * presentation memory that holds bodies (the sounds already played) is
     * empty.
     */
    @Test
    void everyEffectAtItsCeilingAtOnceKeepsEveryLimitAndLeavesTheSimulationToItself() throws Exception {
        Game full = mixedLoad(1f);
        Game bare = mixedLoad(0f);
        assertEquals(com.veylon.simulation.FireSystem.MAX_ACTIVE_FIRES, full.fire.count(),
                "precondition: the block fire cap is full");
        assertEquals(com.veylon.simulation.LiquidFireConstants.MAX_PATCHES, full.liquidFire.count(),
                "precondition: the liquid fire cap is full");
        full.fastTick(com.veylon.simulation.SimulationScheduler.FAST_DT);
        bare.fastTick(com.veylon.simulation.SimulationScheduler.FAST_DT);
        assertTrue(full.fragments.totalSpawned > com.veylon.entity.BodyFragmentConstants.MAX_LIVE_FRAGMENTS,
                "precondition: eleven wolves make more pieces than may fly at once");
        assertEquals(com.veylon.entity.BodyFragmentConstants.MAX_LIVE_FRAGMENTS, full.fragments.liveCount(),
                "and the live-piece cap holds");

        com.sun.management.ThreadMXBean bean =
                (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        int peakParticles = 0;
        int peakPanicking = 0;
        int peakBurning = 0;
        long bytes = 0;
        long nanos = 0;
        int frames = 10 * 60;
        for (int frame = 1; frame <= frames; frame++) {
            long allocated = bean.getThreadAllocatedBytes(thread);
            long started = System.nanoTime();
            full.advanceWorld(FRAME);
            nanos += System.nanoTime() - started;
            bytes += bean.getThreadAllocatedBytes(thread) - allocated;
            bare.advanceWorld(FRAME);
            RuntimeBudgetSnapshot snapshot = RuntimeBudgetSnapshot.capture(full);
            assertTrue(snapshot.withinHardLimits(), "frame " + frame + ": " + snapshot.occupancySummary()
                    + " limits " + RuntimeBudgetSnapshot.hardLimitSummary());
            peakParticles = Math.max(peakParticles, full.particles.count);
            peakPanicking = Math.max(peakPanicking, snapshot.panickingBodies());
            peakBurning = Math.max(peakBurning, snapshot.burningBodies());
        }
        System.out.printf(java.util.Locale.ROOT,
                "mixed full load: %.3f ms and %d bytes per frame over %d frames; peaks: %d particles, "
                        + "%d burning, %d panicking; ragdolls %d spawned%n",
                nanos / 1e6 / frames, bytes / frames, frames, peakParticles, peakBurning, peakPanicking,
                full.ragdolls.totalSpawned);
        assertTrue(peakParticles >= com.veylon.engine.ParticleSystem.SPLASH_LIMIT,
                "precondition: presentation ran into its ceiling: " + peakParticles);
        assertEquals(75, peakBurning, "every living person and animal burned");
        assertTrue(peakPanicking >= 70, "and fled: " + peakPanicking);
        assertTrue(full.ragdolls.totalSpawned >= 16, "precondition: the sixteen hares burned to death");
        assertEquals(0, bare.particles.count, "precondition: the twin drew nothing");

        // The same simulation, with presentation full or absent.
        assertEquals(bare.entities.npcs.size(), full.entities.npcs.size());
        assertEquals(bare.entities.creatures.size(), full.entities.creatures.size());
        for (int i = 0; i < full.entities.npcs.size(); i++) {
            assertSameBody(bare.entities.npcs.get(i), full.entities.npcs.get(i));
            assertEquals(bare.entities.npcs.get(i).panic.goals(), full.entities.npcs.get(i).panic.goals());
        }
        for (int i = 0; i < full.entities.creatures.size(); i++) {
            assertSameBody(bare.entities.creatures.get(i), full.entities.creatures.get(i));
        }
        assertEquals(bare.ragdolls.totalSpawned, full.ragdolls.totalSpawned);
        assertEquals(bare.fragments.settledCount(), full.fragments.settledCount());
        for (int i = 0; i < full.fragments.settled.size(); i++) {
            assertEquals(bare.fragments.settled.get(i).pos, full.fragments.settled.get(i).pos, "piece " + i);
        }
        assertEquals(bare.fire.count(), full.fire.count());
        assertEquals(bare.liquidFire.count(), full.liquidFire.count());
        assertEquals(bare.entities.nextPanicFloat(), full.entities.nextPanicFloat(), 0f, "the panic stream in step");

        // A new world keeps nothing of it.
        full.newWorld(778L, true);
        assertEquals(0, full.fragments.liveCount() + full.fragments.settledCount());
        assertEquals(0, full.ragdolls.liveCount());
        assertEquals(0, full.burnResidues.trackedCount());
        assertEquals(0, full.combustion.burningBodies(full));
        assertTrue(full.entities.corpses.isEmpty());
        java.lang.reflect.Field heard = BodyFireEffects.class.getDeclaredField("heardBody");
        heard.setAccessible(true);
        for (Object body : (Object[]) heard.get(full.ambience.bodyFire)) {
            assertNull(body, "no sound memory holds a body from the last world");
        }
    }

    private static void assertSameBody(Entity expected, Entity actual) {
        assertEquals(expected.pos, actual.pos, actual + " stands where it would unseen");
        assertEquals(expected.health, actual.health, 0f, actual + " is as hurt");
        assertEquals(expected.dead, actual.dead);
        assertEquals(expected.combustion.burning(), actual.combustion.burning());
        assertEquals(expected.combustion.fuel(), actual.combustion.fuel(), 0f);
        assertEquals(expected.combustion.scorch(), actual.combustion.scorch(), 0f);
    }

    /** The full load: see {@link #everyEffectAtItsCeilingAtOnceKeepsEveryLimitAndLeavesTheSimulationToItself}. */
    static Game mixedLoad(float particleDensity) {
        Game g = arena();
        g.particles.density = particleDensity;
        g.weather.current = Weather.STORM;
        g.weather.next = Weather.STORM;
        g.player.pos.set(349.5f, FEET, 290.5f);
        // The west of the yard under a roof five blocks up; the storm falls on the east.
        fill(g, 288, 319, 45, 45, 288, 351, BlockType.STONE);
        for (int x = 290; x < 312; x++) {
            for (int z = 290; z < 300; z++) {
                g.world.setBlock(x, 40, z, BlockType.PLANK, false);
                g.fire.ignite(g, x, 40, z);
            }
        }
        WeaponDefinition bottle = WeaponRegistry.byId("fire_bomb");
        for (int i = 0; i < 8; i++) {
            // Two rows of four, far enough apart that no two spills share a cell.
            g.projectiles.fire(g, g.player, true, 292.5f + (i % 4) * 7f, 42f, 339.5f + (i / 4) * 7f, 0, -1, 0,
                    bottle, null);
        }
        for (int i = 0; i < 100 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.01f);
        }

        List<Entity> crowd = new ArrayList<>();
        Settlement village = settlement(g, Settlement.Alignment.FRIENDLY);
        NpcArchetype[] archetypes = NpcArchetype.values();
        for (int i = 0; i < com.veylon.settlement.SettlementManager.MAX_ACTIVE_NPCS; i++) {
            float x = 300.5f + (i % 10) * 4f, z = 305.5f + (i / 10) * 4f;
            Npc n = g.entities.spawnNpc(g.world, "Crowd " + i, x, FEET, z);
            if (i < com.veylon.settlement.SettlementManager.MAX_ACTIVE_RESIDENTS) {
                NpcArchetype a = archetypes[i % archetypes.length];
                Settlement.Resident record = new Settlement.Resident(n.name, a);
                village.residents.add(record);
                n.archetype = a;
                n.settlementId = village.id;
                n.residentIndex = village.residents.size() - 1;
                record.live = n;
            }
            crowd.add(n);
        }
        CreatureType[] species = CreatureType.values();
        for (int i = 0; i < 35; i++) {
            CreatureType type = species[i % species.length];
            float x = 300.5f + (i % 12) * 3.5f, z = 322.5f + (i / 12) * 4f;
            crowd.add(g.entities.spawnCreature(g.world, type, x, type.flying ? FEET + 2f : FEET, z));
        }
        int bottleId = 1000;
        for (Entity e : crowd) {
            e.maxHealth = e.health = STURDY;
            assertTrue(g.combustion.ignite(g, e, CombustionSource.DIRECT_HIT, 1f, true, bottleId++,
                    e.pos.x, e.pos.y + e.height * 0.5f, e.pos.z));
        }
        for (int i = 0; i < 16; i++) {
            Creature hare = g.entities.spawnCreature(g.world, CreatureType.HARE, 292.5f + i * 2f, FEET, 336.5f);
            hare.health = 0.3f; // the first contact tick kills it
            g.combustion.ignite(g, hare, CombustionSource.DIRECT_HIT, 1f, true, bottleId++,
                    hare.pos.x, hare.pos.y + 0.2f, hare.pos.z);
        }
        for (int i = 0; i < 11; i++) {
            Creature wolf = g.entities.spawnCreature(g.world, CreatureType.WOLF, 291.5f + i * 5.5f, FEET, 349.5f);
            g.explosions.explode(g, wolf.pos.x + 1f, wolf.pos.y + 0.5f, wolf.pos.z, 2.6f, 14f, 0f, true, true);
        }
        return g;
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    /** Everything a death may award or cost, so a test can show it is paid exactly once. */
    record Ledger(List<String> log, Map<ItemType, Integer> items, float trust, Map<String, Float> reputation,
                  Map<String, Float> bounty, List<String> settlements, int questProgress) {

        /**
         * The ledger now. Settlements the world itself registers as the
         * player's surroundings load, and the discovery lines they log, are
         * the world's doing, not a death's: only this fixture's own
         * settlements are compared.
         */
        static Ledger of(Game g) {
            Map<ItemType, Integer> items = new EnumMap<>(ItemType.class);
            for (ItemType t : ItemType.values()) {
                int n = g.player.inventory.count(t);
                if (n > 0) {
                    items.put(t, n);
                }
            }
            List<String> settlements = new ArrayList<>();
            for (Settlement s : new TreeMap<>(g.world.settlements).values()) {
                if (Arrays.stream(FIXTURE_REGIONS).noneMatch(r -> s.id == Settlement.packId(r, r))) {
                    continue;
                }
                settlements.add(s.id + " local=" + s.localReputation + " morale=" + s.morale + " side="
                        + s.alignment + " cleared=" + s.cleared + " alive=" + s.residents.stream()
                        .map(r -> r.alive ? "1" : "0").collect(Collectors.joining()));
            }
            List<String> log = g.eventLog.all().stream().filter(line -> !line.contains("DISCOVERED:")).toList();
            return new Ledger(log, items, g.faction.trust,
                    new TreeMap<>(g.world.factionReputation), new TreeMap<>(g.world.factionBounty),
                    settlements, g.faction.quest == null ? -1 : g.faction.quest.progress);
        }
    }

    /**
     * The log lines a death on this route writes when the player's hand
     * killed it — credit, loot, a crime, a death notice — each at most once
     * and at least one of them. Which ones depends on the side the body's
     * settlement is on at that moment (a neutral village a killing turns
     * hostile also yields loot), so every candidate is bounded.
     */
    private static void assertPaidOnce(Game g, Body b, Npc n) {
        List<String> candidates = switch (b.route()) {
            case ANIMAL -> List.of(b.species() == CreatureType.BIRD
                    ? "Hunted a " + b.species().displayName
                    : "The " + b.species().displayName + " is down.");
            case CAMP_MEMBER -> List.of("You killed " + n.name + "!", n.name + " has died.");
            case WANDERING_TRADER -> List.of(n.name + " has died.");
            case RAIDER -> List.of("You looted the fallen scavenger.");
            case RESIDENT -> List.of("You killed " + n.name + "!", "You killed a protected captive",
                    "You search the fallen ", n.name + " has died.", "leader has fallen");
            case WAR_PARTY -> List.of("You search the fallen ");
            case PLAYER -> throw new AssertionError("the player's death is the death screen");
        };
        int total = 0;
        for (String line : candidates) {
            int count = lines(g, line);
            assertTrue(count <= 1, b + ": \"" + line + "\" " + count + " times in " + g.eventLog.all());
            total += count;
        }
        assertTrue(total >= 1, b + ": its death was paid for: " + g.eventLog.all());
    }

    private static int lines(Game g, String fragment) {
        int count = 0;
        for (String line : g.eventLog.all()) {
            if (line.contains(fragment)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Frames as {@code Game.frame} runs them, without input or drawing: the
     * world step while it simulates, the camera on the eye, the death
     * transition.
     */
    private static void frames(Game g, double seconds) {
        int n = Math.max(1, (int) Math.round(seconds / FRAME));
        for (int i = 0; i < n; i++) {
            if (g.simulates()) {
                g.advanceWorld(FRAME);
            }
            look(g);
            g.enterDeathIfDue();
        }
    }

    /** Steps the projectile system alone until nothing is flying: the bottle's flight with the world held still. */
    private static void fly(Game g) {
        for (int i = 0; i < 400 && g.projectiles.liveCount() > 0; i++) {
            g.projectiles.update(g, 0.01f);
        }
        assertEquals(0, g.projectiles.liveCount(), "precondition: the bottle broke");
    }

    /** True once a body is out of the world: the living lists, or the player's death screen. */
    private static boolean takenOut(Game g, Entity e) {
        if (e == g.player) {
            return g.appState == Game.AppState.DEATH;
        }
        return !g.entities.npcs.contains(e) && !g.entities.creatures.contains(e);
    }

    private static void look(Game g) {
        g.camera.position.set(g.player.pos.x, g.player.pos.y + g.player.eyeHeight(), g.player.pos.z);
    }

    private static void hold(Game g, ItemType item) {
        g.player.inventory.set(0, new ItemStack(item, 4));
        g.player.hotbarSel = 0;
    }

    /**
     * The direction to give the throw command for the lower arc from the eye
     * through {@code (x, y, z)}: the command adds its upward bias, the
     * projectile system normalises it and adds its seeded cone spread.
     */
    private static Vector3f aim(Game g, String weapon, float x, float y, float z) {
        WeaponDefinition w = WeaponRegistry.byId(weapon);
        Vector3f eye = g.camera.position;
        float dx = x - eye.x, dz = z - eye.z;
        float d = (float) Math.sqrt(dx * dx + dz * dz);
        float hx = dx / d, hz = dz / d;
        for (int step = -800; step <= 800; step++) {
            double pitch = Math.toRadians(step * 0.1);
            float cx = (float) Math.cos(pitch) * hx, cz = (float) Math.cos(pitch) * hz;
            float cy = (float) Math.sin(pitch) + 0.18f;
            float len = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
            float vh = (float) Math.sqrt(cx * cx + cz * cz) / len * w.projectileSpeed;
            float vy = cy / len * w.projectileSpeed;
            float t = d / vh;
            if (eye.y + vy * t - 0.5f * w.projectileGravity * t * t >= y) {
                float c = (float) Math.cos(pitch);
                return new Vector3f(c * hx, (float) Math.sin(pitch), c * hz);
            }
        }
        throw new AssertionError("out of the throw's reach");
    }

    /** A sealed 2 x 2 pen round {@code (PEN_X, PEN_Z)}. */
    private static void pen(Game g) {
        room(g, (int) PEN_X - 1, (int) PEN_X, (int) PEN_Z - 1, (int) PEN_Z);
    }

    /**
     * A sealed room with the given interior: walls two high round it, a roof
     * over walls and interior. Plain stone, nobody's property, so a blast
     * breaking it costs the player nothing.
     */
    private static void room(Game g, int x0, int x1, int z0, int z1) {
        fill(g, x0 - 1, x1 + 1, 40, 41, z0 - 1, z1 + 1, BlockType.STONE);
        fill(g, x0, x1, 40, 41, z0, z1, BlockType.AIR);
        fill(g, x0 - 1, x1 + 1, 42, 42, z0 - 1, z1 + 1, BlockType.STONE);
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

    private static List<BodyFragment> allPieces(Game g) {
        List<BodyFragment> all = new ArrayList<>(g.fragments.live);
        all.addAll(g.fragments.settled);
        return all;
    }

    private static Map<BodyFamily, Integer> countByFamily(List<BodyFragment> pieces) {
        Map<BodyFamily, Integer> counts = new EnumMap<>(BodyFamily.class);
        for (BodyFragment f : pieces) {
            counts.merge(f.definition.family, 1, Integer::sum);
        }
        return counts;
    }

    /** Sturdy enough that the fire ends no run before the test weakens it. */
    private static <T extends Entity> T sturdy(T e) {
        if (!(e instanceof com.veylon.entity.Player)) {
            e.maxHealth = STURDY;
            e.health = STURDY;
        }
        return e;
    }

    /**
     * One settlement per side, keyed to a far region and centred on the
     * arena, with a record-only defender so no single death clears it.
     */
    private static Settlement settlement(Game g, Settlement.Alignment side) {
        int region = switch (side) {
            case FRIENDLY -> -43;
            case NEUTRAL -> -44;
            case HOSTILE -> -45;
        };
        long id = Settlement.packId(region, region);
        Settlement s = g.world.settlements.get(id);
        if (s != null) {
            return s;
        }
        String faction = switch (side) {
            case FRIENDLY -> HumanFaction.FRONTIER;
            case NEUTRAL -> HumanFaction.FREE_SETTLERS;
            case HOSTILE -> HumanFaction.HEADHUNTERS;
        };
        s = new Settlement(id, region, region, side == Settlement.Alignment.HOSTILE
                ? SettlementType.FORT : SettlementType.VILLAGE, new Vec3i(326, 40, 326), faction, side);
        s.factionId = faction;
        s.residents.add(new Settlement.Resident("Defender", side == Settlement.Alignment.HOSTILE
                ? NpcArchetype.HUNTER : NpcArchetype.GUARD));
        g.world.settlements.put(id, s);
        return s;
    }

    static Game arena() {
        return arena(GameMode.SURVIVAL);
    }

    /** The flat stone arena, being played: clear noon, nobody about, the player at (310, 40, 310). */
    static Game arena(GameMode mode) {
        Game g = new Game();
        g.newWorld(777L, true, mode);
        for (int cx = 18; cx <= 21; cx++) {
            for (int cz = 18; cz <= 21; cz++) {
                Chunk c = g.world.getOrCreateChunk(cx, cz);
                for (int lx = 0; lx < Chunk.SX; lx++) {
                    for (int lz = 0; lz < Chunk.SZ; lz++) {
                        for (int y = 0; y < Chunk.SY; y++) {
                            c.set(lx, y, lz, y <= 39 ? BlockType.STONE : BlockType.AIR);
                        }
                    }
                }
                c.recomputeAllHeights();
                c.rebuildLights();
            }
        }
        g.entities.creatures.clear();
        g.entities.npcs.clear();
        g.entities.carcasses.clear();
        g.entities.corpses.clear();
        g.world.campPos = null;
        g.world.campfireFuel.clear();
        g.fire.reset();
        g.noise.reset();
        g.player.pos.set(310.5f, FEET, 310.5f);
        g.time.totalMinutes = 12 * 60;
        g.weather.current = Weather.CLEAR;
        g.weather.next = Weather.CLEAR;
        g.weather.blend = 1f;
        g.weather.changeTimer = 1e6f;
        g.projectiles.setRandomSeed(23L);
        g.liquidFire.setRandomSeed(29L);
        g.fire.setRandomSeed(31L);
        g.appState = Game.AppState.PLAYING;
        look(g);
        return g;
    }
}
