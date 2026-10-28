package com.dan.thunder.gl;

import com.dan.ocean.gl.OceanGl;
import com.dan.sky.Sky;
import com.dan.surf.gl.BreakerGl;
import com.dan.thunder.scene.Brandung;
import com.jogamp.opengl.GL4;

/**
 * Die Brecher vor dem Strand auf der Grafikkarte (Phase 18a, aus Sylt): das Netz aus {@link Brandung} mit
 * {@link BreakerGl} (lokal zur Küste gedreht), in dem Loch, das die See dafür lässt. Licht vorbelichtet wie das Gelände.
 */
final class BrandungGl {
    final BreakerGl breakerGl = new BreakerGl();
    boolean ok;
    String info = "";

    void init(GL4 gl) {
        try {
            breakerGl.init(gl);
            ok = true;
            info = breakerGl.info();
        } catch (RuntimeException e) {
            info = "Brecher nicht verfügbar: " + e.getMessage();
            System.err.println("Thunder: " + info);
        }
    }

    /** Das Loch für das Netz in der See. */
    void feed(OceanGl.Light ol, Brandung b) {
        double[][] l = ok ? b.loch() : null;
        if (l == null) { ol.holeHalf = new double[]{0, 0, 10}; return; }
        ol.holeCenter = l[0];
        ol.holeAxis = l[1];
        ol.holeHalf = l[2];
    }

    /**
     * Licht der Brecher: Himmel oben und an der Kimm in Blickrichtung (Strahldichte aus {@code sky}), unter den Wolken
     * gedämpft wie das Rundumbild, das die See spiegelt (sonst stünde das Netz hell in der dunklen See).
     */
    static BreakerGl.Light licht(Sky.State st, double pre, double[] sun, Wetter wetter, boolean clouds, double[] sunFallback, double fogFallback, double blickGrad) {
        BreakerGl.Light l = new BreakerGl.Light();
        l.sunDir = sun.clone();
        if (st != null) {
            double wolken = clouds && wetter.cloudsOk ? wetter.clouds.brightnessFactor() : 1;
            double a = Math.toRadians(blickGrad);
            l.sunColor = Wetter.scale(st.sunColor, pre * wolken);
            l.skyTop = Wetter.scale(st.radiance(new double[]{0, 1, 0}), pre * wolken);
            l.skyHorizon = Wetter.scale(st.radiance(new double[]{Math.sin(a), 0.05, -Math.cos(a)}), pre * Math.sqrt(wolken));
            l.fogColor = Wetter.scale(st.skyIrradiance, pre / Math.PI * wolken);
            l.fogDistance = Wetter.fogDistance(st);
        } else {
            l.sunColor = sunFallback;
            l.fogDistance = fogFallback;
        }
        // Ostsee: grünlich-grau, im Flachen sandig [G]
        l.waterDeep = new double[]{0.018, 0.055, 0.055};
        l.waterShallow = new double[]{0.07, 0.16, 0.13};
        l.sand = new double[]{0.46, 0.40, 0.30};
        l.display = 0;
        return l;
    }

    void draw(GL4 gl, float[] vp, double[] eye, boolean reversedZ, Brandung b, Sky.State st, double pre, double[] sun, Wetter wetter,
              boolean clouds, double[] sunFallback, double fogFallback, double time, double blickGrad) {
        Brandung.Stueck s = ok ? b.stueck() : null;
        if (s == null) return;
        breakerGl.upload(gl, 0, s.netz);
        BreakerGl.View v = new BreakerGl.View();
        v.viewProj = Mat4.mul(vp, s.modell());
        double[] le = s.lokal(eye[0], eye[2]);
        v.eye = new double[]{le[0], eye[1], le[1]};
        v.reversedZ = reversedZ;
        BreakerGl.Light l = licht(st, pre, sun, wetter, clouds, sunFallback, fogFallback, blickGrad);
        l.sunDir = new double[]{sun[0] * Brandung.SX + sun[2] * Brandung.SZ, sun[1], sun[0] * Brandung.AX + sun[2] * Brandung.AZ};
        breakerGl.draw(gl, 0, v, l, time, 99);
    }

    void dispose(GL4 gl) { if (ok) breakerGl.dispose(gl); }
}
