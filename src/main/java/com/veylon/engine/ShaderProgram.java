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
    private final Map<String, Integer> uniforms = new HashMap<>();

    public ShaderProgram(String vertexSrc, String fragmentSrc) {
        int vs = compile(GL_VERTEX_SHADER, vertexSrc);
        int fs = compile(GL_FRAGMENT_SHADER, fragmentSrc);
        program = glCreateProgram();
        glAttachShader(program, vs);
        glAttachShader(program, fs);
        glLinkProgram(program);
        if (glGetProgrami(program, GL_LINK_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader link error: " + glGetProgramInfoLog(program));
        }
        glDeleteShader(vs);
        glDeleteShader(fs);
    }

    private static int compile(int type, String src) {
        int shader = glCreateShader(type);
        glShaderSource(shader, src);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            throw new RuntimeException("Shader compile error: " + glGetShaderInfoLog(shader));
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

    public void delete() {
        glDeleteProgram(program);
    }
}
