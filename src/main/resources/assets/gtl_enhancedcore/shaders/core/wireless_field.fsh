#version 150

uniform sampler2D Sampler0;
in vec4 vertexColor;
in vec2 texCoord0;
out vec4 fragColor;

void main() {
    vec4 light = texture(Sampler0, texCoord0) * vertexColor;
    // Soft light must survive below vanilla position_color_tex's 0.1 alpha cutoff.
    if (light.a < 0.001) discard;
    // Intentionally independent of scene ColorModulator: the transmitter is self luminous.
    fragColor = light;
}
