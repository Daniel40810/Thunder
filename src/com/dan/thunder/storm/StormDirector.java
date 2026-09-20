package com.dan.thunder.storm;

import com.dan.thunder.model.StormLevel;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Regie des Gewitters: löst Blitze nach der gewählten Stufe aus, hält die aktiven Blitze und
 * meldet, wann der Donner ankommt (≈ 2,92 s pro km).
 */
public final class StormDirector {

    private final Random rnd = new Random();
    private final List<Strike> active = new ArrayList<>();
    private final List<Strike> pendingThunder = new ArrayList<>();
    private final List<Double> recent = new ArrayList<>();
    private final List<StormListener> listeners = new CopyOnWriteArrayList<>();
    private StormLevel level;
    private boolean soft;
    private double nextStrikeMs = Double.NaN;
    private Strike last;

    public StormDirector(StormLevel level) {
        this.level = level;
    }

    public void addListener(StormListener l) {
        listeners.add(l);
    }

    public StormLevel getLevel() {
        return level;
    }

    public void setLevel(StormLevel level, double nowMs) {
        this.level = level;
        nextStrikeMs = nowMs + 400;
    }

    public boolean isSoft() {
        return soft;
    }

    /** Sanfte Blitze: eine gedämpfte Entladung ohne Flackern. */
    public void setSoft(boolean soft) {
        this.soft = soft;
    }

    public List<Strike> active() {
        return Collections.unmodifiableList(active);
    }

    public Strike last() {
        return last;
    }

    /** Blitze der letzten 60 s. */
    public int perMinute(double nowMs) {
        recent.removeIf(t -> nowMs - t > 60_000);
        return recent.size();
    }

    /** Einmal pro Bild aufrufen. */
    public void update(double nowMs, Stage stage) {
        if (Double.isNaN(nextStrikeMs)) {
            nextStrikeMs = nowMs + 900;
        }
        if (nowMs >= nextStrikeMs) {
            double r = rnd.nextDouble();
            StrikeKind kind = r < level.shareCrown() ? StrikeKind.CROWN
                    : r < level.shareCrown() + level.shareIc() ? StrikeKind.IC : StrikeKind.CG;
            spawn(kind, nowMs, stage, Double.NaN, Double.NaN, false, rnd.nextLong());
            nextStrikeMs = nowMs + level.rateSec() * 1000 * (0.35 + rnd.nextDouble() * 1.3);
        }
        active.removeIf(s -> s.finished(nowMs));
        for (int i = pendingThunder.size() - 1; i >= 0; i--) {
            Strike s = pendingThunder.get(i);
            if (nowMs >= s.thunderAtMs()) {
                pendingThunder.remove(i);
                for (StormListener l : listeners) {
                    l.thunderArrived(s);
                }
            }
        }
    }

    /** Blitz per Mausklick: Einschlag unter der Klickstelle, je höher der Klick, desto näher. */
    public Strike strikeAt(double x, double y, double nowMs, Stage stage) {
        double q = Math.max(0, Math.min(1, y / stage.horizonY()));
        return spawn(StrikeKind.CG, nowMs, stage, x, 0.4 + q * q * 9, true, rnd.nextLong());
    }

    /** Erzeugt einen Blitz. {@code x}/{@code distKm} = NaN → zufällig nach Stufe. */
    public Strike spawn(StrikeKind kind, double nowMs, Stage stage, double x, double distKm, boolean clicked, long seed) {
        Random r = new Random(seed);
        double w = stage.width(), h = stage.height(), hor = stage.horizonY(), span = stage.span();
        double dist = Double.isNaN(distKm)
                ? level.distMinKm() + (level.distMaxKm() - level.distMinKm()) * Math.pow(r.nextDouble(), 0.8)
                : distKm;
        double q = clamp(1 - Math.sqrt(Math.max(0, dist - 0.3) / 12), 0.03, 0.96);
        double lead = 60 + r.nextDouble() * 100;
        List<List<BoltSegment>> bolts = new ArrayList<>();
        double gx, gy, cx, cy, scale;
        Envelope env;
        Integer ka = null;
        switch (kind) {
            case IC -> {
                cx = w * (0.05 + r.nextDouble() * 0.9);
                cy = lerp(hor * 0.83, h * 0.2, q);
                gx = cx;
                gy = cy;
                scale = 0.4 + 0.9 * q;
                if (r.nextDouble() < 0.55) {
                    bolts.add(BoltGenerator.spider(r, cx, cy + h * 0.03, r.nextBoolean() ? -1 : 1,
                            span * (0.18 + r.nextDouble() * 0.22)));
                }
                env = Envelope.random(r, soft ? 60 : 30, 2 + r.nextInt(4), soft ? 0.8 : 0.75, soft);
            }
            case CROWN -> {
                cx = w * stage.emberX();
                cy = h * stage.emberY();
                q = Math.max(q, 0.5);
                scale = 1;
                int n = 3 + r.nextInt(3);
                double mx = cx, my = hor;
                for (int i = 0; i < n; i++) {
                    double bx = cx + (i - (n - 1) / 2.0) * w * (0.12 + r.nextDouble() * 0.06) + (r.nextDouble() - 0.5) * w * 0.05;
                    double by = hor + (stage.shoreY(bx) - hor) * (0.25 + r.nextDouble() * 0.4);
                    bolts.add(BoltGenerator.cloudToGround(r, cx + (r.nextDouble() - 0.5) * 20, cy + 10, bx, by));
                    if (i == n / 2) {
                        mx = bx;
                        my = by;
                    }
                }
                gx = mx;
                gy = my;
                env = Envelope.random(r, lead, 2 + r.nextInt(3), 1, soft);
                ka = peakCurrent(r, true);
            }
            default -> {
                gx = Double.isNaN(x) ? w * (0.08 + r.nextDouble() * 0.84) : x;
                gy = hor + (stage.shoreY(gx) - hor) * q * 0.92;
                cy = lerp(hor * 0.63, h * 0.1, q);
                cx = gx + (r.nextDouble() - 0.5) * span * 0.12;
                scale = 0.45 + 1.3 * q;
                bolts.add(BoltGenerator.cloudToGround(r, cx, cy, gx, gy));
                env = Envelope.random(r, lead, 2 + r.nextInt(3), 1, soft);
                ka = peakCurrent(r, false);
            }
        }
        Strike s = new Strike(kind, clicked, seed, nowMs, dist, q, gx, gy, cx, cy, scale, ka, env, bolts);
        active.add(s);
        pendingThunder.add(s);
        recent.add(nowMs);
        last = s;
        for (StormListener l : listeners) {
            l.strikeStarted(s);
        }
        return s;
    }

    /** Scheitelstrom: meist negativ 8–80 kA, gut jeder zehnte positiv und stärker. */
    private static int peakCurrent(Random r, boolean crown) {
        double base = 8 + r.nextDouble() * r.nextDouble() * 70 + (crown ? 15 : 0);
        return r.nextDouble() < 0.1 ? (int) Math.round(base * 1.6) : -(int) Math.round(base);
    }

    private static double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private static double clamp(double v, double lo, double hi) {
        return v < lo ? lo : v > hi ? hi : v;
    }
}
