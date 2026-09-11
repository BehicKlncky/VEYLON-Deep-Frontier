package com.veylon.engine;

/** Conditioning and mono signed-16 conversion at the OpenAL upload boundary. */
final class PcmAudio {
    private PcmAudio() { }

    /** Removes DC without moving the zero endpoints, and scales peaks without clipping. */
    static float[] prepare(float[] samples) {
        int fade = Math.max(1, Math.min(samples.length / 2,
                (int) (ProceduralAudio.RATE * AudioConstants.EDGE_SECONDS)));
        double sum = 0, weights = 0;
        for (int i = 0; i < samples.length; i++) {
            if (!Float.isFinite(samples[i])) throw new IllegalArgumentException("Non-finite PCM");
            float weight = edge(i, samples.length, fade);
            samples[i] *= weight;
            sum += samples[i];
            weights += weight;
        }
        float dc = weights > 0 ? (float) (sum / weights) : 0;
        float peak = 0;
        for (int i = 0; i < samples.length; i++) {
            samples[i] -= dc * edge(i, samples.length, fade);
            peak = Math.max(peak, Math.abs(samples[i]));
        }
        float gain = peak > AudioConstants.PCM_PEAK ? AudioConstants.PCM_PEAK / peak : 1;
        for (int i = 0; i < samples.length; i++) samples[i] *= gain;
        if (samples.length > 0) samples[0] = samples[samples.length - 1] = 0;
        return samples;
    }

    private static float edge(int i, int length, int fade) {
        int distance = Math.min(i, length - 1 - i);
        return distance >= fade ? 1 : (float) (0.5 - 0.5 * Math.cos(Math.PI * distance / fade));
    }

    static short encode(float sample) {
        return (short) (Math.max(-1f, Math.min(1f, sample)) * Short.MAX_VALUE * 0.9f);
    }
}
