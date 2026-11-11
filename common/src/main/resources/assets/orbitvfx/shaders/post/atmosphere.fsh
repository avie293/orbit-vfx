#version 330
#extension GL_ARB_separate_shader_objects : require

#include <minecraft:fog.glsl>

uniform sampler2D DepthSampler;

layout(std140) uniform Atmosphere {
    vec4 SunDirection;    // xyz direction, w scattering strength
    vec4 ScatterColor;
    vec4 FogShape;        // x density, y layer base relative to the camera, z height falloff, w max opacity
    vec4 NoiseOrigin;     // xy camera position, zw wind offset, both wrapped to 2048 blocks
    vec4 Settings;        // x depth range is [0, 1], y distance used for sky pixels
    mat4 InvViewRotation;
};

layout(location = 0) in vec2 texCoord;
layout(location = 1) flat in mat4 invProjMat;

layout(location = 0) out vec4 fragColor;

// One noise cell is 32 blocks, so 64 cells cover the 2048 block wrap period of NoiseOrigin.
const float CELL_SIZE = 32.0;
const float CELL_PERIOD = 64.0;

float hash(vec2 cell, float period) {
    uvec2 q = uvec2(mod(cell, period));
    uint h = (q.x * 1597334677u) ^ (q.y * 3812015801u);
    h *= h ^ (h >> 15u);
    return float(h) * (1.0 / 4294967295.0);
}

float valueNoise(vec2 p, float period) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    float a = hash(i, period);
    float b = hash(i + vec2(1.0, 0.0), period);
    float c = hash(i + vec2(0.0, 1.0), period);
    float d = hash(i + vec2(1.0, 1.0), period);
    return mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}

// Interleaved gradient noise, breaks up banding in the smooth gradients.
float dither(vec2 fragCoord) {
    return fract(52.9829189 * fract(dot(fragCoord, vec2(0.06711056, 0.00583715))));
}

float densityAt(float height, float falloff) {
    return exp(min(-falloff * height, 8.0));
}

void main() {
    float depth = texture(DepthSampler, texCoord).r;
    float ndcDepth = Settings.x > 0.5 ? depth : depth * 2.0 - 1.0;
    vec4 viewPos = invProjMat * vec4(texCoord * 2.0 - 1.0, ndcDepth, 1.0);
    vec3 relPos = (InvViewRotation * vec4(viewPos.xyz / viewPos.w, 0.0)).xyz;
    float dist = length(relPos);
    vec3 dir = relPos / dist;
    // Reversed depth: the depth buffer is cleared to 0, so anything at 0 is sky.
    if (depth <= 0.0) {
        dist = Settings.y;
    }

    // Integral of density(h) = exp(-falloff * h) along the ray, h measured from the top of the ground layer.
    float falloff = FogShape.z;
    float startHeight = -FogShape.y;
    float endHeight = startHeight + dir.y * dist;
    float slope = dir.y * falloff;
    float opticalDepth = abs(slope) > 1.0e-4
        ? (densityAt(startHeight, falloff) - densityAt(endHeight, falloff)) / slope
        : densityAt(startHeight, falloff) * dist;

    // Drifting patches: sampled halfway into the layer in front of the camera, two octaves moving apart.
    vec2 samplePos = NoiseOrigin.xy + dir.xz * min(dist, 64.0) * 0.5;
    float patches = valueNoise((samplePos + NoiseOrigin.zw) / CELL_SIZE, CELL_PERIOD) * 0.65
        + valueNoise((samplePos - NoiseOrigin.wz) / (CELL_SIZE * 0.5), CELL_PERIOD * 2.0) * 0.35;
    float density = FogShape.x * mix(0.3, 1.7, patches);

    float amount = min(1.0 - exp(-opticalDepth * density), FogShape.w);
    amount += (dither(gl_FragCoord.xy) - 0.5) / 255.0;

    // Mist takes the vanilla fog colour, pulled towards a cool white that follows the scene brightness.
    float brightness = dot(FogColor.rgb, vec3(0.2126, 0.7152, 0.0722));
    vec3 color = mix(FogColor.rgb, vec3(0.86, 0.9, 0.96) * (brightness * 1.1 + 0.02), 0.45);
    float phase = pow(max(dot(dir, SunDirection.xyz), 0.0), 6.0);
    color += ScatterColor.rgb * phase * SunDirection.w;

    fragColor = vec4(color, clamp(amount, 0.0, 1.0));
}
