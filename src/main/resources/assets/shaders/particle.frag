#version 330 core
// Procedural particle sprites: 0 = soft puff, 1 = hard dot, 2 = streak, 3 = spark, 4 = flame tongue.
in vec2 vUV;
in vec4 vColor;
flat in float vSprite;
flat in float vSeed;

uniform float uTime;
// The body-flame pass blends premultiplied (ONE, ONE_MINUS_SRC_ALPHA): flames add
// their light and also cover this much of what is behind them, so they stay
// orange against a bright day; 0 is purely additive. Glows stay additive.
uniform float uPremultiply;
uniform float uOcclusion;

out vec4 FragColor;

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

// A tongue of flame, base at the bottom of the quad (y = 0) and tip at the top:
// a rounded base narrowing to a tip, its edge torn by noise that scrolls upward
// so the silhouette never repeats, the tip breaking into separate licks, a hot
// yellow-white core at the base cooling through the instance's orange to red
// at the rim. Additive, so the rim fades into whatever burns behind it.
vec4 flame(vec2 uv) {
    float s = vSeed;
    float t = uTime;
    float y = uv.y;
    // Sideways wobble growing towards the tip.
    float wobble = (vnoise(vec2(y * 2.4 - t * 3.1, s * 7.0)) - 0.5) * 0.32 * y;
    float x = uv.x - 0.5 - wobble;
    float profile = 0.5 * pow(max(1.0 - y, 0.0), 0.75) * smoothstep(-0.06, 0.2, y);
    float n1 = vnoise(vec2(x * 6.0 + s * 3.0, y * 5.0 - t * 4.2 + s * 11.0));
    float n2 = vnoise(vec2(x * 13.0 - s, y * 11.0 - t * 7.3));
    float edge = profile * (0.7 + 0.45 * n1 + 0.2 * n2);
    float body = smoothstep(edge, edge * 0.5, abs(x));
    // Tearing near the tip: licks come loose instead of a smooth point.
    float tear = smoothstep(0.6, 1.0, y)
            * smoothstep(0.42, 0.72, vnoise(vec2(x * 4.0 + s * 5.0, y * 3.2 - t * 5.5)));
    float a = body * (1.0 - tear) * smoothstep(0.0, 0.06, y);
    float core = clamp(1.0 - abs(x) / max(edge, 1e-3), 0.0, 1.0) * (1.0 - y);
    // Colours may be brighter than 1 (flames are light): hue is green over red.
    float brightness = max(vColor.r, 1.0);
    vec3 rim = vColor.rgb * vec3(0.9, 0.42, 0.28);
    vec3 col = mix(rim, vColor.rgb, smoothstep(0.0, 0.45, core));
    float yellow = clamp(vColor.g / brightness, 0.0, 1.0);
    col = mix(col, vec3(1.0, 0.94, 0.74) * brightness, smoothstep(0.5, 0.95, core) * yellow);
    return vec4(col, a);
}

void main() {
    vec2 d = vUV - 0.5;
    float a;
    vec3 rgb = vColor.rgb;
    int sprite = int(vSprite + 0.5);
    if (sprite == 0) {
        a = smoothstep(0.5, 0.08, length(d));                       // soft puff
    } else if (sprite == 1) {
        a = smoothstep(0.42, 0.3, length(d));                       // hard dot
    } else if (sprite == 2) {
        a = (1.0 - smoothstep(0.2, 0.5, abs(d.x)))                    // velocity-aligned streak
          * (1.0 - smoothstep(0.35, 0.52, abs(d.y)));
    } else if (sprite == 4) {
        vec4 f = flame(vUV);                                          // flame tongue
        rgb = f.rgb;
        a = f.a;
    } else {
        a = smoothstep(0.5, 0.0, length(d));                        // bright spark
        a *= a;
    }
    a *= vColor.a;
    if (a < 0.01) {
        discard;
    }
    if (uPremultiply > 0.5) {
        FragColor = vec4(rgb * a, sprite == 4 ? a * uOcclusion : 0.0);
    } else {
        FragColor = vec4(rgb, a);
    }
}
