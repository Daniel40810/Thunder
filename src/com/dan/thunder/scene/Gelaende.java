package com.dan.thunder.scene;

import java.util.Locale;

/**
 * Das Gelände des Ostseestrands (Phase 18a), gerechnet statt gemessen [G]. Im Blickrahmen (s voraus nach Nordost, r
 * nach rechts, {@link Site}):
 * <ul>
 * <li><b>Düne</b>: Kamm bei s = −6 m auf 8 m, der Standpunkt des Hauptblicks auf ihrem Hang (6 m), zur See fällt sie bis
 * s = 9 m auf den Strand, landwärts auf 3,5 m; links vorn ein Dünenausläufer (die dunkle Kuppe in den Bildern).</li>
 * <li><b>Strand</b>: von 2,2 m mit 1:15 bis zur Wasserlinie bei s = 41 m; links biegt die Küste zur Bucht nach vorn.</li>
 * <li><b>Vorstrand</b>: unter Wasser 1:22, dann flacher, ein Riff rund 85 m vor dem Ufer mit einer Rinne davor, draußen
 * bis −15 m (Lübecker Bucht: um 10 bis 20 m) [G].</li>
 * <li><b>Landzunge</b>: links draußen eine flache Sandzunge mit dem Leuchtturm (rund 2 m hoch, 55 m breit).</li>
 * <li><b>Hinterland</b>: ab 40 m hinter dem Kamm 3,5 m, leicht gewellt.</li>
 * </ul>
 * Java ({@link #height}) und die Grafikkarte (Netz aus {@link #netz}) zeigen dieselbe Fläche; die See nimmt den Grund aus
 * {@link #bett} (R16).
 */
public final class Gelaende {
    /** Wasserlinie vor dem Hauptblick (s, m), Fuß und Kamm der Düne (s, m), Höhen (m). */
    public static final double WASSERLINIE = 41, DUENENFUSS = 9, KAMM = -6, KAMM_HOEHE = 8.0, STRAND_OBEN = 2.2, HINTERLAND = 3.5;
    /** Tiefe weit draußen (m, negativ). */
    public static final double DRAUSSEN = -15;
    /** Landzunge: Wurzel und Spitze (Blickrahmen s, r), halbe Breite (m), Höhe (m). */
    public static final double[] ZUNGE_A = {66, -262}, ZUNGE_B = {362, -208};
    public static final double ZUNGE_B2 = 28, ZUNGE_H = 2.0;

    private Gelaende() { }

    /** Arten des Grunds je Ecke (gleiche Nummern in gelaende.frag). */
    public static final int SAND = 0, NASS = 1, DUENE = 2, GRUND = 3, LAND = 4;
    public static final String[] ART = {"Sand", "nasser Sand", "Düne mit Strandhafer", "Seegrund", "Hinterland"};

    /** Wasserlinie (s) an der Stelle r: gerade, links zur Bucht nach vorn gebogen. */
    public static double wasserlinie(double r) { return WASSERLINIE + Math.max(0, -60 - r) * 0.12; }

    /** Höhe über Mittelwasser an (x, z) in der Szene. */
    public static double height(double x, double z) {
        double[] q = Site.rahmen(x, z);
        return hoehe(q[0], q[1]);
    }

    /** Höhe im Blickrahmen. */
    public static double hoehe(double s, double r) {
        return Math.max(kueste(s, r), zunge(s, r));
    }

    static double kueste(double s, double r) {
        double d = s - wasserlinie(r);
        if (d > 0) {
            // See: Vorstrand, Riff mit Rinne, dann flach hinaus
            double y = -(1.6 * (1 - Math.exp(-d / 35)) + 0.0045 * d) + 0.75 * Math.exp(-sq((d - 85) / 16)) - 0.35 * Math.exp(-sq((d - 58) / 12));
            y += 0.12 * (noise(s * 0.02, r * 0.02) - 0.5) * Math.min(1, d / 40);
            return Math.max(DRAUSSEN, y);
        }
        // Strand bis 2,2 m, gerippelt
        double strand = Math.min(STRAND_OBEN, -d / 15) + 0.03 * (noise(s * 0.4, r * 0.4) - 0.5);
        // Düne: vor dem Kamm zum Strand fallend, dahinter zum Hinterland
        double welle = 1 + 0.14 * Math.sin(r / 23.0) + 0.18 * (noise(r * 0.03, 7.3) - 0.5);
        double fuss = DUENENFUSS + wasserlinie(r) - WASSERLINIE;
        double kamm = KAMM + wasserlinie(r) - WASSERLINIE;
        double y;
        if (s >= fuss) y = strand;
        else if (s >= kamm) y = STRAND_OBEN + (KAMM_HOEHE * welle - STRAND_OBEN) * smooth(fuss, kamm, s);
        else y = KAMM_HOEHE * welle - (KAMM_HOEHE * welle - HINTERLAND) * smooth(kamm, kamm - 34, s);
        if (s < kamm - 34) y = HINTERLAND + 0.8 * (noise(s * 0.01, r * 0.01) - 0.5);
        // Dünenausläufer links vorn und kleine Kuppen auf dem Hang
        y += 4.2 * Math.exp(-(sq(s - 5) / 45 + sq(r + 17) / 70));
        if (s < fuss) y += 0.6 * (noise(s * 0.08, r * 0.08) - 0.5) * smooth(fuss, fuss - 6, s);
        return Math.max(y, strand);
    }

    /** Die Landzunge: Abstand zur Mittellinie, zur Spitze schmaler. */
    static double zunge(double s, double r) {
        double ax = ZUNGE_B[0] - ZUNGE_A[0], ar = ZUNGE_B[1] - ZUNGE_A[1];
        double t = ((s - ZUNGE_A[0]) * ax + (r - ZUNGE_A[1]) * ar) / (ax * ax + ar * ar);
        double tc = Math.max(0, Math.min(1, t));
        double ds = s - (ZUNGE_A[0] + ax * tc), dr = r - (ZUNGE_A[1] + ar * tc);
        double q = Math.hypot(ds, dr);
        double w = ZUNGE_B2 * (1 - 0.45 * tc * tc) + (t > 1 ? 0 : 0);
        double y = ZUNGE_H * (1 - sq(q / w)) + 0.4 * (noise(s * 0.05, r * 0.05) - 0.5);
        // Sockel des Leuchtturms etwas höher
        double[] lt = Site.rahmen(Site.LEUCHTTURM[0], Site.LEUCHTTURM[1]);
        y += 0.8 * Math.exp(-(sq(s - lt[0]) + sq(r - lt[1])) / 200);
        return Math.max(DRAUSSEN, y);
    }

    /** Art des Grunds an einem Punkt (für die Farben). */
    public static int art(double s, double r, double y) {
        double d = s - wasserlinie(r);
        boolean zunge = zunge(s, r) >= kueste(s, r) - 1e-9;
        if (y < -0.05) return GRUND;
        if (zunge) return y > 0.9 ? DUENE : y > 0.15 ? SAND : NASS;
        if (d > -2.5) return NASS;
        double kamm = KAMM + wasserlinie(r) - WASSERLINIE;
        if (s < kamm - 30) return LAND;
        return y > STRAND_OBEN + 0.35 ? DUENE : SAND;
    }

    // ------------------------------------------------------------------ Netz für die Grafikkarte

    /** Halbe Kante des Netzes (m) und Ecken je Seite. */
    public static final double L = 6000;
    public static final int N = 400;
    /** floats je Ecke im Netz. */
    public static final int STRIDE = 10;

    /** Lage der Ecke i (0..N−1) längs einer Achse: kubisch, fein in der Mitte (rund 0,5 m), grob am Rand (rund 90 m). */
    public static double achse(int i) {
        double t = 2.0 * i / (N - 1) - 1;
        return L * (0.016 * t + 0.984 * t * t * t);
    }

    /**
     * Das Netz: je Ecke Ort (3), Normale (3) und die Anteile der Arten (4: nasser Sand, Düne, Hinterland, Seegrund; der
     * Rest ist trockener Sand) als float; Indizes für Dreiecke. Die Mitte liegt am Ursprung, das
     * Netz ist im Blickrahmen ausgerichtet (Achsen s und r), damit der Strand fein liegt.
     */
    public static Netzdaten netz() {
        float[] v = new float[N * N * STRIDE];
        double e = 0.25;
        for (int j = 0; j < N; j++)
            for (int i = 0; i < N; i++) {
                double s = achse(i), r = achse(j);
                double y = hoehe(s, r);
                double gs = (hoehe(s + e, r) - hoehe(s - e, r)) / (2 * e), gr = (hoehe(s, r + e) - hoehe(s, r - e)) / (2 * e);
                // Normale im Rahmen (−gs, 1, −gr), in die Szene gedreht
                double nx = -gs * Site.FX - gr * Site.RX, nz = -gs * Site.FZ - gr * Site.RZ, ny = 1;
                double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
                double[] p = Site.welt(s, r);
                int o = (j * N + i) * STRIDE;
                v[o] = (float) p[0];
                v[o + 1] = (float) y;
                v[o + 2] = (float) p[1];
                v[o + 3] = (float) (nx / l);
                v[o + 4] = (float) (ny / l);
                v[o + 5] = (float) (nz / l);
                int a = art(s, r, y);
                if (a == NASS) v[o + 6] = 1;
                else if (a == DUENE) v[o + 7] = 1;
                else if (a == LAND) v[o + 8] = 1;
                else if (a == GRUND) v[o + 9] = 1;
            }
        int[] idx = new int[(N - 1) * (N - 1) * 6];
        int k = 0;
        for (int j = 0; j < N - 1; j++)
            for (int i = 0; i < N - 1; i++) {
                int a = j * N + i, b = a + 1, c = a + N, d = c + 1;
                idx[k++] = a; idx[k++] = c; idx[k++] = b;
                idx[k++] = b; idx[k++] = c; idx[k++] = d;
            }
        return new Netzdaten(v, idx);
    }

    public record Netzdaten(float[] ecken, int[] indizes) { }

    // ------------------------------------------------------------------ Grund für die See

    /** Raster des Grunds für {@code ocean}: Kante (m), Zellen je Seite, Ecke {x, z}; Werte als R16 über {@link #BETT_MIN} bis {@link #BETT_MAX}. */
    public static final double BETT_KANTE = 3072, BETT_MIN = -20, BETT_MAX = 20;
    public static final int BETT_N = 1536;

    /** Nordwestecke des Rasters (Mitte 900 m vor dem Hauptblick). */
    public static double[] bettEcke() {
        double[] m = Site.welt(900, -150);
        return new double[]{m[0] - BETT_KANTE / 2, m[1] - BETT_KANTE / 2};
    }

    /** Das Raster als vorzeichenlose 16-Bit-Werte, Zeile für Zeile von Nord nach Süd (z wächst). */
    public static short[] bett() {
        short[] h = new short[BETT_N * BETT_N];
        double[] c = bettEcke();
        double cell = BETT_KANTE / BETT_N;
        for (int j = 0; j < BETT_N; j++)
            for (int i = 0; i < BETT_N; i++) {
                double y = height(c[0] + (i + 0.5) * cell, c[1] + (j + 0.5) * cell);
                double u = (y - BETT_MIN) / (BETT_MAX - BETT_MIN);
                h[j * BETT_N + i] = (short) Math.round(Math.max(0, Math.min(1, u)) * 65535);
            }
        return h;
    }

    public static String describe() {
        return String.format(Locale.GERMAN, "Gelände gerechnet: Netz %d² Ecken über %.0f km, Grund %d² zu %.1f m, Wasserlinie %.0f m vor dem Blick, Düne %.1f m",
                N, 2 * L / 1000, BETT_N, BETT_KANTE / BETT_N, WASSERLINIE, KAMM_HOEHE);
    }

    // ------------------------------------------------------------------ Hilfen

    static double sq(double x) { return x * x; }

    static double smooth(double a, double b, double x) {
        double t = Math.max(0, Math.min(1, (x - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }

    static double hash(int x, int y) {
        long h = x * 374761393L + y * 668265263L;
        h = (h ^ (h >>> 13)) * 1274126177L;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (double) 0xFFFFFF;
    }

    /** Wertrauschen 0..1, glatt. */
    public static double noise(double x, double y) {
        int ix = (int) Math.floor(x), iy = (int) Math.floor(y);
        double fx = x - ix, fy = y - iy;
        fx = fx * fx * (3 - 2 * fx);
        fy = fy * fy * (3 - 2 * fy);
        double a = hash(ix, iy), b = hash(ix + 1, iy), c = hash(ix, iy + 1), d = hash(ix + 1, iy + 1);
        return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy;
    }
}
