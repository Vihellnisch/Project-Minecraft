#version 150

// Anime impact frame: for a few frames the scene becomes a stark two-tone print (dark shapes as solid ink on a
// flat paper colour) with speed lines exploding outward from the impact point.

uniform sampler2D DiffuseSampler;

uniform float Strength;
uniform float Style;
uniform float OriginX;
uniform float OriginY;
uniform float Aspect;
uniform float GameTime;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

void main() {
    vec3 scene = texture(DiffuseSampler, texCoord).rgb;
    float lum = dot(scene, vec3(0.299, 0.587, 0.114));
    float ink = step(lum, 0.38);

    vec2 c = texCoord - vec2(OriginX, OriginY);
    c.x *= Aspect;
    float r = length(c);
    float a = (atan(c.y, c.x) + 3.14159265) / 6.2831853 * 96.0;
    float h = hash(vec2(floor(a), floor(GameTime * 30.0)));
    float lines = step(0.55, h) * smoothstep(0.12, 0.55, r) * step(abs(fract(a) - 0.5), 0.12 + 0.25 * h);

    vec3 paper = vec3(1.0);
    vec3 inkc = vec3(0.0);
    if (Style > 0.5 && Style < 1.5) { paper = vec3(0.9, 0.05, 0.08); inkc = vec3(0.02, 0.0, 0.0); }
    else if (Style > 1.5 && Style < 2.5) { paper = vec3(1.0, 0.86, 0.35); inkc = vec3(0.18, 0.08, 0.0); }
    else if (Style > 2.5 && Style < 3.5) { paper = vec3(0.55, 0.95, 1.0); inkc = vec3(0.0, 0.05, 0.15); }
    else if (Style > 3.5) { paper = vec3(0.75, 0.45, 1.0); inkc = vec3(0.04, 0.0, 0.08); }

    vec3 frame = mix(paper, inkc, ink);
    frame = mix(frame, inkc, lines * 0.9);
    fragColor = vec4(mix(scene, frame, Strength), 1.0);
}
