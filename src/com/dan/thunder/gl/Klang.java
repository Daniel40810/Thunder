package com.dan.thunder.gl;

import com.dan.lightning.Bolt;
import com.dan.lightning.Lightning;
import com.dan.soundscape.AudioOut;
import com.dan.soundscape.Soundscape;
import com.dan.soundscape.Soundscape.Kind;
import com.dan.surf.Surf;
import com.dan.thunder.scene.Brandung;
import com.dan.thunder.scene.Gelaende;
import com.dan.thunder.scene.Site;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.SplittableRandom;

/**
 * Der Klang (Phase 18b, F51 A) aus {@code com.dan.soundscape}, erzeugt, ohne Audiodateien; er ersetzt den
 * {@code ThunderSynth} des 2D-Thunder. Das Ohr ist die Kamera. Verdrahtet (nach Sylt Phase 14):
 * <ul>
 * <li><b>Donner</b> aus dem Kanal jedes Blitzes ({@code lightning.onThunder}): jedes Stück mit seinem Abstand zum
 * Ohr, der Hauptkanal voll, Äste schwächer, alle Schläge; die Laufzeit hat das Paket schon abgewartet.</li>
 * <li><b>Regen</b> am Ohr aus {@code rain}, <b>Wind</b> aus Südwest nach der Stufe, Strandhafer auf der Düne.</li>
 * <li><b>Ferne Brandung</b>: die Wasserlinie als Kette von Stücken zu 25 m auf ±1,5 km, die Leistung der brechenden
 * Wellen je m aus H_s am Strand [G]; im Stück der Brandung vor dem Hauptblick klingen die einzelnen Wellen
 * (Schwallbrecher, Sturzbrecher, Schwall), wie in Sylt.</li>
 * <li><b>Pfähle</b> der Seebrücke im Wasser: die Wellen glucksen daran.</li>
 * </ul>
 * Ereignisse rechnet ein eigener Faden (nur bei Ton an), {@link AudioOut} spielt in seinem.
 */
public final class Klang {
    public static final double LINIE = 1500, STUECK = 25, SEITE = 50;

    public final Soundscape s = new Soundscape();
    private final Renderer r;
    private AudioOut out;
    public volatile boolean on, offline;
    private final SplittableRandom rng = new SplittableRandom(2026);
    private final java.util.concurrent.ExecutorService synth = java.util.concurrent.Executors.newSingleThreadExecutor(q -> {
        Thread t = new Thread(q, "Thunder-Klang");
        t.setDaemon(true);
        t.setPriority(Thread.NORM_PRIORITY - 1);
        return t;
    });
    private final java.util.concurrent.atomic.AtomicInteger queued = new java.util.concurrent.atomic.AtomicInteger();
    public volatile int donner, schwallbrecher, sturzbrecher, schwaelle;
    public volatile String note = "";
    private Brandung.Stueck hooked;
    private volatile double yOhr = Double.NaN;
    private final double[] lastPlunge = new double[3], lastSpill = new double[3];
    private double time, lineT = 99, lineX = 1e18, lineZ = 1e18, lineHs = -1;
    private final Set<Integer> angekommen = new HashSet<>();

    Klang(Renderer r) {
        this.r = r;
        s.seed = 2026;
        s.quality = Soundscape.Quality.MEDIUM;
        s.windCarry = true;
        s.temperatureC = 19;
        for (double[] p : r.bauwerke.pfaehle) s.pilings.add(p);
        r.gewitter.blitz.onThunder = this::donner;
    }

    /** Donner aus dem Kanal des Blitzes (aus Lighthouse Phase 14). */
    void donner(Lightning.Thunder t) {
        donner++;
        r.donnerAngekommen(t.distance());
        double[] ear = {s.earX, s.earY, s.earZ};
        Bolt b = t.bolt();
        double[] d = null, w = null, strokes = null;
        if (b != null && b.n > 0) {
            int n = b.n;
            d = new double[n];
            w = new double[n];
            for (int i = 0; i < n; i++) {
                double mx = (b.ax[i] + b.bx[i]) / 2.0, my = (b.ay[i] + b.by[i]) / 2.0, mz = (b.az[i] + b.bz[i]) / 2.0;
                d[i] = Math.sqrt((mx - ear[0]) * (mx - ear[0]) + (my - ear[1]) * (my - ear[1]) + (mz - ear[2]) * (mz - ear[2]));
                w[i] = b.length(i) * (b.order[i] == 0 ? 1 : 0.4);
            }
            strokes = b.strokeTime.clone();
            for (int k = strokes.length - 1; k >= 0; k--) strokes[k] -= strokes[0];
        }
        final double[] dd = d, ww = w, ss = strokes;
        run(() -> s.thunder(t.x(), t.y(), t.z(), t.levelDb(), dd, ww, ss, true));
    }

    private void run(Runnable job) {
        if (offline) { job.run(); return; }
        if (!on || queued.get() > 24) return;
        queued.incrementAndGet();
        synth.execute(() -> { try { job.run(); } catch (RuntimeException e) { note = "!" + e; } finally { queued.decrementAndGet(); } });
    }

    private void ev(Kind k, double x, double y, double z, double str, double tone) { run(() -> s.event(k, x, y, z, str, tone)); }

    /** Ton an oder aus; gibt zurück, ob er läuft. */
    public synchronized boolean setOn(boolean b) {
        on = b;
        if (b && out == null) {
            try { out = new AudioOut(s); out.start(); note = ""; } catch (RuntimeException | LinkageError e) { note = "!kein Ton: " + e.getMessage(); out = null; on = false; }
        } else if (!b && out != null) { out.stop(); out = null; }
        if (on && out != null && !out.error().isEmpty()) { note = "!kein Ton: " + out.error(); out.stop(); out = null; on = false; }
        return on;
    }

    public synchronized void dispose() {
        if (out != null) { out.stop(); out = null; }
        on = false;
        synth.shutdownNow();
    }

    public String describe() {
        return (on ? "Klang an · " : "Klang aus · ") + s.describe()
                + String.format(Locale.GERMAN, " · %d Donner · Wellen %d Schwallbrecher, %d Sturzbrecher, %d Schwälle", donner, schwallbrecher, sturzbrecher, schwaelle)
                + (note.isEmpty() ? "" : " · " + note);
    }

    /** Je Bild: Ohr, Wind, Regen, See, Brandungslinie, einzelne Wellen. */
    public void feed(double dt) {
        time += dt;
        var w = r.wiring;
        var cam = r.camera;
        double ex = cam.eye[0], ey = cam.eye[1], ez = cam.eye[2];
        double grund = Gelaende.height(ex, ez), hoch = ey - Math.max(grund, 0);
        s.earX = ex; s.earY = ey; s.earZ = ez;
        s.earYawDeg = cam.heading();
        s.windSpeed = w.windSpeed();
        s.windFromDeg = w.windFromDeg();
        s.gustiness = 0.25 + 0.2 * w.wind();
        s.waterLevel = 0;
        s.rainRate = r.gewitter.regen.rate > 0 ? r.gewitter.regen.rateAt(ex, ez) : 0;
        // die offene See: so laut, wie das Wasser nah ist (Wasserlinie im Blickrahmen)
        double[] f = Site.rahmen(ex, ez);
        double off = f[0] - Gelaende.wasserlinie(f[1]);
        double sea = off >= 0 ? 1 : 1 / (1 + -off / 60);
        double hs = w.seaHs();
        s.waveHeight = Math.max(0.05, hs) * sea;
        s.wavePeriod = Math.max(3, w.seaTp());
        // Strandhafer auf der Düne (über 3 m und hinter dem Strand)
        s.marramNear = hoch < 4 && grund > 2.5 ? Math.min(1, (grund - 2.5) / 3) * 0.7 : 0;
        // die ferne Brandung entlang der Wasserlinie
        lineT += dt;
        Brandung.Stueck st = r.brandung.stueck();
        if (lineT > 2 || Math.hypot(ex - lineX, ez - lineZ) > 20 || Math.abs(hs - lineHs) > 0.05) {
            lineT = 0;
            lineX = ex; lineZ = ez; lineHs = hs;
            s.surfLine = linie(ex, ez, hs, w.seaTp(), st);
        }
        if (st != hooked) {
            if (hooked != null) { hooked.zug.onBreaking = null; hooked.zug.onImpact = null; }
            angekommen.clear();
            hooked = st;
            if (st != null) haken(st);
        }
        if (st != null) {
            double[] l = st.lokal(ex, ez);
            yOhr = Math.max(0, Math.min(st.zug.width, l[1]));
            schwall(st);
        }
    }

    /**
     * Die Brandungslinie: je 25 m die Wasserlinie, 20 m davor die Stelle, wo die Wellen brechen; die Leistung, die je m
     * in der Brandung vergeht, ist der Energiefluss der Wellen beim Brechen, E·c_g = ρ g H² / 8 · √(g H / 0,78) [G];
     * die Ostsee bricht überwiegend schäumend (Anteil Sturzbrecher 0,2). Das Stück der Brandung bleibt ausgespart.
     */
    double[][] linie(double ex, double ez, double hs, double tp, Brandung.Stueck st) {
        double[] f = Site.rahmen(ex, ez);
        double flux = 1025 * 9.81 * hs * hs / 8 * Math.sqrt(9.81 * Math.max(0.1, hs) / 0.78);
        double pw = Soundscape.surfAcousticPower(flux);
        double nahA = Brandung.MITTE_R - Brandung.BREITE / 2, nahB = Brandung.MITTE_R + Brandung.BREITE / 2;
        List<double[]> out = new ArrayList<>();
        for (double ra = f[1] - LINIE; ra <= f[1] + LINIE; ra += STUECK) {
            if (st != null && ra > nahA && ra < nahB) continue;
            double sa = Gelaende.wasserlinie(ra) - 20;
            double[] p = Site.welt(sa, ra);
            if (Gelaende.height(p[0], p[1]) > -0.2) continue;   // die Landzunge: dort liegt kein Strand vor
            out.add(new double[]{p[0], p[1], pw, 0.2, tp, STUECK});
        }
        return out.isEmpty() ? null : out.toArray(new double[0][]);
    }

    /** Der Schwall (wie Sylt): erreicht eine Walze den Rand des Schwalls, läuft sie hinauf; drei Stellen je Welle. */
    private void schwall(Brandung.Stueck st) {
        var zug = st.zug;
        var sw = st.schwall;
        double rand = sw.boundaryX() - 3, yo = yOhr;
        Set<Integer> da = new HashSet<>();
        for (var wv : zug.waves) {
            da.add(wv.id);
            if (wv.x < rand || angekommen.contains(wv.id)) continue;
            angekommen.add(wv.id);
            for (int k = 0; k < 3; k++) {
                double yt = yo + (k - 1) * SEITE;
                if (yt < 0 || yt > sw.width) continue;
                int row = Math.max(0, Math.min(zug.rows - 1, (int) (yt / zug.width * zug.rows)));
                double hw = zug.heightRow(wv, row);
                if (!(hw > 0.04)) continue;
                double h = Math.max(0.05, Math.min(1.5, 0.6 * hw + 0.1)) * (0.85 + 0.3 * rng.nextDouble());
                int srow = Math.max(0, Math.min(sw.rows - 1, (int) (yt / sw.width * sw.rows)));
                double tip = sw.tipX(srow);
                double[] p = st.welt(Double.isNaN(tip) ? st.xWasserlinie : tip + 1, yt);
                schwaelle++;
                ev(Kind.SCHWALL, p[0], Gelaende.height(p[0], p[1]) + 0.05, p[1], 0.85, h);
            }
        }
        angekommen.retainAll(da);
    }

    private void haken(Brandung.Stueck st) {
        st.zug.onBreaking = (x, y, hb, type) -> {
            if (type == Surf.Breaker.PLUNGING) return;   // der Sturzbrecher klingt beim Aufschlag der Lippe
            double yo = yOhr;
            if (Double.isNaN(yo)) return;
            for (int k = 0; k < 3; k++) {
                double yt = yo + (k - 1) * SEITE;
                if (yt < 0 || yt > st.zug.width || time - lastSpill[k] < 1.2) continue;
                lastSpill[k] = time;
                double[] p = st.welt(x, yt);
                schwallbrecher++;
                ev(Kind.SCHWALLBRECHER, p[0], 0.3 * hb, p[1], 0.85 + 0.15 * rng.nextDouble(), hb * (type == Surf.Breaker.SPILLING ? 1 : 0.8));
            }
        };
        st.zug.onImpact = (x, y, z, vol, speed) -> {
            double yo = yOhr;
            if (Double.isNaN(yo)) return;
            for (int k = 0; k < 3; k++) {
                double yt = yo + (k - 1) * SEITE;
                if (Math.abs(y - yt) > SEITE / 2 || time - lastPlunge[k] < 1.2) continue;
                lastPlunge[k] = time;
                double hb = Math.sqrt(Math.max(0, vol) * st.zug.rows / (0.12 * st.zug.width));
                double[] p = st.welt(x, y);
                sturzbrecher++;
                ev(Kind.STURZBRECHER, p[0], z, p[1], 0.85 + 0.15 * rng.nextDouble(), hb);
            }
        };
    }
}
