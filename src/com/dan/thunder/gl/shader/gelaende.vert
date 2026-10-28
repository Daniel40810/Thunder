// gelaende.vert — das gerechnete Gelände des Ostseestrands (Phase 18a): Ort, Normale und Anteile der Arten je Ecke (Gelaende.java),
// Erdkrümmung wie die Bauten.
#include "common.glsl"
layout(location = 0) in vec3 aPos;
layout(location = 1) in vec3 aNrm;
layout(location = 2) in vec4 aArt;   // Anteile: nasser Sand, Düne, Hinterland, Seegrund (Rest Sand)
out vec3 vWorld;
out vec3 vNormal;
out vec4 vArt;

void main() {
    vWorld = aPos;
    vNormal = aNrm;
    vArt = aArt;
    vec2 dh = aPos.xz - uCam.xz;
    gl_Position = uVP * vec4(aPos.x, aPos.y - dot(dh, dh) / (2.0 * EARTH_R), aPos.z, 1.0);
}
