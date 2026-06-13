package com.veylon.engine;

import static org.lwjgl.opengl.GL33C.*;

/** Interleaved-float VAO/VBO wrapper for both static and per-frame meshes. */
public class Mesh {

    private final int vao;
    private final int vbo;
    private final int strideFloats;
    private int vertexCount;

    public Mesh(int[] attribSizes) {
        int stride = 0;
        for (int s : attribSizes) {
            stride += s;
        }
        strideFloats = stride;
        vao = glGenVertexArrays();
        vbo = glGenBuffers();
        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        int offset = 0;
        for (int i = 0; i < attribSizes.length; i++) {
            glVertexAttribPointer(i, attribSizes[i], GL_FLOAT, false, stride * 4, (long) offset * 4);
            glEnableVertexAttribArray(i);
            offset += attribSizes[i];
        }
        glBindVertexArray(0);
    }

    /** Uploads the first floatCount floats from data. */
    public void upload(float[] data, int floatCount) {
        vertexCount = floatCount / strideFloats;
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        if (floatCount == data.length) {
            glBufferData(GL_ARRAY_BUFFER, data, GL_DYNAMIC_DRAW);
        } else {
            float[] trimmed = java.util.Arrays.copyOf(data, floatCount);
            glBufferData(GL_ARRAY_BUFFER, trimmed, GL_DYNAMIC_DRAW);
        }
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    public void draw(int mode) {
        if (vertexCount == 0) {
            return;
        }
        glBindVertexArray(vao);
        glDrawArrays(mode, 0, vertexCount);
        glBindVertexArray(0);
    }

    public void draw() {
        draw(GL_TRIANGLES);
    }

    public int vertexCount() {
        return vertexCount;
    }

    public void delete() {
        glDeleteBuffers(vbo);
        glDeleteVertexArrays(vao);
    }
}
