#version 330 core
// Water surface: pos, sky/block light, column depth (for shore color/foam).
layout(location = 0) in vec3 aPos;
layout(location = 1) in float aSky;
layout(location = 2) in float aBlock;
layout(location = 3) in float aDepth;

uniform mat4 uProj;
uniform mat4 uView;
uniform float uTime;

out vec3 vWorldPos;
out float vSky;
out float vBlock;
out float vDepth;
out float vDist;

void main() {
    vec3 pos = aPos;
    // Gentle swell (only on top faces, which sit at fractional heights).
    float isTop = step(0.5, fract(aPos.y + 0.001) );
    pos.y += (sin(uTime * 1.3 + aPos.x * 0.7 + aPos.z * 0.9)
            + sin(uTime * 2.1 + aPos.z * 1.3)) * 0.02 * isTop;
    vec4 viewPos = uView * vec4(pos, 1.0);
    gl_Position = uProj * viewPos;
    vWorldPos = pos;
    vSky = aSky;
    vBlock = aBlock;
    vDepth = aDepth;
    vDist = length(viewPos.xyz);
}
