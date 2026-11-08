#version 330

uniform sampler2D InSampler;

in vec2 texCoord;

out vec4 fragColor;

// Ferrum FSR 1 upscaler, pass 1: EASU (edge-adaptive spatial upsampling).
// Implementation of the algorithm AMD published as FidelityFX Super Resolution 1.0 (MIT licensed):
// per output pixel, analyse luma gradients of the 2x2 input quad around it to find the local edge
// direction and strength, then reconstruct with a 12-tap Lanczos-like kernel stretched along that
// edge, and clamp to the quad's min/max to prevent ringing. Written from the published description;
// no AMD source is included.

float ferrumLuma(vec3 c) {
    return c.g + 0.5 * (c.r + c.b);
}

vec3 ferrumFetch(ivec2 p, ivec2 maxP) {
    return texelFetch(InSampler, clamp(p, ivec2(0), maxP), 0).rgb;
}

// Accumulates the edge direction and length contribution of one of the four centre pixels.
//     a
//   b c d
//     e
void ferrumEasuSet(inout vec2 dir, inout float len, float w,
                   float lA, float lB, float lC, float lD, float lE) {
    float dc = lD - lC;
    float cb = lC - lB;
    float lenX = max(abs(dc), abs(cb));
    lenX = lenX > 0.0 ? 1.0 / lenX : 0.0;
    float dirX = lD - lB;
    dir.x += dirX * w;
    lenX = clamp(abs(dirX) * lenX, 0.0, 1.0);
    lenX *= lenX;
    len += lenX * w;

    float ec = lE - lC;
    float ca = lC - lA;
    float lenY = max(abs(ec), abs(ca));
    lenY = lenY > 0.0 ? 1.0 / lenY : 0.0;
    float dirY = lE - lA;
    dir.y += dirY * w;
    lenY = clamp(abs(dirY) * lenY, 0.0, 1.0);
    lenY *= lenY;
    len += lenY * w;
}

void ferrumEasuTap(inout vec3 aC, inout float aW, vec2 off, vec2 dir, vec2 len2,
                   float lob, float clp, vec3 c) {
    vec2 v = vec2(off.x * dir.x + off.y * dir.y, off.x * -dir.y + off.y * dir.x);
    v *= len2;
    float d2 = min(dot(v, v), clp);
    float wB = 0.4 * d2 - 1.0;
    float wA = lob * d2 - 1.0;
    wB *= wB;
    wA *= wA;
    wB = 1.5625 * wB - 0.5625;
    float w = wB * wA;
    aC += c * w;
    aW += w;
}

void main() {
    ivec2 inSize = textureSize(InSampler, 0);
    ivec2 maxP = inSize - 1;
    vec2 pp = texCoord * vec2(inSize) - 0.5;
    vec2 fp = floor(pp);
    pp -= fp;
    ivec2 o = ivec2(fp);

    //     b c
    //   e f g h
    //   i j k l
    //     n o
    vec3 b = ferrumFetch(o + ivec2(0, -1), maxP);
    vec3 c = ferrumFetch(o + ivec2(1, -1), maxP);
    vec3 e = ferrumFetch(o + ivec2(-1, 0), maxP);
    vec3 f = ferrumFetch(o + ivec2(0, 0), maxP);
    vec3 g = ferrumFetch(o + ivec2(1, 0), maxP);
    vec3 h = ferrumFetch(o + ivec2(2, 0), maxP);
    vec3 i = ferrumFetch(o + ivec2(-1, 1), maxP);
    vec3 j = ferrumFetch(o + ivec2(0, 1), maxP);
    vec3 k = ferrumFetch(o + ivec2(1, 1), maxP);
    vec3 l = ferrumFetch(o + ivec2(2, 1), maxP);
    vec3 n = ferrumFetch(o + ivec2(0, 2), maxP);
    vec3 oo = ferrumFetch(o + ivec2(1, 2), maxP);

    float bL = ferrumLuma(b), cL = ferrumLuma(c), eL = ferrumLuma(e), fL = ferrumLuma(f);
    float gL = ferrumLuma(g), hL = ferrumLuma(h), iL = ferrumLuma(i), jL = ferrumLuma(j);
    float kL = ferrumLuma(k), lL = ferrumLuma(l), nL = ferrumLuma(n), oL = ferrumLuma(oo);

    vec2 dir = vec2(0.0);
    float len = 0.0;
    ferrumEasuSet(dir, len, (1.0 - pp.x) * (1.0 - pp.y), bL, eL, fL, gL, jL);
    ferrumEasuSet(dir, len, pp.x * (1.0 - pp.y), cL, fL, gL, hL, kL);
    ferrumEasuSet(dir, len, (1.0 - pp.x) * pp.y, fL, iL, jL, kL, nL);
    ferrumEasuSet(dir, len, pp.x * pp.y, gL, jL, kL, lL, oL);

    vec2 dir2 = dir * dir;
    float dirR = dir2.x + dir2.y;
    bool zro = dirR < (1.0 / 32768.0);
    dirR = zro ? 1.0 : inversesqrt(dirR);
    dir.x = zro ? 1.0 : dir.x;
    dir *= dirR;

    len = len * 0.5;
    len *= len;
    float stretch = (dir.x * dir.x + dir.y * dir.y) / max(abs(dir.x), abs(dir.y));
    vec2 len2 = vec2(1.0 + (stretch - 1.0) * len, 1.0 - 0.5 * len);
    float lob = 0.5 + ((1.0 / 4.0 - 0.04) - 0.5) * len;
    float clp = 1.0 / lob;

    vec3 aC = vec3(0.0);
    float aW = 0.0;
    ferrumEasuTap(aC, aW, vec2(0.0, -1.0) - pp, dir, len2, lob, clp, b);
    ferrumEasuTap(aC, aW, vec2(1.0, -1.0) - pp, dir, len2, lob, clp, c);
    ferrumEasuTap(aC, aW, vec2(-1.0, 1.0) - pp, dir, len2, lob, clp, i);
    ferrumEasuTap(aC, aW, vec2(0.0, 1.0) - pp, dir, len2, lob, clp, j);
    ferrumEasuTap(aC, aW, vec2(0.0, 0.0) - pp, dir, len2, lob, clp, f);
    ferrumEasuTap(aC, aW, vec2(-1.0, 0.0) - pp, dir, len2, lob, clp, e);
    ferrumEasuTap(aC, aW, vec2(1.0, 1.0) - pp, dir, len2, lob, clp, k);
    ferrumEasuTap(aC, aW, vec2(2.0, 1.0) - pp, dir, len2, lob, clp, l);
    ferrumEasuTap(aC, aW, vec2(2.0, 0.0) - pp, dir, len2, lob, clp, h);
    ferrumEasuTap(aC, aW, vec2(1.0, 0.0) - pp, dir, len2, lob, clp, g);
    ferrumEasuTap(aC, aW, vec2(1.0, 2.0) - pp, dir, len2, lob, clp, oo);
    ferrumEasuTap(aC, aW, vec2(0.0, 2.0) - pp, dir, len2, lob, clp, n);

    vec3 mn = min(min(f, g), min(j, k));
    vec3 mx = max(max(f, g), max(j, k));
    vec3 color = aW != 0.0 ? aC / aW : f;
    fragColor = vec4(clamp(color, mn, mx), 1.0);
}
