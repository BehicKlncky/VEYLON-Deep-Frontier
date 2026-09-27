#version 330 core
// Instanced camera-facing particle quads.
layout(location = 0) in vec2 aCorner;    // -0.5..0.5 unit quad
layout(location = 1) in vec3 iPos;       // per-instance
layout(location = 2) in float iSize;
layout(location = 3) in vec4 iColor;     // rgb + alpha
layout(location = 4) in vec2 iParams;    // x = sprite id, y = ordinary stretch

layout(location = 5) in vec3 iVelocity;  // a flame tongue (sprite 4): lean x, flicker number, lean z

uniform mat4 uProj;
uniform mat4 uView;
uniform vec3 uCamRight;
uniform vec3 uCamUp;
uniform float uTime;      // particle clock: holds still while the game is paused
uniform float uFogStart;
uniform float uFogEnd;

out vec2 vUV;
out vec4 vColor;
flat out float vSprite;
flat out float vSeed;

void main() {
    vec3 world = iPos
        + uCamRight * (aCorner.x * iSize)
        + uCamUp * (aCorner.y * iSize * iParams.y);
    vec4 viewPos = uView * vec4(world, 1.0);
    float visibility = 1.0;
    int sprite = int(iParams.x + 0.5);
    vSeed = 0.0;
    if (sprite == 2) {
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
    } else if (sprite == 4) {
        // A flame tongue standing on its base at iPos: it rises along world up
        // (tilted a little towards the view's up, so it never turns edge-on seen
        // from above), leans with the air past it, and flickers in height on its
        // own rhythm, so neighbouring tongues never pulse together.
        float seed = iVelocity.y;
        float rise = aCorner.y + 0.5;
        vec3 up = normalize(vec3(0.0, 1.0, 0.0) + uCamUp * 0.35);
        float flicker = 0.84
                + 0.16 * sin(uTime * (7.0 + 5.0 * fract(seed * 0.713)) + seed * 4.3)
                + 0.08 * sin(uTime * (16.0 + 7.0 * fract(seed * 0.377)) + seed * 1.1);
        float height = iSize * iParams.y * flicker;
        vec3 lean = vec3(iVelocity.x, 0.0, iVelocity.z);
        world = iPos + uCamRight * (aCorner.x * iSize) + up * (rise * height) + lean * (rise * rise * height);
        viewPos = uView * vec4(world, 1.0);
        float distanceToCamera = length((uView * vec4(iPos, 1.0)).xyz);
        float fog = clamp((distanceToCamera - uFogStart) / max(uFogEnd - uFogStart, 1.0), 0.0, 1.0);
        fog = fog * fog * (3.0 - 2.0 * fog);
        // Flames right against the camera thin out rather than filling the view.
        visibility = smoothstep(0.3, 1.1, distanceToCamera) * (1.0 - fog);
        vSeed = seed;
    }
    gl_Position = uProj * viewPos;
    vUV = aCorner + 0.5;
    vColor = vec4(iColor.rgb, iColor.a * visibility);
    vSprite = iParams.x;
}
