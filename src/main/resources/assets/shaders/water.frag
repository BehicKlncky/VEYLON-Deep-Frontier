#version 330 core
// Stylized water: animated normals, fresnel, depth color, shoreline foam, sun glints.
in vec3 vWorldPos;
in float vSky;
in float vBlock;
in float vDepth;
in float vDist;

uniform vec3 uCamPos;
uniform vec3 uLightDir;
uniform vec3 uLightColor;
uniform vec3 uAmbientSky;
uniform vec3 uDeepColor;
uniform vec3 uShallowColor;
uniform float uTime;
uniform vec3 uFogColor;
uniform float uFogStart;
uniform float uFogEnd;

out vec4 FragColor;

float hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1, 0)), f.x),
               mix(hash(i + vec2(0, 1)), hash(i + vec2(1, 1)), f.x), f.y);
}

void main() {
    // Two scrolling noise octaves perturb the normal.
    vec2 p = vWorldPos.xz;
    float n1 = vnoise(p * 0.9 + vec2(uTime * 0.22, uTime * 0.14));
    float n2 = vnoise(p * 2.3 - vec2(uTime * 0.31, uTime * 0.09));
    vec3 N = normalize(vec3((n1 - 0.5) * 0.5 + (n2 - 0.5) * 0.28, 1.0,
                            (n2 - 0.5) * 0.5 + (n1 - 0.5) * 0.22));

    vec3 V = normalize(uCamPos - vWorldPos);
    float fresnel = pow(1.0 - max(dot(V, vec3(0, 1, 0)), 0.0), 2.2);

    float depthT = clamp(vDepth / 5.0, 0.0, 1.0);
    vec3 waterCol = mix(uShallowColor, uDeepColor, depthT);

    float daylight = max(vSky, 0.06);
    vec3 col = waterCol * (uAmbientSky * 0.85 + uLightColor * 0.4) * daylight;
    col += vec3(1.0, 0.72, 0.35) * vBlock * vBlock * 0.5; // firelight on water

    // Sun glints.
    vec3 H = normalize(uLightDir + V);
    float spec = pow(max(dot(N, H), 0.0), 140.0) * 1.6;
    col += uLightColor * spec * daylight;

    // Shoreline foam: shallow band + noise breakup.
    float foamBand = 1.0 - smoothstep(0.0, 0.75, vDepth);
    float foamN = vnoise(p * 3.4 + vec2(uTime * 0.5, -uTime * 0.35));
    float foam = foamBand * smoothstep(0.45, 0.75, foamN + foamBand * 0.3);
    col = mix(col, vec3(0.9, 0.95, 1.0) * (0.4 + 0.6 * daylight), foam * 0.8);

    float alpha = mix(0.58, 0.88, fresnel);
    alpha = mix(alpha, 0.45, foamBand * 0.4 * (1.0 - foam));
    alpha = max(alpha, foam * 0.85);

    float fog = clamp((vDist - uFogStart) / (uFogEnd - uFogStart), 0.0, 1.0);
    fog = fog * fog * (3.0 - 2.0 * fog);
    col = mix(col, uFogColor, fog);
    FragColor = vec4(col, alpha);
}
