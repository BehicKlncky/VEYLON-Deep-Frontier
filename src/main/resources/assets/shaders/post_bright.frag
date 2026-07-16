#version 330 core
// Bloom bright-pass: keep only HDR energy above threshold.
in vec2 vUV;

uniform sampler2D uScene;

out vec4 FragColor;

void main() {
    vec3 c = texture(uScene, vUV).rgb;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    float keep = smoothstep(1.05, 1.9, luma);
    FragColor = vec4(c * keep, 1.0);
}
