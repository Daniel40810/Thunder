package com.dan.thunder.storm;

import java.util.Random;

/**
 * Lichtkurve eines Blitzes: Leitblitz (schwach, wachsend), Hauptentladung und Folgeblitze mit
 * exponentiellem Abklingen, dazu ein langsamer Reststrom.
 */
public final class Envelope {

    private final double leadMs;
    private final double tauMs;
    private final double[] strokeAt;
    private final double[] strokeAmp;

    public Envelope(double leadMs, double tauMs, double[] strokeAt, double[] strokeAmp) {
        this.leadMs = leadMs;
        this.tauMs = tauMs;
        this.strokeAt = strokeAt;
        this.strokeAmp = strokeAmp;
    }

    /** Zufällige Kurve. Im sanften Modus nur eine gedämpfte, langsam abklingende Entladung. */
    public static Envelope random(Random rnd, double leadMs, int strokes, double amp, boolean soft) {
        int n = soft ? 1 : Math.max(1, strokes);
        double[] at = new double[n], a = new double[n];
        double t = leadMs;
        for (int i = 0; i < n; i++) {
            if (i > 0) {
                t += 40 + rnd.nextDouble() * 110;
            }
            at[i] = t;
            a[i] = (soft ? amp * 0.5 : amp) * (i == 0 ? 1 : 0.35 + rnd.nextDouble() * 0.55);
        }
        return new Envelope(leadMs, soft ? 200 : 55, at, a);
    }

    public double leadMs() {
        return leadMs;
    }

    /** Helligkeit zum Zeitpunkt {@code ms} nach Beginn (0 … etwa 1,2). */
    public double intensity(double ms) {
        if (ms < 0) {
            return 0;
        }
        if (ms < leadMs) {
            return 0.1 * ms / leadMs;
        }
        double v = 0;
        for (int i = 0; i < strokeAt.length; i++) {
            if (ms >= strokeAt[i]) {
                v += strokeAmp[i] * Math.exp(-(ms - strokeAt[i]) / tauMs);
            }
        }
        return v + 0.12 * Math.exp(-(ms - leadMs) / 350);
    }

    /** Ab hier ist der Blitz praktisch erloschen. */
    public double durationMs() {
        return strokeAt[strokeAt.length - 1] + 900;
    }

    public int strokeCount() {
        return strokeAt.length;
    }
}
