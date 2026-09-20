package com.dan.thunder.render;

import com.dan.thunder.model.PaletteRole;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.util.Random;

/** Schräger Regen im Wind mit Spritzern auf dem Sand. Tropfen in Tiefen gestaffelt. */
final class RainField {

    private static final int MAX = 1200;
    private static final int MAX_SPLASH = 180;
    private final Random rnd = new Random(3L);
    private final float[] x = new float[MAX], y = new float[MAX], z = new float[MAX], stop = new float[MAX];
    private final float[] sx = new float[MAX_SPLASH], sy = new float[MAX_SPLASH], sAge = new float[MAX_SPLASH];
    private int splashCount;
    private double w = -1, h = -1;

    void paint(Graphics2D g, FrameState f, int drops) {
        if (f.w != w || f.h != h) {
            w = f.w;
            h = f.h;
            for (int i = 0; i < MAX; i++) {
                respawn(i, f, true);
            }
        }
        int n = Math.min(MAX, drops);
        double vx = f.wind * 380;
        Color c = f.palette.get(PaletteRole.RAIN);
        Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO, n * 2);
        for (int i = 0; i < n; i++) {
            double sp = 700 + z[i] * 900, len = 8 + z[i] * 16;
            y[i] += sp * f.dt;
            x[i] += vx * f.dt * (0.5 + z[i]);
            if (y[i] > stop[i]) {
                if (stop[i] > f.h * 0.8 && splashCount < MAX_SPLASH) {
                    sx[splashCount] = x[i];
                    sy[splashCount] = stop[i];
                    sAge[splashCount] = 0;
                    splashCount++;
                }
                respawn(i, f, false);
                continue;
            }
            double k = len / sp;
            path.moveTo(x[i], y[i]);
            path.lineTo(x[i] - vx * (0.5 + z[i]) * k, y[i] - len);
        }
        // Dünne, blasse Striche: ohne Kantenglättung kaum zu unterscheiden, aber viel schneller
        Object aa = g.getRenderingHint(RenderingHints.KEY_ANTIALIASING);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setStroke(new BasicStroke(1f));
        g.setColor(Sprites.alpha(c, Math.min(1, 0.16 + 0.45 * f.flash)));
        g.draw(path);
        Path2D.Float sp = new Path2D.Float();
        for (int i = splashCount - 1; i >= 0; i--) {
            sAge[i] += (float) (f.dt / 0.26);
            if (sAge[i] > 1) {
                splashCount--;
                sx[i] = sx[splashCount];
                sy[i] = sy[splashCount];
                sAge[i] = sAge[splashCount];
                continue;
            }
            float r = 1 + sAge[i] * 4;
            sp.moveTo(sx[i] - r, sy[i]);
            sp.quadTo(sx[i], sy[i] - r * 0.9, sx[i] + r, sy[i]);
        }
        g.setColor(Sprites.alpha(c, Math.min(1, 0.25 + 0.4 * f.flash)));
        g.draw(sp);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, aa);
    }

    private void respawn(int i, FrameState f, boolean anywhere) {
        z[i] = rnd.nextFloat();
        x[i] = (float) (rnd.nextDouble() * f.w * 1.3 - f.w * 0.15 - f.wind * f.w * 0.1);
        y[i] = (float) (anywhere ? rnd.nextDouble() * f.h : -20 - rnd.nextDouble() * f.h * 0.3);
        stop[i] = (float) (f.hor + (f.h - f.hor) * Math.pow(rnd.nextDouble(), 0.7));
    }
}
