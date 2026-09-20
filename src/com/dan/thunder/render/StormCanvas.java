package com.dan.thunder.render;

import com.dan.thunder.model.Mood;
import com.dan.thunder.model.Palette;
import com.dan.thunder.model.StormLevel;
import com.dan.thunder.model.ThunderData;
import com.dan.thunder.storm.Stage;
import com.dan.thunder.storm.StormDirector;
import com.dan.thunder.storm.StormListener;
import com.dan.thunder.storm.Strike;
import com.dan.thunder.storm.StrikeKind;

import javax.swing.JComponent;
import javax.swing.Timer;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.util.Random;

/**
 * Die animierte Gewitterszene. Ein 15-ms-Timer treibt Regie, Wolken, Regen und Brandung;
 * gezeichnet wird in Ebenen von hinten nach vorn. Klick in den Himmel löst einen Blitz aus.
 */
public class StormCanvas extends JComponent {

    private final CloudDeck deck = new CloudDeck();
    private final ScenePainter scene = new ScenePainter();
    private final BoltPainter bolts = new BoltPainter();
    private final RainField rain = new RainField();
    private final Hud hud = new Hud();
    private final FrameState f = new FrameState();
    private final Random shakeRnd = new Random();
    private final Timer timer = new Timer(15, e -> repaint());
    private final long t0 = System.nanoTime();
    private final StageImpl stage = new StageImpl();

    private ThunderData data;
    private StormDirector director;
    private Mood mood = Mood.NACHT;
    private Palette palFrom, palTo, palette;
    private double palStartMs = -1e9;
    private boolean showRain = true;
    private double lastMs = -1;
    private double shake;
    private String status = "";
    private String loadingText = "Gewitter zieht auf …";

    public StormCanvas() {
        setPreferredSize(new Dimension(1440, 820));
        setOpaque(true);
        setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (director != null && e.getY() < stage.horizonY()) {
                    director.strikeAt(e.getX(), e.getY(), nowMs(), stage);
                }
            }
        });
    }

    // ------------------------------------------------------------------ API

    /** Übernimmt die geladenen Daten und startet das Gewitter mit der mittleren Stufe. */
    public void setData(ThunderData data) {
        this.data = data;
        StormLevel start = data.level("STORM").orElse(data.levels().get(Math.min(1, data.levels().size() - 1)));
        StormDirector d = new StormDirector(start);
        d.addListener(new StormListener() {
            @Override
            public void thunderArrived(Strike s) {
                if (s.distKm() < 2.2) {
                    shake = Math.max(shake, (2.2 - s.distKm()) * 3.5);
                }
            }
        });
        if (director != null) {
            d.setSoft(director.isSoft());
        }
        director = d;
        palette = palTo = palFrom = data.palette(mood);
        firePropertyChange("director", null, d);
        repaint();
    }

    public ThunderData getData() {
        return data;
    }

    public StormDirector getDirector() {
        return director;
    }

    public void setLevel(StormLevel level) {
        if (director != null && level != null) {
            director.setLevel(level, nowMs());
        }
    }

    public Mood getMood() {
        return mood;
    }

    /** Wechselt die Tageszeit mit weicher Überblendung (1,5 s). */
    public void setMood(Mood m) {
        if (m == mood) {
            return;
        }
        Mood old = mood;
        mood = m;
        if (data != null) {
            palFrom = palette;
            palTo = data.palette(m);
            palStartMs = nowMs();
        }
        firePropertyChange("mood", old, m);
    }

    public void setRainVisible(boolean on) {
        showRain = on;
    }

    public boolean isRainVisible() {
        return showRain;
    }

    public void setSoft(boolean soft) {
        if (director != null) {
            director.setSoft(soft);
        }
    }

    public void setStatusText(String text) {
        status = text == null ? "" : text;
    }

    public void setLoadingText(String text) {
        loadingText = text;
        repaint();
    }

    /** Für Tests: löst sofort einen Blitz aus. */
    public Strike strikeForTest(StrikeKind kind, double x, double distKm) {
        return director.spawn(kind, nowMs(), stage, x, distKm, false, new Random().nextLong());
    }

    public double nowMs() {
        return (System.nanoTime() - t0) / 1e6;
    }

    @Override
    public void addNotify() {
        super.addNotify();
        timer.start();
    }

    @Override
    public void removeNotify() {
        timer.stop();
        super.removeNotify();
    }

    // ------------------------------------------------------------------ Zeichnen

    @Override
    protected void paintComponent(Graphics g0) {
        Graphics2D g = (Graphics2D) g0.create();
        int w = getWidth(), h = getHeight();
        g.setColor(new Color(4, 6, 12));
        g.fillRect(0, 0, w, h);
        if (data == null || w < 50 || h < 50) {
            g.setColor(new Color(160, 170, 196));
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 15));
            g.drawString(loadingText, 32, h / 2);
            g.dispose();
            return;
        }
        double now = nowMs();
        double dt = lastMs < 0 ? 0.016 : Math.min(0.05, (now - lastMs) / 1000);
        lastMs = now;
        update(now, dt, w, h);

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        AffineTransform base = g.getTransform();
        if (shake > 0.05) {
            g.translate((shakeRnd.nextDouble() - 0.5) * shake, (shakeRnd.nextDouble() - 0.5) * shake);
        }
        double er = f.span * 0.11 * 2.2;
        deck.paintBack(g, f, er);
        bolts.paint(g, director.active(), now, f.palette, f.span);
        deck.paintFront(g, f, er);
        scene.paintHeadland(g, f);
        scene.paintSea(g, f, director.active(), bolts);
        java.awt.Shape clip = g.getClip();
        g.clip(new java.awt.geom.Rectangle2D.Double(-20, f.hor, f.w + 40, f.h));
        bolts.paint(g, director.active(), now, f.palette, f.span, f.hor);
        g.setClip(clip);
        scene.paintPier(g, f);
        scene.paintSurf(g, f);
        scene.paintBeach(g, f);
        scene.paintDune(g, f);
        if (showRain) {
            rain.paint(g, f, director.getLevel().rainDrops());
        }
        scene.paintFlashOverlay(g, f, director.isSoft());
        g.setTransform(base);
        String sub = director.getLevel().name() + " · " + mood.getLabel() + " · " + data.scene().name();
        hud.paint(g, w, h, sub, director.last(), now, director.perMinute(now), status, "Klick in den Himmel: Blitz auslösen");
        g.dispose();
    }

    private void update(double now, double dt, int w, int h) {
        f.w = w;
        f.h = h;
        f.scene = data.scene();
        f.hor = h * f.scene.horizonY();
        f.span = Math.max(w, h * 1.5);
        f.t = now / 1000;
        f.dt = dt;
        f.nowMs = now;
        double k = Math.max(0, Math.min(1, (now - palStartMs) / 1500));
        k = k * k * (3 - 2 * k);
        palette = k >= 1 ? palTo : Palette.lerp(palFrom, palTo, k);
        f.palette = palette;

        StormLevel lvl = director.getLevel();
        f.wind = lvl.wind() * (0.75 + 0.25 * Math.sin(f.t * 0.37) + 0.12 * Math.sin(f.t * 1.9));
        f.rain = showRain ? Math.min(1, lvl.rainDrops() / 950.0) : 0;
        director.update(now, stage);

        f.lights.clear();
        double flash = 0, best = 0;
        f.key = null;
        f.keyLight = 0;
        for (Strike s : director.active()) {
            double i = s.intensity(now);
            flash += i * (0.35 + 0.65 * s.nearness());
            double sx = s.kind() == StrikeKind.IC ? s.cx() : s.cx() + (s.gx() - s.cx()) * 0.25;
            double sy = s.kind() == StrikeKind.IC ? s.cy() : s.cy() + h * 0.04;
            f.lights.add(new Light(sx, sy, i * (0.5 + 0.6 * s.scale()), f.span * (0.12 + 0.16 * s.scale())));
            double score = i * s.nearness();
            if (score > best) {
                best = score;
                f.key = s;
                f.keyLight = i * (0.25 + 0.75 * s.nearness());
                f.keyX = s.kind() == StrikeKind.IC ? s.cx() : s.gx();
            }
        }
        f.flash = Math.min(1.3, flash);
        double ex = w * f.scene.emberX(), ey = h * f.scene.emberY(), atEmber = 0;
        for (Light li : f.lights) {
            double dx = ex - li.x(), dy = (ey - li.y()) * 1.4;
            atEmber += li.intensity() * Math.exp(-(dx * dx + dy * dy) / (2 * li.sigma() * li.sigma()));
        }
        double moodBoost = mood == Mood.ABEND ? 1.25 : 1.0;
        f.ember = lvl.ember() * moodBoost * (0.75 + 0.25 * Math.sin(f.t * 0.7)) + atEmber * 0.6;

        deck.ensureSize(w, h);
        deck.ensureColors(palette);
        deck.advance(dt, f.wind);
        if (shake > 0.05) {
            shake *= Math.pow(0.02, dt);
        } else {
            shake = 0;
        }
    }

    /** Geometrie für die Regie, immer passend zur aktuellen Größe. */
    private final class StageImpl implements Stage {
        @Override
        public double width() {
            return getWidth();
        }

        @Override
        public double height() {
            return getHeight();
        }

        @Override
        public double horizonY() {
            return getHeight() * (data == null ? 0.6 : data.scene().horizonY());
        }

        @Override
        public double shoreY(double x) {
            double h = getHeight(), w = Math.max(1, getWidth());
            return h * (data == null ? 0.8 : data.scene().shoreY()) + Math.sin(x / w * 3 + 0.5) * h * 0.012;
        }

        @Override
        public double emberX() {
            return data == null ? 0.62 : data.scene().emberX();
        }

        @Override
        public double emberY() {
            return data == null ? 0.33 : data.scene().emberY();
        }
    }
}
