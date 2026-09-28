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
uniform float uBurn;           // 0..1 how strongly the player's own body burns
uniform float uBurnTime;       // particle clock; holds still while paused
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

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float vnoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1.0, 0.0)), u.x),
               mix(hash21(i + vec2(0.0, 1.0)), hash21(i + vec2(1.0, 1.0)), u.x), u.y);
}

// The player's own flames at the edges of the view: tongues licking up from
// the bottom edge, higher at the lower corners, and thin flames up the lower
// sides. The middle of the view — crosshair, target, the way ahead — stays
// clear; the flames reach at most about a third of the way up at the corners.
// One row of separate tongues along the bottom edge, columns of them across:
// each its own height, rhythm and sway, narrowing to a wavering tip.
// Returns the flame's strength, 1 in its hot base.
float tongueRow(vec2 uv, float columns, float offset, float corner) {
    float t = uBurnTime;
    float c = uv.x * columns + offset;
    float id = floor(c);
    float r = hash21(vec2(id, offset));
    float height = uBurn * (0.045 + 0.08 * r + 0.16 * corner)
            * (0.75 + 0.25 * sin(t * (3.0 + 4.0 * r) + id * 1.7));
    float y = uv.y / max(height, 1e-3);
    float sway = (vnoise(vec2(id * 3.1, uv.y * 7.0 - t * 2.6)) - 0.5) * 0.6 * y;
    float halfWidth = 0.46 * pow(max(1.0 - y, 0.0), 0.7);
    float edge = halfWidth * (0.75 + 0.5 * vnoise(vec2(c * 4.0, uv.y * 11.0 - t * 5.0)));
    return smoothstep(edge, edge * 0.45, abs(fract(c) - 0.5 - sway)) * (1.0 - smoothstep(0.85, 1.0, y));
}

float burnFringe(vec2 uv) {
    float side = min(uv.x, 1.0 - uv.x);
    float corner = 1.0 - smoothstep(0.0, 0.3, side);
    // Two rows offset from each other, so the tongues never line up in a regular comb.
    float f = max(tongueRow(uv, 17.0, 0.0, corner), 0.85 * tongueRow(uv, 11.0, 0.37, corner));
    // A hot band along the very bottom, where the tongues rise from.
    f = max(f, 1.0 - smoothstep(0.0, uBurn * 0.035, uv.y));
    // Thin flames up the lower sides.
    float sideReach = uBurn * (0.025 + 0.04 * vnoise(vec2(uv.y * 9.0 - uBurnTime * 2.2, uv.x > 0.5 ? 7.0 : 1.0)));
    float sides = (1.0 - smoothstep(0.4, 1.0, side / max(sideReach, 1e-3))) * (1.0 - smoothstep(0.15, 0.55, uv.y));
    return clamp(max(f, sides), 0.0, 1.0);
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

    if (uBurn > 0.001) {
        // Heat first: a warm wash towards the edges, then the flames over it.
        col += vec3(0.22, 0.08, 0.0) * edge * uBurn;
        float f = burnFringe(vUV);
        // Red at the tips, orange, then yellow-white in the hot base of each tongue.
        vec3 flameCol = mix(vec3(0.72, 0.13, 0.02), vec3(1.0, 0.55, 0.1), smoothstep(0.1, 0.6, f));
        flameCol = mix(flameCol, vec3(1.0, 0.88, 0.55), smoothstep(0.75, 1.0, f));
        col = mix(col, flameCol, smoothstep(0.0, 0.35, f) * 0.9);
    }

    // Base cinematic vignette, very subtle.
    col *= 1.0 - edge * 0.13;

    // Gamma encode (backbuffer is non-sRGB).
    col = pow(max(col, 0.0), vec3(1.0 / 2.2));
    FragColor = vec4(col, 1.0);
}
