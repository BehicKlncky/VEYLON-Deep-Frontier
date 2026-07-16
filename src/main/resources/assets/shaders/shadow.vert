#version 330 core
// Depth-only pass into the sun shadow map. Works for chunk VAOs (location 0 =
// position, uModel = identity) and entity cuboids (uModel set per part).
layout(location = 0) in vec3 aPos;

uniform mat4 uSunMatrix;
uniform mat4 uModel;

void main() {
    gl_Position = uSunMatrix * uModel * vec4(aPos, 1.0);
}
