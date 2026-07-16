#version 330 core
// Procedural sky: gradient, sun/moon disks, stars, drifting clouds, horizon haze.
in vec2 vNDC;

uniform mat4 uInvProjView;
uniform vec3 uCamPos;
uniform vec3 uSunDir;        // toward the sun (may be below horizon)
uniform vec3 uZenithColor;
uniform vec3 uHorizonColor;
uniform vec3 uSunColor;
uniform float uNight;        // 0 day .. 1 night
uniform float uCloudCover;   // 0..1
uniform float uCloudDark;    // storminess 0..1
uniform float uTime;
uniform float uFlash;        // lightning 0..1
uniform vec3 uFogColor;

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
    float a = hash(i);
    float b = hash(i + vec2(1, 0));
    float c = hash(i + vec2(0, 1));
    float d = hash(i + vec2(1, 1));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

float fbm(vec2 p) {
    float s = 0.0, a = 0.55;
    for (int i = 0; i < 4; i++) {
        s += vnoise(p) * a;
        p = p * 2.13 + vec2(19.7, 5.3);
        a *= 0.5;
    }
    return s;
}

void main() {
    vec4 far = uInvProjView * vec4(vNDC, 1.0, 1.0);
    vec3 dir = normalize(far.xyz / far.w - uCamPos);

    float up = clamp(dir.y, -1.0, 1.0);
    float t = pow(clamp(up, 0.0, 1.0), 0.55);
    vec3 col = mix(uHorizonColor, uZenithColor, t);
    // Below horizon: fade to fog color (ground haze).
    col = mix(uFogColor, col, smoothstep(-0.08, 0.02, up));

    // Sun disk + warm glow.
    float sd = dot(dir, uSunDir);
    float disk = smoothstep(0.9993, 0.9997, sd);
    float glow = pow(max(sd, 0.0), 160.0) * 0.6 + pow(max(sd, 0.0), 8.0) * 0.10;
    col += uSunColor * (disk * 22.0 + glow) * (1.0 - uNight);

    // Moon: opposite the sun, small cool disk with a soft rim.
    vec3 moonDir = -uSunDir;
    float md = dot(dir, moonDir);
    float moon = smoothstep(0.9996, 0.9999, md);
    float moonGlow = pow(max(md, 0.0), 300.0) * 0.35;
    col += vec3(0.72, 0.78, 0.92) * (moon * 3.2 + moonGlow) * uNight;

    // Stars: stable hash grid, gentle twinkle, only at night and above horizon.
    if (uNight > 0.05 && up > 0.0) {
        vec2 sp = dir.xz / (dir.y + 0.32) * 38.0;
        vec2 cell = floor(sp);
        float h = hash(cell);
        if (h > 0.985) {
            vec2 sub = fract(sp) - 0.5;
            float star = smoothstep(0.10, 0.02, length(sub));
            float tw = 0.7 + 0.3 * sin(uTime * 2.4 + h * 40.0);
            col += vec3(0.9, 0.93, 1.0) * star * tw * uNight * smoothstep(0.0, 0.15, up) * 1.6;
        }
    }

    // Two cloud layers on a high plane, drifting.
    if (up > 0.005 && uCloudCover > 0.01) {
        vec2 cp = uCamPos.xz + dir.xz / max(dir.y, 0.05) * 220.0;
        float n1 = fbm(cp * 0.004 + vec2(uTime * 0.008, uTime * 0.002));
        float n2 = fbm(cp * 0.011 + vec2(-uTime * 0.014, uTime * 0.006) + 37.0);
        float cover = mix(0.72, 0.30, uCloudCover);
        float cl = smoothstep(cover, cover + 0.28, n1 * 0.72 + n2 * 0.38);
        vec3 cloudCol = mix(vec3(1.04, 1.02, 1.0), vec3(0.34, 0.36, 0.40), uCloudDark);
        cloudCol = mix(cloudCol * (0.35 + 0.65 * (1.0 - uNight)), cloudCol * 0.10, uNight);
        // Sun-facing edges catch light.
        cloudCol += uSunColor * pow(max(sd, 0.0), 6.0) * 0.25 * (1.0 - uNight) * (1.0 - uCloudDark);
        float horizonFade = smoothstep(0.005, 0.12, up);
        col = mix(col, cloudCol, cl * horizonFade * 0.92);
    }

    col += vec3(uFlash) * 2.2;
    FragColor = vec4(col, 1.0);
}
