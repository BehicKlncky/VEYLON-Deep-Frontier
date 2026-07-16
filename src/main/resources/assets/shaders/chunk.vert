#version 330 core
// Voxel terrain vertex: pos, uv, texture layer, face dir, sky/block light, AO, tint, flags.
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec2 aUV;
layout(location = 2) in float aLayer;
layout(location = 3) in float aDir;
layout(location = 4) in float aSky;
layout(location = 5) in float aBlock;
layout(location = 6) in float aAO;
layout(location = 7) in vec3 aTint;
layout(location = 8) in float aFlags; // bit0 = foliage tint, bit1 = sway

uniform mat4 uProj;
uniform mat4 uView;
uniform mat4 uSunMatrix;
uniform float uTime;

out vec2 vUV;
flat out float vLayer;
out vec3 vNormal;
out float vSky;
out float vBlock;
out float vAO;
out float vFoliage;
out vec3 vTint;
out float vDist;
out vec4 vSunSpace;
out vec3 vWorldPos;

const vec3 NORMALS[8] = vec3[8](
    vec3(0, 1, 0), vec3(0, -1, 0), vec3(0, 0, -1), vec3(0, 0, 1),
    vec3(-1, 0, 0), vec3(1, 0, 0),
    vec3(0.7071, 0, 0.7071), vec3(-0.7071, 0, 0.7071)
);

void main() {
    vec3 pos = aPos;
    int flags = int(aFlags + 0.5);
    // Gentle wind sway for cross-shaped plants (top vertices only, uv.y < 0.5).
    if ((flags & 2) != 0 && aUV.y < 0.5) {
        float sway = sin(uTime * 1.7 + aPos.x * 0.9 + aPos.z * 1.1) * 0.045
                   + sin(uTime * 3.1 + aPos.z * 1.7) * 0.02;
        pos.x += sway;
        pos.z += sway * 0.6;
    }
    vec4 viewPos = uView * vec4(pos, 1.0);
    gl_Position = uProj * viewPos;
    vUV = aUV;
    vLayer = aLayer;
    vNormal = NORMALS[int(aDir + 0.5)];
    vSky = aSky;
    vBlock = aBlock;
    vAO = aAO;
    vFoliage = float(flags & 1);
    vTint = aTint;
    vDist = length(viewPos.xyz);
    vSunSpace = uSunMatrix * vec4(pos, 1.0);
    vWorldPos = pos;
}
