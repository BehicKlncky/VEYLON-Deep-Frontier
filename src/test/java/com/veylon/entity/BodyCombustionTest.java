package com.veylon.entity;

import com.veylon.Game;
import com.veylon.engine.ParticleSystem;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.SimulationScheduler;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.BlockType;
import com.veylon.world.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Stream;

import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_CREATURE;
import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_NPC;
import static com.veylon.entity.CombustionConstants.AFTERBURN_DPS_PLAYER;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_CREATURE;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_NPC;
import static com.veylon.entity.CombustionConstants.CONTACT_DPS_PLAYER;
import static com.veylon.entity.CombustionConstants.FADE_SECONDS;
import static com.veylon.entity.CombustionConstants.MAX_BURN_SECONDS;
import static com.veylon.entity.CombustionConstants.MAX_FUEL_SECONDS;
import static com.veylon.entity.CombustionConstants.MIN_INTENSITY;
import static com.veylon.entity.CombustionConstants.RAIN_EXTINGUISH_SECONDS;
import static com.veylon.entity.CombustionConstants.SHALLOW_WATER_DRAIN;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One fire per living body, the same rules for every kind of body: each
 * species (taken from {@code CreatureType.values()}, so a new one is covered
 * without editing this list), every family of person, and the Survival
 * player.
 *
 * <p>Isolated mechanics fixture: {@code MolotovTest}'s flat stone arena (floor
 * top at y = 39, so bodies stand at y = 40), clear weather unless a test sets
 * it, and only {@link CombustionSystem#fastTick} driven, one
 * {@link SimulationScheduler#FAST_DT} at a time, so no AI, needs or physics
 * move anything. Bodies are made sturdy (1000 health) unless a test is about
 * death. Contacts are reported through the public command API; production
 * flame sources are wired to it in milestone 07.
 */
class BodyCombustionTest {

    private static final float DT = SimulationScheduler.FAST_DT;
    private static final float FEET = 40f;
    private static final float STURDY = 1000f;
    /** Float resolution near {@link #STURDY} health is about 6e-5. */
    private static final float HEALTH_EPS = 2e-4f;

    // ------------------------------------------------------------------
    // Every living body
    // ------------------------------------------------------------------

    /** How to put one kind of living body into the arena. */
    interface Spawner {
        Entity spawn(Game g, float x, float y, float z);
    }

    record Target(String label, Spawner spawner) {
        Entity spawn(Game g, float x, float y, float z) {
            return spawner.spawn(g, x, y, z);
        }

        @Override
        public String toString() {
            return label;
        }
    }

    static Stream<Target> everyLivingBody() {
        return Stream.concat(everyPersonAndAnimal(), Stream.of(new Target("Survival player",
                (g, x, y, z) -> {
                    g.player.pos.set(x, y, z);
                    return g.player;
                })));
    }

    static Stream<Target> everyPersonAndAnimal() {
        Stream<Target> animals = Arrays.stream(Creature.CreatureType.values())
                .map(type -> new Target(type.name(),
                        (g, x, y, z) -> g.entities.spawnCreature(g.world, type, x, y, z)));
        Stream<Target> people = Stream.of(
                person("camp member", n -> {
                    n.campIndex = 1;
                }),
                person("wandering trader", n -> n.isTrader = true),
                person("raider", n -> n.raider = true),
                person("settlement resident", n -> {
                    n.archetype = NpcArchetype.VILLAGER;
                    n.residentIndex = 0;
                }),
                person("captive", n -> {
                    n.archetype = NpcArchetype.CAPTIVE;
                    n.residentIndex = 1;
                }),
                person("war party", n -> {
                    n.archetype = NpcArchetype.GUARD;
                    n.warParty = true;
                    n.partyKind = Npc.PartyKind.BOUNTY_HUNTER;
                }));
        return Stream.concat(animals, people);
    }

    private static Target person(String label, java.util.function.Consumer<Npc> role) {
        return new Target(label, (g, x, y, z) -> {
            Npc n = g.entities.spawnNpc(g.world, label, x, y, z);
            role.accept(n);
            return n;
        });
    }

    // ------------------------------------------------------------------
    // Ignition, afterburn, refresh, burnout, reignition
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void aLiquidContactLightsTheBodyAtOnceAndBurnsAtTheContactRate(Target target) {
        Game g = arena();
        Entity body = sturdy(target.spawn(g, 320.5f, FEET, 320.5f));
        assertTrue(g.combustion.expose(body, CombustionSource.LIQUID, 1f, true, 7,
                320.5f, FEET + 0.1f, 320.5f));
        assertFalse(body.combustion.burning(), "a contact is applied by the fast tick, not when reported");

        float before = body.health;
        g.combustion.fastTick(g, DT);

        BodyCombustion fire = body.combustion;
        assertTrue(fire.burning(), "burning liquid lights a body on first contact");
        assertTrue(fire.inContact());
        assertSame(CombustionSource.LIQUID, fire.owner());
        assertEquals(7, fire.ownerSourceId());
        assertEquals(CombustionSource.LIQUID.fuelSeconds, fire.fuel(), 1e-6f);
        assertEquals(1f, fire.intensity(), 1e-6f);
        assertEquals(contactDps(body) * DT, before - body.health, HEALTH_EPS,
                "the ignition tick already burns at the contact rate");
        assertEquals(1, g.combustion.totalIgnitions);
        assertTrue(g.combustion.isBurning(body));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void itKeepsBurningAfterLeavingTheFlameThenFadesAndBurnsOut(Target target) {
        Game g = arena();
        Entity body = sturdy(target.spawn(g, 320.5f, FEET, 320.5f));
        g.combustion.expose(body, CombustionSource.LIQUID, 1f, false, 0, 320.5f, FEET, 320.5f);
        g.combustion.fastTick(g, DT);

        float afterContact = body.health;
        int afterburnTicks = 0;
        float lastIntensity = 1f;
        while (body.combustion.burning() && afterburnTicks < 1000) {
            float fuel = body.combustion.fuel();
            float intensity = body.combustion.intensity();
            if (fuel >= FADE_SECONDS + 1e-3f) {
                assertEquals(1f, intensity, 1e-6f, "full flames while more than the fade is left");
            }
            assertTrue(intensity <= lastIntensity + 1e-6f, "the flames never grow back unfed");
            assertTrue(intensity >= MIN_INTENSITY - 1e-6f);
            lastIntensity = intensity;
            float before = body.health;
            g.combustion.fastTick(g, DT);
            assertFalse(body.combustion.inContact(), "nothing touches it any more");
            assertEquals(afterburnDps(body) * intensity * DT, before - body.health, HEALTH_EPS,
                    "afterburn burns at its own rate, scaled by intensity");
            afterburnTicks++;
        }

        assertEquals(Math.round(CombustionSource.LIQUID.fuelSeconds / DT), afterburnTicks,
                "a finite afterburn of exactly the granted seconds");
        assertTrue(lastIntensity < 0.4f, "and the flames had shrunk by the end: " + lastIntensity);
        // Full strength while fuel exceeds the fade, then a linear taper to MIN_INTENSITY.
        float full = CombustionSource.LIQUID.fuelSeconds - FADE_SECONDS;
        float expected = afterburnDps(body) * (full + FADE_SECONDS * (1f + MIN_INTENSITY) / 2f);
        assertEquals(expected, afterContact - body.health, expected * 0.02f,
                "the afterburn's damage budget");
        assertEquals(1, g.combustion.totalBurnouts);
        assertTrue(body.combustion.scorch() > 0f, "the burn leaves its scorch");

        float burnt = body.health;
        for (int i = 0; i < 40; i++) {
            g.combustion.fastTick(g, DT);
        }
        assertEquals(burnt, body.health, 0f, "a fire that burned out does no more damage");
        assertNull(body.combustion.owner());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void reExposureRefreshesTheOneFireUpToItsCapAndNeverStacks(Target target) {
        Game g = arena();
        Entity body = sturdy(target.spawn(g, 320.5f, FEET, 320.5f));
        g.combustion.expose(body, CombustionSource.LIQUID, 1f, false, 0, 320.5f, FEET, 320.5f);
        g.combustion.fastTick(g, DT);
        tick(g, 40);
        assertEquals(CombustionSource.LIQUID.fuelSeconds - 40 * DT, body.combustion.fuel(), 1e-3f);

        // Back into the flames: fuel returns to the grant, not to the grant on top.
        g.combustion.expose(body, CombustionSource.LIQUID, 1f, false, 0, 320.5f, FEET, 320.5f);
        float before = body.health;
        g.combustion.fastTick(g, DT);
        assertEquals(CombustionSource.LIQUID.fuelSeconds, body.combustion.fuel(), 1e-6f);
        assertEquals(contactDps(body) * DT, before - body.health, HEALTH_EPS);

        // Eight flames at once still burn like one.
        for (int i = 0; i < 5; i++) {
            g.combustion.expose(body, CombustionSource.LIQUID, 1f, false, i, 320.5f, FEET, 320.5f);
        }
        for (int i = 0; i < 3; i++) {
            g.combustion.expose(body, CombustionSource.BLOCK_FIRE, 1f, false, i, 320.5f, FEET, 320.5f);
        }
        before = body.health;
        g.combustion.fastTick(g, DT);
        assertEquals(contactDps(body) * DT, before - body.health, HEALTH_EPS,
                "overlapping flames never add up");

        // However long the contact lasts, the fuel stays within the cap.
        for (int i = 0; i < 200; i++) {
            g.combustion.expose(body, CombustionSource.DIRECT_HIT, 1f, false, 0, 320.5f, FEET, 320.5f);
            g.combustion.expose(body, CombustionSource.LIQUID, 1f, false, 0, 320.5f, FEET, 320.5f);
            g.combustion.fastTick(g, DT);
            assertTrue(body.combustion.fuel() <= MAX_FUEL_SECONDS);
        }
        assertEquals(CombustionSource.DIRECT_HIT.fuelSeconds, body.combustion.fuel(), 1e-6f);
        assertEquals(1, g.combustion.totalIgnitions, "one episode throughout: no second fire");

        // A weaker flame feeds the fire only up to its own grant and takes it over.
        tick(g, 40);
        float fuel = body.combustion.fuel();
        g.combustion.expose(body, CombustionSource.TORCH, 0.6f, true, 3, 320.5f, FEET + 1f, 320.5f);
        g.combustion.fastTick(g, DT);
        assertEquals(Math.max(fuel, CombustionSource.TORCH.fuelSeconds), body.combustion.fuel(), 1e-6f);
        assertSame(CombustionSource.TORCH, body.combustion.owner(),
                "the latest flame to touch the body owns its fire");
        assertTrue(body.combustion.ownerByPlayer());
        assertEquals(1f, body.combustion.peakIntensity(), 0f, "and the peak is kept");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void aBodyThatBurnedOutCatchesAgainAsANewEpisode(Target target) {
        Game g = arena();
        Entity body = sturdy(target.spawn(g, 320.5f, FEET, 320.5f));
        g.combustion.expose(body, CombustionSource.LIQUID, 1f, false, 0, 320.5f, FEET, 320.5f);
        tick(g, 1 + Math.round(CombustionSource.LIQUID.fuelSeconds / DT));
        assertFalse(body.combustion.burning());
        float firstEpisode = body.combustion.burnSeconds();
        float scorch = body.combustion.scorch();
        assertTrue(firstEpisode > 6f);

        g.combustion.expose(body, CombustionSource.LIQUID, 0.5f, true, 2, 320.5f, FEET, 320.5f);
        g.combustion.fastTick(g, DT);

        assertTrue(body.combustion.burning(), "a fresh flame lights it again");
        assertEquals(2, g.combustion.totalIgnitions);
        assertEquals(DT, body.combustion.burnSeconds(), 1e-6f, "as a new episode");
        assertEquals(0.5f, body.combustion.peakIntensity(), 0f, "with its own strength");
        assertTrue(body.combustion.ownerByPlayer());
        assertTrue(body.combustion.scorch() > scorch, "scorch keeps growing across episodes");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void steadyFlamesNeedSustainedContactAndGrazesNeverAddUp(Target target) {
        Game g = arena();
        Entity body = sturdy(target.spawn(g, 320.5f, FEET, 320.5f));
        float torch = CombustionSource.TORCH.nominalIntensity;
        // Brushing past a torch for half a second, then a second away, twice.
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 10; i++) {
                g.combustion.expose(body, CombustionSource.TORCH, torch, false, 1, 320.5f, FEET + 1f, 320.5f);
                g.combustion.fastTick(g, DT);
            }
            assertFalse(body.combustion.burning());
            assertTrue(body.combustion.heat() > 0f);
            tick(g, 20);
            assertEquals(0f, body.combustion.heat(), 0f, "the heat of a graze is gone again");
        }
        assertEquals(STURDY, body.health, 0f, "heating up does no damage");

        int ticks = 0;
        while (!body.combustion.burning() && ticks < 200) {
            g.combustion.expose(body, CombustionSource.TORCH, torch, false, 1, 320.5f, FEET + 1f, 320.5f);
            g.combustion.fastTick(g, DT);
            ticks++;
        }
        assertEquals((int) Math.ceil(1f / (CombustionSource.TORCH.heatGainPerSecond * torch * DT)), ticks,
                "holding still in a torch flame catches after its heat-up time");
        assertEquals(0f, body.combustion.heat(), 0f);

        // The player target is one body, so forget its fire before reusing it.
        g.combustion.clear(body);
        Entity other = sturdy(target.spawn(g, 330.5f, FEET, 330.5f));
        for (int i = 0; i < 4; i++) {
            g.combustion.expose(other, CombustionSource.BLOCK_FIRE, 1f, false, 1, 330.5f, FEET, 330.5f);
            g.combustion.fastTick(g, DT);
        }
        assertFalse(other.combustion.burning());
        g.combustion.expose(other, CombustionSource.BLOCK_FIRE, 1f, false, 1, 330.5f, FEET, 330.5f);
        g.combustion.fastTick(g, DT);
        assertTrue(other.combustion.burning(), "a burning bush catches in a quarter second");
    }

    // ------------------------------------------------------------------
    // Water and rain
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void waterOverTheTorsoPutsItOutAndShallowWaterOnlyShortensIt(Target target) {
        Game g = arena();
        // A pool two deep at (320, 320), one cell of water at (326, 326).
        g.world.setBlock(320, 39, 320, BlockType.WATER, false);
        g.world.setBlock(320, 38, 320, BlockType.WATER, false);
        g.world.setBlock(326, 39, 326, BlockType.WATER, false);

        Entity body = sturdy(target.spawn(g, 320.5f, 38f, 320.5f));
        assertFalse(g.combustion.ignite(g, body, CombustionSource.DIRECT_HIT, 1f, true, 0, 320.5f, 39f, 320.5f),
                "a body under water cannot be set alight");
        g.combustion.expose(body, CombustionSource.LIQUID, 1f, true, 0, 320.5f, 39f, 320.5f);
        g.combustion.fastTick(g, DT);
        assertFalse(body.combustion.burning(), "nor catch from a flame touching it");

        // Lit on dry ground, then into the deep water.
        body.pos.set(323.5f, FEET, 323.5f);
        assertTrue(g.combustion.ignite(g, body, CombustionSource.DIRECT_HIT, 1f, true, 0, 323.5f, 41f, 323.5f));
        tick(g, 3);
        body.pos.set(320.5f, 38f, 320.5f);
        g.combustion.fastTick(g, DT);
        assertFalse(body.combustion.burning(), "torso-deep water puts it out at once");
        assertEquals(1, g.combustion.totalDoused);
        assertEquals(0f, body.combustion.heat(), 0f);

        // Lit again, then standing in one cell of water.
        body.pos.set(323.5f, FEET, 323.5f);
        g.combustion.ignite(g, body, CombustionSource.DIRECT_HIT, 1f, true, 0, 323.5f, 41f, 323.5f);
        tick(g, 3);
        body.pos.set(326.5f, 39f, 326.5f);
        boolean person = body instanceof Npc || body instanceof Player;
        float fuel = body.combustion.fuel();
        g.combustion.fastTick(g, DT);
        if (person) {
            assertTrue(body.combustion.burning(),
                    "water to the hips is not the torso: a person keeps burning");
            assertEquals(fuel - SHALLOW_WATER_DRAIN * DT, body.combustion.fuel(), 1e-5f,
                    "but the fire burns down faster");
        } else {
            assertFalse(body.combustion.burning(), "one cell of water covers an animal's body");
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void openRainPutsItOutAndARoofOverTheHeadDoesNot(Target target) {
        Game g = arena();
        setWeather(g, Weather.RAIN);
        // A stone roof over (330, 330) with headroom for the tallest body.
        for (int x = 329; x <= 331; x++) {
            for (int z = 329; z <= 331; z++) {
                g.world.setBlock(x, 42, z, BlockType.STONE, false);
            }
        }

        Entity exposed = sturdy(target.spawn(g, 320.5f, FEET, 320.5f));
        g.combustion.ignite(g, exposed, CombustionSource.DIRECT_HIT, 1f, false, 0, 320.5f, 41f, 320.5f);
        int ticks = 0;
        while (exposed.combustion.burning() && ticks < 1000) {
            g.combustion.fastTick(g, DT);
            ticks++;
        }
        assertEquals(Math.round(RAIN_EXTINGUISH_SECONDS / DT), ticks,
                "open rain puts a burning body out long before its fuel would");
        assertEquals(1, g.combustion.totalRainedOut);

        if (exposed != g.player) {
            g.entities.creatures.remove(exposed);
            g.entities.npcs.remove(exposed);
        }
        Entity sheltered = sturdy(target.spawn(g, 330.5f, FEET, 330.5f));
        g.combustion.ignite(g, sheltered, CombustionSource.DIRECT_HIT, 1f, false, 0, 330.5f, 41f, 330.5f);
        ticks = 0;
        while (sheltered.combustion.burning() && ticks < 1000) {
            g.combustion.fastTick(g, DT);
            assertEquals(0f, sheltered.combustion.soak(), 0f, "no rain reaches under the roof");
            ticks++;
        }
        assertEquals(1 + Math.round(CombustionSource.DIRECT_HIT.fuelSeconds / DT), ticks,
                "under a roof it burns out as it would in dry weather");
        assertEquals(1, g.combustion.totalRainedOut);
        assertEquals(1, g.combustion.totalBurnouts);
    }

    // ------------------------------------------------------------------
    // Death and Creative
    // ------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyLivingBody")
    void aBurnDeathIsAnOrdinaryDeathAndEndsTheFire(Target target) {
        Game g = arena();
        Entity body = target.spawn(g, 320.5f, FEET, 320.5f);
        body.health = 1f;
        g.combustion.ignite(g, body, CombustionSource.DIRECT_HIT, 1f, true, 0, 320.5f, 41f, 320.5f);
        int ticks = 0;
        while (!body.dead && ticks < 200) {
            g.combustion.fastTick(g, DT);
            ticks++;
        }

        assertTrue(body.dead && body.health <= 0f, "the fire kills through the damage path");
        assertEquals(!(body instanceof Player), body.lastHitByPlayer,
                "the player's fire earns the kill, except the player's own death");
        assertFalse(body.dismemberOnDeath, "a fire death never comes apart");
        assertFalse(g.combustion.isBurning(body), "a corpse is not a burning body");

        float health = body.health;
        float seconds = body.combustion.burnSeconds();
        assertFalse(g.combustion.expose(body, CombustionSource.LIQUID, 1f, true, 0, 320.5f, FEET, 320.5f));
        assertFalse(g.combustion.ignite(g, body, CombustionSource.DIRECT_HIT, 1f, true, 0, 320.5f, FEET, 320.5f));
        tick(g, 40);
        assertEquals(health, body.health, 0f, "a dead body takes no more burn damage");
        assertEquals(seconds, body.combustion.burnSeconds(), 0f, "and its fire is not advanced");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("everyPersonAndAnimal")
    void theCreativePlayerNeverBurnsWhilePeopleAndAnimalsStillDo(Target target) {
        Game g = arena();
        assertTrue(g.switchGameMode(GameMode.CREATIVE));
        Player player = g.player;
        assertFalse(g.combustion.expose(player, CombustionSource.LIQUID, 1f, false, 0, 310f, FEET, 310f));
        assertFalse(g.combustion.ignite(g, player, CombustionSource.DIRECT_HIT, 1f, false, 0, 310f, FEET, 310f));

        Entity body = sturdy(target.spawn(g, 320.5f, FEET, 320.5f));
        g.combustion.ignite(g, body, CombustionSource.DIRECT_HIT, 1f, true, 0, 320.5f, 41f, 320.5f);
        tick(g, 20);
        assertTrue(body.combustion.burning(), "people and animals are vulnerable in Creative too");
        assertTrue(body.health < STURDY);
        assertFalse(player.combustion.burning());
        assertEquals(player.maxHealth, player.health, 0f);
    }

    @Test
    void aBurningPlayerWhoTurnsCreativeIsPutOutAndStartsClearInSurvival() {
        Game g = arena();
        Player player = g.player;
        g.combustion.ignite(g, player, CombustionSource.DIRECT_HIT, 1f, false, 0, 310f, 41f, 310f);
        tick(g, 30);
        assertTrue(player.combustion.burning() && player.combustion.scorch() > 0f);

        assertTrue(g.switchGameMode(GameMode.CREATIVE));
        assertFalse(player.combustion.burning(), "Creative forgets the fire at once");
        assertEquals(0f, player.combustion.scorch(), 0f);
        g.combustion.expose(player, CombustionSource.LIQUID, 1f, false, 0, 310f, FEET, 310f);
        tick(g, 5);
        assertFalse(player.combustion.burning());

        assertTrue(g.switchGameMode(GameMode.SURVIVAL));
        tick(g, 5);
        assertFalse(player.combustion.burning(), "no fire comes back with Survival");
        assertTrue(g.combustion.ignite(g, player, CombustionSource.DIRECT_HIT, 1f, false, 0, 310f, 41f, 310f),
                "but a fresh flame can light the player again");
    }

    // ------------------------------------------------------------------
    // Ownership, input bounds, presentation independence, player control
    // ------------------------------------------------------------------

    /** Contacts reported in every order leave the same fire, the same owner and the same damage. */
    @Test
    void overlappingContactsResolveToTheSameOwnerWhateverTheOrder() {
        Game g = arena();
        record Contact(CombustionSource kind, float intensity, boolean byPlayer, int id, float x) {
        }
        List<Contact> contacts = List.of(
                new Contact(CombustionSource.LIQUID, 1f, false, 4, 320.2f),
                new Contact(CombustionSource.LIQUID, 1f, true, 9, 320.6f),
                new Contact(CombustionSource.LIQUID, 1f, true, 9, 320.4f),
                new Contact(CombustionSource.BLOCK_FIRE, 1f, true, 1, 320.5f),
                new Contact(CombustionSource.LIQUID, 0.7f, true, 1, 320.5f));
        List<List<Contact>> orders = permutations(contacts);
        List<Npc> bodies = new ArrayList<>();
        for (int i = 0; i < orders.size(); i++) {
            Npc n = (Npc) sturdy(g.entities.spawnNpc(g.world, "Villager", 290.5f + (i % 12) * 5,
                    FEET, 290.5f + (i / 12) * 5));
            for (Contact c : orders.get(i)) {
                g.combustion.expose(n, c.kind(), c.intensity(), c.byPlayer(), c.id(), c.x(), FEET, 320.5f);
            }
            bodies.add(n);
        }
        g.combustion.fastTick(g, DT);

        for (Npc n : bodies) {
            BodyCombustion fire = n.combustion;
            assertSame(CombustionSource.LIQUID, fire.owner(), "liquid outranks block fire");
            assertTrue(fire.ownerByPlayer(), "at equal strength the player's flame owns the fire");
            assertEquals(9, fire.ownerSourceId());
            assertEquals(320.4f, fire.exposureX(), 0f, "and equal contacts keep the lower point");
            assertEquals(STURDY - CONTACT_DPS_NPC * DT, n.health, HEALTH_EPS, "one contact's damage");
        }

        // A direct hit outranks everything, even at lower strength and unattributed.
        Npc hit = bodies.getFirst();
        g.combustion.expose(hit, CombustionSource.LIQUID, 1f, true, 0, 320.5f, FEET, 320.5f);
        g.combustion.expose(hit, CombustionSource.DIRECT_HIT, 0.4f, false, 2, 320.5f, FEET, 320.5f);
        g.combustion.fastTick(g, DT);
        assertSame(CombustionSource.DIRECT_HIT, hit.combustion.owner());
        assertFalse(hit.combustion.ownerByPlayer());
    }

    @Test
    void inputsThatAreNotFlamesAreRefusedAndTheStateStaysFiniteAndBounded() {
        Game g = arena();
        Npc n = (Npc) sturdy(g.entities.spawnNpc(g.world, "Villager", 320.5f, FEET, 320.5f));
        assertFalse(g.combustion.expose(null, CombustionSource.LIQUID, 1f, false, 0, 0, 0, 0));
        assertFalse(g.combustion.expose(n, null, 1f, false, 0, 320f, FEET, 320f));
        for (float bad : new float[] {0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) {
            assertFalse(g.combustion.expose(n, CombustionSource.LIQUID, bad, false, 0, 320f, FEET, 320f));
            assertFalse(g.combustion.ignite(g, n, CombustionSource.LIQUID, bad, false, 0, 320f, FEET, 320f));
        }
        assertFalse(g.combustion.expose(n, CombustionSource.LIQUID, 1f, false, 0, Float.NaN, FEET, 320f));
        g.combustion.fastTick(g, DT);
        assertFalse(n.combustion.burning(), "nothing that is not a flame lights a body");
        assertFalse(g.combustion.extinguish(null));
        g.combustion.clear(null);
        assertFalse(g.combustion.isBurning(null));

        assertTrue(g.combustion.expose(n, CombustionSource.LIQUID, 5f, false, 0, 320f, FEET, 320f));
        g.combustion.fastTick(g, DT);
        assertEquals(1f, n.combustion.intensity(), 0f, "an intensity above 1 is clamped");

        float fuel = n.combustion.fuel();
        float health = n.health;
        for (float bad : new float[] {0f, -0.05f, Float.NaN, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY}) {
            g.combustion.fastTick(g, bad);
        }
        assertEquals(fuel, n.combustion.fuel(), 0f, "an invalid step is ignored");
        assertEquals(health, n.health, 0f);
        g.combustion.fastTick(g, 30f);
        assertEquals(fuel - DT, n.combustion.fuel(), 1e-6f, "a long step advances one fast tick, never a burst");

        // Random abuse: every state value stays finite and in range.
        Random rng = new Random(0x5EED_F12EL);
        g.world.setBlock(321, 39, 320, BlockType.WATER, false);
        CombustionSource[] kinds = CombustionSource.values();
        for (int t = 0; t < 3000; t++) {
            setWeather(g, rng.nextInt(4) == 0 ? Weather.RAIN : Weather.CLEAR);
            n.pos.set(320.5f + rng.nextInt(2), FEET - (rng.nextBoolean() ? 1f : 0f), 320.5f);
            int contacts = rng.nextInt(3);
            for (int c = 0; c < contacts; c++) {
                g.combustion.expose(n, kinds[rng.nextInt(kinds.length)], rng.nextFloat() * 1.5f,
                        rng.nextBoolean(), rng.nextInt(5), 320f, FEET, 320f);
            }
            if (rng.nextInt(50) == 0) {
                g.combustion.ignite(g, n, CombustionSource.DIRECT_HIT, rng.nextFloat(), true, 0, 320f, FEET, 320f);
            }
            g.combustion.fastTick(g, rng.nextInt(20) == 0 ? rng.nextFloat() * 3f : DT);
            n.health = STURDY;
            BodyCombustion fire = n.combustion;
            assertTrue(fire.heat() >= 0f && fire.heat() <= 1f, "heat " + fire.heat());
            assertTrue(fire.fuel() >= 0f && fire.fuel() <= MAX_FUEL_SECONDS, "fuel " + fire.fuel());
            assertTrue(fire.intensity() >= 0f && fire.intensity() <= 1f, "intensity " + fire.intensity());
            assertTrue(fire.soak() >= 0f && fire.soak() <= RAIN_EXTINGUISH_SECONDS, "soak " + fire.soak());
            assertTrue(fire.scorch() >= 0f && fire.scorch() <= 1f, "scorch " + fire.scorch());
            assertTrue(fire.burnSeconds() >= 0f && fire.burnSeconds() <= MAX_BURN_SECONDS);
            assertTrue(Float.isFinite(fire.exposureX()) && Float.isFinite(fire.exposureY())
                    && Float.isFinite(fire.exposureZ()));
        }
    }

    /** Gameplay never waits on particles: a spent effects budget burns exactly the same. */
    @Test
    void aFullParticleBudgetNeverMakesABodyImmune() {
        Game plain = arena();
        Game saturated = arena();
        saturated.particles.count = ParticleSystem.MAX;
        float[] health = new float[2];
        Game[] games = {plain, saturated};
        for (int i = 0; i < 2; i++) {
            Game g = games[i];
            Creature deer = g.entities.spawnCreature(g.world, Creature.CreatureType.DEER, 320.5f, FEET, 320.5f);
            g.combustion.expose(deer, CombustionSource.LIQUID, 1f, true, 0, 320.5f, FEET, 320.5f);
            tick(g, 40);
            assertTrue(deer.combustion.burning());
            health[i] = deer.health;
        }
        assertEquals(health[0], health[1], 0f);
        assertEquals(ParticleSystem.MAX, saturated.particles.count, "and it spends no particles itself");
    }

    /** R4: a burning player moves exactly as a player who is not burning. */
    @Test
    void theBurningPlayersMovementIsExactlyTheUnburnedPlayers() {
        Game burning = arena();
        Game calm = arena();
        burning.combustion.ignite(burning, burning.player, CombustionSource.DIRECT_HIT, 1f, false, 0,
                310f, 41f, 310f);
        PlayerMovementSystem movement = new PlayerMovementSystem();
        PlayerMovementSystem.Command command = new PlayerMovementSystem.Command();
        PlayerMovementSystem.FrameResult result = new PlayerMovementSystem.FrameResult();
        for (int frame = 0; frame < 90; frame++) {
            command.set(frame * 2f, frame % 30 < 20, false, frame % 7 == 0, false, true, false,
                    frame % 15 == 0, frame % 15 == 0);
            for (Game g : new Game[] {burning, calm}) {
                movement.update(g.player, g.world, command, 1f / 60f, result);
                if (frame % 3 == 0) {
                    g.combustion.fastTick(g, DT);
                }
            }
            assertTrue(burning.player.combustion.burning());
            assertEquals(calm.player.pos, burning.player.pos, "frame " + frame);
            assertEquals(calm.player.vel, burning.player.vel, "frame " + frame);
            assertEquals(calm.player.sprinting, burning.player.sprinting);
            assertEquals(calm.player.crouching, burning.player.crouching);
            assertEquals(calm.player.moveSpeedMul(), burning.player.moveSpeedMul(), 0f);
            assertEquals(calm.player.canSprint(), burning.player.canSprint());
            assertEquals(calm.player.yaw, burning.player.yaw, 0f);
        }
        assertTrue(burning.player.health < calm.player.health, "it only hurts");
    }

    // ------------------------------------------------------------------
    // Fixture
    // ------------------------------------------------------------------

    private static float contactDps(Entity e) {
        return e instanceof Player ? CONTACT_DPS_PLAYER : e instanceof Npc ? CONTACT_DPS_NPC : CONTACT_DPS_CREATURE;
    }

    private static float afterburnDps(Entity e) {
        return e instanceof Player ? AFTERBURN_DPS_PLAYER : e instanceof Npc ? AFTERBURN_DPS_NPC : AFTERBURN_DPS_CREATURE;
    }

    private static Entity sturdy(Entity e) {
        e.maxHealth = STURDY;
        e.health = STURDY;
        return e;
    }

    private static void tick(Game g, int ticks) {
        for (int i = 0; i < ticks; i++) {
            g.combustion.fastTick(g, DT);
        }
    }

    /** {@code CombatSystemsTest}'s isolated arena: four chunks of stone up to y = 39, nobody about, dry. */
    static Game arena() {
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
        setWeather(game, Weather.CLEAR);
        return game;
    }

    private static void setWeather(Game game, Weather weather) {
        game.weather.current = weather;
        game.weather.next = weather;
        game.weather.blend = 1f;
    }

    private static <T> List<List<T>> permutations(List<T> items) {
        if (items.isEmpty()) {
            return List.of(List.of());
        }
        List<List<T>> all = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            List<T> rest = new ArrayList<>(items);
            T head = rest.remove(i);
            for (List<T> tail : permutations(rest)) {
                List<T> order = new ArrayList<>();
                order.add(head);
                order.addAll(tail);
                all.add(order);
            }
        }
        return all;
    }
}
