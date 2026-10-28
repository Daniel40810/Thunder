package com.dan.thunder.scene;

import com.dan.surf.BreakerMesh;
import com.dan.surf.Surf;
import com.dan.surf.Swash;
import com.dan.surf.WaveTrain;

import java.util.Locale;

/**
 * Die Ostsee-Brandung vor dem Strand (Phase 18a, F30 A: {@code surf.ostsee()}), nach der Nahbrandung aus Sylt: ein
 * Stück Strand von {@link #BREITE} m vor dem Hauptblick, quer zur Küste vom tiefen Wasser bis auf den Sand, mit den
 * Brechern aus {@link WaveTrain} und dem Auflauf aus {@link Swash}. Die Ostsee hat kurze Windsee (H_s 0,6 m, T_p 4 s
 * in der Vorgabe) und keine Tide; die Wellen brechen schäumend auf dem Riff und am steilen Strand.
 * <p>
 * Der Grund des Stücks ist das Mittel des Geländes über seine Breite. In dem Stück zeichnet nicht {@code ocean},
 * sondern das Netz (die See lässt ein Loch mit weichem Rand). Ändert sich der Seegang deutlich, wird ein neues Stück im
 * eigenen Faden gebaut und eingeschwungen (40 s), das alte läuft so lange weiter.
 */
public final class Brandung {
    /** Breite längs der Küste (m), Mitte des Stücks (r im Blickrahmen), Einschwingzeit (s). */
    public static final double BREITE = 220, MITTE_R = 25, EINSCHWINGEN = 40;
    /** Richtung zur Küste (von der See an Land) und längs (nach links) in der Szene. */
    public static final double SX = -Site.FX, SZ = -Site.FZ, AX = -SZ, AZ = SX;

    /** Ein Stück: Wellenzug, Schwall, Netz, Lage. */
    public static final class Stueck {
        public final WaveTrain zug;
        public final Swash schwall;
        public final BreakerMesh netz = new BreakerMesh();
        /** Ursprung (Rand zur See, Reihe 0) in der Szene, Ende landwärts (m lokal), Wasserlinie (m lokal), See beim Bau. */
        public final double ox, oz, xEnde, xWasserlinie, hs, tp;
        Stueck(WaveTrain zug, Swash schwall, double ox, double oz, double xEnde, double xWasserlinie, double hs, double tp) {
            this.zug = zug;
            this.schwall = schwall;
            this.ox = ox;
            this.oz = oz;
            this.xEnde = xEnde;
            this.xWasserlinie = xWasserlinie;
            this.hs = hs;
            this.tp = tp;
        }

        public double[] welt(double x, double z) { return new double[]{ox + x * SX + z * AX, oz + x * SZ + z * AZ}; }

        public double[] lokal(double wx, double wz) {
            double dx = wx - ox, dz = wz - oz;
            return new double[]{dx * SX + dz * SZ, dx * AX + dz * AZ};
        }

        /** Matrix lokal → Szene (Spalten zuerst). */
        public float[] modell() {
            return new float[]{(float) SX, 0, (float) SZ, 0, 0, 1, 0, 0, (float) AX, 0, (float) AZ, 0, (float) ox, 0, (float) oz, 1};
        }
    }

    public final Surf surf = Surf.ostsee();
    private volatile Stueck aktiv;
    private volatile Thread bau;
    public volatile int reihen = 40;
    public volatile boolean an = true;
    public volatile String meldung = "noch kein Stück";
    public volatile double schrittMs, netzMs;
    public volatile Swash.Weather wetter = Swash.Weather.OVERCAST;

    public Stueck stueck() { return an ? aktiv : null; }

    public boolean baut() { return bau != null; }

    /** Je Bild: neu bauen, wenn sich der Seegang deutlich geändert hat, und das Stück um dt weiterrechnen. */
    public void schritt(double dt, double hs, double tp) {
        if (!an) { meldung = "Brandung aus"; return; }
        Stueck s = aktiv;
        boolean neu = s == null || Math.abs(hs - s.hs) > 0.15 * Math.max(0.2, s.hs) || Math.abs(tp - s.tp) > 0.6 || s.zug.rows != reihen;
        if (neu && bau == null) {
            final int rows = reihen;
            Thread th = new Thread(() -> {
                try {
                    aktiv = baue(hs, tp, rows);
                } catch (RuntimeException e) {
                    meldung = "Brandung: " + e.getMessage();
                } finally { bau = null; }
            }, "thunder-brandung");
            th.setDaemon(true);
            bau = th;
            th.start();
        }
        if (s == null) return;
        long t0 = System.nanoTime();
        double h = Math.min(0.05, Math.max(0, dt));
        s.schwall.weather = wetter;
        if (h > 0) {
            s.zug.step(h);
            s.schwall.step(h, s.zug);
        }
        long t1 = System.nanoTime();
        s.netz.build(s.zug, s.xEnde, s.schwall);
        netzMs = netzMs * 0.9 + (System.nanoTime() - t1) / 1e6 * 0.1;
        schrittMs = schrittMs * 0.9 + (t1 - t0) / 1e6 * 0.1;
        meldung = beschreibe(s);
    }

    String beschreibe(Stueck s) {
        int[] b = s.zug.brokenByType;
        return String.format(Locale.GERMAN, "Brandung %d Reihen auf %.0f m · H_s %.2f m, T_p %.1f s · %d Wellen · gebrochen %d Schwall, %d Sturz, %d Reflexion · Auflauf R2 %% %s · Rechnen %.1f ms, Netz %.1f ms",
                s.zug.rows, s.zug.width, s.hs, s.tp, s.zug.waves.size(), b[Surf.Breaker.SPILLING.ordinal()], b[Surf.Breaker.PLUNGING.ordinal()],
                b[Surf.Breaker.COLLAPSING.ordinal()] + b[Surf.Breaker.SURGING.ordinal()],
                Double.isNaN(s.schwall.r2()) ? "–" : String.format(Locale.GERMAN, "%.2f m", s.schwall.r2()), schrittMs, netzMs);
    }

    /** Ein Stück bauen und einschwingen (im aufrufenden Faden). */
    public Stueck baue(double hs, double tp, int rows) {
        hs = Math.max(0.05, hs);
        surf.hs = hs;
        surf.tp = tp;
        surf.bed = Gelaende::height;
        // die Wasserlinie in der Mitte des Stücks, seewärts bis in 2,5·H_s + 1,5 m Tiefe (höchstens 150 m: hinter dem Riff,
        // der Vorstrand der Ostsee fällt flach), landwärts bis 2,2 m über dem Wasser
        double[] w = Site.welt(Gelaende.wasserlinie(MITTE_R), MITTE_R);
        double tiefe = 2.5 * hs + 1.5;
        double xs = 0;
        while (xs < 150 && -mittel(w, -xs) < tiefe) xs += 2;
        double xl = 0;
        while (xl < 60 && mittel(w, xl) < 2.2) xl += 1;
        double ox = w[0] - xs * SX - BREITE / 2 * AX, oz = w[1] - xs * SZ - BREITE / 2 * AZ;
        double xEnde = xs + xl;
        int n = (int) Math.ceil(xEnde / 0.5) + 2;
        double[] grund = new double[n];
        for (int k = 0; k < n; k++) {
            double x = k * 0.5, s = 0;
            for (int q = 0; q < 7; q++) {
                double zl = (q + 0.5) / 7 * BREITE;
                s += Gelaende.height(ox + x * SX + zl * AX, oz + x * SZ + zl * AZ);
            }
            grund[k] = s / 7;
        }
        WaveTrain.Profile prof = x -> {
            double u = Math.max(0, Math.min(n - 1.001, x / 0.5));
            int i = (int) u;
            return grund[i] + (grund[i + 1] - grund[i]) * (u - i);
        };
        WaveTrain zug = new WaveTrain(rows, 0, xEnde, BREITE);
        zug.profile = prof;
        zug.level = 0;
        zug.tp = tp;
        double h0 = Math.max(0.3, -grund[0]);
        zug.hs = Math.min(hs * Surf.shoaling(tp, h0), 0.6 * h0);
        zug.seed = 2026;
        double xa = 0, ta = Math.min(h0 - 0.1, 1.6 * zug.hs + 0.3);
        while (xa < xEnde - 5 && -prof.bed(xa) > ta) xa += 0.5;
        Swash sw = new Swash(zug, Math.max(0, xa - 2), xEnde, 0.5);
        sw.weather = wetter;
        Stueck st = new Stueck(zug, sw, ox, oz, xEnde, xs, hs, tp);
        for (double t = 0; t < EINSCHWINGEN; t += 1 / 30.0) {
            zug.step(1 / 30.0);
            sw.step(1 / 30.0, zug);
        }
        sw.resetStatistics();
        st.netz.build(zug, xEnde, sw);
        meldung = beschreibe(st);
        return st;
    }

    /** Mittlerer Grund über die Breite, x m landwärts (negativ: seewärts) der Wasserlinie w. */
    private static double mittel(double[] w, double x) {
        double s = 0;
        for (int q = 0; q < 5; q++) {
            double zl = ((q + 0.5) / 5 - 0.5) * BREITE;
            s += Gelaende.height(w[0] + x * SX + zl * AX, w[1] + x * SZ + zl * AZ);
        }
        return s / 5;
    }

    /** Für Standbilder: jetzt bauen. */
    public void jetzt(double hs, double tp) { if (an) aktiv = baue(hs, tp, reihen); }

    /** Loch für die See: Mitte {x, z}, Achse {ux, uz} (zur Küste), halbe Länge, halbe Breite, weicher Rand; null ohne Stück. */
    public double[][] loch() {
        Stueck s = stueck();
        if (s == null) return null;
        double xa = 14, xe = s.xEnde, cx = (xa + xe) / 2;
        return new double[][]{s.welt(cx, BREITE / 2), {SX, SZ}, {(xe - xa) / 2, BREITE / 2 - 16, 14}};
    }
}
