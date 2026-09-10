package com.veylon.engine;

import static org.lwjgl.openal.AL10.*;
import static org.lwjgl.openal.AL11.alSource3i;
import static org.lwjgl.openal.EXTEfx.*;

/** Optional main-thread EFX ownership. Every native path is guarded by successful feature detection. */
final class EfxProcessor {
    private static final int[] PARAMETERS = {AL_REVERB_DECAY_TIME, AL_REVERB_GAIN,
            AL_REVERB_GAINHF, AL_REVERB_DECAY_HFRATIO, AL_REVERB_DENSITY, AL_REVERB_DIFFUSION};
    private final float[] current = new float[PARAMETERS.length];
    private AudioEnvironment.Zone zone = AudioEnvironment.Zone.OPEN;
    private boolean enabled, reported;
    private int effect, slot;
    private float submitTimer;

    void init(boolean supported) {
        if (enabled || reported) return;
        reported = true;
        if (!supported) {
            System.out.println("[audio] EFX unavailable; dry playback enabled.");
            return;
        }
        try {
            effect = alGenEffects();
            alEffecti(effect, AL_EFFECT_TYPE, AL_EFFECT_REVERB);
            slot = alGenAuxiliaryEffectSlots();
            for (int i = 0; i < current.length; i++) current[i] = ReverbPresets.value(zone, i);
            submit();
            if (alGetError() != AL_NO_ERROR) throw new IllegalStateException("reverb allocation rejected");
            enabled = true;
            System.out.println("[audio] EFX reverb initialized (six interpolated zones).");
        } catch (RuntimeException failure) {
            close();
            System.out.println("[audio] EFX unavailable (" + failure.getMessage() + "); dry playback enabled.");
        }
    }

    boolean enabled() { return enabled; }

    void zone(AudioEnvironment.Zone target) { if (enabled && target != null) zone = target; }

    void update(float dt) {
        if (!enabled) return;
        ReverbPresets.blend(current, zone, dt);
        submitTimer -= dt;
        if (submitTimer > 0) return;
        submitTimer = ReverbPresets.SUBMIT_INTERVAL;
        submit();
    }

    void route(int source, boolean wet) {
        if (!enabled) return;
        alSource3i(source, AL_AUXILIARY_SEND_FILTER, wet ? slot : 0, 0, AL_FILTER_NULL);
    }

    void reset() {
        if (!enabled) return;
        zone = AudioEnvironment.Zone.OPEN;
        for (int i = 0; i < current.length; i++) current[i] = ReverbPresets.value(zone, i);
        // Replacing the effect clears the outgoing world's reverberant tail.
        alAuxiliaryEffectSloti(slot, AL_EFFECTSLOT_EFFECT, AL_EFFECT_NULL);
        submit();
    }

    private void submit() {
        for (int i = 0; i < PARAMETERS.length; i++) alEffectf(effect, PARAMETERS[i], current[i]);
        alAuxiliaryEffectSloti(slot, AL_EFFECTSLOT_EFFECT, effect);
    }

    /** Sources must be deleted or detached before this owner is closed. */
    void close() {
        enabled = false;
        if (slot != 0) { alDeleteAuxiliaryEffectSlots(slot); slot = 0; }
        if (effect != 0) { alDeleteEffects(effect); effect = 0; }
    }
}
