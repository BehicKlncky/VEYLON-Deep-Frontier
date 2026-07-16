package com.veylon.engine;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;

import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

import static org.lwjgl.opengl.GL33C.*;

public class ShaderProgram {

    private final int program;
    private final String label;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public ShaderProgram(String vertexSrc, String fragmentSrc) {
        this(vertexSrc, fragmentSrc, "<inline>");
    }

    public ShaderProgram(String vertexSrc, String fragmentSrc, String label) {
        this.label = label;
        int vs = compile(GL_VERTEX_SHADER, vertexSrc, label + ".vert");
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSrc, label + ".frag");
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link error in " + label + ": "
                    + glGetProgramInfoLog(program));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
        System.out.println("[gl] shader program " + program + " = " + label);
    }

    /** Loads assets/shaders/&lt;name&gt;.vert + .frag from the classpath. */
    public static ShaderProgram load(String name) {
        return load(name, name);
    }

    /** Loads assets/shaders/&lt;vertName&gt;.vert + &lt;fragName&gt;.frag (shared fullscreen verts). */
    public static ShaderProgram load(String vertName, String fragName) {
        String vs = com.veylon.gfx.ResourceManager.readText("shaders/" + vertName + ".vert");
        String fs = com.veylon.gfx.ResourceManager.readText("shaders/" + fragName + ".frag");
        return new ShaderProgram(vs, fs, "shaders/" + vertName + "|" + fragName);
    }

    private static int compile(int type, String src, String label) {
        int shader = glCreateShader(type);
        glShaderSource(shader, src);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compile error in " + label + ": "
                    + glGetShaderInfoLog(shader));
        }
        return shader;
    }

    public void bind() {
        glUseProgram(program);
    }

    private int loc(String name) {
        return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(program, n));
    }

    public void set(String name, Matrix4f m) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            FloatBuffer fb = stack.mallocFloat(16);
            m.get(fb);
            glUniformMatrix4fv(loc(name), false, fb);
        }
    }

    public void set(String name, float v) {
        glUniform1f(loc(name), v);
    }

    public void set(String name, int v) {
        glUniform1i(loc(name), v);
    }

    public void set(String name, float x, float y) {
        glUniform2f(loc(name), x, y);
    }

    public void set(String name, float x, float y, float z) {
        glUniform3f(loc(name), x, y, z);
    }

    public void set(String name, Vector3f v) {
        glUniform3f(loc(name), v.x, v.y, v.z);
    }

    public void set(String name, float x, float y, float z, float w) {
        glUniform4f(loc(name), x, y, z, w);
    }

    /** Uploads an interleaved vec2 array (x0,y0,x1,y1,...). */
    public void setVec2Array(String name, float[] values) {
        glUniform2fv(loc(name), values);
    }

    public void delete() {
        glDeleteProgram(program);
    }
}
