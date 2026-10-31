#version 150

// Arsenal screen pass (1.4).
//   Deadeye : the world drained to amber and blood, a heavy vignette, grain and chromatic fringes, pulsing with the heart
//   Ink     : iai impact frames: 1 = white on black, 2 = black on white, 3 = red and black
//   Tint    : the E.G.O cut-in grade in the sin's colour (TintR/G/B)
//   Speed   : web swing speed lines streaming in from the edges

uniform sampler2D DiffuseSampler;

uniform float Deadeye;
uniform float Beat;
uniform float Ink;
uniform float InkSeed;
uniform float Tint;
uniform float TintR;
uniform float TintG;
uniform float TintB;
uniform float Speed;
uniform float Aspect;
uniform float GameTime;

in vec2 texCoord;

out vec4 fragColor;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec2 uv = texCoord;
    vec2 c = uv - 0.5;
    c.x *= Aspect;
    float r = length(c);
    vec3 col = texture(DiffuseSampler, uv).rgb;

    if (Deadeye > 0.001) {
        // chromatic fringes toward the edges, swelling with the heartbeat
        vec2 dir = (uv - 0.5) * (0.006 + 0.006 * Beat) * Deadeye;
        vec3 ca = vec3(texture(DiffuseSampler, uv + dir).r, col.g, texture(DiffuseSampler, uv - dir).b);
        float l = luma(ca);
        // amber highlights, blood-red mids, near-black shadows
        vec3 graded = mix(vec3(0.05, 0.01, 0.0), vec3(0.75, 0.18, 0.06), smoothstep(0.0, 0.45, l));
        graded = mix(graded, vec3(1.0, 0.82, 0.5), smoothstep(0.45, 0.95, l));
        graded = mix(graded, ca * vec3(1.1, 0.75, 0.55), 0.25);
        float grain = (hash(uv * 800.0 + fract(GameTime * 7.0)) - 0.5) * 0.12;
        graded += grain;
        float vig = smoothstep(0.35, 1.0, r * (1.15 + 0.12 * Beat));
        graded = mix(graded, vec3(0.12, 0.0, 0.0), vig * 0.85);
        col = mix(col, graded, Deadeye);
    }

    if (Tint > 0.001) {
        vec3 t = vec3(TintR, TintG, TintB);
        float l = luma(col);
        vec3 graded = mix(vec3(0.02), t, smoothstep(0.05, 0.7, l));
        graded = mix(graded, vec3(1.0), smoothstep(0.75, 1.0, l));
        float vig = smoothstep(0.4, 1.1, r);
        graded = mix(graded, t * 0.15, vig * 0.8);
        col = mix(col, graded, Tint);
    }

    if (Speed > 0.01) {
        float ang = atan(c.y, c.x);
        float streak = step(0.82, hash(vec2(floor(ang * 70.0), floor(GameTime * 24.0))));
        float edge = smoothstep(0.3, 0.75, r);
        col = mix(col, vec3(1.0), streak * edge * Speed * 0.35);
        col *= 1.0 - 0.25 * Speed * smoothstep(0.5, 0.95, r);
    }

    if (Ink > 0.5) {
        float l = luma(col);
        float n = hash(floor(uv * vec2(480.0, 270.0)) + InkSeed);
        float edge = smoothstep(0.40, 0.46, l + (n - 0.5) * 0.06);
        if (Ink < 1.5) col = vec3(edge);
        else if (Ink < 2.5) col = vec3(1.0 - edge);
        else col = mix(vec3(0.0), vec3(0.95, 0.08, 0.06), edge);
    }

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
