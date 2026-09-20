package com.dan.thunder.render;

import com.dan.thunder.model.Palette;
import com.dan.thunder.model.PaletteRole;
import com.dan.thunder.storm.BoltSegment;
import com.dan.thunder.storm.Strike;
import com.dan.thunder.storm.StrikeKind;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Graphics2D;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;
import java.util.List;

/**
 * Zeichnet die Blitzkanäle in vier Leuchtschichten (breiter Schein → weißer Kern).
 * Während des Leitblitzes wachsen die Äste sichtbar nach unten, danach leuchtet der
 * Hauptkanal im Takt der Hüllkurve und glimmt kurz violett nach.
 */
final class BoltPainter {

    private static final Color MID = new Color(165, 190, 255);
    private static final Color INNER = new Color(215, 228, 255);
    private static final Color AFTER = new Color(176, 140, 255);
    private static final BasicStroke[] STROKES = new BasicStroke[64];
    private final BufferedImage glow = Sprites.glow(new Color(235, 242, 255), 128);

    void paint(Graphics2D g, List<Strike> strikes, double nowMs, Palette p, double span) {
        paint(g, strikes, nowMs, p, span, Double.NEGATIVE_INFINITY);
    }

    /** Wie oben, aber nur Äste, die bis unter {@code minY} reichen (zweiter Durchgang über dem Meer). */
    void paint(Graphics2D g, List<Strike> strikes, double nowMs, Palette p, double span, double minY) {
        Color outer = p.get(PaletteRole.BOLT_GLOW);
        Composite old = g.getComposite();
        for (Strike s : strikes) {
            if (s.bolts().isEmpty()) {
                continue;
            }
            double dt = nowMs - s.bornMs();
            double lead = s.envelope().leadMs();
            double leadP = Math.max(0, Math.min(1, dt / lead));
            double inten = s.intensity(nowMs);
            for (List<BoltSegment> bolt : s.bolts()) {
                for (BoltSegment seg : bolt) {
                    if (minY > Double.NEGATIVE_INFINITY && maxY(seg) < minY) {
                        continue;
                    }
                    double r = Math.max(0, Math.min(1, (leadP - seg.start()) / seg.span()));
                    if (r <= 0) {
                        continue;
                    }
                    int n = Math.max(2, (int) (seg.size() * r));
                    double a = dt < lead ? 0.35 * (seg.main() ? 1 : 0.8) : inten * (seg.main() ? 1 : 0.55) + (seg.main() ? 0.05 : 0);
                    if (seg.main() && dt >= lead) {
                        double after = Math.exp(-(dt - lead) / 520.0) * 0.16;
                        stroke(g, seg, n, AFTER, after, 2.6 * s.scale());
                    }
                    if (a < 0.01) {
                        continue;
                    }
                    double w = seg.width() * s.scale();
                    // Weiche Leuchtschichten ohne Kantenglättung (breit und blass – sieht gleich aus, ist viel schneller)
                    Object aa = g.getRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_OFF);
                    if (22 * w >= 4) {
                        stroke(g, seg, n, outer, 0.09 * a, 22 * w);
                    }
                    stroke(g, seg, n, MID, 0.28 * a, 7 * w);
                    g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, aa);
                    stroke(g, seg, n, INNER, 0.6 * a, 3.2 * w);
                    stroke(g, seg, n, Color.WHITE, Math.min(1, a * 1.4), Math.max(0.7, 1.7 * w));
                }
            }
            if (dt >= lead && s.kind() != StrikeKind.IC) {
                double gs = span * 0.04 * s.scale() * (0.6 + inten);
                float al = (float) Math.max(0, Math.min(1, inten) * 0.8);
                if (al > 0.01) {
                    g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, al));
                    g.drawImage(glow, (int) (s.gx() - gs), (int) (s.gy() - gs), (int) (gs * 2), (int) (gs * 2), null);
                    g.setComposite(old);
                }
            }
        }
    }

    /** Hauptkanal gespiegelt in der Ostsee, gestaucht und wellig verzogen. */
    void paintReflection(Graphics2D g, List<Strike> strikes, double nowMs, double t, Palette p) {
        Color outer = p.get(PaletteRole.BOLT_GLOW);
        for (Strike s : strikes) {
            if (s.kind() == StrikeKind.IC || s.bolts().isEmpty() || nowMs - s.bornMs() < s.envelope().leadMs()) {
                continue;
            }
            double inten = s.intensity(nowMs);
            if (inten < 0.02) {
                continue;
            }
            BoltSegment m = s.bolts().get(0).get(0);
            double gy = m.ys()[m.size() - 1];
            Path2D.Float path = new Path2D.Float();
            for (int i = 0; i < m.size(); i++) {
                double yy = gy + (gy - m.ys()[i]) * 0.32;
                double xx = m.xs()[i] + Math.sin(yy * 0.35 + t * 7) * 2.5 * (yy - gy) / 40;
                if (i == 0) {
                    path.moveTo(xx, yy);
                } else {
                    path.lineTo(xx, yy);
                }
            }
            g.setColor(Sprites.alpha(outer, 0.18 * inten));
            g.setStroke(stroke(7 * s.scale()));
            g.draw(path);
            g.setColor(Sprites.alpha(new Color(235, 242, 255), Math.min(1, 0.45 * inten)));
            g.setStroke(stroke(1.4 * s.scale()));
            g.draw(path);
        }
    }

    private static float maxY(BoltSegment seg) {
        float m = Float.NEGATIVE_INFINITY;
        for (float y : seg.ys()) {
            m = Math.max(m, y);
        }
        return m;
    }

    private static void stroke(Graphics2D g, BoltSegment seg, int n, Color c, double a, double width) {
        if (a < 0.005) {
            return;
        }
        Path2D.Float path = new Path2D.Float(Path2D.WIND_NON_ZERO, n);
        path.moveTo(seg.xs()[0], seg.ys()[0]);
        for (int i = 1; i < n; i++) {
            path.lineTo(seg.xs()[i], seg.ys()[i]);
        }
        g.setColor(Sprites.alpha(c, Math.min(1, a)));
        g.setStroke(stroke(width));
        g.draw(path);
    }

    /** Striche in 0,5-px-Stufen zwischengespeichert. */
    static BasicStroke stroke(double width) {
        int i = (int) Math.max(1, Math.min(STROKES.length - 1, Math.round(width * 2)));
        BasicStroke s = STROKES[i];
        if (s == null) {
            s = new BasicStroke(i / 2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND);
            STROKES[i] = s;
        }
        return s;
    }
}
