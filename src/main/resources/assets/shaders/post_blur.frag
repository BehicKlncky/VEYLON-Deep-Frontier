#version 330 core
// Separable 9-tap Gaussian blur, direction via uDir.
in vec2 vUV;

uniform sampler2D uScene;
uniform vec2 uDir; // (1/width, 0) or (0, 1/height)

out vec4 FragColor;

void main() {
    float w[5] = float[](0.227027, 0.1945946, 0.1216216, 0.054054, 0.016216);
    vec3 sum = texture(uScene, vUV).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        sum += texture(uScene, vUV + uDir * float(i)).rgb * w[i];
        sum += texture(uScene, vUV - uDir * float(i)).rgb * w[i];
    }
    FragColor = vec4(sum, 1.0);
}
