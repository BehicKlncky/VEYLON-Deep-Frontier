package com.veylon;

import com.sun.management.ThreadMXBean;
import com.veylon.engine.AudioManager;
import com.veylon.engine.ParticleSystem;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.Creature;
import com.veylon.entity.Creature.CreatureType;
import com.veylon.entity.Entity;
import com.veylon.entity.Npc;
import com.veylon.gfx.BodyFireLook;
import com.veylon.gfx.BodyFlames;
import com.veylon.gfx.model.BodyPosing;
import com.veylon.gfx.model.FlameAnchors;
import com.veylon.gfx.model.ModelPart;
import com.veylon.settlement.NpcArchetype;
import com.veylon.simulation.WeatherSystem.Weather;
import com.veylon.world.BlockType;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What burning bodies give off and how they are heard: flames, embers and
 * smoke leave the burning parts of the body with its own velocity and settle
 * into the wind; smoke thickens as the flames weaken; water and rain steam, a
 * fire that burns out does not; the player's own body gives off nothing into
 * their view but is heard. All of it is bounded — per body, per pass, by range
 * and by the particle ceiling — and none of it reaches the simulation: a world
 * burns, panics and dies identically with every effect off.
 */
class BodyFireEffectsTest {

    private static final float PASS = 0.12f;

    @Test
    void flamesAndSmokeLeaveTheBurningBodyWithItsVelocityAndSettleIntoTheWind() {
        Game game = BodyFireArena.arena();
        Npc runner = burning(game, BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 303.5f));
        runner.vel.set(4f, 0f, 0f);
        ParticleSystem ps = game.particles;
        ps.setRainWind(1f, 0f);
        Random rng = new Random(5);
        int smoke = 0;
        for (int pass = 0; pass < 40 && smoke == 0; pass++) {
            ps.count = 0;
            game.ambience.bodyFire.update(PASS, rng);
            for (int i = 0; i < ps.count; i++) {
                assertTrue(Math.abs(ps.px[i] - runner.pos.x) < 1.2f && Math.abs(ps.pz[i] - runner.pos.z) < 1.2f
                        && ps.py[i] > runner.pos.y - 0.2f && ps.py[i] < runner.pos.y + runner.height + 0.6f,
                        "particle " + i + " leaves from the body");
                if (ps.kind[i] == ParticleSystem.KIND_HAZE) {
                    assertTrue(ps.velocityX(i) > 3f, "smoke leaves with the runner's pace: " + ps.velocityX(i));
                    smoke++;
                } else {
                    assertTrue(ps.velocityX(i) > 1.5f, "so do flames and embers: " + ps.velocityX(i));
                }
                assertTrue(ps.airResponse(i) > 0f, "and the air takes each of them");
            }
        }
        assertTrue(smoke > 0, "a burning body smokes");
        for (int s = 0; s < 72; s++) {
            ps.update(1f / 60f);
        }
        int drifting = 0;
        for (int i = 0; i < ps.count; i++) {
            if (ps.kind[i] == ParticleSystem.KIND_HAZE) {
                drifting++;
                assertEquals(1f, ps.velocityX(i), 0.8f, "1.2 s on, the smoke has slowed to the wind's pace");
                assertTrue(ps.px[i] > runner.pos.x + 0.5f, "and trails downwind of the body");
            }
        }
        assertTrue(drifting > 0, "the smoke outlives the flames");
    }

    @Test
    void theSmokeThickensAsTheFlamesWeaken() {
        Game game = BodyFireArena.arena();
        Npc n = burning(game, BodyFireArena.person(game, NpcArchetype.GUARD, 310.5f, 303.5f));
        int strong = smokeOver(game, 300);
        while (n.combustion.intensity() > 0.4f) {
            BodyFireArena.burn(game, 0.05f);
        }
        int weak = smokeOver(game, 300);
        assertTrue(weak > 2.5f * strong, "weak flames smoke far more: " + weak + " vs " + strong);
    }

    @Test
    void waterAndRainSteamAndAFireThatBurnsOutDoesNot() {
        Game game = BodyFireArena.arena();
        Creature wolf = burning(game, BodyFireArena.creature(game, CreatureType.WOLF, 310.5f, 303.5f));
        Creature deer = burning(game, BodyFireArena.creature(game, CreatureType.DEER, 314.5f, 303.5f));
        game.world.setBlock(310, 40, 303, BlockType.WATER, false);
        BodyFireArena.burn(game, 0.05f);
        assertFalse(wolf.combustion.burning(), "the wolf is put out by the water");
        assertTrue(deer.combustion.burning());
        assertTrue(steamNear(game, wolf, 6) > 0, "and steams");
        while (deer.combustion.burning()) {
            BodyFireArena.burn(game, 0.05f);
        }
        assertEquals(0, steamNear(game, deer, 12), "the deer's fire burned out: smoke, no steam");

        Npc walker = burning(game, BodyFireArena.person(game, NpcArchetype.GUARD, 320.5f, 303.5f));
        BodyFireArena.weather(game, Weather.RAIN);
        BodyFireArena.burn(game, 0.6f);
        assertTrue(walker.combustion.burning() && walker.combustion.soak() > 0f);
        assertTrue(steamNear(game, walker, 6) > 0, "rain on a burning body steams before it is out");
    }

    @Test
    void thePlayersOwnFireGivesOffNothingIntoTheirViewButIsHeard() {
        SoundProbe audio = new SoundProbe();
        Game game = BodyFireArena.arena(audio);
        BodyFireArena.ignite(game, game.player, 1);
        BodyFireArena.burn(game, 0.05f);
        assertTrue(game.combustion.isBurning(game.player));
        game.particles.count = 0;
        Random rng = new Random(3);
        for (int pass = 0; pass < 20; pass++) {
            game.ambience.bodyFire.update(PASS, rng);
        }
        assertEquals(0, game.particles.count, "no flame or smoke is put inside the camera");
        assertEquals(1, audio.flares, "the player hears themself catch, once");
        assertTrue(audio.crackles > 0, "and burn");
        assertTrue(game.ambience.bodyFire.loopGain > 0.8f, "their own fire takes the fire loop");
    }

    @Test
    void theNearestFewAreHeardEachCatchAndDouseOnce() {
        SoundProbe audio = new SoundProbe();
        Game game = BodyFireArena.arena(audio);
        List<Creature> herd = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Creature deer = BodyFireArena.creature(game, CreatureType.DEER, 312.5f + i * 2f, 310.5f);
            deer.maxHealth = deer.health = 500f;
            herd.add(deer);
            BodyFireArena.ignite(game, deer, i + 1);
        }
        BodyFireArena.burn(game, 0.05f);
        Random rng = new Random(9);
        game.ambience.bodyFire.update(PASS, rng);
        assertEquals(BodyFireEffects.AUDIO_SOURCES, game.ambience.bodyFire.heardCount(), "four heard of eight");
        assertTrue(audio.flares <= BodyFireEffects.AUDIO_SOURCES, "a crowd catching is not a wall of sound");
        int flares = audio.flares;
        for (int pass = 0; pass < 100; pass++) {
            game.ambience.bodyFire.update(PASS, rng);
        }
        assertEquals(flares, audio.flares, "each catch is heard once");
        assertTrue(audio.crackles > 30, "crackling on: " + audio.crackles);
        for (float[] at : audio.crackleAt) {
            assertTrue(at[0] < 312.5f + 4 * 2f, "only the four nearest crackle: x " + at[0]);
        }

        Creature near = herd.getFirst();
        game.world.setBlock((int) Math.floor(near.pos.x), 40, (int) Math.floor(near.pos.z), BlockType.WATER, false);
        BodyFireArena.burn(game, 0.05f);
        for (int pass = 0; pass < 20; pass++) {
            game.ambience.bodyFire.update(PASS, rng);
        }
        assertEquals(1, audio.sizzles, "a douse hisses once");

        game.ambience.reseed(1L);
        assertEquals(0, game.ambience.bodyFire.heardCount(), "a new world starts silent");
    }

    @Test
    void aFarBodyGivesOffAndSoundsNothing() {
        SoundProbe audio = new SoundProbe();
        Game game = BodyFireArena.arena(audio);
        Creature far = burning(game, BodyFireArena.creature(game, CreatureType.THORNHORN, 310.5f, 310.5f));
        game.player.pos.set(310.5f, 40f, 310.5f + BodyFireEffects.REDUCED_RANGE + 4f);
        game.particles.count = 0;
        Random rng = new Random(1);
        for (int pass = 0; pass < 50; pass++) {
            game.ambience.bodyFire.update(PASS, rng);
        }
        assertTrue(game.combustion.isBurning(far), "it burns on");
        assertEquals(0, game.particles.count, "unseen from here");
        assertEquals(0, audio.crackles + audio.flares, "and unheard");
    }

    @Test
    void everyPassIsBoundedPerBodyPerPassAndByTheCeiling() {
        Game game = BodyFireArena.arena();
        List<Entity> crowd = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            crowd.add(BodyFireArena.person(game, NpcArchetype.GUARD, 292.5f + (i % 10) * 3f, 296.5f + (i / 10) * 3f));
        }
        CreatureType[] species = CreatureType.values();
        for (int i = 0; i < 35; i++) {
            crowd.add(BodyFireArena.creature(game, species[i % species.length], 292.5f + (i % 12) * 3f,
                    312.5f + (i / 12) * 4f));
        }
        for (int i = 0; i < crowd.size(); i++) {
            crowd.get(i).maxHealth = crowd.get(i).health = 500f;
            BodyFireArena.ignite(game, crowd.get(i), i + 1);
        }
        BodyFireArena.weather(game, Weather.RAIN);
        BodyFireArena.burn(game, 0.5f);
        Random rng = new Random(2);
        ParticleSystem ps = game.particles;
        int[] givenOff = new int[crowd.size()];
        for (int pass = 0; pass < 200; pass++) {
            ps.count = 0;
            game.ambience.bodyFire.update(PASS, rng);
            assertTrue(game.ambience.bodyFire.particlesLastPass <= BodyFireEffects.MAX_PER_PASS,
                    "pass " + pass + " added " + game.ambience.bodyFire.particlesLastPass);
            for (int i = 0; i < ps.count; i++) {
                for (int b = 0; b < crowd.size(); b++) {
                    Entity e = crowd.get(b);
                    if (Math.abs(ps.px[i] - e.pos.x) < 1.2f && Math.abs(ps.pz[i] - e.pos.z) < 1.2f) {
                        givenOff[b]++;
                        break;
                    }
                }
            }
        }
        for (int b = 0; b < crowd.size(); b++) {
            assertTrue(givenOff[b] > 10, "a crowded pass is shared by every body, not the first ones listed: body "
                    + b + " gave off " + givenOff[b]);
        }

        // One body in the rain at full strength, alone: never more than its own cap.
        Game single = BodyFireArena.arena();
        Creature thornhorn = burning(single, BodyFireArena.creature(single, CreatureType.THORNHORN, 310.5f, 305.5f));
        // Rain nearly enough to put it out: the steam, on top of the flames, presses past a body's cap.
        BodyFireArena.weather(single, Weather.RAIN);
        BodyFireArena.burn(single, 1.3f);
        assertTrue(single.combustion.isBurning(thornhorn));
        int most = 0;
        for (int pass = 0; pass < 300; pass++) {
            single.particles.count = 0;
            single.ambience.bodyFire.update(PASS, rng);
            most = Math.max(most, single.ambience.bodyFire.particlesLastPass);
        }
        assertTrue(most <= BodyFireEffects.MAX_PER_BODY, "one body adds at most its cap: " + most);
        assertTrue(most >= 3, "and a burning thornhorn in the rain does give off plenty: " + most);

        // Near the ceiling it stops there, leaving blood and blast debris their slots.
        single.particles.count = ParticleSystem.SPLASH_LIMIT - 2;
        for (int pass = 0; pass < 20; pass++) {
            single.ambience.bodyFire.update(PASS, rng);
            assertTrue(single.particles.count <= ParticleSystem.SPLASH_LIMIT);
        }
    }

    @Test
    void aBodyInPiecesGivesOffNoMoreThanItDidWholeAndIsHeardOnce() {
        Game game = BodyFireArena.arena();
        Creature deer = burning(game, BodyFireArena.creature(game, CreatureType.DEER, 310.5f, 305.5f));
        int whole = particlesOver(game, 200, 11);
        deer.killBy(false);
        deer.recordBlastDeath(deer.pos.x, deer.pos.y + 0.5f, deer.pos.z + 1.5f, 2.6f);
        game.entities.creatures.remove(deer);
        game.fragments.spawnFromCreature(game, deer, game.fragments.deathPose(deer),
                deer.blastX, deer.blastY, deer.blastZ, deer.blastStrength);
        int pieces = game.fragments.liveCount();
        game.ambience.bodyFire.update(PASS, new Random(11));
        assertEquals(1, game.ambience.bodyFire.heardCount(), "the " + pieces + " pieces are heard as one fire");
        int apart = particlesOver(game, 200, 11);
        assertTrue(apart > 0, "the pieces burn on");
        assertTrue(apart <= whole, "and never give off more than the body did whole: " + apart + " vs " + whole);
        for (BodyFragment f : game.fragments.live) {
            assertTrue(f.burn == game.fragments.live.getFirst().burn, "one fire shared by every piece");
        }
    }

    @Test
    void aWorldBurnsPanicsAndDiesIdenticallyWithEveryEffectOff() {
        String withEffects = run(true);
        String without = run(false);
        assertEquals(withEffects, without, "presentation never reaches a burn, a panic, a death or a body");
    }

    @Test
    void aPassOverACrowdOnFireAllocatesNothing() {
        Game game = BodyFireArena.arena();
        for (int i = 0; i < 40; i++) {
            alight(game, BodyFireArena.person(game, NpcArchetype.GUARD, 292.5f + (i % 10) * 3f, 296.5f + (i / 10) * 3f));
        }
        CreatureType[] species = CreatureType.values();
        for (int i = 0; i < 35; i++) {
            alight(game, BodyFireArena.creature(game, species[i % species.length], 292.5f + (i % 12) * 3f,
                    312.5f + (i / 12) * 4f));
        }
        BodyFireArena.burn(game, 1.2f);
        Random rng = new Random(4);
        ThreadMXBean bean = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().threadId();
        long before = 0;
        for (int pass = 0; pass < 3_000; pass++) {
            if (pass == 1_000) {
                before = bean.getThreadAllocatedBytes(thread);
            }
            game.particles.count = 0;
            game.ambience.bodyFire.update(PASS, rng);
        }
        long perPass = (bean.getThreadAllocatedBytes(thread) - before) / 2_000;
        assertTrue(perPass < 64, "a pass over 75 burning bodies allocated " + perPass + " bytes");
    }

    // ------------------------------------------------------------------

    private static <T extends Entity> T burning(Game game, T body) {
        alight(game, body);
        BodyFireArena.burn(game, 1.2f);
        return body;
    }

    /** Sets a body alight without burning anyone yet, for a crowd lit together. */
    private static <T extends Entity> T alight(Game game, T body) {
        body.maxHealth = body.health = 500f;
        BodyFireArena.ignite(game, body, 1);
        return body;
    }

    private static int smokeOver(Game game, int passes) {
        Random rng = new Random(17);
        int smoke = 0;
        for (int pass = 0; pass < passes; pass++) {
            game.particles.count = 0;
            game.ambience.bodyFire.update(PASS, rng);
            for (int i = 0; i < game.particles.count; i++) {
                smoke += game.particles.kind[i] == ParticleSystem.KIND_HAZE && game.particles.cr[i] < 0.5f ? 1 : 0;
            }
        }
        return smoke;
    }

    private static int steamNear(Game game, Entity body, int passes) {
        Random rng = new Random(23);
        int steam = 0;
        for (int pass = 0; pass < passes; pass++) {
            game.particles.count = 0;
            game.ambience.bodyFire.update(PASS, rng);
            for (int i = 0; i < game.particles.count; i++) {
                boolean near = Math.abs(game.particles.px[i] - body.pos.x) < 1.5f
                        && Math.abs(game.particles.pz[i] - body.pos.z) < 1.5f;
                steam += near && game.particles.kind[i] == ParticleSystem.KIND_HAZE
                        && game.particles.cr[i] > 0.8f ? 1 : 0;
            }
            BodyFireArena.burn(game, PASS);
        }
        return steam;
    }

    private static int particlesOver(Game game, int passes, long seed) {
        Random rng = new Random(seed);
        int total = 0;
        for (int pass = 0; pass < passes; pass++) {
            game.particles.count = 0;
            game.ambience.bodyFire.update(PASS, rng);
            total += game.ambience.bodyFire.particlesLastPass;
        }
        return total;
    }

    /**
     * Twelve seconds of a burning crowd through the whole world step. With
     * effects on, every frame also builds the flames the renderer would draw;
     * with them off, the particle density is zero and no flames are built.
     */
    private static String run(boolean effects) {
        Game game = BodyFireArena.arena();
        game.particles.density = effects ? 1f : 0f;
        List<Entity> bodies = new ArrayList<>();
        bodies.add(BodyFireArena.person(game, NpcArchetype.GUARD, 305.5f, 305.5f));
        bodies.add(BodyFireArena.person(game, null, 308.5f, 300.5f));
        bodies.add(BodyFireArena.person(game, NpcArchetype.CAPTIVE, 314.5f, 304.5f));
        CreatureType[] species = CreatureType.values();
        for (int i = 0; i < species.length; i++) {
            bodies.add(BodyFireArena.creature(game, species[i], 302.5f + i * 3f, 312.5f));
        }
        BodyFlames flames = new BodyFlames();
        BodyFireLook look = new BodyFireLook();
        Matrix4f frame = new Matrix4f();
        for (int step = 0; step < 720; step++) {
            if (step == 30) {
                for (int i = 0; i < bodies.size(); i++) {
                    BodyFireArena.ignite(game, bodies.get(i), i + 1);
                }
            }
            game.advanceWorld(1.0 / 60.0);
            if (effects) {
                flames.begin(bodies.size(), 1f, 1f);
                for (Npc n : game.entities.npcs) {
                    ModelPart root = BodyPosing.npc(n, step / 60.0, frame);
                    flames.add(FlameAnchors.of(BodyFamily.HUMANOID), root, frame, look.living(n), 0, 1f, 10f, 0f, 0f);
                }
                for (Creature c : game.entities.creatures) {
                    ModelPart root = BodyPosing.creature(c, step / 60.0, frame);
                    flames.add(FlameAnchors.of(BodyFamily.of(c.type)), root, frame, look.living(c), 0, 1f, 10f, 0f, 0f);
                }
            }
        }
        StringBuilder state = new StringBuilder();
        for (Entity e : bodies) {
            state.append(e.getClass().getSimpleName()).append(' ')
                    .append(e.pos.x).append(',').append(e.pos.y).append(',').append(e.pos.z)
                    .append(" hp ").append(e.health).append(" dead ").append(e.dead)
                    .append(" burning ").append(e.combustion.burning()).append(" fuel ").append(e.combustion.fuel())
                    .append(" scorch ").append(e.combustion.scorch()).append('\n');
        }
        state.append("ragdolls ").append(game.ragdolls.live.size())
                .append(" corpses ").append(game.entities.corpses.size())
                .append(" carcasses ").append(game.entities.carcasses.size())
                .append(" residues ").append(game.burnResidues.trackedCount())
                .append(" panic ").append(game.entities.nextPanicFloat());
        return state.toString();
    }

    /** Records the body-fire sounds a headless game asks for. */
    private static final class SoundProbe extends AudioManager {
        int crackles, flares, sizzles;
        final List<float[]> crackleAt = new ArrayList<>();

        @Override
        public void playBodyCrackle(float x, float y, float z, float strength) {
            crackles++;
            crackleAt.add(new float[] {x, y, z});
        }

        @Override
        public void playBodyFlare(float x, float y, float z, float strength) {
            flares++;
        }

        @Override
        public void playBodySizzle(float x, float y, float z, float strength) {
            sizzles++;
        }
    }
}
