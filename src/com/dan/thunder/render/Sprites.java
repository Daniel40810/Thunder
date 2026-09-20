package com.dan.thunder.render;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.util.Random;

/** Vorgerenderte Bildbausteine. */
final class Sprites {

    static final int PUFF_VARIANTS = 6;
    private static final int PUFF_SIZE = 160;

    private Sprites() { }

    /** Weicher runder Lichtfleck in {@code c}. */
    static BufferedImage glow(Color c, int size) {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB_PRE);
        Graphics2D g = img.createGraphics();
        float r = size / 2f;
        g.setPaint(new RadialGradientPaint(r, r, r, new float[] { 0f, 0.25f, 1f },
                new Color[] { alpha(c, 255), alpha(c, 115), alpha(c, 0) }));
        g.fillRect(0, 0, size, size);
        g.dispose();
        return img;
    }

    /** Wolkenballen als weiße Alpha-Maske: Haufen weicher Kreise, je Variante andere Form. */
    static BufferedImage[] puffs(long seed) {
        Random r = new Random(seed);
        BufferedImage[] out = new BufferedImage[PUFF_VARIANTS];
        for (int v = 0; v < PUFF_VARIANTS; v++) {
            BufferedImage img = new BufferedImage(PUFF_SIZE, PUFF_SIZE, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = img.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int n = 14 + r.nextInt(9);
            for (int i = 0; i < n; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = Math.pow(r.nextDouble(), 0.7) * 0.2;
                float x = (float) ((0.5 + Math.cos(a) * d * 1.15) * PUFF_SIZE);
                float y = (float) ((0.52 + Math.sin(a) * d * 0.75) * PUFF_SIZE);
                // Radius so begrenzt, dass kein Kreis über den Rand der Kachel ragt (sonst harte Kanten)
                double maxR = Math.min(Math.min(x, PUFF_SIZE - x), Math.min(y, PUFF_SIZE - y)) / PUFF_SIZE - 0.01;
                float rad = (float) (Math.min(maxR, 0.12 + r.nextDouble() * 0.16) * PUFF_SIZE);
                g.setPaint(new RadialGradientPaint(x, y, rad, new float[] { 0f, 0.72f, 1f },
                        new Color[] { new Color(255, 255, 255, 242), new Color(255, 255, 255, 206), new Color(255, 255, 255, 0) }));
                g.fillOval((int) (x - rad), (int) (y - rad), (int) (rad * 2), (int) (rad * 2));
            }
            g.dispose();
            out[v] = img;
        }
        return out;
    }

    /** Kachelbare fraktale Rauschmaske (weiß, Alpha = Rauschwert), für Wolkenstruktur. */
    static BufferedImage noise(int w, int h, long seed) {
        Random r = new Random(seed);
        int[] grid = { 6, 12, 24, 48 };
        double[] amp = { 0.5, 0.26, 0.15, 0.09 };
        double[] acc = new double[w * h];
        for (int o = 0; o < grid.length; o++) {
            int gx = grid[o], gy = Math.max(2, grid[o] * h / Math.max(1, w) + 2);
            double[] v = new double[gx * gy];
            for (int i = 0; i < v.length; i++) {
                v[i] = r.nextDouble();
            }
            for (int y = 0; y < h; y++) {
                double fy = (double) y / h * (gy - 1);
                int y0 = (int) fy;
                int y1 = Math.min(gy - 1, y0 + 1);
                double ty = smooth(fy - y0);
                for (int x = 0; x < w; x++) {
                    double fx = (double) x / w * gx;
                    int x0 = (int) fx % gx, x1 = (x0 + 1) % gx; // horizontal nahtlos
                    double tx = smooth(fx - (int) fx);
                    double a0 = v[y0 * gx + x0] + (v[y0 * gx + x1] - v[y0 * gx + x0]) * tx;
                    double a1 = v[y1 * gx + x0] + (v[y1 * gx + x1] - v[y1 * gx + x0]) * tx;
                    acc[y * w + x] += (a0 + (a1 - a0) * ty) * amp[o];
                }
            }
        }
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int i = 0; i < acc.length; i++) {
            double n = Math.max(0, Math.min(1, (acc[i] - 0.45) * 2.2));
            int a = (int) (n * n * 255);
            img.setRGB(i % w, i / w, (a << 24) | 0xFFFFFF);
        }
        return img;
    }

    private static double smooth(double t) {
        return t * t * (3 - 2 * t);
    }

    static Color alpha(Color c, int a) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, a)));
    }

    static Color alpha(Color c, double a) {
        return alpha(c, (int) Math.round(a * 255));
    }

    static void clear(Graphics2D g, int w, int h) {
        g.setComposite(AlphaComposite.Clear);
        g.fillRect(0, 0, w, h);
        g.setComposite(AlphaComposite.SrcOver);
    }
}
