// gelaende.frag — Farben des Ostseestrands (Phase 18a): trockener Sand mit Rippeln, nasser Sand am Wasser (dunkel,
// glänzend), Düne mit Strandhafer (fleckig grün-gelb), Hinterland (Kiefern und Heide, dunkel), Seegrund. Licht wie die
// Bauten: Sonne und Mond vorbelichtet, Himmelslicht, Wolkenschatten, Dunst. Nasse Flächen spiegeln den Himmel nach
// Fresnel. Die Anteile der Arten kommen je Ecke (Gelaende.java): nasser Sand, Düne, Hinterland, Seegrund, Rest Sand.
#include "common.glsl"
in vec3 vWorld;
in vec3 vNormal;
in vec4 vArt;
out vec4 frag;

uniform sampler2D uCloudShadow;
uniform vec4 uCloudRect;
uniform float uWet;          // Nässe nach Regen 0..1 (ab Phase 18b)

const float PI = 3.14159265;

float hash2(vec2 p) { return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453); }
float vnoise(vec2 x) {
    vec2 i = floor(x), f = x - i;
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash2(i), hash2(i + vec2(1, 0)), f.x), mix(hash2(i + vec2(0, 1)), hash2(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 x) { return 0.5 * vnoise(x) + 0.3 * vnoise(x * 2.1 + 3.7) + 0.2 * vnoise(x * 4.3 - 1.3); }
float sicht(float s, float pxm) { return 1.0 - smoothstep(0.12 * s, 0.4 * s, pxm); }

void main() {
    vec3 toEye = uCam - vWorld;
    float dist = length(toEye);
    vec3 V = toEye / max(dist, 1e-4);
    vec3 n = normalize(vNormal);
    float pxm = max(length(fwidth(vWorld)), 1e-4);
    vec4 w = clamp(vArt, 0.0, 1.0);
    vec3 sand = vec3(0.46, 0.40, 0.29) * (0.92 + 0.16 * mix(0.5, fbm(vWorld.xz * 0.35), sicht(3.0, pxm)));
    // Rippeln im trockenen Sand (quer zum Wind aus Südwest)
    float rip = sin(dot(vWorld.xz, vec2(0.707, 0.707)) * 9.0 + vnoise(vWorld.xz * 0.6) * 6.0);
    sand *= mix(1.0, 0.94 + 0.06 * rip, sicht(0.7, pxm));
    vec3 nass = vec3(0.20, 0.17, 0.12);
    float gras = smoothstep(0.35, 0.65, fbm(vWorld.xz * 0.5)) * 0.85;
    vec3 hafer = mix(vec3(0.24, 0.22, 0.12), vec3(0.15, 0.17, 0.08), fbm(vWorld.xz * 1.7));
    vec3 duene = mix(sand, hafer, gras);
    vec3 land = mix(vec3(0.06, 0.08, 0.045), vec3(0.11, 0.10, 0.06), fbm(vWorld.xz * 0.05));
    vec3 grund = vec3(0.30, 0.26, 0.19);
    float ws = max(0.0, 1.0 - w.x - w.y - w.z - w.w);
    vec3 alb = sand * ws + nass * w.x + duene * w.y + land * w.z + grund * w.w;
    float wet = w.x;
    // Nässe nach Regen dunkelt alles Sandige
    alb *= 1.0 - 0.45 * uWet * (1.0 - w.z);
    wet = max(wet, uWet * 0.6 * (1.0 - w.z));

    float cloud = uCloudRect.w > 0.0 ? textureLod(uCloudShadow, (vWorld.xz - uCloudRect.xy) / uCloudRect.z, 0.0).r : 1.0;
    float ndl = max(dot(n, uSun), 0.0);
    vec3 col;
    if (uSkyMode == 1) {
        vec3 direct = (sunLight() * ndl + moonLight() * max(dot(n, uMoonDir), 0.0)) * cloud;
        vec3 amb = uSkyAmb * (0.55 + 0.45 * n.y);
        col = alb * (direct / PI + amb);
    } else {
        float B = skyBrightness(sunElevDeg());
        vec3 amb = vec3(0.30, 0.38, 0.52) * B / 0.32 * (0.55 + 0.45 * n.y);
        col = alb * (sunLight() * ndl * cloud + amb);
    }
    if (wet > 0.0) {
        vec3 r = reflect(-V, n);
        float F = 0.02 + 0.98 * pow(1.0 - max(dot(n, V), 0.0), 5.0);
        vec3 refl = skyColor(normalize(vec3(r.x, max(r.y, 0.02), r.z)));
        col = mix(col, refl, F * wet * 0.8);
    }
    col = aerial(col, -V, dist);
    frag = vec4(col, 1.0);
}
