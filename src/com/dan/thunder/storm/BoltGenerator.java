package com.dan.thunder.storm;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Erzeugt Blitzformen durch Mittelpunktverschiebung mit Seitenästen. Gleicher Seed und gleiche
 * Endpunkte ergeben denselben Blitz – so lässt sich ein Eintrag aus TH_STRIKE wieder abspielen.
 */
public final class BoltGenerator {

    private BoltGenerator() { }

    /** Wolke → Boden. */
    public static List<BoltSegment> cloudToGround(Random rnd, double x0, double y0, double x1, double y1) {
        double len = Math.hypot(x1 - x0, y1 - y0);
        BoltSegment main = segment(displace(rnd, x0, y0, x1, y1, len * 0.16, 7), 1f, 0f, 1f, true);
        List<BoltSegment> out = new ArrayList<>();
        out.add(main);
        branches(rnd, out, main, 3 + rnd.nextInt(6), 0.45f, 2, len);
        return out;
    }

    /** Waagerechter Spinnenblitz an der Wolkenunterseite. */
    public static List<BoltSegment> spider(Random rnd, double x0, double y0, int dir, double len) {
        double x1 = x0 + dir * len, y1 = y0 + (rnd.nextDouble() - 0.3) * len * 0.2;
        BoltSegment main = segment(displace(rnd, x0, y0, x1, y1, len * 0.12, 7), 0.7f, 0f, 1f, true);
        List<BoltSegment> out = new ArrayList<>();
        out.add(main);
        branches(rnd, out, main, 5 + rnd.nextInt(6), 0.4f, 2, len * 0.9);
        return out;
    }

    private static void branches(Random rnd, List<BoltSegment> out, BoltSegment parent, int count,
                                 float width, int depth, double baseLen) {
        int n = parent.size();
        for (int k = 0; k < count; k++) {
            double f = 0.05 + rnd.nextDouble() * 0.75;
            int i = (int) (f * (n - 5));
            double px = parent.xs()[i], py = parent.ys()[i];
            double qx = parent.xs()[i + 4], qy = parent.ys()[i + 4];
            double ang = Math.atan2(qy - py, qx - px) + (rnd.nextBoolean() ? -1 : 1) * (0.3 + rnd.nextDouble() * 0.75);
            double l = baseLen * (0.15 + rnd.nextDouble() * 0.35) * (1 - f * 0.6);
            double ex = px + Math.cos(ang) * l, ey = py + Math.sin(ang) * l;
            BoltSegment seg = segment(displace(rnd, px, py, ex, ey, l * 0.22, depth > 1 ? 5 : 4), width,
                    (float) (parent.start() + parent.span() * f), (float) (parent.span() * (1 - f) * 0.8), false);
            out.add(seg);
            if (depth > 1) {
                branches(rnd, out, seg, 1 + rnd.nextInt(2), width * 0.55f, depth - 1, l);
            }
        }
    }

    private static BoltSegment segment(double[][] pts, float w, float start, float span, boolean main) {
        float[] xs = new float[pts[0].length], ys = new float[pts[0].length];
        for (int i = 0; i < xs.length; i++) {
            xs[i] = (float) pts[0][i];
            ys[i] = (float) pts[1][i];
        }
        return new BoltSegment(xs, ys, w, start, Math.max(0.05f, span), main);
    }

    /** Mittelpunktverschiebung: jede Stufe halbiert die Strecken und versetzt die Mitte quer. */
    static double[][] displace(Random rnd, double x0, double y0, double x1, double y1, double disp, int levels) {
        double[] xs = { x0, x1 }, ys = { y0, y1 };
        for (int l = 0; l < levels; l++) {
            int n = xs.length;
            double[] nx = new double[n * 2 - 1], ny = new double[n * 2 - 1];
            for (int i = 0; i < n - 1; i++) {
                double dx = xs[i + 1] - xs[i], dy = ys[i + 1] - ys[i];
                double len = Math.hypot(dx, dy);
                if (len == 0) {
                    len = 1;
                }
                double o = (rnd.nextDouble() * 2 - 1) * disp;
                nx[2 * i] = xs[i];
                ny[2 * i] = ys[i];
                nx[2 * i + 1] = (xs[i] + xs[i + 1]) / 2 - dy / len * o;
                ny[2 * i + 1] = (ys[i] + ys[i + 1]) / 2 + dx / len * o;
            }
            nx[nx.length - 1] = xs[n - 1];
            ny[ny.length - 1] = ys[n - 1];
            xs = nx;
            ys = ny;
            disp *= 0.53;
        }
        return new double[][] { xs, ys };
    }
}
