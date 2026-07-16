package com.veylon.gfx;

import com.veylon.engine.ShaderProgram;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import static org.lwjgl.opengl.GL33C.*;

/** Fullscreen procedural sky pass (gradient, sun, moon, stars, clouds). */
public class SkyRenderer {

    private ShaderProgram shader;
    private int vao;
    private final Matrix4f invProjView = new Matrix4f();

    public void init() {
        shader = ShaderProgram.load("fullscreen", "sky");
        vao = glGenVertexArrays();
    }

    /** Draw first into the scene buffer, before any depth-tested geometry. */
    public void render(Matrix4f proj, Matrix4f view, Vector3f camPos, Environment env, float time) {
        invProjView.set(proj).mul(view).invert();
        glDepthMask(false);
        glDisable(GL_DEPTH_TEST);
        shader.bind();
        shader.set("uInvProjView", invProjView);
        shader.set("uCamPos", camPos);
        shader.set("uSunDir", env.sunDirVisual);
        shader.set("uZenithColor", env.zenith);
        shader.set("uHorizonColor", env.horizon);
        shader.set("uSunColor", env.sunDiskColor);
        shader.set("uNight", env.night);
        shader.set("uCloudCover", env.cloudCover);
        shader.set("uCloudDark", env.cloudDark);
        shader.set("uTime", time);
        shader.set("uFlash", env.flash);
        shader.set("uFogColor", env.fogColor);
        glBindVertexArray(vao);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glEnable(GL_DEPTH_TEST);
        glDepthMask(true);
    }

    public void delete() {
        if (shader != null) {
            shader.delete();
        }
        if (vao != 0) {
            glDeleteVertexArrays(vao);
        }
    }
}
