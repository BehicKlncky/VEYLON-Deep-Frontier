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
    private boolean enabled;
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
    private int bRain, bWind, bFire, bCave, bCrickets, bBeacon;
    private int sRain, sWind, sFire, sCave, sCrickets, sBeacon;
    private final float[] ambTarget = new float[6];
    private final float[] ambCurrent = new float[6];
    private final int[] ambSources = new int[6];
    private static final float[] AMBIENCE_LEVELS = {0.65f, 0.5f, 0.55f, 0.4f, 0.3f, 0.35f};
    private final float[] listenerOrientation = new float[6];

    private int[] pool;
    private int poolNext;

    public void init() {
        try {
            device = alcOpenDevice((CharSequence) null);
            if (device == NULL) {
                System.out.println("[audio] No audio device; sound disabled.");
                return;
            }
            context = alcCreateContext(device, (int[]) null);
            alcMakeContextCurrent(context);
            ALCCapabilities alcCaps = ALC.createCapabilities(device);
            AL.createCapabilities(alcCaps);
            alDistanceModel(AL_INVERSE_DISTANCE_CLAMPED);
            alListenerf(AL_GAIN, MASTER);

            synthesizeAll();

            pool = new int[16];
            for (int i = 0; i < pool.length; i++) {
                pool[i] = alGenSources();
                alSourcef(pool[i], AL_REFERENCE_DISTANCE, 3f);
                alSourcef(pool[i], AL_MAX_DISTANCE, 44f);
                alSourcef(pool[i], AL_ROLLOFF_FACTOR, 1.1f);
            }
            sRain = makeLoop(bRain);
            sWind = makeLoop(bWind);
            sFire = makeLoop(bFire);
            sCave = makeLoop(bCave);
            sCrickets = makeLoop(bCrickets);
            sBeacon = makeLoop(bBeacon);
            ambSources[0] = sRain;
            ambSources[1] = sWind;
            ambSources[2] = sFire;
            ambSources[3] = sCave;
            ambSources[4] = sCrickets;
            ambSources[5] = sBeacon;
            enabled = true;
            System.out.println("[audio] OpenAL initialized (" + pool.length + " voices).");
        } catch (Throwable t) {
            System.out.println("[audio] Init failed (" + t + "); sound disabled.");
            enabled = false;
        }
    }

    private int makeLoop(int buffer) {
        int src = alGenSources();
        alSourcei(src, AL_BUFFER, buffer);
        alSourcei(src, AL_LOOPING, AL_TRUE);
        alSourcei(src, AL_SOURCE_RELATIVE, AL_TRUE);
        alSource3f(src, AL_POSITION, 0, 0, 0);
        alSourcef(src, AL_GAIN, 0f);
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
        for (int i = 0; i < 6; i++) {
            float cur = ambCurrent[i];
            float tgt = ambTarget[i];
            cur += (tgt - cur) * Math.min(1f, dt * 1.5f);
            if (Math.abs(cur - ambCurrent[i]) > 0.0005f || cur != tgt) {
                alSourcef(ambSources[i], AL_GAIN, cur * AMBIENCE_LEVELS[i]);
            }
            ambCurrent[i] = cur;
        }
    }

    public void playFootstep(BlockType under, boolean inWater) {
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
        int buf = switch (t.preferredTool) {
            case PICKAXE -> bHitStone;
            case AXE -> bHitWood;
            default -> bHitSoft;
        };
        playAt(buf, x, y, z, 0.5f, pitchVar(0.2f));
    }

    public void playBlockBreak(float x, float y, float z) {
        playAt(bBreak, x, y, z, 0.7f, pitchVar(0.15f));
    }

    public void playBlockPlace(float x, float y, float z) {
        playAt(bPlace, x, y, z, 0.6f, pitchVar(0.15f));
    }

    public void playClick() {
        play2d(bClick, 0.5f, 1f);
    }

    public void playEat() {
        play2d(bEat, 0.6f, pitchVar(0.1f));
    }

    public void playDrink() {
        play2d(bDrink, 0.6f, pitchVar(0.1f));
    }

    public void playBoil() {
        play2d(bBoil, 0.55f, 1f);
    }

    public void playHit() {
        play2d(bHit, 0.7f, pitchVar(0.15f));
    }

    public void playHurt() {
        play2d(bHurt, 0.65f, pitchVar(0.12f));
    }

    public void playSwing() {
        play2d(bSwing, 0.35f, pitchVar(0.2f));
    }

    public void playCraft() {
        play2d(bCraft, 0.6f, 1f);
    }

    public void playEquip() {
        play2d(bEquip, 0.6f, 1f);
    }

    public void playToolBreak() {
        play2d(bToolBreak, 0.8f, 1f);
    }

    public void playCough() {
        play2d(bCough, 0.6f, pitchVar(0.1f));
    }

    public void playSleep() {
        play2d(bSleep, 0.5f, 1f);
    }

    public void playDiscover() {
        play2d(bDiscover, 0.7f, 1f);
    }

    public void playQuest() {
        play2d(bQuest, 0.7f, 1f);
    }

    public void playThunder() {
        play2d(bThunder, 0.9f, pitchVar(0.2f));
    }

    public void playHowl(float x, float y, float z) {
        playAt(bHowl, x, y, z, 0.85f, pitchVar(0.1f));
    }

    public void playGrowl(float x, float y, float z) {
        playAt(bGrowl, x, y, z, 0.7f, pitchVar(0.15f));
    }

    public void playChirp(float x, float y, float z) {
        playAt(bChirp, x, y, z, 0.45f, pitchVar(0.25f));
    }

    public void playBirdFlap(float x, float y, float z) {
        playAt(bFlap, x, y, z, 0.45f, pitchVar(0.2f));
    }

    public void playDeerCall(float x, float y, float z) {
        playAt(bDeer, x, y, z, 0.6f, pitchVar(0.1f));
    }

    // ---- 0.3.0 combat & settlement one-shots ----

    public void playBowDraw() {
        play2d(bBowDraw, 0.45f, pitchVar(0.1f));
    }

    public void playBowRelease(float x, float y, float z) {
        playAt(bBowRelease, x, y, z, 0.55f, pitchVar(0.12f));
    }

    public void playArrowImpact(float x, float y, float z) {
        playAt(bArrowImpact, x, y, z, 0.5f, pitchVar(0.2f));
    }

    public void playBulletImpact(float x, float y, float z) {
        playAt(bBulletImpact, x, y, z, 0.5f, pitchVar(0.2f));
    }

    /** Gunshots carry much farther than ordinary sounds. */
    public void playGunshot(boolean pistol, float x, float y, float z) {
        playAtFar(pistol ? bPistol : bMusket, x, y, z, 0.95f, pitchVar(0.08f));
    }

    public void playDryFire() {
        play2d(bDryFire, 0.5f, pitchVar(0.1f));
    }

    public void playReload() {
        play2d(bReload, 0.55f, pitchVar(0.08f));
    }

    public void playFuse(float x, float y, float z) {
        playAt(bFuse, x, y, z, 0.6f, pitchVar(0.1f));
    }

    public void playExplosion(float x, float y, float z) {
        playAtFar(bExplosion, x, y, z, 1.0f, pitchVar(0.1f));
    }

    public void playAlarmBell(float x, float y, float z) {
        playAtFar(bAlarmBell, x, y, z, 0.85f, pitchVar(0.05f));
    }

    public void playGate(float x, float y, float z) {
        playAt(bGate, x, y, z, 0.6f, pitchVar(0.12f));
    }

    public void shutdown() {
        if (!enabled) {
            return;
        }
        enabled = false;
        alcMakeContextCurrent(NULL);
        alcDestroyContext(context);
        alcCloseDevice(device);
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
        alSource3f(src, AL_POSITION, x, y, z);
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
        alSource3f(src, AL_POSITION, 0, 0, 0);
        alSourcef(src, AL_GAIN, gain);
        alSourcef(src, AL_PITCH, pitch);
        alSourcePlay(src);
    }

    // ------------------------------------------------------------------
    // Synthesis
    // ------------------------------------------------------------------

    private void synthesizeAll() {
        java.util.Map<String, Integer> bank = new java.util.HashMap<>();
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
    }

    private int upload(float[] samples) {
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
