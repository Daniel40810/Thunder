package com.dan.thunder.gl;

import com.dan.ocean.Ocean;
import com.dan.ocean.gl.OceanGl;
import com.dan.sky.Sky;
import com.dan.thunder.scene.Gelaende;
import com.jogamp.opengl.GL;
import com.jogamp.opengl.GL4;

import java.util.Locale;

/**
 * Die Ostsee aus {@code com.dan.ocean} (Phase 18a, nach Sylt): kurze ablandige Windsee aus Südwest und die auflandige
 * Restsee aus Nordost als Dünung (Verdrahtung), keine Tide. Der Grund ist das gerechnete Gelände ({@link Gelaende#bett},
 * R16, 2 m): zum Ufer hin werden die Wellen flacher, im seichten Wasser scheint der Sand durch; draußen −15 m. Vor dem
 * Hauptblick lässt die See ein Loch, in dem das Netz der Brecher liegt ({@code BrandungGl}).
 */
public final class Meer {
    public final Ocean ocean = Ocean.beaufort(4);
    final OceanGl oceanGl = new OceanGl();
    boolean ok;
    String check = "";
    int bedTex;

    Meer() { ocean.seed = 2026; }

    void init(GL4 gl, boolean compute, java.util.function.Consumer<String> err) {
        bedTex = bedTexture(gl, Gelaende.bett());
        if (!compute) {
            System.err.println("Thunder: Meer braucht Compute-Shader");
            err.accept("Das Meer (com.dan.ocean) braucht Compute-Shader (OpenGL 4.3).");
            return;
        }
        try {
            oceanGl.init(gl);
            ok = true;
            System.out.println("Thunder: Meer " + oceanGl.info());
        } catch (RuntimeException e) {
            System.err.println("Thunder: Meer nicht verfügbar: " + e.getMessage());
            err.accept("Das Meer (com.dan.ocean) lässt sich nicht starten.\n" + e.getMessage());
        }
    }

    static int bedTexture(GL4 gl, short[] h) {
        int n = Gelaende.BETT_N;
        int[] tex = new int[1];
        gl.glGenTextures(1, tex, 0);
        gl.glBindTexture(GL.GL_TEXTURE_2D, tex[0]);
        gl.glTexStorage2D(GL.GL_TEXTURE_2D, 1, GL4.GL_R16, n, n);
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 2);
        gl.glTexSubImage2D(GL.GL_TEXTURE_2D, 0, 0, 0, n, n, GL4.GL_RED, GL.GL_UNSIGNED_SHORT, java.nio.ShortBuffer.wrap(h));
        gl.glPixelStorei(GL.GL_UNPACK_ALIGNMENT, 4);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MIN_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_MAG_FILTER, GL.GL_LINEAR);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_S, GL.GL_CLAMP_TO_EDGE);
        gl.glTexParameteri(GL.GL_TEXTURE_2D, GL.GL_TEXTURE_WRAP_T, GL.GL_CLAMP_TO_EDGE);
        gl.glBindTexture(GL.GL_TEXTURE_2D, 0);
        return tex[0];
    }

    void draw(GL4 gl, float[] vp, double[] eye, boolean reversedZ, Sky.State st, double pre, int panoTex, int cloudTex, double[] cloudRect,
              double[] sun, double[] sunFallback, double fogFallback, java.util.function.Consumer<OceanGl.Light> extra) {
        oceanGl.update(gl, ocean);
        OceanGl.View ov = new OceanGl.View();
        ov.viewProj = vp;
        ov.eye = eye.clone();
        ov.reversedZ = reversedZ;
        OceanGl.Light ol = new OceanGl.Light();
        ol.sunDir = sun;
        ol.skyTexture = panoTex;
        if (st != null) {
            ol.sunColor = Wetter.scale(st.sunColor, pre);
            ol.moonDir = st.moonDir;
            ol.moonColor = Wetter.scale(st.moonColor, pre);
            ol.fogDistance = Wetter.fogDistance(st);
            if (cloudTex != 0) {
                ol.shadowTexture = cloudTex;
                ol.shadowRect = cloudRect;
            }
        } else {
            ol.sunColor = sunFallback;
            ol.fogDistance = fogFallback;
        }
        double[] c = Gelaende.bettEcke();
        ol.bedTexture = bedTex;
        ol.bedRect = new double[]{c[0], c[1], Gelaende.BETT_KANTE};
        ol.bedScale = Gelaende.BETT_MAX - Gelaende.BETT_MIN;
        ol.bedOffset = Gelaende.BETT_MIN;
        ol.bedOutside = Gelaende.DRAUSSEN;
        ol.bedTurbidity = 0.8;
        ol.bedColor = new double[]{0.26, 0.22, 0.15};
        ol.tileBreak = 0.6;
        if (extra != null) extra.accept(ol);
        gl.glDepthFunc(reversedZ ? GL.GL_GREATER : GL.GL_LESS);
        oceanGl.draw(gl, ocean, ov, ol);
    }

    String describe() {
        return ok ? String.format(Locale.GERMAN, "See (ocean) %s · Wind %.1f m/s aus %03.0f° · Restsee %.2f m, %.1f s aus %03.0f°%s",
                oceanGl.info(), ocean.windSpeed, ocean.windFromDeg, ocean.swellHeight, ocean.swellPeriod, ocean.swellFromDeg,
                check.isEmpty() ? "" : " · " + check) : "!See: com.dan.ocean nicht gestartet";
    }

    void dispose(GL4 gl) {
        if (ok) oceanGl.dispose(gl);
        Gpu.deleteTextures(gl, bedTex);
    }
}
