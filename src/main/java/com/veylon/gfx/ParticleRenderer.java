package com.veylon.gfx;

import com.veylon.engine.ParticleSystem;
import com.veylon.engine.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Instanced camera-facing particle quads: one alpha-blended draw call and one
 * additive draw call per frame, replacing thousands of per-particle cubes.
 */
public class ParticleRenderer {

    private static final int INSTANCE_FLOATS = 10; // pos3 size1 rgba4 params2

    private ShaderProgram shader;
    private int vao, quadVbo, instanceVbo;
    private final float[] scratch = new float[ParticleSystem.MAX * INSTANCE_FLOATS];
    private final java.nio.FloatBuffer uploadBuf =
            org.lwjgl.BufferUtils.createFloatBuffer(ParticleSystem.MAX * INSTANCE_FLOATS);
    public int drawnLastFrame;
    /** Actual instanced draw submissions made during the most recent frame (0..2). */
    public int drawCallsLastFrame;

    public void init() {
        shader = ShaderProgram.load("particle");
        vao = glGenVertexArrays();
        glBindVertexArray(vao);

        quadVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, quadVbo);
        glBufferData(GL_ARRAY_BUFFER, new float[]{
                -0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, 0.5f, 0.5f}, GL_STATIC_DRAW);
        glVertexAttribPointer(0, 2, GL_FLOAT, false, 8, 0);
        glEnableVertexAttribArray(0);

        instanceVbo = glGenBuffers();
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        glBufferData(GL_ARRAY_BUFFER, (long) ParticleSystem.MAX * INSTANCE_FLOATS * 4, GL_STREAM_DRAW);
        int stride = INSTANCE_FLOATS * 4;
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 12);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 16);
        glVertexAttribPointer(4, 2, GL_FLOAT, false, stride, 32);
        for (int a = 1; a <= 4; a++) {
            glEnableVertexAttribArray(a);
            glVertexAttribDivisor(a, 1);
        }
        glBindVertexArray(0);
    }

    /**
     * Draws all particles in two instanced passes. ambient scales non-additive
     * particle brightness so smoke/dust sit in the scene's light.
     */
    public void render(ParticleSystem ps, Matrix4f proj, Matrix4f view,
                       Vector3f camRight, Vector3f camUp, float ambient) {
        drawnLastFrame = 0;
        drawCallsLastFrame = 0;
        if (ps.count == 0) {
            return;
        }
        shader.bind();
        shader.set("uProj", proj);
        shader.set("uView", view);
        shader.set("uCamRight", camRight);
        shader.set("uCamUp", camUp);
        glBindVertexArray(vao);
        glEnable(GL_BLEND);
        glDepthMask(false);

        // Pass 1: alpha-blended (everything except sparks).
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        int n = fill(ps, false, ambient);
        drawInstances(n);

        // Pass 2: additive sparks/embers/energy.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        n = fill(ps, true, 1f);
        drawInstances(n);

        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glBindVertexArray(0);
    }

    private int fill(ParticleSystem ps, boolean additive, float ambient) {
        int n = 0;
        for (int i = 0; i < ps.count; i++) {
            boolean isAdd = ps.kind[i] == ParticleSystem.KIND_SPARK;
            if (isAdd != additive) {
                continue;
            }
            float fade = ps.fade(i);
            float s = ps.size[i] * (0.6f + 0.4f * fade);
            if (s < 0.004f) {
                continue;
            }
            int o = n * INSTANCE_FLOATS;
            scratch[o] = ps.px[i];
            scratch[o + 1] = ps.py[i];
            scratch[o + 2] = ps.pz[i];
            scratch[o + 3] = s;
            scratch[o + 4] = ps.cr[i] * ambient;
            scratch[o + 5] = ps.cg[i] * ambient;
            scratch[o + 6] = ps.cb[i] * ambient;
            scratch[o + 7] = additive ? fade : 0.85f * fade;
            scratch[o + 8] = ps.kind[i] == ParticleSystem.KIND_SPARK ? 3f : ps.kind[i];
            scratch[o + 9] = ps.kind[i] == ParticleSystem.KIND_STREAK ? 5f : 1f;
            n++;
        }
        return n;
    }

    private void drawInstances(int n) {
        if (n == 0) {
            return;
        }
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        uploadBuf.clear();
        uploadBuf.put(scratch, 0, n * INSTANCE_FLOATS).flip();
        // Orphan then refill: avoids stalls without allocating per frame.
        glBufferData(GL_ARRAY_BUFFER, (long) ParticleSystem.MAX * INSTANCE_FLOATS * 4, GL_STREAM_DRAW);
        glBufferSubData(GL_ARRAY_BUFFER, 0, uploadBuf);
        glDrawArraysInstanced(GL_TRIANGLE_STRIP, 0, 4, n);
        drawnLastFrame += n;
        drawCallsLastFrame++;
    }

    public void delete() {
        if (shader != null) {
            shader.delete();
        }
        glDeleteBuffers(quadVbo);
        glDeleteBuffers(instanceVbo);
        glDeleteVertexArrays(vao);
    }
}
