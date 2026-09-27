package com.veylon.gfx;

import com.veylon.engine.ParticleSystem;
import com.veylon.engine.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33C.*;

/**
 * Instanced camera-facing particle quads: one alpha-blended draw call and one
 * additive draw call per frame, replacing thousands of per-particle cubes.
 * The flames standing on burning bodies ({@link BodyFlames}) are a third
 * call of their own, blended so they both add light and cover some of a
 * bright background, which keeps them orange by day.
 */
public class ParticleRenderer {

    public static final int INSTANCE_FLOATS = 13; // pos3 size1 rgba4 params2 velocity3
    /** Instances one submission can hold: every particle, or every body flame. */
    public static final int CAPACITY = Math.max(ParticleSystem.MAX, BodyFlames.MAX_INSTANCES);
    /** Height over width of a flame tongue licking off a body. */
    private static final float LICK_STRETCH = 1.8f;
    /** A free flame tongue leans this much per unit height per m/s of air past it. */
    private static final float LICK_LEAN = 0.08f, LICK_MAX_LEAN = 0.5f;
    /**
     * Haze (smoke and steam off a body) starts at this share of its size and
     * swells by this much over its life; it is this opaque at most, and takes
     * this share of its life to fade in.
     */
    private static final float HAZE_START = 0.55f, HAZE_GROWTH = 1.6f, HAZE_ALPHA = 0.45f, HAZE_FADE_IN = 0.15f;
    /** Fog far away enough that nothing drawn in camera space is ever in it. */
    private static final float NO_FOG = 1e6f;

    private ShaderProgram shader;
    private int vao, quadVbo, instanceVbo;
    private final float[] scratch = new float[CAPACITY * INSTANCE_FLOATS];
    private final java.nio.FloatBuffer uploadBuf =
            org.lwjgl.BufferUtils.createFloatBuffer(CAPACITY * INSTANCE_FLOATS);
    public int drawnLastFrame;
    /** Actual instanced draw submissions made during the most recent frame (0..3: body flames are the third). */
    public int drawCallsLastFrame;
    /** Body flame tongues drawn during the most recent frame. */
    public int bodyFlamesLastFrame;

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
        glBufferData(GL_ARRAY_BUFFER, (long) CAPACITY * INSTANCE_FLOATS * 4, GL_STREAM_DRAW);
        int stride = INSTANCE_FLOATS * 4;
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 12);
        glVertexAttribPointer(3, 4, GL_FLOAT, false, stride, 16);
        glVertexAttribPointer(4, 2, GL_FLOAT, false, stride, 32);
        glVertexAttribPointer(5, 3, GL_FLOAT, false, stride, 40);
        for (int a = 1; a <= 5; a++) {
            glEnableVertexAttribArray(a);
            glVertexAttribDivisor(a, 1);
        }
        glBindVertexArray(0);
    }

    /**
     * Draws all particles in two instanced passes, the body flames in the
     * additive one. ambient scales non-additive particle brightness so
     * smoke/dust sit in the scene's light; {@code time} is the particle clock
     * flames flicker by; flames fade into the fog like the world behind them.
     */
    public void render(ParticleSystem ps, BodyFlames flames, Matrix4f proj, Matrix4f view,
                       Vector3f camRight, Vector3f camUp, float ambient,
                       float time, float fogStart, float fogEnd, float occlusion) {
        drawnLastFrame = 0;
        drawCallsLastFrame = 0;
        bodyFlamesLastFrame = 0;
        int flameCount = flames == null ? 0 : flames.count;
        if (ps.count == 0 && flameCount == 0) {
            return;
        }
        bind(proj, view, camRight, camUp, time, fogStart, fogEnd);

        // Pass 1: alpha-blended (everything except sparks and flames).
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        int n = pack(ps, false, ambient, scratch);
        drawInstances(n);

        // Pass 2: additive sparks/embers/energy/licks of flame.
        glBlendFunc(GL_SRC_ALPHA, GL_ONE);
        n = pack(ps, true, 1f, scratch);
        drawInstances(n);

        // Pass 3: the flames standing on bodies, premultiplied (see particle.frag).
        int room = Math.min(flameCount, CAPACITY);
        if (room > 0) {
            System.arraycopy(flames.data, 0, scratch, 0, room * INSTANCE_FLOATS);
            flamePass(occlusion);
            drawInstances(room);
            bodyFlamesLastFrame = room;
        }
        unbind();
    }

    /**
     * Draws flames already packed in camera space, over the held item: the
     * flames at the grip of the item a burning player holds.
     */
    public void renderCameraSpace(float[] instances, int n, Matrix4f proj, float time, float occlusion) {
        if (n <= 0) {
            return;
        }
        bind(proj, IDENTITY, CAMERA_RIGHT, CAMERA_UP, time, NO_FOG, NO_FOG * 2f);
        int count = Math.min(n, CAPACITY);
        System.arraycopy(instances, 0, scratch, 0, count * INSTANCE_FLOATS);
        flamePass(occlusion);
        drawInstances(count);
        unbind();
    }

    /** Premultiplied blending for body flames: they add light and cover {@code occlusion} of the scene behind. */
    private void flamePass(float occlusion) {
        glBlendFunc(GL_ONE, GL_ONE_MINUS_SRC_ALPHA);
        shader.set("uPremultiply", 1f);
        shader.set("uOcclusion", Math.max(0f, Math.min(1f, occlusion)));
    }

    private static final Matrix4f IDENTITY = new Matrix4f();
    private static final Vector3f CAMERA_RIGHT = new Vector3f(1, 0, 0);
    private static final Vector3f CAMERA_UP = new Vector3f(0, 1, 0);

    private void bind(Matrix4f proj, Matrix4f view, Vector3f camRight, Vector3f camUp,
                      float time, float fogStart, float fogEnd) {
        shader.bind();
        shader.set("uProj", proj);
        shader.set("uView", view);
        shader.set("uCamRight", camRight);
        shader.set("uCamUp", camUp);
        shader.set("uTime", time);
        shader.set("uFogStart", fogStart);
        shader.set("uFogEnd", fogEnd);
        shader.set("uPremultiply", 0f);
        glBindVertexArray(vao);
        glEnable(GL_BLEND);
        glDepthMask(false);
    }

    private void unbind() {
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);
        glDepthMask(true);
        glDisable(GL_BLEND);
        glBindVertexArray(0);
    }

    /** Headless packing seam shared by both render passes. */
    public static int pack(ParticleSystem ps, boolean additive, float ambient, float[] scratch) {
        int n = 0;
        for (int i = 0; i < ps.count; i++) {
            byte kind = ps.kind[i];
            boolean isAdd = kind == ParticleSystem.KIND_SPARK || kind == ParticleSystem.KIND_FLAME;
            if (isAdd != additive) {
                continue;
            }
            float fade = ps.fade(i);
            float s = ps.size[i] * (0.6f + 0.4f * fade);
            if (kind == ParticleSystem.KIND_HAZE) {
                // Smoke and steam off a body: they swell as they rise, and fade in as well as out.
                float age = ps.age(i);
                s = ps.size[i] * (HAZE_START + HAZE_GROWTH * age);
                fade *= HAZE_ALPHA * Math.min(1f, age / HAZE_FADE_IN);
            }
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
            scratch[o + 7] = (kind == ParticleSystem.KIND_STREAK ? 0.60f : additive ? 1f : 0.85f) * fade;
            if (kind == ParticleSystem.KIND_FLAME) {
                // A tongue standing on its base, leaning with the air past it; the
                // velocity slot carries the lean and its flicker number instead.
                scratch[o + 8] = BodyFlames.SPRITE_FLAME;
                scratch[o + 9] = LICK_STRETCH;
                scratch[o + 10] = lean(ps.windX() - ps.velocityX(i));
                scratch[o + 11] = ps.seed(i);
                scratch[o + 12] = lean(ps.windZ() - ps.velocityZ(i));
            } else {
                scratch[o + 8] = kind == ParticleSystem.KIND_SPARK ? 3f
                        : kind == ParticleSystem.KIND_HAZE ? ParticleSystem.KIND_PUFF : kind;
                scratch[o + 9] = 1f;
                scratch[o + 10] = ps.velocityX(i);
                scratch[o + 11] = ps.velocityY(i);
                scratch[o + 12] = ps.velocityZ(i);
            }
            n++;
        }
        return n;
    }

    private static float lean(float air) {
        return Math.max(-LICK_MAX_LEAN, Math.min(LICK_MAX_LEAN, air * LICK_LEAN));
    }

    private void drawInstances(int n) {
        if (n == 0) {
            return;
        }
        glBindBuffer(GL_ARRAY_BUFFER, instanceVbo);
        uploadBuf.clear();
        uploadBuf.put(scratch, 0, n * INSTANCE_FLOATS).flip();
        // Orphan then refill: avoids stalls without allocating per frame.
        glBufferData(GL_ARRAY_BUFFER, (long) CAPACITY * INSTANCE_FLOATS * 4, GL_STREAM_DRAW);
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
