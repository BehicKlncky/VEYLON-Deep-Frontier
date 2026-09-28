#version 330 core
// Flat-colored cuboid parts with the same light rig as terrain.
in vec3 vNormal;
in float vDist;
in vec4 vSunSpace;
in vec3 vWorldPos;
in vec3 vLocal;

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
// A burning body, set per body and zero for everything else.
uniform float uScorch;      // 0..1 how charred it is; never fades
uniform float uBurnGlow;    // 0..1 embers glowing along the edges of the char while it burns
uniform float uFireLight;   // 0..1 its own flames lighting it
uniform float uFireTime;    // particle clock, for the embers' pulse

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

float hash13(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float vnoise3(vec3 p) {
    vec3 i = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash13(i), hash13(i + vec3(1, 0, 0)), f.x),
                   mix(hash13(i + vec3(0, 1, 0)), hash13(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash13(i + vec3(0, 0, 1)), hash13(i + vec3(1, 0, 1)), f.x),
                   mix(hash13(i + vec3(0, 1, 1)), hash13(i + vec3(1, 1, 1)), f.x), f.y), f.z);
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
    vec3 embers = vec3(0.0);
    if (uScorch > 0.001) {
        // Char spreads in blotches as the scorch grows, over a body that darkens
        // as a whole with soot; the part's own colour seeds its pattern, so the
        // blotches differ from box to box and species keep their colours
        // between them.
        vec3 p = vLocal * 4.5 + uColor * 37.0;
        // Warped, so the char spreads in ragged patches rather than round spots.
        vec3 q = p + vec3(vnoise3(p * 1.7), vnoise3(p * 1.7 + 5.2), vnoise3(p * 1.7 + 9.1)) * 0.9;
        float n = vnoise3(q) * 0.6 + vnoise3(q * 2.7 + 11.0) * 0.4;
        float threshold = 0.97 - 0.62 * uScorch;
        float charred = smoothstep(threshold - 0.03, threshold + 0.06, n);
        // Soot darkens the whole body, and more so round the char, so it fades in rather than stops.
        vec3 soot = base * mix(1.0, 0.55, uScorch)
                * (1.0 - 0.25 * smoothstep(threshold - 0.25, threshold, n));
        vec3 charColour = mix(base * 0.16, vec3(0.05, 0.04, 0.035), 0.6);
        base = mix(soot, charColour, charred);
        // Embers glow only in a thin line where the char meets the unburned surface.
        float edge = charred * (1.0 - smoothstep(threshold + 0.01, threshold + 0.05, n));
        embers = vec3(1.0, 0.34, 0.05) * edge * uBurnGlow
                * (0.6 + 0.4 * vnoise3(p * 0.8 + vec3(0.0, uFireTime * 2.0, 0.0)));
    }
    vec3 fireLight = vec3(1.0, 0.52, 0.2) * (uFireLight * 0.8);

    vec3 color = base * (diffuse + ambient + blockLight + fireLight);
    color += base * uEmissive * 2.6 + embers * 1.1;

    float fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fog = fog * fog * (3.0 - 2.0 * fog);
    color = mix(color, uFogColor, fog);
    FragColor = vec4(color, 1.0);
}
