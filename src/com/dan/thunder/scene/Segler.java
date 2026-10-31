package com.dan.thunder.scene;

import com.dan.cloth.Cloth;
import com.dan.sail.Sail;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Rückfluss aus Timmendorf (Phase 18, F29 A): Segelboote aus {@code com.dan.sail} und Flaggen aus {@code com.dan.cloth}
 * am Ostseestrand.
 * <ul>
 * <li><b>Boote:</b> zwei Fahrtenyachten und ein Gaffelschoner laufen vor dem Strand hin und her, 350 bis 1 100 m
 * draußen. Der Wind kommt aus Südwest (ablandig, {@code Wiring}), er steht quer zur Küste: Beide Kurse längs des Strands
 * sind halber Wind. Am Ende des Reviers halsen sie und laufen zurück. Fahrt, Krängung und Abdrift rechnet {@code sail};
 * im Unwetter (11 m/s) krängen sie stark und fieren, wenn es zu viel wird. Die Lage im Wasser kommt aus {@code ocean}.</li>
 * <li><b>Flaggen:</b> am Kopf der Seebrücke die Landesflagge Schleswig-Holstein, am Strand vor den Körben die rote
 * Flagge „Baden verboten“ (bei Gewitter gesetzt, DLRG) [G]. Beide an 6-m-Stangen, Tuch nach XPBD, Wind mit Höhenprofil
 * und Böen [G].</li>
 * </ul>
 * Alles ist abschaltbar ({@code Renderer.showSegler}); dann bleibt die Szene, wie sie war.
 */
public final class Segler {
    /** Revier im Blickrahmen: s voraus von–bis (m), r rechts von–bis (m), Mitte der Bahn je Boot. */
    public static final double R_MIN = -650, R_MAX = 700;

    public final Sail sail = Sail.breeze();
    public final List<Sail.Boat> boote = new ArrayList<>();
    /** Bahn je Boot: Abstand s vom Strand (m). */
    private final List<Double> bahn = new ArrayList<>();
    public final List<Cloth> flaggen = new ArrayList<>();
    /** Fuß der Stangen {x, y, z, Höhe}. */
    public final List<double[]> stangen = new ArrayList<>();
    private double time, windSpeed = 6, windFrom = 225;
    /** Tuch mitrechnen (die Prüfung lässt es für lange Läufe aus; XPBD ist der teure Teil). */
    public boolean tuch = true;
    private int halsen, wenden, kenterungen;

    public Segler() {
        sail.seed = 2026;
        sail.wind = new Sail.Wind() {
            public double speed(double x, double z, double t) { return windSpeed * boe(x, z, t); }
            public double fromDeg(double x, double z, double t) { return windFrom + 5 * Math.sin(t * 0.021 + x * 0.002) * Math.cos(t * 0.013 - z * 0.0017); }
        };
        sail.water = (x, z, t) -> 0;
        sail.events = new Sail.Events() {
            @Override public void onGybe(Sail.Boat b, double v) { halsen++; }
            @Override public void onTack(Sail.Boat b) { wenden++; }
            @Override public void onCapsize(Sail.Boat b) { kenterungen++; }
        };
        boot(Sail.CRUISER, "GER 4711", 420, -150, 135, new float[]{0.82f, 0.82f, 0.80f}, null);
        boot(Sail.CRUISER, "GER 1885", 680, 260, 315, new float[]{0.05f, 0.09f, 0.20f}, null);
        boot(Sail.SCHOONER, "Ostwind", 1050, -320, 135, new float[]{0.12f, 0.05f, 0.03f}, new float[]{0.62f, 0.45f, 0.30f});
        // Flaggen: Seebrücke (Kopf, neben der Laterne) und Strand (vor den Körben)
        double[] k = Site.welt(Site.BRUECKE_KOPF[0] - 3, Site.BRUECKE_KOPF[1] + Site.BRUECKE_BREITE / 2 - 0.2);
        flagge(k[0], Site.DECK, k[1], Cloth.SCHLESWIG_HOLSTEIN, 1.5, 1.0);
        double[] st = Site.welt(25, -9);
        flagge(st[0], Gelaende.height(st[0], st[1]), st[1], ROT, 1.2, 0.8);
    }

    /** Rot der Flagge „Baden verboten“ (linear) [G]. */
    public static final float[] ROT = {0.70f, 0.02f, 0.02f};

    private void boot(Sail.Rig rig, String label, double s, double r, double heading, float[] hull, float[] tuch) {
        Sail.Boat b = new Sail.Boat(rig);
        double[] p = Site.welt(s, r);
        b.x = p[0];
        b.z = p[1];
        b.headingDeg = heading;
        b.targetHeadingDeg = heading;
        b.speed = rig.kind() == Sail.Kind.SHIP ? 2.5 : 3.0;
        b.label = label;
        b.hullColor = hull;
        if (tuch != null) b.sailColor = tuch;
        boote.add(b);
        bahn.add(s);
    }

    private void flagge(double x, double y, double z, float[] farben, double w, double h) {
        Cloth c = Cloth.flag(x, y + 6, z, w, h);
        c.colors = farben.clone();
        c.seed = 2026 + flaggen.size();
        c.wind = (px, py, pz, t, o) -> {
            double u = windSpeed * Math.pow(Math.max(0.5, py - Math.max(0, y - 1)) / 10, 0.14) * boe(px, pz, t);
            double to = Math.toRadians(windFrom + 180 + 8 * Math.sin(t * 0.37 + px));
            o[0] = u * Math.sin(to);
            o[1] = 0;
            o[2] = -u * Math.cos(to);
        };
        c.ground = (px, pz) -> Math.max(Gelaende.height(px, pz), y);
        flaggen.add(c);
        stangen.add(new double[]{x, y, z, 6.15});
    }

    /** Böen [G]: zwei langsame Wellen, ±25 %. */
    static double boe(double x, double z, double t) {
        return 1 + 0.15 * Math.sin(t * 0.11 + x * 0.004) + 0.10 * Math.sin(t * 0.47 - z * 0.006 + 1.3);
    }

    /** Ein Schritt: Wind setzen, Rudergänger, Segeln, Tuch. */
    public void schritt(double dt, double ws, double from, double nass) {
        if (dt <= 0) return;
        windSpeed = ws;
        windFrom = from;
        sail.windSpeed = ws;
        sail.windFromDeg = from;
        time += dt;
        double h = Math.min(dt, 0.05);
        int n = (int) Math.ceil(dt / h);
        for (int i = 0; i < boote.size(); i++) {
            Sail.Boat b = boote.get(i);
            ruder(b, bahn.get(i));
            for (int k = 0; k < n; k++) sail.step(b, dt / n);
        }
        if (tuch) for (Cloth c : flaggen) {
            c.wetness = nass;
            c.step(dt);
        }
    }

    /** Der Rudergänger: längs der Küste halber Wind, am Ende des Reviers halsen, die Bahn halten. */
    private void ruder(Sail.Boat b, double s0) {
        double[] f = Site.rahmen(b.x, b.z);
        double s = f[0], r = f[1];
        double laengs = Site.BLICK + 90;                     // 135°: r wächst
        boolean nachRechts = angle(b.targetHeadingDeg, laengs) < 90;
        if (nachRechts && r > R_MAX) nachRechts = false;
        else if (!nachRechts && r < R_MIN) nachRechts = true;
        double korrektur = Math.max(-12, Math.min(12, (s0 - s) * 0.06));
        b.targetHeadingDeg = nachRechts ? norm(laengs - korrektur) : norm(laengs + 180 + korrektur);
    }

    static double angle(double a, double b) { return Math.abs(((a - b) % 360 + 540) % 360 - 180); }
    static double norm(double a) { return (a % 360 + 360) % 360; }

    /** Die Stangen als Dreiecke (9 Werte je Ecke: Lage, Normale, Farbe linear) für {@code SailGl}. */
    public float[] stangenDreiecke() {
        int seg = 8;
        float[] o = new float[stangen.size() * seg * 2 * 3 * 9 + stangen.size() * seg * 3 * 9];
        int p = 0;
        for (double[] st : stangen) {
            double r0 = 0.055, r1 = 0.035;
            for (int i = 0; i < seg; i++) {
                double a0 = 2 * Math.PI * i / seg, a1 = 2 * Math.PI * (i + 1) / seg;
                double[][] q = {
                        {st[0] + r0 * Math.cos(a0), st[1], st[2] + r0 * Math.sin(a0), Math.cos(a0), Math.sin(a0)},
                        {st[0] + r0 * Math.cos(a1), st[1], st[2] + r0 * Math.sin(a1), Math.cos(a1), Math.sin(a1)},
                        {st[0] + r1 * Math.cos(a1), st[1] + st[3], st[2] + r1 * Math.sin(a1), Math.cos(a1), Math.sin(a1)},
                        {st[0] + r1 * Math.cos(a0), st[1] + st[3], st[2] + r1 * Math.sin(a0), Math.cos(a0), Math.sin(a0)}};
                int[] idx = {0, 1, 2, 0, 2, 3};
                for (int k : idx) p = ecke(o, p, q[k][0], q[k][1], q[k][2], q[k][3], 0, q[k][4], 0.78f, 0.78f, 0.76f);
                // Knauf
                double ty = st[1] + st[3];
                p = ecke(o, p, st[0], ty + 0.06, st[2], 0, 1, 0, 0.75f, 0.62f, 0.25f);
                p = ecke(o, p, st[0] + 0.06 * Math.cos(a0), ty, st[2] + 0.06 * Math.sin(a0), Math.cos(a0), 0.4, Math.sin(a0), 0.75f, 0.62f, 0.25f);
                p = ecke(o, p, st[0] + 0.06 * Math.cos(a1), ty, st[2] + 0.06 * Math.sin(a1), Math.cos(a1), 0.4, Math.sin(a1), 0.75f, 0.62f, 0.25f);
            }
        }
        return o;
    }

    private static int ecke(float[] o, int p, double x, double y, double z, double nx, double ny, double nz, float r, float g, float b) {
        o[p++] = (float) x; o[p++] = (float) y; o[p++] = (float) z;
        o[p++] = (float) nx; o[p++] = (float) ny; o[p++] = (float) nz;
        o[p++] = r; o[p++] = g; o[p++] = b;
        return p;
    }

    /** Wohin die freie Ecke der Flagge i zeigt (Grad von Nord). */
    public double flaggeNach(int i) {
        Cloth c = flaggen.get(i);
        double[] tip = c.point(c.nu / 2, c.nv - 1), base = c.point(c.nu / 2, 0);
        return norm(Math.toDegrees(Math.atan2(tip[0] - base[0], -(tip[2] - base[2]))));
    }

    /** Eine Zeile für das HUD. */
    public String zeile() {
        StringBuilder s = new StringBuilder("Segler:");
        for (Sail.Boat b : boote) {
            double twa = angle(b.headingDeg, windFrom);
            s.append(String.format(Locale.GERMAN, " %s %.1f kn %03.0f° (%.0f° zum Wind, Krängung %.0f°)%s;", b.label, b.speed / Sail.KN, b.headingDeg, twa, Math.abs(b.rollDeg), b.state().isEmpty() ? "" : " " + b.state()));
        }
        s.append(String.format(Locale.GERMAN, " %d Wenden, %d Halsen, %d gekentert · Flaggen wehen nach %03.0f° und %03.0f°", wenden, halsen, kenterungen, flaggeNach(0), flaggeNach(1)));
        return s.toString();
    }

    public int halsen() { return halsen; }
    public int kenterungen() { return kenterungen; }
    public double zeit() { return time; }
}
