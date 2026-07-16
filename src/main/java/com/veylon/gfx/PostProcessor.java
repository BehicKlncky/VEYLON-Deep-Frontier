package com.veylon.gfx;

import com.veylon.engine.ShaderProgram;

import static org.lwjgl.opengl.GL33C.*;

/**
 * HDR offscreen scene buffer + bloom chain + final composite (exposure, ACES,
 * grade, FXAA-lite, vignettes). Owns an empty VAO for fullscreen triangles.
 */
public class PostProcessor {

    private int width = -1, height = -1;
    private int sceneFbo, sceneColor, sceneDepth;
    private int brightFbo, brightTex;
    private int blurFboA, blurTexA, blurFboB, blurTexB;
    private int emptyVao;

    private ShaderProgram brightShader;
    private ShaderProgram blurShader;
    private ShaderProgram finalShader;

    public void init() {
        emptyVao = glGenVertexArrays();
        brightShader = ShaderProgram.load("fullscreen", "post_bright");
        blurShader = ShaderProgram.load("fullscreen", "post_blur");
        finalShader = ShaderProgram.load("fullscreen", "post_final");
    }

    /** (Re)creates render targets when the window size changes. */
    public void resize(int w, int h) {
        if (w == width && h == height) {
            return;
        }
        deleteTargets();
        width = w;
        height = h;

        sceneColor = colorTex(w, h, GL_RGBA16F);
        sceneDepth = glGenRenderbuffers();
        glBindRenderbuffer(GL_RENDERBUFFER, sceneDepth);
        glRenderbufferStorage(GL_RENDERBUFFER, GL_DEPTH_COMPONENT24, w, h);
        sceneFbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, sceneColor, 0);
        glFramebufferRenderbuffer(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_RENDERBUFFER, sceneDepth);
        check("scene");

        int bw = Math.max(1, w / 4), bh = Math.max(1, h / 4);
        brightTex = colorTex(bw, bh, GL_RGBA16F);
        brightFbo = fboFor(brightTex, "bright");
        blurTexA = colorTex(bw, bh, GL_RGBA16F);
        blurFboA = fboFor(blurTexA, "blurA");
        blurTexB = colorTex(bw, bh, GL_RGBA16F);
        blurFboB = fboFor(blurTexB, "blurB");
        glBindFramebuffer(GL_FRAMEBUFFER, 0);
    }

    private int colorTex(int w, int h, int internal) {
        int tex = glGenTextures();
        glBindTexture(GL_TEXTURE_2D, tex);
        glTexImage2D(GL_TEXTURE_2D, 0, internal, w, h, 0, GL_RGBA, GL_FLOAT,
                (java.nio.ByteBuffer) null);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        return tex;
    }

    private int fboFor(int tex, String label) {
        int fbo = glGenFramebuffers();
        glBindFramebuffer(GL_FRAMEBUFFER, fbo);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, tex, 0);
        check(label);
        return fbo;
    }

    private void check(String label) {
        int status = glCheckFramebufferStatus(GL_FRAMEBUFFER);
        if (status != GL_FRAMEBUFFER_COMPLETE) {
            throw new RuntimeException("Post FBO '" + label + "' incomplete: 0x"
                    + Integer.toHexString(status));
        }
    }

    /** Binds the HDR scene target; world rendering goes here. */
    public void beginScene() {
        glBindFramebuffer(GL_FRAMEBUFFER, sceneFbo);
        glViewport(0, 0, width, height);
    }

    /** Runs bloom + composite into the default framebuffer. */
    public void composite(Environment env, boolean bloomOn, boolean fxaaOn) {
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);
        glBindVertexArray(emptyVao);

        int bw = Math.max(1, width / 4), bh = Math.max(1, height / 4);
        if (bloomOn) {
            glViewport(0, 0, bw, bh);
            glBindFramebuffer(GL_FRAMEBUFFER, brightFbo);
            brightShader.bind();
            brightShader.set("uScene", 0);
            glActiveTexture(GL_TEXTURE0);
            glBindTexture(GL_TEXTURE_2D, sceneColor);
            glDrawArrays(GL_TRIANGLES, 0, 3);

            blurShader.bind();
            blurShader.set("uScene", 0);
            glBindFramebuffer(GL_FRAMEBUFFER, blurFboA);
            blurShader.set("uDir", 1f / bw, 0f);
            glBindTexture(GL_TEXTURE_2D, brightTex);
            glDrawArrays(GL_TRIANGLES, 0, 3);
            glBindFramebuffer(GL_FRAMEBUFFER, blurFboB);
            blurShader.set("uDir", 0f, 1f / bh);
            glBindTexture(GL_TEXTURE_2D, blurTexA);
            glDrawArrays(GL_TRIANGLES, 0, 3);
        }

        glBindFramebuffer(GL_FRAMEBUFFER, 0);
        glViewport(0, 0, width, height);
        finalShader.bind();
        finalShader.set("uScene", 0);
        finalShader.set("uBloom", 1);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, sceneColor);
        glActiveTexture(GL_TEXTURE1);
        glBindTexture(GL_TEXTURE_2D, bloomOn ? blurTexB : sceneColor);
        finalShader.set("uBloomStrength", bloomOn ? 0.55f : 0f);
        finalShader.set("uExposure", env.exposure);
        finalShader.set("uSaturation", env.saturation);
        finalShader.set("uTint", env.gradeTint);
        finalShader.set("uContrast", env.contrast);
        finalShader.set("uVignettes", env.vigDamage, env.vigCold, env.vigPoison, env.vigSmokeHeat);
        finalShader.set("uHeat", env.heatMix);
        finalShader.set("uUnderwater", env.underwater);
        finalShader.set("uFxaaOn", fxaaOn ? 1f : 0f);
        finalShader.set("uTexel", 1f / width, 1f / height);
        glDrawArrays(GL_TRIANGLES, 0, 3);
        glBindVertexArray(0);
        glEnable(GL_DEPTH_TEST);
    }

    private void deleteTargets() {
        if (sceneFbo != 0) {
            glDeleteFramebuffers(new int[]{sceneFbo, brightFbo, blurFboA, blurFboB});
            glDeleteTextures(new int[]{sceneColor, brightTex, blurTexA, blurTexB});
            glDeleteRenderbuffers(sceneDepth);
            sceneFbo = 0;
        }
    }

    public void delete() {
        deleteTargets();
        if (brightShader != null) {
            brightShader.delete();
            blurShader.delete();
            finalShader.delete();
        }
        if (emptyVao != 0) {
            glDeleteVertexArrays(emptyVao);
        }
    }
}
