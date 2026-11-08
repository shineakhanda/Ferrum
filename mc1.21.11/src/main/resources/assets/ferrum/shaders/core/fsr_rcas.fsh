#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// Ferrum FSR 1 upscaler, pass 2: RCAS (robust contrast-adaptive sharpening) at output resolution.
// Implementation of the algorithm AMD published with FidelityFX Super Resolution 1.0 (MIT licensed):
// the sharpening lobe is limited per pixel so it can never push a channel outside the range of its
// 4 neighbours, which avoids the halos of plain unsharp masking. FSR_SHARPNESS is in stops
// (0 = strongest, 2 = mild) and is baked into the pipeline as a shader define.

#ifndef FSR_SHARPNESS
#define FSR_SHARPNESS 0.2
#endif

#define FSR_RCAS_LIMIT (0.25 - (1.0 / 16.0))

void main() {
    ivec2 size = textureSize(InSampler, 0);
    ivec2 maxP = size - 1;
    ivec2 p = clamp(ivec2(texCoord * vec2(size)), ivec2(0), maxP);

    //   b
    // d e f
    //   h
    vec3 b = texelFetch(InSampler, clamp(p + ivec2(0, -1), ivec2(0), maxP), 0).rgb;
    vec3 d = texelFetch(InSampler, clamp(p + ivec2(-1, 0), ivec2(0), maxP), 0).rgb;
    vec3 e = texelFetch(InSampler, p, 0).rgb;
    vec3 f = texelFetch(InSampler, clamp(p + ivec2(1, 0), ivec2(0), maxP), 0).rgb;
    vec3 h = texelFetch(InSampler, clamp(p + ivec2(0, 1), ivec2(0), maxP), 0).rgb;

    vec3 mn4 = min(min(b, d), min(f, h));
    vec3 mx4 = max(max(b, d), max(f, h));

    vec3 hitMin = min(mn4, e) / (4.0 * mx4 + 1.0e-5);
    vec3 hitMax = (1.0 - max(mx4, e)) / (4.0 * mn4 - 4.0 - 1.0e-5);
    vec3 lobeRGB = max(-hitMin, hitMax);
    float lobe = max(-FSR_RCAS_LIMIT, min(max(lobeRGB.r, max(lobeRGB.g, lobeRGB.b)), 0.0))
        * exp2(-float(FSR_SHARPNESS));

    vec3 color = (lobe * (b + d + f + h) + e) / (4.0 * lobe + 1.0);
    fragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
}
