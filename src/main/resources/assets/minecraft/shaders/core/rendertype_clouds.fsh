#version 150
#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec2 uvOffset;
uniform vec4 cloudColor;

in vec2 texCoord0;
in float vertexDistance;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 color = texture(Sampler0, texCoord0 + uvOffset) * cloudColor * ColorModulator;
    if(color.a < 0.01) discard;
    float d = length(texCoord0) * 512;
    fragColor = linear_fog(color, d, 128, 512, FogColor);
}
