#version 330 core
// Instanced camera-facing particle quads.
layout(location = 0) in vec2 aCorner;    // -0.5..0.5 unit quad
layout(location = 1) in vec3 iPos;       // per-instance
layout(location = 2) in float iSize;
layout(location = 3) in vec4 iColor;     // rgb + alpha
layout(location = 4) in vec2 iParams;    // x = sprite id, y = vertical stretch

uniform mat4 uProj;
uniform mat4 uView;
uniform vec3 uCamRight;
uniform vec3 uCamUp;

out vec2 vUV;
out vec4 vColor;
flat out float vSprite;

void main() {
    vec3 world = iPos
        + uCamRight * (aCorner.x * iSize)
        + uCamUp * (aCorner.y * iSize * iParams.y);
    gl_Position = uProj * uView * vec4(world, 1.0);
    vUV = aCorner + 0.5;
    vColor = iColor;
    vSprite = iParams.x;
}
