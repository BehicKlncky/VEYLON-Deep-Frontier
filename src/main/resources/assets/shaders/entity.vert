#version 330 core
// Cuboid entity/model parts: position + normal, transformed by uModel.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNormal;

uniform mat4 uProj;
uniform mat4 uView;
uniform mat4 uModel;
uniform mat4 uSunMatrix;

out vec3 vNormal;
out float vDist;
out vec4 vSunSpace;
out vec3 vWorldPos;

void main() {
    vec4 world = uModel * vec4(aPos, 1.0);
    vec4 viewPos = uView * world;
    gl_Position = uProj * viewPos;
    // Normal matrix: fine for rigid + uniform-ish scales used by cuboid parts.
    vNormal = mat3(uModel) * aNormal;
    vDist = length(viewPos.xyz);
    vSunSpace = uSunMatrix * world;
    vWorldPos = world.xyz;
}
