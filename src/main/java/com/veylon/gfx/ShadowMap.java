package com.veylon.gfx;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33C.*;

/**
 * One practical PCF sun shadow map: an ortho box that follows the camera,
 * snapped to texel grid to avoid shimmer.
 */
public class ShadowMap {

    public final int size;
    private final int fbo;
    private final int depthTex;
    public final Matrix4f lightMatrix = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private final Matrix4f proj = new Matrix4f();
    private final Vector3f center = new Vector3f();
    private final Vector3f eye = new Vector3f();

    /** World-space half extent covered by the map. */
    public static final float RADIUS = 52f;

    public ShadowMap(int size) {
        this.size = size;
        depthTex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, depthTex);
        glTexImage2D(GL_TEXTURE_2D, 0, GL_DEPTH_COMPONENT24, size, size, 0,
                GL_DEPTH_COMPONENT, GL_FLOAT, (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_BORDER);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_BORDER);
        glTexParameterfv(GL_TEXTURE_2D, GL_TEXTURE_BORDER_COLOR, new float[]{1, 1, 1, 1});
        // Hardware PCF via comparison sampling.
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_MODE, GL_COMPARE_REF_TO_TEXTURE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_COMPARE_FUNC, GL_LEQUAL);

        fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, depthTex, 0);
        glDrawBuffer(GL_NONE);
        glReadBuffer(GL_NONE);
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Shadow FBO incomplete: 0x" + Integer.toHexString(status));
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    /** Recomputes the light matrix around a focus point for the given light direction. */
    public void updateMatrix(Vector3f focus, Vector3f lightDir) {
        // Snap the focus to shadow-texel increments to stop edge shimmer.
        float texel = RADIUS * 2f / size;
        center.set(
                Math.round(focus.x / texel) * texel,
                Math.round(focus.y / texel) * texel,
                Math.round(focus.z / texel) * texel);
        eye.set(lightDir).mul(90f).add(center);
        view.identity().lookAt(eye, center, Math.abs(lightDir.y) > 0.98f
                ? new Vector3f(1, 0, 0) : new Vector3f(0, 1, 0));
        proj.identity().ortho(-RADIUS, RADIUS, -RADIUS, RADIUS, 1f, 220f);
        lightMatrix.set(proj).mul(view);
    }

    /** Binds the FBO for depth rendering (caller renders, then calls end()). */
    public void begin() {
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glViewport(0, 0, size, size);
        glClear(GL_DEPTH_BUFFER_BIT);
        glEnable(GL_POLYGON_OFFSET_FILL);
        glPolygonOffset(2.2f, 5.0f);
    }

    public void end() {
        glDisable(GL_POLYGON_OFFSET_FILL);
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    public void bindTexture(int unit) {
        glActiveTexture(GL_TEXTURE0 + unit);
        glBindTexture(GL_TEXTURE_2D, depthTex);
    }

    public void delete() {
        glDeleteFramebuffers(fbo);
        glDeleteTextures(depthTex);
    }
}
