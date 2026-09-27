package com.veylon;

import com.veylon.engine.ParticleSystem;
import com.veylon.entity.BodyCombustion;
import com.veylon.entity.BodyFamily;
import com.veylon.entity.BodyFragment;
import com.veylon.entity.BurnResidue;
import com.veylon.entity.Carcass;
import com.veylon.entity.CombustionSystem;
import com.veylon.entity.Creature;
import com.veylon.entity.HumanCorpse;
import com.veylon.entity.Npc;
import com.veylon.entity.Player;
import com.veylon.entity.Ragdoll;
import com.veylon.gfx.BodyFireLook;
import com.veylon.gfx.BodyFlames;
import com.veylon.gfx.model.BodyPosing;
import com.veylon.gfx.model.FlameAnchors;
import com.veylon.gfx.model.ModelPart;
import org.joml.Matrix4f;

import java.util.List;
import java.util.Random;

/**
 * What burning bodies give off, and how they sound.
 *
 * <p>On each ambience pass every burning body near the player — the living,
 * the falling, the dead and the pieces of one blown apart — gives off tongues
 * of flame that lick up and away, embers, smoke that thickens as the flames
 * weaken, and steam while rain or water puts it out. Each leaves from an
 * anchor that is alight on the body as it is posed now ({@link FlameAnchors},
 * {@link BodyFlames#alight}) with the body's own velocity, and then settles
 * into the wind, so a running body trails its smoke. The player's own body
 * gives off nothing into the view: their fire is shown at the edges of the
 * screen and on the item in hand instead.
 *
 * <p>The nearest few burning bodies are heard: crackles as often as they burn
 * strongly, a rush of flame as one catches, a hiss as water or rain puts one
 * out, and the nearest takes over the fire loop when it is louder than any
 * burning block or campfire.
 *
 * <p>Presentation only. It reads the fires ({@link BodyFireLook}) and writes
 * particles and sound; nothing it does reaches a burn, a death, a panic or any
 * simulation random stream, and it runs only in simulated frames, so a paused
 * game gives off nothing. Bounded: bodies beyond {@link #REDUCED_RANGE} give
 * off nothing, each at most {@link #MAX_PER_BODY} particles a pass and all of
 * them together {@link #MAX_PER_PASS}, thinned evenly past {@link #FULL_BODIES},
 * and all of it stops at {@link ParticleSystem#SPLASH_LIMIT}, under blood and
 * blast debris. At most {@link #AUDIO_SOURCES} bodies are heard.
 */
final class BodyFireEffects {

    /** Within this distance a body gives off everything; beyond it half, up to {@link #REDUCED_RANGE}. */
    static final float FULL_RANGE = 48f, REDUCED_RANGE = 96f;
    /** Bodies within {@link #FULL_RANGE} given off in full before every body is thinned evenly. */
    static final int FULL_BODIES = 24;
    /** Most particles one body (or one piece) adds in a pass. */
    static final int MAX_PER_BODY = 6;
    /** Most particles every burning body together adds in a pass. */
    static final int MAX_PER_PASS = 96;
    /**
     * Particles a person-sized body gives off per pass at full strength:
     * licks of flame, embers, smoke and steam. A larger body gives off more,
     * a smaller less, by how many of its anchors a pass finds.
     */
    static final float LICKS = 1.4f, EMBERS = 0.3f, SMOKE = 0.9f, STEAM = 1.4f;
    /** Anchors of a person-sized body; a body's output scales with its anchors over this. */
    static final float REFERENCE_ANCHORS = 12f;
    /** Burning bodies heard at once, nearest first, and how far away they can be heard. */
    static final int AUDIO_SOURCES = 4;
    static final float AUDIBLE_RANGE = 24f;
    /** Crackles per second from one body, at its weakest and at its fullest. */
    static final float CRACKLES_WEAK = 1.5f, CRACKLES_FULL = 5f;
    /** A body is heard catching within this long of it, and hissing within this long of a douse. */
    static final float FLARE_WINDOW = 0.35f, SIZZLE_WINDOW = 0.35f;
    /** A burning body within this distance can take the fire loop. */
    static final float LOOP_RANGE = 8f;
    /** Bodies whose catching or douse has been heard, remembered so each is heard once; oldest forgotten. */
    static final int HEARD = 16;

    private final Game game;
    private final Matrix4f frame = new Matrix4f();
    private final float[] anchors = new float[FlameAnchors.MAX_ANCHORS * 4];
    private final BodyFireLook look = new BodyFireLook();

    private final float[] sourceDistance = new float[AUDIO_SOURCES];
    private final float[] sourceX = new float[AUDIO_SOURCES];
    private final float[] sourceY = new float[AUDIO_SOURCES];
    private final float[] sourceZ = new float[AUDIO_SOURCES];
    private final float[] sourceFlame = new float[AUDIO_SOURCES];
    private int sources;
    private int events;

    private final Object[] heardBody = new Object[HEARD];
    private final int[] heardEpisode = new int[HEARD];
    private final boolean[] heardDouse = new boolean[HEARD];
    private int heardNext;

    private float viewX, viewY, viewZ;
    private float crowd;
    private int passBudget;

    /** The loudest burning body for the fire loop this pass: its gain 0..1 and position. */
    float loopGain, loopX, loopY, loopZ;
    /** Diagnostics: particles added and bodies heard in the last pass, and sounds so far. */
    int particlesLastPass, heardLastPass;
    int crackles, flares, sizzles;

    BodyFireEffects(Game game) {
        this.game = game;
    }

    /** Forgets every body heard and the loop; a new world starts silent. */
    void reset() {
        java.util.Arrays.fill(heardBody, null);
        heardNext = 0;
        sources = 0;
        loopGain = 0f;
        particlesLastPass = heardLastPass = 0;
        crackles = flares = sizzles = 0;
    }

    /** One pass, {@code seconds} after the last; {@code rng} is presentation-only. */
    void update(float seconds, Random rng) {
        Player p = game.player;
        viewX = p.pos.x;
        viewY = p.pos.y + p.eyeHeight();
        viewZ = p.pos.z;
        sources = 0;
        events = 0;
        loopGain = 0f;
        particlesLastPass = 0;
        passBudget = MAX_PER_PASS;
        int near = countNear();
        crowd = near > FULL_BODIES ? (float) FULL_BODIES / near : 1f;

        List<Creature> creatures = game.entities.creatures;
        for (int i = 0; i < creatures.size(); i++) {
            Creature c = creatures.get(i);
            if (c.dead || !look.living(c).active()) {
                continue;
            }
            hearLiving(c, c.combustion, c.pos.x, c.pos.y + c.height * 0.5f, c.pos.z);
            float s = share(c.pos.x, c.pos.y, c.pos.z, 1f);
            if (s > 0f) {
                ModelPart root = BodyPosing.creature(c, game.totalTime, frame);
                emit(FlameAnchors.of(BodyFamily.of(c.type)), root, 0, s, c.vel.x, c.vel.y, c.vel.z, rng);
            }
        }
        List<Npc> npcs = game.entities.npcs;
        for (int i = 0; i < npcs.size(); i++) {
            Npc n = npcs.get(i);
            if (n.dead || !look.living(n).active()) {
                continue;
            }
            hearLiving(n, n.combustion, n.pos.x, n.pos.y + n.height * 0.5f, n.pos.z);
            float s = share(n.pos.x, n.pos.y, n.pos.z, 1f);
            if (s > 0f) {
                ModelPart root = BodyPosing.npc(n, game.totalTime, frame);
                emit(FlameAnchors.of(BodyFamily.HUMANOID), root, 0, s, n.vel.x, n.vel.y, n.vel.z, rng);
            }
        }
        // The player is heard burning, close by; nothing is given off into their own view.
        if (!p.dead && CombustionSystem.canBurn(p) && look.living(p).active()) {
            hearLiving(p, p.combustion, p.pos.x, p.pos.y + p.height * 0.5f, p.pos.z);
        }
        List<Ragdoll> ragdolls = game.ragdolls.live;
        for (int i = 0; i < ragdolls.size(); i++) {
            Ragdoll r = ragdolls.get(i);
            float x = r.px[Ragdoll.TORSO], y = r.py[Ragdoll.TORSO], z = r.pz[Ragdoll.TORSO];
            if (!remains(r.burn, x, y, z, true)) {
                continue;
            }
            float s = share(x, y, z, 1f);
            if (s > 0f) {
                ModelPart root = BodyPosing.ragdoll(r, frame);
                emit(FlameAnchors.of(BodyPosing.family(r)), root, 0, s,
                        r.vx[Ragdoll.TORSO], r.vy[Ragdoll.TORSO], r.vz[Ragdoll.TORSO], rng);
            }
        }
        List<HumanCorpse> corpses = game.entities.corpses;
        for (int i = 0; i < corpses.size(); i++) {
            HumanCorpse c = corpses.get(i);
            if (remains(c.burn, c.pos.x, c.pos.y, c.pos.z, true)) {
                float s = share(c.pos.x, c.pos.y, c.pos.z, 1f);
                if (s > 0f) {
                    emit(FlameAnchors.of(BodyFamily.HUMANOID), BodyPosing.corpse(c, frame), 0, s, 0f, 0f, 0f, rng);
                }
            }
        }
        List<Carcass> carcasses = game.entities.carcasses;
        for (int i = 0; i < carcasses.size(); i++) {
            Carcass c = carcasses.get(i);
            if (!c.fragmented() && remains(c.burn, c.pos.x, c.pos.y, c.pos.z, true)) {
                float s = share(c.pos.x, c.pos.y, c.pos.z, 1f);
                if (s > 0f) {
                    emit(FlameAnchors.of(BodyFamily.of(c.type)), BodyPosing.carcass(c, frame), 0, s,
                            0f, 0f, 0f, rng);
                }
            }
        }
        pieces(game.fragments.live, rng);
        pieces(game.fragments.settled, rng);
        heardLastPass = sources;
        crackle(seconds, rng);
    }

    /** The pieces of bodies blown apart: each at its share of its body, the core piece heard. */
    private void pieces(List<BodyFragment> list, Random rng) {
        for (int i = 0; i < list.size(); i++) {
            BodyFragment f = list.get(i);
            if (!remains(f.burn, f.pos.x, f.pos.y, f.pos.z, f.definition.parent == -1)) {
                continue;
            }
            float s = share(f.pos.x, f.pos.y, f.pos.z, f.burnShare);
            if (s > 0f) {
                ModelPart root = BodyPosing.fragment(f, frame);
                emit(FlameAnchors.of(f.definition.family), root, f.definition.id + 1, s,
                        f.vel.x, f.vel.y, f.vel.z, rng);
            }
        }
    }

    /** Reads the remains' fire into {@link #look}; the carrier holding its core is heard. */
    private boolean remains(BurnResidue r, float x, float y, float z, boolean heard) {
        if (r == null || !look.remains(r).active()) {
            return false;
        }
        if (heard) {
            listen(x, y, z, look.flame);
            float after = r.age() - r.flameSeconds();
            if (r.doused() && after >= 0f && after <= SIZZLE_WINDOW && remember(r, 0, true)) {
                sizzle(x, y, z, r.flameAtDeath());
            }
        }
        return true;
    }

    /** How strongly a body at this distance gives off, times its share of its body; 0 beyond range. */
    private float share(float x, float y, float z, float bodyShare) {
        float d = distance(x, y, z);
        if (d > REDUCED_RANGE) {
            return 0f;
        }
        return (d <= FULL_RANGE ? 1f : 0.5f) * crowd * bodyShare;
    }

    /** Burning bodies and remains within {@link #FULL_RANGE}, pieces by share, for even thinning. */
    private int countNear() {
        float n = 0f;
        for (int i = 0; i < game.entities.creatures.size(); i++) {
            Creature c = game.entities.creatures.get(i);
            n += !c.dead && c.combustion.burning() && distance(c.pos.x, c.pos.y, c.pos.z) <= FULL_RANGE ? 1f : 0f;
        }
        for (int i = 0; i < game.entities.npcs.size(); i++) {
            Npc c = game.entities.npcs.get(i);
            n += !c.dead && c.combustion.burning() && distance(c.pos.x, c.pos.y, c.pos.z) <= FULL_RANGE ? 1f : 0f;
        }
        for (int i = 0; i < game.ragdolls.live.size(); i++) {
            Ragdoll r = game.ragdolls.live.get(i);
            n += near(r.burn, r.px[Ragdoll.TORSO], r.py[Ragdoll.TORSO], r.pz[Ragdoll.TORSO]) ? 1f : 0f;
        }
        for (int i = 0; i < game.entities.corpses.size(); i++) {
            HumanCorpse c = game.entities.corpses.get(i);
            n += near(c.burn, c.pos.x, c.pos.y, c.pos.z) ? 1f : 0f;
        }
        for (int i = 0; i < game.entities.carcasses.size(); i++) {
            Carcass c = game.entities.carcasses.get(i);
            n += !c.fragmented() && near(c.burn, c.pos.x, c.pos.y, c.pos.z) ? 1f : 0f;
        }
        for (int i = 0; i < game.fragments.live.size(); i++) {
            BodyFragment f = game.fragments.live.get(i);
            n += near(f.burn, f.pos.x, f.pos.y, f.pos.z) ? f.burnShare : 0f;
        }
        for (int i = 0; i < game.fragments.settled.size(); i++) {
            BodyFragment f = game.fragments.settled.get(i);
            n += near(f.burn, f.pos.x, f.pos.y, f.pos.z) ? f.burnShare : 0f;
        }
        return Math.round(n);
    }

    private boolean near(BurnResidue r, float x, float y, float z) {
        return r != null && r.flame() > 0f && distance(x, y, z) <= FULL_RANGE;
    }

    /**
     * Gives off one body's (or piece's) particles from its alight anchors, as
     * posed from {@code root} in {@link #frame}; {@code scale} is its range,
     * crowd and piece share.
     */
    private void emit(FlameAnchors fa, ModelPart root, int pieceSeed, float scale,
                      float velX, float velY, float velZ, Random rng) {
        ParticleSystem ps = game.particles;
        int n = fa.sample(root, frame, anchors);
        if (n == 0 || passBudget <= 0 || ps.count >= ParticleSystem.SPLASH_LIMIT) {
            return;
        }
        float size = scale * Math.min(2f, Math.max(0.4f, n / REFERENCE_ANCHORS));
        float shift = BodyFlames.shift(look, pieceSeed);
        float puff = 0.18f + fa.largestWidth() * 0.9f;
        int budget = Math.min(MAX_PER_BODY, passBudget);
        int added = 0;
        for (int k = count(LICKS * look.flame * size, rng); k > 0 && added < budget; k--) {
            int i = litAnchor(fa, n, shift, rng);
            if (i >= 0 && ps.bodyLick(x(i), y(i), z(i), velX * 0.7f, velY * 0.7f, velZ * 0.7f,
                    fa.width((int) anchors[i * 4]) * (0.5f + 0.5f * look.flame), look.flame)) {
                added++;
            }
        }
        for (int k = count(EMBERS * look.embers * size, rng); k > 0 && added < budget; k--) {
            int i = litAnchor(fa, n, shift, rng);
            if (i >= 0 && ps.bodyEmber(x(i), y(i), z(i), velX, velY, velZ)) {
                added++;
            }
        }
        for (int k = count(SMOKE * look.smoke * size, rng); k > 0 && added < budget; k--) {
            int i = rng.nextInt(n);
            if (ps.bodySmoke(x(i), y(i) + 0.1f, z(i), velX * 0.9f, velY * 0.9f, velZ * 0.9f, puff, look.flame)) {
                added++;
            }
        }
        for (int k = count(STEAM * look.steam * size, rng); k > 0 && added < budget; k--) {
            int i = rng.nextInt(n);
            if (ps.bodySteam(x(i), y(i) + 0.05f, z(i), velX * 0.6f, velY * 0.6f, velZ * 0.6f, puff * 0.8f)) {
                added++;
            }
        }
        passBudget -= added;
        particlesLastPass += added;
    }

    /** A sampled anchor that is alight, tried twice at random; -1 if neither was. */
    private int litAnchor(FlameAnchors fa, int n, float shift, Random rng) {
        for (int attempt = 0; attempt < 2; attempt++) {
            int i = rng.nextInt(n);
            if (BodyFlames.alight(fa, (int) anchors[i * 4], x(i), y(i), z(i), look, shift, 1f)) {
                return i;
            }
        }
        return -1;
    }

    private float x(int i) {
        return anchors[i * 4 + 1];
    }

    private float y(int i) {
        return anchors[i * 4 + 2];
    }

    private float z(int i) {
        return anchors[i * 4 + 3];
    }

    /** A whole number of particles whose average is {@code expected}. */
    private static int count(float expected, Random rng) {
        if (!(expected > 0f)) {
            return 0;
        }
        int k = (int) expected;
        return rng.nextFloat() < expected - k ? k + 1 : k;
    }

    // ------------------------------------------------------------------
    // Sound
    // ------------------------------------------------------------------

    /** A living body is heard: catching, a douse, and its crackle while it burns. */
    private void hearLiving(Object body, BodyCombustion b, float x, float y, float z) {
        if (distance(x, y, z) > AUDIBLE_RANGE) {
            return;
        }
        if (b.burning() && b.burnSeconds() <= FLARE_WINDOW && remember(body, b.episodes(), false)) {
            if (events++ < AUDIO_SOURCES) {
                game.audio.playBodyFlare(x, y, z, b.intensity());
                flares++;
            }
        } else if (!b.burning() && b.outDoused() && b.outSeconds() <= SIZZLE_WINDOW
                && remember(body, b.episodes(), true)) {
            sizzle(x, y, z, b.outIntensity());
        }
        listen(x, y, z, look.flame);
    }

    private void sizzle(float x, float y, float z, float strength) {
        if (events++ < AUDIO_SOURCES) {
            game.audio.playBodySizzle(x, y, z, strength);
            sizzles++;
        }
    }

    /** Keeps the body among the nearest {@link #AUDIO_SOURCES} burning ones, if it is. */
    private void listen(float x, float y, float z, float flame) {
        float d = distance(x, y, z);
        if (flame <= 0f || d > AUDIBLE_RANGE) {
            return;
        }
        float gain = d < LOOP_RANGE ? flame * (1f - d / LOOP_RANGE) : 0f;
        if (gain > loopGain) {
            loopGain = gain;
            loopX = x;
            loopY = y;
            loopZ = z;
        }
        int at;
        if (sources < AUDIO_SOURCES) {
            at = sources++;
        } else if (d < sourceDistance[AUDIO_SOURCES - 1]) {
            at = AUDIO_SOURCES - 1;
        } else {
            return;
        }
        while (at > 0 && sourceDistance[at - 1] > d) {
            sourceDistance[at] = sourceDistance[at - 1];
            sourceX[at] = sourceX[at - 1];
            sourceY[at] = sourceY[at - 1];
            sourceZ[at] = sourceZ[at - 1];
            sourceFlame[at] = sourceFlame[at - 1];
            at--;
        }
        sourceDistance[at] = d;
        sourceX[at] = x;
        sourceY[at] = y;
        sourceZ[at] = z;
        sourceFlame[at] = flame;
    }

    /** Each heard body crackles as often as it burns strongly. */
    private void crackle(float seconds, Random rng) {
        for (int i = 0; i < sources; i++) {
            float rate = CRACKLES_WEAK + (CRACKLES_FULL - CRACKLES_WEAK) * sourceFlame[i];
            if (rng.nextFloat() < rate * seconds) {
                game.audio.playBodyCrackle(sourceX[i], sourceY[i], sourceZ[i], sourceFlame[i]);
                crackles++;
            }
        }
    }

    /** Whether this body's catch or douse ({@code douse}) of this episode is new; if so it is remembered. */
    private boolean remember(Object body, int episode, boolean douse) {
        for (int i = 0; i < HEARD; i++) {
            if (heardBody[i] == body && heardEpisode[i] == episode && heardDouse[i] == douse) {
                return false;
            }
        }
        heardBody[heardNext] = body;
        heardEpisode[heardNext] = episode;
        heardDouse[heardNext] = douse;
        heardNext = (heardNext + 1) % HEARD;
        return true;
    }

    /** Burning bodies heard this pass, nearest first. */
    int heardCount() {
        return sources;
    }

    private float distance(float x, float y, float z) {
        float dx = x - viewX, dy = y - viewY, dz = z - viewZ;
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
