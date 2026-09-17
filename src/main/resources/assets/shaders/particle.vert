#version 330 core
// Instanced camera-facing particle quads.
layout(location = 0) in vec2 aCorner;    // -0.5..0.5 unit quad
layout(location = 1) in vec3 iPos;       // per-instance
layout(location = 2) in float iSize;
layout(location = 3) in vec4 iColor;     // rgb + alpha
layout(location = 4) in vec2 iParams;    // x = sprite id, y = ordinary stretch

layout(location = 5) in vec3 iVelocity;

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
    vec4 viewPos = uView * vec4(world, 1.0);
    float visibility = 1.0;
    if (int(iParams.x + 0.5) == 2) {
        vec3 center = (uView * vec4(iPos, 1.0)).xyz;
        vec3 velocity = mat3(uView) * iVelocity;
        float speed = length(velocity);
        vec3 axis = speed > 0.001 ? velocity / speed : vec3(0, -1, 0);
        float projected = length(axis.xy);
        // End-on streaks become short readable droplets; neither normalization can be zero.
        // Keep the strip counterclockwise under the scene's back-face culling.
        vec3 side = projected > 0.02 ? vec3(axis.y, -axis.x, 0) / projected : vec3(-1, 0, 0);
        if (projected <= 0.02) axis = vec3(0, -1, 0);
        float streakLength = clamp(speed * 0.022, 0.16, 0.55) * (iSize / 0.05);
        if (projected <= 0.02) streakLength = iSize * 1.8;
        // The simulated point is the leading tip. The trailing streak cannot poke through its next surface.
        viewPos = vec4(center + side * (aCorner.x * iSize)
                + axis * ((aCorner.y - 0.5) * streakLength), 1.0);
        float distanceToCamera = length(center);
        visibility = smoothstep(0.8, 2.0, distanceToCamera)
                * (1.0 - smoothstep(18.0, 38.0, distanceToCamera));
    }
    gl_Position = uProj * viewPos;
    vUV = aCorner + 0.5;
    vColor = vec4(iColor.rgb, iColor.a * visibility);
    vSprite = iParams.x;
}
