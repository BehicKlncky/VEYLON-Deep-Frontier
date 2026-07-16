#version 330 core
// Flat-colored cuboid parts with the same light rig as terrain.
in vec3 vNormal;
in float vDist;
in vec4 vSunSpace;
in vec3 vWorldPos;

uniform vec3 uColor;
uniform vec3 uTintMul;   // whole-model tint (carcass rot, hurt flash)
uniform float uEmissive;
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform vec3 uAmbientSky;
uniform vec3 uAmbientGround;
uniform vec3 uBlockLightColor;
uniform float uSkyLight;    // 0..1 heightmap skylight at the entity
uniform float uBlockLight;  // 0..1 torch light at the entity
uniform sampler2DShadow uShadow;
uniform float uShadowsOn;
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

out vec4 FragColor;

float shadowFactor() {
    if (uShadowsOn < 0.5) {
        return 1.0;
    }
    vec3 proj = vSunSpace.xyz / vSunSpace.w * 0.5 + 0.5;
    if (proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0 || proj.z > 1.0) {
        return 1.0;
    }
    float sum = 0.0;
    vec2 texel = vec2(1.0 / 2048.0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            sum += texture(uShadow, vec3(proj.xy + vec2(x, y) * texel, proj.z - 0.002));
        }
    }
    return sum / 9.0;
}

void main() {
    vec3 N = normalize(vNormal);
    float skyGate = smoothstep(0.03, 0.85, uSkyLight);
    float ndl = max(dot(N, uLightDir), 0.0);
    vec3 diffuse = uLightColor * (ndl * skyGate * shadowFactor());
    float hemi = N.y * 0.5 + 0.5;
    vec3 ambient = mix(uAmbientGround, uAmbientSky, hemi) * mix(0.15, 1.0, uSkyLight);
    vec3 blockLight = uBlockLightColor * (uBlockLight * uBlockLight * uBlockLight * 1.5);

    vec3 base = uColor * uTintMul;
    vec3 color = base * (diffuse + ambient + blockLight);
    color += base * uEmissive * 2.6;

    float fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fog = fog * fog * (3.0 - 2.0 * fog);
    color = mix(color, uFogColor, fog);
    FragColor = vec4(color, 1.0);
}
