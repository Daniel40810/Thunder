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
 * Der Grund des Stücks ist das Mittel des Geländes über seine Breite. Seit Sylt Phase 19 (wie Lighthouse Phase 17)
 * lässt die See kein Loch mehr: Das Netz trägt die Höhe von {@code ocean} ({@link #see}); wo es tiefer als
 * {@link #UEBER_GRUND} m ist, taucht sein ruhiges Wasser unter die See, und nur das weiße Wasser liegt obenauf. Am
 * seewärtigen Rand und an den Seiten ist das Netz in den äußeren {@link #NAHT} m ganz die See. So zeichnet sich das
 * Stück nicht mehr als Rechteck in der See ab (Befund 18c). Ändert sich der Seegang deutlich, wird ein neues Stück im
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

    /** Die Höhe der See an einem Ort (von {@code ocean}); null = ohne Naht. */
    public interface See { double hoehe(double x, double z); }
    public volatile See see;
    /** Naht: Rand in m, Raster der Abfrage in m, wie weit das ruhige Wasser unter der See liegt (m), ab welcher Tiefe die See zeichnet (m). */
    static final double NAHT = 12, SEE_RASTER = 2.5, UNTER = 0.6, UEBER_GRUND = -0.5, SEE_TIEFE = 0.45;

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
        naht(s, see);
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
        naht(st, see);
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

    /** Seit Sylt Phase 19 kein Loch mehr in der See (die Naht legt das Netz an sie an); bleibt für die Hülle: null. */
    public double[][] loch() { return null; }

    /**
     * Die See im Netz (aus Lighthouse Phase 17, {@code Sockelbrandung.naht}): die Höhe von {@code ocean} auf einem Raster
     * von {@link #SEE_RASTER} m legt sich unter das Netz. Am Rand zur See und an den Seiten ist das Netz ganz die See,
     * innen kommt der Wellenzug dazu. Wo der Grund tiefer als {@link #UEBER_GRUND} liegt, zeichnet die See selbst: Dort
     * zeigt sich nur das weiße Wasser über ihr, das ruhige taucht {@link #UNTER} m unter sie. Danach die Normalen neu.
     */
    static void naht(Stueck s, See see) {
        if (see == null) return;
        com.dan.surf.BreakerMesh m = s.netz;
        double laenge = s.xEnde;
        int gx = (int) Math.ceil(laenge / SEE_RASTER) + 1, gy = (int) Math.ceil(BREITE / SEE_RASTER) + 1;
        double[] g = new double[gx * gy];
        for (int j = 0; j < gy; j++)
            for (int i = 0; i < gx; i++) {
                double[] p = s.welt(i * SEE_RASTER, j * SEE_RASTER);
                g[j * gx + i] = see.hoehe(p[0], p[1]);
            }
        float[] v = m.vertices;
        int ns = m.ns, ny = m.ny;
        double level = s.zug.level;
        for (int r = 0; r < ny; r++)
            for (int k = 0; k < ns; k++) {
                int o = (r * ns + k) * com.dan.surf.BreakerMesh.STRIDE;
                double x = v[o], y = v[o + 2];
                // Rand: seewärts (x = 0) und an den Seiten; landwärts liegt der Strand
                double ed = Math.min(x, Math.min(y, BREITE - y));
                double w = Math.max(0, Math.min(1, ed / NAHT));
                w = w * w * (3 - 2 * w);
                double u = Math.max(0, Math.min(gx - 1.001, x / SEE_RASTER)), q = Math.max(0, Math.min(gy - 1.001, y / SEE_RASTER));
                int i = (int) u, j = (int) q;
                double fu = u - i, fq = q - j;
                double h = (g[j * gx + i] * (1 - fu) + g[j * gx + i + 1] * fu) * (1 - fq) + (g[(j + 1) * gx + i] * (1 - fu) + g[(j + 1) * gx + i + 1] * fu) * fq;
                double bed = v[o + 9], still = Math.max(0, level - bed);
                double lim = SEE_TIEFE * still / Math.max(1e-3, w);
                double sea = Math.max(-lim, Math.min(lim, h - level));
                double yt = v[o + 1];
                if (yt < bed) continue;
                double yn = level + sea + (yt - level) * w;
                if (bed < UEBER_GRUND) {
                    double a = Math.max(0, Math.min(1, (Math.max(v[o + 6], v[o + 7]) * w - 0.12) / 0.33));
                    a = a * a * (3 - 2 * a);
                    double top = h + Math.max(0, yt - level) * w + 0.04;
                    yn = (h - UNTER) * (1 - a) + top * a;
                }
                v[o + 1] = (float) Math.max(bed - 0.03, yn);
                v[o + 6] *= (float) w;
                v[o + 7] *= (float) w;
            }
        for (int r = 0; r < ny; r++)
            for (int i = 0; i < ns; i++) {
                int st = com.dan.surf.BreakerMesh.STRIDE;
                int i0 = Math.max(0, i - 1), i1 = Math.min(ns - 1, i + 1), r0 = Math.max(0, r - 1), r1 = Math.min(ny - 1, r + 1);
                int a = (r * ns + i0) * st, b = (r * ns + i1) * st, c = (r0 * ns + i) * st, d = (r1 * ns + i) * st;
                double sx = v[b] - v[a], sy = v[b + 1] - v[a + 1], sz = v[b + 2] - v[a + 2];
                double tx = v[d] - v[c], ty = v[d + 1] - v[c + 1], tz = v[d + 2] - v[c + 2];
                double nx = ty * sz - tz * sy, nyy = tz * sx - tx * sz, nz = tx * sy - ty * sx;
                double l = Math.sqrt(nx * nx + nyy * nyy + nz * nz);
                if (l < 1e-12) { nx = 0; nyy = 1; nz = 0; l = 1; }
                int o = (r * ns + i) * st;
                v[o + 3] = (float) (nx / l);
                v[o + 4] = (float) (nyy / l);
                v[o + 5] = (float) (nz / l);
            }
    }
}
