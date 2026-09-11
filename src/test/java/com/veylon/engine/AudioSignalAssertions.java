package com.veylon.engine;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Small deterministic signal measurements shared by the headless audio tests. */
final class AudioSignalAssertions {
    private AudioSignalAssertions() { }

    static double mean(float[] samples) {
        double sum = 0;
        for (float sample : samples) sum += sample;
        return sum / samples.length;
    }

    static double peak(float[] samples) {
        double peak = 0;
        for (float sample : samples) peak = Math.max(peak, Math.abs(sample));
        return peak;
    }

    static double rms(float[] samples) {
        double sum = 0;
        for (float sample : samples) sum += sample * sample;
        return Math.sqrt(sum / samples.length);
    }

    /** Windowed DFT energy at a frequency in Hz, independent of sample rate. */
    static double energy(float[] samples, int rate, double hz) {
        int n = Math.min(samples.length, 8192);
        int offset = (samples.length - n) / 2;
        double real = 0, imaginary = 0;
        for (int i = 0; i < n; i++) {
            double window = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (n - 1));
            double phase = 2 * Math.PI * hz * i / rate;
            real += samples[offset + i] * window * Math.cos(phase);
            imaginary += samples[offset + i] * window * Math.sin(phase);
        }
        return (real * real + imaginary * imaginary) / (n * (double) n);
    }

    static double band(float[] samples, int rate, double low, double high) {
        double energy = 0;
        for (int i = 0; i < 64; i++) {
            energy += energy(samples, rate, low + (high - low) * (i + 0.5) / 64);
        }
        return energy;
    }

    static void clean(String name, float[] samples) {
        assertTrue(Math.abs(mean(samples)) < 0.00001, name + " DC offset");
        assertTrue(peak(samples) <= 0.90, name + " peak exceeds headroom");
        assertTrue(rms(samples) > 0.001 && rms(samples) < 0.5, name + " RMS outside target");
        assertTrue(Math.abs(samples[0] - samples[samples.length - 1]) < 0.00001,
                name + " discontinuous seam");
    }
}
