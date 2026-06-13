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

    private static final int RATE = 22050;
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

    // Ambience loops.
    private int bRain, bWind, bFire, bCave, bCrickets, bBeacon;
    private int sRain, sWind, sFire, sCave, sCrickets, sBeacon;
    private final float[] ambTarget = new float[6];
    private final float[] ambCurrent = new float[6];

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
        float[] ori = {fx, 0, fz, 0, 1, 0};
        alListenerfv(AL_ORIENTATION, ori);
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
        int[] sources = {sRain, sWind, sFire, sCave, sCrickets, sBeacon};
        float[] level = {0.65f, 0.5f, 0.55f, 0.4f, 0.3f, 0.35f};
        for (int i = 0; i < 6; i++) {
            float cur = ambCurrent[i];
            float tgt = ambTarget[i];
            cur += (tgt - cur) * Math.min(1f, dt * 1.5f);
            if (Math.abs(cur - ambCurrent[i]) > 0.0005f || cur != tgt) {
                alSourcef(sources[i], AL_GAIN, cur * level[i]);
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
        bFootGrass = upload(noiseBurst(0.10f, 0.18f, 3.5f, 0.5f));
        bFootStone = upload(noiseBurst(0.07f, 0.55f, 6f, 0.7f));
        bFootWood = upload(mix(noiseBurst(0.08f, 0.35f, 5f, 0.6f), tone(160, 0.08f, 10f, 0.3f)));
        bFootSnow = upload(noiseBurst(0.13f, 0.12f, 3f, 0.45f));
        bFootWater = upload(noiseBurst(0.16f, 0.30f, 2.5f, 0.6f));

        bHitSoft = upload(noiseBurst(0.06f, 0.22f, 8f, 0.6f));
        bHitStone = upload(mix(noiseBurst(0.05f, 0.7f, 12f, 0.8f), tone(900, 0.04f, 25f, 0.25f)));
        bHitWood = upload(mix(noiseBurst(0.06f, 0.4f, 10f, 0.7f), tone(240, 0.06f, 15f, 0.4f)));

        bBreak = upload(mix(noiseBurst(0.22f, 0.5f, 4f, 0.9f), tone(110, 0.18f, 7f, 0.4f)));
        bPlace = upload(mix(noiseBurst(0.09f, 0.45f, 8f, 0.7f), tone(200, 0.07f, 14f, 0.35f)));
        bClick = upload(tone(1400, 0.035f, 50f, 0.5f));
        bEat = upload(chew());
        bDrink = upload(gulp());
        bBoil = upload(noiseBurst(0.5f, 0.25f, 2f, 0.5f));
        bHit = upload(mix(noiseBurst(0.08f, 0.4f, 9f, 0.9f), tone(120, 0.09f, 12f, 0.6f)));
        bHurt = upload(grunt());
        bSwing = upload(swish());
        bCraft = upload(mix(tone(520, 0.08f, 12f, 0.4f), tone(780, 0.12f, 8f, 0.3f)));
        bEquip = upload(noiseBurst(0.14f, 0.3f, 5f, 0.55f));
        bToolBreak = upload(mix(tone(620, 0.07f, 22f, 0.6f), noiseBurst(0.2f, 0.6f, 6f, 0.7f)));
        bCough = upload(cough());
        bSleep = upload(chime(new float[]{392, 494, 587}, 0.32f));
        bDiscover = upload(chime(new float[]{523, 659, 784, 1047}, 0.26f));
        bQuest = upload(chime(new float[]{440, 554, 659}, 0.3f));
        bThunder = upload(thunder());
        bHowl = upload(howl());
        bGrowl = upload(growl());
        bChirp = upload(chirp());
        bFlap = upload(flap());
        bDeer = upload(deerCall());

        bRain = upload(rainLoop());
        bWind = upload(windLoop());
        bFire = upload(fireLoop());
        bCave = upload(caveLoop());
        bCrickets = upload(cricketsLoop());
        bBeacon = upload(beaconLoop());
    }

    private int upload(float[] samples) {
        ShortBuffer buf = BufferUtils.createShortBuffer(samples.length);
        for (float s : samples) {
            buf.put((short) (Math.max(-1f, Math.min(1f, s)) * Short.MAX_VALUE * 0.9f));
        }
        buf.flip();
        int buffer = alGenBuffers();
        alBufferData(buffer, AL_FORMAT_MONO16, buf, RATE);
        return buffer;
    }

    private static int len(float seconds) {
        return (int) (seconds * RATE);
    }

    /** White noise through a one-pole lowpass with an exponential decay envelope. */
    private float[] noiseBurst(float seconds, float lowpass, float decay, float amp) {
        int n = len(seconds);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float x = rng.nextFloat() * 2f - 1f;
            y += lowpass * (x - y);
            float t = (float) i / RATE;
            out[i] = y * (float) Math.exp(-decay * t) * amp;
        }
        return out;
    }

    private float[] tone(float freq, float seconds, float decay, float amp) {
        int n = len(seconds);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            out[i] = (float) (Math.sin(2 * Math.PI * freq * t) * Math.exp(-decay * t)) * amp;
        }
        return out;
    }

    private static float[] mix(float[] a, float[] b) {
        int n = Math.max(a.length, b.length);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float v = 0;
            if (i < a.length) v += a[i];
            if (i < b.length) v += b[i];
            out[i] = v;
        }
        return out;
    }

    private float[] chew() {
        float[] out = new float[len(0.5f)];
        for (int c = 0; c < 3; c++) {
            float[] bite = noiseBurst(0.09f, 0.25f, 9f, 0.55f);
            int off = len(0.16f) * c;
            for (int i = 0; i < bite.length && off + i < out.length; i++) {
                out[off + i] += bite[i];
            }
        }
        return out;
    }

    private float[] gulp() {
        float[] out = new float[len(0.6f)];
        for (int c = 0; c < 3; c++) {
            int off = len(0.18f) * c;
            float f = 300 - c * 50;
            float[] g = tone(f, 0.12f, 14f, 0.5f);
            for (int i = 0; i < g.length && off + i < out.length; i++) {
                out[off + i] += g[i] * (1 + 0.4f * (float) Math.sin(i * 0.01f));
            }
        }
        return out;
    }

    private float[] grunt() {
        int n = len(0.22f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float f = 130 - t * 120;
            float x = (float) Math.sin(2 * Math.PI * f * t) * 0.7f + (rng.nextFloat() - 0.5f) * 0.5f;
            y += 0.3f * (x - y);
            out[i] = y * (float) Math.exp(-8 * t);
        }
        return out;
    }

    private float[] swish() {
        int n = len(0.18f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / n;
            float x = rng.nextFloat() * 2f - 1f;
            float lp = 0.1f + 0.5f * (float) Math.sin(Math.PI * t);
            y += lp * (x - y);
            out[i] = y * (float) Math.sin(Math.PI * t) * 0.45f;
        }
        return out;
    }

    private float[] cough() {
        float[] a = noiseBurst(0.12f, 0.3f, 10f, 0.7f);
        float[] out = new float[len(0.35f)];
        System.arraycopy(a, 0, out, 0, a.length);
        float[] b = noiseBurst(0.10f, 0.28f, 12f, 0.55f);
        int off = len(0.18f);
        for (int i = 0; i < b.length && off + i < out.length; i++) {
            out[off + i] += b[i];
        }
        return out;
    }

    private float[] chime(float[] freqs, float noteSec) {
        int n = len(noteSec * freqs.length + 0.5f);
        float[] out = new float[n];
        for (int k = 0; k < freqs.length; k++) {
            int off = len(noteSec * k);
            float[] note = tone(freqs[k], 0.7f, 4f, 0.35f);
            for (int i = 0; i < note.length && off + i < n; i++) {
                out[off + i] += note[i];
            }
        }
        return out;
    }

    private float[] thunder() {
        int n = len(2.4f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float x = rng.nextFloat() * 2f - 1f;
            y += 0.045f * (x - y);
            float env = (float) (Math.exp(-1.6 * t) * (0.6 + 0.4 * Math.sin(t * 9)));
            out[i] = y * env * 1.6f;
        }
        return out;
    }

    private float[] howl() {
        int n = len(1.9f);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float prog = t / 1.9f;
            // Rise, hold, fall with vibrato.
            float f = prog < 0.3f ? 380 + prog / 0.3f * 270
                    : (prog < 0.7f ? 650 : 650 - (prog - 0.7f) / 0.3f * 220);
            f += (float) Math.sin(t * 35) * 9;
            phase += 2 * Math.PI * f / RATE;
            float env = (float) (Math.sin(Math.PI * Math.min(1, prog * 1.04)) * 0.55);
            out[i] = (float) (Math.sin(phase) * 0.8 + Math.sin(phase * 2) * 0.2) * env;
        }
        return out;
    }

    private float[] growl() {
        int n = len(0.9f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float x = rng.nextFloat() * 2f - 1f;
            y += 0.06f * (x - y);
            float am = 0.6f + 0.4f * (float) Math.sin(2 * Math.PI * 28 * t);
            float env = (float) Math.sin(Math.PI * Math.min(1f, t / 0.9f));
            out[i] = y * am * env * 1.8f;
        }
        return out;
    }

    private float[] chirp() {
        float[] out = new float[len(0.45f)];
        for (int c = 0; c < 3; c++) {
            int off = len(0.15f * c);
            float base = 2800 + rng.nextInt(800);
            int m = len(0.09f);
            for (int i = 0; i < m && off + i < out.length; i++) {
                float t = (float) i / RATE;
                float f = base + (float) Math.sin(t * 200) * 400;
                out[off + i] += (float) (Math.sin(2 * Math.PI * f * t)
                        * Math.sin(Math.PI * i / (float) m)) * 0.3f;
            }
        }
        return out;
    }

    private float[] flap() {
        float[] out = new float[len(0.5f)];
        for (int c = 0; c < 4; c++) {
            float[] puff = noiseBurst(0.06f, 0.2f, 14f, 0.4f);
            int off = len(0.11f * c);
            for (int i = 0; i < puff.length && off + i < out.length; i++) {
                out[off + i] += puff[i];
            }
        }
        return out;
    }

    private float[] deerCall() {
        int n = len(0.7f);
        float[] out = new float[n];
        double phase = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float f = 520 - t * 180 + (float) Math.sin(t * 60) * 25;
            phase += 2 * Math.PI * f / RATE;
            float env = (float) Math.sin(Math.PI * Math.min(1f, t / 0.7f));
            out[i] = (float) (Math.sin(phase) * 0.5 + Math.sin(phase * 3) * 0.15) * env * 0.7f;
        }
        return out;
    }

    // ---- Loops (seamless via full-length noise / periodic content) ----

    private float[] rainLoop() {
        int n = len(2.5f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float x = rng.nextFloat() * 2f - 1f;
            y += 0.35f * (x - y);
            out[i] = y * 0.5f;
            // Occasional droplet plinks.
            if (rng.nextFloat() < 0.0006f) {
                out[i] += 0.3f;
            }
        }
        return fadeEnds(out);
    }

    private float[] windLoop() {
        int n = len(4f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float x = rng.nextFloat() * 2f - 1f;
            y += 0.05f * (x - y);
            // Two slow gust cycles that line up with the loop length.
            float gust = 0.55f + 0.45f * (float) Math.sin(2 * Math.PI * t / 4f)
                    * (float) Math.sin(2 * Math.PI * t / 2f);
            out[i] = y * gust * 1.4f;
        }
        return fadeEnds(out);
    }

    private float[] fireLoop() {
        int n = len(3f);
        float[] out = new float[n];
        float y = 0;
        for (int i = 0; i < n; i++) {
            float x = rng.nextFloat() * 2f - 1f;
            y += 0.12f * (x - y);
            out[i] = y * 0.55f;
        }
        // Crackle pops.
        for (int p = 0; p < 26; p++) {
            int at = rng.nextInt(n - len(0.05f));
            float amp = 0.25f + rng.nextFloat() * 0.4f;
            int m = len(0.012f + rng.nextFloat() * 0.02f);
            for (int i = 0; i < m; i++) {
                out[at + i] += (rng.nextFloat() * 2f - 1f)
                        * amp * (float) Math.exp(-12.0 * i / m);
            }
        }
        return fadeEnds(out);
    }

    private float[] caveLoop() {
        int n = len(4f);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            out[i] = (float) (Math.sin(2 * Math.PI * 55 * t) * 0.35
                    + Math.sin(2 * Math.PI * 82.5 * t) * 0.2
                    + Math.sin(2 * Math.PI * 0.25 * t) * 0.08);
        }
        return out; // pure periodic content, already loop-clean at 4 s
    }

    private float[] cricketsLoop() {
        int n = len(2f);
        float[] out = new float[n];
        for (int burst = 0; burst < 14; burst++) {
            int at = rng.nextInt(n - len(0.06f));
            float f = 3800 + rng.nextInt(700);
            int m = len(0.045f);
            for (int i = 0; i < m; i++) {
                float t = (float) i / RATE;
                out[at + i] += (float) (Math.sin(2 * Math.PI * f * t)
                        * Math.sin(2 * Math.PI * 60 * t)
                        * Math.sin(Math.PI * i / (float) m)) * 0.22f;
            }
        }
        return fadeEnds(out);
    }

    private float[] beaconLoop() {
        int n = len(2f);
        float[] out = new float[n];
        for (int i = 0; i < n; i++) {
            float t = (float) i / RATE;
            float pulse = 0.6f + 0.4f * (float) Math.sin(2 * Math.PI * t / 2f);
            out[i] = (float) (Math.sin(2 * Math.PI * 220 * t) * 0.3
                    + Math.sin(2 * Math.PI * 331 * t) * 0.18) * pulse;
        }
        return out;
    }

    private static float[] fadeEnds(float[] s) {
        int fade = Math.min(s.length / 8, 1400);
        for (int i = 0; i < fade; i++) {
            float k = (float) i / fade;
            s[i] *= k;
            s[s.length - 1 - i] *= k;
        }
        return s;
    }
}
