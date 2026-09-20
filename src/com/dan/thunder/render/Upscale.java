package com.dan.thunder.render;

import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

/**
 * Schnelle 2×-Vergrößerung mit Zwischenwerten (bilinear-ähnlich) direkt auf den Pixeldaten.
 * Java2D skaliert in Software bilinear rund 15× langsamer; so bleibt die Szene auch ohne
 * Grafikbeschleunigung flüssig. Alle vier Kanäle werden gepackt gemittelt.
 */
final class Upscale {

    private Upscale() { }

    /** Vergrößert die Zeilen {@code y0..y1} von {@code src} in {@code dst} (doppelte Breite und Höhe). */
    static void twice(BufferedImage src, BufferedImage dst, int y0, int y1) {
        int[] s = ((DataBufferInt) src.getRaster().getDataBuffer()).getData();
        int[] d = ((DataBufferInt) dst.getRaster().getDataBuffer()).getData();
        int sw = src.getWidth(), sh = src.getHeight(), dw = dst.getWidth();
        y0 = Math.max(0, y0);
        y1 = Math.min(sh, y1);
        for (int y = y0; y < y1; y++) {
            int row = y * sw, next = (y + 1 < sh ? y + 1 : y) * sw;
            int o0 = (2 * y) * dw, o1 = o0 + dw;
            for (int x = 0; x < sw; x++) {
                int xr = x + 1 < sw ? x + 1 : x;
                int a = s[row + x], b = s[row + xr], c = s[next + x], e = s[next + xr];
                int ab = avg(a, b), ac = avg(a, c);
                int ox = 2 * x;
                d[o0 + ox] = a;
                d[o0 + ox + 1] = ab;
                d[o1 + ox] = ac;
                d[o1 + ox + 1] = avg(ab, avg(c, e));
            }
        }
    }

    /** Mittelwert zweier gepackter ARGB-Werte je Kanal (ohne Überlauf). */
    private static int avg(int p, int q) {
        return (((p ^ q) & 0xFEFEFEFE) >>> 1) + (p & q);
    }
}
