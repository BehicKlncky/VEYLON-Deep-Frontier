package com.veylon.engine;

import com.veylon.world.BlockType;
import org.lwjgl.BufferUtils;
import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;

import java.nio.ShortBuffer;
import java.util.Random;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Real OpenAL audio. Every sound is synthesized at startup (no assets):
 * filtered-noise impacts and footsteps, FM chirps, sine-sweep howls, plus
 * seamless ambience loops (rain, wind, fire, cave drone, night crickets,
 * beacon hum) that crossfade with the world state.
 *
 * <p>If no audio device is available (headless smoke test, CI) the manager
 * disables itself and every call becomes a no-op.</p>
 */
public class AudioManager {

    private static final float MASTER = 0.85f;

    private long device;
    private long context;
    private boolean enabled, nativeReady;
    private final EfxProcessor effects = new EfxProcessor();
    private AcousticSources acoustics;
    private final Random rng = new Random();

    // One-shot buffers.
    private int bFootGrass, bFootStone, bFootWood, bFootSnow, bFootWater;
    private int bHitSoft, bHitStone, bHitWood;
    private int bBreak, bPlace, bClick, bEat, bDrink, bHit, bHurt, bSwing, bCraft, bEquip;
    private int bToolBreak, bHowl, bGrowl, bChirp, bFlap, bDeer, bCough, bSleep;
    private int bDiscover, bQuest, bThunder, bBoil;
    // 0.3.0 combat & settlement sounds (all synthesized, like everything else).
    private int bBowDraw, bBowRelease, bArrowImpact, bBulletImpact, bMusket, bPistol;
    private int bDryFire, bReload, bFuse, bExplosion, bAlarmBell, bGate;

    // Ambience loops.
    private int bRain, bWind, bFire, bCave, bCrickets, bBeacon, bRainHigh, bWindHigh;
    private final int[] detailBuffers = new int[4];
    private final AmbientEvents ambientEvents = new AmbientEvents(rng);
    private float weatherIntensity, currentIntensity;
    private final java.util.function.IntConsumer detailPlayer = this::playAmbientDetail;
    private java.util.Map<String, Integer> buffers;
    private float fireX, fireY, fireZ;
    private boolean firePositioned;
    private final float[] ambTarget = new float[6];
    private final float[] ambCurrent = new float[6];
    private final int[] ambSources = new int[SpatialAmbience.EMITTERS.length];
    private static final float[] AMBIENCE_LEVELS = {0.65f, 0.5f, 0.55f, 0.4f, 0.3f, 0.35f};
    private final float[] listenerOrientation = new float[6];

    private long pcmBytes;
    private double synthesisMs;
    private long updateCount, updateNanos, maxUpdateNanos;
    private int nativeErrors;

    private int[] pool;
    private int poolNext;

    public void init() {
        if (enabled) return;
        try {
            device = alcOpenDevice((CharSequence) null);
            if (device == NULL) {
                System.out.println("[audio] No audio device; sound disabled.");
                return;
            }
            context = alcCreateContext(device, (int[]) null);
            if (context == NULL || !alcMakeContextCurrent(context)) throw new IllegalStateException("No audio context");
            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            AL.createCapabilities(alcCaps);
            nativeReady = true;
            effects.init(alcCaps.ALC_EXT_EFX && !"1".equals(System.getenv("VEYLON_NO_EFX")));
            alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);
            alListenerf(AL_GAIN, MASTER);

            synthesizeAll();

            pool = new int[16];
            for (int i = 0; i < pool.length; i++) {
                pool[i] = alGenSources();
                effects.route(pool[i], true);
                alSourcef(pool[i], AL_REFERENCE_DISTANCE, 3f);
                alSourcef(pool[i], AL_MAX_DISTANCE, 44f);
                alSourcef(pool[i], AL_ROLLOFF_FACTOR, 1.1f);
            }
            for (int i = 0; i < ambSources.length; i++) {
                var emitter = SpatialAmbience.EMITTERS[i];
                ambSources[i] = makeLoop(buffers.get(emitter.name()), emitter);
            }
            acoustics = new AcousticSources(effects.enabled(), pool, ambSources);
            if (alGetError() != AL_NO_ERROR) throw new IllegalStateException("OpenAL initialization error");
            enabled = true;
            System.out.println("[audio] OpenAL initialized (" + pool.length + " voices).");
        } catch (Throwable t) {
            System.out.println("[audio] Init failed (" + t + "); sound disabled.");
            shutdown();
        }
    }

    private int makeLoop(int buffer, SpatialAmbience.Emitter emitter) {
        int src = alGenSources();
        alSourcei(src, AL_BUFFER, buffer);
        alSourcei(src, AL_LOOPING, AL_TRUE);
        alSourcei(src, AL_SOURCE_RELATIVE, emitter.relative() ? AL_TRUE : AL_FALSE);
        alSource3f(src, AL_POSITION, emitter.x(), emitter.y(), emitter.z());
        alSourcef(src, AL_REFERENCE_DISTANCE, SpatialAmbience.FIRE_REFERENCE_DISTANCE);
        alSourcef(src, AL_ROLLOFF_FACTOR, 0);
        alSourcef(src, AL_GAIN, 0f);
        effects.route(src, true);
        alSourcePlay(src);
        return src;
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void setListener(float x, float y, float z, float yawDeg) {
        if (!enabled) {
            return;
        }
        alListener3f(AL_POSITION, x, y, z);
        acoustics.listener(x, y, z);
        float yaw = (float) Math.toRadians(yawDeg);
        float fx = (float) Math.sin(yaw), fz = -(float) Math.cos(yaw);
        listenerOrientation[0] = fx;
        listenerOrientation[1] = 0;
        listenerOrientation[2] = fz;
        listenerOrientation[3] = 0;
        listenerOrientation[4] = 1;
        listenerOrientation[5] = 0;
        alListenerfv(AL_ORIENTATION, listenerOrientation);
    }

    /**
     * Sets target gains for the ambience layers
     * (rain, wind, fire, cave, crickets, beacon); call once per medium tick.
     */
    public void setAmbience(float rain, float wind, float fire, float cave,
                            float crickets, float beacon) {
        if (!enabled) return;
        ambTarget[0] = rain;
        ambTarget[1] = wind;
        ambTarget[2] = fire;
        ambTarget[3] = cave;
        ambTarget[4] = crickets;
        ambTarget[5] = beacon;
    }

    /** Smooths ambience crossfades; call every frame. */
    public void update(float dt) {
        if (!enabled) {
            return;
        }
        long start = System.nanoTime();
        float blend = (float) -Math.expm1(-Math.max(0, dt) * 1.5f);
        currentIntensity += (weatherIntensity - currentIntensity) * blend;
        for (int i = 0; i < 6; i++) ambCurrent[i] += (ambTarget[i] - ambCurrent[i]) * blend;
        ambientEvents.update(dt, ambCurrent, detailPlayer);
        effects.update(dt);
        acoustics.update(dt);
        for (int i = 0; i < ambSources.length; i++) {
            int layer = SpatialAmbience.EMITTERS[i].layer();
            int channel = SpatialAmbience.channel(layer);
            float gain = ambCurrent[channel] * AMBIENCE_LEVELS[channel]
                    * AmbientEvents.layerWeight(layer, currentIntensity, ambientEvents.gust()) * SpatialAmbience.allocation(layer);
            alSourcef(ambSources[i], AL_GAIN, gain);
        }
        pollErrors();
        long nanos = System.nanoTime() - start;
        updateCount++;
        updateNanos += nanos;
        maxUpdateNanos = Math.max(maxUpdateNanos, nanos);
    }

    /** Sets weather timbre independently of the already-derived ambience channel gains. */
    public void setWeatherIntensity(float intensity) {
        if (!enabled) return;
        weatherIntensity = Math.max(0, Math.min(1, intensity));
    }

    /** Cancels scene-local details and mix state before a world is replaced or abandoned. */
    public void resetWorld() {
        if (!enabled) return;
        ambientEvents.reset();
        effects.reset();
        acoustics.reset();
        firePositioned = false;
        java.util.Arrays.fill(ambTarget, 0);
        java.util.Arrays.fill(ambCurrent, 0);
        weatherIntensity = currentIntensity = 0;
        for (int source : ambSources) alSourcef(source, AL_GAIN, 0);
        for (int source : pool) alSourceStop(source);
    }

    private void playAmbientDetail(int kind) {
        int channel = switch (kind) { case 0 -> 0; case 1 -> 2; case 2 -> 3; default -> 4; };
        if (kind == 1 && firePositioned) {
            playAt(detailBuffers[kind], fireX, fireY, fireZ, ambCurrent[channel] * 0.35f, pitchVar(0.15f));
        } else if (kind != 1) {
            play2d(detailBuffers[kind], ambCurrent[channel] * 0.35f, pitchVar(0.15f));
        }
    }

    /** True only after native initialization completed successfully. */
    public boolean isEnabled() { return enabled; }

    /** Requests a smoothly interpolated environment preset; dry devices safely ignore it. */
    public void setEnvironment(AudioEnvironment.Zone zone) {
        if (!enabled) return;
        effects.zone(zone);
    }

    /** Places fire ambience at the nearest audible heat source in world coordinates. */
    public void setFirePosition(float x, float y, float z) {
        if (!enabled) return;
        fireX = x; fireY = y; fireZ = z;
        firePositioned = true;
        alSource3f(ambSources[2], AL_POSITION, x, y, z);
        acoustics.position(ambSources[2], x, y, z, false);
    }

    /** Associates occlusion queries with the newly constructed world; never generates chunks. */
    public void bindWorld(com.veylon.world.World world) {
        if (!enabled) return;
        acoustics.world(world);
    }

    public void playFootstep(BlockType under, boolean inWater) {
        if (!enabled) return;
        if (inWater) {
            play2d(bFootWater, 0.4f, pitchVar(0.2f));
            return;
        }
        int buf = switch (under) {
            case STONE, GRAVEL, COAL_ORE, COPPER_ORE, IRON_ORE, RUIN_STONE, FURNACE, ASH,
                 SCRAP_BLOCK, POD_HULL, ICE -> bFootStone;
            case PLANK, LOG, WORKBENCH, CRATE, WALL, MAP_TABLE, HERB_STATION -> bFootWood;
            case SNOW -> bFootSnow;
            default -> bFootGrass;
        };
        play2d(buf, 0.35f, pitchVar(0.25f));
    }

    public void playBlockHit(BlockType t, float x, float y, float z) {
        if (!enabled) return;
        int buf = switch (t.preferredTool) {
            case PICKAXE -> bHitStone;
            case AXE -> bHitWood;
            default -> bHitSoft;
        };
        playAt(buf, x, y, z, 0.5f, pitchVar(0.2f));
    }

    public void playBlockBreak(float x, float y, float z) {
        if (!enabled) return;
        playAt(bBreak, x, y, z, 0.7f, pitchVar(0.15f));
    }

    public void playBlockPlace(float x, float y, float z) {
        if (!enabled) return;
        playAt(bPlace, x, y, z, 0.6f, pitchVar(0.15f));
    }

    public void playClick() {
        if (!enabled) return;
        play2d(bClick, 0.5f, 1f);
    }

    public void playEat() {
        if (!enabled) return;
        play2d(bEat, 0.6f, pitchVar(0.1f));
    }

    public void playDrink() {
        if (!enabled) return;
        play2d(bDrink, 0.6f, pitchVar(0.1f));
    }

    public void playBoil() {
        if (!enabled) return;
        play2d(bBoil, 0.55f, 1f);
    }

    public void playHit() {
        if (!enabled) return;
        play2d(bHit, 0.7f, pitchVar(0.15f));
    }

    public void playHurt() {
        if (!enabled) return;
        play2d(bHurt, 0.65f, pitchVar(0.12f));
    }

    public void playSwing() {
        if (!enabled) return;
        play2d(bSwing, 0.35f, pitchVar(0.2f));
    }

    public void playCraft() {
        if (!enabled) return;
        play2d(bCraft, 0.6f, 1f);
    }

    public void playEquip() {
        if (!enabled) return;
        play2d(bEquip, 0.6f, 1f);
    }

    public void playToolBreak() {
        if (!enabled) return;
        play2d(bToolBreak, 0.8f, 1f);
    }

    public void playCough() {
        if (!enabled) return;
        play2d(bCough, 0.6f, pitchVar(0.1f));
    }

    public void playSleep() {
        if (!enabled) return;
        play2d(bSleep, 0.5f, 1f);
    }

    public void playDiscover() {
        if (!enabled) return;
        play2d(bDiscover, 0.7f, 1f);
    }

    public void playQuest() {
        if (!enabled) return;
        play2d(bQuest, 0.7f, 1f);
    }

    public void playThunder() {
        if (!enabled) return;
        play2d(bThunder, 0.9f, pitchVar(0.2f));
    }

    public void playHowl(float x, float y, float z) {
        if (!enabled) return;
        playAt(bHowl, x, y, z, 0.85f, pitchVar(0.1f));
    }

    public void playGrowl(float x, float y, float z) {
        if (!enabled) return;
        playAt(bGrowl, x, y, z, 0.7f, pitchVar(0.15f));
    }

    public void playChirp(float x, float y, float z) {
        if (!enabled) return;
        playAt(bChirp, x, y, z, 0.45f, pitchVar(0.25f));
    }

    public void playBirdFlap(float x, float y, float z) {
        if (!enabled) return;
        playAt(bFlap, x, y, z, 0.45f, pitchVar(0.2f));
    }

    public void playDeerCall(float x, float y, float z) {
        if (!enabled) return;
        playAt(bDeer, x, y, z, 0.6f, pitchVar(0.1f));
    }

    // ---- 0.3.0 combat & settlement one-shots ----

    public void playBowDraw() {
        if (!enabled) return;
        play2d(bBowDraw, 0.45f, pitchVar(0.1f));
    }

    public void playBowRelease(float x, float y, float z) {
        if (!enabled) return;
        playAt(bBowRelease, x, y, z, 0.55f, pitchVar(0.12f));
    }

    public void playArrowImpact(float x, float y, float z) {
        if (!enabled) return;
        playAt(bArrowImpact, x, y, z, 0.5f, pitchVar(0.2f));
    }

    public void playBulletImpact(float x, float y, float z) {
        if (!enabled) return;
        playAt(bBulletImpact, x, y, z, 0.5f, pitchVar(0.2f));
    }

    /** Gunshots carry much farther than ordinary sounds. */
    public void playGunshot(boolean pistol, float x, float y, float z) {
        if (!enabled) return;
        playAtFar(pistol ? bPistol : bMusket, x, y, z, 0.95f, pitchVar(0.08f));
    }

    public void playDryFire() {
        if (!enabled) return;
        play2d(bDryFire, 0.5f, pitchVar(0.1f));
    }

    public void playReload() {
        if (!enabled) return;
        play2d(bReload, 0.55f, pitchVar(0.08f));
    }

    public void playFuse(float x, float y, float z) {
        if (!enabled) return;
        playAt(bFuse, x, y, z, 0.6f, pitchVar(0.1f));
    }

    public void playExplosion(float x, float y, float z) {
        if (!enabled) return;
        playAtFar(bExplosion, x, y, z, 1.0f, pitchVar(0.1f));
    }

    public void playAlarmBell(float x, float y, float z) {
        if (!enabled) return;
        playAtFar(bAlarmBell, x, y, z, 0.85f, pitchVar(0.05f));
    }

    public void playGate(float x, float y, float z) {
        if (!enabled) return;
        playAt(bGate, x, y, z, 0.6f, pitchVar(0.12f));
    }

    /** Releases all native resources, including a partially initialized device. */
    public void shutdown() {
        enabled = false;
        if (nativeReady) {
            if (pool != null) for (int source : pool) if (source != 0) alDeleteSources(source);
            for (int source : ambSources) if (source != 0) alDeleteSources(source);
            if (acoustics != null) { acoustics.close(); acoustics = null; }
            effects.close();
            if (buffers != null) for (int buffer : buffers.values()) alDeleteBuffers(buffer);
            pollErrors();
            System.out.printf("[audio] updates=%d meanUpdateMs=%.6f maxUpdateMs=%.6f nativeErrors=%d%n",
                    updateCount, updateCount == 0 ? 0 : updateNanos / (double) updateCount / 1e6,
                    maxUpdateNanos / 1e6, nativeErrors);
            java.util.Arrays.fill(ambSources, 0);
            pool = null;
            buffers = null;
            nativeReady = false;
        }
        if (context != NULL) {
            alcMakeContextCurrent(NULL);
            alcDestroyContext(context);
            context = NULL;
        }
        if (device != NULL) { alcCloseDevice(device); device = NULL; }
    }

    private void pollErrors() {
        int error = alGetError();
        if (error != AL_NO_ERROR && nativeErrors++ == 0) {
            System.err.println("[audio] OpenAL error 0x" + Integer.toHexString(error));
        }
    }

    // ------------------------------------------------------------------
    // Playback plumbing
    // ------------------------------------------------------------------

    private float pitchVar(float spread) {
        return 1f + (rng.nextFloat() * 2f - 1f) * spread;
    }

    private int grabSource() {
        for (int i = 0; i < pool.length; i++) {
            int src = pool[(poolNext + i) % pool.length];
            if (alGetSourcei(src, AL_SOURCE_STATE) != AL_PLAYING) {
                poolNext = (poolNext + i + 1) % pool.length;
                return src;
            }
        }
        return -1; // all voices busy; drop the sound
    }

    private void playAt(int buffer, float x, float y, float z, float gain, float pitch) {
        playAt(buffer, x, y, z, gain, pitch, 3f, 44f);
    }

    /** Long-carry variant for gunshots, explosions and alarm bells. */
    private void playAtFar(int buffer, float x, float y, float z, float gain, float pitch) {
        playAt(buffer, x, y, z, gain, pitch, 14f, 220f);
    }

    private void playAt(int buffer, float x, float y, float z, float gain, float pitch,
                        float refDist, float maxDist) {
        if (!enabled) {
            return;
        }
        int src = grabSource();
        if (src < 0) {
            return;
        }
        alSourceStop(src);
        alSourcei(src, AL_BUFFER, buffer);
        alSourcei(src, AL_SOURCE_RELATIVE, AL_FALSE);
        effects.route(src, true);
        alSource3f(src, AL_POSITION, x, y, z);
        acoustics.position(src, x, y, z, false);
        alSourcef(src, AL_GAIN, gain);
        alSourcef(src, AL_PITCH, pitch);
        alSourcef(src, AL_REFERENCE_DISTANCE, refDist);
        alSourcef(src, AL_MAX_DISTANCE, maxDist);
        alSourcePlay(src);
    }

    private void play2d(int buffer, float gain, float pitch) {
        if (!enabled) {
            return;
        }
        int src = grabSource();
        if (src < 0) {
            return;
        }
        alSourceStop(src);
        alSourcei(src, AL_BUFFER, buffer);
        alSourcei(src, AL_SOURCE_RELATIVE, AL_TRUE);
        effects.route(src, buffer != bClick && buffer != bQuest && buffer != bDiscover);
        alSource3f(src, AL_POSITION, 0, 0, 0);
        acoustics.position(src, 0, 0, 0, true);
        alSourcef(src, AL_GAIN, gain);
        alSourcef(src, AL_PITCH, pitch);
        alSourcePlay(src);
    }

    // ------------------------------------------------------------------
    // Synthesis
    // ------------------------------------------------------------------

    private void synthesizeAll() {
        long start = System.nanoTime();
        pcmBytes = 0;
        java.util.Map<String, Integer> bank = new java.util.HashMap<>();
        buffers = bank;
        new ProceduralAudio(rng).synthesize((name, samples) -> bank.put(name, upload(samples)));
        bFootGrass = bank.get("FootGrass");
        bFootStone = bank.get("FootStone");
        bFootWood = bank.get("FootWood");
        bFootSnow = bank.get("FootSnow");
        bFootWater = bank.get("FootWater");
        bHitSoft = bank.get("HitSoft");
        bHitStone = bank.get("HitStone");
        bHitWood = bank.get("HitWood");
        bBreak = bank.get("Break");
        bPlace = bank.get("Place");
        bClick = bank.get("Click");
        bEat = bank.get("Eat");
        bDrink = bank.get("Drink");
        bBoil = bank.get("Boil");
        bHit = bank.get("Hit");
        bHurt = bank.get("Hurt");
        bSwing = bank.get("Swing");
        bCraft = bank.get("Craft");
        bEquip = bank.get("Equip");
        bToolBreak = bank.get("ToolBreak");
        bCough = bank.get("Cough");
        bSleep = bank.get("Sleep");
        bDiscover = bank.get("Discover");
        bQuest = bank.get("Quest");
        bThunder = bank.get("Thunder");
        bHowl = bank.get("Howl");
        bGrowl = bank.get("Growl");
        bChirp = bank.get("Chirp");
        bFlap = bank.get("Flap");
        bDeer = bank.get("Deer");
        bBowDraw = bank.get("BowDraw");
        bBowRelease = bank.get("BowRelease");
        bArrowImpact = bank.get("ArrowImpact");
        bBulletImpact = bank.get("BulletImpact");
        bMusket = bank.get("Musket");
        bPistol = bank.get("Pistol");
        bDryFire = bank.get("DryFire");
        bReload = bank.get("Reload");
        bFuse = bank.get("Fuse");
        bExplosion = bank.get("Explosion");
        bAlarmBell = bank.get("AlarmBell");
        bGate = bank.get("Gate");
        bRain = bank.get("Rain");
        bWind = bank.get("Wind");
        bFire = bank.get("Fire");
        bCave = bank.get("Cave");
        bCrickets = bank.get("Crickets");
        bBeacon = bank.get("Beacon");
        bRainHigh = bank.get("RainHigh");
        bWindHigh = bank.get("WindHigh");
        for (int i = 0; i < detailBuffers.length; i++) detailBuffers[i] = bank.get(AmbienceBeds.EVENT_NAMES[i]);
        synthesisMs = (System.nanoTime() - start) / 1e6;
        System.out.printf("[audio] rate=%d buffers=%d pcmBytes=%d synthesisAndUploadMs=%.3f%n",
                ProceduralAudio.RATE, bank.size(), pcmBytes, synthesisMs);
    }

    private int upload(float[] samples) {
        PcmAudio.prepare(samples);
        pcmBytes += samples.length * 2L;
        ShortBuffer buf = BufferUtils.createShortBuffer(samples.length);
        for (float sample : samples) {
            buf.put(PcmAudio.encode(sample));
        }
        buf.flip();
        int buffer = alGenBuffers();
        alBufferData(buffer, AL_FORMAT_MONO16, buf, ProceduralAudio.RATE);
        return buffer;
    }
}
