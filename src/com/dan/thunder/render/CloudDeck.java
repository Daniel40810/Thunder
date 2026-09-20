package com.dan.thunder.render;

import com.dan.thunder.model.Palette;
import com.dan.thunder.model.PaletteRole;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Himmel und Wolkendecke in drei Tiefen: ferne Zellen am Horizont, Hauptdecke und dunkle Fetzen davor.
 * <p>
 * Gerechnet wird in halber Auflösung. Himmel, Glutkern, ferne Zellen und Hauptdecke werden in einem
 * Puffer zusammengesetzt und nur einmal hochskaliert; die vorderen Fetzen haben einen eigenen Puffer,
 * weil die Blitze zwischen Hauptdecke und Fetzen liegen.
 * <p>
 * Jede Ebene ist eine horizontal nahtlose Textur, die mit dem Wind scrollt. Aus einer weißen
 * Formmaske entstehen per {@code SrcIn} die eingefärbten Varianten: Körper (mit Oberlicht und
 * Rauschstruktur), Blitzlicht und Glut. Beleuchtet wird, indem die Licht-Variante durch eine
 * radiale Maske gezeichnet wird – nur im Ausschnitt um die Lichtquelle.
 */
final class CloudDeck {

    static final double SCALE = 0.5;

    enum Kind { FAR, MAIN, FRONT }

    private record Puff(double u, double v, double r, int variant) { }

    private static final class Layer {
        final Kind kind;
        final List<Puff> puffs = new ArrayList<>();
        final double speed;
        BufferedImage shape, body, lit, ember;
        /** Dieselben Texturen um einen halben Texel versetzt (= 1 Bildpunkt): ruckelfreies Scrollen ohne teure Transformation. */
        BufferedImage bodyH, litH, emberH;
        double offset;
        int y0, y1;

        Layer(Kind kind, double speed) {
            this.kind = kind;
            this.speed = speed;
        }
    }

    private final BufferedImage[] puffSprites = Sprites.puffs(7L);
    private final Layer far = new Layer(Kind.FAR, 0.002);
    private final Layer main = new Layer(Kind.MAIN, 0.006);
    private final Layer front = new Layer(Kind.FRONT, 0.012);
    private final Layer[] layers = { far, main, front };
    private int tw, th, width, height;
    private BufferedImage back, frontBuf, backFull, frontFull, temp, noise, sky, emberSprite;
    private Color emberSpriteColor;
    private Palette colored, skyColored;

    CloudDeck() {
        Random r = new Random(20260920L);
        add(r, far, 40, 0.47, 0.585, 0.025, 0.06, 1);
        add(r, main, 150, -0.06, 0.40, 0.05, 0.12, 1.4);
        add(r, front, 40, 0.02, 0.30, 0.04, 0.085, 1.8);
    }

    private static void add(Random r, Layer l, int n, double vmin, double vmax, double rmin, double rmax, double bias) {
        for (int i = 0; i < n; i++) {
            l.puffs.add(new Puff(r.nextDouble(), vmin + (vmax - vmin) * Math.pow(r.nextDouble(), bias),
                    rmin + (rmax - rmin) * r.nextDouble(), r.nextInt(Sprites.PUFF_VARIANTS)));
        }
        l.puffs.sort((a, b) -> Double.compare(a.v(), b.v()));
    }

    // ------------------------------------------------------------------ Aufbau

    /** Baut Formmasken und Puffer neu, wenn sich die Größe ändert. */
    void ensureSize(int w, int h) {
        if (w == width && h == height && main.shape != null) {
            return;
        }
        width = w;
        height = h;
        tw = Math.max(8, (int) Math.ceil(w * SCALE));
        th = Math.max(8, (int) Math.ceil(h * 0.64 * SCALE));
        double span = Math.max(w, h * 1.5) * SCALE;
        for (Layer l : layers) {
            l.shape = image();
            Graphics2D g = l.shape.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            double minY = th, maxY = 0;
            for (Puff p : l.puffs) {
                double x = p.u() * tw, y = p.v() * h * SCALE, rad = p.r() * span;
                minY = Math.min(minY, y - rad);
                maxY = Math.max(maxY, y + rad);
                for (int k = -1; k <= 1; k++) {
                    double px = x + k * tw;
                    if (px + rad < 0 || px - rad > tw) {
                        continue;
                    }
                    g.drawImage(puffSprites[p.variant()], (int) Math.round(px - rad), (int) Math.round(y - rad),
                            (int) Math.round(rad * 2), (int) Math.round(rad * 2), null);
                }
            }
            g.dispose();
            l.y0 = Math.max(0, (int) Math.floor(minY) - 8);   // -8: Oberlicht ist nach oben versetzt
            l.y1 = Math.min(th, (int) Math.ceil(maxY) + 2);
            l.body = image();
            l.lit = image();
            l.ember = image();
            l.bodyH = image();
            l.litH = image();
            l.emberH = image();
        }
        back = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        frontBuf = image();
        backFull = new BufferedImage(tw * 2, th * 2, BufferedImage.TYPE_INT_RGB);
        frontFull = new BufferedImage(tw * 2, th * 2, BufferedImage.TYPE_INT_ARGB_PRE);
        temp = image();
        noise = Sprites.noise(tw, th, 99L);
        colored = null;
        skyColored = null;
    }

    /** Färbt die Ebenen neu ein, wenn sich die Farbstimmung geändert hat. */
    void ensureColors(Palette p) {
        if (p == colored) {
            return;
        }
        colored = p;
        Color rim = p.get(PaletteRole.CLOUD_RIM);
        BufferedImage lightNoise = null, darkNoise = null;
        for (Layer l : layers) {
            Color base = switch (l.kind) {
                case FAR -> p.get(PaletteRole.CLOUD_FAR);
                case FRONT -> p.get(PaletteRole.CLOUD_FRONT);
                default -> p.get(PaletteRole.CLOUD_BASE);
            };
            // Körper: Oberlicht (Form nach oben versetzt, Randfarbe) + Grundfarbe darüber
            Graphics2D g = l.body.createGraphics();
            Sprites.clear(g, tw, th);
            if (l.kind != Kind.FRONT) {
                g.drawImage(l.shape, 0, l.kind == Kind.MAIN ? -7 : -3, null);
                g.setComposite(AlphaComposite.SrcIn);
                g.setPaint(new GradientPaint(0, 0, Sprites.alpha(rim, l.kind == Kind.MAIN ? 0.75 : 0.45),
                        0, th, Sprites.alpha(rim, 0.08)));
                g.fillRect(0, 0, tw, th);
                g.setComposite(AlphaComposite.SrcOver);
            }
            tint(temp, l.shape, new GradientPaint(0, 0, darker(base, 0.8), 0, th, base));
            // Struktur: helle Schlieren (Randfarbe) und dunkle Kerne aus fraktalem Rauschen
            lightNoise = tintedNoise(lightNoise, Palette.mix(base, rim, 0.55));
            Graphics2D tg = temp.createGraphics();
            tg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, l.kind == Kind.FRONT ? 0.18f : 0.32f));
            tg.drawImage(lightNoise, 0, 0, null);
            darkNoise = tintedNoise(darkNoise, darker(base, 0.45));
            tg.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, 0.35f));
            tg.drawImage(darkNoise, -tw / 3, 0, null);
            tg.drawImage(darkNoise, tw - tw / 3, 0, null);
            tg.dispose();
            g.drawImage(temp, 0, 0, null);
            g.dispose();
            // Blitzlicht: Unterseite heller als Oberseite
            Color lit = p.get(PaletteRole.CLOUD_LIT);
            tint(l.lit, l.shape, new GradientPaint(0, 0, Sprites.alpha(lit, 0.45), 0, th, Sprites.alpha(lit, 0.95)));
            Color em = p.get(PaletteRole.EMBER);
            tint(l.ember, l.shape, new GradientPaint(0, 0, Sprites.alpha(em, 0.35), 0, th, Sprites.alpha(em, 0.85)));
            halfShift(l.body, l.bodyH);
            halfShift(l.lit, l.litH);
            halfShift(l.ember, l.emberH);
        }
    }

    /** Kopie um +0,5 Texel nach rechts, nahtlos umlaufend. */
    private void halfShift(BufferedImage src, BufferedImage dst) {
        Graphics2D g = dst.createGraphics();
        Sprites.clear(g, tw, th);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, AffineTransform.getTranslateInstance(0.5, 0), null);
        g.drawImage(src, AffineTransform.getTranslateInstance(0.5 - tw, 0), null);
        g.dispose();
    }

    private BufferedImage tintedNoise(BufferedImage reuse, Color c) {
        BufferedImage img = reuse != null ? reuse : image();
        Graphics2D g = img.createGraphics();
        Sprites.clear(g, tw, th);
        g.drawImage(noise, 0, 0, null);
        g.setComposite(AlphaComposite.SrcIn);
        g.setColor(c);
        g.fillRect(0, 0, tw, th);
        g.dispose();
        return img;
    }

    private void tint(BufferedImage dst, BufferedImage shape, java.awt.Paint paint) {
        Graphics2D g = dst.createGraphics();
        Sprites.clear(g, tw, th);
        g.drawImage(shape, 0, 0, null);
        g.setComposite(AlphaComposite.SrcIn);
        g.setPaint(paint);
        g.fillRect(0, 0, tw, th);
        g.dispose();
    }

    private BufferedImage sky(Palette p) {
        if (sky == null || skyColored != p || sky.getWidth() != tw || sky.getHeight() != th) {
            sky = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = sky.createGraphics();
            float hor = (float) (height * 0.6 * SCALE) + 1;
            Color horizon = p.get(PaletteRole.SKY_HORIZON);
            g.setPaint(new LinearGradientPaint(0, 0, 0, hor, new float[] { 0f, 0.55f, 0.86f, 0.97f, 1f },
                    new Color[] { p.get(PaletteRole.SKY_TOP), p.get(PaletteRole.SKY_MID),
                            Palette.mix(p.get(PaletteRole.SKY_MID), horizon, 0.45), horizon,
                            Palette.mix(horizon, p.get(PaletteRole.HORIZON_GLOW), 0.25) }));
            g.fillRect(0, 0, tw, th);
            g.dispose();
            skyColored = p;
        }
        return sky;
    }

    /** Glutkern als vorgerenderter Lichtfleck (bei voller Stärke 1,8). */
    private BufferedImage emberSprite(Color em) {
        if (emberSprite == null || !em.equals(emberSpriteColor)) {
            int n = 256;
            emberSprite = new BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = emberSprite.createGraphics();
            g.setPaint(new RadialGradientPaint(n / 2f, n / 2f, n / 2f, new float[] { 0f, 0.35f, 1f },
                    new Color[] { Sprites.alpha(em, 0.99), Sprites.alpha(darker(em, 0.85), 0.29), Sprites.alpha(em, 0) }));
            g.fillRect(0, 0, n, n);
            g.dispose();
            emberSpriteColor = em;
        }
        return emberSprite;
    }

    /** Wind treibt die Wolken; vordere Ebenen schneller (Parallaxe). */
    void advance(double dt, double wind) {
        for (Layer l : layers) {
            l.offset = (l.offset + l.speed * wind * dt * tw) % tw;
        }
    }

    // ------------------------------------------------------------------ Zeichnen

    /**
     * Himmel, Glutkern, Horizontglühen, ferne Zellen und Hauptdecke (mit Glut und Blitzlicht).
     * Koordinaten in Bildpunkten des Fensters.
     */
    void paintBack(Graphics2D g, FrameState f, double emberR) {
        Palette p = f.palette;
        Graphics2D b = back.createGraphics();
        b.drawImage(sky(p), 0, 0, null);
        float ex = (float) (f.w * f.scene.emberX() * SCALE), ey = (float) (f.h * f.scene.emberY() * SCALE);
        float er = (float) (f.span * 0.2 * SCALE);
        float ea = (float) Math.max(0, Math.min(1, f.ember / 1.8));
        if (ea > 0.005) {
            b.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, ea));
            b.drawImage(emberSprite(p.get(PaletteRole.EMBER)), (int) (ex - er), (int) (ey - er), (int) (2 * er), (int) (2 * er), null);
            b.setComposite(AlphaComposite.SrcOver);
        }
        Color hg = p.get(PaletteRole.HORIZON_GLOW);
        float hor = (float) (f.hor * SCALE), top = (float) ((f.hor - f.h * 0.12) * SCALE);
        b.setPaint(new GradientPaint(0, top, Sprites.alpha(hg, 0), 0, hor, Sprites.alpha(hg, Math.min(1, 0.16 + 0.2 * f.flash))));
        b.fillRect(0, (int) top, tw, (int) Math.ceil(hor - top) + 1);

        b.dispose();
        drawLayer(back, far, f, f.ember * 0.5, emberR, 0.9);
        drawLayer(back, main, f, f.ember, emberR, 1.0);
        Upscale.twice(back, backFull, 0, th);
        g.drawImage(backFull, 0, 0, null);
    }

    /** Dunkle Fetzen vor den Blitzwurzeln. */
    void paintFront(Graphics2D g, FrameState f, double emberR) {
        Graphics2D b = frontBuf.createGraphics();
        b.setComposite(AlphaComposite.Clear);
        b.fillRect(0, front.y0, tw, front.y1 - front.y0);
        b.dispose();
        drawLayer(frontBuf, front, f, 0, emberR, 0.45);
        Upscale.twice(frontBuf, frontFull, front.y0, front.y1);
        int fy0 = front.y0 * 2, fy1 = front.y1 * 2;
        g.drawImage(frontFull, 0, fy0, tw * 2, fy1, 0, fy0, tw * 2, fy1, null);
    }

    private final float[] lx = new float[16], ly = new float[16], lr = new float[16], la = new float[16];

    private void drawLayer(BufferedImage dst, Layer l, FrameState f, double emberI, double emberR, double litGain) {
        Rectangle rows = new Rectangle(0, l.y0, tw, l.y1 - l.y0);
        Graphics2D b = dst.createGraphics();
        wrapBlit(b, l.body, l.bodyH, l.offset, rows);
        b.dispose();
        // Verschiebung in halben Texeln → Textur und Variante wählen (wie wrapBlit)
        int steps = (int) Math.floor(-l.offset * 2);
        int shift = Math.floorDiv(steps, 2);
        boolean half = (steps & 1) != 0;
        if (emberI > 0.02) {
            float ex = (float) (f.w * f.scene.emberX() * SCALE), ey = (float) (f.h * f.scene.emberY() * SCALE);
            float r = (float) (emberR * SCALE);
            Rectangle area = new Rectangle((int) (ex - r), (int) (ey - r), (int) (2 * r) + 2, (int) (2 * r) + 2).intersection(rows);
            if (!area.isEmpty()) {
                lx[0] = ex;
                ly[0] = ey;
                lr[0] = r;
                la[0] = (float) Math.min(1, emberI);
                LightMixer.mix(dst, half ? l.emberH : l.ember, shift, area, lx, ly, lr, la, 1);
            }
        }
        Rectangle area = null;
        int n = 0;
        for (Light li : f.lights) {
            float a = (float) Math.min(1, li.intensity() * litGain);
            if (a <= 0.02 || n == lx.length) {
                continue;
            }
            float r = (float) (li.sigma() * 2.2 * SCALE);
            lx[n] = (float) (li.x() * SCALE);
            ly[n] = (float) (li.y() * SCALE);
            lr[n] = r;
            la[n] = a;
            n++;
            Rectangle lrect = new Rectangle((int) (li.x() * SCALE - r), (int) (li.y() * SCALE - r), (int) (2 * r) + 2, (int) (2 * r) + 2);
            area = area == null ? lrect : area.union(lrect);
        }
        if (area == null) {
            return;
        }
        area = area.intersection(rows);
        if (!area.isEmpty()) {
            LightMixer.mix(dst, half ? l.litH : l.lit, shift, area, lx, ly, lr, la, n);
        }
    }

    /**
     * Zeichnet eine nahtlose Textur um {@code offset} verschoben, nur im Bereich {@code area}.
     * Halbe Texel werden über die vorab versetzte Variante {@code half} abgebildet.
     */
    private void wrapBlit(Graphics2D g, BufferedImage img, BufferedImage half, double offset, Rectangle area) {
        java.awt.Shape oldClip = g.getClip();
        g.clip(area);
        double x = -offset;
        int steps = (int) Math.floor(x * 2);          // halbe Texel
        int ix = Math.floorDiv(steps, 2);
        BufferedImage src = (steps & 1) == 0 ? img : half;
        g.drawImage(src, ix, 0, null);
        g.drawImage(src, ix + tw, 0, null);
        g.setClip(oldClip);
    }

    private BufferedImage image() {
        return new BufferedImage(tw, th, BufferedImage.TYPE_INT_ARGB_PRE);
    }

    private static Color darker(Color c, double f) {
        return new Color((int) (c.getRed() * f), (int) (c.getGreen() * f), (int) (c.getBlue() * f));
    }
}
