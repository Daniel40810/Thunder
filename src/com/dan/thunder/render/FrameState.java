package com.dan.thunder.render;

import com.dan.thunder.model.Palette;
import com.dan.thunder.model.Scene;
import com.dan.thunder.storm.Strike;

import java.util.ArrayList;
import java.util.List;

/** Werte eines Bildes, die alle Maler teilen. Wird pro Bild neu befüllt. */
final class FrameState {
    double w, h, hor, span;
    /** Szenenzeit in Sekunden. */
    double t;
    double dt;
    double nowMs;
    double wind;
    /** Summe der Blitzhelligkeit, gewichtet nach Nähe (0 … 1,3). */
    double flash;
    /** Helligkeit des Glutkerns. */
    double ember;
    /** Regenmenge 0..1 (0 = aus). */
    double rain;
    /** Stärkster Blitz dieses Bildes und wie stark er den Strand erhellt. */
    Strike key;
    double keyLight;
    double keyX;
    Palette palette;
    Scene scene;
    final List<Light> lights = new ArrayList<>();

    double shoreY(double x) {
        return h * scene.shoreY() + Math.sin(x / w * 3 + 0.5) * h * 0.012;
    }
}
