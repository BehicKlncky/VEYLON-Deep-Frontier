package com.veylon.util;

import java.util.Arrays;

/** Growable primitive float buffer used for mesh building without boxing. */
public final class FloatList {

    private float[] data;
    private int size;

    public FloatList(int initialCapacity) {
        data = new float[Math.max(16, initialCapacity)];
    }

    public void clear() {
        size = 0;
    }

    public int size() {
        return size;
    }

    public float[] array() {
        return data;
    }

    private void ensure(int extra) {
        if (size + extra > data.length) {
            data = Arrays.copyOf(data, Math.max(data.length * 2, size + extra));
        }
    }

    public void add(float v) {
        ensure(1);
        data[size++] = v;
    }

    public void add(float a, float b) {
        ensure(2);
        data[size++] = a;
        data[size++] = b;
    }

    public void add(float a, float b, float c) {
        ensure(3);
        data[size++] = a;
        data[size++] = b;
        data[size++] = c;
    }

    public void add(float a, float b, float c, float d) {
        ensure(4);
        data[size++] = a;
        data[size++] = b;
        data[size++] = c;
        data[size++] = d;
    }

    /** Adds one chunk-mesh vertex: position, color, sky light, block light. */
    public void vertex(float x, float y, float z, float r, float g, float b, float sky, float block) {
        ensure(8);
        data[size++] = x;
        data[size++] = y;
        data[size++] = z;
        data[size++] = r;
        data[size++] = g;
        data[size++] = b;
        data[size++] = sky;
        data[size++] = block;
    }
}
