package com.veylon.engine;

/** Bounded admission and fade-before-reuse policy; the backend alone touches OpenAL. */
final class VoicePool {
    /** One-shot voices, separate from the 16 permanently allocated ambience emitters. */
    static final int CAPACITY = 24;
    /** Seconds to fade a stolen voice; leave it at zero for one update before reuse. */
    static final float STEAL_SECONDS = 0.03f;
    enum Priority { BACKGROUND, ORDINARY, IMPORTANT, CRITICAL }

    record Request(int buffer, float x, float y, float z, float gain, float pitch,
                   float reference, float maximum, boolean relative, boolean wet,
                   boolean ambience, Priority priority, float highFrequency) {
        Request(int buffer, float x, float y, float z, float gain, float pitch,
                float reference, float maximum, boolean relative, boolean wet,
                boolean ambience, Priority priority) {
            this(buffer, x, y, z, gain, pitch, reference, maximum, relative, wet, ambience, priority, 1);
        }
        float audibility(float lx, float ly, float lz) {
            if (relative) return gain;
            float dx = x - lx, dy = y - ly, dz = z - lz;
            float distance = Math.max(reference, Math.min(maximum, (float) Math.sqrt(dx * dx + dy * dy + dz * dz)));
            return gain * reference / (reference + 1.1f * (distance - reference));
        }
    }

    interface Backend {
        boolean playing(int voice);
        void start(int voice, Request request);
        void gain(int voice, float gain);
        void stop(int voice);
    }

    private final Backend backend;
    private final Request[] active = new Request[CAPACITY], pending = new Request[CAPACITY];
    private final float[] fade = new float[CAPACITY];
    private final long[] age = new long[CAPACITY];
    private long clock;
    private float listenerX, listenerY, listenerZ;
    int steals, dropped;

    VoicePool(Backend backend) { this.backend = backend; }

    void listener(float x, float y, float z) { listenerX = x; listenerY = y; listenerZ = z; }

    boolean play(Request request) {
        int victim = -1;
        for (int i = 0; i < CAPACITY; i++) {
            if (pending[i] == null && !backend.playing(i)) {
                start(i, request);
                return true;
            }
            Request protectedRequest = pending[i] == null ? active[i] : pending[i];
            if (protectedRequest == null || protectedRequest.priority.ordinal() > request.priority.ordinal()) continue;
            if (victim < 0 || lower(i, victim)) victim = i;
        }
        if (victim < 0) { dropped++; return false; }
        if (pending[victim] == null) { fade[victim] = 0; steals++; }
        pending[victim] = request;
        return true;
    }

    private boolean lower(int a, int b) {
        Request ra = pending[a] == null ? active[a] : pending[a];
        Request rb = pending[b] == null ? active[b] : pending[b];
        int priority = ra.priority.compareTo(rb.priority);
        if (priority != 0) return priority < 0;
        int gain = Float.compare(ra.audibility(listenerX, listenerY, listenerZ), rb.audibility(listenerX, listenerY, listenerZ));
        return gain != 0 ? gain < 0 : age[a] < age[b];
    }

    void update(float dt) {
        for (int i = 0; i < CAPACITY; i++) {
            if (pending[i] == null) continue;
            if (fade[i] >= STEAL_SECONDS || !backend.playing(i)) {
                Request next = pending[i];
                backend.stop(i);
                start(i, next);
            } else {
                fade[i] = Math.min(STEAL_SECONDS, fade[i] + Math.max(0, dt));
                backend.gain(i, active[i].gain * fadeGain(fade[i]));
            }
        }
    }

    static float fadeGain(float elapsed) {
        return (float) (0.5 + 0.5 * Math.cos(Math.PI * Math.min(1, Math.max(0, elapsed / STEAL_SECONDS))));
    }

    private void start(int i, Request request) {
        active[i] = request;
        pending[i] = null;
        age[i] = ++clock;
        backend.start(i, request);
    }

    void reset() {
        for (int i = 0; i < CAPACITY; i++) {
            backend.stop(i);
            active[i] = pending[i] = null;
            fade[i] = 0;
        }
        clock = 0;
    }
}
