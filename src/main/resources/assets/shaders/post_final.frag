#version 330 core
// Final composite: bloom add, exposure, ACES tonemap, grade, vignettes, FXAA-lite, gamma.
in vec2 vUV;

uniform sampler2D uScene;      // HDR
uniform sampler2D uBloom;      // blurred bright pass
uniform float uBloomStrength;  // 0 disables
uniform float uExposure;
uniform float uSaturation;
uniform vec3 uTint;            // multiplicative grade
uniform float uContrast;
// Vignettes: x=damage(red) y=cold(blue) z=poison(green) w=smoke/heat(gray-orange)
uniform vec4 uVignettes;
uniform float uHeat;           // heat vs smoke selector for w channel
uniform float uUnderwater;
uniform float uFxaaOn;
uniform vec2 uTexel;

out vec4 FragColor;

vec3 aces(vec3 x) {
    const float a = 2.51, b = 0.03, c = 2.43, d = 0.59, e = 0.14;
    return clamp((x * (a * x + b)) / (x * (c * x + d) + e), 0.0, 1.0);
}

vec3 tonemapAt(vec2 uv) {
    vec3 hdr = texture(uScene, uv).rgb;
    if (uBloomStrength > 0.001) {
        hdr += texture(uBloom, uv).rgb * uBloomStrength;
    }
    return aces(hdr * uExposure);
}

float lumaAt(vec2 uv) {
    return dot(tonemapAt(uv), vec3(0.299, 0.587, 0.114));
}

void main() {
    vec3 col = tonemapAt(vUV);

    // FXAA-lite: blur along detected edges using tonemapped luma.
    if (uFxaaOn > 0.5) {
        float lC = dot(col, vec3(0.299, 0.587, 0.114));
        float lN = lumaAt(vUV + vec2(0.0, -uTexel.y));
        float lS = lumaAt(vUV + vec2(0.0, uTexel.y));
        float lE = lumaAt(vUV + vec2(uTexel.x, 0.0));
        float lW = lumaAt(vUV + vec2(-uTexel.x, 0.0));
        float lMin = min(lC, min(min(lN, lS), min(lE, lW)));
        float lMax = max(lC, max(max(lN, lS), max(lE, lW)));
        if (lMax - lMin > max(0.05, lMax * 0.12)) {
            vec2 dir = normalize(vec2(abs(lN - lS), abs(lE - lW)) + 1e-5);
            vec3 blur = (tonemapAt(vUV + dir * uTexel) + tonemapAt(vUV - dir * uTexel)) * 0.5;
            col = mix(col, blur, 0.65);
        }
    }

    if (uUnderwater > 0.01) {
        col = mix(col, col * vec3(0.45, 0.75, 0.95) + vec3(0.0, 0.03, 0.07), uUnderwater);
    }

    // Grade: tint, saturation, gentle contrast.
    col *= uTint;
    float luma = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(luma), col, uSaturation);
    col = (col - 0.5) * uContrast + 0.5;

    // Radial state vignettes (kept soft; readability first).
    vec2 d = vUV - 0.5;
    float edge = smoothstep(0.28, 0.72, length(d) * 1.35);
    col = mix(col, vec3(0.45, 0.02, 0.02), edge * uVignettes.x);
    col = mix(col, vec3(0.35, 0.55, 0.85), edge * uVignettes.y * 0.7);
    col = mix(col, vec3(0.35, 0.48, 0.10), edge * uVignettes.z * 0.7);
    vec3 wCol = mix(vec3(0.22, 0.22, 0.22), vec3(0.75, 0.32, 0.05), uHeat);
    col = mix(col, wCol, edge * uVignettes.w * 0.8);

    // Base cinematic vignette, very subtle.
    col *= 1.0 - edge * 0.13;

    // Gamma encode (backbuffer is non-sRGB).
    col = pow(max(col, 0.0), vec3(1.0 / 2.2));
    FragColor = vec4(col, 1.0);
}
