#version 150

// Shockwave rings: pixels are refracted outward on the wavefront and pulled in just behind it, with a slight
// RGB split and a faint bright rim, like a pressure wave tearing through the air. Up to 4 rings.

uniform sampler2D DiffuseSampler;

uniform float Count;
uniform float Aspect;
uniform float S0X; uniform float S0Y; uniform float S0R; uniform float S0A;
uniform float S1X; uniform float S1Y; uniform float S1R; uniform float S1A;
uniform float S2X; uniform float S2Y; uniform float S2R; uniform float S2A;
uniform float S3X; uniform float S3Y; uniform float S3R; uniform float S3A;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec4 rings[4] = vec4[](vec4(S0X, S0Y, S0R, S0A), vec4(S1X, S1Y, S1R, S1A), vec4(S2X, S2Y, S2R, S2A), vec4(S3X, S3Y, S3R, S3A));
    vec2 off = vec2(0.0);
    float glow = 0.0;
    for (int i = 0; i < 4; i++) {
        if (float(i) >= Count) break;
        vec4 s = rings[i];
        if (s.w <= 0.0 || s.z <= 0.0) continue;
        vec2 d = texCoord - s.xy;
        d.x *= Aspect;
        float dist = length(d);
        float width = max(0.015, s.z * 0.2);
        float x = (dist - s.z) / width;
        float ring = exp(-x * x);
        vec2 dir = d / max(dist, 1e-4);
        vec2 o = dir * ring * x * 0.04 * s.w;
        o.x /= Aspect;
        off -= o;
        glow += ring * s.w * 0.07;
    }
    vec3 col;
    col.r = texture(DiffuseSampler, texCoord + off * 1.2).r;
    col.g = texture(DiffuseSampler, texCoord + off).g;
    col.b = texture(DiffuseSampler, texCoord + off * 0.8).b;
    col += vec3(glow);
    fragColor = vec4(col, 1.0);
}
