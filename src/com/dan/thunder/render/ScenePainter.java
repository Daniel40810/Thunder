package com.dan.thunder.render;

import com.dan.thunder.model.Palette;
import com.dan.thunder.model.PaletteRole;
import com.dan.thunder.model.SceneObject;
import com.dan.thunder.storm.Strike;
import com.dan.thunder.storm.StrikeKind;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Shape;
import java.awt.geom.GeneralPath;
import java.awt.geom.Path2D;
import java.awt.geom.QuadCurve2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * Alles außer Wolken, Blitzen und Regen: Himmel, Glutkern, Landzunge mit Leuchtturm, Ostsee mit
 * Spiegelungen, Seebrücke, Brandung, Sand, Strandkörbe mit Schlagschatten und Düne mit Strandhafer.
 */
final class ScenePainter {

    private final BufferedImage glow = Sprites.glow(new Color(235, 242, 255), 128);
    private BufferedImage lampGlow;
    private Color lampGlowColor;
    private final double[] grassX = new double[80], grassLen = new double[80], grassPh = new double[80], grassLean = new double[80];
    private final double[] glintX = new double[140], glintY = new double[140], glintPh = new double[140], glintW = new double[140];
    private final double[] wavePhase = { 0, 0.33, 0.66 };
    private final double[] waveSpeed = new double[3];
    private final double[] waveOff = new double[3];

    ScenePainter() {
        Random r = new Random(54L);
        for (int i = 0; i < grassX.length; i++) {
            grassX[i] = r.nextDouble() * 0.31;
            grassLen[i] = 0.5 + r.nextDouble();
            grassPh[i] = r.nextDouble() * Math.PI * 2;
            grassLean[i] = (r.nextDouble() - 0.4) * 0.4;
        }
        for (int i = 0; i < glintX.length; i++) {
            glintX[i] = r.nextDouble();
            glintY[i] = r.nextDouble();
            glintPh[i] = r.nextDouble() * 6.28;
            glintW[i] = 4 + r.nextDouble() * 14;
        }
        for (int i = 0; i < 3; i++) {
            waveSpeed[i] = 0.055 + r.nextDouble() * 0.02;
            waveOff[i] = r.nextDouble() * 6;
        }
    }

    // ------------------------------------------------------------------ Landzunge + Leuchtturm

    private double headH(FrameState f, double x) {
        double end = f.w * 0.27;
        if (x < 0 || x > end) {
            return x < 0 ? f.h * 0.012 : 0;
        }
        double u = x / end;
        double env = Math.pow(Math.sin(Math.PI * Math.min(1, u * 0.55 + 0.45)), 0.7);
        return f.h * (0.013 + 0.004 * Math.sin(u * 9) + 0.002 * Math.sin(u * 23)) * env;
    }

    void paintHeadland(Graphics2D g, FrameState f) {
        Palette p = f.palette;
        Color land = Palette.mix(p.get(PaletteRole.SILHOUETTE), p.get(PaletteRole.SKY_HORIZON), 0.22);
        Path2D.Double path = new Path2D.Double();
        path.moveTo(-2, f.hor + 1);
        for (double x = -2; x <= f.w * 0.27 + 2; x += 3) {
            path.lineTo(x, f.hor - headH(f, x));
        }
        path.lineTo(f.w * 0.27 + 2, f.hor + 1);
        path.closePath();
        g.setColor(land);
        g.fill(path);
        if (f.keyLight > 0.03) {
            g.setColor(Sprites.alpha(p.get(PaletteRole.CLOUD_LIT), Math.min(0.25, f.flash * 0.18)));
            g.setStroke(new BasicStroke(1f));
            Path2D.Double rim = new Path2D.Double();
            rim.moveTo(-2, f.hor - headH(f, -2));
            for (double x = 0; x <= f.w * 0.27; x += 3) {
                rim.lineTo(x, f.hor - headH(f, x));
            }
            g.draw(rim);
        }
        for (SceneObject o : f.scene.objectsOf(SceneObject.Type.LEUCHTTURM)) {
            double x = o.x() * f.w;
            paintLighthouse(g, f, x, f.hor - headH(f, x) + 1, o.scale(), o.color() == null ? new Color(200, 16, 46) : o.color());
        }
    }

    private void paintLighthouse(Graphics2D g, FrameState f, double x, double y, double scale, Color band) {
        Palette p = f.palette;
        Color sil = Palette.mix(p.get(PaletteRole.SILHOUETTE), p.get(PaletteRole.SKY_HORIZON), 0.12);
        double h = f.h * 0.085 * scale, wb = f.h * 0.0105 * scale, wt = wb * 0.66;
        Path2D.Double tower = new Path2D.Double();
        tower.moveTo(x - wb, y);
        tower.lineTo(x - wt, y - h);
        tower.lineTo(x + wt, y - h);
        tower.lineTo(x + wb, y);
        tower.closePath();
        g.setColor(sil);
        g.fill(tower);
        // Galerie, Laterne, Kuppel
        double gy = y - h;
        g.fill(new Rectangle2D.Double(x - wt * 1.45, gy - wt * 0.35, wt * 2.9, wt * 0.35));
        double ly = gy - wt * 0.35;
        double lh = wt * 1.25;
        g.fill(new Rectangle2D.Double(x - wt * 0.8, ly - lh, wt * 0.18, lh));
        g.fill(new Rectangle2D.Double(x + wt * 0.62, ly - lh, wt * 0.18, lh));
        Path2D.Double dome = new Path2D.Double();
        dome.moveTo(x - wt * 0.95, ly - lh);
        dome.quadTo(x, ly - lh - wt * 1.4, x + wt * 0.95, ly - lh);
        dome.closePath();
        g.fill(dome);

        double lit = Math.min(1, f.keyLight * 1.2 + f.flash * 0.3);
        if (lit > 0.03) {
            Shape old = g.getClip();
            g.clip(tower);
            double bh = h / 6;
            for (int i = 0; i < 6; i++) {
                Color c = i % 2 == 0 ? band : new Color(236, 236, 232);
                g.setColor(Sprites.alpha(c, lit * 0.75));
                g.fill(new Rectangle2D.Double(x - wb, y - (i + 1) * bh, wb * 2, bh + 0.5));
            }
            g.setClip(old);
        }

        // Leuchtfeuer: rotierender Lichtkegel, im Regen besonders sichtbar
        Color lamp = p.get(PaletteRole.LAMP);
        double lx = x, lyC = ly - lh * 0.5;
        double ph = f.t * Math.PI * 2 / 7.5;
        double c = Math.cos(ph), s = Math.sin(ph);
        double visible = 0.55 + 0.9 * f.rain;
        double len = f.span * 0.6, spread = 0.035 + 0.08 * Math.abs(s);
        double ex = lx + c * len, eyC = lyC - len * 0.01;
        GeneralPath beam = new GeneralPath();
        beam.moveTo(lx, lyC);
        beam.lineTo(ex, eyC - len * spread * Math.abs(c));
        beam.lineTo(ex, eyC + len * spread * Math.abs(c) * 0.6);
        beam.closePath();
        double ba = Math.min(0.5, (0.07 + 0.2 * Math.max(0, s)) * visible);
        g.setPaint(new GradientPaint((float) lx, (float) lyC, Sprites.alpha(lamp, ba), (float) ex, (float) eyC, Sprites.alpha(lamp, 0)));
        g.fill(beam);
        double flash = Math.pow(Math.max(0, s), 6);
        drawGlow(g, lampGlow(lamp), lx, lyC, wt * 3.5 + wt * 20 * flash, 0.55 + 0.45 * flash);
        g.setColor(Palette.mix(lamp, Color.WHITE, 0.5));
        g.fill(new Rectangle2D.Double(lx - wt * 0.55, lyC - wt * 0.3, wt * 1.1, wt * 0.6));
        // Lichtstraße auf dem Wasser, wenn der Kegel zum Strand zeigt
        double road = 0.08 + 0.35 * flash;
        for (int i = 0; i < 14; i++) {
            double yy = f.hor + 3 + i * (2.5 + i * 0.55);
            double wob = Math.sin(f.t * 2.6 + i * 1.3) * (1 + i * 0.4);
            g.setColor(Sprites.alpha(lamp, Math.max(0, road * (1 - i / 14.0))));
            double ww = 3 + i * 0.9;
            g.fill(new Rectangle2D.Double(lx - ww / 2 + wob, yy, ww, 1.3));
        }
    }

    private BufferedImage lampGlow(Color lamp) {
        if (lampGlow == null || !lamp.equals(lampGlowColor)) {
            lampGlow = Sprites.glow(lamp, 96);
            lampGlowColor = lamp;
        }
        return lampGlow;
    }

    // ------------------------------------------------------------------ Meer

    Shape seaShape(FrameState f) {
        Path2D.Double sea = new Path2D.Double();
        sea.moveTo(-2, f.hor);
        sea.lineTo(f.w + 2, f.hor);
        for (double x = f.w + 2; x >= -2; x -= 12) {
            sea.lineTo(x, f.shoreY(x) + 5);
        }
        sea.closePath();
        return sea;
    }

    void paintSea(Graphics2D g, FrameState f, List<Strike> strikes, BoltPainter bolts) {
        Palette p = f.palette;
        // Nur rechteckig klippen (schnell); was über die Uferlinie ragt, überdeckt danach der Sand
        Shape old = g.getClip();
        g.clip(new Rectangle2D.Double(-2, f.hor, f.w + 4, f.h - f.hor));
        Color top = p.get(PaletteRole.SEA_TOP), deep = p.get(PaletteRole.SEA_DEEP);
        g.setPaint(new LinearGradientPaint(0, (float) f.hor, 0, (float) f.h, new float[] { 0f, 0.12f, 1f },
                new Color[] { top, Palette.mix(top, deep, 0.6), deep }));
        g.fill(new Rectangle2D.Double(0, f.hor, f.w, f.h - f.hor));

        // Glut und Horizontglühen im Wasser
        Color em = p.get(PaletteRole.EMBER);
        double ex = f.w * f.scene.emberX(), er = f.span * 0.13;
        g.setPaint(new RadialGradientPaint((float) ex, (float) f.hor, (float) er, new float[] { 0f, 1f },
                new Color[] { Sprites.alpha(em, Math.min(1, 0.16 * f.ember)), Sprites.alpha(em, 0) }));
        g.fill(new Rectangle2D.Double(ex - er, f.hor, er * 2, er));
        Color hg = p.get(PaletteRole.HORIZON_GLOW);
        g.setPaint(new GradientPaint(0, (float) f.hor, Sprites.alpha(hg, 0.22), 0, (float) (f.hor + f.h * 0.05), Sprites.alpha(hg, 0)));
        g.fill(new Rectangle2D.Double(0, f.hor, f.w, f.h * 0.05));

        // Blitzlicht auf dem Wasser + Lichtsäule unter dem Einschlag
        Color lit = p.get(PaletteRole.CLOUD_LIT);
        for (Strike s : strikes) {
            double i = s.intensity(f.nowMs);
            if (i < 0.02) {
                continue;
            }
            g.setColor(Sprites.alpha(lit, Math.min(1, 0.10 * i * (0.4 + s.nearness()))));
            g.fill(new Rectangle2D.Double(0, f.hor, f.w, f.h - f.hor));
            if (s.kind() != StrikeKind.IC && f.nowMs - s.bornMs() >= s.envelope().leadMs()) {
                // Lichtstraße vom Einschlag zum Strand: wellige, nach vorn breiter werdende Striche
                double shore = f.shoreY(s.gx());
                int n = 22;
                for (int k = 0; k < n; k++) {
                    double u = (double) k / n;
                    double yy = s.gy() + 3 + (shore - s.gy()) * u * u;
                    double ww = (6 + 44 * u) * s.scale();
                    double wob = Math.sin(f.t * 5 + k * 1.7) * (2 + 6 * u);
                    g.setColor(Sprites.alpha(lit, Math.min(1, 0.4 * i * (1 - u * 0.8))));
                    g.fill(new Rectangle2D.Double(s.gx() - ww / 2 + wob, yy, ww, 1.4 + u));
                }
            }
        }
        bolts.paintReflection(g, strikes, f.nowMs, f.t, p);

        // Wellenlinien, im Blitzlicht deutlicher
        Color wave = Palette.mix(p.get(PaletteRole.BOLT_GLOW), lit, 0.5);
        int rows = 20;
        g.setStroke(new BasicStroke(1f));
        for (int k = 1; k <= rows; k++) {
            double fr = Math.pow((double) k / rows, 1.8);
            double y0 = f.hor + (f.h * f.scene.shoreY() - f.hor) * fr;
            double amp = 0.4 + fr * 3.2, freq = 0.012 / (0.3 + fr), sp = (0.6 + fr * 1.2) * (0.6 + f.wind * 0.6);
            double a = 0.05 + 0.06 * fr + 0.35 * f.flash * (0.3 + fr * 0.7);
            g.setColor(Sprites.alpha(wave, Math.min(1, a)));
            Path2D.Float line = new Path2D.Float();
            for (double x = -10; x <= f.w + 10; x += 22) {
                double y = y0 + amp * Math.sin(x * freq + f.t * sp + k * 1.7) + amp * 0.5 * Math.sin(x * freq * 2.3 - f.t * sp * 1.3 + k);
                if (x < 0) {
                    line.moveTo(x, y);
                } else {
                    line.lineTo(x, y);
                }
            }
            g.draw(line);
        }
        // Glitzern
        for (int i = 0; i < glintX.length; i++) {
            double x = glintX[i] * f.w, hy = f.hor;
            double y = hy + 3 + Math.pow(glintY[i], 1.7) * (f.h * f.scene.shoreY() - hy);
            double a = 0.05 + 0.08 * Math.max(0, Math.sin(f.t * 1.7 + glintPh[i])) + 0.2 * f.flash;
            double ww = glintW[i] * (0.4 + glintY[i]);
            g.setColor(Sprites.alpha(wave, Math.min(1, a)));
            g.fill(new Rectangle2D.Double(x - ww / 2, y, ww, 1));
        }
        g.setClip(old);
    }

    // ------------------------------------------------------------------ Seebrücke

    void paintPier(Graphics2D g, FrameState f) {
        for (SceneObject o : f.scene.objectsOf(SceneObject.Type.SEEBRUECKE)) {
            paintPier(g, f, o.x() * f.w);
        }
    }

    private void paintPier(Graphics2D g, FrameState f, double x) {
        Palette p = f.palette;
        Color sil = p.get(PaletteRole.SILHOUETTE);
        double nx = x + f.w * 0.08, ny = f.h * 1.04, fx = x - f.w * 0.07, fy = f.hor + f.h * 0.012;
        int n = 26;
        g.setColor(sil);
        for (int k = 0; k <= n; k++) {
            double gk = Math.pow((double) k / n, 2.2);
            double px = fx + (nx - fx) * gk, py = fy + (ny - fy) * gk;
            double th = 1.2 + (12 - 1.2) * gk, post = 3 + (f.h * 0.09 - 3) * gk;
            g.setStroke(new BasicStroke((float) Math.max(0.8, th * 0.35)));
            g.draw(new java.awt.geom.Line2D.Double(px, py, px, py + post));
            g.setStroke(new BasicStroke((float) Math.max(0.5, th * 0.12)));
            g.draw(new java.awt.geom.Line2D.Double(px, py, px, py - th * 1.8));
        }
        Path2D.Double deck = new Path2D.Double();
        deck.moveTo(fx, fy - 1);
        deck.lineTo(nx - 14, ny - 12);
        deck.lineTo(nx + 14, ny);
        deck.lineTo(fx + 2, fy + 1);
        deck.closePath();
        g.fill(deck);
        g.setStroke(new BasicStroke(1.2f));
        g.draw(new java.awt.geom.Line2D.Double(fx, fy - 2.2, nx - 14, ny - 34));
        double pw = f.h * 0.022, ph = f.h * 0.016;
        g.fill(new Rectangle2D.Double(fx - pw / 2, fy - ph, pw, ph));
        Path2D.Double roof = new Path2D.Double();
        roof.moveTo(fx - pw * 0.65, fy - ph);
        roof.lineTo(fx, fy - ph * 1.9);
        roof.lineTo(fx + pw * 0.65, fy - ph);
        roof.closePath();
        g.fill(roof);
        if (f.flash > 0.03) {
            g.setColor(Sprites.alpha(p.get(PaletteRole.CLOUD_LIT), Math.min(0.5, f.flash * 0.45)));
            g.setStroke(new BasicStroke(1f));
            g.draw(new java.awt.geom.Line2D.Double(fx, fy - 2.2, nx - 14, ny - 34));
        }
        // Laterne am Kopf der Brücke mit Spiegelung
        Color lamp = p.get(PaletteRole.LAMP);
        double fl = 0.8 + 0.2 * Math.sin(f.t * 7) * Math.sin(f.t * 2.3);
        drawGlow(g, lampGlow(lamp), fx + pw * 0.1, fy - ph * 0.55, f.h * 0.015, 0.8 * fl);
        for (int i = 0; i < 10; i++) {
            double yy = fy + 4 + i * 4.5, wob = Math.sin(f.t * 3 + i) * 2;
            g.setColor(Sprites.alpha(lamp, Math.max(0, (0.35 - i * 0.03) * fl)));
            g.fill(new Rectangle2D.Double(fx + pw * 0.1 - 3 + wob, yy, 6, 1.3));
        }
    }

    // ------------------------------------------------------------------ Brandung, Sand, Körbe, Düne

    void paintSurf(Graphics2D g, FrameState f) {
        Color foam = Palette.mix(f.palette.get(PaletteRole.CLOUD_LIT), Color.WHITE, 0.4);
        for (int i = 0; i < 3; i++) {
            wavePhase[i] += waveSpeed[i] * f.dt * (0.7 + f.wind * 0.5);
            if (wavePhase[i] > 1) {
                wavePhase[i] -= 1;
            }
            double ph = wavePhase[i], e = 1 - Math.pow(1 - ph, 2);
            double off = -f.h * 0.045 + (f.h * 0.012 + f.h * 0.045) * e;
            double a = (1 - ph) * 0.35 * (0.5 + f.wind * 0.5) + f.flash * 0.25 * (1 - ph);
            g.setColor(Sprites.alpha(foam, Math.min(1, a)));
            g.setStroke(new BasicStroke((float) (2.2 + (0.6 - 2.2) * ph)));
            Path2D.Float line = new Path2D.Float();
            for (double x = -10; x <= f.w + 10; x += 14) {
                double y = f.shoreY(x) + off + Math.sin(x * 0.02 + waveOff[i]) * 2.5;
                if (x < 0) {
                    line.moveTo(x, y);
                } else {
                    line.lineTo(x, y);
                }
            }
            g.draw(line);
        }
    }

    void paintBeach(Graphics2D g, FrameState f) {
        Palette p = f.palette;
        Path2D.Double sand = new Path2D.Double();
        sand.moveTo(-10, f.h + 10);
        for (double x = -10; x <= f.w + 10; x += 12) {
            sand.lineTo(x, f.shoreY(x) + 4);
        }
        sand.lineTo(f.w + 10, f.h + 10);
        sand.closePath();
        g.setColor(p.get(PaletteRole.SAND));
        g.fill(sand);
        float top = (float) (f.h * f.scene.shoreY() - f.h * 0.012);
        Color wet = Palette.mix(p.get(PaletteRole.SKY_HORIZON), p.get(PaletteRole.SEA_TOP), 0.5);
        g.setPaint(new LinearGradientPaint(0, top, 0, (float) f.h, new float[] { 0f, 0.15f, 1f },
                new Color[] { Sprites.alpha(wet, 0.28), Sprites.alpha(wet, 0.08), Sprites.alpha(wet, 0) }));
        g.fill(new Rectangle2D.Double(0, top, f.w, f.h - top));
        if (f.keyLight > 0.02) {
            double lx = f.keyX;
            float r = (float) (f.span * 0.7);
            Color sandLit = Palette.mix(p.get(PaletteRole.CLOUD_LIT), new Color(160, 132, 104), 0.6);
            g.setPaint(new RadialGradientPaint((float) lx, (float) (f.h * 0.8), r, new float[] { 0f, 1f },
                    new Color[] { Sprites.alpha(sandLit, Math.min(1, 0.45 * f.keyLight)), Sprites.alpha(sandLit, Math.min(1, 0.12 * f.keyLight)) }));
            Shape old = g.getClip();
            g.clip(sand);
            g.fill(new Rectangle2D.Double(0, top, f.w, f.h - top));
            g.setClip(old);
        }
        List<SceneObject> korbs = new ArrayList<>(f.scene.objectsOf(SceneObject.Type.STRANDKORB));
        korbs.sort(Comparator.comparingDouble(SceneObject::depth));
        for (SceneObject o : korbs) {
            double x = o.x() * f.w;
            double y = f.shoreY(x) + f.h * 0.03 + (f.h * 0.97 - f.shoreY(x) - f.h * 0.03) * o.depth();
            double s = f.h * 0.075 * o.scale() * (0.6 + o.depth() * 0.6);
            paintKorb(g, f, x, y, s, o.color() == null ? new Color(60, 100, 190) : o.color());
        }
    }

    private void paintKorb(Graphics2D g, FrameState f, double x, double y, double s, Color stripe) {
        Palette p = f.palette;
        double w = s * 0.62, lit = f.keyLight;
        if (lit > 0.02) {
            double dir = Math.signum(x - f.keyX);
            if (dir == 0) {
                dir = 1;
            }
            double len = s * (1.2 + 1.4 * Math.min(1, Math.abs(x - f.keyX) / f.w * 2));
            Path2D.Double sh = new Path2D.Double();
            sh.moveTo(x - w, y);
            sh.lineTo(x + w, y);
            sh.lineTo(x + w + dir * len, y + s * 0.18);
            sh.lineTo(x - w + dir * len, y + s * 0.18);
            sh.closePath();
            g.setColor(new Color(0f, 0f, 0f, (float) Math.min(0.75, lit * 0.9)));
            g.fill(sh);
        }
        Color sil = p.get(PaletteRole.SILHOUETTE);
        // Sitzkasten (unten) und Haube (oben, etwas breiter, oben gewölbt)
        double baseTop = y - s * 0.46, hoodW = w * 1.08, hoodTop = y - s * 1.3;
        Path2D.Double body = new Path2D.Double();
        body.moveTo(x - w, y);
        body.lineTo(x + w, y);
        body.lineTo(x + w, baseTop);
        body.lineTo(x + hoodW, baseTop - s * 0.04);
        body.lineTo(x + hoodW, hoodTop + s * 0.3);
        body.curveTo(x + hoodW, hoodTop + s * 0.05, x + w * 0.6, hoodTop - s * 0.02, x, hoodTop - s * 0.03);
        body.curveTo(x - w * 0.6, hoodTop - s * 0.02, x - hoodW, hoodTop + s * 0.05, x - hoodW, hoodTop + s * 0.3);
        body.lineTo(x - hoodW, baseTop - s * 0.04);
        body.lineTo(x - w, baseTop);
        body.closePath();
        g.setColor(sil);
        g.fill(body);
        // Fußstützen (Schubladen)
        g.fill(new Rectangle2D.Double(x - w * 0.9, y - s * 0.1, w * 0.85, s * 0.1));
        g.fill(new Rectangle2D.Double(x + w * 0.05, y - s * 0.1, w * 0.85, s * 0.1));
        // Sitzöffnung: rund überwölbt, innen nur wenig heller
        double oL = x - w * 0.78, oR = x + w * 0.78, oB = baseTop + s * 0.02, oT = hoodTop + s * 0.2;
        Path2D.Double opening = new Path2D.Double();
        opening.moveTo(oL, oB);
        opening.lineTo(oL, oT + s * 0.18);
        opening.curveTo(oL, oT, x - w * 0.3, oT - s * 0.04, x, oT - s * 0.04);
        opening.curveTo(x + w * 0.3, oT - s * 0.04, oR, oT, oR, oT + s * 0.18);
        opening.lineTo(oR, oB);
        opening.closePath();
        g.setColor(Palette.mix(sil, p.get(PaletteRole.SKY_MID), 0.22));
        g.fill(opening);
        // Sitzpolster und Armlehnen
        g.setColor(sil);
        g.fill(new Rectangle2D.Double(oL, baseTop - s * 0.02, oR - oL, s * 0.07));
        g.fill(new Rectangle2D.Double(oL - w * 0.02, baseTop - s * 0.2, w * 0.14, s * 0.2));
        g.fill(new Rectangle2D.Double(oR - w * 0.12, baseTop - s * 0.2, w * 0.14, s * 0.2));
        // Oberkante leicht vom Himmel angestrahlt
        g.setColor(Sprites.alpha(p.get(PaletteRole.CLOUD_RIM), 0.25));
        g.setStroke(new BasicStroke(1f));
        g.draw(new java.awt.geom.CubicCurve2D.Double(x - hoodW, hoodTop + s * 0.3, x - hoodW, hoodTop + s * 0.05,
                x - w * 0.6, hoodTop - s * 0.02, x, hoodTop - s * 0.03));
        g.draw(new java.awt.geom.CubicCurve2D.Double(x, hoodTop - s * 0.03, x + w * 0.6, hoodTop - s * 0.02,
                x + hoodW, hoodTop + s * 0.05, x + hoodW, hoodTop + s * 0.3));
        if (lit > 0.03) {
            double a = Math.min(0.85, lit);
            Shape old = g.getClip();
            java.awt.geom.Area hood = new java.awt.geom.Area(body);
            hood.subtract(new java.awt.geom.Area(opening));
            hood.subtract(new java.awt.geom.Area(new Rectangle2D.Double(x - w * 1.3, baseTop - s * 0.02, w * 2.6, s)));
            g.clip(hood);
            for (int i = -7; i < 8; i++) {
                g.setColor(Sprites.alpha((i & 1) != 0 ? stripe : new Color(225, 232, 245), a));
                g.fill(new Rectangle2D.Double(x + i * w * 0.16, hoodTop - s * 0.1, w * 0.16, s * 1.0));
            }
            g.setClip(old);
            // Korbgeflecht unten: warmes Braun mit feinen Rillen
            Shape old2 = g.getClip();
            g.clip(new Rectangle2D.Double(x - w, baseTop, w * 2, y - baseTop));
            g.setColor(Sprites.alpha(new Color(150, 118, 84), Math.min(0.65, lit * 0.75)));
            g.fill(new Rectangle2D.Double(x - w, baseTop, w * 2, y - baseTop - s * 0.1));
            g.setColor(Sprites.alpha(new Color(60, 44, 30), Math.min(0.5, lit * 0.6)));
            for (double yy = baseTop + s * 0.06; yy < y - s * 0.1; yy += s * 0.06) {
                g.draw(new java.awt.geom.Line2D.Double(x - w, yy, x + w, yy));
            }
            g.setClip(old2);
        }
    }

    private double duneY(FrameState f, double x) {
        double u = x / (f.w * 0.34);
        return f.h * 0.82 + Math.pow(u, 1.6) * f.h * 0.2 - Math.sin(u * 3.1) * f.h * 0.035;
    }

    void paintDune(Graphics2D g, FrameState f) {
        if (f.scene.objectsOf(SceneObject.Type.DUENE).isEmpty()) {
            return;
        }
        Color sil = Palette.mix(f.palette.get(PaletteRole.SILHOUETTE), Color.BLACK, 0.4);
        Path2D.Double dune = new Path2D.Double();
        dune.moveTo(-10, f.h + 10);
        dune.lineTo(-10, f.h * 0.8);
        for (double x = 0; x <= f.w * 0.34; x += 8) {
            dune.lineTo(x, duneY(f, x));
        }
        dune.lineTo(f.w * 0.36, f.h + 10);
        dune.closePath();
        g.setColor(sil);
        g.fill(dune);
        g.setStroke(new BasicStroke(1.3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        for (int i = 0; i < grassX.length; i++) {
            double x = grassX[i] * f.w, y = duneY(f, x) + 2, len = f.h * 0.05 * grassLen[i];
            double sway = Math.sin(f.t * 2.2 + grassPh[i]) * 0.18 * f.wind + f.wind * 0.35 + grassLean[i];
            g.draw(new QuadCurve2D.Double(x, y, x + sway * len * 0.3, y - len * 0.6, x + sway * len, y - len * Math.cos(sway * 0.6)));
        }
        if (f.keyLight > 0.05) {
            g.setColor(Sprites.alpha(f.palette.get(PaletteRole.CLOUD_LIT), Math.min(0.35, f.keyLight * 0.35)));
            g.setStroke(new BasicStroke(1f));
            Path2D.Double rim = new Path2D.Double();
            rim.moveTo(0, duneY(f, 0));
            for (double x = 8; x <= f.w * 0.34; x += 8) {
                rim.lineTo(x, duneY(f, x));
            }
            g.draw(rim);
        }
    }

    /** Blitzlicht über der ganzen Szene (gedämpft im sanften Modus). */
    void paintFlashOverlay(Graphics2D g, FrameState f, boolean soft) {
        if (f.flash <= 0.01) {
            return;
        }
        double a = Math.min(soft ? 0.05 : 0.16, f.flash * (soft ? 0.05 : 0.14));
        g.setColor(Sprites.alpha(Palette.mix(f.palette.get(PaletteRole.CLOUD_LIT), new Color(175, 190, 255), 0.5), a));
        g.fill(new Rectangle2D.Double(-20, -20, f.w + 40, f.h + 40));
    }

    private static void drawGlow(Graphics2D g, BufferedImage img, double x, double y, double size, double alpha) {
        float a = (float) Math.max(0, Math.min(1, alpha));
        if (a <= 0.005 || size < 1) {
            return;
        }
        Composite old = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, a));
        g.drawImage(img, (int) (x - size / 2), (int) (y - size / 2), (int) size, (int) size, null);
        g.setComposite(old);
    }

    BufferedImage glowSprite() {
        return glow;
    }

    private static Color darker(Color c, double k) {
        return new Color((int) (c.getRed() * k), (int) (c.getGreen() * k), (int) (c.getBlue() * k));
    }
}
