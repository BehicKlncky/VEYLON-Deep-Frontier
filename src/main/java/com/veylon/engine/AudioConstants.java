package com.veylon.engine;

/** DSP and mix limits; values are presentation-only and never affect simulation. */
final class AudioConstants {
    /** Linear full-scale ceiling before signed-16 conversion, leaving mix headroom. */
    static final float PCM_PEAK = 0.88f;
    /** Seconds of raised-cosine attack/release to avoid discontinuous buffer boundaries. */
    static final float EDGE_SECONDS = 0.004f;

    private AudioConstants() { }
}
