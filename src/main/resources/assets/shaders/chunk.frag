#version 330 core
// Forward PBR-lite voxel shading: sun/moon + hemisphere ambient + block light,
// PCF shadow map, AO, wetness/frost accents, distance fog. HDR output.
in vec2 vUV;
flat in float vLayer;
in vec3 vNormal;
in float vSky;
in float vBlock;
in float vAO;
in float vFoliage;
in vec3 vTint;
in float vDist;
in vec4 vSunSpace;
in vec3 vWorldPos;

uniform sampler2DArray uTiles;
uniform sampler2DShadow uShadow;
uniform float uShadowsOn;
uniform float uAoOn;         // QA comparison toggle; 1 = use baked vertex AO

uniform vec3 uLightDir;      // direction TOWARD the light
uniform vec3 uLightColor;
uniform vec3 uAmbientSky;
uniform vec3 uAmbientGround;
uniform vec3 uBlockLightColor;
uniform vec3 uCamPos;
uniform vec3 uFoliageTint;
uniform float uWetness;      // 0..1 rain-wet surfaces
uniform float uFrost;        // 0..1 frost accent on top faces
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;
uniform vec2 uLayerProps[160]; // x = emissive, y = roughness

out vec4 FragColor;

float shadowFactor() {
    if (uShadowsOn < 0.5) {
        return 1.0;
    }
    vec3 proj = vSunSpace.xyz / vSunSpace.w * 0.5 + 0.5;
    if (proj.x < 0.0 || proj.x > 1.0 || proj.y < 0.0 || proj.y > 1.0 || proj.z > 1.0) {
        return 1.0;
    }
    float bias = 0.0016;
    float sum = 0.0;
    vec2 texel = vec2(1.0 / 2048.0);
    for (int x = -1; x <= 1; x++) {
        for (int y = -1; y <= 1; y++) {
            sum += texture(uShadow, vec3(proj.xy + vec2(x, y) * texel, proj.z - bias));
        }
    }
    return sum / 9.0;
}

void main() {
    vec4 albedo = texture(uTiles, vec3(vUV, vLayer));
    if (albedo.a < 0.5) {
        discard;
    }
    vec2 props = uLayerProps[int(vLayer + 0.5)];
    float emissive = props.x;
    float rough = props.y;

    vec3 base = albedo.rgb * vTint;
    base = mix(base, base * uFoliageTint, vFoliage);

    vec3 N = normalize(vNormal);
    float topness = clamp(N.y, 0.0, 1.0);

    // Wet surfaces darken and get glossier; frost whitens upward faces.
    float wet = uWetness * (0.35 + 0.65 * topness) * vSky;
    base *= 1.0 - 0.28 * wet;
    rough = mix(rough, 0.18, wet * 0.8);
    base = mix(base, vec3(0.82, 0.87, 0.95), uFrost * topness * vSky * 0.55);

    // Sun/moon diffuse, gated by heightmap skylight (caves) and the shadow map.
    float skyGate = smoothstep(0.03, 0.85, vSky);
    float ndl = max(dot(N, uLightDir), 0.0);
    // Cross foliage: allow some translucent backlight.
    ndl = mix(ndl, ndl * 0.6 + 0.4, vFoliage * 0.6);
    float sunVis = skyGate * shadowFactor();
    vec3 diffuse = uLightColor * (ndl * sunVis);

    // Hemisphere ambient by normal, gated softly by skylight (floor keeps
    // under-canopy and shallow caves navigable).
    float hemi = N.y * 0.5 + 0.5;
    vec3 ambient = mix(uAmbientGround, uAmbientSky, hemi) * mix(0.28, 1.0, vSky);

    // Torch/campfire light: cubic falloff keeps fire pools tight and warm.
    vec3 blockLight = uBlockLightColor * (vBlock * vBlock * vBlock * 1.5);

    float ao = mix(1.0, vAO, uAoOn);
    vec3 light = (diffuse + ambient) * ao + blockLight * (0.6 + 0.4 * ao);
    vec3 color = base * light;

    // Cheap Blinn-Phong specular for wet/icy/metal surfaces.
    float specStrength = mix(0.55, 0.02, rough);
    if (specStrength > 0.03) {
        vec3 V = normalize(uCamPos - vWorldPos);
        vec3 H = normalize(uLightDir + V);
        float specPow = mix(96.0, 8.0, rough);
        float spec = pow(max(dot(N, H), 0.0), specPow) * specStrength * sunVis;
        color += uLightColor * spec;
    }

    // Emissive surfaces ignore lighting and push into bloom range.
    color += albedo.rgb * emissive * 2.6;

    float fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fog = fog * fog * (3.0 - 2.0 * fog);
    color = mix(color, uFogColor, fog);

    FragColor = vec4(color, 1.0);
}
