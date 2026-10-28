package com.dan.thunder.scene;

import java.util.List;
import java.util.Locale;

/**
 * Die Lage der Objekte in Metern (Phase 18c, F52 A): was {@code TH_SCENE_OBJECT.X_M, Z_M, ROT_DEG} hält. Die Werte hier
 * sind die Vorgabe aus {@link Site} (abgelesen aus den fünf Bildern); {@code sql/04_th_3d.sql} und die eingebauten Daten
 * tragen dieselben Zahlen, die Prüfung vergleicht sie.
 * <p>
 * Die Strandkörbe nimmt die App aus den Daten (Ort, Öffnung, Farbe). Leuchtturm und Seebrücke hängen am Gelände (der
 * Turm steht auf der Landzunge, die Brücke läuft vom Strand ins Tiefe): ihre Lage in der Datenbank ist zum Auswerten da,
 * die App nimmt sie aus dem Code und meldet, wenn die Datenbank anderes sagt.
 */
public final class Lage {
    private Lage() { }

    /** Ein Objekt: Art (wie OBJ_TYPE), Ort x Ost, z Süd (m, Ursprung Hauptblick), Richtung (Grad von Nord). */
    public record Objekt(String typ, double xM, double zM, double rotDeg) {
        public String sql() { return String.format(Locale.ROOT, "%.2f, %.2f, %.1f", xM, zM, rotDeg); }
    }

    /** Ein Strandkorb, wie Bauwerke ihn baut: Ort, Öffnung, Streifenfarbe (#RRGGBB). */
    public record Korb(double xM, double zM, double rotDeg, String farbe) { }

    /** Leuchtturm: Fuß auf der Landzunge; Richtung 0. */
    public static Objekt leuchtturm() { return new Objekt("LEUCHTTURM", r2(Site.LEUCHTTURM[0]), r2(Site.LEUCHTTURM[1]), 0); }

    /** Seebrücke: Wurzel am Strand, Richtung zum Kopf. */
    public static Objekt seebruecke() {
        double[] a = Site.welt(Site.BRUECKE_WURZEL[0], Site.BRUECKE_WURZEL[1]), b = Site.welt(Site.BRUECKE_KOPF[0], Site.BRUECKE_KOPF[1]);
        return new Objekt("SEEBRUECKE", r2(a[0]), r2(a[1]), r1(kurs(a[0], a[1], b[0], b[1])));
    }

    /** Düne: der Standpunkt des Hauptblicks auf ihrem Kamm, Richtung des Blicks. */
    public static Objekt duene() { return new Objekt("DUENE", 0, 0, Site.BLICK); }

    /** Die vier Strandkörbe der Vorgabe: Öffnung zum Hauptblick, leicht gestreut (+8°, −6° im Wechsel). */
    public static List<Korb> koerbe(String[] farben) {
        Korb[] k = new Korb[Site.KOERBE.length];
        for (int i = 0; i < k.length; i++) {
            double[] p = Site.welt(Site.KOERBE[i][0], Site.KOERBE[i][1]);
            double zumBlick = Math.toDegrees(Math.atan2(-p[0], p[1])) + (i % 2 == 0 ? 8 : -6);
            k[i] = new Korb(r2(p[0]), r2(p[1]), r1(norm(zumBlick)), farben[i]);
        }
        return List.of(k);
    }

    /** Kurs von a nach b (Grad von Nord, 0..360). */
    public static double kurs(double ax, double az, double bx, double bz) { return norm(Math.toDegrees(Math.atan2(bx - ax, -(bz - az)))); }

    public static double norm(double g) { g %= 360; return g < 0 ? g + 360 : g; }

    static double r2(double v) { return Math.round(v * 100) / 100.0; }

    static double r1(double v) { return Math.round(v * 10) / 10.0; }
}
