package com.veylon.gfx;

import java.util.Arrays;

/** Allocation-free rolling CPU frame-time capture used by smoke/benchmark reports. */
public final class FrameProfiler {

    private static final int CAPACITY = 16_384;
    private final float[] samples = new float[CAPACITY];
    private int count;
    private int cursor;
    private double totalSeconds;
    private long totalFrames;

    public void reset() {
        count = 0;
        cursor = 0;
        totalSeconds = 0;
        totalFrames = 0;
    }

    /** Records the uncapped wall-clock interval between two presented frames. */
    public void record(double seconds) {
        if (!(seconds > 0) || !Double.isFinite(seconds)) {
            return;
        }
        samples[cursor] = (float) (seconds * 1000.0);
        cursor = (cursor + 1) % samples.length;
        count = Math.min(samples.length, count + 1);
        totalSeconds += seconds;
        totalFrames++;
    }

    public Snapshot snapshot() {
        if (count == 0 || totalSeconds <= 0) {
            return new Snapshot(0, 0, 0, 0, 0, 0);
        }
        float[] sorted = new float[count];
        if (count < samples.length) {
            System.arraycopy(samples, 0, sorted, 0, count);
        } else {
            for (int i = 0; i < count; i++) {
                sorted[i] = samples[(cursor + i) % samples.length];
            }
        }
        Arrays.sort(sorted);
        double avgMs = totalSeconds * 1000.0 / totalFrames;
        double fps = totalFrames / totalSeconds;
        return new Snapshot(totalFrames, fps, avgMs,
                percentile(sorted, 0.95), percentile(sorted, 0.99), sorted[count - 1]);
    }

    private static double percentile(float[] sorted, double fraction) {
        if (sorted.length == 0) return 0;
        int index = (int) Math.ceil(fraction * sorted.length) - 1;
        return sorted[Math.max(0, Math.min(sorted.length - 1, index))];
    }

    public record Snapshot(long frames, double averageFps, double averageMs,
                           double p95Ms, double p99Ms, double maxMs) {
    }
}
