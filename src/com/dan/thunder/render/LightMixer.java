package com.dan.thunder.render;

import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Mischt eine Licht-Textur (vormultipliziertes ARGB) mit einer Summe von Gaußglocken als Maske
 * direkt auf einen Puffer – in einem Durchgang über die Pixel, ohne Zwischenbilder.
 * Ersetzt Maskenbild + {@code DstIn} + Kopie, die in Java2D-Software ein Vielfaches kosten.
 */
final class LightMixer {

    /** Glocke über t = d²/r² (0..1), bei t = 1 auf 0 abgesenkt. */
    private static final int LUT = 1024;
    private static final float[] BELL = new float[LUT + 1];

    static {
        double edge = Math.exp(-2.42);
        for (int i = 0; i <= LUT; i++) {
            double t = (double) i / LUT;
            BELL[i] = (float) Math.max(0, (Math.exp(-2.42 * t) - edge) / (1 - edge));
        }
    }

    private LightMixer() { }

    /**
     * @param dst    Ziel (TYPE_INT_RGB oder TYPE_INT_ARGB_PRE)
     * @param tex    nahtlose Textur gleicher Höhe, vormultipliziert
     * @param shift  Verschiebung der Textur in ganzen Texeln (Ziel-x = Textur-x + shift)
     * @param area   zu bearbeitender Bereich im Ziel
     * @param lx     Mittelpunkte x, ly: y, lr: Radien, la: Stärken (0..1); n Lichter
     */
    static void mix(BufferedImage dst, BufferedImage tex, int shift, Rectangle area,
                    float[] lx, float[] ly, float[] lr, float[] la, int n) {
        int[] d = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
        int[] s = ((DataBufferInt) tex.getRaster().getDataBuffer()).getData();
        int dw = dst.getWidth(), tw = tex.getWidth();
        boolean opaque = dst.getType() == BufferedImage.TYPE_INT_RGB;
        int x0 = Math.max(0, area.x), x1 = Math.min(dw, area.x + area.width);
        int y0 = Math.max(0, area.y), y1 = Math.min(Math.min(dst.getHeight(), tex.getHeight()), area.y + area.height);
        float[] inv = new float[n];
        for (int i = 0; i < n; i++) {
            inv[i] = 1f / (lr[i] * lr[i]);
        }
        for (int y = y0; y < y1; y++) {
            int drow = y * dw, srow = y * tw;
            for (int x = x0; x < x1; x++) {
                int sx = x - shift;
                sx %= tw;
                if (sx < 0) {
                    sx += tw;
                }
                int p = s[srow + sx];
                int pa = p >>> 24;
                if (pa == 0) {
                    continue;
                }
                float m = 0;
                for (int i = 0; i < n; i++) {
                    float dx = x - lx[i], dy = y - ly[i];
                    float t = (dx * dx + dy * dy) * inv[i];
                    if (t < 1f) {
                        m += la[i] * BELL[(int) (t * LUT)];
                    }
                }
                if (m <= 0.004f) {
                    continue;
                }
                int k = m >= 1f ? 256 : (int) (m * 256);
                int sa = (pa * k) >> 8;
                int sr = (((p >> 16) & 0xFF) * k) >> 8;
                int sg = (((p >> 8) & 0xFF) * k) >> 8;
                int sb = ((p & 0xFF) * k) >> 8;
                int q = d[drow + x];
                int keep = 255 - sa;
                int r = sr + ((((q >> 16) & 0xFF) * keep) >> 8);
                int g = sg + ((((q >> 8) & 0xFF) * keep) >> 8);
                int b = sb + (((q & 0xFF) * keep) >> 8);
                int a = opaque ? 255 : sa + (((q >>> 24) * keep) >> 8);
                d[drow + x] = (Math.min(255, a) << 24) | (Math.min(255, r) << 16) | (Math.min(255, g) << 8) | Math.min(255, b);
            }
        }
    }
}
