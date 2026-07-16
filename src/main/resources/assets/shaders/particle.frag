#version 330 core
// Procedural particle sprites: 0 = soft puff, 1 = hard dot, 2 = streak, 3 = spark.
in vec2 vUV;
in vec4 vColor;
flat in float vSprite;

out vec4 FragColor;

void main() {
    vec2 d = vUV - 0.5;
    float a;
    int sprite = int(vSprite + 0.5);
    if (sprite == 0) {
        a = smoothstep(0.5, 0.08, length(d));                       // soft puff
    } else if (sprite == 1) {
        a = smoothstep(0.42, 0.3, length(d));                       // hard dot
    } else if (sprite == 2) {
        a = smoothstep(0.5, 0.2, abs(d.x) * 4.0)                    // vertical streak
          * smoothstep(0.52, 0.35, abs(d.y));
    } else {
        a = smoothstep(0.5, 0.0, length(d));                        // bright spark
        a *= a;
    }
    a *= vColor.a;
    if (a < 0.01) {
        discard;
    }
    FragColor = vec4(vColor.rgb, a);
}
