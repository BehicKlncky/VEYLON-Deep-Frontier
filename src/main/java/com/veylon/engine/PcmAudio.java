package com.veylon.engine;

/** The exact mono signed-16 conversion used at the OpenAL upload boundary. */
final class PcmAudio {
    private PcmAudio() { }

    static short encode(float sample) {
        return (short) (Math.max(-1f, Math.min(1f, sample)) * Short.MAX_VALUE * 0.9f);
    }
}
